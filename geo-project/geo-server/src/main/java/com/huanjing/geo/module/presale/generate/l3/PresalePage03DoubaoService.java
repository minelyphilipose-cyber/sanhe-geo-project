package com.huanjing.geo.module.presale.generate.l3;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.llm.pool.LlmPermitUnavailableException;
import com.huanjing.geo.module.presale.dto.snapshot.editable.EditableContentDTO;
import com.huanjing.geo.module.presale.dto.snapshot.editable.MarketBattleground;
import com.huanjing.geo.module.presale.dto.snapshot.raw.ClientInfo;
import com.huanjing.geo.module.presale.dto.snapshot.raw.RawSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.raw.SamplePrompt;
import com.huanjing.geo.module.presale.generate.llm.LlmCallResult;
import com.huanjing.geo.module.presale.generate.llm.LlmInvokeException;
import com.huanjing.geo.module.presale.generate.llm.MarketBattlegroundPromptTemplates;
import com.huanjing.geo.module.presale.generate.llm.PlatformCallContext;
import com.huanjing.geo.module.presale.generate.llm.PresaleLlmInvoker;
import com.huanjing.geo.module.presale.generate.PresaleEvaluationModelRouter;
import com.huanjing.geo.module.presale.service.PresalePage03MarketConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Page03 市场战场页使用售前评估模型池生成,后端负责结构校验与数值重算。
 */
@Service
@RequiredArgsConstructor
public class PresalePage03DoubaoService {

    private static final int BATCH_NO_PAGE03 = 3;

    private final ObjectMapper objectMapper;
    private final PresaleLlmInvoker llmInvoker;
    private final PresaleL3Defaults l3Defaults;
    private final MarketBattlegroundValidator marketBattlegroundValidator;
    private final PresaleEvaluationModelRouter evaluationModelRouter;
    private final PresalePage03MarketConfigService page03MarketConfigService;

    public String generateAndApply(Long versionId,
                                   String rawSnapshotJson,
                                   String editableContentJson,
                                   Long operatorUserId,
                                   boolean operatorIsManager) {
        try {
            RawSnapshotDTO raw = objectMapper.readValue(rawSnapshotJson, RawSnapshotDTO.class);
            EditableContentDTO editable = objectMapper.readValue(editableContentJson, EditableContentDTO.class);
            String prompt = MarketBattlegroundPromptTemplates.renderUserPrompt(buildPromptInputJson(raw));
            String brandName = raw.getClientInfo() == null ? null : raw.getClientInfo().getBrandName();
            PlatformCallContext sourceCtx = new PlatformCallContext(
                    versionId,
                    BATCH_NO_PAGE03,
                    "",
                    null,
                    "",
                    brandName,
                    operatorUserId,
                    operatorIsManager
            );

            LlmCallResult result = marketBattlegroundWithEvaluationModel(sourceCtx, prompt);
            JsonNode aiNode = objectMapper.readTree(result.rawResponse());
            MarketBattleground market = buildMarketPatch(raw, aiNode);

            editable.setMarketBattleground(market);
            EditableContentDTO normalized = l3Defaults.normalizeGenerated(editable, raw, null);
            marketBattlegroundValidator.validate(normalized.getMarketBattleground());
            return objectMapper.writeValueAsString(normalized);
        } catch (LlmInvokeException ex) {
            throw new BizException(500, "Page03 Doubao generation failed: " + ex.getMessage());
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new BizException(500, "Page03 Doubao output invalid: " + ex.getMessage());
        }
    }

    private LlmCallResult marketBattlegroundWithEvaluationModel(PlatformCallContext sourceCtx, String prompt)
            throws LlmInvokeException {
        List<PlatformCallContext> candidates = evaluationModelRouter.routeContexts(sourceCtx);
        if (candidates.isEmpty()) {
            throw new LlmInvokeException("No presale evaluation model enabled");
        }
        LlmPermitUnavailableException lastBusy = null;
        for (PlatformCallContext candidate : candidates) {
            try {
                return llmInvoker.marketBattleground(candidate, prompt);
            } catch (LlmPermitUnavailableException ex) {
                lastBusy = ex;
            }
        }
        throw new LlmInvokeException("All presale evaluation models are busy", lastBusy);
    }

    private String buildPromptInputJson(RawSnapshotDTO raw) throws JsonProcessingException {
        ClientInfo client = raw == null ? null : raw.getClientInfo();
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("brand_name", client == null ? null : client.getBrandName());
        input.put("industry", client == null ? null : client.getIndustry());
        input.put("industry_role", client == null ? null : client.getIndustryRole());
        input.put("region", client == null ? null : client.getRegion());
        input.put("user_demand", client == null ? null : client.getUserDemand());
        input.put("question_count", page03MarketConfigService.getConfig().getQuestionCount());
        input.put("question_max_length", 34);
        input.put("sample_prompts", buildSamplePromptInput(raw));
        return objectMapper.writeValueAsString(input);
    }

    private MarketBattleground buildMarketPatch(RawSnapshotDTO raw, JsonNode aiNode) {
        if (aiNode == null || !aiNode.isObject()) {
            throw new IllegalArgumentException("Page03 AI output must be a JSON object");
        }
        String parentCategoryName = requiredParentCategoryName(aiNode, "parent_category_name", raw);
        String parentShare = requiredPercent(aiNode, "parent_category_share");
        String industryShare = requiredPercent(aiNode, "industry_share");
        String regionShare = requiredPercent(aiNode, "region_share");
        List<String> questions = requiredQuestions(aiNode, "questions", 3);

        ClientInfo client = raw == null ? null : raw.getClientInfo();
        String brand = client == null ? null : client.getBrandName();
        String region = client == null ? null : client.getRegion();

        return MarketBattleground.builder()
                .nationalCard(MarketBattleground.CalculationCard.builder()
                        .rows(List.of(
                                calcRow(null, null, false),
                                calcRow(parentCategoryName + "类占比", parentShare, false),
                                calcRow(null, industryShare, false),
                                calcRow(null, null, true)
                        ))
                        .build())
                .regionalCard(MarketBattleground.CalculationCard.builder()
                        .rows(List.of(
                                calcRow(null, null, false),
                                calcRow(null, regionShare, false),
                                calcRow("数据来源", null, false),
                                calcRow(null, null, true)
                        ))
                        .build())
                .narrative(MarketBattleground.Narrative.builder()
                        .questions(questions)
                        .brandName(brand)
                        .build())
                .build();
    }

    private String requiredText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Page03 AI output missing " + field);
        }
        return value.asText().trim();
    }

    private String requiredPercent(JsonNode node, String field) {
        String value = requiredText(node, field);
        if (!value.contains("%")) {
            throw new IllegalArgumentException("Page03 AI output " + field + " must contain percent sign");
        }
        return value;
    }

    private String requiredParentCategoryName(JsonNode node, String field, RawSnapshotDTO raw) {
        String value = requiredText(node, field);
        if (value.length() < 2 || value.length() > 6) {
            throw new IllegalArgumentException("Page03 AI output " + field + " length must be between 2 and 6");
        }
        if (value.contains("类") || value.contains("占比") || value.contains("行业")) {
            throw new IllegalArgumentException("Page03 AI output " + field + " must not contain suffix words");
        }
        ClientInfo client = raw == null ? null : raw.getClientInfo();
        String brand = client == null ? null : client.getBrandName();
        String region = client == null ? null : client.getRegion();
        if (containsNonBlank(value, brand) || containsNonBlank(value, region)) {
            throw new IllegalArgumentException("Page03 AI output " + field + " must not contain brand_name or region");
        }
        return value;
    }

    private List<String> requiredQuestions(JsonNode node, String field, int size) {
        JsonNode values = node.path(field);
        if (!values.isArray() || values.size() != size) {
            throw new IllegalArgumentException("Page03 AI output questions must contain exactly " + size + " items");
        }
        List<String> out = new ArrayList<>();
        for (JsonNode item : values) {
            if (!item.isTextual() || item.asText().isBlank()) {
                throw new IllegalArgumentException("Page03 AI output questions item must not be blank");
            }
            out.add(item.asText().trim());
        }
        return out;
    }

    private boolean containsNonBlank(String value, String keyword) {
        return keyword != null && !keyword.isBlank() && value.contains(keyword);
    }

    private List<Map<String, String>> buildSamplePromptInput(RawSnapshotDTO raw) {
        if (raw == null || raw.getSamplePrompts() == null) {
            return List.of();
        }
        return raw.getSamplePrompts().stream()
                .filter(item -> item != null && item.getPromptContent() != null && !item.getPromptContent().isBlank())
                .limit(6)
                .map(this::samplePromptMap)
                .toList();
    }

    private Map<String, String> samplePromptMap(SamplePrompt item) {
        Map<String, String> out = new LinkedHashMap<>();
        out.put("category", item.getCategory());
        out.put("prompt_content", item.getPromptContent());
        return out;
    }

    private MarketBattleground.CalculationRow calcRow(String label, String value, boolean isTotal) {
        return MarketBattleground.CalculationRow.builder()
                .label(label)
                .value(value)
                .isTotal(isTotal)
                .build();
    }
}
