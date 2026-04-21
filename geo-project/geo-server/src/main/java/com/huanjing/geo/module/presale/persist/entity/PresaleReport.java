package com.huanjing.geo.module.presale.persist.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

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

    /** 行业字典 key(如 restaurant),值必须存在于 sys_dict_item(presale_industry)。 */
    private String industry;

    /** 身份字典 key(如 chain_brand)。 */
    private String industryRole;

    private String region;

    /** 客户诉求,可选。 */
    private String userDemand;

    /** 指向最新版本,初始为 null,第一版创建后更新。 */
    private Long latestVersionId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 创建人(sys_user.id),由当前登录上下文注入。 */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;
}
