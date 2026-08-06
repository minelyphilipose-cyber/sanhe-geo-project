<template>
  <div class="presale-report-create">
    <header class="report-topbar">
      <div class="report-wrap topbar-inner">
        <div class="logo-dot">
          <el-icon><MagicStick /></el-icon>
        </div>
        <span class="topbar-title">幻境 AI · GEO 诊断平台</span>
        <span class="topbar-spacer"></span>
        <button class="ghost-btn" type="button" @click="onSaveDraft">保存草稿</button>
      </div>
    </header>

    <main class="report-wrap report-main">
      <div class="page-head">
        <nav class="breadcrumb-line">
          <router-link to="/admin/presale/report">AI 可见度诊断报告</router-link>
          <el-icon><ArrowRight /></el-icon>
          <span>新建报告</span>
        </nav>
        <h1 class="page-title">新建报告</h1>
        <p class="page-subtitle">录入品牌基础信息，确认诊断范围与问题模板后提交生成。</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        class="basic-info-form"
      >
        <section class="report-section">
          <div class="section-head">
            <div class="section-ico blue">
              <el-icon><OfficeBuilding /></el-icon>
            </div>
            <div class="section-titles">
              <h2>品牌基础信息</h2>
              <p>用于定位诊断主体与所属行业范围</p>
            </div>
          </div>

          <div class="section-body">
            <div class="form-grid">
              <el-form-item class="form-item span-full" prop="brandName">
                <template #label>
                  <span class="field-label">品牌名称 <span class="req">*</span></span>
                </template>
                <el-input
                  v-model="form.brandName"
                  placeholder="请输入品牌全称"
                  :maxlength="REPORT_BRAND_NAME_MAX_LENGTH"
                  show-word-limit
                />
              </el-form-item>

              <el-form-item class="form-item span-full" prop="brandFormerNames">
                <template #label>
                  <span class="field-label">品牌曾用名 <span class="optional-badge">可选</span></span>
                </template>
                <div class="former-name-inputs">
                  <el-input
                    v-for="(_, index) in form.brandFormerNames"
                    :key="index"
                    v-model="form.brandFormerNames[index]"
                    :placeholder="`曾用名 ${index + 1}`"
                    maxlength="100"
                    show-word-limit
                  />
                </div>
              </el-form-item>

              <el-form-item class="form-item span-full" prop="industry">
                <template #label>
                  <span class="field-label">行业 <span class="req">*</span></span>
                </template>
                <el-select
                  v-model="form.industry"
                  placeholder="选择或输入行业"
                  filterable
                  allow-create
                  default-first-option
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

              <el-form-item class="form-item span-full" prop="industryRole">
                <template #label>
                  <span class="field-label">身份 <span class="req">*</span></span>
                </template>
                <el-select
                  v-model="form.industryRole"
                  placeholder="选择或输入身份"
                  :disabled="!form.industry"
                  filterable
                  allow-create
                  default-first-option
                >
                  <el-option
                    v-for="opt in filteredRoleOptions"
                    :key="opt.value"
                    :label="opt.label"
                    :value="opt.value"
                  />
                </el-select>
              </el-form-item>

              <el-form-item
                v-if="showRepresentedBrands"
                class="form-item span-full"
                prop="representedBrands"
              >
                <template #label>
                  <span class="field-label">代理品牌 <span class="optional-badge">可选，可填写多个</span></span>
                </template>
                <div class="represented-brand-editor">
                  <div
                    v-for="(_, index) in form.representedBrands"
                    :key="index"
                    class="represented-brand-row"
                  >
                    <el-input
                      v-model="form.representedBrands[index]"
                      :placeholder="`代理品牌 ${index + 1}`"
                      maxlength="100"
                      show-word-limit
                    />
                    <el-button
                      text
                      type="danger"
                      aria-label="删除代理品牌"
                      @click="removeRepresentedBrand(index)"
                    >删除</el-button>
                  </div>
                  <el-button
                    v-if="form.representedBrands.length < MAX_REPRESENTED_BRANDS"
                    class="represented-brand-add"
                    plain
                    @click="addRepresentedBrand"
                  >+ 添加代理品牌</el-button>
                  <div class="field-help">
                    仅用于区分客户主体与其代理的上游品牌，不会被计作目标品牌提及或竞品。
                  </div>
                </div>
              </el-form-item>

              <el-form-item class="form-item span-full" prop="region">
                <template #label>
                  <span class="field-label">地区 <span class="req">*</span></span>
                </template>
                <el-input
                  v-model="form.region"
                  placeholder="如:全国"
                  :maxlength="REPORT_MARKET_LABEL_PAIR_MAX_LENGTH"
                />
                <div class="field-counter">地区与行业 {{ marketLabelPairLength }} / {{ REPORT_MARKET_LABEL_PAIR_MAX_LENGTH }}</div>
              </el-form-item>

              <el-form-item class="form-item span-full" prop="userType">
                <template #label>
                  <span class="field-label">目标用户 <span class="optional-badge">可选</span></span>
                </template>
                <el-input
                  v-model="form.userType"
                  placeholder="可选，如:商务宴请人群、年轻消费者"
                  maxlength="50"
                  show-word-limit
                />
              </el-form-item>
            </div>
          </div>
        </section>

        <section class="report-section">
          <div class="section-head">
            <div class="section-ico teal">
              <el-icon><Aim /></el-icon>
            </div>
            <div class="section-titles">
              <h2>诊断目标</h2>
              <p>明确诉求与对比范围，让结论更贴合需求</p>
            </div>
          </div>

          <div class="section-body">
            <div class="form-grid">
              <el-form-item class="form-item span-full" prop="userDemand">
                <template #label>
                  <span class="field-label">客户诉求 <span class="optional-badge">可选</span></span>
                </template>
                <el-input
                  v-model="form.userDemand"
                  type="textarea"
                  :rows="4"
                  placeholder="可选，最多 500 字"
                  maxlength="500"
                  show-word-limit
                />
              </el-form-item>

              <el-form-item class="form-item span-full" prop="specifiedCompetitors">
                <template #label>
                  <span class="field-label">指定竞品 <span class="optional-badge">可选</span></span>
                </template>
                <div class="competitor-inputs">
                  <el-input
                    v-for="(_, index) in form.specifiedCompetitors"
                    :key="index"
                    v-model="form.specifiedCompetitors[index]"
                    :placeholder="`竞品 ${index + 1}`"
                    :maxlength="REPORT_COMPETITOR_GROUP_MAX_LENGTH"
                  />
                  <div
                    class="field-counter"
                    :class="{ 'is-error': specifiedCompetitorGroupLength > REPORT_COMPETITOR_GROUP_MAX_LENGTH }"
                  >
                    竞品合计 {{ specifiedCompetitorGroupLength }} / {{ REPORT_COMPETITOR_GROUP_MAX_LENGTH }}
                  </div>
                </div>
              </el-form-item>
            </div>
          </div>
        </section>
      </el-form>

      <section class="report-section">
        <div class="section-head">
          <div class="section-ico amber">
            <el-icon><TrendCharts /></el-icon>
          </div>
          <div class="section-titles">
            <h2>诊断范围预览</h2>
            <p>按当前配置预估的覆盖规模</p>
          </div>
        </div>

        <div class="section-body">
          <div class="scope-grid">
            <div class="scope-item">
              <div class="scope-icon"><el-icon><Monitor /></el-icon></div>
              <div class="scope-number">{{ scopeNumber(effectiveScopePreview?.platformCount) }}</div>
              <div class="scope-label">AI 平台</div>
            </div>
            <div class="scope-item">
              <div class="scope-icon"><el-icon><Search /></el-icon></div>
              <div class="scope-number">{{ scopeNumber(effectiveScopePreview?.promptQueryCount) }}</div>
              <div class="scope-label">Prompt 查询</div>
            </div>
            <div class="scope-item">
              <div class="scope-icon"><el-icon><Promotion /></el-icon></div>
              <div class="scope-number">{{ scopeNumber(effectiveScopePreview?.llmCallUpperBound) }}</div>
              <div class="scope-label">最多 LLM 调用</div>
            </div>
            <div class="scope-item">
              <div class="scope-icon"><el-icon><Star /></el-icon></div>
              <div class="scope-number">{{ scopeNumber(effectiveScopePreview?.dimensionCount) }}</div>
              <div class="scope-label">分析维度</div>
            </div>
          </div>
          <div class="scope-note">
            <el-icon><InfoFilled /></el-icon>
            <span>{{ scopeNote }}</span>
          </div>
        </div>

        <div class="prompt-preview">
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
                      <div v-if="templatePromptItemError(item)" class="validation-error">
                        {{ templatePromptItemError(item) }}
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

                  <div
                    v-for="item in llmQuestionsByCategory[cat.code]"
                    :key="item.id"
                    class="prompt-row"
                    :class="{ 'is-modified': llmQuestionError(item) }"
                  >
                    <div class="prompt-row-head">
                      <div class="prompt-meta">
                        <span class="prompt-code">LLM_{{ cat.code }}</span>
                        <el-tag v-if="llmQuestionError(item)" size="small" type="danger">
                          {{ llmQuestionError(item) }}
                        </el-tag>
                        <el-tag v-else-if="llmQuestionWarning(item)" size="small" type="warning">
                          {{ llmQuestionWarning(item) }}
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
        </div>
      </section>
    </main>

    <footer class="action-bar">
      <div class="report-wrap action-inner">
        <span class="template-version">模板版本 <b>{{ promptTemplateVersion || 'v3' }}</b></span>
        <span class="action-spacer"></span>
        <button class="ghost-btn" type="button" @click="onCancel">取消</button>
        <el-button class="submit-btn" type="primary" :loading="submitting" @click="onSubmit">
          <el-icon v-if="!submitting"><Check /></el-icon>
          {{ submitting ? '提交中...' : '提交生成' }}
        </el-button>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  Aim,
  ArrowDown,
  ArrowRight,
  Check,
  InfoFilled,
  MagicStick,
  Monitor,
  OfficeBuilding,
  Promotion,
  Search,
  Star,
  TrendCharts
} from '@element-plus/icons-vue'
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
import { createIdempotencyKey } from '@/utils/idempotency'
import {
  countMarketLabelPair,
  REPORT_MARKET_INDUSTRY_LABEL_MAX_LENGTH,
  REPORT_MARKET_LABEL_PAIR_MAX_LENGTH,
  resolveMarketIndustryLabel
} from './presaleMarketInput'
import {
  competitorGroupLength,
  REPORT_BRAND_NAME_MAX_LENGTH,
  REPORT_COMPETITOR_GROUP_MAX_LENGTH,
  REPORT_INDUSTRY_ROLE_MAX_LENGTH,
  supportsRepresentedBrands,
  templatePromptError
} from './presaleReportValidation'

interface PromptDraftItem {
  sourceTemplateId: number
  promptContent: string
}

interface PromptItem {
  source: PromptTemplateVO
  draft: PromptDraftItem
}

type CreateReportForm = Omit<CreateReportRequest, 'specifiedCompetitors' | 'representedBrands'> & {
  brandFormerNames: string[]
  representedBrands: string[]
  specifiedCompetitors: string[]
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
const DEFAULT_LLM_CATEGORY_COUNTS: Record<PresalePromptCategoryCode, number> = {
  RECOMMENDATION: 10,
  COMPARISON: 5,
  PROBLEM: 5,
  COGNITIVE: 5,
  SCENARIO: 5
}
const ALLOWED_PROMPT_VARIABLES = new Set([
  'competitor'
])
const MAX_LLM_TOTAL_COUNT = 60
const MAX_LLM_CATEGORY_COUNT = 30
const MIN_LLM_COGNITIVE_COUNT = 3
const MAX_LLM_EXISTING_QUESTIONS = 80
const MAX_REPRESENTED_BRANDS = 10

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
let pendingCreateRequest: { payloadSignature: string; requestId: string } | null = null
const DEFAULT_USER_DEMAND = '了解品牌在AI搜索中的真实表现。'
const llmPlan = reactive<LlmPromptQuestionPlan>({
  totalCount: 0,
  categoryCounts: emptyCategoryCounts()
})

const form = reactive<CreateReportForm>({
  brandName: '',
  brandFormerNames: ['', '', ''],
  industry: '',
  industryRole: '',
  representedBrands: [],
  region: '',
  userDemand: DEFAULT_USER_DEMAND,
  userType: '',
  specifiedCompetitors: ['', '', ''],
  promptTemplateVersion: '',
  promptTemplates: []
})

function validateSpecifiedCompetitors(_: unknown, value: string[] | undefined, callback: (error?: Error) => void) {
  const values = normalizeSpecifiedCompetitorInputs(value)
  if (values.length === 0) {
    callback()
    return
  }
  if (values.length !== 3) {
    callback(new Error('指定竞品必须为空或填满 3 个'))
    return
  }
  if (competitorGroupLength(values) > REPORT_COMPETITOR_GROUP_MAX_LENGTH) {
    callback(new Error(`3 个竞品拼接后总长度不能超过 ${REPORT_COMPETITOR_GROUP_MAX_LENGTH} 字`))
    return
  }
  const normalizedBrand = normalizeCompetitorName(form.brandName)
  const normalizedFormerNames = new Set(
    normalizeBrandFormerNameInputs(form.brandFormerNames).map(normalizeCompetitorName)
  )
  const normalizedRepresentedBrands = new Set(
    normalizeRepresentedBrandInputs(form.representedBrands).map(normalizeCompetitorName)
  )
  const dedup = new Set<string>()
  for (const item of values) {
    const normalized = normalizeCompetitorName(item)
    if (normalized === normalizedBrand || normalizedFormerNames.has(normalized) || normalizedRepresentedBrands.has(normalized)) {
      callback(new Error('指定竞品不能与品牌名称、曾用名或代理品牌相同'))
      return
    }
    if (dedup.has(normalized)) {
      callback(new Error('指定竞品不能重复'))
      return
    }
    dedup.add(normalized)
  }
  callback()
}

function validateRepresentedBrands(_: unknown, value: string[] | undefined, callback: (error?: Error) => void) {
  const values = normalizeRepresentedBrandInputs(value)
  if (values.length > MAX_REPRESENTED_BRANDS) {
    callback(new Error(`代理品牌最多 ${MAX_REPRESENTED_BRANDS} 个`))
    return
  }
  const normalizedBrand = normalizeCompetitorName(form.brandName)
  const dedup = new Set<string>()
  for (const item of values) {
    const normalized = normalizeCompetitorName(item)
    if (normalized === normalizedBrand) {
      callback(new Error('代理品牌不能与目标品牌相同'))
      return
    }
    if (dedup.has(normalized)) {
      callback(new Error('代理品牌不能重复'))
      return
    }
    dedup.add(normalized)
  }
  callback()
}

function validateBrandFormerNames(_: unknown, value: string[] | undefined, callback: (error?: Error) => void) {
  const values = normalizeBrandFormerNameInputs(value)
  if (values.length > 3) {
    callback(new Error('品牌曾用名最多 3 个'))
    return
  }
  const normalizedBrand = normalizeCompetitorName(form.brandName)
  const dedup = new Set<string>()
  for (const item of values) {
    const normalized = normalizeCompetitorName(item)
    if (normalized === normalizedBrand) {
      callback(new Error('品牌曾用名不能与品牌名称相同'))
      return
    }
    if (dedup.has(normalized)) {
      callback(new Error('品牌曾用名不能重复'))
      return
    }
    dedup.add(normalized)
  }
  callback()
}

function marketIndustryLabel(value: string | undefined) {
  return resolveMarketIndustryLabel(value, industryOptions)
}

function validateMarketIndustry(_: unknown, value: string | undefined, callback: (error?: Error) => void) {
  const industryLabel = marketIndustryLabel(value)
  if (industryLabel.length > REPORT_MARKET_INDUSTRY_LABEL_MAX_LENGTH) {
    callback(new Error(`行业名称过长，请控制在 ${REPORT_MARKET_INDUSTRY_LABEL_MAX_LENGTH} 字以内`))
    return
  }
  validateMarketLabelPair(_, value, callback)
}

function validateMarketLabelPair(_: unknown, __: string | undefined, callback: (error?: Error) => void) {
  const region = form.region.trim()
  const industryLabel = marketIndustryLabel(form.industry)
  if (region && industryLabel && region.length + industryLabel.length > REPORT_MARKET_LABEL_PAIR_MAX_LENGTH) {
    callback(new Error(`地区与行业合计最多 ${REPORT_MARKET_LABEL_PAIR_MAX_LENGTH} 字，请使用短名称`))
    return
  }
  callback()
}

const rules: FormRules = {
  brandName: [
    { required: true, message: '品牌名不能为空', trigger: 'blur' },
    { max: REPORT_BRAND_NAME_MAX_LENGTH, message: `品牌名最多 ${REPORT_BRAND_NAME_MAX_LENGTH} 字`, trigger: 'blur' }
  ],
  brandFormerNames: [{ validator: validateBrandFormerNames, trigger: 'blur' }],
  industry: [
    { required: true, message: '请选择行业', trigger: 'change' },
    { validator: validateMarketIndustry, trigger: 'change' }
  ],
  industryRole: [
    { required: true, message: '请选择身份', trigger: 'change' },
    { max: REPORT_INDUSTRY_ROLE_MAX_LENGTH, message: `身份最多 ${REPORT_INDUSTRY_ROLE_MAX_LENGTH} 字`, trigger: 'change' }
  ],
  representedBrands: [{ validator: validateRepresentedBrands, trigger: 'blur' }],
  region: [
    { required: true, message: '请输入地区', trigger: 'blur' },
    { validator: validateMarketLabelPair, trigger: 'blur' }
  ],
  userType: [{ max: 50, message: '目标用户最多 50 字', trigger: 'blur' }],
  userDemand: [{ max: 500, message: '客户诉求最多 500 字', trigger: 'blur' }],
  specifiedCompetitors: [{ validator: validateSpecifiedCompetitors, trigger: 'blur' }]
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

const marketLabelPairLength = computed(() =>
  countMarketLabelPair(form.region, marketIndustryLabel(form.industry))
)

const specifiedCompetitorGroupLength = computed(() =>
  competitorGroupLength(form.specifiedCompetitors)
)

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

const showRepresentedBrands = computed(() => {
  return supportsRepresentedBrands(
    form.industryRole,
    optionLabel(allRoleOptions, form.industryRole)
  )
})

watch(showRepresentedBrands, (visible) => {
  if (visible) {
    if (form.representedBrands.length === 0) {
      form.representedBrands = ['']
    }
    return
  }
  form.representedBrands = []
  formRef.value?.clearValidate('representedBrands')
}, { immediate: true })

function optionLabel(options: Array<{ value: string; label: string }>, value: string) {
  if (!value) {
    return ''
  }
  return options.find((item) => item.value === value)?.label || value
}

function normalizeSpecifiedCompetitorInputs(value: string[] | undefined): string[] {
  return (value || [])
    .map((item) => item.trim())
    .filter(Boolean)
}

function normalizeBrandFormerNameInputs(value: string[] | undefined): string[] {
  return (value || [])
    .map((item) => item.trim())
    .filter(Boolean)
}

function normalizeRepresentedBrandInputs(value: string[] | undefined): string[] {
  return (value || [])
    .map((item) => item.trim())
    .filter(Boolean)
}

function addRepresentedBrand() {
  if (form.representedBrands.length < MAX_REPRESENTED_BRANDS) {
    form.representedBrands.push('')
  }
}

function removeRepresentedBrand(index: number) {
  form.representedBrands.splice(index, 1)
  if (form.representedBrands.length === 0) {
    form.representedBrands.push('')
  }
}

function normalizeCompetitorName(value: string | undefined): string {
  return (value || '').trim().replace(/\s+/g, '').toLowerCase()
}

function specifiedCompetitorsForSubmit(): string[] | undefined {
  const values = normalizeSpecifiedCompetitorInputs(form.specifiedCompetitors)
  return values.length === 0 ? undefined : values
}

function brandFormerNamesForSubmit(): string[] | undefined {
  const values = normalizeBrandFormerNameInputs(form.brandFormerNames)
  return values.length === 0 ? undefined : values
}

function representedBrandsForSubmit(): string[] | undefined {
  if (!showRepresentedBrands.value) {
    return undefined
  }
  const values = normalizeRepresentedBrandInputs(form.representedBrands)
  return values.length === 0 ? undefined : values
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
      modifiedCount: items.filter((item) => isModified(item)).length
    }
  }).filter((group) => group.items.length > 0)
})

const modifiedCount = computed(() => promptItems.value.filter((item) => isModified(item)).length)

const effectiveScopePreview = computed<ReportScopePreviewVO | null>(() => {
  const base = scopePreview.value
  if (!base || activePromptTab.value !== 'llm') {
    return base
  }
  const competitorPromptCount = Math.max(0, Number(llmPlan.categoryCounts.COMPARISON || 0))
  const totalCount = Math.max(0, Number(llmPlan.totalCount || 0))
  const genericPromptCount = Math.max(0, totalCount - competitorPromptCount)
  const scope = calculatePromptScope(base.platformCount, genericPromptCount, competitorPromptCount)
  return {
    ...base,
    genericPromptCount,
    competitorPromptCount,
    promptQueryCount: totalCount,
    llmCallUpperBound: scope.totalUpperBound
  }
})

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
  if (!effectiveScopePreview.value) return '暂无诊断范围数据。'
  const sourceText = activePromptTab.value === 'llm' ? '当前 LLM 问题配置' : '当前启用模板配置'
  return `按${sourceText}预计最多发起 ${formatInt(effectiveScopePreview.value.llmCallUpperBound)} 次 LLM 调用。生成过程异步进行,提交后会跳到进度页。`
})

const promptSummary = computed(() => {
  if (promptLoading.value) return '正在读取 Prompt 清单...'
  if (promptLoadFailed.value) return 'Prompt 清单读取失败'
  if (activePromptTab.value === 'llm') {
    const status = llmSubmitIssues.value.length ? `，还差 ${llmMissingTotal.value} 条或存在待修正项` : '，数量已匹配'
    return `当前为 LLM 问题预览：${llmQuestions.value.length}/${llmPlan.totalCount} 条${status}。提交时只使用当前 Tab 的问题。`
  }
  if (!promptSources.value.length) return '当前模板问题为空，请切换到 LLM 问题预览生成问题。'
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
  form.brandFormerNames = draft.brandFormerNames?.length
    ? [...draft.brandFormerNames, '', '', ''].slice(0, 3)
    : ['', '', '']
  form.industry = draft.industry || ''
  form.industryRole = draft.industryRole || ''
  form.representedBrands = draft.representedBrands?.length
    ? [...draft.representedBrands]
    : (showRepresentedBrands.value ? [''] : [])
  form.region = draft.region || ''
  form.userDemand = draft.userDemand || DEFAULT_USER_DEMAND
  form.userType = draft.userType || ''
  form.specifiedCompetitors = draft.specifiedCompetitors?.length
    ? [...draft.specifiedCompetitors, '', '', ''].slice(0, 3)
    : ['', '', '']

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
  formRef.value?.validateField(['industry', 'region'])
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
  const hasTemplatePlan = promptSources.value.length > 0
  Object.assign(llmPlan.categoryCounts, hasTemplatePlan ? counts : DEFAULT_LLM_CATEGORY_COUNTS)
  llmPlan.totalCount = hasTemplatePlan
    ? promptSources.value.length
    : Object.values(DEFAULT_LLM_CATEGORY_COUNTS).reduce((sum, count) => sum + count, 0)
  if (!hasTemplatePlan) {
    activePromptTab.value = 'llm'
  }
}

function currentBaseSnapshot() {
  return JSON.stringify({
    brandName: form.brandName.trim(),
    industry: form.industry,
    industryRole: form.industryRole,
    representedBrands: normalizeRepresentedBrandInputs(form.representedBrands),
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

function templatePromptItemError(item: PromptItem) {
  return templatePromptError(item.draft.promptContent, item.source.hasCompetitorVar)
}

function validateTemplateSubmit(showMessage: boolean) {
  const issues: string[] = []
  for (const item of promptItems.value) {
    const error = templatePromptItemError(item)
    if (error) {
      issues.push(`${item.source.promptCode} 不合法：${error}`)
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

function llmQuestionWarning(item: LlmQuestionDraftItem) {
  const content = item.promptContent.trim()
  if (!content) return ''
  if (llmQuestions.value.some((candidate) => candidate.id !== item.id
    && candidate.categoryCode === item.categoryCode
    && candidate.promptContent.trim() === content)) {
    return '建议合并或修改重复问题'
  }
  if (!containsConfiguredRegion(content)) return `建议包含地域：${form.region.trim()}`
  const effectiveLength = content.length - (item.categoryCode === 'COMPARISON' ? Math.max(0, '{competitor}'.length - 4) : 0)
  const maxLength = item.categoryCode === 'SCENARIO' ? 30 : 25
  if (effectiveLength > maxLength) return `建议控制在 ${maxLength} 字内，当前 ${effectiveLength} 字`
  if (!['COGNITIVE', 'COMPARISON'].includes(item.categoryCode)) {
    const forbidden = [form.brandName.trim(), ...normalizeRepresentedBrandInputs(form.representedBrands)].filter(Boolean)
    const matched = forbidden.find((name) => content.includes(name))
    if (matched) return `建议不要直接出现品牌：${matched}`
  }
  return !/[？?]$/.test(content) ? '建议使用自然问句并以问号结尾' : ''
}

function containsConfiguredRegion(content: string) {
  const region = form.region.trim().replace(/\s+/g, '')
  if (!region) return true
  if (content.includes(region)) return true
  const concise = region.split('特别行政区').join('').split('自治区').join('').split('省').join('').split('市').join('')
  return concise.length >= 2 && content.includes(concise)
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
      representedBrands: representedBrandsForSubmit(),
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
  if (activePromptTab.value === 'template' && (!promptSources.value.length || promptLoadFailed.value || promptLoading.value)) {
    ElMessage.error('Prompt 清单未加载完成')
    return
  }
  if (activePromptTab.value === 'template' && validateTemplateSubmit(true).length) {
    return
  }
  if (activePromptTab.value === 'llm' && validateLlmSubmit(true).length) {
    return
  }

  submitting.value = true
  try {
    const specifiedCompetitors = specifiedCompetitorsForSubmit()
    const brandFormerNames = brandFormerNamesForSubmit()
    const representedBrands = representedBrandsForSubmit()
    const payload: CreateReportRequest =
      activePromptTab.value === 'llm'
        ? {
            brandName: form.brandName.trim(),
            brandFormerNames,
            industry: form.industry,
            industryRole: form.industryRole,
            representedBrands,
            region: form.region.trim(),
            userDemand: form.userDemand?.trim() || undefined,
            userType: form.userType?.trim() || undefined,
            specifiedCompetitors,
            promptSourceMode: 'llm',
            llmQuestionPlan: {
              totalCount: Number(llmPlan.totalCount || 0),
              categoryCounts: { ...llmPlan.categoryCounts }
            },
            llmPromptQuestions: buildLlmPromptQuestions()
          }
        : {
            brandName: form.brandName.trim(),
            brandFormerNames,
            industry: form.industry,
            industryRole: form.industryRole,
            representedBrands,
            region: form.region.trim(),
            userDemand: form.userDemand?.trim() || undefined,
            userType: form.userType?.trim() || undefined,
            specifiedCompetitors,
            promptSourceMode: 'template',
            promptTemplateVersion: promptTemplateVersion.value,
            promptTemplates: buildPromptTemplateDrafts()
          }
    const payloadSignature = JSON.stringify(payload)
    if (!pendingCreateRequest || pendingCreateRequest.payloadSignature !== payloadSignature) {
      pendingCreateRequest = {
        payloadSignature,
        requestId: createIdempotencyKey('presale-report')
      }
    }
    const reportId = await createReport({ ...payload, requestId: pendingCreateRequest.requestId })
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

function onSaveDraft() {
  ElMessage.info('保存草稿功能待接入')
}

async function onCancel() {
  const hasInput =
    form.brandName ||
    normalizeBrandFormerNameInputs(form.brandFormerNames).length > 0 ||
    form.industry ||
    form.industryRole ||
    normalizeRepresentedBrandInputs(form.representedBrands).length > 0 ||
    form.region ||
    (form.userDemand && form.userDemand !== DEFAULT_USER_DEMAND) ||
    form.userType ||
    normalizeSpecifiedCompetitorInputs(form.specifiedCompetitors).length > 0 ||
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
  --brand-1: #2f6df6;
  --brand-2: #16b8a6;
  --brand-grad: linear-gradient(120deg, #2f6df6 0%, #16b8a6 100%);
  --accent-soft: #eaf1ff;
  --accent-ring: rgba(47, 109, 246, 0.16);
  --text: #1a2233;
  --text-2: #5a6478;
  --text-3: #98a1b3;
  --border: #e7ebf2;
  --border-strong: #d7dde8;
  min-height: calc(100vh - 24px);
  padding-bottom: 84px;
  background: #f4f6fb;
  color: var(--text);
  font-size: 14px;
}
.report-wrap {
  width: min(100%, 980px);
  margin: 0;
  padding: 0 18px;
}
.report-topbar {
  position: relative;
  z-index: 40;
  background: #ffffff;
  border-bottom: 1px solid var(--border);
  border-radius: 10px 10px 0 0;
}
.topbar-inner {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 56px;
}
.logo-dot {
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  flex: none;
  border-radius: 8px;
  color: #ffffff;
  background: var(--brand-grad);
  box-shadow: 0 2px 8px rgba(47, 109, 246, 0.3);
}
.topbar-title {
  color: #111827;
  font-size: 15px;
  font-weight: 800;
}
.topbar-spacer,
.action-spacer {
  flex: 1;
}
.ghost-btn {
  min-height: 34px;
  padding: 0 14px;
  border: 1px solid var(--border-strong);
  border-radius: 8px;
  background: #ffffff;
  color: var(--text-2);
  cursor: pointer;
  font-size: 13px;
  font-weight: 700;
  transition: border-color 0.15s ease, color 0.15s ease, background 0.15s ease;
}
.ghost-btn:hover {
  border-color: var(--brand-1);
  background: var(--accent-soft);
  color: var(--brand-1);
}
.report-main {
  padding-top: 22px;
}
.page-head {
  margin-bottom: 18px;
}
.breadcrumb-line {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  color: var(--text-3);
  font-size: 12px;
  font-weight: 600;
}
.breadcrumb-line a {
  color: var(--text-3);
  text-decoration: none;
}
.breadcrumb-line span {
  color: var(--text-2);
}
.page-title {
  margin: 0;
  color: #101828;
  font-size: 24px;
  font-weight: 900;
  line-height: 1.2;
}
.page-subtitle {
  margin: 6px 0 0;
  color: var(--text-2);
  font-size: 14px;
}
.report-section {
  overflow: hidden;
  margin-bottom: 18px;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: #ffffff;
  box-shadow: 0 1px 2px rgba(20, 30, 55, 0.04), 0 1px 3px rgba(20, 30, 55, 0.05);
}
.section-head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 18px 24px;
  border-bottom: 1px solid var(--border);
  background: #fafbfd;
}
.section-ico {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  flex: none;
  border-radius: 8px;
  color: #ffffff;
  font-size: 17px;
}
.section-ico.blue {
  background: linear-gradient(135deg, #3f7bff, #2f6df6);
}
.section-ico.teal {
  background: linear-gradient(135deg, #1fc9b6, #12a594);
}
.section-ico.amber {
  background: linear-gradient(135deg, #ffb245, #f59e0b);
}
.section-titles h2 {
  margin: 0;
  color: #111827;
  font-size: 15px;
  font-weight: 800;
}
.section-titles p {
  margin: 2px 0 0;
  color: var(--text-3);
  font-size: 12px;
}
.section-body {
  padding: 22px 24px 24px;
}
.basic-info-form {
  width: 100%;
}
.form-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 18px;
}
.form-item {
  min-width: 0;
  margin-bottom: 0;
}
.span-full {
  grid-column: 1 / -1;
}
.field-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--text);
  font-size: 13px;
  font-weight: 800;
}
.req {
  color: #e5484d;
  font-weight: 800;
}
.optional-badge {
  padding: 1px 8px;
  border-radius: 999px;
  background: #f1f3f8;
  color: var(--text-3);
  font-size: 11px;
  font-weight: 600;
}
.basic-info-form :deep(.el-form-item__label) {
  padding-bottom: 8px;
  line-height: 1.2;
}
.basic-info-form :deep(.el-form-item.is-required:not(.is-no-asterisk).asterisk-left > .el-form-item__label::before) {
  display: none;
}
.basic-info-form :deep(.el-input),
.basic-info-form :deep(.el-select),
.basic-info-form :deep(.el-textarea) {
  width: 100%;
}
.basic-info-form :deep(.el-input__wrapper),
.basic-info-form :deep(.el-select__wrapper) {
  min-height: 44px;
  padding: 0 14px;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 0 0 1.5px var(--border-strong) inset;
  transition: box-shadow 0.15s ease, background 0.15s ease;
}
.basic-info-form :deep(.el-input__wrapper:hover),
.basic-info-form :deep(.el-select__wrapper:hover) {
  box-shadow: 0 0 0 1.5px #c2cad8 inset;
}
.basic-info-form :deep(.el-input__wrapper.is-focus),
.basic-info-form :deep(.el-select__wrapper.is-focused) {
  box-shadow: 0 0 0 1.5px var(--brand-1) inset, 0 0 0 4px var(--accent-ring);
}
.basic-info-form :deep(.el-textarea__inner) {
  min-height: 104px !important;
  padding: 12px 14px;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 0 0 1.5px var(--border-strong) inset;
  line-height: 1.65;
  resize: vertical;
}
.basic-info-form :deep(.el-textarea__inner:hover) {
  box-shadow: 0 0 0 1.5px #c2cad8 inset;
}
.basic-info-form :deep(.el-textarea__inner:focus) {
  box-shadow: 0 0 0 1.5px var(--brand-1) inset, 0 0 0 4px var(--accent-ring);
}
.basic-info-form :deep(.el-input__count),
.basic-info-form :deep(.el-textarea .el-input__count) {
  color: var(--text-3);
  background: transparent;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}
.validation-error {
  margin-top: 6px;
  color: var(--el-color-danger);
  font-size: 12px;
  line-height: 1.5;
}
.field-counter {
  grid-column: 1 / -1;
  width: 100%;
  margin-top: 4px;
  color: var(--text-3);
  font-size: 12px;
  line-height: 1;
  text-align: right;
  font-variant-numeric: tabular-nums;
}
.field-counter.is-error {
  color: var(--el-color-danger);
}
.competitor-inputs,
.former-name-inputs {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  width: 100%;
}
.represented-brand-editor {
  display: grid;
  gap: 10px;
  width: 100%;
}
.represented-brand-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
}
.represented-brand-add {
  justify-self: start;
}
.field-help {
  color: var(--text-3);
  font-size: 12px;
  line-height: 1.5;
}
.scope-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}
.scope-item {
  position: relative;
  overflow: hidden;
  min-height: 126px;
  padding: 16px 16px 14px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: #fafbfd;
}
.scope-item::before {
  content: "";
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: var(--brand-grad);
}
.scope-icon {
  width: 26px;
  height: 26px;
  display: grid;
  place-items: center;
  margin-bottom: 12px;
  border-radius: 8px;
  background: var(--accent-soft);
  color: var(--brand-1);
  font-size: 15px;
}
.scope-number {
  color: #111827;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 28px;
  font-weight: 900;
  line-height: 1;
}
.scope-label {
  margin-top: 6px;
  color: var(--text-2);
  font-size: 12px;
}
.scope-note {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-top: 16px;
  padding: 12px 14px;
  border: 1px solid #d8e4ff;
  border-radius: 8px;
  background: var(--accent-soft);
  color: #2a4a8f;
  font-size: 12px;
  line-height: 1.6;
}
.scope-note .el-icon {
  margin-top: 3px;
  flex: none;
}
.prompt-preview {
  padding: 0 24px 24px;
}
.prompt-summary {
  width: 100%;
  min-height: 56px;
  display: grid;
  grid-template-columns: auto auto 1fr auto;
  gap: 12px;
  align-items: center;
  padding: 14px 16px;
  border: 1px dashed var(--border-strong);
  border-radius: 8px;
  background: #fafbfd;
  color: var(--text);
  cursor: pointer;
  text-align: left;
  transition: background 0.15s ease, border-color 0.15s ease;
}
.prompt-summary:hover {
  border-color: #c2cad8;
  background: #f2f5fa;
}
.prompt-summary:focus-visible {
  outline: 2px solid var(--brand-1);
  outline-offset: 2px;
}
.summary-main {
  font-size: 14px;
  font-weight: 800;
}
.summary-text {
  color: var(--text-3);
  font-size: 13px;
  line-height: 1.5;
}
.summary-action {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--brand-1);
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
  background: var(--accent-soft);
  transition: background-color 0.2s ease, transform 0.2s ease;
}
.action-icon {
  font-size: 14px;
  color: var(--brand-1);
  transition: transform 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}
.prompt-summary.is-open .action-icon {
  transform: rotate(180deg);
}
.prompt-summary:hover .action-icon-wrap {
  background: #dfe9ff;
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
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 50;
  border-top: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.88);
  box-shadow: 0 -2px 16px rgba(20, 30, 55, 0.05);
  backdrop-filter: blur(10px);
}
.action-inner {
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 62px;
}
.template-version {
  color: var(--text-3);
  font-size: 12px;
}
.template-version b {
  color: var(--text-2);
  font-weight: 800;
}
.submit-btn {
  min-height: 38px;
  padding: 0 22px;
  border: none;
  border-radius: 8px;
  background: var(--brand-grad);
  box-shadow: 0 4px 14px rgba(47, 109, 246, 0.32);
  font-size: 13px;
  font-weight: 800;
}
.submit-btn:hover,
.submit-btn:focus {
  border: none;
  background: var(--brand-grad);
  filter: brightness(1.05);
  box-shadow: 0 6px 20px rgba(47, 109, 246, 0.4);
}
.submit-btn :deep(.el-icon) {
  margin-right: 6px;
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
  .report-main {
    padding-top: 18px;
  }
  .section-head,
  .section-body,
  .prompt-preview {
    padding-left: 18px;
    padding-right: 18px;
  }
  .page-title {
    font-size: 22px;
  }
  .former-name-inputs,
  .competitor-inputs {
    grid-template-columns: 1fr;
  }
  .scope-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .prompt-summary {
    grid-template-columns: auto 1fr auto;
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

@media (max-width: 620px) {
  .topbar-title {
    font-size: 15px;
  }
  .scope-grid {
    grid-template-columns: 1fr;
  }
  .action-inner {
    gap: 10px;
  }
  .template-version {
    display: none;
  }
  .ghost-btn,
  .submit-btn {
    padding-left: 14px;
    padding-right: 14px;
  }
}
</style>
