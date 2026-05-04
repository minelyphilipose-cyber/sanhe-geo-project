package com.huanjing.geo.module.content.wechat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WechatHtmlRewriterTest {

    private final WechatHtmlRewriter rewriter = new WechatHtmlRewriter();

    @Test
    void rewriteRemovesUnsafeElementsAndAttributes() {
        String html = """
                <section>
                  <script>alert(1)</script>
                  <p onclick="evil()" style="position:fixed;color:red">hello</p>
                  <a href="javascript:alert(1)" title="x">bad link</a>
                  <iframe src="https://example.com/video"></iframe>
                </section>
                """;

        String result = rewriter.rewrite(html, src -> "https://mmbiz.qpic.cn/rewritten.png");

        assertThat(result).doesNotContain("<script");
        assertThat(result).doesNotContain("onclick");
        assertThat(result).doesNotContain("style=");
        assertThat(result).doesNotContain("javascript:");
        assertThat(result).doesNotContain("<iframe");
        assertThat(result).contains("详情见原文");
    }

    @Test
    void rewriteImagesUsesDataSrcFallbackAndWechatUrl() {
        String html = """
                <p>
                  <img data-src="https://assets.example.com/a.png" alt="a">
                  <img src="">
                </p>
                """;

        String result = rewriter.rewrite(html, src -> "https://mmbiz.qpic.cn/" + src.substring(src.lastIndexOf('/') + 1));

        assertThat(result).contains("src=\"https://mmbiz.qpic.cn/a.png\"");
        assertThat(result).contains("data-src=\"https://mmbiz.qpic.cn/a.png\"");
        assertThat(result).doesNotContain("<img src=\"\"");
    }

    @Test
    void rewriteKeepsSafeStyleAndHttpLinks() {
        String html = """
                <p style="color:#333;text-align:center">
                  <a href="https://example.com/page">safe</a>
                </p>
                """;

        String result = rewriter.rewrite(html, src -> src);

        assertThat(result).contains("style=\"color:#333;text-align:center\"");
        assertThat(result).contains("href=\"https://example.com/page\"");
    }

    @Test
    void rewriteRemovesRelativeLinksAndDangerousStyle() {
        String html = """
                <p style="position:absolute">fixed</p>
                <p style="background:url(https://evil.example/a.png)">tracked</p>
                <a href="/relative/path">relative</a>
                """;

        String result = rewriter.rewrite(html, src -> src);

        assertThat(result).doesNotContain("style=");
        assertThat(result).doesNotContain("href=\"/relative/path\"");
        assertThat(result).contains(">relative</a>");
    }

    @Test
    void rewriteHandlesBlankInput() {
        assertThat(rewriter.rewrite(null, src -> src)).isEmpty();
        assertThat(rewriter.rewrite("   ", src -> src)).isEmpty();
    }
}
