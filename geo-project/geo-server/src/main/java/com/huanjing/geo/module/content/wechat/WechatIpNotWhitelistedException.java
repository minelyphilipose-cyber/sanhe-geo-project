package com.huanjing.geo.module.content.wechat;

import com.huanjing.geo.common.exception.BizException;
import lombok.Getter;

@Getter
public class WechatIpNotWhitelistedException extends BizException {
    private final String outboundIp;

    public WechatIpNotWhitelistedException(String message, String outboundIp) {
        super(40164, message == null || message.isBlank()
                ? "wechat request ip is not in whitelist"
                : "wechat request ip is not in whitelist: " + message);
        this.outboundIp = outboundIp;
    }
}
