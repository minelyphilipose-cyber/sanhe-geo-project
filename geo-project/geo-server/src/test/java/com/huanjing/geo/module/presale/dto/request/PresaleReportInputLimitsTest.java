package com.huanjing.geo.module.presale.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresaleReportInputLimitsTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void createRequest_acceptsBrandAt18AndRejectsBrandAt19() {
        CreateReportRequest request = validRequest();
        request.setBrandName("甲".repeat(18));
        assertTrue(validator.validate(request).isEmpty());

        request.setBrandName("甲".repeat(19));
        assertEquals(1, validator.validate(request).stream()
                .filter(error -> "brandName".equals(error.getPropertyPath().toString()))
                .count());
    }

    @Test
    void createRequest_acceptsIndustryRoleAt50AndRejectsIndustryRoleAt51() {
        CreateReportRequest request = validRequest();
        request.setIndustryRole("甲".repeat(50));
        assertTrue(validator.validate(request).isEmpty());

        request.setIndustryRole("甲".repeat(51));
        assertEquals(1, validator.validate(request).stream()
                .filter(error -> "industryRole".equals(error.getPropertyPath().toString()))
                .count());
    }

    @Test
    void competitorGroupLength_includesTwoSeparators() {
        assertEquals(100, PresaleReportInputLimits.competitorGroupLength(
                List.of("甲".repeat(32), "乙".repeat(32), "丙".repeat(34))));
        assertEquals(101, PresaleReportInputLimits.competitorGroupLength(
                List.of("甲".repeat(33), "乙".repeat(33), "丙".repeat(33))));
    }

    private static CreateReportRequest validRequest() {
        CreateReportRequest request = new CreateReportRequest();
        request.setBrandName("测试品牌");
        request.setIndustry("汽车");
        request.setIndustryRole("经销商");
        request.setRegion("亳州");
        return request;
    }
}
