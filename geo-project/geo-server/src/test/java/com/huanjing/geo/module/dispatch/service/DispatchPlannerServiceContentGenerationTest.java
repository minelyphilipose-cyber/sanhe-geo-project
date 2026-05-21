package com.huanjing.geo.module.dispatch.service;

import com.huanjing.geo.module.dispatch.enums.DispatchTaskType;
import com.huanjing.geo.module.dispatch.config.DispatchProperties;
import com.huanjing.geo.module.dispatch.mapper.DispatchTaskMapper;
import com.huanjing.geo.module.customer.service.CustomerPackageExpiryService;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.entity.ProjectChannelAllocation;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.project.service.ProjectDistributionChannelAllocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DispatchPlannerServiceContentGenerationTest {

    private ProjectMapper projectMapper;
    private DispatchTaskService dispatchTaskService;
    private ProjectDistributionChannelAllocationService allocationService;
    private DispatchTaskMapper dispatchTaskMapper;
    private DispatchProperties dispatchProperties;
    private CustomerPackageExpiryService customerPackageExpiryService;
    private DispatchPlannerService service;

    @BeforeEach
    void setUp() {
        projectMapper = mock(ProjectMapper.class);
        dispatchTaskService = mock(DispatchTaskService.class);
        allocationService = mock(ProjectDistributionChannelAllocationService.class);
        dispatchTaskMapper = mock(DispatchTaskMapper.class);
        dispatchProperties = new DispatchProperties();
        customerPackageExpiryService = mock(CustomerPackageExpiryService.class);
        service = new DispatchPlannerService(
                projectMapper,
                dispatchTaskService,
                allocationService,
                dispatchTaskMapper,
                dispatchProperties,
                customerPackageExpiryService
        );
    }

    @Test
    void scanSkipsContentGenerationWhenAutoPlanningDisabled() {
        Project project = activeProject(LocalDate.of(2026, 5, 1));
        when(projectMapper.selectList(any())).thenReturn(List.of(project));

        service.scanAndPlan(LocalDate.of(2026, 5, 3));

        verify(allocationService, never()).contentGenerationAllocations(anyLong());
        verify(dispatchTaskService, never()).createTaskAndEnqueue(
                eq(project.getId()),
                eq(DispatchTaskType.CONTENT_GENERATION),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    void planContentGenerationFillsMissingSlotsOnly() {
        dispatchProperties.setAutoContentGenerationEnabled(true);
        Project project = activeProject(LocalDate.of(2026, 5, 1));
        ProjectChannelAllocation allocation = allocation("official_site", "week", 3);
        LocalDate today = LocalDate.of(2026, 5, 3);

        when(projectMapper.selectList(any())).thenReturn(List.of(project));
        when(allocationService.contentGenerationAllocations(project.getId())).thenReturn(List.of(allocation));
        when(dispatchTaskMapper.selectOccupiedGenerationSlotsForUpdate(
                eq(project.getId()),
                eq(DispatchTaskType.CONTENT_GENERATION.name()),
                eq("official_site"),
                eq(LocalDate.of(2026, 4, 27)),
                eq(LocalDate.of(2026, 5, 3))
        )).thenReturn(List.of(1, 3));

        service.scanAndPlan(today);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(dispatchTaskService).createTaskAndEnqueue(
                eq(project.getId()),
                eq(DispatchTaskType.CONTENT_GENERATION),
                eq(LocalDate.of(2026, 4, 27)),
                eq(LocalDate.of(2026, 5, 3)),
                any(LocalDateTime.class),
                payloadCaptor.capture(),
                eq("content:official_site:2"),
                eq("official_site"),
                eq(2)
        );
        assertEquals(2, payloadCaptor.getValue().get("generationSlotNo"));
        assertEquals("week", payloadCaptor.getValue().get("periodType"));
        assertEquals("2026-W18", payloadCaptor.getValue().get("periodKey"));
    }

    @Test
    void dayPeriodBypassesBiDailyGate() {
        dispatchProperties.setAutoContentGenerationEnabled(true);
        Project project = activeProject(LocalDate.of(2026, 5, 1));
        ProjectChannelAllocation allocation = allocation("industry_site", "day", 1);
        LocalDate today = LocalDate.of(2026, 5, 2);

        when(projectMapper.selectList(any())).thenReturn(List.of(project));
        when(allocationService.contentGenerationAllocations(project.getId())).thenReturn(List.of(allocation));
        when(dispatchTaskMapper.selectOccupiedGenerationSlotsForUpdate(
                eq(project.getId()),
                eq(DispatchTaskType.CONTENT_GENERATION.name()),
                eq("industry_site"),
                eq(today),
                eq(today)
        )).thenReturn(List.of());

        service.scanAndPlan(today);

        verify(dispatchTaskService).createTaskAndEnqueue(
                eq(project.getId()),
                eq(DispatchTaskType.CONTENT_GENERATION),
                eq(today),
                eq(today),
                any(LocalDateTime.class),
                any(),
                eq("content:industry_site:1"),
                eq("industry_site"),
                eq(1)
        );
    }

    @Test
    void contentGenerationSkipsInactiveProject() {
        dispatchProperties.setAutoContentGenerationEnabled(true);
        Project project = activeProject(LocalDate.of(2026, 5, 1));
        project.setStatus("paused");
        when(projectMapper.selectList(any())).thenReturn(List.of(project));

        service.scanAndPlan(LocalDate.of(2026, 5, 3));

        verify(allocationService, never()).contentGenerationAllocations(anyLong());
        verify(dispatchTaskService, times(1)).createTaskAndEnqueue(
                eq(project.getId()),
                eq(DispatchTaskType.BI_DAILY_POLL),
                any(),
                any(),
                any(),
                any(),
                eq("question-poll:A"),
                any(),
                any()
        );
    }

    private static Project activeProject(LocalDate activatedDate) {
        Project project = new Project();
        project.setId(100L);
        project.setCompanyId(200L);
        project.setStatus("active");
        project.setContentGenerationEnabled(true);
        project.setActivatedAt(activatedDate.atStartOfDay());
        return project;
    }

    private static ProjectChannelAllocation allocation(String channel, String periodType, int allocatedCount) {
        ProjectChannelAllocation allocation = new ProjectChannelAllocation();
        allocation.setProjectId(100L);
        allocation.setCompanyId(200L);
        allocation.setChannelCode(channel);
        allocation.setPeriodTypeSnapshot(periodType);
        allocation.setAllocatedCount(allocatedCount);
        return allocation;
    }
}
