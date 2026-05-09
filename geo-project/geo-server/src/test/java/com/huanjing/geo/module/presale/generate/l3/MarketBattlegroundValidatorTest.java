package com.huanjing.geo.module.presale.generate.l3;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.presale.dto.snapshot.editable.EditableContentDTO;
import com.huanjing.geo.module.presale.dto.snapshot.editable.MarketBattleground;
import com.huanjing.geo.module.presale.dto.snapshot.raw.ClientInfo;
import com.huanjing.geo.module.presale.dto.snapshot.raw.RawSnapshotDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MarketBattlegroundValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PresaleL3Defaults defaults = new PresaleL3Defaults(objectMapper);
    private final MarketBattlegroundValidator validator = new MarketBattlegroundValidator();

    @Test
    void validate_acceptsDefaultMarketBattleground() throws Exception {
        MarketBattleground market = defaultMarket();

        assertDoesNotThrow(() -> validator.validate(market));
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
        RawSnapshotDTO raw = RawSnapshotDTO.builder()
                .clientInfo(ClientInfo.builder()
                        .brandName("Acme")
                        .industry("medical_beauty")
                        .industryRole("brand")
                        .region("阜阳")
                        .build())
                .build();
        String normalized = defaults.normalizeJson("{}", objectMapper.writeValueAsString(raw), "{}");
        return objectMapper.readValue(normalized, EditableContentDTO.class).getMarketBattleground();
    }
}
