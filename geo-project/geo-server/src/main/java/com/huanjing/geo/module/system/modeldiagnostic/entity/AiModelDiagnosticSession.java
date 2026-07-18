package com.huanjing.geo.module.system.modeldiagnostic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_model_diagnostic_sessions")
public class AiModelDiagnosticSession {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sessionId;
    private Long operatorId;
    private String status;
    private Integer nextTurnNo;
    private LocalDateTime lastRunAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
