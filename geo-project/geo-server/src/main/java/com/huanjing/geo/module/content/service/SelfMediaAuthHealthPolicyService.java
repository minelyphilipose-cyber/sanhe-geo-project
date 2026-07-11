package com.huanjing.geo.module.content.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.dto.SelfMediaAuthHealthPolicyUpdateRequest;
import com.huanjing.geo.module.content.dto.SelfMediaAuthHealthPolicyVO;
import com.huanjing.geo.module.content.entity.SelfMediaAuthHealthPolicy;
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

@Service
@RequiredArgsConstructor
public class SelfMediaAuthHealthPolicyService {

    private final SelfMediaAuthHealthPolicyMapper policyMapper;
    private final SelfMediaAuthHealthPolicyAuditMapper auditMapper;
    private final CurrentUserService currentUserService;

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
        requirePermission("self-media.auth-health.read", "company.read");
        return SelfMediaAuthHealthPolicyVO.from(requirePolicy(platform));
    }

    public List<SelfMediaAuthHealthPolicyAudit> audits(String platform) {
        requirePermission("self-media.auth-health.audit", "company.update");
        SelfMediaAuthHealthPolicy policy = requirePolicy(platform);
        return auditMapper.selectList(new LambdaQueryWrapper<SelfMediaAuthHealthPolicyAudit>()
                .eq(SelfMediaAuthHealthPolicyAudit::getPolicyId, policy.getId())
                .orderByDesc(SelfMediaAuthHealthPolicyAudit::getChangedAt)
                .last("LIMIT 100"));
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

    private void requirePermission(String permission, String legacyPermission) {
        if (!currentUserService.hasPermission(permission)
                && !currentUserService.hasPermission(legacyPermission)) {
            throw new BizException(403, "No permission: " + permission);
        }
    }
}
