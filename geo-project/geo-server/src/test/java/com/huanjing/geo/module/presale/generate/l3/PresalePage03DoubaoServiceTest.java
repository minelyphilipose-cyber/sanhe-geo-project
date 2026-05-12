package com.huanjing.geo.module.presale.generate.l3;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.presale.dto.snapshot.editable.EditableContentDTO;
import com.huanjing.geo.module.presale.dto.snapshot.editable.MarketBattleground;
import com.huanjing.geo.module.presale.dto.snapshot.raw.ClientInfo;
import com.huanjing.geo.module.presale.dto.snapshot.raw.RawSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.raw.SamplePrompt;
import com.huanjing.geo.module.presale.generate.llm.CallStatus;
import com.huanjing.geo.module.presale.generate.llm.LlmCallResult;
import com.huanjing.geo.module.presale.generate.llm.PlatformCallContext;
import com.huanjing.geo.module.presale.generate.llm.PresaleLlmInvoker;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PresalePage03DoubaoServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PresaleLlmInvoker llmInvoker = mock(PresaleLlmInvoker.class);
    private final PresaleL3Defaults l3Defaults = new PresaleL3Defaults(objectMapper);
    private final MarketBattlegroundValidator validator = new MarketBattlegroundValidator();
    private final PresalePage03DoubaoService service =
            new PresalePage03DoubaoService(objectMapper, llmInvoker, l3Defaults, validator);

    @Test
    void generateAndApply_usesDoubaoAndRecalculatesTraffic() throws Exception {
        RawSnapshotDTO raw = RawSnapshotDTO.builder()
                .clientInfo(ClientInfo.builder()
                        .brandName("无二火锅")
                        .industry("restaurant")
                        .industryRole("连锁品牌")
                        .region("阜阳")
                        .userDemand("本地火锅品牌 AI 可见度诊断")
                        .build())
                .samplePrompts(List.of(
                        SamplePrompt.builder().category("推荐型").promptContent("阜阳火锅店哪家更好吃？").build(),
                        SamplePrompt.builder().category("问题型").promptContent("阜阳吃火锅哪家性价比高？").build(),
                        SamplePrompt.builder().category("场景型").promptContent("阜阳家庭聚餐吃火锅推荐哪家？").build()
                ))
                .build();
        String rawJson = objectMapper.writeValueAsString(raw);
        String editableJson = objectMapper.writeValueAsString(l3Defaults.normalize(new EditableContentDTO(), raw, null));
        when(llmInvoker.marketBattleground(any(PlatformCallContext.class), anyString()))
                .thenReturn(new LlmCallResult(doubaoResponse(), 100, 200, 30L, 0,
                        CallStatus.SUCCESS, "doubao", "豆包", "doubao-pro", "豆包 Pro"));

        String resultJson = service.generateAndApply(290L, rawJson, editableJson, 1L, false);

        ArgumentCaptor<PlatformCallContext> ctxCaptor = ArgumentCaptor.forClass(PlatformCallContext.class);
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(llmInvoker).marketBattleground(ctxCaptor.capture(), promptCaptor.capture());
        assertEquals("doubao", ctxCaptor.getValue().platformCode());
        assertTrue(promptCaptor.getValue().contains("\"brand_name\":\"无二火锅\""));
        assertTrue(promptCaptor.getValue().contains("固定句式为「每天，{决策主题}决策正在 AI 上发生」"));

        MarketBattleground market = objectMapper.readValue(resultJson, EditableContentDTO.class).getMarketBattleground();
        assertEquals("每天，火锅决策正在 AI 上发生", market.getPageTitle());
        assertEquals("281.3", market.getNationalCard().getValue());
        assertEquals("万次", market.getNationalCard().getUnit());
        assertEquals("281.3万次", market.getNationalCard().getRows().get(3).getValue());
        assertEquals("2250", market.getRegionalCard().getValue());
        assertEquals("次", market.getRegionalCard().getUnit());
        assertEquals("281.3万次", market.getRegionalCard().getRows().get(0).getValue());
        assertEquals("2250次", market.getRegionalCard().getRows().get(3).getValue());
    }

    @Test
    void generateAndApply_fillsBlankGeneratedMarketCardLabel() throws Exception {
        RawSnapshotDTO raw = RawSnapshotDTO.builder()
                .clientInfo(ClientInfo.builder()
                        .brandName("无二火锅")
                        .industry("restaurant")
                        .industryRole("连锁品牌")
                        .region("阜阳")
                        .build())
                .build();
        String rawJson = objectMapper.writeValueAsString(raw);
        String editableJson = objectMapper.writeValueAsString(l3Defaults.normalize(new EditableContentDTO(), raw, null));
        String responseWithBlankLabel = doubaoResponse().replace(
                "\"label\": \"AI 搜索大盘流量\"",
                "\"label\": \"\""
        );
        when(llmInvoker.marketBattleground(any(PlatformCallContext.class), anyString()))
                .thenReturn(new LlmCallResult(responseWithBlankLabel, 100, 200, 30L, 0,
                        CallStatus.SUCCESS, "doubao", "豆包", "doubao-pro", "豆包 Pro"));

        String resultJson = service.generateAndApply(290L, rawJson, editableJson, 1L, false);

        MarketBattleground market = objectMapper.readValue(resultJson, EditableContentDTO.class).getMarketBattleground();
        assertEquals("CHINA AI MARKET · 2026 Q1", market.getMarketCard().getLabel());
    }

    @Test
    void generateAndApply_replacesOverlongNarrativeQuestion() throws Exception {
        RawSnapshotDTO raw = RawSnapshotDTO.builder()
                .clientInfo(ClientInfo.builder()
                        .brandName("诗帝尼")
                        .industry("门窗")
                        .industryRole("manufacturer")
                        .region("国内")
                        .userDemand("了解诗帝尼在AI推荐中的真实表现")
                        .build())
                .samplePrompts(List.of(
                        SamplePrompt.builder().category("问题型").promptContent("国内系统门窗加盟应该优先看哪些品牌的综合实力？").build(),
                        SamplePrompt.builder().category("推荐型").promptContent("国内高端系统门窗加盟推荐哪类品牌？").build(),
                        SamplePrompt.builder().category("场景型").promptContent("国内代理商选择门窗品牌时哪家更适合长期合作？").build()
                ))
                .build();
        String rawJson = objectMapper.writeValueAsString(raw);
        String editableJson = objectMapper.writeValueAsString(l3Defaults.normalize(new EditableContentDTO(), raw, null));
        String responseWithLongQuestion = doubaoResponse()
                .replace("\"无二火锅\"", "\"诗帝尼\"")
                .replace("\"每天，火锅决策正在 AI 上发生\"", "\"每天，门窗决策正在 AI 上发生\"")
                .replace("\"阜阳哪家火锅店适合聚会？\"",
                        "\"国内系统门窗加盟应该优先看哪些品牌的综合实力和长期扶持能力？\"");
        when(llmInvoker.marketBattleground(any(PlatformCallContext.class), anyString()))
                .thenReturn(new LlmCallResult(responseWithLongQuestion, 100, 200, 30L, 0,
                        CallStatus.SUCCESS, "doubao", "豆包", "doubao-pro", "豆包 Pro"));

        String resultJson = service.generateAndApply(330L, rawJson, editableJson, 1L, false);

        MarketBattleground market = objectMapper.readValue(resultJson, EditableContentDTO.class).getMarketBattleground();
        assertTrue(market.getNarrative().getQuestions().stream()
                .allMatch(question -> question.length() <= 34 && !question.contains("诗帝尼")));
    }

    private String doubaoResponse() {
        return """
                {
                  "topbar_title": "MARKET BATTLEGROUND · AI 搜索新战场",
                  "topbar_right": "GEO · CONFIDENTIAL",
                  "page_title": "每天，火锅决策正在 AI 上发生",
                  "page_kicker": "THE NEW BATTLEGROUND FOR YOUR BRAND",
                  "market_card": {
                    "label": "AI 搜索大盘流量",
                    "source": "来源：行业公开数据综合估算",
                    "stats": [
                      {"value": "9.8", "unit": "亿", "label": "AI 原生 APP 月活"},
                      {"value": "3.5", "unit": "亿", "label": "日均活跃用户"},
                      {"value": "15.0", "unit": "亿次", "label": "日均提问总量"},
                      {"value": "19.6", "unit": "次", "label": "豆包人均月使用"}
                    ],
                    "platform_label": "TOP 平台",
                    "platforms": [
                      {"name": "豆包", "value": "5.8亿月活"},
                      {"name": "千问", "value": "3.2亿月活"},
                      {"name": "DeepSeek", "value": "1.9亿月活"}
                    ],
                    "platform_suffix": "元宝 / Kimi 等"
                  },
                  "national_card": {
                    "label": "全国火锅AI决策流量",
                    "value_prefix": "全国日均",
                    "value": "9999",
                    "unit": "万次",
                    "subtitle": "火锅消费决策向AI迁移",
                    "calculation_label": "流量推导逻辑",
                    "rows": [
                      {"label": "AI日均提问总量", "value": "15.0亿次", "is_total": false},
                      {"label": "生活餐饮占比", "value": "12.5%", "is_total": false},
                      {"label": "火锅品类占比", "value": "1.5%", "is_total": false},
                      {"label": "全国火锅AI提问", "value": "9999万次", "is_total": true}
                    ]
                  },
                  "bridge_text": "↓ 聚焦到您的核心市场",
                  "regional_card": {
                    "label": "阜阳火锅AI决策流量",
                    "value_prefix": "阜阳日均",
                    "value": "9999",
                    "unit": "次",
                    "subtitle": "本地火锅AI搜索需求活跃",
                    "calculation_label": "区域推导逻辑",
                    "rows": [
                      {"label": "全国火锅AI提问", "value": "9999万次", "is_total": false},
                      {"label": "阜阳区域占比", "value": "0.08%", "is_total": false},
                      {"label": "数据来源", "value": "公开口径综合测算", "is_total": false},
                      {"label": "阜阳火锅AI提问", "value": "9999次", "is_total": true}
                    ]
                  },
                  "narrative": {
                    "intro": "这意味着，消费者正在通过 AI 持续询问：",
                    "questions": [
                      "阜阳哪家火锅店适合聚会？",
                      "阜阳火锅哪家性价比高？",
                      "阜阳约会火锅店推荐哪家？"
                    ],
                    "conclusion": "而 AI 给出的答案，正在影响他们下一步选择。",
                    "brand_line_prefix": "→",
                    "brand_name": "无二火锅",
                    "brand_line_suffix": "在这些场景中的真实可见度如何？详见下章诊断结果。"
                  },
                  "footnote": "本数据为行业公开信息综合估算，存在合理浮动区间，仅作市场量级参考，不构成精确市场断言。",
                  "footer_brand": "无二火锅"
                }
                """;
    }
}
