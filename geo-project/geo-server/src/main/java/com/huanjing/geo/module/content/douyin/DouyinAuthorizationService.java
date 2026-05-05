package com.huanjing.geo.module.content.douyin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.config.DouyinClientProperties;
import com.huanjing.geo.module.content.config.DouyinFeatureProperties;
import com.huanjing.geo.module.content.config.DouyinOpenPlatformProperties;
import com.huanjing.geo.module.content.douyin.client.DouyinClient;
import com.huanjing.geo.module.content.douyin.client.dto.DouyinCodeTokenRequest;
import com.huanjing.geo.module.content.douyin.client.dto.DouyinTokenResponse;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.vo.DouyinCapabilityVO;
import com.huanjing.geo.module.content.vo.DouyinAuthUrlVO;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DouyinAuthorizationService {
    private static final String PLATFORM = "douyin";
    private static final String AUTH_PAGE = "https://open.douyin.com/platform/oauth/connect/";
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
        String state = createState(brandId, redirectArticleId);
        String authUrl = UriComponentsBuilder.fromUriString(AUTH_PAGE)
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
        return new DouyinCapabilityVO(
                enabled,
                clientProperties.getMode(),
                enabled ? null : "feature flag disabled"
        );
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
