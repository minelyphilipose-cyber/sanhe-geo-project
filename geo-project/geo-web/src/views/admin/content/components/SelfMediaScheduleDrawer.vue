<template>
  <el-drawer v-model="visible" title="自媒体发布排期" size="84%" class="schedule-drawer">
    <div class="schedule-toolbar">
      <el-input v-model="query.brandId" class="schedule-filter" clearable placeholder="品牌 ID" @keyup.enter="search" />
      <el-input v-model="query.articleId" class="schedule-filter" clearable placeholder="文章 ID" @keyup.enter="search" />
      <el-input v-model="query.selfMediaAccountId" class="schedule-filter" clearable placeholder="账号 ID" @keyup.enter="search" />
      <el-select v-model="query.platform" class="schedule-filter" clearable placeholder="平台">
        <el-option label="今日头条" value="toutiao" />
        <el-option label="知乎" value="zhihu" />
        <el-option label="小红书" value="xiaohongshu" />
      </el-select>
      <el-select v-model="query.status" class="schedule-filter" clearable placeholder="状态">
        <el-option v-for="item in scheduleStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-select v-model="query.health" class="schedule-filter" clearable placeholder="健康">
        <el-option v-for="item in scheduleHealthOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="search">查询</el-button>
      <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
    </div>

    <div class="schedule-health-grid">
      <button
        v-for="item in scheduleHealthCards"
        :key="item.value"
        type="button"
        class="schedule-health-card"
        :class="[`is-${item.tone}`, { selected: query.health === item.value }]"
        @click="toggleHealthFilter(item.value)"
      >
        <span class="schedule-health-label">{{ item.label }}</span>
        <strong class="schedule-health-value">{{ item.count }}</strong>
        <span class="schedule-health-hint">{{ item.hint }}</span>
      </button>
    </div>

    <div v-if="alertOverview.total" class="schedule-alert-overview">
      <span class="schedule-alert-overview-title">异常概览</span>
      <el-tag v-if="alertOverview.critical" type="danger" size="small">严重 {{ alertOverview.critical }}</el-tag>
      <el-tag v-if="alertOverview.warning" type="warning" size="small">警告 {{ alertOverview.warning }}</el-tag>
      <el-tag v-if="alertOverview.info" type="info" size="small">提示 {{ alertOverview.info }}</el-tag>
    </div>

    <DataState :loading="loading" :empty="!filteredRows.length" empty-text="暂无发布排期">
      <el-table :data="filteredRows" border table-layout="fixed" class="schedule-table">
        <el-table-column label="排期" width="90">
          <template #default="scope">#{{ scope.row.id }}</template>
        </el-table-column>
        <el-table-column label="文章/品牌" min-width="150">
          <template #default="scope">
            <div class="schedule-stack">
              <button type="button" class="schedule-link" @click="emit('openArticle', scope.row.articleId)">文章 #{{ scope.row.articleId }}</button>
              <span>{{ brandDisplay(scope.row) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="平台账号" min-width="150">
          <template #default="scope">
            <div class="schedule-stack">
              <span>{{ platformLabel(scope.row.platform) }}</span>
              <span>{{ accountDisplay(scope.row) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="发布时间" min-width="190">
          <template #default="scope">
            <div class="schedule-stack">
              <span>计划 {{ timeText(scope.row.plannedPublishAt) }}</span>
              <span>{{ platformTimeLine(scope.row) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="执行状态" min-width="150">
          <template #default="scope">
            <div class="schedule-stack">
              <el-tag size="small" :type="statusTag(scope.row.status)">{{ statusLabel(scope.row.status) }}</el-tag>
              <span>{{ attemptText(scope.row) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="健康/阶段" min-width="170">
          <template #default="scope">
            <div class="schedule-stack">
              <div class="schedule-tag-row">
                <el-tag size="small" :type="healthTag(scope.row)">{{ healthLabel(scope.row) }}</el-tag>
                <el-tag v-if="activeAlertCount(scope.row)" size="small" :type="alertTag(scope.row)">
                  告警 {{ activeAlertCount(scope.row) }}
                </el-tag>
              </div>
              <span>{{ stageLabel(scope.row) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="下次处理" min-width="170">
          <template #default="scope">
            <div class="schedule-stack">
              <span>{{ timeText(scope.row.nextAttemptAt) }}</span>
              <span v-if="scope.row.lockedUntil">锁定至 {{ timeText(scope.row.lockedUntil) }}</span>
              <span v-if="delayText(scope.row)" class="schedule-delay">{{ delayText(scope.row) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="异常信息" min-width="220" show-overflow-tooltip>
          <template #default="scope">{{ failureText(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right" align="center">
          <template #default="scope">
            <div class="schedule-actions">
              <el-button link type="primary" @click="showDiagnostics(scope.row)">诊断</el-button>
              <el-button v-if="canRecheck(scope.row)" link type="primary" @click="recheck(scope.row)">重新校验</el-button>
              <el-button v-if="canConfirmPublished(scope.row)" link type="success" @click="confirmPublished(scope.row)">确认发布</el-button>
              <el-button v-if="canConfirmFailed(scope.row)" link type="warning" @click="confirmFailed(scope.row)">确认失败</el-button>
              <el-button v-if="canCancel(scope.row)" link type="danger" @click="cancel(scope.row)">取消</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <div class="schedule-pagination">
        <el-pagination
          v-model:current-page="page.current"
          v-model:page-size="page.size"
          background
          layout="total, sizes, prev, pager, next"
          :page-sizes="[10, 20, 50, 100]"
          :total="page.total"
          @current-change="load"
          @size-change="handleSizeChange"
        />
      </div>
    </DataState>

    <el-dialog
      v-model="diagnosticsVisible"
      :title="diagnosticsRow ? `排期 #${diagnosticsRow.id} 诊断` : '排期诊断'"
      width="680px"
      class="schedule-diagnostics-dialog"
    >
      <div v-if="diagnosticsRow" class="schedule-diagnostics">
        <section class="schedule-diagnostics-section">
          <h4>基础状态</h4>
          <dl class="schedule-diagnostics-grid">
            <template v-for="item in diagnosticsFields" :key="item.label">
              <dt>{{ item.label }}</dt>
              <dd>{{ item.value }}</dd>
            </template>
          </dl>
        </section>

        <section v-if="diagnosticsRow.activeAlerts?.length" class="schedule-diagnostics-section">
          <h4>活动告警</h4>
          <div class="schedule-diagnostics-alerts">
            <div v-for="alert in diagnosticsRow.activeAlerts" :key="alert.id" class="schedule-diagnostics-alert">
              <el-tag size="small" :type="alert.severity === 'critical' ? 'danger' : alert.severity === 'warning' ? 'warning' : 'info'">
                {{ alertSeverityLabel(alert.severity) }}
              </el-tag>
              <span>{{ alertTypeLabel(alert.alertType) }}：{{ alert.message }}</span>
            </div>
          </div>
        </section>

        <section class="schedule-diagnostics-section">
          <h4>处理建议</h4>
          <p class="schedule-diagnostics-advice">{{ recommendationText(diagnosticsRow) }}</p>
        </section>

        <section class="schedule-diagnostics-section">
          <h4>原始诊断</h4>
          <pre class="schedule-diagnostics-json">{{ diagnosticsJsonText }}</pre>
        </section>
      </div>
    </el-dialog>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import DataState from '@/components/ui/DataState.vue'
import {
  cancelSelfMediaPublishSchedule,
  confirmSelfMediaPublishScheduleFailed,
  confirmSelfMediaPublishSchedulePublished,
  getSelfMediaPublishSchedules,
  recheckSelfMediaPublishScheduleResult,
} from '@/api/content'
import type { SelfMediaPublishSchedule } from '@/types'
import { formatDateTime } from '@/utils/format'

type ScheduleHealth = 'failed' | 'manual' | 'overdue' | 'running' | 'waiting' | 'scheduled' | 'checking' | 'done' | 'cancelled'

const props = defineProps<{
  modelValue: boolean
  canPublish: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  openArticle: [articleId: number]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const loading = ref(false)
const rows = ref<SelfMediaPublishSchedule[]>([])
const diagnosticsVisible = ref(false)
const diagnosticsRow = ref<SelfMediaPublishSchedule | null>(null)
const diagnosticsJsonText = ref('暂无诊断信息')
const page = reactive({ current: 1, size: 20, total: 0 })
const query = reactive({
  brandId: '',
  articleId: '',
  selfMediaAccountId: '',
  platform: '',
  status: '',
  health: '' as '' | ScheduleHealth,
})

const scheduleStatusOptions = [
  { label: '待领取', value: 'pending' },
  { label: '助手填充中', value: 'filling' },
  { label: '填充已核验', value: 'filled_verified' },
  { label: '平台定时中', value: 'scheduling' },
  { label: '已定时', value: 'scheduled' },
  { label: '到点待核验', value: 'publish_due' },
  { label: '发布结果核验中', value: 'checking_publish_result' },
  { label: '发布待确认', value: 'publish_unknown' },
  { label: '已确认发布', value: 'published_confirmed' },
  { label: '定时失败', value: 'schedule_failed' },
  { label: '发布失败', value: 'publish_failed' },
  { label: '需人工处理', value: 'manual_required' },
  { label: '已转半自动', value: 'routed_to_semi_auto' },
  { label: '取消待平台处理', value: 'cancel_pending_platform' },
  { label: '已取消', value: 'cancelled' },
]

const scheduleHealthOptions: Array<{ label: string; value: ScheduleHealth }> = [
  { label: '失败', value: 'failed' },
  { label: '人工处理', value: 'manual' },
  { label: '超时待处理', value: 'overdue' },
  { label: '执行中', value: 'running' },
  { label: '待执行', value: 'waiting' },
  { label: '平台已定时', value: 'scheduled' },
  { label: '发布待确认', value: 'checking' },
  { label: '已完成', value: 'done' },
  { label: '已取消', value: 'cancelled' },
]

const filteredRows = computed(() => {
  if (!query.health) return rows.value
  return rows.value.filter((row) => health(row) === query.health)
})

const scheduleHealthCards = computed(() => {
  const counts = rows.value.reduce<Record<ScheduleHealth, number>>((acc, row) => {
    acc[health(row)] += 1
    return acc
  }, {
    failed: 0,
    manual: 0,
    overdue: 0,
    running: 0,
    waiting: 0,
    scheduled: 0,
    checking: 0,
    done: 0,
    cancelled: 0,
  })
  return [
    { label: '失败', value: 'failed' as ScheduleHealth, count: counts.failed, hint: '执行或发布失败', tone: 'danger' },
    { label: '人工', value: 'manual' as ScheduleHealth, count: counts.manual, hint: '需人工介入', tone: 'danger' },
    { label: '超时', value: 'overdue' as ScheduleHealth, count: counts.overdue, hint: '已到处理时间', tone: 'warning' },
    { label: '执行中', value: 'running' as ScheduleHealth, count: counts.running, hint: '助手或平台处理中', tone: 'primary' },
    { label: '待执行', value: 'waiting' as ScheduleHealth, count: counts.waiting, hint: '等待下次轮询', tone: 'info' },
    { label: '已定时', value: 'scheduled' as ScheduleHealth, count: counts.scheduled, hint: '等待平台发布', tone: 'success' },
    { label: '待确认', value: 'checking' as ScheduleHealth, count: counts.checking, hint: '到点后核验发布', tone: 'warning' },
    { label: '完成', value: 'done' as ScheduleHealth, count: counts.done, hint: '已确认发布', tone: 'success' },
    { label: '取消', value: 'cancelled' as ScheduleHealth, count: counts.cancelled, hint: '不再执行', tone: 'muted' },
  ]
})

const alertOverview = computed(() => {
  return rows.value.flatMap((row) => row.activeAlerts || []).reduce((acc, alert) => {
    acc.total += 1
    if (alert.severity === 'critical') acc.critical += 1
    else if (alert.severity === 'warning') acc.warning += 1
    else acc.info += 1
    return acc
  }, { total: 0, critical: 0, warning: 0, info: 0 })
})

const diagnosticsFields = computed(() => {
  const row = diagnosticsRow.value
  if (!row) return []
  return [
    { label: '健康', value: healthLabel(row) },
    { label: '阶段', value: stageLabel(row) },
    { label: '状态', value: `${statusLabel(row.status)}（${row.status || '-'}）` },
    { label: '计划发布时间', value: timeText(row.plannedPublishAt) },
    { label: platformTimeFieldLabel(row), value: platformTimeFieldValue(row) },
    { label: '队列', value: row.queueKind || '-' },
    { label: '请求', value: `${row.requestId || '-'} / ${row.requestIdempotencyKey || '-'}` },
    { label: '浏览器环境', value: `${row.browserEnvironmentId || '-'} / 绑定 ${row.browserEnvironmentAccountId || '-'}` },
    { label: '平台排期 ID', value: platformScheduleIdFieldValue(row) },
    { label: '平台发布 ID', value: row.platformPublishId || '-' },
    { label: '平台发布链接', value: row.platformPublishedUrl || '-' },
    { label: '下次处理', value: timeText(row.nextAttemptAt) },
    { label: '锁定至', value: timeText(row.lockedUntil) },
    { label: '尝试次数', value: attemptText(row) },
    { label: '异常', value: failureText(row) },
  ]
})

watch(() => props.modelValue, (opened) => {
  if (opened) {
    load()
  }
})

function positiveNumberInput(value: string) {
  const text = value.trim()
  if (!text) return undefined
  const num = Number(text)
  return Number.isInteger(num) && num > 0 ? num : undefined
}

function queryParams() {
  return {
    brandId: positiveNumberInput(query.brandId),
    articleId: positiveNumberInput(query.articleId),
    selfMediaAccountId: positiveNumberInput(query.selfMediaAccountId),
    platform: query.platform || undefined,
    status: query.status || undefined,
    current: page.current,
    size: page.size,
  }
}

async function load() {
  loading.value = true
  try {
    const res = await getSelfMediaPublishSchedules(queryParams())
    const data = res.data.data
    rows.value = data.records || []
    page.current = data.current
    page.size = data.size
    page.total = data.total
  } finally {
    loading.value = false
  }
}

function search() {
  page.current = 1
  load()
}

function resetQuery() {
  query.brandId = ''
  query.articleId = ''
  query.selfMediaAccountId = ''
  query.platform = ''
  query.status = ''
  query.health = ''
  search()
}

function handleSizeChange(size: number) {
  page.size = size
  page.current = 1
  load()
}

function statusLabel(status?: string | null) {
  return scheduleStatusOptions.find((item) => item.value === status)?.label || status || '-'
}

function statusTag(status?: string | null): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'scheduled' || status === 'published_confirmed') return 'success'
  if (['pending', 'filling', 'filled_verified', 'scheduling', 'publish_due', 'checking_publish_result', 'publish_unknown', 'cancel_pending_platform'].includes(status || '')) return 'warning'
  if (['schedule_failed', 'publish_failed', 'manual_required'].includes(status || '')) return 'danger'
  return 'info'
}

function toggleHealthFilter(value: ScheduleHealth) {
  query.health = query.health === value ? '' : value
}

function timeText(value?: string | null) {
  return value ? formatDateTime(value) : '-'
}

function timeMs(value?: string | null) {
  if (!value) return null
  const normalized = value.includes('T') ? value : value.replace(' ', 'T')
  const time = new Date(normalized).getTime()
  return Number.isNaN(time) ? null : time
}

function isLocked(row: SelfMediaPublishSchedule) {
  const lockedUntil = timeMs(row.lockedUntil)
  return lockedUntil !== null && lockedUntil > Date.now()
}

function isOverdue(row: SelfMediaPublishSchedule) {
  if (['schedule_failed', 'publish_failed', 'manual_required', 'routed_to_semi_auto', 'cancelled', 'published_confirmed', 'scheduled'].includes(row.status)) return false
  if (isLocked(row)) return false
  const nextAttemptAt = timeMs(row.nextAttemptAt)
  return nextAttemptAt !== null && nextAttemptAt <= Date.now()
}

function health(row: SelfMediaPublishSchedule): ScheduleHealth {
  if (row.status === 'schedule_failed' || row.status === 'publish_failed') return 'failed'
  if (row.status === 'manual_required' || row.status === 'routed_to_semi_auto') return 'manual'
  if (row.status === 'published_confirmed') return 'done'
  if (row.status === 'cancelled') return 'cancelled'
  if (row.status === 'scheduled') return 'scheduled'
  if (row.status === 'publish_due' || row.status === 'publish_unknown' || row.status === 'cancel_pending_platform') return 'checking'
  if (isLocked(row) || row.status === 'filling' || row.status === 'scheduling' || row.status === 'checking_publish_result') return 'running'
  if (isOverdue(row)) return 'overdue'
  return 'waiting'
}

function healthLabel(row: SelfMediaPublishSchedule) {
  return scheduleHealthOptions.find((item) => item.value === health(row))?.label || '-'
}

function healthTag(row: SelfMediaPublishSchedule): 'success' | 'warning' | 'danger' | 'info' {
  const value = health(row)
  if (value === 'failed' || value === 'manual') return 'danger'
  if (value === 'overdue' || value === 'checking') return 'warning'
  if (value === 'done' || value === 'scheduled') return 'success'
  return 'info'
}

function activeAlertCount(row: SelfMediaPublishSchedule) {
  return row.activeAlerts?.length || 0
}

function alertTag(row: SelfMediaPublishSchedule): 'danger' | 'warning' | 'info' {
  const alerts = row.activeAlerts || []
  if (alerts.some((item) => item.severity === 'critical')) return 'danger'
  if (alerts.some((item) => item.severity === 'warning')) return 'warning'
  return 'info'
}

function stageLabel(row: SelfMediaPublishSchedule) {
  const map: Record<string, string> = {
    pending: '等待助手领取',
    filling: '助手填充中',
    filled_verified: '内容填充已核验',
    scheduling: '平台定时设置中',
    scheduled: '平台已定时',
    publish_due: '到点待核验',
    checking_publish_result: '发布结果核验中',
    publish_unknown: '等待最终发布确认',
    published_confirmed: '发布已确认',
    schedule_failed: '定时设置失败',
    publish_failed: '发布结果失败',
    manual_required: '需要人工处理',
    routed_to_semi_auto: '已转半自动',
    cancel_pending_platform: '等待平台取消确认',
    cancelled: '后台已取消',
  }
  if (isBackendDelayedPlatform(row.platform)) {
    const backendDelayedMap: Record<string, string> = {
      pending: '等待助手领取',
      filling: '助手即时发布中',
      filled_verified: '内容填充已核验',
      scheduling: '发布提交中',
      scheduled: '已提交平台',
      publish_due: '待确认发布结果',
      checking_publish_result: '发布结果核验中',
      publish_unknown: '等待最终发布确认',
      published_confirmed: '发布已确认',
      schedule_failed: '发布提交失败',
      publish_failed: '发布结果失败',
      manual_required: '需要人工处理',
      routed_to_semi_auto: '已转半自动',
      cancel_pending_platform: '等待平台取消确认',
      cancelled: '后台已取消',
    }
    return backendDelayedMap[row.status] || row.status || '-'
  }
  return map[row.status] || row.status || '-'
}

function isBackendDelayedPlatform(platform?: string | null) {
  return platform === 'zhihu'
}

function platformTimeLine(row: SelfMediaPublishSchedule) {
  if (isBackendDelayedPlatform(row.platform)) {
    return `触发 ${timeText(row.nextAttemptAt || row.plannedPublishAt)}`
  }
  return `平台 ${timeText(row.platformScheduledAt)}`
}

function platformTimeFieldLabel(row: SelfMediaPublishSchedule) {
  return isBackendDelayedPlatform(row.platform) ? '后台触发时间' : '平台定时时间'
}

function platformTimeFieldValue(row: SelfMediaPublishSchedule) {
  if (isBackendDelayedPlatform(row.platform)) return timeText(row.plannedPublishAt)
  return timeText(row.platformScheduledAt)
}

function platformScheduleIdFieldValue(row: SelfMediaPublishSchedule) {
  if (isBackendDelayedPlatform(row.platform)) return '不适用'
  return row.platformScheduleId || '-'
}

function delayText(row: SelfMediaPublishSchedule) {
  if (!isOverdue(row)) return ''
  const nextAttemptAt = timeMs(row.nextAttemptAt)
  if (nextAttemptAt === null) return ''
  const minutes = Math.max(0, Math.floor((Date.now() - nextAttemptAt) / 60000))
  return minutes > 0 ? `已超时 ${minutes} 分钟` : '已到处理时间'
}

function attemptText(row: SelfMediaPublishSchedule) {
  const attempt = row.attemptCount ?? 0
  const max = row.maxAttempts ?? 0
  return max > 0 ? `尝试 ${attempt}/${max}` : `尝试 ${attempt}`
}

function failureText(row: SelfMediaPublishSchedule) {
  if (row.failureCode && row.failureMessage) return `${row.failureCode}：${row.failureMessage}`
  return row.failureMessage || row.failureCode || row.scheduleDriftReason || '-'
}

function brandDisplay(row: SelfMediaPublishSchedule) {
  return row.brandName || '未命名品牌'
}

function accountDisplay(row: SelfMediaPublishSchedule) {
  return row.selfMediaAccountName || '未命名账号'
}

function canCancel(row: SelfMediaPublishSchedule) {
  return props.canPublish && !['cancelled', 'published_confirmed', 'schedule_failed', 'publish_failed', 'manual_required', 'routed_to_semi_auto'].includes(row.status)
}

function canConfirmPublished(row: SelfMediaPublishSchedule) {
  return props.canPublish && ['scheduled', 'publish_due', 'checking_publish_result', 'publish_unknown', 'publish_failed'].includes(row.status)
}

function canConfirmFailed(row: SelfMediaPublishSchedule) {
  return props.canPublish && !['cancelled', 'published_confirmed', 'schedule_failed', 'publish_failed', 'manual_required', 'routed_to_semi_auto'].includes(row.status)
}

function canRecheck(row: SelfMediaPublishSchedule) {
  return props.canPublish && ['scheduled', 'publish_due', 'checking_publish_result', 'publish_unknown', 'publish_failed', 'manual_required'].includes(row.status)
}

async function cancel(row: SelfMediaPublishSchedule) {
  try {
    const result = await ElMessageBox.prompt(`确认取消排期 #${row.id}？`, '取消发布排期', {
      confirmButtonText: '取消排期',
      cancelButtonText: '返回',
      inputPlaceholder: '取消原因，可选',
      confirmButtonClass: 'el-button--danger',
    })
    await cancelSelfMediaPublishSchedule(row.id, { reason: result.value || undefined })
    ElMessage.success('排期已取消')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      throw error
    }
  }
}

async function confirmPublished(row: SelfMediaPublishSchedule) {
  try {
    const result = await ElMessageBox.prompt(`确认排期 #${row.id} 已在平台发布？`, '人工确认发布', {
      confirmButtonText: '确认发布',
      cancelButtonText: '返回',
      inputPlaceholder: '平台发布链接，可选',
    })
    await confirmSelfMediaPublishSchedulePublished(row.id, { platformPublishedUrl: result.value || undefined })
    ElMessage.success('已确认发布')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      throw error
    }
  }
}

async function confirmFailed(row: SelfMediaPublishSchedule) {
  try {
    const result = await ElMessageBox.prompt(`确认排期 #${row.id} 失败？`, '人工确认失败', {
      confirmButtonText: '确认失败',
      cancelButtonText: '返回',
      inputPlaceholder: '失败原因',
      inputValidator: (value) => Boolean(value?.trim()) || '请填写失败原因',
    })
    await confirmSelfMediaPublishScheduleFailed(row.id, {
      failureCode: 'MANUAL_CONFIRMED_FAILED',
      failureMessage: result.value.trim(),
    })
    ElMessage.success('已确认失败')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      throw error
    }
  }
}

async function recheck(row: SelfMediaPublishSchedule) {
  try {
    await ElMessageBox.confirm(`确认重新校验排期 #${row.id} 的平台发布结果？`, '重新校验发布结果', {
      confirmButtonText: '重新校验',
      cancelButtonText: '返回',
      type: 'warning',
    })
    await recheckSelfMediaPublishScheduleResult(row.id)
    ElMessage.success('已加入发布结果校验队列')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      throw error
    }
  }
}

function showDiagnostics(row: SelfMediaPublishSchedule) {
  diagnosticsRow.value = row
  diagnosticsJsonText.value = row.diagnosticsJson ? formatDiagnosticsJson(row.diagnosticsJson) : '暂无诊断信息'
  diagnosticsVisible.value = true
}

function alertSeverityLabel(value?: string | null) {
  if (value === 'critical') return '严重'
  if (value === 'warning') return '警告'
  return '提示'
}

function alertTypeLabel(value?: string | null) {
  const map: Record<string, string> = {
    HELPER_OFFLINE: '助手离线',
    SCHEDULE_FILL_OVERDUE: '填充超时',
    TASK_STUCK_RUNNING: '执行卡住',
    PLATFORM_SCHEDULE_MISSED: '平台发布时间已过',
    PUBLISH_RESULT_UNKNOWN: '发布待确认',
    PUBLISH_LINK_MISSING: '发布链接缺失',
    MANUAL_REQUIRED: '人工处理',
    SCHEDULE_FAILED: '定时失败',
    PUBLISH_FAILED: '发布失败',
  }
  return value ? map[value] || value : '-'
}

function recommendationText(row: SelfMediaPublishSchedule) {
  if (isBackendDelayedPlatform(row.platform)) {
    if (row.status === 'pending') return '等待本地助手到点领取；该平台不支持平台内定时，计划时间即后台触发发布时间。'
    if (row.status === 'filling') return '本地助手正在填充并提交发布；若长时间不变化，请检查 AdsPower 页面和扩展日志。'
    if (row.status === 'published_confirmed') return '已确认发布，无需处理。'
    if (row.status === 'manual_required') return '按异常信息处理页面或配置问题；处理后重新创建排期。'
  }
  if (row.status === 'publish_unknown') return '等待自动复查；若长时间未变化，可点击“重新校验”或人工确认发布。'
  if (row.status === 'publish_failed') return '检查本地助手、AdsPower 浏览器和头条作品管理页；修复后点击“重新校验”。'
  if (row.status === 'manual_required') return '按异常信息处理配置或页面问题；处理后可点击“重新校验”或重新创建排期。'
  if (row.status === 'checking_publish_result') return '本地助手正在校验作品管理页；若锁定超时仍无变化，可重新校验。'
  if (row.status === 'scheduled') return '等待平台发布时间，到点后本地助手会自动校验发布结果。'
  if (row.status === 'cancel_pending_platform') return '已提交后台取消，仍需在平台侧确认是否需要人工撤销。'
  if (row.status === 'published_confirmed') return '无需处理。'
  return row.failureCode || row.failureMessage ? '根据异常信息修复后重试或人工确认。' : '暂无额外操作建议。'
}

function formatDiagnosticsJson(value: string) {
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

function platformLabel(value?: string | null) {
  const map: Record<string, string> = {
    wechat_mp: '微信公众号',
    douyin: '抖音图文',
    toutiao: '今日头条',
    zhihu: '知乎',
    xiaohongshu: '小红书',
  }
  return value ? map[value] || value : '-'
}
</script>

<style scoped>
.schedule-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.schedule-filter {
  width: 150px;
}

.schedule-health-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(128px, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.schedule-alert-overview {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: -4px 0 14px;
  padding: 10px 12px;
  background: #fff7ed;
  border: 1px solid #fed7aa;
  border-radius: 8px;
}

.schedule-alert-overview-title {
  color: #9a3412;
  font-size: 13px;
  font-weight: 700;
}

.schedule-health-card {
  display: grid;
  gap: 5px;
  min-height: 86px;
  padding: 12px 14px;
  text-align: left;
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.16s ease, box-shadow 0.16s ease, transform 0.16s ease;
}

.schedule-health-card:hover,
.schedule-health-card.selected {
  border-color: #3b82f6;
  box-shadow: 0 8px 22px rgba(37, 99, 235, 0.12);
  transform: translateY(-1px);
}

.schedule-health-card.is-danger {
  background: #fef2f2;
  border-color: #fecaca;
}

.schedule-health-card.is-warning {
  background: #fffbeb;
  border-color: #fde68a;
}

.schedule-health-card.is-primary {
  background: #eff6ff;
  border-color: #bfdbfe;
}

.schedule-health-card.is-success {
  background: #ecfdf5;
  border-color: #bbf7d0;
}

.schedule-health-label,
.schedule-health-hint {
  color: #64748b;
  font-size: 12px;
}

.schedule-health-value {
  color: #0f172a;
  font-size: 24px;
  line-height: 1;
}

.schedule-table {
  width: 100%;
}

.schedule-stack {
  display: grid;
  gap: 4px;
  min-width: 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.45;
}

.schedule-stack > span:first-child {
  color: #0f172a;
  font-size: 13px;
  font-weight: 650;
}

.schedule-tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.schedule-link {
  display: inline;
  width: fit-content;
  padding: 0;
  color: #2563eb;
  background: transparent;
  border: 0;
  font: inherit;
  font-size: 13px;
  font-weight: 650;
  cursor: pointer;
}

.schedule-link:hover {
  color: #1d4ed8;
}

.schedule-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 2px 8px;
}

.schedule-delay {
  color: #d97706;
  font-weight: 650;
}

.schedule-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.schedule-diagnostics {
  display: grid;
  gap: 14px;
  color: #1f2937;
}

.schedule-diagnostics-section {
  padding: 14px 16px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
}

.schedule-diagnostics-section h4 {
  margin: 0 0 12px;
  color: #111827;
  font-size: 14px;
  font-weight: 700;
}

.schedule-diagnostics-grid {
  display: grid;
  grid-template-columns: 112px minmax(0, 1fr);
  gap: 8px 14px;
  margin: 0;
  font-size: 13px;
  line-height: 1.5;
}

.schedule-diagnostics-grid dt {
  color: #6b7280;
}

.schedule-diagnostics-grid dd {
  min-width: 0;
  margin: 0;
  color: #111827;
  overflow-wrap: anywhere;
}

.schedule-diagnostics-alerts {
  display: grid;
  gap: 8px;
}

.schedule-diagnostics-alert {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  color: #374151;
  font-size: 13px;
  line-height: 1.5;
}

.schedule-diagnostics-advice {
  margin: 0;
  color: #374151;
  font-size: 13px;
  line-height: 1.6;
}

.schedule-diagnostics-json {
  max-height: 280px;
  margin: 0;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  color: #374151;
  font-family: "JetBrains Mono", "Consolas", monospace;
  font-size: 12px;
  line-height: 1.6;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 12px;
}
</style>
