<template>
  <div class="runtime-page">
    <div class="admin-page-header">
      <div>
        <div class="admin-page-kicker">自媒体自动化</div>
        <h1>运行环境</h1>
        <div class="admin-page-subtitle">按品牌、账号和 AdsPower 环境查看扩展、本地助手和准入状态。</div>
      </div>
      <el-button type="primary" :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>

    <section class="filter-panel">
      <el-form :model="filters" inline label-width="68px" class="filter-form">
        <el-form-item label="品牌ID">
          <el-input-number
            v-model="filters.brandId"
            :min="1"
            :controls="false"
            clearable
            placeholder="全部"
            class="brand-input"
          />
        </el-form-item>
        <el-form-item label="平台">
          <el-select v-model="filters.platform" clearable placeholder="全部平台" class="filter-select">
            <el-option v-for="item in platformOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.ready" clearable placeholder="全部状态" class="filter-select">
            <el-option label="可接任务" value="true" />
            <el-option label="不可接任务" value="false" />
          </el-select>
        </el-form-item>
        <el-form-item label="阻断码">
          <el-select
            v-model="filters.blockedReason"
            clearable
            filterable
            allow-create
            default-first-option
            placeholder="全部"
            class="reason-select"
          >
            <el-option v-for="item in blockedReasonOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input
            v-model="filters.keyword"
            clearable
            placeholder="品牌、账号、环境"
            class="keyword-input"
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item class="filter-actions">
          <el-button type="primary" :icon="Search" @click="search">查询</el-button>
          <el-button :icon="RefreshLeft" @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="metric-grid">
      <div class="metric-panel" :class="pageStats.blocked ? 'is-warning' : 'is-success'">
        <span>当前页观测可接</span>
        <strong>{{ pageStats.ready }} / {{ records.length }}</strong>
        <small>不可接 {{ pageStats.blocked }}</small>
      </div>
      <div class="metric-panel" :class="pageStats.extensionSeen === records.length && records.length ? 'is-success' : ''">
        <span>扩展可见</span>
        <strong>{{ pageStats.extensionSeen }}</strong>
        <small>最近上报 {{ latestExtensionSeen }}</small>
      </div>
      <div class="metric-panel" :class="pageStats.helperSeen === records.length && records.length ? 'is-success' : ''">
        <span>助手可见</span>
        <strong>{{ pageStats.helperSeen }}</strong>
        <small>最近上报 {{ latestHelperSeen }}</small>
      </div>
      <div class="metric-panel" :class="pageStats.accountIssues ? 'is-danger' : 'is-success'">
        <span>账号准入异常</span>
        <strong>{{ pageStats.accountIssues }}</strong>
        <small>登录状态以 verified 为准</small>
      </div>
    </section>

    <section class="panel">
      <div class="panel-head">
        <strong>环境列表</strong>
        <span>共 {{ page.total }} 条</span>
      </div>

      <el-table :data="records" v-loading="loading" border table-layout="fixed">
        <el-table-column label="品牌 / 平台" min-width="170">
          <template #default="{ row }">
            <div class="cell-stack">
              <strong>{{ row.brandName || `品牌 ${row.brandId}` }}</strong>
              <span>{{ platformLabel(row.platform) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="自媒体账号" min-width="190">
          <template #default="{ row }">
            <div class="cell-stack">
              <strong>{{ row.accountName || '-' }}</strong>
              <span class="mono">{{ row.platformAccountId || row.expectedPlatformAccountId || '-' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="AdsPower 环境" min-width="230">
          <template #default="{ row }">
            <div class="cell-stack">
              <strong>{{ row.environmentName || row.environmentKey || '-' }}</strong>
              <span class="mono">{{ row.providerProfileId || '-' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="扩展" min-width="210">
          <template #default="{ row }">
            <div class="cell-stack">
              <div class="inline-tags">
                <el-tag size="small" :type="row.extension?.lastSeenAt ? 'success' : 'info'">
                  {{ row.extension?.extensionVersion || '未上报' }}
                </el-tag>
                <el-tag v-if="row.extension?.runtimeStage" size="small" type="info">
                  {{ stageLabel(row.extension.runtimeStage) }}
                </el-tag>
              </div>
              <span>{{ formatTime(row.extension?.lastSeenAt) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="本地助手" min-width="220">
          <template #default="{ row }">
            <div class="cell-stack">
              <div class="inline-tags">
                <el-tag size="small" :type="row.helper?.lastSeenAt ? 'success' : 'info'">
                  {{ row.helper?.helperVersion || '未上报' }}
                </el-tag>
                <el-tag size="small" :type="adspowerApiTone(row.helper?.adspowerApiOk)">
                  AdsPower {{ adspowerApiLabel(row.helper?.adspowerApiOk) }}
                </el-tag>
              </div>
              <span>{{ helperCapacityText(row) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="登录" width="120">
          <template #default="{ row }">
            <el-tag :type="loginStatusTone(row.loginStatus)" size="small">{{ loginStatusLabel(row.loginStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="观测准入" min-width="190">
          <template #default="{ row }">
            <div class="cell-stack">
              <div class="inline-tags">
                <el-tag :type="row.readiness?.ready ? 'success' : 'danger'" size="small">
                  {{ row.readiness?.ready ? '可接任务' : '不可接任务' }}
                </el-tag>
                <el-tag size="small" type="info">{{ gateModeLabel(row.readiness?.gateMode) }}</el-tag>
              </div>
              <span>{{ blockedReasonText(row.readiness?.blockedReasons) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="最近错误" min-width="210">
          <template #default="{ row }">
            <div class="cell-stack">
              <strong>{{ row.extension?.lastErrorCode || row.helper?.lastErrorCode || '-' }}</strong>
              <span>{{ row.extension?.lastErrorMessage || row.helper?.lastErrorMessage || '-' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="86" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-row">
        <el-pagination
          v-model:current-page="page.current"
          v-model:page-size="page.size"
          :total="page.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="load"
        />
      </div>
    </section>

    <el-drawer v-model="detailVisible" title="运行态详情" size="620px">
      <template v-if="activeRow">
        <section class="detail-section">
          <h3>账号环境</h3>
          <div class="detail-grid">
            <span>品牌</span><strong>{{ activeRow.brandName || `品牌 ${activeRow.brandId}` }}</strong>
            <span>平台</span><strong>{{ platformLabel(activeRow.platform) }}</strong>
            <span>账号</span><strong>{{ activeRow.accountName || '-' }}</strong>
            <span>平台账号ID</span><strong class="mono">{{ activeRow.platformAccountId || '-' }}</strong>
            <span>期望账号</span><strong>{{ activeRow.expectedAccountName || '-' }}</strong>
            <span>期望平台ID</span><strong class="mono">{{ activeRow.expectedPlatformAccountId || '-' }}</strong>
            <span>环境</span><strong>{{ activeRow.environmentName || '-' }}</strong>
            <span>环境键</span><strong class="mono">{{ activeRow.environmentKey || '-' }}</strong>
            <span>Provider Profile</span><strong class="mono">{{ activeRow.providerProfileId || '-' }}</strong>
          </div>
        </section>

        <section class="detail-section">
          <h3>扩展</h3>
          <div class="detail-grid">
            <span>Install ID</span><strong class="mono">{{ activeRow.extension?.installId || '-' }}</strong>
            <span>版本</span><strong>{{ activeRow.extension?.extensionVersion || '-' }}</strong>
            <span>协议</span><strong>{{ activeRow.extension?.protocolVersion || '-' }}</strong>
            <span>最近上报</span><strong>{{ formatTime(activeRow.extension?.lastSeenAt) }}</strong>
            <span>阶段</span><strong>{{ stageLabel(activeRow.extension?.runtimeStage) }}</strong>
            <span>阶段信息</span><strong>{{ activeRow.extension?.runtimeStageMessage || '-' }}</strong>
            <span>错误码</span><strong>{{ activeRow.extension?.lastErrorCode || '-' }}</strong>
            <span>错误信息</span><strong>{{ activeRow.extension?.lastErrorMessage || '-' }}</strong>
          </div>
        </section>

        <section class="detail-section">
          <h3>本地助手</h3>
          <div class="detail-grid">
            <span>Session</span><strong class="mono">{{ activeRow.helper?.sessionId || '-' }}</strong>
            <span>Machine</span><strong class="mono">{{ activeRow.helper?.machineId || '-' }}</strong>
            <span>Profile</span><strong class="mono">{{ activeRow.helper?.activeProfile || '-' }}</strong>
            <span>版本</span><strong>{{ activeRow.helper?.helperVersion || '-' }}</strong>
            <span>协议</span><strong>{{ activeRow.helper?.protocolVersion || '-' }}</strong>
            <span>AdsPower</span><strong>{{ adspowerApiLabel(activeRow.helper?.adspowerApiOk) }}</strong>
            <span>容量</span><strong>{{ helperCapacityText(activeRow) }}</strong>
            <span>最近上报</span><strong>{{ formatTime(activeRow.helper?.lastSeenAt) }}</strong>
            <span>错误码</span><strong>{{ activeRow.helper?.lastErrorCode || '-' }}</strong>
            <span>错误信息</span><strong>{{ activeRow.helper?.lastErrorMessage || '-' }}</strong>
          </div>
        </section>

        <section class="detail-section">
          <h3>观测准入</h3>
          <div class="readiness-line">
            <el-tag :type="activeRow.readiness?.ready ? 'success' : 'danger'">
              {{ activeRow.readiness?.ready ? '可接任务' : '不可接任务' }}
            </el-tag>
            <el-tag type="info">{{ gateModeLabel(activeRow.readiness?.gateMode) }}</el-tag>
            <el-tag v-if="activeRow.readiness?.retryAfterSeconds" type="warning">
              {{ activeRow.readiness.retryAfterSeconds }} 秒后重试
            </el-tag>
          </div>
          <div class="scope-line">{{ readinessScopeLabel(activeRow.readiness?.scope) }}</div>
          <div class="reason-list">
            <el-tag
              v-for="reason in activeRow.readiness?.blockedReasons || []"
              :key="reason"
              type="warning"
              effect="plain"
            >
              {{ reason }}
            </el-tag>
            <span v-if="!activeRow.readiness?.blockedReasons?.length" class="muted">-</span>
          </div>
        </section>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Refresh, RefreshLeft, Search } from '@element-plus/icons-vue'
import { getSelfMediaRuntimeEnvironments } from '@/api/content'
import type { SelfMediaRuntimeEnvironment } from '@/types'

type ReadyFilter = '' | 'true' | 'false'
type TagTone = 'success' | 'warning' | 'danger' | 'info'

const platformOptions = [
  { label: '微信公众号', value: 'wechat_mp' },
  { label: '今日头条', value: 'toutiao' },
  { label: '百家号', value: 'baijiahao' },
  { label: '知乎', value: 'zhihu' },
  { label: '抖音图文', value: 'douyin' },
  { label: '小红书', value: 'xiaohongshu' },
]

const blockedReasonOptions = [
  'HELPER_OFFLINE',
  'HELPER_CAPACITY_FULL',
  'ADSPOWER_API_DOWN',
  'EXTENSION_NOT_SEEN',
  'EXTENSION_STALE',
  'ACCOUNT_NOT_VERIFIED',
  'ACCOUNT_MISMATCH',
  'BROWSER_ENVIRONMENT_DISABLED',
  'EXTENSION_VERSION_TOO_LOW',
  'HELPER_VERSION_TOO_LOW',
  'EXTENSION_CAPABILITY_UNSUPPORTED',
  'HELPER_CAPABILITY_UNSUPPORTED',
]

const loading = ref(false)
const records = ref<SelfMediaRuntimeEnvironment[]>([])
const detailVisible = ref(false)
const activeRow = ref<SelfMediaRuntimeEnvironment | null>(null)

const filters = reactive({
  brandId: undefined as number | undefined,
  platform: '',
  ready: '' as ReadyFilter,
  blockedReason: '',
  keyword: '',
})

const page = reactive({
  current: 1,
  size: 20,
  total: 0,
})

const pageStats = computed(() => {
  const ready = records.value.filter((item) => item.readiness?.ready).length
  const extensionSeen = records.value.filter((item) => Boolean(item.extension?.lastSeenAt)).length
  const helperSeen = records.value.filter((item) => Boolean(item.helper?.lastSeenAt)).length
  const accountIssues = records.value.filter((item) => item.loginStatus !== 'verified').length
  return {
    ready,
    blocked: records.value.length - ready,
    extensionSeen,
    helperSeen,
    accountIssues,
  }
})

const latestExtensionSeen = computed(() => latestSeen(records.value.map((item) => item.extension?.lastSeenAt)))
const latestHelperSeen = computed(() => latestSeen(records.value.map((item) => item.helper?.lastSeenAt)))

onMounted(load)

async function load() {
  loading.value = true
  try {
    const res = await getSelfMediaRuntimeEnvironments({
      brandId: filters.brandId || undefined,
      platform: filters.platform || undefined,
      ready: filters.ready ? filters.ready === 'true' : undefined,
      blockedReason: filters.blockedReason.trim() || undefined,
      keyword: filters.keyword.trim() || undefined,
      page: page.current,
      size: page.size,
    })
    const data = res.data.data
    records.value = data.records || []
    page.current = data.current || 1
    page.size = data.size || page.size
    page.total = data.total || 0
  } finally {
    loading.value = false
  }
}

function search() {
  page.current = 1
  load()
}

function resetFilters() {
  filters.brandId = undefined
  filters.platform = ''
  filters.ready = ''
  filters.blockedReason = ''
  filters.keyword = ''
  search()
}

function handleSizeChange(size: number) {
  page.size = size
  page.current = 1
  load()
}

function openDetail(row: SelfMediaRuntimeEnvironment) {
  activeRow.value = row
  detailVisible.value = true
}

function platformLabel(platform?: string | null) {
  const map: Record<string, string> = {
    wechat_mp: '微信公众号',
    douyin: '抖音图文',
    toutiao: '今日头条',
    baijiahao: '百家号',
    zhihu: '知乎',
    xiaohongshu: '小红书',
  }
  return platform ? map[platform] || platform : '-'
}

function loginStatusLabel(status?: string | null) {
  const map: Record<string, string> = {
    unknown: '未知',
    logged_in: '已登录',
    verified: '已核验',
    mismatch: '不匹配',
    expired: '已过期',
  }
  return status ? map[status] || status : '-'
}

function loginStatusTone(status?: string | null): TagTone {
  if (status === 'verified') return 'success'
  if (status === 'logged_in' || status === 'unknown') return 'warning'
  if (status === 'mismatch' || status === 'expired') return 'danger'
  return 'info'
}

function gateModeLabel(mode?: string | null) {
  const map: Record<string, string> = {
    observe_only: '观察',
    block_non_destructive: '阻断',
    manual_required_terminal: '终态',
  }
  return mode ? map[mode] || mode : '-'
}

function readinessScopeLabel(scope?: string | null) {
  const map: Record<string, string> = {
    brand_latest_helper: '按品牌最近助手上报观测，真实领取仍以当前签名助手为准',
  }
  return scope ? map[scope] || scope : '-'
}

function adspowerApiLabel(value?: boolean | null) {
  if (value === true) return '正常'
  if (value === false) return '异常'
  return '未上报'
}

function adspowerApiTone(value?: boolean | null): TagTone {
  if (value === true) return 'success'
  if (value === false) return 'danger'
  return 'info'
}

function stageLabel(stage?: string | null) {
  const map: Record<string, string> = {
    claimed: '已领取',
    browser_started: '浏览器已启动',
    extension_seen: '扩展已见',
    account_verified: '账号已核验',
    editor_opened: '编辑器已打开',
    content_filled: '内容已填充',
    publish_submitting: '提交中',
    publish_submitted: '已提交',
    publish_checking: '回查中',
    reported: '已上报',
  }
  return stage ? map[stage] || stage : '-'
}

function helperCapacityText(row: SelfMediaRuntimeEnvironment) {
  const running = row.helper?.runningTaskCount
  const capacity = row.helper?.capacity
  if (running == null && capacity == null) return '-'
  return `${running ?? 0} / ${capacity ?? 0}`
}

function blockedReasonText(reasons?: string[] | null) {
  if (!reasons?.length) return '-'
  if (reasons.length <= 2) return reasons.join('；')
  return `${reasons.slice(0, 2).join('；')} 等 ${reasons.length} 项`
}

function formatTime(value?: string | null) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 16)
}

function latestSeen(values: Array<string | null | undefined>) {
  const latest = values
    .filter((item): item is string => Boolean(item))
    .sort((a, b) => new Date(b).getTime() - new Date(a).getTime())[0]
  return formatTime(latest)
}
</script>

<style scoped>
.runtime-page {
  display: grid;
  gap: 18px;
}

.filter-panel,
.metric-panel,
.panel {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.filter-panel {
  padding: 16px 16px 4px;
}

.filter-form {
  align-items: flex-start;
}

.brand-input {
  width: 132px;
}

.filter-select {
  width: 150px;
}

.reason-select {
  width: 240px;
}

.keyword-input {
  width: 220px;
}

.filter-actions {
  margin-left: auto;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}

.metric-panel {
  display: grid;
  gap: 6px;
  min-height: 104px;
  padding: 16px;
}

.metric-panel span,
.metric-panel small,
.panel-head span,
.cell-stack span,
.muted {
  color: #64748b;
  font-size: 12px;
}

.metric-panel strong {
  color: #0f172a;
  font-size: 28px;
  line-height: 1.1;
}

.metric-panel.is-success {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.metric-panel.is-warning {
  border-color: #fde68a;
  background: #fffbeb;
}

.metric-panel.is-danger {
  border-color: #fecaca;
  background: #fef2f2;
}

.panel {
  min-width: 0;
  padding: 14px;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.panel-head strong {
  color: #111827;
  font-size: 15px;
}

.cell-stack {
  display: grid;
  gap: 5px;
  min-width: 0;
}

.cell-stack strong {
  color: #111827;
  font-size: 13px;
  line-height: 1.35;
  overflow-wrap: anywhere;
}

.cell-stack span {
  line-height: 1.35;
  overflow-wrap: anywhere;
}

.inline-tags,
.readiness-line,
.reason-list {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  padding-top: 14px;
}

.detail-section {
  display: grid;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid #e5e7eb;
}

.detail-section:first-child {
  padding-top: 0;
}

.detail-section:last-child {
  border-bottom: 0;
}

.detail-section h3 {
  margin: 0;
  color: #111827;
  font-size: 15px;
}

.detail-grid {
  display: grid;
  grid-template-columns: 130px minmax(0, 1fr);
  gap: 10px 12px;
}

.detail-grid span {
  color: #64748b;
  font-size: 12px;
}

.detail-grid strong {
  color: #0f172a;
  font-size: 13px;
  font-weight: 500;
  overflow-wrap: anywhere;
}

.reason-list {
  align-items: flex-start;
  min-height: 24px;
}

.scope-line {
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
}

@media (max-width: 980px) {
  .filter-actions {
    margin-left: 0;
  }

  .keyword-input,
  .reason-select,
  .filter-select {
    width: 100%;
  }
}
</style>
