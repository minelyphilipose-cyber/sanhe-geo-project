package com.huanjing.geo.module.customer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.customer.dto.BrandCreateRequest;
import com.huanjing.geo.module.customer.dto.BrandUpdateRequest;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.ActivityLogService;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BrandService {

    private static final Set<String> BRAND_STATUS = Set.of("draft", "active", "archived");

    private final BrandMapper brandMapper;
    private final CompanyMapper companyMapper;
    private final ProjectMapper projectMapper;
    private final CurrentUserService currentUserService;
    private final ActivityLogService activityLogService;

    public Page<Brand> page(long current, long size, Long companyId, String keyword) {
        SysUser user = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("company.read");

        LambdaQueryWrapper<Brand> wrapper = new LambdaQueryWrapper<Brand>()
                .orderByDesc(Brand::getCreatedAt);

        if (companyId != null) {
            Company filterCompany = requireCompany(companyId);
            currentUserService.ensurePartnerResourceAccess(user, filterCompany.getPartnerId(), "company");
            wrapper.eq(Brand::getCompanyId, companyId);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Brand::getBrandName, keyword);
        }

        Long scopePartnerId = currentUserService.requirePartnerScope(user);
        if (scopePartnerId != null) {
            wrapper.inSql(Brand::getCompanyId, "select id from company where partner_id = " + scopePartnerId);
        }

        return brandMapper.selectPage(new Page<>(current, size), wrapper);
    }

    public Brand detail(Long id) {
        SysUser user = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("company.read");
        Brand brand = requireBrand(id);
        Company company = requireCompany(brand.getCompanyId());
        currentUserService.ensurePartnerResourceAccess(user, company.getPartnerId(), "brand");
        return brand;
    }

    public Brand create(BrandCreateRequest req) {
        currentUserService.ensurePermission("company.write");
        SysUser operator = currentUserService.requireCurrentUser();
        Company company = requireCompany(req.getCompanyId());
        validateBrandStatus(StringUtils.hasText(req.getStatus()) ? req.getStatus() : "active");
        currentUserService.ensurePartnerResourceAccess(operator, company.getPartnerId(), "company");

        Brand existed = brandMapper.selectOne(new LambdaQueryWrapper<Brand>()
                .eq(Brand::getCompanyId, req.getCompanyId())
                .eq(Brand::getBrandSlug, req.getBrandSlug()));
        if (existed != null) {
            throw new BizException(400, "brand_slug already exists in company");
        }

        Brand brand = new Brand();
        brand.setCompanyId(req.getCompanyId());
        brand.setBrandName(req.getBrandName());
        brand.setBrandSlug(req.getBrandSlug());
        brand.setMainBusiness(req.getMainBusiness());
        brand.setServiceArea(req.getServiceArea());
        brand.setWebsite(req.getWebsite());
        brand.setPhone(req.getPhone());
        brand.setWechat(req.getWechat());
        brand.setDescription(req.getDescription());
        brand.setStandardBrandStatement(req.getStandardBrandStatement());
        brand.setForbiddenPhrases(req.getForbiddenPhrases());
        brand.setStatus(StringUtils.hasText(req.getStatus()) ? req.getStatus() : "active");
        brandMapper.insert(brand);
        activityLogService.logAction(
                operator.getId(),
                "brand.create",
                "brand",
                brand.getId(),
                null,
                snapshotBrand(brand),
                Map.of("companyId", company.getId())
        );
        return brand;
    }

    public Brand update(Long id, BrandUpdateRequest req) {
        currentUserService.ensurePermission("company.write");
        SysUser operator = currentUserService.requireCurrentUser();

        Brand brand = requireBrand(id);
        Company company = requireCompany(brand.getCompanyId());
        currentUserService.ensurePartnerResourceAccess(operator, company.getPartnerId(), "brand");
        validateBrandStatus(req.getStatus());
        Map<String, Object> before = snapshotBrand(brand);
        Brand existed = brandMapper.selectOne(new LambdaQueryWrapper<Brand>()
                .eq(Brand::getCompanyId, brand.getCompanyId())
                .eq(Brand::getBrandSlug, req.getBrandSlug())
                .ne(Brand::getId, id));
        if (existed != null) {
            throw new BizException(400, "brand_slug already exists in company");
        }

        brand.setBrandName(req.getBrandName());
        brand.setBrandSlug(req.getBrandSlug());
        brand.setMainBusiness(req.getMainBusiness());
        brand.setServiceArea(req.getServiceArea());
        brand.setWebsite(req.getWebsite());
        brand.setPhone(req.getPhone());
        brand.setWechat(req.getWechat());
        brand.setDescription(req.getDescription());
        brand.setStandardBrandStatement(req.getStandardBrandStatement());
        brand.setForbiddenPhrases(req.getForbiddenPhrases());
        brand.setStatus(req.getStatus());
        brandMapper.updateById(brand);
        activityLogService.logAction(
                operator.getId(),
                "brand.update",
                "brand",
                brand.getId(),
                before,
                snapshotBrand(brand),
                Map.of("companyId", company.getId())
        );
        return brand;
    }

    public void delete(Long id) {
        currentUserService.ensurePermission("company.write");
        SysUser operator = currentUserService.requireCurrentUser();
        Brand brand = requireBrand(id);
        Company company = requireCompany(brand.getCompanyId());
        currentUserService.ensurePartnerResourceAccess(operator, company.getPartnerId(), "brand");

        Long projectCount = projectMapper.selectCount(
                new LambdaQueryWrapper<Project>().eq(Project::getBrandId, id)
        );
        if (projectCount != null && projectCount > 0) {
            throw new BizException(400, "Brand has projects, cannot delete");
        }

        brandMapper.deleteById(id);
        activityLogService.logAction(
                operator.getId(),
                "brand.delete",
                "brand",
                id,
                snapshotBrand(brand),
                null,
                Map.of("companyId", company.getId())
        );
    }

    private Brand requireBrand(Long id) {
        Brand brand = brandMapper.selectById(id);
        if (brand == null) {
            throw new BizException(404, "Brand not found");
        }
        return brand;
    }

    private Company requireCompany(Long id) {
        Company company = companyMapper.selectById(id);
        if (company == null) {
            throw new BizException(404, "Company not found");
        }
        return company;
    }

    private void validateBrandStatus(String status) {
        if (!BRAND_STATUS.contains(status)) {
            throw new BizException(400, "Invalid brand status");
        }
    }

    private Map<String, Object> snapshotBrand(Brand brand) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", brand.getId());
        snapshot.put("companyId", brand.getCompanyId());
        snapshot.put("brandName", brand.getBrandName());
        snapshot.put("brandSlug", brand.getBrandSlug());
        snapshot.put("status", brand.getStatus());
        return snapshot;
    }
}
