<template>
  <div class="admin-page">
    <el-page-header content="客户详情" @back="$router.back()" />

    <section v-if="company" class="admin-object-hero customer-detail-hero" :class="customerHeroClass((company as any).status)">
      <div class="admin-object-hero-main">
        <div>
          <h1 class="admin-object-title">{{ company.companyName }}</h1>
          <div class="admin-object-meta">
            {{ companyIndustryText }} · {{ companyRegion(company) }} · {{ dictStore.label('owner_type', company.ownerType) || '-' }}
          </div>
        </div>
        <span class="admin-status-tag" :class="companyStatusClass((company as any).status)">
          {{ dictStore.label('company_status', (company as any).status) || '-' }}
        </span>
      </div>
      <div class="admin-object-kpis">
        <div class="admin-object-kpi customer-hero-kpi customer-hero-kpi--brand">
          <span>品牌数量</span>
          <strong>{{ brands.length }}</strong>
        </div>
        <div class="admin-object-kpi customer-hero-kpi customer-hero-kpi--package">
          <span>套餐状态</span>
          <strong>{{ activePackageBinding ? '已绑定' : '未绑定' }}</strong>
        </div>
        <div class="admin-object-kpi customer-hero-kpi customer-hero-kpi--quota">
          <span>关键词组额度</span>
          <strong>{{ keywordGroupQuotaText }}</strong>
        </div>
      </div>
    </section>

    <el-card v-loading="loading" class="admin-rich-card">
      <template #header>
        <div class="flex items-center justify-between">
          <span>客户信息</span>
          <div class="space-x-2">
            <el-button v-if="canUpdateCompany" type="primary" link @click="editVisible = true">编辑</el-button>
            <el-button v-if="canDeleteCompany" type="danger" link @click="removeCurrentCompany">删除客户</el-button>
          </div>
        </div>
      </template>
      <div class="admin-info-grid">
        <div v-for="item in customerBasicInfoItems" :key="item.label" class="admin-info-item">
          <span class="admin-info-label">{{ item.label }}</span>
          <strong class="admin-info-value">{{ item.value }}</strong>
        </div>
      </div>
    </el-card>

    <el-card v-loading="packageLoading" class="admin-rich-card">
      <template #header>
        <div class="flex items-center justify-between">
          <span>客户套餐</span>
          <div v-if="canManagePackageBinding" class="space-x-2">
            <el-button v-if="!activePackageBinding" type="primary" @click="openPackageBind">绑定套餐</el-button>
            <el-button v-else type="danger" plain @click="confirmUnbindPackage">解绑套餐</el-button>
          </div>
        </div>
      </template>
      <template v-if="activePackageBinding">
        <el-descriptions :column="4" border>
          <el-descriptions-item label="套餐名称">{{ activePackageBinding.packageName }}</el-descriptions-item>
          <el-descriptions-item label="服务周期">{{ activePackageBinding.serviceMonths }} 个月</el-descriptions-item>
          <el-descriptions-item label="关键词组总额度">{{ activePackageBinding.keywordGroupLimit }}</el-descriptions-item>
          <el-descriptions-item label="绑定时间">{{ formatDateTimeSeconds(activePackageBinding.boundAt) }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ packageStatusLabel(activePackageBinding.status) }}</el-descriptions-item>
        </el-descriptions>
        <div v-loading="keywordGroupQuotaLoading" class="quota-panel quota-panel--keyword">
          <div class="quota-panel__header">
            <div>
              <span>关键词组额度</span>
              <small>统计客户下所有已激活项目分配的问题额度</small>
            </div>
            <strong>{{ keywordGroupQuotaText }}</strong>
          </div>
          <el-progress
            class="keyword-total-progress"
            :percentage="keywordGroupQuotaPercentage"
            :status="keywordGroupQuotaStatus"
            :color="keywordGroupQuotaColor"
            :stroke-width="9"
            :show-text="false"
          />
          <div class="quota-panel__meta">
            <span v-if="isKeywordGroupOverQuota">超出额度 {{ keywordGroupOverflow }}</span>
            <span v-else>剩余额度 {{ keywordGroupQuota?.remainingCount ?? 0 }}</span>
          </div>
          <div class="keyword-tier-list">
            <div
              v-for="item in keywordGroupTierItems"
              :key="item.tier"
              class="keyword-tier-row"
              :class="`keyword-tier-row--${item.tier.toLowerCase()}`"
            >
              <div class="keyword-tier-row__main">
                <div>
                  <span>{{ item.label }}</span>
                  <strong>{{ item.used }} / {{ item.limit }}</strong>
                </div>
                <em>{{ item.percentage }}%</em>
              </div>
              <el-progress
                class="keyword-tier-progress"
                :percentage="item.percentage"
                :status="item.status"
                :color="item.color"
                :show-text="false"
                :stroke-width="7"
              />
              <div class="keyword-tier-row__meta">
                <span v-if="item.overflow > 0">超出 {{ item.overflow }}</span>
                <span v-else>剩余 {{ item.remaining }}</span>
              </div>
            </div>
          </div>
        </div>
        <div v-loading="distributionQuotaLoading" class="quota-panel">
          <div class="quota-panel__header">
            <span>分发额度</span>
            <span>{{ distributionQuotaSummary }}</span>
          </div>
          <el-alert
            v-if="distributionQuota?.hasLimitMismatch"
            class="mb-3"
            type="warning"
            show-icon
            :closable="false"
            title="额度配置与当前周期扣减上限不一致，实际分发仍以当前周期 usage 上限为准。"
          />
          <el-table
            :data="distributionQuotaItems"
            border
            :row-class-name="distributionQuotaRowClassName"
          >
            <el-table-column label="渠道" min-width="150">
              <template #default="scope">
                <div class="quota-channel-cell">
                  <span>{{ scope.row.channelName || channelLabel(scope.row.channelCode) }}</span>
                  <el-tag v-if="!scope.row.enabled" size="small" type="info">未开通</el-tag>
                  <el-tag v-else-if="scope.row.status === 'exceeded'" size="small" type="danger">超额</el-tag>
                  <el-tag v-else-if="scope.row.status === 'warning'" size="small" type="warning">预警</el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="周期" width="110">
              <template #default="scope">{{ scope.row.enabled ? periodLabel(scope.row.periodType) : '-' }}</template>
            </el-table-column>
            <el-table-column label="已用 / 额度" min-width="180">
              <template #default="scope">
                <span v-if="!scope.row.enabled">未开通</span>
                <div v-else class="quota-used-cell">
                  <span>
                    {{ scope.row.usedCount }} / {{ scope.row.quotaLimit }}
                    <el-tooltip
                      v-if="scope.row.limitMismatch"
                      effect="dark"
                      :content="limitMismatchText(scope.row)"
                      placement="top"
                    >
                      <sup class="quota-limit-mark">*</sup>
                    </el-tooltip>
                  </span>
                  <el-progress
                    :percentage="distributionQuotaPercentage(scope.row)"
                    :status="distributionQuotaProgressStatus(scope.row)"
                    :show-text="false"
                    :stroke-width="8"
                  />
                </div>
              </template>
            </el-table-column>
            <el-table-column label="剩余额度" width="120">
              <template #default="scope">{{ distributionQuotaRemainingText(scope.row) }}</template>
            </el-table-column>
            <el-table-column label="下次重置" width="150">
              <template #default="scope">{{ nextResetText(scope.row) }}</template>
            </el-table-column>
          </el-table>
        </div>
      </template>
      <el-empty v-else description="当前客户未绑定套餐" />
      <el-table v-if="packageBindingHistory.length" class="mt-4" :data="packageBindingHistory" border>
        <el-table-column prop="packageName" label="历史套餐" min-width="180" />
        <el-table-column label="状态" width="110">
          <template #default="scope">{{ packageStatusLabel(scope.row.status) }}</template>
        </el-table-column>
        <el-table-column label="绑定时间" width="180">
          <template #default="scope">{{ formatDateTimeSeconds(scope.row.boundAt) }}</template>
        </el-table-column>
        <el-table-column label="解绑时间" width="180">
          <template #default="scope">{{ formatDateTimeSeconds(scope.row.unboundAt) }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card v-loading="brandLoading" class="admin-rich-card">
      <template #header>
        <div class="flex items-center justify-between">
          <span>品牌列表</span>
          <el-button v-if="canCreateBrand" type="primary" @click="openBrandCreate">新增品牌</el-button>
        </div>
      </template>
      <DataState :loading="brandLoading" :empty="!brandLoading && brands.length === 0" empty-text="暂无品牌数据">
        <el-table :data="brands" border>
          <el-table-column prop="brandName" label="品牌名称" min-width="180" />
          <el-table-column label="行业" min-width="140">
            <template #default="scope">{{ dictStore.label('industry_tag', scope.row.industry) || scope.row.industry || '-' }}</template>
          </el-table-column>
          <el-table-column prop="mainBusiness" label="主营业务" min-width="180" />
          <el-table-column prop="serviceArea" label="地区" min-width="220">
            <template #default="scope">{{ brandRegion(scope.row) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="scope">{{ dictStore.label('brand_status', scope.row.status) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="scope">
              <div class="flex flex-col gap-1">
                <div class="flex items-center justify-center gap-2">
                  <el-button v-if="canUpdateBrand" link type="primary" @click="openBrandEdit(scope.row)">编辑</el-button>
                  <el-button v-if="canDeleteBrand" link type="danger" @click="removeBrand(scope.row)">删除</el-button>
                  <el-button link type="primary" @click="goBrandDetail(scope.row.id)">详情</el-button>
                </div>
                <div class="flex justify-center">
                  <el-button v-if="canCreateProject" link type="primary" @click="goCreateProject(scope.row.id)">基于该品牌建项目</el-button>
                </div>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </DataState>
    </el-card>

    <el-dialog v-model="editVisible" title="编辑客户" width="820px" class="admin-editor-dialog">
      <el-form ref="companyFormRef" class="admin-dialog-form" :model="companyForm" :rules="companyRules" label-width="100px">
        <el-form-item label="客户名称" required><el-input v-model="companyForm.companyName" /></el-form-item>
        <el-form-item class="is-full" label="行业" prop="industryTags">
          <el-select
            v-model="companyForm.industryTags"
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
        <el-form-item label="地区"><RegionCascader v-model="companyForm.regionCodes" /></el-form-item>
        <el-form-item label="归属" required>
          <el-select v-model="companyForm.ownerType" style="width: 100%">
            <el-option
              v-for="item in dictStore.options('owner_type')"
              :key="item.dictKey"
              :label="item.dictValue"
              :value="item.dictKey"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="所属合伙人">
          <el-select v-model="companyForm.partnerId" clearable filterable style="width: 100%">
            <el-option v-for="p in partnerOptions" :key="p.id" :label="p.partnerName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="canSelectSalesOwner" label="销售负责人">
          <el-select v-model="companyForm.salesOwnerId" clearable filterable placeholder="请选择销售人员" style="width: 100%">
            <el-option
              v-for="item in salesOwnerOptions"
              :key="item.id"
              :label="item.displayName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="来源"><el-input v-model="companyForm.referralSource" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="companyForm.status" style="width: 100%">
            <el-option
              v-for="item in dictStore.options('company_status')"
              :key="item.dictKey"
              :label="item.dictValue"
              :value="item.dictKey"
            />
          </el-select>
        </el-form-item>
        <el-form-item class="is-full" label="备注"><el-input v-model="companyForm.remark" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCompany">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="brandVisible" :title="brandMode === 'create' ? '新增品牌' : '编辑品牌'" width="860px" class="admin-editor-dialog">
      <el-form ref="brandFormRef" class="admin-dialog-form" :model="brandForm" :rules="brandRules" label-width="120px">
        <el-form-item label="品牌名称" required><el-input v-model="brandForm.brandName" /></el-form-item>
        <el-form-item label="品牌行业" prop="industry" required>
          <el-select v-model="brandForm.industry" filterable style="width: 100%">
            <el-option
              v-for="tag in availableBrandIndustries"
              :key="tag"
              :label="dictStore.label('industry_tag', tag) || tag"
              :value="tag"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="主营业务"><el-input v-model="brandForm.mainBusiness" /></el-form-item>
        <el-form-item label="地区"><RegionCascader v-model="brandForm.regionCodes" /></el-form-item>
        <el-form-item label="官网"><el-input v-model="brandForm.website" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="brandForm.phone" /></el-form-item>
        <el-form-item label="微信"><el-input v-model="brandForm.wechat" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="brandForm.status" style="width: 100%">
            <el-option
              v-for="item in dictStore.options('brand_status')"
              :key="item.dictKey"
              :label="item.dictValue"
              :value="item.dictKey"
            />
          </el-select>
        </el-form-item>
        <el-form-item class="is-full" label="品牌描述"><el-input v-model="brandForm.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item class="is-full" label="品牌标准表述"><el-input v-model="brandForm.standardBrandStatement" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="brandVisible = false">取消</el-button>
        <el-button type="primary" :loading="brandSaving" @click="submitBrand">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="packageBindVisible" title="绑定客户套餐" width="560px" class="admin-editor-dialog">
      <el-form class="admin-dialog-form" label-width="100px">
        <el-form-item class="is-full" label="套餐" required>
          <el-select
            v-model="packageBindForm.packagePlanId"
            filterable
            placeholder="请选择已启用套餐"
            style="width: 100%"
          >
            <el-option
              v-for="item in packagePlanOptions"
              :key="item.id"
              :label="`${item.packageName} / ${item.keywordGroupLimit} 关键词`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="packageBindVisible = false">取消</el-button>
        <el-button type="primary" :loading="packageSubmitting" @click="submitPackageBind">确认绑定</el-button>
      </template>
    </el-dialog>

  </div>
</template>
<script setup lang="ts">
import { computed, reactive, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import dayjs from 'dayjs'
import { useUserStore } from '@/stores/user'
import { useDictStore } from '@/stores/dict'
import {
  createBrand,
  bindCompanyPackage,
  deleteBrand,
  deleteCompany,
  getActiveCompanyPackageBinding,
  getBrandList,
  getCompanyDetail,
  getCompanyDistributionQuotas,
  getCompanyPackageBindings,
  getCompanyKeywordGroupQuota,
  getSalesOwnerOptions,
  unbindCompanyPackage,
  updateBrand,
  updateCompany,
  type SalesOwnerOption,
} from '@/api/customer'
import { getEnabledPackagePlans } from '@/api/packagePlan'
import { getPartnerList, type PartnerItem } from '@/api/partner'
import type { Brand, Company, CompanyDistributionQuota, CompanyDistributionQuotaItem, CompanyPackageBinding, CompanyKeywordGroupQuota, PackagePlan } from '@/types'
import DataState from '@/components/ui/DataState.vue'
import RegionCascader from '@/components/ui/RegionCascader.vue'
import { regionCodesFromPayload, regionDisplayFromPayload, regionPayloadFromCodes } from '@/constants/region'
import { errorMessage } from '@/utils/error'
import { formatDateTimeSeconds } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const dictStore = useDictStore()
const canUpdateCompany = computed(() => userStore.hasPermission('company.update'))
const canDeleteCompany = computed(() => userStore.hasPermission('company.delete'))
const canCreateBrand = computed(() => userStore.hasPermission('brand.create'))
const canUpdateBrand = computed(() => userStore.hasPermission('brand.update'))
const canDeleteBrand = computed(() => userStore.hasPermission('brand.delete'))
const canCreateProject = computed(() => userStore.hasPermission('project.create'))
const canManagePackageBinding = computed(() => userStore.hasPermission('user.manage'))
const canSelectSalesOwner = computed(() => userStore.role !== 'sales' && canUpdateCompany.value)
const companyId = Number(route.params.id)
const hasValidId = Number.isFinite(companyId) && companyId > 0

const loading = ref(false)
const brandLoading = ref(false)
const packageLoading = ref(false)
const keywordGroupQuotaLoading = ref(false)
const distributionQuotaLoading = ref(false)
const saving = ref(false)
const brandSaving = ref(false)
const packageSubmitting = ref(false)

const company = ref<Company | null>(null)
const brands = ref<Brand[]>([])
const partnerOptions = ref<PartnerItem[]>([])
const salesOwnerOptions = ref<SalesOwnerOption[]>([])
const activePackageBinding = ref<CompanyPackageBinding | null>(null)
const keywordGroupQuota = ref<CompanyKeywordGroupQuota | null>(null)
const distributionQuota = ref<CompanyDistributionQuota | null>(null)
const packageBindingHistory = ref<CompanyPackageBinding[]>([])
const packagePlanOptions = ref<PackagePlan[]>([])

const companyFormRef = ref<FormInstance>()
const brandFormRef = ref<FormInstance>()

const editVisible = ref(false)
const packageBindVisible = ref(false)
const packageBindForm = reactive({
  packagePlanId: null as number | null,
})
const companyForm = reactive({
  companyName: '',
  industryTags: [] as string[],
  regionCodes: [] as string[],
  ownerType: 'direct',
  partnerId: null as number | null,
  salesOwnerId: null as number | null,
  referralSource: '',
  status: 'potential',
  remark: '',
})
const companyRules: FormRules = {
  companyName: [{ required: true, message: '请输入客户名称', trigger: 'blur' }],
  industryTags: [{ required: true, message: '请选择至少一个行业', trigger: 'change' }],
  ownerType: [{ required: true, message: '璇烽€夋嫨褰掑睘绫诲瀷', trigger: 'change' }],
}

const brandVisible = ref(false)
const brandMode = ref<'create' | 'edit'>('create')
const brandEditingId = ref<number | null>(null)
const brandForm = reactive({
  brandName: '',
  brandSlug: '',
  industry: '',
  mainBusiness: '',
  regionCodes: [] as string[],
  website: '',
  phone: '',
  wechat: '',
  status: 'active',
  description: '',
  standardBrandStatement: '',
})
const brandRules: FormRules = {
  brandName: [{ required: true, message: '请输入品牌名称', trigger: 'blur' }],
  industry: [{ required: true, message: '请选择品牌行业', trigger: 'change' }],
}

function packageStatusLabel(value?: string | null) {
  const mapping: Record<string, string> = { active: '生效中', inactive: '已解绑' }
  return value ? mapping[value] || value : '-'
}

function channelLabel(value?: string | null) {
  const mapping: Record<string, string> = {
    official_site: '官网',
    industry_site: '行业资讯站',
    self_media: '自媒体平台',
    authority_media: '权重媒体平台',
  }
  return value ? mapping[value] || value : '-'
}

function periodLabel(value?: string | null) {
  const mapping: Record<string, string> = { day: '日', week: '周', month: '月', total: '总额度' }
  return value ? mapping[value] || value : '-'
}

const distributionQuotaItems = computed(() => distributionQuota.value?.items || [])
const distributionQuotaSummary = computed(() => {
  const opened = distributionQuotaItems.value.filter((item) => item.enabled).length
  return `${opened}/4 个渠道已开通`
})
const hasActiveKeywordGroupQuota = computed(() => !!keywordGroupQuota.value?.activeBinding)
const isKeywordGroupOverQuota = computed(() => {
  const quota = keywordGroupQuota.value
  return !!quota?.activeBinding && quota.usedCount > quota.quotaLimit
})
const keywordGroupOverflow = computed(() => {
  const quota = keywordGroupQuota.value
  if (!quota?.activeBinding) return 0
  return Math.max(quota.usedCount - quota.quotaLimit, 0)
})
const keywordGroupQuotaText = computed(() => {
  const quota = keywordGroupQuota.value
  if (!quota?.activeBinding) {
    return '未绑定套餐'
  }
  return `${quota.usedCount}/${quota.quotaLimit}`
})
const keywordGroupQuotaPercentage = computed(() => {
  const quota = keywordGroupQuota.value
  if (!quota?.activeBinding) {
    return 0
  }
  if (quota.quotaLimit <= 0) {
    return quota.usedCount > 0 ? 100 : 0
  }
  return Math.min(100, Math.round(quota.usedCount * 100 / quota.quotaLimit))
})
const keywordGroupQuotaStatus = computed(() => {
  if (!hasActiveKeywordGroupQuota.value) {
    return undefined
  }
  if (isKeywordGroupOverQuota.value) {
    return 'exception'
  }
  if (keywordGroupQuotaPercentage.value >= 90) {
    return 'warning'
  }
  return undefined
})
const keywordGroupQuotaColor = computed(() => {
  if (isKeywordGroupOverQuota.value) return '#ef4444'
  if (keywordGroupQuotaPercentage.value >= 90) return '#f59e0b'
  return '#2563eb'
})
const keywordGroupTierItems = computed(() => {
  const quota = keywordGroupQuota.value
  const tiers = [
    {
      tier: 'A',
      label: 'A档问题',
      limit: quota?.quotaLimitA ?? 0,
      used: quota?.usedCountA ?? 0,
      remaining: quota?.remainingCountA ?? 0,
      color: '#2563eb',
    },
    {
      tier: 'B',
      label: 'B档问题',
      limit: quota?.quotaLimitB ?? 0,
      used: quota?.usedCountB ?? 0,
      remaining: quota?.remainingCountB ?? 0,
      color: '#10b981',
    },
    {
      tier: 'C',
      label: 'C档问题',
      limit: quota?.quotaLimitC ?? 0,
      used: quota?.usedCountC ?? 0,
      remaining: quota?.remainingCountC ?? 0,
      color: '#8b5cf6',
    },
  ]
  return tiers.map((item) => {
    const overflow = Math.max(item.used - item.limit, 0)
    const percentage = item.limit <= 0 ? (item.used > 0 ? 100 : 0) : Math.min(100, Math.round(item.used * 100 / item.limit))
    const status = overflow > 0 ? 'exception' : (percentage >= 90 ? 'warning' : undefined)
    return { ...item, overflow, percentage, status }
  })
})

function distributionQuotaPercentage(row: CompanyDistributionQuotaItem) {
  if (!row.enabled) return 0
  if (row.quotaLimit <= 0) {
    return row.usedCount > 0 ? 100 : 0
  }
  return Math.min(100, Math.round(row.usedCount * 100 / row.quotaLimit))
}

function distributionQuotaProgressStatus(row: CompanyDistributionQuotaItem) {
  if (!row.enabled) return undefined
  if (row.status === 'exceeded') return 'exception'
  if (row.status === 'warning') return 'warning'
  return undefined
}

function distributionQuotaRemainingText(row: CompanyDistributionQuotaItem) {
  if (!row.enabled) return '-'
  if (row.status === 'exceeded') {
    return `超出 ${Math.max(row.usedCount - row.quotaLimit, 0)}`
  }
  return String(row.remainingCount)
}

function nextResetText(row: CompanyDistributionQuotaItem) {
  if (!row.enabled) return '-'
  if (row.periodType === 'total') return '不重置'
  if (!row.nextResetAt) return '-'
  const date = dayjs(row.nextResetAt)
  if (!date.isValid()) return '-'
  const weekdays = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  return `${date.format('M月D日')} ${weekdays[date.day()]}`
}

function limitMismatchText(row: CompanyDistributionQuotaItem) {
  return `套餐配置已变更为 ${row.quotaLimit}，本周期实际上限仍为 ${row.usageQuotaLimit ?? '-'}，下周期生效`
}

function distributionQuotaRowClassName({ row }: { row: CompanyDistributionQuotaItem }) {
  if (!row.enabled) return 'quota-row-disabled'
  if (row.status === 'exceeded') return 'quota-row-exceeded'
  return ''
}

function companyRegion(value?: Company | null) {
  if (!value) return '-'
  return regionDisplayFromPayload(value) || value.city || '-'
}

function companyStatusClass(status?: string | null) {
  const value = String(status || '')
  if (['active', 'signed', 'deal', 'converted'].includes(value)) return 'is-success'
  if (['disabled', 'lost', 'deleted'].includes(value)) return 'is-muted'
  if (['risk', 'blocked'].includes(value)) return 'is-danger'
  return 'is-warning'
}

function customerHeroClass(status?: string | null) {
  const value = String(status || '')
  if (['active', 'signed', 'deal', 'converted'].includes(value)) return 'is-signed'
  if (['disabled', 'lost', 'deleted'].includes(value)) return 'is-disabled'
  return 'is-potential'
}

function brandRegion(value: Brand) {
  return regionDisplayFromPayload(value) || value.serviceArea || '-'
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

function fillCompanyForm(data: Company) {
  companyForm.companyName = data.companyName
  companyForm.industryTags = parseIndustryTags(data.industryTags)
  companyForm.regionCodes = regionCodesFromPayload(data)
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
  brandForm.industry = availableBrandIndustries.value[0] || ''
  brandForm.mainBusiness = ''
  brandForm.regionCodes = []
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
    await ElMessageBox.confirm(`确认删除客户「${company.value.companyName}」？该操作不可撤销。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '确认',
      cancelButtonText: '取消',
    })
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
  if (!valid) return
  if ((companyForm.ownerType === 'partner' || companyForm.ownerType === 'joint') && !companyForm.partnerId) {
    ElMessage.warning('褰掑睘涓衡€滃悎浼欎汉/鑱斿悎鈥濈殑瀹㈡埛闇€濉啓鍚堜紮浜篒D')
    return
  }
  saving.value = true
  try {
    const region = regionPayloadFromCodes(companyForm.regionCodes)
    await updateCompany(companyId, {
      companyName: companyForm.companyName,
      industryTags: normalizeIndustryTags(companyForm.industryTags),
      city: region.displayName,
      provinceCode: region.provinceCode,
      provinceName: region.provinceName,
      cityCode: region.cityCode,
      cityName: region.cityName,
      districtCode: region.districtCode,
      districtName: region.districtName,
      ownerType: companyForm.ownerType,
      partnerId: companyForm.partnerId || undefined,
      salesOwnerId: canSelectSalesOwner.value ? companyForm.salesOwnerId || undefined : undefined,
      referralSource: companyForm.referralSource || undefined,
      status: companyForm.status,
      remark: companyForm.remark || undefined,
    })
    ElMessage.success('客户信息已更新')
    editVisible.value = false
    await Promise.all([loadCompany(), loadBrands()])
  } finally {
    saving.value = false
  }
}

function openBrandCreate() {
  router.push({ path: '/admin/brands/create', query: { companyId: String(companyId) } })
}

async function loadPackageBinding() {
  packageLoading.value = true
  try {
    const activeRes = await getActiveCompanyPackageBinding(companyId)
    activePackageBinding.value = activeRes.data.data
    if (!canManagePackageBinding.value) {
      packageBindingHistory.value = []
      return
    }
    try {
      const historyRes = await getCompanyPackageBindings(companyId)
      packageBindingHistory.value = (historyRes.data.data || []).filter((item) => item.status !== 'active')
    } catch (err) {
      ElMessage.error(errorMessage(err, '加载客户套餐历史失败'))
    }
  } catch (err) {
    ElMessage.error(errorMessage(err, '加载客户套餐信息失败'))
  } finally {
    packageLoading.value = false
  }
}

async function loadKeywordGroupQuota() {
  keywordGroupQuotaLoading.value = true
  try {
    const { data } = await getCompanyKeywordGroupQuota(companyId)
    keywordGroupQuota.value = data.data
  } catch (err) {
    ElMessage.error(errorMessage(err, '加载关键词组额度失败'))
  } finally {
    keywordGroupQuotaLoading.value = false
  }
}

async function loadDistributionQuotas() {
  distributionQuotaLoading.value = true
  try {
    const { data } = await getCompanyDistributionQuotas(companyId)
    distributionQuota.value = data.data
  } catch (err) {
    ElMessage.error(errorMessage(err, '加载分发额度失败'))
  } finally {
    distributionQuotaLoading.value = false
  }
}

async function loadPackagePlanOptions() {
  try {
    const { data } = await getEnabledPackagePlans()
    packagePlanOptions.value = data.data || []
  } catch (err) {
    packagePlanOptions.value = []
    ElMessage.error(errorMessage(err, '加载套餐选项失败'))
  }
}

async function openPackageBind() {
  packageBindForm.packagePlanId = null
  await loadPackagePlanOptions()
  packageBindVisible.value = true
}

async function submitPackageBind() {
  if (!packageBindForm.packagePlanId) {
    ElMessage.warning('请选择套餐')
    return
  }
  packageSubmitting.value = true
  try {
    await bindCompanyPackage(companyId, packageBindForm.packagePlanId)
    ElMessage.success('客户套餐已绑定')
    packageBindVisible.value = false
    await Promise.all([loadPackageBinding(), loadKeywordGroupQuota(), loadDistributionQuotas()])
  } catch (err) {
    ElMessage.error(errorMessage(err, '绑定套餐失败'))
  } finally {
    packageSubmitting.value = false
  }
}

async function confirmUnbindPackage() {
  if (!activePackageBinding.value) return
  try {
    await ElMessageBox.confirm(`确认解绑套餐「${activePackageBinding.value.packageName}」？解绑后该客户下项目将不能激活或分发文章。`, '解绑确认', {
      type: 'warning',
      confirmButtonText: '确认解绑',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  packageSubmitting.value = true
  try {
    await unbindCompanyPackage(companyId)
    ElMessage.success('客户套餐已解绑')
    await Promise.all([loadPackageBinding(), loadKeywordGroupQuota(), loadDistributionQuotas()])
  } catch (err) {
    ElMessage.error(errorMessage(err, '解绑套餐失败'))
  } finally {
    packageSubmitting.value = false
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

function openBrandEdit(row: Brand) {
  brandMode.value = 'edit'
  brandEditingId.value = row.id
  brandForm.brandName = row.brandName
  brandForm.brandSlug = row.brandSlug
  brandForm.industry = row.industry || availableBrandIndustries.value[0] || ''
  brandForm.mainBusiness = row.mainBusiness || ''
  brandForm.regionCodes = regionCodesFromPayload(row)
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
  if (!valid) return
  brandSaving.value = true
  try {
    const region = regionPayloadFromCodes(brandForm.regionCodes)
    const payload: Record<string, any> = {
      companyId,
      brandName: brandForm.brandName,
      industry: brandForm.industry,
      mainBusiness: brandForm.mainBusiness || undefined,
      serviceArea: region.displayName,
      provinceCode: region.provinceCode,
      provinceName: region.provinceName,
      cityCode: region.cityCode,
      cityName: region.cityName,
      districtCode: region.districtCode,
      districtName: region.districtName,
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
      payload.brandSlug = brandForm.brandSlug
      await updateBrand(brandEditingId.value, payload as any)
    }
    ElMessage.success('鍝佺墝淇濆瓨鎴愬姛')
    brandVisible.value = false
    resetBrandForm()
    await loadBrands()
  } finally {
    brandSaving.value = false
  }
}

async function removeBrand(row: Brand) {
  try {
    await ElMessageBox.confirm(`确认删除品牌「${row.brandName}」？该操作不可撤销。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
    })
    await deleteBrand(row.id)
    ElMessage.success('删除成功')
    await loadBrands()
  } catch {
    // canceled
  }
}

function goCreateProject(brandId: number) {
  router.push({
    path: '/admin/projects',
    query: {
      brandId: String(brandId),
      companyId: String(companyId),
      source: 'customer_brand',
    },
  })
}

function goBrandDetail(brandId: number) {
  router.push(`/admin/brands/${brandId}`)
}

const availableBrandIndustries = computed(() => companyForm.industryTags || [])

const companyIndustryText = computed(() => {
  const tags = companyForm.industryTags || []
  if (!tags.length) return '-'
  return tags.map((tag) => dictStore.label('industry_tag', tag) || tag).join(' / ')
})
const customerBasicInfoItems = computed(() => [
  { label: '客户名称', value: company.value?.companyName || '-' },
  { label: '行业', value: companyIndustryText.value },
  { label: '地区', value: companyRegion(company.value) },
  { label: '归属', value: dictStore.label('owner_type', company.value?.ownerType) || '-' },
  { label: '合伙人', value: (company.value as any)?.partnerName || '-' },
  { label: '状态', value: dictStore.label('company_status', (company.value as any)?.status) || '-' },
  { label: '创建时间', value: formatDateTimeSeconds(company.value?.createdAt) },
])

onMounted(async () => {
  if (!hasValidId) {
    ElMessage.error('鏃犳晥鐨勫鎴稩D')
    return
  }
  await dictStore.ensureLoaded()
  await Promise.all([loadPartners(), loadSalesOwners()])
  await loadCompany()
  await Promise.all([loadBrands(), loadPackageBinding(), loadKeywordGroupQuota(), loadDistributionQuotas()])
})
</script>

<style scoped>
.customer-detail-hero {
  border-color: rgba(147, 197, 253, 0.72);
  background:
    radial-gradient(circle at 88% 0%, rgba(34, 197, 94, 0.08), transparent 28%),
    linear-gradient(135deg, #ffffff 0%, #f8fbff 45%, #effdf7 100%);
}

.customer-detail-hero::after {
  width: 220px;
  opacity: 0.42;
}

.customer-detail-hero.is-signed {
  border-left: 4px solid #10b981;
}

.customer-detail-hero.is-potential {
  border-left: 4px solid #f59e0b;
}

.customer-detail-hero.is-disabled {
  border-left: 4px solid #94a3b8;
}

.customer-hero-kpi {
  position: relative;
  overflow: hidden;
  border-color: rgba(191, 219, 254, 0.7);
  background: rgba(255, 255, 255, 0.86);
}

.customer-hero-kpi::before {
  content: "";
  position: absolute;
  top: 0;
  left: 14px;
  right: 14px;
  height: 3px;
  border-radius: 999px;
}

.customer-hero-kpi--brand::before {
  background: linear-gradient(90deg, #60a5fa, rgba(96, 165, 250, 0.08));
}

.customer-hero-kpi--brand strong {
  color: #1d4ed8;
}

.customer-hero-kpi--package::before {
  background: linear-gradient(90deg, #34d399, rgba(52, 211, 153, 0.08));
}

.customer-hero-kpi--package strong {
  color: #047857;
}

.customer-hero-kpi--quota::before {
  background: linear-gradient(90deg, #fbbf24, rgba(251, 191, 36, 0.08));
}

.customer-hero-kpi--quota strong {
  color: #b45309;
}

.quota-panel {
  margin-top: 16px;
  padding: 14px 16px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
}

.quota-panel--keyword {
  padding: 18px;
  border-color: #dbeafe;
  border-radius: 12px;
  background:
    radial-gradient(circle at top right, rgba(37, 99, 235, 0.08), transparent 28%),
    linear-gradient(135deg, #ffffff 0%, #f8fbff 58%, #f7f7ff 100%);
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.05);
}

.quota-panel__header,
.quota-panel__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.quota-panel__header {
  margin-bottom: 10px;
  font-weight: 600;
}

.quota-panel__header > div {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.quota-panel__header small {
  color: #64748b;
  font-size: 12px;
  font-weight: 400;
}

.quota-panel__header strong {
  padding: 5px 12px;
  border: 1px solid rgba(37, 99, 235, 0.16);
  border-radius: 999px;
  background: rgba(37, 99, 235, 0.08);
  color: #1d4ed8;
  font-size: 18px;
  line-height: 1.2;
}

.quota-panel__meta {
  justify-content: flex-start;
  margin-top: 8px;
  color: #606266;
  font-size: 12px;
}

.keyword-total-progress {
  width: min(560px, 100%);
}

.keyword-tier-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.keyword-tier-row {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  padding: 14px;
  border: 1px solid rgba(148, 163, 184, 0.22);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.82);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.72);
}

.keyword-tier-row__main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  font-size: 13px;
}

.keyword-tier-row__main > div {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
}

.keyword-tier-row__main span {
  color: #475569;
}

.keyword-tier-row__main strong {
  color: #0f172a;
  font-size: 18px;
  line-height: 1.1;
  font-weight: 600;
}

.keyword-tier-row__main em {
  flex: 0 0 auto;
  color: #64748b;
  font-size: 12px;
  font-style: normal;
}

.keyword-tier-progress {
  width: 100%;
}

.keyword-tier-row__meta {
  font-size: 12px;
  color: #606266;
  text-align: left;
}

.keyword-tier-row--a {
  background: linear-gradient(135deg, rgba(37, 99, 235, 0.08), rgba(255, 255, 255, 0.92));
}

.keyword-tier-row--b {
  background: linear-gradient(135deg, rgba(16, 185, 129, 0.09), rgba(255, 255, 255, 0.92));
}

.keyword-tier-row--c {
  background: linear-gradient(135deg, rgba(139, 92, 246, 0.09), rgba(255, 255, 255, 0.92));
}

.quota-channel-cell,
.quota-used-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.quota-limit-mark {
  color: #e6a23c;
  font-weight: 700;
}

:deep(.quota-row-disabled > td.el-table__cell) {
  color: #909399;
  background: #fafafa;
}

:deep(.quota-row-exceeded > td.el-table__cell) {
  background: #fef0f0;
}

@media (max-width: 1100px) {
  .keyword-tier-list {
    grid-template-columns: 1fr;
  }
}
</style>

