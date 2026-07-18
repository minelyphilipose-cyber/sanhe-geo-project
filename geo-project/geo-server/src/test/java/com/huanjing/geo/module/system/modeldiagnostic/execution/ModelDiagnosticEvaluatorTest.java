package com.huanjing.geo.module.system.modeldiagnostic.execution;

import com.huanjing.geo.module.dispatch.websearch.enums.BrandMatchStrength;
import com.huanjing.geo.module.dispatch.websearch.enums.CitationConfidence;
import com.huanjing.geo.module.dispatch.websearch.enums.SearchStatus;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchCitation;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchSource;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticCapabilityStatus;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticConclusion;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelDiagnosticEvaluatorTest {

    private final ModelDiagnosticEvaluator evaluator = new ModelDiagnosticEvaluator();

    @Test
    void basicChatMarksSearchCapabilitiesNotApplicable() {
        ModelDiagnosticEvaluation evaluation = evaluator.evaluate(
                ModelDiagnosticMode.BASIC_CHAT,
                result(SearchStatus.NOT_CONFIRMED, List.of(), List.of()));

        assertEquals(ModelDiagnosticConclusion.PASS, evaluation.conclusion());
        assertEquals(ModelDiagnosticCapabilityStatus.NOT_APPLICABLE, evaluation.webSearch());
        assertEquals(3, evaluation.promptTokens());
        assertEquals(5, evaluation.totalTokens());
    }

    @Test
    void successfulHttpWithoutConfirmedSearchIsDiagnosticFail() {
        ModelDiagnosticEvaluation evaluation = evaluator.evaluate(
                ModelDiagnosticMode.WEB_SEARCH,
                result(SearchStatus.NOT_CONFIRMED, List.of(), List.of()));

        assertEquals(ModelDiagnosticConclusion.FAIL, evaluation.conclusion());
        assertEquals(ModelDiagnosticCapabilityStatus.FAIL, evaluation.webSearch());
    }

    @Test
    void triggeredSearchWithValidSourceButNoConfirmedCitationIsWarning() {
        WebSearchSource source = new WebSearchSource(
                1, 1, "query", "title", "https://example.com/a", "https://example.com/a",
                "example.com", "snippet", null, BrandMatchStrength.NONE, List.of());
        WebSearchCitation probable = new WebSearchCitation(
                1, 1, null, null, "citation", CitationConfidence.PROBABLE, "INCOMPLETE");

        ModelDiagnosticEvaluation evaluation = evaluator.evaluate(
                ModelDiagnosticMode.WEB_SEARCH,
                result(SearchStatus.TRIGGERED, List.of(source), List.of(probable)));

        assertEquals(ModelDiagnosticConclusion.WARNING, evaluation.conclusion());
        assertEquals(1, evaluation.validSourceCount());
        assertEquals(0, evaluation.validCitationCount());
    }

    @Test
    void readsTokenHubWebSearchCallUsage() {
        ModelDiagnosticProviderResult providerResult = new ModelDiagnosticProviderResult(
                "request", "model", "answer", 200, SearchStatus.TRIGGERED,
                List.of(), List.of(), List.of(),
                Map.of("input_tokens", 33130,
                        "output_tokens", 793,
                        "tool_usage", Map.of("web_search_call", 3)),
                "completed", "{}", "{}", 10L);

        ModelDiagnosticEvaluation evaluation = evaluator.evaluate(
                ModelDiagnosticMode.WEB_SEARCH, providerResult);

        assertEquals(3, evaluation.webSearchCallCount());
        assertEquals(33130, evaluation.promptTokens());
    }

    private ModelDiagnosticProviderResult result(SearchStatus status,
                                                 List<WebSearchSource> sources,
                                                 List<WebSearchCitation> citations) {
        return new ModelDiagnosticProviderResult(
                "request", "model", "answer", 200, status,
                List.of(), sources, citations,
                Map.of("prompt_tokens", 3, "completion_tokens", 2),
                "stop", "{}", "{}", 10L);
    }
}
