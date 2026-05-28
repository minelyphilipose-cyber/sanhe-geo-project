package com.huanjing.geo.module.content.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.ContentErrorCodes;
import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.content.constant.ArticleTypes;
import com.huanjing.geo.module.content.entity.ArticlePromptTemplate;
import com.huanjing.geo.module.content.entity.ArticlePromptTemplateVersion;
import com.huanjing.geo.module.content.entity.BatchArticleGenerationBatch;
import com.huanjing.geo.module.content.entity.BatchArticleGenerationTask;
import com.huanjing.geo.module.content.mapper.ArticlePromptTemplateMapper;
import com.huanjing.geo.module.content.mapper.ArticlePromptTemplateVersionMapper;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.project.entity.KeywordGroup;
import com.huanjing.geo.module.project.entity.KeywordGroupResult;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.entity.ProjectKeywordGroupRel;
import com.huanjing.geo.module.project.mapper.KeywordGroupMapper;
import com.huanjing.geo.module.project.mapper.KeywordGroupResultMapper;
import com.huanjing.geo.module.project.mapper.ProjectKeywordGroupRelMapper;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ArticleGenerationPromptContextFactory {

    private static final String TEMPLATE_SOURCE_FALLBACK_DEFAULT_PROMPT = "fallback_default_prompt";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final ProjectMapper projectMapper;
    private final BrandMapper brandMapper;
    private final KeywordGroupMapper keywordGroupMapper;
    private final KeywordGroupResultMapper keywordGroupResultMapper;
    private final ProjectKeywordGroupRelMapper projectKeywordGroupRelMapper;
    private final ArticlePromptTemplateMapper promptTemplateMapper;
    private final ArticlePromptTemplateVersionMapper promptTemplateVersionMapper;
    private final BatchArticlePromptBuilder promptBuilder;

    public PromptContextResult buildForBatch(BatchArticleGenerationBatch batch,
                                             BatchArticleGenerationTask task) {
        PromptContextRequest request = new PromptContextRequest(
                batch.getProjectId(),
                batch.getTopicSource(),
                task.getArticleType(),
                task.getChannelGroupCode(),
                task.getChannelSubCode(),
                StringUtils.hasText(task.getTopic()) ? task.getTopic() : batch.getTopic(),
                task.getTopicAsQuestion(),
                task.getLength(),
                // Batch topic provenance stays on the task; prompt keyword selection follows
                // the same project-level rules as single template generation.
                null,
                null,
                task.getExtraPrompt(),
                task.getPromptTemplateId(),
                task.getPromptTemplateVersionId(),
                task.getArticleIndexInBatch()
        );
        return build(request, true);
    }

    public PromptContextResult buildStrict(PromptContextRequest request) {
        return build(request, false);
    }

    private PromptContextResult build(PromptContextRequest request, boolean allowDefaultPromptFallback) {
        Project project = requireProject(request.projectId());
        Brand brand = project.getBrandId() == null ? null : brandMapper.selectById(project.getBrandId());
        ChannelRef channel = resolveChannel(request.channelGroupCode(), request.channelSubCode());
        String topic = requireTopic(request.topic());
        String articleType = normalizeArticleType(request.articleType());
        int articleIndexInBatch = request.articleIndexInBatch();
        String topicAsQuestion = StringUtils.hasText(request.topicAsQuestion())
                ? request.topicAsQuestion().trim()
                : promptBuilder.topicAsQuestion(topic, articleType, articleIndexInBatch);
        KeywordGroup keywordGroup = validateKeywordGroup(project.getId(), request.keywordGroupId());
        Long keywordGroupId = keywordGroup == null ? null : keywordGroup.getId();
        String keywordGroupName = keywordGroup == null ? trimToNull(request.keywordGroupName()) : keywordGroup.getName();
        List<String> forbiddenPhrases = forbiddenPhrases(project, brand);

        ArticlePromptTemplate titleTemplate = request.promptTemplateId() == null
                ? null
                : promptTemplateMapper.selectById(request.promptTemplateId());
        String titleGuide = buildTitleGuide(channel.groupCode(), articleIndexInBatch, titleTemplate, project, brand, topic);
        BatchArticlePromptBuilder.PromptBuildInput input = new BatchArticlePromptBuilder.PromptBuildInput(
                project,
                brand,
                resolveBrandStatement(project, brand),
                StringUtils.hasText(request.topicSource()) ? request.topicSource().trim() : "manual",
                topic,
                topicAsQuestion,
                keywordGroupId,
                keywordGroupName,
                relatedKeywords(project, keywordGroupId),
                articleType,
                channel.contentStyle(),
                StringUtils.hasText(request.length()) ? request.length().trim() : "medium",
                trimToNull(request.extraPrompt()),
                articleIndexInBatch,
                forbiddenPhrases,
                titleGuide
        );

        TemplateResolution resolution = resolveTemplate(request, channel, articleType, allowDefaultPromptFallback);
        BatchArticlePromptBuilder.PromptBuildResult prompt = resolution.template() == null
                ? promptBuilder.build(input)
                : promptBuilder.buildFromTemplate(input, resolution.template(), resolution.version());
        return new PromptContextResult(project, brand, input, prompt, forbiddenPhrases,
                resolution.template(), resolution.version(), channel.groupCode(), channel.subCode(), channel.contentStyle(),
                topicAsQuestion, resolution.fallbackToDefaultPrompt());
    }

    private Project requireProject(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null || project.getDeletedAt() != null) {
            throw new BizException(404, "Project not found");
        }
        return project;
    }

    private String requireTopic(String topic) {
        String value = trimToNull(topic);
        if (value == null) {
            throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "Topic is required");
        }
        return value;
    }

    private String normalizeArticleType(String articleType) {
        String value = trimToNull(articleType);
        if (value == null || !ArticleTypes.isSupported(value)) {
            throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "Invalid article type");
        }
        return value;
    }

    private ChannelRef resolveChannel(String channelGroupCode, String channelSubCode) {
        String group = trimToNull(channelGroupCode);
        String sub = trimToNull(channelSubCode);
        if (!ArticlePromptChannels.isValidCode(group)) {
            throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "Invalid channel group");
        }
        if (sub != null && !ArticlePromptChannels.isValidCode(sub)) {
            throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "Invalid channel sub code");
        }
        sub = ArticlePromptChannels.canonicalSubCode(group, sub);
        return new ChannelRef(group, sub, ArticlePromptChannels.contentStyle(group, sub));
    }

    private KeywordGroup validateKeywordGroup(Long projectId, Long keywordGroupId) {
        if (keywordGroupId == null) {
            return null;
        }
        Long count = projectKeywordGroupRelMapper.selectCount(
                new LambdaQueryWrapper<ProjectKeywordGroupRel>()
                        .eq(ProjectKeywordGroupRel::getProjectId, projectId)
                        .eq(ProjectKeywordGroupRel::getKeywordGroupId, keywordGroupId)
        );
        if (count == null || count <= 0) {
            throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "Keyword group does not belong to project");
        }
        KeywordGroup group = keywordGroupMapper.selectById(keywordGroupId);
        if (group == null || Boolean.TRUE.equals(group.getDeleted())) {
            throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "Keyword group not found");
        }
        return group;
    }

    private TemplateResolution resolveTemplate(PromptContextRequest request,
                                               ChannelRef channel,
                                               String articleType,
                                               boolean allowDefaultPromptFallback) {
        if ((request.promptTemplateId() == null && allowDefaultPromptFallback) || request.promptTemplateVersionId() == null) {
            if (allowDefaultPromptFallback) {
                return new TemplateResolution(null, null, true);
            }
            throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "Prompt template version is required");
        }
        ArticlePromptTemplateVersion version = promptTemplateVersionMapper.selectById(request.promptTemplateVersionId());
        ArticlePromptTemplate template = version == null ? null : promptTemplateMapper.selectById(version.getTemplateId());
        if (template == null || version == null
                || (request.promptTemplateId() != null && !Objects.equals(request.promptTemplateId(), version.getTemplateId()))) {
            if (allowDefaultPromptFallback) {
                return new TemplateResolution(null, null, true);
            }
            throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "Prompt template version not found");
        }
        if (!allowDefaultPromptFallback) {
            validateTemplateApplicable(template, version, channel, articleType);
        }
        return new TemplateResolution(template, version, false);
    }

    private void validateTemplateApplicable(ArticlePromptTemplate template,
                                            ArticlePromptTemplateVersion version,
                                            ChannelRef channel,
                                            String articleType) {
        if (!ArticlePromptTemplateService.STATUS_ACTIVE.equals(template.getStatus())
                || !ArticlePromptTemplateService.VERSION_PUBLISHED.equals(version.getStatus())
                || !Objects.equals(template.getCurrentVersionId(), version.getId())) {
            throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "Prompt template version is not active");
        }
        if (!Objects.equals(template.getChannelGroupCode(), channel.groupCode())
                || !Objects.equals(ArticlePromptChannels.canonicalSubCode(template.getChannelGroupCode(), template.getChannelSubCode()), channel.subCode())) {
            throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "Prompt template does not match channel");
        }
        String templateArticleType = normalizeArticleType(template.getArticleTypeCode());
        if (!Objects.equals(templateArticleType, articleType)) {
            throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "Prompt template does not match article type");
        }
    }

    private List<String> relatedKeywords(Project project, Long keywordGroupId) {
        List<String> coreKeywords = parseCommaKeywords(project.getCoreKeywords());
        if (!coreKeywords.isEmpty()) {
            return coreKeywords;
        }
        List<Long> groupIds = new ArrayList<>();
        if (keywordGroupId != null) {
            groupIds.add(keywordGroupId);
        } else {
            groupIds.addAll(projectKeywordGroupRelMapper.selectList(
                    new LambdaQueryWrapper<ProjectKeywordGroupRel>()
                            .eq(ProjectKeywordGroupRel::getProjectId, project.getId())
                            .orderByAsc(ProjectKeywordGroupRel::getId)
            ).stream().map(ProjectKeywordGroupRel::getKeywordGroupId).distinct().toList());
        }
        if (groupIds.isEmpty()) {
            return List.of();
        }
        return keywordGroupResultMapper.selectList(
                new LambdaQueryWrapper<KeywordGroupResult>()
                        .in(KeywordGroupResult::getGroupId, groupIds)
                        .eq(KeywordGroupResult::getQuestionTier, "A")
                        .last("ORDER BY RAND() LIMIT 5")
        ).stream()
                .map(KeywordGroupResult::getKeywordText)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private List<String> parseCommaKeywords(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return java.util.Arrays.stream(value.replace('，', ',').split("[,、;；\\n\\r]+"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(12)
                .toList();
    }

    private String resolveBrandStatement(Project project, Brand brand) {
        if (StringUtils.hasText(project.getCustomStatement())) {
            return project.getCustomStatement().trim();
        }
        if (brand == null) {
            return null;
        }
        return buildBrandProfileStatement(brand);
    }

    private String buildBrandProfileStatement(Brand brand) {
        List<String> parts = new ArrayList<>();
        addPart(parts, "品牌定位", brand.getBrandPositioning());
        addPart(parts, "主营业务", brand.getMainBusiness());
        addPart(parts, "核心产品", brand.getCoreProducts());
        addPart(parts, "业务介绍", brand.getBusinessIntro());
        addPart(parts, "资质背书", brand.getBrandQualificationDescription());
        addPart(parts, "案例素材", brand.getBrandCaseDescription());
        return parts.isEmpty() ? null : String.join("；", parts);
    }

    private String buildTitleGuide(String channelGroupCode,
                                   int articleIndexInBatch,
                                   ArticlePromptTemplate template,
                                   Project project,
                                   Brand brand,
                                   String topic) {
        if (!ArticlePromptChannels.FORUM.equals(channelGroupCode)) {
            return null;
        }
        String safeTopic = cleanTitlePart(topic);
        if (!StringUtils.hasText(safeTopic)) {
            return null;
        }
        String timeAnchor = timeAnchor(articleIndexInBatch, safeTopic);
        String region = cleanTitlePart(resolveTitleRegion(project, brand));
        String industry = cleanTitlePart(resolveTitleIndustry(project, brand));
        String brandName = cleanTitlePart(brand == null ? project.getBrandName() : brand.getBrandName());
        boolean comparison = isComparisonForumTemplate(template);
        String brandRule = comparison
                ? "对比推荐帖可自然出现品牌名，但必须服务于语义，不要每篇都机械使用“聚焦XX”。"
                : "普通讨论帖标题默认不露出品牌名，优先在正文中自然带出。";
        String titleTags = comparison ? "[对比]、[杂谈]、[分享]、[讨论]" : "[杂谈]、[讨论]、[分享]、[避坑]";
        return """
                # 标题生成参考

                请根据下列元素自行生成文章标题，允许按语义调整顺序、删减非必要元素，使标题读起来像真实论坛用户发帖，而不是机器拼接。

                【可用标题元素】
                - 论坛标签：%s
                - 时间锚点：%s
                - 地域：%s
                - 行业：%s
                - 主题：%s
                - 品牌：%s

                【标题规则】
                1. 正文第一行必须是你生成的标题。
                2. 标题必须以一个论坛标签开头，例如“[杂谈] ”或“[讨论] ”。
                3. 时间锚点来自系统动态计算，可使用但不要强行堆叠；如果标题已很自然，可以弱化时间表达。
                4. 地域、行业、主题、品牌不必全部出现，优先保证标题顺畅、真实、有讨论感。
                5. %s
                6. 避免“服务商选择指南”“综合评估”“专业服务商”“聚焦XX”这类资讯站或官网口吻。
                7. 标题长度建议 24-42 个中文字符，不要超过 55 个中文字符。
                8. 标题需避开历史已写标题中的表达，减少重复。

                【可参考的标题语气】
                - “[杂谈] %s%s怎么选？最近看了几家，说说感受”
                - “[讨论] %s做%s，到底该看哪些细节？”
                - “[分享] %s在%s选%s，我比较关注这几个点”
                - “[避坑] %s别只看热度和价格，这几个点容易忽略”
                """.formatted(
                titleTags,
                blankToDash(timeAnchor),
                blankToDash(region),
                blankToDash(industry),
                safeTopic,
                blankToDash(brandName),
                brandRule,
                blankToEmpty(region),
                safeTopic,
                blankToEmpty(region),
                safeTopic,
                timeAnchor,
                blankToEmpty(region),
                safeTopic,
                safeTopic
        );
    }

    private boolean isComparisonForumTemplate(ArticlePromptTemplate template) {
        if (template == null || !StringUtils.hasText(template.getName())) {
            return false;
        }
        String name = template.getName();
        return name.contains("对比") || name.contains("推荐");
    }

    private String timeAnchor(int articleIndexInBatch, String topic) {
        LocalDate now = LocalDate.now(BUSINESS_ZONE);
        String year = String.valueOf(now.getYear());
        int monthValue = now.getMonthValue();
        int quarter = (monthValue + 2) / 3;
        String month = year + "年" + monthValue + "月";
        List<String> anchors = List.of(
                year + "现阶段",
                year + "年至今",
                month,
                month + "更新",
                month + "最新指南",
                month + "新消息",
                year + "年Q" + quarter,
                year + "年第" + quarterCn(quarter) + "季度"
        );
        if (topic.contains(year) || topic.matches(".*\\d{4}年\\d{1,2}月.*") || topic.matches(".*\\d{4}年Q[1-4].*")) {
            return "现阶段";
        }
        return anchors.get(Math.floorMod(Math.max(0, articleIndexInBatch - 1), anchors.size()));
    }

    private String quarterCn(int quarter) {
        return switch (quarter) {
            case 1 -> "一";
            case 2 -> "二";
            case 3 -> "三";
            case 4 -> "四";
            default -> "";
        };
    }

    private String resolveTitleRegion(Project project, Brand brand) {
        if (StringUtils.hasText(project.getDistrictName())) {
            return project.getDistrictName();
        }
        if (StringUtils.hasText(project.getCityName())) {
            return project.getCityName();
        }
        if (StringUtils.hasText(project.getProvinceName())) {
            return project.getProvinceName();
        }
        if (brand != null) {
            if (StringUtils.hasText(brand.getDistrictName())) {
                return brand.getDistrictName();
            }
            if (StringUtils.hasText(brand.getCityName())) {
                return brand.getCityName();
            }
            if (StringUtils.hasText(brand.getServiceArea())) {
                return brand.getServiceArea();
            }
        }
        return "";
    }

    private String resolveTitleIndustry(Project project, Brand brand) {
        if (brand != null && StringUtils.hasText(brand.getIndustry())) {
            return brand.getIndustry();
        }
        return project.getProjectName();
    }

    private List<String> forbiddenPhrases(Project project, Brand brand) {
        List<String> result = new ArrayList<>();
        if (brand != null) {
            result.addAll(parseJsonArray(brand.getForbiddenPhrases()));
        }
        result.addAll(parseJsonArray(project.getExtraForbiddenPhrases()));
        return result.stream().filter(StringUtils::hasText).map(String::trim).distinct().toList();
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

    private void addPart(List<String> parts, String label, String value) {
        if (StringUtils.hasText(value)) {
            parts.add(label + "：" + value.trim());
        }
    }

    private String cleanTitlePart(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim()
                .replaceAll("[\\r\\n\\t]+", "")
                .replace("，", "")
                .replace(",", "")
                .replace("。", "")
                .replace("？", "")
                .replace("?", "")
                .replace("-", "");
    }

    private String blankToDash(String value) {
        return StringUtils.hasText(value) ? value.trim() : "-";
    }

    private String blankToEmpty(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record ChannelRef(String groupCode, String subCode, String contentStyle) {
    }

    private record TemplateResolution(ArticlePromptTemplate template,
                                      ArticlePromptTemplateVersion version,
                                      boolean fallbackToDefaultPrompt) {
    }

    public record PromptContextResult(Project project,
                                      Brand brand,
                                      BatchArticlePromptBuilder.PromptBuildInput promptInput,
                                      BatchArticlePromptBuilder.PromptBuildResult prompt,
                                      List<String> forbiddenPhrases,
                                      ArticlePromptTemplate template,
                                      ArticlePromptTemplateVersion version,
                                      String channelGroupCode,
                                      String channelSubCode,
                                      String contentStyle,
                                      String topicAsQuestion,
                                      boolean fallbackToDefaultPrompt) {
    }
}
