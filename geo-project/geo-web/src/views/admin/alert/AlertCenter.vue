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

    <el-tabs v-model="activeTab" class="alert-tabs" @tab-change="onTabChange">
      <el-tab-pane v-if="canViewDispatchAlerts" label="调度告警" name="dispatch" />
      <el-tab-pane v-if="canViewSystemAlerts" label="系统待办" name="system" />
    </el-tabs>

    <el-card v-if="activeTab === 'dispatch' && canViewDispatchAlerts" shadow="never" class="admin-surface alert-toolbar-card">
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

    <div v-if="activeTab === 'dispatch' && canViewDispatchAlerts" class="admin-metric-grid alert-metric-grid">
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

    <div v-if="activeTab === 'dispatch' && canViewDispatchAlerts" class="alert-focus-grid">
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

    <el-card v-if="activeTab === 'dispatch' && canViewDispatchAlerts" shadow="never" class="admin-table-card alert-table-card">
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
                  <div class="admin-entity-sub">
                    <span>{{ scope.row.alertCode || `告警 #${scope.row.id}` }}</span>
                    <el-tag v-if="Number(scope.row.groupCount || 0) > 1" size="small" type="warning" effect="plain">
                      {{ scope.row.groupCount }} 条
                    </el-tag>
                  </div>
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
              <el-button link type="primary" :loading="detailLoading && detailRow?.id === scope.row.id" @click="openDispatchDetail(scope.row)">
                {{ dispatchDetailPreview(scope.row) }}
              </el-button>
            </template>
          </el-table-column>
          <el-table-column label="处理" width="120" fixed="right">
            <template #default="scope">
              <el-button
                link
                type="primary"
                :disabled="scope.row.status !== 'open' || !canResolveDispatchAlert"
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

    <el-card v-if="activeTab === 'system' && canViewSystemAlerts" shadow="never" class="admin-table-card alert-table-card">
      <div class="table-header">
        <div>
          <div class="table-title">系统待办</div>
          <div class="table-subtitle">自动分发产生的配置类待办，处理后可在此标记闭环。</div>
        </div>
        <div class="chips">
          <span class="chip chip-muted">总计 {{ systemPage.total }}</span>
          <span class="chip chip-danger">待处理 {{ systemRows.length }}</span>
        </div>
      </div>

      <DataState :loading="loading" :empty="!loading && systemRows.length === 0" empty-text="暂无系统待办">
        <el-table :data="systemRows" border table-layout="fixed">
          <el-table-column label="待办事项" min-width="260" show-overflow-tooltip>
            <template #default="scope">
              <div class="admin-entity-cell">
                <div class="admin-entity-avatar alert-avatar" :class="severityClass(scope.row.severity)">
                  {{ alertInitial(scope.row.message) }}
                </div>
                <div class="min-w-0">
                  <div class="admin-entity-main">{{ scope.row.message }}</div>
                  <div class="admin-entity-sub">
                    <span>{{ scope.row.source || scope.row.alertType }}</span>
                    <el-tag v-if="isSpecialIndustryTodo(scope.row)" size="small" type="warning" effect="plain">特殊行业</el-tag>
                  </div>
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
          <el-table-column label="详情" min-width="260">
            <template #default="scope">
              <el-popover trigger="click" width="520" placement="top">
                <template #reference>
                  <el-button link type="primary">{{ systemTodoDetailPreview(scope.row) }}</el-button>
                </template>
                <div class="detail-wrap">
                  <div v-if="isSpecialIndustryTodo(scope.row)" class="special-alert-detail">
                    <div>
                      <strong>处理类型</strong>
                      <span>{{ specialIndustryActionLabel(systemTodoContext(scope.row).action) }}</span>
                    </div>
                    <div>
                      <strong>项目</strong>
                      <span>{{ contextValue(systemTodoContext(scope.row), 'projectName') || contextValue(systemTodoContext(scope.row), 'projectId') || '-' }}</span>
                    </div>
                    <div>
                      <strong>品牌</strong>
                      <span>{{ contextValue(systemTodoContext(scope.row), 'brandName') || contextValue(systemTodoContext(scope.row), 'brandId') || '-' }}</span>
                    </div>
                    <div>
                      <strong>文章</strong>
                      <span>{{ formatObjectId('文章', systemTodoContext(scope.row).articleId) }}</span>
                    </div>
                    <div>
                      <strong>批次/任务</strong>
                      <span>{{ formatTraceIds(systemTodoContext(scope.row)) }}</span>
                    </div>
                    <div v-if="formatHitRules(systemTodoContext(scope.row).hitRuleTypes)">
                      <strong>命中规则</strong>
                      <span>{{ formatHitRules(systemTodoContext(scope.row).hitRuleTypes) }}</span>
                    </div>
                  </div>
                  <pre v-else>{{ scope.row.contextJson || '-' }}</pre>
                </div>
              </el-popover>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="190" fixed="right">
            <template #default="scope">
              <el-button link type="primary" @click="openSystemTodo(scope.row)">去处理</el-button>
              <el-button link type="success" :disabled="!canResolveSystemAlert" @click="resolveSystemTodo(scope.row)">标记已处理</el-button>
            </template>
          </el-table-column>
        </el-table>
      </DataState>

      <div class="admin-table-footer">
        <el-pagination
          background
          layout="prev, pager, next, total"
          :current-page="systemPage.current"
          :page-size="systemPage.size"
          :total="systemPage.total"
          @current-change="onSystemPageChange"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="detailVisible"
      title="告警详情"
      width="860px"
      class="dispatch-alert-dialog"
      append-to-body
      destroy-on-close
    >
      <DataState :loading="detailLoading" :empty="!detailLoading && !detailRow" empty-text="暂无告警详情">
        <div v-if="detailRow" class="dispatch-detail">
          <section class="detail-hero" :class="severityClass(detailRow.severity)">
            <div class="detail-hero-main">
              <div class="detail-eyebrow">调度告警 · {{ severityLabel(detailRow.severity) }}</div>
              <h2>{{ detailTitle(detailRow) }}</h2>
              <p>{{ detailRow.content || '暂无告警说明' }}</p>
              <div class="detail-meta">
                <span>{{ detailRow.projectName || '未关联项目' }}</span>
                <span>{{ formatDateTime(detailRow.createdAt) }}</span>
                <span>{{ statusLabel(detailRow.status) }}</span>
                <span>组内 {{ detailRow.groupCount || 1 }} 条</span>
              </div>
            </div>
            <div class="detail-hero-stat">
              <span>失败率</span>
              <strong>{{ formatPercent(detailFailureRate(detailRow)) }}</strong>
              <em>{{ detailFailedTotal(detailRow) }} / {{ detailExpectedTotal(detailRow) || '-' }}</em>
            </div>
          </section>

          <section class="detail-kpi-grid">
            <div class="detail-kpi-card">
              <span>涉及平台</span>
              <strong>{{ detailPlatformCount(detailRow) }}</strong>
              <em>出现失败的平台数量</em>
            </div>
            <div class="detail-kpi-card">
              <span>失败次数</span>
              <strong>{{ detailFailedTotal(detailRow) }}</strong>
              <em>问题轮询失败结果数</em>
            </div>
            <div class="detail-kpi-card">
              <span>原始告警</span>
              <strong>{{ detailRow.detailAlerts?.length || detailRow.groupCount || 1 }}</strong>
              <em>同客户同日已聚合</em>
            </div>
          </section>

          <section class="detail-panel">
            <div class="detail-panel-header">
              <div>
                <div class="detail-title">平台失败统计</div>
                <div class="detail-subtitle">按平台汇总失败次数、比例和主要原因。</div>
              </div>
              <span class="detail-panel-badge">{{ detailPlatformCount(detailRow) }} 个平台</span>
            </div>
            <div v-if="(detailRow.platformFailures || []).length" class="platform-failure-list">
              <article
                v-for="platform in detailRow.platformFailures || []"
                :key="`${platform.platformCode || 'platform'}-${platform.platformId ?? 'unknown'}`"
                class="platform-failure-card"
              >
                <div class="platform-card-head">
                  <div>
                    <div class="platform-name">{{ platform.platformName || platform.platformCode || '-' }}</div>
                    <div class="platform-code">{{ platform.platformCode || '-' }}</div>
                  </div>
                  <div class="platform-rate">
                    <strong>{{ formatPercent(platform.failureRate) }}</strong>
                    <span>{{ platform.failedCount || 0 }} / {{ platform.expectedCount || 0 }}</span>
                  </div>
                </div>
                <div class="reason-list">
                  <div v-for="reason in platform.reasons || []" :key="`${reason.errorCode}-${reason.errorMessage}`" class="reason-item">
                    <div class="reason-code">{{ reason.errorCode || 'UNKNOWN' }}</div>
                    <div class="reason-message">{{ reason.errorMessage || '-' }}</div>
                    <div class="reason-count">{{ reason.count || 0 }} 次</div>
                  </div>
                </div>
              </article>
            </div>
            <div v-else class="detail-empty-state">
              <strong>暂无平台失败</strong>
              <span>当前告警没有附带平台维度失败统计，建议查看组内告警上下文。</span>
            </div>
          </section>

          <section class="detail-panel">
            <div class="detail-panel-header">
              <div>
                <div class="detail-title">组内告警</div>
                <div class="detail-subtitle">同一客户当天的跑批异常已合并展示，以下为原始告警明细。</div>
              </div>
              <span class="detail-panel-badge">共 {{ detailAlertTotal }} 条</span>
            </div>
            <div class="detail-timeline">
              <article v-for="item in pagedDetailAlerts" :key="item.id" class="detail-alert-item">
                <div class="timeline-dot" :class="severityClass(item.severity)" />
                <div class="timeline-content">
                  <div class="timeline-head">
                    <strong>{{ item.title }}</strong>
                    <span>{{ formatDateTime(item.createdAt) }}</span>
                  </div>
                  <div class="timeline-tags">
                    <span>{{ severityLabel(item.severity) }}</span>
                    <span>{{ statusLabel(item.status) }}</span>
                    <span>{{ item.alertCode || `#${item.id}` }}</span>
                  </div>
                  <p>{{ item.content || '-' }}</p>
                  <details>
                    <summary>查看上下文</summary>
                    <pre>{{ formatJsonText(item.contextJson) }}</pre>
                  </details>
                </div>
              </article>
            </div>
            <div v-if="detailAlertTotal > detailAlertPageSize" class="detail-pagination">
              <el-pagination
                v-model:current-page="detailAlertPage"
                :page-size="detailAlertPageSize"
                :total="detailAlertTotal"
                background
                layout="prev, pager, next, jumper"
                small
              />
            </div>
          </section>
        </div>
      </DataState>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import DataState from '@/components/ui/DataState.vue'
import { getDispatchAlert, getDispatchAlerts, resolveDispatchAlert, type DispatchAlertQuery } from '@/api/dispatch'
import { getMySystemAlertTodos, resolveSystemAlert } from '@/api/systemAlert'
import type { DispatchAlertItem, SystemAlertTodoItem } from '@/types'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const router = useRouter()
const canViewDispatchAlerts = userStore.hasPermission(['content.distribution.retry', 'dispatch.alert.resolve'])
const canViewSystemAlerts = userStore.hasPermission(['system.alert.resolve', 'content.read'])
const canResolveDispatchAlert = userStore.hasPermission('dispatch.alert.resolve')
const canResolveSystemAlert = userStore.hasPermission('system.alert.resolve')

const activeTab = ref<'dispatch' | 'system'>(canViewDispatchAlerts ? 'dispatch' : 'system')
const loading = ref(false)
const rows = ref<DispatchAlertItem[]>([])
const page = reactive({ current: 1, size: 20, total: 0 })
const systemRows = ref<SystemAlertTodoItem[]>([])
const systemPage = reactive({ current: 1, size: 20, total: 0 })
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailRow = ref<DispatchAlertItem | null>(null)
const detailAlertPage = ref(1)
const detailAlertPageSize = 3

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
const detailAlertTotal = computed(() => detailRow.value?.detailAlerts?.length || 0)
const pagedDetailAlerts = computed(() => {
  const alerts = detailRow.value?.detailAlerts || []
  const start = (detailAlertPage.value - 1) * detailAlertPageSize
  return alerts.slice(start, start + detailAlertPageSize)
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

function formatPercent(value?: number | null) {
  if (value === null || value === undefined || !Number.isFinite(Number(value))) return '-'
  return `${Number(value).toFixed(2)}%`
}

function detailTitle(row: DispatchAlertItem) {
  return String(row.title || '').replace(/（\d+条）$/, '')
}

function detailPlatformCount(row?: DispatchAlertItem | null) {
  return row?.platformFailures?.length || 0
}

function detailFailedTotal(row?: DispatchAlertItem | null) {
  if (row?.failedCount !== null && row?.failedCount !== undefined) {
    return Number(row.failedCount || 0)
  }
  return (row?.platformFailures || []).reduce((sum, item) => sum + Number(item.failedCount || 0), 0)
}

function detailExpectedTotal(row?: DispatchAlertItem | null) {
  if (row?.expectedResultCount !== null && row?.expectedResultCount !== undefined) {
    return Number(row.expectedResultCount || 0)
  }
  return (row?.platformFailures || []).reduce((sum, item) => sum + Number(item.expectedCount || 0), 0)
}

function detailFailureRate(row?: DispatchAlertItem | null) {
  if (row?.failureRate !== null && row?.failureRate !== undefined) {
    return Number(row.failureRate)
  }
  const expected = detailExpectedTotal(row)
  if (!expected) return null
  return Math.round((detailFailedTotal(row) * 10000) / expected) / 100
}

function formatJsonText(value?: string | null) {
  if (!value) return '-'
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

function dispatchDetailPreview(row: DispatchAlertItem) {
  const failures = row.platformFailures || []
  if (failures.length > 0) {
    const failed = failures.reduce((sum, item) => sum + Number(item.failedCount || 0), 0)
    const platforms = failures.map((item) => item.platformName || item.platformCode).filter(Boolean).slice(0, 2).join('、')
    return shortText(`${platforms || '平台'}失败 ${failed} 次`)
  }
  if (Number(row.groupCount || 0) > 1) return shortText(`${row.groupCount} 条告警，点击查看明细`)
  return shortText(row.content || '-')
}

async function openDispatchDetail(row: DispatchAlertItem) {
  detailVisible.value = true
  detailLoading.value = true
  detailAlertPage.value = 1
  detailRow.value = row
  try {
    const { data } = await getDispatchAlert(row.id)
    detailRow.value = data.data
    detailAlertPage.value = 1
  } finally {
    detailLoading.value = false
  }
}

async function loadAlerts() {
  if (activeTab.value === 'dispatch' && !canViewDispatchAlerts) {
    activeTab.value = canViewSystemAlerts ? 'system' : 'dispatch'
  }
  if (activeTab.value === 'system' && !canViewSystemAlerts) {
    activeTab.value = canViewDispatchAlerts ? 'dispatch' : 'system'
  }
  if (activeTab.value === 'system') {
    await loadSystemTodos()
    return
  }
  await loadDispatchAlerts()
}

async function loadDispatchAlerts() {
  if (!canViewDispatchAlerts) return
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

async function loadSystemTodos() {
  if (!canViewSystemAlerts) return
  loading.value = true
  try {
    const { data } = await getMySystemAlertTodos({
      current: systemPage.current,
      size: systemPage.size,
    })
    systemRows.value = data.data.records || []
    systemPage.total = data.data.total || 0
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

function onSystemPageChange(v: number) {
  systemPage.current = v
  loadSystemTodos()
}

function onTabChange() {
  loadAlerts()
}

async function resolve(row: DispatchAlertItem) {
  if (!canResolveDispatchAlert) {
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

function parseSystemTodoContext(row: SystemAlertTodoItem) {
  if (!row.contextJson) return {}
  try {
    return JSON.parse(row.contextJson) as Record<string, unknown>
  } catch {
    return {}
  }
}

function systemTodoContext(row: SystemAlertTodoItem) {
  return parseSystemTodoContext(row)
}

function contextValue(context: Record<string, unknown>, key: string) {
  const value = context[key]
  if (value === null || value === undefined || value === '') return ''
  return String(value)
}

function isSpecialIndustryTodo(row: SystemAlertTodoItem) {
  return row.source === 'special_industry_compliance' || String(row.alertType || '').startsWith('special_industry_')
}

function specialIndustryActionLabel(action: unknown) {
  const map: Record<string, string> = {
    discarded_compliance_failed: '合规失败已废弃',
    publish_review_pending: '官网发布待确认',
    publish_review_rejected: '官网发布被驳回',
  }
  const key = String(action || '')
  return map[key] || key || '-'
}

function formatObjectId(label: string, value: unknown) {
  const text = value === null || value === undefined || value === '' ? '' : String(value)
  return text ? `${label} #${text}` : '-'
}

function formatTraceIds(context: Record<string, unknown>) {
  const values = [
    formatObjectId('批次', context.batchId),
    formatObjectId('任务', context.taskId),
  ].filter((item) => item !== '-')
  return values.length ? values.join(' / ') : '-'
}

function formatHitRules(value: unknown) {
  if (Array.isArray(value)) {
    return value.filter(Boolean).map(String).join('、')
  }
  return value === null || value === undefined ? '' : String(value)
}

function systemTodoDetailPreview(row: SystemAlertTodoItem) {
  if (!isSpecialIndustryTodo(row)) return shortText(row.contextJson || '-')
  const context = systemTodoContext(row)
  const actionLabel = specialIndustryActionLabel(context.action)
  const project = contextValue(context, 'projectName') || contextValue(context, 'projectId')
  const article = contextValue(context, 'articleId')
  return shortText([actionLabel, project ? `项目 ${project}` : '', article ? `文章 #${article}` : ''].filter(Boolean).join(' · ') || '-')
}

function normalizedSystemTodoRoute(row: SystemAlertTodoItem) {
  const context = systemTodoContext(row)
  const path = typeof context.route === 'string' ? context.route : ''
  if (!path) return null
  if (!isSpecialIndustryTodo(row) || path !== '/admin/content/special-industry-compliance') {
    return { path }
  }
  const query: Record<string, string> = {}
  ;['articleId', 'batchId', 'taskId', 'action'].forEach((key) => {
    const value = context[key]
    if (value !== null && value !== undefined && value !== '') {
      query[key] = String(value)
    }
  })
  return { path, query }
}

function openSystemTodo(row: SystemAlertTodoItem) {
  const route = normalizedSystemTodoRoute(row)
  if (!route) {
    ElMessage.warning('该待办未配置跳转地址')
    return
  }
  router.push(route)
}

async function resolveSystemTodo(row: SystemAlertTodoItem) {
  if (!canResolveSystemAlert) {
    ElMessage.warning('当前账号无系统待办处理权限')
    return
  }
  await resolveSystemAlert(row.id)
  ElMessage.success('已标记处理')
  await loadSystemTodos()
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

.admin-entity-sub {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.special-alert-detail {
  display: grid;
  gap: 10px;
}

.special-alert-detail div {
  display: grid;
  grid-template-columns: 82px minmax(0, 1fr);
  gap: 10px;
  align-items: start;
}

.special-alert-detail strong {
  color: #475569;
  font-size: 12px;
}

.special-alert-detail span {
  color: #0f172a;
  font-size: 13px;
  line-height: 1.5;
  word-break: break-word;
}

.dispatch-detail {
  display: grid;
  gap: 18px;
}

.detail-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 160px;
  gap: 18px;
  align-items: stretch;
  border: 1px solid #dbeafe;
  border-radius: 12px;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.96), rgba(248, 251, 255, 0.94)),
    linear-gradient(135deg, #eff6ff, #fff7ed);
  padding: 18px;
}

.detail-hero.is-danger {
  border-color: #fecaca;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.96), rgba(255, 247, 247, 0.95)),
    linear-gradient(135deg, #fff1f2, #fff7ed);
}

.detail-hero-main {
  min-width: 0;
}

.detail-eyebrow {
  color: #2563eb;
  font-size: 12px;
  font-weight: 900;
}

.detail-hero h2 {
  margin: 8px 0 0;
  color: #0f172a;
  font-size: 20px;
  font-weight: 900;
  line-height: 1.25;
}

.detail-hero p {
  margin: 10px 0 0;
  color: #475569;
  font-size: 14px;
  line-height: 1.65;
}

.detail-hero-stat {
  display: grid;
  place-content: center;
  justify-items: center;
  border: 1px solid rgba(239, 68, 68, 0.18);
  border-radius: 10px;
  background: #fff;
  padding: 14px;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.06);
}

.detail-hero-stat span,
.detail-hero-stat em {
  color: #64748b;
  font-size: 12px;
  font-style: normal;
  font-weight: 800;
}

.detail-hero-stat strong {
  margin: 6px 0;
  color: #dc2626;
  font-size: 28px;
  font-weight: 900;
  line-height: 1;
}

.detail-kpi-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.detail-kpi-card {
  min-width: 0;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #fff;
  padding: 12px;
}

.detail-kpi-card span,
.detail-kpi-card em {
  display: block;
  color: #64748b;
  font-size: 12px;
  font-style: normal;
  font-weight: 700;
}

.detail-kpi-card strong {
  display: block;
  margin: 6px 0;
  color: #0f172a;
  font-size: 22px;
  font-weight: 900;
}

.detail-panel {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #fff;
  overflow: hidden;
}

.detail-panel-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  padding: 14px 16px;
  border-bottom: 1px solid #e2e8f0;
  background: #f8fafc;
}

.detail-title {
  color: #0f172a;
  font-size: 15px;
  font-weight: 900;
}

.detail-subtitle {
  margin-top: 3px;
  color: #64748b;
  font-size: 12px;
}

.detail-panel-badge {
  flex: none;
  border-radius: 999px;
  background: #eff6ff;
  color: #1d4ed8;
  padding: 5px 10px;
  font-size: 12px;
  font-weight: 800;
}

.detail-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.detail-meta span {
  border-radius: 14px;
  background: #f1f5f9;
  color: #475569;
  padding: 3px 9px;
  font-size: 12px;
  font-weight: 700;
}

.platform-failure-list {
  display: grid;
  gap: 10px;
  padding: 12px;
}

.platform-failure-card {
  border: 1px solid #dbeafe;
  border-radius: 10px;
  background: #f8fbff;
  padding: 12px;
}

.platform-card-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.platform-name {
  color: #0f172a;
  font-size: 14px;
  font-weight: 900;
}

.platform-code {
  margin-top: 2px;
  color: #64748b;
  font-size: 12px;
}

.platform-rate {
  flex: none;
  text-align: right;
}

.platform-rate strong,
.platform-rate span {
  display: block;
}

.platform-rate strong {
  color: #dc2626;
  font-size: 18px;
  font-weight: 900;
}

.platform-rate span {
  color: #64748b;
  font-size: 12px;
}

.reason-list {
  display: grid;
  gap: 8px;
  margin-top: 12px;
}

.reason-item {
  display: grid;
  grid-template-columns: minmax(90px, 0.35fr) minmax(0, 1fr) 52px;
  gap: 10px;
  align-items: start;
  border-radius: 8px;
  background: #fff;
  padding: 9px 10px;
}

.reason-code {
  color: #b91c1c;
  font-size: 12px;
  font-weight: 900;
  word-break: break-word;
}

.reason-message {
  color: #334155;
  font-size: 12px;
  line-height: 1.5;
  word-break: break-word;
}

.reason-count {
  justify-self: end;
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
  white-space: nowrap;
}

.detail-empty-state {
  display: grid;
  justify-items: center;
  gap: 5px;
  padding: 30px 16px;
  color: #64748b;
}

.detail-empty-state strong {
  color: #334155;
  font-size: 14px;
}

.detail-empty-state span {
  font-size: 12px;
}

.detail-timeline {
  display: grid;
  gap: 0;
  padding: 8px 14px 14px;
}

.detail-alert-item {
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr);
  gap: 10px;
  position: relative;
  padding: 10px 0;
}

.detail-alert-item + .detail-alert-item {
  border-top: 1px solid #edf2f7;
}

.timeline-dot {
  width: 9px;
  height: 9px;
  margin-top: 8px;
  border-radius: 999px;
  background: #10b981;
  box-shadow: 0 0 0 4px rgba(16, 185, 129, 0.12);
}

.timeline-dot.is-danger {
  background: #ef4444;
  box-shadow: 0 0 0 4px rgba(239, 68, 68, 0.12);
}

.timeline-dot.is-warning {
  background: #f59e0b;
  box-shadow: 0 0 0 4px rgba(245, 158, 11, 0.14);
}

.timeline-content {
  min-width: 0;
}

.timeline-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: baseline;
}

.timeline-head strong {
  min-width: 0;
  color: #0f172a;
  font-size: 13px;
  font-weight: 900;
}

.timeline-head span {
  flex: none;
  color: #64748b;
  font-size: 12px;
}

.timeline-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
}

.timeline-tags span {
  border-radius: 999px;
  background: #f1f5f9;
  color: #475569;
  padding: 2px 8px;
  font-size: 12px;
  font-weight: 700;
}

.detail-alert-item p {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.55;
}

.detail-alert-item details {
  margin-top: 8px;
}

.detail-alert-item summary {
  cursor: pointer;
  color: #2563eb;
  font-size: 12px;
  font-weight: 800;
}

.detail-alert-item pre {
  max-height: 180px;
  overflow: auto;
  margin: 8px 0 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  line-height: 1.55;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #f8fafc;
  padding: 10px;
}

.detail-pagination {
  display: flex;
  justify-content: flex-end;
  border-top: 1px solid #edf2f7;
  padding: 12px 14px 14px;
}

:deep(.dispatch-alert-dialog .el-dialog__body) {
  max-height: min(72vh, 720px);
  overflow: auto;
  padding-top: 8px;
}

:deep(.dispatch-alert-dialog .el-dialog) {
  border-radius: 14px;
}

:deep(.dispatch-alert-dialog .el-dialog__header) {
  padding: 18px 22px 8px;
}

:deep(.dispatch-alert-dialog .el-dialog__title) {
  color: #0f172a;
  font-size: 18px;
  font-weight: 900;
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

  .detail-hero,
  .detail-kpi-grid {
    grid-template-columns: 1fr;
  }

  .reason-item {
    grid-template-columns: 1fr;
  }

  .reason-count {
    justify-self: start;
  }
}
</style>
