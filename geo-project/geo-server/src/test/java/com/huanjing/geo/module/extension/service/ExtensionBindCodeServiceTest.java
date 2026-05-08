package com.huanjing.geo.module.extension.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.audit.AuditMode;
import com.huanjing.geo.module.audit.AuditResult;
import com.huanjing.geo.module.customer.access.BrandAccessAction;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.extension.config.ExtensionProperties;
import com.huanjing.geo.module.extension.dto.BindCodeCreateResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.huanjing.geo.module.extension.ExtensionErrorCodes.BIND_CODE_INVALID;
import static com.huanjing.geo.module.extension.ExtensionErrorCodes.BIND_RATE_LIMIT_EXCEEDED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExtensionBindCodeServiceTest {

    private ExtensionProperties properties;
    private ExtensionRedisStore redisStore;
    private BrandAccessService brandAccessService;
    private ExtensionAuditSupport auditSupport;
    private ExtensionBindCodeService service;

    @BeforeEach
    void setUp() {
        properties = new ExtensionProperties();
        redisStore = mock(ExtensionRedisStore.class);
        brandAccessService = mock(BrandAccessService.class);
        auditSupport = mock(ExtensionAuditSupport.class);
        service = new ExtensionBindCodeService(properties, redisStore, brandAccessService, new ObjectMapper(), auditSupport);
    }

    @Test
    void createStoresNormalizedCodePayload() {
        BindCodeCreateResponse response = service.create(10L, 99L);

        assertEquals(9, response.code().length());
        verify(brandAccessService).requireBrandAccess(10L, 99L, BrandAccessAction.MANAGE);
        verify(redisStore).set(any(), any(), any());
    }

    @Test
    void consumeRejectsCrossBrandUse() throws Exception {
        String payload = new ObjectMapper().writeValueAsString(
                new ExtensionBindCodeService.BindCodePayload(10L, 99L, Long.MAX_VALUE)
        );
        when(redisStore.incrementWithTtl(any(), any())).thenReturn(1L);
        when(redisStore.getAndDelete("bind_code:ABCDEFGH")).thenReturn(payload);

        BizException ex = assertThrows(BizException.class, () -> service.consume("ABCD-EFGH", 11L, "127.0.0.1"));

        assertEquals(BIND_CODE_INVALID, ex.getCode());
    }

    @Test
    void brandRateLimitRejectsSixthAttempt() {
        when(redisStore.incrementWithTtl(any(), any())).thenReturn(6L);

        BizException ex = assertThrows(BizException.class, () -> service.consume("ABCD-EFGH", 10L, "127.0.0.1"));

        assertEquals(BIND_RATE_LIMIT_EXCEEDED, ex.getCode());
        verify(auditSupport).record(
                eq("BIND_RATE_LIMIT_EXCEEDED"),
                eq(AuditResult.DENIED),
                eq(AuditMode.SYNC),
                eq(true),
                eq(null),
                eq(10L),
                eq(null),
                eq(null),
                eq(null),
                eq("BIND_CODE"),
                eq(null),
                eq(String.valueOf(BIND_RATE_LIMIT_EXCEEDED)),
                eq("RATE_LIMIT_BRAND"),
                any()
        );
    }

    @Test
    void ipRateLimitRejectsTwentyFirstAttempt() {
        when(redisStore.incrementWithTtl(any(), any())).thenReturn(1L, 21L);

        BizException ex = assertThrows(BizException.class, () -> service.consume("ABCD-EFGH", 10L, "127.0.0.1"));

        assertEquals(BIND_RATE_LIMIT_EXCEEDED, ex.getCode());
    }

    @Test
    void consumeChecksManageAccessAfterSuccessfulPayload() throws Exception {
        String payload = new ObjectMapper().writeValueAsString(
                new ExtensionBindCodeService.BindCodePayload(10L, 99L, Long.MAX_VALUE)
        );
        when(redisStore.incrementWithTtl(any(), any())).thenReturn(1L);
        when(redisStore.getAndDelete("bind_code:ABCDEFGH")).thenReturn(payload);

        service.consume("ABCD-EFGH", 10L, "127.0.0.1");

        verify(brandAccessService).requireBrandAccess(eq(10L), eq(99L), eq(BrandAccessAction.MANAGE));
    }

    @Test
    void consumeCanResolveBrandIdFromBindCodePayload() throws Exception {
        String payload = new ObjectMapper().writeValueAsString(
                new ExtensionBindCodeService.BindCodePayload(10L, 99L, Long.MAX_VALUE)
        );
        when(redisStore.incrementWithTtl(any(), any())).thenReturn(1L);
        when(redisStore.getAndDelete("bind_code:ABCDEFGH")).thenReturn(payload);

        ExtensionBindCodeService.BindCodePayload result = service.consume("ABCD-EFGH", null, "127.0.0.1");

        assertEquals(10L, result.brandId());
        assertEquals(99L, result.operatorId());
        verify(brandAccessService).requireBrandAccess(eq(10L), eq(99L), eq(BrandAccessAction.MANAGE));
    }
}
