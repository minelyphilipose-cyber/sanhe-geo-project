package com.huanjing.geo.module.presale.persist.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("presale_benchmark_history")
public class PresaleBenchmarkHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long benchmarkId;
    private String industry;
    private String industryRole;
    private String operation;
    private String beforeSnapshot;
    private String afterSnapshot;
    private Long operatorId;
    private String operatorName;
    private String remark;
    private LocalDateTime createdAt;
}
