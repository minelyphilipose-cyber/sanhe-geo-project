package com.huanjing.geo.module.presale.persist.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("presale_ai_prompt_result")
public class PresaleAiPromptResult {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long versionId;
    private Integer batchNo;
    private String platformCode;
    private Long promptTemplateId;
    private String competitorName;
    private Long queryCallId;
    private Long analyzeCallId;
    private Boolean effectiveSample;
    private String requestPromptContent;
    private Integer isMentioned;
    private Integer targetEntityHit;
    private Integer representedBrandHit;
    private Integer targetBrandRelationHit;
    private String attributionType;
    private Integer ranking;
    private String sentiment;
    private String mentionedCompetitors;
    private String sceneAdvantages;
    private String topKeywordsJson;
    private String negativeEvidenceJson;
    private LocalDateTime createdAt;
}
