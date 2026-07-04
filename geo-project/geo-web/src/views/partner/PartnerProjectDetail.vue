<template>
  <div class="partner-page partner-project-detail-page">
    <div class="partner-page-header">
      <div>
        <div class="partner-page-kicker">项目资产</div>
        <h1 class="partner-page-title">项目详情</h1>
        <div class="partner-page-subtitle">查看项目基础资料、额度分配、展示渠道和拓词组信息。</div>
      </div>
      <div class="partner-page-actions">
        <el-button @click="router.back()">返回</el-button>
        <el-button v-if="canUpdateProject" type="primary" @click="goEdit">编辑项目</el-button>
      </div>
    </div>

    <DataState :loading="loading" :empty="!loading && !project" empty-text="暂无项目详情">
      <template v-if="project">
        <section class="project-hero">
          <div class="project-hero-main">
            <span class="project-avatar">{{ entityInitial(project.projectName) }}</span>
            <div>
              <span class="hero-kicker">{{ project.companyName || '-' }} / {{ project.brandName || '-' }}</span>
              <h2>{{ project.projectName }}</h2>
              <p>{{ project.projectAliases || project.primaryGoal || '未填写项目别名' }}</p>
            </div>
            <span class="partner-status-tag" :class="projectStatusClass(project.status)">
              {{ projectStatusLabel(project.status) }}
            </span>
          </div>
          <div class="project-hero-kpis">
            <div>
              <span>拓词组</span>
              <strong>{{ projectQuestionTaskCount }}</strong>
            </div>
            <div>
              <span>核心问题额度</span>
              <strong>{{ coreQuestionLimit }}</strong>
            </div>
            <div>
              <span>可见渠道</span>
              <strong>{{ visibleChannels.length }}</strong>
            </div>
          </div>
        </section>

        <el-card shadow="never" class="partner-surface detail-card basic-card">
          <template #header>
            <div class="section-heading">
              <div class="section-title">基础信息</div>
              <div class="section-subtitle">项目归属与创建信息</div>
            </div>
          </template>
          <div class="detail-grid">
            <div v-for="item in basicInfoItems" :key="item.label" :class="{ wide: item.wide }">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </div>
          </div>
        </el-card>

        <el-card shadow="never" class="partner-surface detail-card competitor-card">
          <template #header>
            <div class="section-heading">
              <div class="section-title">竞品信息</div>
              <div class="section-subtitle">用于后续诊断识别竞品提及</div>
            </div>
          </template>
          <div v-if="activeCompetitors.length" class="competitor-table">
            <div class="competitor-row head">
              <div>序号</div>
              <div>竞品全名</div>
              <div>简称/别名</div>
            </div>
            <div v-for="(item, index) in activeCompetitors" :key="item.id || item.competitorName" class="competitor-row">
              <div>{{ index + 1 }}</div>
              <div>{{ item.competitorName }}</div>
              <div>{{ item.aliases?.length ? item.aliases.join('、') : '-' }}</div>
            </div>
          </div>
          <div v-else class="empty-panel">暂无竞品信息</div>
        </el-card>

        <el-card shadow="never" class="partner-surface detail-card quota-card">
          <template #header>
            <div class="section-heading">
              <div class="section-title">额度信息</div>
              <div class="section-subtitle">核心问题任务与项目额度核对</div>
            </div>
          </template>
          <div class="detail-grid quota-grid">
            <div>
              <span>核心问题额度</span>
              <strong>{{ coreQuestionLimit }} 个</strong>
            </div>
            <div>
              <span>已选核心问题组</span>
              <strong>{{ projectQuestionTaskCount }} 个</strong>
            </div>
            <div>
              <span>已选核心问题</span>
              <strong>{{ projectCoreQuestionCount }} 条</strong>
            </div>
          </div>
        </el-card>

        <el-card shadow="never" class="partner-surface detail-card channel-card">
          <template #header>
            <div class="section-heading">
              <div class="section-title">展示渠道</div>
              <div class="section-subtitle">仅展示当前项目已启用并可见的渠道额度</div>
            </div>
          </template>
          <div v-if="visibleChannels.length" class="channel-table">
            <div class="channel-row head">
              <div>渠道</div>
              <div>周期</div>
              <div>已分配 / 总量</div>
            </div>
            <div v-for="item in visibleChannels" :key="item.channelCode" class="channel-row">
              <div>{{ item.channelName }}</div>
              <div>{{ channelPeriodText(item.periodType) }}</div>
              <div>{{ item.currentProjectAllocatedCount || 0 }} / {{ item.quotaLimit || 0 }}</div>
            </div>
          </div>
          <div v-else class="empty-panel">暂无已启用展示渠道额度</div>
        </el-card>

        <el-card shadow="never" class="partner-surface detail-card task-card">
          <template #header>
            <div class="section-header">
              <div class="section-heading">
                <div class="section-title">核心问题任务</div>
                <div class="section-subtitle">展示交付员工已维护的核心问题任务。</div>
              </div>
              <el-button v-if="canUpdateProject" size="small" plain @click="goKeywordManage">前往拓词管理</el-button>
            </div>
          </template>
          <div v-if="projectWorkorders.length" class="workorder-table">
            <div class="workorder-row head">
              <div>任务</div>
              <div>状态</div>
              <div>核心问题数</div>
              <div>最近更新</div>
              <div>操作</div>
            </div>
            <div v-for="item in projectWorkorders" :key="item.id" class="workorder-row">
              <div class="workorder-title-cell">
                <strong>{{ item.workorderNo }}</strong>
                <span>{{ item.packageName || project.projectName || '-' }}</span>
              </div>
              <div class="workorder-status-cell">
                <span class="question-pill is-success">{{ workorderStatusLabel(item.status) }}</span>
                <small>{{ partnerReviewStatusLabel(item.partnerReviewStatus) }}</small>
              </div>
              <div class="workorder-count">{{ item.countTotal || 0 }}</div>
              <div>{{ workorderUpdatedAt(item) }}</div>
              <div>
                <el-button link type="primary" @click="openWorkorderQuestions(item)">查看问题</el-button>
              </div>
            </div>
          </div>
          <div v-else class="empty-panel">暂无核心问题任务</div>
        </el-card>

        <el-card v-if="legacyKeywordGroups.length" shadow="never" class="partner-surface detail-card legacy-card">
          <template #header>
            <div class="section-heading">
              <div class="section-title">历史拓词组</div>
              <div class="section-subtitle">兼容旧项目绑定的拓词组数据</div>
            </div>
          </template>
          <el-table :data="legacyKeywordGroups" border empty-text="暂无绑定拓词组">
            <el-table-column prop="name" label="拓词组名称" min-width="220" />
            <el-table-column label="类型" width="140">
              <template #default="{ row }">{{ keywordGroupTypeLabel(row) }}</template>
            </el-table-column>
            <el-table-column label="总问题数" width="120">
              <template #default="{ row }">{{ row.savedKeywordCount || 0 }}</template>
            </el-table-column>
            <el-table-column label="核心问题" width="120">
              <template #default="{ row }">{{ row.savedCoreQuestionCount ?? row.savedKeywordCountA ?? 0 }}</template>
            </el-table-column>
            <el-table-column label="更新时间" width="180">
              <template #default="{ row }">{{ formatDateTimeSeconds(row.updatedAt || row.createdAt) || '-' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openKeywordQuestions(row)">查看问题</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card shadow="never" class="partner-surface detail-card project-supplement-card">
          <template #header>
            <div class="section-heading">
              <div class="section-title">项目资料补充</div>
              <div class="section-subtitle">交付员工录入的写作依据与项目补充口径</div>
            </div>
          </template>
          <div class="detail-grid supplement-grid">
            <div v-for="item in projectSupplementItems" :key="item.label" :class="{ wide: item.wide }">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </div>
          </div>
        </el-card>
      </template>
    </DataState>

    <el-drawer v-model="questionDrawerVisible" size="78%" class="keyword-question-drawer" :with-header="false">
      <div class="question-drawer-layout">
        <header class="question-drawer-hero">
          <div class="question-drawer-title-block">
            <span class="question-drawer-kicker">核心问题明细</span>
            <h2>{{ currentKeywordGroup?.name || '-' }}</h2>
            <p>查看当前拓词组内已入库的核心问题，便于核对后续项目诊断和内容准备口径。</p>
          </div>
          <div class="question-drawer-actions">
            <el-button plain @click="loadKeywordQuestions(questionPage.current)">刷新</el-button>
            <el-button plain @click="questionDrawerVisible = false">关闭</el-button>
          </div>
        </header>

        <section class="question-summary-grid">
          <div>
            <span>核心问题</span>
            <strong>{{ questionPage.total }}</strong>
            <small>当前拓词组已入库</small>
          </div>
          <div>
            <span>当前页</span>
            <strong>{{ questionPage.current }}</strong>
            <small>每页 {{ questionPage.size }} 条</small>
          </div>
          <div>
            <span>问题场景</span>
            <strong>{{ currentPageSceneCount }}</strong>
            <small>当前页覆盖场景</small>
          </div>
          <div>
            <span>更新时间</span>
            <strong>{{ currentKeywordGroupUpdatedAt }}</strong>
            <small>拓词组最近维护</small>
          </div>
        </section>

        <section class="question-list-panel">
          <div class="question-list-head">
            <div>
              <strong>问题列表</strong>
              <span>按序号展示当前页核心问题</span>
            </div>
          </div>

          <el-table
            v-loading="questionLoading"
            :data="questionPage.records"
            class="question-table"
            empty-text="暂无核心问题"
            table-layout="fixed"
          >
            <el-table-column label="序号" width="86" align="center">
              <template #default="{ $index }">
                <span class="question-index">{{ (questionPage.current - 1) * questionPage.size + $index + 1 }}</span>
              </template>
            </el-table-column>
            <el-table-column label="核心问题" min-width="360">
              <template #default="{ row }">
                <div class="question-text-cell">
                  <strong>{{ row.questionText }}</strong>
                  <span v-if="row.articleGenerationNote">{{ row.articleGenerationNote }}</span>
                  <span v-else-if="row.designReason">生成依据：{{ row.designReason }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="场景" width="140">
              <template #default="{ row }">
                <span class="question-pill is-scene">{{ sceneLabel(row.sceneCode) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="优先级" width="120">
              <template #default="{ row }">
                <span class="question-pill" :class="priorityClass(row.priority)">{{ priorityLabel(row.priority) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="更新时间" width="190">
              <template #default="{ row }">
                <span class="question-date">{{ formatDateTimeSeconds(row.updatedAt || row.createdAt) || '-' }}</span>
              </template>
            </el-table-column>
          </el-table>

          <div class="question-scene-legend">
            <span class="legend-title">场景说明</span>
            <span v-for="item in sceneLegendItems" :key="item.code">
              <strong>{{ item.label }}</strong>{{ item.description }}
            </span>
          </div>

          <div class="question-pagination">
            <span>共 {{ questionPage.total }} 条核心问题</span>
            <el-pagination
              background
              layout="prev, pager, next"
              :current-page="questionPage.current"
              :page-size="questionPage.size"
              :total="questionPage.total"
              @current-change="loadKeywordQuestions"
            />
          </div>
        </section>
      </div>
    </el-drawer>

    <el-drawer v-model="workorderQuestionDrawerVisible" size="82%" class="keyword-question-drawer" :with-header="false">
      <div class="question-drawer-layout">
        <header class="question-drawer-hero">
          <div class="question-drawer-title-block">
            <span class="question-drawer-kicker">核心问题明细</span>
            <h2>{{ currentWorkorder?.workorderNo || '-' }}</h2>
            <p>{{ project?.projectName || '-' }} · {{ currentWorkorder?.packageName || '合伙人核心问题任务' }}</p>
          </div>
          <div class="question-drawer-actions">
            <el-button plain @click="loadWorkorderQuestions(workorderQuestionPage.current)">刷新</el-button>
            <el-button plain @click="workorderQuestionDrawerVisible = false">关闭</el-button>
          </div>
        </header>

        <section class="question-summary-grid">
          <div>
            <span>核心问题</span>
            <strong>{{ workorderQuestionPage.total }}</strong>
            <small>当前任务已入库</small>
          </div>
          <div>
            <span>当前页</span>
            <strong>{{ workorderQuestionPage.current }}</strong>
            <small>每页 {{ workorderQuestionPage.size }} 条</small>
          </div>
          <div>
            <span>问题场景</span>
            <strong>{{ workorderCurrentPageSceneCount }}</strong>
            <small>当前页覆盖场景</small>
          </div>
          <div>
            <span>最近更新</span>
            <strong>{{ currentWorkorderUpdatedAt }}</strong>
            <small>核心问题任务</small>
          </div>
        </section>

        <section class="question-list-panel">
          <div class="question-list-head">
            <div>
              <strong>问题列表</strong>
              <span>按序号核对当前任务中的核心问题</span>
            </div>
          </div>

          <el-table
            v-loading="workorderQuestionLoading"
            :data="workorderQuestionPage.records"
            class="question-table"
            empty-text="暂无核心问题"
            table-layout="fixed"
          >
            <el-table-column label="序号" width="86" align="center">
              <template #default="{ $index }">
                <span class="question-index">
                  {{ (workorderQuestionPage.current - 1) * workorderQuestionPage.size + $index + 1 }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="核心问题" min-width="300">
              <template #default="{ row }">
                <div class="question-text-cell">
                  <strong>{{ row.questionText }}</strong>
                  <span v-if="row.designReason">生成依据：{{ row.designReason }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="场景" width="120">
              <template #default="{ row }">
                <span class="question-pill is-scene">{{ sceneLabel(row.sceneCode) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="优先级" width="120">
              <template #default="{ row }">
                <span class="question-pill" :class="priorityClass(row.priority)">{{ priorityLabel(row.priority) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="120">
              <template #default="{ row }">
                <span class="question-pill is-success">{{ questionStatusLabel(row.status) }}</span>
              </template>
            </el-table-column>
          </el-table>

          <div class="question-scene-legend">
            <span class="legend-title">场景说明</span>
            <span v-for="item in sceneLegendItems" :key="item.code">
              <strong>{{ item.label }}</strong>{{ item.description }}
            </span>
          </div>

          <div class="question-pagination">
            <span>共 {{ workorderQuestionPage.total }} 条核心问题</span>
            <el-pagination
              background
              layout="prev, pager, next"
              :current-page="workorderQuestionPage.current"
              :page-size="workorderQuestionPage.size"
              :total="workorderQuestionPage.total"
              @current-change="loadWorkorderQuestions"
            />
          </div>
        </section>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getKeywordGroupQuestions, getProjectDetail } from '@/api/project'
import {
  getGeoProjectWorkorders,
  getGeoQuestions,
  type QuestionPageVO,
  type WorkorderListItem,
} from '@/api/geoQuestion'
import { getProjectMobileDashboardCompetitors, type ProjectCompetitorConfig } from '@/api/mobileDashboard'
import DataState from '@/components/ui/DataState.vue'
import type { KeywordGroup, KeywordGroupQuestion, PageResult, Project, ProjectChannelAllocationItem } from '@/types'
import { useDictStore } from '@/stores/dict'
import { useUserStore } from '@/stores/user'
import { errorMessage } from '@/utils/error'
import { formatDateTimeSeconds } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const dictStore = useDictStore()
const userStore = useUserStore()

const loading = ref(false)
const project = ref<Project | null>(null)
const competitors = ref<ProjectCompetitorConfig[]>([])
const projectWorkorders = ref<WorkorderListItem[]>([])
const questionDrawerVisible = ref(false)
const questionLoading = ref(false)
const currentKeywordGroup = ref<KeywordGroup | null>(null)
const questionPage = reactive<PageResult<KeywordGroupQuestion>>({ records: [], total: 0, current: 1, size: 10 })
const workorderQuestionDrawerVisible = ref(false)
const workorderQuestionLoading = ref(false)
const currentWorkorder = ref<WorkorderListItem | null>(null)
const workorderQuestionPage = reactive<QuestionPageVO>({ records: [], total: 0, current: 1, size: 10, pages: 0 })

const projectId = computed(() => Number(route.params.id))
const canUpdateProject = computed(() => userStore.role === 'partner_staff' && userStore.hasPermission('project.update'))
const coreQuestionLimit = computed(() => project.value?.planCoreQuestionLimit ?? project.value?.planKeywordGroupLimitA ?? 0)
const selectedCoreQuestionCount = computed(() =>
  project.value?.selectedCoreQuestionSavedKeywords
  ?? project.value?.selectedKeywordSavedKeywordsA
  ?? sumCoreQuestionCount(project.value?.selectedKeywordGroups || []),
)
const legacyKeywordGroups = computed(() => project.value?.selectedKeywordGroups || [])
const workorderCoreQuestionCount = computed(() =>
  projectWorkorders.value.reduce((sum, item) => sum + (Number(item.countTotal) || 0), 0),
)
const projectQuestionTaskCount = computed(() => projectWorkorders.value.length || legacyKeywordGroups.value.length || 0)
const projectCoreQuestionCount = computed(() => workorderCoreQuestionCount.value || selectedCoreQuestionCount.value || 0)

const partnerVisibleSelfMediaChannels = new Set([
  'self_media:wechat',
  'self_media:douyin',
  'self_media:toutiao',
  'self_media:zhihu',
  'self_media:baijiahao',
  'self_media:xiaohongshu',
])

const visibleChannels = computed(() => (project.value?.channelAllocations || []).filter(isPartnerVisibleQuotaChannel))
const activeCompetitors = computed(() => competitors.value.filter((item) => item.status !== 'disabled'))
const currentPageSceneCount = computed(() => {
  const scenes = new Set(questionPage.records.map((item) => item.sceneCode || '').filter(Boolean))
  return scenes.size || 0
})
const currentKeywordGroupUpdatedAt = computed(() =>
  formatDateTimeSeconds(currentKeywordGroup.value?.updatedAt || currentKeywordGroup.value?.createdAt) || '-',
)
const workorderCurrentPageSceneCount = computed(() => {
  const scenes = new Set(workorderQuestionPage.records.map((item) => item.sceneCode || '').filter(Boolean))
  return scenes.size || 0
})
const currentWorkorderUpdatedAt = computed(() => (currentWorkorder.value ? workorderUpdatedAt(currentWorkorder.value) : '-'))
const sceneLegendItems = computed(() =>
  ['brand', 'decision', 'deal', 'compare', 'qa', 'function'].map((code) => ({
    code,
    ...sceneMeta(code),
  })),
)

interface DetailDisplayItem {
  label: string
  value: string
  wide?: boolean
}

const basicInfoItems = computed<DetailDisplayItem[]>(() => {
  if (!project.value) return []
  return [
    { label: '客户', value: project.value.companyName || '-' },
    { label: '品牌', value: project.value.brandName || '-' },
    { label: '地区', value: projectRegionText(project.value) },
    { label: '创建时间', value: formatDateTimeSeconds(project.value.createdAt) || '-' },
  ]
})

const projectSupplementItems = computed<DetailDisplayItem[]>(() => {
  if (!project.value) return []
  return [
    { label: '项目别名', value: project.value.projectAliases || '-' },
    { label: '目标区域词', value: joinArray(project.value.targetRegions) },
    { label: '核心关键词', value: project.value.coreKeywords || '-' },
    { label: '目标受众', value: project.value.targetAudience || '-' },
    { label: '主目标', value: project.value.primaryGoal || '-', wide: true },
    { label: '备注', value: project.value.remark || '-', wide: true },
  ]
})

function entityInitial(value?: string | null) {
  const text = String(value || '').trim()
  return text ? Array.from(text)[0] : '项'
}

function projectStatusLabel(status?: string | null) {
  if (!status) return '-'
  return dictStore.label('project_status', status) || status
}

function projectStatusClass(status?: string | null) {
  if (status === 'active' || status === 'setup_ready') return 'is-success'
  if (status === 'approved_pending_setup' || status === 'submitted' || status === 'pending_start') return 'is-warning'
  if (status === 'rejected') return 'is-danger'
  if (status === 'completed' || status === 'archived' || status === 'cancelled' || status === 'expired') return 'is-muted'
  return ''
}

function projectRegionText(item: Project) {
  return [item.provinceName, item.cityName, item.districtName].filter(Boolean).join(' / ') || '-'
}

function parseStringArray(value?: string | string[] | null) {
  if (Array.isArray(value)) return value.map((item) => String(item).trim()).filter(Boolean)
  if (!value) return []
  try {
    const parsed = JSON.parse(value)
    if (Array.isArray(parsed)) return parsed.map((item) => String(item).trim()).filter(Boolean)
  } catch {
    return String(value).split(/[,，、;；\n\r]+/).map((item) => item.trim()).filter(Boolean)
  }
  return []
}

function joinArray(value?: string | string[] | null) {
  return parseStringArray(value).join('、') || '-'
}

function channelPeriodText(periodType?: string | null) {
  if (!periodType || periodType === 'none') return '-'
  const map: Record<string, string> = {
    day: '日',
    week: '周',
    month: '月',
    quarter: '季度',
    year: '年',
  }
  return map[periodType] || periodType
}

function isPartnerVisibleQuotaChannel(item: ProjectChannelAllocationItem) {
  const code = String(item.channelCode || '')
  return item.enabled && (code === 'official_site' || partnerVisibleSelfMediaChannels.has(code))
}

function keywordGroupTypeLabel(row: KeywordGroup) {
  return row.typeLabel || dictStore.label('keyword_group_type', row.type || '') || row.type || '-'
}

function sumCoreQuestionCount(groups: KeywordGroup[]) {
  return groups.reduce((sum, item) => sum + (item.savedCoreQuestionCount ?? item.savedKeywordCountA ?? 0), 0)
}

function goEdit() {
  router.push({ name: 'MyProjects', query: { editProjectId: projectId.value } })
}

function goKeywordManage() {
  router.push({ name: 'PartnerLayeredKeywordGroupManage', query: { projectId: String(projectId.value) } })
}

function sceneLabel(value?: string | null) {
  return sceneMeta(value).label
}

function sceneMeta(value?: string | null) {
  const meta: Record<string, { label: string; description: string }> = {
    // 来源：geo-server/src/main/resources/prompts/geo-question/system-prompt.txt 的 sceneCode 定义。
    brand: { label: '品牌', description: '资质、口碑、门店、服务能力' },
    decision: { label: '决策', description: '方案或机构选型前判断' },
    deal: { label: '成交', description: '找服务商、推荐、报价、预约' },
    compare: { label: '对比', description: '方案或机构横向比较' },
    qa: { label: '问答', description: '流程、售后、质保等服务标准' },
    function: { label: '功能', description: '技术、设备、系统能力' },
  }
  if (!value) return { label: '-', description: '-' }
  return meta[value] || { label: value, description: '未匹配到场景定义' }
}

function priorityLabel(value?: string | null) {
  const labels: Record<string, string> = {
    high: '高',
    medium: '中',
    low: '低',
  }
  return value ? (labels[value] || value) : '-'
}

function priorityClass(value?: string | null) {
  if (value === 'high') return 'is-high'
  if (value === 'low') return 'is-low'
  return 'is-medium'
}

function workorderStatusLabel(value?: string | null) {
  const labels: Record<string, string> = {
    draft: '录入中',
    inputting: '录入中',
    committed: '已入库',
    submitted: '已提交',
    completed: '已完成',
    cancelled: '已取消',
  }
  return value ? (labels[value] || value) : '-'
}

function partnerReviewStatusLabel(value?: string | null) {
  const labels: Record<string, string> = {
    inputting: '录入中',
    pending_owner_review: '待负责人确认',
    returned: '已退回修改',
    submitted_to_hq: '已提交总部',
  }
  return value ? (labels[value] || value) : '录入中'
}

function questionStatusLabel(value?: string | null) {
  const labels: Record<string, string> = {
    draft: '草稿',
    pending: '待确认',
    pending_review: '待确认',
    committed: '已入库',
    submitted: '已提交',
    active: '有效',
    deleted: '已删除',
    disabled: '停用',
  }
  return value ? (labels[value] || value) : '-'
}

function workorderUpdatedAt(row: WorkorderListItem) {
  return formatDateTimeSeconds(row.partnerReviewUpdatedAt || row.updatedAt || row.latestBatchAt || row.createdAt) || '-'
}

async function openKeywordQuestions(row: KeywordGroup) {
  currentKeywordGroup.value = row
  questionDrawerVisible.value = true
  await loadKeywordQuestions(1)
}

async function loadKeywordQuestions(page = questionPage.current) {
  if (!currentKeywordGroup.value) return
  questionLoading.value = true
  try {
    const { data } = await getKeywordGroupQuestions(currentKeywordGroup.value.id, {
      current: page,
      size: questionPage.size,
      tier: 'all',
    })
    Object.assign(questionPage, data.data)
  } catch (err) {
    ElMessage.error(errorMessage(err, '加载拓词组问题失败'))
  } finally {
    questionLoading.value = false
  }
}

async function openWorkorderQuestions(row: WorkorderListItem) {
  currentWorkorder.value = row
  workorderQuestionDrawerVisible.value = true
  await loadWorkorderQuestions(1)
}

async function loadWorkorderQuestions(page = workorderQuestionPage.current) {
  if (!currentWorkorder.value) return
  workorderQuestionLoading.value = true
  try {
    const { data } = await getGeoQuestions(currentWorkorder.value.id, {
      current: page,
      size: workorderQuestionPage.size,
      tier: 'all',
    })
    Object.assign(workorderQuestionPage, data.data)
  } catch (err) {
    ElMessage.error(errorMessage(err, '加载核心问题失败'))
  } finally {
    workorderQuestionLoading.value = false
  }
}

async function load() {
  if (!Number.isFinite(projectId.value) || projectId.value <= 0) {
    project.value = null
    return
  }
  loading.value = true
  try {
    const [projectRes, competitorRes] = await Promise.all([
      getProjectDetail(projectId.value),
      getProjectMobileDashboardCompetitors(projectId.value),
    ])
    project.value = projectRes.data.data
    competitors.value = competitorRes.data.data || []
    try {
      const workorderRes = await getGeoProjectWorkorders(projectId.value)
      projectWorkorders.value = workorderRes.data.data || []
    } catch (err) {
      projectWorkorders.value = []
      ElMessage.warning(errorMessage(err, '核心问题任务暂未加载'))
    }
  } catch (err) {
    project.value = null
    competitors.value = []
    projectWorkorders.value = []
    ElMessage.error(errorMessage(err, '加载项目详情失败'))
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await dictStore.ensureLoaded()
  await load()
})
</script>

<style scoped>
.partner-project-detail-page {
  display: grid;
  gap: 12px;
}

.project-hero,
.detail-card {
  --card-accent: #2563eb;
  --card-accent-soft: #eff6ff;
  --card-accent-border: #cfe2ff;
  overflow: hidden;
  border: 1px solid #d9e7f7;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 10px 26px rgba(15, 23, 42, 0.055);
}

.detail-card :deep(.el-card__header) {
  position: relative;
  padding: 14px 18px;
  border-bottom: 1px solid #e8eef7;
  background:
    linear-gradient(90deg, var(--card-accent-soft) 0%, rgba(255, 255, 255, 0) 42%),
    linear-gradient(180deg, #ffffff 0%, #fbfdff 100%);
}

.detail-card :deep(.el-card__header)::before {
  position: absolute;
  top: 15px;
  bottom: 15px;
  left: 0;
  width: 4px;
  border-radius: 0 999px 999px 0;
  background: var(--card-accent);
  content: '';
}

.detail-card :deep(.el-card__body) {
  padding: 16px 18px;
}

.project-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: stretch;
  gap: 18px;
  padding: 20px;
  border-color: #cfe2ff;
  background:
    radial-gradient(circle at 88% 16%, rgba(251, 191, 36, 0.2) 0, rgba(251, 191, 36, 0) 28%),
    linear-gradient(135deg, rgba(255, 255, 255, 0.96) 0%, rgba(239, 246, 255, 0.94) 52%, rgba(236, 253, 245, 0.95) 100%);
}

.project-hero-main {
  display: grid;
  grid-template-columns: 50px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.project-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 46px;
  height: 46px;
  border-radius: 10px;
  background: linear-gradient(135deg, #2563eb 0%, #0f766e 100%);
  color: #fff;
  font-size: 20px;
  font-weight: 900;
  box-shadow: 0 12px 24px rgba(37, 99, 235, 0.18);
}

.hero-kicker {
  color: #2563eb;
  font-size: 12px;
  font-weight: 900;
}

.project-hero-main h2 {
  margin: 4px 0 0;
  color: #0f172a;
  font-size: 24px;
  line-height: 1.22;
  font-weight: 900;
}

.project-hero-main p {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
}

.project-hero-kpis,
.detail-grid {
  display: grid;
  gap: 10px;
}

.project-hero-kpis {
  grid-template-columns: repeat(3, 126px);
}

.detail-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.project-hero-kpis div,
.detail-grid > div {
  min-width: 0;
  padding: 11px 13px;
  border: 1px solid #e1ebf7;
  border-radius: 8px;
  background: #fbfdff;
}

.project-hero-kpis div {
  position: relative;
  min-height: 78px;
  padding: 12px 14px;
  background: rgba(255, 255, 255, 0.92);
}

.project-hero-kpis div:nth-child(2)::before {
  background: #8b5cf6;
}

.project-hero-kpis div:nth-child(3)::before {
  background: #f59e0b;
}

.project-hero-kpis div::before {
  position: absolute;
  top: 12px;
  right: 12px;
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: #14b8a6;
  content: '';
}

.project-hero-kpis span,
.detail-grid span {
  display: block;
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.project-hero-kpis strong,
.detail-grid strong {
  display: block;
  margin-top: 5px;
  color: #0f172a;
  font-weight: 900;
  word-break: break-word;
}

.project-hero-kpis strong {
  font-size: 21px;
  line-height: 1;
}

.detail-grid .wide {
  grid-column: 1 / -1;
}

.section-heading {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.section-title {
  position: relative;
  padding-left: 12px;
  color: #0f172a;
  font-size: 15px;
  line-height: 1.3;
  font-weight: 900;
}

.section-title::before {
  position: absolute;
  top: 2px;
  bottom: 2px;
  left: 0;
  width: 4px;
  border-radius: 999px;
  background: var(--card-accent);
  content: '';
}

.section-subtitle {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.quota-grid strong {
  font-size: 18px;
}

.basic-card {
  --card-accent: #2563eb;
  --card-accent-soft: #eff6ff;
  --card-accent-border: #bfdbfe;
}

.competitor-card {
  --card-accent: #f59e0b;
  --card-accent-soft: #fffbeb;
  --card-accent-border: #fde68a;
  border-color: #f8df9b;
}

.quota-card,
.task-card,
.legacy-card {
  --card-accent: #7c3aed;
  --card-accent-soft: #f5f3ff;
  --card-accent-border: #ddd6fe;
  border-color: #ddd6fe;
}

.channel-card {
  --card-accent: #0f766e;
  --card-accent-soft: #ecfdf5;
  --card-accent-border: #99f6e4;
  border-color: #b8eee5;
}

.project-supplement-card {
  --card-accent: #16a34a;
  --card-accent-soft: #f0fdf4;
  --card-accent-border: #bbf7d0;
  border-color: #bbf7d0;
}

.project-supplement-card :deep(.el-card__body) {
  background: linear-gradient(180deg, #ffffff 0%, #f7fef9 100%);
}

.supplement-grid > div {
  background: #fff;
}

.channel-table {
  overflow: hidden;
  border: 1px solid var(--card-accent-border);
  border-radius: 10px;
  background: #fff;
}

.channel-row,
.competitor-row {
  display: grid;
  gap: 12px;
  align-items: center;
  min-height: 44px;
  padding: 10px 14px;
  border-bottom: 1px solid #edf2f7;
  color: #0f172a;
  font-weight: 800;
}

.channel-row {
  grid-template-columns: minmax(160px, 1fr) 96px 140px;
}

.competitor-table {
  overflow: hidden;
  border: 1px solid var(--card-accent-border);
  border-radius: 10px;
  background: #fff;
}

.competitor-row {
  grid-template-columns: 64px minmax(180px, 0.8fr) minmax(220px, 1.2fr);
}

.channel-row:last-child,
.competitor-row:last-child {
  border-bottom: 0;
}

.channel-row.head,
.competitor-row.head {
  background: var(--card-accent-soft);
  color: #334155;
  font-size: 13px;
  font-weight: 900;
}

.channel-row:not(.head):hover,
.competitor-row:not(.head):hover,
.workorder-row:not(.head):hover {
  background: #fbfdff;
}

.workorder-table {
  overflow: hidden;
  border: 1px solid var(--card-accent-border);
  border-radius: 10px;
  background: #fff;
}

.workorder-row {
  display: grid;
  grid-template-columns: minmax(180px, 1.1fr) 150px 112px 170px 150px;
  gap: 12px;
  align-items: center;
  min-height: 56px;
  padding: 10px 14px;
  border-bottom: 1px solid #edf2f7;
  color: #0f172a;
  font-weight: 800;
}

.workorder-row:last-child {
  border-bottom: 0;
}

.workorder-row.head {
  min-height: 40px;
  background: var(--card-accent-soft);
  color: #334155;
  font-size: 13px;
  font-weight: 900;
}

.workorder-title-cell,
.workorder-status-cell {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.workorder-title-cell strong {
  overflow: hidden;
  color: #0f172a;
  font-size: 14px;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workorder-title-cell span,
.workorder-status-cell small {
  overflow: hidden;
  color: #64748b;
  font-size: 12px;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workorder-status-cell {
  justify-items: start;
}

.workorder-count {
  font-size: 18px;
  font-weight: 900;
}

:deep(.keyword-question-drawer .el-drawer__body) {
  padding: 0;
  background: #f6f8fb;
}

.question-drawer-layout {
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr);
  gap: 14px;
  min-height: 100%;
  padding: 24px;
}

.question-drawer-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  padding: 20px;
  border: 1px solid #cfe2ff;
  border-radius: 14px;
  background: linear-gradient(135deg, #f8fbff 0%, #eff6ff 62%, #ecfdf5 100%);
  box-shadow: 0 14px 34px rgba(15, 23, 42, 0.07);
}

.question-drawer-title-block {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.question-drawer-kicker {
  color: #2563eb;
  font-size: 12px;
  font-weight: 900;
}

.question-drawer-title-block h2 {
  margin: 0;
  color: #0f172a;
  font-size: 24px;
  line-height: 1.25;
  font-weight: 900;
}

.question-drawer-title-block p {
  margin: 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
  font-weight: 700;
}

.question-drawer-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.question-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.question-summary-grid > div {
  display: grid;
  gap: 5px;
  min-height: 88px;
  padding: 14px;
  border: 1px solid #dbeafe;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.04);
}

.question-summary-grid span,
.question-summary-grid small {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.question-summary-grid strong {
  overflow: hidden;
  color: #0f172a;
  font-size: 22px;
  line-height: 1.2;
  font-weight: 900;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.question-list-panel {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  min-height: 0;
  overflow: hidden;
  border: 1px solid #dbeafe;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 16px 38px rgba(15, 23, 42, 0.07);
}

.question-list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 18px;
  border-bottom: 1px solid #e5edf8;
  background: #fbfdff;
}

.question-list-head div {
  display: grid;
  gap: 3px;
}

.question-list-head strong {
  color: #0f172a;
  font-size: 16px;
  font-weight: 900;
}

.question-list-head span {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.question-table {
  min-height: 420px;
}

.question-table :deep(.el-table__header th) {
  height: 46px;
  background: #f8fbff;
  color: #334155;
  font-weight: 900;
}

.question-table :deep(.el-table__row td) {
  height: 62px;
}

.question-table :deep(.el-table__row:hover > td) {
  background: #f8fbff;
}

.question-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 30px;
  height: 30px;
  border-radius: 999px;
  background: #eff6ff;
  color: #2563eb;
  font-weight: 900;
}

.question-text-cell {
  display: grid;
  gap: 5px;
  min-width: 0;
}

.question-text-cell strong {
  overflow: hidden;
  color: #0f172a;
  font-size: 14px;
  line-height: 1.45;
  font-weight: 900;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.question-text-cell span {
  overflow: hidden;
  color: #64748b;
  font-size: 12px;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.question-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 52px;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 900;
}

.question-pill.is-scene {
  color: #075985;
  background: #e0f2fe;
}

.question-pill.is-high {
  color: #b45309;
  background: #fef3c7;
}

.question-pill.is-medium {
  color: #047857;
  background: #d1fae5;
}

.question-pill.is-low {
  color: #475569;
  background: #e2e8f0;
}

.question-pill.is-success {
  color: #047857;
  background: #d1fae5;
}

.question-date {
  color: #475569;
  font-size: 13px;
  font-weight: 700;
}

.question-scene-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  padding: 10px 16px;
  border-top: 1px solid #e5edf8;
  background: #f8fbff;
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
  font-weight: 700;
}

.question-scene-legend .legend-title {
  color: #0f172a;
  font-weight: 900;
}

.question-scene-legend strong {
  margin-right: 4px;
  color: #2563eb;
  font-weight: 900;
}

.question-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  border-top: 1px solid #e5edf8;
  background: #fbfdff;
  color: #64748b;
  font-size: 13px;
  font-weight: 800;
}

.empty-panel {
  padding: 28px;
  border: 1px dashed #bfdbfe;
  border-radius: 12px;
  background: #f8fbff;
  color: #64748b;
  text-align: center;
  font-weight: 800;
}

@media (max-width: 768px) {
  .project-hero,
  .project-hero-main,
  .project-hero-kpis,
  .detail-grid,
  .channel-row,
  .competitor-row,
  .workorder-row {
    grid-template-columns: 1fr;
  }
  .section-header,
  .question-pagination {
    align-items: flex-start;
    flex-direction: column;
  }

  .question-drawer-layout {
    padding: 14px;
  }

  .question-drawer-hero {
    flex-direction: column;
  }

  .question-drawer-actions {
    flex-wrap: wrap;
    justify-content: flex-start;
    width: 100%;
  }

  .question-summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
