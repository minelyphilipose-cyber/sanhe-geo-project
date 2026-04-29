package com.huanjing.geo.module.content.service.render;

import com.huanjing.geo.common.exception.BizException;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
public class MarkdownToHtmlRenderer {

    private final Parser parser = Parser.builder()
            .extensions(List.of(TablesExtension.create()))
            .build();
    private final HtmlRenderer renderer = HtmlRenderer.builder()
            .extensions(List.of(TablesExtension.create()))
            .escapeHtml(false)
            .build();

    public String render(String markdown) {
        if (!StringUtils.hasText(markdown)) {
            return "";
        }
        try {
            Node document = parser.parse(markdown);
            return sanitize(renderer.render(document));
        } catch (Throwable ex) {
            throw new BizException(500, "markdown render failed");
        }
    }

    private String sanitize(String html) {
        Document document = Jsoup.parseBodyFragment(html);
        document.outputSettings().prettyPrint(false);
        document.select("script,iframe,object,embed").remove();
        for (Element element : document.getAllElements()) {
            for (Attribute attribute : element.attributes().asList()) {
                removeUnsafeAttribute(element, attribute);
            }
        }
        return document.body().html();
    }

    private void removeUnsafeAttribute(Element element, Attribute attribute) {
        String key = attribute.getKey();
        String value = attribute.getValue();
        if (key != null && key.toLowerCase().startsWith("on")) {
            element.removeAttr(key);
            return;
        }
        if (value != null && value.trim().toLowerCase().startsWith("javascript:")) {
            element.removeAttr(key);
        }
    }
}
