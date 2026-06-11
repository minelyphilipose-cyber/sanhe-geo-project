package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("medical_generation_history")
public class MedicalGenerationHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long brandId;
    private Long topicAngleId;
    private String structureSkeleton;
    private String focus;
    private Long articleId;
    private LocalDateTime createdAt;
}
