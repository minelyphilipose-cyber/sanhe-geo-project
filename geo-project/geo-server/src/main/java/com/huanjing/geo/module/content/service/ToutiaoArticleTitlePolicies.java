package com.huanjing.geo.module.content.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.ContentErrorCodes;
import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.project.entity.Project;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

public final class ToutiaoArticleTitlePolicies {
    private static final Pattern STRONG_RECOMMENDATION = Pattern.compile("强烈推荐|闭眼选|首选|全城最好|全市最好");

    private ToutiaoArticleTitlePolicies() {}

    static boolean applies(String group, String sub) {
        return ArticlePromptChannels.SELF_MEDIA.equals(group) && "toutiao".equals(sub);
    }

    static String titleRequirement(Project project, Brand brand, String selectedTopic) {
        String region = SelfMediaQuestionTitlePolicies.region(project, brand);
        return "标题围绕“真实地域 + A 类问题的核心对象 + 一个具体关切”自然拟定。"
                + (region == null ? "资料缺失时不得虚构地域。"
                : "标题必须自然包含本篇主体地域“" + region + "”（可用无歧义简称），不要借用发布账号的地域。")
                + (StringUtils.hasText(selectedTopic) ? "本篇所选 A 类问题：" + selectedTopic.trim() + "。" : "")
                + "地域已在原问题中出现时不重复添加；保留具体产品、服务或项目词，以及价格、流程、资质、适用条件、选择标准等本篇真正要回答的关切。"
                + "允许自然改写原问题，但不改变其核心对象和问题意图：问收费就围绕收费，问流程就围绕流程，不能统一改成“怎么选”。"
                + "其他关联问题词仅作参考，不得替代本篇选题。"
                + "取消必须带品牌名和必须追问品牌的要求，标题默认不出现品牌名称；明确的品牌事实介绍可以中性保留品牌名，不预设赞扬或推荐结论。"
                + "正文继续按原有品牌、写作视角和联系方式规则处理。"
                + "标题可以使用自然疑问句，也可以使用自然说明句，不强制反问或同一种句式，不使用强烈推荐、首选、闭眼选等强推荐表达。"
                + "例如本篇问题关注收费差异，可围绕价格差别拟题；关注预约流程，则围绕预约前后的具体服务信息拟题，不增加无关追问。"
                + "标题提出的问题必须由正文实际回答，不能为了拟题添加正文没有的项目、费用或结论，不把一家品牌的资料扩大为全城比较、行业排名或市场价格结论。"
                + "随本篇问题和正文改变具体关注点与措辞，不机械拼接地域和行业词，不套用固定句式。"
                + "标题不超过28字，地域和核心对象均计入28字上限；超长时重新拟完整短标题，不直接截断。";
    }

    static String issue(String title, Project project, Brand brand, int maxChars) {
        String basicIssue = publicationIssue(title, maxChars);
        if (basicIssue != null) return basicIssue;
        String region = SelfMediaQuestionTitlePolicies.region(project, brand);
        if (region != null) {
            String shortRegion = region.replaceFirst("(?:自治州|地区|市|区|县|省)$", "");
            if (!title.contains(region) && !(shortRegion.length() >= 2 && title.contains(shortRegion))) {
                return "标题缺少本篇主体地域“" + region + "”";
            }
        }
        return null;
    }

    public static void validateForPublish(String title) {
        String issue = publicationIssue(title, 30);
        if (issue != null) {
            throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST,
                    "TOUTIAO_TITLE_INVALID：头条标题需要修改：" + issue + "；请围绕地域、A 类问题核心对象和具体关切拟定完整标题，不会自动截断");
        }
    }

    private static String publicationIssue(String title, int maxChars) {
        if (!StringUtils.hasText(title) || title.strip().codePointCount(0, title.strip().length()) < 2) {
            return "标题至少需要2字";
        }
        if (title.codePointCount(0, title.length()) > maxChars) return "标题超过" + maxChars + "字";
        if (STRONG_RECOMMENDATION.matcher(title).find()) return "标题包含强推荐或无依据的排名表达";
        return null;
    }
}
