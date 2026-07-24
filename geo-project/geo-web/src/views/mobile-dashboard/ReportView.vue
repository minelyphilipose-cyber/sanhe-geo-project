<template>
  <div class="mobile-page">
    <DashboardCard title="阶段成绩" icon="monitor">
      <div class="hero-card">
        <div>
          <span class="eyebrow">总体提及率</span>
          <strong>{{ metricText(data?.overallMentionRate) }}</strong>
          <p>品牌在 AI 回答中的可见度与推荐度</p>
        </div>
        <TrendLineChart :labels="trendLabels" :values="trendValues" />
      </div>
    </DashboardCard>

    <DashboardCard title="核心结果" icon="check">
      <section class="overview-strip">
        <div v-for="item in data?.coreResults || []" :key="item.key" class="inline-metric">
          <MobileIcon :name="resultIcons[item.key] || 'dashboard'" />
          <span>{{ resultLabels[item.key] || item.key }}</span>
          <strong>{{ metricText(item.metric) }}</strong>
        </div>
      </section>
    </DashboardCard>

    <DashboardCard title="本期亮点" icon="star">
      <EmptyState :description="data?.highlights?.reason || '暂无本期亮点数据'" />
    </DashboardCard>

    <DashboardCard title="本期交付摘要" icon="document">
      <section v-if="deliveryCards.length" class="metric-grid">
        <div v-for="item in deliveryCards" :key="item.label" class="inline-metric">
          <MobileIcon :name="item.icon" />
          <span>{{ item.label }}</span>
          <strong>{{ metricText(item.metric) }}</strong>
        </div>
      </section>
      <p v-if="data?.deliverySummary?.indexMeasurementScope" class="scope-note">{{ shortIndexScope }}</p>
    </DashboardCard>

    <DashboardCard title="生态资产" icon="cluster">
      <section v-if="ecoCards.length" class="metric-grid">
        <div v-for="item in ecoCards" :key="item.label" class="inline-metric">
          <MobileIcon :name="item.icon" />
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
import { getMobileDashboardReport, withRenewedMobileDashboardSession } from '@/api/mobileDashboard'
import DashboardCard from '@/components/mobile-dashboard/DashboardCard.vue'
import EmptyState from '@/components/mobile-dashboard/EmptyState.vue'
import MobileIcon from '@/components/mobile-dashboard/MobileIcon.vue'
import TrendLineChart from '@/components/mobile-dashboard/TrendLineChart.vue'
import { useMobileDashboardStore } from '@/stores/mobileDashboard'
import type { DashboardMetric, ReportDashboardData } from '@/types/mobileDashboard'

const store = useMobileDashboardStore()
const data = ref<ReportDashboardData>()
const resultLabels: Record<string, string> = {
  ai_recommend_rate: 'AI推荐率',
  first_recommend_count: '首推次数',
  covered_question_count: '核心问题覆盖',
  total_asset_count: '累计资产',
}
const resultIcons: Record<string, string> = {
  ai_recommend_rate: 'star',
  first_recommend_count: 'bars',
  covered_question_count: 'check',
  total_asset_count: 'document',
}

const trendLabels = computed(() => data.value?.trend?.map((item) => item.date.slice(5)) || [])
const trendValues = computed(() => data.value?.trend?.map((item) => item.value) || [])
const shortIndexScope = computed(() => {
  if (!data.value?.deliverySummary?.indexMeasurementScope) return ''
  return '已收录仅统计可测量渠道，未回查渠道不计入。'
})
const deliveryCards = computed(() => {
  const summary = data.value?.deliverySummary
  if (!summary) return []
  return [
    { label: '代运营平台发布', icon: 'publish', metric: summary.published },
    { label: '生态资产新增', icon: 'cluster', metric: summary.assetNew },
    { label: '已收录', icon: 'eye', metric: summary.indexed },
    { label: '核心问题覆盖', icon: 'check', metric: summary.coveredQuestions },
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

onMounted(async () => {
  try {
    const res = await withRenewedMobileDashboardSession(
      (sessionToken) => getMobileDashboardReport(sessionToken),
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
  color: #52625C;
  font-size: var(--mobile-text-2xs, 10px);
  font-weight: 500;
  line-height: var(--mobile-leading-label-sm, 14px);
}

.hero-card strong,
.inline-metric strong {
  display: block;
  margin-top: 8px;
  color: #131b2e;
  font-size: var(--mobile-metric, 18px);
  font-weight: 700;
  line-height: var(--mobile-leading-title, 24px);
}

.hero-card strong {
  font-size: var(--mobile-metric-lg, 36px);
  font-weight: 700;
  line-height: var(--mobile-leading-display, 40px);
  letter-spacing: -0.04em;
}

.hero-card p {
  margin: 8px 0 0;
  color: #52625C;
  font-size: var(--mobile-text-xs, 12px);
  line-height: var(--mobile-leading-label, 16px);
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  min-width: 0;
}

.overview-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  min-width: 0;
}

.inline-metric {
  min-width: 0;
  min-height: 112px;
  padding: 14px 12px;
  border-radius: 14px;
  background: #f8fafc;
}

.overview-strip .inline-metric {
  padding: 10px 6px;
  text-align: center;
}

.overview-strip .inline-metric strong {
  font-size: var(--mobile-text-md, 14px);
  line-height: var(--mobile-leading-md, 20px);
}

.inline-metric .mobile-icon {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  margin-bottom: 10px;
  border-radius: 13px;
  background: #e6f7ef;
  color: #006D44;
  font-size: 20px;
}

.overview-strip .inline-metric .mobile-icon {
  margin-inline: auto;
}

.scope-note {
  margin: 10px 0 0;
  color: #52625C;
  font-size: var(--mobile-text-xs, 12px);
  line-height: var(--mobile-leading-label, 16px);
}

@media (max-width: 374px) {
  .overview-strip {
    gap: 6px;
  }

  .overview-strip .inline-metric {
    padding: 9px 4px;
  }

  .overview-strip .inline-metric strong {
    font-size: var(--mobile-text-md, 14px);
  }
}
</style>
