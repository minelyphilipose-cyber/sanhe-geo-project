<template>
  <div class="special-compliance-page admin-page">
    <div class="admin-page-header">
      <div>
        <div class="admin-page-kicker">合规运营</div>
        <h1 class="admin-page-title">特殊行业合规工作台</h1>
        <div class="admin-page-subtitle">集中处理医疗等强监管行业的法务确认、合规失败、命中日志和生成历史。</div>
      </div>
      <el-button @click="openMedicalConfig">规则配置</el-button>
    </div>

    <div class="admin-metric-grid">
      <div class="admin-metric-card" style="--metric-accent: #f59e0b; --metric-tone: #fffbeb">
        <span class="admin-metric-label">待法务确认</span>
        <strong class="admin-metric-value">{{ pendingReviewCount }}</strong>
        <span class="admin-metric-hint">当前筛选结果</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #ef4444; --metric-tone: #fef2f2">
        <span class="admin-metric-label">合规失败/废弃</span>
        <strong class="admin-metric-value">{{ failedComplianceCount }}</strong>
        <span class="admin-metric-hint">当前筛选结果</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #2563eb; --metric-tone: #eff6ff">
        <span class="admin-metric-label">命中日志</span>
        <strong class="admin-metric-value">{{ logPage.total }}</strong>
        <span class="admin-metric-hint">规则触发记录</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #059669; --metric-tone: #ecfdf5">
        <span class="admin-metric-label">生成历史</span>
        <strong class="admin-metric-value">{{ historyPage.total }}</strong>
        <span class="admin-metric-hint">选题与结构留痕</span>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="workbench-tabs" @tab-change="handleTabChange">
      <el-tab-pane label="待处理文章" name="articles" />
      <el-tab-pane label="命中日志" name="logs" />
      <el-tab-pane label="生成历史" name="history" />
    </el-tabs>

    <el-card v-show="activeTab === 'articles'" shadow="never" class="admin-table-card">
      <div class="preset-bar">
        <el-button v-for="item in articlePresets" :key="item.key" size="small" @click="applyArticlePreset(item.key)">
          {{ item.label }}
        </el-button>
      </div>
      <div class="filter-bar">
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
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import DataState from '@/components/ui/DataState.vue'
import {
  getMedicalComplianceHitLogs,
  getMedicalGenerationHistory,
  getSpecialIndustryArticles,
  reviewMedicalPublishArticle,
  type MedicalComplianceHitLog,
  type MedicalGenerationHistory,
} from '@/api/content'
import type { ArticleDraft } from '@/types'
import { useDictStore } from '@/stores/dict'
import { useUserStore } from '@/stores/user'
import { formatDateTime } from '@/utils/format'

type TabName = 'articles' | 'logs' | 'history'
type ArticlePresetKey = 'pending_review' | 'review_rejected' | 'compliance_discarded' | 'official_pending'

const router = useRouter()
const dictStore = useDictStore()
const userStore = useUserStore()
const activeTab = ref<TabName>('articles')

const articleLoading = ref(false)
const logLoading = ref(false)
const historyLoading = ref(false)
const articleRows = ref<ArticleDraft[]>([])
const logRows = ref<MedicalComplianceHitLog[]>([])
const historyRows = ref<MedicalGenerationHistory[]>([])
const articlePage = reactive({ current: 1, size: 10, total: 0 })
const logPage = reactive({ current: 1, size: 10, total: 0 })
const historyPage = reactive({ current: 1, size: 10, total: 0 })
const articleQuery = reactive({
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
const pendingReviewCount = computed(() => articleRows.value.filter((row) => row.publishReviewStatus === 'pending').length)
const failedComplianceCount = computed(() => articleRows.value.filter((row) =>
  row.complianceStatus === 'failed' || row.complianceStatus === 'discarded_compliance_failed',
).length)
const canArticleWrite = computed(() => userStore.hasPermission(['content.article.write', 'project.update']))

async function loadArticles() {
  articleLoading.value = true
  try {
    const { data } = await getSpecialIndustryArticles({
      current: articlePage.current,
      size: articlePage.size,
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
    const { data } = await getMedicalComplianceHitLogs({
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
    const { data } = await getMedicalGenerationHistory({
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

function searchArticles() {
  articlePage.current = 1
  void loadArticles()
}

function resetArticleQuery() {
  articleQuery.projectName = ''
  articleQuery.medicalIndustryCode = ''
  articleQuery.medicalChannelTier = ''
  articleQuery.complianceStatus = ''
  articleQuery.publishReviewStatus = ''
  searchArticles()
}

function applyArticlePreset(key: ArticlePresetKey) {
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

function onHistoryPageChange(page: number) {
  historyPage.current = page
  void loadHistory()
}

function handleTabChange(name: string | number) {
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

function openMedicalConfig() {
  router.push('/admin/content/medical-article-config')
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
  await loadArticles()
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

function channelLabel(row: ArticleDraft) {
  return [row.channelGroupCode, row.channelSubCode].filter(Boolean).join(' / ') || '-'
}

onMounted(async () => {
  await dictStore.ensureLoaded()
  await Promise.all([loadArticles(), loadLogs(), loadHistory()])
})
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
</style>
