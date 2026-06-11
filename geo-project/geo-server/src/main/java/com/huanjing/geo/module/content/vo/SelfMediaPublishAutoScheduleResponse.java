package com.huanjing.geo.module.content.vo;

import lombok.Data;

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
    private List<SelfMediaPublishScheduleVO> createdSchedules = new ArrayList<>();
    private List<SelfMediaPublishScheduleVO> existingSchedules = new ArrayList<>();
    private List<SelfMediaPublishScheduleRejectedItemVO> rejectedItems = new ArrayList<>();
}
