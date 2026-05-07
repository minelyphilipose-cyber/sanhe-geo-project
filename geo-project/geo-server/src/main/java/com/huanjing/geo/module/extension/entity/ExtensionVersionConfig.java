package com.huanjing.geo.module.extension.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("extension_version_config")
public class ExtensionVersionConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String platform;
    private String minVersion;
    private String latestVersion;
    private String recommendedVersion;
    private Boolean forceUpgrade;
    private String downloadUrl;
    private String releaseNote;
    private String status;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
