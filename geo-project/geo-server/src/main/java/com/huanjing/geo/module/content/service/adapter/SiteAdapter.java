package com.huanjing.geo.module.content.service.adapter;

import com.huanjing.geo.module.content.distribution.TargetContext;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.system.entity.PublishSite;

public interface SiteAdapter {

    String OP_SUBMIT_TO_TARGET = "submitToTarget";
    String OP_QUERY_STATUS = "queryStatus";
    String OP_CHECK_AUTH = "checkAuth";
    String OP_REFRESH_AUTH = "refreshAuth";

    boolean supports(String integrationMethod);

    ValidationResult validate(ArticleDraft article, String contentMarkdown, PublishSite site);

    SubmitResult submit(ArticleDraft article, String contentMarkdown, PublishSite site);

    String parsePublishedUrl(String responseBody, PublishSite site);

    default boolean supportsPlatform(String platform) {
        return false;
    }

    default SubmitResult submitToTarget(ArticleDraft article, String contentMarkdown, TargetContext target) {
        throw unsupported(OP_SUBMIT_TO_TARGET);
    }

    default String rebindMedia(String contentHtml, TargetContext target) {
        return contentHtml;
    }

    default SubmitResult queryStatus(TargetContext target, String platformArticleId) {
        throw unsupported(OP_QUERY_STATUS);
    }

    default AuthCheckResult checkAuth(TargetContext target) {
        throw unsupported(OP_CHECK_AUTH);
    }

    default AuthCheckResult refreshAuth(TargetContext target) {
        throw unsupported(OP_REFRESH_AUTH);
    }

    private UnsupportedOperationException unsupported(String op) {
        return new UnsupportedOperationException("Operation not implemented: " + op);
    }
}
