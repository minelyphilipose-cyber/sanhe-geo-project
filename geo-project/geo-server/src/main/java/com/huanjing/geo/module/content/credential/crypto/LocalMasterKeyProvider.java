package com.huanjing.geo.module.content.credential.crypto;

import com.huanjing.geo.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.AEADBadTagException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.function.Supplier;

import static com.huanjing.geo.module.content.credential.CredentialErrorCodes.CREDENTIAL_INTEGRITY_VIOLATION;
import static com.huanjing.geo.module.content.credential.CredentialErrorCodes.CREDENTIAL_INTERNAL_ERROR;

@Slf4j
@Service
public class LocalMasterKeyProvider implements MasterKeyProvider {

    private static final String ALG = "AES-256-GCM";
    private static final int IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final SecureRandom secureRandom = new SecureRandom();
    private final String keyId;
    private final byte[] masterKey;

    public LocalMasterKeyProvider(
            @Value("${geo.cookie.master-key-id:local-v1}") String keyId,
            @Value("${geo.cookie.master-key-base64:}") String configuredMasterKeyBase64
    ) {
        this(keyId, configuredMasterKeyBase64, () -> System.getenv("GEO_COOKIE_MASTER_KEY_BASE64"));
    }

    LocalMasterKeyProvider(String keyId, String configuredMasterKeyBase64, Supplier<String> envLookup) {
        this.keyId = StringUtils.hasText(keyId) ? keyId.trim() : "local-v1";
        this.masterKey = resolveMasterKey(configuredMasterKeyBase64, envLookup);
    }

    @Override
    public String keyId() {
        return keyId;
    }

    @Override
    public EncryptedData encryptDek(byte[] dek, String canonicalAad) {
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(masterKey, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(contextAad(canonicalAad));
            byte[] ciphertext = cipher.doFinal(dek);
            return new EncryptedData(keyId, ALG, b64(iv), b64(ciphertext));
        } catch (Exception ex) {
            throw new BizException(CREDENTIAL_INTERNAL_ERROR, "dek encrypt failed", ex);
        }
    }

    @Override
    public byte[] decryptDek(EncryptedData encryptedDek, String canonicalAad) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            byte[] iv = Base64.getDecoder().decode(encryptedDek.getIvBase64());
            if (iv.length != IV_BYTES) {
                throw new BizException(CREDENTIAL_INTEGRITY_VIOLATION, "invalid dek iv length");
            }
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(masterKey, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(contextAad(canonicalAad));
            return cipher.doFinal(Base64.getDecoder().decode(encryptedDek.getCiphertextBase64()));
        } catch (BizException ex) {
            throw ex;
        } catch (AEADBadTagException ex) {
            throw new BizException(CREDENTIAL_INTEGRITY_VIOLATION, "dek integrity check failed", ex);
        } catch (Exception ex) {
            throw new BizException(CREDENTIAL_INTERNAL_ERROR, "dek decrypt failed", ex);
        }
    }

    private byte[] resolveMasterKey(String configuredMasterKeyBase64, Supplier<String> envLookup) {
        String value = firstText(configuredMasterKeyBase64, envLookup == null ? null : envLookup.get());
        if (!StringUtils.hasText(value) || isRequiredPlaceholder(value)) {
            throw new IllegalStateException(
                    "geo.cookie.master-key-base64 (or GEO_COOKIE_MASTER_KEY_BASE64) is required. "
                            + "Generate via: openssl rand -base64 32. Refuse to start without explicit configuration."
            );
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("geo.cookie.master-key-base64 is not valid Base64", ex);
        }
        if (decoded.length != 32) {
            throw new IllegalStateException(
                    "geo.cookie.master-key-base64 must decode to exactly 32 bytes (got " + decoded.length + ")"
            );
        }
        return decoded;
    }

    private boolean isRequiredPlaceholder(String value) {
        return "__REQUIRED_GEO_COOKIE_MASTER_KEY_BASE64__".equals(value);
    }

    private String firstText(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first.trim();
        }
        return StringUtils.hasText(second) ? second.trim() : null;
    }

    private byte[] contextAad(String canonicalAad) {
        return (canonicalAad == null ? "" : canonicalAad).getBytes(StandardCharsets.UTF_8);
    }

    private String b64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
