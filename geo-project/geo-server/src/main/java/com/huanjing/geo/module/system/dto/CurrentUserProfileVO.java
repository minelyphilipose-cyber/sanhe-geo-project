package com.huanjing.geo.module.system.dto;

import lombok.Data;

import java.util.Set;

@Data
public class CurrentUserProfileVO {
    private Long id;
    private String username;
    private String displayName;
    private String role;
    private Long partnerId;
    private String phone;
    private String email;
    private String avatarUrl;
    private Boolean isActive;
    private Set<String> permissions;
}
