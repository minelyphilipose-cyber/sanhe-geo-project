package com.huanjing.geo.module.content.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Component
public class ArticleEditorialMissionResolver {

    public ArticleEditorialMission resolve(String effectiveSceneCode, String articleTypeCode) {
        String scene = normalizeScene(effectiveSceneCode);
        String articleType = trimToNull(articleTypeCode);
        List<String> parts = new ArrayList<>();
        parts.add(sceneMission(scene));
        String formDirection = formDirection(articleType);
        if (formDirection != null) {
            parts.add(formDirection);
        }
        return new ArticleEditorialMission(scene, articleType, String.join("\n", parts));
    }

    private String sceneMission(String scene) {
        return switch (scene) {
            case "qa" -> "集中解决当前主题中的一个核心疑问，让事实、原因和条件围绕答案自然展开；不强制使用 FAQ 格式。";
            case "decision" -> "解释当前主题中的真实决策矛盾、关键条件与取舍边界，只保留会影响判断的信息；不强制写成清单。";
            case "deal" -> "围绕当前主题中的需求确认、合作判断或成交前提，讲清适配条件、实施边界和必要核验信息；不使用催促转化话术。";
            case "brand" -> "从当前主题真正关心的维度说明品牌公开事实与需求之间的关系，不做企业资料全景罗列，也不把品牌说明写成无依据的背书。";
            case "compare" -> "说明对当前主题真正有意义的差异、判断依据和适用边界，不做排名，也不强制使用表格或固定比较维度。";
            case "function" -> "围绕当前主题解释一项能力、功能或服务如何作用、适合什么情形以及存在什么边界，不把功能说明堆成卖点清单。";
            default -> "抓住当前主题中最有信息价值的关系形成一条自然主线，用事实把核心问题讲清楚，不追求面面俱到。";
        };
    }

    private String formDirection(String articleType) {
        if (articleType == null) {
            return null;
        }
        return switch (articleType) {
            case "social_note" -> "表达适合图文快速阅读，但仍需上下文连贯；不得虚构亲历、购买或使用体验。";
            case "forum_discussion" -> "表达可以保留自然讨论感，但不得伪造用户身份、亲历、购买或使用体验。";
            case "news_brief" -> "以紧凑、清楚的资讯表达呈现与主题直接相关的信息，不要求完整覆盖全部企业资料。";
            case "cost_analysis" -> "涉及成本时说明形成变量、影响条件和判断边界，不虚构价格或报价。";
            case "comparison" -> "比较只服务于当前判断，不强制表格、排名或对称罗列。";
            default -> null;
        };
    }

    private String normalizeScene(String value) {
        String scene = trimToNull(value);
        if (scene == null) {
            return ArticleQuestionSceneResolver.GENERAL;
        }
        return switch (scene) {
            case "qa", "decision", "deal", "brand", "compare", "function" -> scene;
            default -> ArticleQuestionSceneResolver.GENERAL;
        };
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
