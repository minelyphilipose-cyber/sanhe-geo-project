package com.huanjing.geo.module.customer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.customer.dto.CompanyCreateRequest;
import com.huanjing.geo.module.customer.dto.CompanyUpdateRequest;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyMapper companyMapper;
    private final CurrentUserService currentUserService;

    public Page<Company> page(long current, long size, String keyword, String ownerType, Long partnerId) {
        SysUser user = currentUserService.requireCurrentUser();
        LambdaQueryWrapper<Company> wrapper = new LambdaQueryWrapper<Company>()
                .orderByDesc(Company::getCreatedAt);

        if (StringUtils.hasText(keyword)) {
            wrapper.like(Company::getCompanyName, keyword);
        }
        if (StringUtils.hasText(ownerType)) {
            wrapper.eq(Company::getOwnerType, ownerType);
        }

        Long scopePartnerId = currentUserService.requirePartnerScope(user);
        if (scopePartnerId != null) {
            wrapper.eq(Company::getPartnerId, scopePartnerId);
        } else if (partnerId != null) {
            wrapper.eq(Company::getPartnerId, partnerId);
        }

        return companyMapper.selectPage(new Page<>(current, size), wrapper);
    }

    public Company detail(Long id) {
        SysUser user = currentUserService.requireCurrentUser();
        Company company = requireCompany(id);

        Long scopePartnerId = currentUserService.requirePartnerScope(user);
        if (scopePartnerId != null && !scopePartnerId.equals(company.getPartnerId())) {
            throw new BizException(403, "No permission to access this company");
        }
        return company;
    }

    public Company create(CompanyCreateRequest req) {
        currentUserService.ensureInternalOperator();

        Company company = new Company();
        company.setCompanyName(req.getCompanyName());
        company.setIndustry(req.getIndustry());
        company.setCity(req.getCity());
        company.setOwnerType(req.getOwnerType());
        company.setPartnerId(req.getPartnerId());
        company.setSalesOwnerId(req.getSalesOwnerId());
        company.setReferralSource(req.getReferralSource());
        company.setStatus(StringUtils.hasText(req.getStatus()) ? req.getStatus() : "potential");
        company.setRemark(req.getRemark());
        companyMapper.insert(company);
        return company;
    }

    public Company update(Long id, CompanyUpdateRequest req) {
        currentUserService.ensureInternalOperator();

        Company company = requireCompany(id);
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
        return company;
    }

    private Company requireCompany(Long id) {
        Company company = companyMapper.selectById(id);
        if (company == null) {
            throw new BizException(404, "Company not found");
        }
        return company;
    }
}
