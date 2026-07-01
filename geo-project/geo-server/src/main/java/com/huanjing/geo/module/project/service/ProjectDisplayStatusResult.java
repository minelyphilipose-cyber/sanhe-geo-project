package com.huanjing.geo.module.project.service;

public record ProjectDisplayStatusResult(
        String projectDisplayStatus,
        String label,
        boolean editable,
        boolean submittable
) {
}
