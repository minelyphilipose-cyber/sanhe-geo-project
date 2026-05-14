package com.huanjing.geo.module.dispatch.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.util.QuotaPeriodResolver;
import com.huanjing.geo.module.dispatch.enums.DispatchTaskType;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.entity.ProjectChannelAllocation;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.project.service.ProjectDistributionChannelAllocationService;
import com.huanjing.geo.module.dispatch.mapper.DispatchTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchPlannerService {

    private final ProjectMapper projectMapper;
    private final DispatchTaskService dispatchTaskService;
    private final ProjectDistributionChannelAllocationService channelAllocationService;
    private final DispatchTaskMapper dispatchTaskMapper;

    @Transactional
    public void scanAndPlan(LocalDate today) {
        List<Project> projects = projectMapper.selectList(
                new LambdaQueryWrapper<Project>()
                        .in(Project::getStatus, List.of("active", "paused"))
                        .isNotNull(Project::getActivatedAt)
        );

        for (Project project : projects) {
            if (handleExpireCheck(project, today)) {
                continue;
            }
            planBiDaily(project, today);
            planContentGeneration(project, today);
            planMonthly(project, today);
            planQuarterly(project, today);
        }
    }

    private boolean handleExpireCheck(Project project, LocalDate today) {
        LocalDate expireDate = resolveExpireDate(project);
        if (expireDate == null || today.isBefore(expireDate)) {
            return false;
        }
        if (!"expired".equals(project.getStatus())) {
            if ("active".equals(project.getStatus())) {
                channelAllocationService.lockCompany(project.getCompanyId());
                channelAllocationService.auditCurrentAllocations(project, null, "project.expire", true);
            }
            Project update = new Project();
            update.setId(project.getId());
            update.setStatus("expired");
            update.setExpiredAt(LocalDateTime.of(today, LocalTime.MIDNIGHT));
            projectMapper.updateById(update);
            log.info("Project {} expired at {}", project.getId(), update.getExpiredAt());
        }
        return true;
    }

    private void planBiDaily(Project project, LocalDate today) {
        if (!DispatchScheduleCalculator.isBiDailyDue(project.getActivatedAt().toLocalDate(), today)) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("mode", "bi-daily");
        payload.put("batchDate", today.toString());
        payload.put("batchNo", 1);
        dispatchTaskService.createTaskAndEnqueue(
                project.getId(),
                DispatchTaskType.BI_DAILY_POLL,
                today.minusDays(1),
                today,
                LocalDateTime.now(),
                payload
        );
    }

    private void planContentGeneration(Project project, LocalDate today) {
        if (!"active".equals(project.getStatus())) {
            return;
        }
        if (Boolean.FALSE.equals(project.getContentGenerationEnabled())) {
            return;
        }
        for (ProjectChannelAllocation allocation : channelAllocationService.contentGenerationAllocations(project.getId())) {
            String channel = allocation.getChannelCode();
            String periodType = allocation.getPeriodTypeSnapshot();
            if (!isContentGenerationDue(project, today, periodType)) {
                continue;
            }
            QuotaPeriodResolver.PeriodWindow window = QuotaPeriodResolver.periodWindow(periodType, today);
            Set<Integer> occupied = new HashSet<>(dispatchTaskMapper.selectOccupiedGenerationSlotsForUpdate(
                    project.getId(),
                    DispatchTaskType.CONTENT_GENERATION.name(),
                    channel,
                    window.start(),
                    window.end()
            ));
            int allocatedCount = allocation.getAllocatedCount() == null ? 0 : allocation.getAllocatedCount();
            for (int slotNo = 1; slotNo <= allocatedCount; slotNo++) {
                if (occupied.contains(slotNo)) {
                    continue;
                }
                Map<String, Object> payload = new HashMap<>();
                payload.put("mode", "content-generation");
                payload.put("batchDate", today.toString());
                payload.put("batchNo", slotNo);
                payload.put("targetChannel", channel);
                payload.put("periodType", periodType);
                payload.put("periodKey", QuotaPeriodResolver.periodKey(periodType, window.start()));
                payload.put("generationSlotNo", slotNo);
                dispatchTaskService.createTaskAndEnqueue(
                        project.getId(),
                        DispatchTaskType.CONTENT_GENERATION,
                        window.start(),
                        window.end(),
                        LocalDateTime.now(),
                        payload,
                        "content:" + channel + ":" + slotNo,
                        channel,
                        slotNo
                );
                occupied.add(slotNo);
            }
        }
    }

    private boolean isContentGenerationDue(Project project, LocalDate today, String periodType) {
        if ("day".equalsIgnoreCase(periodType)) {
            return true;
        }
        return DispatchScheduleCalculator.isBiDailyDue(project.getActivatedAt().toLocalDate(), today);
    }

    private void planMonthly(Project project, LocalDate today) {
        if (today.getDayOfMonth() != 1) {
            return;
        }
        LocalDate monthStart = today.minusMonths(1).withDayOfMonth(1);
        LocalDate monthEnd = today.minusDays(1);
        dispatchTaskService.createTaskAndEnqueue(
                project.getId(),
                DispatchTaskType.MONTHLY_REPORT,
                monthStart,
                monthEnd,
                LocalDateTime.now(),
                Map.of("mode", "monthly")
        );
    }

    private void planQuarterly(Project project, LocalDate today) {
        if (!(today.getMonthValue() == 1 || today.getMonthValue() == 4 || today.getMonthValue() == 7 || today.getMonthValue() == 10)
                || today.getDayOfMonth() != 1) {
            return;
        }

        LocalDate quarterEnd = today.minusDays(1);
        LocalDate quarterStart = quarterEnd.minusMonths(2).withDayOfMonth(1);
        dispatchTaskService.createTaskAndEnqueue(
                project.getId(),
                DispatchTaskType.QUARTERLY_REPORT,
                quarterStart,
                quarterEnd,
                LocalDateTime.now(),
                Map.of("mode", "quarterly")
        );
    }

    private LocalDate resolveExpireDate(Project project) {
        LocalDate byEndDate = project.getEndDate();
        LocalDate byService = null;
        if (project.getActivatedAt() != null && project.getServiceMonths() != null) {
            byService = project.getActivatedAt().toLocalDate().plusMonths(project.getServiceMonths());
        }
        if (byEndDate == null) {
            return byService;
        }
        if (byService == null) {
            return byEndDate;
        }
        return byEndDate.isBefore(byService) ? byEndDate : byService;
    }
}
