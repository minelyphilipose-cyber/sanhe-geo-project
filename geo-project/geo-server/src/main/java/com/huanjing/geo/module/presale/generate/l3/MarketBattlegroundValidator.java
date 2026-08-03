package com.huanjing.geo.module.presale.generate.l3;

import com.fasterxml.jackson.databind.JsonNode;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.presale.dto.snapshot.editable.MarketBattleground;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 第 03 页固定布局的结构护栏。
 */
@Component
public class MarketBattlegroundValidator {

    static final String TOPBAR_TITLE = "MARKET BATTLEGROUND · AI 搜索新战场";
    static final String TOPBAR_RIGHT = "GEO · CONFIDENTIAL";
    static final String PAGE_KICKER = "THE NEW BATTLEGROUND FOR YOUR BRAND";
    static final String BRIDGE_TEXT = "↓ 聚焦到您的核心市场";
    static final String PLATFORM_LABEL = "TOP 平台";
    static final String PLATFORM_SUFFIX = "元宝 / Kimi 等";
    static final String BRAND_LINE_PREFIX = "→";
    static final int CALCULATION_CARD_LABEL_MAX_LENGTH = 36;
    static final int PAGE_TITLE_MIN_LENGTH = 12;
    static final int PAGE_TITLE_MAX_LENGTH = 22;

    public void validateRawJson(JsonNode marketNode) {
        if (marketNode == null || marketNode.isNull()) {
            return;
        }
        if (!marketNode.isObject()) {
            throw new BizException(400, "market_battleground must be an object");
        }
        validateRawRows("market_battleground.national_card", marketNode.path("national_card"));
        validateRawRows("market_battleground.regional_card", marketNode.path("regional_card"));
    }

    public void validate(MarketBattleground value) {
        if (value == null) {
            return;
        }
        requireLiteral("market_battleground.topbar_title", value.getTopbarTitle(), TOPBAR_TITLE);
        requireLiteral("market_battleground.topbar_right", value.getTopbarRight(), TOPBAR_RIGHT);
        validatePageTitle(value.getPageTitle());
        requireLiteral("market_battleground.page_kicker", value.getPageKicker(), PAGE_KICKER);
        validateMarketCard(value.getMarketCard());
        validateCalculationCard("market_battleground.national_card", value.getNationalCard());
        requireLiteral("market_battleground.bridge_text", value.getBridgeText(), BRIDGE_TEXT);
        validateCalculationCard("market_battleground.regional_card", value.getRegionalCard());
        validateNarrative(value.getNarrative());
        requireText("market_battleground.footnote", value.getFootnote(), 150);
        requireText("market_battleground.footer_brand", value.getFooterBrand(), 24);
    }

    private void validateRawRows(String field, JsonNode cardNode) {
        if (cardNode == null || cardNode.isMissingNode() || cardNode.isNull()) {
            return;
        }
        JsonNode rows = cardNode.path("rows");
        if (rows.isMissingNode() || rows.isNull()) {
            return;
        }
        if (!rows.isArray()) {
            throw new BizException(400, field + ".rows must be an array");
        }
        for (int i = 0; i < rows.size(); i++) {
            JsonNode isTotal = rows.get(i).path("is_total");
            if (!isTotal.isMissingNode() && !isTotal.isNull() && !isTotal.isBoolean()) {
                throw new BizException(400, field + ".rows[" + i + "].is_total must be boolean");
            }
        }
    }

    private void validateMarketCard(MarketBattleground.MarketCard value) {
        if (value == null) {
            throw new BizException(400, "market_battleground.market_card must not be null");
        }
        requireText("market_battleground.market_card.label", value.getLabel(), 32);
        requireText("market_battleground.market_card.source", value.getSource(), 32);
        requireSizedList("market_battleground.market_card.stats", value.getStats(), 4);
        for (MarketBattleground.Stat item : value.getStats()) {
            if (item == null) {
                throw new BizException(400, "market_battleground.market_card.stats item must not be null");
            }
            requireText("market_battleground.market_card.stats.value", item.getValue(), 12);
            requireText("market_battleground.market_card.stats.unit", item.getUnit(), 8);
            requireText("market_battleground.market_card.stats.label", item.getLabel(), 24);
        }
        requireLiteral("market_battleground.market_card.platform_label", value.getPlatformLabel(), PLATFORM_LABEL);
        requireSizedList("market_battleground.market_card.platforms", value.getPlatforms(), 3);
        for (MarketBattleground.Platform item : value.getPlatforms()) {
            if (item == null) {
                throw new BizException(400, "market_battleground.market_card.platforms item must not be null");
            }
            requireText("market_battleground.market_card.platforms.name", item.getName(), 12);
            requireText("market_battleground.market_card.platforms.value", item.getValue(), 12);
        }
        requireText("market_battleground.market_card.platform_suffix", value.getPlatformSuffix(), 18);
    }

    private void validateCalculationCard(String field, MarketBattleground.CalculationCard value) {
        if (value == null) {
            throw new BizException(400, field + " must not be null");
        }
        requireText(field + ".label", value.getLabel(), CALCULATION_CARD_LABEL_MAX_LENGTH);
        requireText(field + ".value_prefix", value.getValuePrefix(), 6);
        requireText(field + ".value", value.getValue(), 12);
        requireText(field + ".unit", value.getUnit(), 8);
        requireText(field + ".subtitle", value.getSubtitle(), 28);
        requireText(field + ".calculation_label", value.getCalculationLabel(), 24);
        requireSizedList(field + ".rows", value.getRows(), 4);
        for (int i = 0; i < value.getRows().size(); i++) {
            MarketBattleground.CalculationRow row = value.getRows().get(i);
            if (row == null) {
                throw new BizException(400, field + ".rows item must not be null");
            }
            requireText(field + ".rows.label", row.getLabel(), 18);
            requireText(field + ".rows.value", row.getValue(), 30);
            Boolean expected = i == 3;
            if (!expected.equals(row.getIsTotal())) {
                throw new BizException(400, field + ".rows[" + i + "].is_total must be " + expected);
            }
        }
    }

    private void validateNarrative(MarketBattleground.Narrative value) {
        if (value == null) {
            throw new BizException(400, "market_battleground.narrative must not be null");
        }
        requireText("market_battleground.narrative.intro", value.getIntro(), 56);
        requireSizedList("market_battleground.narrative.questions", value.getQuestions(), 3);
        String brandName = value.getBrandName();
        for (String question : value.getQuestions()) {
            requireText("market_battleground.narrative.questions[]", question, 34);
            if (brandName != null && !brandName.isBlank() && question.contains(brandName)) {
                throw new BizException(400, "market_battleground.narrative.questions must not contain brand_name");
            }
        }
        requireText("market_battleground.narrative.conclusion", value.getConclusion(), 44);
        requireLiteral("market_battleground.narrative.brand_line_prefix", value.getBrandLinePrefix(), BRAND_LINE_PREFIX);
        requireText("market_battleground.narrative.brand_name", value.getBrandName(), 18);
        requireText("market_battleground.narrative.brand_line_suffix", value.getBrandLineSuffix(), 48);
    }

    private void validatePageTitle(String value) {
        requireText("market_battleground.page_title", value, PAGE_TITLE_MAX_LENGTH);
        int length = value.length();
        if (length < PAGE_TITLE_MIN_LENGTH) {
            throw new BizException(400, "market_battleground.page_title length must be between 12 and 22");
        }
        if (!value.contains("AI")) {
            throw new BizException(400, "market_battleground.page_title must contain AI");
        }
        if (!(value.contains("每天") || value.contains("数") || value.contains("万") || value.contains("亿"))) {
            throw new BizException(400, "market_battleground.page_title must contain quantity wording");
        }
        if (value.contains("!") || value.contains("！")) {
            throw new BizException(400, "market_battleground.page_title must not contain exclamation mark");
        }
    }

    private void requireSizedList(String field, List<?> list, int size) {
        if (list == null) {
            throw new BizException(400, field + " must not be null");
        }
        if (list.size() != size) {
            throw new BizException(400, field + " must contain exactly " + size + " items");
        }
    }

    private void requireLiteral(String field, String value, String expected) {
        if (!expected.equals(value)) {
            throw new BizException(400, field + " must be " + expected);
        }
    }

    private void requireText(String field, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new BizException(400, field + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw new BizException(400, field + " length must not exceed " + maxLength);
        }
    }
}
