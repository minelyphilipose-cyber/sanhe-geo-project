<template>
  <div class="monitoring-page">
    <el-card shadow="never" class="topbar-card">
      <div class="topbar">
        <el-tabs v-model="activeTab" class="tabs compact-tabs" @tab-change="onTabChange">
          <el-tab-pane label="Dashboard 总览" name="dashboard" />
          <el-tab-pane label="任务监控" name="tasks" />
          <el-tab-pane label="平台健康" name="platforms" />
          <el-tab-pane label="告警列表" name="alerts" />
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
          <el-button :type="autoRefresh ? 'primary' : 'default'" plain @click="toggleAutoRefresh">
            60秒自动刷新 {{ autoRefresh ? 'ON' : 'OFF' }}
          </el-button>
          <el-button :loading="loading" @click="reloadActiveTab">刷新</el-button>
        </div>
      </div>
    </el-card>

    <div v-show="activeTab === 'dashboard'">
      <el-row :gutter="12" class="metrics-row">
        <el-col :xs="24" :sm="12" :md="6">
          <el-card shadow="never" class="metric-card metric-card-neutral">
            <div class="metric-title">进行中项目数</div>
            <div class="metric-value">{{ dashboard.activeProjectCount }}</div>
          </el-card>
        </el-col>
        <el-col :xs="24" :sm="12" :md="6">
          <el-card shadow="never" class="metric-card metric-card-success">
            <div class="metric-title">今日调度 / 已完成</div>
            <div class="metric-value">{{ dashboard.dueTaskCount }} / {{ dashboard.completedTaskCount }}</div>
            <el-progress :percentage="dashboardProgress" :stroke-width="8" />
          </el-card>
        </el-col>
        <el-col :xs="24" :sm="12" :md="6">
          <el-card shadow="never" class="metric-card metric-card-danger">
            <div class="metric-title">失败 / 死信待处理</div>
            <div class="metric-value">{{ dashboard.failedTaskCount }} / {{ dashboard.deadLetterPendingCount }}</div>
            <div class="metric-tip" v-if="dashboard.deadLetterPendingCount > 0">存在待处理 dead_letter 任务</div>
          </el-card>
        </el-col>
        <el-col :xs="24" :sm="12" :md="6">
          <el-card shadow="never" class="metric-card metric-card-warning">
            <div class="metric-title">平台异常 / 平均耗时</div>
            <div class="metric-value">{{ dashboard.platformExceptionCount }} / {{ formatDurationMs(dashboard.avgTaskDurationMs) }}</div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <div v-show="activeTab === 'tasks'">
      <el-card shadow="never">
        <div class="table-header">
          <div class="table-title">任务监控</div>
          <div class="chips">
            <span class="chip chip-muted">All {{ taskStat.total }}</span>
            <span class="chip chip-run">Running {{ taskStat.running }}</span>
            <span class="chip chip-fail">Failed {{ taskStat.failed }}</span>
            <span class="chip chip-dead">Dead {{ taskStat.dead }}</span>
          </div>
        </div>
        <div class="mb-3 table-toolbar">
          <el-input v-model="taskQuery.keyword" placeholder="项目名称关键字" clearable style="width: 220px" @keyup.enter="loadTasks" />
          <el-select v-model="taskQuery.taskType" clearable placeholder="任务类型" style="width: 180px" @change="loadTasks">
            <el-option label="双日跑批" value="BI_DAILY_POLL" />
            <el-option label="品牌标准表达生成" value="BRAND_STATEMENT_GENERATION" />
            <el-option label="双周报" value="BIWEEKLY_REPORT" />
            <el-option label="月报" value="MONTHLY_REPORT" />
            <el-option label="季报" value="QUARTERLY_REPORT" />
            <el-option label="售前诊断" value="PRESALE_DIAGNOSIS" />
            <el-option label="问题场景内容建议" value="QUESTION_STRATEGY_GENERATION" />
            <el-option label="内容生成" value="CONTENT_GENERATION" />
          </el-select>
          <el-select v-model="taskQuery.status" clearable placeholder="任务状态" style="width: 150px" @change="loadTasks">
            <el-option label="pending" value="pending" />
            <el-option label="running" value="running" />
            <el-option label="completed" value="completed" />
            <el-option label="failed" value="failed" />
            <el-option label="dead_letter" value="dead_letter" />
          </el-select>
        </div>

        <DataState :loading="loading" :empty="!loading && tasks.length === 0" empty-text="暂无任务数据">
          <el-table :data="tasks" border>
            <el-table-column prop="projectName" label="projectName" min-width="160" />
            <el-table-column prop="taskType" label="taskType" min-width="150" />
            <el-table-column prop="priorityLevel" label="priority" width="90">
              <template #default="scope">P{{ scope.row.priorityLevel }}</template>
            </el-table-column>
            <el-table-column prop="platformCode" label="platformCode" width="140" />
            <el-table-column prop="currentChannel" label="currentChannel" width="130" />
            <el-table-column prop="status" label="status" width="120">
              <template #default="scope">
                <el-tag :type="taskStatusTag(scope.row.status)">{{ taskStatusLabel(scope.row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="retryCount" label="retryCount" width="100" />
            <el-table-column label="duration" width="120">
              <template #default="scope">{{ taskDuration(scope.row) }}</template>
            </el-table-column>
            <el-table-column label="lastError" min-width="280">
              <template #default="scope">
                <el-tooltip v-if="scope.row.lastError" :content="scope.row.lastError" placement="top">
                  <span>{{ shortText(scope.row.lastError, 50) }}</span>
                </el-tooltip>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="scope">
                <el-button
                  v-if="scope.row.status === 'failed' || scope.row.status === 'dead_letter'"
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

        <div class="mt-3 flex justify-end">
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

    <div v-show="activeTab === 'platforms'">
      <div class="section-title">平台健康</div>
      <el-row :gutter="12">
        <el-col v-for="item in platforms" :key="item.id" :xs="24" :sm="12" :md="8" :lg="6" class="mb-3">
          <el-card shadow="never" class="platform-card" :class="platformCardClass(item)">
            <div class="platform-head">
              <div>
                <div class="platform-name">
                  <span class="health-dot" :class="platformDotClass(item)"></span>
                  {{ item.platformName }}
                </div>
                <div class="platform-sub">{{ item.platformCode }} · {{ item.priorityLevel }}</div>
              </div>
              <el-tag :type="platformTagType(item)">{{ platformStatusText(item) }}</el-tag>
            </div>
            <div class="platform-line">RPM 上限：{{ item.rpmLimit || 0 }}</div>
            <div class="platform-line">异常次数：{{ item.exceptionCount || 0 }}</div>
            <el-progress :percentage="platformPercent(item)" :status="platformProgressStatus(item)" />
            <div v-if="item.degradedReason" class="platform-risk">{{ item.degradedReason }}</div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <div v-show="activeTab === 'alerts'">
      <el-card shadow="never">
        <div class="table-header">
          <div class="table-title">告警列表</div>
          <div class="chips">
            <span class="chip chip-critical">critical {{ alertStat.critical }}</span>
            <span class="chip chip-error">error {{ alertStat.error }}</span>
            <span class="chip chip-warn">warn {{ alertStat.warn }}</span>
            <span class="chip chip-info">info {{ alertStat.info }}</span>
          </div>
        </div>
        <div class="mb-3 table-toolbar">
          <el-select v-model="alertQuery.severity" clearable placeholder="严重级别" style="width: 130px" @change="loadAlerts">
            <el-option label="info" value="info" />
            <el-option label="warn" value="warn" />
            <el-option label="error" value="error" />
            <el-option label="critical" value="critical" />
          </el-select>
          <el-select v-model="alertQuery.status" clearable placeholder="处理状态" style="width: 130px" @change="loadAlerts">
            <el-option label="open" value="open" />
            <el-option label="resolved" value="resolved" />
          </el-select>
        </div>
        <DataState :loading="loading" :empty="!loading && alerts.length === 0" empty-text="暂无告警数据">
          <el-table :data="alerts" border>
            <el-table-column prop="createdAt" label="时间" min-width="170" />
            <el-table-column prop="severity" label="级别" width="100">
              <template #default="scope">
                <span class="sev-dot" :class="`sev-${scope.row.severity}`"></span>
                {{ scope.row.severity }}
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="110" />
            <el-table-column prop="projectName" label="项目" min-width="140" />
            <el-table-column prop="title" label="摘要" min-width="220" />
            <el-table-column label="关联信息" min-width="220">
              <template #default="scope">
                taskId={{ scope.row.taskId || '-' }} | retry={{ scope.row.retryCount }}
              </template>
            </el-table-column>
            <el-table-column label="处理" width="110" fixed="right">
              <template #default="scope">
                <el-button
                  link
                  type="primary"
                  :disabled="scope.row.status !== 'open' || !canResolveAlert"
                  @click="resolve(scope.row)"
                >
                  Resolve
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </DataState>

        <div class="mt-3 flex justify-end">
          <el-pagination
            background
            layout="prev, pager, next, total"
            :current-page="alertPage.current"
            :page-size="alertPage.size"
            :total="alertPage.total"
            @current-change="onAlertPageChange"
          />
        </div>
        <div class="alert-footer">
          <span>保留策略：info 30天 · warn 90天 · error 365天 · critical 永久</span>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import DataState from '@/components/ui/DataState.vue'
import { useUserStore } from '@/stores/user'
import {
  getDispatchAlerts,
  getDispatchDashboard,
  getDispatchPlatforms,
  getDispatchTasks,
  replayDispatchTask,
  resolveDispatchAlert,
  type DispatchAlertQuery,
  type DispatchRangeParams,
  type DispatchTaskQuery,
} from '@/api/dispatch'
import type { DispatchAlertItem, DispatchDashboardMetrics, DispatchPlatformHealthItem, DispatchTaskItem } from '@/types'

const userStore = useUserStore()
const canResolveAlert = userStore.hasPermission('dispatch.alert.resolve')

const activeTab = ref<'dashboard' | 'tasks' | 'platforms' | 'alerts'>('dashboard')
const loading = ref(false)
const autoRefresh = ref(true)
let timer: number | null = null

const filters = reactive({
  rangeType: 'today' as 'today' | 'last7' | 'last30' | 'custom',
  customRange: [] as string[],
})

const dashboard = reactive<DispatchDashboardMetrics>({
  activeProjectCount: 0,
  dueTaskCount: 0,
  completedTaskCount: 0,
  failedTaskCount: 0,
  deadLetterPendingCount: 0,
  platformExceptionCount: 0,
  avgTaskDurationMs: 0,
  rangeLabel: '',
})

const tasks = ref<DispatchTaskItem[]>([])
const taskPage = reactive({ current: 1, size: 20, total: 0 })
const taskQuery = reactive({
  keyword: '',
  taskType: '',
  status: '',
})

const platforms = ref<DispatchPlatformHealthItem[]>([])

const alerts = ref<DispatchAlertItem[]>([])
const alertPage = reactive({ current: 1, size: 20, total: 0 })
const alertQuery = reactive({
  severity: '',
  status: 'open',
})

const dashboardProgress = computed(() => {
  if (!dashboard.dueTaskCount) return 0
  return Math.min(100, Math.round((dashboard.completedTaskCount / dashboard.dueTaskCount) * 100))
})

const taskStat = computed(() => ({
  total: taskPage.total,
  running: tasks.value.filter((x) => x.status === 'running').length,
  failed: tasks.value.filter((x) => x.status === 'failed').length,
  dead: tasks.value.filter((x) => x.status === 'dead_letter').length,
}))

const alertStat = computed(() => ({
  critical: alerts.value.filter((x) => x.severity === 'critical').length,
  error: alerts.value.filter((x) => x.severity === 'error').length,
  warn: alerts.value.filter((x) => x.severity === 'warn').length,
  info: alerts.value.filter((x) => x.severity === 'info').length,
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
  if (!task.finishedAt || !task.createdAt) return '-'
  const end = new Date(task.finishedAt).getTime()
  const start = new Date(task.createdAt).getTime()
  if (!Number.isFinite(end) || !Number.isFinite(start) || end <= start) return '-'
  return formatDurationMs(end - start)
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

function taskStatusTag(status: string): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'completed') return 'success'
  if (status === 'running') return 'warning'
  if (status === 'failed' || status === 'dead_letter') return 'danger'
  return 'info'
}

function platformPercent(item: DispatchPlatformHealthItem) {
  const limit = item.rpmLimit || 0
  if (limit <= 0) return 0
  return Math.min(100, Math.round(((item.exceptionCount || 0) / limit) * 100))
}

function platformStatusText(item: DispatchPlatformHealthItem) {
  if (item.degraded) return '已降级'
  const p = platformPercent(item)
  if (p >= 80) return '接近阈值'
  return '正常'
}

function platformTagType(item: DispatchPlatformHealthItem): 'success' | 'warning' | 'danger' | 'info' {
  if (item.degraded) return 'danger'
  const p = platformPercent(item)
  if (p >= 80) return 'warning'
  return 'success'
}

function platformProgressStatus(item: DispatchPlatformHealthItem): '' | 'success' | 'warning' | 'exception' {
  if (item.degraded) return 'exception'
  const p = platformPercent(item)
  if (p >= 80) return 'warning'
  return 'success'
}

function platformCardClass(item: DispatchPlatformHealthItem) {
  if (item.degraded) return 'platform-card-danger'
  const p = platformPercent(item)
  if (p >= 80) return 'platform-card-warning'
  return 'platform-card-success'
}

function platformDotClass(item: DispatchPlatformHealthItem) {
  if (item.degraded) return 'dot-red'
  const p = platformPercent(item)
  if (p >= 80) return 'dot-yellow'
  return 'dot-green'
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

async function loadPlatforms() {
  const { data } = await getDispatchPlatforms(buildRangeParams())
  platforms.value = data.data || []
}

async function loadAlerts() {
  const params: DispatchAlertQuery = {
    ...buildRangeParams(),
    current: alertPage.current,
    size: alertPage.size,
    severity: alertQuery.severity || undefined,
    status: alertQuery.status || undefined,
  }
  const { data } = await getDispatchAlerts(params)
  alerts.value = data.data.records || []
  alertPage.total = data.data.total || 0
}

async function replay(taskId: number) {
  await replayDispatchTask(taskId)
  ElMessage.success('任务已重新入队')
  await loadTasks()
}

async function resolve(row: DispatchAlertItem) {
  if (!canResolveAlert) {
    ElMessage.warning('当前账号无告警处理权限')
    return
  }
  const { value } = await ElMessageBox.prompt('请输入处理备注（可选）', '告警处理', {
    inputPlaceholder: '例如：平台恢复，已重放',
    confirmButtonText: '确认',
    cancelButtonText: '取消',
  }).catch(() => ({ value: '' }))
  await resolveDispatchAlert(row.id, value?.trim() || undefined)
  ElMessage.success('告警已处理')
  await loadAlerts()
}

async function reloadActiveTab() {
  if (!ensureCustomRange()) return
  loading.value = true
  try {
    if (activeTab.value === 'dashboard') await loadDashboard()
    if (activeTab.value === 'tasks') await loadTasks()
    if (activeTab.value === 'platforms') await loadPlatforms()
    if (activeTab.value === 'alerts') await loadAlerts()
  } finally {
    loading.value = false
  }
}

function onTabChange() {
  reloadActiveTab()
}

function onRangeChange() {
  taskPage.current = 1
  alertPage.current = 1
  reloadActiveTab()
}

function onTaskPageChange(v: number) {
  taskPage.current = v
  loadTasks()
}

function onAlertPageChange(v: number) {
  alertPage.current = v
  loadAlerts()
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
.monitoring-page {
  padding: 6px 0;
}

.topbar-card {
  margin-bottom: 12px;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.tabs {
  flex: 1;
  min-width: 420px;
}

.filters {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.metrics-row {
  margin-bottom: 12px;
}

.metric-card {
  min-height: 126px;
  border: 1px solid var(--el-border-color-lighter);
}

.metric-title {
  color: #6b7280;
  font-size: 13px;
  margin-bottom: 6px;
}

.metric-value {
  font-size: 24px;
  font-weight: 600;
  margin-bottom: 8px;
}

.metric-card-success {
  background: linear-gradient(180deg, #f6fbf8 0%, #ffffff 100%);
}

.metric-card-danger {
  background: linear-gradient(180deg, #fff7f7 0%, #ffffff 100%);
}

.metric-card-warning {
  background: linear-gradient(180deg, #fffbf3 0%, #ffffff 100%);
}

.metric-card-neutral {
  background: linear-gradient(180deg, #f8fafc 0%, #ffffff 100%);
}

.metric-tip {
  font-size: 12px;
  color: #ef4444;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  margin: 0 0 10px;
}

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.table-title {
  font-size: 15px;
  font-weight: 600;
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
  padding: 2px 8px;
  font-size: 12px;
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

.chip-critical {
  background: #fef2f2;
  color: #dc2626;
}

.chip-error {
  background: #fff7ed;
  color: #ea580c;
}

.chip-warn {
  background: #fffbeb;
  color: #b45309;
}

.chip-info {
  background: #f3f4f6;
  color: #6b7280;
}

.table-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.platform-card {
  min-height: 156px;
  border: 1px solid var(--el-border-color-lighter);
}

.platform-card-success {
  border-color: #b7ebc6;
}

.platform-card-warning {
  border-color: #f8d08a;
}

.platform-card-danger {
  border-color: #f2b1b1;
}

.platform-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 8px;
}

.platform-name {
  font-size: 15px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
}

.platform-sub {
  margin-top: 2px;
  color: #6b7280;
  font-size: 12px;
}

.platform-line {
  color: #374151;
  font-size: 13px;
  margin-bottom: 6px;
}

.platform-risk {
  margin-top: 6px;
  color: #dc2626;
  font-size: 12px;
}

.health-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}

.dot-green {
  background: #65a30d;
}

.dot-yellow {
  background: #d97706;
}

.dot-red {
  background: #ef4444;
}

.sev-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 6px;
}

.sev-critical {
  background: #ef4444;
}

.sev-error {
  background: #f97316;
}

.sev-warn {
  background: #f59e0b;
}

.sev-info {
  background: #9ca3af;
}

.alert-footer {
  margin-top: 10px;
  font-size: 12px;
  color: #6b7280;
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
</style>
