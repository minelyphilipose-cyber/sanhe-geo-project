<template>
  <div class="space-y-4">
    <el-page-header content="监测任务" @back="$router.back()" />

    <el-row :gutter="12">
      <el-col :span="6">
        <el-card shadow="never" class="metric-card metric-card-neutral">
          <div class="metric-title">任务总数</div>
          <div class="metric-value">{{ dashboard.dueTaskCount }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="metric-card metric-card-success">
          <div class="metric-title">已完成</div>
          <div class="metric-value">{{ dashboard.completedTaskCount }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="metric-card metric-card-warning">
          <div class="metric-title">运行中</div>
          <div class="metric-value">{{ dashboard.runningTaskCount }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="metric-card metric-card-danger">
          <div class="metric-title">失败 / 死信</div>
          <div class="metric-value">{{ dashboard.failedTaskCount }} / {{ dashboard.deadLetterPendingCount }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" v-loading="loading">
      <template #header>
        <div class="flex items-center justify-between flex-wrap gap-2">
          <span class="text-sm font-medium">项目监测任务</span>
          <div class="flex items-center gap-2 flex-wrap">
            <el-select v-model="filters.rangeType" style="width: 120px" size="small" @change="onFilterChange">
              <el-option label="今日" value="today" />
              <el-option label="近7天" value="last7" />
              <el-option label="近30天" value="last30" />
              <el-option label="自定义" value="custom" />
            </el-select>
            <el-date-picker
              v-if="filters.rangeType === 'custom'"
              v-model="filters.customRange"
              type="daterange"
              value-format="YYYY-MM-DD"
              range-separator="至"
              start-placeholder="开始"
              end-placeholder="结束"
              size="small"
              @change="onFilterChange"
            />
            <el-select v-model="filters.taskType" clearable placeholder="任务类型" size="small" style="width: 170px" @change="onFilterChange">
              <el-option label="问题池跑批" value="BI_DAILY_POLL" />
              <el-option label="双周报" value="BIWEEKLY_REPORT" />
              <el-option label="月报" value="MONTHLY_REPORT" />
              <el-option label="季报" value="QUARTERLY_REPORT" />
              <el-option label="内容生成" value="CONTENT_GENERATION" />
              <el-option label="品牌标准表达生成" value="BRAND_STATEMENT_GENERATION" />
            </el-select>
            <el-select v-model="filters.status" clearable placeholder="任务状态" size="small" style="width: 120px" @change="onFilterChange">
              <el-option label="排队中" value="pending" />
              <el-option label="运行中" value="running" />
              <el-option label="已完成" value="completed" />
              <el-option label="失败" value="failed" />
              <el-option label="死信" value="dead_letter" />
            </el-select>
            <el-button size="small" :loading="loading" @click="reloadAll">刷新</el-button>
          </div>
        </div>
      </template>

      <DataState :loading="loading" :empty="!loading && tasks.length === 0" empty-text="该项目暂无监测任务">
        <el-table :data="tasks" border>
          <el-table-column label="任务类型" min-width="150">
            <template #default="{ row }">{{ taskTypeLabel(row) }}</template>
          </el-table-column>
          <el-table-column prop="platformCode" label="平台" width="130" />
          <el-table-column label="执行通道" width="110">
            <template #default="{ row }">{{ channelLabel(row.currentChannel) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="retryCount" label="重试" width="70" align="center" />
          <el-table-column label="耗时" width="110">
            <template #default="{ row }">{{ taskDuration(row) }}</template>
          </el-table-column>
          <el-table-column prop="createdAt" label="创建时间" width="170" />
          <el-table-column label="最近错误" min-width="260">
            <template #default="{ row }">
              <el-tooltip v-if="row.lastError" :content="row.lastError" placement="top">
                <span class="text-xs text-gray-500">{{ shortText(row.lastError, 60) }}</span>
              </el-tooltip>
              <span v-else class="text-gray-400">-</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="canReplay && (row.status === 'failed' || row.status === 'dead_letter')"
                link
                type="primary"
                size="small"
                @click="handleReplay(row.id)"
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
          @current-change="onPageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import DataState from '@/components/ui/DataState.vue'
import { useUserStore } from '@/stores/user'
import { getDispatchDashboard, getDispatchTasks, replayDispatchTask, type DispatchTaskQuery } from '@/api/dispatch'
import type { DispatchDashboardMetrics, DispatchTaskItem } from '@/types'

const route = useRoute()
const userStore = useUserStore()
const projectId = computed(() => Number(route.params.id))
const canReplay = computed(() => userStore.hasPermission('dispatch.task.replay.dead_letter'))

const loading = ref(false)
const tasks = ref<DispatchTaskItem[]>([])
const taskPage = reactive({ current: 1, size: 20, total: 0 })
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

const filters = reactive({
  rangeType: 'last7' as 'today' | 'last7' | 'last30' | 'custom',
  customRange: [] as string[],
  taskType: '',
  status: '',
})

function buildParams(): DispatchTaskQuery {
  const params: DispatchTaskQuery = {
    projectId: projectId.value,
    rangeType: filters.rangeType,
    current: taskPage.current,
    size: taskPage.size,
    taskType: filters.taskType || undefined,
    status: filters.status || undefined,
  }
  if (filters.rangeType === 'custom') {
    params.startDate = filters.customRange?.[0]
    params.endDate = filters.customRange?.[1]
  }
  return params
}

async function loadDashboard() {
  const { data } = await getDispatchDashboard(buildParams())
  Object.assign(dashboard, data.data || {})
}

async function loadTasks() {
  const { data } = await getDispatchTasks(buildParams())
  tasks.value = data.data?.records || []
  taskPage.total = data.data?.total || 0
}

async function reloadAll() {
  if (!Number.isFinite(projectId.value) || projectId.value <= 0) {
    ElMessage.error('项目参数无效')
    return
  }
  if (filters.rangeType === 'custom' && (!filters.customRange?.[0] || !filters.customRange?.[1])) {
    ElMessage.warning('请选择完整的自定义日期范围')
    return
  }
  loading.value = true
  try {
    await Promise.all([loadDashboard(), loadTasks()])
  } finally {
    loading.value = false
  }
}

function onFilterChange() {
  taskPage.current = 1
  void reloadAll()
}

function onPageChange(page: number) {
  taskPage.current = page
  void reloadAll()
}

async function handleReplay(taskId: number) {
  try {
    await replayDispatchTask(taskId)
    ElMessage.success('任务已重新入队')
    await reloadAll()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '重放失败')
  }
}

function taskTypeLabel(task?: DispatchTaskItem | string) {
  const type = typeof task === 'string' ? task : task?.taskType
  if (typeof task !== 'string' && task?.taskDisplayName) return task.taskDisplayName
  const map: Record<string, string> = {
    BI_DAILY_POLL: '问题池跑批',
    BRAND_STATEMENT_GENERATION: '品牌标准表达生成',
    BIWEEKLY_REPORT: '双周报',
    MONTHLY_REPORT: '月报',
    QUARTERLY_REPORT: '季报',
    CONTENT_GENERATION: '内容生成',
  }
  return map[type || ''] || type || '-'
}

function statusLabel(status: string) {
  const map: Record<string, string> = {
    pending: '排队中',
    running: '运行中',
    completed: '已完成',
    failed: '失败',
    dead_letter: '死信',
  }
  return map[status] || status
}

function statusTagType(status: string): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'completed') return 'success'
  if (status === 'running') return 'warning'
  if (status === 'failed' || status === 'dead_letter') return 'danger'
  return 'info'
}

function channelLabel(channel?: string | null) {
  const map: Record<string, string> = {
    primary: '主通道',
    backup_key: '备用Key',
    backup_provider: '备用服务商',
  }
  return map[channel || ''] || channel || '-'
}

function taskDuration(task: DispatchTaskItem) {
  if (!task.finishedAt || !task.firstStartedAt) return '-'
  const ms = new Date(task.finishedAt).getTime() - new Date(task.firstStartedAt).getTime()
  if (ms <= 0 || !Number.isFinite(ms)) return '-'
  if (ms < 1000) return `${ms}ms`
  const s = Math.floor(ms / 1000)
  if (s < 60) return `${s}s`
  return `${Math.floor(s / 60)}m${s % 60}s`
}

function shortText(text?: string | null, len = 60) {
  if (!text) return '-'
  return text.length > len ? `${text.slice(0, len)}...` : text
}

onMounted(() => {
  void reloadAll()
})
</script>

<style scoped>
.metric-card { min-height: 90px; }
.metric-card :deep(.el-card__body) { padding: 14px; }
.metric-title { color: #6b7280; font-size: 13px; margin-bottom: 4px; }
.metric-value { font-size: 22px; font-weight: 600; }
.metric-card-success { background: linear-gradient(180deg, #f6fbf8 0%, #fff 100%); }
.metric-card-danger { background: linear-gradient(180deg, #fff7f7 0%, #fff 100%); }
.metric-card-warning { background: linear-gradient(180deg, #fffbf3 0%, #fff 100%); }
.metric-card-neutral { background: linear-gradient(180deg, #f8fafc 0%, #fff 100%); }
</style>
