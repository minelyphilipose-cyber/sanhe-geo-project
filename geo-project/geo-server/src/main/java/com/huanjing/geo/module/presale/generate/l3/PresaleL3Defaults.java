package com.huanjing.geo.module.presale.generate.l3;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.presale.dto.response.EditableFieldMetaVO;
import com.huanjing.geo.module.presale.dto.snapshot.computed.ComputedSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.editable.EditableContentDTO;
import com.huanjing.geo.module.presale.dto.snapshot.editable.MarketBattleground;
import com.huanjing.geo.module.presale.dto.snapshot.raw.RawSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.raw.SamplePrompt;
import com.huanjing.geo.module.presale.persist.entity.PresalePage03MarketConfig;
import com.huanjing.geo.module.presale.service.PresalePage03MarketConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * L3 默认值、字段元数据和 schema 归一化的后端权威源。
 */
@Component
@RequiredArgsConstructor
public class PresaleL3Defaults {

    private static final int MARKET_NARRATIVE_QUESTION_MAX_LENGTH = 34;

    private final ObjectMapper objectMapper;
    private final PresalePage03MarketConfigService page03MarketConfigService;

    public EditableContentDTO normalize(EditableContentDTO input, RawSnapshotDTO raw, ComputedSnapshotDTO computed) {
        return normalize(input, raw, computed, false);
    }

    public EditableContentDTO normalizeGenerated(EditableContentDTO input, RawSnapshotDTO raw, ComputedSnapshotDTO computed) {
        return normalize(input, raw, computed, true);
    }

    private EditableContentDTO normalize(EditableContentDTO input,
                                         RawSnapshotDTO raw,
                                         ComputedSnapshotDTO computed,
                                         boolean blankAsMissing) {
        EditableContentDTO out = input == null ? new EditableContentDTO() : input;
        if (out.getMarketBattleground() == null) {
            out.setMarketBattleground(defaultMarketBattleground(raw));
        } else {
            out.setMarketBattleground(normalizeMarket(
                    out.getMarketBattleground(),
                    defaultMarketBattleground(raw),
                    blankAsMissing
            ));
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
        meta(out, "market_battleground." + card + ".label", "卡片标签", block,
                MarketBattlegroundValidator.CALCULATION_CARD_LABEL_MAX_LENGTH, 30);
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
        String region = raw == null || raw.getClientInfo() == null || isBlank(raw.getClientInfo().getRegion())
                ? "本地" : raw.getClientInfo().getRegion();
        String industry = raw == null || raw.getClientInfo() == null
                ? null : raw.getClientInfo().getIndustry();
        IndustryProfile profile = resolveIndustryProfile(industry);
        PresalePage03MarketConfig config = page03MarketConfigService.getConfig();
        MarketScale scale = estimateMarketScale(region, profile, config);

        return MarketBattleground.builder()
                .topbarTitle(MarketBattlegroundValidator.TOPBAR_TITLE)
                .topbarRight(MarketBattlegroundValidator.TOPBAR_RIGHT)
                .pageTitle("每天，有数千万次消费决策正在 AI 上发生")
                .pageKicker(MarketBattlegroundValidator.PAGE_KICKER)
                .marketCard(MarketBattleground.MarketCard.builder()
                        .label(config.getMarketLabel())
                        .source(config.getMarketSource())
                        .stats(List.of(
                                stat(config.getAppMonthlyActiveValue(), config.getAppMonthlyActiveUnit(), "AI 原生 APP 月活"),
                                stat(config.getDailyActiveUsersValue(), config.getDailyActiveUsersUnit(), "日均活跃用户"),
                                stat(config.getDailyQuestionTotalValue(), config.getDailyQuestionTotalUnit(), "日均提问总量"),
                                stat(config.getDoubaoMonthlyUsageValue(), config.getDoubaoMonthlyUsageUnit(), "豆包人均月使用")
                        ))
                        .platformLabel(MarketBattlegroundValidator.PLATFORM_LABEL)
                        .platforms(List.of(
                                platform(config.getPlatform1Name(), config.getPlatform1Value()),
                                platform(config.getPlatform2Name(), config.getPlatform2Value()),
                                platform(config.getPlatform3Name(), config.getPlatform3Value())
                        ))
                        .platformSuffix(config.getPlatformSuffix())
                        .build())
                .nationalCard(MarketBattleground.CalculationCard.builder()
                        .label("NATIONAL · 全国" + profile.industryLabel() + "每天")
                        .valuePrefix("约")
                        .value(scale.nationalValue())
                        .unit(scale.nationalUnit())
                        .subtitle("条 / 天 · " + profile.industryLabel() + "相关 AI 提问")
                        .calculationLabel("CALCULATION · 推导口径")
                        .rows(List.of(
                                calcRow("日均提问总量", "约 " + config.getDailyQuestionTotalValue() + config.getDailyQuestionTotalUnit() + " / 天", false),
                                calcRow(profile.parentCategory() + "类占比", profile.parentShareRange(), false),
                                calcRow(profile.industryLabel() + "占比", profile.industryShareRange(), false),
                                calcRow("中枢值", scale.nationalTotalText(), true)
                        ))
                        .build())
                .bridgeText(MarketBattlegroundValidator.BRIDGE_TEXT)
                .regionalCard(MarketBattleground.CalculationCard.builder()
                        .label("REGIONAL · " + region + profile.industryLabel() + "每天")
                        .valuePrefix("约")
                        .value(scale.regionalValue())
                        .unit(scale.regionalUnit())
                        .subtitle("条 / 天 · " + region + "消费者向 AI 提问")
                        .calculationLabel("CALCULATION · 推导口径")
                        .rows(List.of(
                                calcRow("全国" + profile.industryLabel() + "日提问", scale.nationalTotalText(), false),
                                calcRow(region + "占比", scale.regionShareText(), false),
                                calcRow("数据来源", config.getPage03DataSource(), false),
                                calcRow("区域日提问", scale.regionalTotalText(), true)
                        ))
                        .build())
                .narrative(MarketBattleground.Narrative.builder()
                        .intro("这意味着，消费者正在通过 AI 持续询问：")
                        .questions(buildDecisionQuestions(raw, region, profile, brand))
                        .conclusion("而 AI 给出的答案，正在影响他们下一步选择。")
                        .brandLinePrefix(MarketBattlegroundValidator.BRAND_LINE_PREFIX)
                        .brandName(brand)
                        .brandLineSuffix("在这些场景中的真实可见度如何？详见下章诊断结果。")
                        .build())
                .footnote(config.getFootnote())
                .footerBrand("GEO · CONFIDENTIAL")
                .build();
    }

    private IndustryProfile resolveIndustryProfile(String industry) {
        String key = industry == null ? "" : industry.trim().toLowerCase();
        return switch (key) {
            case "medical_beauty", "medical_beauty_hospital", "医美", "医疗美容" ->
                    new IndustryProfile("医美", "生活/美容", "约 0.8% - 2.0%", "约 12% - 25%",
                            List.of("做双眼皮哪家更自然？", "光子嫩肤选哪家性价比高？", "面部填充推荐哪家医生？"));
            case "dental", "口腔" ->
                    new IndustryProfile("口腔", "医疗/健康", "约 1.0% - 2.5%", "约 8% - 18%",
                            List.of("种植牙哪家机构更靠谱？", "牙齿矫正选哪家性价比高？", "儿童齿科推荐哪家医生？"));
            case "hair_transplant", "植发" ->
                    new IndustryProfile("植发", "医疗/美容", "约 0.8% - 2.0%", "约 3% - 8%",
                            List.of("植发哪家机构成活率高？", "发际线种植选哪家更自然？", "脱发治疗推荐哪家医生？"));
            case "home_decoration", "decoration", "家装", "装修" ->
                    new IndustryProfile("家装", "生活/居住", "约 1.5% - 3.5%", "约 18% - 35%",
                            List.of("装修公司哪家更靠谱？", "全屋装修选哪家性价比高？", "装修避坑找哪家公司？"));
            case "education", "教培", "教育" ->
                    new IndustryProfile("教培", "教育/学习", "约 1.5% - 4.0%", "约 10% - 28%",
                            List.of("课程培训哪家更有效？", "辅导机构选哪家性价比高？", "孩子补课推荐哪家老师？"));
            case "local_food", "restaurant", "餐饮", "本地餐饮" ->
                    new IndustryProfile("餐饮", "生活/消费", "约 2.0% - 5.0%", "约 15% - 35%",
                            List.of("聚餐去哪家更合适？", "附近哪家餐厅性价比高？", "请客吃饭推荐哪家店？"));
            case "auto_service", "汽车服务" ->
                    new IndustryProfile("汽车服务", "生活/出行", "约 1.0% - 2.5%", "约 12% - 25%",
                            List.of("汽车保养哪家门店靠谱？", "修车选哪家性价比高？", "洗美养护推荐哪家店？"));
            default ->
                    new IndustryProfile(isBlank(industry) ? "本地服务" : industry, "生活/服务",
                            "约 0.8% - 3.0%", "约 5% - 25%",
                            List.of("服务机构哪家更靠谱？", "选哪家性价比更高？", "本地推荐哪家更合适？"));
        };
    }

    private MarketScale estimateMarketScale(String region, IndustryProfile profile, PresalePage03MarketConfig config) {
        double dailyQuestions = configDailyQuestionCount(config);
        double national = dailyQuestions * midpoint(profile.parentShareRange()) * midpoint(profile.industryShareRange());
        double regionShare = estimateRegionShare(region);
        double regional = national * regionShare;
        return new MarketScale(
                compactValue(national),
                compactUnit(national, true),
                totalText(national),
                compactValue(regional),
                compactUnit(regional, false),
                totalText(regional),
                "约 " + formatPercent(regionShare)
        );
    }

    private double configDailyQuestionCount(PresalePage03MarketConfig config) {
        Double parsed = parseCount(config.getDailyQuestionTotalValue() + config.getDailyQuestionTotalUnit());
        return parsed == null ? 1_200_000_000D : parsed;
    }

    private List<String> buildDecisionQuestions(RawSnapshotDTO raw,
                                                String region,
                                                IndustryProfile profile,
                                                String brandName) {
        List<String> samplePrompts = raw == null || raw.getSamplePrompts() == null
                ? List.of()
                : raw.getSamplePrompts().stream()
                .filter(item -> item != null && !isBlank(item.getPromptContent()))
                .map(SamplePrompt::getPromptContent)
                .distinct()
                .limit(3)
                .toList();
        if (samplePrompts.size() == 3) {
            return samplePrompts.stream()
                    .map(this::quoteQuestion)
                    .map(question -> compactQuestion(question, brandName))
                    .toList();
        }
        return profile.questionSuffixes().stream()
                .map(question -> "\"" + region + question + "\"")
                .map(question -> compactQuestion(question, brandName))
                .toList();
    }

    private String quoteQuestion(String value) {
        String text = value.trim();
        if ((text.startsWith("\"") && text.endsWith("\""))
                || (text.startsWith("“") && text.endsWith("”"))) {
            return text;
        }
        return "\"" + text + "\"";
    }

    private double midpoint(String rangeText) {
        Matcher matcher = Pattern.compile("(\\d+(?:\\.\\d+)?)%").matcher(rangeText);
        List<Double> values = new ArrayList<>();
        while (matcher.find()) {
            values.add(Double.parseDouble(matcher.group(1)) / 100D);
        }
        if (values.size() >= 2) {
            return (values.get(0) + values.get(1)) / 2D;
        }
        if (values.size() == 1) {
            return values.get(0);
        }
        return 0.01D;
    }

    private double estimateRegionShare(String region) {
        String value = region == null ? "" : region;
        if (value.contains("北京") || value.contains("上海") || value.contains("广州") || value.contains("深圳")) {
            return 0.012D;
        }
        if (value.contains("阜阳")) {
            return 0.0006D;
        }
        if (value.contains("县")) {
            return 0.0003D;
        }
        return 0.0008D;
    }

    private String compactValue(double value) {
        if (value >= 100_000_000D) {
            return formatOneDecimal(value / 100_000_000D);
        }
        return formatOneDecimal(value / 10_000D);
    }

    private String compactUnit(double value, boolean national) {
        if (value >= 100_000_000D) {
            return national ? "亿+" : "亿";
        }
        return national ? "万+" : "万";
    }

    private String totalText(double value) {
        return "约 " + compactValue(value) + " " + (value >= 100_000_000D ? "亿" : "万") + "条 / 天";
    }

    private String formatPercent(double value) {
        double percent = value * 100D;
        if (percent >= 1D) {
            return formatOneDecimal(percent) + "%";
        }
        return String.format(java.util.Locale.ROOT, "%.2f%%", percent);
    }

    private String formatOneDecimal(double value) {
        String text = String.format(java.util.Locale.ROOT, "%.1f", value);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }

    private String formatOneDecimalFixed(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private MarketBattleground normalizeMarket(MarketBattleground value,
                                               MarketBattleground defaults,
                                               boolean blankAsMissing) {
        value.setTopbarTitle(defaults.getTopbarTitle());
        value.setTopbarRight(defaults.getTopbarRight());
        value.setPageTitle(coalesce(value.getPageTitle(), defaults.getPageTitle(), blankAsMissing));
        value.setPageKicker(defaults.getPageKicker());
        value.setMarketCard(normalizeMarketCard(value.getMarketCard(), defaults.getMarketCard(), blankAsMissing));
        value.setNationalCard(normalizeCard(value.getNationalCard(), defaults.getNationalCard(), blankAsMissing));
        value.setBridgeText(defaults.getBridgeText());
        value.setRegionalCard(normalizeCard(value.getRegionalCard(), defaults.getRegionalCard(), blankAsMissing));
        value.setNarrative(normalizeNarrative(value.getNarrative(), defaults.getNarrative(), blankAsMissing));
        value.setFootnote(coalesce(value.getFootnote(), defaults.getFootnote(), blankAsMissing));
        value.setFooterBrand(coalesce(value.getFooterBrand(), defaults.getFooterBrand(), blankAsMissing));
        recalculateMarketTraffic(value);
        return value;
    }

    private void recalculateMarketTraffic(MarketBattleground value) {
        if (value == null
                || value.getNationalCard() == null
                || value.getRegionalCard() == null
                || value.getNationalCard().getRows() == null
                || value.getRegionalCard().getRows() == null
                || value.getNationalCard().getRows().size() < 4
                || value.getRegionalCard().getRows().size() < 4) {
            return;
        }

        List<MarketBattleground.CalculationRow> nationalRows = value.getNationalCard().getRows();
        List<MarketBattleground.CalculationRow> regionalRows = value.getRegionalCard().getRows();
        Double dailyQuestionCount = parseCount(nationalRows.get(0).getValue());
        Double parentCategoryShare = parsePercent(nationalRows.get(1).getValue());
        Double industryShare = parsePercent(nationalRows.get(2).getValue());
        Double regionShare = parsePercent(regionalRows.get(1).getValue());
        if (dailyQuestionCount == null || parentCategoryShare == null || industryShare == null || regionShare == null) {
            return;
        }

        double nationalTotal = dailyQuestionCount * parentCategoryShare * industryShare;
        double regionalTotal = nationalTotal * regionShare;
        if (nationalTotal <= 0D || regionalTotal <= 0D
                || Double.isNaN(nationalTotal) || Double.isNaN(regionalTotal)
                || Double.isInfinite(nationalTotal) || Double.isInfinite(regionalTotal)) {
            return;
        }

        FormattedTraffic national = formatTraffic(nationalTotal);
        FormattedTraffic regional = formatTraffic(regionalTotal);
        value.getNationalCard().setValue(national.value());
        value.getNationalCard().setUnit(national.unit());
        nationalRows.get(3).setValue(national.text());

        value.getRegionalCard().setValue(regional.value());
        value.getRegionalCard().setUnit(regional.unit());
        regionalRows.get(0).setValue(national.text());
        regionalRows.get(3).setValue(regional.text());
    }

    private Double parseCount(String text) {
        Double number = firstNumber(text);
        if (number == null) {
            return null;
        }
        String value = text == null ? "" : text.replace(" ", "");
        if (value.contains("亿")) {
            return number * 100_000_000D;
        }
        if (value.contains("万")) {
            return number * 10_000D;
        }
        return null;
    }

    private Double parsePercent(String text) {
        if (text == null || !text.contains("%")) {
            return null;
        }
        Double number = firstNumber(text);
        if (number == null) {
            return null;
        }
        return number / 100D;
    }

    private Double firstNumber(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = Pattern.compile("(\\d+(?:\\.\\d+)?)").matcher(text);
        if (!matcher.find()) {
            return null;
        }
        return Double.parseDouble(matcher.group(1));
    }

    private FormattedTraffic formatTraffic(double count) {
        if (count >= 10_000D) {
            String value = formatOneDecimalFixed(count / 10_000D);
            return new FormattedTraffic(value, "万次", value + "万次");
        }
        String value = String.valueOf(Math.round(count));
        return new FormattedTraffic(value, "次", value + "次");
    }

    private MarketBattleground.MarketCard normalizeMarketCard(MarketBattleground.MarketCard value,
                                                              MarketBattleground.MarketCard defaults,
                                                              boolean blankAsMissing) {
        if (value == null) return defaults;
        value.setLabel(coalesce(value.getLabel(), defaults.getLabel(), blankAsMissing));
        value.setSource(coalesce(value.getSource(), defaults.getSource(), blankAsMissing));
        value.setStats(normalizeList(value.getStats(), defaults.getStats(),
                (item, itemDefaults) -> normalizeStat(item, itemDefaults, blankAsMissing)));
        value.setPlatformLabel(defaults.getPlatformLabel());
        value.setPlatforms(normalizeList(value.getPlatforms(), defaults.getPlatforms(),
                (item, itemDefaults) -> normalizePlatform(item, itemDefaults, blankAsMissing)));
        value.setPlatformSuffix(defaults.getPlatformSuffix());
        return value;
    }

    private MarketBattleground.Stat normalizeStat(MarketBattleground.Stat value,
                                                  MarketBattleground.Stat defaults,
                                                  boolean blankAsMissing) {
        if (value == null) return defaults;
        value.setValue(coalesce(value.getValue(), defaults.getValue(), blankAsMissing));
        value.setUnit(coalesce(value.getUnit(), defaults.getUnit(), blankAsMissing));
        value.setLabel(coalesce(value.getLabel(), defaults.getLabel(), blankAsMissing));
        return value;
    }

    private MarketBattleground.Platform normalizePlatform(MarketBattleground.Platform value,
                                                          MarketBattleground.Platform defaults,
                                                          boolean blankAsMissing) {
        if (value == null) return defaults;
        value.setName(coalesce(value.getName(), defaults.getName(), blankAsMissing));
        value.setValue(coalesce(value.getValue(), defaults.getValue(), blankAsMissing));
        return value;
    }

    private MarketBattleground.CalculationCard normalizeCard(MarketBattleground.CalculationCard value,
                                                             MarketBattleground.CalculationCard defaults,
                                                             boolean blankAsMissing) {
        if (value == null) return defaults;
        value.setLabel(coalesce(value.getLabel(), defaults.getLabel(), blankAsMissing));
        value.setValuePrefix(coalesce(value.getValuePrefix(), defaults.getValuePrefix(), blankAsMissing));
        value.setValue(coalesce(value.getValue(), defaults.getValue(), blankAsMissing));
        value.setUnit(coalesce(value.getUnit(), defaults.getUnit(), blankAsMissing));
        value.setSubtitle(coalesce(value.getSubtitle(), defaults.getSubtitle(), blankAsMissing));
        value.setCalculationLabel(coalesce(value.getCalculationLabel(), defaults.getCalculationLabel(), blankAsMissing));
        value.setRows(normalizeList(value.getRows(), defaults.getRows(),
                (row, rowDefaults) -> normalizeCalcRow(row, rowDefaults, blankAsMissing)));
        return value;
    }

    private MarketBattleground.CalculationRow normalizeCalcRow(MarketBattleground.CalculationRow value,
                                                               MarketBattleground.CalculationRow defaults,
                                                               boolean blankAsMissing) {
        if (value == null) return defaults;
        value.setLabel(coalesce(value.getLabel(), defaults.getLabel(), blankAsMissing));
        value.setValue(coalesce(value.getValue(), defaults.getValue(), blankAsMissing));
        value.setIsTotal(value.getIsTotal() == null ? defaults.getIsTotal() : value.getIsTotal());
        return value;
    }

    private MarketBattleground.Narrative normalizeNarrative(MarketBattleground.Narrative value,
                                                            MarketBattleground.Narrative defaults,
                                                            boolean blankAsMissing) {
        if (value == null) return defaults;
        value.setIntro(coalesce(value.getIntro(), defaults.getIntro(), blankAsMissing));
        String brandName = coalesce(value.getBrandName(), defaults.getBrandName(), blankAsMissing);
        value.setQuestions(normalizeQuestionList(value.getQuestions(), defaults.getQuestions(), brandName, blankAsMissing));
        value.setConclusion(coalesce(value.getConclusion(), defaults.getConclusion(), blankAsMissing));
        value.setBrandLinePrefix(defaults.getBrandLinePrefix());
        value.setBrandName(brandName);
        value.setBrandLineSuffix(coalesce(value.getBrandLineSuffix(), defaults.getBrandLineSuffix(), blankAsMissing));
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

    private List<String> normalizeStringList(List<String> value, List<String> defaults, boolean blankAsMissing) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < defaults.size(); i++) {
            String existing = value == null || i >= value.size() ? null : value.get(i);
            out.add(coalesce(existing, defaults.get(i), blankAsMissing));
        }
        return out;
    }

    private List<String> normalizeQuestionList(List<String> value,
                                               List<String> defaults,
                                               String brandName,
                                               boolean blankAsMissing) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < defaults.size(); i++) {
            String existing = value == null || i >= value.size() ? null : value.get(i);
            String fallback = compactQuestion(defaults.get(i), brandName);
            String question = coalesce(existing, fallback, blankAsMissing);
            if (!isValidQuestion(question, brandName)) {
                question = fallback;
            }
            out.add(compactQuestion(question, brandName));
        }
        return out;
    }

    private boolean isValidQuestion(String value, String brandName) {
        return !isBlank(value)
                && value.length() <= MARKET_NARRATIVE_QUESTION_MAX_LENGTH
                && (isBlank(brandName) || !value.contains(brandName));
    }

    private String compactQuestion(String value, String brandName) {
        String text = isBlank(value) ? "本地服务机构哪家更合适？" : value.trim();
        if (!isBlank(brandName)) {
            text = text.replace(brandName, "这类品牌");
        }
        if (text.length() <= MARKET_NARRATIVE_QUESTION_MAX_LENGTH) {
            return text;
        }
        String suffix = text.endsWith("？") || text.endsWith("?") ? "？" : "";
        int maxBodyLength = suffix.isEmpty()
                ? MARKET_NARRATIVE_QUESTION_MAX_LENGTH
                : MARKET_NARRATIVE_QUESTION_MAX_LENGTH - suffix.length();
        String body = suffix.isEmpty() ? text : text.substring(0, text.length() - 1);
        return body.substring(0, Math.min(maxBodyLength, body.length())) + suffix;
    }

    private String coalesce(String value, String defaultValue) {
        return coalesce(value, defaultValue, false);
    }

    private String coalesce(String value, String defaultValue, boolean blankAsMissing) {
        if (blankAsMissing && (value == null || value.isBlank())) {
            return defaultValue;
        }
        return value == null ? defaultValue : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
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

    private record IndustryProfile(String industryLabel,
                                   String parentCategory,
                                   String parentShareRange,
                                   String industryShareRange,
                                   List<String> questionSuffixes) {
    }

    private record MarketScale(String nationalValue,
                               String nationalUnit,
                               String nationalTotalText,
                               String regionalValue,
                               String regionalUnit,
                               String regionalTotalText,
                               String regionShareText) {
    }

    private record FormattedTraffic(String value, String unit, String text) {
    }
}
