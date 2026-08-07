package com.huanjing.geo.module.presale.dto.snapshot.raw;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 客户填报信息冻结副本。
 * <p>Schema v1.2 $.raw_snapshot.client_info</p>
 * <p>
 * Required: brand_name, industry, industry_role, region。
 * user_demand 可选(v1.2 变更,null 或省略均合法)。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClientInfo {

    /** 品牌名(maxLength 200)。 */
    @JsonProperty("brand_name")
    private String brandName;

    /** 品牌曾用名,仅用于竞品统计排除,不参与本品牌提及判断。 */
    @JsonProperty("brand_former_names")
    private List<String> brandFormerNames;

    /** 行业枚举值,来源 sys_dict_item(dict_type='presale_industry')。 */
    private String industry;

    /** 身份枚举值,来源 sys_dict_item(dict_type='presale_industry_role')。 */
    @JsonProperty("industry_role")
    private String industryRole;

    /** 代理/经销类客户所代理的上游品牌；仅作归属消歧，不作为目标品牌提及。 */
    @JsonProperty("represented_brands")
    private List<String> representedBrands;

    /** STANDARD / DEALER；旧快照缺失时按 STANDARD。 */
    @JsonProperty("attribution_mode")
    private String attributionMode;

    /** 创建版本时冻结的中文角色名称。 */
    @JsonProperty("matched_role_name")
    private String matchedRoleName;

    /** 区域(如"北京市")。v1.2 必填业务字段。 */
    private String region;

    /**
     * 需求说明,可为 null 或省略(v1.2 变更为可选,maxLength 2000)。
     * 数据库 NULL 时 JSON 快照可存 null 或缺失键,序列化用 @JsonInclude(NON_NULL) 统一。
     */
    @JsonProperty("user_demand")
    private String userDemand;
}
