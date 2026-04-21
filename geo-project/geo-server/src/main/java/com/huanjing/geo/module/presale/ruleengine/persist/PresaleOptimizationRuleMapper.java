package com.huanjing.geo.module.presale.ruleengine.persist;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis-Plus Mapper,读取 {@code presale_optimization_rule} 表。
 *
 * <p>规则引擎运行时只读;阈值调整由运营直接 UPDATE,不走本 Mapper。</p>
 */
@Mapper
public interface PresaleOptimizationRuleMapper extends BaseMapper<PresaleOptimizationRule> {
}
