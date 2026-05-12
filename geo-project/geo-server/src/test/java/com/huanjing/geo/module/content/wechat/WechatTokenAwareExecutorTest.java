package com.huanjing.geo.module.content.wechat;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WechatTokenAwareExecutorTest {

    private final WechatAuthorizerTokenService tokenService = mock(WechatAuthorizerTokenService.class);
    private final WechatTokenAwareExecutor executor = new WechatTokenAwareExecutor(tokenService);
    private final SelfMediaAccount account = account();

    @Test
    void execute_success_doesNotRetry() {
        when(tokenService.getAccessToken(account)).thenReturn("token-1");

        String result = executor.execute(account, token -> "ok:" + token);

        assertEquals("ok:token-1", result);
        verify(tokenService, never()).evictAccessToken(account);
    }

    @Test
    void execute_40001_evictsAndRetriesOnce() {
        when(tokenService.getAccessToken(account)).thenReturn("token-1", "token-2");
        AtomicInteger calls = new AtomicInteger();

        String result = executor.execute(account, token -> {
            if (calls.incrementAndGet() == 1) {
                throw new BizException(40001, "invalid credential");
            }
            return "ok:" + token;
        });

        assertEquals("ok:token-2", result);
        assertEquals(2, calls.get());
        verify(tokenService).evictAccessToken(account);
    }

    @Test
    void execute_42001_evictsAndRetriesOnce() {
        when(tokenService.getAccessToken(account)).thenReturn("token-1", "token-2");
        AtomicInteger calls = new AtomicInteger();

        String result = executor.execute(account, token -> {
            if (calls.incrementAndGet() == 1) {
                throw new BizException(42001, "token expired");
            }
            return "ok:" + token;
        });

        assertEquals("ok:token-2", result);
        assertEquals(2, calls.get());
        verify(tokenService).evictAccessToken(account);
    }

    @Test
    void execute_5xx_doesNotRetry() {
        when(tokenService.getAccessToken(account)).thenReturn("token-1");
        BizException expected = new BizException(500, "wechat server error");

        BizException actual = assertThrows(BizException.class,
                () -> executor.execute(account, token -> {
                    throw expected;
                }));

        assertSame(expected, actual);
        verify(tokenService, never()).evictAccessToken(account);
    }

    @Test
    void execute_timeoutRuntimeException_doesNotRetry() {
        when(tokenService.getAccessToken(account)).thenReturn("token-1");
        RuntimeException expected = new RuntimeException("timeout");

        RuntimeException actual = assertThrows(RuntimeException.class,
                () -> executor.execute(account, token -> {
                    throw expected;
                }));

        assertSame(expected, actual);
        verify(tokenService, never()).evictAccessToken(account);
    }

    @Test
    void execute_40001Then61023_doesNotRetryAgain() {
        when(tokenService.getAccessToken(account)).thenReturn("token-1", "token-2");
        AtomicInteger calls = new AtomicInteger();

        BizException actual = assertThrows(BizException.class,
                () -> executor.execute(account, token -> {
                    if (calls.incrementAndGet() == 1) {
                        throw new BizException(40001, "invalid credential");
                    }
                    throw new BizException(61023, "refresh token invalid");
                }));

        assertEquals(61023, actual.getCode());
        assertEquals(2, calls.get());
        verify(tokenService).evictAccessToken(account);
    }

    @Test
    void execute_45009_doesNotRetry() {
        when(tokenService.getAccessToken(account)).thenReturn("token-1");

        BizException actual = assertThrows(BizException.class,
                () -> executor.execute(account, token -> {
                    throw new BizException(45009, "rate limited");
                }));

        assertEquals(45009, actual.getCode());
        verify(tokenService, never()).evictAccessToken(account);
    }

    private SelfMediaAccount account() {
        SelfMediaAccount account = new SelfMediaAccount();
        account.setId(1L);
        account.setPlatformAccountId("wx-authorizer");
        return account;
    }
}
