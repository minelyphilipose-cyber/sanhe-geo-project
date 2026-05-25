package com.huanjing.geo.module.content.service.render.wechat;

import com.huanjing.geo.module.content.dto.render.WechatRenderDtos.RenderWarning;
import com.huanjing.geo.module.content.dto.render.WechatRenderDtos.BodyStyle;
import com.huanjing.geo.module.content.dto.render.WechatRenderDtos.RoleSchema;
import com.huanjing.geo.module.content.dto.render.WechatRenderDtos.TemplateParseResponse;
import com.huanjing.geo.module.content.dto.render.WechatRenderDtos.TemplateRoleDraft;
import com.huanjing.geo.module.content.dto.render.WechatRenderDtos.TemplateSlice;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WechatTemplateImportService {
    private static final String SOURCE_GENERIC = "generic";
    private static final String SOURCE_135 = "source_135";
    private static final Set<String> SUPPORTED_SOURCE_TYPES = Set.of(SOURCE_GENERIC, SOURCE_135);
    private static final Set<String> SAFE_WRAPPER_TAGS = Set.of("section", "p", "span", "br", "strong", "em", "b", "i");

    private final WechatHtmlSanitizer htmlSanitizer;

    public TemplateParseResponse parse(String sourceHtml, String requestedSourceType) {
        String sourceType = normalizeSourceType(sourceHtml, requestedSourceType);
        TemplateParseResponse response = new TemplateParseResponse();
        response.setSourceType(sourceType);
        response.setBodyStyle(defaultBodyStyle(sourceType));
        List<Element> slices = SOURCE_135.equals(sourceType) ? flatten135(sourceHtml) : flattenGeneric(sourceHtml);
        applyParagraphBodyStyleOverrides(response.getBodyStyle(), slices);
        Map<String, List<TemplateSlice>> byRole = new LinkedHashMap<>();
        int order = 0;
        for (Element sliceElement : slices) {
            if (isBlankSlice(sliceElement)) {
                continue;
            }
            TemplateSlice slice = toSlice(sliceElement, ++order, sourceType);
            response.getSlices().add(slice);
            byRole.computeIfAbsent(slice.getSuggestedRole(), ignored -> new ArrayList<>()).add(slice);
        }
        buildRoleDrafts(response, byRole);
        return response;
    }

    public Map<String, RoleSchema> normalizeRoles(Map<String, RoleSchema> roles) {
        Map<String, RoleSchema> normalized = new LinkedHashMap<>();
        if (roles == null) {
            return normalized;
        }
        for (Map.Entry<String, RoleSchema> entry : roles.entrySet()) {
            if (!StringUtils.hasText(entry.getKey()) || entry.getValue() == null) {
                continue;
            }
            RoleSchema schema = new RoleSchema();
            schema.setWrapperHtml(htmlSanitizer.sanitizeTemplateHtml(entry.getValue().getWrapperHtml()));
            if (StringUtils.hasText(schema.getWrapperHtml())) {
                schema.setWrapperSafe(isSafeParagraphWrapper(schema.getWrapperHtml()));
                normalized.put(entry.getKey().trim(), schema);
            }
        }
        return normalized;
    }

    public boolean isSafeParagraphWrapper(String wrapperHtml) {
        if (!StringUtils.hasText(wrapperHtml)) {
            return false;
        }
        Document document = Jsoup.parseBodyFragment(wrapperHtml);
        if (document.body().childrenSize() == 0) {
            return false;
        }
        for (Element element : document.body().select("*")) {
            String tag = element.tagName().toLowerCase(Locale.ROOT);
            if ("html".equals(tag) || "head".equals(tag) || "body".equals(tag)) {
                continue;
            }
            if (!SAFE_WRAPPER_TAGS.contains(tag)) {
                return false;
            }
            if (element.hasAttr("data-id")) {
                return false;
            }
            String style = element.attr("style").toLowerCase(Locale.ROOT);
            if (containsUnsafeWrapperStyle(style)) {
                return false;
            }
        }
        return true;
    }

    private boolean containsUnsafeWrapperStyle(String style) {
        if (!StringUtils.hasText(style)) {
            return false;
        }
        if (style.toLowerCase(Locale.ROOT).contains("linear-gradient")) {
            return true;
        }
        Map<String, String> declarations = parseStyle(style);
        for (String property : declarations.keySet()) {
            if (property.startsWith("background")
                    || property.startsWith("border")
                    || "box-shadow".equals(property)
                    || "border-radius".equals(property)) {
                return true;
            }
        }
        return false;
    }

    public BodyStyle normalizeBodyStyle(BodyStyle bodyStyle, String sourceType) {
        BodyStyle normalized = bodyStyle == null ? defaultBodyStyle(sourceType) : mergeBodyStyle(defaultBodyStyle(sourceType), bodyStyle);
        if (normalized == null) {
            return null;
        }
        normalized.setFontSize(cleanCssValue(normalized.getFontSize()));
        normalized.setLineHeight(cleanCssValue(normalized.getLineHeight()));
        normalized.setLetterSpacing(cleanCssValue(normalized.getLetterSpacing()));
        normalized.setColor(cleanCssValue(normalized.getColor()));
        normalized.setTextAlign(cleanCssValue(normalized.getTextAlign()));
        normalized.setParagraphMargin(cleanCssValue(normalized.getParagraphMargin()));
        return hasAnyBodyStyle(normalized) ? normalized : null;
    }

    public BodyStyle defaultBodyStyle(String sourceType) {
        if (!SOURCE_135.equals(sourceType)) {
            return null;
        }
        BodyStyle style = new BodyStyle();
        style.setFontSize("14px");
        style.setLineHeight("1.75");
        style.setLetterSpacing("0.5px");
        style.setColor("#333333");
        style.setTextAlign("justify");
        style.setParagraphMargin("0 0 14px");
        return style;
    }

    private BodyStyle copyBodyStyle(BodyStyle source) {
        BodyStyle target = new BodyStyle();
        target.setFontSize(source.getFontSize());
        target.setLineHeight(source.getLineHeight());
        target.setLetterSpacing(source.getLetterSpacing());
        target.setColor(source.getColor());
        target.setTextAlign(source.getTextAlign());
        target.setParagraphMargin(source.getParagraphMargin());
        return target;
    }

    private BodyStyle mergeBodyStyle(BodyStyle base, BodyStyle override) {
        BodyStyle target = base == null ? new BodyStyle() : copyBodyStyle(base);
        if (override == null) {
            return target;
        }
        if (StringUtils.hasText(override.getFontSize())) {
            target.setFontSize(override.getFontSize());
        }
        if (StringUtils.hasText(override.getLineHeight())) {
            target.setLineHeight(override.getLineHeight());
        }
        if (StringUtils.hasText(override.getLetterSpacing())) {
            target.setLetterSpacing(override.getLetterSpacing());
        }
        if (StringUtils.hasText(override.getColor())) {
            target.setColor(override.getColor());
        }
        if (StringUtils.hasText(override.getTextAlign())) {
            target.setTextAlign(override.getTextAlign());
        }
        if (StringUtils.hasText(override.getParagraphMargin())) {
            target.setParagraphMargin(override.getParagraphMargin());
        }
        return target;
    }

    private boolean hasAnyBodyStyle(BodyStyle bodyStyle) {
        return bodyStyle != null && (StringUtils.hasText(bodyStyle.getFontSize())
                || StringUtils.hasText(bodyStyle.getLineHeight())
                || StringUtils.hasText(bodyStyle.getLetterSpacing())
                || StringUtils.hasText(bodyStyle.getColor())
                || StringUtils.hasText(bodyStyle.getTextAlign())
                || StringUtils.hasText(bodyStyle.getParagraphMargin()));
    }

    private String cleanCssValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim().replaceAll(";+$", "");
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(trimmed)
                || lower.contains("expression(")
                || lower.contains("javascript:")
                || lower.contains("@import")
                || lower.contains("behavior:")
                || lower.contains("url(")
                || lower.contains("position:fixed")
                || lower.contains("caret-color")) {
            return null;
        }
        return trimmed;
    }

    private String detectSourceType(String html) {
        return html != null && html.contains("article135") ? SOURCE_135 : SOURCE_GENERIC;
    }

    private String normalizeSourceType(String html, String requestedSourceType) {
        if (StringUtils.hasText(requestedSourceType)) {
            String normalized = requestedSourceType.trim();
            if (SUPPORTED_SOURCE_TYPES.contains(normalized)) {
                return normalized;
            }
        }
        return detectSourceType(html);
    }

    private void applyParagraphBodyStyleOverrides(BodyStyle bodyStyle, List<Element> slices) {
        if (bodyStyle == null || slices == null || slices.isEmpty()) {
            return;
        }
        Map<String, Map<String, Integer>> values = new LinkedHashMap<>();
        for (Element slice : slices) {
            if (!isTrueParagraphSlice(slice)) {
                continue;
            }
            for (Element candidate : slice.select("p,span,section,div")) {
                if (!isBodyStyleCandidate(candidate)) {
                    continue;
                }
                Map<String, String> style = parseStyle(candidate.attr("style"));
                addStyleValue(values, "fontSize", style.get("font-size"));
                addStyleValue(values, "lineHeight", style.get("line-height"));
                addStyleValue(values, "color", style.get("color"));
                addStyleValue(values, "textAlign", style.get("text-align"));
                String marginBottom = style.get("margin-bottom");
                if (StringUtils.hasText(marginBottom)) {
                    addStyleValue(values, "paragraphMargin", "0 0 " + marginBottom.trim());
                } else {
                    addStyleValue(values, "paragraphMargin", style.get("margin"));
                }
            }
        }
        applyMajorityValue(values, "fontSize", bodyStyle::setFontSize);
        applyMajorityValue(values, "lineHeight", bodyStyle::setLineHeight);
        applyMajorityValue(values, "color", bodyStyle::setColor);
        applyMajorityValue(values, "textAlign", bodyStyle::setTextAlign);
        applyMajorityValue(values, "paragraphMargin", bodyStyle::setParagraphMargin);
    }

    private boolean isTrueParagraphSlice(Element slice) {
        if (!"paragraph".equalsIgnoreCase(slice.attr("data-role"))) {
            return false;
        }
        if (is135Module(slice) || isStandaloneImage(slice) || slice.selectFirst("img") != null) {
            return false;
        }
        if (contentBrush(slice) != null || looksLikeEndingCta(slice.text())
                || looksLikeLabeledContentModule(slice) || looksLikeQuoteModule(slice)) {
            return false;
        }
        return StringUtils.hasText(slice.text());
    }

    private boolean isBodyStyleCandidate(Element candidate) {
        if (!StringUtils.hasText(candidate.attr("style"))) {
            return false;
        }
        if (candidate.hasClass("135brush") || hasAncestor(candidate, ".135brush")
                || candidate.hasAttr("data-autoskip") || hasAncestor(candidate, "[data-autoskip]")) {
            return false;
        }
        String text = candidate.text().replace('\u00A0', ' ').trim();
        if (!StringUtils.hasText(text) || looksLikeNumberLabel(candidate, text)) {
            return false;
        }
        return text.replaceAll("\\s+", "").length() >= 8;
    }

    private boolean hasAncestor(Element element, String cssQuery) {
        Element current = element.parent();
        while (current != null) {
            if (current.is(cssQuery)) {
                return true;
            }
            current = current.parent();
        }
        return false;
    }

    private void addStyleValue(Map<String, Map<String, Integer>> values, String field, String rawValue) {
        String value = cleanCssValue(rawValue);
        if (!StringUtils.hasText(value)) {
            return;
        }
        values.computeIfAbsent(field, ignored -> new LinkedHashMap<>())
                .merge(value, 1, Integer::sum);
    }

    private void applyMajorityValue(Map<String, Map<String, Integer>> values,
                                    String field,
                                    java.util.function.Consumer<String> setter) {
        Map<String, Integer> counts = values.get(field);
        if (counts == null || counts.isEmpty()) {
            return;
        }
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        Map.Entry<String, Integer> winner = counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElse(null);
        if (winner != null && winner.getValue() * 2 > total) {
            setter.accept(winner.getKey());
        }
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

    private List<Element> flatten135(String html) {
        Document document = Jsoup.parseBodyFragment(html);
        Element outer = document.selectFirst("section.article135[data-role=outer]");
        Element root = outer == null ? document.body() : outer;
        List<Element> slices = new ArrayList<>();
        for (Element child : root.children()) {
            flatten135Child(child, slices);
        }
        return slices;
    }

    private void flatten135Child(Element element, List<Element> slices) {
        if (is135Module(element) && !"paragraph".equalsIgnoreCase(element.attr("data-role"))) {
            slices.add(element);
            return;
        }
        if ("paragraph".equalsIgnoreCase(element.attr("data-role"))) {
            boolean emittedNestedModule = false;
            for (Element child : element.children()) {
                if (is135Module(child)) {
                    slices.add(child);
                    emittedNestedModule = true;
                } else if (isStandaloneImage(child)) {
                    slices.add(child);
                    emittedNestedModule = true;
                }
            }
            if (!emittedNestedModule && !isBlankSlice(element)) {
                slices.add(element);
            }
            return;
        }
        if (isStandaloneImage(element)) {
            slices.add(element);
            return;
        }
        if (!isBlankSlice(element)) {
            slices.add(element);
        }
    }

    private List<Element> flattenGeneric(String html) {
        Document document = Jsoup.parseBodyFragment(html);
        return new ArrayList<>(document.body().children());
    }

    private boolean is135Module(Element element) {
        return element.hasClass("_135editor") && StringUtils.hasText(element.attr("data-id"));
    }

    private boolean isStandaloneImage(Element element) {
        return element.selectFirst("img") != null && !StringUtils.hasText(element.text());
    }

    private boolean isBlankSlice(Element element) {
        if (element == null) {
            return true;
        }
        return !StringUtils.hasText(element.text()) && element.selectFirst("img,svg") == null;
    }

    private TemplateSlice toSlice(Element source, int order, String sourceType) {
        Element normalized = source.clone();
        String role = guessRole(normalized);
        String wrapper = wrapperHtml(normalized, role, sourceType);
        String fingerprint = fingerprint(normalized, role);
        TemplateSlice slice = new TemplateSlice();
        slice.setId("slice_" + order);
        slice.setOrder(order);
        slice.setSuggestedRole(role);
        slice.setRole(role);
        slice.setFingerprint(fingerprint);
        slice.setHtml(htmlSanitizer.sanitizeTemplateHtml(wrapper));
        slice.setPreviewText(source.text());
        slice.setPreviewHtml(previewHtml(source, role));
        if (source.outerHtml().toLowerCase(Locale.ROOT).contains("background-image")) {
            slice.getWarnings().add("检测到 background-image，一期会清除该背景图样式");
        }
        return slice;
    }

    private String previewHtml(Element source, String role) {
        if ("image_block".equals(role)) {
            return "";
        }
        Element clone = source.clone();
        Element brush = contentBrush(clone);
        String html = brush == null ? clone.html() : brush.html();
        return htmlSanitizer.sanitizeTemplateHtml(html);
    }

    private String guessRole(Element element) {
        String role = element.attr("data-role");
        if ("splitline".equalsIgnoreCase(role)) {
            return "divider";
        }
        if ("title".equalsIgnoreCase(role)) {
            if (looksLikeEndingCta(element.text())) {
                return "ending_cta";
            }
            return "heading";
        }
        if (element.selectFirst("img") != null && !StringUtils.hasText(element.text())) {
            return "image_block";
        }
        if (looksLikeEndingCta(element.text())) {
            return "ending_cta";
        }
        if (looksLikeLabeledContentModule(element)) {
            return "highlight_block";
        }
        if (looksLikeQuoteModule(element)) {
            return "quote_block";
        }
        if (!StringUtils.hasText(role) && is135Module(element)) {
            return "highlight_block";
        }
        return "paragraph";
    }

    private boolean looksLikeEndingCta(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String normalized = text.replaceAll("\\s+", "");
        return normalized.contains("联系")
                || normalized.contains("扫码")
                || normalized.contains("关注")
                || normalized.contains("咨询")
                || normalized.contains("客服");
    }

    private boolean looksLikeLabeledContentModule(Element element) {
        Element brush = contentBrush(element);
        if (brush == null) {
            return hasNumberLabel(element);
        }
        if (brush.selectFirst("ol,ul") != null || "list".equalsIgnoreCase(brush.attr("data-role"))) {
            return true;
        }
        String outsideText = textOutsideContentBrush(element, brush).replaceAll("\\s+", "");
        if (!StringUtils.hasText(outsideText)) {
            return false;
        }
        if (outsideText.length() <= 16) {
            return true;
        }
        return hasShortLabelBrushOutsideContent(element, brush);
    }

    private boolean looksLikeQuoteModule(Element element) {
        if (element.selectFirst("svg,blockquote") != null) {
            return true;
        }
        Element brush = contentBrush(element);
        if (brush == null) {
            return false;
        }
        String outsideText = textOutsideContentBrush(element, brush).replaceAll("\\s+", "");
        if (StringUtils.hasText(outsideText)) {
            return false;
        }
        return hasSoftBackground(element);
    }

    private boolean hasNumberLabel(Element element) {
        for (Element strong : element.select("strong")) {
            String text = strong.text().replace('\u00A0', ' ').trim();
            if (looksLikeNumberLabel(strong, text)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasShortLabelBrushOutsideContent(Element element, Element contentBrush) {
        for (Element brush : element.select(".135brush")) {
            if (brush == contentBrush || isDescendantOf(brush, contentBrush)) {
                continue;
            }
            String text = brush.text().replaceAll("\\s+", "");
            if (StringUtils.hasText(text) && text.length() <= 16) {
                return true;
            }
        }
        return false;
    }

    private String textOutsideContentBrush(Element element, Element contentBrush) {
        Element clone = element.clone();
        Element brushClone = findCloneByElementPath(element, clone, contentBrush);
        if (brushClone != null) {
            brushClone.remove();
        }
        return clone.text();
    }

    private Element findCloneByElementPath(Element originalRoot, Element cloneRoot, Element target) {
        List<Integer> path = new ArrayList<>();
        Element current = target;
        while (current != null && current != originalRoot) {
            Element parent = current.parent();
            if (parent == null) {
                return null;
            }
            path.add(0, current.elementSiblingIndex());
            current = parent;
        }
        if (current != originalRoot) {
            return null;
        }
        Element clone = cloneRoot;
        for (Integer index : path) {
            if (index < 0 || index >= clone.childrenSize()) {
                return null;
            }
            clone = clone.child(index);
        }
        return clone;
    }

    private boolean isDescendantOf(Element element, Element ancestor) {
        Element current = element.parent();
        while (current != null) {
            if (current == ancestor) {
                return true;
            }
            current = current.parent();
        }
        return false;
    }

    private boolean hasSoftBackground(Element element) {
        for (Element styled : element.select("[style]")) {
            String style = styled.attr("style").toLowerCase(Locale.ROOT);
            if ((style.contains("background-color") || style.contains("background:"))
                    && !style.contains("transparent")) {
                return true;
            }
        }
        return false;
    }

    private String wrapperHtml(Element element, String role, String sourceType) {
        if ("image_block".equals(role)) {
            Element img = element.selectFirst("img");
            if (img != null) {
                img.attr("src", "{{imageUrl}}");
                img.attr("data-src", "{{imageUrl}}");
                img.attr("alt", "{{imageAlt}}");
            }
            return element.outerHtml();
        }
        if ("divider".equals(role)) {
            return element.outerHtml();
        }
        Element brush = contentBrush(element);
        if (brush != null) {
            brush.html("{{content}}");
        } else if (StringUtils.hasText(element.text())) {
            replaceTextNodes(element, "{{content}}");
        }
        normalizeNumberText(element);
        return element.outerHtml();
    }

    private void replaceTextNodes(Element element, String value) {
        Element carrier = contentCarrier(element);
        if (carrier == null) {
            element.empty();
            element.append(value);
            return;
        }
        carrier.empty();
        carrier.append(value);
    }

    private Element contentCarrier(Element root) {
        List<Element> textElements = new ArrayList<>();
        for (Element candidate : root.select("p,span,strong,em,section,div,h1,h2,h3,h4,blockquote,li")) {
            String ownText = candidate.ownText().replace('\u00A0', ' ').trim();
            if (!StringUtils.hasText(ownText) || looksLikeNumberLabel(candidate, ownText)) {
                continue;
            }
            textElements.add(candidate);
        }
        if (textElements.isEmpty()) {
            return null;
        }
        if (textElements.size() == 1) {
            return textElements.get(0);
        }
        Element common = commonAncestor(root, textElements);
        if (common != null && common != root) {
            return common;
        }
        return firstTextBlock(root);
    }

    private Element commonAncestor(Element root, List<Element> elements) {
        Element current = elements.get(0);
        while (current != null && current != root.parent()) {
            boolean containsAll = true;
            for (Element element : elements) {
                if (element != current && !isDescendantOf(element, current)) {
                    containsAll = false;
                    break;
                }
            }
            if (containsAll) {
                return current;
            }
            current = current.parent();
        }
        return null;
    }

    private Element firstTextBlock(Element root) {
        for (Element candidate : root.select("p,h1,h2,h3,h4,blockquote,li,section,div")) {
            String text = candidate.text().replace('\u00A0', ' ').trim();
            if (StringUtils.hasText(text)) {
                return candidate;
            }
        }
        return root;
    }

    private Element contentBrush(Element element) {
        Element autoskip = element.selectFirst(".135brush[data-autoskip]");
        if (autoskip != null) {
            return autoskip;
        }
        Element paragraphBrush = element.selectFirst(".135brush:has(p), .135brush:has(h1), .135brush:has(h2), .135brush:has(h3)");
        if (paragraphBrush != null) {
            return paragraphBrush;
        }
        Element brush = element.selectFirst(".135brush");
        if (brush != null) {
            return brush;
        }
        return titleTextElement(element);
    }

    private Element titleTextElement(Element element) {
        if (!"title".equalsIgnoreCase(element.attr("data-role"))) {
            return null;
        }
        List<Element> strongs = element.select("strong");
        for (int i = strongs.size() - 1; i >= 0; i--) {
            Element strong = strongs.get(i);
            String text = strong.text().replace('\u00A0', ' ').trim();
            if (!StringUtils.hasText(text) || looksLikeNumberLabel(strong, text)) {
                continue;
            }
            return strong;
        }
        return null;
    }

    private void normalizeNumberText(Element element) {
        for (Element autoNum : element.select(".autonum")) {
            clearPrecedingNumberLabels(element, autoNum);
            Element parent = autoNum.parent();
            if (parent != null) {
                for (Element sibling : parent.children()) {
                    if (sibling == autoNum || !"strong".equalsIgnoreCase(sibling.tagName())) {
                        continue;
                    }
                    String siblingText = sibling.text().replace('\u00A0', ' ').trim();
                    if (looksLikeNumberLabel(sibling, siblingText)) {
                        sibling.empty();
                    }
                }
            }
            autoNum.html("{{index}}");
        }
        for (Element strong : element.select("strong")) {
            String text = strong.text().replace('\u00A0', ' ').trim();
            if (strong.hasClass("autonum")) {
                continue;
            }
            if (looksLikeNumberLabel(strong, text)) {
                strong.html("{{index}}");
            }
        }
    }

    private void clearPrecedingNumberLabels(Element root, Element autoNum) {
        List<Element> strongs = root.select("strong");
        int autoNumIndex = strongs.indexOf(autoNum);
        for (int i = autoNumIndex - 1; i >= 0; i--) {
            Element strong = strongs.get(i);
            String text = strong.text().replace('\u00A0', ' ').trim();
            if (!looksLikeNumberLabel(strong, text)) {
                break;
            }
            strong.empty();
        }
    }

    private boolean looksLikeNumberLabel(Element element, String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        if (element.hasClass("autonum")) {
            return true;
        }
        return text.matches("\\d{1,3}") || text.matches("[一二三四五六七八九十百]{1,4}");
    }

    private String fingerprint(Element element, String role) {
        Element clone = element.clone();
        Element brush = contentBrush(clone);
        if (brush != null) {
            brush.html("{{content}}");
        }
        for (Element labelBrush : clone.select(".135brush")) {
            if (labelBrush != brush) {
                labelBrush.html("{{title}}");
            }
        }
        for (Element img : clone.select("img")) {
            img.attr("src", "{{imageUrl}}");
            img.attr("data-src", "{{imageUrl}}");
            img.attr("alt", "{{imageAlt}}");
        }
        normalizeNumberText(clone);
        normalizeVolatileAttributes(clone);
        normalizeTextNodes(clone);
        String normalized = role + ":" + clone.outerHtml().replaceAll("\\s+", " ").trim();
        return DigestUtils.md5DigestAsHex(normalized.getBytes(StandardCharsets.UTF_8));
    }

    private void normalizeVolatileAttributes(Element element) {
        element.select("[data-start]").removeAttr("data-start");
        element.select("[data-end]").removeAttr("data-end");
        element.select("[data-width]").removeAttr("data-width");
        element.select("[data-ratio]").removeAttr("data-ratio");
        element.select("[data-w]").removeAttr("data-w");
        element.select("[data-index]").removeAttr("data-index");
        element.select("[title]").removeAttr("title");
        element.select("[draggable]").removeAttr("draggable");
        element.select("[alt]").removeAttr("alt");
    }

    private void normalizeTextNodes(Element element) {
        for (TextNode textNode : element.textNodes()) {
            String text = textNode.text().replace('\u00A0', ' ').trim();
            if (StringUtils.hasText(text) && !text.contains("{{")) {
                textNode.text("{{text}}");
            }
        }
        for (Element child : element.children()) {
            normalizeTextNodes(child);
        }
    }

    private void buildRoleDrafts(TemplateParseResponse response, Map<String, List<TemplateSlice>> byRole) {
        for (Map.Entry<String, List<TemplateSlice>> entry : byRole.entrySet()) {
            Map<String, List<TemplateSlice>> clusters = new LinkedHashMap<>();
            for (TemplateSlice slice : entry.getValue()) {
                clusters.computeIfAbsent(slice.getFingerprint(), ignored -> new ArrayList<>()).add(slice);
            }
            List<TemplateSlice> mainCluster = clusters.values().stream()
                    .max((a, b) -> Integer.compare(a.size(), b.size()))
                    .orElse(List.of());
            for (TemplateSlice slice : entry.getValue()) {
                if (!mainCluster.contains(slice)) {
                    slice.setOutlier(true);
                    response.getWarnings().add(RenderWarning.of("template_outlier", slice.getId(), entry.getKey(), "该片段与同角色主样式不同，请确认角色"));
                }
            }
            TemplateSlice representative = mainCluster.isEmpty() ? entry.getValue().get(0) : mainCluster.get(0);
            TemplateRoleDraft draft = new TemplateRoleDraft();
            draft.setRole(entry.getKey());
            draft.setWrapperHtml(representative.getHtml());
            draft.setWrapperSafe(isSafeParagraphWrapper(representative.getHtml()));
            draft.setReuseCount(entry.getValue().size());
            draft.setNeedsConfirmation(entry.getValue().stream().anyMatch(TemplateSlice::isOutlier));
            if ("paragraph".equals(entry.getKey()) && !Boolean.TRUE.equals(draft.getWrapperSafe())) {
                response.getWarnings().add(RenderWarning.of(
                        "paragraph_wrapper_unsafe",
                        null,
                        "paragraph",
                        "正文外壳包含背景/边框等装饰，可能不适合作为通用正文样式，渲染时将默认使用基础排版"
                ));
            }
            for (TemplateSlice slice : entry.getValue()) {
                draft.getSliceIds().add(slice.getId());
            }
            response.getRoles().add(draft);
        }
    }
}
