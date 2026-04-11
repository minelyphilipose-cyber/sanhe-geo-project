package com.huanjing.geo.module.customer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.customer.dto.CompanyCreateRequest;
import com.huanjing.geo.module.customer.dto.CompanyUpdateRequest;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
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
public class CompanyService {

    private static final Set<String> OWNER_TYPES = Set.of("direct", "partner", "joint");
    private static final Set<String> STATUSES = Set.of("potential", "signed", "inactive");

    private final CompanyMapper companyMapper;
    private final BrandMapper brandMapper;
    private final CurrentUserService currentUserService;
    private final ActivityLogService activityLogService;

    public Page<Company> page(long current, long size, String keyword, String ownerType, Long partnerId) {
        SysUser user = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("company.read");
        LambdaQueryWrapper<Company> wrapper = new LambdaQueryWrapper<Company>()
                .orderByDesc(Company::getCreatedAt);

        if (StringUtils.hasText(keyword)) {
            wrapper.like(Company::getCompanyName, keyword);
        }
        if (StringUtils.hasText(ownerType)) {
            wrapper.eq(Company::getOwnerType, ownerType);
        }

        Long scopePartnerId = currentUserService.resolvePartnerQueryScope(user, partnerId);
        if (scopePartnerId != null) {
            wrapper.eq(Company::getPartnerId, scopePartnerId);
        }

        return companyMapper.selectPage(new Page<>(current, size), wrapper);
    }

    public Company detail(Long id) {
        SysUser user = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("company.read");
        Company company = requireCompany(id);
        currentUserService.ensurePartnerResourceAccess(user, company.getPartnerId(), "company");
        return company;
    }

    public Company create(CompanyCreateRequest req) {
        currentUserService.ensurePermission("company.write");
        SysUser operator = currentUserService.requireCurrentUser();
        validateOwnerBinding(req.getOwnerType(), req.getPartnerId());
        String status = StringUtils.hasText(req.getStatus()) ? req.getStatus() : "potential";
        validateStatus(status);

        Company company = new Company();
        company.setCompanyName(req.getCompanyName());
        company.setIndustry(req.getIndustry());
        company.setCity(req.getCity());
        company.setOwnerType(req.getOwnerType());
        company.setPartnerId(req.getPartnerId());
        company.setSalesOwnerId(req.getSalesOwnerId());
        company.setReferralSource(req.getReferralSource());
        company.setStatus(status);
        company.setRemark(req.getRemark());
        companyMapper.insert(company);
        activityLogService.logAction(
                operator.getId(),
                "company.create",
                "company",
                company.getId(),
                null,
                snapshotCompany(company),
                null
        );
        return company;
    }

    public Company update(Long id, CompanyUpdateRequest req) {
        currentUserService.ensurePermission("company.write");
        SysUser operator = currentUserService.requireCurrentUser();
        validateOwnerBinding(req.getOwnerType(), req.getPartnerId());
        validateStatus(req.getStatus());

        Company company = requireCompany(id);
        Map<String, Object> before = snapshotCompany(company);
        company.setCompanyName(req.getCompanyName());
        company.setIndustry(req.getIndustry());
        company.setCity(req.getCity());
        company.setOwnerType(req.getOwnerType());
        company.setPartnerId(req.getPartnerId());
        company.setSalesOwnerId(req.getSalesOwnerId());
        company.setReferralSource(req.getReferralSource());
        company.setStatus(req.getStatus());
        company.setRemark(req.getRemark());
        companyMapper.updateById(company);
        activityLogService.logAction(
                operator.getId(),
                "company.update",
                "company",
                company.getId(),
                before,
                snapshotCompany(company),
                null
        );
        return company;
    }

    public void delete(Long id) {
        currentUserService.ensurePermission("company.write");
        SysUser operator = currentUserService.requireCurrentUser();
        Company company = requireCompany(id);

        Long brandCount = brandMapper.selectCount(
                new LambdaQueryWrapper<Brand>().eq(Brand::getCompanyId, id)
        );
        if (brandCount != null && brandCount > 0) {
            throw new BizException(400, "Company has brands, cannot delete");
        }

        companyMapper.deleteById(id);
        activityLogService.logAction(
                operator.getId(),
                "company.delete",
                "company",
                id,
                snapshotCompany(company),
                null,
                null
        );
    }

    private Company requireCompany(Long id) {
        Company company = companyMapper.selectById(id);
        if (company == null) {
            throw new BizException(404, "Company not found");
        }
        return company;
    }

    private void validateOwnerBinding(String ownerType, Long partnerId) {
        if (!OWNER_TYPES.contains(ownerType)) {
            throw new BizException(400, "Invalid owner_type");
        }
        if ("direct".equals(ownerType) && partnerId != null) {
            throw new BizException(400, "direct company must not bind partner_id");
        }
        if (("partner".equals(ownerType) || "joint".equals(ownerType)) && partnerId == null) {
            throw new BizException(400, "partner/joint company must bind partner_id");
        }
    }

    private void validateStatus(String status) {
        if (!STATUSES.contains(status)) {
            throw new BizException(400, "Invalid status");
        }
    }

    private Map<String, Object> snapshotCompany(Company company) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", company.getId());
        snapshot.put("companyName", company.getCompanyName());
        snapshot.put("ownerType", company.getOwnerType());
        snapshot.put("partnerId", company.getPartnerId());
        snapshot.put("status", company.getStatus());
        return snapshot;
    }
}
