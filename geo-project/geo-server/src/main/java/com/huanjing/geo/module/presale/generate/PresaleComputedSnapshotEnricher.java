package com.huanjing.geo.module.presale.generate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.presale.dto.snapshot.computed.ComputedSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.computed.IntentBreakdown;
import com.huanjing.geo.module.presale.dto.snapshot.computed.PlatformIntentCell;
import com.huanjing.geo.module.presale.dto.snapshot.computed.PresaleIntentCode;
import com.huanjing.geo.module.presale.dto.snapshot.raw.RawSnapshotDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一 computed_snapshot 增强/校验入口。
 *
 * <p>mock/real 生成链路均应在写库前调用此入口。</p>
 */
@Component
@RequiredArgsConstructor
public class PresaleComputedSnapshotEnricher {

    private static final String[] COMPUTED_KEYS = {
            "scores", "intent_breakdown", "scene_coverage", "optimization_findings", "roi_simulation"
    };

    private final ObjectMapper objectMapper;
    private final PlatformIntentBreakdownBuilder builder;
    private final PlatformIntentBreakdownValidator validator;

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
            computedSnapshot.setIntentBreakdown(mergeIntentBreakdown(computedSnapshot.getIntentBreakdown(),
                    buildResult.intentTotalPrompts()));

            validator.validate(rawSnapshot.getPlatformBreakdown(), computedSnapshot.getIntentBreakdown(), cells);
            return objectMapper.writeValueAsString(computedSnapshot);
        } catch (BizException e) {
            throw e;
        } catch (JsonProcessingException e) {
            throw new BizException(500, "platform_intent_breakdown integrity violated: json parse failed - " + e.getMessage());
        }
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

    private List<IntentBreakdown> mergeIntentBreakdown(List<IntentBreakdown> existing,
                                                       Map<String, Integer> intentTotals) {
        Map<String, IntentBreakdown> byCode = new HashMap<>();
        List<IntentBreakdown> source = existing == null ? List.of() : existing;
        for (IntentBreakdown item : source) {
            if (item == null || item.getCategory() == null) {
                continue;
            }
            PresaleIntentCode intentCode = PresaleIntentCode.fromLabel(item.getCategory());
            byCode.put(intentCode.getCode(), item);
        }

        List<IntentBreakdown> merged = new ArrayList<>();
        for (PresaleIntentCode intentCode : PresaleIntentCode.allInOrder()) {
            IntentBreakdown item = byCode.get(intentCode.getCode());
            if (item == null) {
                item = new IntentBreakdown();
                item.setCategory(intentCode.getLabel());
                item.setBusinessValue(defaultBusinessValue(intentCode));
                item.setCoveredPrompts(0);
                item.setCoverageRate(0D);
                item.setAvgRanking(null);
            }
            item.setTotalPrompts(intentTotals.getOrDefault(intentCode.getCode(), 0));
            merged.add(item);
        }
        return merged;
    }

    private String defaultBusinessValue(PresaleIntentCode intentCode) {
        return switch (intentCode) {
            case RECOMMENDATION, COMPARISON -> "高";
            case INQUIRY, COGNITIVE -> "中";
            case SCENARIO -> "低";
        };
    }
}
