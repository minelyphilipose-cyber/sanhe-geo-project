package com.huanjing.geo.module.presale.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 列表页单行 VO。
 *
 * <p>对应 UI 定稿列:品牌/行业/身份/地区/状态/冻结/版本/创建时间/操作。
 * "最近导出"作为二级信息放进 latestVersion.exportSuccessAt。</p>
 */
@Data
@Builder
public class ReportListItemVO {

    private Long reportId;
    private String brandName;

    /** 行业 key。前端自行通过字典表翻译为中文展示。 */
    private String industry;
    private String industryRole;

    private String region;

    /** 版本总数(含派生历史)。 */
    private Integer versionCount;

    /** 最新版本元信息。始终非空(创建报告时已经入第一版 INIT)。 */
    private ReportVersionMetaVO latestVersion;

    /** 当前用户是否可对最新版本发起编辑/派生编辑入口。 */
    private Boolean canEdit;

    /** canEdit=false 时的前端 tooltip 文案。 */
    private String canEditReason;

    private LocalDateTime createdAt;
}
