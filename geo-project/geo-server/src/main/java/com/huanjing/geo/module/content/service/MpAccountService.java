package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.config.WechatMpClientProperties;
import com.huanjing.geo.module.content.config.WechatOpenPlatformProperties;
import com.huanjing.geo.module.content.dto.MpAccountDevSeedRequest;
import com.huanjing.geo.module.content.entity.MpAccount;
import com.huanjing.geo.module.content.mapper.MpAccountMapper;
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
    private final MpAccountMapper mpAccountMapper;
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
        return mpAccountMapper.selectList(new LambdaQueryWrapper<MpAccount>()
                        .eq(MpAccount::getBrandId, brandId)
                        .eq(MpAccount::getPlatform, "wechat_mp")
                        .orderByDesc(MpAccount::getUpdatedAt))
                .stream()
                .map(MpAccountVO::from)
                .toList();
    }

    public MpAccountVO checkAuth(Long id) {
        MpAccount account = requireAccount(id);
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
        mpAccountMapper.updateById(account);
        return MpAccountVO.from(account);
    }

    private void checkMaterialCount(MpAccount account) {
        wechatMpClient.getMaterialCount(authorizerTokenService.getAccessToken(account));
    }

    private void retryAfterTokenEvict(MpAccount account) {
        authorizerTokenService.evictAccessToken(account);
        try {
            checkMaterialCount(account);
            account.setStatus("active");
            account.setLastAuthError(null);
        } catch (BizException retryEx) {
            log.warn("WeChat check-auth failed after token refresh appid={} code={} message={}",
                    account.getAuthorizerAppid(), retryEx.getCode(), retryEx.getMessage());
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
        MpAccount account = mpAccountMapper.selectOne(new LambdaQueryWrapper<MpAccount>()
                .eq(MpAccount::getAuthorizerAppid, request.getAuthorizerAppid())
                .last("LIMIT 1"));
        if (account == null) {
            account = new MpAccount();
            account.setCreatedAt(LocalDateTime.now());
        }
        account.setBrandId(request.getBrandId());
        account.setPlatform("wechat_mp");
        account.setAccountName(request.getAccountName());
        account.setAuthorizerAppid(request.getAuthorizerAppid());
        account.setAuthorizerRefreshTokenCipher(cipherService.encryptForStorage("mock_authorizer_refresh_token"));
        account.setCredentialKeyVersion("v1");
        account.setFuncInfoJson("[{\"funcscope_category\":{\"id\":1}},{\"funcscope_category\":{\"id\":13}}]");
        account.setHeadImg("https://mock.local/wechat-head.png");
        account.setQrcodeUrl("https://mock.local/wechat-qrcode.png");
        account.setStatus(request.getStatus());
        account.setLastAuthCheckedAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());
        if (account.getId() == null) {
            mpAccountMapper.insert(account);
        } else {
            mpAccountMapper.updateById(account);
        }
        return MpAccountVO.from(account);
    }

    private MpAccount requireAccount(Long id) {
        MpAccount account = mpAccountMapper.selectById(id);
        if (account == null) {
            throw new BizException(404, "mp account not found");
        }
        return account;
    }

}
