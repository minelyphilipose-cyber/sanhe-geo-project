package com.huanjing.geo.module.content.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DouyinReadinessCheckVO {
    private String code;
    private String label;
    private String status;
    private String message;
}
