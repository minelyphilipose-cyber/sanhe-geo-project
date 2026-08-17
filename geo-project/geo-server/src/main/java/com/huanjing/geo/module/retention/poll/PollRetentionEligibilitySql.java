package com.huanjing.geo.module.retention.poll;

import com.huanjing.geo.module.mobiledashboard.service.MobileDashboardAiPlatformCatalog;

public final class PollRetentionEligibilitySql {

    private PollRetentionEligibilitySql() {
    }

    public static String channelMatch(String newerAlias, String currentAlias) {
        String newer = MobileDashboardAiPlatformCatalog.canonicalSqlExpression(rawChannel(newerAlias));
        String current = MobileDashboardAiPlatformCatalog.canonicalSqlExpression(rawChannel(currentAlias));
        return "((" + newer + " = " + current + ") OR (" + newer + " IS NULL AND " + current + " IS NULL))";
    }

    public static String sameProjectionIdentity(String newerAlias, String currentAlias) {
        return channelMatch(newerAlias, currentAlias) + " AND ("
                + "(" + currentAlias + ".keyword_result_id IS NOT NULL AND "
                + newerAlias + ".keyword_result_id = " + currentAlias + ".keyword_result_id) OR ("
                + currentAlias + ".keyword_result_id IS NULL AND " + newerAlias + ".keyword_result_id IS NULL AND "
                + "LOWER(TRIM(COALESCE(" + newerAlias + ".keyword_text_snapshot, ''))) = "
                + "LOWER(TRIM(COALESCE(" + currentAlias + ".keyword_text_snapshot, '')))))";
    }

    public static String newerProjection(String newerAlias, String currentAlias) {
        return sameProjectionIdentity(newerAlias, currentAlias) + " AND ("
                + newerAlias + ".batch_date > " + currentAlias + ".batch_date OR ("
                + newerAlias + ".batch_date = " + currentAlias + ".batch_date AND "
                + "COALESCE(" + newerAlias + ".updated_at, " + newerAlias + ".created_at) > "
                + "COALESCE(" + currentAlias + ".updated_at, " + currentAlias + ".created_at)) OR ("
                + newerAlias + ".batch_date = " + currentAlias + ".batch_date AND "
                + "COALESCE(" + newerAlias + ".updated_at, " + newerAlias + ".created_at) = "
                + "COALESCE(" + currentAlias + ".updated_at, " + currentAlias + ".created_at) AND "
                + newerAlias + ".id > " + currentAlias + ".id))";
    }

    public static String purgeable(String currentAlias) {
        return "(COALESCE(" + currentAlias + ".status, '') <> 'completed' OR EXISTS ("
                + "SELECT 1 FROM poll_results newer WHERE newer.project_id = " + currentAlias + ".project_id "
                + "AND newer.question_tier = " + currentAlias + ".question_tier "
                + "AND newer.status = 'completed' AND " + newerProjection("newer", currentAlias) + "))";
    }

    private static String rawChannel(String alias) {
        return "COALESCE(NULLIF(TRIM(" + alias + ".channel_code), ''), " + alias + ".platform_code)";
    }
}
