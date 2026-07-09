package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("article_publish_record")
public class ArticlePublishRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long articleId;
    private Long distributionTaskId;
    private Long projectId;
    private Long selfMediaAccountId;
    private Long brandId;
    private String sourceType;
    private Long sourceId;
    private String targetKind;
    private String targetChannel;
    private String publishedUrl;
    private String urlQuality;
    private String urlSource;
    private String platformArticleId;
    private String platformPublishId;
    private String publishStatus;
    private String title;
    private String coverUrl;
    private String digest;
    private String rawResponse;
    private LocalDateTime publishedAt;
    private LocalDateTime verifiedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
