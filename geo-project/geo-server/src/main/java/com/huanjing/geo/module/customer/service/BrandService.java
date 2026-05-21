package com.huanjing.geo.module.customer.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.customer.dto.BrandCreateRequest;
import com.huanjing.geo.module.customer.dto.BrandUpdateRequest;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.entity.BrandMaterial;
import com.huanjing.geo.module.customer.entity.BrandProfileVersion;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.customer.mapper.BrandMaterialMapper;
import com.huanjing.geo.module.customer.mapper.BrandProfileVersionMapper;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.ActivityLogService;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class BrandService {

    private static final Set<String> BRAND_STATUS = Set.of("draft", "active", "archived");
    private static final Set<String> GEO_SITE_STATUS = Set.of("active", "disabled");
    private static final Pattern GEO_SITE_CODE_PATTERN =
            Pattern.compile("^[a-z0-9](?:[a-z0-9_-]{0,62}[a-z0-9])?$");

    private final BrandMapper brandMapper;
    private final BrandMaterialMapper brandMaterialMapper;
    private final BrandProfileVersionMapper brandProfileVersionMapper;
    private final CompanyMapper companyMapper;
    private final ProjectMapper projectMapper;
    private final CurrentUserService currentUserService;
    private final ActivityLogService activityLogService;
    private final BrandProfileService brandProfileService;

    public Page<Brand> page(long current, long size, Long companyId, String keyword) {
        SysUser user = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("company.read");

        LambdaQueryWrapper<Brand> wrapper = new LambdaQueryWrapper<Brand>()
                .isNull(Brand::getDeletedAt)
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

    public Brand requireBrandWithAccess(Long id, boolean write) {
        SysUser user = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission(write ? "brand.update" : "company.read");
        Brand brand = requireBrand(id);
        Company company = requireCompany(brand.getCompanyId());
        currentUserService.ensurePartnerResourceAccess(user, company.getPartnerId(), "brand");
        return brand;
    }

    public Brand create(BrandCreateRequest req) {
        currentUserService.ensurePermission("brand.create");
        SysUser operator = currentUserService.requireCurrentUser();
        Company company = requireCompany(req.getCompanyId());
        validateBrandStatus(StringUtils.hasText(req.getStatus()) ? req.getStatus() : "active");
        currentUserService.ensurePartnerResourceAccess(operator, company.getPartnerId(), "company");

        Brand brand = new Brand();
        brand.setCompanyId(req.getCompanyId());
        String industry = normalizeIndustry(req.getIndustry());
        validateBrandIndustry(industry, company);
        brand.setIndustry(industry);
        brand.setBrandName(req.getBrandName());
        brand.setBrandShortName(req.getBrandShortName());
        brand.setBrandSlug(generateBrandSlug(req.getCompanyId()));
        brand.setMainBusiness(req.getMainBusiness());
        brand.setCoreProducts(req.getCoreProducts());
        brand.setBrandPositioning(req.getBrandPositioning());
        applyRegionFields(brand, req.getProvinceCode(), req.getProvinceName(), req.getCityCode(), req.getCityName(), req.getDistrictCode(), req.getDistrictName());
        brand.setServiceArea(StringUtils.hasText(req.getServiceArea())
                ? req.getServiceArea()
                : buildRegionDisplay(req.getProvinceName(), req.getCityName(), req.getDistrictName()));
        brand.setWebsite(req.getWebsite());
        brand.setOfficialAccount(req.getOfficialAccount());
        brand.setVideoAccount(req.getVideoAccount());
        brand.setDouyinAccount(req.getDouyinAccount());
        brand.setPhone(req.getPhone());
        brand.setPublicPhone(req.getPublicPhone());
        brand.setPublicAddress(req.getPublicAddress());
        brand.setWechat(req.getWechat());
        brand.setDescription(req.getDescription());
        brand.setBusinessIntro(req.getBusinessIntro());
        brand.setBrandQualificationDescription(req.getBrandQualificationDescription());
        brand.setBrandCaseDescription(req.getBrandCaseDescription());
        brand.setForbiddenPhrases(normalizeForbiddenPhrases(req.getForbiddenPhrases()));
        applyGeoSiteFields(brand, req.getGeoSiteCode(), req.getGeoSiteStatus(), null);
        applyIndustrySiteFields(brand, req.getIndustrySiteName(), req.getIndustrySiteCode());
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
        currentUserService.ensurePermission("brand.update");
        SysUser operator = currentUserService.requireCurrentUser();

        Brand brand = requireBrand(id);
        Company company = requireCompany(brand.getCompanyId());
        currentUserService.ensurePartnerResourceAccess(operator, company.getPartnerId(), "brand");
        validateBrandStatus(req.getStatus());
        Map<String, Object> before = snapshotBrand(brand);
        Brand existed = brandMapper.selectOne(new LambdaQueryWrapper<Brand>()
                .isNull(Brand::getDeletedAt)
                .eq(Brand::getCompanyId, brand.getCompanyId())
                .eq(Brand::getBrandSlug, req.getBrandSlug())
                .ne(Brand::getId, id));
        if (existed != null) {
            throw new BizException(400, "brand_slug already exists in company");
        }

        brand.setBrandName(req.getBrandName());
        brand.setBrandShortName(req.getBrandShortName());
        brand.setBrandSlug(req.getBrandSlug());
        String industry = normalizeIndustry(req.getIndustry());
        validateBrandIndustry(industry, company);
        brand.setIndustry(industry);
        brand.setMainBusiness(req.getMainBusiness());
        brand.setCoreProducts(req.getCoreProducts());
        brand.setBrandPositioning(req.getBrandPositioning());
        applyRegionFields(brand, req.getProvinceCode(), req.getProvinceName(), req.getCityCode(), req.getCityName(), req.getDistrictCode(), req.getDistrictName());
        brand.setServiceArea(StringUtils.hasText(req.getServiceArea())
                ? req.getServiceArea()
                : buildRegionDisplay(req.getProvinceName(), req.getCityName(), req.getDistrictName()));
        brand.setWebsite(req.getWebsite());
        brand.setOfficialAccount(req.getOfficialAccount());
        brand.setVideoAccount(req.getVideoAccount());
        brand.setDouyinAccount(req.getDouyinAccount());
        brand.setPhone(req.getPhone());
        brand.setPublicPhone(req.getPublicPhone());
        brand.setPublicAddress(req.getPublicAddress());
        brand.setWechat(req.getWechat());
        brand.setDescription(req.getDescription());
        brand.setBusinessIntro(req.getBusinessIntro());
        brand.setBrandQualificationDescription(req.getBrandQualificationDescription());
        brand.setBrandCaseDescription(req.getBrandCaseDescription());
        brand.setForbiddenPhrases(normalizeForbiddenPhrases(req.getForbiddenPhrases()));
        applyGeoSiteFields(brand, req.getGeoSiteCode(), req.getGeoSiteStatus(), id);
        applyIndustrySiteFields(brand, req.getIndustrySiteName(), req.getIndustrySiteCode());
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

    @Transactional
    public void delete(Long id) {
        currentUserService.ensurePermission("brand.delete");
        SysUser operator = currentUserService.requireCurrentUser();
        Brand brand = requireBrand(id);
        Company company = requireCompany(brand.getCompanyId());
        currentUserService.ensurePartnerResourceAccess(operator, company.getPartnerId(), "brand");

        List<Long> projectIds = projectMapper.selectList(
                new LambdaQueryWrapper<Project>()
                        .isNull(Project::getDeletedAt)
                        .eq(Project::getBrandId, id)
                        .select(Project::getId)
        ).stream().map(Project::getId).toList();
        if (!projectIds.isEmpty()) {
            throw new BizException(400, "Brand has projects, cannot delete");
        }

        brand.setDeletedAt(java.time.LocalDateTime.now());
        brand.setDeletedBy(operator.getId());
        brandMapper.updateById(brand);
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
        if (brand == null || brand.getDeletedAt() != null) {
            throw new BizException(404, "Brand not found");
        }
        return brand;
    }

    public Brand requireExistingBrand(Long id) {
        return requireBrand(id);
    }

    private Company requireCompany(Long id) {
        Company company = companyMapper.selectById(id);
        if (company == null || company.getDeletedAt() != null) {
            throw new BizException(404, "Company not found");
        }
        return company;
    }

    private void validateBrandStatus(String status) {
        if (!BRAND_STATUS.contains(status)) {
            throw new BizException(400, "Invalid brand status");
        }
    }

    private String generateBrandSlug(Long companyId) {
        for (int i = 0; i < 8; i++) {
            String slug = "brand_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            Brand existed = brandMapper.selectOne(new LambdaQueryWrapper<Brand>()
                    .isNull(Brand::getDeletedAt)
                    .eq(Brand::getCompanyId, companyId)
                    .eq(Brand::getBrandSlug, slug)
                    .last("LIMIT 1"));
            if (existed == null) {
                return slug;
            }
        }
        throw new BizException(500, "Failed to generate brand_slug");
    }

    private void applyGeoSiteFields(Brand brand, String rawCode, String rawStatus, Long selfId) {
        String code = trimToNull(rawCode);
        String status = trimToNull(rawStatus);
        if (code == null) {
            if (status != null) {
                throw new BizException(400, "geo_site_status requires geo_site_code");
            }
            brand.setGeoSiteCode(null);
            brand.setGeoSiteStatus(null);
            return;
        }

        code = code.toLowerCase(Locale.ROOT);
        if (!GEO_SITE_CODE_PATTERN.matcher(code).matches()) {
            throw new BizException(400, "Invalid geo_site_code");
        }
        Brand existed = brandMapper.selectOne(new LambdaQueryWrapper<Brand>()
                .isNull(Brand::getDeletedAt)
                .eq(Brand::getGeoSiteCode, code)
                .ne(selfId != null, Brand::getId, selfId)
                .last("LIMIT 1"));
        if (existed != null) {
            throw new BizException(400, "geo_site_code already exists");
        }

        if (status == null) {
            status = "active";
        }
        status = status.toLowerCase(Locale.ROOT);
        if (!GEO_SITE_STATUS.contains(status)) {
            throw new BizException(400, "Invalid geo_site_status");
        }
        brand.setGeoSiteCode(code);
        brand.setGeoSiteStatus(status);
    }

    private void applyIndustrySiteFields(Brand brand, String rawName, String rawCode) {
        String name = trimToNull(rawName);
        String code = trimToNull(rawCode);
        if (name == null && code == null) {
            brand.setIndustrySiteName(null);
            brand.setIndustrySiteCode(null);
            return;
        }
        if (code == null) {
            throw new BizException(400, "industry_site_code is required when industry site is configured");
        }
        if (!Pattern.compile("^[a-z0-9][a-z0-9_-]{1,127}$").matcher(code).matches()) {
            throw new BizException(400, "Invalid industry_site_code");
        }
        brand.setIndustrySiteName(name);
        brand.setIndustrySiteCode(code);
    }

    private Map<String, Object> snapshotBrand(Brand brand) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", brand.getId());
        snapshot.put("companyId", brand.getCompanyId());
        snapshot.put("industry", brand.getIndustry());
        snapshot.put("brandName", brand.getBrandName());
        snapshot.put("brandShortName", brand.getBrandShortName());
        snapshot.put("brandSlug", brand.getBrandSlug());
        snapshot.put("mainBusiness", brand.getMainBusiness());
        snapshot.put("coreProducts", brand.getCoreProducts());
        snapshot.put("brandPositioning", brand.getBrandPositioning());
        snapshot.put("provinceCode", brand.getProvinceCode());
        snapshot.put("provinceName", brand.getProvinceName());
        snapshot.put("cityCode", brand.getCityCode());
        snapshot.put("cityName", brand.getCityName());
        snapshot.put("districtCode", brand.getDistrictCode());
        snapshot.put("districtName", brand.getDistrictName());
        snapshot.put("status", brand.getStatus());
        snapshot.put("businessIntro", brand.getBusinessIntro());
        snapshot.put("brandQualificationDescription", brand.getBrandQualificationDescription());
        snapshot.put("brandCaseDescription", brand.getBrandCaseDescription());
        snapshot.put("geoSiteCode", brand.getGeoSiteCode());
        snapshot.put("geoSiteStatus", brand.getGeoSiteStatus());
        snapshot.put("industrySiteName", brand.getIndustrySiteName());
        snapshot.put("industrySiteCode", brand.getIndustrySiteCode());
        snapshot.put("officialAccount", brand.getOfficialAccount());
        snapshot.put("videoAccount", brand.getVideoAccount());
        snapshot.put("douyinAccount", brand.getDouyinAccount());
        snapshot.put("phone", brand.getPhone());
        snapshot.put("publicPhone", brand.getPublicPhone());
        snapshot.put("publicAddress", brand.getPublicAddress());
        snapshot.put("wechat", brand.getWechat());
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
        Set<String> companyTags = parseCompanyIndustryTags(company.getIndustryTags());
        if (companyTags.isEmpty()) {
            throw new BizException(400, "所属客户未配置行业，请先完善客户行业");
        }
        if (!companyTags.contains(industry.toLowerCase(Locale.ROOT))) {
            throw new BizException(400, "品牌行业必须从所属客户行业中选择");
        }
    }

    private String normalizeIndustry(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(400, "品牌行业不能为空");
        }
        return value.trim();
    }

    private String normalizeForbiddenPhrases(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        List<String> values = new ArrayList<>();
        try {
            JSONUtil.parseArray(raw).forEach(item -> {
                if (item != null && StringUtils.hasText(String.valueOf(item))) {
                    values.add(String.valueOf(item).trim());
                }
            });
        } catch (Exception ex) {
            for (String item : raw.split("[,，、;；\\n\\r]+")) {
                if (StringUtils.hasText(item)) {
                    values.add(item.trim());
                }
            }
        }
        List<String> normalized = values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        if (normalized.isEmpty()) {
            return null;
        }
        return JSONUtil.toJsonStr(normalized);
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
