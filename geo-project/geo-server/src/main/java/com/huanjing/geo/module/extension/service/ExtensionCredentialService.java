package com.huanjing.geo.module.extension.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.audit.AuditMode;
import com.huanjing.geo.module.audit.AuditResult;
import com.huanjing.geo.module.content.credential.CredentialErrorCodes;
import com.huanjing.geo.module.content.credential.dto.CookieCredentialPlaintext;
import com.huanjing.geo.module.content.credential.service.CredentialVaultService;
import com.huanjing.geo.module.extension.dto.ExtensionFillTokenConsumeResponse;
import com.huanjing.geo.module.extension.dto.FillTokenConsumeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bridges extension fill-token verification to credential vault decryption.
 *
 * <p>SECURITY CONTRACT: callers pass the operator id from an already validated extension session.
 * This service consumes the fill token exactly once, then uses the signed brand/account context from
 * that token when calling {@link CredentialVaultService#decryptActiveCookies(Long, Long, Long)}.
 * It never reads brand id from the account row to echo back into the vault.</p>
 */
@Service
@RequiredArgsConstructor
public class ExtensionCredentialService {

    private final FillTokenService fillTokenService;
    private final CredentialVaultService credentialVaultService;
    private final ExtensionTaskStateService taskStateService;
    private final ExtensionAuditSupport auditSupport;

    public ExtensionFillTokenConsumeResponse consumeFillTokenAndDecrypt(
            String fillToken,
            Long expectedOperatorId,
            Long extensionSessionId,
            String ipAddress
    ) {
        FillTokenConsumeResponse consumed = fillTokenService.consume(fillToken, expectedOperatorId, extensionSessionId);
        CookieCredentialPlaintext plaintext;
        try {
            plaintext = credentialVaultService.decryptActiveCookies(
                    consumed.accountId(),
                    consumed.brandId(),
                    consumed.operatorId()
            );
        } catch (BizException ex) {
            auditCookieDecryptFailure(consumed, extensionSessionId, ipAddress, ex);
            throw ex;
        }
        taskStateService.markFillingFromFillTokenConsume(
                consumed.taskTargetId(),
                consumed.operatorId(),
                extensionSessionId
        );

        auditSupport.record(
                "COOKIE_DECRYPT_VIA_FILL_TOKEN",
                AuditResult.SUCCESS,
                AuditMode.SYNC,
                true,
                consumed.operatorId(),
                consumed.brandId(),
                consumed.accountId(),
                consumed.taskTargetId(),
                extensionSessionId,
                "FILL_TOKEN",
                consumed.nonce(),
                null,
                null,
                decryptAuditDetail(consumed, plaintext, ipAddress)
        );

        return new ExtensionFillTokenConsumeResponse(
                consumed.taskTargetId(),
                consumed.expiresAt(),
                consumed.nonce(),
                plaintext.platform(),
                plaintext.version(),
                plaintext.cookiesJson(),
                plaintext.userAgent(),
                plaintext.requiredCookieCheckJson()
        );
    }

    private void auditCookieDecryptFailure(
            FillTokenConsumeResponse consumed,
            Long extensionSessionId,
            String ipAddress,
            BizException ex
    ) {
        AuditResult result = ex.getCode() == CredentialErrorCodes.CREDENTIAL_NOT_FOUND
                ? AuditResult.NOT_FOUND
                : AuditResult.DENIED;
        auditSupport.record(
                "COOKIE_DECRYPT_VIA_FILL_TOKEN",
                result,
                AuditMode.SYNC,
                true,
                consumed.operatorId(),
                consumed.brandId(),
                consumed.accountId(),
                consumed.taskTargetId(),
                extensionSessionId,
                "FILL_TOKEN",
                consumed.nonce(),
                String.valueOf(ex.getCode()),
                ex.getMessage(),
                decryptFailureAuditDetail(consumed, ipAddress)
        );
    }

    private Map<String, Object> decryptAuditDetail(
            FillTokenConsumeResponse consumed,
            CookieCredentialPlaintext plaintext,
            String ipAddress
    ) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("nonce", consumed.nonce());
        detail.put("expiresAt", consumed.expiresAt());
        detail.put("credentialVersion", plaintext.version());
        detail.put("platform", plaintext.platform());
        detail.put("ipAddress", auditIp(ipAddress));
        return detail;
    }

    private Map<String, Object> decryptFailureAuditDetail(FillTokenConsumeResponse consumed, String ipAddress) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("nonce", consumed.nonce());
        detail.put("expiresAt", consumed.expiresAt());
        detail.put("ipAddress", auditIp(ipAddress));
        return detail;
    }

    private String auditIp(String ipAddress) {
        return StringUtils.hasText(ipAddress) ? ipAddress : "unknown";
    }
}
