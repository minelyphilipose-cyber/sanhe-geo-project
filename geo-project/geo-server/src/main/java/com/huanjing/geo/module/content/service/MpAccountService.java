package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.config.WechatMpClientProperties;
import com.huanjing.geo.module.content.config.WechatOpenPlatformProperties;
import com.huanjing.geo.module.content.dto.MpAccountDevSeedRequest;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.vo.MpAccountVO;
import com.huanjing.geo.module.content.vo.WechatMpCapabilityVO;
import com.huanjing.geo.module.content.wechat.WechatAuthorizerTokenService;
import com.huanjing.geo.module.content.wechat.WechatMpClient;
import com.huanjing.geo.module.system.service.MpCredentialCipherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class MpAccountService {
    private final SelfMediaAccountMapper selfMediaAccountMapper;
    private final WechatMpClient wechatMpClient;
    private final WechatOpenPlatformProperties openPlatformProperties;
    private final WechatMpClientProperties clientProperties;
    private final MpCredentialCipherService cipherService;
    private final WechatAuthorizerTokenService authorizerTokenService;

    public WechatMpCapabilityVO capability() {
        String reason = openPlatformProperties.isDraftDistributionEnabled()
                ? null
                : "wechat_open_platform_review_pending";
        return new WechatMpCapabilityVO(
                openPlatformProperties.isDraftDistributionEnabled(),
                clientProperties.getMode(),
                reason
        );
    }

    public List<MpAccountVO> listByBrand(Long brandId) {
        return selfMediaAccountMapper.selectList(new LambdaQueryWrapper<SelfMediaAccount>()
                        .eq(SelfMediaAccount::getBrandId, brandId)
                        .eq(SelfMediaAccount::getPlatform, "wechat_mp")
                        .orderByDesc(SelfMediaAccount::getUpdatedAt))
                .stream()
                .map(MpAccountVO::from)
                .toList();
    }

    public MpAccountVO checkAuth(Long id) {
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
        return MpAccountVO.from(account);
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

    public MpAccountVO seedForDev(MpAccountDevSeedRequest request) {
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
        return MpAccountVO.from(account);
    }

    private SelfMediaAccount requireAccount(Long id) {
        SelfMediaAccount account = selfMediaAccountMapper.selectById(id);
        if (account == null) {
            throw new BizException(404, "mp account not found");
        }
        return account;
    }

}
