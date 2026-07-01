package com.huanjing.geo.module.partner.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.content.dto.ChannelQuotaSnapshotItem;
import com.huanjing.geo.module.customer.dto.CompanyKeywordGroupQuotaVO;
import com.huanjing.geo.module.customer.dto.CompanyDistributionQuotaItemVO;
import com.huanjing.geo.module.customer.dto.CompanyDistributionQuotaVO;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.entity.CompanyPackageBinding;
import com.huanjing.geo.module.partner.dto.PartnerChannelQuotaVO;
import com.huanjing.geo.module.partner.dto.PartnerCompanyPackageBindingVO;
import com.huanjing.geo.module.partner.dto.PartnerCompanyKeywordGroupQuotaVO;
import com.huanjing.geo.module.partner.dto.PartnerCompanyVO;
import com.huanjing.geo.module.partner.dto.PartnerKeywordGroupColumnsVO;
import com.huanjing.geo.module.partner.dto.PartnerKeywordGroupListItemVO;
import com.huanjing.geo.module.partner.dto.PartnerKeywordGroupQuestionVO;
import com.huanjing.geo.module.partner.dto.PartnerKeywordGroupVO;
import com.huanjing.geo.module.partner.dto.PartnerPackagePlanVO;
import com.huanjing.geo.module.partner.dto.PartnerProjectKeywordGroupQuotaVO;
import com.huanjing.geo.module.partner.dto.PartnerProjectVO;
import com.huanjing.geo.module.project.dto.KeywordGroupColumnsVO;
import com.huanjing.geo.module.project.dto.KeywordGroupListItemVO;
import com.huanjing.geo.module.project.dto.KeywordGroupQuestionVO;
import com.huanjing.geo.module.project.dto.KeywordGroupVO;
import com.huanjing.geo.module.project.dto.ProjectChannelAllocationQuotaVO;
import com.huanjing.geo.module.project.dto.ProjectChannelAllocationVO;
import com.huanjing.geo.module.project.dto.ProjectKeywordGroupQuotaVO;
import com.huanjing.geo.module.project.entity.PackageChannelQuotaConfig;
import com.huanjing.geo.module.project.entity.PackagePlan;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.service.ProjectDistributionChannelAllocationService;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PartnerResponseSanitizer {

    private static final Set<String> PARTNER_SELF_MEDIA_PLATFORMS = Set.of(
            "wechat", "douyin", "toutiao", "zhihu", "baijiahao", "xiaohongshu"
    );

    private static final Map<String, String> PARTNER_CHANNEL_NAMES = Map.of(
            ProjectDistributionChannelAllocationService.OFFICIAL_SITE, "Agent官网",
            "self_media:wechat", "公众号",
            "self_media:douyin", "抖音",
            "self_media:toutiao", "头条",
            "self_media:zhihu", "知乎",
            "self_media:baijiahao", "百家号",
            "self_media:xiaohongshu", "小红书"
    );

    private final CurrentUserService currentUserService;

    public boolean currentUserIsPartner() {
        SysUser user = currentUserService.requireCurrentUser();
        return currentUserService.isPartnerUser(user);
    }

    public Object projectPage(Page<Project> page) {
        if (!currentUserIsPartner()) {
            return page;
        }
        Page<PartnerProjectVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toPartnerProject).toList());
        return result;
    }

    public Object project(Project project) {
        if (!currentUserIsPartner()) {
            return project;
        }
        return toPartnerProject(project);
    }

    public Object companyPage(Page<Company> page) {
        if (!currentUserIsPartner()) {
            return page;
        }
        Page<PartnerCompanyVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toPartnerCompany).toList());
        return result;
    }

    public Object company(Company company) {
        if (!currentUserIsPartner()) {
            return company;
        }
        return toPartnerCompany(company);
    }

    public Object packagePlans(List<PackagePlan> plans) {
        if (!currentUserIsPartner()) {
            return plans;
        }
        return plans.stream().map(this::toPartnerPackagePlan).toList();
    }

    public Object companyPackageBindings(List<CompanyPackageBinding> bindings) {
        if (!currentUserIsPartner()) {
            return bindings;
        }
        return bindings.stream().map(this::toPartnerCompanyPackageBinding).toList();
    }

    public Object companyPackageBinding(CompanyPackageBinding binding) {
        if (!currentUserIsPartner()) {
            return binding;
        }
        return toPartnerCompanyPackageBinding(binding);
    }

    public Object projectChannelAllocationQuota(ProjectChannelAllocationQuotaVO quota) {
        if (!currentUserIsPartner() || quota == null) {
            return quota;
        }
        ProjectChannelAllocationQuotaVO result = new ProjectChannelAllocationQuotaVO();
        BeanUtils.copyProperties(quota, result);
        result.setItems(filterProjectChannelAllocations(quota.getItems()));
        return result;
    }

    public Object companyDistributionQuotas(CompanyDistributionQuotaVO quota) {
        if (!currentUserIsPartner() || quota == null) {
            return quota;
        }
        CompanyDistributionQuotaVO result = new CompanyDistributionQuotaVO();
        BeanUtils.copyProperties(quota, result);
        result.setItems(quota.getItems() == null
                ? List.of()
                : quota.getItems().stream().filter(item -> isPartnerVisibleChannel(item.getChannelCode())).toList());
        result.setHasLimitMismatch(Boolean.TRUE.equals(result.getHasLimitMismatch())
                && result.getItems().stream().anyMatch(item -> Boolean.TRUE.equals(item.getLimitMismatch())));
        return result;
    }

    public Object keywordGroupPage(Page<KeywordGroupListItemVO> page) {
        if (!currentUserIsPartner()) {
            return page;
        }
        Page<PartnerKeywordGroupListItemVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toPartnerKeywordGroup).toList());
        return result;
    }

    public Object keywordGroup(KeywordGroupVO group) {
        if (!currentUserIsPartner()) {
            return group;
        }
        return toPartnerKeywordGroup(group);
    }

    public Object keywordGroupQuestions(Page<KeywordGroupQuestionVO> page) {
        if (!currentUserIsPartner()) {
            return page;
        }
        Page<PartnerKeywordGroupQuestionVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toPartnerKeywordGroupQuestion).toList());
        return result;
    }

    public Object keywordGroupQuestion(KeywordGroupQuestionVO question) {
        if (!currentUserIsPartner()) {
            return question;
        }
        return toPartnerKeywordGroupQuestion(question);
    }

    public Object projectKeywordGroupQuota(ProjectKeywordGroupQuotaVO quota) {
        if (!currentUserIsPartner() || quota == null) {
            return quota;
        }
        PartnerProjectKeywordGroupQuotaVO vo = new PartnerProjectKeywordGroupQuotaVO();
        vo.setCompanyId(quota.getCompanyId());
        vo.setExcludeProjectId(quota.getExcludeProjectId());
        vo.setCoreQuestionQuotaLimit(quota.getQuotaLimitA());
        vo.setActiveAllocatedCoreQuestionCount(quota.getActiveAllocatedCountA());
        vo.setCurrentProjectAllocatedCoreQuestionCount(quota.getCurrentProjectAllocatedCountA());
        vo.setRemainingCoreQuestionCount(quota.getRemainingCountA());
        vo.setInputMaxCoreQuestionCount(quota.getInputMaxA());
        return vo;
    }

    public Object companyKeywordGroupQuota(CompanyKeywordGroupQuotaVO quota) {
        if (!currentUserIsPartner() || quota == null) {
            return quota;
        }
        PartnerCompanyKeywordGroupQuotaVO vo = new PartnerCompanyKeywordGroupQuotaVO();
        vo.setCompanyId(quota.getCompanyId());
        vo.setPackageBindingId(quota.getPackageBindingId());
        vo.setPackageName(quota.getPackageName());
        vo.setActiveBinding(quota.getActiveBinding());
        vo.setCoreQuestionQuotaLimit(quota.getQuotaLimitA());
        vo.setUsedCoreQuestionCount(quota.getUsedCountA());
        vo.setRemainingCoreQuestionCount(quota.getRemainingCountA());
        vo.setUsageRate(quota.getUsageRate());
        return vo;
    }

    public PartnerProjectVO toPartnerProject(Project project) {
        if (project == null) {
            return null;
        }
        PartnerProjectVO vo = new PartnerProjectVO();
        BeanUtils.copyProperties(project, vo);
        vo.setPlanCoreQuestionLimit(project.getPlanKeywordGroupLimitA());
        vo.setSelectedCoreQuestionSavedKeywords(project.getSelectedKeywordSavedKeywordsA());
        vo.setSelectedPlatformCodesP0(filterChannelCodes(project.getSelectedPlatformCodesP0()));
        vo.setSelectedPlatformCodesP1(filterChannelCodes(project.getSelectedPlatformCodesP1()));
        vo.setSelectedPlatformCodesP2(filterChannelCodes(project.getSelectedPlatformCodesP2()));
        vo.setSelectedKeywordGroups(project.getSelectedKeywordGroups() == null
                ? List.of()
                : project.getSelectedKeywordGroups().stream().map(this::toPartnerKeywordGroup).toList());
        vo.setChannelAllocations(filterProjectChannelAllocations(project.getChannelAllocations()).stream()
                .map(this::toPartnerChannelQuota)
                .toList());
        return vo;
    }

    public PartnerCompanyVO toPartnerCompany(Company company) {
        if (company == null) {
            return null;
        }
        PartnerCompanyVO vo = new PartnerCompanyVO();
        BeanUtils.copyProperties(company, vo);
        return vo;
    }

    public PartnerPackagePlanVO toPartnerPackagePlan(PackagePlan plan) {
        if (plan == null) {
            return null;
        }
        PartnerPackagePlanVO vo = new PartnerPackagePlanVO();
        BeanUtils.copyProperties(plan, vo);
        vo.setCoreQuestionLimit(plan.getKeywordGroupLimitA());
        vo.setChannelQuotaConfigs(plan.getChannelQuotaConfigs() == null
                ? List.of()
                : plan.getChannelQuotaConfigs().stream()
                .filter(config -> isPartnerVisibleChannel(config.getChannelCode()))
                .map(this::toPartnerChannelQuota)
                .toList());
        return vo;
    }

    public PartnerCompanyPackageBindingVO toPartnerCompanyPackageBinding(CompanyPackageBinding binding) {
        if (binding == null) {
            return null;
        }
        PartnerCompanyPackageBindingVO vo = new PartnerCompanyPackageBindingVO();
        BeanUtils.copyProperties(binding, vo);
        vo.setCoreQuestionLimit(binding.getKeywordGroupLimitA());
        vo.setVisibleChannelQuotas(parseVisibleSnapshot(binding.getChannelQuotaSnapshot()));
        return vo;
    }

    private PartnerKeywordGroupListItemVO toPartnerKeywordGroup(KeywordGroupListItemVO source) {
        PartnerKeywordGroupListItemVO vo = new PartnerKeywordGroupListItemVO();
        vo.setId(source.getId());
        vo.setCompanyId(source.getCompanyId());
        vo.setCompanyName(source.getCompanyName());
        vo.setProjectId(source.getProjectId());
        vo.setProjectName(source.getProjectName());
        vo.setPackageType(source.getPackageType());
        vo.setName(source.getName());
        vo.setTypeLabel("核心问题");
        vo.setSavedCoreQuestionCount(source.getSavedKeywordCountA());
        vo.setUpdatedAt(source.getUpdatedAt());
        return vo;
    }

    private PartnerKeywordGroupVO toPartnerKeywordGroup(KeywordGroupVO source) {
        PartnerKeywordGroupVO vo = new PartnerKeywordGroupVO();
        vo.setId(source.getId());
        vo.setCompanyId(source.getCompanyId());
        vo.setCompanyName(source.getCompanyName());
        vo.setProjectId(source.getProjectId());
        vo.setProjectName(source.getProjectName());
        vo.setPackageType(source.getPackageType());
        vo.setName(source.getName());
        vo.setTypeLabel("核心问题");
        vo.setAreaEnabled(source.getAreaEnabled());
        vo.setFunctionIndustryTag(source.getFunctionIndustryTag());
        vo.setRemark(source.getRemark());
        vo.setColumns(toPartnerKeywordGroupColumns(source.getColumns()));
        vo.setLlmQuestions(source.getLlmQuestions());
        vo.setEstimatedCoreQuestionCount(source.getEstimatedKeywordCount());
        vo.setSavedCoreQuestionCount(source.getSavedKeywordCountA());
        vo.setCreatedAt(source.getCreatedAt());
        vo.setUpdatedAt(source.getUpdatedAt());
        return vo;
    }

    private PartnerKeywordGroupColumnsVO toPartnerKeywordGroupColumns(KeywordGroupColumnsVO source) {
        if (source == null) {
            return null;
        }
        PartnerKeywordGroupColumnsVO vo = new PartnerKeywordGroupColumnsVO();
        vo.setAreaWords(source.getAreaWords());
        vo.setPrefixWords(source.getPrefixWords());
        vo.setCoreWords(source.getCoreWords());
        vo.setIndustryWords(source.getIndustryWords());
        vo.setSuffixWords(source.getSuffixWords());
        vo.setCoreQuestionWords(source.getCoreWordsA());
        return vo;
    }

    private PartnerKeywordGroupQuestionVO toPartnerKeywordGroupQuestion(KeywordGroupQuestionVO source) {
        PartnerKeywordGroupQuestionVO vo = new PartnerKeywordGroupQuestionVO();
        BeanUtils.copyProperties(source, vo);
        return vo;
    }

    private List<ProjectChannelAllocationVO> filterProjectChannelAllocations(List<ProjectChannelAllocationVO> allocations) {
        if (allocations == null) {
            return List.of();
        }
        return allocations.stream()
                .filter(item -> isPartnerVisibleChannel(item.getChannelCode()))
                .map(this::copyProjectChannelAllocation)
                .toList();
    }

    private ProjectChannelAllocationVO copyProjectChannelAllocation(ProjectChannelAllocationVO source) {
        ProjectChannelAllocationVO vo = new ProjectChannelAllocationVO();
        BeanUtils.copyProperties(source, vo);
        vo.setChannelName(partnerChannelName(source.getChannelCode()));
        return vo;
    }

    private PartnerChannelQuotaVO toPartnerChannelQuota(ProjectChannelAllocationVO source) {
        PartnerChannelQuotaVO vo = new PartnerChannelQuotaVO();
        vo.setChannelCode(source.getChannelCode());
        vo.setChannelName(partnerChannelName(source.getChannelCode()));
        vo.setPeriodType(source.getPeriodType());
        vo.setEnabled(source.isEnabled());
        vo.setQuotaLimit(source.getQuotaLimit());
        vo.setActiveAllocatedCount(source.getActiveAllocatedCount());
        vo.setCurrentProjectAllocatedCount(source.getCurrentProjectAllocatedCount());
        vo.setRemainingCount(source.getRemainingCount());
        vo.setInputMax(source.getInputMax());
        return vo;
    }

    private PartnerChannelQuotaVO toPartnerChannelQuota(PackageChannelQuotaConfig source) {
        PartnerChannelQuotaVO vo = new PartnerChannelQuotaVO();
        vo.setChannelCode(source.getChannelCode());
        vo.setChannelName(partnerChannelName(source.getChannelCode()));
        vo.setPeriodType(source.getPeriodType());
        vo.setEnabled(source.getEnabled());
        vo.setQuotaLimit(source.getQuotaLimit());
        return vo;
    }

    private List<PartnerChannelQuotaVO> parseVisibleSnapshot(String snapshotJson) {
        if (!StringUtils.hasText(snapshotJson) || !JSONUtil.isTypeJSONArray(snapshotJson)) {
            return List.of();
        }
        return JSONUtil.parseArray(snapshotJson).stream()
                .map(item -> JSONUtil.toBean(JSONUtil.parseObj(item), ChannelQuotaSnapshotItem.class))
                .filter(item -> isPartnerVisibleChannel(item.getChannelCode()))
                .map(this::toPartnerChannelQuota)
                .toList();
    }

    private PartnerChannelQuotaVO toPartnerChannelQuota(ChannelQuotaSnapshotItem source) {
        PartnerChannelQuotaVO vo = new PartnerChannelQuotaVO();
        vo.setChannelCode(source.getChannelCode());
        vo.setChannelName(partnerChannelName(source.getChannelCode()));
        vo.setPeriodType(source.getPeriodType());
        vo.setEnabled(source.isEnabled());
        vo.setQuotaLimit(source.getQuotaLimit());
        return vo;
    }

    private List<String> filterChannelCodes(List<String> codes) {
        if (codes == null) {
            return null;
        }
        return codes.stream().filter(this::isPartnerVisibleChannel).toList();
    }

    private boolean isPartnerVisibleChannel(String channelCode) {
        if (!StringUtils.hasText(channelCode)) {
            return false;
        }
        if (ProjectDistributionChannelAllocationService.OFFICIAL_SITE.equals(channelCode)) {
            return true;
        }
        if (!channelCode.startsWith(ArticlePromptChannels.SELF_MEDIA + ":")) {
            return false;
        }
        String platform = channelCode.substring((ArticlePromptChannels.SELF_MEDIA + ":").length());
        return PARTNER_SELF_MEDIA_PLATFORMS.contains(ArticlePromptChannels.canonicalSelfMediaQuotaPlatform(platform));
    }

    private String partnerChannelName(String channelCode) {
        return PARTNER_CHANNEL_NAMES.getOrDefault(channelCode, channelCode);
    }
}
