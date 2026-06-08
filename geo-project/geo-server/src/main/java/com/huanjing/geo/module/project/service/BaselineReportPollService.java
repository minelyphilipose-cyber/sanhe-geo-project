package com.huanjing.geo.module.project.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.llm.LlmModelConfig;
import com.huanjing.geo.common.llm.router.LlmFeature;
import com.huanjing.geo.common.llm.router.LlmRouteException;
import com.huanjing.geo.common.llm.router.LlmRouteRequest;
import com.huanjing.geo.common.llm.router.LlmRouteResult;
import com.huanjing.geo.common.llm.router.LlmPlatformRouter;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.project.dto.BaselinePollBatchVO;
import com.huanjing.geo.module.project.dto.BaselinePollOptionVO;
import com.huanjing.geo.module.project.dto.BaselinePollOptionsVO;
import com.huanjing.geo.module.project.dto.BaselinePollQuestionTierVO;
import com.huanjing.geo.module.project.dto.BaselinePollResultVO;
import com.huanjing.geo.module.project.dto.BaselinePollStartRequest;
import com.huanjing.geo.module.project.entity.BaselineReportPollBatch;
import com.huanjing.geo.module.project.entity.BaselineReportPollResult;
import com.huanjing.geo.module.project.entity.KeywordGroupResult;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.BaselineReportPollBatchMapper;
import com.huanjing.geo.module.project.mapper.BaselineReportPollResultMapper;
import com.huanjing.geo.module.project.mapper.KeywordGroupResultMapper;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BaselineReportPollService {
    private static final Set<String> VALID_TIERS = Set.of("A", "B", "C");

    private final CurrentUserService currentUserService;
    private final CompanyMapper companyMapper;
    private final ProjectMapper projectMapper;
    private final AiPlatformConfigMapper aiPlatformConfigMapper;
    private final KeywordGroupResultMapper keywordGroupResultMapper;
    private final BaselineReportPollBatchMapper batchMapper;
    private final BaselineReportPollResultMapper resultMapper;
    private final LlmPlatformRouter llmPlatformRouter;

    public BaselinePollOptionsVO options(Long projectId) {
        requireReadableActiveProject(projectId);
        BaselinePollOptionsVO vo = new BaselinePollOptionsVO();
        vo.setPlatforms(loadProjectPlatforms(projectId).stream().map(this::toOption).toList());
        vo.setQuestionTiers(fillTierCounts(keywordGroupResultMapper.countProjectQuestionsByTier(projectId)));
        vo.setLatestBatch(toBatchVO(loadLatestBatch(projectId)));
        return vo;
    }

    public BaselinePollBatchVO start(Long projectId, BaselinePollStartRequest request) {
        SysUser operator = currentUserService.requireCurrentUser();
        Project project = requireReadableActiveProject(projectId);
        List<String> selectedPlatformCodes = normalizePlatformCodes(request.getPlatformCodes());
        List<String> selectedTiers = normalizeTiers(request.getQuestionTiers());
        if (selectedPlatformCodes.isEmpty()) {
            throw new BizException(400, "请选择至少一个平台");
        }
        if (selectedTiers.isEmpty()) {
            throw new BizException(400, "请选择至少一个问题分组");
        }

        List<AiPlatformConfig> platforms = resolveSelectedPlatforms(projectId, selectedPlatformCodes);
        List<KeywordGroupResult> questions = loadQuestions(projectId, selectedTiers);
        if (questions.isEmpty()) {
            throw new BizException(400, "当前项目没有可轮询的问题");
        }

        BaselineReportPollBatch batch = new BaselineReportPollBatch();
        batch.setProjectId(project.getId());
        batch.setStatus("running");
        batch.setSelectedPlatformCodes(JSONUtil.toJsonStr(selectedPlatformCodes));
        batch.setSelectedQuestionTiers(JSONUtil.toJsonStr(selectedTiers));
        batch.setPlatformCount(platforms.size());
        batch.setQuestionCount(questions.size());
        batch.setTotalCount(platforms.size() * questions.size());
        batch.setCompletedCount(0);
        batch.setFailedCount(0);
        batch.setCreatedBy(operator.getId());
        batch.setStartedAt(LocalDateTime.now());
        batchMapper.insert(batch);

        int completed = 0;
        int failed = 0;
        String lastError = null;
        for (AiPlatformConfig platform : platforms) {
            for (KeywordGroupResult question : questions) {
                BaselineReportPollResult result = pollOne(batch, platform, question);
                resultMapper.insert(result);
                if ("completed".equals(result.getStatus())) {
                    completed++;
                } else {
                    failed++;
                    lastError = result.getErrorMessage();
                }
            }
        }

        batch.setCompletedCount(completed);
        batch.setFailedCount(failed);
        batch.setStatus(failed == batch.getTotalCount() ? "failed" : "completed");
        batch.setErrorMessage(failed > 0 ? lastError : null);
        batch.setFinishedAt(LocalDateTime.now());
        batchMapper.updateById(batch);
        return toBatchVO(batch);
    }

    public Page<BaselinePollResultVO> results(Long projectId, Long batchId, long current, long size) {
        requireReadableActiveProject(projectId);
        Long targetBatchId = batchId;
        if (targetBatchId == null) {
            BaselineReportPollBatch latest = loadLatestBatch(projectId);
            if (latest == null) {
                return new Page<>(current, size);
            }
            targetBatchId = latest.getId();
        }
        Page<BaselineReportPollResult> page = resultMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<BaselineReportPollResult>()
                        .eq(BaselineReportPollResult::getProjectId, projectId)
                        .eq(BaselineReportPollResult::getBatchId, targetBatchId)
                        .orderByAsc(BaselineReportPollResult::getId));
        Page<BaselinePollResultVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::toResultVO).toList());
        return voPage;
    }

    private BaselineReportPollResult pollOne(BaselineReportPollBatch batch,
                                             AiPlatformConfig platform,
                                             KeywordGroupResult question) {
        BaselineReportPollResult result = new BaselineReportPollResult();
        result.setBatchId(batch.getId());
        result.setProjectId(batch.getProjectId());
        result.setKeywordResultId(question.getId());
        result.setQuestionTier(question.getQuestionTier());
        result.setQuestionText(question.getKeywordText());
        result.setPlatformId(platform.getId());
        result.setPlatformCode(platform.getPlatformCode());
        result.setPlatformName(platform.getPlatformName());
        try {
            LlmRouteResult routeResult = llmPlatformRouter.invoke(new LlmRouteRequest(
                    LlmFeature.GENERIC,
                    "You are a GEO (Generative Engine Optimization) baseline report polling assistant. Answer the user's question directly.",
                    question.getKeywordText(),
                    0D,
                    null,
                    null,
                    LlmModelConfig.LONG_FORM_MAX_REQUEST_TIMEOUT_MS,
                    platform.getMaxRetry(),
                    null,
                    false,
                    1,
                    0,
                    List.of(platform)
            ));
            result.setStatus("completed");
            result.setRequestCount(routeResult.requestCount());
            result.setResponseTimeMs(routeResult.durationMs());
            result.setResponseText(routeResult.responseText());
            result.setDetailJson(JSONUtil.toJsonStr(Map.of(
                    "channel", routeResult.channel(),
                    "model_id", routeResult.modelId(),
                    "model_name", routeResult.modelName()
            )));
        } catch (LlmRouteException ex) {
            result.setStatus("failed");
            result.setRequestCount(ex.requestCount());
            result.setErrorMessage(ex.getMessage());
            result.setDetailJson(JSONUtil.toJsonStr(Map.of("failure_kind", ex.failureKind().name())));
            log.warn("Baseline poll failed, batch={}, platform={}, question={}",
                    batch.getId(), platform.getPlatformCode(), question.getId(), ex);
        } catch (Exception ex) {
            result.setStatus("failed");
            result.setRequestCount(0);
            result.setErrorMessage(ex.getMessage());
            log.warn("Baseline poll failed, batch={}, platform={}, question={}",
                    batch.getId(), platform.getPlatformCode(), question.getId(), ex);
        }
        return result;
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
            throw new BizException(400, "仅已启动项目可以生成基线检测报告");
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

    private List<AiPlatformConfig> loadProjectPlatforms(Long projectId) {
        return aiPlatformConfigMapper.selectList(new LambdaQueryWrapper<AiPlatformConfig>()
                        .eq(AiPlatformConfig::getEnabled, true)
                        .orderByAsc(AiPlatformConfig::getPriorityLevel, AiPlatformConfig::getId))
                .stream()
                .filter(platform -> StringUtils.hasText(platform.getApiUrl()))
                .filter(platform -> StringUtils.hasText(platform.getModelId()))
                .toList();
    }

    private List<AiPlatformConfig> resolveSelectedPlatforms(Long projectId, List<String> selectedCodes) {
        List<AiPlatformConfig> platforms = loadProjectPlatforms(projectId);
        Map<String, AiPlatformConfig> map = platforms.stream().collect(Collectors.toMap(
                AiPlatformConfig::getPlatformCode, p -> p, (a, b) -> a, LinkedHashMap::new));
        List<AiPlatformConfig> selected = new ArrayList<>();
        for (String code : selectedCodes) {
            AiPlatformConfig platform = map.get(code);
            if (platform == null) {
                throw new BizException(400, "平台未绑定到当前项目或已禁用: " + code);
            }
            selected.add(platform);
        }
        return selected;
    }

    private List<KeywordGroupResult> loadQuestions(Long projectId, List<String> selectedTiers) {
        String tiersSql = selectedTiers.stream()
                .map(t -> "'" + t + "'")
                .collect(Collectors.joining(","));
        return keywordGroupResultMapper.selectProjectQuestionsByTiers(projectId, tiersSql);
    }

    private List<String> normalizePlatformCodes(List<String> codes) {
        if (codes == null) {
            return List.of();
        }
        return codes.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private List<String> normalizeTiers(List<String> tiers) {
        if (tiers == null) {
            return List.of();
        }
        return tiers.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .map(String::toUpperCase)
                .filter(VALID_TIERS::contains)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<BaselinePollQuestionTierVO> fillTierCounts(List<BaselinePollQuestionTierVO> rows) {
        Map<String, Long> countMap = rows == null ? Map.of() : rows.stream().collect(Collectors.toMap(
                BaselinePollQuestionTierVO::getTier,
                v -> v.getQuestionCount() == null ? 0L : v.getQuestionCount(),
                (a, b) -> a,
                LinkedHashMap::new
        ));
        List<BaselinePollQuestionTierVO> result = new ArrayList<>();
        for (String tier : List.of("A", "B", "C")) {
            BaselinePollQuestionTierVO item = new BaselinePollQuestionTierVO();
            item.setTier(tier);
            item.setQuestionCount(countMap.getOrDefault(tier, 0L));
            result.add(item);
        }
        return result;
    }

    private BaselinePollOptionVO toOption(AiPlatformConfig platform) {
        BaselinePollOptionVO vo = new BaselinePollOptionVO();
        vo.setId(platform.getId());
        vo.setCode(platform.getPlatformCode());
        vo.setName(platform.getPlatformName());
        vo.setPriorityLevel(platform.getPriorityLevel());
        return vo;
    }

    private BaselineReportPollBatch loadLatestBatch(Long projectId) {
        return batchMapper.selectOne(new LambdaQueryWrapper<BaselineReportPollBatch>()
                .eq(BaselineReportPollBatch::getProjectId, projectId)
                .orderByDesc(BaselineReportPollBatch::getId)
                .last("LIMIT 1"));
    }

    private BaselinePollBatchVO toBatchVO(BaselineReportPollBatch batch) {
        if (batch == null) {
            return null;
        }
        BaselinePollBatchVO vo = new BaselinePollBatchVO();
        vo.setId(batch.getId());
        vo.setProjectId(batch.getProjectId());
        vo.setStatus(batch.getStatus());
        vo.setSelectedPlatformCodes(parseStringList(batch.getSelectedPlatformCodes()));
        vo.setSelectedQuestionTiers(parseStringList(batch.getSelectedQuestionTiers()));
        vo.setPlatformCount(batch.getPlatformCount());
        vo.setQuestionCount(batch.getQuestionCount());
        vo.setTotalCount(batch.getTotalCount());
        vo.setCompletedCount(batch.getCompletedCount());
        vo.setFailedCount(batch.getFailedCount());
        vo.setErrorMessage(batch.getErrorMessage());
        vo.setStartedAt(batch.getStartedAt());
        vo.setFinishedAt(batch.getFinishedAt());
        return vo;
    }

    private BaselinePollResultVO toResultVO(BaselineReportPollResult result) {
        BaselinePollResultVO vo = new BaselinePollResultVO();
        vo.setId(result.getId());
        vo.setBatchId(result.getBatchId());
        vo.setKeywordResultId(result.getKeywordResultId());
        vo.setQuestionTier(result.getQuestionTier());
        vo.setQuestionText(result.getQuestionText());
        vo.setPlatformCode(result.getPlatformCode());
        vo.setPlatformName(result.getPlatformName());
        vo.setStatus(result.getStatus());
        vo.setRequestCount(result.getRequestCount());
        vo.setResponseTimeMs(result.getResponseTimeMs());
        vo.setResponseText(result.getResponseText());
        vo.setErrorMessage(result.getErrorMessage());
        vo.setCreatedAt(result.getCreatedAt());
        return vo;
    }

    private List<String> parseStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return JSONUtil.parseArray(json).stream()
                    .map(String::valueOf)
                    .collect(Collectors.toCollection(ArrayList::new));
        } catch (Exception ex) {
            return List.of();
        }
    }
}
