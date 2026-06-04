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

    <DataState :loading="loading" :empty="!filteredRows.length" empty-text="暂无发布排期">
      <el-table :data="filteredRows" border table-layout="fixed" class="schedule-table">
        <el-table-column label="排期" width="90">
          <template #default="scope">#{{ scope.row.id }}</template>
        </el-table-column>
        <el-table-column label="文章/品牌" min-width="150">
          <template #default="scope">
            <div class="schedule-stack">
              <button type="button" class="schedule-link" @click="emit('openArticle', scope.row.articleId)">文章 #{{ scope.row.articleId }}</button>
              <span>品牌 #{{ scope.row.brandId }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="平台账号" min-width="150">
          <template #default="scope">
            <div class="schedule-stack">
              <span>{{ platformLabel(scope.row.platform) }}</span>
              <span>账号 #{{ scope.row.selfMediaAccountId }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="发布时间" min-width="190">
          <template #default="scope">
            <div class="schedule-stack">
              <span>计划 {{ timeText(scope.row.plannedPublishAt) }}</span>
              <span>平台 {{ timeText(scope.row.platformScheduledAt) }}</span>
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
              <el-tag size="small" :type="healthTag(scope.row)">{{ healthLabel(scope.row) }}</el-tag>
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
} from '@/api/content'
import type { SelfMediaPublishSchedule } from '@/types'
import { formatDateTime } from '@/utils/format'

type ScheduleHealth = 'failed' | 'overdue' | 'locked' | 'waiting' | 'scheduled' | 'done' | 'cancelled'

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
  { label: '待处理', value: 'pending' },
  { label: '已领取', value: 'claimed' },
  { label: '已填充待核验', value: 'filled_pending_verify' },
  { label: '排期中', value: 'scheduling' },
  { label: '已定时', value: 'scheduled' },
  { label: '发布待确认', value: 'publish_check_unknown' },
  { label: '已确认发布', value: 'published_confirmed' },
  { label: '失败', value: 'failed' },
  { label: '已取消', value: 'cancelled' },
  { label: '平台已取消', value: 'platform_cancelled' },
]

const scheduleHealthOptions: Array<{ label: string; value: ScheduleHealth }> = [
  { label: '失败', value: 'failed' },
  { label: '超时待处理', value: 'overdue' },
  { label: '执行中', value: 'locked' },
  { label: '待执行', value: 'waiting' },
  { label: '平台已定时', value: 'scheduled' },
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
    overdue: 0,
    locked: 0,
    waiting: 0,
    scheduled: 0,
    done: 0,
    cancelled: 0,
  })
  return [
    { label: '失败', value: 'failed' as ScheduleHealth, count: counts.failed, hint: '需要人工处理', tone: 'danger' },
    { label: '超时', value: 'overdue' as ScheduleHealth, count: counts.overdue, hint: '已到处理时间', tone: 'warning' },
    { label: '执行中', value: 'locked' as ScheduleHealth, count: counts.locked, hint: '助手已领取', tone: 'primary' },
    { label: '待执行', value: 'waiting' as ScheduleHealth, count: counts.waiting, hint: '等待下次轮询', tone: 'info' },
    { label: '已定时', value: 'scheduled' as ScheduleHealth, count: counts.scheduled, hint: '等待平台发布', tone: 'success' },
    { label: '完成', value: 'done' as ScheduleHealth, count: counts.done, hint: '已确认发布', tone: 'success' },
    { label: '取消', value: 'cancelled' as ScheduleHealth, count: counts.cancelled, hint: '不再执行', tone: 'muted' },
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
  if (status === 'pending' || status === 'claimed' || status === 'filled_pending_verify' || status === 'scheduling' || status === 'publish_check_unknown') return 'warning'
  if (status === 'failed') return 'danger'
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
  if (['failed', 'cancelled', 'platform_cancelled', 'published_confirmed', 'scheduled'].includes(row.status)) return false
  if (isLocked(row)) return false
  const nextAttemptAt = timeMs(row.nextAttemptAt)
  return nextAttemptAt !== null && nextAttemptAt <= Date.now()
}

function health(row: SelfMediaPublishSchedule): ScheduleHealth {
  if (row.status === 'failed') return 'failed'
  if (row.status === 'published_confirmed') return 'done'
  if (row.status === 'cancelled' || row.status === 'platform_cancelled') return 'cancelled'
  if (row.status === 'scheduled' || row.status === 'publish_check_unknown') return 'scheduled'
  if (isLocked(row)) return 'locked'
  if (isOverdue(row)) return 'overdue'
  return 'waiting'
}

function healthLabel(row: SelfMediaPublishSchedule) {
  return scheduleHealthOptions.find((item) => item.value === health(row))?.label || '-'
}

function healthTag(row: SelfMediaPublishSchedule): 'success' | 'warning' | 'danger' | 'info' {
  const value = health(row)
  if (value === 'failed') return 'danger'
  if (value === 'overdue') return 'warning'
  if (value === 'done' || value === 'scheduled') return 'success'
  return 'info'
}

function stageLabel(row: SelfMediaPublishSchedule) {
  const map: Record<string, string> = {
    pending: '等待助手领取',
    claimed: '助手已领取',
    filled_pending_verify: '内容填充待核验',
    scheduling: '平台定时设置中',
    scheduled: '平台已定时',
    publish_check_unknown: '等待最终发布确认',
    published_confirmed: '发布已确认',
    failed: '执行失败',
    cancelled: '后台已取消',
    platform_cancelled: '平台已取消',
  }
  return map[row.status] || row.status || '-'
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

function canCancel(row: SelfMediaPublishSchedule) {
  return props.canPublish && !['cancelled', 'platform_cancelled', 'published_confirmed', 'failed'].includes(row.status)
}

function canConfirmPublished(row: SelfMediaPublishSchedule) {
  return props.canPublish && ['scheduled', 'publish_check_unknown', 'failed'].includes(row.status)
}

function canConfirmFailed(row: SelfMediaPublishSchedule) {
  return props.canPublish && !['cancelled', 'platform_cancelled', 'published_confirmed', 'failed'].includes(row.status)
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

function showDiagnostics(row: SelfMediaPublishSchedule) {
  const diagnostics = row.diagnosticsJson ? formatDiagnosticsJson(row.diagnosticsJson) : '暂无诊断信息'
  ElMessageBox.alert(diagnosticsText(row, diagnostics), `排期 #${row.id} 诊断`, {
    confirmButtonText: '关闭',
    customClass: 'schedule-diagnostics-dialog',
  })
}

function diagnosticsText(row: SelfMediaPublishSchedule, diagnostics: string) {
  return [
    `健康：${healthLabel(row)}`,
    `阶段：${stageLabel(row)}`,
    `状态：${statusLabel(row.status)}（${row.status || '-'}）`,
    `计划发布时间：${timeText(row.plannedPublishAt)}`,
    `平台定时时间：${timeText(row.platformScheduledAt)}`,
    `下次处理：${timeText(row.nextAttemptAt)}`,
    `锁定至：${timeText(row.lockedUntil)}`,
    `尝试次数：${attemptText(row)}`,
    `异常：${failureText(row)}`,
    '',
    diagnostics,
  ].join('\n')
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

:global(.schedule-diagnostics-dialog) {
  width: min(680px, calc(100vw - 40px));
}

:global(.schedule-diagnostics-dialog .el-message-box__message) {
  max-height: 420px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: "JetBrains Mono", "Consolas", monospace;
  font-size: 12px;
  line-height: 1.6;
}
</style>
