<template>
  <div class="space-y-4">
    <el-page-header content="项目详情" @back="$router.back()" />

    <el-card v-loading="loading">
      <template #header>
        <div class="flex items-center justify-between">
          <span>基础信息</span>
          <div class="space-x-2">
            <el-button size="small" @click="goReports">项目报表</el-button>
            <el-tag>{{ dictStore.label('project_status', project?.status) }}</el-tag>
            <el-tag type="info">{{ dictStore.label('project_stage', project?.stage) }}</el-tag>
          </div>
        </div>
      </template>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="项目编码">{{ project?.projectCode }}</el-descriptions-item>
        <el-descriptions-item label="项目名称">{{ project?.projectName }}</el-descriptions-item>
        <el-descriptions-item label="项目别名">{{ project?.projectAliases || '-' }}</el-descriptions-item>
        <el-descriptions-item label="客户名称">{{ project?.companyName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="品牌名称">{{ project?.brandName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="拓词组">{{ keywordSummary }}</el-descriptions-item>
        <el-descriptions-item label="问题额度">{{ keywordAllocationSummary }}</el-descriptions-item>
        <el-descriptions-item label="归属类型">{{ dictStore.label('owner_type', project?.ownerType) }}</el-descriptions-item>
        <el-descriptions-item label="合伙人">{{ project?.ownerType === 'direct' ? '-' : '已绑定' }}</el-descriptions-item>
        <el-descriptions-item label="所在地区">{{ regionText(project) }}</el-descriptions-item>
        <el-descriptions-item label="交付模式">{{ project?.deliveryMode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="启动日期">{{ project?.activatedAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="签约扣款(元)">{{ centsToYuan(project?.deductionAmount) }}</el-descriptions-item>
        <el-descriptions-item label="折扣快照">{{ project?.discountRateSnapshot != null ? (project.discountRateSnapshot * 100).toFixed(2) + '%' : '-' }}</el-descriptions-item>
        <el-descriptions-item label="扣款流水号">{{ project?.deductionTxnNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="主目标" :span="3">{{ project?.primaryGoal || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card v-if="project">
      <template #header>
        <div class="flex items-center justify-between">
          <span>绑定拓词组</span>
          <el-upload
            v-if="canImportKeywordGroup"
            :auto-upload="false"
            :show-file-list="false"
            accept=".xlsx"
            :on-change="handleKeywordImport"
          >
            <el-button type="primary" size="small" :loading="importing">导入拓词组</el-button>
          </el-upload>
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
          <template #default="{ row }">{{ row.typeLabel || row.type || '-' }}</template>
        </el-table-column>
        <el-table-column prop="savedKeywordCount" label="总问题数" width="110" />
        <el-table-column label="A/B/C" width="160">
          <template #default="{ row }">A {{ row.savedKeywordCountA || 0 }} / B {{ row.savedKeywordCountB || 0 }} / C {{ row.savedKeywordCountC || 0 }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-button link type="primary" @click="openKeywordQuestions(row)">查看编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card v-if="project">
      <template #header><span>内容策略配置</span></template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="目标区域词">{{ joinArray(project.targetRegions) }}</el-descriptions-item>
        <el-descriptions-item label="目标受众">{{ project.targetAudience || '-' }}</el-descriptions-item>
        <el-descriptions-item label="内容调性">{{ project.contentTone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="优先写作角度">{{ joinArray(project.preferredAngles) }}</el-descriptions-item>
        <el-descriptions-item label="项目定制表述" :span="2">{{ project.customStatement || '-' }}</el-descriptions-item>
        <el-descriptions-item label="补充禁用词" :span="2">{{ joinArray(project.extraForbiddenPhrases) }}</el-descriptions-item>
        <el-descriptions-item label="内容备注" :span="2">{{ project.contentNote || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card v-if="showActivationGuide">
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
              <el-button link type="primary" :disabled="project?.status !== 'paused'" @click="openQuestionEdit(row)">编辑</el-button>
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

    <el-dialog v-model="questionEditVisible" title="编辑问题" width="720px">
      <el-form label-width="130px">
        <el-form-item label="问题文本" required>
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
        <el-form-item label="生成文章备注">
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
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadFile } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { useDictStore } from '@/stores/dict'
import { deleteProject, getProjectDetail, getKeywordGroupQuestions, importProjectKeywordGroup, updateKeywordGroupQuestion, updateProjectStatus } from '@/api/project'
import type { KeywordGroup, KeywordGroupQuestion, PageResult, Project } from '@/types'
import { regionDisplayFromPayload } from '@/constants/region'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const dictStore = useDictStore()
const canActivateProject = computed(() => userStore.hasPermission('project.start'))
const projectId = Number(route.params.id)
const hasValidId = Number.isFinite(projectId) && projectId > 0

const loading = ref(false)
const saving = ref(false)
const importing = ref(false)
const project = ref<Project | null>(null)
const questionDrawerVisible = ref(false)
const questionEditVisible = ref(false)
const questionLoading = ref(false)
const questionSaving = ref(false)
const currentKeywordGroup = ref<KeywordGroup | null>(null)
const currentQuestionId = ref<number | null>(null)
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

const activationConfirmed = ref(false)
const showActivationGuide = computed(() => route.query.activate === '1' && project.value?.status === 'paused')
const canImportKeywordGroup = computed(() => {
  const current = project.value
  return !!current && current.status === 'paused' && (current.selectedKeywordGroupCount || 0) === 0
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

function centsToYuan(v?: number | null) {
  if (v == null) return '-'
  return Number(v).toFixed(2)
}

function regionText(p?: Project | null) {
  if (!p) return '-'
  return regionDisplayFromPayload(p) || '-'
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

function goReports() {
  router.push(`/admin/projects/${projectId}/reports`)
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
    if (current.status !== 'paused') {
      ElMessage.info('当前项目已启动')
      return
    }
    if (!activationConfirmed.value) {
      ElMessage.warning('请先勾选“已阅读并确认项目基础信息”后再激活')
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
.score-input {
  width: 220px;
}
</style>
