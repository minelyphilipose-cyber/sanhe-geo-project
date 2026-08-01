package com.huanjing.geo.module.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.dto.DouyinImageTextPublishSnapshot;
import com.huanjing.geo.module.content.dto.DouyinImageTextQuickPublishRequest;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.entity.BrandMaterial;
import com.huanjing.geo.module.customer.mapper.BrandMaterialMapper;
import com.huanjing.geo.module.customer.service.BrandMaterialPublicUrlService;
import com.huanjing.geo.module.system.entity.SysDictItem;
import com.huanjing.geo.module.system.mapper.SysDictItemMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DouyinImageTextPublishSnapshotServiceTest {
    private BrandMaterialMapper materialMapper;
    private SysDictItemMapper dictMapper;
    private BrandMaterialPublicUrlService publicUrlService;
    private DouyinImageTextPublishSnapshotService service;

    @BeforeEach
    void setUp() {
        materialMapper = mock(BrandMaterialMapper.class);
        dictMapper = mock(SysDictItemMapper.class);
        publicUrlService = mock(BrandMaterialPublicUrlService.class);
        service = new DouyinImageTextPublishSnapshotService(
                materialMapper,
                dictMapper,
                publicUrlService,
                new ObjectMapper().findAndRegisterModules()
        );
    }

    @Test
    void resolvesChineseCityAndIndustryIntoTopic() {
        Brand brand = brand("安徽省阜阳市", "全屋定制");

        DouyinImageTextPublishSnapshotService.TopicPreview preview = service.previewTopic(brand);

        assertEquals("阜阳", preview.topicRegionText());
        assertEquals("全屋定制", preview.topicIndustryText());
        assertEquals("#阜阳全屋定制", preview.topicQuery());
        assertEquals("cityName", preview.regionSourceField());
    }

    @Test
    void resolvesAdministrativeCodeAndIndustryCodeFromDictionary() {
        Brand brand = brand("341200", "whole_house_customization");
        SysDictItem region = dict("阜阳市");
        SysDictItem industry = dict("全屋定制");
        when(dictMapper.selectOne(any())).thenReturn(region, industry);

        DouyinImageTextPublishSnapshotService.TopicPreview preview = service.previewTopic(brand);

        assertEquals("#阜阳全屋定制", preview.topicQuery());
    }

    @Test
    void rejectsUnmappedEnglishRegion() {
        Brand brand = brand("Fuyang", "全屋定制");
        when(dictMapper.selectOne(any())).thenReturn(null);

        assertThrows(BizException.class, () -> service.previewTopic(brand));
    }

    @Test
    void createsImmutableSnapshotWithoutExpiringImageUrlsAndDeduplicatesTopic() {
        Brand brand = brand("阜阳市", "全屋定制");
        for (long id = 1; id <= 4; id++) {
            BrandMaterial material = material(id);
            when(materialMapper.selectById(id)).thenReturn(material);
            when(publicUrlService.buildPublicStreamUrl(material)).thenReturn("https://signed.example/" + id);
        }
        DouyinImageTextQuickPublishRequest request = new DouyinImageTextQuickPublishRequest();
        request.setTitle("阜阳装修避坑");
        request.setDescription("正文内容\n#阜阳全屋定制");
        request.setImageMaterialIds(List.of(1L, 2L, 3L, 4L));

        DouyinImageTextPublishSnapshot snapshot = service.build(brand, request);
        String json = service.toJson(snapshot);

        assertEquals("正文内容", snapshot.descriptionBase());
        assertEquals("正文内容\n#阜阳全屋定制", snapshot.finalDescription());
        assertTrue(snapshot.finalDescription().endsWith("\n" + snapshot.topicQuery()));
        assertEquals(List.of(1L, 2L, 3L, 4L), snapshot.imageMaterialIds());
        assertEquals(4, snapshot.expectedImageCount());
        assertFalse(json.contains("signed.example"));
        assertFalse(json.contains("imageUrls"));
    }

    @Test
    void preservesInlineTopicTextAndRemovesOnlyDuplicateTrailingTopicLines() {
        Brand brand = brand("阜阳市", "全屋定制");
        for (long id = 1; id <= 4; id++) {
            BrandMaterial material = material(id);
            when(materialMapper.selectById(id)).thenReturn(material);
            when(publicUrlService.buildPublicStreamUrl(material)).thenReturn("https://signed.example/" + id);
        }
        DouyinImageTextQuickPublishRequest request = new DouyinImageTextQuickPublishRequest();
        request.setTitle("阜阳装修避坑");
        request.setDescription("""
                正文中提到#阜阳全屋定制时必须保留。
                #阜阳全屋定制
                #阜阳全屋定制
                """);
        request.setImageMaterialIds(List.of(1L, 2L, 3L, 4L));

        DouyinImageTextPublishSnapshot snapshot = service.build(brand, request);

        assertEquals("正文中提到#阜阳全屋定制时必须保留。", snapshot.descriptionBase());
        assertEquals("""
                正文中提到#阜阳全屋定制时必须保留。
                #阜阳全屋定制""", snapshot.finalDescription());
    }

    @Test
    void usesServerAutoSelectedImagesWhenClientDoesNotSubmitImageIds() {
        Brand brand = brand("阜阳市", "全屋定制");
        for (long id = 1; id <= 4; id++) {
            BrandMaterial material = material(id);
            when(materialMapper.selectById(id)).thenReturn(material);
            when(publicUrlService.buildPublicStreamUrl(material)).thenReturn("https://signed.example/" + id);
        }
        DouyinImageTextQuickPublishRequest request = new DouyinImageTextQuickPublishRequest();
        request.setTitle("阜阳装修避坑");
        request.setDescription("正文内容");

        DouyinImageTextPublishSnapshot snapshot = service.build(
                brand,
                request,
                List.of(1L, 2L, 3L, 4L)
        );

        assertEquals(List.of(1L, 2L, 3L, 4L), snapshot.imageMaterialIds());
        assertEquals(4, snapshot.expectedImageCount());
    }

    @Test
    void buildsFromGeneratedArticleAndSafelyLimitsLegacyLongContent() {
        Brand brand = brand("阜阳市", "全屋定制");
        for (long id = 1; id <= 4; id++) {
            BrandMaterial material = material(id);
            when(materialMapper.selectById(id)).thenReturn(material);
            when(publicUrlService.buildPublicStreamUrl(material)).thenReturn("https://signed.example/" + id);
        }
        ArticleDraft article = new ArticleDraft();
        article.setTitle("阜阳全屋智能怎么选？这是一段超过二十个字的标题");
        String paragraph = "这是用于验证抖音图文自动收束的完整句子。".repeat(35);
        String markdown = """
                # 阜阳全屋智能怎么选？这是一段超过二十个字的标题

                ![配图](https://example.test/image.jpg)

                %s
                """.formatted(paragraph);

        DouyinImageTextPublishSnapshot snapshot = service.buildFromArticle(
                brand,
                article,
                markdown,
                List.of(1L, 2L, 3L, 4L)
        );

        assertEquals(20, snapshot.title().codePointCount(0, snapshot.title().length()));
        assertFalse(snapshot.descriptionBase().startsWith(article.getTitle()));
        assertFalse(snapshot.descriptionBase().contains("example.test"));
        String finalDescription = snapshot.finalDescription();
        assertTrue(finalDescription.codePointCount(0, finalDescription.length()) <= 1000);
        assertTrue(snapshot.descriptionBase().endsWith("。"));
    }

    private Brand brand(String cityName, String industry) {
        Brand brand = new Brand();
        brand.setId(88L);
        brand.setCityName(cityName);
        brand.setIndustry(industry);
        brand.setPublicAddress("安徽省阜阳市颍州区测试路1号");
        return brand;
    }

    private SysDictItem dict(String value) {
        SysDictItem item = new SysDictItem();
        item.setDictValue(value);
        item.setEnabled(true);
        return item;
    }

    private BrandMaterial material(long id) {
        BrandMaterial material = new BrandMaterial();
        material.setId(id);
        material.setBrandId(88L);
        material.setFileType("jpg");
        material.setFileName(id + ".jpg");
        material.setFileSize(1024L);
        return material;
    }
}
