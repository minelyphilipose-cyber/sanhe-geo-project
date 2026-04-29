package com.huanjing.geo.module.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("keyword_group_result")
public class KeywordGroupResult {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long groupId;
    private String keywordText;
    private String sourceType;
    private String seedText;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
