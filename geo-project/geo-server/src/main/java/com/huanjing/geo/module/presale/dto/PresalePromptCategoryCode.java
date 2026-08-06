package com.huanjing.geo.module.presale.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum PresalePromptCategoryCode {
    RECOMMENDATION(
            "推荐型",
            "REC",
            "高",
            "定位: 用户有购买意图但未锁定品牌,希望大模型直接推荐商家或品牌,测试品牌在自然语境下的 AI 召回能力。\n"
                    + "要点: 包含“哪家好”“推荐”“选哪个”等求推荐信号词;带地域、预算或场景限定;必须以行业、品类或需求为主体，不出现任何具体品牌名。"
    ),
    COMPARISON(
            "对比型",
            "CMP",
            "高",
            "定位: 用户在多个候选品牌间纠结,核心是目标品牌与竞品 {competitor} 的横向比较,测试 AI 在多品牌并列时对目标品牌的呈现立场。\n"
                    + "要点: 必须使用 {competitor} 占位符,竞品由调用方动态填充;对比维度可涵盖整体推荐、口碑、性价比、生态、售后、适用人群、特定功能等;双方平等并列,避免明显倾向。"
    ),
    PROBLEM(
            "问题型",
            "PRB",
            "中",
            "定位: 用户带着疑虑、纠结、避坑心态提问,测试 AI 在用户存在负面预设时的品牌呈现倾向。\n"
                    + "要点: 先表达“会不会”“怎么办”“坑不坑”“靠不靠谱”等真实担忧，再追问“该选哪些品牌/哪家服务商/怎样选择更稳妥”；涵盖质量、售后、价格、施工、合同、维护等痛点。必须从行业、地区、场景或用户痛点发问，不直接出现目标品牌名；不能只问风险是否存在，必须让回答有理由自然推荐品牌或服务商。"
    ),
    COGNITIVE(
            "认知型",
            "CGN",
            "中",
            "定位: 用户对品牌或品类已有耳闻但了解不深,希望获取基础信息,测试 AI 对该品牌的认知准确度和呈现倾向。\n"
                    + "要点: 句式以“是什么”“有哪些”“怎么样”等了解型表达为主;可适度提及目标品牌名,占比约 30%-50%;包含部分纯行业认知问题,如行情、价格段、市场格局。"
    ),
    SCENARIO(
            "场景型",
            "SCN",
            "中",
            "定位: 用户描述具体生活情境,如房型、预算、家庭、装修阶段等,寻求针对性建议,最贴近真实决策路径。\n"
                    + "要点: 必须包含具体场景要素,如房型/面积、装修阶段、家庭成员、预算、特殊需求;典型句式为“我家 XX 情况,怎么办/找谁/选啥”;以行业、品类或需求为主体，不出现任何具体品牌名;用户画像需多样化,避免集中。"
    );

    private final String displayName;
    private final String promptCodePrefix;
    private final String defaultBusinessValue;
    private final String generationGuide;

    PresalePromptCategoryCode(String displayName, String promptCodePrefix, String defaultBusinessValue, String generationGuide) {
        this.displayName = displayName;
        this.promptCodePrefix = promptCodePrefix;
        this.defaultBusinessValue = defaultBusinessValue;
        this.generationGuide = generationGuide;
    }

    @JsonCreator
    public static PresalePromptCategoryCode fromJson(String value) {
        return Arrays.stream(values())
                .filter(code -> code.name().equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown prompt category code: " + value));
    }

    @JsonValue
    public String toJson() {
        return name();
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPromptCodePrefix() {
        return promptCodePrefix;
    }

    public String getDefaultBusinessValue() {
        return defaultBusinessValue;
    }

    public String getGenerationGuide() {
        return generationGuide;
    }
}
