package com.huanjing.geo.module.content.constant;

import java.util.Set;

public final class ArticlePublishRecordStatusPolicy {

    public static final Set<String> ARCHIVE_DELIVERED_STATUSES = Set.of(
            "published",
            "published_confirmed",
            "published_url_pending",
            "distributed",
            "offline"
    );

    public static final String ARCHIVE_DELIVERED_STATUS_SQL =
            "'published','published_confirmed','published_url_pending','distributed','offline'";

    private ArticlePublishRecordStatusPolicy() {
    }
}
