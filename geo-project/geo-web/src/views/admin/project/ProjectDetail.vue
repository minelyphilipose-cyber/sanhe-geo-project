<template>
  <div class="admin-page">
    <el-page-header content="项目详情" @back="$router.back()" />

    <section v-if="project" class="admin-object-hero">
      <div class="admin-object-hero-main">
        <div>
          <h1 class="admin-object-title">{{ project.projectName }}</h1>
          <div class="admin-object-meta">
            {{ project.companyName || '-' }} · {{ project.brandName || '-' }}
          </div>
        </div>
        <span class="admin-status-tag" :class="projectStatusClass(project.status)">
          {{ projectStatusLabel(project.status) }}
        </span>
      </div>
      <div class="admin-object-kpis project-hero-kpis">
        <div class="admin-object-kpi project-hero-kpi project-hero-kpi--keyword">
          <span>拓词组</span>
          <strong>{{ project.selectedKeywordGroups?.length || 0 }}</strong>
        </div>
        <div class="admin-object-kpi project-hero-kpi project-hero-kpi--quota">
          <span>问题额度</span>
          <strong>{{ keywordAllocationSummary }}</strong>
        </div>
        <div class="admin-object-kpi project-hero-kpi project-hero-kpi--channel">
          <span>渠道额度</span>
          <strong>{{ project.channelAllocations?.length || 0 }}</strong>
        </div>
      </div>
    </section>

    <el-card v-loading="loading" class="admin-rich-card">
      <template #header>
        <div class="flex items-center justify-between">
          <span>基础信息</span>
          <div class="space-x-2">
            <el-button size="small" @click="goReports">实时看板</el-button>
            <el-button v-if="project?.status === 'active'" size="small" type="primary" plain @click="goBaselineReport">基线检测报告</el-button>
            <el-tag>{{ projectStatusLabel(project?.status) }}</el-tag>
          </div>
        </div>
      </template>
      <div class="admin-info-grid">
        <div
          v-for="item in projectBasicInfoItems"
          :key="item.label"
          class="admin-info-item"
          :class="{ 'is-wide': item.wide }"
        >
          <span class="admin-info-label">{{ item.label }}</span>
          <strong class="admin-info-value">{{ item.value }}</strong>
        </div>
      </div>
    </el-card>

    <el-card v-if="project" class="admin-rich-card">
      <template #header>
        <div class="section-header">
          <span>客户需求</span>
          <el-button v-if="canUpdateProject" size="small" type="primary" plain @click="openRequirementEdit">维护需求</el-button>
        </div>
      </template>
      <div v-if="project.customerRequirements?.length" class="requirement-view-list">
        <div v-for="(item, index) in project.customerRequirements" :key="`${index}-${item}`" class="requirement-view-item">
          <div class="requirement-view-index">{{ index + 1 }}</div>
          <div class="requirement-view-text">{{ item }}</div>
        </div>
      </div>
      <el-empty v-else description="暂无客户需求" :image-size="72" />
    </el-card>

    <el-card v-if="project" class="admin-rich-card">
      <template #header>
        <div class="flex items-center justify-between">
          <span>分发渠道额度</span>
          <el-button v-if="canUpdateProject" size="small" type="primary" plain @click="openChannelAllocationEdit">调整额度</el-button>
        </div>
      </template>
      <el-alert
        type="info"
        :closable="false"
        class="mb-3"
        title="仅官网、行业资讯站参与文章生成调度；额度为 0 时不会生成文章。"
      />
      <el-table :data="project.channelAllocations || []" border empty-text="暂无渠道额度">
        <el-table-column prop="channelName" label="渠道" min-width="140">
          <template #default="{ row }">
            <div class="channel-name">
              <span>{{ row.channelName || row.channelCode }}</span>
              <el-tag v-if="isArticleGenerationChannel(row.channelCode)" size="small" type="success">生成文章渠道</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="周期" width="100">
          <template #default="{ row }">{{ periodLabel(row.periodType) }}</template>
        </el-table-column>
        <el-table-column label="套餐总额" width="110">
          <template #default="{ row }">{{ row.quotaLimit || 0 }}</template>
        </el-table-column>
        <el-table-column label="已激活占用" width="120">
          <template #default="{ row }">{{ row.activeAllocatedCount || 0 }}</template>
        </el-table-column>
        <el-table-column label="当前项目" width="120">
          <template #default="{ row }">{{ row.currentProjectAllocatedCount || 0 }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '可用' : '未启用' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card v-if="project" class="admin-rich-card">
      <template #header>
        <div class="keyword-group-header">
          <span>绑定拓词组</span>
          <div class="keyword-group-actions">
            <el-button v-if="canCreateKeywordGroup" type="primary" plain size="small" @click="goCreateKeywordGroup">创建拓词组</el-button>
            <el-upload
              v-if="canImportKeywordGroup"
              class="keyword-import-upload"
              :auto-upload="false"
              :show-file-list="false"
              accept=".xlsx"
              :on-change="handleKeywordImport"
            >
              <el-button type="primary" size="small" :loading="importing">导入拓词组</el-button>
            </el-upload>
          </div>
        </div>
      </template>
      <el-alert
        v-if="canImportKeywordGroup"
        type="warning"
        :closable="false"
        class="mb-3"
        title="当前项目暂无拓词组，请添加或导入拓词组后再启动项目。导入 A/B/C 数量必须与项目额度一致。"
      />
      <el-table :data="project.selectedKeywordGroups || []" border empty-text="暂无绑定拓词组">
        <el-table-column prop="name" label="拓词组名称" min-width="220" />
        <el-table-column prop="typeLabel" label="类型" min-width="120">
          <template #default="{ row }">{{ keywordGroupTypeLabel(row) }}</template>
        </el-table-column>
        <el-table-column prop="savedKeywordCount" label="总问题数" width="110" />
        <el-table-column label="A/B/C" width="160">
          <template #default="{ row }">A {{ row.savedKeywordCountA || 0 }} / B {{ row.savedKeywordCountB || 0 }} / C {{ row.savedKeywordCountC || 0 }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button link type="primary" @click="openKeywordQuestions(row)">查看编辑</el-button>
            <el-button v-if="canDeleteKeywordGroup" link type="danger" @click="removeKeywordGroup(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card v-if="project" class="admin-rich-card">
      <template #header><span>内容策略配置</span></template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="核心关键词">{{ project.coreKeywords || '-' }}</el-descriptions-item>
        <el-descriptions-item label="目标区域词">{{ joinArray(project.targetRegions) }}</el-descriptions-item>
        <el-descriptions-item label="目标受众">{{ project.targetAudience || '-' }}</el-descriptions-item>
        <el-descriptions-item label="内容调性">{{ project.contentTone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="优先写作角度">{{ joinArray(project.preferredAngles) }}</el-descriptions-item>
        <el-descriptions-item label="项目定制表述" :span="2">{{ project.customStatement || '-' }}</el-descriptions-item>
        <el-descriptions-item label="补充禁用词" :span="2">{{ joinArray(project.extraForbiddenPhrases) }}</el-descriptions-item>
        <el-descriptions-item label="内容备注" :span="2">{{ project.contentNote || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card v-if="showActivationGuide" class="admin-rich-card">
      <template #header><span>项目启动</span></template>
      <el-form label-width="120px" style="max-width: 540px">
        <el-form-item label="启动前确认">
          <el-checkbox v-model="activationConfirmed">我已阅读并确认项目基础信息</el-checkbox>
        </el-form-item>
        <el-form-item v-if="canActivateProject">
          <el-button type="primary" :loading="saving" :disabled="!activationConfirmed" @click="startProject">启动项目</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-drawer v-model="questionDrawerVisible" size="88%" title="拓词组问题明细">
      <div class="space-y-3">
        <div class="flex items-center justify-between">
          <el-radio-group v-model="questionTier" @change="loadKeywordQuestions(1)">
            <el-radio-button label="all">全部</el-radio-button>
            <el-radio-button label="A">A 类</el-radio-button>
            <el-radio-button label="B">B 类</el-radio-button>
            <el-radio-button label="C">C 类</el-radio-button>
          </el-radio-group>
          <span class="text-sm text-gray-500">编辑不会改变 A/B/C 层级数量</span>
        </div>
        <el-table v-loading="questionLoading" :data="questionPage.records" border>
          <el-table-column prop="questionCode" label="ID" width="100" />
          <el-table-column prop="questionText" label="问题文本" min-width="260" />
          <el-table-column label="场景" width="110">
            <template #default="{ row }">{{ sceneLabel(row.sceneCode) }}</template>
          </el-table-column>
          <el-table-column prop="questionTier" label="分级" width="80" />
          <el-table-column label="优先级" width="90">
            <template #default="{ row }">{{ priorityLabel(row.priority) }}</template>
          </el-table-column>
          <el-table-column prop="scoreRelevance" label="商业价值" width="95" />
          <el-table-column prop="scoreIntent" label="成交距离" width="95" />
          <el-table-column prop="scoreCompetition" label="品牌绑定" width="95" />
          <el-table-column prop="scoreConversion" label="地域行业" width="95" />
          <el-table-column prop="scoreCoverage" label="一期可达" width="95" />
          <el-table-column prop="totalScore" label="总分" width="80" />
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" :disabled="!canPrepareProject" @click="openQuestionEdit(row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="questionPage.current"
          v-model:page-size="questionPage.size"
          layout="total, sizes, prev, pager, next"
          :total="questionPage.total"
          :page-sizes="[20, 50, 100]"
          @current-change="loadKeywordQuestions"
          @size-change="() => loadKeywordQuestions(1)"
        />
      </div>
    </el-drawer>

    <el-dialog v-model="questionEditVisible" title="编辑问题" width="820px" class="admin-editor-dialog">
      <el-form class="admin-dialog-form" label-width="130px">
        <el-form-item class="is-full" label="问题文本" required>
          <el-input v-model="questionForm.questionText" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="场景">
          <el-select v-model="questionForm.sceneCode" style="width: 220px">
            <el-option label="品牌场景" value="brand" />
            <el-option label="决策场景" value="decision" />
            <el-option label="成交场景" value="deal" />
            <el-option label="对比场景" value="compare" />
            <el-option label="问答场景" value="qa" />
            <el-option label="功能场景" value="function" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="questionForm.priority" style="width: 220px">
            <el-option label="高" value="high" />
            <el-option label="中" value="medium" />
            <el-option label="低" value="low" />
          </el-select>
        </el-form-item>
        <el-form-item label="商业价值评分">
          <el-input-number v-model="questionForm.scoreRelevance" class="score-input" :min="1" :max="5" :step="1" controls-position="right" />
        </el-form-item>
        <el-form-item label="成交距离评分">
          <el-input-number v-model="questionForm.scoreIntent" class="score-input" :min="1" :max="5" :step="1" controls-position="right" />
        </el-form-item>
        <el-form-item label="品牌绑定评分">
          <el-input-number v-model="questionForm.scoreCompetition" class="score-input" :min="1" :max="5" :step="1" controls-position="right" />
        </el-form-item>
        <el-form-item label="地域行业评分">
          <el-input-number v-model="questionForm.scoreConversion" class="score-input" :min="1" :max="5" :step="1" controls-position="right" />
        </el-form-item>
        <el-form-item label="一期可达评分">
          <el-input-number v-model="questionForm.scoreCoverage" class="score-input" :min="1" :max="5" :step="1" controls-position="right" />
        </el-form-item>
        <el-form-item class="is-full" label="生成文章备注">
          <el-input
            v-model="questionForm.articleGenerationNote"
            type="textarea"
            :rows="3"
            maxlength="1000"
            show-word-limit
            placeholder="用于后续大模型根据该问题生成文章时补充 prompt"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="questionEditVisible = false">取消</el-button>
        <el-button type="primary" :loading="questionSaving" @click="saveQuestion">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="channelEditVisible" title="调整分发渠道额度" width="720px" class="admin-editor-dialog">
      <div class="channel-edit-note">官网、行业资讯站额度会参与文章生成调度；可填范围为客户套餐总额度减去当前已激活项目占用，保存时后端会再次校验。</div>
      <div v-loading="channelQuotaLoading" class="channel-allocation-panel">
        <div v-for="item in channelQuotaItems" :key="item.channelCode" class="channel-row">
          <div class="channel-meta">
            <div class="channel-name">
              <span>{{ item.channelName }}</span>
              <el-tag v-if="isArticleGenerationChannel(item.channelCode)" size="small" type="success">生成文章渠道</el-tag>
            </div>
            <small>{{ channelQuotaText(item) }}</small>
          </div>
          <el-input-number
            v-model="channelAllocationForm[item.channelCode]"
            :min="0"
            :max="channelInputMax(item)"
            :disabled="!item.enabled"
            controls-position="right"
          />
        </div>
      </div>
      <template #footer>
        <el-button @click="channelEditVisible = false">取消</el-button>
        <el-button type="primary" :loading="channelSaving" @click="saveChannelAllocations">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="requirementEditVisible" title="维护客户需求" width="760px" class="admin-editor-dialog">
      <div class="requirement-editor">
        <div v-for="(_, index) in requirementForm.items" :key="index" class="requirement-edit-item">
          <div class="requirement-row-head">
            <span>需求 {{ index + 1 }}</span>
            <el-button link type="danger" :disabled="requirementForm.items.length <= 1" @click="removeRequirementItem(index)">删除</el-button>
          </div>
          <el-input
            v-model="requirementForm.items[index]"
            type="textarea"
            :rows="3"
            maxlength="100"
            show-word-limit
            resize="none"
            placeholder="请输入 10-100 字客户需求"
          />
        </div>
        <el-button class="requirement-add" plain @click="addRequirementItem">新增需求</el-button>
      </div>
      <template #footer>
        <el-button @click="requirementEditVisible = false">取消</el-button>
        <el-button type="primary" :loading="requirementSaving" @click="saveRequirements">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadFile } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { useDictStore } from '@/stores/dict'
import {
  deleteProject,
  deleteKeywordGroup,
  getProjectChannelAllocationQuota,
  getProjectDetail,
  getKeywordGroupQuestions,
  importProjectKeywordGroup,
  updateKeywordGroupQuestion,
  updateProject,
  updateProjectStatus,
} from '@/api/project'
import type { KeywordGroup, KeywordGroupQuestion, PageResult, Project, ProjectChannelAllocationItem } from '@/types'
import { regionDisplayFromPayload } from '@/constants/region'
import { nullableText } from '@/utils/form'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const dictStore = useDictStore()
const PROJECT_STATUS_LABELS: Record<string, string> = {
  pending_start: '待启动',
  active: '已启动',
  paused: '已暂停',
  expired: '已过期',
}
const KEYWORD_GROUP_TYPE_LABELS: Record<string, string> = {
  brand: '品牌词',
  decision: '决策词',
  transaction: '成交词',
  comparison: '对比词',
  qa: '问答词',
  function: '功能词',
  imported: '导入问题池',
  search: '搜索词(历史)',
  location: '地域词(历史)',
  industry: '行业词(历史)',
  competitor: '竞品词(历史)',
}
const canActivateProject = computed(() => userStore.hasPermission('project.start'))
const canUpdateProject = computed(() => userStore.hasPermission('project.update'))
const canPrepareProject = computed(() => project.value?.status === 'pending_start' || project.value?.status === 'paused')
const canCreateKeywordGroup = computed(() => !!project.value && userStore.hasPermission('keyword_group.write'))
const canDeleteKeywordGroup = computed(() => !!project.value && canPrepareProject.value && userStore.hasPermission('keyword_group.write'))
const projectId = Number(route.params.id)
const hasValidId = Number.isFinite(projectId) && projectId > 0

const loading = ref(false)
const saving = ref(false)
const importing = ref(false)
const project = ref<Project | null>(null)
const questionDrawerVisible = ref(false)
const questionEditVisible = ref(false)
const channelEditVisible = ref(false)
const requirementEditVisible = ref(false)
const questionLoading = ref(false)
const questionSaving = ref(false)
const channelQuotaLoading = ref(false)
const channelSaving = ref(false)
const requirementSaving = ref(false)
const currentKeywordGroup = ref<KeywordGroup | null>(null)
const currentQuestionId = ref<number | null>(null)
const channelQuotaItems = ref<ProjectChannelAllocationItem[]>([])
const allocationVersion = ref<number | null>(null)
const channelAllocationForm = reactive<Record<string, number>>({})
const questionTier = ref('all')
const questionPage = reactive<PageResult<KeywordGroupQuestion>>({ records: [], total: 0, current: 1, size: 20 })
const questionForm = reactive({
  questionText: '',
  sceneCode: 'brand',
  priority: 'medium',
  scoreRelevance: 4,
  scoreIntent: 4,
  scoreCompetition: 4,
  scoreConversion: 4,
  scoreCoverage: 4,
  articleGenerationNote: '',
})
const requirementForm = reactive({
  items: [''],
})

const activationConfirmed = ref(false)
const showActivationGuide = computed(() => route.query.activate === '1' && canPrepareProject.value)
const canImportKeywordGroup = computed(() => {
  const current = project.value
  return !!current && canPrepareProject.value && (current.selectedKeywordGroupCount || 0) === 0
})
const keywordSummary = computed(() => {
  const current = project.value
  if (!current) return '-'
  return `已选 ${current.selectedKeywordGroupCount || 0} 个，已入库 ${current.selectedKeywordSavedKeywords || 0} 条关键词（A ${current.selectedKeywordSavedKeywordsA || 0} / B ${current.selectedKeywordSavedKeywordsB || 0} / C ${current.selectedKeywordSavedKeywordsC || 0}）`
})
const keywordAllocationSummary = computed(() => {
  const current = project.value
  if (!current) return '-'
  return `总 ${current.planKeywordGroupLimit || 0}，A ${current.planKeywordGroupLimitA || 0} / B ${current.planKeywordGroupLimitB || 0} / C ${current.planKeywordGroupLimitC || 0}`
})
const channelAllocationSummary = computed(() => {
  const rows = project.value?.channelAllocations || []
  const targets = rows.filter((row) => isArticleGenerationChannel(row.channelCode))
  if (!targets.length) return '-'
  return targets.map((row) => `${row.channelName || row.channelCode} ${row.currentProjectAllocatedCount || 0}`).join(' / ')
})
const projectBasicInfoItems = computed(() => {
  const current = project.value
  return [
    { label: '项目名称', value: current?.projectName || '-' },
    { label: '项目别名', value: current?.projectAliases || '-' },
    { label: '客户名称', value: current?.companyName || '-' },
    { label: '品牌名称', value: current?.brandName || '-' },
    { label: '拓词组', value: keywordSummary.value },
    { label: '问题额度', value: keywordAllocationSummary.value },
    { label: '分发渠道额度', value: channelAllocationSummary.value },
    { label: '所在地区', value: regionText(current) },
    { label: '启动日期', value: current?.activatedAt || '-' },
    { label: '有效期至', value: current?.endDate || '-' },
    { label: '主目标', value: current?.primaryGoal || '-', wide: true },
  ]
})

function regionText(p?: Project | null) {
  if (!p) return '-'
  return regionDisplayFromPayload(p) || '-'
}

function projectStatusLabel(status?: string | null) {
  if (!status) return '-'
  return PROJECT_STATUS_LABELS[status] || dictStore.label('project_status', status) || status
}

function projectStatusClass(status?: string | null) {
  if (status === 'active') return 'is-success'
  if (status === 'paused' || status === 'pending_start') return 'is-warning'
  if (status === 'expired') return 'is-danger'
  return 'is-muted'
}

function keywordExpectedCounts(current: Project) {
  return {
    a: current.planKeywordGroupLimitA ?? current.planKeywordGroupLimit ?? 0,
    b: current.planKeywordGroupLimitB ?? 0,
    c: current.planKeywordGroupLimitC ?? 0,
  }
}

function keywordActualCounts(current: Project) {
  return {
    a: current.selectedKeywordSavedKeywordsA || 0,
    b: current.selectedKeywordSavedKeywordsB || 0,
    c: current.selectedKeywordSavedKeywordsC || 0,
  }
}

function validateKeywordGroupCountsBeforeStart(current: Project) {
  const expected = keywordExpectedCounts(current)
  const actual = keywordActualCounts(current)
  if ((current.selectedKeywordGroupCount || 0) <= 0) {
    ElMessage.warning('项目启动前必须至少绑定一个拓词组')
    return false
  }
  if (actual.a !== expected.a || actual.b !== expected.b || actual.c !== expected.c) {
    ElMessage.warning(`拓词组问题数量需与项目额度一致：额度 A/B/C=${expected.a}/${expected.b}/${expected.c}，当前 A/B/C=${actual.a}/${actual.b}/${actual.c}`)
    return false
  }
  return true
}

function goCreateKeywordGroup() {
  router.push({ name: 'LayeredKeywordGroupManage', query: { projectId: String(projectId) } })
}

function joinArray(value?: string | string[] | null) {
  if (Array.isArray(value)) {
    return value.length ? value.join('、') : '-'
  }
  if (!value) {
    return '-'
  }
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) && parsed.length ? parsed.join('、') : '-'
  } catch {
    return value
  }
}

function isArticleGenerationChannel(channelCode?: string | null) {
  return channelCode === 'official_site' || channelCode === 'industry_site'
}

function keywordGroupTypeLabel(row: KeywordGroup) {
  if (row.typeLabel) return row.typeLabel
  if (!row.type) return '-'
  return KEYWORD_GROUP_TYPE_LABELS[row.type] || row.type
}

function periodLabel(value?: string | null) {
  const labels: Record<string, string> = {
    day: '日',
    week: '周',
    month: '月',
    total: '总量',
    none: '-',
  }
  return value ? (labels[value] || value) : '-'
}

function channelInputMax(item: ProjectChannelAllocationItem) {
  return Math.max(item.inputMax ?? item.remainingCount ?? 0, 0)
}

function channelQuotaText(item: ProjectChannelAllocationItem) {
  if (!item.enabled) {
    return '套餐未启用'
  }
  return `可分配 ${channelInputMax(item)} / 套餐总额 ${item.quotaLimit || 0}（${periodLabel(item.periodType)}）`
}

function resetChannelAllocationForm() {
  for (const key of Object.keys(channelAllocationForm)) {
    delete channelAllocationForm[key]
  }
}

async function openChannelAllocationEdit() {
  const current = project.value
  if (!current?.companyId) {
    ElMessage.warning('项目信息缺少客户，无法调整渠道额度')
    return
  }
  channelEditVisible.value = true
  channelQuotaLoading.value = true
  resetChannelAllocationForm()
  try {
    const { data } = await getProjectChannelAllocationQuota({
      companyId: current.companyId,
      excludeProjectId: current.id,
    })
    channelQuotaItems.value = data.data.items || []
    allocationVersion.value = data.data.allocationVersion
    for (const item of channelQuotaItems.value) {
      channelAllocationForm[item.channelCode] = item.currentProjectAllocatedCount || 0
    }
  } finally {
    channelQuotaLoading.value = false
  }
}

function projectUpdatePayload(current: Project) {
  const allocationRows = channelQuotaItems.value.length
    ? channelQuotaItems.value.map((item) => ({
        channelCode: item.channelCode,
        allocatedCount: channelAllocationForm[item.channelCode] || 0,
      }))
    : (current.channelAllocations || []).map((item) => ({
        channelCode: item.channelCode,
        allocatedCount: item.currentProjectAllocatedCount || 0,
      }))
  return {
    provinceCode: current.provinceCode,
    provinceName: current.provinceName,
    cityCode: current.cityCode,
    cityName: current.cityName,
    districtCode: current.districtCode,
    districtName: current.districtName,
    projectName: current.projectName,
    projectAliases: nullableText(current.projectAliases),
    companyId: current.companyId,
    brandId: current.brandId,
    keywordGroupIds: current.selectedKeywordGroupIds || [],
    keywordGroupLimitA: current.planKeywordGroupLimitA ?? current.planKeywordGroupLimit ?? 0,
    keywordGroupLimitB: current.planKeywordGroupLimitB ?? 0,
    keywordGroupLimitC: current.planKeywordGroupLimitC ?? 0,
    allocationVersion: allocationVersion.value ?? current.allocationVersion,
    channelAllocations: allocationRows,
    deliveryMode: current.deliveryMode || 'managed',
    primaryGoal: nullableText(current.primaryGoal),
    customerRequirements: current.customerRequirements || [],
    targetRegions: parseStringArray(current.targetRegions),
    coreKeywords: current.coreKeywords,
    targetAudience: nullableText(current.targetAudience),
    customStatement: nullableText(current.customStatement),
    contentTone: nullableText(current.contentTone),
    preferredAngles: parseStringArray(current.preferredAngles),
    extraForbiddenPhrases: parseStringArray(current.extraForbiddenPhrases),
    contentNote: nullableText(current.contentNote),
    remark: nullableText(current.remark),
  }
}

function normalizeRequirementInputs(requirements?: string[] | null) {
  const normalized = (requirements || [])
    .map((item) => String(item || '').trim())
    .filter(Boolean)
  return normalized.length ? normalized : ['']
}

function openRequirementEdit() {
  requirementForm.items = normalizeRequirementInputs(project.value?.customerRequirements)
  requirementEditVisible.value = true
}

function addRequirementItem() {
  if (requirementForm.items.length >= 20) {
    ElMessage.warning('客户需求最多录入 20 条')
    return
  }
  requirementForm.items.push('')
}

function removeRequirementItem(index: number) {
  if (requirementForm.items.length <= 1) {
    return
  }
  requirementForm.items.splice(index, 1)
}

function buildRequirementPayload() {
  const items = requirementForm.items.map((item) => item.trim()).filter(Boolean)
  if (items.length > 20) {
    ElMessage.warning('客户需求最多录入 20 条')
    return null
  }
  for (const item of items) {
    const length = Array.from(item).length
    if (length < 10 || length > 100) {
      ElMessage.warning('每条客户需求字数需在 10-100 之间')
      return null
    }
  }
  return items
}

async function saveRequirements() {
  const current = project.value
  if (!current) return
  const customerRequirements = buildRequirementPayload()
  if (!customerRequirements) {
    return
  }
  requirementSaving.value = true
  try {
    await updateProject(current.id, {
      ...projectUpdatePayload(current),
      customerRequirements,
    })
    ElMessage.success('客户需求已保存')
    requirementEditVisible.value = false
    await load()
  } finally {
    requirementSaving.value = false
  }
}

function parseStringArray(value?: string | string[] | null) {
  if (Array.isArray(value)) {
    return value.map((item) => String(item).trim()).filter((item, index, arr) => item.length > 0 && arr.indexOf(item) === index)
  }
  if (!value) {
    return [] as string[]
  }
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed)
      ? parsed.map((item) => String(item).trim()).filter((item, index, arr) => item.length > 0 && arr.indexOf(item) === index)
      : []
  } catch {
    return String(value)
      .split(/[,，、;；\n\r]+/)
      .map((item) => item.trim())
      .filter((item, index, arr) => item.length > 0 && arr.indexOf(item) === index)
  }
}

async function saveChannelAllocations() {
  const current = project.value
  if (!current) return
  channelSaving.value = true
  try {
    await updateProject(current.id, projectUpdatePayload(current))
    ElMessage.success('渠道额度已保存')
    channelEditVisible.value = false
    await load()
  } finally {
    channelSaving.value = false
  }
}

function goReports() {
  router.push(`/admin/projects/${projectId}/reports`)
}

function goBaselineReport() {
  router.push(`/admin/projects/${projectId}/baseline-report`)
}

async function load() {
  loading.value = true
  try {
    const { data } = await getProjectDetail(projectId)
    project.value = data.data
    activationConfirmed.value = false
  } catch {
    project.value = null
  } finally {
    loading.value = false
  }
}

async function startProject() {
  if (!canActivateProject.value) {
    ElMessage.warning('当前账号无项目启动权限')
    return
  }
  saving.value = true
  try {
    await ElMessageBox.confirm(
      '确认启动该项目？',
      '项目启动确认',
      { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' },
    )
    const current = project.value
    if (!current) {
      ElMessage.error('项目信息不存在')
      return
    }
    if (current.status !== 'pending_start' && current.status !== 'paused') {
      ElMessage.info('当前项目不可启动')
      return
    }
    if (!activationConfirmed.value) {
      ElMessage.warning('请先勾选“已阅读并确认项目基础信息”后再激活')
      return
    }
    if (!validateKeywordGroupCountsBeforeStart(current)) {
      return
    }
    await updateProjectStatus(projectId, 'active')
    ElMessage.success('项目已启动')
    await load()
  } catch (err: any) {
    if (err === 'cancel' || err === 'close') return
  } finally {
    saving.value = false
  }
}

async function handleKeywordImport(file: UploadFile) {
  if (!project.value || !file.raw) return
  importing.value = true
  try {
    await importProjectKeywordGroup(project.value.id, file.raw)
    ElMessage.success('拓词组导入成功')
    await load()
  } finally {
    importing.value = false
  }
}

async function removeKeywordGroup(row: KeywordGroup) {
  if (!project.value || !canDeleteKeywordGroup.value) {
    ElMessage.warning('当前项目状态不可删除拓词组')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认删除拓词组「${row.name}」？删除后可重新创建或导入拓词组。`,
      '删除拓词组确认',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' },
    )
    await deleteKeywordGroup(row.id)
    ElMessage.success('拓词组已删除')
    if (currentKeywordGroup.value?.id === row.id) {
      questionDrawerVisible.value = false
      currentKeywordGroup.value = null
    }
    await load()
  } catch (err: any) {
    if (err === 'cancel' || err === 'close') return
  }
}

async function openKeywordQuestions(row: KeywordGroup) {
  currentKeywordGroup.value = row
  questionTier.value = 'all'
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
      tier: questionTier.value,
    })
    Object.assign(questionPage, data.data)
  } finally {
    questionLoading.value = false
  }
}

function openQuestionEdit(row: KeywordGroupQuestion) {
  currentQuestionId.value = row.id
  questionForm.questionText = row.questionText
  questionForm.sceneCode = row.sceneCode || 'brand'
  questionForm.priority = row.priority || 'medium'
  questionForm.scoreRelevance = Number(row.scoreRelevance || 4)
  questionForm.scoreIntent = Number(row.scoreIntent || 4)
  questionForm.scoreCompetition = Number(row.scoreCompetition || 4)
  questionForm.scoreConversion = Number(row.scoreConversion || 4)
  questionForm.scoreCoverage = Number(row.scoreCoverage || 4)
  questionForm.articleGenerationNote = row.articleGenerationNote || ''
  questionEditVisible.value = true
}

async function saveQuestion() {
  if (!currentKeywordGroup.value || !currentQuestionId.value) return
  questionSaving.value = true
  try {
    await updateKeywordGroupQuestion(currentKeywordGroup.value.id, currentQuestionId.value, questionForm)
    ElMessage.success('问题已保存')
    questionEditVisible.value = false
    await loadKeywordQuestions()
  } finally {
    questionSaving.value = false
  }
}

function sceneLabel(value?: string | null) {
  const labels: Record<string, string> = {
    brand: '品牌场景',
    decision: '决策场景',
    deal: '成交场景',
    compare: '对比场景',
    qa: '问答场景',
    function: '功能场景',
  }
  return value ? (labels[value] || value) : '-'
}

function priorityLabel(value?: string | null) {
  const labels: Record<string, string> = { high: '高', medium: '中', low: '低' }
  return value ? (labels[value] || value) : '-'
}

async function removeCurrentProject() {
  if (!project.value) return
  try {
    await ElMessageBox.confirm(
      `确认删除项目「${project.value.projectName}」？该操作不可撤销。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' },
    )
    await deleteProject(projectId)
    ElMessage.success('删除成功')
    router.push('/admin/projects')
  } catch (err: any) {
    if (err === 'cancel' || err === 'close') return
  }
}

onMounted(() => {
  if (!hasValidId) {
    ElMessage.error('项目参数无效')
    return
  }
  dictStore.ensureLoaded()
  load()
})
</script>

<style scoped>
.project-hero-kpis {
  gap: 12px;
}

.project-hero-kpi {
  position: relative;
  overflow: hidden;
  border-color: rgba(148, 163, 184, 0.22);
}

.project-hero-kpi::after {
  content: "";
  position: absolute;
  right: 14px;
  bottom: -18px;
  width: 74px;
  height: 74px;
  border-radius: 999px;
  opacity: 0.16;
}

.project-hero-kpi span,
.project-hero-kpi strong {
  position: relative;
  z-index: 1;
}

.project-hero-kpi--keyword {
  background: linear-gradient(135deg, rgba(239, 246, 255, 0.96), rgba(255, 255, 255, 0.9));
}

.project-hero-kpi--keyword::after {
  background: #2563eb;
}

.project-hero-kpi--keyword strong {
  color: #2563eb;
}

.project-hero-kpi--quota {
  background: linear-gradient(135deg, rgba(245, 243, 255, 0.96), rgba(255, 255, 255, 0.9));
}

.project-hero-kpi--quota::after {
  background: #8b5cf6;
}

.project-hero-kpi--quota strong {
  color: #6d28d9;
}

.project-hero-kpi--channel {
  background: linear-gradient(135deg, rgba(236, 253, 245, 0.96), rgba(255, 255, 255, 0.9));
}

.project-hero-kpi--channel::after {
  background: #10b981;
}

.project-hero-kpi--channel strong {
  color: #059669;
}

.score-input {
  width: 220px;
}
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.keyword-group-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.keyword-group-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}
.keyword-import-upload {
  display: inline-flex;
}
.keyword-import-upload :deep(.el-upload) {
  display: inline-flex;
}
.requirement-view-list {
  display: grid;
  gap: 10px;
}
.requirement-view-item {
  display: grid;
  grid-template-columns: 28px 1fr;
  gap: 10px;
  align-items: flex-start;
  padding: 12px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fafafa;
}
.requirement-view-index {
  width: 24px;
  height: 24px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #ecf5ff;
  color: #409eff;
  font-size: 12px;
  font-weight: 600;
}
.requirement-view-text {
  line-height: 1.6;
  color: #303133;
  word-break: break-word;
}
.requirement-editor {
  display: grid;
  gap: 12px;
}
.requirement-edit-item {
  padding: 12px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #fafafa;
}
.requirement-row-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}
.requirement-add {
  width: 100%;
  border-style: dashed;
}
.channel-name {
  display: flex;
  align-items: center;
  gap: 8px;
}
.channel-edit-note {
  margin-bottom: 12px;
  font-size: 13px;
  color: #606266;
}
.channel-allocation-panel {
  display: grid;
  gap: 12px;
}
.channel-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.channel-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.channel-meta small {
  color: #909399;
}
@media (max-width: 640px) {
  .keyword-group-header {
    align-items: flex-start;
    flex-direction: column;
  }
  .keyword-group-actions {
    justify-content: flex-start;
    width: 100%;
  }
}
</style>
