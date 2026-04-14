package com.huanjing.geo.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("system_alerts")
public class SystemAlert {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String alertType;
    private String severity;
    private String source;
    private String message;
    private String contextJson;
    private Boolean isResolved;
    private Long resolvedBy;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
