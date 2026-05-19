package com.huanjing.geo.module.content.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.Map;

final class JsonColumnPayloads {

    private JsonColumnPayloads() {
    }

    static String normalize(ObjectMapper objectMapper, String payload) {
        if (!StringUtils.hasText(payload)) {
            return null;
        }
        String text = payload.trim();
        try {
            objectMapper.readTree(text);
            return text;
        } catch (Exception ignored) {
            return rawPayload(objectMapper, text);
        }
    }

    private static String rawPayload(ObjectMapper objectMapper, String text) {
        try {
            return objectMapper.writeValueAsString(Map.of("raw", text));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize JSON column payload", ex);
        }
    }
}
