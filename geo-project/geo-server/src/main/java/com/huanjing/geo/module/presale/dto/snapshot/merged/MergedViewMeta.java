package com.huanjing.geo.module.presale.dto.snapshot.merged;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.huanjing.geo.module.presale.dto.snapshot.common.MatchLevel;
import com.huanjing.geo.module.presale.json.PresaleDateTimeJson;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 合并视图元数据。
 * <p>
 * 字段全部对齐 V62 v4 真实列名和类型(presale_report_version 表)。
 * 前端据此展示版本号、冻结状态、回退提示条、降级标记等全局信息。
 * </p>
 * <p>
 * <b>存储:</b>不落库,每次请求由合并服务从 presale_report_version 行 + 三层 JSON 提取。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MergedViewMeta {

    /** 版本 ID。对应 presale_report_version.id(BIGINT → Long)。 */
    @JsonProperty("version_id")
    private Long versionId;

    /** 报表主表 ID。对应 presale_report_version.report_id(BIGINT)。 */
    @JsonProperty("report_id")
    private Long reportId;

    /** 版本号。对应 presale_report_version.version_no(INT)。 */
    @JsonProperty("version_no")
    private Integer versionNo;

    /** Schema 版本,对应 presale_report_version.schema_version。当前固定 "v1.2"。 */
    @JsonProperty("schema_version")
    private String schemaVersion;

    /**
     * 生成状态。对应 presale_report_version.generation_status(VARCHAR 30)。
     * 取值:INIT/QUEUED/LOADING_PROMPTS/TESTING_ROUND_1/ANALYZING_ROUND_1/
     * COMPETITOR_DETECTION/TESTING_ROUND_2/ANALYZING_ROUND_2/AGGREGATING/
     * FINALIZING/DONE/FAILED。
     * 前端仅在 DONE 时渲染完整报告,其他状态展示生成中 / 失败页。
     * 保留 String,不做 Java enum(取值过多且仍在演化)。
     */
    @JsonProperty("generation_status")
    private String generationStatus;

    /** 是否冻结(frozen_at != null 派生得到,便于前端判断)。 */
    private Boolean frozen;

    /** 冻结时间。对应 frozen_at(DATETIME)。序列化为 RFC3339 带 +08:00。 */
    @JsonProperty("frozen_at")
    @JsonSerialize(using = PresaleDateTimeJson.Serializer.class)
    @JsonDeserialize(using = PresaleDateTimeJson.Deserializer.class)
    private LocalDateTime frozenAt;

    /** 冻结操作人。对应 frozen_by(BIGINT → Long,用户 ID)。 */
    @JsonProperty("frozen_by")
    private Long frozenBy;

    /** 冻结原因。当前阶段固定 MANUAL,预留扩展。 */
    @JsonProperty("frozen_reason")
    private String frozenReason;

    /** L3 最后编辑时间。对应 content_updated_at。序列化为 RFC3339 带 +08:00。 */
    @JsonProperty("content_updated_at")
    @JsonSerialize(using = PresaleDateTimeJson.Serializer.class)
    @JsonDeserialize(using = PresaleDateTimeJson.Deserializer.class)
    private LocalDateTime contentUpdatedAt;

    /** L3 最后编辑人。对应 content_updated_by(BIGINT → Long)。 */
    @JsonProperty("content_updated_by")
    private Long contentUpdatedBy;

    /** 整份报告是否降级。对应 is_degraded(TINYINT)。 */
    @JsonProperty("is_degraded")
    private Boolean isDegraded;

    /**
     * 降级平台 code 列表。对应 degraded_platforms(JSON)。
     * 与 L1.test_summary.degraded_platforms 冗余,此处提升到 meta 便于前端警示条一处读取。
     */
    @JsonProperty("degraded_platforms")
    private List<String> degradedPlatforms;

    /**
     * 基准值匹配等级。从 L1.benchmarks_frozen.match_level 提升到 meta,
     * 便于前端判断是否展示"基准值回退"警示条,无需深入 L1 读取。
     */
    @JsonProperty("match_level")
    private MatchLevel matchLevel;

    /** 导出成功次数。对应 export_success_count。 */
    @JsonProperty("export_success_count")
    private Integer exportSuccessCount;

    /** 最近导出成功时间,可 null。对应 export_success_at。序列化为 RFC3339 带 +08:00。 */
    @JsonProperty("export_success_at")
    @JsonSerialize(using = PresaleDateTimeJson.Serializer.class)
    @JsonDeserialize(using = PresaleDateTimeJson.Deserializer.class)
    private LocalDateTime exportSuccessAt;
}
