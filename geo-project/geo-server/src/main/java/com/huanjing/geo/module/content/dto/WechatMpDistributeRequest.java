package com.huanjing.geo.module.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WechatMpDistributeRequest {
    @NotNull
    private Long mpAccountId;
    @NotNull
    private Long coverMaterialId;
    @NotBlank
    private String requestId;
}
