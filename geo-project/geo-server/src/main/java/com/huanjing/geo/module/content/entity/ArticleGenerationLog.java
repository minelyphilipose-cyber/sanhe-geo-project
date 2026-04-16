package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("article_generation_log")
public class ArticleGenerationLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String articleType;
    private String articleAngle;
    private String generatedTitle;
    private String modelCode;
    private LocalDateTime createdAt;
}
