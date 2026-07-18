package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.distribution.TargetContext;
import com.huanjing.geo.module.content.dto.BatchArticlePublishJobSummary;
import com.huanjing.geo.module.content.dto.BatchArticlePublishRequest;
import com.huanjing.geo.module.content.dto.BatchArticlePublishResponse;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.entity.BatchArticleGenerationTask;
import com.huanjing.geo.module.content.entity.BatchArticlePublishItem;
import com.huanjing.geo.module.content.entity.BatchArticlePublishJob;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.mapper.ArticleDraftMapper;
import com.huanjing.geo.module.content.mapper.BatchArticleGenerationTaskMapper;
import com.huanjing.geo.module.content.mapper.BatchArticlePublishItemMapper;
import com.huanjing.geo.module.content.mapper.BatchArticlePublishJobMapper;
import com.huanjing.geo.module.content.mapper.DistributionTaskMapper;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.PublishSite;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.PublishSiteMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BatchArticlePublishServiceTest {

    private BatchArticlePublishJobMapper jobMapper;
    private BatchArticlePublishItemMapper itemMapper;
    private ArticleDraftMapper articleDraftMapper;
    private BatchArticleGenerationTaskMapper generationTaskMapper;
    private DistributionTaskMapper distributionTaskMapper;
    private ProjectMapper projectMapper;
    private BrandMapper brandMapper;
    private PublishSiteMapper publishSiteMapper;
    private CurrentUserService currentUserService;
    private ContentDistributionService contentDistributionService;
    private ForumBoardRoutingService forumBoardRoutingService;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private BatchArticlePublishService service;
    private final List<BatchArticlePublishJob> insertedJobs = new ArrayList<>();
    private final List<BatchArticlePublishItem> insertedItems = new ArrayList<>();

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BatchArticlePublishJob.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BatchArticlePublishItem.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ArticleDraft.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BatchArticleGenerationTask.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Project.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Brand.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), PublishSite.class);

        jobMapper = mock(BatchArticlePublishJobMapper.class);
        itemMapper = mock(BatchArticlePublishItemMapper.class);
        articleDraftMapper = mock(ArticleDraftMapper.class);
        generationTaskMapper = mock(BatchArticleGenerationTaskMapper.class);
        distributionTaskMapper = mock(DistributionTaskMapper.class);
        projectMapper = mock(ProjectMapper.class);
        brandMapper = mock(BrandMapper.class);
        publishSiteMapper = mock(PublishSiteMapper.class);
        currentUserService = mock(CurrentUserService.class);
        contentDistributionService = mock(ContentDistributionService.class);
        forumBoardRoutingService = mock(ForumBoardRoutingService.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(any(), any(), anyLong(), any())).thenReturn(true);
        service = new BatchArticlePublishService(
                jobMapper,
                itemMapper,
                articleDraftMapper,
                generationTaskMapper,
                distributionTaskMapper,
                projectMapper,
                brandMapper,
                publishSiteMapper,
                currentUserService,
                contentDistributionService,
                forumBoardRoutingService,
                redisTemplate
        );
        AtomicBoolean executorBusy = new AtomicBoolean(false);
        ReflectionTestUtils.setField(service, "batchPublishExecutor", (Executor) command -> {
            if (!executorBusy.compareAndSet(false, true)) {
                return;
            }
            try {
                command.run();
            } finally {
                executorBusy.set(false);
            }
        });

        SysUser operator = new SysUser();
        operator.setId(100L);
        when(currentUserService.requireCurrentUser()).thenReturn(operator);
        when(jobMapper.insert(any(BatchArticlePublishJob.class))).thenAnswer(invocation -> {
            BatchArticlePublishJob job = invocation.getArgument(0);
            job.setId(900L);
            insertedJobs.add(job);
            return 1;
        });
        when(itemMapper.insert(any(BatchArticlePublishItem.class))).thenAnswer(invocation -> {
            BatchArticlePublishItem item = invocation.getArgument(0);
            item.setId(1000L + insertedItems.size());
            insertedItems.add(item);
            return 1;
        });
        when(jobMapper.selectById(900L)).thenAnswer(invocation -> insertedJobs.isEmpty() ? null : insertedJobs.get(0));
        when(itemMapper.selectList(any())).thenAnswer(invocation -> List.copyOf(insertedItems));
    }

    @Test
    void submit_industrySiteWithoutManualTarget_matchesBrandConfiguredSiteCode() {
        givenArticle(1L, "industry_site", 20L);
        givenProject(20L, 30L, "火锅项目");
        Brand brand = brand(30L, "火锅资讯", "hotpot_news");
        PublishSite site = industrySite(66L, "火锅资讯", "hotpot_news");
        when(brandMapper.selectById(30L)).thenReturn(brand);
        when(publishSiteMapper.selectOne(any())).thenReturn(site);
        when(publishSiteMapper.selectById(66L)).thenReturn(site);

        BatchArticlePublishResponse response = service.submit(scheduledRequest(List.of(1L), null));

        assertEquals(900L, response.getJobId());
        assertEquals(1, insertedItems.size());
        BatchArticlePublishItem item = insertedItems.get(0);
        assertEquals("industry_site", item.getPlatformKey());
        assertEquals(66L, item.getTargetSiteId());
        assertEquals("industry_site", item.getContentStyle());
    }

    @Test
    void submit_industrySiteFallbacksToBrandSiteNameWhenCodeLookupMisses() {
        givenArticle(1L, "industry_site", 20L);
        givenProject(20L, 30L, "火锅项目");
        Brand brand = brand(30L, "火锅资讯", "missing_code");
        PublishSite site = industrySite(77L, "火锅资讯", "site_by_name");
        when(brandMapper.selectById(30L)).thenReturn(brand);
        when(publishSiteMapper.selectOne(any())).thenReturn(null, site);
        when(publishSiteMapper.selectById(77L)).thenReturn(site);

        service.submit(scheduledRequest(List.of(1L), null));

        assertEquals(77L, insertedItems.get(0).getTargetSiteId());
    }

    @Test
    void submit_manualIndustrySiteOverridesBrandAutoMatch() {
        givenArticle(1L, "industry_site", 20L);
        givenProject(20L, 30L, "火锅项目");
        PublishSite manualSite = industrySite(88L, "手动资讯站", "manual_site");
        when(publishSiteMapper.selectById(88L)).thenReturn(manualSite);

        service.submit(scheduledRequest(List.of(1L), 88L));

        assertEquals(88L, insertedItems.get(0).getTargetSiteId());
        verify(brandMapper, never()).selectById(any());
        verify(publishSiteMapper, never()).selectOne(any());
    }

    @Test
    void submit_industrySiteRequiresBrandSiteCodeWhenNoManualTarget() {
        givenArticle(1L, "industry_site", 20L);
        givenProject(20L, 30L, "火锅项目");
        when(brandMapper.selectById(30L)).thenReturn(brand(30L, "火锅资讯", null));

        BizException ex = assertThrows(BizException.class, () -> service.submit(scheduledRequest(List.of(1L), null)));

        assertEquals(400, ex.getCode());
        assertEquals("brand industry site code is not configured", ex.getMessage());
    }

    @Test
    void submit_agentStyleTargetsProjectBrand() {
        givenArticle(1L, "linkedin", 20L);
        givenProject(20L, 30L, "官网项目");

        service.submit(scheduledRequest(List.of(1L), null));

        BatchArticlePublishItem item = insertedItems.get(0);
        assertEquals("agent_site", item.getPlatformKey());
        assertEquals(30L, item.getTargetBrandId());
    }

    @Test
    void submit_usesArticleContentStyleWhenNoBatchTaskExists() {
        givenManualArticle(1L, "linkedin", 20L);
        givenProject(20L, 30L, "手动官网项目");

        service.submit(scheduledRequest(List.of(1L), null));

        BatchArticlePublishItem item = insertedItems.get(0);
        assertEquals("agent_site", item.getPlatformKey());
        assertEquals("linkedin", item.getContentStyle());
        assertEquals(30L, item.getTargetBrandId());
    }

    @Test
    void submit_blockedStyleFailsBeforeCreatingItem() {
        givenArticle(1L, "wechat", 20L);
        givenProject(20L, 30L, "公众号项目");

        BizException ex = assertThrows(BizException.class, () -> service.submit(scheduledRequest(List.of(1L), null)));

        assertEquals(400, ex.getCode());
        assertEquals("公众号不允许自动发布", ex.getMessage());
        assertTrue(insertedItems.isEmpty());
    }

    @Test
    void page_mapsJobSummaryForListPage() {
        BatchArticlePublishJob job = new BatchArticlePublishJob();
        job.setId(900L);
        job.setJobName("日常分发");
        job.setPublishMode("scheduled");
        job.setStatus("running");
        job.setTotalCount(2);
        job.setSuccessCount(1);
        job.setFailedCount(0);
        Page<BatchArticlePublishJob> mapperPage = new Page<>(1, 10, 1);
        mapperPage.setRecords(List.of(job));
        when(jobMapper.selectPage(any(Page.class), any())).thenReturn(mapperPage);

        Page<BatchArticlePublishJobSummary> result = service.page(1, 10, "running");

        assertEquals(1, result.getTotal());
        assertEquals(900L, result.getRecords().get(0).getJobId());
        assertEquals("日常分发", result.getRecords().get(0).getJobName());
        assertEquals("running", result.getRecords().get(0).getStatus());
        assertEquals(2, result.getRecords().get(0).getTotalCount());
        verify(currentUserService).ensurePermission("project.read");
    }

    @Test
    void page_rewritesLegacyAutoDistributionJobNameWithProjectName() {
        BatchArticlePublishJob job = new BatchArticlePublishJob();
        job.setId(900L);
        job.setJobName("自动分发_20_2026-05-27");
        job.setPublishMode("scheduled");
        job.setStatus("pending");
        job.setTotalCount(1);
        Page<BatchArticlePublishJob> mapperPage = new Page<>(1, 10, 1);
        mapperPage.setRecords(List.of(job));
        when(jobMapper.selectPage(any(Page.class), any())).thenReturn(mapperPage);
        when(projectMapper.selectById(20L)).thenReturn(project(20L, 30L, "北京火锅项目"));

        Page<BatchArticlePublishJobSummary> result = service.page(1, 10, null);

        assertEquals("自动分发_北京火锅项目_2026-05-27", result.getRecords().get(0).getJobName());
    }

    @Test
    void response_rewritesLegacyAutoDistributionJobNameWithProjectName() {
        BatchArticlePublishJob job = publishJob();
        job.setJobName("自动分发_20_2026-05-27");
        when(jobMapper.selectById(900L)).thenReturn(job);
        when(itemMapper.selectList(any())).thenReturn(List.of());
        when(projectMapper.selectById(20L)).thenReturn(project(20L, 30L, "北京火锅项目"));

        BatchArticlePublishResponse response = service.response(900L);

        assertEquals("自动分发_北京火锅项目_2026-05-27", response.getJobName());
    }

    @Test
    void response_enrichesItemsWithArticleTitleAndProjectName() {
        BatchArticlePublishJob job = publishJob();
        BatchArticlePublishItem item = publishItem(1000L, "industry_site", "pending");
        when(jobMapper.selectById(900L)).thenReturn(job);
        when(itemMapper.selectList(any())).thenReturn(List.of(item));
        ArticleDraft article = article(1L, 20L, "approved");
        article.setTitle("北京火锅加盟怎么选");
        Project project = project(20L, 30L, "北京火锅项目");
        when(articleDraftMapper.selectById(1L)).thenReturn(article);
        when(projectMapper.selectById(20L)).thenReturn(project);

        BatchArticlePublishResponse response = service.response(900L);

        assertEquals("北京火锅加盟怎么选", response.getItems().get(0).getArticleTitle());
        assertEquals("北京火锅项目", response.getItems().get(0).getProjectName());
    }

    @Test
    void executeDueItems_skipsWhenSamePlatformAlreadyRunning() {
        BatchArticlePublishItem item = publishItem(1000L, "industry_site", "pending");
        when(itemMapper.selectList(any())).thenReturn(List.of(item));
        when(valueOperations.setIfAbsent(any(), any(), anyLong(), any())).thenReturn(false);

        service.executeDueItems(20);

        verify(itemMapper, never()).update(eq(null), any());
        verify(contentDistributionService, never()).distributeToAsOperator(any(), any(), any());
    }

    @Test
    void executeDueItems_marksDuplicateTargetSuccessfulAfterLock() {
        BatchArticlePublishItem item = publishItem(1000L, "industry_site", "pending");
        when(itemMapper.selectList(any())).thenReturn(List.of(item));
        when(itemMapper.selectCount(any())).thenReturn(1L);
        when(itemMapper.update(eq(null), any())).thenReturn(1);

        service.executeDueItems(20);

        ArgumentCaptor<LambdaUpdateWrapper<BatchArticlePublishItem>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(itemMapper, org.mockito.Mockito.times(2)).update(eq(null), captor.capture());
        assertTrue(captor.getAllValues().get(0).getParamNameValuePairs().values().contains("running"));
        assertTrue(captor.getAllValues().get(1).getParamNameValuePairs().values().contains("success"));
        verify(contentDistributionService, never()).distributeToAsOperator(any(), any(), any());
    }

    @Test
    void executeDueItems_agentSitePublishesWithBrandTargetAndRefreshesSuccess() {
        BatchArticlePublishJob job = publishJob();
        BatchArticlePublishItem item = publishItem(1000L, "agent_site", "pending");
        item.setTargetBrandId(30L);
        DistributionTask task = new DistributionTask();
        task.setId(700L);
        when(itemMapper.selectList(any())).thenReturn(List.of(item), List.of(successItem(1000L)));
        when(itemMapper.selectCount(any())).thenReturn(0L, 0L);
        when(itemMapper.update(eq(null), any())).thenReturn(1);
        when(jobMapper.selectById(900L)).thenReturn(job);
        when(contentDistributionService.distributeToAsOperator(eq(1L), any(), eq(100L))).thenReturn(task);

        service.executeDueItems(20);

        ArgumentCaptor<TargetContext> targetCaptor = ArgumentCaptor.forClass(TargetContext.class);
        verify(contentDistributionService).distributeToAsOperator(eq(1L), targetCaptor.capture(), eq(100L));
        assertInstanceOf(TargetContext.BrandGeoSiteTarget.class, targetCaptor.getValue());
        verify(jobMapper, org.mockito.Mockito.atLeastOnce()).update(eq(null), any());
    }

    @Test
    void executeDueItems_autoDistributionIndustrySiteFailsWhenBrandConfigRemoved() {
        BatchArticlePublishJob job = publishJob();
        job.setJobName("自动分发_天勇云服务_2026-06-01");
        BatchArticlePublishItem item = publishItem(1000L, "industry_site", "pending");
        item.setTargetSiteId(66L);
        PublishSite staleSite = industrySite(66L, "智装", "zz");
        when(itemMapper.selectList(any())).thenReturn(List.of(item), List.of(item));
        when(itemMapper.selectCount(any())).thenReturn(0L, 0L);
        when(itemMapper.update(eq(null), any())).thenReturn(1);
        when(jobMapper.selectById(900L)).thenReturn(job);
        when(publishSiteMapper.selectById(66L)).thenReturn(staleSite);
        when(projectMapper.selectById(20L)).thenReturn(project(20L, 30L, "天勇云服务"));
        when(brandMapper.selectById(30L)).thenReturn(brand(30L, null, null));

        service.executeDueItems(20);

        ArgumentCaptor<LambdaUpdateWrapper<BatchArticlePublishItem>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(itemMapper, org.mockito.Mockito.times(2)).update(eq(null), captor.capture());
        assertTrue(captor.getAllValues().get(1).getParamNameValuePairs().values().contains("failed"));
        assertTrue(captor.getAllValues().get(1).getParamNameValuePairs().values()
                .contains("品牌行业资讯站配置已取消或变更，跳过旧自动分发计划"));
        verify(contentDistributionService, never()).distributeToAsOperator(any(), any(), any());
    }

    private BatchArticlePublishRequest scheduledRequest(List<Long> articleIds, Long industrySiteId) {
        BatchArticlePublishRequest request = new BatchArticlePublishRequest();
        request.setArticleIds(articleIds);
        request.setPublishMode("scheduled");
        request.setScheduledAt(LocalDateTime.now().plusHours(2).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        request.setIntervalMinutes(30);
        request.setIndustrySiteId(industrySiteId);
        return request;
    }

    private void givenArticle(Long articleId, String contentStyle, Long projectId) {
        when(articleDraftMapper.selectById(articleId)).thenReturn(article(articleId, projectId, "approved"));
        BatchArticleGenerationTask task = new BatchArticleGenerationTask();
        task.setArticleId(articleId);
        task.setContentStyle(contentStyle);
        when(generationTaskMapper.selectOne(any())).thenReturn(task);
    }

    private void givenManualArticle(Long articleId, String contentStyle, Long projectId) {
        ArticleDraft article = article(articleId, projectId, "approved");
        article.setContentStyle(contentStyle);
        when(articleDraftMapper.selectById(articleId)).thenReturn(article);
        when(generationTaskMapper.selectOne(any())).thenReturn(null);
    }

    private void givenProject(Long projectId, Long brandId, String name) {
        when(projectMapper.selectById(projectId)).thenReturn(project(projectId, brandId, name));
    }

    private ArticleDraft article(Long id, Long projectId, String status) {
        ArticleDraft article = new ArticleDraft();
        article.setId(id);
        article.setProjectId(projectId);
        article.setStatus(status);
        article.setTitle("文章 " + id);
        return article;
    }

    private Project project(Long id, Long brandId, String name) {
        Project project = new Project();
        project.setId(id);
        project.setBrandId(brandId);
        project.setProjectName(name);
        return project;
    }

    private Brand brand(Long id, String industrySiteName, String industrySiteCode) {
        Brand brand = new Brand();
        brand.setId(id);
        brand.setIndustrySiteName(industrySiteName);
        brand.setIndustrySiteCode(industrySiteCode);
        return brand;
    }

    private PublishSite industrySite(Long id, String name, String code) {
        PublishSite site = new PublishSite();
        site.setId(id);
        site.setSiteName(name);
        site.setSiteCode(code);
        site.setStatus("active");
        site.setIntegrationMethod("industry_site");
        return site;
    }

    private BatchArticlePublishJob publishJob() {
        BatchArticlePublishJob job = new BatchArticlePublishJob();
        job.setId(900L);
        job.setPublishMode("scheduled");
        job.setStatus("pending");
        job.setTotalCount(1);
        job.setSuccessCount(0);
        job.setFailedCount(0);
        job.setIntervalMinutes(30);
        job.setCreatedBy(100L);
        return job;
    }

    private BatchArticlePublishItem publishItem(Long id, String platformKey, String status) {
        BatchArticlePublishItem item = new BatchArticlePublishItem();
        item.setId(id);
        item.setJobId(900L);
        item.setArticleId(1L);
        item.setProjectId(20L);
        item.setPlatformKey(platformKey);
        item.setContentStyle(platformKey);
        item.setStatus(status);
        item.setPlannedAt(LocalDateTime.now().minusMinutes(1));
        return item;
    }

    private BatchArticlePublishItem successItem(Long id) {
        BatchArticlePublishItem item = publishItem(id, "agent_site", "success");
        item.setDistributionTaskId(700L);
        return item;
    }
}
