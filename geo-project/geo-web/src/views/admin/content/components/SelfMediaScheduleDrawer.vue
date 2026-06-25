<template>
  <el-drawer v-model="visible" title="自媒体发布排期" size="84%" class="schedule-drawer">
    <div class="schedule-toolbar">
      <el-input v-model="query.brandId" class="schedule-filter" clearable placeholder="品牌 ID" @keyup.enter="search" />
      <el-input v-model="query.articleId" class="schedule-filter" clearable placeholder="文章 ID" @keyup.enter="search" />
      <el-input v-model="query.selfMediaAccountId" class="schedule-filter" clearable placeholder="账号 ID" @keyup.enter="search" />
      <el-select v-model="query.platform" class="schedule-filter" clearable placeholder="平台">
        <el-option label="今日头条" value="toutiao" />
        <el-option label="百家号" value="baijiahao" />
        <el-option label="知乎" value="zhihu" />
        <el-option label="小红书" value="xiaohongshu" />
        <el-option label="抖音图文" value="douyin" />
      </el-select>
      <el-select v-model="query.status" class="schedule-filter" clearable placeholder="状态">
        <el-option v-for="item in scheduleStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-input v-model="query.failureCode" class="schedule-filter is-wide" clearable placeholder="失败码" @keyup.enter="search" />
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
              <button type="button" class="schedule-link" @click="emit('openArticle', scope.row.articleId)">{{ articleDisplay(scope.row) }}</button>
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
        <el-table-column label="平台结果" min-width="190">
          <template #default="scope">
            <div class="schedule-stack">
              <div class="schedule-tag-row">
                <el-tag v-if="platformResultStatus(scope.row)" size="small" :type="platformResultTag(scope.row)">
                  {{ platformResultStatus(scope.row) }}
                </el-tag>
                <span v-else>-</span>
              </div>
              <a
                v-if="resolvedPlatformPublishedUrl(scope.row)"
                class="schedule-link"
                :href="resolvedPlatformPublishedUrl(scope.row)"
                target="_blank"
                rel="noopener noreferrer"
              >
                {{ publishedUrlLinkText(scope.row) }}
              </a>
              <span v-else-if="scope.row.platformPublishId">发布 ID {{ scope.row.platformPublishId }}</span>
              <span v-if="scope.row.publishCheckCoverUrl" class="schedule-cover-line">
                <img :src="scope.row.publishCheckCoverUrl" alt="" loading="lazy" />
                已回传封面
              </span>
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
              <el-button v-if="canHandleMaterials(scope.row)" link type="primary" @click="handleMaterials(scope.row)">处理素材</el-button>
              <el-button v-if="canRetryNow(scope.row)" link type="primary" @click="retryNow(scope.row)">立即重试</el-button>
              <el-button v-if="canRecheck(scope.row)" link type="primary" @click="recheck(scope.row)">重新校验</el-button>
              <el-button v-if="canMarkManual(scope.row)" link type="warning" @click="markManual(scope.row)">转人工</el-button>
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
              <dd>
                <a
                  v-if="item.href"
                  class="schedule-link"
                  :href="item.href"
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  {{ item.value }}
                </a>
                <span v-else>{{ item.value }}</span>
              </dd>
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
          <div class="schedule-diagnostics-actions">
            <el-button v-if="canHandleMaterials(diagnosticsRow)" type="primary" @click="handleMaterials(diagnosticsRow)">处理素材</el-button>
            <el-button v-if="canRetryNow(diagnosticsRow)" @click="retryNow(diagnosticsRow)">立即重试</el-button>
          </div>
        </section>

        <section v-if="platformDiagnosticsFields.length" class="schedule-diagnostics-section">
          <h4>平台诊断摘要</h4>
          <dl class="schedule-diagnostics-grid">
            <template v-for="item in platformDiagnosticsFields" :key="item.label">
              <dt>{{ item.label }}</dt>
              <dd>{{ item.value }}</dd>
            </template>
          </dl>
        </section>

        <section v-if="publishVerificationFields.length" class="schedule-diagnostics-section">
          <h4>发布回查结果</h4>
          <dl class="schedule-diagnostics-grid">
            <template v-for="item in publishVerificationFields" :key="item.label">
              <dt>{{ item.label }}</dt>
              <dd>
                <a
                  v-if="item.href"
                  class="schedule-link"
                  :href="item.href"
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  {{ item.value }}
                </a>
                <span v-else>{{ item.value }}</span>
              </dd>
            </template>
          </dl>
        </section>

        <section v-if="failureSnapshotFields.length" class="schedule-diagnostics-section">
          <h4>失败页面快照</h4>
          <dl class="schedule-diagnostics-grid">
            <template v-for="item in failureSnapshotFields" :key="item.label">
              <dt>{{ item.label }}</dt>
              <dd>{{ item.value }}</dd>
            </template>
          </dl>
        </section>

        <section v-if="failureSnapshotInputs.length" class="schedule-diagnostics-section">
          <h4>页面输入框</h4>
          <div class="schedule-diagnostics-list">
            <div v-for="item in failureSnapshotInputs" :key="item.key" class="schedule-diagnostics-list-item">
              <strong>{{ item.title }}</strong>
              <span>{{ item.detail }}</span>
            </div>
          </div>
        </section>

        <section v-if="failureSnapshotActions.length" class="schedule-diagnostics-section">
          <h4>可点击元素</h4>
          <div class="schedule-diagnostics-list is-compact">
            <span v-for="item in failureSnapshotActions" :key="item.key">{{ item.text }}</span>
          </div>
        </section>

        <section v-if="failureSnapshotAdapterState" class="schedule-diagnostics-section">
          <h4>适配器状态</h4>
          <pre class="schedule-diagnostics-json is-compact">{{ failureSnapshotAdapterState }}</pre>
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
  markSelfMediaPublishScheduleManualRequired,
  recheckSelfMediaPublishScheduleResult,
  retrySelfMediaPublishScheduleNow,
} from '@/api/content'
import type { SelfMediaPublishSchedule } from '@/types'
import { formatDateTime } from '@/utils/format'

type ScheduleHealth = 'failed' | 'manual' | 'overdue' | 'running' | 'waiting' | 'scheduled' | 'checking' | 'done' | 'cancelled'

const props = defineProps<{
  modelValue: boolean
  canPublish: boolean
  initialFailureCode?: string | null
  initialStatus?: string | null
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
  failureCode: '',
  health: '' as '' | ScheduleHealth,
})

const scheduleStatusOptions = [
  { label: '待执行', value: 'pending' },
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
    { label: '执行中', value: 'running' as ScheduleHealth, count: counts.running, hint: '系统或平台处理中', tone: 'primary' },
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
    { label: '平台发布链接', value: resolvedPlatformPublishedUrl(row) || '-', href: resolvedPlatformPublishedUrl(row) },
    { label: '下次处理', value: timeText(row.nextAttemptAt) },
    { label: '锁定至', value: timeText(row.lockedUntil) },
    { label: '尝试次数', value: attemptText(row) },
    { label: '失败环节', value: failureStageText(row) || '-' },
    { label: '异常', value: failureText(row) },
  ]
})

const diagnosticsPayload = computed<Record<string, any> | null>(() => {
  const raw = diagnosticsRow.value?.diagnosticsJson
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw)
    return parsed && typeof parsed === 'object' ? parsed : null
  } catch {
    return null
  }
})

const platformDiagnosticsFields = computed(() => {
  const row = diagnosticsRow.value
  const payload = diagnosticsPayload.value
  if (!row || !payload) return []
  const verification = extractPublishVerification(payload)
  const values: Array<{ label: string; value: string }> = []
  const add = (label: string, value: unknown) => {
    const text = diagnosticValueText(value)
    if (text) values.push({ label, value: text })
  }

  add('页面 URL', payload.pageUrl || payload.url || verification?.manageUrl)
  add('页面标题', payload.pageTitle)
  add('目标标题', payload.expectedTitle || payload.targetTitle || verification?.title)
  add('计划时间', payload.scheduledAtText || payload.platformScheduledAt || payload.scheduleProbe || verification?.scheduledAtText)
  add('失败环节', operationStageLabel(payload.operationStage, payload.operationStageLabel))
  add('平台状态', platformPublishStatusLabel(firstText(payload.platformStatus, verification?.pageStatusCode, verification?.platformStatus)))
  add('回查原因', payload.reason)
  add('回查失败码', failureCodeLabel(payload.failureCode) || payload.failureLabel)
  add('匹配标题', payload.hasTitle === undefined ? undefined : (payload.hasTitle ? '已匹配' : '未匹配'))
  add('匹配时间', payload.hasScheduleTime === undefined ? undefined : (payload.hasScheduleTime ? '已匹配' : '未匹配'))
  add('发布信号', payload.hasPublishedSignal === undefined ? undefined : (payload.hasPublishedSignal ? '已检测到' : '未检测到'))
  add('审核信号', payload.hasReviewSignal === undefined ? undefined : (payload.hasReviewSignal ? '已检测到' : '未检测到'))
  add('定时信号', payload.hasScheduledSignal === undefined ? undefined : (payload.hasScheduledSignal ? '已检测到' : '未检测到'))

  const account = payload.account || {}
  add('账号', account.accountNames || account.expectedAccountName)
  add('账号诊断', account.diagnostics)

  const publishUi = payload.publishUi || {}
  add('发布设置页', publishUi.publishSettingsVisible === undefined ? undefined : (publishUi.publishSettingsVisible ? '可见' : '不可见'))
  add('定时控件', publishUi.scheduleEnabled === undefined ? undefined : (publishUi.scheduleEnabled ? '已开启/已填写' : '未开启'))
  add('底部按钮', publishUi.bottomButtons)
  add('最后点击', publishUi.lastTrustedClick || payload.lastTrustedClick)

  add('图片生成', payload.xhsImageGenerating === undefined ? undefined : (payload.xhsImageGenerating ? '生成中' : '已结束'))
  add('缩略图数', payload.xhsThumbnailCount)
  add('可见图片数', payload.xhsVisibleImageCount)
  add('预览页数', payload.xhsPreviewPages)
  return values.slice(0, 18)
})

const publishVerificationFields = computed(() => {
  const row = diagnosticsRow.value
  const payload = diagnosticsPayload.value
  if (!row) return []
  const verification = payload ? extractPublishVerification(payload) : null
  const publishedUrl = resolvedPlatformPublishedUrl(row)
  const values: Array<{ label: string; value: string; href?: string }> = []
  const add = (label: string, value: unknown, href?: unknown) => {
    const text = diagnosticValueText(value)
    if (!text) return
    const link = diagnosticValueText(href)
    values.push({ label, value: text, ...(link ? { href: link } : {}) })
  }

  add('平台发布 ID', firstText(row.platformPublishId, verification?.platformPublishId))
  add('平台发布链接', publishedUrl || '-', publishedUrl)
  add('封面图', firstText(row.publishCheckCoverUrl, verification?.coverImageUrl), firstText(row.publishCheckCoverUrl, verification?.coverImageUrl))
  add('平台状态', platformPublishStatusLabel(firstText(verification?.pageStatusCode, verification?.platformStatus)) || verification?.pageStatus)
  add('平台定时时间', firstText(verification?.scheduledAtText, verification?.platformScheduledAt, row.platformScheduledAt))
  add('作品管理页', verification?.manageUrl, verification?.manageUrl)
  add('匹配标题', verification?.title)
  add('刷新回查', verification?.refreshed === undefined ? undefined : (verification.refreshed ? `已刷新 ${verification.reloadCount || 1} 次` : '未刷新'))
  add('匹配文本', verification?.matchedText)
  return values
})

const failureSnapshot = computed(() => {
  const payload = diagnosticsPayload.value
  if (!payload) return null
  return extractFailureSnapshot(payload)
})

const failureSnapshotFields = computed(() => {
  const snapshot = failureSnapshot.value
  if (!snapshot) return []
  const page = snapshot.page && typeof snapshot.page === 'object' ? snapshot.page : {}
  const values: Array<{ label: string; value: string }> = []
  const add = (label: string, value: unknown) => {
    const text = diagnosticValueText(value)
    if (text) values.push({ label, value: text })
  }
  add('扩展版本', snapshot.extensionVersion || page.version)
  add('环境标识', snapshot.environmentKey)
  add('快照 URL', page.href || snapshot.href)
  add('页面标题', snapshot.title)
  add('平台', page.platform)
  add('截图', snapshot.screenshotCaptured === undefined ? undefined : (snapshot.screenshotCaptured ? '已采集' : '未采集'))
  add('页面文本', page.text)
  return values
})

const failureSnapshotInputs = computed(() => {
  const page = failureSnapshot.value?.page
  const inputs = Array.isArray(page?.inputs) ? page.inputs : []
  return inputs.slice(0, 8).map((item: any, index: number) => {
    const title = `#${item.index ?? index} ${firstText(item.type, 'input')}`
    const detail = [
      item.placeholder ? `placeholder=${item.placeholder}` : '',
      item.accept ? `accept=${item.accept}` : '',
      item.value ? `value=${item.value}` : '',
    ].filter(Boolean).join('；') || '无可见描述'
    return { key: `${index}-${title}-${detail}`, title, detail }
  })
})

const failureSnapshotActions = computed(() => {
  const page = failureSnapshot.value?.page
  const actions = Array.isArray(page?.actions) ? page.actions : []
  return actions.slice(0, 14)
    .map((item: any, index: number) => ({
      key: `${index}-${item.text || ''}`,
      text: item.text || `元素 #${index + 1}`,
    }))
    .filter((item: { text: string }) => item.text)
})

const failureSnapshotAdapterState = computed(() => {
  const state = failureSnapshot.value?.page?.adapterState
  if (!state) return ''
  return typeof state === 'string' ? state : JSON.stringify(state, null, 2)
})

watch(() => props.modelValue, (opened) => {
  if (opened) {
    applyInitialFilters()
    load()
  }
})

watch(() => [props.initialFailureCode, props.initialStatus], () => {
  if (props.modelValue) {
    applyInitialFilters()
    search()
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
    failureCode: query.failureCode.trim() || undefined,
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
  query.failureCode = ''
  query.health = ''
  search()
}

function applyInitialFilters() {
  query.failureCode = props.initialFailureCode?.trim() || ''
  query.status = props.initialStatus?.trim() || ''
  query.health = ''
  page.current = 1
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
  if (isOfficialApiPlatform(row.platform)) {
    const officialApiMap: Record<string, string> = {
      pending: '等待系统处理',
      filling: '正在提交平台',
      filled_verified: '内容已校验',
      scheduling: '正在提交平台',
      scheduled: '已提交平台',
      publish_due: '待确认发布结果',
      checking_publish_result: '发布结果核验中',
      publish_unknown: '等待最终发布确认',
      published_confirmed: '发布已确认',
      schedule_failed: '提交平台失败',
      publish_failed: '发布结果失败',
      manual_required: '需要人工处理',
      routed_to_semi_auto: '已转半自动',
      cancel_pending_platform: '等待平台取消确认',
      cancelled: '后台已取消',
    }
    return officialApiMap[row.status] || row.status || '-'
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

function isOfficialApiPlatform(platform?: string | null) {
  return platform === 'wechat_mp' || platform === 'wechat'
}

function platformTimeLine(row: SelfMediaPublishSchedule) {
  if (isOfficialApiPlatform(row.platform)) {
    return `触发 ${timeText(row.nextAttemptAt || row.plannedPublishAt)}`
  }
  if (isBackendDelayedPlatform(row.platform)) {
    return `触发 ${timeText(row.nextAttemptAt || row.plannedPublishAt)}`
  }
  return `平台 ${timeText(row.platformScheduledAt)}`
}

function platformTimeFieldLabel(row: SelfMediaPublishSchedule) {
  if (isOfficialApiPlatform(row.platform)) return '后台触发时间'
  return isBackendDelayedPlatform(row.platform) ? '后台触发时间' : '平台定时时间'
}

function platformTimeFieldValue(row: SelfMediaPublishSchedule) {
  if (isOfficialApiPlatform(row.platform)) return timeText(row.nextAttemptAt || row.plannedPublishAt)
  if (isBackendDelayedPlatform(row.platform)) return timeText(row.plannedPublishAt)
  return timeText(row.platformScheduledAt)
}

function platformScheduleIdFieldValue(row: SelfMediaPublishSchedule) {
  if (isOfficialApiPlatform(row.platform)) return row.platformScheduleId || '不适用'
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
  const code = row.failureLabel || failureCodeLabel(row.failureCode)
  const message = displayFailureMessage(row)
  const stage = failureStageText(row)
  const drift = scheduleDriftReasonLabel(row.scheduleDriftReason)
  if (stage && code && message) return `${code}（${stage}）：${message}`
  if (stage && code) return `${code}（${stage}）`
  if (stage && message) return `${stage}：${message}`
  if (code && message) return `${code}：${message}`
  return message || code || drift || '-'
}

function failureStageText(row: SelfMediaPublishSchedule) {
  const payload = parseDiagnostics(row.diagnosticsJson)
  if (!payload) return ''
  return operationStageLabel(payload.operationStage, payload.operationStageLabel)
}

function displayFailureMessage(row: SelfMediaPublishSchedule) {
  if (row.failureCode === 'WECHAT_API_UNAUTHORIZED') {
    const stage = failureStageText(row)
    if (stage.includes('草稿')) return '当前公众号缺少新增草稿权限。请确认客户公众号具备草稿箱/文章管理能力，并重新授权公众号。'
    if (stage.includes('提交') || stage.includes('发布')) return '当前公众号缺少提交发布权限。请确认客户公众号具备发布/群发与通知能力，并重新授权公众号。'
    if (stage.includes('素材') || stage.includes('图片')) return '当前公众号缺少素材上传或图片处理权限。请确认客户公众号具备素材管理权限，并重新授权公众号。'
    return '当前公众号缺少发布所需授权。请确认客户公众号具备素材、草稿和发布相关权限，并重新授权公众号。'
  }
  return row.failureMessage || ''
}

function parseDiagnostics(value?: string | null): Record<string, any> | null {
  if (!value) return null
  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === 'object' ? parsed : null
  } catch {
    return null
  }
}

function firstText(...values: unknown[]) {
  for (const value of values) {
    if (value === null || value === undefined) continue
    const text = String(value).trim()
    if (text) return text
  }
  return ''
}

function nestedRecord(source: any, path: string[]) {
  let current = source
  for (const key of path) {
    if (!current || typeof current !== 'object') return null
    current = current[key]
  }
  return current && typeof current === 'object' ? current : null
}

function extractPublishVerification(payload: Record<string, any>) {
  return nestedRecord(payload, ['fillResult', 'publishOptions', 'publishVerification'])
    || nestedRecord(payload, ['publishOptions', 'publishVerification'])
    || nestedRecord(payload, ['publishVerification'])
    || nestedRecord(payload, ['result', 'publishOptions', 'publishVerification'])
}

function extractPublishOptions(payload: Record<string, any>) {
  return nestedRecord(payload, ['fillResult', 'publishOptions'])
    || nestedRecord(payload, ['publishOptions'])
    || nestedRecord(payload, ['result', 'publishOptions'])
}

function extractFailureSnapshot(payload: Record<string, any>) {
  return nestedRecord(payload, ['failureSnapshot'])
    || nestedRecord(payload, ['failure', 'failureSnapshot'])
    || nestedRecord(payload, ['error', 'failureSnapshot'])
    || null
}

function operationStageLabel(stage?: string | null, fallback?: string | null) {
  if (fallback) return fallback
  const labels: Record<string, string> = {
    WECHAT_PREPARE_COVER_MATERIAL: '准备公众号封面素材',
    WECHAT_RENDER_CONTENT: '转换公众号正文与图片',
    WECHAT_ADD_DRAFT: '新增公众号草稿',
    WECHAT_SUBMIT_PUBLISH: '提交公众号发布',
  }
  return stage ? labels[stage] || stage : ''
}

function platformResultStatus(row: SelfMediaPublishSchedule) {
  if (row.status === 'published_confirmed') return '已确认发布'
  if (resolvedPlatformPublishedUrl(row)) return '已回传链接'
  if (row.platformPublishId) return '已回传 ID'
  if (row.publishCheckCoverUrl) return '已回传封面'
  if (row.status === 'scheduled') return '等待发布'
  if (row.status === 'publish_unknown' || row.status === 'checking_publish_result') return '回查中'
  return ''
}

function platformResultTag(row: SelfMediaPublishSchedule): 'success' | 'warning' | 'danger' | 'info' {
  if (row.status === 'published_confirmed' || resolvedPlatformPublishedUrl(row)) return 'success'
  if (row.status === 'publish_unknown' || row.status === 'checking_publish_result') return 'warning'
  if (row.status === 'publish_failed') return 'danger'
  return 'info'
}

function resolvedPlatformPublishedUrl(row: SelfMediaPublishSchedule, candidate?: string | null) {
  const payload = parseDiagnostics(row.diagnosticsJson)
  const verification = payload ? extractPublishVerification(payload) : null
  const publishOptions = payload ? extractPublishOptions(payload) : null
  const value = firstText(
    candidate,
    row.platformPublishedUrl,
    verification?.platformPublishedUrl,
    verification?.publishedUrl,
    publishOptions?.platformPublishedUrl,
    publishOptions?.publishedUrl,
    payload?.platformPublishedUrl,
    payload?.publishedUrl,
  )
  if (!/^https?:\/\//i.test(value)) return ''
  return value
}

function publishedUrlLinkText(row: SelfMediaPublishSchedule) {
  const url = resolvedPlatformPublishedUrl(row)
  if (!url) return '打开作品'
  try {
    const parsed = new URL(url)
    const path = `${parsed.pathname}${parsed.search}`.replace(/\/$/, '')
    const text = `${parsed.hostname}${path}`
    return text.length > 42 ? `${text.slice(0, 39)}...` : text
  } catch {
    return url.length > 42 ? `${url.slice(0, 39)}...` : url
  }
}

function articleDisplay(row: SelfMediaPublishSchedule) {
  return row.articleTitle || '未命名文章'
}

function failureCodeLabel(code?: string | null) {
  if (!code) return ''
  const labels: Record<string, string> = {
    CANCELLED_BY_OPERATOR: '操作员已取消',
    MANUAL_CONFIRMED_FAILED: '人工确认失败',
    PUBLISH_RESULT_MANUAL_FAILED: '人工确认发布失败',
    PLATFORM_CAPABILITY_UNVERIFIED: '平台能力未验证',
    PLATFORM_SCHEDULE_UNSUPPORTED: '平台不支持定时',
    PLATFORM_SCHEDULE_STRATEGY_MISMATCH: '排期策略不匹配',
    PLATFORM_SCHEDULE_TIME_EXPIRED: '定时时间过近或已过期',
    PLATFORM_SCHEDULE_TIME_TOO_CLOSE: '平台定时时间过近',
    PLATFORM_SCHEDULE_TIME_TOO_FAR: '平台定时时间超过可选范围',
    ARTICLE_NOT_FOUND: '文章不存在',
    ARTICLE_NOT_READY: '文章未就绪',
    ARTICLE_BRAND_MISMATCH: '文章品牌不匹配',
    ARTICLE_COVER_REQUIRED: '文章缺少封面',
    SELF_MEDIA_ACCOUNT_NOT_FOUND: '自媒体账号不存在',
    SELF_MEDIA_ACCOUNT_BRAND_MISMATCH: '自媒体账号品牌不匹配',
    SELF_MEDIA_ACCOUNT_INACTIVE: '自媒体账号未启用',
    ENVIRONMENT_ACCOUNT_BINDING_NOT_FOUND: '未绑定浏览器环境账号',
    BROWSER_ENVIRONMENT_LOCKED: '浏览器环境被锁定',
    LOCAL_AGENT_OFFLINE: '本地助手离线',
    EXTENSION_TASK_FAILED: '扩展任务失败',
    SCHEDULE_EXECUTION_FAILED: '排期执行失败',
    DISTRIBUTION_TASK_PREPARE_FAILED: '分发任务准备失败',
    DISTRIBUTION_QUOTA_EXHAUSTED: '分发额度已用尽',
    CHANNEL_QUOTA_EXHAUSTED: '渠道额度已用完',
    CHANNEL_QUOTA_UNAVAILABLE: '渠道额度配置不可用',
    ACCOUNT_MISMATCH: '平台账号不一致',
    IDENTITY_EXPECTATION_MISSING: '缺少账号校验信息',
    COVER_MATERIAL_NOT_FOUND: '封面素材不存在',
    COVER_IMAGE_UNSUPPORTED: '封面图片类型不支持',
    MATERIAL_IMAGE_UNAVAILABLE: '素材图片不可用',
    PUBLIC_MATERIAL_NOT_FOUND: '素材公开链接失效',
    WORKS_LIST_VERIFY_TIMEOUT: '作品列表回查超时',
    PAGE_LOAD_TIMEOUT: '页面加载或执行超时',
    EDITOR_NOT_READY: '编辑器未就绪',
    PUBLISH_RESULT_CHECK_HELPER_FAILED: '发布结果回查失败',
    PLATFORM_SCHEDULED_WAITING: '平台已定时，等待发布时间后复查',
    PUBLISH_RESULT_NOT_MATCHED_RETRYING: '发布结果暂未匹配，等待复查',
    PUBLISH_RESULT_NOT_MATCHED: '发布结果多次未匹配',
    PUBLISH_RESULT_RECHECK_REQUESTED: '已人工触发重新校验',
    PUBLISH_CHECK_FAILED: '发布结果校验失败',
    PUBLISH_RESULT_CHECK_FAILED: '发布结果校验失败',
    MANUAL_RETRY_REQUESTED: '操作员已请求立即重试',
    MANUAL_REQUIRED_BY_OPERATOR: '操作员已转人工处理',
    FILL_FAILED: '页面填充失败',
    PUBLISH_BUTTON_NOT_FOUND: '发布按钮未找到',
    TASK_EXPIRED: '任务已过期',
    PAGE_CHANGED: '页面结构已变化',
    COOKIE_MISSING: '登录凭证缺失',
    LOGIN_REQUIRED: '平台登录失效',
    UNKNOWN: '未知异常',
    FILL_TOKEN_INVALID: '填充令牌无效',
    FILL_TOKEN_USED_OR_EXPIRED: '填充令牌已使用或过期',
    FILL_TOKEN_OPERATOR_MISMATCH: '填充令牌操作员不匹配',
    FILL_TOKEN_BINDING_MISMATCH: '填充令牌账号绑定不匹配',
    WECHAT_API_UNAUTHORIZED: '微信公众号发布权限不足',
    AUTH_EXPIRED: '授权已过期',
    CLIENT_ERROR: '平台账号或内容异常',
    token_expired: '填充令牌已使用或过期',
    login_required: '平台登录失效',
    account_mismatch: '平台账号不一致',
    editor_not_found: '编辑器未就绪',
    failed: '执行失败',
    '70006': '填充令牌无效',
    '70007': '填充令牌已使用或过期',
    '70017': '填充令牌操作员不匹配',
    '70018': '填充令牌账号绑定不匹配',
    ZHIHU_DRAFT_LOADING: '知乎草稿仍在加载',
    ZHIHU_ADAPTER_NOT_LOADED: '知乎平台适配器未加载',
    ZHIHU_PUBLISH_NOT_SUBMITTED: '知乎发布未完成',
    ZHIHU_EDITOR_STATE_NOT_ACTIVE: '知乎编辑器未就绪',
    ZHIHU_COVER_UPLOAD_NOT_CONFIRMED: '知乎封面上传未确认',
    ZHIHU_COVER_UPLOAD_ENTRY_NOT_FOUND: '知乎封面上传入口未找到',
    ZHIHU_COVER_UPLOAD_TIMEOUT: '知乎封面上传超时',
    ZHIHU_COVER_DIALOG_NOT_READY: '知乎封面弹窗未就绪',
    ZHIHU_COVER_SELECTION_FAILED: '知乎封面选择失败',
    ZHIHU_PUBLISH_BUTTON_NOT_FOUND: '知乎发布按钮未找到',
    ZHIHU_FILL_FAILED: '知乎页面填充失败',
    ZHIFU_EDITOR_STATE_NOT_ACTIVE: '知乎编辑器未就绪',
    ZHIFU_COVER_DIALOG_NOT_READY: '知乎封面弹窗未就绪',
    ZHIFU_COVER_SELECTION_FAILED: '知乎封面选择失败',
    XIAOHONGSHU_FORMAT_BUTTON_NOT_FOUND: '小红书一键排版按钮未找到',
    XIAOHONGSHU_FORMAT_NOT_READY: '小红书排版页未就绪',
    XIAOHONGSHU_IMAGE_GENERATION_TIMEOUT: '小红书笔记图片生成超时',
    XIAOHONGSHU_NEXT_BUTTON_NOT_FOUND: '小红书下一步按钮未找到',
    XIAOHONGSHU_PUBLISH_SETTINGS_NOT_READY: '小红书发布设置页未就绪',
    XIAOHONGSHU_SCHEDULE_SWITCH_NOT_FOUND: '小红书定时发布开关未找到',
    XIAOHONGSHU_SCHEDULE_TIME_INPUT_NOT_FOUND: '小红书定时时间输入框未找到',
    XIAOHONGSHU_SCHEDULE_TIME_INVALID: '小红书定时时间无效',
    XIAOHONGSHU_SCHEDULE_TIME_NOT_APPLIED: '小红书定时时间未生效',
    XIAOHONGSHU_SCHEDULE_TIME_TOO_SOON: '小红书定时时间过近',
    XIAOHONGSHU_SCHEDULE_TIME_TOO_LATE: '小红书定时时间超过平台范围',
    XIAOHONGSHU_PUBLISH_BUTTON_NOT_FOUND: '小红书发布按钮未找到',
    XIAOHONGSHU_PUBLISH_NOT_CONFIRMED: '小红书发布成功状态未确认',
    XIAOHONGSHU_FILL_FAILED: '小红书页面填充失败',
    BAIJIAHAO_COVER_REQUIRED: '百家号缺少文章封面',
    BAIJIAHAO_APP_ID_REQUIRED: '百家号 ID/app_id 未填写',
    BAIJIAHAO_COVER_UPLOAD_ENTRY_NOT_FOUND: '百家号封面上传入口未找到',
    BAIJIAHAO_COVER_PICKER_NOT_OPEN: '百家号封面选择弹窗未打开',
    BAIJIAHAO_COVER_UPLOAD_INPUT_NOT_FOUND: '百家号封面本地上传入口未找到',
    BAIJIAHAO_COVER_UPLOAD_TIMEOUT: '百家号封面上传超时',
    BAIJIAHAO_COVER_CONFIRM_NOT_FOUND: '百家号封面确认按钮未找到',
    BAIJIAHAO_CONTENT_WRITTEN_TO_TITLE: '百家号正文误入标题区域',
    BAIJIAHAO_UEDITOR_FILL_NOT_VISIBLE: '百家号正文编辑器未显示内容',
    BAIJIAHAO_SCHEDULE_TIME_TOO_SOON: '百家号定时时间过近',
    BAIJIAHAO_SCHEDULE_TIME_TOO_LATE: '百家号定时时间超过平台范围',
    BAIJIAHAO_SCHEDULE_TIME_INVALID: '百家号定时时间无效',
    BAIJIAHAO_SCHEDULE_BUTTON_NOT_FOUND: '百家号定时发布按钮未找到',
    BAIJIAHAO_SCHEDULE_DIALOG_NOT_READY: '百家号定时发布弹窗未就绪',
    BAIJIAHAO_SCHEDULE_OPTION_NOT_FOUND: '百家号定时时间选项未找到',
    BAIJIAHAO_PLATFORM_RATE_LIMITED: '百家号平台频控/点击过快',
    BAIJIAHAO_PUBLISH_NOT_CONFIRMED: '百家号发布成功状态未确认',
    BAIJIAHAO_REVIEW_REJECTED: '百家号审核未通过',
    BAIJIAHAO_WORK_WITHDRAWN: '百家号作品已撤回或删除',
    BAIJIAHAO_FILL_FAILED: '百家号页面填充失败',
    TOUTIAO_SCHEDULE_DIALOG_NOT_READY: '头条定时发布弹窗未就绪',
    TOUTIAO_SCHEDULE_TIME_INPUT_NOT_FOUND: '头条定时时间输入框未找到',
    TOUTIAO_COVER_SELECTION_FAILED: '头条封面选择失败',
  }
  return labels[code] || readableFailureCode(code)
}

function platformPublishStatusLabel(status?: string | null) {
  if (!status) return ''
  const labels: Record<string, string> = {
    published: '已发布',
    reviewing: '审核中',
    scheduled: '已定时/待发布',
    rejected: '审核未通过',
    withdrawn: '已撤回/删除',
    draft: '草稿',
    unknown: '未知',
  }
  return labels[status] || status
}

function readableFailureCode(code: string) {
  const normalized = code.trim()
  if (!normalized) return ''
  const platformPrefixes: Record<string, string> = {
    ZHIHU: '知乎',
    ZHIFU: '知乎',
    XIAOHONGSHU: '小红书',
    BAIJIAHAO: '百家号',
    TOUTIAO: '头条',
    FILL: '填充',
    PUBLISH: '发布',
    SCHEDULE: '排期',
    PLATFORM: '平台',
    ARTICLE: '文章',
    SELF_MEDIA_ACCOUNT: '自媒体账号',
    ENVIRONMENT: '浏览器环境',
    BROWSER_ENVIRONMENT: '浏览器环境',
    LOCAL_AGENT: '本地助手',
  }
  const matchedPrefix = Object.keys(platformPrefixes)
    .sort((left, right) => right.length - left.length)
    .find((prefix) => normalized.startsWith(`${prefix}_`))
  if (!matchedPrefix) return `未识别异常（${normalized}）`
  return `${platformPrefixes[matchedPrefix]}异常（${normalized}）`
}

function scheduleDriftReasonLabel(reason?: string | null) {
  if (!reason) return ''
  const labels: Record<string, string> = {
    delayed_by_platform_min_remaining: '已按平台最小提前量顺延',
    shifted_by_interval: '已按发布间隔顺延',
    window_exceeded: '发布时间超出窗口',
  }
  return labels[reason] || reason
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
  return props.canPublish && ['scheduled', 'publish_due', 'checking_publish_result', 'publish_unknown', 'publish_failed'].includes(row.status)
}

function canRetryNow(row: SelfMediaPublishSchedule | null) {
  if (!props.canPublish || !row) return false
  if (['cancelled', 'published_confirmed', 'cancel_pending_platform', 'routed_to_semi_auto'].includes(row.status)) return false
  if (hasPendingRetryRequest(row) || isLocked(row)) return false
  if (row.queueKind === 'publish_result_check') return ['scheduled', 'publish_due', 'checking_publish_result', 'publish_unknown', 'publish_failed', 'manual_required'].includes(row.status)
  return ['pending', 'filling', 'filled_verified', 'scheduling', 'schedule_failed', 'manual_required'].includes(row.status)
}

function hasPendingRetryRequest(row: SelfMediaPublishSchedule | null) {
  if (!row) return false
  const text = `${row.failureCode || ''} ${row.failureMessage || ''} ${row.status || ''}`.toLowerCase()
  return text.includes('retry_requested')
    || text.includes('requested immediate retry')
    || text.includes('立即重试')
    || text.includes('已请求')
    || text.includes('重试中')
}

function isMaterialFailure(row: SelfMediaPublishSchedule | null) {
  if (!row) return false
  const code = row.failureCode || ''
  if (['MATERIAL_IMAGE_UNAVAILABLE', 'PUBLIC_MATERIAL_NOT_FOUND', 'COVER_MATERIAL_NOT_FOUND', 'COVER_IMAGE_UNSUPPORTED'].includes(code)) return true
  const text = `${row.failureMessage || ''} ${row.diagnosticsJson || ''}`
  return text.includes('/api/public/brand-materials/')
    || text.includes('Material not found')
    || text.includes('image content-type is not supported')
    || text.includes('content-type is not supported')
}

function canHandleMaterials(row: SelfMediaPublishSchedule | null) {
  return Boolean(props.canPublish && row?.articleId && isMaterialFailure(row))
}

function handleMaterials(row: SelfMediaPublishSchedule | null) {
  if (!row?.articleId) return
  diagnosticsVisible.value = false
  emit('openArticle', row.articleId)
  ElMessage.info('请在文章详情中替换失效的封面或正文图片，保存后回到排期明细点击“立即重试”。')
}

function canMarkManual(row: SelfMediaPublishSchedule) {
  if (!props.canPublish) return false
  return ['pending', 'filling', 'filled_verified', 'scheduling', 'scheduled', 'publish_due', 'checking_publish_result', 'publish_unknown', 'schedule_failed', 'publish_failed'].includes(row.status)
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

async function retryNow(row: SelfMediaPublishSchedule | null) {
  if (!row) return
  try {
    await ElMessageBox.confirm(`确认立即重试排期 #${row.id}？`, '立即重试', {
      confirmButtonText: '立即重试',
      cancelButtonText: '返回',
      type: 'warning',
    })
    await retrySelfMediaPublishScheduleNow(row.id)
    ElMessage.success(row.queueKind === 'publish_result_check' ? '已加入发布结果校验队列' : '已加入自动执行队列')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      throw error
    }
  }
}

async function markManual(row: SelfMediaPublishSchedule) {
  try {
    const result = await ElMessageBox.prompt(`确认将排期 #${row.id} 转为人工处理？`, '转人工处理', {
      confirmButtonText: '转人工',
      cancelButtonText: '返回',
      inputPlaceholder: '处理原因，可选',
      type: 'warning',
    })
    await markSelfMediaPublishScheduleManualRequired(row.id, { reason: result.value || undefined })
    ElMessage.success('已转为人工处理')
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

function diagnosticValueText(value: unknown) {
  if (value === null || value === undefined || value === '') return ''
  if (Array.isArray(value)) return value.filter((item) => item !== null && item !== undefined && item !== '').join('，')
  if (typeof value === 'object') return JSON.stringify(value).slice(0, 300)
  return String(value)
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
  if (isMaterialFailure(row)) return '素材公开链接返回 404 或非图片内容。请点击“处理素材”打开文章，替换失效的封面/正文图片并保存，然后点击“立即重试”。'
  if (row.failureActionHint) return row.failureActionHint
  if (row.failureCode === 'LOCAL_AGENT_HEARTBEAT_TIMEOUT') return '本地助手执行心跳超时；确认本地助手、AdsPower 和平台页面正常后，可点击“立即重试”。'
  if (isOfficialApiPlatform(row.platform)) {
    if (row.failureCode === 'WECHAT_API_UNAUTHORIZED') return '当前公众号缺少发布所需授权。请在品牌详情重新授权公众号，并确认授权时已勾选素材、草稿和发布相关权限。'
    if (row.status === 'pending') return '等待系统到计划时间后自动提交到平台；不需要本地助手领取。'
    if (row.status === 'filling' || row.status === 'scheduling') return '系统正在向平台提交内容；若长时间不变化，请联系管理员查看后台执行记录。'
    if (row.status === 'published_confirmed') return '已确认发布，无需处理。'
    if (row.status === 'manual_required') return '请根据异常信息处理账号授权、平台权限或文章内容问题；修复后可点击“立即重试”或重新创建排期。'
  }
  if (isBackendDelayedPlatform(row.platform)) {
    if (row.status === 'pending') return '等待本地助手到点领取；该平台不支持平台内定时，计划时间即后台触发发布时间。'
    if (row.status === 'filling') return '本地助手正在填充并提交发布；若长时间不变化，请检查 AdsPower 页面和扩展日志。'
    if (row.status === 'published_confirmed') return '已确认发布，无需处理。'
    if (row.status === 'manual_required') return '按异常信息处理页面或配置问题；处理后重新创建排期。'
  }
  if (row.status === 'publish_unknown') return '等待自动复查；若长时间未变化，可点击“重新校验”或人工确认发布。'
  if (row.status === 'publish_failed') return `检查本地助手、AdsPower 浏览器和${platformLabel(row.platform)}作品管理页；修复后点击“重新校验”。`
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
    baijiahao: '百家号',
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

.schedule-filter.is-wide {
  width: 240px;
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

.schedule-cover-line {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  color: #4b5563;
}

.schedule-cover-line img {
  width: 28px;
  height: 28px;
  flex: 0 0 28px;
  object-fit: cover;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  background: #f9fafb;
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

.schedule-diagnostics-list {
  display: grid;
  gap: 8px;
  color: #374151;
  font-size: 13px;
  line-height: 1.5;
}

.schedule-diagnostics-list.is-compact {
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 6px;
}

.schedule-diagnostics-list.is-compact span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #111827;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  padding: 4px 8px;
}

.schedule-diagnostics-list-item {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr);
  gap: 10px;
  align-items: start;
}

.schedule-diagnostics-list-item strong {
  color: #111827;
  font-weight: 650;
}

.schedule-diagnostics-list-item span {
  min-width: 0;
  overflow-wrap: anywhere;
}

.schedule-diagnostics-advice {
  margin: 0;
  color: #374151;
  font-size: 13px;
  line-height: 1.6;
}

.schedule-diagnostics-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
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

.schedule-diagnostics-json.is-compact {
  max-height: 180px;
}
</style>
