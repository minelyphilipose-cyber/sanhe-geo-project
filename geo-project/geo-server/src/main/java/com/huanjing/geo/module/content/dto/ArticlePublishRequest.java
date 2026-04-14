package com.huanjing.geo.module.content.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ArticlePublishRequest {
    @NotBlank
    private String publishAction;
    private String channelName;
    private String channelUrl;
    private String note;
}

