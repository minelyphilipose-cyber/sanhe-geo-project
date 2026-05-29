package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("brand_channel_template_perspective")
public class BrandChannelTemplatePerspective {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private String channelGroupCode;
    private String channelSubCode;
    private String perspectiveCode;
    private Boolean enabled;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
