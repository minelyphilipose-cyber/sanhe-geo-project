package com.huanjing.geo.module.content.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "geo.wechat.menu")
public class WechatMenuProperties {
    private String webBaseUrl = "https://www.huanjingaigeo.com";
    private List<Long> pocAllowedBrandIds = new ArrayList<>();
    private boolean pocWhitelistEnabled = true;
}
