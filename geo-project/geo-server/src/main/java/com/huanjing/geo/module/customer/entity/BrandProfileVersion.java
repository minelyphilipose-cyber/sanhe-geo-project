package com.huanjing.geo.module.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("brand_profile_version")
public class BrandProfileVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private Integer versionNo;
    private String snapshotJson;
    private String changeReason;
    private Long createdBy;
    private LocalDateTime createdAt;
}
