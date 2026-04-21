# P1.E Rule Engine Design v1

## 1. Scope

This document defines the implementation contract for `P1.E`:

- Execute 10 rules from `presale_optimization_rule`
- Evaluate `trigger_expression` via SpEL
- Build `OptimizationFinding` with `evidence_data`
- Allocate in-report finding ids (`F001`, `F002`, ...)
- Return ordered findings for upper-layer generate flow

Out of scope in v1:

- DB transaction for `presale_optimization_finding` persistence
- Rule hot-reload without restart
- Cross-rule priority merge strategy
- Industry-specific rule filtering

## 2. Module Layout

Proposed package: `com.huanjing.geo.module.presale.ruleengine`

- `PresaleRuleEngineExecutor`
- `RuleEvaluationContextFactory`
- `RuleExpressionEvaluator`
- `FindingIdAllocator`
- `EvidenceDataBuilder` (interface)
- `EvidenceDataBuilderRegistry`
- `builders/*` (10 builder implementations, one per `rule_code`)
- `RuleEngineResult` (execution result DTO for upper service)

Rule loading layer:

- `PresaleOptimizationRuleMapper` (MyBatis-Plus)
- `PresaleOptimizationRuleService`

## 3. Runtime Flow

1. Load enabled rules ordered by `sort_order ASC, id ASC`.
2. Build SpEL context variables:
   - `#l1` -> `RawSnapshotDTO`
   - `#l2` -> `ComputedSnapshotDTO`
   - `#benchmarks` -> `l1.benchmarksFrozen`
3. Evaluate each `trigger_expression`.
4. For hit rules:
   - allocate finding id (`F001+`)
   - dispatch `EvidenceDataBuilder` by `rule_code`
   - construct `OptimizationFinding`
5. Sort findings:
   - `default_priority` rank: `HIGH > MEDIUM > LOW`
   - tie-break: rule `sort_order ASC`, then rule `id ASC`
6. Return findings + diagnostics (hit count / failure count).

Special case:

- If rule table is empty or all rules are disabled, return normal empty result (`findings=[]`), do not throw.

## 4. SpEL Execution Contract

`RuleExpressionEvaluator`:

- Input: expression string + context
- Output: `boolean`
- Safety rules:
  - `null` result => treat as `false`
  - parse/eval exception => mark rule as evaluation failure, continue next rule
  - never fail whole report because one rule expression is invalid

Error reporting:

- Keep per-rule error detail (`rule_code`, exception message)
- Return in `RuleEngineResult.errors` for generate log aggregation

Downstream behavior:

- A rule evaluation failure is treated as `hit=false` (not hit), and no finding is produced for that rule.
- Report generation continues (no report-level degrade flag in v1).
- If `RuleEngineResult.errors` is non-empty, upper layer (`GenerateService`) should:
  - write `WARN` log with report/version + failed rule codes
  - emit error-count metric for observability
  - not expose this as UI warning in v1.

## 5. Evidence Data Contract

Interface:

```java
public interface EvidenceDataBuilder {
    String supportRuleCode();
    Map<String, Object> build(RuleBuildInput input);
}
```

`RuleBuildInput` contains:

- `RawSnapshotDTO l1`
- `ComputedSnapshotDTO l2`
- `BenchmarksFrozen benchmarks` (shortcut reference to `l1.benchmarksFrozen`)
- current rule row (`rule_code`, templates, priority)

Contract rules:

- Builder output keys must match README_P1D evidence table exactly
- Use snake_case keys in `evidence_data`
- Missing optional value => omit key (do not put placeholder string)
- Missing required value => fallback to safe default (`0`, empty string, empty list text)

Complex builder note:

- `RULE_PLATFORM_IMBALANCE`, `RULE_SCENE_MISS_HIGH_VALUE`, `RULE_SINGLE_PLATFORM_DOMINANT` require aggregation and text formatting, not plain projection.
- Builders may use shared helper utils (for example `PlatformStatUtil`, `TextFormatUtil`) to avoid duplicate logic.
- Text formatting must be consistent:
  - `weak_platforms_text`: `Name(28%)、Name(18%)`
  - `missed_scenes_text`: `“北京最正宗火锅店”、“北京约会吃火锅推荐”`
  - `affected_platforms_text`: `豆包` or `豆包、文心一言`

Builder list (v1):

- `RULE_COVERAGE_LOW_RECOMMEND`
- `RULE_BRAND_AWARENESS_LOW`
- `RULE_COMPARE_GAP`
- `RULE_PLATFORM_IMBALANCE`
- `RULE_SCENE_MISS_HIGH_VALUE`
- `RULE_NEGATIVE_EVIDENCE`
- `RULE_LOW_SENTIMENT_SCORE`
- `RULE_PLATFORM_COVERAGE_NARROW`
- `RULE_PLATFORM_COUNT_LOW`
- `RULE_SINGLE_PLATFORM_DOMINANT`

## 5.1 Engine Result Contract

`RuleEngineResult`:

```java
public class RuleEngineResult {
    List<OptimizationFinding> findings;   // sorted findings
    int evaluatedRuleCount;               // enabled rules evaluated
    int hitCount;                         // findings count before/after sort is same
    List<RuleEvaluationError> errors;     // per-rule evaluation failures
}
```

`RuleEvaluationError`:

```java
public class RuleEvaluationError {
    String ruleCode;
    String expression;
    String errorMessage;
    String errorType; // PARSE / EVAL / TYPE_MISMATCH
}
```

## 6. Finding Id Allocation

`FindingIdAllocator` is executor-local and deterministic:

- initialize counter at `1` per report version execution
- each hit allocates `String.format("F%03d", counter++)`

Allocation timing:

- allocate at hit time inside executor (before building finding DTO)
- persistence layer must not re-allocate

Reason:

- keeps snapshot L2 and optional subtable rows fully consistent
- allows test assertions against deterministic ids

## 7. De-dup and Ordering

No semantic dedup in v1:

- one rule can produce at most one finding per execution
- different rules with similar meaning are both allowed

Final ordering for output list:

1. `priority_rank` (`HIGH`, `MEDIUM`, `LOW`)
2. rule `sort_order`
3. rule `id`

This order is stable for UI and PDF rendering.

## 8. Test Plan

Unit tests (minimum 20):

- 10 rules x (`hit` + `not-hit`) => 20 tests
- Verify:
  - boolean hit behavior
  - evidence_data required keys
  - finding id format and order

Integration test (engine-only):

- use mock L1/L2 fixture
- expect 5 findings matching mock F001-F005 baseline
- verify priorities, order, evidence_data shape

## 9. Integration Boundary

`GenerateService` (or equivalent upper flow) responsibilities:

- call executor with built snapshots
- persist returned findings into:
  - `computed_snapshot_json.optimization_findings`
  - optional `presale_optimization_finding` subtable (if enabled in that flow)
- merge executor diagnostics into generation log

Executor responsibilities:

- pure rule evaluation and finding construction
- no DB write side effects
