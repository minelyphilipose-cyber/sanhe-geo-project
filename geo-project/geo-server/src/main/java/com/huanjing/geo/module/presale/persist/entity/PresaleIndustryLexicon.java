package com.huanjing.geo.module.presale.persist.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("presale_industry_lexicon")
public class PresaleIndustryLexicon {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String industry;
    private String customerTerm;
    private String conversionTerm;
    private String industryShort;
    private Boolean approved;
    private String source;
    private String configVersion;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
