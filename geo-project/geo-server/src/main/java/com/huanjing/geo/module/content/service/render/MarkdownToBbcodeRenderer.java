package com.huanjing.geo.module.content.service.render;

import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
public class MarkdownToBbcodeRenderer {

    private final Parser parser = Parser.builder()
            .extensions(List.of(TablesExtension.create()))
            .build();
    private final HtmlRenderer renderer = HtmlRenderer.builder()
            .extensions(List.of(TablesExtension.create()))
            .escapeHtml(true)
            .build();

    public String render(String markdown) {
        if (!StringUtils.hasText(markdown)) {
            return "";
        }
        Node parsed = parser.parse(markdown);
        Document document = Jsoup.parseBodyFragment(renderer.render(parsed));
        document.outputSettings().prettyPrint(false);
        return normalize(renderChildren(document.body())).trim();
    }

    private String renderChildren(Element element) {
        StringBuilder builder = new StringBuilder();
        for (org.jsoup.nodes.Node child : element.childNodes()) {
            builder.append(renderNode(child));
        }
        return builder.toString();
    }

    private String renderNode(org.jsoup.nodes.Node node) {
        if (node instanceof TextNode textNode) {
            return textNode.text();
        }
        if (!(node instanceof Element element)) {
            return "";
        }
        String content = renderChildren(element);
        return switch (element.normalName()) {
            case "p" -> content.strip() + "\n\n";
            case "br" -> "\n";
            case "strong", "b" -> "[b]" + content + "[/b]";
            case "em", "i" -> "[i]" + content + "[/i]";
            case "u" -> "[u]" + content + "[/u]";
            case "a" -> renderLink(element, content);
            case "img" -> renderImage(element);
            case "blockquote" -> "[quote]" + content.strip() + "[/quote]\n\n";
            case "code" -> "[code]" + content + "[/code]";
            case "pre" -> "[code]" + content.strip() + "[/code]\n\n";
            case "ul", "ol" -> renderList(element);
            case "li" -> "[*]" + content.strip() + "\n";
            case "h1", "h2", "h3", "h4", "h5", "h6" -> "[b]" + content.strip() + "[/b]\n\n";
            case "table" -> content.strip() + "\n\n";
            case "thead", "tbody", "tr" -> content.strip() + "\n";
            case "th", "td" -> content.strip() + "\t";
            default -> content;
        };
    }

    private String renderLink(Element element, String content) {
        String href = element.attr("href");
        if (!StringUtils.hasText(href)) {
            return content;
        }
        return "[url=" + href.trim() + "]" + (StringUtils.hasText(content) ? content : href.trim()) + "[/url]";
    }

    private String renderImage(Element element) {
        String src = element.attr("src");
        if (!StringUtils.hasText(src)) {
            return "";
        }
        String normalized = src.trim();
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            return "";
        }
        return "[img]" + normalized + "[/img]";
    }

    private String renderList(Element element) {
        String content = renderChildren(element).strip();
        return "[list]\n" + content + "\n[/list]\n\n";
    }

    private String normalize(String value) {
        return value
                .replace("\r\n", "\n")
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n");
    }
}
