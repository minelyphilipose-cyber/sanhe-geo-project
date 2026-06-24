package com.huanjing.geo.module.mobiledashboard.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mobile_dashboard_access_log")
public class MobileDashboardAccessLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long shareId;
    private Long projectId;
    private String eventType;
    private Boolean success;
    private String failReason;
    private String clientIpMasked;
    private String clientIpHash;
    private String userAgent;
    private LocalDateTime createdAt;
}
