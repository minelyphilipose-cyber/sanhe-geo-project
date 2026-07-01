package com.huanjing.geo.module.partner.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PartnerStaffVO {
    private Long id;
    private String username;
    private String displayName;
    private Long partnerId;
    private String phone;
    private String email;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
