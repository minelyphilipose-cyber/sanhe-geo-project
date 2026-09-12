package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.llm.LlmCallFacade;
import com.huanjing.geo.common.llm.router.LlmRouteException;
import com.huanjing.geo.common.llm.router.LlmRouteFailureKind;
import com.huanjing.geo.common.storage.ObjectStorageService;
import com.huanjing.geo.module.content.entity.*;
import com.huanjing.geo.module.content.mapper.*;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.customer.entity.BrandMaterial;
import com.huanjing.geo.module.customer.mapper.*;
import com.huanjing.geo.module.customer.service.BrandMaterialPublicUrlService;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DouyinArticleImageServiceTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final DouyinArticleImageBatchMapper batches = mock(DouyinArticleImageBatchMapper.class);
    private final ArticleDraftMapper articles = mock(ArticleDraftMapper.class);
    private final BrandMaterialMapper materials = mock(BrandMaterialMapper.class);
    private final ProjectMapper projects = mock(ProjectMapper.class);
    private final AiPlatformConfigMapper models = mock(AiPlatformConfigMapper.class);
    private final ArkImageClient client = mock(ArkImageClient.class);
    private final LlmCallFacade llm = mock(LlmCallFacade.class);
    private final ObjectStorageService storage = mock(ObjectStorageService.class);
    private final BrandMaterialPublicUrlService urls = mock(BrandMaterialPublicUrlService.class);
    private final TransactionTemplate tx = mock(TransactionTemplate.class);
    private final SelfMediaPublishMaterialSelectionService materialSelection = mock(SelfMediaPublishMaterialSelectionService.class);
    private final ProjectSelfMediaScheduleItemMapper scheduleItems = mock(ProjectSelfMediaScheduleItemMapper.class);
    private DouyinArticleImageService service;

    @BeforeAll
    static void metadata() {
        for (Class<?> type : List.of(DouyinArticleImageBatch.class, ArticleDraft.class, ArticleDraftVersion.class, BrandMaterial.class, AiPlatformConfig.class, SelfMediaPublishSchedule.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), type);
        }
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        service = new DouyinArticleImageService(batches, articles, mock(ArticleDraftVersionMapper.class), mock(BrandMapper.class),
                projects, materials, models, mock(SelfMediaPublishScheduleMapper.class), scheduleItems, mock(ArticleBodyProvider.class),
                urls, storage, mock(CurrentUserService.class), mock(BrandAccessService.class), tx, json, llm, client, materialSelection);
        when(batches.update(isNull(), any())).thenReturn(1);
        Project activeProject = new Project(); activeProject.setId(7L); activeProject.setStatus("active");
        when(projects.selectById(7L)).thenReturn(activeProject);
        doAnswer(inv -> {
            ((Consumer<TransactionStatus>) inv.getArgument(0)).accept(new SimpleTransactionStatus());
            return null;
        }).when(tx).executeWithoutResult(any());
    }

    @Test
    void distributionReadinessUsesPublishValidationAndDoesNotGenerateImages() {
        ArticleDraft article = distributionArticle();
        when(materialSelection.selectDouyinImageTextImages(any(), eq(article), eq("body"))).thenReturn(List.of(9L));
        assertDoesNotThrow(() -> service.requireDistributionImagesReady(article.getId()));
        when(materialSelection.selectDouyinImageTextImages(any(), eq(article), eq("body")))
                .thenThrow(new BizException(409, "图片尚未生成并应用"));
        assertEquals("图片尚未生成并应用", assertThrows(BizException.class,
                () -> service.requireDistributionImagesReady(article.getId())).getMessage());
        verifyNoInteractions(client, storage, llm);
    }

    @Test
    void distributionReadinessRejectsEmptyImages() {
        ArticleDraft article = distributionArticle();
        when(materialSelection.selectDouyinImageTextImages(any(), eq(article), anyString())).thenReturn(List.of());
        assertThrows(BizException.class, () -> service.requireDistributionImagesReady(article.getId()));
    }

    private ArticleDraft distributionArticle() {
        ArticleDraft article = new ArticleDraft();
        article.setId(42L); article.setProjectId(7L); article.setCurrentVersionNo(1);
        article.setChannelGroupCode("self_media"); article.setChannelSubCode("douyin");
        when(articles.selectById(42L)).thenReturn(article);
        SysUser user = new SysUser(); user.setId(2L);
        when(((CurrentUserService) ReflectionTestUtils.getField(service, "users")).requireCurrentUser()).thenReturn(user);
        Project project = new Project(); project.setId(7L);
        when(((ProjectMapper) ReflectionTestUtils.getField(service, "projects")).selectById(7L)).thenReturn(project);
        ArticleDraftVersion version = new ArticleDraftVersion();
        when(((ArticleDraftVersionMapper) ReflectionTestUtils.getField(service, "versions")).selectOne(any())).thenReturn(version);
        when(((ArticleBodyProvider) ReflectionTestUtils.getField(service, "bodies")).getArticleBody(version))
                .thenReturn(new ArticleBodyProvider.ArticleBody(1L, "body", "db", "hash"));
        return article;
    }

    @Test
    void autoGenerationIsOffUntilExplicitlyEnabled() {
        assertTrue(service.prepareAutomatic(1L, 2L));
        assertFalse(service.automaticEnabledFor("self_media", "douyin"));
        service.enqueueForGeneratedArticle(1L, 2L);
        verifyNoInteractions(articles, models, client);
    }

    @Test
    void automaticGenerationIsLimitedToDouyinImageText() {
        ReflectionTestUtils.setField(service, "autoEnabled", true);
        assertTrue(service.automaticEnabledFor("self_media", "douyin"));
        assertTrue(service.automaticEnabledFor("self_media", "douyin_image_text"));
        assertFalse(service.automaticEnabledFor("self_media", "douyin_video"));
        assertFalse(service.automaticEnabledFor("self_media", "toutiao"));
        assertFalse(service.automaticEnabledFor("website", "douyin"));
    }

    @Test
    void automaticBatchAppliesCompletedPicturesWithoutManualButton() {
        DouyinArticleImageBatch batch = batch("minimal"); batch.setAutomatic(true);
        batch.setSourceMarkdown("正文"); batch.setPlanJson(plan()); batch.setMaterialIdsJson("[101,102]");
        when(batches.selectOne(any())).thenReturn(batch); when(batches.selectById(7L)).thenReturn(batch);
        when(articles.selectById(1L)).thenReturn(article()); when(articles.selectOne(any())).thenReturn(article());
        when(articles.update(isNull(), any())).thenReturn(1);
        AiPlatformConfig model = new AiPlatformConfig(); model.setApiUrl("https://ark.cn-beijing.volces.com/api/v3");
        when(models.selectOne(any())).thenReturn(model);
        ArticleDraftVersionMapper versions = (ArticleDraftVersionMapper) ReflectionTestUtils.getField(service, "versions");
        when(versions.selectOne(any())).thenReturn(new ArticleDraftVersion());
        for (long id : List.of(101L,102L)) {
            BrandMaterial material = new BrandMaterial(); material.setId(id); material.setBrandId(8L); material.setSourceArticleId(1L);
            when(materials.selectById(id)).thenReturn(material);
        }
        when(urls.buildPublicStreamUrl(any())).thenAnswer(inv -> "https://app.test/image/"+((BrandMaterial)inv.getArgument(0)).getId());
        service.processNext();
        var captured = org.mockito.ArgumentCaptor.forClass(ArticleDraftVersion.class);
        verify(versions).insert(captured.capture());
        assertEquals(2, captured.getValue().getVersionNo());
        assertEquals("douyin_image", captured.getValue().getGeneratedBy());
        assertTrue(captured.getValue().getContentMarkdown().contains("/101"));
        assertTrue(captured.getValue().getContentMarkdown().contains("/102"));
        verify(scheduleItems).restoreReservedDouyinScheduleAfterImagesApplied(eq(1L), eq(2L), any());
        verify(scheduleItems).markRefundedDouyinScheduleForManualReschedule(eq(1L), eq(2L), any());
        verifyNoInteractions(client);
    }

    @ParameterizedTest
    @CsvSource({"self_media,wechat", "self_media,toutiao", "self_media,sohu", "self_media,xiaohongshu",
            "self_media,douyin_video", "website,douyin", "website,website"})
    void otherChannelsCannotUseImageEndpointsOrAutomaticGeneration(String group, String channel) {
        ArticleDraft other = article();
        other.setChannelGroupCode(group);
        other.setChannelSubCode(channel);
        when(articles.selectById(1L)).thenReturn(other);
        ReflectionTestUtils.setField(service, "autoEnabled", true);

        assertTrue(service.prepareAutomatic(1L, 2L));
        assertThrows(BizException.class, () -> service.list(1L));
        assertThrows(BizException.class, () -> service.generate(1L,
                new DouyinArticleImageService.GenerateRequest("test-request", 4, "auto", null)));
        assertThrows(BizException.class, () -> service.retry(1L, 7L));
        assertThrows(BizException.class, () -> service.apply(1L, 7L));
        verifyNoInteractions(batches, models, client, llm, materials, storage, tx);
    }

    @Test
    void stopsQueuedImageWorkIfArticleChangesChannel() {
        DouyinArticleImageBatch batch = batch("minimal");
        ArticleDraft changed = article();
        changed.setChannelSubCode("toutiao");
        when(batches.selectOne(any())).thenReturn(batch);
        when(articles.selectById(1L)).thenReturn(changed);

        service.processNext();

        assertEquals("failed", batch.getStatus());
        verifyNoInteractions(models, client, llm, materials, storage);
    }

    @Test
    void temporaryPlannerCapacityReturnsBatchToQueue() throws Exception {
        DouyinArticleImageBatch batch = batch("minimal");
        batch.setMaterialIdsJson("[]");
        when(batches.selectOne(any())).thenReturn(batch);
        when(articles.selectById(1L)).thenReturn(article());
        AiPlatformConfig model = new AiPlatformConfig();
        model.setApiUrl("https://ark.cn-beijing.volces.com/api/v3");
        when(models.selectOne(any())).thenReturn(model);
        when(llm.execute(any())).thenThrow(new LlmRouteException(
                LlmRouteFailureKind.ALL_PERMIT_BUSY,
                "All LLM candidates are waiting for permits",
                0,
                null));

        service.processNext();

        assertEquals("queued", batch.getStatus());
        assertEquals("图片生成正在等待模型资源，系统会自动重试，无需操作", batch.getErrorMessage());
        verifyNoInteractions(client, storage, materials);
    }

    @Test
    void plannerAuthenticationFailureStopsForManualAction() throws Exception {
        DouyinArticleImageBatch batch = batch("minimal");
        batch.setMaterialIdsJson("[]");
        when(batches.selectOne(any())).thenReturn(batch);
        when(articles.selectById(1L)).thenReturn(article());
        AiPlatformConfig model = new AiPlatformConfig();
        model.setApiUrl("https://ark.cn-beijing.volces.com/api/v3");
        when(models.selectOne(any())).thenReturn(model);
        when(llm.execute(any())).thenThrow(new LlmRouteException(
                LlmRouteFailureKind.ALL_FAILED,
                "All LLM candidates failed",
                1,
                new BizException(502, "模型请求失败，HTTP 401")));

        service.processNext();

        assertEquals("failed", batch.getStatus());
        assertEquals("分图模型配置不可用，请联系管理员检查", batch.getErrorMessage());
        verifyNoInteractions(client, storage, materials);
    }

    @Test
    void inactiveProjectCancelsQueuedAutomaticBatchBeforeCallingModels() {
        DouyinArticleImageBatch batch = batch("minimal");
        batch.setAutomatic(true);
        batch.setMaterialIdsJson("[]");
        when(batches.selectOne(any())).thenReturn(batch);
        when(articles.selectById(1L)).thenReturn(article());
        Project paused = new Project(); paused.setId(7L); paused.setStatus("paused");
        when(projects.selectById(7L)).thenReturn(paused);

        service.processNext();

        assertEquals("cancelled", batch.getStatus());
        assertNull(batch.getErrorMessage());
        verifyNoInteractions(models, client, llm, storage, materials);
    }

    @Test
    void temporaryImageProviderFailureReturnsBatchToQueue() throws Exception {
        DouyinArticleImageBatch batch = batch("minimal");
        batch.setPlanJson(plan());
        batch.setMaterialIdsJson("[]");
        when(batches.selectOne(any())).thenReturn(batch);
        when(articles.selectById(1L)).thenReturn(article());
        AiPlatformConfig model = new AiPlatformConfig();
        model.setApiUrl("https://ark.cn-beijing.volces.com/api/v3");
        when(models.selectOne(any())).thenReturn(model);
        when(client.generate(any(), anyString())).thenThrow(
                new BizException(502, "豆包生图失败，HTTP 429；错误码：RateLimitExceeded"));

        service.processNext();

        assertEquals("queued", batch.getStatus());
        assertEquals("图片模型暂时不可用，系统会自动重试，无需操作", batch.getErrorMessage());
        verifyNoInteractions(storage, materials);
    }

    @Test
    void automaticRetryWindowEndsWithActionableOperatorMessage() throws Exception {
        ReflectionTestUtils.setField(service, "automaticRetryWindowMinutes", 360);
        DouyinArticleImageBatch batch = batch("minimal");
        batch.setAutomatic(true);
        batch.setCreatedAt(java.time.LocalDateTime.now().minusHours(7));
        batch.setPlanJson(plan());
        batch.setMaterialIdsJson("[]");
        when(batches.selectOne(any())).thenReturn(batch);
        when(articles.selectById(1L)).thenReturn(article());
        AiPlatformConfig model = new AiPlatformConfig();
        model.setApiUrl("https://ark.cn-beijing.volces.com/api/v3");
        when(models.selectOne(any())).thenReturn(model);
        when(client.generate(any(), anyString())).thenThrow(
                new BizException(502, "豆包生图失败，HTTP 429；错误码：RateLimitExceeded"));

        service.processNext();

        assertEquals("failed", batch.getStatus());
        assertEquals("图片任务自创建起已等待超过360分钟，系统已停止自动重试，请在文章详情中手动重试",
                batch.getErrorMessage());
        verifyNoInteractions(storage, materials);
    }

    @Test
    void preservesPageOrderEvenWithOneParagraphAndRemovesOldImages() {
        String body = DouyinArticleImageService.insertImages("一段文章。\n\n![旧图](https://old/image.jpg)",
                List.of("https://new/1", "https://new/2", "https://new/3", "https://new/4", "https://new/5", "https://new/6"));
        assertFalse(body.contains("https://old"));
        for (int i = 1; i < 6; i++) assertTrue(body.indexOf("https://new/" + i) < body.indexOf("https://new/" + (i + 1)));
        assertTrue(body.contains("一段文章。"));
    }

    @Test
    void rotatesAwayFromThreeRecentSubjectStyles() {
        List<DouyinArticleImageBatch> history = List.of(batch("editorial"), batch("photographic"), batch("minimal"));
        when(batches.selectList(any())).thenReturn(history);
        for (int i = 0; i < 20; i++) assertFalse(List.of("editorial", "photographic", "minimal").contains(service.selectStyle(8L, "auto")));
        assertEquals("editorial", service.selectStyle(8L, "editorial"));
        assertThrows(BizException.class, () -> service.selectStyle(8L, "unknown"));
    }

    @Test
    void rejectsStaleSourceAndWrongPageCount() throws Exception {
        DouyinArticleImageBatch batch = batch("minimal");
        ArticleDraft article = article();
        DouyinArticleImageService.requireCurrentSource(batch, article);
        article.setCurrentVersionNo(2);
        assertThrows(BizException.class, () -> DouyinArticleImageService.requireCurrentSource(batch, article));
        assertThrows(BizException.class, () -> service.parsePlan(plan(), 4));
        assertEquals(2, service.parsePlan(plan(), 2).path("pages").size());
    }

    @Test
    void zeroImageSpecialIndustryRetryReplansNeutralMedicalCopyBeforeCallingImageModel() throws Exception {
        DouyinArticleImageBatch batch = batch("minimal");
        batch.setPlanJson(plan().replace("主题", "旧推广标题")); batch.setMaterialIdsJson("[]");
        ArticleDraft article = article(); article.setMedicalIndustryCode("medical_beauty");
        when(batches.selectOne(any())).thenReturn(batch);
        when(articles.selectById(1L)).thenReturn(article);
        AiPlatformConfig model = new AiPlatformConfig(); model.setId(9L);
        model.setApiUrl("https://ark.cn-beijing.volces.com/api/v3");
        when(models.selectOne(any())).thenReturn(model);
        var result = mock(com.huanjing.geo.common.llm.LlmCallResult.class, RETURNS_DEEP_STUBS);
        when(result.invokeResult().responseText()).thenReturn(plan().replace("主题", "了解就医信息"));
        when(llm.execute(any())).thenReturn(result);
        when(client.generate(any(), anyString())).thenThrow(new BizException(502, "模拟接口拒绝"));

        service.processNext();

        verify(llm).execute(any());
        assertEquals(DouyinImagePrompts.SPECIAL_INDUSTRY_POLICY,
                json.readTree(batch.getPlanJson()).path("contentPolicy").asText());
        verify(client).generate(eq(model), argThat(prompt -> prompt.contains("了解就医信息")
                && !prompt.contains("旧推广标题") && !prompt.contains("品牌介绍")));
        verifyNoInteractions(storage, materials);
    }

    @Test
    void medicalRetryDoesNotMixPartiallyGeneratedOldPlanWithNewPolicy() throws Exception {
        DouyinArticleImageBatch batch = batch("minimal");
        batch.setPlanJson(plan()); batch.setMaterialIdsJson("[101]");
        ArticleDraft article = article(); article.setMedicalIndustryCode("medical_beauty");
        when(batches.selectOne(any())).thenReturn(batch);
        when(articles.selectById(1L)).thenReturn(article);
        AiPlatformConfig model = new AiPlatformConfig(); model.setApiUrl("https://ark.cn-beijing.volces.com/api/v3");
        when(models.selectOne(any())).thenReturn(model);

        service.processNext();

        assertEquals("failed", batch.getStatus());
        assertEquals("[101]", batch.getMaterialIdsJson());
        assertEquals("这组图片使用旧版方案，请点击“生成一组测试图”重新创建", batch.getErrorMessage());
        verifyNoInteractions(llm, client, storage, materials);
    }

    @Test
    void oralIndustryAlsoUsesNeutralPlanAndOperatorErrorsStayShort() throws Exception {
        DouyinArticleImageBatch batch = batch("minimal");
        batch.setPlanJson(null); batch.setMaterialIdsJson("[]");
        ArticleDraft article = article(); article.setMedicalIndustryCode("oral");
        when(batches.selectOne(any())).thenReturn(batch);
        when(articles.selectById(1L)).thenReturn(article);
        AiPlatformConfig model = new AiPlatformConfig(); model.setApiUrl("https://ark.cn-beijing.volces.com/api/v3");
        when(models.selectOne(any())).thenReturn(model);
        var result = mock(com.huanjing.geo.common.llm.LlmCallResult.class, RETURNS_DEEP_STUBS);
        when(result.invokeResult().responseText()).thenReturn(plan().replace("主题", "就医信息怎么了解"));
        when(llm.execute(any())).thenReturn(result);
        when(client.generate(any(), anyString())).thenThrow(new BizException(502,
                "豆包生图失败，HTTP 400；错误码：InputTextSensitiveContentDetected；Request ID：abc"));

        service.processNext();

        assertEquals("图片文案未通过内容检查，请重新生成一组图片", batch.getErrorMessage());
        verify(llm).execute(any());
        verify(client).generate(eq(model), argThat(prompt -> !prompt.contains("品牌介绍")));
        assertEquals("图片模型当前请求较多，请稍后重试",
                DouyinArticleImageService.operatorError("豆包生图失败，HTTP 429；错误码：RateLimitExceeded"));
        assertEquals("图片模型配置不可用，请联系管理员检查",
                DouyinArticleImageService.operatorError("豆包生图失败，HTTP 401"));
        assertEquals("图片模型未接受本次请求，请联系管理员检查",
                DouyinArticleImageService.operatorError("豆包生图失败，HTTP 400；错误码：InvalidParameter"));
        assertTrue(DouyinArticleImageService.isRetryableAutomaticFailure(
                "All LLM candidates are waiting for permits"));
        assertTrue(DouyinArticleImageService.isRetryableAutomaticFailure(
                "图片模型当前请求较多，请稍后重试"));
        assertTrue(DouyinArticleImageService.isRetryableAutomaticFailure(
                "任务中断或超时，可重试未完成图片"));
        assertFalse(DouyinArticleImageService.isRetryableAutomaticFailure(
                "图片文案未通过内容检查，请重新生成一组图片"));
        assertNull(DouyinArticleImageService.operatorError((String) null));
    }

    @Test
    void retryKeepsCompletedPageAndOnlyGeneratesRemainingPage() throws Exception {
        DouyinArticleImageBatch batch = batch("minimal");
        batch.setPlanJson(plan());
        batch.setMaterialIdsJson("[101]");
        when(batches.selectOne(any())).thenReturn(batch);
        when(articles.selectById(1L)).thenReturn(article());
        AiPlatformConfig model = new AiPlatformConfig();
        model.setId(9L);
        model.setApiUrl("https://ark.cn-beijing.volces.com/api/v3");
        when(models.selectOne(any())).thenReturn(model);
        when(client.generate(eq(model), anyString())).thenReturn(new ArkImageClient.GeneratedImage(new byte[]{1}, "png", "image/png"));
        doAnswer(inv -> {
            BrandMaterial material = inv.getArgument(0);
            assertNotNull(material.getFileUrl(), "file_url must satisfy the MySQL NOT NULL constraint at insert time");
            material.setId(202L);
            return 1;
        }).when(materials).insert(any(BrandMaterial.class));
        when(urls.buildPublicStreamUrl(any())).thenReturn("https://app.test/image/202");
        service.processNext();
        assertEquals("succeeded", batch.getStatus());
        assertEquals("[101,202]", batch.getMaterialIdsJson());
        verify(client, times(1)).generate(eq(model), contains("第2/2页"));
        verify(storage).putBytes(contains("/1/7/2.png"), any(), eq("image/png"));
        verifyNoInteractions(llm);
    }

    private DouyinArticleImageBatch batch(String style) {
        DouyinArticleImageBatch batch = new DouyinArticleImageBatch();
        batch.setId(7L); batch.setArticleId(1L); batch.setSubjectBrandId(8L); batch.setModelConfigId(9L);
        batch.setCreatedBy(2L); batch.setSourceVersionNo(1); batch.setSourceTitle("品牌介绍");
        batch.setStyleCode(style); batch.setImageCount(2); batch.setStatus("queued"); batch.setBrandFactsJson("{\"companyName\":\"示例公司\"}");
        return batch;
    }

    private ArticleDraft article() {
        ArticleDraft article = new ArticleDraft();
        article.setId(1L); article.setTitle("品牌介绍"); article.setCurrentVersionNo(1); article.setStatus("approved");
        article.setProjectId(7L);
        article.setChannelGroupCode("self_media"); article.setChannelSubCode("douyin");
        return article;
    }

    private String plan() {
        return "{\"visualDirection\":\"蓝白\",\"pages\":[{\"title\":\"主题\",\"text\":\"摘要\",\"visual\":\"场景\"},{\"title\":\"服务\",\"text\":\"内容\",\"visual\":\"示意\"}]}";
    }
}
