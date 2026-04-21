package com.huanjing.geo.module.presale.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * POST /api/presale/reports/{id}/versions/{versionNo}/freeze 请求体。
 *
 * <p>reason 可选(对应 Entity 的 {@code frozenReason} 字段,V62 已有)。
 * 前端可以只传空 body,此时 reason 为 null。</p>
 */
@Data
public class FreezeVersionRequest {

    /** 冻结原因,可空。最长 500 字,防误传大文本。 */
    @Size(max = 500, message = "reason length must be <= 500")
    private String reason;
}
