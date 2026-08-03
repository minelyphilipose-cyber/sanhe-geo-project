package com.huanjing.geo.module.presale.generate.l3;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.presale.dto.snapshot.editable.EditableContentDTO;
import com.huanjing.geo.module.presale.dto.snapshot.editable.MarketBattleground;
import com.huanjing.geo.module.presale.dto.snapshot.raw.ClientInfo;
import com.huanjing.geo.module.presale.dto.snapshot.raw.RawSnapshotDTO;
import com.huanjing.geo.module.presale.persist.entity.PresalePage03MarketConfig;
import com.huanjing.geo.module.presale.service.PresalePage03MarketConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketBattlegroundValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PresalePage03MarketConfigService configService = mockConfigService();
    private final PresaleL3Defaults defaults = new PresaleL3Defaults(objectMapper, configService);
    private final MarketBattlegroundValidator validator = new MarketBattlegroundValidator();

    @Test
    void validate_acceptsDefaultMarketBattleground() throws Exception {
        MarketBattleground market = defaultMarket();

        assertDoesNotThrow(() -> validator.validate(market));
    }

    @Test
    void validate_acceptsTenCharacterRegionAndIndustryInCalculationLabels() throws Exception {
        MarketBattleground market = defaultMarket("一二三四五六七八九十", "甲乙丙丁戊己庚辛壬癸");

        assertDoesNotThrow(() -> validator.validate(market));
    }

    @ParameterizedTest
    @CsvSource({
            "automotive, 汽车",
            "retail, 电商零售",
            "finance, 金融",
            "tourism, 旅游酒店",
            "tech_software, 'SaaS 企业软件'"
    })
    void defaults_renderConfiguredIndustryCodesAsVisibleLabels(String industryCode, String expectedLabel) throws Exception {
        MarketBattleground market = defaultMarket("亳州", industryCode);

        assertEquals("NATIONAL · 全国" + expectedLabel + "每天", market.getNationalCard().getLabel());
        assertEquals("REGIONAL · 亳州" + expectedLabel + "每天", market.getRegionalCard().getLabel());
    }

    @Test
    void validate_rejectsChangedFixedKicker() throws Exception {
        MarketBattleground market = defaultMarket();
        market.setPageKicker("A different English line");

        assertThrows(BizException.class, () -> validator.validate(market));
    }

    @Test
    void validate_rejectsBrandInNarrativeQuestions() throws Exception {
        MarketBattleground market = defaultMarket();
        market.getNarrative().getQuestions().set(0, "Acme 怎么样？");

        assertThrows(BizException.class, () -> validator.validate(market));
    }

    @Test
    void validateRawJson_rejectsStringIsTotal() throws Exception {
        String json = """
                {
                  "national_card": {
                    "rows": [
                      {"label": "A", "value": "B", "is_total": "true"}
                    ]
                  }
                }
                """;

        assertThrows(BizException.class, () -> validator.validateRawJson(objectMapper.readTree(json)));
    }

    private MarketBattleground defaultMarket() throws Exception {
        return defaultMarket("阜阳", "medical_beauty");
    }

    private MarketBattleground defaultMarket(String region, String industry) throws Exception {
        RawSnapshotDTO raw = RawSnapshotDTO.builder()
                .clientInfo(ClientInfo.builder()
                        .brandName("Acme")
                        .industry(industry)
                        .industryRole("brand")
                        .region(region)
                        .build())
                .build();
        String normalized = defaults.normalizeJson("{}", objectMapper.writeValueAsString(raw), "{}");
        return objectMapper.readValue(normalized, EditableContentDTO.class).getMarketBattleground();
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
