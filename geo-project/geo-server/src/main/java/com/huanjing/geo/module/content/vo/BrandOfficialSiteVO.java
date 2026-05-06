package com.huanjing.geo.module.content.vo;

import com.huanjing.geo.module.content.entity.BrandOfficialSite;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * View object for BrandOfficialSite REST responses.
 *
 * <p>Critical: this MUST NOT include credentialsCipher or any other secret material.
 * The entity field is excluded by omission, not by @JsonIgnore on the entity, to keep
 * the entity reusable for internal flows that legitimately need the cipher
 * (e.g. OfficialCmsSiteAdapter publishing).
 */
@Data
public class BrandOfficialSiteVO {
    private Long id;
    private Long brandId;
    private String siteName;
    private String siteDomain;
    private String cmsFrameworkCode;
    private String tenantKey;
    private String apiEndpoint;
    private String authType;
    private String status;
    private LocalDateTime lastCheckAt;
    private String lastCheckResult;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    // credentialsCipher: intentionally omitted - never expose to API consumers
    // createdBy: intentionally omitted - internal audit field
    // Quota fields intentionally omitted: publishing quota is owned by the
    // customer's active package binding and channel snapshot, not by a site row.

    public static BrandOfficialSiteVO from(BrandOfficialSite entity) {
        if (entity == null) {
            return null;
        }
        BrandOfficialSiteVO vo = new BrandOfficialSiteVO();
        vo.setId(entity.getId());
        vo.setBrandId(entity.getBrandId());
        vo.setSiteName(entity.getSiteName());
        vo.setSiteDomain(entity.getSiteDomain());
        vo.setCmsFrameworkCode(entity.getCmsFrameworkCode());
        vo.setTenantKey(entity.getTenantKey());
        vo.setApiEndpoint(entity.getApiEndpoint());
        vo.setAuthType(entity.getAuthType());
        vo.setStatus(entity.getStatus());
        vo.setLastCheckAt(entity.getLastCheckAt());
        vo.setLastCheckResult(entity.getLastCheckResult());
        vo.setRemark(entity.getRemark());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
