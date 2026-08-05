package com.huanjing.geo.module.presale.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 版本元信息 VO。用于列表页行内"最新版本"信息 + 版本管理页时间线 + 进度页轮询。
 */
@Data
@Builder
public class ReportVersionMetaVO {
    private Long versionId;
    private Integer versionNo;

    /** INIT / QUEUED / RUNNING / DONE / FAILED。 */
    private String generationStatus;
    private String generationStage;
    private String queryWebMode;

    private Integer totalLlmCalls;
    private Integer completedLlmCalls;
    private Integer batch1TotalCalls;
    private Integer batch1CompletedCalls;
    private Integer batch2TotalCalls;
    private Integer batch2CompletedCalls;
    private Integer extractedCompetitorCount;
    private Integer plannedQueryCount;
    private Integer plannedWebQueryCount;
    private Integer webValidQueryCount;
    private Integer effectiveSampleCount;
    private Integer queryFailedCount;
    private Integer analyzeFailedCount;
    private Integer skippedQueryCount;
    private Integer degradedExcludedSampleCount;
    private String mainWebFailureCode;

    private Boolean isDegraded;
    private List<String> degradedPlatforms;

    private String failureReason;

    /** 是否冻结(frozen_at != null)。 */
    private Boolean frozen;
    private LocalDateTime frozenAt;

    /** 内容最后编辑时间(L3)。 */
    private LocalDateTime contentUpdatedAt;

    private Integer exportSuccessCount;
    private LocalDateTime exportSuccessAt;

    private LocalDateTime createdAt;
}
