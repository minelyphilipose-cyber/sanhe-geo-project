package com.huanjing.geo.module.system.service;

import com.huanjing.geo.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class MpCredentialCipherServiceTest {

    private MpCredentialCipherService service;

    @BeforeEach
    void setUp() {
        service = new MpCredentialCipherService();
        ReflectionTestUtils.setField(service, "aesSecret", "test-mp-cipher-key1");
        service.init();
    }

    @Test
    void decrypt_nullOrBlank_throwsIae() {
        assertThrows(IllegalArgumentException.class, () -> service.decrypt(null));
        assertThrows(IllegalArgumentException.class, () -> service.decrypt("   "));
    }

    @Test
    void decrypt_notEncrypted_throwsIse() {
        assertThrows(IllegalStateException.class, () -> service.decrypt("plain-secret"));
    }

    @Test
    void decrypt_notEncrypted_messageDoesNotEchoInput() {
        String input = "unique-plain-token-xyzzy-12345";
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.decrypt(input));
        assertFalse(ex.getMessage() != null && ex.getMessage().contains(input), "message must not echo input");
    }

    @Test
    void decrypt_badCipher_throwsBizWithFixedMessage() {
        String longCipher = "ENC:deadbeefdeadbeefdeadbeefdeadbeef";
        BizException ex = assertThrows(BizException.class, () -> service.decrypt(longCipher));
        assertEquals(500, ex.getCode());
        assertEquals("credential decrypt failed", ex.getMessage());
        String msg = ex.getMessage();
        assertNotNull(msg);
        assertFalse(msg.contains(longCipher), "message must not contain cipher material");
    }

    @Test
    void roundTrip_encrypted() {
        String plain = "my-secret-value";
        String enc = service.encryptForStorage(plain);
        assertNotNull(enc);
        assertTrue(enc.startsWith("ENC:"));
        assertEquals(plain, service.decrypt(enc));
    }
}
