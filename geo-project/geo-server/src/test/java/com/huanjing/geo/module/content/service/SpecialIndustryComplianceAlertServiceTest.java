package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.content.entity.BatchArticleGenerationTask;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.entity.BrandOperatorAssignment;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.mapper.BrandOperatorAssignmentMapper;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.SysUserMapper;
import com.huanjing.geo.module.system.service.SystemAlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpecialIndustryComplianceAlertServiceTest {

    private SystemAlertService systemAlertService;
    private BrandOperatorAssignmentMapper assignmentMapper;
    private CompanyMapper companyMapper;
    private SysUserMapper sysUserMapper;
    private SpecialIndustryComplianceAlertService service;

    @BeforeEach
    void setUp() {
        systemAlertService = mock(SystemAlertService.class);
        assignmentMapper = mock(BrandOperatorAssignmentMapper.class);
        companyMapper = mock(CompanyMapper.class);
        sysUserMapper = mock(SysUserMapper.class);
        when(assignmentMapper.selectList(any())).thenReturn(List.of(assignment(101L)));
        when(companyMapper.selectById(5L)).thenReturn(company(102L));
        when(sysUserMapper.selectById(101L)).thenReturn(activeUser(101L));
        when(sysUserMapper.selectById(102L)).thenReturn(activeUser(102L));
        service = new SpecialIndustryComplianceAlertService(systemAlertService, assignmentMapper, companyMapper, sysUserMapper);
    }

    @Test
    void notifyComplianceDiscardedCreatesErrorTodoWithHitRuleTypes() {
        MedicalArticleComplianceChecker.CheckResult result = new MedicalArticleComplianceChecker.CheckResult(false, List.of(
                new MedicalArticleComplianceChecker.ComplianceIssue(1L, "ranking_claim", "block", "排名第一", "命中规则"),
                new MedicalArticleComplianceChecker.ComplianceIssue(2L, "ranking_claim", "block", "效果最好", "命中规则")
        ));

        service.notifyComplianceDiscarded(project(), brand(), task(), 88L, result);

        ArgumentCaptor<Map<String, Object>> contextCaptor = ArgumentCaptor.forClass(Map.class);
        verify(systemAlertService, times(3)).createRecipientAlert(
                eq("special_industry_compliance_discarded"),
                eq("error"),
                eq("special_industry_compliance"),
                eq("特殊行业文章合规 3 次校验失败，已废弃留痕"),
                contextCaptor.capture(),
                any(),
                any(),
                org.mockito.ArgumentMatchers.startsWith("special-industry:compliance-discarded:article:88")
        );
        Map<String, Object> alertContext = contextCaptor.getAllValues().get(0);
        assertEquals("discarded_compliance_failed", alertContext.get("action"));
        assertTrue(((List<?>) alertContext.get("hitRuleTypes")).contains("ranking_claim"));
    }

    private Project project() {
        Project project = new Project();
        project.setId(10L);
        project.setCompanyId(5L);
        project.setProjectName("医美项目");
        project.setBrandId(20L);
        return project;
    }

    private Brand brand() {
        Brand brand = new Brand();
        brand.setId(20L);
        brand.setBrandName("示例品牌");
        return brand;
    }

    private BatchArticleGenerationTask task() {
        BatchArticleGenerationTask task = new BatchArticleGenerationTask();
        task.setId(30L);
        task.setBatchId(40L);
        task.setTopic("敏感选题");
        task.setMedicalIndustryCode("medical_beauty");
        task.setMedicalCategoryCode("skin");
        return task;
    }

    private BrandOperatorAssignment assignment(Long operatorId) {
        BrandOperatorAssignment assignment = new BrandOperatorAssignment();
        assignment.setBrandId(20L);
        assignment.setOperatorId(operatorId);
        assignment.setRole("PRIMARY");
        assignment.setStatus("active");
        return assignment;
    }

    private Company company(Long ownerId) {
        Company company = new Company();
        company.setId(5L);
        company.setOwnerId(ownerId);
        return company;
    }

    private SysUser activeUser(Long id) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setIsActive(true);
        return user;
    }

}
