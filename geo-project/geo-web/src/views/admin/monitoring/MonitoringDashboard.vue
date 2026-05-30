<template>
  <div class="monitoring-page admin-page">
    <div class="admin-page-header monitoring-header">
      <div>
        <div class="admin-page-kicker">监控中心</div>
        <h1 class="admin-page-title">调度监控</h1>
        <div class="admin-page-subtitle">追踪任务队列、执行通道与异常状态，及时发现阻塞和重试风险。</div>
      </div>
      <div class="admin-page-actions monitoring-header-actions">
        <span class="refresh-state" :class="{ 'is-active': autoRefresh }">
          <span class="refresh-dot" />
          {{ autoRefresh ? '自动刷新中' : '手动刷新' }}
        </span>
        <el-button :type="autoRefresh ? 'primary' : 'default'" plain @click="toggleAutoRefresh">
          60秒自动刷新 {{ autoRefresh ? 'ON' : 'OFF' }}
        </el-button>
        <el-button type="primary" :loading="loading" @click="reloadActiveTab">刷新</el-button>
      </div>
    </div>

    <el-card shadow="never" class="admin-surface monitoring-toolbar-card">
      <div class="monitoring-toolbar">
        <el-tabs v-model="activeTab" class="tabs compact-tabs" @tab-change="onTabChange">
          <el-tab-pane label="Dashboard 总览" name="dashboard" />
          <el-tab-pane label="任务监控" name="tasks" />
        </el-tabs>

        <div class="filters">
          <el-select v-model="filters.rangeType" style="width: 130px" @change="onRangeChange">
            <el-option label="今日" value="today" />
            <el-option label="7天" value="last7" />
            <el-option label="30天" value="last30" />
            <el-option label="自定义" value="custom" />
          </el-select>
          <el-date-picker
            v-if="filters.rangeType === 'custom'"
            v-model="filters.customRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            @change="reloadActiveTab"
          />
        </div>
      </div>
    </el-card>

    <div v-show="activeTab === 'dashboard'">
      <div class="admin-metric-grid monitoring-metric-grid">
        <div class="admin-metric-card" style="--metric-accent: #2563eb; --metric-tone: #eff6ff">
          <span class="admin-metric-label">进行中项目数</span>
          <strong class="admin-metric-value">{{ dashboard.activeProjectCount }}</strong>
          <span class="admin-metric-hint">当前仍在服务的项目</span>
        </div>
        <div class="admin-metric-card metric-with-progress" style="--metric-accent: #059669; --metric-tone: #ecfdf5">
          <span class="admin-metric-label">今日调度 / 已完成</span>
          <strong class="admin-metric-value">{{ dashboard.dueTaskCount }} / {{ dashboard.completedTaskCount }}</strong>
          <el-progress :percentage="dashboardProgress" :stroke-width="8" />
        </div>
        <div class="admin-metric-card" style="--metric-accent: #ef4444; --metric-tone: #fef2f2">
          <span class="admin-metric-label">失败 / 死信待处理</span>
          <strong class="admin-metric-value">{{ dashboard.failedTaskCount }} / {{ dashboard.deadLetterPendingCount }}</strong>
          <span class="admin-metric-hint">{{ dashboard.deadLetterPendingCount > 0 ? '存在待处理 dead_letter 任务' : '暂无死信积压' }}</span>
        </div>
        <div class="admin-metric-card" style="--metric-accent: #f59e0b; --metric-tone: #fffbeb">
          <span class="admin-metric-label">平台异常 / 平均耗时</span>
          <strong class="admin-metric-value">{{ dashboard.platformExceptionCount }} / {{ formatDurationMs(dashboard.avgTaskDurationMs) }}</strong>
          <span class="admin-metric-hint">按当前筛选周期统计</span>
        </div>
      </div>

      <div class="dashboard-insight-grid">
        <section class="dashboard-panel completion-panel">
          <div class="panel-head">
            <div>
              <div class="panel-kicker">执行完成度</div>
              <h3 class="panel-title">{{ dashboard.rangeLabel || rangeLabelText }}</h3>
            </div>
            <span class="panel-status" :class="dashboardRiskClass">{{ dashboardRiskText }}</span>
          </div>
          <div class="completion-body">
            <div class="completion-ring" :style="{ '--progress': `${dashboardProgress}%` }">
              <strong>{{ dashboardProgress }}%</strong>
              <span>完成率</span>
            </div>
            <div class="completion-list">
              <div class="completion-item">
                <span>应执行任务</span>
                <strong>{{ dashboard.dueTaskCount }}</strong>
              </div>
              <div class="completion-item">
                <span>已完成</span>
                <strong>{{ dashboard.completedTaskCount }}</strong>
              </div>
              <div class="completion-item">
                <span>运行中</span>
                <strong>{{ dashboard.runningTaskCount }}</strong>
              </div>
            </div>
          </div>
        </section>

        <section class="dashboard-panel risk-panel">
          <div class="panel-head">
            <div>
              <div class="panel-kicker">风险队列</div>
              <h3 class="panel-title">异常与积压</h3>
            </div>
            <strong class="panel-count">{{ unresolvedTaskCount }}</strong>
          </div>
          <div class="risk-list">
            <div class="risk-item is-danger">
              <span class="risk-dot"></span>
              <div>
                <strong>失败任务</strong>
                <span>已失败且等待处理或重放</span>
              </div>
              <b>{{ dashboard.failedTaskCount }}</b>
            </div>
            <div class="risk-item is-dead">
              <span class="risk-dot"></span>
              <div>
                <strong>死信任务</strong>
                <span>超时或重试耗尽，需要优先清理</span>
              </div>
              <b>{{ dashboard.deadLetterPendingCount }}</b>
            </div>
            <div class="risk-item is-warning">
              <span class="risk-dot"></span>
              <div>
                <strong>平台异常</strong>
                <span>平台调用异常或链路降级信号</span>
              </div>
              <b>{{ dashboard.platformExceptionCount }}</b>
            </div>
          </div>
        </section>

        <section class="dashboard-panel chain-panel">
          <div class="panel-head">
            <div>
              <div class="panel-kicker">链路效率</div>
              <h3 class="panel-title">任务吞吐与耗时</h3>
            </div>
            <span class="health-score">{{ dashboardHealthScore }}</span>
          </div>
          <div class="chain-grid">
            <div class="chain-item">
              <span>平均耗时</span>
              <strong>{{ formatDurationMs(dashboard.avgTaskDurationMs) }}</strong>
            </div>
            <div class="chain-item">
              <span>在服项目</span>
              <strong>{{ dashboard.activeProjectCount }}</strong>
            </div>
            <div class="chain-item">
              <span>待收敛任务</span>
              <strong>{{ pendingTaskCount }}</strong>
            </div>
          </div>
          <div class="chain-bar">
            <span :style="{ width: `${dashboardHealthScore}%` }"></span>
          </div>
          <div class="chain-note">健康度由完成率、失败、死信和平台异常综合折算，仅用于运营判断。</div>
        </section>

        <section class="dashboard-panel action-panel">
          <div class="panel-head">
            <div>
              <div class="panel-kicker">处理建议</div>
              <h3 class="panel-title">下一步动作</h3>
            </div>
          </div>
          <div class="action-list">
            <div v-for="item in dashboardActions" :key="item.title" class="action-item" :class="item.type">
              <span class="action-mark">{{ item.mark }}</span>
              <div>
                <strong>{{ item.title }}</strong>
                <span>{{ item.desc }}</span>
              </div>
            </div>
          </div>
        </section>
      </div>
    </div>

    <div v-show="activeTab === 'tasks'">
      <el-card shadow="never" class="admin-table-card monitoring-table-card">
        <div class="table-header">
          <div>
            <div class="table-title">任务监控</div>
            <div class="table-subtitle">按项目、通道和执行状态筛选调度任务。</div>
          </div>
          <div class="chips">
            <span class="chip chip-muted">总计 {{ taskStat.total }}</span>
            <span class="chip chip-run">运行中 {{ taskStat.running }}</span>
            <span class="chip chip-fail">失败 {{ taskStat.failed }}</span>
            <span class="chip chip-dead">死信 {{ taskStat.dead }}</span>
          </div>
        </div>
        <div class="mb-3 table-toolbar">
          <el-input v-model="taskQuery.keyword" placeholder="项目名称关键字" clearable style="width: 220px" @keyup.enter="loadTasks" />
          <el-select v-model="taskQuery.taskType" clearable placeholder="任务类型" style="width: 180px" @change="loadTasks">
            <el-option label="问题池跑批" value="BI_DAILY_POLL" />
            <el-option label="品牌标准表达生成" value="BRAND_STATEMENT_GENERATION" />
            <el-option label="内容生成" value="CONTENT_GENERATION" />
          </el-select>
          <el-select v-model="taskQuery.status" clearable placeholder="任务状态" style="width: 150px" @change="loadTasks">
            <el-option label="排队中" value="pending" />
            <el-option label="运行中" value="running" />
            <el-option label="已完成" value="completed" />
            <el-option label="失败" value="failed" />
            <el-option label="死信" value="dead_letter" />
          </el-select>
        </div>

        <DataState :loading="loading" :empty="!loading && tasks.length === 0" empty-text="暂无任务数据">
          <el-table :data="tasks" border table-layout="fixed">
            <el-table-column label="项目对象" min-width="220" show-overflow-tooltip>
              <template #default="scope">
                <div class="admin-entity-cell">
                  <div class="admin-entity-avatar task-avatar" :class="taskStatusClass(scope.row.status)">
                    {{ taskAvatarInitial(scope.row.projectName) }}
                  </div>
                  <div class="min-w-0">
                    <div class="admin-entity-main">{{ scope.row.projectName || '-' }}</div>
                    <div class="admin-entity-sub">{{ scope.row.taskNo || `任务 #${scope.row.id}` }}</div>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="任务类型" min-width="150">
              <template #default="scope">
                <span class="admin-mini-pill is-blue">{{ taskTypeLabel(scope.row) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="priorityLevel" label="优先级" width="90">
              <template #default="scope">
                <span class="priority-pill" :class="priorityClass(scope.row.priorityLevel)">P{{ scope.row.priorityLevel }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="platformCode" label="平台编码" width="140" />
            <el-table-column label="执行通道" width="130">
              <template #default="scope">{{ channelLabel(scope.row.currentChannel) }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="120">
              <template #default="scope">
                <span class="admin-status-tag" :class="taskStatusClass(scope.row.status)">
                  {{ taskStatusLabel(scope.row.status) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="retryCount" label="重试次数" width="100" />
            <el-table-column label="耗时" width="120">
              <template #default="scope">{{ taskDuration(scope.row) }}</template>
            </el-table-column>
            <el-table-column label="新增时间" width="170">
              <template #default="scope">{{ formatDateTime(scope.row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="更新时间" width="170">
              <template #default="scope">{{ formatDateTime(scope.row.updatedAt) }}</template>
            </el-table-column>
            <el-table-column label="最近错误" min-width="280">
              <template #default="scope">
                <el-tooltip v-if="scope.row.lastError" :content="scope.row.lastError" placement="top">
                  <span>{{ shortText(scope.row.lastError, 50) }}</span>
                </el-tooltip>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="scope">
                <el-button link type="primary" @click="openTaskDetail(scope.row.id)">详情</el-button>
                <el-button
                  v-if="canReplayDeadLetter && (scope.row.status === 'failed' || scope.row.status === 'dead_letter')"
                  link
                  type="primary"
                  @click="replay(scope.row.id)"
                >
                  重放
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </DataState>

        <div class="admin-table-footer">
          <el-pagination
            background
            layout="prev, pager, next, total"
            :current-page="taskPage.current"
            :page-size="taskPage.size"
            :total="taskPage.total"
            @current-change="onTaskPageChange"
          />
        </div>
      </el-card>
    </div>

    <el-dialog v-model="taskDetailVisible" title="调度详情" width="920px" class="admin-editor-dialog monitoring-detail-dialog">
      <div v-loading="taskDetailLoading" class="detail-loading-wrap">
        <div v-if="taskDetail" class="dispatch-detail">
          <section class="detail-hero">
            <div class="detail-hero-avatar" :class="taskStatusClass(taskDetail.status)">
              {{ taskAvatarInitial(taskDetail.projectName) }}
            </div>
            <div class="detail-hero-main">
              <div class="detail-task-no">{{ taskDetail.taskNo || `任务 #${taskDetail.id}` }}</div>
              <h2>{{ taskDetail.projectName || '-' }}</h2>
              <div class="detail-hero-meta">
                <span class="admin-mini-pill is-blue">{{ taskTypeLabel(taskDetail) }}</span>
                <span class="admin-status-tag" :class="taskStatusClass(taskDetail.status)">
                  {{ taskStatusLabel(taskDetail.status) }}
                </span>
                <span class="priority-pill" :class="priorityClass(taskDetail.priorityLevel)">P{{ taskDetail.priorityLevel }}</span>
              </div>
            </div>
          </section>

          <section class="detail-section">
            <div class="detail-section-title">基础信息</div>
            <div class="detail-info-grid">
              <div v-for="item in taskDetailBaseItems" :key="item.label" class="detail-info-item">
                <span>{{ item.label }}</span>
                <strong>{{ item.value }}</strong>
              </div>
            </div>
          </section>

          <section class="detail-section">
            <div class="detail-section-title">执行时间</div>
            <div class="detail-info-grid is-time">
              <div v-for="item in taskDetailTimeItems" :key="item.label" class="detail-info-item">
                <span>{{ item.label }}</span>
                <strong>{{ item.value }}</strong>
              </div>
            </div>
          </section>

          <section class="detail-section">
            <div class="detail-section-title">异常信息</div>
            <div class="detail-error-text">{{ taskDetail.lastError || '-' }}</div>
          </section>

          <section class="detail-section">
            <div class="detail-code-grid">
              <div class="detail-code-panel">
                <div class="detail-section-title">错误上下文</div>
                <pre class="detail-pre">{{ formatJsonBlock(taskDetail.errorContext) }}</pre>
              </div>
              <div class="detail-code-panel">
                <div class="detail-section-title">任务载荷</div>
                <pre class="detail-pre">{{ formatJsonBlock(taskDetail.payloadJson) }}</pre>
              </div>
            </div>
          </section>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import DataState from '@/components/ui/DataState.vue'
import {
  getDispatchDashboard,
  getDispatchTask,
  getDispatchTasks,
  replayDispatchTask,
  type DispatchRangeParams,
  type DispatchTaskQuery,
} from '@/api/dispatch'
import type { DispatchDashboardMetrics, DispatchTaskItem } from '@/types'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const activeTab = ref<'dashboard' | 'tasks'>('tasks')
const loading = ref(false)
const autoRefresh = ref(true)
let timer: number | null = null
const canReplayDeadLetter = computed(() => userStore.hasPermission('dispatch.task.replay.dead_letter'))

const filters = reactive({
  rangeType: 'today' as 'today' | 'last7' | 'last30' | 'custom',
  customRange: [] as string[],
})

const dashboard = reactive<DispatchDashboardMetrics>({
  activeProjectCount: 0,
  dueTaskCount: 0,
  runningTaskCount: 0,
  completedTaskCount: 0,
  failedTaskCount: 0,
  deadLetterPendingCount: 0,
  platformExceptionCount: 0,
  avgTaskDurationMs: 0,
  rangeLabel: '',
})

const tasks = ref<DispatchTaskItem[]>([])
const taskDetailVisible = ref(false)
const taskDetailLoading = ref(false)
const taskDetail = ref<DispatchTaskItem | null>(null)
const taskPage = reactive({ current: 1, size: 20, total: 0 })
const taskQuery = reactive({
  keyword: '',
  taskType: '',
  status: '',
})

interface DetailItem {
  label: string
  value: string
}

const taskDetailBaseItems = computed<DetailItem[]>(() => {
  const detail = taskDetail.value
  if (!detail) return []
  return [
    { label: '执行通道', value: channelLabel(detail.currentChannel) },
    { label: '平台编码', value: detail.platformCode || '-' },
    { label: '重试次数', value: `${detail.retryCount ?? 0} / ${detail.maxRetry ?? '-'}` },
    { label: '窗口开始', value: detail.windowStart || '-' },
    { label: '窗口结束', value: detail.windowEnd || '-' },
    { label: '当前状态', value: taskStatusLabel(detail.status) },
  ]
})

const taskDetailTimeItems = computed<DetailItem[]>(() => {
  const detail = taskDetail.value
  if (!detail) return []
  return [
    { label: '应执行时间', value: formatDateTime(detail.dueTime) },
    { label: '首次启动时间', value: formatDateTime(detail.firstStartedAt) },
    { label: '最近启动时间', value: formatDateTime(detail.lastStartedAt) },
    { label: '下次重试时间', value: formatDateTime(detail.nextRetryAt) },
    { label: '超时时间', value: formatDateTime(detail.timeoutAt) },
    { label: '完成时间', value: formatDateTime(detail.finishedAt) },
    { label: '新增时间', value: formatDateTime(detail.createdAt) },
    { label: '更新时间', value: formatDateTime(detail.updatedAt) },
  ]
})

const dashboardProgress = computed(() => {
  if (!dashboard.dueTaskCount) return 0
  return Math.min(100, Math.round((dashboard.completedTaskCount / dashboard.dueTaskCount) * 100))
})

const rangeLabelText = computed(() => {
  const map: Record<string, string> = {
    today: '今日调度',
    last7: '近 7 天调度',
    last30: '近 30 天调度',
    custom: '自定义周期调度',
  }
  return map[filters.rangeType] || '调度周期'
})

const unresolvedTaskCount = computed(() => (
  dashboard.failedTaskCount + dashboard.deadLetterPendingCount + dashboard.platformExceptionCount
))

const pendingTaskCount = computed(() => Math.max(
  dashboard.dueTaskCount - dashboard.completedTaskCount,
  0,
))

const dashboardRiskClass = computed(() => {
  if (dashboard.deadLetterPendingCount > 0 || dashboard.failedTaskCount > 0) return 'is-danger'
  if (dashboard.platformExceptionCount > 0 || dashboard.runningTaskCount > 0) return 'is-warning'
  return 'is-success'
})

const dashboardRiskText = computed(() => {
  if (dashboard.deadLetterPendingCount > 0) return '死信待处理'
  if (dashboard.failedTaskCount > 0) return '存在失败'
  if (dashboard.platformExceptionCount > 0) return '平台异常'
  if (dashboard.runningTaskCount > 0) return '执行中'
  return '运行平稳'
})

const dashboardHealthScore = computed(() => {
  const base = dashboard.dueTaskCount > 0 ? dashboardProgress.value : 100
  const riskPenalty = dashboard.failedTaskCount * 5
    + dashboard.deadLetterPendingCount * 12
    + dashboard.platformExceptionCount * 4
  return Math.max(0, Math.min(100, base - riskPenalty))
})

const dashboardActions = computed(() => {
  const actions: Array<{ type: string; mark: string; title: string; desc: string }> = []
  if (dashboard.deadLetterPendingCount > 0) {
    actions.push({
      type: 'is-danger',
      mark: '!',
      title: '优先清理死信任务',
      desc: '进入任务监控筛选死信状态，查看错误上下文后重放或人工处理。',
    })
  }
  if (dashboard.failedTaskCount > 0) {
    actions.push({
      type: 'is-danger',
      mark: 'F',
      title: '复核失败任务',
      desc: '确认是否已切备用链路，必要时按项目或平台批量排查失败原因。',
    })
  }
  if (dashboard.platformExceptionCount > 0) {
    actions.push({
      type: 'is-warning',
      mark: 'P',
      title: '检查平台健康',
      desc: '关注平台异常次数和额度阈值，避免异常平台继续影响调度成功率。',
    })
  }
  if (dashboard.runningTaskCount > 0) {
    actions.push({
      type: 'is-info',
      mark: 'R',
      title: '观察运行中任务',
      desc: '持续关注运行中任务耗时，若超出预期再进入详情查看超时窗口。',
    })
  }
  if (!actions.length) {
    actions.push({
      type: 'is-success',
      mark: 'OK',
      title: '当前无需人工介入',
      desc: '本周期未发现失败、死信或平台异常，可继续保持自动刷新观察。',
    })
  }
  return actions
})

const taskStat = computed(() => ({
  total: taskPage.total,
  running: tasks.value.filter((x) => x.status === 'running').length,
  failed: tasks.value.filter((x) => x.status === 'failed').length,
  dead: tasks.value.filter((x) => x.status === 'dead_letter').length,
}))

function buildRangeParams(): DispatchRangeParams {
  const params: DispatchRangeParams = { rangeType: filters.rangeType }
  if (filters.rangeType === 'custom') {
    params.startDate = filters.customRange?.[0]
    params.endDate = filters.customRange?.[1]
  }
  return params
}

function ensureCustomRange() {
  if (filters.rangeType !== 'custom') return true
  if (!filters.customRange?.[0] || !filters.customRange?.[1]) {
    ElMessage.warning('请选择完整的自定义日期范围')
    return false
  }
  return true
}

function shortText(text?: string | null, len = 50) {
  if (!text) return '-'
  return text.length > len ? `${text.slice(0, len)}...` : text
}

function formatDurationMs(ms?: number | null) {
  if (!ms || ms <= 0) return '-'
  if (ms < 1000) return `${ms}ms`
  const s = Math.floor(ms / 1000)
  if (s < 60) return `${s}s`
  const m = Math.floor(s / 60)
  const r = s % 60
  return `${m}m${r}s`
}

function taskDuration(task: DispatchTaskItem) {
  if (!task.finishedAt || !(task.firstStartedAt || task.createdAt)) return '-'
  const end = new Date(task.finishedAt).getTime()
  const start = new Date(task.firstStartedAt || task.createdAt).getTime()
  if (!Number.isFinite(end) || !Number.isFinite(start) || end <= start) return '-'
  return formatDurationMs(end - start)
}

function formatDateTime(value?: string | null) {
  if (!value) return '-'
  const date = new Date(value)
  if (!Number.isFinite(date.getTime())) return value
  const yyyy = date.getFullYear()
  const mm = `${date.getMonth() + 1}`.padStart(2, '0')
  const dd = `${date.getDate()}`.padStart(2, '0')
  const hh = `${date.getHours()}`.padStart(2, '0')
  const mi = `${date.getMinutes()}`.padStart(2, '0')
  const ss = `${date.getSeconds()}`.padStart(2, '0')
  return `${yyyy}-${mm}-${dd} ${hh}:${mi}:${ss}`
}

function formatJsonBlock(value?: string | null) {
  if (!value) return '-'
  const text = value.trim()
  if (!text) return '-'
  try {
    return JSON.stringify(JSON.parse(text), null, 2)
  } catch {
    return value
  }
}

function taskStatusLabel(status: string) {
  const map: Record<string, string> = {
    running: '运行中',
    failed: '失败（已切备用链路）',
    dead_letter: '死信（超时/重试耗尽）',
    completed: '已完成',
    pending: '排队中',
  }
  return map[status] || status
}

function taskTypeLabel(task?: DispatchTaskItem | string | null) {
  const taskType = typeof task === 'string' ? task : task?.taskType
  if (typeof task !== 'string' && task?.taskDisplayName) return task.taskDisplayName
  const map: Record<string, string> = {
    BI_DAILY_POLL: '问题池跑批',
    BRAND_STATEMENT_GENERATION: '品牌标准表达生成',
    CONTENT_GENERATION: '内容生成',
  }
  return map[taskType || ''] || taskType || '-'
}

function channelLabel(channel?: string | null) {
  const map: Record<string, string> = {
    primary: '主通道',
    backup_key: '备用Key',
    backup_provider: '备用服务商',
  }
  return map[channel || ''] || channel || '-'
}

function taskStatusClass(status: string) {
  if (status === 'completed') return 'is-success'
  if (status === 'running') return 'is-warning'
  if (status === 'failed' || status === 'dead_letter') return 'is-danger'
  return 'is-muted'
}

function priorityClass(priority?: number | null) {
  if (priority === 0) return 'is-critical'
  if (priority === 1) return 'is-high'
  return 'is-normal'
}

function taskAvatarInitial(value?: string | null) {
  const text = String(value || '').trim()
  return text ? Array.from(text)[0] : '任'
}

async function loadDashboard() {
  const { data } = await getDispatchDashboard(buildRangeParams())
  Object.assign(dashboard, data.data)
}

async function loadTasks() {
  const params: DispatchTaskQuery = {
    ...buildRangeParams(),
    current: taskPage.current,
    size: taskPage.size,
    keyword: taskQuery.keyword || undefined,
    taskType: taskQuery.taskType || undefined,
    status: taskQuery.status || undefined,
  }
  const { data } = await getDispatchTasks(params)
  tasks.value = data.data.records || []
  taskPage.total = data.data.total || 0
}

async function replay(taskId: number) {
  if (!canReplayDeadLetter.value) {
    ElMessage.warning('当前账号无死信任务重放权限')
    return
  }
  await replayDispatchTask(taskId)
  ElMessage.success('任务已重新入队')
  await loadTasks()
}

async function openTaskDetail(taskId: number) {
  taskDetailVisible.value = true
  taskDetailLoading.value = true
  taskDetail.value = null
  try {
    const { data } = await getDispatchTask(taskId)
    taskDetail.value = data.data
  } finally {
    taskDetailLoading.value = false
  }
}

async function reloadActiveTab() {
  if (!ensureCustomRange()) return
  loading.value = true
  try {
    if (activeTab.value === 'dashboard') await loadDashboard()
    if (activeTab.value === 'tasks') await loadTasks()
  } finally {
    loading.value = false
  }
}

function onTabChange() {
  reloadActiveTab()
}

function onRangeChange() {
  taskPage.current = 1
  reloadActiveTab()
}

function onTaskPageChange(v: number) {
  taskPage.current = v
  loadTasks()
}

function startTimer() {
  stopTimer()
  if (!autoRefresh.value) return
  timer = window.setInterval(() => {
    if (document.hidden) return
    reloadActiveTab()
  }, 60000)
}

function stopTimer() {
  if (!timer) return
  window.clearInterval(timer)
  timer = null
}

function toggleAutoRefresh() {
  autoRefresh.value = !autoRefresh.value
  startTimer()
}

onMounted(async () => {
  await reloadActiveTab()
  startTimer()
})

onBeforeUnmount(() => {
  stopTimer()
})
</script>

<style scoped>
.monitoring-header {
  align-items: center;
}

.monitoring-header-actions,
.monitoring-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
}

.refresh-state {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 32px;
  padding: 0 12px;
  border: 1px solid #e2e8f0;
  border-radius: 999px;
  background: #f8fafc;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

.refresh-state.is-active {
  border-color: #a7f3d0;
  background: #ecfdf5;
  color: #047857;
}

.refresh-dot {
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: #94a3b8;
  box-shadow: 0 0 0 3px rgba(148, 163, 184, 0.16);
}

.refresh-state.is-active .refresh-dot {
  background: #10b981;
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.16);
}

.monitoring-toolbar-card :deep(.el-card__body) {
  padding: 12px;
}

.tabs {
  flex: 1;
  min-width: 320px;
}

.filters {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.monitoring-metric-grid {
  margin-bottom: 0;
}

.metric-with-progress :deep(.el-progress) {
  position: relative;
  z-index: 1;
  margin-top: 10px;
}

.dashboard-insight-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.18fr) minmax(0, 0.92fr);
  gap: 14px;
}

.dashboard-panel {
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--admin-panel-border);
  border-radius: 14px;
  background:
    linear-gradient(180deg, #ffffff 0%, #ffffff 72%, #f8fafc 100%);
  box-shadow: 0 14px 32px rgba(15, 23, 42, 0.065);
}

.panel-head {
  min-height: 58px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 18px 13px;
  border-bottom: 1px solid var(--admin-panel-border-soft);
  background: linear-gradient(90deg, #f8fbff 0%, #ffffff 58%, #f0fdf4 100%);
}

.panel-kicker {
  color: #2563eb;
  font-size: 12px;
  font-weight: 800;
}

.panel-title {
  margin: 4px 0 0;
  color: var(--admin-text-strong);
  font-size: 16px;
  line-height: 1.35;
  font-weight: 800;
}

.panel-status,
.panel-count,
.health-score {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border-radius: 999px;
  font-weight: 800;
}

.panel-status {
  height: 28px;
  padding: 0 11px;
  background: #ecfdf5;
  color: #047857;
  font-size: 12px;
}

.panel-status.is-warning {
  background: #fffbeb;
  color: #b45309;
}

.panel-status.is-danger {
  background: #fef2f2;
  color: #b91c1c;
}

.panel-count {
  min-width: 36px;
  height: 36px;
  background: #fef2f2;
  color: #b91c1c;
  font-size: 18px;
}

.completion-body {
  display: grid;
  grid-template-columns: 190px minmax(0, 1fr);
  gap: 20px;
  align-items: center;
  padding: 24px;
}

.completion-ring {
  width: 156px;
  aspect-ratio: 1;
  display: grid;
  place-items: center;
  place-content: center;
  border-radius: 999px;
  background:
    radial-gradient(circle at center, #ffffff 0 57%, transparent 58%),
    conic-gradient(#059669 var(--progress), #e2e8f0 0);
  box-shadow:
    inset 0 0 0 1px rgba(226, 232, 240, 0.86),
    0 16px 34px rgba(15, 23, 42, 0.08);
}

.completion-ring strong {
  color: #0f172a;
  font-size: 30px;
  line-height: 1;
  font-weight: 800;
}

.completion-ring span {
  margin-top: 7px;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.completion-list {
  display: grid;
  gap: 10px;
}

.completion-item,
.chain-item {
  min-width: 0;
  border: 1px solid #e7edf5;
  border-radius: 12px;
  background:
    linear-gradient(135deg, #ffffff 0%, #fbfdff 64%, #f8fbff 100%);
  padding: 13px 14px;
}

.completion-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.completion-item span,
.chain-item span {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.completion-item strong,
.chain-item strong {
  color: #0f172a;
  font-size: 18px;
  font-weight: 800;
}

.risk-list,
.action-list {
  display: grid;
  gap: 10px;
  padding: 16px;
}

.risk-item,
.action-item {
  display: grid;
  align-items: center;
  min-width: 0;
  border: 1px solid #e7edf5;
  border-radius: 12px;
  background: #ffffff;
}

.risk-item {
  grid-template-columns: 10px minmax(0, 1fr) auto;
  gap: 11px;
  padding: 13px 14px;
}

.risk-item strong,
.action-item strong {
  display: block;
  color: #0f172a;
  font-size: 14px;
  font-weight: 800;
}

.risk-item span:not(.risk-dot),
.action-item span:not(.action-mark) {
  display: block;
  margin-top: 3px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.45;
}

.risk-item b {
  color: #0f172a;
  font-size: 18px;
}

.risk-dot {
  width: 9px;
  height: 9px;
  border-radius: 999px;
  background: #94a3b8;
}

.risk-item.is-danger .risk-dot,
.risk-item.is-dead .risk-dot {
  background: #ef4444;
  box-shadow: 0 0 0 4px rgba(239, 68, 68, 0.12);
}

.risk-item.is-warning .risk-dot {
  background: #f59e0b;
  box-shadow: 0 0 0 4px rgba(245, 158, 11, 0.14);
}

.chain-panel,
.action-panel {
  min-height: 250px;
}

.health-score {
  min-width: 42px;
  height: 42px;
  background: linear-gradient(135deg, #2563eb, #06b6d4);
  color: #ffffff;
  font-size: 18px;
  box-shadow: 0 12px 22px rgba(37, 99, 235, 0.18);
}

.chain-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  padding: 16px 16px 12px;
}

.chain-item strong {
  display: block;
  margin-top: 8px;
}

.chain-bar {
  height: 10px;
  margin: 2px 16px 12px;
  overflow: hidden;
  border-radius: 999px;
  background: #e2e8f0;
}

.chain-bar span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #2563eb, #10b981);
}

.chain-note {
  padding: 0 16px 16px;
  color: #94a3b8;
  font-size: 12px;
  line-height: 1.55;
}

.action-item {
  grid-template-columns: 38px minmax(0, 1fr);
  gap: 12px;
  padding: 13px 14px;
}

.action-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: #eff6ff;
  color: #2563eb;
  font-size: 12px;
  font-weight: 800;
}

.action-item.is-success .action-mark {
  background: #ecfdf5;
  color: #047857;
}

.action-item.is-warning .action-mark {
  background: #fffbeb;
  color: #b45309;
}

.action-item.is-danger .action-mark {
  background: #fef2f2;
  color: #b91c1c;
}

.monitoring-table-card :deep(.el-card__body) {
  padding: 0;
}

.monitoring-table-card {
  margin-top: 0;
}

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  padding: 16px 16px 12px;
  border-bottom: 1px solid var(--admin-panel-border-soft);
  background: linear-gradient(90deg, #f8fbff 0%, #ffffff 55%, #f0fdf4 100%);
}

.table-title {
  color: var(--admin-text-strong);
  font-size: 16px;
  font-weight: 800;
}

.table-subtitle {
  margin-top: 4px;
  color: var(--admin-text-muted);
  font-size: 12px;
}

.chips {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.chip {
  display: inline-flex;
  align-items: center;
  border-radius: 14px;
  padding: 3px 9px;
  font-size: 12px;
  font-weight: 700;
  border: 1px solid transparent;
}

.chip-muted {
  background: #f3f4f6;
  color: #6b7280;
}

.chip-run {
  background: #ecf5ff;
  color: #2563eb;
}

.chip-fail {
  background: #fef2f2;
  color: #dc2626;
}

.chip-dead {
  background: #fce7e7;
  color: #7f1d1d;
}

.table-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 12px 16px;
  margin-bottom: 0;
  border-bottom: 1px solid var(--admin-panel-border-soft);
  background: #ffffff;
}

.task-avatar.is-success {
  background: linear-gradient(135deg, #059669, #14b8a6);
}

.task-avatar.is-warning {
  background: linear-gradient(135deg, #d97706, #f59e0b);
}

.task-avatar.is-danger {
  background: linear-gradient(135deg, #dc2626, #ef4444);
}

.task-avatar.is-muted {
  background: linear-gradient(135deg, #64748b, #94a3b8);
}

.priority-pill {
  display: inline-flex;
  align-items: center;
  height: 23px;
  padding: 0 8px;
  border-radius: 999px;
  background: #f8fafc;
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.priority-pill.is-critical {
  background: #fef2f2;
  color: #b91c1c;
}

.priority-pill.is-high {
  background: #fffbeb;
  color: #b45309;
}

.priority-pill.is-normal {
  background: #eff6ff;
  color: #1d4ed8;
}

:deep(.compact-tabs .el-tabs__header) {
  margin: 0;
}

:deep(.compact-tabs .el-tabs__nav-wrap::after) {
  display: none;
}

:deep(.compact-tabs .el-tabs__item) {
  height: 32px;
  line-height: 32px;
  padding: 0 14px;
}

.monitoring-detail-dialog :deep(.el-dialog__body) {
  padding: 0;
  background: #f8fafc;
}

.detail-loading-wrap {
  min-height: 220px;
}

.dispatch-detail {
  display: grid;
  gap: 14px;
  padding: 18px;
}

.detail-hero,
.detail-section {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background: #ffffff;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.055);
}

.detail-hero {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px;
  background:
    linear-gradient(135deg, #ffffff 0%, #f8fbff 58%, #ecfdf5 100%);
}

.detail-hero-avatar {
  width: 54px;
  height: 54px;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border-radius: 14px;
  background: linear-gradient(135deg, #64748b, #94a3b8);
  color: #fff;
  font-size: 20px;
  font-weight: 800;
  box-shadow: 0 12px 22px rgba(15, 23, 42, 0.16);
}

.detail-hero-avatar.is-success {
  background: linear-gradient(135deg, #059669, #14b8a6);
}

.detail-hero-avatar.is-warning {
  background: linear-gradient(135deg, #d97706, #f59e0b);
}

.detail-hero-avatar.is-danger {
  background: linear-gradient(135deg, #dc2626, #ef4444);
}

.detail-hero-main {
  min-width: 0;
}

.detail-task-no {
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.detail-hero h2 {
  margin: 4px 0 9px;
  color: #0f172a;
  font-size: 19px;
  line-height: 1.45;
  font-weight: 800;
}

.detail-hero-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.detail-section {
  padding: 14px;
}

.detail-section-title {
  margin-bottom: 10px;
  color: #0f172a;
  font-size: 14px;
  font-weight: 800;
}

.detail-info-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.detail-info-grid.is-time {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.detail-info-item {
  min-width: 0;
  min-height: 66px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 7px;
  border: 1px solid #e7edf5;
  border-radius: 10px;
  background: linear-gradient(135deg, #ffffff 0%, #fbfdff 64%, #f8fbff 100%);
  padding: 10px 12px;
}

.detail-info-item span {
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.detail-info-item strong {
  min-width: 0;
  color: #0f172a;
  font-size: 13px;
  line-height: 1.45;
  font-weight: 700;
  overflow-wrap: anywhere;
}

.detail-error-text {
  border: 1px solid #fecaca;
  border-radius: 10px;
  background: #fef2f2;
  padding: 12px 14px;
  color: #991b1b;
  line-height: 1.65;
  overflow-wrap: anywhere;
}

.detail-code-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.detail-code-panel {
  min-width: 0;
}

.detail-pre {
  margin: 0;
  max-height: 260px;
  overflow: auto;
  padding: 12px 14px;
  border: 1px solid #dbeafe;
  border-radius: 10px;
  background: #f8fbff;
  color: #334155;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  line-height: 1.65;
}

@media (max-width: 768px) {
  .monitoring-header,
  .monitoring-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .dashboard-insight-grid,
  .completion-body,
  .chain-grid,
  .detail-info-grid,
  .detail-info-grid.is-time,
  .detail-code-grid {
    grid-template-columns: 1fr;
  }

  .completion-ring {
    margin-inline: auto;
  }

  .tabs {
    min-width: 0;
  }

  .filters {
    align-items: stretch;
  }

  .dispatch-detail {
    padding: 12px;
  }

  .detail-hero {
    align-items: flex-start;
  }
}
</style>
