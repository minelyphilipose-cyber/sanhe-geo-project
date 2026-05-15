<template>
  <div class="dashboard-page">
    <div v-if="loading" class="center-state">
      <div class="state-card">
        <el-icon class="is-loading" :size="32"><Loading /></el-icon>
        <div class="state-title">正在加载项目售后看板</div>
        <div class="state-subtitle">请稍候，系统正在获取看板数据。</div>
      </div>
    </div>

    <div v-else-if="loadError" class="center-state">
      <div class="state-card">
        <div class="state-title">链接无效或已停用</div>
        <div class="state-subtitle">请联系服务团队获取新的售后看板链接。</div>
      </div>
    </div>

    <template v-else>
      <section class="page-header">
        <div class="container header-grid">
          <div>
            <div class="eyebrow">项目售后看板</div>
            <h1>{{ summary.projectName || '项目售后看板' }}</h1>
            <p>{{ summary.brandName || '品牌信息未提供' }}</p>
          </div>
          <div class="header-meta">
            <div>
              <span>服务阶段</span>
              <strong>{{ projectStageLabel }}</strong>
            </div>
            <div>
              <span>服务周期</span>
              <strong>{{ servicePeriod }}</strong>
            </div>
            <div>
              <span>监测范围</span>
              <strong>平台 {{ formatNum(summary.monitorPlatformCount) }} 个 / 问题 {{ formatNum(summary.monitorQuestionCount) }} 条</strong>
            </div>
            <div>
              <span>看板数据更新时间</span>
              <strong>{{ formatDateTime(summary.refreshedAt) }}</strong>
            </div>
          </div>
        </div>
      </section>

      <main class="container main-content">
        <section class="toolbar">
          <div class="period-tabs">
            <button
              v-for="item in periodOptions"
              :key="item.value"
              class="period-button"
              :class="{ active: selectedDays === item.value }"
              @click="changeDays(item.value)"
            >
              {{ item.label }}
            </button>
          </div>
          <div class="window-note">当前展示近 {{ selectedDays }} 天窗口内聚合数据</div>
        </section>

        <section class="metrics-grid">
          <article class="metric-card">
            <span>AI 命中总量</span>
            <strong>{{ formatNum(summary.summary?.hitTotal) }}</strong>
            <small>窗口内命中次数合计</small>
          </article>
          <article class="metric-card">
            <span>命中平台数</span>
            <strong>{{ formatNum(summary.summary?.platformCount) }}</strong>
            <small>窗口内命中过的平台去重</small>
          </article>
          <article class="metric-card">
            <span>官网曝光数</span>
            <strong>{{ formatNum(summary.summary?.siteTotal) }}</strong>
            <small>AI 回答中明确提及官网</small>
          </article>
          <article class="metric-card">
            <span>联系曝光数</span>
            <strong>{{ formatNum(summary.summary?.contactTotal) }}</strong>
            <small>AI 回答中明确提及联系方式</small>
          </article>
        </section>

        <section class="panel">
          <div class="panel-header">
            <div>
              <h2>内容交付概览</h2>
              <span>项目累计交付状态，不受上方时间筛选影响</span>
            </div>
            <span class="panel-badge">项目累计</span>
          </div>
          <div class="progress-grid">
            <article
              v-for="item in contentProgressItems"
              :key="item.key"
              class="progress-card"
              :class="`progress-${item.key}`"
            >
              <span>{{ item.label }}</span>
              <strong>{{ formatNum(item.value) }}</strong>
              <small>{{ item.description }}</small>
            </article>
          </div>
        </section>

        <section v-if="hasAdvice" class="panel advice-panel">
          <div class="panel-header">
            <div>
              <h2>服务观察与下阶段动作</h2>
              <span>由服务团队结合项目数据人工维护</span>
            </div>
            <span class="panel-badge">客户可见</span>
          </div>
          <p v-if="summary.advice?.summary" class="advice-summary">{{ summary.advice.summary }}</p>
          <div class="advice-grid">
            <article v-if="summary.advice?.highlights?.length" class="advice-block">
              <h3>服务亮点</h3>
              <ul>
                <li v-for="item in summary.advice.highlights" :key="item">{{ item }}</li>
              </ul>
            </article>
            <article v-if="summary.advice?.improvementDirections?.length" class="advice-block">
              <h3>待加强方向</h3>
              <ul>
                <li v-for="item in summary.advice.improvementDirections" :key="item">{{ item }}</li>
              </ul>
            </article>
            <article v-if="summary.advice?.nextActions?.length" class="advice-block">
              <h3>下阶段动作</h3>
              <ul>
                <li v-for="item in summary.advice.nextActions" :key="item">{{ item }}</li>
              </ul>
            </article>
          </div>
        </section>

        <section class="split-grid">
          <article class="panel">
            <div class="panel-header">
              <h2>平台表现分布</h2>
              <span>按 AI 命中数排序</span>
            </div>
            <div class="platform-list">
              <div v-for="item in summary.platforms || []" :key="item.platformCode" class="platform-item">
                <div>
                  <strong>{{ item.platformName || item.platformCode }}</strong>
                  <span>{{ item.platformCode }}</span>
                </div>
                <div class="platform-stats">
                  <b>{{ formatNum(item.hitCount) }}</b>
                  <span>官网 {{ formatNum(item.siteCount) }} / 联系 {{ formatNum(item.contactCount) }}</span>
                </div>
              </div>
              <div v-if="!(summary.platforms || []).length" class="empty-state">暂无平台数据</div>
            </div>
          </article>

          <article class="panel">
            <div class="panel-header">
              <h2>高频命中问题</h2>
              <span>来自已命中监测问题</span>
            </div>
            <div class="question-cloud">
              <span
                v-for="item in visibleWordCloud"
                :key="item.word"
                class="question-chip"
                :class="wordSizeClass(item.frequency)"
              >
                {{ item.word }}
              </span>
              <div v-if="!visibleWordCloud.length" class="empty-state">暂无高频命中问题</div>
            </div>
          </article>
        </section>

        <section class="panel">
          <div class="panel-header">
            <h2>效果趋势</h2>
            <span>AI 命中数 / 文章创建 / 文章发布</span>
          </div>
          <div class="chart-area">
            <canvas ref="trendCanvasRef"></canvas>
          </div>
        </section>

        <section class="panel">
          <div class="panel-header detail-header">
            <div>
              <h2>命中明细</h2>
              <span>默认展示当前 {{ selectedDays }} 天窗口内的已命中记录，最多在线查看 {{ formatNum(details.maxViewable) }} 条</span>
            </div>
            <button class="filter-toggle" @click="filterExpanded = !filterExpanded">
              {{ filterExpanded ? '收起筛选' : '展开筛选' }}
            </button>
          </div>

          <div v-show="filterExpanded" class="filter-panel">
            <select v-model="detailQuery.platformCode" @change="searchDetails">
              <option value="">全部平台</option>
              <option v-for="item in summary.platforms || []" :key="item.platformCode" :value="item.platformCode">
                {{ item.platformName || item.platformCode }}
              </option>
            </select>
            <input v-model="detailQuery.startDate" type="date" :min="windowStartDate" :max="windowEndDate" />
            <input v-model="detailQuery.endDate" type="date" :min="windowStartDate" :max="windowEndDate" />
            <input v-model="detailQuery.keyword" type="text" placeholder="搜索问题或关键词" @keyup.enter="searchDetails" />
            <button @click="searchDetails">查询</button>
          </div>

          <div class="desktop-table">
            <table>
              <thead>
                <tr>
                  <th>问题或关键词</th>
                  <th>AI 平台</th>
                  <th>查询日期</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in details.items" :key="item.id">
                  <td>{{ item.questionText || '-' }}</td>
                  <td>{{ item.platformName || item.platformCode }}</td>
                  <td>{{ item.batchDate || '-' }}</td>
                  <td>
                    <a v-if="item.platformUrl" :href="item.platformUrl" target="_blank" rel="noopener noreferrer">转到平台</a>
                    <span v-else>-</span>
                  </td>
                </tr>
                <tr v-if="!details.items.length">
                  <td colspan="4"><div class="empty-state">暂无命中明细</div></td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="mobile-cards">
            <article v-for="item in details.items" :key="item.id" class="detail-card">
              <strong>{{ item.questionText || '-' }}</strong>
              <div>
                <span>{{ item.platformName || item.platformCode }}</span>
                <span>{{ item.batchDate || '-' }}</span>
              </div>
              <a v-if="item.platformUrl" :href="item.platformUrl" target="_blank" rel="noopener noreferrer">转到平台</a>
            </article>
            <div v-if="!details.items.length" class="empty-state">暂无命中明细</div>
          </div>

          <div class="pagination-wrap">
            <el-pagination
              background
              layout="prev, pager, next, total"
              :current-page="detailPage.page"
              :page-size="detailPage.size"
              :total="details.total"
              @current-change="onDetailPageChange"
            />
          </div>
        </section>

        <section class="method-note">
          <strong>数据口径说明</strong>
          <p>看板聚合数据按小时刷新，命中明细按当前筛选条件查询。AI 平台结果存在动态波动，数据用于项目交付过程和 GEO 曝光效果查看。</p>
          <p>联系曝光指 AI 回答中明确提及客户电话、邮箱、微信、地址等联系方式；官网曝光指 AI 回答中明确提及官网链接或官网入口。</p>
          <p>内容交付概览为项目累计口径，不受近 7/30/90 天筛选影响；已分发统计已实际进入执行的分发任务，不包含待执行和失败任务；生成失败和分发失败分开展示。</p>
        </section>
      </main>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Loading } from '@element-plus/icons-vue'
import {
  getPublicProjectDashboardDetails,
  getPublicProjectDashboardSummary,
  getPublicProjectDashboardTrend,
} from '@/api/projectDashboard'
import { PROJECT_STAGE_MAP } from '@/utils/constants'
import type {
  ProjectDashboardContentProgressItem,
  ProjectDashboardDetailResponse,
  ProjectDashboardSummaryResponse,
  ProjectDashboardTrendItem,
  ProjectDashboardTrendResponse,
  ProjectDashboardWordItem,
} from '@/types'

const route = useRoute()
const shareCode = String(route.params.shareCode || '')
const trendCanvasRef = ref<HTMLCanvasElement>()

const periodOptions = [
  { label: '近 7 天', value: 7 },
  { label: '近 30 天', value: 30 },
  { label: '近 90 天', value: 90 },
]

const loading = ref(true)
const loadError = ref(false)
const selectedDays = ref(30)
const filterExpanded = ref(false)

const summary = reactive<ProjectDashboardSummaryResponse>({
  projectName: '',
  brandName: '',
  projectStage: '',
  startDate: '',
  endDate: '',
  monitorPlatformCount: 0,
  monitorQuestionCount: 0,
  days: 30,
  summary: {
    hitTotal: 0,
    platformCount: 0,
    contactTotal: 0,
    siteTotal: 0,
  },
  platforms: [],
  wordCloud: [],
  contentProgress: {
    generatedCount: 0,
    approvedCount: 0,
    distributedCount: 0,
    publishedCount: 0,
    pendingCount: 0,
    generationFailureCount: 0,
    distributionFailureCount: 0,
    items: [],
  },
  advice: null,
  refreshedAt: '',
})

const trend = reactive<ProjectDashboardTrendResponse>({ items: [] })
const details = reactive<ProjectDashboardDetailResponse>({
  total: 0,
  page: 1,
  size: 20,
  maxViewable: 5000,
  items: [],
})

const detailPage = reactive({ page: 1, size: 20 })
const detailQuery = reactive({
  platformCode: '',
  startDate: '',
  endDate: '',
  keyword: '',
})

const visibleWordCloud = computed<ProjectDashboardWordItem[]>(() => (summary.wordCloud || []).slice(0, 20))
const hasAdvice = computed(() => {
  const advice = summary.advice
  if (!advice) return false
  return Boolean(
    advice.summary ||
    advice.highlights?.length ||
    advice.improvementDirections?.length ||
    advice.nextActions?.length,
  )
})
const contentProgressItems = computed<ProjectDashboardContentProgressItem[]>(() => {
  const items = summary.contentProgress?.items || []
  if (items.length) return items
  const progress = summary.contentProgress
  return [
    { key: 'generated', label: '已生成', value: progress?.generatedCount || 0, description: '已进入内容库的文章草稿数量' },
    { key: 'approved', label: '已审核通过', value: progress?.approvedCount || 0, description: '当前处于审核通过后链路的文章数量' },
    { key: 'distributed', label: '已分发', value: progress?.distributedCount || 0, description: '已实际进入分发执行的去重文章数量' },
    { key: 'published', label: '发布成功', value: progress?.publishedCount || 0, description: '分发任务成功提交或确认的去重文章数量' },
    { key: 'pending', label: '待处理', value: progress?.pendingCount || 0, description: '待审核/待修改文章与待执行分发任务按文章去重' },
    { key: 'generation_failed', label: '生成失败', value: progress?.generationFailureCount || 0, description: '内容生成批次中的失败条目数量' },
    { key: 'distribution_failed', label: '分发失败', value: progress?.distributionFailureCount || 0, description: '分发任务失败的去重文章数量' },
  ]
})
const projectStageLabel = computed(() => {
  const key = String(summary.projectStage || '')
  return PROJECT_STAGE_MAP[key as keyof typeof PROJECT_STAGE_MAP]?.label || key || '-'
})
const windowEndDate = computed(() => formatDate(new Date()))
const windowStartDate = computed(() => {
  const date = new Date()
  date.setDate(date.getDate() - selectedDays.value + 1)
  return formatDate(date)
})
const servicePeriod = computed(() => {
  if (!summary.startDate && !summary.endDate) return '-'
  const start = summary.startDate || '未设置'
  const end = summary.endDate || '未设置'
  return `${start} 至 ${end}`
})

async function loadSummary() {
  const { data } = await getPublicProjectDashboardSummary(shareCode, { days: selectedDays.value })
  const payload = data.data
  Object.assign(summary, payload || {})
  if (payload?.days && periodOptions.some((item) => item.value === payload.days)) {
    selectedDays.value = payload.days
  }
}

async function loadTrend() {
  const { data } = await getPublicProjectDashboardTrend(shareCode, { days: selectedDays.value })
  trend.items = data.data?.items || []
}

async function loadDetails() {
  ensureDetailDateWindow()
  const { data } = await getPublicProjectDashboardDetails(shareCode, {
    page: detailPage.page,
    size: detailPage.size,
    platformCode: detailQuery.platformCode || undefined,
    startDate: detailQuery.startDate || undefined,
    endDate: detailQuery.endDate || undefined,
    keyword: detailQuery.keyword || undefined,
  })
  Object.assign(details, data.data || {})
}

async function changeDays(days: number) {
  selectedDays.value = days
  detailPage.page = 1
  syncDetailDateWindow()
  await Promise.all([loadSummary(), loadTrend(), loadDetails()])
}

function searchDetails() {
  detailPage.page = 1
  void loadDetails()
}

function onDetailPageChange(page: number) {
  detailPage.page = page
  void loadDetails()
}

function syncDetailDateWindow() {
  detailQuery.startDate = windowStartDate.value
  detailQuery.endDate = windowEndDate.value
}

function ensureDetailDateWindow() {
  if (!detailQuery.startDate || detailQuery.startDate < windowStartDate.value) {
    detailQuery.startDate = windowStartDate.value
  }
  if (!detailQuery.endDate || detailQuery.endDate > windowEndDate.value) {
    detailQuery.endDate = windowEndDate.value
  }
  if (detailQuery.startDate > detailQuery.endDate) {
    detailQuery.startDate = windowStartDate.value
    detailQuery.endDate = windowEndDate.value
  }
}

function formatDate(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function formatNum(value?: number | null) {
  return Number(value || 0).toLocaleString()
}

function formatDateTime(value?: string | null) {
  if (!value) return '-'
  const normalized = value.replace('T', ' ')
  if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}/.test(normalized)) {
    return normalized.slice(0, 19)
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return normalized
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  const seconds = String(date.getSeconds()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
}

function wordSizeClass(frequency: number) {
  const values = visibleWordCloud.value.map((item) => item.frequency).sort((a, b) => b - a)
  if (!values.length) return 'sm'
  if (frequency >= values[0]) return 'xl'
  if (frequency >= (values[1] || values[0])) return 'lg'
  if (frequency >= (values[4] || values[1] || values[0])) return 'md'
  return 'sm'
}

function drawTrendChart() {
  const canvas = trendCanvasRef.value
  const parent = canvas?.parentElement
  const ctx = canvas?.getContext('2d')
  if (!canvas || !parent || !ctx) return

  const width = parent.clientWidth
  const height = parent.clientHeight
  const dpr = window.devicePixelRatio || 1
  canvas.width = width * dpr
  canvas.height = height * dpr
  canvas.style.width = `${width}px`
  canvas.style.height = `${height}px`
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  ctx.clearRect(0, 0, width, height)

  const items = trend.items || []
  const padL = 44
  const padR = 20
  const padT = 16
  const padB = 38
  const chartW = width - padL - padR
  const chartH = height - padT - padB
  const maxVal = Math.max(10, ...items.map((item) => Math.max(item.hitCount || 0, item.articleCreated || 0, item.articlePublished || 0)))

  ctx.strokeStyle = '#e5e7eb'
  ctx.lineWidth = 1
  for (let i = 0; i <= 4; i += 1) {
    const y = padT + (chartH / 4) * i
    ctx.beginPath()
    ctx.moveTo(padL, y)
    ctx.lineTo(width - padR, y)
    ctx.stroke()
    ctx.fillStyle = '#6b7280'
    ctx.font = '11px "Microsoft YaHei", sans-serif'
    ctx.textAlign = 'right'
    ctx.fillText(String(Math.round(maxVal - (maxVal / 4) * i)), padL - 8, y + 4)
  }

  if (!items.length) {
    ctx.fillStyle = '#9ca3af'
    ctx.font = '14px "Microsoft YaHei", sans-serif'
    ctx.textAlign = 'center'
    ctx.fillText('暂无趋势数据', width / 2, height / 2)
    return
  }

  const groupWidth = chartW / items.length
  const barWidth = Math.max(6, Math.min(18, groupWidth * 0.22))
  items.forEach((item: ProjectDashboardTrendItem, index: number) => {
    const centerX = padL + groupWidth * index + groupWidth / 2
    drawBar(ctx, centerX - barWidth * 1.5, padT + chartH, barWidth, (item.articleCreated || 0) / maxVal * chartH, '#2563eb')
    drawBar(ctx, centerX - barWidth / 2, padT + chartH, barWidth, (item.articlePublished || 0) / maxVal * chartH, '#10b981')
    drawBar(ctx, centerX + barWidth / 2, padT + chartH, barWidth, (item.hitCount || 0) / maxVal * chartH, '#f59e0b')

    if (index % Math.max(1, Math.ceil(items.length / 8)) === 0 || items.length <= 8) {
      ctx.fillStyle = '#6b7280'
      ctx.font = '11px "Microsoft YaHei", sans-serif'
      ctx.textAlign = 'center'
      ctx.fillText(formatDateLabel(item.date), centerX, height - 12)
    }
  })
}

function drawBar(ctx: CanvasRenderingContext2D, x: number, baseY: number, width: number, height: number, color: string) {
  if (height <= 0) return
  ctx.fillStyle = color
  ctx.fillRect(x, baseY - height, width, height)
}

function formatDateLabel(value: string) {
  return value?.length >= 10 ? value.slice(5, 10) : value || ''
}

function handleResize() {
  drawTrendChart()
}

watch(
  () => trend.items,
  async () => {
    await nextTick()
    drawTrendChart()
  },
  { deep: true },
)

onMounted(async () => {
  loading.value = true
  loadError.value = false
  try {
    await Promise.all([loadSummary(), loadTrend(), loadDetails()])
    await nextTick()
    drawTrendChart()
    window.addEventListener('resize', handleResize)
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
:global(body) {
  margin: 0;
  background: #f6f7fb;
}

.dashboard-page {
  min-height: 100vh;
  background: #f6f7fb;
  color: #111827;
  font-family: "Microsoft YaHei", "PingFang SC", "Segoe UI", sans-serif;
}

.center-state {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.state-card {
  width: min(360px, 100%);
  padding: 28px;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.08);
  text-align: center;
}

.state-title {
  margin-top: 12px;
  font-size: 20px;
  font-weight: 700;
}

.state-subtitle {
  margin-top: 8px;
  color: #6b7280;
  font-size: 14px;
}

.container {
  width: min(1180px, calc(100% - 40px));
  margin: 0 auto;
}

.page-header {
  padding: 34px 0 42px;
  background: #111827;
  color: #f9fafb;
}

.header-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(360px, 520px);
  gap: 28px;
  align-items: start;
}

.eyebrow {
  margin-bottom: 10px;
  color: #93c5fd;
  font-size: 13px;
  font-weight: 600;
}

h1 {
  margin: 0 0 8px;
  font-size: 32px;
  line-height: 1.2;
  font-weight: 700;
}

.page-header p {
  margin: 0;
  color: #cbd5e1;
}

.header-meta {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.header-meta div {
  padding: 12px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 8px;
}

.header-meta span,
.panel-header span,
.metric-card small {
  display: block;
  color: #6b7280;
  font-size: 12px;
}

.header-meta span {
  color: #94a3b8;
}

.header-meta strong {
  display: block;
  margin-top: 4px;
  color: #f9fafb;
  font-size: 13px;
  font-weight: 600;
}

.main-content {
  padding: 24px 0 40px;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.period-tabs {
  display: inline-flex;
  gap: 8px;
  padding: 4px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.period-button,
.filter-toggle,
.filter-panel button {
  border: 0;
  border-radius: 6px;
  background: #fff;
  color: #374151;
  cursor: pointer;
  font-size: 13px;
}

.period-button {
  padding: 8px 14px;
}

.period-button.active,
.filter-panel button {
  background: #2563eb;
  color: #fff;
}

.window-note {
  color: #6b7280;
  font-size: 13px;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 16px;
}

.metric-card,
.panel,
.method-note {
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  background: #fff;
}

.metric-card {
  padding: 18px;
}

.metric-card span {
  color: #6b7280;
  font-size: 13px;
}

.metric-card strong {
  display: block;
  margin: 8px 0 4px;
  font-size: 30px;
  line-height: 1.1;
}

.split-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
}

.panel {
  padding: 20px;
  margin-bottom: 16px;
}

.panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.panel-header h2 {
  margin: 0 0 4px;
  font-size: 17px;
}

.panel-badge {
  flex: 0 0 auto;
  padding: 4px 9px;
  border-radius: 999px;
  background: #f3f4f6;
  color: #4b5563;
  font-size: 12px;
}

.progress-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 10px;
}

.progress-card {
  min-height: 118px;
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f9fafb;
}

.progress-card span {
  display: block;
  color: #4b5563;
  font-size: 13px;
}

.progress-card strong {
  display: block;
  margin: 8px 0 6px;
  color: #111827;
  font-size: 26px;
  line-height: 1.1;
}

.progress-card small {
  display: block;
  color: #6b7280;
  font-size: 12px;
  line-height: 1.45;
}

.progress-pending {
  background: #fffbeb;
}

.progress-generation_failed,
.progress-distribution_failed {
  background: #fef2f2;
}

.advice-panel {
  background: #ffffff;
}

.advice-summary {
  margin: 0 0 16px;
  color: #374151;
  font-size: 15px;
  line-height: 1.75;
}

.advice-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.advice-block {
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f9fafb;
}

.advice-block h3 {
  margin: 0 0 10px;
  color: #111827;
  font-size: 14px;
}

.advice-block ul {
  display: grid;
  gap: 8px;
  margin: 0;
  padding-left: 18px;
  color: #4b5563;
  font-size: 13px;
  line-height: 1.6;
}

.platform-list {
  display: grid;
  gap: 8px;
}

.platform-item {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #f9fafb;
}

.platform-item strong,
.detail-card strong {
  display: block;
  color: #111827;
  font-size: 14px;
}

.platform-item span,
.platform-stats span,
.detail-card div {
  color: #6b7280;
  font-size: 12px;
}

.platform-stats {
  text-align: right;
  white-space: nowrap;
}

.platform-stats b {
  display: block;
  font-size: 16px;
}

.question-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  min-height: 180px;
}

.question-chip {
  display: inline-flex;
  align-items: center;
  max-width: 100%;
  border-radius: 999px;
  background: #eff6ff;
  color: #1d4ed8;
  line-height: 1.4;
  word-break: break-word;
}

.question-chip.xl { padding: 10px 18px; font-size: 17px; font-weight: 700; }
.question-chip.lg { padding: 8px 15px; font-size: 15px; font-weight: 600; }
.question-chip.md { padding: 7px 13px; font-size: 13px; }
.question-chip.sm { padding: 6px 11px; font-size: 12px; }

.chart-area {
  width: 100%;
  height: 280px;
}

.chart-area canvas {
  width: 100%;
  height: 100%;
}

.detail-header {
  align-items: center;
}

.filter-toggle {
  padding: 8px 12px;
  border: 1px solid #d1d5db;
}

.filter-panel {
  display: grid;
  grid-template-columns: 160px 150px 150px minmax(180px, 1fr) 80px;
  gap: 10px;
  margin-bottom: 16px;
}

.filter-panel select,
.filter-panel input {
  min-width: 0;
  padding: 8px 10px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  background: #fff;
  color: #111827;
  font-size: 13px;
}

table {
  width: 100%;
  border-collapse: collapse;
}

th,
td {
  padding: 12px 10px;
  border-bottom: 1px solid #e5e7eb;
  text-align: left;
  font-size: 13px;
  vertical-align: top;
}

th {
  color: #6b7280;
  background: #f9fafb;
  font-weight: 600;
}

a {
  color: #2563eb;
  text-decoration: none;
  font-weight: 600;
}

.mobile-cards {
  display: none;
}

.detail-card {
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.detail-card div {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  margin: 8px 0;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.empty-state {
  padding: 20px;
  color: #9ca3af;
  text-align: center;
  font-size: 13px;
}

.method-note {
  padding: 16px 18px;
  color: #4b5563;
  font-size: 13px;
  line-height: 1.7;
}

.method-note strong {
  display: block;
  margin-bottom: 6px;
  color: #111827;
}

.method-note p {
  margin: 4px 0;
}

@media (max-width: 960px) {
  .header-grid,
  .split-grid,
  .advice-grid {
    grid-template-columns: 1fr;
  }

  .metrics-grid,
  .progress-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .filter-panel {
    grid-template-columns: 1fr 1fr;
  }

  .filter-panel input[type="text"],
  .filter-panel button {
    grid-column: span 2;
  }
}

@media (max-width: 640px) {
  .container {
    width: min(100% - 28px, 1180px);
  }

  .page-header {
    padding: 26px 0 32px;
  }

  h1 {
    font-size: 25px;
  }

  .header-meta,
  .metrics-grid,
  .progress-grid,
  .advice-grid,
  .filter-panel {
    grid-template-columns: 1fr;
  }

  .toolbar,
  .panel-header,
  .detail-header {
    align-items: stretch;
    flex-direction: column;
  }

  .period-tabs {
    width: 100%;
  }

  .period-button {
    flex: 1;
  }

  .desktop-table {
    display: none;
  }

  .mobile-cards {
    display: grid;
    gap: 10px;
  }

  .filter-panel input[type="text"],
  .filter-panel button {
    grid-column: auto;
  }

  .pagination-wrap {
    justify-content: center;
    overflow-x: auto;
  }
}
</style>
