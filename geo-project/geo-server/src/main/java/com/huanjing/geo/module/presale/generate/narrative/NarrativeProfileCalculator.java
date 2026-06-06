package com.huanjing.geo.module.presale.generate.narrative;

import com.huanjing.geo.module.presale.dto.snapshot.common.ScoreSet;
import com.huanjing.geo.module.presale.dto.snapshot.computed.ComputedSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.computed.NarrativeProfile;
import com.huanjing.geo.module.presale.dto.snapshot.computed.OptimizationFinding;
import com.huanjing.geo.module.presale.dto.snapshot.computed.PlatformIntentCell;
import com.huanjing.geo.module.presale.dto.snapshot.computed.SceneCompetitorPressure;
import com.huanjing.geo.module.presale.dto.snapshot.computed.Scores;
import com.huanjing.geo.module.presale.dto.snapshot.raw.BenchmarksFrozen;
import com.huanjing.geo.module.presale.dto.snapshot.raw.Competitor;
import com.huanjing.geo.module.presale.dto.snapshot.raw.RawSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.raw.SentimentDetail;
import com.huanjing.geo.module.presale.persist.entity.PresaleNarrativeBandRule;
import com.huanjing.geo.module.presale.ruleengine.RuleCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class NarrativeProfileCalculator {

    private static final String PROFILE_VERSION = "v1";
    private static final double NEUTRAL_SHARE_THRESHOLD = 0.80D;
    private static final double RECO_BLANK_RATE = 5D;
    private static final double RECO_EMERGING_RATE = 35D;
    private static final double RECO_UNSTABLE_GAP = 35D;
    private static final String STRONG_COMPETITOR_CLAIM_PATTERN = "抢走|指给别人|没有你|报的是对手";

    private final NarrativeConfigService configService;

    public NarrativeProfile compute(RawSnapshotDTO raw, ComputedSnapshotDTO computed) {
        String industry = raw == null || raw.getClientInfo() == null ? null : raw.getClientInfo().getIndustry();
        NarrativeConfigService.NarrativeConfigSnapshot config = configService.load(industry);
        try {
            BandDecision bandDecision = decideBand(computed == null ? null : computed.getScores(),
                    raw == null ? null : raw.getBenchmarksFrozen(), config.getBandRules());
            SignalSnapshot signals = collectSignals(raw, computed);
            NarrativeProfile.Archetype primary = decidePrimaryArchetype(bandDecision.band(), signals);
            List<NarrativeProfile.FindingTier> tiers = buildFindingTiers(computed, signals, primary, bandDecision.band());
            NarrativeProfile.HeatmapPattern heatmapPattern = decideHeatmapPattern(computed);
            NarrativeProfile.CompetitorStory competitorStory = buildCompetitorStory(
                    bandDecision.band(), signals.competitorPressure(), config.getLexicon());

            return NarrativeProfile.builder()
                    .profileVersion(PROFILE_VERSION)
                    .configVersion(config.getConfigVersion())
                    .band(bandDecision.band())
                    .bandTone(bandTone(bandDecision.band()))
                    .archetypePrimary(primary)
                    .archetypeSecondary(decideSecondaryArchetypes(primary, bandDecision.band(), signals))
                    .findingTiers(tiers)
                    .heatmapPattern(heatmapPattern)
                    .displayFlags(buildDisplayFlags(primary, signals, bandDecision.band()))
                    .competitorStory(competitorStory)
                    .lexiconFallback(config.getLexicon() == null || config.getLexicon().isFallback())
                    .fallback(false)
                    .diagnostics(buildDiagnostics(bandDecision, signals))
                    .build();
        } catch (RuntimeException e) {
            return fallbackProfile(config.getConfigVersion(), "calculator_exception:" + e.getClass().getSimpleName());
        }
    }

    private BandDecision decideBand(Scores scores,
                                    BenchmarksFrozen benchmarks,
                                    List<PresaleNarrativeBandRule> rules) {
        Double overall = scores == null ? null : scores.getOverall();
        ScoreSet avg = benchmarks == null ? null : benchmarks.getIndustryAvg();
        ScoreSet top1 = benchmarks == null ? null : benchmarks.getTop1();
        Double avgOverall = avg == null ? null : avg.getOverall();
        Double top1Overall = top1 == null ? null : top1.getOverall();
        if (!positive(overall) || !positive(avgOverall)) {
            return new BandDecision(NarrativeProfile.Band.MIDDLE, null, null, "missing_overall_or_avg");
        }

        Double avgRatio = overall / avgOverall;
        Double top1Ratio = positive(top1Overall) ? overall / top1Overall : null;
        boolean leaderGate = highBandGatePassed(scores, rules, NarrativeProfile.Band.LEADER);
        boolean strongGate = highBandGatePassed(scores, rules, NarrativeProfile.Band.STRONG);
        if (top1Ratio != null && top1Ratio >= minTop1Ratio(rules, NarrativeProfile.Band.LEADER)) {
            if (leaderGate) {
                return new BandDecision(NarrativeProfile.Band.LEADER, avgRatio, top1Ratio, "leader_high_priority");
            }
            return new BandDecision(NarrativeProfile.Band.MIDDLE, avgRatio, top1Ratio, "leader_ratio_gate_downgraded");
        }
        if (avgRatio >= minAvgRatio(rules, NarrativeProfile.Band.STRONG)
                && (top1Ratio == null || top1Ratio < maxTop1Ratio(rules, NarrativeProfile.Band.STRONG))) {
            if (strongGate) {
                return new BandDecision(NarrativeProfile.Band.STRONG, avgRatio, top1Ratio, "strong_ratio");
            }
            return new BandDecision(NarrativeProfile.Band.MIDDLE, avgRatio, top1Ratio, "strong_ratio_gate_downgraded");
        }
        if (avgRatio >= minAvgRatio(rules, NarrativeProfile.Band.MIDDLE)) {
            return new BandDecision(NarrativeProfile.Band.MIDDLE, avgRatio, top1Ratio, "middle_ratio");
        }
        if (avgRatio >= minAvgRatio(rules, NarrativeProfile.Band.BEHIND)) {
            return new BandDecision(NarrativeProfile.Band.BEHIND, avgRatio, top1Ratio, "behind_ratio");
        }
        return new BandDecision(NarrativeProfile.Band.INVISIBLE, avgRatio, top1Ratio, "invisible_ratio");
    }

    private boolean highBandGatePassed(Scores scores, List<PresaleNarrativeBandRule> rules, NarrativeProfile.Band band) {
        if (scores == null) {
            return false;
        }
        double fallback = band == NarrativeProfile.Band.LEADER ? 65D : 50D;
        double minMention = minScoreGate(rules, band, PresaleNarrativeBandRule::getMinMentionScore, fallback);
        double minCoverage = minScoreGate(rules, band, PresaleNarrativeBandRule::getMinCoverageScore, fallback);
        return valueOrZero(scores.getMention()) >= minMention && valueOrZero(scores.getCoverage()) >= minCoverage;
    }

    private double minAvgRatio(List<PresaleNarrativeBandRule> rules, NarrativeProfile.Band band) {
        return firstRuleValue(rules, band, PresaleNarrativeBandRule::getMinAvgRatio, switch (band) {
            case BEHIND -> 0.40D;
            case MIDDLE -> 0.85D;
            case STRONG -> 1.15D;
            default -> 0D;
        });
    }

    private double minTop1Ratio(List<PresaleNarrativeBandRule> rules, NarrativeProfile.Band band) {
        return firstRuleValue(rules, band, PresaleNarrativeBandRule::getMinTop1Ratio, 0.90D);
    }

    private double maxTop1Ratio(List<PresaleNarrativeBandRule> rules, NarrativeProfile.Band band) {
        return firstRuleValue(rules, band, PresaleNarrativeBandRule::getMaxTop1Ratio, 0.90D);
    }

    private double minScoreGate(List<PresaleNarrativeBandRule> rules,
                                NarrativeProfile.Band band,
                                java.util.function.Function<PresaleNarrativeBandRule, BigDecimal> getter,
                                double fallback) {
        return firstRuleValue(rules, band, getter, fallback);
    }

    private double firstRuleValue(List<PresaleNarrativeBandRule> rules,
                                  NarrativeProfile.Band band,
                                  java.util.function.Function<PresaleNarrativeBandRule, BigDecimal> getter,
                                  double fallback) {
        if (rules != null) {
            for (PresaleNarrativeBandRule rule : rules) {
                if (rule != null && band.name().equals(rule.getBand()) && getter.apply(rule) != null) {
                    return getter.apply(rule).doubleValue();
                }
            }
        }
        return fallback;
    }

    private SignalSnapshot collectSignals(RawSnapshotDTO raw, ComputedSnapshotDTO computed) {
        SentimentStats sentiment = sentimentStats(raw == null ? null : raw.getSentimentDetail());
        IntentStats recommendation = intentStats(computed, "RECOMMENDATION");
        IntentStats comparison = intentStats(computed, "COMPARISON");
        IntentStats cognitive = intentStats(computed, "COGNITIVE");
        IntentStats inquiry = intentStats(computed, "INQUIRY");
        IntentStats scenario = intentStats(computed, "SCENARIO");
        CompetitorPressureStats pressure = competitorPressureStats(computed);
        boolean recommendationOvertake = pressure.suppressedSceneCount() > 0;
        boolean comparisonPreference = hasComparisonPreference(raw);
        boolean platformBlind = hasPlatformBlind(raw);
        return new SignalSnapshot(sentiment, recommendation, comparison, cognitive, inquiry, scenario,
                recommendationOvertake, comparisonPreference, platformBlind, pressure);
    }

    private NarrativeProfile.Archetype decidePrimaryArchetype(NarrativeProfile.Band band, SignalSnapshot signals) {
        if (signals.sentiment().trueNegative()) {
            return NarrativeProfile.Archetype.NEGATIVE_PRESSURE;
        }
        if (signals.recommendationCompetitorOvertake()) {
            return NarrativeProfile.Archetype.COMPETITOR_OVERTAKE;
        }
        if (signals.recommendation().avgRate() <= RECO_BLANK_RATE && signals.cognitive().avgScore() >= 50D) {
            return NarrativeProfile.Archetype.BRANDED_ONLY;
        }
        if (signals.platformBlind()) {
            return NarrativeProfile.Archetype.CHANNEL_BLIND;
        }
        if (band == NarrativeProfile.Band.STRONG || band == NarrativeProfile.Band.LEADER) {
            return NarrativeProfile.Archetype.LEADER_WITH_HOLES;
        }
        if (signals.recommendation().avgRate() < RECO_EMERGING_RATE || signals.comparison().avgScore() < 50D) {
            return NarrativeProfile.Archetype.DECISION_GAP;
        }
        return NarrativeProfile.Archetype.DECISION_GAP;
    }

    private List<NarrativeProfile.Archetype> decideSecondaryArchetypes(NarrativeProfile.Archetype primary,
                                                                       NarrativeProfile.Band band,
                                                                       SignalSnapshot signals) {
        List<NarrativeProfile.Archetype> out = new ArrayList<>();
        addSecondary(out, primary, signals.comparisonPreference()
                ? NarrativeProfile.Archetype.COMPETITOR_OVERTAKE : null);
        addSecondary(out, primary, signals.platformBlind() ? NarrativeProfile.Archetype.CHANNEL_BLIND : null);
        if ((band == NarrativeProfile.Band.STRONG || band == NarrativeProfile.Band.LEADER)
                && primary != NarrativeProfile.Archetype.LEADER_WITH_HOLES) {
            addSecondary(out, primary, NarrativeProfile.Archetype.LEADER_WITH_HOLES);
        }
        return out;
    }

    private void addSecondary(List<NarrativeProfile.Archetype> out,
                              NarrativeProfile.Archetype primary,
                              NarrativeProfile.Archetype candidate) {
        if (candidate != null && candidate != primary && !out.contains(candidate)) {
            out.add(candidate);
        }
    }

    private List<NarrativeProfile.FindingTier> buildFindingTiers(ComputedSnapshotDTO computed,
                                                                 SignalSnapshot signals,
                                                                 NarrativeProfile.Archetype primary,
                                                                 NarrativeProfile.Band band) {
        Map<String, NarrativeProfile.FindingTier> deduped = new LinkedHashMap<>();
        if (computed != null && computed.getOptimizationFindings() != null) {
            for (OptimizationFinding finding : computed.getOptimizationFindings()) {
                NarrativeProfile.FindingTier tier = fromRuleFinding(finding, primary);
                if ("NEGATIVE_PRESSURE".equals(tier.getDedupeKey()) && !signals.sentiment().trueNegative()) {
                    continue;
                }
                deduped.putIfAbsent(tier.getDedupeKey(), tier);
            }
        }
        addDerived(deduped, signals.recommendation().avgRate() <= RECO_BLANK_RATE,
                "RECO_ABSENT", "RECO_ABSENT", NarrativeProfile.FindingTierLevel.T1,
                NarrativeProfile.Archetype.DECISION_GAP, primary, 15, Map.of("recommendation_rate", signals.recommendation().avgRate()));
        addDerived(deduped, signals.recommendation().avgRate() <= RECO_BLANK_RATE && signals.cognitive().avgScore() >= 50D,
                "BRANDED_ONLY", "BRANDED_ONLY", NarrativeProfile.FindingTierLevel.T2,
                NarrativeProfile.Archetype.BRANDED_ONLY, primary, 25, Map.of("cognitive_score", signals.cognitive().avgScore()));
        addDerived(deduped, signals.sentiment().sentimentThin(),
                "SENTIMENT_THIN", "SENTIMENT_THIN", NarrativeProfile.FindingTierLevel.T2,
                NarrativeProfile.Archetype.DECISION_GAP, primary, 35, Map.of(
                        "neutral_share", signals.sentiment().neutralShare(),
                        "positive_share", signals.sentiment().positiveShare()));
        addDerived(deduped, signals.sentiment().trueNegative(),
                "NEGATIVE_PRESSURE", "NEGATIVE_PRESSURE", NarrativeProfile.FindingTierLevel.T1,
                NarrativeProfile.Archetype.NEGATIVE_PRESSURE, primary, 5, Map.of("negative_count", signals.sentiment().negativeCount()));
        addDerived(deduped, signals.platformBlind(),
                "PLATFORM_BLIND", "PLATFORM_BLIND", NarrativeProfile.FindingTierLevel.T2,
                NarrativeProfile.Archetype.CHANNEL_BLIND, primary, 45, Map.of());
        addDerived(deduped, signals.recommendationCompetitorOvertake(),
                "COMPETITOR_OVERTAKE_STRONG", "COMPETITOR_OVERTAKE", NarrativeProfile.FindingTierLevel.T1,
                NarrativeProfile.Archetype.COMPETITOR_OVERTAKE, primary, 8, Map.of("claim_type", "recommendation_presence"));
        addDerived(deduped, !signals.recommendationCompetitorOvertake() && signals.comparisonPreference(),
                "COMPETITOR_OVERTAKE_SOFT", "COMPETITOR_OVERTAKE", NarrativeProfile.FindingTierLevel.T2,
                NarrativeProfile.Archetype.COMPETITOR_OVERTAKE, primary, 38, Map.of("claim_type", "comparison_preference"));
        if (band == NarrativeProfile.Band.STRONG || band == NarrativeProfile.Band.LEADER) {
            addDerived(deduped, true, "COVERAGE_STRENGTH", "COVERAGE_STRENGTH",
                    NarrativeProfile.FindingTierLevel.STRENGTH, NarrativeProfile.Archetype.LEADER_WITH_HOLES,
                    primary, 80, Map.of());
            addDerived(deduped, signals.recommendation().avgRate() >= 50D, "RECO_STRENGTH", "RECO_STRENGTH",
                    NarrativeProfile.FindingTierLevel.STRENGTH, NarrativeProfile.Archetype.LEADER_WITH_HOLES,
                    primary, 85, Map.of("recommendation_rate", signals.recommendation().avgRate()));
        }

        return deduped.values().stream()
                .sorted(Comparator
                        .comparing((NarrativeProfile.FindingTier item) -> Boolean.TRUE.equals(item.getPrimaryArchetypeMatch()) ? 0 : 1)
                        .thenComparing(item -> item.getPriority() == null ? Integer.MAX_VALUE : item.getPriority()))
                .limit(5)
                .toList();
    }

    private NarrativeProfile.FindingTier fromRuleFinding(OptimizationFinding finding,
                                                         NarrativeProfile.Archetype primary) {
        String code = finding == null ? null : finding.getRuleCode();
        String dedupeKey = dedupeKey(code);
        NarrativeProfile.Archetype archetype = archetypeForCode(dedupeKey);
        return NarrativeProfile.FindingTier.builder()
                .source(NarrativeProfile.FindingSource.RULE)
                .code(code)
                .dedupeKey(dedupeKey)
                .tier(tierForPriority(finding == null ? null : finding.getPriority()))
                .priority(priorityValue(finding == null ? null : finding.getPriority()))
                .archetype(archetype)
                .primaryArchetypeMatch(archetype == primary)
                .evidence(finding == null ? null : finding.getEvidenceData())
                .build();
    }

    private void addDerived(Map<String, NarrativeProfile.FindingTier> out,
                            boolean enabled,
                            String code,
                            String dedupeKey,
                            NarrativeProfile.FindingTierLevel tier,
                            NarrativeProfile.Archetype archetype,
                            NarrativeProfile.Archetype primary,
                            int priority,
                            Map<String, Object> evidence) {
        if (!enabled || out.containsKey(dedupeKey)) {
            return;
        }
        out.put(dedupeKey, NarrativeProfile.FindingTier.builder()
                .source(tier == NarrativeProfile.FindingTierLevel.STRENGTH
                        ? NarrativeProfile.FindingSource.STRENGTH : NarrativeProfile.FindingSource.DERIVED)
                .code(code)
                .dedupeKey(dedupeKey)
                .tier(tier)
                .priority(priority)
                .archetype(archetype)
                .primaryArchetypeMatch(archetype == primary)
                .evidence(evidence)
                .build());
    }

    private NarrativeProfile.HeatmapPattern decideHeatmapPattern(ComputedSnapshotDTO computed) {
        IntentStats recommendation = intentStats(computed, "RECOMMENDATION");
        IntentStats inquiry = intentStats(computed, "INQUIRY");
        IntentStats scenario = intentStats(computed, "SCENARIO");
        double newCustomerAvg = averageSampledRates(recommendation, inquiry, scenario);
        if (newCustomerAvg <= RECO_BLANK_RATE) {
            return NarrativeProfile.HeatmapPattern.NEW_CUSTOMER_BLANK;
        }
        if (recommendation.maxMinGap() >= RECO_UNSTABLE_GAP) {
            return NarrativeProfile.HeatmapPattern.RECO_UNSTABLE;
        }
        if (recommendation.avgRate() < RECO_EMERGING_RATE) {
            return NarrativeProfile.HeatmapPattern.RECO_EMERGING;
        }
        return NarrativeProfile.HeatmapPattern.BROAD_PRESENCE;
    }

    private NarrativeProfile.DisplayFlags buildDisplayFlags(NarrativeProfile.Archetype primary,
                                                            SignalSnapshot signals,
                                                            NarrativeProfile.Band band) {
        NarrativeProfile.ComparisonMetric comparisonMetric = NarrativeProfile.ComparisonMetric.MENTION_RATE;
        if (signals.recommendationCompetitorOvertake()) {
            comparisonMetric = NarrativeProfile.ComparisonMetric.RECOMMENDATION_PRESENCE;
        } else if (signals.comparisonPreference()) {
            comparisonMetric = NarrativeProfile.ComparisonMetric.COMPARISON_PREFERENCE;
        }
        return NarrativeProfile.DisplayFlags.builder()
                .showNegativeBox(primary == NarrativeProfile.Archetype.NEGATIVE_PRESSURE && signals.sentiment().trueNegative())
                .showAdvantageBox((band == NarrativeProfile.Band.STRONG || band == NarrativeProfile.Band.LEADER)
                        && primary != NarrativeProfile.Archetype.NEGATIVE_PRESSURE)
                .comparisonMetric(comparisonMetric)
                .showRadarBaselineGap(true)
                .hideEmptyBlocks(true)
                .allowCompetitorOvertakeClaim(signals.recommendationCompetitorOvertake())
                .build();
    }

    private NarrativeProfile.CompetitorStory buildCompetitorStory(NarrativeProfile.Band band,
                                                                  CompetitorPressureStats pressure,
                                                                  NarrativeConfigService.IndustryLexicon lexicon) {
        NarrativeProfile.CompetitorStoryTier tier = decideCompetitorStoryTier(band, pressure);
        boolean competitorClaimAllowed = pressure.suppressedSceneCount() > 0;
        int absent = pressure.clientAbsentCount();
        int total = pressure.hvRecoTotal();
        String customer = lexicon != null && StringUtils.hasText(lexicon.getCustomerTerm())
                ? lexicon.getCustomerTerm() : "客户";
        String convert = lexicon != null && StringUtils.hasText(lexicon.getConversionTerm())
                ? lexicon.getConversionTerm() : "下单";
        String competitor = StringUtils.hasText(pressure.topSuppressingCompetitor())
                ? pressure.topSuppressingCompetitor() : "竞品";
        String title;
        String landing;
        switch (tier) {
            case T1 -> {
                if (competitorClaimAllowed) {
                    title = customer + "让 AI 推荐时,它报的是对手的名字——没有你。";
                    landing = "每一次,都是一位准备" + convert + "的" + customer + "——AI 把他指给了别人。";
                } else {
                    title = "在" + customer + "求推荐的 " + absent + "/" + total + " 个高价值场景里,AI 几乎没提到你。";
                    landing = "这些是已经在问\"哪家好\"、准备" + convert + "的" + customer + ";AI 还没把你列进去。";
                }
            }
            case T2 -> {
                if (competitorClaimAllowed) {
                    title = customer + "求推荐时,对手比你先被 AI 提到。";
                    landing = "这些是已经在比较选择的" + customer + ";对手在 "
                            + pressure.suppressedSceneCount() + " 个推荐型高价值场景被先提起,你还没稳定占住。";
                } else {
                    title = customer + "求推荐时,你在多数关键场景还没出现。";
                    landing = "这些是已经在问\"哪家好\"、准备" + convert + "的" + customer + ";AI 还没稳定把你列进去。";
                }
            }
            case T3 -> {
                if (competitorClaimAllowed) {
                    title = "少数场景对手抢先一步。";
                    landing = "大部分" + customer + "的求推荐场景你已在场;个别场景" + competitor + "先一步,补上更稳。";
                } else {
                    title = "个别" + customer + "求推荐场景你还没出现。";
                    landing = "大部分" + customer + "的求推荐场景你已在场;把剩余入口补上,推荐稳定性会更好。";
                }
            }
            default -> {
                title = "你已在多数" + customer + "求推荐场景出现。";
                landing = competitorClaimAllowed
                        ? "你已在多数推荐型高价值场景出现,盯住少数对手活跃的场景即可。"
                        : "你已在多数推荐型高价值场景出现,持续巩固即可。";
            }
        }
        try {
            validateCompetitorStory(tier, title, landing, competitorClaimAllowed, absent, total);
            return NarrativeProfile.CompetitorStory.builder()
                    .tier(tier)
                    .title(title)
                    .landingCopy(landing)
                    .suppressedSceneCount(pressure.suppressedSceneCount())
                    .hvRecoTotal(pressure.hvRecoTotal())
                    .clientAbsentCount(absent)
                    .absenceRatio(absenceRatio(absent, total))
                    .topSuppressingCompetitor(pressure.topSuppressingCompetitor())
                    .fallback(false)
                    .build();
        } catch (RuntimeException ex) {
            return NarrativeProfile.CompetitorStory.builder()
                    .tier(tier)
                    .title(absent > 0
                            ? "推荐型高价值场景还有可优化空间。"
                            : "你已在多数" + customer + "求推荐场景出现。")
                    .landingCopy(absent > 0
                            ? "建议结合下方真实推荐场景,优先补齐 AI 尚未稳定提及的入口。"
                            : "持续巩固 AI 已经能看到你的推荐入口即可。")
                    .suppressedSceneCount(pressure.suppressedSceneCount())
                    .hvRecoTotal(pressure.hvRecoTotal())
                    .clientAbsentCount(absent)
                    .absenceRatio(absenceRatio(absent, total))
                    .topSuppressingCompetitor(pressure.topSuppressingCompetitor())
                    .fallback(true)
                    .build();
        }
    }

    private NarrativeProfile.CompetitorStoryTier decideCompetitorStoryTier(NarrativeProfile.Band band,
                                                                          CompetitorPressureStats pressure) {
        int total = pressure.hvRecoTotal();
        int absent = pressure.clientAbsentCount();
        NarrativeProfile.CompetitorStoryTier ratioTier;
        if (total <= 0 || absent <= 0) {
            ratioTier = NarrativeProfile.CompetitorStoryTier.T4;
        } else {
            double ratio = absent * 1D / total;
            if (ratio >= 0.6D) {
                ratioTier = NarrativeProfile.CompetitorStoryTier.T1;
            } else if (ratio >= 0.3D) {
                ratioTier = NarrativeProfile.CompetitorStoryTier.T2;
            } else {
                ratioTier = NarrativeProfile.CompetitorStoryTier.T3;
            }
        }
        NarrativeProfile.CompetitorStoryTier ceiling = switch (band) {
            case LEADER -> NarrativeProfile.CompetitorStoryTier.T3;
            case STRONG -> NarrativeProfile.CompetitorStoryTier.T2;
            default -> NarrativeProfile.CompetitorStoryTier.T1;
        };
        return minSeverity(ratioTier, ceiling);
    }

    private NarrativeProfile.CompetitorStoryTier minSeverity(NarrativeProfile.CompetitorStoryTier candidate,
                                                            NarrativeProfile.CompetitorStoryTier ceiling) {
        return severity(candidate) >= severity(ceiling) ? candidate : ceiling;
    }

    private int severity(NarrativeProfile.CompetitorStoryTier tier) {
        return switch (tier) {
            case T1 -> 1;
            case T2 -> 2;
            case T3 -> 3;
            case T4 -> 4;
        };
    }

    private void validateCompetitorStory(NarrativeProfile.CompetitorStoryTier tier,
                                         String title,
                                         String landing,
                                         boolean competitorClaimAllowed,
                                         int absent,
                                         int total) {
        String text = (title == null ? "" : title) + "\n" + (landing == null ? "" : landing);
        if ((!competitorClaimAllowed || tier != NarrativeProfile.CompetitorStoryTier.T1)
                && text.matches("(?s).*(" + STRONG_COMPETITOR_CLAIM_PATTERN + ").*")) {
            throw new IllegalStateException("strong competitor story words outside T1");
        }
        if (absent > 0 && text.matches("(?s).*(已被 AI 看到|优先参考|你已被看到).*")) {
            throw new IllegalStateException("positive competitor story text while client is absent");
        }
        if (tier == NarrativeProfile.CompetitorStoryTier.T4 && total > 0 && absent > 0) {
            throw new IllegalStateException("T4 competitor story while client is absent");
        }
        if (text.contains("{{") || text.contains("}}") || text.matches("(?s).*\\bT[1-4]\\b.*")) {
            throw new IllegalStateException("competitor story placeholder or tier leaked");
        }
    }

    private double absenceRatio(int absent, int total) {
        if (total <= 0 || absent <= 0) {
            return 0D;
        }
        return absent * 1D / total;
    }

    private SentimentStats sentimentStats(SentimentDetail detail) {
        int positive = intValue(detail == null ? null : detail.getPositiveCount());
        int neutral = intValue(detail == null ? null : detail.getNeutralCount());
        int negative = intValue(detail == null ? null : detail.getNegativeCount());
        int total = positive + neutral + negative;
        double positiveShare = total <= 0 ? 0D : positive * 1D / total;
        double neutralShare = total <= 0 ? 0D : neutral * 1D / total;
        boolean hasNegativeKeyword = detail != null && detail.getTopKeywords() != null
                && detail.getTopKeywords().stream().anyMatch(item ->
                item != null && item.getSentiment() == SentimentDetail.Sentiment.NEGATIVE);
        boolean hasSnippet = detail != null && detail.getNegativeEvidence() != null
                && detail.getNegativeEvidence().stream().anyMatch(item ->
                item != null
                        && item.getSentiment() == SentimentDetail.Sentiment.NEGATIVE
                        && StringUtils.hasText(item.getSnippet()));
        boolean trueNegative = negative > 0 && hasNegativeKeyword && hasSnippet;
        boolean thin = total > 0 && neutralShare >= NEUTRAL_SHARE_THRESHOLD && !trueNegative;
        return new SentimentStats(positive, neutral, negative, positiveShare, neutralShare, trueNegative, thin);
    }

    private IntentStats intentStats(ComputedSnapshotDTO computed, String intentCode) {
        List<PlatformIntentCell> cells = computed == null || computed.getPlatformIntentBreakdown() == null
                ? List.of()
                : computed.getPlatformIntentBreakdown().stream()
                .filter(item -> item != null && intentCode.equals(item.getIntentCode()))
                .filter(item -> intValue(item.getPlatformPromptCount()) > 0 || intValue(item.getJudgeSampleCount()) > 0)
                .toList();
        if (cells.isEmpty()) {
            return new IntentStats(0D, 0D, 0D, 0);
        }
        List<Integer> rates = cells.stream()
                .map(PlatformIntentCell::getMentionRate)
                .filter(Objects::nonNull)
                .toList();
        List<Integer> scores = cells.stream()
                .map(PlatformIntentCell::getJudgeScore)
                .filter(Objects::nonNull)
                .toList();
        double avgRate = rates.isEmpty() ? 0D : rates.stream().mapToInt(Integer::intValue).average().orElse(0D);
        double avgScore = scores.isEmpty() ? avgRate : scores.stream().mapToInt(Integer::intValue).average().orElse(0D);
        double maxMinGap = rates.isEmpty() ? 0D : rates.stream().mapToInt(Integer::intValue).max().orElse(0)
                - rates.stream().mapToInt(Integer::intValue).min().orElse(0);
        return new IntentStats(avgRate, avgScore, maxMinGap, cells.size());
    }

    private CompetitorPressureStats competitorPressureStats(ComputedSnapshotDTO computed) {
        SceneCompetitorPressure pressure = computed == null ? null : computed.getSceneCompetitorPressure();
        int absent = pressure == null || pressure.getItems() == null
                ? 0
                : (int) pressure.getItems().stream()
                .filter(Objects::nonNull)
                .filter(item -> intValue(item.getTargetMentionedPlatformCount()) <= 0)
                .count();
        return new CompetitorPressureStats(
                intValue(pressure == null ? null : pressure.getSuppressedSceneCount()),
                intValue(pressure == null ? null : pressure.getHvRecoTotal()),
                absent,
                pressure == null ? null : pressure.getTopSuppressingCompetitor()
        );
    }

    private boolean hasComparisonPreference(RawSnapshotDTO raw) {
        List<Competitor> competitors = raw == null ? null : raw.getCompetitors();
        if (competitors == null) {
            return false;
        }
        return competitors.stream().filter(Objects::nonNull).anyMatch(item ->
                valueOrZero(item.getCompetitorPreferredRate()) >= 60D
                        || intValue(item.getCompetitorPreferredCount()) > intValue(item.getTargetPreferredCount()));
    }

    private boolean hasPlatformBlind(RawSnapshotDTO raw) {
        if (raw == null || raw.getPlatformBreakdown() == null) {
            return false;
        }
        return raw.getPlatformBreakdown().stream()
                .filter(item -> item != null && !Boolean.TRUE.equals(item.getIsDegraded()))
                .anyMatch(item -> valueOrZero(item.getMentionRate()) <= 0D);
    }

    private NarrativeProfile fallbackProfile(String configVersion, String reason) {
        return NarrativeProfile.builder()
                .profileVersion(PROFILE_VERSION)
                .configVersion(configVersion)
                .band(NarrativeProfile.Band.MIDDLE)
                .bandTone("neutral")
                .heatmapPattern(NarrativeProfile.HeatmapPattern.RECO_EMERGING)
                .displayFlags(NarrativeProfile.DisplayFlags.builder()
                        .showNegativeBox(false)
                        .showAdvantageBox(false)
                        .comparisonMetric(NarrativeProfile.ComparisonMetric.MENTION_RATE)
                        .showRadarBaselineGap(false)
                        .hideEmptyBlocks(true)
                        .allowCompetitorOvertakeClaim(false)
                        .build())
                .competitorStory(NarrativeProfile.CompetitorStory.builder()
                        .tier(NarrativeProfile.CompetitorStoryTier.T4)
                        .title("推荐型高价值场景还有可优化空间。")
                        .landingCopy("建议结合下方真实推荐场景,优先补齐 AI 尚未稳定提及的入口。")
                        .suppressedSceneCount(0)
                        .hvRecoTotal(0)
                        .clientAbsentCount(0)
                        .absenceRatio(0D)
                        .fallback(true)
                        .build())
                .lexiconFallback(true)
                .fallback(true)
                .fallbackReason(reason)
                .build();
    }

    private String dedupeKey(String code) {
        if (RuleCodes.RULE_COVERAGE_LOW_RECOMMEND.equals(code)
                || RuleCodes.RULE_SCENE_MISS_HIGH_VALUE.equals(code)) {
            return "HV_COVERAGE_LOW";
        }
        if (RuleCodes.RULE_PLATFORM_COVERAGE_NARROW.equals(code)
                || RuleCodes.RULE_PLATFORM_COUNT_LOW.equals(code)
                || RuleCodes.RULE_SINGLE_PLATFORM_DOMINANT.equals(code)
                || RuleCodes.RULE_PLATFORM_IMBALANCE.equals(code)) {
            return "PLATFORM_BLIND";
        }
        if (RuleCodes.RULE_NEGATIVE_EVIDENCE.equals(code)) {
            return "NEGATIVE_PRESSURE";
        }
        return code == null ? "UNKNOWN" : code;
    }

    private NarrativeProfile.Archetype archetypeForCode(String code) {
        if ("NEGATIVE_PRESSURE".equals(code)) return NarrativeProfile.Archetype.NEGATIVE_PRESSURE;
        if ("PLATFORM_BLIND".equals(code)) return NarrativeProfile.Archetype.CHANNEL_BLIND;
        if ("HV_COVERAGE_LOW".equals(code) || RuleCodes.RULE_COMPARE_GAP.equals(code)) {
            return NarrativeProfile.Archetype.DECISION_GAP;
        }
        return NarrativeProfile.Archetype.DECISION_GAP;
    }

    private NarrativeProfile.FindingTierLevel tierForPriority(OptimizationFinding.Priority priority) {
        if (priority == OptimizationFinding.Priority.HIGH) return NarrativeProfile.FindingTierLevel.T1;
        if (priority == OptimizationFinding.Priority.MEDIUM) return NarrativeProfile.FindingTierLevel.T2;
        return NarrativeProfile.FindingTierLevel.T3;
    }

    private int priorityValue(OptimizationFinding.Priority priority) {
        if (priority == OptimizationFinding.Priority.HIGH) return 20;
        if (priority == OptimizationFinding.Priority.MEDIUM) return 50;
        return 70;
    }

    private Map<String, Object> buildDiagnostics(BandDecision bandDecision, SignalSnapshot signals) {
        Map<String, Object> out = new HashMap<>();
        out.put("band_reason", bandDecision.reason());
        out.put("avg_ratio", bandDecision.avgRatio());
        out.put("top1_ratio", bandDecision.top1Ratio());
        out.put("true_negative", signals.sentiment().trueNegative());
        out.put("neutral_share", signals.sentiment().neutralShare());
        out.put("recommendation_rate", signals.recommendation().avgRate());
        out.put("recommendation_competitor_overtake", signals.recommendationCompetitorOvertake());
        out.put("suppressed_scene_count", signals.competitorPressure().suppressedSceneCount());
        out.put("hv_reco_total", signals.competitorPressure().hvRecoTotal());
        out.put("client_absent_count", signals.competitorPressure().clientAbsentCount());
        out.put("absence_ratio", absenceRatio(
                signals.competitorPressure().clientAbsentCount(),
                signals.competitorPressure().hvRecoTotal()));
        out.put("top_suppressing_competitor", signals.competitorPressure().topSuppressingCompetitor());
        out.put("comparison_preference", signals.comparisonPreference());
        return out;
    }

    private String bandTone(NarrativeProfile.Band band) {
        return switch (band) {
            case INVISIBLE, BEHIND -> "urgent";
            case MIDDLE -> "balanced";
            case STRONG, LEADER -> "positive";
        };
    }

    private double averageSampledRates(IntentStats... stats) {
        if (stats == null || stats.length == 0) return 0D;
        double sum = 0D;
        int count = 0;
        for (IntentStats stat : stats) {
            if (stat == null || stat.sampleCount() <= 0) {
                continue;
            }
            sum += stat.avgRate();
            count += 1;
        }
        return count == 0 ? 0D : sum / count;
    }

    private boolean positive(Double value) {
        return value != null && value > 0D;
    }

    private int intValue(Integer value) {
        return value == null ? 0 : value;
    }

    private double valueOrZero(Double value) {
        return value == null ? 0D : value;
    }

    private record BandDecision(NarrativeProfile.Band band, Double avgRatio, Double top1Ratio, String reason) {
    }

    private record SignalSnapshot(SentimentStats sentiment,
                                  IntentStats recommendation,
                                  IntentStats comparison,
                                  IntentStats cognitive,
                                  IntentStats inquiry,
                                  IntentStats scenario,
                                  boolean recommendationCompetitorOvertake,
                                  boolean comparisonPreference,
                                  boolean platformBlind,
                                  CompetitorPressureStats competitorPressure) {
    }

    private record SentimentStats(int positiveCount,
                                  int neutralCount,
                                  int negativeCount,
                                  double positiveShare,
                                  double neutralShare,
                                  boolean trueNegative,
                                  boolean sentimentThin) {
    }

    private record IntentStats(double avgRate, double avgScore, double maxMinGap, int sampleCount) {
    }

    private record CompetitorPressureStats(int suppressedSceneCount,
                                           int hvRecoTotal,
                                           int clientAbsentCount,
                                           String topSuppressingCompetitor) {
    }
}
