package com.huanjing.geo.module.presale.dto.snapshot.editable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 阶段描述(L3)。
 * <p>Schema v1.2 $.editable_content.phase_descriptions[](严格 3 条)</p>
 * <p>通过 phase_no 与 L2.roi_simulation.phases 对应。phase_no 必填,title/description 可 null。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PhaseDescription {

    /** 阶段编号 1/2/3,必填。 */
    @JsonProperty("phase_no")
    private Integer phaseNo;

    /** 阶段标题,如"基础优化阶段",可 null。 */
    private String title;

    /** 阶段描述,可 null。 */
    private String description;
}
