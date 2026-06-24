<template>
  <div class="mobile-page">
    <DashboardCard>
      <div class="hero-card">
        <div>
          <span class="eyebrow">总体提及率</span>
          <strong>{{ metricText(data?.overallMentionRate) }}</strong>
          <p>核心问题提及轮询数 / 核心问题完成轮询数</p>
        </div>
        <TrendLineChart :labels="trendLabels" :values="trendValues" />
      </div>
    </DashboardCard>

    <section class="metric-grid">
      <DashboardCard v-for="item in metricCards" :key="item.key" class="metric-card">
        <span>{{ metricLabels[item.key] || item.key }}</span>
        <strong>{{ metricText(item.metric) }}</strong>
      </DashboardCard>
    </section>

    <DashboardCard title="平台表现">
      <div v-if="data?.platformPerformance?.length" class="progress-list">
        <div v-for="item in data.platformPerformance" :key="item.code" class="progress-row">
          <span>{{ aiPlatformLabel(item.code) }}</span>
          <div class="bar"><i :style="{ width: metricPercent(item.rate) }" /></div>
          <strong>{{ metricText(item.rate) }}</strong>
        </div>
      </div>
      <EmptyState v-else description="暂无平台表现数据" />
    </DashboardCard>

    <DashboardCard title="场景覆盖">
      <div v-if="visibleScenes.length" class="compact-list">
        <div v-for="item in visibleScenes" :key="item.code" class="compact-row">
          <span>{{ sceneLabel(item.code) }}</span>
          <strong>{{ metricText(item.covered, false) }}/{{ metricText(item.total, false) }}</strong>
        </div>
      </div>
      <EmptyState v-else description="暂无场景覆盖数据" />
    </DashboardCard>

    <DashboardCard v-if="data?.competitorComparison?.available" title="竞品对比">
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

    <DashboardCard title="内容交付进展">
      <section v-if="data?.contentProgress" class="metric-grid">
        <div v-for="item in contentProgressCards" :key="item.label" class="inline-metric">
          <span>{{ item.label }}</span>
          <strong>{{ metricText(item.metric) }}</strong>
        </div>
      </section>
    </DashboardCard>

    <DashboardCard title="生态资产">
      <section v-if="data?.ecoAssets" class="metric-grid">
        <div v-for="item in ecoCards" :key="item.label" class="inline-metric">
          <span>{{ item.label }}</span>
          <strong>{{ metricText(item.metric) }}</strong>
        </div>
      </section>
      <p v-if="data?.ecoAssets?.indexMeasurementScope" class="scope-note">{{ data.ecoAssets.indexMeasurementScope }}</p>
    </DashboardCard>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { showToast } from 'vant'
import { getMobileDashboardHome } from '@/api/mobileDashboard'
import DashboardCard from '@/components/mobile-dashboard/DashboardCard.vue'
import EmptyState from '@/components/mobile-dashboard/EmptyState.vue'
import TrendLineChart from '@/components/mobile-dashboard/TrendLineChart.vue'
import { useMobileDashboardStore } from '@/stores/mobileDashboard'
import type { DashboardMetric, HomeDashboardData } from '@/types/mobileDashboard'
import { aiPlatformLabel, sceneLabel } from '@/utils/mobileDashboardDictionaries'

const store = useMobileDashboardStore()
const data = ref<HomeDashboardData>()

const metricLabels: Record<string, string> = {
  ai_recommend_rate: 'AI推荐率',
  first_recommend_count: '首推次数',
  covered_question_count: '核心问题达标',
  platform_coverage_count: '覆盖平台数',
}

const metricCards = computed(() => data.value?.metrics || [])
const visibleScenes = computed(() => (data.value?.sceneCoverage || []).filter((item) => item.visible))
const trendLabels = computed(() => data.value?.trend?.map((item) => item.date.slice(5)) || [])
const trendValues = computed(() => data.value?.trend?.map((item) => item.value) || [])
const competitorRows = computed(() => data.value?.competitorComparison?.rows || [])
const contentProgressCards = computed(() => {
  const progress = data.value?.contentProgress
  if (!progress) return []
  return [
    { label: '本月内容', metric: progress.monthContent },
    { label: '已发布', metric: progress.published },
    { label: '已收录', metric: progress.indexed },
    { label: '建设中', metric: progress.building },
  ]
})
const ecoCards = computed(() => {
  const eco = data.value?.ecoAssets
  if (!eco) return []
  return [
    { label: '累计资产', metric: eco.totalAssets },
    { label: '本月新增', metric: eco.monthNew },
    { label: '已收录', metric: eco.indexed },
    { label: '核心问题覆盖', metric: eco.coveredQuestions },
  ]
})

function metricText(metric?: DashboardMetric, includeUnit = true) {
  if (!metric?.available) return '暂未统计'
  const value = metric.value ?? 0
  return includeUnit && metric.unit ? `${value}${metric.unit}` : `${value}`
}

function metricPercent(metric?: DashboardMetric<number>) {
  if (!metric?.available) return '0%'
  return `${metric.value ?? 0}%`
}

onMounted(async () => {
  try {
    const res = await getMobileDashboardHome(store.sessionToken)
    data.value = res.data.data
  } catch (error: any) {
    showToast(error?.message || '数据加载失败')
  }
})
</script>

<style scoped>
.mobile-page {
  display: grid;
  gap: 12px;
  min-width: 0;
  max-width: 100%;
}

.hero-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.eyebrow,
.metric-card span {
  display: block;
  color: #9ca3af;
  font-size: 12px;
  line-height: 1.35;
}

.hero-card strong,
.metric-card strong {
  display: block;
  margin-top: 8px;
  color: #0f172a;
  font-size: 20px;
  font-weight: 800;
  line-height: 1.1;
}

.hero-card p {
  margin: 8px 0 0;
  color: #9ca3af;
  font-size: 12px;
  line-height: 1.5;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  min-width: 0;
}

.inline-metric {
  min-width: 0;
  padding: 12px;
  border-radius: 12px;
  background: #f8fafc;
}

.inline-metric span {
  display: block;
  color: #9ca3af;
  font-size: 12px;
}

.inline-metric strong {
  display: block;
  margin-top: 6px;
  color: #0f172a;
  font-size: 18px;
  font-weight: 800;
}

.progress-list,
.compact-list,
.competitor-list {
  display: grid;
  gap: 12px;
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
  color: #0f172a;
  font-size: 14px;
  font-weight: 800;
  line-height: 1.35;
}

.competitor-name i {
  flex: 0 0 auto;
  padding: 3px 8px;
  border-radius: 999px;
  background: #07a66b;
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
  color: #9ca3af;
  font-size: 11px;
  line-height: 1.3;
  white-space: nowrap;
}

.competitor-stats strong {
  display: block;
  margin-top: 4px;
  color: #0f172a;
  font-size: 16px;
  font-weight: 800;
  line-height: 1.1;
}

.progress-row,
.compact-row {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #6b7280;
  font-size: 13px;
}

.progress-row span,
.compact-row span {
  flex: 0 0 70px;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.progress-row strong,
.compact-row strong {
  margin-left: auto;
  color: #0f172a;
  font-size: 14px;
}

.bar {
  flex: 1;
  height: 7px;
  border-radius: 999px;
  background: #eef0f2;
  overflow: hidden;
}

.bar i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #07a66b;
}

.scope-note {
  margin: 10px 0 0;
  color: #9ca3af;
  font-size: 11px;
  line-height: 1.5;
}
</style>
