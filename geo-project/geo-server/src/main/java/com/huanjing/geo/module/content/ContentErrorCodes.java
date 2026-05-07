package com.huanjing.geo.module.content;

public final class ContentErrorCodes {

    public static final int ARTICLE_BAD_REQUEST = 80001;
    public static final int ARTICLE_STATE_CONFLICT = 80002;
    public static final int ARTICLE_NOT_FOUND = 80003;
    public static final int ARTICLE_AUTHOR_CANNOT_REVIEW = 80004;
    public static final int ARTICLE_AI_DRAFT_RATE_LIMITED = 80200;
    public static final int ARTICLE_AI_DRAFT_CONFIG_MISSING = 80201;
    public static final int ARTICLE_AI_DRAFT_GENERATE_FAILED = 80202;

    private ContentErrorCodes() {
    }
}
