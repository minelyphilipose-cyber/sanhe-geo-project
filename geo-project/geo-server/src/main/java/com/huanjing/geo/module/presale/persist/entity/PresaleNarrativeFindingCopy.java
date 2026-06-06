package com.huanjing.geo.module.presale.persist.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("presale_narrative_finding_copy")
public class PresaleNarrativeFindingCopy {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String configVersion;
    private String code;
    private String tier;
    private String bandOverride;
    private String archetypeOverride;
    private String titleTemplate;
    private String bodyTemplate;
    private String evidenceTemplate;
    private Integer priority;
    private Boolean enabled;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
