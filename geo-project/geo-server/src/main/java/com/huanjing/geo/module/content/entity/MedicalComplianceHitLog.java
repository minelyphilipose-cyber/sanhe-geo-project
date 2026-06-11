package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("medical_compliance_hit_log")
public class MedicalComplianceHitLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long articleId;
    private Long batchId;
    private Long taskId;
    private Long projectId;
    private Long brandId;
    private Long ruleId;
    private String ruleType;
    private String matchedText;
    private String checkStage;
    private String action;
    private LocalDateTime createdAt;
}
