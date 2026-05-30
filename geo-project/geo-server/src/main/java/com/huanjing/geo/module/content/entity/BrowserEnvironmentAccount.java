package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("browser_environment_account")
public class BrowserEnvironmentAccount {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private Long browserEnvironmentId;
    private Long selfMediaAccountId;
    private String platform;
    private String expectedPlatformAccountId;
    private String expectedAccountName;
    private String loginStatus;
    private LocalDateTime lastVerifiedAt;
    private LocalDateTime lastLoginSeenAt;
    private String lastErrorCode;
    private String lastErrorMessage;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic(value = "null", delval = "NOW()")
    private LocalDateTime deletedAt;
}
