package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.huanjing.geo.module.customer.access.BrandAccessAction;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.content.wechat.WechatAuthorizerTokenService;
import com.huanjing.geo.module.content.wechat.WechatMpClient;
import com.huanjing.geo.module.system.service.MpCredentialCipherService;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
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
    private final CredentialVaultService credentialVaultService;
    private final BrandAccessService brandAccessService;
    private final CurrentUserService currentUserService;

    public WechatMpCapabilityVO capability() {
        String reason = openPlatformProperties.isDraftDistributionEnabled()
                ? null
                : "wechat_open_platform_review_pending";
        return new WechatMpCapabilityVO(
                openPlatformProperties.isDraftDistributionEnabled(),
                openPlatformProperties.isAutoPublishEnabled(),
                clientProperties.getMode(),
                reason,
                openPlatformProperties.isAutoPublishEnabled()
                        ? "微信公众号已支持自动提交发布；提交后进入微信官方发布/审核流程。"
                        : "微信公众号当前默认保存到草稿箱，适合联调测试；配置 WECHAT_AUTO_PUBLISH_ENABLED=true 且请求 publishAction=publish 后可提交发布。"
        );
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
        String platformAccountId = generatedPlatformAccountId(request.platform(), brandId);
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
        if (!StringUtils.hasText(account.getPlatformAccountId())) {
            account.setPlatformAccountId(generatedPlatformAccountId(request.platform(), account.getBrandId()));
        } else if (!request.platform().equals(account.getPlatform())
                && platformAccountIdExists(request.platform(), account.getPlatformAccountId())) {
            throw new BizException(400, "self media account already exists");
        }
        account.setPlatform(request.platform());
        account.setAccountName(request.accountName().trim());
        account.setStatus(normalizeStatus(request.status()));
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
            }
        }
        return vo;
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

    private boolean platformAccountIdExists(String platform, String platformAccountId) {
        SelfMediaAccount existing = selfMediaAccountMapper.selectOne(new LambdaQueryWrapper<SelfMediaAccount>()
                .eq(SelfMediaAccount::getPlatform, platform)
                .eq(SelfMediaAccount::getPlatformAccountId, platformAccountId)
                .last("LIMIT 1"));
        return existing != null;
    }

    private String normalizeStatus(String status) {
        return StringUtils.hasText(status) ? status.trim() : "active";
    }

}
