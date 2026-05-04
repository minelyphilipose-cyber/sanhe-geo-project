<template>
  <div>
    <div class="mb-4 flex items-center justify-between gap-3">
      <div class="flex items-center gap-2">
        <el-input v-model="query.keyword" placeholder="搜索公司名称" clearable style="width: 260px" @keyup.enter="load" />
        <el-select v-model="query.ownerType" placeholder="归属类型" clearable style="width: 140px" @change="load">
          <el-option
            v-for="item in dictStore.options('owner_type')"
            :key="item.dictKey"
            :label="item.dictValue"
            :value="item.dictKey"
          />
        </el-select>
        <el-button @click="load">查询</el-button>
      </div>
      <el-button v-if="canCreateCompany" type="primary" @click="openCreate">新建客户</el-button>
    </div>

    <el-card>
      <DataState :loading="loading" :empty="!loading && rows.length === 0" empty-text="暂无客户数据">
        <el-table :data="rows" border>
        <el-table-column prop="companyName" label="公司名称" min-width="220" />
        <el-table-column label="联系人" min-width="160">
          <template #default="scope">{{ scope.row.contactName || '-' }}{{ scope.row.contactPhone ? ` / ${scope.row.contactPhone}` : '' }}</template>
        </el-table-column>
        <el-table-column label="行业" min-width="180">
          <template #default="scope">{{ industryLabels(scope.row) }}</template>
        </el-table-column>
        <el-table-column prop="businessDirection" label="主营方向" min-width="160" />
        <el-table-column prop="city" label="地区" min-width="220">
          <template #default="scope">{{ companyRegion(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="归属" width="100">
          <template #default="scope">{{ dictStore.label('owner_type', scope.row.ownerType) }}</template>
        </el-table-column>
        <el-table-column label="来源" width="120">
          <template #default="scope">{{ dictStore.label('company_source_type', scope.row.sourceType) || '-' }}</template>
        </el-table-column>
        <el-table-column prop="partnerName" label="合伙人" width="160" />
        <el-table-column label="状态" width="120">
          <template #default="scope">{{ dictStore.label('company_status', scope.row.status) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="goDetail(scope.row.id)">详情</el-button>
            <el-button v-if="canUpdateCompany" link type="primary" @click="openEdit(scope.row)">编辑</el-button>
            <el-button v-if="canDeleteCompany" link type="danger" @click="removeCompany(scope.row)">删除</el-button>
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

    <el-dialog v-model="formVisible" :title="formMode === 'create' ? '新建客户' : '编辑客户'" width="620px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="公司名称" required><el-input v-model="form.companyName" /></el-form-item>
        <el-form-item label="客户联系人"><el-input v-model="form.contactName" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.contactPhone" /></el-form-item>
        <el-form-item label="行业" prop="industryTags">
          <el-select v-model="form.industryTags" multiple filterable style="width: 100%">
            <el-option
              v-for="item in dictStore.options('industry_tag')"
              :key="item.dictKey"
              :label="item.dictValue"
              :value="item.dictKey"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="主营方向"><el-input v-model="form.businessDirection" /></el-form-item>
        <el-form-item label="服务区域">
          <RegionCascader v-model="form.serviceAreaCodes" />
          <div class="mt-1 text-xs text-gray-500">{{ serviceAreaTextPreview || '未选择' }}</div>
        </el-form-item>
        <el-form-item label="竞品"><el-input v-model="form.competitors" placeholder="多个可用逗号分隔" /></el-form-item>
        <el-form-item label="官网"><el-input v-model="form.officialWebsite" /></el-form-item>
        <el-form-item label="公众号"><el-input v-model="form.officialAccount" /></el-form-item>
        <el-form-item label="视频号"><el-input v-model="form.videoAccount" /></el-form-item>
        <el-form-item label="抖音号"><el-input v-model="form.douyinAccount" /></el-form-item>
        <el-form-item label="地区"><RegionCascader v-model="form.regionCodes" /></el-form-item>
        <el-form-item label="归属类型" required>
          <el-select v-model="form.ownerType" style="width: 100%">
            <el-option
              v-for="item in dictStore.options('owner_type')"
              :key="item.dictKey"
              :label="item.dictValue"
              :value="item.dictKey"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="formMode === 'edit'" label="客户来源" required>
          <el-select v-model="form.sourceType" style="width: 100%" disabled>
            <el-option
              v-for="item in dictStore.options('company_source_type')"
              :key="item.dictKey"
              :label="item.dictValue"
              :value="item.dictKey"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-else label="客户来源">
          <el-input :model-value="createSourceTypeLabel" disabled />
        </el-form-item>
        <el-form-item v-if="form.sourceType === 'partner'" label="所属合伙人">
          <el-select v-model="form.partnerId" clearable filterable style="width: 100%">
            <el-option v-for="p in partnerOptions" :key="p.id" :label="p.partnerName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="销售ID"><el-input-number v-model="form.salesOwnerId" :min="1" style="width: 100%" /></el-form-item>
        <el-form-item label="来源"><el-input v-model="form.referralSource" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option
              v-for="item in dictStore.options('company_status')"
              :key="item.dictKey"
              :label="item.dictValue"
              :value="item.dictKey"
            />
          </el-select>
        </el-form-item>
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
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { useDictStore } from '@/stores/dict'
import { createCompany, deleteCompany, getCompanyList, updateCompany } from '@/api/customer'
import { getPartnerList, type PartnerItem } from '@/api/partner'
import type { Company } from '@/types'
import DataState from '@/components/ui/DataState.vue'
import RegionCascader from '@/components/ui/RegionCascader.vue'
import { regionCodesFromPayload, regionDisplayFromPayload, regionPayloadFromCodes } from '@/constants/region'

const router = useRouter()
const userStore = useUserStore()
const dictStore = useDictStore()
const canCreateCompany = computed(() => userStore.hasPermission('company.create'))
const canUpdateCompany = computed(() => userStore.hasPermission('company.update'))
const canDeleteCompany = computed(() => userStore.hasPermission('company.delete'))

const loading = ref(false)
const saving = ref(false)
const rows = ref<Company[]>([])
const partnerOptions = ref<PartnerItem[]>([])
const page = reactive({ current: 1, size: 10, total: 0 })
const query = reactive({ keyword: '', ownerType: '' })

const formVisible = ref(false)
const formRef = ref<FormInstance>()
const formMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)

const form = reactive({
  companyName: '',
  contactName: '',
  contactPhone: '',
  industryTags: [] as string[],
  businessDirection: '',
  serviceArea: '',
  serviceAreaCodes: [] as string[],
  competitors: '',
  officialWebsite: '',
  officialAccount: '',
  videoAccount: '',
  douyinAccount: '',
  regionCodes: [] as string[],
  ownerType: 'direct',
  sourceType: 'internal' as 'internal' | 'partner',
  partnerId: null as number | null,
  salesOwnerId: null as number | null,
  referralSource: '',
  status: 'potential',
  remark: '',
})
const rules: FormRules = {
  companyName: [{ required: true, message: '请输入公司名称', trigger: 'blur' }],
  industryTags: [{ required: true, message: '请选择至少一个行业', trigger: 'change' }],
  ownerType: [{ required: true, message: '请选择归属类型', trigger: 'change' }],
}

const isPartnerOperator = computed(() => ['partner', 'partner_staff', 'partner_viewer'].includes(userStore.role || ''))
const createSourceType = computed<'internal' | 'partner'>(() => (isPartnerOperator.value ? 'partner' : 'internal'))
const createSourceTypeLabel = computed(() => dictStore.label('company_source_type', createSourceType.value) || (createSourceType.value === 'partner' ? '合伙人' : '本部'))

function resetForm() {
  form.companyName = ''
  form.contactName = ''
  form.contactPhone = ''
  form.industryTags = []
  form.businessDirection = ''
  form.serviceArea = ''
  form.serviceAreaCodes = []
  form.competitors = ''
  form.officialWebsite = ''
  form.officialAccount = ''
  form.videoAccount = ''
  form.douyinAccount = ''
  form.regionCodes = []
  form.ownerType = 'direct'
  form.sourceType = 'internal'
  form.partnerId = null
  form.salesOwnerId = null
  form.referralSource = ''
  form.status = 'potential'
  form.remark = ''
}

function companyRegion(company: Company) {
  return regionDisplayFromPayload(company) || company.city || '-'
}

function parseIndustryTags(value?: string | string[] | null) {
  if (Array.isArray(value)) return value
  if (!value) return []
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function industryLabels(company: Company) {
  const tags = parseIndustryTags(company.industryTags)
  if (!tags.length) return '-'
  return tags.map((tag) => dictStore.label('industry_tag', tag) || tag).join(' / ')
}

async function load() {
  loading.value = true
  try {
    const { data } = await getCompanyList({
      current: page.current,
      size: page.size,
      keyword: query.keyword || undefined,
      ownerType: query.ownerType || undefined,
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

async function loadPartners() {
  try {
    const { data } = await getPartnerList({ current: 1, size: 500 })
    partnerOptions.value = data.data.records || []
  } catch {
    partnerOptions.value = []
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
  form.sourceType = createSourceType.value
  formVisible.value = true
}

function openEdit(row: Company) {
  formMode.value = 'edit'
  editingId.value = row.id
  form.companyName = row.companyName
  form.contactName = row.contactName || ''
  form.contactPhone = row.contactPhone || ''
  form.industryTags = parseIndustryTags(row.industryTags)
  form.businessDirection = row.businessDirection || ''
  form.serviceArea = row.serviceArea || ''
  form.serviceAreaCodes = []
  form.competitors = row.competitors || ''
  form.officialWebsite = row.officialWebsite || ''
  form.officialAccount = row.officialAccount || ''
  form.videoAccount = row.videoAccount || ''
  form.douyinAccount = row.douyinAccount || ''
  form.regionCodes = regionCodesFromPayload(row)
  form.ownerType = row.ownerType
  form.sourceType = row.sourceType || (row.partnerId ? 'partner' : 'internal')
  form.partnerId = row.partnerId
  form.salesOwnerId = row.salesOwnerId
  form.referralSource = row.referralSource || ''
  form.status = (row as any).status || 'potential'
  form.remark = (row as any).remark || ''
  formVisible.value = true
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  if ((form.ownerType === 'partner' || form.ownerType === 'joint' || form.sourceType === 'partner') && !form.partnerId) {
    ElMessage.warning('当前客户需选择所属合伙人')
    return
  }
  saving.value = true
  try {
    const region = regionPayloadFromCodes(form.regionCodes)
    const serviceArea = regionPayloadFromCodes(form.serviceAreaCodes).displayName || form.serviceArea
    const payload = {
      companyName: form.companyName,
      contactName: form.contactName || undefined,
      contactPhone: form.contactPhone || undefined,
      industryTags: form.industryTags,
      businessDirection: form.businessDirection || undefined,
      serviceArea: serviceArea || undefined,
      competitors: form.competitors || undefined,
      officialWebsite: form.officialWebsite || undefined,
      officialAccount: form.officialAccount || undefined,
      videoAccount: form.videoAccount || undefined,
      douyinAccount: form.douyinAccount || undefined,
      city: region.displayName,
      provinceCode: region.provinceCode,
      provinceName: region.provinceName,
      cityCode: region.cityCode,
      cityName: region.cityName,
      districtCode: region.districtCode,
      districtName: region.districtName,
      ownerType: form.ownerType,
      partnerId: form.partnerId || undefined,
      salesOwnerId: form.salesOwnerId || undefined,
      referralSource: form.referralSource || undefined,
      status: form.status,
      remark: form.remark || undefined,
    }
    if (formMode.value === 'create') {
      const { data } = await createCompany(payload)
      formVisible.value = false
      ElMessage.success('保存成功')
      await load()
      goDetail(data.data.id)
    } else if (editingId.value) {
      await updateCompany(editingId.value, {
        ...payload,
        sourceType: form.sourceType,
      })
      formVisible.value = false
      ElMessage.success('保存成功')
      load()
    }
  } finally {
    saving.value = false
  }
}

function goDetail(id: number) {
  router.push(`/admin/customers/${id}`)
}

async function removeCompany(row: Company) {
  try {
    await ElMessageBox.confirm(
      `确认删除客户「${row.companyName}」？该操作不可撤销。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' },
    )
    await deleteCompany(row.id)
    ElMessage.success('删除成功')
    await load()
  } catch {
    // canceled
  }
}

onMounted(async () => {
  await dictStore.ensureLoaded()
  await loadPartners()
  await load()
})

const serviceAreaTextPreview = computed(() => {
  const selected = regionPayloadFromCodes(form.serviceAreaCodes).displayName
  if (selected) return selected
  return form.serviceArea
})
</script>

