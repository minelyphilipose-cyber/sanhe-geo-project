package com.huanjing.geo.module.content.controller;

import com.huanjing.geo.module.content.service.ContentArticleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/content")
@RequiredArgsConstructor
public class PublicContentPreviewController {

    private final ContentArticleService contentArticleService;

    @GetMapping(value = "/articles/{articleId}/preview", produces = MediaType.TEXT_HTML_VALUE)
    public String articlePreview(@PathVariable Long articleId) {
        return contentArticleService.publicPreviewHtml(articleId);
    }
}
