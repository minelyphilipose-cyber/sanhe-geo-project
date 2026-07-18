package com.huanjing.geo.module.system.modeldiagnostic.api;

import com.huanjing.geo.module.dispatch.websearch.QuestionPollPromptTemplate;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticMode;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticTestMode;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionPollPromptTemplateContractTest {

    @Test
    void diagnosticAndProductionDispatchUseTheSameVersionedTemplate() throws Exception {
        ModelDiagnosticProbeCatalog.ProbeDefinition probe =
                new ModelDiagnosticProbeCatalog().require(
                        "production_poll_question", ModelDiagnosticMode.WEB_SEARCH,
                        ModelDiagnosticTestMode.PRODUCTION_POLL_TEMPLATE);

        assertEquals(QuestionPollPromptTemplate.VERSION, probe.templateVersion());
        assertEquals(QuestionPollPromptTemplate.SYSTEM_PROMPT, probe.systemPrompt());

        String dispatchSource = Files.readString(Path.of(
                "src/main/java/com/huanjing/geo/module/dispatch/service/DispatchExecutionService.java"));
        assertTrue(dispatchSource.contains("QuestionPollPromptTemplate.SYSTEM_PROMPT"));
        assertFalse(dispatchSource.contains("\"" + QuestionPollPromptTemplate.SYSTEM_PROMPT + "\""));
    }
}
