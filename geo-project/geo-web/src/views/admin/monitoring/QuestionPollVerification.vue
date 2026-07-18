<template>
  <div class="verification-page">
    <header class="page-hero">
      <div>
        <div class="eyebrow">QUESTION POLL · MANUAL VERIFICATION</div>
        <h1>轮询链路验证</h1>
        <p>用少量真实问题验证正式联网轮询链路。测试批次独立标记，不推进定时轮转，也不进入正式日报与经营统计。</p>
      </div>
      <div class="hero-actions">
        <el-button :icon="Clock" @click="openHistory">验证记录</el-button>
        <el-button :icon="Monitor" @click="openTaskMonitor">查看调度监控</el-button>
      </div>
    </header>

    <section class="flow-strip" aria-label="验证链路">
      <template v-for="(step, index) in flowSteps" :key="step.title">
        <div class="flow-step">
          <span>{{ String(index + 1).padStart(2, '0') }}</span>
          <div>
            <strong>{{ step.title }}</strong>
            <small>{{ step.description }}</small>
          </div>
        </div>
        <el-icon v-if="index < flowSteps.length - 1" class="flow-arrow"><ArrowRight /></el-icon>
      </template>
    </section>

    <div class="workspace-grid">
      <section class="panel configuration-panel">
        <div class="panel-heading">
          <div>
            <span class="section-index">01</span>
            <div>
              <h2>创建验证批次</h2>
              <p>建议首次只选 1 个问题和 1 个平台，确认后再扩大范围。</p>
            </div>
          </div>
          <el-tag effect="plain" type="info">MANUAL</el-tag>
        </div>

        <el-form label-position="top" class="verification-form">
          <div class="form-grid">
            <el-form-item label="项目">
              <el-select
                v-model="form.projectId"
                filterable
                placeholder="选择已激活项目"
                :loading="projectsLoading"
                @change="invalidateRequestId"
              >
                <el-option
                  v-for="project in projects"
                  :key="project.id"
                  :label="project.projectName"
                  :value="project.id"
                >
                  <div class="project-option">
                    <span>{{ project.projectName }}</span>
                    <small>{{ project.projectCode || `ID ${project.id}` }}</small>
                  </div>
                </el-option>
              </el-select>
            </el-form-item>

            <el-form-item label="问题层级">
              <el-segmented
                v-model="form.questionTier"
                :options="tierOptions"
                block
                @change="invalidateRequestId"
              />
            </el-form-item>

            <el-form-item label="问题数量">
              <el-input-number
                v-model="form.questionLimit"
                :min="1"
                :max="10"
                controls-position="right"
                @change="invalidateRequestId"
              />
              <span class="field-help">从该层级已保存问题中取前 N 条，不改变定时轮询游标。</span>
            </el-form-item>
          </div>

          <el-form-item class="platform-field">
            <template #label>
              <div class="platform-label">
                <span>联网平台</span>
                <span>最多选择 4 个；仅显示正式问题轮询配置</span>
              </div>
            </template>

            <div v-if="platformsLoading" class="platform-loading">
              <el-skeleton :rows="2" animated />
            </div>
            <el-empty
              v-else-if="platforms.length === 0"
              description="暂无 QUESTION_POLL_WEB 平台配置"
              :image-size="70"
            />
            <el-checkbox-group
              v-else
              v-model="form.platformIds"
              class="platform-grid"
              :max="4"
              @change="invalidateRequestId"
            >
              <el-checkbox
                v-for="platform in platforms"
                :key="platform.platformId"
                :value="platform.platformId"
                :disabled="!platform.selectable"
                class="platform-card"
              >
                <div class="platform-card-body">
                  <div class="platform-card-title">
                    <strong>{{ platform.platformName }}</strong>
                    <el-tag
                      size="small"
                      :type="platform.selectable ? 'success' : 'info'"
                      effect="light"
                    >
                      {{ platform.selectable ? '可执行' : '不可执行' }}
                    </el-tag>
                  </div>
                  <span>{{ platform.modelId || '未配置模型 ID' }}</span>
                  <small>{{ platform.integrationType }}</small>
                  <em v-if="platform.unavailableReason">{{ platform.unavailableReason }}</em>
                </div>
              </el-checkbox>
            </el-checkbox-group>
          </el-form-item>
        </el-form>

        <div class="execution-summary">
          <div class="summary-copy">
            <el-icon><InfoFilled /></el-icon>
            <div>
              <strong>本次预计生成 {{ logicalResultCount }} 条逻辑结果</strong>
              <span>
                {{ form.platformIds.length }} 个平台 × {{ form.questionLimit }} 个问题；
                自动搜索重试存在时，物理供应商调用最多约 {{ maxProviderCallCount }} 次。
              </span>
            </div>
          </div>
          <el-button
            type="primary"
            size="large"
            :loading="submitting"
            :disabled="!canSubmit"
            :icon="VideoPlay"
            @click="confirmStart"
          >
            创建手工验证批次
          </el-button>
        </div>
      </section>

      <aside class="panel guardrail-panel">
        <div class="panel-heading compact">
          <div>
            <span class="section-index">02</span>
            <div>
              <h2>执行边界</h2>
              <p>测试会真实消耗平台额度。</p>
            </div>
          </div>
        </div>
        <ul class="guardrail-list">
          <li><el-icon><CircleCheck /></el-icon><span>复用正式分片、队列、Worker 与联网 Gateway</span></li>
          <li><el-icon><CircleCheck /></el-icon><span>使用创建任务时读取到的最新启用平台配置</span></li>
          <li><el-icon><CircleCheck /></el-icon><span>保留 Attempt、Provider Call、来源与引用审计</span></li>
          <li><el-icon><Remove /></el-icon><span>不推进定时问题轮转位置</span></li>
          <li><el-icon><Remove /></el-icon><span>不写正式日报、汇总统计和正式失败告警</span></li>
        </ul>
      </aside>
    </div>

    <section v-if="batch" ref="resultPanel" class="panel result-panel">
      <div class="panel-heading">
        <div>
          <span class="section-index">03</span>
          <div>
            <h2>批次执行进度</h2>
            <p>
              批次 #{{ batch.batchId }} · {{ batch.projectName }} ·
              {{ batch.questionTier }} 级问题 · Batch No. {{ batch.batchNo }}
            </p>
          </div>
        </div>
        <div class="result-actions">
          <el-tag :type="batchStatusType(batch.status)" effect="light" size="large">
            {{ batchStatusLabel(batch.status) }}
          </el-tag>
          <el-button :icon="Refresh" :loading="refreshing" @click="refreshBatch">刷新</el-button>
        </div>
      </div>

      <div class="progress-overview">
        <div class="progress-ring">
          <el-progress
            type="dashboard"
            :percentage="progressPercent"
            :status="batch.status === 'failed' ? 'exception' : undefined"
            :width="132"
          />
          <span>终态分片 {{ batch.terminalShardCount }} / {{ batch.shardCount }}</span>
        </div>
        <div class="metric-grid">
          <div class="metric-card">
            <span>逻辑结果</span>
            <strong>{{ batch.resultCount }}</strong>
            <small>预计 {{ batch.questionLimit * batch.platformCount }}</small>
          </div>
          <div class="metric-card success">
            <span>完成</span>
            <strong>{{ batch.completedCount }}</strong>
            <small>有效结果</small>
          </div>
          <div class="metric-card danger">
            <span>失败</span>
            <strong>{{ batch.failedCount }}</strong>
            <small>含缺失结果</small>
          </div>
          <div class="metric-card">
            <span>确认联网</span>
            <strong>{{ batch.searchConfirmedCount }}</strong>
            <small>结构化搜索证据</small>
          </div>
          <div class="metric-card">
            <span>确认引用曝光</span>
            <strong>{{ batch.confirmedCitationExposureCount }}</strong>
            <small>满足严格 R5 规则</small>
          </div>
        </div>
      </div>

      <el-table :data="batch.platforms" class="platform-progress-table">
        <el-table-column label="平台" min-width="210">
          <template #default="{ row }">
            <div class="table-platform">
              <strong>{{ row.platformName }}</strong>
              <span>{{ row.channelCode || row.platformCode }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="分片" min-width="190">
          <template #default="{ row }">
            <div class="mini-statuses">
              <span>待执行 {{ row.readyCount }}</span>
              <span>执行中 {{ row.runningCount }}</span>
              <span class="positive">完成 {{ row.completedShardCount }}</span>
              <span :class="{ negative: row.failedShardCount > 0 }">失败 {{ row.failedShardCount }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="结果进度" min-width="230">
          <template #default="{ row }">
            <el-progress
              :percentage="platformProgress(row)"
              :status="row.failedCount > 0 ? 'warning' : undefined"
            />
            <small>{{ row.completedCount + row.failedCount }} / {{ row.expectedCount }}</small>
          </template>
        </el-table-column>
        <el-table-column prop="completedCount" label="完成" width="90" align="center" />
        <el-table-column prop="failedCount" label="失败" width="90" align="center" />
        <el-table-column prop="resourceWaitCount" label="资源等待" width="110" align="center" />
      </el-table>

      <div v-if="batch.results.length > 0" class="result-detail-section">
        <div class="detail-heading">
          <div>
            <h3>问题级验证结果</h3>
            <p>逐条检查模型回答、联网状态、来源证据和引用映射。</p>
          </div>
          <el-tag effect="plain">{{ batch.results.length }} 条</el-tag>
        </div>
        <el-collapse class="result-collapse">
          <el-collapse-item
            v-for="item in batch.results"
            :key="item.pollResultId"
            :name="String(item.pollResultId)"
          >
            <template #title>
              <div class="result-collapse-title">
                <span class="result-platform">{{ item.platformName }}</span>
                <strong>{{ item.question }}</strong>
                <el-tag
                  size="small"
                  :type="item.status === 'completed' ? 'success' : 'danger'"
                  effect="light"
                >
                  {{ item.status === 'completed' ? '完成' : '失败' }}
                </el-tag>
                <el-tag
                  size="small"
                  :type="item.searchTriggered ? 'success' : 'warning'"
                  effect="plain"
                >
                  {{ searchStatusLabel(item.searchStatus) }}
                </el-tag>
              </div>
            </template>

            <div class="result-detail-body">
              <div class="result-facts">
                <span>耗时 <b>{{ formatDuration(item.latencyMs ?? item.responseTimeMs) }}</b></span>
                <span>请求 <b>{{ item.requestCount ?? 0 }}</b></span>
                <span>来源 <b>{{ item.sources.length }}</b></span>
                <span>引用 <b>{{ item.citations.length }}</b></span>
                <span>确认引用曝光 <b>{{ item.confirmedCitationExposure ? '是' : '否' }}</b></span>
              </div>

              <div v-if="item.answer" class="answer-block">
                <h4>模型回答</h4>
                <div>{{ item.answer }}</div>
              </div>
              <el-alert
                v-else-if="item.errorMessage"
                type="error"
                :closable="false"
                :title="item.errorCategory || '执行失败'"
                :description="item.errorMessage"
                show-icon
              />
              <el-empty v-else description="当前结果没有可展示的回答" :image-size="60" />

              <div v-if="item.sources.length > 0" class="evidence-block">
                <h4>来源证据</h4>
                <div class="source-detail-grid">
                  <article v-for="source in item.sources" :key="source.sourceId" class="source-detail-card">
                    <span>{{ source.rankNo || '-' }}</span>
                    <div>
                      <a
                        v-if="safeVerificationSourceUrl(source.url)"
                        :href="safeVerificationSourceUrl(source.url)"
                        target="_blank"
                        rel="noopener noreferrer"
                      >
                        {{ source.title || source.domain || '未命名来源' }}
                      </a>
                      <strong v-else>{{ source.title || source.domain || '未命名来源' }}</strong>
                      <small>{{ source.domain || '未知域名' }}</small>
                    </div>
                  </article>
                </div>
              </div>

              <div v-if="item.citations.length > 0" class="evidence-block">
                <h4>引用映射</h4>
                <div class="citation-detail-list">
                  <div v-for="(citation, index) in item.citations" :key="`${item.pollResultId}-${index}`">
                    <span>引用 {{ citation.citationIndex ?? index + 1 }}</span>
                    <strong>{{ citation.sourceTitle || '未关联来源' }}</strong>
                    <small>
                      {{ citationPosition(citation.answerStart, citation.answerEnd) }} ·
                      {{ citationConfidenceLabel(citation.confidence) }}
                    </small>
                  </div>
                </div>
              </div>
            </div>
          </el-collapse-item>
        </el-collapse>
      </div>

      <el-alert
        v-if="isBatchTerminal"
        :type="batch.status === 'finished' ? 'success' : 'warning'"
        :closable="false"
        show-icon
        class="terminal-alert"
      >
        <template #title>
          {{ batch.status === 'finished' ? '手工验证批次已完成' : '批次已结束，但存在失败结果' }}
        </template>
        <template #default>
          可前往调度监控查看分片与任务详情；联网回答、来源、引用和物理调用证据继续保存在正式审计表中。
        </template>
      </el-alert>
    </section>

    <el-drawer
      v-model="historyOpen"
      title="验证批次记录"
      size="560px"
      class="history-drawer"
      @open="loadHistory"
    >
      <div class="history-intro">
        <div>
          <strong>最近手工验证</strong>
          <span>仅展示你创建的批次，点击记录可恢复完整执行进度。</span>
        </div>
        <el-button :icon="Refresh" :loading="historyLoading" circle @click="loadHistory" />
      </div>

      <el-skeleton v-if="historyLoading && historyBatches.length === 0" :rows="5" animated />
      <el-empty
        v-else-if="historyBatches.length === 0"
        description="暂无手工验证批次"
        :image-size="90"
      />
      <div v-else class="history-list">
        <button
          v-for="item in historyBatches"
          :key="item.batchId"
          type="button"
          class="history-card"
          :class="{ active: batch?.batchId === item.batchId }"
          @click="selectHistoryBatch(item)"
        >
          <div class="history-card-top">
            <div>
              <strong>{{ item.projectName }}</strong>
              <span>#{{ item.batchId }} · {{ item.questionTier }}级 · {{ item.platformCount }}个平台</span>
            </div>
            <el-tag :type="batchStatusType(item.status)" effect="light">
              {{ batchStatusLabel(item.status) }}
            </el-tag>
          </div>
          <div class="history-metrics">
            <span>结果 <b>{{ item.resultCount }}</b></span>
            <span class="success">完成 <b>{{ item.completedCount }}</b></span>
            <span :class="{ danger: item.failedCount > 0 }">失败结果 <b>{{ item.failedCount }}</b></span>
            <span :class="{ danger: item.failedShardCount > 0 }">失败分片 <b>{{ item.failedShardCount }}</b></span>
            <span>确认联网 <b>{{ item.searchConfirmedCount }}</b></span>
          </div>
          <div class="history-card-footer">
            <span>{{ formatBatchTime(item.triggeredAt) }}</span>
            <span>Batch No. {{ item.batchNo }}</span>
          </div>
        </button>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowRight,
  CircleCheck,
  Clock,
  InfoFilled,
  Monitor,
  Refresh,
  Remove,
  VideoPlay,
} from '@element-plus/icons-vue'
import { getProjectList } from '@/api/project'
import {
  getManualQuestionPollBatch,
  getManualQuestionPollPlatforms,
  getRecentManualQuestionPollBatches,
  startManualQuestionPoll,
  type ManualQuestionPollBatchView,
  type ManualQuestionPollPlatformOption,
  type ManualQuestionPollPlatformProgress,
} from '@/api/dispatch'
import type { Project } from '@/types'
import {
  batchProgress,
  createClientRequestId,
  estimateLogicalResults,
  estimateMaxProviderCalls,
  isTerminalBatchStatus,
  safeVerificationSourceUrl,
} from './questionPollVerification'

const router = useRouter()
const route = useRoute()
const projects = ref<Project[]>([])
const platforms = ref<ManualQuestionPollPlatformOption[]>([])
const projectsLoading = ref(false)
const platformsLoading = ref(false)
const submitting = ref(false)
const refreshing = ref(false)
const historyOpen = ref(false)
const historyLoading = ref(false)
const historyBatches = ref<ManualQuestionPollBatchView[]>([])
const batch = ref<ManualQuestionPollBatchView | null>(null)
const resultPanel = ref<HTMLElement | null>(null)
const clientRequestId = ref(createClientRequestId())
let pollTimer: ReturnType<typeof setTimeout> | null = null

const form = reactive({
  projectId: undefined as number | undefined,
  questionTier: 'A' as 'A' | 'B' | 'C',
  questionLimit: 1,
  platformIds: [] as number[],
})

const tierOptions = [
  { label: 'A级核心问题', value: 'A' },
  { label: 'B级扩展问题', value: 'B' },
  { label: 'C级长尾问题', value: 'C' },
]

const flowSteps = [
  { title: '生成手工批次', description: '独立幂等键与批次号' },
  { title: '正式分片入队', description: '沿用 Dispatch 队列' },
  { title: '联网 Worker 执行', description: '读取最新平台配置' },
  { title: '结果与证据聚合', description: '不进入正式经营统计' },
]

const logicalResultCount = computed(() =>
  estimateLogicalResults(form.platformIds.length, form.questionLimit),
)
const maxProviderCallCount = computed(() =>
  estimateMaxProviderCalls(form.platformIds.length, form.questionLimit),
)
const canSubmit = computed(() =>
  Boolean(form.projectId) && form.platformIds.length > 0 && form.questionLimit > 0,
)
const progressPercent = computed(() => batchProgress(batch.value))
const isBatchTerminal = computed(() => isTerminalBatchStatus(batch.value?.status))

function invalidateRequestId() {
  clientRequestId.value = createClientRequestId()
}

async function loadProjects() {
  projectsLoading.value = true
  try {
    const response = await getProjectList({ current: 1, size: 500, status: 'active' })
    projects.value = (response.data.data.records || []).filter((project) => Boolean(project.activatedAt))
  } finally {
    projectsLoading.value = false
  }
}

async function loadPlatforms() {
  platformsLoading.value = true
  try {
    const response = await getManualQuestionPollPlatforms()
    platforms.value = response.data.data || []
    const selectable = platforms.value.filter((platform) => platform.selectable)
    if (selectable.length === 1) {
      form.platformIds = [selectable[0].platformId]
      invalidateRequestId()
    }
  } finally {
    platformsLoading.value = false
  }
}

async function confirmStart() {
  if (!canSubmit.value || !form.projectId) return
  const selectedNames = platforms.value
    .filter((platform) => form.platformIds.includes(platform.platformId))
    .map((platform) => platform.platformName)
    .join('、')
  try {
    await ElMessageBox.confirm(
      `将对 ${form.questionLimit} 个问题调用 ${selectedNames}，预计 ${logicalResultCount.value} 条逻辑结果，最多约 ${maxProviderCallCount.value} 次物理调用。确认开始？`,
      '创建手工验证批次',
      {
        confirmButtonText: '确认执行',
        cancelButtonText: '再检查一下',
        type: 'warning',
      },
    )
  } catch {
    return
  }
  await startBatch()
}

async function startBatch() {
  if (!form.projectId) return
  submitting.value = true
  stopPolling()
  try {
    const response = await startManualQuestionPoll({
      projectId: form.projectId,
      questionTier: form.questionTier,
      platformIds: [...form.platformIds],
      questionLimit: form.questionLimit,
      clientRequestId: clientRequestId.value,
    })
    batch.value = response.data.data
    await router.replace({
      query: { ...route.query, batchId: String(batch.value.batchId) },
    })
    ElMessage.success('手工验证批次已创建，正在沿正式链路执行')
    scheduleRefresh()
  } finally {
    submitting.value = false
  }
}

async function refreshBatch() {
  if (!batch.value || refreshing.value) return
  refreshing.value = true
  try {
    const response = await getManualQuestionPollBatch(batch.value.batchId)
    batch.value = response.data.data
  } finally {
    refreshing.value = false
  }
}

async function openHistory() {
  historyOpen.value = true
  await loadHistory()
}

async function loadHistory() {
  if (historyLoading.value) return
  historyLoading.value = true
  try {
    const response = await getRecentManualQuestionPollBatches(20)
    historyBatches.value = response.data.data || []
  } finally {
    historyLoading.value = false
  }
}

async function selectHistoryBatch(item: ManualQuestionPollBatchView) {
  historyLoading.value = true
  try {
    const response = await getManualQuestionPollBatch(item.batchId)
    batch.value = response.data.data
  } finally {
    historyLoading.value = false
  }
  historyOpen.value = false
  await router.replace({
    query: { ...route.query, batchId: String(item.batchId) },
  })
  scheduleRefresh()
  await nextTick()
  resultPanel.value?.scrollIntoView?.({ behavior: 'smooth', block: 'start' })
}

function formatBatchTime(value?: string | null) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value.replace('T', ' ')
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date)
}

function formatDuration(value?: number | null) {
  if (value === null || value === undefined) return '-'
  return value >= 1000 ? `${(value / 1000).toFixed(1)}s` : `${value}ms`
}

function searchStatusLabel(status?: string | null) {
  const labels: Record<string, string> = {
    TRIGGERED: '已确认联网',
    EMPTY: '搜索为空',
    NO_VALID_SOURCE: '无有效来源',
    FAILED: '搜索失败',
    NOT_CONFIRMED: '未确认联网',
  }
  return labels[String(status || '')] || '等待判定'
}

function citationPosition(start?: number | null, end?: number | null) {
  return start === null || start === undefined || end === null || end === undefined
    ? '回答位置未确认'
    : `回答位置 ${start}–${end}`
}

function citationConfidenceLabel(value?: string | null) {
  const labels: Record<string, string> = {
    CONFIRMED: '已确认',
    PROBABLE: '可能匹配',
    INVALID: '无效',
  }
  return labels[String(value || '')] || value || '未判定'
}

function scheduleRefresh() {
  stopPolling()
  if (!batch.value || isTerminalBatchStatus(batch.value.status)) return
  pollTimer = setTimeout(async () => {
    try {
      await refreshBatch()
    } finally {
      scheduleRefresh()
    }
  }, 2000)
}

function stopPolling() {
  if (pollTimer) {
    clearTimeout(pollTimer)
    pollTimer = null
  }
}

function batchStatusLabel(status: string) {
  const labels: Record<string, string> = {
    planning: '规划中',
    ready: '队列执行中',
    finished: '已完成',
    finished_with_failures: '完成但有失败',
    failed: '批次失败',
  }
  return labels[status] || status
}

function batchStatusType(status: string): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'finished') return 'success'
  if (status === 'finished_with_failures') return 'warning'
  if (status === 'failed') return 'danger'
  return 'info'
}

function platformProgress(row: ManualQuestionPollPlatformProgress) {
  if (!row.expectedCount) return 0
  return Math.min(100, Math.round(((row.completedCount + row.failedCount) / row.expectedCount) * 100))
}

function openTaskMonitor() {
  router.push({ path: '/admin/monitoring/tasks', query: { taskType: 'QUESTION_POLL' } })
}

async function restoreBatchFromRoute() {
  const rawBatchId = Array.isArray(route.query.batchId) ? route.query.batchId[0] : route.query.batchId
  const batchId = Number(rawBatchId)
  if (!Number.isSafeInteger(batchId) || batchId <= 0) return
  const response = await getManualQuestionPollBatch(batchId)
  batch.value = response.data.data
  scheduleRefresh()
}

onMounted(() => {
  void Promise.all([loadProjects(), loadPlatforms(), restoreBatchFromRoute()])
})

onBeforeUnmount(stopPolling)
</script>

<style scoped>
.verification-page {
  min-height: 100%;
  padding: 28px;
  background:
    radial-gradient(circle at 92% 0%, rgba(55, 109, 246, 0.1), transparent 27%),
    #f5f7fb;
  color: #172033;
}

.page-hero,
.panel-heading,
.panel-heading > div,
.hero-actions,
.result-actions,
.platform-card-title,
.platform-label,
.summary-copy,
.flow-step,
.project-option {
  display: flex;
  align-items: center;
}

.page-hero {
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 22px;
}

.hero-actions {
  gap: 10px;
}

.eyebrow {
  margin-bottom: 6px;
  color: #2f67e9;
  font: 700 12px/1.4 "JetBrains Mono", monospace;
  letter-spacing: 0.14em;
}

.page-hero h1 {
  margin: 0;
  font-size: 32px;
  letter-spacing: -0.04em;
}

.page-hero p,
.panel-heading p {
  margin: 6px 0 0;
  color: #758198;
}

.flow-strip {
  display: grid;
  grid-template-columns: 1fr auto 1fr auto 1fr auto 1fr;
  align-items: center;
  margin-bottom: 18px;
  padding: 16px 20px;
  border: 1px solid #e4e9f2;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.78);
}

.flow-step {
  gap: 11px;
  min-width: 0;
}

.flow-step > span,
.section-index {
  display: inline-grid;
  flex: 0 0 auto;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: #edf3ff;
  color: #3268e9;
  font: 700 12px/1 "JetBrains Mono", monospace;
}

.flow-step div {
  display: grid;
  min-width: 0;
}

.flow-step strong {
  font-size: 14px;
}

.flow-step small {
  overflow: hidden;
  margin-top: 3px;
  color: #8b95a8;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.flow-arrow {
  color: #b1bbcc;
}

.workspace-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 310px;
  gap: 18px;
  align-items: start;
}

.panel {
  border: 1px solid #e3e8f1;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 12px 36px rgba(24, 38, 67, 0.05);
}

.configuration-panel,
.guardrail-panel,
.result-panel {
  padding: 22px;
}

.panel-heading {
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 22px;
}

.panel-heading > div {
  gap: 12px;
}

.panel-heading h2 {
  margin: 0;
  font-size: 18px;
}

.panel-heading p {
  font-size: 13px;
}

.panel-heading.compact {
  margin-bottom: 16px;
}

.form-grid {
  display: grid;
  grid-template-columns: minmax(240px, 1.2fr) minmax(240px, 1fr) 180px;
  gap: 16px;
}

.verification-form :deep(.el-select),
.verification-form :deep(.el-input-number) {
  width: 100%;
}

.field-help {
  margin-top: 7px;
  color: #8a95a8;
  font-size: 12px;
}

.platform-field {
  margin-bottom: 16px;
}

.platform-label {
  justify-content: space-between;
  width: 100%;
}

.platform-label span:last-child {
  color: #929cad;
  font-size: 12px;
  font-weight: 400;
}

.platform-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  width: 100%;
}

.platform-card {
  width: 100%;
  height: auto;
  margin: 0;
  padding: 14px;
  border: 1px solid #e1e6ef;
  border-radius: 12px;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.platform-card:hover {
  border-color: #aabff6;
  box-shadow: 0 7px 18px rgba(47, 103, 233, 0.08);
}

.platform-card.is-checked {
  border-color: #4a78ea;
  background: #f6f9ff;
}

.platform-card :deep(.el-checkbox__label) {
  width: calc(100% - 22px);
  padding-left: 10px;
}

.platform-card-body {
  display: grid;
  gap: 5px;
  width: 100%;
  white-space: normal;
}

.platform-card-title {
  justify-content: space-between;
  gap: 8px;
}

.platform-card-body > span {
  color: #45526a;
  font: 500 12px/1.5 "JetBrains Mono", monospace;
}

.platform-card-body small {
  color: #929cad;
}

.platform-card-body em {
  color: #c66f00;
  font-size: 12px;
  font-style: normal;
}

.platform-loading {
  width: 100%;
}

.execution-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 16px;
  border-radius: 13px;
  background: #f4f7fd;
}

.summary-copy {
  gap: 11px;
  color: #3268e9;
}

.summary-copy > div {
  display: grid;
  gap: 3px;
}

.summary-copy span {
  color: #6d7890;
  font-size: 12px;
}

.guardrail-list {
  display: grid;
  gap: 14px;
  margin: 0;
  padding: 4px 0 0;
  list-style: none;
}

.guardrail-list li {
  display: grid;
  grid-template-columns: 20px 1fr;
  gap: 9px;
  color: #58647a;
  font-size: 13px;
  line-height: 1.55;
}

.guardrail-list li:nth-child(-n + 3) .el-icon {
  color: #17a875;
}

.guardrail-list li:nth-child(n + 4) .el-icon {
  color: #8d98aa;
}

.result-panel {
  margin-top: 18px;
}

.result-actions {
  gap: 10px;
}

.progress-overview {
  display: grid;
  grid-template-columns: 180px 1fr;
  gap: 22px;
  padding: 18px;
  border: 1px solid #e7ebf3;
  border-radius: 15px;
  background: #fafbfe;
}

.progress-ring {
  display: grid;
  justify-items: center;
  align-content: center;
  border-right: 1px solid #e3e8f1;
  color: #7c879b;
  font-size: 12px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(110px, 1fr));
  gap: 10px;
}

.metric-card {
  display: grid;
  align-content: center;
  min-height: 104px;
  padding: 14px;
  border: 1px solid #e4e9f2;
  border-radius: 12px;
  background: #fff;
}

.metric-card span,
.metric-card small {
  color: #8994a7;
  font-size: 12px;
}

.metric-card strong {
  margin: 5px 0;
  font: 700 25px/1.1 "JetBrains Mono", monospace;
}

.metric-card.success strong {
  color: #118a62;
}

.metric-card.danger strong {
  color: #d94c4c;
}

.platform-progress-table {
  margin-top: 18px;
}

.result-detail-section {
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid #e6eaf1;
}

.detail-heading,
.result-collapse-title,
.result-facts,
.source-detail-card,
.citation-detail-list > div {
  display: flex;
  align-items: center;
}

.detail-heading {
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}

.detail-heading h3,
.answer-block h4,
.evidence-block h4 {
  margin: 0;
}

.detail-heading p {
  margin: 5px 0 0;
  color: #8994a7;
  font-size: 12px;
}

.result-collapse {
  border: 1px solid #e4e9f2;
  border-radius: 13px;
  overflow: hidden;
}

.result-collapse :deep(.el-collapse-item__header) {
  height: auto;
  min-height: 58px;
  padding: 10px 16px;
  line-height: 1.4;
}

.result-collapse :deep(.el-collapse-item__content) {
  padding: 0 16px 18px;
}

.result-collapse-title {
  flex: 1;
  gap: 10px;
  min-width: 0;
  padding-right: 12px;
}

.result-collapse-title strong {
  overflow: hidden;
  flex: 1;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.result-platform {
  flex: 0 0 auto;
  color: #3268e9;
  font-size: 12px;
  font-weight: 700;
}

.result-detail-body {
  display: grid;
  gap: 16px;
  padding: 16px;
  border-radius: 12px;
  background: #f7f9fc;
}

.result-facts {
  flex-wrap: wrap;
  gap: 8px;
}

.result-facts span {
  padding: 5px 9px;
  border: 1px solid #e2e7ef;
  border-radius: 8px;
  background: #fff;
  color: #718096;
  font-size: 12px;
}

.answer-block,
.evidence-block {
  display: grid;
  gap: 10px;
}

.answer-block > div {
  max-height: 360px;
  overflow: auto;
  padding: 14px;
  border: 1px solid #e1e7ef;
  border-radius: 10px;
  background: #fff;
  color: #33405a;
  line-height: 1.75;
  white-space: pre-wrap;
}

.source-detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.source-detail-card {
  align-items: flex-start;
  gap: 9px;
  padding: 11px;
  border: 1px solid #e1e7ef;
  border-radius: 10px;
  background: #fff;
}

.source-detail-card > span {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 24px;
  height: 24px;
  border-radius: 7px;
  background: #edf3ff;
  color: #3268e9;
  font-weight: 700;
}

.source-detail-card > div {
  display: grid;
  min-width: 0;
  gap: 4px;
}

.source-detail-card a,
.source-detail-card strong {
  overflow: hidden;
  color: #26344f;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.source-detail-card a:hover {
  color: #3268e9;
}

.source-detail-card small,
.citation-detail-list small {
  color: #8b96a9;
}

.citation-detail-list {
  display: grid;
  gap: 7px;
}

.citation-detail-list > div {
  gap: 12px;
  padding: 10px 12px;
  border-radius: 9px;
  background: #fff;
  font-size: 12px;
}

.citation-detail-list span {
  color: #3268e9;
  font-weight: 700;
}

.citation-detail-list strong {
  flex: 1;
}

.table-platform {
  display: grid;
  gap: 3px;
}

.table-platform span,
.platform-progress-table small {
  color: #8c97aa;
  font-size: 12px;
}

.mini-statuses {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 10px;
  color: #7a8598;
  font-size: 12px;
}

.mini-statuses .positive {
  color: #108760;
}

.mini-statuses .negative {
  color: #d54b4b;
}

.terminal-alert {
  margin-top: 16px;
}

.project-option {
  justify-content: space-between;
  gap: 20px;
}

.project-option small {
  color: #9aa4b5;
}

.history-intro {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
  padding: 14px 16px;
  border-radius: 12px;
  background: #f4f7fd;
}

.history-intro > div {
  display: grid;
  gap: 4px;
}

.history-intro span {
  color: #7b879b;
  font-size: 12px;
}

.history-list {
  display: grid;
  gap: 10px;
}

.history-card {
  display: grid;
  gap: 13px;
  width: 100%;
  padding: 16px;
  border: 1px solid #e1e7f0;
  border-radius: 13px;
  background: #fff;
  color: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.18s, box-shadow 0.18s, transform 0.18s;
}

.history-card:hover,
.history-card:focus-visible {
  border-color: #8ba9f2;
  box-shadow: 0 8px 22px rgba(47, 103, 233, 0.1);
  outline: none;
  transform: translateY(-1px);
}

.history-card.active {
  border-color: #4b79e9;
  background: #f7f9ff;
}

.history-card-top,
.history-card-footer,
.history-metrics {
  display: flex;
  align-items: center;
}

.history-card-top,
.history-card-footer {
  justify-content: space-between;
  gap: 12px;
}

.history-card-top > div {
  display: grid;
  gap: 4px;
}

.history-card-top span,
.history-card-footer {
  color: #8994a7;
  font-size: 12px;
}

.history-metrics {
  flex-wrap: wrap;
  gap: 8px;
}

.history-metrics span {
  padding: 5px 9px;
  border-radius: 8px;
  background: #f4f6fa;
  color: #69758a;
  font-size: 12px;
}

.history-metrics .success {
  color: #11855f;
  background: #eefaf5;
}

.history-metrics .danger {
  color: #cf4848;
  background: #fff1f1;
}

@media (max-width: 1200px) {
  .workspace-grid {
    grid-template-columns: 1fr;
  }

  .flow-strip {
    grid-template-columns: repeat(4, 1fr);
  }

  .flow-arrow {
    display: none;
  }

  .metric-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 760px) {
  .verification-page {
    padding: 16px;
  }

  .page-hero,
  .execution-summary,
  .panel-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .flow-strip,
  .form-grid,
  .platform-grid,
  .progress-overview,
  .metric-grid,
  .source-detail-grid {
    grid-template-columns: 1fr;
  }

  .progress-ring {
    padding-bottom: 16px;
    border-right: 0;
    border-bottom: 1px solid #e3e8f1;
  }
}
</style>
