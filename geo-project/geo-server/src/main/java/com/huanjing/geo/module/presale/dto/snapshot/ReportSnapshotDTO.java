package com.huanjing.geo.module.presale.dto.snapshot;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.huanjing.geo.module.presale.dto.snapshot.computed.ComputedSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.editable.EditableContentDTO;
import com.huanjing.geo.module.presale.dto.snapshot.raw.RawSnapshotDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 完整三层快照。
 * <p>Schema v1.2 根对象</p>
 * <p>
 * <b>用途(决策 4A):</b>
 * <ul>
 *   <li>ops 调试接口:{@code GET /api/presale/versions/{versionNo}/raw-snapshot} 返回此完整对象,
 *       便于线上问题排查时看到三层原始数据</li>
 *   <li>Schema 校验:整体对象可被 JSON Schema v1.2 校验(根 required 字段对齐)</li>
 *   <li>版本派生:派生服务读取此对象复制 raw/computed/editable 三个 JSON 字段</li>
 * </ul>
 * </p>
 * <p>
 * <b>非用途:</b>前端常规消费<b>不走</b>此 DTO,走 {@code MergedViewDTO}。
 * </p>
 * <p>
 * <b>schema_version:</b>固定 "v1.2",未来升级时用于兼容判断。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReportSnapshotDTO {

    /** Schema 版本号,固定 "v1.2"。 */
    @JsonProperty("schema_version")
    private String schemaVersion;

    /** L1 原始事实层。 */
    @JsonProperty("raw_snapshot")
    private RawSnapshotDTO rawSnapshot;

    /** L2 计算结果层。 */
    @JsonProperty("computed_snapshot")
    private ComputedSnapshotDTO computedSnapshot;

    /** L3 可编辑文案层。 */
    @JsonProperty("editable_content")
    private EditableContentDTO editableContent;
}
