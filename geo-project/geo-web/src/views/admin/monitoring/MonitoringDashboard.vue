<template>
  <div class="monitoring-page">
    <el-card shadow="never" class="topbar-card">
      <div class="topbar">
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
            <span class="chip chip-muted">总计 {{ taskStat.total }}</span>
            <span class="chip chip-run">运行中 {{ taskStat.running }}</span>
            <span class="chip chip-fail">失败 {{ taskStat.failed }}</span>
            <span class="chip chip-dead">死信 {{ taskStat.dead }}</span>
          </div>
        </div>
        <div class="mb-3 table-toolbar">
          <el-input v-model="taskQuery.keyword" placeholder="项目名称关键字" clearable style="width: 220px" @keyup.enter="loadTasks" />
          <el-select v-model="taskQuery.taskType" clearable placeholder="任务类型" style="width: 180px" @change="loadTasks">
            <el-option label="双日跑批" value="BI_DAILY_POLL" />
            <el-option label="品牌标准表达生成" value="BRAND_STATEMENT_GENERATION" />
            <el-option label="售前诊断" value="PRESALE_DIAGNOSIS" />
            <el-option label="问题场景内容建议" value="QUESTION_STRATEGY_GENERATION" />
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
          <el-table :data="tasks" border>
            <el-table-column prop="projectName" label="项目名称" min-width="160" />
            <el-table-column label="任务类型" min-width="150">
              <template #default="scope">{{ taskTypeLabel(scope.row.taskType) }}</template>
            </el-table-column>
            <el-table-column prop="priorityLevel" label="优先级" width="90">
              <template #default="scope">P{{ scope.row.priorityLevel }}</template>
            </el-table-column>
            <el-table-column prop="platformCode" label="平台编码" width="140" />
            <el-table-column label="执行通道" width="130">
              <template #default="scope">{{ channelLabel(scope.row.currentChannel) }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="120">
              <template #default="scope">
                <el-tag :type="taskStatusTag(scope.row.status)">{{ taskStatusLabel(scope.row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="retryCount" label="重试次数" width="100" />
            <el-table-column label="耗时" width="120">
              <template #default="scope">{{ taskDuration(scope.row) }}</template>
            </el-table-column>
            <el-table-column label="最近错误" min-width="280">
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
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import DataState from '@/components/ui/DataState.vue'
import {
  getDispatchDashboard,
  getDispatchTasks,
  replayDispatchTask,
  type DispatchRangeParams,
  type DispatchTaskQuery,
} from '@/api/dispatch'
import type { DispatchDashboardMetrics, DispatchTaskItem } from '@/types'

const activeTab = ref<'dashboard' | 'tasks'>('tasks')
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
  runningTaskCount: 0,
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

function taskTypeLabel(taskType?: string) {
  const map: Record<string, string> = {
    BI_DAILY_POLL: '双日跑批',
    BRAND_STATEMENT_GENERATION: '品牌标准表达生成',
    PRESALE_DIAGNOSIS: '售前诊断',
    QUESTION_STRATEGY_GENERATION: '问题场景内容建议',
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

function taskStatusTag(status: string): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'completed') return 'success'
  if (status === 'running') return 'warning'
  if (status === 'failed' || status === 'dead_letter') return 'danger'
  return 'info'
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
  await replayDispatchTask(taskId)
  ElMessage.success('任务已重新入队')
  await loadTasks()
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
  min-width: 320px;
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

.table-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
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
