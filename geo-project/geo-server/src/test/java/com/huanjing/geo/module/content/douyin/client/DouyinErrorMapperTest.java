package com.huanjing.geo.module.content.douyin.client;

import com.huanjing.geo.module.content.douyin.client.exception.DouyinAuthException;
import com.huanjing.geo.module.content.douyin.client.exception.DouyinClientException;
import com.huanjing.geo.module.content.douyin.client.exception.DouyinErrorMapper;
import com.huanjing.geo.module.content.douyin.client.exception.DouyinPermissionException;
import com.huanjing.geo.module.content.douyin.client.exception.DouyinRateLimitException;
import com.huanjing.geo.module.content.douyin.client.exception.DouyinServerException;
import com.huanjing.geo.module.content.douyin.client.exception.DouyinValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DouyinErrorMapperTest {

    @Test
    void mapsAuthCodesAsNonRetryable() {
        DouyinClientException ex = DouyinErrorMapper.toException(200, 28001008L, "expired", "log", "{}");

        assertInstanceOf(DouyinAuthException.class, ex);
        assertEquals(28001008L, ex.getErrorCode());
        assertFalse(ex.isRetryable());
    }

    @Test
    void mapsPermissionCodesAsNonRetryable() {
        DouyinClientException ex = DouyinErrorMapper.toException(200, 28001018L, "missing scope", "log", "{}");

        assertInstanceOf(DouyinPermissionException.class, ex);
        assertFalse(ex.isRetryable());
    }

    @Test
    void mapsRateLimitCodesAsNonRetryableUntilQuotaResets() {
        DouyinClientException ex = DouyinErrorMapper.toException(200, 28003017L, "quota", "log", "{}");

        assertInstanceOf(DouyinRateLimitException.class, ex);
        assertFalse(ex.isRetryable());
    }

    @Test
    void mapsValidationCodesAsNonRetryable() {
        DouyinClientException ex = DouyinErrorMapper.toException(200, 2114001L, "too long", "log", "{}");

        assertInstanceOf(DouyinValidationException.class, ex);
        assertFalse(ex.isRetryable());
    }

    @Test
    void mapsServerCodesAsRetryable() {
        DouyinClientException ex = DouyinErrorMapper.toException(200, 2100004L, "busy", "log", "{}");

        assertInstanceOf(DouyinServerException.class, ex);
        assertTrue(ex.isRetryable());
    }

    @Test
    void mapsUnknownCodesToGenericNonRetryableException() {
        DouyinClientException ex = DouyinErrorMapper.toException(200, 999999L, "unknown", "log", "{}");

        assertEquals(DouyinClientException.class, ex.getClass());
        assertEquals(999999L, ex.getErrorCode());
        assertFalse(ex.isRetryable());
    }

    @Test
    void mapsNullCodesToGenericNonRetryableException() {
        DouyinClientException ex = DouyinErrorMapper.toException(500, null, "network", null, null);

        assertEquals(DouyinClientException.class, ex.getClass());
        assertEquals(500, ex.getHttpStatus());
        assertFalse(ex.isRetryable());
    }
}
