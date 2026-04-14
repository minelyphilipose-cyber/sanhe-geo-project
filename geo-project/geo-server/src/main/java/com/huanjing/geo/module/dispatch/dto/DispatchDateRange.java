package com.huanjing.geo.module.dispatch.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class DispatchDateRange {
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime startAt;
    private LocalDateTime endAtExclusive;
}

