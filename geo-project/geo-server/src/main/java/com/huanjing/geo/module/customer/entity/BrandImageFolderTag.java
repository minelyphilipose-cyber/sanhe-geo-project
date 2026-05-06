package com.huanjing.geo.module.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("brand_image_folder_tag")
public class BrandImageFolderTag {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long folderId;
    private String tagName;
    private LocalDateTime createdAt;
}
