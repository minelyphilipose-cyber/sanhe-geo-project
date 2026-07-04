package com.huanjing.geo.module.content.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.huanjing.geo.module.system.entity.PublishSite;
import com.huanjing.geo.module.system.mapper.PublishSiteMapper;
import com.huanjing.geo.module.system.service.PlatformCredentialService;
import com.huanjing.geo.module.system.service.SystemAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ForumCookieHealthAlertService {

    private static final String ALERT_TYPE = "FORUM_COOKIE_AUTH_HEALTH";
    private static final String SOURCE = "forum_cookie_health";
    private static final String RECIPIENT_ROLE = "manager";
    private static final Set<String> FORUM_METHODS = Set.of("forum_playwright", "discuz_http");
    private final PublishSiteMapper publishSiteMapper;
    private final PlatformCredentialService platformCredentialService;
    private final SystemAlertService systemAlertService;

    @Value("${geo.forum-cookie-health.scan-limit:500}")
    private int scanLimit;

    @Value("${geo.forum-cookie-health.credential-expiring-days:7}")
    private int credentialExpiringDays;

    @Value("${geo.forum-cookie-health.default-cookie-valid-days:30}")
    private int defaultCookieValidDays;

    @Transactional
    public int scanOnce() {
        List<PublishSite> sites = publishSiteMapper.selectList(new LambdaQueryWrapper<PublishSite>()
                .eq(PublishSite::getStatus, "active")
                .in(PublishSite::getIntegrationMethod, FORUM_METHODS)
                .orderByAsc(PublishSite::getId)
                .last("LIMIT " + Math.max(scanLimit, 1)));
        LocalDateTime now = LocalDateTime.now();
        int changed = 0;
        for (PublishSite site : sites) {
            List<ForumAccountHealth> accounts = parseAccounts(site);
            List<PendingForumIssue> pendingIssues = new ArrayList<>();
            int checkedForSite = 0;
            if (accounts.isEmpty()) {
                checkedForSite = 1;
                pendingIssues.add(new PendingForumIssue("site",
                        issue("FORUM_COOKIE_MISSING", "high", "论坛登录 Cookie 或账号凭据缺失", null, null)));
            } else {
                for (ForumAccountHealth account : accounts) {
                    checkedForSite++;
                    HealthIssue issue = detectIssue(account, now);
                    if (issue != null) {
                        pendingIssues.add(new PendingForumIssue(account.accountKey(), issue));
                    }
                }
            }
            Set<String> activeDedupeKeys = new LinkedHashSet<>();
            for (PendingForumIssue pendingIssue : pendingIssues) {
                activeDedupeKeys.add(dedupePrefix(site.getId(), pendingIssue.accountKey()) + pendingIssue.issue().code());
            }
            systemAlertService.resolveOpenByDedupeKeyPrefixExcept(siteDedupePrefix(site.getId()), activeDedupeKeys, null);
            for (PendingForumIssue pendingIssue : pendingIssues) {
                createIssueAlert(site, pendingIssue.accountKey(), pendingIssue.issue());
            }
            changed += checkedForSite;
        }
        return changed;
    }

    private List<ForumAccountHealth> parseAccounts(PublishSite site) {
        String raw = platformCredentialService.resolveCredential(site.getCredentialRef(), site.getApiCredentialEncrypted());
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            JSONObject root = JSONUtil.parseObj(raw);
            List<ForumAccountHealth> accounts = new ArrayList<>();
            Object accountArray = root.get("accounts");
            if (accountArray instanceof JSONArray array) {
                for (Object item : array) {
                    if (item instanceof JSONObject account && isActiveUsable(account)) {
                        accounts.add(toHealth(account));
                    }
                }
            }
            if (accounts.isEmpty() && isActiveUsable(root)) {
                accounts.add(toHealth(root));
            }
            return accounts;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private boolean isActiveUsable(JSONObject account) {
        String status = account.getStr("status", "active");
        if (StringUtils.hasText(status) && !"active".equalsIgnoreCase(status)) {
            return false;
        }
        return StringUtils.hasText(account.getStr("cookie"))
                || (StringUtils.hasText(account.getStr("username")) && StringUtils.hasText(account.getStr("password")));
    }

    private ForumAccountHealth toHealth(JSONObject account) {
        String username = account.getStr("username");
        String cookie = account.getStr("cookie");
        LocalDateTime capturedAt = parseDateTime(account.getStr("capturedAt"));
        LocalDateTime expiresAt = parseDateTime(account.getStr("expiresAt"));
        if (expiresAt == null && capturedAt != null) {
            expiresAt = capturedAt.plusDays(Math.max(defaultCookieValidDays, 1));
        }
        return new ForumAccountHealth(accountKey(username, cookie), displayName(username), capturedAt, expiresAt);
    }

    private HealthIssue detectIssue(ForumAccountHealth account, LocalDateTime now) {
        if (account.expiresAt() == null) {
            return issue("FORUM_COOKIE_EXPIRY_UNKNOWN", "warn", "论坛 Cookie 到期时间缺失，请更新登录凭据", null, null);
        }
        long daysUntilExpiry = ChronoUnit.DAYS.between(now.toLocalDate(), account.expiresAt().toLocalDate());
        if (!account.expiresAt().isAfter(now)) {
            return issue("FORUM_COOKIE_EXPIRED", "high", expiredMessage(daysUntilExpiry), account.expiresAt(), daysUntilExpiry);
        }
        if (account.expiresAt().isBefore(now.plusDays(Math.max(credentialExpiringDays, 1)))) {
            return issue("FORUM_COOKIE_EXPIRING", expiringSeverity(daysUntilExpiry), expiringMessage(daysUntilExpiry),
                    account.expiresAt(), daysUntilExpiry);
        }
        return null;
    }

    private void createIssueAlert(PublishSite site, String accountKey, HealthIssue issue) {
        String prefix = dedupePrefix(site.getId(), accountKey);
        systemAlertService.createRecipientAlert(
                ALERT_TYPE,
                issue.severity(),
                SOURCE,
                message(site, issue),
                context(site, accountKey, issue),
                null,
                RECIPIENT_ROLE,
                prefix + issue.code()
        );
        if ("FORUM_COOKIE_EXPIRED".equals(issue.code())) {
            publishSiteMapper.update(null, new LambdaUpdateWrapper<PublishSite>()
                    .eq(PublishSite::getId, site.getId())
                    .set(PublishSite::getCurrentHealthStatus, "degraded")
                    .set(PublishSite::getLastFailureAt, LocalDateTime.now()));
        }
    }

    private String message(PublishSite site, HealthIssue issue) {
        return "论坛站点「" + displaySiteName(site) + "」" + issue.message();
    }

    private Map<String, Object> context(PublishSite site, String accountKey, HealthIssue issue) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("publishSiteId", site.getId());
        context.put("siteName", site.getSiteName());
        context.put("siteCode", site.getSiteCode());
        context.put("integrationMethod", site.getIntegrationMethod());
        context.put("accountKey", accountKey);
        context.put("issueCode", issue.code());
        context.put("expiresAt", issue.expiresAt());
        context.put("daysUntilExpiry", issue.daysUntilExpiry());
        context.put("route", "/admin/settings/publish-sites?siteId=" + site.getId());
        return context;
    }

    private String dedupePrefix(Long siteId, String accountKey) {
        return siteDedupePrefix(siteId) + accountKey + ":";
    }

    private String siteDedupePrefix(Long siteId) {
        return "forum_cookie_auth:" + siteId + ":";
    }

    private String accountKey(String username, String cookie) {
        if (StringUtils.hasText(username)) {
            return username.trim().toLowerCase(Locale.ROOT);
        }
        if (StringUtils.hasText(cookie)) {
            return "cookie-" + Integer.toHexString(Math.abs(cookie.hashCode()));
        }
        return "site";
    }

    private String displayName(String username) {
        return StringUtils.hasText(username) ? username.trim() : "cookie";
    }

    private String displaySiteName(PublishSite site) {
        if (StringUtils.hasText(site.getSiteName())) {
            return site.getSiteName().trim();
        }
        if (StringUtils.hasText(site.getSiteCode())) {
            return site.getSiteCode().trim();
        }
        return String.valueOf(site.getId());
    }

    private String expiringSeverity(long daysUntilExpiry) {
        return daysUntilExpiry <= 3 ? "high" : "warn";
    }

    private String expiringMessage(long daysUntilExpiry) {
        if (daysUntilExpiry <= 0) {
            return "论坛 Cookie 今天到期，请当天完成更新";
        }
        return "论坛 Cookie 还剩 " + daysUntilExpiry + " 天到期，请提前更新";
    }

    private String expiredMessage(long daysUntilExpiry) {
        long overdueDays = Math.abs(daysUntilExpiry);
        if (overdueDays <= 0) {
            return "论坛 Cookie 已到期，请立即更新";
        }
        return "论坛 Cookie 已过期 " + overdueDays + " 天，请立即更新";
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

    private HealthIssue issue(String code, String severity, String message, LocalDateTime expiresAt, Long daysUntilExpiry) {
        return new HealthIssue(code, severity, message, expiresAt, daysUntilExpiry);
    }

    private record ForumAccountHealth(String accountKey, String displayName, LocalDateTime capturedAt,
                                      LocalDateTime expiresAt) {
    }

    private record PendingForumIssue(String accountKey, HealthIssue issue) {
    }

    private record HealthIssue(String code, String severity, String message, LocalDateTime expiresAt,
                               Long daysUntilExpiry) {
    }
}
