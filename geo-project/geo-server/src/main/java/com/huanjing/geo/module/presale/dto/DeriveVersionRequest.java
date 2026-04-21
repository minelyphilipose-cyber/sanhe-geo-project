package com.huanjing.geo.module.presale.dto;

import lombok.Data;

/**
 * POST /api/presale/reports/{id}/versions/{versionNo}/derive 请求体。
 *
 * <p>v1 不记录派生原因(定稿条款:V62 不扩表)。本 DTO 当前为空壳,
 * 保留类型结构以便未来扩展(如 v2 加 reason / tags 时无需改 Controller 签名)。</p>
 *
 * <p>前端当前可传空 body({});后端忽略任何字段。</p>
 */
@Data
public class DeriveVersionRequest {
    // v1 刻意留空,不要删除此类。
}
