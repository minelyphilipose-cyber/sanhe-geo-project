<template>
  <div class="retention-page admin-page">
    <header class="admin-page-header retention-header">
      <div>
        <h1 class="admin-page-title">数据保留本地验收</h1>
        <p class="admin-page-subtitle">先核验迁移、静态门禁、存储与动态状态，再运行只生成审计证据的 dry-run。</p>
      </div>
      <el-button :icon="Refresh" :loading="statusLoading" @click="loadPhaseZero">刷新 Phase 0</el-button>
    </header>

    <div class="scope-notice" role="status">
      <div class="scope-notice__mark">DRY</div>
      <div>
        <strong>本页不会清理业务数据</strong>
        <span>只读状态接口不写数据；dry-run 会写 retention run / attempt 审计，但不执行 mutation、canary、开关写入或 detail cleanup。</span>
      </div>
    </div>

    <section class="phase-panel" aria-labelledby="phase-zero-title">
      <div class="phase-panel__head">
        <div>
          <h2 id="phase-zero-title">Phase 0 · 环境门禁</h2>
          <p>目标库需包含 V365–V368，所有 mutation 静态门禁保持关闭。</p>
        </div>
        <el-tag v-if="phaseZeroReady" type="success" effect="dark">可进行本地 dry-run</el-tag>
        <el-tag v-else-if="status" type="danger" effect="dark">门禁未通过</el-tag>
        <el-tag v-else type="info">待检查</el-tag>
      </div>

      <div v-if="statusLoading" class="loading-state">
        <el-skeleton :rows="3" animated />
      </div>
      <el-alert v-else-if="statusError" type="error" :closable="false" show-icon>
        <template #title>Phase 0 读取失败</template>
        {{ statusError }}。请确认后端、MySQL、Redis 和当前账号权限后重试。
      </el-alert>
      <template v-else-if="status">
        <div class="status-strip status-strip--five">
          <div>
            <span>数据库版本</span>
            <strong>V{{ status.schema.currentVersion || '未知' }}</strong>
            <small :class="status.schema.compatibleForLocalAcceptance ? 'is-ok' : 'is-error'">
              {{ status.schema.compatibleForLocalAcceptance ? 'V365–V368 完整' : `缺少 ${status.schema.missingRequiredVersions.join('、') || '迁移证据'}` }}
            </small>
          </div>
          <div>
            <span>Mutation 静态门禁</span>
            <strong>{{ status.allStaticMutationGatesClosed ? '全部关闭' : '存在开启项' }}</strong>
            <small :class="status.allStaticMutationGatesClosed ? 'is-ok' : 'is-error'">{{ openGateSummary }}</small>
          </div>
          <div>
            <span>审计 writer 版本</span>
            <strong>{{ status.auditWriter.applicationVersion || '未配置' }}</strong>
            <small :class="status.auditWriter.configured ? 'is-ok' : 'is-error'">{{ status.auditWriter.configured ? '部署身份可写入审计' : status.auditWriter.errorCode }}</small>
          </div>
          <div>
            <span>对象存储</span>
            <strong>{{ storageLabel }}</strong>
            <small :class="status.storage.provider === 'MINIO' ? 'is-ok' : ''">{{ storageDetail }}</small>
          </div>
          <div>
            <span>WP8 readiness</span>
            <strong>{{ status.detailCleanupReadiness.primaryCode }}</strong>
            <small :class="status.detailCleanupReadiness.ready ? 'is-ok' : 'is-warning'">{{ status.detailCleanupReadiness.severity }}</small>
          </div>
        </div>

        <div class="phase-details">
          <div class="gate-list">
            <div class="detail-title">静态配置</div>
            <div v-for="gate in gateRows" :key="gate.key" class="gate-row">
              <span>{{ gate.label }}</span>
              <el-tag size="small" :type="gate.enabled ? 'danger' : 'success'">{{ gate.enabled ? '已开启' : '已关闭' }}</el-tag>
            </div>
          </div>
          <div class="switch-list">
            <div class="detail-title">Redis 动态开关（只读）</div>
            <el-empty v-if="!switches.length" :image-size="44" description="无动态开关快照" />
            <div v-for="item in switches" :key="item.switchKey" class="switch-row">
              <div>
                <strong>{{ switchLabel(item.switchKey) }}</strong>
                <small>一致性：{{ item.runtime?.integrity || 'UNKNOWN' }} · 审计差异：{{ item.divergence }}</small>
              </div>
              <el-tag size="small" :type="runtimeEnabled(item) ? 'danger' : 'success'">
                {{ runtimeEnabled(item) ? 'enabled' : 'disabled' }}
              </el-tag>
            </div>
          </div>
        </div>
      </template>
    </section>

    <section class="phase-panel" aria-labelledby="phase-one-title">
      <div class="phase-panel__head">
        <div>
          <h2 id="phase-one-title">Phase 1 · 运营样本 dry-run</h2>
          <p>每项独立执行并保留 retentionRunId。按钮仅在 Phase 0 通过后可用。</p>
        </div>
        <span class="audit-note">执行后会新增审计行</span>
      </div>

      <div class="scenario-list">
        <article class="scenario">
          <div class="scenario__intro">
            <span class="scenario__index">A</span>
            <div>
              <h3>Website 已发布内容</h3>
              <p>正例 990007577 应命中；当日文章 990007920 必须排除。</p>
            </div>
          </div>
          <el-form :model="websiteForm" label-position="top" class="scenario__form">
            <el-form-item label="保留小时"><el-input-number v-model="websiteForm.retentionHours" :min="24" :max="8760" /></el-form-item>
            <el-form-item label="起始游标"><el-input-number v-model="websiteForm.cursorArticleId" :min="1" :controls="false" /></el-form-item>
            <el-form-item label="数量上限"><el-input-number v-model="websiteForm.limit" :min="1" :max="20" /></el-form-item>
            <el-button type="primary" plain :loading="running.website" :disabled="!phaseZeroReady" @click="runWebsite">运行 Website dry-run</el-button>
          </el-form>
          <ResultState :error="errors.website" :result="results.website || undefined">
            <template #summary>
              <SummaryLine :run-id="results.website?.retentionRunId" :items="[
                ['候选文章', results.website?.candidateCount],
                ['正文估算', formatBytes(websiteBodyBytes)],
                ['Payload 估算', formatBytes(websitePayloadBytes)],
              ]" />
            </template>
            <el-table :data="results.website?.items || []" border size="small">
              <el-table-column prop="articleId" label="Article ID" width="130" />
              <el-table-column prop="projectId" label="Project ID" width="130" />
              <el-table-column prop="lastPublishedAt" label="最近发布" min-width="170" />
              <el-table-column label="正文 / Payload" min-width="180">
                <template #default="{ row }">{{ row.bodyRowCount }} 行 · {{ formatBytes(row.bodyBytes) }} / {{ row.payloadRowCount }} 行 · {{ formatBytes(row.payloadBytes) }}</template>
              </el-table-column>
              <el-table-column label="样本判断" width="120">
                <template #default="{ row }"><el-tag :type="row.articleId === 990007577 ? 'success' : 'info'">{{ row.articleId === 990007577 ? '目标正例' : row.result }}</el-tag></template>
              </el-table-column>
            </el-table>
          </ResultState>
        </article>

        <article class="scenario">
          <div class="scenario__intro">
            <span class="scenario__index">B</span>
            <div>
              <h3>Poll 历史 Slice</h3>
              <p>项目 990006017、2026-07-08、A：预期 28 条可清、2 条 latest 保留。</p>
            </div>
          </div>
          <el-form :model="pollForm" label-position="top" class="scenario__form scenario__form--wide">
            <el-form-item label="Project ID"><el-input-number v-model="pollForm.projectId" :min="1" :controls="false" /></el-form-item>
            <el-form-item label="开始日期"><el-date-picker v-model="pollForm.startDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
            <el-form-item label="结束日期"><el-date-picker v-model="pollForm.endDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
            <el-form-item label="Tier"><el-select v-model="pollForm.questionTier"><el-option label="A" value="A" /></el-select></el-form-item>
            <el-button type="primary" plain :loading="running.poll" :disabled="!phaseZeroReady" @click="runPoll">运行 Poll dry-run</el-button>
          </el-form>
          <ResultState :error="errors.poll" :result="results.poll || undefined">
            <template #summary>
              <SummaryLine :run-id="results.poll?.retentionRunId" :items="[
                ['候选 Slice', results.poll?.candidateSlices],
                ['可清 Slice', results.poll?.eligibleSlices],
                ['结果总数', results.poll?.pollResultRows],
                ['非 latest', pollNonLatestRows],
                ['Entity Judge', results.poll?.entityJudgeRows],
              ]" />
            </template>
            <el-table :data="results.poll?.slices || []" border size="small">
              <el-table-column prop="batchDate" label="日期" width="120" />
              <el-table-column prop="questionTier" label="Tier" width="80" />
              <el-table-column label="资格" width="100"><template #default="{ row }"><el-tag :type="row.eligible ? 'success' : 'warning'">{{ row.eligible ? '可清理' : '阻断' }}</el-tag></template></el-table-column>
              <el-table-column prop="pollResultRows" label="结果总数" width="100" />
              <el-table-column label="非 latest" width="100"><template #default="{ row }">{{ Math.max(0, row.pollResultRows - row.latestLiveResultRows) }}</template></el-table-column>
              <el-table-column prop="latestLiveResultRows" label="保留 latest" width="110" />
              <el-table-column label="关联明细" min-width="260"><template #default="{ row }">attempt {{ row.invocationAttemptRows }} · provider {{ row.providerCallRows }} · source {{ row.searchSourceRows }} · citation {{ row.citationRows }} · judge {{ row.entityJudgeRows }}</template></el-table-column>
              <el-table-column label="阻断/警告" min-width="190"><template #default="{ row }">{{ [...(row.blockedReasons || []), ...(row.warnings || [])].join('、') || '-' }}</template></el-table-column>
            </el-table>
          </ResultState>
        </article>

        <article class="scenario">
          <div class="scenario__intro">
            <span class="scenario__index">C</span>
            <div>
              <h3>归档正文近期反例</h3>
              <p>项目 990006017 的近期版本不应进入 90 天归档候选。</p>
            </div>
          </div>
          <el-form :model="archiveForm" label-position="top" class="scenario__form">
            <el-form-item label="Project ID"><el-input-number v-model="archiveForm.projectId" :min="1" :controls="false" /></el-form-item>
            <el-form-item label="最小发布天数"><el-input-number v-model="archiveForm.minPublishedAgeDays" :min="90" /></el-form-item>
            <el-form-item label="数量上限"><el-input-number v-model="archiveForm.limit" :min="1" :max="20" /></el-form-item>
            <el-button type="primary" plain :loading="running.archive" :disabled="!phaseZeroReady" @click="runArchive">运行归档 dry-run</el-button>
          </el-form>
          <ResultState :error="errors.archive" :result="results.archive || undefined">
            <template #summary><SummaryLine :run-id="results.archive?.retentionRunId" :items="[['候选版本', results.archive?.candidateCount], ['可归档', results.archive?.eligibleCount], ['被阻断', results.archive?.blockedCount], ['估算正文', formatBytes(results.archive?.estimatedBytes || 0)]]" /></template>
            <ArticleResultTable :items="results.archive?.items || []" />
          </ResultState>
        </article>

        <article class="scenario">
          <div class="scenario__intro">
            <span class="scenario__index">D</span>
            <div>
              <h3>已归档正文阻断验证</h3>
              <p>项目 990006018 的 990006110/v1 应因活动任务 990006063 被阻断。</p>
            </div>
          </div>
          <el-form :model="purgeForm" label-position="top" class="scenario__form scenario__form--wide">
            <el-form-item label="Project ID"><el-input-number v-model="purgeForm.projectId" :min="1" :controls="false" /></el-form-item>
            <el-form-item label="保留天数"><el-input-number v-model="purgeForm.retentionDays" :min="90" /></el-form-item>
            <el-form-item label="归档宽限小时"><el-input-number v-model="purgeForm.archiveGraceHours" :min="24" /></el-form-item>
            <el-form-item label="数量上限"><el-input-number v-model="purgeForm.limit" :min="1" :max="20" /></el-form-item>
            <el-button type="primary" plain :loading="running.purge" :disabled="!phaseZeroReady" @click="runPurge">运行正文清理 dry-run</el-button>
          </el-form>
          <ResultState :error="errors.purge" :result="results.purge || undefined">
            <template #summary><SummaryLine :run-id="results.purge?.retentionRunId" :items="[['候选版本', results.purge?.candidateCount], ['可清理', results.purge?.eligibleCount], ['被阻断', results.purge?.blockedCount], ['估算正文', formatBytes(results.purge?.estimatedBytes || 0)]]" /></template>
            <ArticleResultTable :items="results.purge?.items || []" />
          </ResultState>
        </article>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, onMounted, reactive, ref } from 'vue'
import { ElAlert, ElEmpty, ElMessage, ElTable, ElTableColumn, ElTag } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import type { ApiError } from '@/api/request'
import {
  getDataRetentionDynamicSwitches,
  getDataRetentionOperationsStatus,
  runArticleArchiveDryRun,
  runArticlePurgeDryRun,
  runPollRetentionDryRun,
  runWebsiteRetentionDryRun,
  type ArticleDryRunItem,
  type ArticleDryRunResult,
  type DataRetentionOperationsStatus,
  type DynamicSwitchView,
  type PollDryRunResult,
  type WebsiteDryRunResult,
} from '@/api/dataRetentionOperations'

type ScenarioKey = 'website' | 'poll' | 'archive' | 'purge'

const ResultState = defineComponent({
  props: { error: { type: String, default: '' }, result: { type: Object, default: null } },
  setup(props, { slots }) {
    return () => h('div', { class: 'result-state' }, [
      props.error ? h(ElAlert, { type: 'error', title: props.error, closable: false, showIcon: true }) : null,
      props.result ? h('div', { class: 'result-content' }, [slots.summary?.(), slots.default?.()]) : null,
      !props.error && !props.result ? h(ElEmpty, { imageSize: 42, description: '尚未运行' }) : null,
    ])
  },
})

const SummaryLine = defineComponent({
  props: { runId: { type: Number, default: undefined }, items: { type: Array as () => Array<[string, unknown]>, default: () => [] } },
  setup(props) {
    return () => h('div', { class: 'result-summary' }, [
      h('div', { class: 'run-id' }, [h('span', 'retentionRunId'), h('strong', props.runId == null ? '-' : String(props.runId))]),
      ...props.items.map(([label, value]) => h('div', [h('span', label), h('strong', value == null ? '-' : String(value))])),
    ])
  },
})

const ArticleResultTable = defineComponent({
  props: { items: { type: Array as () => ArticleDryRunItem[], default: () => [] } },
  setup(props) {
    return () => h(ElTable, { data: props.items, border: true, size: 'small' }, () => [
      h(ElTableColumn, { prop: 'articleId', label: 'Article ID', width: 125 }),
      h(ElTableColumn, { prop: 'versionId', label: 'Version ID', width: 115 }),
      h(ElTableColumn, { prop: 'publishedAt', label: '发布时间', minWidth: 170 }),
      h(ElTableColumn, { label: '资格', width: 100 }, { default: ({ row }: { row: ArticleDryRunItem }) => h(ElTag, { type: row.eligible ? 'success' : 'warning' }, () => row.eligible ? '可处理' : '阻断') }),
      h(ElTableColumn, { label: '阻断原因', minWidth: 220 }, { default: ({ row }: { row: ArticleDryRunItem }) => row.blockedReasons?.join('、') || '-' }),
      h(ElTableColumn, { label: '活动任务', width: 100 }, { default: ({ row }: { row: ArticleDryRunItem }) => String(row.activeDistributionTaskCount ?? 0) }),
      h(ElTableColumn, { prop: 'action', label: '计划动作', minWidth: 130 }),
    ])
  },
})

const statusLoading = ref(false)
const statusError = ref('')
const status = ref<DataRetentionOperationsStatus | null>(null)
const switches = ref<DynamicSwitchView[]>([])
const running = reactive<Record<ScenarioKey, boolean>>({ website: false, poll: false, archive: false, purge: false })
const errors = reactive<Record<ScenarioKey, string>>({ website: '', poll: '', archive: '', purge: '' })
const results = reactive<{
  website: WebsiteDryRunResult | null
  poll: PollDryRunResult | null
  archive: ArticleDryRunResult | null
  purge: ArticleDryRunResult | null
}>({ website: null, poll: null, archive: null, purge: null })

const websiteForm = reactive({ retentionHours: 24, cursorArticleId: 990007576, limit: 20, reason: 'local-operations-acceptance' })
const pollForm = reactive({ projectId: 990006017, startDate: '2026-07-08', endDate: '2026-07-08', questionTier: 'A', hotRetentionDays: 14, limit: 20, reason: 'local-operations-acceptance' })
const archiveForm = reactive({ dryRun: true as const, projectId: 990006017, minPublishedAgeDays: 90, limit: 20, reason: 'local-operations-acceptance' })
const purgeForm = reactive({ projectId: 990006018, retentionDays: 90, archiveGraceHours: 24, limit: 20, reason: 'local-operations-acceptance' })

const phaseZeroReady = computed(() => Boolean(status.value?.schema.compatibleForLocalAcceptance && status.value?.auditWriter.configured && status.value?.allStaticMutationGatesClosed))
const gateLabels: Record<string, string> = {
  schedulerExecuteEnabled: 'Scheduler execute', websiteExecuteEnabled: 'Website execute', articleArchiveExecuteEnabled: 'Article archive execute',
  articlePurgeExecuteEnabled: 'Article purge execute', pollExecuteEnabled: 'Poll execute', contentUrlRewriteExecuteEnabled: 'URL rewrite execute',
  objectStorageMigrationExecuteEnabled: '对象存储迁移 execute', executePromotionEnabled: 'Execute promotion', dynamicEnableWriteEnabled: '动态 enable-write', detailCleanupEnabled: 'WP8 detail cleanup',
}
const gateRows = computed(() => Object.entries(gateLabels).map(([key, label]) => ({ key, label, enabled: Boolean(status.value?.staticSafetyGates[key as keyof typeof status.value.staticSafetyGates]) })))
const openGateSummary = computed(() => {
  const opened = gateRows.value.filter((item) => item.enabled).map((item) => item.label)
  return opened.length ? opened.join('、') : 'execute / enable-write / cleanup 均为 false'
})
const storageLabel = computed(() => status.value?.storage.provider === 'MINIO' ? 'MinIO 本地' : status.value?.storage.provider || '未知')
const storageDetail = computed(() => status.value?.storage.cosTargetConfigured ? '检测到 COS 目标参数，但当前 provider 仍为 MinIO' : 'COS 未配置；对象存储迁移不适用')
const websiteBodyBytes = computed(() => (results.website?.items || []).reduce((sum, item) => sum + Number(item.bodyBytes || 0), 0))
const websitePayloadBytes = computed(() => (results.website?.items || []).reduce((sum, item) => sum + Number(item.payloadBytes || 0), 0))
const pollNonLatestRows = computed(() => (results.poll?.slices || []).reduce(
  (sum, item) => sum + Math.max(0, Number(item.pollResultRows || 0) - Number(item.latestLiveResultRows || 0)), 0,
))

async function loadPhaseZero() {
  statusLoading.value = true
  statusError.value = ''
  try {
    const [statusResponse, switchResponse] = await Promise.all([getDataRetentionOperationsStatus(), getDataRetentionDynamicSwitches()])
    status.value = statusResponse.data.data
    switches.value = switchResponse.data.data || []
  } catch (error) {
    status.value = null
    switches.value = []
    statusError.value = errorMessage(error)
  } finally {
    statusLoading.value = false
  }
}

async function executeScenario<T>(key: ScenarioKey, action: () => Promise<{ data: { data: T } }>) {
  running[key] = true
  errors[key] = ''
  try {
    const response = await action()
    ;(results as Record<ScenarioKey, unknown>)[key] = response.data.data
    ElMessage.success(`${scenarioLabel(key)} dry-run 已完成`)
  } catch (error) {
    ;(results as Record<ScenarioKey, unknown>)[key] = null
    errors[key] = errorMessage(error)
  } finally {
    running[key] = false
  }
}

function runWebsite() { return executeScenario('website', () => runWebsiteRetentionDryRun({ ...websiteForm })) }
function runPoll() { return executeScenario('poll', () => runPollRetentionDryRun({ ...pollForm })) }
function runArchive() { return executeScenario('archive', () => runArticleArchiveDryRun({ ...archiveForm })) }
function runPurge() { return executeScenario('purge', () => runArticlePurgeDryRun({ ...purgeForm })) }
function scenarioLabel(key: ScenarioKey) { return ({ website: 'Website', poll: 'Poll', archive: '文章归档', purge: '正文清理' })[key] }
function errorMessage(error: unknown) { return (error as ApiError)?.message || '请求失败，请检查服务日志后重试' }
function runtimeEnabled(item: DynamicSwitchView) { return item.runtime?.payload?.enabled === true }
function switchLabel(key: string) { return ({ GLOBAL_EXECUTE: '全局 execute', WEBSITE_EXECUTE: 'Website execute', POLL_EXECUTE: 'Poll execute' } as Record<string, string>)[key] || key }
function formatBytes(value: number) {
  if (!Number.isFinite(value) || value <= 0) return '0 B'
  if (value < 1024) return `${value} B`
  return `${(value / 1024).toFixed(value < 10240 ? 2 : 1)} KiB`
}

onMounted(loadPhaseZero)
</script>

<style scoped>
.retention-page { display: grid; gap: 16px; color: var(--admin-text-strong, #0f172a); }
.retention-header { align-items: center; margin-bottom: 0; }
.retention-header p { max-width: 72ch; }
.scope-notice { display: flex; align-items: center; gap: 14px; padding: 15px 18px; border: 1px solid #bae6fd; border-radius: 14px; background: #f0f9ff; box-shadow: 0 7px 20px rgba(14, 116, 144, .06); }
.scope-notice__mark { display: grid; place-items: center; flex: 0 0 44px; height: 44px; border-radius: 10px; background: #075985; color: #fff; font-family: 'JetBrains Mono', monospace; font-size: 12px; font-weight: 800; letter-spacing: .06em; }
.scope-notice strong, .scope-notice span { display: block; }
.scope-notice strong { color: #0c4a6e; font-size: 15px; }
.scope-notice span { margin-top: 3px; color: #075985; font-size: 13px; line-height: 1.55; }
.phase-panel { overflow: hidden; border: 1px solid var(--admin-panel-border, #e2e8f0); border-radius: 16px; background: #fff; box-shadow: 0 10px 28px rgba(15, 23, 42, .055); }
.phase-panel__head { display: flex; align-items: center; justify-content: space-between; gap: 18px; padding: 18px 20px; border-bottom: 1px solid #e9eef5; background: #fbfdff; }
.phase-panel__head h2 { margin: 0; font-size: 18px; line-height: 1.35; letter-spacing: -.01em; }
.phase-panel__head p { margin: 5px 0 0; color: #64748b; font-size: 13px; }
.loading-state { padding: 24px; }
.status-strip { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); border-bottom: 1px solid #e9eef5; }
.status-strip--five { grid-template-columns: repeat(5, minmax(0, 1fr)); }
.status-strip > div { min-width: 0; padding: 18px 20px; border-right: 1px solid #e9eef5; }
.status-strip > div:last-child { border-right: 0; }
.status-strip span, .status-strip small { display: block; }
.status-strip span { color: #64748b; font-size: 12px; font-weight: 700; }
.status-strip strong { display: block; margin: 7px 0 5px; overflow-wrap: anywhere; font-size: 19px; }
.status-strip small { color: #64748b; font-size: 12px; line-height: 1.45; }
.is-ok { color: #047857 !important; }.is-error { color: #b91c1c !important; }.is-warning { color: #b45309 !important; }
.phase-details { display: grid; grid-template-columns: minmax(0, 1.1fr) minmax(0, .9fr); }
.gate-list, .switch-list { padding: 18px 20px; }
.gate-list { border-right: 1px solid #e9eef5; }
.detail-title { margin-bottom: 9px; color: #334155; font-size: 13px; font-weight: 800; }
.gate-row, .switch-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; min-height: 38px; border-bottom: 1px solid #f1f5f9; font-size: 13px; }
.gate-row:last-child, .switch-row:last-child { border-bottom: 0; }
.switch-row strong, .switch-row small { display: block; }.switch-row small { margin-top: 2px; color: #64748b; }
.audit-note { color: #92400e; font-size: 12px; font-weight: 700; }
.scenario-list { display: grid; }
.scenario { display: grid; grid-template-columns: minmax(220px, .62fr) minmax(440px, 1.15fr) minmax(360px, 1.25fr); gap: 20px; padding: 22px 20px; border-bottom: 1px solid #e9eef5; }
.scenario:last-child { border-bottom: 0; }
.scenario__intro { display: flex; align-items: flex-start; gap: 12px; }
.scenario__index { display: grid; place-items: center; flex: 0 0 34px; height: 34px; border-radius: 9px; background: #e0f2fe; color: #0369a1; font-weight: 900; }
.scenario h3 { margin: 1px 0 5px; font-size: 15px; }.scenario p { margin: 0; color: #64748b; font-size: 12px; line-height: 1.6; }
.scenario__form { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); align-items: end; gap: 10px; }
.scenario__form--wide { grid-template-columns: repeat(4, minmax(0, 1fr)); }
.scenario__form :deep(.el-form-item) { margin-bottom: 0; }.scenario__form :deep(.el-input-number), .scenario__form :deep(.el-select), .scenario__form :deep(.el-date-editor) { width: 100%; }
.scenario__form > .el-button { grid-column: 1 / -1; min-height: 38px; }
.result-state { min-width: 0; align-self: stretch; border: 1px solid #e2e8f0; border-radius: 12px; background: #f8fafc; }
.result-state :deep(.el-empty) { padding: 13px 0; }.result-state :deep(.el-empty__description) { margin-top: 4px; }
.result-state :deep(.result-content) { padding: 10px; }
.result-state :deep(.result-summary) { display: flex; gap: 8px; margin-bottom: 10px; overflow-x: auto; }
.result-state :deep(.result-summary > div) { min-width: 86px; padding: 7px 9px; border-radius: 8px; background: #fff; }
.result-state :deep(.result-summary span), .result-state :deep(.result-summary strong) { display: block; white-space: nowrap; }
.result-state :deep(.result-summary span) { color: #64748b; font-size: 10px; }
.result-state :deep(.result-summary strong) { margin-top: 3px; font-size: 13px; font-variant-numeric: tabular-nums; }
.result-state :deep(.result-summary .run-id) { background: #ecfdf5; }
.result-state :deep(.result-summary .run-id strong) { color: #047857; }
@media (max-width: 1380px) { .scenario { grid-template-columns: 230px minmax(400px, 1fr); }.result-state { grid-column: 1 / -1; } }
@media (max-width: 1100px) { .status-strip--five { grid-template-columns: repeat(3, 1fr); } }
@media (max-width: 900px) { .status-strip, .status-strip--five { grid-template-columns: repeat(2, 1fr); }.status-strip > div:nth-child(2) { border-right: 0; }.phase-details, .scenario { grid-template-columns: 1fr; }.gate-list { border-right: 0; border-bottom: 1px solid #e9eef5; }.scenario__form, .scenario__form--wide { grid-template-columns: repeat(2, minmax(0, 1fr)); }.result-state { grid-column: auto; } }
@media (max-width: 560px) { .phase-panel__head { align-items: flex-start; flex-direction: column; }.status-strip, .scenario__form, .scenario__form--wide { grid-template-columns: 1fr; }.status-strip > div { border-right: 0; border-bottom: 1px solid #e9eef5; }.scope-notice { align-items: flex-start; } }
</style>
