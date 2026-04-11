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
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class BrandService {

    private final BrandMapper brandMapper;
    private final CompanyMapper companyMapper;
    private final CurrentUserService currentUserService;

    public Page<Brand> page(long current, long size, Long companyId, String keyword) {
        SysUser user = currentUserService.requireCurrentUser();

        LambdaQueryWrapper<Brand> wrapper = new LambdaQueryWrapper<Brand>()
                .orderByDesc(Brand::getCreatedAt);

        if (companyId != null) {
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
        Brand brand = requireBrand(id);

        Long scopePartnerId = currentUserService.requirePartnerScope(user);
        if (scopePartnerId != null) {
            Company company = requireCompany(brand.getCompanyId());
            if (!scopePartnerId.equals(company.getPartnerId())) {
                throw new BizException(403, "No permission to access this brand");
            }
        }
        return brand;
    }

    public Brand create(BrandCreateRequest req) {
        currentUserService.ensureInternalOperator();
        requireCompany(req.getCompanyId());

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
        return brand;
    }

    public Brand update(Long id, BrandUpdateRequest req) {
        currentUserService.ensureInternalOperator();

        Brand brand = requireBrand(id);
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
        return brand;
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
}
