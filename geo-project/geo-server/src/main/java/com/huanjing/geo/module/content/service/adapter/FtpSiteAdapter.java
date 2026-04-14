package com.huanjing.geo.module.content.service.adapter;

import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.system.entity.PublishSite;
import org.springframework.stereotype.Component;

@Component
public class FtpSiteAdapter implements SiteAdapter {

    @Override
    public boolean supports(String integrationMethod) {
        return "ftp".equalsIgnoreCase(integrationMethod);
    }

    @Override
    public ValidationResult validate(ArticleDraft article, String contentMarkdown, PublishSite site) {
        throw new UnsupportedOperationException("Integration method not implemented: ftp");
    }

    @Override
    public SubmitResult submit(ArticleDraft article, String contentMarkdown, PublishSite site) {
        throw new UnsupportedOperationException("Integration method not implemented: ftp");
    }

    @Override
    public String parsePublishedUrl(String responseBody, PublishSite site) {
        throw new UnsupportedOperationException("Integration method not implemented: ftp");
    }
}
