package com.huanjing.geo.module.content.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document.OutputSettings;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

@Component
public class ArticleHtmlSanitizer {

    private final Safelist safelist;

    public ArticleHtmlSanitizer() {
        this.safelist = Safelist.basicWithImages();
        this.safelist.addProtocols("a", "href", "http", "https");
        this.safelist.addProtocols("img", "src", "http", "https");
    }

    public String clean(String htmlOrMarkdown) {
        return Jsoup.clean(htmlOrMarkdown == null ? "" : htmlOrMarkdown, "", safelist, new OutputSettings().prettyPrint(false));
    }
}
