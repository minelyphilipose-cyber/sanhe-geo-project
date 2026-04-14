package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("article_draft_version")
public class ArticleDraftVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long articleId;
    private Integer versionNo;
    private String title;
    private String contentMarkdown;
    private String promptSnapshot;
    private String inputSnapshot;
    private String modelPlatformCode;
    private String modelId;
    private String generatedBy;
    private Long createdBy;
    private LocalDateTime createdAt;
}

