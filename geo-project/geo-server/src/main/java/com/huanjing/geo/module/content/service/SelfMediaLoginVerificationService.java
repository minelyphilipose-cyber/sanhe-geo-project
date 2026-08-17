package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.content.constant.BrowserEnvironmentConstants;
import com.huanjing.geo.module.content.dto.BrowserEnvironmentLoginStatusRequest;
import com.huanjing.geo.module.content.dto.SelfMediaLoginVerificationVO;
import com.huanjing.geo.module.content.entity.BrowserEnvironment;
import com.huanjing.geo.module.content.entity.BrowserEnvironmentAccount;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.entity.SelfMediaLoginVerification;
import com.huanjing.geo.module.content.event.SelfMediaAuthHealthChangedEvent;
import com.huanjing.geo.module.content.mapper.BrowserEnvironmentAccountMapper;
import com.huanjing.geo.module.content.mapper.BrowserEnvironmentMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaLoginVerificationMapper;
import com.huanjing.geo.module.customer.access.BrandAccessAction;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import com.huanjing.geo.module.system.service.SystemAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SelfMediaLoginVerificationService {
    private static final List<String> TECHNICAL_RESULT_CODES = List.of(
            "IDENTITY_UNREADABLE", "MULTIPLE_IDENTITIES", "DOM_CHANGED", "SCRIPT_TIMEOUT",
            "ENVIRONMENT_MISMATCH", "PLATFORM_MISMATCH", "ACCOUNT_NAME_MISMATCH");
    private static final List<String> OPEN_STATUSES = List.of("pending", "running");
    private static final int TTL_SECONDS = 300;
    public static final String METHOD_SYSTEM_TRIGGERED = "system_triggered_identity_check";
    public static final String METHOD_EXTENSION_PASSIVE = "extension_passive_identity_report";
    public static final String METHOD_BINDING_INVALIDATED = "browser_binding_invalidated";
    public static final String METHOD_ACCOUNT_IDENTITY_CHANGED = "account_identity_changed";
    public static final String METHOD_PUBLISH_TASK_FAILURE = "publish_task_identity_failure";

    private final SelfMediaLoginVerificationMapper verificationMapper;
    private final SelfMediaAccountMapper accountMapper;
    private final BrowserEnvironmentAccountMapper environmentAccountMapper;
    private final BrowserEnvironmentMapper environmentMapper;
    private final BrandAccessService brandAccessService;
    private final CurrentUserService currentUserService;
    private final SelfMediaAuthHealthPolicyService policyService;
    private final SystemAlertService systemAlertService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public SelfMediaLoginVerificationVO create(Long brandId, Long accountId) {
        SysUser operator = currentUserService.requireCurrentUser();
        requireAnyPermission("self-media.auth-health.verify", "company.update", "brand.update");
        brandAccessService.requireBrandAccess(brandId, operator.getId(), BrandAccessAction.MANAGE);
        SelfMediaAccount account = requireAccount(accountId);
        if (!Objects.equals(brandId, account.getBrandId())) throw new BizException(404, "未找到品牌下的自媒体账号");
        if (!StringUtils.hasText(account.getAccountName())) throw new BizException(400, "请先维护账号名称后再验证登录状态");
        BrowserEnvironmentAccount binding = environmentAccountMapper.selectActiveBySelfMediaAccountId(accountId);
        if (binding == null) throw new BizException(400, "请先绑定指纹浏览器环境");
        BrowserEnvironment environment = environmentMapper.selectById(binding.getBrowserEnvironmentId());
        if (environment == null || !"active".equalsIgnoreCase(environment.getStatus())) {
            throw new BizException(400, "绑定的指纹浏览器环境未启用");
        }
        LocalDateTime now = LocalDateTime.now();
        expireOpen(accountId, now);
        SelfMediaLoginVerification existing = verificationMapper.selectOne(new LambdaQueryWrapper<SelfMediaLoginVerification>()
                .eq(SelfMediaLoginVerification::getSelfMediaAccountId, accountId)
                .in(SelfMediaLoginVerification::getStatus, OPEN_STATUSES)
                .gt(SelfMediaLoginVerification::getExpiresAt, now)
                .orderByDesc(SelfMediaLoginVerification::getId)
                .last("LIMIT 1"));
        if (existing != null) return SelfMediaLoginVerificationVO.from(existing);

        SelfMediaLoginVerification row = new SelfMediaLoginVerification();
        row.setBrandId(brandId);
        row.setSelfMediaAccountId(accountId);
        row.setBrowserEnvironmentId(binding.getBrowserEnvironmentId());
        row.setBrowserEnvironmentAccountId(binding.getId());
        row.setPlatform(canonicalPlatform(account.getPlatform()));
        row.setExpectedAccountName(account.getAccountName().trim());
        row.setExpectedPlatformAccountId(account.getPlatformAccountId());
        row.setStatus("pending");
        row.setRequestedBy(operator.getId());
        row.setRequestedAt(now);
        row.setExpiresAt(now.plusSeconds(TTL_SECONDS));
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        verificationMapper.insert(row);
        return SelfMediaLoginVerificationVO.from(row);
    }

    public SelfMediaLoginVerificationVO get(Long brandId, Long accountId, Long verificationId) {
        SysUser operator = currentUserService.requireCurrentUser();
        requireAnyPermission("self-media.auth-health.read", "company.read");
        brandAccessService.requireBrandAccess(brandId, operator.getId(), BrandAccessAction.READ);
        SelfMediaLoginVerification row = requireVerification(verificationId);
        if (!Objects.equals(brandId, row.getBrandId()) || !Objects.equals(accountId, row.getSelfMediaAccountId())) {
            throw new BizException(404, "登录验证任务不存在");
        }
        if (OPEN_STATUSES.contains(row.getStatus()) && !row.getExpiresAt().isAfter(LocalDateTime.now())) {
            markTimeout(row);
        }
        return SelfMediaLoginVerificationVO.from(row);
    }

    @Transactional
    public boolean completeFromLoginReport(Long verificationId,
                                           BrowserEnvironmentAccount binding,
                                           SelfMediaAccount account,
                                           BrowserEnvironmentLoginStatusRequest request,
                                           String reportedStatus) {
        if (verificationId == null) return false;
        SelfMediaLoginVerification row = requireVerification(verificationId);
        LocalDateTime now = LocalDateTime.now();
        if (!OPEN_STATUSES.contains(row.getStatus())) return false;
        if (!row.getExpiresAt().isAfter(now)) {
            markTimeout(row);
            return false;
        }
        if (!Objects.equals(row.getBrowserEnvironmentAccountId(), binding.getId())
                || !Objects.equals(row.getSelfMediaAccountId(), account.getId())) {
            finishInconclusive(row, null, "ENVIRONMENT_MISMATCH", "验证结果不是来自账号绑定的指纹浏览器环境", request, now);
            return false;
        }
        if (!Objects.equals(canonicalPlatform(row.getPlatform()), canonicalPlatform(request.platform()))) {
            finishInconclusive(row, null, "PLATFORM_MISMATCH", "验证结果平台与目标平台不一致", request, now);
            return false;
        }
        if (!BrowserEnvironmentConstants.LOGIN_LOGGED_IN.equals(request.loginStatus())) {
            String errorCode = trimToNull(request.errorCode());
            if ("MULTIPLE_IDENTITIES".equals(errorCode)) {
                finishInconclusive(row, account, "MULTIPLE_IDENTITIES", "读取到多个平台账号身份，需要运营内部核对", request, now);
            } else if ("IDENTITY_UNREADABLE".equals(errorCode)) {
                finishInconclusive(row, account, "IDENTITY_UNREADABLE", "未读取到当前平台账号名称，可能是页面结构变化", request, now);
            } else if (TECHNICAL_RESULT_CODES.contains(errorCode)) {
                finishInconclusive(row, account, errorCode, "平台页面检测未形成可信登录结论", request, now);
            } else if (isExplicitLoginPageFailure(errorCode, request.currentUrl())) {
                finishFailure(row, account, "LOGIN_PAGE_CONFIRMED", "平台明确进入登录页，需运营确认后再联系客户", request, now);
            } else {
                finishInconclusive(row, account, "LOGIN_STATE_UNCONFIRMED", "当前页面未形成可信登录结论，需要运营内部复核", request, now);
            }
            return false;
        }
        String actualName = trimToNull(request.actualAccountName());
        if (actualName == null) {
            finishInconclusive(row, account, "IDENTITY_UNREADABLE", "未读取到当前平台账号名称，可能是页面结构变化", request, now);
            return false;
        }
        if (!normalizeName(row.getExpectedAccountName()).equals(normalizeName(actualName))) {
            finishInconclusive(row, account, "ACCOUNT_NAME_MISMATCH",
                    "读取账号为“" + actualName + "”，与系统记录“" + row.getExpectedAccountName() + "”不同，需要运营核对",
                    request, now);
            return false;
        }
        row.setStatus("succeeded");
        row.setResultCode("SUCCESS");
        row.setResultMessage("登录状态验证成功");
        applyReport(row, request, now);
        verificationMapper.updateById(row);

        applySuccessfulHealthFact(account, null, METHOD_SYSTEM_TRIGGERED, now);
        return true;
    }

    @Transactional
    public boolean recordTrustedPassiveHealthReport(BrowserEnvironmentAccount binding,
                                                    SelfMediaAccount account,
                                                    BrowserEnvironmentLoginStatusRequest request) {
        if (binding == null || account == null || request == null || request.loginVerificationId() != null
                || !Objects.equals(binding.getSelfMediaAccountId(), account.getId())) {
            return false;
        }
        if (!BrowserEnvironmentConstants.LOGIN_LOGGED_IN.equals(request.loginStatus())) {
            return false;
        }
        String actualName = trimToNull(request.actualAccountName());
        if (!StringUtils.hasText(account.getAccountName())) {
            return false;
        }
        if (actualName == null) {
            return false;
        }
        if (!normalizeName(account.getAccountName()).equals(normalizeName(actualName))) {
            return false;
        }
        applySuccessfulHealthFact(account, null, METHOD_EXTENSION_PASSIVE, LocalDateTime.now());
        return true;
    }

    public void invalidateAccountHealth(Long accountId, String method, String warning) {
        if (accountId == null) {
            return;
        }
        accountMapper.invalidateLoginHealth(
                accountId,
                LocalDateTime.now(),
                StringUtils.hasText(method) ? method.trim() : METHOD_BINDING_INVALIDATED,
                StringUtils.hasText(warning) ? warning.trim() : "账号登录绑定已发生变化，请重新验证登录状态"
        );
        systemAlertService.resolveOpenByDedupeKeyPrefix("self_media_auth:" + accountId + ":", null);
        publishHealthChanged(accountId);
    }

    public void recordPublishTaskLoginFailure(Long accountId, String message) {
        if (accountId == null) {
            return;
        }
        SelfMediaAccount account = accountMapper.selectById(accountId);
        if (account == null || account.getDeletedAt() != null) {
            return;
        }
        String warning = StringUtils.hasText(message)
                ? sanitize(message)
                : "发布任务检测到平台登录状态异常，请重新登录并验证";
        applyFailedHealthFact(account, warning, METHOD_PUBLISH_TASK_FAILURE, LocalDateTime.now());
    }

    private void applySuccessfulHealthFact(SelfMediaAccount account,
                                           String warning,
                                           String method,
                                           LocalDateTime verifiedAt) {
        account.setLastLoginVerifiedAt(verifiedAt);
        account.setLastLoginVerificationCheckedAt(verifiedAt);
        account.setLastLoginVerificationResult("success");
        account.setLastLoginVerificationMethod(method);
        account.setLastLoginVerificationWarning(warning);
        var policy = policyService.findPolicy(account.getPlatform());
        account.setRecommendedReverifyAt(policy == null || !Boolean.TRUE.equals(policy.getEnabled())
                ? null : verifiedAt.plusDays(Math.max(policy.getReverifyIntervalDays(), 1)));
        account.setUpdatedAt(verifiedAt);
        accountMapper.updateById(account);
        // updateById 默认忽略 null；成功事实必须显式清除历史阻塞，并允许关闭策略时清空建议复验时间。
        accountMapper.updateNullableLoginHealthFields(
                account.getId(), warning, account.getRecommendedReverifyAt());
        systemAlertService.resolveOpenByDedupeKeyPrefix("self_media_auth:" + account.getId() + ":", null);
    }

    private void finishFailure(SelfMediaLoginVerification row,
                               SelfMediaAccount account,
                               String code,
                               String message,
                               BrowserEnvironmentLoginStatusRequest request, LocalDateTime now) {
        row.setStatus("failed");
        row.setResultCode(code);
        row.setResultMessage(message);
        applyReport(row, request, now);
        verificationMapper.updateById(row);
        if (account != null) {
            applyFailedHealthFact(account, message, METHOD_SYSTEM_TRIGGERED, now);
        }
    }

    private void finishInconclusive(SelfMediaLoginVerification row,
                                    SelfMediaAccount account,
                                    String code,
                                    String message,
                                    BrowserEnvironmentLoginStatusRequest request,
                                    LocalDateTime now) {
        row.setStatus("inconclusive");
        row.setResultCode(code);
        row.setResultMessage(message);
        applyReport(row, request, now);
        verificationMapper.updateById(row);
        if (account != null) {
            applyInconclusiveHealthCheck(account, message, now);
        }
    }

    private void applyInconclusiveHealthCheck(SelfMediaAccount account,
                                              String message,
                                              LocalDateTime checkedAt) {
        account.setLastLoginVerificationCheckedAt(checkedAt);
        account.setLastLoginVerificationMethod(METHOD_SYSTEM_TRIGGERED);
        account.setLastLoginVerificationWarning(message);
        account.setUpdatedAt(checkedAt);
        accountMapper.updateById(account);
        accountMapper.updateNullableLoginHealthFields(
                account.getId(), message, account.getRecommendedReverifyAt());
    }

    private boolean isExplicitLoginPageFailure(String errorCode, String currentUrl) {
        if ("LOGIN_PAGE_CONFIRMED".equals(errorCode)) return true;
        if (!StringUtils.hasText(currentUrl)) return false;
        String url = currentUrl.toLowerCase(Locale.ROOT);
        return ("LOGIN_REQUIRED".equals(errorCode) || "login_required".equalsIgnoreCase(errorCode))
                && (url.contains("/login") || url.contains("/signin")
                || url.contains("passport") || url.contains("sso"));
    }

    private void applyFailedHealthFact(SelfMediaAccount account,
                                       String message,
                                       String method,
                                       LocalDateTime checkedAt) {
        account.setLastLoginVerificationCheckedAt(checkedAt);
        account.setLastLoginVerificationResult("failed");
        account.setLastLoginVerificationMethod(method);
        account.setLastLoginVerificationWarning(message);
        account.setRecommendedReverifyAt(null);
        account.setUpdatedAt(checkedAt);
        accountMapper.updateById(account);
        accountMapper.updateNullableLoginHealthFields(account.getId(), message, null);
        publishHealthChanged(account.getId());
    }

    private void publishHealthChanged(Long accountId) {
        eventPublisher.publishEvent(new SelfMediaAuthHealthChangedEvent(accountId));
    }

    private void applyReport(SelfMediaLoginVerification row, BrowserEnvironmentLoginStatusRequest request, LocalDateTime now) {
        row.setActualAccountName(trimToNull(request.actualAccountName()));
        row.setActualPlatformAccountId(trimToNull(request.actualPlatformAccountId()));
        row.setIdentityDiagnostics(sanitize(request.errorMessage()));
        row.setReportedAt(now);
        row.setUpdatedAt(now);
    }

    private void expireOpen(Long accountId, LocalDateTime now) {
        verificationMapper.update(null, new LambdaUpdateWrapper<SelfMediaLoginVerification>()
                .eq(SelfMediaLoginVerification::getSelfMediaAccountId, accountId)
                .in(SelfMediaLoginVerification::getStatus, OPEN_STATUSES)
                .le(SelfMediaLoginVerification::getExpiresAt, now)
                .set(SelfMediaLoginVerification::getStatus, "timeout")
                .set(SelfMediaLoginVerification::getResultCode, "TIMEOUT")
                .set(SelfMediaLoginVerification::getResultMessage, "登录状态验证超时")
                .set(SelfMediaLoginVerification::getUpdatedAt, now));
    }

    private void markTimeout(SelfMediaLoginVerification row) {
        row.setStatus("timeout");
        row.setResultCode("TIMEOUT");
        row.setResultMessage("登录状态验证超时");
        row.setUpdatedAt(LocalDateTime.now());
        verificationMapper.updateById(row);
    }

    private SelfMediaAccount requireAccount(Long id) {
        SelfMediaAccount row = accountMapper.selectById(id);
        if (row == null || row.getDeletedAt() != null) throw new BizException(404, "自媒体账号不存在");
        return row;
    }

    private SelfMediaLoginVerification requireVerification(Long id) {
        SelfMediaLoginVerification row = verificationMapper.selectById(id);
        if (row == null) throw new BizException(404, "登录验证任务不存在");
        return row;
    }

    private String normalizeName(String value) {
        String normalized = Normalizer.normalize(String.valueOf(value), Normalizer.Form.NFKC).trim();
        return normalized.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String canonicalPlatform(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
        String canonical = ArticlePromptChannels.normalizeSelfMediaPublishPlatform(normalized);
        return StringUtils.hasText(canonical) ? canonical : normalized;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String sanitize(String value) {
        String text = trimToNull(value);
        if (text == null) return null;
        String sanitized = text.replaceAll("(?i)(bearer\\s+|token[=:]\\s*|cookie[=:]\\s*)[^\\s,;]+", "$1***");
        return sanitized.length() > 512 ? sanitized.substring(0, 512) : sanitized;
    }

    private void requireAnyPermission(String permission, String... compatiblePermissions) {
        if (currentUserService.hasPermission(permission)) return;
        for (String compatiblePermission : compatiblePermissions) {
            if (currentUserService.hasPermission(compatiblePermission)) return;
        }
        throw new BizException(403, "No permission: " + permission);
    }
}
