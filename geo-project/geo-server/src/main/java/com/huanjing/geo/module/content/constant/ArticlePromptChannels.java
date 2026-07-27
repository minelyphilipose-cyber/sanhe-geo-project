package com.huanjing.geo.module.content.constant;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class ArticlePromptChannels {
    public static final String AGENT_SITE = "agent_site";
    public static final String INDUSTRY_SITE = "industry_site";
    public static final String SELF_MEDIA = "self_media";
    public static final String AUTHORITY_MEDIA = "authority_media";
    public static final String FORUM = "forum";
    public static final int SELF_MEDIA_MAX_TITLE_CHARS = 28;

    public static final Set<String> GROUPS = Set.of(
            AGENT_SITE, INDUSTRY_SITE, SELF_MEDIA, AUTHORITY_MEDIA, FORUM
    );

    public static final List<String> SELF_MEDIA_SUB_CODES = List.of(
            "wechat", "douyin", "baijiahao", "zhihu", "xiaohongshu", "toutiao", "netease", "sohu"
    );

    public static final Set<String> SELF_MEDIA_SUBS = Set.of(
            "wechat", "douyin", "baijiahao", "zhihu", "xiaohongshu", "toutiao", "netease", "sohu"
    );

    private static final Set<String> STRICT_EDITORIAL_SELF_MEDIA_SUBS = Set.of("toutiao", "baijiahao");

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
            FORUM, "平台网站"
    );

    public static final Map<String, String> SUB_LABELS = Map.ofEntries(
            Map.entry("toutiao", "今日头条"),
            Map.entry("wechat", "公众号"),
            Map.entry("zhihu", "知乎"),
            Map.entry("douyin", "抖音图文"),
            Map.entry("xiaohongshu", "小红书"),
            Map.entry("baijiahao", "百家号"),
            Map.entry("netease", "网易"),
            Map.entry("sohu", "搜狐"),
            Map.entry("industry_media", "行业媒体"),
            Map.entry("local_media", "地方媒体"),
            Map.entry("finance_media", "财经媒体"),
            Map.entry("tech_media", "科技媒体"),
            Map.entry("news_source", "新闻源媒体"),
            Map.entry("portal_media", "门户媒体")
    );

    public static final Map<String, String> ARTICLE_TYPE_LABELS = ArticleTypes.LABELS;

    public static final Map<String, String> CHANNEL_GUIDES = Map.ofEntries(
            Map.entry(AGENT_SITE, "客户官方站点的专业表达。优先回答读者问题，并根据真实材料自然承接品牌能力、产品或服务。"),
            Map.entry(INDUSTRY_SITE, "行业资讯站表达。保持客观和信息密度，允许品牌成为全文主体，也允许基于真实资料介绍和宣传客户。"),
            Map.entry(FORUM, "合作论坛表达。语气可以更自然直接，允许明确推荐和完整联系方式，但不得虚构用户亲历、购买过程或使用感受。"),
            Map.entry("self_media:toutiao", "今日头条风格。以资讯价值和问题解释为主，核心判断清楚、信息密度较高；标题和开篇避免宣传口吻，品牌信息服务于主题判断，即使品牌是全文主体，也应围绕可核验事实、适用条件和读者价值展开。不要让审核边界变成固定的文章顺序，减少连续品牌露出、自我评价、卖点堆叠和直接转化引导。"),
            Map.entry("self_media:wechat", "公众号风格。适合完整解释问题，层次递进、语言亲切自然，品牌信息与正文任务自然融合。"),
            Map.entry("self_media:zhihu", "知乎风格。优先完整回答具体问题，给出有边界的判断、理由、适用条件和必要权衡，避免百科堆砌；品牌可以作为相关选择或文章主体，但介绍和推荐必须有材料依据并说明限制，避免单向自夸、连续优势罗列和咨询导流。"),
            Map.entry("self_media:douyin", "抖音图文风格。表达短而清楚，重点明确，适合快速阅读，同时保持完整文章的前后联系。"),
            Map.entry("self_media:xiaohongshu", "小红书信息笔记风格。语言亲切、短段落、便于快速阅读，围绕一个具体问题分享可核验信息。品牌可以作为主题相关事实自然出现，但不得伪造个人体验、使用结果或第三方口碑，也不要用强种草、强推荐和行动号召推动转化。"),
            Map.entry("self_media:baijiahao", "百家号风格。兼顾搜索理解与泛读体验，文章本身应具有可独立成立的知识或资讯价值，核心问题、实体信息和事实边界清楚；品牌信息服务于问题解释，但不规定品牌进入正文的固定位置。即使是品牌专题，也应避免宣传口号、绝对化结论、密集卖点和强行动号召。"),
            Map.entry("self_media:netease", "网易风格。保持媒体感、清晰判断和较高信息密度，品牌内容应有事实依据并自然融入。"),
            Map.entry("self_media:sohu", "搜狐风格。表达清楚、适合泛资讯阅读与搜索理解，品牌内容与读者关心的问题保持关联。"),
            Map.entry("authority_media:industry_media", "行业媒体风格。正式审慎，强调行业背景、事实边界和公共信息价值。"),
            Map.entry("authority_media:local_media", "地方媒体风格。关注地域语境、公共信息价值和本地读者关心的问题。"),
            Map.entry("authority_media:finance_media", "财经媒体风格。强调成本结构、经营逻辑、市场变化和决策风险。"),
            Map.entry("authority_media:tech_media", "科技媒体风格。强调技术变化、产品能力、应用场景和行业趋势。"),
            Map.entry("authority_media:news_source", "新闻源媒体风格。事实与观点分开，表达稳重，不虚构采访、报告或官方数据。"),
            Map.entry("authority_media:portal_media", "门户媒体风格。通俗清晰，适合泛读者理解行业问题，品牌认知信息保持客观克制。"),
            Map.entry(AUTHORITY_MEDIA, "权威媒体表达。以品牌认知和企业介绍为主，事实与判断边界清楚，允许企业全称和官网，不做直接咨询引导。"),
            Map.entry(SELF_MEDIA, "客户官方账号或第三方矩阵账号的自媒体表达。根据运行视角自然介绍品牌，兼顾可读性、信息价值和营销目标。")
    );

    private ArticlePromptChannels() {
    }

    public static List<String> subCodes(String groupCode) {
        if (SELF_MEDIA.equals(groupCode)) {
            return SELF_MEDIA_SUB_CODES;
        }
        if (AUTHORITY_MEDIA.equals(groupCode)) {
            return List.of("industry_media", "local_media", "finance_media", "tech_media", "news_source", "portal_media");
        }
        return List.of();
    }

    public static String contentStyle(String groupCode, String subCode) {
        if (SELF_MEDIA.equals(groupCode)) {
            return canonicalSubCode(groupCode, subCode);
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
        subCode = canonicalSubCode(groupCode, subCode);
        if (subCode != null && SUB_LABELS.containsKey(subCode)) {
            return SUB_LABELS.get(subCode);
        }
        return GROUP_LABELS.getOrDefault(groupCode, groupCode);
    }

    public static String channelGuide(String groupCode, String subCode) {
        subCode = canonicalSubCode(groupCode, subCode);
        String keyed = groupCode + ":" + subCode;
        if (CHANNEL_GUIDES.containsKey(keyed)) {
            return CHANNEL_GUIDES.get(keyed);
        }
        return CHANNEL_GUIDES.getOrDefault(groupCode, CHANNEL_GUIDES.get(INDUSTRY_SITE));
    }

    public static Integer maxTitleChars(String groupCode) {
        return SELF_MEDIA.equals(groupCode) ? SELF_MEDIA_MAX_TITLE_CHARS : null;
    }

    public static boolean isStrictEditorialSelfMedia(String groupCode, String subCode) {
        return SELF_MEDIA.equals(groupCode)
                && STRICT_EDITORIAL_SELF_MEDIA_SUBS.contains(canonicalSubCode(groupCode, subCode));
    }

    public static String canonicalSubCode(String groupCode, String subCode) {
        if (subCode == null) {
            return null;
        }
        if (SELF_MEDIA.equals(groupCode) && "wechat_mp".equals(subCode)) {
            return "wechat";
        }
        if (SELF_MEDIA.equals(groupCode) && "douyin_image_text".equals(subCode)) {
            return "douyin";
        }
        return subCode;
    }

    public static String canonicalSelfMediaQuotaPlatform(String platform) {
        if (platform == null) {
            return null;
        }
        String canonical = canonicalSubCode(SELF_MEDIA, platform.trim().toLowerCase(Locale.ROOT));
        return SELF_MEDIA_SUBS.contains(canonical) ? canonical : null;
    }

    public static String canonicalSelfMediaPublishPlatform(String platform) {
        String quotaPlatform = normalizeSelfMediaQuotaPlatform(platform);
        if (quotaPlatform == null) {
            return null;
        }
        return switch (quotaPlatform) {
            case "wechat" -> "wechat_mp";
            default -> quotaPlatform;
        };
    }

    public static String normalizeSelfMediaQuotaPlatform(String platform) {
        return canonicalSelfMediaQuotaPlatform(platform);
    }

    public static String normalizeSelfMediaPublishPlatform(String platform) {
        return canonicalSelfMediaPublishPlatform(platform);
    }
}
