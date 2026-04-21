package com.huanjing.geo.module.presale.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * POST retry 响应。
 *
 * <p>retry 复用 versionNo(定稿条款:覆盖失败版本数据),因此响应里
 * versionNo 不变,主要告诉前端"状态已切回 QUEUED/RUNNING,可以去轮询进度"。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetryVersionResponse {

    private Long versionId;
    private Integer versionNo;

    /** 重置后的状态,通常是 QUEUED(orchestrator 接手后会转 RUNNING)。 */
    private String generationStatus;
}
