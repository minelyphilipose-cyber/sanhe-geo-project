package com.huanjing.geo.module.system.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.module.system.entity.SystemAlert;
import com.huanjing.geo.module.system.mapper.SystemAlertMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class SystemAlertService {

    private final SystemAlertMapper systemAlertMapper;

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
        if (StringUtils.hasText(dedupeKey)) {
            Long existing = systemAlertMapper.selectCount(new LambdaQueryWrapper<SystemAlert>()
                    .eq(SystemAlert::getDedupeKey, dedupeKey.trim()));
            if (existing != null && existing > 0) {
                return;
            }
        }
        SystemAlert alert = new SystemAlert();
        alert.setAlertType(alertType);
        alert.setSeverity(StringUtils.hasText(severity) ? severity : "warn");
        alert.setSource(StringUtils.hasText(source) ? source : "system");
        alert.setMessage(StringUtils.hasText(message) ? message : "unknown");
        alert.setContextJson(context == null ? null : JSONUtil.toJsonStr(context));
        alert.setRecipientUserId(recipientUserId);
        alert.setRecipientRole(StringUtils.hasText(recipientRole) ? recipientRole.trim() : null);
        alert.setDedupeKey(StringUtils.hasText(dedupeKey) ? dedupeKey.trim() : null);
        alert.setIsResolved(false);
        systemAlertMapper.insert(alert);
    }
}
