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
     * 阶段 3:Page03 AI 搜索新战场内容生成。
     */
    LlmCallResult marketBattleground(PlatformCallContext ctx, String marketBattlegroundPrompt)
            throws LlmInvokeException;
}
