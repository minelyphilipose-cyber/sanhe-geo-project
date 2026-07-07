package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.content.distribution.DistributionTargetKind;
import com.huanjing.geo.module.content.dto.BatchArticleGenerateRequest;
import com.huanjing.geo.module.content.dto.BatchArticleGenerateResponse;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.entity.BatchArticleGenerationTask;
import com.huanjing.geo.module.content.entity.BrowserEnvironmentAccount;
import com.huanjing.geo.module.content.entity.ContentAutoDistributionBatch;
import com.huanjing.geo.module.content.entity.ContentAutoDistributionItem;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.mapper.ArticleDraftMapper;
import com.huanjing.geo.module.content.mapper.BatchArticleGenerationTaskMapper;
import com.huanjing.geo.module.content.mapper.BatchArticlePublishItemMapper;
import com.huanjing.geo.module.content.mapper.ContentAutoDistributionBatchMapper;
import com.huanjing.geo.module.content.mapper.ContentAutoDistributionItemMapper;
import com.huanjing.geo.module.content.mapper.ContentAutoDistributionPlanMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaPublishScheduleMapper;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.project.entity.KeywordGroupResult;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.entity.ProjectChannelAllocation;
import com.huanjing.geo.module.project.mapper.KeywordGroupResultMapper;
import com.huanjing.geo.module.project.mapper.ProjectChannelAllocationMapper;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.mapper.PublishSiteMapper;
import com.huanjing.geo.module.system.mapper.SysUserMapper;
import com.huanjing.geo.module.system.service.SystemAlertService;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContentAutoDistributionServiceTest {

    private ProjectChannelAllocationMapper allocationMapper;
    private KeywordGroupResultMapper keywordGroupResultMapper;
    private ContentAutoDistributionBatchMapper batchMapper;
    private ContentAutoDistributionItemMapper itemMapper;
    private BatchArticleGenerationTaskMapper generationTaskMapper;
    private ArticleDraftMapper articleDraftMapper;
    private SelfMediaAccountMapper selfMediaAccountMapper;
    private BatchArticleGenerationService generationService;
    private BatchArticlePublishService publishService;
    private SelfMediaScheduleCapabilityService scheduleCapabilityService;
    private BrowserEnvironmentService browserEnvironmentService;
    private ContentAutoDistributionService service;

    @BeforeEach
    void setUp() {
        initTableInfo(ContentAutoDistributionBatch.class);
        initTableInfo(ContentAutoDistributionItem.class);
        initTableInfo(ArticleDraft.class);
        allocationMapper = mock(ProjectChannelAllocationMapper.class);
        keywordGroupResultMapper = mock(KeywordGroupResultMapper.class);
        batchMapper = mock(ContentAutoDistributionBatchMapper.class);
        itemMapper = mock(ContentAutoDistributionItemMapper.class);
        generationTaskMapper = mock(BatchArticleGenerationTaskMapper.class);
        articleDraftMapper = mock(ArticleDraftMapper.class);
        selfMediaAccountMapper = mock(SelfMediaAccountMapper.class);
        generationService = mock(BatchArticleGenerationService.class);
        publishService = mock(BatchArticlePublishService.class);
        scheduleCapabilityService = mock(SelfMediaScheduleCapabilityService.class);
        browserEnvironmentService = mock(BrowserEnvironmentService.class);
        service = new ContentAutoDistributionService(
                mock(ProjectMapper.class),
                allocationMapper,
                keywordGroupResultMapper,
                mock(BrandMapper.class),
                mock(CompanyMapper.class),
                mock(PublishSiteMapper.class),
                mock(SysUserMapper.class),
                batchMapper,
                itemMapper,
                mock(ContentAutoDistributionPlanMapper.class),
                generationTaskMapper,
                mock(BatchArticlePublishItemMapper.class),
                articleDraftMapper,
                selfMediaAccountMapper,
                mock(SelfMediaPublishScheduleMapper.class),
                generationService,
                publishService,
                mock(SelfMediaPublishScheduleService.class),
                scheduleCapabilityService,
                browserEnvironmentService,
                mock(SystemAlertService.class),
                mock(ForumBoardRoutingService.class),
                mock(StringRedisTemplate.class)
        );
    }

    @Test
    void createProjectPlanPlansOnlyMatchingSelfMediaPlatformQuotaChannel() {
        Project project = new Project();
        project.setId(11L);
        project.setCompanyId(7L);
        project.setBrandId(3L);
        ProjectChannelAllocation allocation = new ProjectChannelAllocation();
        allocation.setChannelCode("self_media:zhihu");
        allocation.setAllocatedCount(1);
        KeywordGroupResult question = new KeywordGroupResult();
        question.setId(101L);
        question.setKeywordText("如何选择服务商");
        SelfMediaAccount zhihu = account(201L, "zhihu");
        SelfMediaAccount toutiao = account(202L, "toutiao");
        BrowserEnvironmentAccount binding = new BrowserEnvironmentAccount();
        binding.setId(301L);

        when(allocationMapper.selectList(any())).thenReturn(List.of(allocation));
        when(keywordGroupResultMapper.selectProjectQuestionsByTiers(11L, "'A'")).thenReturn(List.of(question));
        when(selfMediaAccountMapper.selectList(any())).thenReturn(List.of(toutiao, zhihu));
        when(scheduleCapabilityService.readiness("zhihu"))
                .thenReturn(new SelfMediaScheduleCapabilityService.PlatformScheduleReadiness(true, null, null, null));
        when(browserEnvironmentService.validateForTaskCreation(zhihu)).thenReturn(binding);
        when(itemMapper.selectList(any())).thenReturn(List.of());
        doAnswer(invocation -> {
            ContentAutoDistributionBatch batch = invocation.getArgument(0);
            batch.setId(500L);
            return 1;
        }).when(batchMapper).insert(any(ContentAutoDistributionBatch.class));

        service.createProjectPlan(project, LocalDate.of(2026, 6, 8), 900L);

        ArgumentCaptor<ContentAutoDistributionItem> itemCaptor = ArgumentCaptor.forClass(ContentAutoDistributionItem.class);
        verify(itemMapper).insert(itemCaptor.capture());
        ContentAutoDistributionItem item = itemCaptor.getValue();
        assertEquals("self_media:zhihu", item.getChannelCode());
        assertEquals(ArticlePromptChannels.SELF_MEDIA, item.getChannelGroupCode());
        assertEquals("zhihu", item.getContentStyle());
        assertEquals(DistributionTargetKind.MP_ACCOUNT, item.getTargetKind());
        assertEquals(201L, item.getTargetId());
        assertEquals(101L, item.getQuestionId());
    }

    @Test
    void createProjectPlanSkipsWithPlatformHintWhenSelfMediaAccountMissing() {
        Project project = new Project();
        project.setId(12L);
        project.setCompanyId(7L);
        project.setBrandId(3L);
        ProjectChannelAllocation allocation = new ProjectChannelAllocation();
        allocation.setChannelCode("self_media:zhihu");
        allocation.setAllocatedCount(2);

        when(allocationMapper.selectList(any())).thenReturn(List.of(allocation));
        when(selfMediaAccountMapper.selectList(any())).thenReturn(List.of(account(202L, "toutiao")));

        service.createProjectPlan(project, LocalDate.of(2026, 6, 8), 900L);

        ArgumentCaptor<ContentAutoDistributionBatch> batchCaptor = ArgumentCaptor.forClass(ContentAutoDistributionBatch.class);
        verify(batchMapper).insert(batchCaptor.capture());
        ContentAutoDistributionBatch batch = batchCaptor.getValue();
        assertEquals("skipped", batch.getStatus());
        assertEquals("自媒体平台 / 知乎 无可用账号，请先在品牌下配置并启用账号", batch.getErrorMessage());
        verify(itemMapper, never()).insert(any(ContentAutoDistributionItem.class));
    }

    @Test
    void createProjectPlanResumesPendingGenerationWhenDailyBatchAlreadyExists() {
        Project project = new Project();
        project.setId(12L);
        project.setCompanyId(8L);
        project.setBrandId(8L);

        ContentAutoDistributionBatch existingBatch = new ContentAutoDistributionBatch();
        existingBatch.setId(282L);
        existingBatch.setProjectId(12L);
        existingBatch.setPlanDate(LocalDate.of(2026, 7, 7));

        ContentAutoDistributionItem item = new ContentAutoDistributionItem();
        item.setId(4299L);
        item.setBatchId(282L);
        item.setProjectId(12L);
        item.setCompanyId(8L);
        item.setBrandId(8L);
        item.setQuestionText("在阜阳，配套售后维护服务好的门诊有哪些");
        item.setChannelGroupCode(ArticlePromptChannels.FORUM);
        item.setContentStyle("forum");
        item.setStatus("pending_generation");

        ContentAutoDistributionItem selfMediaItem = new ContentAutoDistributionItem();
        selfMediaItem.setId(4304L);
        selfMediaItem.setBatchId(282L);
        selfMediaItem.setProjectId(12L);
        selfMediaItem.setCompanyId(8L);
        selfMediaItem.setBrandId(8L);
        selfMediaItem.setQuestionText("阜阳本地牙齿修复怎么选");
        selfMediaItem.setChannelCode("self_media:baijiahao");
        selfMediaItem.setChannelGroupCode(ArticlePromptChannels.SELF_MEDIA);
        selfMediaItem.setContentStyle("baijiahao");
        selfMediaItem.setStatus("pending_generation");

        BatchArticleGenerationTask task = new BatchArticleGenerationTask();
        task.setId(9001L);
        task.setBatchId(7001L);
        task.setArticleIndexInBatch(1);
        BatchArticleGenerationTask selfMediaTask = new BatchArticleGenerationTask();
        selfMediaTask.setId(9002L);
        selfMediaTask.setBatchId(7001L);
        selfMediaTask.setArticleIndexInBatch(2);

        when(batchMapper.selectOne(any())).thenReturn(existingBatch);
        when(itemMapper.selectList(any())).thenReturn(List.of(item, selfMediaItem), List.of(), List.of(), List.of(), List.of());
        when(generationService.createSystemBatch(any(), eq(900L)))
                .thenReturn(new BatchArticleGenerateResponse(7001L, 2, "pending"));
        when(generationTaskMapper.selectList(any())).thenReturn(List.of(task, selfMediaTask));

        service.createProjectPlan(project, LocalDate.of(2026, 7, 7), 900L);

        ArgumentCaptor<BatchArticleGenerateRequest> requestCaptor = ArgumentCaptor.forClass(BatchArticleGenerateRequest.class);
        verify(generationService).createSystemBatch(requestCaptor.capture(), eq(900L));
        BatchArticleGenerateRequest request = requestCaptor.getValue();
        assertEquals(ArticlePromptChannels.FORUM, request.getTopics().get(0).getPlatforms().get(0).getChannelGroupCode());
        assertEquals(null, request.getTopics().get(0).getPlatforms().get(0).getChannelSubCode());
        assertEquals("baijiahao", request.getTopics().get(1).getPlatforms().get(0).getChannelSubCode());
        ArgumentCaptor<LambdaUpdateWrapper<ContentAutoDistributionItem>> updateCaptor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(itemMapper, org.mockito.Mockito.atLeastOnce()).update(eq(null), updateCaptor.capture());
        String params = updateCaptor.getAllValues().stream()
                .flatMap(wrapper -> wrapper.getParamNameValuePairs().values().stream())
                .map(String::valueOf)
                .toList()
                .toString();
        assertTrue(params.contains("7001"));
        assertTrue(params.contains("9001"));
        assertTrue(params.contains("9002"));
        assertTrue(params.contains("generating"));
    }

    @Test
    void progressActivePlansFailsGeneratedItemWhenArticleAlreadyDistributed() {
        ContentAutoDistributionBatch batch = new ContentAutoDistributionBatch();
        batch.setId(70L);
        batch.setProjectId(990006017L);
        batch.setPlanDate(LocalDate.of(2026, 6, 25));
        batch.setStatus("created");

        ContentAutoDistributionItem generated = new ContentAutoDistributionItem();
        generated.setId(1176L);
        generated.setBatchId(70L);
        generated.setProjectId(990006017L);
        generated.setArticleId(990006884L);
        generated.setStatus("generated");
        generated.setTargetKind(DistributionTargetKind.BRAND_GEO_SITE);
        generated.setContentStyle("linkedin");
        generated.setPlannedPublishAt(LocalDateTime.of(2026, 6, 25, 15, 30));

        ContentAutoDistributionItem failed = new ContentAutoDistributionItem();
        failed.setId(1176L);
        failed.setBatchId(70L);
        failed.setArticleId(990006884L);
        failed.setStatus("failed");

        ArticleDraft article = new ArticleDraft();
        article.setId(990006884L);
        article.setStatus("distributed");

        when(batchMapper.selectList(any())).thenReturn(List.of(batch));
        when(itemMapper.selectList(any())).thenReturn(
                List.of(),
                List.of(),
                List.of(generated),
                List.of(),
                List.of(),
                List.of(failed)
        );
        when(articleDraftMapper.selectById(990006884L)).thenReturn(article);

        service.progressActivePlans();

        ArgumentCaptor<LambdaUpdateWrapper<ContentAutoDistributionItem>> updateCaptor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(itemMapper).update(eq(null), updateCaptor.capture());
        String params = updateCaptor.getValue().getParamNameValuePairs().values().toString();
        assertTrue(params.contains("failed"));
        assertTrue(params.contains("文章当前状态不可发布：distributed"));
        verify(publishService, never()).createSystemScheduledJob(any(), any(), any());
    }

    private SelfMediaAccount account(Long id, String platform) {
        SelfMediaAccount account = new SelfMediaAccount();
        account.setId(id);
        account.setBrandId(3L);
        account.setPlatform(platform);
        account.setAccountName(platform + "-account");
        account.setStatus("active");
        return account;
    }

    private void initTableInfo(Class<?> entityClass) {
        TableName tableName = entityClass.getAnnotation(TableName.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), tableName == null ? "" : tableName.value()),
                entityClass
        );
    }
}
