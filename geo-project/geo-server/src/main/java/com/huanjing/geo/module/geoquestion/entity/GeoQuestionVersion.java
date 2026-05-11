package com.huanjing.geo.module.geoquestion.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("geo_question_version")
public class GeoQuestionVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workorderId;
    private Long companyId;
    private Long projectId;
    private String versionLabel;
    private String status;
    private Integer countA;
    private Integer countB;
    private Integer countC;
    private Boolean isPartial;
    private String commitMode;
    private String snapshotJson;
    private Long legacyKeywordGroupId;
    private Long committedBy;
    private LocalDateTime committedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
