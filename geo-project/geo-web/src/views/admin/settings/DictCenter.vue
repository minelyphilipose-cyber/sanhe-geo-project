<template>
  <div class="dict-center-page admin-page">
    <div class="admin-page-header dict-header">
      <div>
        <div class="admin-page-kicker">系统配置</div>
        <h1 class="admin-page-title">字典中心</h1>
        <div class="admin-page-subtitle">统一维护业务枚举、状态标签和页面选项，保证前后端展示口径一致。</div>
      </div>
      <div class="admin-page-actions">
        <el-button @click="refreshGlobalDict">刷新全局字典缓存</el-button>
        <el-button type="primary" @click="openCreate">新增字典项</el-button>
      </div>
    </div>

    <el-card shadow="never" class="admin-surface dict-toolbar-card">
      <div class="dict-toolbar">
        <el-select v-model="query.dictType" class="filter-type" clearable filterable placeholder="字典类型" @change="onSearch">
          <el-option v-for="type in dictTypes" :key="type" :label="type" :value="type" />
        </el-select>
        <el-select v-model="query.enabled" class="filter-status" clearable placeholder="状态" @change="onSearch">
          <el-option label="启用" :value="true" />
          <el-option label="停用" :value="false" />
        </el-select>
        <el-input
          v-model="query.keyword"
          class="filter-keyword"
          clearable
          placeholder="搜索 key/value/备注"
          @keyup.enter="onSearch"
        />
        <el-button type="primary" plain @click="onSearch">查询</el-button>
      </div>
    </el-card>

    <div class="admin-metric-grid dict-metric-grid">
      <div class="admin-metric-card" style="--metric-accent: #2563eb; --metric-tone: #eff6ff">
        <span class="admin-metric-label">字典项总数</span>
        <strong class="admin-metric-value">{{ page.total }}</strong>
        <span class="admin-metric-hint">当前筛选结果</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #059669; --metric-tone: #ecfdf5">
        <span class="admin-metric-label">启用项</span>
        <strong class="admin-metric-value">{{ enabledCount }}</strong>
        <span class="admin-metric-hint">当前页可用字典项</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #f59e0b; --metric-tone: #fffbeb">
        <span class="admin-metric-label">停用项</span>
        <strong class="admin-metric-value">{{ disabledCount }}</strong>
        <span class="admin-metric-hint">当前页已停用字典项</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #7c3aed; --metric-tone: #f5f3ff">
        <span class="admin-metric-label">字典类型</span>
        <strong class="admin-metric-value">{{ dictTypes.length }}</strong>
        <span class="admin-metric-hint">系统已加载类型数</span>
      </div>
    </div>

    <el-card shadow="never" class="admin-table-card dict-table-card">
      <div class="table-header">
        <div>
          <div class="table-title">字典项列表</div>
          <div class="table-subtitle">按类型、Key、中文值和启用状态核对系统枚举配置。</div>
        </div>
        <div class="chips">
          <span class="chip chip-muted">当前页 {{ rows.length }}</span>
          <span class="chip chip-success">启用 {{ enabledCount }}</span>
          <span class="chip chip-warning">停用 {{ disabledCount }}</span>
        </div>
      </div>
      <DataState :loading="loading" :empty="!loading && rows.length === 0" empty-text="暂无字典项">
        <el-table :data="rows" border table-layout="fixed">
          <el-table-column prop="dictValue" label="字典值" min-width="180" show-overflow-tooltip />
          <el-table-column label="字典项" min-width="260" show-overflow-tooltip>
            <template #default="scope">
              <div class="admin-entity-cell">
                <div class="admin-entity-avatar dict-avatar" :class="scope.row.enabled ? 'is-success' : 'is-muted'">
                  {{ dictInitial(scope.row.dictType) }}
                </div>
                <div class="min-w-0">
                  <div class="admin-entity-main">{{ scope.row.dictType }}</div>
                  <div class="admin-entity-sub">{{ scope.row.dictKey }}</div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="sortOrder" label="排序" width="90" />
          <el-table-column label="状态" width="100">
            <template #default="scope">
              <span class="admin-status-tag" :class="scope.row.enabled ? 'is-success' : 'is-muted'">
                {{ scope.row.enabled ? '启用' : '停用' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="220" show-overflow-tooltip>
            <template #default="scope">{{ scope.row.remark || '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="170" fixed="right">
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
      </DataState>

      <div class="admin-table-footer">
        <el-pagination
          background
          layout="prev, pager, next, total"
          :current-page="page.current"
          :page-size="page.size"
          :total="page.total"
          @current-change="onPageChange"
        />
      </div>
    </el-card>

    <el-dialog v-model="formVisible" :title="formMode === 'create' ? '新增字典项' : '编辑字典项'" width="760px" class="admin-editor-dialog dict-editor-dialog">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="dict-form">
        <section class="form-section">
          <div class="form-section-head">
            <div>
              <span>基础信息</span>
              <strong>字典类型、Key 与中文展示值</strong>
            </div>
          </div>
          <div class="form-grid is-two">
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
              <el-input-number v-model="form.sortOrder" :min="0" :step="10" />
            </el-form-item>
          </div>
        </section>

        <section class="form-section">
          <div class="form-section-head">
            <div>
              <span>状态与备注</span>
              <strong>新增时可设置启用状态，编辑时通过列表切换</strong>
            </div>
          </div>
          <div class="form-grid is-two">
            <el-form-item v-if="formMode === 'create'" label="启用状态" required>
              <el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" />
            </el-form-item>
            <el-form-item label="备注" class="is-full">
              <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="补充该字典项的使用场景或维护说明" />
            </el-form-item>
          </div>
        </section>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
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
const route = useRoute()

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
const enabledCount = computed(() => rows.value.filter((item) => item.enabled).length)
const disabledCount = computed(() => rows.value.filter((item) => !item.enabled).length)

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

function dictInitial(value?: string | null) {
  const text = String(value || '').trim()
  return text ? Array.from(text)[0].toUpperCase() : 'D'
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
  if (query.dictType) {
    form.dictType = query.dictType
  }
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
  if (typeof route.query.dictType === 'string') {
    query.dictType = route.query.dictType
  }
  await Promise.all([loadTypes(), load()])
})
</script>

<style scoped>
.dict-header {
  align-items: center;
}

.dict-toolbar-card :deep(.el-card__body) {
  padding: 12px;
}

.dict-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.filter-type {
  width: 200px;
}

.filter-status {
  width: 128px;
}

.filter-keyword {
  width: 260px;
}

.dict-metric-grid {
  margin-bottom: 0;
}

.dict-table-card :deep(.el-card__body) {
  padding: 0;
}

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  padding: 16px 16px 12px;
  border-bottom: 1px solid var(--admin-panel-border-soft);
  background: linear-gradient(90deg, #f8fbff 0%, #ffffff 55%, #f0fdf4 100%);
}

.table-title {
  color: var(--admin-text-strong);
  font-size: 16px;
  font-weight: 800;
}

.table-subtitle {
  margin-top: 4px;
  color: var(--admin-text-muted);
  font-size: 12px;
}

.chips {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.chip {
  display: inline-flex;
  align-items: center;
  border-radius: 14px;
  padding: 3px 9px;
  font-size: 12px;
  font-weight: 700;
}

.chip-muted {
  background: #f3f4f6;
  color: #6b7280;
}

.chip-success {
  background: #ecfdf5;
  color: #047857;
}

.chip-warning {
  background: #fffbeb;
  color: #b45309;
}

.dict-avatar.is-success {
  background: linear-gradient(135deg, #2563eb, #14b8a6);
}

.dict-avatar.is-muted {
  background: linear-gradient(135deg, #64748b, #94a3b8);
}

.dict-editor-dialog :deep(.el-dialog__body) {
  background: #f8fafc;
}

.dict-form {
  display: grid;
  gap: 14px;
}

.form-section {
  overflow: hidden;
  border: 1px solid #dbeafe;
  border-radius: 14px;
  background: #ffffff;
  box-shadow: 0 10px 22px rgba(15, 23, 42, 0.04);
}

.form-section-head {
  padding: 13px 16px 11px;
  border-bottom: 1px solid #e7edf5;
  background: linear-gradient(90deg, #f8fbff 0%, #ffffff 62%, #f0fdf4 100%);
}

.form-section-head span {
  display: block;
  color: #2563eb;
  font-size: 12px;
  font-weight: 800;
}

.form-section-head strong {
  display: block;
  margin-top: 4px;
  color: #0f172a;
  font-size: 15px;
  font-weight: 800;
}

.form-grid {
  display: grid;
  gap: 13px 14px;
  padding: 16px;
}

.form-grid.is-two {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.form-grid .is-full {
  grid-column: 1 / -1;
}

.dict-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.dict-form :deep(.el-form-item__label) {
  padding-bottom: 7px;
  color: #475569;
  font-size: 12px;
  font-weight: 800;
  line-height: 1.2;
}

.dict-form :deep(.el-input-number) {
  width: 100%;
}

@media (max-width: 768px) {
  .dict-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .filter-type,
  .filter-status,
  .filter-keyword,
  .dict-toolbar .el-button {
    width: 100%;
  }

  .form-grid.is-two {
    grid-template-columns: 1fr;
  }
}
</style>
