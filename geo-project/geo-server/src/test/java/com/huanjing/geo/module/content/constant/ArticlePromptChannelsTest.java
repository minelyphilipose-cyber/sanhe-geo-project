package com.huanjing.geo.module.content.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArticlePromptChannelsTest {

    @Test
    void canonicalSubCode_mapsLegacyDouyinImageTextToDouyin() {
        assertEquals("douyin", ArticlePromptChannels.canonicalSubCode("self_media", "douyin_image_text"));
        assertEquals("douyin", ArticlePromptChannels.contentStyle("self_media", "douyin_image_text"));
        assertEquals("抖音图文", ArticlePromptChannels.channelName("self_media", "douyin_image_text"));
        assertTrue(ArticlePromptChannels.channelGuide("self_media", "douyin_image_text").contains("Open API"));
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
        assertEquals("xiaohongshu", ArticlePromptChannels.canonicalSelfMediaQuotaPlatform("xiaohongshu"));
        assertNull(ArticlePromptChannels.canonicalSelfMediaQuotaPlatform("unknown"));
    }

    @Test
    void subCodes_exposesCanonicalDouyinAndXiaohongshu() {
        assertTrue(ArticlePromptChannels.subCodes("self_media").contains("douyin"));
        assertTrue(ArticlePromptChannels.subCodes("self_media").contains("xiaohongshu"));
        assertEquals(8, ArticlePromptChannels.subCodes("self_media").size());
        assertTrue(ArticlePromptChannels.subCodes("self_media").contains("sohu"));
        assertEquals("搜狐", ArticlePromptChannels.channelName("self_media", "sohu"));
    }
}
