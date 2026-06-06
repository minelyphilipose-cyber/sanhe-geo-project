package com.huanjing.geo.module.presale.generate.narrative;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.module.presale.persist.entity.PresaleIndustryBucketMapping;
import com.huanjing.geo.module.presale.persist.entity.PresaleIndustryBucketReviewTask;
import com.huanjing.geo.module.presale.persist.entity.PresaleHeatmapSummary;
import com.huanjing.geo.module.presale.persist.entity.PresaleLexiconBucket;
import com.huanjing.geo.module.presale.persist.entity.PresaleNarrativeBandRule;
import com.huanjing.geo.module.presale.persist.entity.PresaleNarrativeFindingCopy;
import com.huanjing.geo.module.presale.persist.mapper.PresaleHeatmapSummaryMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleIndustryBucketMappingMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleIndustryBucketReviewTaskMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleIndustryLexiconMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleLexiconBucketMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleNarrativeBandRuleMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleNarrativeFindingCopyMapper;
import lombok.Builder;
import lombok.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class NarrativeConfigService {

    private static final Logger log = LoggerFactory.getLogger(NarrativeConfigService.class);
    private static final String DEFAULT_CONFIG_VERSION = "v1";

    private final PresaleNarrativeBandRuleMapper bandRuleMapper;
    private final PresaleLexiconBucketMapper lexiconBucketMapper;
    private final PresaleIndustryBucketMappingMapper industryBucketMappingMapper;
    private final PresaleIndustryBucketReviewTaskMapper industryBucketReviewTaskMapper;
    private final PresaleNarrativeFindingCopyMapper findingCopyMapper;
    private final PresaleHeatmapSummaryMapper heatmapSummaryMapper;

    public NarrativeConfigService(PresaleNarrativeBandRuleMapper bandRuleMapper,
                                  PresaleIndustryLexiconMapper ignoredLegacyLexiconMapper,
                                  PresaleLexiconBucketMapper lexiconBucketMapper,
                                  PresaleIndustryBucketMappingMapper industryBucketMappingMapper,
                                  PresaleIndustryBucketReviewTaskMapper industryBucketReviewTaskMapper,
                                  PresaleNarrativeFindingCopyMapper findingCopyMapper,
                                  PresaleHeatmapSummaryMapper heatmapSummaryMapper) {
        this.bandRuleMapper = bandRuleMapper;
        this.lexiconBucketMapper = lexiconBucketMapper;
        this.industryBucketMappingMapper = industryBucketMappingMapper;
        this.industryBucketReviewTaskMapper = industryBucketReviewTaskMapper;
        this.findingCopyMapper = findingCopyMapper;
        this.heatmapSummaryMapper = heatmapSummaryMapper;
    }

    public NarrativeConfigSnapshot load(String industry) {
        return NarrativeConfigSnapshot.builder()
                .configVersion(DEFAULT_CONFIG_VERSION)
                .bandRules(loadBandRules())
                .lexicon(loadLexicon(industry))
                .build();
    }

    public Map<String, PresaleNarrativeFindingCopy> loadFindingCopyMap() {
        try {
            LambdaQueryWrapper<PresaleNarrativeFindingCopy> q = new LambdaQueryWrapper<>();
            q.eq(PresaleNarrativeFindingCopy::getConfigVersion, DEFAULT_CONFIG_VERSION);
            q.eq(PresaleNarrativeFindingCopy::getEnabled, Boolean.TRUE);
            q.orderByAsc(PresaleNarrativeFindingCopy::getPriority);
            q.orderByAsc(PresaleNarrativeFindingCopy::getId);
            List<PresaleNarrativeFindingCopy> rows = findingCopyMapper.selectList(q);
            if (rows != null && !rows.isEmpty()) {
                return rows.stream()
                        .collect(Collectors.toMap(
                                row -> copyKey(row.getCode(), row.getTier(), row.getBandOverride(), row.getArchetypeOverride()),
                                row -> row,
                                (left, right) -> left
                        ));
            }
        } catch (RuntimeException e) {
            log.warn("Load presale narrative finding copy failed, using defaults: {}", e.getMessage());
        }
        return Map.of();
    }

    public PresaleHeatmapSummary loadHeatmapSummary(String heatmapPattern, String band) {
        try {
            LambdaQueryWrapper<PresaleHeatmapSummary> q = new LambdaQueryWrapper<>();
            q.eq(PresaleHeatmapSummary::getConfigVersion, DEFAULT_CONFIG_VERSION);
            q.eq(PresaleHeatmapSummary::getHeatmapPattern, heatmapPattern);
            q.eq(PresaleHeatmapSummary::getEnabled, Boolean.TRUE);
            if (StringUtils.hasText(band)) {
                q.and(w -> w.isNull(PresaleHeatmapSummary::getBandOverride)
                        .or()
                        .eq(PresaleHeatmapSummary::getBandOverride, band));
            } else {
                q.isNull(PresaleHeatmapSummary::getBandOverride);
            }
            q.orderByDesc(PresaleHeatmapSummary::getBandOverride);
            q.orderByAsc(PresaleHeatmapSummary::getSortOrder);
            q.orderByAsc(PresaleHeatmapSummary::getId);
            q.last("LIMIT 1");
            PresaleHeatmapSummary row = heatmapSummaryMapper.selectOne(q);
            return row == null ? defaultHeatmapSummary(heatmapPattern) : row;
        } catch (RuntimeException e) {
            log.warn("Load presale heatmap summary failed pattern={}, using default: {}", heatmapPattern, e.getMessage());
            return defaultHeatmapSummary(heatmapPattern);
        }
    }

    private PresaleHeatmapSummary defaultHeatmapSummary(String heatmapPattern) {
        PresaleHeatmapSummary row = new PresaleHeatmapSummary();
        row.setConfigVersion(DEFAULT_CONFIG_VERSION);
        row.setHeatmapPattern(heatmapPattern);
        row.setEnabled(Boolean.TRUE);
        switch (heatmapPattern == null ? "" : heatmapPattern) {
            case "NEW_CUSTOMER_BLANK" -> {
                row.setSummaryTemplate("新顾客入口场景仍存在明显空白,需要优先补齐推荐、咨询和具体场景问题中的品牌出现。");
                row.setColorLegendTemplate("颜色越深表示该场景下品牌越稳定出现;灰色表示该平台未参与或无有效样本。");
            }
            case "RECO_UNSTABLE" -> {
                row.setSummaryTemplate("推荐场景已有出现,但平台间波动较大,说明 AI 对品牌的推荐信号还不稳定。");
                row.setColorLegendTemplate("颜色差异体现不同平台的推荐稳定性差异;灰色表示该平台未参与或无有效样本。");
            }
            case "BROAD_PRESENCE" -> {
                row.setSummaryTemplate("新老顾客场景均已有品牌出现,当前重点是保持稳定覆盖并补强局部短板。");
                row.setColorLegendTemplate("颜色用于观察平台和场景之间的强弱差异,不是单一好坏判断。");
            }
            default -> {
                row.setHeatmapPattern("RECO_EMERGING");
                row.setSummaryTemplate("推荐场景开始出现品牌信号,但覆盖广度和强度仍需要继续放大。");
                row.setColorLegendTemplate("颜色越深表示该场景信号越强;浅色表示仍处在建设初期。");
            }
        }
        return row;
    }

    public static String copyKey(String code, String tier, String band, String archetype) {
        return String.join("|",
                code == null ? "" : code,
                tier == null ? "" : tier,
                band == null ? "" : band,
                archetype == null ? "" : archetype);
    }

    private List<PresaleNarrativeBandRule> loadBandRules() {
        try {
            LambdaQueryWrapper<PresaleNarrativeBandRule> q = new LambdaQueryWrapper<>();
            q.eq(PresaleNarrativeBandRule::getConfigVersion, DEFAULT_CONFIG_VERSION);
            q.eq(PresaleNarrativeBandRule::getEnabled, Boolean.TRUE);
            q.orderByAsc(PresaleNarrativeBandRule::getSortOrder);
            q.orderByAsc(PresaleNarrativeBandRule::getId);
            List<PresaleNarrativeBandRule> rows = bandRuleMapper.selectList(q);
            if (rows != null && !rows.isEmpty()) {
                return rows;
            }
        } catch (RuntimeException e) {
            log.warn("Load presale narrative band rules failed, using defaults: {}", e.getMessage());
        }
        return defaultBandRules();
    }

    private IndustryLexicon loadLexicon(String industry) {
        PresaleIndustryBucketMapping mapping = selectMapping(industry);
        if (mapping != null) {
            PresaleLexiconBucket bucket = selectBucket(mapping.getBucketCode());
            if (bucket != null) {
                return IndustryLexicon.builder()
                        .customerTerm(bucket.getCustomerTerm())
                        .conversionTerm(bucket.getConversionTerm())
                        .industryShort(firstText(mapping.getIndustryShort(), bucket.getDefaultIndustryShort(), mapping.getIndustryKey()))
                        .fallback(false)
                        .build();
            }
            log.warn("Approved industry bucket mapping points to missing/disabled bucket, industry={}, bucket={}",
                    industry, mapping.getBucketCode());
        }
        PresaleLexiconBucket generic = selectBucket("_ALL_");
        if (generic != null) {
            upsertLexiconReviewTask(industry);
            return IndustryLexicon.builder()
                    .customerTerm(generic.getCustomerTerm())
                    .conversionTerm(generic.getConversionTerm())
                    .industryShort(firstText(generic.getDefaultIndustryShort(), "行业"))
                    .fallback(true)
                    .build();
        }
        upsertLexiconReviewTask(industry);
        return IndustryLexicon.builder()
                .customerTerm("客户")
                .conversionTerm("下单")
                .industryShort("行业")
                .fallback(true)
                .build();
    }

    private void upsertLexiconReviewTask(String industry) {
        String industryKey = IndustryKeyNormalizer.normalize(industry);
        if (!StringUtils.hasText(industryKey) || "_all_".equals(industryKey)) {
            return;
        }
        try {
            LambdaQueryWrapper<PresaleIndustryBucketReviewTask> q = new LambdaQueryWrapper<>();
            q.eq(PresaleIndustryBucketReviewTask::getIndustryKey, industryKey);
            q.last("LIMIT 1");
            PresaleIndustryBucketReviewTask existing = industryBucketReviewTaskMapper.selectOne(q);
            if (existing != null) {
                existing.setFallbackHitCount((existing.getFallbackHitCount() == null ? 0 : existing.getFallbackHitCount()) + 1);
                industryBucketReviewTaskMapper.updateById(existing);
                return;
            }
            PresaleIndustryBucketReviewTask task = new PresaleIndustryBucketReviewTask();
            task.setIndustry(industry == null ? industryKey : industry.trim());
            task.setIndustryKey(industryKey);
            task.setStatus("PENDING");
            task.setSource("MISSING_MAPPING");
            task.setFallbackHitCount(1);
            industryBucketReviewTaskMapper.insert(task);
        } catch (RuntimeException e) {
            log.warn("Create presale bucket review task failed industry={}: {}", industry, e.getMessage());
        }
    }

    private PresaleIndustryBucketMapping selectMapping(String industry) {
        String industryKey = IndustryKeyNormalizer.normalize(industry);
        if (!StringUtils.hasText(industryKey)) {
            return null;
        }
        try {
            LambdaQueryWrapper<PresaleIndustryBucketMapping> q = new LambdaQueryWrapper<>();
            q.eq(PresaleIndustryBucketMapping::getConfigVersion, DEFAULT_CONFIG_VERSION);
            q.eq(PresaleIndustryBucketMapping::getIndustryKey, industryKey);
            q.eq(PresaleIndustryBucketMapping::getApproved, Boolean.TRUE);
            q.last("LIMIT 1");
            return industryBucketMappingMapper.selectOne(q);
        } catch (RuntimeException e) {
            log.warn("Load presale industry bucket mapping failed industry={}, using fallback: {}", industry, e.getMessage());
            return null;
        }
    }

    private PresaleLexiconBucket selectBucket(String bucketCode) {
        if (!StringUtils.hasText(bucketCode)) {
            return null;
        }
        try {
            LambdaQueryWrapper<PresaleLexiconBucket> q = new LambdaQueryWrapper<>();
            q.eq(PresaleLexiconBucket::getConfigVersion, DEFAULT_CONFIG_VERSION);
            q.eq(PresaleLexiconBucket::getBucketCode, bucketCode);
            q.eq(PresaleLexiconBucket::getEnabled, Boolean.TRUE);
            q.last("LIMIT 1");
            return lexiconBucketMapper.selectOne(q);
        } catch (RuntimeException e) {
            log.warn("Load presale lexicon bucket failed bucket={}, using fallback: {}", bucketCode, e.getMessage());
            return null;
        }
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private List<PresaleNarrativeBandRule> defaultBandRules() {
        return List.of(
                band("INVISIBLE", null, bd("0.4000"), null, null, null, null, 10),
                band("BEHIND", bd("0.4000"), bd("0.8500"), null, null, null, null, 20),
                band("MIDDLE", bd("0.8500"), bd("1.1500"), null, null, null, null, 30),
                band("STRONG", bd("1.1500"), null, null, bd("0.9000"), bd("50.00"), bd("50.00"), 40),
                band("LEADER", null, null, bd("0.9000"), null, bd("65.00"), bd("65.00"), 50)
        );
    }

    private PresaleNarrativeBandRule band(String name,
                                          BigDecimal minAvg,
                                          BigDecimal maxAvg,
                                          BigDecimal minTop1,
                                          BigDecimal maxTop1,
                                          BigDecimal minMention,
                                          BigDecimal minCoverage,
                                          int sortOrder) {
        PresaleNarrativeBandRule rule = new PresaleNarrativeBandRule();
        rule.setConfigVersion(DEFAULT_CONFIG_VERSION);
        rule.setBand(name);
        rule.setMinAvgRatio(minAvg);
        rule.setMaxAvgRatio(maxAvg);
        rule.setMinTop1Ratio(minTop1);
        rule.setMaxTop1Ratio(maxTop1);
        rule.setMinMentionScore(minMention);
        rule.setMinCoverageScore(minCoverage);
        rule.setEnabled(Boolean.TRUE);
        rule.setSortOrder(sortOrder);
        return rule;
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    @Value
    @Builder
    public static class NarrativeConfigSnapshot {
        String configVersion;
        List<PresaleNarrativeBandRule> bandRules;
        IndustryLexicon lexicon;
    }

    @Value
    @Builder
    public static class IndustryLexicon {
        String customerTerm;
        String conversionTerm;
        String industryShort;
        boolean fallback;
    }
}
