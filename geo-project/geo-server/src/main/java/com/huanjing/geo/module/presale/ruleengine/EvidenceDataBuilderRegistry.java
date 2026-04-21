package com.huanjing.geo.module.presale.ruleengine;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 按 rule_code 分派 EvidenceDataBuilder。
 *
 * <p>Spring 注入所有 {@link EvidenceDataBuilder} 实现,按
 * {@link EvidenceDataBuilder#supportRuleCode()} 建立映射。</p>
 *
 * <p>启动期校验:若发现两个 Builder 声明同一 rule_code,抛出 {@link IllegalStateException},
 * 避免运行时分派歧义。若规则 INSERT 了但没有对应 Builder,本类不报错,由 Executor
 * 在调用 {@link #get(String)} 时决定如何处理(当前 Executor 跳过并记 WARN)。</p>
 */
@Component
public class EvidenceDataBuilderRegistry {

    private final Map<String, EvidenceDataBuilder> byRuleCode;

    public EvidenceDataBuilderRegistry(List<EvidenceDataBuilder> builders) {
        this.byRuleCode = new HashMap<>();
        for (EvidenceDataBuilder b : builders) {
            String code = b.supportRuleCode();
            if (byRuleCode.containsKey(code)) {
                throw new IllegalStateException(
                        "Duplicate EvidenceDataBuilder for rule_code=" + code
                                + " (existing=" + byRuleCode.get(code).getClass().getSimpleName()
                                + ", new=" + b.getClass().getSimpleName() + ")");
            }
            byRuleCode.put(code, b);
        }
    }

    /**
     * 获取 rule_code 对应的 Builder。找不到返回 {@code null},Executor 处理兜底。
     */
    public EvidenceDataBuilder get(String ruleCode) {
        return byRuleCode.get(ruleCode);
    }

    /** 内省用:返回已注册的 rule_code 集合。 */
    public java.util.Set<String> registeredRuleCodes() {
        return byRuleCode.keySet();
    }
}
