package com.huanjing.geo.module.presale.dto.response;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.huanjing.geo.module.presale.json.PresaleDateTimeJson;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 详情页 VO。详情页右侧 19 页渲染主要消费 mergedViewJson。
 *
 * <p>P1·F·1·a 暂不在后端做 merge(merge 逻辑在 P1·B 前端 TS 工具中),
 * 后端直接返回三层原始 JSON,前端 {@code mergeSnapshot()} 合成 MergedView。
 * P2 可以考虑把 merge 前移到后端统一输出 mergedViewJson。</p>
 */
@Data
@Builder
public class ReportDetailVO {

    private Long reportId;
    private String brandName;
    private List<String> brandFormerNames;
    private String industry;
    private String industryRole;
    private List<String> representedBrands;
    private String region;
    private String userDemand;
    @JsonSerialize(using = PresaleDateTimeJson.Serializer.class)
    @JsonDeserialize(using = PresaleDateTimeJson.Deserializer.class)
    private LocalDateTime createdAt;

    /** 当前查看的版本元信息。 */
    private ReportVersionMetaVO version;

    /** L1 原始快照 JSON 字符串。前端解析后交给 mergeSnapshot。 */
    private String rawSnapshotJson;
    /** L2 计算快照 JSON 字符串。 */
    private String computedSnapshotJson;
    /** L3 编辑快照 JSON 字符串。 */
    private String editableContentJson;
    /** L3 编辑字段元数据,后端为 maxLength / warnLength 权威源。 */
    private List<EditableFieldMetaVO> editableFieldMeta;
}
