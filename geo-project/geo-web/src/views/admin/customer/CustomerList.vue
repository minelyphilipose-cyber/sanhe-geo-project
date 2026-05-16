<template>
  <div class="admin-page">
    <div class="admin-page-header">
      <div>
        <div class="admin-page-kicker">客户资产</div>
        <h1 class="admin-page-title">客户管理</h1>
        <div class="admin-page-subtitle">让每一个客户从品牌到项目都成为可经营的长期资产。</div>
      </div>
      <div class="admin-page-actions">
        <el-button v-if="canCreateCompany" type="primary" @click="openCreate">新建客户</el-button>
      </div>
    </div>

    <div class="admin-filter-panel">
      <div class="admin-filter-controls">
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
    </div>

    <div class="admin-metric-grid">
      <div class="admin-metric-card" style="--metric-accent: #2563eb; --metric-tone: #eff6ff">
        <span class="admin-metric-label">客户总数</span>
        <strong class="admin-metric-value">{{ page.total }}</strong>
        <span class="admin-metric-hint">当前筛选结果</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #059669; --metric-tone: #ecfdf5">
        <span class="admin-metric-label">直营客户</span>
        <strong class="admin-metric-value">{{ directCount }}</strong>
        <span class="admin-metric-hint">本页可见</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #7c3aed; --metric-tone: #f5f3ff">
        <span class="admin-metric-label">已签约</span>
        <strong class="admin-metric-value">{{ signedCount }}</strong>
        <span class="admin-metric-hint">本页可见</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #f59e0b; --metric-tone: #fffbeb">
        <span class="admin-metric-label">待跟进</span>
        <strong class="admin-metric-value">{{ potentialCount }}</strong>
        <span class="admin-metric-hint">潜在/跟进中客户</span>
      </div>
    </div>

    <el-card class="admin-table-card">
      <DataState :loading="loading" :empty="!loading && rows.length === 0" empty-text="暂无客户数据">
        <el-table class="customer-list-table" :data="rows" border table-layout="fixed">
        <el-table-column label="客户对象" min-width="300" show-overflow-tooltip>
          <template #default="scope">
            <div class="admin-entity-cell">
              <div class="admin-entity-avatar customer-avatar" :class="customerAvatarClass(scope.row.status)">
                {{ entityInitial(scope.row.companyName) }}
              </div>
              <div class="min-w-0">
                <div class="admin-entity-main">{{ scope.row.companyName }}</div>
                <div class="admin-entity-sub">
                  {{ customerIdentityLabel(scope.row) }}
                </div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="联系人" min-width="160" show-overflow-tooltip>
          <template #default="scope">
            <span v-if="contactText(scope.row) !== '-'">{{ contactText(scope.row) }}</span>
            <el-button
              v-else-if="canUpdateCompany"
              class="customer-contact-link"
              link
              type="primary"
              @click="openEdit(scope.row)"
            >
              + 添加联系人
            </el-button>
            <span v-else class="customer-empty-text">-</span>
          </template>
        </el-table-column>
        <el-table-column label="行业" min-width="200" show-overflow-tooltip>
          <template #default="scope">{{ industryLabels(scope.row) }}</template>
        </el-table-column>
        <el-table-column prop="city" label="地区" min-width="150" show-overflow-tooltip>
          <template #default="scope">{{ companyRegion(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="120" show-overflow-tooltip>
          <template #default="scope">
            <span class="customer-status-text" :class="companyStatusClass(scope.row.status)">
              <span class="customer-status-dot" />
              {{ dictStore.label('company_status', scope.row.status) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="scope">
            <div class="admin-row-actions">
              <el-button link type="primary" @click="goDetail(scope.row.id)">详情</el-button>
              <el-button v-if="canUpdateCompany" class="customer-edit-action" link @click="openEdit(scope.row)">编辑</el-button>
              <el-button v-if="canDeleteCompany" link type="danger" @click="removeCompany(scope.row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
        </el-table>

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
      </DataState>
    </el-card>

    <el-dialog
      v-model="formVisible"
      :title="formMode === 'create' ? '新建客户' : '编辑客户'"
      width="860px"
      class="admin-editor-dialog"
    >
      <el-form ref="formRef" class="admin-dialog-form" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="公司名称" required><el-input v-model="form.companyName" /></el-form-item>
        <el-form-item label="客户联系人"><el-input v-model="form.contactName" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.contactPhone" /></el-form-item>
        <el-form-item class="is-full" label="行业" prop="industryTags">
          <el-select
            v-model="form.industryTags"
            multiple
            filterable
            allow-create
            default-first-option
            style="width: 100%"
          >
            <el-option
              v-for="item in dictStore.options('industry_tag')"
              :key="item.dictKey"
              :label="item.dictValue"
              :value="item.dictKey"
            />
          </el-select>
        </el-form-item>
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
        <el-form-item v-if="canSelectSalesOwner" label="销售人员">
          <el-select v-model="form.salesOwnerId" clearable filterable placeholder="请选择销售人员" style="width: 100%">
            <el-option
              v-for="item in salesOwnerOptions"
              :key="item.id"
              :label="item.displayName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
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
        <el-form-item class="is-full" label="备注"><el-input v-model="form.remark" type="textarea" :rows="3" /></el-form-item>
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
import { createCompany, deleteCompany, getCompanyList, getSalesOwnerOptions, updateCompany, type SalesOwnerOption } from '@/api/customer'
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
const canSelectSalesOwner = computed(() => userStore.role !== 'sales' && (canCreateCompany.value || canUpdateCompany.value))

const loading = ref(false)
const saving = ref(false)
const rows = ref<Company[]>([])
const partnerOptions = ref<PartnerItem[]>([])
const salesOwnerOptions = ref<SalesOwnerOption[]>([])
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
const directCount = computed(() => rows.value.filter((row) => row.ownerType === 'direct').length)
const signedCount = computed(() =>
  rows.value.filter((row) => ['signed', 'deal', 'converted'].includes(String((row as any).status || ''))).length
)
const potentialCount = computed(() => rows.value.filter((row) => ['potential', 'following', 'follow_up'].includes(String((row as any).status || ''))).length)

function resetForm() {
  form.companyName = ''
  form.contactName = ''
  form.contactPhone = ''
  form.industryTags = []
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
  const parts = regionParts(company).map((item) => item.label)
  return parts.length ? parts.join(' · ') : regionDisplayFromPayload(company) || company.city || '-'
}

function contactText(company: Company) {
  const name = company.contactName || ''
  const phone = company.contactPhone || ''
  if (name && phone) return `${name} / ${phone}`
  return name || phone || '-'
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

function normalizeIndustryTags(tags: string[]) {
  const normalized: string[] = []
  for (const tag of tags) {
    const value = tag.trim()
    if (value && !normalized.includes(value)) {
      normalized.push(value)
    }
  }
  return normalized
}

function industryLabels(company: Company) {
  const labels = industryLabelList(company)
  return labels.length ? labels.join(' / ') : '-'
}

function industryLabelList(company: Company) {
  return parseIndustryTags(company.industryTags).map((tag) => dictStore.label('industry_tag', tag) || tag)
}

function regionParts(company: Company) {
  const parts: Array<{ level: 'province' | 'city' | 'district'; label: string }> = []
  const source = company as any
  if (source.provinceName) parts.push({ level: 'province', label: source.provinceName })
  if (source.cityName && source.cityName !== source.provinceName) parts.push({ level: 'city', label: source.cityName })
  if (source.districtName) parts.push({ level: 'district', label: source.districtName })
  if (!parts.length && company.city) parts.push({ level: 'city', label: company.city })
  return parts
}

function customerIdentityLabel(company: Company) {
  if (company.ownerType === 'direct') return '直营客户'
  if (company.ownerType === 'partner' || company.sourceType === 'partner') return '合伙人客户'
  if (company.ownerType === 'joint') return '合作客户'
  return dictStore.label('owner_type', company.ownerType) || '客户'
}

function entityInitial(value?: string | null) {
  const text = String(value || '').trim()
  return text ? Array.from(text)[0] : '客'
}

function companyStatusClass(status?: string | null) {
  const value = String(status || '')
  if (['active', 'signed', 'deal', 'converted'].includes(value)) return 'is-success'
  if (['disabled', 'lost', 'deleted'].includes(value)) return 'is-muted'
  return 'is-warning'
}

function customerAvatarClass(status?: string | null) {
  const value = String(status || '')
  if (['active', 'signed', 'deal', 'converted'].includes(value)) return 'is-signed'
  if (['disabled', 'lost', 'deleted'].includes(value)) return 'is-disabled'
  return 'is-potential'
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

async function loadSalesOwners() {
  if (!canSelectSalesOwner.value) {
    salesOwnerOptions.value = []
    return
  }
  try {
    const { data } = await getSalesOwnerOptions()
    salesOwnerOptions.value = data.data || []
  } catch {
    salesOwnerOptions.value = []
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
    const payload = {
      companyName: form.companyName,
      contactName: form.contactName || undefined,
      contactPhone: form.contactPhone || undefined,
      industryTags: normalizeIndustryTags(form.industryTags),
      city: region.displayName,
      provinceCode: region.provinceCode,
      provinceName: region.provinceName,
      cityCode: region.cityCode,
      cityName: region.cityName,
      districtCode: region.districtCode,
      districtName: region.districtName,
      ownerType: form.ownerType,
      partnerId: form.partnerId || undefined,
      salesOwnerId: canSelectSalesOwner.value ? form.salesOwnerId || undefined : undefined,
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
  await Promise.all([loadPartners(), loadSalesOwners()])
  await load()
})
</script>

<style scoped>
.customer-empty-text {
  color: #94a3b8;
}

.customer-avatar.is-signed {
  background: linear-gradient(135deg, #059669, #10b981);
}

.customer-avatar.is-potential {
  background: linear-gradient(135deg, #2563eb, #0891b2);
}

.customer-avatar.is-disabled {
  background: linear-gradient(135deg, #64748b, #94a3b8);
}

.customer-contact-link {
  color: #64748b;
  font-weight: 500;
}

.customer-contact-link:hover {
  color: #2563eb;
}

.customer-status-text {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: #92400e;
  font-weight: 700;
}

.customer-status-text.is-success {
  color: #047857;
}

.customer-status-text.is-muted {
  color: #64748b;
}

.customer-status-dot {
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: #f59e0b;
  box-shadow: 0 0 0 3px rgba(245, 158, 11, 0.14);
  flex-shrink: 0;
}

.customer-status-text.is-success .customer-status-dot {
  background: #10b981;
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.14);
}

.customer-status-text.is-muted .customer-status-dot {
  background: #94a3b8;
  box-shadow: 0 0 0 3px rgba(148, 163, 184, 0.16);
}

.customer-edit-action {
  color: #64748b;
}

.customer-edit-action:hover {
  color: #334155;
}
</style>

