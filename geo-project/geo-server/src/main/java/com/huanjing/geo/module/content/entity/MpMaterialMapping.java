package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mp_material_mapping")
public class MpMaterialMapping {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long mpAccountId;
    private Long brandMaterialId;
    private String contentHash;
    private String mediaType;
    private String mediaId;
    private String wechatUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
