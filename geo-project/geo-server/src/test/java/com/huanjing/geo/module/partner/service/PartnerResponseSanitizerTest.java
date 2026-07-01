package com.huanjing.geo.module.partner.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.module.customer.dto.CompanyDistributionQuotaItemVO;
import com.huanjing.geo.module.customer.dto.CompanyDistributionQuotaVO;
import com.huanjing.geo.module.customer.dto.CompanyKeywordGroupQuotaVO;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.entity.CompanyPackageBinding;
import com.huanjing.geo.module.partner.dto.PartnerCompanyPackageBindingVO;
import com.huanjing.geo.module.partner.dto.PartnerCompanyKeywordGroupQuotaVO;
import com.huanjing.geo.module.partner.dto.PartnerCompanyVO;
import com.huanjing.geo.module.partner.dto.PartnerKeywordGroupListItemVO;
import com.huanjing.geo.module.partner.dto.PartnerKeywordGroupQuestionVO;
import com.huanjing.geo.module.partner.dto.PartnerKeywordGroupVO;
import com.huanjing.geo.module.partner.dto.PartnerPackagePlanVO;
import com.huanjing.geo.module.partner.dto.PartnerProjectKeywordGroupQuotaVO;
import com.huanjing.geo.module.partner.dto.PartnerProjectVO;
import com.huanjing.geo.module.project.dto.KeywordGroupListItemVO;
import com.huanjing.geo.module.project.dto.KeywordGroupQuestionVO;
import com.huanjing.geo.module.project.dto.KeywordGroupVO;
import com.huanjing.geo.module.project.dto.ProjectChannelAllocationQuotaVO;
import com.huanjing.geo.module.project.dto.ProjectChannelAllocationVO;
import com.huanjing.geo.module.project.dto.ProjectKeywordGroupQuotaVO;
import com.huanjing.geo.module.project.entity.PackageChannelQuotaConfig;
import com.huanjing.geo.module.project.entity.PackagePlan;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PartnerResponseSanitizerTest {

    private CurrentUserService currentUserService;
    private PartnerResponseSanitizer sanitizer;
    private SysUser currentUser;

    @BeforeEach
    void setUp() {
        currentUserService = mock(CurrentUserService.class);
        sanitizer = new PartnerResponseSanitizer(currentUserService);
        currentUser = new SysUser();
        currentUser.setId(1L);
        when(currentUserService.requireCurrentUser()).thenReturn(currentUser);
    }

    @Test
    void internalUserGetsOriginalProjectResponse() {
        when(currentUserService.isPartnerUser(currentUser)).thenReturn(false);
        Project project = new Project();

        assertSame(project, sanitizer.project(project));
    }

    @Test
    void partnerProjectFiltersHiddenChannelsAndInternalFields() {
        when(currentUserService.isPartnerUser(currentUser)).thenReturn(true);
        Project project = new Project();
        project.setId(10L);
        project.setDeliveryMode("internal");
        project.setDeductionTxnNo("txn-1");
        project.setPlanKeywordGroupLimitA(11);
        project.setPlanKeywordGroupLimitB(22);
        project.setPlanKeywordGroupLimitC(33);
        project.setSelectedKeywordSavedKeywordsA(5L);
        project.setSelectedKeywordSavedKeywordsB(6L);
        project.setSelectedKeywordSavedKeywordsC(7L);
        project.setSelectedPlatformCodesP0(List.of("official_site", "industry_site", "self_media:wechat", "self_media:netease"));
        project.setSelectedKeywordGroups(List.of(keywordGroup()));
        project.setChannelAllocations(List.of(
                allocation("official_site"),
                allocation("industry_site"),
                allocation("authority_media:industry_media"),
                allocation("self_media:wechat"),
                allocation("self_media:netease")
        ));

        PartnerProjectVO vo = assertInstanceOf(PartnerProjectVO.class, sanitizer.project(project));

        assertEquals(10L, vo.getId());
        assertEquals(List.of("official_site", "self_media:wechat"), vo.getSelectedPlatformCodesP0());
        assertEquals(List.of("official_site", "self_media:wechat"),
                vo.getChannelAllocations().stream().map(item -> item.getChannelCode()).toList());
        assertEquals("Agent官网", vo.getChannelAllocations().get(0).getChannelName());
        assertEquals("公众号", vo.getChannelAllocations().get(1).getChannelName());
        assertEquals(11, vo.getPlanCoreQuestionLimit());
        assertEquals(5L, vo.getSelectedCoreQuestionSavedKeywords());
        assertEquals(5L, vo.getSelectedKeywordGroups().get(0).getSavedCoreQuestionCount());
        assertEquals("核心问题", vo.getSelectedKeywordGroups().get(0).getTypeLabel());
        assertNoBcGetter(vo);
        assertNoBcGetter(vo.getSelectedKeywordGroups().get(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void partnerProjectPageReturnsPartnerDtos() {
        when(currentUserService.isPartnerUser(currentUser)).thenReturn(true);
        Project project = new Project();
        project.setId(11L);
        Page<Project> page = new Page<>(1, 20, 1);
        page.setRecords(List.of(project));

        Page<PartnerProjectVO> result = assertInstanceOf(Page.class, sanitizer.projectPage(page));

        assertEquals(1, result.getRecords().size());
        assertInstanceOf(PartnerProjectVO.class, result.getRecords().get(0));
    }

    @Test
    void partnerPackagePlanFiltersHiddenChannelConfigs() {
        when(currentUserService.isPartnerUser(currentUser)).thenReturn(true);
        PackagePlan plan = new PackagePlan();
        plan.setId(20L);
        plan.setPackageName("partner-plan");
        plan.setKeywordGroupLimitA(12);
        plan.setKeywordGroupLimitB(22);
        plan.setKeywordGroupLimitC(32);
        plan.setChannelQuotaConfigs(List.of(
                packageChannel("official_site"),
                packageChannel("forum"),
                packageChannel("industry_site"),
                packageChannel("self_media:douyin"),
                packageChannel("self_media:sohu")
        ));

        @SuppressWarnings("unchecked")
        List<PartnerPackagePlanVO> result = (List<PartnerPackagePlanVO>) sanitizer.packagePlans(List.of(plan));

        assertEquals(1, result.size());
        assertEquals(List.of("official_site", "self_media:douyin"),
                result.get(0).getChannelQuotaConfigs().stream().map(item -> item.getChannelCode()).toList());
        assertEquals("抖音", result.get(0).getChannelQuotaConfigs().get(1).getChannelName());
        assertEquals(12, result.get(0).getCoreQuestionLimit());
        assertNoBcGetter(result.get(0));
    }

    @Test
    void partnerPackageBindingHidesRawSnapshotAndFiltersVisibleSnapshot() {
        when(currentUserService.isPartnerUser(currentUser)).thenReturn(true);
        CompanyPackageBinding binding = new CompanyPackageBinding();
        binding.setId(30L);
        binding.setKeywordGroupLimitA(13);
        binding.setKeywordGroupLimitB(23);
        binding.setKeywordGroupLimitC(33);
        binding.setChannelQuotaSnapshot("""
                [
                  {"channelCode":"official_site","periodType":"monthly","quotaLimit":10,"enabled":true},
                  {"channelCode":"industry_site","periodType":"monthly","quotaLimit":20,"enabled":true},
                  {"channelCode":"self_media:zhihu","periodType":"monthly","quotaLimit":30,"enabled":true},
                  {"channelCode":"self_media:netease","periodType":"monthly","quotaLimit":40,"enabled":true}
                ]
                """);

        PartnerCompanyPackageBindingVO vo = assertInstanceOf(
                PartnerCompanyPackageBindingVO.class,
                sanitizer.companyPackageBinding(binding)
        );

        assertEquals(30L, vo.getId());
        assertEquals(List.of("official_site", "self_media:zhihu"),
                vo.getVisibleChannelQuotas().stream().map(item -> item.getChannelCode()).toList());
        assertEquals(13, vo.getCoreQuestionLimit());
        assertNoBcGetter(vo);
        assertFalse(hasGetter(vo, "getChannelQuotaSnapshot"));
    }

    @Test
    void partnerCompanyHidesInternalOwnerFields() {
        when(currentUserService.isPartnerUser(currentUser)).thenReturn(true);
        Company company = new Company();
        company.setId(40L);
        company.setCompanyName("customer");
        company.setOwnerId(100L);
        company.setOwnerName("internal delivery");
        company.setSalesOwnerId(101L);
        company.setPartnerStaffOwnerId(102L);

        PartnerCompanyVO vo = assertInstanceOf(PartnerCompanyVO.class, sanitizer.company(company));

        assertEquals("customer", vo.getCompanyName());
        assertFalse(hasGetter(vo, "getOwnerId"));
        assertFalse(hasGetter(vo, "getOwnerName"));
        assertFalse(hasGetter(vo, "getSalesOwnerId"));
        assertFalse(hasGetter(vo, "getPartnerStaffOwnerId"));
    }

    @Test
    void partnerQuotaResponsesFilterHiddenChannels() {
        when(currentUserService.isPartnerUser(currentUser)).thenReturn(true);
        ProjectChannelAllocationQuotaVO projectQuota = new ProjectChannelAllocationQuotaVO();
        projectQuota.setItems(List.of(allocation("official_site"), allocation("authority_media:industry_media"), allocation("self_media:xiaohongshu")));

        ProjectChannelAllocationQuotaVO sanitizedProjectQuota = assertInstanceOf(
                ProjectChannelAllocationQuotaVO.class,
                sanitizer.projectChannelAllocationQuota(projectQuota)
        );
        assertEquals(List.of("official_site", "self_media:xiaohongshu"),
                sanitizedProjectQuota.getItems().stream().map(ProjectChannelAllocationVO::getChannelCode).toList());

        CompanyDistributionQuotaVO companyQuota = new CompanyDistributionQuotaVO();
        companyQuota.setHasLimitMismatch(true);
        companyQuota.setItems(List.of(distribution("official_site", false), distribution("forum", true), distribution("self_media:baijiahao", false)));

        CompanyDistributionQuotaVO sanitizedCompanyQuota = assertInstanceOf(
                CompanyDistributionQuotaVO.class,
                sanitizer.companyDistributionQuotas(companyQuota)
        );
        assertEquals(List.of("official_site", "self_media:baijiahao"),
                sanitizedCompanyQuota.getItems().stream().map(CompanyDistributionQuotaItemVO::getChannelCode).toList());
        assertFalse(sanitizedCompanyQuota.getHasLimitMismatch());
    }

    @Test
    void partnerKeywordGroupQuotaResponsesOnlyExposeCoreQuestions() {
        when(currentUserService.isPartnerUser(currentUser)).thenReturn(true);
        ProjectKeywordGroupQuotaVO projectQuota = new ProjectKeywordGroupQuotaVO();
        projectQuota.setCompanyId(50L);
        projectQuota.setExcludeProjectId(51L);
        projectQuota.setQuotaLimitA(10);
        projectQuota.setQuotaLimitB(20);
        projectQuota.setQuotaLimitC(30);
        projectQuota.setActiveAllocatedCountA(1);
        projectQuota.setCurrentProjectAllocatedCountA(2);
        projectQuota.setRemainingCountA(7);
        projectQuota.setInputMaxA(7);

        PartnerProjectKeywordGroupQuotaVO projectVo = assertInstanceOf(
                PartnerProjectKeywordGroupQuotaVO.class,
                sanitizer.projectKeywordGroupQuota(projectQuota)
        );

        assertEquals(10, projectVo.getCoreQuestionQuotaLimit());
        assertEquals(7, projectVo.getRemainingCoreQuestionCount());
        assertNoBcGetter(projectVo);

        CompanyKeywordGroupQuotaVO companyQuota = new CompanyKeywordGroupQuotaVO();
        companyQuota.setCompanyId(60L);
        companyQuota.setQuotaLimitA(11);
        companyQuota.setQuotaLimitB(21);
        companyQuota.setQuotaLimitC(31);
        companyQuota.setUsedCountA(3);
        companyQuota.setRemainingCountA(8);

        PartnerCompanyKeywordGroupQuotaVO companyVo = assertInstanceOf(
                PartnerCompanyKeywordGroupQuotaVO.class,
                sanitizer.companyKeywordGroupQuota(companyQuota)
        );

        assertEquals(11, companyVo.getCoreQuestionQuotaLimit());
        assertEquals(3, companyVo.getUsedCoreQuestionCount());
        assertNoBcGetter(companyVo);
    }

    @Test
    @SuppressWarnings("unchecked")
    void partnerKeywordGroupStandaloneResponsesOnlyExposeCoreQuestions() {
        when(currentUserService.isPartnerUser(currentUser)).thenReturn(true);
        Page<KeywordGroupListItemVO> page = new Page<>(1, 20, 1);
        page.setRecords(List.of(keywordGroup()));

        Page<PartnerKeywordGroupListItemVO> listVo = assertInstanceOf(Page.class, sanitizer.keywordGroupPage(page));

        assertEquals(5L, listVo.getRecords().get(0).getSavedCoreQuestionCount());
        assertNoBcGetter(listVo.getRecords().get(0));

        KeywordGroupVO detail = new KeywordGroupVO();
        detail.setId(101L);
        detail.setType("raw");
        detail.setTypeLabel("raw type");
        detail.setSavedKeywordCountA(8L);
        detail.setSavedKeywordCountB(9L);
        detail.setSavedKeywordCountC(10L);

        PartnerKeywordGroupVO detailVo = assertInstanceOf(PartnerKeywordGroupVO.class, sanitizer.keywordGroup(detail));

        assertEquals("核心问题", detailVo.getTypeLabel());
        assertEquals(8L, detailVo.getSavedCoreQuestionCount());
        assertNoBcGetter(detailVo);

        KeywordGroupQuestionVO question = new KeywordGroupQuestionVO();
        question.setId(201L);
        question.setQuestionTier("A");
        question.setQuestionText("core question");
        Page<KeywordGroupQuestionVO> questionPage = new Page<>(1, 20, 1);
        questionPage.setRecords(List.of(question));

        Page<PartnerKeywordGroupQuestionVO> questionVo = assertInstanceOf(Page.class, sanitizer.keywordGroupQuestions(questionPage));

        assertEquals("core question", questionVo.getRecords().get(0).getQuestionText());
        assertFalse(hasGetter(questionVo.getRecords().get(0), "getQuestionTier"));
    }

    @Test
    void nullPackageBindingStaysNullForPartner() {
        when(currentUserService.isPartnerUser(currentUser)).thenReturn(true);

        assertNull(sanitizer.companyPackageBinding(null));
    }

    private ProjectChannelAllocationVO allocation(String channelCode) {
        ProjectChannelAllocationVO vo = new ProjectChannelAllocationVO();
        vo.setChannelCode(channelCode);
        vo.setChannelName("raw-" + channelCode);
        vo.setEnabled(true);
        vo.setQuotaLimit(10);
        return vo;
    }

    private PackageChannelQuotaConfig packageChannel(String channelCode) {
        PackageChannelQuotaConfig config = new PackageChannelQuotaConfig();
        config.setChannelCode(channelCode);
        config.setEnabled(true);
        config.setQuotaLimit(10);
        return config;
    }

    private CompanyDistributionQuotaItemVO distribution(String channelCode, boolean limitMismatch) {
        CompanyDistributionQuotaItemVO vo = new CompanyDistributionQuotaItemVO();
        vo.setChannelCode(channelCode);
        vo.setLimitMismatch(limitMismatch);
        return vo;
    }

    private boolean hasGetter(Object bean, String getterName) {
        return List.of(bean.getClass().getMethods()).stream().anyMatch(method -> method.getName().equals(getterName));
    }

    private void assertNoBcGetter(Object bean) {
        List<String> getterNames = List.of(bean.getClass().getMethods()).stream()
                .map(method -> method.getName())
                .toList();
        assertTrue(getterNames.stream().noneMatch(name -> name.endsWith("B") || name.endsWith("C")),
                "Partner DTO must not expose B/C getters: " + getterNames);
    }

    private KeywordGroupListItemVO keywordGroup() {
        KeywordGroupListItemVO vo = new KeywordGroupListItemVO();
        vo.setId(100L);
        vo.setName("group");
        vo.setType("custom");
        vo.setTypeLabel("raw type");
        vo.setSavedKeywordCountA(5L);
        vo.setSavedKeywordCountB(6L);
        vo.setSavedKeywordCountC(7L);
        return vo;
    }
}
