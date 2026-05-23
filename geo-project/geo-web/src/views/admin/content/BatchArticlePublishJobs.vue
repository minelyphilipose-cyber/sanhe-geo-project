<template>
  <div class="batch-publish-jobs-page">
    <el-card shadow="never" class="mb-3">
      <div class="toolbar">
        <div class="toolbar-left">
          <el-select v-model="query.status" clearable placeholder="任务状态" style="width: 180px">
            <el-option label="待执行" value="pending" />
            <el-option label="执行中" value="running" />
            <el-option label="已完成" value="completed" />
            <el-option label="部分失败" value="partial_failed" />
            <el-option label="失败" value="failed" />
          </el-select>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </div>
        <div class="toolbar-right">
          <el-switch v-model="autoRefresh" active-text="自动刷新" />
          <el-button @click="load">刷新</el-button>
          <el-button @click="goBack">返回内容列表</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never">
      <DataState :loading="loading" :empty="!loading && rows.length === 0" empty-text="暂无批量发布任务">
        <el-table :data="rows" border>
          <el-table-column prop="jobId" label="任务ID" width="100">
            <template #default="{ row }">#{{ row.jobId }}</template>
          </el-table-column>
          <el-table-column label="发布方式" width="120">
            <template #default="{ row }">{{ publishModeLabel(row.publishMode) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)">{{ jobStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="进度" min-width="180">
            <template #default="{ row }">
              {{ row.successCount || 0 }} / {{ row.totalCount || 0 }} 成功
              <span v-if="row.failedCount">，{{ row.failedCount }} 失败</span>
            </template>
          </el-table-column>
          <el-table-column label="计划开始" width="170">
            <template #default="{ row }">{{ row.scheduledAt ? formatDateTimeSeconds(row.scheduledAt) : '-' }}</template>
          </el-table-column>
          <el-table-column label="发布间隔" width="110">
            <template #default="{ row }">{{ row.intervalMinutes }} 分钟</template>
          </el-table-column>
          <el-table-column label="创建时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="完成时间" width="170">
            <template #default="{ row }">{{ row.finishedAt || '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(row.jobId)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pager">
          <el-pagination
            background
            layout="prev, pager, next, total"
            :current-page="page.current"
            :page-size="page.size"
            :total="page.total"
            @current-change="onPageChange"
          />
        </div>
      </DataState>
    </el-card>

    <el-drawer v-model="detailVisible" title="批量发布详情" size="76%" class="batch-detail-drawer">
      <DataState :loading="detailLoading" :empty="!detailLoading && !detail">
        <div v-if="detail" class="batch-detail">
          <section class="batch-detail-hero">
            <div class="batch-detail-avatar" :class="statusClass(detail.status)">
              {{ jobStatusShortLabel(detail.status) }}
            </div>
            <div class="batch-detail-main">
              <div class="batch-detail-kicker">批量发布任务 #{{ detail.jobId }}</div>
              <h2>{{ publishModeLabel(detail.publishMode) }}</h2>
              <div class="batch-detail-meta">
                <span class="admin-status-tag" :class="statusClass(detail.status)">
                  {{ jobStatusLabel(detail.status) }}
                </span>
                <span class="admin-mini-pill is-blue">间隔 {{ detail.intervalMinutes }} 分钟</span>
                <span class="admin-mini-pill is-green">{{ detailPlatformText }}</span>
                <span class="admin-mini-pill">{{ detail.scheduledAt ? formatDateTimeSeconds(detail.scheduledAt) : '未设置计划开始时间' }}</span>
              </div>
            </div>
          </section>

          <section class="batch-progress-panel">
            <div class="batch-progress-main">
              <div class="batch-progress-ring" :style="{ '--progress': `${detailProgress}%` }">
                <strong>{{ detailProgress }}%</strong>
                <span>完成率</span>
              </div>
              <div>
                <div class="batch-section-kicker">执行概览</div>
                <h3>共 {{ detail.totalCount || 0 }} 篇文章</h3>
                <p>
                  发布平台：{{ detailPlatformText }}。成功 {{ detail.successCount || 0 }} 篇，失败 {{ detail.failedCount || 0 }} 篇，剩余 {{ detailPendingCount }} 篇等待执行。
                </p>
              </div>
            </div>

            <div class="batch-metric-grid">
              <div class="batch-metric-card is-total">
                <span>文章总数</span>
                <strong>{{ detail.totalCount || 0 }}</strong>
              </div>
              <div class="batch-metric-card is-success">
                <span>成功</span>
                <strong>{{ detail.successCount || 0 }}</strong>
              </div>
              <div class="batch-metric-card is-danger">
                <span>失败</span>
                <strong>{{ detail.failedCount || 0 }}</strong>
              </div>
              <div class="batch-metric-card is-pending">
                <span>待执行</span>
                <strong>{{ detailPendingCount }}</strong>
              </div>
            </div>
          </section>

          <section class="batch-detail-table admin-table-card">
            <div class="batch-table-header">
              <div>
                <div class="batch-table-title">文章发布明细</div>
                <div class="batch-table-subtitle">按计划时间展示每篇文章的平台任务与执行状态。</div>
              </div>
              <span class="admin-mini-pill is-blue">{{ detail.items?.length || 0 }} 条记录</span>
            </div>
            <el-table :data="detail.items" border table-layout="fixed">
              <el-table-column label="文章标题" min-width="260" show-overflow-tooltip>
                <template #default="{ row }">
                  <div class="admin-cell-stack">
                    <span class="admin-cell-main">{{ row.articleTitle || '-' }}</span>
                    <span class="admin-cell-sub">{{ row.errorMessage || '暂无异常信息' }}</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="项目" min-width="170" show-overflow-tooltip>
                <template #default="{ row }">{{ row.projectName || '-' }}</template>
              </el-table-column>
              <el-table-column label="平台" width="120">
                <template #default="{ row }">
                  <span class="admin-mini-pill">{{ platformLabel(row.platformKey) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="计划时间" width="180">
                <template #default="{ row }">{{ row.plannedAt ? formatDateTimeSeconds(row.plannedAt) : '-' }}</template>
              </el-table-column>
              <el-table-column label="发布时间" width="180">
                <template #default="{ row }">{{ row.publishedAt ? formatDateTimeSeconds(row.publishedAt) : '-' }}</template>
              </el-table-column>
              <el-table-column label="状态" width="116">
                <template #default="{ row }">
                  <span class="admin-status-tag" :class="itemStatusClass(row.status)">
                    {{ itemStatusLabel(row.status) }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="分发任务" width="116">
                <template #default="{ row }">{{ row.distributionTaskId ? `#${row.distributionTaskId}` : '-' }}</template>
              </el-table-column>
              <el-table-column label="失败原因" min-width="220" show-overflow-tooltip>
                <template #default="{ row }">{{ row.errorMessage || '-' }}</template>
              </el-table-column>
            </el-table>
          </section>
        </div>
      </DataState>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DataState from '@/components/ui/DataState.vue'
import { formatDateTime, formatDateTimeSeconds } from '@/utils/format'
import {
  getBatchArticlePublish,
  getBatchArticlePublishJobs,
  type BatchArticlePublishJobSummary,
  type BatchArticlePublishResponse,
} from '@/api/content'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const detailLoading = ref(false)
const autoRefresh = ref(true)
const rows = ref<BatchArticlePublishJobSummary[]>([])
const detail = ref<BatchArticlePublishResponse | null>(null)
const detailVisible = ref(false)
const page = reactive({ current: 1, size: 10, total: 0 })
const query = reactive({ status: '' })
let refreshTimer: number | null = null

const detailPendingCount = computed(() => {
  const current = detail.value
  if (!current) return 0
  const total = current.totalCount || 0
  const success = current.successCount || 0
  const failed = current.failedCount || 0
  return Math.max(total - success - failed, 0)
})

const detailProgress = computed(() => {
  const current = detail.value
  if (!current?.totalCount) return 0
  const done = (current.successCount || 0) + (current.failedCount || 0)
  return Math.min(100, Math.round((done / current.totalCount) * 100))
})

const detailPlatformText = computed(() => {
  const platforms = Array.from(new Set(
    (detail.value?.items || [])
      .map((item) => platformLabel(item.platformKey))
      .filter((item) => item && item !== '-'),
  ))
  return platforms.length ? platforms.join('、') : '暂无平台'
})

onMounted(async () => {
  await load()
  const initialJobId = Number(route.query.jobId)
  if (Number.isFinite(initialJobId) && initialJobId > 0) {
    await openDetail(initialJobId)
  }
  startTimer()
})
onBeforeUnmount(stopTimer)

watch(autoRefresh, (enabled) => {
  if (enabled) startTimer()
  else stopTimer()
})

async function load() {
  loading.value = true
  try {
    const { data } = await getBatchArticlePublishJobs({
      current: page.current,
      size: page.size,
      status: query.status || undefined,
    })
    rows.value = data.data.records || []
    page.total = data.data.total || 0
    if (detailVisible.value && detail.value?.jobId) {
      await loadDetail(detail.value.jobId, false)
    }
  } finally {
    loading.value = false
  }
}

function search() {
  page.current = 1
  load()
}

function resetQuery() {
  query.status = ''
  search()
}

function onPageChange(v: number) {
  page.current = v
  load()
}

async function openDetail(jobId: number) {
  detailVisible.value = true
  await loadDetail(jobId, true)
}

async function loadDetail(jobId: number, showLoading: boolean) {
  if (showLoading) detailLoading.value = true
  try {
    const { data } = await getBatchArticlePublish(jobId)
    detail.value = data.data
  } finally {
    if (showLoading) detailLoading.value = false
  }
}

function startTimer() {
  stopTimer()
  refreshTimer = window.setInterval(() => {
    if (autoRefresh.value) load()
  }, 15000)
}

function stopTimer() {
  if (refreshTimer) {
    window.clearInterval(refreshTimer)
    refreshTimer = null
  }
}

function publishModeLabel(v: string) {
  return v === 'scheduled' ? '定时发布' : '立刻发布'
}

function jobStatusLabel(v: string) {
  const map: Record<string, string> = {
    pending: '待执行',
    running: '执行中',
    completed: '已完成',
    partial_failed: '部分失败',
    failed: '失败',
  }
  return map[v] || v
}

function itemStatusLabel(v: string) {
  const map: Record<string, string> = {
    pending: '待执行',
    running: '执行中',
    success: '成功',
    failed: '失败',
  }
  return map[v] || v
}

function statusTagType(v: string): 'success' | 'warning' | 'danger' | 'info' {
  if (v === 'completed') return 'success'
  if (v === 'failed' || v === 'partial_failed') return 'danger'
  if (v === 'running') return 'warning'
  return 'info'
}

function statusClass(v: string) {
  if (v === 'completed') return 'is-success'
  if (v === 'failed' || v === 'partial_failed') return 'is-danger'
  if (v === 'running') return 'is-warning'
  return 'is-muted'
}

function itemStatusClass(v: string) {
  if (v === 'success') return 'is-success'
  if (v === 'failed') return 'is-danger'
  if (v === 'running') return 'is-warning'
  return 'is-muted'
}

function jobStatusShortLabel(v: string) {
  if (v === 'completed') return '成'
  if (v === 'failed') return '败'
  if (v === 'partial_failed') return '部'
  if (v === 'running') return '执'
  return '待'
}

function platformLabel(v: string) {
  if (v === 'agent_site') return 'Agent 官网'
  if (v === 'industry_site') return '行业资讯站'
  if (v === 'forum_site') return '平台网站'
  return v || '-'
}

function goBack() {
  router.push('/admin/content/execution')
}
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.mb-3 {
  margin-bottom: 16px;
}

.batch-detail-drawer :deep(.el-drawer__header) {
  align-items: center;
  min-height: 64px;
  margin-bottom: 0;
  padding: 18px 22px;
  border-bottom: 1px solid #e2e8f0;
  background: linear-gradient(135deg, #f8fbff, #eff6ff 58%, #ecfdf5);
}

.batch-detail-drawer :deep(.el-drawer__title) {
  color: var(--admin-text-strong);
  font-size: 18px;
  font-weight: 800;
}

.batch-detail-drawer :deep(.el-drawer__body) {
  padding: 18px;
  background: #f8fafc;
}

.batch-detail {
  display: grid;
  gap: 14px;
}

.batch-detail-hero,
.batch-progress-panel,
.batch-detail-table {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background: #ffffff;
  box-shadow: 0 14px 32px rgba(15, 23, 42, 0.065);
}

.batch-detail-hero {
  display: flex;
  align-items: center;
  gap: 14px;
  overflow: hidden;
  padding: 16px;
  background:
    linear-gradient(135deg, #ffffff 0%, #f8fbff 58%, #ecfdf5 100%);
}

.batch-detail-avatar {
  width: 56px;
  height: 56px;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border-radius: 14px;
  background: linear-gradient(135deg, #64748b, #94a3b8);
  color: #ffffff;
  font-size: 20px;
  font-weight: 800;
  box-shadow: 0 12px 22px rgba(15, 23, 42, 0.16);
}

.batch-detail-avatar.is-success {
  background: linear-gradient(135deg, #059669, #14b8a6);
}

.batch-detail-avatar.is-warning {
  background: linear-gradient(135deg, #d97706, #f59e0b);
}

.batch-detail-avatar.is-danger {
  background: linear-gradient(135deg, #dc2626, #ef4444);
}

.batch-detail-main {
  min-width: 0;
}

.batch-detail-kicker,
.batch-section-kicker {
  color: #2563eb;
  font-size: 12px;
  font-weight: 800;
}

.batch-detail-main h2,
.batch-progress-main h3 {
  margin: 4px 0 9px;
  color: #0f172a;
  line-height: 1.35;
  font-weight: 800;
}

.batch-detail-main h2 {
  font-size: 20px;
}

.batch-progress-main h3 {
  font-size: 18px;
}

.batch-detail-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.batch-progress-panel {
  display: grid;
  grid-template-columns: minmax(280px, 0.95fr) minmax(0, 1.2fr);
  gap: 16px;
  padding: 16px;
}

.batch-progress-main {
  display: grid;
  grid-template-columns: 126px minmax(0, 1fr);
  gap: 16px;
  align-items: center;
  min-width: 0;
}

.batch-progress-main p {
  margin: 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.65;
}

.batch-progress-ring {
  width: 116px;
  aspect-ratio: 1;
  display: grid;
  place-items: center;
  place-content: center;
  border-radius: 999px;
  background:
    radial-gradient(circle at center, #ffffff 0 58%, transparent 59%),
    conic-gradient(#2563eb var(--progress), #e2e8f0 0);
  box-shadow:
    inset 0 0 0 1px rgba(226, 232, 240, 0.86),
    0 14px 28px rgba(37, 99, 235, 0.1);
}

.batch-progress-ring strong {
  color: #0f172a;
  font-size: 24px;
  line-height: 1;
  font-weight: 800;
}

.batch-progress-ring span {
  margin-top: 6px;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.batch-metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  min-width: 0;
}

.batch-metric-card {
  min-width: 0;
  min-height: 92px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
  border: 1px solid #e7edf5;
  border-radius: 12px;
  background: linear-gradient(135deg, #ffffff 0%, #fbfdff 64%, #f8fbff 100%);
  padding: 13px 14px;
  box-shadow: inset 3px 0 0 #dbeafe;
}

.batch-metric-card span {
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.batch-metric-card strong {
  color: #0f172a;
  font-size: 26px;
  line-height: 1;
  font-weight: 800;
}

.batch-metric-card.is-success {
  box-shadow: inset 3px 0 0 #10b981;
}

.batch-metric-card.is-danger {
  box-shadow: inset 3px 0 0 #ef4444;
}

.batch-metric-card.is-pending {
  box-shadow: inset 3px 0 0 #f59e0b;
}

.batch-table-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px;
  border-bottom: 1px solid var(--admin-panel-border-soft);
  background: linear-gradient(90deg, #f8fbff 0%, #ffffff 55%, #f0fdf4 100%);
}

.batch-table-title {
  color: var(--admin-text-strong);
  font-size: 16px;
  font-weight: 800;
}

.batch-table-subtitle {
  margin-top: 4px;
  color: var(--admin-text-muted);
  font-size: 12px;
}

.batch-detail-table :deep(.el-table) {
  border-radius: 0 0 14px 14px;
}

@media (max-width: 768px) {
  .toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .toolbar-left,
  .toolbar-right {
    flex-wrap: wrap;
  }

  .batch-detail-drawer {
    width: 100% !important;
  }

  .batch-detail-hero,
  .batch-table-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .batch-progress-panel,
  .batch-progress-main,
  .batch-metric-grid {
    grid-template-columns: 1fr;
  }

  .batch-progress-ring {
    margin-inline: auto;
  }
}
</style>
