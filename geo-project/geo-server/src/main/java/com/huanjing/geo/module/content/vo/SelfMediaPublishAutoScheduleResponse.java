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
    private Integer plannedCount;
    private Integer rejectedCount;
    private Boolean created;
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
}
