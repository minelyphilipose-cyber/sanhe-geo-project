package com.huanjing.geo.module.geoquestion.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("geo_question_workorder")
public class GeoQuestionWorkorder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long companyId;
    private Long projectId;
    private Long packageBindingId;
    private String packageName;
    private String status;
    private String versionLabel;
    private Integer targetA;
    private Integer targetB;
    private Integer targetC;
    private Long committedVersionId;
    private Long legacyKeywordGroupId;
    private Integer versionNo;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
