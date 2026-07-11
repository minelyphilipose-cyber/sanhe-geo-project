package com.huanjing.geo.module.content.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.module.content.credential.entity.SelfMediaCookieCredential;
import com.huanjing.geo.module.content.dto.AccountAuthHealthOverviewVO;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaCookieCredentialMapper;
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformCapabilityContract;
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformPublishChannel;
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformScheduleAdapterRouter;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.entity.BrandOperatorAssignment;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.customer.mapper.BrandOperatorAssignmentMapper;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.system.entity.PublishSite;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.entity.SystemAlert;
import com.huanjing.geo.module.system.mapper.PublishSiteMapper;
import com.huanjing.geo.module.system.mapper.SysUserMapper;
import com.huanjing.geo.module.system.mapper.SystemAlertMapper;
import com.huanjing.geo.module.system.service.PlatformCredentialService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
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
public class AccountAuthHealthOverviewService {

    private static final Set<String> FORUM_METHODS = Set.of("forum_playwright", "discuz_http");
    private static final Set<String> BRAND_OPERATOR_ROLES = Set.of("PRIMARY", "SECONDARY");

    private final SelfMediaAccountMapper selfMediaAccountMapper;
    private final SelfMediaCookieCredentialMapper credentialMapper;
    private final SelfMediaPlatformScheduleAdapterRouter platformRouter;
    private final BrandMapper brandMapper;
    private final CompanyMapper companyMapper;
    private final BrandOperatorAssignmentMapper brandOperatorAssignmentMapper;
    private final SysUserMapper sysUserMapper;
    private final PublishSiteMapper publishSiteMapper;
    private final PlatformCredentialService platformCredentialService;
    private final SystemAlertMapper systemAlertMapper;
    private final SelfMediaAuthHealthPolicyService authHealthPolicyService;
    private final SelfMediaAuthRiskEvaluator authRiskEvaluator;

    @Value("${geo.self-media-account-health.credential-expiring-days:7}")
    private int selfMediaExpiringDays;

    @Value("${geo.self-media-account-health.cookie-valid-days:30}")
    private int defaultSelfMediaCookieValidDays;

    @Value("${geo.self-media-account-health.cookie-platform-valid-days:toutiao:30,baijiahao:30,zhihu:30,xiaohongshu:7}")
    private String cookiePlatformValidDays;

    @Value("${geo.forum-cookie-health.credential-expiring-days:7}")
    private int forumExpiringDays;

    @Value("${geo.forum-cookie-health.default-cookie-valid-days:30}")
    private int forumDefaultCookieValidDays;

    public AccountAuthHealthOverviewVO overview() {
        LocalDateTime now = LocalDateTime.now();
        List<AccountAuthHealthOverviewVO.RiskItem> items = new ArrayList<>();
        items.addAll(selfMediaItems(now));
        items.addAll(forumItems(now));
        items.sort(Comparator
                .comparingInt((AccountAuthHealthOverviewVO.RiskItem item) -> riskRank(item.riskStatus()))
                .thenComparing(item -> item.daysUntilExpiry() == null ? Long.MAX_VALUE : item.daysUntilExpiry())
                .thenComparing(AccountAuthHealthOverviewVO.RiskItem::displayName, Comparator.nullsLast(String::compareTo)));

        List<AccountAuthHealthOverviewVO.AlertGroup> alertGroups = alertGroups();
        return new AccountAuthHealthOverviewVO(
                now,
                summary(items, alertGroups),
                items,
                alertGroups,
                trendBuckets(items, now.toLocalDate(), 14)
        );
    }

    public AccountAuthHealthOverviewVO refresh(SelfMediaAccountHealthAlertService selfMediaHealthAlertService,
                                               ForumCookieHealthAlertService forumCookieHealthAlertService) {
        selfMediaHealthAlertService.scanOnce();
        forumCookieHealthAlertService.scanOnce();
        return overview();
    }

    private List<AccountAuthHealthOverviewVO.RiskItem> selfMediaItems(LocalDateTime now) {
        List<SelfMediaAccount> accounts = selfMediaAccountMapper.selectList(new LambdaQueryWrapper<SelfMediaAccount>()
                .eq(SelfMediaAccount::getStatus, "active")
                .isNull(SelfMediaAccount::getDeletedAt)
                .orderByAsc(SelfMediaAccount::getBrandId)
                .orderByAsc(SelfMediaAccount::getId));
        if (accounts.isEmpty()) {
            return List.of();
        }
        List<Long> accountIds = accounts.stream().map(SelfMediaAccount::getId).filter(Objects::nonNull).toList();
        Map<Long, SelfMediaCookieCredential> credentialByAccountId = accountIds.isEmpty()
                ? Map.of()
                : credentialMapper.selectActiveMetaByAccountIds(accountIds).stream()
                .collect(Collectors.toMap(SelfMediaCookieCredential::getSelfMediaAccountId, Function.identity(), (first, ignored) -> first));
        Map<Long, BrandContext> brandContexts = brandContexts(accounts.stream()
                .map(SelfMediaAccount::getBrandId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        Map<String, SelfMediaPlatformCapabilityContract> contractByPlatform = platformRouter.contracts().stream()
                .collect(Collectors.toMap(SelfMediaPlatformCapabilityContract::platform, Function.identity(), (first, ignored) -> first));

        List<AccountAuthHealthOverviewVO.RiskItem> result = new ArrayList<>();
        for (SelfMediaAccount account : accounts) {
            BrandContext brandContext = brandContexts.get(account.getBrandId());
            Expiry expiry = selfMediaExpiry(account, credentialByAccountId.get(account.getId()), contractByPlatform.get(account.getPlatform()));
            Risk risk;
            if ("COOKIE".equalsIgnoreCase(account.getAuthMode())) {
                SelfMediaCookieCredential credential = credentialByAccountId.get(account.getId());
                var policy = authHealthPolicyService.findPolicy(account.getPlatform());
                LocalDateTime declaredExpiry = credential != null && Set.of("cookie_expires", "manual")
                        .contains(String.valueOf(credential.getExpirySource())) ? credential.getExpiresAt() : null;
                var evaluated = authRiskEvaluator.evaluate(new SelfMediaAuthRiskEvaluator.Input(policy, now, true,
                        credential != null, account.getLastLoginVerifiedAt(), credential == null ? null : credential.getCapturedAt(),
                        declaredExpiry, account.getCreatedAt()));
                expiry = new Expiry(evaluated.recommendedReverifyAt(), evaluated.recommendedReverifySource(),
                        "credential_missing".equals(evaluated.riskStatus()), "unknown".equals(evaluated.riskStatus()));
                risk = new Risk(evaluated.riskStatus(),
                        "reverify_overdue".equals(evaluated.riskStatus()) ? "warn"
                                : "credential_missing".equals(evaluated.riskStatus()) ? "warn" : "info",
                        evaluated.recommendedReverifyAt() == null ? null
                                : ChronoUnit.DAYS.between(now.toLocalDate(), evaluated.recommendedReverifyAt().toLocalDate()));
            } else {
                risk = risk(expiry.expiresAt(), expiry.missing(), expiry.unknown(), Math.max(selfMediaExpiringDays, 1), now);
            }
            result.add(new AccountAuthHealthOverviewVO.RiskItem(
                    "self_media",
                    account.getId(),
                    String.valueOf(account.getId()),
                    account.getAccountName(),
                    account.getPlatform(),
                    platformLabel(account.getPlatform(), contractByPlatform.get(account.getPlatform())),
                    account.getBrandId(),
                    brandContext == null ? null : brandContext.brandName(),
                    brandContext == null ? null : brandContext.companyName(),
                    brandContext == null ? null : brandContext.ownerUserId(),
                    brandContext == null ? null : brandContext.ownerName(),
                    risk.status(),
                    risk.severity(),
                    expiry.expiresAt(),
                    risk.daysUntilExpiry(),
                    expiry.source(),
                    expirySourceLabel(expiry.source()),
                    "/admin/brands/" + account.getBrandId() + "?tab=self-media&accountId=" + account.getId(),
                    risk.actionLabel("重新登录"),
                    selfMediaActionHint(risk.status(), expiry.source())
            ));
        }
        return result;
    }

    private Expiry selfMediaExpiry(SelfMediaAccount account,
                                   SelfMediaCookieCredential credential,
                                   SelfMediaPlatformCapabilityContract contract) {
        boolean officialApi = contract != null && contract.publishChannel() == SelfMediaPlatformPublishChannel.OFFICIAL_API;
        if (officialApi) {
            if (!StringUtils.hasText(account.getRefreshTokenCipher())) {
                return Expiry.missing("official_api");
            }
            return new Expiry(account.getRefreshTokenExpiresAt(), "official_api", false, account.getRefreshTokenExpiresAt() == null);
        }
        if (credential == null) {
            return Expiry.missing("platform_policy");
        }
        LocalDateTime expiresAt = credential.getExpiresAt();
        String source = StringUtils.hasText(credential.getExpirySource()) ? credential.getExpirySource() : "platform_policy";
        if (expiresAt == null && credential.getCapturedAt() != null) {
            expiresAt = credential.getCapturedAt().plusDays(cookieValidDays(account.getPlatform()));
            source = "platform_policy";
        }
        return new Expiry(expiresAt, source, false, expiresAt == null);
    }

    private List<AccountAuthHealthOverviewVO.RiskItem> forumItems(LocalDateTime now) {
        List<PublishSite> sites = publishSiteMapper.selectList(new LambdaQueryWrapper<PublishSite>()
                .eq(PublishSite::getStatus, "active")
                .in(PublishSite::getIntegrationMethod, FORUM_METHODS)
                .orderByAsc(PublishSite::getId));
        List<AccountAuthHealthOverviewVO.RiskItem> result = new ArrayList<>();
        for (PublishSite site : sites) {
            List<ForumCredentialHealth> accounts = forumCredentialHealth(site);
            if (accounts.isEmpty()) {
                Risk risk = new Risk("missing", "high", null);
                result.add(forumItem(site, "site", "论坛 Cookie 缺失", risk, null, "default", now));
                continue;
            }
            for (ForumCredentialHealth account : accounts) {
                Risk risk = risk(account.expiresAt(), false, account.expiresAt() == null, Math.max(forumExpiringDays, 1), now);
                result.add(forumItem(site, account.accountKey(), account.displayName(), risk, account.expiresAt(), account.expirySource(), now));
            }
        }
        return result;
    }

    private AccountAuthHealthOverviewVO.RiskItem forumItem(PublishSite site,
                                                           String accountKey,
                                                           String displayName,
                                                           Risk risk,
                                                           LocalDateTime expiresAt,
                                                           String expirySource,
                                                           LocalDateTime now) {
        Long days = expiresAt == null ? null : ChronoUnit.DAYS.between(now.toLocalDate(), expiresAt.toLocalDate());
        return new AccountAuthHealthOverviewVO.RiskItem(
                "forum",
                site.getId(),
                accountKey,
                displayName,
                site.getIntegrationMethod(),
                "论坛",
                null,
                null,
                site.getSiteName(),
                null,
                "manager",
                risk.status(),
                risk.severity(),
                expiresAt,
                days,
                expirySource,
                expirySourceLabel(expirySource),
                "/admin/settings/publish-sites?siteId=" + site.getId(),
                risk.actionLabel("更新 Cookie"),
                forumActionHint(risk.status())
        );
    }

    private List<ForumCredentialHealth> forumCredentialHealth(PublishSite site) {
        String raw = platformCredentialService.resolveCredential(site.getCredentialRef(), site.getApiCredentialEncrypted());
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            JSONObject root = JSONUtil.parseObj(raw);
            Object accounts = root.get("accounts");
            List<ForumCredentialHealth> result = new ArrayList<>();
            if (accounts instanceof JSONArray array) {
                for (Object item : array) {
                    if (item instanceof JSONObject account && isActiveForumCredential(account)) {
                        result.add(toForumCredentialHealth(account));
                    }
                }
            } else if (isActiveForumCredential(root)) {
                result.add(toForumCredentialHealth(root));
            }
            return result;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private ForumCredentialHealth toForumCredentialHealth(JSONObject account) {
        String username = account.getStr("username");
        String cookie = account.getStr("cookie");
        LocalDateTime capturedAt = parseDateTime(account.getStr("capturedAt"));
        LocalDateTime expiresAt = parseDateTime(account.getStr("expiresAt"));
        String source = account.getStr("expirySource", "manual");
        if (expiresAt == null && capturedAt != null) {
            expiresAt = capturedAt.plusDays(Math.max(forumDefaultCookieValidDays, 1));
            source = "default";
        }
        String accountKey = StringUtils.hasText(username)
                ? username.trim().toLowerCase(Locale.ROOT)
                : "cookie-" + Integer.toUnsignedString(String.valueOf(cookie).hashCode(), 16);
        return new ForumCredentialHealth(accountKey, StringUtils.hasText(username) ? username.trim() : "Cookie 账号", expiresAt, source);
    }

    private boolean isActiveForumCredential(JSONObject account) {
        String status = account.getStr("status", "active");
        if (StringUtils.hasText(status) && !"active".equalsIgnoreCase(status)) {
            return false;
        }
        return StringUtils.hasText(account.getStr("cookie"))
                || (StringUtils.hasText(account.getStr("username")) && StringUtils.hasText(account.getStr("password")));
    }

    private AccountAuthHealthOverviewVO.Summary summary(List<AccountAuthHealthOverviewVO.RiskItem> items,
                                                        List<AccountAuthHealthOverviewVO.AlertGroup> alertGroups) {
        int normal = 0;
        int expiring = 0;
        int expired = 0;
        int missing = 0;
        int unknown = 0;
        int high = 0;
        int due7 = 0;
        int due30 = 0;
        for (AccountAuthHealthOverviewVO.RiskItem item : items) {
            switch (String.valueOf(item.riskStatus())) {
                case "normal" -> normal++;
                case "expiring", "reverify_due_soon" -> expiring++;
                case "expired", "reverify_overdue" -> expired++;
                case "missing", "credential_missing" -> missing++;
                default -> unknown++;
            }
            if ("high".equals(item.severity()) || "critical".equals(item.severity())) {
                high++;
            }
            if (item.daysUntilExpiry() != null && item.daysUntilExpiry() >= 0 && item.daysUntilExpiry() <= 7) {
                due7++;
            }
            if (item.daysUntilExpiry() != null && item.daysUntilExpiry() >= 0 && item.daysUntilExpiry() <= 30) {
                due30++;
            }
        }
        return new AccountAuthHealthOverviewVO.Summary(
                items.size(),
                normal,
                expiring,
                expired,
                missing,
                unknown,
                alertGroups.stream().mapToInt(AccountAuthHealthOverviewVO.AlertGroup::count).sum(),
                high,
                due7,
                due30
        );
    }

    private List<AccountAuthHealthOverviewVO.AlertGroup> alertGroups() {
        List<SystemAlert> alerts = systemAlertMapper.selectList(new LambdaQueryWrapper<SystemAlert>()
                .in(SystemAlert::getAlertType, List.of("SELF_MEDIA_ACCOUNT_AUTH_HEALTH", "FORUM_COOKIE_AUTH_HEALTH"))
                .eq(SystemAlert::getIsResolved, false)
                .orderByDesc(SystemAlert::getCreatedAt));
        Map<String, MutableAlertGroup> groups = new LinkedHashMap<>();
        for (SystemAlert alert : alerts) {
            JSONObject context = parseContext(alert.getContextJson());
            String targetType = "FORUM_COOKIE_AUTH_HEALTH".equals(alert.getAlertType()) ? "forum" : "self_media";
            String issueCode = context.getStr("issueCode", alert.getAlertType());
            String groupKey = targetType + ":" + issueCode;
            MutableAlertGroup group = groups.computeIfAbsent(groupKey, ignored -> new MutableAlertGroup(groupKey, targetType, issueCode));
            group.accept(alert, context);
        }
        return groups.values().stream()
                .map(MutableAlertGroup::toVo)
                .sorted(Comparator
                        .comparingInt((AccountAuthHealthOverviewVO.AlertGroup group) -> severityRank(group.severity()))
                        .thenComparing(AccountAuthHealthOverviewVO.AlertGroup::latestCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private List<AccountAuthHealthOverviewVO.TrendBucket> trendBuckets(List<AccountAuthHealthOverviewVO.RiskItem> items,
                                                                       LocalDate start,
                                                                       int days) {
        Map<LocalDate, int[]> counts = new LinkedHashMap<>();
        for (int i = 0; i < days; i++) {
            counts.put(start.plusDays(i), new int[2]);
        }
        for (AccountAuthHealthOverviewVO.RiskItem item : items) {
            if (item.expiresAt() == null) {
                continue;
            }
            LocalDate date = item.expiresAt().toLocalDate();
            int[] bucket = counts.get(date);
            if (bucket == null) {
                continue;
            }
            if ("forum".equals(item.targetType())) {
                bucket[1]++;
            } else {
                bucket[0]++;
            }
        }
        return counts.entrySet().stream()
                .map(entry -> new AccountAuthHealthOverviewVO.TrendBucket(entry.getKey(), entry.getValue()[0], entry.getValue()[1], entry.getValue()[0] + entry.getValue()[1]))
                .toList();
    }

    private Map<Long, BrandContext> brandContexts(List<Long> brandIds) {
        if (brandIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Brand> brands = brandMapper.selectBatchIds(brandIds).stream()
                .collect(Collectors.toMap(Brand::getId, Function.identity(), (first, ignored) -> first));
        List<Long> companyIds = brands.values().stream().map(Brand::getCompanyId).filter(Objects::nonNull).distinct().toList();
        Map<Long, Company> companies = companyIds.isEmpty()
                ? Map.of()
                : companyMapper.selectBatchIds(companyIds).stream()
                .filter(company -> company.getDeletedAt() == null)
                .collect(Collectors.toMap(Company::getId, Function.identity(), (first, ignored) -> first));
        Map<Long, Long> operatorIdsByBrand = assignedOperators(brandIds);
        List<Long> userIds = new ArrayList<>();
        userIds.addAll(operatorIdsByBrand.values());
        companies.values().stream().map(Company::getOwnerId).filter(Objects::nonNull).forEach(userIds::add);
        Map<Long, SysUser> users = userIds.isEmpty()
                ? Map.of()
                : sysUserMapper.selectBatchIds(userIds.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity(), (first, ignored) -> first));

        Map<Long, BrandContext> result = new LinkedHashMap<>();
        for (Brand brand : brands.values()) {
            Company company = companies.get(brand.getCompanyId());
            Long ownerUserId = operatorIdsByBrand.getOrDefault(brand.getId(), company == null ? null : company.getOwnerId());
            SysUser owner = ownerUserId == null ? null : users.get(ownerUserId);
            result.put(brand.getId(), new BrandContext(
                    brand.getBrandName(),
                    company == null ? null : company.getCompanyName(),
                    ownerUserId,
                    owner == null ? null : owner.getDisplayName()
            ));
        }
        return result;
    }

    private Map<Long, Long> assignedOperators(List<Long> brandIds) {
        if (brandIds.isEmpty()) {
            return Map.of();
        }
        return brandOperatorAssignmentMapper.selectList(new LambdaQueryWrapper<BrandOperatorAssignment>()
                        .in(BrandOperatorAssignment::getBrandId, brandIds)
                        .eq(BrandOperatorAssignment::getStatus, "active")
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

    private Risk risk(LocalDateTime expiresAt, boolean missing, boolean unknown, int expiringDays, LocalDateTime now) {
        if (missing) {
            return new Risk("missing", "high", null);
        }
        if (unknown) {
            return new Risk("unknown", "warn", null);
        }
        long days = ChronoUnit.DAYS.between(now.toLocalDate(), expiresAt.toLocalDate());
        if (!expiresAt.isAfter(now)) {
            return new Risk("expired", "high", days);
        }
        if (expiresAt.isBefore(now.plusDays(expiringDays))) {
            return new Risk("expiring", days <= 3 ? "high" : "warn", days);
        }
        return new Risk("normal", "info", days);
    }

    private int cookieValidDays(String platform) {
        Map<String, Integer> configured = parsePlatformDays(cookiePlatformValidDays);
        return configured.getOrDefault(String.valueOf(platform).toLowerCase(Locale.ROOT), Math.max(defaultSelfMediaCookieValidDays, 1));
    }

    private Map<String, Integer> parsePlatformDays(String config) {
        if (!StringUtils.hasText(config)) {
            return Map.of();
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String item : config.split(",")) {
            String[] parts = item.split(":");
            if (parts.length != 2) {
                continue;
            }
            try {
                result.put(parts[0].trim().toLowerCase(Locale.ROOT), Math.max(Integer.parseInt(parts[1].trim()), 1));
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    private JSONObject parseContext(String contextJson) {
        if (!StringUtils.hasText(contextJson)) {
            return new JSONObject();
        }
        try {
            return JSONUtil.parseObj(contextJson);
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private LocalDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String platformLabel(String platform, SelfMediaPlatformCapabilityContract contract) {
        if (contract != null && StringUtils.hasText(contract.displayName())) {
            return contract.displayName();
        }
        return switch (String.valueOf(platform)) {
            case "toutiao" -> "今日头条";
            case "baijiahao" -> "百家号";
            case "zhihu" -> "知乎";
            case "xiaohongshu" -> "小红书";
            case "douyin" -> "抖音";
            case "wechat_mp" -> "微信公众号";
            default -> StringUtils.hasText(platform) ? platform : "-";
        };
    }

    private String expirySourceLabel(String source) {
        return switch (String.valueOf(source)) {
            case "cookie_expires" -> "Cookie 原始到期";
            case "official_api" -> "官方授权";
            case "platform_policy" -> "平台策略估算";
            case "manual" -> "手工维护";
            case "default" -> "系统默认";
            case "last_verification" -> "最近登录验证";
            case "credential_capture" -> "凭据采集时间";
            case "credential_reference" -> "平台参考周期";
            case "cookie_declared_expiry" -> "Cookie 声明时间";
            case "account_binding" -> "账号绑定时间";
            default -> "未记录来源";
        };
    }

    private String selfMediaActionHint(String status, String source) {
        if ("missing".equals(status)) {
            return "账号缺少有效 Cookie 凭据，请打开对应浏览器环境完成登录采集。";
        }
        if ("credential_missing".equals(status)) {
            return "尚无可信登录健康记录；可同步已登录环境，或在品牌详情中验证登录状态。";
        }
        if ("reverify_overdue".equals(status)) {
            return "已超过建议复验时间，请在品牌详情中验证当前登录状态。";
        }
        if ("reverify_due_soon".equals(status)) {
            return "即将需要复验，可提前在品牌详情中确认当前登录状态。";
        }
        if ("monitoring_disabled".equals(status)) {
            return "该平台未启用授权时间风险监控。";
        }
        if ("expired".equals(status)) {
            return "已超过旧版建议复验时间，请在品牌详情中验证当前登录状态。";
        }
        if ("expiring".equals(status)) {
            return "旧版估算显示即将需要复验，可提前确认当前登录状态。";
        }
        if ("unknown".equals(status)) {
            return "暂时无法计算建议复验时间，请检查平台策略及最近验证记录。";
        }
        return "当前授权可用，来源：" + expirySourceLabel(source) + "。";
    }

    private String forumActionHint(String status) {
        if ("missing".equals(status)) {
            return "站点缺少论坛 Cookie 或账号凭据，请在发布站点配置中更新。";
        }
        if ("expired".equals(status)) {
            return "论坛 Cookie 已过期，更新前可能导致分发失败。";
        }
        if ("expiring".equals(status)) {
            return "论坛 Cookie 临近到期，仅告警提醒，不自动停用站点。";
        }
        if ("unknown".equals(status)) {
            return "凭据中没有到期时间，请重新保存凭据让系统补齐默认有效期。";
        }
        return "当前论坛 Cookie 风险正常。";
    }

    private int riskRank(String riskStatus) {
        return switch (String.valueOf(riskStatus)) {
            case "expired" -> 0;
            case "reverify_overdue" -> 0;
            case "missing", "credential_missing" -> 1;
            case "expiring", "reverify_due_soon" -> 2;
            case "unknown" -> 3;
            default -> 4;
        };
    }

    private int severityRank(String severity) {
        return switch (String.valueOf(severity)) {
            case "critical" -> 0;
            case "high", "error" -> 1;
            case "warn" -> 2;
            default -> 3;
        };
    }

    private record BrandContext(String brandName, String companyName, Long ownerUserId, String ownerName) {
    }

    private record Expiry(LocalDateTime expiresAt, String source, boolean missing, boolean unknown) {
        private static Expiry missing(String source) {
            return new Expiry(null, source, true, false);
        }
    }

    private record Risk(String status, String severity, Long daysUntilExpiry) {
        private String actionLabel(String verb) {
            return switch (status) {
                case "expired" -> "立即" + verb;
                case "reverify_overdue" -> "立即复验";
                case "missing" -> "补充凭据";
                case "credential_missing" -> "验证登录";
                case "expiring", "reverify_due_soon" -> "提前复验";
                case "unknown" -> "补齐到期";
                default -> "查看详情";
            };
        }
    }

    private record ForumCredentialHealth(String accountKey, String displayName, LocalDateTime expiresAt, String expirySource) {
    }

    private class MutableAlertGroup {
        private final String groupKey;
        private final String targetType;
        private final String issueCode;
        private int count;
        private String severity = "info";
        private LocalDateTime latestCreatedAt;
        private String sampleMessage;
        private String actionRoute;

        private MutableAlertGroup(String groupKey, String targetType, String issueCode) {
            this.groupKey = groupKey;
            this.targetType = targetType;
            this.issueCode = issueCode;
        }

        private void accept(SystemAlert alert, JSONObject context) {
            count++;
            if (severityRank(alert.getSeverity()) < severityRank(severity)) {
                severity = alert.getSeverity();
            }
            if (latestCreatedAt == null || (alert.getCreatedAt() != null && alert.getCreatedAt().isAfter(latestCreatedAt))) {
                latestCreatedAt = alert.getCreatedAt();
                sampleMessage = alert.getMessage();
                actionRoute = context.getStr("route");
            }
        }

        private AccountAuthHealthOverviewVO.AlertGroup toVo() {
            return new AccountAuthHealthOverviewVO.AlertGroup(
                    groupKey,
                    targetType,
                    issueCode,
                    severity,
                    count,
                    latestCreatedAt,
                    alertGroupTitle(targetType, issueCode),
                    sampleMessage,
                    actionRoute,
                    "forum".equals(targetType) ? "处理论坛凭据" : "处理自媒体账号"
            );
        }
    }

    private String alertGroupTitle(String targetType, String issueCode) {
        return switch (issueCode) {
            case "COOKIE_CREDENTIAL_EXPIRED" -> "自媒体登录已过期";
            case "COOKIE_CREDENTIAL_EXPIRING" -> "自媒体登录临近到期";
            case "COOKIE_CREDENTIAL_MISSING" -> "自媒体 Cookie 缺失";
            case "OFFICIAL_CREDENTIAL_EXPIRED" -> "官方授权已过期";
            case "OFFICIAL_CREDENTIAL_EXPIRING" -> "官方授权临近到期";
            case "ACCOUNT_REVERIFY_OVERDUE" -> "自媒体账号复验超期";
            case "ACCOUNT_REVERIFY_DUE_SOON" -> "自媒体账号即将需要复验";
            case "FORUM_COOKIE_EXPIRED" -> "论坛 Cookie 已过期";
            case "FORUM_COOKIE_EXPIRING" -> "论坛 Cookie 临近到期";
            case "FORUM_COOKIE_MISSING" -> "论坛 Cookie 缺失";
            case "FORUM_COOKIE_EXPIRY_UNKNOWN" -> "论坛 Cookie 到期未知";
            default -> ("forum".equals(targetType) ? "论坛账号风险" : "自媒体账号风险");
        };
    }
}
