<template>
  <div>
    <div class="mb-4 flex items-center justify-between gap-3">
      <div class="flex items-center gap-2">
        <el-select v-model="query.dictType" clearable filterable placeholder="字典类型" style="width: 200px" @change="onSearch">
          <el-option v-for="type in dictTypes" :key="type" :label="type" :value="type" />
        </el-select>
        <el-select v-model="query.enabled" clearable placeholder="状态" style="width: 120px" @change="onSearch">
          <el-option label="启用" :value="true" />
          <el-option label="停用" :value="false" />
        </el-select>
        <el-input
          v-model="query.keyword"
          clearable
          placeholder="搜索 key/value/备注"
          style="width: 280px"
          @keyup.enter="onSearch"
        />
        <el-button @click="onSearch">查询</el-button>
      </div>
      <div class="flex items-center gap-2">
        <el-button @click="refreshGlobalDict">刷新全局字典缓存</el-button>
        <el-button type="primary" @click="openCreate">新增字典项</el-button>
      </div>
    </div>

    <el-card>
      <DataState :loading="loading" :empty="!loading && rows.length === 0" empty-text="暂无字典项">
        <el-table :data="rows" border>
          <el-table-column prop="dictType" label="字典类型" min-width="160" />
          <el-table-column prop="dictKey" label="字典Key" min-width="170" />
          <el-table-column prop="dictValue" label="中文Value" min-width="180" />
          <el-table-column prop="sortOrder" label="排序" width="90" />
          <el-table-column label="状态" width="100">
            <template #default="scope">
              <el-tag :type="scope.row.enabled ? 'success' : 'info'">{{ scope.row.enabled ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="200" />
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="scope">
              <el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button>
              <el-button
                link
                :type="scope.row.enabled ? 'warning' : 'success'"
                @click="changeStatus(scope.row)"
              >
                {{ scope.row.enabled ? '停用' : '启用' }}
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

    <el-dialog v-model="formVisible" :title="formMode === 'create' ? '新增字典项' : '编辑字典项'" width="640px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="字典类型" prop="dictType" required>
          <el-input v-model="form.dictType" placeholder="如: project_status" />
        </el-form-item>
        <el-form-item label="字典Key" prop="dictKey" required>
          <el-input v-model="form.dictKey" placeholder="如: draft" />
        </el-form-item>
        <el-form-item label="中文Value" prop="dictValue" required>
          <el-input v-model="form.dictValue" />
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder" required>
          <el-input-number v-model="form.sortOrder" :min="0" :step="10" style="width: 100%" />
        </el-form-item>
        <el-form-item v-if="formMode === 'create'" label="启用状态" required>
          <el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" />
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
import { useDictStore } from '@/stores/dict'
import {
  createAdminDictItem,
  getAdminDictPage,
  getAdminDictTypes,
  updateAdminDictItem,
  updateAdminDictItemStatus,
  type DictAdminItem,
} from '@/api/dict'

const dictStore = useDictStore()

const loading = ref(false)
const saving = ref(false)
const rows = ref<DictAdminItem[]>([])
const dictTypes = ref<string[]>([])
const page = reactive({ current: 1, size: 20, total: 0 })
const query = reactive<{ dictType: string; keyword: string; enabled: boolean | undefined }>({
  dictType: '',
  keyword: '',
  enabled: undefined,
})

const formVisible = ref(false)
const formRef = ref<FormInstance>()
const formMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const form = reactive({
  dictType: '',
  dictKey: '',
  dictValue: '',
  sortOrder: 10,
  enabled: true,
  remark: '',
})

const rules: FormRules = {
  dictType: [
    { required: true, message: '请输入字典类型', trigger: 'blur' },
    { pattern: /^[a-z][a-z0-9_]{1,63}$/, message: '格式: 小写字母开头, 仅字母数字下划线', trigger: 'blur' },
  ],
  dictKey: [
    { required: true, message: '请输入字典Key', trigger: 'blur' },
    { pattern: /^[A-Za-z][A-Za-z0-9_.-]{0,63}$/, message: '格式不正确', trigger: 'blur' },
  ],
  dictValue: [{ required: true, message: '请输入中文Value', trigger: 'blur' }],
  sortOrder: [{ required: true, message: '请输入排序', trigger: 'change' }],
}

function resetForm() {
  form.dictType = ''
  form.dictKey = ''
  form.dictValue = ''
  form.sortOrder = 10
  form.enabled = true
  form.remark = ''
}

async function loadTypes() {
  const { data } = await getAdminDictTypes()
  dictTypes.value = data.data || []
}

async function load() {
  loading.value = true
  try {
    const { data } = await getAdminDictPage({
      current: page.current,
      size: page.size,
      dictType: query.dictType || undefined,
      keyword: query.keyword || undefined,
      enabled: query.enabled,
    })
    rows.value = data.data.records || []
    page.total = data.data.total || 0
  } catch {
    rows.value = []
    page.total = 0
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

function openCreate() {
  formMode.value = 'create'
  editingId.value = null
  resetForm()
  formVisible.value = true
}

function openEdit(row: DictAdminItem) {
  formMode.value = 'edit'
  editingId.value = row.id
  form.dictType = row.dictType
  form.dictKey = row.dictKey
  form.dictValue = row.dictValue
  form.sortOrder = row.sortOrder
  form.enabled = row.enabled
  form.remark = row.remark || ''
  formVisible.value = true
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    if (formMode.value === 'create') {
      await createAdminDictItem({
        dictType: form.dictType,
        dictKey: form.dictKey,
        dictValue: form.dictValue,
        sortOrder: form.sortOrder,
        enabled: form.enabled,
        remark: form.remark || undefined,
      })
    } else if (editingId.value) {
      await updateAdminDictItem(editingId.value, {
        dictType: form.dictType,
        dictKey: form.dictKey,
        dictValue: form.dictValue,
        sortOrder: form.sortOrder,
        remark: form.remark || undefined,
      })
    }
    await dictStore.reload()
    await loadTypes()
    await load()
    ElMessage.success('保存成功')
    formVisible.value = false
  } finally {
    saving.value = false
  }
}

async function changeStatus(row: DictAdminItem) {
  try {
    const target = !row.enabled
    await ElMessageBox.confirm(
      `确认${target ? '启用' : '停用'}字典项 ${row.dictType}.${row.dictKey}？`,
      '状态变更确认',
      { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' },
    )
    await updateAdminDictItemStatus(row.id, target)
    await dictStore.reload()
    await load()
    ElMessage.success('状态已更新')
  } catch {
    // canceled
  }
}

async function refreshGlobalDict() {
  await dictStore.reload()
  ElMessage.success('全局字典缓存已刷新')
}

onMounted(async () => {
  await Promise.all([loadTypes(), load()])
})
</script>

