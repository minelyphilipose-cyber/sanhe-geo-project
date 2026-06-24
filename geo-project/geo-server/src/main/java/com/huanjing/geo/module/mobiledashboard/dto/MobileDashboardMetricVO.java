package com.huanjing.geo.module.mobiledashboard.dto;

import lombok.Data;

@Data
public class MobileDashboardMetricVO<T> {
    private boolean available;
    private String reason;
    private T value;
    private String unit;

    public static <T> MobileDashboardMetricVO<T> available(T value) {
        MobileDashboardMetricVO<T> vo = new MobileDashboardMetricVO<>();
        vo.setAvailable(true);
        vo.setValue(value);
        return vo;
    }

    public static <T> MobileDashboardMetricVO<T> available(T value, String unit) {
        MobileDashboardMetricVO<T> vo = available(value);
        vo.setUnit(unit);
        return vo;
    }

    public static <T> MobileDashboardMetricVO<T> unavailable(String reason) {
        MobileDashboardMetricVO<T> vo = new MobileDashboardMetricVO<>();
        vo.setAvailable(false);
        vo.setReason(reason);
        return vo;
    }
}
