package com.huanjing.geo.module.mobiledashboard.dto;

public record MobileDashboardWechatConfigVO(
        boolean enabled,
        String appId,
        Long timestamp,
        String nonceStr,
        String signature,
        ShareContent share
) {
    public static MobileDashboardWechatConfigVO disabled() {
        return new MobileDashboardWechatConfigVO(false, null, null, null, null, null);
    }

    public record ShareContent(
            String title,
            String description,
            String link,
            String imageUrl
    ) {
    }
}
