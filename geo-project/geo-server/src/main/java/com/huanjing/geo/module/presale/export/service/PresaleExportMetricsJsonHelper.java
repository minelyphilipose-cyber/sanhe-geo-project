package com.huanjing.geo.module.presale.export.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class PresaleExportMetricsJsonHelper {
    private final ObjectMapper objectMapper;

    public String appendRetryHistory(String currentJson, RetryHistoryEntry entry) {
        try {
            ObjectNode root = readRoot(currentJson);
            JsonNode existing = root.get("retry_history");
            ArrayNode retryHistory;
            if (existing instanceof ArrayNode arrayNode) {
                retryHistory = arrayNode;
            } else {
                retryHistory = objectMapper.createArrayNode();
                root.set("retry_history", retryHistory);
            }

            ObjectNode item = objectMapper.createObjectNode();
            item.put("error_code", entry.errorCode());
            if (entry.errorMsg() != null) {
                item.put("error_msg", entry.errorMsg());
            }
            if (entry.retryCount() != null) {
                item.put("retry_count", entry.retryCount());
            }
            item.put("at", entry.at() == null ? LocalDateTime.now().toString() : entry.at().toString());
            retryHistory.add(item);
            return objectMapper.writeValueAsString(root);
        } catch (Exception ex) {
            throw new IllegalStateException("Append presale export metrics failed", ex);
        }
    }

    public String mergeRenderMetrics(String currentJson, String renderMetricsJson) {
        try {
            ObjectNode renderRoot = readRoot(renderMetricsJson);
            ObjectNode currentRoot = readRoot(currentJson);
            JsonNode retryHistory = currentRoot.get("retry_history");
            if (retryHistory instanceof ArrayNode && retryHistory.size() > 0) {
                renderRoot.set("retry_history", retryHistory);
            }
            return objectMapper.writeValueAsString(renderRoot);
        } catch (Exception ex) {
            throw new IllegalStateException("Merge presale export metrics failed", ex);
        }
    }

    private ObjectNode readRoot(String currentJson) throws Exception {
        if (!StringUtils.hasText(currentJson)) {
            return objectMapper.createObjectNode();
        }
        JsonNode parsed;
        try {
            parsed = objectMapper.readTree(currentJson);
        } catch (Exception ex) {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("previous_metrics_parse_failed", true);
            return root;
        }
        if (parsed instanceof ObjectNode objectNode) {
            return objectNode;
        }
        ObjectNode root = objectMapper.createObjectNode();
        root.set("previous_metrics", parsed);
        return root;
    }

    @Builder
    public record RetryHistoryEntry(
            String errorCode,
            String errorMsg,
            Integer retryCount,
            LocalDateTime at
    ) {
    }
}
