package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.content.dto.ArticleGenerationOptionDtos.AllocationItemVO;
import com.huanjing.geo.module.content.dto.ArticleGenerationOptionDtos.AllocationPreviewResponse;
import com.huanjing.geo.module.content.dto.ArticleGenerationOptionDtos.ChannelGroupVO;
import com.huanjing.geo.module.content.dto.ArticleGenerationOptionDtos.ChannelOptionVO;
import com.huanjing.geo.module.content.dto.ArticleGenerationOptionDtos.GenerationOptionsVO;
import com.huanjing.geo.module.content.dto.ArticleGenerationOptionDtos.TemplateOptionVO;
import com.huanjing.geo.module.content.entity.ArticlePromptTemplate;
import com.huanjing.geo.module.content.entity.ArticlePromptTemplateVersion;
import com.huanjing.geo.module.content.mapper.ArticlePromptTemplateMapper;
import com.huanjing.geo.module.content.mapper.ArticlePromptTemplateVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ArticleTemplateAllocationService {

    private final ArticlePromptTemplateMapper templateMapper;
    private final ArticlePromptTemplateVersionMapper versionMapper;

    public GenerationOptionsVO options() {
        List<ChannelGroupVO> groups = new ArrayList<>();
        groups.add(new ChannelGroupVO(ArticlePromptChannels.AGENT_SITE, "官网平台",
                "用于 Agent 官网发布，发布时根据模板元数据进入 FAQ / 知识库 / 产品服务模块。",
                List.of(channelOption(ArticlePromptChannels.AGENT_SITE, null, "Agent 官网", "官网文章风格，适合品牌自有 GEO 站点发布"))));
        groups.add(new ChannelGroupVO(ArticlePromptChannels.INDUSTRY_SITE, "行业资讯站",
                "统一按行业资讯口吻生成，发布目标由品牌绑定站点决定。",
                List.of(channelOption(ArticlePromptChannels.INDUSTRY_SITE, null, "行业资讯站", "行业资讯站稿件，客观中立、可检索、可引用"))));
        groups.add(new ChannelGroupVO(ArticlePromptChannels.SELF_MEDIA, "自媒体平台",
                "不同平台独立配置模板，按平台阅读习惯生成内容。",
                channelSubCodes(ArticlePromptChannels.SELF_MEDIA).stream()
                        .map(sub -> channelOption(ArticlePromptChannels.SELF_MEDIA, sub,
                                ArticlePromptChannels.SUB_LABELS.getOrDefault(sub, sub), selfMediaDesc(sub)))
                        .toList()));
        groups.add(new ChannelGroupVO(ArticlePromptChannels.AUTHORITY_MEDIA, "权威媒体",
                "按媒体类型配置模板，强调事实边界和正式表达。",
                channelSubCodes(ArticlePromptChannels.AUTHORITY_MEDIA).stream()
                        .map(sub -> channelOption(ArticlePromptChannels.AUTHORITY_MEDIA, sub,
                                ArticlePromptChannels.SUB_LABELS.getOrDefault(sub, sub), "正式审慎，事实边界清晰"))
                        .toList()));
        groups.add(new ChannelGroupVO(ArticlePromptChannels.FORUM, "平台网站",
                "统一按平台网站的社区讨论、经验分享、避坑帖风格生成。",
                List.of(channelOption(ArticlePromptChannels.FORUM, null, "平台网站", "平台网站讨论感"))));
        groups.addAll(customGroups());
        return new GenerationOptionsVO(groups);
    }

    public AllocationPreviewResponse preview(String groupCode, String subCode, int count) {
        return preview(groupCode, subCode, null, count);
    }

    public AllocationPreviewResponse preview(String groupCode, String subCode, String questionSceneCode, int count) {
        List<AllocatedTemplate> allocated = allocate(groupCode, subCode, questionSceneCode, count);
        return new AllocationPreviewResponse(
                groupCode,
                trimToNull(subCode),
                allocated.stream().mapToInt(AllocatedTemplate::count).sum(),
                allocated.stream().map(this::toAllocationItem).toList()
        );
    }

    public List<AllocatedTemplate> allocate(String groupCode, String subCode, int count) {
        return allocate(groupCode, subCode, null, count);
    }

    public List<AllocatedTemplate> allocate(String groupCode, String subCode, String questionSceneCode, int count) {
        if (count <= 0) {
            return List.of();
        }
        List<TemplateWithVersion> candidates = activeTemplates(groupCode, subCode, questionSceneCode).stream()
                .filter(item -> item.template().getWeight() != null && item.template().getWeight() > 0)
                .toList();
        return allocateCandidates(candidates, count);
    }

    public List<AllocatedTemplate> allocateCandidates(List<TemplateWithVersion> candidates, int count) {
        if (count <= 0 || candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        candidates = candidates.stream()
                .filter(item -> item.template().getWeight() != null && item.template().getWeight() > 0)
                .toList();
        if (candidates.isEmpty()) {
            return List.of();
        }
        int totalWeight = candidates.stream().mapToInt(item -> item.template().getWeight()).sum();
        List<AllocationRow> rows = new ArrayList<>();
        int used = 0;
        for (int i = 0; i < candidates.size(); i++) {
            TemplateWithVersion item = candidates.get(i);
            BigDecimal exact = BigDecimal.valueOf(count)
                    .multiply(BigDecimal.valueOf(item.template().getWeight()))
                    .divide(BigDecimal.valueOf(totalWeight), 8, RoundingMode.HALF_UP);
            int base = exact.setScale(0, RoundingMode.DOWN).intValue();
            BigDecimal remainder = exact.subtract(BigDecimal.valueOf(base));
            rows.add(new AllocationRow(item, base, remainder, i));
            used += base;
        }
        int remaining = count - used;
        rows.stream()
                .sorted(Comparator
                        .comparing(AllocationRow::remainder).reversed()
                        .thenComparing(row -> row.item().template().getWeight(), Comparator.reverseOrder())
                        .thenComparing(row -> row.item().template().getUpdatedAt(), Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(row -> row.item().template().getId(), Comparator.reverseOrder()))
                .limit(Math.max(0, remaining))
                .forEach(row -> row.count += 1);
        return rows.stream()
                .filter(row -> row.count() > 0)
                .sorted(Comparator.comparingInt(AllocationRow::originalIndex))
                .map(row -> new AllocatedTemplate(row.item().template(), row.item().version(), row.count()))
                .toList();
    }

    public List<TemplateWithVersion> activeTemplates(String groupCode, String subCode) {
        return activeTemplates(groupCode, subCode, null);
    }

    public List<TemplateWithVersion> activeTemplates(String groupCode, String subCode, String questionSceneCode) {
        List<ArticlePromptTemplate> templates = templateMapper.selectList(
                new LambdaQueryWrapper<ArticlePromptTemplate>()
                        .eq(ArticlePromptTemplate::getChannelGroupCode, groupCode)
                        .eq(StringUtils.hasText(subCode), ArticlePromptTemplate::getChannelSubCode, trimToNull(subCode))
                        .isNull(!StringUtils.hasText(subCode), ArticlePromptTemplate::getChannelSubCode)
                        .eq(ArticlePromptTemplate::getStatus, ArticlePromptTemplateService.STATUS_ACTIVE)
                        .isNotNull(ArticlePromptTemplate::getCurrentVersionId)
                        .orderByDesc(ArticlePromptTemplate::getUpdatedAt, ArticlePromptTemplate::getId)
        );
        List<TemplateWithVersion> result = new ArrayList<>();
        for (ArticlePromptTemplate template : templates) {
            ArticlePromptTemplateVersion version = versionMapper.selectById(template.getCurrentVersionId());
            if (version != null && ArticlePromptTemplateService.VERSION_PUBLISHED.equals(version.getStatus())) {
                result.add(new TemplateWithVersion(template, version));
            }
        }
        return filterByQuestionScene(result, questionSceneCode);
    }

    public TemplateWithVersion resolveTemplate(Long templateId, Long versionId) {
        ArticlePromptTemplate template = templateMapper.selectById(templateId);
        ArticlePromptTemplateVersion version = versionId == null ? null : versionMapper.selectById(versionId);
        if (template == null || version == null || !template.getId().equals(version.getTemplateId())) {
            return null;
        }
        if (!ArticlePromptTemplateService.STATUS_ACTIVE.equals(template.getStatus())
                || !ArticlePromptTemplateService.VERSION_PUBLISHED.equals(version.getStatus())
                || !version.getId().equals(template.getCurrentVersionId())) {
            return null;
        }
        return new TemplateWithVersion(template, version);
    }

    public Map<String, List<TemplateWithVersion>> activeTemplateMap() {
        Map<String, List<TemplateWithVersion>> map = new LinkedHashMap<>();
        for (ChannelGroupVO group : options().groups()) {
            for (ChannelOptionVO channel : group.channels()) {
                map.put(key(channel.channelGroupCode(), channel.channelSubCode()),
                        activeTemplates(channel.channelGroupCode(), channel.channelSubCode()));
            }
        }
        return map;
    }

    public static String key(String groupCode, String subCode) {
        return groupCode + ":" + (StringUtils.hasText(subCode) ? subCode.trim() : "");
    }

    public record TemplateWithVersion(ArticlePromptTemplate template, ArticlePromptTemplateVersion version) {
    }

    public record AllocatedTemplate(ArticlePromptTemplate template, ArticlePromptTemplateVersion version, int count) {
    }

    private record TemplateChannel(String groupCode, String subCode) {
    }

    private ChannelOptionVO channelOption(String groupCode, String subCode, String label, String description) {
        List<TemplateOptionVO> templates = activeTemplates(groupCode, subCode).stream()
                .map(this::toTemplateOption)
                .toList();
        return new ChannelOptionVO(
                groupCode,
                ArticlePromptChannels.GROUP_LABELS.getOrDefault(groupCode, groupCode),
                subCode,
                subCode == null ? null : ArticlePromptChannels.SUB_LABELS.getOrDefault(subCode, subCode),
                label,
                description,
                ArticlePromptChannels.contentStyle(groupCode, subCode),
                !templates.isEmpty(),
                templates.isEmpty() ? "未配置启用模板" : null,
                templates.size(),
                templates
        );
    }

    private List<String> channelSubCodes(String groupCode) {
        Set<String> codes = new LinkedHashSet<>(ArticlePromptChannels.subCodes(groupCode));
        templateChannels().stream()
                .filter(channel -> groupCode.equals(channel.groupCode()))
                .map(TemplateChannel::subCode)
                .filter(StringUtils::hasText)
                .forEach(codes::add);
        return new ArrayList<>(codes);
    }

    private List<ChannelGroupVO> customGroups() {
        Map<String, Set<String>> groupMap = new LinkedHashMap<>();
        for (TemplateChannel channel : templateChannels()) {
            if (ArticlePromptChannels.GROUPS.contains(channel.groupCode())) {
                continue;
            }
            groupMap.computeIfAbsent(channel.groupCode(), ignored -> new LinkedHashSet<>())
                    .add(channel.subCode() == null ? "" : channel.subCode());
        }
        List<ChannelGroupVO> groups = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : groupMap.entrySet()) {
            String groupCode = entry.getKey();
            List<ChannelOptionVO> channels = entry.getValue().stream()
                    .map(this::trimToNull)
                    .map(sub -> channelOption(groupCode, sub, sub == null ? groupCode : sub, "自定义分发渠道"))
                    .toList();
            groups.add(new ChannelGroupVO(groupCode, groupCode, "自定义分发渠道", channels));
        }
        return groups;
    }

    private List<TemplateChannel> templateChannels() {
        List<ArticlePromptTemplate> templates = templateMapper.selectList(
                new LambdaQueryWrapper<ArticlePromptTemplate>()
                        .eq(ArticlePromptTemplate::getStatus, ArticlePromptTemplateService.STATUS_ACTIVE)
                        .isNotNull(ArticlePromptTemplate::getCurrentVersionId)
                        .orderByDesc(ArticlePromptTemplate::getUpdatedAt, ArticlePromptTemplate::getId)
        );
        Map<String, TemplateChannel> channels = new LinkedHashMap<>();
        for (ArticlePromptTemplate template : templates) {
            String group = trimToNull(template.getChannelGroupCode());
            if (!StringUtils.hasText(group)) {
                continue;
            }
            String sub = trimToNull(template.getChannelSubCode());
            channels.putIfAbsent(key(group, sub), new TemplateChannel(group, sub));
        }
        return new ArrayList<>(channels.values());
    }

    private TemplateOptionVO toTemplateOption(TemplateWithVersion item) {
        ArticlePromptTemplate template = item.template();
        return new TemplateOptionVO(
                template.getId(),
                item.version().getId(),
                template.getName(),
                template.getChannelGroupCode(),
                template.getChannelSubCode(),
                template.getAgentSiteModule(),
                template.getArticleTypeCode(),
                ArticlePromptChannels.ARTICLE_TYPE_LABELS.getOrDefault(template.getArticleTypeCode(), template.getArticleTypeCode()),
                template.getQuestionSceneCode(),
                questionSceneLabel(template.getQuestionSceneCode()),
                template.getWeight(),
                template.getSortOrder()
        );
    }

    private List<TemplateWithVersion> filterByQuestionScene(List<TemplateWithVersion> templates, String questionSceneCode) {
        String scene = trimToNull(questionSceneCode);
        if (!StringUtils.hasText(scene)) {
            return templates;
        }
        List<TemplateWithVersion> matched = templates.stream()
                .filter(item -> scene.equals(trimToNull(item.template().getQuestionSceneCode())))
                .toList();
        if (!matched.isEmpty()) {
            return matched;
        }
        return templates.stream()
                .filter(item -> !StringUtils.hasText(item.template().getQuestionSceneCode()))
                .toList();
    }

    private AllocationItemVO toAllocationItem(AllocatedTemplate item) {
        ArticlePromptTemplate template = item.template();
        return new AllocationItemVO(
                template.getId(),
                item.version().getId(),
                template.getName(),
                template.getArticleTypeCode(),
                ArticlePromptChannels.ARTICLE_TYPE_LABELS.getOrDefault(template.getArticleTypeCode(), template.getArticleTypeCode()),
                template.getQuestionSceneCode(),
                questionSceneLabel(template.getQuestionSceneCode()),
                template.getAgentSiteModule(),
                template.getWeight(),
                item.count()
        );
    }

    private String selfMediaDesc(String sub) {
        return switch (sub) {
            case "toutiao" -> "泛资讯阅读，结论前置";
            case "wechat" -> "完整长文，结构稳";
            case "zhihu" -> "问题回答，判断清晰";
            case "douyin_image_text" -> "图文卡片式阅读";
            case "baijiahao" -> "搜索收录友好，信息密度高";
            case "netease" -> "门户资讯阅读，媒体感强";
            default -> "自媒体内容风格";
        };
    }

    private String questionSceneLabel(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return ArticlePromptTemplateService.QUESTION_SCENE_LABELS.getOrDefault(value, value);
    }

    private int normalize(Integer value) {
        return value == null ? 0 : value;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static final class AllocationRow {
        private final TemplateWithVersion item;
        private int count;
        private final BigDecimal remainder;
        private final int originalIndex;

        private AllocationRow(TemplateWithVersion item, int count, BigDecimal remainder, int originalIndex) {
            this.item = item;
            this.count = count;
            this.remainder = remainder;
            this.originalIndex = originalIndex;
        }

        private TemplateWithVersion item() {
            return item;
        }

        private int count() {
            return count;
        }

        private BigDecimal remainder() {
            return remainder;
        }

        private int originalIndex() {
            return originalIndex;
        }
    }
}
