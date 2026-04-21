# mergeSnapshot 对账 fixture(v1.2)

本目录下的 4 组 fixture 用于前端 `mergeSnapshot` 实现与后端 `MergeService` 实现的**合并规则对账**。

## 用途

1. **前端单测**:`mergeSnapshot(fx.input.raw, fx.input.computed, fx.input.editable, fx.input.version_row)` 的返回值必须**深度等于** `fx.expected`
2. **后端单测**:后端 `MergeService` 同样以 fx.input 为输入、fx.expected 为断言基线
3. **跨栈对账**:联调前 CI 跑一遍四份 fixture 在两边都通过,保证"前端 mock 合并结果 === 后端 /merged-view 返回"

## 7 组 fixture 覆盖点

| 文件 | 覆盖规则 |
|---|---|
| `01-normal.json` | 完整正常数据。所有 L3 字段均有值,无隐藏 finding,无降级平台。**用于验证最简单路径的正确性(baseline)** |
| `02-l3-fallback.json` | L3 多字段为 null,触发所有默认模板回退:报告标题 / 副标题 / 执行摘要 / ROI 免责声明 / 阶段标题描述 / finding 文案 |
| `03-competitor-raw.json` | 所有 3 个竞品 `scene_advantages_polished = null`,强制回退到 L1 `scene_advantages_raw`。验证 `scene_is_polished=false` 标记和 L1 回退 |
| `04-findings-filter.json` | F002 `is_hidden=true` 跳过;F003 `sort_order=0` 置顶;其余无 sort_order 按 L2 原序 idx+1。验证过滤和排序规则 |
| `05-unordered-phases-competitors.json` | L1.competitors 和 L2.roi_simulation.phases 输入故意乱序 3/1/2,**expected 仍严格按 rank/phase_no 1→2→3 输出**。覆盖 Codex P1 指出的合并顺序契约防退化 |
| `06-degraded-null.json` | `version_row.degraded_platforms = null`(模拟 DB NULL),**expected.meta.degraded_platforms = []**(归一)。覆盖 Codex P2 指出的 nullable 口径防退化 |
| `07-benchmark-fallback.json` | `benchmarks_frozen.match_level = FALLBACK_INDUSTRY`,`industry_role = "_ALL_"`。**expected.meta.match_level** 应正确提升为 `FALLBACK_INDUSTRY`(前端据此展示回退警示条) |

## 数据基线

4 组 fixture 的基础数据都来自 `report_data_mock_sample_v1_2.json`(海底捞案例),只在具体 fixture 里调整特定字段以触发不同代码路径,保持其他字段稳定便于 diff。

## 使用示例(前端)

```ts
import { describe, it, expect } from 'vitest';
import { mergeSnapshot } from '@/utils/presale/merge-snapshot';
import fx01 from '@/fixtures/presale/01-normal.json';

describe('mergeSnapshot', () => {
  it('01-normal: baseline', () => {
    const actual = mergeSnapshot(
      fx01.input.raw,
      fx01.input.computed,
      fx01.input.editable,
      fx01.input.version_row
    );
    expect(actual).toEqual(fx01.expected);
  });

  // ... 其余 3 组类似
});
```

## 使用示例(后端 Java)

```java
@Test
void fx01_normal_baseline() throws Exception {
    Fixture fx = loadFixture("01-normal.json");
    MergedViewDTO actual = mergeService.merge(
        fx.input.raw, fx.input.computed, fx.input.editable, fx.input.versionRow
    );
    assertThat(toJson(actual)).isEqualTo(toJson(fx.expected));
}
```

## 扩展 fixture 的指南

新增 fixture 时:
- 文件名按 `NN-短描述.json` 编号
- 必须带 `_description` 字段说明覆盖的规则点
- 基础数据推荐从 01-normal 改写,只改必要字段
- 修改任何 expected 值必须同步更新前后端实现的判断,或证明基线错了

## 版本

- Schema 基线:report_data_schema_v1_2.json
- 生成时:P1·B 阶段(rebuild-r4 DTO 骨架之后)
- 生成脚本:`/tmp/gen_fixtures.py`(本目录未纳入生成器;fixture 一旦产出视为固定基线,不跟随代码自动再生成)
