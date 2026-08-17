package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("self_media_auth_work_order_event")
public class SelfMediaAuthWorkOrderEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workOrderId;
    private String action;
    private String fromStatus;
    private String toStatus;
    private String note;
    private Long operatorId;
    private LocalDateTime createdAt;
}
