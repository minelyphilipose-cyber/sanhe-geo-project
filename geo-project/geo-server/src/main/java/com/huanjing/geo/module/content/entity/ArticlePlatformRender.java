package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("article_platform_render")
public class ArticlePlatformRender {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long articleId;
    private String platformCode;
    private Long templateId;
    private Long templateVersionId;
    private String annotationsJson;
    private String renderConfigJson;
    private String blockSnapshotJson;
    private String renderedHtmlSnapshot;
    private String status;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
