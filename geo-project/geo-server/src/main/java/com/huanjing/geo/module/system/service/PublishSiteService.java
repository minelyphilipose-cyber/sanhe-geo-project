package com.huanjing.geo.module.system.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.config.BrandGeoSiteProperties;
import com.huanjing.geo.module.system.dto.PublishSiteCreateRequest;
import com.huanjing.geo.module.system.dto.PublishSiteStatusUpdateRequest;
import com.huanjing.geo.module.system.dto.PublishSiteUpdateRequest;
import com.huanjing.geo.module.system.entity.PublishSite;
import com.huanjing.geo.module.system.entity.SysDictItem;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.PublishSiteMapper;
import com.huanjing.geo.module.system.mapper.SysDictItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PublishSiteService {

    private static final Set<String> WRITE_ROLES = Set.of("super_admin", "manager");
    private static final Set<String> READ_ROLES = Set.of("super_admin", "manager", "delivery_manager", "operator");
    private static final Set<String> TIER_SET = Set.of("S0", "S1", "S2");
    private static final Set<String> STATUS_SET = Set.of("active", "suspended", "maintenance");
    private static final Set<String> HEALTH_SET = Set.of("normal", "slow", "high_failure", "degraded");
    private static final Set<String> METHOD_SET = Set.of("rest_api", "ftp", "email", "manual", "brand_geo_site");
    private static final Set<String> HTTP_METHOD_SET = Set.of("POST", "PUT");
    private static final Set<String> AUTH_SET = Set.of("api_key", "bearer_token", "basic_auth", "oauth2");

    private final PublishSiteMapper publishSiteMapper;
    private final SysDictItemMapper sysDictItemMapper;
    private final CurrentUserService currentUserService;
    private final PlatformCredentialService platformCredentialService;
    private final BrandGeoSiteProperties brandGeoSiteProperties;

    public List<PublishSite> list(String tier, String status, String industry) {
        ensureReadRole();
        LambdaQueryWrapper<PublishSite> wrapper = new LambdaQueryWrapper<PublishSite>()
                .eq(PublishSite::getIsFramework, 0)
                .orderByAsc(PublishSite::getTier)
                .orderByAsc(PublishSite::getSiteName)
                .orderByAsc(PublishSite::getId);
        if (StringUtils.hasText(tier)) {
            wrapper.eq(PublishSite::getTier, tier.trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(PublishSite::getStatus, status.trim().toLowerCase(Locale.ROOT));
        }
        if (StringUtils.hasText(industry)) {
            wrapper.like(PublishSite::getIndustryTags, industry.trim().toLowerCase(Locale.ROOT));
        }
        return publishSiteMapper.selectList(wrapper);
    }

    public PublishSite detail(Long id) {
        ensureReadRole();
        return requireById(id);
    }

    public PublishSite create(PublishSiteCreateRequest req) {
        ensureWriteRole();
        validate(req.getSiteName(), req.getSiteCode(), req.getDomain(), req.getIndustryTags(), req.getTier(), req.getStatus(), req.getIntegrationMethod(),
                req.getHttpMethod(), req.getAuthType(), req.getCurrentHealthStatus());
        PublishSite site = new PublishSite();
        fill(site, req.getSiteName(), req.getSiteCode(), req.getDomain(), req.getIconUrl(), req.getIndustryTags(), req.getTier(), req.getStatus(),
                req.getIntegrationMethod(), req.getApiEndpoint(), req.getHttpMethod(), req.getAuthType(),
                req.getCredentialRef(), req.getApiCredential(), req.getRequestHeaderTemplate(), req.getRequestBodyTemplate(),
                req.getResponseUrlPath(), req.getContentConstraints(), req.getCurrentHealthStatus(), req.getRemark());
        publishSiteMapper.insert(site);
        return site;
    }

    public PublishSite update(Long id, PublishSiteUpdateRequest req) {
        ensureWriteRole();
        validate(req.getSiteName(), req.getSiteCode(), req.getDomain(), req.getIndustryTags(), req.getTier(), req.getStatus(), req.getIntegrationMethod(),
                req.getHttpMethod(), req.getAuthType(), req.getCurrentHealthStatus());
        PublishSite site = requireById(id);
        fill(site, req.getSiteName(), req.getSiteCode(), req.getDomain(), req.getIconUrl(), req.getIndustryTags(), req.getTier(), req.getStatus(),
                req.getIntegrationMethod(), req.getApiEndpoint(), req.getHttpMethod(), req.getAuthType(),
                req.getCredentialRef(), req.getApiCredential(), req.getRequestHeaderTemplate(), req.getRequestBodyTemplate(),
                req.getResponseUrlPath(), req.getContentConstraints(), req.getCurrentHealthStatus(), req.getRemark());
        publishSiteMapper.updateById(site);
        return site;
    }

    public PublishSite updateStatus(Long id, PublishSiteStatusUpdateRequest req) {
        ensureWriteRole();
        if (!StringUtils.hasText(req.getStatus()) || !STATUS_SET.contains(req.getStatus().trim().toLowerCase(Locale.ROOT))) {
            throw new BizException(400, "status must be active/suspended/maintenance");
        }
        PublishSite site = requireById(id);
        site.setStatus(req.getStatus().trim().toLowerCase(Locale.ROOT));
        publishSiteMapper.updateById(site);
        return site;
    }

    public Map<String, Object> testConnectivity(Long id) {
        ensureWriteRole();
        PublishSite site = requireById(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("siteId", site.getId());
        result.put("siteName", site.getSiteName());
        result.put("domain", site.getDomain());
        if ("brand_geo_site".equalsIgnoreCase(site.getIntegrationMethod())) {
            return testBrandGeoSiteEndpoint(result);
        }
        String host = resolvePingHost(site);
        result.put("host", host);
        result.put("testType", "ping");
        return pingHost(result, host);
    }

    private Map<String, Object> testBrandGeoSiteEndpoint(Map<String, Object> result) {
        String endpoint = brandGeoSiteProperties.getEndpoint();
        result.put("testType", "endpoint_ping");
        result.put("endpoint", endpoint);
        if (!StringUtils.hasText(endpoint)) {
            result.put("success", false);
            result.put("reachable", false);
            result.put("message", "BRAND_GEO_SITE_ENDPOINT is not configured");
            return result;
        }
        String host = resolveHost(endpoint.trim());
        result.put("host", host);
        return pingHost(result, host);
    }

    private Map<String, Object> pingHost(Map<String, Object> result, String host) {
        try {
            long startedAt = System.currentTimeMillis();
            PingResult ping = runPing(host);
            boolean reachable = ping.reachable();
            result.put("success", reachable);
            result.put("reachable", reachable);
            result.put("elapsedMs", System.currentTimeMillis() - startedAt);
            if (!reachable) {
                result.put("message", StringUtils.hasText(ping.output()) ? ping.output().trim() : "ping unreachable");
            }
            return result;
        } catch (Exception ex) {
            result.put("success", false);
            result.put("reachable", false);
            result.put("message", ex.getMessage());
            return result;
        }
    }

    protected PingResult runPing(String host) throws Exception {
        Process process = new ProcessBuilder(pingCommand(host))
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!finished) {
            process.destroyForcibly();
        }
        return new PingResult(finished && process.exitValue() == 0, output);
    }

    private PublishSite requireById(Long id) {
        PublishSite site = publishSiteMapper.selectById(id);
        if (site == null) {
            throw new BizException(404, "Publish site not found");
        }
        return site;
    }

    private void fill(PublishSite site,
                      String siteName,
                      String siteCode,
                      String domain,
                      String iconUrl,
                      List<String> industryTags,
                      String tier,
                      String status,
                      String integrationMethod,
                      String apiEndpoint,
                      String httpMethod,
                      String authType,
                      String credentialRef,
                      String apiCredential,
                      String requestHeaderTemplate,
                      String requestBodyTemplate,
                      String responseUrlPath,
                      String contentConstraints,
                      String currentHealthStatus,
                      String remark) {
        site.setSiteName(siteName.trim());
        site.setSiteCode(normalizeSiteCode(siteCode));
        site.setDomain(domain.trim().toLowerCase(Locale.ROOT));
        site.setIconUrl(StringUtils.hasText(iconUrl) ? iconUrl.trim() : null);
        site.setIndustryTags(normalizeJsonArray(industryTags));
        site.setTier(tier.trim().toUpperCase(Locale.ROOT));
        site.setStatus(status.trim().toLowerCase(Locale.ROOT));
        site.setIntegrationMethod(integrationMethod.trim().toLowerCase(Locale.ROOT));
        site.setApiEndpoint(StringUtils.hasText(apiEndpoint) ? apiEndpoint.trim() : null);
        site.setHttpMethod(StringUtils.hasText(httpMethod) ? httpMethod.trim().toUpperCase(Locale.ROOT) : null);
        site.setAuthType(StringUtils.hasText(authType) ? authType.trim().toLowerCase(Locale.ROOT) : null);
        site.setCredentialRef(StringUtils.hasText(credentialRef) ? credentialRef.trim() : null);
        site.setApiCredentialEncrypted(platformCredentialService.encryptForStorage(apiCredential));
        site.setRequestHeaderTemplate(normalizeJsonObject(requestHeaderTemplate));
        site.setRequestBodyTemplate(normalizeJsonObject(requestBodyTemplate));
        site.setResponseUrlPath(StringUtils.hasText(responseUrlPath) ? responseUrlPath.trim() : null);
        site.setContentConstraints(normalizeJsonObject(contentConstraints));
        site.setCurrentHealthStatus(StringUtils.hasText(currentHealthStatus) ? currentHealthStatus.trim().toLowerCase(Locale.ROOT) : "normal");
        site.setRemark(remark);
    }

    private void validate(String siteName,
                          String siteCode,
                          String domain,
                          List<String> industryTags,
                          String tier,
                          String status,
                          String integrationMethod,
                          String httpMethod,
                          String authType,
                          String currentHealthStatus) {
        if (!StringUtils.hasText(siteName)) {
            throw new BizException(400, "site_name is required");
        }
        if (!StringUtils.hasText(siteCode)) {
            throw new BizException(400, "site_code is required");
        }
        normalizeSiteCode(siteCode);
        if (!StringUtils.hasText(domain)) {
            throw new BizException(400, "domain is required");
        }
        normalizeJsonArray(industryTags);
        if (!StringUtils.hasText(tier) || !TIER_SET.contains(tier.trim().toUpperCase(Locale.ROOT))) {
            throw new BizException(400, "tier must be S0/S1/S2");
        }
        if (!StringUtils.hasText(status) || !STATUS_SET.contains(status.trim().toLowerCase(Locale.ROOT))) {
            throw new BizException(400, "status must be active/suspended/maintenance");
        }
        if (!StringUtils.hasText(integrationMethod) || !METHOD_SET.contains(integrationMethod.trim().toLowerCase(Locale.ROOT))) {
            throw new BizException(400, "integration_method must be rest_api/ftp/email/manual/brand_geo_site");
        }
        if (StringUtils.hasText(httpMethod) && !HTTP_METHOD_SET.contains(httpMethod.trim().toUpperCase(Locale.ROOT))) {
            throw new BizException(400, "http_method must be POST/PUT");
        }
        if (StringUtils.hasText(authType) && !AUTH_SET.contains(authType.trim().toLowerCase(Locale.ROOT))) {
            throw new BizException(400, "auth_type must be api_key/bearer_token/basic_auth/oauth2");
        }
        if (StringUtils.hasText(currentHealthStatus) && !HEALTH_SET.contains(currentHealthStatus.trim().toLowerCase(Locale.ROOT))) {
            throw new BizException(400, "current_health_status must be normal/slow/high_failure/degraded");
        }
    }

    private void ensureWriteRole() {
        SysUser user = currentUserService.requireCurrentUser();
        if (!WRITE_ROLES.contains(user.getRole())) {
            throw new BizException(403, "No permission to manage publish sites");
        }
    }

    private void ensureReadRole() {
        SysUser user = currentUserService.requireCurrentUser();
        if (!READ_ROLES.contains(user.getRole())) {
            throw new BizException(403, "No permission to view publish sites");
        }
    }

    private String normalizeJsonObject(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return JSONUtil.parseObj(raw.trim()).toString();
        } catch (Exception ex) {
            throw new BizException(400, "Invalid JSON object");
        }
    }

    private String normalizeJsonArray(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new BizException(400, "industry_tags is required");
        }
        try {
            List<String> normalized = new ArrayList<>();
            for (String tag : raw) {
                if (!StringUtils.hasText(tag)) {
                    continue;
                }
                String key = tag.trim().toLowerCase(Locale.ROOT);
                if (!normalized.contains(key)) {
                    normalized.add(key);
                }
            }
            if (normalized.isEmpty()) {
                throw new BizException(400, "industry_tags cannot be empty");
            }
            return JSONUtil.toJsonStr(normalized);
        } catch (Exception ex) {
            if (ex instanceof BizException bizException) {
                throw bizException;
            }
            throw new BizException(400, "Invalid industry_tags");
        }
    }

    private String normalizeSiteCode(String value) {
        String siteCode = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!siteCode.matches("^[a-z0-9][a-z0-9_-]{1,127}$")) {
            throw new BizException(400, "site_code must be 2-128 chars and only contain lowercase letters, numbers, underscores or hyphens");
        }
        return siteCode;
    }

    private String resolvePingHost(PublishSite site) {
        String raw = StringUtils.hasText(site.getDomain()) ? site.getDomain().trim() : site.getApiEndpoint();
        if (!StringUtils.hasText(raw)) {
            throw new BizException(400, "domain is required");
        }
        return resolveHost(raw);
    }

    private String resolveHost(String raw) {
        String value = raw.trim();
        try {
            URI uri = value.contains("://") ? URI.create(value) : URI.create("http://" + value);
            if (StringUtils.hasText(uri.getHost())) {
                return uri.getHost();
            }
        } catch (Exception ignored) {
            // Fall through to plain host normalization.
        }
        int slash = value.indexOf('/');
        if (slash >= 0) {
            value = value.substring(0, slash);
        }
        int colon = value.indexOf(':');
        if (colon >= 0) {
            value = value.substring(0, colon);
        }
        if (!StringUtils.hasText(value)) {
            throw new BizException(400, "domain is invalid");
        }
        return value;
    }

    protected record PingResult(boolean reachable, String output) {}

    private List<String> pingCommand(String host) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return Arrays.asList("ping", "-n", "1", "-w", "3000", host);
        }
        return Arrays.asList("ping", "-c", "1", "-W", "3", host);
    }

    private Set<String> queryValidIndustryTags() {
        return sysDictItemMapper.selectList(
                        new LambdaQueryWrapper<SysDictItem>()
                                .eq(SysDictItem::getDictType, "industry_tag")
                                .eq(SysDictItem::getEnabled, true)
                                .select(SysDictItem::getDictKey)
                ).stream()
                .map(SysDictItem::getDictKey)
                .filter(StringUtils::hasText)
                .map(item -> item.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
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

    private void applyAuthHeader(Map<String, String> headers, String authType, String credential) {
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

    private String replacePlaceholders(String template, Map<String, String> values) {
        String result = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }

    private String extractJsonPath(String body, String responseUrlPath) {
        if (!StringUtils.hasText(body) || !StringUtils.hasText(responseUrlPath)) {
            return null;
        }
        String path = responseUrlPath.trim();
        if (!path.startsWith("$.")) {
            return null;
        }
        try {
            Object cursor = JSONUtil.parseObj(body);
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
}
