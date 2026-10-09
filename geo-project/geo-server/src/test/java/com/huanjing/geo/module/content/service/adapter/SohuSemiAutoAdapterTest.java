package com.huanjing.geo.module.content.service.adapter;

import com.huanjing.geo.module.content.config.SemiAutoPlatformProperties;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.service.render.MarkdownToHtmlRenderer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SohuSemiAutoAdapterTest {
    @Test
    void configuredSohuProfilePreparesSafeContentWithAiDeclaration() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))
                .forEach(source -> environment.getPropertySources().addLast(source));
        SemiAutoPlatformProperties properties = Binder.get(environment)
                .bind("semi-auto.platforms", SemiAutoPlatformProperties.class).get();
        SohuSemiAutoAdapter adapter = new SohuSemiAutoAdapter(properties, new MarkdownToHtmlRenderer());
        ArticleDraft article = new ArticleDraft();
        article.setTitle("如何整理一份清晰的工作交接清单");
        SemiAutoFillTask task = adapter.prepareFillTask(article, """
                # 如何整理一份清晰的工作交接清单

                ## 记录工作事项

                **明确负责人**。<script>alert(1)</script>

                ![目录示意图](https://example.test/directory.png)

                <a href="javascript:alert(1)">链接</a>
                """, adapter.fillProfile());

        assertEquals("sohu", task.platform());
        assertEquals("https://mp.sohu.com/mpfe/v4/contentManagement/news/addarticle?contentStatus=1", task.publishUrl());
        assertTrue(task.renderedHtml().contains("<h2>记录工作事项</h2>"));
        assertTrue(task.renderedHtml().contains("<strong>明确负责人</strong>"));
        assertFalse(task.renderedHtml().contains("<h1>"));
        assertFalse(task.renderedHtml().contains("<script"));
        assertFalse(task.renderedHtml().contains("javascript:"));
        assertTrue(task.renderedHtml().contains("<img src=\"https://example.test/directory.png\" alt=\"目录示意图\""));
        assertEquals("含有AI生成内容", ((Map<?, ?>) adapter.fillProfile().platformOptions().get("sohu")).get("creationStatement"));
    }
}
