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
import com.huanjing.geo.module.system.service.PlatformCredentialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class IndustryNewsSiteAdapter implements SiteAdapter {

    private static final List<String> CATEGORY_SLUGS = List.of("industry", "region", "guide", "service");
    private static final int SUMMARY_MAX_LENGTH = 180;
    private static final Map<String, String> INDUSTRY_PATH_SLUGS = Map.ofEntries(
            Map.entry("全屋智能", "whole-house"),
            Map.entry("智能家居", "smart-home"),
            Map.entry("家装", "home-decoration"),
            Map.entry("装修", "home-decoration"),
            Map.entry("医美", "medical-beauty"),
            Map.entry("口腔", "oral-care"),
            Map.entry("餐饮", "restaurant"),
            Map.entry("教育", "education")
    );
    private static final Map<String, String> REGION_SLUGS = Map.ofEntries(
            Map.entry("安徽省", "anhui"),
            Map.entry("安徽", "anhui"),
            Map.entry("阜阳市", "fuyang"),
            Map.entry("阜阳", "fuyang"),
            Map.entry("合肥市", "hefei"),
            Map.entry("合肥", "hefei"),
            Map.entry("北京市", "beijing"),
            Map.entry("北京", "beijing"),
            Map.entry("上海市", "shanghai"),
            Map.entry("上海", "shanghai"),
            Map.entry("广州市", "guangzhou"),
            Map.entry("广州", "guangzhou"),
            Map.entry("深圳市", "shenzhen"),
            Map.entry("深圳", "shenzhen")
    );

    private final ObjectMapper objectMapper;
    private final PlatformCredentialService platformCredentialService;

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
                    validationFailureKind(validation.getErrors()), false);
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
                    "行业资讯站发布请求失败：" + safeMessage(ex),
                    FailureKind.NETWORK_ERROR, true);
        }
    }

    private ValidationResult validateIndustrySite(ArticleDraft article,
                                                  String contentMarkdown,
                                                  PublishSite site,
                                                  Project project) {
        java.util.ArrayList<String> errors = new java.util.ArrayList<>();
        if (site == null || !StringUtils.hasText(site.getApiEndpoint())) {
            errors.add("行业资讯站发布接口不能为空");
        }
        if (site == null || !StringUtils.hasText(resolveAdminToken(site))) {
            errors.add("行业资讯站 X-Admin-Token 未配置");
        }
        if (article == null || !StringUtils.hasText(article.getTitle())) {
            errors.add("文章标题不能为空");
        }
        if (!StringUtils.hasText(contentMarkdown)) {
            errors.add("文章正文不能为空");
        }
        String categorySlug = resolveCategorySlug(article, project);
        if ("region".equals(categorySlug)) {
            if (project == null || !StringUtils.hasText(project.getProvinceName())) {
                errors.add("区域类文章需要配置省份");
            }
            if (project == null || !StringUtils.hasText(project.getCityName())) {
                errors.add("区域类文章需要配置城市");
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
        collectKeywords(article, project, site).forEach(keywords::add);
        root.put("author", "智装参考编辑部");
        if ("region".equals(categorySlug)) {
            root.put("province", trim(project.getProvinceName()));
            root.put("city", trim(project.getCityName()));
        }
        root.put("markdown", contentMarkdown);
        root.put("status", "PUBLISHED");

        ObjectNode meta = root.putObject("meta");
        meta.put("canonicalPath", canonicalPath(article, project, site, categorySlug));
        meta.put("industry", firstIndustry(site));
        meta.put("sourceType", "geo_system_distribution");
        meta.putArray("sourceUrls");
        if (article.getId() != null) {
            meta.put("articleId", article.getId());
            meta.put("publishQueueId", "article-" + article.getId());
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
        if (httpCode == 401 || httpCode == 403) {
            return SubmitResult.failure(httpCode, requestPayload, body, "行业资讯站登录认证信息已过期，请更新", FailureKind.AUTH_EXPIRED, false);
        }
        if (httpCode == 429) {
            return SubmitResult.failure(httpCode, requestPayload, body, "行业资讯站发布接口请求过于频繁", FailureKind.RATE_LIMIT, true);
        }
        if (httpCode == 404) {
            return SubmitResult.failure(httpCode, requestPayload, body, "行业资讯站发布接口不存在，请检查发布 URL", FailureKind.CLIENT_ERROR, false);
        }
        if (httpCode == 409 || containsDuplicateSignal(body)) {
            return SubmitResult.failure(httpCode, requestPayload, body, "行业资讯站已存在相同文章，请检查 canonicalPath 或标题", FailureKind.VALIDATION, false);
        }
        if (httpCode >= 400 && httpCode < 500) {
            return SubmitResult.failure(httpCode, requestPayload, body, "行业资讯站发布请求参数异常，状态码：" + httpCode, FailureKind.CLIENT_ERROR, false);
        }
        if (httpCode < 200 || httpCode >= 300) {
            return SubmitResult.failure(httpCode, requestPayload, body, "行业资讯站发布接口异常，状态码：" + httpCode, FailureKind.SERVER_ERROR, true);
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(StringUtils.hasText(body) ? body : "{}");
        } catch (Exception ex) {
            return SubmitResult.failure(httpCode, requestPayload, body, safeMessage(ex), FailureKind.UNKNOWN, false);
        }

        boolean success = root.path("success").asBoolean(false)
                || root.path("code").asInt(Integer.MIN_VALUE) == 0;
        if (!success) {
            String message = root.path("message").asText("业务处理失败");
            return SubmitResult.failure(httpCode, requestPayload, body, message, classifyBusinessFailure(message), false);
        }

        String publishedUrl = parsePublishedUrl(body, site);
        String platformArticleId = firstText(root.path("data"), "id", "articleId", "slug");
        return SubmitResult.success(httpCode, requestPayload, body, publishedUrl, platformArticleId);
    }

    private Map<String, String> requestHeaders(PublishSite site) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json; charset=utf-8");
        if (!StringUtils.hasText(site.getRequestHeaderTemplate())) {
            putAdminToken(headers, site);
            return headers;
        }
        try {
            JSONObject obj = JSONUtil.parseObj(site.getRequestHeaderTemplate());
            for (String key : obj.keySet()) {
                Object value = obj.get(key);
                if (StringUtils.hasText(key) && value != null) {
                    if ("x-admin-token".equalsIgnoreCase(key.trim())) {
                        continue;
                    }
                    headers.put(key.trim(), String.valueOf(value));
                }
            }
        } catch (Exception ignored) {
            // Invalid header JSON is ignored so a bad optional header does not block validation.
        }
        putAdminToken(headers, site);
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
                case "buying_guide", "pitfall_guide", "stage_advice", "faq" -> "guide";
                case "scenario_content" -> "service";
                default -> "industry";
            };
        }
        return "industry";
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

    private List<String> collectKeywords(ArticleDraft article, Project project, PublishSite site) {
        Set<String> keywords = new LinkedHashSet<>();
        if (article != null) {
            keywords.addAll(parseKeywords(article.getTagsJson()));
            addKeyword(keywords, article.getTopic());
            addKeyword(keywords, article.getTopicAsQuestion());
        }
        addKeyword(keywords, firstIndustry(site));
        if (project != null) {
            addKeyword(keywords, project.getCityName());
            addKeyword(keywords, project.getBrandName());
        }
        return keywords.stream().limit(8).toList();
    }

    private void addKeyword(Set<String> keywords, String value) {
        if (StringUtils.hasText(value)) {
            keywords.add(value.trim());
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
        for (String line : markdown.split("\\R")) {
            String text = line == null ? "" : line.trim();
            if (!StringUtils.hasText(text)
                    || text.startsWith("#")
                    || text.startsWith("!")
                    || text.startsWith("|")
                    || text.matches("^[-*+]\\s+.*")) {
                continue;
            }
            String plainLine = toPlainText(text);
            if (StringUtils.hasText(plainLine)) {
                return truncate(plainLine, SUMMARY_MAX_LENGTH);
            }
        }
        return truncate(toPlainText(markdown), SUMMARY_MAX_LENGTH);
    }

    private String canonicalPath(ArticleDraft article, Project project, PublishSite site, String categorySlug) {
        String titleSlug = slugText(article == null ? null : article.getTitle());
        if (!StringUtils.hasText(titleSlug) || titleSlug.length() < 8) {
            titleSlug = "article-" + (article != null && article.getId() != null ? article.getId() : System.currentTimeMillis());
        }
        return switch (categorySlug) {
            case "region" -> "/" + categorySlug + "/" + regionSlug(project == null ? null : project.getProvinceName(), "province")
                    + "/" + regionSlug(project == null ? null : project.getCityName(), "city")
                    + "/" + titleSlug + ".html";
            case "guide" -> "/guide/" + industrySlug(site) + "/decision/" + titleSlug + ".html";
            case "service" -> "/service/reference/" + titleSlug + ".html";
            default -> "/industry/" + industrySlug(site) + "/topic/" + titleSlug + ".html";
        };
    }

    private String industrySlug(PublishSite site) {
        String industry = firstIndustry(site);
        if (StringUtils.hasText(industry)) {
            String mapped = INDUSTRY_PATH_SLUGS.get(industry.trim());
            if (StringUtils.hasText(mapped)) {
                return mapped;
            }
            String slug = slugText(industry);
            if (StringUtils.hasText(slug)) {
                return slug;
            }
        }
        return "general";
    }

    private String regionSlug(String value, String fallback) {
        if (StringUtils.hasText(value)) {
            String trimmed = value.trim();
            String mapped = REGION_SLUGS.get(trimmed);
            if (StringUtils.hasText(mapped)) {
                return mapped;
            }
            String slug = slugText(trimmed);
            if (StringUtils.hasText(slug)) {
                return slug;
            }
        }
        return fallback;
    }

    private String slugText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "");
        String slug = normalized.replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
        return slug.length() <= 96 ? slug : slug.substring(0, 96).replaceAll("-+$", "");
    }

    private String toPlainText(String markdown) {
        if (!StringUtils.hasText(markdown)) {
            return "";
        }
        return markdown
                .replaceAll("(?m)^#{1,6}\\s*", "")
                .replaceAll("!\\[[^]]*]\\([^)]*\\)", "")
                .replaceAll("\\[[^]]+]\\([^)]*\\)", "")
                .replaceAll("[*_`>\\-]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String truncate(String text, int maxLength) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private void putAdminToken(Map<String, String> headers, PublishSite site) {
        String token = resolveAdminToken(site);
        if (StringUtils.hasText(token)) {
            headers.put("X-Admin-Token", token);
        }
    }

    private String resolveAdminToken(PublishSite site) {
        if (site == null) {
            return null;
        }
        String credential = platformCredentialService.resolveCredential(site.getCredentialRef(), site.getApiCredentialEncrypted());
        if (StringUtils.hasText(credential)) {
            return credential.trim();
        }
        if (StringUtils.hasText(site.getApiCredential())) {
            return site.getApiCredential().trim();
        }
        return legacyHeaderToken(site.getRequestHeaderTemplate());
    }

    private String legacyHeaderToken(String rawHeaders) {
        if (!StringUtils.hasText(rawHeaders)) {
            return null;
        }
        try {
            JSONObject obj = JSONUtil.parseObj(rawHeaders);
            Object value = obj.get("X-Admin-Token");
            if (value == null) {
                value = obj.get("x-admin-token");
            }
            return value == null ? null : String.valueOf(value).trim();
        } catch (Exception ignored) {
            return null;
        }
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

    private String classifyBusinessFailure(String message) {
        String lowered = message == null ? "" : message.toLowerCase(Locale.ROOT);
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

    private String validationFailureKind(List<String> errors) {
        String message = errors == null ? "" : String.join(";", errors).toLowerCase(Locale.ROOT);
        if (message.contains("x-admin-token") || message.contains("token")) {
            return FailureKind.AUTH;
        }
        return FailureKind.CLIENT_ERROR;
    }

    private boolean containsDuplicateSignal(String body) {
        if (!StringUtils.hasText(body)) {
            return false;
        }
        String lowered = body.toLowerCase(Locale.ROOT);
        return lowered.contains("duplicate")
                || lowered.contains("already exists")
                || lowered.contains("conflict")
                || lowered.contains("重复")
                || lowered.contains("已存在");
    }
}
