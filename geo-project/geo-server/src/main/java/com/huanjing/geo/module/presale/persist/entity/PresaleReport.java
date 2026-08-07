package com.huanjing.geo.module.presale.persist.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 售前报告主表,对应 {@code presale_report}(V62 v4)。
 *
 * <p>一个 report 可以有多个 version(派生链),latest_version_id 始终指向最新有效版本。</p>
 */
@Data
@TableName("presale_report")
public class PresaleReport {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 品牌名,业务唯一性由上层 Service 校验(不加 DB unique)。 */
    private String brandName;

    /** 品牌曾用名 JSON 数组。仅用于竞品统计排除。 */
    private String brandFormerNames;

    /** 行业字典 key(如 restaurant),值必须存在于 sys_dict_item(presale_industry)。 */
    private String industry;

    /** 身份字典 key(如 chain_brand)。 */
    private String industryRole;

    /** 代理/经销类客户所代理的品牌 JSON 数组；不作为目标品牌提及。 */
    private String representedBrands;

    /** STANDARD / DEALER。创建时按中文角色名称与代理品牌冻结。 */
    private String attributionMode;

    /** 创建时解析出的中文角色名称，避免后续字典变更影响身份口径。 */
    private String matchedRoleName;

    private String region;

    /** 客户诉求,可选。 */
    private String userDemand;

    /** 目标用户/消费群体,可选。 */
    private String userType;

    /** 客户指定竞品 JSON 数组。为空时生成链路自动识别竞品。 */
    private String specifiedCompetitors;

    /** 指向最新版本,初始为 null,第一版创建后更新。 */
    @TableField("current_version_id")
    private Long latestVersionId;

    @TableField("current_version_no")
    private Integer currentVersionNo;

    /** DRAFT / GENERATING / DONE / FAILED / ARCHIVED。 */
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** 创建人(sys_user.id),由当前登录上下文注入。 */
    private Long createdBy;

    /** 软删除时间。删除后的报告不再出现在列表/详情入口。 */
    private LocalDateTime deletedAt;

    /** 软删除操作人。 */
    private Long deletedBy;

    private Long partnerId;

    private String partnerPresaleChargeType;

    private BigDecimal partnerPresalePoints;

    private Long partnerPresaleQuotaTxnId;

    private Long partnerPresalePointsTxnId;

    private String requestId;

    private String requestHash;

    private String requestPayloadSnapshotJson;
}
