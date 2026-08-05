package com.huanjing.geo.module.presale.generate;

/** 固定到一次报告生成执行的数据库轮次。 */
public record PresaleGenerationRun(Long versionId, long attempt) {

    public PresaleGenerationRun {
        if (versionId == null) {
            throw new IllegalArgumentException("versionId must not be null");
        }
        if (attempt < 0L) {
            throw new IllegalArgumentException("attempt must not be negative");
        }
    }

    /** 仅供不经过正式任务领取入口的兼容测试和内部旧重载使用。 */
    public static PresaleGenerationRun legacy(Long versionId) {
        return new PresaleGenerationRun(versionId, 0L);
    }

    public boolean fenced() {
        return attempt > 0L;
    }
}
