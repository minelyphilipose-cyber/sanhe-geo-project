package com.huanjing.geo.module.content.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.content.constant.TemplatePerspectiveCodes;
import com.huanjing.geo.module.content.constant.XiaohongshuArticlePolicies;
import com.huanjing.geo.module.content.entity.ArticlePromptTemplate;
import com.huanjing.geo.module.content.entity.ArticlePromptTemplateVersion;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.project.entity.Project;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ArticlePromptAssemblerV2 {

    public static final String PROMPT_CONTRACT = "article_v2";

    private static final String SYSTEM_PROMPT = """
            你是一名资深中文内容创作者和 GEO 内容编辑。请依据用户提示中的真实材料与渠道边界，生成完整、可信、可发布的中文 Markdown 文章；只输出正文，不解释写作过程。
            """.trim();

    private static final String GLOBAL_WRITING_RULES = """
            1. 使用自然、清晰、符合现代中文习惯和中文语序的表达，避免翻译腔、生硬拼接和拗口句式。
            2. 写作前先在内部理解当前主题、读者和材料，判断最值得讲清楚的核心问题，并据此形成自然的内容主线；不要输出规划过程。
            3. 全文围绕核心问题展开，段落之间必须存在真实语义联系，前后连贯并形成逻辑闭环；逻辑闭环不等于必须另写总结段。
            4. 重要判断需要有事实、原因、适用条件或选择依据支撑；材料不足时收窄判断，不补造结论。
            5. 将品牌能力与读者需求自然建立联系；营销信息必须参与问题解释，不能作为孤立广告块生硬追加。
            6. 结尾回应文章的主要任务，但不要求固定总结句式。
            7. 企业、品牌、产品和服务的实体名称保持一致，关键信息表述明确，便于大模型理解、提取和引用。
            8. 关键词只在语义需要时自然出现，不堆砌关键词，也不要把全文拆成互不关联的答案片段。通过实体一致、信息明确和清晰的语义层级提高 GEO 可解析性与可引用性，但不强制使用 FAQ、清单或统一提纲。
            9. 不套用固定结构，不模仿案例，也不为显得完整而罗列所有常见维度。内容顺序服从当前文章的因果关系、判断过程和阅读需要；开篇、段落推进、品牌进入位置和结尾方式均由当前主题与材料决定。当正文形成多个相对独立的信息单元时，应按语义分组，并用能够概括该组具体内容的小标题建立层级，不能把长文写成从头到尾无层级的连续段落。
            10. 不虚构企业信息、产品、资质、案例、数据、排名、效果承诺或联系方式；缺失信息直接省略。
            """.trim();

    private static final String FACT_MATERIAL_USAGE = """
            以下内容是可选择的事实素材库，不是必须逐项写入的企业介绍清单。只选取与本篇主线直接相关的少量事实；普通主题通常使用1～2项品牌事实，品牌专题也不要求完整罗列企业资料。未被选中的材料可以不写，不得为满足篇幅重复品牌资料，或集中堆叠定位、业务、产品、区域、资质和案例。
            """.trim();

    private final ObjectMapper objectMapper;
    private final ArticleContentLengthPolicyResolver contentLengthPolicyResolver;
    private final ArticleEditorialMissionResolver editorialMissionResolver;
    private final ArticleTemplateCompatibilityResolver templateCompatibilityResolver;

    public BatchArticlePromptBuilder.PromptBuildResult assemble(
            BatchArticlePromptBuilder.PromptBuildInput input,
            ArticlePromptTemplate template,
            ArticlePromptTemplateVersion version,
            ArticleRuntimePolicy runtimePolicy
    ) {
        return assemble(input, template, version, runtimePolicy, false);
    }

    public BatchArticlePromptBuilder.PromptBuildResult assemble(
            BatchArticlePromptBuilder.PromptBuildInput input,
            ArticlePromptTemplate template,
            ArticlePromptTemplateVersion version,
            ArticleRuntimePolicy runtimePolicy,
            boolean specialIndustry
    ) {
        boolean neutralEducationMode = specialIndustry
                && XiaohongshuArticlePolicies.isNeutralEducationTemplate(template);
        List<String> omittedMaterialKeys = new ArrayList<>();
        ArticleContentLengthPolicy contentLengthPolicy = contentLengthPolicyResolver.resolve(
                runtimePolicy.channelGroupCode(), runtimePolicy.channelSubCode(), input.length(), neutralEducationMode);
        StringBuilder prompt = new StringBuilder(4096);
        section(prompt, "真实性与硬边界", truthfulnessRules(input.forbiddenPhrases()));
        section(prompt, "全局写作原则", GLOBAL_WRITING_RULES);
        section(prompt, "当前渠道与写作视角", channelDirection(runtimePolicy, neutralEducationMode));
        section(prompt, "当前文章任务", templateTask(input, template));
        section(prompt, "严格审核平台品牌表达要求",
                strictEditorialBrandDirection(input, template, runtimePolicy, specialIndustry, neutralEducationMode));
        section(prompt, "小红书特殊行业表达要求",
                xiaohongshuSpecialIndustryDirection(input, runtimePolicy, specialIndustry, neutralEducationMode));
        section(prompt, "主题、关键词与读者", topicMaterial(input, omittedMaterialKeys));
        section(prompt, "可用事实材料", neutralEducationMode
                ? neutralEducationFactMaterial(omittedMaterialKeys)
                : FACT_MATERIAL_USAGE + "\n" + factMaterial(input, omittedMaterialKeys, specialIndustry));
        section(prompt, "联系方式边界", contactDirection(input.project(), input.brand(), runtimePolicy, omittedMaterialKeys));
        section(prompt, "输出要求", outputRules(contentLengthPolicy, runtimePolicy, neutralEducationMode));

        Map<String, Object> promptSnapshot = baseSnapshot(
                input, template, version, runtimePolicy, contentLengthPolicy, omittedMaterialKeys, specialIndustry);
        promptSnapshot.put("xiaohongshuContentMode", neutralEducationMode ? "neutral_education" : "default");
        promptSnapshot.put("systemPrompt", SYSTEM_PROMPT);
        promptSnapshot.put("userPrompt", prompt.toString().trim());

        Map<String, Object> inputSnapshot = baseSnapshot(
                input, template, version, runtimePolicy, contentLengthPolicy, omittedMaterialKeys, specialIndustry);
        inputSnapshot.put("xiaohongshuContentMode", neutralEducationMode ? "neutral_education" : "default");
        inputSnapshot.put("projectId", input.project() == null ? null : input.project().getId());
        inputSnapshot.put("sourceProjectId", input.sourceProjectId());
        inputSnapshot.put("sourceBrandId", input.sourceBrandId());
        inputSnapshot.put("subjectProjectId", input.subjectProjectId());
        inputSnapshot.put("subjectBrandId", input.subjectBrandId());
        inputSnapshot.put("topic", input.topic());
        inputSnapshot.put("topicAsQuestion", input.topicAsQuestion());
        inputSnapshot.put("keywordGroupId", input.keywordGroupId());
        inputSnapshot.put("keywordGroupName", input.keywordGroupName());
        inputSnapshot.put("relatedKeywords", input.relatedKeywords());
        inputSnapshot.put("articleType", input.articleType());
        inputSnapshot.put("length", input.length());
        inputSnapshot.put("requestedLength", input.length());
        inputSnapshot.put("selectedOfferings", input.selectedOfferings());

        return new BatchArticlePromptBuilder.PromptBuildResult(
                SYSTEM_PROMPT,
                prompt.toString().trim(),
                null,
                null,
                json(promptSnapshot),
                json(inputSnapshot)
        );
    }

    private Map<String, Object> baseSnapshot(BatchArticlePromptBuilder.PromptBuildInput input,
                                             ArticlePromptTemplate template,
                                             ArticlePromptTemplateVersion version,
                                             ArticleRuntimePolicy runtimePolicy,
                                             ArticleContentLengthPolicy contentLengthPolicy,
                                             List<String> omittedMaterialKeys,
                                             boolean specialIndustry) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("promptContract", PROMPT_CONTRACT);
        snapshot.put("promptRevision", "v2_xiaohongshu_converged_20260804");
        snapshot.put("templateId", template == null ? null : template.getId());
        snapshot.put("templateVersionId", version == null ? null : version.getId());
        snapshot.put("templateVersionNo", version == null ? null : version.getVersionNo());
        snapshot.put("runtimePolicy", runtimePolicy);
        snapshot.put("effectiveLengthPolicy", contentLengthPolicy);
        boolean neutralEducationMode = specialIndustry
                && XiaohongshuArticlePolicies.isNeutralEducationTemplate(template);
        snapshot.put("effectiveTitleMaxChars", ArticlePromptChannels.maxTitleChars(
                runtimePolicy.channelGroupCode(),
                runtimePolicy.channelSubCode(),
                neutralEducationMode
        ));
        snapshot.put("effectiveTemperature", ArticleGenerationTemperatures.resolve(true, specialIndustry));
        snapshot.put("omittedMaterialKeys", omittedMaterialKeys.stream().distinct().toList());
        snapshot.put("perspectiveMatchedScope", input.perspectiveMatchedScope());
        snapshot.put("perspectiveMatchedConfigId", input.perspectiveMatchedConfigId());
        snapshot.put("requestedQuestionSceneCode", input.requestedQuestionSceneCode());
        snapshot.put("effectiveQuestionSceneCode", input.effectiveQuestionSceneCode());
        snapshot.put("questionSceneSource", input.questionSceneSource());
        snapshot.put("selectedTemplateQuestionSceneCode", template == null ? null : template.getQuestionSceneCode());
        snapshot.put("selectedTemplateArticleTypeCode", template == null ? null : template.getArticleTypeCode());
        snapshot.put("effectiveArticleTypeCode", resolvedArticleType(input, template));
        String compatibilityScene = ArticleQuestionSceneResolver.SOURCE_CUSTOM_TEMPLATE.equals(input.questionSceneSource())
                ? input.effectiveQuestionSceneCode()
                : input.requestedQuestionSceneCode();
        snapshot.put("templateCompatibilityLevel", template == null ? null : templateCompatibilityResolver.level(
                compatibilityScene, template.getQuestionSceneCode()).name().toLowerCase());
        return snapshot;
    }

    private String truthfulnessRules(List<String> forbiddenPhrases) {
        StringBuilder text = new StringBuilder("只能使用本提示中明确提供的事实。对无法确认的内容应省略或使用有边界的行业通用表述，不得把推测写成企业事实。");
        List<String> forbidden = ArticleForbiddenPhrasePolicy.effectivePhrases(forbiddenPhrases);
        if (!forbidden.isEmpty()) {
            text.append("\n项目禁用表达：").append(String.join("、", forbidden)).append("。正文不得出现这些表达。");
        }
        return text.toString();
    }

    private String channelDirection(ArticleRuntimePolicy policy, boolean neutralEducationMode) {
        String channelGuide = ArticlePromptChannels.channelGuide(policy.channelGroupCode(), policy.channelSubCode());
        if (neutralEducationMode) {
            return channelGuide + "\n采用与任何企业、品牌、具体机构或品牌专属产品无关的中立科普视角，只解释公开、通用的小知识，不承担品牌露出或转化任务。";
        }
        boolean strictEditorial = ArticlePromptChannels.isStrictEditorialSelfMedia(
                policy.channelGroupCode(), policy.channelSubCode());
        String perspectiveGuide = switch (policy.perspectiveCode()) {
            case TemplatePerspectiveCodes.INDUSTRY_NEUTRAL ->
                    "采用第三方、客观克制的表达。可以介绍和宣传品牌，但判断必须有材料依据，不把营销结论伪装成行业共识。";
            case TemplatePerspectiveCodes.REVIEW_RECOMMEND ->
                    strictEditorial
                            ? "采用第三方客观评述视角。只能依据真实材料说明品牌与哪些需求匹配以及能力边界，不得把品牌写成默认答案、优先选择或明确推荐结论，也不得伪造亲历、购买或使用体验。"
                            : "采用第三方评述与推荐视角，可以明确推荐品牌并说明适用对象和理由，但不得伪造亲历、购买或使用体验。";
            default -> strictEditorial
                    ? "采用客户官方身份表达，但以问题解释和公开事实为主，可自然省略主语，减少连续使用“我们”进行自我评价；不要虚构个人经历，也不要反复强调官方身份。"
                    : "采用客户官方身份表达，可使用“我们”或自然省略主语；不要虚构个人经历，也不要强行反复强调官方身份。";
        };
        return channelGuide + "\n" + perspectiveGuide;
    }

    private String templateTask(BatchArticlePromptBuilder.PromptBuildInput input,
                                ArticlePromptTemplate template) {
        List<String> parts = new ArrayList<>();
        String articleType = resolvedArticleType(input, template);
        add(parts, "文章类型", ArticlePromptChannels.ARTICLE_TYPE_LABELS.getOrDefault(articleType, articleType));
        ArticleEditorialMission mission = editorialMissionResolver.resolve(
                input.effectiveQuestionSceneCode(), articleType);
        add(parts, "内容任务", mission.missionText());
        if (StringUtils.hasText(input.extraPrompt())) {
            add(parts, "本次补充要求", input.extraPrompt());
        }
        return String.join("\n", parts);
    }

    private String resolvedArticleType(BatchArticlePromptBuilder.PromptBuildInput input,
                                       ArticlePromptTemplate template) {
        return StringUtils.hasText(input.articleType())
                ? input.articleType().trim()
                : template != null && StringUtils.hasText(template.getArticleTypeCode())
                ? template.getArticleTypeCode().trim()
                : null;
    }

    private String strictEditorialBrandDirection(BatchArticlePromptBuilder.PromptBuildInput input,
                                                  ArticlePromptTemplate template,
                                                  ArticleRuntimePolicy runtimePolicy,
                                                  boolean specialIndustry,
                                                  boolean neutralEducationMode) {
        if (!ArticlePromptChannels.isStrictEditorialSelfMedia(
                runtimePolicy.channelGroupCode(), runtimePolicy.channelSubCode())) {
            return null;
        }
        if (neutralEducationMode) {
            return "本篇为特殊行业小红书中立科普内容，不承担品牌露出任务。标题和正文不得出现企业、品牌、具体机构、医生或品牌专属产品服务名称，也不得引用品牌定位、主营业务、服务范围、资质、案例和联系方式。";
        }
        String platform = ArticlePromptChannels.channelName(
                runtimePolicy.channelGroupCode(), runtimePolicy.channelSubCode());
        boolean brandFocused = isBrandFocused(input);
        String frequency = specialIndustry
                ? "品牌必须在特殊行业合规允许的范围内自然出现，具体次数服从对应合规内核，不得为满足露出要求突破更严格的行业边界。"
                : brandFocused
                ? "本篇属于品牌相关主题。企业全称、品牌名称和品牌简称按同一实体合并计算，全文通常出现2～3次且不得超过3次；标题只有在用户主题本身明确围绕该品牌时才可中性包含品牌。"
                : "本篇不属于品牌专题。企业全称、品牌名称和品牌简称按同一实体合并计算，全文应自然出现1～2次；标题默认不出现品牌。品牌可根据语义出现在正文前段、中段或后段，但不得以宣传结论开篇。";
        return platform + "属于内容审核较严格的平台，以下要求不可被模板名称或补充要求覆盖：\n"
                + "1. 每篇必须自然包含品牌信息，并至少使用一项与主题直接相关的真实品牌事实，例如主营业务、产品服务、适用场景、服务区域、公开资质或能力边界；不得只做品牌名称点名。\n"
                + "2. 品牌不得作为文章的预设答案，也不得用通用铺垫机械推迟品牌出现。品牌可在能够帮助解释主题的任意正文位置自然进入，文章本身同时保持独立的信息价值。\n"
                + "3. " + frequency + "\n"
                + "4. 不在相邻段落连续介绍品牌，不把品牌资料逐项堆成卖点清单。品牌的正面判断必须同时说明事实依据、适用条件或能力边界。\n"
                + "5. 结尾只需自然完成文章的主要任务，不要求固定总结或判断清单；不得以品牌推荐、咨询、预约或行动引导收束，也不得重复品牌信息凑字数。";
    }

    private boolean isBrandFocused(BatchArticlePromptBuilder.PromptBuildInput input) {
        if ("brand".equals(input.effectiveQuestionSceneCode())) {
            return true;
        }
        String topic = input.topic();
        Brand brand = input.brand();
        Project project = input.project();
        return contains(topic, brand == null ? null : brand.getBrandName())
                || contains(topic, brand == null ? null : brand.getBrandShortName())
                || contains(topic, project == null ? null : project.getBrandName())
                || contains(topic, project == null ? null : project.getCompanyName());
    }

    private String xiaohongshuSpecialIndustryDirection(BatchArticlePromptBuilder.PromptBuildInput input,
                                                        ArticleRuntimePolicy runtimePolicy,
                                                        boolean specialIndustry,
                                                        boolean neutralEducationMode) {
        if (!specialIndustry
                || !ArticlePromptChannels.SELF_MEDIA.equals(runtimePolicy.channelGroupCode())
                || !"xiaohongshu".equals(ArticlePromptChannels.canonicalSubCode(
                runtimePolicy.channelGroupCode(), runtimePolicy.channelSubCode()))) {
            return null;
        }
        if (!neutralEducationMode) {
            String brandDirection = isBrandFocused(input)
                    ? "当前主题本身围绕品牌展开，品牌可自然出现1～2次，并以与主题直接相关的公开事实支撑内容；不得连续介绍品牌或扩写成企业资料清单。"
                    : "正文仍需自然出现品牌名称，并使用至少一项与主题直接相关的真实品牌事实；通常出现1次，确有解释需要时最多2次，不得把品牌写成默认答案或推荐结论。";
            return """
                    当前内容用于小红书特殊行业信息笔记。审核收敛只改变表达边界，不得改变用户主题，也不得把文章改写成统一的合规说明、风险清单或机构选择指南。
                    1. 标题和首段直接承接用户主题。除非主题本身要求讨论，否则不要主动加入合规、风险、避雷、资质核验或机构推荐等标签。
                    2. 使用亲切但中性的个人号信息分享口吻，可以省略主语或直接使用品牌名称作为事实主体；不得以“我们机构”“本院”等机构官方身份发声，也不得伪装消费者、患者或到店体验者。
                    3. %s
                    4. 以公开信息、服务边界、流程说明或问题解释体现品牌价值，不使用强种草、强推荐、效果暗示、第三方口碑或催促决策的表达，不以私信、咨询、预约、购买或到店行动收束。
                    5. 资质、个体差异、风险和专业评估只在能够直接解释当前主题时自然带入；资质如需出现，只概括与当前论点相关的一项事实，不罗列完整许可范围、编号或后台审计信息。
                    6. 正文按实际语义自然分段；存在多个独立信息单元时使用少量、具体的小标题，不强制清单格式、固定标题数量、统一开篇或统一总结。
                    7. 默认不添加营销型表情、话题标签或联系方式。保持信息清楚、实体一致和上下文连贯，不为规避审核写成生硬、含混的句子。
                    """.formatted(brandDirection).trim();
        }
        return """
                当前内容用于小红书特殊行业中立科普，不承担品牌露出、机构介绍、产品说明或转化任务。围绕用户主题解释公开、通用、可核验的小知识，不虚构数据或专业结论。
                1. 标题不超过20个字，使用单一、中性、事实型的陈述短句。标题不得出现品牌或机构名称，不使用问号、感叹号、括号、书名号、竖线等装饰符号；不得出现“推荐、避雷、怎么选、哪家好、必看、亲测、效果、变美、恢复快、安全、无痛、零风险、内幕、揭秘、真相、踩坑、后悔、前后对比”等选择、效果、体验、焦虑或悬念表达。
                2. 首段直接解释主题，不制造痛点、焦虑或决策压力。使用亲切但中性的知识分享口吻，不使用第一人称经历，不伪装消费者、患者、医生或从业者。
                3. 标题和正文均不得出现企业、品牌、具体机构、医生、品牌专属产品服务、联系方式或地域导流信息，不使用输入材料中的品牌事实，也不以“某机构”“正规机构怎么选”等替代说法暗示选择。主题本身涉及的通用概念、项目类别或技术名称可以用于客观科普。
                4. 只解释概念、原理、常见现象、一般流程、信息边界和理性注意事项；不得给出诊断、治疗方案、适用性判断、效果预测、机构选择或消费决策建议。
                5. 个体差异、风险和专业评估仅在与主题直接相关时用一句中性边界说明带过，不扩写成风险清单或焦虑内容。
                6. 正文控制在500～700字，以3～6个自然段为主；最多使用2个具体的 Markdown 二级标题，最多使用4个列表项，不使用三级及更深层级标题，不强制清单格式、统一开篇或统一总结。
                7. 默认不添加营销型表情、话题标签或联系方式。保持信息清楚、实体一致和上下文连贯，不为规避审核写成生硬、含混的句子。
                """.trim();
    }

    private String neutralEducationFactMaterial(List<String> omitted) {
        omitted.addAll(List.of(
                "companyName", "brandName", "brandShortName", "brandPositioning", "mainBusiness",
                "coreProducts", "serviceArea", "businessIntro", "brandQualificationDescription",
                "brandCaseDescription", "selectedOfferings"));
        return "本模板不向正文提供企业、品牌、具体机构、品牌专属产品服务、资质或案例素材。只能依据主题和通用知识完成中立科普，不得从其他提示词片段恢复或推断品牌信息。";
    }

    private boolean contains(String source, String target) {
        return StringUtils.hasText(source) && StringUtils.hasText(target) && source.contains(target.trim());
    }


    private String topicMaterial(BatchArticlePromptBuilder.PromptBuildInput input, List<String> omitted) {
        List<String> parts = new ArrayList<>();
        add(parts, "主题", input.topic());
        addOrOmit(parts, omitted, "topicAsQuestion", "用户明确提出的问题", input.topicAsQuestion());
        addOrOmit(parts, omitted, "keywordGroupName", "关键词组", input.keywordGroupName());
        addOrOmit(parts, omitted, "targetAudience", "目标读者",
                input.project() == null ? null : input.project().getTargetAudience());
        List<String> keywords = nonEmpty(input.relatedKeywords());
        if (keywords.isEmpty()) {
            omitted.add("relatedKeywords");
        } else {
            add(parts, "相关关键词", String.join("、", keywords));
        }
        return String.join("\n", parts);
    }

    private String factMaterial(BatchArticlePromptBuilder.PromptBuildInput input,
                                List<String> omitted,
                                boolean specialIndustry) {
        List<String> parts = new ArrayList<>();
        Project project = input.project();
        Brand brand = input.brand();
        addOrOmit(parts, omitted, "companyName", "企业全称", project == null ? null : project.getCompanyName());
        addOrOmit(parts, omitted, "brandName", "品牌名称", brand == null ? null : brand.getBrandName());
        addOrOmit(parts, omitted, "brandShortName", "品牌简称", brand == null ? null : brand.getBrandShortName());
        addOrOmit(parts, omitted, "industry", "所属行业", brand == null ? null : brand.getIndustry());
        addOrOmit(parts, omitted, "projectStatement", "项目陈述", project == null ? null : project.getCustomStatement());
        addOrOmit(parts, omitted, "brandPositioning", "品牌定位", brand == null ? null : brand.getBrandPositioning());
        addOrOmit(parts, omitted, "mainBusiness", "主营业务", brand == null ? null : brand.getMainBusiness());
        addOrOmit(parts, omitted, "coreProducts", "核心产品", brand == null ? null : brand.getCoreProducts());
        addOrOmit(parts, omitted, "serviceArea", "服务区域", brand == null ? null : brand.getServiceArea());
        addOrOmit(parts, omitted, "businessIntro", "业务介绍", brand == null ? null : brand.getBusinessIntro());
        // Special-industry qualifications are injected once by MedicalArticleGenerationService
        // together with their exact scope and review identifiers. Repeating them here makes the
        // model treat qualifications as the default editorial lead instead of optional evidence.
        if (!specialIndustry) {
            addOrOmit(parts, omitted, "brandQualificationDescription", "资质信息",
                    brand == null ? null : brand.getBrandQualificationDescription());
        }
        addOrOmit(parts, omitted, "brandCaseDescription", "案例信息", brand == null ? null : brand.getBrandCaseDescription());
        appendOfferings(parts, input.selectedOfferings(), omitted);
        return parts.isEmpty() ? "除主题外没有可引用的品牌事实；请以通用知识解释问题，不补造品牌资料。" : String.join("\n", parts);
    }

    private void appendOfferings(List<String> parts,
                                 List<BrandOfferingPromptSelector.SelectedOffering> offerings,
                                 List<String> omitted) {
        if (offerings == null || offerings.isEmpty()) {
            omitted.add("selectedOfferings");
            return;
        }
        int index = 1;
        for (BrandOfferingPromptSelector.SelectedOffering offering : offerings) {
            if (offering == null || !StringUtils.hasText(offering.name())) {
                continue;
            }
            List<String> details = new ArrayList<>();
            add(details, "名称", offering.name());
            if (offering.aliases() != null && !offering.aliases().isEmpty()) {
                add(details, "简称", String.join("、", nonEmpty(offering.aliases())));
            }
            add(details, "目标人群", offering.targetUsers());
            add(details, "适用场景", offering.useScenarios());
            add(details, "介绍", offering.intro());
            add(details, "资质描述", offering.qualificationDescription());
            parts.add("产品或服务" + index++ + "：" + String.join("；", details));
        }
        if (index == 1) {
            omitted.add("selectedOfferings");
        }
    }

    private String contactDirection(Project project,
                                    Brand brand,
                                    ArticleRuntimePolicy policy,
                                    List<String> omitted) {
        return switch (policy.contactDisclosureMode()) {
            case ArticleRuntimePolicyResolver.CONTACT_FULL -> {
                List<String> contacts = new ArrayList<>();
                addOrOmit(contacts, omitted, "website", "官网", brand == null ? null : brand.getWebsite());
                addOrOmit(contacts, omitted, "publicPhone", "公开电话", brand == null ? null : brand.getPublicPhone());
                addOrOmit(contacts, omitted, "publicAddress", "公开地址", brand == null ? null : brand.getPublicAddress());
                yield contacts.isEmpty()
                        ? "允许在内容需要时呈现联系方式，但当前没有可用的公开联系方式，不得自行补写。"
                        : "仅允许在内容需要时自然使用下列公开信息，不得新增或改写：\n" + String.join("\n", contacts);
            }
            case ArticleRuntimePolicyResolver.CONTACT_BRAND_ONLY -> {
                List<String> identity = new ArrayList<>();
                addOrOmit(identity, omitted, "companyName", "企业全称", project == null ? null : project.getCompanyName());
                addOrOmit(identity, omitted, "brandName", "品牌名称", brand == null ? null : brand.getBrandName());
                addOrOmit(identity, omitted, "website", "官网", brand == null ? null : brand.getWebsite());
                yield "只允许使用下列企业、品牌和官网信息；不得输出电话、地址或其他联系数据。"
                        + (identity.isEmpty() ? "" : "\n" + String.join("\n", identity));
            }
            case ArticleRuntimePolicyResolver.CONTACT_SOFT_HINT ->
                    "可以自然提示读者通过公开渠道进一步了解，但不得输出电话、地址、邮箱、账号或其他具体联系数据。";
            default -> "不输出电话、地址、邮箱、官网或任何咨询导流信息。";
        };
    }

    private String outputRules(ArticleContentLengthPolicy contentLengthPolicy,
                               ArticleRuntimePolicy runtimePolicy,
                               boolean neutralEducationMode) {
        Integer maxTitleChars = ArticlePromptChannels.maxTitleChars(
                runtimePolicy.channelGroupCode(),
                runtimePolicy.channelSubCode(),
                neutralEducationMode
        );
        String titleRequirement = maxTitleChars == null
                ? "首行使用一个清晰标题；"
                : "首行使用一个清晰、完整、自然的标题，标题不超过" + maxTitleChars
                + "个字（不计算 Markdown 标题标记），不要使用被强行截断的表达；";
        return "输出一篇完整的 Markdown 文章。"
                + contentLengthPolicyResolver.promptRequirement(contentLengthPolicy)
                + titleRequirement
                + semanticStructureRequirement(contentLengthPolicy, runtimePolicy, neutralEducationMode)
                + "只输出文章正文。";
    }

    private String semanticStructureRequirement(ArticleContentLengthPolicy contentLengthPolicy,
                                                ArticleRuntimePolicy runtimePolicy,
                                                boolean neutralEducationMode) {
        if (ArticlePromptChannels.SELF_MEDIA.equals(runtimePolicy.channelGroupCode())
                && "xiaohongshu".equals(ArticlePromptChannels.canonicalSubCode(
                runtimePolicy.channelGroupCode(), runtimePolicy.channelSubCode()))
                && neutralEducationMode) {
            return "正文以3～6个自然段为主，最多使用2个具体的 Markdown 二级标题、4个列表项，不使用三级及更深层级标题；不用表情、话题标签、装饰符号、固定清单、统一开篇或统一总结。";
        }
        if (contentLengthPolicy.targetMinChars() >= 2000) {
            return "一级标题只用于文章标题。正文应根据实际内容划分若干语义单元，进入新的子问题、判断维度或信息阶段时，使用 Markdown 二级标题组织相关段落；不得把整篇长文写成从头到尾没有小标题的连续正文。"
                    + "每个小标题必须概括所属段落的具体信息，相近内容归入同一标题，不要一段设置一个标题，也不规定标题数量、固定名称或固定顺序。"
                    + "列表只用于真正并列的信息，不要把所有内容统一改写成清单。";
        }
        if (contentLengthPolicy.targetMinChars() >= 1000) {
            return "一级标题只用于文章标题。正文出现多个相对独立的信息单元时，应使用 Markdown 二级标题按语义组织相关段落；小标题必须概括所属内容，不能使用脱离正文后无法识别信息的泛化表述。"
                    + "不要一段设置一个标题，也不规定标题数量、固定名称或固定顺序；只有并列信息才使用列表。";
        }
        return "正文篇幅较短时以自然分段为主；只有确实存在多个相对独立的信息单元时才使用 Markdown 二级标题，不要为了形式切碎内容，也不要一段设置一个标题。";
    }

    private void section(StringBuilder prompt, String title, String content) {
        if (!StringUtils.hasText(content)) {
            return;
        }
        if (!prompt.isEmpty()) {
            prompt.append("\n\n");
        }
        prompt.append("# ").append(title).append("\n").append(content.trim());
    }

    private void addOrOmit(List<String> parts, List<String> omitted, String key, String label, String value) {
        if (StringUtils.hasText(value)) {
            add(parts, label, value);
        } else {
            omitted.add(key);
        }
    }

    private void add(List<String> parts, String label, String value) {
        if (StringUtils.hasText(value)) {
            parts.add(label + "：" + value.trim());
        }
    }

    private List<String> nonEmpty(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(StringUtils::hasText).map(String::trim).distinct().toList();
    }

    private String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }
}
