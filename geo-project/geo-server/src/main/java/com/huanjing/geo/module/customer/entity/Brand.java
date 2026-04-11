package com.huanjing.geo.module.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("brand")
public class Brand {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long companyId;
    private String brandName;
    private String brandSlug;
    private String mainBusiness;
    private String serviceArea;
    private String website;
    private String phone;
    private String wechat;
    private String description;
    private String standardBrandStatement;
    private String forbiddenPhrases;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
