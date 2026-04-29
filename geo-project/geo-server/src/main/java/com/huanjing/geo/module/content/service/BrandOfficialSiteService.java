package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.dto.BrandOfficialSiteCreateRequest;
import com.huanjing.geo.module.content.dto.BrandOfficialSiteUpdateRequest;
import com.huanjing.geo.module.content.entity.BrandOfficialSite;
import com.huanjing.geo.module.content.mapper.BrandOfficialSiteMapper;
import com.huanjing.geo.module.content.service.adapter.OfficialCmsSiteAdapter;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import com.huanjing.geo.module.system.service.MpCredentialCipherService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BrandOfficialSiteService {

    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_DISABLED = "disabled";
    private static final String DEFAULT_AUTH_TYPE = "bearer_token";

    private final BrandOfficialSiteMapper brandOfficialSiteMapper;
    private final CurrentUserService currentUserService;
    private final MpCredentialCipherService mpCredentialCipherService;

    @Transactional
    public BrandOfficialSite createSite(Long brandId, BrandOfficialSiteCreateRequest req) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensureBrandAccess(operator, brandId, "brand");

        String credentials = requireTrimmed(req.getCredentials(), "credentials is required");
        BrandOfficialSite entity = new BrandOfficialSite();
        entity.setBrandId(brandId);
        entity.setSiteName(requireTrimmed(req.getSiteName(), "site_name is required"));
        entity.setSiteDomain(trimToNull(req.getSiteDomain()));
        entity.setCmsFrameworkCode(requireSupportedFrameworkCode(req.getCmsFrameworkCode()));
        entity.setTenantKey(requireTrimmed(req.getTenantKey(), "tenant_key is required"));
        entity.setApiEndpoint(requireTrimmed(req.getApiEndpoint(), "api_endpoint is required"));
        entity.setAuthType(StringUtils.hasText(req.getAuthType()) ? req.getAuthType().trim() : DEFAULT_AUTH_TYPE);
        entity.setCredentialsCipher(mpCredentialCipherService.encryptForStorage(credentials));
        entity.setStatus(STATUS_ACTIVE);
        entity.setRemark(trimToNull(req.getRemark()));
        entity.setCreatedBy(operator.getId());
        brandOfficialSiteMapper.insert(entity);
        return entity;
    }

    @Transactional
    public BrandOfficialSite updateSite(Long siteId, BrandOfficialSiteUpdateRequest req) {
        BrandOfficialSite entity = requireSite(siteId);
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensureBrandAccess(operator, entity.getBrandId(), "brand");

        applyIfPresent(req.getSiteName(), value -> entity.setSiteName(requireTrimmed(value, "site_name is required")));
        applyIfPresent(req.getSiteDomain(), value -> entity.setSiteDomain(trimToNull(value)));
        applyIfPresent(req.getCmsFrameworkCode(), value -> entity.setCmsFrameworkCode(requireSupportedFrameworkCode(value)));
        applyIfPresent(req.getTenantKey(), value -> entity.setTenantKey(requireTrimmed(value, "tenant_key is required")));
        applyIfPresent(req.getApiEndpoint(), value -> entity.setApiEndpoint(requireTrimmed(value, "api_endpoint is required")));
        applyIfPresent(req.getAuthType(), value -> entity.setAuthType(StringUtils.hasText(value) ? value.trim() : DEFAULT_AUTH_TYPE));
        applyIfPresent(req.getRemark(), value -> entity.setRemark(trimToNull(value)));
        if (StringUtils.hasText(req.getCredentials())) {
            entity.setCredentialsCipher(mpCredentialCipherService.encryptForStorage(req.getCredentials().trim()));
        }
        brandOfficialSiteMapper.updateById(entity);
        return entity;
    }

    @Transactional
    public void deleteSite(Long siteId) {
        BrandOfficialSite entity = requireSite(siteId);
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensureBrandAccess(operator, entity.getBrandId(), "brand");
        brandOfficialSiteMapper.deleteById(siteId);
    }

    @Transactional
    public BrandOfficialSite disableSite(Long siteId) {
        BrandOfficialSite entity = requireSite(siteId);
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensureBrandAccess(operator, entity.getBrandId(), "brand");
        entity.setStatus(STATUS_DISABLED);
        brandOfficialSiteMapper.updateById(entity);
        return entity;
    }

    @Transactional
    public BrandOfficialSite enableSite(Long siteId) {
        BrandOfficialSite entity = requireSite(siteId);
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensureBrandAccess(operator, entity.getBrandId(), "brand");
        entity.setStatus(STATUS_ACTIVE);
        brandOfficialSiteMapper.updateById(entity);
        return entity;
    }

    public BrandOfficialSite getSite(Long siteId) {
        BrandOfficialSite entity = requireSite(siteId);
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensureBrandAccess(operator, entity.getBrandId(), "brand");
        return entity;
    }

    public List<BrandOfficialSite> listByBrand(Long brandId) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensureBrandAccess(operator, brandId, "brand");
        return brandOfficialSiteMapper.selectList(
                new LambdaQueryWrapper<BrandOfficialSite>()
                        .eq(BrandOfficialSite::getBrandId, brandId)
                        .orderByDesc(BrandOfficialSite::getUpdatedAt)
                        .orderByDesc(BrandOfficialSite::getId)
        );
    }

    private BrandOfficialSite requireSite(Long siteId) {
        BrandOfficialSite entity = brandOfficialSiteMapper.selectById(siteId);
        if (entity == null) {
            throw new BizException(404, "Brand official site not found");
        }
        return entity;
    }

    private String requireTrimmed(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(400, message);
        }
        return value.trim();
    }

    private String requireSupportedFrameworkCode(String value) {
        String frameworkCode = requireTrimmed(value, "cms_framework_code is required");
        if (!OfficialCmsSiteAdapter.FRAMEWORK_CODE_DEFAULT.equals(frameworkCode)) {
            throw new BizException(400,
                    "Phase 1 only supports cms_framework_code='" + OfficialCmsSiteAdapter.FRAMEWORK_CODE_DEFAULT + "'");
        }
        return frameworkCode;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void applyIfPresent(String value, java.util.function.Consumer<String> consumer) {
        if (value != null) {
            consumer.accept(value);
        }
    }
}
