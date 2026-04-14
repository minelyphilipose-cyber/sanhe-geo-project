package com.huanjing.geo.module.partner.dto;

import com.huanjing.geo.module.partner.entity.Partner;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PartnerCreateResult {
    private Partner partner;
    private String username;
    private String initialPassword;
}

