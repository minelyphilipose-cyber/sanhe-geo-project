package com.huanjing.geo.module.presale.dto.snapshot.editable;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 执行摘要段落(L3)。
 * <p>Schema v1.2 $.editable_content.executive_summary(可为 null)</p>
 * <p>整体可 null;非 null 时 headline + paragraph 两块独立填写。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExecutiveSummary {
    /** 一句话核心结论。 */
    private String headline;
    /** 展开描述段落。 */
    private String paragraph;
}
