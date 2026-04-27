<template>
  <div class="presale-report-create">
    <div class="page-header">
      <el-breadcrumb separator="/">
        <el-breadcrumb-item :to="{ path: '/admin/presale/report' }">售前报告</el-breadcrumb-item>
        <el-breadcrumb-item>新建报告</el-breadcrumb-item>
      </el-breadcrumb>
      <h2 class="page-title">新建报告</h2>
    </div>

    <el-card shadow="never" class="form-card">
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
      <div class="scope-preview">
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
                size="small"
                :disabled="modifiedCount === 0"
                @click="restoreAllModified"
              >
                全部恢复默认
              </el-button>
            </div>

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
          </template>
        </div>
      </section>

      <div class="action-bar">
        <el-button @click="onCancel">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">
          {{ submitting ? '提交中...' : '提交生成' }}
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
import {
  createReport,
  getReportScopePreview,
  listPromptTemplates,
  type CreateReportRequest,
  type PromptTemplateDraftRequest,
  type PromptTemplateVO,
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

const CATEGORY_ORDER = ['推荐型', '对比型', '问题型', '认知型', '场景型']

const router = useRouter()
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
  } catch {
    promptSources.value = []
    promptDrafts.value = []
    promptLoadFailed.value = true
  } finally {
    promptLoading.value = false
  }
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

async function onSubmit() {
  if (!formRef.value) return
  const ok = await formRef.value.validate().catch(() => false)
  if (!ok) return
  if (!promptSources.value.length || promptLoadFailed.value) {
    ElMessage.error('Prompt 清单未加载完成')
    return
  }

  submitting.value = true
  try {
    const reportId = await createReport({
      brandName: form.brandName.trim(),
      industry: form.industry,
      industryRole: form.industryRole,
      region: form.region.trim(),
      userDemand: form.userDemand?.trim() || undefined,
      userType: form.userType?.trim() || undefined,
      promptTemplateVersion: promptTemplateVersion.value,
      promptTemplates: buildPromptTemplateDrafts()
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
    modifiedCount.value > 0
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
  padding: 16px 24px;
  max-width: 1120px;
}
.page-header {
  margin-bottom: 16px;
}
.page-title {
  margin: 8px 0 0 0;
  font-size: 22px;
  font-weight: 600;
}
.form-card {
  padding: 12px 0;
}
.form-tip {
  margin-left: 12px;
  color: #909399;
  font-size: 12px;
}
.scope-preview {
  padding: 12px 0;
}
.scope-title {
  font-size: 14px;
  color: #606266;
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
  background: #f5f7fa;
  border-radius: 4px;
}
.scope-number {
  font-size: 28px;
  font-weight: 600;
  color: #409eff;
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
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: #fff;
  color: #303133;
  cursor: pointer;
  text-align: left;
}
.prompt-summary:hover {
  border-color: #c6e2ff;
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
.prompt-empty {
  padding: 24px;
  text-align: center;
  color: #909399;
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
}
</style>
