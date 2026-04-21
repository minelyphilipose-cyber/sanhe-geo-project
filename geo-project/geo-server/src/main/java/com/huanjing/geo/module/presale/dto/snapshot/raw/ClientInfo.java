package com.huanjing.geo.module.presale.dto.snapshot.raw;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    /** 行业枚举值,来源 sys_dict_item(dict_type='presale_industry')。 */
    private String industry;

    /** 身份枚举值,来源 sys_dict_item(dict_type='presale_industry_role')。 */
    @JsonProperty("industry_role")
    private String industryRole;

    /** 区域(如"北京市")。v1.2 必填业务字段。 */
    private String region;

    /**
     * 需求说明,可为 null 或省略(v1.2 变更为可选,maxLength 2000)。
     * 数据库 NULL 时 JSON 快照可存 null 或缺失键,序列化用 @JsonInclude(NON_NULL) 统一。
     */
    @JsonProperty("user_demand")
    private String userDemand;
}
