package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.config.WechatMenuProperties;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.entity.WechatMenuConfig;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.mapper.WechatMenuConfigMapper;
import com.huanjing.geo.module.content.wechat.WechatFuncInfoValidator;
import com.huanjing.geo.module.content.wechat.WechatMpClient;
import com.huanjing.geo.module.content.wechat.WechatTokenAwareExecutor;
import com.huanjing.geo.module.customer.access.BrandAccessAction;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WechatMenuConfigService {
    public static final String MENU_NAME = "往期文章";

    private static final Duration LOCK_TTL = Duration.ofSeconds(30);
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_CONFIGURED = "configured";
    private static final String STATUS_PERMISSION_MISSING = "permission_missing";
    private static final String STATUS_MENU_FULL = "menu_full";
    private static final String STATUS_CONFIG_FAILED = "config_failed";
    private static final String STATUS_MANUAL_REQUIRED = "manual_required";

    private final WechatMenuConfigMapper menuConfigMapper;
    private final SelfMediaAccountMapper selfMediaAccountMapper;
    private final WechatMenuProperties menuProperties;
    private final WechatFuncInfoValidator funcInfoValidator;
    private final WechatTokenAwareExecutor tokenAwareExecutor;
    private final WechatMpClient wechatMpClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final CurrentUserService currentUserService;
    private final BrandAccessService brandAccessService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public WechatMenuConfig ensureConfig(SelfMediaAccount account) {
        requireWechatAccount(account);
        WechatMenuConfig existing = menuConfigMapper.selectOne(new LambdaQueryWrapper<WechatMenuConfig>()
                .eq(WechatMenuConfig::getSelfMediaAccountId, account.getId())
                .last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        String publicSlug = existing == null || !StringUtils.hasText(existing.getPublicSlug())
                ? generateUniqueSlug()
                : existing.getPublicSlug();
        String url = listPageUrl(publicSlug);
        if (existing == null) {
            WechatMenuConfig row = new WechatMenuConfig();
            row.setSelfMediaAccountId(account.getId());
            row.setBrandId(account.getBrandId());
            row.setAuthorizerAppid(account.getPlatformAccountId());
            row.setPublicSlug(publicSlug);
            row.setMenuName(MENU_NAME);
            row.setMenuStatus(STATUS_PENDING);
            row.setListPageUrl(url);
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            menuConfigMapper.insert(row);
            return row;
        }
        existing.setBrandId(account.getBrandId());
        existing.setAuthorizerAppid(account.getPlatformAccountId());
        existing.setMenuName(MENU_NAME);
        existing.setListPageUrl(url);
        existing.setUpdatedAt(now);
        menuConfigMapper.updateById(existing);
        return existing;
    }

    @Transactional
    public WechatMenuConfig initializeMenu(Long selfMediaAccountId) {
        SelfMediaAccount account = selfMediaAccountId == null ? null : selfMediaAccountMapper.selectById(selfMediaAccountId);
        requireWechatAccount(account);
        SysUser operator = currentUserService.requireCurrentUser();
        brandAccessService.requireBrandAccess(account.getBrandId(), operator.getId(), BrandAccessAction.MANAGE);
        return initializeMenuForAccount(account);
    }

    public WechatMenuConfig getMenuConfig(Long selfMediaAccountId) {
        SelfMediaAccount account = selfMediaAccountId == null ? null : selfMediaAccountMapper.selectById(selfMediaAccountId);
        requireWechatAccount(account);
        SysUser operator = currentUserService.requireCurrentUser();
        brandAccessService.requireBrandAccess(account.getBrandId(), operator.getId(), BrandAccessAction.MANAGE);
        return menuConfigMapper.selectOne(new LambdaQueryWrapper<WechatMenuConfig>()
                .eq(WechatMenuConfig::getSelfMediaAccountId, account.getId())
                .last("LIMIT 1"));
    }

    @Transactional
    public WechatMenuConfig initializeMenuAfterAuthorization(Long selfMediaAccountId) {
        SelfMediaAccount account = selfMediaAccountId == null ? null : selfMediaAccountMapper.selectById(selfMediaAccountId);
        requireWechatAccount(account);
        return initializeMenuForAccount(account);
    }

    private WechatMenuConfig initializeMenuForAccount(SelfMediaAccount account) {
        WechatMenuConfig config = ensureConfig(account);
        if (!funcInfoValidator.hasMenuPermission(account.getScopeJson())) {
            return updateStatus(config, STATUS_PERMISSION_MISSING, "wechat menu permission missing: "
                    + funcInfoValidator.missingMenuRequired(account.getScopeJson()));
        }
        if (!isPocAllowed(account.getBrandId())) {
            return updateStatus(config, STATUS_MANUAL_REQUIRED, "brand not allowed by wechat menu poc whitelist");
        }

        String lockKey = "wechat:menu:init:" + account.getPlatformAccountId();
        String lockValue = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            return updateStatus(config, STATUS_CONFIG_FAILED, "wechat menu init lock busy");
        }
        try {
            return initializeMenuLocked(account, config);
        } finally {
            String current = redisTemplate.opsForValue().get(lockKey);
            if (lockValue.equals(current)) {
                redisTemplate.delete(lockKey);
            }
        }
    }

    private WechatMenuConfig initializeMenuLocked(SelfMediaAccount account, WechatMenuConfig config) {
        LocalDateTime now = LocalDateTime.now();
        try {
            String rawMenu = getMenuRaw(account);
            config.setBackupMenuJson(rawMenu);
            config.setBackupMenuAt(now);
            MenuDecision decision = decide(rawMenu, config.getListPageUrl());
            if (decision.status() != null) {
                config.setLastSyncAt(now);
                config.setMenuStatus(decision.status());
                config.setLastSyncError(decision.reason());
                menuConfigMapper.updateById(config);
                return config;
            }
            tokenAwareExecutor.execute(account, accessToken -> {
                wechatMpClient.createMenu(accessToken, decision.menuJson());
                return true;
            });
            config.setMenuStatus(STATUS_CONFIGURED);
            config.setLastSyncAt(now);
            config.setLastSyncError(null);
            menuConfigMapper.updateById(config);
            return config;
        } catch (Exception ex) {
            log.warn("WeChat menu init failed accountId={} appid={}", account.getId(), account.getPlatformAccountId(), ex);
            config.setMenuStatus(STATUS_CONFIG_FAILED);
            config.setLastSyncAt(now);
            config.setLastSyncError(trimError(ex.getMessage()));
            menuConfigMapper.updateById(config);
            return config;
        }
    }

    private String getMenuRaw(SelfMediaAccount account) {
        try {
            WechatMpClient.MenuResult result = tokenAwareExecutor.execute(account, wechatMpClient::getMenu);
            return StringUtils.hasText(result.rawResponse()) ? result.rawResponse() : emptyMenuJson();
        } catch (BizException ex) {
            if (ex.getCode() == 46003) {
                return emptyMenuJson();
            }
            throw ex;
        }
    }

    private MenuDecision decide(String rawMenu, String listPageUrl) throws Exception {
        JsonNode root = objectMapper.readTree(StringUtils.hasText(rawMenu) ? rawMenu : emptyMenuJson());
        if (hasNonEmptyArray(root.path("conditionalmenu")) || hasNonEmptyArray(root.path("conditionalmenu_info"))) {
            return new MenuDecision(STATUS_MANUAL_REQUIRED, "conditional menu exists", null);
        }
        JsonNode buttonNode = root.path("menu").path("button");
        if (!buttonNode.isMissingNode() && !buttonNode.isArray()) {
            return new MenuDecision(STATUS_MANUAL_REQUIRED, "unexpected menu button structure", null);
        }
        ArrayNode buttons = objectMapper.createArrayNode();
        if (buttonNode.isArray()) {
            for (JsonNode button : buttonNode) {
                String name = button.path("name").asText("");
                if (MENU_NAME.equals(name)) {
                    String url = button.path("url").asText("");
                    if (listPageUrl.equals(url)) {
                        return new MenuDecision(STATUS_CONFIGURED, null, null);
                    }
                    return new MenuDecision(STATUS_MANUAL_REQUIRED, "same menu name with different url", null);
                }
                buttons.add(button.deepCopy());
            }
        }
        if (buttons.size() >= 3) {
            return new MenuDecision(STATUS_MENU_FULL, "top level menu full", null);
        }
        ObjectNode item = objectMapper.createObjectNode();
        item.put("type", "view");
        item.put("name", MENU_NAME);
        item.put("url", listPageUrl);
        buttons.add(item);
        ObjectNode menu = objectMapper.createObjectNode();
        menu.set("button", buttons);
        return new MenuDecision(null, null, objectMapper.writeValueAsString(menu));
    }

    private boolean hasNonEmptyArray(JsonNode node) {
        return node != null && node.isArray() && !node.isEmpty();
    }

    private WechatMenuConfig updateStatus(WechatMenuConfig config, String status, String error) {
        config.setMenuStatus(status);
        config.setLastSyncAt(LocalDateTime.now());
        config.setLastSyncError(trimError(error));
        menuConfigMapper.updateById(config);
        return config;
    }

    private boolean isPocAllowed(Long brandId) {
        if (!menuProperties.isPocWhitelistEnabled()) {
            return true;
        }
        List<Long> allowed = menuProperties.getPocAllowedBrandIds();
        return brandId != null && allowed != null && allowed.contains(brandId);
    }

    private String generateUniqueSlug() {
        for (int i = 0; i < 10; i++) {
            String slug = randomSlug();
            Long count = menuConfigMapper.selectCount(new LambdaQueryWrapper<WechatMenuConfig>()
                    .eq(WechatMenuConfig::getPublicSlug, slug));
            if (count == null || count == 0) {
                return slug;
            }
        }
        throw new BizException(500, "wechat public slug generate failed");
    }

    private String randomSlug() {
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String listPageUrl(String publicSlug) {
        return trimTrailingSlash(menuProperties.getWebBaseUrl()) + "/wechat/mp/" + publicSlug + "/articles";
    }

    private String trimTrailingSlash(String value) {
        String text = StringUtils.hasText(value) ? value.trim() : "https://www.huanjingaigeo.com";
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private String emptyMenuJson() {
        return "{\"menu\":{\"button\":[]}}";
    }

    private String trimError(String message) {
        if (!StringUtils.hasText(message)) {
            return null;
        }
        String text = message.trim();
        return text.length() <= 500 ? text : text.substring(0, 500);
    }

    private void requireWechatAccount(SelfMediaAccount account) {
        if (account == null || account.getId() == null || !"wechat_mp".equals(normalize(account.getPlatform()))) {
            throw new BizException(404, "wechat self-media account not found");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record MenuDecision(String status, String reason, String menuJson) {
    }
}
