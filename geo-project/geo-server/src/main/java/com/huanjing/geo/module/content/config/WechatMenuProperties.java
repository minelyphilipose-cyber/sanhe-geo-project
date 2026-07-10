package com.huanjing.geo.module.content.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "geo.wechat.menu")
public class WechatMenuProperties {
    private String webBaseUrl = "https://www.huanjingaigeo.com";
}
