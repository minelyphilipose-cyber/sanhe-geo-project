package com.huanjing.geo.module.content.service.render.wechat;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

@Component
public class WechatHtmlSanitizer {
    private static final PolicyFactory BASE_POLICY = new HtmlPolicyBuilder()
            .allowElements("p", "br", "strong", "b", "em", "i", "u", "s", "blockquote",
                    "ul", "ol", "li", "table", "thead", "tbody", "tr", "th", "td",
                    "img", "a", "span", "section", "div", "h1", "h2", "h3", "h4")
            .allowAttributes("href", "title").onElements("a")
            .allowAttributes("src", "data-src", "alt", "title", "width", "height").onElements("img")
            .allowAttributes("style", "align").globally()
            .allowUrlProtocols("http", "https")
            .toFactory();

    private static final Set<String> TEMPLATE_TAGS = Set.of(
            "p", "br", "strong", "b", "em", "i", "u", "s", "blockquote",
            "ul", "ol", "li", "table", "thead", "tbody", "tr", "th", "td",
            "img", "a", "span", "section", "div", "h1", "h2", "h3", "h4",
            "svg", "g", "path", "polygon", "circle", "rect", "line", "polyline"
    );
    private static final Set<String> NATIVE_TAGS = Set.of(
            "ul", "ol", "li", "table", "thead", "tbody", "tr", "th", "td",
            "p", "br", "strong", "b", "em", "i", "code", "a"
    );
    private static final Set<String> SVG_ATTRS = Set.of(
            "xmlns", "viewbox", "d", "fill", "stroke", "points", "style", "width", "height",
            "x", "y", "cx", "cy", "r", "rx", "ry", "x1", "x2", "y1", "y2", "transform",
            "xml:space", "id", "data-name", "fill-rule"
    );

    public String sanitizeTemplateHtml(String html) {
        return sanitize(html, TEMPLATE_TAGS, true, true);
    }

    public String sanitizeNativeHtml(String html) {
        return sanitize(html, NATIVE_TAGS, false, false);
    }

    public String sanitizeFinalHtml(String html) {
        return sanitize(html, TEMPLATE_TAGS, true, true);
    }

    private String sanitize(String html, Set<String> allowedTags, boolean allowStyle, boolean allowSvg) {
        if (!StringUtils.hasText(html)) {
            return "";
        }
        String base = allowSvg ? html : BASE_POLICY.sanitize(html);
        Document document = Jsoup.parseBodyFragment(allowSvg ? html : base);
        document.outputSettings().prettyPrint(false);
        document.select("script,style,link,iframe,object,embed,form,input,button,textarea").remove();
        for (Element element : document.body().select("*")) {
            String tag = element.tagName().toLowerCase(Locale.ROOT);
            if ("body".equals(tag)) {
                continue;
            }
            if (!allowedTags.contains(tag)) {
                element.unwrap();
                continue;
            }
            sanitizeAttributes(element, allowStyle, allowSvg);
        }
        return document.body().html();
    }

    private void sanitizeAttributes(Element element, boolean allowStyle, boolean allowSvg) {
        String tag = element.tagName().toLowerCase(Locale.ROOT);
        for (Attribute attribute : element.attributes().asList()) {
            String key = attribute.getKey() == null ? "" : attribute.getKey().toLowerCase(Locale.ROOT);
            String value = attribute.getValue();
            if (key.startsWith("on") || isUnsafeValue(value)) {
                element.removeAttr(attribute.getKey());
                continue;
            }
            if ("style".equals(key)) {
                if (!allowStyle) {
                    element.removeAttr(attribute.getKey());
                } else {
                    String cleanStyle = sanitizeStyle(value);
                    if (StringUtils.hasText(cleanStyle)) {
                        element.attr(attribute.getKey(), cleanStyle);
                    } else {
                        element.removeAttr(attribute.getKey());
                    }
                }
                continue;
            }
            if (tag.equals("img") && Set.of("src", "data-src", "alt", "title", "width", "height", "draggable", "data-ratio", "data-w").contains(key)) {
                continue;
            }
            if (tag.equals("a") && Set.of("href", "title").contains(key)) {
                if ("href".equals(key) && !isHttpUrl(value)) {
                    element.removeAttr(attribute.getKey());
                }
                continue;
            }
            if (allowSvg && Set.of("svg", "g", "path", "polygon", "circle", "rect", "line", "polyline").contains(tag)
                    && SVG_ATTRS.contains(key)) {
                continue;
            }
            if (Set.of("align", "class", "data-width", "data-role", "data-id", "data-tools", "data-autoskip").contains(key)) {
                continue;
            }
            element.removeAttr(attribute.getKey());
        }
    }

    private String sanitizeStyle(String style) {
        if (!StringUtils.hasText(style)) {
            return "";
        }
        String lower = style.toLowerCase(Locale.ROOT);
        if (lower.contains("expression(") || lower.contains("javascript:")
                || lower.contains("@import") || lower.contains("behavior:")
                || lower.contains("url(") || lower.contains("position:fixed")) {
            return "";
        }
        StringBuilder clean = new StringBuilder();
        for (String declaration : style.split(";")) {
            int index = declaration.indexOf(':');
            if (index <= 0) {
                continue;
            }
            String property = declaration.substring(0, index).trim().toLowerCase(Locale.ROOT);
            String value = declaration.substring(index + 1).trim();
            if ("caret-color".equals(property) || !StringUtils.hasText(value)) {
                continue;
            }
            if (!clean.isEmpty()) {
                clean.append(';');
            }
            clean.append(property).append(':').append(value);
        }
        return clean.toString();
    }

    private boolean isUnsafeValue(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("javascript:") || normalized.startsWith("data:text/html");
    }

    private boolean isHttpUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("http://") || normalized.startsWith("https://");
    }
}
