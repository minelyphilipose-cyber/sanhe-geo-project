package com.huanjing.geo.module.content.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class WechatMpAuthUrlVO {
    private String authUrl;
    private int expiresIn;
}
