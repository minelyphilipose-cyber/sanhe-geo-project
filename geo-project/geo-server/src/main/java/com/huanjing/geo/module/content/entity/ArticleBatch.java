package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("article_batch")
public class ArticleBatch {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long dispatchTaskId;
    private Long projectId;
    private String targetChannel;
    private Integer generationSlotNo;
    private LocalDate batchDate;
    private Integer batchNo;
    private String status;
    private Integer totalCount;
    private Integer completedCount;
    private Integer failedCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
