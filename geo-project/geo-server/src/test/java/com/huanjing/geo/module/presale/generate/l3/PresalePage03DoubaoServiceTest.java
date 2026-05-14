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
import com.huanjing.geo.module.presale.generate.PresaleEvaluationModelRouter;
import com.huanjing.geo.module.presale.persist.entity.PresalePage03MarketConfig;
import com.huanjing.geo.module.presale.service.PresalePage03MarketConfigService;
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
    private final PresalePage03MarketConfigService configService = mockConfigService();
    private final PresaleL3Defaults l3Defaults = new PresaleL3Defaults(objectMapper, configService);
    private final MarketBattlegroundValidator validator = new MarketBattlegroundValidator();
    private final PresaleEvaluationModelRouter evaluationModelRouter = mock(PresaleEvaluationModelRouter.class);
    private final PresalePage03DoubaoService service =
            new PresalePage03DoubaoService(objectMapper, llmInvoker, l3Defaults, validator, evaluationModelRouter,
                    configService);

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
        when(evaluationModelRouter.routeContexts(any(PlatformCallContext.class)))
                .thenReturn(List.of(new PlatformCallContext(290L, 3, "doubao", null, "", "无二火锅", 1L, false)));

        String resultJson = service.generateAndApply(290L, rawJson, editableJson, 1L, false);

        ArgumentCaptor<PlatformCallContext> ctxCaptor = ArgumentCaptor.forClass(PlatformCallContext.class);
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(llmInvoker).marketBattleground(ctxCaptor.capture(), promptCaptor.capture());
        assertEquals("doubao", ctxCaptor.getValue().platformCode());
        assertTrue(promptCaptor.getValue().contains("\"brand_name\":\"无二火锅\""));
        assertTrue(promptCaptor.getValue().contains("\"question_max_length\":34"));
        assertTrue(promptCaptor.getValue().contains("\"parent_category_name\""));

        MarketBattleground market = objectMapper.readValue(resultJson, EditableContentDTO.class).getMarketBattleground();
        assertEquals("每天，有数千万次消费决策正在 AI 上发生", market.getPageTitle());
        assertEquals("餐饮消费类占比", market.getNationalCard().getRows().get(1).getLabel());
        assertEquals("225.0", market.getNationalCard().getValue());
        assertEquals("万次", market.getNationalCard().getUnit());
        assertEquals("225.0万次", market.getNationalCard().getRows().get(3).getValue());
        assertEquals("1800", market.getRegionalCard().getValue());
        assertEquals("次", market.getRegionalCard().getUnit());
        assertEquals("225.0万次", market.getRegionalCard().getRows().get(0).getValue());
        assertEquals("1800次", market.getRegionalCard().getRows().get(3).getValue());
    }

    @Test
    void generateAndApply_keepsConfiguredMarketCardLabel() throws Exception {
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
        when(llmInvoker.marketBattleground(any(PlatformCallContext.class), anyString()))
                .thenReturn(new LlmCallResult(doubaoResponse(), 100, 200, 30L, 0,
                        CallStatus.SUCCESS, "doubao", "豆包", "doubao-pro", "豆包 Pro"));
        when(evaluationModelRouter.routeContexts(any(PlatformCallContext.class)))
                .thenReturn(List.of(new PlatformCallContext(290L, 3, "doubao", null, "", "无二火锅", 1L, false)));

        String resultJson = service.generateAndApply(290L, rawJson, editableJson, 1L, false);

        MarketBattleground market = objectMapper.readValue(resultJson, EditableContentDTO.class).getMarketBattleground();
        assertEquals("AI 搜索流量总览", market.getMarketCard().getLabel());
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
                .replace("\"阜阳哪家火锅店适合聚会？\"",
                        "\"国内系统门窗加盟应该优先看哪些品牌的综合实力和长期扶持能力？\"");
        when(llmInvoker.marketBattleground(any(PlatformCallContext.class), anyString()))
                .thenReturn(new LlmCallResult(responseWithLongQuestion, 100, 200, 30L, 0,
                        CallStatus.SUCCESS, "doubao", "豆包", "doubao-pro", "豆包 Pro"));
        when(evaluationModelRouter.routeContexts(any(PlatformCallContext.class)))
                .thenReturn(List.of(new PlatformCallContext(330L, 3, "doubao", null, "", "诗帝尼", 1L, false)));

        String resultJson = service.generateAndApply(330L, rawJson, editableJson, 1L, false);

        MarketBattleground market = objectMapper.readValue(resultJson, EditableContentDTO.class).getMarketBattleground();
        assertTrue(market.getNarrative().getQuestions().stream()
                .allMatch(question -> question.length() <= 34 && !question.contains("诗帝尼")));
    }

    private String doubaoResponse() {
        return """
                {
                  "parent_category_name": "餐饮消费",
                  "parent_category_share": "12.5%",
                  "industry_share": "1.5%",
                  "region_share": "0.08%",
                  "questions": [
                    "阜阳哪家火锅店适合聚会？",
                    "阜阳火锅哪家性价比高？",
                    "阜阳约会火锅店推荐哪家？"
                  ]
                }
                """;
    }

    private PresalePage03MarketConfigService mockConfigService() {
        PresalePage03MarketConfigService service = mock(PresalePage03MarketConfigService.class);
        when(service.getConfig()).thenReturn(defaultConfig());
        return service;
    }

    private PresalePage03MarketConfig defaultConfig() {
        PresalePage03MarketConfig out = new PresalePage03MarketConfig();
        out.setMarketLabel("AI 搜索流量总览");
        out.setMarketSource("来源：行业公开数据综合估算");
        out.setAppMonthlyActiveValue("8.3");
        out.setAppMonthlyActiveUnit("亿");
        out.setDailyActiveUsersValue("7.2");
        out.setDailyActiveUsersUnit("亿");
        out.setDailyQuestionTotalValue("12");
        out.setDailyQuestionTotalUnit("亿次");
        out.setDoubaoMonthlyUsageValue("28");
        out.setDoubaoMonthlyUsageUnit("次");
        out.setPlatform1Name("豆包");
        out.setPlatform1Value("5.8亿/月活");
        out.setPlatform2Name("千问");
        out.setPlatform2Value("4.2亿/月活");
        out.setPlatform3Name("DeepSeek");
        out.setPlatform3Value("3.1亿/月活");
        out.setPlatformSuffix("元宝 / Kimi 等");
        out.setPage03DataSource("公开口径综合测算");
        out.setFootnote("注：以上数据基于行业公开数据与主流AI平台问答量综合估算，存在±20%合理浮动区间，仅作量级参考，不构成精确市场断言。");
        out.setQuestionCount(3);
        return out;
    }
}
