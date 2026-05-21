package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("article_prompt_template_version")
public class ArticlePromptTemplateVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long templateId;
    private Integer versionNo;
    private String systemPrompt;
    private String userPromptTemplate;
    private String variablesJson;
    private String qualityRulesJson;
    private String status;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;
}
