package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.content.constant.MedicalArticleConstants;
import com.huanjing.geo.module.content.dto.BatchArticleGenerateRequest;
import com.huanjing.geo.module.content.entity.MedicalChannelStyleModule;
import com.huanjing.geo.module.content.entity.MedicalComplianceKernel;
import com.huanjing.geo.module.content.entity.MedicalGenerationHistory;
import com.huanjing.geo.module.content.entity.MedicalTopicAngle;
import com.huanjing.geo.module.content.mapper.MedicalChannelStyleModuleMapper;
import com.huanjing.geo.module.content.mapper.MedicalComplianceKernelMapper;
import com.huanjing.geo.module.content.mapper.MedicalGenerationHistoryMapper;
import com.huanjing.geo.module.content.mapper.MedicalTopicAngleMapper;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.entity.BrandOffering;
import com.huanjing.geo.module.customer.mapper.BrandOfferingMapper;
import com.huanjing.geo.module.project.entity.Project;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class MedicalArticleGenerationService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final String V2_SPECIAL_INDUSTRY_COMPLIANCE_DIRECTION = """
            特殊行业属性只用于控制事实和表达边界，不是文章主题，也不要求每篇都采用资质核验、风险告知或选择指南的写法。
            首段和首个小标题应先承接用户主题中最需要解释的信息，不得默认把资质、合规或风险前置为固定开篇。
            只有主题本身讨论资质、机构核验或相关选择依据时，资质才可以成为主要内容；其他主题中如确有必要提及，应把资质作为支撑当前论点的事实自然融入，不单独设置生硬的前置资质段。
            即使需要引用资质，也只摘取与当前论点直接相关的一项或一句进行概括，不得整段复制主体资质，不得逐项罗列全部诊疗科目、许可范围或编号。
            涉及具体医疗项目、效果、适用性或选择建议时，仅在与当前主题直接相关的位置说明必要的个体差异、风险和专业评估边界；
            仅整理机构或品牌公开信息时，应围绕与主题直接相关的主体信息、公开业务、服务范围和可核验事实展开，不强行扩写无关维度。
            不得作疗效、收益、安全性、时效或持续周期保证，不得制造焦虑、替代专业判断，
            不得使用价格促销、咨询预约或其他直接转化表达。材料不足时应收窄表述，不得补造资质、案例或结果。
            """.trim();

    private final BrandOfferingMapper brandOfferingMapper;
    private final MedicalTopicAngleMapper topicAngleMapper;
    private final MedicalComplianceKernelMapper kernelMapper;
    private final MedicalChannelStyleModuleMapper channelStyleMapper;
    private final MedicalGenerationHistoryMapper historyMapper;
    private final ObjectMapper objectMapper;
    private final SpecialIndustryService specialIndustryService;

    public Optional<String> detectIndustryCode(Brand brand) {
        return specialIndustryService.detectSpecialIndustryCode(brand);
    }

    public String resolveChannelTier(String channelGroupCode, String channelSubCode) {
        String group = trim(channelGroupCode);
        if (ArticlePromptChannels.AGENT_SITE.equals(group)) {
            return MedicalArticleConstants.TIER_OFFICIAL_SITE;
        }
        if (ArticlePromptChannels.INDUSTRY_SITE.equals(group)) {
            return MedicalArticleConstants.TIER_SOURCE_SITE;
        }
        return MedicalArticleConstants.TIER_EDUCATION;
    }

    public Optional<MedicalPromptContext> resolveContext(Project project,
                                                         Brand brand,
                                                         String channelGroupCode,
                                                         String channelSubCode,
                                                         BatchArticleGenerateRequest.TopicConfig topicConfig) {
        Optional<String> industry = detectIndustryCode(brand);
        if (industry.isEmpty()) {
            if (topicConfig != null && StringUtils.hasText(topicConfig.getMedicalIndustryCode())) {
                throw new BizException(400, "品牌未配置特殊行业类型，不能生成特殊行业文章");
            }
            return Optional.empty();
        }
        String industryCode = industry.get();
        if (topicConfig != null && StringUtils.hasText(topicConfig.getMedicalIndustryCode())
                && !industryCode.equals(topicConfig.getMedicalIndustryCode().trim())) {
            throw new BizException(400, "特殊行业与品牌行业不匹配，不能生成特殊行业文章");
        }
        String channelTier = resolveChannelTier(channelGroupCode, channelSubCode);
        List<BrandOffering> enabledOfferings = enabledMedicalOfferings(brand.getId(), industryCode);

        MedicalTopicAngle topicAngle = resolveTopicAngle(project, topicConfig, industryCode, enabledOfferings);
        String skeleton = resolveSkeleton(topicConfig == null ? null : topicConfig.getStructureSkeleton());
        String focus = resolveFocus(topicConfig == null ? null : topicConfig.getFocus(), topicAngle, skeleton);
        SelectionPair pair = avoidRecentPair(project.getId(), brand.getId(), skeleton, focus, topicAngle);
        MedicalComplianceKernel kernel = requireKernel(industryCode, channelTier);
        MedicalChannelStyleModule style = requireStyle(channelGroupCode, channelSubCode, channelTier);

        BrandOffering offering = enabledOfferings.stream()
                .filter(item -> Objects.equals(trim(item.getMedicalCategoryCode()), topicAngle.getCategoryCode()))
                .findFirst()
                .orElse(enabledOfferings.isEmpty() ? null : enabledOfferings.get(0));
        return Optional.of(new MedicalPromptContext(
                industryCode,
                channelTier,
                topicAngle.getCategoryCode(),
                topicAngle.getCategoryName(),
                topicAngle.getId(),
                topicAngle.getTopicAngle(),
                pair.structureSkeleton(),
                pair.focus(),
                kernel.getSystemPrompt(),
                kernel.getBrandExposureLimit(),
                false,
                style.getStylePrompt(),
                Boolean.TRUE.equals(style.getHighRisk()),
                offering == null ? null : trimToNull(offering.getQualificationRef()),
                qualificationSnapshot(brand, industryCode),
                scopeSnapshot(brand, industryCode),
                trimToNull(brand.getPractitionerInfoPublic()),
                trimToNull(brand.getMedicalAdReviewNo())
        ));
    }

    /**
     * V2 keeps the user topic as the primary writing task. A medical topic angle is
     * resolved only when the caller explicitly selected one, and is then treated as
     * optional supporting direction rather than a replacement topic.
     */
    public Optional<MedicalPromptContext> resolveContextV2(Project project,
                                                           Brand brand,
                                                           String channelGroupCode,
                                                           String channelSubCode,
                                                           BatchArticleGenerateRequest.TopicConfig topicConfig) {
        Optional<String> industry = detectIndustryCode(brand);
        if (industry.isEmpty()) {
            if (topicConfig != null && StringUtils.hasText(topicConfig.getMedicalIndustryCode())) {
                throw new BizException(400, "品牌未配置特殊行业类型，不能生成特殊行业文章");
            }
            return Optional.empty();
        }
        String industryCode = industry.get();
        if (topicConfig != null && StringUtils.hasText(topicConfig.getMedicalIndustryCode())
                && !industryCode.equals(topicConfig.getMedicalIndustryCode().trim())) {
            throw new BizException(400, "特殊行业与品牌行业不匹配，不能生成特殊行业文章");
        }

        String channelTier = resolveChannelTier(channelGroupCode, channelSubCode);
        List<BrandOffering> enabledOfferings = enabledMedicalOfferings(brand.getId(), industryCode);
        String requestedCategory = topicConfig == null ? null : trimToNull(topicConfig.getMedicalCategoryCode());

        MedicalTopicAngle topicAngle = resolveExplicitTopicAngle(topicConfig, industryCode, requestedCategory);
        String categoryCode = topicAngle == null ? requestedCategory : trimToNull(topicAngle.getCategoryCode());
        String categoryName = topicAngle == null
                ? (topicConfig == null ? null : trimToNull(topicConfig.getMedicalCategoryName()))
                : trimToNull(topicAngle.getCategoryName());
        String focus = resolveV2Focus(topicConfig, topicAngle);
        MedicalComplianceKernel kernel = findKernel(industryCode, channelTier);
        MedicalChannelStyleModule style = findStyle(channelGroupCode, channelSubCode, channelTier);
        BrandOffering offering = enabledOfferings.stream()
                .filter(item -> categoryCode != null && Objects.equals(trim(item.getMedicalCategoryCode()), categoryCode))
                .findFirst()
                .orElse(null);

        return Optional.of(new MedicalPromptContext(
                industryCode,
                channelTier,
                categoryCode,
                categoryName,
                topicAngle == null ? null : topicAngle.getId(),
                topicAngle == null ? null : trimToNull(topicAngle.getTopicAngle()),
                null,
                focus,
                kernel == null ? null : kernel.getSystemPrompt(),
                kernel == null ? null : kernel.getBrandExposureLimit(),
                false,
                style == null ? null : style.getStylePrompt(),
                style != null && Boolean.TRUE.equals(style.getHighRisk()),
                offering == null ? null : trimToNull(offering.getQualificationRef()),
                qualificationSnapshot(brand, industryCode),
                scopeSnapshot(brand, industryCode),
                trimToNull(brand.getPractitionerInfoPublic()),
                trimToNull(brand.getMedicalAdReviewNo())
        ));
    }

    public BatchArticlePromptBuilder.PromptBuildResult applyMedicalPrompt(
            BatchArticlePromptBuilder.PromptBuildResult prompt,
            MedicalPromptContext context
    ) {
        if (context == null) {
            return prompt;
        }
        String diffBlock = """
                # 特殊行业差异化变量
                - 本篇特殊行业：%s
                - 本篇渠道档位：%s
                - 本篇项目/服务品类：%s
                - 本篇选题角度：%s
                - 本篇结构骨架：%s
                - 本篇内容侧重：%s
                - 项目/服务资质引用：%s
                - 主体资质信息：%s
                - 服务/业务范围：%s
                - 执业/服务人员可公示信息：%s
                - 审查/备案编号：%s
                """.formatted(
                specialIndustryService.industryLabel(context.industryCode()),
                context.channelTier(),
                context.categoryName(),
                context.topicAngle(),
                context.structureSkeleton(),
                context.focus(),
                StringUtils.hasText(context.qualificationRef()) ? context.qualificationRef() : "未提供",
                StringUtils.hasText(context.medicalLicense()) ? context.medicalLicense() : "未提供",
                StringUtils.hasText(context.diagnosisScope()) ? context.diagnosisScope() : "未提供",
                StringUtils.hasText(context.practitionerInfoPublic()) ? context.practitionerInfoPublic() : "未提供",
                StringUtils.hasText(context.medicalAdReviewNo()) ? context.medicalAdReviewNo() : "未提供"
        ).trim();

        String systemPrompt = String.join("\n\n",
                context.complianceKernelPrompt(),
                context.channelStylePrompt(),
                diffBlock,
                prompt.systemPrompt()
        );
        String originalBody = prompt.userPrompt();
        if (originalBody.startsWith(prompt.systemPrompt())) {
            originalBody = originalBody.substring(prompt.systemPrompt().length()).trim();
        }
        String userPrompt = systemPrompt + "\n\n" + originalBody;
        return new BatchArticlePromptBuilder.PromptBuildResult(
                systemPrompt,
                userPrompt,
                prompt.contentAngle(),
                prompt.audiencePerspective(),
                enrichSnapshot(prompt.promptSnapshot(), context),
                enrichSnapshot(prompt.inputSnapshot(), context)
        );
    }

    public BatchArticlePromptBuilder.PromptBuildResult applyMedicalPromptV2(
            BatchArticlePromptBuilder.PromptBuildResult prompt,
            MedicalPromptContext context
    ) {
        if (context == null) {
            return prompt;
        }
        List<String> facts = new ArrayList<>();
        appendFact(facts, "特殊行业", specialIndustryService.industryLabel(context.industryCode()));
        appendFact(facts, "项目或服务品类", context.categoryName());
        appendFact(facts, "辅助关注角度", context.topicAngle());
        appendFact(facts, "本篇关注方向", context.focus());
        appendFact(facts, "可选项目或服务资质（仅摘取主题所需部分）", context.qualificationRef());
        appendFact(facts, "可选主体资质（不得整段复制）", context.medicalLicense());
        appendFact(facts, "可选服务/业务范围（仅按主题需要引用）", context.diagnosisScope());
        appendFact(facts, "可选执业/服务人员公开信息（仅按主题需要引用）", context.practitionerInfoPublic());

        StringBuilder specialRules = new StringBuilder("# 特殊行业内容边界\n")
                .append("以下规则只约束表达方式，不得改变用户主题。标题必须直接回应原主题，不得为了体现行业规则主动加入与主题无关的审核术语或风险标签。\n")
                .append(V2_SPECIAL_INDUSTRY_COMPLIANCE_DIRECTION);
        if (!facts.isEmpty()) {
            specialRules.append("\n以下是备用的特殊行业事实材料，不是必写清单，也不决定文章结构或出现顺序。")
                    .append("只在能够直接解释当前主题时按需引用；未列出的事实不得补写。")
                    .append("未列入正文材料的后台审计字段不得写入文章：\n")
                    .append(String.join("\n", facts));
        }
        String userPrompt = specialRules + "\n\n" + prompt.userPrompt();
        return new BatchArticlePromptBuilder.PromptBuildResult(
                prompt.systemPrompt(),
                userPrompt,
                null,
                null,
                enrichSnapshotV2(prompt.promptSnapshot(), context),
                enrichSnapshotV2(prompt.inputSnapshot(), context)
        );
    }

    public void recordHistory(Project project, Brand brand, MedicalPromptContext context, Long articleId) {
        if (project == null || context == null) {
            return;
        }
        MedicalGenerationHistory history = new MedicalGenerationHistory();
        history.setProjectId(project.getId());
        history.setBrandId(brand == null ? null : brand.getId());
        history.setTopicAngleId(context.topicAngleId());
        history.setStructureSkeleton(context.structureSkeleton());
        history.setFocus(context.focus());
        history.setArticleId(articleId);
        historyMapper.insert(history);
    }

    private List<BrandOffering> enabledMedicalOfferings(Long brandId, String industryCode) {
        if (brandId == null) {
            return List.of();
        }
        return brandOfferingMapper.selectList(new LambdaQueryWrapper<BrandOffering>()
                .eq(BrandOffering::getBrandId, brandId)
                .eq(BrandOffering::getStatus, "active")
                .eq(BrandOffering::getMedicalProjectEnabled, true)
                .eq(BrandOffering::getMedicalIndustryCode, industryCode)
                .isNull(BrandOffering::getDeletedAt)
                .orderByAsc(BrandOffering::getPriority, BrandOffering::getId));
    }

    private MedicalTopicAngle resolveTopicAngle(Project project,
                                                BatchArticleGenerateRequest.TopicConfig topicConfig,
                                                String industryCode,
                                                List<BrandOffering> enabledOfferings) {
        Set<String> allowedCategories = new HashSet<>();
        for (BrandOffering offering : enabledOfferings) {
            if (StringUtils.hasText(offering.getMedicalCategoryCode())) {
                allowedCategories.add(offering.getMedicalCategoryCode().trim());
            }
        }
        String requestedCategory = topicConfig == null ? null : trimToNull(topicConfig.getMedicalCategoryCode());
        if (!allowedCategories.isEmpty() && requestedCategory != null && !allowedCategories.contains(requestedCategory)) {
            throw new BizException(400, "特殊行业品类不属于该品牌已启用资质项目");
        }
        if (topicConfig != null && topicConfig.getTopicAngleId() != null) {
            MedicalTopicAngle angle = topicAngleMapper.selectById(topicConfig.getTopicAngleId());
            if (angle == null || angle.getDeletedAt() != null || !Boolean.TRUE.equals(angle.getEnabled())
                    || !industryCode.equals(angle.getIndustryCode())
                    || (!allowedCategories.isEmpty() && !allowedCategories.contains(angle.getCategoryCode()))
                    || (requestedCategory != null && !requestedCategory.equals(angle.getCategoryCode()))) {
                throw new BizException(400, "特殊行业选题不属于该品牌已启用资质项目");
            }
            return angle;
        }
        LambdaQueryWrapper<MedicalTopicAngle> query = new LambdaQueryWrapper<MedicalTopicAngle>()
                .eq(MedicalTopicAngle::getIndustryCode, industryCode)
                .eq(requestedCategory != null, MedicalTopicAngle::getCategoryCode, requestedCategory)
                .eq(MedicalTopicAngle::getEnabled, true)
                .isNull(MedicalTopicAngle::getDeletedAt)
                .orderByAsc(MedicalTopicAngle::getSortOrder, MedicalTopicAngle::getId);
        if (!allowedCategories.isEmpty()) {
            query.in(MedicalTopicAngle::getCategoryCode, allowedCategories);
        }
        List<MedicalTopicAngle> candidates = topicAngleMapper.selectList(query);
        if (candidates.isEmpty()) {
            throw new BizException(400, "特殊行业选题库没有可用选题，请先维护选题角度");
        }
        Set<Long> recentTopicIds = recentHistory(project.getId()).stream()
                .map(MedicalGenerationHistory::getTopicAngleId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        List<MedicalTopicAngle> filtered = candidates.stream()
                .filter(item -> !recentTopicIds.contains(item.getId()))
                .toList();
        List<MedicalTopicAngle> pool = filtered.isEmpty() ? candidates : filtered;
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    private MedicalTopicAngle resolveExplicitTopicAngle(BatchArticleGenerateRequest.TopicConfig topicConfig,
                                                         String industryCode,
                                                         String requestedCategory) {
        if (topicConfig == null || topicConfig.getTopicAngleId() == null) {
            return null;
        }
        MedicalTopicAngle angle = topicAngleMapper.selectById(topicConfig.getTopicAngleId());
        if (angle == null || angle.getDeletedAt() != null || !Boolean.TRUE.equals(angle.getEnabled())
                || !industryCode.equals(angle.getIndustryCode())
                || (requestedCategory != null && !requestedCategory.equals(angle.getCategoryCode()))) {
            throw new BizException(400, "特殊行业选题不属于当前行业或所选品类");
        }
        return angle;
    }

    private String resolveV2Focus(BatchArticleGenerateRequest.TopicConfig topicConfig, MedicalTopicAngle topicAngle) {
        String requested = topicConfig == null ? null : trimToNull(topicConfig.getFocus());
        if (requested != null && MedicalArticleConstants.FOCUSES.contains(requested)) {
            return requested;
        }
        if (topicAngle == null) {
            return null;
        }
        String recommended = trimToNull(topicAngle.getRecommendedFocus());
        return recommended != null && MedicalArticleConstants.FOCUSES.contains(recommended) ? recommended : null;
    }

    private String resolveSkeleton(String requested) {
        String value = trimToNull(requested);
        if (value != null && MedicalArticleConstants.STRUCTURE_SKELETONS.contains(value)) {
            return value;
        }
        List<String> skeletons = MedicalArticleConstants.STRUCTURE_SKELETONS;
        return skeletons.get(ThreadLocalRandom.current().nextInt(skeletons.size()));
    }

    private String resolveFocus(String requested, MedicalTopicAngle topicAngle, String skeleton) {
        Set<String> allowed = MedicalArticleConstants.ALLOWED_FOCUSES_BY_SKELETON.getOrDefault(
                skeleton,
                Set.copyOf(MedicalArticleConstants.FOCUSES)
        );
        String value = trimToNull(requested);
        if (value != null && allowed.contains(value)) {
            return value;
        }
        String recommended = trimToNull(topicAngle.getRecommendedFocus());
        if (recommended != null && allowed.contains(recommended)) {
            return recommended;
        }
        List<String> pool = new ArrayList<>(allowed);
        pool.sort(Comparator.naturalOrder());
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    private SelectionPair avoidRecentPair(Long projectId, Long brandId, String skeleton, String focus, MedicalTopicAngle topicAngle) {
        List<MedicalGenerationHistory> recent = recentHistory(projectId);
        boolean used = recent.stream().anyMatch(item -> skeleton.equals(item.getStructureSkeleton()) && focus.equals(item.getFocus()));
        if (!used) {
            return new SelectionPair(skeleton, focus);
        }
        List<SelectionPair> pairs = new ArrayList<>();
        for (String candidateSkeleton : MedicalArticleConstants.STRUCTURE_SKELETONS) {
            for (String candidateFocus : MedicalArticleConstants.ALLOWED_FOCUSES_BY_SKELETON.getOrDefault(candidateSkeleton, Set.of())) {
                boolean pairUsed = recent.stream().anyMatch(item ->
                        candidateSkeleton.equals(item.getStructureSkeleton()) && candidateFocus.equals(item.getFocus()));
                if (!pairUsed) {
                    pairs.add(new SelectionPair(candidateSkeleton, candidateFocus));
                }
            }
        }
        if (pairs.isEmpty()) {
            return new SelectionPair(skeleton, focus);
        }
        SelectionPair candidate = pairs.get(ThreadLocalRandom.current().nextInt(pairs.size()));
        String resolvedFocus = resolveFocus(candidate.focus(), topicAngle, candidate.structureSkeleton());
        return new SelectionPair(candidate.structureSkeleton(), resolvedFocus);
    }

    private List<MedicalGenerationHistory> recentHistory(Long projectId) {
        return historyMapper.selectList(new LambdaQueryWrapper<MedicalGenerationHistory>()
                .eq(MedicalGenerationHistory::getProjectId, projectId)
                .orderByDesc(MedicalGenerationHistory::getCreatedAt, MedicalGenerationHistory::getId)
                .last("LIMIT " + MedicalArticleConstants.RECENT_HISTORY_LIMIT));
    }

    private MedicalComplianceKernel requireKernel(String industryCode, String channelTier) {
        MedicalComplianceKernel kernel = findKernel(industryCode, channelTier);
        if (kernel == null) {
            throw new BizException(400, "特殊行业合规内核未配置：" + industryCode + "/" + channelTier);
        }
        return kernel;
    }

    private MedicalComplianceKernel findKernel(String industryCode, String channelTier) {
        return kernelMapper.selectOne(new LambdaQueryWrapper<MedicalComplianceKernel>()
                .eq(MedicalComplianceKernel::getIndustryCode, industryCode)
                .eq(MedicalComplianceKernel::getChannelTier, channelTier)
                .eq(MedicalComplianceKernel::getEnabled, true)
                .orderByDesc(MedicalComplianceKernel::getVersionNo, MedicalComplianceKernel::getId)
                .last("LIMIT 1"));
    }

    private MedicalChannelStyleModule requireStyle(String channelGroupCode, String channelSubCode, String channelTier) {
        MedicalChannelStyleModule style = findStyleCandidate(channelGroupCode, channelSubCode);
        if (style == null) {
            throw new BizException(400, "特殊行业渠道文体模块未配置：" + channelGroupCode + "/" + channelSubCode);
        }
        if (!channelTier.equals(style.getChannelTier())) {
            throw new BizException(400, "特殊行业渠道文体模块档位不匹配：" + channelGroupCode);
        }
        return style;
    }

    private MedicalChannelStyleModule findStyle(String channelGroupCode, String channelSubCode, String channelTier) {
        MedicalChannelStyleModule style = findStyleCandidate(channelGroupCode, channelSubCode);
        return style != null && channelTier.equals(style.getChannelTier()) ? style : null;
    }

    private MedicalChannelStyleModule findStyleCandidate(String channelGroupCode, String channelSubCode) {
        String sub = trimToNull(channelSubCode);
        MedicalChannelStyleModule style = channelStyleMapper.selectOne(new LambdaQueryWrapper<MedicalChannelStyleModule>()
                .eq(MedicalChannelStyleModule::getChannelGroupCode, trim(channelGroupCode))
                .eq(sub != null, MedicalChannelStyleModule::getChannelSubCode, sub)
                .isNull(sub == null, MedicalChannelStyleModule::getChannelSubCode)
                .eq(MedicalChannelStyleModule::getEnabled, true)
                .last("LIMIT 1"));
        if (style == null && sub != null) {
            style = channelStyleMapper.selectOne(new LambdaQueryWrapper<MedicalChannelStyleModule>()
                    .eq(MedicalChannelStyleModule::getChannelGroupCode, trim(channelGroupCode))
                    .isNull(MedicalChannelStyleModule::getChannelSubCode)
                    .eq(MedicalChannelStyleModule::getEnabled, true)
                    .last("LIMIT 1"));
        }
        return style;
    }

    private String qualificationSnapshot(Brand brand, String industryCode) {
        if (brand == null) {
            return null;
        }
        if (specialIndustryService.isMedicalIndustry(industryCode)) {
            return trimToNull(brand.getMedicalLicense());
        }
        String qualification = trimToNull(brand.getBrandQualificationDescription());
        return qualification != null ? qualification : trimToNull(brand.getMedicalLicense());
    }

    private String scopeSnapshot(Brand brand, String industryCode) {
        if (brand == null) {
            return null;
        }
        if (specialIndustryService.isMedicalIndustry(industryCode)) {
            return trimToNull(brand.getDiagnosisScope());
        }
        String scope = trimToNull(brand.getMainBusiness());
        return scope != null ? scope : trimToNull(brand.getDiagnosisScope());
    }

    private String enrichSnapshot(String raw, MedicalPromptContext context) {
        try {
            Map<String, Object> map = StringUtils.hasText(raw)
                    ? objectMapper.readValue(raw, MAP_TYPE)
                    : new java.util.LinkedHashMap<>();
            map.put("medicalIndustryCode", context.industryCode());
            map.put("medicalChannelTier", context.channelTier());
            map.put("medicalCategoryCode", context.categoryCode());
            map.put("medicalCategoryName", context.categoryName());
            map.put("topicAngleId", context.topicAngleId());
            map.put("diffTopicAngle", context.topicAngle());
            map.put("diffStructureSkeleton", context.structureSkeleton());
            map.put("diffFocus", context.focus());
            map.put("medicalLicense", context.medicalLicense());
            map.put("diagnosisScope", context.diagnosisScope());
            map.put("practitionerInfoPublic", context.practitionerInfoPublic());
            map.put("medicalAdReviewNo", context.medicalAdReviewNo());
            map.put("medicalBrandExposureLimit", context.brandExposureLimit());
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException ex) {
            return raw;
        }
    }

    private String enrichSnapshotV2(String raw, MedicalPromptContext context) {
        try {
            Map<String, Object> map = StringUtils.hasText(raw)
                    ? objectMapper.readValue(raw, MAP_TYPE)
                    : new java.util.LinkedHashMap<>();
            map.put("medicalIndustryCode", context.industryCode());
            map.put("medicalChannelTier", context.channelTier());
            map.put("medicalCategoryCode", context.categoryCode());
            map.put("medicalCategoryName", context.categoryName());
            map.put("topicAngleId", context.topicAngleId());
            map.put("topicAngle", context.topicAngle());
            map.put("focus", context.focus());
            map.put("medicalLicense", context.medicalLicense());
            map.put("diagnosisScope", context.diagnosisScope());
            map.put("practitionerInfoPublic", context.practitionerInfoPublic());
            map.put("medicalAdReviewNo", context.medicalAdReviewNo());
            map.put("medicalBrandExposureLimit", context.brandExposureLimit());
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException ex) {
            return raw;
        }
    }

    private void appendFact(List<String> facts, String label, String value) {
        if (StringUtils.hasText(value)) {
            facts.add("- " + label + "：" + value.trim());
        }
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record SelectionPair(String structureSkeleton, String focus) {
    }

    public record MedicalPromptContext(String industryCode,
                                       String channelTier,
                                       String categoryCode,
                                       String categoryName,
                                       Long topicAngleId,
                                       String topicAngle,
                                       String structureSkeleton,
                                       String focus,
                                       String complianceKernelPrompt,
                                       Integer brandExposureLimit,
                                       boolean requireManualPublishReview,
                                       String channelStylePrompt,
                                       boolean highRiskChannel,
                                       String qualificationRef,
                                       String medicalLicense,
                                       String diagnosisScope,
                                       String practitionerInfoPublic,
                                       String medicalAdReviewNo) {
    }
}
