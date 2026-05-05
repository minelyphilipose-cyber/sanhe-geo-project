package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("self_media_material_mapping")
public class SelfMediaMaterialMapping {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long selfMediaAccountId;
    private Long brandMaterialId;
    private String contentHash;
    private String mediaType;
    private String platformMediaId;
    private String platformUrl;
    private String extraJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
