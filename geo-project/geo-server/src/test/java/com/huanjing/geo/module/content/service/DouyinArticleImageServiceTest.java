package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.llm.LlmCallFacade;
import com.huanjing.geo.common.storage.ObjectStorageService;
import com.huanjing.geo.module.content.entity.*;
import com.huanjing.geo.module.content.mapper.*;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.customer.entity.BrandMaterial;
import com.huanjing.geo.module.customer.mapper.*;
import com.huanjing.geo.module.customer.service.BrandMaterialPublicUrlService;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
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
    private final AiPlatformConfigMapper models = mock(AiPlatformConfigMapper.class);
    private final ArkImageClient client = mock(ArkImageClient.class);
    private final LlmCallFacade llm = mock(LlmCallFacade.class);
    private final ObjectStorageService storage = mock(ObjectStorageService.class);
    private final BrandMaterialPublicUrlService urls = mock(BrandMaterialPublicUrlService.class);
    private final TransactionTemplate tx = mock(TransactionTemplate.class);
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
                mock(ProjectMapper.class), materials, models, mock(SelfMediaPublishScheduleMapper.class), mock(ArticleBodyProvider.class),
                urls, storage, mock(CurrentUserService.class), mock(BrandAccessService.class), tx, json, llm, client);
        when(batches.update(isNull(), any())).thenReturn(1);
        doAnswer(inv -> {
            ((Consumer<TransactionStatus>) inv.getArgument(0)).accept(new SimpleTransactionStatus());
            return null;
        }).when(tx).executeWithoutResult(any());
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
        article.setChannelGroupCode("self_media"); article.setChannelSubCode("douyin");
        return article;
    }

    private String plan() {
        return "{\"visualDirection\":\"蓝白\",\"pages\":[{\"title\":\"主题\",\"text\":\"摘要\",\"visual\":\"场景\"},{\"title\":\"服务\",\"text\":\"内容\",\"visual\":\"示意\"}]}";
    }
}
