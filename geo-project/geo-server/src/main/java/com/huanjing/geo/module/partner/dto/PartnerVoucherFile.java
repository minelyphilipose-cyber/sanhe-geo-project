package com.huanjing.geo.module.partner.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PartnerVoucherFile {
    private String fileName;
    private Long fileSize;
    private String contentType;
    private String objectKey;
    private String downloadUrl;
}
