package com.huanjing.geo.module.content.service.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huanjing.geo.common.util.HttpClientUtil;
import com.huanjing.geo.module.content.config.BrandGeoSiteProperties;
import com.huanjing.geo.module.content.distribution.TargetContext;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.service.render.MarkdownToHtmlRenderer;
import com.huanjing.geo.module.system.entity.PublishSite;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class BrandGeoSiteAdapter implements SiteAdapter {

    public static final String PLATFORM = "brand_geo_site";
    private static final String ADMIN_CONTENT_PATH = "/api/v1/admin/content";

    private final ObjectMapper objectMapper;
    private final BrandGeoSiteProperties properties;
    private final MarkdownToHtmlRenderer markdownRenderer;

    @Override
    public boolean supports(String integrationMethod) {
        return PLATFORM.equalsIgnoreCase(integrationMethod);
    }

    @Override
    public boolean supportsPlatform(String platform) {
        return PLATFORM.equalsIgnoreCase(platform);
    }

    @Override
    public ValidationResult validate(ArticleDraft article, String contentMarkdown, PublishSite site) {
        return ValidationResult.pass();
    }

    @Override
    public SubmitResult submit(ArticleDraft article, String contentMarkdown, PublishSite site) {
        throw new UnsupportedOperationException("Agent 官网发布请使用指定目标发布入口");
    }

    @Override
    public String parsePublishedUrl(String responseBody, PublishSite site) {
        return null;
    }

    @Override
    public SubmitResult submitToTarget(ArticleDraft article, String contentMarkdown, TargetContext target) {
        TargetContext.BrandGeoSiteTarget geoTarget = requireTarget(target);
        String contentType = mapContentType(article);
        String endpoint;
        String domain;
        try {
            endpoint = buildEndpoint(geoTarget.domain());
            domain = extractHost(endpoint);
        } catch (Exception ex) {
            return SubmitResult.failure(400, null, null,
                    "Agent 官网域名配置无效：" + safeMessage(ex), FailureKind.CLIENT_ERROR, false);
        }
        String requestPayload;
        try {
            requestPayload = buildRequestPayload(domain, contentType, article, contentMarkdown);
        } catch (Exception ex) {
            return SubmitResult.failure(500, null, null, safeMessage(ex), FailureKind.UNKNOWN, false);
        }

        try {
            log.info("brand_geo_site outbound request articleId={} siteName={} domain={} endpoint={} payloadBytes={}",
                    article == null ? null : article.getId(),
                    geoTarget.siteName(),
                    domain,
                    endpoint,
                    requestPayload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
            HttpClientUtil.HttpResult response = postJson(
                    endpoint,
                    jsonHeaders(),
                    requestPayload,
                    properties.getConnectTimeoutMs(),
                    properties.getReadTimeoutMs()
            );
            log.info("brand_geo_site outbound response articleId={} siteName={} domain={} endpoint={} statusCode={} responseBytes={}",
                    article == null ? null : article.getId(),
                    geoTarget.siteName(),
                    domain,
                    endpoint,
                    response.statusCode(),
                    response.body() == null ? 0 : response.body().getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
            return toSubmitResult(response, requestPayload, endpoint, contentType);
        } catch (Exception ex) {
            log.warn("brand_geo_site outbound exception articleId={} siteName={} domain={} endpoint={} error={}",
                    article == null ? null : article.getId(),
                    geoTarget.siteName(),
                    domain,
                    endpoint,
                    safeMessage(ex),
                    ex);
            return SubmitResult.failure(500, requestPayload, null,
                    "Agent 官网发布请求失败：" + safeMessage(ex),
                    FailureKind.NETWORK_ERROR, true);
        }
    }

    protected HttpClientUtil.HttpResult postJson(String url,
                                                 Map<String, String> headers,
                                                 String body,
                                                 int connectTimeoutMs,
                                                 int requestTimeoutMs) throws Exception {
        return HttpClientUtil.postJson(url, headers, body, connectTimeoutMs, requestTimeoutMs);
    }

    public EndpointProbeResult probeEndpoint(String domain) {
        String endpoint;
        try {
            endpoint = buildEndpoint(domain);
        } catch (Exception ex) {
            return new EndpointProbeResult(false, null, null, "Agent 官网域名配置无效：" + safeMessage(ex));
        }
        try {
            HttpClientUtil.HttpResult response = HttpClientUtil.get(
                    endpoint,
                    Map.of("Accept", "application/json"),
                    properties.getConnectTimeoutMs(),
                    properties.getReadTimeoutMs()
            );
            boolean passed = isProbePassed(response.statusCode(), response.body());
            String message = passed
                    ? "Agent 官网发布接口可达"
                    : "Agent 官网发布接口不存在或未部署，请检查域名与 /api/v1/admin/content";
            return new EndpointProbeResult(passed, endpoint, response.statusCode(), message);
        } catch (Exception ex) {
            return new EndpointProbeResult(false, endpoint, null, "Agent 官网发布接口连接失败：" + safeMessage(ex));
        }
    }

    private TargetContext.BrandGeoSiteTarget requireTarget(TargetContext target) {
        if (!(target instanceof TargetContext.BrandGeoSiteTarget geoTarget)) {
            throw new IllegalArgumentException("BrandGeoSiteAdapter requires BrandGeoSiteTarget");
        }
        return geoTarget;
    }

    private String buildRequestPayload(String domain,
                                       String contentType,
                                       ArticleDraft article,
                                       String contentMarkdown) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("domain", domain);
        root.put("contentType", contentType);
        root.put("contentMarkdown", ensureMarkdownTitle(article, contentMarkdown));
        root.put("sort", 0);
        return objectMapper.writeValueAsString(root);
    }

    private SubmitResult toSubmitResult(HttpClientUtil.HttpResult response,
                                        String requestPayload,
                                        String endpoint,
                                        String contentType) {
        int httpCode = response.statusCode();
        String body = response.body();
        if (httpCode == 429) {
            return SubmitResult.failure(httpCode, requestPayload, body, "Agent 官网发布接口请求过于频繁", FailureKind.RATE_LIMIT, true);
        }
        if (httpCode == 401 || httpCode == 403) {
            return SubmitResult.failure(httpCode, requestPayload, body, "Agent 官网登录认证信息已过期，请更新", FailureKind.AUTH_EXPIRED, false);
        }
        if (httpCode == 404) {
            return SubmitResult.failure(httpCode, requestPayload, body, "Agent 官网发布接口不存在，请检查域名", FailureKind.CLIENT_ERROR, false);
        }
        if (httpCode == 409 || containsDuplicateSignal(body)) {
            return SubmitResult.failure(httpCode, requestPayload, body, "Agent 官网已存在相同文章，请检查标题或栏目", FailureKind.VALIDATION, false);
        }
        if (httpCode >= 400 && httpCode < 500) {
            return SubmitResult.failure(httpCode, requestPayload, body, "Agent 官网发布请求参数异常，状态码：" + httpCode, FailureKind.CLIENT_ERROR, false);
        }
        if (httpCode < 200 || httpCode >= 300) {
            return SubmitResult.failure(httpCode, requestPayload, body, "Agent 官网发布接口异常，状态码：" + httpCode, FailureKind.SERVER_ERROR, true);
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(StringUtils.hasText(body) ? body : "{}");
        } catch (Exception ex) {
            return SubmitResult.failure(httpCode, requestPayload, body, safeMessage(ex), FailureKind.UNKNOWN, false);
        }

        int bizCode = root.path("code").asInt(-1);
        if (bizCode != 0 && bizCode != 200) {
            String message = root.path("message").asText("业务处理失败");
            return SubmitResult.failure(httpCode, requestPayload, body, "body code " + bizCode + ": " + message,
                    classifyBusinessFailure(message), false);
        }

        JsonNode idNode = root.path("data").path("id");
        if (!idNode.canConvertToLong()) {
            return SubmitResult.failure(httpCode, requestPayload, body, "Agent 官网返回结果缺少文章 ID",
                    FailureKind.SERVER_ERROR, true);
        }

        long platformId = idNode.asLong();
        String publishedUrl = firstText(root.path("data"), "url", "publishedUrl", "articleUrl", "link", "permalink");
        if (!StringUtils.hasText(publishedUrl)) {
            publishedUrl = publicBaseUrl(endpoint);
        }
        return SubmitResult.success(httpCode, requestPayload, body, publishedUrl, String.valueOf(platformId));
    }

    private Map<String, String> jsonHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");
        return headers;
    }

    private String mapContentType(ArticleDraft article) {
        String module = article == null ? null : article.getAgentSiteModule();
        if ("product".equalsIgnoreCase(module)) {
            return "PRODUCT";
        }
        if ("faq".equalsIgnoreCase(module)) {
            return "FAQ";
        }
        String articleType = article == null ? null : article.getArticleType();
        if ("FAQ".equalsIgnoreCase(articleType)) {
            return "FAQ";
        }
        if ("scenario_content".equalsIgnoreCase(articleType)) {
            return "PRODUCT";
        }
        return "KNOWLEDGE";
    }

    private String ensureMarkdownTitle(ArticleDraft article, String contentMarkdown) {
        String markdown = nullToEmpty(contentMarkdown).trim();
        if (markdown.matches("(?s)^#\\s+.+")) {
            return markdown;
        }
        String title = article == null ? null : article.getTitle();
        if (!StringUtils.hasText(title)) {
            return markdown;
        }
        return "# " + title.trim() + (markdown.isEmpty() ? "" : "\n\n" + markdown);
    }

    private String buildEndpoint(String domain) {
        String base = normalizeBaseUrl(domain);
        return base + ADMIN_CONTENT_PATH;
    }

    private String normalizeBaseUrl(String domain) {
        if (!StringUtils.hasText(domain)) {
            throw new IllegalArgumentException("domain is required");
        }
        String value = domain.trim();
        if (!value.contains("://")) {
            value = "https://" + value;
        }
        URI uri = URI.create(value);
        if (!StringUtils.hasText(uri.getHost())) {
            throw new IllegalArgumentException("domain host is required");
        }
        StringBuilder base = new StringBuilder();
        base.append(StringUtils.hasText(uri.getScheme()) ? uri.getScheme() : "https")
                .append("://")
                .append(uri.getHost());
        if (uri.getPort() > 0) {
            base.append(":").append(uri.getPort());
        }
        return base.toString();
    }

    private String extractHost(String endpoint) {
        URI uri = URI.create(endpoint);
        if (!StringUtils.hasText(uri.getHost())) {
            throw new IllegalArgumentException("domain host is required");
        }
        return uri.getHost();
    }

    private String publicBaseUrl(String endpoint) {
        return normalizeBaseUrl(endpoint);
    }

    private boolean isProbePassed(int statusCode, String body) {
        if (Set.of(400, 401, 403, 405, 415, 422).contains(statusCode)) {
            return true;
        }
        return statusCode >= 200 && statusCode < 300 && !looksLikeHtml(body);
    }

    private boolean looksLikeHtml(String body) {
        if (!StringUtils.hasText(body)) {
            return false;
        }
        String trimmed = body.trim().toLowerCase();
        return trimmed.startsWith("<!doctype html")
                || trimmed.startsWith("<html")
                || trimmed.contains("<body");
    }

    private String firstText(JsonNode node, String... names) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        for (String name : names) {
            JsonNode value = node.path(name);
            if (value.isValueNode() && StringUtils.hasText(value.asText())) {
                return value.asText();
            }
        }
        return null;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String safeMessage(Exception ex) {
        return StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : ex.getClass().getSimpleName();
    }

    private String classifyBusinessFailure(String message) {
        String lowered = message == null ? "" : message.toLowerCase();
        if (lowered.contains("auth") || lowered.contains("token") || lowered.contains("login")
                || lowered.contains("认证") || lowered.contains("登录") || lowered.contains("未授权")) {
            return FailureKind.AUTH_EXPIRED;
        }
        if (lowered.contains("duplicate") || lowered.contains("exists") || lowered.contains("conflict")
                || lowered.contains("重复") || lowered.contains("已存在")) {
            return FailureKind.VALIDATION;
        }
        return FailureKind.CLIENT_ERROR;
    }

    private boolean containsDuplicateSignal(String body) {
        if (!StringUtils.hasText(body)) {
            return false;
        }
        String lowered = body.toLowerCase();
        return lowered.contains("duplicate")
                || lowered.contains("already exists")
                || lowered.contains("conflict")
                || lowered.contains("重复")
                || lowered.contains("已存在");
    }

    public record EndpointProbeResult(boolean passed, String endpoint, Integer statusCode, String message) {
    }
}
