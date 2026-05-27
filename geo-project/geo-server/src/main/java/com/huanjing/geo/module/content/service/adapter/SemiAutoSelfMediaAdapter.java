package com.huanjing.geo.module.content.service.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.service.render.MarkdownToHtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Document.OutputSettings;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

public interface SemiAutoSelfMediaAdapter extends SelfMediaAdapter {

    ObjectMapper TAGS_OBJECT_MAPPER = new ObjectMapper();
    TypeReference<List<String>> TAG_LIST_TYPE = new TypeReference<>() {
    };
    Set<String> ATTRIBUTE_TAGS = Set.of("a", "img");

    PlatformFillProfile fillProfile();

    MarkdownToHtmlRenderer markdownToHtmlRenderer();

    default String renderContent(String markdown, PlatformFillProfile profile) {
        return renderContent(markdown, null, profile);
    }

    default String renderContent(String markdown, String title, PlatformFillProfile profile) {
        validateFillProfile(profile);
        String html = markdownToHtmlRenderer().render(markdown);
        return applyPlatformHtmlPolicy(removeDuplicateLeadingTitle(html, title), profile);
    }

    default SemiAutoFillTask prepareFillTask(ArticleDraft article,
                                             String contentMarkdown,
                                             PlatformFillProfile profile) {
        return new SemiAutoFillTask(
                platform(),
                profile.publishUrl(),
                article == null ? null : article.getTitle(),
                renderContent(contentMarkdown, article == null ? null : article.getTitle(), profile),
                article == null ? null : article.getCoverImageUrl(),
                parseTags(article == null ? null : article.getTagsJson()),
                article == null ? null : article.getCategory(),
                profile
        );
    }

    default String applyPlatformHtmlPolicy(String html, PlatformFillProfile profile) {
        validateFillProfile(profile);
        Safelist safelist = Safelist.none();
        safelist.addTags(profile.allowedHtmlTags().toArray(String[]::new));
        if (profile.allowedHtmlTags().stream().map(String::toLowerCase).anyMatch(ATTRIBUTE_TAGS::contains)) {
            safelist.addAttributes("a", "href", "title");
            safelist.addProtocols("a", "href", "http", "https");
            safelist.addAttributes("img", "src", "alt", "title");
            safelist.addProtocols("img", "src", "http", "https");
        }
        return Jsoup.clean(html == null ? "" : html, "", safelist, new OutputSettings().prettyPrint(false));
    }

    default String removeDuplicateLeadingTitle(String html, String title) {
        if (!StringUtils.hasText(html) || !StringUtils.hasText(title)) {
            return html;
        }
        Document document = Jsoup.parseBodyFragment(html);
        document.outputSettings().prettyPrint(false);
        Element firstBlock = document.body().children().stream()
                .filter(this::isContentBlock)
                .filter(element -> StringUtils.hasText(element.text()))
                .findFirst()
                .orElse(null);
        if (firstBlock == null || !normalizeArticleText(firstBlock.text()).equals(normalizeArticleText(title))) {
            return html;
        }
        firstBlock.remove();
        return document.body().html();
    }

    default boolean isContentBlock(Element element) {
        return Set.of("h1", "h2", "h3", "h4", "h5", "h6", "p").contains(element.tagName().toLowerCase());
    }

    default String normalizeArticleText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replace('\u00A0', ' ')
                .replaceAll("\\s+", "")
                .replaceAll("[，。！？、,.!?;；:：\"'“”‘’（）()\\[\\]【】《》<>]", "")
                .trim();
    }

    default void validateFillProfile(PlatformFillProfile profile) {
        if (profile == null) {
            throw new IllegalStateException("semi-auto platform profile is required for platform " + platform());
        }
        if (!StringUtils.hasText(profile.publishUrl())) {
            throw new IllegalStateException("semi-auto publishUrl is required for platform " + platform());
        }
        if (profile.allowedHtmlTags().isEmpty()) {
            throw new IllegalStateException("semi-auto allowedHtmlTags must not be empty for platform " + platform());
        }
    }

    default List<String> parseTags(String tagsJson) {
        if (!StringUtils.hasText(tagsJson)) {
            return List.of();
        }
        try {
            List<String> tags = TAGS_OBJECT_MAPPER.readValue(tagsJson, TAG_LIST_TYPE);
            return tags == null ? List.of() : tags.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .toList();
        } catch (Exception ex) {
            return List.of(tagsJson);
        }
    }
}
