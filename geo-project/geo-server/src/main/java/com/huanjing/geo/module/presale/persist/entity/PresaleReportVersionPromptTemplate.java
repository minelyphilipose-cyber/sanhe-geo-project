package com.huanjing.geo.module.presale.persist.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("presale_report_version_prompt_template")
public class PresaleReportVersionPromptTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long reportId;
    private Long reportVersionId;
    private Long sourceTemplateId;
    private String sourcePromptCode;
    private String sourceTemplateVersion;
    private String sourceType;
    private String category;
    private String businessValue;
    private String promptContent;
    private Integer hasCompetitorVar;
    private Integer sortOrderInVersion;
    private String remark;
    private Integer isUserAdded;
    private LocalDateTime createdAt;
}
