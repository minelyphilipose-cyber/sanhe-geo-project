package com.huanjing.geo.module.presale.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.presale.generate.llm.BenchmarkIndustryClassificationPromptTemplates;
import com.huanjing.geo.module.presale.generate.llm.LlmCallResult;
import com.huanjing.geo.module.presale.generate.llm.LlmInvokeException;
import com.huanjing.geo.module.presale.generate.llm.PlatformCallContext;
import com.huanjing.geo.module.presale.generate.llm.PresaleLlmInvoker;
import com.huanjing.geo.module.system.entity.SysDictItem;
import com.huanjing.geo.module.system.mapper.SysDictItemMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Resolves a report's benchmark industry once and freezes the result on its first version. */
@Slf4j
@Service
public class PresaleBenchmarkIndustryClassifier {

    static final String GLOBAL = "_ALL_";
    private static final String PLATFORM_QWEN = "qwen";
    private static final String DICT_TYPE = "presale_industry";

    private final SysDictItemMapper sysDictItemMapper;
    private final PresaleLlmInvoker llmInvoker;
    private final ObjectMapper objectMapper;

    public PresaleBenchmarkIndustryClassifier(SysDictItemMapper sysDictItemMapper,
                                              PresaleLlmInvoker llmInvoker,
                                              ObjectMapper objectMapper) {
        this.sysDictItemMapper = sysDictItemMapper;
        this.llmInvoker = llmInvoker;
        this.objectMapper = objectMapper;
    }

    public Classification classify(String rawIndustry, Long operatorUserId, boolean operatorIsManager) {
        List<SysDictItem> options = enabledIndustryOptions();
        SysDictItem direct = findDirectOption(rawIndustry, options);
        if (direct != null) {
            return new Classification(direct.getDictKey(), "DIRECT", "HIGH", null);
        }
        if (!StringUtils.hasText(rawIndustry) || options.isEmpty()) {
            return fallback();
        }

        try {
            String prompt = BenchmarkIndustryClassificationPromptTemplates.renderUserPrompt(
                    rawIndustry, objectMapper.writeValueAsString(options.stream()
                            .map(option -> Map.of("key", option.getDictKey(), "label", option.getDictValue()))
                            .toList()));
            LlmCallResult result = llmInvoker.classifyBenchmarkIndustry(
                    new PlatformCallContext(null, 0, PLATFORM_QWEN, null, "", "",
                            rawIndustry, "", List.of(), operatorUserId, operatorIsManager),
                    prompt);
            return parseResult(result, options);
        } catch (LlmInvokeException ex) {
            log.warn("Benchmark industry classification failed; global fallback used. industry={}", rawIndustry, ex);
            return fallback();
        } catch (Exception ex) {
            log.warn("Benchmark industry classification parse failed; global fallback used. industry={}", rawIndustry, ex);
            return fallback();
        }
    }

    /**
     * Resolves the synchronous portion of classification during report creation.
     *
     * <p>Direct dictionary matches do not need an LLM call. Manual input is deliberately
     * deferred to the generation worker so creating a report can return its ID immediately.</p>
     */
    public Classification classifyDirectlyOrDefer(String rawIndustry) {
        List<SysDictItem> options = enabledIndustryOptions();
        SysDictItem direct = findDirectOption(rawIndustry, options);
        if (direct != null) {
            return new Classification(direct.getDictKey(), "DIRECT", "HIGH", null);
        }
        if (!StringUtils.hasText(rawIndustry) || options.isEmpty()) {
            return fallback();
        }
        return pending();
    }

    public boolean requiresDeferredClassification(Classification classification) {
        return classification != null && "PENDING".equals(classification.source());
    }

    private Classification parseResult(LlmCallResult result, List<SysDictItem> options) throws Exception {
        JsonNode root = objectMapper.readTree(result.rawResponse());
        String key = text(root, "industry_key");
        String confidence = text(root, "confidence").toUpperCase(Locale.ROOT);
        boolean validConfidence = "HIGH".equals(confidence) || "MEDIUM".equals(confidence) || "LOW".equals(confidence);
        boolean allowedKey = options.stream().anyMatch(option -> option.getDictKey().equals(key));
        if (!validConfidence || !allowedKey || "LOW".equals(confidence)) {
            return fallback();
        }
        return new Classification(key, "LLM", confidence,
                result.modelId() == null ? result.modelName() : result.modelId());
    }

    private List<SysDictItem> enabledIndustryOptions() {
        List<SysDictItem> rows = sysDictItemMapper.selectList(new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getDictType, DICT_TYPE)
                .eq(SysDictItem::getEnabled, true)
                .orderByAsc(SysDictItem::getSortOrder)
                .orderByAsc(SysDictItem::getId));
        return rows == null ? List.of() : rows.stream()
                .filter(row -> StringUtils.hasText(row.getDictKey()) && StringUtils.hasText(row.getDictValue()))
                .toList();
    }

    private SysDictItem findDirectOption(String rawIndustry, List<SysDictItem> options) {
        String normalized = normalize(rawIndustry);
        if (!StringUtils.hasText(normalized)) return null;
        return options.stream().filter(option -> normalized.equals(normalize(option.getDictKey()))
                        || normalized.equals(normalize(option.getDictValue())))
                .findFirst().orElse(null);
    }

    private Classification fallback() {
        return new Classification(GLOBAL, "FALLBACK", "LOW", null);
    }

    private Classification pending() {
        return new Classification(null, "PENDING", null, null);
    }

    private String text(JsonNode root, String field) {
        JsonNode value = root == null ? null : root.get(field);
        return value != null && value.isTextual() ? value.asText().trim() : "";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replace(" ", "").replace("　", "").toLowerCase(Locale.ROOT);
    }

    public record Classification(String benchmarkIndustryKey,
                                 String source,
                                 String confidence,
                                 String modelId) {
    }
}
