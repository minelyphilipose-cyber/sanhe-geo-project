package com.huanjing.geo.module.content.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.mapper.ArticleDraftMapper;
import com.huanjing.geo.module.content.mapper.ArticleGenerationLogMapper;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.service.BrandStatementService;
import com.huanjing.geo.module.project.entity.KeywordGroupResult;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.entity.ProjectKeywordGroupRel;
import com.huanjing.geo.module.project.mapper.KeywordGroupResultMapper;
import com.huanjing.geo.module.project.mapper.ProjectKeywordGroupRelMapper;
import com.huanjing.geo.module.system.entity.SysDictItem;
import com.huanjing.geo.module.system.mapper.SysDictItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GeoPromptBuilder {

    private static final List<String> DEFAULT_ANGLES = List.of(
            "行业趋势分析", "选择指南", "避坑指南", "费用解析", "对比分析",
            "流程科普", "案例解读", "常见问答", "清单建议", "地域特色"
    );
    private static final List<String> BRAND_PLACEHOLDERS = List.of(
            "测试品牌", "品牌名称", "xxx", "xxxx", "测试", "待定", "brand", "brandname"
    );

    private final BrandStatementService brandStatementService;
    private final KeywordGroupResultMapper keywordGroupResultMapper;
    private final ProjectKeywordGroupRelMapper projectKeywordGroupRelMapper;
    private final ArticleGenerationLogMapper articleGenerationLogMapper;
    private final ArticleDraftMapper articleDraftMapper;
    private final SysDictItemMapper sysDictItemMapper;

    public PromptPair buildContentPrompt(Project project, Brand brand, String articleType, int articleIndex) {
        KeywordSelection keywords = resolveKeywords(project.getId(), articleIndex);
        String angle = resolveArticleAngle(project, articleIndex);
        return new PromptPair(buildSystemPrompt(), buildUserPrompt(project, brand, articleType, keywords, angle));
    }

    public void ensureHasSavedKeywords(Long projectId) {
        List<Long> groupIds = projectKeywordGroupRelMapper.selectList(
                new LambdaQueryWrapper<ProjectKeywordGroupRel>()
                        .eq(ProjectKeywordGroupRel::getProjectId, projectId)
                        .orderByAsc(ProjectKeywordGroupRel::getId)
        ).stream().map(ProjectKeywordGroupRel::getKeywordGroupId).distinct().toList();
        if (groupIds.isEmpty()) {
            throw new BizException(400, "项目无已入库关键词");
        }
        Long count = keywordGroupResultMapper.selectCount(
                new LambdaQueryWrapper<KeywordGroupResult>()
                        .in(KeywordGroupResult::getGroupId, groupIds)
        );
        if (count == null || count <= 0) {
            throw new BizException(400, "项目无已入库关键词");
        }
    }

    public String resolveArticleAngle(Project project, int articleIndex) {
        List<String> angles = parseJsonArray(project.getPreferredAngles());
        if (angles.isEmpty()) {
            angles = DEFAULT_ANGLES;
        }
        int recentLimit = Math.max(1, angles.size() / 2);
        List<String> recentAngles = articleGenerationLogMapper.selectRecentAngles(project.getId(), recentLimit).stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        List<String> available = angles.stream().filter(angle -> !recentAngles.contains(angle)).toList();
        List<String> finalAngles = available.isEmpty() ? angles : available;
        return finalAngles.get(Math.floorMod(articleIndex, finalAngles.size()));
    }

    private String buildSystemPrompt() {
        return """
                你是一位资深的中文内容营销专家，负责产出适合 GEO 场景使用的高质量文章。

                【写作质量标准】
                - 信息密度高，每段都要有明确观点或有效信息，不写空话套话
                - 语言自然克制，像行业内行在认真解释，不要像广告软文
                - 论述要具体，尽量给出定义、流程、对比、清单、问答等可引用结构
                - 文章结构清晰，只使用 Markdown 一级和二级标题

                【GEO 优化规则】
                - 标题必须自然包含本篇问题词
                - 开头前 100 字内自然出现本篇问题词
                - 全文只围绕本篇问题词展开，不要同时覆盖其他问题词
                - 品牌名全篇出现 3 到 5 次，分散在开头、中段和结尾
                - 关键词使用保持自然，不堆砌，不做机械重复
                - 不要使用“本文”“本公司”“我们公司”等自指表达，优先使用品牌名或第三人称
                - 文章中只允许出现品牌信息中给定的品牌名称，严禁自行编造、虚构任何品牌名、公司名或产品名
                - 如果品牌信息不充分，用“该品牌”“该企业”等通用指代替代，绝不捏造具体名称

                【可引用性要求】
                - 文中至少包含定义解释、结构化列表、对比分析、常见问答、步骤流程中的若干种
                - 输出必须是完整可发布文章，而不是提纲或说明

                【输出格式】
                - 仅输出 Markdown 正文
                - 直接以文章标题开头
                - 不输出写作说明、提示词解释或额外备注
                """;
    }

    private String buildUserPrompt(Project project, Brand brand, String articleType, KeywordSelection keywords, String angle) {
        StringBuilder sb = new StringBuilder();
        sb.append("【品牌信息】\n");
        String brandName = resolveUsableBrandName(brand);
        if (brand != null) {
            appendLine(sb, "品牌名称", brandName);
            appendLine(sb, "所属行业", brand.getIndustry());
            appendLine(sb, "主营业务", brand.getMainBusiness());
            appendLine(sb, "品牌简介", brand.getDescription());
            appendLine(sb, "对外公开电话", brand.getPublicPhone());
            appendLine(sb, "对外公开地址", brand.getPublicAddress());
            appendLine(sb, "品牌表述", resolveBrandStatement(project, brand));
        } else {
            appendLine(sb, "项目名称", project.getProjectName());
        }
        if (brand != null && !StringUtils.hasText(brandName)) {
            sb.append("本次未提供具体品牌名称，请使用通用行业表述，不要编造品牌名。\n");
        }

        sb.append("\n【项目策略】\n");
        List<String> regions = parseJsonArray(project.getTargetRegions());
        if (!regions.isEmpty()) {
            sb.append("目标区域：").append(String.join("、", regions)).append("\n");
        }
        appendLine(sb, "目标受众", project.getTargetAudience());
        appendLine(sb, "内容调性", project.getContentTone());
        appendLine(sb, "补充说明", project.getContentNote());

        sb.append("\n【问题词】\n");
        sb.append("本篇问题词：").append(keywords.primary()).append("\n");
        sb.append("请仅围绕该问题词生成文章，不要扩展为多个问题词合集。\n");

        sb.append("\n【写作任务】\n");
        sb.append("文章类型：").append(resolveArticleTypeLabel(articleType)).append("\n");
        sb.append("写作角度：").append(angle).append("\n");
        sb.append("字数要求：1200-1800字\n");
        sb.append("结构要求：").append(resolveStructureRule(articleType)).append("\n");

        List<String> historyTitles = resolveHistoryTitles(project.getId(), 10);
        if (!historyTitles.isEmpty()) {
            sb.append("\n【差异化要求】\n");
            sb.append("以下为该项目最近30天已有文章标题，本次标题和选题需明显区分：\n");
            for (String title : historyTitles) {
                sb.append("- ").append(title).append("\n");
            }
        }

        List<String> forbidden = resolveForbiddenPhrases(project, brand);
        if (!forbidden.isEmpty()) {
            sb.append("\n【合规要求】\n");
            sb.append("禁止使用以下词语：").append(String.join("、", forbidden)).append("\n");
        }

        sb.append("\n现在请直接输出完整文章。\n");
        return sb.toString();
    }

    private KeywordSelection resolveKeywords(Long projectId, int articleIndex) {
        List<Long> groupIds = projectKeywordGroupRelMapper.selectList(
                new LambdaQueryWrapper<ProjectKeywordGroupRel>()
                        .eq(ProjectKeywordGroupRel::getProjectId, projectId)
                        .orderByAsc(ProjectKeywordGroupRel::getId)
        ).stream().map(ProjectKeywordGroupRel::getKeywordGroupId).distinct().toList();
        if (groupIds.isEmpty()) {
            throw new BizException(400, "项目无已入库关键词");
        }
        List<String> allKeywords = keywordGroupResultMapper.selectList(
                new LambdaQueryWrapper<KeywordGroupResult>()
                        .in(KeywordGroupResult::getGroupId, groupIds)
                        .orderByAsc(KeywordGroupResult::getGroupId, KeywordGroupResult::getSortOrder, KeywordGroupResult::getId)
        ).stream()
                .map(KeywordGroupResult::getKeywordText)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (allKeywords.isEmpty()) {
            throw new BizException(400, "项目无已入库关键词");
        }
        int primaryIdx = Math.floorMod(articleIndex, allKeywords.size());
        String primary = allKeywords.get(primaryIdx);
        return new KeywordSelection(primary);
    }

    private List<String> resolveForbiddenPhrases(Project project, Brand brand) {
        Set<String> words = new LinkedHashSet<>();
        if (brand != null) {
            words.addAll(parseJsonArray(brand.getForbiddenPhrases()));
        }
        words.addAll(parseJsonArray(project.getExtraForbiddenPhrases()));
        List<SysDictItem> globals = sysDictItemMapper.selectList(
                new LambdaQueryWrapper<SysDictItem>()
                        .eq(SysDictItem::getDictType, "global_forbidden_phrase")
                        .eq(SysDictItem::getEnabled, true)
                        .orderByAsc(SysDictItem::getSortOrder, SysDictItem::getId)
        );
        for (SysDictItem item : globals) {
            if (StringUtils.hasText(item.getDictKey())) {
                words.add(item.getDictKey().trim());
            }
        }
        return words.stream().map(String::trim).filter(StringUtils::hasText).distinct().toList();
    }

    private List<String> resolveHistoryTitles(Long projectId, int limit) {
        return articleDraftMapper.selectList(
                new LambdaQueryWrapper<ArticleDraft>()
                        .select(ArticleDraft::getTitle, ArticleDraft::getCreatedAt)
                        .eq(ArticleDraft::getProjectId, projectId)
                        .ge(ArticleDraft::getCreatedAt, LocalDateTime.now().minusDays(30))
                        .orderByDesc(ArticleDraft::getCreatedAt)
                        .last("LIMIT " + Math.max(1, limit))
        ).stream()
                .map(ArticleDraft::getTitle)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
    }

    private String resolveBrandStatement(Project project, Brand brand) {
        String raw = StringUtils.hasText(project.getCustomStatement())
                ? project.getCustomStatement().trim()
                : brandStatementService.resolvePromptStatement(brand);
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            JSONObject json = JSONUtil.parseObj(raw);
            String paragraph = json.getStr("brand_paragraph");
            if (StringUtils.hasText(paragraph)) {
                return paragraph.trim();
            }
        } catch (Exception ignored) {
        }
        return raw.trim();
    }

    private String resolveUsableBrandName(Brand brand) {
        if (brand == null || !StringUtils.hasText(brand.getBrandName())) {
            return null;
        }
        String value = brand.getBrandName().trim();
        String normalized = value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        if (BRAND_PLACEHOLDERS.contains(normalized)) {
            return null;
        }
        return value;
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
            return result.stream().distinct().toList();
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String resolveArticleTypeLabel(String articleType) {
        return switch (StringUtils.hasText(articleType) ? articleType.trim().toLowerCase(Locale.ROOT) : "") {
            case "faq" -> "问答文章";
            case "scenario_content" -> "场景内容文";
            case "industry_article" -> "行业分析文";
            case "stage_advice" -> "阶段建议文";
            default -> StringUtils.hasText(articleType) ? articleType.trim() : "通用文章";
        };
    }

    private String resolveStructureRule(String articleType) {
        return switch (StringUtils.hasText(articleType) ? articleType.trim().toLowerCase(Locale.ROOT) : "") {
            case "faq" -> "围绕本篇问题词设计 3-5 组问答，答案需具体且可执行";
            case "scenario_content" -> "按场景痛点、解决方案、品牌建议、行动建议展开";
            case "industry_article" -> "按背景、分析、对比、建议的顺序展开";
            case "stage_advice" -> "按阶段现状、关键问题、优化建议、执行步骤展开";
            default -> "围绕本篇问题词组织完整文章结构，包含分析与建议";
        };
    }

    private void appendLine(StringBuilder sb, String label, String value) {
        if (StringUtils.hasText(value)) {
            sb.append(label).append("：").append(value.trim()).append("\n");
        }
    }

    private record KeywordSelection(String primary) {
    }

    public record PromptPair(String systemPrompt, String userPrompt) {
    }
}
