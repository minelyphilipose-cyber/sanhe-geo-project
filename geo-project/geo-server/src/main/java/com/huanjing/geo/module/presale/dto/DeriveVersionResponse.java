package com.huanjing.geo.module.presale.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * POST derive 响应。
 *
 * <p>前端需要 3 个信息:① 新版本 id ② 新版本号 ③ 派生后 latestVersionId 指向
 * (定稿条款:派生后 report.latestVersionId 自动切到新版)。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeriveVersionResponse {

    private Long newVersionId;
    private Integer newVersionNo;
    private Long sourceVersionId;
    private Integer sourceVersionNo;

    /** 派生后 report 表的 latest_version_id(应 = newVersionId)。 */
    private Long latestVersionId;
}
