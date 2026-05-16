<template>
  <div class="alert-center-page admin-page">
    <div class="admin-page-header alert-header">
      <div>
        <div class="admin-page-kicker">监控中心</div>
        <h1 class="admin-page-title">告警中心</h1>
        <div class="admin-page-subtitle">聚合调度异常、平台风险与人工处置状态，帮助运营优先处理高风险告警。</div>
      </div>
      <div class="admin-page-actions alert-header-actions">
        <span class="refresh-state">
          <span class="refresh-dot" />
          自动刷新中
        </span>
        <el-button type="primary" :loading="loading" @click="loadAlerts">刷新</el-button>
      </div>
    </div>

    <el-card shadow="never" class="admin-surface alert-toolbar-card">
      <div class="alert-toolbar">
        <div class="alert-filters">
          <el-select v-model="filters.rangeType" style="width: 140px" @change="onFilterChange">
            <el-option label="今日" value="today" />
            <el-option label="近7天" value="last7" />
            <el-option label="近30天" value="last30" />
            <el-option label="自定义" value="custom" />
          </el-select>
          <el-date-picker
            v-if="filters.rangeType === 'custom'"
            v-model="filters.customRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            @change="onFilterChange"
          />
          <el-select v-model="filters.severity" clearable placeholder="严重级别" style="width: 130px" @change="onFilterChange">
            <el-option label="信息" value="info" />
            <el-option label="警告" value="warn" />
            <el-option label="错误" value="error" />
            <el-option label="严重" value="critical" />
          </el-select>
          <el-select v-model="filters.status" clearable placeholder="处理状态" style="width: 130px" @change="onFilterChange">
            <el-option label="待处理" value="open" />
            <el-option label="已处理" value="resolved" />
          </el-select>
        </div>
        <div class="toolbar-note">后台标签页自动暂停刷新</div>
      </div>
    </el-card>

    <div class="admin-metric-grid alert-metric-grid">
      <div class="admin-metric-card" style="--metric-accent: #ef4444; --metric-tone: #fef2f2">
        <span class="admin-metric-label">待处理告警</span>
        <strong class="admin-metric-value">{{ openCount }}</strong>
        <span class="admin-metric-hint">当前页未关闭告警</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #7c3aed; --metric-tone: #f5f3ff">
        <span class="admin-metric-label">严重告警</span>
        <strong class="admin-metric-value">{{ criticalCount }}</strong>
        <span class="admin-metric-hint">critical 级别需优先确认</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #f59e0b; --metric-tone: #fffbeb">
        <span class="admin-metric-label">累计重试</span>
        <strong class="admin-metric-value">{{ retryTotal }}</strong>
        <span class="admin-metric-hint">当前页告警关联任务重试</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #059669; --metric-tone: #ecfdf5">
        <span class="admin-metric-label">已处理</span>
        <strong class="admin-metric-value">{{ resolvedCount }}</strong>
        <span class="admin-metric-hint">当前筛选结果中已闭环</span>
      </div>
    </div>

    <div class="alert-focus-grid">
      <section class="alert-focus-card is-critical">
        <div class="focus-label">优先级队列</div>
        <strong>{{ priorityMessage.title }}</strong>
        <span>{{ priorityMessage.desc }}</span>
      </section>
      <section class="alert-focus-card">
        <div class="focus-label">项目影响面</div>
        <strong>{{ affectedProjectCount }}</strong>
        <span>当前页涉及项目数，重复项目按一次计算。</span>
      </section>
      <section class="alert-focus-card">
        <div class="focus-label">最近告警</div>
        <strong>{{ latestAlertTime }}</strong>
        <span>用于判断异常是否仍在持续产生。</span>
      </section>
    </div>

    <el-card shadow="never" class="admin-table-card alert-table-card">
      <div class="table-header">
        <div>
          <div class="table-title">告警列表</div>
          <div class="table-subtitle">按时间、级别、状态和项目追踪告警处置进度。</div>
        </div>
        <div class="chips">
          <span class="chip chip-muted">总计 {{ page.total }}</span>
          <span class="chip chip-danger">待处理 {{ openCount }}</span>
          <span class="chip chip-success">已处理 {{ resolvedCount }}</span>
        </div>
      </div>

      <DataState :loading="loading" :empty="!loading && rows.length === 0" empty-text="暂无告警数据">
        <el-table :data="rows" border table-layout="fixed">
          <el-table-column label="告警对象" min-width="230" show-overflow-tooltip>
            <template #default="scope">
              <div class="admin-entity-cell">
                <div class="admin-entity-avatar alert-avatar" :class="severityClass(scope.row.severity)">
                  {{ alertInitial(scope.row.projectName || scope.row.title) }}
                </div>
                <div class="min-w-0">
                  <div class="admin-entity-main">{{ scope.row.projectName || '未关联项目' }}</div>
                  <div class="admin-entity-sub">{{ scope.row.alertCode || `告警 #${scope.row.id}` }}</div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="时间" width="170">
            <template #default="scope">{{ formatDateTime(scope.row.createdAt) }}</template>
          </el-table-column>
          <el-table-column prop="severity" label="级别" width="110">
            <template #default="scope">
              <span class="admin-status-tag" :class="severityClass(scope.row.severity)">
                {{ severityLabel(scope.row.severity) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="scope">
              <span class="admin-status-tag" :class="statusClass(scope.row.status)">
                {{ statusLabel(scope.row.status) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
          <el-table-column prop="retryCount" label="重试次数" width="100" />
          <el-table-column label="详情" min-width="280">
            <template #default="scope">
              <el-popover trigger="click" width="560" placement="top">
                <template #reference>
                  <el-button link type="primary">{{ shortText(scope.row.content || '-') }}</el-button>
                </template>
                <div class="detail-wrap">
                  <div><strong>content:</strong> {{ scope.row.content || '-' }}</div>
                  <div class="mt-2"><strong>context:</strong></div>
                  <pre>{{ scope.row.contextJson || '-' }}</pre>
                </div>
              </el-popover>
            </template>
          </el-table-column>
          <el-table-column label="处理" width="120" fixed="right">
            <template #default="scope">
              <el-button
                link
                type="primary"
                :disabled="scope.row.status !== 'open' || !canResolveAlert"
                @click="resolve(scope.row)"
              >
                标记已处理
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </DataState>

      <div class="admin-table-footer">
        <el-pagination
          background
          layout="prev, pager, next, total"
          :current-page="page.current"
          :page-size="page.size"
          :total="page.total"
          @current-change="onPageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import DataState from '@/components/ui/DataState.vue'
import { getDispatchAlerts, resolveDispatchAlert, type DispatchAlertQuery } from '@/api/dispatch'
import type { DispatchAlertItem } from '@/types'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const canResolveAlert = userStore.hasPermission('dispatch.alert.resolve')

const loading = ref(false)
const rows = ref<DispatchAlertItem[]>([])
const page = reactive({ current: 1, size: 20, total: 0 })

const filters = reactive({
  rangeType: 'today' as 'today' | 'last7' | 'last30' | 'custom',
  customRange: [] as string[],
  severity: '',
  status: 'open',
})

let timer: number | null = null

const openCount = computed(() => rows.value.filter((item) => item.status === 'open').length)
const resolvedCount = computed(() => rows.value.filter((item) => item.status === 'resolved').length)
const criticalCount = computed(() => rows.value.filter((item) => item.severity === 'critical').length)
const retryTotal = computed(() => rows.value.reduce((sum, item) => sum + Number(item.retryCount || 0), 0))
const affectedProjectCount = computed(() => new Set(rows.value.map((item) => item.projectId || item.projectName).filter(Boolean)).size)
const latestAlertTime = computed(() => {
  const latest = rows.value[0]?.createdAt
  return latest ? formatDateTime(latest) : '-'
})
const priorityMessage = computed(() => {
  if (criticalCount.value > 0) {
    return { title: `${criticalCount.value} 条严重告警`, desc: '建议先处理 critical 级别告警，再回看普通失败和重试记录。' }
  }
  if (openCount.value > 0) {
    return { title: `${openCount.value} 条待处理`, desc: '当前仍有未闭环告警，建议按项目影响面逐条确认。' }
  }
  return { title: '暂无高风险告警', desc: '当前筛选结果未发现待处理严重告警，可保持自动观察。' }
})

function buildParams(): DispatchAlertQuery {
  const params: DispatchAlertQuery = {
    current: page.current,
    size: page.size,
    rangeType: filters.rangeType,
    severity: filters.severity || undefined,
    status: filters.status || undefined,
  }
  if (filters.rangeType === 'custom') {
    params.startDate = filters.customRange?.[0]
    params.endDate = filters.customRange?.[1]
  }
  return params
}

function shortText(text: string) {
  return text.length > 50 ? `${text.slice(0, 50)}...` : text
}

function severityClass(severity?: string) {
  if (severity === 'critical' || severity === 'error') return 'is-danger'
  if (severity === 'warn') return 'is-warning'
  return 'is-success'
}

function severityLabel(severity?: string) {
  const map: Record<string, string> = {
    info: '信息',
    warn: '警告',
    error: '错误',
    critical: '严重',
  }
  return map[severity || ''] || severity || '-'
}

function statusClass(status?: string) {
  if (status === 'open') return 'is-danger'
  if (status === 'resolved') return 'is-success'
  return 'is-muted'
}

function statusLabel(status?: string) {
  const map: Record<string, string> = {
    open: '待处理',
    resolved: '已处理',
  }
  return map[status || ''] || status || '-'
}

function alertInitial(value?: string | null) {
  const text = String(value || '').trim()
  return text ? Array.from(text)[0] : '告'
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

async function loadAlerts() {
  if (filters.rangeType === 'custom' && (!filters.customRange?.[0] || !filters.customRange?.[1])) {
    ElMessage.warning('请选择完整的自定义日期范围')
    return
  }
  loading.value = true
  try {
    const { data } = await getDispatchAlerts(buildParams())
    rows.value = data.data.records || []
    page.total = data.data.total || 0
  } finally {
    loading.value = false
  }
}

function onFilterChange() {
  page.current = 1
  loadAlerts()
}

function onPageChange(v: number) {
  page.current = v
  loadAlerts()
}

async function resolve(row: DispatchAlertItem) {
  if (!canResolveAlert) {
    ElMessage.warning('当前账号无告警处理权限')
    return
  }
  const { value } = await ElMessageBox.prompt('请输入处理备注（可选）', '标记告警已处理', {
    inputPlaceholder: '例如：平台恢复正常，已重放任务',
    confirmButtonText: '确认',
    cancelButtonText: '取消',
  }).catch(() => ({ value: '' }))
  await resolveDispatchAlert(row.id, value?.trim() || undefined)
  ElMessage.success('已标记处理')
  await loadAlerts()
}

function startAutoRefresh() {
  stopAutoRefresh()
  timer = window.setInterval(() => {
    if (document.hidden) return
    loadAlerts()
  }, 60000)
}

function stopAutoRefresh() {
  if (timer) {
    window.clearInterval(timer)
    timer = null
  }
}

onMounted(async () => {
  await loadAlerts()
  startAutoRefresh()
})

onBeforeUnmount(() => {
  stopAutoRefresh()
})
</script>

<style scoped>
.alert-header,
.alert-header-actions,
.alert-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
}

.alert-toolbar-card :deep(.el-card__body) {
  padding: 12px;
}

.alert-filters {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.toolbar-note {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.refresh-state {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 32px;
  padding: 0 12px;
  border: 1px solid #a7f3d0;
  border-radius: 999px;
  background: #ecfdf5;
  color: #047857;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

.refresh-dot {
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: #10b981;
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.16);
}

.alert-metric-grid {
  margin-bottom: 0;
}

.alert-focus-grid {
  display: grid;
  grid-template-columns: 1.2fr repeat(2, minmax(0, 0.9fr));
  gap: 12px;
}

.alert-focus-card {
  min-width: 0;
  border: 1px solid #dbeafe;
  border-radius: 14px;
  background: linear-gradient(135deg, #ffffff 0%, #ffffff 62%, #f8fbff 100%);
  padding: 16px;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.055);
}

.alert-focus-card.is-critical {
  border-color: #fecaca;
  background: linear-gradient(135deg, #ffffff 0%, #ffffff 60%, #fef2f2 100%);
}

.focus-label {
  color: #2563eb;
  font-size: 12px;
  font-weight: 800;
}

.alert-focus-card strong {
  display: block;
  margin-top: 8px;
  color: #0f172a;
  font-size: 20px;
  font-weight: 800;
}

.alert-focus-card span {
  display: block;
  margin-top: 8px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.55;
}

.alert-table-card :deep(.el-card__body) {
  padding: 0;
}

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  padding: 16px 16px 12px;
  border-bottom: 1px solid var(--admin-panel-border-soft);
  background: linear-gradient(90deg, #f8fbff 0%, #ffffff 55%, #fef2f2 100%);
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
}

.chip-muted {
  background: #f3f4f6;
  color: #6b7280;
}

.chip-danger {
  background: #fef2f2;
  color: #b91c1c;
}

.chip-success {
  background: #ecfdf5;
  color: #047857;
}

.alert-avatar.is-success {
  background: linear-gradient(135deg, #059669, #14b8a6);
}

.alert-avatar.is-warning {
  background: linear-gradient(135deg, #d97706, #f59e0b);
}

.alert-avatar.is-danger {
  background: linear-gradient(135deg, #dc2626, #ef4444);
}

.detail-wrap pre {
  margin: 8px 0 0;
  max-height: 280px;
  overflow: auto;
  padding: 10px 12px;
  border: 1px solid #dbeafe;
  border-radius: 10px;
  background: #f8fbff;
  color: #334155;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  line-height: 1.6;
}

@media (max-width: 900px) {
  .alert-header,
  .alert-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .alert-focus-grid {
    grid-template-columns: 1fr;
  }

  .alert-filters {
    align-items: stretch;
  }
}
</style>
