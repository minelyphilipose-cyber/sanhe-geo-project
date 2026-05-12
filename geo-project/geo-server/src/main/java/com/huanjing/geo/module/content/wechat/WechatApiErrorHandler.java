package com.huanjing.geo.module.content.wechat;

import com.fasterxml.jackson.databind.JsonNode;
import com.huanjing.geo.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WechatApiErrorHandler {
    private static final int IP_NOT_WHITELISTED = 40164;

    private final WechatOutboundIpService outboundIpService;

    public void throwIfError(String apiName, JsonNode root) {
        int errcode = root.path("errcode").asInt(0);
        if (errcode == 0) {
            return;
        }
        String errmsg = root.path("errmsg").asText("wechat api error");
        if (errcode == IP_NOT_WHITELISTED) {
            String outboundIp = outboundIpService.currentOutboundIp();
            log.error("WeChat api rejected by IP whitelist api={} outboundIp={} errmsg={}",
                    apiName, outboundIp, errmsg);
            throw new WechatIpNotWhitelistedException(errmsg, outboundIp);
        }
        throw new BizException(errcode, errmsg);
    }
}
