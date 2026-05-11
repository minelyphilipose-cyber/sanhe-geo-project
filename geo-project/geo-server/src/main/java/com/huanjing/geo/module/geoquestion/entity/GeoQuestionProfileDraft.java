package com.huanjing.geo.module.geoquestion.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("geo_question_profile_draft")
public class GeoQuestionProfileDraft {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workorderId;
    private String profileJson;
    private Boolean syncToCustomerProfile;
    private String validationStatus;
    private LocalDateTime autoSavedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
