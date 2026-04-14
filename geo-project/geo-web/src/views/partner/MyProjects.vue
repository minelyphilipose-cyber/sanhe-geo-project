<template>
  <div class="space-y-4">
    <el-card>
      <template #header>
        <div class="flex items-center justify-between">
          <span>我的项目</span>
          <div class="flex items-center gap-2">
            <el-input v-model="keyword" clearable placeholder="搜索项目名称" style="width: 240px" @keyup.enter="load" />
            <el-button @click="load">查询</el-button>
            <el-button v-if="canWriteProject" type="primary" @click="openCreate">新增项目</el-button>
          </div>
        </div>
      </template>

      <DataState :loading="loading" :empty="!loading && rows.length === 0" empty-text="暂无项目">
        <el-table :data="rows" border>
          <el-table-column prop="projectName" label="项目名称" min-width="220" />
          <el-table-column prop="companyName" label="客户" min-width="180" />
          <el-table-column prop="brandName" label="品牌" min-width="160" />
          <el-table-column label="套餐" width="180">
            <template #default="scope">{{ dictStore.label('package_type', scope.row.packageType) || scope.row.packageType }}</template>
          </el-table-column>
          <el-table-column label="签约价(元)" width="130">
            <template #default="scope">{{ money(scope.row.packagePrice) }}</template>
          </el-table-column>
          <el-table-column label="项目状态" width="120">
            <template #default="scope">{{ dictStore.label('project_status', scope.row.status) || scope.row.status }}</template>
          </el-table-column>
          <el-table-column label="阶段" width="180">
            <template #default="scope">{{ dictStore.label('project_stage', scope.row.stage) || scope.row.stage }}</template>
          </el-table-column>
          <el-table-column v-if="canWriteProject" label="操作" width="100" fixed="right">
            <template #default="scope">
              <el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>
      </DataState>
    </el-card>

    <el-dialog v-model="formVisible" :title="formMode === 'create' ? '新增项目' : '编辑项目'" width="760px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="项目名称" prop="projectName" required><el-input v-model="form.projectName" /></el-form-item>
        <el-form-item label="项目别名">
          <el-input v-model="form.projectAliases" placeholder="多个别名用英文逗号分隔" />
        </el-form-item>
        <el-form-item label="客户" prop="companyId" required>
          <el-select
            v-model="form.companyId"
            filterable
            style="width: 100%"
            placeholder="先选择客户"
            :disabled="formMode === 'edit'"
            @change="onCompanyChange"
          >
            <el-option v-for="c in companyOptions" :key="c.id" :label="c.companyName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="品牌">
          <el-select
            v-model="form.brandId"
            filterable
            clearable
            style="width: 100%"
            placeholder="可不选；选品牌前需先选客户"
            :disabled="!form.companyId || formMode === 'edit'"
          >
            <el-option v-for="b in brandOptions" :key="b.id" :label="b.brandName" :value="b.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="套餐" prop="packageType" required>
          <el-select v-model="form.packageType" style="width: 100%" @change="onPackageChange">
            <el-option
              v-for="pkg in packagePlanOptions"
              :key="pkg.packageType"
              :label="dictStore.label('package_type', pkg.packageType)"
              :value="pkg.packageType"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="签约价(元)">
          <el-input-number v-model="form.packagePriceYuan" :disabled="true" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="服务月数">
          <el-input-number v-model="form.serviceMonths" :disabled="true" style="width: 100%" />
        </el-form-item>

        <el-divider content-position="left">平台选择（按套餐要求）</el-divider>
        <el-form-item :label="`P0平台（需${form.requiredPlatformP0Count}个）`" required>
          <el-select
            v-model="form.selectedPlatformCodesP0"
            multiple
            collapse-tags
            collapse-tags-tooltip
            :multiple-limit="form.requiredPlatformP0Count"
            :disabled="form.requiredPlatformP0Count === 0"
            placeholder="选择 P0 平台"
            style="width: 100%"
          >
            <el-option
              v-for="item in platformOptions.P0"
              :key="item.platformCode"
              :label="`${item.platformName} (${item.platformCode})`"
              :value="item.platformCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="`P1平台（需${form.requiredPlatformP1Count}个）`" required>
          <el-select
            v-model="form.selectedPlatformCodesP1"
            multiple
            collapse-tags
            collapse-tags-tooltip
            :multiple-limit="form.requiredPlatformP1Count"
            :disabled="form.requiredPlatformP1Count === 0"
            placeholder="选择 P1 平台"
            style="width: 100%"
          >
            <el-option
              v-for="item in platformOptions.P1"
              :key="item.platformCode"
              :label="`${item.platformName} (${item.platformCode})`"
              :value="item.platformCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="`P2平台（需${form.requiredPlatformP2Count}个）`" required>
          <el-select
            v-model="form.selectedPlatformCodesP2"
            multiple
            collapse-tags
            collapse-tags-tooltip
            :multiple-limit="form.requiredPlatformP2Count"
            :disabled="form.requiredPlatformP2Count === 0"
            placeholder="选择 P2 平台"
            style="width: 100%"
          >
            <el-option
              v-for="item in platformOptions.P2"
              :key="item.platformCode"
              :label="`${item.platformName} (${item.platformCode})`"
              :value="item.platformCode"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="地区"><RegionCascader v-model="form.regionCodes" /></el-form-item>
        <el-form-item v-if="formMode === 'edit'" label="激活状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option :label="dictStore.label('project_status', 'active')" value="active" />
            <el-option :label="dictStore.label('project_status', 'paused')" value="paused" />
          </el-select>
        </el-form-item>
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
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { getBrandList, getCompanyList } from '@/api/customer'
import {
  createProject,
  getProjectList,
  getProjectPlatformOptions,
  updateProject,
  updateProjectStatus,
} from '@/api/project'
import { getEnabledPackagePlans } from '@/api/packagePlan'
import type { Brand, Company, PackagePlan, Project, ProjectPlatformOption } from '@/types'
import DataState from '@/components/ui/DataState.vue'
import RegionCascader from '@/components/ui/RegionCascader.vue'
import { regionCodesFromPayload, regionPayloadFromCodes } from '@/constants/region'
import { useDictStore } from '@/stores/dict'
import { useUserStore } from '@/stores/user'

const dictStore = useDictStore()
const userStore = useUserStore()
const canWriteProject = computed(() => userStore.hasPermission('project.write'))

const loading = ref(false)
const saving = ref(false)
const keyword = ref('')
const rows = ref<Project[]>([])
const companyOptions = ref<Company[]>([])
const brandOptions = ref<Brand[]>([])
const packagePlans = ref<PackagePlan[]>([])
const platformOptions = ref<Record<'P0' | 'P1' | 'P2', ProjectPlatformOption[]>>({ P0: [], P1: [], P2: [] })

const formVisible = ref(false)
const formRef = ref<FormInstance>()
const formMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const originalStatus = ref<'active' | 'paused'>('paused')

const form = reactive({
  projectName: '',
  projectAliases: '',
  companyId: null as number | null,
  brandId: null as number | null,
  packageType: '',
  packagePriceYuan: 0,
  serviceMonths: 0,
  requiredPlatformP0Count: 0,
  requiredPlatformP1Count: 0,
  requiredPlatformP2Count: 0,
  selectedPlatformCodesP0: [] as string[],
  selectedPlatformCodesP1: [] as string[],
  selectedPlatformCodesP2: [] as string[],
  status: 'paused' as 'active' | 'paused',
  regionCodes: [] as string[],
  deliveryMode: 'managed',
  primaryGoal: '',
  remark: '',
})

const rules: FormRules = {
  projectName: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
  companyId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  packageType: [{ required: true, message: '请选择套餐', trigger: 'change' }],
}

const packagePlanOptions = computed(() => {
  if (!form.packageType) return packagePlans.value
  const exists = packagePlans.value.some((x) => x.packageType === form.packageType)
  if (exists) return packagePlans.value
  return [
    ...packagePlans.value,
    {
      id: -1,
      packageType: form.packageType,
      packageName: '已下架套餐',
      standardPrice: Number(form.packagePriceYuan.toFixed(2)),
      serviceMonths: form.serviceMonths,
      enabled: false,
      sortOrder: 9999,
    } as PackagePlan,
  ]
})

function money(v?: number | null) {
  if (v == null) return '-'
  return Number(v).toFixed(2)
}

function resetForm() {
  form.projectName = ''
  form.projectAliases = ''
  form.companyId = null
  form.brandId = null
  form.packageType = ''
  form.packagePriceYuan = 0
  form.serviceMonths = 0
  form.requiredPlatformP0Count = 0
  form.requiredPlatformP1Count = 0
  form.requiredPlatformP2Count = 0
  form.selectedPlatformCodesP0 = []
  form.selectedPlatformCodesP1 = []
  form.selectedPlatformCodesP2 = []
  form.status = 'paused'
  form.regionCodes = []
  form.deliveryMode = 'managed'
  form.primaryGoal = ''
  form.remark = ''
}

function onPackageChange(v: string) {
  const plan = packagePlans.value.find((x) => x.packageType === v)
  if (!plan) return
  form.packagePriceYuan = Number(plan.standardPrice)
  form.serviceMonths = plan.serviceMonths
  form.requiredPlatformP0Count = plan.platformP0Count || 0
  form.requiredPlatformP1Count = plan.platformP1Count || 0
  form.requiredPlatformP2Count = plan.platformP2Count || 0
  form.selectedPlatformCodesP0 = []
  form.selectedPlatformCodesP1 = []
  form.selectedPlatformCodesP2 = []
}

function onCompanyChange() {
  if (!form.companyId) {
    form.brandId = null
    brandOptions.value = []
    return
  }
  if (formMode.value === 'create') {
    form.brandId = null
  }
  loadBrands(form.companyId)
}

function applyDefaultPackage() {
  const firstPlan = packagePlans.value[0]
  if (!firstPlan) {
    form.packageType = ''
    form.packagePriceYuan = 0
    form.serviceMonths = 0
    form.requiredPlatformP0Count = 0
    form.requiredPlatformP1Count = 0
    form.requiredPlatformP2Count = 0
    return
  }
  form.packageType = firstPlan.packageType
  form.packagePriceYuan = Number(firstPlan.standardPrice)
  form.serviceMonths = firstPlan.serviceMonths
  form.requiredPlatformP0Count = firstPlan.platformP0Count || 0
  form.requiredPlatformP1Count = firstPlan.platformP1Count || 0
  form.requiredPlatformP2Count = firstPlan.platformP2Count || 0
}

function openCreate() {
  formMode.value = 'create'
  editingId.value = null
  resetForm()
  applyDefaultPackage()
  formVisible.value = true
}

async function openEdit(row: Project) {
  formMode.value = 'edit'
  editingId.value = row.id
  form.projectName = row.projectName
  form.projectAliases = row.projectAliases || ''
  form.companyId = row.companyId || null
  form.brandId = row.brandId
  form.packageType = row.packageType
  form.packagePriceYuan = Number(row.packagePrice || 0)
  form.serviceMonths = row.serviceMonths || 1
  form.requiredPlatformP0Count = row.planPlatformP0Count || 0
  form.requiredPlatformP1Count = row.planPlatformP1Count || 0
  form.requiredPlatformP2Count = row.planPlatformP2Count || 0
  form.selectedPlatformCodesP0 = [...(row.selectedPlatformCodesP0 || [])]
  form.selectedPlatformCodesP1 = [...(row.selectedPlatformCodesP1 || [])]
  form.selectedPlatformCodesP2 = [...(row.selectedPlatformCodesP2 || [])]
  form.status = row.status === 'active' ? 'active' : 'paused'
  originalStatus.value = form.status
  form.regionCodes = regionCodesFromPayload(row)
  form.deliveryMode = row.deliveryMode || 'managed'
  form.primaryGoal = row.primaryGoal || ''
  form.remark = row.remark || ''
  await loadBrands(form.companyId)
  formVisible.value = true
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  if (!form.companyId) {
    ElMessage.warning('请先选择客户')
    return
  }
  if (form.selectedPlatformCodesP0.length !== form.requiredPlatformP0Count) {
    ElMessage.warning(`P0 平台需选择 ${form.requiredPlatformP0Count} 个`)
    return
  }
  if (form.selectedPlatformCodesP1.length !== form.requiredPlatformP1Count) {
    ElMessage.warning(`P1 平台需选择 ${form.requiredPlatformP1Count} 个`)
    return
  }
  if (form.selectedPlatformCodesP2.length !== form.requiredPlatformP2Count) {
    ElMessage.warning(`P2 平台需选择 ${form.requiredPlatformP2Count} 个`)
    return
  }

  const uniquePlatforms = new Set([
    ...form.selectedPlatformCodesP0,
    ...form.selectedPlatformCodesP1,
    ...form.selectedPlatformCodesP2,
  ])
  const selectedTotal =
    form.selectedPlatformCodesP0.length + form.selectedPlatformCodesP1.length + form.selectedPlatformCodesP2.length
  if (uniquePlatforms.size !== selectedTotal) {
    ElMessage.warning('同一平台不能同时出现在 P0/P1/P2')
    return
  }

  saving.value = true
  try {
    const region = regionPayloadFromCodes(form.regionCodes)
    const payload = {
      projectName: form.projectName,
      projectAliases: form.projectAliases || undefined,
      companyId: form.companyId,
      brandId: form.brandId,
      packageType: form.packageType,
      packagePrice: Number(form.packagePriceYuan.toFixed(2)),
      serviceMonths: form.serviceMonths,
      selectedPlatformCodesP0: form.selectedPlatformCodesP0,
      selectedPlatformCodesP1: form.selectedPlatformCodesP1,
      selectedPlatformCodesP2: form.selectedPlatformCodesP2,
      deliveryMode: form.deliveryMode || 'managed',
      provinceCode: region.provinceCode,
      provinceName: region.provinceName,
      cityCode: region.cityCode,
      cityName: region.cityName,
      districtCode: region.districtCode,
      districtName: region.districtName,
      primaryGoal: form.primaryGoal || undefined,
      remark: form.remark || undefined,
    }

    if (formMode.value === 'create') {
      await createProject(payload)
    } else if (editingId.value) {
      await updateProject(editingId.value, payload)
      if (form.status !== originalStatus.value) {
        await updateProjectStatus(editingId.value, form.status)
      }
    }

    formVisible.value = false
    ElMessage.success('保存成功')
    await load()
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
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

async function loadBrands(companyId?: number | null) {
  try {
    const { data } = await getBrandList({ current: 1, size: 500, companyId: companyId || undefined })
    brandOptions.value = data.data.records || []
  } catch {
    brandOptions.value = []
  }
}

async function loadPackagePlans() {
  try {
    const { data } = await getEnabledPackagePlans()
    packagePlans.value = data.data || []
  } catch {
    packagePlans.value = []
  }
}

async function loadPlatformOptions() {
  try {
    const { data } = await getProjectPlatformOptions()
    platformOptions.value = {
      P0: data.data.P0 || [],
      P1: data.data.P1 || [],
      P2: data.data.P2 || [],
    }
  } catch {
    platformOptions.value = { P0: [], P1: [], P2: [] }
  }
}

async function load() {
  loading.value = true
  try {
    const { data } = await getProjectList({
      current: 1,
      size: 200,
      keyword: keyword.value || undefined,
    })
    rows.value = data.data.records || []
  } catch {
    rows.value = []
    ElMessage.error('加载项目失败')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await dictStore.ensureLoaded()
  await loadPackagePlans()
  await loadPlatformOptions()
  await loadCompanies()
  applyDefaultPackage()
  await load()
})
</script>
