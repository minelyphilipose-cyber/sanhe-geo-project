package com.huanjing.geo.module.dispatch.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class DispatchDueTimeDistributionVO {
    private String taskType;
    private LocalDateTime rangeStart;
    private LocalDateTime rangeEnd;
    private int bucketMinutes;
    private List<PlatformSeries> platforms = new ArrayList<>();

    @Data
    public static class PlatformSeries {
        private String platformCode;
        private List<StatusSeries> statuses = new ArrayList<>();
    }

    @Data
    public static class StatusSeries {
        private String status;
        private List<Bucket> buckets = new ArrayList<>();
    }

    @Data
    public static class Bucket {
        private LocalDateTime bucketStart;
        private long taskCount;
    }
}
