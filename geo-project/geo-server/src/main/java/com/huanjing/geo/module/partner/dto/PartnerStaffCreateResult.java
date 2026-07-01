package com.huanjing.geo.module.partner.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PartnerStaffCreateResult {
    private PartnerStaffVO staff;
    private String initialPassword;
}
