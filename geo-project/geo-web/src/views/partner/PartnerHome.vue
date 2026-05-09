<template>
  <div>
    <h1 class="page-title">合伙人首页</h1>

    <div class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-4 mb-6">
      <div
        v-for="card in statCards"
        :key="card.key"
        class="glass-card-sm p-5 cursor-pointer hover:shadow-md transition-shadow"
        @click="card.onClick()"
      >
        <div class="flex items-center justify-between mb-2">
          <span class="text-sm text-gray-500">{{ card.label }}</span>
          <el-icon :size="20" :color="card.color">
            <component :is="card.icon" />
          </el-icon>
        </div>
        <div class="text-2xl font-bold" :style="{ color: card.color }">
          <span v-if="loading">-</span>
          <span v-else>{{ card.value }}</span>
        </div>
        <div v-if="card.sub" class="text-xs text-gray-400 mt-1">{{ card.sub }}</div>
      </div>
    </div>

    <div class="grid grid-cols-1 xl:grid-cols-2 gap-4 mb-6">
      <div class="glass-card-sm p-5">
        <h3 class="section-title">项目阶段分布</h3>
        <div v-if="!loading && stageData.length === 0" class="h-[260px] flex items-center justify-center">
          <el-empty description="暂无项目数据" :image-size="60" />
        </div>
        <v-chart v-else :option="stageChartOption" :loading="loading" autoresize style="height: 260px" />
      </div>

      <div class="glass-card-sm p-5">
        <div class="flex items-center justify-between mb-2">
          <h3 class="section-title mb-0">报表趋势</h3>
          <el-radio-group v-model="trendDays" size="small" @change="loadReportTrend">
            <el-radio-button :value="7">7天</el-radio-button>
            <el-radio-button :value="15">15天</el-radio-button>
            <el-radio-button :value="30">30天</el-radio-button>
          </el-radio-group>
        </div>
        <v-chart :option="trendChartOption" :loading="loading" autoresize style="height: 240px" />
      </div>
    </div>

    <div class="grid grid-cols-1 xl:grid-cols-3 gap-4">
      <div class="xl:col-span-2 glass-card-sm p-5">
        <div class="flex items-center justify-between mb-3">
          <h3 class="section-title mb-0">待处理事项</h3>
          <el-tag v-if="pendingItems.length > 0" type="danger" size="small" round>
            {{ pendingItems.length }}
          </el-tag>
        </div>

        <el-empty v-if="!loading && pendingItems.length === 0" description="暂无待处理事项" :image-size="80" />

        <div v-else class="flex flex-col gap-2 max-h-[320px] overflow-y-auto">
          <div
            v-for="item in pendingItems"
            :key="`${item.type}-${item.targetId ?? 'none'}-${item.createdAt ?? ''}`"
            class="flex items-center gap-3 px-3 py-2.5 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-800 cursor-pointer transition-colors"
            @click="navigateTo(item.targetPath)"
          >
            <div
              class="w-1 h-8 rounded-full shrink-0"
              :class="{
                'bg-red-500': item.priority === 'high',
                'bg-amber-500': item.priority === 'medium',
                'bg-blue-400': item.priority === 'low',
              }"
            />
            <el-icon :size="18" :color="pendingTypeColor(item.type)">
              <component :is="pendingTypeIcon(item.type)" />
            </el-icon>
            <div class="flex-1 min-w-0">
              <div class="text-sm font-medium text-gray-800 dark:text-gray-200 truncate">{{ item.title }}</div>
              <div class="text-xs text-gray-400 truncate">{{ item.description }}</div>
            </div>
            <span class="text-xs text-gray-400 shrink-0">{{ formatRelativeTime(item.createdAt) }}</span>
          </div>
        </div>
      </div>

      <div class="glass-card-sm p-5">
        <h3 class="section-title">快捷操作</h3>
        <div class="flex flex-col gap-2">
          <el-button class="w-full !ml-0" @click="router.push('/partner/my-customers')">
            <el-icon class="mr-1"><User /></el-icon>
            我的客户
          </el-button>
          <el-button class="w-full !ml-0" @click="router.push('/partner/my-projects')">
            <el-icon class="mr-1"><Folder /></el-icon>
            我的项目
          </el-button>
          <el-button class="w-full !ml-0" @click="router.push('/partner/balance')">
            <el-icon class="mr-1"><Wallet /></el-icon>
            余额与扣款
          </el-button>
          <el-button class="w-full !ml-0" @click="reloadAll">
            <el-icon class="mr-1"><Refresh /></el-icon>
            刷新数据
          </el-button>
        </div>
      </div>
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
import { useUserStore } from '@/stores/user'

use([CanvasRenderer, PieChart, LineChart, LegendComponent, TooltipComponent, GridComponent])
dayjs.extend(relativeTime)
dayjs.locale('zh-cn')

const router = useRouter()
const userStore = useUserStore()

const loading = ref(true)
const trendDays = ref(30)
const account = ref<PartnerAccount | null>(null)
const overviewData = ref<DashboardOverviewVO>({
  totalCustomers: 0,
  activeProjects: 0,
  totalProjects: 0,
  monthlyReports: 0,
  openAlerts: 0,
  totalPartners: null,
  monthlyNewCustomers: 0,
  highRiskProjects: 0,
})
const pendingItems = ref<PendingItemVO[]>([])
const stageData = ref<ProjectStageDistributionVO[]>([])
const trendData = ref<ReportTrendVO[]>([])

const statCards = computed(() => [
  {
    key: 'balance',
    label: '账户余额',
    value: Number(account.value?.currentBalance || 0).toFixed(2),
    icon: 'Wallet',
    color: '#0EA5E9',
    sub: '元',
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
    key: 'highRisk',
    label: '高风险项目',
    value: overviewData.value.highRiskProjects ?? 0,
    icon: 'Warning',
    color: (overviewData.value.highRiskProjects ?? 0) > 0 ? '#EF4444' : '#9CA3AF',
    sub: `本月报表 ${overviewData.value.monthlyReports ?? 0} 份`,
    onClick: () => router.push('/partner/my-projects'),
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
      return `${p.axisValue}<br/>报表生成: <b>${p.data}</b> 份`
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
  }
  return map[type] || 'InfoFilled'
}

function pendingTypeColor(type: string): string {
  const map: Record<string, string> = {
    report_review: '#2563EB',
    high_risk_project: '#EF4444',
    pending_renewal: '#8B5CF6',
  }
  return map[type] || '#6B7280'
}

function formatRelativeTime(dateStr: string | null): string {
  if (!dateStr) return ''
  return dayjs(dateStr).fromNow()
}

function mapPartnerPath(path: string): string {
  if (!path) return '/partner/home'
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
  pendingItems.value = (data.data || []).filter((item) => item.type !== 'system_alert')
}

async function loadStageDistribution() {
  const { data } = await getDashboardStageDistribution()
  stageData.value = data.data || []
}

async function loadReportTrend() {
  const { data } = await getDashboardReportTrend(trendDays.value)
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
      loadStageDistribution(),
      loadReportTrend(),
    ])
  } finally {
    loading.value = false
  }
}

onMounted(reloadAll)
</script>
