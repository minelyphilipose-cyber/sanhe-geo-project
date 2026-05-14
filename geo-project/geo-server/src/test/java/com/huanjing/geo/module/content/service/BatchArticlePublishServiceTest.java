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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
    private ProjectMapper projectMapper;
    private BrandMapper brandMapper;
    private PublishSiteMapper publishSiteMapper;
    private CurrentUserService currentUserService;
    private ContentDistributionService contentDistributionService;
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
        projectMapper = mock(ProjectMapper.class);
        brandMapper = mock(BrandMapper.class);
        publishSiteMapper = mock(PublishSiteMapper.class);
        currentUserService = mock(CurrentUserService.class);
        contentDistributionService = mock(ContentDistributionService.class);
        service = new BatchArticlePublishService(
                jobMapper,
                itemMapper,
                articleDraftMapper,
                generationTaskMapper,
                projectMapper,
                brandMapper,
                publishSiteMapper,
                currentUserService,
                contentDistributionService
        );

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
        assertEquals("running", result.getRecords().get(0).getStatus());
        assertEquals(2, result.getRecords().get(0).getTotalCount());
        verify(currentUserService).ensurePermission("project.read");
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
        when(itemMapper.selectCount(any())).thenReturn(1L);

        service.executeDueItems(20);

        verify(itemMapper, never()).update(eq(null), any());
        verify(contentDistributionService, never()).distributeToAsOperator(any(), any(), any());
    }

    @Test
    void executeDueItems_revertsLockWhenAnotherPlatformItemStartsAfterLock() {
        BatchArticlePublishItem item = publishItem(1000L, "industry_site", "pending");
        when(itemMapper.selectList(any())).thenReturn(List.of(item));
        when(itemMapper.selectCount(any())).thenReturn(0L, 1L);
        when(itemMapper.update(eq(null), any())).thenReturn(1);

        service.executeDueItems(20);

        ArgumentCaptor<LambdaUpdateWrapper<BatchArticlePublishItem>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(itemMapper, org.mockito.Mockito.times(2)).update(eq(null), captor.capture());
        assertTrue(captor.getAllValues().get(0).getParamNameValuePairs().values().contains("running"));
        assertTrue(captor.getAllValues().get(1).getParamNameValuePairs().values().contains("pending"));
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
