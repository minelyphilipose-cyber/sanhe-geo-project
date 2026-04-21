package com.huanjing.geo.module.presale.ruleengine.persist;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 规则库实体,对应表 {@code presale_optimization_rule}(V62 v4 创建,V64 种子数据填充)。
 *
 * <p>运行时只读:规则引擎加载规则后不写回此表。阈值调整由运营/DBA 直接 UPDATE。</p>
 */
@Data
@TableName("presale_optimization_rule")
public class PresaleOptimizationRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ruleCode;

    private String ruleName;

    /** 类别:基础设施 / 内容建设 / 关系建设 / 平台扩展。 */
    private String category;

    /** 默认优先级:HIGH / MEDIUM / LOW。 */
    private String defaultPriority;

    /** SpEL 表达式。上下文变量 #l1 / #l2 / #benchmarks。 */
    private String triggerExpression;

    private String titleTemplate;

    private String descriptionTemplate;

    private String evidenceTemplate;

    /** TINYINT(1) 存 0/1,MyBatis-Plus 默认映射 Boolean。 */
    private Boolean enabled;

    private Integer sortOrder;

    private String remark;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
