package com.huanjing.geo.module.partner.service;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.partner.dto.PartnerAdjustRequest;
import com.huanjing.geo.module.partner.dto.PartnerCreateRequest;
import com.huanjing.geo.module.partner.dto.PartnerRechargeRequest;
import com.huanjing.geo.module.partner.dto.PartnerUpdateRequest;
import com.huanjing.geo.module.partner.entity.Partner;
import com.huanjing.geo.module.partner.entity.PartnerAccount;
import com.huanjing.geo.module.partner.entity.PartnerAccountTxn;
import com.huanjing.geo.module.partner.mapper.PartnerAccountMapper;
import com.huanjing.geo.module.partner.mapper.PartnerAccountTxnMapper;
import com.huanjing.geo.module.partner.mapper.PartnerMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PartnerService {

    private final PartnerMapper partnerMapper;
    private final PartnerAccountMapper partnerAccountMapper;
    private final PartnerAccountTxnMapper partnerAccountTxnMapper;
    private final CurrentUserService currentUserService;

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
    public Partner create(PartnerCreateRequest req) {
        currentUserService.ensurePermission("partner.write");

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

        PartnerAccount account = new PartnerAccount();
        account.setPartnerId(partner.getId());
        account.setCurrentBalance(0L);
        account.setTotalRecharge(0L);
        account.setTotalDeduction(0L);
        account.setCurrency("CNY");
        account.setStatus("active");
        partnerAccountMapper.insert(account);

        return partner;
    }

    public Partner update(Long id, PartnerUpdateRequest req) {
        currentUserService.ensurePermission("partner.write");

        Partner partner = requirePartner(id);
        partner.setPartnerName(req.getPartnerName());
        partner.setPartnerLevel(req.getPartnerLevel());
        partner.setDiscountRate(req.getDiscountRate());
        partner.setStatus(req.getStatus());
        partner.setContactName(req.getContactName());
        partner.setContactPhone(req.getContactPhone());
        partner.setCity(req.getCity());
        partner.setRemark(req.getRemark());
        partnerMapper.updateById(partner);
        return partner;
    }

    public void updateStatus(Long id, String status) {
        currentUserService.ensurePermission("partner.write");
        Partner partner = requirePartner(id);
        partner.setStatus(status);
        partnerMapper.updateById(partner);
    }

    public PartnerAccount account(Long partnerId) {
        Partner partner = detail(partnerId);
        PartnerAccount account = partnerAccountMapper.selectOne(
                new LambdaQueryWrapper<PartnerAccount>().eq(PartnerAccount::getPartnerId, partner.getId())
        );
        if (account == null) {
            throw new BizException(404, "Partner account not found");
        }
        return account;
    }

    public Page<PartnerAccountTxn> accountTxns(Long partnerId, long current, long size) {
        detail(partnerId);
        return partnerAccountTxnMapper.selectPage(
                new Page<>(current, size),
                new LambdaQueryWrapper<PartnerAccountTxn>()
                        .eq(PartnerAccountTxn::getPartnerId, partnerId)
                        .orderByDesc(PartnerAccountTxn::getCreatedAt)
        );
    }

    @Transactional
    public PartnerAccountTxn recharge(Long partnerId, PartnerRechargeRequest req) {
        currentUserService.ensurePermission("partner.write");
        detail(partnerId);

        PartnerAccount account = ensureAccount(partnerId);
        long before = account.getCurrentBalance();
        long after = before + req.getAmount();

        account.setCurrentBalance(after);
        account.setTotalRecharge(account.getTotalRecharge() + req.getAmount());
        partnerAccountMapper.updateById(account);

        PartnerAccountTxn txn = new PartnerAccountTxn();
        txn.setPartnerId(partnerId);
        txn.setAccountId(account.getId());
        txn.setTxnNo(buildTxnNo("R"));
        txn.setTxnType("recharge");
        txn.setBizType("partner_prepaid");
        txn.setAmount(req.getAmount());
        txn.setBalanceBefore(before);
        txn.setBalanceAfter(after);
        txn.setOperatorUserId(currentUserService.requireCurrentUser().getId());
        txn.setOfflineReference(req.getOfflineReference());
        txn.setRemark(req.getRemark());
        partnerAccountTxnMapper.insert(txn);
        return txn;
    }

    @Transactional
    public PartnerAccountTxn adjust(Long partnerId, PartnerAdjustRequest req) {
        currentUserService.ensurePermission("partner.write");
        detail(partnerId);

        PartnerAccount account = ensureAccount(partnerId);
        long before = account.getCurrentBalance();
        long after = before + req.getAmount();
        if (after < 0) {
            throw new BizException(400, "Balance cannot be negative");
        }

        account.setCurrentBalance(after);
        if (req.getAmount() >= 0) {
            account.setTotalRecharge(account.getTotalRecharge() + req.getAmount());
        } else {
            account.setTotalDeduction(account.getTotalDeduction() + Math.abs(req.getAmount()));
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
        return txn;
    }

    private Partner requirePartner(Long id) {
        Partner partner = partnerMapper.selectById(id);
        if (partner == null) {
            throw new BizException(404, "Partner not found");
        }
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
        created.setCurrentBalance(0L);
        created.setTotalRecharge(0L);
        created.setTotalDeduction(0L);
        created.setCurrency("CNY");
        created.setStatus("active");
        partnerAccountMapper.insert(created);
        return created;
    }

    private String buildTxnNo(String prefix) {
        return "PT" + prefix + System.currentTimeMillis() + RandomUtil.randomNumbers(6);
    }
}
