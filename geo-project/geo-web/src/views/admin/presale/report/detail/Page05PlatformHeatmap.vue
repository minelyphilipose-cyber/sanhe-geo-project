<template>
  <section id="page-05" class="page-anchor">
    <div class="page">
      <div class="page-topbar">
        <span>GEO 诊断报告 · {{ mergedView.brand_name }}</span>
        <span>04 / 多平台提及率</span>
      </div>

      <div class="p05-body">
        <!-- 章节标题 -->
        <div class="section-title">
          <span class="section-number">04</span>
          <div>
            <div class="section-label">PLATFORM MATRIX</div>
            <div class="section-heading">多平台提及率热力图</div>
          </div>
        </div>

        <!-- 正常态:有交叉数据 -->
        <template v-if="hasCrossData">
          <!-- 热力图主体 -->
          <div class="p05-heatmap">
            <!-- 列头行(平台) -->
            <div class="p05-row p05-row-head" :style="gridTemplate">
              <div><!-- 左上角空格 --></div>
              <div
                v-for="p in platformCols"
                :key="p.platform_code"
                class="mono p05-col-head"
              >
                {{ p.platform_name }}
              </div>
            </div>

            <!-- 5 行 intent -->
            <div
              v-for="row in heatRows"
              :key="row.intent_code"
              class="p05-row"
              :style="gridTemplate"
            >
              <div class="p05-row-label">{{ row.intent_label }}</div>
              <div
                v-for="cell in row.cells"
                :key="cell.key"
                class="heat-cell"
                :class="[cell.heatClass, { 'p05-cell-null': cell.isNull }]"
                :title="cell.tooltip"
              >
                {{ cell.display }}
              </div>
            </div>

            <!-- 图例 -->
            <div class="mono p05-legend">
              <span>提及率</span>
              <div v-for="l in LEGEND" :key="l.cls" class="p05-legend-item">
                <div class="heat-cell p05-legend-swatch" :class="l.cls"></div>
                <span>{{ l.label }}</span>
              </div>
              <div v-if="hasNullCells" class="p05-legend-item p05-legend-null">
                <div class="heat-cell p05-legend-swatch p05-cell-null"></div>
                <span>未测</span>
              </div>
            </div>
          </div>

          <!-- Pull-quote(A 策略:top1 平均 - top2 平均 ≥ 10 才显示) -->
          <div v-if="pullQuoteText" class="pull-quote">{{ pullQuoteText }}</div>
        </template>

        <!-- 降级态:历史报告 platform_intent_breakdown 为空 -->
        <template v-else>
          <div class="p05-fallback">
            <div class="mono p05-fallback-badge">LEGACY REPORT · 数据缺失</div>
            <div class="chinese-serif p05-fallback-title">多平台热力图数据不可用</div>
            <div class="p05-fallback-text">
              <template v-if="reportCreatedAtText">
                本报告生成于 {{ reportCreatedAtText }},早于多平台热力图功能上线。
              </template>
              <template v-else>
                本报告缺少多平台热力图所需的交叉数据,可能是早于该功能上线时生成。
              </template>
              如需查看各平台在不同意图类别下的提及率分布,请重新生成报告。
            </div>
          </div>
        </template>
      </div>

      <div class="page-footer-brand">GEO · CONFIDENTIAL</div>
      <div class="page-label">05</div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, watchEffect } from 'vue'
import { useMergedView } from '@/composables/presale/useMergedView'
import type { IntentCode, PlatformIntentCell } from '@/types/presale/computed'
import type { PlatformBreakdown } from '@/types/presale/raw'

/**
 * Page05 多平台提及率热力图(β·2·补)。
 *
 * 数据源:mergedView.platform_intent_breakdown(平台 × 意图交叉 cell 列表)。
 *
 * 渲染约定:
 *   - 列序:严格按 mergedView.platform_breakdown 顺序,平台列头显示 platform_name
 *   - 行序:固定 RECOMMENDATION → COMPARISON → INQUIRY → COGNITIVE → SCENARIO(INTENT_ORDER)
 *     行头显示 intent_label(来自 cell,失败时使用 FALLBACK_INTENT_LABEL 兜底)
 *   - heat class:按 spec v3 §3 的区间规则
 *       [0, 10)   → heat-0
 *       [10, 30)  → heat-1
 *       [30, 50)  → heat-2
 *       [50, 70)  → heat-3
 *       [70, 100] → heat-4
 *   - null cell(platform_prompt_count === null 或 0):显示 "—",叠加 p05-cell-null 灰色态
 *
 * 降级态(历史报告 platform_intent_breakdown 为空):
 *   不渲染热力图,显示 p05-fallback 块提示重新生成报告。
 *
 * Pull-quote(A 策略):
 *   前端计算各平台 avg(mention_rate);若 top1 - top2 >= 10,显示
 *   "您在 X 上的表现(平均 N%)明显优于其他平台",否则整块不渲染(避免"5% 差距说明显"的假)。
 *
 * 开发期守恒律校验:
 *   每个 platform_code 下所有 cell 的 mention_count 之和 应等于 platform_breakdown[x].mention_count。
 *   import.meta.env.DEV 下,每次渲染聚合一次 console.warn(不每平台一条,避免刷屏),
 *   附带不匹配列表;若某平台的严格 key 不命中但 case-insensitive 可命中,warn 里额外标注诊断信息
 *   (仍用严格 === 做业务匹配,spec v3 §2.1 硬约束)。
 */

const { mergedView: mergedViewRef, reportCreatedAt } = useMergedView()
const mergedView = computed(() => mergedViewRef.value!)

// ─── 静态常量 ──────────────────────────────────────────────

/** 5 意图固定渲染顺序(spec v3 §2.2)。 */
const INTENT_ORDER: readonly IntentCode[] = [
  'RECOMMENDATION',
  'COMPARISON',
  'INQUIRY',
  'COGNITIVE',
  'SCENARIO'
] as const

/** 行头中文兜底(正常情况从 cell.intent_label 取;cell 缺失时用这张表)。 */
const FALLBACK_INTENT_LABEL: Record<IntentCode, string> = {
  RECOMMENDATION: '推荐型',
  COMPARISON: '对比型',
  INQUIRY: '问题型',
  COGNITIVE: '认知型',
  SCENARIO: '场景型'
}

/** 图例(不含 null,null 图例单独放,只在有 null cell 时显示)。 */
const LEGEND = [
  { cls: 'heat-0', label: '0-10%' },
  { cls: 'heat-1', label: '10-30%' },
  { cls: 'heat-2', label: '30-50%' },
  { cls: 'heat-3', label: '50-70%' },
  { cls: 'heat-4', label: '70%+' }
] as const

// ─── 数据派生 ──────────────────────────────────────────────

const platformCols = computed<PlatformBreakdown[]>(
  () => mergedView.value.platform_breakdown
)

const cellMap = computed<Map<string, PlatformIntentCell>>(() => {
  const map = new Map<string, PlatformIntentCell>()
  for (const cell of mergedView.value.platform_intent_breakdown) {
    map.set(makeKey(cell.platform_code, cell.intent_code), cell)
  }
  return map
})

const hasCrossData = computed(
  () =>
    mergedView.value.platform_intent_breakdown.length > 0 &&
    platformCols.value.length > 0
)

interface HeatCellView {
  key: string
  display: string
  heatClass: string
  isNull: boolean
  tooltip: string
  stance: PlatformIntentCell['stance']
}

interface HeatRowView {
  intent_code: IntentCode
  intent_label: string
  cells: HeatCellView[]
}

const heatRows = computed<HeatRowView[]>(() => {
  return INTENT_ORDER.map<HeatRowView>((intentCode) => {
    const cells: HeatCellView[] = platformCols.value.map((p) => {
      const cell = cellMap.value.get(makeKey(p.platform_code, intentCode))
      return buildCellView(cell, p, intentCode)
    })
    // 行头 intent_label:取该行第一个存在 cell 的 intent_label;否则回退硬编码
    const firstCellWithLabel = platformCols.value
      .map((p) => cellMap.value.get(makeKey(p.platform_code, intentCode)))
      .find((c): c is PlatformIntentCell => c != null)
    const intentLabel =
      firstCellWithLabel?.intent_label ?? FALLBACK_INTENT_LABEL[intentCode]
    return { intent_code: intentCode, intent_label: intentLabel, cells }
  })
})

const hasNullCells = computed(() =>
  heatRows.value.some((row) => row.cells.some((c) => c.isNull))
)

/** grid template:行头 110px 固定 + N 列平台均分。 */
const gridTemplate = computed(
  () => `grid-template-columns: 110px repeat(${platformCols.value.length}, 1fr);`
)

// ─── Pull-quote(A 策略) ─────────────────────────────────

interface PlatformAvg {
  platform_code: string
  platform_name: string
  avg_rate: number
}

/**
 * 平台平均提及率(跨 5 个 intent)。
 * 分母仅计入"非 null cell"(未测的 intent 不参与平均,避免稀释)。
 * 若某平台所有 intent 都是 null,该平台不参与 top1/top2 比较。
 */
const platformAvgs = computed<PlatformAvg[]>(() => {
  return platformCols.value
    .map((p) => {
      const cells = INTENT_ORDER.map((ic) =>
        cellMap.value.get(makeKey(p.platform_code, ic))
      )
      const validRates = cells
        .filter((c): c is PlatformIntentCell => c != null)
        .filter((c) => c.platform_prompt_count != null && c.platform_prompt_count > 0)
        .map((c) => c.mention_rate)
      if (validRates.length === 0) return null
      const avg = validRates.reduce((sum, x) => sum + x, 0) / validRates.length
      return {
        platform_code: p.platform_code,
        platform_name: p.platform_name,
        avg_rate: avg
      }
    })
    .filter((x): x is PlatformAvg => x != null)
    .sort((a, b) => b.avg_rate - a.avg_rate)
})

/** Pull-quote 显示阈值:top1 平均 - top2 平均 >= 10 才显示(避免假判断)。 */
const PULL_QUOTE_GAP_THRESHOLD = 10

const pullQuoteText = computed<string | null>(() => {
  const arr = platformAvgs.value
  if (arr.length < 2) return null
  const top1 = arr[0]
  const top2 = arr[1]
  const gap = top1.avg_rate - top2.avg_rate
  if (gap < PULL_QUOTE_GAP_THRESHOLD) return null
  return `您在 ${top1.platform_name} 上的表现(平均 ${Math.round(top1.avg_rate)}%)明显优于其他平台,这个差异是理解您品牌流量结构的关键切入点。`
})

// ─── 降级态文案辅助 ───────────────────────────────────────

const reportCreatedAtText = computed<string>(() => {
  const raw = reportCreatedAt.value
  if (!raw) return ''
  try {
    const d = new Date(raw)
    if (Number.isNaN(d.getTime())) return ''
    // YYYY-MM-DD 对降级提示够用,不做时分秒
    const y = d.getFullYear()
    const m = String(d.getMonth() + 1).padStart(2, '0')
    const day = String(d.getDate()).padStart(2, '0')
    return `${y}-${m}-${day}`
  } catch {
    return ''
  }
})

// ─── 开发期守恒律校验(spec v3 §6.2) ──────────────────────

if (import.meta.env?.DEV) {
  watchEffect(() => {
    if (!hasCrossData.value) return

    // 按 platform_code 聚合 cell.mention_count
    const sumByPlatform = new Map<string, number>()
    for (const cell of mergedView.value.platform_intent_breakdown) {
      sumByPlatform.set(
        cell.platform_code,
        (sumByPlatform.get(cell.platform_code) ?? 0) + cell.mention_count
      )
    }

    // 收集所有不匹配的平台,最后统一告警(避免每个平台一条 warn 刷屏)
    interface Mismatch {
      platform_code: string
      sum_from_cells: number
      expected: number
      case_insensitive_hit: string | null
    }
    const mismatches: Mismatch[] = []

    // 小写 → 原 key 的反查表,仅用于诊断"是否存在大小写偏差"(不参与业务匹配)
    const lowerToOrig = new Map<string, string>()
    for (const k of sumByPlatform.keys()) {
      lowerToOrig.set(k.toLowerCase(), k)
    }

    for (const p of platformCols.value) {
      const sumFromCells = sumByPlatform.get(p.platform_code) ?? 0
      if (sumFromCells !== p.mention_count) {
        // 诊断:如果严格 key 不存在但 case-insensitive 命中,记录下来帮助后端排查
        const hasExactKey = sumByPlatform.has(p.platform_code)
        const ciHit = hasExactKey
          ? null
          : lowerToOrig.get(p.platform_code.toLowerCase()) ?? null
        mismatches.push({
          platform_code: p.platform_code,
          sum_from_cells: sumFromCells,
          expected: p.mention_count,
          case_insensitive_hit: ciHit
        })
      }
    }

    if (mismatches.length === 0) return

    // 仅在本次渲染打一条聚合 warn
    const lines = mismatches.map((m) => {
      const base = `  - ${m.platform_code}: Σcell=${m.sum_from_cells}, expected=${m.expected}`
      return m.case_insensitive_hit == null
        ? base
        : `${base} (case-insensitive match exists: "${m.case_insensitive_hit}",仅诊断,不参与业务匹配)`
    })
    // eslint-disable-next-line no-console
    console.warn(
      `[P05 守恒律告警] ${mismatches.length}/${platformCols.value.length} 个平台不匹配 ` +
        `(spec v3 §6.2:Σ platform_intent_breakdown[p].mention_count === platform_breakdown[p].mention_count):\n` +
        lines.join('\n') +
        `\n请后端核查 platform_intent_breakdown 计算逻辑。`
    )
  })
}

// ─── 纯函数区 ──────────────────────────────────────────────

function makeKey(platformCode: string, intentCode: IntentCode): string {
  return `${platformCode}::${intentCode}`
}

function buildCellView(
  cell: PlatformIntentCell | undefined,
  platform: PlatformBreakdown,
  intentCode: IntentCode
): HeatCellView {
  const key = makeKey(platform.platform_code, intentCode)

  // 缺失 cell(理论上新数据不会发生,spec v3 §2.4 硬约束全量;
  // 但历史或降级数据兜底,视作 null 态)
  if (cell == null) {
    return {
      key,
      display: '—',
      heatClass: 'heat-0',
      isNull: true,
      tooltip: `${platform.platform_name} · ${FALLBACK_INTENT_LABEL[intentCode]}:数据缺失`,
      stance: null
    }
  }

  const isNull =
    cell.platform_prompt_count == null || cell.platform_prompt_count <= 0

  if (isNull) {
    return {
      key,
      display: '—',
      heatClass: 'heat-0',
      isNull: true,
      tooltip: `${platform.platform_name} · ${cell.intent_label}:未测试该意图`,
      stance: cell.stance ?? null
    }
  }

  const tooltip = buildTooltip(cell)

  return {
    key,
    display: `${cell.mention_rate}%`,
    heatClass: rateToHeatClass(cell.mention_rate),
    isNull: false,
    tooltip: `${platform.platform_name} · ${cell.intent_label}:${tooltip}`,
    stance: cell.stance ?? null
  }
}

function buildTooltip(cell: PlatformIntentCell): string {
  if (cell.intent_code === 'COGNITIVE' || cell.intent_code === 'COMPARISON') {
    const base = `评分 ${cell.mention_rate}%（基于 ${cell.platform_prompt_count} 次裁判）`
    if (cell.intent_code !== 'COMPARISON') return base
    const stanceLabel = toStanceLabel(cell.stance)
    return stanceLabel ? `${base}，站队:${stanceLabel}` : base
  }
  return `${cell.mention_count}/${cell.platform_prompt_count} 提及(${cell.mention_rate}%)`
}

function toStanceLabel(
  stance: PlatformIntentCell['stance']
): '我方领先' | '竞品领先' | '持平' | null {
  if (stance === 'target') return '我方领先'
  if (stance === 'competitor') return '竞品领先'
  if (stance === 'tie') return '持平'
  return null
}

/**
 * mention_rate → heat-N class。区间左闭右开:
 *   [0, 10)   → heat-0
 *   [10, 30)  → heat-1
 *   [30, 50)  → heat-2
 *   [50, 70)  → heat-3
 *   [70, 100] → heat-4
 */
function rateToHeatClass(rate: number): string {
  if (rate < 10) return 'heat-0'
  if (rate < 30) return 'heat-1'
  if (rate < 50) return 'heat-2'
  if (rate < 70) return 'heat-3'
  return 'heat-4'
}
</script>

<style scoped>
.page-anchor {
  display: flex;
  justify-content: center;
}

.p05-body {
  margin-top: 60px;
}

/* ─── 热力图 ────────────────────────────────────────────── */

.p05-heatmap {
  margin-bottom: 32px;
}

.p05-row {
  display: grid;
  gap: 4px;
  margin-bottom: 4px;
}

/* 列头(平台名) */
.p05-row-head {
  margin-bottom: 4px;
}
.p05-col-head {
  font-size: 10px;
  text-align: center;
  color: var(--presale-muted);
  letter-spacing: 1px;
}

/* 行头(意图名) */
.p05-row-label {
  font-size: 13px;
  display: flex;
  align-items: center;
  color: var(--presale-ink);
}

/* 未测 cell —— 灰底 + 灰字 "—",叠加在 heat-0 上 */
.p05-cell-null {
  background: var(--presale-paper-alt) !important;
  color: var(--presale-muted) !important;
  border: 1px dashed var(--presale-line) !important;
}

/* ─── 图例 ─────────────────────────────────────────────── */

.p05-legend {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 24px;
  font-size: 11px;
  color: var(--presale-muted);
  flex-wrap: wrap;
}
.p05-legend-item {
  display: flex;
  align-items: center;
  gap: 4px;
}
.p05-legend-swatch {
  width: 16px;
  height: 16px;
  font-size: 8px;
  aspect-ratio: auto; /* 覆盖全局 .heat-cell 的 aspect-ratio:1 */
}
.p05-legend-null {
  margin-left: 12px;
}

/* ─── 降级态 ───────────────────────────────────────────── */

.p05-fallback {
  margin-top: 32px;
  padding: 32px 24px;
  border: 1px dashed var(--presale-line);
  background: var(--presale-paper-alt);
  text-align: center;
}
.p05-fallback-badge {
  font-size: 11px;
  letter-spacing: 3px;
  color: var(--presale-accent);
  margin-bottom: 12px;
}
.p05-fallback-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--presale-ink);
  margin-bottom: 12px;
}
.p05-fallback-text {
  font-size: 13px;
  color: var(--presale-muted);
  line-height: 1.7;
  max-width: 520px;
  margin: 0 auto;
}
</style>
