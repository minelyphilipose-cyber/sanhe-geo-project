<template>
  <div class="partner-page partner-home-page">
    <div class="partner-page-header">
      <div>
        <div class="partner-page-kicker">合伙人工作台</div>
        <h1 class="partner-page-title">经营概览</h1>
        <div class="partner-page-subtitle">聚合客户、项目、积分与待处理事项，帮助负责人快速判断当前交付节奏。</div>
      </div>
      <div class="partner-page-actions">
        <el-button @click="reloadAll">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
    </div>

    <div class="partner-home-metrics">
      <div
        v-for="card in statCards"
        :key="card.key"
        class="partner-home-metric"
        :style="{ '--metric-accent': card.color }"
        @click="card.onClick()"
      >
        <div class="partner-home-metric__top">
          <span>{{ card.label }}</span>
          <el-icon :size="20">
            <component :is="card.icon" />
          </el-icon>
        </div>
        <div class="partner-home-metric__value">
          <span v-if="loading">-</span>
          <span v-else>{{ card.value }}</span>
        </div>
        <div v-if="card.sub" class="partner-home-metric__hint">{{ card.sub }}</div>
      </div>
    </div>

    <div class="partner-home-grid">
      <section class="partner-surface partner-chart-card">
        <div class="partner-section-head">
          <div>
            <h2>项目阶段分布</h2>
            <p>按项目当前阶段统计本账号可见项目。</p>
          </div>
        </div>
        <div v-if="!loading && stageData.length === 0" class="h-[260px] flex items-center justify-center">
          <el-empty description="暂无项目数据" :image-size="60" />
        </div>
        <v-chart v-else :option="stageChartOption" :loading="loading" autoresize style="height: 260px" />
      </section>

      <section class="partner-surface partner-chart-card">
        <div class="partner-section-head">
          <div>
            <h2>诊断报告趋势</h2>
            <p>观察近期 AI 可见度诊断报告生成数量。</p>
          </div>
          <el-radio-group v-model="trendDays" size="small" @change="loadReportTrend">
            <el-radio-button :value="7">7天</el-radio-button>
            <el-radio-button :value="15">15天</el-radio-button>
            <el-radio-button :value="30">30天</el-radio-button>
          </el-radio-group>
        </div>
        <v-chart :option="trendChartOption" :loading="loading" autoresize style="height: 240px" />
      </section>
    </div>

    <div class="partner-home-bottom">
      <section class="partner-surface partner-todo-card">
        <div class="partner-section-head">
          <div>
            <h2>待处理事项</h2>
            <p>优先处理套餐绑定、继续录入和资料确认事项。</p>
          </div>
          <el-tag v-if="pendingItems.length > 0" type="danger" size="small" round>
            {{ pendingItems.length }}
          </el-tag>
        </div>

        <el-empty v-if="!loading && pendingItems.length === 0" description="暂无待处理事项" :image-size="80" />

        <div v-else class="partner-todo-list">
          <div
            v-for="item in pendingItems"
            :key="`${item.type}-${item.targetId ?? 'none'}-${item.createdAt ?? ''}`"
            class="partner-todo-item"
            @click="navigateTo(item.targetPath)"
          >
            <div
              class="partner-todo-priority"
              :class="{
                'is-high': item.priority === 'high',
                'is-medium': item.priority === 'medium',
                'is-low': item.priority === 'low',
              }"
            />
            <el-icon :size="18" :color="pendingTypeColor(item.type)">
              <component :is="pendingTypeIcon(item.type)" />
            </el-icon>
            <div class="partner-todo-copy">
              <div class="partner-todo-title">{{ item.title }}</div>
              <div class="partner-todo-desc">{{ item.description }}</div>
            </div>
            <span class="partner-todo-time">{{ formatRelativeTime(item.createdAt) }}</span>
          </div>
        </div>
      </section>

      <section class="partner-surface partner-quick-card">
        <div class="partner-section-head">
          <div>
            <h2>快捷操作</h2>
            <p>进入最常用的客户、项目和积分页面。</p>
          </div>
        </div>
        <div class="partner-quick-actions">
          <el-button @click="router.push('/partner/my-customers')">
            <el-icon class="mr-1"><User /></el-icon>
            我的客户
          </el-button>
          <el-button @click="router.push('/partner/my-projects')">
            <el-icon class="mr-1"><Folder /></el-icon>
            我的项目
          </el-button>
          <el-button @click="router.push('/partner/balance')">
            <el-icon class="mr-1"><Wallet /></el-icon>
            余额与扣款
          </el-button>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart, LineChart } from 'echarts/charts'
import { LegendComponent, TooltipComponent, GridComponent } from 'echarts/components'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/zh-cn'
import {
  getDashboardOverview,
  getDashboardPendingItems,
  getDashboardReportTrend,
  getDashboardStageDistribution,
  type DashboardOverviewVO,
  type PendingItemVO,
  type ProjectStageDistributionVO,
  type ReportTrendVO,
} from '@/api/dashboard'
import { getPartnerAccount, type PartnerAccount } from '@/api/partner'
import { getCompanyList } from '@/api/customer'
import { useUserStore } from '@/stores/user'
import type { Company } from '@/types'

use([CanvasRenderer, PieChart, LineChart, LegendComponent, TooltipComponent, GridComponent])
dayjs.extend(relativeTime)
dayjs.locale('zh-cn')

const router = useRouter()
const userStore = useUserStore()

const loading = ref(true)
const trendDays = ref(30)
const account = ref<PartnerAccount | null>(null)
type PartnerTodoItem = PendingItemVO & { priority?: string }
const overviewData = ref<DashboardOverviewVO>({
  totalCustomers: 0,
  activeProjects: 0,
  totalProjects: 0,
  monthlyReports: 0,
  openAlerts: 0,
  totalPartners: null,
  monthlyNewCustomers: 0,
  highRiskProjects: 0,
  monthlyDiagnosisReports: 0,
})
const dashboardPendingItems = ref<PartnerTodoItem[]>([])
const workflowCompanies = ref<Company[]>([])
const stageData = ref<ProjectStageDistributionVO[]>([])
const trendData = ref<ReportTrendVO[]>([])

const workflowPendingItems = computed<PartnerTodoItem[]>(() =>
  workflowCompanies.value
    .filter((company) => ['package_requested', 'package_bound', 'entry_completed'].includes(effectiveWorkflowStatus(company)))
    .map((company) => {
      const status = effectiveWorkflowStatus(company)
      if (status === 'package_requested') {
        return {
          type: 'partner_package_request',
          targetId: company.id,
          targetPath: `/partner/customers/${company.id}`,
          title: `客户「${company.companyName}」待添加套餐`,
          description: '交付员工已提交客户和品牌资料，请进入客户详情绑定合伙人套餐。',
          priority: 'high',
          createdAt: company.partnerWorkflowUpdatedAt || company.createdAt,
        }
      }
      if (status === 'package_bound') {
        return {
          type: 'partner_entry_notify',
          targetId: company.id,
          targetPath: '/partner/my-customers',
          title: `客户「${company.companyName}」待通知继续录入`,
          description: '客户套餐已绑定，请通知交付员工继续录入项目与核心问题。',
          priority: 'medium',
          createdAt: company.partnerWorkflowUpdatedAt || company.createdAt,
        }
      }
      return {
        type: 'partner_entry_completed',
        targetId: company.id,
        targetPath: '/partner/my-projects',
        title: `客户「${company.companyName}」资料待确认`,
        description: '交付员工已完成项目与核心问题录入，请核对后提交总部工单。',
        priority: 'high',
        createdAt: company.partnerWorkflowUpdatedAt || company.createdAt,
      }
    }),
)

const pendingItems = computed(() => [...workflowPendingItems.value, ...dashboardPendingItems.value])

const statCards = computed(() => [
  {
    key: 'balance',
    label: '账户余额',
    value: Number(account.value?.currentBalance || 0).toFixed(2),
    icon: 'Wallet',
    color: '#0EA5E9',
    sub: '当前可用积分',
    onClick: () => router.push('/partner/balance'),
  },
  {
    key: 'customers',
    label: '我的客户',
    value: overviewData.value.totalCustomers ?? 0,
    icon: 'User',
    color: '#7C3AED',
    sub: `本月新签 ${overviewData.value.monthlyNewCustomers ?? 0} 家`,
    onClick: () => router.push('/partner/my-customers'),
  },
  {
    key: 'activeProjects',
    label: '活跃项目',
    value: overviewData.value.activeProjects ?? 0,
    icon: 'Folder',
    color: '#2563EB',
    sub: `共 ${overviewData.value.totalProjects ?? 0} 个项目`,
    onClick: () => router.push('/partner/my-projects'),
  },
  {
    key: 'monthlyDiagnosisReports',
    label: '本月诊断报告',
    value: overviewData.value.monthlyDiagnosisReports ?? overviewData.value.monthlyReports ?? 0,
    icon: 'DataAnalysis',
    color: '#10B981',
    sub: '本月已生成数量',
    onClick: () => router.push('/partner/presale/report'),
  },
])

const STAGE_COLORS: Record<string, string> = {
  pending_start: '#94A3B8',
  collecting_materials: '#60A5FA',
  baseline_diagnosis: '#38BDF8',
  executing: '#2563EB',
  biweekly_feedback: '#3B82F6',
  monthly_report: '#6366F1',
  quarterly_report: '#8B5CF6',
  needs_renewal: '#F59E0B',
  high_risk: '#EF4444',
  dispute_handling: '#DC2626',
  completed: '#6B7280',
}

const stageChartOption = computed(() => ({
  tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
  legend: { orient: 'vertical', right: 10, top: 'center', textStyle: { fontSize: 12 } },
  series: [
    {
      type: 'pie',
      radius: ['42%', '70%'],
      center: ['35%', '50%'],
      avoidLabelOverlap: true,
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
      label: { show: false },
      emphasis: { label: { show: true, fontWeight: 'bold' } },
      data: stageData.value.map((item) => ({
        name: item.label,
        value: item.count,
        itemStyle: { color: STAGE_COLORS[item.stage] || '#94A3B8' },
      })),
    },
  ],
}))

const trendChartOption = computed(() => ({
  tooltip: {
    trigger: 'axis',
    formatter: (params: Array<{ axisValue: string; data: number }>) => {
      const p = params[0]
      return `${p.axisValue}<br/>诊断报告生成: <b>${p.data}</b> 份`
    },
  },
  grid: { left: 40, right: 16, top: 16, bottom: 28 },
  xAxis: {
    type: 'category',
    data: trendData.value.map((item) => item.date.slice(5)),
    axisLabel: { fontSize: 11, interval: Math.max(Math.floor(trendData.value.length / 8) - 1, 0) },
    axisTick: { show: false },
  },
  yAxis: {
    type: 'value',
    minInterval: 1,
    axisLabel: { fontSize: 11 },
    splitLine: { lineStyle: { type: 'dashed', color: '#E5E7EB' } },
  },
  series: [
    {
      type: 'line',
      data: trendData.value.map((item) => item.count),
      smooth: true,
      symbol: 'circle',
      symbolSize: 4,
      lineStyle: { width: 2, color: '#2563EB' },
      itemStyle: { color: '#2563EB' },
      areaStyle: {
        color: {
          type: 'linear',
          x: 0,
          y: 0,
          x2: 0,
          y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(37, 99, 235, 0.15)' },
            { offset: 1, color: 'rgba(37, 99, 235, 0.01)' },
          ],
        },
      },
    },
  ],
}))

function pendingTypeIcon(type: string): string {
  const map: Record<string, string> = {
    report_review: 'DataAnalysis',
    high_risk_project: 'Warning',
    pending_renewal: 'Timer',
    partner_package_request: 'Wallet',
    partner_entry_notify: 'Bell',
    partner_entry_completed: 'FolderChecked',
  }
  return map[type] || 'InfoFilled'
}

function pendingTypeColor(type: string): string {
  const map: Record<string, string> = {
    report_review: '#2563EB',
    high_risk_project: '#EF4444',
    pending_renewal: '#8B5CF6',
    partner_package_request: '#F59E0B',
    partner_entry_notify: '#2563EB',
    partner_entry_completed: '#10B981',
  }
  return map[type] || '#6B7280'
}

function hasActivePackage(company: Company) {
  return Boolean(company.activePackageBindingId || company.activePackageName)
}

function effectiveWorkflowStatus(company: Company) {
  const status = String(company.partnerWorkflowStatus || 'draft')
  if (!hasActivePackage(company) && ['package_bound', 'project_entry', 'entry_completed'].includes(status)) {
    return 'package_requested'
  }
  return status
}

function formatRelativeTime(dateStr: string | null): string {
  if (!dateStr) return ''
  return dayjs(dateStr).fromNow()
}

function mapPartnerPath(path: string): string {
  if (!path) return '/partner/home'
  if (path.startsWith('/partner')) return path
  if (path.startsWith('/admin/projects')) return '/partner/my-projects'
  if (path.startsWith('/admin/customers')) return '/partner/my-customers'
  if (path.startsWith('/admin/reports')) return '/partner/my-projects'
  return '/partner/home'
}

function navigateTo(path: string) {
  router.push(mapPartnerPath(path))
}

async function loadOverview() {
  const { data } = await getDashboardOverview()
  overviewData.value = data.data || overviewData.value
}

async function loadPendingItems() {
  const { data } = await getDashboardPendingItems(15)
  dashboardPendingItems.value = (data.data || []).filter((item) => item.type !== 'system_alert')
}

async function loadWorkflowCompanies() {
  const { data } = await getCompanyList({ current: 1, size: 500 })
  workflowCompanies.value = data.data.records || []
}

async function loadStageDistribution() {
  const { data } = await getDashboardStageDistribution()
  stageData.value = data.data || []
}

async function loadReportTrend() {
  const { data } = await getDashboardReportTrend(trendDays.value, 'diagnosis')
  trendData.value = data.data || []
}

async function loadAccount() {
  const partnerId = userStore.userInfo?.partnerId
  if (!partnerId) {
    account.value = null
    return
  }
  const { data } = await getPartnerAccount(partnerId)
  account.value = data.data || null
}

async function reloadAll() {
  loading.value = true
  try {
    await Promise.all([
      loadAccount(),
      loadOverview(),
      loadPendingItems(),
      loadWorkflowCompanies(),
      loadStageDistribution(),
      loadReportTrend(),
    ])
  } finally {
    loading.value = false
  }
}

onMounted(reloadAll)
</script>

<style scoped>
.partner-home-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.partner-home-metric {
  --metric-accent: #2563eb;
  position: relative;
  min-height: 112px;
  overflow: hidden;
  padding: 16px;
  border: 1px solid color-mix(in srgb, var(--metric-accent) 18%, #e2e8f0);
  border-radius: 14px;
  background: linear-gradient(135deg, #fff 0%, #fff 56%, color-mix(in srgb, var(--metric-accent) 10%, #fff) 100%);
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.055);
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.partner-home-metric:hover {
  transform: translateY(-2px);
  box-shadow: 0 16px 32px rgba(15, 23, 42, 0.08);
}

.partner-home-metric::before {
  content: "";
  position: absolute;
  left: 0;
  top: 16px;
  bottom: 16px;
  width: 4px;
  border-radius: 0 999px 999px 0;
  background: var(--metric-accent);
}

.partner-home-metric__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #64748b;
  font-size: 13px;
  font-weight: 800;
}

.partner-home-metric__top .el-icon {
  color: var(--metric-accent);
}

.partner-home-metric__value {
  margin-top: 14px;
  color: #0f172a;
  font-size: 28px;
  font-weight: 900;
  line-height: 1;
}

.partner-home-metric__hint {
  margin-top: 8px;
  color: #94a3b8;
  font-size: 12px;
  font-weight: 700;
}

.partner-home-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.partner-chart-card,
.partner-todo-card,
.partner-quick-card {
  padding: 18px;
}

.partner-section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.partner-section-head h2 {
  margin: 0;
  color: #0f172a;
  font-size: 17px;
  font-weight: 900;
}

.partner-section-head p {
  margin: 5px 0 0;
  color: #64748b;
  font-size: 12px;
  font-weight: 600;
}

.partner-home-bottom {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(280px, 1fr);
  gap: 16px;
}

.partner-todo-list {
  display: flex;
  max-height: 330px;
  flex-direction: column;
  gap: 8px;
  overflow-y: auto;
}

.partner-todo-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 12px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: #f8fafc;
  cursor: pointer;
  transition: background 0.18s ease, border-color 0.18s ease;
}

.partner-todo-item:hover {
  border-color: #bfdbfe;
  background: #f3f8ff;
}

.partner-todo-priority {
  width: 4px;
  height: 34px;
  flex-shrink: 0;
  border-radius: 999px;
  background: #60a5fa;
}

.partner-todo-priority.is-high {
  background: #ef4444;
}

.partner-todo-priority.is-medium {
  background: #f59e0b;
}

.partner-todo-priority.is-low {
  background: #38bdf8;
}

.partner-todo-copy {
  min-width: 0;
  flex: 1;
}

.partner-todo-title {
  color: #0f172a;
  font-size: 13px;
  font-weight: 800;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.partner-todo-desc {
  margin-top: 3px;
  color: #64748b;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.partner-todo-time {
  flex-shrink: 0;
  color: #94a3b8;
  font-size: 12px;
  font-weight: 700;
}

.partner-quick-actions {
  display: grid;
  gap: 10px;
}

.partner-quick-actions .el-button {
  justify-content: flex-start;
  width: 100%;
  margin-left: 0;
}

@media (max-width: 1200px) {
  .partner-home-metrics,
  .partner-home-grid,
  .partner-home-bottom {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .partner-home-metrics,
  .partner-home-grid,
  .partner-home-bottom {
    grid-template-columns: 1fr;
  }
}
</style>
