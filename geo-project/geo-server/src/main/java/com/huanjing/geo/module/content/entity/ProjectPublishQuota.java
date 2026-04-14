package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("project_publish_quota")
public class ProjectPublishQuota {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String quotaMonth;
    private Integer usedCount;
    private Integer monthlyLimit;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
