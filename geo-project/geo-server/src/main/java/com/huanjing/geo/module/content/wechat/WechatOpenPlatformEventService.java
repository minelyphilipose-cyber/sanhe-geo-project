package com.huanjing.geo.module.content.wechat;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
    private final WechatMpAuthorizationService authorizationService;

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
            case "updateauthorized" -> handleAuthorizationEvent(componentAppid, xml, "updateauthorized");
            case "authorized" -> handleAuthorizationEvent(componentAppid, xml, "authorized");
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

    private void handleAuthorizationEvent(String componentAppid, Map<String, String> xml, String infoType) {
        String authorizerAppid = xml.get("AuthorizerAppid");
        String authorizationCode = xml.get("AuthorizationCode");
        if (!StringUtils.hasText(authorizationCode)) {
            log.info("WeChat authorizer event ignored: missing AuthorizationCode type={} authorizerAppid={}",
                    infoType, authorizerAppid);
            return;
        }
        try {
            SelfMediaAccount account = authorizationService.saveOrUpdateAuthorization(componentAppid, authorizationCode);
            log.info("WeChat authorizer event persisted type={} authorizerAppid={} status={}",
                    infoType, account.getPlatformAccountId(), account.getStatus());
        } catch (Exception ex) {
            log.error("WeChat authorizer event persist failed type={} componentAppid={} authorizerAppid={}",
                    infoType, componentAppid, authorizerAppid, ex);
        }
    }
}
