package com.huanjing.geo.module.content.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JsonColumnPayloadsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void normalizeKeepsValidJsonPayload() {
        String payload = "{\"code\":201,\"message\":\"Invalid value.\"}";

        assertEquals(payload, JsonColumnPayloads.normalize(objectMapper, payload));
    }

    @Test
    void normalizeWrapsPlainTextPayloadAsJsonObject() throws Exception {
        String normalized = JsonColumnPayloads.normalize(objectMapper, "Invalid value.");

        JsonNode root = objectMapper.readTree(normalized);
        assertEquals("Invalid value.", root.path("raw").asText());
    }

    @Test
    void normalizeReturnsNullForBlankPayload() {
        assertNull(JsonColumnPayloads.normalize(objectMapper, "   "));
    }
}
