<template>
  <div class="delivery-page admin-page">
    <div class="admin-page-header delivery-header">
      <div>
        <div class="admin-page-kicker">交付管理</div>
        <h1 class="admin-page-title">交付看板</h1>
        <div class="admin-page-subtitle">全局掌握运营承接、项目风险、内容产出与交付异常处理状态。</div>
      </div>
      <div class="admin-page-actions">
        <el-button type="primary" :loading="loading" @click="reload">刷新</el-button>
      </div>
    </div>

    <div class="admin-metric-grid delivery-metric-grid">
      <div class="admin-metric-card" style="--metric-accent: #2563eb; --metric-tone: #eff6ff">
        <span class="admin-metric-label">活跃项目</span>
        <strong class="admin-metric-value">{{ overview.activeProjects }}</strong>
        <span class="admin-metric-hint">当前服务中的项目</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #059669; --metric-tone: #ecfdf5">
        <span class="admin-metric-label">运营人员</span>
        <strong class="admin-metric-value">{{ overview.activeOperators }}</strong>
        <span class="admin-metric-hint">可承接客户的在职运营</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #f59e0b; --metric-tone: #fffbeb">
        <span class="admin-metric-label">本月产出</span>
        <strong class="admin-metric-value">{{ overview.monthlyArticles }} / {{ overview.monthlyReports }}</strong>
        <span class="admin-metric-hint">文章 / 项目报告</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #ef4444; --metric-tone: #fef2f2">
        <span class="admin-metric-label">待处理异常</span>
        <strong class="admin-metric-value">{{ overview.openExceptions }}</strong>
        <span class="admin-metric-hint">含交付告警与失败任务</span>
      </div>
    </div>

    <div class="delivery-grid">
      <section class="delivery-panel">
        <div class="panel-head">
          <div>
            <div class="panel-kicker">运营交付情况</div>
            <h3 class="panel-title">人员负载与产出</h3>
          </div>
          <span class="panel-count">{{ operatorStats.length }}</span>
        </div>
        <DataState :loading="loading" :empty="!loading && operatorStats.length === 0" empty-text="暂无运营统计">
          <el-table :data="operatorStats" border table-layout="fixed">
            <el-table-column label="运营人员" min-width="160" show-overflow-tooltip>
              <template #default="scope">
                <div class="admin-entity-cell">
                  <div class="admin-entity-avatar delivery-avatar">{{ initials(scope.row.operatorName) }}</div>
                  <div class="min-w-0">
                    <div class="admin-entity-main">{{ scope.row.operatorName || `#${scope.row.operatorId}` }}</div>
                    <div class="admin-entity-sub">客户 {{ scope.row.customerCount }}</div>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="activeProjectCount" label="活跃项目" width="100" />
            <el-table-column prop="highRiskProjectCount" label="高风险" width="90" />
            <el-table-column prop="monthlyArticleCount" label="本月文章" width="100" />
            <el-table-column prop="monthlyReportCount" label="本月报告" width="100" />
            <el-table-column label="异常" width="110">
              <template #default="scope">
                <span class="admin-status-tag" :class="scope.row.openExceptionCount > 0 ? 'is-warning' : 'is-success'">
                  {{ scope.row.openExceptionCount }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="failedDispatchTaskCount" label="失败任务" width="100" />
          </el-table>
        </DataState>
      </section>

      <section class="delivery-panel risk-panel">
        <div class="panel-head">
          <div>
            <div class="panel-kicker">风险状态</div>
            <h3 class="panel-title">项目与任务积压</h3>
          </div>
        </div>
        <div class="risk-stack">
          <div class="risk-row">
            <span>高风险项目</span>
            <strong>{{ overview.highRiskProjects }}</strong>
          </div>
          <div class="risk-row">
            <span>失败/死信任务</span>
            <strong>{{ overview.failedDispatchTasks }}</strong>
          </div>
          <div class="risk-row">
            <span>客户总数</span>
            <strong>{{ overview.totalCustomers }}</strong>
          </div>
        </div>
      </section>
    </div>

    <el-card shadow="never" class="admin-table-card exception-card">
      <div class="table-header">
        <div>
          <div class="table-title">交付异常</div>
          <div class="table-subtitle">处理交付告警，跟踪项目负责人和异常闭环状态。</div>
        </div>
        <div class="exception-filters">
          <el-select v-model="exceptionQuery.severity" clearable placeholder="级别" style="width: 120px" @change="loadExceptions">
            <el-option label="信息" value="info" />
            <el-option label="警告" value="warn" />
            <el-option label="错误" value="error" />
            <el-option label="严重" value="critical" />
          </el-select>
          <el-select v-model="exceptionQuery.status" clearable placeholder="状态" style="width: 120px" @change="loadExceptions">
            <el-option label="待处理" value="open" />
            <el-option label="已处理" value="resolved" />
          </el-select>
        </div>
      </div>

      <DataState :loading="exceptionLoading" :empty="!exceptionLoading && exceptions.length === 0" empty-text="暂无异常">
        <el-table :data="exceptions" border table-layout="fixed">
          <el-table-column label="异常对象" min-width="240" show-overflow-tooltip>
            <template #default="scope">
              <div class="admin-entity-cell">
                <div class="admin-entity-avatar alert-avatar" :class="severityClass(scope.row.severity)">
                  {{ initials(scope.row.projectName || scope.row.title) }}
                </div>
                <div class="min-w-0">
                  <div class="admin-entity-main">{{ scope.row.projectName || '未关联项目' }}</div>
                  <div class="admin-entity-sub">{{ scope.row.ownerName || '未分配负责人' }}</div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="时间" width="165">
            <template #default="scope">{{ formatDateTime(scope.row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="级别" width="100">
            <template #default="scope">
              <span class="admin-status-tag" :class="severityClass(scope.row.severity)">
                {{ severityLabel(scope.row.severity) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="scope">
              <span class="admin-status-tag" :class="scope.row.status === 'resolved' ? 'is-success' : 'is-warning'">
                {{ scope.row.status === 'resolved' ? '已处理' : '待处理' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
          <el-table-column prop="retryCount" label="重试" width="80" />
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="scope">
              <el-button
                link
                type="primary"
                :disabled="scope.row.status !== 'open' || !canHandleException"
                @click="handleException(scope.row)"
              >
                标记处理
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </DataState>

      <div class="admin-table-footer">
        <el-pagination
          background
          layout="prev, pager, next, total"
          :current-page="exceptionPage.current"
          :page-size="exceptionPage.size"
          :total="exceptionPage.total"
          @current-change="onExceptionPageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import DataState from '@/components/ui/DataState.vue'
import {
  getDeliveryExceptions,
  getDeliveryOperatorStats,
  getDeliveryOverview,
  handleDeliveryException,
  type DeliveryException,
  type DeliveryOperatorStats,
  type DeliveryOverview,
} from '@/api/delivery'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const loading = ref(false)
const exceptionLoading = ref(false)
const overview = ref<DeliveryOverview>({
  totalCustomers: 0,
  activeProjects: 0,
  highRiskProjects: 0,
  openExceptions: 0,
  failedDispatchTasks: 0,
  monthlyReports: 0,
  monthlyArticles: 0,
  activeOperators: 0,
})
const operatorStats = ref<DeliveryOperatorStats[]>([])
const exceptions = ref<DeliveryException[]>([])
const exceptionPage = reactive({ current: 1, size: 20, total: 0 })
const exceptionQuery = reactive<{ severity?: string; status?: string }>({ status: 'open' })

const canHandleException = computed(() => userStore.hasPermission('delivery.exception.handle'))

async function reload() {
  loading.value = true
  try {
    const [overviewRes, statsRes] = await Promise.all([
      getDeliveryOverview(),
      getDeliveryOperatorStats(),
    ])
    overview.value = overviewRes.data.data
    operatorStats.value = statsRes.data.data || []
    await loadExceptions()
  } finally {
    loading.value = false
  }
}

async function loadExceptions() {
  exceptionLoading.value = true
  try {
    const res = await getDeliveryExceptions({
      current: exceptionPage.current,
      size: exceptionPage.size,
      severity: exceptionQuery.severity,
      status: exceptionQuery.status,
    })
    exceptions.value = res.data.data.records || []
    exceptionPage.total = Number(res.data.data.total || 0)
  } finally {
    exceptionLoading.value = false
  }
}

function onExceptionPageChange(page: number) {
  exceptionPage.current = page
  loadExceptions()
}

async function handleException(row: DeliveryException) {
  const result = await ElMessageBox.prompt('填写处理备注', '标记异常已处理', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    inputType: 'textarea',
    inputPlaceholder: '可填写处理结果、转交说明或后续动作',
  }).catch(() => null)
  if (!result) return
  await handleDeliveryException(row.id, result.value)
  ElMessage.success('已标记处理')
  await Promise.all([loadExceptions(), refreshOverview()])
}

async function refreshOverview() {
  const overviewRes = await getDeliveryOverview()
  overview.value = overviewRes.data.data
}

function initials(value?: string | null) {
  if (!value) return '?'
  return value.trim().slice(0, 1).toUpperCase()
}

function formatDateTime(value?: string | null) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-'
}

function severityClass(value?: string | null) {
  if (value === 'critical' || value === 'error') return 'is-danger'
  if (value === 'warn') return 'is-warning'
  return 'is-info'
}

function severityLabel(value?: string | null) {
  const map: Record<string, string> = {
    critical: '严重',
    error: '错误',
    warn: '警告',
    info: '信息',
  }
  return value ? map[value] || value : '-'
}

onMounted(reload)
</script>

<style scoped>
.delivery-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.delivery-metric-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.delivery-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 18px;
}

.delivery-panel {
  background: var(--card-bg);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 18px;
}

.panel-head,
.table-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 16px;
}

.panel-kicker {
  color: var(--text-muted);
  font-size: 12px;
}

.panel-title,
.table-title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
}

.panel-count {
  color: var(--primary-color);
  font-weight: 700;
}

.risk-stack {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.risk-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 12px;
}

.risk-row span {
  color: var(--text-muted);
  font-size: 13px;
}

.risk-row strong {
  font-size: 22px;
}

.exception-filters {
  display: flex;
  gap: 10px;
}

.delivery-avatar,
.alert-avatar {
  background: #eff6ff;
  color: #2563eb;
}

.alert-avatar.is-danger {
  background: #fef2f2;
  color: #dc2626;
}

.alert-avatar.is-warning {
  background: #fffbeb;
  color: #d97706;
}

.alert-avatar.is-info {
  background: #eef2ff;
  color: #4f46e5;
}

@media (max-width: 1100px) {
  .delivery-metric-grid,
  .delivery-grid {
    grid-template-columns: 1fr;
  }
}
</style>
