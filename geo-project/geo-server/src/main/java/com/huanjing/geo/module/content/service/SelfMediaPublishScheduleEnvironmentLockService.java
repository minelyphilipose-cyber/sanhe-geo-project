package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.content.entity.SelfMediaPublishScheduleEnvironmentLock;
import com.huanjing.geo.module.content.mapper.SelfMediaPublishScheduleEnvironmentLockMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SelfMediaPublishScheduleEnvironmentLockService {
    private final SelfMediaPublishScheduleEnvironmentLockMapper mapper;

    public boolean tryAcquire(Long browserEnvironmentId,
                              Long scheduleId,
                              LocalDateTime lockedUntil,
                              LocalDateTime now) {
        if (browserEnvironmentId == null || scheduleId == null) {
            return false;
        }
        mapper.upsertIfExpired(browserEnvironmentId, scheduleId, lockedUntil, now);
        SelfMediaPublishScheduleEnvironmentLock owner = mapper.selectByEnvironmentId(browserEnvironmentId);
        return owner != null
                && scheduleId.equals(owner.getScheduleId())
                && owner.getLockedUntil() != null
                && owner.getLockedUntil().isAfter(now);
    }

    public void release(Long scheduleId) {
        if (scheduleId != null) {
            mapper.deleteByScheduleId(scheduleId);
        }
    }

    public boolean renew(Long browserEnvironmentId,
                         Long scheduleId,
                         LocalDateTime lockedUntil,
                         LocalDateTime now) {
        if (browserEnvironmentId == null || scheduleId == null) {
            return false;
        }
        return mapper.renew(browserEnvironmentId, scheduleId, lockedUntil, now) > 0;
    }
}
