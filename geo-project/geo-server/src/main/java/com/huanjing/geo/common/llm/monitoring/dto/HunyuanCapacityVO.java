package com.huanjing.geo.common.llm.monitoring.dto;

import com.huanjing.geo.module.dispatch.dto.PollPlatformSliceProgressRow;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class HunyuanCapacityVO {
    private List<String> platformCodes;
    private int activeLimit;
    private long activePeak;
    private long currentActive;
    private long totalCount;
    private long limitedCount;
    private double limitRatio;
    private double limitRatioThreshold;
    private List<String> limitCategories;
    private SliceProgress sliceProgress;
    private RetryExhausted retryExhausted;
    private OpenAlert openAlert;

    @Data
    public static class SliceProgress {
        private LocalDate batchDate;
        private String questionTier;
        private long expectedCount;
        private long completedCount;
        private long failedCount;
        private long resourceWaitCount;
        private double actualProgress;
        private double expectedProgress;
        private double lag;
        private int windowMinutes;
        private LocalDateTime sliceStart;
        private List<PollPlatformSliceProgressRow> rows;
    }

    @Data
    public static class RetryExhausted {
        private long count;
        private LocalDateTime windowStart;
        private LocalDateTime windowEnd;
    }

    @Data
    public static class OpenAlert {
        private boolean open;
        private String dedupeKey;
        private String alertType;
        private String severity;
        private String message;
    }
}
