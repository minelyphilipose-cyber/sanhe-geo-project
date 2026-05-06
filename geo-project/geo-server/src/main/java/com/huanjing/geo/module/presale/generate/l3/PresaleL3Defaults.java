package com.huanjing.geo.module.presale.generate.l3;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.presale.dto.response.EditableFieldMetaVO;
import com.huanjing.geo.module.presale.dto.snapshot.computed.ComputedSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.editable.EditableContentDTO;
import com.huanjing.geo.module.presale.dto.snapshot.editable.MarketBattleground;
import com.huanjing.geo.module.presale.dto.snapshot.raw.RawSnapshotDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * L3 默认值、字段元数据和 schema 归一化的后端权威源。
 */
@Component
@RequiredArgsConstructor
public class PresaleL3Defaults {

    private final ObjectMapper objectMapper;

    public EditableContentDTO normalize(EditableContentDTO input, RawSnapshotDTO raw, ComputedSnapshotDTO computed) {
        EditableContentDTO out = input == null ? new EditableContentDTO() : input;
        if (out.getMarketBattleground() == null) {
            out.setMarketBattleground(defaultMarketBattleground(raw));
        } else {
            out.setMarketBattleground(normalizeMarket(out.getMarketBattleground(), defaultMarketBattleground(raw)));
        }
        return out;
    }

    public String normalizeJson(String editableJson, String rawJson, String computedJson) {
        try {
            RawSnapshotDTO raw = objectMapper.readValue(rawJson, RawSnapshotDTO.class);
            EditableContentDTO editable = editableJson == null || editableJson.isBlank()
                    ? new EditableContentDTO()
                    : objectMapper.readValue(editableJson, EditableContentDTO.class);
            return objectMapper.writeValueAsString(normalize(editable, raw, null));
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new BizException(400, "Invalid editable content JSON");
        }
    }

    public List<EditableFieldMetaVO> fieldMeta() {
        List<EditableFieldMetaVO> out = new ArrayList<>();
        meta(out, "market_battleground.topbar_title", "顶部章节标题", "顶部条", 40, 32);
        meta(out, "market_battleground.topbar_right", "顶部右侧标识", "顶部条", 24, 18);
        meta(out, "market_battleground.page_title", "页面主标题", "页面标题", 34, 28);
        meta(out, "market_battleground.page_kicker", "英文副标题", "页面标题", 48, 38);
        meta(out, "market_battleground.market_card.label", "市场卡标签", "深色市场卡", 32, 26);
        meta(out, "market_battleground.market_card.source", "市场卡来源", "深色市场卡", 32, 26);
        for (int i = 0; i < 4; i++) {
            int no = i + 1;
            meta(out, "market_battleground.market_card.stats[" + i + "].value", "市场数据" + no + "数值", "深色市场卡", 12, 10);
            meta(out, "market_battleground.market_card.stats[" + i + "].unit", "市场数据" + no + "单位", "深色市场卡", 8, 6);
            meta(out, "market_battleground.market_card.stats[" + i + "].label", "市场数据" + no + "说明", "深色市场卡", 24, 19);
        }
        meta(out, "market_battleground.market_card.platform_label", "平台列表标签", "深色市场卡", 16, 12);
        for (int i = 0; i < 3; i++) {
            int no = i + 1;
            meta(out, "market_battleground.market_card.platforms[" + i + "].name", "平台" + no + "名称", "深色市场卡", 12, 10);
            meta(out, "market_battleground.market_card.platforms[" + i + "].value", "平台" + no + "数值", "深色市场卡", 12, 10);
        }
        meta(out, "market_battleground.market_card.platform_suffix", "其他平台说明", "深色市场卡", 18, 14);
        cardMeta(out, "national_card", "全国推导卡");
        meta(out, "market_battleground.bridge_text", "过渡文案", "过渡", 20, 16);
        cardMeta(out, "regional_card", "区域推导卡");
        meta(out, "market_battleground.narrative.intro", "问题场景引导", "底部叙事", 56, 45);
        for (int i = 0; i < 3; i++) {
            meta(out, "market_battleground.narrative.questions[" + i + "]", "示例问题" + (i + 1), "底部叙事", 34, 28);
        }
        meta(out, "market_battleground.narrative.conclusion", "结论句", "底部叙事", 44, 36);
        meta(out, "market_battleground.narrative.brand_line_prefix", "品牌句前缀", "底部叙事", 8, 6);
        meta(out, "market_battleground.narrative.brand_name", "品牌句品牌名", "底部叙事", 18, 14);
        meta(out, "market_battleground.narrative.brand_line_suffix", "品牌句后缀", "底部叙事", 48, 38);
        meta(out, "market_battleground.footnote", "数据脚注", "脚注", 150, 120);
        meta(out, "market_battleground.footer_brand", "页脚品牌", "脚注", 24, 18);
        return out;
    }

    private void cardMeta(List<EditableFieldMetaVO> out, String card, String block) {
        meta(out, "market_battleground." + card + ".label", "卡片标签", block, 24, 19);
        meta(out, "market_battleground." + card + ".value_prefix", "大数字前缀", block, 6, 4);
        meta(out, "market_battleground." + card + ".value", "大数字", block, 12, 10);
        meta(out, "market_battleground." + card + ".unit", "大数字单位", block, 8, 6);
        meta(out, "market_battleground." + card + ".subtitle", "大数字说明", block, 28, 22);
        meta(out, "market_battleground." + card + ".calculation_label", "推导标题", block, 24, 19);
        for (int i = 0; i < 4; i++) {
            int no = i + 1;
            meta(out, "market_battleground." + card + ".rows[" + i + "].label", "推导行" + no + "标签", block, 18, 14);
            meta(out, "market_battleground." + card + ".rows[" + i + "].value", "推导行" + no + "数值", block, 30, 24);
        }
    }

    private MarketBattleground defaultMarketBattleground(RawSnapshotDTO raw) {
        String brand = raw == null || raw.getClientInfo() == null || raw.getClientInfo().getBrandName() == null
                ? "文王贡酒" : raw.getClientInfo().getBrandName();
        String industry = raw == null || raw.getClientInfo() == null || raw.getClientInfo().getIndustry() == null
                ? "白酒" : raw.getClientInfo().getIndustry();
        String region = raw == null || raw.getClientInfo() == null || raw.getClientInfo().getRegion() == null
                ? "安徽" : raw.getClientInfo().getRegion();

        return MarketBattleground.builder()
                .topbarTitle("MARKET BATTLEGROUND · AI 搜索新战场")
                .topbarRight("GEO · CONFIDENTIAL")
                .pageTitle("每天，有数千万次消费决策正在 AI 上发生")
                .pageKicker("The new battleground for your brand")
                .marketCard(MarketBattleground.MarketCard.builder()
                        .label("CHINA AI MARKET · 2026 Q1")
                        .source("数据来源：QuestMobile")
                        .stats(List.of(
                                stat("4.4", "亿", "AI 原生 APP 月活"),
                                stat("3", "亿+", "日均活跃用户（DAU）"),
                                stat("9", "亿次", "日均提问总量"),
                                stat("54.8", "次", "豆包人均月使用")
                        ))
                        .platformLabel("TOP 平台")
                        .platforms(List.of(
                                platform("豆包", "3.45 亿"),
                                platform("千问", "1.66 亿"),
                                platform("DeepSeek", "1.27 亿")
                        ))
                        .platformSuffix("元宝 / Kimi 等")
                        .build())
                .nationalCard(MarketBattleground.CalculationCard.builder()
                        .label("NATIONAL · 全国" + industry + "每天")
                        .valuePrefix("")
                        .value("2,000")
                        .unit("万+")
                        .subtitle("条 / 天 · " + industry + "相关 AI 提问")
                        .calculationLabel("CALCULATION · 推导口径")
                        .rows(List.of(
                                calcRow("日均提问总量", "约 9 亿次 / 天", false),
                                calcRow("生活/酒水类占比", "约 30% - 35%", false),
                                calcRow(industry + "在酒水类占比", "约 45% - 60%", false),
                                calcRow("中枢值", "约 2,000 万条 / 天", true)
                        ))
                        .build())
                .bridgeText("↓ 聚焦到您的核心市场")
                .regionalCard(MarketBattleground.CalculationCard.builder()
                        .label("REGIONAL · " + region + industry + "每天")
                        .valuePrefix("约")
                        .value("110")
                        .unit("万")
                        .subtitle("条 / 天 · " + region + "消费者向 AI 提问")
                        .calculationLabel("CALCULATION · 推导口径")
                        .rows(List.of(
                                calcRow("全国" + industry + "日提问", "约 2,000 万条 / 天", false),
                                calcRow(region + "市场系数", "约 5.5%（规模口径）", false),
                                calcRow("数据来源", "中国酒业协会、华创证券研报", false),
                                calcRow("区域日提问", "≈ 110 万条 / 天", true)
                        ))
                        .build())
                .narrative(MarketBattleground.Narrative.builder()
                        .intro("这意味着，" + region + "消费者每天 约 110 万次 通过 AI 询问：")
                        .questions(List.of(
                                "\"" + region + "有什么好喝的" + industry + "？\"",
                                "\"" + brand + "和本地头部品牌哪个更好？\"",
                                "\"送" + region + "朋友买什么" + industry + "合适？\""
                        ))
                        .conclusion("而 AI 给出的答案 —— 正在直接决定他们的购买选择。")
                        .brandLinePrefix("→")
                        .brandName(brand)
                        .brandLineSuffix("在这些场景中的真实可见度如何？详见下章诊断结果。")
                        .build())
                .footnote("数据来源：QuestMobile《2026 Q1 AI 应用洞察》、中国酒业协会 2023 年度数据、华创证券研报。具体数值受平台分布、用户行为差异影响，合理区间 1,500-2,500 万条/天，本报告采用中枢值。")
                .footerBrand("GEO · CONFIDENTIAL")
                .build();
    }

    private MarketBattleground normalizeMarket(MarketBattleground value, MarketBattleground defaults) {
        value.setTopbarTitle(coalesce(value.getTopbarTitle(), defaults.getTopbarTitle()));
        value.setTopbarRight(coalesce(value.getTopbarRight(), defaults.getTopbarRight()));
        value.setPageTitle(coalesce(value.getPageTitle(), defaults.getPageTitle()));
        value.setPageKicker(coalesce(value.getPageKicker(), defaults.getPageKicker()));
        value.setMarketCard(normalizeMarketCard(value.getMarketCard(), defaults.getMarketCard()));
        value.setNationalCard(normalizeCard(value.getNationalCard(), defaults.getNationalCard()));
        value.setBridgeText(coalesce(value.getBridgeText(), defaults.getBridgeText()));
        value.setRegionalCard(normalizeCard(value.getRegionalCard(), defaults.getRegionalCard()));
        value.setNarrative(normalizeNarrative(value.getNarrative(), defaults.getNarrative()));
        value.setFootnote(coalesce(value.getFootnote(), defaults.getFootnote()));
        value.setFooterBrand(coalesce(value.getFooterBrand(), defaults.getFooterBrand()));
        return value;
    }

    private MarketBattleground.MarketCard normalizeMarketCard(MarketBattleground.MarketCard value,
                                                              MarketBattleground.MarketCard defaults) {
        if (value == null) return defaults;
        value.setLabel(coalesce(value.getLabel(), defaults.getLabel()));
        value.setSource(coalesce(value.getSource(), defaults.getSource()));
        value.setStats(normalizeList(value.getStats(), defaults.getStats(), this::normalizeStat));
        value.setPlatformLabel(coalesce(value.getPlatformLabel(), defaults.getPlatformLabel()));
        value.setPlatforms(normalizeList(value.getPlatforms(), defaults.getPlatforms(), this::normalizePlatform));
        value.setPlatformSuffix(coalesce(value.getPlatformSuffix(), defaults.getPlatformSuffix()));
        return value;
    }

    private MarketBattleground.Stat normalizeStat(MarketBattleground.Stat value, MarketBattleground.Stat defaults) {
        if (value == null) return defaults;
        value.setValue(coalesce(value.getValue(), defaults.getValue()));
        value.setUnit(coalesce(value.getUnit(), defaults.getUnit()));
        value.setLabel(coalesce(value.getLabel(), defaults.getLabel()));
        return value;
    }

    private MarketBattleground.Platform normalizePlatform(MarketBattleground.Platform value,
                                                          MarketBattleground.Platform defaults) {
        if (value == null) return defaults;
        value.setName(coalesce(value.getName(), defaults.getName()));
        value.setValue(coalesce(value.getValue(), defaults.getValue()));
        return value;
    }

    private MarketBattleground.CalculationCard normalizeCard(MarketBattleground.CalculationCard value,
                                                             MarketBattleground.CalculationCard defaults) {
        if (value == null) return defaults;
        value.setLabel(coalesce(value.getLabel(), defaults.getLabel()));
        value.setValuePrefix(coalesce(value.getValuePrefix(), defaults.getValuePrefix()));
        value.setValue(coalesce(value.getValue(), defaults.getValue()));
        value.setUnit(coalesce(value.getUnit(), defaults.getUnit()));
        value.setSubtitle(coalesce(value.getSubtitle(), defaults.getSubtitle()));
        value.setCalculationLabel(coalesce(value.getCalculationLabel(), defaults.getCalculationLabel()));
        value.setRows(normalizeList(value.getRows(), defaults.getRows(), this::normalizeCalcRow));
        return value;
    }

    private MarketBattleground.CalculationRow normalizeCalcRow(MarketBattleground.CalculationRow value,
                                                               MarketBattleground.CalculationRow defaults) {
        if (value == null) return defaults;
        value.setLabel(coalesce(value.getLabel(), defaults.getLabel()));
        value.setValue(coalesce(value.getValue(), defaults.getValue()));
        value.setIsTotal(value.getIsTotal() == null ? defaults.getIsTotal() : value.getIsTotal());
        return value;
    }

    private MarketBattleground.Narrative normalizeNarrative(MarketBattleground.Narrative value,
                                                            MarketBattleground.Narrative defaults) {
        if (value == null) return defaults;
        value.setIntro(coalesce(value.getIntro(), defaults.getIntro()));
        value.setQuestions(normalizeStringList(value.getQuestions(), defaults.getQuestions()));
        value.setConclusion(coalesce(value.getConclusion(), defaults.getConclusion()));
        value.setBrandLinePrefix(coalesce(value.getBrandLinePrefix(), defaults.getBrandLinePrefix()));
        value.setBrandName(coalesce(value.getBrandName(), defaults.getBrandName()));
        value.setBrandLineSuffix(coalesce(value.getBrandLineSuffix(), defaults.getBrandLineSuffix()));
        return value;
    }

    private <T> List<T> normalizeList(List<T> value, List<T> defaults, java.util.function.BiFunction<T, T, T> merger) {
        List<T> out = new ArrayList<>();
        for (int i = 0; i < defaults.size(); i++) {
            T existing = value == null || i >= value.size() ? null : value.get(i);
            out.add(merger.apply(existing, defaults.get(i)));
        }
        return out;
    }

    private List<String> normalizeStringList(List<String> value, List<String> defaults) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < defaults.size(); i++) {
            String existing = value == null || i >= value.size() ? null : value.get(i);
            out.add(coalesce(existing, defaults.get(i)));
        }
        return out;
    }

    private String coalesce(String value, String defaultValue) {
        return value == null ? defaultValue : value;
    }

    private MarketBattleground.Stat stat(String value, String unit, String label) {
        return MarketBattleground.Stat.builder().value(value).unit(unit).label(label).build();
    }

    private MarketBattleground.Platform platform(String name, String value) {
        return MarketBattleground.Platform.builder().name(name).value(value).build();
    }

    private MarketBattleground.CalculationRow calcRow(String label, String value, boolean isTotal) {
        return MarketBattleground.CalculationRow.builder().label(label).value(value).isTotal(isTotal).build();
    }

    private void meta(List<EditableFieldMetaVO> out, String field, String label, String block,
                      int maxLength, int warnLength) {
        out.add(EditableFieldMetaVO.builder()
                .field(field)
                .label(label)
                .block(block)
                .maxLength(maxLength)
                .warnLength(warnLength)
                .build());
    }
}
