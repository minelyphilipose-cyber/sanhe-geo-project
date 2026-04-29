package com.huanjing.geo.module.content.service.adapter;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.huanjing.geo.common.util.HttpClientUtil;
import com.huanjing.geo.module.content.distribution.TargetContext;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.entity.BrandOfficialSite;
import com.huanjing.geo.module.content.service.render.MarkdownToHtmlRenderer;
import com.huanjing.geo.module.system.entity.PublishSite;
import com.huanjing.geo.module.system.mapper.PublishSiteMapper;
import com.huanjing.geo.module.system.service.MpCredentialCipherService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OfficialCmsSiteAdapter implements SiteAdapter {

    public static final String FRAMEWORK_CODE_DEFAULT = "Official CMS Framework v1";

    private static final String PLATFORM = "official_cms";
    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 30000;

    private final PublishSiteMapper publishSiteMapper;
    private final MarkdownToHtmlRenderer markdownToHtmlRenderer;
    private final MpCredentialCipherService mpCredentialCipherService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(String integrationMethod) {
        return supportsPlatform(integrationMethod);
    }

    @Override
    public boolean supportsPlatform(String platform) {
        return PLATFORM.equals(platform);
    }

    @Override
    public ValidationResult validate(ArticleDraft article, String contentMarkdown, PublishSite site) {
        return ValidationResult.pass();
    }

    @Override
    public SubmitResult submit(ArticleDraft article, String contentMarkdown, PublishSite site) {
        throw new UnsupportedOperationException("Use submitToTarget for official_cms");
    }

    @Override
    public String parsePublishedUrl(String responseBody, PublishSite site) {
        return null;
    }

    @Override
    public SubmitResult submitToTarget(ArticleDraft article, String contentMarkdown, TargetContext target) {
        BrandOfficialSite officialSite = requireOfficialSiteTarget(target);
        String requestPayload = null;
        try {
            PublishSite framework = resolveFramework(officialSite.getCmsFrameworkCode());
            String html = markdownToHtmlRenderer.render(contentMarkdown);
            requestPayload = buildRequestPayload(framework.getRequestBodyTemplate(), article, html, officialSite);
            String token = mpCredentialCipherService.decrypt(officialSite.getCredentialsCipher());
            HttpClientUtil.HttpResult response;
            try {
                response = postJson(officialSite.getApiEndpoint(), authHeaders(token), requestPayload, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
            } catch (Exception ex) {
                return SubmitResult.failure(500, requestPayload, null, safeMessage(ex), FailureKind.SERVER_ERROR, true);
            }
            return toSubmitResult(response, requestPayload);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            return SubmitResult.failure(500, requestPayload, null, safeMessage(ex), FailureKind.UNKNOWN, false);
        }
    }

    @Override
    public AuthCheckResult checkAuth(TargetContext target) {
        BrandOfficialSite officialSite = requireOfficialSiteTarget(target);
        try {
            String token = mpCredentialCipherService.decrypt(officialSite.getCredentialsCipher());
            HttpClientUtil.HttpResult response = get(officialSite.getApiEndpoint(), authHeaders(token), CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
            int code = response.statusCode();
            if (code == 200) {
                return AuthCheckResult.success();
            }
            if (code == 401) {
                return AuthCheckResult.failure(FailureKind.AUTH_EXPIRED, "auth_failed");
            }
            return AuthCheckResult.failure(FailureKind.SERVER_ERROR, "unreachable: " + code);
        } catch (Exception ex) {
            return AuthCheckResult.failure(FailureKind.NETWORK_ERROR, "network_error");
        }
    }

    protected HttpClientUtil.HttpResult postJson(String url, Map<String, String> headers, String body, int connectTimeoutMs, int requestTimeoutMs) throws Exception {
        return HttpClientUtil.postJson(url, headers, body, connectTimeoutMs, requestTimeoutMs);
    }

    protected HttpClientUtil.HttpResult get(String url, Map<String, String> headers, int connectTimeoutMs, int requestTimeoutMs) throws Exception {
        return HttpClientUtil.get(url, headers, connectTimeoutMs, requestTimeoutMs);
    }

    private BrandOfficialSite requireOfficialSiteTarget(TargetContext target) {
        if (!(target instanceof TargetContext.BrandOfficialSiteTarget officialSiteTarget)) {
            throw new IllegalArgumentException("OfficialCmsSiteAdapter requires BrandOfficialSiteTarget");
        }
        return officialSiteTarget.site();
    }

    private PublishSite resolveFramework(String frameworkCode) {
        PublishSite exact = publishSiteMapper.selectOne(frameworkQuery().eq("site_name", frameworkCode).last("LIMIT 1"));
        if (exact != null) {
            return exact;
        }
        // Phase 1 has a single framework metadata row; fallback keeps official_cms_v1 data compatible.
        PublishSite fallback = publishSiteMapper.selectOne(frameworkQuery().eq("site_name", FRAMEWORK_CODE_DEFAULT).last("LIMIT 1"));
        if (fallback == null) {
            throw new IllegalStateException("Official CMS framework row not found");
        }
        return fallback;
    }

    private QueryWrapper<PublishSite> frameworkQuery() {
        return new QueryWrapper<PublishSite>().eq("is_framework", 1).eq("integration_method", PLATFORM);
    }

    private String buildRequestPayload(String template, ArticleDraft article, String contentHtml, BrandOfficialSite officialSite) throws Exception {
        String effectiveTemplate = StringUtils.hasText(template)
                ? template
                : "{\"site_id\":\"{{tenantKey}}\",\"title\":\"{{title}}\",\"type\":\"{{articleType}}\",\"content\":\"{{content}}\"}";
        JsonNode root = objectMapper.readTree(effectiveTemplate);
        Map<String, String> values = new LinkedHashMap<>();
        values.put("tenantKey", nullToEmpty(officialSite.getTenantKey()));
        values.put("title", article == null ? "" : nullToEmpty(article.getTitle()));
        values.put("articleType", article == null ? "" : nullToEmpty(article.getArticleType()));
        values.put("content", nullToEmpty(contentHtml));
        return objectMapper.writeValueAsString(replacePlaceholders(root, values));
    }

    private JsonNode replacePlaceholders(JsonNode node, Map<String, String> values) {
        if (node == null) {
            return TextNode.valueOf("");
        }
        if (node.isTextual()) {
            String text = node.asText();
            for (Map.Entry<String, String> entry : values.entrySet()) {
                text = text.replace("{{" + entry.getKey() + "}}", entry.getValue());
            }
            return TextNode.valueOf(text);
        }
        if (node.isObject()) {
            ObjectNode copy = objectMapper.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                copy.set(field.getKey(), replacePlaceholders(field.getValue(), values));
            }
            return copy;
        }
        if (node.isArray()) {
            ArrayNode copy = objectMapper.createArrayNode();
            for (JsonNode child : node) {
                copy.add(replacePlaceholders(child, values));
            }
            return copy;
        }
        return node.deepCopy();
    }

    private Map<String, String> authHeaders(String token) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + token);
        headers.put("Content-Type", "application/json");
        return headers;
    }

    private SubmitResult toSubmitResult(HttpClientUtil.HttpResult response, String requestPayload) {
        int code = response.statusCode();
        String body = response.body();
        if (code == 200 || code == 201) {
            ResponseFields fields = parseResponseFields(body);
            return SubmitResult.success(code, requestPayload, body, fields.publishedUrl(), fields.platformArticleId());
        }
        if (code == 401) {
            return SubmitResult.failure(code, requestPayload, body, "HTTP 401", FailureKind.AUTH_EXPIRED, false);
        }
        if (code >= 400 && code < 500) {
            return SubmitResult.failure(code, requestPayload, body, "HTTP " + code, FailureKind.CLIENT_ERROR, false);
        }
        return SubmitResult.failure(code, requestPayload, body, "HTTP " + code, FailureKind.SERVER_ERROR, true);
    }

    private ResponseFields parseResponseFields(String body) {
        try {
            JsonNode root = objectMapper.readTree(body == null ? "{}" : body);
            return new ResponseFields(text(root, "url"), text(root, "id"));
        } catch (Exception ex) {
            return new ResponseFields(null, null);
        }
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode child = node == null ? null : node.get(fieldName);
        return child == null || child.isNull() ? null : child.asText();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String safeMessage(Exception ex) {
        return StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : ex.getClass().getSimpleName();
    }

    private record ResponseFields(String publishedUrl, String platformArticleId) {
    }
}
