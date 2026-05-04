package com.huanjing.geo.module.content.wechat;

import com.huanjing.geo.common.exception.BizException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.function.Function;

@Component
public class WechatHtmlRewriter {
    private static final Set<String> ALLOWED_TAGS = Set.of(
            "p", "br", "strong", "b", "em", "i", "u", "s",
            "blockquote", "ul", "ol", "li", "h1", "h2", "h3", "h4",
            "table", "thead", "tbody", "tr", "th", "td",
            "img", "a", "span", "section", "div"
    );
    private static final Set<String> GLOBAL_ATTRS = Set.of("style", "align");
    private static final Set<String> IMG_ATTRS = Set.of("src", "data-src", "alt", "title", "style", "width", "height");
    private static final Set<String> A_ATTRS = Set.of("href", "title", "style");

    public String rewrite(String html, Function<String, String> imageUrlRewriter) {
        if (!StringUtils.hasText(html)) {
            return "";
        }
        try {
            Document document = Jsoup.parseBodyFragment(html);
            document.outputSettings().prettyPrint(false);
            replaceUnsupportedEmbeds(document);
            sanitizeElements(document);
            rewriteImages(document, imageUrlRewriter);
            return document.body().html();
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(500, "wechat html rewrite failed");
        }
    }

    private void replaceUnsupportedEmbeds(Document document) {
        document.select("video,iframe,object,embed").forEach(element ->
                element.replaceWith(new TextNode("详情见原文"))
        );
        document.select("script,style,link").remove();
    }

    private void sanitizeElements(Document document) {
        for (Element element : document.body().select("*")) {
            if ("body".equalsIgnoreCase(element.tagName())) {
                continue;
            }
            if (!ALLOWED_TAGS.contains(element.tagName().toLowerCase())) {
                element.unwrap();
                continue;
            }
            for (Attribute attribute : element.attributes().asList()) {
                if (!isAllowedAttribute(element, attribute)) {
                    element.removeAttr(attribute.getKey());
                    continue;
                }
                if (isUnsafeValue(attribute.getValue())) {
                    element.removeAttr(attribute.getKey());
                }
            }
            sanitizeLink(element);
            sanitizeStyle(element);
        }
    }

    private boolean isAllowedAttribute(Element element, Attribute attribute) {
        String key = attribute.getKey() == null ? "" : attribute.getKey().toLowerCase();
        if (key.startsWith("on")) {
            return false;
        }
        if ("img".equalsIgnoreCase(element.tagName())) {
            return IMG_ATTRS.contains(key);
        }
        if ("a".equalsIgnoreCase(element.tagName())) {
            return A_ATTRS.contains(key);
        }
        return GLOBAL_ATTRS.contains(key);
    }

    private boolean isUnsafeValue(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return normalized.startsWith("javascript:") || normalized.startsWith("data:text/html");
    }

    private void sanitizeLink(Element element) {
        if (!"a".equalsIgnoreCase(element.tagName())) {
            return;
        }
        String href = element.attr("href");
        if (!StringUtils.hasText(href)) {
            element.removeAttr("href");
            return;
        }
        String lower = href.trim().toLowerCase();
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            element.removeAttr("href");
        }
    }

    private void sanitizeStyle(Element element) {
        String style = element.attr("style");
        if (!StringUtils.hasText(style)) {
            return;
        }
        String lower = style.toLowerCase();
        if (lower.contains("position")
                || lower.contains("javascript:")
                || lower.contains("expression(")
                || lower.contains("url(")
                || lower.contains("@import")) {
            element.removeAttr("style");
        }
    }

    private void rewriteImages(Document document, Function<String, String> imageUrlRewriter) {
        for (Element image : document.select("img")) {
            String src = firstText(image.attr("src"), image.attr("data-src"));
            if (!StringUtils.hasText(src)) {
                image.remove();
                continue;
            }
            String rewritten = imageUrlRewriter.apply(src.trim());
            if (!StringUtils.hasText(rewritten)) {
                image.remove();
                continue;
            }
            image.attr("src", rewritten);
            image.attr("data-src", rewritten);
        }
    }

    private String firstText(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first;
        }
        return second;
    }
}
