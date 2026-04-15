package com.huanjing.geo.module.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("keyword_group")
public class KeywordGroup {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String type;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
