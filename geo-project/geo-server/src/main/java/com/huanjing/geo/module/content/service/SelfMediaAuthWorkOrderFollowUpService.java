package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.module.content.entity.SelfMediaAuthWorkOrder;
import com.huanjing.geo.module.content.mapper.SelfMediaAuthWorkOrderMapper;
import com.huanjing.geo.module.system.service.SystemAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class SelfMediaAuthWorkOrderFollowUpService {
    static final String ALERT_TYPE = "SELF_MEDIA_AUTH_WORK_ORDER_FOLLOW_UP";
    private static final String DEDUPE_PREFIX = "self_media_auth_work_order_follow_up:";

    private final SelfMediaAuthWorkOrderMapper workOrderMapper;
    private final SystemAlertService systemAlertService;

    public int scanOnce() {
        LocalDateTime now = LocalDateTime.now();
        List<SelfMediaAuthWorkOrder> rows = workOrderMapper.selectList(
                new LambdaQueryWrapper<SelfMediaAuthWorkOrder>()
                        .isNotNull(SelfMediaAuthWorkOrder::getNextFollowUpAt)
                        .orderByAsc(SelfMediaAuthWorkOrder::getNextFollowUpAt));
        int dueCount = 0;
        List<String> activeDedupeKeys = new ArrayList<>();
        for (SelfMediaAuthWorkOrder row : rows) {
            String dedupeKey = DEDUPE_PREFIX + row.getId();
            boolean due = row.getOwnerUserId() != null
                    && !"resolved".equals(row.getStatus())
                    && row.getNextFollowUpAt() != null
                    && !row.getNextFollowUpAt().isAfter(now);
            if (!due) {
                continue;
            }
            activeDedupeKeys.add(dedupeKey);
            systemAlertService.createRecipientAlert(
                    ALERT_TYPE,
                    "high",
                    "self_media_auth_work_order",
                    "账号健康工单已到跟进时间，请尽快处理",
                    Map.of(
                            "route", "/admin/monitoring/account-auth-health?brandId=" + row.getBrandId(),
                            "brandId", row.getBrandId(),
                            "workOrderId", row.getId(),
                            "workOrderStatus", row.getStatus(),
                            "nextFollowUpAt", row.getNextFollowUpAt().toString(),
                            "actionRoleText", "工单负责人"
                    ),
                    row.getOwnerUserId(),
                    null,
                    dedupeKey
            );
            dueCount++;
        }
        systemAlertService.resolveOpenByDedupeKeyPrefixExcept(DEDUPE_PREFIX, activeDedupeKeys, null);
        return dueCount;
    }
}
