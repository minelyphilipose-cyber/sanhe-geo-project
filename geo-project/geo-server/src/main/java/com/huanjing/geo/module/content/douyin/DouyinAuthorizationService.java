package com.huanjing.geo.module.content.douyin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.config.DouyinClientProperties;
import com.huanjing.geo.module.content.config.DouyinFeatureProperties;
import com.huanjing.geo.module.content.config.DouyinOpenPlatformProperties;
import com.huanjing.geo.module.content.constant.SelfMediaAccountIdentity;
import com.huanjing.geo.module.content.douyin.client.DouyinClient;
import com.huanjing.geo.module.content.douyin.client.dto.DouyinCodeTokenRequest;
import com.huanjing.geo.module.content.douyin.client.dto.DouyinTokenResponse;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.vo.DouyinCapabilityVO;
import com.huanjing.geo.module.content.vo.DouyinAuthUrlVO;
import com.huanjing.geo.module.content.vo.DouyinReadinessCheckVO;
import com.huanjing.geo.module.system.service.MpCredentialCipherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DouyinAuthorizationService {
    private static final String PLATFORM = "douyin";
    private static final String STATE_KEY_PREFIX = "douyin:auth_state:";
    private static final Duration STATE_TTL = Duration.ofMinutes(10);

    private final DouyinOpenPlatformProperties properties;
    private final DouyinFeatureProperties featureProperties;
    private final DouyinClientProperties clientProperties;
    private final DouyinClient douyinClient;
    private final SelfMediaAccountMapper selfMediaAccountMapper;
    private final MpCredentialCipherService cipherService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public DouyinAuthUrlVO buildAuthUrl(Long brandId, Long redirectArticleId) {
        String clientKey = require(properties.getClientKey(), "douyin client key missing");
        String callback = require(properties.getAuthCallbackUrl(), "douyin auth callback url missing");
        String authPage = require(properties.getAuthPageUrl(), "douyin auth page url missing");
        String state = createState(brandId, redirectArticleId);
        String authUrl = UriComponentsBuilder.fromUriString(authPage)
                .queryParam("client_key", clientKey)
                .queryParam("response_type", "code")
                .queryParam("scope", requiredScopeParam())
                .queryParam("redirect_uri", UriUtils.encode(callback, StandardCharsets.UTF_8))
                .queryParam("state", state)
                .build(true)
                .toUriString();
        return new DouyinAuthUrlVO(authUrl, (int) STATE_TTL.toSeconds());
    }

    public DouyinCapabilityVO capability() {
        boolean enabled = featureProperties.getImageText() != null
                && featureProperties.getImageText().isEnabled();
        boolean configuredLiveVerificationBlocked = featureProperties.getImageText() != null
                && featureProperties.getImageText().isLiveVerificationBlocked();
        boolean realMode = "real".equalsIgnoreCase(clientProperties.getMode());
        boolean liveVerificationBlocked = enabled && realMode && configuredLiveVerificationBlocked;
        return new DouyinCapabilityVO(
                enabled,
                clientProperties.getMode(),
                enabled ? null : "feature flag disabled",
                liveVerificationBlocked,
                liveVerificationBlocked ? featureProperties.getImageText().getLiveVerificationReason() : null,
                douyinCapabilityDescription(liveVerificationBlocked),
                readinessChecks(enabled, realMode, liveVerificationBlocked)
        );
    }

    private List<DouyinReadinessCheckVO> readinessChecks(boolean enabled, boolean realMode, boolean liveVerificationBlocked) {
        List<DouyinReadinessCheckVO> checks = new ArrayList<>();
        checks.add(configCheck("client_key", "Client Key", properties.getClientKey(), "请在 .env 配置 DOUYIN_CLIENT_KEY"));
        checks.add(configCheck("client_secret", "Client Secret", properties.getClientSecret(), "请在 .env 配置 DOUYIN_CLIENT_SECRET"));
        checks.add(httpsCheck("auth_callback_url", "授权回调 URL", properties.getAuthCallbackUrl(), "请配置 HTTPS 授权回调 URL"));
        checks.add(httpsCheck("frontend_callback_url", "前端回跳 URL", properties.getFrontendCallbackUrl(), "请配置 HTTPS 前端回跳 URL"));
        checks.add(httpsCheck("webhook_url", "Webhook URL", properties.getWebhookUrl(), "请配置 DOUYIN_WEBHOOK_URL=https://域名/api/douyin/open-platform/webhooks"));
        checks.add(scopeCheck());
        checks.add(new DouyinReadinessCheckVO(
                "client_mode",
                "客户端模式",
                realMode ? "ok" : "warning",
                realMode ? "当前使用真实抖音 Open API" : "当前仍为 mock 模式，认证通过后需设置 DOUYIN_CLIENT_MODE=real"
        ));
        checks.add(new DouyinReadinessCheckVO(
                "image_text_enabled",
                "图文发布开关",
                enabled ? "ok" : "warning",
                enabled ? "图文发布开关已开启" : "认证通过后需设置 DOUYIN_IMAGE_TEXT_ENABLED=true"
        ));
        checks.add(new DouyinReadinessCheckVO(
                "live_verification",
                "上线联调阻断",
                liveVerificationBlocked ? "warning" : "ok",
                liveVerificationBlocked ? "当前仍阻断真实联调，认证通过后需设置 DOUYIN_IMAGE_TEXT_LIVE_VERIFICATION_BLOCKED=false" : "真实联调阻断已关闭"
        ));
        return checks;
    }

    private DouyinReadinessCheckVO configCheck(String code, String label, String value, String missingMessage) {
        if (StringUtils.hasText(value)) {
            return new DouyinReadinessCheckVO(code, label, "ok", label + " 已配置");
        }
        return new DouyinReadinessCheckVO(code, label, "missing", missingMessage);
    }

    private DouyinReadinessCheckVO httpsCheck(String code, String label, String value, String missingMessage) {
        if (!StringUtils.hasText(value)) {
            return new DouyinReadinessCheckVO(code, label, "missing", missingMessage);
        }
        if (!value.startsWith("https://")) {
            return new DouyinReadinessCheckVO(code, label, "warning", label + " 必须使用 HTTPS");
        }
        return new DouyinReadinessCheckVO(code, label, "ok", label + " 已配置 HTTPS");
    }

    private DouyinReadinessCheckVO scopeCheck() {
        List<String> scopes = properties.getRequiredScopes() == null ? List.of() : properties.getRequiredScopes();
        if (scopes.contains("video.create.bind")) {
            return new DouyinReadinessCheckVO("required_scopes", "授权 Scope", "ok", "已包含 video.create.bind");
        }
        return new DouyinReadinessCheckVO("required_scopes", "授权 Scope", "missing", "请确认 required-scopes 包含 video.create.bind");
    }

    private String douyinCapabilityDescription(boolean liveVerificationBlocked) {
        if (liveVerificationBlocked) {
            return "当前域名备案及抖音开放平台审核未完成，真实图文提交/审核联调暂不可用；先保留配置、授权和 mock 链路验证。";
        }
        if (!"real".equalsIgnoreCase(clientProperties.getMode())) {
            return "抖音图文 mock 链路已开放，可用于账号授权、图片选择、提交、失败映射和审核状态流程验证。";
        }
        return "抖音图文支持 Open API 自动提交；需要 OAuth 授权账号、1-30 张 JPG/PNG 图片素材，文案不超过 1000 字。";
    }

    public String handleCallback(String code, String state) {
        State decoded = consumeState(state);
        DouyinTokenResponse token = douyinClient.exchangeCodeForToken(DouyinCodeTokenRequest.builder()
                .clientKey(require(properties.getClientKey(), "douyin client key missing"))
                .clientSecret(require(properties.getClientSecret(), "douyin client secret missing"))
                .code(require(code, "douyin auth code missing"))
                .build());
        SelfMediaAccount account = saveAccount(decoded.brandId(), token);
        String authStatus = "active".equals(account.getStatus()) ? "success" : "scope_missing";
        return redirectUrl(
                decoded.brandId(),
                decoded.redirectArticleId(),
                account.getPlatformAccountId(),
                account.getStatus(),
                authStatus
        );
    }

    public String errorRedirect(String errorCode, String errorMessage) {
        String base = require(properties.getFrontendCallbackUrl(), "douyin frontend callback url missing");
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(base)
                .queryParam("platform", PLATFORM)
                .queryParam("douyinAuth", "callback_failed");
        if (StringUtils.hasText(errorCode)) {
            builder.queryParam("errorCode", errorCode);
        }
        if (StringUtils.hasText(errorMessage)) {
            builder.queryParam("errorMessage", errorMessage);
        }
        return builder.build().encode().toUriString();
    }

    private SelfMediaAccount saveAccount(Long brandId, DouyinTokenResponse token) {
        String openId = require(token.getOpenId(), "douyin open_id missing");
        SelfMediaAccount account = selfMediaAccountMapper.selectOne(new LambdaQueryWrapper<SelfMediaAccount>()
                .eq(SelfMediaAccount::getPlatform, PLATFORM)
                .eq(SelfMediaAccount::getPlatformAccountId, openId)
                .last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        if (account == null) {
            account = new SelfMediaAccount();
            account.setCreatedAt(now);
        }
        account.setBrandId(brandId);
        account.setPlatform(PLATFORM);
        account.setPlatformAccountId(openId);
        account.setAccountName("Douyin " + openId);
        account.setAccountIdentity(SelfMediaAccountIdentity.PERSONAL);
        account.setAccessTokenCipher(cipherService.encryptForStorage(token.getAccessToken()));
        account.setRefreshTokenCipher(cipherService.encryptForStorage(token.getRefreshToken()));
        account.setCredentialKeyVersion("v1");
        account.setAccessTokenExpiresAt(plusSeconds(now, token.getExpiresIn()));
        account.setRefreshTokenExpiresAt(plusSeconds(now, token.getRefreshExpiresIn()));
        account.setScopeJson(scopeJson(token.getScope()));
        account.setExtraJson(extraJson(openId));
        if (hasRequiredScopes(token.getScope())) {
            account.setStatus("active");
            account.setLastAuthError(null);
        } else {
            account.setStatus("disabled");
            account.setLastAuthError("douyin scope missing: " + requiredScopeParam());
        }
        account.setLastAuthCheckedAt(now);
        account.setUpdatedAt(now);
        if (account.getId() == null) {
            selfMediaAccountMapper.insert(account);
        } else {
            selfMediaAccountMapper.updateById(account);
        }
        return account;
    }

    private String redirectUrl(Long brandId,
                               Long articleId,
                               String platformAccountId,
                               String accountStatus,
                               String authStatus) {
        String base = require(properties.getFrontendCallbackUrl(), "douyin frontend callback url missing");
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(base)
                .queryParam("platform", PLATFORM)
                .queryParam("douyinAuth", authStatus)
                .queryParam("brandId", brandId)
                .queryParam("accountStatus", accountStatus)
                .queryParam("platformAccountId", platformAccountId);
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
            throw new BizException(500, "douyin auth state create failed");
        }
    }

    private State consumeState(String state) {
        if (!StringUtils.hasText(state)) {
            throw new BizException(400, "douyin auth state missing");
        }
        String key = STATE_KEY_PREFIX + state;
        String raw = redisTemplate.opsForValue().get(key);
        redisTemplate.delete(key);
        if (!StringUtils.hasText(raw)) {
            throw new BizException(400, "douyin auth state invalid");
        }
        try {
            AuthState payload = objectMapper.readValue(raw, AuthState.class);
            return new State(payload.brandId(), payload.redirectArticleId());
        } catch (Exception ex) {
            log.warn("Failed to parse Douyin auth state payload");
            throw new BizException(400, "douyin auth state invalid");
        }
    }

    private boolean hasRequiredScopes(String rawScope) {
        List<String> granted = parseScopes(rawScope);
        return granted.containsAll(properties.getRequiredScopes());
    }

    private List<String> parseScopes(String rawScope) {
        if (!StringUtils.hasText(rawScope)) {
            return List.of();
        }
        return Arrays.stream(rawScope.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private String requiredScopeParam() {
        return String.join(",", properties.getRequiredScopes());
    }

    private String scopeJson(String rawScope) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "raw", rawScope == null ? "" : rawScope,
                    "list", parseScopes(rawScope)
            ));
        } catch (Exception ex) {
            throw new BizException(500, "douyin scope json create failed");
        }
    }

    private String extraJson(String openId) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "open_id", openId,
                    "source", "oauth_callback"
            ));
        } catch (Exception ex) {
            throw new BizException(500, "douyin extra json create failed");
        }
    }

    private LocalDateTime plusSeconds(LocalDateTime base, Long seconds) {
        return seconds == null ? null : base.plusSeconds(seconds);
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
