package com.huanjing.geo.module.presale.persist.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("presale_industry_lexicon_review_task")
public class PresaleIndustryLexiconReviewTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String industry;
    private String draftJson;
    private String status;
    private String source;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
