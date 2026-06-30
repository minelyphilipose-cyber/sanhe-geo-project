package com.huanjing.geo.module.content.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.content.service.BusinessCalendarAdminService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "BusinessCalendarAdmin")
@RestController
@RequestMapping("/api/admin/business-calendar")
@RequiredArgsConstructor
public class BusinessCalendarAdminController {

    private final BusinessCalendarAdminService businessCalendarAdminService;

    @GetMapping("/next-year/status")
    public R<BusinessCalendarAdminService.CalendarAdminStatus> nextYearStatus() {
        return R.ok(businessCalendarAdminService.nextYearStatus());
    }

    @PostMapping("/next-year/generate")
    public R<BusinessCalendarAdminService.CalendarAdminStatus> generateNextYear(
            @RequestBody(required = false) GenerateCalendarRequest request
    ) {
        boolean force = request != null && Boolean.TRUE.equals(request.force());
        return R.ok(businessCalendarAdminService.generateNextYear(force));
    }

    public record GenerateCalendarRequest(Boolean force) {
    }
}
