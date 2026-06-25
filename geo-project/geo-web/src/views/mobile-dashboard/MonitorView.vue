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
      <p>当前展示全平台汇总。</p>
    </div>

    <DashboardCard title="核心问题监测" icon="search">
      <p v-if="judgeNotice" class="judge-note">
        <MobileIcon name="info" />
        <span>{{ judgeNotice }}</span>
      </p>
      <div v-if="questionItems.length" class="question-list">
        <article
          v-for="item in displayedQuestionItems"
          :key="item.pollResultId"
          class="question-item"
          :class="{ 'question-item--clickable': item.mentioned }"
          :role="item.mentioned ? 'button' : undefined"
          :tabindex="item.mentioned ? 0 : undefined"
          @click="openQuestionDetail(item)"
          @keyup.enter="openQuestionDetail(item)"
        >
          <div class="question-avatar">
            <img
              v-if="platformLogo(item.platformCode)"
              :src="platformLogo(item.platformCode)"
              :alt="platformLabel(item.platformCode)"
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
          <button type="button" :disabled="questionPage <= 1" @click="questionPage -= 1">
            <MobileIcon name="chevronLeft" />
            上一页
          </button>
          <span>{{ questionPage }} / {{ questionPageCount }}</span>
          <button type="button" :disabled="questionPage >= questionPageCount" @click="questionPage += 1">
            下一页
            <MobileIcon name="chevronRight" />
          </button>
        </nav>
      </div>
      <EmptyState v-else :description="data?.questionList?.reason || '暂无重点问题监测数据'" />
    </DashboardCard>

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
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { getMobileDashboardMonitor, withRenewedMobileDashboardSession } from '@/api/mobileDashboard'
import DashboardCard from '@/components/mobile-dashboard/DashboardCard.vue'
import EmptyState from '@/components/mobile-dashboard/EmptyState.vue'
import MobileIcon from '@/components/mobile-dashboard/MobileIcon.vue'
import { useMobileDashboardStore } from '@/stores/mobileDashboard'
import type { DashboardMetric, MonitorDashboardData, QuestionMonitorItem } from '@/types/mobileDashboard'
import { aiPlatformLabel, sceneLabel } from '@/utils/mobileDashboardDictionaries'
import deepseekLogo from '@/assets/ai-model-logos/deepseek-color.png'
import doubaoLogo from '@/assets/ai-model-logos/doubao.png'
import hunyuanLogo from '@/assets/ai-model-logos/hunyuan-color.png'
import qwenLogo from '@/assets/ai-model-logos/qwen-color.png'
import wenxinLogo from '@/assets/ai-model-logos/文心一言.png'

const store = useMobileDashboardStore()
const router = useRouter()
const data = ref<MonitorDashboardData>()
const questionPage = ref(1)
const questionPageSize = 5
const QUESTION_DETAIL_CACHE_KEY = 'mobile_dashboard_question_detail'
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
const questionPageCount = computed(() => Math.max(1, Math.ceil(questionItems.value.length / questionPageSize)))
const displayedQuestionItems = computed(() => {
  const page = Math.min(questionPage.value, questionPageCount.value)
  const start = (page - 1) * questionPageSize
  return questionItems.value.slice(start, start + questionPageSize)
})
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

function platformLogo(code?: string | null) {
  if (!code) return ''
  return aiPlatformLogos[code] || ''
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
  if (item.evidence?.trim()) return truncateText(item.evidence, 42)
  if (metricBool(item.recommended)) return '本轮回答中出现主动推荐，推荐详情可进入查看。'
  if (item.mentioned) return `${hitPlatformLabel(item)} 回答已提及品牌，推荐与排名结果将在核心样本分析充分后展示。`
  return '相关场景内容正在持续建设与覆盖。'
}

function openQuestionDetail(item: QuestionMonitorItem) {
  if (!item.mentioned) return
  sessionStorage.setItem(QUESTION_DETAIL_CACHE_KEY, JSON.stringify(item))
  router.push({ name: 'MobileDashboardQuestionDetail', params: { pollResultId: String(item.pollResultId) } })
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
      (sessionToken) => getMobileDashboardMonitor(sessionToken),
      store,
    )
    data.value = res.data.data
    questionPage.value = 1
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
  font-size: 12px;
  line-height: 1.35;
}

.inline-metric strong {
  display: block;
  margin-top: 6px;
  color: #131b2e;
  font-size: 18px;
  font-weight: 800;
  line-height: 1.15;
}

.overview-strip .inline-metric strong {
  margin-top: 0;
  font-size: clamp(14px, 4vw, 18px);
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
  font-size: 11px;
  line-height: 1.5;
}

.filter-summary p {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.judge-note {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin: 0 0 12px;
  padding: 8px 10px;
  border-radius: 10px;
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

.question-list {
  display: grid;
  gap: 10px;
}

.question-item {
  display: flex;
  gap: 10px;
  min-width: 0;
  padding: 12px 12px 11px;
  border: 1px solid #eef0f2;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 5px 16px rgba(15, 23, 42, 0.025);
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
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  background: #e6f7ef;
  color: #006D44;
  font-size: 14px;
  font-weight: 800;
}

.question-avatar img {
  width: 24px;
  height: 24px;
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
  align-items: flex-start;
  gap: 8px;
}

.question-title-row h3 {
  flex: 1;
  min-width: 0;
  margin: 0;
  color: #131b2e;
  font-size: 14px;
  font-weight: 800;
  line-height: 1.45;
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.question-rank {
  display: inline-flex;
  align-items: center;
  gap: 1px;
  flex: 0 0 auto;
  color: #006D44;
  font-size: 12px;
  font-weight: 800;
  white-space: nowrap;
}

.question-rank .mobile-icon {
  font-size: 11px;
}

.question-rank.building {
  color: #006D44;
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
  padding: 3px 8px;
  border-radius: 999px;
  background: #f3f4f6;
  color: #6b7280;
  font-size: 11px;
  font-weight: 700;
  line-height: 1.4;
  white-space: nowrap;
}

.tag.platform-tag {
  background: #eef4ff;
  color: #4f6174;
}

.tag.success,
.tag.primary {
  background: #e6f7ef;
  color: #006D44;
}

.tag.building {
  border: 1px solid #d7dee8;
  background: #f8fafc;
  color: #52625C;
}

.question-desc {
  margin: 8px 0 0;
  color: #3d4a41;
  font-size: 12px;
  line-height: 1.5;
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
  padding-top: 2px;
}

.question-pagination button {
  height: 34px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0 12px;
  border: 1px solid #d7f0e5;
  border-radius: 999px;
  background: #fff;
  color: #006D44;
  font-size: 12px;
  font-weight: 800;
}

.question-pagination button:disabled {
  border-color: #eef0f2;
  background: #f8fafc;
  color: #cbd5e1;
}

.question-pagination span {
  min-width: 48px;
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
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
  font-size: 13px;
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
  font-size: 14px;
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
  font-size: 17px;
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
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.coverage-row__meta strong {
  flex: 0 0 auto;
  color: #131b2e;
  font-size: 18px;
  font-weight: 800;
  line-height: 1.1;
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
    font-size: 16px;
  }

  .overview-strip .inline-metric .mobile-icon {
    width: 30px;
    height: 30px;
    font-size: 16px;
  }

  .question-title-row h3 {
    font-size: 13px;
  }

  .question-rank {
    font-size: 11px;
  }
}
</style>
