package com.huanjing.geo.module.content.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "geo.douyin.feature")
public class DouyinFeatureProperties {
    private ImageText imageText = new ImageText();

    @Data
    public static class ImageText {
        private boolean enabled = false;
    }
}
