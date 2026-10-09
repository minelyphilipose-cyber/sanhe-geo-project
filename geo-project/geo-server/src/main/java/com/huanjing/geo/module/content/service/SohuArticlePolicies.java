package com.huanjing.geo.module.content.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.ContentErrorCodes;
import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.project.entity.Project;
import org.springframework.util.StringUtils;

import java.util.List;

final class SohuArticlePolicies {
    private SohuArticlePolicies() {}

    static boolean applies(String group, String sub) {
        return ArticlePromptChannels.SELF_MEDIA.equals(group) && "sohu".equals(sub);
    }

    static String titleRequirement(Project project, Brand brand) {
        List<String> names = SelfMediaQuestionTitlePolicies.brandNames(project, brand);
        String region = SelfMediaQuestionTitlePolicies.region(project, brand);
        if (names.isEmpty() || region == null) {
            throw invalid("搜狐文章需要真实品牌名称和地域，请先补齐品牌或项目资料");
        }
        return SelfMediaQuestionTitlePolicies.titleRequirement(project, brand, "sohu");
    }

    static void validateTitle(String title, Project project, Brand brand) {
        titleRequirement(project, brand);
        String region = SelfMediaQuestionTitlePolicies.region(project, brand);
        String shortRegion = region.replaceFirst("(?:自治州|地区|市|区|县|省)$", "");
        boolean hasRegion = title.contains(region) || (shortRegion.length() >= 2 && title.contains(shortRegion));
        if (!hasRegion || SelfMediaQuestionTitlePolicies.brandNames(project, brand).stream().noneMatch(title::contains)) {
            throw invalid("搜狐标题必须同时包含本篇主体的地域和品牌名称，请重新生成（当前标题：" + title + "）");
        }
        if (title.codePointCount(0, title.length()) > ArticlePromptChannels.SOHU_MAX_TITLE_CHARS) {
            throw invalid("搜狐标题超过72字，请重新生成完整标题，不会截断地域或品牌名称");
        }
    }

    static String bodyRequirement() {
        return "正文必须完整回答或解释最终标题，围绕所选 A 级问题保持主线。"
                + "结合已提供的真实品牌资料，细致说明与主题相关的业务、产品或服务，突出品牌优势与特点，"
                + "并解释事实依据和适用条件；不能只点名品牌，也不要写成独立广告块。"
                + "不强制固定提纲或品牌出现次数，不虚构资质、案例、数据、效果或优势，不以强推荐和咨询导流收尾。";
    }

    static String contactRequirement(Brand brand) {
        String address = brand == null ? null : brand.getPublicAddress();
        return "联系方式最多仅可展示已配置的公开地址；不展示电话、微信、QQ、邮箱、官网、网址或二维码，"
                + "不添加咨询、预约或联系引导。"
                + (StringUtils.hasText(address) ? "如需说明位置，只能使用此地址，不得改写或补造：" + address.trim()
                : "当前未配置公开地址，省略地址，不得自行补写。");
    }

    private static BizException invalid(String message) {
        return new BizException(ContentErrorCodes.ARTICLE_AI_DRAFT_GENERATE_FAILED, message);
    }
}
