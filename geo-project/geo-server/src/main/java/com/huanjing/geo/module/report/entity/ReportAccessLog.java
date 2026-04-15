package com.huanjing.geo.module.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("report_access_logs")
public class ReportAccessLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long reportId;
    private String shareToken;
    private LocalDateTime accessAt;
    private String ipAddress;
    private String userAgent;
    private Boolean passwordVerified;
    private String referer;
}
