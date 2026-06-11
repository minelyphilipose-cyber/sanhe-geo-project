package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("medical_compliance_rule")
public class MedicalComplianceRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String ruleType;
    private String industryCode;
    private String channelTier;
    private String channelGroupCode;
    private String channelSubCode;
    private String pattern;
    private String matchMode;
    private String severity;
    private Boolean enabled;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
