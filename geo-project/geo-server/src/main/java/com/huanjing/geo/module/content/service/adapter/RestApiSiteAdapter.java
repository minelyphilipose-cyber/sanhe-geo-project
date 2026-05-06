package com.huanjing.geo.module.content.service.adapter;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.util.HttpClientUtil;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.service.render.MarkdownToHtmlRenderer;
import com.huanjing.geo.module.system.entity.PublishSite;
import com.huanjing.geo.module.system.service.PlatformCredentialService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RestApiSiteAdapter implements SiteAdapter {

    private final PlatformCredentialService platformCredentialService;
    private final MarkdownToHtmlRenderer markdownRenderer;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(String integrationMethod) {
        return "rest_api".equalsIgnoreCase(integrationMethod);
    }

    @Override
    public ValidationResult validate(ArticleDraft article, String contentMarkdown, PublishSite site) {
        List<String> errors = new ArrayList<>();
        String title = article == null || article.getTitle() == null ? "" : article.getTitle().trim();
        String body = contentMarkdown == null ? "" : contentMarkdown.trim();
        if (!StringUtils.hasText(title)) {
            errors.add("title is empty");
        }
        if (!StringUtils.hasText(body)) {
            errors.add("content is empty");
        }
        if (StringUtils.hasText(site.getContentConstraints())) {
            try {
                JSONObject constraints = JSONUtil.parseObj(site.getContentConstraints());
                Integer maxTitle = constraints.getInt("maxTitleLength");
                Integer maxBody = constraints.getInt("maxBodyLength");
                if (maxTitle != null && maxTitle > 0 && title.length() > maxTitle) {
                    errors.add("title length exceeds limit: " + title.length() + "/" + maxTitle);
                }
                if (maxBody != null && maxBody > 0 && body.length() > maxBody) {
                    errors.add("content length exceeds limit: " + body.length() + "/" + maxBody);
                }
                JSONArray required = constraints.getJSONArray("requiredFields");
                if (required != null) {
                    for (Object obj : required) {
                        String field = String.valueOf(obj);
                        if ("title".equalsIgnoreCase(field) && !StringUtils.hasText(title)) {
                            errors.add("required field missing: title");
                        } else if ("content".equalsIgnoreCase(field) && !StringUtils.hasText(body)) {
                            errors.add("required field missing: content");
                        } else if ("author".equalsIgnoreCase(field)) {
                            // author is always provided as default if not available.
                        }
                    }
                }
            } catch (Exception ex) {
                errors.add("content_constraints is invalid JSON");
            }
        }
        return errors.isEmpty() ? ValidationResult.pass() : ValidationResult.fail(errors);
    }

    @Override
    public SubmitResult submit(ArticleDraft article, String contentMarkdown, PublishSite site) {
        String method = StringUtils.hasText(site.getHttpMethod()) ? site.getHttpMethod().trim().toUpperCase(Locale.ROOT) : "POST";
        String template = StringUtils.hasText(site.getRequestBodyTemplate())
                ? site.getRequestBodyTemplate()
                : "{\"title\":\"{{title}}\",\"content\":\"{{content}}\",\"contentMarkdown\":\"{{contentMarkdown}}\",\"contentHtml\":\"{{contentHtml}}\",\"author\":\"{{author}}\"}";
        String contentHtml = markdownRenderer.render(contentMarkdown);
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("title", article.getTitle());
        placeholders.put("content", contentHtml);
        placeholders.put("contentMarkdown", contentMarkdown);
        placeholders.put("contentHtml", contentHtml);
        placeholders.put("keywords", "");
        placeholders.put("author", "geo-system");
        String requestPayload = applyPlaceholders(template, placeholders);

        Map<String, String> headers = parseHeaders(site.getRequestHeaderTemplate());
        headers.putIfAbsent("Content-Type", "application/json");
        String credential = platformCredentialService.resolveCredential(site.getCredentialRef(), site.getApiCredentialEncrypted());
        applyAuthHeaders(headers, site.getAuthType(), credential);

        try {
            HttpClientUtil.HttpResult response = HttpClientUtil.request(
                    method,
                    site.getApiEndpoint(),
                    headers,
                    requestPayload,
                    8000,
                    15000
            );
            int code = response.statusCode();
            String body = response.body();
            if (code >= 200 && code < 300) {
                String publishedUrl = parsePublishedUrl(body, site);
                return SubmitResult.success(code, requestPayload, body, publishedUrl);
            }
            return SubmitResult.fail(code, requestPayload, body, "HTTP " + code);
        } catch (Exception ex) {
            return SubmitResult.fail(500, requestPayload, null, ex.getMessage());
        }
    }

    @Override
    public String parsePublishedUrl(String responseBody, PublishSite site) {
        if (!StringUtils.hasText(responseBody) || !StringUtils.hasText(site.getResponseUrlPath())) {
            return null;
        }
        String path = site.getResponseUrlPath().trim();
        if (!path.startsWith("$.")) {
            return null;
        }
        try {
            Object cursor = JSONUtil.parseObj(responseBody);
            String[] parts = path.substring(2).split("\\.");
            for (String part : parts) {
                if (!(cursor instanceof JSONObject obj)) {
                    return null;
                }
                cursor = obj.get(part);
                if (cursor == null) {
                    return null;
                }
            }
            return String.valueOf(cursor);
        } catch (Exception ex) {
            return null;
        }
    }

    private String applyPlaceholders(String template, Map<String, String> values) {
        String result = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", escapeJsonString(entry.getValue()));
        }
        return result;
    }

    private String escapeJsonString(String value) {
        if (value == null) {
            return "";
        }
        try {
            String json = objectMapper.writeValueAsString(value);
            return json.substring(1, json.length() - 1);
        } catch (Exception ex) {
            return value;
        }
    }

    private Map<String, String> parseHeaders(String raw) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (!StringUtils.hasText(raw)) {
            return headers;
        }
        try {
            JSONObject obj = JSONUtil.parseObj(raw);
            for (String key : obj.keySet()) {
                Object value = obj.get(key);
                if (value != null) {
                    headers.put(key, String.valueOf(value));
                }
            }
            return headers;
        } catch (Exception ex) {
            return headers;
        }
    }

    private void applyAuthHeaders(Map<String, String> headers, String authType, String credential) {
        if (!StringUtils.hasText(authType) || !StringUtils.hasText(credential)) {
            return;
        }
        String type = authType.trim().toLowerCase(Locale.ROOT);
        if ("api_key".equals(type)) {
            headers.putIfAbsent("x-api-key", credential);
            headers.putIfAbsent("api-key", credential);
            return;
        }
        if ("bearer_token".equals(type) || "oauth2".equals(type)) {
            headers.put("Authorization", "Bearer " + credential);
            return;
        }
        if ("basic_auth".equals(type)) {
            headers.put("Authorization", "Basic " + credential);
        }
    }
}
