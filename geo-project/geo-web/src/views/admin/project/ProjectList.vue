<template>
  <div>
    <div class="mb-4 flex items-center justify-between gap-3">
      <div class="flex items-center gap-2">
        <el-input v-model="query.keyword" placeholder="搜索项目名称" clearable style="width: 240px" @keyup.enter="load" />
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px" @change="load">
          <el-option v-for="v in statusOptions" :key="v" :label="dictStore.label('project_status', v)" :value="v" />
        </el-select>
        <el-button @click="load">查询</el-button>
      </div>
      <el-button v-if="canWriteProject" type="primary" @click="openCreate">新建项目</el-button>
    </div>

    <el-card>
      <DataState :loading="loading" :empty="!loading && rows.length === 0" empty-text="暂无项目数据">
        <el-table :data="rows" border>
          <el-table-column prop="projectCode" label="编码" min-width="170" />
          <el-table-column prop="projectName" label="项目名称" min-width="180" />
          <el-table-column prop="companyName" label="客户名称" min-width="180" />
          <el-table-column prop="brandName" label="品牌名称" min-width="180">
            <template #default="scope">{{ scope.row.brandName || '-' }}</template>
          </el-table-column>
          <el-table-column label="套餐" width="150">
            <template #default="scope">{{ dictStore.label('package_type', scope.row.packageType) }}</template>
          </el-table-column>
          <el-table-column label="签约价(元)" width="120">
            <template #default="scope">{{ centsToYuan(scope.row.packagePrice) }}</template>
          </el-table-column>
          <el-table-column label="归属" width="100">
            <template #default="scope">{{ dictStore.label('owner_type', scope.row.ownerType) }}</template>
          </el-table-column>
          <el-table-column prop="cityName" label="地区" min-width="200">
            <template #default="scope">{{ regionDisplay(scope.row) || '-' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="scope">{{ dictStore.label('project_status', scope.row.status) }}</template>
          </el-table-column>
          <el-table-column label="阶段" width="170">
            <template #default="scope">{{ dictStore.label('project_stage', scope.row.stage) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="scope">
              <div class="op-actions">
                <el-button link type="primary" @click="goDetail(scope.row.id)">详情</el-button>
                <el-dropdown
                  trigger="click"
                  @command="(cmd: string | number | object) => onMoreCommand(scope.row, String(cmd))"
                >
                  <el-button link type="primary">更多</el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="questionPool">问题池</el-dropdown-item>
                      <el-dropdown-item v-if="scope.row.status === 'paused' && canActivateProject" command="activate">去激活</el-dropdown-item>
                      <el-dropdown-item v-if="canWriteProject" command="edit">编辑</el-dropdown-item>
                      <el-dropdown-item v-if="canWriteProject" command="delete">删除</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
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
        <el-form-item label="项目别名">
          <el-input v-model="form.projectAliases" placeholder="可选，多个别名用英文逗号分隔" />
        </el-form-item>
        <el-form-item label="客户" required>
          <el-select
            v-model="form.companyId"
            filterable
            style="width: 100%"
            placeholder="先选择客户"
            :disabled="formMode === 'edit' || lockCompanyBrandSelection"
            @change="onCompanyChange"
          >
            <el-option v-for="c in companyOptions" :key="c.id" :label="c.companyName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="品牌" required>
          <el-select
            v-model="form.brandId"
            filterable
            style="width: 100%"
            placeholder="请选择品牌；选品牌前需先选客户"
            :disabled="!form.companyId || formMode === 'edit' || lockCompanyBrandSelection"
          >
            <el-option v-for="b in brandOptions" :key="b.id" :label="b.brandName" :value="b.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="套餐" required>
          <el-select v-model="form.packageType" style="width: 100%" @change="onPackageChange">
            <el-option
              v-for="pkg in packagePlanOptions"
              :key="pkg.packageType"
              :label="dictStore.label('package_type', pkg.packageType)"
              :value="pkg.packageType"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="签约价(元)" required>
          <el-input-number v-model="form.packagePriceYuan" :disabled="true" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="服务月数" required>
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
        <el-form-item label="归属类型">
          <el-input :value="companyOwnerTypeLabel" disabled />
        </el-form-item>
        <el-form-item label="地区"><RegionCascader v-model="form.regionCodes" /></el-form-item>
        <el-form-item v-if="formMode === 'edit' && (canActivateProject || canCloseProject)" label="激活状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option v-if="canActivateProject" :label="dictStore.label('project_status', 'active')" value="active" />
            <el-option v-if="canCloseProject" :label="dictStore.label('project_status', 'paused')" value="paused" />
          </el-select>
        </el-form-item>
        <el-form-item label="交付模式"><el-input v-model="form.deliveryMode" /></el-form-item>
        <el-divider content-position="left">问题池录入</el-divider>
        <el-form-item label="问题列表">
          <div class="w-full">
            <div class="mb-2 flex justify-end">
              <el-button type="primary" @click="addQuestionItem">新增问题</el-button>
            </div>
            <el-table :data="form.questionPoolItems" border>
              <el-table-column label="问题内容" min-width="240">
                <template #default="scope">
                  <el-input v-model="scope.row.questionText" placeholder="输入问题内容" />
                </template>
              </el-table-column>
              <el-table-column label="分类" width="130">
                <template #default="scope">
                  <el-select v-model="scope.row.questionType" style="width: 100%">
                    <el-option
                      v-for="item in dictStore.options('question_type')"
                      :key="item.dictKey"
                      :label="item.dictValue"
                      :value="item.dictKey"
                    />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="等级" width="110">
                <template #default="scope">
                  <el-select v-model="scope.row.priority" style="width: 100%">
                    <el-option
                      v-for="item in dictStore.options('question_priority')"
                      :key="item.dictKey"
                      :label="item.dictValue"
                      :value="item.dictKey"
                    />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="核心问题" width="100">
                <template #default="scope">
                  <el-switch v-model="scope.row.isCore" :disabled="!canToggleCoreFlag(scope.row)" />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="90">
                <template #default="scope">
                  <el-button link type="danger" :disabled="!canRemoveQuestionAt(scope.$index)" @click="removeQuestionItem(scope.$index)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-form-item>
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
import { computed, reactive, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { useDictStore } from '@/stores/dict'
import {
  getProjectList,
  createProject,
  deleteProject,
  getCurrentQuestionPool,
  getProjectPlatformOptions,
  updateProject,
  updateProjectStatus,
} from '@/api/project'
import { getBrandList, getCompanyList } from '@/api/customer'
import { getEnabledPackagePlans } from '@/api/packagePlan'
import type { Brand, Company, PackagePlan, Project, ProjectPlatformOption, QuestionPoolItemInput } from '@/types'
import DataState from '@/components/ui/DataState.vue'
import RegionCascader from '@/components/ui/RegionCascader.vue'
import { regionCodesFromPayload, regionDisplayFromPayload, regionPayloadFromCodes } from '@/constants/region'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const dictStore = useDictStore()
const canWriteProject = computed(() => userStore.hasPermission('project.write'))
const canActivateProject = computed(() => userStore.hasPermission('project.status.activate'))
const canCloseProject = computed(() => userStore.hasPermission('project.status.close'))
const canConfirmCoreQuestion = computed(() => userStore.hasPermission('question_pool.core.confirm'))
const canDeleteCoreQuestion = computed(() => userStore.hasPermission('question_pool.core.delete'))
const presetCompanyId = computed(() => {
  const raw = Number(route.query.companyId)
  return Number.isFinite(raw) && raw > 0 ? raw : null
})
const presetBrandId = computed(() => {
  const raw = Number(route.query.brandId)
  return Number.isFinite(raw) && raw > 0 ? raw : null
})
const fromCustomerBrandPath = computed(() => {
  return route.query.source === 'customer_brand' && !!presetCompanyId.value && !!presetBrandId.value
})
const lockCompanyBrandSelection = computed(() => formMode.value === 'create' && fromCustomerBrandPath.value)
const companyOwnerTypeLabel = computed(() => {
  const company = companyOptions.value.find((c) => c.id === form.companyId)
  const key = company?.ownerType || (company?.partnerId ? 'partner' : 'direct')
  return dictStore.label('owner_type', key) || '-'
})

const statusOptions = computed(() => ['active', 'paused'])
const loading = ref(false)
const saving = ref(false)
const rows = ref<Project[]>([])
const companyOptions = ref<Company[]>([])
const brandOptions = ref<Brand[]>([])
const packagePlans = ref<PackagePlan[]>([])
const platformOptions = ref<Record<'P0' | 'P1' | 'P2', ProjectPlatformOption[]>>({
  P0: [],
  P1: [],
  P2: [],
})
const page = reactive({ current: 1, size: 10, total: 0 })
const query = reactive({ keyword: '', status: '' })

const formVisible = ref(false)
const formRef = ref<FormInstance>()
const formMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const originalQuestionPoolSignature = ref('')
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
  questionPoolItems: [] as QuestionPoolItemInput[],
  primaryGoal: '',
  remark: '',
})


const rules: FormRules = {
  projectName: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
  companyId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  brandId: [{ required: true, message: '请选择品牌', trigger: 'change' }],
  packageType: [{ required: true, message: '请选择套餐', trigger: 'change' }],
  packagePriceYuan: [{ required: true, message: '请输入签约价', trigger: 'change' }],
  serviceMonths: [{ required: true, message: '请输入服务月数', trigger: 'change' }],
}

const packagePlanOptions = computed(() => {
  if (!form.packageType) {
    return packagePlans.value
  }
  const exists = packagePlans.value.some((p) => p.packageType === form.packageType)
  if (exists) {
    return packagePlans.value
  }
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

function centsToYuan(v?: number | null) {
  if (v == null) return '-'
  return Number(v).toFixed(2)
}

function regionDisplay(project: Project) {
  return regionDisplayFromPayload(project)
}

function yuanToCents(v: number) {
  return Number(v.toFixed(2))
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

function applyDefaultPackage() {
  const firstPlan = packagePlans.value[0]
  if (!firstPlan) {
    form.packageType = ''
    form.packagePriceYuan = 0
    form.serviceMonths = 0
    return
  }
  form.packageType = firstPlan.packageType
  form.packagePriceYuan = Number(firstPlan.standardPrice)
  form.serviceMonths = firstPlan.serviceMonths
  form.requiredPlatformP0Count = firstPlan.platformP0Count || 0
  form.requiredPlatformP1Count = firstPlan.platformP1Count || 0
  form.requiredPlatformP2Count = firstPlan.platformP2Count || 0
}

function resetForm() {
  form.projectName = ''
  form.projectAliases = ''
  form.companyId = fromCustomerBrandPath.value ? presetCompanyId.value : null
  form.brandId = fromCustomerBrandPath.value ? presetBrandId.value : null
  applyDefaultPackage()
  form.selectedPlatformCodesP0 = []
  form.selectedPlatformCodesP1 = []
  form.selectedPlatformCodesP2 = []
  form.status = 'paused'
  form.regionCodes = []
  form.deliveryMode = 'managed'
  form.questionPoolItems = []
  originalQuestionPoolSignature.value = ''
  form.primaryGoal = ''
  form.remark = ''
}

function onCompanyChange() {
  if (lockCompanyBrandSelection.value) {
    form.companyId = presetCompanyId.value
    form.brandId = presetBrandId.value
    return
  }
  if (!form.companyId) {
    brandOptions.value = []
    form.brandId = null
    return
  }
  if (formMode.value === 'create') {
    form.brandId = null
  }
  loadBrands(form.companyId)
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
      current: page.current,
      size: page.size,
      keyword: query.keyword || undefined,
      status: query.status || undefined,
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

async function openCreate() {
  formMode.value = 'create'
  editingId.value = null
  resetForm()
  if (form.companyId) {
    await loadBrands(form.companyId)
    if (lockCompanyBrandSelection.value) {
      const hasPresetBrand = brandOptions.value.some((b) => b.id === presetBrandId.value)
      if (!hasPresetBrand) {
        ElMessage.warning('当前客户下未找到预设品牌，请返回客户详情页重新发起')
      } else {
        form.brandId = presetBrandId.value
      }
    }
  } else {
    brandOptions.value = []
  }
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
  form.questionPoolItems = []
  form.primaryGoal = row.primaryGoal || ''
  form.remark = (row as any).remark || ''
  await loadBrands(form.companyId)
  try {
    const { data } = await getCurrentQuestionPool(row.id)
    const current = data.data
    form.questionPoolItems = (current?.items || []).map((item) => ({
      questionText: item.questionText,
      questionType: item.questionType,
      priority: item.priority as 'A' | 'B' | 'C',
      isCore: !!item.isCore,
    }))
  } catch {
    form.questionPoolItems = []
  }
  originalQuestionPoolSignature.value = buildQuestionPoolSignature(form.questionPoolItems)
  formVisible.value = true
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  if (!form.companyId) {
    ElMessage.warning('请先选择客户')
    return
  }
  if (formMode.value === 'create' && !form.brandId) {
    ElMessage.warning('新增项目时品牌为必填项')
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
  const selectedTotal =
    form.selectedPlatformCodesP0.length + form.selectedPlatformCodesP1.length + form.selectedPlatformCodesP2.length
  const uniquePlatforms = new Set([
    ...form.selectedPlatformCodesP0,
    ...form.selectedPlatformCodesP1,
    ...form.selectedPlatformCodesP2,
  ])
  if (uniquePlatforms.size !== selectedTotal) {
    ElMessage.warning('同一平台不能同时出现在 P0/P1/P2')
    return
  }
  const normalizedQuestionItems = form.questionPoolItems
    .map((item) => ({
      questionText: (item.questionText || '').trim(),
      questionType: item.questionType,
      priority: item.priority,
      isCore: !!item.isCore,
    }))
    .filter((item) => item.questionText.length > 0)

  for (const item of normalizedQuestionItems) {
    if (!item.questionType || !item.priority) {
      ElMessage.warning('问题池条目需选择分类和等级')
      return
    }
  }

  saving.value = true
  try {
    const region = regionPayloadFromCodes(form.regionCodes)
    const payload: Record<string, any> = {
      provinceCode: region.provinceCode,
      provinceName: region.provinceName,
      cityCode: region.cityCode,
      cityName: region.cityName,
      districtCode: region.districtCode,
      districtName: region.districtName,
      projectName: form.projectName,
      projectAliases: form.projectAliases || undefined,
      companyId: form.companyId,
      brandId: form.brandId,
      packageType: form.packageType,
      packagePrice: yuanToCents(form.packagePriceYuan),
      serviceMonths: form.serviceMonths,
      selectedPlatformCodesP0: form.selectedPlatformCodesP0,
      selectedPlatformCodesP1: form.selectedPlatformCodesP1,
      selectedPlatformCodesP2: form.selectedPlatformCodesP2,
      deliveryMode: form.deliveryMode || 'managed',
      primaryGoal: form.primaryGoal || undefined,
      remark: form.remark || undefined,
    }
    if (lockCompanyBrandSelection.value) {
      payload.companyId = presetCompanyId.value
      payload.brandId = presetBrandId.value
    }
    if (formMode.value === 'create') {
      payload.questionPoolItems = normalizedQuestionItems
    } else {
      const newSignature = buildQuestionPoolSignature(normalizedQuestionItems)
      const questionPoolChanged = newSignature !== originalQuestionPoolSignature.value
      if (questionPoolChanged) {
        const { value } = await ElMessageBox.prompt(
          '检测到问题池内容有变化，请填写调整原因',
          '问题池调整原因',
          {
            inputPlaceholder: '例如：客户反馈调整、策略优化',
            inputValidator: (val) => !!(val && val.trim()),
            inputErrorMessage: '请填写调整原因',
            confirmButtonText: '确认保存',
            cancelButtonText: '取消',
            closeOnClickModal: false,
          },
        )
        payload.questionPoolChangeReason = value.trim()
        payload.questionPoolItems = normalizedQuestionItems
      }
    }

    if (formMode.value === 'create') {
      const { data } = await createProject(payload)
      ElMessage.success('保存成功')
      formVisible.value = false
      await load()
      goDetail(data.data.id)
    } else if (editingId.value) {
      await updateProject(editingId.value, payload)
      if (form.status !== originalStatus.value) {
        if (form.status === 'active' && !canActivateProject.value) {
          ElMessage.warning('当前账号无项目激活权限')
          return
        }
        if (form.status === 'paused' && !canCloseProject.value) {
          ElMessage.warning('当前账号无项目关闭权限')
          return
        }
        await updateProjectStatus(editingId.value, form.status)
      }
      ElMessage.success('保存成功')
      formVisible.value = false
      load()
    }
  } catch (err: any) {
    if (err === 'cancel' || err === 'close') return
  } finally {
    saving.value = false
  }
}

function addQuestionItem() {
  form.questionPoolItems.push({
    questionText: '',
    questionType: dictStore.options('question_type')[0]?.dictKey || 'brand',
    priority: (dictStore.options('question_priority')[0]?.dictKey as 'A' | 'B' | 'C') || 'A',
    isCore: false,
  })
}

function removeQuestionItem(index: number) {
  if (!canRemoveQuestionAt(index)) {
    ElMessage.warning('当前账号无删除A类核心问题权限')
    return
  }
  form.questionPoolItems.splice(index, 1)
}

function canToggleCoreFlag(item: QuestionPoolItemInput) {
  if (item.isCore) {
    return canDeleteCoreQuestion.value
  }
  return canConfirmCoreQuestion.value
}

function canRemoveQuestionAt(index: number) {
  const item = form.questionPoolItems[index]
  if (!item) return false
  if (!item.isCore) return true
  return canDeleteCoreQuestion.value
}

function buildQuestionPoolSignature(items: QuestionPoolItemInput[]) {
  return JSON.stringify(
    items.map((item) => ({
      questionText: (item.questionText || '').trim(),
      questionType: item.questionType,
      priority: item.priority,
      isCore: !!item.isCore,
    })),
  )
}

function goDetail(id: number) {
  router.push(`/admin/projects/${id}`)
}

function goActivate(id: number) {
  router.push({ path: `/admin/projects/${id}`, query: { activate: '1' } })
}

function onMoreCommand(row: Project, command: string) {
  if (command === 'questionPool') {
    goQuestionPool(row.id)
    return
  }
  if (command === 'activate') {
    goActivate(row.id)
    return
  }
  if (command === 'edit') {
    openEdit(row)
    return
  }
  if (command === 'delete') {
    removeProject(row)
  }
}

function goQuestionPool(id: number) {
  router.push(`/admin/projects/${id}/questions`)
}

async function removeProject(row: Project) {
  try {
    await ElMessageBox.confirm(
      `确认删除项目「${row.projectName}」？该操作不可撤销。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' },
    )
    await deleteProject(row.id)
    ElMessage.success('删除成功')
    await load()
  } catch {
    // canceled
  }
}

onMounted(async () => {
  await dictStore.ensureLoaded()
  await loadPackagePlans()
  await loadPlatformOptions()
  applyDefaultPackage()
  await loadCompanies()
  await loadBrands(form.companyId)
  await load()
  if (fromCustomerBrandPath.value && canWriteProject.value) {
    await openCreate()
  }
})
</script>

<style scoped>
.op-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}
</style>
