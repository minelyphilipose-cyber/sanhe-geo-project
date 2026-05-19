package com.huanjing.geo.module.dispatch.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.util.QuotaPeriodResolver;
import com.huanjing.geo.module.customer.service.CustomerPackageExpiryService;
import com.huanjing.geo.module.dispatch.config.DispatchProperties;
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
import java.time.temporal.ChronoUnit;
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
    private final DispatchProperties dispatchProperties;
    private final CustomerPackageExpiryService customerPackageExpiryService;

    @Transactional
    public void scanAndPlan(LocalDate today) {
        customerPackageExpiryService.scanAndHandle(today);

        List<Project> projects = projectMapper.selectList(
                new LambdaQueryWrapper<Project>()
                        .in(Project::getStatus, List.of("active", "paused"))
                        .isNotNull(Project::getActivatedAt)
        );

        for (Project project : projects) {
            planBiDaily(project, today);
            planContentGeneration(project, today);
            planMonthly(project, today);
            planQuarterly(project, today);
        }
    }

    private void planBiDaily(Project project, LocalDate today) {
        LocalDate activatedDate = project.getActivatedAt().toLocalDate();
        planQuestionTierPoll(project, today, activatedDate, "A", 1);
        planQuestionTierPoll(project, today, activatedDate, "B", 7);
        planQuestionTierPoll(project, today, activatedDate, "C", 14);
    }

    private void planQuestionTierPoll(Project project,
                                      LocalDate today,
                                      LocalDate activatedDate,
                                      String questionTier,
                                      int intervalDays) {
        long daysSinceActivation = ChronoUnit.DAYS.between(activatedDate, today);
        if (daysSinceActivation < 0 || daysSinceActivation % intervalDays != 0) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("mode", "question-poll");
        payload.put("questionTier", questionTier);
        payload.put("batchDate", today.toString());
        payload.put("batchNo", 1);
        dispatchTaskService.createTaskAndEnqueue(
                project.getId(),
                DispatchTaskType.BI_DAILY_POLL,
                today.minusDays(Math.max(intervalDays - 1, 0)),
                today,
                LocalDateTime.now(),
                payload,
                "question-poll:" + questionTier,
                null,
                null
        );
    }

    private void planContentGeneration(Project project, LocalDate today) {
        if (!dispatchProperties.isAutoContentGenerationEnabled()) {
            return;
        }
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

}
