package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("browser_environment")
public class BrowserEnvironment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private String provider;
    private String environmentKey;
    private String providerProfileId;
    private String name;
    private String status;
    private LocalDateTime lastStartedAt;
    private LocalDateTime lastStoppedAt;
    private String lastErrorCode;
    private String lastErrorMessage;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic(value = "null", delval = "NOW()")
    private LocalDateTime deletedAt;
}
