package com.huanjing.geo.module.customer.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.module.customer.dto.CompanyDeductRequest;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.customer.dto.CompanyCreateRequest;
import com.huanjing.geo.module.customer.dto.CompanyRechargeRequest;
import com.huanjing.geo.module.customer.dto.CompanyUpdateRequest;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.entity.CompanyAccount;
import com.huanjing.geo.module.customer.entity.CompanyAccountTxn;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.mapper.CompanyAccountMapper;
import com.huanjing.geo.module.customer.mapper.CompanyAccountTxnMapper;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.partner.entity.Partner;
import com.huanjing.geo.module.partner.mapper.PartnerMapper;
import com.huanjing.geo.module.system.entity.SysDictItem;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.SysDictItemMapper;
import com.huanjing.geo.module.system.service.ActivityLogService;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.stream.Collectors;
import cn.hutool.core.util.RandomUtil;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private static final Set<String> OWNER_TYPES = Set.of("direct", "partner", "joint");
    private static final Set<String> SOURCE_TYPES = Set.of("internal", "partner");
    private static final Set<String> STATUSES = Set.of("potential", "signed", "inactive");

    private final CompanyMapper companyMapper;
    private final CompanyAccountMapper companyAccountMapper;
    private final CompanyAccountTxnMapper companyAccountTxnMapper;
    private final BrandMapper brandMapper;
    private final PartnerMapper partnerMapper;
    private final SysDictItemMapper sysDictItemMapper;
    private final CurrentUserService currentUserService;
    private final ActivityLogService activityLogService;

    public Page<Company> page(long current, long size, String keyword, String ownerType, Long partnerId) {
        SysUser user = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("company.read");
        LambdaQueryWrapper<Company> wrapper = new LambdaQueryWrapper<Company>()
                .isNull(Company::getDeletedAt)
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
        if ("sales".equals(user.getRole())) {
            wrapper.eq(Company::getSalesOwnerId, user.getId())
                    .eq(Company::getStatus, "signed");
        }

        return companyMapper.selectPage(new Page<>(current, size), wrapper);
    }

    public Company detail(Long id) {
        SysUser user = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("company.read");
        Company company = requireCompany(id);
        currentUserService.ensurePartnerResourceAccess(user, company.getPartnerId(), "company");
        ensureSalesCompanyAccess(user, company);
        return company;
    }

    @Transactional
    public Company create(CompanyCreateRequest req) {
        currentUserService.ensurePermission("company.create");
        SysUser operator = currentUserService.requireCurrentUser();
        String ownerType = resolveCreateOwnerType(operator);
        Long partnerId = resolveCreatePartnerId(operator);
        validateOwnerBinding(ownerType, partnerId);
        String sourceType = resolveCreateSourceType(operator);
        validateSourceBinding(sourceType, partnerId);
        currentUserService.ensurePartnerResourceAccess(operator, partnerId, "company");
        String status = StringUtils.hasText(req.getStatus()) ? req.getStatus() : "potential";
        validateStatus(status);

        Company company = new Company();
        company.setCompanyName(req.getCompanyName());
        company.setContactName(req.getContactName());
        company.setContactPhone(req.getContactPhone());
        List<String> normalizedIndustries = normalizeIndustryTags(req.getIndustryTags(), req.getIndustry());
        company.setIndustryTags(JSONUtil.toJsonStr(normalizedIndustries));
        company.setIndustry(normalizedIndustries.get(0));
        company.setBusinessDirection(req.getBusinessDirection());
        company.setCompetitors(req.getCompetitors());
        company.setOfficialWebsite(req.getOfficialWebsite());
        company.setOfficialAccount(req.getOfficialAccount());
        company.setVideoAccount(req.getVideoAccount());
        company.setDouyinAccount(req.getDouyinAccount());
        applyRegionFields(company, req.getProvinceCode(), req.getProvinceName(), req.getCityCode(), req.getCityName(), req.getDistrictCode(), req.getDistrictName());
        company.setCity(StringUtils.hasText(req.getCity())
                ? req.getCity()
                : buildRegionDisplay(req.getProvinceName(), req.getCityName(), req.getDistrictName()));
        company.setServiceArea(req.getServiceArea());
        company.setOwnerType(ownerType);
        company.setSourceType(sourceType);
        company.setPartnerId(partnerId);
        company.setPartnerName(resolvePartnerName(partnerId));
        company.setSalesOwnerId(req.getSalesOwnerId());
        company.setReferralSource(req.getReferralSource());
        company.setStatus(status);
        company.setRemark(req.getRemark());
        company.setCreatedBy(operator.getId());
        companyMapper.insert(company);
        ensureAccount(company.getId());
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
        currentUserService.ensurePermission("company.update");
        SysUser operator = currentUserService.requireCurrentUser();
        Company company = requireCompany(id);
        currentUserService.ensurePartnerResourceAccess(operator, company.getPartnerId(), "company");
        String ownerType = resolveUpdateOwnerType(operator, req.getOwnerType(), company.getOwnerType());
        Long partnerId = resolveUpdatePartnerId(operator, req.getPartnerId(), ownerType, company.getPartnerId());
        String sourceType = resolveUpdateSourceType(operator, req.getSourceType(), company.getSourceType());
        validateOwnerBinding(ownerType, partnerId);
        validateSourceBinding(sourceType, partnerId);
        validateStatus(req.getStatus());
        currentUserService.ensurePartnerResourceAccess(operator, partnerId, "company");
        Map<String, Object> before = snapshotCompany(company);
        company.setCompanyName(req.getCompanyName());
        company.setContactName(req.getContactName());
        company.setContactPhone(req.getContactPhone());
        List<String> normalizedIndustries = normalizeIndustryTags(req.getIndustryTags(), req.getIndustry());
        company.setIndustryTags(JSONUtil.toJsonStr(normalizedIndustries));
        company.setIndustry(normalizedIndustries.get(0));
        company.setBusinessDirection(req.getBusinessDirection());
        company.setCompetitors(req.getCompetitors());
        company.setOfficialWebsite(req.getOfficialWebsite());
        company.setOfficialAccount(req.getOfficialAccount());
        company.setVideoAccount(req.getVideoAccount());
        company.setDouyinAccount(req.getDouyinAccount());
        applyRegionFields(company, req.getProvinceCode(), req.getProvinceName(), req.getCityCode(), req.getCityName(), req.getDistrictCode(), req.getDistrictName());
        company.setCity(StringUtils.hasText(req.getCity())
                ? req.getCity()
                : buildRegionDisplay(req.getProvinceName(), req.getCityName(), req.getDistrictName()));
        company.setServiceArea(req.getServiceArea());
        company.setOwnerType(ownerType);
        company.setSourceType(sourceType);
        company.setPartnerId(partnerId);
        company.setPartnerName(resolvePartnerName(partnerId));
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

    public CompanyAccount account(Long companyId) {
        SysUser user = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("company.read");
        Company company = requireCompany(companyId);
        currentUserService.ensurePartnerResourceAccess(user, company.getPartnerId(), "company");
        ensureSalesCompanyAccess(user, company);
        return ensureAccount(companyId);
    }

    public Page<CompanyAccountTxn> accountTxns(Long companyId, long current, long size,
                                               String txnType, String bizType, String dateFrom, String dateTo) {
        SysUser user = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("company.read");
        Company company = requireCompany(companyId);
        currentUserService.ensurePartnerResourceAccess(user, company.getPartnerId(), "company");
        ensureSalesCompanyAccess(user, company);

        LambdaQueryWrapper<CompanyAccountTxn> wrapper = new LambdaQueryWrapper<CompanyAccountTxn>()
                .eq(CompanyAccountTxn::getCompanyId, companyId)
                .orderByDesc(CompanyAccountTxn::getCreatedAt);
        if (StringUtils.hasText(txnType)) {
            wrapper.eq(CompanyAccountTxn::getTxnType, txnType.trim());
        }
        if (StringUtils.hasText(bizType)) {
            wrapper.eq(CompanyAccountTxn::getBizType, bizType.trim());
        }
        LocalDateTime from = parseDateTimeStart(dateFrom);
        LocalDateTime to = parseDateTimeEnd(dateTo);
        if (from != null) {
            wrapper.ge(CompanyAccountTxn::getCreatedAt, from);
        }
        if (to != null) {
            wrapper.le(CompanyAccountTxn::getCreatedAt, to);
        }
        return companyAccountTxnMapper.selectPage(new Page<>(current, size), wrapper);
    }

    @Transactional
    public CompanyAccountTxn recharge(Long companyId, CompanyRechargeRequest req) {
        currentUserService.ensurePermission("company.account.adjust");
        SysUser operator = currentUserService.requireCurrentUser();
        Company company = requireCompany(companyId);
        currentUserService.ensurePartnerResourceAccess(operator, company.getPartnerId(), "company");
        if (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(400, "Recharge amount must be positive");
        }

        CompanyAccount account = lockAccount(companyId);
        BigDecimal before = account.getCurrentBalance();
        BigDecimal after = before.add(req.getAmount());
        account.setCurrentBalance(after);
        account.setTotalRecharge(account.getTotalRecharge().add(req.getAmount()));
        companyAccountMapper.updateById(account);

        CompanyAccountTxn txn = new CompanyAccountTxn();
        txn.setCompanyId(companyId);
        txn.setAccountId(account.getId());
        txn.setTxnNo(buildTxnNo("R"));
        txn.setTxnType("recharge");
        txn.setBizType("company_prepaid");
        txn.setAmount(req.getAmount());
        txn.setBalanceBefore(before);
        txn.setBalanceAfter(after);
        txn.setOperatorUserId(operator.getId());
        txn.setReason(req.getReason().trim());
        txn.setOfflineReference(req.getOfflineReference());
        txn.setRemark(req.getRemark());
        companyAccountTxnMapper.insert(txn);

        activityLogService.logAction(
                operator.getId(),
                "company.account.recharge",
                "company",
                companyId,
                Map.of("balance", before),
                Map.of("balance", after),
                Map.of("amount", req.getAmount(), "txnNo", txn.getTxnNo(), "reason", txn.getReason())
        );
        return txn;
    }

    @Transactional
    public CompanyAccountTxn deduct(Long companyId, CompanyDeductRequest req) {
        currentUserService.ensurePermission("company.account.adjust");
        SysUser operator = currentUserService.requireCurrentUser();
        Company company = requireCompany(companyId);
        currentUserService.ensurePartnerResourceAccess(operator, company.getPartnerId(), "company");
        if (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(400, "Deduct amount must be positive");
        }

        CompanyAccount account = lockAccount(companyId);
        BigDecimal before = account.getCurrentBalance();
        BigDecimal after = before.subtract(req.getAmount());
        if (after.compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException(400, "Insufficient customer balance");
        }
        account.setCurrentBalance(after);
        account.setTotalDeduction(account.getTotalDeduction().add(req.getAmount()));
        companyAccountMapper.updateById(account);

        CompanyAccountTxn txn = new CompanyAccountTxn();
        txn.setCompanyId(companyId);
        txn.setAccountId(account.getId());
        txn.setTxnNo(buildTxnNo("D"));
        txn.setTxnType("deduction");
        txn.setBizType("finance_adjust");
        txn.setAmount(req.getAmount().negate());
        txn.setBalanceBefore(before);
        txn.setBalanceAfter(after);
        txn.setOperatorUserId(operator.getId());
        txn.setReason(req.getReason().trim());
        txn.setRemark(req.getRemark());
        companyAccountTxnMapper.insert(txn);

        activityLogService.logAction(
                operator.getId(),
                "company.account.deduct",
                "company",
                companyId,
                Map.of("balance", before),
                Map.of("balance", after),
                Map.of("amount", req.getAmount(), "txnNo", txn.getTxnNo(), "reason", txn.getReason())
        );
        return txn;
    }

    @Transactional
    public void delete(Long id) {
        currentUserService.ensurePermission("company.delete");
        SysUser operator = currentUserService.requireCurrentUser();
        Company company = requireCompany(id);
        currentUserService.ensurePartnerResourceAccess(operator, company.getPartnerId(), "company");

        Long brandCount = brandMapper.selectCount(
                new LambdaQueryWrapper<Brand>()
                        .isNull(Brand::getDeletedAt)
                        .eq(Brand::getCompanyId, id)
        );
        if (brandCount != null && brandCount > 0) {
            throw new BizException(400, "Company has brands, cannot delete");
        }

        CompanyAccount account = companyAccountMapper.selectOne(
                new LambdaQueryWrapper<CompanyAccount>().eq(CompanyAccount::getCompanyId, id)
        );
        if (account != null) {
            if (account.getCurrentBalance() != null && account.getCurrentBalance().compareTo(BigDecimal.ZERO) > 0) {
                throw new BizException(400, "Company account balance is not zero, cannot delete");
            }
            Long txnCount = companyAccountTxnMapper.selectCount(
                    new LambdaQueryWrapper<CompanyAccountTxn>().eq(CompanyAccountTxn::getCompanyId, id)
            );
            if (txnCount != null && txnCount > 0) {
                throw new BizException(400, "Company has account transactions, cannot delete");
            }
        }

        company.setDeletedAt(LocalDateTime.now());
        company.setDeletedBy(operator.getId());
        companyMapper.updateById(company);
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
        if (company == null || company.getDeletedAt() != null) {
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

    private void validateSourceBinding(String sourceType, Long partnerId) {
        if (!SOURCE_TYPES.contains(sourceType)) {
            throw new BizException(400, "Invalid source_type");
        }
        if ("partner".equals(sourceType) && partnerId == null) {
            throw new BizException(400, "partner source company must bind partner_id");
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
        snapshot.put("contactName", company.getContactName());
        snapshot.put("contactPhone", company.getContactPhone());
        snapshot.put("industry", company.getIndustry());
        snapshot.put("industryTags", company.getIndustryTags());
        snapshot.put("businessDirection", company.getBusinessDirection());
        snapshot.put("competitors", company.getCompetitors());
        snapshot.put("officialWebsite", company.getOfficialWebsite());
        snapshot.put("officialAccount", company.getOfficialAccount());
        snapshot.put("videoAccount", company.getVideoAccount());
        snapshot.put("douyinAccount", company.getDouyinAccount());
        snapshot.put("ownerType", company.getOwnerType());
        snapshot.put("sourceType", company.getSourceType());
        snapshot.put("partnerId", company.getPartnerId());
        snapshot.put("partnerName", company.getPartnerName());
        snapshot.put("serviceArea", company.getServiceArea());
        snapshot.put("provinceCode", company.getProvinceCode());
        snapshot.put("provinceName", company.getProvinceName());
        snapshot.put("cityCode", company.getCityCode());
        snapshot.put("cityName", company.getCityName());
        snapshot.put("districtCode", company.getDistrictCode());
        snapshot.put("districtName", company.getDistrictName());
        snapshot.put("status", company.getStatus());
        snapshot.put("sourceType", company.getSourceType());
        snapshot.put("createdBy", company.getCreatedBy());
        return snapshot;
    }

    private void ensureSalesCompanyAccess(SysUser user, Company company) {
        if (!"sales".equals(user.getRole())) {
            return;
        }
        if (company.getSalesOwnerId() == null || !company.getSalesOwnerId().equals(user.getId())) {
            throw new BizException(403, "No permission to access this company");
        }
        if (!"signed".equals(company.getStatus())) {
            throw new BizException(403, "Sales can only access signed companies");
        }
    }

    private CompanyAccount ensureAccount(Long companyId) {
        CompanyAccount account = companyAccountMapper.selectOne(
                new LambdaQueryWrapper<CompanyAccount>().eq(CompanyAccount::getCompanyId, companyId)
        );
        if (account != null) {
            return account;
        }
        CompanyAccount created = new CompanyAccount();
        created.setCompanyId(companyId);
        created.setCurrentBalance(BigDecimal.ZERO);
        created.setTotalRecharge(BigDecimal.ZERO);
        created.setTotalDeduction(BigDecimal.ZERO);
        created.setCurrency("CNY");
        created.setStatus("active");
        companyAccountMapper.insert(created);
        return created;
    }

    private CompanyAccount lockAccount(Long companyId) {
        CompanyAccount account = companyAccountMapper.selectOne(
                new LambdaQueryWrapper<CompanyAccount>()
                        .eq(CompanyAccount::getCompanyId, companyId)
                        .last("FOR UPDATE")
        );
        if (account == null) {
            account = ensureAccount(companyId);
        }
        if (!"active".equals(account.getStatus())) {
            throw new BizException(400, "Customer account is not active");
        }
        return account;
    }

    private String buildTxnNo(String prefix) {
        return "CT" + prefix + System.currentTimeMillis() + RandomUtil.randomNumbers(6);
    }

    private void applyRegionFields(Company company,
                                   String provinceCode,
                                   String provinceName,
                                   String cityCode,
                                   String cityName,
                                   String districtCode,
                                   String districtName) {
        company.setProvinceCode(trimToNull(provinceCode));
        company.setProvinceName(trimToNull(provinceName));
        company.setCityCode(trimToNull(cityCode));
        company.setCityName(trimToNull(cityName));
        company.setDistrictCode(trimToNull(districtCode));
        company.setDistrictName(trimToNull(districtName));
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

    private LocalDateTime parseDateTimeStart(String input) {
        if (!StringUtils.hasText(input)) {
            return null;
        }
        String value = input.trim();
        try {
            if (value.length() <= 10) {
                return LocalDate.parse(value).atStartOfDay();
            }
            return LocalDateTime.parse(value.replace(" ", "T"));
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private LocalDateTime parseDateTimeEnd(String input) {
        if (!StringUtils.hasText(input)) {
            return null;
        }
        String value = input.trim();
        try {
            if (value.length() <= 10) {
                return LocalDate.parse(value).atTime(LocalTime.MAX);
            }
            return LocalDateTime.parse(value.replace(" ", "T"));
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String resolveCreateOwnerType(SysUser operator) {
        return currentUserService.isPartnerUser(operator) ? "partner" : "direct";
    }

    private Long resolveCreatePartnerId(SysUser operator) {
        if (!currentUserService.isPartnerUser(operator)) {
            return null;
        }
        return currentUserService.requirePartnerScope(operator);
    }

    private String resolveCreateSourceType(SysUser operator) {
        return currentUserService.isPartnerUser(operator) ? "partner" : "internal";
    }

    private String resolveUpdateOwnerType(SysUser operator, String reqOwnerType, String currentOwnerType) {
        if (currentUserService.isPartnerUser(operator)) {
            return "partner";
        }
        return StringUtils.hasText(reqOwnerType) ? reqOwnerType.trim() : currentOwnerType;
    }

    private Long resolveUpdatePartnerId(SysUser operator, Long reqPartnerId, String ownerType, Long currentPartnerId) {
        if (currentUserService.isPartnerUser(operator)) {
            return currentUserService.requirePartnerScope(operator);
        }
        if (!StringUtils.hasText(ownerType)) {
            return currentPartnerId;
        }
        if ("direct".equals(ownerType)) {
            return null;
        }
        if (reqPartnerId != null) {
            return reqPartnerId;
        }
        return currentPartnerId;
    }

    private String resolveUpdateSourceType(SysUser operator, String reqSourceType, String currentSourceType) {
        if (currentUserService.isPartnerUser(operator)) {
            return "partner";
        }
        return StringUtils.hasText(reqSourceType) ? reqSourceType.trim() : currentSourceType;
    }

    private String resolvePartnerName(Long partnerId) {
        if (partnerId == null) {
            return null;
        }
        Partner partner = partnerMapper.selectById(partnerId);
        if (partner == null) {
            throw new BizException(404, "Partner not found");
        }
        return partner.getPartnerName();
    }

    private List<String> normalizeIndustryTags(List<String> industryTags, String legacyIndustry) {
        List<String> source = industryTags == null ? new ArrayList<>() : new ArrayList<>(industryTags);
        if (source.isEmpty() && StringUtils.hasText(legacyIndustry)) {
            source.add(legacyIndustry);
        }
        if (source.isEmpty()) {
            throw new BizException(400, "客户行业至少选择一个");
        }
        Set<String> validKeys = sysDictItemMapper.selectList(
                        new LambdaQueryWrapper<SysDictItem>()
                                .eq(SysDictItem::getDictType, "industry_tag")
                                .eq(SysDictItem::getEnabled, true)
                                .select(SysDictItem::getDictKey)
                ).stream()
                .map(SysDictItem::getDictKey)
                .filter(StringUtils::hasText)
                .map(item -> item.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(HashSet::new));
        if (validKeys.isEmpty()) {
            throw new BizException(500, "行业字典未配置");
        }
        List<String> normalized = new ArrayList<>();
        for (String item : source) {
            if (!StringUtils.hasText(item)) {
                continue;
            }
            String key = item.trim().toLowerCase(Locale.ROOT);
            if (!validKeys.contains(key)) {
                throw new BizException(400, "存在无效行业标签: " + item);
            }
            if (!normalized.contains(key)) {
                normalized.add(key);
            }
        }
        if (normalized.isEmpty()) {
            throw new BizException(400, "客户行业至少选择一个");
        }
        return normalized;
    }
}
