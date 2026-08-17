package com.huanjing.geo.module.content.douyin;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.config.DouyinOpenPlatformProperties;
import com.huanjing.geo.module.content.vo.DouyinAuthUrlVO;
import com.huanjing.geo.module.content.vo.DouyinCapabilityVO;
import com.huanjing.geo.module.content.vo.DouyinReadinessCheckVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

/**
 * Compatibility facade for the retired Douyin OAuth integration.
 *
 * <p>Douyin publishing is now performed exclusively through a fingerprint-browser environment.
 * The legacy endpoints remain temporarily addressable so old clients receive an explicit 410
 * response instead of accidentally creating another OAuth-backed account.</p>
 */
@Service
@RequiredArgsConstructor
public class DouyinAuthorizationService {
    public static final String RETIRED_MESSAGE = "抖音 OAuth 已停用，请使用指纹浏览器账号完成登录和自动发布";

    private static final String PLATFORM = "douyin";
    private final DouyinOpenPlatformProperties properties;

    public DouyinAuthUrlVO buildAuthUrl(Long brandId, Long redirectArticleId) {
        throw retired();
    }

    public DouyinCapabilityVO capability() {
        return new DouyinCapabilityVO(
                true,
                "fingerprint_browser",
                null,
                false,
                null,
                "抖音 OAuth 已停用；当前统一使用指纹浏览器保持登录态并执行自动发布。",
                List.of(
                        new DouyinReadinessCheckVO(
                                "publish_channel",
                                "发布通道",
                                "ok",
                                "当前使用指纹浏览器自动发布"
                        ),
                        new DouyinReadinessCheckVO(
                                "login_credential",
                                "登录凭据",
                                "ok",
                                "登录状态由浏览器环境与 Cookie 凭据管理"
                        ),
                        new DouyinReadinessCheckVO(
                                "oauth_retired",
                                "OAuth 废弃状态",
                                "ok",
                                "OAuth 授权、令牌刷新和官方 API 发布均已停用"
                        )
                )
        );
    }

    public String handleCallback(String code, String state) {
        throw retired();
    }

    /**
     * Retained for old frontend callback links. It reports retirement and never represents a
     * successful authorization result.
     */
    public String errorRedirect(String errorCode, String errorMessage) {
        String base = StringUtils.hasText(properties.getFrontendCallbackUrl())
                ? properties.getFrontendCallbackUrl()
                : "/admin/content/execution";
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(base)
                .queryParam("platform", PLATFORM)
                .queryParam("douyinAuth", "oauth_retired")
                .queryParam("errorCode", "oauth_retired")
                .queryParam("errorMessage", RETIRED_MESSAGE);
        return builder.build().encode().toUriString();
    }

    private BizException retired() {
        return new BizException(410, RETIRED_MESSAGE, 410, null);
    }
}
