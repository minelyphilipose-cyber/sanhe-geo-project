package com.huanjing.geo.module.mobiledashboard.dto;

public record MobileDashboardWechatSharePreviewVO(
        String title,
        String description,
        String imageUrl,
        boolean wechatJsSdkEnabled,
        String rolloutMode
) {
}
