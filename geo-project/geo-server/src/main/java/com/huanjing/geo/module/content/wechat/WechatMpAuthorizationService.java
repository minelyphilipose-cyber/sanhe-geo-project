package com.huanjing.geo.module.content.wechat;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.config.WechatOpenPlatformProperties;
import com.huanjing.geo.module.content.constant.SelfMediaAccountIdentity;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.service.WechatMenuConfigService;
import com.huanjing.geo.module.content.vo.WechatMpAuthUrlVO;
import com.huanjing.geo.module.system.service.MpCredentialCipherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.time.Duration;

@Slf4j

@Service
@RequiredArgsConstructor
public class WechatMpAuthorizationService {
    private static final String AUTH_PAGE = "https://mp.weixin.qq.com/cgi-bin/componentloginpage";
    private static final String STATE_KEY_PREFIX = "wechat:auth_state:";
    private static final Duration STATE_TTL = Duration.ofMinutes(10);

    private final WechatOpenPlatformProperties properties;
    private final WechatComponentAccessTokenService componentAccessTokenService;
    private final WechatOpenPlatformClient openPlatformClient;
    private final SelfMediaAccountMapper selfMediaAccountMapper;
    private final MpCredentialCipherService cipherService;
    private final WechatFuncInfoValidator funcInfoValidator;
    private final WechatAuthorizerTokenService authorizerTokenService;
    private final StringRedisTemplate redisTemplate;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Autowired(required = false)
    private WechatMenuConfigService wechatMenuConfigService;

    public WechatMpAuthUrlVO buildAuthUrl(Long brandId, Long redirectArticleId) {
        String componentAppid = require(properties.getComponentAppid(), "wechat component appid missing");
        String componentToken = componentAccessTokenService.getAccessToken();
        WechatOpenPlatformClient.PreAuthCodeResult preAuth =
                openPlatformClient.createPreAuthCode(componentToken, componentAppid);
        String callback = require(properties.getBackendAuthCallbackUrl(), "wechat backend auth callback url missing");
        String state = createState(brandId, redirectArticleId);
        String callbackWithState = UriComponentsBuilder.fromUriString(callback)
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString();
        String authUrl = UriComponentsBuilder.fromUriString(AUTH_PAGE)
                .queryParam("component_appid", componentAppid)
                .queryParam("pre_auth_code", preAuth.preAuthCode())
                .queryParam("redirect_uri", callbackWithState)
                .queryParam("auth_type", properties.getAuthType())
                .build()
                .encode()
                .toUriString();
        return new WechatMpAuthUrlVO(authUrl, preAuth.expiresIn());
    }

    public String handleCallback(String authCode, String state) {
        State decoded = consumeState(state);
        String componentAppid = require(properties.getComponentAppid(), "wechat component appid missing");
        SelfMediaAccount account = saveOrUpdateAuthorization(decoded.brandId(), componentAppid, authCode);
        initializeMenuBestEffort(account);
        String status = "active".equals(account.getStatus()) ? "success" : "permission_missing";
        return redirectUrl(decoded.brandId(), decoded.redirectArticleId(), account.getPlatformAccountId(), account.getStatus(), status);
    }

    public SelfMediaAccount saveOrUpdateAuthorization(String componentAppid, String authorizationCode) {
        return saveOrUpdateAuthorization(null, componentAppid, authorizationCode);
    }

    public SelfMediaAccount saveOrUpdateAuthorization(Long brandId, String componentAppid, String authorizationCode) {
        componentAppid = require(componentAppid, "wechat component appid missing");
        authorizationCode = require(authorizationCode, "wechat authorization code missing");
        String componentToken = componentAccessTokenService.getAccessToken();
        WechatOpenPlatformClient.QueryAuthResult queryAuth =
                openPlatformClient.queryAuth(componentToken, componentAppid, authorizationCode);
        WechatOpenPlatformClient.AuthorizerInfoResult info =
                openPlatformClient.getAuthorizerInfo(componentToken, componentAppid, queryAuth.authorizerAppid());
        return saveAccount(brandId, queryAuth, info);
    }

    private SelfMediaAccount saveAccount(Long brandId,
                                         WechatOpenPlatformClient.QueryAuthResult queryAuth,
                                         WechatOpenPlatformClient.AuthorizerInfoResult info) {
        SelfMediaAccount account = selfMediaAccountMapper.selectByPlatformAccountIncludingDeleted(
                "wechat_mp", queryAuth.authorizerAppid());
        LocalDateTime now = LocalDateTime.now();
        if (account == null) {
            account = new SelfMediaAccount();
            account.setCreatedAt(now);
        }
        if (brandId != null) {
            account.setBrandId(brandId);
        }
        account.setPlatform("wechat_mp");
        account.setAccountName(StringUtils.hasText(info.accountName()) ? info.accountName() : queryAuth.authorizerAppid());
        account.setAccountIdentity(SelfMediaAccountIdentity.ENTERPRISE);
        account.setPlatformAccountId(queryAuth.authorizerAppid());
        account.setRefreshTokenCipher(cipherService.encryptForStorage(queryAuth.authorizerRefreshToken()));
        account.setCredentialKeyVersion("v1");
        String funcInfo = StringUtils.hasText(queryAuth.funcInfoJson()) && !"null".equals(queryAuth.funcInfoJson())
                ? queryAuth.funcInfoJson()
                : info.funcInfoJson();
        account.setScopeJson(funcInfo);
        account.setAvatarUrl(info.headImg());
        account.setQrcodeUrl(info.qrcodeUrl());
        if (funcInfoValidator.hasDraftPermissions(funcInfo)) {
            account.setStatus("active");
            account.setLastAuthError(null);
        } else {
            account.setStatus("disabled");
            account.setLastAuthError("wechat permission missing: " + funcInfoValidator.missingRequired(funcInfo));
        }
        account.setLastAuthCheckedAt(now);
        account.setUpdatedAt(now);
        if (account.getId() == null) {
            selfMediaAccountMapper.insert(account);
        } else if (account.getDeletedAt() != null) {
            selfMediaAccountMapper.restoreWechatAuthorization(account, now);
        } else {
            selfMediaAccountMapper.updateById(account);
        }
        authorizerTokenService.evictAccessToken(account);
        return account;
    }

    private void initializeMenuBestEffort(SelfMediaAccount account) {
        try {
            if (wechatMenuConfigService != null && account != null && account.getId() != null) {
                wechatMenuConfigService.initializeMenuAfterAuthorization(account.getId());
            }
        } catch (Exception ex) {
            log.warn("WeChat menu init after authorization failed accountId={} appid={}",
                    account == null ? null : account.getId(),
                    account == null ? null : account.getPlatformAccountId(),
                    ex);
        }
    }

    public String errorRedirect(String status) {
        String base = require(properties.getFrontendCallbackUrl(), "wechat frontend callback url missing");
        return UriComponentsBuilder.fromUriString(base)
                .queryParam("distribution", "wechat_mp")
                .queryParam("wechatAuth", status)
                .build()
                .encode()
                .toUriString();
    }

    private String redirectUrl(Long brandId, Long articleId, String authorizerAppid, String accountStatus, String status) {
        String base = require(properties.getFrontendCallbackUrl(), "wechat frontend callback url missing");
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(base)
                .queryParam("distribution", "wechat_mp")
                .queryParam("wechatAuth", status)
                .queryParam("brandId", brandId)
                .queryParam("wechatStatus", accountStatus)
                .queryParam("authorizerAppid", authorizerAppid);
        if (articleId != null) {
            builder.queryParam("articleId", articleId);
        }
        return builder.build().encode().toUriString();
    }

    private String createState(Long brandId, Long articleId) {
        if (brandId == null) {
            throw new BizException(400, "brandId required");
        }
        try {
            String state = UUID.randomUUID().toString().replace("-", "");
            AuthState payload = new AuthState(brandId, articleId, OffsetDateTime.now().toString());
            redisTemplate.opsForValue().set(
                    STATE_KEY_PREFIX + state,
                    objectMapper.writeValueAsString(payload),
                    STATE_TTL
            );
            return state;
        } catch (Exception ex) {
            throw new BizException(500, "wechat auth state create failed");
        }
    }

    private State consumeState(String state) {
        if (!StringUtils.hasText(state)) {
            throw new BizException(400, "wechat auth state missing");
        }
        String key = STATE_KEY_PREFIX + state;
        String raw = redisTemplate.opsForValue().get(key);
        redisTemplate.delete(key);
        if (!StringUtils.hasText(raw)) {
            throw new BizException(400, "wechat auth state invalid");
        }
        try {
            AuthState payload = objectMapper.readValue(raw, AuthState.class);
            return new State(payload.brandId(), payload.redirectArticleId());
        } catch (Exception ex) {
            log.warn("Failed to parse WeChat auth state payload");
            throw new BizException(400, "wechat auth state invalid");
        }
    }

    private String require(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(500, message);
        }
        return value;
    }

    private record State(Long brandId, Long redirectArticleId) {
    }

    private record AuthState(Long brandId, Long redirectArticleId, String createdAt) {
    }
}
