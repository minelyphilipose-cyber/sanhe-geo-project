<template>
  <div>
    <div class="mb-4 flex items-center justify-between gap-3">
      <div class="flex items-center gap-2">
        <el-input v-model="query.keyword" placeholder="搜索平台编码/名称/模型" clearable style="width: 260px" @keyup.enter="load" />
        <el-select v-model="query.priorityLevel" placeholder="平台等级" clearable style="width: 130px" @change="load">
          <el-option
            v-for="item in dictStore.options('platform_priority')"
            :key="item.dictKey"
            :label="item.dictValue"
            :value="item.dictKey"
          />
        </el-select>
        <el-select v-model="query.enabled" placeholder="状态" clearable style="width: 130px" @change="load">
          <el-option label="启用" :value="true" />
          <el-option label="停用" :value="false" />
        </el-select>
        <el-button @click="load">查询</el-button>
      </div>
      <el-button v-if="canManage" type="primary" @click="openCreate">新增平台</el-button>
    </div>

    <el-card>
      <DataState :loading="loading" :empty="!loading && rows.length === 0" empty-text="暂无平台配置">
        <el-table :data="rows" border>
          <el-table-column prop="platformCode" label="平台编码" min-width="130" />
          <el-table-column prop="platformName" label="平台名称" min-width="130" />
          <el-table-column label="等级" width="120">
            <template #default="scope">{{ dictStore.label('platform_priority', scope.row.priorityLevel) }}</template>
          </el-table-column>
          <el-table-column prop="modelName" label="Model名称" min-width="140" />
          <el-table-column label="降级处理" width="100">
            <template #default="scope">
              <el-tag :type="scope.row.degraded ? 'warning' : 'info'">{{ scope.row.degraded ? '是' : '否' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="degradedReason" label="降级原因" min-width="220" show-overflow-tooltip>
            <template #default="scope">{{ scope.row.degradedReason || '-' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="scope">
              <el-tag :type="scope.row.enabled ? 'success' : 'info'">{{ scope.row.enabled ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="180">
            <template #default="scope">{{ scope.row.createdAt || '-' }}</template>
          </el-table-column>
          <el-table-column v-if="canManage" label="操作" width="180" fixed="right">
            <template #default="scope">
              <el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button>
              <el-button link type="danger" @click="remove(scope.row)">删除</el-button>
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

    <el-dialog v-model="dialogVisible" :title="mode === 'create' ? '新增平台配置' : '编辑平台配置'" width="900px">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-row :gutter="12">
          <el-col :xs="24" :md="8">
            <el-form-item label="平台编码" prop="platformCode">
              <el-input v-model="form.platformCode" placeholder="如: doubao / deepseek" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8">
            <el-form-item label="平台名称" prop="platformName">
              <el-input v-model="form.platformName" placeholder="如: 豆包" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8">
            <el-form-item label="平台等级" prop="priorityLevel">
              <el-select v-model="form.priorityLevel" style="width: 100%">
                <el-option
                  v-for="item in dictStore.options('platform_priority')"
                  :key="item.dictKey"
                  :label="item.dictValue"
                  :value="item.dictKey"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="12">
          <el-col :xs="24" :md="12">
            <el-form-item label="API URL" prop="apiUrl">
              <el-input v-model="form.apiUrl" placeholder="https://xxx/v1" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="API Key" prop="apiKey">
              <el-input v-model="form.apiKey" type="password" show-password placeholder="输入平台 API Key" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="12">
          <el-col :xs="24" :md="12">
            <el-form-item label="primary_key_ref" prop="primaryKeyRef">
              <el-input v-model="form.primaryKeyRef" placeholder="如: vault://keys/doubao-primary" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="backup_key_ref" prop="backupKeyRef">
              <el-input v-model="form.backupKeyRef" placeholder="如: vault://keys/doubao-backup" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="12">
          <el-col :xs="24" :md="8">
            <el-form-item label="backup_provider_name" prop="backupProviderName">
              <el-input v-model="form.backupProviderName" placeholder="如: deepseek" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8">
            <el-form-item label="backup_api_url" prop="backupApiUrl">
              <el-input v-model="form.backupApiUrl" placeholder="https://backup.xxx/v1" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8">
            <el-form-item label="backup_model_id" prop="backupModelId">
              <el-input v-model="form.backupModelId" placeholder="如: deepseek-chat" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="12">
          <el-col :xs="24" :md="12">
            <el-form-item label="Model ID" prop="modelId">
              <el-input v-model="form.modelId" placeholder="如: deepseek-chat" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="Model名称" prop="modelName">
              <el-input v-model="form.modelName" placeholder="如: DeepSeek Chat" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="12">
          <el-col :xs="24" :md="12">
            <el-form-item label="启用状态" prop="enabled">
              <el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="降级处理" prop="degraded">
              <el-switch v-model="form.degraded" active-text="是" inactive-text="否" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="降级原因" prop="degradedReason">
          <el-input v-model="form.degradedReason" type="textarea" :rows="2" placeholder="降级处理开启时建议填写" />
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { useDictStore } from '@/stores/dict'
import { useUserStore } from '@/stores/user'
import DataState from '@/components/ui/DataState.vue'
import { createPlatformConfig, deletePlatformConfig, getPlatformConfigPage, updatePlatformConfig } from '@/api/platformConfig'
import type { AIPlatformConfigItem } from '@/types'

const dictStore = useDictStore()
const userStore = useUserStore()
const canManage = computed(() => userStore.hasPermission('user.manage'))

const loading = ref(false)
const saving = ref(false)
const rows = ref<AIPlatformConfigItem[]>([])
const page = reactive({ current: 1, size: 10, total: 0 })
const query = reactive<{ keyword: string; priorityLevel: string; enabled: boolean | undefined }>({
  keyword: '',
  priorityLevel: '',
  enabled: undefined,
})

const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const mode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)

const form = reactive({
  platformCode: '',
  platformName: '',
  priorityLevel: 'P1',
  apiKey: '',
  primaryKeyRef: '',
  backupKeyRef: '',
  backupProviderName: '',
  backupApiUrl: '',
  backupModelId: '',
  apiUrl: '',
  modelId: '',
  modelName: '',
  enabled: true,
  degraded: false,
  degradedReason: '',
  remark: '',
})

const rules: FormRules = {
  platformCode: [
    { required: true, message: '请输入平台编码', trigger: 'blur' },
    { pattern: /^[a-z][a-z0-9_-]{1,63}$/, message: '编码格式: 小写字母开头, 支持字母数字_-', trigger: 'blur' },
  ],
  platformName: [{ required: true, message: '请输入平台名称', trigger: 'blur' }],
  priorityLevel: [{ required: true, message: '请选择平台等级', trigger: 'change' }],
  apiKey: [
    {
      validator: (_rule, value, callback) => {
        const hasApiKey = !!(value && String(value).trim())
        const hasPrimaryRef = !!(form.primaryKeyRef && form.primaryKeyRef.trim())
        if (hasApiKey || hasPrimaryRef) callback()
        else callback(new Error('API Key 与 primary_key_ref 至少填写一个'))
      },
      trigger: ['blur', 'change'],
    },
  ],
  apiUrl: [{ required: true, message: '请输入API URL', trigger: 'blur' }],
  modelId: [{ required: true, message: '请输入Model ID', trigger: 'blur' }],
  modelName: [{ required: true, message: '请输入Model名称', trigger: 'blur' }],
}

watch(
  () => form.degraded,
  (v) => {
    if (!v) {
      form.degradedReason = ''
    }
  },
)

function resetForm() {
  form.platformCode = ''
  form.platformName = ''
  form.priorityLevel = 'P1'
  form.apiKey = ''
  form.primaryKeyRef = ''
  form.backupKeyRef = ''
  form.backupProviderName = ''
  form.backupApiUrl = ''
  form.backupModelId = ''
  form.apiUrl = ''
  form.modelId = ''
  form.modelName = ''
  form.enabled = true
  form.degraded = false
  form.degradedReason = ''
  form.remark = ''
}

async function load() {
  loading.value = true
  try {
    const { data } = await getPlatformConfigPage({
      current: page.current,
      size: page.size,
      keyword: query.keyword || undefined,
      priorityLevel: query.priorityLevel || undefined,
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

function onPageChange(v: number) {
  page.current = v
  load()
}

function openCreate() {
  mode.value = 'create'
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: AIPlatformConfigItem) {
  mode.value = 'edit'
  editingId.value = row.id
  form.platformCode = row.platformCode
  form.platformName = row.platformName
  form.priorityLevel = row.priorityLevel
  form.apiKey = row.apiKey
  form.primaryKeyRef = row.primaryKeyRef || ''
  form.backupKeyRef = row.backupKeyRef || ''
  form.backupProviderName = row.backupProviderName || ''
  form.backupApiUrl = row.backupApiUrl || ''
  form.backupModelId = row.backupModelId || ''
  form.apiUrl = row.apiUrl
  form.modelId = row.modelId
  form.modelName = row.modelName
  form.enabled = row.enabled
  form.degraded = row.degraded
  form.degradedReason = row.degradedReason || ''
  form.remark = row.remark || ''
  dialogVisible.value = true
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (form.degraded && !form.degradedReason.trim()) {
    ElMessage.warning('开启降级处理时，请填写降级原因')
    return
  }
  saving.value = true
  try {
    const payload = {
      platformCode: form.platformCode.trim(),
      platformName: form.platformName.trim(),
      priorityLevel: form.priorityLevel,
      apiKey: form.apiKey.trim(),
      primaryKeyRef: form.primaryKeyRef.trim() || undefined,
      backupKeyRef: form.backupKeyRef.trim() || undefined,
      backupProviderName: form.backupProviderName.trim() || undefined,
      backupApiUrl: form.backupApiUrl.trim() || undefined,
      backupModelId: form.backupModelId.trim() || undefined,
      apiUrl: form.apiUrl.trim(),
      modelId: form.modelId.trim(),
      modelName: form.modelName.trim(),
      enabled: form.enabled,
      degraded: form.degraded,
      degradedReason: form.degraded ? form.degradedReason.trim() : undefined,
      remark: form.remark || undefined,
    }
    if (mode.value === 'create') {
      await createPlatformConfig(payload)
    } else if (editingId.value) {
      await updatePlatformConfig(editingId.value, payload)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function remove(row: AIPlatformConfigItem) {
  try {
    await ElMessageBox.confirm(
      `确认删除平台「${row.platformName}」？`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' },
    )
    await deletePlatformConfig(row.id)
    ElMessage.success('删除成功')
    await load()
  } catch (err: any) {
    if (err === 'cancel' || err === 'close') return
  }
}

onMounted(async () => {
  await dictStore.ensureLoaded()
  await load()
})
</script>
