package com.huanjing.geo.module.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("project_customer_requirement")
public class ProjectCustomerRequirement {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String requirementText;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
