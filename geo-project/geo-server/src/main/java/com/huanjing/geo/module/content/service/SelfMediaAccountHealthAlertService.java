package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.module.content.credential.entity.SelfMediaCookieCredential;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.entity.AccountAuthRiskScanBatch;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaCookieCredentialMapper;
import com.huanjing.geo.module.content.mapper.AccountAuthRiskScanBatchMapper;
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformCapabilityContract;
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformPublishChannel;
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformScheduleAdapterRouter;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.entity.BrandOperatorAssignment;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.customer.mapper.BrandOperatorAssignmentMapper;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.system.service.SystemAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SelfMediaAccountHealthAlertService {

    private static final String ALERT_TYPE = "SELF_MEDIA_ACCOUNT_AUTH_HEALTH";
    private static final String SOURCE = "self_media_account_health";
    private static final String FALLBACK_RECIPIENT_ROLE = "delivery_manager";
    private static final String AUTH_MODE_COOKIE = "COOKIE";
    private static final String STATUS_ACTIVE = "active";
    private static final List<String> BRAND_OPERATOR_ROLES = List.of("PRIMARY", "SECONDARY");
    private static final Set<String> ALL_ISSUE_CODES = Set.of(
            "OFFICIAL_CREDENTIAL_MISSING",
            "OFFICIAL_CREDENTIAL_EXPIRED",
            "OFFICIAL_CREDENTIAL_EXPIRING",
            "OFFICIAL_ACCOUNT_INACTIVE",
            "COOKIE_CREDENTIAL_MISSING",
            "COOKIE_CREDENTIAL_EXPIRED",
            "COOKIE_CREDENTIAL_EXPIRING",
            "ACCOUNT_REVERIFY_OVERDUE",
            "ACCOUNT_REVERIFY_DUE_SOON"
    );

    private final SelfMediaAccountMapper accountMapper;
    private final SelfMediaCookieCredentialMapper credentialMapper;
    private final BrandMapper brandMapper;
    private final BrandOperatorAssignmentMapper brandOperatorAssignmentMapper;
    private final CompanyMapper companyMapper;
    private final SelfMediaPlatformScheduleAdapterRouter platformRouter;
    private final SystemAlertService systemAlertService;
    private final SelfMediaAuthHealthPolicyService authHealthPolicyService;
    private final SelfMediaAuthRiskEvaluator authRiskEvaluator;
    private final AccountAuthRiskScanBatchMapper scanBatchMapper;

    @Value("${geo.self-media-account-health.scan-limit:500}")
    private int scanLimit;

    @Value("${geo.self-media-account-health.credential-expiring-days:7}")
    private int credentialExpiringDays;

    @Value("${geo.self-media-account-health.cookie-valid-days:30}")
    private int defaultCookieValidDays;

    @Value("${geo.self-media-account-health.cookie-platform-valid-days:toutiao:30,baijiahao:30,zhihu:30,xiaohongshu:7}")
    private String cookiePlatformValidDays;

    public int scanOnce() {
        AccountAuthRiskScanBatch batch = startBatch();
        List<SelfMediaAccount> accounts = loadMonitoredAccounts();
        if (accounts.isEmpty()) {
            finishBatch(batch, 0, 0, 0, null);
            return 0;
        }
        Map<Long, SelfMediaCookieCredential> credentialsByAccountId = loadCookieCredentials(accounts);
        Map<Long, BrandContext> brandContextByBrandId = loadBrandContexts(accounts);
        LocalDateTime now = LocalDateTime.now();

        int changed = 0;
        int succeeded = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();
        for (SelfMediaAccount account : accounts) {
            try {
                HealthIssue issue = detectIssue(account, credentialsByAccountId.get(account.getId()), now);
                BrandContext brandContext = brandContextByBrandId.getOrDefault(account.getBrandId(), BrandContext.fallback());
                changed += reconcile(account, issue, brandContext);
                succeeded++;
            } catch (Exception ex) {
                failed++;
                if (errors.size() < 5) errors.add(account.getId() + ":" + ex.getMessage());
            }
        }
        batch.setLastScannedId(accounts.get(accounts.size() - 1).getId());
        finishBatch(batch, accounts.size(), succeeded, failed, String.join("; ", errors));
        return changed;
    }

    private List<SelfMediaAccount> loadMonitoredAccounts() {
        Set<String> platforms = monitoredPlatforms();
        if (platforms.isEmpty()) {
            return List.of();
        }
        List<SelfMediaAccount> result = new ArrayList<>();
        long lastId = 0L;
        int pageSize = Math.max(scanLimit, 1);
        while (true) {
            List<SelfMediaAccount> page = accountMapper.selectList(new LambdaQueryWrapper<SelfMediaAccount>()
                    .isNull(SelfMediaAccount::getDeletedAt)
                    .in(SelfMediaAccount::getPlatform, platforms)
                    .gt(SelfMediaAccount::getId, lastId)
                    .orderByAsc(SelfMediaAccount::getId)
                    .last("LIMIT " + pageSize));
            if (page.isEmpty()) break;
            result.addAll(page);
            lastId = page.get(page.size() - 1).getId();
            if (page.size() < pageSize) break;
        }
        return result;
    }

    private Set<String> monitoredPlatforms() {
        return platformRouter.contracts().stream()
                .filter(contract -> SelfMediaPlatformPublishChannel.OFFICIAL_API.equals(contract.publishChannel())
                        || SelfMediaPlatformPublishChannel.ADSPOWER_AUTOMATION.equals(contract.publishChannel()))
                .map(SelfMediaPlatformCapabilityContract::platform)
                .filter(StringUtils::hasText)
                .map(this::normalize)
                .collect(Collectors.toUnmodifiableSet());
    }

    private Map<Long, SelfMediaCookieCredential> loadCookieCredentials(List<SelfMediaAccount> accounts) {
        List<Long> cookieAccountIds = accounts.stream()
                .filter(account -> AUTH_MODE_COOKIE.equalsIgnoreCase(account.getAuthMode()))
                .map(SelfMediaAccount::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (cookieAccountIds.isEmpty()) {
            return Map.of();
        }
        return credentialMapper.selectActiveMetaByAccountIds(cookieAccountIds)
                .stream()
                .collect(Collectors.groupingBy(SelfMediaCookieCredential::getSelfMediaAccountId))
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream()
                                .max(Comparator.comparing(SelfMediaCookieCredential::getVersion))
                                .orElse(null)
                ));
    }

    private Map<Long, BrandContext> loadBrandContexts(List<SelfMediaAccount> accounts) {
        List<Long> brandIds = accounts.stream()
                .map(SelfMediaAccount::getBrandId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (brandIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Brand> brandById = brandMapper.selectBatchIds(brandIds).stream()
                .filter(brand -> brand.getDeletedAt() == null)
                .collect(Collectors.toMap(Brand::getId, Function.identity()));
        List<Long> companyIds = brandById.values().stream()
                .map(Brand::getCompanyId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Company> companyById = companyIds.isEmpty()
                ? Map.of()
                : companyMapper.selectBatchIds(companyIds).stream()
                .filter(company -> company.getDeletedAt() == null)
                .collect(Collectors.toMap(Company::getId, Function.identity()));
        Map<Long, Long> assignedOperatorByBrandId = loadAssignedOperators(brandIds);

        Map<Long, BrandContext> result = new LinkedHashMap<>();
        for (Map.Entry<Long, Brand> entry : brandById.entrySet()) {
            Brand brand = entry.getValue();
            Company company = companyById.get(entry.getValue().getCompanyId());
            Long recipientUserId = assignedOperatorByBrandId.get(brand.getId());
            if (recipientUserId == null && company != null) {
                recipientUserId = company.getOwnerId();
            }
            result.put(entry.getKey(), new BrandContext(
                    recipientUserId,
                    recipientUserId == null ? FALLBACK_RECIPIENT_ROLE : null,
                    company == null ? null : company.getCompanyName(),
                    brand.getBrandName()
            ));
        }
        return result;
    }

    private Map<Long, Long> loadAssignedOperators(List<Long> brandIds) {
        if (brandIds.isEmpty()) {
            return Map.of();
        }
        return brandOperatorAssignmentMapper.selectList(new LambdaQueryWrapper<BrandOperatorAssignment>()
                        .in(BrandOperatorAssignment::getBrandId, brandIds)
                        .eq(BrandOperatorAssignment::getStatus, STATUS_ACTIVE)
                        .in(BrandOperatorAssignment::getRole, BRAND_OPERATOR_ROLES)
                        .orderByAsc(BrandOperatorAssignment::getBrandId)
                        .orderByDesc(BrandOperatorAssignment::getAssignedAt, BrandOperatorAssignment::getId))
                .stream()
                .collect(Collectors.toMap(
                        BrandOperatorAssignment::getBrandId,
                        BrandOperatorAssignment::getOperatorId,
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));
    }

    private HealthIssue detectIssue(SelfMediaAccount account,
                                    SelfMediaCookieCredential credential,
                                    LocalDateTime now) {
        String platform = normalize(account.getPlatform());
        if (AUTH_MODE_COOKIE.equalsIgnoreCase(account.getAuthMode())) {
            return detectCookieIssue(account, credential, now);
        }
        if (isOfficialApiPlatform(platform)) {
            return detectOfficialApiIssue(account, now);
        }
        return null;
    }

    private HealthIssue detectCookieIssue(SelfMediaAccount account,
                                          SelfMediaCookieCredential credential,
                                          LocalDateTime now) {
        if (!STATUS_ACTIVE.equalsIgnoreCase(account.getStatus())) {
            return null;
        }
        var policy = authHealthPolicyService.findPolicy(account.getPlatform());
        if (policy == null) return null;
        LocalDateTime declaredExpiry = credential != null
                && Set.of("cookie_expires", "manual").contains(String.valueOf(credential.getExpirySource()))
                ? credential.getExpiresAt() : null;
        var risk = authRiskEvaluator.evaluate(new SelfMediaAuthRiskEvaluator.Input(policy, now, true, credential != null,
                account.getLastLoginVerifiedAt(), credential == null ? null : credential.getCapturedAt(),
                declaredExpiry, account.getCreatedAt()));
        account.setRecommendedReverifyAt(risk.recommendedReverifyAt());
        account.setUpdatedAt(now);
        accountMapper.updateById(account);
        if ("credential_missing".equals(risk.riskStatus())) {
            return issue("COOKIE_CREDENTIAL_MISSING", "warn", "尚无可信登录健康记录，请验证当前登录状态");
        }
        long daysUntil = risk.recommendedReverifyAt() == null ? 0 : daysUntil(now, risk.recommendedReverifyAt());
        if ("reverify_overdue".equals(risk.riskStatus())) {
            return issue("ACCOUNT_REVERIFY_OVERDUE", "warn", "已超过建议复验时间，请确认当前登录状态",
                    risk.recommendedReverifyAt(), daysUntil);
        }
        if ("reverify_due_soon".equals(risk.riskStatus())) {
            return issue("ACCOUNT_REVERIFY_DUE_SOON", "warn", "即将需要复验，请提前确认当前登录状态",
                    risk.recommendedReverifyAt(), daysUntil);
        }
        return null;
    }

    private HealthIssue detectOfficialApiIssue(SelfMediaAccount account, LocalDateTime now) {
        if (!STATUS_ACTIVE.equalsIgnoreCase(account.getStatus())) {
            return issue("OFFICIAL_ACCOUNT_INACTIVE", "high", "官方 API 授权账号未启用或已失效");
        }
        if (!StringUtils.hasText(account.getRefreshTokenCipher())
                && !StringUtils.hasText(account.getAccessTokenCipher())) {
            return issue("OFFICIAL_CREDENTIAL_MISSING", "high", "官方 API 授权凭据缺失");
        }
        LocalDateTime refreshTokenExpiresAt = account.getRefreshTokenExpiresAt();
        if (refreshTokenExpiresAt != null && !refreshTokenExpiresAt.isAfter(now)) {
            long daysUntilExpiry = daysUntil(now, refreshTokenExpiresAt);
            return issue("OFFICIAL_CREDENTIAL_EXPIRED", "high",
                    expiredMessage("官方 API 长期授权", daysUntilExpiry), refreshTokenExpiresAt, daysUntilExpiry);
        }
        if (refreshTokenExpiresAt != null
                && refreshTokenExpiresAt.isBefore(now.plusDays(Math.max(credentialExpiringDays, 1)))) {
            long daysUntilExpiry = daysUntil(now, refreshTokenExpiresAt);
            return issue("OFFICIAL_CREDENTIAL_EXPIRING", expiringSeverity(daysUntilExpiry),
                    expiringMessage("官方 API 长期授权", daysUntilExpiry), refreshTokenExpiresAt, daysUntilExpiry);
        }
        return null;
    }

    private int cookieValidDays(String platform) {
        String normalized = normalize(platform);
        if (StringUtils.hasText(cookiePlatformValidDays)) {
            for (String item : cookiePlatformValidDays.split(",")) {
                String[] parts = item.trim().split(":");
                if (parts.length != 2 || !normalized.equals(normalize(parts[0]))) {
                    continue;
                }
                try {
                    return Math.max(Integer.parseInt(parts[1].trim()), 1);
                } catch (NumberFormatException ignored) {
                    break;
                }
            }
        }
        return Math.max(defaultCookieValidDays, 1);
    }

    private boolean isOfficialApiPlatform(String platform) {
        return platformRouter.contract(platform)
                .map(contract -> SelfMediaPlatformPublishChannel.OFFICIAL_API.equals(contract.publishChannel()))
                .orElse(false);
    }

    private int reconcile(SelfMediaAccount account, HealthIssue issue, BrandContext brandContext) {
        String prefix = dedupePrefix(account);
        if (issue == null) {
            systemAlertService.resolveOpenByDedupeKeyPrefix(prefix, null);
            return 1;
        }
        for (String issueCode : ALL_ISSUE_CODES) {
            if (!issueCode.equals(issue.code())) {
                systemAlertService.resolveOpenByDedupeKey(prefix + issueCode, null);
            }
        }
        systemAlertService.createRecipientAlert(
                ALERT_TYPE,
                issue.severity(),
                SOURCE,
                message(account, issue, brandContext),
                context(account, issue, brandContext),
                brandContext.recipientUserId(),
                brandContext.recipientRole(),
                prefix + issue.code()
        );
        return 1;
    }

    private AccountAuthRiskScanBatch startBatch() {
        AccountAuthRiskScanBatch row = new AccountAuthRiskScanBatch();
        row.setScanType("self_media");
        row.setStatus("running");
        row.setStartedAt(LocalDateTime.now());
        row.setTotalCount(0);
        row.setSuccessCount(0);
        row.setFailureCount(0);
        row.setSkippedCount(0);
        row.setCreatedAt(LocalDateTime.now());
        row.setUpdatedAt(LocalDateTime.now());
        scanBatchMapper.insert(row);
        return row;
    }

    private void finishBatch(AccountAuthRiskScanBatch row, int total, int success, int failed, String error) {
        row.setStatus(failed == 0 ? "succeeded" : success > 0 ? "partial_success" : "failed");
        row.setFinishedAt(LocalDateTime.now());
        row.setTotalCount(total);
        row.setSuccessCount(success);
        row.setFailureCount(failed);
        row.setErrorSummary(StringUtils.hasText(error) ? error.substring(0, Math.min(error.length(), 1000)) : null);
        row.setUpdatedAt(LocalDateTime.now());
        scanBatchMapper.updateById(row);
    }

    private String message(SelfMediaAccount account, HealthIssue issue, BrandContext brandContext) {
        return customerDisplay(brandContext)
                + platformDisplayName(account.getPlatform())
                + "账号「"
                + displayName(account)
                + "」"
                + issue.message();
    }

    private Map<String, Object> context(SelfMediaAccount account, HealthIssue issue, BrandContext brandContext) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("brandId", account.getBrandId());
        context.put("companyName", brandContext.companyName());
        context.put("brandName", brandContext.brandName());
        context.put("selfMediaAccountId", account.getId());
        context.put("platform", normalize(account.getPlatform()));
        context.put("platformName", platformDisplayName(account.getPlatform()));
        context.put("accountName", account.getAccountName());
        context.put("platformAccountId", account.getPlatformAccountId());
        context.put("issueCode", issue.code());
        context.put("authMode", account.getAuthMode());
        context.put("status", account.getStatus());
        context.put("authExpiresAt", issue.expiresAt());
        context.put("daysUntilExpiry", issue.daysUntilExpiry());
        context.put("lastAuthCheckedAt", account.getLastAuthCheckedAt());
        context.put("lastAuthError", account.getLastAuthError());
        context.put("route", "/admin/brands/" + account.getBrandId() + "?tab=self-media&accountId=" + account.getId());
        return context;
    }

    private HealthIssue issue(String code, String severity, String message) {
        return issue(code, severity, message, null, null);
    }

    private HealthIssue issue(String code, String severity, String message, LocalDateTime expiresAt, Long daysUntilExpiry) {
        return new HealthIssue(code, severity, message, expiresAt, daysUntilExpiry);
    }

    private long daysUntil(LocalDateTime now, LocalDateTime expiresAt) {
        return ChronoUnit.DAYS.between(now.toLocalDate(), expiresAt.toLocalDate());
    }

    private String expiringSeverity(long daysUntilExpiry) {
        return daysUntilExpiry <= 3 ? "high" : "warn";
    }

    private String expiringMessage(String subject, long daysUntilExpiry) {
        if (daysUntilExpiry <= 0) {
            return subject + "今天到期，请当天完成更新，避免影响发布";
        }
        if (daysUntilExpiry <= 3) {
            return subject + "还剩 " + daysUntilExpiry + " 天到期，请优先更新账号信息";
        }
        return subject + "还剩 " + daysUntilExpiry + " 天到期，请提前安排账号信息更新";
    }

    private String expiredMessage(String subject, long daysUntilExpiry) {
        long overdueDays = Math.abs(daysUntilExpiry);
        if (overdueDays <= 0) {
            return subject + "已到期，请立即更新账号信息";
        }
        return subject + "已过期 " + overdueDays + " 天，请立即更新账号信息";
    }

    private String dedupePrefix(SelfMediaAccount account) {
        return "self_media_auth:" + account.getId() + ":";
    }

    private String customerDisplay(BrandContext brandContext) {
        if (StringUtils.hasText(brandContext.companyName()) && StringUtils.hasText(brandContext.brandName())) {
            return "客户「" + brandContext.companyName().trim() + "」品牌「" + brandContext.brandName().trim() + "」的";
        }
        if (StringUtils.hasText(brandContext.companyName())) {
            return "客户「" + brandContext.companyName().trim() + "」的";
        }
        if (StringUtils.hasText(brandContext.brandName())) {
            return "品牌「" + brandContext.brandName().trim() + "」的";
        }
        return "";
    }

    private String displayName(SelfMediaAccount account) {
        if (StringUtils.hasText(account.getAccountName())) {
            return account.getAccountName().trim();
        }
        if (StringUtils.hasText(account.getPlatformAccountId())) {
            return account.getPlatformAccountId().trim();
        }
        return String.valueOf(account.getId());
    }

    private String platformDisplayName(String platform) {
        String normalized = normalize(platform);
        return platformRouter.contract(normalized)
                .map(SelfMediaPlatformCapabilityContract::displayName)
                .filter(StringUtils::hasText)
                .orElse(switch (normalized) {
                    case "wechat_mp", "wechat" -> "微信公众号";
                    case "douyin" -> "抖音图文";
                    case "toutiao" -> "今日头条";
                    case "baijiahao" -> "百家号";
                    case "zhihu" -> "知乎";
                    case "xiaohongshu" -> "小红书";
                    default -> normalized;
                });
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
    }

    private record HealthIssue(String code, String severity, String message, LocalDateTime expiresAt,
                               Long daysUntilExpiry) {
    }

    private record BrandContext(Long recipientUserId, String recipientRole, String companyName, String brandName) {
        static BrandContext fallback() {
            return new BrandContext(null, FALLBACK_RECIPIENT_ROLE, null, null);
        }
    }
}
