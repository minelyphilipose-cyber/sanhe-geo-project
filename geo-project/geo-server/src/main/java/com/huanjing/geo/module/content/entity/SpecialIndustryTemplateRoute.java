package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("special_industry_template_route")
public class SpecialIndustryTemplateRoute {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String industryCode;
    private String channelGroupCode;
    private String channelSubCode;
    private String accountIdentity;
    private String templateName;
    private Integer priority;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
