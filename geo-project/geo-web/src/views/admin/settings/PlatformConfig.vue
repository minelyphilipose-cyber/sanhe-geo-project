<template>
  <div class="platform-config-page admin-page">
    <div class="admin-page-header platform-config-header">
      <div>
        <div class="admin-page-kicker">系统配置</div>
        <h1 class="admin-page-title">AI平台配置</h1>
        <div class="admin-page-subtitle">维护平台模型、密钥引用、调用控制和业务能力开关。</div>
      </div>
      <div class="admin-page-actions">
        <el-button v-if="canManage" type="primary" @click="openCreate">新增平台</el-button>
      </div>
    </div>

    <el-card shadow="never" class="admin-surface platform-toolbar-card">
      <div class="platform-toolbar">
        <el-input v-model="query.keyword" class="filter-keyword" placeholder="搜索平台编码/名称/模型" clearable @keyup.enter="load" />
        <el-select v-model="query.priorityLevel" class="filter-level" placeholder="平台等级" clearable @change="load">
          <el-option
            v-for="item in dictStore.options('platform_priority')"
            :key="item.dictKey"
            :label="item.dictValue"
            :value="item.dictKey"
          />
        </el-select>
        <el-select v-model="query.enabled" class="filter-status" placeholder="状态" clearable @change="load">
          <el-option label="启用" :value="true" />
          <el-option label="停用" :value="false" />
        </el-select>
        <el-button type="primary" plain @click="load">查询</el-button>
      </div>
    </el-card>

    <div class="admin-metric-grid platform-metric-grid">
      <div class="admin-metric-card" style="--metric-accent: #2563eb; --metric-tone: #eff6ff">
        <span class="admin-metric-label">平台总数</span>
        <strong class="admin-metric-value">{{ page.total }}</strong>
        <span class="admin-metric-hint">当前筛选结果</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #059669; --metric-tone: #ecfdf5">
        <span class="admin-metric-label">启用平台</span>
        <strong class="admin-metric-value">{{ enabledCount }}</strong>
        <span class="admin-metric-hint">当前页可用配置</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #f59e0b; --metric-tone: #fffbeb">
        <span class="admin-metric-label">降级处理</span>
        <strong class="admin-metric-value">{{ degradedCount }}</strong>
        <span class="admin-metric-hint">需要关注模型链路</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #7c3aed; --metric-tone: #f5f3ff">
        <span class="admin-metric-label">问题池生成</span>
        <strong class="admin-metric-value">{{ questionCount }}</strong>
        <span class="admin-metric-hint">已开启拓词能力</span>
      </div>
    </div>

    <el-card shadow="never" class="admin-table-card platform-table-card">
      <div class="table-header">
        <div>
          <div class="table-title">平台配置列表</div>
          <div class="table-subtitle">按平台等级、启用状态和降级状态核对模型配置。</div>
        </div>
        <div class="chips">
          <span class="chip chip-muted">当前页 {{ rows.length }}</span>
          <span class="chip chip-success">启用 {{ enabledCount }}</span>
          <span class="chip chip-warning">降级 {{ degradedCount }}</span>
        </div>
      </div>

      <DataState :loading="loading" :empty="!loading && rows.length === 0" empty-text="暂无平台配置">
        <el-table :data="rows" border table-layout="fixed">
          <el-table-column label="平台" min-width="220" show-overflow-tooltip>
            <template #default="scope">
              <div class="admin-entity-cell">
                <div class="admin-entity-avatar platform-avatar" :class="enabledClass(scope.row.enabled)">
                  {{ platformInitial(scope.row.platformName) }}
                </div>
                <div class="min-w-0">
                  <div class="admin-entity-main">{{ scope.row.platformName }}</div>
                  <div class="admin-entity-sub">{{ scope.row.platformCode }}</div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="等级" width="120">
            <template #default="scope">
              <span class="priority-pill" :class="priorityClass(scope.row.priorityLevel)">
                {{ dictStore.label('platform_priority', scope.row.priorityLevel) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="modelName" label="高性能版本" min-width="140" />
          <el-table-column prop="lowModelId" label="低性能版本" min-width="140">
            <template #default="scope">{{ scope.row.lowModelId || '-' }}</template>
          </el-table-column>
          <el-table-column prop="concurrencyLimit" label="并发上限" width="100">
            <template #default="scope">{{ scope.row.concurrencyLimit ?? 1 }}</template>
          </el-table-column>
          <el-table-column label="能力开关" min-width="210">
            <template #default="scope">
              <div class="capability-tags">
                <span class="capability-tag" :class="scope.row.enabledForGeoQuestion ? 'is-success' : 'is-muted'">问题池</span>
                <span class="capability-tag" :class="isPresaleEnabled(scope.row) ? 'is-success' : 'is-muted'">售前</span>
                <span class="capability-tag" :class="scope.row.presaleEvaluateEnabled ? 'is-success' : 'is-muted'">评估</span>
                <span class="capability-tag" :class="scope.row.enabledForArticle ? 'is-success' : 'is-muted'">文章</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="降级处理" width="100">
            <template #default="scope">
              <span class="admin-status-tag" :class="scope.row.degraded ? 'is-warning' : 'is-muted'">
                {{ scope.row.degraded ? '是' : '否' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="degradedReason" label="降级原因" min-width="220" show-overflow-tooltip>
            <template #default="scope">{{ scope.row.degradedReason || '-' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="scope">
              <span class="admin-status-tag" :class="enabledClass(scope.row.enabled)">
                {{ scope.row.enabled ? '启用' : '停用' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="180">
            <template #default="scope">{{ scope.row.createdAt || '-' }}</template>
          </el-table-column>
          <el-table-column v-if="canManage" label="操作" width="260" fixed="right">
            <template #default="scope">
              <el-tooltip
                :content="presaleActionTooltip(scope.row)"
                :disabled="!presaleActionDisabled(scope.row)"
                placement="top"
              >
                <el-button
                  link
                  :type="isPresaleEnabled(scope.row) ? 'warning' : 'success'"
                  :disabled="presaleActionDisabled(scope.row)"
                  @click="togglePresaleEnabled(scope.row)"
                >
                  {{ isPresaleEnabled(scope.row) ? '售前已启用' : '售前已停用' }}
                </el-button>
              </el-tooltip>
              <el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button>
              <el-button link type="danger" @click="remove(scope.row)">删除</el-button>
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

    <el-dialog v-model="dialogVisible" :title="mode === 'create' ? '新增平台配置' : '编辑平台配置'" width="960px" class="admin-editor-dialog platform-editor-dialog">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="platform-config-form">
        <section class="form-section">
          <div class="form-section-head">
            <div>
              <span>基础信息</span>
              <strong>平台识别与优先级</strong>
            </div>
          </div>
          <div class="form-grid is-three">
            <el-form-item label="平台编码" prop="platformCode">
              <el-input v-model="form.platformCode" placeholder="如: doubao / deepseek" />
            </el-form-item>
            <el-form-item label="平台名称" prop="platformName">
              <el-input v-model="form.platformName" placeholder="如: 豆包" />
            </el-form-item>
            <el-form-item label="平台等级" prop="priorityLevel">
              <el-select v-model="form.priorityLevel">
                <el-option
                  v-for="item in dictStore.options('platform_priority')"
                  :key="item.dictKey"
                  :label="item.dictValue"
                  :value="item.dictKey"
                />
              </el-select>
            </el-form-item>
          </div>
        </section>

        <section class="form-section">
          <div class="form-section-head">
            <div>
              <span>密钥与接入</span>
              <strong>API 地址、密钥与 Key 引用</strong>
            </div>
            <em>API Key 与 primary_key_ref 至少填写一个</em>
          </div>
          <div class="form-grid is-two">
            <el-form-item label="API URL" prop="apiUrl">
              <el-input v-model="form.apiUrl" placeholder="https://xxx/v1" />
            </el-form-item>
            <el-form-item label="API Key" prop="apiKey">
              <el-input v-model="form.apiKey" type="password" show-password placeholder="输入平台 API Key" />
            </el-form-item>
            <el-form-item label="primary_key_ref" prop="primaryKeyRef">
              <el-input v-model="form.primaryKeyRef" placeholder="如: vault://keys/doubao-primary" />
            </el-form-item>
            <el-form-item label="backup_key_ref" prop="backupKeyRef">
              <el-input v-model="form.backupKeyRef" placeholder="如: vault://keys/doubao-backup" />
            </el-form-item>
          </div>
        </section>

        <section class="form-section">
          <div class="form-section-head">
            <div>
              <span>模型能力</span>
              <strong>主模型、低性能模型与备用服务商</strong>
            </div>
          </div>
          <div class="form-grid is-two">
            <el-form-item label="高性能版本(Model ID)" prop="modelId">
              <el-input v-model="form.modelId" placeholder="如: gpt-5.4" />
            </el-form-item>
            <el-form-item label="Model名称" prop="modelName">
              <el-input v-model="form.modelName" placeholder="如: DeepSeek Chat" />
            </el-form-item>
            <el-form-item label="低性能版本(Model ID)" prop="lowModelId">
              <el-input v-model="form.lowModelId" placeholder="如: gpt-5.3" />
            </el-form-item>
            <el-form-item label="并发上限" prop="concurrencyLimit">
              <el-input-number v-model="form.concurrencyLimit" :min="1" :max="10000" :step="1" />
            </el-form-item>
          </div>
          <div class="form-grid is-three compact-grid">
            <el-form-item label="backup_provider_name" prop="backupProviderName">
              <el-input v-model="form.backupProviderName" placeholder="如: deepseek" />
            </el-form-item>
            <el-form-item label="backup_api_url" prop="backupApiUrl">
              <el-input v-model="form.backupApiUrl" placeholder="https://backup.xxx/v1" />
            </el-form-item>
            <el-form-item label="backup_model_id" prop="backupModelId">
              <el-input v-model="form.backupModelId" placeholder="如: deepseek-chat" />
            </el-form-item>
          </div>
        </section>

        <section class="form-section">
          <div class="form-section-head">
            <div>
              <span>业务能力</span>
              <strong>启用、拓词、售前、文章与降级处理</strong>
            </div>
          </div>
          <div class="switch-grid">
            <el-form-item label="启用状态" prop="enabled">
              <el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" />
            </el-form-item>
            <el-form-item label="售前能力">
              <el-switch v-model="form.enabledForPresale" active-text="启用" inactive-text="停用" />
            </el-form-item>
            <el-form-item label="文章能力">
              <el-switch v-model="form.enabledForArticle" active-text="启用" inactive-text="停用" />
            </el-form-item>
            <el-form-item label="拓词问题池">
              <el-switch
                v-model="form.enabledForGeoQuestion"
                active-text="启用"
                inactive-text="停用"
                :disabled="!canEnableGeoQuestion(form.platformCode)"
              />
            </el-form-item>
            <el-form-item label="售前评估模型">
              <el-switch
                v-model="form.presaleEvaluateEnabled"
                active-text="启用"
                inactive-text="停用"
                :disabled="!canEnablePresaleEvaluate(form.platformCode)"
              />
            </el-form-item>
            <el-form-item label="降级处理" prop="degraded">
              <el-switch v-model="form.degraded" active-text="是" inactive-text="否" />
            </el-form-item>
          </div>
          <div class="form-grid is-two">
            <el-form-item label="降级原因" prop="degradedReason">
              <el-input v-model="form.degradedReason" type="textarea" :rows="2" placeholder="降级处理开启时建议填写" />
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" />
            </el-form-item>
          </div>
        </section>
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
import {
  createPlatformConfig,
  deletePlatformConfig,
  getPlatformConfigPage,
  updatePlatformConfig,
  updatePresaleEnabled,
} from '@/api/platformConfig'
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

const enabledCount = computed(() => rows.value.filter((item) => item.enabled).length)
const degradedCount = computed(() => rows.value.filter((item) => item.degraded).length)
const questionCount = computed(() => rows.value.filter((item) => item.enabledForGeoQuestion).length)

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
  lowModelId: '',
  modelName: '',
  concurrencyLimit: 1,
  enabled: true,
  enabledForPresale: true,
  enabledForArticle: false,
  enabledForGeoQuestion: false,
  presaleEvaluateEnabled: false,
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
  concurrencyLimit: [{ required: true, type: 'number', min: 1, message: '并发上限必须大于0', trigger: 'change' }],
}

watch(
  () => form.degraded,
  (v) => {
    if (!v) {
      form.degradedReason = ''
    }
  },
)
watch(
  () => form.platformCode,
  (value) => {
    if (!canEnableGeoQuestion(value)) {
      form.enabledForGeoQuestion = false
    }
    if (!canEnablePresaleEvaluate(value)) {
      form.presaleEvaluateEnabled = false
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
  form.lowModelId = ''
  form.modelName = ''
  form.concurrencyLimit = 1
  form.enabled = true
  form.enabledForPresale = true
  form.enabledForArticle = false
  form.enabledForGeoQuestion = false
  form.presaleEvaluateEnabled = false
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

function enabledClass(enabled?: boolean) {
  return enabled ? 'is-success' : 'is-muted'
}

function priorityClass(priority?: string) {
  if (priority === 'P0') return 'is-critical'
  if (priority === 'P1') return 'is-high'
  return 'is-normal'
}

function platformInitial(value?: string | null) {
  const text = String(value || '').trim()
  return text ? Array.from(text)[0] : '平'
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
  form.lowModelId = row.lowModelId || ''
  form.modelName = row.modelName
  form.concurrencyLimit = row.concurrencyLimit || 1
  form.enabled = row.enabled
  form.enabledForPresale = row.enabledForPresale ?? true
  form.enabledForArticle = !!row.enabledForArticle
  form.enabledForGeoQuestion = !!row.enabledForGeoQuestion
  form.presaleEvaluateEnabled = !!row.presaleEvaluateEnabled
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
      lowModelId: form.lowModelId.trim() || undefined,
      modelName: form.modelName.trim(),
      concurrencyLimit: form.concurrencyLimit,
      enabled: form.enabled,
      enabledForPresale: form.enabledForPresale,
      enabledForArticle: form.enabledForArticle,
      enabledForGeoQuestion: form.enabledForGeoQuestion,
      presaleEvaluateEnabled: form.presaleEvaluateEnabled,
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

function isPresaleEnabled(row: AIPlatformConfigItem) {
  return row.enabledForPresale ?? true
}

function hasLowModel(row: AIPlatformConfigItem) {
  return !!(row.lowModelId && row.lowModelId.trim())
}

function presaleActionDisabled(row: AIPlatformConfigItem) {
  return !isPresaleEnabled(row) && !hasLowModel(row)
}

function presaleActionTooltip(row: AIPlatformConfigItem) {
  return presaleActionDisabled(row) ? '请先配置低性能模型(low_model_id)' : ''
}

const presaleEvaluateCodes = new Set(['deepseek', 'doubao', 'qwen', 'mimo', 'zhipu'])
const geoQuestionCodes = new Set(['qwen', 'deepseek', 'mimo'])

function canEnablePresaleEvaluate(platformCode: string) {
  return presaleEvaluateCodes.has((platformCode || '').trim())
}

function canEnableGeoQuestion(platformCode: string) {
  return geoQuestionCodes.has((platformCode || '').trim())
}

async function togglePresaleEnabled(row: AIPlatformConfigItem) {
  const target = !isPresaleEnabled(row)
  const actionText = target ? '启用' : '停用'
  if (target && !hasLowModel(row)) {
    ElMessage.warning('请先配置低性能模型(low_model_id)')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认${actionText}平台「${row.platformName}」的售前能力？`,
      `${actionText}确认`,
      { type: target ? 'success' : 'warning', confirmButtonText: '确认', cancelButtonText: '取消' },
    )
    await updatePresaleEnabled(row.id, target)
    ElMessage.success(`售前已${actionText}`)
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

<style scoped>
.platform-config-header {
  align-items: center;
}

.platform-toolbar-card :deep(.el-card__body) {
  padding: 12px;
}

.platform-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.filter-keyword {
  width: 260px;
}

.filter-level,
.filter-status {
  width: 130px;
}

.platform-metric-grid {
  margin-bottom: 0;
}

.platform-table-card :deep(.el-card__body) {
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

.platform-avatar.is-success {
  background: linear-gradient(135deg, #059669, #14b8a6);
}

.platform-avatar.is-muted {
  background: linear-gradient(135deg, #64748b, #94a3b8);
}

.priority-pill {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 9px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
}

.priority-pill.is-critical {
  background: #fef2f2;
  color: #b91c1c;
}

.priority-pill.is-high {
  background: #fffbeb;
  color: #b45309;
}

.priority-pill.is-normal {
  background: #eff6ff;
  color: #1d4ed8;
}

.capability-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.capability-tag {
  display: inline-flex;
  align-items: center;
  height: 22px;
  border-radius: 6px;
  padding: 0 7px;
  font-size: 12px;
  font-weight: 800;
}

.capability-tag.is-success {
  background: #ecfdf5;
  color: #047857;
}

.capability-tag.is-muted {
  background: #f1f5f9;
  color: #64748b;
}

.platform-editor-dialog :deep(.el-dialog__body) {
  background: #f8fafc;
}

.platform-config-form {
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
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
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

.form-section-head em {
  color: #64748b;
  font-size: 12px;
  font-style: normal;
  font-weight: 700;
  text-align: right;
}

.form-grid {
  display: grid;
  gap: 13px 14px;
  padding: 16px;
}

.form-grid.is-two {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.form-grid.is-three {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.compact-grid {
  padding-top: 0;
}

.switch-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
  padding: 16px 16px 2px;
}

.platform-config-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.platform-config-form :deep(.el-form-item__label) {
  padding-bottom: 7px;
  color: #475569;
  font-size: 12px;
  font-weight: 800;
  line-height: 1.2;
}

.platform-config-form :deep(.el-select),
.platform-config-form :deep(.el-input-number) {
  width: 100%;
}

@media (max-width: 768px) {
  .platform-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .filter-keyword,
  .filter-level,
  .filter-status,
  .platform-toolbar .el-button {
    width: 100%;
  }

  .form-grid.is-two,
  .form-grid.is-three,
  .switch-grid {
    grid-template-columns: 1fr;
  }

  .form-section-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .form-section-head em {
    text-align: left;
  }
}
</style>
