package com.huanjing.geo.module.presale.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * L3 编辑字段元数据。后端为长度阈值权威源,前端仅消费该 VO 展示 maxlength / warnLength。
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EditableFieldMetaVO {
    private String field;
    private String label;
    private String block;
    private Integer maxLength;
    private Integer warnLength;
}
