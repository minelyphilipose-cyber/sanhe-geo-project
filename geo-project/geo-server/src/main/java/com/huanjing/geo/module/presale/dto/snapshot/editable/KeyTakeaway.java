package com.huanjing.geo.module.presale.dto.snapshot.editable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 关键发现条目(L3)。
 * <p>Schema v1.2 $.editable_content.key_takeaways[]</p>
 * <p>order_no / title / description 全部 required。运营可改 title/description。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KeyTakeaway {
    /** 排序序号(1 起)。 */
    @JsonProperty("order_no")
    private Integer orderNo;
    private String title;
    private String description;
}
