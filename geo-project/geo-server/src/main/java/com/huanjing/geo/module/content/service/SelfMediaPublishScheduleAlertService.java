package com.huanjing.geo.module.content.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.content.constant.SelfMediaPublishScheduleConstants;
import com.huanjing.geo.module.content.entity.SelfMediaPublishSchedule;
import com.huanjing.geo.module.content.entity.SelfMediaPublishScheduleAlert;
import com.huanjing.geo.module.content.mapper.SelfMediaPublishScheduleAlertMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaPublishScheduleMapper;
import com.huanjing.geo.module.content.vo.SelfMediaPublishScheduleAlertVO;
import com.huanjing.geo.module.extension.entity.LocalAgentSession;
import com.huanjing.geo.module.extension.mapper.LocalAgentSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SelfMediaPublishScheduleAlertService {
    public static final String TYPE_HELPER_OFFLINE = "HELPER_OFFLINE";
    public static final String TYPE_SCHEDULE_FILL_OVERDUE = "SCHEDULE_FILL_OVERDUE";
    public static final String TYPE_TASK_STUCK_RUNNING = "TASK_STUCK_RUNNING";
    public static final String TYPE_PLATFORM_SCHEDULE_MISSED = "PLATFORM_SCHEDULE_MISSED";
    public static final String TYPE_PUBLISH_RESULT_UNKNOWN = "PUBLISH_RESULT_UNKNOWN";
    public static final String TYPE_MANUAL_REQUIRED = "MANUAL_REQUIRED";
    public static final String TYPE_SCHEDULE_FAILED = "SCHEDULE_FAILED";
    public static final String TYPE_PUBLISH_FAILED = "PUBLISH_FAILED";
    public static final String TYPE_PUBLISH_LINK_MISSING = "PUBLISH_LINK_MISSING";

    private static final String STATUS_OPEN = "open";
    private static final String STATUS_RESOLVED = "resolved";
    private static final String SEVERITY_CRITICAL = "critical";
    private static final String SEVERITY_WARNING = "warning";
    private static final String SEVERITY_INFO = "info";
    private static final int EVIDENCE_LIMIT = 1800;
    private static final List<String> MONITORED_STATUSES = List.of(
            SelfMediaPublishScheduleConstants.STATUS_PENDING,
            SelfMediaPublishScheduleConstants.STATUS_FILLING,
            SelfMediaPublishScheduleConstants.STATUS_FILLED_VERIFIED,
            SelfMediaPublishScheduleConstants.STATUS_SCHEDULING,
            SelfMediaPublishScheduleConstants.STATUS_SCHEDULED,
            SelfMediaPublishScheduleConstants.STATUS_PUBLISH_DUE,
            SelfMediaPublishScheduleConstants.STATUS_CHECKING_PUBLISH_RESULT,
            SelfMediaPublishScheduleConstants.STATUS_PUBLISHED_CONFIRMED,
            SelfMediaPublishScheduleConstants.STATUS_PUBLISH_UNKNOWN,
            SelfMediaPublishScheduleConstants.STATUS_SCHEDULE_FAILED,
            SelfMediaPublishScheduleConstants.STATUS_PUBLISH_FAILED,
            SelfMediaPublishScheduleConstants.STATUS_MANUAL_REQUIRED,
            SelfMediaPublishScheduleConstants.STATUS_ROUTED_TO_SEMI_AUTO,
            SelfMediaPublishScheduleConstants.STATUS_CANCEL_PENDING_PLATFORM
    );

    private final SelfMediaPublishScheduleMapper scheduleMapper;
    private final SelfMediaPublishScheduleAlertMapper alertMapper;
    private final LocalAgentSessionMapper localAgentSessionMapper;
    private final ObjectMapper objectMapper;

    @Value("${geo.self-media-schedule.alert.scan-limit:200}")
    private int scanLimit;

    @Value("${geo.self-media-schedule.alert.overdue-grace-minutes:3}")
    private int overdueGraceMinutes;

    @Value("${geo.self-media-schedule.alert.publish-check-grace-minutes:5}")
    private int publishCheckGraceMinutes;

    @Value("${geo.self-media-schedule.alert.helper-offline-minutes:5}")
    private int helperOfflineMinutes;

    @Transactional
    public int scanOnce() {
        LocalDateTime now = LocalDateTime.now();
        List<SelfMediaPublishSchedule> candidates = scheduleMapper.selectMonitorCandidates(
                MONITORED_STATUSES,
                now,
                now.minusMinutes(Math.max(overdueGraceMinutes, 1)),
                Math.max(scanLimit, 1)
        );
        int changed = 0;
        for (SelfMediaPublishSchedule schedule : candidates) {
            changed += reconcile(schedule, now);
        }
        return changed;
    }

    @Transactional
    public int reconcile(SelfMediaPublishSchedule schedule, LocalDateTime now) {
        if (schedule == null || schedule.getId() == null) {
            return 0;
        }
        List<AlertDraft> drafts = detectAlerts(schedule, now);
        Map<String, AlertDraft> draftByType = drafts.stream()
                .collect(Collectors.toMap(AlertDraft::type, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        List<SelfMediaPublishScheduleAlert> openAlerts = alertMapper.selectOpenByScheduleId(schedule.getId());
        Map<String, SelfMediaPublishScheduleAlert> openByType = openAlerts.stream()
                .collect(Collectors.toMap(SelfMediaPublishScheduleAlert::getAlertType, Function.identity(), (left, right) -> left));

        int changed = 0;
        for (AlertDraft draft : drafts) {
            SelfMediaPublishScheduleAlert open = openByType.get(draft.type());
            if (open == null) {
                insertAlert(schedule, draft, now);
                changed++;
            } else if (refreshAlert(open, schedule, draft, now)) {
                changed++;
            }
        }
        Set<String> activeTypes = draftByType.keySet();
        for (SelfMediaPublishScheduleAlert open : openAlerts) {
            if (!activeTypes.contains(open.getAlertType())) {
                resolveAlert(open, now);
                changed++;
            }
        }
        return changed;
    }

    public List<SelfMediaPublishScheduleAlertVO> listOpenAlerts(Long scheduleId) {
        if (scheduleId == null) {
            return List.of();
        }
        return alertMapper.selectOpenByScheduleId(scheduleId).stream()
                .map(SelfMediaPublishScheduleAlertVO::from)
                .toList();
    }

    public Map<Long, List<SelfMediaPublishScheduleAlertVO>> listOpenAlertsByScheduleIds(List<Long> scheduleIds) {
        if (scheduleIds == null || scheduleIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = scheduleIds.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return alertMapper.selectOpenByScheduleIds(ids).stream()
                .map(SelfMediaPublishScheduleAlertVO::from)
                .collect(Collectors.groupingBy(
                        SelfMediaPublishScheduleAlertVO::getScheduleId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    private List<AlertDraft> detectAlerts(SelfMediaPublishSchedule row, LocalDateTime now) {
        String status = normalize(row.getStatus());
        if (isTerminalHealthy(status)) {
            return List.of();
        }
        List<AlertDraft> alerts = new ArrayList<>();
        if (SelfMediaPublishScheduleConstants.STATUS_PUBLISHED_CONFIRMED.equals(status)
                && !StringUtils.hasText(row.getPlatformPublishedUrl())) {
            alerts.add(alert(TYPE_PUBLISH_LINK_MISSING, SEVERITY_WARNING,
                    "排期已确认发布但平台发布链接未回写"));
            return alerts;
        }
        if (SelfMediaPublishScheduleConstants.STATUS_PENDING.equals(status)
                && SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION.equals(row.getQueueKind())
                && isDueBefore(row.getNextAttemptAt(), now.minusMinutes(Math.max(overdueGraceMinutes, 1)))
                && !isLocked(row, now)) {
            alerts.add(alert(TYPE_SCHEDULE_FILL_OVERDUE, SEVERITY_WARNING,
                    "排期已到填充时间但仍未被助手领取"));
        }
        if (isRunningStatus(status) && row.getLockedUntil() != null && row.getLockedUntil().isBefore(now)) {
            alerts.add(alert(TYPE_TASK_STUCK_RUNNING, SEVERITY_CRITICAL,
                    "任务锁已过期但仍停留在执行中状态"));
        }
        if (SelfMediaPublishScheduleConstants.STATUS_SCHEDULED.equals(status)
                && isDueBefore(row.getPlatformScheduledAt(), now.minusMinutes(Math.max(publishCheckGraceMinutes, 1)))) {
            alerts.add(alert(TYPE_PLATFORM_SCHEDULE_MISSED, SEVERITY_WARNING,
                    "平台定时时间已过但发布结果尚未确认"));
        }
        if (SelfMediaPublishScheduleConstants.STATUS_PUBLISH_UNKNOWN.equals(status)) {
            boolean exhausted = row.getNextAttemptAt() == null
                    || (row.getAttemptCount() != null && row.getMaxAttempts() != null
                    && row.getAttemptCount() >= row.getMaxAttempts());
            alerts.add(alert(TYPE_PUBLISH_RESULT_UNKNOWN, exhausted ? SEVERITY_CRITICAL : SEVERITY_WARNING,
                    exhausted ? "发布结果多次回查仍未匹配，请人工确认" : "发布结果暂未匹配，等待系统复查"));
        }
        if (SelfMediaPublishScheduleConstants.STATUS_MANUAL_REQUIRED.equals(status)
                || SelfMediaPublishScheduleConstants.STATUS_ROUTED_TO_SEMI_AUTO.equals(status)) {
            alerts.add(alert(TYPE_MANUAL_REQUIRED, SEVERITY_CRITICAL,
                    "排期已转入人工处理"));
        }
        if (SelfMediaPublishScheduleConstants.STATUS_SCHEDULE_FAILED.equals(status)) {
            alerts.add(alert(TYPE_SCHEDULE_FAILED, SEVERITY_CRITICAL,
                    "平台定时设置失败"));
        }
        if (SelfMediaPublishScheduleConstants.STATUS_PUBLISH_FAILED.equals(status)) {
            alerts.add(alert(TYPE_PUBLISH_FAILED, SEVERITY_CRITICAL,
                    "平台发布结果确认失败"));
        }
        if (needsHelper(row, now) && !hasRecentLocalAgent(row.getCreatedBy(), now)) {
            alerts.add(alert(TYPE_HELPER_OFFLINE, SEVERITY_WARNING,
                    "本地助手近期未在线，排期可能无法自动推进"));
        }
        return alerts.stream()
                .sorted(Comparator.comparing(AlertDraft::severityRank).thenComparing(AlertDraft::type))
                .toList();
    }

    private void insertAlert(SelfMediaPublishSchedule schedule, AlertDraft draft, LocalDateTime now) {
        SelfMediaPublishScheduleAlert row = new SelfMediaPublishScheduleAlert();
        row.setScheduleId(schedule.getId());
        row.setBrandId(schedule.getBrandId());
        row.setArticleId(schedule.getArticleId());
        row.setSelfMediaAccountId(schedule.getSelfMediaAccountId());
        row.setBrowserEnvironmentId(schedule.getBrowserEnvironmentId());
        row.setPlatform(schedule.getPlatform());
        row.setAlertType(draft.type());
        row.setSeverity(draft.severity());
        row.setStatus(STATUS_OPEN);
        row.setMessage(draft.message());
        row.setEvidenceJson(evidenceJson(schedule, draft));
        row.setActiveKey(activeKey(schedule.getId(), draft.type()));
        row.setFirstSeenAt(now);
        row.setLastSeenAt(now);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        try {
            alertMapper.insert(row);
        } catch (DuplicateKeyException duplicate) {
            SelfMediaPublishScheduleAlert existing = alertMapper.selectByActiveKey(row.getActiveKey());
            if (existing != null) {
                refreshAlert(existing, schedule, draft, now);
                return;
            }
            throw duplicate;
        }
    }

    private boolean refreshAlert(SelfMediaPublishScheduleAlert row, SelfMediaPublishSchedule schedule,
                                 AlertDraft draft, LocalDateTime now) {
        String evidenceJson = evidenceJson(schedule, draft);
        boolean changed = !Objects.equals(row.getSeverity(), draft.severity())
                || !Objects.equals(row.getMessage(), draft.message())
                || !Objects.equals(row.getEvidenceJson(), evidenceJson);
        row.setSeverity(draft.severity());
        row.setMessage(draft.message());
        row.setEvidenceJson(evidenceJson);
        row.setLastSeenAt(now);
        row.setUpdatedAt(now);
        alertMapper.updateById(row);
        return changed;
    }

    private void resolveAlert(SelfMediaPublishScheduleAlert row, LocalDateTime now) {
        row.setStatus(STATUS_RESOLVED);
        row.setActiveKey(null);
        row.setResolvedAt(now);
        row.setUpdatedAt(now);
        alertMapper.updateById(row);
    }

    private boolean needsHelper(SelfMediaPublishSchedule row, LocalDateTime now) {
        String status = normalize(row.getStatus());
        if (isTerminalHealthy(status) || isRunningStatus(status)) {
            return false;
        }
        return isDueBefore(row.getNextAttemptAt(), now.minusMinutes(Math.max(overdueGraceMinutes, 1)))
                && List.of(
                SelfMediaPublishScheduleConstants.STATUS_PENDING,
                SelfMediaPublishScheduleConstants.STATUS_PUBLISH_DUE,
                SelfMediaPublishScheduleConstants.STATUS_PUBLISH_UNKNOWN
        ).contains(status);
    }

    private boolean hasRecentLocalAgent(Long operatorId, LocalDateTime now) {
        if (operatorId == null || operatorId <= 0) {
            return true;
        }
        LocalDateTime minSeenAt = now.minusMinutes(Math.max(helperOfflineMinutes, 1));
        List<LocalAgentSession> sessions = localAgentSessionMapper.selectActiveByOperatorId(operatorId);
        if (sessions == null || sessions.isEmpty()) {
            return false;
        }
        return sessions.stream()
                .map(LocalAgentSession::getLastSeenAt)
                .anyMatch(lastSeenAt -> lastSeenAt != null && !lastSeenAt.isBefore(minSeenAt));
    }

    private boolean isTerminalHealthy(String status) {
        return SelfMediaPublishScheduleConstants.STATUS_CANCELLED.equals(status);
    }

    private boolean isRunningStatus(String status) {
        return SelfMediaPublishScheduleConstants.STATUS_FILLING.equals(status)
                || SelfMediaPublishScheduleConstants.STATUS_SCHEDULING.equals(status)
                || SelfMediaPublishScheduleConstants.STATUS_CHECKING_PUBLISH_RESULT.equals(status);
    }

    private boolean isDueBefore(LocalDateTime value, LocalDateTime threshold) {
        return value != null && !value.isAfter(threshold);
    }

    private boolean isLocked(SelfMediaPublishSchedule row, LocalDateTime now) {
        return row.getLockedUntil() != null && row.getLockedUntil().isAfter(now);
    }

    private AlertDraft alert(String type, String severity, String message) {
        return new AlertDraft(type, severity, message);
    }

    private String evidenceJson(SelfMediaPublishSchedule schedule, AlertDraft draft) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("scheduleId", schedule.getId());
        data.put("alertType", draft.type());
        data.put("status", schedule.getStatus());
        data.put("queueKind", schedule.getQueueKind());
        data.put("plannedPublishAt", schedule.getPlannedPublishAt());
        data.put("platformScheduledAt", schedule.getPlatformScheduledAt());
        data.put("nextAttemptAt", schedule.getNextAttemptAt());
        data.put("lockedUntil", schedule.getLockedUntil());
        data.put("attemptCount", schedule.getAttemptCount());
        data.put("maxAttempts", schedule.getMaxAttempts());
        data.put("failureCode", schedule.getFailureCode());
        data.put("failureMessage", schedule.getFailureMessage());
        return evidenceJson(data);
    }

    private String evidenceJson(Map<String, Object> data) {
        try {
            String text = objectMapper.writeValueAsString(data);
            return text.length() <= EVIDENCE_LIMIT ? text : text.substring(0, EVIDENCE_LIMIT);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private String activeKey(Long scheduleId, String alertType) {
        return scheduleId + ":" + alertType;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase() : "";
    }

    private record AlertDraft(String type, String severity, String message) {
        int severityRank() {
            if (SEVERITY_CRITICAL.equals(severity)) {
                return 1;
            }
            if (SEVERITY_WARNING.equals(severity)) {
                return 2;
            }
            if (SEVERITY_INFO.equals(severity)) {
                return 3;
            }
            return 9;
        }
    }
}
