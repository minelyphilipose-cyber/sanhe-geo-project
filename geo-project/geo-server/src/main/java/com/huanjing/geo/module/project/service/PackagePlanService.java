package com.huanjing.geo.module.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.project.dto.PackageChannelQuotaConfigRequest;
import com.huanjing.geo.module.project.dto.PackagePlanCreateRequest;
import com.huanjing.geo.module.project.dto.PackagePlanUpdateRequest;
import com.huanjing.geo.module.customer.service.CompanyPackageBindingService;
import com.huanjing.geo.module.project.entity.PackageChannelQuotaConfig;
import com.huanjing.geo.module.project.entity.PackagePlan;
import com.huanjing.geo.module.project.mapper.PackageChannelQuotaConfigMapper;
import com.huanjing.geo.module.project.mapper.PackagePlanMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class PackagePlanService {

    private static final Pattern PACKAGE_TYPE_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{2,31}$");
    public static final String AUDIENCE_INTERNAL = "internal";
    public static final String AUDIENCE_PARTNER = "partner";
    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_INACTIVE = "inactive";
    private static final Set<String> AUDIENCE_TYPES = Set.of(AUDIENCE_INTERNAL, AUDIENCE_PARTNER);
    private static final Set<String> INTENSITY_LEVELS = Set.of("L1", "L2", "L3");
    private static final List<String> SELF_MEDIA_CHANNEL_CODES = ArticlePromptChannels.SELF_MEDIA_SUB_CODES.stream()
            .map(PackagePlanService::selfMediaChannelCode)
            .toList();
    private static final Set<String> CHANNEL_CODES = Stream.concat(
            Stream.of("official_site", "industry_site", "forum", "authority_media"),
            SELF_MEDIA_CHANNEL_CODES.stream()
    ).collect(Collectors.toUnmodifiableSet());
    private static final Set<String> PERIOD_TYPES = Set.of("day", "week", "month", "total");
    private static final Map<String, String> DEFAULT_PERIOD_BY_CHANNEL = defaultPeriodByChannel();

    private final PackagePlanMapper packagePlanMapper;
    private final PackageChannelQuotaConfigMapper packageChannelQuotaConfigMapper;
    private final CompanyPackageBindingService companyPackageBindingService;
    private final CurrentUserService currentUserService;

    public Page<PackagePlan> page(long current, long size, String keyword, Boolean enabled) {
        return page(current, size, keyword, enabled, null);
    }

    public Page<PackagePlan> page(long current, long size, String keyword, Boolean enabled, String audienceType) {
        currentUserService.ensurePermission("user.manage");
        LambdaQueryWrapper<PackagePlan> wrapper = new LambdaQueryWrapper<PackagePlan>()
                .isNull(PackagePlan::getDeletedAt)
                .orderByAsc(PackagePlan::getSortOrder)
                .orderByAsc(PackagePlan::getId);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(PackagePlan::getPackageType, keyword)
                    .or().like(PackagePlan::getPackageName, keyword));
        }
        if (enabled != null) {
            wrapper.eq(PackagePlan::getEnabled, enabled);
        }
        if (StringUtils.hasText(audienceType)) {
            wrapper.eq(PackagePlan::getAudienceType, normalizeAudienceType(audienceType));
        }
        Page<PackagePlan> result = packagePlanMapper.selectPage(new Page<>(current, size), wrapper);
        normalizeLegacyPrices(result.getRecords());
        attachChannelQuotaConfigs(result.getRecords());
        return result;
    }

    public List<PackagePlan> listEnabled() {
        currentUserService.ensurePermission("project.read");
        SysUser currentUser = currentUserService.requireCurrentUser();
        String audienceType = currentUserService.isPartnerUser(currentUser) ? AUDIENCE_PARTNER : AUDIENCE_INTERNAL;
        List<PackagePlan> plans = packagePlanMapper.selectList(
                new LambdaQueryWrapper<PackagePlan>()
                        .eq(PackagePlan::getEnabled, true)
                        .eq(PackagePlan::getPackageStatus, STATUS_ACTIVE)
                        .eq(PackagePlan::getAudienceType, audienceType)
                        .isNull(PackagePlan::getDeletedAt)
                        .orderByAsc(PackagePlan::getSortOrder)
                        .orderByAsc(PackagePlan::getId)
        );
        normalizeLegacyPrices(plans);
        attachChannelQuotaConfigs(plans);
        return plans;
    }

    @Transactional
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
            throw new BizException(400, "套餐类型已存在，请更换后重试");
        }
        String audienceType = normalizeAudienceType(req.getAudienceType());
        validatePartnerPackageFields(audienceType, req.getPartnerPoints());
        PackagePlan plan = new PackagePlan();
        plan.setPackageType(req.getPackageType().trim());
        plan.setPackageName(req.getPackageName().trim());
        plan.setAudienceType(audienceType);
        plan.setPackageStatus(STATUS_DRAFT);
        plan.setStandardPrice(req.getStandardPrice());
        plan.setPartnerPoints(req.getPartnerPoints());
        plan.setPartnerVisibleConfigJson(req.getPartnerVisibleConfigJson());
        plan.setInternalDeliveryConfigJson(req.getInternalDeliveryConfigJson());
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
        plan.setEnabled(false);
        plan.setSortOrder(req.getSortOrder());
        plan.setRemark(req.getRemark());
        packagePlanMapper.insert(plan);
        saveChannelQuotaConfigs(plan.getId(), req.getChannelQuotaConfigs());
        attachChannelQuotaConfigs(List.of(plan));
        return plan;
    }

    @Transactional
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
        String audienceType = normalizeAudienceType(req.getAudienceType());
        validatePartnerPackageFields(audienceType, req.getPartnerPoints());
        List<PackageChannelQuotaConfigRequest> channelQuotaConfigs = normalizeChannelQuotaInput(req.getChannelQuotaConfigs());
        validateChannelQuotaConfigs(channelQuotaConfigs);
        if (companyPackageBindingService.hasBindingsForPackagePlan(plan.getId())) {
            validateBoundPackageExpansion(
                    plan,
                    req,
                    audienceType,
                    findChannelQuotaConfigs(plan.getId()),
                    channelQuotaConfigs
            );
        }
        plan.setPackageName(req.getPackageName().trim());
        plan.setAudienceType(audienceType);
        plan.setStandardPrice(req.getStandardPrice());
        plan.setPartnerPoints(req.getPartnerPoints());
        plan.setPartnerVisibleConfigJson(req.getPartnerVisibleConfigJson());
        plan.setInternalDeliveryConfigJson(req.getInternalDeliveryConfigJson());
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
        replaceChannelQuotaConfigs(plan.getId(), channelQuotaConfigs);
        attachChannelQuotaConfigs(List.of(plan));
        return plan;
    }

    public void updateStatus(Long id, Boolean enabled) {
        currentUserService.ensurePermission("user.manage");
        PackagePlan plan = requireById(id);
        String nextStatus = resolveNextPackageStatus(plan, enabled);
        plan.setEnabled(enabled);
        plan.setPackageStatus(nextStatus);
        packagePlanMapper.updateById(plan);
    }

    public void delete(Long id) {
        currentUserService.ensurePermission("user.manage");
        PackagePlan plan = requireById(id);
        ensurePackagePlanMutable(plan.getId(), "Package plan already bound to customer, disable it instead");
        plan.setDeletedAt(java.time.LocalDateTime.now());
        plan.setDeletedBy(com.huanjing.geo.common.util.SecurityUtils.getCurrentUserId());
        plan.setEnabled(false);
        plan.setPackageStatus(STATUS_INACTIVE);
        packagePlanMapper.updateById(plan);
    }

    public PackagePlan requireEnabledByType(String packageType) {
        PackagePlan plan = packagePlanMapper.selectOne(
                new LambdaQueryWrapper<PackagePlan>()
                        .eq(PackagePlan::getPackageType, packageType)
                        .eq(PackagePlan::getEnabled, true)
                        .eq(PackagePlan::getPackageStatus, STATUS_ACTIVE)
                        .isNull(PackagePlan::getDeletedAt)
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

    @Transactional
    public List<PackageChannelQuotaConfig> saveChannelQuotaConfigsByPlanId(Long packagePlanId, List<PackageChannelQuotaConfigRequest> configs) {
        currentUserService.ensurePermission("user.manage");
        PackagePlan plan = requireById(packagePlanId);
        List<PackageChannelQuotaConfigRequest> normalizedConfigs = normalizeChannelQuotaInput(configs);
        validateChannelQuotaConfigs(normalizedConfigs);
        if (companyPackageBindingService.hasBindingsForPackagePlan(plan.getId())) {
            validateBoundChannelQuotaExpansion(findChannelQuotaConfigs(plan.getId()), normalizedConfigs);
        }
        replaceChannelQuotaConfigs(plan.getId(), normalizedConfigs);
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
        if (plan == null || plan.getDeletedAt() != null) {
            throw new BizException(404, "Package plan not found");
        }
        return plan;
    }

    private void ensurePackagePlanMutable(Long packagePlanId, String message) {
        if (companyPackageBindingService.hasBindingsForPackagePlan(packagePlanId)) {
            throw new BizException(400, message);
        }
    }

    private String resolveNextPackageStatus(PackagePlan plan, Boolean enabled) {
        if (plan == null || enabled == null) {
            throw new BizException(400, "Invalid package status update");
        }
        String current = StringUtils.hasText(plan.getPackageStatus())
                ? plan.getPackageStatus()
                : (Boolean.TRUE.equals(plan.getEnabled()) ? STATUS_ACTIVE : STATUS_INACTIVE);
        if (STATUS_DRAFT.equals(current)) {
            if (!Boolean.TRUE.equals(enabled)) {
                throw new BizException(400, "Draft package can only be published");
            }
            return STATUS_ACTIVE;
        }
        if (STATUS_ACTIVE.equals(current)) {
            return Boolean.TRUE.equals(enabled) ? STATUS_ACTIVE : STATUS_INACTIVE;
        }
        if (STATUS_INACTIVE.equals(current)) {
            return Boolean.TRUE.equals(enabled) ? STATUS_ACTIVE : STATUS_INACTIVE;
        }
        throw new BizException(400, "Invalid package status: " + current);
    }

    private String normalizeAudienceType(String audienceType) {
        String value = StringUtils.hasText(audienceType) ? audienceType.trim().toLowerCase(Locale.ROOT) : AUDIENCE_INTERNAL;
        if (!AUDIENCE_TYPES.contains(value)) {
            throw new BizException(400, "Invalid audience_type");
        }
        return value;
    }

    private void validatePartnerPackageFields(String audienceType, BigDecimal partnerPoints) {
        if (AUDIENCE_PARTNER.equals(audienceType)
                && (partnerPoints == null || partnerPoints.compareTo(BigDecimal.ZERO) <= 0)) {
            throw new BizException(400, "合伙人套餐消耗积分必须大于 0");
        }
    }

    private void validateType(String packageType) {
        if (!StringUtils.hasText(packageType) || !PACKAGE_TYPE_PATTERN.matcher(packageType.trim()).matches()) {
            throw new BizException(400, "套餐类型格式不正确：仅支持小写字母、数字和下划线，需以小写字母开头，长度 3-32");
        }
    }

    private void validateBase(BigDecimal standardPrice, Integer serviceMonths, Integer sortOrder) {
        if (standardPrice == null || standardPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(400, "消耗积分必须大于 0");
        }
        if (serviceMonths == null || serviceMonths <= 0) {
            throw new BizException(400, "服务月数必须大于 0");
        }
        if (sortOrder == null) {
            throw new BizException(400, "请输入排序值");
        }
    }

    private void saveChannelQuotaConfigs(Long packagePlanId, List<PackageChannelQuotaConfigRequest> configs) {
        List<PackageChannelQuotaConfigRequest> normalizedInput = normalizeChannelQuotaInput(configs);
        validateChannelQuotaConfigs(normalizedInput);
        replaceChannelQuotaConfigs(packagePlanId, normalizedInput);
    }

    private void replaceChannelQuotaConfigs(Long packagePlanId, List<PackageChannelQuotaConfigRequest> normalizedInput) {
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

    private void validateBoundPackageExpansion(
            PackagePlan plan,
            PackagePlanUpdateRequest req,
            String audienceType,
            List<PackageChannelQuotaConfig> currentChannelQuotas,
            List<PackageChannelQuotaConfigRequest> nextChannelQuotas
    ) {
        String currentAudienceType = normalizeAudienceType(plan.getAudienceType());
        if (!Objects.equals(currentAudienceType, audienceType)) {
            throw boundPackageReduction("适用对象不可从 " + currentAudienceType + " 修改为 " + audienceType);
        }

        requireNotDecreased("服务月数", plan.getServiceMonths(), req.getServiceMonths());
        requireNotDecreased("拓词问题总数", plan.getKeywordGroupLimit(), req.getKeywordGroupLimit());
        requireNotDecreased("A 档问题数", plan.getKeywordGroupLimitA(), req.getKeywordGroupLimitA());
        requireNotDecreased("B 档问题数", plan.getKeywordGroupLimitB(), req.getKeywordGroupLimitB());
        requireNotDecreased("C 档问题数", plan.getKeywordGroupLimitC(), req.getKeywordGroupLimitC());
        requireIntensityNotDecreased("月报深度", plan.getMonthlyReportDepth(), req.getMonthlyReportDepth());
        requireIntensityNotDecreased("季报深度", plan.getQuarterlyReportDepth(), req.getQuarterlyReportDepth());
        requireIntensityNotDecreased("顾问服务强度", plan.getConsultantIntensity(), req.getConsultantIntensity());
        requireIntensityNotDecreased("竞品洞察深度", plan.getCompetitorInsightDepth(), req.getCompetitorInsightDepth());
        requireIntensityNotDecreased("媒体分发强度", plan.getMediaDistributionIntensity(), req.getMediaDistributionIntensity());
        requireIntensityNotDecreased("承诺目标强度", plan.getCommitmentTargetIntensity(), req.getCommitmentTargetIntensity());

        String currentMetricType = normalizeText(plan.getTargetMetricType());
        String nextMetricType = normalizeText(req.getTargetMetricType());
        if (currentMetricType != null && !Objects.equals(currentMetricType, nextMetricType)) {
            throw boundPackageReduction("量化目标类型不可修改");
        }
        if (plan.getTargetMetricValue() != null
                && req.getTargetMetricValue().compareTo(plan.getTargetMetricValue()) < 0) {
            throw boundPackageReduction("量化目标值只能提高，不能从 "
                    + plan.getTargetMetricValue() + " 调整为 " + req.getTargetMetricValue());
        }
        if (plan.getTargetWindowDays() != null && req.getTargetWindowDays() > plan.getTargetWindowDays()) {
            throw boundPackageReduction("目标周期只能缩短，不能从 "
                    + plan.getTargetWindowDays() + " 天调整为 " + req.getTargetWindowDays() + " 天");
        }

        validateBoundChannelQuotaExpansion(currentChannelQuotas, nextChannelQuotas);
    }

    private void validateBoundChannelQuotaExpansion(
            List<PackageChannelQuotaConfig> currentConfigs,
            List<PackageChannelQuotaConfigRequest> nextConfigs
    ) {
        Map<String, PackageChannelQuotaConfigRequest> nextByChannel = nextConfigs.stream()
                .collect(Collectors.toMap(
                        cfg -> normalizeChannel(cfg.getChannelCode()),
                        cfg -> cfg,
                        (left, right) -> right,
                        LinkedHashMap::new
                ));
        for (PackageChannelQuotaConfig current : currentConfigs == null ? List.<PackageChannelQuotaConfig>of() : currentConfigs) {
            if (current == null || !Boolean.TRUE.equals(current.getEnabled())) {
                continue;
            }
            String channel = normalizeChannel(current.getChannelCode());
            PackageChannelQuotaConfigRequest next = nextByChannel.get(channel);
            if (next == null || !Boolean.TRUE.equals(next.getEnabled())) {
                throw boundPackageReduction("已启用渠道 " + channel + " 不可删除或停用");
            }
            String currentPeriod = normalizePeriod(current.getPeriodType());
            String nextPeriod = normalizePeriod(next.getPeriodType());
            if (!Objects.equals(currentPeriod, nextPeriod)) {
                throw boundPackageReduction("已启用渠道 " + channel + " 的额度周期不可从 "
                        + currentPeriod + " 修改为 " + nextPeriod);
            }
            if (current.getQuotaLimit() != null && next.getQuotaLimit() < current.getQuotaLimit()) {
                throw boundPackageReduction("渠道 " + channel + " 的额度只能增加，不能从 "
                        + current.getQuotaLimit() + " 调整为 " + next.getQuotaLimit());
            }
        }
    }

    private void requireNotDecreased(String fieldLabel, Integer current, Integer next) {
        if (current != null && next < current) {
            throw boundPackageReduction(fieldLabel + "只能增加，不能从 " + current + " 调整为 " + next);
        }
    }

    private void requireIntensityNotDecreased(String fieldLabel, String current, String next) {
        String normalizedCurrent = normalizeText(current);
        if (normalizedCurrent == null) {
            return;
        }
        if (intensityRank(next) < intensityRank(normalizedCurrent)) {
            throw boundPackageReduction(fieldLabel + "只能提高，不能从 " + normalizedCurrent + " 调整为 " + next.trim());
        }
    }

    private int intensityRank(String level) {
        return Integer.parseInt(level.trim().substring(1));
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private BizException boundPackageReduction(String detail) {
        return new BizException(400, "套餐已绑定客户，只允许扩大权益：" + detail);
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
            String key = channel;
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

    private static String selfMediaChannelCode(String platform) {
        return ArticlePromptChannels.SELF_MEDIA + ":" + platform;
    }

    private static Map<String, String> defaultPeriodByChannel() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("official_site", "week");
        result.put("industry_site", "week");
        result.put("forum", "week");
        for (String channelCode : SELF_MEDIA_CHANNEL_CODES) {
            result.put(channelCode, "week");
        }
        result.put("authority_media", "total");
        return Map.copyOf(result);
    }
}
