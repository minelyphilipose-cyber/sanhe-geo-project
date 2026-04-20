<template>
  <div>
    <div class="mb-4 flex items-center justify-between gap-3">
      <div class="flex items-center gap-2">
        <el-select v-model="query.type" clearable placeholder="类型" style="width: 160px" @change="onSearch">
          <el-option v-for="item in typeOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="query.affixKind" clearable placeholder="词位" style="width: 160px" @change="onSearch">
          <el-option label="前缀词" value="prefix" />
          <el-option label="后缀词" value="suffix" />
          <el-option label="行业词" value="industry" />
        </el-select>
        <el-select v-model="query.enabled" clearable placeholder="状态" style="width: 120px" @change="onSearch">
          <el-option label="启用" :value="true" />
          <el-option label="停用" :value="false" />
        </el-select>
        <el-input v-model="query.keyword" clearable placeholder="搜索词" style="width: 220px" @keyup.enter="onSearch" />
        <el-button @click="onSearch">查询</el-button>
      </div>
      <el-button type="primary" @click="openCreate">新增词条</el-button>
    </div>

    <el-card>
      <DataState :loading="loading" :empty="!loading && rows.length === 0" empty-text="暂无词条">
        <el-table :data="rows" border>
          <el-table-column label="类型编码" width="180">
            <template #default="{ row }">{{ typeCodeLabel(row.type) }}</template>
          </el-table-column>
          <el-table-column label="词位" width="120">
            <template #default="{ row }">{{ affixKindLabel(row.affixKind) }}</template>
          </el-table-column>
          <el-table-column prop="wordText" label="词文本" min-width="220" />
          <el-table-column prop="sortOrder" label="排序" width="100" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="updatedAt" label="更新时间" width="180" />
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link :type="row.enabled ? 'warning' : 'success'" @click="toggleStatus(row)">
                {{ row.enabled ? '停用' : '启用' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="mt-4 flex justify-end">
          <el-pagination
            background
            layout="prev, pager, next, total"
            :current-page="page.current"
            :page-size="page.size"
            :total="page.total"
            @current-change="onPageChange"
          />
        </div>
      </DataState>
    </el-card>

    <el-dialog v-model="formVisible" :title="formMode === 'create' ? '新增词条' : '编辑词条'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="词位" prop="affixKind" required>
          <el-select v-model="form.affixKind" style="width: 100%">
            <el-option label="前缀词" value="prefix" />
            <el-option label="后缀词" value="suffix" />
            <el-option label="行业词" value="industry" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型编码" prop="type" required>
          <el-select v-model="form.type" style="width: 100%" placeholder="请选择类型">
            <el-option v-for="item in typeOptions" :key="item.value" :label="`${item.label} (${item.value})`" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="词文本" prop="wordText" required>
          <el-input v-model="form.wordText" maxlength="64" show-word-limit placeholder="请输入词文本" />
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="form.sortOrder" :min="0" :step="10" style="width: 100%" />
        </el-form-item>
        <el-form-item v-if="formMode === 'create'" label="状态">
          <el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import DataState from '@/components/ui/DataState.vue'
import {
  createAdminKeywordAffixWord,
  getAdminKeywordAffixWords,
  updateAdminKeywordAffixWord,
  updateAdminKeywordAffixWordStatus,
} from '@/api/system'
import { useDictStore } from '@/stores/dict'
import type { KeywordAffixWord, KeywordTypeOption } from '@/types'

const loading = ref(false)
const saving = ref(false)
const rows = ref<KeywordAffixWord[]>([])
const typeOptions = ref<KeywordTypeOption[]>([])
const dictStore = useDictStore()

const page = reactive({ current: 1, size: 20, total: 0 })
const query = reactive<{
  type: string
  affixKind: '' | 'prefix' | 'suffix' | 'industry'
  keyword: string
  enabled: boolean | undefined
}>({
  type: '',
  affixKind: '',
  keyword: '',
  enabled: undefined,
})

const formVisible = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<{
  type: string
  affixKind: 'prefix' | 'suffix' | 'industry'
  wordText: string
  sortOrder: number
  enabled: boolean
}>({
  type: '',
  affixKind: 'prefix',
  wordText: '',
  sortOrder: 10,
  enabled: true,
})

const rules: FormRules = {
  affixKind: [{ required: true, message: '请选择词位', trigger: 'change' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  wordText: [{ required: true, message: '请输入词文本', trigger: 'blur' }],
}

function typeCodeLabel(type: string) {
  const option = typeOptions.value.find((item) => item.value === type)
  return option ? `${option.label} (${type})` : type
}

function affixKindLabel(affixKind: string) {
  if (affixKind === 'prefix') return '前缀词'
  if (affixKind === 'suffix') return '后缀词'
  if (affixKind === 'industry') return '行业词'
  return affixKind
}

async function loadTypeOptions() {
  await dictStore.ensureLoaded()
  typeOptions.value = (dictStore.options('question_type') || []).map((item) => ({
    value: item.dictKey,
    label: item.dictValue,
  }))
}

async function load() {
  loading.value = true
  try {
    const { data } = await getAdminKeywordAffixWords({
      current: page.current,
      size: page.size,
      type: query.type || undefined,
      affixKind: query.affixKind || undefined,
      keyword: query.keyword || undefined,
      enabled: query.enabled,
    })
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

function resetForm() {
  form.type = typeOptions.value[0]?.value || ''
  form.affixKind = 'prefix'
  form.wordText = ''
  form.sortOrder = 10
  form.enabled = true
}

function openCreate() {
  formMode.value = 'create'
  editingId.value = null
  resetForm()
  formVisible.value = true
}

function openEdit(row: KeywordAffixWord) {
  formMode.value = 'edit'
  editingId.value = row.id
  form.type = row.type
  form.affixKind = row.affixKind as 'prefix' | 'suffix' | 'industry'
  form.wordText = row.wordText
  form.sortOrder = row.sortOrder
  form.enabled = row.enabled
  formVisible.value = true
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = {
      type: form.type.trim(),
      affixKind: form.affixKind,
      wordText: form.wordText.trim(),
      sortOrder: form.sortOrder,
      enabled: form.enabled,
    }
    if (formMode.value === 'create') {
      await createAdminKeywordAffixWord(payload)
    } else if (editingId.value) {
      await updateAdminKeywordAffixWord(editingId.value, payload)
    }
    ElMessage.success('保存成功')
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row: KeywordAffixWord) {
  try {
    const target = !row.enabled
    await ElMessageBox.confirm(
      `确认${target ? '启用' : '停用'}词条「${row.wordText}」？`,
      '状态变更确认',
      { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' },
    )
    await updateAdminKeywordAffixWordStatus(row.id, target)
    ElMessage.success('状态已更新')
    await load()
  } catch {
    // canceled
  }
}

onMounted(async () => {
  await loadTypeOptions()
  if (!form.type) {
    form.type = typeOptions.value[0]?.value || ''
  }
  await load()
})
</script>
