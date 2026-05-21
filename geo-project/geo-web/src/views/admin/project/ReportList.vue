<template>
  <main class="postsale-report-page">
    <section class="report-hero">
      <div>
        <button class="back-button" type="button" @click="router.back()">
          <span class="back-button-icon" aria-hidden="true">‹</span>
          <span>返回项目</span>
        </button>
        <div class="hero-eyebrow">项目实时数据看板</div>
        <h1>项目实时数据看板</h1>
        <p>让品牌在每一次 AI 对话中被看见——多模型协同感知，全链路曝光可量化。</p>
      </div>
      <div class="hero-meta">
        <article>
          <span>项目名称</span>
          <strong>{{ projectName }}</strong>
        </article>
        <article>
          <span>服务周期</span>
          <strong>{{ servicePeriod }}</strong>
        </article>
        <article>
          <span>数据更新时间</span>
          <strong>{{ refreshDateTime }}</strong>
        </article>
      </div>
    </section>

    <section class="data-cockpit panel">
      <div class="cockpit-header">
        <div class="cockpit-title">
          <i></i>
          <span>数据驾驶舱</span>
          <em>·</em>
          <strong>跨平台收录态势 · 智能搜索曝光全景</strong>
        </div>
        <div class="cockpit-sync">
          <span></span>
          实时同步 · {{ syncTime }} 更新
        </div>
      </div>

      <div class="cockpit-grid">
        <aside class="signal-tower">
          <div class="cockpit-section-head">
            <div>
              <h2>大模型收录</h2>
              <span>收录来源分布</span>
            </div>
            <strong>10 个</strong>
          </div>

          <div class="signal-list">
            <article v-for="source in indexingSources" :key="source.name" class="signal-row">
              <i>
                <img :src="source.logo" :alt="source.name" />
              </i>
              <span>{{ source.name }}</span>
              <div class="signal-bar">
                <em :style="{ width: `${source.percent}%`, background: source.gradient }"></em>
              </div>
              <strong>{{ source.value }}</strong>
            </article>
          </div>

          <footer>
            <span>收录小计</span>
            <strong>60.99万</strong>
          </footer>
        </aside>

        <div class="visibility-engine">
          <div class="engine-grid-bg"></div>
          <div class="engine-title">
            <strong>AI可见度引擎</strong>
            <span>{{ indexingSources.length }} 个平台 · 实时同步</span>
          </div>
          <div class="engine-live">
            <i></i>
            Live
          </div>

          <div class="lissa-stage" ref="lissaStageRef">
            <canvas class="lissa-trail-canvas" ref="lissaCanvasRef" aria-hidden="true"></canvas>

            <div class="lissa-logos" ref="lissaLogosRef">
              <div
                v-for="item in lissajousPlatforms"
                :key="item.name"
                class="lissa-chip"
                :class="{ 'lissa-chip--small': !item.big }"
              >
                <span :data-tip="`${item.name} · ${item.value}`">
                  <img :src="item.logo" :alt="item.name" />
                </span>
              </div>
            </div>

            <div class="engine-core">
              <svg class="engine-core-ring engine-core-ring--outer" viewBox="0 0 140 140" aria-hidden="true">
                <defs>
                  <linearGradient id="dashboardCoreArcA" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="140" y2="140">
                    <stop offset="0%" stop-color="#2f6bff" stop-opacity="0" />
                    <stop offset="100%" stop-color="#2f6bff" stop-opacity="0.55" />
                  </linearGradient>
                </defs>
                <circle cx="70" cy="70" r="50" fill="none" stroke="url(#dashboardCoreArcA)" stroke-width="1.4" stroke-dasharray="80 234" stroke-linecap="round" />
              </svg>
              <svg class="engine-core-ring engine-core-ring--inner" viewBox="0 0 140 140" aria-hidden="true">
                <defs>
                  <linearGradient id="dashboardCoreArcB" gradientUnits="userSpaceOnUse" x1="140" y1="0" x2="0" y2="140">
                    <stop offset="0%" stop-color="#7b61ff" stop-opacity="0" />
                    <stop offset="100%" stop-color="#7b61ff" stop-opacity="0.55" />
                  </linearGradient>
                </defs>
                <circle cx="70" cy="70" r="42" fill="none" stroke="url(#dashboardCoreArcB)" stroke-width="1.4" stroke-dasharray="58 206" stroke-linecap="round" />
              </svg>
              <span>TOTAL</span>
              <strong>60.99万</strong>
              <em>已收录信号</em>
            </div>
          </div>

          <div class="engine-flow" ref="engineFlowRef">
            <div v-for="item in engineFlowItems" :key="item.label" class="engine-flow-item" :class="`is-${item.tone}`">
              <div>
                <span></span>
                <em>{{ item.label }}</em>
              </div>
              <strong>{{ item.value }}</strong>
            </div>
          </div>
        </div>

        <aside class="ai-exposure-matrix">
          <div class="cockpit-section-head">
            <div>
              <h2>AI 搜索曝光</h2>
              <span>AI SEARCH VISIBILITY</span>
            </div>
            <strong>{{ aiExposurePlatforms.length }} 平台</strong>
          </div>

          <div class="ai-exposure-list">
            <article
              v-for="item in aiExposurePlatforms"
              :key="item.name"
              class="ai-exposure-card"
              :class="{ highlighted: item.highlighted }"
            >
              <div class="ai-exposure-top">
                <div class="ai-exposure-identity">
                  <i :style="{ background: item.iconBg, color: item.iconColor }">{{ item.short }}</i>
                  <div>
                    <strong>{{ item.name }}</strong>
                    <span>{{ item.enName }}</span>
                  </div>
                </div>
                <em :class="item.trendType === 'down' ? 'trend-down' : 'trend-up'">
                  <span aria-hidden="true">{{ item.trendType === 'down' ? '↓' : '↑' }}</span>
                  {{ item.trend }}
                </em>
              </div>
              <div class="ai-exposure-metric">
                <strong>{{ formatInt(item.value) }}</strong>
                <span>{{ item.percent }}%</span>
              </div>
              <div class="ai-exposure-track">
                <span :style="{ width: `${item.percent}%` }"></span>
              </div>
            </article>
          </div>

          <footer class="ai-exposure-total">
            <span>曝光总量</span>
            <strong>{{ formatInt(aiExposureTotal) }}</strong>
          </footer>
        </aside>
      </div>
    </section>

    <section class="overview-grid">
      <article v-for="item in metricCards" :key="item.label" class="metric-card" :class="`metric-card--${item.tone}`">
        <div class="metric-topline">
          <span class="metric-icon" v-html="item.icon"></span>
          <span class="metric-trend" :class="{ down: item.trendType === 'down' }">
            <span aria-hidden="true">{{ item.trendType === 'down' ? '↓' : '↑' }}</span>
            {{ item.trend }}
          </span>
        </div>
        <span class="metric-label">{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
        <small>{{ item.caption }}</small>
      </article>
    </section>

    <section class="insight-grid">
      <article class="panel keyword-panel">
        <div class="panel-heading">
          <div>
            <h2>高频意图词</h2>
            <span>高频用户搜索意图</span>
          </div>
          <div class="live-badge">实时穿梭</div>
        </div>
        <div class="keyword-stage">
          <span
            v-for="item in intentParticles"
            :key="item.id"
            class="cloud-tag"
            :style="item.style"
          >
            {{ item.text }}
          </span>
        </div>
        <div class="keyword-footer">
          <span><i></i>前三核心词</span>
          <span><i class="purple"></i>高频词</span>
          <strong>关键词总数 328</strong>
        </div>
      </article>

      <article class="panel trend-panel">
        <div class="panel-heading">
          <div>
            <h2>文章数据与收录趋势图</h2>
            <span>文章发布与收录趋势</span>
          </div>
          <button class="ghost-button" type="button">近30日</button>
        </div>
        <div class="trend-summary">
          <article>
            <span class="dot blue"></span>
            <div>
              <small>近30日创作总量</small>
              <strong>487 <em>条</em></strong>
            </div>
          </article>
          <article>
            <span class="dot purple"></span>
            <div>
              <small>近30日发布总量</small>
              <strong>462 <em>条</em></strong>
            </div>
          </article>
          <article>
            <small>发布率</small>
            <strong>94.8<em>%</em></strong>
          </article>
        </div>
        <div class="line-chart" aria-label="文章创作与发布趋势">
          <svg viewBox="0 0 760 236" role="img" aria-hidden="true">
            <defs>
              <linearGradient id="createTrendFill" x1="0" x2="0" y1="0" y2="1">
                <stop offset="0%" stop-color="#7db7ff" stop-opacity="0.18" />
                <stop offset="100%" stop-color="#7db7ff" stop-opacity="0" />
              </linearGradient>
              <linearGradient id="publishTrendFill" x1="0" x2="0" y1="0" y2="1">
                <stop offset="0%" stop-color="#b39cff" stop-opacity="0.16" />
                <stop offset="100%" stop-color="#b39cff" stop-opacity="0" />
              </linearGradient>
            </defs>
            <g class="chart-grid">
              <line v-for="line in trendGridLines" :key="line" x1="0" x2="760" :y1="line" :y2="line" />
            </g>
            <polygon class="line-area line-area-create" :points="createAreaPoints" />
            <polygon class="line-area line-area-publish" :points="publishAreaPoints" />
            <polyline class="trend-line trend-line-create" :points="createLinePoints" />
            <polyline class="trend-line trend-line-publish" :points="publishLinePoints" />
            <g class="trend-dots">
              <circle v-for="point in createChartPoints" :key="`create-${point.x}`" class="create-dot" :cx="point.x" :cy="point.y" r="4" />
              <circle v-for="point in publishChartPoints" :key="`publish-${point.x}`" class="publish-dot" :cx="point.x" :cy="point.y" r="4" />
            </g>
          </svg>
          <div class="line-chart-axis">
            <small v-for="item in trendData" :key="item.date">{{ item.date }}</small>
          </div>
        </div>
      </article>
    </section>

    <section class="panel report-table-panel">
      <nav class="report-tabs" aria-label="报表类型">
        <button
          v-for="tab in reportTabs"
          :key="tab"
          class="report-tab"
          :class="{ active: activeTab === tab }"
          type="button"
          @click="activeTab = tab"
        >
          {{ tab }}
        </button>
      </nav>

      <div class="report-alert">
        <span aria-hidden="true">!</span>
        由于大模型的动态学习、千人千面等特性，不同时间、不同区域的用户查询结果可能存在差异，报表支持在线预览最新的 5000 条数据。
      </div>

      <div class="filter-block">
        <div class="filter-header">
          <strong>平台筛选</strong>
          <span>已选 {{ selectedPlatforms.length }} / {{ platformChips.length }} 项</span>
        </div>
        <div class="platform-chips">
          <button
            v-for="platform in platformChips"
            :key="platform.name"
            class="platform-chip"
            :class="{ active: selectedPlatforms.includes(platform.name) }"
            type="button"
            @click="togglePlatform(platform.name)"
          >
            <span class="chip-mark">
              <img v-if="platform.logo" :src="platform.logo" :alt="platform.name" />
              <template v-else>{{ platform.short }}</template>
            </span>
            {{ platform.name }}
            <em>{{ platform.count }}</em>
          </button>
        </div>

        <div class="filter-form">
          <label>
            <span>关键词</span>
            <input v-model="searchKeyword" type="text" placeholder="搜索问题或关键词" />
          </label>
          <label>
            <span>开始时间</span>
            <input v-model="dateRange.start" type="date" />
          </label>
          <label>
            <span>结束时间</span>
            <input v-model="dateRange.end" type="date" />
          </label>
          <button type="button" class="primary-button">查询</button>
        </div>
      </div>

      <div class="table-toolbar">
        <div>
          <h2>智能问答命中明细</h2>
          <span>当前展示 {{ displayedRows.length }} / {{ filteredRows.length }} 条命中记录。</span>
        </div>
        <div class="toolbar-actions">
          <button type="button" class="ghost-button">导出</button>
          <button type="button" class="primary-button" @click="copyDashboardUrl">复制看板链接</button>
        </div>
      </div>

      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>序号</th>
              <th>问题 / 关键词</th>
              <th>报表类型</th>
              <th>智能平台</th>
              <th>命中状态</th>
              <th>查询时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, index) in displayedRows" :key="row.id">
              <td class="mono muted">{{ String((currentPage - 1) * pageSize + index + 1).padStart(2, '0') }}</td>
              <td>
                <div class="question-cell">
                  <span class="hot-icon">⌁</span>
                  <strong>{{ row.question }}</strong>
                </div>
              </td>
              <td><span class="type-pill">{{ row.type }}</span></td>
              <td>
                <span class="platform-cell">
                  <i>
                    <img
                      v-if="platformLogoMap[row.platform]"
                      :src="platformLogoMap[row.platform]"
                      :alt="row.platform"
                    />
                    <template v-else>{{ row.platform.slice(0, 1) }}</template>
                  </i>
                  {{ row.platform }}
                </span>
              </td>
              <td>
                <span class="status-pill" :class="row.status === '已命中' ? 'success' : 'warning'">
                  {{ row.status }}
                </span>
              </td>
              <td class="mono muted">{{ row.time }}</td>
              <td><button type="button" class="link-button">转到平台 →</button></td>
            </tr>
            <tr v-if="filteredRows.length === 0">
              <td class="empty-table-cell" colspan="7">暂无匹配的命中记录</td>
            </tr>
          </tbody>
        </table>
      </div>

      <footer class="table-footer">
        <span>共 <strong>{{ filteredRows.length }}</strong> 条数据 · 每页展示 {{ pageSize }} 条</span>
        <div v-if="totalPages > 1" class="pager">
          <button type="button" :disabled="currentPage === 1" @click="changePage(currentPage - 1)">‹</button>
          <button
            v-for="page in pageNumbers"
            :key="page"
            type="button"
            :class="{ active: page === currentPage }"
            @click="changePage(page)"
          >
            {{ page }}
          </button>
          <button type="button" :disabled="currentPage === totalPages" @click="changePage(currentPage + 1)">›</button>
        </div>
      </footer>
    </section>
  </main>
</template>

<script setup lang="ts">
import type { CSSProperties } from 'vue'
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getActiveCompanyPackageBinding } from '@/api/customer'
import { getKeywordGroupPage, getKeywordGroupQuestions, getProjectDetail } from '@/api/project'
import type { CompanyPackageBinding, KeywordGroup, KeywordGroupQuestion, Project } from '@/types'
import ai360Logo from '@/assets/ai-model-logos/ai360-color.png'
import deepseekLogo from '@/assets/ai-model-logos/deepseek-color.png'
import doubaoLogo from '@/assets/ai-model-logos/doubao.png'
import glmLogo from '@/assets/ai-model-logos/glm.png'
import hailuoLogo from '@/assets/ai-model-logos/hailuo-color.png'
import hunyuanLogo from '@/assets/ai-model-logos/hunyuan-color.png'
import kimiLogo from '@/assets/ai-model-logos/kimi.png'
import minimaxLogo from '@/assets/ai-model-logos/minimax-color.png'
import qwenLogo from '@/assets/ai-model-logos/qwen-color.png'
import wenxinLogo from '@/assets/ai-model-logos/文心一言.png'
import xiaomiMimoLogo from '@/assets/ai-model-logos/xiaomimimo.png'

type MetricTone = 'blue' | 'purple' | 'teal' | 'orange'
type TrendType = 'up' | 'down'

interface MetricCard {
  label: string
  value: string
  caption: string
  trend: string
  trendType: TrendType
  tone: MetricTone
  icon: string
}

interface ReportRow {
  id: number
  question: string
  type: string
  platform: string
  status: string
  time: string
  timeValue: number
}

interface IndexingSource {
  name: string
  short: string
  logo: string
  value: string
  percent: number
  color: string
  bg: string
  gradient: string
}

interface AiExposurePlatform {
  name: string
  enName: string
  short: string
  value: number
  percent: number
  trend: string
  trendType: TrendType
  iconBg: string
  iconColor: string
  highlighted?: boolean
}

interface EngineFlowItem {
  label: string
  value: string
  tone: 'blue' | 'purple' | 'teal'
}

interface IntentTag {
  text: string
  weight: number
}

interface IntentParticle {
  id: number
  text: string
  weight: number
  offsetX: number
  offsetY: number
  z: number
  speed: number
  style: CSSProperties
}

const route = useRoute()
const router = useRouter()
const projectId = computed(() => Number(route.params.id) || 10086)
const project = ref<Project | null>(null)
const activePackageBinding = ref<CompanyPackageBinding | null>(null)
const refreshedAt = ref(new Date())

const activeTab = ref('全部')
const searchKeyword = ref('')
const selectedPlatforms = ref(['全部'])
const currentPage = ref(1)
const pageSize = 5
const dateRange = reactive({
  start: '2026-01-01',
  end: '2026-01-31',
})

const reportTabs = ['全部', '搜索报表', '问答报表', '品牌报表']

function padTime(value: number) {
  return `${value}`.padStart(2, '0')
}

function toDate(value?: string | Date | null) {
  if (!value) return null
  const date = value instanceof Date ? value : new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

function addPackageMonths(value?: string | null, months = 0) {
  if (!value || !months) return null
  const start = toDate(value)
  if (!start) return null
  const end = new Date(start)
  end.setMonth(end.getMonth() + months)
  end.setDate(end.getDate() - 1)
  return end
}

function addDays(value: Date, days: number) {
  const date = new Date(value)
  date.setDate(date.getDate() + days)
  return date
}

function dayDiff(start: Date, end: Date) {
  const startDate = new Date(start.getFullYear(), start.getMonth(), start.getDate())
  const endDate = new Date(end.getFullYear(), end.getMonth(), end.getDate())
  return Math.max(0, Math.round((endDate.getTime() - startDate.getTime()) / 86400000))
}

function formatDate(value?: string | Date | null) {
  const date = toDate(value)
  if (!date) return '-'
  return `${date.getFullYear()}.${padTime(date.getMonth() + 1)}.${padTime(date.getDate())}`
}

function formatDateInput(value?: string | Date | null) {
  const date = toDate(value)
  if (!date) return ''
  return `${date.getFullYear()}-${padTime(date.getMonth() + 1)}-${padTime(date.getDate())}`
}

function formatMonthDay(value?: string | Date | null) {
  const date = toDate(value)
  if (!date) return '--'
  return `${padTime(date.getMonth() + 1)}-${padTime(date.getDate())}`
}

function formatDateTime(value?: string | Date | null) {
  const date = toDate(value)
  if (!date) return '-'
  return `${date.getFullYear()}-${padTime(date.getMonth() + 1)}-${padTime(date.getDate())} ${padTime(date.getHours())}:${padTime(date.getMinutes())}:${padTime(date.getSeconds())}`
}

function formatHourMinute(value?: string | Date | null) {
  const date = toDate(value)
  if (!date) return '--:--'
  return `${padTime(date.getHours())}:${padTime(date.getMinutes())}`
}

function formatInt(value: number) {
  return Math.round(value).toLocaleString('zh-CN')
}

const projectName = computed(() => project.value?.projectName || `项目 ${projectId.value}`)
const serviceStartDate = computed(() => {
  const packageStart = activePackageBinding.value?.boundAt
  return toDate(packageStart || project.value?.startDate || project.value?.activatedAt || dateRange.start) || new Date(dateRange.start)
})
const serviceEndDate = computed(() => {
  const packageEnd = addPackageMonths(activePackageBinding.value?.boundAt, activePackageBinding.value?.serviceMonths || 0)
  return packageEnd || toDate(project.value?.endDate || project.value?.expiredAt || dateRange.end) || new Date(dateRange.end)
})
const servicePeriod = computed(() => {
  return `${formatDate(serviceStartDate.value)} - ${formatDate(serviceEndDate.value)}`
})
const refreshDateTime = computed(() => formatDateTime(refreshedAt.value))
const syncTime = computed(() => formatHourMinute(refreshedAt.value))

const platformLogoMap: Record<string, string> = {
  DeepSeek: deepseekLogo,
  'DeepSeek PC': deepseekLogo,
  豆包: doubaoLogo,
  文心一言: wenxinLogo,
  腾讯元宝: hunyuanLogo,
  元宝: hunyuanLogo,
  通义千问: qwenLogo,
  智谱清言: glmLogo,
  '360 智脑': ai360Logo,
  MiniMax: minimaxLogo,
  '小米 Mimo': xiaomiMimoLogo,
  Kimi: kimiLogo,
  讯飞星火: hailuoLogo,
}

const indexingSources: IndexingSource[] = [
  { name: 'DeepSeek', short: 'D', logo: deepseekLogo, value: '11.27万', percent: 100, color: '#2f6bff', bg: '#eef4ff', gradient: 'linear-gradient(90deg, #4f6bff, #2f4ecf)' },
  { name: '豆包', short: '豆', logo: doubaoLogo, value: '11.12万', percent: 98.7, color: '#7b61ff', bg: '#f0edff', gradient: 'linear-gradient(90deg, #7b61ff, #5b4bd6)' },
  { name: '文心一言', short: '文', logo: wenxinLogo, value: '9.68万', percent: 85.9, color: '#2563eb', bg: '#eff6ff', gradient: 'linear-gradient(90deg, #60a5fa, #2563eb)' },
  { name: '腾讯元宝', short: '元', logo: hunyuanLogo, value: '8.35万', percent: 74.1, color: '#0891b2', bg: '#ecfeff', gradient: 'linear-gradient(90deg, #22d3ee, #0891b2)' },
  { name: '通义千问', short: '通', logo: qwenLogo, value: '7.24万', percent: 64.2, color: '#4f46e5', bg: '#eef2ff', gradient: 'linear-gradient(90deg, #818cf8, #4f46e5)' },
  { name: '智谱清言', short: '智', logo: glmLogo, value: '5.16万', percent: 45.8, color: '#0f766e', bg: '#f0fdfa', gradient: 'linear-gradient(90deg, #4dd4ac, #0f766e)' },
  { name: '360 智脑', short: '360', logo: ai360Logo, value: '4.52万', percent: 40.1, color: '#16a34a', bg: '#f0fdf4', gradient: 'linear-gradient(90deg, #86efac, #16a34a)' },
  { name: 'MiniMax', short: 'M', logo: minimaxLogo, value: '1.86万', percent: 16.5, color: '#d97706', bg: '#fff7ed', gradient: 'linear-gradient(90deg, #fbbf24, #d97706)' },
  { name: '小米 Mimo', short: '米', logo: xiaomiMimoLogo, value: '1.40万', percent: 12.4, color: '#c77640', bg: '#fff7ed', gradient: 'linear-gradient(90deg, #e8985c, #c77640)' },
  { name: 'Kimi', short: 'K', logo: kimiLogo, value: '0.39万', percent: 3.5, color: '#1f2937', bg: '#f3f4f6', gradient: 'linear-gradient(90deg, #6b7280, #111827)' },
]

const lissajousPlatforms = [
  { name: 'DeepSeek', logo: deepseekLogo, value: '11.27万', big: true },
  { name: '豆包', logo: doubaoLogo, value: '11.12万', big: true },
  { name: '文心一言', logo: wenxinLogo, value: '9.68万', big: true },
  { name: '元宝', logo: hunyuanLogo, value: '8.35万', big: true },
  { name: '通义千问', logo: qwenLogo, value: '7.24万', big: true },
  { name: '智谱清言', logo: glmLogo, value: '5.16万', big: false },
  { name: '360 智脑', logo: ai360Logo, value: '4.52万', big: false },
  { name: 'MiniMax', logo: minimaxLogo, value: '1.86万', big: false },
  { name: '小米 Mimo', logo: xiaomiMimoLogo, value: '1.40万', big: false },
  { name: 'Kimi', logo: kimiLogo, value: '0.39万', big: false },
]

const aiExposurePlatforms: AiExposurePlatform[] = [
  {
    name: '抖音AI',
    enName: 'Douyin AI',
    short: '抖',
    value: 52281,
    percent: 44.8,
    trend: '4.2%',
    trendType: 'up',
    iconBg: '#111827',
    iconColor: '#ffffff',
    highlighted: true,
  },
  {
    name: '百度AI',
    enName: 'Baidu AI',
    short: '百',
    value: 48356,
    percent: 41.5,
    trend: '2.1%',
    trendType: 'up',
    iconBg: 'linear-gradient(135deg, #2932e1, #1b22a0)',
    iconColor: '#ffffff',
  },
  {
    name: '夸克AI',
    enName: 'Quark AI',
    short: '夸',
    value: 15946,
    percent: 13.7,
    trend: '1.5%',
    trendType: 'up',
    iconBg: 'linear-gradient(135deg, #4f6bff, #7b61ff)',
    iconColor: '#ffffff',
  },
]

const engineFlowItems: EngineFlowItem[] = [
  { label: '收录分析', value: '610K', tone: 'blue' },
  { label: '信源归因', value: '328', tone: 'purple' },
  { label: '搜索曝光', value: '116K', tone: 'teal' },
]
const aiExposureTotal = computed(() => aiExposurePlatforms.reduce((total, item) => total + item.value, 0))

const metricCards: MetricCard[] = [
  {
    label: '智能平台命中总量',
    value: '12,486',
    caption: '本月问答与搜索累计命中',
    trend: '18.2%',
    trendType: 'up',
    tone: 'blue',
    icon: '<svg viewBox="0 0 24 24"><path d="M4 19V5"/><path d="M4 19h16"/><path d="M8 16l3-5 4 3 5-8"/></svg>',
  },
  {
    label: '内容发布量',
    value: '462',
    caption: '文章、问答素材与品牌内容',
    trend: '31 条',
    trendType: 'up',
    tone: 'purple',
    icon: '<svg viewBox="0 0 24 24"><path d="M4 5h16"/><path d="M4 12h16"/><path d="M4 19h10"/></svg>',
  },
  {
    label: '联系方式曝光量',
    value: '830',
    caption: '含电话、微信、邮箱与官网入口',
    trend: '12.5%',
    trendType: 'up',
    tone: 'teal',
    icon: '<svg viewBox="0 0 24 24"><path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6A19.79 19.79 0 0 1 2.08 4.18 2 2 0 0 1 4.06 2h3a2 2 0 0 1 2 1.72c.12.9.32 1.77.59 2.61a2 2 0 0 1-.45 2.11L8 9.64a16 16 0 0 0 6.36 6.36l1.2-1.2a2 2 0 0 1 2.11-.45c.84.27 1.71.47 2.61.59A2 2 0 0 1 22 16.92z"/></svg>',
  },
  {
    label: '品牌问题覆盖量',
    value: '67',
    caption: '客户关注问题覆盖与沉淀',
    trend: '8 个',
    trendType: 'up',
    tone: 'orange',
    icon: '<svg viewBox="0 0 24 24"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>',
  },
]

const fallbackIntentTags: IntentTag[] = [
  { text: '电动门厂家', weight: 1 },
  { text: '伸缩门厂家', weight: 0.95 },
  { text: '悬浮门厂家', weight: 0.9 },
  { text: '快速门厂家', weight: 0.75 },
  { text: '卷帘门厂家', weight: 0.7 },
  { text: '工业门', weight: 0.65 },
  { text: '自动门', weight: 0.6 },
  { text: '快速门厂家排名', weight: 0.55 },
  { text: '门厂家', weight: 0.55 },
  { text: '卷帘门', weight: 0.5 },
  { text: '电动卷帘门', weight: 0.48 },
  { text: '车库门', weight: 0.45 },
  { text: '伸缩门', weight: 0.45 },
  { text: '工业快速门', weight: 0.42 },
  { text: '门厂家排名', weight: 0.4 },
  { text: '悬浮门价格', weight: 0.4 },
  { text: '卷帘门定制', weight: 0.38 },
  { text: '电动门安装', weight: 0.35 },
]

const intentTags = ref<IntentTag[]>(fallbackIntentTags)
const intentParticles = ref<IntentParticle[]>([])
let particleId = 0
let animationFrame = 0

const trendValueSeries = [
  { create: 17, publish: 17 },
  { create: 18, publish: 18 },
  { create: 17, publish: 16 },
  { create: 18, publish: 18 },
  { create: 18, publish: 17 },
  { create: 17, publish: 18 },
  { create: 18, publish: 17 },
  { create: 18, publish: 6 },
]
const trendData = computed(() => {
  const spanDays = dayDiff(serviceStartDate.value, serviceEndDate.value)
  const denominator = Math.max(trendValueSeries.length - 1, 1)

  return trendValueSeries.map((item, index) => {
    const offset = Math.round((spanDays / denominator) * index)
    return {
      ...item,
      date: formatMonthDay(addDays(serviceStartDate.value, offset)),
    }
  })
})

const trendChartWidth = 760
const trendChartHeight = 236
const trendChartPadding = {
  top: 16,
  right: 18,
  bottom: 26,
  left: 18,
}
const trendMaxValue = computed(() => Math.max(...trendData.value.flatMap((item) => [item.create, item.publish]), 1))
const trendGridLines = computed(() => {
  const steps = 5
  const innerHeight = trendChartHeight - trendChartPadding.top - trendChartPadding.bottom
  return Array.from({ length: steps }, (_, index) => trendChartPadding.top + (innerHeight / (steps - 1)) * index)
})

function getTrendChartPoints(key: 'create' | 'publish') {
  const innerWidth = trendChartWidth - trendChartPadding.left - trendChartPadding.right
  const innerHeight = trendChartHeight - trendChartPadding.top - trendChartPadding.bottom
  const denominator = Math.max(trendData.value.length - 1, 1)
  return trendData.value.map((item, index) => {
    const x = trendChartPadding.left + (innerWidth / denominator) * index
    const y = trendChartPadding.top + (1 - item[key] / trendMaxValue.value) * innerHeight
    return { x: Number(x.toFixed(2)), y: Number(y.toFixed(2)) }
  })
}

const createChartPoints = computed(() => getTrendChartPoints('create'))
const publishChartPoints = computed(() => getTrendChartPoints('publish'))
const createLinePoints = computed(() => createChartPoints.value.map((point) => `${point.x},${point.y}`).join(' '))
const publishLinePoints = computed(() => publishChartPoints.value.map((point) => `${point.x},${point.y}`).join(' '))

function getTrendAreaPoints(points: Array<{ x: number; y: number }>) {
  const baseline = trendChartHeight - trendChartPadding.bottom
  const lastPoint = points[points.length - 1]
  return [
    `${points[0]?.x || trendChartPadding.left},${baseline}`,
    ...points.map((point) => `${point.x},${point.y}`),
    `${lastPoint?.x || trendChartWidth - trendChartPadding.right},${baseline}`,
  ].join(' ')
}

const createAreaPoints = computed(() => getTrendAreaPoints(createChartPoints.value))
const publishAreaPoints = computed(() => getTrendAreaPoints(publishChartPoints.value))

const platformChips = computed(() => [
  { name: '全部', short: '✓', logo: '', count: '60.99万' },
  ...indexingSources.map((source) => ({
    name: source.name,
    short: source.short,
    logo: source.logo,
    count: source.value,
  })),
])

const reportRows = ref<ReportRow[]>([])

const filteredRows = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase()
  return reportRows.value
    .filter((row) => {
      const matchTab = activeTab.value === '全部' || row.type === activeTab.value
      const matchPlatform = selectedPlatforms.value.includes('全部') || selectedPlatforms.value.includes(row.platform)
      const matchKeyword =
        !keyword ||
        row.question.toLowerCase().includes(keyword) ||
        row.platform.toLowerCase().includes(keyword) ||
        row.type.toLowerCase().includes(keyword)
      return matchTab && matchPlatform && matchKeyword
    })
    .sort((first, second) => second.timeValue - first.timeValue)
})

const totalPages = computed(() => Math.max(1, Math.ceil(filteredRows.value.length / pageSize)))
const displayedRows = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return filteredRows.value.slice(start, start + pageSize)
})
const pageNumbers = computed(() => Array.from({ length: totalPages.value }, (_, index) => index + 1))

watch([activeTab, searchKeyword, selectedPlatforms], () => {
  currentPage.value = 1
})

watch(filteredRows, () => {
  if (currentPage.value > totalPages.value) {
    currentPage.value = totalPages.value
  }
})

function questionReportType(question: KeywordGroupQuestion) {
  if (question.questionTier === 'A') return '品牌报表'
  if (question.questionTier === 'B') return '搜索报表'
  return '问答报表'
}

function seededRatio(seed: number) {
  const value = Math.sin(seed * 9301 + 49297) * 233280
  return value - Math.floor(value)
}

function mockHitTime(index: number) {
  const maxRange = 12 * 60 * 60 * 1000
  const offset = Math.floor(seededRatio(index + projectId.value) * maxRange)
  const date = new Date(refreshedAt.value.getTime() - offset)
  return {
    text: formatDateTime(date),
    value: date.getTime(),
  }
}

function buildReportRowsFromQuestions(questions: KeywordGroupQuestion[]): ReportRow[] {
  const modelSources = indexingSources
  return questions
    .filter((question) => question.questionText?.trim())
    .map((question, index) => {
      const source = modelSources[index % modelSources.length]
      const hitTime = mockHitTime(index + 1)
      return {
        id: question.id || index + 1,
        question: question.questionText.trim(),
        type: questionReportType(question),
        platform: source.name,
        status: '已命中',
        time: hitTime.text,
        timeValue: hitTime.value,
      }
    })
    .sort((first, second) => second.timeValue - first.timeValue)
}

function changePage(page: number) {
  currentPage.value = Math.min(Math.max(page, 1), totalPages.value)
}

function togglePlatform(name: string) {
  if (name === '全部') {
    selectedPlatforms.value = ['全部']
    return
  }
  const next = selectedPlatforms.value.filter((item) => item !== '全部')
  const index = next.indexOf(name)
  if (index >= 0) {
    next.splice(index, 1)
  } else {
    next.push(name)
  }
  selectedPlatforms.value = next.length ? next : ['全部']
}

async function copyDashboardUrl() {
  await navigator.clipboard.writeText(`${window.location.origin}/dashboard/demo-${projectId.value}`)
  ElMessage.success('看板链接已复制')
}

function normalizeIntentText(value?: string | null) {
  if (!value) return ''
  const compact = value
    .replace(/[“”"'`]/g, '')
    .replace(/[?？!！。；;：:]/g, '')
    .replace(/\s+/g, '')
    .trim()

  if (compact.length <= 12) return compact

  const parts = compact
    .split(/哪家好|哪个好|推荐|多少钱|价格|报价|怎么选|如何选|有哪些|是否|可以|适合|需要|的|，|,|、/)
    .map((item) => item.trim())
    .filter((item) => item.length >= 3)
    .sort((a, b) => b.length - a.length)

  const preferred = parts.find((item) => item.length <= 12) || parts[0] || compact
  return preferred.length > 12 ? preferred.slice(0, 12) : preferred
}

function mapQuestionWeight(question: KeywordGroupQuestion, index: number) {
  const score = Number(question.totalScore || question.scoreIntent || question.scoreRelevance || 0)
  if (score > 0) return Math.min(1, Math.max(0.35, score / 100))
  if (question.questionTier === 'A') return 0.92
  if (question.questionTier === 'B') return 0.72
  if (question.questionTier === 'C') return 0.54
  return Math.max(0.35, 0.9 - index * 0.03)
}

function buildIntentTagsFromQuestions(questions: KeywordGroupQuestion[]) {
  const map = new Map<string, number>()
  questions.forEach((question, index) => {
    const text = normalizeIntentText(question.questionText)
    if (!text) return
    const weight = mapQuestionWeight(question, index)
    map.set(text, Math.max(map.get(text) || 0, weight))
  })

  const tags = Array.from(map.entries())
    .map(([text, weight]) => ({ text, weight }))
    .sort((a, b) => b.weight - a.weight)
    .slice(0, 24)

  return tags.length ? tags : fallbackIntentTags
}

async function getBoundKeywordGroups() {
  const selectedGroups = project.value?.selectedKeywordGroups || []
  if (selectedGroups.length) return selectedGroups

  const { data } = await getKeywordGroupPage({
    current: 1,
    size: 20,
    projectId: projectId.value,
  })
  return data.data.records || []
}

async function loadIntentTagsFromKeywordGroups() {
  try {
    const groups = await getBoundKeywordGroups()
    const questionResults = await Promise.all(
      groups.slice(0, 8).map((group: KeywordGroup) =>
        getKeywordGroupQuestions(group.id, { current: 1, size: 80 }).then((res) => res.data.data.records || []),
      ),
    )
    const questions = questionResults.flat()
    intentTags.value = buildIntentTagsFromQuestions(questions)
    reportRows.value = buildReportRowsFromQuestions(questions)
    resetIntentParticles()
  } catch {
    intentTags.value = fallbackIntentTags
    reportRows.value = []
    resetIntentParticles()
  }
}

async function loadProjectInfo() {
  try {
    const { data } = await getProjectDetail(projectId.value)
    project.value = data.data || null
    if (project.value?.companyId) {
      const packageRes = await getActiveCompanyPackageBinding(project.value.companyId)
      activePackageBinding.value = packageRes.data.data || null
    } else {
      activePackageBinding.value = null
    }
    dateRange.start = formatDateInput(serviceStartDate.value)
    dateRange.end = formatDateInput(serviceEndDate.value)
    loadIntentTagsFromKeywordGroups()
  } catch {
    project.value = null
    activePackageBinding.value = null
    reportRows.value = []
    loadIntentTagsFromKeywordGroups()
  }
}

function getIntentTagStyle(weight: number) {
  const fontSize = 12 + weight * 14
  if (weight >= 0.85) {
    return { fontSize, color: '#2f6bff', fontWeight: 700 }
  }
  if (weight >= 0.6) {
    return { fontSize, color: '#1e3a8a', fontWeight: 600 }
  }
  if (weight >= 0.4) {
    return { fontSize, color: '#5b6473', fontWeight: 600 }
  }
  return { fontSize, color: '#8a94a6', fontWeight: 500 }
}

function createIntentParticle(forceFront = false): IntentParticle {
  const sourceTags = intentTags.value.length ? intentTags.value : fallbackIntentTags
  const tag = sourceTags[Math.floor(Math.random() * sourceTags.length)]
  const angle = Math.random() * Math.PI * 2
  const radius = Math.random() * 0.85
  const { fontSize, color, fontWeight } = getIntentTagStyle(tag.weight)

  return {
    id: particleId++,
    text: tag.text,
    weight: tag.weight,
    offsetX: Math.cos(angle) * radius * 190,
    offsetY: Math.sin(angle) * radius * 110,
    z: forceFront ? Math.random() * 600 - 600 : -800,
    speed: 2.5 + Math.random() * 1.5,
    style: {
      fontSize: `${fontSize}px`,
      color,
      fontWeight,
      opacity: 0,
      transform: 'translate3d(0, 0, 0) translate(-50%, -50%) scale(0.15)',
    },
  }
}

function resetIntentParticles() {
  particleId = 0
  intentParticles.value = Array.from({ length: 14 }, () => createIntentParticle(true))
}

function updateIntentParticles() {
  const next: IntentParticle[] = intentParticles.value
    .map<IntentParticle>((particle) => {
      const z = particle.z + particle.speed
      const normalized = (z + 800) / 1000
      const scale = 0.15 + normalized * 1.4
      let opacity = 1

      if (normalized < 0.2) {
        opacity = (normalized / 0.2) * 0.6
      } else if (normalized < 0.75) {
        opacity = 0.6 + ((normalized - 0.2) / 0.55) * 0.4
      } else {
        opacity = 1 - (normalized - 0.75) / 0.25
      }

      return {
        ...particle,
        z,
        style: {
          ...particle.style,
          opacity: Math.max(0, opacity),
          transform: `translate3d(${particle.offsetX}px, ${particle.offsetY}px, 0) translate(-50%, -50%) scale(${scale})`,
        } satisfies CSSProperties,
      }
    })
    .filter((particle) => particle.z <= 250)

  while (next.length < 14) {
    next.push(createIntentParticle())
  }

  intentParticles.value = next
  animationFrame = window.requestAnimationFrame(updateIntentParticles)
}

// ===== Lissajous 可见度引擎 =====
const lissaStageRef = ref<HTMLElement | null>(null)
const lissaCanvasRef = ref<HTMLCanvasElement | null>(null)
const lissaLogosRef = ref<HTMLElement | null>(null)
const engineFlowRef = ref<HTMLElement | null>(null)

const LISSA_TRAIL_LIFE_MS = 2000
const LISSA_TRIGGER_RADIUS = 100
const LISSA_SAFE_RADIUS = 150
const LISSA_REJOIN_DURATION_MS = 1400
const LISSA_EJECT_BASE_SPEED = 3.5
const LISSA_EJECT_DEPTH_GAIN = 0.05
const LISSA_TRAIL_RGB = '100,110,125'
const LISSA_TRAIL_ALPHA_PEAK = 0.42
const LISSA_CORE_Y_OFFSET = -30
const LISSA_VISIBLE_WINDOW = 0.4
const LISSA_HEAD_LINE_WIDTH = 1.8

type LissaPhase = 'lissa' | 'eject' | 'rejoin'
interface LissaHarmonic {
  f1: number
  f2: number
  ph1: number
  ph2: number
  w1: number
  w2: number
}
interface LissaTrailPoint {
  x: number
  y: number
  t: number
}
interface LissaState {
  x: number
  y: number
  hx: LissaHarmonic
  hy: LissaHarmonic
  // 每个 logo 的独立时间偏移，让它们在 Lissajous 时间轴上一开始就分散
  tOffset: number
  phase: LissaPhase
  vx: number
  vy: number
  rejoinStartT: number
  rejoinFromX: number
  rejoinFromY: number
  ejectStartT: number
  trail: LissaTrailPoint[]
}

let lissaState: LissaState[] = []
let lissaChipNodes: HTMLElement[] = []
let lissaCtx: CanvasRenderingContext2D | null = null
let lissaStartT = 0
let lissaAnimationFrame = 0
let lissaResizeObserver: ResizeObserver | null = null

function lissaRand(min: number, max: number) {
  return min + Math.random() * (max - min)
}

function makeLissaHarmonic(baseFreqJitter: number): LissaHarmonic {
  return {
    f1: (1 + Math.random() * 2.5) * baseFreqJitter,
    f2: (1.4 + Math.random() * 2.0) * baseFreqJitter * 1.8,
    ph1: lissaRand(0, Math.PI * 2),
    ph2: lissaRand(0, Math.PI * 2),
    // 总权重 1.2 > 1：配合外层钳位，logo 实际运动会更经常贴近舞台四边
    w1: 0.75,
    w2: 0.45,
  }
}

function lissaPos(s: LissaState, t: number, halfW: number, halfH: number, cx: number, cy: number) {
  const ts = t + s.tOffset
  const dx = s.hx.w1 * Math.sin(s.hx.f1 * ts + s.hx.ph1) + s.hx.w2 * Math.sin(s.hx.f2 * ts + s.hx.ph2)
  const dy = s.hy.w1 * Math.sin(s.hy.f1 * ts + s.hy.ph1) + s.hy.w2 * Math.sin(s.hy.f2 * ts + s.hy.ph2)
  // 振幅 1.05：让 sin 叠加峰值能真的把 logo 推到舞台边缘，多余的部分由调用方钳位
  return { x: cx + dx * halfW * 1.05, y: cy + dy * halfH * 1.05 }
}

function lissaEaseOutCubic(t: number) {
  return 1 - Math.pow(1 - t, 3)
}

function resizeLissaCanvas() {
  const stage = lissaStageRef.value
  const canvas = lissaCanvasRef.value
  if (!stage || !canvas || !lissaCtx) return
  const rect = stage.getBoundingClientRect()
  const dpr = window.devicePixelRatio || 1
  canvas.width = rect.width * dpr
  canvas.height = rect.height * dpr
  canvas.style.width = `${rect.width}px`
  canvas.style.height = `${rect.height}px`
  lissaCtx.setTransform(dpr, 0, 0, dpr, 0, 0)
  updatePelletDistance()
}

function updatePelletDistance() {
  const flow = engineFlowRef.value
  if (!flow) return
  const items = flow.querySelectorAll<HTMLElement>('.engine-flow-item')
  if (items.length < 2) return
  const r0 = items[0].getBoundingClientRect()
  const r1 = items[1].getBoundingClientRect()
  const dist = r1.left + r1.width / 2 - (r0.left + r0.width / 2)
  flow.style.setProperty('--pellet-dist', `${dist}px`)
}

function getLissaBounds() {
  const stage = lissaStageRef.value
  if (!stage) return { w: 0, h: 0, minX: 0, maxX: 0, minY: 0, maxY: 0 }
  const rect = stage.getBoundingClientRect()
  return {
    w: rect.width,
    h: rect.height,
    minX: 50,
    maxX: rect.width - 50,
    minY: 60,
    maxY: rect.height - 110,
  }
}

function lissaTick(now: number) {
  if (!lissaCtx || !lissaState.length) {
    lissaAnimationFrame = window.requestAnimationFrame(lissaTick)
    return
  }
  const ctx = lissaCtx
  const b = getLissaBounds()
  // 舞台尺寸不正常时（挂载早期、隐藏状态等），跳过这一帧并不要污染 state
  if (b.w < 100 || b.h < 100) {
    lissaAnimationFrame = window.requestAnimationFrame(lissaTick)
    return
  }

  const cx = b.w / 2
  const cy = b.h / 2 + LISSA_CORE_Y_OFFSET
  const halfW = (b.maxX - b.minX) / 2
  const halfH = (b.maxY - b.minY) / 2

  ctx.clearRect(0, 0, b.w, b.h)
  const t = now - lissaStartT

  lissaState.forEach((s, i) => {
    const chip = lissaChipNodes[i]
    if (!chip) return

    const target = lissaPos(s, t, halfW, halfH, cx, cy)
    let x: number
    let y: number

    if (s.phase === 'lissa') {
      x = target.x
      y = target.y
      const ddx = x - cx
      const ddy = y - cy
      const dist = Math.sqrt(ddx * ddx + ddy * ddy)
      if (dist < LISSA_TRIGGER_RADIUS) {
        const dirX = dist > 0.1 ? ddx / dist : Math.random() - 0.5
        const dirY = dist > 0.1 ? ddy / dist : Math.random() - 0.5
        const ejectionStrength = LISSA_EJECT_BASE_SPEED + (LISSA_TRIGGER_RADIUS - dist) * LISSA_EJECT_DEPTH_GAIN
        s.vx = dirX * ejectionStrength
        s.vy = dirY * ejectionStrength
        s.phase = 'eject'
        s.ejectStartT = now
        x = cx + dirX * LISSA_TRIGGER_RADIUS
        y = cy + dirY * LISSA_TRIGGER_RADIUS
      }
    } else if (s.phase === 'eject') {
      s.vx *= 0.96
      s.vy *= 0.96
      x = s.x + s.vx
      y = s.y + s.vy
      x = Math.max(b.minX, Math.min(b.maxX, x))
      y = Math.max(b.minY, Math.min(b.maxY, y))
      const ddx = x - cx
      const ddy = y - cy
      const dist = Math.sqrt(ddx * ddx + ddy * ddy)
      const speedMag = Math.sqrt(s.vx * s.vx + s.vy * s.vy)
      const ejectAge = now - s.ejectStartT
      // 正常出口：飞够距离且基本停下
      if (dist > LISSA_SAFE_RADIUS && speedMag < 0.8) {
        s.phase = 'rejoin'
        s.rejoinStartT = now
        s.rejoinFromX = x
        s.rejoinFromY = y
      } else if (ejectAge > 3000) {
        // 兜底：3 秒还没满足正常出口条件（边界回弹卡住等情况），强制 rejoin
        s.phase = 'rejoin'
        s.rejoinStartT = now
        s.rejoinFromX = x
        s.rejoinFromY = y
      }
    } else {
      const k = Math.min(1, (now - s.rejoinStartT) / LISSA_REJOIN_DURATION_MS)
      const e = lissaEaseOutCubic(k)
      x = s.rejoinFromX + (target.x - s.rejoinFromX) * e
      y = s.rejoinFromY + (target.y - s.rejoinFromY) * e
      if (k >= 1) s.phase = 'lissa'
    }

    x = Math.max(b.minX, Math.min(b.maxX, x))
    y = Math.max(b.minY, Math.min(b.maxY, y))

    s.x = x
    s.y = y
    chip.style.transform = `translate(${x}px, ${y}px) translate(-50%, -50%)`

    s.trail.push({ x, y, t: now })
    while (s.trail.length && now - s.trail[0].t > LISSA_TRAIL_LIFE_MS) s.trail.shift()

    if (s.trail.length > 1) {
      const cutoff = 1 - LISSA_VISIBLE_WINDOW
      for (let j = 1; j < s.trail.length; j++) {
        const p0 = s.trail[j - 1]
        const p1 = s.trail[j]
        const age = now - (p0.t + p1.t) / 2
        const lifeRatio = 1 - age / LISSA_TRAIL_LIFE_MS
        if (lifeRatio < cutoff) continue
        const headRatio = (lifeRatio - cutoff) / LISSA_VISIBLE_WINDOW
        const alpha = Math.pow(headRatio, 1.4) * LISSA_TRAIL_ALPHA_PEAK
        const lineWidth = headRatio * LISSA_HEAD_LINE_WIDTH
        if (alpha < 0.01 || lineWidth < 0.05) continue
        ctx.strokeStyle = `rgba(${LISSA_TRAIL_RGB},${alpha.toFixed(3)})`
        ctx.lineWidth = lineWidth
        ctx.lineCap = 'round'
        ctx.beginPath()
        ctx.moveTo(p0.x, p0.y)
        ctx.lineTo(p1.x, p1.y)
        ctx.stroke()
      }
    }
  })

  lissaAnimationFrame = window.requestAnimationFrame(lissaTick)
}

function startLissajous() {
  const canvas = lissaCanvasRef.value
  const logosLayer = lissaLogosRef.value
  if (!canvas || !logosLayer) return

  // 如果舞台还没真正进入 layout（zero size 或太小），下一帧再试，避免在 (0,0) 起跳触发误判
  const stage = lissaStageRef.value
  if (stage) {
    const rect = stage.getBoundingClientRect()
    if (rect.width < 100 || rect.height < 100) {
      window.requestAnimationFrame(startLissajous)
      return
    }
  }

  lissaCtx = canvas.getContext('2d')
  if (!lissaCtx) return

  lissaChipNodes = Array.from(logosLayer.querySelectorAll<HTMLElement>('.lissa-chip'))

  const baseFreq = 0.00010
  lissaState = lissajousPlatforms.map((_, i) => ({
    x: 0,
    y: 0,
    hx: makeLissaHarmonic(baseFreq + i * 0.000003),
    hy: makeLissaHarmonic(baseFreq + i * 0.000003),
    // 给每个 logo 一个均匀分布在 [0, 60s] 内的时间偏移，让 10 个 logo 在 t=0 时就分布在 Lissajous 轨迹的不同位置
    tOffset: (i / lissajousPlatforms.length) * 60000 + Math.random() * 4000,
    phase: 'lissa',
    vx: 0,
    vy: 0,
    rejoinStartT: 0,
    rejoinFromX: 0,
    rejoinFromY: 0,
    ejectStartT: 0,
    trail: [],
  }))

  // 让每个 logo 的初始位置 = Lissajous 公式在 t=0 时的位置，避免从 (0,0) 跳到第一帧目标的"瞬移"被状态机理解为撞击
  const b0 = getLissaBounds()
  const cx = b0.w / 2
  const cy = b0.h / 2 + LISSA_CORE_Y_OFFSET
  const halfW = (b0.maxX - b0.minX) / 2
  const halfH = (b0.maxY - b0.minY) / 2
  lissaState.forEach((s) => {
    const p = lissaPos(s, 0, halfW, halfH, cx, cy)
    s.x = p.x
    s.y = p.y
  })

  resizeLissaCanvas()
  if (lissaStageRef.value) {
    lissaResizeObserver = new ResizeObserver(resizeLissaCanvas)
    lissaResizeObserver.observe(lissaStageRef.value)
  }
  window.requestAnimationFrame(updatePelletDistance)

  lissaStartT = performance.now()
  lissaAnimationFrame = window.requestAnimationFrame(lissaTick)
}

function stopLissajous() {
  if (lissaAnimationFrame) {
    window.cancelAnimationFrame(lissaAnimationFrame)
    lissaAnimationFrame = 0
  }
  if (lissaResizeObserver) {
    lissaResizeObserver.disconnect()
    lissaResizeObserver = null
  }
  lissaCtx = null
  lissaChipNodes = []
  lissaState = []
}

onMounted(() => {
  refreshedAt.value = new Date()
  loadProjectInfo()
  resetIntentParticles()
  animationFrame = window.requestAnimationFrame(updateIntentParticles)
  // 等 Vue 的 DOM 更新完成且 layout 稳定，再启动 Lissajous，否则 lissaStageRef 可能拿到 0 尺寸
  nextTick(() => {
    startLissajous()
  })
})

onBeforeUnmount(() => {
  window.cancelAnimationFrame(animationFrame)
  stopLissajous()
})
</script>

<style scoped>
.postsale-report-page {
  --primary: #2f6bff;
  --primary-deep: #1e3a8a;
  --primary-soft: #eef4ff;
  --primary-softer: #f5f8ff;
  --purple: #7b61ff;
  --purple-soft: #f0edff;
  --teal: #4dd4ac;
  --teal-soft: #e6f9f2;
  --orange: #ff9f1c;
  --orange-soft: #fff4e0;
  --bg: #f7f9fc;
  --surface: #ffffff;
  --text: #1f2937;
  --text-2: #5b6473;
  --text-3: #8a94a6;
  --text-4: #b0b8c5;
  --line: #ecf0f5;
  --line-2: #e1e7ee;
  min-height: calc(100vh - 72px);
  margin: -20px;
  padding: 24px;
  background: var(--bg);
  color: var(--text);
  font-family: Inter, -apple-system, BlinkMacSystemFont, "PingFang SC", "Microsoft YaHei", sans-serif;
  font-size: 14px;
}

.mono {
  font-family: "JetBrains Mono", "SF Mono", Consolas, monospace;
  font-variant-numeric: tabular-nums;
}

.panel,
.metric-card,
.report-hero {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 16px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}

.report-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 32px;
  align-items: end;
  padding: 24px;
  margin-bottom: 18px;
  background:
    radial-gradient(circle at 10% 0%, rgba(47, 107, 255, 0.14), transparent 32%),
    linear-gradient(135deg, #ffffff 0%, #f8fbff 100%);
}

.back-button,
.ghost-button,
.primary-button,
.link-button,
.report-tab,
.platform-chip,
.pager button {
  border: 0;
  font: inherit;
  cursor: pointer;
}

.back-button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 14px 0 10px;
  margin-bottom: 20px;
  color: #52627a;
  font-weight: 600;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(226, 232, 240, 0.92);
  border-radius: 999px;
  box-shadow: 0 10px 24px rgba(47, 107, 255, 0.08);
  transition:
    color 0.2s ease,
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s ease;
}

.back-button:hover {
  color: var(--primary);
  border-color: rgba(47, 107, 255, 0.18);
  box-shadow: 0 14px 28px rgba(47, 107, 255, 0.12);
  transform: translateY(-1px);
}

.back-button-icon {
  display: grid;
  width: 18px;
  height: 18px;
  place-items: center;
  color: inherit;
  font-size: 18px;
  line-height: 1;
}

.hero-eyebrow,
.panel-heading span {
  color: var(--text-3);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
}

.report-hero h1 {
  margin: 0;
  color: var(--text);
  font-size: 30px;
  font-weight: 700;
  letter-spacing: 0;
}

.report-hero p {
  max-width: 640px;
  margin: 10px 0 0;
  color: var(--text-2);
  line-height: 1.7;
}

.hero-meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(148px, 1fr));
  gap: 10px;
}

.hero-meta article {
  padding: 14px 16px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(47, 107, 255, 0.1);
  border-radius: 12px;
}

.hero-meta span,
.metric-label,
.metric-card small,
.trend-summary small,
.table-toolbar span,
.filter-header span,
.table-footer {
  color: var(--text-3);
}

.hero-meta strong {
  display: block;
  margin-top: 6px;
  color: var(--text);
  font-size: 13px;
  font-weight: 600;
}

.data-cockpit {
  position: relative;
  padding: 24px;
  margin-bottom: 18px;
  overflow: hidden;
}

.cockpit-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.cockpit-title,
.cockpit-sync {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.cockpit-title i {
  width: 4px;
  height: 16px;
  background: linear-gradient(180deg, var(--primary), var(--purple));
  border-radius: 999px;
}

.cockpit-title span {
  color: var(--text-2);
  font-size: 12px;
  font-weight: 700;
}

.cockpit-title em,
.cockpit-title strong {
  color: var(--text-3);
  font-size: 11px;
  font-style: normal;
  font-weight: 500;
}

.cockpit-sync {
  color: var(--text-3);
  font-family: "JetBrains Mono", Consolas, monospace;
  font-size: 11px;
}

.cockpit-sync span,
.engine-live i {
  width: 7px;
  height: 7px;
  background: var(--teal);
  border-radius: 50%;
  animation: statusPulse 2s ease-in-out infinite;
}

.cockpit-grid {
  display: grid;
  grid-template-columns: 22fr 56fr 22fr;
  gap: 20px;
  min-height: 380px;
}

.signal-tower,
.cockpit-kpis {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.cockpit-section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.cockpit-section-head h2 {
  margin: 0;
  color: var(--text);
  font-size: 13px;
  font-weight: 700;
}

.cockpit-section-head span {
  display: block;
  margin-top: 3px;
  color: var(--text-3);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.cockpit-section-head strong {
  color: var(--text-3);
  font-family: "JetBrains Mono", Consolas, monospace;
  font-size: 10px;
  font-weight: 600;
}

.signal-list {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 6px;
}

.signal-row {
  display: grid;
  grid-template-columns: 28px minmax(62px, 0.72fr) minmax(70px, 1fr) 52px;
  gap: 8px;
  align-items: center;
  min-height: 28px;
  padding: 4px 6px;
  border-radius: 8px;
  transition: background 0.15s ease;
}

.signal-row:hover {
  background: var(--primary-softer);
}

.signal-row i {
  display: inline-flex;
  width: 24px;
  height: 24px;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(47, 107, 255, 0.14);
  border-radius: 7px;
  box-shadow: 0 4px 10px rgba(47, 107, 255, 0.08);
  font-size: 10px;
  font-style: normal;
  font-weight: 800;
}

.signal-row i img,
.chip-mark img,
.platform-cell i img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.signal-row span {
  overflow: hidden;
  color: var(--text-2);
  font-size: 12px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.signal-bar {
  height: 5px;
  overflow: hidden;
  background: var(--line);
  border-radius: 999px;
}

.signal-bar em {
  display: block;
  height: 100%;
  border-radius: inherit;
}

.signal-row strong {
  color: var(--text);
  font-family: "JetBrains Mono", Consolas, monospace;
  font-size: 11px;
  font-weight: 700;
  text-align: right;
}

.signal-tower footer {
  display: flex;
  justify-content: space-between;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed var(--line-2);
}

.signal-tower footer span {
  color: var(--text-3);
  font-size: 10px;
}

.signal-tower footer strong {
  color: var(--text);
  font-size: 13px;
  font-weight: 800;
}

.visibility-engine {
  position: relative;
  min-width: 0;
  min-height: 380px;
  overflow: hidden;
  background: linear-gradient(180deg, rgba(47, 107, 255, 0.025) 0%, rgba(123, 97, 255, 0.035) 100%);
  border: 1px solid rgba(47, 107, 255, 0.08);
  border-radius: 16px;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.72);
}

.visibility-engine::before {
  position: absolute;
  inset: 0;
  background: radial-gradient(ellipse 70% 60% at 50% 50%, rgba(47, 107, 255, 0.08), transparent 70%);
  content: "";
}

.visibility-engine::after {
  position: absolute;
  inset: 66px 22px 78px;
  background:
    radial-gradient(circle at 12% 50%, rgba(47, 107, 255, 0.11), transparent 28%),
    radial-gradient(circle at 88% 50%, rgba(77, 212, 172, 0.12), transparent 30%),
    repeating-linear-gradient(90deg, transparent 0 38px, rgba(47, 107, 255, 0.08) 39px 40px);
  content: "";
  mask-image: linear-gradient(90deg, #000 0%, transparent 28%, transparent 72%, #000 100%);
  opacity: 0.55;
  pointer-events: none;
}

.engine-grid-bg {
  position: absolute;
  inset: 0;
  background-image: radial-gradient(circle, rgba(47, 107, 255, 0.09) 1px, transparent 1px);
  background-size: 24px 24px;
  opacity: 0.42;
}

.engine-title {
  position: absolute;
  top: 18px;
  left: 22px;
  z-index: 2;
  text-align: left;
}

.engine-title strong {
  display: block;
  color: var(--text);
  font-size: 14px;
  font-weight: 800;
}

.engine-title span {
  display: block;
  margin-top: 4px;
  color: var(--text-3);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.engine-live {
  position: absolute;
  top: 18px;
  right: 18px;
  z-index: 2;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: var(--text-3);
  font-family: "JetBrains Mono", Consolas, monospace;
  font-size: 10px;
  font-weight: 700;
}

.lissa-stage {
  position: absolute;
  inset: 56px 24px 96px;
  z-index: 1;
}

.lissa-trail-canvas {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
}

.lissa-logos {
  position: absolute;
  inset: 0;
  z-index: 2;
}

.lissa-chip {
  position: absolute;
  display: inline-flex;
  width: 52px;
  height: 52px;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid rgba(47, 107, 255, 0.18);
  border-radius: 50%;
  box-shadow: 0 8px 18px rgba(47, 107, 255, 0.14);
  cursor: pointer;
  transition: box-shadow 0.2s ease;
  will-change: transform;
}

.lissa-chip:hover {
  box-shadow: 0 14px 30px rgba(47, 107, 255, 0.32);
  z-index: 10;
}

.lissa-chip--small {
  width: 44px;
  height: 44px;
  border-color: rgba(123, 97, 255, 0.18);
}

.lissa-chip span {
  position: relative;
  display: inline-flex;
  width: 100%;
  height: 100%;
  align-items: center;
  justify-content: center;
}

.lissa-chip img {
  display: block;
  width: 95%;
  height: 95%;
  object-fit: contain;
  pointer-events: none;
}

.lissa-chip span::after {
  position: absolute;
  bottom: -28px;
  left: 50%;
  padding: 4px 8px;
  color: #fff;
  background: rgba(15, 23, 42, 0.86);
  border-radius: 6px;
  content: attr(data-tip);
  font-size: 10px;
  font-weight: 600;
  opacity: 0;
  pointer-events: none;
  transform: translateX(-50%);
  transition: opacity 0.15s ease;
  white-space: nowrap;
}

.lissa-chip:hover span::after {
  opacity: 1;
}

.engine-core {
  position: absolute;
  top: calc(50% - 30px);
  left: 50%;
  display: flex;
  width: 140px;
  height: 140px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background:
    radial-gradient(circle at 35% 28%, rgba(255, 255, 255, 0.98), rgba(238, 244, 255, 0.9) 54%, rgba(47, 107, 255, 0.12)),
    linear-gradient(135deg, rgba(47, 107, 255, 0.08), rgba(123, 97, 255, 0.08));
  border: 1px solid rgba(47, 107, 255, 0.22);
  border-radius: 50%;
  box-shadow:
    0 18px 42px rgba(47, 107, 255, 0.18),
    inset 0 2px 14px rgba(255, 255, 255, 0.82);
  pointer-events: none;
  transform: translate(-50%, -50%);
  z-index: 5;
}

.engine-core-ring {
  position: absolute;
  inset: 0;
  z-index: 1;
  width: 100%;
  height: 100%;
  pointer-events: none;
}

.engine-core-ring--outer {
  animation: orbitRotate 14s linear infinite;
}

.engine-core-ring--inner {
  animation: orbitRotateReverse 18s linear infinite;
}

.engine-core span {
  position: relative;
  z-index: 2;
  color: var(--primary);
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.18em;
}

.engine-core strong {
  position: relative;
  z-index: 2;
  margin-top: 4px;
  background: linear-gradient(135deg, var(--primary), var(--purple));
  background-clip: text;
  color: transparent;
  font-family: "JetBrains Mono", Consolas, monospace;
  font-size: 24px;
  font-weight: 900;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.engine-core em {
  position: relative;
  z-index: 2;
  margin-top: 3px;
  color: var(--primary);
  font-size: 10px;
  font-style: normal;
  font-weight: 700;
}

.engine-flow {
  position: absolute;
  right: 34px;
  bottom: 18px;
  left: 34px;
  z-index: 3;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 28px;
}

.engine-flow::before {
  position: absolute;
  top: 16px;
  right: 36px;
  left: 36px;
  height: 1px;
  background:
    linear-gradient(90deg, transparent, rgba(47, 107, 255, 0.34) 9%, rgba(123, 97, 255, 0.42) 50%, rgba(77, 212, 172, 0.38) 91%, transparent),
    linear-gradient(90deg, rgba(47, 107, 255, 0.16), rgba(77, 212, 172, 0.16));
  content: "";
  opacity: 1;
}

.engine-flow-item {
  position: relative;
  z-index: 1;
  display: grid;
  justify-items: center;
  gap: 4px;
  min-width: 0;
}

.engine-flow-item::after {
  position: absolute;
  top: 14px;
  left: calc(50% + 22px);
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--primary);
  content: "";
  opacity: 0;
  animation: flowPellet 2.6s ease-in-out infinite;
}

.engine-flow-item:nth-child(2)::after {
  animation-delay: 1.1s;
}

.engine-flow-item:last-child::after {
  display: none;
}

.engine-flow-item div {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--text-2);
  font-size: 10px;
  font-weight: 700;
}

.engine-flow-item span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--primary);
  border: 2px solid rgba(255, 255, 255, 0.94);
  box-shadow: 0 0 0 4px rgba(47, 107, 255, 0.1);
}

.engine-flow-item.is-purple span {
  background: var(--purple);
  box-shadow: 0 0 0 4px rgba(123, 97, 255, 0.1);
}

.engine-flow-item.is-teal span {
  background: var(--teal);
  box-shadow: 0 0 0 4px rgba(77, 212, 172, 0.12);
}

.engine-flow-item em {
  overflow: hidden;
  font-style: normal;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.engine-flow-item strong {
  color: var(--text);
  font-family: "JetBrains Mono", Consolas, monospace;
  font-size: 12px;
  font-weight: 800;
}

.ai-exposure-matrix {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.ai-exposure-list {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 10px;
}

.ai-exposure-card {
  position: relative;
  display: flex;
  flex: 1;
  min-height: 96px;
  flex-direction: column;
  justify-content: space-between;
  padding: 12px 14px;
  overflow: hidden;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 12px;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s ease;
}

.ai-exposure-card:hover {
  border-color: rgba(47, 107, 255, 0.36);
  box-shadow: 0 10px 22px rgba(47, 107, 255, 0.08);
  transform: translateY(-1px);
}

.ai-exposure-card.highlighted {
  background: linear-gradient(135deg, var(--primary-softer) 0%, #fff 70%);
  border-color: rgba(47, 107, 255, 0.22);
}

.ai-exposure-card.highlighted::before {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  width: 3px;
  background: linear-gradient(180deg, var(--primary), var(--purple));
  content: "";
}

.ai-exposure-top,
.ai-exposure-identity,
.ai-exposure-metric,
.ai-exposure-total {
  display: flex;
  align-items: center;
}

.ai-exposure-top,
.ai-exposure-metric,
.ai-exposure-total {
  justify-content: space-between;
  gap: 10px;
}

.ai-exposure-identity {
  min-width: 0;
  gap: 9px;
}

.ai-exposure-identity i {
  display: inline-flex;
  width: 26px;
  height: 26px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  font-size: 11px;
  font-style: normal;
  font-weight: 800;
}

.ai-exposure-identity div {
  min-width: 0;
}

.ai-exposure-identity strong {
  display: block;
  overflow: hidden;
  color: var(--text);
  font-size: 12px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-exposure-identity span {
  display: block;
  margin-top: 2px;
  color: var(--text-3);
  font-size: 9px;
}

.ai-exposure-metric strong {
  color: var(--text);
  font-family: "JetBrains Mono", Consolas, monospace;
  font-size: 18px;
  font-weight: 900;
}

.ai-exposure-metric span {
  color: var(--text-3);
  font-family: "JetBrains Mono", Consolas, monospace;
  font-size: 10px;
  font-weight: 700;
}

.ai-exposure-track {
  height: 6px;
  overflow: hidden;
  background: var(--line);
  border-radius: 999px;
}

.ai-exposure-track span {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, var(--primary), var(--purple));
  border-radius: inherit;
}

.ai-exposure-total {
  margin-top: 10px;
  padding-top: 11px;
  border-top: 1px dashed var(--line-2);
}

.ai-exposure-total span {
  color: var(--text-3);
  font-size: 10px;
}

.ai-exposure-total strong {
  color: var(--text);
  font-family: "JetBrains Mono", Consolas, monospace;
  font-size: 14px;
  font-weight: 900;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 18px;
}

.metric-card {
  position: relative;
  overflow: hidden;
  padding: 20px;
}

.metric-card::before {
  position: absolute;
  top: 0;
  right: 0;
  left: 0;
  height: 3px;
  content: "";
}

.metric-card--blue::before { background: var(--primary); }
.metric-card--purple::before { background: var(--purple); }
.metric-card--teal::before { background: var(--teal); }
.metric-card--orange::before { background: var(--orange); }

.metric-topline,
.panel-heading,
.trend-summary,
.filter-header,
.table-toolbar,
.table-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.metric-icon {
  display: inline-flex;
  width: 40px;
  height: 40px;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
}

.metric-icon :deep(svg) {
  width: 20px;
  height: 20px;
  fill: none;
  stroke: currentColor;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.metric-card--blue .metric-icon { color: var(--primary); background: var(--primary-soft); }
.metric-card--purple .metric-icon { color: var(--purple); background: var(--purple-soft); }
.metric-card--teal .metric-icon { color: #13a985; background: var(--teal-soft); }
.metric-card--orange .metric-icon { color: var(--orange); background: var(--orange-soft); }

.metric-trend {
  color: #10b981;
  font-size: 12px;
  font-weight: 600;
}

.metric-trend.down {
  color: #ef4444;
}

.metric-label {
  display: block;
  margin-top: 16px;
  font-size: 12px;
}

.metric-card strong {
  display: block;
  margin-top: 4px;
  color: var(--text);
  font-size: 28px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.metric-card small {
  display: block;
  margin-top: 4px;
  font-size: 11px;
}

.insight-grid {
  display: grid;
  grid-template-columns: minmax(320px, 1fr) minmax(0, 2fr);
  gap: 18px;
  margin-bottom: 18px;
}

.panel {
  padding: 22px;
}

.panel-heading {
  margin-bottom: 18px;
}

.panel-heading h2,
.table-toolbar h2 {
  margin: 0;
  color: var(--text);
  font-size: 16px;
  font-weight: 700;
}

.live-badge {
  display: inline-flex;
  align-items: center;
  height: 26px;
  padding: 0 10px;
  color: var(--primary);
  background: var(--primary-soft);
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
}

.keyword-stage {
  position: relative;
  height: 248px;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 12px;
  background:
    linear-gradient(180deg, #ffffff 0%, transparent 16%, transparent 84%, #ffffff 100%),
    radial-gradient(circle at 50% 45%, rgba(47, 107, 255, 0.1), transparent 48%);
}

.cloud-tag {
  position: absolute;
  top: 50%;
  left: 50%;
  color: var(--text-2);
  font-weight: 600;
  pointer-events: none;
  white-space: nowrap;
  will-change: opacity, transform;
}

.keyword-footer {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid var(--line);
  color: var(--text-3);
  font-size: 12px;
}

.keyword-footer span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.keyword-footer i,
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--primary);
}

.keyword-footer i.purple,
.dot.purple {
  background: var(--purple);
}

.keyword-footer strong {
  margin-left: auto;
  color: var(--text-2);
  font-weight: 600;
}

.trend-summary {
  justify-content: flex-start;
  padding-bottom: 18px;
  margin-bottom: 18px;
  border-bottom: 1px dashed var(--line-2);
}

.trend-summary article {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 156px;
}

.trend-summary article:last-child {
  margin-left: auto;
  display: block;
  text-align: right;
}

.dot.blue {
  background: #7db7ff;
}

.dot.purple {
  background: #b39cff;
}

.trend-summary strong {
  display: block;
  margin-top: 2px;
  color: var(--text);
  font-size: 20px;
  font-weight: 700;
}

.trend-summary em {
  color: var(--text-3);
  font-size: 12px;
  font-style: normal;
  font-weight: 400;
}

.line-chart {
  height: 260px;
  padding: 4px 0 0;
}

.line-chart svg {
  display: block;
  width: 100%;
  height: 226px;
  overflow: visible;
}

.chart-grid line {
  stroke: rgba(226, 232, 240, 0.9);
  stroke-dasharray: 5 7;
  stroke-width: 1;
}

.line-area {
  pointer-events: none;
}

.line-area-create {
  fill: url("#createTrendFill");
}

.line-area-publish {
  fill: url("#publishTrendFill");
}

.trend-line {
  fill: none;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 3;
  filter: drop-shadow(0 6px 10px rgba(125, 151, 255, 0.12));
}

.trend-line-create {
  stroke: #7db7ff;
}

.trend-line-publish {
  stroke: #b39cff;
}

.trend-dots circle {
  stroke: #fff;
  stroke-width: 2.5;
}

.create-dot {
  fill: #7db7ff;
}

.publish-dot {
  fill: #b39cff;
}

.line-chart-axis {
  display: grid;
  grid-template-columns: repeat(8, minmax(48px, 1fr));
  padding: 0 4px;
}

.line-chart-axis small {
  color: var(--text-3);
  font-size: 11px;
  text-align: center;
}

.report-table-panel {
  padding: 0;
  overflow: hidden;
}

.report-tabs {
  display: flex;
  gap: 28px;
  padding: 10px 24px 0;
  border-bottom: 1px solid var(--line);
}

.report-tab {
  position: relative;
  padding: 10px 0 12px;
  color: var(--text-2);
  background: transparent;
  font-weight: 600;
}

.report-tab.active {
  color: var(--primary);
}

.report-tab.active::after {
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  height: 2px;
  background: var(--primary);
  border-radius: 2px;
  content: "";
}

.report-alert {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin: 20px 24px 0;
  padding: 12px 14px;
  color: #b36f00;
  background: var(--orange-soft);
  border: 1px solid rgba(255, 159, 28, 0.22);
  border-radius: 12px;
  font-size: 12px;
  line-height: 1.7;
}

.report-alert span {
  display: inline-flex;
  width: 16px;
  height: 16px;
  align-items: center;
  justify-content: center;
  margin-top: 2px;
  color: var(--orange);
  border: 1px solid currentColor;
  border-radius: 50%;
  font-size: 11px;
  font-weight: 700;
}

.filter-block {
  padding: 22px 24px 20px;
}

.filter-header {
  margin-bottom: 12px;
}

.filter-header strong {
  font-size: 13px;
}

.platform-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 18px;
}

.platform-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 12px;
  color: var(--text-2);
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 10px;
  font-size: 12px;
  transition: all 0.15s ease;
}

.platform-chip.active {
  color: #fff;
  background: linear-gradient(135deg, var(--primary) 0%, #4a7dff 100%);
  border-color: var(--primary);
  box-shadow: 0 2px 8px rgba(47, 107, 255, 0.25);
}

.chip-mark {
  display: inline-flex;
  width: 17px;
  height: 17px;
  align-items: center;
  justify-content: center;
  background: var(--primary-soft);
  border-radius: 5px;
  color: var(--primary);
  font-size: 10px;
  font-weight: 700;
}

.chip-mark img {
  border-radius: 4px;
}

.platform-chip.active .chip-mark {
  color: #fff;
  background: rgba(255, 255, 255, 0.86);
}

.platform-chip em {
  opacity: 0.8;
  font-style: normal;
  font-variant-numeric: tabular-nums;
}

.filter-form {
  display: grid;
  grid-template-columns: 360px 200px 200px 64px;
  gap: 12px;
  align-items: end;
  justify-content: start;
}

.filter-form label {
  display: grid;
  min-width: 0;
  gap: 7px;
}

.filter-form label span {
  color: var(--text-3);
  font-size: 12px;
}

.filter-form input {
  box-sizing: border-box;
  width: 100%;
  height: 40px;
  padding: 0 12px;
  color: var(--text);
  background: #fff;
  border: 1px solid var(--line-2);
  border-radius: 10px;
  outline: none;
}

.filter-form input:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(47, 107, 255, 0.1);
}

.filter-form .primary-button {
  height: 40px;
}

.ghost-button,
.primary-button {
  display: inline-flex;
  height: 36px;
  align-items: center;
  justify-content: center;
  padding: 0 14px;
  border-radius: 10px;
  font-weight: 600;
  white-space: nowrap;
}

.ghost-button {
  color: var(--text-2);
  background: #fff;
  border: 1px solid var(--line-2);
}

.primary-button {
  color: #fff;
  background: linear-gradient(135deg, var(--primary) 0%, #4a7dff 100%);
  box-shadow: 0 2px 8px rgba(47, 107, 255, 0.25);
}

.table-toolbar {
  padding: 18px 24px;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
}

.table-toolbar span {
  display: block;
  margin-top: 4px;
  font-size: 12px;
}

.toolbar-actions {
  display: flex;
  gap: 10px;
}

.table-wrap {
  overflow-x: auto;
}

table {
  width: 100%;
  min-width: 980px;
  border-collapse: collapse;
}

th {
  padding: 14px 16px;
  color: var(--text-2);
  background: #fafbfd;
  border-bottom: 1px solid var(--line);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-align: left;
}

td {
  height: 58px;
  padding: 0 16px;
  border-bottom: 1px solid var(--line);
  color: var(--text-2);
  font-size: 13px;
  vertical-align: middle;
}

.empty-table-cell {
  height: 96px;
  color: var(--text-3);
  text-align: center;
}

tbody tr:hover {
  background: var(--primary-softer);
}

.muted {
  color: var(--text-3);
}

.question-cell,
.platform-cell {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.question-cell strong {
  color: var(--text);
  font-weight: 600;
}

.hot-icon {
  display: inline-flex;
  width: 18px;
  height: 18px;
  align-items: center;
  justify-content: center;
  color: var(--orange);
  background: var(--orange-soft);
  border-radius: 6px;
  font-size: 15px;
  line-height: 1;
}

.platform-cell i {
  display: inline-flex;
  width: 22px;
  height: 22px;
  align-items: center;
  justify-content: center;
  color: var(--primary);
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 6px;
  font-style: normal;
  font-size: 11px;
  font-weight: 700;
}

.platform-cell i img {
  width: 16px;
  height: 16px;
}

.type-pill,
.status-pill {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 9px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.type-pill {
  color: var(--primary-deep);
  background: var(--primary-soft);
}

.status-pill.success {
  color: #059669;
  background: #ecfdf5;
}

.status-pill.warning {
  color: #b36f00;
  background: var(--orange-soft);
}

.link-button {
  padding: 0;
  color: var(--primary);
  background: transparent;
  font-weight: 600;
}

.table-footer {
  padding: 16px 24px;
}

.table-footer strong {
  color: var(--text);
  font-weight: 700;
}

.pager {
  display: flex;
  gap: 5px;
}

.pager button {
  width: 32px;
  height: 32px;
  color: var(--text-2);
  background: #fff;
  border: 1px solid var(--line-2);
  border-radius: 9px;
}

.pager button.active {
  color: #fff;
  background: var(--primary);
  border-color: var(--primary);
}

@keyframes statusPulse {
  0%, 100% {
    opacity: 0.45;
    transform: scale(0.9);
  }
  50% {
    opacity: 1;
    transform: scale(1.22);
  }
}

@keyframes orbitRotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

@keyframes orbitRotateReverse {
  from { transform: rotate(0deg); }
  to { transform: rotate(-360deg); }
}

@keyframes flowPellet {
  0% {
    opacity: 0;
    transform: translateX(0);
  }
  18% {
    opacity: 1;
  }
  82% {
    opacity: 1;
  }
  100% {
    opacity: 0;
    transform: translateX(var(--pellet-dist, 200px));
  }
}

@media (max-width: 1180px) {
  .report-hero,
  .cockpit-grid,
  .insight-grid {
    grid-template-columns: 1fr;
  }

  .hero-meta,
  .overview-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .ai-exposure-matrix {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
  }

  .ai-exposure-matrix .cockpit-section-head,
  .ai-exposure-list,
  .ai-exposure-total {
    grid-column: 1 / -1;
  }

  .ai-exposure-list {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .postsale-report-page {
    margin: -12px;
    padding: 12px;
  }

  .hero-meta,
  .overview-grid,
  .cockpit-kpis,
  .filter-form {
    grid-template-columns: 1fr;
  }

  .data-cockpit {
    padding: 16px;
  }

  .cockpit-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .cockpit-title {
    flex-wrap: wrap;
  }

  .visibility-engine {
    min-height: 380px;
  }

  .lissa-stage {
    inset: 56px 16px 96px;
  }

  .engine-core {
    width: 120px;
    height: 120px;
  }

  .trend-summary,
  .table-toolbar,
  .table-footer {
    align-items: flex-start;
    flex-direction: column;
  }

  .trend-summary article:last-child {
    margin-left: 0;
    text-align: left;
  }

  .bar-chart {
    grid-template-columns: repeat(4, minmax(48px, 1fr));
    height: auto;
  }

  .report-tabs {
    overflow-x: auto;
  }
}
</style>
