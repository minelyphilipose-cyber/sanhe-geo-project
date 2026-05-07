package com.huanjing.geo.module.extension.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.audit.AuditMode;
import com.huanjing.geo.module.audit.AuditResult;
import com.huanjing.geo.module.content.credential.dto.CookieCredentialCaptureCommand;
import com.huanjing.geo.module.content.credential.dto.CookieCredentialMeta;
import com.huanjing.geo.module.content.credential.service.CredentialVaultService;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.customer.access.BrandAccessAction;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.extension.dto.ExtensionCookieCaptureRequest;
import com.huanjing.geo.module.extension.dto.ExtensionCookieCaptureResponse;
import com.huanjing.geo.module.extension.dto.ExtensionSelfMediaAccountResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.huanjing.geo.module.extension.ExtensionErrorCodes.COOKIE_CAPTURE_ACCOUNT_BRAND_MISMATCH;
import static com.huanjing.geo.module.extension.ExtensionErrorCodes.COOKIE_CAPTURE_CONFIRM_REQUIRED;
import static com.huanjing.geo.module.extension.ExtensionErrorCodes.COOKIE_CAPTURE_NONCE_REPLAYED;
import static com.huanjing.geo.module.extension.ExtensionErrorCodes.EXTENSION_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class ExtensionCookieCaptureService {

    private static final int ACCOUNT_CANDIDATE_LIMIT = 500;
    private static final Duration NONCE_TTL = Duration.ofMinutes(10);
    private static final String NONCE_KEY_PREFIX = "geo:extension:cookie-capture:nonce:";

    private final SelfMediaAccountMapper accountMapper;
    private final BrandAccessService brandAccessService;
    private final CredentialVaultService credentialVaultService;
    private final ExtensionRedisStore redisStore;
    private final ExtensionAuditSupport auditSupport;

    public List<ExtensionSelfMediaAccountResponse> listAccounts(Long operatorId) {
        return accountMapper.selectExtensionAccountCandidates(ACCOUNT_CANDIDATE_LIMIT)
                .stream()
                .filter(account -> brandAccessService.hasBrandAccess(
                        account.getBrandId(), operatorId, BrandAccessAction.OPERATE))
                .map(account -> new ExtensionSelfMediaAccountResponse(
                        account.getId(),
                        account.getPlatform(),
                        account.getAccountName(),
                        account.getBrandId()
                ))
                .toList();
    }

    public ExtensionCookieCaptureResponse capture(ExtensionCookieCaptureRequest request,
                                                  Long operatorId,
                                                  Long extensionSessionId) {
        validateConfirmed(request);
        brandAccessService.requireBrandAccess(request.brandId(), operatorId, BrandAccessAction.MANAGE);
        SelfMediaAccount account = requireAccount(request.accountId());
        if (!request.brandId().equals(account.getBrandId())) {
            auditCapture(AuditResult.DENIED, operatorId, request.brandId(), request.accountId(), extensionSessionId,
                    String.valueOf(COOKIE_CAPTURE_ACCOUNT_BRAND_MISMATCH), "ACCOUNT_BRAND_MISMATCH", null);
            throw new BizException(COOKIE_CAPTURE_ACCOUNT_BRAND_MISMATCH, "cookie capture account brand mismatch");
        }
        if (StringUtils.hasText(request.platform()) && !request.platform().equalsIgnoreCase(account.getPlatform())) {
            auditCapture(AuditResult.DENIED, operatorId, request.brandId(), request.accountId(), extensionSessionId,
                    String.valueOf(COOKIE_CAPTURE_ACCOUNT_BRAND_MISMATCH), "ACCOUNT_PLATFORM_MISMATCH", null);
            throw new BizException(COOKIE_CAPTURE_ACCOUNT_BRAND_MISMATCH, "cookie capture account brand mismatch");
        }
        consumeConfirmNonce(operatorId, request.confirmNonce());

        CookieCredentialMeta meta = credentialVaultService.storeCapturedCookies(new CookieCredentialCaptureCommand(
                request.accountId(),
                request.cookiesJson(),
                request.userAgent(),
                request.capturedFingerprintJson(),
                request.requiredCookieCheckJson(),
                operatorId
        ));
        auditCapture(AuditResult.SUCCESS, operatorId, meta.brandId(), meta.selfMediaAccountId(), extensionSessionId,
                null, null, Map.of(
                        "platform", meta.platform(),
                        "version", meta.version(),
                        "installId", request.installId()
                ));
        return new ExtensionCookieCaptureResponse(
                meta.id(),
                meta.selfMediaAccountId(),
                meta.brandId(),
                meta.platform(),
                meta.version(),
                meta.capturedAt(),
                "ACTIVE"
        );
    }

    private void validateConfirmed(ExtensionCookieCaptureRequest request) {
        if (!Boolean.TRUE.equals(request.operatorConfirmed())) {
            throw new BizException(COOKIE_CAPTURE_CONFIRM_REQUIRED, "cookie capture confirmation is required");
        }
        if (!StringUtils.hasText(request.confirmNonce())) {
            throw new BizException(COOKIE_CAPTURE_CONFIRM_REQUIRED, "cookie capture confirmation nonce is required");
        }
    }

    private void consumeConfirmNonce(Long operatorId, String confirmNonce) {
        String key = NONCE_KEY_PREFIX + operatorId + ":" + confirmNonce.trim();
        if (!redisStore.tryLock(key, "1", NONCE_TTL)) {
            throw new BizException(COOKIE_CAPTURE_NONCE_REPLAYED, "cookie capture confirmation already used");
        }
    }

    private SelfMediaAccount requireAccount(Long accountId) {
        SelfMediaAccount account = accountMapper.selectById(accountId);
        if (account == null || account.getDeletedAt() != null) {
            throw new BizException(EXTENSION_NOT_FOUND, "self media account not found");
        }
        return account;
    }

    private void auditCapture(AuditResult result,
                              Long operatorId,
                              Long brandId,
                              Long accountId,
                              Long extensionSessionId,
                              String errorCode,
                              String errorMessage,
                              Map<String, Object> extraDetail) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("operation", "cookie_capture");
        if (extraDetail != null) {
            detail.putAll(extraDetail);
        }
        auditSupport.record(
                "EXTENSION_COOKIE_CAPTURE",
                result,
                AuditMode.SYNC,
                true,
                operatorId,
                brandId,
                accountId,
                null,
                extensionSessionId,
                "SELF_MEDIA_ACCOUNT",
                accountId == null ? null : String.valueOf(accountId),
                errorCode,
                errorMessage,
                detail
        );
    }
}
