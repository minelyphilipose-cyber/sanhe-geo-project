package com.huanjing.geo.module.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.project.dto.PackageChannelQuotaConfigRequest;
import com.huanjing.geo.module.project.dto.PackagePlanCreateRequest;
import com.huanjing.geo.module.project.dto.PackagePlanUpdateRequest;
import com.huanjing.geo.module.project.entity.PackageChannelQuotaConfig;
import com.huanjing.geo.module.project.entity.PackagePlan;
import com.huanjing.geo.module.project.mapper.PackageChannelQuotaConfigMapper;
import com.huanjing.geo.module.project.mapper.PackagePlanMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
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
    private static final Set<String> CHANNEL_CODES = Set.of("official_site", "industry_site", "self_media", "authority_media");
    private static final Set<String> PERIOD_TYPES = Set.of("day", "week", "month", "total");
    private static final Map<String, String> DEFAULT_PERIOD_BY_CHANNEL = Map.of(
            "official_site", "week",
            "industry_site", "week",
            "self_media", "week",
            "authority_media", "total"
    );

    private final PackagePlanMapper packagePlanMapper;
    private final PackageChannelQuotaConfigMapper packageChannelQuotaConfigMapper;
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
        attachChannelQuotaConfigs(result.getRecords());
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
        attachChannelQuotaConfigs(plans);
        return plans;
    }

    public PackagePlan create(PackagePlanCreateRequest req) {
        currentUserService.ensurePermission("user.manage");
        validateType(req.getPackageType());
        validateBase(req.getStandardPrice(), req.getServiceMonths(), req.getSortOrder());
        validateBusinessFields(
                req.getKeywordGroupLimit(),
                req.getKeywordGroupLimitA(),
                req.getKeywordGroupLimitB(),
                req.getKeywordGroupLimitC(),
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
        plan.setKeywordGroupLimit(req.getKeywordGroupLimit());
        plan.setKeywordGroupLimitA(req.getKeywordGroupLimitA());
        plan.setKeywordGroupLimitB(req.getKeywordGroupLimitB());
        plan.setKeywordGroupLimitC(req.getKeywordGroupLimitC());
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
        saveChannelQuotaConfigs(plan.getId(), req.getChannelQuotaConfigs());
        attachChannelQuotaConfigs(List.of(plan));
        return plan;
    }

    public PackagePlan update(Long id, PackagePlanUpdateRequest req) {
        currentUserService.ensurePermission("user.manage");
        validateBase(req.getStandardPrice(), req.getServiceMonths(), req.getSortOrder());
        validateBusinessFields(
                req.getKeywordGroupLimit(),
                req.getKeywordGroupLimitA(),
                req.getKeywordGroupLimitB(),
                req.getKeywordGroupLimitC(),
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
        plan.setKeywordGroupLimit(req.getKeywordGroupLimit());
        plan.setKeywordGroupLimitA(req.getKeywordGroupLimitA());
        plan.setKeywordGroupLimitB(req.getKeywordGroupLimitB());
        plan.setKeywordGroupLimitC(req.getKeywordGroupLimitC());
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
        saveChannelQuotaConfigs(plan.getId(), req.getChannelQuotaConfigs());
        attachChannelQuotaConfigs(List.of(plan));
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

    public List<PackageChannelQuotaConfig> listChannelQuotaConfigs(Long packagePlanId) {
        currentUserService.ensurePermission("user.manage");
        PackagePlan plan = requireById(packagePlanId);
        return findChannelQuotaConfigs(plan.getId());
    }

    public List<PackageChannelQuotaConfig> saveChannelQuotaConfigsByPlanId(Long packagePlanId, List<PackageChannelQuotaConfigRequest> configs) {
        currentUserService.ensurePermission("user.manage");
        PackagePlan plan = requireById(packagePlanId);
        saveChannelQuotaConfigs(plan.getId(), configs);
        return findChannelQuotaConfigs(plan.getId());
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

    private void saveChannelQuotaConfigs(Long packagePlanId, List<PackageChannelQuotaConfigRequest> configs) {
        List<PackageChannelQuotaConfigRequest> normalizedInput = normalizeChannelQuotaInput(configs);
        validateChannelQuotaConfigs(normalizedInput);

        packageChannelQuotaConfigMapper.delete(
                new LambdaQueryWrapper<PackageChannelQuotaConfig>()
                        .eq(PackageChannelQuotaConfig::getPackagePlanId, packagePlanId)
        );

        for (PackageChannelQuotaConfigRequest req : normalizedInput) {
            PackageChannelQuotaConfig entity = new PackageChannelQuotaConfig();
            entity.setPackagePlanId(packagePlanId);
            entity.setChannelCode(normalizeChannel(req.getChannelCode()));
            entity.setPeriodType(normalizePeriod(req.getPeriodType()));
            entity.setQuotaLimit(req.getQuotaLimit());
            entity.setEnabled(req.getEnabled());
            packageChannelQuotaConfigMapper.insert(entity);
        }
    }

    private List<PackageChannelQuotaConfigRequest> normalizeChannelQuotaInput(List<PackageChannelQuotaConfigRequest> configs) {
        if (configs != null && !configs.isEmpty()) {
            return configs;
        }
        return DEFAULT_PERIOD_BY_CHANNEL.entrySet().stream().map(entry -> {
            PackageChannelQuotaConfigRequest req = new PackageChannelQuotaConfigRequest();
            req.setChannelCode(entry.getKey());
            req.setPeriodType(entry.getValue());
            req.setQuotaLimit("authority_media".equals(entry.getKey()) ? 0 : 1);
            req.setEnabled(true);
            return req;
        }).collect(Collectors.toList());
    }

    private void validateChannelQuotaConfigs(List<PackageChannelQuotaConfigRequest> configs) {
        Map<String, Integer> seen = new LinkedHashMap<>();
        for (PackageChannelQuotaConfigRequest cfg : configs) {
            String channel = normalizeChannel(cfg.getChannelCode());
            String period = normalizePeriod(cfg.getPeriodType());
            if (!CHANNEL_CODES.contains(channel)) {
                throw new BizException(400, "Unsupported channel_code: " + cfg.getChannelCode());
            }
            if (!PERIOD_TYPES.contains(period)) {
                throw new BizException(400, "Unsupported period_type: " + cfg.getPeriodType());
            }
            if ("authority_media".equals(channel) && !"total".equals(period)) {
                throw new BizException(400, "authority_media period_type must be total");
            }
            if (!"authority_media".equals(channel) && "total".equals(period)) {
                throw new BizException(400, channel + " period_type cannot be total");
            }
            if (cfg.getQuotaLimit() == null || cfg.getQuotaLimit() < 0) {
                throw new BizException(400, "quota_limit must be >= 0");
            }
            if (cfg.getEnabled() == null) {
                throw new BizException(400, "channel quota enabled is required");
            }
            String key = channel + ":" + period;
            seen.put(key, seen.getOrDefault(key, 0) + 1);
        }
        for (Map.Entry<String, Integer> entry : seen.entrySet()) {
            if (entry.getValue() > 1) {
                throw new BizException(400, "Duplicate channel quota config: " + entry.getKey());
            }
        }
    }

    private List<PackageChannelQuotaConfig> findChannelQuotaConfigs(Long packagePlanId) {
        List<PackageChannelQuotaConfig> list = packageChannelQuotaConfigMapper.selectList(
                new LambdaQueryWrapper<PackageChannelQuotaConfig>()
                        .eq(PackageChannelQuotaConfig::getPackagePlanId, packagePlanId)
                        .orderByAsc(PackageChannelQuotaConfig::getChannelCode, PackageChannelQuotaConfig::getPeriodType, PackageChannelQuotaConfig::getId)
        );
        return list == null ? List.of() : list;
    }

    private void attachChannelQuotaConfigs(List<PackagePlan> plans) {
        if (plans == null || plans.isEmpty()) {
            return;
        }
        List<Long> packagePlanIds = plans.stream()
                .map(PackagePlan::getId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .collect(Collectors.toList());
        if (packagePlanIds.isEmpty()) {
            return;
        }
        List<PackageChannelQuotaConfig> allConfigs = packageChannelQuotaConfigMapper.selectList(
                new LambdaQueryWrapper<PackageChannelQuotaConfig>()
                        .in(PackageChannelQuotaConfig::getPackagePlanId, packagePlanIds)
                        .orderByAsc(PackageChannelQuotaConfig::getPackagePlanId, PackageChannelQuotaConfig::getChannelCode, PackageChannelQuotaConfig::getPeriodType)
        );
        Map<Long, List<PackageChannelQuotaConfig>> grouped = allConfigs.stream()
                .collect(Collectors.groupingBy(PackageChannelQuotaConfig::getPackagePlanId, LinkedHashMap::new, Collectors.toList()));
        for (PackagePlan plan : plans) {
            plan.setChannelQuotaConfigs(grouped.getOrDefault(plan.getId(), List.of()));
        }
    }

    private void validateBusinessFields(
            Integer keywordGroupLimit,
            Integer keywordGroupLimitA,
            Integer keywordGroupLimitB,
            Integer keywordGroupLimitC,
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
        if (keywordGroupLimit == null || keywordGroupLimit <= 0) {
            throw new BizException(400, "keyword_group_limit must be positive");
        }
        validateKeywordTierLimits(keywordGroupLimit, keywordGroupLimitA, keywordGroupLimitB, keywordGroupLimitC);
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

    private void validateKeywordTierLimits(Integer total, Integer a, Integer b, Integer c) {
        if (a == null || a < 0 || b == null || b < 0 || c == null || c < 0) {
            throw new BizException(400, "keyword group tier limits must be >= 0");
        }
        if (a + b + c != total) {
            throw new BizException(400, "KEYWORD_TIER_LIMIT_MISMATCH: A/B/C 额度之和必须等于关键词组总数");
        }
    }

    private void validateIntensityLevel(String level, String fieldName) {
        if (!StringUtils.hasText(level) || !INTENSITY_LEVELS.contains(level.trim())) {
            throw new BizException(400, "Invalid " + fieldName);
        }
    }

    private String normalizeChannel(String channelCode) {
        if (!StringUtils.hasText(channelCode)) {
            throw new BizException(400, "channel_code is required");
        }
        return channelCode.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizePeriod(String periodType) {
        if (!StringUtils.hasText(periodType)) {
            throw new BizException(400, "period_type is required");
        }
        return periodType.trim().toLowerCase(Locale.ROOT);
    }
}
