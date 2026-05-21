package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("article_prompt_template")
public class ArticlePromptTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private String channelGroupCode;
    private String channelSubCode;
    private String agentSiteModule;
    private String articleTypeCode;
    private String questionSceneCode;
    private Integer weight;
    private Integer sortOrder;
    private String status;
    private String sampleOutputUrl;
    private String contactDisclosureMode;
    private Long currentVersionId;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
