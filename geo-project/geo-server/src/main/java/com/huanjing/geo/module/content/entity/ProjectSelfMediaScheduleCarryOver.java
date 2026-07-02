package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("project_self_media_schedule_carry_over")
public class ProjectSelfMediaScheduleCarryOver {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long companyId;
    private Long brandId;
    private Long sourceBatchId;
    private String sourceMonth;
    private String targetMonth;
    private Integer requestedCount;
    private Integer carryOverCount;
    private Integer consumedCount;
    private String status;
    private Long decisionOperatorId;
    private String decisionReason;
    private String capacitySnapshotJson;
    private String carryOverPlanJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
