package com.huanjing.geo.module.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("brand_material")
public class BrandMaterial {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private String category;
    private String fileName;
    private String fileType;
    private String fileUrl;
    private String objectKey;
    private Long fileSize;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
