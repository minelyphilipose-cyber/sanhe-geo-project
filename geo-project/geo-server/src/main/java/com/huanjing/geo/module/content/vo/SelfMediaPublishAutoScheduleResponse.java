package com.huanjing.geo.module.content.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class SelfMediaPublishAutoScheduleResponse {
    private Long brandId;
    private String targetMonth;
    private String scheduleStrategy;
    private Integer requestedCount;
    private Integer normalRequiredCount;
    private Integer pendingCarryOverCount;
    private Integer availableSlotCount;
    private Integer deficitCount;
    private Boolean enough;
    private String recommendedStrategy;
    private String decisionStrategy;
    private Integer plannedCount;
    private Integer rejectedCount;
    private Boolean created;
    private Boolean carryOverCreated;
    private Integer carryOverCount;
    private String carryOverTargetMonth;
    private Integer unavailableCarryOverCount;
    private List<String> warnings = new ArrayList<>();
    private List<CarryOverSource> carryOverSources = new ArrayList<>();
    private List<SelfMediaPublishAutoScheduleItemVO> plannedItems = new ArrayList<>();
    private List<SlotGroup> slotGroups = new ArrayList<>();
    private List<SelfMediaPublishScheduleVO> createdSchedules = new ArrayList<>();
    private List<SelfMediaPublishScheduleVO> existingSchedules = new ArrayList<>();
    private List<SelfMediaPublishScheduleRejectedItemVO> rejectedItems = new ArrayList<>();

    @Data
    public static class SlotGroup {
        private String platform;
        private String platformLabel;
        private String scheduleStrategy;
        private Integer requestedCount;
        private Integer availableSlotCount;
        private Integer deficitCount;
        private Integer remainingWorkdayCount;
        private Boolean enough;
        private String message;
        private List<SlotPreview> selectedSlots = new ArrayList<>();
    }

    @Data
    public static class SlotPreview {
        private LocalDateTime executionAt;
        private LocalDateTime plannedPublishAt;
        private String windowName;
    }

    @Data
    public static class CarryOverSource {
        private Long id;
        private String sourceMonth;
        private String targetMonth;
        private Integer carryOverCount;
        private Integer consumedCount;
        private Integer pendingCount;
        private String status;
    }
}
