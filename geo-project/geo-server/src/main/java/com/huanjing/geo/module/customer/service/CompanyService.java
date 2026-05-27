package com.huanjing.geo.module.customer.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.util.QuotaPeriodResolver;
import com.huanjing.geo.module.content.dto.ChannelQuotaSnapshotItem;
import com.huanjing.geo.module.content.entity.CompanyChannelQuotaUsage;
import com.huanjing.geo.module.content.mapper.CompanyChannelQuotaUsageMapper;
import com.huanjing.geo.module.customer.access.InternalScopeService;
import com.huanjing.geo.module.customer.dto.CompanyDeductRequest;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.customer.dto.CompanyCreateRequest;
import com.huanjing.geo.module.customer.dto.CompanyDistributionQuotaItemVO;
import com.huanjing.geo.module.customer.dto.CompanyDistributionQuotaVO;
import com.huanjing.geo.module.customer.dto.CompanyKeywordGroupQuotaVO;
import com.huanjing.geo.module.customer.dto.CompanyOwnerTransferRequest;
import com.huanjing.geo.module.customer.dto.CompanyRechargeRequest;
import com.huanjing.geo.module.customer.dto.CompanyUpdateRequest;
import com.huanjing.geo.module.customer.dto.SalesOwnerOptionVO;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.entity.CompanyAccount;
import com.huanjing.geo.module.customer.entity.CompanyAccountTxn;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.entity.CompanyPackageBinding;
import com.huanjing.geo.module.customer.mapper.CompanyAccountMapper;
import com.huanjing.geo.module.customer.mapper.CompanyAccountTxnMapper;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.partner.entity.Partner;
import com.huanjing.geo.module.partner.mapper.PartnerMapper;
import com.huanjing.geo.module.project.service.KeywordGroupService;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.SysDictItem;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.SysDictItemMapper;
import com.huanjing.geo.module.system.mapper.SysUserMapper;
import com.huanjing.geo.module.system.service.ActivityLogService;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.WeekFields;
import java.util.stream.Collectors;
import cn.hutool.core.util.RandomUtil;

@Service
@Slf4j
@RequiredArgsConstructor
public class CompanyService {

    private static final Set<String> OWNER_TYPES = Set.of("direct", "partner", "joint");
    private static final Set<String> SOURCE_TYPES = Set.of("internal", "partner");
    private static final Set<String> STATUSES = Set.of("potential", "signed", "inactive");
    private static final Set<String> DISTRIBUTION_PERIOD_TYPES = Set.of("day", "week", "month", "total");
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final List<ChannelDefinition> DISTRIBUTION_CHANNELS = List.of(
            new ChannelDefinition("official_site", "官网"),
            new ChannelDefinition("industry_site", "行业资讯站"),
            new ChannelDefinition("forum", "平台网站"),
            new ChannelDefinition("self_media", "自媒体平台"),
            new ChannelDefinition("authority_media", "权重媒体平台")
    );

    private final CompanyMapper companyMapper;
    private final CompanyAccountMapper companyAccountMapper;
    private final CompanyAccountTxnMapper companyAccountTxnMapper;
    private final BrandMapper brandMapper;
    private final PartnerMapper partnerMapper;
    private final SysDictItemMapper sysDictItemMapper;
    private final SysUserMapper sysUserMapper;
    private final CurrentUserService currentUserService;
    private final InternalScopeService internalScopeService;
    private final CompanyPackageBindingService companyPackageBindingService;
    private final CompanyChannelQuotaUsageMapper companyChannelQuotaUsageMapper;
    private final KeywordGroupService keywordGroupService;
    private final ProjectMapper projectMapper;
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
        if (internalScopeService.isSalesUser(user)) {
            wrapper.eq(Company::getSalesOwnerId, user.getId())
                    .eq(Company::getStatus, "signed");
        } else {
            internalScopeService.applyCompanyScope(wrapper, user);
        }

        return companyMapper.selectPage(new Page<>(current, size), wrapper);
    }

    public Company detail(Long id) {
        SysUser user = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("company.read");
        Company company = requireCompany(id);
        currentUserService.ensurePartnerResourceAccess(user, company.getPartnerId(), "company");
        internalScopeService.ensureCompanyAccess(user, company, "company");
        ensureSalesCompanyAccess(user, company);
        return company;
    }

    public CompanyKeywordGroupQuotaVO keywordGroupQuota(Long companyId) {
        SysUser user = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("company.read");
        Company company = requireCompany(companyId);
        currentUserService.ensurePartnerResourceAccess(user, company.getPartnerId(), "company");
        internalScopeService.ensureCompanyAccess(user, company, "company");
        ensureSalesCompanyAccess(user, company);

        CompanyPackageBinding binding = companyPackageBindingService.activeBinding(companyId);
        int quotaLimit = binding == null || binding.getKeywordGroupLimit() == null ? 0 : binding.getKeywordGroupLimit();
        List<com.huanjing.geo.module.project.entity.Project> activeProjects = projectMapper.selectList(
                new LambdaQueryWrapper<com.huanjing.geo.module.project.entity.Project>()
                        .eq(com.huanjing.geo.module.project.entity.Project::getCompanyId, companyId)
                        .eq(com.huanjing.geo.module.project.entity.Project::getStatus, "active")
                        .isNull(com.huanjing.geo.module.project.entity.Project::getDeletedAt)
        );
        int usedA = activeProjects.stream().mapToInt(p -> defaultInt(p.getPlanKeywordGroupLimitA(), defaultInt(p.getPlanKeywordGroupLimit(), 0))).sum();
        int usedB = activeProjects.stream().mapToInt(p -> defaultInt(p.getPlanKeywordGroupLimitB(), 0)).sum();
        int usedC = activeProjects.stream().mapToInt(p -> defaultInt(p.getPlanKeywordGroupLimitC(), 0)).sum();
        int usedCount = usedA + usedB + usedC;
        int quotaLimitA = binding == null ? 0 : defaultInt(binding.getKeywordGroupLimitA(), quotaLimit);
        int quotaLimitB = binding == null ? 0 : defaultInt(binding.getKeywordGroupLimitB(), 0);
        int quotaLimitC = binding == null ? 0 : defaultInt(binding.getKeywordGroupLimitC(), 0);

        CompanyKeywordGroupQuotaVO vo = new CompanyKeywordGroupQuotaVO();
        vo.setCompanyId(companyId);
        vo.setPackageBindingId(binding == null ? null : binding.getId());
        vo.setPackageName(binding == null ? null : binding.getPackageName());
        vo.setActiveBinding(binding != null);
        vo.setQuotaLimit(quotaLimit);
        vo.setQuotaLimitA(quotaLimitA);
        vo.setQuotaLimitB(quotaLimitB);
        vo.setQuotaLimitC(quotaLimitC);
        vo.setUsedCount(usedCount);
        vo.setUsedCountA(usedA);
        vo.setUsedCountB(usedB);
        vo.setUsedCountC(usedC);
        vo.setRemainingCount(Math.max(quotaLimit - usedCount, 0));
        vo.setRemainingCountA(Math.max(quotaLimitA - usedA, 0));
        vo.setRemainingCountB(Math.max(quotaLimitB - usedB, 0));
        vo.setRemainingCountC(Math.max(quotaLimitC - usedC, 0));
        vo.setUsageRate(quotaLimit <= 0 ? 0D : Math.min(1D, usedCount * 1D / quotaLimit));
        return vo;
    }

    private int defaultInt(Integer value, Integer fallback) {
        return value == null ? (fallback == null ? 0 : fallback) : value;
    }

    public List<SalesOwnerOptionVO> salesOwnerOptions() {
        SysUser operator = currentUserService.requireCurrentUser();
        if (!currentUserService.hasPermission("company.create") && !currentUserService.hasPermission("company.update")) {
            throw new BizException(403, "No permission: company.create or company.update");
        }
        List<SysUser> users;
        if ("sales".equals(operator.getRole())) {
            users = List.of(operator);
        } else {
            users = sysUserMapper.selectList(
                    new LambdaQueryWrapper<SysUser>()
                            .eq(SysUser::getRole, "sales")
                            .eq(SysUser::getIsActive, true)
                            .orderByAsc(SysUser::getDisplayName)
                            .orderByAsc(SysUser::getId)
            );
        }
        return users.stream().map(this::toSalesOwnerOption).collect(Collectors.toList());
    }

    public CompanyDistributionQuotaVO distributionQuotas(Long companyId) {
        SysUser user = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("company.read");
        Company company = requireCompany(companyId);
        currentUserService.ensurePartnerResourceAccess(user, company.getPartnerId(), "company");
        internalScopeService.ensureCompanyAccess(user, company, "company");
        ensureSalesCompanyAccess(user, company);

        CompanyPackageBinding binding = companyPackageBindingService.activeBinding(companyId);
        Map<String, ChannelQuotaSnapshotItem> snapshot = parseChannelQuotaSnapshot(binding);
        List<CompanyDistributionQuotaItemVO> items = new ArrayList<>();
        boolean hasLimitMismatch = false;
        for (ChannelDefinition channel : DISTRIBUTION_CHANNELS) {
            CompanyDistributionQuotaItemVO item = buildDistributionQuotaItem(companyId, channel, snapshot.get(channel.code()));
            if (Boolean.TRUE.equals(item.getLimitMismatch())) {
                hasLimitMismatch = true;
            }
            items.add(item);
        }

        CompanyDistributionQuotaVO vo = new CompanyDistributionQuotaVO();
        vo.setCompanyId(companyId);
        vo.setHasLimitMismatch(hasLimitMismatch);
        vo.setItems(items);
        return vo;
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
        company.setSalesOwnerId(resolveCreateSalesOwnerId(operator, req.getSalesOwnerId()));
        company.setReferralSource(req.getReferralSource());
        company.setStatus(status);
        company.setRemark(req.getRemark());
        company.setCreatedBy(operator.getId());
        company.setOwnerId(operator.getId());
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
        internalScopeService.ensureCompanyAccess(operator, company, "company");
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
        company.setSalesOwnerId(resolveUpdateSalesOwnerId(operator, req.getSalesOwnerId()));
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

    @Transactional
    public Company transferOwner(Long id, CompanyOwnerTransferRequest req) {
        currentUserService.ensurePermission("delivery.assignment.manage");
        SysUser operator = currentUserService.requireCurrentUser();
        Company company = requireCompany(id);
        Long oldOwnerId = company.getOwnerId();
        SysUser newOwner = requireActiveOperator(req.getNewOwnerId());
        if (newOwner.getId().equals(oldOwnerId)) {
            throw new BizException(400, "New owner is already assigned to this company");
        }

        Map<String, Object> before = snapshotCompany(company);
        company.setOwnerId(newOwner.getId());
        companyMapper.updateById(company);

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("oldOwnerId", oldOwnerId);
        extra.put("newOwnerId", newOwner.getId());
        extra.put("newOwnerName", displayName(newOwner));
        extra.put("reason", trimToNull(req.getReason()));
        activityLogService.logActionRequired(
                operator.getId(),
                "company.owner.transfer",
                "company",
                company.getId(),
                before,
                snapshotCompany(company),
                extra
        );
        return company;
    }

    public CompanyAccount account(Long companyId) {
        SysUser user = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("company.read");
        Company company = requireCompany(companyId);
        currentUserService.ensurePartnerResourceAccess(user, company.getPartnerId(), "company");
        internalScopeService.ensureCompanyAccess(user, company, "company");
        ensureSalesCompanyAccess(user, company);
        return ensureAccount(companyId);
    }

    public Page<CompanyAccountTxn> accountTxns(Long companyId, long current, long size,
                                               String txnType, String bizType, String dateFrom, String dateTo) {
        SysUser user = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("company.read");
        Company company = requireCompany(companyId);
        currentUserService.ensurePartnerResourceAccess(user, company.getPartnerId(), "company");
        internalScopeService.ensureCompanyAccess(user, company, "company");
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
        internalScopeService.ensureCompanyAccess(operator, company, "company");
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
        internalScopeService.ensureCompanyAccess(operator, company, "company");
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
        internalScopeService.ensureCompanyAccess(operator, company, "company");

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

    private Map<String, ChannelQuotaSnapshotItem> parseChannelQuotaSnapshot(CompanyPackageBinding binding) {
        Map<String, ChannelQuotaSnapshotItem> snapshot = new LinkedHashMap<>();
        if (binding == null || !StringUtils.hasText(binding.getChannelQuotaSnapshot())) {
            return snapshot;
        }
        JSONArray arr = JSONUtil.parseArray(binding.getChannelQuotaSnapshot());
        for (Object obj : arr) {
            ChannelQuotaSnapshotItem item = JSONUtil.toBean(JSONUtil.parseObj(obj), ChannelQuotaSnapshotItem.class);
            if (!StringUtils.hasText(item.getChannelCode())) {
                continue;
            }
            snapshot.put(item.getChannelCode().trim(), item);
        }
        return snapshot;
    }

    private CompanyDistributionQuotaItemVO buildDistributionQuotaItem(Long companyId,
                                                                      ChannelDefinition channel,
                                                                      ChannelQuotaSnapshotItem snapshotItem) {
        String periodType = normalizePeriodType(snapshotItem == null ? null : snapshotItem.getPeriodType());
        boolean enabled = snapshotItem != null && snapshotItem.isEnabled() && periodType != null;
        if (snapshotItem != null && snapshotItem.isEnabled() && periodType == null) {
            log.warn("Invalid periodType in binding snapshot, treating as not_configured. companyId={}, channel={}, rawPeriodType={}",
                    companyId, channel.code(), snapshotItem.getPeriodType());
        }
        CompanyDistributionQuotaItemVO vo = new CompanyDistributionQuotaItemVO();
        vo.setChannelCode(channel.code());
        vo.setChannelName(channel.name());
        if (!enabled) {
            vo.setEnabled(false);
            vo.setPeriodType(null);
            vo.setPeriodKey(null);
            vo.setQuotaLimit(0);
            vo.setUsageQuotaLimit(null);
            vo.setLimitMismatch(false);
            vo.setUsedCount(0);
            vo.setRemainingCount(0);
            vo.setUsageRate(0D);
            vo.setNextResetAt(null);
            vo.setStatus("not_configured");
            return vo;
        }

        int quotaLimit = snapshotItem == null ? 0 : snapshotItem.getQuotaLimit();
        String periodKey = distributionPeriodKey(periodType);
        CompanyChannelQuotaUsage usage = selectDistributionUsage(companyId, channel.code(), periodType, periodKey);
        Integer usageQuotaLimit = usage == null ? null : usage.getQuotaLimit();
        int usedCount = usage == null || usage.getUsedCount() == null ? 0 : usage.getUsedCount();
        boolean limitMismatch = usageQuotaLimit != null && usageQuotaLimit != quotaLimit;
        int remainingCount = Math.max(quotaLimit - usedCount, 0);
        double usageRate = quotaLimit <= 0 ? 0D : Math.min(1D, usedCount * 1D / quotaLimit);

        vo.setEnabled(true);
        vo.setPeriodType(periodType);
        vo.setPeriodKey(periodKey);
        vo.setQuotaLimit(quotaLimit);
        vo.setUsageQuotaLimit(usageQuotaLimit);
        vo.setLimitMismatch(limitMismatch);
        vo.setUsedCount(usedCount);
        vo.setRemainingCount(remainingCount);
        vo.setUsageRate(usageRate);
        vo.setNextResetAt(enabled && periodType != null ? nextResetAt(periodType) : null);
        vo.setStatus(distributionQuotaStatus(enabled, quotaLimit, usedCount, usageRate));
        return vo;
    }

    private CompanyChannelQuotaUsage selectDistributionUsage(Long companyId, String channelCode, String periodType, String periodKey) {
        return companyChannelQuotaUsageMapper.selectOne(
                new LambdaQueryWrapper<CompanyChannelQuotaUsage>()
                        .eq(CompanyChannelQuotaUsage::getCompanyId, companyId)
                        .eq(CompanyChannelQuotaUsage::getChannelCode, channelCode)
                        .eq(CompanyChannelQuotaUsage::getPeriodType, periodType)
                        .eq(CompanyChannelQuotaUsage::getPeriodKey, periodKey)
                        .last("LIMIT 1")
        );
    }

    private String distributionQuotaStatus(boolean enabled, int quotaLimit, int usedCount, double usageRate) {
        if (!enabled) {
            return "not_configured";
        }
        if (usedCount > quotaLimit || (quotaLimit <= 0 && usedCount > 0)) {
            return "exceeded";
        }
        if (usageRate >= 0.9D) {
            return "warning";
        }
        return "normal";
    }

    private String normalizePeriodType(String periodType) {
        if (!StringUtils.hasText(periodType)) {
            return null;
        }
        String normalized = periodType.trim().toLowerCase(Locale.ROOT);
        return DISTRIBUTION_PERIOD_TYPES.contains(normalized) ? normalized : null;
    }

    private String distributionPeriodKey(String periodType) {
        return QuotaPeriodResolver.periodKeyOrNull(periodType);
    }

    private String nextResetAt(String periodType) {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        // nextResetAt uses ZonedDateTime + WeekFields.ISO so JDK handles cross-year and zone-boundary rules.
        ZonedDateTime resetAt = switch (periodType) {
            case "day" -> today.plusDays(1).atStartOfDay(BUSINESS_ZONE);
            case "week" -> today.with(WeekFields.ISO.dayOfWeek(), 1).plusWeeks(1).atStartOfDay(BUSINESS_ZONE);
            case "month" -> YearMonth.from(today).plusMonths(1).atDay(1).atStartOfDay(BUSINESS_ZONE);
            default -> null;
        };
        return resetAt == null ? null : resetAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
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
        snapshot.put("provinceCode", company.getProvinceCode());
        snapshot.put("provinceName", company.getProvinceName());
        snapshot.put("cityCode", company.getCityCode());
        snapshot.put("cityName", company.getCityName());
        snapshot.put("districtCode", company.getDistrictCode());
        snapshot.put("districtName", company.getDistrictName());
        snapshot.put("serviceArea", company.getServiceArea());
        snapshot.put("salesOwnerId", company.getSalesOwnerId());
        snapshot.put("referralSource", company.getReferralSource());
        snapshot.put("remark", company.getRemark());
        snapshot.put("status", company.getStatus());
        snapshot.put("sourceType", company.getSourceType());
        snapshot.put("createdBy", company.getCreatedBy());
        snapshot.put("ownerId", company.getOwnerId());
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

    private Long resolveCreateSalesOwnerId(SysUser operator, Long requestedSalesOwnerId) {
        if ("sales".equals(operator.getRole())) {
            return operator.getId();
        }
        validateSalesOwnerId(requestedSalesOwnerId);
        return requestedSalesOwnerId;
    }

    private Long resolveUpdateSalesOwnerId(SysUser operator, Long requestedSalesOwnerId) {
        if ("sales".equals(operator.getRole())) {
            return operator.getId();
        }
        validateSalesOwnerId(requestedSalesOwnerId);
        return requestedSalesOwnerId;
    }

    private void validateSalesOwnerId(Long salesOwnerId) {
        if (salesOwnerId == null) {
            return;
        }
        SysUser sales = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getId, salesOwnerId)
                        .eq(SysUser::getRole, "sales")
                        .eq(SysUser::getIsActive, true)
                        .last("LIMIT 1")
        );
        if (sales == null) {
            throw new BizException(400, "销售人员不存在或不是启用状态");
        }
    }

    private SysUser requireActiveOperator(Long userId) {
        if (userId == null) {
            throw new BizException(400, "newOwnerId is required");
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || !Boolean.TRUE.equals(user.getIsActive()) || !"operator".equals(user.getRole())) {
            throw new BizException(400, "New owner must be an active operator");
        }
        return user;
    }

    private SalesOwnerOptionVO toSalesOwnerOption(SysUser user) {
        SalesOwnerOptionVO vo = new SalesOwnerOptionVO();
        vo.setId(user.getId());
        vo.setDisplayName(displayName(user));
        vo.setUsername(user.getUsername());
        return vo;
    }

    private String displayName(SysUser user) {
        return StringUtils.hasText(user.getDisplayName()) ? user.getDisplayName() : user.getUsername();
    }

    private List<String> normalizeIndustryTags(List<String> industryTags, String legacyIndustry) {
        List<String> source = industryTags == null ? new ArrayList<>() : new ArrayList<>(industryTags);
        if (source.isEmpty() && StringUtils.hasText(legacyIndustry)) {
            source.add(legacyIndustry);
        }
        if (source.isEmpty()) {
            throw new BizException(400, "客户行业至少选择一个");
        }
        Map<String, String> dictKeyLookup = sysDictItemMapper.selectList(
                        new LambdaQueryWrapper<SysDictItem>()
                                .eq(SysDictItem::getDictType, "industry_tag")
                                .eq(SysDictItem::getEnabled, true)
                                .select(SysDictItem::getDictKey)
                ).stream()
                .map(SysDictItem::getDictKey)
                .filter(StringUtils::hasText)
                .collect(Collectors.toMap(
                        item -> item.trim().toLowerCase(Locale.ROOT),
                        item -> item.trim(),
                        (left, right) -> left
                ));
        List<String> normalized = new ArrayList<>();
        for (String item : source) {
            if (!StringUtils.hasText(item)) {
                continue;
            }
            String value = item.trim();
            String normalizedKey = value.toLowerCase(Locale.ROOT);
            String storedValue = dictKeyLookup.getOrDefault(normalizedKey, value);
            boolean exists = normalized.stream()
                    .anyMatch(existing -> existing.equalsIgnoreCase(storedValue));
            if (!exists) {
                normalized.add(storedValue);
            }
        }
        if (normalized.isEmpty()) {
            throw new BizException(400, "客户行业至少选择一个");
        }
        return normalized;
    }

    private record ChannelDefinition(String code, String name) {
    }
}
