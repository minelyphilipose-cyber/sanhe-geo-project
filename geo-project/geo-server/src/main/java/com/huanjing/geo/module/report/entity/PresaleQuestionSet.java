package com.huanjing.geo.module.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("presale_question_sets")
public class PresaleQuestionSet {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Integer versionNo;
    private String status;
    private Integer questionCount;
    private LocalDateTime generatedAt;
    private LocalDateTime lockedAt;
    private Long lockedBy;
    private LocalDateTime archivedAt;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
