<template>
  <div class="mobile-page">
    <DashboardCard title="监测总览">
      <section class="metric-grid">
        <div v-for="item in overviewCards" :key="item.label" class="inline-metric">
          <span>{{ item.label }}</span>
          <strong>{{ metricText(item.metric) }}</strong>
        </div>
      </section>
    </DashboardCard>

    <p class="summary-note">当前展示全平台汇总。</p>

    <DashboardCard title="重点问题监测">
      <p v-if="judgeNotice" class="judge-note">{{ judgeNotice }}</p>
      <div v-if="questionItems.length" class="question-list">
        <article v-for="item in questionItems" :key="item.pollResultId" class="question-item">
          <div class="question-avatar">{{ platformInitial(item.platformCode) }}</div>
          <div class="question-main">
            <div class="question-title-row">
              <h3>{{ item.questionTitle }}</h3>
              <span class="question-rank">{{ rightStatus(item) }}</span>
            </div>
            <div class="tag-row">
              <span class="tag platform-tag">{{ itemPlatformSummary(item) }}</span>
              <span v-if="item.mentioned" class="tag success">已提及品牌</span>
              <span v-if="metricBool(item.recommended)" class="tag success">推荐</span>
              <span v-if="metricBool(item.firstRecommend)" class="tag primary">首推</span>
            </div>
            <p v-if="item.evidence" class="question-desc">{{ item.evidence }}</p>
          </div>
        </article>
      </div>
      <EmptyState v-else :description="data?.questionList?.reason || '暂无重点问题监测数据'" />
    </DashboardCard>

    <DashboardCard title="场景表现">
      <div v-if="visibleScenes.length" class="compact-list">
        <div v-for="item in visibleScenes" :key="item.code" class="compact-row">
          <span>{{ sceneLabel(item.code) }}</span>
          <strong>{{ metricText(item.covered, false) }}/{{ metricText(item.total, false) }}</strong>
        </div>
      </div>
      <EmptyState v-else description="暂无场景表现数据" />
    </DashboardCard>

    <DashboardCard title="问题词覆盖进展">
      <section v-if="coverageCards.length" class="metric-grid">
        <div v-for="item in coverageCards" :key="item.label" class="inline-metric">
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
import { getMobileDashboardMonitor } from '@/api/mobileDashboard'
import DashboardCard from '@/components/mobile-dashboard/DashboardCard.vue'
import EmptyState from '@/components/mobile-dashboard/EmptyState.vue'
import { useMobileDashboardStore } from '@/stores/mobileDashboard'
import type { DashboardMetric, MonitorDashboardData, QuestionMonitorItem } from '@/types/mobileDashboard'
import { aiPlatformLabel, sceneLabel } from '@/utils/mobileDashboardDictionaries'

const store = useMobileDashboardStore()
const data = ref<MonitorDashboardData>()

const overviewCards = computed(() => {
  const overview = data.value?.overview
  if (!overview) return []
  return [
    { label: '核心问题', metric: overview.monitoredQuestions },
    { label: '已覆盖问题', metric: overview.brandMentioned },
    { label: 'AI推荐', metric: overview.aiRecommendRate },
    { label: '首推', metric: overview.firstRecommendCount },
  ]
})
const questionItems = computed(() => data.value?.questionList?.items || [])
const judgeNotice = computed(() => {
  const reason = questionItems.value.find((item) => !item.recommended?.available)?.recommended?.reason
  return reason ? `裁判样本分析中，推荐/首推数据达标后展示。${reason}` : ''
})
const visibleScenes = computed(() => (data.value?.scenePerformance || []).filter((item) => item.visible))
const coverageCards = computed(() => {
  const coverage = data.value?.questionCoverage
  if (!coverage) return []
  return [
    { label: '核心已覆盖', metric: coverage.covered },
    { label: '核心监测中', metric: coverage.monitoring },
    { label: '持续建设', metric: coverage.building },
  ]
})

function metricText(metric?: DashboardMetric, includeUnit = true) {
  if (!metric?.available) return '暂未统计'
  const value = metric.value ?? 0
  return includeUnit && metric.unit ? `${value}${metric.unit}` : `${value}`
}

function metricBool(metric?: DashboardMetric<boolean>) {
  return metric?.available && metric.value === true
}

function platformLabel(code: string) {
  return aiPlatformLabel(code)
}

function platformInitial(code: string) {
  const label = platformLabel(code)
  return label.slice(0, 1)
}

function rightStatus(item: QuestionMonitorItem) {
  if (metricBool(item.firstRecommend)) return '首推'
  if (item.rankPosition?.available && item.rankPosition.value) return `第${item.rankPosition.value}位`
  if (metricBool(item.recommended)) return '已推荐'
  if (item.mentioned) return '已提及'
  return '未提及'
}

function itemPlatformSummary(item: QuestionMonitorItem) {
  const codes = item.platformCodes?.length ? item.platformCodes : [item.platformCode]
  const labels = codes.map(platformLabel)
  if (labels.length <= 3) return labels.join('、')
  return `${labels.slice(0, 3).join('、')} +${labels.length - 3}`
}

async function loadData() {
  try {
    const res = await getMobileDashboardMonitor(store.sessionToken)
    data.value = res.data.data
  } catch (error: any) {
    showToast(error?.message || '数据加载失败')
  }
}

onMounted(loadData)
</script>

<style scoped>
.mobile-page {
  display: grid;
  gap: 12px;
  min-width: 0;
  max-width: 100%;
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

.summary-note,
.judge-note {
  margin: 0 2px;
  color: #9ca3af;
  font-size: 11px;
  line-height: 1.5;
}

.judge-note {
  margin: 0 0 10px;
  padding: 8px 10px;
  border-radius: 10px;
  background: #f8fafc;
}

.question-list {
  display: grid;
  gap: 12px;
}

.question-item {
  display: flex;
  gap: 10px;
  min-width: 0;
  padding: 12px;
  border: 1px solid #eef0f2;
  border-radius: 14px;
  background: #fff;
}

.question-avatar {
  flex: 0 0 auto;
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  background: #e6f7ef;
  color: #07a66b;
  font-size: 14px;
  font-weight: 800;
}

.question-main {
  min-width: 0;
  flex: 1;
}

.question-title-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.question-title-row h3 {
  flex: 1;
  min-width: 0;
  margin: 0;
  color: #0f172a;
  font-size: 14px;
  font-weight: 800;
  line-height: 1.45;
}

.question-rank {
  flex: 0 0 auto;
  color: #07a66b;
  font-size: 12px;
  font-weight: 800;
  white-space: nowrap;
}

.tag-row {
  display: flex;
  gap: 6px;
  margin-top: 8px;
  overflow-x: auto;
  scrollbar-width: none;
  max-width: 100%;
}

.tag-row::-webkit-scrollbar {
  display: none;
}

.tag {
  flex: 0 0 auto;
  padding: 3px 7px;
  border-radius: 999px;
  background: #f3f4f6;
  color: #6b7280;
  font-size: 11px;
  font-weight: 700;
  line-height: 1.4;
  white-space: nowrap;
}

.tag.platform-tag {
  background: #f8fafc;
  color: #6b7280;
}

.tag.success,
.tag.primary {
  background: #e6f7ef;
  color: #07a66b;
}

.question-desc {
  margin: 8px 0 0;
  color: #6b7280;
  font-size: 12px;
  line-height: 1.5;
}

.question-desc.muted {
  color: #9ca3af;
}

.compact-list {
  display: grid;
  gap: 12px;
}

.compact-row {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #6b7280;
  font-size: 13px;
}

.compact-row span {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.compact-row strong {
  color: #0f172a;
  font-size: 14px;
}
</style>
