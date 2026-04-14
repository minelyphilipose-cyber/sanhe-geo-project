package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("package_publish_config")
public class PackagePublishConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String packageType;
    private String allowedSiteTiers;
    private Integer monthlyPublishLimit;
    private Integer weeklyPublishLimit;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
