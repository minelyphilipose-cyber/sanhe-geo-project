package com.huanjing.geo.module.dispatch.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.module.dispatch.enums.DispatchTaskType;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchPlannerService {

    private final ProjectMapper projectMapper;
    private final DispatchTaskService dispatchTaskService;

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
            planBiWeekly(project, today);
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
        if (!DispatchScheduleCalculator.isBiDailyDue(project.getActivatedAt().toLocalDate(), today)) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("mode", "content-generation");
        payload.put("batchDate", today.toString());
        payload.put("batchNo", 1);
        dispatchTaskService.createTaskAndEnqueue(
                project.getId(),
                DispatchTaskType.CONTENT_GENERATION,
                today.minusDays(1),
                today,
                LocalDateTime.now(),
                payload
        );
    }

    private void planBiWeekly(Project project, LocalDate today) {
        if (project.getPlanBiweeklyFrequency() != null && project.getPlanBiweeklyFrequency() == 2) {
            return;
        }
        LocalDate anchor = project.getBiweeklyAnchorDate();
        if (anchor == null) {
            LocalDate firstMonday = DispatchScheduleCalculator.firstBiweeklyMonday(project.getActivatedAt().toLocalDate());
            if (!today.equals(firstMonday)) {
                return;
            }
            Project update = new Project();
            update.setId(project.getId());
            update.setBiweeklyAnchorDate(firstMonday);
            projectMapper.updateById(update);
            project.setBiweeklyAnchorDate(firstMonday);
            anchor = firstMonday;
        }

        if (!DispatchScheduleCalculator.isBiweeklyDue(anchor, today)) {
            return;
        }

        long coveredDays = java.time.temporal.ChronoUnit.DAYS.between(project.getActivatedAt().toLocalDate(), today) + 1;
        Map<String, Object> payload = new HashMap<>();
        payload.put("mode", "biweekly");
        payload.put("firstPeriod", coveredDays < 14);
        payload.put("coveredDays", Math.min(coveredDays, 14));

        dispatchTaskService.createTaskAndEnqueue(
                project.getId(),
                DispatchTaskType.BIWEEKLY_REPORT,
                today.minusDays(13),
                today,
                LocalDateTime.now(),
                payload
        );
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
