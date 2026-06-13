package com.huanjing.geo.module.content.constant;

import java.util.Set;

public final class ArticlePublishRecordStatusPolicy {

    public static final Set<String> ARCHIVE_DELIVERED_STATUSES = Set.of(
            "published",
            "published_confirmed",
            "distributed",
            "offline"
    );

    public static final String ARCHIVE_DELIVERED_STATUS_SQL =
            "'published','published_confirmed','distributed','offline'";

    private ArticlePublishRecordStatusPolicy() {
    }
}
