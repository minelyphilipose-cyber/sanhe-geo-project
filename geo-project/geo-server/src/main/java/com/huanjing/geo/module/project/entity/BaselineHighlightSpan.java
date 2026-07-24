package com.huanjing.geo.module.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("baseline_highlight_span")
public class BaselineHighlightSpan {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long observationId;
    private String algorithmVersion;
    private String type;
    private String text;
    private Integer startOffset;
    private Integer endOffset;
    private Long normalizedEntityId;
    private LocalDateTime createdAt;
}
