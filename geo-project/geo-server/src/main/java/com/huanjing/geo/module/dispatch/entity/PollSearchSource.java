package com.huanjing.geo.module.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("poll_search_sources")
public class PollSearchSource {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long attemptId;
    private Long providerCallId;
    private Integer searchEventIndex;
    private Integer rankNo;
    private String queryText;
    private String title;
    private String originalUrl;
    private String normalizedUrl;
    private String domain;
    private String snippet;
    private LocalDateTime publishTime;
    private Boolean brandMatched;
    private String brandMatchStrength;
    private String matchedKeywordsJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
