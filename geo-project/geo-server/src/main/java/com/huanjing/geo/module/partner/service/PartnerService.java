package com.huanjing.geo.module.partner.service;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.partner.dto.PartnerAdjustRequest;
import com.huanjing.geo.module.partner.dto.PartnerCreateRequest;
import com.huanjing.geo.module.partner.dto.PartnerCreateResult;
import com.huanjing.geo.module.partner.dto.PartnerRechargeApplyRequest;
import com.huanjing.geo.module.partner.dto.PartnerRechargeAuditRequest;
import com.huanjing.geo.module.partner.dto.PartnerRechargeRequest;
import com.huanjing.geo.module.partner.dto.PartnerUpdateRequest;
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
import com.huanjing.geo.module.system.entity.SysRole;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.entity.SysUserRole;
import com.huanjing.geo.module.system.mapper.SysRoleMapper;
import com.huanjing.geo.module.system.mapper.SysUserMapper;
import com.huanjing.geo.module.system.mapper.SysUserRoleMapper;
import com.huanjing.geo.module.system.service.ActivityLogService;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PartnerService {

    private final PartnerMapper partnerMapper;
    private final PartnerAccountMapper partnerAccountMapper;
    private final PartnerAccountTxnMapper partnerAccountTxnMapper;
    private final PartnerDiscountHistoryMapper partnerDiscountHistoryMapper;
    private final PartnerRechargeOrderMapper partnerRechargeOrderMapper;
    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUserService;
    private final ActivityLogService activityLogService;

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

        return partnerMapper.selectPage(new Page<>(current, size), wrapper);
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

        Partner existed = partnerMapper.selectOne(
                new LambdaQueryWrapper<Partner>().eq(Partner::getPartnerCode, req.getPartnerCode())
        );
        if (existed != null) {
            throw new BizException(400, "partner_code already exists");
        }

        Partner partner = new Partner();
        partner.setPartnerCode(req.getPartnerCode());
        partner.setPartnerName(req.getPartnerName());
        partner.setPartnerLevel(req.getPartnerLevel());
        partner.setDiscountRate(req.getDiscountRate());
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
            txn.setRemark("create partner initial prepaid");
            partnerAccountTxnMapper.insert(txn);
        }

        String username = buildPartnerUsername(req.getPartnerCode());
        String initialPassword = RandomUtil.randomString(10);
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(initialPassword));
        user.setDisplayName(partner.getPartnerName());
        user.setRole("partner");
        user.setPartnerId(partner.getId());
        user.setPhone(partner.getContactPhone());
        user.setIsActive(true);
        user.setTokenVersion(0);
        sysUserMapper.insert(user);
        bindPartnerOwnerRole(user.getId());

        return new PartnerCreateResult(partner, username, initialPassword);
    }

    private void bindPartnerOwnerRole(Long userId) {
        SysRole role = sysRoleMapper.selectOne(
                new LambdaQueryWrapper<SysRole>()
                        .eq(SysRole::getRoleKey, "partner")
                        .eq(SysRole::getStatus, "active")
        );
        if (role == null) {
            throw new BizException(500, "partner role is not configured");
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
        partner.setPartnerLevel(req.getPartnerLevel());
        partner.setDiscountRate(req.getDiscountRate());
        partner.setStatus(req.getStatus());
        partner.setContactName(req.getContactName());
        partner.setContactPhone(req.getContactPhone());
        partner.setCity(req.getCity());
        partner.setRemark(req.getRemark());
        partnerMapper.updateById(partner);
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
        order.setOfflineReference(trimToLength(req.getOfflineReference(), 128));
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
        txn.setOfflineReference(offlineReference);
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
