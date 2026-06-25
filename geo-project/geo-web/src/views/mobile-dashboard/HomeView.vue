<template>
  <div class="mobile-page">
    <DashboardCard>
      <div class="hero-card">
        <div>
          <span class="eyebrow">总体提及率</span>
          <strong>{{ metricText(data?.overallMentionRate) }}</strong>
          <p>核心问题中，品牌被 AI 回答提及的轮询占比</p>
        </div>
        <TrendLineChart :labels="trendLabels" :values="trendValues" />
      </div>
    </DashboardCard>

    <section class="metric-grid">
      <DashboardCard v-for="item in metricCards" :key="item.key" class="metric-card">
        <MobileIcon :name="metricIcons[item.key] || 'dashboard'" />
        <span>{{ metricLabels[item.key] || item.key }}</span>
        <strong>{{ metricText(item.metric) }}</strong>
        <small v-if="metricHint(item.metric)">{{ metricHint(item.metric) }}</small>
      </DashboardCard>
    </section>

    <section class="responsive-pair">
      <DashboardCard title="平台表现" icon="dashboard">
        <p class="card-subtitle">核心问题下各平台提及率</p>
        <div v-if="data?.platformPerformance?.length" class="progress-list">
          <div
            v-for="item in data.platformPerformance"
            :key="item.code"
            class="progress-row"
            :class="{ zero: metricNumber(item.rate) === 0 }"
          >
            <div class="progress-row__meta">
              <span class="platform-name">
                <img v-if="aiPlatformLogo(item.code)" :src="aiPlatformLogo(item.code)" :alt="aiPlatformLabel(item.code)">
                <i v-else>{{ aiPlatformLabel(item.code).slice(0, 1) }}</i>
                <b>{{ aiPlatformLabel(item.code) }}</b>
              </span>
              <strong>{{ metricText(item.rate) }}</strong>
            </div>
            <div class="bar"><i :style="{ width: metricPercent(item.rate) }" /></div>
          </div>
        </div>
        <EmptyState v-else description="暂无平台表现数据" />
      </DashboardCard>

      <DashboardCard title="场景覆盖" icon="cluster">
        <div v-if="visibleScenes.length" class="scene-list">
          <div
            v-for="item in visibleScenes"
            :key="item.code"
            class="scene-row"
            :class="{ zero: sceneCoveredValue(item) === 0 }"
          >
            <div class="scene-row__meta">
              <span>
                <MobileIcon :name="sceneIcons[item.code] || 'tag'" />
                {{ sceneLabel(item.code) }}
              </span>
              <strong>{{ metricText(item.covered, false) }}/{{ metricText(item.total, false) }}</strong>
            </div>
            <div class="bar">
              <i :style="{ width: scenePercent(item) }" />
            </div>
          </div>
        </div>
        <EmptyState v-else description="暂无场景覆盖数据" />
      </DashboardCard>
    </section>

    <DashboardCard v-if="data?.competitorComparison?.available" title="竞品对比" icon="monitor">
      <div v-if="competitorRows.length" class="competitor-list">
        <div
          v-for="item in competitorRows"
          :key="`${item.entityType}-${item.displayName}`"
          class="competitor-row"
          :class="{ highlighted: item.highlight }"
        >
          <div class="competitor-name">
            <span>{{ item.displayName }}</span>
            <i v-if="item.highlight">本品牌</i>
          </div>
          <div class="competitor-stats">
            <div>
              <span>推荐次数</span>
              <strong>{{ item.recommendedCount }}</strong>
            </div>
            <div>
              <span>首推次数</span>
              <strong>{{ item.firstRecommendCount }}</strong>
            </div>
            <div>
              <span>裁判覆盖</span>
              <strong>{{ item.coveragePercent }}%</strong>
            </div>
          </div>
        </div>
      </div>
      <EmptyState v-else description="暂无竞品数据" />
    </DashboardCard>
    <DashboardCard v-else title="竞品对比" icon="monitor">
      <EmptyState description="竞品数据完成核验后展示" />
    </DashboardCard>

    <section class="responsive-pair compact-pair">
      <DashboardCard title="内容交付进展" icon="content">
        <section v-if="data?.contentProgress" class="delivery-metrics">
          <div v-for="item in contentProgressCards" :key="item.label" class="delivery-metric">
            <MobileIcon :name="item.icon" />
            <span>{{ item.label }}</span>
            <strong>{{ metricText(item.metric) }}</strong>
          </div>
        </section>
      </DashboardCard>

      <DashboardCard title="生态资产" icon="cluster">
        <section v-if="data?.ecoAssets" class="eco-metrics">
          <div v-for="item in ecoCards" :key="item.label" class="eco-metric">
            <span>{{ item.label }}</span>
            <strong>{{ metricText(item.metric) }}</strong>
          </div>
        </section>
        <p v-if="data?.ecoAssets?.indexMeasurementScope" class="scope-note">{{ shortIndexScope }}</p>
      </DashboardCard>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { showToast } from 'vant'
import { getMobileDashboardHome, withRenewedMobileDashboardSession } from '@/api/mobileDashboard'
import DashboardCard from '@/components/mobile-dashboard/DashboardCard.vue'
import EmptyState from '@/components/mobile-dashboard/EmptyState.vue'
import MobileIcon from '@/components/mobile-dashboard/MobileIcon.vue'
import TrendLineChart from '@/components/mobile-dashboard/TrendLineChart.vue'
import { useMobileDashboardStore } from '@/stores/mobileDashboard'
import type { DashboardMetric, HomeDashboardData } from '@/types/mobileDashboard'
import { aiPlatformLabel, sceneLabel } from '@/utils/mobileDashboardDictionaries'
import deepseekLogo from '@/assets/ai-model-logos/deepseek-color.png'
import doubaoLogo from '@/assets/ai-model-logos/doubao.png'
import hunyuanLogo from '@/assets/ai-model-logos/hunyuan-color.png'
import qwenLogo from '@/assets/ai-model-logos/qwen-color.png'
import wenxinLogo from '@/assets/ai-model-logos/文心一言.png'

const store = useMobileDashboardStore()
const data = ref<HomeDashboardData>()

const metricLabels: Record<string, string> = {
  ai_recommend_rate: 'AI推荐率',
  first_recommend_count: '首推次数',
  covered_question_count: '核心问题覆盖',
  total_asset_count: '累计资产',
}
const metricIcons: Record<string, string> = {
  ai_recommend_rate: 'star',
  first_recommend_count: 'bars',
  covered_question_count: 'check',
  total_asset_count: 'document',
}
const aiPlatformLogos: Record<string, string> = {
  doubao: doubaoLogo,
  deepseek: deepseekLogo,
  tongyi: qwenLogo,
  qwen: qwenLogo,
  wenxin: wenxinLogo,
  ernie: wenxinLogo,
  yuanbao: hunyuanLogo,
  hunyuan: hunyuanLogo,
}
const sceneIcons: Record<string, string> = {
  brand_awareness: 'document',
  regional_recommendation: 'mapPin',
  decision_scenario: 'grid',
  purchase_consultation: 'cart',
  qa: 'question',
}

const metricCards = computed(() => data.value?.metrics || [])
const visibleScenes = computed(() => (data.value?.sceneCoverage || []).filter((item) => item.visible))
const trendLabels = computed(() => data.value?.trend?.map((item) => item.date.slice(5)) || [])
const trendValues = computed(() => data.value?.trend?.map((item) => item.value) || [])
const competitorRows = computed(() => data.value?.competitorComparison?.rows || [])
const shortIndexScope = computed(() => {
  if (!data.value?.ecoAssets?.indexMeasurementScope) return ''
  return '已收录仅统计可测量渠道，未回查渠道不计入。'
})
const contentProgressCards = computed(() => {
  const progress = data.value?.contentProgress
  if (!progress) return []
  return [
    { label: '本月内容', icon: 'document', metric: progress.monthContent },
    { label: '已发布', icon: 'publish', metric: progress.published },
    { label: '已收录', icon: 'inbox', metric: progress.indexed },
    { label: '建设中', icon: 'tools', metric: progress.building },
  ]
})
const ecoCards = computed(() => {
  const eco = data.value?.ecoAssets
  if (!eco) return []
  return [
    { label: '累计资产', icon: 'article', metric: eco.totalAssets },
    { label: '本月新增', icon: 'plus', metric: eco.monthNew },
    { label: '已收录', icon: 'eye', metric: eco.indexed },
    { label: '核心问题覆盖', icon: 'check', metric: eco.coveredQuestions },
  ]
})

function metricText(metric?: DashboardMetric, includeUnit = true) {
  if (!metric?.available) return '暂未统计'
  const value = metric.value ?? 0
  return includeUnit && metric.unit ? `${value}${metric.unit}` : `${value}`
}

function metricHint(metric?: DashboardMetric) {
  if (metric?.available || !metric?.reason) return ''
  return '样本分析中'
}

function metricNumber(metric?: DashboardMetric<number>) {
  if (!metric?.available) return 0
  return Number(metric.value || 0)
}

function metricPercent(metric?: DashboardMetric<number>) {
  if (!metric?.available) return '0%'
  return `${metric.value ?? 0}%`
}

function aiPlatformLogo(code?: string | null) {
  if (!code) return ''
  return aiPlatformLogos[code] || ''
}

function scenePercent(item: { covered?: DashboardMetric<number>; total?: DashboardMetric<number> }) {
  if (!item.covered?.available || !item.total?.available || !item.total.value) return '0%'
  return `${Math.min(100, Math.round(((item.covered.value || 0) / item.total.value) * 100))}%`
}

function sceneCoveredValue(item: { covered?: DashboardMetric<number> }) {
  return item.covered?.available ? Number(item.covered.value || 0) : 0
}

onMounted(async () => {
  try {
    const res = await withRenewedMobileDashboardSession(
      (sessionToken) => getMobileDashboardHome(sessionToken),
      store,
    )
    data.value = res.data.data
  } catch (error: any) {
    showToast(error?.message || '数据加载失败')
  }
})
</script>

<style scoped>
.mobile-page {
  display: grid;
  gap: 11px;
  min-width: 0;
  max-width: 100%;
}

.hero-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 146px;
  align-items: end;
  gap: 14px;
  min-height: 96px;
}

.eyebrow,
.metric-card span {
  display: block;
  color: #52625C;
  font-size: 12px;
  line-height: 1.35;
}

.metric-card {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr);
  grid-template-rows: auto auto auto;
  align-items: center;
  column-gap: 11px;
  min-height: 76px;
}

.metric-card .mobile-icon {
  grid-row: 1 / 4;
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border-radius: 11px;
  background: #e6f7ef;
  color: #006D44;
  font-size: 18px;
}

.metric-card span,
.metric-card strong,
.metric-card small {
  min-width: 0;
  margin-left: 0;
}

.hero-card strong,
.metric-card strong {
  display: block;
  margin-top: 6px;
  color: #131b2e;
  font-size: 19px;
  font-weight: 800;
  line-height: 1.1;
}

.metric-card small {
  display: block;
  margin-top: 4px;
  color: #7a8982;
  font-size: 10px;
  font-weight: 700;
  line-height: 1.25;
}

.hero-card strong {
  margin-top: 9px;
  font-size: 25px;
}

.hero-card p {
  max-width: 150px;
  margin: 8px 0 0;
  color: #52625C;
  font-size: 12px;
  line-height: 1.45;
}

.hero-card :deep(.mobile-trend-chart) {
  width: 146px;
  height: 82px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  min-width: 0;
}

.responsive-pair {
  display: grid;
  grid-template-columns: 1fr;
  gap: 11px;
  min-width: 0;
}

.compact-pair {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.inline-metric {
  min-width: 0;
  min-height: 96px;
  padding: 11px 12px;
  border-radius: 12px;
  background: #f8fafc;
}

.inline-metric .mobile-icon {
  width: 29px;
  height: 29px;
  display: grid;
  place-items: center;
  border-radius: 10px;
  background: #e6f7ef;
  color: #006D44;
  font-size: 16px;
}

.inline-metric span {
  display: block;
  margin-top: 9px;
  color: #52625C;
  font-size: 12px;
  line-height: 1.35;
}

.inline-metric strong {
  display: block;
  margin-top: 6px;
  color: #131b2e;
  font-size: 18px;
  font-weight: 800;
}

.compact-pair :deep(.mobile-dashboard-card) {
  padding: 14px;
}

.compact-pair :deep(.mobile-dashboard-card__head) {
  margin-bottom: 12px;
}

.compact-pair :deep(.mobile-dashboard-card__head h2) {
  gap: 5px;
  font-size: 15px;
}

.delivery-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px 10px;
  min-width: 0;
}

.delivery-metric {
  min-width: 0;
  display: grid;
  justify-items: center;
  text-align: center;
}

.delivery-metric .mobile-icon {
  width: 31px;
  height: 31px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: #e6f7ef;
  color: #006D44;
  font-size: 16px;
}

.delivery-metric span {
  margin-top: 7px;
  color: #52625C;
  font-size: 11px;
  line-height: 1.3;
}

.delivery-metric strong {
  margin-top: 3px;
  color: #071225;
  font-size: 18px;
  line-height: 1.1;
  font-weight: 800;
}

.eco-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px 12px;
  padding: 2px 2px 0;
  min-width: 0;
}

.eco-metric {
  min-width: 0;
}

.eco-metric span {
  display: block;
  color: #52625C;
  font-size: 11px;
  line-height: 1.35;
}

.eco-metric strong {
  display: block;
  margin-top: 4px;
  color: #071225;
  font-size: 19px;
  line-height: 1.1;
  font-weight: 800;
}

.progress-list,
.scene-list,
.competitor-list {
  display: grid;
  gap: 11px;
}

.card-subtitle {
  margin: -5px 0 12px;
  color: #52625C;
  font-size: 12px;
  line-height: 1.45;
}

.competitor-row {
  display: grid;
  gap: 10px;
  padding: 12px;
  border: 1px solid #eef0f2;
  border-radius: 12px;
  background: #fff;
}

.competitor-row.highlighted {
  border-color: #b9efd5;
  background: #f2fbf7;
}

.competitor-name {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.competitor-name span {
  min-width: 0;
  color: #131b2e;
  font-size: 14px;
  font-weight: 800;
  line-height: 1.35;
}

.competitor-name i {
  flex: 0 0 auto;
  padding: 3px 8px;
  border-radius: 999px;
  background: #006D44;
  color: #fff;
  font-size: 11px;
  font-style: normal;
  font-weight: 700;
  white-space: nowrap;
}

.competitor-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.competitor-stats div {
  min-width: 0;
  padding: 8px;
  border-radius: 10px;
  background: #f8fafc;
}

.competitor-row.highlighted .competitor-stats div {
  background: #fff;
}

.competitor-stats span {
  display: block;
  color: #52625C;
  font-size: 11px;
  line-height: 1.3;
  white-space: nowrap;
}

.competitor-stats strong {
  display: block;
  margin-top: 4px;
  color: #131b2e;
  font-size: 16px;
  font-weight: 800;
  line-height: 1.1;
}

.progress-row,
.scene-row {
  display: grid;
  gap: 6px;
}

.progress-row__meta,
.scene-row__meta,
.compact-row {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #6b7280;
  font-size: 13px;
}

.progress-row.zero .progress-row__meta,
.scene-row.zero .scene-row__meta {
  color: #9aa5ad;
}

.progress-row.zero .progress-row__meta strong,
.scene-row.zero .scene-row__meta strong {
  color: #52625C;
}

.progress-row__meta span,
.scene-row__meta span,
.compact-row span {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.platform-name {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  color: #6b7280;
  font-size: 13px;
}

.platform-name img,
.platform-name i {
  flex: 0 0 auto;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  object-fit: contain;
}

.platform-name i {
  display: grid;
  place-items: center;
  background: #e6f7ef;
  color: #006D44;
  font-size: 11px;
  font-style: normal;
  font-weight: 800;
}

.platform-name b {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-weight: 500;
  white-space: nowrap;
}

.scene-row__meta span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.scene-row__meta .mobile-icon {
  flex: 0 0 auto;
  color: #006D44;
  font-size: 15px;
}

.progress-row__meta strong,
.scene-row__meta strong,
.compact-row strong {
  margin-left: auto;
  color: #131b2e;
  font-size: 14px;
  font-weight: 800;
}

.bar {
  flex: 1;
  height: 6px;
  border-radius: 999px;
  background: #eef0f2;
  overflow: hidden;
}

.bar i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #006D44;
}

.progress-row.zero .bar i,
.scene-row.zero .bar i {
  background: transparent;
}

.scope-note {
  margin: 9px 0 0;
  padding-top: 9px;
  border-top: 1px solid #eef0f2;
  color: #52625C;
  font-size: 11px;
  line-height: 1.45;
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

@media (min-width: 640px) {
  .responsive-pair {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 374px) {
  .compact-pair {
    grid-template-columns: 1fr;
  }

  .hero-card {
    grid-template-columns: minmax(0, 1fr) 112px;
    align-items: flex-start;
    gap: 10px;
  }

  .hero-card :deep(.mobile-trend-chart) {
    width: 112px;
    height: 68px;
  }

  .hero-card p {
    max-width: 128px;
  }

  .progress-row,
  .scene-row {
    gap: 6px;
  }

  .metric-card {
    grid-template-columns: 32px minmax(0, 1fr);
    column-gap: 9px;
    min-height: 76px;
  }

  .metric-card .mobile-icon {
    width: 32px;
    height: 32px;
  }
}
</style>
