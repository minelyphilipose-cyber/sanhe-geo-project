<template>
  <div class="space-y-4">
    <el-page-header content="客户详情" @back="$router.back()" />

    <el-card v-loading="loading">
      <template #header>
        <div class="flex items-center justify-between">
          <span>公司信息</span>
          <div class="space-x-2">
            <el-button v-if="canWriteCompany" type="primary" link @click="editVisible = true">编辑</el-button>
            <el-button v-if="canWriteCompany" type="danger" link @click="removeCurrentCompany">删除客户</el-button>
          </div>
        </div>
      </template>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="公司名称">{{ company?.companyName }}</el-descriptions-item>
        <el-descriptions-item label="行业">{{ company?.industry || '-' }}</el-descriptions-item>
        <el-descriptions-item label="城市">{{ company?.city || '-' }}</el-descriptions-item>
        <el-descriptions-item label="归属">{{ company?.ownerType }}</el-descriptions-item>
        <el-descriptions-item label="合伙人ID">{{ company?.partnerId ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ (company as any)?.status || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card v-loading="brandLoading">
      <template #header>
        <div class="flex items-center justify-between">
          <span>品牌列表</span>
          <el-button v-if="canWriteCompany" type="primary" @click="openBrandCreate">新增品牌</el-button>
        </div>
      </template>
      <DataState :loading="brandLoading" :empty="!brandLoading && brands.length === 0" empty-text="暂无品牌数据">
        <el-table :data="brands" border>
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="brandName" label="品牌名" min-width="180" />
          <el-table-column prop="brandSlug" label="标识" min-width="150" />
          <el-table-column prop="mainBusiness" label="主营业务" min-width="180" />
          <el-table-column prop="status" label="状态" width="110" />
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="scope">
              <el-button v-if="canWriteCompany" link type="primary" @click="openBrandEdit(scope.row)">编辑</el-button>
              <el-button v-if="canWriteCompany" link type="danger" @click="removeBrand(scope.row)">删除</el-button>
              <el-button v-if="canWriteProject" link type="primary" @click="goCreateProject(scope.row.id)">基于该品牌建项目</el-button>
            </template>
          </el-table-column>
        </el-table>
      </DataState>
    </el-card>

    <el-dialog v-model="editVisible" title="编辑客户" width="620px">
      <el-form ref="companyFormRef" :model="companyForm" :rules="companyRules" label-width="100px">
        <el-form-item label="公司名称" required><el-input v-model="companyForm.companyName" /></el-form-item>
        <el-form-item label="行业"><el-input v-model="companyForm.industry" /></el-form-item>
        <el-form-item label="城市"><el-input v-model="companyForm.city" /></el-form-item>
        <el-form-item label="归属" required>
          <el-select v-model="companyForm.ownerType" style="width: 100%">
            <el-option label="direct" value="direct" />
            <el-option label="partner" value="partner" />
            <el-option label="joint" value="joint" />
          </el-select>
        </el-form-item>
        <el-form-item label="合伙人ID"><el-input-number v-model="companyForm.partnerId" :min="1" style="width: 100%" /></el-form-item>
        <el-form-item label="销售ID"><el-input-number v-model="companyForm.salesOwnerId" :min="1" style="width: 100%" /></el-form-item>
        <el-form-item label="来源"><el-input v-model="companyForm.referralSource" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="companyForm.status" style="width: 100%">
            <el-option label="potential" value="potential" />
            <el-option label="signed" value="signed" />
            <el-option label="inactive" value="inactive" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="companyForm.remark" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCompany">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="brandVisible" :title="brandMode === 'create' ? '新增品牌' : '编辑品牌'" width="680px">
      <el-form ref="brandFormRef" :model="brandForm" :rules="brandRules" label-width="120px">
        <el-form-item label="品牌名称" required><el-input v-model="brandForm.brandName" /></el-form-item>
        <el-form-item label="品牌标识" required><el-input v-model="brandForm.brandSlug" /></el-form-item>
        <el-form-item label="主营业务"><el-input v-model="brandForm.mainBusiness" /></el-form-item>
        <el-form-item label="服务区域"><el-input v-model="brandForm.serviceArea" /></el-form-item>
        <el-form-item label="官网"><el-input v-model="brandForm.website" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="brandForm.phone" /></el-form-item>
        <el-form-item label="微信"><el-input v-model="brandForm.wechat" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="brandForm.status" style="width: 100%">
            <el-option label="draft" value="draft" />
            <el-option label="active" value="active" />
            <el-option label="archived" value="archived" />
          </el-select>
        </el-form-item>
        <el-form-item label="品牌描述"><el-input v-model="brandForm.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="标准口径"><el-input v-model="brandForm.standardBrandStatement" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="brandVisible = false">取消</el-button>
        <el-button type="primary" :loading="brandSaving" @click="submitBrand">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { useUserStore } from '@/stores/user'
import {
  createBrand,
  deleteBrand,
  deleteCompany,
  getBrandList,
  getCompanyDetail,
  updateBrand,
  updateCompany,
} from '@/api/customer'
import type { Brand, Company } from '@/types'
import DataState from '@/components/ui/DataState.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const canWriteCompany = computed(() => userStore.hasPermission('company.write'))
const canWriteProject = computed(() => userStore.hasPermission('project.write'))
const companyId = Number(route.params.id)
const hasValidId = Number.isFinite(companyId) && companyId > 0

const loading = ref(false)
const brandLoading = ref(false)
const saving = ref(false)
const brandSaving = ref(false)

const company = ref<Company | null>(null)
const brands = ref<Brand[]>([])
const companyFormRef = ref<FormInstance>()
const brandFormRef = ref<FormInstance>()

const editVisible = ref(false)
const companyForm = reactive({
  companyName: '',
  industry: '',
  city: '',
  ownerType: 'direct',
  partnerId: null as number | null,
  salesOwnerId: null as number | null,
  referralSource: '',
  status: 'potential',
  remark: '',
})
const companyRules: FormRules = {
  companyName: [{ required: true, message: '请输入公司名称', trigger: 'blur' }],
  ownerType: [{ required: true, message: '请选择归属', trigger: 'change' }],
}

const brandVisible = ref(false)
const brandMode = ref<'create' | 'edit'>('create')
const brandEditingId = ref<number | null>(null)
const brandForm = reactive({
  brandName: '',
  brandSlug: '',
  mainBusiness: '',
  serviceArea: '',
  website: '',
  phone: '',
  wechat: '',
  status: 'active',
  description: '',
  standardBrandStatement: '',
})
const brandRules: FormRules = {
  brandName: [{ required: true, message: '请输入品牌名称', trigger: 'blur' }],
  brandSlug: [
    { required: true, message: '请输入品牌标识', trigger: 'blur' },
    { pattern: /^[a-z0-9][a-z0-9_-]{1,127}$/, message: '标识需小写字母数字开头，可含 _ -', trigger: 'blur' },
  ],
}

function fillCompanyForm(data: Company) {
  companyForm.companyName = data.companyName
  companyForm.industry = data.industry || ''
  companyForm.city = data.city || ''
  companyForm.ownerType = data.ownerType
  companyForm.partnerId = data.partnerId
  companyForm.salesOwnerId = data.salesOwnerId
  companyForm.referralSource = data.referralSource || ''
  companyForm.status = (data as any).status || 'potential'
  companyForm.remark = (data as any).remark || ''
}

function resetBrandForm() {
  brandForm.brandName = ''
  brandForm.brandSlug = ''
  brandForm.mainBusiness = ''
  brandForm.serviceArea = ''
  brandForm.website = ''
  brandForm.phone = ''
  brandForm.wechat = ''
  brandForm.status = 'active'
  brandForm.description = ''
  brandForm.standardBrandStatement = ''
}

async function loadCompany() {
  loading.value = true
  try {
    const { data } = await getCompanyDetail(companyId)
    company.value = data.data
    fillCompanyForm(data.data)
  } catch {
    company.value = null
  } finally {
    loading.value = false
  }
}

async function removeCurrentCompany() {
  if (!company.value) return
  try {
    await ElMessageBox.confirm(
      `确认删除客户「${company.value.companyName}」？该操作不可撤销。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' },
    )
    await deleteCompany(companyId)
    ElMessage.success('删除成功')
    router.push('/admin/customers')
  } catch {
    // canceled
  }
}

async function loadBrands() {
  brandLoading.value = true
  try {
    const { data } = await getBrandList({ current: 1, size: 200, companyId })
    brands.value = data.data.records || []
  } catch {
    brands.value = []
  } finally {
    brandLoading.value = false
  }
}

async function submitCompany() {
  const valid = await companyFormRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  if ((companyForm.ownerType === 'partner' || companyForm.ownerType === 'joint') && !companyForm.partnerId) {
    ElMessage.warning('partner/joint 客户需填写合伙人ID')
    return
  }
  saving.value = true
  try {
    await updateCompany(companyId, {
      companyName: companyForm.companyName,
      industry: companyForm.industry || undefined,
      city: companyForm.city || undefined,
      ownerType: companyForm.ownerType,
      partnerId: companyForm.partnerId || undefined,
      salesOwnerId: companyForm.salesOwnerId || undefined,
      referralSource: companyForm.referralSource || undefined,
      status: companyForm.status,
      remark: companyForm.remark || undefined,
    } as any)
    ElMessage.success('客户信息已更新')
    editVisible.value = false
    await Promise.all([loadCompany(), loadBrands()])
  } finally {
    saving.value = false
  }
}

function openBrandCreate() {
  brandMode.value = 'create'
  brandEditingId.value = null
  resetBrandForm()
  brandVisible.value = true
}

function openBrandEdit(row: Brand) {
  brandMode.value = 'edit'
  brandEditingId.value = row.id
  brandForm.brandName = row.brandName
  brandForm.brandSlug = row.brandSlug
  brandForm.mainBusiness = row.mainBusiness || ''
  brandForm.serviceArea = row.serviceArea || ''
  brandForm.website = row.website || ''
  brandForm.phone = row.phone || ''
  brandForm.wechat = row.wechat || ''
  brandForm.status = (row as any).status || 'active'
  brandForm.description = row.description || ''
  brandForm.standardBrandStatement = row.standardBrandStatement || ''
  brandVisible.value = true
}

async function submitBrand() {
  const valid = await brandFormRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  brandSaving.value = true
  try {
    const payload = {
      companyId,
      brandName: brandForm.brandName,
      brandSlug: brandForm.brandSlug,
      mainBusiness: brandForm.mainBusiness || undefined,
      serviceArea: brandForm.serviceArea || undefined,
      website: brandForm.website || undefined,
      phone: brandForm.phone || undefined,
      wechat: brandForm.wechat || undefined,
      status: brandForm.status,
      description: brandForm.description || undefined,
      standardBrandStatement: brandForm.standardBrandStatement || undefined,
    }

    if (brandMode.value === 'create') {
      await createBrand(payload as any)
    } else if (brandEditingId.value) {
      await updateBrand(brandEditingId.value, payload as any)
    }

    ElMessage.success('品牌保存成功')
    brandVisible.value = false
    resetBrandForm()
    await loadBrands()
  } finally {
    brandSaving.value = false
  }
}

async function removeBrand(row: Brand) {
  try {
    await ElMessageBox.confirm(
      `确认删除品牌「${row.brandName}」？该操作不可撤销。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' },
    )
    await deleteBrand(row.id)
    ElMessage.success('删除成功')
    await loadBrands()
  } catch {
    // canceled
  }
}

function goCreateProject(brandId: number) {
  router.push({ path: '/admin/projects', query: { brandId: String(brandId), companyId: String(companyId) } })
}

onMounted(async () => {
  if (!hasValidId) {
    ElMessage.error('无效的客户ID')
    return
  }
  await loadCompany()
  await loadBrands()
})
</script>

