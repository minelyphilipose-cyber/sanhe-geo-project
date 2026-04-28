package com.huanjing.geo.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("keyword_affix_word")
public class KeywordAffixWord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String type;
    private String affixKind;
    private String subCategory;
    private String visualTag;
    private String industryTag;
    private String wordText;
    private Integer sortOrder;
    private Boolean enabled;
    private Boolean isManual;
    private Boolean isTemporary;
    private String scopeType;
    private Long scopeId;
    private LocalDateTime lastUsedAt;
    private Long addedByUserId;
    private String approvalStatus;
    private String approvalReason;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
