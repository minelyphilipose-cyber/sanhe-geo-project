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
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SystemAlertService {

    private static final Set<String> AUTO_RESOLVE_ONLY_ALERT_TYPES = Set.of(
            "SELF_MEDIA_ACCOUNT_AUTH_HEALTH", "FORUM_COOKIE_AUTH_HEALTH",
            "SELF_MEDIA_IDENTITY_PARSER_HEALTH", "SELF_MEDIA_AUTH_WORK_ORDER_FOLLOW_UP");

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

    public void createOrRefreshRecipientAlert(String alertType,
                                              String severity,
                                              String source,
                                              String message,
                                              Map<String, Object> context,
                                              Long recipientUserId,
                                              String recipientRole,
                                              String dedupeKey) {
        String normalizedDedupeKey = StringUtils.hasText(dedupeKey) ? dedupeKey.trim() : null;
        if (StringUtils.hasText(normalizedDedupeKey)) {
            SystemAlert existing = systemAlertMapper.selectOne(new LambdaQueryWrapper<SystemAlert>()
                    .eq(SystemAlert::getDedupeKey, normalizedDedupeKey)
                    .last("LIMIT 1"));
            if (existing != null) {
                boolean wasOpen = !Boolean.TRUE.equals(existing.getIsResolved());
                LocalDateTime previousReadAt = existing.getReadAt();
                LocalDateTime previousSnoozedUntil = existing.getSnoozedUntil();
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
                if (wasOpen) {
                    existing.setReadAt(previousReadAt);
                    existing.setSnoozedUntil(previousSnoozedUntil);
                }
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
            if (existing == null) {
                throw duplicate;
            }
            boolean wasOpen = !Boolean.TRUE.equals(existing.getIsResolved());
            LocalDateTime previousReadAt = existing.getReadAt();
            LocalDateTime previousSnoozedUntil = existing.getSnoozedUntil();
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
            if (wasOpen) {
                existing.setReadAt(previousReadAt);
                existing.setSnoozedUntil(previousSnoozedUntil);
            }
            systemAlertMapper.updateById(existing);
        }
    }

    public Page<SystemAlertTodoVO> myTodos(long current, long size, boolean includeResolved,
                                           boolean unreadOnly, boolean snoozedOnly) {
        SysUser user = currentUserService.requireCurrentUser();
        LambdaQueryWrapper<SystemAlert> wrapper = visibleAlertWrapper(user);
        if (snoozedOnly) {
            wrapper.eq(SystemAlert::getIsResolved, false)
                    .gt(SystemAlert::getSnoozedUntil, LocalDateTime.now());
        } else if (!includeResolved) {
            wrapper.eq(SystemAlert::getIsResolved, false);
            wrapper.and(scope -> scope.isNull(SystemAlert::getSnoozedUntil)
                    .or().le(SystemAlert::getSnoozedUntil, LocalDateTime.now()));
        }
        if (unreadOnly) {
            wrapper.isNull(SystemAlert::getReadAt);
        }
        Page<SystemAlert> page = systemAlertMapper.selectPage(new Page<>(current, size),
                wrapper.orderByDesc(SystemAlert::getCreatedAt));
        Page<SystemAlertTodoVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toTodoVO).toList());
        return result;
    }

    public Page<SystemAlertTodoVO> myTodos(long current, long size, boolean includeResolved, boolean unreadOnly) {
        return myTodos(current, size, includeResolved, unreadOnly, false);
    }

    public long myUnreadCount() {
        SysUser user = currentUserService.requireCurrentUser();
        return systemAlertMapper.selectCount(visibleAlertWrapper(user)
                .isNull(SystemAlert::getReadAt)
                .and(scope -> scope.isNull(SystemAlert::getSnoozedUntil)
                        .or().le(SystemAlert::getSnoozedUntil, LocalDateTime.now())));
    }

    public void markRead(Long alertId) {
        SysUser user = currentUserService.requireCurrentUser();
        SystemAlert alert = systemAlertMapper.selectOne(visibleAlertWrapper(user)
                .eq(SystemAlert::getId, alertId)
                .last("LIMIT 1"));
        if (alert == null) {
            throw new BizException(404, "站内信不存在或无权查看");
        }
        if (alert.getReadAt() == null) {
            alert.setReadAt(LocalDateTime.now());
            systemAlertMapper.updateById(alert);
        }
    }

    public void resolve(Long alertId) {
        SysUser user = currentUserService.requireCurrentUser();
        SystemAlert alert = systemAlertMapper.selectOne(visibleAlertWrapper(user)
                .eq(SystemAlert::getId, alertId)
                .eq(SystemAlert::getIsResolved, false)
                .last("LIMIT 1"));
        if (alert == null) {
            throw new BizException(404, "待办不存在或已处理");
        }
        if (!currentUserService.hasPermission("system.alert.resolve")
                && !Objects.equals(alert.getRecipientUserId(), user.getId())) {
            throw new BizException(403, "No permission: system.alert.resolve");
        }
        if (alert.getAlertType() != null && AUTO_RESOLVE_ONLY_ALERT_TYPES.contains(alert.getAlertType())) {
            throw new BizException(409, "账号健康待办需由系统检测恢复后自动关闭，可先选择稍后提醒");
        }
        alert.setIsResolved(true);
        alert.setResolvedBy(user.getId());
        alert.setResolvedAt(LocalDateTime.now());
        systemAlertMapper.updateById(alert);
    }

    public void snooze(Long alertId, int hours) {
        SysUser user = currentUserService.requireCurrentUser();
        SystemAlert alert = systemAlertMapper.selectOne(visibleAlertWrapper(user)
                .eq(SystemAlert::getId, alertId)
                .eq(SystemAlert::getIsResolved, false)
                .last("LIMIT 1"));
        if (alert == null) throw new BizException(404, "待办不存在或已恢复");
        if (hours < 1 || hours > 168) throw new BizException(400, "稍后提醒时间需在 1 至 168 小时之间");
        alert.setSnoozedUntil(LocalDateTime.now().plusHours(hours));
        alert.setReadAt(LocalDateTime.now());
        systemAlertMapper.updateById(alert);
    }

    public void claim(Long alertId) {
        SysUser user = currentUserService.requireCurrentUser();
        SystemAlert alert = requireVisibleOpenAlert(alertId, user);
        if ("SELF_MEDIA_ACCOUNT_AUTH_HEALTH".equals(alert.getAlertType())
                || "SELF_MEDIA_AUTH_WORK_ORDER_FOLLOW_UP".equals(alert.getAlertType())) {
            throw new BizException(409, "请前往账号授权健康工单认领并处理");
        }
        if (alert.getClaimedBy() != null && !Objects.equals(alert.getClaimedBy(), user.getId())) {
            throw new BizException(409, "该待办已由其他运营人员认领");
        }
        if (alert.getClaimedBy() == null) {
            LocalDateTime now = LocalDateTime.now();
            if (systemAlertMapper.claimIfUnowned(alertId, user.getId(), now) == 0) {
                throw new BizException(409, "该待办已由其他运营人员认领");
            }
        }
    }

    private SystemAlert requireVisibleOpenAlert(Long alertId, SysUser user) {
        SystemAlert alert = systemAlertMapper.selectOne(visibleAlertWrapper(user)
                .eq(SystemAlert::getId, alertId)
                .eq(SystemAlert::getIsResolved, false)
                .last("LIMIT 1"));
        if (alert == null) throw new BizException(404, "待办不存在或已恢复");
        return alert;
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

    public void resolveOpenByDedupeKeyPrefixExcept(String dedupeKeyPrefix,
                                                   Collection<String> activeDedupeKeys,
                                                   Long resolvedBy) {
        if (!StringUtils.hasText(dedupeKeyPrefix)) {
            return;
        }
        Set<String> activeKeys = activeDedupeKeys == null ? Set.of() : new HashSet<>(activeDedupeKeys);
        List<SystemAlert> alerts = systemAlertMapper.selectList(new LambdaQueryWrapper<SystemAlert>()
                .likeRight(SystemAlert::getDedupeKey, dedupeKeyPrefix.trim())
                .eq(SystemAlert::getIsResolved, false));
        for (SystemAlert alert : alerts) {
            String dedupeKey = alert.getDedupeKey();
            if (StringUtils.hasText(dedupeKey) && activeKeys.contains(dedupeKey.trim())) {
                continue;
            }
            alert.setIsResolved(true);
            alert.setResolvedBy(resolvedBy);
            alert.setResolvedAt(LocalDateTime.now());
            systemAlertMapper.updateById(alert);
        }
    }

    private LambdaQueryWrapper<SystemAlert> visibleAlertWrapper(SysUser user) {
        LambdaQueryWrapper<SystemAlert> wrapper = new LambdaQueryWrapper<SystemAlert>();
        if (isSuperAdmin(user)) {
            return wrapper;
        }
        return wrapper.and(scope -> scope.eq(SystemAlert::getRecipientUserId, user.getId())
                .or()
                .eq(SystemAlert::getRecipientRole, user.getRole()));
    }

    private boolean isSuperAdmin(SysUser user) {
        return user != null
                && StringUtils.hasText(user.getRole())
                && "super_admin".equals(user.getRole().trim().toLowerCase(Locale.ROOT));
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
        alert.setReadAt(null);
        alert.setSnoozedUntil(null);
        alert.setOccurrenceCount(alert.getOccurrenceCount() == null ? 1 : alert.getOccurrenceCount() + 1);
        alert.setLastOccurredAt(LocalDateTime.now());
    }

    private SystemAlertTodoVO toTodoVO(SystemAlert alert) {
        SystemAlertTodoVO vo = new SystemAlertTodoVO();
        vo.setId(alert.getId());
        vo.setAlertType(alert.getAlertType());
        vo.setSeverity(alert.getSeverity());
        vo.setSource(alert.getSource());
        vo.setMessage(alert.getMessage());
        vo.setContextJson(alert.getContextJson());
        vo.setIsResolved(Boolean.TRUE.equals(alert.getIsResolved()));
        vo.setResolvedAt(alert.getResolvedAt());
        vo.setReadAt(alert.getReadAt());
        vo.setSnoozedUntil(alert.getSnoozedUntil());
        vo.setClaimedBy(alert.getClaimedBy());
        vo.setClaimedAt(alert.getClaimedAt());
        vo.setOperationNote(alert.getOperationNote());
        vo.setOccurrenceCount(alert.getOccurrenceCount());
        vo.setLastOccurredAt(alert.getLastOccurredAt());
        vo.setAutoResolveOnly(alert.getAlertType() != null && AUTO_RESOLVE_ONLY_ALERT_TYPES.contains(alert.getAlertType()));
        vo.setCreatedAt(alert.getCreatedAt());
        return vo;
    }

    private String limit(String value, int max) {
        if (!StringUtils.hasText(value)) return null;
        String text = value.trim();
        return text.length() > max ? text.substring(0, max) : text;
    }
}
