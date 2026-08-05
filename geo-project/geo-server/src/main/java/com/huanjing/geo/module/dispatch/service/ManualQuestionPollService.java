package com.huanjing.geo.module.dispatch.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.customer.access.InternalScopeService;
import com.huanjing.geo.module.dispatch.dto.ManualQuestionPollBatchView;
import com.huanjing.geo.module.dispatch.dto.ManualQuestionPollPlatformOption;
import com.huanjing.geo.module.dispatch.dto.ManualQuestionPollRequest;
import com.huanjing.geo.module.dispatch.entity.PollBatch;
import com.huanjing.geo.module.dispatch.entity.PollBatchShard;
import com.huanjing.geo.module.dispatch.entity.PollCitation;
import com.huanjing.geo.module.dispatch.entity.PollInvocationAttempt;
import com.huanjing.geo.module.dispatch.entity.PollResult;
import com.huanjing.geo.module.dispatch.entity.PollSearchSource;
import com.huanjing.geo.module.dispatch.mapper.PollBatchMapper;
import com.huanjing.geo.module.dispatch.mapper.PollBatchShardMapper;
import com.huanjing.geo.module.dispatch.mapper.PollCitationMapper;
import com.huanjing.geo.module.dispatch.mapper.PollInvocationAttemptMapper;
import com.huanjing.geo.module.dispatch.mapper.PollResultMapper;
import com.huanjing.geo.module.dispatch.mapper.PollSearchSourceMapper;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.system.service.ActivityLogService;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ManualQuestionPollService {

    private static final String MANUAL_PERMISSION = "dispatch.question_poll.manual";
    private static final int MANUAL_BATCH_NO_BASE = 1_000_000;
    private static final int DEFAULT_HISTORY_SIZE = 20;
    private static final int MAX_HISTORY_SIZE = 50;

    private final PollBatchMapper pollBatchMapper;
    private final PollBatchShardMapper pollBatchShardMapper;
    private final PollResultMapper pollResultMapper;
    private final PollInvocationAttemptMapper pollInvocationAttemptMapper;
    private final PollSearchSourceMapper pollSearchSourceMapper;
    private final PollCitationMapper pollCitationMapper;
    private final ProjectMapper projectMapper;
    private final AiPlatformConfigMapper aiPlatformConfigMapper;
    private final DispatchQuestionPollPlanningService planningService;
    private final CurrentUserService currentUserService;
    private final InternalScopeService internalScopeService;
    private final ActivityLogService activityLogService;

    public List<ManualQuestionPollPlatformOption> platformOptions() {
        requireOperator();
        List<AiPlatformConfig> candidates = aiPlatformConfigMapper.selectList(
                new LambdaQueryWrapper<AiPlatformConfig>()
                        .in(AiPlatformConfig::getUsageScene, "STANDARD_CHAT", "QUESTION_POLL_WEB")
                        .orderByAsc(AiPlatformConfig::getPriorityLevel, AiPlatformConfig::getId)
        );
        return QuestionPollPlatformSelection.preferredForOptions(candidates).stream()
                .map(this::toOption)
                .toList();
    }

    @Transactional
    public ManualQuestionPollBatchView start(ManualQuestionPollRequest request) {
        SysUser operator = requireOperator();
        String requestId = normalizeRequestId(request.getClientRequestId());
        String tier = request.getQuestionTier().trim().toUpperCase(Locale.ROOT);
        List<Long> platformIds = normalizePlatformIds(request.getPlatformIds());
        String fingerprint = fingerprint(request.getProjectId(), tier, platformIds, request.getQuestionLimit());

        PollBatch existing = findByRequest(operator.getId(), requestId);
        if (existing != null) {
            ensureFingerprint(existing, fingerprint);
            ensureProjectAccess(operator, existing.getProjectId());
            return view(existing.getId());
        }

        Project project = requireRunnableProject(operator, request.getProjectId());
        if (pollBatchMapper.lockProjectForManualPoll(project.getId()) == null) {
            throw new BizException(404, "Project not found", 404, null);
        }
        existing = findByRequest(operator.getId(), requestId);
        if (existing != null) {
            ensureFingerprint(existing, fingerprint);
            return view(existing.getId());
        }

        List<AiPlatformConfig> platforms = requirePlatforms(platformIds);
        LocalDate batchDate = LocalDate.now();
        int maxBatchNo = pollBatchMapper.selectMaxBatchNo(project.getId(), batchDate, tier);
        int batchNo = Math.max(MANUAL_BATCH_NO_BASE, maxBatchNo + 1);
        PollBatch batch = planningService.planManualProjectTierPoll(
                project,
                batchDate,
                tier,
                batchNo,
                platforms,
                request.getQuestionLimit(),
                operator.getId(),
                requestId,
                fingerprint
        );
        activityLogService.logAction(
                operator.getId(),
                "dispatch.question_poll.manual.start",
                "poll_batch",
                batch.getId(),
                null,
                Map.of(
                        "projectId", project.getId(),
                        "questionTier", tier,
                        "platformIds", platformIds,
                        "questionLimit", request.getQuestionLimit()
                ),
                Map.of("clientRequestId", requestId)
        );
        return view(batch.getId());
    }

    public ManualQuestionPollBatchView get(Long batchId) {
        SysUser operator = requireOperator();
        PollBatch batch = pollBatchMapper.selectById(batchId);
        if (batch == null || !"MANUAL".equalsIgnoreCase(batch.getTriggerType())) {
            throw new BizException(404, "Manual question poll batch not found", 404, null);
        }
        Project project = ensureProjectAccess(operator, batch.getProjectId());
        return view(batch, project, true);
    }

    public List<ManualQuestionPollBatchView> listRecent(Integer requestedSize) {
        SysUser operator = requireOperator();
        int size = requestedSize == null
                ? DEFAULT_HISTORY_SIZE
                : Math.max(1, Math.min(requestedSize, MAX_HISTORY_SIZE));
        List<PollBatch> batches = pollBatchMapper.selectList(
                new LambdaQueryWrapper<PollBatch>()
                        .eq(PollBatch::getTriggerType, "MANUAL")
                        .eq(PollBatch::getCreatedBy, operator.getId())
                        .orderByDesc(PollBatch::getId)
                        .last("LIMIT " + size)
        );
        List<ManualQuestionPollBatchView> history = new ArrayList<>(batches.size());
        for (PollBatch batch : batches) {
            Project project = ensureProjectAccess(operator, batch.getProjectId());
            history.add(view(batch, project, false));
        }
        return history;
    }

    private SysUser requireOperator() {
        currentUserService.ensurePermission(MANUAL_PERMISSION);
        return currentUserService.requireCurrentUser();
    }

    private Project requireRunnableProject(SysUser operator, Long projectId) {
        Project project = ensureProjectAccess(operator, projectId);
        if (!"active".equalsIgnoreCase(project.getStatus()) || project.getActivatedAt() == null) {
            throw new BizException(400,
                    "Only activated active projects can run question poll verification", 400, null);
        }
        return project;
    }

    private Project ensureProjectAccess(SysUser operator, Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null || project.getDeletedAt() != null) {
            throw new BizException(404, "Project not found", 404, null);
        }
        internalScopeService.ensureProjectAccess(operator, project, "project");
        currentUserService.ensurePartnerResourceAccess(operator, project.getPartnerId(), "project");
        return project;
    }

    private List<AiPlatformConfig> requirePlatforms(List<Long> requestedIds) {
        List<AiPlatformConfig> rows = aiPlatformConfigMapper.selectBatchIds(requestedIds);
        Set<Long> preferredIds = QuestionPollPlatformSelection.preferredEnabled(
                aiPlatformConfigMapper.selectList(new LambdaQueryWrapper<AiPlatformConfig>()
                        .eq(AiPlatformConfig::getEnabled, true)
                        .eq(AiPlatformConfig::getEnabledForQuestionPoll, true)
                        .in(AiPlatformConfig::getUsageScene, "STANDARD_CHAT", "QUESTION_POLL_WEB")))
                .stream().map(AiPlatformConfig::getId).collect(java.util.stream.Collectors.toSet());
        if (preferredIds.isEmpty()) {
            // Defensive compatibility for a concurrent configuration refresh; requested rows are
            // still checked by isSelectable below.
            preferredIds = rows.stream().map(AiPlatformConfig::getId)
                    .collect(java.util.stream.Collectors.toSet());
        }
        Map<Long, AiPlatformConfig> byId = new LinkedHashMap<>();
        rows.forEach(row -> byId.put(row.getId(), row));
        List<AiPlatformConfig> ordered = new ArrayList<>();
        for (Long id : requestedIds) {
            AiPlatformConfig config = byId.get(id);
            if (config == null) {
                throw new BizException(404, "Question poll platform not found: " + id, 404, null);
            }
            if (!isSelectable(config) || !preferredIds.contains(config.getId())) {
                throw new BizException(400,
                        "Question poll platform is not the active route for its channel: "
                                + config.getPlatformName(), 400, null);
            }
            ordered.add(config);
        }
        return ordered;
    }

    private ManualQuestionPollPlatformOption toOption(AiPlatformConfig config) {
        ManualQuestionPollPlatformOption option = new ManualQuestionPollPlatformOption();
        option.setPlatformId(config.getId());
        option.setPlatformCode(config.getPlatformCode());
        option.setChannelCode(config.getChannelCode());
        option.setPlatformName(config.getPlatformName());
        option.setIntegrationType(config.getIntegrationType());
        option.setModelId(config.getModelId());
        option.setEnabled(config.getEnabled());
        option.setEnabledForQuestionPoll(config.getEnabledForQuestionPoll());
        option.setSelectable(isSelectable(config));
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            option.setUnavailableReason("平台总开关未启用");
        } else if (!Boolean.TRUE.equals(config.getEnabledForQuestionPoll())) {
            option.setUnavailableReason("问题轮询开关未启用");
        }
        return option;
    }

    private boolean isSelectable(AiPlatformConfig config) {
        return Boolean.TRUE.equals(config.getEnabled())
                && Boolean.TRUE.equals(config.getEnabledForQuestionPoll())
                && QuestionPollPlatformSelection.supportsQuestionPollScene(config);
    }

    private PollBatch findByRequest(Long operatorId, String requestId) {
        return pollBatchMapper.selectOne(
                new LambdaQueryWrapper<PollBatch>()
                        .eq(PollBatch::getCreatedBy, operatorId)
                        .eq(PollBatch::getClientRequestId, requestId)
                        .last("LIMIT 1")
        );
    }

    private void ensureFingerprint(PollBatch batch, String fingerprint) {
        if (!fingerprint.equals(batch.getRequestFingerprint())) {
            throw new BizException(409, "IDEMPOTENCY_CONFLICT", 409, null);
        }
    }

    private ManualQuestionPollBatchView view(Long batchId) {
        PollBatch batch = pollBatchMapper.selectById(batchId);
        if (batch == null) {
            throw new BizException(404, "Question poll batch not found", 404, null);
        }
        Project project = projectMapper.selectById(batch.getProjectId());
        return view(batch, project, true);
    }

    private ManualQuestionPollBatchView view(PollBatch batch, Project project, boolean includeResultDetails) {
        Long batchId = batch.getId();
        List<PollBatchShard> shards = pollBatchShardMapper.selectByBatchId(batchId);
        List<PollResult> results = pollResultMapper.selectList(
                new LambdaQueryWrapper<PollResult>()
                        .eq(PollResult::getBatchId, batchId)
                        .isNull(PollResult::getDeletedAt)
        );

        ManualQuestionPollBatchView result = new ManualQuestionPollBatchView();
        result.setBatchId(batch.getId());
        result.setProjectId(batch.getProjectId());
        result.setProjectName(project == null ? "" : project.getProjectName());
        result.setBatchDate(batch.getBatchDate());
        result.setBatchNo(batch.getBatchNo());
        result.setQuestionTier(batch.getQuestionTier());
        result.setTriggerType(batch.getTriggerType());
        result.setStatus(batch.getStatus());
        result.setQuestionLimit(batch.getManualQuestionLimit());
        result.setPlatformCount(batch.getTotalPlatformCount());
        result.setShardCount(batch.getTotalShardCount());
        result.setTerminalShardCount((int) shards.stream()
                .filter(row -> "completed".equals(row.getStatus()) || "failed".equals(row.getStatus()))
                .count());
        result.setFailedShardCount((int) shards.stream()
                .filter(row -> "failed".equals(row.getStatus()))
                .count());
        result.setResultCount(results.size());
        result.setCompletedCount((int) results.stream().filter(row -> "completed".equals(row.getStatus())).count());
        result.setFailedCount((int) results.stream().filter(row -> "failed".equals(row.getStatus())).count());
        result.setSearchConfirmedCount((int) results.stream().filter(this::isEffectiveSearchConfirmed).count());
        result.setConfirmedCitationExposureCount((int) results.stream()
                .filter(this::isEffectiveCitationExposure).count());
        result.setTriggeredAt(batch.getTriggeredAt());
        result.setFinishedAt(batch.getFinishedAt());
        result.setPlatforms(buildPlatformProgress(shards));
        if (includeResultDetails) {
            result.setResults(buildResultDetails(results, shards));
        }
        return result;
    }

    private List<ManualQuestionPollBatchView.ResultDetail> buildResultDetails(
            List<PollResult> results,
            List<PollBatchShard> shards
    ) {
        List<Long> attemptIds = results.stream()
                .map(PollResult::getEffectiveAttemptId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, PollInvocationAttempt> attempts = attemptIds.isEmpty()
                ? Map.of()
                : pollInvocationAttemptMapper.selectBatchIds(attemptIds).stream()
                .collect(Collectors.toMap(PollInvocationAttempt::getId, Function.identity()));
        List<PollSearchSource> sources = attemptIds.isEmpty()
                ? List.of()
                : pollSearchSourceMapper.selectList(
                new LambdaQueryWrapper<PollSearchSource>()
                        .in(PollSearchSource::getAttemptId, attemptIds)
                        .orderByAsc(PollSearchSource::getAttemptId, PollSearchSource::getRankNo, PollSearchSource::getId)
        );
        List<PollCitation> citations = attemptIds.isEmpty()
                ? List.of()
                : pollCitationMapper.selectList(
                new LambdaQueryWrapper<PollCitation>()
                        .in(PollCitation::getAttemptId, attemptIds)
                        .orderByAsc(PollCitation::getAttemptId, PollCitation::getCitationIndex, PollCitation::getId)
        );
        Map<Long, List<PollSearchSource>> sourcesByAttempt = sources.stream()
                .collect(Collectors.groupingBy(PollSearchSource::getAttemptId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, List<PollCitation>> citationsByAttempt = citations.stream()
                .collect(Collectors.groupingBy(PollCitation::getAttemptId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, PollSearchSource> sourcesById = sources.stream()
                .collect(Collectors.toMap(PollSearchSource::getId, Function.identity(), (left, right) -> left));
        Map<Long, String> platformNames = shards.stream()
                .collect(Collectors.toMap(
                        PollBatchShard::getPlatformId,
                        PollBatchShard::getPlatformName,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        return results.stream()
                .sorted(Comparator.comparing(PollResult::getKeywordTextSnapshot,
                                Comparator.nullsLast(String::compareTo))
                        .thenComparing(PollResult::getPlatformId, Comparator.nullsLast(Long::compareTo)))
                .map(row -> toResultDetail(
                        row,
                        attempts.get(row.getEffectiveAttemptId()),
                        sourcesByAttempt.getOrDefault(row.getEffectiveAttemptId(), List.of()),
                        citationsByAttempt.getOrDefault(row.getEffectiveAttemptId(), List.of()),
                        sourcesById,
                        platformNames.get(row.getPlatformId())
                ))
                .toList();
    }

    private ManualQuestionPollBatchView.ResultDetail toResultDetail(
            PollResult result,
            PollInvocationAttempt attempt,
            List<PollSearchSource> sources,
            List<PollCitation> citations,
            Map<Long, PollSearchSource> sourcesById,
            String platformName
    ) {
        ManualQuestionPollBatchView.ResultDetail detail = new ManualQuestionPollBatchView.ResultDetail();
        detail.setPollResultId(result.getId());
        detail.setPlatformId(result.getPlatformId());
        detail.setPlatformCode(result.getPlatformCode());
        detail.setPlatformName(platformName == null ? result.getPlatformCode() : platformName);
        detail.setQuestion(result.getKeywordTextSnapshot());
        detail.setStatus(result.getStatus());
        detail.setResultCode(result.getResultCode());
        detail.setRequestCount(result.getRequestCount());
        detail.setResponseTimeMs(result.getResponseTimeMs());
        detail.setExecutionFinalized(result.getExecutionFinalized());
        detail.setSearchStatus(result.getSearchStatus());
        detail.setSearchTriggered(result.getSearchTriggered());
        detail.setConfirmedCitationExposure(result.getConfirmedCitationExposure());
        if (attempt != null) {
            detail.setAnswer(attempt.getAnswer());
            detail.setErrorCategory(attempt.getErrorCategory());
            detail.setErrorMessage(attempt.getErrorMessage());
            detail.setLatencyMs(attempt.getLatencyMs());
        }
        detail.setSources(sources.stream().map(this::toSourceDetail).toList());
        detail.setCitations(citations.stream()
                .map(citation -> toCitationDetail(citation, sourcesById.get(citation.getSourceId())))
                .toList());
        return detail;
    }

    private ManualQuestionPollBatchView.SourceDetail toSourceDetail(PollSearchSource source) {
        ManualQuestionPollBatchView.SourceDetail detail = new ManualQuestionPollBatchView.SourceDetail();
        detail.setSourceId(source.getId());
        detail.setRankNo(source.getRankNo());
        detail.setTitle(source.getTitle());
        detail.setUrl(source.getNormalizedUrl());
        detail.setDomain(source.getDomain());
        detail.setBrandMatched(source.getBrandMatched());
        detail.setBrandMatchStrength(source.getBrandMatchStrength());
        return detail;
    }

    private ManualQuestionPollBatchView.CitationDetail toCitationDetail(
            PollCitation citation,
            PollSearchSource source
    ) {
        ManualQuestionPollBatchView.CitationDetail detail = new ManualQuestionPollBatchView.CitationDetail();
        detail.setCitationIndex(citation.getCitationIndex());
        detail.setSourceId(citation.getSourceId());
        detail.setSourceTitle(source == null ? null : source.getTitle());
        detail.setSourceUrl(source == null ? null : source.getNormalizedUrl());
        detail.setAnswerStart(citation.getAnswerStart());
        detail.setAnswerEnd(citation.getAnswerEnd());
        detail.setConfidence(citation.getConfidence());
        detail.setValidationStatus(citation.getValidationStatus());
        return detail;
    }

    private List<ManualQuestionPollBatchView.PlatformProgress> buildPlatformProgress(List<PollBatchShard> shards) {
        Map<Long, List<PollBatchShard>> grouped = new LinkedHashMap<>();
        shards.forEach(shard -> grouped.computeIfAbsent(shard.getPlatformId(), ignored -> new ArrayList<>()).add(shard));
        return grouped.values().stream().map(rows -> {
            PollBatchShard first = rows.get(0);
            ManualQuestionPollBatchView.PlatformProgress progress = new ManualQuestionPollBatchView.PlatformProgress();
            progress.setPlatformId(first.getPlatformId());
            progress.setPlatformCode(first.getPlatformCode());
            progress.setChannelCode(first.getChannelCode());
            progress.setPlatformName(first.getPlatformName());
            progress.setShardCount(rows.size());
            progress.setReadyCount(countStatus(rows, "ready"));
            progress.setRunningCount(countStatus(rows, "running"));
            progress.setCompletedShardCount(countStatus(rows, "completed"));
            progress.setFailedShardCount(countStatus(rows, "failed"));
            progress.setExpectedCount(sum(rows, PollBatchShard::getExpectedCount));
            progress.setCompletedCount(sum(rows, PollBatchShard::getCompletedCount));
            progress.setFailedCount(sum(rows, PollBatchShard::getFailedCount));
            progress.setResourceWaitCount(sum(rows, PollBatchShard::getResourceWaitCount));
            return progress;
        }).toList();
    }

    private int countStatus(List<PollBatchShard> rows, String status) {
        return (int) rows.stream().filter(row -> status.equals(row.getStatus())).count();
    }

    private int sum(List<PollBatchShard> rows,
                    java.util.function.Function<PollBatchShard, Integer> extractor) {
        return rows.stream().map(extractor).mapToInt(value -> value == null ? 0 : value).sum();
    }

    private boolean isEffectiveSearchConfirmed(PollResult result) {
        return isEffectiveWebResult(result) && Boolean.TRUE.equals(result.getSearchTriggered());
    }

    private boolean isEffectiveCitationExposure(PollResult result) {
        return isEffectiveWebResult(result) && Boolean.TRUE.equals(result.getConfirmedCitationExposure());
    }

    private boolean isEffectiveWebResult(PollResult result) {
        return Boolean.TRUE.equals(result.getExecutionFinalized())
                && result.getEffectiveAttemptId() != null
                && Boolean.TRUE.equals(result.getSearchRequested());
    }

    private List<Long> normalizePlatformIds(List<Long> platformIds) {
        return platformIds.stream().distinct().sorted(Comparator.naturalOrder()).toList();
    }

    private String normalizeRequestId(String requestId) {
        String normalized = requestId == null ? "" : requestId.trim();
        try {
            UUID.fromString(normalized);
        } catch (IllegalArgumentException ex) {
            throw new BizException(400, "clientRequestId must be a UUID", 400, null);
        }
        return normalized;
    }

    private String fingerprint(Long projectId, String tier, List<Long> platformIds, Integer questionLimit) {
        String canonical = projectId + "|" + tier + "|"
                + platformIds.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("")
                + "|" + questionLimit;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                value.append(String.format("%02x", b));
            }
            return value.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
