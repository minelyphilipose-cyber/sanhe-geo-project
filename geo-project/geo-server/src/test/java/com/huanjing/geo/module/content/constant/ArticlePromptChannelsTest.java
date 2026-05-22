package com.huanjing.geo.module.content.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void subCodes_exposesCanonicalDouyinAndXiaohongshu() {
        assertTrue(ArticlePromptChannels.subCodes("self_media").contains("douyin"));
        assertTrue(ArticlePromptChannels.subCodes("self_media").contains("xiaohongshu"));
    }
}
