package com.huanjing.geo.module.content.wechat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.huanjing.geo.module.content.config.WechatOpenPlatformProperties;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WechatOpenPlatformEventService {
    private final WechatComponentTicketService ticketService;
    private final SelfMediaAccountMapper selfMediaAccountMapper;
    private final WechatComponentAccessTokenService componentAccessTokenService;
    private final WechatOpenPlatformClient openPlatformClient;
    private final WechatOpenPlatformProperties properties;
    private final WechatFuncInfoValidator funcInfoValidator;

    public String handleComponentEvent(String rawXml) {
        Map<String, String> xml = WechatXmlParser.parse(rawXml);
        String infoType = xml.get("InfoType");
        String componentAppid = xml.get("AppId");
        if (!StringUtils.hasText(infoType)) {
            log.warn("WeChat component event ignored: missing InfoType");
            return "success";
        }
        switch (infoType) {
            case "component_verify_ticket" ->
                    ticketService.storeTicket(componentAppid, xml.get("ComponentVerifyTicket"));
            case "unauthorized" -> markAuthorizer(xml.get("AuthorizerAppid"), "revoked", "authorized revoked by wechat");
            case "updateauthorized" -> revalidateAuthorizer(xml.get("AuthorizerAppid"));
            case "authorized" -> log.info("WeChat authorizer event received type={} authorizerAppid={}",
                    infoType, xml.get("AuthorizerAppid"));
            default -> log.info("WeChat component event received type={} componentAppid={}", infoType, componentAppid);
        }
        return "success";
    }

    private void markAuthorizer(String authorizerAppid, String status, String error) {
        if (!StringUtils.hasText(authorizerAppid)) {
            return;
        }
        LambdaUpdateWrapper<SelfMediaAccount> update = new LambdaUpdateWrapper<SelfMediaAccount>()
                .eq(SelfMediaAccount::getPlatformAccountId, authorizerAppid)
                .set(SelfMediaAccount::getStatus, status)
                .set(SelfMediaAccount::getLastAuthCheckedAt, LocalDateTime.now())
                .set(SelfMediaAccount::getLastAuthError, error);
        int rows = selfMediaAccountMapper.update(null, update);
        log.info("WeChat authorizer status synced authorizerAppid={} status={} rows={}",
                authorizerAppid, status, rows);
    }

    private void revalidateAuthorizer(String authorizerAppid) {
        if (!StringUtils.hasText(authorizerAppid)) {
            return;
        }
        SelfMediaAccount account = selfMediaAccountMapper.selectOne(new LambdaQueryWrapper<SelfMediaAccount>()
                .eq(SelfMediaAccount::getPlatformAccountId, authorizerAppid)
                .last("LIMIT 1"));
        if (account == null) {
            log.info("WeChat updateauthorized ignored; mp account not found authorizerAppid={}", authorizerAppid);
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        try {
            String componentToken = componentAccessTokenService.getAccessToken();
            WechatOpenPlatformClient.AuthorizerInfoResult info = openPlatformClient.getAuthorizerInfo(
                    componentToken,
                    properties.getComponentAppid(),
                    authorizerAppid
            );
            String funcInfo = info.funcInfoJson();
            account.setAccountName(StringUtils.hasText(info.accountName()) ? info.accountName() : account.getAccountName());
            account.setAvatarUrl(info.headImg());
            account.setQrcodeUrl(info.qrcodeUrl());
            account.setScopeJson(funcInfo);
            if (funcInfoValidator.hasDraftPermissions(funcInfo)) {
                account.setStatus("active");
                account.setLastAuthError(null);
            } else {
                account.setStatus("disabled");
                account.setLastAuthError("wechat permission missing: " + funcInfoValidator.missingRequired(funcInfo));
            }
            account.setLastAuthCheckedAt(now);
            account.setUpdatedAt(now);
            selfMediaAccountMapper.updateById(account);
            log.info("WeChat updateauthorized revalidated authorizerAppid={} status={}",
                    authorizerAppid, account.getStatus());
        } catch (Exception ex) {
            LambdaUpdateWrapper<SelfMediaAccount> update = new LambdaUpdateWrapper<SelfMediaAccount>()
                    .eq(SelfMediaAccount::getPlatformAccountId, authorizerAppid)
                    .set(SelfMediaAccount::getLastAuthCheckedAt, now)
                    .set(SelfMediaAccount::getLastAuthError, "func_info revalidation failed");
            selfMediaAccountMapper.update(null, update);
            log.warn("WeChat updateauthorized revalidation failed authorizerAppid={}", authorizerAppid, ex);
        }
    }
}
