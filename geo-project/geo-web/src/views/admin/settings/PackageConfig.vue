<template>
  <div>
    <div class="mb-4 flex items-center justify-between gap-3">
      <div class="flex items-center gap-2">
        <el-input v-model="query.keyword" placeholder="搜索套餐类型/名称" clearable style="width: 260px" @keyup.enter="load" />
        <el-select v-model="query.enabled" placeholder="状态" clearable style="width: 140px" @change="load">
          <el-option label="启用" :value="true" />
          <el-option label="停用" :value="false" />
        </el-select>
        <el-button @click="load">查询</el-button>
      </div>
      <el-button type="primary" @click="openCreate">新增套餐</el-button>
    </div>

    <el-card>
      <DataState :loading="loading" :empty="!loading && rows.length === 0" empty-text="暂无套餐配置">
        <el-table :data="rows" border>
          <el-table-column prop="packageType" label="套餐类型" min-width="150" />
          <el-table-column prop="packageName" label="套餐名称" min-width="180" />
          <el-table-column label="标准价(元)" width="120">
            <template #default="scope">{{ Number(scope.row.standardPrice || 0).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column prop="serviceMonths" label="服务月数" width="100" />
          <el-table-column label="问题池" width="130">
            <template #default="scope">{{ scope.row.questionPoolSize }}/{{ scope.row.coreQuestionCount }}</template>
          </el-table-column>
          <el-table-column label="渠道额度" min-width="260">
            <template #default="scope">{{ quotaSummary(scope.row.channelQuotaConfigs) }}</template>
          </el-table-column>
          <el-table-column prop="sortOrder" label="排序" width="80" />
          <el-table-column label="状态" width="90">
            <template #default="scope">
              <el-tag :type="scope.row.enabled ? 'success' : 'info'">{{ scope.row.enabled ? '启用' : '停用' }}</el-tag>
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

    <el-dialog v-model="formVisible" :title="formMode === 'create' ? '新增套餐' : '编辑套餐'" width="900px">
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
            <el-form-item label="问题池总数" prop="questionPoolSize" required>
              <el-input-number v-model="form.questionPoolSize" :min="1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :lg="6">
            <el-form-item label="核心问题数" prop="coreQuestionCount" required>
              <el-input-number v-model="form.coreQuestionCount" :min="1" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

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
  questionPoolSize: 100,
  coreQuestionCount: 20,
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
  questionPoolSize: [{ required: true, message: '请输入问题池总数', trigger: 'change' }],
  coreQuestionCount: [{ required: true, message: '请输入核心问题数', trigger: 'change' }],
}

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
  form.questionPoolSize = 100
  form.coreQuestionCount = 20
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
  form.questionPoolSize = row.questionPoolSize
  form.coreQuestionCount = row.coreQuestionCount
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
    questionPoolSize: form.questionPoolSize,
    coreQuestionCount: form.coreQuestionCount,
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
  if (form.coreQuestionCount > form.questionPoolSize) {
    ElMessage.warning('核心问题数不能超过问题池总数')
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
