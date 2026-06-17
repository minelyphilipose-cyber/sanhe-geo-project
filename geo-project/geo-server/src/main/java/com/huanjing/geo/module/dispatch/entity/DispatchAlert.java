package com.huanjing.geo.module.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dispatch_alert")
public class DispatchAlert {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String alertCode;
    private Long taskId;
    private Long projectId;
    private String dedupeKey;
    private String severity;
    private String status;
    private String title;
    private String content;
    private Integer retryCount;
    private String contextJson;
    private LocalDateTime resolvedAt;
    private Long resolvedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
