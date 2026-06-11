package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.content.constant.MedicalArticleConstants;
import com.huanjing.geo.module.content.entity.ArticleDraft;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    void notifyPublishReviewPendingCreatesOwnerTodosAndManagerFallbackWithRouteAndDedupeKey() {
        MedicalArticleGenerationService.MedicalPromptContext context = medicalContext(null);

        service.notifyPublishReviewPending(project(), brand(), task(), 99L, context);

        ArgumentCaptor<Map<String, Object>> contextCaptor = ArgumentCaptor.forClass(Map.class);
        verify(systemAlertService, times(3)).createRecipientAlert(
                eq("special_industry_publish_review_pending"),
                eq("warn"),
                eq("special_industry_compliance"),
                eq("特殊行业官网档文章待法务发布确认"),
                contextCaptor.capture(),
                any(),
                any(),
                org.mockito.ArgumentMatchers.startsWith("special-industry:publish-review-pending:article:99")
        );
        Map<String, Object> alertContext = contextCaptor.getAllValues().get(0);
        assertEquals("/admin/content/special-industry-compliance", alertContext.get("route"));
        assertEquals(99L, alertContext.get("articleId"));
        assertEquals(10L, alertContext.get("projectId"));
        assertEquals("medical_beauty", alertContext.get("medicalIndustryCode"));
        assertEquals("official_site", alertContext.get("medicalChannelTier"));
        verify(systemAlertService).createRecipientAlert(
                eq("special_industry_publish_review_pending"),
                eq("warn"),
                eq("special_industry_compliance"),
                eq("特殊行业官网档文章待法务发布确认"),
                any(),
                isNull(),
                eq("manager"),
                eq("special-industry:publish-review-pending:article:99:role:manager")
        );
    }

    @Test
    void notifyPublishReviewPendingSkipsWhenMedicalAdReviewNoExists() {
        service.notifyPublishReviewPending(project(), brand(), task(), 99L, medicalContext("审字2026-001"));

        verify(systemAlertService, never()).createRecipientAlert(
                eq("special_industry_publish_review_pending"),
                eq("warn"),
                eq("special_industry_compliance"),
                eq("特殊行业官网档文章待法务发布确认"),
                org.mockito.ArgumentMatchers.any(),
                isNull(),
                eq("manager"),
                eq("special-industry:publish-review-pending:article:99:role:manager")
        );
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

    @Test
    void closePublishReviewPendingResolvesOpenDedupeKey() {
        ArticleDraft article = new ArticleDraft();
        article.setId(77L);

        service.closePublishReviewPending(article, project(), 7L);

        verify(systemAlertService).resolveOpenByDedupeKeyPrefix("special-industry:publish-review-pending:article:77", 7L);
    }

    @Test
    void notifyPublishReviewRejectedCreatesOperationTodo() {
        ArticleDraft article = new ArticleDraft();
        article.setId(66L);
        article.setMedicalIndustryCode("oral");
        article.setMedicalChannelTier("official_site");

        service.notifyPublishReviewRejected(article, project(), 7L, "资质信息不足");

        ArgumentCaptor<Map<String, Object>> contextCaptor = ArgumentCaptor.forClass(Map.class);
        verify(systemAlertService, times(3)).createRecipientAlert(
                eq("special_industry_publish_review_rejected"),
                eq("error"),
                eq("special_industry_compliance"),
                eq("特殊行业官网档文章法务驳回，需运营处理"),
                contextCaptor.capture(),
                any(),
                any(),
                org.mockito.ArgumentMatchers.startsWith("special-industry:publish-review-rejected:article:66")
        );
        Map<String, Object> alertContext = contextCaptor.getAllValues().get(0);
        assertEquals("publish_review_rejected", alertContext.get("action"));
        assertEquals("资质信息不足", alertContext.get("comment"));
    }

    @Test
    void closePublishReviewRejectedResolvesOpenDedupeKey() {
        ArticleDraft article = new ArticleDraft();
        article.setId(66L);

        service.closePublishReviewRejected(article, 7L);

        verify(systemAlertService).resolveOpenByDedupeKeyPrefix("special-industry:publish-review-rejected:article:66", 7L);
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

    private MedicalArticleGenerationService.MedicalPromptContext medicalContext(String medicalAdReviewNo) {
        return new MedicalArticleGenerationService.MedicalPromptContext(
                "medical_beauty",
                MedicalArticleConstants.TIER_OFFICIAL_SITE,
                "skin",
                "皮肤",
                1L,
                "风险科普",
                "faq",
                "risk",
                "合规内核",
                1,
                true,
                "官网文体",
                false,
                "资质引用",
                "许可证",
                "诊疗范围",
                medicalAdReviewNo
        );
    }
}
