package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("third_party_subject_pool_item")
public class ThirdPartySubjectPoolItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sourceBrandId;
    private Long subjectBrandId;
    private Long subjectProjectId;
    private String matchSource;
    private String matchedIndustry;
    private String coverageTermsSnapshot;
    private LocalDateTime confirmedAt;
    private Long confirmedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
