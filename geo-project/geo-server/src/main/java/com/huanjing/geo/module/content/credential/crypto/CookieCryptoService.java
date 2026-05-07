package com.huanjing.geo.module.content.credential.crypto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import static com.huanjing.geo.module.content.credential.CredentialErrorCodes.CREDENTIAL_INTEGRITY_VIOLATION;
import static com.huanjing.geo.module.content.credential.CredentialErrorCodes.CREDENTIAL_INTERNAL_ERROR;
import static com.huanjing.geo.module.content.credential.CredentialErrorCodes.CREDENTIAL_PAYLOAD_INVALID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CookieCryptoService {

    private static final String CIPHER_ALG = "AES-256-GCM";
    private static final int DEK_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int MAX_COOKIE_CIPHERTEXT_BYTES = 64 * 1024;

    private final MasterKeyProvider masterKeyProvider;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public EncryptedCookiePayload encrypt(String cookiesJson, CookieEncryptionContext context) {
        byte[] dek = new byte[DEK_BYTES];
        secureRandom.nextBytes(dek);
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(dek, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(aad(context));
            byte[] ciphertext = cipher.doFinal(cookiesJson.getBytes(StandardCharsets.UTF_8));
            EncryptedData encryptedDek = masterKeyProvider.encryptDek(dek, context.canonicalAad());
            return new EncryptedCookiePayload(
                    CIPHER_ALG,
                    context.canonicalAad(),
                    Base64.getEncoder().encodeToString(iv),
                    Base64.getEncoder().encodeToString(ciphertext),
                    objectMapper.writeValueAsString(encryptedDek),
                    masterKeyProvider.keyId()
            );
        } catch (Exception ex) {
            throw new BizException(CREDENTIAL_INTERNAL_ERROR, "cookie encrypt failed", ex);
        } finally {
            zero(dek);
        }
    }

    public String decrypt(EncryptedCookiePayload payload, CookieEncryptionContext context) {
        byte[] dek = null;
        byte[] plaintext = null;
        try {
            byte[] iv = Base64.getDecoder().decode(payload.ivBase64());
            if (iv.length != IV_BYTES) {
                throw new BizException(CREDENTIAL_INTEGRITY_VIOLATION, "invalid cookie iv length");
            }
            byte[] ciphertext = Base64.getDecoder().decode(payload.cookiesCiphertext());
            if (ciphertext.length > MAX_COOKIE_CIPHERTEXT_BYTES) {
                throw new BizException(CREDENTIAL_INTEGRITY_VIOLATION, "cookie ciphertext too large");
            }
            EncryptedData encryptedDek = readEncryptedDek(payload.encryptedDek());
            dek = decryptDek(encryptedDek, context);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(dek, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(aad(context));
            plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (BizException ex) {
            throw ex;
        } catch (AEADBadTagException ex) {
            log.warn("cookie integrity check failed for accountId={}", context == null ? null : context.accountId());
            throw new BizException(CREDENTIAL_INTEGRITY_VIOLATION, "cookie integrity check failed", ex);
        } catch (IllegalArgumentException ex) {
            throw new BizException(CREDENTIAL_PAYLOAD_INVALID, "cookie credential payload invalid", ex);
        } catch (Exception ex) {
            log.warn("cookie decrypt failed for accountId={}", context == null ? null : context.accountId(), ex);
            throw new BizException(CREDENTIAL_INTERNAL_ERROR, "cookie decrypt failed", ex);
        } finally {
            zero(dek);
            zero(plaintext);
        }
    }

    private EncryptedData readEncryptedDek(String encryptedDek) {
        try {
            return objectMapper.readValue(encryptedDek, EncryptedData.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new BizException(CREDENTIAL_PAYLOAD_INVALID, "cookie credential payload invalid", ex);
        }
    }

    private byte[] decryptDek(EncryptedData encryptedDek, CookieEncryptionContext context) {
        try {
            return masterKeyProvider.decryptDek(encryptedDek, context.canonicalAad());
        } catch (BizException ex) {
            if (ex.getCode() == CREDENTIAL_INTEGRITY_VIOLATION) {
                throw new BizException(CREDENTIAL_INTEGRITY_VIOLATION, "cookie integrity check failed", ex);
            }
            throw new BizException(CREDENTIAL_INTERNAL_ERROR, "cookie decrypt failed", ex);
        }
    }

    private byte[] aad(CookieEncryptionContext context) {
        return context.canonicalAad().getBytes(StandardCharsets.UTF_8);
    }

    private static void zero(byte[] value) {
        if (value == null) {
            return;
        }
        java.util.Arrays.fill(value, (byte) 0);
    }

    public record CookieEncryptionContext(Long brandId, Long accountId, String platform, Integer version) {
        /**
         * Canonical AAD form. Field order is part of the cryptographic contract; do not reorder.
         */
        public String canonicalAad() {
            return "brandId=" + brandId
                    + "|accountId=" + accountId
                    + "|platform=" + (platform == null ? "" : platform)
                    + "|version=" + version;
        }

    }

    public record EncryptedCookiePayload(
            String cipherAlg,
            String aadContext,
            String ivBase64,
            String cookiesCiphertext,
            String encryptedDek,
            String masterKeyId
    ) {}
}
