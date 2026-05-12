package com.huanjing.geo.module.presale.generate.l3;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Page03 市场战场页固定使用豆包生成,后端负责结构校验与数值重算。
 */
@Service
@RequiredArgsConstructor
public class PresalePage03DoubaoService {

    private static final String PLATFORM_DOUBAO = "doubao";
    private static final int BATCH_NO_PAGE03 = 3;

    private final ObjectMapper objectMapper;
    private final PresaleLlmInvoker llmInvoker;
    private final PresaleL3Defaults l3Defaults;
    private final MarketBattlegroundValidator marketBattlegroundValidator;

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
            PlatformCallContext ctx = new PlatformCallContext(
                    versionId,
                    BATCH_NO_PAGE03,
                    PLATFORM_DOUBAO,
                    null,
                    "",
                    brandName,
                    operatorUserId,
                    operatorIsManager
            );

            LlmCallResult result = llmInvoker.marketBattleground(ctx, prompt);
            JsonNode marketNode = objectMapper.readTree(result.rawResponse());
            marketBattlegroundValidator.validateRawJson(marketNode);
            MarketBattleground market = objectMapper.treeToValue(marketNode, MarketBattleground.class);

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

    private String buildPromptInputJson(RawSnapshotDTO raw) throws JsonProcessingException {
        ClientInfo client = raw == null ? null : raw.getClientInfo();
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("brand_name", client == null ? null : client.getBrandName());
        input.put("industry", client == null ? null : client.getIndustry());
        input.put("industry_role", client == null ? null : client.getIndustryRole());
        input.put("region", client == null ? null : client.getRegion());
        input.put("user_demand", client == null ? null : client.getUserDemand());
        input.put("sample_prompts", buildSamplePromptInput(raw));
        return objectMapper.writeValueAsString(input);
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
}
