package com.huanjing.geo.module.project.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class PartnerSubmissionReadinessVO {
    private Long companyId;
    private String companyName;
    private Boolean ready;
    private Integer totalCount;
    private Integer readyCount;
    private Integer pendingCount;
    private LocalDateTime checkedAt;
    private List<PartnerSubmissionReadinessItemVO> items = new ArrayList<>();
}
