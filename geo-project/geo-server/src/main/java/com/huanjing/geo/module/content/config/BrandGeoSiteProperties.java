package com.huanjing.geo.module.content.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "geo.brand-geo-site")
public class BrandGeoSiteProperties {
    private String endpoint;
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 30000;
}
