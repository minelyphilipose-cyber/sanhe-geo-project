package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("special_industry_profile")
public class SpecialIndustryProfile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String industryCode;
    private String industryName;
    private String regulatoryDomain;
    private String keywords;
    private String qualificationSchemaJson;
    private String readinessPolicyJson;
    private String promptLabelsJson;
    private Boolean enabled;
    private Integer sortOrder;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
