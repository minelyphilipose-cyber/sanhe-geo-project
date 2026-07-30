package com.huanjing.geo.module.content.service.adapter;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class DiscuzPublishedPageVerifierTest {

    private static final String TITLE = "阜阳值得了解的本地服务信息";

    @Test
    void verifiesRightComThreadFromCanonicalInsteadOfUnrelatedPageLinks() {
        String html = """
                <html>
                  <head>
                    <link rel="canonical"
                          href="https://www.right.com.cn/forum/thread-8483770-1-1.html">
                  </head>
                  <body>
                    <a href="https://www.right.com.cn/forum/thread-8287728-1-1.html">举报不良信息</a>
                    <span id="thread_subject">%s</span>
                    <div id="postmessage_100">正文</div>
                  </body>
                </html>
                """.formatted(TITLE);

        DiscuzPublishedPageVerifier.Verification result = verify(
                URI.create("https://www.right.com.cn/forum/"),
                html,
                "none"
        );

        assertThat(result.evidenceStatus())
                .withFailMessage("unexpected evidence reason: %s", result.evidenceReason())
                .isEqualTo("verified");
        assertThat(result.publishedUrl())
                .isEqualTo("https://www.right.com.cn/forum/thread-8483770-1-1.html");
        assertThat(result.platformArticleId()).isEqualTo("8483770");
    }

    @Test
    void marksAnhuiPendingThreadEligibleOnlyUnderExplicitDelayPolicy() {
        String html = """
                <html>
                  <head>
                    <link rel="canonical" href="https://bbs.ahv.cc/thread-18861-1-1.html">
                  </head>
                  <body>
                    <h1 class="ts"><a id="thread_subject">%s</a></h1>
                    <span class="xg1">（审核中）</span>
                    <div id="postmessage_200">正文</div>
                  </body>
                </html>
                """.formatted(TITLE);

        DiscuzPublishedPageVerifier.Verification delayed = verify(
                URI.create("https://bbs.ahv.cc/"),
                html,
                DiscuzPublishedPageVerifier.MODERATION_ASSUME_APPROVED_AFTER_DELAY
        );
        DiscuzPublishedPageVerifier.Verification strict = verify(
                URI.create("https://bbs.ahv.cc/"),
                html,
                "none"
        );

        assertThat(delayed.evidenceStatus())
                .withFailMessage("unexpected evidence reason: %s", delayed.evidenceReason())
                .isEqualTo("pending_review");
        assertThat(delayed.publishedUrl()).isEqualTo("https://bbs.ahv.cc/thread-18861-1-1.html");
        assertThat(strict.evidenceStatus()).isEqualTo("unverified");
        assertThat(strict.evidenceReason()).isEqualTo("moderation_pending_without_assumed_approval");
    }

    @Test
    void rejectsUnsupportedPendingReviewGracePeriod() {
        String html = """
                <html>
                  <head>
                    <link rel="canonical" href="https://bbs.ahv.cc/thread-18861-1-1.html">
                  </head>
                  <body>
                    <h1 class="ts"><span id="thread_subject">%s</span></h1>
                    <span class="xg1">（审核中）</span>
                    <div id="postmessage_200">正文</div>
                  </body>
                </html>
                """.formatted(TITLE);

        DiscuzPublishedPageVerifier.Verification result = DiscuzPublishedPageVerifier.verify(
                URI.create("https://bbs.ahv.cc/"),
                "https://bbs.ahv.cc/thread-18861-1-1.html",
                html,
                TITLE,
                "link[rel=canonical]",
                "#thread_subject",
                "[id^=\"postmessage_\"]",
                "h1.ts + .xg1",
                "审核中",
                DiscuzPublishedPageVerifier.MODERATION_ASSUME_APPROVED_AFTER_DELAY,
                12
        );

        assertThat(result.evidenceStatus()).isEqualTo("unverified");
        assertThat(result.evidenceReason()).isEqualTo("moderation_grace_hours_must_be_24");
    }

    @Test
    void rejectsBoardUrlTitleMismatchAndCrossOriginCanonical() {
        String boardHtml = html("https://bbs.ahv.cc/forum-124-1.html", TITLE);
        String wrongTitleHtml = html("https://bbs.ahv.cc/thread-18861-1-1.html", "另一篇文章");
        String crossOriginHtml = html("https://example.com/thread-18861-1-1.html", TITLE);

        assertThat(verify(URI.create("https://bbs.ahv.cc/"), boardHtml, "none").evidenceStatus())
                .isEqualTo("ambiguous");
        assertThat(verify(URI.create("https://bbs.ahv.cc/"), wrongTitleHtml, "none").evidenceReason())
                .isEqualTo("thread_title_mismatch");
        assertThat(verify(URI.create("https://bbs.ahv.cc/"), crossOriginHtml, "none").evidenceReason())
                .isEqualTo("canonical_cross_origin");
    }

    private DiscuzPublishedPageVerifier.Verification verify(URI baseUri,
                                                            String html,
                                                            String moderationPolicy) {
        return DiscuzPublishedPageVerifier.verify(
                baseUri,
                baseUri.toString(),
                html,
                TITLE,
                "link[rel=canonical]",
                "#thread_subject",
                "[id^=\"postmessage_\"]",
                "h1.ts + .xg1",
                "审核中",
                moderationPolicy,
                24
        );
    }

    private String html(String canonicalUrl, String title) {
        return """
                <html>
                  <head><link rel="canonical" href="%s"></head>
                  <body>
                    <span id="thread_subject">%s</span>
                    <div id="postmessage_1">正文</div>
                  </body>
                </html>
                """.formatted(canonicalUrl, title);
    }
}
