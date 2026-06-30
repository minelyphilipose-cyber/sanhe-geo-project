package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.content.constant.MedicalArticleConstants;
import com.huanjing.geo.module.content.entity.MedicalChannelStyleModule;
import com.huanjing.geo.module.content.entity.MedicalComplianceKernel;
import com.huanjing.geo.module.content.entity.MedicalTopicAngle;
import com.huanjing.geo.module.content.mapper.MedicalChannelStyleModuleMapper;
import com.huanjing.geo.module.content.mapper.MedicalComplianceKernelMapper;
import com.huanjing.geo.module.content.mapper.MedicalTopicAngleMapper;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.entity.BrandOffering;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.customer.mapper.BrandOfferingMapper;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.entity.ProjectChannelAllocation;
import com.huanjing.geo.module.project.mapper.ProjectChannelAllocationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SpecialIndustryReadinessService {

    private final BrandMapper brandMapper;
    private final BrandOfferingMapper brandOfferingMapper;
    private final MedicalTopicAngleMapper topicAngleMapper;
    private final MedicalComplianceKernelMapper kernelMapper;
    private final MedicalChannelStyleModuleMapper channelStyleMapper;
    private final ProjectChannelAllocationMapper channelAllocationMapper;
    private final SpecialIndustryService specialIndustryService;

    public Optional<String> detectMedicalIndustryCode(Brand brand) {
        return specialIndustryService.detectMedicalIndustryCode(brand);
    }

    public void validateProjectActivation(Project project) {
        if (project == null || project.getBrandId() == null) {
            return;
        }
        Brand brand = brandMapper.selectById(project.getBrandId());
        Optional<String> industry = detectMedicalIndustryCode(brand);
        if (industry.isEmpty()) {
            return;
        }
        List<String> issues = activationIssues(project, brand, industry.get());
        if (!issues.isEmpty()) {
            throw new BizException(400, "SPECIAL_INDUSTRY_READINESS_FAILED: " + String.join("；", issues));
        }
    }

    public String questionGenerationGuidance(Brand brand) {
        Optional<String> industry = detectMedicalIndustryCode(brand);
        if (industry.isEmpty()) {
            return "";
        }
        String industryName = specialIndustryService.industryLabel(industry.get());
        return """

                ## 特殊行业合规约束
                当前品牌属于%s强监管行业。生成问题池时必须提前规避医疗广告和医疗建议风险：
                1. 不生成承诺疗效、保证结果、暗示治愈、立即见效、永久解决、零风险的问题。
                2. 不生成诱导用户直接购买治疗项目、药械产品或手术方案的问题。
                3. A 类可以覆盖选择入口，但应表达为机构资质、医生评估、风险边界、适用人群、术前沟通、术后护理等理性决策问题。
                4. B 类优先覆盖方案差异、禁忌人群、恢复周期、风险提示、费用构成、面诊评估、资质判断。
                5. C 类优先覆盖概念科普、流程常识、注意事项、护理常识，不出现强成交词和具体治疗承诺。
                6. 问题中避免“包好、根治、痊愈、立竿见影、一次见效、无痛无风险、永久保持、真实案例、亲测”等医疗高风险表达。
                7. 遇到症状、治疗或手术相关问题，使用“是否适合需要医生评估”“有哪些风险和注意事项”这类安全问法。
                """.formatted(industryName);
    }

    private List<String> activationIssues(Project project, Brand brand, String industryCode) {
        List<String> issues = new ArrayList<>();
        if (!industryCode.equals(trimToNull(brand.getComplianceIndustryCode()))) {
            issues.add("品牌需明确配置合规行业类型");
        }
        requireText(issues, brand.getMedicalLicense(), "缺少医疗机构执业许可信息");
        requireText(issues, brand.getDiagnosisScope(), "缺少诊疗科目范围");

        List<ProjectChannelAllocation> allocations = channelAllocationMapper.selectList(
                new LambdaQueryWrapper<ProjectChannelAllocation>()
                        .eq(ProjectChannelAllocation::getProjectId, project.getId())
                        .gt(ProjectChannelAllocation::getAllocatedCount, 0)
        );
        if (allocations.stream().anyMatch(row -> "official_site".equals(row.getChannelCode()))
                && !StringUtils.hasText(brand.getMedicalAdReviewNo())) {
            issues.add("官网档缺少医疗广告审查证明编号");
        }

        List<BrandOffering> offerings = activeMedicalOfferings(brand.getId(), industryCode);
        if (offerings.isEmpty()) {
            issues.add("缺少已启用的医疗资质项目");
            return issues;
        }
        Set<String> categoryCodes = new LinkedHashSet<>();
        for (BrandOffering offering : offerings) {
            if (!StringUtils.hasText(offering.getMedicalCategoryCode())) {
                issues.add("医疗资质项目缺少品类编码：" + defaultText(offering.getOfferingName(), String.valueOf(offering.getId())));
                continue;
            }
            categoryCodes.add(offering.getMedicalCategoryCode().trim());
            if (!StringUtils.hasText(offering.getQualificationRef())) {
                issues.add("医疗资质项目缺少资质引用：" + defaultText(offering.getOfferingName(), offering.getMedicalCategoryCode()));
            }
        }
        if (!categoryCodes.isEmpty() && countTopicAngles(industryCode, categoryCodes) <= 0) {
            issues.add("医疗选题库缺少可用选题角度");
        }

        for (String tier : requiredTiers(allocations)) {
            if (!hasKernel(industryCode, tier)) {
                issues.add("缺少医疗合规内核：" + industryCode + "/" + tier);
            }
        }
        for (ProjectChannelAllocation allocation : allocations) {
            ChannelRef channel = parseChannel(allocation.getChannelCode());
            if (channel == null) {
                continue;
            }
            String tier = resolveChannelTier(channel.groupCode());
            if (!hasChannelStyle(channel.groupCode(), channel.subCode(), tier)) {
                issues.add("缺少医疗渠道文体模块：" + allocation.getChannelCode());
            }
        }
        return issues.stream().distinct().toList();
    }

    private List<BrandOffering> activeMedicalOfferings(Long brandId, String industryCode) {
        return brandOfferingMapper.selectList(new LambdaQueryWrapper<BrandOffering>()
                .eq(BrandOffering::getBrandId, brandId)
                .eq(BrandOffering::getStatus, "active")
                .eq(BrandOffering::getMedicalProjectEnabled, true)
                .eq(BrandOffering::getMedicalIndustryCode, industryCode)
                .isNull(BrandOffering::getDeletedAt)
                .orderByAsc(BrandOffering::getPriority, BrandOffering::getId));
    }

    private long countTopicAngles(String industryCode, Set<String> categoryCodes) {
        return topicAngleMapper.selectCount(new LambdaQueryWrapper<MedicalTopicAngle>()
                .eq(MedicalTopicAngle::getIndustryCode, industryCode)
                .in(MedicalTopicAngle::getCategoryCode, categoryCodes)
                .eq(MedicalTopicAngle::getEnabled, true)
                .isNull(MedicalTopicAngle::getDeletedAt));
    }

    private Set<String> requiredTiers(List<ProjectChannelAllocation> allocations) {
        Set<String> tiers = new LinkedHashSet<>();
        for (ProjectChannelAllocation allocation : allocations) {
            ChannelRef channel = parseChannel(allocation.getChannelCode());
            if (channel != null) {
                tiers.add(resolveChannelTier(channel.groupCode()));
            }
        }
        return tiers;
    }

    private boolean hasKernel(String industryCode, String tier) {
        Long count = kernelMapper.selectCount(new LambdaQueryWrapper<MedicalComplianceKernel>()
                .eq(MedicalComplianceKernel::getIndustryCode, industryCode)
                .eq(MedicalComplianceKernel::getChannelTier, tier)
                .eq(MedicalComplianceKernel::getEnabled, true));
        return count != null && count > 0;
    }

    private boolean hasChannelStyle(String groupCode, String subCode, String tier) {
        LambdaQueryWrapper<MedicalChannelStyleModule> exact = new LambdaQueryWrapper<MedicalChannelStyleModule>()
                .eq(MedicalChannelStyleModule::getChannelGroupCode, groupCode)
                .eq(StringUtils.hasText(subCode), MedicalChannelStyleModule::getChannelSubCode, subCode)
                .isNull(!StringUtils.hasText(subCode), MedicalChannelStyleModule::getChannelSubCode)
                .eq(MedicalChannelStyleModule::getChannelTier, tier)
                .eq(MedicalChannelStyleModule::getEnabled, true);
        Long exactCount = channelStyleMapper.selectCount(exact);
        if (exactCount != null && exactCount > 0) {
            return true;
        }
        if (!StringUtils.hasText(subCode)) {
            return false;
        }
        Long fallbackCount = channelStyleMapper.selectCount(new LambdaQueryWrapper<MedicalChannelStyleModule>()
                .eq(MedicalChannelStyleModule::getChannelGroupCode, groupCode)
                .isNull(MedicalChannelStyleModule::getChannelSubCode)
                .eq(MedicalChannelStyleModule::getChannelTier, tier)
                .eq(MedicalChannelStyleModule::getEnabled, true));
        return fallbackCount != null && fallbackCount > 0;
    }

    private String resolveChannelTier(String channelGroupCode) {
        if (ArticlePromptChannels.AGENT_SITE.equals(channelGroupCode) || "official_site".equals(channelGroupCode)) {
            return MedicalArticleConstants.TIER_OFFICIAL_SITE;
        }
        if (ArticlePromptChannels.INDUSTRY_SITE.equals(channelGroupCode) || "industry_site".equals(channelGroupCode)) {
            return MedicalArticleConstants.TIER_SOURCE_SITE;
        }
        return MedicalArticleConstants.TIER_EDUCATION;
    }

    private ChannelRef parseChannel(String channelCode) {
        String code = trimToNull(channelCode);
        if (code == null) {
            return null;
        }
        if ("official_site".equals(code)) {
            return new ChannelRef(ArticlePromptChannels.AGENT_SITE, null);
        }
        if ("industry_site".equals(code)) {
            return new ChannelRef(ArticlePromptChannels.INDUSTRY_SITE, null);
        }
        int separator = code.indexOf(':');
        if (separator > 0) {
            return new ChannelRef(code.substring(0, separator), code.substring(separator + 1));
        }
        return new ChannelRef(code, null);
    }

    private void requireText(List<String> issues, String value, String message) {
        if (!StringUtils.hasText(value)) {
            issues.add(message);
        }
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record ChannelRef(String groupCode, String subCode) {
    }
}
