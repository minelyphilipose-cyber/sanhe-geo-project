<template>
  <div class="keyword-expander-page">
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">关键词拓词管理</h2>
      </div>
      <div class="header-right">
        <span v-if="editingId" class="editing-tip">当前编辑：{{ form.name || '未命名' }}</span>
      </div>
    </div>

    <div class="form-section">
      <label class="form-label required">客户</label>
      <el-select v-model="form.companyId" class="form-input" filterable placeholder="请选择客户">
        <el-option v-for="item in companyOptions" :key="item.id" :label="item.companyName" :value="item.id" />
      </el-select>
    </div>

    <div class="form-section">
      <label class="form-label required">关键词组名称</label>
      <input v-model="form.name" class="form-input" type="text" maxlength="64" placeholder="请输入词组名称，支持中文与英文" />
    </div>

    <div class="form-section">
      <label class="form-label required">类型</label>
      <div class="type-selector">
        <label v-for="item in typeOptions" :key="item.value" class="type-option" :class="{ active: form.type === item.value }">
          <input type="radio" :value="item.value" v-model="form.type" @change="onTypeChanged" />
          <span class="radio-dot"></span>
          <span class="type-label">{{ item.label }}</span>
        </label>
      </div>
    </div>

    <div class="column-builder">
      <div class="preview-bar">
        <div class="estimate-text">
          预计生成 <strong>{{ estimatedCount }}</strong> 条          <span v-if="estimatedCount > limit" class="over-limit">超过上限 {{ limit }} 条，请减少选词</span>
        </div>
        <div class="preview-actions-inline">
          <el-input-number v-model="form.previewCount" :min="1" :max="1000" size="small" />
          <button class="btn-preview" :disabled="estimatedCount === 0 || estimatedCount > limit || previewing" @click="doPreview">
            {{ previewing ? '预览中...' : '预览拓词' }}
          </button>
        </div>
      </div>

      <div class="columns-wrapper">
        <div class="word-column">
          <div class="column-header"><div class="column-step"><span class="step-number">1</span><span class="step-label">地区词</span></div></div>
          <div class="column-body">
            <textarea
              v-model="form.regionText"
              class="core-textarea"
              rows="10"
              placeholder="推荐词如：&#10;北京&#10;上海&#10;深圳&#10;南京"
            ></textarea>
          </div>
        </div>

        <div class="word-column">
          <div class="column-header"><div class="column-step"><span class="step-number">2</span><span class="step-label">前缀词</span></div></div>
          <div class="column-body">
            <div class="section-hint">系统词</div>
            <label v-for="item in prefixOptions" :key="item.id" class="word-item" :class="{ checked: form.prefixSystemWords.includes(item.wordText) }">
              <input v-model="form.prefixSystemWords" type="checkbox" :value="item.wordText" class="word-checkbox" />
              <span class="checkmark"></span>
              <span class="word-text">{{ item.wordText }}</span>
            </label>
            <div class="section-hint mt">自定义词（每行一个）</div>
            <textarea v-model="form.prefixCustomText" class="core-textarea" rows="4" placeholder="请输入自定义前缀词"></textarea>
          </div>
        </div>

        <div class="word-column">
          <div class="column-header"><div class="column-step"><span class="step-number">3</span><span class="step-label">核心词（必填）</span></div></div>
          <div class="column-body">
            <textarea v-model="form.coreText" class="core-textarea" rows="10" placeholder="请填写核心词，每行一个"></textarea>
          </div>
        </div>

        <div class="word-column">
          <div class="column-header"><div class="column-step"><span class="step-number">4</span><span class="step-label">行业词（必填）</span></div></div>
          <div class="column-body">
            <label v-for="item in displayIndustryOptions" :key="`${item.id}_${item.wordText}`" class="word-item" :class="{ checked: form.industryWords.includes(item.wordText) }">
              <input v-model="form.industryWords" type="checkbox" :value="item.wordText" class="word-checkbox" />
              <span class="checkmark"></span>
              <span class="word-text">{{ item.wordText }}</span>
            </label>
          </div>
        </div>

        <div class="word-column">
          <div class="column-header"><div class="column-step"><span class="step-number">5</span><span class="step-label">后缀词</span></div></div>
          <div class="column-body">
            <div class="section-hint">系统词</div>
            <label v-for="item in suffixOptions" :key="item.id" class="word-item" :class="{ checked: form.suffixSystemWords.includes(item.wordText) }">
              <input v-model="form.suffixSystemWords" type="checkbox" :value="item.wordText" class="word-checkbox" />
              <span class="checkmark"></span>
              <span class="word-text">{{ item.wordText }}</span>
            </label>
            <div class="section-hint mt">自定义词（每行一个）</div>
            <textarea v-model="form.suffixCustomText" class="core-textarea" rows="4" placeholder="请输入自定义后缀词"></textarea>
          </div>
        </div>
      </div>
    </div>

    <el-card class="group-list-card mt-4">
      <div class="list-toolbar">
        <el-input v-model="query.keyword" clearable placeholder="搜索拓词组" style="width: 240px" @keyup.enter="onSearch" />
        <el-select v-model="query.companyId" clearable filterable placeholder="客户" style="width: 200px" @change="onSearch">
          <el-option v-for="item in companyOptions" :key="item.id" :label="item.companyName" :value="item.id" />
        </el-select>
        <el-select v-model="query.type" clearable placeholder="类型" style="width: 160px" @change="onSearch">
          <el-option v-for="item in typeOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-button @click="onSearch">查询</el-button>
      </div>

      <DataState :loading="loading" :empty="!loading && rows.length === 0" empty-text="暂无拓词组">
        <el-table :data="rows" border>
          <el-table-column prop="name" label="拓词组名称" min-width="220" />
          <el-table-column prop="companyName" label="客户" min-width="180" />
          <el-table-column label="类型" width="160">
            <template #default="scope">{{ typeLabel(scope.row.type) }}</template>
          </el-table-column>
          <el-table-column prop="updatedAt" label="更新时间" width="180" />
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="scope">
              <el-button link type="primary" @click="openEdit(scope.row.id)">编辑</el-button>
              <el-button link type="success" @click="openPreviewOnly(scope.row.id)">预览</el-button>
              <el-button link type="danger" @click="remove(scope.row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="mt-4 flex justify-end">
          <el-pagination background layout="prev, pager, next, total" :current-page="page.current" :page-size="page.size" :total="page.total" @current-change="onPageChange" />
        </div>
      </DataState>
    </el-card>

    <transition name="fade"><div v-if="previewVisible" class="overlay" @click="previewVisible = false"></div></transition>
    <transition name="slide-up">
      <div v-if="previewVisible" class="preview-panel">
        <div class="preview-header">
          <h3 class="preview-title">拓词预览</h3>
          <div class="preview-meta">本次入库 <strong>{{ previewKeywords.length }}</strong> 条，候选池共 {{ previewTotalAvailable }} 条</div>
        </div>
        <div class="preview-body">
          <div v-if="previewTotalAvailable < form.previewCount" class="preview-tip">
            当前组合可生成 {{ previewTotalAvailable }} 条关键词，少于设定的 {{ form.previewCount }} 条，请增加选词后再预览。
          </div>
          <div class="keyword-tags"><span v-for="(kw, idx) in displayKeywords" :key="`${idx}_${kw}`" class="keyword-tag">{{ kw }}</span></div>
          <div v-if="previewKeywords.length > maxDisplay" class="show-more">
            <button class="btn-show-more" @click="showAll = !showAll">{{ showAll ? '收起' : `展开全部 ${previewKeywords.length} 条` }}</button>
          </div>
        </div>
        <div class="preview-actions">
          <button class="btn-secondary" @click="previewVisible = false">取消</button>
          <button class="btn-primary" :disabled="saving" @click="submit">{{ saving ? '保存中...' : '保存关键词组' }}</button>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import DataState from '@/components/ui/DataState.vue'
import { createKeywordGroup, deleteKeywordGroup, getKeywordGroupDetail, getKeywordGroupPage, previewKeywordGroup, updateKeywordGroup } from '@/api/project'
import { getKeywordAffixWordOptions } from '@/api/system'
import { getCompanyList } from '@/api/customer'
import { useDictStore } from '@/stores/dict'
import type { Company, KeywordAffixWord, KeywordGroup, KeywordGroupPayload, KeywordTypeOption, KeywordWordItem } from '@/types'

const limit = 1000
const maxDisplay = 50
const showAll = ref(false)
const previewVisible = ref(false)

const loading = ref(false)
const previewing = ref(false)
const saving = ref(false)
const rows = ref<KeywordGroup[]>([])
const previewKeywords = ref<string[]>([])
const previewTotalAvailable = ref(0)
const companyOptions = ref<Company[]>([])

const page = reactive({ current: 1, size: 20, total: 0 })
const query = reactive<{ keyword: string; companyId?: number; type: string }>({ keyword: '', companyId: undefined, type: '' })

const editingId = ref<number | null>(null)
const typeOptions = ref<KeywordTypeOption[]>([])
const prefixOptions = ref<KeywordAffixWord[]>([])
const suffixOptions = ref<KeywordAffixWord[]>([])
const industryOptions = ref<KeywordAffixWord[]>([])
const dictStore = useDictStore()

const form = reactive({
  companyId: null as number | null,
  name: '',
  type: '',
  remark: '',
  previewCount: 100,
  regionText: '',
  prefixSystemWords: [] as string[],
  prefixCustomText: '',
  coreText: '',
  industryWords: [] as string[],
  suffixSystemWords: [] as string[],
  suffixCustomText: '',
})

const displayKeywords = computed(() => (showAll.value ? previewKeywords.value : previewKeywords.value.slice(0, maxDisplay)))
const displayIndustryOptions = computed(() => {
  const options = [...industryOptions.value]
  const existed = new Set(options.map((item) => item.wordText))
  for (const wordText of form.industryWords) {
    if (existed.has(wordText)) {
      continue
    }
    options.push({
      id: -options.length - 1,
      type: form.type,
      affixKind: 'industry',
      wordText,
      sortOrder: 9999,
      enabled: true,
      createdAt: '',
      updatedAt: '',
    })
  }
  return options
})

const estimatedCount = computed(() => {
  const columns = buildColumns()
  const counts = [columns.regionWords.length, columns.prefixWords.length, columns.coreWords.length, columns.industryWords.length, columns.suffixWords.length]
  return counts.reduce((acc, n) => acc * Math.max(1, n), 1)
})

function typeLabel(type: string) {
  return typeOptions.value.find((v) => v.value === type)?.label || type
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

function buildColumns() {
  return {
    regionWords: toWordItems(parseTextWords(form.regionText), 'custom'),
    prefixWords: [...toWordItems(form.prefixSystemWords, 'system'), ...toWordItems(parseTextWords(form.prefixCustomText), 'custom')],
    coreWords: toWordItems(parseTextWords(form.coreText), 'custom'),
    industryWords: toWordItems(form.industryWords, 'system'),
    suffixWords: [...toWordItems(form.suffixSystemWords, 'system'), ...toWordItems(parseTextWords(form.suffixCustomText), 'custom')],
  }
}

function buildPayload(): KeywordGroupPayload {
  return {
    companyId: Number(form.companyId),
    name: form.name.trim(),
    type: form.type,
    remark: form.remark.trim() || undefined,
    count: form.previewCount,
    columns: buildColumns(),
  }
}

function resetForm() {
  editingId.value = null
  form.companyId = null
  form.name = ''
  form.type = typeOptions.value[0]?.value || ''
  form.remark = ''
  form.previewCount = 100
  form.regionText = ''
  form.prefixSystemWords = []
  form.prefixCustomText = ''
  form.coreText = ''
  form.industryWords = []
  form.suffixSystemWords = []
  form.suffixCustomText = ''
  previewKeywords.value = []
  previewTotalAvailable.value = 0
  showAll.value = false
  previewVisible.value = false
}

async function loadCompanies() {
  try {
    const { data } = await getCompanyList({ current: 1, size: 500 })
    companyOptions.value = data.data.records || []
  } catch {
    companyOptions.value = []
  }
}
async function loadTypeAndIndustryOptions() {
  await dictStore.ensureLoaded()
  typeOptions.value = (dictStore.options('question_type') || []).map((item) => ({
    value: item.dictKey,
    label: item.dictValue,
  }))
  if (!form.type && typeOptions.value.length > 0) {
    form.type = typeOptions.value[0].value
  }
}

async function loadPrefixSuffixOptions(type: string) {
  if (!type) {
    prefixOptions.value = []
    suffixOptions.value = []
    industryOptions.value = []
    return
  }
  const { data } = await getKeywordAffixWordOptions(type)
  prefixOptions.value = data.data.prefixWords || []
  suffixOptions.value = data.data.suffixWords || []
  industryOptions.value = data.data.industryWords || []
}

async function onTypeChanged() {
  await loadPrefixSuffixOptions(form.type)
  form.prefixSystemWords = []
  form.industryWords = []
  form.suffixSystemWords = []
  previewKeywords.value = []
  previewTotalAvailable.value = 0
}

async function doPreview() {
  if (!form.companyId) {
    ElMessage.warning('请选择客户')
    return
  }
  if (!form.name.trim()) {
    ElMessage.warning('请输入关键词组名')
    return
  }
  if (!parseTextWords(form.coreText).length) {
    ElMessage.warning('请至少填写一个核心词')
    return
  }
  if (!form.industryWords.length) {
    ElMessage.warning('请至少选择一个行业词')
    return
  }
  if (!form.type) {
    ElMessage.warning('请选择类型')
    return
  }
  if (estimatedCount.value > limit) {
    ElMessage.warning(`预计生成 ${estimatedCount.value} 条，超过上限 ${limit} 条，请减少选词`)
    return
  }

  previewing.value = true
  try {
    const { data } = await previewKeywordGroup(buildPayload())
    previewKeywords.value = data.data.keywords || []
    previewTotalAvailable.value = data.data.totalAvailable || 0
    showAll.value = false
    previewVisible.value = true
    if ((data.data.totalAvailable || 0) < form.previewCount) {
      ElMessage.warning(`当前组合仅可生成 ${data.data.totalAvailable || 0} 条关键词，少于设定的 ${form.previewCount} 条`)
    }
  } finally {
    previewing.value = false
  }
}

async function submit() {
  if (!form.companyId) {
    ElMessage.warning('请选择客户')
    return
  }
  if (!form.name.trim()) {
    ElMessage.warning('请输入关键词组名')
    return
  }
  if (!parseTextWords(form.coreText).length) {
    ElMessage.warning('请至少填写一个核心词')
    return
  }
  if (!form.industryWords.length) {
    ElMessage.warning('请至少选择一个行业词')
    return
  }
  if (!form.type) {
    ElMessage.warning('请选择类型')
    return
  }
  if (estimatedCount.value > limit) {
    ElMessage.warning(`预计生成 ${estimatedCount.value} 条，超过上限 ${limit} 条，请减少选词`)
    return
  }
  if (!previewKeywords.value.length) {
    ElMessage.warning('请先预览并确认当前入库关键词')
    return
  }

  saving.value = true
  try {
    const payload = {
      ...buildPayload(),
      resultKeywords: [...previewKeywords.value],
    }
    if (editingId.value) {
      await updateKeywordGroup(editingId.value, payload)
    } else {
      await createKeywordGroup(payload)
    }
    ElMessage.success('保存成功')
    previewVisible.value = false
    await load()
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

async function openEdit(id: number) {
  const { data } = await getKeywordGroupDetail(id)
  const detail = data.data
  resetForm()
  editingId.value = id
  form.companyId = detail.companyId
  form.name = detail.name
  form.type = detail.type
  form.remark = detail.remark || ''
  await loadPrefixSuffixOptions(form.type)

  const columns = detail.columns || { regionWords: [], prefixWords: [], coreWords: [], industryWords: [], suffixWords: [] }
  form.regionText = dedupText((columns.regionWords || []).map((v) => v.wordText).filter(Boolean)).join('\n')
  form.industryWords = dedupText((columns.industryWords || []).map((v) => v.wordText).filter(Boolean))
  const prefix = collectWordsBySource(columns.prefixWords)
  const suffix = collectWordsBySource(columns.suffixWords)
  form.prefixSystemWords = prefix.system
  form.prefixCustomText = prefix.custom.join('\n')
  form.coreText = dedupText((columns.coreWords || []).map((v) => v.wordText).filter(Boolean)).join('\n')
  form.suffixSystemWords = suffix.system
  form.suffixCustomText = suffix.custom.join('\n')
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
  await loadCompanies()
  await loadTypeAndIndustryOptions()
  await loadPrefixSuffixOptions(form.type)
  await load()
})
</script>

<style scoped>
.keyword-expander-page { max-width: 1280px; margin: 0 auto; color: #1a1d26; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20px; }
.header-left { display: flex; align-items: center; gap: 12px; }
.page-title { margin: 0; font-size: 20px; font-weight: 600; }
.editing-tip { font-size: 12px; color: #64748b; }
.form-section { margin-bottom: 16px; }
.form-label { display: block; margin-bottom: 8px; font-size: 14px; font-weight: 500; }
.form-label.required::before { content: '*'; color: #f43f5e; margin-right: 4px; }
.form-input { width: 460px; max-width: 100%; border: 1px solid #dbe2ea; border-radius: 6px; padding: 10px 12px; outline: none; }
.form-input:focus { border-color: #4361ee; box-shadow: 0 0 0 2px rgba(67, 97, 238, 0.12); }
.type-selector { display: flex; gap: 22px; flex-wrap: wrap; }
.type-option { display: flex; align-items: center; gap: 8px; cursor: pointer; }
.type-option input { display: none; }
.radio-dot { width: 16px; height: 16px; border: 2px solid #c6cdda; border-radius: 50%; position: relative; }
.type-option.active .radio-dot { border-color: #4361ee; }
.type-option.active .radio-dot::after { content: ''; position: absolute; inset: 3px; border-radius: 50%; background: #4361ee; }
.column-builder { background: #fff; border: 1px solid #e8ecf1; border-radius: 10px; overflow: hidden; }
.preview-bar { display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #eef2f6; padding: 12px 14px; }
.estimate-text { font-size: 13px; color: #475569; }
.over-limit { color: #ef4444; margin-left: 8px; }
.preview-actions-inline { display: flex; align-items: center; gap: 8px; }
.btn-preview { border: none; background: #2563eb; color: #fff; border-radius: 6px; padding: 8px 14px; cursor: pointer; }
.btn-preview:disabled { opacity: 0.6; cursor: not-allowed; }
.columns-wrapper { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); }
.word-column { border-right: 1px solid #f0f3f7; min-height: 330px; }
.word-column:last-child { border-right: none; }
.column-header { padding: 10px 12px; border-bottom: 1px solid #f0f3f7; background: linear-gradient(135deg, #f2f5ff 0%, #fafbfe 100%); }
.column-step { display: flex; align-items: center; gap: 8px; }
.step-number { width: 20px; height: 20px; border-radius: 50%; background: #4361ee; color: #fff; font-size: 11px; display: inline-flex; align-items: center; justify-content: center; }
.step-label { font-size: 13px; font-weight: 600; }
.column-body { padding: 12px; max-height: 360px; overflow: auto; }
.section-hint { font-size: 12px; color: #94a3b8; margin-bottom: 6px; }
.section-hint.mt { margin-top: 12px; }
.core-textarea { width: 100%; border: 1px dashed #d8dfeb; border-radius: 6px; padding: 8px 10px; resize: vertical; font-family: inherit; box-sizing: border-box; }
.word-item { display: flex; align-items: center; gap: 8px; padding: 6px 8px; border-radius: 6px; cursor: pointer; }
.word-item:hover, .word-item.checked { background: #eef1ff; }
.word-checkbox { display: none; }
.checkmark { width: 14px; height: 14px; border: 1px solid #cbd5e1; border-radius: 3px; flex-shrink: 0; }
.word-item.checked .checkmark { background: #4361ee; border-color: #4361ee; }
.word-text { font-size: 13px; color: #334155; }
.group-list-card { margin-top: 16px; }
.list-toolbar { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; }
.overlay { position: fixed; inset: 0; background: rgba(0, 0, 0, 0.25); z-index: 100; }
.preview-panel { position: fixed; left: 50%; bottom: 0; transform: translateX(-50%); width: min(920px, 94vw); max-height: 72vh; background: #fff; border-radius: 14px 14px 0 0; box-shadow: 0 10px 30px rgba(0, 0, 0, 0.12); z-index: 101; display: flex; flex-direction: column; }
.preview-header, .preview-actions { padding: 14px 18px; border-bottom: 1px solid #f1f5f9; display: flex; align-items: center; justify-content: space-between; }
.preview-actions { border-bottom: none; border-top: 1px solid #f1f5f9; justify-content: flex-end; gap: 10px; }
.preview-title { margin: 0; font-size: 15px; }
.preview-meta { font-size: 13px; color: #64748b; }
.preview-body { padding: 16px 18px; overflow: auto; }
.preview-tip { margin-bottom: 12px; font-size: 13px; color: #b45309; background: #fff7ed; border: 1px solid #fdba74; border-radius: 8px; padding: 10px 12px; }
.keyword-tags { display: flex; flex-wrap: wrap; gap: 8px; }
.keyword-tag { font-size: 13px; color: #4361ee; background: #eef1ff; border-radius: 16px; padding: 5px 12px; }
.show-more { margin-top: 12px; }
.btn-show-more, .btn-secondary, .btn-primary { border-radius: 6px; padding: 7px 14px; cursor: pointer; }
.btn-show-more, .btn-secondary { border: 1px solid #cbd5e1; background: #fff; color: #334155; }
.btn-primary { border: none; background: #4361ee; color: #fff; }
.slide-up-enter-active, .slide-up-leave-active { transition: all 0.25s ease; }
.slide-up-enter-from, .slide-up-leave-to { transform: translateX(-50%) translateY(100%); opacity: 0; }
.fade-enter-active, .fade-leave-active { transition: opacity 0.2s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
@media (max-width: 960px) {
  .columns-wrapper { grid-template-columns: 1fr; }
  .word-column { border-right: none; border-bottom: 1px solid #f0f3f7; }
  .word-column:last-child { border-bottom: none; }
  .preview-bar { flex-direction: column; align-items: flex-start; gap: 10px; }
}
</style>
