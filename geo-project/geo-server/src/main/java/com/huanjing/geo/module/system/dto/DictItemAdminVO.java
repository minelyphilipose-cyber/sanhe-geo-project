package com.huanjing.geo.module.system.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DictItemAdminVO {
    private Long id;
    private String dictType;
    private String dictKey;
    private String dictValue;
    private Integer sortOrder;
    private Boolean enabled;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

