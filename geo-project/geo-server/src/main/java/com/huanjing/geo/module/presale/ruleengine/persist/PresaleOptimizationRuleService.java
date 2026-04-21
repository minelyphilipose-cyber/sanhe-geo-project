package com.huanjing.geo.module.presale.ruleengine.persist;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 规则加载服务。
 *
 * <p>查询语义固定(docs §3 Step 1):</p>
 * <ul>
 *   <li>WHERE enabled = 1</li>
 *   <li>ORDER BY sort_order ASC, id ASC</li>
 * </ul>
 *
 * <p>规则表空或全 disabled 时返回空列表,由 Executor 处理。</p>
 */
@Service
public class PresaleOptimizationRuleService {

    private final PresaleOptimizationRuleMapper mapper;

    public PresaleOptimizationRuleService(PresaleOptimizationRuleMapper mapper) {
        this.mapper = mapper;
    }

    public List<PresaleOptimizationRule> loadEnabledRulesOrdered() {
        LambdaQueryWrapper<PresaleOptimizationRule> q = new LambdaQueryWrapper<>();
        q.eq(PresaleOptimizationRule::getEnabled, Boolean.TRUE);
        q.orderByAsc(PresaleOptimizationRule::getSortOrder);
        q.orderByAsc(PresaleOptimizationRule::getId);
        return mapper.selectList(q);
    }
}
