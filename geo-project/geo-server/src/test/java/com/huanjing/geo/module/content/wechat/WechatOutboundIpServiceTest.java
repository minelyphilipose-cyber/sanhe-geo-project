package com.huanjing.geo.module.content.wechat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WechatOutboundIpServiceTest {

    @Test
    void currentOutboundIp_prefersConfiguredEnvironmentValue() {
        WechatOutboundIpService service = new WechatOutboundIpService(" 1.2.3.4 ", () -> "5.6.7.8");

        service.detectOnStartup();

        assertEquals("1.2.3.4", service.currentOutboundIp());
    }

    @Test
    void currentOutboundIp_usesStartupDetectedValueWhenNotConfigured() {
        WechatOutboundIpService service = new WechatOutboundIpService("", () -> " 5.6.7.8 ");

        service.detectOnStartup();

        assertEquals("5.6.7.8", service.currentOutboundIp());
    }

    @Test
    void currentOutboundIp_fallsBackToUnknownWhenDetectionFails() {
        WechatOutboundIpService service = new WechatOutboundIpService("", () -> {
            throw new RuntimeException("network down");
        });

        service.detectOnStartup();

        assertEquals("unknown", service.currentOutboundIp());
    }
}
