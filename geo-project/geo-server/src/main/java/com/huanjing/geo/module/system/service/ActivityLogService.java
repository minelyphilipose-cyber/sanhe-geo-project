package com.huanjing.geo.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.system.dto.ActivityLogItem;
import com.huanjing.geo.module.system.entity.ActivityLog;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.ActivityLogMapper;
import com.huanjing.geo.module.system.mapper.SysUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityLogService {

    private final ActivityLogMapper activityLogMapper;
    private final CurrentUserService currentUserService;
    private final SysUserMapper sysUserMapper;
    private final ObjectMapper objectMapper;

    public void logAction(Long userId, String action, String targetType, Long targetId, Object before, Object after, Object extra) {
        try {
            ActivityLog logItem = new ActivityLog();
            logItem.setUserId(userId);
            logItem.setAction(action);
            logItem.setTargetType(targetType);
            logItem.setTargetId(targetId);
            logItem.setIpAddress(resolveClientIp());
            logItem.setDetailJson(toDetailJson(before, after, extra));
            activityLogMapper.insert(logItem);
        } catch (Exception ex) {
            log.warn("Write activity log failed, action={}, targetType={}, targetId={}, err={}",
                    action, targetType, targetId, ex.getMessage());
        }
    }

    public Page<ActivityLogItem> page(long current, long size,
                                      Long userId, String action, String targetType, Long targetId,
                                      String dateFrom, String dateTo) {
        boolean canReadAll = currentUserService.hasPermission("activity_log.read");
        boolean canReadFinance = currentUserService.hasPermission("activity_log.finance.read");
        if (!canReadAll && !canReadFinance) {
            throw new com.huanjing.geo.common.exception.BizException(403, "No permission: activity_log.read");
        }

        LambdaQueryWrapper<ActivityLog> wrapper = new LambdaQueryWrapper<ActivityLog>()
                .orderByDesc(ActivityLog::getCreatedAt);
        if (userId != null) {
            wrapper.eq(ActivityLog::getUserId, userId);
        }
        if (StringUtils.hasText(action)) {
            wrapper.eq(ActivityLog::getAction, action.trim());
        }
        if (StringUtils.hasText(targetType)) {
            wrapper.eq(ActivityLog::getTargetType, targetType.trim());
        }
        if (targetId != null) {
            wrapper.eq(ActivityLog::getTargetId, targetId);
        }
        if (!canReadAll) {
            // Finance filter must stay wrapped in .and() so these OR branches do not escape outer filters.
            wrapper.and(w -> w.in(ActivityLog::getTargetType, financeTargetTypes())
                    .or()
                    .likeRight(ActivityLog::getAction, "partner.account.")
                    .or()
                    .likeRight(ActivityLog::getAction, "company.account.")
                    .or()
                    .eq(ActivityLog::getAction, "project.sign_and_deduct")
                    .or()
                    .eq(ActivityLog::getAction, "partner.discount.update"));
        }

        LocalDateTime from = parseDateTimeStart(dateFrom);
        LocalDateTime to = parseDateTimeEnd(dateTo);
        if (from != null) {
            wrapper.ge(ActivityLog::getCreatedAt, from);
        }
        if (to != null) {
            wrapper.le(ActivityLog::getCreatedAt, to);
        }

        Page<ActivityLog> rawPage = activityLogMapper.selectPage(new Page<>(current, size), wrapper);
        List<ActivityLog> records = rawPage.getRecords();
        Map<Long, String> userNameMap = buildUserNameMap(records);

        List<ActivityLogItem> items = records.stream().map(r -> {
            ActivityLogItem item = new ActivityLogItem();
            item.setId(r.getId());
            item.setUserId(r.getUserId());
            item.setOperatorName(r.getUserId() == null ? "system" : userNameMap.getOrDefault(r.getUserId(), "unknown"));
            item.setAction(r.getAction());
            item.setTargetType(r.getTargetType());
            item.setTargetId(r.getTargetId());
            item.setDetailJson(r.getDetailJson());
            item.setIpAddress(r.getIpAddress());
            item.setCreatedAt(r.getCreatedAt());
            return item;
        }).toList();

        Page<ActivityLogItem> result = new Page<>(rawPage.getCurrent(), rawPage.getSize(), rawPage.getTotal());
        result.setRecords(items);
        return result;
    }

    private List<String> financeTargetTypes() {
        return List.of("partner_account_txn", "partner_recharge_order", "partner_account", "company_account_txn");
    }

    private Map<Long, String> buildUserNameMap(List<ActivityLog> records) {
        Set<Long> userIds = records.stream()
                .map(ActivityLog::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<SysUser> users = sysUserMapper.selectBatchIds(userIds);
        Map<Long, String> map = new HashMap<>();
        for (SysUser user : users) {
            String name = StringUtils.hasText(user.getDisplayName()) ? user.getDisplayName() : user.getUsername();
            map.put(user.getId(), name);
        }
        return map;
    }

    private String toDetailJson(Object before, Object after, Object extra) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("before", before);
        payload.put("after", after);
        payload.put("extra", extra);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "{\"before\":null,\"after\":null,\"extra\":{\"error\":\"serialize_failed\"}}";
        }
    }

    private String resolveClientIp() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        String[] headerNames = {"X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP"};
        for (String header : headerNames) {
            String value = request.getHeader(header);
            if (!StringUtils.hasText(value) || "unknown".equalsIgnoreCase(value)) {
                continue;
            }
            int comma = value.indexOf(',');
            return comma > 0 ? value.substring(0, comma).trim() : value.trim();
        }
        return request.getRemoteAddr();
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
