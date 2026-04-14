package com.huanjing.geo.module.content.service.adapter;

import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.system.entity.PublishSite;

public interface SiteAdapter {

    boolean supports(String integrationMethod);

    ValidationResult validate(ArticleDraft article, String contentMarkdown, PublishSite site);

    SubmitResult submit(ArticleDraft article, String contentMarkdown, PublishSite site);

    String parsePublishedUrl(String responseBody, PublishSite site);
}
