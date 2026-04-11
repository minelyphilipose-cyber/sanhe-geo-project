package com.huanjing.geo.module.system.dto;

import lombok.Data;

@Data
public class RoleSimpleVO {
    private Long id;
    private String roleKey;
    private String roleName;
    private String roleType;
    private String status;
    private Integer sortOrder;
}
