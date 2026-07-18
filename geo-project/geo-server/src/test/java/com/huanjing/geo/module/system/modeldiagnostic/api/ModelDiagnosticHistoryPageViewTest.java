package com.huanjing.geo.module.system.modeldiagnostic.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.system.modeldiagnostic.history.ModelDiagnosticHistoryPage;
import com.huanjing.geo.module.system.modeldiagnostic.history.ModelDiagnosticRunSummary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelDiagnosticHistoryPageViewTest {

    @Test
    void externalHistoryProjectionDoesNotExposeOperatorIdentity() throws Exception {
        ModelDiagnosticRunSummary summary = new ModelDiagnosticRunSummary();
        summary.setId(3L);
        summary.setOperatorId(99L);
        summary.setPlatformName("Platform");

        String json = new ObjectMapper().writeValueAsString(
                ModelDiagnosticHistoryPageView.from(
                        new ModelDiagnosticHistoryPage(List.of(summary), 1, 1, 20)));

        assertTrue(json.contains("\"platformName\":\"Platform\""));
        assertFalse(json.contains("operatorId"));
        assertFalse(json.contains("99"));
    }
}
