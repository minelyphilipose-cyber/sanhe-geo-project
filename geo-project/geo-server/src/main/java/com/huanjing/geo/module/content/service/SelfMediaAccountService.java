package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.config.WechatMpClientProperties;
import com.huanjing.geo.module.content.config.WechatOpenPlatformProperties;
import com.huanjing.geo.module.content.credential.dto.CookieCredentialMeta;
import com.huanjing.geo.module.content.credential.service.CredentialVaultService;
import com.huanjing.geo.module.content.dto.SelfMediaAccountManageRequest;
import com.huanjing.geo.module.content.dto.WechatMpDevSeedRequest;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.vo.SelfMediaAccountVO;
import com.huanjing.geo.module.content.vo.WechatMpCapabilityVO;
import com.huanjing.geo.module.content.vo.WechatReadinessCheckVO;
import com.huanjing.geo.module.customer.access.BrandAccessAction;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.content.wechat.WechatAuthorizerTokenService;
import com.huanjing.geo.module.content.wechat.WechatComponentTicketService;
import com.huanjing.geo.module.content.wechat.WechatMpClient;
import com.huanjing.geo.module.system.service.MpCredentialCipherService;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class SelfMediaAccountService {
    private final SelfMediaAccountMapper selfMediaAccountMapper;
    private final WechatMpClient wechatMpClient;
    private final WechatOpenPlatformProperties openPlatformProperties;
    private final WechatMpClientProperties clientProperties;
    private final MpCredentialCipherService cipherService;
    private final WechatAuthorizerTokenService authorizerTokenService;
    private final WechatComponentTicketService componentTicketService;
    private final CredentialVaultService credentialVaultService;
    private final BrandAccessService brandAccessService;
    private final CurrentUserService currentUserService;
    private final SelfMediaAccountPlatformEligibilityService platformEligibilityService;

    public WechatMpCapabilityVO capability() {
        String reason = openPlatformProperties.isDraftDistributionEnabled()
                ? null
                : "wechat_open_platform_review_pending";
        boolean liveVerificationBlocked = openPlatformProperties.isLiveVerificationBlocked();
        return new WechatMpCapabilityVO(
                openPlatformProperties.isDraftDistributionEnabled(),
                openPlatformProperties.isAutoPublishEnabled(),
                clientProperties.getMode(),
                reason,
                liveVerificationBlocked,
                liveVerificationBlocked ? openPlatformProperties.getLiveVerificationReason() : null,
                wechatCapabilityDescription(liveVerificationBlocked),
                readinessChecks(liveVerificationBlocked)
        );
    }

    private List<WechatReadinessCheckVO> readinessChecks(boolean liveVerificationBlocked) {
        List<WechatReadinessCheckVO> checks = new ArrayList<>();
        checks.add(configCheck("component_appid", "Component AppID", openPlatformProperties.getComponentAppid(), "请配置 WECHAT_COMPONENT_APPID"));
        checks.add(configCheck("component_secret", "Component Secret", openPlatformProperties.getComponentAppSecret(), "请配置 WECHAT_COMPONENT_SECRET"));
        checks.add(configCheck("token", "消息校验 Token", openPlatformProperties.getToken(), "请配置 WECHAT_TOKEN，并与微信开放平台后台一致"));
        checks.add(configCheck("encoding_aes_key", "消息加解密 Key", openPlatformProperties.getEncodingAesKey(), "请配置 43 位 WECHAT_ENCODING_AES_KEY"));
        checks.add(httpsCheck("auth_callback_url", "授权回调 URL", openPlatformProperties.getBackendAuthCallbackUrl(), "请配置 HTTPS 授权回调 URL"));
        checks.add(httpsCheck("frontend_callback_url", "前端回跳 URL", openPlatformProperties.getFrontendCallbackUrl(), "请配置 HTTPS 前端回跳 URL"));
        checks.add(httpsCheck("component_event_url", "授权事件接收 URL", openPlatformProperties.getComponentEventUrl(), "请配置 WECHAT_COMPONENT_EVENT_URL=https://域名/api/wechat/open-platform/events"));
        checks.add(httpsCheck("authorizer_message_url", "公众号消息事件 URL", openPlatformProperties.getAuthorizerMessageUrl(), "请配置 WECHAT_AUTHORIZER_MESSAGE_URL=https://域名/api/wechat/open-platform/messages/$APPID"));
        checks.add(ticketCheck());
        checks.add(new WechatReadinessCheckVO(
                "client_mode",
                "客户端模式",
                "real".equalsIgnoreCase(clientProperties.getMode()) ? "ok" : "warning",
                "real".equalsIgnoreCase(clientProperties.getMode()) ? "当前使用真实微信 Open API" : "当前仍为 mock 模式，联调前需设置 WECHAT_CLIENT_MODE=real"
        ));
        checks.add(new WechatReadinessCheckVO(
                "draft_distribution",
                "草稿箱分发开关",
                openPlatformProperties.isDraftDistributionEnabled() ? "ok" : "warning",
                openPlatformProperties.isDraftDistributionEnabled() ? "草稿箱分发已开启" : "认证通过并完成联调后设置 WECHAT_DRAFT_DISTRIBUTION_ENABLED=true"
        ));
        checks.add(new WechatReadinessCheckVO(
                "auto_publish",
                "自动发布开关",
                openPlatformProperties.isAutoPublishEnabled() ? "warning" : "ok",
                openPlatformProperties.isAutoPublishEnabled() ? "自动提交发布已开启，请确认微信权限和审核链路已完成" : "自动提交发布保持关闭，当前优先保存到草稿箱"
        ));
        checks.add(new WechatReadinessCheckVO(
                "live_verification",
                "上线联调阻断",
                liveVerificationBlocked ? "warning" : "ok",
                liveVerificationBlocked ? "当前仍阻断真实联调，认证通过后设置 WECHAT_LIVE_VERIFICATION_BLOCKED=false" : "真实联调阻断已关闭"
        ));
        return checks;
    }

    private WechatReadinessCheckVO configCheck(String code, String label, String value, String missingMessage) {
        if (StringUtils.hasText(value)) {
            return new WechatReadinessCheckVO(code, label, "ok", label + " 已配置");
        }
        return new WechatReadinessCheckVO(code, label, "missing", missingMessage);
    }

    private WechatReadinessCheckVO httpsCheck(String code, String label, String value, String missingMessage) {
        if (!StringUtils.hasText(value)) {
            return new WechatReadinessCheckVO(code, label, "missing", missingMessage);
        }
        if (!value.startsWith("https://")) {
            return new WechatReadinessCheckVO(code, label, "warning", label + " 必须使用 HTTPS");
        }
        return new WechatReadinessCheckVO(code, label, "ok", label + " 已配置 HTTPS");
    }

    private WechatReadinessCheckVO ticketCheck() {
        try {
            LocalDateTime receivedAt = componentTicketService.getLatestReceivedAt(openPlatformProperties.getComponentAppid());
            if (receivedAt == null) {
                return new WechatReadinessCheckVO("component_verify_ticket", "Component Verify Ticket", "missing", "尚未收到微信 component_verify_ticket，请确认授权事件接收 URL 已配置并可公网访问");
            }
            return new WechatReadinessCheckVO("component_verify_ticket", "Component Verify Ticket", "ok", "最近接收时间：" + receivedAt);
        } catch (Exception ex) {
            return new WechatReadinessCheckVO("component_verify_ticket", "Component Verify Ticket", "warning", "读取 ticket 状态失败：" + (StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : ex.getClass().getSimpleName()));
        }
    }

    private String wechatCapabilityDescription(boolean liveVerificationBlocked) {
        if (liveVerificationBlocked) {
            return "当前域名备案及微信公众号第三方平台审核未完成，真实自动发布/审核联调暂不可用；草稿箱接口可继续用于测试。";
        }
        if (openPlatformProperties.isAutoPublishEnabled()) {
            return "微信公众号已支持自动提交发布；提交后进入微信官方发布/审核流程。";
        }
        return "微信公众号当前默认保存到草稿箱，适合联调测试；配置 WECHAT_AUTO_PUBLISH_ENABLED=true 且请求 publishAction=publish 后可提交发布。";
    }

    public List<SelfMediaAccountVO> listByBrand(Long brandId) {
        return selfMediaAccountMapper.selectList(new LambdaQueryWrapper<SelfMediaAccount>()
                        .eq(SelfMediaAccount::getBrandId, brandId)
                        .orderByAsc(SelfMediaAccount::getPlatform)
                .orderByDesc(SelfMediaAccount::getUpdatedAt))
                .stream()
                .map(this::toVoWithCredentialStatus)
                .toList();
    }

    public SelfMediaAccountVO checkAuth(Long id) {
        SelfMediaAccount account = requireAccount(id);
        try {
            checkMaterialCount(account);
            account.setStatus("active");
            account.setLastAuthError(null);
        } catch (BizException ex) {
            if (ex.getCode() == 40001 || ex.getCode() == 42001) {
                retryAfterTokenEvict(account);
            } else if (ex.getCode() == 48001) {
                account.setStatus("disabled");
                account.setLastAuthError("wechat permission missing");
            } else {
                account.setLastAuthError(ex.getMessage());
            }
        }
        account.setLastAuthCheckedAt(LocalDateTime.now());
        selfMediaAccountMapper.updateById(account);
        return SelfMediaAccountVO.from(account);
    }

    @Transactional
    public SelfMediaAccountVO createCookieAccount(Long brandId, SelfMediaAccountManageRequest request) {
        SysUser operator = currentUserService.requireCurrentUser();
        brandAccessService.requireBrandAccess(brandId, operator.getId(), BrandAccessAction.MANAGE);
        platformEligibilityService.requireEligible(brandId, request.platform());
        String platformAccountId = resolvePlatformAccountId(
                request.platform(),
                request.platformAccountId(),
                brandId,
                null
        );
        LocalDateTime now = LocalDateTime.now();
        SelfMediaAccount account = new SelfMediaAccount();
        account.setBrandId(brandId);
        account.setPlatform(request.platform());
        account.setPlatformAccountId(platformAccountId);
        account.setAccountName(request.accountName().trim());
        account.setStatus(normalizeStatus(request.status()));
        account.setAuthMode("COOKIE");
        account.setCreatedBy(operator.getId());
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        selfMediaAccountMapper.insert(account);
        return toVoWithCredentialStatus(account);
    }

    @Transactional
    public SelfMediaAccountVO updateCookieAccount(Long id, SelfMediaAccountManageRequest request) {
        SysUser operator = currentUserService.requireCurrentUser();
        SelfMediaAccount account = requireAccount(id);
        if (!"COOKIE".equalsIgnoreCase(account.getAuthMode())) {
            throw new BizException(400, "Only cookie self-media accounts can be managed here");
        }
        brandAccessService.requireBrandAccess(account.getBrandId(), operator.getId(), BrandAccessAction.MANAGE);
        if (!request.platform().equals(account.getPlatform())) {
            platformEligibilityService.requireEligible(account.getBrandId(), request.platform());
        }
        String platformAccountId = resolvePlatformAccountId(
                request.platform(),
                request.platformAccountId(),
                account.getBrandId(),
                account
        );
        if (platformAccountIdExistsForOther(request.platform(), platformAccountId, account.getId())) {
            throw new BizException(400, "self media account already exists");
        }
        account.setPlatform(request.platform());
        account.setPlatformAccountId(platformAccountId);
        account.setAccountName(request.accountName().trim());
        account.setStatus(normalizeStatus(request.status()));
        account.setUpdatedAt(LocalDateTime.now());
        selfMediaAccountMapper.updateById(account);
        return toVoWithCredentialStatus(account);
    }

    @Transactional
    public SelfMediaAccountVO destroyCookieCredential(Long id) {
        SysUser operator = currentUserService.requireCurrentUser();
        SelfMediaAccount account = requireAccount(id);
        if (!"COOKIE".equalsIgnoreCase(account.getAuthMode())) {
            throw new BizException(400, "Only cookie self-media accounts have cookie credentials");
        }
        brandAccessService.requireBrandAccess(account.getBrandId(), operator.getId(), BrandAccessAction.MANAGE);
        credentialVaultService.destroyCredentials(account.getId(), account.getBrandId(), operator.getId());
        account.setLastAuthCheckedAt(LocalDateTime.now());
        account.setLastAuthError("cookie credential cleared by operator");
        account.setUpdatedAt(LocalDateTime.now());
        selfMediaAccountMapper.updateById(account);
        return toVoWithCredentialStatus(account);
    }

    private void checkMaterialCount(SelfMediaAccount account) {
        wechatMpClient.getMaterialCount(authorizerTokenService.getAccessToken(account));
    }

    private void retryAfterTokenEvict(SelfMediaAccount account) {
        authorizerTokenService.evictAccessToken(account);
        try {
            checkMaterialCount(account);
            account.setStatus("active");
            account.setLastAuthError(null);
        } catch (BizException retryEx) {
            log.warn("WeChat check-auth failed after token refresh appid={} code={} message={}",
                    account.getPlatformAccountId(), retryEx.getCode(), retryEx.getMessage());
            if (retryEx.getCode() == 48001) {
                account.setStatus("disabled");
                account.setLastAuthError("wechat permission missing");
            } else {
                account.setStatus("expired");
                account.setLastAuthError("wechat credential expired");
            }
        }
    }

    public SelfMediaAccountVO seedForDev(WechatMpDevSeedRequest request) {
        SelfMediaAccount account = selfMediaAccountMapper.selectOne(new LambdaQueryWrapper<SelfMediaAccount>()
                .eq(SelfMediaAccount::getPlatformAccountId, request.getAuthorizerAppid())
                .last("LIMIT 1"));
        if (account == null) {
            account = new SelfMediaAccount();
            account.setCreatedAt(LocalDateTime.now());
        }
        account.setBrandId(request.getBrandId());
        account.setPlatform("wechat_mp");
        account.setAccountName(request.getAccountName());
        account.setPlatformAccountId(request.getAuthorizerAppid());
        account.setRefreshTokenCipher(cipherService.encryptForStorage("mock_authorizer_refresh_token"));
        account.setCredentialKeyVersion("v1");
        account.setScopeJson("[{\"funcscope_category\":{\"id\":1}},{\"funcscope_category\":{\"id\":13}}]");
        account.setAvatarUrl("https://mock.local/wechat-head.png");
        account.setQrcodeUrl("https://mock.local/wechat-qrcode.png");
        account.setStatus(request.getStatus());
        account.setLastAuthCheckedAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());
        if (account.getId() == null) {
            selfMediaAccountMapper.insert(account);
        } else {
            selfMediaAccountMapper.updateById(account);
        }
        return SelfMediaAccountVO.from(account);
    }

    private SelfMediaAccount requireAccount(Long id) {
        SelfMediaAccount account = selfMediaAccountMapper.selectById(id);
        if (account == null) {
            throw new BizException(404, "self media account not found");
        }
        return account;
    }

    private SelfMediaAccountVO toVoWithCredentialStatus(SelfMediaAccount account) {
        SelfMediaAccountVO vo = SelfMediaAccountVO.from(account);
        if ("COOKIE".equalsIgnoreCase(account.getAuthMode())) {
            CookieCredentialMeta credential = credentialVaultService.getActiveCredentialMeta(account.getId());
            if (credential == null) {
                vo.setCookieCredentialStatus("missing");
            } else {
                vo.setCookieCredentialStatus("active");
                vo.setCookieCredentialVersion(credential.version());
                vo.setCookieCredentialCapturedAt(credential.capturedAt());
                applyCookieCredentialIdentity(vo, credential);
            }
        }
        return vo;
    }

    private void applyCookieCredentialIdentity(SelfMediaAccountVO vo, CookieCredentialMeta credential) {
        vo.setCookieCredentialIdentityStatus("unknown");
        if (!StringUtils.hasText(credential.capturedFingerprintJson())) {
            vo.setCookieCredentialIdentityMessage("未记录平台账号身份识别结果");
            return;
        }
        try {
            JSONObject fingerprint = JSONUtil.parseObj(credential.capturedFingerprintJson());
            JSONObject identity = fingerprint.getJSONObject("platformIdentity");
            JSONObject check = fingerprint.getJSONObject("identityCheck");
            if (identity != null) {
                vo.setCookieCredentialIdentityName(identity.getStr("displayName"));
            }
            if (check != null) {
                vo.setCookieCredentialIdentityStatus(check.getStr("status", "unknown"));
                vo.setCookieCredentialIdentityMessage(check.getStr("message"));
            }
        } catch (Exception ex) {
            vo.setCookieCredentialIdentityMessage("平台账号身份识别结果解析失败");
        }
    }

    private String generatedPlatformAccountId(String platform, Long brandId) {
        for (int i = 0; i < 5; i++) {
            String candidate = "geo-" + platform + "-" + brandId + "-" + UUID.randomUUID()
                    .toString()
                    .replace("-", "")
                    .substring(0, 16);
            if (!platformAccountIdExists(platform, candidate)) {
                return candidate;
            }
        }
        throw new BizException(500, "self media account identifier generation failed");
    }

    private String resolvePlatformAccountId(String platform,
                                            String requestedPlatformAccountId,
                                            Long brandId,
                                            SelfMediaAccount currentAccount) {
        String requested = normalizeText(requestedPlatformAccountId);
        if (StringUtils.hasText(requested)) {
            if ("baijiahao".equals(platform) && !requested.matches("\\d{6,}")) {
                throw new BizException(400, "百家号 ID / app_id 应为数字");
            }
            return requested;
        }
        if ("baijiahao".equals(platform)) {
            throw new BizException(400, "百家号账号必须填写百家号 ID / app_id");
        }
        if (currentAccount != null
                && platform.equals(currentAccount.getPlatform())
                && StringUtils.hasText(currentAccount.getPlatformAccountId())) {
            return currentAccount.getPlatformAccountId();
        }
        return generatedPlatformAccountId(platform, brandId);
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private boolean platformAccountIdExists(String platform, String platformAccountId) {
        SelfMediaAccount existing = selfMediaAccountMapper.selectOne(new LambdaQueryWrapper<SelfMediaAccount>()
                .eq(SelfMediaAccount::getPlatform, platform)
                .eq(SelfMediaAccount::getPlatformAccountId, platformAccountId)
                .last("LIMIT 1"));
        return existing != null;
    }

    private boolean platformAccountIdExistsForOther(String platform, String platformAccountId, Long currentAccountId) {
        SelfMediaAccount existing = selfMediaAccountMapper.selectOne(new LambdaQueryWrapper<SelfMediaAccount>()
                .eq(SelfMediaAccount::getPlatform, platform)
                .eq(SelfMediaAccount::getPlatformAccountId, platformAccountId)
                .ne(currentAccountId != null, SelfMediaAccount::getId, currentAccountId)
                .last("LIMIT 1"));
        return existing != null;
    }

    private String normalizeStatus(String status) {
        return StringUtils.hasText(status) ? status.trim() : "active";
    }

}
