# LLM Call Facade Inventory

This inventory records the call-site mapping used by the v4 behavior-preserving LLM facade migration.

| Call site | Business flow | Previous access | Previous routing | Previous wait | Error handling | Transaction | Target tuple |
|---|---|---|---|---|---|---|---|
| `DispatchExecutionService.invokeWithFallback` | brand statement / legacy dispatch fallback | custom HTTP + legacy limiter | legacy dispatch primary/backup | blocking on legacy concurrency limiter | `InvocationResult.failure` via `BizException` mapping | no | `LEGACY_LIMITER` / `BLOCKING` / `LEGACY_DISPATCH_ROUTING` |
| `DispatchExecutionService.invokeArticleContentWithRouter` | article generation dispatch | router | failover | fast fail | `LlmRouteException` to `InvocationResult` / resource busy | no | `GATEWAY` / fast fail / `FAILOVER` |
| `DispatchExecutionService.invokeMonitoringWithRouter` | BI daily poll shard | router | failover | fast fail | `LlmRouteException` to `InvocationResult` / resource busy | no | `GATEWAY` / fast fail / `FAILOVER` |
| `DispatchExecutionService.judgeEffectiveHitIfNeeded` | monitoring effective hit judge | router | failover | fast fail | judge fallback | no | `GATEWAY` / fast fail / `FAILOVER` |
| `BaselineObservationCollectionWorker.collectOne` | baseline sampling | router | pinned-effective before migration | blocking | observation failure row | no | `GATEWAY` / blocking / strict `PINNED` |
| `BaselineSemanticJudgeService.invokeJudge` | baseline semantic judge | router | candidate failover | blocking | rule fallback | no | `GATEWAY` / blocking / `FAILOVER` |
| `BaselineReportPollService.pollOne` | baseline report polling | router | failover | fast fail | failed poll row | no | `GATEWAY` / fast fail / `FAILOVER` |
| `ArticleGenerationEngine.invokeModel` | article generation | direct invoker or router | pinned direct / failover | blocking for direct, fast fail for router | `LlmInvokeException` / `LlmRouteException` wrapping | no | `GATEWAY` / existing wait / existing routing |
| `KeywordLlmQuestionService.invokeOnce` | keyword question generation | direct invoker | pinned direct | blocking | coded `BizException` fallback | no | `GATEWAY` / blocking / `PINNED` |
| `GeoQuestionService.invokeQuestionModel` | GEO question generation | direct invoker | pinned direct | blocking | `BizException` wrapping | async worker | `GATEWAY` / blocking / `PINNED` |
| `GeoQuestionService.regenerateQuestion` | GEO question regeneration | direct invoker | pinned direct | blocking | `BizException` wrapping | no | `GATEWAY` / blocking / `PINNED` |
| `PresaleLlmPromptQuestionService.invokePlatform` | presale prompt question generation | direct invoker | manual candidate loop | blocking | provider exception / quota handling | no | `GATEWAY` / blocking / `PINNED` per candidate |
| `OpenAiCompatiblePresaleLlmInvoker.invokeWithRetry` | presale report generation adapter | module-local adapter to direct invoker | presale-selected platform | blocking | presale `LlmInvokeException` translation | no | `GATEWAY` / blocking / `PINNED` |
| `BrandOfferingPromptSelector.selectWithModel` | article offering selection | direct invoker | pinned direct | blocking | local fallback | no | `GATEWAY` / blocking / `PINNED` |
| `BatchArticleGenerationService.selectSmartTemplates` | batch article template matching | direct invoker | pinned direct | blocking | weighted fallback | no | `GATEWAY` / blocking / `PINNED` |

## Bypass Summary

- LLM chat-completions custom HTTP under `module/**` has been removed from production call paths.
- The former level-1 dark traffic path is now routed through `LlmCallFacade` with `LEGACY_LIMITER`, preserving legacy dispatch limiter behavior and avoiding gateway permit behavior changes.
- `PresaleLlmInvoker` remains a module-local business adapter, but its production implementation now uses `LlmCallFacade`.
- Capacity reads in module code should go through `LlmCapacityView`, not `LlmExecutionGateway` directly.
