package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("package_content_config")
public class PackageContentConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String packageType;
    private String articleType;
    private Integer articlesPerBatch;
    private Integer questionsPerArticle;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

