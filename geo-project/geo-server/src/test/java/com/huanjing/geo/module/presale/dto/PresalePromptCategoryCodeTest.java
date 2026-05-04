package com.huanjing.geo.module.presale.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PresalePromptCategoryCodeTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserializesMapKeyFromCode() throws Exception {
        Map<PresalePromptCategoryCode, Integer> result = objectMapper.readValue(
                "{\"RECOMMENDATION\":2,\"COMPARISON\":1}",
                new TypeReference<>() {
                }
        );

        assertEquals(2, result.get(PresalePromptCategoryCode.RECOMMENDATION));
        assertEquals(1, result.get(PresalePromptCategoryCode.COMPARISON));
    }

    @Test
    void rejectsUnknownCode() {
        assertThrows(JsonProcessingException.class, () -> objectMapper.readValue(
                "{\"BAD_CATEGORY\":1}",
                new TypeReference<Map<PresalePromptCategoryCode, Integer>>() {
                }
        ));
    }
}
