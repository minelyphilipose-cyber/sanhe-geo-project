package com.huanjing.geo.module.presale.generate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.presale.dto.snapshot.computed.ComputedSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.computed.PlatformIntentCell;
import com.huanjing.geo.module.presale.dto.snapshot.raw.RawSnapshotDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

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
            JsonNode rawRoot = objectMapper.readTree(rawSnapshotJson);
            RawSnapshotDTO rawSnapshot = objectMapper.treeToValue(extractRawNode(rawRoot), RawSnapshotDTO.class);
            if (rawSnapshot == null) {
                throw new BizException(500, "platform_intent_breakdown integrity violated: raw_snapshot is null");
            }

            ObjectNode computedNode = extractComputedNode(rawRoot, computedSnapshotJson);
            ComputedSnapshotDTO computedSnapshot = objectMapper.treeToValue(computedNode, ComputedSnapshotDTO.class);
            if (computedSnapshot == null) {
                throw new BizException(500, "platform_intent_breakdown integrity violated: computed_snapshot is null");
            }

            List<PlatformIntentCell> cells = builder.build(
                    versionId, rawSnapshot, computedSnapshot, allowSyntheticFallback);
            computedSnapshot.setPlatformIntentBreakdown(cells);

            validator.validate(rawSnapshot.getPlatformBreakdown(), computedSnapshot.getIntentBreakdown(), cells);
            return objectMapper.writeValueAsString(computedSnapshot);
        } catch (BizException e) {
            throw e;
        } catch (JsonProcessingException e) {
            throw new BizException(500, "platform_intent_breakdown integrity violated: json parse failed - " + e.getMessage());
        }
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
        return out;
    }
}

