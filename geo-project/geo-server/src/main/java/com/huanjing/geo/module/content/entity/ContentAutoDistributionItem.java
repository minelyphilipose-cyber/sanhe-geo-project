package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("content_auto_distribution_item")
public class ContentAutoDistributionItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long batchId;
    private Long projectId;
    private Long companyId;
    private Long brandId;
    private LocalDate planDate;
    private String channelCode;
    private String channelGroupCode;
    private String contentStyle;
    private String targetKind;
    private Long targetId;
    private String targetName;
    private Long targetBrandId;
    private Integer targetForumFid;
    private Integer sequenceNo;
    private Long questionId;
    private String questionText;
    private Long generationBatchId;
    private Long generationTaskId;
    private Long articleId;
    private Long publishJobId;
    private Long publishItemId;
    private Long selfMediaScheduleId;
    private LocalDateTime plannedPublishAt;
    private String status;
    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
