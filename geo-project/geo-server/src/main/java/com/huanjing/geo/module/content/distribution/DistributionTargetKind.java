package com.huanjing.geo.module.content.distribution;

public final class DistributionTargetKind {
    public static final String SITE = "site";
    /**
     * 自媒体账号目标。历史上仅指微信公众号，自 Step 2 起涵盖所有
     * self_media_account 类型（包括抖音、未来其他平台）。具体平台
     * 通过 self_media_account.platform 字段区分。
     */
    public static final String MP_ACCOUNT = "mp_account";
    public static final String BRAND_OFFICIAL_SITE = "brand_official_site";
    public static final String BRAND_GEO_SITE = "brand_geo_site";
    public static final String INDUSTRY_SITE = "industry_site";
    public static final String AUTHORITY_MEDIA = "authority_media";

    private DistributionTargetKind() {
    }
}
