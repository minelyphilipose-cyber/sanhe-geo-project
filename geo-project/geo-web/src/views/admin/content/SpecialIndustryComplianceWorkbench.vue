<template>
  <div class="special-compliance-page admin-page">
    <div class="admin-page-header">
      <div>
        <div class="admin-page-kicker">专项运营</div>
        <h1 class="admin-page-title">行业专项工作台</h1>
        <div class="admin-page-subtitle">集中处理医疗等强监管行业的法务确认、合规失败、命中日志和生成历史。</div>
      </div>
      <el-button @click="openSpecialIndustryConfig">规则配置</el-button>
    </div>

    <div class="admin-metric-grid">
      <div class="admin-metric-card" style="--metric-accent: #f59e0b; --metric-tone: #fffbeb">
        <span class="admin-metric-label">待法务确认</span>
        <strong class="admin-metric-value">{{ overview?.pendingReviewCount ?? 0 }}</strong>
        <span class="admin-metric-hint">全局待处理</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #ef4444; --metric-tone: #fef2f2">
        <span class="admin-metric-label">合规失败/废弃</span>
        <strong class="admin-metric-value">{{ overview?.complianceFailedCount ?? 0 }}</strong>
        <span class="admin-metric-hint">需排查文章</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #2563eb; --metric-tone: #eff6ff">
        <span class="admin-metric-label">近 7 日命中</span>
        <strong class="admin-metric-value">{{ overview?.sevenDayHitCount ?? 0 }}</strong>
        <span class="admin-metric-hint">规则触发记录</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #059669; --metric-tone: #ecfdf5">
        <span class="admin-metric-label">近 7 日废弃</span>
        <strong class="admin-metric-value">{{ overview?.sevenDayDiscardedCount ?? 0 }}</strong>
        <span class="admin-metric-hint">3 次失败后留痕</span>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="workbench-tabs" @tab-change="handleTabChange">
      <el-tab-pane label="概览" name="overview" />
      <el-tab-pane label="待处理文章" name="articles" />
      <el-tab-pane label="批次追溯" name="batches" />
      <el-tab-pane label="命中日志" name="logs" />
      <el-tab-pane label="生成历史" name="history" />
    </el-tabs>

    <el-card v-show="activeTab === 'overview'" v-loading="overviewLoading" shadow="never" class="admin-table-card">
      <div class="overview-grid">
        <section class="overview-panel">
          <div class="overview-panel-head">
            <strong>规则命中 Top</strong>
            <span>近 7 日</span>
          </div>
          <el-table :data="overview?.topRuleHits || []" border>
            <el-table-column prop="ruleType" label="规则类型" min-width="180" />
            <el-table-column prop="hitCount" label="命中次数" width="120" />
          </el-table>
        </section>
        <section class="overview-panel">
          <div class="overview-panel-head">
            <strong>问题批次</strong>
            <span>最近 5 条</span>
          </div>
          <el-table :data="overview?.recentProblemBatches || []" border>
            <el-table-column label="批次" width="110">
              <template #default="{ row }">#{{ row.batchId }}</template>
            </el-table-column>
            <el-table-column label="项目/品牌" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ row.projectName || row.projectId || '-' }} / {{ row.brandName || row.brandId || '-' }}</template>
            </el-table-column>
            <el-table-column label="失败/废弃" width="120">
              <template #default="{ row }">{{ row.failedCount || 0 }} / {{ row.discardedCount || 0 }}</template>
            </el-table-column>
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button link type="primary" @click="openBatchLogs(row.batchId)">日志</el-button>
              </template>
            </el-table-column>
          </el-table>
        </section>
      </div>
    </el-card>

    <el-card v-show="activeTab === 'articles'" shadow="never" class="admin-table-card">
      <div class="preset-bar">
        <el-button v-for="item in articlePresets" :key="item.key" size="small" @click="applyArticlePreset(item.key)">
          {{ item.label }}
        </el-button>
      </div>
      <div class="filter-bar">
        <el-input-number v-model="articleQuery.articleId" :min="1" :controls="false" placeholder="文章ID" class="filter-number" />
        <el-input v-model="articleQuery.projectName" clearable placeholder="搜索项目名称" class="filter-control is-wide" @keyup.enter="loadArticles" />
        <el-select v-model="articleQuery.medicalIndustryCode" clearable placeholder="行业类型" class="filter-control">
          <el-option v-for="item in industryOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="articleQuery.medicalChannelTier" clearable placeholder="渠道档位" class="filter-control">
          <el-option label="科普" value="education" />
          <el-option label="信源站" value="source_site" />
          <el-option label="官网" value="official_site" />
        </el-select>
        <el-select v-model="articleQuery.complianceStatus" clearable placeholder="合规状态" class="filter-control">
          <el-option label="合规通过" value="passed" />
          <el-option label="合规失败" value="failed" />
          <el-option label="已废弃" value="discarded_compliance_failed" />
        </el-select>
        <el-select v-model="articleQuery.publishReviewStatus" clearable placeholder="发布确认" class="filter-control">
          <el-option label="待法务确认" value="pending" />
          <el-option label="法务通过" value="passed" />
          <el-option label="法务驳回" value="rejected" />
          <el-option label="无需法务" value="not_required" />
        </el-select>
        <el-button type="primary" @click="searchArticles">查询</el-button>
        <el-button @click="resetArticleQuery">重置</el-button>
      </div>

      <DataState :loading="articleLoading" :empty="!articleLoading && articleRows.length === 0" empty-text="暂无特殊行业文章">
        <el-table :data="articleRows" border table-layout="fixed">
          <el-table-column label="文章" min-width="300" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="article-cell">
                <strong>{{ row.title || `#${row.id}` }}</strong>
                <span>{{ row.projectName || `项目 #${row.projectId}` }} · {{ channelLabel(row) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="行业/档位" width="170">
            <template #default="{ row }">
              <div class="tag-stack">
                <el-tag size="small" type="info">{{ industryLabel(row.medicalIndustryCode) }}</el-tag>
                <el-tag size="small">{{ tierLabel(row.medicalChannelTier) }}</el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="合规状态" width="120">
            <template #default="{ row }">
              <el-tag size="small" :type="complianceTag(row.complianceStatus)">{{ complianceLabel(row.complianceStatus) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="发布确认" width="130">
            <template #default="{ row }">
              <el-tag size="small" :type="reviewTag(row.publishReviewStatus)">{{ reviewLabel(row.publishReviewStatus) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="260" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openContentExecution">内容列表</el-button>
              <el-button link @click="openLogsForArticle(row.id)">命中日志</el-button>
              <el-button v-if="canReview(row)" link type="warning" @click="reviewMedicalPublish(row, 'approve')">法务通过</el-button>
              <el-button v-if="canReview(row)" link type="danger" @click="reviewMedicalPublish(row, 'reject')">驳回</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="admin-table-footer">
          <el-pagination background layout="prev, pager, next, total" :current-page="articlePage.current" :page-size="articlePage.size" :total="articlePage.total" @current-change="onArticlePageChange" />
        </div>
      </DataState>
    </el-card>

    <el-card v-show="activeTab === 'batches'" shadow="never" class="admin-table-card">
      <div class="filter-bar">
        <el-select v-model="batchQuery.status" clearable placeholder="批次状态" class="filter-control">
          <el-option label="运行中" value="running" />
          <el-option label="成功" value="success" />
          <el-option label="部分成功" value="partial_success" />
          <el-option label="失败" value="failed" />
        </el-select>
        <el-select v-model="batchQuery.industryCode" clearable placeholder="行业类型" class="filter-control">
          <el-option v-for="item in industryOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-button type="primary" @click="searchBatches">查询</el-button>
        <el-button @click="resetBatchQuery">重置</el-button>
      </div>
      <DataState :loading="batchLoading" :empty="!batchLoading && batchRows.length === 0" empty-text="暂无特殊行业批次">
        <el-table :data="batchRows" border table-layout="fixed">
          <el-table-column label="批次" width="110">
            <template #default="{ row }">#{{ row.batchId }}</template>
          </el-table-column>
          <el-table-column label="项目/品牌" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ row.projectName || row.projectId || '-' }} / {{ row.brandName || row.brandId || '-' }}</template>
          </el-table-column>
          <el-table-column label="行业/档位" width="170">
            <template #default="{ row }">
              <div class="tag-stack">
                <el-tag size="small" type="info">{{ industryLabel(row.medicalIndustryCode) }}</el-tag>
                <el-tag size="small">{{ tierLabel(row.medicalChannelTier) }}</el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="topic" label="主题" min-width="220" show-overflow-tooltip />
          <el-table-column label="进度" width="150">
            <template #default="{ row }">{{ row.successCount || 0 }}/{{ row.totalCount || 0 }} 成功，{{ row.failedCount || 0 }} 失败</template>
          </el-table-column>
          <el-table-column label="废弃/重试" width="130">
            <template #default="{ row }">{{ row.discardedCount || 0 }} / {{ row.retryTaskCount || 0 }}</template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }"><el-tag size="small" :type="batchStatusTag(row.status)">{{ batchStatusLabel(row.status) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="创建时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openBatchLogs(row.batchId)">命中日志</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="admin-table-footer">
          <el-pagination background layout="prev, pager, next, total" :current-page="batchPage.current" :page-size="batchPage.size" :total="batchPage.total" @current-change="onBatchPageChange" />
        </div>
      </DataState>
    </el-card>

    <el-card v-show="activeTab === 'logs'" shadow="never" class="admin-table-card">
      <div class="filter-bar">
        <el-input-number v-model="logQuery.articleId" :min="1" :controls="false" placeholder="文章ID" class="filter-number" />
        <el-input-number v-model="logQuery.batchId" :min="1" :controls="false" placeholder="批次ID" class="filter-number" />
        <el-input-number v-model="logQuery.taskId" :min="1" :controls="false" placeholder="任务ID" class="filter-number" />
        <el-input-number v-model="logQuery.projectId" :min="1" :controls="false" placeholder="项目ID" class="filter-number" />
        <el-input-number v-model="logQuery.brandId" :min="1" :controls="false" placeholder="品牌ID" class="filter-number" />
        <el-input v-model="logQuery.ruleType" clearable placeholder="规则类型" class="filter-control" />
        <el-select v-model="logQuery.action" clearable placeholder="处理动作" class="filter-control">
          <el-option label="重试" value="retry" />
          <el-option label="废弃" value="discard" />
          <el-option label="阻断" value="block" />
        </el-select>
        <el-date-picker
          v-model="logQuery.createdRange"
          class="filter-date"
          type="daterange"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          value-format="YYYY-MM-DD"
          unlink-panels
          clearable
        />
        <el-button type="primary" @click="searchLogs">查询</el-button>
        <el-button @click="resetLogQuery">重置</el-button>
      </div>
      <DataState :loading="logLoading" :empty="!logLoading && logRows.length === 0" empty-text="暂无命中日志">
        <el-table :data="logRows" border table-layout="fixed">
          <el-table-column prop="createdAt" label="时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="项目/品牌" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ row.projectName || row.projectId || '-' }} / {{ row.brandName || row.brandId || '-' }}</template>
          </el-table-column>
          <el-table-column label="对象" width="180">
            <template #default="{ row }">文章 {{ row.articleId || '-' }} · 批次 {{ row.batchId || '-' }} · 任务 {{ row.taskId || '-' }}</template>
          </el-table-column>
          <el-table-column prop="ruleType" label="规则类型" width="150" />
          <el-table-column prop="matchedText" label="命中文本" min-width="260" show-overflow-tooltip />
          <el-table-column prop="checkStage" label="阶段" width="120" />
          <el-table-column prop="action" label="动作" width="120" />
        </el-table>
        <div class="admin-table-footer">
          <el-pagination background layout="prev, pager, next, total" :current-page="logPage.current" :page-size="logPage.size" :total="logPage.total" @current-change="onLogPageChange" />
        </div>
      </DataState>
    </el-card>

    <el-card v-show="activeTab === 'history'" shadow="never" class="admin-table-card">
      <div class="filter-bar">
        <el-input-number v-model="historyQuery.projectId" :min="1" :controls="false" placeholder="项目ID" class="filter-number" />
        <el-input-number v-model="historyQuery.brandId" :min="1" :controls="false" placeholder="品牌ID" class="filter-number" />
        <el-input-number v-model="historyQuery.articleId" :min="1" :controls="false" placeholder="文章ID" class="filter-number" />
        <el-input-number v-model="historyQuery.topicAngleId" :min="1" :controls="false" placeholder="选题ID" class="filter-number" />
        <el-button type="primary" @click="searchHistory">查询</el-button>
        <el-button @click="resetHistoryQuery">重置</el-button>
      </div>
      <DataState :loading="historyLoading" :empty="!historyLoading && historyRows.length === 0" empty-text="暂无生成历史">
        <el-table :data="historyRows" border table-layout="fixed">
          <el-table-column prop="createdAt" label="时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="项目/品牌" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ row.projectName || row.projectId || '-' }} / {{ row.brandName || row.brandId || '-' }}</template>
          </el-table-column>
          <el-table-column label="文章" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ row.articleTitle || row.articleId || '-' }}</template>
          </el-table-column>
          <el-table-column label="选题角度" min-width="240" show-overflow-tooltip>
            <template #default="{ row }">{{ row.topicAngle || row.topicAngleId || '-' }}</template>
          </el-table-column>
          <el-table-column prop="focus" label="侧重" width="140" />
          <el-table-column prop="structureSkeleton" label="结构骨架" min-width="320" show-overflow-tooltip />
        </el-table>
        <div class="admin-table-footer">
          <el-pagination background layout="prev, pager, next, total" :current-page="historyPage.current" :page-size="historyPage.size" :total="historyPage.total" @current-change="onHistoryPageChange" />
        </div>
      </DataState>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import DataState from '@/components/ui/DataState.vue'
import {
  getSpecialIndustryBatches,
  getSpecialIndustryComplianceHitLogs,
  getSpecialIndustryGenerationHistory,
  getSpecialIndustryOverview,
  getSpecialIndustryArticles,
  reviewMedicalPublishArticle,
  type MedicalComplianceHitLog,
  type MedicalGenerationHistory,
  type SpecialIndustryBatchTrace,
  type SpecialIndustryOverview,
} from '@/api/content'
import type { ArticleDraft } from '@/types'
import { useDictStore } from '@/stores/dict'
import { useUserStore } from '@/stores/user'
import { formatDateTime } from '@/utils/format'

type TabName = 'overview' | 'articles' | 'batches' | 'logs' | 'history'
type ArticlePresetKey = 'pending_review' | 'review_rejected' | 'compliance_discarded' | 'official_pending'

const router = useRouter()
const route = useRoute()
const dictStore = useDictStore()
const userStore = useUserStore()
const activeTab = ref<TabName>('overview')

const overviewLoading = ref(false)
const articleLoading = ref(false)
const batchLoading = ref(false)
const logLoading = ref(false)
const historyLoading = ref(false)
const overview = ref<SpecialIndustryOverview | null>(null)
const articleRows = ref<ArticleDraft[]>([])
const batchRows = ref<SpecialIndustryBatchTrace[]>([])
const logRows = ref<MedicalComplianceHitLog[]>([])
const historyRows = ref<MedicalGenerationHistory[]>([])
const articlePage = reactive({ current: 1, size: 10, total: 0 })
const batchPage = reactive({ current: 1, size: 10, total: 0 })
const logPage = reactive({ current: 1, size: 10, total: 0 })
const historyPage = reactive({ current: 1, size: 10, total: 0 })
const articleQuery = reactive({
  articleId: undefined as number | undefined,
  projectName: '',
  medicalIndustryCode: '',
  medicalChannelTier: '',
  complianceStatus: '',
  publishReviewStatus: '',
})
const logQuery = reactive<{
  articleId?: number
  batchId?: number
  taskId?: number
  projectId?: number
  brandId?: number
  ruleType: string
  action: string
  createdRange: string[]
}>({
  ruleType: '',
  action: '',
  createdRange: [],
})
const historyQuery = reactive<{ projectId?: number, brandId?: number, articleId?: number, topicAngleId?: number }>({})
const batchQuery = reactive({
  status: '',
  industryCode: '',
})
const articlePresets: Array<{ key: ArticlePresetKey, label: string }> = [
  { key: 'pending_review', label: '待法务确认' },
  { key: 'review_rejected', label: '法务驳回' },
  { key: 'compliance_discarded', label: '合规失败/已废弃' },
  { key: 'official_pending', label: '官网待处理' },
]

const industryOptions = computed(() =>
  dictStore.options('compliance_industry')
    .filter((item) => item.dictKey && item.dictKey !== 'none')
    .map((item) => ({ label: item.dictValue, value: item.dictKey })),
)
const canArticleWrite = computed(() => userStore.hasPermission(['content.article.write', 'project.update']))

async function loadOverview() {
  overviewLoading.value = true
  try {
    const { data } = await getSpecialIndustryOverview()
    overview.value = data.data
  } catch {
    overview.value = null
    ElMessage.error('加载特殊行业概览失败')
  } finally {
    overviewLoading.value = false
  }
}

async function loadArticles() {
  articleLoading.value = true
  try {
    const { data } = await getSpecialIndustryArticles({
      current: articlePage.current,
      size: articlePage.size,
      articleId: articleQuery.articleId || undefined,
      projectName: articleQuery.projectName.trim() || undefined,
      medicalIndustryCode: articleQuery.medicalIndustryCode || undefined,
      medicalChannelTier: articleQuery.medicalChannelTier || undefined,
      complianceStatus: articleQuery.complianceStatus || undefined,
      publishReviewStatus: articleQuery.publishReviewStatus || undefined,
      specialIndustryOnly: true,
    })
    articleRows.value = data.data.records || []
    articlePage.total = data.data.total || 0
  } catch {
    articleRows.value = []
    articlePage.total = 0
    ElMessage.error('加载特殊行业文章失败')
  } finally {
    articleLoading.value = false
  }
}

async function loadLogs() {
  logLoading.value = true
  try {
    const { data } = await getSpecialIndustryComplianceHitLogs({
      current: logPage.current,
      size: logPage.size,
      articleId: logQuery.articleId || undefined,
      batchId: logQuery.batchId || undefined,
      taskId: logQuery.taskId || undefined,
      projectId: logQuery.projectId || undefined,
      brandId: logQuery.brandId || undefined,
      ruleType: logQuery.ruleType.trim() || undefined,
      action: logQuery.action || undefined,
      createdStartDate: logQuery.createdRange[0] || undefined,
      createdEndDate: logQuery.createdRange[1] || undefined,
    })
    logRows.value = data.data.records || []
    logPage.total = data.data.total || 0
  } catch {
    logRows.value = []
    logPage.total = 0
    ElMessage.error('加载命中日志失败')
  } finally {
    logLoading.value = false
  }
}

async function loadHistory() {
  historyLoading.value = true
  try {
    const { data } = await getSpecialIndustryGenerationHistory({
      current: historyPage.current,
      size: historyPage.size,
      projectId: historyQuery.projectId || undefined,
      brandId: historyQuery.brandId || undefined,
      articleId: historyQuery.articleId || undefined,
      topicAngleId: historyQuery.topicAngleId || undefined,
    })
    historyRows.value = data.data.records || []
    historyPage.total = data.data.total || 0
  } catch {
    historyRows.value = []
    historyPage.total = 0
    ElMessage.error('加载生成历史失败')
  } finally {
    historyLoading.value = false
  }
}

async function loadBatches() {
  batchLoading.value = true
  try {
    const { data } = await getSpecialIndustryBatches({
      current: batchPage.current,
      size: batchPage.size,
      status: batchQuery.status || undefined,
      industryCode: batchQuery.industryCode || undefined,
    })
    batchRows.value = data.data.records || []
    batchPage.total = data.data.total || 0
  } catch {
    batchRows.value = []
    batchPage.total = 0
    ElMessage.error('加载特殊行业批次失败')
  } finally {
    batchLoading.value = false
  }
}

function searchArticles() {
  articlePage.current = 1
  void loadArticles()
}

function resetArticleQuery() {
  articleQuery.articleId = undefined
  articleQuery.projectName = ''
  articleQuery.medicalIndustryCode = ''
  articleQuery.medicalChannelTier = ''
  articleQuery.complianceStatus = ''
  articleQuery.publishReviewStatus = ''
  searchArticles()
}

function applyArticlePreset(key: ArticlePresetKey) {
  articleQuery.articleId = undefined
  articleQuery.projectName = ''
  articleQuery.medicalIndustryCode = ''
  articleQuery.medicalChannelTier = ''
  articleQuery.complianceStatus = ''
  articleQuery.publishReviewStatus = ''
  if (key === 'pending_review') {
    articleQuery.publishReviewStatus = 'pending'
  } else if (key === 'review_rejected') {
    articleQuery.publishReviewStatus = 'rejected'
  } else if (key === 'compliance_discarded') {
    articleQuery.complianceStatus = 'discarded_compliance_failed'
  } else if (key === 'official_pending') {
    articleQuery.medicalChannelTier = 'official_site'
    articleQuery.publishReviewStatus = 'pending'
  }
  searchArticles()
}

function searchLogs() {
  logPage.current = 1
  void loadLogs()
}

function resetLogQuery() {
  logQuery.articleId = undefined
  logQuery.batchId = undefined
  logQuery.taskId = undefined
  logQuery.projectId = undefined
  logQuery.brandId = undefined
  logQuery.ruleType = ''
  logQuery.action = ''
  logQuery.createdRange = []
  searchLogs()
}

function searchBatches() {
  batchPage.current = 1
  void loadBatches()
}

function resetBatchQuery() {
  batchQuery.status = ''
  batchQuery.industryCode = ''
  searchBatches()
}

function searchHistory() {
  historyPage.current = 1
  void loadHistory()
}

function resetHistoryQuery() {
  historyQuery.projectId = undefined
  historyQuery.brandId = undefined
  historyQuery.articleId = undefined
  historyQuery.topicAngleId = undefined
  searchHistory()
}

function onArticlePageChange(page: number) {
  articlePage.current = page
  void loadArticles()
}

function onLogPageChange(page: number) {
  logPage.current = page
  void loadLogs()
}

function onBatchPageChange(page: number) {
  batchPage.current = page
  void loadBatches()
}

function onHistoryPageChange(page: number) {
  historyPage.current = page
  void loadHistory()
}

function handleTabChange(name: string | number) {
  if (name === 'overview') void loadOverview()
  if (name === 'batches' && !batchRows.value.length) void loadBatches()
  if (name === 'logs' && !logRows.value.length) void loadLogs()
  if (name === 'history' && !historyRows.value.length) void loadHistory()
}

function openLogsForArticle(articleId: number) {
  logQuery.articleId = articleId
  logQuery.batchId = undefined
  logQuery.taskId = undefined
  logPage.current = 1
  activeTab.value = 'logs'
  void loadLogs()
}

function openBatchLogs(batchId: number) {
  logQuery.articleId = undefined
  logQuery.batchId = batchId
  logQuery.taskId = undefined
  logPage.current = 1
  activeTab.value = 'logs'
  void loadLogs()
}

function parsePositiveNumber(value: unknown) {
  const raw = Array.isArray(value) ? value[0] : value
  const number = Number(raw)
  return Number.isFinite(number) && number > 0 ? number : undefined
}

function clearLogFocus() {
  logQuery.articleId = undefined
  logQuery.batchId = undefined
  logQuery.taskId = undefined
}

function applyRouteFocus() {
  const action = String(Array.isArray(route.query.action) ? route.query.action[0] : route.query.action || '')
  const articleId = parsePositiveNumber(route.query.articleId)
  const batchId = parsePositiveNumber(route.query.batchId)
  const taskId = parsePositiveNumber(route.query.taskId)

  if (action === 'publish_review_pending' || action === 'publish_review_rejected') {
    articleQuery.articleId = articleId
    articleQuery.projectName = ''
    articleQuery.medicalIndustryCode = ''
    articleQuery.medicalChannelTier = ''
    articleQuery.complianceStatus = ''
    articleQuery.publishReviewStatus = action === 'publish_review_pending' ? 'pending' : 'rejected'
    articlePage.current = 1
    activeTab.value = 'articles'
    void loadArticles()
    return
  }

  if (articleId || batchId || taskId) {
    clearLogFocus()
    logQuery.articleId = articleId
    logQuery.batchId = batchId
    logQuery.taskId = taskId
    logPage.current = 1
    activeTab.value = 'logs'
    void loadLogs()
  }
}

function openSpecialIndustryConfig() {
  router.push('/admin/content/special-industry-config')
}

function openContentExecution() {
  router.push('/admin/content/execution')
}

function canReview(row: ArticleDraft) {
  return canArticleWrite.value
    && row.medicalChannelTier === 'official_site'
    && row.complianceStatus === 'passed'
    && row.publishReviewStatus !== 'passed'
}

async function reviewMedicalPublish(row: ArticleDraft, action: 'approve' | 'reject') {
  const verb = action === 'approve' ? '通过' : '驳回'
  let comment = ''
  try {
    const result = await ElMessageBox.prompt(`请输入医疗官网发布法务${verb}说明`, `医疗发布${verb}`, {
      confirmButtonText: verb,
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputPlaceholder: action === 'approve' ? '例如：已核对广告审查编号/资质信息' : '例如：缺少审查证明或内容需调整',
      inputValidator: (value) => action === 'approve' || !!value.trim() || '驳回时需填写原因',
    })
    comment = result.value || ''
  } catch {
    return
  }
  await reviewMedicalPublishArticle(row.id, { action, comment })
  ElMessage.success(`医疗发布已${verb}`)
  await Promise.all([loadArticles(), loadOverview()])
}

function industryLabel(value?: string | null) {
  if (!value) return '-'
  return dictStore.label('compliance_industry', value)
}

function tierLabel(value?: string | null) {
  const map: Record<string, string> = {
    education: '科普',
    source_site: '信源站',
    official_site: '官网',
  }
  return value ? map[value] || value : '-'
}

function complianceLabel(value?: string | null) {
  const map: Record<string, string> = {
    pending: '待校验',
    passed: '合规通过',
    failed: '合规失败',
    discarded_compliance_failed: '已废弃',
  }
  return value ? map[value] || value : '-'
}

function complianceTag(value?: string | null): 'success' | 'warning' | 'danger' | 'info' {
  if (value === 'passed') return 'success'
  if (value === 'pending') return 'warning'
  if (value === 'failed' || value === 'discarded_compliance_failed') return 'danger'
  return 'info'
}

function reviewLabel(value?: string | null) {
  const map: Record<string, string> = {
    not_required: '无需法务',
    pending: '待法务确认',
    passed: '法务通过',
    rejected: '法务驳回',
  }
  return value ? map[value] || value : '-'
}

function reviewTag(value?: string | null): 'success' | 'warning' | 'danger' | 'info' {
  if (value === 'passed' || value === 'not_required') return 'success'
  if (value === 'pending') return 'warning'
  if (value === 'rejected') return 'danger'
  return 'info'
}

function batchStatusLabel(value?: string | null) {
  const map: Record<string, string> = {
    pending: '待生成',
    running: '运行中',
    success: '成功',
    partial_success: '部分成功',
    failed: '失败',
  }
  return value ? map[value] || value : '-'
}

function batchStatusTag(value?: string | null): 'success' | 'warning' | 'danger' | 'info' {
  if (value === 'success') return 'success'
  if (value === 'running' || value === 'pending' || value === 'partial_success') return 'warning'
  if (value === 'failed') return 'danger'
  return 'info'
}

function channelLabel(row: ArticleDraft) {
  return [row.channelGroupCode, row.channelSubCode].filter(Boolean).join(' / ') || '-'
}

onMounted(async () => {
  await dictStore.ensureLoaded()
  await Promise.all([loadOverview(), loadArticles(), loadBatches(), loadLogs(), loadHistory()])
  applyRouteFocus()
})

watch(
  () => route.fullPath,
  () => {
    applyRouteFocus()
  },
)
</script>

<style scoped>
.special-compliance-page {
  padding: 18px;
}

.workbench-tabs {
  margin-top: 16px;
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-bottom: 14px;
}

.preset-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.filter-control {
  width: 160px;
}

.filter-control.is-wide {
  width: 240px;
}

.filter-number {
  width: 150px;
}

.filter-date {
  width: 260px;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.overview-panel {
  min-width: 0;
}

.overview-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.overview-panel-head strong {
  color: #0f172a;
  font-size: 15px;
}

.overview-panel-head span {
  color: #64748b;
  font-size: 12px;
}

.article-cell,
.tag-stack {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.article-cell strong {
  overflow: hidden;
  color: #0f172a;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.article-cell span {
  overflow: hidden;
  color: #64748b;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 960px) {
  .overview-grid {
    grid-template-columns: 1fr;
  }
}
</style>
