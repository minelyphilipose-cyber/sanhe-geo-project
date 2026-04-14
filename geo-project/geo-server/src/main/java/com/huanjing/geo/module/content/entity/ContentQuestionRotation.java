package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("content_question_rotation")
public class ContentQuestionRotation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String articleType;
    private Integer currentOffset;
    private LocalDateTime updatedAt;
}
