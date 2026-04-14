package com.huanjing.geo.module.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.entity.PackageContentConfig;
import com.huanjing.geo.module.content.mapper.PackageContentConfigMapper;
import com.huanjing.geo.module.project.dto.PackageContentConfigRequest;
import com.huanjing.geo.module.project.dto.PackagePlanCreateRequest;
import com.huanjing.geo.module.project.dto.PackagePlanUpdateRequest;
import com.huanjing.geo.module.project.entity.PackagePlan;
import com.huanjing.geo.module.project.mapper.PackagePlanMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PackagePlanService {

    private static final Pattern PACKAGE_TYPE_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{2,31}$");
    private static final Set<String> INTENSITY_LEVELS = Set.of("L1", "L2", "L3");
    private static final Set<Integer> BIWEEKLY_FREQUENCY_VALUES = Set.of(1, 2);
    private static final Set<String> CONTENT_ARTICLE_TYPES = Set.of("faq", "scenario_content", "industry_article", "stage_advice");
    private static final List<String> DEFAULT_CONTENT_ARTICLE_TYPE_ORDER = List.of("faq", "scenario_content", "industry_article", "stage_advice");

    private final PackagePlanMapper packagePlanMapper;
    private final PackageContentConfigMapper packageContentConfigMapper;
    private final CurrentUserService currentUserService;

    public Page<PackagePlan> page(long current, long size, String keyword, Boolean enabled) {
        currentUserService.ensurePermission("user.manage");
        LambdaQueryWrapper<PackagePlan> wrapper = new LambdaQueryWrapper<PackagePlan>()
                .orderByAsc(PackagePlan::getSortOrder)
                .orderByAsc(PackagePlan::getId);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(PackagePlan::getPackageType, keyword)
                    .or().like(PackagePlan::getPackageName, keyword));
        }
        if (enabled != null) {
            wrapper.eq(PackagePlan::getEnabled, enabled);
        }
        Page<PackagePlan> result = packagePlanMapper.selectPage(new Page<>(current, size), wrapper);
        normalizeLegacyPrices(result.getRecords());
        attachContentConfigs(result.getRecords());
        return result;
    }

    public List<PackagePlan> listEnabled() {
        currentUserService.ensurePermission("project.read");
        List<PackagePlan> plans = packagePlanMapper.selectList(
                new LambdaQueryWrapper<PackagePlan>()
                        .eq(PackagePlan::getEnabled, true)
                        .orderByAsc(PackagePlan::getSortOrder)
                        .orderByAsc(PackagePlan::getId)
        );
        normalizeLegacyPrices(plans);
        attachContentConfigs(plans);
        return plans;
    }

    public PackagePlan create(PackagePlanCreateRequest req) {
        currentUserService.ensurePermission("user.manage");
        validateType(req.getPackageType());
        validateBase(req.getStandardPrice(), req.getServiceMonths(), req.getSortOrder());
        validateBusinessFields(
                req.getQuestionPoolSize(),
                req.getCoreQuestionCount(),
                req.getPlatformP0Count(),
                req.getPlatformP1Count(),
                req.getPlatformP2Count(),
                req.getPerQuestionPlatformCalls(),
                req.getPerQuestionCallsP0(),
                req.getPerQuestionCallsP1(),
                req.getPerQuestionCallsP2(),
                req.getBiweeklyFrequency(),
                req.getMonthlyReportDepth(),
                req.getQuarterlyReportDepth(),
                req.getConsultantIntensity(),
                req.getCompetitorInsightDepth(),
                req.getMediaDistributionIntensity(),
                req.getCommitmentTargetIntensity(),
                req.getTargetMetricType(),
                req.getTargetMetricValue(),
                req.getTargetWindowDays()
        );
        PackagePlan existed = packagePlanMapper.selectOne(
                new LambdaQueryWrapper<PackagePlan>().eq(PackagePlan::getPackageType, req.getPackageType())
        );
        if (existed != null) {
            throw new BizException(400, "package_type already exists");
        }
        PackagePlan plan = new PackagePlan();
        plan.setPackageType(req.getPackageType().trim());
        plan.setPackageName(req.getPackageName().trim());
        plan.setStandardPrice(req.getStandardPrice());
        plan.setServiceMonths(req.getServiceMonths());
        plan.setQuestionPoolSize(req.getQuestionPoolSize());
        plan.setCoreQuestionCount(req.getCoreQuestionCount());
        plan.setPlatformP0Count(req.getPlatformP0Count());
        plan.setPlatformP1Count(req.getPlatformP1Count());
        plan.setPlatformP2Count(req.getPlatformP2Count());
        plan.setPerQuestionPlatformCalls(resolveUnifiedPerQuestionCalls(
                req.getPerQuestionPlatformCalls(),
                req.getPerQuestionCallsP0(),
                req.getPerQuestionCallsP1(),
                req.getPerQuestionCallsP2()
        ));
        plan.setPerQuestionCallsP0(req.getPerQuestionCallsP0());
        plan.setPerQuestionCallsP1(req.getPerQuestionCallsP1());
        plan.setPerQuestionCallsP2(req.getPerQuestionCallsP2());
        plan.setBiweeklyFrequency(req.getBiweeklyFrequency());
        plan.setMonthlyReportDepth(req.getMonthlyReportDepth().trim());
        plan.setQuarterlyReportDepth(req.getQuarterlyReportDepth().trim());
        plan.setConsultantIntensity(req.getConsultantIntensity().trim());
        plan.setCompetitorInsightDepth(req.getCompetitorInsightDepth().trim());
        plan.setMediaDistributionIntensity(req.getMediaDistributionIntensity().trim());
        plan.setCommitmentTargetIntensity(req.getCommitmentTargetIntensity().trim());
        plan.setTargetMetricType(req.getTargetMetricType().trim());
        plan.setTargetMetricValue(req.getTargetMetricValue());
        plan.setTargetWindowDays(req.getTargetWindowDays());
        plan.setEnabled(req.getEnabled());
        plan.setSortOrder(req.getSortOrder());
        plan.setRemark(req.getRemark());
        packagePlanMapper.insert(plan);
        saveContentConfigs(plan.getPackageType(), req.getContentConfigs());
        attachContentConfigs(List.of(plan));
        return plan;
    }

    public PackagePlan update(Long id, PackagePlanUpdateRequest req) {
        currentUserService.ensurePermission("user.manage");
        validateBase(req.getStandardPrice(), req.getServiceMonths(), req.getSortOrder());
        validateBusinessFields(
                req.getQuestionPoolSize(),
                req.getCoreQuestionCount(),
                req.getPlatformP0Count(),
                req.getPlatformP1Count(),
                req.getPlatformP2Count(),
                req.getPerQuestionPlatformCalls(),
                req.getPerQuestionCallsP0(),
                req.getPerQuestionCallsP1(),
                req.getPerQuestionCallsP2(),
                req.getBiweeklyFrequency(),
                req.getMonthlyReportDepth(),
                req.getQuarterlyReportDepth(),
                req.getConsultantIntensity(),
                req.getCompetitorInsightDepth(),
                req.getMediaDistributionIntensity(),
                req.getCommitmentTargetIntensity(),
                req.getTargetMetricType(),
                req.getTargetMetricValue(),
                req.getTargetWindowDays()
        );
        PackagePlan plan = requireById(id);
        plan.setPackageName(req.getPackageName().trim());
        plan.setStandardPrice(req.getStandardPrice());
        plan.setServiceMonths(req.getServiceMonths());
        plan.setQuestionPoolSize(req.getQuestionPoolSize());
        plan.setCoreQuestionCount(req.getCoreQuestionCount());
        plan.setPlatformP0Count(req.getPlatformP0Count());
        plan.setPlatformP1Count(req.getPlatformP1Count());
        plan.setPlatformP2Count(req.getPlatformP2Count());
        plan.setPerQuestionPlatformCalls(resolveUnifiedPerQuestionCalls(
                req.getPerQuestionPlatformCalls(),
                req.getPerQuestionCallsP0(),
                req.getPerQuestionCallsP1(),
                req.getPerQuestionCallsP2()
        ));
        plan.setPerQuestionCallsP0(req.getPerQuestionCallsP0());
        plan.setPerQuestionCallsP1(req.getPerQuestionCallsP1());
        plan.setPerQuestionCallsP2(req.getPerQuestionCallsP2());
        plan.setBiweeklyFrequency(req.getBiweeklyFrequency());
        plan.setMonthlyReportDepth(req.getMonthlyReportDepth().trim());
        plan.setQuarterlyReportDepth(req.getQuarterlyReportDepth().trim());
        plan.setConsultantIntensity(req.getConsultantIntensity().trim());
        plan.setCompetitorInsightDepth(req.getCompetitorInsightDepth().trim());
        plan.setMediaDistributionIntensity(req.getMediaDistributionIntensity().trim());
        plan.setCommitmentTargetIntensity(req.getCommitmentTargetIntensity().trim());
        plan.setTargetMetricType(req.getTargetMetricType().trim());
        plan.setTargetMetricValue(req.getTargetMetricValue());
        plan.setTargetWindowDays(req.getTargetWindowDays());
        plan.setSortOrder(req.getSortOrder());
        plan.setRemark(req.getRemark());
        packagePlanMapper.updateById(plan);
        saveContentConfigs(plan.getPackageType(), req.getContentConfigs());
        attachContentConfigs(List.of(plan));
        return plan;
    }

    public void updateStatus(Long id, Boolean enabled) {
        currentUserService.ensurePermission("user.manage");
        PackagePlan plan = requireById(id);
        plan.setEnabled(enabled);
        packagePlanMapper.updateById(plan);
    }

    public PackagePlan requireEnabledByType(String packageType) {
        PackagePlan plan = packagePlanMapper.selectOne(
                new LambdaQueryWrapper<PackagePlan>()
                        .eq(PackagePlan::getPackageType, packageType)
                        .eq(PackagePlan::getEnabled, true)
        );
        if (plan == null) {
            throw new BizException(400, "Package plan not found or disabled: " + packageType);
        }
        normalizeLegacyPrice(plan);
        return plan;
    }

    public List<PackageContentConfig> listContentConfigs(Long packagePlanId) {
        currentUserService.ensurePermission("user.manage");
        PackagePlan plan = requireById(packagePlanId);
        return findContentConfigsByPackageType(plan.getPackageType());
    }

    public List<PackageContentConfig> saveContentConfigsByPlanId(Long packagePlanId, List<PackageContentConfigRequest> configs) {
        currentUserService.ensurePermission("user.manage");
        PackagePlan plan = requireById(packagePlanId);
        saveContentConfigs(plan.getPackageType(), configs);
        return findContentConfigsByPackageType(plan.getPackageType());
    }

    private void normalizeLegacyPrices(List<PackagePlan> plans) {
        if (plans == null || plans.isEmpty()) {
            return;
        }
        for (PackagePlan plan : plans) {
            normalizeLegacyPrice(plan);
        }
    }

    /**
     * 兼容早期“分”单位遗留数据：
     * package_type 形如 trial_6980，若 standard_price = 698000，则自动归一化为 6980.00（元）。
     */
    private void normalizeLegacyPrice(PackagePlan plan) {
        if (plan == null || plan.getStandardPrice() == null || !StringUtils.hasText(plan.getPackageType())) {
            return;
        }
        Matcher matcher = Pattern.compile(".*_(\\d+)$").matcher(plan.getPackageType().trim());
        if (!matcher.matches()) {
            return;
        }
        BigDecimal expectedYuan = new BigDecimal(matcher.group(1));
        BigDecimal legacyCent = expectedYuan.multiply(new BigDecimal("100"));
        if (plan.getStandardPrice().compareTo(legacyCent) == 0) {
            plan.setStandardPrice(expectedYuan.setScale(2));
        }
    }

    private PackagePlan requireById(Long id) {
        PackagePlan plan = packagePlanMapper.selectById(id);
        if (plan == null) {
            throw new BizException(404, "Package plan not found");
        }
        return plan;
    }

    private void validateType(String packageType) {
        if (!StringUtils.hasText(packageType) || !PACKAGE_TYPE_PATTERN.matcher(packageType.trim()).matches()) {
            throw new BizException(400, "Invalid package_type");
        }
    }

    private void validateBase(BigDecimal standardPrice, Integer serviceMonths, Integer sortOrder) {
        if (standardPrice == null || standardPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(400, "standard_price must be positive");
        }
        if (serviceMonths == null || serviceMonths <= 0) {
            throw new BizException(400, "service_months must be positive");
        }
        if (sortOrder == null) {
            throw new BizException(400, "sort_order is required");
        }
    }

    private void validateContentConfigs(List<PackageContentConfigRequest> configs) {
        if (configs == null || configs.isEmpty()) {
            return;
        }
        Map<String, Integer> articleTypeCount = new LinkedHashMap<>();
        for (PackageContentConfigRequest cfg : configs) {
            if (cfg == null || !StringUtils.hasText(cfg.getArticleType())) {
                throw new BizException(400, "content_configs.article_type is required");
            }
            String articleType = cfg.getArticleType().trim().toLowerCase(Locale.ROOT);
            if (!CONTENT_ARTICLE_TYPES.contains(articleType)) {
                throw new BizException(400, "Unsupported article_type: " + cfg.getArticleType());
            }
            articleTypeCount.put(articleType, articleTypeCount.getOrDefault(articleType, 0) + 1);
            if (cfg.getArticlesPerBatch() == null || cfg.getArticlesPerBatch() <= 0) {
                throw new BizException(400, "articles_per_batch must be positive");
            }
            if (cfg.getQuestionsPerArticle() == null || cfg.getQuestionsPerArticle() <= 0) {
                throw new BizException(400, "questions_per_article must be positive");
            }
            if (cfg.getIsActive() == null) {
                throw new BizException(400, "is_active is required");
            }
        }
        for (Map.Entry<String, Integer> entry : articleTypeCount.entrySet()) {
            if (entry.getValue() > 1) {
                throw new BizException(400, "Duplicate article_type in content configs: " + entry.getKey());
            }
        }
    }

    private void saveContentConfigs(String packageType, List<PackageContentConfigRequest> configs) {
        List<PackageContentConfigRequest> normalizedInput = normalizeContentConfigInput(configs);
        validateContentConfigs(normalizedInput);

        packageContentConfigMapper.delete(
                new LambdaQueryWrapper<PackageContentConfig>()
                        .eq(PackageContentConfig::getPackageType, packageType)
        );

        for (PackageContentConfigRequest req : normalizedInput) {
            PackageContentConfig entity = new PackageContentConfig();
            entity.setPackageType(packageType);
            entity.setArticleType(req.getArticleType().trim().toLowerCase(Locale.ROOT));
            entity.setArticlesPerBatch(req.getArticlesPerBatch());
            entity.setQuestionsPerArticle(req.getQuestionsPerArticle());
            entity.setIsActive(req.getIsActive());
            packageContentConfigMapper.insert(entity);
        }
    }

    private List<PackageContentConfigRequest> normalizeContentConfigInput(List<PackageContentConfigRequest> configs) {
        if (configs != null && !configs.isEmpty()) {
            return configs;
        }
        List<PackageContentConfigRequest> defaults = new ArrayList<>();
        for (String articleType : DEFAULT_CONTENT_ARTICLE_TYPE_ORDER) {
            PackageContentConfigRequest req = new PackageContentConfigRequest();
            req.setArticleType(articleType);
            req.setArticlesPerBatch(1);
            req.setQuestionsPerArticle(3);
            req.setIsActive(true);
            defaults.add(req);
        }
        return defaults;
    }

    private List<PackageContentConfig> findContentConfigsByPackageType(String packageType) {
        List<PackageContentConfig> list = packageContentConfigMapper.selectList(
                new LambdaQueryWrapper<PackageContentConfig>()
                        .eq(PackageContentConfig::getPackageType, packageType)
                        .orderByAsc(PackageContentConfig::getArticleType, PackageContentConfig::getId)
        );
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        return list;
    }

    private void attachContentConfigs(List<PackagePlan> plans) {
        if (plans == null || plans.isEmpty()) {
            return;
        }
        List<String> packageTypes = plans.stream()
                .map(PackagePlan::getPackageType)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        if (packageTypes.isEmpty()) {
            return;
        }
        List<PackageContentConfig> allConfigs = packageContentConfigMapper.selectList(
                new LambdaQueryWrapper<PackageContentConfig>()
                        .in(PackageContentConfig::getPackageType, packageTypes)
                        .orderByAsc(PackageContentConfig::getPackageType, PackageContentConfig::getArticleType, PackageContentConfig::getId)
        );
        Map<String, List<PackageContentConfig>> grouped = allConfigs.stream()
                .collect(Collectors.groupingBy(PackageContentConfig::getPackageType, LinkedHashMap::new, Collectors.toList()));
        for (PackagePlan plan : plans) {
            plan.setContentConfigs(grouped.getOrDefault(plan.getPackageType(), List.of()));
        }
    }

    private void validateBusinessFields(
            Integer questionPoolSize,
            Integer coreQuestionCount,
            Integer platformP0Count,
            Integer platformP1Count,
            Integer platformP2Count,
            Integer perQuestionPlatformCalls,
            Integer perQuestionCallsP0,
            Integer perQuestionCallsP1,
            Integer perQuestionCallsP2,
            Integer biweeklyFrequency,
            String monthlyReportDepth,
            String quarterlyReportDepth,
            String consultantIntensity,
            String competitorInsightDepth,
            String mediaDistributionIntensity,
            String commitmentTargetIntensity,
            String targetMetricType,
            BigDecimal targetMetricValue,
            Integer targetWindowDays
    ) {
        if (questionPoolSize == null || questionPoolSize <= 0) {
            throw new BizException(400, "question_pool_size must be positive");
        }
        if (coreQuestionCount == null || coreQuestionCount <= 0) {
            throw new BizException(400, "core_question_count must be positive");
        }
        if (coreQuestionCount > questionPoolSize) {
            throw new BizException(400, "core_question_count cannot exceed question_pool_size");
        }
        if (platformP0Count == null || platformP0Count < 0
                || platformP1Count == null || platformP1Count < 0
                || platformP2Count == null || platformP2Count < 0) {
            throw new BizException(400, "platform P0/P1/P2 counts must be >= 0");
        }
        if (perQuestionPlatformCalls == null || perQuestionPlatformCalls <= 0) {
            throw new BizException(400, "per_question_platform_calls must be positive");
        }
        if (perQuestionCallsP0 == null || perQuestionCallsP0 <= 0
                || perQuestionCallsP1 == null || perQuestionCallsP1 <= 0
                || perQuestionCallsP2 == null || perQuestionCallsP2 <= 0) {
            throw new BizException(400, "per_question_calls_p0/p1/p2 must be positive");
        }
        if (biweeklyFrequency == null || !BIWEEKLY_FREQUENCY_VALUES.contains(biweeklyFrequency)) {
            throw new BizException(400, "biweekly_frequency must be 1 or 2");
        }
        validateIntensityLevel(monthlyReportDepth, "monthly_report_depth");
        validateIntensityLevel(quarterlyReportDepth, "quarterly_report_depth");
        validateIntensityLevel(consultantIntensity, "consultant_intensity");
        validateIntensityLevel(competitorInsightDepth, "competitor_insight_depth");
        validateIntensityLevel(mediaDistributionIntensity, "media_distribution_intensity");
        validateIntensityLevel(commitmentTargetIntensity, "commitment_target_intensity");
        if (!StringUtils.hasText(targetMetricType)) {
            throw new BizException(400, "target_metric_type is required");
        }
        if (targetMetricValue == null || targetMetricValue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(400, "target_metric_value must be positive");
        }
        if (targetWindowDays == null || targetWindowDays <= 0) {
            throw new BizException(400, "target_window_days must be positive");
        }
    }

    private void validateIntensityLevel(String level, String fieldName) {
        if (!StringUtils.hasText(level) || !INTENSITY_LEVELS.contains(level.trim())) {
            throw new BizException(400, "Invalid " + fieldName);
        }
    }

    private Integer resolveUnifiedPerQuestionCalls(
            Integer legacyPerQuestionPlatformCalls,
            Integer perQuestionCallsP0,
            Integer perQuestionCallsP1,
            Integer perQuestionCallsP2
    ) {
        if (legacyPerQuestionPlatformCalls != null && legacyPerQuestionPlatformCalls > 0) {
            return legacyPerQuestionPlatformCalls;
        }
        return Math.max(Math.max(perQuestionCallsP0, perQuestionCallsP1), perQuestionCallsP2);
    }
}
