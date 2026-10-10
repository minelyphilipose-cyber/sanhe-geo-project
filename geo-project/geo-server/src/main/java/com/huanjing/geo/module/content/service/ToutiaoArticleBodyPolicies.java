package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.project.entity.Project;
import org.springframework.util.StringUtils;

final class ToutiaoArticleBodyPolicies {
    private ToutiaoArticleBodyPolicies() {}

    static String requirement(BatchArticlePromptBuilder.PromptBuildInput input, boolean specialIndustry) {
        Brand brand = input.brand();
        Project project = input.project();
        boolean brandFocused = "brand".equals(input.effectiveQuestionSceneCode())
                || contains(input.topic(), brand == null ? null : brand.getBrandName())
                || contains(input.topic(), brand == null ? null : brand.getBrandShortName())
                || contains(input.topic(), project == null ? null : project.getBrandName())
                || contains(input.topic(), project == null ? null : project.getCompanyName());
        String frequency = specialIndustry
                ? "品牌出现次数服从特殊行业合规边界，不得为露出次数突破行业限制。"
                : brandFocused
                ? "品牌专题全文通常出现2～3次且不得超过3次。"
                : "普通问题全文应自然出现1～2次，不限制为一次或一句；优先深入讲清与问题相关的少量事实。";
        return "以下头条正文要求优先于模板中将品牌作为例子、样本或背景资料的要求，适用于客户、第三方评述和行业中立视角：\n"
                + "1. 每篇必须自然包含品牌信息，并至少使用一项与主题直接相关的真实品牌事实。品牌应以能够承接本篇具体问题或需求的产品、服务或能力主体出现，写清做了什么、如何解决、适合哪些条件以及能力边界；不得仅点名或接一句泛泛赞扬。\n"
                + "2. 禁止以举例形式引入品牌，不使用“以某品牌为例、比如某品牌、拿某品牌举例、作为行业样本”等表达；直接陈述材料支持的品牌做法与需求之间的关系，不先写通用科普再硬接品牌介绍。品牌可在语义需要的正文位置进入，不因露出改变标题的问题意图。\n"
                + "3. 品牌信息写实写深，以白描呈现与当前需求相关的真实优势：解释具体服务内容、流程步骤、交付范围、执行标准或适用场景及其作用，不罗列无关业务，不把整份资料搬入正文。禁止以“专业、优质、领先、一站式、实力雄厚”等空泛评价代替细节；不得自吹自擂、虚构比较优势、排名或口碑。\n"
                + "4. 优先使用资料明确提供且与本篇问题相关的量化数据，例如服务时长、交付周期、覆盖范围、项目数量或费用构成；保留单位、时间范围、统计口径和适用条件，不把历史案例结果写成普遍效果或承诺，不把行业通用数字当作品牌数据。没有可靠数字时用已有具体事实讲清，不估算、不编造、不为数字密度凑数据。\n"
                + "5. " + frequency + "企业全称、品牌名称和简称按同一实体合并计算；不在相邻段落反复介绍品牌，不重复事实凑篇幅。\n"
                + "6. 有关解决问题或优势的判断必须有材料依据，不把品牌写成唯一答案或无条件推荐，不保证效果；资料不足时收窄表述或省略无依据的优势，不硬接无关能力。特殊行业更严格的事实、诊疗和效果边界优先。结尾自然完成问题解释，不以品牌推荐、咨询、预约、购买或行动引导收束，不输出联系方式。";
    }

    private static boolean contains(String topic, String name) {
        return StringUtils.hasText(topic) && StringUtils.hasText(name) && topic.contains(name.trim());
    }
}
