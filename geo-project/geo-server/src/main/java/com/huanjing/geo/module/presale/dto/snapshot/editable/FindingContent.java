package com.huanjing.geo.module.presale.dto.snapshot.editable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 优化发现的对客文案(L3)。
 * <p>Schema v1.2 $.editable_content.optimization_findings_content[]</p>
 * <p>
 * 通过 {@link #findingId} 与 L2 {@code optimization_findings[i].finding_id} 一一对应。
 * <br>仅 finding_id 必填,其余字段均可 null(回退到规则模板生成的默认文案)。
 * </p>
 * <p>
 * <b>is_hidden 的语义:</b>L3 唯一的隐藏控制(L3 整体<b>没有</b>通用的模块隐藏/排序能力,
 * 只有 optimization_findings_content 这一处支持条目级的 is_hidden + sort_order)。
 * merged view 中 {@code is_hidden=true} 的条目会被跳过,PDF 也不展示。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FindingContent {

    /** 必填,关联 L2 的 finding_id(如 "F001")。 */
    @JsonProperty("finding_id")
    private String findingId;

    /** 对客展示的发现标题,可 null 回退默认模板。 */
    private String title;

    /** 展开描述,可 null。 */
    private String description;

    /** 证据文字描述(由 L2.evidence_data 默认渲染,L3 可覆盖)。 */
    @JsonProperty("evidence_text")
    private String evidenceText;

    /** 运营自定义排序,可 null(null 时保持 L2 原序)。 */
    @JsonProperty("sort_order")
    private Integer sortOrder;

    /**
     * 是否隐藏(默认 false)。
     * true 时此条发现不出现在 merged view 和 PDF 中。schema default=false,
     * 反序列化时 null 视作 false,合并服务负责补默认值。
     */
    @JsonProperty("is_hidden")
    private Boolean isHidden;
}
