package com.huanjing.geo.module.presale.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.llm.pool.LlmPermitUnavailableException;
import com.huanjing.geo.module.presale.dto.request.PresaleIndustryBucketDraftUpdateRequest;
import com.huanjing.geo.module.presale.dto.request.PresaleIndustryBucketMappingUpdateRequest;
import com.huanjing.geo.module.presale.dto.request.PresaleIndustryBucketRejectRequest;
import com.huanjing.geo.module.presale.dto.request.PresaleHeatmapSummaryUpdateRequest;
import com.huanjing.geo.module.presale.dto.request.PresaleLexiconBucketCreateRequest;
import com.huanjing.geo.module.presale.dto.request.PresaleLexiconBucketUpdateRequest;
import com.huanjing.geo.module.presale.dto.request.PresaleNarrativeFindingCopyUpdateRequest;
import com.huanjing.geo.module.presale.dto.response.PresaleNarrativeConfigAdminResponse;
import com.huanjing.geo.module.presale.generate.PresaleEvaluationModelRouter;
import com.huanjing.geo.module.presale.generate.llm.LexiconBucketClassificationPromptTemplates;
import com.huanjing.geo.module.presale.generate.llm.LlmCallResult;
import com.huanjing.geo.module.presale.generate.llm.LlmInvokeException;
import com.huanjing.geo.module.presale.generate.llm.PlatformCallContext;
import com.huanjing.geo.module.presale.generate.llm.PresaleLlmInvoker;
import com.huanjing.geo.module.presale.generate.narrative.IndustryKeyNormalizer;
import com.huanjing.geo.module.presale.persist.entity.PresaleHeatmapSummary;
import com.huanjing.geo.module.presale.persist.entity.PresaleIndustryBucketMapping;
import com.huanjing.geo.module.presale.persist.entity.PresaleIndustryBucketReviewTask;
import com.huanjing.geo.module.presale.persist.entity.PresaleLexiconBucket;
import com.huanjing.geo.module.presale.persist.entity.PresaleNarrativeFindingCopy;
import com.huanjing.geo.module.presale.persist.mapper.PresaleHeatmapSummaryMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleIndustryBucketMappingMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleIndustryBucketReviewTaskMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleLexiconBucketMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleNarrativeFindingCopyMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.ActivityLogService;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PresaleNarrativeConfigAdminService {

    private static final Set<String> MANAGE_ROLES = Set.of("delivery_manager", "manager", "super_admin");
    private static final String DEFAULT_CONFIG_VERSION = "v1";
    private static final Pattern SLOT_PATTERN = Pattern.compile("\\{\\{\\s*([a-z_]+)\\s*}}");
    private static final Pattern FORBIDDEN_CLAIM_PATTERN = Pattern.compile(
            "保证|保底|必然|排名第?一|第?一名|全市第?一|全城第?一|行业第?一|区域第?一|本地第?一|100%|百分百"
    );
    private static final Pattern BAD_SHORT_TEXT_PATTERN = Pattern.compile("[0-9０-９%％]|[。！？!?；;]");
    private static final Set<String> ACTIVE_REVIEW_STATUSES = Set.of("PENDING", "DRAFTED");
    private static final Set<String> ALLOWED_FINDING_SLOTS = Set.of(
            "brand_name",
            "customer_term",
            "conversion_term",
            "industry_short",
            "loss_phrase",
            "scene_example",
            "overall_score",
            "coverage_score",
            "recommendation_rate",
            "neutral_share",
            "positive_share",
            "competitor_names",
            "competitor_preferred_rate",
            "cognitive_score",
            "negative_count",
            "negative_evidence_count",
            "key_topic",
            "affected_platform_count",
            "affected_platforms_text",
            "weak_platforms",
            "high_value_covered",
            "high_value_total"
    );

    private final PresaleNarrativeFindingCopyMapper findingCopyMapper;
    private final PresaleHeatmapSummaryMapper heatmapSummaryMapper;
    private final PresaleLexiconBucketMapper lexiconBucketMapper;
    private final PresaleIndustryBucketMappingMapper industryBucketMappingMapper;
    private final PresaleIndustryBucketReviewTaskMapper industryBucketReviewTaskMapper;
    private final PresaleLlmInvoker llmInvoker;
    private final PresaleEvaluationModelRouter evaluationModelRouter;
    private final ObjectMapper objectMapper;
    private final CurrentUserService currentUserService;
    private final ActivityLogService activityLogService;

    public PresaleNarrativeConfigAdminResponse getConfig() {
        ensureManagerRole();
        return PresaleNarrativeConfigAdminResponse.builder()
                .configVersion(DEFAULT_CONFIG_VERSION)
                .findingCopies(loadFindingCopies())
                .heatmapSummaries(loadHeatmapSummaries())
                .lexiconBuckets(loadLexiconBuckets())
                .industryBucketMappings(loadIndustryBucketMappings())
                .lexiconReviewTasks(loadLexiconReviewTasks())
                .build();
    }

    @Transactional
    public PresaleNarrativeFindingCopy updateFindingCopy(Long id, PresaleNarrativeFindingCopyUpdateRequest req) {
        SysUser operator = ensureManagerRole();
        PresaleNarrativeFindingCopy row = findingCopyMapper.selectById(id);
        if (row == null) {
            throw new BizException(404, "Finding copy config not found");
        }
        validateFindingCopy(req);
        Map<String, Object> before = findingCopySnapshot(row);
        row.setTitleTemplate(req.getTitleTemplate().trim());
        row.setBodyTemplate(req.getBodyTemplate().trim());
        row.setEvidenceTemplate(req.getEvidenceTemplate().trim());
        row.setPriority(req.getPriority());
        row.setEnabled(req.getEnabled());
        row.setRemark(trimToNull(req.getRemark()));
        findingCopyMapper.updateById(row);
        activityLogService.logAction(
                operator.getId(),
                "presale.narrative_config.finding_copy.update",
                "presale_narrative_finding_copy",
                row.getId(),
                before,
                findingCopySnapshot(row),
                null
        );
        return row;
    }

    @Transactional
    public PresaleHeatmapSummary updateHeatmapSummary(Long id, PresaleHeatmapSummaryUpdateRequest req) {
        SysUser operator = ensureManagerRole();
        PresaleHeatmapSummary row = heatmapSummaryMapper.selectById(id);
        if (row == null) {
            throw new BizException(404, "Heatmap summary config not found");
        }
        validateHeatmapSummary(req);
        Map<String, Object> before = heatmapSummarySnapshot(row);
        row.setSummaryTemplate(req.getSummaryTemplate().trim());
        row.setColorLegendTemplate(req.getColorLegendTemplate().trim());
        row.setSortOrder(req.getSortOrder());
        row.setEnabled(req.getEnabled());
        row.setRemark(trimToNull(req.getRemark()));
        heatmapSummaryMapper.updateById(row);
        activityLogService.logAction(
                operator.getId(),
                "presale.narrative_config.heatmap_summary.update",
                "presale_heatmap_summary",
                row.getId(),
                before,
                heatmapSummarySnapshot(row),
                null
        );
        return row;
    }

    @Transactional
    public PresaleIndustryBucketReviewTask draftIndustryBucket(Long id) {
        SysUser operator = ensureManagerRole();
        PresaleIndustryBucketReviewTask task = requireReviewTask(id);
        ensureTaskEditable(task);
        List<PresaleLexiconBucket> buckets = enabledBuckets();
        if (buckets.isEmpty()) {
            throw new BizException(400, "No enabled lexicon bucket available");
        }
        List<PlatformCallContext> contexts = evaluationModelRouter.routeContexts(
                new PlatformCallContext(null, 1, "", null, "", "presale-lexicon", operator.getId(), true)
        );
        if (contexts.isEmpty()) {
            throw new BizException(503, "No available evaluation model for lexicon bucket draft");
        }
        String prompt = LexiconBucketClassificationPromptTemplates.renderUserPrompt(
                task.getIndustry(),
                task.getIndustryKey(),
                toBucketOptionsJson(buckets)
        );
        Exception last = null;
        for (PlatformCallContext ctx : contexts) {
            try {
                LlmCallResult result = llmInvoker.classifyIndustryBucket(ctx, prompt);
                PresaleIndustryBucketDraftUpdateRequest draft = parseDraft(result.rawResponse());
                validateDraftPayload(draft, buckets);
                return saveDraft(task, draft, operator, "LLM_CLASSIFIER");
            } catch (LlmPermitUnavailableException ex) {
                last = ex;
            } catch (LlmInvokeException ex) {
                last = ex;
            }
        }
        throw new BizException(503, last == null ? "LLM draft failed" : last.getMessage());
    }

    @Transactional
    public PresaleIndustryBucketReviewTask updateIndustryBucketDraft(Long id, PresaleIndustryBucketDraftUpdateRequest req) {
        SysUser operator = ensureManagerRole();
        PresaleIndustryBucketReviewTask task = requireReviewTask(id);
        ensureTaskEditable(task);
        validateDraftPayload(req, enabledBuckets());
        return saveDraft(task, req, operator, "MANUAL");
    }

    @Transactional
    public PresaleIndustryBucketReviewTask approveIndustryBucketTask(Long id) {
        SysUser operator = ensureManagerRole();
        PresaleIndustryBucketReviewTask task = requireReviewTask(id);
        if (!"DRAFTED".equals(task.getStatus())) {
            throw new BizException(400, "Only DRAFTED lexicon bucket tasks can be approved");
        }
        PresaleIndustryBucketDraftUpdateRequest draft = parseDraft(task.getDraftJson());
        validateDraftPayload(draft, enabledBuckets());
        if (Boolean.TRUE.equals(draft.getSuggestNewBucket())) {
            throw new BizException(400, "Suggested new bucket draft cannot be approved directly");
        }
        PresaleLexiconBucket bucket = selectEnabledBucket(draft.getBucketCode())
                .orElseThrow(() -> new BizException(400, "Bucket not found or disabled"));
        String industryKey = normalizeTaskKey(task);
        PresaleIndustryBucketMapping mapping = selectMapping(industryKey);
        Map<String, Object> before = mapping == null ? Map.of() : industryMappingSnapshot(mapping);
        if (mapping == null) {
            mapping = new PresaleIndustryBucketMapping();
            mapping.setIndustry(task.getIndustry());
            mapping.setIndustryKey(industryKey);
            mapping.setConfigVersion(DEFAULT_CONFIG_VERSION);
        }
        mapping.setBucketCode(bucket.getBucketCode());
        mapping.setIndustryShort(trimToNull(draft.getIndustryShort()));
        mapping.setApproved(Boolean.TRUE);
        mapping.setSource("APPROVED_TASK");
        mapping.setOriginTaskId(task.getId());
        mapping.setApprovedBy(operator.getId());
        mapping.setApprovedAt(java.time.LocalDateTime.now());
        mapping.setRemark(trimToNull(draft.getReason()));
        if (mapping.getId() == null) {
            industryBucketMappingMapper.insert(mapping);
        } else {
            industryBucketMappingMapper.updateById(mapping);
        }
        task.setStatus("APPROVED");
        task.setApprovedBy(operator.getId());
        task.setApprovedAt(java.time.LocalDateTime.now());
        industryBucketReviewTaskMapper.updateById(task);
        activityLogService.logAction(
                operator.getId(),
                "presale.narrative_config.industry_bucket_task.approve",
                "presale_industry_bucket_mapping",
                mapping.getId(),
                before,
                industryMappingSnapshot(mapping),
                null
        );
        return task;
    }

    @Transactional
    public PresaleIndustryBucketReviewTask rejectIndustryBucketTask(Long id, PresaleIndustryBucketRejectRequest req) {
        SysUser operator = ensureManagerRole();
        PresaleIndustryBucketReviewTask task = requireReviewTask(id);
        if (!ACTIVE_REVIEW_STATUSES.contains(task.getStatus())) {
            throw new BizException(400, "Only active lexicon bucket tasks can be rejected");
        }
        task.setStatus("REJECTED");
        task.setRejectReason(trimToNull(req == null ? null : req.getReason()));
        task.setRejectedBy(operator.getId());
        task.setRejectedAt(java.time.LocalDateTime.now());
        industryBucketReviewTaskMapper.updateById(task);
        return task;
    }

    @Transactional
    public PresaleLexiconBucket createLexiconBucket(PresaleLexiconBucketCreateRequest req) {
        SysUser operator = ensureManagerRole();
        String bucketCode = req.getBucketCode().trim();
        if (selectBucket(bucketCode).isPresent()) {
            throw new BizException(400, "Lexicon bucket code already exists");
        }
        validateBucketTerms(req.getCustomerTerm(), req.getConversionTerm(), req.getDefaultIndustryShort());

        PresaleLexiconBucket row = new PresaleLexiconBucket();
        row.setBucketCode(bucketCode);
        row.setBucketName(req.getBucketName().trim());
        row.setCustomerTerm(req.getCustomerTerm().trim());
        row.setConversionTerm(req.getConversionTerm().trim());
        row.setDefaultIndustryShort(trimToNull(req.getDefaultIndustryShort()));
        row.setEnabled(req.getEnabled());
        row.setSource("MANUAL");
        row.setConfigVersion(DEFAULT_CONFIG_VERSION);
        row.setRemark(trimToNull(req.getRemark()));
        lexiconBucketMapper.insert(row);
        activityLogService.logAction(
                operator.getId(),
                "presale.narrative_config.lexicon_bucket.create",
                "presale_lexicon_bucket",
                row.getId(),
                Map.of(),
                bucketSnapshot(row),
                null
        );
        return row;
    }

    @Transactional
    public PresaleLexiconBucket updateLexiconBucket(Long id, PresaleLexiconBucketUpdateRequest req) {
        SysUser operator = ensureManagerRole();
        PresaleLexiconBucket row = lexiconBucketMapper.selectById(id);
        if (row == null) {
            throw new BizException(404, "Lexicon bucket not found");
        }
        validateBucketTerms(req.getCustomerTerm(), req.getConversionTerm(), req.getDefaultIndustryShort());
        if (Boolean.FALSE.equals(req.getEnabled()) && hasApprovedMappings(row.getBucketCode())) {
            throw new BizException(400, "Cannot disable bucket while approved industry mappings still reference it");
        }
        Map<String, Object> before = bucketSnapshot(row);
        row.setBucketName(req.getBucketName().trim());
        row.setCustomerTerm(req.getCustomerTerm().trim());
        row.setConversionTerm(req.getConversionTerm().trim());
        row.setDefaultIndustryShort(trimToNull(req.getDefaultIndustryShort()));
        row.setEnabled(req.getEnabled());
        row.setSource("MANUAL");
        row.setRemark(trimToNull(req.getRemark()));
        lexiconBucketMapper.updateById(row);
        activityLogService.logAction(
                operator.getId(),
                "presale.narrative_config.lexicon_bucket.update",
                "presale_lexicon_bucket",
                row.getId(),
                before,
                bucketSnapshot(row),
                null
        );
        return row;
    }

    @Transactional
    public PresaleIndustryBucketMapping updateIndustryBucketMapping(Long id,
                                                                    PresaleIndustryBucketMappingUpdateRequest req) {
        SysUser operator = ensureManagerRole();
        PresaleIndustryBucketMapping row = industryBucketMappingMapper.selectById(id);
        if (row == null) {
            throw new BizException(404, "Industry bucket mapping not found");
        }
        PresaleLexiconBucket bucket = selectEnabledBucket(req.getBucketCode())
                .orElseThrow(() -> new BizException(400, "Bucket not found or disabled"));
        validateShortText("industryShort", req.getIndustryShort(), 50, true);
        validateNoForbiddenClaim(req.getRemark());

        Map<String, Object> before = industryMappingSnapshot(row);
        row.setBucketCode(bucket.getBucketCode());
        row.setIndustryShort(trimToNull(req.getIndustryShort()));
        row.setApproved(Boolean.TRUE);
        row.setSource("MANUAL_MAPPING");
        row.setOriginTaskId(null);
        row.setApprovedBy(operator.getId());
        row.setApprovedAt(java.time.LocalDateTime.now());
        row.setRemark(trimToNull(req.getRemark()));
        industryBucketMappingMapper.updateById(row);
        activityLogService.logAction(
                operator.getId(),
                "presale.narrative_config.industry_bucket_mapping.update",
                "presale_industry_bucket_mapping",
                row.getId(),
                before,
                industryMappingSnapshot(row),
                null
        );
        return row;
    }

    private List<PresaleNarrativeFindingCopy> loadFindingCopies() {
        LambdaQueryWrapper<PresaleNarrativeFindingCopy> q = new LambdaQueryWrapper<>();
        q.eq(PresaleNarrativeFindingCopy::getConfigVersion, DEFAULT_CONFIG_VERSION);
        q.orderByAsc(PresaleNarrativeFindingCopy::getCode);
        q.orderByAsc(PresaleNarrativeFindingCopy::getTier);
        q.orderByAsc(PresaleNarrativeFindingCopy::getPriority);
        q.orderByAsc(PresaleNarrativeFindingCopy::getId);
        List<PresaleNarrativeFindingCopy> rows = findingCopyMapper.selectList(q);
        return rows == null ? List.of() : rows;
    }

    private void validateFindingCopy(PresaleNarrativeFindingCopyUpdateRequest req) {
        validateNoForbiddenClaim(req.getTitleTemplate());
        validateNoForbiddenClaim(req.getBodyTemplate());
        validateNoForbiddenClaim(req.getEvidenceTemplate());
        validateSlots("titleTemplate", req.getTitleTemplate(), ALLOWED_FINDING_SLOTS);
        validateSlots("bodyTemplate", req.getBodyTemplate(), ALLOWED_FINDING_SLOTS);
        validateSlots("evidenceTemplate", req.getEvidenceTemplate(), ALLOWED_FINDING_SLOTS);
    }

    private void validateHeatmapSummary(PresaleHeatmapSummaryUpdateRequest req) {
        validateNoForbiddenClaim(req.getSummaryTemplate());
        validateNoForbiddenClaim(req.getColorLegendTemplate());
        validateSlots("summaryTemplate", req.getSummaryTemplate(), Set.of());
        validateSlots("colorLegendTemplate", req.getColorLegendTemplate(), Set.of());
    }

    private void validateNoForbiddenClaim(String text) {
        if (text != null && FORBIDDEN_CLAIM_PATTERN.matcher(text).find()) {
            throw new BizException(400, "Template contains forbidden absolute claim words");
        }
    }

    private void validateSlots(String fieldName, String template, Set<String> allowedSlots) {
        if (template == null || template.isBlank()) {
            return;
        }
        Matcher matcher = SLOT_PATTERN.matcher(template);
        Set<String> invalid = new HashSet<>();
        while (matcher.find()) {
            String slot = matcher.group(1);
            if (!allowedSlots.contains(slot)) {
                invalid.add(slot);
            }
        }
        if (!invalid.isEmpty()) {
            throw new BizException(400, "Invalid template slots in " + fieldName + ": " + String.join(",", invalid));
        }
    }

    private List<PresaleHeatmapSummary> loadHeatmapSummaries() {
        LambdaQueryWrapper<PresaleHeatmapSummary> q = new LambdaQueryWrapper<>();
        q.eq(PresaleHeatmapSummary::getConfigVersion, DEFAULT_CONFIG_VERSION);
        q.orderByAsc(PresaleHeatmapSummary::getSortOrder);
        q.orderByAsc(PresaleHeatmapSummary::getId);
        List<PresaleHeatmapSummary> rows = heatmapSummaryMapper.selectList(q);
        return rows == null ? List.of() : rows;
    }

    private List<PresaleLexiconBucket> loadLexiconBuckets() {
        LambdaQueryWrapper<PresaleLexiconBucket> q = new LambdaQueryWrapper<>();
        q.eq(PresaleLexiconBucket::getConfigVersion, DEFAULT_CONFIG_VERSION);
        q.orderByDesc(PresaleLexiconBucket::getEnabled);
        q.orderByAsc(PresaleLexiconBucket::getBucketCode);
        List<PresaleLexiconBucket> rows = lexiconBucketMapper.selectList(q);
        return rows == null ? List.of() : rows;
    }

    private List<PresaleIndustryBucketMapping> loadIndustryBucketMappings() {
        LambdaQueryWrapper<PresaleIndustryBucketMapping> q = new LambdaQueryWrapper<>();
        q.eq(PresaleIndustryBucketMapping::getConfigVersion, DEFAULT_CONFIG_VERSION);
        q.orderByAsc(PresaleIndustryBucketMapping::getIndustryKey);
        q.orderByDesc(PresaleIndustryBucketMapping::getUpdatedAt);
        List<PresaleIndustryBucketMapping> rows = industryBucketMappingMapper.selectList(q);
        return rows == null ? List.of() : rows;
    }

    private List<PresaleIndustryBucketReviewTask> loadLexiconReviewTasks() {
        LambdaQueryWrapper<PresaleIndustryBucketReviewTask> q = new LambdaQueryWrapper<>();
        q.orderByAsc(PresaleIndustryBucketReviewTask::getStatus);
        q.orderByDesc(PresaleIndustryBucketReviewTask::getUpdatedAt);
        q.orderByDesc(PresaleIndustryBucketReviewTask::getId);
        List<PresaleIndustryBucketReviewTask> rows = industryBucketReviewTaskMapper.selectList(q);
        return rows == null ? List.of() : rows;
    }

    private PresaleIndustryBucketReviewTask requireReviewTask(Long id) {
        PresaleIndustryBucketReviewTask task = industryBucketReviewTaskMapper.selectById(id);
        if (task == null) {
            throw new BizException(404, "Lexicon bucket review task not found");
        }
        return task;
    }

    private void ensureTaskEditable(PresaleIndustryBucketReviewTask task) {
        if (!ACTIVE_REVIEW_STATUSES.contains(task.getStatus())) {
            throw new BizException(400, "Only active lexicon bucket tasks can be edited");
        }
    }

    private List<PresaleLexiconBucket> enabledBuckets() {
        LambdaQueryWrapper<PresaleLexiconBucket> q = new LambdaQueryWrapper<>();
        q.eq(PresaleLexiconBucket::getConfigVersion, DEFAULT_CONFIG_VERSION);
        q.eq(PresaleLexiconBucket::getEnabled, Boolean.TRUE);
        q.orderByAsc(PresaleLexiconBucket::getBucketCode);
        List<PresaleLexiconBucket> rows = lexiconBucketMapper.selectList(q);
        return rows == null ? List.of() : rows;
    }

    private Optional<PresaleLexiconBucket> selectEnabledBucket(String bucketCode) {
        if (bucketCode == null || bucketCode.isBlank()) {
            return Optional.empty();
        }
        LambdaQueryWrapper<PresaleLexiconBucket> q = new LambdaQueryWrapper<>();
        q.eq(PresaleLexiconBucket::getConfigVersion, DEFAULT_CONFIG_VERSION);
        q.eq(PresaleLexiconBucket::getBucketCode, bucketCode.trim());
        q.eq(PresaleLexiconBucket::getEnabled, Boolean.TRUE);
        q.last("LIMIT 1");
        return Optional.ofNullable(lexiconBucketMapper.selectOne(q));
    }

    private Optional<PresaleLexiconBucket> selectBucket(String bucketCode) {
        if (bucketCode == null || bucketCode.isBlank()) {
            return Optional.empty();
        }
        LambdaQueryWrapper<PresaleLexiconBucket> q = new LambdaQueryWrapper<>();
        q.eq(PresaleLexiconBucket::getConfigVersion, DEFAULT_CONFIG_VERSION);
        q.eq(PresaleLexiconBucket::getBucketCode, bucketCode.trim());
        q.last("LIMIT 1");
        return Optional.ofNullable(lexiconBucketMapper.selectOne(q));
    }

    private PresaleIndustryBucketMapping selectMapping(String industryKey) {
        LambdaQueryWrapper<PresaleIndustryBucketMapping> q = new LambdaQueryWrapper<>();
        q.eq(PresaleIndustryBucketMapping::getConfigVersion, DEFAULT_CONFIG_VERSION);
        q.eq(PresaleIndustryBucketMapping::getIndustryKey, industryKey);
        q.last("LIMIT 1");
        return industryBucketMappingMapper.selectOne(q);
    }

    private boolean hasApprovedMappings(String bucketCode) {
        LambdaQueryWrapper<PresaleIndustryBucketMapping> q = new LambdaQueryWrapper<>();
        q.eq(PresaleIndustryBucketMapping::getConfigVersion, DEFAULT_CONFIG_VERSION);
        q.eq(PresaleIndustryBucketMapping::getBucketCode, bucketCode);
        q.eq(PresaleIndustryBucketMapping::getApproved, Boolean.TRUE);
        return industryBucketMappingMapper.selectCount(q) > 0;
    }

    private PresaleIndustryBucketReviewTask saveDraft(PresaleIndustryBucketReviewTask task,
                                                      PresaleIndustryBucketDraftUpdateRequest draft,
                                                      SysUser operator,
                                                      String draftSource) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("bucket_code", draft.getBucketCode().trim());
            payload.put("industry_short", trimToNull(draft.getIndustryShort()));
            payload.put("suggest_new_bucket", Boolean.TRUE.equals(draft.getSuggestNewBucket()));
            payload.put("reason", trimToNull(draft.getReason()));
            task.setDraftJson(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            throw new BizException(400, "Failed to serialize lexicon bucket draft");
        }
        task.setStatus("DRAFTED");
        task.setDraftSource(draftSource);
        task.setDraftedBy(operator.getId());
        task.setDraftedAt(java.time.LocalDateTime.now());
        industryBucketReviewTaskMapper.updateById(task);
        return task;
    }

    private PresaleIndustryBucketDraftUpdateRequest parseDraft(String draftJson) {
        if (draftJson == null || draftJson.isBlank()) {
            throw new BizException(400, "Lexicon bucket draft is empty");
        }
        try {
            JsonNode root = objectMapper.readTree(draftJson);
            if (root.has("customer_term") || root.has("conversion_term")) {
                throw new BizException(400, "Draft must not contain customer_term/conversion_term");
            }
            PresaleIndustryBucketDraftUpdateRequest out = new PresaleIndustryBucketDraftUpdateRequest();
            out.setBucketCode(root.path("bucket_code").asText(null));
            out.setIndustryShort(root.path("industry_short").asText(null));
            out.setSuggestNewBucket(root.path("suggest_new_bucket").asBoolean(false));
            out.setReason(root.path("reason").asText(null));
            return out;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(400, "Invalid lexicon bucket draft JSON");
        }
    }

    private void validateDraftPayload(PresaleIndustryBucketDraftUpdateRequest draft, List<PresaleLexiconBucket> buckets) {
        if (draft == null || draft.getBucketCode() == null || draft.getBucketCode().isBlank()) {
            throw new BizException(400, "bucket_code is required");
        }
        boolean known = buckets.stream().anyMatch(bucket -> draft.getBucketCode().trim().equals(bucket.getBucketCode()));
        if (!known) {
            throw new BizException(400, "bucket_code must reference an enabled bucket");
        }
        validateShortText("industry_short", draft.getIndustryShort(), 50, true);
        validateNoForbiddenClaim(draft.getReason());
    }

    private void validateBucketTerms(String customerTerm, String conversionTerm, String industryShort) {
        validateShortText("customerTerm", customerTerm, 20, false);
        validateShortText("conversionTerm", conversionTerm, 20, false);
        validateShortText("defaultIndustryShort", industryShort, 50, true);
    }

    private void validateShortText(String fieldName, String value, int maxLength, boolean nullable) {
        if (!StringUtils.hasText(value)) {
            if (nullable) {
                return;
            }
            throw new BizException(400, fieldName + " is required");
        }
        String text = value.trim();
        if (text.length() > maxLength) {
            throw new BizException(400, fieldName + " is too long");
        }
        validateNoForbiddenClaim(text);
        if (BAD_SHORT_TEXT_PATTERN.matcher(text).find() || text.contains("{{") || text.contains("}}")) {
            throw new BizException(400, fieldName + " contains invalid characters");
        }
    }

    private String toBucketOptionsJson(List<PresaleLexiconBucket> buckets) {
        try {
            List<Map<String, Object>> payload = buckets.stream()
                    .map(bucket -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("bucket_code", bucket.getBucketCode());
                        row.put("bucket_name", bucket.getBucketName());
                        row.put("customer_term", bucket.getCustomerTerm());
                        row.put("conversion_term", bucket.getConversionTerm());
                        row.put("default_industry_short", bucket.getDefaultIndustryShort());
                        return row;
                    })
                    .toList();
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String normalizeTaskKey(PresaleIndustryBucketReviewTask task) {
        String key = IndustryKeyNormalizer.normalize(task.getIndustry());
        if (StringUtils.hasText(task.getIndustryKey()) && task.getIndustryKey().equals(key)) {
            return task.getIndustryKey();
        }
        if (StringUtils.hasText(task.getIndustryKey()) && !StringUtils.hasText(key)) {
            return task.getIndustryKey();
        }
        task.setIndustryKey(key);
        return key;
    }

    private SysUser ensureManagerRole() {
        SysUser user = currentUserService.requireCurrentUser();
        String role = user.getRole() == null ? "" : user.getRole();
        if (!MANAGE_ROLES.contains(role)) {
            throw new BizException(403, "No permission to manage narrative config");
        }
        return user;
    }

    private Map<String, Object> findingCopySnapshot(PresaleNarrativeFindingCopy row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", row.getCode());
        out.put("tier", row.getTier());
        out.put("bandOverride", row.getBandOverride());
        out.put("archetypeOverride", row.getArchetypeOverride());
        out.put("titleTemplate", row.getTitleTemplate());
        out.put("bodyTemplate", row.getBodyTemplate());
        out.put("evidenceTemplate", row.getEvidenceTemplate());
        out.put("priority", row.getPriority());
        out.put("enabled", row.getEnabled());
        out.put("remark", row.getRemark());
        return out;
    }

    private Map<String, Object> heatmapSummarySnapshot(PresaleHeatmapSummary row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("heatmapPattern", row.getHeatmapPattern());
        out.put("bandOverride", row.getBandOverride());
        out.put("summaryTemplate", row.getSummaryTemplate());
        out.put("colorLegendTemplate", row.getColorLegendTemplate());
        out.put("sortOrder", row.getSortOrder());
        out.put("enabled", row.getEnabled());
        out.put("remark", row.getRemark());
        return out;
    }

    private Map<String, Object> bucketSnapshot(PresaleLexiconBucket row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("bucketCode", row.getBucketCode());
        out.put("bucketName", row.getBucketName());
        out.put("customerTerm", row.getCustomerTerm());
        out.put("conversionTerm", row.getConversionTerm());
        out.put("defaultIndustryShort", row.getDefaultIndustryShort());
        out.put("enabled", row.getEnabled());
        out.put("source", row.getSource());
        out.put("remark", row.getRemark());
        return out;
    }

    private Map<String, Object> industryMappingSnapshot(PresaleIndustryBucketMapping row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("industry", row.getIndustry());
        out.put("industryKey", row.getIndustryKey());
        out.put("bucketCode", row.getBucketCode());
        out.put("industryShort", row.getIndustryShort());
        out.put("approved", row.getApproved());
        out.put("source", row.getSource());
        out.put("originTaskId", row.getOriginTaskId());
        out.put("approvedBy", row.getApprovedBy());
        out.put("approvedAt", row.getApprovedAt());
        out.put("remark", row.getRemark());
        return out;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
