package com.huanjing.geo.module.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("keyword_group_word")
public class KeywordGroupWord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long groupId;
    private String columnType;
    private String wordText;
    private Integer sortOrder;
    private String source;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
