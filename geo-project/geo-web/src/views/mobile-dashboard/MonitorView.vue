<template>
  <div class="mobile-page">
    <DashboardCard title="监测总览" icon="dashboard">
      <section class="overview-strip">
        <div v-for="item in overviewCards" :key="item.label" class="inline-metric">
          <MobileIcon :name="item.icon" />
          <strong>{{ metricText(item.metric) }}</strong>
          <span>{{ item.label }}</span>
        </div>
      </section>
    </DashboardCard>

    <div class="filter-summary" aria-label="当前展示范围">
      <MobileIcon name="info" />
      <p>{{ filterSummaryText }}</p>
    </div>

    <nav v-if="platformChips.length" class="platform-filter" aria-label="平台筛选">
      <button
        v-for="chip in platformChips"
        :key="chip.code"
        type="button"
        :class="{ active: selectedPlatform === chip.code }"
        @click="changePlatform(chip.code)"
      >
        {{ chip.label }}
      </button>
    </nav>

    <section class="question-section">
      <h2>核心问题监测</h2>
      <p v-if="judgeNotice" class="judge-note">
        <MobileIcon name="info" />
        <span>{{ judgeNotice }}</span>
      </p>
      <div v-if="questionItems.length" class="question-list">
        <article
          v-for="item in displayedQuestionItems"
          :key="item.keywordResultId || item.pollResultId || item.questionTitle"
          class="question-item"
          :class="{ 'question-item--clickable': item.mentioned && item.pollResultId }"
          :role="item.mentioned && item.pollResultId ? 'button' : undefined"
          :tabindex="item.mentioned && item.pollResultId ? 0 : undefined"
          @click="openQuestionDetail(item)"
          @keyup.enter="openQuestionDetail(item)"
        >
          <div class="question-avatar">
            <img
              v-if="aiPlatformLogoSrc(item)"
              :src="aiPlatformLogoSrc(item)"
              :alt="platformLabel(item.platformCode)"
              @error="fallbackAiPlatformLogo($event, item)"
            >
            <span v-else>{{ platformInitial(item.platformCode) }}</span>
          </div>
          <div class="question-main">
            <div class="question-title-row">
              <h3>{{ item.questionTitle }}</h3>
              <span class="question-rank" :class="{ building: !item.mentioned }">
                {{ rightStatus(item) }}
                <MobileIcon v-if="item.mentioned" name="chevronRight" />
              </span>
            </div>
            <div class="tag-row">
              <span class="tag" :class="item.mentioned ? 'platform-tag' : 'building'">
                {{ item.mentioned ? hitPlatformLabel(item) : '持续覆盖' }}
              </span>
              <span v-for="tag in statusTags(item)" :key="tag.text" class="tag" :class="tag.kind">
                {{ tag.text }}
              </span>
            </div>
            <p class="question-desc">{{ questionSummary(item) }}</p>
          </div>
        </article>
        <nav v-if="questionPageCount > 1" class="question-pagination" aria-label="核心问题监测分页">
          <button type="button" :disabled="questionPage <= 1" @click="changeQuestionPage(questionPage - 1)">
            <MobileIcon name="chevronLeft" />
            上一页
          </button>
          <span>{{ questionPage }} / {{ questionPageCount }}</span>
          <button type="button" :disabled="questionPage >= questionPageCount" @click="changeQuestionPage(questionPage + 1)">
            下一页
            <MobileIcon name="chevronRight" />
          </button>
        </nav>
      </div>
      <EmptyState v-else :description="data?.questionList?.reason || '暂无重点问题监测数据'" />
    </section>

    <DashboardCard title="场景表现分析" icon="cluster">
      <div v-if="visibleScenes.length" class="scene-list">
        <div v-for="item in visibleScenes" :key="item.code" class="scene-row">
          <div class="scene-row__meta">
            <span>{{ sceneLabel(item.code) }}</span>
            <strong>{{ metricText(item.covered, false) }}/{{ metricText(item.total, false) }}</strong>
          </div>
          <div class="bar">
            <i :style="{ width: scenePercent(item) }" />
          </div>
        </div>
      </div>
      <EmptyState v-else description="暂无场景表现数据" />
    </DashboardCard>

    <DashboardCard title="问题词覆盖进展" icon="document">
      <section v-if="coverageCards.length" class="coverage-list">
        <div v-for="item in coverageCards" :key="item.label" class="coverage-row">
          <MobileIcon :name="item.icon" />
          <div class="coverage-row__body">
            <div class="coverage-row__meta">
              <span>{{ item.label }}</span>
              <strong>{{ metricText(item.metric) }}</strong>
            </div>
            <div class="bar"><i :style="{ width: coveragePercent(item.metric) }" /></div>
          </div>
        </div>
      </section>
    </DashboardCard>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { getMobileDashboardMonitor, withRenewedMobileDashboardSession } from '@/api/mobileDashboard'
import DashboardCard from '@/components/mobile-dashboard/DashboardCard.vue'
import EmptyState from '@/components/mobile-dashboard/EmptyState.vue'
import MobileIcon from '@/components/mobile-dashboard/MobileIcon.vue'
import { useMobileDashboardStore } from '@/stores/mobileDashboard'
import type { DashboardMetric, MonitorDashboardData, QuestionMonitorItem } from '@/types/mobileDashboard'
import { aiPlatformLabel, sceneLabel } from '@/utils/mobileDashboardDictionaries'
import { aiPlatformLogoSrc, fallbackAiPlatformLogo } from '@/utils/aiPlatformLogo'

const store = useMobileDashboardStore()
const router = useRouter()
const route = useRoute()
const data = ref<MonitorDashboardData>()
const questionPage = ref(1)
const selectedPlatform = ref('all')
const questionPageSize = 5
const QUESTION_DETAIL_CACHE_KEY = 'mobile_dashboard_question_detail'

const overviewCards = computed(() => {
  const overview = data.value?.overview
  if (!overview) return []
  return [
    { label: '核心问题', icon: 'shield', metric: overview.monitoredQuestions },
    { label: '已覆盖问题', icon: 'check', metric: overview.brandMentioned },
    { label: 'AI推荐', icon: 'star', metric: overview.aiRecommendRate },
    { label: '首推', icon: 'bars', metric: overview.firstRecommendCount },
  ]
})
const questionItems = computed(() => data.value?.questionList?.items || [])
const platformChips = computed(() => [
  { code: 'all', label: '全部' },
  ...(data.value?.platformFilters || []).map((code) => ({ code, label: platformLabel(code) })),
])
const filterSummaryText = computed(() =>
  selectedPlatform.value === 'all'
    ? '当前展示全平台汇总。'
    : `当前展示 ${platformLabel(selectedPlatform.value)} 平台。`
)
const questionPageCount = computed(() => Math.max(1, data.value?.questionList?.totalPages || 1))
const displayedQuestionItems = computed(() => questionItems.value)
const judgeNotice = computed(() => {
  const reason = questionItems.value.find((item) => !item.recommended?.available)?.recommended?.reason
  return reason ? '样本分析中，推荐与排名结果将在核心样本分析充分后展示。' : ''
})
const visibleScenes = computed(() => (data.value?.scenePerformance || []).filter((item) => item.visible))
const coverageCards = computed(() => {
  const coverage = data.value?.questionCoverage
  if (!coverage) return []
  return [
    { label: '核心已覆盖', icon: 'check', metric: coverage.covered },
    { label: '核心监测中', icon: 'search', metric: coverage.monitoring },
    { label: '持续建设', icon: 'clock', metric: coverage.building },
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
  return '建设中'
}

function statusTags(item: QuestionMonitorItem) {
  const tags: Array<{ text: string; kind: string }> = []
  if (item.mentioned) {
    tags.push({ text: '已提及品牌', kind: 'success' })
  }
  if (metricBool(item.recommended)) {
    tags.push({ text: '推荐', kind: 'success' })
  }
  if (metricBool(item.firstRecommend)) {
    tags.push({ text: '首推', kind: 'primary' })
  }
  return tags
}

function hitPlatformLabel(item: QuestionMonitorItem) {
  const codes = item.platformCodes?.length ? item.platformCodes : [item.platformCode]
  return platformLabel(codes[0])
}

function truncateText(text: string, max = 34) {
  const value = text.replace(/\s+/g, ' ').trim()
  if (value.length <= max) return value
  return `${value.slice(0, max)}...`
}

function questionSummary(item: QuestionMonitorItem) {
  const evidence = publicEvidence(item.evidence)
  if (evidence) return truncateText(evidence, 42)
  if (metricBool(item.recommended)) return '本轮回答中出现主动推荐，推荐详情可进入查看。'
  if (item.mentioned) return `${hitPlatformLabel(item)} 回答已提及品牌，推荐与排名结果将在核心样本分析充分后展示。`
  return '相关场景内容正在持续建设与覆盖。'
}

function publicEvidence(value?: string | null) {
  const text = value?.replace(/\s+/g, ' ').trim()
  if (!text) return ''
  const normalized = text.toLowerCase()
  if (
    normalized === 'no_tracked_entity_matched'
    || normalized === 'no_entity_hit'
    || normalized === 'deterministic_no_entity_hit'
    || normalized.startsWith('no_tracked_entity_')
    || normalized.startsWith('deterministic_')
  ) {
    return ''
  }
  return text
}

function openQuestionDetail(item: QuestionMonitorItem) {
  if (!item.mentioned || !item.pollResultId) return
  sessionStorage.setItem(QUESTION_DETAIL_CACHE_KEY, JSON.stringify(item))
  router.push({
    name: 'MobileDashboardQuestionDetail',
    params: {
      shareCode: String(route.params.shareCode || ''),
      pollResultId: String(item.pollResultId),
    },
  })
}

async function changePlatform(code: string) {
  if (selectedPlatform.value === code) return
  selectedPlatform.value = code
  questionPage.value = 1
  await loadData()
}

async function changeQuestionPage(page: number) {
  const nextPage = Math.min(Math.max(page, 1), questionPageCount.value)
  if (nextPage === questionPage.value) return
  questionPage.value = nextPage
  await loadData()
}

function scenePercent(item: { covered?: DashboardMetric<number>; total?: DashboardMetric<number> }) {
  if (!item.covered?.available || !item.total?.available || !item.total.value) return '0%'
  return `${Math.min(100, Math.round(((item.covered.value || 0) / item.total.value) * 100))}%`
}

function coveragePercent(metric?: DashboardMetric<number>) {
  if (!metric?.available || !metric.value) return '0%'
  const total = data.value?.overview?.monitoredQuestions?.value || 0
  if (!total) return '0%'
  return `${Math.min(100, Math.round((metric.value / total) * 100))}%`
}

async function loadData() {
  try {
    const res = await withRenewedMobileDashboardSession(
      (sessionToken) => getMobileDashboardMonitor(sessionToken, selectedPlatform.value, {
        page: questionPage.value,
        size: questionPageSize,
      }),
      store,
    )
    data.value = res.data.data
    questionPage.value = data.value?.questionList?.page || questionPage.value
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
  padding: 12px;
  border-radius: 12px;
  background: #f8fafc;
}

.overview-strip .inline-metric {
  min-height: 94px;
  padding: 12px 5px 10px;
  text-align: center;
}

.inline-metric .mobile-icon {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  margin-bottom: 8px;
  border-radius: 11px;
  background: #e6f7ef;
  color: #006D44;
  font-size: 18px;
}

.overview-strip .inline-metric .mobile-icon {
  margin-inline: auto;
}

.inline-metric span {
  display: block;
  color: #52625C;
  font-size: var(--mobile-text-2xs, 10px);
  font-weight: 500;
  line-height: var(--mobile-leading-label-sm, 14px);
}

.inline-metric strong {
  display: block;
  margin-top: 6px;
  color: #131b2e;
  font-size: var(--mobile-metric, 18px);
  font-weight: 700;
  line-height: var(--mobile-leading-title, 24px);
}

.overview-strip .inline-metric strong {
  margin-top: 0;
  font-size: var(--mobile-metric, 18px);
  white-space: nowrap;
}

.filter-summary {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  margin: -2px 2px 0;
  overflow: hidden;
}

.filter-summary .mobile-icon {
  flex: 0 0 auto;
  color: #006D44;
  font-size: 14px;
}

.filter-summary p,
.judge-note {
  margin: 0 2px;
  color: #52625C;
  font-size: var(--mobile-text-xs, 12px);
  line-height: var(--mobile-leading-label, 16px);
}

.filter-summary p {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.platform-filter {
  display: flex;
  gap: 8px;
  min-width: 0;
  margin: -2px -2px 0;
  padding: 2px;
  overflow-x: auto;
  scrollbar-width: none;
}

.platform-filter::-webkit-scrollbar {
  display: none;
}

.platform-filter button {
  flex: 0 0 auto;
  min-height: 34px;
  padding: 0 14px;
  border: 1px solid var(--mobile-border, #eef0f2);
  border-radius: 999px;
  background: var(--mobile-subtle, #f8fafc);
  color: var(--mobile-muted, #52625C);
  font-size: var(--mobile-text-md, 14px);
  font-weight: 400;
  line-height: var(--mobile-leading-md, 20px);
  white-space: nowrap;
}

.platform-filter button.active {
  border-color: var(--mobile-primary, #006D44);
  background: var(--mobile-primary, #006D44);
  color: #fff;
  box-shadow: 0 4px 12px rgba(0, 109, 68, 0.16);
}

.judge-note {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin: 0 0 12px;
  padding: 12px;
  border-radius: 12px;
  background: #f2fbf7;
  color: #64748b;
}

.judge-note .mobile-icon {
  flex: 0 0 auto;
  margin-top: 1px;
  color: #006D44;
}

.judge-note span {
  min-width: 0;
}

.question-section {
  display: grid;
  gap: 12px;
  min-width: 0;
}

.question-section > h2 {
  margin: 0;
  padding: 0 4px;
  color: #131b2e;
  font-size: var(--mobile-text-lg, 16px);
  font-weight: 600;
  line-height: var(--mobile-leading-lg, 22px);
}

.question-list {
  display: grid;
  gap: 10px;
}

.question-item {
  display: flex;
  gap: 10px;
  min-width: 0;
  padding: 14px;
  border: 1px solid #eef0f2;
  border-radius: 12px;
  background: #fff;
  box-shadow: var(--mobile-card-shadow, 0 4px 20px rgba(15, 23, 42, 0.04));
}

.question-item--clickable {
  cursor: pointer;
  transition: border-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
}

.question-item--clickable:active {
  transform: scale(0.992);
}

.question-item--clickable:focus-visible {
  outline: 2px solid rgba(0, 109, 68, 0.2);
  outline-offset: 2px;
  border-color: rgba(0, 109, 68, 0.35);
}

.question-avatar {
  flex: 0 0 auto;
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  background: #e6f7ef;
  color: #006D44;
  font-size: var(--mobile-text-md, 14px);
  font-weight: 700;
  overflow: hidden;
}

.question-avatar img {
  width: 30px;
  height: 30px;
  object-fit: contain;
}

.question-avatar span {
  line-height: 1;
}

.question-main {
  min-width: 0;
  flex: 1;
}

.question-title-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}

.question-title-row h3 {
  flex: 1;
  min-width: 0;
  margin: 0;
  color: #131b2e;
  font-size: var(--mobile-text-md, 14px);
  font-weight: 700;
  line-height: var(--mobile-leading-md, 20px);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.question-rank {
  display: inline-flex;
  align-items: center;
  gap: 1px;
  flex: 0 0 auto;
  color: #006D44;
  font-size: var(--mobile-text-2xs, 10px);
  font-weight: 700;
  line-height: var(--mobile-leading-label-sm, 14px);
  white-space: nowrap;
}

.question-rank .mobile-icon {
  font-size: var(--mobile-text-2xs, 10px);
}

.question-rank.building {
  color: #006D44;
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
  overflow-x: auto;
  scrollbar-width: none;
  max-width: 100%;
}

.tag-row::-webkit-scrollbar {
  display: none;
}

.tag {
  flex: 0 0 auto;
  padding: 2px 8px;
  border: 1px solid transparent;
  border-radius: 4px;
  background: #f3f4f6;
  color: #6b7280;
  font-size: var(--mobile-text-2xs, 10px);
  font-weight: 500;
  line-height: var(--mobile-leading-label-sm, 14px);
  white-space: nowrap;
}

.tag.platform-tag {
  border-color: #dbeafe;
  background: #eff6ff;
  color: #2563eb;
}

.tag.success,
.tag.primary {
  border-color: rgba(7, 166, 107, 0.2);
  background: #e6f7ef;
  color: #006D44;
}

.tag.building {
  border: 1px solid #d7dee8;
  background: #f8fafc;
  color: #52625C;
}

.question-desc {
  margin: 0;
  color: #3d4a41;
  font-size: var(--mobile-text-xs, 12px);
  font-weight: 400;
  line-height: 18px;
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.question-desc.muted {
  color: #52625C;
}

.question-pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding-top: 4px;
}

.question-pagination button {
  min-width: 116px;
  height: 38px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0 12px;
  border: 1px solid #d7f0e5;
  border-radius: 999px;
  background: #fff;
  color: #006D44;
  font-size: var(--mobile-text-md, 14px);
  font-weight: 400;
  line-height: var(--mobile-leading-md, 20px);
}

.question-pagination button:disabled {
  border-color: #eef0f2;
  background: #f8fafc;
  color: #cbd5e1;
}

.question-pagination span {
  min-width: 48px;
  color: #64748b;
  font-size: var(--mobile-text-xs, 12px);
  font-weight: 700;
  text-align: center;
}

.scene-list {
  display: grid;
  gap: 12px;
}

.scene-row {
  display: grid;
  gap: 7px;
}

.scene-row__meta {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #6b7280;
  font-size: var(--mobile-text-md, 14px);
  line-height: var(--mobile-leading-md, 20px);
}

.scene-row__meta span {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.scene-row__meta strong {
  color: #131b2e;
  font-size: var(--mobile-text-xs, 12px);
  font-weight: 500;
}

.bar {
  height: 7px;
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

.coverage-list {
  display: grid;
  gap: 14px;
}

.coverage-row {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.coverage-row > .mobile-icon {
  flex: 0 0 auto;
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 999px;
  background: #e6f7ef;
  color: #006D44;
  font-size: 18px;
}

.coverage-row__body {
  flex: 1;
  display: grid;
  gap: 7px;
  min-width: 0;
}

.coverage-row__meta {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.coverage-row__meta span {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  color: #6b7280;
  font-size: var(--mobile-text-md, 14px);
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.coverage-row__meta strong {
  flex: 0 0 auto;
  color: #131b2e;
  font-size: var(--mobile-metric, 18px);
  font-weight: 700;
  line-height: var(--mobile-leading-title, 24px);
}

@media (max-width: 374px) {
  .overview-strip {
    gap: 6px;
  }

  .overview-strip .inline-metric {
    min-height: 86px;
    padding: 10px 4px 8px;
  }

  .overview-strip .inline-metric strong {
    font-size: var(--mobile-text-md, 14px);
  }

  .overview-strip .inline-metric .mobile-icon {
    width: 30px;
    height: 30px;
    font-size: 16px;
  }

}
</style>
