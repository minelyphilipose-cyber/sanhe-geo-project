<template>
  <div class="mobile-page">
    <DashboardCard>
      <div class="hero-card">
        <div>
          <span class="eyebrow">总体提及率</span>
          <strong>{{ metricText(data?.overallMentionRate) }}</strong>
          <p>品牌在AI中的可见度与推荐度持续提升</p>
        </div>
        <TrendLineChart :labels="trendLabels" :values="trendValues" />
      </div>
    </DashboardCard>

    <DashboardCard title="核心结果">
      <section class="metric-grid">
        <div v-for="item in data?.coreResults || []" :key="item.key" class="inline-metric">
          <span>{{ resultLabels[item.key] || item.key }}</span>
          <strong>{{ metricText(item.metric) }}</strong>
        </div>
      </section>
    </DashboardCard>

    <DashboardCard title="本期亮点">
      <EmptyState :description="data?.highlights?.reason || '暂无本期亮点数据'" />
    </DashboardCard>

    <DashboardCard title="本期交付摘要">
      <section v-if="deliveryCards.length" class="metric-grid">
        <div v-for="item in deliveryCards" :key="item.label" class="inline-metric">
          <span>{{ item.label }}</span>
          <strong>{{ metricText(item.metric) }}</strong>
        </div>
      </section>
      <p v-if="data?.deliverySummary?.indexMeasurementScope" class="scope-note">{{ data.deliverySummary.indexMeasurementScope }}</p>
    </DashboardCard>

    <DashboardCard title="生态资产">
      <section v-if="ecoCards.length" class="metric-grid">
        <div v-for="item in ecoCards" :key="item.label" class="inline-metric">
          <span>{{ item.label }}</span>
          <strong>{{ metricText(item.metric) }}</strong>
        </div>
      </section>
    </DashboardCard>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { showToast } from 'vant'
import { getMobileDashboardReport } from '@/api/mobileDashboard'
import DashboardCard from '@/components/mobile-dashboard/DashboardCard.vue'
import EmptyState from '@/components/mobile-dashboard/EmptyState.vue'
import TrendLineChart from '@/components/mobile-dashboard/TrendLineChart.vue'
import { useMobileDashboardStore } from '@/stores/mobileDashboard'
import type { DashboardMetric, ReportDashboardData } from '@/types/mobileDashboard'

const store = useMobileDashboardStore()
const data = ref<ReportDashboardData>()
const resultLabels: Record<string, string> = {
  ai_recommend_rate: 'AI推荐率',
  first_recommend_count: '首推次数',
  covered_question_count: '核心问题达标',
  platform_coverage_count: '覆盖平台数',
}

const trendLabels = computed(() => data.value?.trend?.map((item) => item.date.slice(5)) || [])
const trendValues = computed(() => data.value?.trend?.map((item) => item.value) || [])
const deliveryCards = computed(() => {
  const summary = data.value?.deliverySummary
  if (!summary) return []
  return [
    { label: '自有平台发布', metric: summary.published },
    { label: '生态资产新增', metric: summary.assetNew },
    { label: '已收录', metric: summary.indexed },
    { label: '核心问题覆盖', metric: summary.coveredQuestions },
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

onMounted(async () => {
  try {
    const res = await getMobileDashboardReport(store.sessionToken)
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
.inline-metric span {
  display: block;
  color: #9ca3af;
  font-size: 12px;
  line-height: 1.35;
}

.hero-card strong,
.inline-metric strong {
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
  gap: 10px;
  min-width: 0;
}

.inline-metric {
  min-width: 0;
  padding: 12px;
  border-radius: 12px;
  background: #f8fafc;
}

.scope-note {
  margin: 10px 0 0;
  color: #9ca3af;
  font-size: 11px;
  line-height: 1.5;
}
</style>
