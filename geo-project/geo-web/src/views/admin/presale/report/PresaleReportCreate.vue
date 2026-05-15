<template>
  <div class="presale-report-create admin-page">
    <div class="page-header admin-page-header">
      <div>
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/admin/presale/report' }">AI可见度诊断报告</el-breadcrumb-item>
          <el-breadcrumb-item>新建报告</el-breadcrumb-item>
        </el-breadcrumb>
        <div class="admin-page-kicker">报告生成</div>
        <h2 class="page-title admin-page-title">新建报告</h2>
        <div class="admin-page-subtitle">录入品牌基础信息，确认诊断范围与问题模板后提交生成。</div>
      </div>
    </div>

    <el-card shadow="never" class="form-card admin-rich-card">
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="120px"
        label-position="right"
      >
        <el-form-item label="品牌名称" prop="brandName">
          <el-input
            v-model="form.brandName"
            placeholder="如:海底捞"
            maxlength="100"
            show-word-limit
            style="max-width: 480px"
          />
        </el-form-item>

        <el-form-item label="行业" prop="industry">
          <el-select
            v-model="form.industry"
            placeholder="选择或输入行业"
            filterable
            allow-create
            default-first-option
            style="max-width: 320px"
            @change="onIndustryChange"
          >
            <el-option
              v-for="opt in industryOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="身份" prop="industryRole">
          <el-select
            v-model="form.industryRole"
            placeholder="选择或输入身份"
            :disabled="!form.industry"
            filterable
            allow-create
            default-first-option
            style="max-width: 320px"
          >
            <el-option
              v-for="opt in filteredRoleOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
          <span v-if="!form.industry" class="form-tip">请先选择行业</span>
        </el-form-item>

        <el-form-item label="地区" prop="region">
          <el-input
            v-model="form.region"
            placeholder="如:全国"
            maxlength="50"
            show-word-limit
            style="max-width: 320px"
          />
        </el-form-item>

        <el-form-item label="目标用户" prop="userType">
          <el-input
            v-model="form.userType"
            placeholder="可选,如:商务宴请人群、年轻消费者"
            maxlength="50"
            show-word-limit
            style="max-width: 480px"
          />
        </el-form-item>

        <el-form-item label="客户诉求" prop="userDemand">
          <el-input
            v-model="form.userDemand"
            type="textarea"
            :rows="3"
            placeholder="可选,最多 500 字。例如:了解我们品牌在 AI 推荐中的真实表现。"
            maxlength="500"
            show-word-limit
            style="max-width: 640px"
          />
        </el-form-item>
      </el-form>

      <el-divider />
      <div class="scope-preview admin-scope-preview">
        <div class="scope-title">诊断范围预览</div>
        <div class="scope-grid">
          <div class="scope-item">
            <div class="scope-number">{{ scopeNumber(scopePreview?.platformCount) }}</div>
            <div class="scope-label">AI 平台</div>
          </div>
          <div class="scope-item">
            <div class="scope-number">{{ scopeNumber(scopePreview?.promptQueryCount) }}</div>
            <div class="scope-label">Prompt 查询</div>
          </div>
          <div class="scope-item">
            <div class="scope-number">{{ scopeNumber(scopePreview?.llmCallUpperBound) }}</div>
            <div class="scope-label">最多 LLM 调用</div>
          </div>
          <div class="scope-item">
            <div class="scope-number">{{ scopeNumber(scopePreview?.dimensionCount) }}</div>
            <div class="scope-label">分析维度</div>
          </div>
        </div>
        <div class="scope-note">{{ scopeNote }}</div>
      </div>

      <el-divider />
      <section class="prompt-preview">
        <button
          class="prompt-summary"
          :class="{ 'is-open': promptPanelOpen }"
          type="button"
          :aria-expanded="promptPanelOpen"
          aria-controls="prompt-preview-panel"
          @click="togglePromptPanel"
        >
          <span class="summary-main">问题预览</span>
          <span class="summary-text">{{ promptSummary }}</span>
          <span class="summary-action">
            <span class="action-text">{{ promptPanelOpen ? '收起' : '展开' }}</span>
            <span class="action-icon-wrap">
              <el-icon class="action-icon">
                <ArrowDown />
              </el-icon>
            </span>
          </span>
        </button>

        <el-alert
          v-if="!canPreviewPrompts"
          title="请先填写品牌名称、行业、身份和地区后查看 Prompt 预览。"
          type="warning"
          :closable="false"
          show-icon
          class="prompt-alert"
        />

        <div
          v-if="promptPanelOpen && canPreviewPrompts"
          id="prompt-preview-panel"
          class="prompt-panel"
        >
          <div v-if="promptLoading" class="prompt-empty">正在读取 Prompt 清单...</div>
          <div v-else-if="promptLoadFailed" class="prompt-empty">
            Prompt 清单读取失败,请刷新页面后重试。
          </div>
          <template v-else>
            <div class="prompt-toolbar">
              <div class="prompt-version">模板版本: {{ promptTemplateVersion || '—' }}</div>
              <el-button
                v-if="activePromptTab === 'template'"
                size="small"
                :disabled="modifiedCount === 0"
                @click="restoreAllModified"
              >
                全部恢复默认
              </el-button>
            </div>

            <el-tabs v-model="activePromptTab" class="prompt-tabs">
              <el-tab-pane label="模板问题预览" name="template">
                <div
                  v-for="group in promptGroups"
                  :key="group.category"
                  class="prompt-group"
                >
                  <div class="group-header">
                    <div>
                      <span class="group-title">{{ group.category }}</span>
                      <span class="group-count">{{ group.items.length }} 条</span>
                    </div>
                    <el-button
                      size="small"
                      text
                      :disabled="group.modifiedCount === 0"
                      @click="restoreGroup(group.category, group.modifiedCount)"
                    >
                      恢复本类默认
                    </el-button>
                  </div>

                  <el-alert
                    v-if="group.hasCompetitorVar"
                    title="{competitor} 为竞品组占位符。系统会在第一轮分析后识别最多 3 个竞品，并用「品牌1、品牌2、品牌3」的形式整体替换该占位符。每条对比型问题在每个平台只执行一次。"
                    type="info"
                    :closable="false"
                    class="competitor-hint"
                  />

                  <div
                    v-for="item in group.items"
                    :key="item.source.id"
                    class="prompt-row"
                    :class="{ 'is-modified': isModified(item) }"
                  >
                    <div class="prompt-row-head">
                      <div class="prompt-meta">
                        <span class="prompt-code">{{ item.source.promptCode }}</span>
                        <el-tag v-if="isModified(item)" size="small" type="warning">已修改</el-tag>
                        <el-tag v-else size="small" type="info">默认</el-tag>
                      </div>
                      <div class="prompt-actions">
                        <el-button size="small" text @click="toggleEdit(item.source.id)">
                          {{ editingId === item.source.id ? '完成' : '编辑' }}
                        </el-button>
                        <el-button
                          size="small"
                          text
                          :disabled="!isModified(item)"
                          @click="restoreOne(item.source.id)"
                        >
                          恢复默认
                        </el-button>
                      </div>
                    </div>

                    <template v-if="editingId === item.source.id">
                      <el-input
                        v-model="item.draft.promptContent"
                        type="textarea"
                        :rows="4"
                        maxlength="1000"
                        show-word-limit
                        class="prompt-editor"
                      />
                      <div class="variable-help">
                        可用变量: {brand} {product} {industry} {industry_role} {region} {user_type}
                        <span v-if="item.source.hasCompetitorVar">{competitor}</span>
                      </div>
                      <div class="render-preview">
                        <span class="preview-label">实时预览</span>
                        <span v-html="renderPromptHtml(item.draft.promptContent)"></span>
                      </div>
                    </template>
                    <template v-else>
                      <div class="rendered-prompt" v-html="renderPromptHtml(item.draft.promptContent)"></div>
                    </template>

                    <div v-if="isModified(item)" class="modified-tip">
                      基础信息已变化时,当前内容仍为你的修改版本。点击恢复默认可使用最新基础信息重新渲染。
                    </div>
                  </div>
                </div>
              </el-tab-pane>

              <el-tab-pane label="LLM问题预览" name="llm">
                <div class="llm-config">
                  <div class="llm-total">
                    <span class="form-label compact">总问题数</span>
                    <el-input-number v-model="llmPlan.totalCount" :min="1" :max="60" controls-position="right" />
                  </div>
                  <div class="llm-category-grid">
                    <div v-for="cat in CATEGORY_OPTIONS" :key="cat.code" class="llm-category-input">
                      <span>{{ cat.label }}</span>
                      <el-input-number
                        v-model="llmPlan.categoryCounts[cat.code]"
                        :min="cat.code === 'COMPARISON' ? 1 : cat.code === 'COGNITIVE' ? 3 : 0"
                        :max="30"
                        size="small"
                        controls-position="right"
                      />
                    </div>
                  </div>
                </div>

                <el-alert
                  v-if="llmStale"
                  title="基础信息已变化，当前 LLM 问题可能不再匹配。请重新生成或确认继续使用。"
                  type="warning"
                  :closable="false"
                  class="prompt-alert"
                />

                <div class="llm-actions">
                  <el-button
                    type="primary"
                    :loading="llmGenerating"
                    :disabled="!canGenerateLlm || (llmQuestions.length > 0 && llmMissingTotal === 0) || llmGenerationIssues.length > 0"
                    @click="generateLlmQuestions(false)"
                  >
                    {{ llmQuestions.length ? `补 ${llmMissingTotal} 条` : '生成 LLM 问题' }}
                  </el-button>
                  <el-button :loading="llmGenerating" :disabled="!canGenerateLlm" @click="generateLlmQuestions(true)">重新生成</el-button>
                  <el-button v-if="llmStale" @click="confirmUseStaleLlmQuestions">确认继续使用当前问题</el-button>
                  <span class="llm-count-status" :class="{ danger: llmSubmitIssues.length > 0 }">
                    当前 {{ llmQuestions.length }} / {{ llmPlan.totalCount }}
                  </span>
                </div>

                <el-alert
                  v-if="llmLastWarning"
                  :title="llmLastWarning"
                  type="warning"
                  :closable="false"
                  class="prompt-alert"
                />

                <el-alert
                  v-if="llmGenerationIssues.length"
                  :title="llmGenerationIssues[0]"
                  type="warning"
                  :closable="false"
                  class="prompt-alert"
                />

                <div v-for="cat in CATEGORY_OPTIONS" :key="cat.code" class="prompt-group">
                  <div class="group-header">
                    <div>
                      <span class="group-title">{{ cat.label }}</span>
                      <span class="group-count">{{ llmActualCategoryCounts[cat.code] }} / {{ llmPlan.categoryCounts[cat.code] }}</span>
                    </div>
                    <el-button size="small" text @click="addLlmQuestion(cat.code)">手动新增</el-button>
                  </div>

                  <el-alert
                    v-if="cat.code === 'COMPARISON'"
                    title="对比型问题必须包含 {competitor}，其他类型不能包含该占位符。"
                    type="info"
                    :closable="false"
                    class="competitor-hint"
                  />

                  <div
                    v-for="item in llmQuestionsByCategory[cat.code]"
                    :key="item.id"
                    class="prompt-row"
                    :class="{ 'is-modified': llmQuestionError(item) }"
                  >
                    <div class="prompt-row-head">
                      <div class="prompt-meta">
                        <span class="prompt-code">LLM_{{ cat.code }}</span>
                        <el-tag size="small" :type="llmQuestionError(item) ? 'danger' : 'success'">
                          {{ llmQuestionError(item) || '有效' }}
                        </el-tag>
                      </div>
                      <div class="prompt-actions">
                        <el-button size="small" text type="danger" @click="removeLlmQuestion(item.id)">删除</el-button>
                      </div>
                    </div>
                    <el-input
                      v-model="item.promptContent"
                      type="textarea"
                      :rows="3"
                      maxlength="1000"
                      show-word-limit
                      class="prompt-editor"
                    />
                    <div v-if="cat.code === 'COMPARISON'" class="variable-help">
                      可用变量: 仅 {competitor}（其他信息请直接写真实文本）
                    </div>
                    <div class="render-preview">
                      <span class="preview-label">实时预览</span>
                      <span v-html="renderPromptHtml(item.promptContent)"></span>
                    </div>
                  </div>
                </div>
              </el-tab-pane>
            </el-tabs>
          </template>
        </div>
      </section>

      <div class="action-bar">
        <el-button @click="onCancel">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">
          {{ submitting ? '提交中...' : '提交生成' }}
        </el-button>
        <div class="submit-source-tip">将使用当前 Tab 的问题生成报告：{{ activePromptTab === 'template' ? '模板问题预览' : 'LLM问题预览' }}</div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
import {
  createReport,
  generateLlmPromptQuestions,
  getRegenerateDraft,
  getReportScopePreview,
  listPromptTemplates,
  type CreateReportRequest,
  type LlmPromptQuestionDraft,
  type LlmPromptQuestionPlan,
  type PromptTemplateDraftRequest,
  type PromptTemplateVO,
  type PresalePromptCategoryCode,
  type RegenerateDraftVO,
  type PromptSourceMode,
  type ReportScopePreviewVO
} from '@/api/presaleReport'
import { calculatePromptScope } from '@/utils/presale/prompt-scope'

interface PromptDraftItem {
  sourceTemplateId: number
  promptContent: string
}

interface PromptItem {
  source: PromptTemplateVO
  draft: PromptDraftItem
}

interface LlmQuestionDraftItem extends LlmPromptQuestionDraft {
  id: string
}

const CATEGORY_ORDER = ['推荐型', '对比型', '问题型', '认知型', '场景型']
const CATEGORY_OPTIONS: Array<{ code: PresalePromptCategoryCode; label: string }> = [
  { code: 'RECOMMENDATION', label: '推荐型' },
  { code: 'COMPARISON', label: '对比型' },
  { code: 'PROBLEM', label: '问题型' },
  { code: 'COGNITIVE', label: '认知型' },
  { code: 'SCENARIO', label: '场景型' }
]
const CATEGORY_LABEL_TO_CODE: Record<string, PresalePromptCategoryCode> = {
  推荐型: 'RECOMMENDATION',
  对比型: 'COMPARISON',
  问题型: 'PROBLEM',
  认知型: 'COGNITIVE',
  场景型: 'SCENARIO'
}
const CATEGORY_CODES = CATEGORY_OPTIONS.map((item) => item.code)
const ALLOWED_PROMPT_VARIABLES = new Set([
  'competitor'
])
const MAX_LLM_TOTAL_COUNT = 60
const MAX_LLM_CATEGORY_COUNT = 30
const MIN_LLM_COGNITIVE_COUNT = 3
const MAX_LLM_EXISTING_QUESTIONS = 80

const router = useRouter()
const route = useRoute()
const formRef = ref<FormInstance>()
const submitting = ref(false)
const scopePreview = ref<ReportScopePreviewVO | null>(null)
const scopeLoading = ref(false)
const scopeLoadFailed = ref(false)
const promptSources = ref<PromptTemplateVO[]>([])
const promptDrafts = ref<PromptDraftItem[]>([])
const promptLoading = ref(false)
const promptLoadFailed = ref(false)
const promptPanelOpen = ref(false)
const editingId = ref<number | null>(null)
const activePromptTab = ref<PromptSourceMode>('template')
const llmGenerating = ref(false)
const llmStale = ref(false)
const llmBaseSnapshot = ref('')
const llmLastWarning = ref('')
const llmQuestionSeq = ref(0)
const llmQuestions = ref<LlmQuestionDraftItem[]>([])
const llmPlan = reactive<LlmPromptQuestionPlan>({
  totalCount: 0,
  categoryCounts: emptyCategoryCounts()
})

const form = reactive<CreateReportRequest>({
  brandName: '',
  industry: '',
  industryRole: '',
  region: '',
  userDemand: '',
  userType: '',
  promptTemplateVersion: '',
  promptTemplates: []
})

const rules: FormRules = {
  brandName: [{ required: true, message: '品牌名不能为空', trigger: 'blur' }],
  industry: [{ required: true, message: '请选择行业', trigger: 'change' }],
  industryRole: [{ required: true, message: '请选择身份', trigger: 'change' }],
  region: [{ required: true, message: '请输入地区', trigger: 'blur' }],
  userType: [{ max: 50, message: '目标用户最多 50 字', trigger: 'blur' }],
  userDemand: [{ max: 500, message: '客户诉求最多 500 字', trigger: 'blur' }]
}

const industryOptions = [
  { value: 'restaurant', label: '餐饮' },
  { value: 'education', label: '教培' },
  { value: 'automotive', label: '汽车' },
  { value: 'retail', label: '电商零售' },
  { value: 'finance', label: '金融' },
  { value: 'tourism', label: '旅游酒店' },
  { value: 'medical_beauty', label: '医美美容' },
  { value: 'tech_software', label: 'SaaS 企业软件' }
]

const allRoleOptions = [
  { value: 'chain_brand', label: '连锁品牌' },
  { value: 'single_store', label: '单店' },
  { value: 'franchise', label: '加盟商' },
  { value: 'manufacturer', label: '生产厂家' },
  { value: 'dealer', label: '经销商' },
  { value: 'platform', label: '平台方' },
  { value: 'service_provider', label: '服务商' },
  { value: 'kol', label: '个人/KOL' }
]

const filteredRoleOptions = computed(() => allRoleOptions)

function optionLabel(options: Array<{ value: string; label: string }>, value: string) {
  if (!value) {
    return ''
  }
  return options.find((item) => item.value === value)?.label || value
}

const canPreviewPrompts = computed(() => {
  return Boolean(
    form.brandName.trim() &&
      form.industry &&
      form.industryRole &&
      form.region.trim()
  )
})

watch(canPreviewPrompts, (ready) => {
  if (!ready && promptPanelOpen.value) {
    promptPanelOpen.value = false
    editingId.value = null
    ElMessage.warning('基础信息不完整，已收起 Prompt 预览')
  }
})

watch(
  () => [
    form.brandName,
    form.industry,
    form.industryRole,
    form.region,
    form.userType,
    form.userDemand
  ],
  () => {
    if (!llmQuestions.value.length || !llmBaseSnapshot.value) {
      return
    }
    llmStale.value = currentBaseSnapshot() !== llmBaseSnapshot.value
  }
)

const promptTemplateVersion = computed(() => {
  return promptSources.value[0]?.templateVersion || ''
})

const promptItems = computed<PromptItem[]>(() => {
  const draftById = new Map(promptDrafts.value.map((d) => [d.sourceTemplateId, d]))
  return promptSources.value.map((source) => ({
    source,
    draft: draftById.get(source.id) ?? {
      sourceTemplateId: source.id,
      promptContent: source.promptContent
    }
  }))
})

const promptGroups = computed(() => {
  return CATEGORY_ORDER.map((category) => {
    const items = promptItems.value.filter((item) => item.source.category === category)
    return {
      category,
      items,
      hasCompetitorVar: items.some((item) => item.source.hasCompetitorVar),
      modifiedCount: items.filter((item) => isModified(item)).length
    }
  }).filter((group) => group.items.length > 0)
})

const modifiedCount = computed(() => promptItems.value.filter((item) => isModified(item)).length)

const llmQuestionsByCategory = computed<Record<PresalePromptCategoryCode, LlmQuestionDraftItem[]>>(() => {
  const grouped = createCategoryRecord<LlmQuestionDraftItem[]>(() => [])
  for (const item of llmQuestions.value) {
    grouped[item.categoryCode].push(item)
  }
  return grouped
})

const llmActualCategoryCounts = computed<Record<PresalePromptCategoryCode, number>>(() => {
  const counts = createCategoryRecord(() => 0)
  for (const item of llmQuestions.value) {
    counts[item.categoryCode] += 1
  }
  return counts
})

const llmMissingTotal = computed(() => Math.max(0, Number(llmPlan.totalCount || 0) - llmQuestions.value.length))

const llmSubmitIssues = computed(() => validateLlmSubmit(false))

const llmGenerationIssues = computed(() => validateLlmGeneration(false, false))

const canGenerateLlm = computed(() => {
  return canPreviewPrompts.value && !llmGenerating.value && validateLlmPlanConfig(false).length === 0
})

const scopeNote = computed(() => {
  if (scopeLoading.value) return '正在读取当前启用平台与 Prompt 模板配置...'
  if (scopeLoadFailed.value) return '诊断范围预览读取失败,请刷新页面后重试。'
  if (!scopePreview.value) return '暂无诊断范围数据。'
  return `按当前启用配置预计最多发起 ${formatInt(scopePreview.value.llmCallUpperBound)} 次 LLM 调用。生成过程异步进行,提交后会跳到进度页。`
})

const promptSummary = computed(() => {
  if (promptLoading.value) return '正在读取 Prompt 清单...'
  if (promptLoadFailed.value) return 'Prompt 清单读取失败'
  if (!promptSources.value.length) return '暂无 Prompt 模板'
  if (activePromptTab.value === 'llm') {
    const status = llmSubmitIssues.value.length ? `，还差 ${llmMissingTotal.value} 条或存在待修正项` : '，数量已匹配'
    return `当前为 LLM 问题预览：${llmQuestions.value.length}/${llmPlan.totalCount} 条${status}。提交时只使用当前 Tab 的问题。`
  }
  const counts = CATEGORY_ORDER.map((category) => {
    const count = promptSources.value.filter((x) => x.category === category).length
    return count > 0 ? `${category} ${count}` : ''
  }).filter(Boolean)
  const scope = scopePreview.value
    ? calculatePromptScope(
        scopePreview.value.platformCount,
        scopePreview.value.genericPromptCount,
        scopePreview.value.competitorPromptCount
      )
    : null
  const maxCalls = scope ? `,最多产生约 ${formatInt(scope.totalUpperBound)} 次 LLM 调用` : ''
  return `本次报告将向 AI 平台提问 ${promptSources.value.length} 条问题（${counts.join(' / ')}）${maxCalls}。`
})

onMounted(() => {
  void loadScopePreview()
  void loadPromptTemplates()
})

async function loadScopePreview() {
  scopeLoading.value = true
  scopeLoadFailed.value = false
  try {
    scopePreview.value = await getReportScopePreview()
  } catch {
    scopePreview.value = null
    scopeLoadFailed.value = true
  } finally {
    scopeLoading.value = false
  }
}

async function loadPromptTemplates() {
  promptLoading.value = true
  promptLoadFailed.value = false
  try {
    promptSources.value = await listPromptTemplates()
    promptDrafts.value = promptSources.value.map((source) => ({
      sourceTemplateId: source.id,
      promptContent: source.promptContent
    }))
    form.promptTemplateVersion = promptTemplateVersion.value
    applyDefaultLlmPlanFromTemplates()
    await applyRegenerateDraftFromRoute()
  } catch {
    promptSources.value = []
    promptDrafts.value = []
    promptLoadFailed.value = true
  } finally {
    promptLoading.value = false
  }
}

async function applyRegenerateDraftFromRoute() {
  const reportId = Number(route.query.regenerateFrom)
  if (!Number.isFinite(reportId) || reportId <= 0) {
    return
  }
  try {
    const draft = await getRegenerateDraft(reportId)
    applyRegenerateDraft(draft)
    promptPanelOpen.value = true
    ElMessage.success('已载入上一次报告的问题')
  } catch (err: any) {
    ElMessage.error(err?.message || '载入再次生成草稿失败')
  }
}

function applyRegenerateDraft(draft: RegenerateDraftVO) {
  form.brandName = draft.brandName || ''
  form.industry = draft.industry || ''
  form.industryRole = draft.industryRole || ''
  form.region = draft.region || ''
  form.userDemand = draft.userDemand || ''
  form.userType = draft.userType || ''

  if (draft.promptSourceMode === 'llm') {
    activePromptTab.value = 'llm'
    const plan = draft.llmQuestionPlan
    if (plan) {
      llmPlan.totalCount = plan.totalCount
      Object.assign(llmPlan.categoryCounts, {
        ...emptyCategoryCounts(),
        ...plan.categoryCounts
      })
    }
    llmQuestions.value = (draft.llmPromptQuestions || []).map((item) => ({
      id: nextLlmQuestionId(),
      categoryCode: item.categoryCode,
      promptContent: item.promptContent
    }))
    llmBaseSnapshot.value = currentBaseSnapshot()
    llmStale.value = false
    return
  }

  activePromptTab.value = 'template'
  const previousBySourceId = new Map(
    (draft.promptTemplates || []).map((item) => [item.sourceTemplateId, item.promptContent])
  )
  const previousByPromptCode = new Map(
    (draft.promptTemplates || [])
      .filter((item) => item.sourcePromptCode)
      .map((item) => [item.sourcePromptCode as string, item.promptContent])
  )
  promptDrafts.value = promptDrafts.value.map((item) => ({
    ...item,
    promptContent:
      previousBySourceId.get(item.sourceTemplateId) ||
      previousByPromptCode.get(promptSources.value.find((source) => source.id === item.sourceTemplateId)?.promptCode || '') ||
      item.promptContent
  }))
}

function onIndustryChange() {
  form.industryRole = ''
}

function togglePromptPanel() {
  if (!promptPanelOpen.value && !canPreviewPrompts.value) {
    ElMessage.warning('请先填写完整基础信息后查看 Prompt 预览')
    return
  }
  promptPanelOpen.value = !promptPanelOpen.value
}

function toggleEdit(sourceTemplateId: number) {
  editingId.value = editingId.value === sourceTemplateId ? null : sourceTemplateId
}

function isModified(item: PromptItem) {
  return item.draft.promptContent !== item.source.promptContent
}

function restoreOne(sourceTemplateId: number) {
  const source = promptSources.value.find((item) => item.id === sourceTemplateId)
  const draft = promptDrafts.value.find((item) => item.sourceTemplateId === sourceTemplateId)
  if (!source || !draft) return
  draft.promptContent = source.promptContent
  if (editingId.value === sourceTemplateId) {
    editingId.value = null
  }
}

async function restoreGroup(category: string, count: number) {
  if (count <= 0) return
  try {
    await ElMessageBox.confirm(
      `将丢弃${category}分类下 ${count} 条已修改的 Prompt,确定继续?`,
      '恢复默认',
      {
        confirmButtonText: '恢复默认',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch {
    return
  }
  for (const item of promptItems.value.filter((x) => x.source.category === category)) {
    restoreOne(item.source.id)
  }
}

async function restoreAllModified() {
  const count = modifiedCount.value
  if (count <= 0) return
  try {
    await ElMessageBox.confirm(`将丢弃 ${count} 条已修改的 Prompt,确定继续?`, '全部恢复默认', {
      confirmButtonText: '恢复默认',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  for (const source of promptSources.value) {
    restoreOne(source.id)
  }
}

function emptyCategoryCounts(): Record<PresalePromptCategoryCode, number> {
  return createCategoryRecord(() => 0)
}

function createCategoryRecord<T>(factory: () => T): Record<PresalePromptCategoryCode, T> {
  return CATEGORY_CODES.reduce((result, code) => {
    result[code] = factory()
    return result
  }, {} as Record<PresalePromptCategoryCode, T>)
}

function applyDefaultLlmPlanFromTemplates() {
  const counts = emptyCategoryCounts()
  for (const source of promptSources.value) {
    const code = CATEGORY_LABEL_TO_CODE[source.category]
    if (code) {
      counts[code] += 1
    }
  }
  Object.assign(llmPlan.categoryCounts, counts)
  llmPlan.totalCount = promptSources.value.length
}

function currentBaseSnapshot() {
  return JSON.stringify({
    brandName: form.brandName.trim(),
    industry: form.industry,
    industryRole: form.industryRole,
    region: form.region.trim(),
    userType: form.userType?.trim() || '',
    userDemand: form.userDemand?.trim() || ''
  })
}

function validateLlmPlanConfig(showMessage: boolean) {
  const issues: string[] = []
  const totalCount = Number(llmPlan.totalCount || 0)
  if (!Number.isInteger(totalCount) || totalCount < 1 || totalCount > MAX_LLM_TOTAL_COUNT) {
    issues.push(`总问题数需在 1-${MAX_LLM_TOTAL_COUNT} 之间`)
  }

  let sum = 0
  for (const cat of CATEGORY_OPTIONS) {
    const value = Number(llmPlan.categoryCounts[cat.code] || 0)
    if (!Number.isInteger(value) || value < 0 || value > MAX_LLM_CATEGORY_COUNT) {
      issues.push(`${cat.label}数量需在 0-${MAX_LLM_CATEGORY_COUNT} 之间`)
    }
    sum += value
  }

  if (Number(llmPlan.categoryCounts.COMPARISON || 0) < 1) {
    issues.push('对比型分类必须存在，数量至少为 1')
  }
  if (Number(llmPlan.categoryCounts.COGNITIVE || 0) < MIN_LLM_COGNITIVE_COUNT) {
    issues.push(`认知型分类数量至少为 ${MIN_LLM_COGNITIVE_COUNT}`)
  }
  if (sum !== totalCount) {
    issues.push(`各分类数量之和需等于总问题数，当前为 ${sum}`)
  }

  if (showMessage && issues.length) {
    ElMessage.warning(issues[0])
  }
  return issues
}

function validateLlmGeneration(reset: boolean, showMessage: boolean) {
  const issues = validateLlmPlanConfig(false)
  if (!reset) {
    const totalCount = Number(llmPlan.totalCount || 0)
    if (llmQuestions.value.length > MAX_LLM_EXISTING_QUESTIONS) {
      issues.push(`已有 LLM 问题最多保留 ${MAX_LLM_EXISTING_QUESTIONS} 条，请删除多余问题后再补生成`)
    }
    if (llmQuestions.value.length > totalCount) {
      issues.push(`已有 LLM 问题 ${llmQuestions.value.length} 条，超过总问题数 ${totalCount} 条，请调大总数、删除多余问题或重新生成`)
    }
    for (const cat of CATEGORY_OPTIONS) {
      const actual = llmActualCategoryCounts.value[cat.code]
      const target = Number(llmPlan.categoryCounts[cat.code] || 0)
      if (actual > target) {
        issues.push(`${cat.label}已有 ${actual} 条，超过目标 ${target} 条，请调大该分类数量、删除多余问题或重新生成`)
      }
    }
  }

  if (showMessage && issues.length) {
    ElMessage.warning(issues[0])
  }
  return issues
}

function validateLlmSubmit(showMessage: boolean) {
  const issues = validateLlmPlanConfig(false)
  if (llmStale.value) {
    issues.push('基础信息已变化，请重新生成或确认继续使用当前问题')
  }
  if (llmQuestions.value.length !== Number(llmPlan.totalCount || 0)) {
    issues.push(`LLM 问题数量需为 ${llmPlan.totalCount} 条，当前 ${llmQuestions.value.length} 条`)
  }
  for (const cat of CATEGORY_OPTIONS) {
    const actual = llmActualCategoryCounts.value[cat.code]
    const expected = Number(llmPlan.categoryCounts[cat.code] || 0)
    if (actual !== expected) {
      issues.push(`${cat.label}需为 ${expected} 条，当前 ${actual} 条`)
    }
  }
  for (const item of llmQuestions.value) {
    const error = llmQuestionError(item)
    if (error) {
      issues.push(`${categoryLabel(item.categoryCode)}问题不合法：${error}`)
      break
    }
  }

  if (showMessage && issues.length) {
    ElMessage.warning(issues[0])
  }
  return issues
}

function llmQuestionError(item: LlmPromptQuestionDraft) {
  const content = item.promptContent.trim()
  if (!content) return '内容不能为空'
  if (content.length > 1000) return '内容最多 1000 字'
  if (item.categoryCode === 'COMPARISON' && !content.includes('{competitor}')) {
    return '必须包含 {competitor}'
  }
  if (item.categoryCode !== 'COMPARISON' && content.includes('{competitor}')) {
    return '不能包含 {competitor}'
  }
  const invalidVar = extractVariables(content).find((name) => !ALLOWED_PROMPT_VARIABLES.has(name))
  if (invalidVar) return `除 {competitor} 外不能包含占位符`
  if ((content.includes('{') || content.includes('}')) && extractVariables(content).length === 0) {
    return '不能包含花括号'
  }
  return ''
}

function extractVariables(content: string) {
  return Array.from(content.matchAll(/\{([a-z_]+)\}/g)).map((match) => match[1])
}

function categoryLabel(code: PresalePromptCategoryCode) {
  return CATEGORY_OPTIONS.find((item) => item.code === code)?.label || code
}

function nextLlmQuestionId() {
  llmQuestionSeq.value += 1
  return `llm-${Date.now()}-${llmQuestionSeq.value}`
}

function buildLlmPromptQuestions(): LlmPromptQuestionDraft[] {
  return llmQuestions.value.map((item) => ({
    categoryCode: item.categoryCode,
    promptContent: item.promptContent.trim()
  }))
}

async function generateLlmQuestions(reset: boolean) {
  if (!formRef.value) return
  const ok = await formRef.value.validate().catch(() => false)
  if (!ok || validateLlmGeneration(reset, true).length) {
    return
  }

  llmGenerating.value = true
  llmLastWarning.value = ''
  try {
    if (reset) {
      llmQuestions.value = []
    }
    const existingQuestions = reset ? [] : buildLlmPromptQuestions()
    const result = await generateLlmPromptQuestions({
      brandName: form.brandName.trim(),
      industry: form.industry,
      industryRole: form.industryRole,
      region: form.region.trim(),
      userType: form.userType?.trim() || undefined,
      userDemand: form.userDemand?.trim() || undefined,
      totalCount: Number(llmPlan.totalCount || 0),
      categoryCounts: { ...llmPlan.categoryCounts },
      existingQuestions
    })
    llmQuestions.value.push(
      ...result.questions.map((item) => ({
        id: nextLlmQuestionId(),
        categoryCode: item.categoryCode,
        promptContent: item.promptContent
      }))
    )
    llmBaseSnapshot.value = currentBaseSnapshot()
    llmStale.value = false
    llmLastWarning.value =
      result.warnings?.[0] ||
      (result.missingTotal > 0 ? `还差 ${result.missingTotal} 条，请手动补齐或重新生成。` : '')
    if (!llmLastWarning.value) {
      ElMessage.success('LLM 问题已生成')
    }
  } catch (err: any) {
    ElMessage.error(err?.message || 'LLM 问题生成失败')
  } finally {
    llmGenerating.value = false
  }
}

function addLlmQuestion(categoryCode: PresalePromptCategoryCode) {
  const brandName = form.brandName.trim() || '目标品牌'
  llmQuestions.value.push({
    id: nextLlmQuestionId(),
    categoryCode,
    promptContent:
      categoryCode === 'COMPARISON'
        ? `${brandName} 与 {competitor} 相比，用户更关注哪些差异？`
        : ''
  })
}

function removeLlmQuestion(id: string) {
  llmQuestions.value = llmQuestions.value.filter((item) => item.id !== id)
}

async function confirmUseStaleLlmQuestions() {
  try {
    await ElMessageBox.confirm(
      '当前 LLM 问题内容不会随基础信息变更自动更新。除对比型的 {competitor} 会由系统动态填充外，品牌名、行业、地区等内容都已写入问题原文。如基础信息有重大变化，建议重新生成。确认继续使用当前问题吗？',
      '确认继续使用当前问题',
      {
        confirmButtonText: '继续使用',
        cancelButtonText: '重新检查',
        type: 'warning'
      }
    )
  } catch {
    return
  }
  llmBaseSnapshot.value = currentBaseSnapshot()
  llmStale.value = false
}

async function onSubmit() {
  if (!formRef.value) return
  const ok = await formRef.value.validate().catch(() => false)
  if (!ok) return
  if (!promptSources.value.length || promptLoadFailed.value) {
    ElMessage.error('Prompt 清单未加载完成')
    return
  }
  if (activePromptTab.value === 'llm' && validateLlmSubmit(true).length) {
    return
  }

  submitting.value = true
  try {
    const payload: CreateReportRequest =
      activePromptTab.value === 'llm'
        ? {
            brandName: form.brandName.trim(),
            industry: form.industry,
            industryRole: form.industryRole,
            region: form.region.trim(),
            userDemand: form.userDemand?.trim() || undefined,
            userType: form.userType?.trim() || undefined,
            promptSourceMode: 'llm',
            llmQuestionPlan: {
              totalCount: Number(llmPlan.totalCount || 0),
              categoryCounts: { ...llmPlan.categoryCounts }
            },
            llmPromptQuestions: buildLlmPromptQuestions()
          }
        : {
            brandName: form.brandName.trim(),
            industry: form.industry,
            industryRole: form.industryRole,
            region: form.region.trim(),
            userDemand: form.userDemand?.trim() || undefined,
            userType: form.userType?.trim() || undefined,
            promptSourceMode: 'template',
            promptTemplateVersion: promptTemplateVersion.value,
            promptTemplates: buildPromptTemplateDrafts()
          }
    const reportId = await createReport({
      ...payload
    })
    ElMessage.success('已创建报告,开始生成')
    router.push(`/admin/presale/report/${reportId}/progress`)
  } catch (err: any) {
    if (err?.data?.errorCode === 'template_version_changed') {
      await handleTemplateVersionChanged()
    } else {
      ElMessage.error(err?.message || '创建失败')
    }
  } finally {
    submitting.value = false
  }
}

async function handleTemplateVersionChanged() {
  try {
    await ElMessageBox.confirm(
      'Prompt 模板版本已更新,请刷新后重新确认 Prompt 清单。',
      '模板版本已更新',
      {
        confirmButtonText: '刷新 Prompt 清单',
        cancelButtonText: '取消提交',
        type: 'warning'
      }
    )
    await loadPromptTemplates()
  } catch {
    // 用户取消提交。
  }
}

async function onCancel() {
  const hasInput =
    form.brandName ||
    form.industry ||
    form.industryRole ||
    form.region ||
    form.userDemand ||
    form.userType ||
    modifiedCount.value > 0 ||
    llmQuestions.value.length > 0
  if (hasInput) {
    try {
      await ElMessageBox.confirm('表单有未保存内容,确定要离开吗?', '提示', {
        confirmButtonText: '离开',
        cancelButtonText: '继续填写',
        type: 'warning'
      })
    } catch {
      return
    }
  }
  router.push('/admin/presale/report')
}

function buildPromptTemplateDrafts(): PromptTemplateDraftRequest[] {
  return promptSources.value.map((source) => {
    const draft = promptDrafts.value.find((item) => item.sourceTemplateId === source.id)
    return {
      sourceTemplateId: source.id,
      promptContent: draft?.promptContent ?? source.promptContent
    }
  })
}

function renderPromptHtml(template: string) {
  const rendered = template
    .split('{brand}').join(form.brandName.trim())
    .split('{product}').join(form.brandName.trim())
    .split('{industry}').join(optionLabel(industryOptions, form.industry))
    .split('{industry_role}').join(optionLabel(allRoleOptions, form.industryRole))
    .split('{region}').join(form.region.trim())
    .split('{user_type}').join(form.userType?.trim() || '__MISSING_USER_TYPE__')
  return escapeHtml(rendered)
    .split('__MISSING_USER_TYPE__').join('<span class="missing-var">&lt;未填写&gt;</span>')
    .split('{competitor}').join('<span class="competitor-var">{competitor}</span>')
}

function escapeHtml(value: string) {
  return value
    .split('&').join('&amp;')
    .split('<').join('&lt;')
    .split('>').join('&gt;')
    .split('"').join('&quot;')
    .split("'").join('&#39;')
}

function scopeNumber(value: number | undefined) {
  if (value == null) return '—'
  return formatInt(value)
}

function formatInt(value: number) {
  return new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 0 }).format(value)
}
</script>

<style scoped>
.presale-report-create {
  max-width: 1180px;
}
.page-header {
  margin-bottom: 0;
}
.page-title {
  margin: 8px 0 0 0;
}
.form-card {
  padding: 0;
}
.form-tip {
  margin-left: 12px;
  color: #909399;
  font-size: 12px;
}
.scope-preview {
  padding: 16px;
  margin: 4px 0 14px;
}
.scope-title {
  font-size: 15px;
  color: #0f172a;
  font-weight: 800;
  margin-bottom: 12px;
}
.scope-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  max-width: 760px;
  margin-bottom: 12px;
}
.scope-item {
  text-align: center;
  padding: 16px;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid #dbeafe;
  border-radius: 12px;
}
.scope-number {
  font-size: 28px;
  font-weight: 800;
  color: #2563eb;
  font-family: 'JetBrains Mono', Consolas, monospace;
}
.scope-label {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
.scope-note {
  color: #909399;
  font-size: 12px;
  margin-top: 8px;
}
.prompt-preview {
  padding: 8px 0 0;
}
.prompt-summary {
  width: 100%;
  min-height: 56px;
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 16px;
  align-items: center;
  padding: 14px 16px;
  border: 1px solid #dbeafe;
  border-radius: 14px;
  background: linear-gradient(135deg, #ffffff, #f8fbff);
  color: #303133;
  cursor: pointer;
  text-align: left;
}
.prompt-summary:hover {
  border-color: #93c5fd;
}
.prompt-summary:focus-visible {
  outline: 2px solid #409eff;
  outline-offset: 2px;
}
.summary-main {
  font-weight: 600;
}
.summary-text {
  color: #606266;
  font-size: 13px;
  line-height: 1.5;
}
.summary-action {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #409eff;
  font-size: 13px;
  user-select: none;
}
.action-text {
  font-weight: 500;
}
.action-icon-wrap {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: rgba(64, 158, 255, 0.08);
  transition: background-color 0.2s ease, transform 0.2s ease;
}
.action-icon {
  font-size: 14px;
  color: #409eff;
  transition: transform 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}
.prompt-summary.is-open .action-icon {
  transform: rotate(180deg);
}
.prompt-summary:hover .action-icon-wrap {
  background: rgba(64, 158, 255, 0.16);
}
.prompt-summary:active .action-icon-wrap {
  transform: scale(0.92);
}
.prompt-alert {
  margin-top: 12px;
}
.prompt-panel {
  margin-top: 16px;
}
.prompt-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.prompt-version {
  color: #606266;
  font-size: 13px;
}
.prompt-tabs {
  margin-top: 4px;
}
.prompt-empty {
  padding: 24px;
  text-align: center;
  color: #909399;
}
.llm-config {
  display: grid;
  grid-template-columns: minmax(180px, 220px) 1fr;
  gap: 16px;
  align-items: start;
  padding: 12px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  background: #f7f9fc;
}
.llm-total,
.llm-category-input,
.llm-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.form-label.compact {
  color: #606266;
  font-size: 13px;
  white-space: nowrap;
}
.llm-category-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(120px, 1fr));
  gap: 10px;
}
.llm-category-input {
  justify-content: space-between;
  min-width: 0;
}
.llm-category-input span {
  color: #606266;
  font-size: 13px;
  white-space: nowrap;
}
.llm-actions {
  flex-wrap: wrap;
  margin-top: 12px;
}
.llm-count-status {
  color: #606266;
  font-size: 13px;
}
.llm-count-status.danger {
  color: #e6a23c;
}
.prompt-group {
  border-top: 1px solid #ebeef5;
  padding-top: 16px;
  margin-top: 16px;
}
.group-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}
.group-title {
  font-size: 15px;
  font-weight: 600;
}
.group-count {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}
.competitor-hint {
  margin-bottom: 10px;
}
.prompt-row {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 14px;
  margin-bottom: 10px;
  background: #fff;
}
.prompt-row.is-modified {
  border-color: #e6a23c;
  background: #fdf6ec;
}
.prompt-row-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}
.prompt-meta,
.prompt-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.prompt-code {
  color: #606266;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12px;
}
.rendered-prompt,
.render-preview {
  color: #303133;
  line-height: 1.7;
  word-break: break-word;
}
.prompt-editor {
  margin-bottom: 8px;
}
.variable-help,
.modified-tip {
  color: #909399;
  font-size: 12px;
  line-height: 1.6;
}
.render-preview {
  margin-top: 8px;
  padding: 10px 12px;
  background: #f5f7fa;
  border-radius: 4px;
  font-size: 13px;
}
.preview-label {
  color: #909399;
  margin-right: 8px;
}
.modified-tip {
  margin-top: 8px;
}
.action-bar {
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid #e4e7ed;
  text-align: right;
}
.submit-source-tip {
  margin-top: 10px;
  color: #909399;
  font-size: 12px;
}
.action-bar .el-button + .el-button {
  margin-left: 12px;
}
:deep(.missing-var) {
  color: #909399;
  font-style: italic;
}
:deep(.competitor-var) {
  color: #409eff;
  font-weight: 600;
}

@media (max-width: 900px) {
  .presale-report-create {
    padding: 12px;
  }
  .scope-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .prompt-summary {
    grid-template-columns: 1fr auto;
  }
  .summary-text {
    grid-column: 1 / -1;
  }
  .llm-config {
    grid-template-columns: 1fr;
  }
  .llm-category-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
