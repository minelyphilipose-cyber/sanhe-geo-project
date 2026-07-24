package com.huanjing.geo.module.project.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.project.dto.BaselineQuestionSnapshotVO;
import com.huanjing.geo.module.project.dto.BaselineSnapshotCompetitorSourceRequest;
import com.huanjing.geo.module.project.dto.BaselineSnapshotCreateRequest;
import com.huanjing.geo.module.project.dto.BaselineSnapshotIntentOverrideRequest;
import com.huanjing.geo.module.project.dto.BaselineSnapshotQuestionReviewRequest;
import com.huanjing.geo.module.project.dto.BaselineSnapshotReviewRequest;
import com.huanjing.geo.module.project.dto.BaselineSnapshotVO;
import com.huanjing.geo.module.project.entity.BaselineCompetitorSource;
import com.huanjing.geo.module.project.entity.BaselineQuestionSnapshot;
import com.huanjing.geo.module.project.entity.BaselineSnapshot;
import com.huanjing.geo.module.project.entity.KeywordGroupResult;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.BaselineCompetitorSourceMapper;
import com.huanjing.geo.module.project.mapper.BaselineQuestionSnapshotMapper;
import com.huanjing.geo.module.project.mapper.BaselineSnapshotMapper;
import com.huanjing.geo.module.project.mapper.KeywordGroupResultMapper;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BaselineReportSnapshotService {
    private static final String SCHEMA_VERSION = BaselineCanonicalVersionPolicy.SCHEMA_VERSION;
    private static final String INTENT_RUBRIC_VERSION = "baseline_intent_rubric_v1";
    private static final String SCORE_ALGORITHM_VERSION = BaselineCanonicalVersionPolicy.SCORE_ALGORITHM_VERSION;
    private static final String HIGHLIGHT_ALGORITHM_VERSION = BaselineCanonicalVersionPolicy.HIGHLIGHT_ALGORITHM_VERSION;
    private static final String COMPETITOR_NORMALIZATION_VERSION = BaselineCanonicalVersionPolicy.COMPETITOR_NORMALIZATION_VERSION;
    private static final String CANONICAL_AGGREGATE_VERSION = BaselineCanonicalVersionPolicy.CANONICAL_AGGREGATE_VERSION;
    private static final int BASELINE_MONITORING_QUESTION_COUNT = 40;
    private static final List<String> INTENT_ORDER = List.of(
            BaselineReportSnapshotRules.INTENT_RECOMMENDATION,
            BaselineReportSnapshotRules.INTENT_COMPARISON,
            BaselineReportSnapshotRules.INTENT_PROBLEM,
            BaselineReportSnapshotRules.INTENT_AWARENESS,
            BaselineReportSnapshotRules.INTENT_SCENE
    );

    private final CurrentUserService currentUserService;
    private final CompanyMapper companyMapper;
    private final ProjectMapper projectMapper;
    private final KeywordGroupResultMapper keywordGroupResultMapper;
    private final BaselineSnapshotMapper baselineSnapshotMapper;
    private final BaselineQuestionSnapshotMapper baselineQuestionSnapshotMapper;
    private final BaselineCompetitorSourceMapper baselineCompetitorSourceMapper;

    @Transactional
    public BaselineSnapshotVO create(Long projectId, BaselineSnapshotCreateRequest request) {
        SysUser operator = currentUserService.requireCurrentUser();
        Project project = requireReadableActiveProject(projectId);
        List<KeywordGroupResult> candidates = keywordGroupResultMapper.selectProjectBaselineQuestions(projectId);
        List<SelectedQuestion> questions = selectBaselineMonitoringQuestions(candidates, project.getBrandName());
        if (questions.isEmpty()) {
            throw new BizException(400, "当前项目没有可创建基线/监测题集的问题");
        }

        Map<Long, String> intentOverrides = normalizeIntentOverrides(request == null ? null : request.getIntentOverrides());
        LocalDateTime now = LocalDateTime.now();
        BaselineSnapshot snapshot = new BaselineSnapshot();
        snapshot.setProjectId(project.getId());
        snapshot.setCompanyId(project.getCompanyId());
        snapshot.setBrandId(project.getBrandId());
        snapshot.setRunSeq(0);
        snapshot.setStatus("DRAFT");
        snapshot.setSchemaVersion(SCHEMA_VERSION);
        snapshot.setIntentRubricVersion(INTENT_RUBRIC_VERSION);
        snapshot.setAlgorithmVersionsJson(JSONUtil.toJsonStr(defaultAlgorithmVersions()));
        snapshot.setSelectedVersionsJson(JSONUtil.toJsonStr(defaultSelectedVersions()));
        snapshot.setSourcePollBatchId(request == null ? null : request.getSourcePollBatchId());
        snapshot.setCreatedBy(operator.getId());
        snapshot.setCreatedAt(now);
        snapshot.setUpdatedAt(now);
        baselineSnapshotMapper.insert(snapshot);

        int index = 0;
        for (SelectedQuestion selected : questions) {
            KeywordGroupResult question = selected.question();
            BaselineQuestionSnapshot frozen = new BaselineQuestionSnapshot();
            frozen.setBaselineId(snapshot.getId());
            frozen.setQuestionKey("Q" + question.getId());
            frozen.setSourceKeywordResultId(question.getId());
            frozen.setQuestionText(question.getKeywordText());
            frozen.setValueTier(BaselineReportSnapshotRules.mapValueTier(question.getQuestionTier()));
            frozen.setSourceQuestionTier(question.getQuestionTier());
            frozen.setSourcePriority(question.getPriority());
            frozen.setIntentType(intentOverrides.getOrDefault(question.getId(), selected.intentType()));
            frozen.setSceneCode(question.getSceneCode());
            frozen.setSortOrder(resolveSortOrder(question, index));
            frozen.setCreatedAt(now);
            baselineQuestionSnapshotMapper.insert(frozen);
            index++;
        }
        replaceCompetitorSources(snapshot.getId(), request == null ? null : request.getCompetitorSources());
        return get(projectId, snapshot.getId());
    }

    @Transactional
    public BaselineSnapshotVO review(Long projectId, Long baselineId, BaselineSnapshotReviewRequest request) {
        requireReadableActiveProject(projectId);
        BaselineSnapshot snapshot = loadProjectSnapshot(projectId, baselineId);
        ensureDraft(snapshot);
        List<BaselineQuestionSnapshot> questions = loadQuestions(snapshot.getId());
        Map<Long, BaselineQuestionSnapshot> byId = questions.stream().collect(Collectors.toMap(
                BaselineQuestionSnapshot::getId,
                item -> item,
                (first, ignored) -> first,
                LinkedHashMap::new
        ));
        Map<Long, BaselineQuestionSnapshot> bySourceId = questions.stream()
                .filter(item -> item.getSourceKeywordResultId() != null)
                .collect(Collectors.toMap(
                        BaselineQuestionSnapshot::getSourceKeywordResultId,
                        item -> item,
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));
        Set<Long> updatedQuestionIds = new HashSet<>();
        if (request != null && request.getQuestions() != null) {
            for (BaselineSnapshotQuestionReviewRequest item : request.getQuestions()) {
                BaselineQuestionSnapshot question = resolveQuestionForReview(item, byId, bySourceId);
                if (question == null) {
                    throw new BizException(400, "复核的问题不属于当前基线草稿");
                }
                boolean changed = false;
                if (item.getIntentType() != null) {
                    question.setIntentType(BaselineReportSnapshotRules.normalizeIntentType(item.getIntentType()));
                    changed = true;
                }
                if (item.getValueTier() != null) {
                    question.setValueTier(BaselineReportSnapshotRules.normalizeValueTier(item.getValueTier()));
                    changed = true;
                }
                if (changed && updatedQuestionIds.add(question.getId())) {
                    baselineQuestionSnapshotMapper.updateById(question);
                }
            }
        }
        if (request != null && request.getCompetitorSources() != null) {
            replaceCompetitorSources(snapshot.getId(), request.getCompetitorSources());
        }
        return get(projectId, snapshot.getId());
    }

    @Transactional
    public BaselineSnapshotVO seal(Long projectId, Long baselineId) {
        SysUser operator = currentUserService.requireCurrentUser();
        requireReadableActiveProject(projectId);
        BaselineSnapshot snapshot = loadProjectSnapshot(projectId, baselineId);
        ensureDraft(snapshot);
        LocalDateTime now = LocalDateTime.now();
        snapshot.setStatus("SEALED");
        snapshot.setSealedAt(now);
        snapshot.setSealedBy(operator.getId());
        snapshot.setUpdatedAt(now);
        baselineSnapshotMapper.updateById(snapshot);
        return get(projectId, snapshot.getId());
    }

    public BaselineSnapshotVO latest(Long projectId) {
        requireReadableActiveProject(projectId);
        BaselineSnapshot snapshot = baselineSnapshotMapper.selectOne(new LambdaQueryWrapper<BaselineSnapshot>()
                .eq(BaselineSnapshot::getProjectId, projectId)
                .orderByDesc(BaselineSnapshot::getId)
                .last("LIMIT 1"));
        return snapshot == null ? null : toVO(snapshot, loadQuestions(snapshot.getId()));
    }

    public BaselineSnapshotVO get(Long projectId, Long baselineId) {
        requireReadableActiveProject(projectId);
        BaselineSnapshot snapshot = loadProjectSnapshot(projectId, baselineId);
        return toVO(snapshot, loadQuestions(snapshot.getId()));
    }

    private BaselineSnapshot loadProjectSnapshot(Long projectId, Long baselineId) {
        BaselineSnapshot snapshot = baselineSnapshotMapper.selectById(baselineId);
        if (snapshot == null || !projectId.equals(snapshot.getProjectId())) {
            throw new BizException(404, "Baseline snapshot not found");
        }
        return snapshot;
    }

    private void ensureDraft(BaselineSnapshot snapshot) {
        if (!"DRAFT".equals(snapshot.getStatus())) {
            throw new BizException(400, "仅 DRAFT 状态的基线快照允许复核或封板");
        }
    }

    private Project requireReadableActiveProject(Long projectId) {
        SysUser user = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("project.read");
        Project project = projectMapper.selectById(projectId);
        if (project == null || project.getDeletedAt() != null) {
            throw new BizException(404, "Project not found");
        }
        currentUserService.ensurePartnerResourceAccess(user, project.getPartnerId(), "project");
        ensureSalesProjectAccess(user, project);
        if (!"active".equals(project.getStatus())) {
            throw new BizException(400, "仅已启动项目可以封板基线报告");
        }
        return project;
    }

    private void ensureSalesProjectAccess(SysUser user, Project project) {
        if (!"sales".equals(user.getRole())) {
            return;
        }
        Company company = companyMapper.selectById(project.getCompanyId());
        if (company == null || company.getDeletedAt() != null
                || company.getSalesOwnerId() == null || !company.getSalesOwnerId().equals(user.getId())) {
            throw new BizException(403, "No permission to access this project");
        }
        if (!"signed".equals(company.getStatus())) {
            throw new BizException(403, "Sales can only access projects of signed companies");
        }
    }

    private Map<Long, String> normalizeIntentOverrides(List<BaselineSnapshotIntentOverrideRequest> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return Map.of();
        }
        return overrides.stream().collect(Collectors.toMap(
                BaselineSnapshotIntentOverrideRequest::getKeywordResultId,
                item -> BaselineReportSnapshotRules.normalizeIntentType(item.getIntentType()),
                (first, ignored) -> first,
                LinkedHashMap::new
        ));
    }

    private BaselineQuestionSnapshot resolveQuestionForReview(BaselineSnapshotQuestionReviewRequest item,
                                                              Map<Long, BaselineQuestionSnapshot> byId,
                                                              Map<Long, BaselineQuestionSnapshot> bySourceId) {
        if (item == null) {
            throw new BizException(400, "复核问题不能为空");
        }
        if (item.getQuestionSnapshotId() != null) {
            return byId.get(item.getQuestionSnapshotId());
        }
        if (item.getKeywordResultId() != null) {
            return bySourceId.get(item.getKeywordResultId());
        }
        throw new BizException(400, "复核问题必须提供 questionSnapshotId 或 keywordResultId");
    }

    private Map<String, String> defaultAlgorithmVersions() {
        Map<String, String> versions = new LinkedHashMap<>();
        versions.put("score_bundle", SCORE_ALGORITHM_VERSION);
        versions.put("highlight", HIGHLIGHT_ALGORITHM_VERSION);
        versions.put("competitor_normalization", COMPETITOR_NORMALIZATION_VERSION);
        versions.put("canonical_aggregate", CANONICAL_AGGREGATE_VERSION);
        versions.put("intent_rubric", INTENT_RUBRIC_VERSION);
        return versions;
    }

    private Map<String, String> defaultSelectedVersions() {
        return BaselineCanonicalVersionPolicy.selectedVersions();
    }

    private List<SelectedQuestion> selectBaselineMonitoringQuestions(List<KeywordGroupResult> candidates, String brandName) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        candidates = candidates.stream()
                .filter(question -> Set.of("A", "B").contains(question.getQuestionTier() == null
                        ? ""
                        : question.getQuestionTier().trim().toUpperCase()))
                .toList();
        if (candidates.isEmpty()) {
            return List.of();
        }
        Map<String, List<SelectedQuestion>> byIntent = new LinkedHashMap<>();
        for (KeywordGroupResult question : candidates) {
            String intent = BaselineReportSnapshotRules.classifyIntent(question.getKeywordText(), question.getSceneCode(), brandName);
            byIntent.computeIfAbsent(intent, ignored -> new ArrayList<>()).add(new SelectedQuestion(question, intent));
        }
        Comparator<SelectedQuestion> comparator = Comparator
                .comparingInt((SelectedQuestion item) -> tierRank(item.question().getQuestionTier()))
                .thenComparing((SelectedQuestion item) -> item.question().getSortOrder() == null ? Integer.MAX_VALUE : item.question().getSortOrder())
                .thenComparing(item -> item.question().getId());
        byIntent.values().forEach(list -> list.sort(comparator));

        int targetPerIntent = BASELINE_MONITORING_QUESTION_COUNT / INTENT_ORDER.size();
        List<SelectedQuestion> selected = new ArrayList<>();
        Set<Long> selectedIds = new HashSet<>();
        for (String intent : INTENT_ORDER) {
            List<SelectedQuestion> bucket = byIntent.getOrDefault(intent, List.of());
            for (SelectedQuestion item : bucket.stream().limit(targetPerIntent).toList()) {
                if (selectedIds.add(item.question().getId())) {
                    selected.add(item);
                }
            }
        }
        List<SelectedQuestion> remainder = byIntent.values().stream()
                .flatMap(List::stream)
                .filter(item -> !selectedIds.contains(item.question().getId()))
                .sorted(comparator)
                .toList();
        for (SelectedQuestion item : remainder) {
            if (selected.size() >= BASELINE_MONITORING_QUESTION_COUNT) {
                break;
            }
            if (selectedIds.add(item.question().getId())) {
                selected.add(item);
            }
        }
        selected.sort(Comparator
                .comparing((SelectedQuestion item) -> INTENT_ORDER.indexOf(item.intentType()))
                .thenComparing(comparator));
        return selected;
    }

    private int tierRank(String tier) {
        return switch (tier == null ? "" : tier.trim().toUpperCase()) {
            case "A" -> 0;
            case "B" -> 1;
            case "C" -> 2;
            default -> 3;
        };
    }

    private void replaceCompetitorSources(Long baselineId, List<BaselineSnapshotCompetitorSourceRequest> sources) {
        baselineCompetitorSourceMapper.delete(new LambdaQueryWrapper<BaselineCompetitorSource>()
                .eq(BaselineCompetitorSource::getBaselineId, baselineId));
        if (sources == null || sources.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (BaselineSnapshotCompetitorSourceRequest source : sources) {
            if (source == null || source.getCompetitorName() == null || source.getCompetitorName().isBlank()) {
                continue;
            }
            BaselineCompetitorSource entity = new BaselineCompetitorSource();
            entity.setBaselineId(baselineId);
            entity.setCompetitorId(source.getCompetitorId());
            entity.setCompetitorName(source.getCompetitorName().trim());
            entity.setAliasesJson(normalizeAliasesJson(source.getAliasesJson()));
            entity.setSourceType(source.getSourceType());
            entity.setSourceUrl(source.getSourceUrl());
            entity.setSourceNote(source.getSourceNote());
            entity.setReviewStatus(normalizeReviewStatus(source.getReviewStatus()));
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            baselineCompetitorSourceMapper.insert(entity);
        }
    }

    private String normalizeReviewStatus(String status) {
        if (status == null || status.isBlank()) {
            return "UNVERIFIED";
        }
        String normalized = status.trim().toUpperCase();
        if (!Set.of("UNVERIFIED", "VERIFIED", "REJECTED").contains(normalized)) {
            throw new BizException(400, "竞品核实状态非法: " + status);
        }
        return normalized;
    }

    private String normalizeAliasesJson(String aliases) {
        List<String> parsed = parseAliasList(aliases);
        return parsed.isEmpty() ? null : JSONUtil.toJsonStr(parsed);
    }

    private List<String> parseAliasList(String aliases) {
        if (aliases == null || aliases.isBlank()) {
            return List.of();
        }
        List<String> raw;
        try {
            raw = JSONUtil.parseArray(aliases).stream()
                    .map(String::valueOf)
                    .toList();
        } catch (Exception ignored) {
            raw = List.of(aliases.split("[,，\\n\\r；;]+"));
        }
        return raw.stream()
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
    }

    private int resolveSortOrder(KeywordGroupResult question, int fallbackIndex) {
        if (question.getSortOrder() != null) {
            return question.getSortOrder();
        }
        return fallbackIndex;
    }

    private List<BaselineQuestionSnapshot> loadQuestions(Long baselineId) {
        return baselineQuestionSnapshotMapper.selectList(new LambdaQueryWrapper<BaselineQuestionSnapshot>()
                .eq(BaselineQuestionSnapshot::getBaselineId, baselineId)
                .orderByAsc(BaselineQuestionSnapshot::getSortOrder, BaselineQuestionSnapshot::getId));
    }

    private BaselineSnapshotVO toVO(BaselineSnapshot snapshot, List<BaselineQuestionSnapshot> questions) {
        BaselineSnapshotVO vo = new BaselineSnapshotVO();
        vo.setId(snapshot.getId());
        vo.setProjectId(snapshot.getProjectId());
        vo.setCompanyId(snapshot.getCompanyId());
        vo.setBrandId(snapshot.getBrandId());
        vo.setRunSeq(snapshot.getRunSeq());
        vo.setStatus(snapshot.getStatus());
        vo.setSchemaVersion(snapshot.getSchemaVersion());
        vo.setIntentRubricVersion(snapshot.getIntentRubricVersion());
        vo.setAlgorithmVersionsJson(snapshot.getAlgorithmVersionsJson());
        vo.setSelectedVersionsJson(snapshot.getSelectedVersionsJson());
        vo.setSourcePollBatchId(snapshot.getSourcePollBatchId());
        vo.setSealedAt(snapshot.getSealedAt());
        vo.setSealedBy(snapshot.getSealedBy());
        vo.setCreatedBy(snapshot.getCreatedBy());
        vo.setCreatedAt(snapshot.getCreatedAt());
        vo.setUpdatedAt(snapshot.getUpdatedAt());
        vo.setQuestionCount(questions.size());
        vo.setQuestions(questions.stream().map(this::toQuestionVO).toList());
        List<BaselineCompetitorSource> competitorSources = baselineCompetitorSourceMapper.selectList(
                new LambdaQueryWrapper<BaselineCompetitorSource>()
                        .eq(BaselineCompetitorSource::getBaselineId, snapshot.getId())
                        .orderByAsc(BaselineCompetitorSource::getId));
        vo.setCompetitorCount(competitorSources.size());
        vo.setCompetitorSources(competitorSources.stream().map(this::toCompetitorSourceRequest).toList());
        vo.setWarnings(competitorSources.isEmpty()
                ? List.of("当前基线未配置竞品集，报告中的「对手在 AI 里的样子」模块会缺少核心内容。")
                : List.of());
        return vo;
    }

    private BaselineSnapshotCompetitorSourceRequest toCompetitorSourceRequest(BaselineCompetitorSource source) {
        BaselineSnapshotCompetitorSourceRequest request = new BaselineSnapshotCompetitorSourceRequest();
        request.setCompetitorId(source.getCompetitorId());
        request.setCompetitorName(source.getCompetitorName());
        request.setAliasesJson(source.getAliasesJson());
        request.setSourceType(source.getSourceType());
        request.setSourceUrl(source.getSourceUrl());
        request.setSourceNote(source.getSourceNote());
        request.setReviewStatus(source.getReviewStatus());
        return request;
    }

    private BaselineQuestionSnapshotVO toQuestionVO(BaselineQuestionSnapshot question) {
        BaselineQuestionSnapshotVO vo = new BaselineQuestionSnapshotVO();
        vo.setId(question.getId());
        vo.setQuestionKey(question.getQuestionKey());
        vo.setSourceKeywordResultId(question.getSourceKeywordResultId());
        vo.setQuestionText(question.getQuestionText());
        vo.setValueTier(question.getValueTier());
        vo.setSourceQuestionTier(question.getSourceQuestionTier());
        vo.setSourcePriority(question.getSourcePriority());
        vo.setIntentType(question.getIntentType());
        vo.setSceneCode(question.getSceneCode());
        vo.setSortOrder(question.getSortOrder());
        vo.setCreatedAt(question.getCreatedAt());
        return vo;
    }

    private record SelectedQuestion(KeywordGroupResult question, String intentType) {
    }
}
