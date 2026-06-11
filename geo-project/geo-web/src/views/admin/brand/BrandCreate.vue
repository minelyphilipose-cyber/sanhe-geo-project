<template>
  <div class="admin-page brand-create-page">
    <div class="brand-hero">
      <div>
        <div class="brand-hero-kicker">品牌资产</div>
        <h1 class="brand-hero-title">新建品牌</h1>
        <div class="brand-hero-subtitle">维护品牌基础资料、业务介绍、发布阵地、资质案例与素材资产。</div>
      </div>
      <div class="brand-hero-actions">
        <el-button @click="$router.back()">返回</el-button>
        <el-button v-if="createdBrandId" @click="goBrandDetail">查看品牌详情</el-button>
        <el-button type="primary" :loading="saving" @click="submitBrand">保存品牌</el-button>
      </div>
    </div>

    <section class="brand-panel">
      <div class="brand-panel-header">
        <div>
          <h2 class="brand-panel-title">品牌资料表单</h2>
          <p class="brand-panel-hint">按基础信息、介绍素材和发布阵地分区录入，减少后续维护成本。</p>
        </div>
      </div>

      <el-form ref="formRef" class="brand-form" :model="form" :rules="rules" label-position="top">
        <div class="brand-section-bar"><span />基础信息<i /></div>
        <div class="brand-form-grid">
          <el-form-item label="所属客户" required>
            <el-input :model-value="companyName || '-'" disabled />
          </el-form-item>
          <el-form-item label="品牌名称" prop="brandName" required><el-input v-model="form.brandName" /></el-form-item>
          <el-form-item label="品牌简称"><el-input v-model="form.brandShortName" maxlength="128" show-word-limit /></el-form-item>
          <el-form-item label="品牌状态" prop="status" required>
            <el-select v-model="form.status" style="width: 100%">
              <el-option
                v-for="item in dictStore.options('brand_status')"
                :key="item.dictKey"
                :label="item.dictValue"
                :value="item.dictKey"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="品牌行业" prop="industry" required>
            <el-select v-model="form.industry" filterable style="width: 100%">
              <el-option
                v-for="tag in availableBrandIndustries"
                :key="tag"
                :label="dictStore.label('industry_tag', tag) || tag"
                :value="tag"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="行业合规类型">
            <el-select v-model="form.complianceIndustryCode" clearable filterable placeholder="非特殊合规行业" style="width: 100%">
              <el-option label="非特殊合规行业" value="none" />
              <el-option
                v-for="item in dictStore.options('compliance_industry')"
                :key="item.dictKey"
                :label="item.dictValue"
                :value="item.dictKey"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="主营业务方向"><el-input v-model="form.mainBusiness" /></el-form-item>
          <el-form-item label="核心产品">
            <el-input v-model="form.coreProducts" maxlength="500" show-word-limit placeholder="多个产品以逗号隔开" />
          </el-form-item>
          <el-form-item label="品牌定位">
            <el-input v-model="form.brandPositioning" maxlength="255" show-word-limit placeholder="如“某某方案服务商/代理商”“本地某某平台”" />
          </el-form-item>
          <el-form-item class="is-wide" label="服务区域"><RegionCascader v-model="form.serviceAreaCodes" /></el-form-item>
          <el-form-item class="is-wide" label="所在地区"><RegionCascader v-model="form.regionCodes" /></el-form-item>
        </div>

        <div class="brand-section-bar"><span />业务介绍录入<i /></div>
        <div class="brand-form-grid">
          <el-form-item class="is-wide" label="业务介绍"><el-input v-model="form.businessIntro" type="textarea" :rows="4" /></el-form-item>
          <el-form-item class="is-wide" label="品牌资质描述">
            <el-input v-model="form.brandQualificationDescription" type="textarea" :rows="4" maxlength="300" show-word-limit :placeholder="qualificationDescriptionPlaceholder" />
            <div class="brand-field-help">仅填写可公开引用、可核验的资质与背书信息。</div>
          </el-form-item>
          <el-form-item class="is-wide" label="品牌案例描述">
            <el-input v-model="form.brandCaseDescription" type="textarea" :rows="4" maxlength="300" show-word-limit :placeholder="caseDescriptionPlaceholder" />
            <div class="brand-field-help">客户名称不可公开时，可使用行业或区域客户描述。</div>
          </el-form-item>
        </div>

        <div class="brand-section-bar"><span />联系方式与阵地<i /></div>
        <div class="brand-form-grid">
          <el-form-item label="联系电话"><el-input v-model="form.phone" /></el-form-item>
          <el-form-item label="对外公开电话"><el-input v-model="form.publicPhone" /></el-form-item>
          <el-form-item label="对外公开地址"><el-input v-model="form.publicAddress" /></el-form-item>
          <el-form-item label="默认发布位置">
            <el-input v-model="form.selfMediaPublishLocationName" maxlength="64" placeholder="用于头条等自媒体发布页添加位置" />
          </el-form-item>
          <el-form-item label="微信"><el-input v-model="form.wechat" /></el-form-item>
          <el-form-item class="is-wide" label="官网"><el-input v-model="form.website" /></el-form-item>
        </div>

        <div class="brand-section-bar"><span />发布阵地<i /></div>
        <div class="brand-form-grid">
          <el-form-item label="Agent 官网">
            <el-select
              v-model="form.geoSiteCode"
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
              v-model="form.industrySiteCode"
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

        <div class="brand-section-bar"><span />内容约束<i /></div>
        <div class="brand-form-grid">
          <el-form-item class="is-wide" label="禁用词"><el-input v-model="form.forbiddenPhrases" type="textarea" :rows="3" /></el-form-item>
          <el-form-item class="is-wide" label="版本变更原因"><el-input v-model="form.versionChangeReason" placeholder="用于版本记录，建议填写" /></el-form-item>
        </div>

        <template v-if="isMedicalComplianceIndustry">
          <div class="brand-section-bar"><span />医疗合规信息<i /></div>
          <el-alert
            class="is-wide"
            type="warning"
            show-icon
            :closable="false"
            title="保存品牌后，请在品牌详情的产品信息中至少启用一个医疗项目；否则医疗文章生成会被项目资质闸门拦截。"
          />
          <div class="brand-form-grid">
            <el-form-item label="机构类型"><el-input v-model="form.institutionType" maxlength="128" /></el-form-item>
            <el-form-item label="医疗广告审查证明编号"><el-input v-model="form.medicalAdReviewNo" maxlength="128" /></el-form-item>
            <el-form-item class="is-wide" label="医疗机构执业许可">
              <el-input v-model="form.medicalLicense" type="textarea" :rows="2" maxlength="500" show-word-limit />
            </el-form-item>
            <el-form-item class="is-wide" label="诊疗科目范围">
              <el-input v-model="form.diagnosisScope" type="textarea" :rows="2" maxlength="1000" show-word-limit />
            </el-form-item>
            <el-form-item class="is-wide" label="医师/执业人员可公示信息">
              <el-input v-model="form.practitionerInfoPublic" type="textarea" :rows="2" />
            </el-form-item>
            <el-form-item class="is-wide" label="医疗合规备注">
              <el-input v-model="form.complianceNotesMedical" type="textarea" :rows="2" />
            </el-form-item>
          </div>
        </template>
      </el-form>
    </section>

    <section class="brand-panel">
      <div class="brand-panel-header">
        <div>
          <h2 class="brand-panel-title">素材上传</h2>
          <p class="brand-panel-hint">保存品牌后可上传图片、案例和资质材料。</p>
        </div>
      </div>
      <div class="brand-upload-row">
        <el-select v-model="uploadCategory" style="width: 220px" :disabled="!createdBrandId">
          <el-option
            v-for="item in dictStore.options('brand_material_category')"
            :key="item.dictKey"
            :label="item.dictValue"
            :value="item.dictKey"
          />
        </el-select>
        <el-upload :show-file-list="false" :before-upload="beforeUpload" :disabled="!createdBrandId">
          <el-button type="primary" :disabled="!createdBrandId">上传素材</el-button>
        </el-upload>
        <span class="text-xs text-gray-500">单文件不超过 10MB</span>
        <span v-if="!createdBrandId" class="text-xs text-gray-500">请先保存品牌后再上传素材</span>
      </div>

      <el-table :data="materials" border>
        <el-table-column label="分类" width="130">
          <template #default="scope">{{ dictStore.label('brand_material_category', scope.row.category) }}</template>
        </el-table-column>
        <el-table-column prop="fileName" label="文件名" min-width="220" />
        <el-table-column prop="fileType" label="类型" width="140" />
        <el-table-column label="大小(KB)" width="120">
          <template #default="scope">{{ ((scope.row.fileSize || 0) / 1024).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="链接" min-width="220">
          <template #default="scope">
            <el-button v-if="isPreviewableMaterial(scope.row)" link type="primary" @click="openMaterialPreview(scope.row)">查看文件</el-button>
            <el-button v-else link type="primary" @click="downloadMaterial(scope.row)">下载文件</el-button>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="上传时间" width="180" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="scope">
            <el-button type="danger" link @click="removeMaterial(scope.row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="brand-panel">
      <div class="brand-panel-header">
        <div>
          <h2 class="brand-panel-title">历史版本</h2>
          <p class="brand-panel-hint">记录品牌资料快照和变更原因。</p>
        </div>
      </div>
      <el-table :data="versions" border>
        <el-table-column prop="versionNo" label="版本号" width="100" />
        <el-table-column prop="changeReason" label="变更原因" min-width="260" />
        <el-table-column prop="createdAt" label="创建时间" width="180" />
      </el-table>
    </section>

    <el-dialog v-model="previewVisible" title="文件预览" width="80%" class="admin-editor-dialog" @closed="onPreviewClosed">
      <div v-loading="previewLoading" style="min-height: 200px">
        <template v-if="previewMode === 'image' && previewImageUrl">
          <img :src="previewImageUrl" alt="preview" class="mx-auto max-w-full" />
        </template>
        <template v-else-if="previewMode === 'pdf'">
          <div v-if="pdfPageImages.length === 0 && !previewLoading" class="text-gray-500">暂无可预览内容</div>
          <div v-for="(img, idx) in pdfPageImages" :key="idx" class="mb-4">
            <img :src="img" :alt="`pdf-page-${idx + 1}`" class="w-full border border-gray-200" />
          </div>
        </template>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type UploadRawFile } from 'element-plus'
import RegionCascader from '@/components/ui/RegionCascader.vue'
import { useDictStore } from '@/stores/dict'
import {
  createBrand,
  deleteBrandMaterial,
  getBrandMaterials,
  getBrandMaterialStream,
  getBrandVersions,
  getCompanyDetail,
  updateBrand,
  uploadBrandMaterial,
} from '@/api/customer'
import { getPublishSites } from '@/api/publishSite'
import type { BrandMaterial, BrandProfileVersion, PublishSite } from '@/types'
import { regionPayloadFromCodes } from '@/constants/region'
import { getDocument, GlobalWorkerOptions } from 'pdfjs-dist'
import pdfWorkerSrc from 'pdfjs-dist/build/pdf.worker.mjs?url'
import { nullableText } from '@/utils/form'

GlobalWorkerOptions.workerSrc = pdfWorkerSrc

const route = useRoute()
const router = useRouter()
const dictStore = useDictStore()

const companyId = computed(() => Number(route.query.companyId || 0))
const createdBrandId = ref<number | null>(null)
const companyName = ref('')
const companyIndustryTags = ref<string[]>([])
const saving = ref(false)

const formRef = ref<FormInstance>()
const form = reactive({
  brandName: '',
  brandShortName: '',
  brandSlug: '',
  status: 'active',
  industry: '',
  complianceIndustryCode: 'none',
  mainBusiness: '',
  coreProducts: '',
  brandPositioning: '',
  businessIntro: '',
  serviceAreaCodes: [] as string[],
  regionCodes: [] as string[],
  phone: '',
  publicPhone: '',
  publicAddress: '',
  selfMediaPublishLocationName: '',
  wechat: '',
  website: '',
  officialAccount: '',
  videoAccount: '',
  douyinAccount: '',
  geoSiteCode: '',
  geoSiteStatus: 'active',
  industrySiteName: '',
  industrySiteCode: '',
  brandQualificationDescription: '',
  brandCaseDescription: '',
  forbiddenPhrases: '',
  medicalLicense: '',
  diagnosisScope: '',
  institutionType: '',
  practitionerInfoPublic: '',
  medicalAdReviewNo: '',
  complianceNotesMedical: '',
  versionChangeReason: '',
})

const rules: FormRules = {
  brandName: [{ required: true, message: '请输入品牌名称', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
  industry: [{ required: true, message: '请选择品牌行业', trigger: 'change' }],
}

const qualificationDescriptionPlaceholder = '请填写品牌可公开引用的资质与背书信息，包括认证资质、检测报告、执行标准、专利/软著、荣誉奖项、协会或平台背书、生产/服务能力证明等。请写清楚名称、编号、发证机构、适用范围、有效期等可核验信息。没有真实依据的内容不要填写。'
const caseDescriptionPlaceholder = '请填写可公开引用的品牌案例素材，包括客户类型或客户名称、项目背景、服务内容、项目规模、交付周期、合作结果、复购或长期合作情况等。如客户名称不可公开，请使用“某行业客户/某区域客户”表述，不要编造客户名或效果数据。'

const availableBrandIndustries = computed(() => companyIndustryTags.value)
const isMedicalComplianceIndustry = computed(() =>
  ['medical_beauty', 'oral'].includes(form.complianceIndustryCode),
)

const materials = ref<BrandMaterial[]>([])
const versions = ref<BrandProfileVersion[]>([])
const publishSites = ref<PublishSite[]>([])
const GEO_SITE_CODE_PATTERN = /^[a-z0-9](?:[a-z0-9_-]{0,62}[a-z0-9])?$/
const uploadCategory = ref('brand_image')
const MAX_UPLOAD_SIZE = 10 * 1024 * 1024

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

function handleAgentSiteChange(value: string) {
  form.geoSiteCode = normalizeGeoSiteCode(value)
  const site = agentSiteOptions.value.find((item) => item.siteCode === form.geoSiteCode)
  form.geoSiteStatus = site ? 'active' : ''
}

function normalizeGeoSiteCode(code?: string | null) {
  const normalized = code?.trim().toLowerCase() || ''
  return GEO_SITE_CODE_PATTERN.test(normalized) ? normalized : ''
}

function isValidGeoSiteCode(code?: string | null) {
  return !!normalizeGeoSiteCode(code)
}

function handleIndustrySiteChange(value: string) {
  const site = industrySiteOptions.value.find((item) => item.siteCode === value)
  form.industrySiteName = site?.siteName || ''
}

const previewableExtSet = new Set(['pdf', 'png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp', 'svg', 'tif', 'tiff'])
const imageExtSet = new Set(['png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp', 'svg', 'tif', 'tiff'])
const previewVisible = ref(false)
const previewLoading = ref(false)
const previewMode = ref<'image' | 'pdf'>('image')
const previewImageUrl = ref('')
const pdfPageImages = ref<string[]>([])

function resolveMaterialExt(item: BrandMaterial): string {
  const extByType = (item.fileType || '').trim().toLowerCase()
  if (extByType) return extByType
  const fileName = (item.fileName || '').trim()
  const dot = fileName.lastIndexOf('.')
  if (dot >= 0 && dot < fileName.length - 1) {
    return fileName.substring(dot + 1).toLowerCase()
  }
  const cleanUrl = (item.fileUrl || '').trim().split('?')[0]
  const slash = cleanUrl.lastIndexOf('/')
  const lastPart = slash >= 0 ? cleanUrl.substring(slash + 1) : cleanUrl
  const lastDot = lastPart.lastIndexOf('.')
  if (lastDot >= 0 && lastDot < lastPart.length - 1) {
    return lastPart.substring(lastDot + 1).toLowerCase()
  }
  return ''
}

function isPreviewableMaterial(item: BrandMaterial): boolean {
  return previewableExtSet.has(resolveMaterialExt(item))
}

function clearPreviewState() {
  previewImageUrl.value = ''
  pdfPageImages.value = []
}

function onPreviewClosed() {
  if (previewImageUrl.value) {
    URL.revokeObjectURL(previewImageUrl.value)
  }
  clearPreviewState()
}

async function openMaterialPreview(item: BrandMaterial) {
  previewVisible.value = true
  previewLoading.value = true
  clearPreviewState()
  try {
    const { data } = await getBrandMaterialStream(item.brandId, item.id, false)
    const blob = data as unknown as Blob
    const ext = resolveMaterialExt(item)
    if (imageExtSet.has(ext)) {
      previewMode.value = 'image'
      previewImageUrl.value = URL.createObjectURL(blob)
      return
    }
    previewMode.value = 'pdf'
    await renderPdfPages(blob)
  } finally {
    previewLoading.value = false
  }
}

async function renderPdfPages(blob: Blob) {
  const bytes = new Uint8Array(await blob.arrayBuffer())
  const pdf = await getDocument({ data: bytes }).promise
  const pages: string[] = []
  for (let i = 1; i <= pdf.numPages; i += 1) {
    const page = await pdf.getPage(i)
    const viewport = page.getViewport({ scale: 1.4 })
    const canvas = document.createElement('canvas')
    const context = canvas.getContext('2d')
    if (!context) continue
    canvas.width = viewport.width
    canvas.height = viewport.height
    await page.render({ canvasContext: context, viewport }).promise
    pages.push(canvas.toDataURL('image/png'))
  }
  pdfPageImages.value = pages
}

async function downloadMaterial(item: BrandMaterial) {
  const { data } = await getBrandMaterialStream(item.brandId, item.id, true)
  const blob = data as unknown as Blob
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = item.fileName || `material_${item.id}`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

async function loadCompany() {
  if (!companyId.value) return
  try {
    const { data } = await getCompanyDetail(companyId.value)
    companyName.value = data.data.companyName || ''
    companyIndustryTags.value = parseIndustryTags((data.data as any).industryTags)
    if (!form.industry) {
      form.industry = companyIndustryTags.value[0] || ''
    }
  } catch {
    companyName.value = ''
    companyIndustryTags.value = []
  }
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

async function submitBrand() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (!companyId.value) {
    ElMessage.error('缺少所属客户，无法创建品牌')
    return
  }
  saving.value = true
  try {
    const region = regionPayloadFromCodes(form.regionCodes)
    const serviceArea = regionPayloadFromCodes(form.serviceAreaCodes).displayName
    const commonPayload = {
      brandName: form.brandName,
      brandShortName: nullableText(form.brandShortName),
      status: form.status,
      industry: form.industry,
      complianceIndustryCode: form.complianceIndustryCode === 'none' ? null : form.complianceIndustryCode,
      mainBusiness: nullableText(form.mainBusiness),
      coreProducts: nullableText(form.coreProducts),
      brandPositioning: nullableText(form.brandPositioning),
      businessIntro: nullableText(form.businessIntro),
      serviceArea: nullableText(serviceArea),
      provinceCode: region.provinceCode,
      provinceName: region.provinceName,
      cityCode: region.cityCode,
      cityName: region.cityName,
      districtCode: region.districtCode,
      districtName: region.districtName,
      phone: nullableText(form.phone),
      publicPhone: nullableText(form.publicPhone),
      publicAddress: nullableText(form.publicAddress),
      selfMediaPublishLocationName: nullableText(form.selfMediaPublishLocationName),
      wechat: nullableText(form.wechat),
      website: nullableText(form.website),
      geoSiteCode: normalizeGeoSiteCode(form.geoSiteCode) || null,
      geoSiteStatus: normalizeGeoSiteCode(form.geoSiteCode) ? form.geoSiteStatus || 'active' : null,
      industrySiteName: nullableText(form.industrySiteName),
      industrySiteCode: nullableText(form.industrySiteCode),
      brandQualificationDescription: nullableText(form.brandQualificationDescription),
      brandCaseDescription: nullableText(form.brandCaseDescription),
      description: nullableText(form.businessIntro),
      forbiddenPhrases: nullableText(form.forbiddenPhrases),
      medicalLicense: isMedicalComplianceIndustry.value ? nullableText(form.medicalLicense) : null,
      diagnosisScope: isMedicalComplianceIndustry.value ? nullableText(form.diagnosisScope) : null,
      institutionType: isMedicalComplianceIndustry.value ? nullableText(form.institutionType) : null,
      practitionerInfoPublic: isMedicalComplianceIndustry.value ? nullableText(form.practitionerInfoPublic) : null,
      medicalAdReviewNo: isMedicalComplianceIndustry.value ? nullableText(form.medicalAdReviewNo) : null,
      complianceNotesMedical: isMedicalComplianceIndustry.value ? nullableText(form.complianceNotesMedical) : null,
      versionChangeReason: nullableText(form.versionChangeReason),
    }

    if (!createdBrandId.value) {
      const { data } = await createBrand({ companyId: companyId.value, ...commonPayload })
      createdBrandId.value = data.data.id
      form.brandSlug = data.data.brandSlug
      ElMessage.success('品牌创建成功，可继续上传素材')
    } else {
      await updateBrand(createdBrandId.value, { ...commonPayload, brandSlug: form.brandSlug })
      ElMessage.success('品牌信息已更新')
    }
    await Promise.all([loadMaterials(), loadVersions()])
  } finally {
    saving.value = false
  }
}

async function beforeUpload(file: UploadRawFile) {
  if (!createdBrandId.value) return false
  if (file.size > MAX_UPLOAD_SIZE) {
    ElMessage.error(`文件「${file.name}」超过 10MB，已拒绝上传`)
    return false
  }
  try {
    await uploadBrandMaterial(createdBrandId.value, uploadCategory.value, file as File)
    ElMessage.success('上传成功')
    await Promise.all([loadMaterials(), loadVersions()])
  } catch {
    // handled by interceptor
  }
  return false
}

async function loadMaterials() {
  if (!createdBrandId.value) {
    materials.value = []
    return
  }
  const { data } = await getBrandMaterials(createdBrandId.value)
  materials.value = data.data || []
}

async function loadVersions() {
  if (!createdBrandId.value) {
    versions.value = []
    return
  }
  const { data } = await getBrandVersions(createdBrandId.value, { current: 1, size: 50 })
  versions.value = data.data.records || []
}

async function loadPublishSiteOptions() {
  try {
    const { data } = await getPublishSites({ status: 'active' })
    publishSites.value = data.data || []
  } catch {
    publishSites.value = []
  }
}

async function removeMaterial(materialId: number) {
  if (!createdBrandId.value) return
  await ElMessageBox.confirm('确认删除该素材？', '删除确认', {
    type: 'warning',
    confirmButtonText: '确认',
    cancelButtonText: '取消',
  })
  await deleteBrandMaterial(createdBrandId.value, materialId)
  ElMessage.success('素材已删除')
  await Promise.all([loadMaterials(), loadVersions()])
}

function goBrandDetail() {
  if (!createdBrandId.value) return
  router.push(`/admin/brands/${createdBrandId.value}`)
}

onMounted(async () => {
  await dictStore.ensureLoaded()
  await Promise.all([loadCompany(), loadPublishSiteOptions()])
  if (!dictStore.options('brand_material_category').length) {
    uploadCategory.value = 'other'
  }
})
</script>

<style scoped>
.brand-create-page {
  gap: 18px;
}

.brand-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  position: relative;
  overflow: hidden;
  min-height: 118px;
  padding: 24px 26px;
  border: 1px solid #dbeafe;
  border-radius: 16px;
  background:
    radial-gradient(circle at 84% 18%, rgba(16, 185, 129, 0.14), transparent 32%),
    linear-gradient(135deg, rgba(255, 255, 255, 0.98) 0%, rgba(239, 246, 255, 0.94) 58%, rgba(240, 253, 250, 0.9) 100%);
  box-shadow: 0 18px 42px rgba(37, 99, 235, 0.08);
}

.brand-hero::before {
  content: "";
  position: absolute;
  inset: 18px auto 18px 0;
  width: 5px;
  border-radius: 0 999px 999px 0;
  background: linear-gradient(180deg, #2563eb, #06b6d4 48%, #10b981);
}

.brand-hero > * {
  position: relative;
  z-index: 1;
}

.brand-hero-kicker {
  margin-bottom: 8px;
  color: #2563eb;
  font-size: 13px;
  font-weight: 800;
}

.brand-hero-title {
  margin: 0;
  color: var(--admin-text-strong);
  font-size: 24px;
  font-weight: 850;
  line-height: 1.28;
}

.brand-hero-subtitle {
  margin-top: 7px;
  color: #475569;
  font-size: 13px;
  line-height: 1.7;
}

.brand-hero-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}

.brand-panel {
  overflow: hidden;
  border: 1px solid var(--admin-panel-border);
  border-radius: 16px;
  background: linear-gradient(180deg, #fff 0%, #fff 78%, #f8fafc 100%);
  box-shadow: 0 16px 36px rgba(15, 23, 42, 0.075);
}

.brand-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  min-height: 58px;
  padding: 14px 20px;
  border-bottom: 1px solid var(--admin-panel-border-soft);
  background: linear-gradient(90deg, #f8fbff 0%, #ffffff 52%, #f0fdf4 100%);
}

.brand-panel-title {
  margin: 0;
  color: var(--admin-text-strong);
  font-size: 16px;
  font-weight: 850;
}

.brand-panel-hint {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.55;
}

.brand-form {
  padding: 20px;
}

.brand-section-bar {
  display: flex;
  align-items: center;
  gap: 9px;
  margin: 4px 0 18px;
  color: #1e40af;
  font-size: 14px;
  font-weight: 850;
}

.brand-section-bar:not(:first-child) {
  margin-top: 28px;
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

.brand-upload-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin: 18px 20px;
  padding: 14px;
  border: 1px dashed #bfdbfe;
  border-radius: 12px;
  background: linear-gradient(135deg, #f8fbff, #ffffff);
}

.brand-panel :deep(.el-table) {
  margin: 0 20px 20px;
  width: calc(100% - 40px);
}

@media (max-width: 900px) {
  .brand-hero {
    flex-direction: column;
    padding: 22px;
  }

  .brand-hero-actions {
    justify-content: flex-start;
  }

  .brand-form-grid {
    grid-template-columns: 1fr;
  }

  .brand-form {
    padding: 16px;
  }

  .brand-panel :deep(.el-table) {
    margin: 0 16px 16px;
    width: calc(100% - 32px);
  }
}
</style>
