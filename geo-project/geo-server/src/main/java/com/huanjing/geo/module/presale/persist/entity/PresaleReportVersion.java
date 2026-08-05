package com.huanjing.geo.module.presale.persist.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 售前报告版本,对应 {@code presale_report_version}(V62 v4)。
 *
 * <p>存储 v1.2 契约三层快照 JSON 字段(raw/computed/editable)。P1·F·1·a 阶段
 * 快照字段暂存 mock 值(由 MockOrchestrator 从 fixture 注入)。</p>
 */
@Data
@TableName("presale_report_version")
public class PresaleReportVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reportId;

    /** 版本号,同一 report_id 内递增(v1, v2, v3 ...)。 */
    private Integer versionNo;

    /** 派生自哪个版本,初版为 null。 */
    private Long derivedFromVersionId;

    /**
     * 生成状态:INIT / QUEUED / RUNNING / DONE / FAILED。
     * 对应 {@link com.huanjing.geo.module.presale.generate.PresaleGenerateStatus}。
     */
    private String generationStatus;
    /** 生成子阶段(BATCH1/COMPETITOR_EXTRACT/BATCH2/L1_AGGREGATE/L2_COMPUTE/L3_INIT)。 */
    private String generationStage;

    /**
     * 每次从 QUEUED 原子领取为 RUNNING 时递增。
     * 异步任务必须携带领取时的值，避免上一轮延迟返回后污染新一轮生成。
     */
    private Long generationAttempt;

    /** Version-level QUERY contract mode, fixed when a generation run starts. */
    private String queryWebMode;

    /** 总 LLM 调用数,v1.2 契约 11×30×2 = 660。 */
    private Integer totalLlmCalls;

    /** 已完成 LLM 调用数,进度页展示。 */
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

    /** 是否降级(部分平台失败)。 */
    private Boolean isDegraded;

    /**
     * 降级平台 code 列表,JSON 数组字符串存储。
     * <p>DB 列名 {@code degraded_platforms}(V62 DDL,不带 _json 后缀)。
     * MyBatis-Plus 默认驼峰转下划线 degradedPlatforms → degraded_platforms,
     * 这里字段名必须与列名对齐,不能叫 degradedPlatformsJson 否则会被映射为
     * 不存在的列 degraded_platforms_json。</p>
     */
    private String degradedPlatforms;

    /** 失败原因,FAILED 状态时填。 */
    private String failureReason;
    /** 失败类别(枚举编码),FAILED 状态时填。 */
    private String failureCategory;

    /** L1 原始快照 JSON(v1.2 raw_snapshot_json)。 */
    private String rawSnapshotJson;

    /** L2 计算快照 JSON(v1.2 computed_snapshot_json)。 */
    private String computedSnapshotJson;

    /** L3 编辑快照 JSON(v1.2 editable_content_json)。 */
    private String editableContentJson;

    /** 冻结时间,未冻结为 null。 */
    private LocalDateTime frozenAt;

    /** 冻结人。 */
    private Long frozenBy;

    /** 冻结原因(V62 已有字段,v1 可为空)。 */
    private String frozenReason;

    /** 内容最后编辑时间(L3 patch)。 */
    private LocalDateTime contentUpdatedAt;

    /** 成功导出 PDF 次数。 */
    private Integer exportSuccessCount;

    /** 最近导出时间。 */
    private LocalDateTime exportSuccessAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Long createdBy;
}
