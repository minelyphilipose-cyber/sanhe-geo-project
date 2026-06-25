<template>
  <div class="mobile-page">
    <DashboardCard title="内容交付概览" icon="content">
      <section class="overview-strip">
        <div v-for="item in overviewCards" :key="item.label" class="inline-metric">
          <MobileIcon :name="item.icon" />
          <span>{{ item.label }}</span>
          <strong>{{ metricText(item.metric) }}</strong>
        </div>
      </section>
    </DashboardCard>

    <DashboardCard :title="platformCompletionTitle" icon="dashboard">
      <div v-if="data?.platformCompletion?.length" class="completion-rail">
        <div v-for="item in data.platformCompletion" :key="item.code" class="completion-item">
          <div class="platform-icon" :class="`platform-icon--${item.code}`">
            <img
              v-if="contentPlatformLogo(item.code)"
              :src="contentPlatformLogo(item.code)"
              :alt="contentPlatformLabel(item.code)"
            >
            <MobileIcon v-else :name="contentPlatformIcon(item.code)" />
          </div>
          <div class="completion-copy">
            <span>{{ contentPlatformLabel(item.code) }}</span>
            <strong>{{ completionMainText(item) }}</strong>
            <i :style="{ width: completionBarWidth(item) }" />
          </div>
        </div>
      </div>
      <p v-if="completionScopeNote" class="module-note">{{ completionScopeNote }}</p>
      <EmptyState v-else description="暂无本月发布数据或逐渠道月度配额。" />
    </DashboardCard>

    <DashboardCard title="内容任务列表" icon="document">
      <div v-if="taskItems.length" class="task-list">
        <article
          v-for="item in taskItems"
          :key="item.draftId"
          class="task-item"
          :class="{ clickable: canOpenTask(item) }"
          @click="openTask(item)"
        >
          <div class="task-icon" :class="`task-icon--${firstTaskPlatformCode(item.platformCodes)}`">
            <img
              v-if="taskLogo(item.platformCodes)"
              :src="taskLogo(item.platformCodes)"
              :alt="taskPlatforms(item.platformCodes)"
            >
            <span v-else>{{ taskIconText(item.platformCodes) }}</span>
          </div>
          <div class="task-main">
            <div class="task-title-row">
              <h3>{{ item.title }}</h3>
              <span class="task-status" :class="item.status">
                {{ taskStatusLabel(item.status) }}
                <MobileIcon v-if="canOpenTask(item)" name="chevronRight" />
              </span>
            </div>
            <p v-if="item.keywords?.length" class="task-keyword">关键词：{{ item.keywords[0] }}</p>
            <div class="task-meta">
              <span class="task-platform">{{ taskPlatforms(item.platformCodes) }}</span>
              <time>{{ formatDate(item.date) }}</time>
            </div>
          </div>
        </article>
      </div>
      <EmptyState v-else :description="data?.taskList?.reason || '暂无内容任务数据'" />
    </DashboardCard>

    <DashboardCard title="自有平台发布情况" icon="grid">
      <section v-if="data?.ownedPublish?.length" class="owned-grid">
        <div v-for="item in data.ownedPublish" :key="item.code" class="owned-item">
          <div class="platform-symbol" :class="`platform-symbol--${item.code}`">
            <img
              v-if="contentPlatformLogo(item.code)"
              :src="contentPlatformLogo(item.code)"
              :alt="contentPlatformLabel(item.code)"
            >
            <MobileIcon v-else :name="contentPlatformIcon(item.code)" />
          </div>
          <span>{{ contentPlatformLabel(item.code) }}</span>
          <strong>{{ metricText(item.published, false) }}/{{ metricText(item.indexed, false) }}</strong>
          <small>已发布 / 已收录</small>
        </div>
      </section>
    </DashboardCard>

    <DashboardCard title="生态资产" icon="cluster">
      <section v-if="ecoCards.length" class="eco-summary">
        <div v-for="item in ecoCards" :key="item.label" class="eco-item">
          <span>{{ item.label }}</span>
          <strong>{{ metricText(item.metric) }}</strong>
        </div>
      </section>
      <p v-if="data?.ecoAssets?.indexMeasurementScope" class="scope-note">{{ shortIndexScope }}</p>
    </DashboardCard>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { showToast } from 'vant'
import { getMobileDashboardContent, withRenewedMobileDashboardSession } from '@/api/mobileDashboard'
import DashboardCard from '@/components/mobile-dashboard/DashboardCard.vue'
import EmptyState from '@/components/mobile-dashboard/EmptyState.vue'
import MobileIcon from '@/components/mobile-dashboard/MobileIcon.vue'
import { useMobileDashboardStore } from '@/stores/mobileDashboard'
import type { ContentDashboardData, ContentTaskItem, DashboardMetric, PlatformCompletion } from '@/types/mobileDashboard'
import { contentPlatformIcon, contentPlatformLabel } from '@/utils/mobileDashboardDictionaries'
import baijiahaoLogo from '@/assets/self-media-platform-logos/百家号.svg'
import douyinLogo from '@/assets/self-media-platform-logos/抖音.svg'
import officialSiteLogo from '@/assets/self-media-platform-logos/Agent官网.svg'
import toutiaoLogo from '@/assets/self-media-platform-logos/今日头条.svg'
import wechatMpLogo from '@/assets/self-media-platform-logos/公众号.svg'
import xiaohongshuLogo from '@/assets/self-media-platform-logos/小红书.svg'
import zhihuLogo from '@/assets/self-media-platform-logos/知乎.svg'

const store = useMobileDashboardStore()
const data = ref<ContentDashboardData>()
const selfMediaPlatformLogos: Record<string, string> = {
  official_site: officialSiteLogo,
  douyin: douyinLogo,
  xiaohongshu: xiaohongshuLogo,
  wechat_mp: wechatMpLogo,
  toutiao: toutiaoLogo,
  baijiahao: baijiahaoLogo,
  zhihu: zhihuLogo,
}

const overviewCards = computed(() => {
  const overview = data.value?.overview
  if (!overview) return []
  return [
    { label: '本月内容', icon: 'document', metric: overview.monthContent },
    { label: '已发布', icon: 'check', metric: overview.published },
    { label: '已收录', icon: 'inbox', metric: overview.indexed },
    { label: '建设中', icon: 'tools', metric: overview.building },
  ]
})
const ecoCards = computed(() => {
  const eco = data.value?.ecoAssets
  if (!eco) return []
  return [
    { label: '累计资产', icon: 'document', metric: eco.totalAssets },
    { label: '本月新增', icon: 'plus', metric: eco.monthNew },
    { label: '已收录', icon: 'eye', metric: eco.indexed },
    { label: '核心问题覆盖', icon: 'check', metric: eco.coveredQuestions },
  ]
})
const taskItems = computed(() => data.value?.taskList?.items || [])
const platformCompletionTitle = computed(() =>
  data.value?.platformCompletion?.some((item) => item.completionRate?.available)
    ? '平台完成度'
    : '平台发布情况'
)
const completionScopeNote = computed(() => {
  const items = data.value?.platformCompletion || []
  if (!items.length) return ''
  return items.some((item) => item.completionRate?.available)
    ? ''
    : '当前展示各平台真实发布数。'
})
const shortIndexScope = computed(() => {
  if (!data.value?.ecoAssets?.indexMeasurementScope) return ''
  return '已收录仅统计可测量渠道，未回查渠道不计入。'
})

function metricText(metric?: DashboardMetric, includeUnit = true) {
  if (!metric?.available) return '暂未统计'
  const value = metric.value ?? 0
  return includeUnit && metric.unit ? `${value}${metric.unit}` : `${value}`
}

function completionMainText(item: PlatformCompletion) {
  return item.completionRate?.available ? metricText(item.completionRate) : `已发布 ${item.published}`
}

function completionBarWidth(item: PlatformCompletion) {
  if (item.completionRate?.available) {
    return `${Math.max(6, Math.min(100, Number(item.completionRate.value || 0)))}%`
  }
  const maxPublished = Math.max(...(data.value?.platformCompletion || []).map((row) => row.published || 0), 1)
  return `${Math.max(12, Math.round(((item.published || 0) / maxPublished) * 100))}%`
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

function taskIconText(codes: string[]) {
  if (!codes?.length) return '待'
  return contentPlatformLabel(codes[0]).slice(0, 1)
}

function firstTaskPlatformCode(codes: string[]) {
  return codes?.[0] || 'pending'
}

function contentPlatformLogo(code?: string | null) {
  if (!code) return ''
  return selfMediaPlatformLogos[code] || ''
}

function taskLogo(codes: string[]) {
  if (!codes?.length) return ''
  return contentPlatformLogo(codes[0])
}

function formatDate(value?: string | null) {
  if (!value) return ''
  return value.slice(5, 10)
}

function canOpenTask(item: ContentTaskItem) {
  return /^https?:\/\//i.test(item.publishUrl || '')
}

function openTask(item: ContentTaskItem) {
  if (!canOpenTask(item)) return
  window.open(item.publishUrl!, '_blank', 'noopener,noreferrer')
}

onMounted(async () => {
  try {
    const res = await withRenewedMobileDashboardSession(
      (sessionToken) => getMobileDashboardContent(sessionToken),
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

.overview-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  min-width: 0;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  min-width: 0;
}

.inline-metric {
  min-width: 0;
  padding: 10px;
  border-radius: 12px;
  background: #f8fafc;
}

.overview-strip .inline-metric {
  display: grid;
  justify-items: center;
  align-content: center;
  min-height: 80px;
  padding: 8px 4px;
}

.inline-metric .mobile-icon {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border-radius: 10px;
  background: #e6f7ef;
  color: #006D44;
  font-size: 18px;
}

.overview-strip .inline-metric .mobile-icon {
  margin-inline: auto;
  width: 32px;
  height: 32px;
  border-radius: 11px;
}

.inline-metric span {
  display: block;
  margin-top: 7px;
  color: #52625C;
  font-size: var(--mobile-text-xs, 12px);
  font-weight: 500;
  line-height: var(--mobile-leading-label, 16px);
}

.overview-strip .inline-metric span {
  text-align: center;
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.inline-metric strong {
  display: block;
  margin-top: 5px;
  color: #131b2e;
  font-size: var(--mobile-metric, 18px);
  font-weight: 700;
  line-height: var(--mobile-leading-title, 24px);
}

.overview-strip .inline-metric strong {
  text-align: center;
  font-size: var(--mobile-metric, 18px);
}

.completion-rail {
  display: flex;
  gap: 10px;
  min-width: 0;
  margin: 0 -4px;
  padding: 2px 4px 7px;
  overflow-x: auto;
  scrollbar-width: none;
  scroll-snap-type: x proximity;
  -webkit-mask-image: linear-gradient(90deg, #000 0, #000 calc(100% - 24px), transparent 100%);
  mask-image: linear-gradient(90deg, #000 0, #000 calc(100% - 24px), transparent 100%);
}

.completion-rail::-webkit-scrollbar {
  display: none;
}

.completion-item {
  flex: 0 0 132px;
  min-width: 132px;
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr);
  align-items: center;
  gap: 9px;
  padding: 12px;
  border-radius: 14px;
  background: #f8fafc;
  scroll-snap-align: start;
}

.completion-copy {
  min-width: 0;
  flex: 1;
}

.platform-icon {
  flex: 0 0 auto;
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  background: #e6f7ef;
  color: #006D44;
  font-size: 14px;
  font-weight: 700;
}

.platform-icon .mobile-icon {
  font-size: 18px;
}

.platform-icon img,
.platform-symbol img,
.task-icon img {
  display: block;
  max-width: 72%;
  max-height: 72%;
  object-fit: contain;
}

.platform-icon img {
  max-width: 28px;
  max-height: 28px;
}

.platform-symbol img {
  max-width: 30px;
  max-height: 30px;
}

.task-icon img {
  max-width: 30px;
  max-height: 30px;
}

.platform-icon--xiaohongshu {
  background: #fff5f5;
  color: #dc2626;
}

.platform-icon--douyin,
.platform-icon--toutiao,
.platform-icon--baijiahao,
.platform-icon--zhihu,
.platform-symbol--douyin,
.platform-symbol--toutiao,
.platform-symbol--baijiahao,
.platform-symbol--zhihu,
.task-icon--douyin,
.task-icon--toutiao,
.task-icon--baijiahao,
.task-icon--zhihu {
  border: 1px solid #eef0f2;
  background: #fff;
}

.platform-icon--wechat_mp,
.platform-icon--official_site,
.platform-symbol--wechat_mp,
.platform-symbol--official_site,
.task-icon--wechat_mp,
.task-icon--official_site {
  border: 1px solid #d8f5e5;
  background: #fff;
}

.task-icon--xiaohongshu,
.platform-symbol--xiaohongshu {
  border: 1px solid #fee2e2;
  background: #fff5f5;
}

.platform-icon--zhihu {
  background: #eff6ff;
  color: #2563eb;
}

.platform-icon--douyin {
  background: #f8fafc;
  color: #131b2e;
}

.completion-item span,
.owned-item span {
  display: block;
  color: #6b7280;
  font-size: var(--mobile-text-xs, 12px);
  line-height: var(--mobile-leading-label, 16px);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.completion-item strong,
.owned-item strong {
  display: block;
  margin-top: 3px;
  color: #131b2e;
  font-size: var(--mobile-text-lg, 16px);
  font-weight: 600;
  line-height: var(--mobile-leading-lg, 22px);
  white-space: nowrap;
}

.completion-item i {
  display: block;
  width: 100%;
  height: 3px;
  margin-top: 6px;
  border-radius: 999px;
  background: #006D44;
}

.owned-item small {
  display: block;
  margin-top: 5px;
  color: #52625C;
  font-size: var(--mobile-text-xs, 12px);
  line-height: var(--mobile-leading-label, 16px);
}

.module-note {
  margin: 5px 0 0;
  color: #52625C;
  font-size: var(--mobile-text-xs, 12px);
  line-height: var(--mobile-leading-label, 16px);
}

.task-list {
  display: grid;
  gap: 10px;
}

.task-item {
  display: flex;
  gap: 12px;
  min-width: 0;
  padding: 14px;
  border: 1px solid #eef0f2;
  border-radius: 16px;
  background: #fff;
  transition: border-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
}

.task-item.clickable {
  cursor: pointer;
}

.task-item.clickable:active {
  transform: scale(0.99);
  border-color: rgba(0, 109, 68, 0.18);
  box-shadow: 0 6px 16px rgba(0, 109, 68, 0.08);
}

.task-icon {
  flex: 0 0 auto;
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: #e6f7ef;
  color: #006D44;
  font-size: 14px;
  font-weight: 700;
  overflow: hidden;
}

.task-icon span {
  line-height: 1;
}

.task-main {
  flex: 1;
  min-width: 0;
}

.task-title-row {
  display: flex;
  align-items: flex-start;
  gap: 7px;
}

.task-title-row h3 {
  flex: 1;
  min-width: 0;
  margin: 0;
  color: #131b2e;
  display: -webkit-box;
  overflow: hidden;
  font-size: var(--mobile-text-md, 14px);
  font-weight: 700;
  line-height: var(--mobile-leading-md, 20px);
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.task-status {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 3px 7px;
  border-radius: 999px;
  background: #f3f4f6;
  color: #6b7280;
  font-size: var(--mobile-text-xs, 12px);
  font-weight: 500;
  line-height: var(--mobile-leading-label, 16px);
  white-space: nowrap;
}

.task-status .mobile-icon {
  font-size: 10px;
  font-weight: 500;
}

.task-status.indexed,
.task-status.published {
  background: #e6f7ef;
  color: #006D44;
}

.task-status.building {
  background: #eef2ff;
  color: #4f46e5;
}

.task-keyword {
  max-width: 100%;
  margin: 7px 0 0;
  color: #52625C;
  font-size: var(--mobile-text-xs, 12px);
  font-weight: 500;
  line-height: var(--mobile-leading-label, 16px);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-meta {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-top: 7px;
  color: #52625C;
  font-size: var(--mobile-text-xs, 12px);
  line-height: var(--mobile-leading-label, 16px);
}

.task-meta span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-platform {
  font-weight: 700;
}

.task-meta time {
  flex: 0 0 auto;
  white-space: nowrap;
}

.owned-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 9px;
  min-width: 0;
}

.owned-item {
  min-width: 0;
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr);
  column-gap: 10px;
  align-items: center;
  padding: 12px;
  border: 1px solid #eef0f2;
  border-radius: 16px;
  background: #fff;
}

.owned-item .platform-symbol {
  grid-row: span 3;
}

.platform-symbol {
  flex: 0 0 auto;
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  border-radius: 13px;
  background: #e6f7ef;
  color: #006D44;
  font-size: 18px;
}

.platform-symbol--douyin {
  background: #f8fafc;
  color: #131b2e;
}

.platform-symbol--xiaohongshu {
  background: #fff5f5;
  color: #dc2626;
}

.platform-symbol--zhihu {
  background: #eff6ff;
  color: #2563eb;
}

.eco-summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 9px;
  min-width: 0;
}

.eco-item {
  min-width: 0;
  padding: 12px;
  border-radius: 13px;
  background: #f8fafc;
}

.eco-item span {
  display: block;
  color: #52625C;
  font-size: var(--mobile-text-2xs, 10px);
  line-height: var(--mobile-leading-label-sm, 14px);
}

.eco-item strong {
  display: block;
  margin-top: 5px;
  color: #131b2e;
  font-size: var(--mobile-text-lg, 16px);
  font-weight: 600;
  line-height: var(--mobile-leading-lg, 22px);
}

.scope-note {
  margin: 13px 0 0;
  padding-top: 10px;
  border-top: 1px solid #eef0f2;
  color: #52625C;
  font-size: var(--mobile-text-2xs, 10px);
  line-height: var(--mobile-leading-label-sm, 14px);
}

@media (max-width: 374px) {
  .overview-strip {
    gap: 6px;
  }

  .overview-strip .inline-metric {
    padding: 9px 4px;
  }

  .overview-strip .inline-metric strong {
    font-size: var(--mobile-text-lg, 16px);
  }

  .completion-item {
    flex-basis: 112px;
    min-width: 112px;
    gap: 7px;
    padding: 9px;
  }

  .task-item {
    gap: 9px;
    padding: 9px;
  }

  .task-icon {
    width: 36px;
    height: 36px;
  }

  .task-title-row h3 {
    font-size: var(--mobile-text-xs, 12px);
  }
}
</style>
