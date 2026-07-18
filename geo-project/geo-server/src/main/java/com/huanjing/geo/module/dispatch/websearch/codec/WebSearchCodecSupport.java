package com.huanjing.geo.module.dispatch.websearch.codec;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

final class WebSearchCodecSupport {

    private WebSearchCodecSupport() {
    }

    static Map<String, Object> asMap(ObjectMapper objectMapper, JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        return objectMapper.convertValue(node, new TypeReference<LinkedHashMap<String, Object>>() { });
    }

    static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText(null);
    }

    static Integer integer(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || !value.canConvertToInt() ? null : value.asInt();
    }

    static boolean validHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    static String domain(String value) {
        try {
            return URI.create(value).getHost();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    static int findCitationMarker(String answer, Integer index) {
        if (answer == null || index == null) {
            return -1;
        }
        int bracket = answer.indexOf("[" + index + "]");
        if (bracket >= 0) {
            return bracket;
        }
        return answer.indexOf("[ref_" + index + "]");
    }
}
