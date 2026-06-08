package com.huanjing.geo.module.project.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.customer.entity.CompanyPackageBinding;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.customer.service.CompanyPackageBindingService;
import com.huanjing.geo.module.project.dto.ProjectChannelAllocationRequest;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.entity.ProjectChannelAllocation;
import com.huanjing.geo.module.project.mapper.ProjectChannelAllocationAuditMapper;
import com.huanjing.geo.module.project.mapper.ProjectChannelAllocationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectDistributionChannelAllocationServiceTest {

    private CompanyMapper companyMapper;
    private CompanyPackageBindingService bindingService;
    private ProjectChannelAllocationMapper allocationMapper;
    private ProjectDistributionChannelAllocationService service;

    @BeforeEach
    void setUp() {
        companyMapper = mock(CompanyMapper.class);
        bindingService = mock(CompanyPackageBindingService.class);
        allocationMapper = mock(ProjectChannelAllocationMapper.class);
        service = new ProjectDistributionChannelAllocationService(
                companyMapper,
                bindingService,
                allocationMapper,
                mock(ProjectChannelAllocationAuditMapper.class)
        );
        when(companyMapper.lockCompanyForUpdate(7L)).thenReturn(7L);
        when(bindingService.requireActiveBinding(7L)).thenReturn(binding("""
                [
                  {"channelCode":"self_media:zhihu","periodType":"week","quotaLimit":2,"enabled":true}
                ]
                """));
        when(allocationMapper.selectList(any())).thenReturn(List.of());
        when(allocationMapper.maxRevisionByCompany(7L)).thenReturn(0L);
    }

    @Test
    void replaceAllocationsRejectsLegacySelfMediaAggregateChannel() {
        ProjectChannelAllocationRequest req = request("self_media", 1);

        BizException ex = assertThrows(BizException.class,
                () -> service.replaceAllocations(project(), List.of(req), 0L, 99L, "test"));

        assertEquals("Unsupported project distribution channel: self_media", ex.getMessage());
    }

    @Test
    void replaceAllocationsAcceptsSelfMediaPlatformChannel() {
        ProjectChannelAllocationRequest req = request("self_media:zhihu", 1);

        service.replaceAllocations(project(), List.of(req), 0L, 99L, "test");

        ArgumentCaptor<ProjectChannelAllocation> captor = ArgumentCaptor.forClass(ProjectChannelAllocation.class);
        verify(allocationMapper, atLeastOnce()).insert(captor.capture());
        ProjectChannelAllocation row = captor.getAllValues().stream()
                .filter(item -> "self_media:zhihu".equals(item.getChannelCode()))
                .findFirst()
                .orElseThrow();
        assertEquals(1, row.getAllocatedCount());
        assertEquals("week", row.getPeriodTypeSnapshot());
        assertEquals(2, row.getPackageQuotaLimitSnapshot());
    }

    private Project project() {
        Project project = new Project();
        project.setId(11L);
        project.setCompanyId(7L);
        return project;
    }

    private ProjectChannelAllocationRequest request(String channelCode, int allocatedCount) {
        ProjectChannelAllocationRequest req = new ProjectChannelAllocationRequest();
        req.setChannelCode(channelCode);
        req.setAllocatedCount(allocatedCount);
        return req;
    }

    private CompanyPackageBinding binding(String snapshot) {
        CompanyPackageBinding binding = new CompanyPackageBinding();
        binding.setCompanyId(7L);
        binding.setChannelQuotaSnapshot(snapshot);
        return binding;
    }
}
