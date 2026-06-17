package com.huanjing.geo.module.system.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.system.dto.SystemAlertTodoVO;
import com.huanjing.geo.module.system.entity.SystemAlert;
import com.huanjing.geo.module.system.mapper.SystemAlertMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SystemAlertService {

    private final SystemAlertMapper systemAlertMapper;
    private final CurrentUserService currentUserService;

    public void createAlert(String alertType,
                            String severity,
                            String source,
                            String message,
                            Map<String, Object> context) {
        SystemAlert alert = new SystemAlert();
        alert.setAlertType(alertType);
        alert.setSeverity(StringUtils.hasText(severity) ? severity : "warn");
        alert.setSource(StringUtils.hasText(source) ? source : "system");
        alert.setMessage(StringUtils.hasText(message) ? message : "unknown");
        alert.setContextJson(context == null ? null : JSONUtil.toJsonStr(context));
        alert.setIsResolved(false);
        systemAlertMapper.insert(alert);
    }

    public void createRecipientAlert(String alertType,
                                     String severity,
                                     String source,
                                     String message,
                                     Map<String, Object> context,
                                     Long recipientUserId,
                                     String recipientRole,
                                     String dedupeKey) {
        String normalizedDedupeKey = StringUtils.hasText(dedupeKey) ? dedupeKey.trim() : null;
        if (StringUtils.hasText(dedupeKey)) {
            SystemAlert existing = systemAlertMapper.selectOne(new LambdaQueryWrapper<SystemAlert>()
                    .eq(SystemAlert::getDedupeKey, normalizedDedupeKey)
                    .last("LIMIT 1"));
            if (existing != null && !Boolean.TRUE.equals(existing.getIsResolved())) {
                return;
            }
            if (existing != null) {
                populateRecipientAlert(
                        existing,
                        alertType,
                        severity,
                        source,
                        message,
                        context,
                        recipientUserId,
                        recipientRole,
                        normalizedDedupeKey
                );
                systemAlertMapper.updateById(existing);
                return;
            }
        }
        SystemAlert alert = new SystemAlert();
        populateRecipientAlert(
                alert,
                alertType,
                severity,
                source,
                message,
                context,
                recipientUserId,
                recipientRole,
                normalizedDedupeKey
        );
        try {
            systemAlertMapper.insert(alert);
        } catch (DuplicateKeyException duplicate) {
            if (!StringUtils.hasText(normalizedDedupeKey)) {
                throw duplicate;
            }
            SystemAlert existing = systemAlertMapper.selectOne(new LambdaQueryWrapper<SystemAlert>()
                    .eq(SystemAlert::getDedupeKey, normalizedDedupeKey)
                    .last("LIMIT 1"));
            if (existing == null || !Boolean.TRUE.equals(existing.getIsResolved())) {
                return;
            }
            populateRecipientAlert(
                    existing,
                    alertType,
                    severity,
                    source,
                    message,
                    context,
                    recipientUserId,
                    recipientRole,
                    normalizedDedupeKey
            );
            systemAlertMapper.updateById(existing);
        }
    }

    public Page<SystemAlertTodoVO> myTodos(long current, long size) {
        SysUser user = currentUserService.requireCurrentUser();
        Page<SystemAlert> page = systemAlertMapper.selectPage(new Page<>(current, size),
                visibleAlertWrapper(user)
                        .eq(SystemAlert::getIsResolved, false)
                        .orderByDesc(SystemAlert::getCreatedAt));
        Page<SystemAlertTodoVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toTodoVO).toList());
        return result;
    }

    public void resolve(Long alertId) {
        currentUserService.ensurePermission("system.alert.resolve");
        SysUser user = currentUserService.requireCurrentUser();
        SystemAlert alert = systemAlertMapper.selectOne(visibleAlertWrapper(user)
                .eq(SystemAlert::getId, alertId)
                .eq(SystemAlert::getIsResolved, false)
                .last("LIMIT 1"));
        if (alert == null) {
            throw new BizException(404, "待办不存在或已处理");
        }
        alert.setIsResolved(true);
        alert.setResolvedBy(user.getId());
        alert.setResolvedAt(LocalDateTime.now());
        systemAlertMapper.updateById(alert);
    }

    public void resolveOpenByDedupeKey(String dedupeKey, Long resolvedBy) {
        if (!StringUtils.hasText(dedupeKey)) {
            return;
        }
        SystemAlert alert = systemAlertMapper.selectOne(new LambdaQueryWrapper<SystemAlert>()
                .eq(SystemAlert::getDedupeKey, dedupeKey.trim())
                .eq(SystemAlert::getIsResolved, false)
                .last("LIMIT 1"));
        if (alert == null) {
            return;
        }
        alert.setIsResolved(true);
        alert.setResolvedBy(resolvedBy);
        alert.setResolvedAt(LocalDateTime.now());
        systemAlertMapper.updateById(alert);
    }

    public void resolveOpenByDedupeKeyPrefix(String dedupeKeyPrefix, Long resolvedBy) {
        if (!StringUtils.hasText(dedupeKeyPrefix)) {
            return;
        }
        List<SystemAlert> alerts = systemAlertMapper.selectList(new LambdaQueryWrapper<SystemAlert>()
                .likeRight(SystemAlert::getDedupeKey, dedupeKeyPrefix.trim())
                .eq(SystemAlert::getIsResolved, false));
        for (SystemAlert alert : alerts) {
            alert.setIsResolved(true);
            alert.setResolvedBy(resolvedBy);
            alert.setResolvedAt(LocalDateTime.now());
            systemAlertMapper.updateById(alert);
        }
    }

    private LambdaQueryWrapper<SystemAlert> visibleAlertWrapper(SysUser user) {
        return new LambdaQueryWrapper<SystemAlert>()
                .and(wrapper -> wrapper.eq(SystemAlert::getRecipientUserId, user.getId())
                        .or()
                        .eq(SystemAlert::getRecipientRole, user.getRole()));
    }

    private void populateRecipientAlert(SystemAlert alert,
                                        String alertType,
                                        String severity,
                                        String source,
                                        String message,
                                        Map<String, Object> context,
                                        Long recipientUserId,
                                        String recipientRole,
                                        String dedupeKey) {
        alert.setAlertType(alertType);
        alert.setSeverity(StringUtils.hasText(severity) ? severity : "warn");
        alert.setSource(StringUtils.hasText(source) ? source : "system");
        alert.setMessage(StringUtils.hasText(message) ? message : "unknown");
        alert.setContextJson(context == null ? null : JSONUtil.toJsonStr(context));
        alert.setRecipientUserId(recipientUserId);
        alert.setRecipientRole(StringUtils.hasText(recipientRole) ? recipientRole.trim() : null);
        alert.setDedupeKey(StringUtils.hasText(dedupeKey) ? dedupeKey.trim() : null);
        alert.setIsResolved(false);
        alert.setResolvedBy(null);
        alert.setResolvedAt(null);
    }

    private SystemAlertTodoVO toTodoVO(SystemAlert alert) {
        SystemAlertTodoVO vo = new SystemAlertTodoVO();
        vo.setId(alert.getId());
        vo.setAlertType(alert.getAlertType());
        vo.setSeverity(alert.getSeverity());
        vo.setSource(alert.getSource());
        vo.setMessage(alert.getMessage());
        vo.setContextJson(alert.getContextJson());
        vo.setCreatedAt(alert.getCreatedAt());
        return vo;
    }
}
