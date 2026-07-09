<template>
  <div class="batch-jobs-page">
    <section class="jobs-hero">
      <div>
        <div class="jobs-kicker">内容执行</div>
        <h1>批量任务中心</h1>
        <p>集中查看文章批量生成与批量发布的执行进度、失败原因和处理结果。</p>
      </div>
      <div class="jobs-stat-grid">
        <div class="jobs-stat-card">
          <span>任务总数</span>
          <strong>{{ activeStats.total }}</strong>
        </div>
        <div class="jobs-stat-card is-warning">
          <span>执行中</span>
          <strong>{{ activeStats.running }}</strong>
        </div>
        <div class="jobs-stat-card is-success">
          <span>已完成</span>
          <strong>{{ activeStats.completed }}</strong>
        </div>
        <div class="jobs-stat-card is-danger">
          <span>异常任务</span>
          <strong>{{ activeStats.failed }}</strong>
        </div>
      </div>
    </section>

    <el-card shadow="never" class="jobs-card">
      <el-tabs v-model="activeTab" class="jobs-tabs" @tab-change="handleTabChange">
        <el-tab-pane label="批量生成任务" name="generation">
          <div class="toolbar">
            <div class="toolbar-left">
              <el-input
                v-model="generationQuery.projectName"
                clearable
                placeholder="搜索项目名称"
                style="width: 220px"
                @keyup.enter="searchGeneration"
              />
              <el-select v-model="generationQuery.status" clearable placeholder="任务状态" style="width: 160px">
                <el-option label="待生成" value="pending" />
                <el-option label="生成中" value="running" />
                <el-option label="成功" value="success" />
                <el-option label="部分成功" value="partial_success" />
                <el-option label="失败" value="failed" />
              </el-select>
              <el-button type="primary" @click="searchGeneration">查询</el-button>
              <el-button @click="resetGeneration">重置</el-button>
            </div>
            <div class="toolbar-right">
              <el-switch v-model="autoRefresh" active-text="自动刷新" />
              <el-button @click="loadGeneration">刷新</el-button>
              <el-button @click="goBack">返回内容列表</el-button>
            </div>
          </div>

          <DataState :loading="generationLoading" :empty="!generationLoading && generationRows.length === 0" empty-text="暂无批量生成任务">
            <el-table :data="generationRows" class="jobs-table" table-layout="fixed">
              <el-table-column label="批次主题" min-width="280" show-overflow-tooltip>
                <template #default="{ row }">
                  <div class="job-name-cell">
                    <span class="job-name-main">{{ row.topic || `批次 #${row.batchId}` }}</span>
                    <span class="job-name-sub">
                      <span class="job-source-dot">{{ topicSourceLabel(row.topicSource) }}</span>
                      {{ row.projectName || `项目 #${row.projectId}` }} · {{ row.totalCount || 0 }} 篇文章
                    </span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="112">
                <template #default="{ row }">
                  <span class="admin-status-tag" :class="generationStatusClass(row.status)">
                    {{ generationStatusLabel(row.status) }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="进度" min-width="210">
                <template #default="{ row }">
                  <div class="job-progress-summary">
                    <span class="job-progress-count">{{ generationDoneCount(row) }}/{{ row.totalCount || 0 }}</span>
                    <span class="job-progress-label">已处理</span>
                    <span v-if="row.failedCount" class="job-progress-failed">{{ row.failedCount }} 失败</span>
                    <span v-else-if="generationPendingCount(row)" class="job-progress-pending">{{ generationPendingCount(row) }} 待生成</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="质量提醒" width="96">
                <template #default="{ row }">{{ row.warningCount || 0 }}</template>
              </el-table-column>
              <el-table-column label="创建时间" width="170">
                <template #default="{ row }">{{ formatNullableDateTime(row.createdAt) }}</template>
              </el-table-column>
              <el-table-column label="完成时间" width="170">
                <template #default="{ row }">{{ formatNullableDateTime(row.finishedAt) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="88" align="center">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openGenerationDetail(row.batchId)">详情</el-button>
                </template>
              </el-table-column>
            </el-table>

            <div class="pager">
              <el-pagination
                background
                layout="prev, pager, next, total"
                :current-page="generationPage.current"
                :page-size="generationPage.size"
                :total="generationPage.total"
                @current-change="onGenerationPageChange"
              />
            </div>
          </DataState>
        </el-tab-pane>

        <el-tab-pane label="批量发布任务" name="publish">
          <div class="toolbar">
            <div class="toolbar-left">
              <el-select v-model="publishQuery.status" clearable placeholder="任务状态" style="width: 160px">
                <el-option label="待执行" value="pending" />
                <el-option label="执行中" value="running" />
                <el-option label="已完成" value="completed" />
                <el-option label="部分失败" value="partial_failed" />
                <el-option label="失败" value="failed" />
              </el-select>
              <el-select v-model="publishQuery.jobSource" placeholder="任务来源" style="width: 156px">
                <el-option label="手动批量" value="manual" />
                <el-option label="自动分发" value="auto" />
                <el-option label="全部来源" value="all" />
              </el-select>
              <el-button type="primary" @click="searchPublish">查询</el-button>
              <el-button @click="resetPublish">重置</el-button>
            </div>
            <div class="toolbar-right">
              <el-switch v-model="autoRefresh" active-text="自动刷新" />
              <el-button @click="loadPublish">刷新</el-button>
              <el-button @click="goBack">返回内容列表</el-button>
            </div>
          </div>

          <DataState :loading="publishLoading" :empty="!publishLoading && publishRows.length === 0" empty-text="暂无批量发布任务">
            <el-table :data="publishRows" class="jobs-table" table-layout="fixed">
              <el-table-column label="任务名称" min-width="260" show-overflow-tooltip>
                <template #default="{ row }">
                  <div class="job-name-cell">
                    <span class="job-name-main">{{ row.jobName || fallbackJobName(row) }}</span>
                    <span class="job-name-sub">
                      <span class="job-source-dot" :class="{ 'is-auto': row.jobSource === 'auto' }">{{ jobSourceLabel(row.jobSource) }}</span>
                      {{ publishModeLabel(row.publishMode) }} · {{ row.totalCount || 0 }} 篇文章
                    </span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="发布方式" width="120">
                <template #default="{ row }">
                  <span class="publish-mode-pill" :class="{ 'is-scheduled': row.publishMode === 'scheduled' }">
                    {{ publishModeLabel(row.publishMode) }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="108">
                <template #default="{ row }">
                  <span class="admin-status-tag" :class="publishStatusClass(row.status)">
                    {{ jobStatusLabel(row.status) }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="进度" min-width="190">
                <template #default="{ row }">
                  <div class="job-progress-summary">
                    <span class="job-progress-count">{{ row.successCount || 0 }}/{{ row.totalCount || 0 }}</span>
                    <span class="job-progress-label">成功</span>
                    <span v-if="row.failedCount" class="job-progress-failed">{{ row.failedCount }} 失败</span>
                    <span v-else-if="pendingPublishCount(row)" class="job-progress-pending">{{ pendingPublishCount(row) }} 待执行</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="计划开始" width="170">
                <template #default="{ row }">{{ formatJobScheduledAt(row) }}</template>
              </el-table-column>
              <el-table-column label="发布间隔" width="104">
                <template #default="{ row }">
                  <span class="interval-pill">{{ row.intervalMinutes }} 分钟</span>
                </template>
              </el-table-column>
              <el-table-column label="创建时间" width="170">
                <template #default="{ row }">{{ formatNullableDateTime(row.createdAt) }}</template>
              </el-table-column>
              <el-table-column label="完成时间" width="170">
                <template #default="{ row }">{{ formatNullableDateTime(row.finishedAt) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="88" align="center">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openPublishDetail(row.jobId)">详情</el-button>
                </template>
              </el-table-column>
            </el-table>

            <div class="pager">
              <el-pagination
                background
                layout="prev, pager, next, total"
                :current-page="publishPage.current"
                :page-size="publishPage.size"
                :total="publishPage.total"
                @current-change="onPublishPageChange"
              />
            </div>
          </DataState>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-drawer v-model="generationDetailVisible" title="批量生成详情" size="76%" class="batch-detail-drawer">
      <DataState :loading="generationDetailLoading" :empty="!generationDetailLoading && !generationDetail">
        <div v-if="generationDetail" class="batch-detail">
          <section class="batch-detail-hero">
            <div class="batch-detail-avatar" :class="generationStatusClass(generationDetail.status)">
              {{ generationStatusShortLabel(generationDetail.status) }}
            </div>
            <div class="batch-detail-main">
              <div class="batch-detail-kicker">批量生成任务</div>
              <h2>{{ generationDetail.topic || `批次 #${generationDetail.batchId}` }}</h2>
              <div class="batch-detail-meta">
                <span class="admin-status-tag" :class="generationStatusClass(generationDetail.status)">
                  {{ generationStatusLabel(generationDetail.status) }}
                </span>
                <span class="admin-mini-pill is-blue">{{ generationDetailProjectText }}</span>
                <span class="admin-mini-pill">{{ formatNullableDateTime(generationDetail.createdAt) }}</span>
              </div>
            </div>
            <el-button
              v-if="canRetryGeneration"
              type="danger"
              :loading="generationRetrying"
              @click="retryGenerationFailedTasks"
            >
              重试失败任务
            </el-button>
          </section>

          <section class="batch-progress-panel">
            <div class="batch-progress-main">
              <div class="batch-progress-ring" :style="{ '--progress': `${generationDetailProgress}%` }">
                <strong>{{ generationDetailProgress }}%</strong>
                <span>完成率</span>
              </div>
              <div>
                <div class="batch-section-kicker">生成概览</div>
                <h3>共 {{ generationDetail.totalCount || 0 }} 篇文章</h3>
                <p>
                  成功 {{ generationDetail.successCount || 0 }} 篇，失败 {{ generationDetail.failedCount || 0 }} 篇，剩余 {{ generationDetailPendingCount }} 篇等待生成。
                </p>
              </div>
            </div>

            <div class="batch-metric-grid">
              <div class="batch-metric-card is-total">
                <span>文章总数</span>
                <strong>{{ generationDetail.totalCount || 0 }}</strong>
              </div>
              <div class="batch-metric-card is-success">
                <span>成功</span>
                <strong>{{ generationDetail.successCount || 0 }}</strong>
              </div>
              <div class="batch-metric-card is-danger">
                <span>失败</span>
                <strong>{{ generationDetail.failedCount || 0 }}</strong>
              </div>
              <div class="batch-metric-card is-pending">
                <span>待生成</span>
                <strong>{{ generationDetailPendingCount }}</strong>
              </div>
            </div>
          </section>

          <section class="batch-detail-table admin-table-card">
            <div class="batch-table-header">
              <div>
                <div class="batch-table-title">文章生成明细</div>
                <div class="batch-table-subtitle">按批次序号展示每篇文章的生成状态、模型结果与失败原因。</div>
              </div>
              <span class="admin-mini-pill is-blue">{{ generationDetail.tasks?.length || 0 }} 条记录</span>
            </div>
            <el-table :data="generationDetail.tasks" border table-layout="fixed">
              <el-table-column label="序号" width="76">
                <template #default="{ row }">{{ row.articleIndexInBatch || '-' }}</template>
              </el-table-column>
              <el-table-column label="主题" min-width="240" show-overflow-tooltip>
                <template #default="{ row }">
                  <div class="admin-cell-stack">
                    <el-button
                      v-if="row.articleId"
                      link
                      type="primary"
                      class="article-title-link"
                      @click="openArticleDetail(row.articleId)"
                    >
                      {{ generationTaskTitle(row) }}
                    </el-button>
                    <span v-else class="admin-cell-main">{{ generationTaskTitle(row) }}</span>
                    <span class="admin-cell-sub">{{ channelText(row) }}</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="模板" min-width="160" show-overflow-tooltip>
                <template #default="{ row }">{{ promptTemplateText(row) }}</template>
              </el-table-column>
              <el-table-column label="状态" width="108">
                <template #default="{ row }">
                  <span class="admin-status-tag" :class="generationStatusClass(row.status)">
                    {{ generationTaskStatusLabel(row.status) }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="质量" width="96">
                <template #default="{ row }">{{ qualityStatusLabel(row.qualityStatus) }}</template>
              </el-table-column>
              <el-table-column label="重试" width="76">
                <template #default="{ row }">{{ row.retryCount ?? 0 }}</template>
              </el-table-column>
              <el-table-column label="失败原因" min-width="260" show-overflow-tooltip>
                <template #default="{ row }">{{ row.errorMessage || '-' }}</template>
              </el-table-column>
              <el-table-column label="完成时间" width="170">
                <template #default="{ row }">{{ formatNullableDateTime(row.finishedAt) }}</template>
              </el-table-column>
            </el-table>
          </section>
        </div>
      </DataState>
    </el-drawer>

    <el-drawer v-model="publishDetailVisible" title="批量发布详情" size="76%" class="batch-detail-drawer">
      <DataState :loading="publishDetailLoading" :empty="!publishDetailLoading && !publishDetail">
        <div v-if="publishDetail" class="batch-detail">
          <section class="batch-detail-hero">
            <div class="batch-detail-avatar" :class="publishStatusClass(publishDetail.status)">
              {{ jobStatusShortLabel(publishDetail.status) }}
            </div>
            <div class="batch-detail-main">
              <div class="batch-detail-kicker">批量发布任务</div>
              <h2>{{ publishDetail.jobName || publishModeLabel(publishDetail.publishMode) }}</h2>
              <div class="batch-detail-meta">
                <span class="admin-status-tag" :class="publishStatusClass(publishDetail.status)">
                  {{ jobStatusLabel(publishDetail.status) }}
                </span>
                <span class="admin-mini-pill is-blue">间隔 {{ publishDetail.intervalMinutes }} 分钟</span>
                <span class="admin-mini-pill is-green">{{ detailPlatformText }}</span>
                <span class="admin-mini-pill">{{ formatDetailScheduledAt(publishDetail) }}</span>
              </div>
            </div>
          </section>

          <section class="batch-progress-panel">
            <div class="batch-progress-main">
              <div class="batch-progress-ring" :style="{ '--progress': `${publishDetailProgress}%` }">
                <strong>{{ publishDetailProgress }}%</strong>
                <span>完成率</span>
              </div>
              <div>
                <div class="batch-section-kicker">执行概览</div>
                <h3>共 {{ publishDetail.totalCount || 0 }} 篇文章</h3>
                <p>
                  发布平台：{{ detailPlatformText }}。成功 {{ publishDetail.successCount || 0 }} 篇，失败 {{ publishDetail.failedCount || 0 }} 篇，剩余 {{ publishDetailPendingCount }} 篇等待执行。
                </p>
              </div>
            </div>

            <div class="batch-metric-grid">
              <div class="batch-metric-card is-total">
                <span>文章总数</span>
                <strong>{{ publishDetail.totalCount || 0 }}</strong>
              </div>
              <div class="batch-metric-card is-success">
                <span>成功</span>
                <strong>{{ publishDetail.successCount || 0 }}</strong>
              </div>
              <div class="batch-metric-card is-danger">
                <span>失败</span>
                <strong>{{ publishDetail.failedCount || 0 }}</strong>
              </div>
              <div class="batch-metric-card is-pending">
                <span>待执行</span>
                <strong>{{ publishDetailPendingCount }}</strong>
              </div>
            </div>
          </section>

          <section class="batch-detail-table admin-table-card">
            <div class="batch-table-header">
              <div>
                <div class="batch-table-title">文章发布明细</div>
                <div class="batch-table-subtitle">按计划时间展示每篇文章的平台任务与执行状态。</div>
              </div>
              <span class="admin-mini-pill is-blue">{{ publishDetail.items?.length || 0 }} 条记录</span>
            </div>
            <el-table :data="publishDetail.items" border table-layout="fixed">
              <el-table-column label="文章标题" min-width="260" show-overflow-tooltip>
                <template #default="{ row }">
                  <div class="admin-cell-stack">
                    <el-button
                      v-if="row.articleId"
                      link
                      type="primary"
                      class="article-title-link"
                      @click="openArticleDetail(row.articleId)"
                    >
                      {{ row.articleTitle || '查看文章详情' }}
                    </el-button>
                    <span v-else class="admin-cell-main">{{ row.articleTitle || '-' }}</span>
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
                  <span class="admin-status-tag" :class="publishItemStatusClass(row.status)">
                    {{ itemStatusLabel(row.status) }}
                  </span>
                </template>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DataState from '@/components/ui/DataState.vue'
import {
  getBatchArticleGeneration,
  getBatchArticleGenerationJobs,
  getBatchArticlePublish,
  getBatchArticlePublishJobs,
  retryFailedBatchArticleGeneration,
  type BatchArticleGenerationBatchSummary,
  type BatchArticleGenerationDetailResponse,
  type BatchArticleGenerationTask,
  type BatchArticlePublishJobSummary,
  type BatchArticlePublishResponse,
} from '@/api/content'
import { useDictStore } from '@/stores/dict'
import { useUserStore } from '@/stores/user'
import { formatDateTimeSeconds } from '@/utils/format'

type TabName = 'generation' | 'publish'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const dictStore = useDictStore()

const activeTab = ref<TabName>(route.query.tab === 'generation' ? 'generation' : 'publish')
const autoRefresh = ref(true)
let refreshTimer: number | null = null

const generationLoading = ref(false)
const generationDetailLoading = ref(false)
const generationRetrying = ref(false)
const generationRows = ref<BatchArticleGenerationBatchSummary[]>([])
const generationDetail = ref<BatchArticleGenerationDetailResponse | null>(null)
const generationDetailVisible = ref(false)
const generationPage = reactive({ current: 1, size: 10, total: 0 })
const generationQuery = reactive({ status: '', projectName: '' })

const publishLoading = ref(false)
const publishDetailLoading = ref(false)
const publishRows = ref<BatchArticlePublishJobSummary[]>([])
const publishDetail = ref<BatchArticlePublishResponse | null>(null)
const publishDetailVisible = ref(false)
const publishPage = reactive({ current: 1, size: 10, total: 0 })
const publishQuery = reactive<{ status: string; jobSource: 'manual' | 'auto' | 'all' }>({ status: '', jobSource: 'manual' })

const activeStats = computed(() => activeTab.value === 'generation' ? generationStats.value : publishStats.value)

const generationStats = computed(() => {
  const rows = generationRows.value
  return {
    total: generationPage.total || rows.length,
    running: rows.filter((row) => row.status === 'running' || row.status === 'pending').length,
    completed: rows.filter((row) => row.status === 'success').length,
    failed: rows.filter((row) => row.status === 'failed' || row.status === 'partial_success').length,
  }
})

const publishStats = computed(() => {
  const rows = publishRows.value
  return {
    total: publishPage.total || rows.length,
    running: rows.filter((row) => row.status === 'running' || row.status === 'pending').length,
    completed: rows.filter((row) => row.status === 'completed').length,
    failed: rows.filter((row) => row.status === 'failed' || row.status === 'partial_failed').length,
  }
})

const generationDetailPendingCount = computed(() => {
  const current = generationDetail.value
  if (!current) return 0
  return Math.max((current.totalCount || 0) - (current.successCount || 0) - (current.failedCount || 0), 0)
})

const generationDetailProgress = computed(() => {
  const current = generationDetail.value
  if (!current?.totalCount) return 0
  const done = (current.successCount || 0) + (current.failedCount || 0)
  return Math.min(100, Math.round((done / current.totalCount) * 100))
})

const generationDetailProjectText = computed(() => {
  const current = generationDetail.value
  if (!current) return '-'
  return current.projectName || '项目名称未返回'
})

const canRetryGeneration = computed(() => (
  userStore.hasPermission('content.ai.generate')
  && Boolean(generationDetail.value?.failedCount)
))

const publishDetailPendingCount = computed(() => {
  const current = publishDetail.value
  if (!current) return 0
  return Math.max((current.totalCount || 0) - (current.successCount || 0) - (current.failedCount || 0), 0)
})

const publishDetailProgress = computed(() => {
  const current = publishDetail.value
  if (!current?.totalCount) return 0
  const done = (current.successCount || 0) + (current.failedCount || 0)
  return Math.min(100, Math.round((done / current.totalCount) * 100))
})

const detailPlatformText = computed(() => {
  const platforms = Array.from(new Set(
    (publishDetail.value?.items || [])
      .map((item) => platformLabel(item.platformKey))
      .filter((item) => item && item !== '-'),
  ))
  return platforms.length ? platforms.join('、') : '暂无平台'
})

onMounted(async () => {
  await Promise.all([dictStore.ensureLoaded(), loadActiveTab()])
  const initialBatchId = Number(route.query.batchId)
  const initialJobId = Number(route.query.jobId)
  if (activeTab.value === 'generation' && Number.isFinite(initialBatchId) && initialBatchId > 0) {
    await openGenerationDetail(initialBatchId)
  } else if (activeTab.value === 'publish' && Number.isFinite(initialJobId) && initialJobId > 0) {
    await openPublishDetail(initialJobId)
  }
  startTimer()
})

onBeforeUnmount(stopTimer)

watch(autoRefresh, (enabled) => {
  if (enabled) startTimer()
  else stopTimer()
})

async function handleTabChange() {
  await loadActiveTab()
}

async function loadActiveTab() {
  if (activeTab.value === 'generation') {
    await loadGeneration()
  } else {
    await loadPublish()
  }
}

async function loadGeneration() {
  generationLoading.value = true
  try {
    const { data } = await getBatchArticleGenerationJobs({
      current: generationPage.current,
      size: generationPage.size,
      status: generationQuery.status || undefined,
      projectName: generationQuery.projectName || undefined,
    })
    generationRows.value = data.data.records || []
    generationPage.total = data.data.total || 0
    if (generationDetailVisible.value && generationDetail.value?.batchId) {
      await loadGenerationDetail(generationDetail.value.batchId, false)
    }
  } finally {
    generationLoading.value = false
  }
}

function searchGeneration() {
  generationPage.current = 1
  loadGeneration()
}

function resetGeneration() {
  generationQuery.status = ''
  generationQuery.projectName = ''
  searchGeneration()
}

function onGenerationPageChange(v: number) {
  generationPage.current = v
  loadGeneration()
}

async function openGenerationDetail(batchId: number) {
  generationDetailVisible.value = true
  await loadGenerationDetail(batchId, true)
}

async function loadGenerationDetail(batchId: number, showLoading: boolean) {
  if (showLoading) generationDetailLoading.value = true
  try {
    const { data } = await getBatchArticleGeneration(batchId)
    generationDetail.value = data.data
  } finally {
    if (showLoading) generationDetailLoading.value = false
  }
}

async function retryGenerationFailedTasks() {
  const batchId = generationDetail.value?.batchId
  if (!batchId) return
  try {
    await ElMessageBox.confirm('将重新提交当前批次中的失败任务，确认继续？', '重试失败任务', {
      confirmButtonText: '重试',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  generationRetrying.value = true
  try {
    const { data } = await retryFailedBatchArticleGeneration(batchId)
    generationDetail.value = data.data
    await loadGeneration()
    ElMessage.success('失败任务已重新提交')
  } finally {
    generationRetrying.value = false
  }
}

async function loadPublish() {
  publishLoading.value = true
  try {
    const { data } = await getBatchArticlePublishJobs({
      current: publishPage.current,
      size: publishPage.size,
      status: publishQuery.status || undefined,
      jobSource: publishQuery.jobSource,
    })
    publishRows.value = data.data.records || []
    publishPage.total = data.data.total || 0
    if (publishDetailVisible.value && publishDetail.value?.jobId) {
      await loadPublishDetail(publishDetail.value.jobId, false)
    }
  } finally {
    publishLoading.value = false
  }
}

function searchPublish() {
  publishPage.current = 1
  loadPublish()
}

function resetPublish() {
  publishQuery.status = ''
  publishQuery.jobSource = 'manual'
  searchPublish()
}

function onPublishPageChange(v: number) {
  publishPage.current = v
  loadPublish()
}

async function openPublishDetail(jobId: number) {
  publishDetailVisible.value = true
  await loadPublishDetail(jobId, true)
}

async function loadPublishDetail(jobId: number, showLoading: boolean) {
  if (showLoading) publishDetailLoading.value = true
  try {
    const { data } = await getBatchArticlePublish(jobId)
    publishDetail.value = data.data
  } finally {
    if (showLoading) publishDetailLoading.value = false
  }
}

function startTimer() {
  stopTimer()
  refreshTimer = window.setInterval(() => {
    if (autoRefresh.value) loadActiveTab()
  }, 15000)
}

function stopTimer() {
  if (refreshTimer) {
    window.clearInterval(refreshTimer)
    refreshTimer = null
  }
}

function generationDoneCount(row: BatchArticleGenerationBatchSummary) {
  return (row.successCount || 0) + (row.failedCount || 0)
}

function generationPendingCount(row: BatchArticleGenerationBatchSummary) {
  return Math.max((row.totalCount || 0) - generationDoneCount(row), 0)
}

function pendingPublishCount(row: BatchArticlePublishJobSummary) {
  const total = row.totalCount || 0
  const done = (row.successCount || 0) + (row.failedCount || 0)
  return Math.max(total - done, 0)
}

function topicSourceLabel(value?: string | null) {
  return value === 'keyword_group' ? '拓词组' : '手动批量'
}

function generationStatusLabel(value: string) {
  const map: Record<string, string> = {
    pending: '待生成',
    running: '生成中',
    success: '成功',
    partial_success: '部分成功',
    failed: '失败',
  }
  return map[value] || value
}

function generationTaskStatusLabel(value: string) {
  const map: Record<string, string> = {
    pending: '待生成',
    running: '生成中',
    success: '成功',
    failed: '失败',
  }
  return map[value] || value
}

function generationStatusClass(value: string) {
  if (value === 'success') return 'is-success'
  if (value === 'failed' || value === 'partial_success') return 'is-danger'
  if (value === 'running') return 'is-warning'
  return 'is-muted'
}

function generationStatusShortLabel(value: string) {
  if (value === 'success') return '成'
  if (value === 'failed') return '败'
  if (value === 'partial_success') return '部'
  if (value === 'running') return '生'
  return '待'
}

function channelText(row: BatchArticleGenerationTask) {
  return [
    channelGroupLabel(row.channelGroupCode),
    channelSubLabel(row.channelSubCode),
    contentStyleLabel(row.contentStyle),
  ].filter((item) => item && item !== '-').join(' / ') || '-'
}

function generationTaskTitle(row: BatchArticleGenerationTask) {
  const index = row.articleIndexInBatch ? ` #${row.articleIndexInBatch}` : ''
  return row.topic || `${generationDetail.value?.topic || '批量生成文章'}${index}`
}

function promptTemplateText(row: BatchArticleGenerationTask) {
  const name = row.promptTemplateName || '模板名称未返回'
  const version = row.promptTemplateVersionNo ? `v${row.promptTemplateVersionNo}` : '版本未返回'
  return `${name} / ${version}`
}

function dictLabel(dictType: string, value?: string | null) {
  if (!value) return '-'
  return dictStore.label(dictType, value) || value
}

function channelGroupLabel(value?: string | null) {
  const fallback: Record<string, string> = {
    self_media: '自媒体平台',
    official_site: '品牌官网',
    industry_site: '行业资讯站',
    forum: '论坛社区',
    authority_media: '权威媒体',
  }
  if (!value) return '-'
  const label = dictLabel('channel_group', value)
  return label === value ? (fallback[value] || value) : label
}

function channelSubLabel(value?: string | null) {
  const fallback: Record<string, string> = {
    toutiao: '今日头条',
    wechat: '公众号',
    wechat_mp: '微信公众号',
    zhihu: '知乎',
    douyin: '抖音图文',
    xiaohongshu: '小红书',
    baijiahao: '百家号',
    netease: '网易',
    sohu: '搜狐',
  }
  if (!value) return '-'
  const label = dictLabel('channel_sub', value)
  return label === value ? (fallback[value] || value) : label
}

function contentStyleLabel(value?: string | null) {
  const fallback: Record<string, string> = {
    toutiao: '今日头条风格',
    wechat: '公众号风格',
    zhihu: '知乎风格',
    douyin: '抖音图文风格',
    xiaohongshu: '小红书风格',
    baijiahao: '百家号风格',
    netease: '网易风格',
    sohu: '搜狐风格',
  }
  if (!value) return '-'
  const label = dictLabel('content_style', value)
  return label === value ? (fallback[value] || value) : label
}

function qualityStatusLabel(value?: string | null) {
  const fallback: Record<string, string> = {
    passed: '通过',
    warning: '有提醒',
    failed: '未通过',
  }
  if (!value) return '-'
  const label = dictLabel('article_quality_status', value)
  return label === value ? (fallback[value] || value) : label
}

function publishModeLabel(value: string) {
  return value === 'scheduled' ? '定时发布' : '立刻发布'
}

function fallbackJobName(row: BatchArticlePublishJobSummary) {
  return `${publishModeLabel(row.publishMode)}任务 · ${row.totalCount || 0} 篇 · ${formatJobScheduledAt(row)}`
}

function jobSourceLabel(value?: string | null) {
  return value === 'auto' ? '自动分发' : '手动批量'
}

function jobStatusLabel(value: string) {
  const map: Record<string, string> = {
    pending: '待执行',
    running: '执行中',
    completed: '已完成',
    partial_failed: '部分失败',
    failed: '失败',
  }
  return map[value] || value
}

function itemStatusLabel(value: string) {
  const map: Record<string, string> = {
    pending: '待执行',
    running: '执行中',
    success: '成功',
    failed: '失败',
  }
  return map[value] || value
}

function publishStatusClass(value: string) {
  if (value === 'completed') return 'is-success'
  if (value === 'failed' || value === 'partial_failed') return 'is-danger'
  if (value === 'running') return 'is-warning'
  return 'is-muted'
}

function publishItemStatusClass(value: string) {
  if (value === 'success') return 'is-success'
  if (value === 'failed') return 'is-danger'
  if (value === 'running') return 'is-warning'
  return 'is-muted'
}

function jobStatusShortLabel(value: string) {
  if (value === 'completed') return '成'
  if (value === 'failed') return '败'
  if (value === 'partial_failed') return '部'
  if (value === 'running') return '执'
  return '待'
}

function platformLabel(value: string) {
  if (value === 'agent_site') return 'Agent 官网'
  if (value === 'industry_site') return '行业资讯站'
  if (value === 'forum_site') return '平台网站'
  return value || '-'
}

function formatNullableDateTime(value?: string | null) {
  return value ? formatDateTimeSeconds(value) : '-'
}

function resolveJobScheduledAt(row: BatchArticlePublishJobSummary) {
  return row.scheduledAt || (row.publishMode === 'now' ? row.createdAt : null)
}

function formatJobScheduledAt(row: BatchArticlePublishJobSummary) {
  return formatNullableDateTime(resolveJobScheduledAt(row))
}

function resolveDetailScheduledAt(current: BatchArticlePublishResponse) {
  return current.scheduledAt || (current.publishMode === 'now' ? current.items?.[0]?.plannedAt || null : null)
}

function formatDetailScheduledAt(current: BatchArticlePublishResponse) {
  return formatNullableDateTime(resolveDetailScheduledAt(current))
}

function openArticleDetail(articleId: number) {
  router.push({
    path: '/admin/content/execution',
    query: { articleId: String(articleId) },
  })
}

function goBack() {
  router.push('/admin/content/execution')
}
</script>

<style scoped>
.batch-jobs-page {
  display: grid;
  gap: 16px;
}

.jobs-hero {
  display: grid;
  grid-template-columns: minmax(260px, 0.82fr) minmax(460px, 1fr);
  gap: 18px;
  align-items: stretch;
  overflow: hidden;
  border: 1px solid #dbe7f5;
  border-radius: 16px;
  background: linear-gradient(135deg, #ffffff 0%, #f8fbff 55%, #eef6ff 100%);
  padding: 20px;
  box-shadow: 0 14px 34px rgba(15, 23, 42, 0.06);
}

.jobs-kicker {
  color: #2563eb;
  font-size: 12px;
  font-weight: 800;
}

.jobs-hero h1 {
  margin: 6px 0 8px;
  color: #0f172a;
  font-size: 26px;
  line-height: 1.25;
  font-weight: 800;
}

.jobs-hero p {
  max-width: 560px;
  margin: 0;
  color: #64748b;
  font-size: 14px;
  line-height: 1.7;
}

.jobs-stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.jobs-stat-card {
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.86);
  padding: 14px;
  box-shadow: inset 3px 0 0 #3b82f6;
}

.jobs-stat-card span,
.batch-metric-card span {
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.jobs-stat-card strong,
.batch-metric-card strong {
  color: #0f172a;
  font-size: 26px;
  line-height: 1;
  font-weight: 800;
}

.jobs-stat-card.is-warning,
.batch-metric-card.is-pending {
  box-shadow: inset 3px 0 0 #f59e0b;
}

.jobs-stat-card.is-success,
.batch-metric-card.is-success {
  box-shadow: inset 3px 0 0 #10b981;
}

.jobs-stat-card.is-danger,
.batch-metric-card.is-danger {
  box-shadow: inset 3px 0 0 #ef4444;
}

.jobs-card {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.045);
}

.jobs-card :deep(.el-card__body) {
  padding: 0;
}

.jobs-tabs :deep(.el-tabs__header) {
  margin: 0;
  padding: 0 16px;
}

.jobs-tabs :deep(.el-tabs__content) {
  padding: 0;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border-bottom: 1px solid #eef2f7;
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.pager {
  display: flex;
  justify-content: flex-end;
  padding: 16px;
  border-top: 1px solid #eef2f7;
}

.jobs-table :deep(.el-table__header th) {
  background: #f8fafc;
  color: #475569;
  font-weight: 800;
}

.jobs-table :deep(.el-table__cell) {
  border-bottom-color: #edf2f7;
}

.jobs-table :deep(.el-table__body td) {
  padding: 11px 0;
}

.job-name-cell,
.admin-cell-stack {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.job-name-main,
.admin-cell-main {
  overflow: hidden;
  color: #0f172a;
  font-size: 14px;
  font-weight: 800;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.job-name-sub,
.admin-cell-sub {
  overflow: hidden;
  display: flex;
  align-items: center;
  gap: 6px;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.job-source-dot,
.publish-mode-pill,
.interval-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  padding: 2px 8px;
  color: #2563eb;
  background: #eff6ff;
  font-size: 11px;
  font-weight: 800;
  white-space: nowrap;
}

.job-source-dot.is-auto {
  background: #fff7ed;
  color: #c2410c;
}

.publish-mode-pill,
.interval-pill {
  min-height: 28px;
  padding: 0 10px;
  font-size: 12px;
}

.publish-mode-pill.is-scheduled {
  color: #7c3aed;
  background: #f5f3ff;
}

.interval-pill {
  color: #475569;
  background: #f1f5f9;
}

.job-progress-summary {
  display: flex;
  align-items: center;
  gap: 5px;
  color: #64748b;
  font-size: 13px;
  white-space: nowrap;
}

.job-progress-count {
  color: #0f172a;
  font-size: 14px;
  font-weight: 800;
}

.job-progress-label,
.job-progress-failed,
.job-progress-pending {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.job-progress-failed {
  margin-left: 4px;
  color: #dc2626;
}

.job-progress-pending {
  margin-left: 4px;
  color: #b45309;
}

.article-title-link {
  height: auto;
  justify-content: flex-start;
  padding: 0;
  color: #2563eb;
  font-weight: 800;
  line-height: 1.45;
  white-space: normal;
  text-align: left;
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
  color: #0f172a;
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
  background: linear-gradient(135deg, #ffffff 0%, #f8fbff 58%, #ecfdf5 100%);
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
  flex: 1 1 auto;
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

.batch-table-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px;
  border-bottom: 1px solid #eef2f7;
  background: linear-gradient(90deg, #f8fbff 0%, #ffffff 55%, #f0fdf4 100%);
}

.batch-table-title {
  color: #0f172a;
  font-size: 16px;
  font-weight: 800;
}

.batch-table-subtitle {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
}

.batch-detail-table :deep(.el-table) {
  border-radius: 0 0 14px 14px;
}

@media (max-width: 768px) {
  .jobs-hero,
  .jobs-stat-grid,
  .batch-progress-panel,
  .batch-progress-main,
  .batch-metric-grid {
    grid-template-columns: 1fr;
  }

  .toolbar,
  .batch-detail-hero,
  .batch-table-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .batch-detail-drawer {
    width: 100% !important;
  }

  .batch-progress-ring {
    margin-inline: auto;
  }
}
</style>
