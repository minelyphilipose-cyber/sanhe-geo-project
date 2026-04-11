package com.huanjing.geo.module.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("project")
public class Project {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String projectCode;
    private Long brandId;
    private String projectName;
    private String packageType;
    private Long packagePrice;
    private Integer serviceMonths;
    private String status;
    private String stage;
    private String ownerType;
    private Long partnerId;
    private String deliveryMode;
    private LocalDateTime signedAt;
    private LocalDate startDate;
    private LocalDate endDate;
    private String primaryGoal;
    private Long createdBy;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
