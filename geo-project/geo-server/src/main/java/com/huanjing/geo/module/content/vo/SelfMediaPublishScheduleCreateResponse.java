package com.huanjing.geo.module.content.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SelfMediaPublishScheduleCreateResponse {
    private Long requestId;
    private String requestIdempotencyKey;
    private List<SelfMediaPublishScheduleVO> createdSchedules = new ArrayList<>();
    private List<SelfMediaPublishScheduleVO> existingSchedules = new ArrayList<>();
    private List<SelfMediaPublishScheduleRejectedItemVO> rejectedItems = new ArrayList<>();
}
