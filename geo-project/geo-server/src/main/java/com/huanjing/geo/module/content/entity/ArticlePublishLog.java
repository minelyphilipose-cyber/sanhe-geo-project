package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("article_publish_log")
public class ArticlePublishLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long articleId;
    private String publishAction;
    private String channelName;
    private String channelUrl;
    private Long operatorId;
    private String note;
    private LocalDateTime createdAt;
}

