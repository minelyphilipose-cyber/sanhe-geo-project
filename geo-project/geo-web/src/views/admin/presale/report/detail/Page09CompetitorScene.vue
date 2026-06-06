<template>
  <section id="page-09" class="page-anchor">
    <div class="page">
      <div class="page-topbar">
        <span>GEO 诊断报告 · {{ mergedView.brand_name }}</span>
        <span>06 / 竞品对标(续)</span>
      </div>

      <div class="p09-body">
        <!-- 顶部标题 -->
        <div class="p09-header">
          <div class="mono p09-subtitle">RECOMMENDATION PRESSURE · 推荐型高价值场景</div>
          <h3 class="chinese-serif p09-title">求推荐时,竞品在场而您缺席的场景</h3>
          <div class="p09-title-note">
            本页只看"推荐型高价值"切片;覆盖度页的"高价值"为全部高价值意图,包含推荐与已点名对比。
          </div>
        </div>

        <!-- 场景对比表 -->
        <div v-if="visibleMissingRows.length > 0" class="data-matrix p09-matrix">
          <!-- 表头 -->
          <div class="data-matrix-row p09-row-head">
            <div class="mono p09-col-label">价值</div>
            <div class="mono p09-col-label">查询场景</div>
            <div class="mono p09-col-label p09-col-center">意图</div>
            <div class="mono p09-col-label p09-col-center">您</div>
            <div
              v-for="c in competitorNames"
              :key="c"
              class="mono p09-col-label p09-col-center"
            >
              {{ shortenCompetitorName(c) }}
            </div>
          </div>

          <!-- 数据行 -->
          <div
            v-for="(row, idx) in visibleMissingRows"
            :key="`${row.prompt_code}-${idx}`"
            class="data-matrix-row p09-row-data"
          >
            <div>
              <span class="priority-badge" :class="`priority-${row.priorityClass}`">
                <span class="priority-dot"></span>{{ row.priorityLabel }}
              </span>
            </div>
            <div class="p09-query-text">"{{ row.prompt_content }}"</div>
            <div class="p09-col-center p09-intent-text">{{ row.category }}</div>
            <!-- 您:展示集定义下目标品牌缺席 -->
            <div class="p09-col-center cross">✗</div>
            <!-- 每个竞品:该场景真实出现则 ✓,否则 ✗ -->
            <div
              v-for="c in competitorNames"
              :key="c"
              class="p09-col-center"
              :class="row.coverageByCompetitor[c] ? 'tick' : 'cross'"
            >
              {{ row.coverageByCompetitor[c] ? '✓' : '✗' }}
            </div>
          </div>
        </div>

        <!-- 无缺口兜底:极罕见情况,但逻辑上可能(100% 覆盖) -->
        <div v-else class="p09-empty">
          未发现满足"竞品在场且您缺席"的推荐型高价值场景。
        </div>

        <div v-if="hiddenMissingCount > 0" class="p09-limit-note">
          本页按商业价值优先展示 {{ visibleMissingRows.length }} 条代表性缺口；其余
          {{ hiddenMissingCount }} 条已计入下方缺口总数。
        </div>

        <!-- 3 张卡片:各价值层的缺口数/总数 -->
        <div class="p09-gap-cards">
          <div class="p09-gap-card p09-gap-high">
            <div class="mono p09-gap-label p09-gap-label-high">RECO HIGH VALUE GAP</div>
            <div class="metric-hero p09-gap-number">
              {{ highGapCount
              }}<span class="p09-gap-denominator">/{{ pressure.hv_reco_total }}</span>
            </div>
            <div class="p09-gap-desc">推荐型高价值 · 竞品在场 · 您缺席</div>
            <div class="p09-gap-context">
              全部高价值已覆盖 {{ highValueCovered }}/{{ highValueTotal }};本卡只取其中推荐型切片。
            </div>
          </div>
          <div class="p09-gap-card p09-gap-mid">
            <div class="mono p09-gap-label p09-gap-label-mid">MID VALUE GAP</div>
            <div class="metric-hero p09-gap-number">
              {{ midGapCount
              }}<span class="p09-gap-denominator">/{{ mergedView.scene_coverage.mid_value.total }}</span>
            </div>
            <div class="p09-gap-desc">覆盖缺口 · 全部中价值场景</div>
            <div class="p09-gap-context">缺口 = 总数 - 已覆盖;已覆盖 {{ midCovered }}。</div>
          </div>
          <div class="p09-gap-card p09-gap-low">
            <div class="mono p09-gap-label p09-gap-label-low">LOW VALUE GAP</div>
            <div class="metric-hero p09-gap-number">
              {{ lowGapCount
              }}<span class="p09-gap-denominator">/{{ mergedView.scene_coverage.low_value.total }}</span>
            </div>
            <div class="p09-gap-desc">覆盖缺口 · 全部低价值场景</div>
            <div class="p09-gap-context">缺口 = 总数 - 已覆盖;已覆盖 {{ lowCovered }}。</div>
          </div>
        </div>

        <!-- 底部引用(优先使用竞品组优势,否则回退 Top1 竞品 scene_advantages) -->
        <div v-if="topCompetitorAdvantagesText" class="p09-quote-wrap">
          <div class="pull-quote">
            {{ topCompetitorAdvantagesText }}
            <!-- 若来自 L1 原始提取,加个弱标签提示 -->
            <span v-if="showRawTag" class="p09-raw-tag mono">· 原始提取</span>
          </div>
        </div>
      </div>

      <div class="page-footer-brand">GEO · CONFIDENTIAL</div>
      <div class="page-label">09</div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useMergedView } from '@/composables/presale/useMergedView'
import type { SceneCompetitorPressureItem } from '@/types/presale/computed'

/**
 * Page09 竞品场景差异。
 *
 * 数据映射:
 *   - 场景表:scene_coverage.{high_value, mid_value, low_value}.missing_queries[]
 *     每行 = 一条 missing query
 *       价值 badge = 所在分组(high/mid/low)
 *       您列 = ✗(固定,因为在 missing 列表里)
 *       竞品列 = top_competitor_coverage 是否含该竞品名
 *       仅展示至少一个竞品被推荐的缺口,避免混入双方都未推荐的场景
 *   - 3 张卡片:每组竞品差异缺口数 / group.total
 *   - 底部引用:竞品组对比模式优先取 group_scene_advantages;否则回退 Top1 竞品 scene_advantages
 *
 * 不做:
 *   - 不展示 covered_queries(主题是"gap",覆盖的不显示)
 */

const { mergedView: mergedViewRef } = useMergedView()
const mergedView = computed(() => mergedViewRef.value!)
const MAX_VISIBLE_MISSING_ROWS = 8
const DISPLAY_TARGET_THRESHOLD = 0
const pressure = computed(() => mergedView.value.scene_competitor_pressure)

// ─── 竞品名列表(动态,不硬编码) ──────────────────────
const competitorNames = computed(() =>
  sortedCompetitors.value.map((c) => c.name)
)

const sortedCompetitors = computed(() =>
  [...mergedView.value.merged_competitors].sort((a, b) => {
    const mentionDiff = (b.mention_count ?? 0) - (a.mention_count ?? 0)
    if (mentionDiff !== 0) return mentionDiff
    return a.rank - b.rank
  })
)

const competitorNameSet = computed(() =>
  new Set(competitorNames.value.filter(Boolean))
)

// 长名字截断(表头空间有限,保留可识别性)
function shortenCompetitorName(name: string): string {
  // 中文超过 3 字取前 2 字 + 省略号;英文超 6 字符同理
  if (name.length > 4) return name.slice(0, 3) + '…'
  return name
}

// ─── 缺口行合成 ────────────────────────────────────────
interface MissingRow {
  prompt_code: string
  prompt_content: string
  category: string
  priorityClass: 'high' | 'mid' | 'low'
  priorityLabel: '高' | '中' | '低'
  /** 以竞品名为 key,是否覆盖。 */
  coverageByCompetitor: Record<string, boolean>
}

const missingRows = computed<MissingRow[]>(() => {
  return (pressure.value.items ?? [])
    .filter((scene) => isDisplayPressureScene(scene))
    .map((scene) => {
      const map: Record<string, boolean> = {}
      for (const cname of competitorNames.value) {
        map[cname] = isCoveredByCompetitor(scene, cname)
      }
      return {
        prompt_code: scene.prompt_code ?? scene.query,
        prompt_content: scene.query?.trim() ? scene.query : '—',
        category: '推荐型',
        priorityClass: 'high',
        priorityLabel: '高',
        coverageByCompetitor: map
      }
    })
})

const visibleMissingRows = computed(() => missingRows.value.slice(0, MAX_VISIBLE_MISSING_ROWS))
const hiddenMissingCount = computed(() => Math.max(0, missingRows.value.length - visibleMissingRows.value.length))

// ─── 3 张卡片计数 ──────────────────────────────────────
function isDisplayPressureScene(scene: SceneCompetitorPressureItem): boolean {
  return (scene.target_mentioned_platform_count ?? 0) <= DISPLAY_TARGET_THRESHOLD &&
    (scene.competitors ?? []).some((item) =>
      competitorNameSet.value.has(item.name) && (item.mentioned_platform_count ?? 0) > 0
    )
}

function isCoveredByCompetitor(
  query: SceneCompetitorPressureItem,
  competitorName: string
): boolean {
  return (query.competitors ?? []).some((item) =>
    item.name === competitorName && (item.mentioned_platform_count ?? 0) > 0
  )
}

function coverageGapCount(group: typeof mergedView.value.scene_coverage.high_value): number {
  return group.missing_queries?.length ?? 0
}

const highGapCount = computed(() => missingRows.value.length)
const highValueTotal = computed(() => mergedView.value.scene_coverage.high_value.total)
const highValueCovered = computed(() => mergedView.value.scene_coverage.high_value.covered)
const midCovered = computed(() => mergedView.value.scene_coverage.mid_value.covered)
const lowCovered = computed(() => mergedView.value.scene_coverage.low_value.covered)
const midGapCount = computed(
  () => coverageGapCount(mergedView.value.scene_coverage.mid_value)
)
const lowGapCount = computed(
  () => coverageGapCount(mergedView.value.scene_coverage.low_value)
)

// ─── 底部引用:竞品组优势优先,否则 Top1 竞品 scene_advantages ────────────
const topCompetitor = computed(() =>
  sortedCompetitors.value[0] ?? null
)
const groupSceneAdvantages = computed(() =>
  mergedView.value.group_scene_advantages?.filter((item) => item && item.trim()) ?? []
)
const topCompetitorAdvantagesText = computed(() => {
  if (groupSceneAdvantages.value.length > 0) {
    const joined = groupSceneAdvantages.value.slice(0, 3).join('、')
    return `竞品组优势场景集中在:${joined}。建议围绕这些场景补足内容覆盖,降低竞品组在关键查询中的先发优势。`
  }
  const top = topCompetitor.value
  if (!top || !top.scene_advantages || top.scene_advantages.length === 0) return ''
  const joined = top.scene_advantages.slice(0, 3).join('、')
  return `Top1 品牌 ${top.name} 的优势场景集中在:${joined}。建议针对这些场景做针对性的内容布局。`
})
const showRawTag = computed(() =>
  groupSceneAdvantages.value.length === 0 && topCompetitor.value?.scene_is_polished === false
)
</script>

<style scoped>
.page-anchor {
  display: flex;
  justify-content: center;
}

.p09-body {
  margin-top: 60px;
}

.p09-header {
  margin-bottom: 28px;
}
.p09-subtitle {
  font-size: 11px;
  letter-spacing: 3px;
  color: #6b6456;
  margin-bottom: 8px;
}
.p09-title {
  font-size: 22px;
  font-weight: 700;
  color: #0b1426;
  margin: 0;
}
.p09-title-note {
  margin-top: 8px;
  font-size: 11px;
  line-height: 1.7;
  color: #6b6456;
}

/* 场景表 */
.p09-matrix {
  margin-bottom: 32px;
}
.p09-row-head,
.p09-row-data {
  /* 60 价值 | 1fr 查询 | 90 意图 | 60 您 | 每竞品 60 */
  /* 3 竞品是固定数,模板直接生成表头和数据的 grid-template-columns */
  grid-template-columns: 60px 1fr 90px 60px 60px 60px 60px;
}
.p09-row-head {
  padding: 12px 0 !important;
}
.p09-col-label {
  font-size: 10px;
  letter-spacing: 2px;
  color: #6b6456;
}
.p09-col-center {
  text-align: center;
}
.p09-query-text {
  font-size: 13px;
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}
.p09-intent-text {
  font-size: 11px;
  color: #6b6456;
}

/* 空态 */
.p09-empty {
  padding: 48px 0;
  text-align: center;
  color: #6b6456;
  font-style: italic;
  border-top: 2px solid #0b1426;
  border-bottom: 2px solid #0b1426;
  margin-bottom: 32px;
}

.p09-limit-note {
  margin: -18px 0 22px;
  font-size: 11px;
  color: #6b6456;
}

/* 3 张卡片 */
.p09-gap-cards {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 16px;
  margin-bottom: 32px;
}
.p09-gap-card {
  background: #f7f3ea;
  padding: 20px;
}
.p09-gap-high {
  border-top: 2px solid #b91c1c;
}
.p09-gap-mid {
  border-top: 2px solid #d97706;
}
.p09-gap-low {
  border-top: 2px solid #6b6456;
}
.p09-gap-label {
  font-size: 10px;
  letter-spacing: 2px;
  margin-bottom: 8px;
}
.p09-gap-label-high {
  color: #b91c1c;
}
.p09-gap-label-mid {
  color: #d97706;
}
.p09-gap-label-low {
  color: #6b6456;
}
.p09-gap-number {
  font-size: 36px;
  color: #0b1426;
}
.p09-gap-denominator {
  font-size: 16px;
  color: #6b6456;
  font-family: 'Noto Sans SC', sans-serif;
  /* 解除 metric-hero 的 letter-spacing / font-weight */
  letter-spacing: 0;
  font-weight: 400;
  margin-left: 6px;
}
.p09-gap-desc {
  font-size: 11px;
  color: #6b6456;
  margin-top: 6px;
}
.p09-gap-context {
  margin-top: 6px;
  font-size: 10px;
  line-height: 1.6;
  color: #8a8272;
}

/* 底部引用的 "原始提取" 弱标签 */
.p09-quote-wrap {
  margin-top: 8px;
}
.p09-raw-tag {
  display: inline-block;
  margin-left: 8px;
  font-size: 10px;
  letter-spacing: 2px;
  color: #9b9486;
  font-style: normal;
  text-transform: uppercase;
}
</style>
