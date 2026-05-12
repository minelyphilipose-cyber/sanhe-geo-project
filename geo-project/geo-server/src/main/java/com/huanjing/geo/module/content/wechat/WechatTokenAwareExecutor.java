package com.huanjing.geo.module.content.wechat;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * Executes a WeChat MP operation with one access-token refresh retry.
 *
 * <p>Retry is strictly limited to {@link BizException} codes 40001 and 42001. Other failures,
 * including 5xx, timeouts, 61023, 45009 and 40164, are propagated immediately.</p>
 *
 * <p>Retry safety for non-idempotent operations depends on WeChat's 40001/42001 semantics:
 * when token validation fails, the request has not been actually executed.</p>
 */
@Component
@RequiredArgsConstructor
public class WechatTokenAwareExecutor {
    private final WechatAuthorizerTokenService tokenService;

    public <T> T execute(SelfMediaAccount account, Function<String, T> operation) {
        String accessToken = tokenService.getAccessToken(account);
        try {
            return operation.apply(accessToken);
        } catch (BizException ex) {
            if (!isAccessTokenExpired(ex.getCode())) {
                throw ex;
            }
            tokenService.evictAccessToken(account);
            String refreshedToken = tokenService.getAccessToken(account);
            return operation.apply(refreshedToken);
        }
    }

    private boolean isAccessTokenExpired(int code) {
        return code == 40001 || code == 42001;
    }
}
