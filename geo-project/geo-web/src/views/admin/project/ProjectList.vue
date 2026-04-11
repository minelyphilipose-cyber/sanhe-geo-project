<template>
  <div>
    <div class="mb-4 flex items-center justify-between gap-3">
      <div class="flex items-center gap-2">
        <el-input v-model="query.keyword" placeholder="搜索项目名称" clearable style="width: 240px" @keyup.enter="load" />
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px" @change="load">
          <el-option v-for="v in statusOptions" :key="v" :label="v" :value="v" />
        </el-select>
        <el-select v-model="query.stage" placeholder="阶段" clearable style="width: 180px" @change="load">
          <el-option v-for="v in stageOptions" :key="v" :label="v" :value="v" />
        </el-select>
        <el-button @click="load">查询</el-button>
      </div>
      <el-button type="primary" @click="openCreate">新建项目</el-button>
    </div>

    <el-card>
      <DataState :loading="loading" :empty="!loading && rows.length === 0" empty-text="暂无项目数据">
        <el-table :data="rows" border>
        <el-table-column prop="projectCode" label="编码" min-width="170" />
        <el-table-column prop="projectName" label="项目名称" min-width="180" />
        <el-table-column prop="brandId" label="品牌ID" width="100" />
        <el-table-column prop="packageType" label="套餐" width="150" />
        <el-table-column label="签约价(元)" width="120">
          <template #default="scope">{{ centsToYuan(scope.row.packagePrice) }}</template>
        </el-table-column>
        <el-table-column prop="ownerType" label="归属" width="100" />
        <el-table-column prop="partnerId" label="合伙人ID" width="110" />
        <el-table-column prop="status" label="状态" width="120" />
        <el-table-column prop="stage" label="阶段" width="170" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="goDetail(scope.row.id)">详情</el-button>
            <el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button>
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

    <el-dialog v-model="formVisible" :title="formMode === 'create' ? '新建项目' : '编辑项目'" width="760px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="项目名称" required><el-input v-model="form.projectName" /></el-form-item>
        <el-form-item label="品牌" required>
          <el-select v-model="form.brandId" filterable style="width: 100%" placeholder="选择品牌">
            <el-option v-for="b in brandOptions" :key="b.id" :label="`${b.brandName} (#${b.id})`" :value="b.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="套餐" required>
          <el-select v-model="form.packageType" style="width: 100%" @change="onPackageChange">
            <el-option label="trial_6980" value="trial_6980" />
            <el-option label="standard_12800" value="standard_12800" />
            <el-option label="growth_26800" value="growth_26800" />
          </el-select>
        </el-form-item>
        <el-form-item label="签约价(元)" required>
          <el-input-number v-model="form.packagePriceYuan" :min="1" :precision="2" :step="100" style="width: 100%" />
        </el-form-item>
        <el-form-item label="服务月数" required>
          <el-input-number v-model="form.serviceMonths" :min="1" :max="24" style="width: 100%" />
        </el-form-item>
        <el-form-item label="归属类型" required>
          <el-select v-model="form.ownerType" style="width: 100%">
            <el-option label="direct" value="direct" />
            <el-option label="partner" value="partner" />
            <el-option label="joint" value="joint" />
          </el-select>
        </el-form-item>
        <el-form-item label="合伙人ID"><el-input-number v-model="form.partnerId" :min="1" style="width: 100%" /></el-form-item>
        <el-form-item label="交付模式"><el-input v-model="form.deliveryMode" /></el-form-item>
        <el-form-item label="主目标"><el-input v-model="form.primaryGoal" type="textarea" :rows="3" /></el-form-item>
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
import { reactive, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { getProjectList, createProject, updateProject } from '@/api/project'
import { getBrandList } from '@/api/customer'
import type { Brand, Project } from '@/types'
import DataState from '@/components/ui/DataState.vue'

const route = useRoute()
const router = useRouter()

const PACKAGE_PRESET: Record<string, { price: number; months: number }> = {
  trial_6980: { price: 6980, months: 3 },
  standard_12800: { price: 12800, months: 12 },
  growth_26800: { price: 26800, months: 12 },
}

const statusOptions = ['draft', 'active', 'paused', 'dispute', 'completed', 'archived']
const stageOptions = [
  'pending_start',
  'collecting_materials',
  'baseline_diagnosis',
  'building_questions',
  'executing',
  'biweekly_feedback',
  'monthly_report',
  'quarterly_report',
  'needs_renewal',
  'high_risk',
  'dispute_handling',
  'completed',
]

const loading = ref(false)
const saving = ref(false)
const rows = ref<Project[]>([])
const brandOptions = ref<Brand[]>([])
const page = reactive({ current: 1, size: 10, total: 0 })
const query = reactive({ keyword: '', status: '', stage: '' })

const formVisible = ref(false)
const formRef = ref<FormInstance>()
const formMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)

const form = reactive({
  projectName: '',
  brandId: null as number | null,
  packageType: 'trial_6980',
  packagePriceYuan: 6980,
  serviceMonths: 3,
  ownerType: 'direct',
  partnerId: null as number | null,
  deliveryMode: 'managed',
  primaryGoal: '',
  remark: '',
})
const rules: FormRules = {
  projectName: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
  brandId: [{ required: true, message: '请选择品牌', trigger: 'change' }],
  packageType: [{ required: true, message: '请选择套餐', trigger: 'change' }],
  packagePriceYuan: [{ required: true, message: '请输入签约价', trigger: 'change' }],
  serviceMonths: [{ required: true, message: '请输入服务月数', trigger: 'change' }],
  ownerType: [{ required: true, message: '请选择归属类型', trigger: 'change' }],
}

function centsToYuan(v?: number | null) {
  if (v == null) return '-'
  return (v / 100).toFixed(2)
}

function yuanToCents(v: number) {
  return Math.round(v * 100)
}

function onPackageChange(v: string) {
  const preset = PACKAGE_PRESET[v]
  if (!preset) return
  form.packagePriceYuan = preset.price
  form.serviceMonths = preset.months
}

function resetForm() {
  form.projectName = ''
  form.brandId = route.query.brandId ? Number(route.query.brandId) : null
  form.packageType = 'trial_6980'
  form.packagePriceYuan = 6980
  form.serviceMonths = 3
  form.ownerType = 'direct'
  form.partnerId = null
  form.deliveryMode = 'managed'
  form.primaryGoal = ''
  form.remark = ''
}

async function loadBrands() {
  try {
    const { data } = await getBrandList({ current: 1, size: 500 })
    brandOptions.value = data.data.records || []
  } catch {
    brandOptions.value = []
  }
}

async function load() {
  loading.value = true
  try {
    const { data } = await getProjectList({
      current: page.current,
      size: page.size,
      keyword: query.keyword || undefined,
      status: query.status || undefined,
      stage: query.stage || undefined,
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
  formMode.value = 'create'
  editingId.value = null
  resetForm()
  formVisible.value = true
}

function openEdit(row: Project) {
  formMode.value = 'edit'
  editingId.value = row.id
  form.projectName = row.projectName
  form.brandId = row.brandId
  form.packageType = row.packageType
  form.packagePriceYuan = (row.packagePrice || 0) / 100
  form.serviceMonths = row.serviceMonths || 1
  form.ownerType = row.ownerType
  form.partnerId = row.partnerId
  form.deliveryMode = row.deliveryMode || 'managed'
  form.primaryGoal = row.primaryGoal || ''
  form.remark = (row as any).remark || ''
  formVisible.value = true
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  if ((form.ownerType === 'partner' || form.ownerType === 'joint') && !form.partnerId) {
    ElMessage.warning('partner/joint 项目需填写合伙人ID')
    return
  }
  saving.value = true
  try {
    const payload = {
      projectName: form.projectName,
      brandId: form.brandId,
      packageType: form.packageType,
      packagePrice: yuanToCents(form.packagePriceYuan),
      serviceMonths: form.serviceMonths,
      ownerType: form.ownerType,
      partnerId: form.partnerId || undefined,
      deliveryMode: form.deliveryMode || 'managed',
      primaryGoal: form.primaryGoal || undefined,
      remark: form.remark || undefined,
    }

    if (formMode.value === 'create') {
      const { data } = await createProject(payload)
      ElMessage.success('保存成功')
      formVisible.value = false
      await load()
      goDetail(data.data.id)
    } else if (editingId.value) {
      await updateProject(editingId.value, payload)
      ElMessage.success('保存成功')
      formVisible.value = false
      load()
    }
  } finally {
    saving.value = false
  }
}

function goDetail(id: number) {
  router.push(`/admin/projects/${id}`)
}

onMounted(async () => {
  await loadBrands()
  await load()
})
</script>

