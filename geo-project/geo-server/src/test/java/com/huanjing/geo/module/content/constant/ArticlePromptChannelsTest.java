package com.huanjing.geo.module.content.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArticlePromptChannelsTest {

    @Test
    void canonicalSubCode_mapsLegacyDouyinImageTextToDouyin() {
        assertEquals("douyin", ArticlePromptChannels.canonicalSubCode("self_media", "douyin_image_text"));
        assertEquals("douyin", ArticlePromptChannels.contentStyle("self_media", "douyin_image_text"));
        assertEquals("抖音图文", ArticlePromptChannels.channelName("self_media", "douyin_image_text"));
        assertTrue(ArticlePromptChannels.channelGuide("self_media", "douyin_image_text").contains("抖音图文"));
    }

    @Test
    void canonicalSubCode_mapsWechatMpToWechatQuotaPlatform() {
        assertEquals("wechat", ArticlePromptChannels.canonicalSubCode("self_media", "wechat_mp"));
        assertEquals("wechat", ArticlePromptChannels.canonicalSelfMediaQuotaPlatform("wechat_mp"));
        assertEquals("wechat", ArticlePromptChannels.contentStyle("self_media", "wechat_mp"));
        assertEquals("公众号", ArticlePromptChannels.channelName("self_media", "wechat_mp"));
    }

    @Test
    void canonicalSelfMediaQuotaPlatform_normalizesAndRejectsUnsupportedPlatform() {
        assertEquals("douyin", ArticlePromptChannels.canonicalSelfMediaQuotaPlatform(" DOUYIN_IMAGE_TEXT "));
        assertEquals("wechat", ArticlePromptChannels.canonicalSelfMediaQuotaPlatform(" WECHAT_MP "));
        assertEquals("xiaohongshu", ArticlePromptChannels.canonicalSelfMediaQuotaPlatform("xiaohongshu"));
        assertNull(ArticlePromptChannels.canonicalSelfMediaQuotaPlatform("unknown"));
        assertEquals("douyin", ArticlePromptChannels.normalizeSelfMediaQuotaPlatform(" DOUYIN_IMAGE_TEXT "));
        assertEquals("wechat", ArticlePromptChannels.normalizeSelfMediaQuotaPlatform(" WECHAT_MP "));
    }

    @Test
    void canonicalSelfMediaPublishPlatform_mapsQuotaAndLegacyAliasesToAdapterPlatforms() {
        assertEquals("wechat_mp", ArticlePromptChannels.canonicalSelfMediaPublishPlatform("wechat"));
        assertEquals("wechat_mp", ArticlePromptChannels.canonicalSelfMediaPublishPlatform("WECHAT_MP"));
        assertEquals("douyin", ArticlePromptChannels.canonicalSelfMediaPublishPlatform("douyin_image_text"));
        assertEquals("toutiao", ArticlePromptChannels.canonicalSelfMediaPublishPlatform("toutiao"));
        assertNull(ArticlePromptChannels.canonicalSelfMediaPublishPlatform("unknown"));
        assertEquals("wechat_mp", ArticlePromptChannels.normalizeSelfMediaPublishPlatform("wechat"));
        assertEquals("wechat_mp", ArticlePromptChannels.normalizeSelfMediaPublishPlatform("WECHAT_MP"));
        assertEquals("douyin", ArticlePromptChannels.normalizeSelfMediaPublishPlatform("douyin_image_text"));
    }

    @Test
    void subCodes_exposesCanonicalDouyinAndXiaohongshu() {
        assertTrue(ArticlePromptChannels.subCodes("self_media").contains("douyin"));
        assertTrue(ArticlePromptChannels.subCodes("self_media").contains("xiaohongshu"));
        assertEquals(8, ArticlePromptChannels.subCodes("self_media").size());
        assertTrue(ArticlePromptChannels.subCodes("self_media").contains("sohu"));
        assertEquals("搜狐", ArticlePromptChannels.channelName("self_media", "sohu"));
    }

    @Test
    void stricterEditorialPlatformsKeepMarketingEvidenceBasedAndRestrained() {
        String toutiao = ArticlePromptChannels.channelGuide("self_media", "toutiao");
        String baijiahao = ArticlePromptChannels.channelGuide("self_media", "baijiahao");
        String zhihu = ArticlePromptChannels.channelGuide("self_media", "zhihu");

        assertTrue(toutiao.contains("资讯价值和问题解释为主"));
        assertTrue(toutiao.contains("减少连续品牌露出"));
        assertTrue(baijiahao.contains("可独立成立的知识或资讯价值"));
        assertTrue(baijiahao.contains("不规定品牌进入正文的固定位置"));
        assertTrue(baijiahao.contains("避免宣传口号"));
        assertTrue(zhihu.contains("适用条件和必要权衡"));
        assertTrue(zhihu.contains("介绍和推荐必须有材料依据"));
        assertFalse(toutiao.contains("先讲问题"));
        assertFalse(baijiahao.contains("先形成"));

        String wechat = ArticlePromptChannels.channelGuide("self_media", "wechat");
        assertFalse(wechat.contains("减少连续品牌露出"));
        assertFalse(wechat.contains("避免宣传口号"));
    }

    @Test
    void strictEditorialSelfMediaOnlyCoversToutiaoAndBaijiahao() {
        assertTrue(ArticlePromptChannels.isStrictEditorialSelfMedia("self_media", "toutiao"));
        assertTrue(ArticlePromptChannels.isStrictEditorialSelfMedia("self_media", "baijiahao"));
        assertFalse(ArticlePromptChannels.isStrictEditorialSelfMedia("self_media", "zhihu"));
        assertFalse(ArticlePromptChannels.isStrictEditorialSelfMedia("industry_site", "toutiao"));
    }
}
