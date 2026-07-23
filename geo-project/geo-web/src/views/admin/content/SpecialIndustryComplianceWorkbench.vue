<template>
  <div class="special-compliance-page admin-page">
    <div class="admin-page-header">
      <div>
        <div class="admin-page-kicker">专项运营</div>
        <h1 class="admin-page-title">行业专项工作台</h1>
        <div class="admin-page-subtitle">集中查看特殊行业的确定性合规结果、提醒、命中日志和生成历史。</div>
      </div>
      <el-button @click="openSpecialIndustryConfig">规则配置</el-button>
    </div>

    <div class="admin-metric-grid">
      <div class="admin-metric-card" style="--metric-accent: #f59e0b; --metric-tone: #fffbeb">
        <span class="admin-metric-label">今日硬规则命中</span>
        <strong class="admin-metric-value">{{ overview?.todayHitCount ?? 0 }}</strong>
        <span class="admin-metric-hint">触发重试或废弃</span>
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
      <el-tab-pane label="特殊行业文章" name="articles" />
      <el-tab-pane label="批次追溯" name="batches" />
      <el-tab-pane label="命中日志" name="logs" />
      <el-tab-pane label="生成历史" name="history" />
    </el-tabs>

    <section class="workbench-guide">
      <div>
        <strong>{{ activeGuide.title }}</strong>
        <p>{{ activeGuide.description }}</p>
      </div>
      <ol>
        <li v-for="step in activeGuide.steps" :key="step">{{ step }}</li>
      </ol>
    </section>

    <el-card v-show="activeTab === 'overview'" v-loading="overviewLoading" shadow="never" class="admin-table-card">
      <div class="overview-grid">
        <section class="overview-panel">
          <div class="overview-panel-head">
            <strong>规则命中 Top</strong>
            <span>近 7 日</span>
          </div>
          <el-table :data="overview?.topRuleHits || []" border>
            <el-table-column label="规则类型" min-width="180">
              <template #default="{ row }">{{ ruleTypeLabel(row.ruleType) }}</template>
            </el-table-column>
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
              <el-tag size="small" :type="complianceTag(row)">{{ complianceLabel(row) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openContentExecution">内容列表</el-button>
              <el-button link @click="openLogsForArticle(row.id)">命中日志</el-button>
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
        <el-input v-model="batchQuery.projectName" clearable placeholder="搜索项目名称" class="filter-control is-wide" @keyup.enter="searchBatches" />
        <el-input v-model="batchQuery.brandName" clearable placeholder="搜索品牌名称" class="filter-control is-wide" @keyup.enter="searchBatches" />
        <el-input v-model="batchQuery.topicKeyword" clearable placeholder="搜索主题关键词" class="filter-control is-wide" @keyup.enter="searchBatches" />
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
      <el-alert
        v-if="logFocusText"
        class="focus-alert"
        type="info"
        show-icon
        :closable="false"
        :title="logFocusText"
      />
      <div class="filter-bar">
        <el-input v-model="logQuery.projectName" clearable placeholder="搜索项目名称" class="filter-control is-wide" @keyup.enter="searchLogs" />
        <el-input v-model="logQuery.brandName" clearable placeholder="搜索品牌名称" class="filter-control is-wide" @keyup.enter="searchLogs" />
        <el-input v-model="logQuery.articleTitle" clearable placeholder="搜索文章标题" class="filter-control is-wide" @keyup.enter="searchLogs" />
        <el-select v-model="logQuery.ruleType" clearable filterable placeholder="规则类型" class="filter-control">
          <el-option v-for="item in ruleTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
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
          <el-table-column label="规则类型" width="180">
            <template #default="{ row }">{{ ruleTypeLabel(row.ruleType) }}</template>
          </el-table-column>
          <el-table-column label="命中文本" min-width="260" show-overflow-tooltip>
            <template #default="{ row }">{{ row.matchedText || '结构缺失类规则，无固定命中文本' }}</template>
          </el-table-column>
          <el-table-column label="阶段" width="130">
            <template #default="{ row }">{{ checkStageLabel(row.checkStage) }}</template>
          </el-table-column>
          <el-table-column label="动作" width="120">
            <template #default="{ row }">{{ actionLabel(row.action) }}</template>
          </el-table-column>
        </el-table>
        <div class="admin-table-footer">
          <el-pagination background layout="prev, pager, next, total" :current-page="logPage.current" :page-size="logPage.size" :total="logPage.total" @current-change="onLogPageChange" />
        </div>
      </DataState>
    </el-card>

    <el-card v-show="activeTab === 'history'" shadow="never" class="admin-table-card">
      <div class="filter-bar">
        <el-input v-model="historyQuery.projectName" clearable placeholder="搜索项目名称" class="filter-control is-wide" @keyup.enter="searchHistory" />
        <el-input v-model="historyQuery.brandName" clearable placeholder="搜索品牌名称" class="filter-control is-wide" @keyup.enter="searchHistory" />
        <el-input v-model="historyQuery.articleTitle" clearable placeholder="搜索文章标题" class="filter-control is-wide" @keyup.enter="searchHistory" />
        <el-input v-model="historyQuery.topicKeyword" clearable placeholder="搜索选题关键词" class="filter-control is-wide" @keyup.enter="searchHistory" />
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
          <el-table-column label="侧重" width="150">
            <template #default="{ row }">{{ focusLabel(row.focus) }}</template>
          </el-table-column>
          <el-table-column label="结构骨架" min-width="320" show-overflow-tooltip>
            <template #default="{ row }">{{ skeletonLabel(row.structureSkeleton) }}</template>
          </el-table-column>
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
import { ElMessage } from 'element-plus'
import DataState from '@/components/ui/DataState.vue'
import {
  getSpecialIndustryBatches,
  getSpecialIndustryComplianceHitLogs,
  getSpecialIndustryGenerationHistory,
  getSpecialIndustryOverview,
  getSpecialIndustryArticles,
  getSpecialIndustryRuleTypes,
  type MedicalComplianceHitLog,
  type MedicalGenerationHistory,
  type SpecialIndustryBatchTrace,
  type SpecialIndustryOverview,
  type SpecialIndustryRuleType,
} from '@/api/content'
import type { ArticleDraft } from '@/types'
import { useDictStore } from '@/stores/dict'
import { formatDateTime } from '@/utils/format'

type TabName = 'overview' | 'articles' | 'batches' | 'logs' | 'history'

const router = useRouter()
const route = useRoute()
const dictStore = useDictStore()
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
const complianceRuleTypes = ref<SpecialIndustryRuleType[]>([])
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
})
const logQuery = reactive<{
  articleId?: number
  batchId?: number
  taskId?: number
  projectName: string
  brandName: string
  articleTitle: string
  ruleType: string
  action: string
  createdRange: string[]
}>({
  projectName: '',
  brandName: '',
  articleTitle: '',
  ruleType: '',
  action: '',
  createdRange: [],
})
const historyQuery = reactive({
  projectName: '',
  brandName: '',
  articleTitle: '',
  topicKeyword: '',
})
const batchQuery = reactive({
  status: '',
  industryCode: '',
  projectName: '',
  brandName: '',
  topicKeyword: '',
})
const industryOptions = computed(() =>
  dictStore.options('compliance_industry')
    .filter((item) => item.dictKey && item.dictKey !== 'none')
    .map((item) => ({ label: item.dictValue, value: item.dictKey })),
)
const workbenchGuides: Record<TabName, { title: string, description: string, steps: string[] }> = {
  overview: {
    title: '先判断是不是系统性问题',
    description: '概览用于看近期风险是否集中爆发，先看规则 Top 和问题批次，再决定是处理单篇还是调整模板/规则。',
    steps: ['看近 7 日命中和废弃趋势', '查看命中 Top 的中文规则类型', '点击问题批次日志定位集中失败原因'],
  },
  articles: {
    title: '查看特殊行业文章状态',
    description: '特殊行业文章只区分正常通过、通过但有提醒以及确定性合规废弃，不进入内部法务审核。',
    steps: ['按项目、行业和合规状态筛选', '有提醒的文章可进入内容列表查看详情', '只有硬规则命中才会出现在失败命中日志中'],
  },
  batches: {
    title: '判断失败是否来自同一批生成',
    description: '批次追溯用于看一次批量生成的整体结果。如果同一批多篇失败，优先排查模板、品牌资料和选题配置。',
    steps: ['按项目、品牌或主题搜索批次', '查看成功/失败/废弃比例', '进入命中日志确认是否同一规则反复触发'],
  },
  logs: {
    title: '定位具体命中原因',
    description: '命中日志用于解释文章为什么被拦。结构缺失类规则可能没有固定命中文本，应结合规则类型和文章内容判断。',
    steps: ['按项目、品牌、文章标题或规则类型筛选', '查看命中文本和处理动作', '若同类规则频繁出现，优先调整模板或生成约束'],
  },
  history: {
    title: '复盘文章生成策略',
    description: '生成历史用于回看选题角度、内容侧重和结构骨架，判断失败是否与某类选题或结构有关。',
    steps: ['按项目、品牌、文章标题或选题关键词搜索', '查看选题角度和结构骨架', '对高风险选题降低频率或改用更克制模板'],
  },
}
const activeGuide = computed(() => workbenchGuides[activeTab.value])
const logFocusText = computed(() => {
  const parts = []
  if (logQuery.articleId) parts.push(`文章 ${logQuery.articleId}`)
  if (logQuery.batchId) parts.push(`批次 ${logQuery.batchId}`)
  if (logQuery.taskId) parts.push(`任务 ${logQuery.taskId}`)
  return parts.length ? `当前已从告警或操作入口聚焦：${parts.join('、')}` : ''
})
const ruleTypeOptions = computed(() => complianceRuleTypes.value.map((item) => ({
  label: item.displayName,
  value: item.ruleType,
})))

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
      projectName: logQuery.projectName.trim() || undefined,
      brandName: logQuery.brandName.trim() || undefined,
      articleTitle: logQuery.articleTitle.trim() || undefined,
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
      projectName: historyQuery.projectName.trim() || undefined,
      brandName: historyQuery.brandName.trim() || undefined,
      articleTitle: historyQuery.articleTitle.trim() || undefined,
      topicKeyword: historyQuery.topicKeyword.trim() || undefined,
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
      projectName: batchQuery.projectName.trim() || undefined,
      brandName: batchQuery.brandName.trim() || undefined,
      topicKeyword: batchQuery.topicKeyword.trim() || undefined,
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
  logQuery.projectName = ''
  logQuery.brandName = ''
  logQuery.articleTitle = ''
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
  batchQuery.projectName = ''
  batchQuery.brandName = ''
  batchQuery.topicKeyword = ''
  searchBatches()
}

function searchHistory() {
  historyPage.current = 1
  void loadHistory()
}

function resetHistoryQuery() {
  historyQuery.projectName = ''
  historyQuery.brandName = ''
  historyQuery.articleTitle = ''
  historyQuery.topicKeyword = ''
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
  logQuery.projectName = ''
  logQuery.brandName = ''
  logQuery.articleTitle = ''
  logQuery.articleId = articleId
  logQuery.batchId = undefined
  logQuery.taskId = undefined
  logPage.current = 1
  activeTab.value = 'logs'
  void loadLogs()
}

function openBatchLogs(batchId: number) {
  logQuery.projectName = ''
  logQuery.brandName = ''
  logQuery.articleTitle = ''
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
  const articleId = parsePositiveNumber(route.query.articleId)
  const batchId = parsePositiveNumber(route.query.batchId)
  const taskId = parsePositiveNumber(route.query.taskId)

  if (articleId || batchId || taskId) {
    clearLogFocus()
    logQuery.projectName = ''
    logQuery.brandName = ''
    logQuery.articleTitle = ''
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

function complianceLabel(row: ArticleDraft) {
  if (row.complianceStatus === 'passed' && row.hasComplianceWarnings) {
    return `合规通过（${row.complianceWarningCount || 0}项提醒）`
  }
  const map: Record<string, string> = {
    pending: '待校验',
    passed: '合规通过',
    failed: '合规失败',
    discarded_compliance_failed: '已废弃',
  }
  return row.complianceStatus ? map[row.complianceStatus] || row.complianceStatus : '-'
}

function complianceTag(row: ArticleDraft): 'success' | 'warning' | 'danger' | 'info' {
  if (row.complianceStatus === 'passed') return row.hasComplianceWarnings ? 'warning' : 'success'
  if (row.complianceStatus === 'pending') return 'warning'
  if (row.complianceStatus === 'failed' || row.complianceStatus === 'discarded_compliance_failed') return 'danger'
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

function ruleTypeLabel(value?: string | null) {
  return value
    ? complianceRuleTypes.value.find((item) => item.ruleType === value)?.displayName || value
    : '-'
}

function checkStageLabel(value?: string | null) {
  const map: Record<string, string> = {
    pre_generate: '生成前检查',
    post_generate: '生成后检查',
  }
  return value ? map[value] || value : '-'
}

function actionLabel(value?: string | null) {
  const map: Record<string, string> = {
    retry: '重试',
    discard: '废弃',
    block: '阻断',
    warn: '提醒',
    pass: '放行',
  }
  return value ? map[value] || value : '-'
}

function focusLabel(value?: string | null) {
  const map: Record<string, string> = {
    risk: '风险提示',
    principle: '原理科普',
    misconception: '误区澄清',
    rational_decision: '理性决策',
  }
  return value ? map[value] || value : '-'
}

function skeletonLabel(value?: string | null) {
  const map: Record<string, string> = {
    medical_decision: '医疗决策说明',
    audience_focus: '人群适配说明',
    faq: '问答结构',
    concept_distinction: '概念辨析',
    risk_checklist: '风险清单',
  }
  return value ? map[value] || value : '-'
}

function channelLabel(row: ArticleDraft) {
  return [channelGroupLabel(row.channelGroupCode), channelSubLabel(row.channelSubCode)].filter((item) => item && item !== '-').join(' / ') || '-'
}

function channelGroupLabel(value?: string | null) {
  const map: Record<string, string> = {
    forum: '平台论坛',
    industry_site: '行业资讯站',
    agent_site: 'Agent 官网',
    self_media: '自媒体',
    authority_media: '权威媒体',
  }
  return value ? map[value] || value : '-'
}

function channelSubLabel(value?: string | null) {
  const map: Record<string, string> = {
    wechat: '微信公众号',
    wechat_mp: '微信公众号',
    baijiahao: '百家号',
    toutiao: '头条',
    zhihu: '知乎',
    xiaohongshu: '小红书',
    douyin: '抖音图文',
  }
  return value ? map[value] || value : ''
}

onMounted(async () => {
  await dictStore.ensureLoaded()
  const ruleTypesRequest = getSpecialIndustryRuleTypes()
    .then(({ data }) => { complianceRuleTypes.value = data.data || [] })
    .catch(() => { complianceRuleTypes.value = [] })
  await Promise.all([loadOverview(), loadArticles(), loadBatches(), loadLogs(), loadHistory(), ruleTypesRequest])
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

.workbench-guide {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(280px, 1fr);
  gap: 18px;
  margin: 12px 0 16px;
  padding: 14px 16px;
  border: 1px solid #dbeafe;
  border-radius: 12px;
  background: #f8fbff;
}

.workbench-guide strong {
  color: #0f172a;
  font-size: 15px;
  font-weight: 850;
}

.workbench-guide p {
  margin: 6px 0 0;
  color: #475569;
  font-size: 13px;
  line-height: 1.6;
}

.workbench-guide ol {
  display: grid;
  gap: 6px;
  margin: 0;
  padding-left: 18px;
  color: #334155;
  font-size: 13px;
  line-height: 1.5;
}

.focus-alert {
  margin-bottom: 12px;
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
  .workbench-guide,
  .overview-grid {
    grid-template-columns: 1fr;
  }
}
</style>
