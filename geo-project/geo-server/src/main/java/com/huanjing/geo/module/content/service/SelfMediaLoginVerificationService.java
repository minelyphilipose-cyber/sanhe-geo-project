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
    private static final List<String> OPEN_STATUSES = List.of("pending", "running");
    private static final int TTL_SECONDS = 60;
    public static final String METHOD_SYSTEM_TRIGGERED = "system_triggered_identity_check";
    public static final String METHOD_EXTENSION_PASSIVE = "extension_passive_identity_report";

    private final SelfMediaLoginVerificationMapper verificationMapper;
    private final SelfMediaAccountMapper accountMapper;
    private final BrowserEnvironmentAccountMapper environmentAccountMapper;
    private final BrowserEnvironmentMapper environmentMapper;
    private final BrandAccessService brandAccessService;
    private final CurrentUserService currentUserService;
    private final SelfMediaAuthHealthPolicyService policyService;
    private final SystemAlertService systemAlertService;

    @Transactional
    public SelfMediaLoginVerificationVO create(Long brandId, Long accountId) {
        SysUser operator = currentUserService.requireCurrentUser();
        requirePermission("self-media.auth-health.verify", "company.update");
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
        requirePermission("self-media.auth-health.read", "company.read");
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
    public void completeFromLoginReport(Long verificationId,
                                        BrowserEnvironmentAccount binding,
                                        SelfMediaAccount account,
                                        BrowserEnvironmentLoginStatusRequest request,
                                        String reportedStatus) {
        if (verificationId == null) return;
        SelfMediaLoginVerification row = requireVerification(verificationId);
        LocalDateTime now = LocalDateTime.now();
        if (!OPEN_STATUSES.contains(row.getStatus())) return;
        if (!row.getExpiresAt().isAfter(now)) {
            markTimeout(row);
            return;
        }
        if (!Objects.equals(row.getBrowserEnvironmentAccountId(), binding.getId())
                || !Objects.equals(row.getSelfMediaAccountId(), account.getId())) {
            finishFailure(row, "ENVIRONMENT_MISMATCH", "验证结果不是来自账号绑定的指纹浏览器环境", request, now);
            return;
        }
        if (!Objects.equals(canonicalPlatform(row.getPlatform()), canonicalPlatform(request.platform()))) {
            finishFailure(row, "PLATFORM_MISMATCH", "验证结果平台与目标平台不一致", request, now);
            return;
        }
        if (!BrowserEnvironmentConstants.LOGIN_LOGGED_IN.equals(request.loginStatus())) {
            String errorCode = trimToNull(request.errorCode());
            if ("MULTIPLE_IDENTITIES".equals(errorCode)) {
                finishFailure(row, "MULTIPLE_IDENTITIES", "读取到多个平台账号身份，请确认当前环境只登录一个账号", request, now);
            } else if ("IDENTITY_UNREADABLE".equals(errorCode)) {
                finishFailure(row, "IDENTITY_UNREADABLE", "未读取到当前平台账号名称", request, now);
            } else {
                finishFailure(row, "LOGIN_REQUIRED", "当前平台尚未完成登录", request, now);
            }
            return;
        }
        String actualName = trimToNull(request.actualAccountName());
        if (actualName == null) {
            finishFailure(row, "IDENTITY_UNREADABLE", "未读取到当前平台账号名称", request, now);
            return;
        }
        if (!normalizeName(row.getExpectedAccountName()).equals(normalizeName(actualName))) {
            finishFailure(row, "ACCOUNT_NAME_MISMATCH",
                    "当前登录账号为“" + actualName + "”，系统绑定账号为“" + row.getExpectedAccountName() + "”",
                    request, now);
            return;
        }
        boolean idWarning = StringUtils.hasText(row.getExpectedPlatformAccountId())
                && StringUtils.hasText(request.actualPlatformAccountId())
                && !row.getExpectedPlatformAccountId().trim().equals(request.actualPlatformAccountId().trim());
        String warning = idWarning ? "平台账号 ID 与系统记录不同，建议后续核对账号资料" : null;
        row.setStatus("succeeded");
        row.setResultCode(idWarning ? "SUCCESS_WITH_ID_WARNING" : "SUCCESS");
        row.setResultMessage(idWarning ? warning : "登录状态验证成功");
        applyReport(row, request, now);
        verificationMapper.updateById(row);

        applySuccessfulHealthFact(account, warning, METHOD_SYSTEM_TRIGGERED, now);
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
            recordPassiveHealthDiagnostic(account, passiveFailureMessage(request));
            return false;
        }
        String actualName = trimToNull(request.actualAccountName());
        if (!StringUtils.hasText(account.getAccountName())) {
            recordPassiveHealthDiagnostic(account, "系统账号名称为空，请先编辑账号名称");
            return false;
        }
        if (actualName == null) {
            recordPassiveHealthDiagnostic(account, "扩展未读取到唯一账号名称，请确认身份页已完全加载");
            return false;
        }
        if (!normalizeName(account.getAccountName()).equals(normalizeName(actualName))) {
            recordPassiveHealthDiagnostic(account,
                    "系统账号“" + account.getAccountName().trim() + "”与扩展读取账号“" + actualName + "”不一致");
            return false;
        }
        boolean idWarning = StringUtils.hasText(account.getPlatformAccountId())
                && StringUtils.hasText(request.actualPlatformAccountId())
                && !account.getPlatformAccountId().trim().equals(request.actualPlatformAccountId().trim());
        String warning = idWarning ? "平台账号 ID 与系统记录不同，登录名称已确认一致" : null;
        applySuccessfulHealthFact(account, warning, METHOD_EXTENSION_PASSIVE, LocalDateTime.now());
        return true;
    }

    private String passiveFailureMessage(BrowserEnvironmentLoginStatusRequest request) {
        String code = trimToNull(request.errorCode());
        if ("MULTIPLE_IDENTITIES".equals(code)) {
            return "扩展读取到多个账号身份，请确认当前环境的该平台只登录一个账号";
        }
        if ("IDENTITY_UNREADABLE".equals(code)) {
            return "扩展未读取到唯一账号名称，请确认身份页已完全加载";
        }
        if (BrowserEnvironmentConstants.LOGIN_REQUIRED.equals(request.loginStatus())) {
            return "平台身份页未确认登录，请在指纹浏览器中完成登录后重新同步";
        }
        String message = sanitize(request.errorMessage());
        return message == null ? "扩展登录健康上报未通过，请重新同步登录状态" : message;
    }

    private void recordPassiveHealthDiagnostic(SelfMediaAccount account, String message) {
        account.setLastLoginVerificationWarning(message);
        if (account.getLastLoginVerifiedAt() == null) {
            account.setLastLoginVerificationResult("failed");
            account.setLastLoginVerificationMethod(METHOD_EXTENSION_PASSIVE);
        }
        account.setUpdatedAt(LocalDateTime.now());
        accountMapper.updateById(account);
    }

    private void applySuccessfulHealthFact(SelfMediaAccount account,
                                           String warning,
                                           String method,
                                           LocalDateTime verifiedAt) {
        account.setLastLoginVerifiedAt(verifiedAt);
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

    private void finishFailure(SelfMediaLoginVerification row, String code, String message,
                               BrowserEnvironmentLoginStatusRequest request, LocalDateTime now) {
        row.setStatus("failed");
        row.setResultCode(code);
        row.setResultMessage(message);
        applyReport(row, request, now);
        verificationMapper.updateById(row);
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

    private void requirePermission(String permission, String legacyPermission) {
        if (!currentUserService.hasPermission(permission)
                && !currentUserService.hasPermission(legacyPermission)) {
            throw new BizException(403, "No permission: " + permission);
        }
    }
}
