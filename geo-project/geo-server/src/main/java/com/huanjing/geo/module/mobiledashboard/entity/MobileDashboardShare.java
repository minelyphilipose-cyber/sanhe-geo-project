package com.huanjing.geo.module.mobiledashboard.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mobile_dashboard_share")
public class MobileDashboardShare {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String tokenHash;
    private String tokenPrefix;
    private String status;
    private String accessPasswordHash;
    private LocalDateTime expiresAt;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime disabledAt;
    private LocalDateTime lastAccessAt;
    private Long accessCount;
}
