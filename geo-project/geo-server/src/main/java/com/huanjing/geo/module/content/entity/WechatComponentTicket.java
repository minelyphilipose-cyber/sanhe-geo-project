package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wechat_component_ticket")
public class WechatComponentTicket {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String componentAppid;
    private String componentVerifyTicketCipher;
    private LocalDateTime receivedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
