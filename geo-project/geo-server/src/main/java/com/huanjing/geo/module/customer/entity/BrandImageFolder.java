package com.huanjing.geo.module.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("brand_image_folder")
public class BrandImageFolder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private String folderName;
    private String description;
    private String status;
    @TableField("is_default")
    private Boolean defaultFlag;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
