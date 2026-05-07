package com.huanjing.geo.module.content.credential.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.credential.audit.CredentialAuditHook;
import com.huanjing.geo.module.content.credential.crypto.CookieCryptoService;
import com.huanjing.geo.module.content.credential.crypto.CookieCryptoService.CookieEncryptionContext;
import com.huanjing.geo.module.content.credential.dto.CookieCredentialCaptureCommand;
import com.huanjing.geo.module.content.credential.dto.CookieCredentialMeta;
import com.huanjing.geo.module.content.credential.dto.CookieCredentialPlaintext;
import com.huanjing.geo.module.content.credential.entity.SelfMediaCookieCredential;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaCookieCredentialMapper;
import com.huanjing.geo.module.customer.access.BrandAccessAction;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static com.huanjing.geo.module.content.credential.CredentialErrorCodes.CREDENTIAL_INTEGRITY_VIOLATION;
import static com.huanjing.geo.module.content.credential.CredentialErrorCodes.CREDENTIAL_BAD_REQUEST;
import static com.huanjing.geo.module.content.credential.CredentialErrorCodes.CREDENTIAL_NOT_FOUND;
import static com.huanjing.geo.module.content.credential.CredentialErrorCodes.CREDENTIAL_PAYLOAD_INVALID;

/**
 * Cookie credential vault.
 *
 * <p>SECURITY CONTRACT: this service owns credential encryption, versioning, destruction, and
 * brand-level access checks for sensitive credential operations. Upstream controllers must pass
 * the expected brand id from an authoritative source, such as a signed fill token, instead of
 * reading the account row and echoing its brand id back into this service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CredentialVaultService {

    private static final int MAX_COOKIE_JSON_BYTES = 48 * 1024;
    private static final String AUTH_MODE_COOKIE = "COOKIE";
    private static final String STATUS_ACTIVE = "active";

    private final SelfMediaCookieCredentialMapper credentialMapper;
    private final SelfMediaAccountMapper accountMapper;
    private final CookieCryptoService cookieCryptoService;
    private final CredentialAuditHook auditHook;
    private final BrandAccessService brandAccessService;

    /**
     * Stores newly captured cookies as a new credential version.
     *
     * <p>For capture, {@code self_media_account.brand_id} is the authoritative ownership source:
     * this method is creating a new credential row from the account row, so there is no existing
     * credential brand id to compare against. Decrypt/destroy operations accept an external
     * {@code expectedBrandId} because they operate on existing credential rows whose brand id must
     * be checked against a signed or otherwise authoritative caller context.</p>
     */
    @Transactional
    public CookieCredentialMeta storeCapturedCookies(CookieCredentialCaptureCommand command) {
        validateCaptureCommand(command);
        lockAccount(command.selfMediaAccountId());
        SelfMediaAccount account = requireAccount(command.selfMediaAccountId());
        brandAccessService.requireBrandAccess(account.getBrandId(), command.capturedBy(), BrandAccessAction.MANAGE);
        LocalDateTime now = LocalDateTime.now();

        SelfMediaCookieCredential latest = credentialMapper.selectLatestByAccountIdForUpdate(account.getId());
        int nextVersion = latest == null ? 1 : latest.getVersion() + 1;
        credentialMapper.closeActiveVersions(account.getId(), now);

        CookieEncryptionContext context = new CookieEncryptionContext(
                account.getBrandId(),
                account.getId(),
                account.getPlatform(),
                nextVersion
        );
        CookieCryptoService.EncryptedCookiePayload encrypted =
                cookieCryptoService.encrypt(command.cookiesJson(), context);

        SelfMediaCookieCredential credential = new SelfMediaCookieCredential();
        credential.setSelfMediaAccountId(account.getId());
        credential.setBrandId(account.getBrandId());
        credential.setPlatform(account.getPlatform());
        credential.setVersion(nextVersion);
        credential.setCookiesCiphertext(encrypted.cookiesCiphertext());
        credential.setCookieIvBase64(encrypted.ivBase64());
        credential.setEncryptedDek(encrypted.encryptedDek());
        credential.setMasterKeyId(encrypted.masterKeyId());
        credential.setCipherAlg(encrypted.cipherAlg());
        credential.setAadContext(encrypted.aadContext());
        credential.setUserAgent(command.userAgent());
        credential.setCapturedFingerprintJson(command.capturedFingerprintJson());
        credential.setRequiredCookieCheckJson(command.requiredCookieCheckJson());
        credential.setCapturedBy(command.capturedBy());
        credential.setCapturedAt(now);
        credential.setValidFrom(now);
        credential.setCreatedAt(now);
        credentialMapper.insert(credential);

        markAccountCookieActive(account, now);

        CookieCredentialMeta meta = CookieCredentialMeta.from(credential);
        auditHook.onCredentialStored(meta);
        return meta;
    }

    public CookieCredentialMeta getActiveCredentialMeta(Long selfMediaAccountId) {
        requireAccountId(selfMediaAccountId);
        return CookieCredentialMeta.from(credentialMapper.selectActiveMetaByAccountId(selfMediaAccountId));
    }

    public List<CookieCredentialMeta> listCredentialMetaHistory(Long selfMediaAccountId) {
        requireAccountId(selfMediaAccountId);
        return credentialMapper.selectMetaHistoryByAccountId(selfMediaAccountId)
                .stream()
                .map(CookieCredentialMeta::from)
                .toList();
    }

    /**
     * Decrypts the active cookie credential after the caller has completed authorization.
     *
     * <p>The returned plaintext currently contains a short-lived String because extension API
     * serialization still expects JSON text. Callers must use it immediately and must not log,
     * cache, or retain it beyond the request scope.
     */
    public CookieCredentialPlaintext decryptActiveCookies(Long selfMediaAccountId, Long expectedBrandId, Long operatorId) {
        requireAccountId(selfMediaAccountId);
        if (expectedBrandId == null) {
            throw new BizException(CREDENTIAL_BAD_REQUEST, "expected brand id is required");
        }
        brandAccessService.requireBrandAccess(expectedBrandId, operatorId, BrandAccessAction.OPERATE);
        SelfMediaCookieCredential credential = credentialMapper.selectActiveFullByAccountId(selfMediaAccountId);
        if (credential == null) {
            throw new BizException(CREDENTIAL_NOT_FOUND, "active cookie credential not found");
        }
        if (!expectedBrandId.equals(credential.getBrandId())) {
            log.warn("credential brand mismatch: accountId={}, expectedBrandId={}, actualBrandId={}, operatorId={}",
                    selfMediaAccountId, expectedBrandId, credential.getBrandId(), operatorId);
            auditHook.onCredentialAccessDenied(
                    selfMediaAccountId,
                    expectedBrandId,
                    credential.getBrandId(),
                    operatorId,
                    "BRAND_MISMATCH"
            );
            throw new BizException(CREDENTIAL_INTEGRITY_VIOLATION, "credential brand mismatch");
        }

        CookieEncryptionContext context = new CookieEncryptionContext(
                credential.getBrandId(),
                credential.getSelfMediaAccountId(),
                credential.getPlatform(),
                credential.getVersion()
        );
        if (StringUtils.hasText(credential.getAadContext())
                && !credential.getAadContext().equals(context.canonicalAad())) {
            throw new BizException(CREDENTIAL_INTEGRITY_VIOLATION, "credential aad context mismatch");
        }
        if (!StringUtils.hasText(credential.getCookiesCiphertext())
                || !StringUtils.hasText(credential.getCookieIvBase64())
                || !StringUtils.hasText(credential.getEncryptedDek())) {
            throw new BizException(CREDENTIAL_PAYLOAD_INVALID, "cookie credential payload invalid");
        }

        CookieCryptoService.EncryptedCookiePayload payload = new CookieCryptoService.EncryptedCookiePayload(
                credential.getCipherAlg(),
                credential.getAadContext(),
                credential.getCookieIvBase64(),
                credential.getCookiesCiphertext(),
                credential.getEncryptedDek(),
                credential.getMasterKeyId()
        );
        String cookiesJson = cookieCryptoService.decrypt(payload, context);

        CookieCredentialMeta meta = CookieCredentialMeta.from(toMetaOnly(credential));
        auditHook.onCredentialDecrypted(meta, operatorId);
        return new CookieCredentialPlaintext(
                credential.getSelfMediaAccountId(),
                credential.getBrandId(),
                credential.getPlatform(),
                credential.getVersion(),
                cookiesJson,
                credential.getUserAgent(),
                credential.getRequiredCookieCheckJson()
        );
    }

    @Transactional
    public int destroyCredentials(Long selfMediaAccountId, Long expectedBrandId, Long operatorId) {
        requireAccountId(selfMediaAccountId);
        if (expectedBrandId == null) {
            throw new BizException(CREDENTIAL_BAD_REQUEST, "expected brand id is required");
        }
        brandAccessService.requireBrandAccess(expectedBrandId, operatorId, BrandAccessAction.MANAGE);
        SelfMediaAccount account = requireAccount(selfMediaAccountId);
        if (!expectedBrandId.equals(account.getBrandId())) {
            auditHook.onCredentialAccessDenied(
                    selfMediaAccountId,
                    expectedBrandId,
                    account.getBrandId(),
                    operatorId,
                    "BRAND_MISMATCH"
            );
            throw new BizException(CREDENTIAL_INTEGRITY_VIOLATION, "credential brand mismatch");
        }
        int affectedRows = credentialMapper.destroyByAccountId(selfMediaAccountId, LocalDateTime.now());
        auditHook.onCredentialDestroyed(selfMediaAccountId, operatorId, affectedRows);
        return affectedRows;
    }

    private void validateCaptureCommand(CookieCredentialCaptureCommand command) {
        if (command == null || command.selfMediaAccountId() == null) {
            throw new BizException(CREDENTIAL_BAD_REQUEST, "self media account id is required");
        }
        if (!StringUtils.hasText(command.cookiesJson())) {
            throw new BizException(CREDENTIAL_BAD_REQUEST, "cookies json is required");
        }
        if (command.cookiesJson().getBytes(StandardCharsets.UTF_8).length > MAX_COOKIE_JSON_BYTES) {
            throw new BizException(CREDENTIAL_PAYLOAD_INVALID, "cookies json too large");
        }
    }

    private SelfMediaAccount requireAccount(Long selfMediaAccountId) {
        SelfMediaAccount account = accountMapper.selectById(selfMediaAccountId);
        if (account == null) {
            throw new BizException(CREDENTIAL_NOT_FOUND, "self media account not found");
        }
        return account;
    }

    private void requireAccountId(Long selfMediaAccountId) {
        if (selfMediaAccountId == null) {
            throw new BizException(CREDENTIAL_BAD_REQUEST, "self media account id is required");
        }
    }

    private void lockAccount(Long selfMediaAccountId) {
        Long lockedId = accountMapper.lockById(selfMediaAccountId);
        if (lockedId == null) {
            throw new BizException(CREDENTIAL_NOT_FOUND, "self media account not found");
        }
    }

    private void markAccountCookieActive(SelfMediaAccount account, LocalDateTime now) {
        accountMapper.update(null, Wrappers.<SelfMediaAccount>lambdaUpdate()
                .eq(SelfMediaAccount::getId, account.getId())
                .set(SelfMediaAccount::getAuthMode, AUTH_MODE_COOKIE)
                .set(SelfMediaAccount::getStatus, STATUS_ACTIVE)
                .set(SelfMediaAccount::getLastAuthCheckedAt, now)
                .set(SelfMediaAccount::getLastAuthError, null)
                .set(SelfMediaAccount::getUpdatedAt, now));
    }

    private SelfMediaCookieCredential toMetaOnly(SelfMediaCookieCredential credential) {
        SelfMediaCookieCredential meta = new SelfMediaCookieCredential();
        meta.setId(credential.getId());
        meta.setSelfMediaAccountId(credential.getSelfMediaAccountId());
        meta.setBrandId(credential.getBrandId());
        meta.setPlatform(credential.getPlatform());
        meta.setVersion(credential.getVersion());
        meta.setMasterKeyId(credential.getMasterKeyId());
        meta.setCipherAlg(credential.getCipherAlg());
        meta.setCookieIvBase64(credential.getCookieIvBase64());
        meta.setAadContext(credential.getAadContext());
        meta.setUserAgent(credential.getUserAgent());
        meta.setCapturedFingerprintJson(credential.getCapturedFingerprintJson());
        meta.setRequiredCookieCheckJson(credential.getRequiredCookieCheckJson());
        meta.setCapturedBy(credential.getCapturedBy());
        meta.setCapturedAt(credential.getCapturedAt());
        meta.setValidFrom(credential.getValidFrom());
        meta.setValidUntil(credential.getValidUntil());
        meta.setDestroyedAt(credential.getDestroyedAt());
        meta.setCreatedAt(credential.getCreatedAt());
        return meta;
    }
}
