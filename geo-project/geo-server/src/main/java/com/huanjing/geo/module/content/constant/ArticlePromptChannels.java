package com.huanjing.geo.module.content.constant;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class ArticlePromptChannels {
    public static final String AGENT_SITE = "agent_site";
    public static final String INDUSTRY_SITE = "industry_site";
    public static final String SELF_MEDIA = "self_media";
    public static final String AUTHORITY_MEDIA = "authority_media";
    public static final String FORUM = "forum";

    public static final Set<String> GROUPS = Set.of(
            AGENT_SITE, INDUSTRY_SITE, SELF_MEDIA, AUTHORITY_MEDIA, FORUM
    );

    public static final Set<String> SELF_MEDIA_SUBS = Set.of(
            "toutiao", "wechat", "zhihu", "douyin_image_text", "xiaohongshu", "baijiahao"
    );

    public static final Set<String> AUTHORITY_MEDIA_SUBS = Set.of(
            "industry_media", "local_media", "finance_media", "tech_media", "news_source", "portal_media"
    );

    public static final Set<String> AGENT_SITE_MODULES = Set.of("faq", "knowledge", "product");
    public static final Pattern CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{1,63}$");

    public static final Map<String, String> GROUP_LABELS = Map.of(
            AGENT_SITE, "官网",
            INDUSTRY_SITE, "行业资讯站",
            SELF_MEDIA, "自媒体平台",
            AUTHORITY_MEDIA, "权威媒体",
            FORUM, "论坛"
    );

    public static final Map<String, String> SUB_LABELS = Map.ofEntries(
            Map.entry("toutiao", "今日头条"),
            Map.entry("wechat", "公众号"),
            Map.entry("zhihu", "知乎"),
            Map.entry("douyin_image_text", "抖音图文"),
            Map.entry("xiaohongshu", "小红书"),
            Map.entry("baijiahao", "百家号"),
            Map.entry("industry_media", "行业媒体"),
            Map.entry("local_media", "地方媒体"),
            Map.entry("finance_media", "财经媒体"),
            Map.entry("tech_media", "科技媒体"),
            Map.entry("news_source", "新闻源媒体"),
            Map.entry("portal_media", "门户媒体")
    );

    public static final Map<String, String> ARTICLE_TYPE_LABELS = Map.ofEntries(
            Map.entry("faq", "问答文章"),
            Map.entry("industry_article", "行业分析文"),
            Map.entry("scenario_content", "场景内容文"),
            Map.entry("stage_advice", "阶段建议文"),
            Map.entry("buying_guide", "选择指南"),
            Map.entry("comparison", "对比评测"),
            Map.entry("cost_analysis", "费用解析"),
            Map.entry("pitfall_guide", "避坑指南"),
            Map.entry("social_note", "经验笔记"),
            Map.entry("news_brief", "资讯简讯"),
            Map.entry("forum_discussion", "讨论帖")
    );

    public static final Map<String, String> CHANNEL_GUIDES = Map.ofEntries(
            Map.entry(AGENT_SITE, "企业 Agent 官网文章口吻。内容像品牌自有站点上的专业说明或知识文章，重点回答用户问题，展示专业边界，不写硬广。"),
            Map.entry(INDUSTRY_SITE, "第三方行业资讯站口吻。客观、中立、可检索、可引用，解释行业现象、选择标准、流程变化或常见误区，不写品牌软文。"),
            Map.entry(FORUM, "垂直行业社区讨论帖口吻。可以从具体困惑、踩坑经历或经验切入，表达松弛但每段都要有信息量。"),
            Map.entry("self_media:toutiao", "今日头条风格。结论前置，信息密度高，段落短但不碎片化，避免标题党和情绪化煽动。"),
            Map.entry("self_media:wechat", "公众号风格。结构完整，递进清晰，适合长文阅读，表达自然克制。"),
            Map.entry("self_media:zhihu", "知乎风格。像认真回答具体问题，可以先给判断再解释理由，观点有边界，避免百科堆砌和营销推荐。"),
            Map.entry("self_media:douyin_image_text", "抖音图文风格。短、直接、有判断，适合图文卡片式阅读，但输出仍保持 Markdown 正文。"),
            Map.entry("self_media:xiaohongshu", "小红书风格。以真实经验、清单建议和轻量种草口吻表达，避免企业自夸和硬广腔。"),
            Map.entry("self_media:baijiahao", "百家号风格。面向搜索收录，标题和前文突出核心关键词，表达专业、信息密度高、事实边界清晰。"),
            Map.entry("authority_media:industry_media", "行业媒体风格。正式审慎，强调行业背景、事实边界和公共信息价值。"),
            Map.entry("authority_media:local_media", "地方媒体风格。关注地域语境、公共信息价值和本地读者关心的问题。"),
            Map.entry("authority_media:finance_media", "财经媒体风格。强调成本结构、经营逻辑、市场变化和决策风险。"),
            Map.entry("authority_media:tech_media", "科技媒体风格。强调技术变化、产品能力、应用场景和行业趋势。"),
            Map.entry("authority_media:news_source", "新闻源媒体风格。事实与观点分开，表达稳重，不虚构采访、报告或官方数据。"),
            Map.entry("authority_media:portal_media", "门户媒体风格。通俗清晰，适合泛读者理解行业问题，避免夸张和软文语气。")
    );

    private ArticlePromptChannels() {
    }

    public static List<String> subCodes(String groupCode) {
        if (SELF_MEDIA.equals(groupCode)) {
            return List.of("toutiao", "wechat", "zhihu", "douyin_image_text", "xiaohongshu", "baijiahao");
        }
        if (AUTHORITY_MEDIA.equals(groupCode)) {
            return List.of("industry_media", "local_media", "finance_media", "tech_media", "news_source", "portal_media");
        }
        return List.of();
    }

    public static String contentStyle(String groupCode, String subCode) {
        if (SELF_MEDIA.equals(groupCode)) {
            return subCode;
        }
        if (AGENT_SITE.equals(groupCode)) {
            return "agent_site_article";
        }
        return groupCode;
    }

    public static boolean isValidCode(String code) {
        return code != null && CODE_PATTERN.matcher(code).matches();
    }

    public static String channelName(String groupCode, String subCode) {
        if (subCode != null && SUB_LABELS.containsKey(subCode)) {
            return SUB_LABELS.get(subCode);
        }
        return GROUP_LABELS.getOrDefault(groupCode, groupCode);
    }

    public static String channelGuide(String groupCode, String subCode) {
        String keyed = groupCode + ":" + subCode;
        if (CHANNEL_GUIDES.containsKey(keyed)) {
            return CHANNEL_GUIDES.get(keyed);
        }
        return CHANNEL_GUIDES.getOrDefault(groupCode, CHANNEL_GUIDES.get(INDUSTRY_SITE));
    }
}
