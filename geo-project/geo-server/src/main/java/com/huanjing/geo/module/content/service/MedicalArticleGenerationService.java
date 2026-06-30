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

    private final BrandOfferingMapper brandOfferingMapper;
    private final MedicalTopicAngleMapper topicAngleMapper;
    private final MedicalComplianceKernelMapper kernelMapper;
    private final MedicalChannelStyleModuleMapper channelStyleMapper;
    private final MedicalGenerationHistoryMapper historyMapper;
    private final ObjectMapper objectMapper;
    private final SpecialIndustryService specialIndustryService;

    public Optional<String> detectIndustryCode(Brand brand) {
        return specialIndustryService.detectMedicalIndustryCode(brand);
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
                throw new BizException(400, "品牌未配置行业合规类型，不能生成医疗文章");
            }
            return Optional.empty();
        }
        String industryCode = industry.get();
        if (topicConfig != null && StringUtils.hasText(topicConfig.getMedicalIndustryCode())
                && !industryCode.equals(topicConfig.getMedicalIndustryCode().trim())) {
            throw new BizException(400, "医疗行业与品牌行业不匹配，不能生成医疗文章");
        }
        String channelTier = resolveChannelTier(channelGroupCode, channelSubCode);
        List<BrandOffering> enabledOfferings = enabledMedicalOfferings(brand.getId(), industryCode);
        if (enabledOfferings.isEmpty()) {
            throw new BizException(400, "特殊行业项目未配置已启用的资质项目，不能生成特殊行业文章");
        }

        MedicalTopicAngle topicAngle = resolveTopicAngle(project, topicConfig, industryCode, enabledOfferings);
        String skeleton = resolveSkeleton(topicConfig == null ? null : topicConfig.getStructureSkeleton());
        String focus = resolveFocus(topicConfig == null ? null : topicConfig.getFocus(), topicAngle, skeleton);
        SelectionPair pair = avoidRecentPair(project.getId(), brand.getId(), skeleton, focus, topicAngle);
        MedicalComplianceKernel kernel = requireKernel(industryCode, channelTier);
        MedicalChannelStyleModule style = requireStyle(channelGroupCode, channelSubCode, channelTier);

        BrandOffering offering = enabledOfferings.stream()
                .filter(item -> Objects.equals(trim(item.getMedicalCategoryCode()), topicAngle.getCategoryCode()))
                .findFirst()
                .orElse(enabledOfferings.get(0));
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
                Boolean.TRUE.equals(kernel.getRequireManualPublishReview()),
                style.getStylePrompt(),
                Boolean.TRUE.equals(style.getHighRisk()),
                trimToNull(offering.getQualificationRef()),
                trimToNull(brand.getMedicalLicense()),
                trimToNull(brand.getDiagnosisScope()),
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
                # 医疗差异化变量
                - 本篇医疗行业：%s
                - 本篇渠道档位：%s
                - 本篇项目品类：%s
                - 本篇选题角度：%s
                - 本篇结构骨架：%s
                - 本篇科普侧重：%s
                - 项目资质引用：%s
                - 医疗机构执业许可：%s
                - 诊疗科目范围：%s
                - 医疗广告审查证明编号：%s
                """.formatted(
                context.industryCode(),
                context.channelTier(),
                context.categoryName(),
                context.topicAngle(),
                context.structureSkeleton(),
                context.focus(),
                StringUtils.hasText(context.qualificationRef()) ? context.qualificationRef() : "未提供",
                StringUtils.hasText(context.medicalLicense()) ? context.medicalLicense() : "未提供",
                StringUtils.hasText(context.diagnosisScope()) ? context.diagnosisScope() : "未提供",
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
        if (allowedCategories.isEmpty()) {
            throw new BizException(400, "特殊行业项目未配置项目品类，不能生成特殊行业文章");
        }
        String requestedCategory = topicConfig == null ? null : trimToNull(topicConfig.getMedicalCategoryCode());
        if (requestedCategory != null && !allowedCategories.contains(requestedCategory)) {
            throw new BizException(400, "特殊行业品类不属于该品牌已启用资质项目");
        }
        if (topicConfig != null && topicConfig.getTopicAngleId() != null) {
            MedicalTopicAngle angle = topicAngleMapper.selectById(topicConfig.getTopicAngleId());
            if (angle == null || angle.getDeletedAt() != null || !Boolean.TRUE.equals(angle.getEnabled())
                    || !industryCode.equals(angle.getIndustryCode()) || !allowedCategories.contains(angle.getCategoryCode())) {
                throw new BizException(400, "医疗选题不属于该品牌已启用资质项目");
            }
            return angle;
        }
        List<MedicalTopicAngle> candidates = topicAngleMapper.selectList(new LambdaQueryWrapper<MedicalTopicAngle>()
                .eq(MedicalTopicAngle::getIndustryCode, industryCode)
                .in(MedicalTopicAngle::getCategoryCode, allowedCategories)
                .eq(requestedCategory != null, MedicalTopicAngle::getCategoryCode, requestedCategory)
                .eq(MedicalTopicAngle::getEnabled, true)
                .isNull(MedicalTopicAngle::getDeletedAt)
                .orderByAsc(MedicalTopicAngle::getSortOrder, MedicalTopicAngle::getId));
        if (candidates.isEmpty()) {
            throw new BizException(400, "医疗选题库没有可用选题，请先维护 topic angle");
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
        MedicalComplianceKernel kernel = kernelMapper.selectOne(new LambdaQueryWrapper<MedicalComplianceKernel>()
                .eq(MedicalComplianceKernel::getIndustryCode, industryCode)
                .eq(MedicalComplianceKernel::getChannelTier, channelTier)
                .eq(MedicalComplianceKernel::getEnabled, true)
                .orderByDesc(MedicalComplianceKernel::getVersionNo, MedicalComplianceKernel::getId)
                .last("LIMIT 1"));
        if (kernel == null) {
            throw new BizException(400, "医疗合规内核未配置：" + industryCode + "/" + channelTier);
        }
        return kernel;
    }

    private MedicalChannelStyleModule requireStyle(String channelGroupCode, String channelSubCode, String channelTier) {
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
        if (style == null) {
            throw new BizException(400, "医疗渠道文体模块未配置：" + channelGroupCode + "/" + channelSubCode);
        }
        if (!channelTier.equals(style.getChannelTier())) {
            throw new BizException(400, "医疗渠道文体模块档位不匹配：" + channelGroupCode);
        }
        return style;
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
            map.put("medicalAdReviewNo", context.medicalAdReviewNo());
            map.put("medicalBrandExposureLimit", context.brandExposureLimit());
            map.put("medicalRequireManualPublishReview", context.requireManualPublishReview());
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException ex) {
            return raw;
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
                                       String medicalAdReviewNo) {
    }
}
