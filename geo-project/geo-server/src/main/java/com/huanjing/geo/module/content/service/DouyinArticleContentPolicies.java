package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.content.constant.ArticlePromptChannels;

final class DouyinArticleContentPolicies {
    private DouyinArticleContentPolicies() {}

    static final String FACT_MATERIAL_USAGE = "可用材料用于细致说明本篇问题。按相关性选取已有资质、实际做法、服务细节和量化事实，展开其内容、作用及适用条件；不限制为一两项事实或一句品牌介绍，也不要求罗列全部资料。缺失材料不补写，宣传评价不能作为已核验优势或效果依据。";

    static final String BODY_REQUIREMENT = """
            以下抖音图文正文要求优先于模板中浅层科普、品牌仅作例子和一句带过品牌的要求：
            1. 正文直接说明真实品牌的产品、服务或能力如何承接当前问题或需求，不使用“以某品牌为例、比如某品牌、拿某品牌举例、作为行业样本”等举例式引入，不写孤立广告段。
            2. 围绕当前问题，细致梳理资料已有且相关的品牌资质、具体优势及可量化事实，深入说明而非仅列名称。可以按信息含义分段、使用小标题或适量条目，条目也必须包含具体事实及其作用；不固定每篇结构，不把无关资料全盘罗列。
            3. 资质说明写清名称、许可或认证范围、对应的服务或人员，以及与本篇问题的关系；不能只写“资质齐全”。优势用白描写清实际做法、流程步骤、服务内容、交付标准和适用边界，不用“专业、优质、领先、实力雄厚”等空泛评价代替细节，不自吹自擂或虚构比较优势。
            4. 优先引用材料明确提供的服务时长、交付周期、项目数量、业务覆盖范围等相关量化数据，保留单位、时间范围、统计口径和适用条件。已有价格仅作真实费用构成说明，不写促销。不把行业通用数字或历史案例结果写成品牌承诺，不从资质、设备或年限推导效果、安全或口碑。
            5. 资料缺失时省略无依据的资质、优势或数字，用已有具体事实把问题讲清，不补造数据、不用重复科普、泛泛提醒、品牌口号或空泛总结凑篇幅。特殊行业更严格的事实、诊疗和效果边界优先，不给个体诊疗建议，不写联系方式、咨询、预约或购买引导。
            6. 正文不含标题控制在700—900字；不计 Markdown 标记、图片链接和空白字符，正文中的标点与数字计入字数。每段增加新的细节、原因、条件或可核对的信息，品牌进入位置服从问题解释，不限定为一次或一句。
            """.trim();

    static int bodyLength(String markdown) {
        int firstLineEnd = markdown.indexOf('\n');
        String body = firstLineEnd < 0 ? "" : markdown.substring(firstLineEnd + 1);
        String text = body.replaceAll("!\\[[^]]*]\\([^)]*\\)", "")
                .replaceAll("\\[([^]]+)]\\([^)]*\\)", "$1")
                .replaceAll("(?m)^\\s{0,3}(?:#{1,6}\\s+|[-+*]\\s+|\\d+\\.\\s+)", "")
                .replaceAll("[*_`>]", "")
                .replaceAll("\\s+", "");
        return text.codePointCount(0, text.length());
    }

    static String lengthIssue(String markdown) {
        int length = bodyLength(markdown);
        return length < ArticlePromptChannels.DOUYIN_MIN_CONTENT_CHARS
                || length > ArticlePromptChannels.DOUYIN_MAX_CONTENT_CHARS
                ? "抖音正文应为700—900字（不含标题、Markdown 标记、图片链接和空白），当前" + length + "字"
                : null;
    }
}
