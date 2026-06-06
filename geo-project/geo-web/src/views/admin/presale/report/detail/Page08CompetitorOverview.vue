<template>
  <section class="report-page page-08">
    <div class="page">
      <div class="page-topbar">
        <span>GEO 诊断报告 · {{ mergedView.brand_name }}</span>
        <span>06 / 竞品对标</span>
      </div>

      <div class="p08-panel">
        <div class="p08-kicker mono">06 · COMPETITIVE BENCHMARK</div>
        <h1 class="p08-headline chinese-serif">{{ storyTitle }}</h1>

        <div v-if="showEvidenceCard && representativeDisplayScene" class="p08-answer-card">
          <div class="p08-small-label">{{ askerLabel }}</div>
          <div class="p08-query">“{{ representativeDisplayScene.query }}”</div>

          <div class="p08-divider"></div>

          <div class="p08-small-label">AI 的回答里出现了</div>
          <div class="p08-chip-row">
            <span v-for="item in representativeAppearedCompetitors" :key="item.name" class="p08-chip p08-chip-hit">
              ✓ {{ item.name }}
              <small>本场景 {{ item.mentioned_platform_count }} 平台</small>
            </span>
          </div>

          <div class="p08-small-label p08-missing-label">未出现</div>
          <div class="p08-chip-row">
            <span class="p08-chip p08-chip-miss">× {{ brandName }}（你）</span>
          </div>
        </div>

        <div v-else-if="layoutMode === 'minimal' && representativeDisplayScene" class="p08-evidence-strip">
          <span>个别推荐场景</span>
          <p>{{ minimalPressureLine }}</p>
        </div>

        <div v-else-if="layoutMode === 'positive'" class="p08-positive-card">
          <span>推荐型高价值场景</span>
          <p>本轮没有发现实质性的“竞品被推荐而你缺席”压制场景，建议把重点放在维持已出现的推荐入口。</p>
        </div>

        <div v-else class="p08-answer-card p08-answer-card-empty">
          <div class="p08-small-label">推荐型高价值场景</div>
          <div class="p08-query">本轮没有足够证据显示“求推荐时被竞品压制”。</div>
          <div class="p08-empty-copy">下方仍保留竞品提及与点名比较结果，便于判断整体竞争位置。</div>
        </div>

        <div v-if="showScoreboard" class="p08-count-section" :class="`p08-count-${layoutMode}`">
          <div class="p08-section-title">{{ barSectionTitle }}</div>
          <div v-if="barRows.length" class="p08-bars">
            <div v-for="row in barRows" :key="row.name" class="p08-bar-row" :class="{ 'is-self': row.isSelf }">
              <div class="p08-bar-name">{{ row.name }}</div>
              <div class="p08-bar-track">
                <div class="p08-bar-fill" :style="{ width: `${row.width}%` }"></div>
              </div>
              <div class="p08-bar-value">{{ row.value }}平台</div>
            </div>
          </div>
          <div v-else class="p08-empty-copy">暂无可展示的平台累计点名次数。</div>
        </div>

        <div v-if="showHeroSelfBlock" class="p08-self-block">
          <div>
            <div class="p08-self-name">{{ brandName }}（你）</div>
            <p>{{ selfBlockCopy }}</p>
          </div>
          <div class="p08-self-count">
            <span>{{ selfPlatformPresenceCount }}</span>
            <small> 平台</small>
          </div>
        </div>

        <div v-if="comparisonCallout" class="p08-comparison-callout">
          <span>点名比较时</span>
          <p>{{ comparisonCallout }}</p>
        </div>

        <div class="p08-landing-copy">
          {{ storyLandingCopy }}
        </div>

        <div class="p08-footnote">
          数据来自本次对 {{ platformCount }} 个 AI 平台的真实测试；竞品名为 AI 在回答中实际提及的机构，未做任何添加。
        </div>
      </div>

    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useMergedView } from '@/composables/presale/useMergedView'
import type {
  CompetitorStoryTier,
  SceneCompetitorPressureCompetitor,
  SceneCompetitorPressureItem
} from '@/types/presale/computed'
import type { MergedCompetitor } from '@/types/presale/merged'

type Page08LayoutMode = 'full' | 'compact' | 'minimal' | 'positive'
const DISPLAY_TARGET_THRESHOLD = 0

const { mergedView: mergedViewRef } = useMergedView()
const mergedView = computed(() => mergedViewRef.value!)

const brandName = computed(() => mergedView.value.brand_name || '本品牌')
const sortedCompetitors = computed<MergedCompetitor[]>(() => [...(mergedView.value.merged_competitors || [])].sort((a, b) => {
  const mentionDiff = (b.mention_count || 0) - (a.mention_count || 0)
  if (mentionDiff !== 0) return mentionDiff
  return a.rank - b.rank
}))
const pressure = computed(() => mergedView.value.scene_competitor_pressure)
const competitorStory = computed(() => mergedView.value.narrative_profile?.competitor_story)
const platformCount = computed(() => mergedView.value.platform_breakdown?.length || 0)
const askerLabel = computed(() => {
  const region = mergedView.value.region?.trim()
  return region ? `一位${region}用户问 AI` : '一位用户问 AI'
})

const storyTitle = computed(() =>
  competitorStory.value?.title || 'AI 推荐竞品时，你需要看清自己缺席在哪些关键场景'
)

const storyLandingCopy = computed(() =>
  competitorStory.value?.landing_copy || '这些不是泛泛的曝光次数，而是用户已经开始向 AI 寻找选择建议的时刻。'
)

const storyTier = computed<CompetitorStoryTier>(() => competitorStory.value?.tier || 'T4')

const competitorNameSet = computed(() =>
  new Set(sortedCompetitors.value.map((item: MergedCompetitor) => item.name).filter(Boolean))
)

function isDisplayPressureScene(item: SceneCompetitorPressureItem): boolean {
  return (item.target_mentioned_platform_count || 0) <= DISPLAY_TARGET_THRESHOLD &&
    (item.competitors || []).some((competitor) =>
      competitorNameSet.value.has(competitor.name) &&
      (competitor.mentioned_platform_count || 0) > 0
    )
}

const displayPressureItems = computed(() =>
  (pressure.value?.items || []).filter((item) => isDisplayPressureScene(item))
)

const platformPresenceByCompetitor = computed<Record<string, number>>(() => {
  const counts: Record<string, number> = {}
  displayPressureItems.value.forEach(item => {
    item.competitors?.forEach(competitor => {
      if (!competitor.name || !competitorNameSet.value.has(competitor.name)) return
      if ((competitor.mentioned_platform_count || 0) <= 0) return
      counts[competitor.name] = (counts[competitor.name] || 0) + (competitor.mentioned_platform_count || 0)
    })
  })
  return counts
})

const selfPlatformPresenceCount = computed(() => {
  return displayPressureItems.value
    .reduce((sum, item) => sum + (item.target_mentioned_platform_count || 0), 0)
})

const platformPresenceRows = computed(() =>
  sortedCompetitors.value
    .map((item) => ({
      name: item.name,
      value: platformPresenceByCompetitor.value[item.name] || 0
    }))
    .filter((item) => item.value > 0)
    .sort((a, b) => b.value - a.value)
)

const displayCompetitorCount = computed(() => platformPresenceRows.value.length)
const displayPlatformTotal = computed(() =>
  platformPresenceRows.value.reduce((sum, item) => sum + item.value, 0)
)

const isSparsePressure = computed(() =>
  displayCompetitorCount.value < 2 || displayPlatformTotal.value < 3
)

const layoutMode = computed<Page08LayoutMode>(() => {
  if (displayPressureItems.value.length <= 0) {
    return 'positive'
  }
  if (storyTier.value === 'T3') {
    return 'minimal'
  }
  if (storyTier.value === 'T2' || storyTier.value === 'T4' || isSparsePressure.value) {
    return 'compact'
  }
  return 'full'
})

const showEvidenceCard = computed(() => layoutMode.value === 'full' || layoutMode.value === 'compact')
const showScoreboard = computed(() => layoutMode.value === 'full' || layoutMode.value === 'compact')
const showHeroSelfBlock = computed(() => layoutMode.value === 'full')

const barRows = computed(() => {
  const competitorRows = platformPresenceRows.value.slice(0, 3)
  const rows = [
    ...competitorRows.map((row) => ({ ...row, isSelf: false })),
    { name: `${brandName.value}（你）`, value: selfPlatformPresenceCount.value, isSelf: true }
  ]
  const max = Math.max(1, ...rows.map((row) => row.value))

  return rows.map((row) => ({
    ...row,
    width: row.value <= 0 ? 0 : Math.round((row.value / max) * 100)
  }))
})

const barSectionTitle = computed(() => '在你缺席的推荐场景中，被 AI 点名的平台次数')

const selfBlockCopy = computed(() => {
  return selfPlatformPresenceCount.value > 0
    ? '在这些推荐场景里，你也有被 AI 主动提起，但还没有形成明显优势。'
    : '在这些推荐场景里，你的名字还没有被 AI 主动提起。'
})

const representativeDisplayScene = computed<SceneCompetitorPressureItem | undefined>(() => {
  return displayPressureItems.value.find(item => item.query)
})

const representativeAppearedCompetitors = computed(() => {
  const item = representativeDisplayScene.value
  if (!item) return [] as SceneCompetitorPressureCompetitor[]
  return (item.competitors || [])
    .filter((competitor: SceneCompetitorPressureCompetitor) => (competitor.mentioned_platform_count || 0) > 0)
    .sort((a: SceneCompetitorPressureCompetitor, b: SceneCompetitorPressureCompetitor) => (b.mentioned_platform_count || 0) - (a.mentioned_platform_count || 0))
})

const minimalPressureLine = computed(() => {
  const top = platformPresenceRows.value[0]
  if (!top) return '个别推荐场景中，竞品先被 AI 提到，你暂未出现。'
  return `${top.name} 被 AI 先提到，你暂未出现。`
})

const topComparisonCompetitor = computed(() => {
  const competitorsWithVerdict = [...sortedCompetitors.value]
    .filter((item: MergedCompetitor) => (item.target_preferred_count || 0) + (item.competitor_preferred_count || 0) > 0)
  const primaryScoreboardCompetitor = platformPresenceRows.value[0]
  if (primaryScoreboardCompetitor) {
    const matched = competitorsWithVerdict.find((item) => item.name === primaryScoreboardCompetitor.name)
    if (matched) return matched
  }
  return competitorsWithVerdict
    .sort((a: MergedCompetitor, b: MergedCompetitor) => (b.competitor_preferred_count || 0) - (a.competitor_preferred_count || 0))[0]
})

const comparisonCallout = computed(() => {
  const item = topComparisonCompetitor.value
  if (!item?.name) return ''
  return `AI 在 ${item.name} 和你之间的判断是：偏向对手 ${item.competitor_preferred_count || 0} 次，偏向你 ${item.target_preferred_count || 0} 次。`
})
</script>

<style scoped>
.page-08 .page {
  background: #f7f4ec;
}

.p08-panel {
  box-sizing: border-box;
  width: 844px;
  margin: 70px auto 0;
  color: #1a2942;
}

.p08-kicker {
  color: #c0841e;
  font-size: 11px;
  letter-spacing: 2px;
  font-weight: 700;
}

.p08-headline {
  width: 760px;
  margin: 10px 0 24px;
  color: #0f1d3a;
  font-size: 34px;
  line-height: 1.45;
  font-weight: 600;
}

.p08-answer-card {
  box-sizing: border-box;
  width: 100%;
  padding: 20px 22px;
  border-radius: 10px;
  background: #fff;
  border: 1px solid #e3ddcd;
  box-shadow: none;
}

.p08-answer-card-empty {
  background: #fbf6ed;
}

.p08-small-label {
  color: #8a8472;
  font-size: 11px;
  line-height: 1.4;
  letter-spacing: 1px;
  font-weight: 700;
}

.p08-query {
  margin-top: 7px;
  color: #0f1d3a;
  font-size: 22px;
  line-height: 1.6;
  font-weight: 700;
}

.p08-divider {
  height: 1px;
  margin: 16px 0;
  background: #eee7d7;
}

.p08-chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 9px;
}

.p08-missing-label {
  margin-top: 18px;
}

.p08-chip {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-height: 29px;
  padding: 5px 11px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 700;
}

.p08-chip small {
  color: inherit;
  opacity: 0.68;
  font-size: 12px;
}

.p08-chip-hit {
  color: #854f0b;
  background: #fbeede;
  border: 0;
}

.p08-chip-miss {
  color: #9a2f2f;
  background: #f6e4e4;
}

.p08-count-section {
  box-sizing: border-box;
  width: 100%;
  margin-top: 26px;
  overflow: hidden;
}

.p08-count-compact {
  margin-top: 20px;
  padding: 14px 18px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.48);
  border: 1px solid #eee7d7;
}

.p08-section-title {
  margin-bottom: 12px;
  color: #8a8472;
  font-size: 11px;
  line-height: 1.4;
  letter-spacing: 1px;
  font-weight: 700;
}

.p08-bars {
  display: flex;
  flex-direction: column;
  gap: 9px;
}

.p08-bar-row {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr) 72px;
  align-items: center;
  gap: 12px;
}

.p08-bar-name {
  min-width: 0;
  color: #1a2942;
  font-size: 14px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.p08-bar-track {
  height: 22px;
  border-radius: 4px;
  background: #eee7d7;
  overflow: hidden;
}

.p08-bar-fill {
  height: 100%;
  border-radius: 4px;
  background: #c0841e;
}

.p08-bar-value {
  color: #0f1d3a;
  font-size: 16px;
  line-height: 1;
  font-weight: 700;
  text-align: right;
  white-space: nowrap;
}

.p08-bar-row.is-self .p08-bar-name {
  color: #0f1d3a;
  font-weight: 700;
}

.p08-bar-row.is-self .p08-bar-track {
  background: #d9d1c0;
}

.p08-bar-row.is-self .p08-bar-fill {
  background: #0f1d3a;
}

.p08-self-block {
  box-sizing: border-box;
  width: 100%;
  margin-top: 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 16px 20px;
  border-radius: 8px;
  background: #0f1d3a;
  color: #f7f4ec;
}

.p08-self-name {
  font-size: 15px;
  font-weight: 600;
}

.p08-self-block p {
  margin: 3px 0 0;
  color: #b9c2d4;
  font-size: 12px;
  line-height: 1.5;
}

.p08-self-count {
  flex: 0 0 auto;
  color: #b9c2d4;
  text-align: right;
}

.p08-self-count span {
  color: #e0894e;
  font-size: 38px;
  line-height: 1;
  font-weight: 700;
}

.p08-self-count small {
  color: #b9c2d4;
  font-size: 13px;
  font-weight: 500;
}

.p08-comparison-callout {
  box-sizing: border-box;
  width: 100%;
  margin-top: 24px;
  padding: 4px 0 4px 16px;
  border-radius: 0;
  border-left: 3px solid #c0841e;
  background: transparent;
}

.p08-comparison-callout span {
  display: none;
}

.p08-comparison-callout p {
  margin: 0;
  color: #1a2942;
  font-size: 18px;
  line-height: 1.7;
  font-weight: 700;
}

.p08-evidence-strip,
.p08-positive-card {
  box-sizing: border-box;
  width: 100%;
  padding: 16px 20px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid #e3ddcd;
}

.p08-evidence-strip span,
.p08-positive-card span {
  color: #8a8472;
  font-size: 11px;
  letter-spacing: 1px;
  font-weight: 700;
}

.p08-evidence-strip p,
.p08-positive-card p {
  margin: 7px 0 0;
  color: #0f1d3a;
  font-size: 18px;
  line-height: 1.6;
  font-weight: 700;
}

.p08-landing-copy {
  box-sizing: border-box;
  width: 100%;
  margin-top: 22px;
  padding: 15px 20px;
  border-radius: 8px;
  background: #fbeede;
  color: #0f1d3a;
  font-size: 18px;
  line-height: 1.7;
  font-weight: 700;
}

.p08-footnote {
  margin-top: 18px;
  width: 100%;
  color: #877462;
  font-size: 11px;
  line-height: 1.6;
}

.p08-empty-copy {
  margin-top: 10px;
  color: #8a7461;
  font-size: 14px;
  line-height: 1.6;
}
</style>
