<template>
  <div>
    <el-card class="mb-4" shadow="never">
      <div class="toolbar">
        <div class="left">
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
          <el-button :loading="loading" @click="loadAlerts">刷新</el-button>
        </div>
        <div class="right">自动刷新：60秒（后台标签页暂停）</div>
      </div>
    </el-card>

    <el-card shadow="never">
      <DataState :loading="loading" :empty="!loading && rows.length === 0" empty-text="暂无告警数据">
        <el-table :data="rows" border>
          <el-table-column prop="createdAt" label="时间" min-width="170" />
          <el-table-column prop="severity" label="级别" width="110">
            <template #default="scope">
              <el-tag :type="severityTagType(scope.row.severity)">{{ severityLabel(scope.row.severity) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="scope">{{ statusLabel(scope.row.status) }}</template>
          </el-table-column>
          <el-table-column prop="projectName" label="项目" min-width="150" />
          <el-table-column prop="title" label="标题" min-width="210" />
          <el-table-column prop="retryCount" label="重试次数" width="100" />
          <el-table-column label="详情" min-width="320">
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

      <div class="mt-4 flex justify-end">
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
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
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

function severityTagType(severity: string) {
  if (severity === 'critical') return 'danger'
  if (severity === 'error') return 'warning'
  if (severity === 'warn') return 'info'
  return 'success'
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

function statusLabel(status?: string) {
  const map: Record<string, string> = {
    open: '待处理',
    resolved: '已处理',
  }
  return map[status || ''] || status || '-'
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
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.left {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.right {
  color: #6b7280;
  font-size: 12px;
}

.detail-wrap pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
