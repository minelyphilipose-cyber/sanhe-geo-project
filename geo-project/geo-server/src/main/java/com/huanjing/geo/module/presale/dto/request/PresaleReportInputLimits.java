package com.huanjing.geo.module.presale.dto.request;

import java.util.List;

public final class PresaleReportInputLimits {

    public static final int BRAND_NAME_MAX_LENGTH = 18;
    public static final int INDUSTRY_ROLE_MAX_LENGTH = 50;
    public static final int COMPETITOR_GROUP_MAX_LENGTH = 100;
    public static final String COMPETITOR_SEPARATOR = "、";

    private PresaleReportInputLimits() {
    }

    public static int competitorGroupLength(List<String> competitors) {
        if (competitors == null || competitors.isEmpty()) {
            return 0;
        }
        return String.join(COMPETITOR_SEPARATOR, competitors).length();
    }
}
