<template>
  <div class="space-y-4">
    <el-page-header content="品牌详情" @back="$router.back()" />

    <el-card v-loading="loading">
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

      <el-descriptions :column="3" border>
        <el-descriptions-item label="品牌名称">{{ brand?.brandName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="品牌标识">{{ brand?.brandSlug || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ dictStore.label('brand_status', brand?.status) }}</el-descriptions-item>

        <el-descriptions-item label="所属客户">{{ companyName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="品牌行业">{{ industryLabel(brand?.industry) }}</el-descriptions-item>
        <el-descriptions-item label="主营业务">{{ brand?.mainBusiness || '-' }}</el-descriptions-item>
        <el-descriptions-item label="所在地区">{{ regionText }}</el-descriptions-item>

        <el-descriptions-item label="官网">{{ brand?.website || '-' }}</el-descriptions-item>
        <el-descriptions-item label="公众号">{{ brand?.officialAccount || '-' }}</el-descriptions-item>
        <el-descriptions-item label="视频号">{{ brand?.videoAccount || '-' }}</el-descriptions-item>
        <el-descriptions-item label="GEO站点标识">{{ brand?.geoSiteCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="GEO站点状态">{{ geoSiteStatusLabel(brand?.geoSiteStatus) }}</el-descriptions-item>
        <el-descriptions-item label="GEO站点域名">{{ brand?.geoSiteCode ? `https://www.${brand.geoSiteCode}.com` : '-' }}</el-descriptions-item>
        <el-descriptions-item label="抖音号">{{ brand?.douyinAccount || '-' }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ brand?.phone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="微信">{{ brand?.wechat || '-' }}</el-descriptions-item>

        <el-descriptions-item label="品牌标准表述" :span="3">{{ brand?.standardBrandStatement || '-' }}</el-descriptions-item>
        <el-descriptions-item label="业务标准表述" :span="3">{{ brand?.businessStandardStatement || '-' }}</el-descriptions-item>
        <el-descriptions-item label="业务介绍" :span="3">{{ brand?.businessIntro || '-' }}</el-descriptions-item>
        <el-descriptions-item label="品牌描述" :span="3">{{ brand?.description || '-' }}</el-descriptions-item>
        <el-descriptions-item label="禁用词" :span="3">{{ brand?.forbiddenPhrases || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card>
      <template #header>
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2">
            <span>品牌标准表达</span>
            <el-tag :type="statementTagType">{{ statementStatusLabel }}</el-tag>
            <el-tag v-if="statement?.statementVersion" type="info">v{{ statement?.statementVersion }}</el-tag>
          </div>
          <div class="space-x-2">
            <el-button v-if="canEditStatement" type="primary" link @click="openStatementEditor">编辑</el-button>
            <el-button v-if="canRegenerateStatement" type="warning" link @click="regenerateStatementNow">重新生成</el-button>
            <el-button v-if="canLockStatement && statement?.statementStatus !== 'locked'" type="success" link @click="lockStatementNow">确认锁定</el-button>
            <el-button v-if="canLockStatement && statement?.statementStatus === 'locked'" type="danger" link @click="unlockStatementNow">解锁</el-button>
          </div>
        </div>
      </template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="一句话定位" :span="2">{{ statement?.standardStatement?.positioning || '-' }}</el-descriptions-item>
        <el-descriptions-item label="核心卖点" :span="2">
          <div v-if="statement?.standardStatement?.selling_points?.length" class="flex flex-wrap gap-2">
            <el-tag v-for="(point, idx) in statement?.standardStatement?.selling_points || []" :key="`${idx}-${point}`" type="info">{{ point }}</el-tag>
          </div>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="差异化表达" :span="2">{{ statement?.standardStatement?.differentiation || '-' }}</el-descriptions-item>
        <el-descriptions-item label="推荐品牌介绍段落" :span="2">{{ statement?.standardStatement?.brand_paragraph || '-' }}</el-descriptions-item>
        <el-descriptions-item label="生成时间">{{ statement?.statementGeneratedAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="锁定时间">{{ statement?.statementLockedAt || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card>
      <template #header><span>扩展入口</span></template>
      <div class="flex flex-wrap gap-3">
        <el-button @click="router.push(`/admin/brands/${brandId}/profile`)">品牌画像</el-button>
        <el-button @click="router.push(`/admin/brands/${brandId}/assets`)">品牌资产</el-button>
      </div>
    </el-card>

    <el-dialog v-model="statementVisible" title="编辑品牌标准表达" width="760px">
      <el-form :model="statementForm" label-width="140px">
        <el-form-item label="一句话定位">
          <el-input v-model="statementForm.positioning" maxlength="20" show-word-limit />
        </el-form-item>
        <el-form-item label="核心卖点（每行一条）">
          <el-input v-model="statementForm.sellingPointsText" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="差异化表达">
          <el-input v-model="statementForm.differentiation" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="推荐品牌介绍段落">
          <el-input v-model="statementForm.brandParagraph" type="textarea" :rows="6" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="statementVisible = false">取消</el-button>
        <el-button type="primary" :loading="statementSaving" @click="submitStatementDraft">保存草稿</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editVisible" title="编辑品牌" width="680px">
      <el-form ref="brandFormRef" :model="brandForm" :rules="brandRules" label-width="120px">
        <el-form-item label="品牌名称" required><el-input v-model="brandForm.brandName" /></el-form-item>
        <el-form-item label="品牌标识" required><el-input v-model="brandForm.brandSlug" /></el-form-item>
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
        <el-form-item label="主营业务"><el-input v-model="brandForm.mainBusiness" /></el-form-item>
        <el-form-item label="地区"><RegionCascader v-model="brandForm.regionCodes" /></el-form-item>
        <el-form-item label="官网"><el-input v-model="brandForm.website" /></el-form-item>
        <el-form-item label="GEO站点标识">
          <el-input v-model="brandForm.geoSiteCode" placeholder="例如 ok / sanhexinglian" />
        </el-form-item>
        <el-form-item label="GEO站点状态">
          <el-select v-model="brandForm.geoSiteStatus" style="width: 100%" clearable>
            <el-option label="启用" value="active" />
            <el-option label="停用" value="disabled" />
          </el-select>
        </el-form-item>
        <el-form-item label="公众号"><el-input v-model="brandForm.officialAccount" /></el-form-item>
        <el-form-item label="视频号"><el-input v-model="brandForm.videoAccount" /></el-form-item>
        <el-form-item label="抖音号"><el-input v-model="brandForm.douyinAccount" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="brandForm.phone" /></el-form-item>
        <el-form-item label="微信"><el-input v-model="brandForm.wechat" /></el-form-item>
        <el-form-item label="状态" required>
          <el-select v-model="brandForm.status" style="width: 100%">
            <el-option
              v-for="item in dictStore.options('brand_status')"
              :key="item.dictKey"
              :label="item.dictValue"
              :value="item.dictKey"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="品牌描述"><el-input v-model="brandForm.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="业务介绍"><el-input v-model="brandForm.businessIntro" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="标准口径"><el-input v-model="brandForm.standardBrandStatement" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="业务标准表述"><el-input v-model="brandForm.businessStandardStatement" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="禁用词"><el-input v-model="brandForm.forbiddenPhrases" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitBrand">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  getBrandDetail,
  updateBrand,
  deleteBrand,
  getCompanyDetail,
  getBrandStatementDetail,
  saveBrandStatementDraft,
  lockBrandStatement,
  unlockBrandStatement,
  regenerateBrandStatement,
} from '@/api/customer'
import type { Brand, BrandStatementView } from '@/types'
import { useUserStore } from '@/stores/user'
import { useDictStore } from '@/stores/dict'
import RegionCascader from '@/components/ui/RegionCascader.vue'
import { regionCodesFromPayload, regionDisplayFromPayload, regionPayloadFromCodes } from '@/constants/region'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const dictStore = useDictStore()

const brandId = Number(route.params.id)
const hasValidId = Number.isFinite(brandId) && brandId > 0

const canUpdateBrand = computed(() => userStore.hasPermission('brand.update'))
const canDeleteBrand = computed(() => userStore.hasPermission('brand.delete'))
const canCreateProject = computed(() => userStore.hasPermission('project.create'))
const canLockStatement = computed(() => userStore.hasPermission('brand.statement.lock'))
const isPartnerRole = computed(() =>
  ['partner', 'partner_staff', 'partner_viewer'].includes(userStore.role || ''),
)
const canEditStatement = computed(() => canUpdateBrand.value && !isPartnerRole.value)
const canRegenerateStatement = computed(() => canEditStatement.value)

const loading = ref(false)
const saving = ref(false)
const editVisible = ref(false)
const statementVisible = ref(false)
const statementSaving = ref(false)
const brand = ref<Brand | null>(null)
const statement = ref<BrandStatementView | null>(null)
const companyName = ref('')
const companyIndustryTags = ref<string[]>([])
const brandFormRef = ref<FormInstance>()

const brandForm = reactive({
  brandName: '',
  brandSlug: '',
  industry: '',
  mainBusiness: '',
  regionCodes: [] as string[],
  website: '',
  geoSiteCode: '',
  geoSiteStatus: 'active',
  officialAccount: '',
  videoAccount: '',
  douyinAccount: '',
  phone: '',
  wechat: '',
  status: 'active',
  description: '',
  businessIntro: '',
  standardBrandStatement: '',
  businessStandardStatement: '',
  forbiddenPhrases: '',
})

const statementForm = reactive({
  positioning: '',
  sellingPointsText: '',
  differentiation: '',
  brandParagraph: '',
})

const brandRules: FormRules = {
  brandName: [{ required: true, message: '请输入品牌名称', trigger: 'blur' }],
  brandSlug: [
    { required: true, message: '请输入品牌标识', trigger: 'blur' },
    { pattern: /^[a-z0-9][a-z0-9_-]{1,127}$/, message: '标识需小写字母数字开头，可含 _ -', trigger: 'blur' },
  ],
  industry: [{ required: true, message: '请选择品牌行业', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
}

const regionText = computed(() => {
  if (!brand.value) return '-'
  return regionDisplayFromPayload(brand.value) || brand.value.serviceArea || '-'
})

const statementStatusLabel = computed(() => {
  const status = statement.value?.statementStatus
  if (status === 'locked') return '已锁定'
  if (status === 'draft') return '待确认'
  if (status === 'pending') return '生成中'
  return '未生成'
})

const statementTagType = computed<'success' | 'warning' | 'info'>(() => {
  const status = statement.value?.statementStatus
  if (status === 'locked') return 'success'
  if (status === 'draft') return 'warning'
  return 'info'
})

const availableBrandIndustries = computed(() => companyIndustryTags.value)

function industryLabel(value?: string | null) {
  if (!value) return '-'
  return dictStore.label('industry_tag', value) || value
}

function geoSiteStatusLabel(value?: string | null) {
  if (value === 'active') return '启用'
  if (value === 'disabled') return '停用'
  return '-'
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
  brandForm.brandSlug = data.brandSlug
  brandForm.industry = data.industry || availableBrandIndustries.value[0] || ''
  brandForm.mainBusiness = data.mainBusiness || ''
  brandForm.regionCodes = regionCodesFromPayload(data)
  brandForm.website = data.website || ''
  brandForm.geoSiteCode = data.geoSiteCode || ''
  brandForm.geoSiteStatus = data.geoSiteCode ? (data.geoSiteStatus || 'active') : ''
  brandForm.officialAccount = data.officialAccount || ''
  brandForm.videoAccount = data.videoAccount || ''
  brandForm.douyinAccount = data.douyinAccount || ''
  brandForm.phone = data.phone || ''
  brandForm.wechat = data.wechat || ''
  brandForm.status = data.status || 'active'
  brandForm.description = data.description || ''
  brandForm.businessIntro = data.businessIntro || ''
  brandForm.standardBrandStatement = data.standardBrandStatement || ''
  brandForm.businessStandardStatement = data.businessStandardStatement || ''
  brandForm.forbiddenPhrases = Array.isArray(data.forbiddenPhrases)
    ? data.forbiddenPhrases.join('，')
    : (data.forbiddenPhrases || '')
}

async function load() {
  loading.value = true
  try {
    const { data } = await getBrandDetail(brandId)
    brand.value = data.data
    fillForm(data.data)
    const statementRes = await getBrandStatementDetail(brandId)
    statement.value = statementRes.data.data
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
  } catch {
    brand.value = null
    statement.value = null
    companyName.value = ''
  } finally {
    loading.value = false
  }
}

function openStatementEditor() {
  const detail = statement.value?.standardStatement
  statementForm.positioning = detail?.positioning || ''
  statementForm.sellingPointsText = (detail?.selling_points || []).join('\n')
  statementForm.differentiation = detail?.differentiation || ''
  statementForm.brandParagraph = detail?.brand_paragraph || ''
  statementVisible.value = true
}

async function submitStatementDraft() {
  if (!statementForm.positioning.trim() || !statementForm.differentiation.trim() || !statementForm.brandParagraph.trim()) {
    ElMessage.warning('请补全定位、差异化表达和品牌介绍段落')
    return
  }
  statementSaving.value = true
  try {
    const sellingPoints = statementForm.sellingPointsText
      .split(/\r?\n/)
      .map((v) => v.trim())
      .filter(Boolean)
    const { data } = await saveBrandStatementDraft(brandId, {
      positioning: statementForm.positioning.trim(),
      sellingPoints,
      differentiation: statementForm.differentiation.trim(),
      brandParagraph: statementForm.brandParagraph.trim(),
    })
    statement.value = data.data
    statementVisible.value = false
    ElMessage.success('品牌标准表达已保存为草稿')
  } finally {
    statementSaving.value = false
  }
}

async function lockStatementNow() {
  const { data } = await lockBrandStatement(brandId)
  statement.value = data.data
  ElMessage.success('品牌标准表达已锁定')
}

async function unlockStatementNow() {
  const { data } = await unlockBrandStatement(brandId)
  statement.value = data.data
  ElMessage.success('已解锁，可继续编辑')
}

async function regenerateStatementNow() {
  await regenerateBrandStatement(brandId, {
    remark: 'manual_regenerate_from_brand_detail',
  })
  ElMessage.success('已投递重生成任务')
  await load()
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
      brandSlug: brandForm.brandSlug,
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
      geoSiteCode: brandForm.geoSiteCode || undefined,
      geoSiteStatus: brandForm.geoSiteStatus || undefined,
      officialAccount: brandForm.officialAccount || undefined,
      videoAccount: brandForm.videoAccount || undefined,
      douyinAccount: brandForm.douyinAccount || undefined,
      phone: brandForm.phone || undefined,
      wechat: brandForm.wechat || undefined,
      status: brandForm.status,
      description: brandForm.description || undefined,
      businessIntro: brandForm.businessIntro || undefined,
      standardBrandStatement: brandForm.standardBrandStatement || undefined,
      businessStandardStatement: brandForm.businessStandardStatement || undefined,
      forbiddenPhrases: brandForm.forbiddenPhrases || undefined,
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
