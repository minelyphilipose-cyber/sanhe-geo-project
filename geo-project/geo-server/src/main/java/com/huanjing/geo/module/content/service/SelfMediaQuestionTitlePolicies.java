package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.project.entity.Project;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Stream;

final class SelfMediaQuestionTitlePolicies {
    private SelfMediaQuestionTitlePolicies() {}

    static boolean applies(String group, String sub) {
        return ArticlePromptChannels.SELF_MEDIA.equals(group)
                && ("sohu".equals(sub) || "toutiao".equals(sub));
    }

    static List<String> brandNames(Project project, Brand brand) {
        return Stream.of(brand == null ? null : brand.getBrandName(),
                        brand == null ? null : brand.getBrandShortName(),
                        project == null ? null : project.getBrandName())
                .filter(StringUtils::hasText).map(String::trim).distinct().toList();
    }

    static String region(Project project, Brand brand) {
        return Stream.of(brand == null ? null : brand.getCityName(),
                        project == null ? null : project.getCityName(),
                        brand == null ? null : brand.getDistrictName(),
                        project == null ? null : project.getDistrictName(),
                        brand == null ? null : brand.getProvinceName(),
                        project == null ? null : project.getProvinceName())
                .filter(StringUtils::hasText).map(String::trim).findFirst().orElse(null);
    }

    static String titleRequirement(Project project, Brand brand, String platform) {
        List<String> names = brandNames(project, brand);
        String region = region(project, brand);
        String subject = region != null && !names.isEmpty()
                ? "标题必须包含本篇主体地域“" + region + "”（可用无歧义简称）和品牌名称“"
                    + names.get(0) + "”（可使用已配置品牌简称）。"
                : "标题自然带入已提供的真实地域和品牌名称，可使用已配置简称；资料缺失时不得虚构地域或品牌。"
                    + (region == null ? "" : "已提供的主体地域：" + region + "。")
                    + (names.isEmpty() ? "" : "已提供的品牌名称：" + names.get(0) + "。");
        int maxTitleChars = ArticlePromptChannels.maxTitleChars(ArticlePromptChannels.SELF_MEDIA, platform);
        return subject + "第三方视角同样使用本篇所选主体的品牌和地域，不要借用发布账号的资料。"
                + "围绕所选 A 级问题拟标题，地域和品牌自然带入。"
                + "涉及品牌的表达采用开放式疑问句或反问句，不预设肯定答案，把品牌评价留给正文依据事实回答。"
                + "可以先提地域行业问题，再接地域品牌追问；也可以直接提出品牌的具体问题，再接相关追问。"
                + "一句或两句均可，不要求所有标题使用相同句式。追问与 A 级选题相关，紧扣其核心关切，并能依据已有品牌资料回答。"
                + "随选题改变追问关注点，不把不同问题都转向同一个产品或服务维度；避免“能说明什么”等空泛追问。"
                + "避免把品牌写成解答者或肯定结论，例如“品牌帮你理清”“品牌说清楚”；"
                + "也不以“先看懂、再判断”等说明式导语替代品牌追问。"
                + "也避免“为什么这么受欢迎”“为什么值得信赖”等预设赞扬的问法，以及首选、强烈推荐、闭眼选等强推荐表达。"
                + "地域和品牌都计入" + maxTitleChars + "字上限，不能为缩短标题而省略。"
                + ("toutiao".equals(platform)
                    ? "优先用一句完整、具体的疑问表达；两句仅在字数允许且各自完整时使用。缩短非必要措辞，不堆叠多个追问。"
                    : "");
    }
}
