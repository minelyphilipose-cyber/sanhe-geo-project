package com.huanjing.geo.module.content.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MpAccountDevSeedRequest {
    @NotNull
    private Long brandId;
    private String status = "active";
    private String accountName = "Mock 微信公众号";
    private String authorizerAppid = "mock_authorizer_appid";
}
