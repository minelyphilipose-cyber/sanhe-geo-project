package com.huanjing.geo.module.system.service;

import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlatformCredentialServiceTest {

    @Test
    void malformedExplicitEnvironmentReferenceIsRejected() throws Exception {
        PlatformCredentialService service = new PlatformCredentialService(null);

        assertNull(service.resolveByRef("env://INVALID-ENV-NAME"));
        assertNull(service.resolveByRef("env://"));
    }

    @Test
    void strictPrimaryReferenceNeverFallsBackToStoredApiKey() {
        PlatformCredentialService service = initializedService();

        assertNull(service.resolvePrimaryCredentialStrict(
                "env://MODEL_DIAGNOSTIC_MISSING_KEY_7F3A9C", "database-secret"));
    }

    @Test
    void strictPrimaryDatabaseCredentialMustDecryptSuccessfully() {
        PlatformCredentialService service = initializedService();
        String encrypted = service.encryptForStorage("database-secret");

        assertEquals("database-secret", service.resolvePrimaryCredentialStrict(null, encrypted));
        assertNull(service.resolvePrimaryCredentialStrict(null, "ENC:not-valid-ciphertext"));
    }

    @Test
    void encryptedPlatformCredentialCanBeRotatedAndResolvedThroughDatabaseReference() {
        AiPlatformConfigMapper mapper = mock(AiPlatformConfigMapper.class);
        PlatformCredentialService service = initializedService(mapper);
        AiPlatformConfig config = new AiPlatformConfig();
        config.setId(7L);
        config.setApiKey(service.encryptForStorage("rotated-secret"));
        when(mapper.selectById(7L)).thenReturn(config);

        assertEquals("rotated-secret", service.resolveCredential(
                PlatformCredentialService.databaseCredentialRef(7L), null));
    }

    private PlatformCredentialService initializedService() {
        return initializedService(null);
    }

    private PlatformCredentialService initializedService(AiPlatformConfigMapper mapper) {
        PlatformCredentialService service = new PlatformCredentialService(mapper);
        ReflectionTestUtils.setField(service, "aesSecret", "diagnostic-test-key");
        service.init();
        return service;
    }
}
