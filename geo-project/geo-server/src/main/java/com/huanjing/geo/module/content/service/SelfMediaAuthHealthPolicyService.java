package com.huanjing.geo.module.content.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.dto.SelfMediaAuthHealthPolicyUpdateRequest;
import com.huanjing.geo.module.content.dto.SelfMediaAuthHealthPolicyVO;
import com.huanjing.geo.module.content.dto.SelfMediaAuthHealthPolicyImpactVO;
import com.huanjing.geo.module.content.entity.SelfMediaAuthHealthPolicy;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.entity.SelfMediaAuthHealthPolicyAudit;
import com.huanjing.geo.module.content.mapper.SelfMediaAuthHealthPolicyAuditMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaAuthHealthPolicyMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SelfMediaAuthHealthPolicyService {

    private static final Set<String> ALLOWED_RECIPIENT_ROLES = Set.of("delivery_manager", "manager");

    private final SelfMediaAuthHealthPolicyMapper policyMapper;
    private final SelfMediaAuthHealthPolicyAuditMapper auditMapper;
    private final CurrentUserService currentUserService;
    private final SelfMediaAccountMapper accountMapper;

    public SelfMediaAuthHealthPolicy requirePolicy(String platform) {
        String normalized = normalizePlatform(platform);
        SelfMediaAuthHealthPolicy row = policyMapper.selectOne(new LambdaQueryWrapper<SelfMediaAuthHealthPolicy>()
                .eq(SelfMediaAuthHealthPolicy::getPlatformCode, normalized)
                .last("LIMIT 1"));
        if (row == null) throw new BizException(404, "未配置该平台的授权健康策略");
        return row;
    }

    public SelfMediaAuthHealthPolicy findPolicy(String platform) {
        if (!StringUtils.hasText(platform)) return null;
        return policyMapper.selectOne(new LambdaQueryWrapper<SelfMediaAuthHealthPolicy>()
                .eq(SelfMediaAuthHealthPolicy::getPlatformCode, normalizePlatform(platform))
                .last("LIMIT 1"));
    }

    public SelfMediaAuthHealthPolicyVO get(String platform) {
        requireAnyPermission("self-media.auth-health.read", "company.read", "content.read");
        return SelfMediaAuthHealthPolicyVO.from(requirePolicy(platform));
    }

    public List<SelfMediaAuthHealthPolicyAudit> audits(String platform) {
        requireAnyPermission("self-media.auth-health.audit", "company.update");
        SelfMediaAuthHealthPolicy policy = requirePolicy(platform);
        return auditMapper.selectList(new LambdaQueryWrapper<SelfMediaAuthHealthPolicyAudit>()
                .eq(SelfMediaAuthHealthPolicyAudit::getPolicyId, policy.getId())
                .orderByDesc(SelfMediaAuthHealthPolicyAudit::getChangedAt)
                .last("LIMIT 100"));
    }

    public SelfMediaAuthHealthPolicyImpactVO previewImpact(String platform, int reverifyIntervalDays, int warningDays) {
        requireAnyPermission("self-media.auth-health.read", "company.read", "content.read");
        String normalized = normalizePlatform(platform);
        LocalDateTime now = LocalDateTime.now();
        List<SelfMediaAccount> accounts = accountMapper.selectList(new LambdaQueryWrapper<SelfMediaAccount>()
                .eq(SelfMediaAccount::getPlatform, normalized)
                .eq(SelfMediaAccount::getStatus, "active")
                .isNull(SelfMediaAccount::getDeletedAt));
        int currentOverdue = 0;
        int projectedOverdue = 0;
        int projectedDueSoon = 0;
        int newlyOverdue = 0;
        for (SelfMediaAccount account : accounts) {
            if (account.getRecommendedReverifyAt() != null && !account.getRecommendedReverifyAt().isAfter(now)) {
                currentOverdue++;
            }
            LocalDateTime base = account.getLastLoginVerifiedAt() != null
                    ? account.getLastLoginVerifiedAt() : account.getCreatedAt();
            if (base == null) continue;
            LocalDateTime projected = base.plusDays(Math.max(reverifyIntervalDays, 1));
            if (!projected.isAfter(now)) {
                projectedOverdue++;
                if (account.getRecommendedReverifyAt() == null || account.getRecommendedReverifyAt().isAfter(now)) newlyOverdue++;
            } else if (!projected.minusDays(Math.max(warningDays, 0)).isAfter(now)) {
                projectedDueSoon++;
            }
        }
        return new SelfMediaAuthHealthPolicyImpactVO(normalized, accounts.size(), currentOverdue,
                projectedOverdue, projectedDueSoon, newlyOverdue,
                "仅调整内部复验计划与告警，不会修改登录事实，也不会要求客户重新登录");
    }

    @Transactional
    public SelfMediaAuthHealthPolicyVO update(String platform, SelfMediaAuthHealthPolicyUpdateRequest request) {
        validate(request);
        SysUser operator = currentUserService.requireCurrentUser();
        if (!currentUserService.hasPermission("self-media.auth-health.policy-manage")
                && !currentUserService.hasPermission("company.update")) {
            throw new BizException(403, "无权修改授权健康策略");
        }
        SelfMediaAuthHealthPolicy current = requirePolicy(platform);
        String beforeJson = JSONUtil.toJsonStr(current);
        LocalDateTime now = LocalDateTime.now();
        int affected = policyMapper.update(null, new LambdaUpdateWrapper<SelfMediaAuthHealthPolicy>()
                .eq(SelfMediaAuthHealthPolicy::getId, current.getId())
                .eq(SelfMediaAuthHealthPolicy::getVersion, request.version())
                .set(SelfMediaAuthHealthPolicy::getEnabled, request.enabled())
                .set(SelfMediaAuthHealthPolicy::getReverifyIntervalDays, request.reverifyIntervalDays())
                .set(SelfMediaAuthHealthPolicy::getWarningDays, request.warningDays())
                .set(SelfMediaAuthHealthPolicy::getCredentialReferenceDays, request.credentialReferenceDays())
                .set(SelfMediaAuthHealthPolicy::getCredentialExpiryMode, request.credentialExpiryMode())
                .set(SelfMediaAuthHealthPolicy::getAlertEnabled, request.alertEnabled())
                .set(SelfMediaAuthHealthPolicy::getDefaultRecipientRole, trimToNull(request.defaultRecipientRole()))
                .set(SelfMediaAuthHealthPolicy::getVersion, request.version() + 1)
                .set(SelfMediaAuthHealthPolicy::getUpdatedBy, operator.getId())
                .set(SelfMediaAuthHealthPolicy::getUpdatedAt, now));
        if (affected != 1) throw new BizException(409, "策略已被其他人员修改，请刷新后重试");
        SelfMediaAuthHealthPolicy updated = requirePolicy(platform);
        SelfMediaAuthHealthPolicyAudit audit = new SelfMediaAuthHealthPolicyAudit();
        audit.setPolicyId(updated.getId());
        audit.setPlatformCode(updated.getPlatformCode());
        audit.setBeforeJson(beforeJson);
        audit.setAfterJson(JSONUtil.toJsonStr(updated));
        audit.setChangeReason(request.changeReason().trim());
        audit.setChangedBy(operator.getId());
        audit.setChangedAt(now);
        auditMapper.insert(audit);
        return SelfMediaAuthHealthPolicyVO.from(updated);
    }

    private void validate(SelfMediaAuthHealthPolicyUpdateRequest request) {
        if (request.warningDays() > request.reverifyIntervalDays()) {
            throw new BizException(400, "临期提醒提前量不能大于建议复验周期");
        }
        if (!SelfMediaAuthRiskEvaluator.MODES.contains(request.credentialExpiryMode())) {
            throw new BizException(400, "不支持的到期参考方式");
        }
        String recipientRole = trimToNull(request.defaultRecipientRole());
        if (recipientRole != null && !ALLOWED_RECIPIENT_ROLES.contains(recipientRole.toLowerCase(Locale.ROOT))) {
            throw new BizException(400, "默认处理角色仅支持 delivery_manager 或 manager");
        }
    }

    private String normalizePlatform(String value) {
        if (!StringUtils.hasText(value)) throw new BizException(400, "平台不能为空");
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("douyin_image_text".equals(normalized)) return "douyin";
        return normalized;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void requireAnyPermission(String permission, String... compatiblePermissions) {
        if (currentUserService.hasPermission(permission)) return;
        for (String compatiblePermission : compatiblePermissions) {
            if (currentUserService.hasPermission(compatiblePermission)) return;
        }
        throw new BizException(403, "No permission: " + permission);
    }
}
