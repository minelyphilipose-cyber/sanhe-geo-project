<template>
  <div class="mobile-page">
    <DashboardCard title="内容交付概览">
      <section class="metric-grid">
        <div v-for="item in overviewCards" :key="item.label" class="inline-metric">
          <van-icon :name="item.icon" />
          <span>{{ item.label }}</span>
          <strong>{{ metricText(item.metric) }}</strong>
        </div>
      </section>
    </DashboardCard>

    <DashboardCard :title="platformCompletionTitle">
      <div v-if="data?.platformCompletion?.length" class="completion-grid">
        <div v-for="item in data.platformCompletion" :key="item.code" class="completion-item">
          <span>{{ contentPlatformLabel(item.code) }}</span>
          <strong>{{ completionMainText(item) }}</strong>
          <small>{{ completionCaption(item) }}</small>
        </div>
      </div>
      <EmptyState v-else description="暂无本月发布数据或逐渠道月度配额。" />
    </DashboardCard>

    <DashboardCard title="内容任务列表">
      <div v-if="taskItems.length" class="task-list">
        <article v-for="item in taskItems" :key="item.draftId" class="task-item">
          <div class="task-main">
            <div class="task-title-row">
              <h3>{{ item.title }}</h3>
              <span class="task-status" :class="item.status">{{ taskStatusLabel(item.status) }}</span>
            </div>
            <div v-if="item.keywords?.length" class="keyword-row">
              <span v-for="keyword in item.keywords" :key="keyword">{{ keyword }}</span>
            </div>
            <div class="task-meta">
              <span>{{ taskPlatforms(item.platformCodes) }}</span>
              <time>{{ formatDate(item.date) }}</time>
            </div>
          </div>
        </article>
      </div>
      <EmptyState v-else :description="data?.taskList?.reason || '暂无内容任务数据'" />
    </DashboardCard>

    <DashboardCard title="自有平台发布情况">
      <section v-if="data?.ownedPublish?.length" class="owned-grid">
        <div v-for="item in data.ownedPublish" :key="item.code" class="owned-item">
          <span>{{ contentPlatformLabel(item.code) }}</span>
          <strong>{{ metricText(item.published, false) }}/{{ metricText(item.indexed, false) }}</strong>
          <small>已发布 / 已收录</small>
        </div>
      </section>
    </DashboardCard>

    <DashboardCard title="生态资产">
      <section v-if="ecoCards.length" class="metric-grid">
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
import { getMobileDashboardContent } from '@/api/mobileDashboard'
import DashboardCard from '@/components/mobile-dashboard/DashboardCard.vue'
import EmptyState from '@/components/mobile-dashboard/EmptyState.vue'
import { useMobileDashboardStore } from '@/stores/mobileDashboard'
import type { ContentDashboardData, DashboardMetric, PlatformCompletion } from '@/types/mobileDashboard'
import { contentPlatformLabel } from '@/utils/mobileDashboardDictionaries'

const store = useMobileDashboardStore()
const data = ref<ContentDashboardData>()

const overviewCards = computed(() => {
  const overview = data.value?.overview
  if (!overview) return []
  return [
    { label: '本月内容', icon: 'description-o', metric: overview.monthContent },
    { label: '已发布', icon: 'passed', metric: overview.published },
    { label: '已收录', icon: 'bookmark-o', metric: overview.indexed },
    { label: '建设中', icon: 'underway-o', metric: overview.building },
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
const taskItems = computed(() => data.value?.taskList?.items || [])
const platformCompletionTitle = computed(() =>
  data.value?.platformCompletion?.some((item) => item.completionRate?.available)
    ? '平台完成度'
    : '平台发布情况'
)

function metricText(metric?: DashboardMetric, includeUnit = true) {
  if (!metric?.available) return '暂未统计'
  const value = metric.value ?? 0
  return includeUnit && metric.unit ? `${value}${metric.unit}` : `${value}`
}

function completionCaption(item: PlatformCompletion) {
  return item.completionRate?.available ? `${item.published}/${item.quota}` : '暂无逐渠道月度配额'
}

function completionMainText(item: PlatformCompletion) {
  return item.completionRate?.available ? metricText(item.completionRate) : `已发布 ${item.published}`
}

function taskStatusLabel(status: string) {
  const labels: Record<string, string> = {
    indexed: '已收录',
    published: '已发布',
    building: '建设中',
  }
  return labels[status] || '暂未统计'
}

function taskPlatforms(codes: string[]) {
  if (!codes?.length) return '待分发'
  return codes.map((code) => contentPlatformLabel(code)).join(' / ')
}

function formatDate(value?: string | null) {
  if (!value) return ''
  return value.slice(0, 10)
}

onMounted(async () => {
  try {
    const res = await getMobileDashboardContent(store.sessionToken)
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

.inline-metric .van-icon {
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  border-radius: 10px;
  background: #e6f7ef;
  color: #07a66b;
  font-size: 17px;
}

.inline-metric span {
  display: block;
  margin-top: 8px;
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

.completion-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.completion-item,
.owned-item {
  min-width: 0;
  padding: 12px;
  border-radius: 12px;
  background: #f8fafc;
}

.completion-item span,
.owned-item span {
  display: block;
  color: #6b7280;
  font-size: 12px;
  white-space: nowrap;
}

.completion-item strong,
.owned-item strong {
  display: block;
  margin-top: 8px;
  color: #0f172a;
  font-size: 18px;
  font-weight: 800;
}

.completion-item small,
.owned-item small {
  display: block;
  margin-top: 4px;
  color: #9ca3af;
  font-size: 11px;
}

.task-list {
  display: grid;
  gap: 10px;
}

.task-item {
  min-width: 0;
  padding: 12px;
  border: 1px solid #eef0f2;
  border-radius: 14px;
  background: #fff;
}

.task-main {
  min-width: 0;
}

.task-title-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.task-title-row h3 {
  flex: 1;
  min-width: 0;
  margin: 0;
  color: #0f172a;
  font-size: 14px;
  font-weight: 800;
  line-height: 1.45;
}

.task-status {
  flex: 0 0 auto;
  padding: 3px 8px;
  border-radius: 999px;
  background: #f3f4f6;
  color: #6b7280;
  font-size: 11px;
  font-weight: 800;
  white-space: nowrap;
}

.task-status.indexed,
.task-status.published {
  background: #e6f7ef;
  color: #07a66b;
}

.task-status.building {
  background: #fff7ed;
  color: #c2410c;
}

.keyword-row {
  display: flex;
  gap: 6px;
  margin-top: 8px;
  overflow-x: auto;
  scrollbar-width: none;
}

.keyword-row::-webkit-scrollbar {
  display: none;
}

.keyword-row span {
  flex: 0 0 auto;
  max-width: 220px;
  padding: 3px 7px;
  border-radius: 999px;
  background: #f8fafc;
  color: #6b7280;
  font-size: 11px;
  font-weight: 700;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-meta {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-top: 8px;
  color: #9ca3af;
  font-size: 11px;
  line-height: 1.5;
}

.task-meta span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-meta time {
  flex: 0 0 auto;
  white-space: nowrap;
}

.owned-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  min-width: 0;
}

.scope-note {
  margin: 10px 0 0;
  color: #9ca3af;
  font-size: 11px;
  line-height: 1.5;
}
</style>
