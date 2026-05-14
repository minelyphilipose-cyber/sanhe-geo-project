package com.huanjing.geo.module.content.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.llm.LlmInvokeException;
import com.huanjing.geo.common.llm.LlmInvokeResult;
import com.huanjing.geo.common.llm.LlmInvoker;
import com.huanjing.geo.common.llm.LlmModelConfig;
import com.huanjing.geo.module.content.ContentErrorCodes;
import com.huanjing.geo.module.content.constant.ArticleTypes;
import com.huanjing.geo.module.content.dto.BatchArticleGenerateRequest;
import com.huanjing.geo.module.content.dto.BatchArticleGenerateResponse;
import com.huanjing.geo.module.content.dto.BatchArticleGenerationDetailResponse;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.entity.ArticleDraftVersion;
import com.huanjing.geo.module.content.entity.ArticleGenerationLog;
import com.huanjing.geo.module.content.entity.BatchArticleGenerationBatch;
import com.huanjing.geo.module.content.entity.BatchArticleGenerationTask;
import com.huanjing.geo.module.content.mapper.ArticleDraftMapper;
import com.huanjing.geo.module.content.mapper.ArticleDraftVersionMapper;
import com.huanjing.geo.module.content.mapper.ArticleGenerationLogMapper;
import com.huanjing.geo.module.content.mapper.BatchArticleGenerationBatchMapper;
import com.huanjing.geo.module.content.mapper.BatchArticleGenerationTaskMapper;
import com.huanjing.geo.module.customer.access.BrandAccessAction;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.customer.service.BrandStatementService;
import com.huanjing.geo.module.project.entity.KeywordGroup;
import com.huanjing.geo.module.project.entity.KeywordGroupResult;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.entity.ProjectKeywordGroupRel;
import com.huanjing.geo.module.project.mapper.KeywordGroupMapper;
import com.huanjing.geo.module.project.mapper.KeywordGroupResultMapper;
import com.huanjing.geo.module.project.mapper.ProjectKeywordGroupRelMapper;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import com.huanjing.geo.module.system.service.PlatformCredentialService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;

@Slf4j
@Service
public class BatchArticleGenerationService {

    private static final int MAX_BATCH_ARTICLE_COUNT = 30;
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_RUNNING = "running";
    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_FAILED = "failed";
    private static final String STATUS_PARTIAL_SUCCESS = "partial_success";
    private static final String GENERATED_BY_BATCH_AI = "batch_ai";
    private static final String DEFAULT_ARTICLE_TYPE = ArticleTypes.INDUSTRY_ARTICLE;
    private static final String DEFAULT_LENGTH = "medium";
    private static final int ARTICLE_REQUEST_TIMEOUT_MS = 120_000;
    private static final Set<String> SUPPORTED_CONTENT_STYLES = Set.of(
            "wechat", "toutiao", "douyin_image_text", "zhihu", "linkedin",
            "industry_site", "authority_media", "forum", "xiaohongshu"
    );
    private static final Set<String> CREATABLE_CONTENT_STYLES = Set.of(
            "wechat", "toutiao", "douyin_image_text", "zhihu", "linkedin",
            "industry_site", "authority_media", "forum"
    );

    private final ProjectMapper projectMapper;
    private final BrandMapper brandMapper;
    private final KeywordGroupMapper keywordGroupMapper;
    private final KeywordGroupResultMapper keywordGroupResultMapper;
    private final ProjectKeywordGroupRelMapper projectKeywordGroupRelMapper;
    private final AiPlatformConfigMapper aiPlatformConfigMapper;
    private final ArticleDraftMapper articleDraftMapper;
    private final ArticleDraftVersionMapper articleDraftVersionMapper;
    private final ArticleGenerationLogMapper articleGenerationLogMapper;
    private final BatchArticleGenerationBatchMapper batchMapper;
    private final BatchArticleGenerationTaskMapper taskMapper;
    private final CurrentUserService currentUserService;
    private final BrandAccessService brandAccessService;
    private final PlatformCredentialService platformCredentialService;
    private final BrandStatementService brandStatementService;
    private final LlmInvoker llmInvoker;
    private final MarkdownImageReferenceValidator markdownImageReferenceValidator;
    private final ArticleAiDraftPromptFilter promptFilter;
    private final BatchArticlePromptBuilder promptBuilder;
    private final BatchArticleQualityChecker qualityChecker;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final Executor articleAiDraftExecutor;

    public BatchArticleGenerationService(ProjectMapper projectMapper,
                                         BrandMapper brandMapper,
                                         KeywordGroupMapper keywordGroupMapper,
                                         KeywordGroupResultMapper keywordGroupResultMapper,
                                         ProjectKeywordGroupRelMapper projectKeywordGroupRelMapper,
                                         AiPlatformConfigMapper aiPlatformConfigMapper,
                                         ArticleDraftMapper articleDraftMapper,
                                         ArticleDraftVersionMapper articleDraftVersionMapper,
                                         ArticleGenerationLogMapper articleGenerationLogMapper,
                                         BatchArticleGenerationBatchMapper batchMapper,
                                         BatchArticleGenerationTaskMapper taskMapper,
                                         CurrentUserService currentUserService,
                                         BrandAccessService brandAccessService,
                                         PlatformCredentialService platformCredentialService,
                                         BrandStatementService brandStatementService,
                                         LlmInvoker llmInvoker,
                                         MarkdownImageReferenceValidator markdownImageReferenceValidator,
                                         ArticleAiDraftPromptFilter promptFilter,
                                         BatchArticlePromptBuilder promptBuilder,
                                         BatchArticleQualityChecker qualityChecker,
                                         ObjectMapper objectMapper,
                                         PlatformTransactionManager transactionManager,
                                         @Qualifier("articleAiDraftExecutor") Executor articleAiDraftExecutor) {
        this.projectMapper = projectMapper;
        this.brandMapper = brandMapper;
        this.keywordGroupMapper = keywordGroupMapper;
        this.keywordGroupResultMapper = keywordGroupResultMapper;
        this.projectKeywordGroupRelMapper = projectKeywordGroupRelMapper;
        this.aiPlatformConfigMapper = aiPlatformConfigMapper;
        this.articleDraftMapper = articleDraftMapper;
        this.articleDraftVersionMapper = articleDraftVersionMapper;
        this.articleGenerationLogMapper = articleGenerationLogMapper;
        this.batchMapper = batchMapper;
        this.taskMapper = taskMapper;
        this.currentUserService = currentUserService;
        this.brandAccessService = brandAccessService;
        this.platformCredentialService = platformCredentialService;
        this.brandStatementService = brandStatementService;
        this.llmInvoker = llmInvoker;
        this.markdownImageReferenceValidator = markdownImageReferenceValidator;
        this.promptFilter = promptFilter;
        this.promptBuilder = promptBuilder;
        this.qualityChecker = qualityChecker;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.articleAiDraftExecutor = articleAiDraftExecutor;
    }

    public BatchArticleGenerateResponse create(BatchArticleGenerateRequest req) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("project.update");

        Project project = requireActiveProject(req.getProjectId());
        currentUserService.ensurePartnerResourceAccess(operator, project.getPartnerId(), "project");
        if (project.getBrandId() != null) {
            brandAccessService.requireBrandAccess(project.getBrandId(), operator.getId(), BrandAccessAction.OPERATE);
        }

        String topicSource = StringUtils.hasText(req.getTopicSource()) ? req.getTopicSource().trim() : "manual";
        List<ValidatedTopic> topics = validateTopics(project.getId(), topicSource, req.getTopics());
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
            batch.setCreatedBy(operator.getId());
            batchMapper.insert(batch);

            int articleIndexInBatch = 1;
            for (int topicIndex = 0; topicIndex < topics.size(); topicIndex++) {
                ValidatedTopic topic = topics.get(topicIndex);
                int articleIndexInTopic = 1;
                for (ValidatedPlatform platform : topic.platforms()) {
                    for (int articleIndexInPlatform = 1; articleIndexInPlatform <= platform.count(); articleIndexInPlatform++) {
                        BatchArticleGenerationTask task = new BatchArticleGenerationTask();
                        task.setBatchId(batch.getId());
                        task.setProjectId(project.getId());
                        task.setRowNo(topicIndex + 1);
                        task.setArticleIndexInRow(articleIndexInTopic);
                        task.setArticleIndexInBatch(articleIndexInBatch);
                        task.setArticleType(DEFAULT_ARTICLE_TYPE);
                        task.setTone("");
                        task.setContentStyle(platform.contentStyle());
                        task.setLength(DEFAULT_LENGTH);
                        task.setTopic(topic.topic());
                        task.setTopicAsQuestion(topic.topicAsQuestion());
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
        return new BatchArticleGenerateResponse(batchId, totalCount, STATUS_PENDING);
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
        List<BatchArticleGenerationDetailResponse.Task> taskItems = tasks.stream()
                .map(task -> new BatchArticleGenerationDetailResponse.Task(
                        task.getId(),
                        task.getArticleId(),
                        task.getRowNo(),
                        task.getArticleIndexInBatch(),
                        task.getArticleType(),
                        task.getTone(),
                        task.getContentStyle(),
                        task.getContentAngle(),
                        task.getAudiencePerspective(),
                        task.getStatus(),
                        task.getQualityStatus(),
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
        for (BatchArticleGenerationTask task : tasks) {
            runTask(batch, task);
        }
        completeBatch(batchId);
    }

    private void runTask(BatchArticleGenerationBatch batch, BatchArticleGenerationTask task) {
        markTaskRunning(task);
        Project project = projectMapper.selectById(batch.getProjectId());
        Brand brand = project == null || project.getBrandId() == null ? null : brandMapper.selectById(project.getBrandId());
        try {
            if (project == null) {
                throw new BizException(404, "Project not found");
            }
            String topic = StringUtils.hasText(task.getTopic()) ? task.getTopic() : batch.getTopic();
            String topicAsQuestion = StringUtils.hasText(task.getTopicAsQuestion())
                    ? task.getTopicAsQuestion()
                    : promptBuilder.topicAsQuestion(topic, task.getArticleType(), task.getArticleIndexInBatch());
            task.setTopicAsQuestion(topicAsQuestion);
            List<String> relatedKeywords = relatedKeywords(batch.getProjectId(), task);
            String brandStatement = resolveBrandStatement(project, brand);
            BatchArticlePromptBuilder.PromptBuildResult prompt = promptBuilder.build(
                    new BatchArticlePromptBuilder.PromptBuildInput(
                            project,
                            brand,
                            brandStatement,
                            batch.getTopicSource(),
                            topic,
                            topicAsQuestion,
                            task.getKeywordGroupId(),
                            task.getKeywordGroupName(),
                            relatedKeywords,
                            task.getArticleType(),
                            task.getContentStyle(),
                            task.getLength(),
                            task.getExtraPrompt(),
                            task.getArticleIndexInBatch()
                    )
            );
            task.setContentAngle(prompt.contentAngle());
            task.setAudiencePerspective(prompt.audiencePerspective());

            ModelSelection model = resolveModel(prompt.systemPrompt());
            String outboundPrompt = promptFilter.filterOutboundPrompt(prompt.userPrompt(), project, brand);
            LlmInvokeResult result = llmInvoker.invoke(outboundPrompt, model.config());
            String content = normalizeContent(promptFilter.filterGeneratedContent(result.responseText(), project, brand, true));

            BatchArticleQualityChecker.QualityResult quality = qualityChecker.check(
                    content, brand, forbiddenPhrases(project, brand)
            );
            int retryCount = 0;

            if (!StringUtils.hasText(content)) {
                throw new BizException(ContentErrorCodes.ARTICLE_AI_DRAFT_GENERATE_FAILED, "AI generated empty article");
            }
            markdownImageReferenceValidator.validate(project, content);
            String title = extractTitle(content);
            Long articleId = persistArticle(project, task, title, content, prompt, model, result);
            markTaskSuccess(task, articleId, prompt, model, result, quality, retryCount);
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
                                ModelSelection model,
                                LlmInvokeResult result) {
        return Objects.requireNonNull(transactionTemplate.execute(status -> {
            ArticleDraft draft = new ArticleDraft();
            draft.setBatchId(null);
            draft.setProjectId(project.getId());
            draft.setArticleType(task.getArticleType());
            draft.setTitle(title);
            draft.setStatus("pending_review");
            draft.setCurrentVersionNo(1);
            draft.setHasRisk(false);
            draft.setRiskSeverity("none");
            draft.setIsDuplicateTitle(false);
            articleDraftMapper.insert(draft);

            ArticleDraftVersion version = new ArticleDraftVersion();
            version.setArticleId(draft.getId());
            version.setVersionNo(1);
            version.setTitle(title);
            version.setContentMarkdown(content);
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

    private ModelSelection resolveModel(String systemPrompt) {
        AiPlatformConfig config = aiPlatformConfigMapper.selectOne(
                new LambdaQueryWrapper<AiPlatformConfig>()
                        .eq(AiPlatformConfig::getEnabled, true)
                        .eq(AiPlatformConfig::getEnabledForArticle, true)
                        .orderByAsc(AiPlatformConfig::getId)
                        .last("LIMIT 1")
        );
        if (config == null || !StringUtils.hasText(config.getApiUrl())) {
            throw new BizException(ContentErrorCodes.ARTICLE_AI_DRAFT_CONFIG_MISSING, "AI article model config missing");
        }
        String modelId = StringUtils.hasText(config.getModelId()) ? config.getModelId().trim() : config.getLowModelId();
        if (!StringUtils.hasText(modelId)) {
            throw new BizException(ContentErrorCodes.ARTICLE_AI_DRAFT_CONFIG_MISSING, "AI article model config missing");
        }
        String apiKey = platformCredentialService.resolveApiKey(
                config.getPlatformCode(), config.getPrimaryKeyRef(), config.getApiKey()
        );
        if (!StringUtils.hasText(apiKey)) {
            throw new BizException(ContentErrorCodes.ARTICLE_AI_DRAFT_CONFIG_MISSING, "AI article model config missing");
        }
        int timeout = Math.min(Math.max(normalize(config.getTimeoutMs(), ARTICLE_REQUEST_TIMEOUT_MS),
                ARTICLE_REQUEST_TIMEOUT_MS), LlmModelConfig.LONG_FORM_MAX_REQUEST_TIMEOUT_MS);
        LlmModelConfig modelConfig = new LlmModelConfig(
                config.getPlatformCode(),
                config.getPlatformName(),
                modelId,
                StringUtils.hasText(config.getModelName()) ? config.getModelName().trim() : modelId,
                config.getApiUrl(),
                apiKey,
                systemPrompt,
                0.4D,
                LlmModelConfig.DEFAULT_CONNECT_TIMEOUT_MS,
                timeout,
                normalize(config.getMaxRetry(), 2),
                Math.max(1, normalize(config.getRateLimitQps(), 1)),
                null,
                false,
                LlmModelConfig.LONG_FORM_MAX_REQUEST_TIMEOUT_MS
        );
        return new ModelSelection(config.getPlatformCode(), modelId, modelConfig);
    }

    private void markBatchRunning(BatchArticleGenerationBatch batch) {
        batch.setStatus(STATUS_RUNNING);
        batch.setStartedAt(LocalDateTime.now());
        batchMapper.updateById(batch);
    }

    private void completeBatch(Long batchId) {
        List<BatchArticleGenerationTask> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<BatchArticleGenerationTask>().eq(BatchArticleGenerationTask::getBatchId, batchId)
        );
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
        batch.setFinishedAt(LocalDateTime.now());
        if (success > 0 && failed > 0) {
            batch.setStatus(STATUS_PARTIAL_SUCCESS);
        } else if (success > 0) {
            batch.setStatus(STATUS_SUCCESS);
        } else {
            batch.setStatus(STATUS_FAILED);
        }
        batchMapper.updateById(batch);
    }

    private void markTaskRunning(BatchArticleGenerationTask task) {
        task.setStatus(STATUS_RUNNING);
        task.setStartedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    private void markTaskSuccess(BatchArticleGenerationTask task,
                                 Long articleId,
                                 BatchArticlePromptBuilder.PromptBuildResult prompt,
                                 ModelSelection model,
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
        task.setFinishedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    private void markTaskFailed(BatchArticleGenerationTask task, Exception ex) {
        task.setStatus(STATUS_FAILED);
        task.setErrorMessage(errorMessage(ex));
        task.setFinishedAt(LocalDateTime.now());
        taskMapper.updateById(task);
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
                                                String topicSource,
                                                List<BatchArticleGenerateRequest.TopicConfig> topics) {
        if (topics == null || topics.isEmpty()) {
            throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "Topics are required");
        }
        List<ValidatedTopic> result = new ArrayList<>();
        for (BatchArticleGenerateRequest.TopicConfig topicConfig : topics) {
            String topic = trim(topicConfig.getTopic());
            if (!StringUtils.hasText(topic)) {
                throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "Topic is required");
            }
            KeywordGroup keywordGroup = validateKeywordGroup(projectId, topicSource, topicConfig.getKeywordGroupId());
            List<ValidatedPlatform> platforms = validatePlatforms(topicConfig.getPlatforms());
            if (platforms.stream().mapToInt(platform -> platform.count() == null ? 0 : platform.count()).sum() <= 0) {
                continue;
            }
            result.add(new ValidatedTopic(
                    topic,
                    trimToNull(topicConfig.getTopicAsQuestion()),
                    keywordGroup == null ? null : keywordGroup.getId(),
                    keywordGroup == null ? trimToNull(topicConfig.getKeywordGroupName()) : keywordGroup.getName(),
                    platforms
            ));
        }
        if (result.isEmpty()) {
            throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "At least one topic must generate articles");
        }
        return result;
    }

    private List<ValidatedPlatform> validatePlatforms(List<BatchArticleGenerateRequest.PlatformCount> platforms) {
        if (platforms == null || platforms.isEmpty()) {
            throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "Platform counts are required");
        }
        List<ValidatedPlatform> result = new ArrayList<>();
        for (BatchArticleGenerateRequest.PlatformCount platform : platforms) {
            String contentStyle = trim(platform.getContentStyle());
            if (!SUPPORTED_CONTENT_STYLES.contains(contentStyle) || !CREATABLE_CONTENT_STYLES.contains(contentStyle)) {
                throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "Invalid content style");
            }
            int count = platform.getCount() == null ? 0 : platform.getCount();
            if (count < 0) {
                throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "Invalid article count");
            }
            if (count == 0) {
                continue;
            }
            result.add(new ValidatedPlatform(contentStyle, count, trimToNull(platform.getExtraPrompt())));
        }
        return result;
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
        return brandStatementService.resolvePromptStatement(brand);
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
        snapshot.put("promptTokens", result.promptTokens());
        snapshot.put("completionTokens", result.completionTokens());
        return writeJson(snapshot);
    }

    private String responseSnapshot(LlmInvokeResult result) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
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

    private String errorMessage(Exception ex) {
        if (ex instanceof LlmInvokeException) {
            return "AI article generation failed";
        }
        return StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : ex.getClass().getSimpleName();
    }

    private record ModelSelection(String platformCode, String modelId, LlmModelConfig config) {
    }

    private record ValidatedTopic(String topic,
                                  String topicAsQuestion,
                                  Long keywordGroupId,
                                  String keywordGroupName,
                                  List<ValidatedPlatform> platforms) {
    }

    private record ValidatedPlatform(String contentStyle, Integer count, String extraPrompt) {
    }
}
