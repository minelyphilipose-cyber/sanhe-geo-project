package com.huanjing.geo.module.content.service.render;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownToHtmlRendererTest {

    private MarkdownToHtmlRenderer renderer;

    @BeforeEach
    void setUp() {
        renderer = new MarkdownToHtmlRenderer();
    }

    @Test
    void render_basicMarkdown_outputsHtml() {
        String html = renderer.render("# Title\n\nParagraph with [link](https://example.com).\n\n- one\n- two");
        assertTrue(html.contains("<h1>Title</h1>"));
        assertTrue(html.contains("<p>Paragraph with <a href=\"https://example.com\">link</a>.</p>"));
        assertTrue(html.contains("<li>one</li>"));
    }

    @Test
    void render_scriptTag_removesScript() {
        String html = renderer.render("safe<script>alert(1)</script>");
        assertFalse(html.contains("<script"));
        assertFalse(html.contains("alert(1)"));
    }

    @Test
    void render_onclickAttribute_removesAttribute() {
        String html = renderer.render("<button onclick=\"evil()\">Click</button>");
        assertFalse(html.contains("onclick"));
        assertTrue(html.contains("Click"));
    }

    @Test
    void render_javascriptHref_removesHref() {
        String html = renderer.render("<a href=\"javascript:alert(1)\">x</a>");
        assertFalse(html.contains("javascript:"));
        assertTrue(html.contains(">x</a>"));
    }

    @Test
    void render_imageOssUrl_keepsSrc() {
        String url = "https://oss.example.com/path/image.png";
        String html = renderer.render("![alt](" + url + ")");
        assertTrue(html.contains("<img"));
        assertTrue(html.contains("src=\"" + url + "\""));
        assertTrue(html.contains("loading=\"lazy\""));
        assertTrue(html.contains("referrerpolicy=\"no-referrer\""));
    }

    @Test
    void render_inlineImageStyle_keepsContainSizing() {
        String html = renderer.render("""
                <p><img src="https://app.example.com/api/public/brand-materials/1/stream?sig=a" alt="品牌图" style="display:block;max-width:100%;width:auto;height:auto;object-fit:contain;margin:16px auto;border-radius:6px;" /></p>
                """);

        assertTrue(html.contains("height:auto"));
        assertTrue(html.contains("object-fit:contain"));
    }

    @Test
    void render_dataImage_removesImage() {
        String html = renderer.render("![alt](data:image/png;base64,abc)");
        assertFalse(html.contains("<img"));
    }

    @Test
    void render_gfmTable_outputsTable() {
        String html = renderer.render("| A | B |\n|---|---|\n| 1 | 2 |");
        assertTrue(html.contains("<table>"));
        assertTrue(html.contains("<td>1</td>"));
    }

    @Test
    void render_codeBlock_outputsPreCode() {
        String html = renderer.render("```java\nSystem.out.println(\"x\");\n```");
        assertTrue(html.contains("<pre><code class=\"language-java\">"));
        assertTrue(html.contains("System.out.println"));
    }

    @Test
    void render_nullInput_returnsEmptyString() {
        assertEquals("", renderer.render(null));
    }

    @Test
    void render_blankInput_returnsEmptyString() {
        assertEquals("", renderer.render("   \n\t"));
    }
}
