package com.huanjing.geo.module.content.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.llm.LlmInvokeException;
import com.huanjing.geo.common.llm.LlmInvokeResult;
import com.huanjing.geo.common.llm.LlmInvoker;
import com.huanjing.geo.module.content.ContentErrorCodes;
import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.content.constant.ArticleTypes;
import com.huanjing.geo.module.content.constant.MedicalArticleConstants;
import com.huanjing.geo.module.content.constant.TemplatePerspectiveCodes;
import com.huanjing.geo.module.content.dto.BatchArticleGenerateRequest;
import com.huanjing.geo.module.content.dto.BatchArticleGenerateResponse;
import com.huanjing.geo.module.content.dto.BatchArticleGenerationDetailResponse;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.entity.ArticleDraftVersion;
import com.huanjing.geo.module.content.entity.ArticleGenerationLog;
import com.huanjing.geo.module.content.entity.ArticlePromptTemplate;
import com.huanjing.geo.module.content.entity.ArticlePromptTemplateVersion;
import com.huanjing.geo.module.content.entity.BatchArticleGenerationBatch;
import com.huanjing.geo.module.content.entity.BatchArticleGenerationTask;
import com.huanjing.geo.module.content.mapper.ArticleDraftMapper;
import com.huanjing.geo.module.content.mapper.ArticleDraftVersionMapper;
import com.huanjing.geo.module.content.mapper.ArticleGenerationLogMapper;
import com.huanjing.geo.module.content.mapper.ArticlePromptTemplateMapper;
import com.huanjing.geo.module.content.mapper.ArticlePromptTemplateVersionMapper;
import com.huanjing.geo.module.content.mapper.BatchArticleGenerationBatchMapper;
import com.huanjing.geo.module.content.mapper.BatchArticleGenerationTaskMapper;
import com.huanjing.geo.module.customer.access.BrandAccessAction;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.project.entity.KeywordGroup;
import com.huanjing.geo.module.project.entity.KeywordGroupResult;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.entity.ProjectKeywordGroupRel;
import com.huanjing.geo.module.project.mapper.KeywordGroupMapper;
import com.huanjing.geo.module.project.mapper.KeywordGroupResultMapper;
import com.huanjing.geo.module.project.mapper.ProjectKeywordGroupRelMapper;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import com.huanjing.geo.module.system.service.PlatformCredentialService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BatchArticleGenerationService {
    private static final Set<String> LEGACY_PROJECT_UPDATE_ROLES =
            Set.of("operator", "delivery_manager", "partner", "partner_staff");


    private static final int MAX_BATCH_ARTICLE_COUNT = 30;
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_RUNNING = "running";
    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_FAILED = "failed";
    private static final String STATUS_PARTIAL_SUCCESS = "partial_success";
    private static final String GENERATED_BY_BATCH_AI = "batch_ai";
    private static final String DEFAULT_ARTICLE_TYPE = ArticleTypes.INDUSTRY_ARTICLE;
    private static final String DEFAULT_LENGTH = "medium";
    private static final String TEMPLATE_SOURCE_SMART = "smart";
    private static final String TEMPLATE_SOURCE_WEIGHTED = "weighted";
    private static final String TEMPLATE_SOURCE_CUSTOM = "custom";
    private static final String TEMPLATE_SOURCE_FALLBACK_DEFAULT_PROMPT = "fallback_default_prompt";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String SMART_TEMPLATE_MATCH_SYSTEM_PROMPT = """
            你是文章提示词模板匹配器。你的任务是根据文章主题、渠道和可用模板摘要，选择最适合生成该主题文章的模板。

            规则：
            1. 只返回 JSON，不输出解释性文字。
            2. 每个 item 可选择 1-3 个模板。只选择 availableTemplates 中存在的 templateId。
            3. 如果主题包含“哪家、推荐、对比、性价比、口碑、排名、差在哪”，优先选择对比、推荐、选择指南类模板。
            4. 如果主题包含“怎么选、避坑、注意什么、陷阱”，优先选择指南、避坑、经验类模板。
            5. 如果主题包含“是什么、原理、标准、分类、应用”，优先选择知识库、科普类模板。
            6. 如果主题是明确问句，优先选择问答类模板。
            7. 如果传入 questionSceneCode，availableTemplates 已按该场景或通用模板过滤，应优先尊重场景语义。
            8. 如果无法判断，返回 fallback=true，并给出简短 reason。

            输出格式：
            {
              "items": [
                {
                  "unitKey": "u0_0",
                  "selectedTemplateIds": [1],
                  "reason": "主题包含对比和推荐意图",
                  "fallback": false
                }
              ]
            }
            """;
    private final ProjectMapper projectMapper;
    private final BrandMapper brandMapper;
    private final KeywordGroupMapper keywordGroupMapper;
    private final KeywordGroupResultMapper keywordGroupResultMapper;
    private final ProjectKeywordGroupRelMapper projectKeywordGroupRelMapper;
    private final ArticleDraftMapper articleDraftMapper;
    private final ArticleDraftVersionMapper articleDraftVersionMapper;
    private final ArticleGenerationLogMapper articleGenerationLogMapper;
    private final ArticlePromptTemplateMapper promptTemplateMapper;
    private final ArticlePromptTemplateVersionMapper promptTemplateVersionMapper;
    private final BatchArticleGenerationBatchMapper batchMapper;
    private final BatchArticleGenerationTaskMapper taskMapper;
    private final CurrentUserService currentUserService;
    private final BrandAccessService brandAccessService;
    private final PlatformCredentialService platformCredentialService;
    private final LlmInvoker llmInvoker;
    private final MarkdownImageReferenceValidator markdownImageReferenceValidator;
    private final ArticleAiDraftPromptFilter promptFilter;
    private final ArticleGenerationEngine articleGenerationEngine;
    private final ArticleModelResolver articleModelResolver;
    private final ArticleAutoImageInsertionService autoImageInsertionService;
    private final ArticleCoverSelectionService coverSelectionService;
    private final BatchArticlePromptBuilder promptBuilder;
    private final ArticleGenerationPromptContextFactory promptContextFactory;
    private final MedicalArticleGenerationService medicalArticleGenerationService;
    private final MedicalArticleComplianceChecker medicalComplianceChecker;
    private final SpecialIndustryComplianceAlertService specialIndustryComplianceAlertService;
    private final BatchArticleQualityChecker qualityChecker;
    private final ArticleTemplateAllocationService allocationService;
    private final TemplatePerspectiveService perspectiveService;
    private final QuestionScenePlatformSuggestionService suggestionService;
    private final ArticleGenerationReadinessService readinessService;
    private final ThirdPartySubjectRotationService subjectRotationService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final Executor articleAiDraftExecutor;

    public BatchArticleGenerationService(ProjectMapper projectMapper,
                                         BrandMapper brandMapper,
                                         KeywordGroupMapper keywordGroupMapper,
                                         KeywordGroupResultMapper keywordGroupResultMapper,
                                         ProjectKeywordGroupRelMapper projectKeywordGroupRelMapper,
                                         ArticleDraftMapper articleDraftMapper,
                                         ArticleDraftVersionMapper articleDraftVersionMapper,
                                         ArticleGenerationLogMapper articleGenerationLogMapper,
                                         ArticlePromptTemplateMapper promptTemplateMapper,
                                         ArticlePromptTemplateVersionMapper promptTemplateVersionMapper,
                                         BatchArticleGenerationBatchMapper batchMapper,
                                         BatchArticleGenerationTaskMapper taskMapper,
                                         CurrentUserService currentUserService,
                                         BrandAccessService brandAccessService,
                                         PlatformCredentialService platformCredentialService,
                                         LlmInvoker llmInvoker,
                                         MarkdownImageReferenceValidator markdownImageReferenceValidator,
                                         ArticleAiDraftPromptFilter promptFilter,
                                         ArticleGenerationEngine articleGenerationEngine,
                                         ArticleModelResolver articleModelResolver,
                                         ArticleAutoImageInsertionService autoImageInsertionService,
                                         ArticleCoverSelectionService coverSelectionService,
                                         BatchArticlePromptBuilder promptBuilder,
                                         ArticleGenerationPromptContextFactory promptContextFactory,
                                         MedicalArticleGenerationService medicalArticleGenerationService,
                                         MedicalArticleComplianceChecker medicalComplianceChecker,
                                         SpecialIndustryComplianceAlertService specialIndustryComplianceAlertService,
                                         BatchArticleQualityChecker qualityChecker,
                                         ArticleTemplateAllocationService allocationService,
                                         TemplatePerspectiveService perspectiveService,
                                         QuestionScenePlatformSuggestionService suggestionService,
                                         ArticleGenerationReadinessService readinessService,
                                         ThirdPartySubjectRotationService subjectRotationService,
                                         ObjectMapper objectMapper,
                                         PlatformTransactionManager transactionManager,
                                         @Qualifier("articleAiDraftExecutor") Executor articleAiDraftExecutor) {
        this.projectMapper = projectMapper;
        this.brandMapper = brandMapper;
        this.keywordGroupMapper = keywordGroupMapper;
        this.keywordGroupResultMapper = keywordGroupResultMapper;
        this.projectKeywordGroupRelMapper = projectKeywordGroupRelMapper;
        this.articleDraftMapper = articleDraftMapper;
        this.articleDraftVersionMapper = articleDraftVersionMapper;
        this.articleGenerationLogMapper = articleGenerationLogMapper;
        this.promptTemplateMapper = promptTemplateMapper;
        this.promptTemplateVersionMapper = promptTemplateVersionMapper;
        this.batchMapper = batchMapper;
        this.taskMapper = taskMapper;
        this.currentUserService = currentUserService;
        this.brandAccessService = brandAccessService;
        this.platformCredentialService = platformCredentialService;
        this.llmInvoker = llmInvoker;
        this.markdownImageReferenceValidator = markdownImageReferenceValidator;
        this.promptFilter = promptFilter;
        this.articleGenerationEngine = articleGenerationEngine;
        this.articleModelResolver = articleModelResolver;
        this.autoImageInsertionService = autoImageInsertionService;
        this.coverSelectionService = coverSelectionService;
        this.promptBuilder = promptBuilder;
        this.promptContextFactory = promptContextFactory;
        this.medicalArticleGenerationService = medicalArticleGenerationService;
        this.medicalComplianceChecker = medicalComplianceChecker;
        this.specialIndustryComplianceAlertService = specialIndustryComplianceAlertService;
        this.qualityChecker = qualityChecker;
        this.allocationService = allocationService;
        this.perspectiveService = perspectiveService;
        this.suggestionService = suggestionService;
        this.readinessService = readinessService;
        this.subjectRotationService = subjectRotationService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.articleAiDraftExecutor = articleAiDraftExecutor;
    }

    public BatchArticleGenerateResponse create(BatchArticleGenerateRequest req) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermissionOrLegacy("content.ai.generate", "project.update", LEGACY_PROJECT_UPDATE_ROLES);
        return createInternal(req, operator, true);
    }

    public BatchArticleGenerateResponse createSystemBatch(BatchArticleGenerateRequest req, Long operatorId) {
        SysUser operator = operatorId == null ? null : currentUserService.requireById(operatorId);
        return createInternal(req, operator, false);
    }

    private BatchArticleGenerateResponse createInternal(BatchArticleGenerateRequest req,
                                                        SysUser operator,
                                                        boolean checkAccess) {
        Project project = requireActiveProject(req.getProjectId());
        Brand brand = project.getBrandId() == null ? null : brandMapper.selectById(project.getBrandId());
        if (checkAccess) {
            currentUserService.ensurePartnerResourceAccess(operator, project.getPartnerId(), "project");
        }
        if (checkAccess && project.getBrandId() != null) {
            brandAccessService.requireBrandAccess(project.getBrandId(), operator.getId(), BrandAccessAction.OPERATE);
        }
        if (checkAccess && isThirdPartySourceBrand(project.getBrandId())) {
            throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "第三方信源项目只能通过自媒体自动排期生成文章");
        }

        String topicSource = StringUtils.hasText(req.getTopicSource()) ? req.getTopicSource().trim() : "manual";
        List<BatchArticleGenerateResponse.Notice> notices = new ArrayList<>();
        if (requestedArticleCount(req) > MAX_BATCH_ARTICLE_COUNT) {
            throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST,
                    "Single batch article generation count must be <= " + MAX_BATCH_ARTICLE_COUNT);
        }
        Map<String, TemplatePerspectiveService.ResolvedPerspective> perspectiveMemo = new HashMap<>();
        Map<String, SmartTemplateSelection> smartSelections = selectSmartTemplates(req, project.getBrandId(), perspectiveMemo);
        List<ValidatedTopic> topics = validateTopics(project.getId(), project.getBrandId(), topicSource, req.getTopics(),
                notices, smartSelections, perspectiveMemo);
        int totalCount = topics.stream()
                .flatMap(topic -> topic.platforms().stream())
                .mapToInt(platform -> platform.count() == null ? 0 : platform.count())
                .sum();
        if (totalCount <= 0) {
            throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "Article generation count must be > 0");
        }
        if (totalCount > MAX_BATCH_ARTICLE_COUNT) {
            throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST,
                    "Single batch article generation count must be <= " + MAX_BATCH_ARTICLE_COUNT);
        }
        String topicSummary = topics.stream().map(ValidatedTopic::topic).limit(10).reduce((a, b) -> a + "、" + b).orElse("");

        Long batchId = Objects.requireNonNull(transactionTemplate.execute(status -> {
            BatchArticleGenerationBatch batch = new BatchArticleGenerationBatch();
            batch.setProjectId(project.getId());
            batch.setCompanyId(project.getCompanyId());
            batch.setBrandId(project.getBrandId());
            batch.setTopicSource(topicSource);
            batch.setTopic(topicSummary);
            batch.setTopicAsQuestion(null);
            batch.setKeywordGroupId(topics.size() == 1 ? topics.get(0).keywordGroupId() : null);
            batch.setKeywordGroupName(topics.size() == 1 ? topics.get(0).keywordGroupName() : null);
            batch.setTotalCount(totalCount);
            batch.setSuccessCount(0);
            batch.setFailedCount(0);
            batch.setWarningCount(0);
            batch.setStatus(STATUS_PENDING);
            batch.setMedicalIndustryCode(topics.stream().map(ValidatedTopic::medicalIndustryCode).filter(StringUtils::hasText).findFirst().orElse(null));
            batch.setCreatedBy(operator == null ? null : operator.getId());
            batchMapper.insert(batch);

            int articleIndexInBatch = 1;
            for (int topicIndex = 0; topicIndex < topics.size(); topicIndex++) {
                ValidatedTopic topic = topics.get(topicIndex);
                int articleIndexInTopic = 1;
                for (ValidatedPlatform platform : topic.platforms()) {
                    for (int articleIndexInPlatform = 1; articleIndexInPlatform <= platform.count(); articleIndexInPlatform++) {
                        BatchArticleGenerationTask task = new BatchArticleGenerationTask();
                        ThirdPartySubjectRotationService.RotationResult subjectContext =
                                subjectRotationService.resolve(project, brand, platform.channelGroupCode(), platform.perspectiveCode());
                        task.setBatchId(batch.getId());
                        task.setProjectId(project.getId());
                        task.setSourceBrandId(subjectContext.sourceBrandId());
                        task.setSubjectBrandId(subjectContext.subjectBrandId());
                        task.setSubjectProjectId(subjectContext.subjectProjectId());
                        task.setRowNo(topicIndex + 1);
                        task.setArticleIndexInRow(articleIndexInTopic);
                        task.setArticleIndexInBatch(articleIndexInBatch);
                        task.setArticleType(resolveTaskArticleType(platform.articleTypeCode()));
                        task.setTone("");
                        task.setContentStyle(platform.contentStyle());
                        task.setChannelGroupCode(platform.channelGroupCode());
                        task.setChannelSubCode(platform.channelSubCode());
                        task.setAgentSiteModule(platform.agentSiteModule());
                        task.setArticleTypeCode(platform.articleTypeCode());
                        task.setPromptTemplateId(platform.templateId());
                        task.setPromptTemplateVersionId(platform.templateVersionId());
                        task.setPerspectiveCode(platform.perspectiveCode());
                        task.setPerspectiveMatchedScope(platform.perspectiveMatchedScope());
                        task.setPerspectiveMatchedConfigId(platform.perspectiveMatchedConfigId());
                        task.setAllocationMode(platform.allocationMode());
                        task.setTemplateSource(platform.templateSource());
                        task.setSuggestedPlatformCodes(writeJson(topic.suggestedPlatformCodes()));
                        task.setSelectedPlatformCodes(writeJson(topic.selectedPlatformCodes()));
                        List<String> warningCodes = readinessService.detectTaskReadinessWarningCodes(
                                topic.questionSceneCode(), platform.contactDisclosureMode(), brand);
                        List<String> confirmedCodes = warningCodes.stream()
                                .filter(topic.confirmedReadinessWarningCodes()::contains)
                                .toList();
                        task.setReadinessWarningCodes(warningCodes.isEmpty() ? null : writeJson(warningCodes));
                        task.setReadinessWarningConfirmed(!confirmedCodes.isEmpty());
                        task.setLength(DEFAULT_LENGTH);
                        task.setTopic(topic.topic());
                        task.setTopicAsQuestion(topic.topicAsQuestion());
                        task.setMedicalIndustryCode(topic.medicalIndustryCode());
                        task.setMedicalCategoryCode(topic.medicalCategoryCode());
                        task.setMedicalCategoryName(topic.medicalCategoryName());
                        task.setTopicAngleId(topic.topicAngleId());
                        task.setStructureSkeleton(topic.structureSkeleton());
                        task.setFocus(topic.focus());
                        task.setKeywordGroupId(topic.keywordGroupId());
                        task.setKeywordGroupName(topic.keywordGroupName());
                        task.setExtraPrompt(platform.extraPrompt());
                        task.setStatus(STATUS_PENDING);
                        task.setRetryCount(0);
                        taskMapper.insert(task);
                        articleIndexInTopic++;
                        articleIndexInBatch++;
                    }
                }
            }
            return batch.getId();
        }));

        articleAiDraftExecutor.execute(() -> runBatch(batchId));
        boolean allocationChanged = notices.stream().anyMatch(notice -> "auto_allocation_changed".equals(notice.type()));
        boolean customSkipped = notices.stream().anyMatch(notice -> "custom_template_skipped".equals(notice.type()));
        return new BatchArticleGenerateResponse(batchId, totalCount, STATUS_PENDING, allocationChanged, customSkipped, notices);
    }

    private boolean isThirdPartySourceBrand(Long brandId) {
        if (brandId == null) {
            return false;
        }
        return brandMapper.selectThirdPartySourceBrands().stream()
                .map(Brand::getId)
                .filter(Objects::nonNull)
                .anyMatch(brandId::equals);
    }

    public BatchArticleGenerationDetailResponse detail(Long batchId) {
        BatchArticleGenerationBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new BizException(404, "Batch not found");
        }
        List<BatchArticleGenerationTask> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<BatchArticleGenerationTask>()
                        .eq(BatchArticleGenerationTask::getBatchId, batchId)
                        .orderByAsc(BatchArticleGenerationTask::getArticleIndexInBatch)
        );
        Set<Long> brandIds = tasks.stream()
                .flatMap(task -> java.util.stream.Stream.of(task.getSourceBrandId(), task.getSubjectBrandId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> brandNameMap = brandIds.isEmpty()
                ? Map.of()
                : brandMapper.selectBatchIds(brandIds).stream()
                .collect(Collectors.toMap(Brand::getId, Brand::getBrandName, (first, ignored) -> first));
        List<BatchArticleGenerationDetailResponse.Task> taskItems = tasks.stream()
                .map(task -> new BatchArticleGenerationDetailResponse.Task(
                        task.getId(),
                        task.getArticleId(),
                        task.getSourceBrandId(),
                        brandNameMap.get(task.getSourceBrandId()),
                        task.getSubjectBrandId(),
                        brandNameMap.get(task.getSubjectBrandId()),
                        task.getSubjectProjectId(),
                        task.getRowNo(),
                        task.getArticleIndexInBatch(),
                        task.getArticleType(),
                        task.getArticleTypeCode(),
                        task.getChannelGroupCode(),
                        task.getChannelSubCode(),
                        task.getTone(),
                        task.getContentStyle(),
                        task.getAgentSiteModule(),
                        task.getContentAngle(),
                        task.getAudiencePerspective(),
                        task.getPromptTemplateId(),
                        task.getPromptTemplateVersionId(),
                        task.getPerspectiveCode(),
                        task.getPerspectiveMatchedScope(),
                        task.getPerspectiveMatchedConfigId(),
                        task.getAllocationMode(),
                        task.getTemplateSource(),
                        task.getSuggestedPlatformCodes(),
                        task.getSelectedPlatformCodes(),
                        task.getReadinessWarningConfirmed(),
                        task.getReadinessWarningCodes(),
                        task.getStatus(),
                        task.getQualityStatus(),
                        task.getComplianceStatus(),
                        task.getComplianceIssuesJson(),
                        task.getDiscardedArticleId(),
                        task.getRetryCount(),
                        task.getMedicalIndustryCode(),
                        task.getMedicalCategoryCode(),
                        task.getMedicalCategoryName(),
                        task.getTopicAngleId(),
                        task.getStructureSkeleton(),
                        task.getFocus(),
                        task.getErrorMessage(),
                        task.getStartedAt(),
                        task.getFinishedAt()
                ))
                .toList();
        return new BatchArticleGenerationDetailResponse(
                batch.getId(),
                batch.getProjectId(),
                batch.getTopic(),
                batch.getTopicAsQuestion(),
                batch.getStatus(),
                batch.getTotalCount(),
                batch.getSuccessCount(),
                batch.getFailedCount(),
                batch.getWarningCount(),
                batch.getCreatedAt(),
                batch.getStartedAt(),
                batch.getFinishedAt(),
                taskItems
        );
    }

    public BatchArticleGenerationDetailResponse retryFailed(Long batchId) {
        currentUserService.ensurePermissionOrLegacy("content.ai.generate", "project.update", LEGACY_PROJECT_UPDATE_ROLES);
        return retryFailedSystem(batchId);
    }

    public BatchArticleGenerationDetailResponse retryFailedSystem(Long batchId) {
        BatchArticleGenerationBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new BizException(404, "Batch not found");
        }
        List<BatchArticleGenerationTask> failedTasks = taskMapper.selectList(
                new LambdaQueryWrapper<BatchArticleGenerationTask>()
                        .eq(BatchArticleGenerationTask::getBatchId, batchId)
                        .eq(BatchArticleGenerationTask::getStatus, STATUS_FAILED)
                        .orderByAsc(BatchArticleGenerationTask::getArticleIndexInBatch)
        );
        if (failedTasks.isEmpty()) {
            throw new BizException(400, "当前批次没有可重试的失败任务");
        }
        for (BatchArticleGenerationTask task : failedTasks) {
            task.setStatus(STATUS_PENDING);
            task.setArticleId(null);
            task.setErrorMessage(null);
            task.setStartedAt(null);
            task.setFinishedAt(null);
            taskMapper.updateById(task);
        }
        refreshBatchProgress(batchId, false);
        articleAiDraftExecutor.execute(() -> runBatchTasks(batchId, failedTasks.stream().map(BatchArticleGenerationTask::getId).toList()));
        return detail(batchId);
    }

    public int recoverStalledBatches(int limit, Duration staleAfter) {
        int safeLimit = limit <= 0 ? 20 : limit;
        Duration safeStaleAfter = staleAfter == null || staleAfter.isZero() || staleAfter.isNegative()
                ? Duration.ofMinutes(15)
                : staleAfter;
        LocalDateTime cutoff = LocalDateTime.now().minus(safeStaleAfter);
        List<BatchArticleGenerationBatch> batches = batchMapper.selectList(
                new LambdaQueryWrapper<BatchArticleGenerationBatch>()
                        .in(BatchArticleGenerationBatch::getStatus, List.of(STATUS_PENDING, STATUS_RUNNING))
                        .isNull(BatchArticleGenerationBatch::getFinishedAt)
                        .le(BatchArticleGenerationBatch::getUpdatedAt, cutoff)
                        .orderByAsc(BatchArticleGenerationBatch::getId)
                        .last("LIMIT " + safeLimit)
        );
        if (batches == null || batches.isEmpty()) {
            return 0;
        }
        int recovered = 0;
        for (BatchArticleGenerationBatch batch : batches) {
            if (recoverStalledBatch(batch, cutoff)) {
                recovered++;
            }
        }
        return recovered;
    }

    private void runBatch(Long batchId) {
        BatchArticleGenerationBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            return;
        }
        markBatchRunning(batch);
        List<BatchArticleGenerationTask> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<BatchArticleGenerationTask>()
                        .eq(BatchArticleGenerationTask::getBatchId, batchId)
                        .orderByAsc(BatchArticleGenerationTask::getArticleIndexInBatch)
        );
        submitBatchTasks(batch, tasks);
    }

    private void runBatchTasks(Long batchId, List<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return;
        }
        BatchArticleGenerationBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            return;
        }
        markBatchRunning(batch);
        List<BatchArticleGenerationTask> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<BatchArticleGenerationTask>()
                        .eq(BatchArticleGenerationTask::getBatchId, batchId)
                        .in(BatchArticleGenerationTask::getId, taskIds)
                        .orderByAsc(BatchArticleGenerationTask::getArticleIndexInBatch)
        );
        submitBatchTasks(batch, tasks);
    }

    private void submitBatchTasks(BatchArticleGenerationBatch batch, List<BatchArticleGenerationTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            completeBatch(batch.getId());
            return;
        }
        for (BatchArticleGenerationTask task : tasks) {
            submitBatchTask(batch, task);
        }
    }

    private void submitBatchTask(BatchArticleGenerationBatch batch, BatchArticleGenerationTask task) {
        try {
            articleAiDraftExecutor.execute(() -> {
                try {
                    runTask(batch, task);
                } finally {
                    completeBatch(task.getBatchId());
                }
            });
        } catch (RuntimeException ex) {
            log.warn("batch article generation task rejected batchId={} taskId={} error={}",
                    task.getBatchId(), task.getId(), ex.getMessage());
            markTaskFailed(task, ex);
            completeBatch(task.getBatchId());
        }
    }

    private boolean recoverStalledBatch(BatchArticleGenerationBatch batch, LocalDateTime cutoff) {
        List<BatchArticleGenerationTask> tasks = selectBatchTasks(batch.getId());
        List<Long> resumableTaskIds = new ArrayList<>();
        for (BatchArticleGenerationTask task : tasks) {
            if (STATUS_PENDING.equals(task.getStatus())) {
                resumableTaskIds.add(task.getId());
                continue;
            }
            if (STATUS_RUNNING.equals(task.getStatus()) && isTaskStalled(task, cutoff)) {
                resetTaskForRecovery(task);
                resumableTaskIds.add(task.getId());
            }
        }
        if (resumableTaskIds.isEmpty()) {
            completeBatch(batch.getId());
            return false;
        }
        log.info("recover stalled batch article generation batchId={} taskCount={}", batch.getId(), resumableTaskIds.size());
        try {
            articleAiDraftExecutor.execute(() -> runBatchTasks(batch.getId(), resumableTaskIds));
            return true;
        } catch (RuntimeException ex) {
            log.warn("recover stalled batch article generation rejected batchId={} error={}", batch.getId(), ex.getMessage());
            return false;
        }
    }

    private boolean isTaskStalled(BatchArticleGenerationTask task, LocalDateTime cutoff) {
        LocalDateTime marker = task.getUpdatedAt() != null ? task.getUpdatedAt() : task.getStartedAt();
        return marker == null || !marker.isAfter(cutoff);
    }

    private void resetTaskForRecovery(BatchArticleGenerationTask task) {
        task.setStatus(STATUS_PENDING);
        task.setStartedAt(null);
        task.setFinishedAt(null);
        task.setErrorMessage(null);
        taskMapper.updateById(task);
    }

    private void runTask(BatchArticleGenerationBatch batch, BatchArticleGenerationTask task) {
        markTaskRunning(task);
        Project project = projectMapper.selectById(batch.getProjectId());
        Brand brand = project == null || project.getBrandId() == null ? null : brandMapper.selectById(project.getBrandId());
        try {
            if (project == null) {
                throw new BizException(404, "Project not found");
            }
            ArticleGenerationPromptContextFactory.PromptContextResult promptContext =
                    promptContextFactory.buildForBatch(batch, task);
            Project contentProject = promptContext.project();
            Brand contentBrand = promptContext.brand();
            applyMedicalContext(batch, task, promptContext.medicalContext());
            task.setTopicAsQuestion(promptContext.topicAsQuestion());
            if (promptContext.fallbackToDefaultPrompt()) {
                task.setTemplateSource(TEMPLATE_SOURCE_FALLBACK_DEFAULT_PROMPT);
            }
            BatchArticlePromptBuilder.PromptBuildResult prompt = promptContext.prompt();
            task.setContentAngle(prompt.contentAngle());
            task.setAudiencePerspective(prompt.audiencePerspective());

            List<String> forbiddenPhrases = promptContext.forbiddenPhrases();
            ArticleModelResolver.ModelSelection selectedModel = articleModelResolver.resolve(null, null, prompt.systemPrompt(), true);
            task.setModelPlatformCode(selectedModel.platformCode());
            task.setModelId(selectedModel.modelId());
            taskMapper.updateById(task);
            ArticleGenerationEngine.GeneratedArticle generated = null;
            MedicalArticleComplianceChecker.CheckResult complianceResult = MedicalArticleComplianceChecker.CheckResult.pass();
            int retryCount = 0;
            int maxAttempts = promptContext.medicalContext() == null ? 1 : MedicalArticleConstants.MAX_COMPLIANCE_GENERATION_ATTEMPTS;
            BatchArticlePromptBuilder.PromptBuildResult attemptPrompt = prompt;
            for (int attemptNo = 1; attemptNo <= maxAttempts; attemptNo++) {
                generated = articleGenerationEngine.generate(
                        new ArticleGenerationEngine.GenerateInput(
                                contentProject,
                                contentBrand,
                                attemptPrompt.systemPrompt(),
                                attemptPrompt.userPrompt(),
                                selectedModel.platformCode(),
                                selectedModel.modelId(),
                                true,
                                true,
                                true,
                                forbiddenPhrases
                        )
                );
                complianceResult = medicalComplianceChecker.check(new MedicalArticleComplianceChecker.CheckInput(
                        batch.getId(),
                        task.getId(),
                        contentProject.getId(),
                        contentProject.getBrandId(),
                        task.getChannelGroupCode(),
                        task.getChannelSubCode(),
                        generated.title(),
                        generated.content(),
                        contentBrand,
                        promptContext.medicalContext()
                ));
                if (complianceResult.passed()) {
                    break;
                }
                medicalComplianceChecker.logHits(new MedicalArticleComplianceChecker.CheckInput(
                                batch.getId(),
                                task.getId(),
                                contentProject.getId(),
                                contentProject.getBrandId(),
                                task.getChannelGroupCode(),
                                task.getChannelSubCode(),
                                generated.title(),
                                generated.content(),
                                contentBrand,
                                promptContext.medicalContext()
                        ),
                        complianceResult,
                        null,
                        attemptNo >= maxAttempts ? "discard" : "retry");
                if (attemptNo < maxAttempts) {
                    retryCount++;
                    attemptPrompt = appendComplianceRetryGuidance(prompt, complianceResult);
                }
            }
            if (!complianceResult.passed()) {
                Long discardedArticleId = persistDiscardedArticle(project, task, generated, prompt, selectedModel, complianceResult,
                        promptContext.medicalContext());
                markTaskComplianceDiscarded(task, discardedArticleId, prompt, selectedModel, generated, complianceResult, retryCount);
                specialIndustryComplianceAlertService.notifyComplianceDiscarded(contentProject, contentBrand, task, discardedArticleId, complianceResult);
                return;
            }

            Long articleId = persistArticle(project, task, generated.title(), generated.content(), prompt, generated.model(), generated.result(),
                    promptContext.medicalContext(), MedicalArticleConstants.COMPLIANCE_PASSED);
            medicalArticleGenerationService.recordHistory(contentProject, contentBrand, promptContext.medicalContext(), articleId);
            specialIndustryComplianceAlertService.notifyPublishReviewPending(contentProject, contentBrand, task, articleId, promptContext.medicalContext());
            markTaskSuccess(task, articleId, prompt, generated.model(), generated.result(), generated.quality(), retryCount);
        } catch (Exception ex) {
            log.warn("Batch article generation task failed batchId={} taskId={}", batch.getId(), task.getId(), ex);
            markTaskFailed(task, ex);
        }
    }

    private Long persistArticle(Project project,
                                BatchArticleGenerationTask task,
                                String title,
                                String content,
                                BatchArticlePromptBuilder.PromptBuildResult prompt,
                                ArticleModelResolver.ModelSelection model,
                                LlmInvokeResult result,
                                MedicalArticleGenerationService.MedicalPromptContext medicalContext,
                                String complianceStatus) {
        return Objects.requireNonNull(transactionTemplate.execute(status -> {
            ArticleDraft draft = new ArticleDraft();
            draft.setBatchId(null);
            draft.setProjectId(project.getId());
            draft.setSourceBrandId(task.getSourceBrandId() == null ? project.getBrandId() : task.getSourceBrandId());
            draft.setSubjectBrandId(task.getSubjectBrandId() == null ? project.getBrandId() : task.getSubjectBrandId());
            draft.setSubjectProjectId(task.getSubjectProjectId() == null ? project.getId() : task.getSubjectProjectId());
            draft.setArticleType(task.getArticleType());
            draft.setContentStyle(task.getContentStyle());
            draft.setChannelGroupCode(task.getChannelGroupCode());
            draft.setChannelSubCode(task.getChannelSubCode());
            draft.setAgentSiteModule(task.getAgentSiteModule());
            draft.setArticleTypeCode(task.getArticleTypeCode());
            draft.setPromptTemplateId(task.getPromptTemplateId());
            draft.setPromptTemplateVersionId(task.getPromptTemplateVersionId());
            draft.setPerspectiveCode(TemplatePerspectiveCodes.normalize(task.getPerspectiveCode()));
            draft.setAllocationMode(task.getAllocationMode());
            draft.setTemplateSource(task.getTemplateSource());
            draft.setTopic(task.getTopic());
            draft.setTopicAsQuestion(task.getTopicAsQuestion());
            draft.setTitle(title);
            applyMedicalDraftFields(draft, medicalContext, complianceStatus);
            if (ArticlePromptChannels.SELF_MEDIA.equals(task.getChannelGroupCode())) {
                draft.setCoverImageUrl(coverSelectionService.selectRandomCoverUrl(coverBrandId(project, task)));
            }
            draft.setStatus("approved");
            draft.setCurrentVersionNo(1);
            draft.setHasRisk(false);
            draft.setRiskSeverity("none");
            draft.setIsDuplicateTitle(false);
            articleDraftMapper.insert(draft);

            ArticleDraftVersion version = new ArticleDraftVersion();
            version.setArticleId(draft.getId());
            version.setVersionNo(1);
            version.setTitle(title);
            version.setContentMarkdown(autoImageInsertionService.insertForChannel(project, task.getChannelGroupCode(), content));
            version.setPromptSnapshot(enrichPromptSnapshot(prompt.promptSnapshot(), result));
            version.setInputSnapshot(prompt.inputSnapshot());
            version.setModelPlatformCode(model.platformCode());
            version.setModelId(model.modelId());
            version.setGeneratedBy(GENERATED_BY_BATCH_AI);
            version.setCreatedBy(null);
            articleDraftVersionMapper.insert(version);

            ArticleGenerationLog logRow = new ArticleGenerationLog();
            logRow.setProjectId(project.getId());
            logRow.setArticleType(task.getArticleType());
            logRow.setArticleAngle(task.getContentAngle());
            logRow.setGeneratedTitle(title);
            logRow.setModelCode(model.platformCode());
            articleGenerationLogMapper.insert(logRow);
            return draft.getId();
        }));
    }

    private Long coverBrandId(Project project, BatchArticleGenerationTask task) {
        if (task != null && task.getSubjectBrandId() != null) {
            return task.getSubjectBrandId();
        }
        return project == null ? null : project.getBrandId();
    }

    private Long persistDiscardedArticle(Project project,
                                         BatchArticleGenerationTask task,
                                         ArticleGenerationEngine.GeneratedArticle generated,
                                         BatchArticlePromptBuilder.PromptBuildResult prompt,
                                         ArticleModelResolver.ModelSelection model,
                                         MedicalArticleComplianceChecker.CheckResult complianceResult,
                                         MedicalArticleGenerationService.MedicalPromptContext medicalContext) {
        return Objects.requireNonNull(transactionTemplate.execute(status -> {
            ArticleDraft draft = new ArticleDraft();
            draft.setBatchId(null);
            draft.setProjectId(project.getId());
            draft.setSourceBrandId(task.getSourceBrandId() == null ? project.getBrandId() : task.getSourceBrandId());
            draft.setSubjectBrandId(task.getSubjectBrandId() == null ? project.getBrandId() : task.getSubjectBrandId());
            draft.setSubjectProjectId(task.getSubjectProjectId() == null ? project.getId() : task.getSubjectProjectId());
            draft.setArticleType(task.getArticleType());
            draft.setContentStyle(task.getContentStyle());
            draft.setChannelGroupCode(task.getChannelGroupCode());
            draft.setChannelSubCode(task.getChannelSubCode());
            draft.setAgentSiteModule(task.getAgentSiteModule());
            draft.setArticleTypeCode(task.getArticleTypeCode());
            draft.setPromptTemplateId(task.getPromptTemplateId());
            draft.setPromptTemplateVersionId(task.getPromptTemplateVersionId());
            draft.setPerspectiveCode(TemplatePerspectiveCodes.normalize(task.getPerspectiveCode()));
            draft.setAllocationMode(task.getAllocationMode());
            draft.setTemplateSource(task.getTemplateSource());
            draft.setTopic(task.getTopic());
            draft.setTopicAsQuestion(task.getTopicAsQuestion());
            String title = generated == null ? task.getTopic() : generated.title();
            draft.setTitle(title);
            applyMedicalDraftFields(draft, medicalContext, MedicalArticleConstants.COMPLIANCE_DISCARDED);
            draft.setMedicalIndustryCode(task.getMedicalIndustryCode());
            draft.setMedicalCategoryCode(task.getMedicalCategoryCode());
            draft.setStatus(MedicalArticleConstants.COMPLIANCE_DISCARDED);
            draft.setCurrentVersionNo(1);
            draft.setHasRisk(true);
            draft.setRiskSeverity("high");
            draft.setIsDuplicateTitle(false);
            articleDraftMapper.insert(draft);

            ArticleDraftVersion version = new ArticleDraftVersion();
            version.setArticleId(draft.getId());
            version.setVersionNo(1);
            version.setTitle(title);
            version.setContentMarkdown(generated == null ? "" : generated.content());
            version.setPromptSnapshot(enrichPromptSnapshot(prompt.promptSnapshot(), generated == null ? null : generated.result()));
            version.setInputSnapshot(enrichComplianceInputSnapshot(prompt.inputSnapshot(), complianceResult));
            version.setModelPlatformCode(model.platformCode());
            version.setModelId(model.modelId());
            version.setGeneratedBy(GENERATED_BY_BATCH_AI);
            version.setCreatedBy(null);
            articleDraftVersionMapper.insert(version);
            medicalComplianceChecker.logHits(new MedicalArticleComplianceChecker.CheckInput(
                            task.getBatchId(),
                            task.getId(),
                            project.getId(),
                            project.getBrandId(),
                            task.getChannelGroupCode(),
                            task.getChannelSubCode(),
                            title,
                            generated == null ? "" : generated.content(),
                            null,
                            null
                    ),
                    complianceResult,
                    draft.getId(),
                    "discard");
            return draft.getId();
        }));
    }

    private void applyMedicalContext(BatchArticleGenerationBatch batch,
                                     BatchArticleGenerationTask task,
                                     MedicalArticleGenerationService.MedicalPromptContext context) {
        if (context == null) {
            return;
        }
        task.setTopic(context.topicAngle());
        task.setMedicalIndustryCode(context.industryCode());
        task.setMedicalCategoryCode(context.categoryCode());
        task.setMedicalCategoryName(context.categoryName());
        task.setTopicAngleId(context.topicAngleId());
        task.setStructureSkeleton(context.structureSkeleton());
        task.setFocus(context.focus());
        task.setComplianceStatus(MedicalArticleConstants.COMPLIANCE_PENDING);
        if (batch.getMedicalIndustryCode() == null || batch.getMedicalChannelTier() == null) {
            batch.setMedicalIndustryCode(context.industryCode());
            batch.setMedicalChannelTier(context.channelTier());
            batchMapper.updateById(batch);
        }
    }

    private BatchArticlePromptBuilder.PromptBuildResult appendComplianceRetryGuidance(
            BatchArticlePromptBuilder.PromptBuildResult prompt,
            MedicalArticleComplianceChecker.CheckResult complianceResult) {
        String retryBlock = """

                # 上一次医疗合规校验未通过
                必须重写正文，避开以下命中项；不要解释校验结果，只输出修正后的完整 Markdown 文章。
                %s
                """.formatted(medicalComplianceChecker.toJson(complianceResult));
        return new BatchArticlePromptBuilder.PromptBuildResult(
                prompt.systemPrompt(),
                prompt.userPrompt() + retryBlock,
                prompt.contentAngle(),
                prompt.audiencePerspective(),
                prompt.promptSnapshot(),
                prompt.inputSnapshot()
        );
    }

    private void applyMedicalDraftFields(ArticleDraft draft,
                                         MedicalArticleGenerationService.MedicalPromptContext context,
                                         String complianceStatus) {
        if (context == null) {
            draft.setComplianceStatus(complianceStatus);
            return;
        }
        draft.setComplianceStatus(complianceStatus);
        draft.setMedicalAdReviewNo(context.medicalAdReviewNo());
        draft.setMedicalChannelTier(context.channelTier());
        draft.setMedicalIndustryCode(context.industryCode());
        draft.setMedicalCategoryCode(context.categoryCode());
        if (MedicalArticleConstants.TIER_OFFICIAL_SITE.equals(context.channelTier())) {
            draft.setPublishReviewStatus(StringUtils.hasText(context.medicalAdReviewNo())
                    ? MedicalArticleConstants.REVIEW_PASSED
                    : MedicalArticleConstants.REVIEW_PENDING);
        } else {
            draft.setPublishReviewStatus(MedicalArticleConstants.REVIEW_NOT_REQUIRED);
        }
    }

    private String enrichComplianceInputSnapshot(String inputSnapshot,
                                                 MedicalArticleComplianceChecker.CheckResult complianceResult) {
        Map<String, Object> snapshot = readJson(inputSnapshot);
        snapshot.put("medicalComplianceResult", complianceResult);
        return writeJson(snapshot);
    }

    private BatchArticlePromptBuilder.PromptBuildResult buildPrompt(BatchArticleGenerationTask task,
                                                                    BatchArticlePromptBuilder.PromptBuildInput input) {
        if (task.getPromptTemplateId() == null || task.getPromptTemplateVersionId() == null) {
            task.setTemplateSource(TEMPLATE_SOURCE_FALLBACK_DEFAULT_PROMPT);
            return promptBuilder.build(input);
        }
        ArticlePromptTemplate template = promptTemplateMapper.selectById(task.getPromptTemplateId());
        ArticlePromptTemplateVersion version = promptTemplateVersionMapper.selectById(task.getPromptTemplateVersionId());
        if (template == null || version == null || !template.getId().equals(version.getTemplateId())) {
            log.warn("Prompt template snapshot missing taskId={} templateId={} versionId={}",
                    task.getId(), task.getPromptTemplateId(), task.getPromptTemplateVersionId());
            task.setTemplateSource(TEMPLATE_SOURCE_FALLBACK_DEFAULT_PROMPT);
            return promptBuilder.build(input);
        }
        return promptBuilder.buildFromTemplate(input, template, version);
    }

    private ArticleModelResolver.ModelSelection resolveModel(String systemPrompt) {
        return articleModelResolver.resolve(null, null, systemPrompt, true);
    }

    private void markBatchRunning(BatchArticleGenerationBatch batch) {
        batch.setStatus(STATUS_RUNNING);
        if (batch.getStartedAt() == null) {
            batch.setStartedAt(LocalDateTime.now());
        }
        batch.setFinishedAt(null);
        batchMapper.updateById(batch);
    }

    private void completeBatch(Long batchId) {
        refreshBatchProgress(batchId, true);
    }

    private void refreshBatchProgress(Long batchId, boolean finishIfDone) {
        List<BatchArticleGenerationTask> tasks = selectBatchTasks(batchId);
        int success = (int) tasks.stream().filter(task -> STATUS_SUCCESS.equals(task.getStatus())).count();
        int failed = (int) tasks.stream().filter(task -> STATUS_FAILED.equals(task.getStatus())).count();
        int warning = (int) tasks.stream().filter(task -> "warning".equals(task.getQualityStatus())).count();
        BatchArticleGenerationBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            return;
        }
        batch.setSuccessCount(success);
        batch.setFailedCount(failed);
        batch.setWarningCount(warning);
        boolean done = !tasks.isEmpty() && success + failed == tasks.size();
        if (done && finishIfDone) {
            batch.setFinishedAt(LocalDateTime.now());
            if (success > 0 && failed > 0) {
                batch.setStatus(STATUS_PARTIAL_SUCCESS);
            } else if (success > 0) {
                batch.setStatus(STATUS_SUCCESS);
            } else {
                batch.setStatus(STATUS_FAILED);
            }
        } else if (success > 0 || failed > 0 || tasks.stream().anyMatch(task -> STATUS_RUNNING.equals(task.getStatus()))) {
            batch.setStatus(STATUS_RUNNING);
        }
        batchMapper.updateById(batch);
    }

    private List<BatchArticleGenerationTask> selectBatchTasks(Long batchId) {
        List<BatchArticleGenerationTask> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<BatchArticleGenerationTask>().eq(BatchArticleGenerationTask::getBatchId, batchId)
        );
        return tasks == null ? List.of() : tasks;
    }

    private void markTaskRunning(BatchArticleGenerationTask task) {
        task.setStatus(STATUS_RUNNING);
        task.setStartedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        refreshBatchProgress(task.getBatchId(), false);
    }

    private void markTaskSuccess(BatchArticleGenerationTask task,
                                 Long articleId,
                                 BatchArticlePromptBuilder.PromptBuildResult prompt,
                                 ArticleModelResolver.ModelSelection model,
                                 LlmInvokeResult result,
                                 BatchArticleQualityChecker.QualityResult quality,
                                 int retryCount) {
        task.setArticleId(articleId);
        task.setStatus(STATUS_SUCCESS);
        task.setQualityStatus("rewrite_required".equals(quality.status()) ? "warning" : quality.status());
        task.setQualityIssuesJson(qualityChecker.toJson(quality));
        task.setContentAngle(prompt.contentAngle());
        task.setAudiencePerspective(prompt.audiencePerspective());
        task.setPromptSnapshot(enrichPromptSnapshot(prompt.promptSnapshot(), result));
        task.setInputSnapshot(prompt.inputSnapshot());
        task.setResponseSnapshot(responseSnapshot(result));
        task.setModelPlatformCode(model.platformCode());
        task.setModelId(model.modelId());
        task.setRetryCount(retryCount);
        if (StringUtils.hasText(task.getMedicalIndustryCode())) {
            task.setComplianceStatus(MedicalArticleConstants.COMPLIANCE_PASSED);
            task.setComplianceIssuesJson(null);
        }
        task.setFinishedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        refreshBatchProgress(task.getBatchId(), false);
    }

    private void markTaskComplianceDiscarded(BatchArticleGenerationTask task,
                                             Long discardedArticleId,
                                             BatchArticlePromptBuilder.PromptBuildResult prompt,
                                             ArticleModelResolver.ModelSelection model,
                                             ArticleGenerationEngine.GeneratedArticle generated,
                                             MedicalArticleComplianceChecker.CheckResult complianceResult,
                                             int retryCount) {
        task.setStatus(STATUS_FAILED);
        task.setDiscardedArticleId(discardedArticleId);
        task.setComplianceStatus(MedicalArticleConstants.COMPLIANCE_DISCARDED);
        task.setComplianceIssuesJson(medicalComplianceChecker.toJson(complianceResult));
        task.setErrorMessage("医疗合规校验失败，已废弃生成结果");
        task.setContentAngle(prompt.contentAngle());
        task.setAudiencePerspective(prompt.audiencePerspective());
        task.setPromptSnapshot(enrichPromptSnapshot(prompt.promptSnapshot(), generated == null ? null : generated.result()));
        task.setInputSnapshot(enrichComplianceInputSnapshot(prompt.inputSnapshot(), complianceResult));
        if (generated != null) {
            task.setResponseSnapshot(responseSnapshot(generated.result()));
        }
        task.setModelPlatformCode(model.platformCode());
        task.setModelId(model.modelId());
        task.setRetryCount(retryCount);
        task.setFinishedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        refreshBatchProgress(task.getBatchId(), false);
    }

    private void markTaskFailed(BatchArticleGenerationTask task, Exception ex) {
        task.setStatus(STATUS_FAILED);
        task.setErrorMessage(errorMessage(ex));
        task.setFinishedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        refreshBatchProgress(task.getBatchId(), false);
    }

    private Project requireActiveProject(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null || project.getDeletedAt() != null) {
            throw new BizException(404, "Project not found");
        }
        if (!"active".equals(project.getStatus())) {
            throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "Only active project can generate articles");
        }
        return project;
    }

    private KeywordGroup validateKeywordGroup(Long projectId, String topicSource, Long keywordGroupId) {
        if (!"keyword_group".equals(topicSource) || keywordGroupId == null) {
            return null;
        }
        Long count = projectKeywordGroupRelMapper.selectCount(
                new LambdaQueryWrapper<ProjectKeywordGroupRel>()
                        .eq(ProjectKeywordGroupRel::getProjectId, projectId)
                        .eq(ProjectKeywordGroupRel::getKeywordGroupId, keywordGroupId)
        );
        if (count == null || count <= 0) {
            throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "Keyword group does not belong to project");
        }
        KeywordGroup group = keywordGroupMapper.selectById(keywordGroupId);
        if (group == null || Boolean.TRUE.equals(group.getDeleted())) {
            throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "Keyword group not found");
        }
        return group;
    }

    private List<ValidatedTopic> validateTopics(Long projectId,
                                                Long brandId,
                                                String topicSource,
                                                List<BatchArticleGenerateRequest.TopicConfig> topics,
                                                List<BatchArticleGenerateResponse.Notice> notices,
                                                Map<String, SmartTemplateSelection> smartSelections,
                                                Map<String, TemplatePerspectiveService.ResolvedPerspective> perspectiveMemo) {
        if (topics == null || topics.isEmpty()) {
            throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "Topics are required");
        }
        List<ValidatedTopic> result = new ArrayList<>();
        for (int topicIndex = 0; topicIndex < topics.size(); topicIndex++) {
            BatchArticleGenerateRequest.TopicConfig topicConfig = topics.get(topicIndex);
            String topic = trim(topicConfig.getTopic());
            if (!StringUtils.hasText(topic)) {
                throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "Topic is required");
            }
            KeywordGroup keywordGroup = validateKeywordGroup(projectId, topicSource, topicConfig.getKeywordGroupId());
            String questionSceneCode = normalizeQuestionScene(topicConfig.getQuestionSceneCode());
            List<String> suggestedPlatformCodes = suggestionService.suggestedPlatformCodes(questionSceneCode);
            List<String> selectedPlatformCodes = selectedPlatformCodes(topicConfig.getPlatforms());
            List<String> confirmedReadinessWarningCodes = Boolean.TRUE.equals(topicConfig.getReadinessWarningConfirmed())
                    ? normalizeWarningCodes(topicConfig.getReadinessWarningCodes())
                    : List.of();
            List<ValidatedPlatform> platforms = validatePlatforms(topicIndex, brandId, topic, questionSceneCode,
                    topicConfig.getPlatforms(), notices, smartSelections, perspectiveMemo);
            if (platforms.stream().mapToInt(platform -> platform.count() == null ? 0 : platform.count()).sum() <= 0) {
                continue;
            }
            result.add(new ValidatedTopic(
                    topic,
                    trimToNull(topicConfig.getTopicAsQuestion()),
                    questionSceneCode,
                    keywordGroup == null ? null : keywordGroup.getId(),
                    keywordGroup == null ? trimToNull(topicConfig.getKeywordGroupName()) : keywordGroup.getName(),
                    suggestedPlatformCodes,
                    selectedPlatformCodes,
                    confirmedReadinessWarningCodes,
                    trimToNull(topicConfig.getMedicalIndustryCode()),
                    trimToNull(topicConfig.getMedicalCategoryCode()),
                    trimToNull(topicConfig.getMedicalCategoryName()),
                    topicConfig.getTopicAngleId(),
                    trimToNull(topicConfig.getStructureSkeleton()),
                    trimToNull(topicConfig.getFocus()),
                    platforms
            ));
        }
        if (result.isEmpty()) {
            throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "At least one topic must generate articles");
        }
        return result;
    }

    private List<String> selectedPlatformCodes(List<BatchArticleGenerateRequest.PlatformCount> platforms) {
        if (platforms == null || platforms.isEmpty()) {
            return List.of();
        }
        return platforms.stream()
                .filter(Objects::nonNull)
                .filter(this::hasRequestedCount)
                .map(this::resolveChannel)
                .map(channel -> QuestionScenePlatformSuggestionService.key(channel.groupCode(), channel.subCode()))
                .distinct()
                .toList();
    }

    private boolean hasRequestedCount(BatchArticleGenerateRequest.PlatformCount platform) {
        String allocationMode = StringUtils.hasText(platform.getAllocationMode()) ? platform.getAllocationMode().trim() : "auto";
        if ("custom".equals(allocationMode)) {
            return platform.getTemplateCounts() != null && platform.getTemplateCounts().stream()
                    .filter(Objects::nonNull)
                    .anyMatch(item -> item.getCount() != null && item.getCount() > 0);
        }
        return platform.getCount() != null && platform.getCount() > 0;
    }

    private List<String> normalizeWarningCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        return codes.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(readinessService::isKnownWarningCode)
                .distinct()
                .toList();
    }

    private List<ValidatedPlatform> validatePlatforms(int topicIndex,
                                                      Long brandId,
                                                      String topic,
                                                      String questionSceneCode,
                                                      List<BatchArticleGenerateRequest.PlatformCount> platforms,
                                                      List<BatchArticleGenerateResponse.Notice> notices,
                                                      Map<String, SmartTemplateSelection> smartSelections,
                                                      Map<String, TemplatePerspectiveService.ResolvedPerspective> perspectiveMemo) {
        if (platforms == null || platforms.isEmpty()) {
            throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "Platform counts are required");
        }
        List<ValidatedPlatform> result = new ArrayList<>();
        for (int platformIndex = 0; platformIndex < platforms.size(); platformIndex++) {
            BatchArticleGenerateRequest.PlatformCount platform = platforms.get(platformIndex);
            if (platform == null) {
                continue;
            }
            String allocationMode = StringUtils.hasText(platform.getAllocationMode()) ? platform.getAllocationMode().trim() : "auto";
            if ("custom".equals(allocationMode)) {
                result.addAll(validateCustomPlatform(brandId, topic, platform, notices, perspectiveMemo));
                continue;
            }
            result.addAll(validateAutoPlatform(unitKey(topicIndex, platformIndex), brandId, topic, platform,
                    questionSceneCode, notices, smartSelections, perspectiveMemo));
        }
        return result;
    }

    private List<ValidatedPlatform> validateAutoPlatform(String unitKey,
                                                         Long brandId,
                                                         String topic,
                                                         BatchArticleGenerateRequest.PlatformCount platform,
                                                         String questionSceneCode,
                                                         List<BatchArticleGenerateResponse.Notice> notices,
                                                         Map<String, SmartTemplateSelection> smartSelections,
                                                         Map<String, TemplatePerspectiveService.ResolvedPerspective> perspectiveMemo) {
        int count = platform.getCount() == null ? 0 : platform.getCount();
        if (count < 0) {
            throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "Invalid article count");
        }
        if (count == 0) {
            return List.of();
        }
        ChannelRef channel = resolveChannel(platform);
        TemplatePerspectiveService.ResolvedPerspective perspective = resolvePerspective(brandId, channel, perspectiveMemo);
        AutoTemplateAllocation allocation = allocateAutoTemplates(unitKey, channel, questionSceneCode, perspective, count, smartSelections);
        if (allocation.templates().isEmpty()) {
            if (TemplatePerspectiveCodes.isThirdParty(perspective.perspectiveCode())) {
                throw missingTemplateException(topic, channel, questionSceneCode, perspective.perspectiveCode());
            }
            addSkippedNotice(notices, topic, channel, null, null, count, "未配置启用模板");
            return List.of();
        }
        maybeAddAllocationChangedNotice(topic, channel, platform.getPreviewTemplateCounts(), allocation.templates(), notices);
        return allocation.templates().stream()
                .map(item -> toValidatedPlatform(channel, item.template(), item.version(), item.count(),
                        platform.getExtraPrompt(), "auto", allocation.templateSource(), perspective))
                .toList();
    }

    private AutoTemplateAllocation allocateAutoTemplates(
            String unitKey,
            ChannelRef channel,
            String questionSceneCode,
            TemplatePerspectiveService.ResolvedPerspective perspective,
            int count,
            Map<String, SmartTemplateSelection> smartSelections) {
        SmartTemplateSelection selection = smartSelections == null ? null : smartSelections.get(unitKey);
        if (selection == null || selection.templateIds().isEmpty()) {
            return new AutoTemplateAllocation(
                    allocationService.allocate(channel.groupCode(), channel.subCode(), questionSceneCode, perspective.perspectiveCode(), count),
                    TEMPLATE_SOURCE_WEIGHTED
            );
        }
        List<ArticleTemplateAllocationService.TemplateWithVersion> candidates = allocationService.activeTemplates(
                        channel.groupCode(), channel.subCode(), questionSceneCode, perspective.perspectiveCode()).stream()
                .filter(item -> selection.templateIds().contains(item.template().getId()))
                .toList();
        List<ArticleTemplateAllocationService.AllocatedTemplate> allocated = allocationService.allocateCandidates(candidates, count);
        if (!allocated.isEmpty()) {
            return new AutoTemplateAllocation(allocated, TEMPLATE_SOURCE_SMART);
        }
        return new AutoTemplateAllocation(
                allocationService.allocate(channel.groupCode(), channel.subCode(), questionSceneCode, perspective.perspectiveCode(), count),
                TEMPLATE_SOURCE_WEIGHTED
        );
    }

    private List<ValidatedPlatform> validateCustomPlatform(Long brandId,
                                                           String topic,
                                                           BatchArticleGenerateRequest.PlatformCount platform,
                                                           List<BatchArticleGenerateResponse.Notice> notices,
                                                           Map<String, TemplatePerspectiveService.ResolvedPerspective> perspectiveMemo) {
        if (platform.getTemplateCounts() == null || platform.getTemplateCounts().isEmpty()) {
            return List.of();
        }
        List<ValidatedPlatform> result = new ArrayList<>();
        for (BatchArticleGenerateRequest.TemplateCount templateCount : platform.getTemplateCounts()) {
            int count = templateCount.getCount() == null ? 0 : templateCount.getCount();
            if (count <= 0) {
                continue;
            }
            ChannelRef requestedChannel = resolveChannel(platform);
            TemplatePerspectiveService.ResolvedPerspective perspective = resolvePerspective(brandId, requestedChannel, perspectiveMemo);
            ArticleTemplateAllocationService.TemplateWithVersion resolved = allocationService.resolveTemplate(
                    templateCount.getTemplateId(), templateCount.getTemplateVersionId(), perspective.perspectiveCode()
            );
            if (resolved == null) {
                if (TemplatePerspectiveCodes.isThirdParty(perspective.perspectiveCode())) {
                    throw missingTemplateException(topic, requestedChannel, null, perspective.perspectiveCode());
                }
                addSkippedNotice(notices, topic, requestedChannel, templateCount.getTemplateId(), null, count, "模板已失效");
                continue;
            }
            ChannelRef channel = new ChannelRef(
                    resolved.template().getChannelGroupCode(),
                    resolved.template().getChannelSubCode(),
                    ArticlePromptChannels.contentStyle(resolved.template().getChannelGroupCode(), resolved.template().getChannelSubCode())
            );
            result.add(toValidatedPlatform(channel, resolved.template(), resolved.version(), count,
                    templateCount.getExtraPrompt(), "custom", TEMPLATE_SOURCE_CUSTOM, perspective));
        }
        return result;
    }

    private Map<String, SmartTemplateSelection> selectSmartTemplates(
            BatchArticleGenerateRequest req,
            Long brandId,
            Map<String, TemplatePerspectiveService.ResolvedPerspective> perspectiveMemo) {
        List<SmartTemplateMatchUnit> units = collectSmartTemplateMatchUnits(req, brandId, perspectiveMemo);
        if (units.isEmpty()) {
            return Map.of();
        }
        try {
            ArticleModelResolver.ModelSelection model = resolveModel(SMART_TEMPLATE_MATCH_SYSTEM_PROMPT);
            LlmInvokeResult result = llmInvoker.invoke(buildSmartTemplateMatchPrompt(units), model.config());
            Map<String, SmartTemplateSelection> selections = parseSmartTemplateMatchResult(result.responseText(), units);
            if (!selections.isEmpty()) {
                return selections;
            }
        } catch (Exception ex) {
            log.warn("smart article template matching failed, fallback to weighted allocation: {}", errorMessage(ex));
        }
        return Map.of();
    }

    private List<SmartTemplateMatchUnit> collectSmartTemplateMatchUnits(
            BatchArticleGenerateRequest req,
            Long brandId,
            Map<String, TemplatePerspectiveService.ResolvedPerspective> perspectiveMemo) {
        if (req.getTopics() == null || req.getTopics().isEmpty()) {
            return List.of();
        }
        List<SmartTemplateMatchUnit> units = new ArrayList<>();
        for (int topicIndex = 0; topicIndex < req.getTopics().size(); topicIndex++) {
            BatchArticleGenerateRequest.TopicConfig topic = req.getTopics().get(topicIndex);
            if (topic == null || topic.getPlatforms() == null || topic.getPlatforms().isEmpty()) {
                continue;
            }
            for (int platformIndex = 0; platformIndex < topic.getPlatforms().size(); platformIndex++) {
                BatchArticleGenerateRequest.PlatformCount platform = topic.getPlatforms().get(platformIndex);
                if (platform == null) {
                    continue;
                }
                String allocationMode = StringUtils.hasText(platform.getAllocationMode()) ? platform.getAllocationMode().trim() : "auto";
                int count = platform.getCount() == null ? 0 : platform.getCount();
                if (!"auto".equals(allocationMode) || count <= 0) {
                    continue;
                }
                String questionSceneCode = normalizeQuestionScene(topic.getQuestionSceneCode());
                ChannelRef channel = resolveChannel(platform);
                TemplatePerspectiveService.ResolvedPerspective perspective = resolvePerspective(brandId, channel, perspectiveMemo);
                List<ArticleTemplateAllocationService.TemplateWithVersion> candidates = allocationService.activeTemplates(
                                channel.groupCode(), channel.subCode(), questionSceneCode, perspective.perspectiveCode()).stream()
                        .filter(item -> item.template().getWeight() != null && item.template().getWeight() > 0)
                        .toList();
                if (candidates.size() <= 1) {
                    continue;
                }
                units.add(new SmartTemplateMatchUnit(
                        unitKey(topicIndex, platformIndex),
                        trim(topic.getTopic()),
                        trimToNull(topic.getTopicAsQuestion()),
                        questionSceneCode,
                        channel,
                        count,
                        candidates
                ));
            }
        }
        return units;
    }

    private String buildSmartTemplateMatchPrompt(List<SmartTemplateMatchUnit> units) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (SmartTemplateMatchUnit unit : units) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("unitKey", unit.unitKey());
            item.put("topic", unit.topic());
            item.put("topicAsQuestion", unit.topicAsQuestion());
            item.put("questionSceneCode", unit.questionSceneCode());
            item.put("questionSceneName", questionSceneLabel(unit.questionSceneCode()));
            item.put("channelGroupCode", unit.channel().groupCode());
            item.put("channelSubCode", unit.channel().subCode());
            item.put("channelName", ArticlePromptChannels.channelName(unit.channel().groupCode(), unit.channel().subCode()));
            item.put("count", unit.count());
            item.put("availableTemplates", unit.candidates().stream().map(candidate -> {
                ArticlePromptTemplate template = candidate.template();
                Map<String, Object> templateItem = new LinkedHashMap<>();
                templateItem.put("templateId", template.getId());
                templateItem.put("templateName", template.getName());
                templateItem.put("description", trimToNull(template.getDescription()));
                templateItem.put("articleTypeCode", template.getArticleTypeCode());
                templateItem.put("articleTypeName", ArticlePromptChannels.ARTICLE_TYPE_LABELS.getOrDefault(template.getArticleTypeCode(), template.getArticleTypeCode()));
                templateItem.put("questionSceneCode", template.getQuestionSceneCode());
                templateItem.put("questionSceneName", questionSceneLabel(template.getQuestionSceneCode()));
                templateItem.put("weight", template.getWeight());
                return templateItem;
            }).toList());
            items.add(item);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("task", "select_article_prompt_templates");
        payload.put("rules", List.of(
                "根据主题意图选择最适合的模板",
                "每个 unit 选择 1-3 个模板",
                "只返回可用模板中的 templateId",
                "无法判断时 fallback=true"
        ));
        payload.put("items", items);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, SmartTemplateSelection> parseSmartTemplateMatchResult(String responseText,
                                                                             List<SmartTemplateMatchUnit> units) {
        Map<String, SmartTemplateMatchUnit> unitMap = units.stream()
                .collect(Collectors.toMap(SmartTemplateMatchUnit::unitKey, item -> item, (a, b) -> a, LinkedHashMap::new));
        Map<String, SmartTemplateSelection> selections = new HashMap<>();
        Map<String, Object> root;
        try {
            root = objectMapper.readValue(normalizeContent(responseText), LinkedHashMap.class);
        } catch (Exception ex) {
            log.warn("smart template matching response is not valid JSON: {}", ex.getMessage());
            return Map.of();
        }
        Object itemsRaw = root.get("items");
        if (!(itemsRaw instanceof List<?> items)) {
            return Map.of();
        }
        for (Object itemRaw : items) {
            if (!(itemRaw instanceof Map<?, ?> item)) {
                continue;
            }
            String unitKey = trimToNull(String.valueOf(item.get("unitKey")));
            SmartTemplateMatchUnit unit = unitKey == null ? null : unitMap.get(unitKey);
            if (unit == null) {
                continue;
            }
            Set<Long> candidateIds = unit.candidates().stream()
                    .map(candidate -> candidate.template().getId())
                    .collect(Collectors.toSet());
            List<Long> selectedIds = parseTemplateIds(item.get("selectedTemplateIds")).stream()
                    .filter(candidateIds::contains)
                    .distinct()
                    .limit(3)
                    .toList();
            boolean fallback = Boolean.TRUE.equals(item.get("fallback"));
            if (fallback || selectedIds.isEmpty()) {
                continue;
            }
            String reason = trimToNull(item.get("reason") == null ? null : String.valueOf(item.get("reason")));
            selections.put(unitKey, new SmartTemplateSelection(selectedIds, reason));
        }
        return selections;
    }

    private List<Long> parseTemplateIds(Object raw) {
        if (!(raw instanceof List<?> values)) {
            return List.of();
        }
        List<Long> result = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof Number number) {
                result.add(number.longValue());
                continue;
            }
            try {
                result.add(Long.parseLong(String.valueOf(value)));
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    private ValidatedPlatform toValidatedPlatform(ChannelRef channel,
                                                  ArticlePromptTemplate template,
                                                  ArticlePromptTemplateVersion version,
                                                  int count,
                                                  String extraPrompt,
                                                  String allocationMode,
                                                  String templateSource,
                                                  TemplatePerspectiveService.ResolvedPerspective perspective) {
        return new ValidatedPlatform(
                channel.contentStyle(),
                channel.groupCode(),
                channel.subCode(),
                template.getAgentSiteModule(),
                template.getArticleTypeCode(),
                template.getId(),
                version.getId(),
                perspective.perspectiveCode(),
                perspective.matchedScope(),
                perspective.matchedConfigId(),
                template.getContactDisclosureMode(),
                allocationMode,
                templateSource,
                count,
                trimToNull(extraPrompt)
        );
    }

    private String resolveTaskArticleType(String articleTypeCode) {
        String articleType = trimToNull(articleTypeCode);
        if (articleType != null && ArticleTypes.isSupported(articleType)) {
            return articleType;
        }
        return DEFAULT_ARTICLE_TYPE;
    }

    private ChannelRef resolveChannel(BatchArticleGenerateRequest.PlatformCount platform) {
        String group = trimToNull(platform.getChannelGroupCode());
        String sub = trimToNull(platform.getChannelSubCode());
        if (group == null) {
            group = groupFromContentStyle(platform.getContentStyle());
            sub = subFromContentStyle(platform.getContentStyle());
        }
        if (!ArticlePromptChannels.isValidCode(group)) {
            throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "Invalid channel group");
        }
        if (sub != null && !ArticlePromptChannels.isValidCode(sub)) {
            throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "Invalid channel sub code");
        }
        sub = ArticlePromptChannels.canonicalSubCode(group, sub);
        return new ChannelRef(group, sub, ArticlePromptChannels.contentStyle(group, sub));
    }

    private TemplatePerspectiveService.ResolvedPerspective resolvePerspective(
            Long brandId,
            ChannelRef channel,
            Map<String, TemplatePerspectiveService.ResolvedPerspective> perspectiveMemo) {
        String key = (brandId == null ? 0 : brandId) + ":" + channel.groupCode() + ":" + (channel.subCode() == null ? "" : channel.subCode());
        return perspectiveMemo.computeIfAbsent(key,
                ignored -> perspectiveService.resolve(brandId, channel.groupCode(), channel.subCode()));
    }

    private BizException missingTemplateException(String topic,
                                                  ChannelRef channel,
                                                  String questionSceneCode,
                                                  String perspectiveCode) {
        String message = "特殊视角缺少启用模板: topic=" + topic
                + ", channelGroup=" + channel.groupCode()
                + ", channelSub=" + (channel.subCode() == null ? "" : channel.subCode())
                + ", questionScene=" + (questionSceneCode == null ? "" : questionSceneCode)
                + ", perspective=" + perspectiveCode;
        return new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, message);
    }

    private String groupFromContentStyle(String contentStyle) {
        String style = trim(contentStyle);
        if (List.of("wechat", "toutiao", "douyin", "douyin_image_text", "zhihu", "xiaohongshu", "baijiahao", "netease").contains(style)) {
            return ArticlePromptChannels.SELF_MEDIA;
        }
        if ("agent_site_article".equals(style) || "linkedin".equals(style)) {
            return ArticlePromptChannels.AGENT_SITE;
        }
        if ("industry_site".equals(style)) {
            return ArticlePromptChannels.INDUSTRY_SITE;
        }
        if ("authority_media".equals(style)) {
            return ArticlePromptChannels.AUTHORITY_MEDIA;
        }
        if ("forum".equals(style)) {
            return ArticlePromptChannels.FORUM;
        }
        if (ArticlePromptChannels.isValidCode(style)) {
            return style;
        }
        throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "Invalid content style");
    }

    private String subFromContentStyle(String contentStyle) {
        String style = trim(contentStyle);
        if (List.of("wechat", "toutiao", "douyin", "douyin_image_text", "zhihu", "xiaohongshu", "baijiahao", "netease").contains(style)) {
            return style;
        }
        if ("authority_media".equals(style)) {
            return "industry_media";
        }
        return null;
    }

    private void maybeAddAllocationChangedNotice(String topic,
                                                 ChannelRef channel,
                                                 List<BatchArticleGenerateRequest.TemplateCount> preview,
                                                 List<ArticleTemplateAllocationService.AllocatedTemplate> actual,
                                                 List<BatchArticleGenerateResponse.Notice> notices) {
        if (preview == null || preview.isEmpty()) {
            return;
        }
        Map<Long, Integer> beforeMap = new LinkedHashMap<>();
        for (BatchArticleGenerateRequest.TemplateCount item : preview) {
            if (item.getCount() != null && item.getCount() > 0) {
                beforeMap.put(item.getTemplateId(), item.getCount());
            }
        }
        Map<Long, Integer> afterMap = new LinkedHashMap<>();
        for (ArticleTemplateAllocationService.AllocatedTemplate item : actual) {
            afterMap.put(item.template().getId(), item.count());
        }
        if (beforeMap.equals(afterMap)) {
            return;
        }
        List<BatchArticleGenerateResponse.TemplateCount> before = preview.stream()
                .filter(item -> item.getCount() != null && item.getCount() > 0)
                .map(item -> new BatchArticleGenerateResponse.TemplateCount(item.getTemplateId(), null, item.getCount()))
                .toList();
        List<BatchArticleGenerateResponse.TemplateCount> after = actual.stream()
                .map(item -> new BatchArticleGenerateResponse.TemplateCount(item.template().getId(), item.template().getName(), item.count()))
                .toList();
        notices.add(new BatchArticleGenerateResponse.Notice(
                "auto_allocation_changed",
                "warning",
                "因模板池有变化，实际分配与预览不一致",
                List.of(new BatchArticleGenerateResponse.Item(topic, channel.groupCode(), channel.subCode(),
                        null, null, null, null, before, after))
        ));
    }

    private void addSkippedNotice(List<BatchArticleGenerateResponse.Notice> notices,
                                  String topic,
                                  ChannelRef channel,
                                  Long templateId,
                                  String templateName,
                                  Integer requestedCount,
                                  String reason) {
        notices.add(new BatchArticleGenerateResponse.Notice(
                "custom_template_skipped",
                "warning",
                "部分模板不可用并已跳过",
                List.of(new BatchArticleGenerateResponse.Item(topic, channel.groupCode(), channel.subCode(),
                        templateId, templateName, requestedCount, reason, null, null))
        ));
    }

    private List<String> relatedKeywords(Long projectId, BatchArticleGenerationTask task) {
        List<Long> groupIds = new ArrayList<>();
        if (task.getKeywordGroupId() != null) {
            groupIds.add(task.getKeywordGroupId());
        } else {
            groupIds.addAll(projectKeywordGroupRelMapper.selectList(
                    new LambdaQueryWrapper<ProjectKeywordGroupRel>()
                            .eq(ProjectKeywordGroupRel::getProjectId, projectId)
                            .orderByAsc(ProjectKeywordGroupRel::getId)
            ).stream().map(ProjectKeywordGroupRel::getKeywordGroupId).distinct().toList());
        }
        if (groupIds.isEmpty()) {
            return List.of();
        }
        return keywordGroupResultMapper.selectList(
                new LambdaQueryWrapper<KeywordGroupResult>()
                        .in(KeywordGroupResult::getGroupId, groupIds)
                        .orderByAsc(KeywordGroupResult::getGroupId, KeywordGroupResult::getSortOrder, KeywordGroupResult::getId)
                        .last("LIMIT 12")
        ).stream()
                .map(KeywordGroupResult::getKeywordText)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private String resolveBrandStatement(Project project, Brand brand) {
        if (StringUtils.hasText(project.getCustomStatement())) {
            return project.getCustomStatement().trim();
        }
        if (brand == null) {
            return null;
        }
        return buildBrandProfileStatement(brand);
    }

    private String buildBrandProfileStatement(Brand brand) {
        List<String> parts = new ArrayList<>();
        addPart(parts, "品牌定位", brand.getBrandPositioning());
        addPart(parts, "主营业务", brand.getMainBusiness());
        addPart(parts, "核心产品", brand.getCoreProducts());
        addPart(parts, "业务介绍", brand.getBusinessIntro());
        addPart(parts, "资质背书", brand.getBrandQualificationDescription());
        addPart(parts, "案例素材", brand.getBrandCaseDescription());
        return parts.isEmpty() ? null : String.join("；", parts);
    }

    private String buildTitleGuide(BatchArticleGenerationTask task, Project project, Brand brand, String topic) {
        if (!ArticlePromptChannels.FORUM.equals(task.getChannelGroupCode())) {
            return null;
        }
        String safeTopic = cleanTitlePart(topic);
        if (!StringUtils.hasText(safeTopic)) {
            return null;
        }
        String timeAnchor = timeAnchor(task.getArticleIndexInBatch(), safeTopic);
        String region = cleanTitlePart(resolveTitleRegion(project, brand));
        String industry = cleanTitlePart(resolveTitleIndustry(project, brand));
        String brandName = cleanTitlePart(brand == null ? project.getBrandName() : brand.getBrandName());
        boolean comparison = isComparisonForumTemplate(task);
        String brandRule = comparison
                ? "对比推荐帖可自然出现品牌名，但必须服务于语义，不要每篇都机械使用“聚焦XX”。"
                : "普通讨论帖标题默认不露出品牌名，优先在正文中自然带出。";
        String titleTags = comparison ? "[对比]、[杂谈]、[分享]、[讨论]" : "[杂谈]、[讨论]、[分享]、[避坑]";
        return """
                # 标题生成参考

                请根据下列元素自行生成文章标题，允许按语义调整顺序、删减非必要元素，使标题读起来像真实论坛用户发帖，而不是机器拼接。

                【可用标题元素】
                - 论坛标签：%s
                - 时间锚点：%s
                - 地域：%s
                - 行业：%s
                - 主题：%s
                - 品牌：%s

                【标题规则】
                1. 正文第一行必须是你生成的标题。
                2. 标题必须以一个论坛标签开头，例如“[杂谈] ”或“[讨论] ”。
                3. 时间锚点来自系统动态计算，可使用但不要强行堆叠；如果标题已很自然，可以弱化时间表达。
                4. 地域、行业、主题、品牌不必全部出现，优先保证标题顺畅、真实、有讨论感。
                5. %s
                6. 避免“服务商选择指南”“综合评估”“专业服务商”“聚焦XX”这类资讯站或官网口吻。
                7. 标题长度建议 24-42 个中文字符，不要超过 55 个中文字符。
                8. 标题需避开历史已写标题中的表达，减少重复。

                【可参考的标题语气】
                - “[杂谈] %s%s怎么选？最近看了几家，说说感受”
                - “[讨论] %s做%s，到底该看哪些细节？”
                - “[分享] %s在%s选%s，我比较关注这几个点”
                - “[避坑] %s别只看热度和价格，这几个点容易忽略”
                """.formatted(
                titleTags,
                blankToDash(timeAnchor),
                blankToDash(region),
                blankToDash(industry),
                safeTopic,
                blankToDash(brandName),
                brandRule,
                blankToEmpty(region),
                safeTopic,
                blankToEmpty(region),
                safeTopic,
                timeAnchor,
                blankToEmpty(region),
                safeTopic,
                safeTopic
        );
    }

    private boolean isComparisonForumTemplate(BatchArticleGenerationTask task) {
        if (task.getPromptTemplateId() == null) {
            return false;
        }
        ArticlePromptTemplate template = promptTemplateMapper.selectById(task.getPromptTemplateId());
        if (template == null || !StringUtils.hasText(template.getName())) {
            return false;
        }
        String name = template.getName();
        return name.contains("对比") || name.contains("推荐");
    }

    private String timeAnchor(int articleIndexInBatch, String topic) {
        LocalDate now = LocalDate.now(BUSINESS_ZONE);
        String year = String.valueOf(now.getYear());
        int monthValue = now.getMonthValue();
        int quarter = (monthValue + 2) / 3;
        String month = year + "年" + monthValue + "月";
        List<String> anchors = List.of(
                year + "现阶段",
                year + "年至今",
                month,
                month + "更新",
                month + "最新指南",
                month + "新消息",
                year + "年Q" + quarter,
                year + "年第" + quarterCn(quarter) + "季度"
        );
        if (topic.contains(year) || topic.matches(".*\\d{4}年\\d{1,2}月.*") || topic.matches(".*\\d{4}年Q[1-4].*")) {
            return "现阶段";
        }
        return anchors.get(Math.floorMod(Math.max(0, articleIndexInBatch - 1), anchors.size()));
    }

    private String quarterCn(int quarter) {
        return switch (quarter) {
            case 1 -> "一";
            case 2 -> "二";
            case 3 -> "三";
            case 4 -> "四";
            default -> "";
        };
    }

    private String resolveTitleRegion(Project project, Brand brand) {
        if (StringUtils.hasText(project.getDistrictName())) {
            return project.getDistrictName();
        }
        if (StringUtils.hasText(project.getCityName())) {
            return project.getCityName();
        }
        if (StringUtils.hasText(project.getProvinceName())) {
            return project.getProvinceName();
        }
        if (brand != null) {
            if (StringUtils.hasText(brand.getDistrictName())) {
                return brand.getDistrictName();
            }
            if (StringUtils.hasText(brand.getCityName())) {
                return brand.getCityName();
            }
            if (StringUtils.hasText(brand.getServiceArea())) {
                return brand.getServiceArea();
            }
        }
        return "";
    }

    private String resolveTitleIndustry(Project project, Brand brand) {
        if (brand != null && StringUtils.hasText(brand.getIndustry())) {
            return brand.getIndustry();
        }
        return project.getProjectName();
    }

    private String cleanTitlePart(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim()
                .replaceAll("[\\r\\n\\t]+", "")
                .replace("，", "")
                .replace(",", "")
                .replace("。", "")
                .replace("？", "")
                .replace("?", "")
                .replace("-", "");
    }

    private void addPart(List<String> parts, String label, String value) {
        if (StringUtils.hasText(value)) {
            parts.add(label + "：" + value.trim());
        }
    }

    private String blankToDash(String value) {
        return StringUtils.hasText(value) ? value.trim() : "-";
    }

    private String blankToEmpty(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private List<String> forbiddenPhrases(Project project, Brand brand) {
        List<String> result = new ArrayList<>();
        if (brand != null) {
            result.addAll(parseJsonArray(brand.getForbiddenPhrases()));
        }
        result.addAll(parseJsonArray(project.getExtraForbiddenPhrases()));
        return result.stream().filter(StringUtils::hasText).map(String::trim).distinct().toList();
    }

    private List<String> parseJsonArray(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            List<String> result = new ArrayList<>();
            JSONUtil.parseArray(raw).forEach(item -> {
                if (item != null && StringUtils.hasText(String.valueOf(item))) {
                    result.add(String.valueOf(item).trim());
                }
            });
            return result;
        } catch (Exception ex) {
            return List.of(raw.trim());
        }
    }

    private String enrichPromptSnapshot(String promptSnapshot, LlmInvokeResult result) {
        Map<String, Object> snapshot = readJson(promptSnapshot);
        snapshot.put("contentSource", "BATCH_AI");
        if (result != null) {
            snapshot.put("promptTokens", result.promptTokens());
            snapshot.put("completionTokens", result.completionTokens());
        }
        return writeJson(snapshot);
    }

    private String responseSnapshot(LlmInvokeResult result) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (result == null) {
            return writeJson(snapshot);
        }
        snapshot.put("responseText", result.responseText());
        snapshot.put("promptTokens", result.promptTokens());
        snapshot.put("completionTokens", result.completionTokens());
        snapshot.put("durationMs", result.durationMs());
        snapshot.put("retryCount", result.retryCount());
        snapshot.put("callStatus", result.callStatus() == null ? null : result.callStatus().name());
        snapshot.put("platformCode", result.platformCode());
        snapshot.put("platformName", result.platformName());
        snapshot.put("modelId", result.modelId());
        snapshot.put("modelName", result.modelName());
        return writeJson(snapshot);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJson(String value) {
        if (!StringUtils.hasText(value)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(value, LinkedHashMap.class);
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private int requestedArticleCount(BatchArticleGenerateRequest req) {
        if (req.getTopics() == null) {
            return 0;
        }
        int total = 0;
        for (BatchArticleGenerateRequest.TopicConfig topic : req.getTopics()) {
            if (topic == null || topic.getPlatforms() == null) {
                continue;
            }
            for (BatchArticleGenerateRequest.PlatformCount platform : topic.getPlatforms()) {
                if (platform == null) {
                    continue;
                }
                String allocationMode = StringUtils.hasText(platform.getAllocationMode()) ? platform.getAllocationMode().trim() : "auto";
                if ("custom".equals(allocationMode)) {
                    if (platform.getTemplateCounts() != null) {
                        for (BatchArticleGenerateRequest.TemplateCount item : platform.getTemplateCounts()) {
                            total += Math.max(0, item.getCount() == null ? 0 : item.getCount());
                        }
                    }
                    continue;
                }
                total += Math.max(0, platform.getCount() == null ? 0 : platform.getCount());
            }
        }
        return total;
    }

    private String unitKey(int topicIndex, int platformIndex) {
        return "u" + topicIndex + "_" + platformIndex;
    }

    private String normalizeContent(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        return content.trim()
                .replaceFirst("^```(?:markdown)?\\s*", "")
                .replaceFirst("\\s*```$", "")
                .trim();
    }

    private String extractTitle(String content) {
        String[] lines = content.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!StringUtils.hasText(trimmed)) {
                continue;
            }
            if (trimmed.startsWith("#")) {
                trimmed = trimmed.replaceFirst("^#+\\s*", "");
            }
            return trimmed.length() > 120 ? trimmed.substring(0, 120) : trimmed;
        }
        return "AI 批量生成文章";
    }

    private int normalize(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeQuestionScene(String value) {
        String scene = trimToNull(value);
        return scene != null && ArticlePromptTemplateService.QUESTION_SCENE_LABELS.containsKey(scene) ? scene : null;
    }

    private String questionSceneLabel(String value) {
        String scene = trimToNull(value);
        return scene == null ? null : ArticlePromptTemplateService.QUESTION_SCENE_LABELS.getOrDefault(scene, scene);
    }

    private String errorMessage(Exception ex) {
        if (ex instanceof LlmInvokeException) {
            String reason = rootMessage(ex);
            return StringUtils.hasText(reason) ? "AI article generation failed: " + reason : "AI article generation failed";
        }
        return StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : ex.getClass().getSimpleName();
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        String message = null;
        while (current != null) {
            if (StringUtils.hasText(current.getMessage())) {
                message = current.getMessage();
            }
            current = current.getCause();
        }
        if (!StringUtils.hasText(message)) {
            return null;
        }
        String trimmed = message.trim();
        return trimmed.length() > 900 ? trimmed.substring(0, 900) : trimmed;
    }

    private record ValidatedTopic(String topic,
                                  String topicAsQuestion,
                                  String questionSceneCode,
                                  Long keywordGroupId,
                                  String keywordGroupName,
                                  List<String> suggestedPlatformCodes,
                                  List<String> selectedPlatformCodes,
                                  List<String> confirmedReadinessWarningCodes,
                                  String medicalIndustryCode,
                                  String medicalCategoryCode,
                                  String medicalCategoryName,
                                  Long topicAngleId,
                                  String structureSkeleton,
                                  String focus,
                                  List<ValidatedPlatform> platforms) {
    }

    private record ChannelRef(String groupCode, String subCode, String contentStyle) {
    }

    private record ValidatedPlatform(String contentStyle,
                                     String channelGroupCode,
                                     String channelSubCode,
                                     String agentSiteModule,
                                     String articleTypeCode,
                                     Long templateId,
                                     Long templateVersionId,
                                     String perspectiveCode,
                                     String perspectiveMatchedScope,
                                     Long perspectiveMatchedConfigId,
                                     String contactDisclosureMode,
                                     String allocationMode,
                                     String templateSource,
                                     Integer count,
                                     String extraPrompt) {
    }

    private record AutoTemplateAllocation(List<ArticleTemplateAllocationService.AllocatedTemplate> templates,
                                          String templateSource) {
    }

    private record SmartTemplateMatchUnit(String unitKey,
                                          String topic,
                                          String topicAsQuestion,
                                          String questionSceneCode,
                                          ChannelRef channel,
                                          int count,
                                          List<ArticleTemplateAllocationService.TemplateWithVersion> candidates) {
    }

    private record SmartTemplateSelection(List<Long> templateIds,
                                          String reason) {
    }
}
