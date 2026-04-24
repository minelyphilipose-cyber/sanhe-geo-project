package com.huanjing.geo.module.presale.persist.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("presale_prompt_template")
public class PresalePromptTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String promptCode;
    private String industry;
    private String industryRole;
    private String category;
    private String businessValue;
    private String promptContent;
    private Integer hasCompetitorVar;
    private Integer enabled;
    private String templateVersion;
    private Integer sortOrder;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
