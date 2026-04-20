<template>
  <div class="dashboard-page">
    <div v-if="loading" class="center-state">
      <div class="state-card">
        <el-icon class="is-loading" :size="32"><Loading /></el-icon>
        <div class="state-title">正在加载统计看板</div>
        <div class="state-subtitle">请稍候，系统正在获取最新数据。</div>
      </div>
    </div>

    <div v-else-if="loadError" class="center-state">
      <div class="state-card">
        <div class="state-title">链接无效或已停用</div>
        <div class="state-subtitle">请联系服务团队获取新的统计看板分享链接。</div>
      </div>
    </div>

    <template v-else>
      <section class="hero">
        <div class="hero-inner">
          <div class="hero-badge">实时统计看板</div>
          <h1 class="hero-title">{{ summary.projectName || '项目统计看板' }}</h1>
          <p class="hero-subtitle">{{ summary.brandName || '品牌信息未提供' }}</p>
          <div class="hero-meta">
            <div class="hero-meta-item">
              <span class="label">项目</span>
              <span>{{ summary.projectName || '-' }}</span>
            </div>
            <div class="hero-meta-item">
              <span class="label">品牌</span>
              <span>{{ summary.brandName || '-' }}</span>
            </div>
            <div class="hero-meta-item">
              <span class="label">更新时间</span>
              <span>{{ currentRequestTime }}</span>
            </div>
          </div>
        </div>
      </section>

      <div class="container">
        <section class="metrics-row">
          <div class="metric-card">
            <div class="metric-icon blue">收</div>
            <div class="metric-info">
              <div class="metric-label">收录总量</div>
              <div class="metric-value">{{ formatNum(summary.summary?.hitTotal) }}</div>
              <div class="metric-change">今日 +{{ formatNum(summary.summary?.hitToday) }}</div>
            </div>
          </div>
          <div class="metric-card">
            <div class="metric-icon purple">平</div>
            <div class="metric-info">
              <div class="metric-label">收录平台</div>
              <div class="metric-value">{{ formatNum(summary.summary?.platformCount) }}</div>
              <div class="metric-change neutral">命中平台去重数</div>
            </div>
          </div>
          <div class="metric-card">
            <div class="metric-icon amber">联</div>
            <div class="metric-info">
              <div class="metric-label">联系方式曝光</div>
              <div class="metric-value">{{ formatNum(summary.summary?.contactTotal) }}</div>
              <div class="metric-change">今日 +{{ formatNum(summary.summary?.contactToday) }}</div>
            </div>
          </div>
          <div class="metric-card">
            <div class="metric-icon green">链</div>
            <div class="metric-info">
              <div class="metric-label">官网链接曝光</div>
              <div class="metric-value">{{ formatNum(summary.summary?.siteTotal) }}</div>
              <div class="metric-change">今日 +{{ formatNum(summary.summary?.siteToday) }}</div>
            </div>
          </div>
        </section>

        <section class="top-section">
          <div class="card">
            <div class="card-header">
              <div class="card-title">
                <span class="card-title-icon">平</span>
                平台收录分布
              </div>
            </div>

            <div class="platform-groups">
              <div>
                <div class="platform-group-title">
                  <span class="dot"></span>
                  平台概览
                </div>
                <div class="platform-list">
                  <div
                    v-for="item in summary.platforms || []"
                    :key="item.platformCode"
                    class="platform-item"
                  >
                    <span class="platform-dot" :style="{ background: platformColor(item.platformCode) }"></span>
                    <span class="platform-name">{{ item.platformName || item.platformCode }}</span>
                    <span class="platform-count">{{ formatNum(item.hitCount) }}</span>
                  </div>
                  <div v-if="!(summary.platforms || []).length" class="platform-empty">
                    暂无平台数据
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="card">
            <div class="card-header">
              <div class="card-title">
                <span class="card-title-icon">词</span>
                蒸馏词云
              </div>
            </div>
            <div class="word-cloud">
              <span
                v-for="item in visibleWordCloud"
                :key="item.word"
                class="word-tag"
                :class="wordSizeClass(item.frequency)"
              >
                {{ item.word }}
              </span>
              <div v-if="!visibleWordCloud.length" class="platform-empty">暂无词云数据</div>
            </div>
          </div>
        </section>

        <section class="card chart-section">
          <div class="card-header">
            <div class="card-title">
              <span class="card-title-icon">趋</span>
              文章数据与收录趋势
            </div>
            <div class="chart-legend">
              <div class="legend-item">
                <span class="legend-dot blue"></span>
                文章创建
              </div>
              <div class="legend-item">
                <span class="legend-dot green"></span>
                文章发布
              </div>
            </div>
            <div class="chart-period-group">
              <button
                v-for="day in [30, 60, 90]"
                :key="day"
                class="chart-period"
                :class="{ active: trendDays === day }"
                @click="changeTrendDays(day)"
              >
                最近 {{ day }} 天
              </button>
            </div>
          </div>
          <div class="chart-area">
            <canvas ref="trendCanvasRef" class="chart-canvas"></canvas>
          </div>
        </section>

        <section class="card detail-card">
          <div class="card-header">
            <div class="card-title">
              <span class="card-title-icon">明</span>
              收录明细
            </div>
          </div>

          <div class="notice-bar">
            <span class="notice-icon">!</span>
            由于 AI 平台结果存在动态变化，当前页面仅展示最近 {{ details.maxViewable || 5000 }} 条可在线查看的命中记录。
          </div>

          <div class="platform-filters">
            <button
              class="pf-btn"
              :class="{ active: !detailQuery.platformCode }"
              @click="applyPlatformFilter('')"
            >
              <span class="pf-icon" style="background: var(--brand)">全</span>
              全部
              <span class="pf-count">({{ formatNum(summary.summary?.hitTotal) }})</span>
            </button>
            <button
              v-for="item in summary.platforms || []"
              :key="item.platformCode"
              class="pf-btn"
              :class="{ active: detailQuery.platformCode === item.platformCode }"
              @click="applyPlatformFilter(item.platformCode)"
            >
              <span class="pf-icon" :style="{ background: platformColor(item.platformCode) }">
                {{ shortPlatformName(item.platformName || item.platformCode) }}
              </span>
              {{ item.platformName || item.platformCode }}
              <span class="pf-count">({{ formatNum(item.hitCount) }})</span>
            </button>
          </div>

          <div class="table-toolbar">
            <div class="toolbar-group">
              <input v-model="detailQuery.startDate" class="input-field" type="date" />
              <span class="separator">-</span>
              <input v-model="detailQuery.endDate" class="input-field" type="date" />
            </div>
            <div class="toolbar-group">
              <input
                v-model="detailQuery.keyword"
                class="input-field search"
                type="text"
                placeholder="搜索问题关键词..."
                @keyup.enter="searchDetails"
              />
              <button class="search-btn" @click="searchDetails">查询</button>
            </div>
          </div>

          <div class="table-wrap">
            <table class="data-table">
              <thead>
                <tr>
                  <th style="width: 70px">序号</th>
                  <th>问题</th>
                  <th style="width: 180px">平台</th>
                  <th style="width: 140px">查询日期</th>
                  <th style="width: 120px">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(item, index) in details.items" :key="item.id">
                  <td>{{ (detailPage.page - 1) * detailPage.size + index + 1 }}</td>
                  <td>
                    <div class="question-cell">
                      <span class="hit-indicator"></span>
                      <span class="question-text">{{ item.questionText || '-' }}</span>
                    </div>
                  </td>
                  <td>
                    <span class="platform-badge">
                      <span class="badge-dot" :style="{ background: platformColor(item.platformCode) }"></span>
                      {{ item.platformName || item.platformCode }}
                    </span>
                  </td>
                  <td>{{ item.batchDate || '-' }}</td>
                  <td>
                    <a
                      v-if="item.platformUrl"
                      :href="item.platformUrl"
                      target="_blank"
                      rel="noopener noreferrer"
                      class="action-link"
                    >
                      转到平台
                    </a>
                    <span v-else>-</span>
                  </td>
                </tr>
                <tr v-if="!details.items.length">
                  <td colspan="5">
                    <div class="table-empty">暂无命中明细</div>
                  </td>
                </tr>
              </tbody>
            </table>
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

        <footer class="footer">
          数据由系统自动采集，每小时刷新一次，仅供项目进展查看使用。
        </footer>
      </div>
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
import type {
  ProjectDashboardDetailResponse,
  ProjectDashboardSummaryResponse,
  ProjectDashboardTrendItem,
  ProjectDashboardTrendResponse,
  ProjectDashboardWordItem,
} from '@/types'

const route = useRoute()
const shareCode = String(route.params.shareCode || '')
const trendCanvasRef = ref<HTMLCanvasElement>()

const loading = ref(true)
const loadError = ref(false)
const trendDays = ref(30)
const currentRequestTime = ref('-')

const summary = reactive<ProjectDashboardSummaryResponse>({
  projectName: '',
  brandName: '',
  summary: {
    hitTotal: 0,
    hitToday: 0,
    platformCount: 0,
    contactTotal: 0,
    contactToday: 0,
    siteTotal: 0,
    siteToday: 0,
  },
  platforms: [],
  wordCloud: [],
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

const platformPalette = [
  '#6C5CE7',
  '#2563EB',
  '#10B981',
  '#06B6D4',
  '#F59E0B',
  '#EF4444',
  '#7C3AED',
  '#1E293B',
]

const visibleWordCloud = computed<ProjectDashboardWordItem[]>(() => {
  return (summary.wordCloud || []).slice(0, 20)
})

async function loadSummary() {
  const { data } = await getPublicProjectDashboardSummary(shareCode)
  Object.assign(summary, data.data || {})
}

async function loadTrend() {
  const { data } = await getPublicProjectDashboardTrend(shareCode, { days: trendDays.value })
  trend.items = data.data?.items || []
}

async function loadDetails() {
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

function changeTrendDays(days: number) {
  trendDays.value = days
  void loadTrend()
}

function searchDetails() {
  detailPage.page = 1
  void loadDetails()
}

function applyPlatformFilter(platformCode: string) {
  detailQuery.platformCode = platformCode
  searchDetails()
}

function onDetailPageChange(page: number) {
  detailPage.page = page
  void loadDetails()
}

function formatNum(value?: number | null) {
  return Number(value || 0).toLocaleString()
}

function formatDateTime(value?: string | null) {
  if (!value) return '-'
  const normalized = value.replace('T', ' ')
  if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(normalized)) {
    return normalized
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

function shortPlatformName(name: string) {
  return (name || '').slice(0, 1) || '平'
}

function platformColor(platformCode?: string | null) {
  const text = platformCode || ''
  let hash = 0
  for (let i = 0; i < text.length; i += 1) {
    hash = (hash + text.charCodeAt(i)) % platformPalette.length
  }
  return platformPalette[hash]
}

function wordSizeClass(frequency: number) {
  const items = visibleWordCloud.value
  if (!items.length) return 'sm'
  const values = items.map((item) => item.frequency).sort((a, b) => b - a)
  const max = values[0] || 0
  const second = values[1] || max
  const fifth = values[4] || second
  if (frequency >= max) return 'xl'
  if (frequency >= second) return 'lg'
  if (frequency >= fifth) return 'md'
  return 'sm'
}

function drawTrendChart() {
  const canvas = trendCanvasRef.value
  if (!canvas) return

  const parent = canvas.parentElement
  if (!parent) return

  const ctx = canvas.getContext('2d')
  if (!ctx) return

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
  const padL = 48
  const padR = 24
  const padT = 18
  const padB = 42
  const chartW = width - padL - padR
  const chartH = height - padT - padB
  const maxVal = Math.max(
    10,
    ...items.map((item) => Math.max(item.articleCreated || 0, item.articlePublished || 0)),
  )

  ctx.strokeStyle = '#F1F5F9'
  ctx.lineWidth = 1
  for (let i = 0; i <= 5; i += 1) {
    const y = padT + (chartH / 5) * i
    ctx.beginPath()
    ctx.moveTo(padL, y)
    ctx.lineTo(width - padR, y)
    ctx.stroke()

    ctx.fillStyle = '#94A3B8'
    ctx.font = '11px "Microsoft YaHei", sans-serif'
    ctx.textAlign = 'right'
    ctx.fillText(String(Math.round(maxVal - (maxVal / 5) * i)), padL - 8, y + 4)
  }

  if (!items.length) {
    ctx.fillStyle = '#94A3B8'
    ctx.font = '14px "Microsoft YaHei", sans-serif'
    ctx.textAlign = 'center'
    ctx.fillText('暂无趋势数据', width / 2, height / 2)
    return
  }

  const groupWidth = chartW / items.length
  const barWidth = Math.max(8, groupWidth * 0.28)
  const gap = 4

  items.forEach((item: ProjectDashboardTrendItem, index: number) => {
    const centerX = padL + groupWidth * index + groupWidth / 2
    const createdHeight = ((item.articleCreated || 0) / maxVal) * chartH
    const publishedHeight = ((item.articlePublished || 0) / maxVal) * chartH

    drawRoundedBar(
      ctx,
      centerX - barWidth - gap / 2,
      padT + chartH - createdHeight,
      barWidth,
      createdHeight,
      4,
      '#2563EB',
    )
    drawRoundedBar(
      ctx,
      centerX + gap / 2,
      padT + chartH - publishedHeight,
      barWidth,
      publishedHeight,
      4,
      '#10B981',
    )

    if (index % Math.max(1, Math.ceil(items.length / 10)) === 0 || items.length <= 10) {
      ctx.fillStyle = '#94A3B8'
      ctx.font = '11px "Microsoft YaHei", sans-serif'
      ctx.textAlign = 'center'
      ctx.fillText(formatDateLabel(item.date), centerX, height - padB + 20)
    }
  })
}

function drawRoundedBar(
  ctx: CanvasRenderingContext2D,
  x: number,
  y: number,
  width: number,
  height: number,
  radius: number,
  color: string,
) {
  if (height <= 0) return
  const r = Math.min(radius, height / 2, width / 2)
  ctx.beginPath()
  ctx.moveTo(x + r, y)
  ctx.lineTo(x + width - r, y)
  ctx.quadraticCurveTo(x + width, y, x + width, y + r)
  ctx.lineTo(x + width, y + height)
  ctx.lineTo(x, y + height)
  ctx.lineTo(x, y + r)
  ctx.quadraticCurveTo(x, y, x + r, y)
  ctx.closePath()
  ctx.fillStyle = color
  ctx.fill()
}

function formatDateLabel(value: string) {
  if (!value) return ''
  return value.length >= 10 ? value.slice(5) : value
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
    currentRequestTime.value = formatDateTime(new Date().toISOString())
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
  background: #f8fafc;
}

.dashboard-page {
  min-height: 100vh;
  background: #f8fafc;
  color: #0f172a;
  font-family: "Microsoft YaHei", "PingFang SC", "Segoe UI", sans-serif;
}

.center-state {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
}

.state-card {
  min-width: 320px;
  padding: 32px;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 8px 30px rgba(15, 23, 42, 0.08);
  text-align: center;
}

.state-title {
  margin-top: 14px;
  font-size: 22px;
  font-weight: 700;
}

.state-subtitle {
  margin-top: 8px;
  color: #64748b;
  font-size: 14px;
}

.hero {
  position: relative;
  overflow: hidden;
  padding: 48px 0 56px;
  background: linear-gradient(135deg, #0f172a 0%, #1e293b 50%, #0f172a 100%);
}

.hero::before,
.hero::after {
  content: '';
  position: absolute;
  border-radius: 999px;
  pointer-events: none;
}

.hero::before {
  top: -40%;
  right: -10%;
  width: 500px;
  height: 500px;
  background: radial-gradient(circle, rgba(37, 99, 235, 0.15) 0%, transparent 70%);
}

.hero::after {
  left: -5%;
  bottom: -30%;
  width: 400px;
  height: 400px;
  background: radial-gradient(circle, rgba(16, 185, 129, 0.1) 0%, transparent 70%);
}

.hero-inner,
.container {
  position: relative;
  z-index: 1;
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 32px;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 14px;
  margin-bottom: 16px;
  border: 1px solid rgba(37, 99, 235, 0.3);
  border-radius: 20px;
  background: rgba(37, 99, 235, 0.2);
  color: #93c5fd;
  font-size: 12px;
  font-weight: 500;
}

.hero-title {
  margin: 0 0 6px;
  color: #f8fafc;
  font-size: 32px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.hero-subtitle {
  margin: 0;
  color: #94a3b8;
  font-size: 14px;
}

.hero-meta {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
  margin-top: 20px;
}

.hero-meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #cbd5e1;
  font-size: 13px;
}

.hero-meta-item .label {
  color: #64748b;
}

.metrics-row {
  position: relative;
  z-index: 2;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-top: -32px;
  margin-bottom: 24px;
}

.metric-card,
.card {
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.06), 0 1px 4px rgba(15, 23, 42, 0.04);
}

.metric-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 22px 24px;
}

.metric-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 48px;
  height: 48px;
  border-radius: 12px;
  font-size: 18px;
  font-weight: 700;
}

.metric-icon.blue {
  background: #dbeafe;
  color: #2563eb;
}

.metric-icon.green {
  background: #d1fae5;
  color: #10b981;
}

.metric-icon.amber {
  background: #fef3c7;
  color: #f59e0b;
}

.metric-icon.purple {
  background: #ede9fe;
  color: #7c3aed;
}

.metric-label {
  margin-bottom: 2px;
  color: #64748b;
  font-size: 13px;
  font-weight: 500;
}

.metric-value {
  line-height: 1.2;
  color: #0f172a;
  font-size: 28px;
  font-weight: 700;
}

.metric-change {
  margin-top: 4px;
  color: #10b981;
  font-size: 12px;
  font-weight: 500;
}

.metric-change.neutral {
  color: #94a3b8;
}

.card {
  padding: 28px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 16px;
  justify-content: space-between;
  flex-wrap: wrap;
  margin-bottom: 20px;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #0f172a;
  font-size: 15px;
  font-weight: 600;
}

.card-title-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: #dbeafe;
  color: #2563eb;
  font-size: 13px;
  font-weight: 700;
}

.top-section {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  margin-bottom: 20px;
}

.platform-group-title {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 12px;
  color: #64748b;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 2px;
  background: #2563eb;
}

.platform-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.platform-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: 8px;
  background: #f8fafc;
}

.platform-dot,
.badge-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.platform-name {
  flex: 1;
  color: #0f172a;
  font-size: 13px;
  font-weight: 500;
}

.platform-count {
  color: #0f172a;
  font-size: 13px;
  font-weight: 600;
}

.platform-empty,
.table-empty {
  padding: 24px 12px;
  color: #94a3b8;
  text-align: center;
  font-size: 13px;
}

.word-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  justify-content: center;
  min-height: 220px;
  padding: 20px 8px;
}

.word-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 24px;
  color: #fff;
  white-space: nowrap;
  background: linear-gradient(135deg, #64748b 0%, #475569 100%);
}

.word-tag.xl {
  padding: 12px 28px;
  background: linear-gradient(135deg, #2563eb 0%, #6366f1 100%);
  font-size: 20px;
  font-weight: 600;
}

.word-tag.lg {
  padding: 10px 22px;
  background: linear-gradient(135deg, #3b82f6 0%, #8b5cf6 100%);
  font-size: 16px;
  font-weight: 600;
}

.word-tag.md {
  padding: 8px 18px;
  background: linear-gradient(135deg, #06b6d4 0%, #3b82f6 100%);
  font-size: 13px;
  font-weight: 500;
}

.word-tag.sm {
  padding: 6px 14px;
  font-size: 12px;
  font-weight: 500;
}

.chart-section {
  margin-bottom: 20px;
}

.chart-legend {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-left: auto;
  color: #64748b;
  font-size: 13px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 3px;
}

.legend-dot.blue {
  background: #2563eb;
}

.legend-dot.green {
  background: #10b981;
}

.chart-period-group {
  display: flex;
  gap: 8px;
}

.chart-period {
  padding: 6px 14px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  background: #fff;
  color: #64748b;
  font-size: 13px;
  cursor: pointer;
}

.chart-period.active {
  border-color: #2563eb;
  background: #2563eb;
  color: #fff;
}

.chart-area {
  width: 100%;
  height: 260px;
}

.chart-canvas {
  width: 100%;
  height: 100%;
}

.notice-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  margin-bottom: 16px;
  border: 1px solid #fde68a;
  border-radius: 8px;
  background: #fef3c7;
  color: #92400e;
  font-size: 12px;
  line-height: 1.5;
}

.notice-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: rgba(146, 64, 14, 0.12);
  font-size: 12px;
  font-weight: 700;
}

.platform-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 20px;
}

.pf-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  background: #fff;
  color: #0f172a;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}

.pf-btn.active {
  border-color: #2563eb;
  background: #2563eb;
  color: #fff;
  box-shadow: 0 2px 8px rgba(37, 99, 235, 0.25);
}

.pf-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 4px;
  color: #fff;
  font-size: 10px;
  font-weight: 700;
}

.pf-count {
  color: #94a3b8;
  font-size: 12px;
}

.pf-btn.active .pf-count {
  color: rgba(255, 255, 255, 0.75);
}

.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.toolbar-group {
  display: flex;
  align-items: center;
  gap: 10px;
}

.input-field {
  padding: 8px 14px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  background: #fff;
  color: #0f172a;
  font-size: 13px;
  outline: none;
}

.input-field.search {
  width: 240px;
}

.separator {
  color: #94a3b8;
  font-size: 13px;
}

.search-btn {
  padding: 8px 14px;
  border: 1px solid #2563eb;
  border-radius: 6px;
  background: #2563eb;
  color: #fff;
  font-size: 13px;
  cursor: pointer;
}

.table-wrap {
  overflow-x: auto;
}

.data-table {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  overflow: hidden;
}

.data-table thead th {
  padding: 14px 20px;
  border-bottom: 1px solid #e2e8f0;
  background: #f8fafc;
  color: #64748b;
  text-align: left;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  white-space: nowrap;
}

.data-table tbody td {
  padding: 14px 20px;
  border-bottom: 1px solid #f1f5f9;
  color: #0f172a;
  font-size: 13px;
  vertical-align: middle;
}

.data-table tbody tr:last-child td {
  border-bottom: none;
}

.question-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.question-text {
  line-height: 1.5;
}

.hit-indicator {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #10b981;
  flex-shrink: 0;
}

.platform-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 6px;
  background: #f8fafc;
  font-size: 13px;
  font-weight: 500;
}

.action-link {
  color: #2563eb;
  text-decoration: none;
  font-size: 13px;
  font-weight: 500;
}

.action-link:hover {
  text-decoration: underline;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.footer {
  padding: 32px 0 40px;
  color: #94a3b8;
  text-align: center;
  font-size: 12px;
}

@media (max-width: 1024px) {
  .metrics-row {
    grid-template-columns: repeat(2, 1fr);
  }

  .top-section {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .hero-inner,
  .container {
    padding: 0 20px;
  }

  .metrics-row {
    grid-template-columns: 1fr;
  }

  .table-toolbar,
  .toolbar-group,
  .chart-period-group,
  .chart-legend {
    width: 100%;
  }

  .toolbar-group {
    flex-wrap: wrap;
  }

  .input-field.search {
    width: 100%;
  }
}
</style>
