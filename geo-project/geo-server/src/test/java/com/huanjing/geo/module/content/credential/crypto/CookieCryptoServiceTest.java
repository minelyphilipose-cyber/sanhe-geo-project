package com.huanjing.geo.module.content.credential.crypto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.huanjing.geo.module.content.credential.CredentialErrorCodes.CREDENTIAL_INTEGRITY_VIOLATION;
import static com.huanjing.geo.module.content.credential.CredentialErrorCodes.CREDENTIAL_PAYLOAD_INVALID;

class CookieCryptoServiceTest {

    private static final String MASTER_KEY = Base64.getEncoder().encodeToString(
            "0123456789abcdef0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.UTF_8)
    );

    @Test
    void encryptAndDecryptRoundTrip() {
        CookieCryptoService service = newService();
        CookieCryptoService.CookieEncryptionContext context =
                new CookieCryptoService.CookieEncryptionContext(10L, 20L, "toutiao", 1);

        CookieCryptoService.EncryptedCookiePayload encrypted =
                service.encrypt("[{\"name\":\"sessionid\",\"value\":\"secret\"}]", context);

        assertEquals("AES-256-GCM", encrypted.cipherAlg());
        assertEquals("local-test", encrypted.masterKeyId());
        assertTrue(encrypted.cookiesCiphertext().length() > 20);
        assertEquals("[{\"name\":\"sessionid\",\"value\":\"secret\"}]", service.decrypt(encrypted, context));
    }

    @Test
    void decryptWithDifferentContextFails() {
        CookieCryptoService service = newService();
        CookieCryptoService.CookieEncryptionContext context =
                new CookieCryptoService.CookieEncryptionContext(10L, 20L, "toutiao", 1);
        CookieCryptoService.CookieEncryptionContext wrongContext =
                new CookieCryptoService.CookieEncryptionContext(10L, 21L, "toutiao", 1);

        CookieCryptoService.EncryptedCookiePayload encrypted =
                service.encrypt("[{\"name\":\"z_c0\",\"value\":\"secret\"}]", context);

        BizException ex = assertThrows(BizException.class, () -> service.decrypt(encrypted, wrongContext));
        assertEquals(CREDENTIAL_INTEGRITY_VIOLATION, ex.getCode());
        assertEquals("cookie integrity check failed", ex.getMessage());
    }

    @Test
    void localMasterKeyProviderRequiresExplicitKey() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> new LocalMasterKeyProvider("local-test", "", () -> null)
        );

        assertTrue(ex.getMessage().contains("required"));
    }

    @Test
    void decryptWithInvalidEncryptedDekPayloadFailsAsPayloadInvalid() {
        CookieCryptoService service = newService();
        CookieCryptoService.CookieEncryptionContext context =
                new CookieCryptoService.CookieEncryptionContext(10L, 20L, "toutiao", 1);
        CookieCryptoService.EncryptedCookiePayload encrypted =
                service.encrypt("[{\"name\":\"sessionid\",\"value\":\"secret\"}]", context);
        CookieCryptoService.EncryptedCookiePayload broken = new CookieCryptoService.EncryptedCookiePayload(
                encrypted.cipherAlg(),
                encrypted.aadContext(),
                encrypted.ivBase64(),
                encrypted.cookiesCiphertext(),
                "{not-json",
                encrypted.masterKeyId()
        );

        BizException ex = assertThrows(BizException.class, () -> service.decrypt(broken, context));
        assertEquals(CREDENTIAL_PAYLOAD_INVALID, ex.getCode());
    }

    private CookieCryptoService newService() {
        return new CookieCryptoService(new LocalMasterKeyProvider("local-test", MASTER_KEY), new ObjectMapper());
    }
}
