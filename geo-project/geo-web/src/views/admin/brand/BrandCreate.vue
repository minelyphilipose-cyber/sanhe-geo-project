<template>
  <div class="admin-page">
    <div class="admin-page-header">
      <div>
        <div class="admin-page-kicker">品牌资产</div>
        <h1 class="admin-page-title">新建品牌</h1>
        <div class="admin-page-subtitle">维护品牌基础资料、发布阵地、资质案例与素材资产。</div>
      </div>
      <div class="admin-page-actions">
        <el-button @click="$router.back()">返回</el-button>
        <el-button v-if="createdBrandId" @click="goBrandDetail">查看品牌详情</el-button>
        <el-button type="primary" :loading="saving" @click="submitBrand">保存品牌</el-button>
      </div>
    </div>

    <el-card class="admin-rich-card">
      <template #header>
        <div class="flex items-center justify-between">
          <span>品牌资料表单</span>
        </div>
      </template>

      <el-form ref="formRef" class="admin-dialog-form" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="所属客户" required>
          <el-input :model-value="companyName || '-'" disabled />
        </el-form-item>
        <el-form-item label="品牌名称" prop="brandName" required><el-input v-model="form.brandName" /></el-form-item>
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

        <el-divider class="is-full" content-position="left">业务介绍录入</el-divider>
        <el-form-item label="品牌简称"><el-input v-model="form.brandShortName" maxlength="128" show-word-limit /></el-form-item>
        <el-form-item label="主营业务方向"><el-input v-model="form.mainBusiness" /></el-form-item>
        <el-form-item label="核心产品">
          <el-input v-model="form.coreProducts" maxlength="500" show-word-limit placeholder="多个产品以逗号隔开" />
        </el-form-item>
        <el-form-item label="品牌定位">
          <el-input v-model="form.brandPositioning" maxlength="255" show-word-limit placeholder="如“某某方案服务商/代理商”“本地某某平台”" />
        </el-form-item>
        <el-form-item class="is-full" label="业务介绍"><el-input v-model="form.businessIntro" type="textarea" :rows="4" /></el-form-item>
        <el-form-item class="is-full" label="品牌资质描述">
          <el-input v-model="form.brandQualificationDescription" type="textarea" :rows="3" maxlength="300" show-word-limit :placeholder="qualificationDescriptionPlaceholder" />
        </el-form-item>
        <el-form-item class="is-full" label="品牌案例描述">
          <el-input v-model="form.brandCaseDescription" type="textarea" :rows="3" maxlength="300" show-word-limit :placeholder="caseDescriptionPlaceholder" />
        </el-form-item>
        <el-form-item class="is-full" label="服务区域"><RegionCascader v-model="form.serviceAreaCodes" /></el-form-item>
        <el-form-item class="is-full" label="所在地区"><RegionCascader v-model="form.regionCodes" /></el-form-item>

        <el-divider class="is-full" content-position="left">联系方式与阵地</el-divider>
        <el-form-item label="联系电话"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="对外公开电话"><el-input v-model="form.publicPhone" /></el-form-item>
        <el-form-item label="对外公开地址"><el-input v-model="form.publicAddress" /></el-form-item>
        <el-form-item label="微信"><el-input v-model="form.wechat" /></el-form-item>
        <el-form-item label="官网"><el-input v-model="form.website" /></el-form-item>

        <el-divider class="is-full" content-position="left">发布阵地</el-divider>
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

        <el-divider class="is-full" content-position="left">内容约束</el-divider>
        <el-form-item class="is-full" label="禁用词"><el-input v-model="form.forbiddenPhrases" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="版本变更原因"><el-input v-model="form.versionChangeReason" placeholder="用于版本记录，建议填写" /></el-form-item>
      </el-form>
    </el-card>

    <el-card class="admin-table-card">
      <template #header><span>素材上传（图片/案例/资质）</span></template>
      <div class="mb-3 flex items-center gap-2">
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
    </el-card>

    <el-card class="admin-table-card">
      <template #header><span>历史版本</span></template>
      <el-table :data="versions" border>
        <el-table-column prop="versionNo" label="版本号" width="100" />
        <el-table-column prop="changeReason" label="变更原因" min-width="260" />
        <el-table-column prop="createdAt" label="创建时间" width="180" />
      </el-table>
    </el-card>

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
  mainBusiness: '',
  coreProducts: '',
  brandPositioning: '',
  businessIntro: '',
  serviceAreaCodes: [] as string[],
  regionCodes: [] as string[],
  phone: '',
  publicPhone: '',
  publicAddress: '',
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

const materials = ref<BrandMaterial[]>([])
const versions = ref<BrandProfileVersion[]>([])
const publishSites = ref<PublishSite[]>([])
const uploadCategory = ref('brand_image')
const MAX_UPLOAD_SIZE = 10 * 1024 * 1024

const agentSiteOptions = computed(() => publishSites.value.filter((site) =>
  site.integrationMethod === 'brand_geo_site' || site.siteCode === 'agent_official_site',
))
const industrySiteOptions = computed(() => publishSites.value.filter((site) =>
  site.integrationMethod !== 'brand_geo_site'
  && site.integrationMethod !== 'forum_playwright'
  && site.integrationMethod !== 'discuz_http'
  && site.siteCode !== 'agent_official_site',
))

function handleAgentSiteChange(value: string) {
  const site = agentSiteOptions.value.find((item) => item.siteCode === value)
  form.geoSiteStatus = site ? 'active' : ''
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
      brandShortName: form.brandShortName || undefined,
      status: form.status,
      industry: form.industry,
      mainBusiness: form.mainBusiness || undefined,
      coreProducts: form.coreProducts || undefined,
      brandPositioning: form.brandPositioning || undefined,
      businessIntro: form.businessIntro || undefined,
      serviceArea: serviceArea || undefined,
      provinceCode: region.provinceCode,
      provinceName: region.provinceName,
      cityCode: region.cityCode,
      cityName: region.cityName,
      districtCode: region.districtCode,
      districtName: region.districtName,
      phone: form.phone || undefined,
      publicPhone: form.publicPhone || undefined,
      publicAddress: form.publicAddress || undefined,
      wechat: form.wechat || undefined,
      website: form.website || undefined,
      geoSiteCode: form.geoSiteCode || undefined,
      geoSiteStatus: form.geoSiteCode ? form.geoSiteStatus || 'active' : undefined,
      industrySiteName: form.industrySiteName || undefined,
      industrySiteCode: form.industrySiteCode || undefined,
      brandQualificationDescription: form.brandQualificationDescription || undefined,
      brandCaseDescription: form.brandCaseDescription || undefined,
      description: form.businessIntro || undefined,
      forbiddenPhrases: form.forbiddenPhrases || undefined,
      versionChangeReason: form.versionChangeReason || undefined,
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
