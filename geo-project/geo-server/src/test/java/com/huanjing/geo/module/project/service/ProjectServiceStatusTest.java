package com.huanjing.geo.module.project.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.module.dispatch.service.BrandStatementDispatchService;
import com.huanjing.geo.module.customer.access.InternalScopeService;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.entity.CompanyPackageBinding;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.customer.service.CompanyPackageBindingService;
import com.huanjing.geo.module.content.service.SpecialIndustryReadinessService;
import com.huanjing.geo.module.partner.entity.PartnerAccount;
import com.huanjing.geo.module.partner.entity.PartnerAccountTxn;
import com.huanjing.geo.module.partner.mapper.PartnerAccountMapper;
import com.huanjing.geo.module.partner.mapper.PartnerAccountTxnMapper;
import com.huanjing.geo.module.project.dto.ProjectStatusUpdateRequest;
import com.huanjing.geo.module.project.entity.KeywordGroup;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.entity.ProjectKeywordGroupRel;
import com.huanjing.geo.module.project.mapper.KeywordGroupMapper;
import com.huanjing.geo.module.project.mapper.KeywordGroupWordMapper;
import com.huanjing.geo.module.project.mapper.ProjectCustomerRequirementMapper;
import com.huanjing.geo.module.project.mapper.ProjectKeywordGroupRelMapper;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.ActivityLogService;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceStatusTest {

    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private BrandMapper brandMapper;
    @Mock
    private CompanyPackageBindingService companyPackageBindingService;
    @Mock
    private KeywordGroupService keywordGroupService;
    @Mock
    private KeywordGroupMapper keywordGroupMapper;
    @Mock
    private KeywordGroupWordMapper keywordGroupWordMapper;
    @Mock
    private ProjectKeywordGroupRelMapper projectKeywordGroupRelMapper;
    @Mock
    private ProjectCustomerRequirementMapper projectCustomerRequirementMapper;
    @Mock
    private PartnerAccountMapper partnerAccountMapper;
    @Mock
    private PartnerAccountTxnMapper partnerAccountTxnMapper;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private InternalScopeService internalScopeService;
    @Mock
    private ProjectStateGuard projectStateGuard;
    @Mock
    private ActivityLogService activityLogService;
    @Mock
    private BrandStatementDispatchService brandStatementDispatchService;
    @Mock
    private ProjectDistributionChannelAllocationService channelAllocationService;
    @Mock
    private KeywordTypeConfigService keywordTypeConfigService;
    @Mock
    private SpecialIndustryReadinessService specialIndustryReadinessService;

    @InjectMocks
    private ProjectService projectService;

    @Test
    void pageExcludesThirdPartySourceBrandsWhenRequested() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Project.class);

        SysUser operator = new SysUser();
        operator.setId(10L);
        operator.setRole("manager");
        Brand sourceBrand = new Brand();
        sourceBrand.setId(30L);
        Project project = new Project();
        project.setId(20L);
        project.setBrandId(40L);
        project.setStatus("active");
        Page<Project> selected = new Page<>(1, 10);
        selected.setRecords(List.of(project));

        when(currentUserService.requireCurrentUser()).thenReturn(operator);
        when(brandMapper.selectThirdPartySourceBrands()).thenReturn(List.of(sourceBrand));
        when(projectMapper.selectPage(any(), any())).thenReturn(selected);
        when(projectCustomerRequirementMapper.selectList(any())).thenReturn(List.of());
        when(projectKeywordGroupRelMapper.selectList(any())).thenReturn(List.of());
        when(keywordGroupService.calcSavedCountsByGroupIds(any())).thenReturn(Map.of());
        when(keywordGroupService.calcSavedTierCountsByGroupIds(any())).thenReturn(Map.of());

        Page<Project> page = projectService.page(1, 10, null, "active", null, null, null, true);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<Project>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(projectMapper).selectPage(any(), wrapperCaptor.capture());
        String sqlSegment = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("brand_id"));
        assertTrue(sqlSegment.contains("NOT IN"));
        assertFalse(Boolean.TRUE.equals(page.getRecords().get(0).getThirdPartySource()));
    }

    @Test
    void updateStatus_paidPausedProject_movesActiveWithoutDeductingAgain() {
        SysUser operator = new SysUser();
        operator.setId(10L);

        Project project = new Project();
        project.setId(20L);
        project.setCompanyId(30L);
        project.setStatus("paused");
        project.setStage("pending_start");
        project.setOwnerType("partner");
        project.setPartnerId(100L);
        project.setDeductionTxnNo("PTD_ALREADY_PAID");

        ProjectStatusUpdateRequest request = new ProjectStatusUpdateRequest();
        request.setStatus("active");

        when(currentUserService.requireCurrentUser()).thenReturn(operator);
        when(projectMapper.selectById(20L)).thenReturn(project);
        when(projectMapper.selectList(any())).thenReturn(java.util.List.of());
        CompanyPackageBinding binding = new CompanyPackageBinding();
        binding.setKeywordGroupLimit(100);
        when(companyPackageBindingService.requireActiveBinding(30L)).thenReturn(binding);

        projectService.updateStatus(20L, request);

        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectMapper).updateById(captor.capture());
        assertEquals("active", captor.getValue().getStatus());
        assertEquals("PTD_ALREADY_PAID", captor.getValue().getDeductionTxnNo());
        verify(projectStateGuard).ensureCanStart(project, operator);
        verify(partnerAccountMapper, never()).updateById(any(PartnerAccount.class));
        verify(partnerAccountTxnMapper, never()).insert(any(PartnerAccountTxn.class));
        verify(activityLogService).logAction(any(), any(), any(), any(), any(), any(), any());
        verify(projectMapper, never()).deleteById(20L);
    }

    @Test
    void detail_filtersDeletedKeywordGroupsFromSelections() {
        SysUser operator = new SysUser();
        operator.setId(10L);
        operator.setRole("manager");

        Project project = new Project();
        project.setId(20L);
        project.setCompanyId(30L);
        project.setStatus("paused");
        project.setPartnerId(100L);

        ProjectKeywordGroupRel activeRel = new ProjectKeywordGroupRel();
        activeRel.setProjectId(20L);
        activeRel.setKeywordGroupId(101L);
        ProjectKeywordGroupRel deletedRel = new ProjectKeywordGroupRel();
        deletedRel.setProjectId(20L);
        deletedRel.setKeywordGroupId(102L);

        KeywordGroup activeGroup = new KeywordGroup();
        activeGroup.setId(101L);
        activeGroup.setCompanyId(30L);
        activeGroup.setProjectId(20L);
        activeGroup.setName("active group");
        activeGroup.setType("imported");
        activeGroup.setDeleted(false);

        when(currentUserService.requireCurrentUser()).thenReturn(operator);
        when(projectMapper.selectById(20L)).thenReturn(project);
        when(projectCustomerRequirementMapper.selectList(any())).thenReturn(List.of());
        when(projectKeywordGroupRelMapper.selectList(any())).thenReturn(List.of(activeRel, deletedRel));
        when(keywordGroupMapper.selectList(any())).thenReturn(List.of(activeGroup));
        when(keywordGroupService.calcSavedCountsByGroupIds(any())).thenReturn(Map.of(101L, 3L));
        when(keywordGroupService.calcSavedTierCountsByGroupIds(any()))
                .thenReturn(Map.of(101L, new KeywordGroupService.KeywordTierCounts(1L, 1L, 1L)));
        when(keywordTypeConfigService.labelOf("imported")).thenReturn("导入词");
        when(keywordTypeConfigService.isLegacyType("imported")).thenReturn(false);

        Project detail = projectService.detail(20L);

        assertEquals(List.of(101L), detail.getSelectedKeywordGroupIds());
        assertEquals(1, detail.getSelectedKeywordGroupCount());
        assertEquals(1, detail.getSelectedKeywordGroups().size());
        assertEquals(101L, detail.getSelectedKeywordGroups().get(0).getId());
        assertEquals(3L, detail.getSelectedKeywordSavedKeywords());
    }
}
