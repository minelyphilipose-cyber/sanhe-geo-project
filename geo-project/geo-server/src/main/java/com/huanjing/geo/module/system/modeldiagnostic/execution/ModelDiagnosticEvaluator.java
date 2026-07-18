package com.huanjing.geo.module.system.modeldiagnostic.execution;

import com.huanjing.geo.module.dispatch.websearch.enums.CitationConfidence;
import com.huanjing.geo.module.dispatch.websearch.enums.SearchStatus;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchSource;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticCapabilityStatus;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticConclusion;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticMode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Map;

@Component
public class ModelDiagnosticEvaluator {

    public static final String EVALUATOR_VERSION = "model-diagnostic-evaluator-v1";

    public ModelDiagnosticEvaluation evaluate(ModelDiagnosticMode mode,
                                              ModelDiagnosticProviderResult result) {
        int sourceCount = result.sources().size();
        int validSourceCount = (int) result.sources().stream().filter(this::validSource).count();
        int citationCount = result.citations().size();
        int validCitationCount = (int) result.citations().stream()
                .filter(citation -> citation.confidence() == CitationConfidence.CONFIRMED)
                .count();
        ModelDiagnosticCapabilityStatus generation = StringUtils.hasText(result.answer())
                ? ModelDiagnosticCapabilityStatus.PASS : ModelDiagnosticCapabilityStatus.FAIL;

        ModelDiagnosticCapabilityStatus webSearch;
        ModelDiagnosticCapabilityStatus sourceParsing;
        ModelDiagnosticCapabilityStatus citationParsing;
        if (mode == ModelDiagnosticMode.BASIC_CHAT) {
            webSearch = ModelDiagnosticCapabilityStatus.NOT_APPLICABLE;
            sourceParsing = ModelDiagnosticCapabilityStatus.NOT_APPLICABLE;
            citationParsing = ModelDiagnosticCapabilityStatus.NOT_APPLICABLE;
        } else {
            CapabilitySet set = webSearchCapabilities(
                    result.searchStatus(), validSourceCount, validCitationCount);
            webSearch = set.webSearch();
            sourceParsing = set.sourceParsing();
            citationParsing = set.citationParsing();
        }

        ModelDiagnosticConclusion conclusion = worst(
                ModelDiagnosticCapabilityStatus.PASS,
                generation, webSearch, sourceParsing, citationParsing);
        Map<String, Object> usage = result.usage();
        Integer promptTokens = integer(usage, "prompt_tokens", "input_tokens");
        Integer completionTokens = integer(usage, "completion_tokens", "output_tokens");
        Integer totalTokens = integer(usage, "total_tokens");
        if (totalTokens == null && promptTokens != null && completionTokens != null) {
            totalTokens = promptTokens + completionTokens;
        }

        return new ModelDiagnosticEvaluation(
                conclusion,
                reason(conclusion, result.searchStatus()),
                ModelDiagnosticCapabilityStatus.PASS,
                generation,
                webSearch,
                sourceParsing,
                citationParsing,
                promptTokens,
                completionTokens,
                totalTokens,
                webSearchCalls(usage),
                sourceCount,
                validSourceCount,
                citationCount,
                validCitationCount
        );
    }

    private CapabilitySet webSearchCapabilities(SearchStatus status,
                                                int validSources,
                                                int validCitations) {
        return switch (status) {
            case NOT_CONFIRMED, FAILED -> new CapabilitySet(
                    ModelDiagnosticCapabilityStatus.FAIL,
                    ModelDiagnosticCapabilityStatus.NOT_APPLICABLE,
                    ModelDiagnosticCapabilityStatus.NOT_APPLICABLE);
            case EMPTY -> new CapabilitySet(
                    ModelDiagnosticCapabilityStatus.PASS,
                    ModelDiagnosticCapabilityStatus.WARNING,
                    ModelDiagnosticCapabilityStatus.NOT_APPLICABLE);
            case NO_VALID_SOURCE -> new CapabilitySet(
                    ModelDiagnosticCapabilityStatus.PASS,
                    ModelDiagnosticCapabilityStatus.FAIL,
                    ModelDiagnosticCapabilityStatus.NOT_APPLICABLE);
            case TRIGGERED -> new CapabilitySet(
                    ModelDiagnosticCapabilityStatus.PASS,
                    validSources > 0 ? ModelDiagnosticCapabilityStatus.PASS
                            : ModelDiagnosticCapabilityStatus.FAIL,
                    validCitations > 0 ? ModelDiagnosticCapabilityStatus.PASS
                            : ModelDiagnosticCapabilityStatus.WARNING);
        };
    }

    private ModelDiagnosticConclusion worst(ModelDiagnosticCapabilityStatus... statuses) {
        boolean warning = false;
        for (ModelDiagnosticCapabilityStatus status : statuses) {
            if (status == ModelDiagnosticCapabilityStatus.FAIL) {
                return ModelDiagnosticConclusion.FAIL;
            }
            if (status == ModelDiagnosticCapabilityStatus.WARNING) {
                warning = true;
            }
        }
        return warning ? ModelDiagnosticConclusion.WARNING : ModelDiagnosticConclusion.PASS;
    }

    private boolean validSource(WebSearchSource source) {
        try {
            URI uri = URI.create(source.normalizedUrl());
            return ("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private Integer integer(Map<String, Object> usage, String... keys) {
        for (String key : keys) {
            Object value = usage.get(key);
            if (value instanceof Number number && number.longValue() >= 0
                    && number.longValue() <= Integer.MAX_VALUE) {
                return number.intValue();
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Integer webSearchCalls(Map<String, Object> usage) {
        Integer direct = integer(usage, "web_search_call_count", "web_search_calls");
        if (direct != null) {
            return direct;
        }
        Object toolUsage = usage.get("tool_usage");
        if (toolUsage instanceof Map<?, ?> map) {
            Object count = map.containsKey("web_search_call")
                    ? map.get("web_search_call") : map.get("web_search");
            if (count instanceof Number number) {
                return Math.max(0, number.intValue());
            }
        }
        return null;
    }

    private String reason(ModelDiagnosticConclusion conclusion, SearchStatus status) {
        if (conclusion == ModelDiagnosticConclusion.PASS) {
            return "All applicable diagnostic capabilities passed";
        }
        if (status == SearchStatus.NOT_CONFIRMED) {
            return "Provider completed the request but web search was not confirmed";
        }
        if (status == SearchStatus.NO_VALID_SOURCE) {
            return "Web search ran but no valid source could be parsed";
        }
        return conclusion == ModelDiagnosticConclusion.WARNING
                ? "The request completed with capability warnings"
                : "At least one required diagnostic capability failed";
    }

    private record CapabilitySet(ModelDiagnosticCapabilityStatus webSearch,
                                 ModelDiagnosticCapabilityStatus sourceParsing,
                                 ModelDiagnosticCapabilityStatus citationParsing) {
    }
}
