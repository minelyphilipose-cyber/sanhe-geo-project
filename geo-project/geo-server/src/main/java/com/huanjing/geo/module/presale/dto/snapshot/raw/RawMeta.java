package com.huanjing.geo.module.presale.dto.snapshot.raw;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.huanjing.geo.module.presale.json.PresaleDateTimeJson;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * L1 meta 块。
 * <p>Schema v1.2 $.raw_snapshot.meta</p>
 * <p>5 个字段全部 required。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RawMeta {

    /** 报表主表 ID。对应 presale_report.id(BIGINT)。 */
    @JsonProperty("report_id")
    private Long reportId;

    /** 版本号(从 1 递增)。对应 presale_report_version.version_no。 */
    @JsonProperty("version_no")
    private Integer versionNo;

    /**
     * 生成完成时间(整个生成流程结束时刻)。
     * <p><b>类型:</b>{@code LocalDateTime} 对齐 MySQL DATETIME(无偏移量)。
     * <br><b>序列化:</b>字段级 {@link PresaleDateTimeJson.Serializer} 输出 RFC3339 带 +08:00 偏移
     * (如 {@code "2026-04-18T14:05:00+08:00"}),对齐 schema v1.2 {@code format: date-time}。
     * 作用域局限于本字段,不影响全局 LocalDateTime 序列化。</p>
     */
    @JsonProperty("generated_at")
    @JsonSerialize(using = PresaleDateTimeJson.Serializer.class)
    @JsonDeserialize(using = PresaleDateTimeJson.Deserializer.class)
    private LocalDateTime generatedAt;

    /** 生成总耗时(秒)。对应 presale_report_version.duration_seconds 的同源快照。 */
    @JsonProperty("generation_duration_seconds")
    private Integer generationDurationSeconds;

    /** 评分公式版本,如 "v1.0"。公式升级后派生新版本回填。 */
    @JsonProperty("formula_version")
    private String formulaVersion;
}
