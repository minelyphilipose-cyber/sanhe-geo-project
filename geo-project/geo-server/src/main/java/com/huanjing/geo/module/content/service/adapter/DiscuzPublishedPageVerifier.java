package com.huanjing.geo.module.content.service.adapter;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DiscuzPublishedPageVerifier {

    static final String EVIDENCE_VERIFIED = "verified";
    static final String EVIDENCE_PENDING_REVIEW = "pending_review";
    static final String EVIDENCE_AMBIGUOUS = "ambiguous";
    static final String EVIDENCE_UNVERIFIED = "unverified";

    static final String MODERATION_ASSUME_APPROVED_AFTER_DELAY = "assume_approved_after_delay";

    private static final Pattern STATIC_THREAD_PATH =
            Pattern.compile("(?:^|/)thread-(\\d+)-\\d+-\\d+\\.html$", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUERY_THREAD_ID = Pattern.compile("(?:^|&)tid=(\\d+)(?:&|$)");
    private static final List<Pattern> SUCCESS_REDIRECT_PATTERNS = List.of(
            Pattern.compile("(?:window\\.)?location(?:\\.href)?\\s*=\\s*[\"']([^\"']+)[\"']",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:window\\.)?location\\.replace\\(\\s*[\"']([^\"']+)[\"']\\s*\\)",
                    Pattern.CASE_INSENSITIVE)
    );

    private DiscuzPublishedPageVerifier() {
    }

    static Verification verify(URI baseUri,
                               String pageUrl,
                               String html,
                               String expectedTitle,
                               String canonicalSelector,
                               String titleSelector,
                               String contentSelector,
                               String moderationSelector,
                               String moderationPendingText,
                               String moderationPolicy,
                               Integer moderationGraceHours) {
        if (baseUri == null || !StringUtils.hasText(html)) {
            return Verification.unverified("detail_page_missing");
        }
        Document document = Jsoup.parse(html, StringUtils.hasText(pageUrl) ? pageUrl : baseUri.toString());
        Element canonical = document.selectFirst(defaultText(canonicalSelector, "link[rel=canonical]"));
        String canonicalUrl = canonical == null ? null : canonical.absUrl("href");
        if (!StringUtils.hasText(canonicalUrl) && canonical != null) {
            canonicalUrl = baseUri.resolve(canonical.attr("href")).toString();
        }
        if (!StringUtils.hasText(canonicalUrl)) {
            return Verification.unverified("canonical_url_missing");
        }

        URI canonicalUri;
        try {
            canonicalUri = URI.create(canonicalUrl.trim());
        } catch (IllegalArgumentException ex) {
            return Verification.unverified("canonical_url_invalid");
        }
        if (!sameOrigin(baseUri, canonicalUri)) {
            return Verification.ambiguous(canonicalUrl, "canonical_cross_origin");
        }

        String threadId = threadId(canonicalUri);
        if (!StringUtils.hasText(threadId)) {
            return Verification.ambiguous(canonicalUrl, "canonical_not_thread_detail");
        }

        Element titleElement = document.selectFirst(defaultText(titleSelector, "#thread_subject"));
        String actualTitle = titleElement == null ? null : titleElement.text();
        if (!sameTitle(expectedTitle, actualTitle)) {
            return Verification.ambiguous(canonicalUrl, threadId, actualTitle, "thread_title_mismatch");
        }
        if (document.selectFirst(contentSelector(contentSelector)) == null) {
            return Verification.ambiguous(canonicalUrl, threadId, actualTitle, "thread_content_missing");
        }

        Element moderationElement =
                document.selectFirst(defaultText(moderationSelector, "h1.ts + .xg1"));
        boolean pendingReview = moderationElement != null
                && StringUtils.hasText(moderationPendingText)
                && moderationElement.text().contains(moderationPendingText.trim());
        if (!pendingReview) {
            return Verification.verified(canonicalUrl, threadId, actualTitle);
        }
        if (MODERATION_ASSUME_APPROVED_AFTER_DELAY.equalsIgnoreCase(
                StringUtils.hasText(moderationPolicy) ? moderationPolicy.trim() : "")
                && Integer.valueOf(24).equals(moderationGraceHours)) {
            return Verification.pendingReview(canonicalUrl, threadId, actualTitle);
        }
        return Verification.unverified(canonicalUrl, threadId, actualTitle,
                MODERATION_ASSUME_APPROVED_AFTER_DELAY.equalsIgnoreCase(
                        StringUtils.hasText(moderationPolicy) ? moderationPolicy.trim() : "")
                        ? "moderation_grace_hours_must_be_24"
                        : "moderation_pending_without_assumed_approval");
    }

    static boolean isThreadDetailUrl(URI baseUri, String value) {
        if (baseUri == null || !StringUtils.hasText(value)) {
            return false;
        }
        try {
            URI uri = baseUri.resolve(value.trim());
            return sameOrigin(baseUri, uri) && StringUtils.hasText(threadId(uri));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    static String extractSuccessThreadDetailUrl(URI baseUri, String html) {
        if (baseUri == null || !StringUtils.hasText(html)) {
            return null;
        }
        Document document = Jsoup.parse(html, baseUri.toString());
        for (Element link : document.select(
                "#messagetext a[href], .alert_info a[href], .alert_right a[href]")) {
            String candidate = normalizedThreadDetailUrl(baseUri, link.attr("href"));
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        for (Element meta : document.select("meta[http-equiv][content]")) {
            if (!"refresh".equalsIgnoreCase(meta.attr("http-equiv"))) {
                continue;
            }
            String content = meta.attr("content");
            int urlIndex = content.toLowerCase(Locale.ROOT).indexOf("url=");
            if (urlIndex >= 0) {
                String candidate = normalizedThreadDetailUrl(
                        baseUri, stripWrappingQuotes(content.substring(urlIndex + 4).trim()));
                if (StringUtils.hasText(candidate)) {
                    return candidate;
                }
            }
        }
        for (Element script : document.select("script")) {
            String source = script.data();
            for (Pattern pattern : SUCCESS_REDIRECT_PATTERNS) {
                Matcher matcher = pattern.matcher(source);
                while (matcher.find()) {
                    String candidate = normalizedThreadDetailUrl(baseUri, matcher.group(1));
                    if (StringUtils.hasText(candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private static String normalizedThreadDetailUrl(URI baseUri, String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String decoded = value.trim().replace("&amp;", "&");
        try {
            URI resolved = baseUri.resolve(decoded);
            return sameOrigin(baseUri, resolved) && StringUtils.hasText(threadId(resolved))
                    ? resolved.toString()
                    : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String stripWrappingQuotes(String value) {
        if (!StringUtils.hasText(value) || value.length() < 2) {
            return value;
        }
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        return (first == '"' && last == '"') || (first == '\'' && last == '\'')
                ? value.substring(1, value.length() - 1)
                : value;
    }

    private static String threadId(URI uri) {
        Matcher staticMatcher = STATIC_THREAD_PATH.matcher(
                uri.getPath() == null ? "" : uri.getPath());
        if (staticMatcher.find()) {
            return staticMatcher.group(1);
        }
        String query = uri.getRawQuery();
        if (!StringUtils.hasText(query)
                || !query.toLowerCase(Locale.ROOT).contains("mod=viewthread")) {
            return null;
        }
        Matcher queryMatcher = QUERY_THREAD_ID.matcher(query);
        return queryMatcher.find() ? queryMatcher.group(1) : null;
    }

    private static boolean sameOrigin(URI expected, URI actual) {
        if (expected == null || actual == null
                || !StringUtils.hasText(expected.getScheme())
                || !StringUtils.hasText(expected.getHost())
                || !StringUtils.hasText(actual.getScheme())
                || !StringUtils.hasText(actual.getHost())) {
            return false;
        }
        return expected.getScheme().equalsIgnoreCase(actual.getScheme())
                && expected.getHost().equalsIgnoreCase(actual.getHost())
                && effectivePort(expected) == effectivePort(actual);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static boolean sameTitle(String expected, String actual) {
        String normalizedExpected = normalizeTitle(expected);
        String normalizedActual = normalizeTitle(actual);
        return StringUtils.hasText(normalizedExpected)
                && normalizedExpected.equals(normalizedActual);
    }

    private static String normalizeTitle(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private static String contentSelector(String value) {
        String selector = defaultText(value, "[id^=\"postmessage_\"]");
        return "[id^=postmessage_]".equals(selector)
                ? "[id^=\"postmessage_\"]"
                : selector;
    }

    record Verification(String publishedUrl,
                        String platformArticleId,
                        String publishedTitle,
                        String evidenceStatus,
                        String evidenceReason) {

        static Verification verified(String url, String threadId, String title) {
            return new Verification(url, threadId, title, EVIDENCE_VERIFIED, null);
        }

        static Verification pendingReview(String url, String threadId, String title) {
            return new Verification(url, threadId, title, EVIDENCE_PENDING_REVIEW, null);
        }

        static Verification ambiguous(String url, String reason) {
            return ambiguous(url, null, null, reason);
        }

        static Verification ambiguous(String url, String threadId, String title, String reason) {
            return new Verification(url, threadId, title, EVIDENCE_AMBIGUOUS, reason);
        }

        static Verification unverified(String reason) {
            return unverified(null, null, null, reason);
        }

        static Verification unverified(String url, String threadId, String title, String reason) {
            return new Verification(url, threadId, title, EVIDENCE_UNVERIFIED, reason);
        }
    }
}
