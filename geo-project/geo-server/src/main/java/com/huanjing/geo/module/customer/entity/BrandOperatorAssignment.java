package com.huanjing.geo.module.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("brand_operator_assignment")
public class BrandOperatorAssignment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private Long operatorId;
    private String role;
    private String status;
    private LocalDateTime assignedAt;
    private Long assignedBy;
}
