<template>
  <div class="keyword-expander-page">
    <div class="page-header">
      <div>
        <h2 class="page-title">关键词拓词管理</h2>
      </div>
      <span v-if="editingId" class="editing-tip">当前编辑：{{ form.name || '未命名' }}</span>
    </div>

    <div class="form-grid">
      <div class="form-section">
        <label class="form-label required">客户</label>
        <el-select v-model="form.companyId" class="form-input" filterable placeholder="请选择客户">
          <el-option v-for="item in companyOptions" :key="item.id" :label="item.companyName" :value="item.id" />
        </el-select>
      </div>
      <div class="form-section">
        <label class="form-label required">关键词组名称</label>
        <input v-model="form.name" class="form-input" type="text" maxlength="64" placeholder="请输入词组名称" />
      </div>
    </div>

    <div class="form-section">
      <label class="form-label required">类型</label>
      <KeywordTypeSelector v-model="form.type" :options="typeSelectorOptions" @change-by-user="handleUserTypeChange" />
    </div>

    <div v-if="currentTypeConfig.functionIndustryRequired" class="form-section">
      <label class="form-label required">行业</label>
      <el-select v-model="form.functionIndustryTag" class="form-input" placeholder="请选择行业" @change="handleFunctionIndustryTagChange">
        <el-option v-for="item in functionIndustryOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
    </div>

    <div class="column-builder">
      <div class="preview-bar">
        <div class="estimate-text">
          预计生成 <strong>{{ estimatedCount }}</strong> 条 = 组合 {{ cartesianEstimatedCount }} 条 + AI 问题 {{ form.llmQuestions.length }} 条
          <span v-if="estimatedCount > limit" class="over-limit">{{ overLimitText }}</span>
        </div>
        <div class="preview-actions-inline">
          <el-input-number v-model="form.previewCount" :min="1" :max="1000" size="small" />
          <button class="btn-preview" :disabled="estimatedCount === 0 || estimatedCount > limit || previewing" @click="doPreview">
            {{ previewing ? '预览中...' : '预览拓词' }}
          </button>
        </div>
      </div>

      <CompareKeywordBuilder
        v-if="currentTypeConfig.structure === 'compare'"
        :form="form"
        :options="wordOptions"
      />
      <KeywordColumnBuilder
        v-else
        :form="form"
        :type-config="currentTypeConfig"
        :options="wordOptions"
      />
    </div>

    <LlmQuestionPanel
      :company-id="form.companyId"
      :seed-text="form.llmSeedText"
      :questions="form.llmQuestions"
      :generation-token="form.llmGenerationToken"
      :preview-count="form.previewCount"
      @update:seed-text="form.llmSeedText = $event"
      @update:questions="form.llmQuestions = $event"
      @update:generation-token="form.llmGenerationToken = $event"
    />

    <el-card class="group-list-card">
      <div class="list-toolbar">
        <el-input v-model="query.keyword" clearable placeholder="搜索拓词组" style="width: 240px" @keyup.enter="onSearch" />
        <el-select v-model="query.companyId" clearable filterable placeholder="客户" style="width: 200px" @change="onSearch">
          <el-option v-for="item in companyOptions" :key="item.id" :label="item.companyName" :value="item.id" />
        </el-select>
        <el-select v-model="query.type" clearable placeholder="类型" style="width: 160px" @change="onSearch">
          <el-option v-for="item in newTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-button @click="onSearch">查询</el-button>
      </div>

      <DataState :loading="loading" :empty="!loading && rows.length === 0" empty-text="暂无拓词组">
        <el-table :data="rows" border>
          <el-table-column prop="name" label="拓词组名称" min-width="220" />
          <el-table-column prop="companyName" label="客户" min-width="180" />
          <el-table-column label="类型" width="170">
            <template #default="{ row }">
              <el-tag v-if="row.legacyType" type="info" size="small" class="mr-1">历史</el-tag>
              <span>{{ row.typeLabel || typeLabel(row.type) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="savedKeywordCount" label="关键词数" width="110" />
          <el-table-column prop="updatedAt" label="更新时间" width="180" />
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEdit(row.id)">编辑</el-button>
              <el-button link type="success" @click="openPreviewOnly(row.id)">预览</el-button>
              <el-button link type="danger" @click="remove(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="mt-4 flex justify-end">
          <el-pagination background layout="prev, pager, next, total" :current-page="page.current" :page-size="page.size" :total="page.total" @current-change="onPageChange" />
        </div>
      </DataState>
    </el-card>

    <KeywordPreviewPanel
      v-model:visible="previewVisible"
      :items="previewItems"
      :total-available="previewTotalAvailable"
      :preview-count="form.previewCount"
      :saving="saving"
      @submit="submit"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import DataState from '@/components/ui/DataState.vue'
import KeywordTypeSelector from './keyword-group/KeywordTypeSelector.vue'
import KeywordColumnBuilder from './keyword-group/KeywordColumnBuilder.vue'
import CompareKeywordBuilder from './keyword-group/CompareKeywordBuilder.vue'
import KeywordPreviewPanel from './keyword-group/KeywordPreviewPanel.vue'
import LlmQuestionPanel from './keyword-group/LlmQuestionPanel.vue'
import { createKeywordGroup, deleteKeywordGroup, getKeywordGroupDetail, getKeywordGroupPage, getKeywordGroupTypeConfigs, previewKeywordGroup, updateKeywordGroup } from '@/api/project'
import { getKeywordAffixWordOptions } from '@/api/system'
import { getCompanyList } from '@/api/customer'
import { ERROR_CODE_HINTS, parseErrorCode } from '@/utils/errorCode'
import type { Company, KeywordAffixWordOptionResult, KeywordGroup, KeywordGroupColumns, KeywordGroupPayload, KeywordPreviewItem, KeywordTypeConfig, KeywordTypeOption, KeywordWordItem } from '@/types'
import type { KeywordGroupFormState } from './keyword-group/types'

const limit = 1000

const loading = ref(false)
const previewing = ref(false)
const saving = ref(false)
const previewVisible = ref(false)
const rows = ref<KeywordGroup[]>([])
const previewItems = ref<KeywordPreviewItem[]>([])
const previewTotalAvailable = ref(0)
const companyOptions = ref<Company[]>([])
const typeConfigs = ref<KeywordTypeConfig[]>([])
const editingId = ref<number | null>(null)
const lastEditState = ref<Record<string, Partial<KeywordGroupFormState>>>({})
const hasUserChangedType = ref(false)

const emptyOptions: KeywordAffixWordOptionResult = {
  areaWords: [],
  prefixWords: [],
  suffixWords: [],
  industryWords: [],
  compareWords: [],
  typeOptions: [],
  typeConfigs: [],
  currentTypeConfig: null,
}
const wordOptions = reactive<KeywordAffixWordOptionResult>({ ...emptyOptions })

const page = reactive({ current: 1, size: 20, total: 0 })
const query = reactive<{ keyword: string; companyId?: number; type: string }>({ keyword: '', companyId: undefined, type: '' })

const form = reactive<KeywordGroupFormState>({
  companyId: null,
  projectId: null,
  name: '',
  type: '',
  legacyType: false,
  remark: '',
  previewCount: 100,
  areaEnabled: false,
  functionIndustryTag: '',
  llmSeedText: '',
  llmGenerationToken: '',
  llmQuestions: [],
  areaText: '',
  prefixSystemWords: [],
  prefixCustomText: '',
  coreText: '',
  industryWords: [],
  suffixSystemWords: [],
  suffixCustomText: '',
  coreTextA: '',
  compareWords: [],
  coreTextB: '',
})

const legacyTypeLabels: Record<string, string> = {
  search: '搜索词(历史)',
  location: '地域词(历史)',
  industry: '行业词(历史)',
  competitor: '竞品词(历史)',
}

const legacyStandardConfig: KeywordTypeConfig = {
  type: 'legacy',
  label: '历史类型',
  structure: 'standard',
  areaEnabledByDefault: true,
  industryRequired: false,
  supportsManualAdd: true,
  functionIndustryRequired: false,
  columns: { area: true, prefix: true, core: true, industry: true, suffix: true, compareCore: false, compareWord: false },
  requiredColumns: { area: false, prefix: false, core: true, industry: false, suffix: false, compareCore: false, compareWord: false },
}

const functionIndustryOptions = [
  { value: 'door_window', label: '门窗' },
  { value: 'appliance', label: '家电' },
  { value: 'building_material', label: '建材' },
  { value: 'fmcg', label: '快消' },
  { value: 'industrial', label: '工业品' },
  { value: 'clothing', label: '服装' },
  { value: 'other', label: '其他' },
]

const newTypeOptions = computed<KeywordTypeOption[]>(() => typeConfigs.value.map((item) => ({ value: item.type, label: item.label })))
const typeSelectorOptions = computed<KeywordTypeOption[]>(() => {
  const options = [...newTypeOptions.value]
  if (form.legacyType && form.type && !options.some((item) => item.value === form.type)) {
    options.unshift({ value: form.type, label: legacyTypeLabels[form.type] || `${form.type}(历史)`, legacy: true })
  }
  return options
})

const currentTypeConfig = computed(() => typeConfigs.value.find((item) => item.type === form.type) || legacyStandardConfig)

const estimatedParts = computed(() => {
  const columns = buildColumns()
  if (currentTypeConfig.value.structure === 'compare') {
    return {
      coreA: normalizedLength(columns.coreWordsA),
      compare: normalizedLength(columns.compareWords),
      coreB: normalizedLength(columns.coreWordsB),
      suffix: normalizedLength(columns.suffixWords),
    }
  }
  return {
    area: currentTypeConfig.value.columns.area && form.areaEnabled ? normalizedLength(columns.areaWords) : 1,
    prefix: currentTypeConfig.value.columns.prefix ? normalizedLength(columns.prefixWords) : 1,
    core: currentTypeConfig.value.columns.core ? normalizedLength(columns.coreWords) : 1,
    industry: currentTypeConfig.value.columns.industry ? normalizedLength(columns.industryWords) : 1,
    suffix: currentTypeConfig.value.columns.suffix ? normalizedLength(columns.suffixWords) : 1,
  }
})

const cartesianEstimatedCount = computed(() => Object.values(estimatedParts.value).reduce((acc, n) => acc * n, 1))
const estimatedCount = computed(() => cartesianEstimatedCount.value + form.llmQuestions.length)
const overLimitText = computed(() => {
  if (currentTypeConfig.value.structure === 'compare' && 'coreA' in estimatedParts.value) {
    const p = estimatedParts.value
    return `预计生成 ${estimatedCount.value} 条，其中组合 ${cartesianEstimatedCount.value} 条、AI 问题 ${form.llmQuestions.length} 条，超过 ${limit} 条，请减少任一侧词数`
  }
  return `超过上限 ${limit} 条，请减少选词`
})

function normalizedLength(words: KeywordWordItem[]) {
  return Math.max(1, words.length)
}

function typeLabel(type: string) {
  return typeConfigs.value.find((v) => v.type === type)?.label || legacyTypeLabels[type] || type
}

function parseTextWords(text: string) {
  return dedupText(text.split(/\r?\n/).map((v) => v.trim()).filter(Boolean))
}

function dedupText(words: string[]) {
  const set = new Set<string>()
  const result: string[] = []
  for (const word of words) {
    if (!set.has(word)) {
      set.add(word)
      result.push(word)
    }
  }
  return result
}

function toWordItems(words: string[], source: 'system' | 'custom') {
  return dedupText(words).map((wordText, idx) => ({ wordText, source, sortOrder: (idx + 1) * 10 }))
}

function dedupWordItems(items: KeywordWordItem[]) {
  const seen = new Set<string>()
  const result: KeywordWordItem[] = []
  for (const item of items) {
    if (!seen.has(item.wordText)) {
      seen.add(item.wordText)
      result.push(item)
    }
  }
  return result
}

function buildColumns(): KeywordGroupColumns {
  const config = currentTypeConfig.value
  return {
    areaWords: config.columns.area && form.areaEnabled ? toWordItems(parseTextWords(form.areaText), 'custom') : [],
    prefixWords: config.columns.prefix
      ? dedupWordItems([...toWordItems(form.prefixSystemWords, 'system'), ...toWordItems(parseTextWords(form.prefixCustomText), 'custom')])
      : [],
    coreWords: config.columns.core ? toWordItems(parseTextWords(form.coreText), 'custom') : [],
    industryWords: config.columns.industry ? toWordItems(form.industryWords, 'system') : [],
    suffixWords: config.columns.suffix
      ? dedupWordItems([...toWordItems(form.suffixSystemWords, 'system'), ...toWordItems(parseTextWords(form.suffixCustomText), 'custom')])
      : [],
    coreWordsA: config.columns.compareCore ? toWordItems(parseTextWords(form.coreTextA), 'custom') : [],
    compareWords: config.columns.compareWord ? toWordItems(form.compareWords, 'system') : [],
    coreWordsB: config.columns.compareCore ? toWordItems(parseTextWords(form.coreTextB), 'custom') : [],
  }
}

function buildPayload(): KeywordGroupPayload {
  return {
    companyId: Number(form.companyId),
    projectId: form.projectId,
    name: form.name.trim(),
    type: form.type,
    areaEnabled: form.areaEnabled,
    functionIndustryTag: form.functionIndustryTag || null,
    remark: form.remark.trim() || undefined,
    count: form.previewCount,
    llmGenerationToken: form.llmGenerationToken || undefined,
    llmQuestions: [...form.llmQuestions],
    columns: buildColumns(),
  }
}

function snapshot() {
  return JSON.parse(JSON.stringify(form)) as KeywordGroupFormState
}

function snapshotBaseFields() {
  return {
    companyId: form.companyId,
    projectId: form.projectId,
    name: form.name,
    remark: form.remark,
    previewCount: form.previewCount,
  }
}

function hasTypeRelatedSelections() {
  return Boolean(
    form.areaText.trim()
    || form.prefixSystemWords.length
    || form.prefixCustomText.trim()
    || form.coreText.trim()
    || form.industryWords.length
    || form.suffixSystemWords.length
    || form.suffixCustomText.trim()
    || form.functionIndustryTag
    || form.coreTextA.trim()
    || form.compareWords.length
    || form.coreTextB.trim()
  )
}

function restore(state: Partial<KeywordGroupFormState>) {
  const baseFields = snapshotBaseFields()
  Object.assign(form, JSON.parse(JSON.stringify(state)), baseFields)
}

function resetPreview() {
  previewItems.value = []
  previewTotalAvailable.value = 0
  previewVisible.value = false
}

function clearTypeRelatedSelections() {
  form.areaEnabled = currentTypeConfig.value.areaEnabledByDefault
  form.functionIndustryTag = ''
  form.areaText = ''
  form.prefixSystemWords = []
  form.prefixCustomText = ''
  form.coreText = ''
  form.industryWords = []
  form.suffixSystemWords = []
  form.suffixCustomText = ''
  form.coreTextA = ''
  form.compareWords = []
  form.coreTextB = ''
  resetPreview()
}

function resetForm() {
  editingId.value = null
  lastEditState.value = {}
  hasUserChangedType.value = false
  form.companyId = null
  form.projectId = null
  form.name = ''
  form.type = typeConfigs.value[0]?.type || ''
  form.legacyType = false
  form.remark = ''
  form.previewCount = 100
  form.llmSeedText = ''
  form.llmGenerationToken = ''
  form.llmQuestions = []
  clearTypeRelatedSelections()
  applyDefaultVisibilityByType(form.type)
}

function applyDefaultVisibilityByType(type: string) {
  const config = typeConfigs.value.find((item) => item.type === type)
  if (config) {
    form.areaEnabled = config.areaEnabledByDefault
  }
}

async function loadCompanies() {
  try {
    const { data } = await getCompanyList({ current: 1, size: 500 })
    companyOptions.value = data.data.records || []
  } catch {
    companyOptions.value = []
  }
}

async function loadTypeConfigs() {
  const { data } = await getKeywordGroupTypeConfigs()
  typeConfigs.value = data.data || []
  if (!form.type && typeConfigs.value.length) {
    form.type = typeConfigs.value[0].type
    applyDefaultVisibilityByType(form.type)
  }
}

async function loadOptionsByType(type: string, industryTag = form.functionIndustryTag) {
  if (!type) {
    Object.assign(wordOptions, emptyOptions)
    return
  }
  const { data } = await getKeywordAffixWordOptions({ type, industryTag: industryTag || undefined, includeManual: true })
  Object.assign(wordOptions, {
    ...emptyOptions,
    ...data.data,
    areaWords: data.data.areaWords || [],
    prefixWords: data.data.prefixWords || [],
    suffixWords: data.data.suffixWords || [],
    industryWords: data.data.industryWords || [],
    compareWords: data.data.compareWords || [],
  })
}

async function handleUserTypeChange(newType: string) {
  const oldType = form.type
  if (oldType === newType) {
    return
  }

  const nextConfig = typeConfigs.value.find((item) => item.type === newType)
  if (form.legacyType && nextConfig) {
    try {
      await ElMessageBox.confirm('当前组为历史类型，切换到新类型后老字段可能不适用，确认升级吗？', '类型升级确认', { type: 'warning' })
    } catch {
      return
    }
  }

  if (oldType && (hasUserChangedType.value || hasTypeRelatedSelections())) {
    lastEditState.value[oldType] = snapshot()
  }

  const cached = lastEditState.value[newType]
  if (cached) {
    restore(cached)
  } else {
    if (nextConfig?.structure === 'compare' && form.coreText.trim()) {
      try {
        await ElMessageBox.confirm('切换到对比词后会清空当前核心词，请确认是否继续。', '切换确认', { type: 'warning' })
      } catch {
        return
      }
    }
    form.type = newType
    form.legacyType = false
    clearTypeRelatedSelections()
    applyDefaultVisibilityByType(newType)
  }

  // 防御性赋值:确保 cached state 异常时 form.type 仍然为 newType。
  form.type = newType
  if (nextConfig) {
    form.legacyType = false
  }
  resetPreview()
  await loadOptionsByType(newType, form.functionIndustryTag)
  hasUserChangedType.value = true
}

async function handleFunctionIndustryTagChange(value: string) {
  await loadOptionsByType(form.type, value)
  const available = new Set(wordOptions.prefixWords.filter((item) => !item.industryTag || item.industryTag === 'common').map((item) => item.wordText))
  form.prefixSystemWords = form.prefixSystemWords.filter((word) => available.has(word))
}

function collectWordsBySource(items: KeywordWordItem[] | undefined) {
  const system: string[] = []
  const custom: string[] = []
  for (const item of items || []) {
    if (!item.wordText) continue
    if ((item.source || 'custom') === 'system') system.push(item.wordText)
    else custom.push(item.wordText)
  }
  return { system: dedupText(system), custom: dedupText(custom) }
}

function hydrateForm(detail: KeywordGroup) {
  form.companyId = detail.companyId
  form.projectId = detail.projectId ?? null
  form.name = detail.name
  form.type = detail.type
  form.legacyType = Boolean(detail.legacyType)
  form.remark = detail.remark || ''
  form.areaEnabled = detail.areaEnabled ?? currentTypeConfig.value.areaEnabledByDefault
  form.functionIndustryTag = detail.functionIndustryTag || ''
  form.llmSeedText = ''
  form.llmGenerationToken = ''
  form.llmQuestions = [...(detail.llmQuestions || [])]
  form.previewCount = 100

  const columns = detail.columns || emptyColumns()
  const areaWords = columns.areaWords?.length ? columns.areaWords : (columns.regionWords || [])
  form.areaText = dedupText(areaWords.map((v) => v.wordText).filter(Boolean)).join('\n')
  form.industryWords = dedupText((columns.industryWords || []).map((v) => v.wordText).filter(Boolean))
  const prefix = collectWordsBySource(columns.prefixWords)
  const suffix = collectWordsBySource(columns.suffixWords)
  form.prefixSystemWords = prefix.system
  form.prefixCustomText = prefix.custom.join('\n')
  form.coreText = dedupText((columns.coreWords || []).map((v) => v.wordText).filter(Boolean)).join('\n')
  form.suffixSystemWords = suffix.system
  form.suffixCustomText = suffix.custom.join('\n')
  form.coreTextA = dedupText((columns.coreWordsA || []).map((v) => v.wordText).filter(Boolean)).join('\n')
  form.compareWords = dedupText((columns.compareWords || []).map((v) => v.wordText).filter(Boolean))
  form.coreTextB = dedupText((columns.coreWordsB || []).map((v) => v.wordText).filter(Boolean)).join('\n')
}

function emptyColumns(): KeywordGroupColumns {
  return { areaWords: [], prefixWords: [], coreWords: [], industryWords: [], suffixWords: [], coreWordsA: [], compareWords: [], coreWordsB: [] }
}

function validateBase(forSave: boolean) {
  if (!form.companyId) {
    ElMessage.warning('请选择客户')
    return false
  }
  if (forSave && !form.name.trim()) {
    ElMessage.warning('请输入关键词组名')
    return false
  }
  if (!form.type) {
    ElMessage.warning('请选择类型')
    return false
  }
  if (currentTypeConfig.value.functionIndustryRequired && !form.functionIndustryTag) {
    ElMessage.warning('请选择行业')
    return false
  }
  const columns = buildColumns()
  if (currentTypeConfig.value.requiredColumns.core && !columns.coreWords.length) {
    ElMessage.warning('请至少填写一个核心词')
    return false
  }
  if (currentTypeConfig.value.requiredColumns.industry && !columns.industryWords.length) {
    ElMessage.warning('请至少选择一个行业词')
    return false
  }
  if (currentTypeConfig.value.requiredColumns.compareCore && !columns.coreWordsA.length) {
    ElMessage.warning('请填写核心词 A')
    return false
  }
  if (currentTypeConfig.value.requiredColumns.compareCore && !columns.coreWordsB.length) {
    ElMessage.warning('请填写核心词 B')
    return false
  }
  if (currentTypeConfig.value.requiredColumns.compareWord && !columns.compareWords.length) {
    ElMessage.warning('请选择对比连接词')
    return false
  }
  if (currentTypeConfig.value.requiredColumns.suffix && !columns.suffixWords.length) {
    ElMessage.warning('请选择后缀词')
    return false
  }
  if (estimatedCount.value > limit) {
    ElMessage.warning(overLimitText.value)
    return false
  }
  if (form.previewCount < form.llmQuestions.length) {
    ElMessage.warning(`入库数 ${form.previewCount} 小于已生成 AI 问题数 ${form.llmQuestions.length}，请调整`)
    return false
  }
  return true
}

function handleApiError(error: any) {
  const payload = error?.response?.data || error
  const parsed = parseErrorCode({ errorCode: payload?.errorCode, message: payload?.message })
  ElMessage.error(parsed.code ? ERROR_CODE_HINTS[parsed.code] : (parsed.text || '操作失败'))
}

async function doPreview() {
  if (!validateBase(false)) {
    return
  }
  previewing.value = true
  try {
    const { data } = await previewKeywordGroup(buildPayload())
    previewItems.value = data.data.items || []
    previewTotalAvailable.value = data.data.totalAvailable || 0
    previewVisible.value = true
    if ((data.data.totalAvailable || 0) < form.previewCount) {
      ElMessage.warning(`当前组合仅可生成 ${data.data.totalAvailable || 0} 条关键词，少于设定的 ${form.previewCount} 条`)
    }
  } catch (error) {
    handleApiError(error)
  } finally {
    previewing.value = false
  }
}

async function submit() {
  if (!validateBase(true)) {
    return
  }
  if (!previewItems.value.length) {
    ElMessage.warning('请先预览并确认当前入库关键词')
    return
  }
  saving.value = true
  try {
    const payload = {
      ...buildPayload(),
      resultKeywords: [...previewItems.value],
    }
    if (editingId.value) {
      await updateKeywordGroup(editingId.value, payload)
    } else {
      await createKeywordGroup(payload)
    }
    ElMessage.success('保存成功')
    previewVisible.value = false
    await load()
  } catch (error) {
    handleApiError(error)
  } finally {
    saving.value = false
  }
}

async function load() {
  loading.value = true
  try {
    const { data } = await getKeywordGroupPage({ current: page.current, size: page.size, keyword: query.keyword || undefined, companyId: query.companyId, type: query.type || undefined })
    rows.value = data.data.records || []
    page.total = data.data.total || 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.current = 1
  load()
}

function onPageChange(v: number) {
  page.current = v
  load()
}

async function openEdit(id: number) {
  const { data } = await getKeywordGroupDetail(id)
  resetForm()
  editingId.value = id
  hydrateForm(data.data)
  await loadOptionsByType(form.type, form.functionIndustryTag)
  resetPreview()
}

async function openPreviewOnly(id: number) {
  await openEdit(id)
  await doPreview()
}

async function remove(row: KeywordGroup) {
  try {
    await ElMessageBox.confirm(`确认删除拓词组「${row.name}」？`, '删除确认', { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' })
    await deleteKeywordGroup(row.id)
    ElMessage.success('删除成功')
    await load()
  } catch {
    // canceled
  }
}

onMounted(async () => {
  await Promise.all([loadCompanies(), loadTypeConfigs()])
  resetForm()
  await loadOptionsByType(form.type)
  await load()
})

</script>

<style scoped>
.keyword-expander-page {
  max-width: 1280px;
  margin: 0 auto;
  color: #1a1d26;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}

.editing-tip {
  font-size: 12px;
  color: #64748b;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 460px));
  gap: 16px;
}

.form-section {
  margin-bottom: 16px;
}

.form-label {
  display: block;
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 500;
}

.form-label.required::before {
  content: '*';
  color: #f43f5e;
  margin-right: 4px;
}

.form-input {
  width: 460px;
  max-width: 100%;
}

input.form-input {
  border: 1px solid #dbe2ea;
  border-radius: 6px;
  padding: 10px 12px;
  outline: none;
}

input.form-input:focus {
  border-color: #4361ee;
  box-shadow: 0 0 0 2px rgba(67, 97, 238, 0.12);
}

.column-builder {
  background: #fff;
  border: 1px solid #e8ecf1;
  border-radius: 8px;
  overflow: hidden;
}

.preview-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #eef2f6;
  padding: 12px 14px;
}

.estimate-text {
  font-size: 13px;
  color: #475569;
}

.over-limit {
  color: #ef4444;
  margin-left: 8px;
}

.preview-actions-inline {
  display: flex;
  align-items: center;
  gap: 8px;
}

.btn-preview {
  border: none;
  background: #2563eb;
  color: #fff;
  border-radius: 6px;
  padding: 8px 14px;
  cursor: pointer;
}

.btn-preview:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.group-list-card {
  margin-top: 16px;
}

.list-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.mr-1 {
  margin-right: 4px;
}

@media (max-width: 960px) {
  .form-grid {
    grid-template-columns: 1fr;
  }

  .preview-bar {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }
}
</style>
