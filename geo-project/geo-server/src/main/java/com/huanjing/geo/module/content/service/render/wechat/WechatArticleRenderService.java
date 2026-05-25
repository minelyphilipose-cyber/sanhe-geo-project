package com.huanjing.geo.module.content.service.render.wechat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.dto.render.WechatRenderDtos.ArticleBlock;
import com.huanjing.geo.module.content.dto.render.WechatRenderDtos.ArticleRenderConfigResponse;
import com.huanjing.geo.module.content.dto.render.WechatRenderDtos.ArticleRenderPreviewResponse;
import com.huanjing.geo.module.content.dto.render.WechatRenderDtos.ArticleRenderSaveRequest;
import com.huanjing.geo.module.content.dto.render.WechatRenderDtos.BodyStyle;
import com.huanjing.geo.module.content.dto.render.WechatRenderDtos.RenderAnnotations;
import com.huanjing.geo.module.content.dto.render.WechatRenderDtos.RenderInsert;
import com.huanjing.geo.module.content.dto.render.WechatRenderDtos.RenderMark;
import com.huanjing.geo.module.content.dto.render.WechatRenderDtos.RenderWarning;
import com.huanjing.geo.module.content.dto.render.WechatRenderDtos.RoleSchema;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.entity.ArticleDraftVersion;
import com.huanjing.geo.module.content.entity.ArticlePlatformRender;
import com.huanjing.geo.module.content.entity.PlatformRenderTemplateVersion;
import com.huanjing.geo.module.content.mapper.ArticleDraftVersionMapper;
import com.huanjing.geo.module.content.mapper.ArticlePlatformRenderMapper;
import com.huanjing.geo.module.content.service.render.MarkdownToHtmlRenderer;
import com.huanjing.geo.module.customer.service.BrandProfileService;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WechatArticleRenderService {
    private final ArticlePlatformRenderMapper articleRenderMapper;
    private final ArticleDraftVersionMapper articleDraftVersionMapper;
    private final ArticleMarkdownBlockParser blockParser;
    private final WechatRenderTemplateService templateService;
    private final WechatHtmlSanitizer htmlSanitizer;
    private final MarkdownToHtmlRenderer markdownToHtmlRenderer;
    private final BrandProfileService brandProfileService;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;

    private enum MaterialUrlMode {
        PREVIEW,
        PUBLIC
    }

    public String renderOrFallback(ArticleDraft article, String contentMarkdown) {
        return renderOrFallback(article, contentMarkdown, MaterialUrlMode.PREVIEW);
    }

    public String renderOrFallbackForPublish(ArticleDraft article, String contentMarkdown) {
        return renderOrFallback(article, contentMarkdown, MaterialUrlMode.PUBLIC);
    }

    private String renderOrFallback(ArticleDraft article, String contentMarkdown, MaterialUrlMode materialUrlMode) {
        ArticlePlatformRender config = currentConfig(article.getId());
        if (config == null || config.getTemplateVersionId() == null) {
            return markdownToHtmlRenderer.render(contentMarkdown);
        }
        ArticleRenderPreviewResponse response = render(article,
                contentMarkdown,
                config.getTemplateVersionId(),
                readAnnotations(config),
                readRenderConfig(config),
                materialUrlMode);
        return response.getHtml();
    }

    public ArticleRenderConfigResponse config(Long articleId) {
        currentUserService.ensurePermission("project.read");
        String content = latestContent(articleId);
        List<ArticleBlock> blocks = blockParser.parse(content, articleTitle(articleId));
        ArticlePlatformRender config = currentConfig(articleId);
        ArticleRenderConfigResponse response = new ArticleRenderConfigResponse();
        response.setArticleId(articleId);
        response.setPlatformCode(WechatRenderTemplateService.PLATFORM);
        response.setBlocks(blocks);
        if (config != null) {
            response.setTemplateId(config.getTemplateId());
            response.setTemplateVersionId(config.getTemplateVersionId());
            response.setAnnotations(readAnnotations(config));
            response.setRenderConfig(readRenderConfig(config));
            response.getWarnings().addAll(anchorWarnings(blocks, response.getAnnotations()));
        }
        return response;
    }

    @Transactional
    public ArticleRenderConfigResponse save(Long articleId, ArticleRenderSaveRequest request) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("project.update");
        PlatformRenderTemplateVersion version = templateService.requireVersion(request.getTemplateVersionId());
        validateAnnotations(blockParser.parse(latestContent(articleId), articleTitle(articleId)), request.getAnnotations());
        ArticlePlatformRender config = currentConfig(articleId);
        if (config == null) {
            config = new ArticlePlatformRender();
            config.setArticleId(articleId);
            config.setPlatformCode(WechatRenderTemplateService.PLATFORM);
            config.setStatus("active");
            config.setCreatedBy(operator.getId());
        }
        config.setTemplateId(version.getTemplateId());
        config.setTemplateVersionId(version.getId());
        config.setAnnotationsJson(toJson(request.getAnnotations() == null ? new RenderAnnotations() : request.getAnnotations()));
        config.setRenderConfigJson(toJson(request.getRenderConfig() == null ? Map.of() : request.getRenderConfig()));
        config.setBlockSnapshotJson(toJson(Map.of("blocks", blockParser.parse(latestContent(articleId), articleTitle(articleId)))));
        if (config.getId() == null) {
            articleRenderMapper.insert(config);
        } else {
            articleRenderMapper.updateById(config);
        }
        return config(articleId);
    }

    public ArticleRenderPreviewResponse preview(ArticleDraft article,
                                                Long templateVersionId,
                                                RenderAnnotations annotations,
                                                Map<String, Object> renderConfig) {
        currentUserService.ensurePermission("project.read");
        String content = latestContent(article.getId());
        if (templateVersionId == null) {
            ArticlePlatformRender config = currentConfig(article.getId());
            if (config == null || config.getTemplateVersionId() == null) {
                ArticleRenderPreviewResponse response = new ArticleRenderPreviewResponse();
                response.setHtml(markdownToHtmlRenderer.render(content));
                return response;
            }
            templateVersionId = config.getTemplateVersionId();
            annotations = annotations == null ? readAnnotations(config) : annotations;
            renderConfig = renderConfig == null ? readRenderConfig(config) : renderConfig;
        }
        return render(article,
                content,
                templateVersionId,
                annotations == null ? new RenderAnnotations() : annotations,
                renderConfig == null ? Map.of() : renderConfig,
                MaterialUrlMode.PREVIEW);
    }

    private ArticleRenderPreviewResponse render(ArticleDraft article,
                                                String contentMarkdown,
                                                Long templateVersionId,
                                                RenderAnnotations annotations,
                                                Map<String, Object> renderConfig,
                                                MaterialUrlMode materialUrlMode) {
        PlatformRenderTemplateVersion version = templateService.requireVersion(templateVersionId);
        WechatRenderTemplateService.TemplateSchema templateSchema = templateService.readSchema(version);
        Map<String, RoleSchema> roles = templateSchema.roles == null ? Map.of() : templateSchema.roles;
        BodyStyle bodyStyle = templateService.resolveBodyStyle(version, templateSchema);
        List<ArticleBlock> blocks = blockParser.parse(contentMarkdown, articleTitleForRender(article));
        applyImageOverrides(blocks, renderConfig, materialUrlMode);
        validateAnnotations(blocks, annotations);
        List<RenderWarning> warnings = anchorWarnings(blocks, annotations);
        boolean useParagraphWrapper = shouldUseParagraphWrapper(renderConfig, roles.get("paragraph"), warnings);
        Map<String, Long> blockIdCounts = blockIdCounts(blocks);
        Map<String, String> markRoles = new HashMap<>();
        Map<String, String> legacyMarkRoles = new HashMap<>();
        for (RenderMark mark : annotations.getMarks()) {
            if (StringUtils.hasText(mark.getBlockId()) && StringUtils.hasText(mark.getRole())) {
                if (mark.getOrder() != null) {
                    markRoles.put(markKey(mark.getBlockId(), mark.getOrder()), mark.getRole());
                } else if (blockIdCounts.getOrDefault(mark.getBlockId(), 0L) <= 1) {
                    legacyMarkRoles.put(mark.getBlockId(), mark.getRole());
                }
            }
        }
        Map<String, List<RenderInsert>> inserts = insertsByAnchor(annotations);
        StringBuilder html = new StringBuilder();
        int headingIndex = 0;
        String lastBlockId = null;
        for (ArticleBlock block : blocks) {
            String role = markRoles.getOrDefault(markKey(block), legacyMarkRoles.getOrDefault(block.getId(), block.getDefaultRole()));
            if ("heading".equals(role)) {
                headingIndex++;
            }
            html.append(renderBlock(article, block, role, roles, bodyStyle, useParagraphWrapper, headingIndex, warnings));
            lastBlockId = block.getId();
            appendInserts(article, html, inserts.remove(block.getId()), roles, warnings);
        }
        List<RenderInsert> lostAnchorInserts = new ArrayList<>();
        inserts.values().forEach(lostAnchorInserts::addAll);
        lostAnchorInserts.sort(Comparator.comparing(insert -> Objects.toString(insert.getRole(), "")));
        if (!lostAnchorInserts.isEmpty()) {
            warnings.add(RenderWarning.of("insert_anchor_lost", null, null, "部分插入块锚点已失效，已降级到文章末尾"));
            appendInserts(article, html, lostAnchorInserts, roles, warnings);
        }
        ArticleRenderPreviewResponse response = new ArticleRenderPreviewResponse();
        response.setHtml(htmlSanitizer.sanitizeFinalHtml(html.toString()));
        response.setWarnings(warnings);
        return response;
    }

    private String renderBlock(ArticleDraft article,
                               ArticleBlock block,
                               String role,
                               Map<String, RoleSchema> roles,
                               BodyStyle bodyStyle,
                               boolean useParagraphWrapper,
                               int headingIndex,
                               List<RenderWarning> warnings) {
        if ("native_html".equals(role)) {
            return htmlSanitizer.sanitizeNativeHtml(block.getHtml());
        }
        if ("article_title".equals(role)) {
            RoleSchema schema = roles.get("article_title");
            if (schema != null) {
                return fillWrapper(schema.getWrapperHtml(), block, 0, articleTitleForRender(article));
            }
            return defaultArticleTitleHtml(block);
        }
        RoleSchema schema = roles.get(role);
        String effectiveRole = role;
        if (schema == null) {
            schema = roles.get("paragraph");
            effectiveRole = "paragraph";
            warnings.add(RenderWarning.of("role_missing", block.getId(), role, "模板未定义该角色，已按普通段落渲染"));
        }
        if (schema == null) {
            return htmlSanitizer.sanitizeNativeHtml(block.getHtml());
        }
        if ("paragraph".equals(effectiveRole) && !useParagraphWrapper) {
            return applyBodyStyle(defaultParagraphHtml(block), bodyStyle);
        }
        String html = fillWrapper(schema.getWrapperHtml(), block, headingIndex, articleTitleForRender(article));
        return "paragraph".equals(effectiveRole) ? applyBodyStyle(html, bodyStyle) : html;
    }

    private boolean shouldUseParagraphWrapper(Map<String, Object> renderConfig,
                                              RoleSchema paragraphSchema,
                                              List<RenderWarning> warnings) {
        boolean requested = false;
        if (renderConfig != null) {
            Object raw = renderConfig.get("useParagraphWrapper");
            requested = Boolean.TRUE.equals(raw) || "true".equalsIgnoreCase(Objects.toString(raw, ""));
        }
        if (!requested) {
            return false;
        }
        if (paragraphSchema != null && Boolean.TRUE.equals(paragraphSchema.getWrapperSafe())) {
            return true;
        }
        warnings.add(RenderWarning.of(
                "paragraph_wrapper_unsafe",
                null,
                "paragraph",
                "当前模板正文外框包含装饰或未确认，已使用基础排版"
        ));
        return false;
    }

    private void appendInserts(ArticleDraft article,
                               StringBuilder html,
                               List<RenderInsert> inserts,
                               Map<String, RoleSchema> roles,
                               List<RenderWarning> warnings) {
        if (inserts == null) {
            return;
        }
        for (RenderInsert insert : inserts) {
            String role = StringUtils.hasText(insert.getRole()) ? insert.getRole() : "divider";
            RoleSchema schema = roles.get(role);
            if (schema == null) {
                schema = roles.get("paragraph");
                warnings.add(RenderWarning.of("role_missing", insert.getAfterBlockId(), role, "模板未定义插入角色，已按普通段落渲染"));
            }
            if (schema != null) {
                ArticleBlock insertBlock = new ArticleBlock();
                insertBlock.setType("insert");
                insertBlock.setText(insert.getContent());
                insertBlock.setHtml(HtmlUtils.htmlEscape(Objects.toString(insert.getContent(), "")));
                html.append(fillWrapper(schema.getWrapperHtml(), insertBlock, 0, articleTitleForRender(article)));
            }
        }
    }

    private String fillWrapper(String wrapper, ArticleBlock block, int index, String articleTitle) {
        String text = Objects.toString(block.getText(), "");
        String result = wrapper;
        result = result.replace("{{content}}", htmlContent(block));
        result = result.replace("{{text}}", escapeText(text));
        result = result.replace("{{index}}", index > 0 ? String.format("%02d", index) : "");
        result = result.replace("{{title}}", escapeText(articleTitle));
        result = result.replace("{{subtitle}}", escapeText(text));
        result = result.replace("{{caption}}", escapeText(text));
        result = result.replace("{{imageUrl}}", escapeAttribute(Objects.toString(block.getImageUrl(), "")));
        result = result.replace("{{imageAlt}}", escapeAttribute(Objects.toString(block.getImageAlt(), "")));
        return result;
    }

    private String applyBodyStyle(String html, BodyStyle bodyStyle) {
        if (bodyStyle == null || !hasAnyBodyStyle(bodyStyle) || !StringUtils.hasText(html)) {
            return html;
        }
        Document document = Jsoup.parseBodyFragment(html);
        Element outer = document.body().firstElementChild();
        if (outer == null) {
            return html;
        }
        Map<String, String> style = parseStyle(outer.attr("style"));
        putIfMissing(style, "font-size", bodyStyle.getFontSize(), "font");
        putIfMissing(style, "line-height", bodyStyle.getLineHeight(), "font");
        putIfMissing(style, "letter-spacing", bodyStyle.getLetterSpacing(), null);
        putIfMissing(style, "color", bodyStyle.getColor(), null);
        putIfMissing(style, "text-align", bodyStyle.getTextAlign(), null);
        putIfMissing(style, "margin", bodyStyle.getParagraphMargin(), "margin-bottom");
        outer.attr("style", toStyle(style));
        return document.body().html();
    }

    private boolean hasAnyBodyStyle(BodyStyle bodyStyle) {
        return StringUtils.hasText(bodyStyle.getFontSize())
                || StringUtils.hasText(bodyStyle.getLineHeight())
                || StringUtils.hasText(bodyStyle.getLetterSpacing())
                || StringUtils.hasText(bodyStyle.getColor())
                || StringUtils.hasText(bodyStyle.getTextAlign())
                || StringUtils.hasText(bodyStyle.getParagraphMargin());
    }

    private void putIfMissing(Map<String, String> style, String property, String value, String shorthandProperty) {
        if (!StringUtils.hasText(value) || style.containsKey(property)) {
            return;
        }
        if (StringUtils.hasText(shorthandProperty) && style.containsKey(shorthandProperty)) {
            return;
        }
        style.put(property, value.trim());
    }

    private Map<String, String> parseStyle(String rawStyle) {
        Map<String, String> result = new LinkedHashMap<>();
        if (!StringUtils.hasText(rawStyle)) {
            return result;
        }
        for (String declaration : rawStyle.split(";")) {
            int index = declaration.indexOf(':');
            if (index <= 0) {
                continue;
            }
            String property = declaration.substring(0, index).trim().toLowerCase(Locale.ROOT);
            String value = declaration.substring(index + 1).trim();
            if (StringUtils.hasText(property) && StringUtils.hasText(value)) {
                result.put(property, value);
            }
        }
        return result;
    }

    private String toStyle(Map<String, String> style) {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, String> entry : style.entrySet()) {
            if (!StringUtils.hasText(entry.getKey()) || !StringUtils.hasText(entry.getValue())) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(';');
            }
            result.append(entry.getKey()).append(':').append(entry.getValue());
        }
        return result.toString();
    }

    private String articleTitle(ArticleDraft article) {
        return article == null ? "" : Objects.toString(article.getTitle(), "");
    }

    private String articleTitleForRender(ArticleDraft article) {
        if (article == null || article.getId() == null) {
            return articleTitle(article);
        }
        String latestTitle = articleTitle(article.getId());
        return StringUtils.hasText(latestTitle) ? latestTitle : articleTitle(article);
    }

    private String articleTitle(Long articleId) {
        ArticleDraftVersion latest = articleDraftVersionMapper.selectOne(new LambdaQueryWrapper<ArticleDraftVersion>()
                .eq(ArticleDraftVersion::getArticleId, articleId)
                .orderByDesc(ArticleDraftVersion::getVersionNo)
                .last("LIMIT 1"));
        if (latest != null && StringUtils.hasText(latest.getTitle())) {
            return latest.getTitle();
        }
        return "";
    }

    private String escapeText(String value) {
        return HtmlUtils.htmlEscape(Objects.toString(value, ""));
    }

    private String escapeAttribute(String value) {
        return HtmlUtils.htmlEscape(Objects.toString(value, "")).replace("'", "&#39;");
    }

    private String htmlContent(ArticleBlock block) {
        String html = Objects.toString(block.getHtml(), "");
        Document document = Jsoup.parseBodyFragment(html);
        Element body = document.body();
        if (body.childrenSize() == 1 && Set.of("p", "h1", "h2", "h3", "h4", "blockquote").contains(body.child(0).tagName())) {
            return body.child(0).html();
        }
        return body.html();
    }

    private String defaultParagraphHtml(ArticleBlock block) {
        String html = Objects.toString(block.getHtml(), "");
        if (StringUtils.hasText(html)) {
            return html;
        }
        return "<p>" + escapeText(block.getText()) + "</p>";
    }

    private String defaultArticleTitleHtml(ArticleBlock block) {
        return "<h1 style=\"margin:0 0 20px;color:#111827;font-size:24px;line-height:1.35;font-weight:700;\">"
                + escapeText(block.getText())
                + "</h1>";
    }

    private void applyImageOverrides(List<ArticleBlock> blocks, Map<String, Object> renderConfig, MaterialUrlMode materialUrlMode) {
        if (blocks == null || renderConfig == null) {
            return;
        }
        Object overridesValue = renderConfig.get("imageOverrides");
        if (!(overridesValue instanceof Map<?, ?> overrides)) {
            return;
        }
        for (ArticleBlock block : blocks) {
            if (!StringUtils.hasText(block.getImageUrl())) {
                continue;
            }
            Object raw = overrides.get(blockKey(block));
            if (raw == null) {
                raw = overrides.get(block.getId());
            }
            if (raw instanceof String url && StringUtils.hasText(url)) {
                block.setImageUrl(url.trim());
            } else if (raw instanceof Map<?, ?> map) {
                Object imageUrl = map.get("imageUrl");
                Object imageAlt = map.get("imageAlt");
                String materialUrl = materialUrl(map, materialUrlMode);
                if (StringUtils.hasText(materialUrl)) {
                    block.setImageUrl(materialUrl);
                } else if (imageUrl instanceof String url && StringUtils.hasText(url)) {
                    block.setImageUrl(url.trim());
                }
                if (imageAlt instanceof String alt) {
                    block.setImageAlt(alt);
                }
            }
        }
    }

    private String materialUrl(Map<?, ?> map, MaterialUrlMode materialUrlMode) {
        Long brandId = asLong(map.get("brandId"));
        Long materialId = asLong(map.get("materialId"));
        if (brandId == null || materialId == null) {
            return null;
        }
        try {
            if (materialUrlMode == MaterialUrlMode.PUBLIC) {
                return brandProfileService.buildMaterialPublicUrl(brandId, materialId);
            }
            return brandProfileService.buildMaterialPreviewUrl(brandId, materialId);
        } catch (BizException ex) {
            return null;
        }
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String blockKey(ArticleBlock block) {
        return block.getId() + "#" + block.getOrder();
    }

    private Map<String, List<RenderInsert>> insertsByAnchor(RenderAnnotations annotations) {
        Map<String, List<RenderInsert>> result = new LinkedHashMap<>();
        for (RenderInsert insert : annotations.getInserts()) {
            if (!StringUtils.hasText(insert.getRole())) {
                continue;
            }
            result.computeIfAbsent(Objects.toString(insert.getAfterBlockId(), ""), ignored -> new ArrayList<>()).add(insert);
        }
        return result;
    }

    private void validateAnnotations(List<ArticleBlock> blocks, RenderAnnotations annotations) {
        if (annotations == null) {
            return;
        }
        Map<String, ArticleBlock> blockMap = new HashMap<>();
        Map<String, ArticleBlock> blockIdMap = new HashMap<>();
        Map<String, Long> blockIdCounts = blockIdCounts(blocks);
        for (ArticleBlock block : blocks) {
            blockMap.put(markKey(block), block);
            blockIdMap.put(block.getId(), block);
        }
        for (RenderMark mark : annotations.getMarks()) {
            ArticleBlock block = mark.getOrder() == null
                    ? (blockIdCounts.getOrDefault(mark.getBlockId(), 0L) <= 1 ? blockIdMap.get(mark.getBlockId()) : null)
                    : blockMap.get(markKey(mark.getBlockId(), mark.getOrder()));
            if (block != null && !block.getAllowedRoles().contains(mark.getRole())) {
                throw new BizException(400, "该段落类型不允许应用角色: " + mark.getRole());
            }
        }
    }

    private List<RenderWarning> anchorWarnings(List<ArticleBlock> blocks, RenderAnnotations annotations) {
        List<RenderWarning> warnings = new ArrayList<>();
        if (annotations == null) {
            return warnings;
        }
        Map<String, ArticleBlock> blockMap = new HashMap<>();
        Map<String, ArticleBlock> blockIdMap = new HashMap<>();
        Map<String, Long> blockIdCounts = blockIdCounts(blocks);
        for (ArticleBlock block : blocks) {
            blockMap.put(markKey(block), block);
            blockIdMap.put(block.getId(), block);
        }
        blockIdCounts.forEach((blockId, count) -> {
            if (count > 1) {
                warnings.add(RenderWarning.of("duplicate_block_id", blockId, null, "文章存在重复段落，标注将按段落序号精确匹配"));
            }
        });
        for (RenderMark mark : annotations.getMarks()) {
            if (mark.getOrder() == null && blockIdCounts.getOrDefault(mark.getBlockId(), 0L) > 1) {
                warnings.add(RenderWarning.of("mark_order_missing", mark.getBlockId(), mark.getRole(), "该标注缺少段落序号且命中重复段落，请重新确认"));
                continue;
            }
            boolean matched = mark.getOrder() == null
                    ? blockIdMap.containsKey(mark.getBlockId())
                    : blockMap.containsKey(markKey(mark.getBlockId(), mark.getOrder()));
            if (!matched) {
                warnings.add(RenderWarning.of("mark_anchor_lost", mark.getBlockId(), mark.getRole(), "标注对应段落已变化，请重新确认"));
            }
        }
        return warnings;
    }

    private Map<String, Long> blockIdCounts(List<ArticleBlock> blocks) {
        Map<String, Long> counts = new HashMap<>();
        for (ArticleBlock block : blocks) {
            counts.merge(block.getId(), 1L, Long::sum);
        }
        return counts;
    }

    private String markKey(ArticleBlock block) {
        return markKey(block.getId(), block.getOrder());
    }

    private String markKey(String blockId, Integer order) {
        return blockId + "#" + order;
    }

    private ArticlePlatformRender currentConfig(Long articleId) {
        return articleRenderMapper.selectOne(new LambdaQueryWrapper<ArticlePlatformRender>()
                .eq(ArticlePlatformRender::getArticleId, articleId)
                .eq(ArticlePlatformRender::getPlatformCode, WechatRenderTemplateService.PLATFORM)
                .last("LIMIT 1"));
    }

    private RenderAnnotations readAnnotations(ArticlePlatformRender config) {
        if (config == null || !StringUtils.hasText(config.getAnnotationsJson())) {
            return new RenderAnnotations();
        }
        try {
            return objectMapper.readValue(config.getAnnotationsJson(), RenderAnnotations.class);
        } catch (Exception ex) {
            return new RenderAnnotations();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readRenderConfig(ArticlePlatformRender config) {
        if (config == null || !StringUtils.hasText(config.getRenderConfigJson())) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(config.getRenderConfigJson(), LinkedHashMap.class);
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }

    private String latestContent(Long articleId) {
        ArticleDraftVersion latest = articleDraftVersionMapper.selectOne(new LambdaQueryWrapper<ArticleDraftVersion>()
                .eq(ArticleDraftVersion::getArticleId, articleId)
                .orderByDesc(ArticleDraftVersion::getVersionNo)
                .last("LIMIT 1"));
        if (latest == null || !StringUtils.hasText(latest.getContentMarkdown())) {
            throw new BizException(400, "Article content is empty");
        }
        return latest.getContentMarkdown();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BizException(500, "公众号渲染配置保存失败");
        }
    }
}
