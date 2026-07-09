package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wechat_menu_config")
public class WechatMenuConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long selfMediaAccountId;
    private Long brandId;
    private String authorizerAppid;
    private String publicSlug;
    private String menuName;
    private String menuStatus;
    private String listPageUrl;
    private String backupMenuJson;
    private LocalDateTime backupMenuAt;
    private LocalDateTime lastSyncAt;
    private String lastSyncError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
