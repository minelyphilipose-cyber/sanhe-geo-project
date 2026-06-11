package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("medical_compliance_kernel")
public class MedicalComplianceKernel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String industryCode;
    private String channelTier;
    private String kernelName;
    private String systemPrompt;
    private Integer brandExposureLimit;
    private Boolean requireManualPublishReview;
    private Boolean enabled;
    private Integer versionNo;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
