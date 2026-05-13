package com.huanjing.geo.module.content.service.adapter;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huanjing.geo.common.util.HttpClientUtil;
import com.huanjing.geo.module.content.distribution.TargetContext;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.system.entity.PublishSite;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
@Slf4j
public class IndustryNewsSiteAdapter implements SiteAdapter {

    private static final List<String> CATEGORY_SLUGS = List.of("industry", "region", "guide", "service");
    private static final int SUMMARY_MAX_LENGTH = 180;

    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(String integrationMethod) {
        return false;
    }

    @Override
    public boolean supportsPlatform(String platform) {
        return "industry_news_site".equalsIgnoreCase(platform);
    }

    @Override
    public ValidationResult validate(ArticleDraft article, String contentMarkdown, PublishSite site) {
        return validateIndustrySite(article, contentMarkdown, site, null);
    }

    @Override
    public SubmitResult submit(ArticleDraft article, String contentMarkdown, PublishSite site) {
        return submitIndustrySite(article, contentMarkdown, site, null);
    }

    @Override
    public String parsePublishedUrl(String responseBody, PublishSite site) {
        if (!StringUtils.hasText(responseBody)) {
            return null;
        }
        try {
            JsonNode data = objectMapper.readTree(responseBody).path("data");
            return firstText(data, "url", "publishedUrl", "articleUrl", "link", "permalink");
        } catch (Exception ex) {
            return null;
        }
    }

    @Override
    public SubmitResult submitToTarget(ArticleDraft article, String contentMarkdown, TargetContext target) {
        TargetContext.IndustrySiteTarget industryTarget = requireTarget(target);
        return submitIndustrySite(article, contentMarkdown, industryTarget.site(), industryTarget.project());
    }

    protected HttpClientUtil.HttpResult postJson(String url,
                                                 Map<String, String> headers,
                                                 String body,
                                                 int connectTimeoutMs,
                                                 int requestTimeoutMs) throws Exception {
        return HttpClientUtil.postJson(url, headers, body, connectTimeoutMs, requestTimeoutMs);
    }

    private SubmitResult submitIndustrySite(ArticleDraft article,
                                            String contentMarkdown,
                                            PublishSite site,
                                            Project project) {
        ValidationResult validation = validateIndustrySite(article, contentMarkdown, site, project);
        if (!validation.isPassed()) {
            return SubmitResult.failure(400, null, null, String.join("; ", validation.getErrors()),
                    FailureKind.CLIENT_ERROR, false);
        }

        String requestPayload;
        try {
            requestPayload = buildRequestPayload(article, contentMarkdown, site, project);
        } catch (Exception ex) {
            return SubmitResult.failure(500, null, null, safeMessage(ex), FailureKind.UNKNOWN, false);
        }

        try {
            log.info("industry_news_site outbound request articleId={} siteId={} endpoint={} payloadBytes={}",
                    article == null ? null : article.getId(),
                    site == null ? null : site.getId(),
                    site == null ? null : site.getApiEndpoint(),
                    requestPayload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
            HttpClientUtil.HttpResult response = postJson(
                    site.getApiEndpoint(),
                    requestHeaders(site),
                    requestPayload,
                    8000,
                    15000
            );
            log.info("industry_news_site outbound response articleId={} siteId={} endpoint={} statusCode={} responseBytes={}",
                    article == null ? null : article.getId(),
                    site == null ? null : site.getId(),
                    site == null ? null : site.getApiEndpoint(),
                    response.statusCode(),
                    response.body() == null ? 0 : response.body().getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
            return toSubmitResult(response, requestPayload, site);
        } catch (Exception ex) {
            log.warn("industry_news_site outbound exception articleId={} siteId={} endpoint={} error={}",
                    article == null ? null : article.getId(),
                    site == null ? null : site.getId(),
                    site == null ? null : site.getApiEndpoint(),
                    safeMessage(ex),
                    ex);
            return SubmitResult.failure(500, requestPayload, null,
                    "network error: " + safeMessage(ex) + " while POST " + site.getApiEndpoint(),
                    FailureKind.SERVER_ERROR, true);
        }
    }

    private ValidationResult validateIndustrySite(ArticleDraft article,
                                                  String contentMarkdown,
                                                  PublishSite site,
                                                  Project project) {
        java.util.ArrayList<String> errors = new java.util.ArrayList<>();
        if (site == null || !StringUtils.hasText(site.getApiEndpoint())) {
            errors.add("industry site apiEndpoint is required");
        }
        if (article == null || !StringUtils.hasText(article.getTitle())) {
            errors.add("title is empty");
        }
        if (!StringUtils.hasText(contentMarkdown)) {
            errors.add("markdown is empty");
        }
        String categorySlug = resolveCategorySlug(article, project);
        if ("region".equals(categorySlug)) {
            if (project == null || !StringUtils.hasText(project.getProvinceName())) {
                errors.add("province is required for region category");
            }
            if (project == null || !StringUtils.hasText(project.getCityName())) {
                errors.add("city is required for region category");
            }
        }
        return errors.isEmpty() ? ValidationResult.pass() : ValidationResult.fail(errors);
    }

    private String buildRequestPayload(ArticleDraft article,
                                       String contentMarkdown,
                                       PublishSite site,
                                       Project project) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        String categorySlug = resolveCategorySlug(article, project);
        root.put("categorySlug", categorySlug);
        root.put("title", trim(article.getTitle()));
        String summary = extractSummary(contentMarkdown);
        if (StringUtils.hasText(summary)) {
            root.put("summary", summary);
        }
        ArrayNode keywords = root.putArray("keywords");
        parseKeywords(article.getTagsJson()).forEach(keywords::add);
        root.put("author", "智装参考编辑部");
        if ("region".equals(categorySlug)) {
            root.put("province", trim(project.getProvinceName()));
            root.put("city", trim(project.getCityName()));
        }
        root.put("markdown", contentMarkdown);
        root.put("status", "PUBLISHED");

        ObjectNode meta = root.putObject("meta");
        meta.put("industry", firstIndustry(site));
        meta.put("sourceType", "geo_system_distribution");
        meta.putArray("sourceUrls");
        if (article.getId() != null) {
            meta.put("articleId", article.getId());
        }
        if (article.getProjectId() != null) {
            meta.put("projectId", article.getProjectId());
        }
        if (project != null && StringUtils.hasText(project.getBrandName())) {
            meta.put("brandName", project.getBrandName().trim());
        }
        return objectMapper.writeValueAsString(root);
    }

    private SubmitResult toSubmitResult(HttpClientUtil.HttpResult response, String requestPayload, PublishSite site) {
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

        if (!root.path("success").asBoolean(false)) {
            String message = root.path("message").asText("business error");
            return SubmitResult.failure(httpCode, requestPayload, body, message, FailureKind.CLIENT_ERROR, false);
        }

        String publishedUrl = parsePublishedUrl(body, site);
        String platformArticleId = firstText(root.path("data"), "id", "articleId", "slug");
        return SubmitResult.success(httpCode, requestPayload, body, publishedUrl, platformArticleId);
    }

    private Map<String, String> requestHeaders(PublishSite site) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        if (!StringUtils.hasText(site.getRequestHeaderTemplate())) {
            return headers;
        }
        try {
            JSONObject obj = JSONUtil.parseObj(site.getRequestHeaderTemplate());
            for (String key : obj.keySet()) {
                Object value = obj.get(key);
                if (StringUtils.hasText(key) && value != null) {
                    headers.put(key.trim(), String.valueOf(value));
                }
            }
        } catch (Exception ignored) {
            // Invalid header JSON is ignored so a bad optional header does not block validation.
        }
        return headers;
    }

    private String resolveCategorySlug(ArticleDraft article, Project project) {
        String explicitCategory = normalizeCategory(article == null ? null : article.getCategory());
        if (explicitCategory != null) {
            return explicitCategory;
        }
        String articleType = article == null ? null : article.getArticleType();
        if (StringUtils.hasText(articleType)) {
            return switch (articleType.trim().toLowerCase(Locale.ROOT)) {
                case "industry_article" -> "industry";
                case "stage_advice", "faq" -> "guide";
                case "scenario_content" -> "service";
                default -> hasRegion(project) ? "region" : "industry";
            };
        }
        return hasRegion(project) ? "region" : "industry";
    }

    private String normalizeCategory(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (CATEGORY_SLUGS.contains(value)) {
            return value;
        }
        return switch (value) {
            case "行业", "行业文章", "产业" -> "industry";
            case "区域", "区域文章", "地区", "城市" -> "region";
            case "指南", "攻略" -> "guide";
            case "服务", "服务文章" -> "service";
            default -> null;
        };
    }

    private boolean hasRegion(Project project) {
        return project != null && StringUtils.hasText(project.getProvinceName()) && StringUtils.hasText(project.getCityName());
    }

    private List<String> parseKeywords(String tagsJson) {
        if (!StringUtils.hasText(tagsJson)) {
            return List.of();
        }
        try {
            JSONArray array = JSONUtil.parseArray(tagsJson);
            return array.stream()
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();
        } catch (Exception ex) {
            return List.of(tagsJson.trim());
        }
    }

    private String firstIndustry(PublishSite site) {
        if (site == null || !StringUtils.hasText(site.getIndustryTags())) {
            return "";
        }
        return parseKeywords(site.getIndustryTags()).stream().findFirst().orElse("");
    }

    private String extractSummary(String markdown) {
        if (!StringUtils.hasText(markdown)) {
            return "";
        }
        String plain = markdown
                .replaceAll("(?m)^#{1,6}\\s*", "")
                .replaceAll("!\\[[^]]*]\\([^)]*\\)", "")
                .replaceAll("\\[[^]]+]\\([^)]*\\)", "")
                .replaceAll("[*_`>\\-]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (plain.length() <= SUMMARY_MAX_LENGTH) {
            return plain;
        }
        return plain.substring(0, SUMMARY_MAX_LENGTH);
    }

    private TargetContext.IndustrySiteTarget requireTarget(TargetContext target) {
        if (!(target instanceof TargetContext.IndustrySiteTarget industryTarget)) {
            throw new IllegalArgumentException("IndustryNewsSiteAdapter requires IndustrySiteTarget");
        }
        return industryTarget;
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

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeMessage(Exception ex) {
        return StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : ex.getClass().getSimpleName();
    }
}
