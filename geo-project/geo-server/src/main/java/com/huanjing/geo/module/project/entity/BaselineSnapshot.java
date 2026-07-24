package com.huanjing.geo.module.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("baseline_snapshot")
public class BaselineSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long companyId;
    private Long brandId;
    private Integer runSeq;
    private String status;
    private String schemaVersion;
    private String intentRubricVersion;
    private String algorithmVersionsJson;
    private String selectedVersionsJson;
    private Long sourcePollBatchId;
    private LocalDateTime sealedAt;
    private Long sealedBy;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
