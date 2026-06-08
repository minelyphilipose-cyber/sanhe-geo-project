package com.huanjing.geo.module.content.vo;

import com.huanjing.geo.module.content.entity.SelfMediaPublishScheduleAlert;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SelfMediaPublishScheduleAlertVO {
    private Long id;
    private Long scheduleId;
    private String alertType;
    private String severity;
    private String status;
    private String message;
    private String evidenceJson;
    private LocalDateTime firstSeenAt;
    private LocalDateTime lastSeenAt;
    private LocalDateTime resolvedAt;

    public static SelfMediaPublishScheduleAlertVO from(SelfMediaPublishScheduleAlert row) {
        if (row == null) {
            return null;
        }
        SelfMediaPublishScheduleAlertVO vo = new SelfMediaPublishScheduleAlertVO();
        vo.setId(row.getId());
        vo.setScheduleId(row.getScheduleId());
        vo.setAlertType(row.getAlertType());
        vo.setSeverity(row.getSeverity());
        vo.setStatus(row.getStatus());
        vo.setMessage(row.getMessage());
        vo.setEvidenceJson(row.getEvidenceJson());
        vo.setFirstSeenAt(row.getFirstSeenAt());
        vo.setLastSeenAt(row.getLastSeenAt());
        vo.setResolvedAt(row.getResolvedAt());
        return vo;
    }
}
