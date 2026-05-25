package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("platform_render_template")
public class PlatformRenderTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String platformCode;
    private String name;
    private String description;
    private String status;
    private Long createdBy;
    @TableField(exist = false)
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
