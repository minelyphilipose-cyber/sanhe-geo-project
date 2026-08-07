package com.huanjing.geo.module.presale.generate.llm;

/**
 * 售前生成链路 LLM 调用接口。
 */
public interface PresaleLlmInvoker {

    /**
     * 阶段 1:Query 调用。
     */
    LlmCallResult query(PlatformCallContext ctx, String renderedPrompt)
            throws LlmInvokeException;

    /**
     * 阶段 2:Analyze 调用。
     */
    LlmCallResult analyze(PlatformCallContext ctx, String originalPrompt, String queryAnswer)
            throws LlmInvokeException, AnalyzeParseException;

    /**
     * 阶段 2.5:Judge 调用。
     */
    LlmCallResult judge(PlatformCallContext ctx, String judgePrompt, double temperature)
            throws LlmInvokeException;

    /**
     * 阶段 2.6:竞品名称归一化调用。
     */
    LlmCallResult normalizeCompetitors(PlatformCallContext ctx, String normalizationPrompt)
            throws LlmInvokeException;

    /**
     * 阶段 2.7:行业词汇 bucket 分类草稿调用。仅供管理端人工触发。
     */
    LlmCallResult classifyIndustryBucket(PlatformCallContext ctx, String classificationPrompt)
            throws LlmInvokeException;

    /**
     * 将手输行业归入报告基准行业。该调用使用独立提示词，不能复用文案词库 bucket 的约束。
     */
    default LlmCallResult classifyBenchmarkIndustry(PlatformCallContext ctx, String classificationPrompt)
            throws LlmInvokeException {
        return classifyIndustryBucket(ctx, classificationPrompt);
    }

    /**
     * 阶段 3:Page03 AI 搜索新战场内容生成。
     */
    LlmCallResult marketBattleground(PlatformCallContext ctx, String marketBattlegroundPrompt)
            throws LlmInvokeException;
}
