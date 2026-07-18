package com.huanjing.geo.module.content.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.content.constant.TemplatePerspectiveCodes;
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
            2. 写作前先在内部理解主题、读者和材料，自主确定内容组织方式；不要输出规划过程。
            3. 全文围绕核心问题展开，段落之间必须存在真实语义联系，前后连贯并形成逻辑闭环。
            4. 重要判断需要有事实、原因、适用条件或选择依据支撑；材料不足时收窄判断，不补造结论。
            5. 将品牌能力与读者需求自然建立联系；营销信息必须参与问题解释，不能作为孤立广告块生硬追加。
            6. 结尾回应文章的主要任务，但不要求固定总结句式。
            7. 企业、品牌、产品和服务的实体名称保持一致，关键信息表述明确，便于大模型理解、提取和引用。
            8. 关键词只在语义需要时自然出现，不堆砌关键词，也不要把全文拆成互不关联的答案片段。
            9. 不套用固定结构，不模仿案例；根据本次主题、材料和渠道自主决定开篇、论证顺序、品牌进入位置和结尾方式。
            10. 不虚构企业信息、产品、资质、案例、数据、排名、效果承诺或联系方式；缺失信息直接省略。
            """.trim();

    private final ObjectMapper objectMapper;
    private final ArticleContentLengthPolicyResolver contentLengthPolicyResolver;

    public BatchArticlePromptBuilder.PromptBuildResult assemble(
            BatchArticlePromptBuilder.PromptBuildInput input,
            ArticlePromptTemplate template,
            ArticlePromptTemplateVersion version,
            ArticleRuntimePolicy runtimePolicy
    ) {
        List<String> omittedMaterialKeys = new ArrayList<>();
        ArticleContentLengthPolicy contentLengthPolicy = contentLengthPolicyResolver.resolve(
                runtimePolicy.channelGroupCode(), runtimePolicy.channelSubCode(), input.length());
        StringBuilder prompt = new StringBuilder(4096);
        section(prompt, "真实性与硬边界", truthfulnessRules(input.forbiddenPhrases()));
        section(prompt, "全局写作原则", GLOBAL_WRITING_RULES);
        section(prompt, "当前渠道与写作视角", channelDirection(runtimePolicy));
        section(prompt, "当前模板任务", templateTask(input, template, version));
        section(prompt, "主题、关键词与读者", topicMaterial(input, omittedMaterialKeys));
        section(prompt, "可用事实材料", factMaterial(input, omittedMaterialKeys));
        section(prompt, "联系方式边界", contactDirection(input.project(), input.brand(), runtimePolicy, omittedMaterialKeys));
        section(prompt, "输出要求", outputRules(contentLengthPolicy, runtimePolicy));

        Map<String, Object> promptSnapshot = baseSnapshot(
                input, template, version, runtimePolicy, contentLengthPolicy, omittedMaterialKeys);
        promptSnapshot.put("systemPrompt", SYSTEM_PROMPT);
        promptSnapshot.put("userPrompt", prompt.toString().trim());

        Map<String, Object> inputSnapshot = baseSnapshot(
                input, template, version, runtimePolicy, contentLengthPolicy, omittedMaterialKeys);
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
                                             List<String> omittedMaterialKeys) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("promptContract", PROMPT_CONTRACT);
        snapshot.put("templateId", template == null ? null : template.getId());
        snapshot.put("templateVersionId", version == null ? null : version.getId());
        snapshot.put("templateVersionNo", version == null ? null : version.getVersionNo());
        snapshot.put("runtimePolicy", runtimePolicy);
        snapshot.put("effectiveLengthPolicy", contentLengthPolicy);
        snapshot.put("effectiveTitleMaxChars", ArticlePromptChannels.maxTitleChars(runtimePolicy.channelGroupCode()));
        snapshot.put("omittedMaterialKeys", omittedMaterialKeys.stream().distinct().toList());
        snapshot.put("perspectiveMatchedScope", input.perspectiveMatchedScope());
        snapshot.put("perspectiveMatchedConfigId", input.perspectiveMatchedConfigId());
        return snapshot;
    }

    private String truthfulnessRules(List<String> forbiddenPhrases) {
        StringBuilder text = new StringBuilder("只能使用本提示中明确提供的事实。对无法确认的内容应省略或使用有边界的行业通用表述，不得把推测写成企业事实。");
        List<String> forbidden = nonEmpty(forbiddenPhrases);
        if (!forbidden.isEmpty()) {
            text.append("\n项目禁用表达：").append(String.join("、", forbidden)).append("。正文不得出现这些表达。");
        }
        return text.toString();
    }

    private String channelDirection(ArticleRuntimePolicy policy) {
        String channelGuide = ArticlePromptChannels.channelGuide(policy.channelGroupCode(), policy.channelSubCode());
        String perspectiveGuide = switch (policy.perspectiveCode()) {
            case TemplatePerspectiveCodes.INDUSTRY_NEUTRAL ->
                    "采用第三方、客观克制的表达。可以介绍和宣传品牌，但判断必须有材料依据，不把营销结论伪装成行业共识。";
            case TemplatePerspectiveCodes.REVIEW_RECOMMEND ->
                    "采用第三方评述与推荐视角，可以明确推荐品牌并说明适用对象和理由，但不得伪造亲历、购买或使用体验。";
            default -> "采用客户官方身份表达，可使用“我们”或自然省略主语；不要虚构个人经历，也不要强行反复强调官方身份。";
        };
        return channelGuide + "\n" + perspectiveGuide;
    }

    private String templateTask(BatchArticlePromptBuilder.PromptBuildInput input,
                                ArticlePromptTemplate template,
                                ArticlePromptTemplateVersion version) {
        List<String> parts = new ArrayList<>();
        add(parts, "模板名称", template == null ? null : template.getName());
        add(parts, "模板说明", safeTemplateDescription(template == null ? null : template.getDescription()));
        add(parts, "文章类型", ArticlePromptChannels.ARTICLE_TYPE_LABELS.getOrDefault(input.articleType(), input.articleType()));
        String task = renderTemplateTask(version == null ? null : version.getUserPromptTemplate(), input);
        if (StringUtils.hasText(task) && !task.trim().equals(input.topic())) {
            add(parts, "本模板补充任务", task);
        }
        if (StringUtils.hasText(input.extraPrompt())) {
            add(parts, "本次补充要求", input.extraPrompt());
        }
        return String.join("\n", parts);
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

    private String factMaterial(BatchArticlePromptBuilder.PromptBuildInput input, List<String> omitted) {
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
        addOrOmit(parts, omitted, "brandQualificationDescription", "资质信息", brand == null ? null : brand.getBrandQualificationDescription());
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
                               ArticleRuntimePolicy runtimePolicy) {
        Integer maxTitleChars = ArticlePromptChannels.maxTitleChars(runtimePolicy.channelGroupCode());
        String titleRequirement = maxTitleChars == null
                ? "首行使用一个清晰标题；"
                : "首行使用一个清晰、完整、自然的标题，标题不超过" + maxTitleChars
                + "个字（不计算 Markdown 标题标记），不要使用被强行截断的表达；";
        return "输出一篇完整的 Markdown 文章。"
                + contentLengthPolicyResolver.promptRequirement(contentLengthPolicy)
                + titleRequirement
                + "正文自然分段，可按语义需要使用小标题或列表，但不要为了形式强行切段。只输出文章正文。";
    }

    private String renderTemplateTask(String raw, BatchArticlePromptBuilder.PromptBuildInput input) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.trim()
                .replace("{{topic}}", nullToEmpty(input.topic()))
                .replace("{{topicAsQuestion}}", nullToEmpty(input.topicAsQuestion()))
                .replace("{{keywordGroupName}}", nullToEmpty(input.keywordGroupName()))
                .replace("{{relatedKeywords}}", String.join("、", nonEmpty(input.relatedKeywords())))
                .replace("{{articleType}}", nullToEmpty(input.articleType()))
                .replace("{{length}}", nullToEmpty(input.length()))
                .replaceAll("\\{\\{[^{}]+}}", "");
    }

    private String safeTemplateDescription(String description) {
        if (!StringUtils.hasText(description)) {
            return null;
        }
        String value = description.trim();
        List<String> structuralAnchors = List.of("固定结构", "结构骨架", "结构要求", "段式", "示例标题", "示范提纲");
        return structuralAnchors.stream().anyMatch(value::contains) ? null : value;
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

    private String nullToEmpty(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }
}
