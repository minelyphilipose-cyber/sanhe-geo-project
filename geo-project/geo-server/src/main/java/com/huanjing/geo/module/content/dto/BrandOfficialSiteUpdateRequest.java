package com.huanjing.geo.module.content.dto;

import lombok.Data;

/**
 * Wrapper types distinguish "field not provided" from "field provided as null/blank".
 * Blank credentials intentionally mean "keep the existing encrypted value".
 */
@Data
public class BrandOfficialSiteUpdateRequest {
    private String siteName;
    private String siteDomain;
    private String cmsFrameworkCode;
    private String tenantKey;
    private String apiEndpoint;
    private String authType;
    private String credentials;
    private String remark;
}
