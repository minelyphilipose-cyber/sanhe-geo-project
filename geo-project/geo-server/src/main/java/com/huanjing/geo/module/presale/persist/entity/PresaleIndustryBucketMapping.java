package com.huanjing.geo.module.presale.persist.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("presale_industry_bucket_mapping")
public class PresaleIndustryBucketMapping {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String industry;
    private String industryKey;
    private String bucketCode;
    private String industryShort;
    private Boolean approved;
    private String source;
    private Long originTaskId;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private String configVersion;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
