package com.huanjing.geo.module.content.service.render.wechat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WechatHtmlSanitizerTest {

    private final WechatHtmlSanitizer sanitizer = new WechatHtmlSanitizer();

    @Test
    void templateSanitizerKeepsInlineSvgAndRemovesDangerousStyle() {
        String html = """
                <section style="color:red;background-image:url(https://example.com/bg.png)" onclick="evil()">
                  <svg viewBox="0 0 10 10"><path d="M0 0L10 10" fill="#d33"></path></svg>
                  <script>alert(1)</script>
                </section>
                """;

        String result = sanitizer.sanitizeTemplateHtml(html);

        assertThat(result).contains("<svg", "<path", "fill=\"#d33\"");
        assertThat(result).doesNotContain("onclick", "<script", "background-image", "style=");
    }

    @Test
    void nativeHtmlSanitizerUsesNarrowWhitelist() {
        String html = """
                <ul style="color:red"><li>保留列表</li></ul>
                <section>模板容器</section>
                <svg viewBox="0 0 10 10"><path d="M0 0L10 10"></path></svg>
                """;

        String result = sanitizer.sanitizeNativeHtml(html);

        assertThat(result).contains("<ul><li>保留列表</li></ul>");
        assertThat(result).contains("模板容器");
        assertThat(result).doesNotContain("<section", "<svg", "<path", "style=");
    }

    @Test
    void templateSanitizerRemovesScriptInsideForeignObject() {
        String html = """
                <section>
                  <svg viewBox="0 0 10 10">
                    <foreignObject><script>alert(1)</script><p onclick="evil()">bad</p></foreignObject>
                    <path d="M0 0L10 10" fill="#d33"></path>
                  </svg>
                </section>
                """;

        String result = sanitizer.sanitizeTemplateHtml(html);

        assertThat(result).contains("<svg", "<path");
        assertThat(result).doesNotContain("<foreignObject", "<script", "onclick", "alert(1)");
    }
}
