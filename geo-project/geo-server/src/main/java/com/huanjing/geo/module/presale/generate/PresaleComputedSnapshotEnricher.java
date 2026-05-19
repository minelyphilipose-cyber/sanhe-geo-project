package com.huanjing.geo.module.presale.generate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.presale.dto.snapshot.computed.ComputedSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.computed.PlatformIntentCell;
import com.huanjing.geo.module.presale.dto.snapshot.raw.RawSnapshotDTO;
import com.huanjing.geo.module.presale.generate.calc.RankingStats;
import com.huanjing.geo.module.presale.generate.calc.RoiCalculator;
import com.huanjing.geo.module.presale.generate.calc.SceneAndIntentResult;
import com.huanjing.geo.module.presale.generate.calc.SceneCoverageCalculator;
import com.huanjing.geo.module.presale.generate.calc.ScoresCalculator;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiPromptResult;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiPromptResultMapper;
import com.huanjing.geo.module.presale.ruleengine.PresaleRuleEngineExecutor;
import com.huanjing.geo.module.presale.ruleengine.RuleEngineResult;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 统一 computed_snapshot 增强/校验入口。
 *
 * <p>mock/real 生成链路均应在写库前调用此入口。</p>
 */
@Component
@RequiredArgsConstructor
public class PresaleComputedSnapshotEnricher {

    private static final Logger log = LoggerFactory.getLogger(PresaleComputedSnapshotEnricher.class);

    private static final String[] COMPUTED_KEYS = {
            "scores", "intent_breakdown", "scene_coverage", "optimization_findings", "roi_simulation"
    };

    private final ObjectMapper objectMapper;
    private final PlatformIntentBreakdownBuilder builder;
    private final PlatformIntentBreakdownValidator validator;
    private final SceneCoverageCalculator sceneCoverageCalculator;
    private final ScoresCalculator scoresCalculator;
    private final PresaleRuleEngineExecutor ruleEngineExecutor;
    private final RoiCalculator roiCalculator;
    private final PresaleAiPromptResultMapper aiPromptResultMapper;

    public String enrichAndValidate(Long versionId,
                                    String rawSnapshotJson,
                                    String computedSnapshotJson,
                                    boolean allowSyntheticFallback) {
        try {
            JsonNode rawRoot = objectMapper.readTree(stripBom(rawSnapshotJson));
            JsonNode effectiveRoot = unwrapInputNode(rawRoot);
            RawSnapshotDTO rawSnapshot = objectMapper.treeToValue(extractRawNode(effectiveRoot), RawSnapshotDTO.class);
            if (rawSnapshot == null) {
                throw new BizException(500, "platform_intent_breakdown integrity violated: raw_snapshot is null");
            }

            ObjectNode computedNode = extractComputedNode(effectiveRoot, computedSnapshotJson);
            ComputedSnapshotDTO computedSnapshot = objectMapper.treeToValue(computedNode, ComputedSnapshotDTO.class);
            if (computedSnapshot == null) {
                throw new BizException(500, "platform_intent_breakdown integrity violated: computed_snapshot is null");
            }

            PlatformIntentBreakdownBuilder.BuildResult buildResult = builder.build(
                    versionId, rawSnapshot, computedSnapshot, allowSyntheticFallback);
            List<PlatformIntentCell> cells = buildResult.cells();
            computedSnapshot.setPlatformIntentBreakdown(cells);

            // 1) Scene coverage + intent breakdown (同源)
            SceneAndIntentResult scenes = sceneCoverageCalculator.compute(
                    versionId, rawSnapshot, buildResult.intentTotalPrompts(), cells);
            computedSnapshot.setSceneCoverage(scenes.sceneCoverage());
            computedSnapshot.setIntentBreakdown(scenes.intentBreakdown());

            // 2) Scores (D25)
            RankingStats rankingStats = queryRankingStats(versionId, rawSnapshot);
            var scores = scoresCalculator.compute(rawSnapshot, scenes, rankingStats);
            computedSnapshot.setScores(scores);

            // 3) Rule engine (必须在 scores/sceneCoverage 就绪后调用)
            RuleEngineResult ruleResult = ruleEngineExecutor.execute(rawSnapshot, computedSnapshot);
            computedSnapshot.setOptimizationFindings(ruleResult.getFindings());
            if (ruleResult.getFindings() == null || ruleResult.getFindings().isEmpty()) {
                log.warn("No optimization rule triggered for versionId={}, this may indicate a perfect-brand scenario or a rule threshold anomaly",
                        versionId);
            }

            // 4) ROI (D27/D28/D30)
            computedSnapshot.setRoiSimulation(roiCalculator.compute(
                    scores == null ? null : scores.getOverall(),
                    ruleResult.getFindings()
            ));

            // 5) Existing integrity validator
            validator.validate(rawSnapshot.getPlatformBreakdown(), computedSnapshot.getIntentBreakdown(), cells);
            return objectMapper.writeValueAsString(computedSnapshot);
        } catch (BizException e) {
            throw e;
        } catch (JsonProcessingException e) {
            throw new BizException(500, "platform_intent_breakdown integrity violated: json parse failed - " + e.getMessage());
        }
    }

    private RankingStats queryRankingStats(Long versionId, RawSnapshotDTO rawSnapshot) {
        LambdaQueryWrapper<PresaleAiPromptResult> wrapper = new LambdaQueryWrapper<PresaleAiPromptResult>()
                .eq(PresaleAiPromptResult::getVersionId, versionId)
                .eq(PresaleAiPromptResult::getBatchNo, 1)
                .isNotNull(PresaleAiPromptResult::getRanking);
        Set<String> degradedPlatforms = rawSnapshot == null
                || rawSnapshot.getTestSummary() == null
                || rawSnapshot.getTestSummary().getDegradedPlatforms() == null
                ? Set.of()
                : Set.copyOf(rawSnapshot.getTestSummary().getDegradedPlatforms());
        if (!degradedPlatforms.isEmpty()) {
            wrapper.notIn(PresaleAiPromptResult::getPlatformCode, degradedPlatforms);
        }
        List<PresaleAiPromptResult> rows = aiPromptResultMapper.selectList(wrapper);
        int c1 = 0;
        int c2 = 0;
        int c3 = 0;
        int c4 = 0;
        int c5 = 0;
        int c6 = 0;
        for (PresaleAiPromptResult row : rows == null ? List.<PresaleAiPromptResult>of() : rows) {
            Integer ranking = row.getRanking();
            if (ranking == null) {
                continue;
            }
            if (ranking == 1) {
                c1++;
            } else if (ranking == 2) {
                c2++;
            } else if (ranking == 3) {
                c3++;
            } else if (ranking == 4) {
                c4++;
            } else if (ranking == 5) {
                c5++;
            } else {
                c6++;
            }
        }
        return new RankingStats(c1, c2, c3, c4, c5, c6);
    }

    private String stripBom(String json) {
        if (json != null && !json.isEmpty() && json.charAt(0) == '\uFEFF') {
            return json.substring(1);
        }
        return json;
    }

    private JsonNode unwrapInputNode(JsonNode root) {
        if (root != null && root.has("input") && root.get("input").isObject()) {
            return root.get("input");
        }
        return root;
    }

    private JsonNode extractRawNode(JsonNode root) {
        if (root != null && root.has("raw") && root.get("raw").isObject()) {
            return root.get("raw");
        }
        return root;
    }

    private ObjectNode extractComputedNode(JsonNode rawRoot, String computedSnapshotJson) throws JsonProcessingException {
        JsonNode computedSource = objectMapper.readTree(computedSnapshotJson == null || computedSnapshotJson.isBlank()
                ? "{}" : computedSnapshotJson);
        if (computedSource != null && computedSource.has("computed") && computedSource.get("computed").isObject()) {
            computedSource = computedSource.get("computed");
        }

        ObjectNode out = objectMapper.createObjectNode();
        if (computedSource != null && computedSource.isObject()) {
            out.setAll((ObjectNode) computedSource);
        }
        for (String key : COMPUTED_KEYS) {
            if (!out.has(key) && rawRoot != null && rawRoot.has(key)) {
                out.set(key, rawRoot.get(key));
            }
        }
        if (!out.has("intent_breakdown") && rawRoot != null
                && rawRoot.has("computed") && rawRoot.get("computed").isObject()
                && rawRoot.get("computed").has("intent_breakdown")) {
            out.set("intent_breakdown", rawRoot.get("computed").get("intent_breakdown"));
        }
        for (String key : COMPUTED_KEYS) {
            if (!out.has(key) && rawRoot != null
                    && rawRoot.has("computed") && rawRoot.get("computed").isObject()
                    && rawRoot.get("computed").has(key)) {
                out.set(key, rawRoot.get("computed").get(key));
            }
        }
        return out;
    }
}
