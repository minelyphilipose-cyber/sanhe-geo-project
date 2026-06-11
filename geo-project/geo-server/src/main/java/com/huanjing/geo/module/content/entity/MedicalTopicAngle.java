package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("medical_topic_angle")
public class MedicalTopicAngle {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String industryCode;
    private String industryName;
    private String categoryCode;
    private String categoryName;
    private String topicAngle;
    private String recommendedFocus;
    private Boolean enabled;
    private Integer sortOrder;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
