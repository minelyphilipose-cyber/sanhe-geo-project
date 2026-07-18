package com.huanjing.geo.module.system.modeldiagnostic.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.system.modeldiagnostic.entity.AiModelDiagnosticRun;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelDiagnosticRunViewMapperTest {

    private final ModelDiagnosticRunViewMapper mapper =
            new ModelDiagnosticRunViewMapper(new ObjectMapper());

    @Test
    void mapsNullableCapabilitiesAndOnlySanitizedProtocolPayloads() {
        AiModelDiagnosticRun run = new AiModelDiagnosticRun();
        run.setId(9L);
        run.setAuthenticationStatus("PASS");
        run.setResponseMode("SYNC");
        run.setPromptTokens(7);
        run.setCompletionTokens(5);
        run.setTotalTokens(12);
        run.setWebSearchCallCount(1);
        run.setSourcesJson("[{\"url\":\"https://example.com\"}]");
        run.setCitationsJson("invalid-json");
        run.setUsageJson("{\"total_tokens\":12}");
        run.setSanitizedRequest("{\"authorization\":\"***\"}");
        run.setSanitizedResponse("{\"answer\":\"ok\"}");

        ModelDiagnosticRunView view = mapper.toView(run);

        assertEquals("PASS", view.capabilities().get("authentication"));
        assertNull(view.capabilities().get("generation"));
        assertEquals("https://example.com", view.sources().get(0).get("url").asText());
        assertTrue(view.citations().isArray());
        assertEquals(0, view.citations().size());
        assertEquals(12, view.usage().get("total_tokens").asInt());
        assertEquals("SYNC", view.responseMode());
        assertEquals(7, view.promptTokens());
        assertEquals(5, view.completionTokens());
        assertEquals(12, view.totalTokens());
        assertEquals(1, view.webSearchCallCount());
        assertEquals("{\"authorization\":\"***\"}", view.sanitizedRequest());
        assertThrows(UnsupportedOperationException.class,
                () -> view.capabilities().put("generation", "PASS"));
    }
}
