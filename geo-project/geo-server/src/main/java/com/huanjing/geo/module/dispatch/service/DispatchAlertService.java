package com.huanjing.geo.module.dispatch.service;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.dispatch.entity.DispatchAlert;
import com.huanjing.geo.module.dispatch.enums.DispatchAlertSeverity;
import com.huanjing.geo.module.dispatch.mapper.DispatchAlertMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DispatchAlertService {

    private final DispatchAlertMapper dispatchAlertMapper;

    public void createAlert(Long taskId,
                            Long projectId,
                            DispatchAlertSeverity severity,
                            String title,
                            String content,
                            Integer retryCount,
                            String contextJson) {
        DispatchAlert alert = new DispatchAlert();
        alert.setAlertCode("ALT" + System.currentTimeMillis() + RandomUtil.randomNumbers(4));
        alert.setTaskId(taskId);
        alert.setProjectId(projectId);
        alert.setSeverity(severity.value());
        alert.setStatus("open");
        alert.setTitle(title);
        alert.setContent(content);
        alert.setRetryCount(retryCount == null ? 0 : retryCount);
        alert.setContextJson(contextJson);
        dispatchAlertMapper.insert(alert);
    }

    public void resolveAlert(Long alertId, Long userId, String note) {
        DispatchAlert existing = dispatchAlertMapper.selectById(alertId);
        if (existing == null) {
            throw new BizException(404, "Alert not found");
        }
        DispatchAlert alert = new DispatchAlert();
        alert.setId(alertId);
        alert.setStatus("resolved");
        alert.setResolvedBy(userId);
        alert.setResolvedAt(LocalDateTime.now());
        if (StringUtils.hasText(note)) {
            Map<String, Object> ctx = new LinkedHashMap<>();
            if (StringUtils.hasText(existing.getContextJson()) && JSONUtil.isTypeJSONObject(existing.getContextJson())) {
                ctx.putAll(JSONUtil.parseObj(existing.getContextJson()));
            }
            ctx.put("resolveNote", note.trim());
            alert.setContextJson(JSONUtil.toJsonStr(ctx));
        }
        dispatchAlertMapper.updateById(alert);
    }
}
