package com.huanjing.geo.module.presale.ruleengine;

/**
 * finding_id 分配器,作用域为**单次规则引擎执行**。
 *
 * <p>契约(docs §6):</p>
 * <ul>
 *   <li>每次 {@link PresaleRuleEngineExecutor#execute} 创建一个新实例,计数器从 1 开始</li>
 *   <li>命中时立即分配,格式 {@code F%03d}(F001, F002, ...)</li>
 *   <li>同一 Executor 执行周期内,分配是**确定的**(即规则顺序决定 ID 顺序)</li>
 *   <li>非线程安全:同一 Executor 执行内部单线程顺序调用,不需要并发保护</li>
 * </ul>
 */
public class FindingIdAllocator {

    private int counter = 1;

    public String next() {
        return String.format("F%03d", counter++);
    }
}
