<template>
  <div class="admin-page">
    <el-page-header content="品牌详情" @back="$router.back()" />

    <section v-if="brand" class="admin-object-hero">
      <div class="admin-object-hero-main">
        <div>
          <h1 class="admin-object-title">{{ brand.brandName }}</h1>
          <div class="admin-object-meta">
            {{ companyName || '-' }} · {{ industryLabel(brand.industry) }}
          </div>
        </div>
        <span class="admin-status-tag" :class="brand?.status === 'active' ? 'is-success' : 'is-muted'">
          {{ dictStore.label('brand_status', brand?.status) || '-' }}
        </span>
      </div>
      <div class="admin-object-kpis">
        <div class="admin-object-kpi">
          <span>品牌行业</span>
          <strong>{{ industryLabel(brand.industry) }}</strong>
        </div>
        <div class="admin-object-kpi">
          <span>自媒体账号</span>
          <strong>{{ semiAutoSelfMediaAccounts.length }}</strong>
        </div>
      </div>
    </section>

    <el-card v-loading="loading" class="admin-rich-card brand-detail-card">
      <template #header>
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2">
            <span>基础信息</span>
            <el-tag>{{ dictStore.label('brand_status', brand?.status) }}</el-tag>
          </div>
          <div class="space-x-2">
            <el-button v-if="brand?.companyId" link @click="goCompanyDetail">查看客户</el-button>
            <el-button v-if="canCreateProject" type="primary" link @click="goCreateProject">基于该品牌建项目</el-button>
            <el-button v-if="canUpdateBrand" type="primary" link @click="openEdit">编辑</el-button>
            <el-button v-if="canDeleteBrand" type="danger" link @click="removeCurrentBrand">删除品牌</el-button>
          </div>
        </div>
      </template>

      <div class="brand-detail-sections">
        <section class="brand-detail-section">
          <div class="brand-section-bar"><span />基础资料<i /></div>
          <div class="brand-info-grid">
            <div v-for="item in brandCoreInfoItems" :key="item.label" class="brand-info-item">
              <span class="brand-info-label">{{ item.label }}</span>
              <strong class="brand-info-value">{{ item.value }}</strong>
            </div>
          </div>
        </section>
        <section class="brand-detail-section">
          <div class="brand-section-bar"><span />联系方式与阵地<i /></div>
          <div class="brand-info-grid">
            <div v-for="item in brandContactInfoItems" :key="item.label" class="brand-info-item">
              <span class="brand-info-label">{{ item.label }}</span>
              <strong class="brand-info-value">{{ item.value }}</strong>
            </div>
          </div>
        </section>
        <section class="brand-detail-section">
          <div class="brand-section-bar"><span />业务介绍与内容约束<i /></div>
          <div class="brand-info-grid">
            <div
              v-for="item in brandTextInfoItems"
              :key="item.label"
              class="brand-info-item is-wide"
            >
              <span class="brand-info-label">{{ item.label }}</span>
              <strong class="brand-info-value">{{ item.value }}</strong>
            </div>
          </div>
        </section>
      </div>
    </el-card>

    <el-card class="admin-table-card">
      <template #header>
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2">
            <span>自媒体账号</span>
            <el-tag type="info">头条 / 知乎 / 小红书</el-tag>
          </div>
          <el-button v-if="canUpdateBrand" type="primary" link @click="openSelfMediaAccountCreate">新增账号</el-button>
        </div>
      </template>
      <el-alert
        class="mb-3"
        type="info"
        show-icon
        :closable="false"
        title="头条、知乎、小红书使用浏览器扩展捕获登录凭证。账号建好后，请在扩展里选择账号并捕获凭证。"
      />
      <el-table v-loading="selfMediaAccountsLoading" :data="semiAutoSelfMediaAccounts" border>
        <el-table-column prop="platform" label="平台" width="110">
          <template #default="{ row }">{{ selfMediaPlatformLabel(row.platform) }}</template>
        </el-table-column>
        <el-table-column prop="accountName" label="账号名称" min-width="180" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'active' ? 'success' : 'info'">
              {{ row.status === 'active' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="凭证" width="130">
          <template #default="{ row }">
            <el-tag size="small" :type="row.cookieCredentialStatus === 'active' ? 'success' : 'warning'">
              {{ row.cookieCredentialStatus === 'active' ? `v${row.cookieCredentialVersion || '-'}` : '未捕获' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="捕获时间" min-width="170">
          <template #default="{ row }">{{ row.cookieCredentialCapturedAt || '-' }}</template>
        </el-table-column>
        <el-table-column v-if="canUpdateBrand" label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openSelfMediaAccountEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card class="admin-rich-card">
      <template #header><span>扩展入口</span></template>
      <div class="flex flex-wrap gap-3">
        <el-button @click="router.push(`/admin/brands/${brandId}/profile`)">品牌画像</el-button>
        <el-button @click="router.push(`/admin/brands/${brandId}/assets`)">品牌资产</el-button>
      </div>
    </el-card>

    <el-dialog v-model="editVisible" title="编辑品牌" width="980px" class="admin-editor-dialog brand-editor-dialog">
      <el-form ref="brandFormRef" class="brand-form" :model="brandForm" :rules="brandRules" label-position="top">
        <div class="brand-section-bar"><span />基础信息<i /></div>
        <div class="brand-form-grid">
          <el-form-item label="品牌名称" prop="brandName" required><el-input v-model="brandForm.brandName" /></el-form-item>
          <el-form-item label="品牌简称"><el-input v-model="brandForm.brandShortName" maxlength="128" show-word-limit /></el-form-item>
          <el-form-item label="品牌行业" prop="industry" required>
            <el-select v-model="brandForm.industry" filterable style="width: 100%">
              <el-option
                v-for="tag in availableBrandIndustries"
                :key="tag"
                :label="industryLabel(tag)"
                :value="tag"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="状态" prop="status" required>
            <el-select v-model="brandForm.status" style="width: 100%">
              <el-option
                v-for="item in dictStore.options('brand_status')"
                :key="item.dictKey"
                :label="item.dictValue"
                :value="item.dictKey"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="主营业务"><el-input v-model="brandForm.mainBusiness" /></el-form-item>
          <el-form-item label="核心产品">
            <el-input v-model="brandForm.coreProducts" maxlength="500" show-word-limit placeholder="多个产品以逗号隔开" />
          </el-form-item>
          <el-form-item label="品牌定位">
            <el-input v-model="brandForm.brandPositioning" maxlength="255" show-word-limit placeholder="如“某某方案服务商/代理商”“本地某某平台”" />
          </el-form-item>
          <el-form-item label="地区"><RegionCascader v-model="brandForm.regionCodes" /></el-form-item>
        </div>

        <div class="brand-section-bar"><span />联系方式与阵地<i /></div>
        <div class="brand-form-grid">
          <el-form-item label="官网"><el-input v-model="brandForm.website" /></el-form-item>
          <el-form-item label="联系电话"><el-input v-model="brandForm.phone" /></el-form-item>
          <el-form-item label="对外公开电话"><el-input v-model="brandForm.publicPhone" /></el-form-item>
          <el-form-item label="微信"><el-input v-model="brandForm.wechat" /></el-form-item>
          <el-form-item class="is-wide" label="对外公开地址"><el-input v-model="brandForm.publicAddress" /></el-form-item>
          <el-form-item label="Agent 官网">
            <el-select
              v-model="brandForm.geoSiteCode"
              clearable
              filterable
              placeholder="选择 Agent 官网，自动带出站点标识"
              style="width: 100%"
              @change="handleAgentSiteChange"
            >
              <el-option
                v-for="site in agentSiteOptions"
                :key="site.siteCode || site.id"
                :label="site.siteName"
                :value="site.siteCode"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="行业资讯站">
            <el-select
              v-model="brandForm.industrySiteCode"
              clearable
              filterable
              placeholder="选择资讯站，自动带出站点标识"
              style="width: 100%"
              @change="handleIndustrySiteChange"
            >
              <el-option
                v-for="site in industrySiteOptions"
                :key="site.siteCode || site.id"
                :label="site.siteName"
                :value="site.siteCode"
              />
            </el-select>
          </el-form-item>
        </div>

        <div class="brand-section-bar"><span />业务介绍与内容约束<i /></div>
        <div class="brand-form-grid">
          <el-form-item class="is-wide" label="业务介绍"><el-input v-model="brandForm.businessIntro" type="textarea" :rows="3" /></el-form-item>
          <el-form-item class="is-wide" label="品牌资质描述">
            <el-input v-model="brandForm.brandQualificationDescription" type="textarea" :rows="3" maxlength="300" show-word-limit :placeholder="qualificationDescriptionPlaceholder" />
            <div class="brand-field-help">仅填写可公开引用、可核验的资质与背书信息。</div>
          </el-form-item>
          <el-form-item class="is-wide" label="品牌案例描述">
            <el-input v-model="brandForm.brandCaseDescription" type="textarea" :rows="3" maxlength="300" show-word-limit :placeholder="caseDescriptionPlaceholder" />
            <div class="brand-field-help">客户名称不可公开时，可使用行业或区域客户描述。</div>
          </el-form-item>
          <el-form-item class="is-wide" label="禁用词"><el-input v-model="brandForm.forbiddenPhrases" type="textarea" :rows="3" /></el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitBrand">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="selfMediaAccountVisible"
      :title="editingSelfMediaAccount ? '编辑自媒体账号' : '新增自媒体账号'"
      width="520px"
      class="admin-editor-dialog"
    >
      <el-form
        ref="selfMediaAccountFormRef"
        :model="selfMediaAccountForm"
        :rules="selfMediaAccountRules"
        label-width="100px"
      >
        <el-form-item label="平台" prop="platform" required>
          <el-select v-model="selfMediaAccountForm.platform" style="width: 100%">
            <el-option label="头条" value="toutiao" />
            <el-option label="知乎" value="zhihu" />
            <el-option label="小红书" value="xiaohongshu" />
          </el-select>
        </el-form-item>
        <el-form-item label="账号名称" prop="accountName" required>
          <el-input v-model="selfMediaAccountForm.accountName" placeholder="运营可识别的账号名称" maxlength="128" />
        </el-form-item>
        <el-form-item label="状态" prop="status" required>
          <el-select v-model="selfMediaAccountForm.status" style="width: 100%">
            <el-option label="启用" value="active" />
            <el-option label="停用" value="disabled" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="selfMediaAccountVisible = false">取消</el-button>
        <el-button type="primary" :loading="selfMediaAccountSaving" @click="submitSelfMediaAccount">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  createSelfMediaAccount,
  getSelfMediaAccountsByBrand,
  updateSelfMediaAccount,
} from '@/api/content'
import {
  getBrandDetail,
  updateBrand,
  deleteBrand,
  getCompanyDetail,
} from '@/api/customer'
import { getPublishSites } from '@/api/publishSite'
import type { Brand, PublishSite, SelfMediaAccount } from '@/types'
import { useUserStore } from '@/stores/user'
import { useDictStore } from '@/stores/dict'
import RegionCascader from '@/components/ui/RegionCascader.vue'
import { regionCodesFromPayload, regionDisplayFromPayload, regionPayloadFromCodes } from '@/constants/region'
import { nullableText } from '@/utils/form'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const dictStore = useDictStore()

const brandId = Number(route.params.id)
const hasValidId = Number.isFinite(brandId) && brandId > 0

const canUpdateBrand = computed(() => userStore.hasPermission('brand.update'))
const canDeleteBrand = computed(() => userStore.hasPermission('brand.delete'))
const canCreateProject = computed(() => userStore.hasPermission('project.create'))

type SemiAutoPlatform = 'toutiao' | 'zhihu' | 'xiaohongshu'
type SemiAutoSelfMediaAccount = SelfMediaAccount & {
  platform: SemiAutoPlatform | string
  cookieCredentialStatus?: string | null
  cookieCredentialVersion?: number | null
  cookieCredentialCapturedAt?: string | null
}

const loading = ref(false)
const saving = ref(false)
const editVisible = ref(false)
const selfMediaAccountsLoading = ref(false)
const selfMediaAccountSaving = ref(false)
const selfMediaAccountVisible = ref(false)
const brand = ref<Brand | null>(null)
const selfMediaAccounts = ref<SemiAutoSelfMediaAccount[]>([])
const publishSites = ref<PublishSite[]>([])
const GEO_SITE_CODE_PATTERN = /^[a-z0-9](?:[a-z0-9_-]{0,62}[a-z0-9])?$/
const editingSelfMediaAccount = ref<SemiAutoSelfMediaAccount | null>(null)
const companyName = ref('')
const companyIndustryTags = ref<string[]>([])
const brandFormRef = ref<FormInstance>()
const selfMediaAccountFormRef = ref<FormInstance>()

const brandForm = reactive({
  brandName: '',
  brandShortName: '',
  brandSlug: '',
  industry: '',
  mainBusiness: '',
  coreProducts: '',
  brandPositioning: '',
  regionCodes: [] as string[],
  website: '',
  phone: '',
  publicPhone: '',
  publicAddress: '',
  wechat: '',
  status: 'active',
  businessIntro: '',
  brandQualificationDescription: '',
  brandCaseDescription: '',
  forbiddenPhrases: '',
  geoSiteCode: '',
  geoSiteStatus: '',
  industrySiteName: '',
  industrySiteCode: '',
})

const selfMediaAccountForm = reactive({
  platform: 'toutiao' as SemiAutoPlatform,
  accountName: '',
  status: 'active' as 'active' | 'disabled',
})

const brandRules: FormRules = {
  brandName: [{ required: true, message: '请输入品牌名称', trigger: 'blur' }],
  industry: [{ required: true, message: '请选择品牌行业', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
}

const qualificationDescriptionPlaceholder = '请填写品牌可公开引用的资质与背书信息，包括认证资质、检测报告、执行标准、专利/软著、荣誉奖项、协会或平台背书、生产/服务能力证明等。请写清楚名称、编号、发证机构、适用范围、有效期等可核验信息。没有真实依据的内容不要填写。'
const caseDescriptionPlaceholder = '请填写可公开引用的品牌案例素材，包括客户类型或客户名称、项目背景、服务内容、项目规模、交付周期、合作结果、复购或长期合作情况等。如客户名称不可公开，请使用“某行业客户/某区域客户”表述，不要编造客户名或效果数据。'

const selfMediaAccountRules: FormRules = {
  platform: [{ required: true, message: '请选择平台', trigger: 'change' }],
  accountName: [{ required: true, message: '请输入账号名称', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
}

const semiAutoSelfMediaAccounts = computed(() =>
  selfMediaAccounts.value.filter((item) => item.platform === 'toutiao' || item.platform === 'zhihu' || item.platform === 'xiaohongshu'),
)

const regionText = computed(() => {
  if (!brand.value) return '-'
  return regionDisplayFromPayload(brand.value) || brand.value.serviceArea || '-'
})

const availableBrandIndustries = computed(() => companyIndustryTags.value)
const agentSiteOptions = computed(() => publishSites.value.filter((site) =>
  isValidGeoSiteCode(site.siteCode)
  && (site.integrationMethod === 'brand_geo_site' || site.siteCode === 'agent_official_site'),
))
const industrySiteOptions = computed(() => publishSites.value.filter((site) =>
  site.integrationMethod !== 'brand_geo_site'
  && site.integrationMethod !== 'forum_playwright'
  && site.integrationMethod !== 'discuz_http'
  && site.siteCode !== 'agent_official_site',
))
const brandCoreInfoItems = computed(() => [
  { label: '品牌名称', value: brand.value?.brandName || '-' },
  { label: '品牌简称', value: brand.value?.brandShortName || '-' },
  { label: '状态', value: dictStore.label('brand_status', brand.value?.status) || '-' },
  { label: '所属客户', value: companyName.value || '-' },
  { label: '品牌行业', value: industryLabel(brand.value?.industry) },
  { label: '主营业务', value: brand.value?.mainBusiness || '-' },
  { label: '核心产品', value: brand.value?.coreProducts || '-' },
  { label: '品牌定位', value: brand.value?.brandPositioning || '-' },
  { label: '所在地区', value: regionText.value },
])

const brandContactInfoItems = computed(() => [
  { label: '官网', value: brand.value?.website || '-' },
  { label: '联系电话', value: brand.value?.phone || '-' },
  { label: '对外公开电话', value: brand.value?.publicPhone || '-' },
  { label: '对外公开地址', value: brand.value?.publicAddress || '-' },
  { label: '微信', value: brand.value?.wechat || '-' },
  { label: 'Agent 官网', value: agentSiteLabel(brand.value?.geoSiteCode) },
  { label: '行业资讯站', value: brand.value?.industrySiteName || '-' },
])

const brandTextInfoItems = computed(() => [
  { label: '业务介绍', value: brand.value?.businessIntro || '-' },
  { label: '品牌资质描述', value: brand.value?.brandQualificationDescription || '-' },
  { label: '品牌案例描述', value: brand.value?.brandCaseDescription || '-' },
  { label: '禁用词', value: brand.value?.forbiddenPhrases || '-' },
])

function industryLabel(value?: string | null) {
  if (!value) return '-'
  return dictStore.label('industry_tag', value) || value
}

function selfMediaPlatformLabel(value?: string | null) {
  if (value === 'toutiao') return '头条'
  if (value === 'zhihu') return '知乎'
  if (value === 'xiaohongshu') return '小红书'
  return value || '-'
}

function agentSiteLabel(code?: string | null) {
  if (!code) return '-'
  return agentSiteOptions.value.find((item) => item.siteCode === code)?.siteName || code
}

function normalizeGeoSiteCode(code?: string | null) {
  const normalized = code?.trim().toLowerCase() || ''
  return GEO_SITE_CODE_PATTERN.test(normalized) ? normalized : ''
}

function isValidGeoSiteCode(code?: string | null) {
  return !!normalizeGeoSiteCode(code)
}

function handleAgentSiteChange(value: string) {
  brandForm.geoSiteCode = normalizeGeoSiteCode(value)
  const site = agentSiteOptions.value.find((item) => item.siteCode === brandForm.geoSiteCode)
  brandForm.geoSiteStatus = site ? 'active' : ''
}

function handleIndustrySiteChange(value: string) {
  const site = industrySiteOptions.value.find((item) => item.siteCode === value)
  brandForm.industrySiteName = site?.siteName || ''
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

function fillForm(data: Brand) {
  brandForm.brandName = data.brandName
  brandForm.brandShortName = data.brandShortName || ''
  brandForm.brandSlug = data.brandSlug
  brandForm.industry = data.industry || availableBrandIndustries.value[0] || ''
  brandForm.mainBusiness = data.mainBusiness || ''
  brandForm.coreProducts = data.coreProducts || ''
  brandForm.brandPositioning = data.brandPositioning || ''
  brandForm.regionCodes = regionCodesFromPayload(data)
  brandForm.website = data.website || ''
  brandForm.phone = data.phone || ''
  brandForm.publicPhone = data.publicPhone || ''
  brandForm.publicAddress = data.publicAddress || ''
  brandForm.wechat = data.wechat || ''
  brandForm.status = data.status || 'active'
  brandForm.businessIntro = data.businessIntro || ''
  brandForm.brandQualificationDescription = data.brandQualificationDescription || ''
  brandForm.brandCaseDescription = data.brandCaseDescription || ''
  brandForm.forbiddenPhrases = Array.isArray(data.forbiddenPhrases)
    ? data.forbiddenPhrases.join('，')
    : (data.forbiddenPhrases || '')
  brandForm.geoSiteCode = data.geoSiteCode || ''
  brandForm.geoSiteStatus = data.geoSiteStatus || ''
  brandForm.industrySiteName = data.industrySiteName || ''
  brandForm.industrySiteCode = data.industrySiteCode || ''
}

async function loadPublishSiteOptions() {
  try {
    const { data } = await getPublishSites({ status: 'active' })
    publishSites.value = data.data || []
  } catch {
    publishSites.value = []
  }
}

async function load() {
  loading.value = true
  try {
    await loadPublishSiteOptions()
    const { data } = await getBrandDetail(brandId)
    brand.value = data.data
    fillForm(data.data)
    if (data.data.companyId) {
      const companyRes = await getCompanyDetail(data.data.companyId)
      companyName.value = companyRes.data.data.companyName || ''
      companyIndustryTags.value = parseIndustryTags((companyRes.data.data as any).industryTags)
      if (!brandForm.industry) {
        brandForm.industry = companyIndustryTags.value[0] || ''
      }
    } else {
      companyName.value = ''
      companyIndustryTags.value = []
    }
    await loadSelfMediaAccounts()
  } catch {
    brand.value = null
    companyName.value = ''
    selfMediaAccounts.value = []
  } finally {
    loading.value = false
  }
}

async function loadSelfMediaAccounts() {
  selfMediaAccountsLoading.value = true
  try {
    const { data } = await getSelfMediaAccountsByBrand(brandId)
    selfMediaAccounts.value = data.data as SemiAutoSelfMediaAccount[]
  } finally {
    selfMediaAccountsLoading.value = false
  }
}

function openSelfMediaAccountCreate() {
  editingSelfMediaAccount.value = null
  selfMediaAccountForm.platform = 'toutiao'
  selfMediaAccountForm.accountName = ''
  selfMediaAccountForm.status = 'active'
  selfMediaAccountVisible.value = true
}

function openSelfMediaAccountEdit(account: SemiAutoSelfMediaAccount) {
  editingSelfMediaAccount.value = account
  selfMediaAccountForm.platform = isSemiAutoPlatform(account.platform) ? account.platform : 'toutiao'
  selfMediaAccountForm.accountName = account.accountName || ''
  selfMediaAccountForm.status = account.status === 'disabled' ? 'disabled' : 'active'
  selfMediaAccountVisible.value = true
}

function isSemiAutoPlatform(platform?: string | null): platform is SemiAutoPlatform {
  return platform === 'toutiao' || platform === 'zhihu' || platform === 'xiaohongshu'
}

async function submitSelfMediaAccount() {
  const valid = await selfMediaAccountFormRef.value?.validate().catch(() => false)
  if (!valid) return
  selfMediaAccountSaving.value = true
  try {
    const payload = {
      platform: selfMediaAccountForm.platform,
      accountName: selfMediaAccountForm.accountName.trim(),
      status: selfMediaAccountForm.status,
    }
    if (editingSelfMediaAccount.value) {
      await updateSelfMediaAccount(editingSelfMediaAccount.value.id, payload)
    } else {
      await createSelfMediaAccount(brandId, payload)
    }
    ElMessage.success('自媒体账号已保存')
    selfMediaAccountVisible.value = false
    await loadSelfMediaAccounts()
  } finally {
    selfMediaAccountSaving.value = false
  }
}

function openEdit() {
  if (!brand.value) return
  fillForm(brand.value)
  editVisible.value = true
}

async function submitBrand() {
  const valid = await brandFormRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const region = regionPayloadFromCodes(brandForm.regionCodes)
    await updateBrand(brandId, {
      companyId: brand.value?.companyId,
      brandName: brandForm.brandName,
      brandShortName: nullableText(brandForm.brandShortName),
      brandSlug: brandForm.brandSlug,
      industry: brandForm.industry,
      mainBusiness: nullableText(brandForm.mainBusiness),
      coreProducts: nullableText(brandForm.coreProducts),
      brandPositioning: nullableText(brandForm.brandPositioning),
      serviceArea: region.displayName,
      provinceCode: region.provinceCode,
      provinceName: region.provinceName,
      cityCode: region.cityCode,
      cityName: region.cityName,
      districtCode: region.districtCode,
      districtName: region.districtName,
      website: nullableText(brandForm.website),
      officialAccount: nullableText(brand.value?.officialAccount),
      videoAccount: nullableText(brand.value?.videoAccount),
      douyinAccount: nullableText(brand.value?.douyinAccount),
      phone: nullableText(brandForm.phone),
      publicPhone: nullableText(brandForm.publicPhone),
      publicAddress: nullableText(brandForm.publicAddress),
      wechat: nullableText(brandForm.wechat),
      status: brandForm.status,
      description: nullableText(brandForm.businessIntro),
      businessIntro: nullableText(brandForm.businessIntro),
      brandQualificationDescription: nullableText(brandForm.brandQualificationDescription),
      brandCaseDescription: nullableText(brandForm.brandCaseDescription),
      forbiddenPhrases: nullableText(brandForm.forbiddenPhrases),
      geoSiteCode: normalizeGeoSiteCode(brandForm.geoSiteCode) || null,
      geoSiteStatus: normalizeGeoSiteCode(brandForm.geoSiteCode) ? brandForm.geoSiteStatus || 'active' : null,
      industrySiteName: nullableText(brandForm.industrySiteName),
      industrySiteCode: nullableText(brandForm.industrySiteCode),
    })
    ElMessage.success('品牌信息已更新')
    editVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function removeCurrentBrand() {
  if (!brand.value) return
  try {
    await ElMessageBox.confirm(`确认删除品牌「${brand.value.brandName}」？该操作不可撤销。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
    })
    await deleteBrand(brandId)
    ElMessage.success('删除成功')
    if (brand.value.companyId) {
      router.push(`/admin/customers/${brand.value.companyId}`)
    } else {
      router.push('/admin/customers')
    }
  } catch (err: any) {
    if (err === 'cancel' || err === 'close') return
  }
}

function goCreateProject() {
  if (!brand.value?.companyId) {
    ElMessage.warning('未找到所属客户，无法创建项目')
    return
  }
  router.push({ path: '/admin/projects', query: { companyId: String(brand.value.companyId), brandId: String(brandId) } })
}

function goCompanyDetail() {
  if (!brand.value?.companyId) return
  router.push(`/admin/customers/${brand.value.companyId}`)
}

onMounted(async () => {
  if (!hasValidId) {
    ElMessage.error('品牌参数无效')
    return
  }
  await dictStore.ensureLoaded()
  await load()
})
</script>

<style scoped>
.brand-detail-card :deep(.el-card__body) {
  padding: 20px;
}

.brand-detail-sections {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.brand-section-bar {
  display: flex;
  align-items: center;
  gap: 9px;
  margin: 0 0 14px;
  color: #1e40af;
  font-size: 14px;
  font-weight: 850;
}

.brand-section-bar span {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: #2563eb;
  box-shadow: 0 0 0 4px #dbeafe;
}

.brand-section-bar i {
  flex: 1;
  height: 1px;
  background: linear-gradient(90deg, #bfdbfe, transparent);
}

.brand-info-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.brand-info-item {
  min-height: 72px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 7px;
  min-width: 0;
  padding: 13px 14px;
  border: 1px solid #e7edf5;
  border-radius: 12px;
  background: linear-gradient(135deg, #ffffff 0%, #fbfdff 66%, #f8fbff 100%);
  box-shadow: inset 3px 0 0 #dbeafe;
}

.brand-info-item.is-wide {
  grid-column: 1 / -1;
}

.brand-info-label {
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.brand-info-value {
  min-width: 0;
  color: var(--admin-text-strong);
  font-size: 14px;
  line-height: 1.58;
  white-space: pre-wrap;
  word-break: break-word;
}

.brand-editor-dialog :deep(.el-dialog__body) {
  padding: 18px 22px 4px;
}

.brand-form {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.brand-form .brand-section-bar:not(:first-child) {
  margin-top: 26px;
}

.brand-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 22px;
  row-gap: 18px;
}

.brand-form-grid :deep(.el-form-item) {
  min-width: 0;
  margin-bottom: 0;
}

.brand-form-grid .is-wide {
  grid-column: 1 / -1;
}

.brand-form :deep(.el-form-item__label) {
  color: #334155;
  font-weight: 750;
  line-height: 1.35;
}

.brand-form :deep(.el-input__wrapper),
.brand-form :deep(.el-select__wrapper),
.brand-form :deep(.el-textarea__inner) {
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 0 0 1px #dbe3ee inset;
}

.brand-form :deep(.el-input__wrapper:hover),
.brand-form :deep(.el-select__wrapper:hover),
.brand-form :deep(.el-textarea__inner:hover) {
  box-shadow: 0 0 0 1px #93c5fd inset;
}

.brand-field-help {
  margin-top: 6px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.55;
}

@media (max-width: 960px) {
  .brand-info-grid,
  .brand-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
