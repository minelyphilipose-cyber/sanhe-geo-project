package com.huanjing.geo.module.presale.generate;

/**
 * 报告生成状态枚举。与 V62 表中 generation_status VARCHAR 字段对应。
 *
 * <p>状态流转:</p>
 * <pre>
 *   INIT → QUEUED → RUNNING → DONE
 *                     ↓
 *                   FAILED (可重试,重试后回到 QUEUED)
 * </pre>
 */
public enum PresaleGenerateStatus {
    /** 刚创建,尚未入队。 */
    INIT,
    /** 已入队等待执行。 */
    QUEUED,
    /** 正在执行(LLM 调用中)。 */
    RUNNING,
    /** 生成完成(含降级成功)。 */
    DONE,
    /** 生成失败。 */
    FAILED;

    public boolean isTerminal() {
        return this == DONE || this == FAILED;
    }

    public boolean isRunning() {
        return this == QUEUED || this == RUNNING;
    }
}
