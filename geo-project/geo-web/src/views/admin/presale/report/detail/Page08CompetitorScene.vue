<template>
  <section id="page-08" class="page-anchor">
    <div class="page">
      <div class="page-topbar">
        <span>GEO 诊断报告 · {{ mergedView.brand_name }}</span>
        <span>05 / 竞品对标(续)</span>
      </div>

      <div class="p08-body">
        <!-- 顶部标题 -->
        <div class="p08-header">
          <div class="mono p08-subtitle">SCENE-LEVEL GAP · 场景差异</div>
          <h3 class="chinese-serif p08-title">竞品被推荐而您未被推荐的场景</h3>
        </div>

        <!-- 场景对比表 -->
        <div v-if="visibleMissingRows.length > 0" class="data-matrix p08-matrix">
          <!-- 表头 -->
          <div class="data-matrix-row p08-row-head">
            <div class="mono p08-col-label">价值</div>
            <div class="mono p08-col-label">查询场景</div>
            <div class="mono p08-col-label p08-col-center">意图</div>
            <div class="mono p08-col-label p08-col-center">您</div>
            <div
              v-for="c in competitorNames"
              :key="c"
              class="mono p08-col-label p08-col-center"
            >
              {{ shortenCompetitorName(c) }}
            </div>
          </div>

          <!-- 数据行 -->
          <div
            v-for="(row, idx) in visibleMissingRows"
            :key="`${row.prompt_code}-${idx}`"
            class="data-matrix-row p08-row-data"
          >
            <div>
              <span class="priority-badge" :class="`priority-${row.priorityClass}`">
                <span class="priority-dot"></span>{{ row.priorityLabel }}
              </span>
            </div>
            <div class="p08-query-text">"{{ row.prompt_content }}"</div>
            <div class="p08-col-center p08-intent-text">{{ row.category }}</div>
            <!-- 您:永远 ✗(因为是 missing) -->
            <div class="p08-col-center cross">✗</div>
            <!-- 每个竞品:top_competitor_coverage 包含则 ✓,否则 ✗ -->
            <div
              v-for="c in competitorNames"
              :key="c"
              class="p08-col-center"
              :class="row.coverageByCompetitor[c] ? 'tick' : 'cross'"
            >
              {{ row.coverageByCompetitor[c] ? '✓' : '✗' }}
            </div>
          </div>
        </div>

        <!-- 无缺口兜底:极罕见情况,但逻辑上可能(100% 覆盖) -->
        <div v-else class="p08-empty">
          未发现您未被推荐的场景 —— 您的覆盖表现优异。
        </div>

        <div v-if="hiddenMissingCount > 0" class="p08-limit-note">
          本页按商业价值优先展示 {{ visibleMissingRows.length }} 条代表性缺口；其余
          {{ hiddenMissingCount }} 条已计入下方缺口总数。
        </div>

        <!-- 3 张卡片:各价值层的缺口数/总数 -->
        <div class="p08-gap-cards">
          <div class="p08-gap-card p08-gap-high">
            <div class="mono p08-gap-label p08-gap-label-high">HIGH VALUE GAP</div>
            <div class="metric-hero p08-gap-number">
              {{ highGapCount
              }}<span class="p08-gap-denominator">/{{ mergedView.scene_coverage.high_value.total }} 缺失</span>
            </div>
            <div class="p08-gap-desc">高价值场景</div>
          </div>
          <div class="p08-gap-card p08-gap-mid">
            <div class="mono p08-gap-label p08-gap-label-mid">MID VALUE GAP</div>
            <div class="metric-hero p08-gap-number">
              {{ midGapCount
              }}<span class="p08-gap-denominator">/{{ mergedView.scene_coverage.mid_value.total }} 缺失</span>
            </div>
            <div class="p08-gap-desc">中价值场景</div>
          </div>
          <div class="p08-gap-card p08-gap-low">
            <div class="mono p08-gap-label p08-gap-label-low">LOW VALUE GAP</div>
            <div class="metric-hero p08-gap-number">
              {{ lowGapCount
              }}<span class="p08-gap-denominator">/{{ mergedView.scene_coverage.low_value.total }} 缺失</span>
            </div>
            <div class="p08-gap-desc">低价值场景</div>
          </div>
        </div>

        <!-- 底部引用(优先使用竞品组优势,否则回退 Top1 竞品 scene_advantages) -->
        <div v-if="topCompetitorAdvantagesText" class="p08-quote-wrap">
          <div class="pull-quote">
            {{ topCompetitorAdvantagesText }}
            <!-- 若来自 L1 原始提取,加个弱标签提示 -->
            <span v-if="showRawTag" class="p08-raw-tag mono">· 原始提取</span>
          </div>
        </div>
      </div>

      <div class="page-footer-brand">GEO · CONFIDENTIAL</div>
      <div class="page-label">08</div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useMergedView } from '@/composables/presale/useMergedView'

/**
 * Page08 竞品场景差异。
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

// ─── 竞品名列表(动态,不硬编码) ──────────────────────
const competitorNames = computed(() =>
  mergedView.value.merged_competitors.map((c) => c.name)
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
  const rows: MissingRow[] = []
  const coverage = mergedView.value.scene_coverage

  const addGroup = (
    group: typeof coverage.high_value,
    priorityClass: 'high' | 'mid' | 'low',
    priorityLabel: '高' | '中' | '低'
  ) => {
    if (!group.missing_queries) return
    for (const m of group.missing_queries) {
      const map: Record<string, boolean> = {}
      for (const cname of competitorNames.value) {
        map[cname] = isCoveredByCompetitor(m, cname)
      }
      if (!Object.values(map).some(Boolean)) continue
      rows.push({
        prompt_code: m.prompt_code,
        prompt_content: m.prompt_content?.trim() ? m.prompt_content : '—',
        category: m.category,
        priorityClass,
        priorityLabel,
        coverageByCompetitor: map
      })
    }
  }

  addGroup(coverage.high_value, 'high', '高')
  addGroup(coverage.mid_value, 'mid', '中')
  addGroup(coverage.low_value, 'low', '低')

  return rows
})

const visibleMissingRows = computed(() => missingRows.value.slice(0, MAX_VISIBLE_MISSING_ROWS))
const hiddenMissingCount = computed(() => Math.max(0, missingRows.value.length - visibleMissingRows.value.length))

// ─── 3 张卡片计数 ──────────────────────────────────────
function isCoveredByCompetitor(
  query: { top_competitor_coverage?: string[] },
  competitorName: string
): boolean {
  return query.top_competitor_coverage?.includes(competitorName) ?? false
}

function competitorGapCount(group: typeof mergedView.value.scene_coverage.high_value): number {
  return (group.missing_queries ?? []).filter((q) =>
    competitorNames.value.some((cname) => isCoveredByCompetitor(q, cname))
  ).length
}

const highGapCount = computed(
  () => competitorGapCount(mergedView.value.scene_coverage.high_value)
)
const midGapCount = computed(
  () => competitorGapCount(mergedView.value.scene_coverage.mid_value)
)
const lowGapCount = computed(
  () => competitorGapCount(mergedView.value.scene_coverage.low_value)
)

// ─── 底部引用:竞品组优势优先,否则 Top1 竞品 scene_advantages ────────────
const topCompetitor = computed(() =>
  mergedView.value.merged_competitors.find((c) => c.rank === 1) ?? null
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

.p08-body {
  margin-top: 60px;
}

.p08-header {
  margin-bottom: 28px;
}
.p08-subtitle {
  font-size: 11px;
  letter-spacing: 3px;
  color: #6b6456;
  margin-bottom: 8px;
}
.p08-title {
  font-size: 22px;
  font-weight: 700;
  color: #0b1426;
  margin: 0;
}

/* 场景表 */
.p08-matrix {
  margin-bottom: 32px;
}
.p08-row-head,
.p08-row-data {
  /* 60 价值 | 1fr 查询 | 90 意图 | 60 您 | 每竞品 60 */
  /* 3 竞品是固定数,模板直接生成表头和数据的 grid-template-columns */
  grid-template-columns: 60px 1fr 90px 60px 60px 60px 60px;
}
.p08-row-head {
  padding: 12px 0 !important;
}
.p08-col-label {
  font-size: 10px;
  letter-spacing: 2px;
  color: #6b6456;
}
.p08-col-center {
  text-align: center;
}
.p08-query-text {
  font-size: 13px;
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}
.p08-intent-text {
  font-size: 11px;
  color: #6b6456;
}

/* 空态 */
.p08-empty {
  padding: 48px 0;
  text-align: center;
  color: #6b6456;
  font-style: italic;
  border-top: 2px solid #0b1426;
  border-bottom: 2px solid #0b1426;
  margin-bottom: 32px;
}

.p08-limit-note {
  margin: -18px 0 22px;
  font-size: 11px;
  color: #6b6456;
}

/* 3 张卡片 */
.p08-gap-cards {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 16px;
  margin-bottom: 32px;
}
.p08-gap-card {
  background: #f7f3ea;
  padding: 20px;
}
.p08-gap-high {
  border-top: 2px solid #b91c1c;
}
.p08-gap-mid {
  border-top: 2px solid #d97706;
}
.p08-gap-low {
  border-top: 2px solid #6b6456;
}
.p08-gap-label {
  font-size: 10px;
  letter-spacing: 2px;
  margin-bottom: 8px;
}
.p08-gap-label-high {
  color: #b91c1c;
}
.p08-gap-label-mid {
  color: #d97706;
}
.p08-gap-label-low {
  color: #6b6456;
}
.p08-gap-number {
  font-size: 36px;
  color: #0b1426;
}
.p08-gap-denominator {
  font-size: 16px;
  color: #6b6456;
  font-family: 'Noto Sans SC', sans-serif;
  /* 解除 metric-hero 的 letter-spacing / font-weight */
  letter-spacing: 0;
  font-weight: 400;
  margin-left: 6px;
}
.p08-gap-desc {
  font-size: 11px;
  color: #6b6456;
  margin-top: 6px;
}

/* 底部引用的 "原始提取" 弱标签 */
.p08-quote-wrap {
  margin-top: 8px;
}
.p08-raw-tag {
  display: inline-block;
  margin-left: 8px;
  font-size: 10px;
  letter-spacing: 2px;
  color: #9b9486;
  font-style: normal;
  text-transform: uppercase;
}
</style>
