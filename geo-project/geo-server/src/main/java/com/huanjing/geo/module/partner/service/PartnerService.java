package com.huanjing.geo.module.partner.service;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.storage.MinioStorageService;
import com.huanjing.geo.module.customer.access.InternalScopeService;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.customer.service.CompanyService;
import com.huanjing.geo.module.partner.dto.PartnerAdjustRequest;
import com.huanjing.geo.module.partner.dto.PartnerCreateRequest;
import com.huanjing.geo.module.partner.dto.PartnerCreateResult;
import com.huanjing.geo.module.partner.dto.PartnerRechargeApplyRequest;
import com.huanjing.geo.module.partner.dto.PartnerRechargeAuditRequest;
import com.huanjing.geo.module.partner.dto.PartnerRechargeRequest;
import com.huanjing.geo.module.partner.dto.PartnerStaffCreateRequest;
import com.huanjing.geo.module.partner.dto.PartnerStaffCreateResult;
import com.huanjing.geo.module.partner.dto.PartnerStaffResetPasswordResult;
import com.huanjing.geo.module.partner.dto.PartnerStaffUpdateRequest;
import com.huanjing.geo.module.partner.dto.PartnerStaffVO;
import com.huanjing.geo.module.partner.dto.PartnerUpdateRequest;
import com.huanjing.geo.module.partner.dto.PartnerVoucherFile;
import com.huanjing.geo.module.partner.entity.Partner;
import com.huanjing.geo.module.partner.entity.PartnerAccount;
import com.huanjing.geo.module.partner.entity.PartnerAccountTxn;
import com.huanjing.geo.module.partner.entity.PartnerDiscountHistory;
import com.huanjing.geo.module.partner.entity.PartnerRechargeOrder;
import com.huanjing.geo.module.partner.mapper.PartnerAccountMapper;
import com.huanjing.geo.module.partner.mapper.PartnerAccountTxnMapper;
import com.huanjing.geo.module.partner.mapper.PartnerDiscountHistoryMapper;
import com.huanjing.geo.module.partner.mapper.PartnerMapper;
import com.huanjing.geo.module.partner.mapper.PartnerRechargeOrderMapper;
import com.huanjing.geo.module.project.service.ProjectFlowPolicy;
import com.huanjing.geo.module.system.entity.SysRole;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.entity.SysUserRole;
import com.huanjing.geo.module.system.mapper.SysRoleMapper;
import com.huanjing.geo.module.system.mapper.SysUserMapper;
import com.huanjing.geo.module.system.mapper.SysUserRoleMapper;
import com.huanjing.geo.module.system.service.ActivityLogService;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PartnerService {

    private static final String ROLE_PARTNER = "partner";
    private static final String ROLE_PARTNER_STAFF = "partner_staff";
    private static final String DEFAULT_PARTNER_LEVEL = "custom";
    private static final long MAX_VOUCHER_FILE_SIZE = 10L * 1024 * 1024;
    private static final int MAX_OFFLINE_REFERENCE_LENGTH = 12000;
    private static final Set<String> HQ_VISIBLE_PARTNER_PROJECT_STATUSES = Set.of(
            ProjectFlowPolicy.SUBMITTED,
            ProjectFlowPolicy.REJECTED,
            ProjectFlowPolicy.APPROVED_PENDING_SETUP,
            ProjectFlowPolicy.SETUP_READY,
            ProjectFlowPolicy.ACTIVE,
            ProjectFlowPolicy.PAUSED,
            ProjectFlowPolicy.COMPLETED,
            ProjectFlowPolicy.ARCHIVED,
            ProjectFlowPolicy.CANCELLED,
            ProjectFlowPolicy.EXPIRED
    );

    private final PartnerMapper partnerMapper;
    private final PartnerAccountMapper partnerAccountMapper;
    private final PartnerAccountTxnMapper partnerAccountTxnMapper;
    private final PartnerDiscountHistoryMapper partnerDiscountHistoryMapper;
    private final PartnerRechargeOrderMapper partnerRechargeOrderMapper;
    private final CompanyMapper companyMapper;
    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUserService;
    private final InternalScopeService internalScopeService;
    private final ActivityLogService activityLogService;
    private final MinioStorageService minioStorageService;
    private final ObjectMapper objectMapper;

    public Page<Partner> page(long current, long size, String keyword, String status) {
        currentUserService.ensurePermission("partner.read");

        LambdaQueryWrapper<Partner> wrapper = new LambdaQueryWrapper<Partner>()
                .orderByDesc(Partner::getCreatedAt);

        SysUser user = currentUserService.requireCurrentUser();
        Long scopePartnerId = currentUserService.requirePartnerScope(user);
        if (scopePartnerId != null) {
            wrapper.eq(Partner::getId, scopePartnerId);
        }

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Partner::getPartnerName, keyword)
                    .or().like(Partner::getPartnerCode, keyword)
                    .or().like(Partner::getCity, keyword));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(Partner::getStatus, status);
        }

        Page<Partner> page = partnerMapper.selectPage(new Page<>(current, size), wrapper);
        attachCustomerCounts(page.getRecords(), user);
        return page;
    }

    private void attachCustomerCounts(List<Partner> partners, SysUser user) {
        if (partners == null || partners.isEmpty()) {
            return;
        }
        boolean canSeeAllPartnerCustomers = internalScopeService.isSuperAdmin(user);
        for (Partner partner : partners) {
            LambdaQueryWrapper<Company> wrapper = new LambdaQueryWrapper<Company>()
                    .eq(Company::getPartnerId, partner.getId())
                    .eq(Company::getOwnerType, "partner")
                    .isNull(Company::getDeletedAt);
            if (!canSeeAllPartnerCustomers) {
                wrapper.inSql(Company::getId, visiblePartnerCompanySql());
            }
            Long count = companyMapper.selectCount(wrapper);
            partner.setCustomerCount(count == null ? 0L : count);
        }
    }

    private String visiblePartnerCompanySql() {
        String statuses = HQ_VISIBLE_PARTNER_PROJECT_STATUSES.stream()
                .map(status -> "'" + status + "'")
                .collect(Collectors.joining(","));
        return "SELECT DISTINCT p.company_id FROM project p WHERE p.deleted_at IS NULL AND p.owner_type = 'partner' AND p.status IN (" + statuses + ")";
    }

    public Partner detail(Long id) {
        SysUser user = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("partner.read");
        Partner partner = requirePartner(id);
        currentUserService.ensurePartnerResourceAccess(user, id, "partner");
        return partner;
    }

    @Transactional
    public PartnerCreateResult create(PartnerCreateRequest req) {
        currentUserService.ensurePermission("partner.create");

        String partnerCode = StringUtils.hasText(req.getPartnerCode())
                ? req.getPartnerCode().trim()
                : generatePartnerCode();
        Partner existed = partnerMapper.selectOne(
                new LambdaQueryWrapper<Partner>().eq(Partner::getPartnerCode, partnerCode)
        );
        if (existed != null) {
            throw new BizException(400, "partner_code already exists");
        }

        Partner partner = new Partner();
        partner.setPartnerCode(partnerCode);
        partner.setPartnerName(req.getPartnerName());
        partner.setPartnerLevel(normalizePartnerLevel(req.getPartnerLevel(), DEFAULT_PARTNER_LEVEL));
        partner.setDiscountRate(req.getDiscountRate());
        partner.setPresaleReportFreeQuotaLimit(normalizePresaleReportFreeQuotaLimit(req.getPresaleReportFreeQuotaLimit()));
        partner.setPresaleReportExtraPoints(normalizePresaleReportExtraPoints(req.getPresaleReportExtraPoints()));
        partner.setStatus("active");
        partner.setContactName(req.getContactName());
        partner.setContactPhone(req.getContactPhone());
        partner.setCity(req.getCity());
        partner.setRemark(req.getRemark());
        partnerMapper.insert(partner);
        logDiscountHistory(partner.getId(), null, partner.getDiscountRate(), "partner.create");

        PartnerAccount account = new PartnerAccount();
        account.setPartnerId(partner.getId());
        account.setCurrentBalance(BigDecimal.ZERO);
        account.setTotalRecharge(BigDecimal.ZERO);
        account.setTotalDeduction(BigDecimal.ZERO);
        account.setCurrency("CNY");
        account.setStatus("active");
        partnerAccountMapper.insert(account);

        BigDecimal initialAmount = req.getInitialAmount() == null ? BigDecimal.ZERO : req.getInitialAmount();
        if (initialAmount.compareTo(BigDecimal.ZERO) > 0) {
            String initialOfflineReference = migrateInitialVoucherReference(
                    partner.getId(),
                    currentUserService.requireCurrentUser().getId(),
                    req.getInitialOfflineReference()
            );
            account.setCurrentBalance(initialAmount);
            account.setTotalRecharge(initialAmount);
            partnerAccountMapper.updateById(account);

            PartnerAccountTxn txn = new PartnerAccountTxn();
            txn.setPartnerId(partner.getId());
            txn.setAccountId(account.getId());
            txn.setTxnNo(buildTxnNo("R"));
            txn.setTxnType("recharge");
            txn.setBizType("partner_prepaid");
            txn.setAmount(initialAmount);
            txn.setBalanceBefore(BigDecimal.ZERO);
            txn.setBalanceAfter(initialAmount);
            txn.setOperatorUserId(currentUserService.requireCurrentUser().getId());
            txn.setOfflineReference(initialOfflineReference);
            txn.setRemark("create partner initial prepaid");
            partnerAccountTxnMapper.insert(txn);
        }

        String username = buildPartnerUsername(partnerCode);
        String initialPassword = RandomUtil.randomString(10);
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(initialPassword));
        user.setDisplayName(partner.getPartnerName());
        user.setRole(ROLE_PARTNER);
        user.setPartnerId(partner.getId());
        user.setPhone(partner.getContactPhone());
        user.setIsActive(true);
        user.setTokenVersion(0);
        sysUserMapper.insert(user);
        bindPartnerOwnerRole(user.getId());

        return new PartnerCreateResult(partner, username, initialPassword);
    }

    private void bindPartnerOwnerRole(Long userId) {
        bindRole(userId, ROLE_PARTNER);
    }

    private void bindPartnerStaffRole(Long userId) {
        bindRole(userId, ROLE_PARTNER_STAFF);
    }

    private void bindRole(Long userId, String roleKey) {
        SysRole role = sysRoleMapper.selectOne(
                new LambdaQueryWrapper<SysRole>()
                        .eq(SysRole::getRoleKey, roleKey)
                        .eq(SysRole::getStatus, "active")
        );
        if (role == null) {
            throw new BizException(500, roleKey + " role is not configured");
        }
        SysUserRole relation = new SysUserRole();
        relation.setUserId(userId);
        relation.setRoleId(role.getId());
        sysUserRoleMapper.insert(relation);
    }

    public Partner update(Long id, PartnerUpdateRequest req) {
        currentUserService.ensurePermission("partner.update");

        Partner partner = requirePartner(id);
        BigDecimal oldDiscountRate = partner.getDiscountRate();
        if (isDiscountChanged(oldDiscountRate, req.getDiscountRate())) {
            currentUserService.ensurePermission("partner.discount.update");
        }
        partner.setPartnerName(req.getPartnerName());
        partner.setPartnerLevel(normalizePartnerLevel(req.getPartnerLevel(), partner.getPartnerLevel()));
        partner.setDiscountRate(req.getDiscountRate());
        partner.setPresaleReportFreeQuotaLimit(normalizePresaleReportFreeQuotaLimit(req.getPresaleReportFreeQuotaLimit()));
        partner.setPresaleReportExtraPoints(normalizePresaleReportExtraPoints(req.getPresaleReportExtraPoints()));
        partner.setStatus(req.getStatus());
        partner.setContactName(req.getContactName());
        partner.setContactPhone(req.getContactPhone());
        partner.setCity(req.getCity());
        partner.setRemark(req.getRemark());
        partnerMapper.updateById(partner);
        if (!"active".equals(req.getStatus())) {
            deactivatePartnerStaffAccounts(partner.getId());
        }
        if (isDiscountChanged(oldDiscountRate, req.getDiscountRate())) {
            logDiscountHistory(partner.getId(), oldDiscountRate, req.getDiscountRate(), "partner.update");
        }
        return partner;
    }

    public void updateStatus(Long id, String status) {
        currentUserService.ensurePermission("partner.status.update");
        Partner partner = requirePartner(id);
        partner.setStatus(status);
        partnerMapper.updateById(partner);
        if (!"active".equals(status)) {
            deactivatePartnerStaffAccounts(id);
        }
    }

    public List<PartnerStaffVO> myStaff() {
        SysUser partnerOwner = requireCurrentPartnerOwner();
        currentUserService.ensurePermission("partner.staff.manage");
        return sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getPartnerId, partnerOwner.getPartnerId())
                        .eq(SysUser::getRole, ROLE_PARTNER_STAFF)
                        .orderByDesc(SysUser::getCreatedAt))
                .stream()
                .map(this::toPartnerStaffVO)
                .collect(Collectors.toList());
    }

    @Transactional
    public PartnerStaffCreateResult createMyStaff(PartnerStaffCreateRequest req) {
        SysUser partnerOwner = requireCurrentPartnerOwner();
        currentUserService.ensurePermission("partner.staff.manage");
        lockActivePartner(partnerOwner.getPartnerId());

        Long existingStaffCount = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPartnerId, partnerOwner.getPartnerId())
                .eq(SysUser::getRole, ROLE_PARTNER_STAFF));
        if (existingStaffCount != null && existingStaffCount > 0) {
            throw new BizException(400, "Only one partner staff account is allowed");
        }
        SysUser existed = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, req.getUsername())
        );
        if (existed != null) {
            throw new BizException(400, "username already exists");
        }

        String initialPassword = RandomUtil.randomString(10);
        SysUser staff = new SysUser();
        staff.setUsername(req.getUsername());
        staff.setPasswordHash(passwordEncoder.encode(initialPassword));
        staff.setDisplayName(req.getDisplayName());
        staff.setRole(ROLE_PARTNER_STAFF);
        staff.setPartnerId(partnerOwner.getPartnerId());
        staff.setPhone(req.getPhone());
        staff.setEmail(req.getEmail());
        staff.setIsActive(true);
        staff.setTokenVersion(0);
        try {
            sysUserMapper.insert(staff);
        } catch (DuplicateKeyException ex) {
            throw new BizException(409, "Only one partner staff account is allowed");
        }
        bindPartnerStaffRole(staff.getId());
        activityLogService.logAction(
                partnerOwner.getId(),
                "partner.staff.create",
                "sys_user",
                staff.getId(),
                null,
                partnerStaffSnapshot(staff),
                java.util.Map.of("partnerId", partnerOwner.getPartnerId())
        );
        return new PartnerStaffCreateResult(toPartnerStaffVO(staff), initialPassword);
    }

    @Transactional
    public PartnerStaffVO updateMyStaff(Long staffUserId, PartnerStaffUpdateRequest req) {
        SysUser partnerOwner = requireCurrentPartnerOwner();
        currentUserService.ensurePermission("partner.staff.manage");
        SysUser staff = requireOwnedPartnerStaff(partnerOwner.getPartnerId(), staffUserId);
        Map<String, Object> before = partnerStaffSnapshot(staff);
        staff.setDisplayName(req.getDisplayName().trim());
        staff.setPhone(normalizeNullableText(req.getPhone()));
        staff.setEmail(normalizeNullableText(req.getEmail()));
        sysUserMapper.updateById(staff);
        activityLogService.logAction(
                partnerOwner.getId(),
                "partner.staff.update",
                "sys_user",
                staff.getId(),
                before,
                partnerStaffSnapshot(staff),
                java.util.Map.of("partnerId", partnerOwner.getPartnerId())
        );
        return toPartnerStaffVO(staff);
    }

    @Transactional
    public PartnerStaffVO updateMyStaffStatus(Long staffUserId, Boolean isActive) {
        SysUser partnerOwner = requireCurrentPartnerOwner();
        currentUserService.ensurePermission("partner.staff.manage");
        if (Boolean.TRUE.equals(isActive)) {
            ensurePartnerActive(partnerOwner.getPartnerId());
        }
        SysUser staff = requireOwnedPartnerStaff(partnerOwner.getPartnerId(), staffUserId);
        Map<String, Object> before = partnerStaffSnapshot(staff);
        staff.setIsActive(isActive);
        staff.setTokenVersion(nextTokenVersion(staff));
        sysUserMapper.updateById(staff);
        activityLogService.logAction(
                partnerOwner.getId(),
                "partner.staff.status.update",
                "sys_user",
                staff.getId(),
                before,
                partnerStaffSnapshot(staff),
                java.util.Map.of("partnerId", partnerOwner.getPartnerId())
        );
        return toPartnerStaffVO(staff);
    }

    @Transactional
    public PartnerStaffResetPasswordResult resetMyStaffPassword(Long staffUserId) {
        SysUser partnerOwner = requireCurrentPartnerOwner();
        currentUserService.ensurePermission("partner.staff.manage");
        SysUser staff = requireOwnedPartnerStaff(partnerOwner.getPartnerId(), staffUserId);
        String newPassword = RandomUtil.randomString(10);
        Map<String, Object> before = partnerStaffSnapshot(staff);
        staff.setPasswordHash(passwordEncoder.encode(newPassword));
        staff.setTokenVersion(nextTokenVersion(staff));
        sysUserMapper.updateById(staff);
        activityLogService.logAction(
                partnerOwner.getId(),
                "partner.staff.password.reset",
                "sys_user",
                staff.getId(),
                before,
                partnerStaffSnapshot(staff),
                java.util.Map.of("partnerId", partnerOwner.getPartnerId())
        );
        return new PartnerStaffResetPasswordResult(toPartnerStaffVO(staff), newPassword);
    }

    @Transactional
    public void deleteMyStaff(Long staffUserId) {
        SysUser partnerOwner = requireCurrentPartnerOwner();
        currentUserService.ensurePermission("partner.staff.manage");
        SysUser staff = requireOwnedPartnerStaff(partnerOwner.getPartnerId(), staffUserId);
        Map<String, Object> before = partnerStaffSnapshot(staff);
        companyMapper.update(null, new UpdateWrapper<Company>()
                .eq("partner_id", partnerOwner.getPartnerId())
                .eq("partner_staff_owner_id", staff.getId())
                .set("partner_staff_owner_id", null)
                .set("partner_workflow_status", CompanyService.PARTNER_WORKFLOW_DRAFT)
                .set("partner_workflow_updated_at", LocalDateTime.now()));
        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, staff.getId()));
        sysUserMapper.deleteById(staff.getId());
        activityLogService.logAction(
                partnerOwner.getId(),
                "partner.staff.delete",
                "sys_user",
                staff.getId(),
                before,
                null,
                java.util.Map.of("partnerId", partnerOwner.getPartnerId())
        );
    }

    private Integer normalizePresaleReportFreeQuotaLimit(Integer value) {
        return value == null ? 0 : value;
    }

    private BigDecimal normalizePresaleReportExtraPoints(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public PartnerAccount account(Long partnerId) {
        currentUserService.ensurePermission("partner.account.read");
        Partner partner = requireAccessiblePartnerAccount(partnerId);
        PartnerAccount account = partnerAccountMapper.selectOne(
                new LambdaQueryWrapper<PartnerAccount>().eq(PartnerAccount::getPartnerId, partner.getId())
        );
        if (account == null) {
            throw new BizException(404, "Partner account not found");
        }
        return account;
    }

    public Page<PartnerAccountTxn> accountTxns(Long partnerId, long current, long size,
                                               String txnType, String bizType, String dateFrom, String dateTo) {
        currentUserService.ensurePermission("partner.account.txn.read");
        requireAccessiblePartnerAccount(partnerId);
        LambdaQueryWrapper<PartnerAccountTxn> wrapper = new LambdaQueryWrapper<PartnerAccountTxn>()
                .eq(PartnerAccountTxn::getPartnerId, partnerId)
                .orderByDesc(PartnerAccountTxn::getCreatedAt);
        if (StringUtils.hasText(txnType)) {
            wrapper.eq(PartnerAccountTxn::getTxnType, txnType.trim());
        }
        if (StringUtils.hasText(bizType)) {
            wrapper.eq(PartnerAccountTxn::getBizType, bizType.trim());
        }
        LocalDateTime from = parseDateTimeStart(dateFrom);
        LocalDateTime to = parseDateTimeEnd(dateTo);
        if (from != null) {
            wrapper.ge(PartnerAccountTxn::getCreatedAt, from);
        }
        if (to != null) {
            wrapper.le(PartnerAccountTxn::getCreatedAt, to);
        }
        return partnerAccountTxnMapper.selectPage(new Page<>(current, size), wrapper);
    }

    public Page<PartnerRechargeOrder> rechargeOrders(Long partnerId, long current, long size, String status) {
        currentUserService.ensurePermission("partner.account.txn.read");
        requireAccessiblePartnerAccount(partnerId);
        LambdaQueryWrapper<PartnerRechargeOrder> wrapper = new LambdaQueryWrapper<PartnerRechargeOrder>()
                .eq(PartnerRechargeOrder::getPartnerId, partnerId)
                .orderByDesc(PartnerRechargeOrder::getCreatedAt);
        if (StringUtils.hasText(status)) {
            wrapper.eq(PartnerRechargeOrder::getStatus, status.trim());
        }
        return partnerRechargeOrderMapper.selectPage(new Page<>(current, size), wrapper);
    }

    @Transactional
    public PartnerRechargeOrder applyRecharge(Long partnerId, PartnerRechargeApplyRequest req) {
        currentUserService.ensurePermission("partner.account.recharge.apply");
        SysUser applicant = currentUserService.requireCurrentUser();
        requireAccessiblePartnerAccount(partnerId);
        if (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(400, "Recharge amount must be positive");
        }

        PartnerRechargeOrder order = new PartnerRechargeOrder();
        order.setOrderNo(buildRechargeOrderNo());
        order.setPartnerId(partnerId);
        order.setAmount(req.getAmount());
        order.setStatus("pending");
        order.setOfflineReference(normalizeOfflineReference(req.getOfflineReference()));
        order.setApplyRemark(trimToLength(req.getRemark(), 500));
        order.setApplicantUserId(applicant.getId());
        order.setExpiresAt(LocalDateTime.now().plusDays(7));
        partnerRechargeOrderMapper.insert(order);
        activityLogService.logAction(
                applicant.getId(),
                "partner.account.recharge.apply",
                "partner_recharge_order",
                order.getId(),
                null,
                null,
                java.util.Map.of("partnerId", partnerId, "amount", order.getAmount(), "orderNo", order.getOrderNo())
        );
        return order;
    }

    @Transactional
    public void cancelRechargeOrder(Long partnerId, Long orderId) {
        currentUserService.ensurePermission("partner.account.recharge.apply");
        requireAccessiblePartnerAccount(partnerId);
        PartnerRechargeOrder order = lockRechargeOrder(partnerId, orderId);
        if (!"pending".equals(order.getStatus())) {
            throw new BizException(400, "Only pending recharge order can be cancelled");
        }
        order.setStatus("cancelled");
        partnerRechargeOrderMapper.updateById(order);
        activityLogService.logAction(
                currentUserService.requireCurrentUser().getId(),
                "partner.account.recharge.cancel",
                "partner_recharge_order",
                order.getId(),
                java.util.Map.of("status", "pending"),
                java.util.Map.of("status", "cancelled"),
                java.util.Map.of("partnerId", partnerId, "amount", order.getAmount(), "orderNo", order.getOrderNo())
        );
    }

    @Transactional
    public PartnerRechargeOrder auditRechargeOrder(Long partnerId, Long orderId, PartnerRechargeAuditRequest req) {
        currentUserService.ensurePermission("partner.account.recharge.audit");
        SysUser auditor = currentUserService.requireCurrentUser();
        requireAccessiblePartnerAccount(partnerId);
        PartnerRechargeOrder order = lockRechargeOrder(partnerId, orderId);
        if (!"pending".equals(order.getStatus())) {
            throw new BizException(400, "Only pending recharge order can be audited");
        }
        String action = req.getAction().trim().toLowerCase();
        if ("approve".equals(action)) {
            if (order.getExpiresAt() != null && LocalDateTime.now().isAfter(order.getExpiresAt())) {
                throw new BizException(400, "Recharge order has expired");
            }
            PartnerAccountTxn txn = rechargeAccount(partnerId, order.getAmount(), order.getOfflineReference(),
                    StringUtils.hasText(req.getRemark()) ? req.getRemark() : order.getApplyRemark(), "partner_prepaid", order.getId());
            order.setStatus("approved");
            order.setAccountTxnId(txn.getId());
        } else if ("reject".equals(action)) {
            if (!StringUtils.hasText(req.getRejectReason())) {
                throw new BizException(400, "Reject reason is required");
            }
            order.setStatus("rejected");
            order.setRejectReason(trimToLength(req.getRejectReason(), 500));
        } else {
            throw new BizException(400, "Invalid audit action");
        }
        order.setAuditedBy(auditor.getId());
        order.setAuditedAt(LocalDateTime.now());
        partnerRechargeOrderMapper.updateById(order);
        activityLogService.logAction(
                auditor.getId(),
                "partner.account.recharge." + action,
                "partner_recharge_order",
                order.getId(),
                java.util.Map.of("status", "pending"),
                detailMap("status", order.getStatus(), "accountTxnId", order.getAccountTxnId()),
                java.util.Map.of("partnerId", partnerId, "amount", order.getAmount(), "orderNo", order.getOrderNo())
        );
        return order;
    }

    @Transactional
    public PartnerAccountTxn recharge(Long partnerId, PartnerRechargeRequest req) {
        currentUserService.ensurePermission("partner.account.recharge.audit");
        requireAccessiblePartnerAccount(partnerId);
        if (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(400, "Recharge amount must be positive");
        }
        return rechargeAccount(partnerId, req.getAmount(), req.getOfflineReference(), req.getRemark(), "partner_prepaid", null);
    }

    public PartnerVoucherFile uploadAccountVoucher(Long partnerId, MultipartFile file) {
        SysUser operator = currentUserService.requireCurrentUser();
        if (!currentUserService.hasPermission("partner.account.recharge.audit")
                && !currentUserService.hasPermission("partner.account.recharge.apply")) {
            throw new BizException(403, "No permission: partner.account.voucher.upload");
        }
        requireAccessiblePartnerAccount(partnerId);
        if (file == null || file.isEmpty()) {
            throw new BizException(400, "凭证文件不能为空");
        }
        if (file.getSize() > MAX_VOUCHER_FILE_SIZE) {
            throw new BizException(400, "凭证文件不能超过10MB");
        }

        String originalName = originalName(file);
        String objectKey = "partner-vouchers/" + partnerId + "/" + LocalDate.now() + "/"
                + System.currentTimeMillis() + "-" + RandomUtil.randomString(8) + "-" + sanitizeFileName(originalName);
        minioStorageService.upload(file, objectKey, file.getContentType());
        activityLogService.logAction(
                operator.getId(),
                "partner.account.voucher.upload",
                "partner",
                partnerId,
                null,
                null,
                detailMap("fileName", originalName, "fileSize", file.getSize(), "objectKey", objectKey)
        );
        return new PartnerVoucherFile(
                trimToLength(originalName, 255),
                file.getSize(),
                trimToLength(file.getContentType(), 128),
                objectKey,
                buildVoucherDownloadUrl(partnerId, objectKey)
        );
    }

    public PartnerVoucherFile uploadInitialAccountVoucher(MultipartFile file) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("partner.create");
        if (file == null || file.isEmpty()) {
            throw new BizException(400, "凭证文件不能为空");
        }
        if (file.getSize() > MAX_VOUCHER_FILE_SIZE) {
            throw new BizException(400, "凭证文件不能超过10MB");
        }

        String originalName = originalName(file);
        String objectKey = "partner-vouchers/initial/" + operator.getId() + "/" + LocalDate.now() + "/"
                + System.currentTimeMillis() + "-" + RandomUtil.randomString(8) + "-" + sanitizeFileName(originalName);
        minioStorageService.upload(file, objectKey, file.getContentType());
        return new PartnerVoucherFile(
                trimToLength(originalName, 255),
                file.getSize(),
                trimToLength(file.getContentType(), 128),
                objectKey,
                null
        );
    }

    public PartnerVoucherFile voucherDetail(Long partnerId, String objectKey) {
        currentUserService.ensurePermission("partner.account.txn.read");
        requireAccessiblePartnerAccount(partnerId);
        String normalizedKey = requireVoucherObjectKey(partnerId, objectKey);
        return new PartnerVoucherFile(
                normalizedKey.substring(normalizedKey.lastIndexOf('/') + 1),
                null,
                null,
                normalizedKey,
                buildVoucherDownloadUrl(partnerId, normalizedKey)
        );
    }

    public byte[] readVoucherBytes(Long partnerId, String objectKey) {
        currentUserService.ensurePermission("partner.account.txn.read");
        requireAccessiblePartnerAccount(partnerId);
        return minioStorageService.getObjectBytes(requireVoucherObjectKey(partnerId, objectKey));
    }

    @Transactional
    public PartnerAccountTxn adjust(Long partnerId, PartnerAdjustRequest req) {
        currentUserService.ensurePermission("partner.account.adjust");
        requireAccessiblePartnerAccount(partnerId);
        if (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) == 0) {
            throw new BizException(400, "Adjust amount cannot be zero");
        }

        PartnerAccount account = lockAccount(partnerId);
        BigDecimal before = account.getCurrentBalance();
        BigDecimal after = before.add(req.getAmount());
        if (after.compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException(400, "Balance cannot be negative");
        }

        account.setCurrentBalance(after);
        if (req.getAmount().compareTo(BigDecimal.ZERO) > 0) {
            account.setTotalRecharge(account.getTotalRecharge().add(req.getAmount()));
        } else {
            account.setTotalDeduction(account.getTotalDeduction().add(req.getAmount().abs()));
        }
        partnerAccountMapper.updateById(account);

        PartnerAccountTxn txn = new PartnerAccountTxn();
        txn.setPartnerId(partnerId);
        txn.setAccountId(account.getId());
        txn.setTxnNo(buildTxnNo("A"));
        txn.setTxnType("manual_adjust");
        txn.setBizType("finance_adjust");
        txn.setAmount(req.getAmount());
        txn.setBalanceBefore(before);
        txn.setBalanceAfter(after);
        txn.setOperatorUserId(currentUserService.requireCurrentUser().getId());
        txn.setOfflineReference(normalizeOfflineReference(req.getOfflineReference()));
        txn.setRemark(req.getRemark());
        partnerAccountTxnMapper.insert(txn);
        activityLogService.logAction(
                txn.getOperatorUserId(),
                "partner.account.adjust",
                "partner_account_txn",
                txn.getId(),
                java.util.Map.of("balance", before),
                java.util.Map.of("balance", after),
                java.util.Map.of("partnerId", partnerId, "amount", req.getAmount(), "txnNo", txn.getTxnNo())
        );
        return txn;
    }

    private Partner requirePartner(Long id) {
        Partner partner = partnerMapper.selectById(id);
        if (partner == null) {
            throw new BizException(404, "Partner not found");
        }
        return partner;
    }

    private SysUser requireCurrentPartnerOwner() {
        SysUser user = currentUserService.requireCurrentUser();
        if (!ROLE_PARTNER.equals(user.getRole()) || user.getPartnerId() == null) {
            throw new BizException(403, "Only partner owner can manage partner staff");
        }
        return user;
    }

    private SysUser requireOwnedPartnerStaff(Long partnerId, Long staffUserId) {
        SysUser staff = sysUserMapper.selectById(staffUserId);
        if (staff == null
                || !ROLE_PARTNER_STAFF.equals(staff.getRole())
                || staff.getPartnerId() == null
                || !staff.getPartnerId().equals(partnerId)) {
            throw new BizException(404, "Partner staff not found");
        }
        return staff;
    }

    private void ensurePartnerActive(Long partnerId) {
        Partner partner = requirePartner(partnerId);
        if (!"active".equals(partner.getStatus())) {
            throw new BizException(400, "Partner is not active");
        }
    }

    private void lockActivePartner(Long partnerId) {
        Partner partner = partnerMapper.selectByIdForUpdate(partnerId);
        if (partner == null) {
            throw new BizException(404, "Partner not found");
        }
        if (!"active".equals(partner.getStatus())) {
            throw new BizException(400, "Partner is not active");
        }
    }

    private void deactivatePartnerStaffAccounts(Long partnerId) {
        List<SysUser> staffUsers = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPartnerId, partnerId)
                .eq(SysUser::getRole, ROLE_PARTNER_STAFF)
                .eq(SysUser::getIsActive, true));
        for (SysUser staff : staffUsers) {
            staff.setIsActive(false);
            staff.setTokenVersion(nextTokenVersion(staff));
            sysUserMapper.updateById(staff);
        }
    }

    private PartnerStaffVO toPartnerStaffVO(SysUser user) {
        PartnerStaffVO vo = new PartnerStaffVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setDisplayName(user.getDisplayName());
        vo.setPartnerId(user.getPartnerId());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setIsActive(user.getIsActive());
        vo.setCreatedAt(user.getCreatedAt());
        vo.setUpdatedAt(user.getUpdatedAt());
        return vo;
    }

    private Map<String, Object> partnerStaffSnapshot(SysUser user) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", user.getId());
        snapshot.put("username", user.getUsername());
        snapshot.put("displayName", user.getDisplayName());
        snapshot.put("partnerId", user.getPartnerId());
        snapshot.put("phone", user.getPhone());
        snapshot.put("email", user.getEmail());
        snapshot.put("isActive", user.getIsActive());
        snapshot.put("tokenVersion", user.getTokenVersion());
        return snapshot;
    }

    private Partner requireAccessiblePartnerAccount(Long partnerId) {
        SysUser operator = currentUserService.requireCurrentUser();
        Partner partner = requirePartner(partnerId);
        currentUserService.ensurePartnerResourceAccess(operator, partner.getId(), "partner_account");
        return partner;
    }

    private PartnerAccount ensureAccount(Long partnerId) {
        PartnerAccount account = partnerAccountMapper.selectOne(
                new LambdaQueryWrapper<PartnerAccount>().eq(PartnerAccount::getPartnerId, partnerId)
        );
        if (account != null) {
            return account;
        }
        PartnerAccount created = new PartnerAccount();
        created.setPartnerId(partnerId);
        created.setCurrentBalance(BigDecimal.ZERO);
        created.setTotalRecharge(BigDecimal.ZERO);
        created.setTotalDeduction(BigDecimal.ZERO);
        created.setCurrency("CNY");
        created.setStatus("active");
        partnerAccountMapper.insert(created);
        return created;
    }

    private String buildTxnNo(String prefix) {
        return "PT" + prefix + System.currentTimeMillis() + RandomUtil.randomNumbers(6);
    }

    private String buildRechargeOrderNo() {
        return "PRO" + System.currentTimeMillis() + RandomUtil.randomNumbers(6);
    }

    private String generatePartnerCode() {
        String prefix = "P" + LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        String candidate;
        do {
            candidate = prefix + RandomUtil.randomNumbers(6);
            Long existing = partnerMapper.selectCount(new LambdaQueryWrapper<Partner>().eq(Partner::getPartnerCode, candidate));
            if (existing == null || existing == 0) {
                return candidate;
            }
        } while (true);
    }

    private String normalizePartnerLevel(String requestedLevel, String fallbackLevel) {
        if (StringUtils.hasText(requestedLevel)) {
            return requestedLevel.trim();
        }
        return StringUtils.hasText(fallbackLevel) ? fallbackLevel.trim() : DEFAULT_PARTNER_LEVEL;
    }

    private String normalizeNullableText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String buildPartnerUsername(String partnerCode) {
        String base = "partner_" + partnerCode.toLowerCase();
        String candidate = base;
        int attempt = 0;
        while (sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, candidate)) > 0) {
            attempt++;
            candidate = base + "_" + attempt;
        }
        return candidate;
    }

    private void logDiscountHistory(Long partnerId, BigDecimal oldRate, BigDecimal newRate, String reason) {
        PartnerDiscountHistory history = new PartnerDiscountHistory();
        history.setPartnerId(partnerId);
        history.setOldDiscountRate(oldRate);
        history.setNewDiscountRate(newRate);
        Long operatorUserId = currentUserService.requireCurrentUser().getId();
        history.setOperatorUserId(operatorUserId);
        history.setReason(reason);
        partnerDiscountHistoryMapper.insert(history);
        activityLogService.logAction(
                operatorUserId,
                "partner.discount.update",
                "partner_discount_history",
                history.getId(),
                detailMap("discountRate", oldRate),
                detailMap("discountRate", newRate),
                java.util.Map.of("partnerId", partnerId, "reason", reason)
        );
    }

    private boolean isDiscountChanged(BigDecimal oldRate, BigDecimal newRate) {
        if (oldRate == null && newRate == null) {
            return false;
        }
        if (oldRate == null || newRate == null) {
            return true;
        }
        return oldRate.compareTo(newRate) != 0;
    }

    private int nextTokenVersion(SysUser user) {
        int current = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        return current + 1;
    }

    private PartnerAccountTxn rechargeAccount(Long partnerId, BigDecimal amount, String offlineReference, String remark,
                                              String bizType, Long rechargeOrderId) {
        PartnerAccount account = lockAccount(partnerId);
        BigDecimal before = account.getCurrentBalance();
        BigDecimal after = before.add(amount);

        account.setCurrentBalance(after);
        account.setTotalRecharge(account.getTotalRecharge().add(amount));
        partnerAccountMapper.updateById(account);

        PartnerAccountTxn txn = new PartnerAccountTxn();
        txn.setPartnerId(partnerId);
        txn.setAccountId(account.getId());
        txn.setTxnNo(buildTxnNo("R"));
        txn.setTxnType("recharge");
        txn.setBizType(bizType);
        txn.setAmount(amount);
        txn.setBalanceBefore(before);
        txn.setBalanceAfter(after);
        txn.setRechargeOrderId(rechargeOrderId);
        txn.setOperatorUserId(currentUserService.requireCurrentUser().getId());
        txn.setOfflineReference(normalizeOfflineReference(offlineReference));
        txn.setRemark(remark);
        partnerAccountTxnMapper.insert(txn);
        activityLogService.logAction(
                txn.getOperatorUserId(),
                rechargeOrderId == null ? "partner.account.recharge.direct" : "partner.account.recharge.deposit",
                "partner_account_txn",
                txn.getId(),
                java.util.Map.of("balance", before),
                java.util.Map.of("balance", after),
                detailMap("partnerId", partnerId, "amount", amount, "txnNo", txn.getTxnNo(), "rechargeOrderId", rechargeOrderId)
        );
        return txn;
    }

    private String normalizeOfflineReference(String offlineReference) {
        if (!StringUtils.hasText(offlineReference)) {
            return null;
        }
        String trimmed = offlineReference.trim();
        if (trimmed.length() > MAX_OFFLINE_REFERENCE_LENGTH) {
            throw new BizException(400, "线下凭证附件数据过长");
        }
        return trimmed;
    }

    private String migrateInitialVoucherReference(Long partnerId, Long operatorId, String offlineReference) {
        if (!StringUtils.hasText(offlineReference)) {
            throw new BizException(400, "初始积分大于0时必须上传线下凭证");
        }
        List<PartnerVoucherFile> files = parseVoucherFiles(offlineReference);
        if (files.isEmpty()) {
            throw new BizException(400, "初始积分大于0时必须上传线下凭证");
        }
        String expectedPrefix = "partner-vouchers/initial/" + operatorId + "/";
        List<PartnerVoucherFile> migrated = files.stream().map(file -> {
            String sourceKey = requireText(file.getObjectKey(), "凭证文件参数缺失");
            if (!sourceKey.startsWith(expectedPrefix) || sourceKey.contains("..")) {
                throw new BizException(403, "无权使用该初始积分凭证");
            }
            byte[] bytes = minioStorageService.getObjectBytes(sourceKey);
            String targetKey = "partner-vouchers/" + partnerId + "/" + LocalDate.now() + "/"
                    + System.currentTimeMillis() + "-" + RandomUtil.randomString(8) + "-"
                    + sanitizeFileName(file.getFileName());
            minioStorageService.uploadBytes(bytes, targetKey, file.getContentType());
            return new PartnerVoucherFile(
                    trimToLength(file.getFileName(), 255),
                    file.getFileSize(),
                    trimToLength(file.getContentType(), 128),
                    targetKey,
                    buildVoucherDownloadUrl(partnerId, targetKey)
            );
        }).collect(Collectors.toList());
        try {
            return objectMapper.writeValueAsString(migrated);
        } catch (JsonProcessingException ex) {
            throw new BizException(500, "保存初始积分凭证失败");
        }
    }

    private List<PartnerVoucherFile> parseVoucherFiles(String offlineReference) {
        String normalized = normalizeOfflineReference(offlineReference);
        try {
            return objectMapper.readValue(normalized, new TypeReference<List<PartnerVoucherFile>>() {});
        } catch (JsonProcessingException ex) {
            throw new BizException(400, "线下凭证数据格式不正确");
        }
    }

    private String requireVoucherObjectKey(Long partnerId, String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            throw new BizException(400, "凭证文件参数缺失");
        }
        String normalized = objectKey.trim();
        String expectedPrefix = "partner-vouchers/" + partnerId + "/";
        if (!normalized.startsWith(expectedPrefix) || normalized.contains("..")) {
            throw new BizException(403, "无权访问该凭证文件");
        }
        return normalized;
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(400, message);
        }
        return value.trim();
    }

    private String buildVoucherDownloadUrl(Long partnerId, String objectKey) {
        return "/api/partners/" + partnerId + "/account/vouchers/download?objectKey="
                + URLEncoder.encode(objectKey, StandardCharsets.UTF_8);
    }

    private String originalName(MultipartFile file) {
        String name = file.getOriginalFilename();
        return StringUtils.hasText(name) ? name.trim() : "voucher";
    }

    private String sanitizeFileName(String fileName) {
        String normalized = fileName == null ? "voucher" : fileName.trim();
        normalized = normalized.replace("\\", "/");
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0) {
            normalized = normalized.substring(slash + 1);
        }
        normalized = normalized.replaceAll("[\\r\\n\\t]+", "_")
                .replaceAll("[^a-zA-Z0-9._\\-\\u4e00-\\u9fa5]+", "_");
        if (!StringUtils.hasText(normalized)) {
            return "voucher";
        }
        return trimToLength(normalized.toLowerCase(Locale.ROOT), 120);
    }

    private Map<String, Object> detailMap(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
            Object value = keysAndValues[i + 1];
            if (value != null) {
                map.put(String.valueOf(keysAndValues[i]), value);
            }
        }
        return map;
    }

    private PartnerRechargeOrder lockRechargeOrder(Long partnerId, Long orderId) {
        PartnerRechargeOrder order = partnerRechargeOrderMapper.selectOne(
                new LambdaQueryWrapper<PartnerRechargeOrder>()
                        .eq(PartnerRechargeOrder::getId, orderId)
                        .eq(PartnerRechargeOrder::getPartnerId, partnerId)
                        .last("FOR UPDATE")
        );
        if (order == null) {
            throw new BizException(404, "Recharge order not found");
        }
        return order;
    }

    private String trimToLength(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private PartnerAccount lockAccount(Long partnerId) {
        PartnerAccount account = partnerAccountMapper.selectOne(
                new LambdaQueryWrapper<PartnerAccount>()
                        .eq(PartnerAccount::getPartnerId, partnerId)
                        .last("FOR UPDATE")
        );
        if (account != null) {
            return account;
        }
        return ensureAccount(partnerId);
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
}
