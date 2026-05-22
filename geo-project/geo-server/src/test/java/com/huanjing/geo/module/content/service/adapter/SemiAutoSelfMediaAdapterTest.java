package com.huanjing.geo.module.content.service.adapter;

import com.huanjing.geo.module.content.config.SemiAutoPlatformProperties;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.service.render.MarkdownToHtmlRenderer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemiAutoSelfMediaAdapterTest {

    @Test
    void toutiaoProfileComesFromConfigurationAndRendersWithWhitelist() {
        SemiAutoPlatformProperties properties = new SemiAutoPlatformProperties();
        SemiAutoPlatformProperties.Platform profile = new SemiAutoPlatformProperties.Platform();
        profile.setPublishUrl("https://configured.example/toutiao/publish");
        profile.setCookieDomains(List.of(".toutiao.test"));
        profile.setRequiredCookieNames(List.of("sessionid"));
        profile.setEditorSelectors(Map.of("content", ".ProseMirror"));
        profile.setAllowedHtmlTags(List.of("p", "strong", "a", "img"));
        properties.setProfiles(Map.of(ToutiaoSemiAutoAdapter.PLATFORM, profile));
        ToutiaoSemiAutoAdapter adapter = new ToutiaoSemiAutoAdapter(properties, new MarkdownToHtmlRenderer());

        PlatformFillProfile fillProfile = adapter.fillProfile();
        String html = adapter.renderContent("**bold**\n\n<script>alert(1)</script>\n\n[link](javascript:alert(1))\n\n![x](https://img.example/a.png)", fillProfile);

        assertEquals("https://configured.example/toutiao/publish", fillProfile.publishUrl());
        assertEquals(List.of(".toutiao.test"), fillProfile.cookieDomains());
        assertTrue(html.contains("<strong>bold</strong>"));
        assertTrue(html.contains("<img src=\"https://img.example/a.png\""));
        assertFalse(html.contains("<script"));
        assertFalse(html.contains("javascript:"));
    }

    @Test
    void zhihuPrepareFillTaskUsesConfiguredPublishUrl() {
        SemiAutoPlatformProperties properties = new SemiAutoPlatformProperties();
        SemiAutoPlatformProperties.Platform profile = new SemiAutoPlatformProperties.Platform();
        profile.setPublishUrl("https://configured.example/zhihu/write");
        profile.setAllowedHtmlTags(List.of("p", "em"));
        properties.setProfiles(Map.of(ZhihuSemiAutoAdapter.PLATFORM, profile));
        ZhihuSemiAutoAdapter adapter = new ZhihuSemiAutoAdapter(properties, new MarkdownToHtmlRenderer());

        ArticleDraft article = new ArticleDraft();
        article.setTitle("Title");
        article.setCoverImageUrl("https://cdn.example/cover.jpg");
        article.setTagsJson("[\"tag-a\",\"tag-b\"]");
        article.setCategory("tech");

        SemiAutoFillTask task = adapter.prepareFillTask(article, "_text_", adapter.fillProfile());

        assertEquals("zhihu", task.platform());
        assertEquals("https://configured.example/zhihu/write", task.publishUrl());
        assertEquals("Title", task.title());
        assertEquals("https://cdn.example/cover.jpg", task.coverImageUrl());
        assertEquals(List.of("tag-a", "tag-b"), task.tags());
        assertEquals("tech", task.category());
        assertTrue(task.renderedHtml().contains("<em>text</em>"));
    }

    @Test
    void xiaohongshuProfileComesFromConfiguration() {
        SemiAutoPlatformProperties properties = new SemiAutoPlatformProperties();
        SemiAutoPlatformProperties.Platform profile = new SemiAutoPlatformProperties.Platform();
        profile.setPublishUrl("https://creator.xiaohongshu.com/publish/publish");
        profile.setCookieDomains(List.of(".xiaohongshu.com", ".xhscdn.com"));
        profile.setRequiredCookieNames(List.of("web_session", "a1"));
        profile.setEditorSelectors(Map.of("title", "input[placeholder*='标题']"));
        profile.setAllowedHtmlTags(List.of("p", "strong", "ul", "li"));
        properties.setProfiles(Map.of(XiaohongshuSemiAutoAdapter.PLATFORM, profile));
        XiaohongshuSemiAutoAdapter adapter = new XiaohongshuSemiAutoAdapter(properties, new MarkdownToHtmlRenderer());

        PlatformFillProfile fillProfile = adapter.fillProfile();
        ArticleDraft article = new ArticleDraft();
        article.setTitle("小红书标题");
        SemiAutoFillTask task = adapter.prepareFillTask(article, "**内容**", fillProfile);

        assertEquals("xiaohongshu", adapter.platform());
        assertEquals("https://creator.xiaohongshu.com/publish/publish", fillProfile.publishUrl());
        assertEquals(List.of(".xiaohongshu.com", ".xhscdn.com"), fillProfile.cookieDomains());
        assertEquals(List.of("web_session", "a1"), fillProfile.requiredCookieNames());
        assertEquals("xiaohongshu", task.platform());
        assertEquals("小红书标题", task.title());
        assertTrue(task.renderedHtml().contains("<strong>内容</strong>"));
    }

    @Test
    void missingProfileFailsFast() {
        SemiAutoPlatformProperties properties = new SemiAutoPlatformProperties();
        ToutiaoSemiAutoAdapter adapter = new ToutiaoSemiAutoAdapter(properties, new MarkdownToHtmlRenderer());

        IllegalStateException ex = assertThrows(IllegalStateException.class, adapter::fillProfile);

        assertTrue(ex.getMessage().contains("missing"));
    }

    @Test
    void emptyAllowedTagsFailsFast() {
        SemiAutoPlatformProperties properties = new SemiAutoPlatformProperties();
        SemiAutoPlatformProperties.Platform profile = new SemiAutoPlatformProperties.Platform();
        profile.setPublishUrl("https://configured.example/toutiao/publish");
        profile.setAllowedHtmlTags(List.of());
        properties.setProfiles(Map.of(ToutiaoSemiAutoAdapter.PLATFORM, profile));
        ToutiaoSemiAutoAdapter adapter = new ToutiaoSemiAutoAdapter(properties, new MarkdownToHtmlRenderer());

        IllegalStateException ex = assertThrows(IllegalStateException.class, adapter::fillProfile);

        assertTrue(ex.getMessage().contains("allowedHtmlTags"));
    }
}
