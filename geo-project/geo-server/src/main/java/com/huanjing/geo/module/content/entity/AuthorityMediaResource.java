package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("authority_media_resource")
public class AuthorityMediaResource {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String resourceType;
    private String externalResourceId;
    private String name;
    private String platform;
    private String industry;
    private String province;
    private BigDecimal price;
    private Integer status;
    private Integer pcWeight;
    private Integer mWeight;
    private Integer newsResource;
    private Integer entranceLevel;
    private Integer includeCondition;
    private Integer publicationTime;
    private Integer weekendPublish;
    private String publishRate;
    private Integer inclusionRate;
    private String remark;
    private Long uptime;
    private String rawPayload;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
