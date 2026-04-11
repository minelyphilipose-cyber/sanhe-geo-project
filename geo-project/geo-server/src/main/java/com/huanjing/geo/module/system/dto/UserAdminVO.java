package com.huanjing.geo.module.system.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserAdminVO {
    private Long id;
    private String username;
    private String displayName;
    private String primaryRole;
    private List<String> roleKeys;
    private Long partnerId;
    private String phone;
    private String email;
    private Boolean isActive;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
