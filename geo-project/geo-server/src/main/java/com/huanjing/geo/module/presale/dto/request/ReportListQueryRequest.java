package com.huanjing.geo.module.presale.dto.request;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;

/**
 * 列表页查询参数。所有字段均可为 null(不过滤)。
 *
 * <p>日期字段采用 RFC3339 含时区,使用 {@link OffsetDateTime} 绑定,
 * Spring 默认会用 ISO 解析器读取 +08:00 offset。前端用
 * {@code toRfc3339Range()} 工具生成 {@code 2026-04-18T00:00:00+08:00} 格式。</p>
 *
 * <p>Codex P1·F·1·a 审阅 P2 修复:之前用 LocalDateTime 不感知 offset,
 * 会在 Jackson 解析时丢失时区信息,改为 OffsetDateTime + ISO_DATE_TIME 格式显式声明。</p>
 */
@Data
public class ReportListQueryRequest {

    private Integer page = 1;
    private Integer pageSize = 20;

    /** 品牌名关键字,模糊匹配。 */
    private String keyword;

    private String industry;
    private String industryRole;

    /** 生成状态:INIT / QUEUED / RUNNING / DONE / FAILED。 */
    private String generationStatus;

    /** 冻结状态:null=全部, true=已冻结, false=未冻结。 */
    private Boolean frozen;

    /** 创建时间起点(含),RFC3339 with offset 如 2026-04-01T00:00:00+08:00。 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private OffsetDateTime startAt;

    /** 创建时间终点(含),RFC3339 with offset。 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private OffsetDateTime endAt;

    /** 排序字段,默认 createdAt。 */
    private String sortBy = "createdAt";

    /** 排序方向,默认 desc。 */
    private String sortDir = "desc";
}
