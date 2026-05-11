package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("authority_media_preview_token")
public class AuthorityMediaPreviewToken {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long articleId;
    private String tokenHash;
    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;
    private Integer accessCount;
    private LocalDateTime lastAccessedAt;
    private String lastAccessIp;
    private String lastUserAgent;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
