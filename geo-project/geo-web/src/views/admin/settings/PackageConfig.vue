<template>
  <div class="package-config-page admin-page">
    <div class="admin-page-header package-header">
      <div>
        <div class="admin-page-kicker">系统配置</div>
        <h1 class="admin-page-title">套餐配置</h1>
        <div class="admin-page-subtitle">维护套餐价格、服务周期、拓词额度和渠道分发额度。</div>
      </div>
      <div class="admin-page-actions">
        <el-button type="primary" @click="openCreate">新增套餐</el-button>
      </div>
    </div>

    <el-card shadow="never" class="admin-surface package-toolbar-card">
      <div class="package-toolbar">
        <el-input v-model="query.keyword" class="filter-keyword" placeholder="搜索套餐类型/名称" clearable @keyup.enter="load" />
        <el-select v-model="query.enabled" class="filter-status" placeholder="状态" clearable @change="load">
          <el-option label="启用" :value="true" />
          <el-option label="停用" :value="false" />
        </el-select>
        <el-button type="primary" plain @click="load">查询</el-button>
      </div>
    </el-card>

    <div class="admin-metric-grid package-metric-grid">
      <div class="admin-metric-card" style="--metric-accent: #2563eb; --metric-tone: #eff6ff">
        <span class="admin-metric-label">套餐总数</span>
        <strong class="admin-metric-value">{{ page.total }}</strong>
        <span class="admin-metric-hint">当前筛选结果</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #059669; --metric-tone: #ecfdf5">
        <span class="admin-metric-label">启用套餐</span>
        <strong class="admin-metric-value">{{ enabledCount }}</strong>
        <span class="admin-metric-hint">当前页启用状态</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #7c3aed; --metric-tone: #f5f3ff">
        <span class="admin-metric-label">平均价格</span>
        <strong class="admin-metric-value">{{ avgPriceText }}</strong>
        <span class="admin-metric-hint">按当前页标准价计算</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #f59e0b; --metric-tone: #fffbeb">
        <span class="admin-metric-label">平均周期</span>
        <strong class="admin-metric-value">{{ avgServiceMonths }}</strong>
        <span class="admin-metric-hint">服务月数均值</span>
      </div>
    </div>

    <el-card shadow="never" class="admin-table-card package-table-card">
      <div class="table-header">
        <div>
          <div class="table-title">套餐列表</div>
          <div class="table-subtitle">按价格、服务周期、拓词额度和渠道额度核对套餐能力。</div>
        </div>
        <div class="chips">
          <span class="chip chip-muted">当前页 {{ rows.length }}</span>
          <span class="chip chip-success">启用 {{ enabledCount }}</span>
        </div>
      </div>

      <DataState :loading="loading" :empty="!loading && rows.length === 0" empty-text="暂无套餐配置">
        <el-table :data="rows" border table-layout="fixed">
          <el-table-column label="套餐" min-width="240" show-overflow-tooltip>
            <template #default="scope">
              <div class="admin-entity-cell">
                <div class="admin-entity-avatar package-avatar" :class="scope.row.enabled ? 'is-success' : 'is-muted'">
                  {{ packageInitial(scope.row.packageName) }}
                </div>
                <div class="min-w-0">
                  <div class="admin-entity-main">{{ scope.row.packageName }}</div>
                  <div class="admin-entity-sub">{{ scope.row.packageType }}</div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="标准价(元)" width="120">
            <template #default="scope">{{ Number(scope.row.standardPrice || 0).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column prop="serviceMonths" label="服务月数" width="100" />
          <el-table-column label="拓词问题额度" min-width="190">
            <template #default="scope">
              {{ scope.row.keywordGroupLimit }}（A {{ scope.row.keywordGroupLimitA ?? scope.row.keywordGroupLimit }} / B {{ scope.row.keywordGroupLimitB ?? 0 }} / C {{ scope.row.keywordGroupLimitC ?? 0 }}）
            </template>
          </el-table-column>
          <el-table-column label="渠道额度" min-width="260">
            <template #default="scope">{{ quotaSummary(scope.row.channelQuotaConfigs) }}</template>
          </el-table-column>
          <el-table-column prop="sortOrder" label="排序" width="80" />
          <el-table-column label="状态" width="90">
            <template #default="scope">
              <span class="admin-status-tag" :class="scope.row.enabled ? 'is-success' : 'is-muted'">
                {{ scope.row.enabled ? '启用' : '停用' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="scope">
              <el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button>
              <el-button link :type="scope.row.enabled ? 'warning' : 'success'" @click="toggleStatus(scope.row)">
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

    <el-dialog v-model="formVisible" :title="formMode === 'create' ? '新增套餐' : '编辑套餐'" width="900px" class="admin-editor-dialog">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-row :gutter="12">
          <el-col :xs="24" :md="12">
            <el-form-item label="套餐类型" prop="packageType" required>
              <el-input v-model="form.packageType" :disabled="formMode === 'edit'" placeholder="如: trial_basic" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="套餐名称" prop="packageName" required>
              <el-input v-model="form.packageName" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="12">
          <el-col :xs="24" :sm="12" :lg="6">
            <el-form-item label="标准价(元)" prop="standardPrice" required>
              <el-input-number v-model="form.standardPrice" :min="0.01" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :lg="6">
            <el-form-item label="服务月数" prop="serviceMonths" required>
              <el-input-number v-model="form.serviceMonths" :min="1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :lg="6">
            <el-form-item label="拓词问题总数" prop="keywordGroupLimit" required>
              <el-input-number v-model="form.keywordGroupLimit" :min="1" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="12">
          <el-col :xs="24" :sm="8">
            <el-form-item label="A档问题数" prop="keywordGroupLimitA" required>
              <el-input-number v-model="form.keywordGroupLimitA" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="8">
            <el-form-item label="B档问题数" prop="keywordGroupLimitB" required>
              <el-input-number v-model="form.keywordGroupLimitB" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="8">
            <el-form-item label="C档问题数" prop="keywordGroupLimitC" required>
              <el-input-number v-model="form.keywordGroupLimitC" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <div class="tier-total-tip" :class="{ invalid: tierTotal !== form.keywordGroupLimit }">
          A/B/C 合计 {{ tierTotal }}，需等于拓词问题总数 {{ form.keywordGroupLimit }}
        </div>

        <el-divider content-position="left">分发渠道额度</el-divider>
        <el-table :data="form.channelQuotaConfigs" border>
          <el-table-column label="渠道" min-width="180">
            <template #default="scope">{{ channelLabel(scope.row.channelCode) }}</template>
          </el-table-column>
          <el-table-column label="周期" min-width="160">
            <template #default="scope">
              <el-select v-model="scope.row.periodType" :disabled="scope.row.channelCode === 'authority_media'" style="width: 100%">
                <el-option v-for="item in periodOptions(scope.row.channelCode)" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="额度" min-width="160">
            <template #default="scope">
              <el-input-number v-model="scope.row.quotaLimit" :min="0" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="启用" width="100">
            <template #default="scope"><el-switch v-model="scope.row.enabled" /></template>
          </el-table-column>
        </el-table>

        <el-row :gutter="12" class="mt-4">
          <el-col :xs="24" :sm="12">
            <el-form-item label="排序" prop="sortOrder" required>
              <el-input-number v-model="form.sortOrder" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="状态" prop="enabled" required>
              <el-switch v-model="form.enabled" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="3" /></el-form-item>
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
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createPackagePlan,
  getAdminPackagePlans,
  updatePackagePlan,
  updatePackagePlanStatus,
} from '@/api/packagePlan'
import type { PackageChannelQuotaConfig, PackagePlan } from '@/types'
import DataState from '@/components/ui/DataState.vue'

const loading = ref(false)
const saving = ref(false)
const rows = ref<PackagePlan[]>([])
const formVisible = ref(false)
const formRef = ref<FormInstance>()
const formMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const page = reactive({ current: 1, size: 20, total: 0 })
const query = reactive({ keyword: '', enabled: undefined as boolean | undefined })

const form = reactive({
  packageType: '',
  packageName: '',
  standardPrice: 0,
  serviceMonths: 12,
  keywordGroupLimit: 100,
  keywordGroupLimitA: 100,
  keywordGroupLimitB: 0,
  keywordGroupLimitC: 0,
  monthlyReportDepth: 'L2',
  quarterlyReportDepth: 'L2',
  consultantIntensity: 'L2',
  competitorInsightDepth: 'L2',
  mediaDistributionIntensity: 'L2',
  commitmentTargetIntensity: 'L2',
  targetMetricType: 'visibility_rate',
  targetMetricValue: 0.05,
  targetWindowDays: 90,
  enabled: true,
  sortOrder: 10,
  remark: '',
  channelQuotaConfigs: defaultChannelQuotas() as PackageChannelQuotaConfig[],
})

const rules: FormRules = {
  packageType: [{ required: true, message: '请输入套餐类型', trigger: 'blur' }],
  packageName: [{ required: true, message: '请输入套餐名称', trigger: 'blur' }],
  standardPrice: [{ required: true, message: '请输入标准价', trigger: 'change' }],
  serviceMonths: [{ required: true, message: '请输入服务月数', trigger: 'change' }],
  keywordGroupLimit: [{ required: true, message: '请输入拓词问题总数', trigger: 'change' }],
  keywordGroupLimitA: [{ required: true, message: '请输入 A 档问题数', trigger: 'change' }],
  keywordGroupLimitB: [{ required: true, message: '请输入 B 档问题数', trigger: 'change' }],
  keywordGroupLimitC: [{ required: true, message: '请输入 C 档问题数', trigger: 'change' }],
}

const tierTotal = computed(() => Number(form.keywordGroupLimitA || 0) + Number(form.keywordGroupLimitB || 0) + Number(form.keywordGroupLimitC || 0))
const enabledCount = computed(() => rows.value.filter((item) => item.enabled).length)
const avgPriceText = computed(() => {
  if (!rows.value.length) return '-'
  const avg = rows.value.reduce((sum, item) => sum + Number(item.standardPrice || 0), 0) / rows.value.length
  return avg.toFixed(0)
})
const avgServiceMonths = computed(() => {
  if (!rows.value.length) return '-'
  const avg = rows.value.reduce((sum, item) => sum + Number(item.serviceMonths || 0), 0) / rows.value.length
  return `${Math.round(avg)} 月`
})

function defaultChannelQuotas(): PackageChannelQuotaConfig[] {
  return [
    { channelCode: 'official_site', periodType: 'week', quotaLimit: 1, enabled: true },
    { channelCode: 'industry_site', periodType: 'week', quotaLimit: 1, enabled: true },
    { channelCode: 'self_media', periodType: 'week', quotaLimit: 1, enabled: true },
    { channelCode: 'authority_media', periodType: 'total', quotaLimit: 0, enabled: true },
  ]
}

function channelLabel(code: string) {
  const map: Record<string, string> = {
    official_site: '官网',
    industry_site: '行业资讯站',
    self_media: '自媒体平台',
    authority_media: '权重媒体平台',
  }
  return map[code] || code
}

function periodOptions(channelCode: string) {
  if (channelCode === 'authority_media') {
    return [{ label: '总额度', value: 'total' }]
  }
  return [
    { label: '日', value: 'day' },
    { label: '周', value: 'week' },
    { label: '月', value: 'month' },
  ]
}

function periodLabel(value: string) {
  return periodOptions(value === 'total' ? 'authority_media' : '').find((item) => item.value === value)?.label || value
}

function quotaSummary(configs?: PackageChannelQuotaConfig[]) {
  const list = configs?.length ? configs : []
  if (!list.length) return '-'
  return list.map((item) => `${channelLabel(item.channelCode)} ${item.quotaLimit}/${periodLabel(item.periodType)}`).join('；')
}

function packageInitial(value?: string | null) {
  const text = String(value || '').trim()
  return text ? Array.from(text)[0] : '套'
}

function normalizeChannelQuotas(configs?: PackageChannelQuotaConfig[]) {
  const existing = new Map((configs || []).map((item) => [item.channelCode, item]))
  return defaultChannelQuotas().map((item) => ({
    ...item,
    ...(existing.get(item.channelCode) || {}),
    periodType: item.channelCode === 'authority_media' ? 'total' : (existing.get(item.channelCode)?.periodType || item.periodType),
  }))
}

function resetForm() {
  form.packageType = ''
  form.packageName = ''
  form.standardPrice = 0
  form.serviceMonths = 12
  form.keywordGroupLimit = 100
  form.keywordGroupLimitA = 100
  form.keywordGroupLimitB = 0
  form.keywordGroupLimitC = 0
  form.enabled = true
  form.sortOrder = 10
  form.remark = ''
  form.channelQuotaConfigs = defaultChannelQuotas()
}

async function load() {
  loading.value = true
  try {
    const { data } = await getAdminPackagePlans({
      current: page.current,
      size: page.size,
      keyword: query.keyword || undefined,
      enabled: query.enabled,
    })
    rows.value = data.data.records || []
    page.total = data.data.total || 0
  } finally {
    loading.value = false
  }
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

function openEdit(row: PackagePlan) {
  formMode.value = 'edit'
  editingId.value = row.id
  form.packageType = row.packageType
  form.packageName = row.packageName
  form.standardPrice = Number(row.standardPrice || 0)
  form.serviceMonths = row.serviceMonths
  form.keywordGroupLimit = row.keywordGroupLimit
  form.keywordGroupLimitA = row.keywordGroupLimitA ?? row.keywordGroupLimit
  form.keywordGroupLimitB = row.keywordGroupLimitB ?? 0
  form.keywordGroupLimitC = row.keywordGroupLimitC ?? 0
  form.monthlyReportDepth = row.monthlyReportDepth
  form.quarterlyReportDepth = row.quarterlyReportDepth
  form.consultantIntensity = row.consultantIntensity
  form.competitorInsightDepth = row.competitorInsightDepth
  form.mediaDistributionIntensity = row.mediaDistributionIntensity
  form.commitmentTargetIntensity = row.commitmentTargetIntensity
  form.targetMetricType = row.targetMetricType
  form.targetMetricValue = row.targetMetricValue
  form.targetWindowDays = row.targetWindowDays
  form.enabled = row.enabled
  form.sortOrder = row.sortOrder
  form.remark = row.remark || ''
  form.channelQuotaConfigs = normalizeChannelQuotas(row.channelQuotaConfigs)
  formVisible.value = true
}

function buildPayload() {
  return {
    packageType: form.packageType,
    packageName: form.packageName,
    standardPrice: form.standardPrice,
    serviceMonths: form.serviceMonths,
    keywordGroupLimit: form.keywordGroupLimit,
    keywordGroupLimitA: form.keywordGroupLimitA,
    keywordGroupLimitB: form.keywordGroupLimitB,
    keywordGroupLimitC: form.keywordGroupLimitC,
    monthlyReportDepth: form.monthlyReportDepth,
    quarterlyReportDepth: form.quarterlyReportDepth,
    consultantIntensity: form.consultantIntensity,
    competitorInsightDepth: form.competitorInsightDepth,
    mediaDistributionIntensity: form.mediaDistributionIntensity,
    commitmentTargetIntensity: form.commitmentTargetIntensity,
    targetMetricType: form.targetMetricType,
    targetMetricValue: form.targetMetricValue,
    targetWindowDays: form.targetWindowDays,
    enabled: form.enabled,
    sortOrder: form.sortOrder,
    remark: form.remark || undefined,
    channelQuotaConfigs: form.channelQuotaConfigs.map((item) => ({
      channelCode: item.channelCode,
      periodType: item.channelCode === 'authority_media' ? 'total' : item.periodType,
      quotaLimit: item.quotaLimit,
      enabled: item.enabled,
    })),
  }
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (tierTotal.value !== Number(form.keywordGroupLimit || 0)) {
    ElMessage.warning('A/B/C 问题数合计必须等于拓词问题总数')
    return
  }
  saving.value = true
  try {
    const payload = buildPayload()
    if (formMode.value === 'create') {
      await createPackagePlan(payload)
    } else if (editingId.value) {
      await updatePackagePlan(editingId.value, payload)
    }
    ElMessage.success('保存成功')
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row: PackagePlan) {
  const target = !row.enabled
  await ElMessageBox.confirm(`确认${target ? '启用' : '停用'}套餐「${row.packageName}」？`, '状态变更确认', {
    type: 'warning',
    confirmButtonText: '确认',
    cancelButtonText: '取消',
  })
  await updatePackagePlanStatus(row.id, target)
  ElMessage.success('状态已更新')
  await load()
}

onMounted(load)
</script>

<style scoped>
.package-header {
  align-items: center;
}

.package-toolbar-card :deep(.el-card__body) {
  padding: 12px;
}

.package-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.filter-keyword {
  width: 240px;
}

.filter-status {
  width: 130px;
}

.package-metric-grid {
  margin-bottom: 0;
}

.package-table-card :deep(.el-card__body) {
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

.package-avatar.is-success {
  background: linear-gradient(135deg, #059669, #14b8a6);
}

.package-avatar.is-muted {
  background: linear-gradient(135deg, #64748b, #94a3b8);
}

.tier-total-tip {
  margin: -4px 0 14px;
  font-size: 12px;
  color: #606266;
}

.tier-total-tip.invalid {
  color: #e6a23c;
}

@media (max-width: 768px) {
  .package-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .filter-keyword,
  .filter-status,
  .package-toolbar .el-button {
    width: 100%;
  }
}
</style>
