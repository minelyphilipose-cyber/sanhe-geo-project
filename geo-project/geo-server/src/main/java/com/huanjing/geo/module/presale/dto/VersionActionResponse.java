package com.huanjing.geo.module.presale.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 版本写动作通用响应体(edit / freeze / unfreeze)。
 *
 * <p>只回传前端需要用来刷新 UI 的最小字段集。完整版本详情仍由 GET /versions/{no}
 * 读模型提供,本响应不要塞满字段。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VersionActionResponse {

    private Long versionId;
    private Integer versionNo;
    private String generationStatus;

    /** 是否冻结,前端据此切换按钮态。 */
    private Boolean frozen;

    /** 仅 freeze 响应回填,其他操作为 null。 */
    private LocalDateTime frozenAt;

    /** 操作后最新的 updatedAt / contentUpdatedAt,用于乐观锁或展示。 */
    private LocalDateTime updatedAt;
}
