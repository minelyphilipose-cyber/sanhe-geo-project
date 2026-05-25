package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("platform_render_template_version")
public class PlatformRenderTemplateVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long templateId;
    private Integer versionNo;
    private String sourceType;
    private String sourceHtml;
    private String templateSchemaJson;
    private String sanitizedPreviewHtml;
    private Long createdBy;
    private LocalDateTime createdAt;
}
