package com.huanjing.geo.module.content.wechat;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.entity.WechatCallbackEvent;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.mapper.WechatCallbackEventMapper;
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
    private final WechatCallbackEventMapper callbackEventMapper;

    public String handleComponentEvent(String rawXml) {
        LocalDateTime receivedAt = LocalDateTime.now();
        Map<String, String> xml = WechatXmlParser.parse(rawXml);
        String infoType = xml.get("InfoType");
        String componentAppid = xml.get("AppId");
        String authorizerAppid = xml.get("AuthorizerAppid");
        String processStatus = "success";
        String processError = null;
        if (!StringUtils.hasText(infoType)) {
            log.warn("WeChat component event ignored: missing InfoType");
            saveAudit(rawXml, xml, receivedAt, "ignored", "missing InfoType", null);
            return "success";
        }
        try {
            switch (infoType) {
                case "component_verify_ticket" ->
                        ticketService.storeTicket(componentAppid, xml.get("ComponentVerifyTicket"));
                case "unauthorized" -> markAuthorizer(authorizerAppid, "revoked", "authorized revoked by wechat");
                case "updateauthorized" -> handleAuthorizationEvent(componentAppid, xml, "updateauthorized");
                case "authorized" -> handleAuthorizationEvent(componentAppid, xml, "authorized");
                default -> log.info("WeChat component event received type={} componentAppid={}", infoType, componentAppid);
            }
        } catch (RuntimeException ex) {
            processStatus = "failed";
            processError = safeMessage(ex);
            throw ex;
        } finally {
            saveAudit(rawXml, xml, receivedAt, processStatus, processError, null);
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

    private void saveAudit(String rawXml,
                           Map<String, String> xml,
                           LocalDateTime receivedAt,
                           String processStatus,
                           String processError,
                           String responseBody) {
        try {
            WechatCallbackEvent event = new WechatCallbackEvent();
            event.setCallbackType("component_event");
            event.setComponentAppid(xml.get("AppId"));
            event.setAuthorizerAppid(xml.get("AuthorizerAppid"));
            event.setEventType(xml.get("InfoType"));
            event.setMsgType(xml.get("MsgType"));
            event.setOpenid(xml.get("FromUserName"));
            event.setRawXml(rawXml);
            event.setDecryptedXml(rawXml);
            event.setResponseBody(responseBody);
            event.setProcessStatus(processStatus);
            event.setProcessError(processError);
            event.setReceivedAt(receivedAt);
            event.setProcessedAt(LocalDateTime.now());
            callbackEventMapper.insert(event);
        } catch (Exception ex) {
            log.warn("Failed to persist WeChat component event audit");
        }
    }

    private String safeMessage(RuntimeException ex) {
        return StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : ex.getClass().getSimpleName();
    }
}
