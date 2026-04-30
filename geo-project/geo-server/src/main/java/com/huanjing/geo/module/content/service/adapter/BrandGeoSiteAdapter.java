package com.huanjing.geo.module.content.service.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huanjing.geo.common.util.HttpClientUtil;
import com.huanjing.geo.module.content.config.BrandGeoSiteProperties;
import com.huanjing.geo.module.content.distribution.TargetContext;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.system.entity.PublishSite;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class BrandGeoSiteAdapter implements SiteAdapter {

    public static final String PLATFORM = "brand_geo_site";
    private static final String URL_TEMPLATE = "https://www.%s.com/%s/detail/%d";

    private final ObjectMapper objectMapper;
    private final BrandGeoSiteProperties properties;

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
        throw new UnsupportedOperationException("Use submitToTarget for brand_geo_site");
    }

    @Override
    public String parsePublishedUrl(String responseBody, PublishSite site) {
        return null;
    }

    @Override
    public SubmitResult submitToTarget(ArticleDraft article, String contentMarkdown, TargetContext target) {
        TargetContext.BrandGeoSiteTarget geoTarget = requireTarget(target);
        String mappedType = mapArticleType(article == null ? null : article.getArticleType());
        String requestPayload;
        try {
            requestPayload = buildRequestPayload(geoTarget.siteCode(), mappedType, article, contentMarkdown);
        } catch (Exception ex) {
            return SubmitResult.failure(500, null, null, safeMessage(ex), FailureKind.UNKNOWN, false);
        }

        if (!StringUtils.hasText(properties.getEndpoint())) {
            return SubmitResult.failure(500, requestPayload, null, "brand geo site endpoint is not configured",
                    FailureKind.SERVER_ERROR, true);
        }

        try {
            HttpClientUtil.HttpResult response = postJson(
                    properties.getEndpoint(),
                    jsonHeaders(),
                    requestPayload,
                    properties.getConnectTimeoutMs(),
                    properties.getReadTimeoutMs()
            );
            return toSubmitResult(response, requestPayload, mappedType, geoTarget.siteCode());
        } catch (Exception ex) {
            return SubmitResult.failure(500, requestPayload, null, "network error: " + safeMessage(ex),
                    FailureKind.SERVER_ERROR, true);
        }
    }

    protected HttpClientUtil.HttpResult postJson(String url,
                                                 Map<String, String> headers,
                                                 String body,
                                                 int connectTimeoutMs,
                                                 int requestTimeoutMs) throws Exception {
        return HttpClientUtil.postJson(url, headers, body, connectTimeoutMs, requestTimeoutMs);
    }

    private TargetContext.BrandGeoSiteTarget requireTarget(TargetContext target) {
        if (!(target instanceof TargetContext.BrandGeoSiteTarget geoTarget)) {
            throw new IllegalArgumentException("BrandGeoSiteAdapter requires BrandGeoSiteTarget");
        }
        return geoTarget;
    }

    private String buildRequestPayload(String siteCode,
                                       String mappedType,
                                       ArticleDraft article,
                                       String contentMarkdown) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("siteCode", siteCode);
        root.put("articleType", mappedType);
        root.put("title", article == null ? "" : nullToEmpty(article.getTitle()));
        root.put("content", nullToEmpty(contentMarkdown));
        return objectMapper.writeValueAsString(root);
    }

    private SubmitResult toSubmitResult(HttpClientUtil.HttpResult response,
                                        String requestPayload,
                                        String mappedType,
                                        String siteCode) {
        int httpCode = response.statusCode();
        String body = response.body();
        if (httpCode == 429) {
            return SubmitResult.failure(httpCode, requestPayload, body, "HTTP 429", FailureKind.SERVER_ERROR, true);
        }
        if (httpCode >= 400 && httpCode < 500) {
            return SubmitResult.failure(httpCode, requestPayload, body, "HTTP " + httpCode, FailureKind.CLIENT_ERROR, false);
        }
        if (httpCode < 200 || httpCode >= 300) {
            return SubmitResult.failure(httpCode, requestPayload, body, "HTTP " + httpCode, FailureKind.SERVER_ERROR, true);
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(StringUtils.hasText(body) ? body : "{}");
        } catch (Exception ex) {
            return SubmitResult.failure(httpCode, requestPayload, body, safeMessage(ex), FailureKind.UNKNOWN, false);
        }

        int bizCode = root.path("code").asInt(-1);
        if (bizCode != 200) {
            String message = root.path("message").asText("business error");
            return SubmitResult.failure(httpCode, requestPayload, body, "body code " + bizCode + ": " + message,
                    FailureKind.CLIENT_ERROR, false);
        }

        JsonNode idNode = root.path("data").path("id");
        if (!idNode.canConvertToLong()) {
            return SubmitResult.failure(httpCode, requestPayload, body, "response data.id missing or invalid",
                    FailureKind.SERVER_ERROR, true);
        }

        long platformId = idNode.asLong();
        String publishedUrl = URL_TEMPLATE.formatted(siteCode, mappedType, platformId);
        return SubmitResult.success(httpCode, requestPayload, body, publishedUrl, String.valueOf(platformId));
    }

    private Map<String, String> jsonHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        return headers;
    }

    private String mapArticleType(String localType) {
        return "FAQ".equalsIgnoreCase(localType) ? "question" : "knowledge";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String safeMessage(Exception ex) {
        return StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : ex.getClass().getSimpleName();
    }
}
