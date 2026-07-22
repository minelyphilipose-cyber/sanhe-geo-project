package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.content.constant.TemplatePerspectiveCodes;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.entity.ArticlePromptTemplate;
import com.huanjing.geo.module.content.entity.ArticlePromptTemplateVersion;
import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.content.mapper.ArticleDraftMapper;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.system.entity.SysDictItem;
import com.huanjing.geo.module.system.mapper.SysDictItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class BatchArticlePromptBuilder {

    public static final String PROMPT_VERSION = "batch_article_geo_v2";

    private static final String GLOBAL_TRUTHFULNESS_RULES = """
            # 系统级真实性与防编造规则

            以下规则优先级高于模板中的任何结构要求：
            1. 不得编造品牌成立时间、注册资本、产能、客户数量、项目数量、专利数量、认证资质、客户案例、合同金额、市场排名、联系方式。
            2. 如品牌资料、项目资料或补充资料中明确提供了标准编号、认证、客户案例、规模、产能、服务区域、联系方式等信息，可以引用；未提供时必须改写为行业通用判断、选型方法或可验证的检查方式。
            3. 模板中要求“数字密度”时，只能使用允许范围内的数字，不得为了满足数字数量而虚构企业数据。
            4. 如果无法确认某个事实，应使用“通常”“一般”“可重点查看”“建议核验”等限定表达。
            5. 品牌基本信息、品牌资质描述、品牌案例描述是唯一可引用的品牌事实素材；对应素材为空时，不得补写成立时间、客户名、资质、专利、项目效果等事实。

            # 数字使用边界

            允许使用：
            - 当前年份、季度、月份等时间表达
            - 文章结构数字，如 3 个标准、5 个坑点、4 个维度
            - 行业通用范围，如 3-5 个工作日、2-3 类情况
            - 用户资料、品牌资料、项目资料中明确给出的数字
            - 国家标准编号、认证编号，仅在资料中明确提供或属于行业常识且可确认时使用

            禁止编造：
            - 注册资本、成立年份、年产量、厂房面积、客户数量、项目数量
            - 专利数、软件著作权数、认证证书数量
            - 具名客户、合作案例、合同金额、增长率、市场份额
            - 品牌排名、获奖信息、联系方式

            # 联系方式呈现规则

            文章结尾的联系方式由系统变量 {{contactBlock}} 提供，已由后端根据品牌配置拼装完成。
            必须原样放置，不得改写、删减、补充，更不得自行编造任何官网、电话或地址。
            如果 {{contactBlock}} 为空，结尾不出现任何联系方式。

            # 标题差异化规则

            模板中的标题示例只作为方向参考，不得逐字套用或只替换变量。
            生成标题时必须结合 {{topic}}、{{contentAngle}}、{{recentTitles}} 做差异化表达。
            同一批次内避免连续使用相同句式、相同开头、相同标点结构。
            历史标题不为空时，新标题不得复用历史标题的核心句式。
            """;

    private static final String SYSTEM_PROMPT = """
            你是一名中文 GEO（生成式引擎优化）内容写作助手，负责生成可被搜索引擎、AI Overview、Perplexity、豆包搜索等系统理解和引用的中文行业文章草稿。

            你的写作立场是行业观察者，不是品牌方、销售人员或市场宣传人员。

            必须遵守：
            - 内容服务对象是搜索这个问题的用户，不是品牌
            - 文章应优先回答用户问题，而不是介绍品牌
            - 品牌信息只作为背景资料和可选例子，不作为推荐对象、不作为结论指向、不作为案例主角
            - 不编造价格、门店数、客户案例、资质、排名、效果承诺、联系方式
            - 不写广告软文，不写营销口播，不堆砌关键词
            - 只输出完整 Markdown 正文，不输出提示词解释
            """;

    private static final Map<String, String> STYLE_LABELS = Map.ofEntries(
            Map.entry("wechat", "公众号"),
            Map.entry("toutiao", "今日头条"),
            Map.entry("douyin", "抖音图文"),
            Map.entry("zhihu", "知乎"),
            Map.entry("xiaohongshu", "小红书"),
            Map.entry("baijiahao", "百家号"),
            Map.entry("netease", "网易"),
            Map.entry("linkedin", "领英"),
            Map.entry("agent_site_article", "Agent 官网文章"),
            Map.entry("industry_site", "行业资讯站"),
            Map.entry("authority_media", "权威媒体"),
            Map.entry("forum", "平台网站")
    );
    private static final Map<String, String> STYLE_GUIDES = Map.ofEntries(
            Map.entry("wechat", "适合完整解释一个行业问题。结构可以更稳，有清晰的小标题和递进关系，但每个小标题下必须有具体信息，不做空泛铺陈。语气自然、克制，像一篇给潜在用户认真看的长文。不要写成品牌宣传稿，不要在结尾强行导向某个品牌。"),
            Map.entry("toutiao", "面向泛资讯阅读用户。开头直接给出结论、判断或一个具体的现象，不铺垫背景。标题可以有明确判断，但不能标题党。正文要保持信息密度，段落较短但不能碎片化。适合解释一个现实问题、拆解一个选择标准或指出一个常见误区。避免营销腔、情绪化煽动和过度口语化。"),
            Map.entry("douyin", "适合图文卡片式阅读，但输出仍是 Markdown 文章正文。开头要短、直接、有判断。句子可以更短，段落更轻，但每段都要有明确的信息点。适合用清晰的小节切分复杂问题，每个小节聚焦一个明确的判断或提醒，小节内仍保持完整的段落化表达，不要碎片化成短句列表。不使用任何 emoji，不写口播稿，不使用短视频营销话术。"),
            Map.entry("zhihu", "像一个了解行业的人在回答具体问题。可以先给判断，再解释理由；也可以先讲一个反常识点，再回到主题。观点要有边界，允许让步和转折。不要用“作为从业者”“根据多年经验”这类自证式开头。不要写成百科词条式的定义罗列，也不要写成销售推荐。避免刻意金句、对仗式短句堆砌、爹味说教语气。"),
            Map.entry("xiaohongshu", "这是 GEO（生成式引擎优化）场景下的小红书风格，不是平台投放文案。使用经验分享语气，但保持信息密度。不使用任何 emoji。不使用密集换行，段落保持完整。不使用“姐妹们”“家人们”这类称呼。重点是让内容像真实用户的经验帖，而不是营销号种草文。"),
            Map.entry("baijiahao", "面向百度搜索收录的行业资讯长文。标题和前 200 字需要自然出现核心关键词，表达专业、信息密度高、事实边界清晰。不要虚构报告、客户、认证、专利或企业数据。"),
            Map.entry("netease", "面向门户资讯阅读和搜索收录的媒体型长文。标题和前 200 字需要自然出现核心关键词，表达专业克制、信息密度高、事实边界清晰。不要虚构报告、客户、认证、专利或企业数据。"),
            Map.entry("linkedin", "偏商业观察和行业分析。强调趋势、结构性问题、经营逻辑、决策框架和方法论。语言专业克制，不夸张，不煽动。可以使用书面化表达，强调判断与论证，避免咨询报告式的商业黑话堆砌。适合面向管理者、从业者、投资人或 B 端读者。"),
            Map.entry("agent_site_article", "企业 Agent 官网文章口吻。内容应像品牌自有站点上的专业说明或行业知识文章，但不能写成硬广。可以结合品牌服务范围、地域和业务背景提供清晰解释，重点回答用户问题、展示专业判断和服务边界。语气稳健、可信、可检索，避免夸张承诺、促销话术和强行导流。"),
            Map.entry("industry_site", "第三方行业资讯或行业科普口吻。内容应客观、中立、可引用，重点解释行业现象、选择标准、流程变化或市场误区。表达上要有“信息来源感”，可以使用“行业内普遍的做法是”“公开资料显示”“常见的合同条款里”这类表达方式，但不虚构具体机构名、报告名或数据来源。不要有明显品牌立场，不要写成软文。标题和正文都要像资讯站可发布的行业稿，而不是企业官网文章。"),
            Map.entry("authority_media", "正式、审慎、信息来源感更强。强调事实边界、行业背景、规范表达和公共信息价值。表达稳重、审慎，对事实和观点做明确区分：事实用陈述句呈现，观点用限定语呈现。可以引用行业惯例和公开信息，但不虚构采访、报告来源或官方数据。语气客观，避免主观推荐和夸张判断。"),
            Map.entry("forum", "像在垂直行业社区或专业论坛里发的讨论帖，不是社交平台或贴吧氛围。可以从一个具体困惑、踩坑场景或对比经历切入，允许第一人称表达和经验感叙述。语气比知乎更松弛一些，但每段都要有具体信息或具体判断。避免吵架式、带节奏、营销号式语气，避免情绪化吐槽或灌水式短句堆砌。")
    );
    private static final Map<String, String> LENGTH_LABELS = Map.of(
            "short", "短文，约 600 字",
            "medium", "中等篇幅，约 1500 字",
            "long", "长文，约 3000 字"
    );
    private static final List<String> CONTENT_ANGLES = List.of(
            "选择指南", "避坑指南", "费用解析", "流程科普", "对比分析",
            "案例解读", "决策建议", "误区澄清", "清单建议", "地域特色"
    );
    private static final List<String> AUDIENCE_PERSPECTIVES = List.of(
            "给完全没接触过的人看",
            "给已经做过功课、还在犹豫的人看",
            "给已经入坑、想避免下一个坑的人看",
            "给对比过多个选项、想要判断标准的人看",
            "给本地用户看，强调地域语境",
            "反常识切入，挑战一个常见观念",
            "从一个具体场景或痛点切入",
            "从一个时间节点切入，例如决策前、签约前、开业前、首年"
    );
    private static final List<String> SELF_MEDIA_TITLE_STRATEGIES = List.of(
            "直接回答型：承接用户问题词，标题给出一个克制判断或结论，不套用固定疑问句",
            "条件判断型：把强推荐意图改写为“在什么条件下更适合/需要谨慎”，突出适配边界",
            "场景代入型：从人群、预算、阶段、地域或使用场景切入，让标题和正文角度同步变化",
            "误区澄清型：围绕一个常见误解或错误选择习惯展开，但不制造焦虑",
            "核验线索型：突出公开信息、资质、流程、合同、材料或服务边界等可验证线索",
            "对比维度型：比较路线、类型或方案的差异，不写品牌排名或优劣榜",
            "流程阶段型：从决策前、了解中、确认前、使用后等阶段切入",
            "成本变量型：解释投入、时间、维护、沟通成本等变量，不写报价或优惠",
            "风险边界型：说明不适合、需要谨慎、需要进一步核验的情况",
            "问题整合型：用一个自然问题覆盖用户搜索意图，但避免复用“怎么选/哪家好”的固定句式"
    );
    private static final List<String> SELF_MEDIA_STRUCTURE_STRATEGIES = List.of(
            "判断清单型：围绕3-5个判断维度展开，不做品牌排名",
            "误区澄清型：每节先说明误区，再给正确核验方式",
            "流程解释型：按决策前、了解中、确认前的顺序解释",
            "公开信息核验型：围绕主体信息、服务范围、资质/材料、案例边界等公开信息说明",
            "FAQ整合型：用多个可独立摘取的问答段落覆盖搜索问题",
            "对比维度型：比较类型、路线或方案，不点名贬低竞品",
            "场景建议型：按不同人群/需求/预算说明适配与不适配"
    );
    private static final String SELF_MEDIA_PLATFORM_POLICY = """
            # 自媒体平台监管与 GEO 平衡规则

            以下规则只用于自媒体平台文章，优先级高于模板里的推荐、评测、种草、避坑等表达：
            1. 标题必须回应用户问题或问题词，但遇到“哪家好、推荐、排名、最好、靠谱机构”等强推荐意图时，只做意图降级：改为条件判断、场景适配、公开信息核验、误区澄清、路线对比或风险边界，不能写成榜单、硬推荐或导购标题，也不要把所有标题都写成“怎么选/怎么判断/注意什么”。
            2. 正文以解释型、科普型、判断型内容为主，不写营销文、招商文、转化页、口播稿、促销文案或品牌软广。
            3. 品牌只作为可选例子或公开信息说明出现，不作为唯一结论、首要推荐、背书对象或案例主角；除品牌专门场景外，品牌名全文不超过 1 次。
            4. 推荐型模板可以写“适合哪类需求 / 不适合哪类需求”，但不能写“首选、最推荐、闭眼选、强烈安利、必看、宝藏、天花板、性价比之王”等平台高风险表达。
            5. 不输出联系方式、私信、加微信、预约、咨询、报价、优惠、套餐、名额、限时、到店、领取资料等转化表达；{{contactBlock}} 为空时不出现任何导流信息。
            6. 为适配大模型抓取，每篇至少包含 2-3 个可独立摘取的段落：段落内同时具备问题、判断和依据；但不要每篇固定同一套小标题。
            7. 模板原有的标题示例、标题括号说明和“XX类问句”只作为语义方向参考，不得作为固定标题句式套用；若与本规则冲突，以本规则为准。
            8. 标题策略：{{titleStrategy}}。该策略只限定标题意图，不限定标题句式；必须结合 {{recentTitles}} 避免复用历史标题结构。
            9. 结构策略：{{structureStrategy}}。这是本篇优先采用的结构方向，不是固定模板；可以按平台调性自然调整。
            """;
    private static final List<String> MARKETING_WORDS = List.of(
            "领先", "专业", "优质", "卓越", "知名", "一流", "高端", "值得信赖",
            "深受认可", "行业标杆", "首选", "强大", "全面", "高效"
    );

    private final ArticleDraftMapper articleDraftMapper;
    private final SysDictItemMapper sysDictItemMapper;
    private final ObjectMapper objectMapper;
    private final ArticlePromptVariableRegistry variableRegistry;

    public PromptBuildResult build(PromptBuildInput input) {
        return build(input, "");
    }

    public PromptBuildResult build(PromptBuildInput input, String contactBlock) {
        String contentAngle = resolveContentAngle(input);
        String audiencePerspective = resolveAudiencePerspective(input.articleIndexInBatch());
        String titleStrategy = resolveSelfMediaTitleStrategy(input);
        String structureStrategy = resolveSelfMediaStructureStrategy(input);
        String businessFocus = resolveBusinessFocus(input.brandStatement(), input.brand());
        List<String> recentTitles = resolveHistoryTitles(input.sourceBrandId(), input.project().getId(), 10);
        String userPrompt = buildUserPrompt(input, contentAngle, audiencePerspective, businessFocus, recentTitles);
        userPrompt = withTitleGuideInstruction(userPrompt, input.titleGuide());

        String resolvedContactBlock = StringUtils.hasText(contactBlock) ? contactBlock.trim() : "";
        String systemPrompt = resolveGlobalRuleVariables(
                withGlobalRules(SYSTEM_PROMPT, input.forbiddenPhrases(), input),
                input,
                contentAngle,
                titleStrategy,
                structureStrategy,
                recentTitles,
                resolvedContactBlock
        );

        Map<String, Object> promptSnapshot = new LinkedHashMap<>();
        promptSnapshot.put("promptVersion", PROMPT_VERSION);
        promptSnapshot.put("systemPrompt", systemPrompt);
        promptSnapshot.put("userPrompt", userPrompt);
        promptSnapshot.put("contentAngle", contentAngle);
        promptSnapshot.put("audiencePerspective", audiencePerspective);
        promptSnapshot.put("titleStrategy", titleStrategy);
        promptSnapshot.put("structureStrategy", structureStrategy);
        promptSnapshot.put("recentTitles", recentTitles);
        promptSnapshot.put("contactBlock", resolvedContactBlock);
        promptSnapshot.put("titleGuide", input.titleGuide());
        promptSnapshot.put("perspectiveCode", input.perspectiveCode());
        promptSnapshot.put("perspectiveMatchedScope", input.perspectiveMatchedScope());
        promptSnapshot.put("perspectiveMatchedConfigId", input.perspectiveMatchedConfigId());
        promptSnapshot.put("selectedOfferings", input.selectedOfferings());

        Map<String, Object> inputSnapshot = new LinkedHashMap<>();
        inputSnapshot.put("promptVersion", PROMPT_VERSION);
        inputSnapshot.put("projectId", input.project().getId());
        inputSnapshot.put("brandId", input.project().getBrandId());
        inputSnapshot.put("sourceProjectId", input.sourceProjectId());
        inputSnapshot.put("sourceBrandId", input.sourceBrandId());
        inputSnapshot.put("subjectProjectId", input.subjectProjectId());
        inputSnapshot.put("subjectBrandId", input.subjectBrandId());
        inputSnapshot.put("topic", input.topic());
        inputSnapshot.put("topicAsQuestion", input.topicAsQuestion());
        inputSnapshot.put("topicSource", input.topicSource());
        inputSnapshot.put("keywordGroupId", input.keywordGroupId());
        inputSnapshot.put("keywordGroupName", input.keywordGroupName());
        inputSnapshot.put("relatedKeywords", input.relatedKeywords());
        inputSnapshot.put("articleType", input.articleType());
        inputSnapshot.put("contentStyle", input.contentStyle());
        inputSnapshot.put("length", input.length());
        inputSnapshot.put("extraPrompt", input.extraPrompt());
        inputSnapshot.put("businessFocus", businessFocus);
        inputSnapshot.put("titleStrategy", titleStrategy);
        inputSnapshot.put("structureStrategy", structureStrategy);
        inputSnapshot.put("contactBlock", resolvedContactBlock);
        inputSnapshot.put("titleGuide", input.titleGuide());
        inputSnapshot.put("perspectiveCode", input.perspectiveCode());
        inputSnapshot.put("perspectiveMatchedScope", input.perspectiveMatchedScope());
        inputSnapshot.put("perspectiveMatchedConfigId", input.perspectiveMatchedConfigId());
        inputSnapshot.put("selectedOfferings", input.selectedOfferings());

        return new PromptBuildResult(
                systemPrompt,
                systemPrompt + "\n\n" + userPrompt,
                contentAngle,
                audiencePerspective,
                json(promptSnapshot),
                json(inputSnapshot)
        );
    }

    public PromptBuildResult buildFromTemplate(PromptBuildInput input,
                                               ArticlePromptTemplate template,
                                               ArticlePromptTemplateVersion version) {
        String contentAngle = resolveContentAngle(input);
        String audiencePerspective = resolveAudiencePerspective(input.articleIndexInBatch());
        String titleStrategy = resolveSelfMediaTitleStrategy(input);
        String structureStrategy = resolveSelfMediaStructureStrategy(input);
        String businessFocus = resolveBusinessFocus(input.brandStatement(), input.brand());
        List<String> recentTitles = resolveHistoryTitles(input.sourceBrandId(), input.project().getId(), 10);
        String contactBlock = buildContactBlock(template, input.brand());
        Map<String, String> brandFacts = buildBrandFacts(input);
        String templateSystemPrompt = StringUtils.hasText(version.getSystemPrompt()) ? version.getSystemPrompt() : SYSTEM_PROMPT;
        String systemPrompt = resolveGlobalRuleVariables(
                withGlobalRules(templateSystemPrompt, input.forbiddenPhrases(), input),
                input,
                contentAngle,
                titleStrategy,
                structureStrategy,
                recentTitles,
                contactBlock
        );
        String userPrompt = renderTemplate(version.getUserPromptTemplate(), input, template, contentAngle,
                audiencePerspective, titleStrategy, structureStrategy, businessFocus, recentTitles, contactBlock, brandFacts);
        userPrompt = withTitleGuideInstruction(userPrompt, input.titleGuide());
        userPrompt = withSelectedOfferings(userPrompt, input.selectedOfferings());

        Map<String, Object> promptSnapshot = new LinkedHashMap<>();
        promptSnapshot.put("promptVersion", "template_v" + version.getVersionNo());
        promptSnapshot.put("templateId", template.getId());
        promptSnapshot.put("templateVersionId", version.getId());
        promptSnapshot.put("templateName", template.getName());
        promptSnapshot.put("systemPrompt", systemPrompt);
        promptSnapshot.put("userPrompt", userPrompt);
        promptSnapshot.put("contentAngle", contentAngle);
        promptSnapshot.put("audiencePerspective", audiencePerspective);
        promptSnapshot.put("titleStrategy", titleStrategy);
        promptSnapshot.put("structureStrategy", structureStrategy);
        promptSnapshot.put("recentTitles", recentTitles);
        promptSnapshot.put("contactDisclosureMode", template.getContactDisclosureMode());
        promptSnapshot.put("contactBlock", contactBlock);
        promptSnapshot.put("brandFacts", brandFacts);
        promptSnapshot.put("titleGuide", input.titleGuide());
        promptSnapshot.put("perspectiveCode", input.perspectiveCode());
        promptSnapshot.put("perspectiveMatchedScope", input.perspectiveMatchedScope());
        promptSnapshot.put("perspectiveMatchedConfigId", input.perspectiveMatchedConfigId());
        promptSnapshot.put("selectedOfferings", input.selectedOfferings());

        Map<String, Object> inputSnapshot = new LinkedHashMap<>();
        inputSnapshot.put("promptVersion", "template_v" + version.getVersionNo());
        inputSnapshot.put("templateId", template.getId());
        inputSnapshot.put("templateVersionId", version.getId());
        inputSnapshot.put("projectId", input.project().getId());
        inputSnapshot.put("brandId", input.project().getBrandId());
        inputSnapshot.put("sourceProjectId", input.sourceProjectId());
        inputSnapshot.put("sourceBrandId", input.sourceBrandId());
        inputSnapshot.put("subjectProjectId", input.subjectProjectId());
        inputSnapshot.put("subjectBrandId", input.subjectBrandId());
        inputSnapshot.put("topic", input.topic());
        inputSnapshot.put("topicAsQuestion", input.topicAsQuestion());
        inputSnapshot.put("topicSource", input.topicSource());
        inputSnapshot.put("keywordGroupId", input.keywordGroupId());
        inputSnapshot.put("keywordGroupName", input.keywordGroupName());
        inputSnapshot.put("relatedKeywords", input.relatedKeywords());
        inputSnapshot.put("articleType", input.articleType());
        inputSnapshot.put("contentStyle", input.contentStyle());
        inputSnapshot.put("channelGroupCode", template.getChannelGroupCode());
        inputSnapshot.put("channelSubCode", template.getChannelSubCode());
        inputSnapshot.put("agentSiteModule", template.getAgentSiteModule());
        inputSnapshot.put("articleTypeCode", template.getArticleTypeCode());
        inputSnapshot.put("perspectiveCode", input.perspectiveCode());
        inputSnapshot.put("perspectiveMatchedScope", input.perspectiveMatchedScope());
        inputSnapshot.put("perspectiveMatchedConfigId", input.perspectiveMatchedConfigId());
        inputSnapshot.put("length", input.length());
        inputSnapshot.put("extraPrompt", input.extraPrompt());
        inputSnapshot.put("businessFocus", businessFocus);
        inputSnapshot.put("titleStrategy", titleStrategy);
        inputSnapshot.put("structureStrategy", structureStrategy);
        inputSnapshot.put("contactDisclosureMode", template.getContactDisclosureMode());
        inputSnapshot.put("contactBlock", contactBlock);
        inputSnapshot.put("brandFacts", brandFacts);
        inputSnapshot.put("titleGuide", input.titleGuide());
        inputSnapshot.put("selectedOfferings", input.selectedOfferings());

        return new PromptBuildResult(systemPrompt, systemPrompt + "\n\n" + userPrompt, contentAngle, audiencePerspective,
                json(promptSnapshot), json(inputSnapshot));
    }

    public String topicAsQuestion(String topic, String articleType, int articleIndexInBatch) {
        return topicAsQuestion(topic, articleType, articleIndexInBatch, null);
    }

    public String topicAsQuestion(String topic, String articleType, int articleIndexInBatch, String contentStyle) {
        String normalized = trimToNull(topic);
        if (normalized == null) {
            return "";
        }
        if (normalized.endsWith("？") || normalized.endsWith("?")
                || normalized.contains("怎么") || normalized.contains("如何")
                || normalized.contains("为什么") || normalized.contains("哪些")) {
            return normalized;
        }
        String question = switch (normalize(articleType)) {
            case "faq" -> normalized + "常见问题有哪些？";
            case "scenario_content" -> "什么场景下需要重点关注" + normalized + "？";
            case "stage_advice" -> "围绕" + normalized + "，不同阶段应该注意什么？";
            default -> "如何理解" + normalized + "的选择逻辑和常见误区？";
        };
        if (isSelfMediaContentStyle(contentStyle)) {
            return question;
        }
        return question + " 本篇从“" + resolveContentAngle(articleIndexInBatch) + "”角度回答。";
    }

    private String buildUserPrompt(PromptBuildInput input,
                                   String contentAngle,
                                   String audiencePerspective,
                                   String businessFocus,
                                   List<String> recentTitles) {
        Project project = input.project();
        Brand brand = input.brand();
        StringBuilder sb = new StringBuilder();
        sb.append("# 写作立场\n\n");
        sb.append("你是行业观察者，不是品牌方。\n");
        sb.append("本文服务对象是搜索这个问题的用户，不是品牌。\n\n");
        sb.append("品牌信息只是背景资料和可选例子：\n");
        sb.append("- 用于理解行业语境\n");
        sb.append("- 可以在自然位置作为例子出现\n");
        sb.append("- 不能作为推荐对象、结论指向或案例主角\n\n");
        sb.append("如果去掉品牌名后一段话仍然成立，才是合格的品牌提及。\n\n");

        sb.append("# 用户搜索意图\n\n");
        appendLine(sb, "本篇围绕的关键词是", input.topic());
        appendLine(sb, "本文真正要回答的用户搜索问题是", input.topicAsQuestion());
        sb.append("\n请写成对这个用户搜索问题的回答，不要写成关键词百科。\n\n");

        sb.append("# 背景资料\n\n");
        sb.append("以下资料仅用于理解行业语境，不要求在文中体现。\n\n");
        appendLine(sb, "行业语境", resolveIndustry(project, brand));
        appendLine(sb, "读者画像参考", project.getTargetAudience());
        appendLine(sb, "项目内容方向", project.getContentTone());
        appendBrandBackground(sb, brand, businessFocus);
        appendSelectedOfferings(sb, input.selectedOfferings());

        sb.append("\n# 写作配置\n\n");
        appendLine(sb, "文章类型", label(ArticlePromptChannels.ARTICLE_TYPE_LABELS, input.articleType(), input.articleType()));
        appendLine(sb, "内容角度", contentAngle);
        appendLine(sb, "读者视角", audiencePerspective);
        appendLine(sb, "标题策略", resolveSelfMediaTitleStrategy(input));
        appendLine(sb, "结构策略", resolveSelfMediaStructureStrategy(input));
        appendLine(sb, "平台风格", label(STYLE_LABELS, input.contentStyle(), input.contentStyle()));
        appendLine(sb, "篇幅参考", label(LENGTH_LABELS, input.length(), "中等篇幅，约 1500 字"));
        appendLine(sb, "补充要求", input.extraPrompt());

        sb.append("\n# 内容组织要求\n\n");
        sb.append("- 标题要像真实用户会搜索的问题，或一个有判断的陈述句\n");
        sb.append("- 开头直接进入问题，不要写新闻评论体、公文体、SEO 软文体开场\n");
        sb.append("- 文章必须有具体、可验证、可讨论的信息点\n");
        sb.append("- 避免模板化递进结构\n");
        sb.append("- 允许自然过渡和有立场的判断\n");
        sb.append("- 不追求结构完整，优先保证每段有信息量\n\n");

        sb.append("# 平台风格规则\n\n");
        sb.append(label(STYLE_GUIDES, input.contentStyle(), STYLE_GUIDES.get("wechat"))).append("\n\n");

        sb.append("# 写作裁决规则\n\n");
        sb.append("当字数要求、二级标题数量、平台风格、自然表达之间存在冲突时：\n");
        sb.append("1. 平台风格和真实阅读感优先\n");
        sb.append("2. 信息密度优先\n");
        sb.append("3. 字数和小标题数量只是参考\n\n");
        sb.append("不要为了凑字数添加冗余段落。\n");
        sb.append("不要为了凑小标题拆分本应连续的内容。\n");
        sb.append("不要为了套结构牺牲自然表达。\n\n");

        sb.append("# GEO（生成式引擎优化）可引用性要求\n\n");
        sb.append("文章中至少要有 2-3 个段落满足这个标准：\n\n");
        sb.append("把这一段单独抄走、不带任何上下文，它仍然能传达一个完整的判断、事实或方法。\n\n");
        sb.append("这是一个判断标准，不是写作模板。\n");
        sb.append("这些段落可以以任何自然形态出现。重点是“信息能独立站住”，不是“形态要齐全”。\n\n");
        sb.append("品牌信息只用于必要的行业例子，不能覆盖用户问题本身。\n");
        sb.append("不得编造价格、门店数、加盟政策、客户案例、市场排名、资质认证、效果承诺或联系方式。\n");
        sb.append("避免新闻评论体、公文体、SEO 软文体的高频套话与商业黑话。\n\n");

        sb.append("# 差异化要求\n\n");
        sb.append("生成时注意标题、开头和行文角度要与历史文章明显区分。\n\n");
        if (!recentTitles.isEmpty()) {
            sb.append("最近已有文章标题：\n");
            for (String title : recentTitles) {
                sb.append("- ").append(title).append("\n");
            }
            sb.append("\n本文的标题不要采用上述任何一个标题的句式结构。\n");
            sb.append("如果上述标题大多是“...怎么选”“...要注意什么”这类疑问句，本文标题应使用陈述判断句或场景描述句。\n\n");
        }

        sb.append("# 最后提醒\n\n");
        sb.append("- 这篇文章的服务对象是搜索“").append(promptSafeInline(input.topicAsQuestion())).append("”的用户，不是品牌\n");
        sb.append("- 写完后通读一遍，如果某一段去掉品牌名后逻辑不成立，请重写这一段\n\n");

        sb.append("# 输出要求\n\n");
        sb.append("- 只输出 Markdown 正文\n");
        sb.append("- 第一行是一级标题，格式为 \"# 标题\"\n");
        sb.append("- 二级标题数量根据内容自然确定\n");
        sb.append("- 二级标题要有信息量，不要使用空泛标题\n");
        sb.append("- 不输出 JSON\n");
        sb.append("- 不输出提示词解释\n");
        sb.append("- 不输出引导语\n");
        return sb.toString();
    }

    private void appendBrandBackground(StringBuilder sb, Brand brand, String businessFocus) {
        if (brand == null || !StringUtils.hasText(brand.getBrandName())) {
            return;
        }
        StringBuilder line = new StringBuilder();
        line.append(brand.getBrandName().trim()).append("（");
        String business = trimToNull(brand.getMainBusiness());
        line.append(business == null ? "相关行业品牌" : business);
        if (StringUtils.hasText(businessFocus)) {
            line.append("，关注").append(businessFocus);
        }
        line.append("）");
        appendLine(sb, "与本主题相关的、可在合适场景被提及的品牌之一", line.toString());
    }

    private String renderTemplate(String raw,
                                  PromptBuildInput input,
                                  ArticlePromptTemplate template,
                                  String contentAngle,
                                  String audiencePerspective,
                                  String titleStrategy,
                                  String structureStrategy,
                                  String businessFocus,
                                  List<String> recentTitles,
                                  String contactBlock,
                                  Map<String, String> brandFacts) {
        String rendered = StringUtils.hasText(raw) ? raw : "";
        Map<String, String> values = new LinkedHashMap<>();
        values.put("topic", trimToDash(input.topic()));
        values.put("topicAsQuestion", trimToDash(input.topicAsQuestion()));
        values.put("brandName", input.brand() == null ? "-" : trimToDash(input.brand().getBrandName()));
        values.put("industry", resolveIndustry(input.project(), input.brand()));
        values.put("category", resolveIndustry(input.project(), input.brand()));
        values.put("projectName", trimToDash(input.project().getProjectName()));
        values.put("channelName", ArticlePromptChannels.channelName(template.getChannelGroupCode(), template.getChannelSubCode()));
        values.put("articleTypeName", ArticlePromptChannels.ARTICLE_TYPE_LABELS.getOrDefault(template.getArticleTypeCode(), template.getArticleTypeCode()));
        values.put("relatedKeywords", input.relatedKeywords() == null || input.relatedKeywords().isEmpty() ? "-" : String.join("、", input.relatedKeywords()));
        values.put("forbiddenPhrases", forbiddenPhrasesText(input.forbiddenPhrases()));
        values.put("channelGuide", ArticlePromptChannels.channelGuide(template.getChannelGroupCode(), template.getChannelSubCode()));
        values.put("region", resolveRegion(input.project(), input.brand()));
        values.put("targetAudience", trimToDash(input.project().getTargetAudience()));
        values.put("contentAngle", contentAngle);
        values.put("audiencePerspective", audiencePerspective);
        values.put("titleStrategy", titleStrategy == null ? "" : titleStrategy);
        values.put("structureStrategy", structureStrategy == null ? "" : structureStrategy);
        values.put("perspectivePolicy", resolvePerspectivePolicy(input, template));
        values.put("businessFocus", businessFocus == null ? "-" : businessFocus);
        values.put("recentTitles", recentTitles.isEmpty() ? "-" : String.join("；", recentTitles));
        values.put("contactBlock", contactBlock == null ? "" : contactBlock);
        values.put("titleGuide", input.titleGuide() == null ? "" : input.titleGuide());
        values.put("titleElements", input.titleGuide() == null ? "" : input.titleGuide());
        values.putAll(brandFacts);
        return variableRegistry.render(rendered, values);
    }

    public String withSelectedOfferings(String prompt,
                                        List<BrandOfferingPromptSelector.SelectedOffering> offerings) {
        StringBuilder sb = new StringBuilder(prompt == null ? "" : prompt);
        appendSelectedOfferings(sb, offerings);
        return sb.toString();
    }

    private void appendSelectedOfferings(StringBuilder sb, List<BrandOfferingPromptSelector.SelectedOffering> offerings) {
        if (offerings == null || offerings.isEmpty()) {
            return;
        }
        List<BrandOfferingPromptSelector.SelectedOffering> availableOfferings = offerings.stream()
                .filter(offering -> offering != null)
                .filter(offering -> StringUtils.hasText(offering.name()))
                .toList();
        if (availableOfferings.isEmpty()) {
            return;
        }
        sb.append("\n本篇可引用的产品/服务项目/特色业务项：\n");
        for (BrandOfferingPromptSelector.SelectedOffering offering : availableOfferings) {
            sb.append("- ").append(offering.name().trim());
            List<String> aliases = offering.aliases() == null ? List.of() : offering.aliases().stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .toList();
            if (!aliases.isEmpty()) {
                sb.append("（简称：").append(String.join("、", aliases)).append("）");
            }
            sb.append("\n");
            appendNestedLine(sb, "目标人群", offering.targetUsers());
            appendNestedLine(sb, "适用场景", offering.useScenarios());
            appendNestedLine(sb, "介绍", offering.intro());
            appendNestedLine(sb, "资质描述", offering.qualificationDescription());
        }
        sb.append("以上资料只作为解释用户问题时的事实素材，不得编造价格、效果、认证、案例或承诺。\n");
    }

    private Map<String, String> buildBrandFacts(PromptBuildInput input) {
        Brand brand = input.brand();
        Project project = input.project();
        Map<String, String> facts = new LinkedHashMap<>();
        String brandName = brand == null ? null : brand.getBrandName();
        facts.put("companyFullName", trimToDash(firstText(project == null ? null : project.getCompanyName(), brandName)));
        facts.put("brandShortName", trimToDash(firstText(brand == null ? null : brand.getBrandShortName(), brandName)));
        facts.put("mainBusiness", trimToDash(brand == null ? null : brand.getMainBusiness()));
        facts.put("coreProducts", normalizeCommaList(brand == null ? null : brand.getCoreProducts()));
        facts.put("brandPositioning", trimToDash(brand == null ? null : brand.getBrandPositioning()));
        facts.put("serviceArea", trimToDash(brand == null ? null : brand.getServiceArea()));
        facts.put("brandIntro", trimToDash(brand == null ? null : brand.getBusinessIntro()));
        facts.put("brandQualificationDescription", trimToDash(brand == null ? null : brand.getBrandQualificationDescription()));
        facts.put("brandCaseDescription", trimToDash(brand == null ? null : brand.getBrandCaseDescription()));
        return facts;
    }

    private String withGlobalRules(String systemPrompt, List<String> forbiddenPhrases, PromptBuildInput input) {
        boolean selfMedia = isSelfMediaContentStyle(input.contentStyle());
        String globalRules = selfMedia
                ? GLOBAL_TRUTHFULNESS_RULES.replace(
                "生成标题时必须结合 {{topic}}、{{contentAngle}}、{{recentTitles}} 做差异化表达。",
                "生成标题时必须结合 {{topic}}、{{recentTitles}}、{{titleStrategy}} 做差异化表达。")
                + "\n\n"
                + SELF_MEDIA_PLATFORM_POLICY
                : GLOBAL_TRUTHFULNESS_RULES;
        return globalRules
                + "\n\n# 禁用表达\n\n"
                + forbiddenPhrasesInstruction(forbiddenPhrases)
                + "\n\n# 模板级系统提示词\n\n"
                + (StringUtils.hasText(systemPrompt) ? systemPrompt.trim() : SYSTEM_PROMPT);
    }

    private String resolveGlobalRuleVariables(String prompt,
                                              PromptBuildInput input,
                                              String contentAngle,
                                              String titleStrategy,
                                              String structureStrategy,
                                              List<String> recentTitles,
                                              String contactBlock) {
        return prompt
                .replace("{{contactBlock}}", contactBlock == null ? "" : contactBlock)
                .replace("{{topic}}", trimToDash(input.topic()))
                .replace("{{contentAngle}}", contentAngle == null ? "" : contentAngle)
                .replace("{{titleStrategy}}", titleStrategy == null ? "" : titleStrategy)
                .replace("{{structureStrategy}}", structureStrategy == null ? "" : structureStrategy)
                .replace("{{recentTitles}}", recentTitles == null || recentTitles.isEmpty()
                        ? ""
                        : String.join("；", recentTitles));
    }

    public String buildContactBlock(Brand brand, String contactDisclosureMode) {
        String mode = trimToNull(contactDisclosureMode);
        if ("soft_hint".equals(mode)) {
            return "感兴趣的可以自己搜一下相关信息了解。";
        }
        if (!"full".equals(mode) || brand == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        String websiteUrl = trimToNull(brand.getWebsite());
        String contactPhone = trimToNull(brand.getPublicPhone());
        String contactAddress = trimToNull(brand.getPublicAddress());
        if (websiteUrl != null) {
            parts.add("访问官网 " + websiteUrl);
        }
        if (contactPhone != null) {
            parts.add("致电 " + contactPhone + " 咨询");
        }
        if (contactAddress != null) {
            parts.add("地址:" + contactAddress);
        }
        return parts.isEmpty() ? "" : "如需了解更多信息,可" + String.join(",", parts) + "。";
    }

    private String buildContactBlock(ArticlePromptTemplate template, Brand brand) {
        String mode = trimToNull(template == null ? null : template.getContactDisclosureMode());
        return buildContactBlock(brand, mode);
    }

    private String forbiddenPhrasesInstruction(List<String> forbiddenPhrases) {
        List<String> values = ArticleForbiddenPhrasePolicy.effectivePhrases(forbiddenPhrases);
        if (values.isEmpty()) {
            return "未配置额外禁用表达，但仍需遵守绝对化词汇和防编造规则。";
        }
        return "以下表达在本文中不能出现：" + String.join("、", values);
    }

    private String forbiddenPhrasesText(List<String> forbiddenPhrases) {
        List<String> values = ArticleForbiddenPhrasePolicy.effectivePhrases(forbiddenPhrases);
        return values.isEmpty() ? "未配置额外禁用表达" : String.join("、", values);
    }

    private String resolveBusinessFocus(String brandStatement, Brand brand) {
        String raw = trimToNull(brandStatement);
        if (raw == null || raw.length() < 12) {
            return null;
        }
        List<String> candidates = new ArrayList<>();
        for (String part : raw.split("[，,；;。\\n、]")) {
            String item = trimToNull(part);
            if (item == null || item.length() < 3 || item.length() > 16) {
                continue;
            }
            if (containsMarketingWord(item)) {
                continue;
            }
            if (brand != null && StringUtils.hasText(brand.getMainBusiness())
                    && item.equals(brand.getMainBusiness().trim())) {
                continue;
            }
            candidates.add(item);
        }
        List<String> distinct = candidates.stream().distinct().limit(4).toList();
        return distinct.size() < 2 ? null : String.join("、", distinct);
    }

    private boolean containsMarketingWord(String value) {
        return MARKETING_WORDS.stream().anyMatch(value::contains);
    }

    private String resolveIndustry(Project project, Brand brand) {
        if (brand != null && StringUtils.hasText(brand.getIndustry())) {
            String industry = brand.getIndustry().trim();
            SysDictItem item = sysDictItemMapper.selectOne(new LambdaQueryWrapper<SysDictItem>()
                    .eq(SysDictItem::getDictType, "industry_tag")
                    .eq(SysDictItem::getDictKey, industry)
                    .eq(SysDictItem::getEnabled, true)
                    .last("LIMIT 1"));
            if (item != null && StringUtils.hasText(item.getDictValue())) {
                return item.getDictValue().trim();
            }
            return industry;
        }
        return project.getProjectName();
    }

    private String resolveRegion(Project project, Brand brand) {
        List<String> targetRegions = parseJsonArray(project.getTargetRegions());
        if (!targetRegions.isEmpty()) {
            return String.join("、", targetRegions);
        }
        String projectRegion = joinRegion(project.getProvinceName(), project.getCityName(), project.getDistrictName());
        if (StringUtils.hasText(projectRegion)) {
            return projectRegion;
        }
        if (brand != null) {
            String brandRegion = joinRegion(brand.getProvinceName(), brand.getCityName(), brand.getDistrictName());
            if (StringUtils.hasText(brandRegion)) {
                return brandRegion;
            }
            if (StringUtils.hasText(brand.getServiceArea())) {
                return brand.getServiceArea().trim();
            }
        }
        return "-";
    }

    private String joinRegion(String provinceName, String cityName, String districtName) {
        return Stream.of(provinceName, cityName, districtName)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.joining(""));
    }

    private List<String> parseJsonArray(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            List<String> result = new ArrayList<>();
            JSONUtil.parseArray(raw).forEach(item -> {
                if (item != null && StringUtils.hasText(String.valueOf(item))) {
                    result.add(String.valueOf(item).trim());
                }
            });
            return result;
        } catch (Exception ex) {
            return List.of(raw.trim());
        }
    }

    private List<String> resolveHistoryTitles(Long sourceBrandId, Long projectId, int limit) {
        return articleDraftMapper.selectList(
                new LambdaQueryWrapper<ArticleDraft>()
                        .select(ArticleDraft::getTitle, ArticleDraft::getCreatedAt)
                        .eq(sourceBrandId != null, ArticleDraft::getSourceBrandId, sourceBrandId)
                        .eq(sourceBrandId == null, ArticleDraft::getProjectId, projectId)
                        .ge(ArticleDraft::getCreatedAt, LocalDateTime.now().minusDays(30))
                        .orderByDesc(ArticleDraft::getCreatedAt)
                        .last("LIMIT " + Math.max(1, limit))
        ).stream()
                .map(ArticleDraft::getTitle)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
    }

    private String resolveContentAngle(int articleIndexInBatch) {
        return CONTENT_ANGLES.get(Math.floorMod(articleIndexInBatch - 1, CONTENT_ANGLES.size()));
    }

    private String resolveContentAngle(PromptBuildInput input) {
        return isSelfMediaContentStyle(input.contentStyle()) ? null : resolveContentAngle(input.articleIndexInBatch());
    }

    private String resolveSelfMediaTitleStrategy(PromptBuildInput input) {
        if (!isSelfMediaContentStyle(input.contentStyle())) {
            return null;
        }
        return SELF_MEDIA_TITLE_STRATEGIES.get(Math.floorMod(input.articleIndexInBatch() - 1, SELF_MEDIA_TITLE_STRATEGIES.size()));
    }

    private String resolveSelfMediaStructureStrategy(PromptBuildInput input) {
        if (!isSelfMediaContentStyle(input.contentStyle())) {
            return null;
        }
        int index = Math.floorDiv(Math.max(input.articleIndexInBatch() - 1, 0), SELF_MEDIA_TITLE_STRATEGIES.size());
        return SELF_MEDIA_STRUCTURE_STRATEGIES.get(Math.floorMod(index, SELF_MEDIA_STRUCTURE_STRATEGIES.size()));
    }

    private String resolvePerspectivePolicy(PromptBuildInput input, ArticlePromptTemplate template) {
        String perspective = TemplatePerspectiveCodes.normalize(input.perspectiveCode());
        String templateName = template == null ? "" : trimToDash(template.getName());
        String base = switch (perspective) {
            case TemplatePerspectiveCodes.INDUSTRY_NEUTRAL ->
                    "当前为第三方中立视角：不以品牌方、消费者或亲历者身份发声；品牌只能作为可选项或公开信息示例，不做倾向性推荐。";
            case TemplatePerspectiveCodes.REVIEW_RECOMMEND ->
                    "当前为第三方评测/推荐视角：可以给出适配判断，但必须同时写清不适合情形；推荐必须降级为维度判断，不能写榜单、背书或强转化。";
            default ->
                    "当前为第一/客户视角：可以更接近日常经验表达，但不得伪造亲测、成交、治疗、到店、使用前后对比或个人收益；品牌出现必须服务于信息核验。";
        };
        if (templateName.contains("特殊行业")) {
            return base + " 特殊行业模板还必须遵守医疗/医美/口腔等强监管边界：不提供诊疗建议，不承诺效果，不写治疗对比、案例疗效、优惠套餐、预约导流。";
        }
        return base;
    }

    private boolean isSelfMediaContentStyle(String contentStyle) {
        return ArticlePromptChannels.SELF_MEDIA_SUBS.contains(normalize(contentStyle));
    }

    private String resolveAudiencePerspective(int articleIndexInBatch) {
        int index = Math.floorDiv(Math.max(articleIndexInBatch - 1, 0), CONTENT_ANGLES.size());
        return AUDIENCE_PERSPECTIVES.get(Math.floorMod(index, AUDIENCE_PERSPECTIVES.size()));
    }

    private String label(Map<String, String> map, String key, String fallback) {
        return map.getOrDefault(normalize(key), fallback);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
    }

    private void appendLine(StringBuilder sb, String label, String value) {
        if (StringUtils.hasText(value)) {
            sb.append("- ").append(label).append("：").append(value.trim()).append("\n");
        }
    }

    private void appendNestedLine(StringBuilder sb, String label, String value) {
        if (StringUtils.hasText(value)) {
            sb.append("  - ").append(label).append("：").append(value.trim()).append("\n");
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String trimToDash(String value) {
        return StringUtils.hasText(value) ? value.trim() : "-";
    }

    private String firstText(String first, String fallback) {
        return StringUtils.hasText(first) ? first.trim() : fallback;
    }

    private String normalizeCommaList(String value) {
        String raw = trimToNull(value);
        if (raw == null) {
            return "-";
        }
        return Stream.of(raw.split("[,，、;；\\n]+"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.joining("、"));
    }

    private String withTitleGuideInstruction(String userPrompt, String titleGuide) {
        if (!StringUtils.hasText(titleGuide) || userPrompt.contains("# 标题生成参考")) {
            return userPrompt;
        }
        return titleGuide.trim()
                + "\n\n"
                + userPrompt;
    }

    private String promptSafeInline(String value) {
        return StringUtils.hasText(value) ? value.trim().replaceAll("[\"\\\\]", "") : "";
    }

    private String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    public record PromptBuildInput(Project project,
                                   Brand brand,
                                   String brandStatement,
                                   String topicSource,
                                   String topic,
                                   String topicAsQuestion,
                                   Long keywordGroupId,
                                   String keywordGroupName,
                                   List<String> relatedKeywords,
                                   String articleType,
                                   String contentStyle,
                                   String length,
                                   String extraPrompt,
                                   int articleIndexInBatch,
                                   List<String> forbiddenPhrases,
                                   String titleGuide,
                                   String perspectiveCode,
                                   String perspectiveMatchedScope,
                                   Long perspectiveMatchedConfigId,
                                   List<BrandOfferingPromptSelector.SelectedOffering> selectedOfferings,
                                   Long sourceProjectId,
                                   Long sourceBrandId,
                                   Long subjectProjectId,
                                   Long subjectBrandId,
                                   String requestedQuestionSceneCode,
                                   String effectiveQuestionSceneCode,
                                   String questionSceneSource) {
        public PromptBuildInput(Project project,
                                Brand brand,
                                String brandStatement,
                                String topicSource,
                                String topic,
                                String topicAsQuestion,
                                Long keywordGroupId,
                                String keywordGroupName,
                                List<String> relatedKeywords,
                                String articleType,
                                String contentStyle,
                                String length,
                                String extraPrompt,
                                int articleIndexInBatch,
                                List<String> forbiddenPhrases,
                                String titleGuide,
                                String perspectiveCode,
                                String perspectiveMatchedScope,
                                Long perspectiveMatchedConfigId,
                                List<BrandOfferingPromptSelector.SelectedOffering> selectedOfferings,
                                Long sourceProjectId,
                                Long sourceBrandId,
                                Long subjectProjectId,
                                Long subjectBrandId) {
            this(project, brand, brandStatement, topicSource, topic, topicAsQuestion, keywordGroupId, keywordGroupName,
                    relatedKeywords, articleType, contentStyle, length, extraPrompt, articleIndexInBatch,
                    forbiddenPhrases, titleGuide, perspectiveCode, perspectiveMatchedScope, perspectiveMatchedConfigId,
                    selectedOfferings, sourceProjectId, sourceBrandId, subjectProjectId, subjectBrandId,
                    null, null, null);
        }

        public PromptBuildInput(Project project,
                                Brand brand,
                                String brandStatement,
                                String topicSource,
                                String topic,
                                String topicAsQuestion,
                                Long keywordGroupId,
                                String keywordGroupName,
                                List<String> relatedKeywords,
                                String articleType,
                                String contentStyle,
                                String length,
                                String extraPrompt,
                                int articleIndexInBatch,
                                List<String> forbiddenPhrases,
                                String titleGuide,
                                String perspectiveCode,
                                String perspectiveMatchedScope,
                                Long perspectiveMatchedConfigId,
                                List<BrandOfferingPromptSelector.SelectedOffering> selectedOfferings) {
            this(project, brand, brandStatement, topicSource, topic, topicAsQuestion, keywordGroupId, keywordGroupName,
                    relatedKeywords, articleType, contentStyle, length, extraPrompt, articleIndexInBatch,
                    forbiddenPhrases, titleGuide, perspectiveCode, perspectiveMatchedScope, perspectiveMatchedConfigId,
                    selectedOfferings,
                    project == null ? null : project.getId(),
                    brand == null ? null : brand.getId(),
                    project == null ? null : project.getId(),
                    brand == null ? null : brand.getId(),
                    null, null, null);
        }
    }

    public record PromptBuildResult(String systemPrompt,
                                    String userPrompt,
                                    String contentAngle,
                                    String audiencePerspective,
                                    String promptSnapshot,
                                    String inputSnapshot) {
    }
}
