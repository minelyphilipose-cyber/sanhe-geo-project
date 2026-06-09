package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wechat_callback_event")
public class WechatCallbackEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String callbackType;
    private String componentAppid;
    private String authorizerAppid;
    private String eventType;
    private String msgType;
    private String openid;
    private String rawXml;
    private String decryptedXml;
    private String responseBody;
    private String processStatus;
    private String processError;
    private LocalDateTime receivedAt;
    private LocalDateTime processedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
