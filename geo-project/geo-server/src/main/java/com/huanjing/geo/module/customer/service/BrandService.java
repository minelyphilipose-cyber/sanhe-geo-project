package com.huanjing.geo.module.customer.service;

import cn.hutool.json.JSONUtil;
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
import com.huanjing.geo.module.system.entity.SysDictItem;
import com.huanjing.geo.module.system.mapper.SysDictItemMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.ActivityLogService;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BrandService {

    private static final Set<String> BRAND_STATUS = Set.of("draft", "active", "archived");

    private final BrandMapper brandMapper;
    private final CompanyMapper companyMapper;
    private final ProjectMapper projectMapper;
    private final CurrentUserService currentUserService;
    private final ActivityLogService activityLogService;
    private final BrandProfileService brandProfileService;
    private final SysDictItemMapper sysDictItemMapper;

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
        String industry = normalizeIndustry(req.getIndustry());
        validateBrandIndustry(industry, company);
        brand.setIndustry(industry);
        brand.setBrandName(req.getBrandName());
        brand.setBrandSlug(req.getBrandSlug());
        brand.setMainBusiness(req.getMainBusiness());
        applyRegionFields(brand, req.getProvinceCode(), req.getProvinceName(), req.getCityCode(), req.getCityName(), req.getDistrictCode(), req.getDistrictName());
        brand.setServiceArea(StringUtils.hasText(req.getServiceArea())
                ? req.getServiceArea()
                : buildRegionDisplay(req.getProvinceName(), req.getCityName(), req.getDistrictName()));
        brand.setWebsite(req.getWebsite());
        brand.setOfficialAccount(req.getOfficialAccount());
        brand.setVideoAccount(req.getVideoAccount());
        brand.setDouyinAccount(req.getDouyinAccount());
        brand.setPhone(req.getPhone());
        brand.setWechat(req.getWechat());
        brand.setDescription(req.getDescription());
        brand.setBusinessIntro(req.getBusinessIntro());
        brand.setStandardBrandStatement(req.getStandardBrandStatement());
        brand.setBusinessStandardStatement(req.getBusinessStandardStatement());
        brand.setForbiddenPhrases(req.getForbiddenPhrases());
        brand.setStatus(StringUtils.hasText(req.getStatus()) ? req.getStatus() : "active");
        brandMapper.insert(brand);
        brandProfileService.createProfileVersionSnapshot(
                brand,
                operator.getId(),
                StringUtils.hasText(req.getVersionChangeReason()) ? req.getVersionChangeReason() : "brand.create"
        );
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
        String industry = normalizeIndustry(req.getIndustry());
        validateBrandIndustry(industry, company);
        brand.setIndustry(industry);
        brand.setMainBusiness(req.getMainBusiness());
        applyRegionFields(brand, req.getProvinceCode(), req.getProvinceName(), req.getCityCode(), req.getCityName(), req.getDistrictCode(), req.getDistrictName());
        brand.setServiceArea(StringUtils.hasText(req.getServiceArea())
                ? req.getServiceArea()
                : buildRegionDisplay(req.getProvinceName(), req.getCityName(), req.getDistrictName()));
        brand.setWebsite(req.getWebsite());
        brand.setOfficialAccount(req.getOfficialAccount());
        brand.setVideoAccount(req.getVideoAccount());
        brand.setDouyinAccount(req.getDouyinAccount());
        brand.setPhone(req.getPhone());
        brand.setWechat(req.getWechat());
        brand.setDescription(req.getDescription());
        brand.setBusinessIntro(req.getBusinessIntro());
        brand.setStandardBrandStatement(req.getStandardBrandStatement());
        brand.setBusinessStandardStatement(req.getBusinessStandardStatement());
        brand.setForbiddenPhrases(req.getForbiddenPhrases());
        brand.setStatus(req.getStatus());
        brandMapper.updateById(brand);
        brandProfileService.createProfileVersionSnapshot(
                brand,
                operator.getId(),
                StringUtils.hasText(req.getVersionChangeReason()) ? req.getVersionChangeReason() : "brand.update"
        );
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
        snapshot.put("industry", brand.getIndustry());
        snapshot.put("brandName", brand.getBrandName());
        snapshot.put("brandSlug", brand.getBrandSlug());
        snapshot.put("provinceCode", brand.getProvinceCode());
        snapshot.put("provinceName", brand.getProvinceName());
        snapshot.put("cityCode", brand.getCityCode());
        snapshot.put("cityName", brand.getCityName());
        snapshot.put("districtCode", brand.getDistrictCode());
        snapshot.put("districtName", brand.getDistrictName());
        snapshot.put("status", brand.getStatus());
        snapshot.put("businessIntro", brand.getBusinessIntro());
        snapshot.put("businessStandardStatement", brand.getBusinessStandardStatement());
        snapshot.put("officialAccount", brand.getOfficialAccount());
        snapshot.put("videoAccount", brand.getVideoAccount());
        snapshot.put("douyinAccount", brand.getDouyinAccount());
        return snapshot;
    }

    private void applyRegionFields(Brand brand,
                                   String provinceCode,
                                   String provinceName,
                                   String cityCode,
                                   String cityName,
                                   String districtCode,
                                   String districtName) {
        brand.setProvinceCode(trimToNull(provinceCode));
        brand.setProvinceName(trimToNull(provinceName));
        brand.setCityCode(trimToNull(cityCode));
        brand.setCityName(trimToNull(cityName));
        brand.setDistrictCode(trimToNull(districtCode));
        brand.setDistrictName(trimToNull(districtName));
    }

    private String buildRegionDisplay(String provinceName, String cityName, String districtName) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(provinceName)) {
            parts.add(provinceName.trim());
        }
        if (StringUtils.hasText(cityName)) {
            parts.add(cityName.trim());
        }
        if (StringUtils.hasText(districtName)) {
            parts.add(districtName.trim());
        }
        return parts.isEmpty() ? null : String.join(" ", parts);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private void validateBrandIndustry(String industry, Company company) {
        Set<String> validTags = queryEnabledIndustryTags();
        if (!validTags.contains(industry)) {
            throw new BizException(400, "品牌行业值不在行业字典范围内");
        }
        Set<String> companyTags = parseCompanyIndustryTags(company.getIndustryTags());
        if (companyTags.isEmpty()) {
            throw new BizException(400, "所属客户未配置行业，请先完善客户行业");
        }
        if (!companyTags.contains(industry)) {
            throw new BizException(400, "品牌行业必须从所属客户行业中选择");
        }
    }

    private String normalizeIndustry(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(400, "品牌行业不能为空");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private Set<String> queryEnabledIndustryTags() {
        return sysDictItemMapper.selectList(new LambdaQueryWrapper<SysDictItem>()
                        .eq(SysDictItem::getDictType, "industry_tag")
                        .eq(SysDictItem::getEnabled, true)
                        .select(SysDictItem::getDictKey))
                .stream()
                .map(SysDictItem::getDictKey)
                .filter(StringUtils::hasText)
                .map(item -> item.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    private Set<String> parseCompanyIndustryTags(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Set.of();
        }
        try {
            return JSONUtil.parseArray(raw).stream()
                    .map(String::valueOf)
                    .filter(StringUtils::hasText)
                    .map(item -> item.trim().toLowerCase(Locale.ROOT))
                    .collect(Collectors.toCollection(HashSet::new));
        } catch (Exception ex) {
            return Set.of();
        }
    }
}
