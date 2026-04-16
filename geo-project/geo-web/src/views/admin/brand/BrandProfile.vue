<template>
  <div class="space-y-4">
    <el-page-header content="品牌画像" @back="$router.back()">
      <template #extra>
        <div class="space-x-2">
          <el-button link @click="router.push(`/admin/brands/${brandId}`)">返回品牌详情</el-button>
          <el-button link @click="router.push(`/admin/brands/${brandId}/assets`)">品牌资产</el-button>
        </div>
      </template>
    </el-page-header>

    <el-card v-loading="loading">
      <template #header>
        <div class="flex items-center gap-2">
          <span class="text-base font-medium">{{ brand?.brandName || '—' }}</span>
          <el-tag v-if="brand?.status" size="small">{{ dictStore.label('brand_status', brand?.status) }}</el-tag>
          <el-tag v-if="brand?.industry" size="small" type="info">{{ industryLabel(brand?.industry) }}</el-tag>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <!-- ═══════ Tab 1: 基础资料 ═══════ -->
        <el-tab-pane label="基础资料" name="info">
          <div class="flex items-center justify-between mb-4">
            <span class="text-sm text-gray-500">品牌基础信息与联系方式</span>
            <div v-if="!editingInfo && canWriteCompany">
              <el-button type="primary" link @click="startEditInfo">编辑</el-button>
            </div>
            <div v-if="editingInfo" class="space-x-2">
              <el-button @click="cancelEditInfo">取消</el-button>
              <el-button type="primary" :loading="savingInfo" @click="saveInfo">保存</el-button>
            </div>
          </div>

          <!-- 只读模式 -->
          <el-descriptions v-if="!editingInfo" :column="3" border>
            <el-descriptions-item label="品牌名称">{{ brand?.brandName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="品牌标识">{{ brand?.brandSlug || '-' }}</el-descriptions-item>
            <el-descriptions-item label="品牌行业">{{ industryLabel(brand?.industry) }}</el-descriptions-item>
            <el-descriptions-item label="主营业务">{{ brand?.mainBusiness || '-' }}</el-descriptions-item>
            <el-descriptions-item label="所在地区">{{ regionText }}</el-descriptions-item>
            <el-descriptions-item label="服务区域">{{ brand?.serviceArea || '-' }}</el-descriptions-item>
            <el-descriptions-item label="官网">
              <a v-if="brand?.website" :href="normalizeUrl(brand.website)" target="_blank" class="text-blue-600 hover:underline">{{ brand.website }}</a>
              <span v-else>-</span>
            </el-descriptions-item>
            <el-descriptions-item label="联系电话">{{ brand?.phone || '-' }}</el-descriptions-item>
            <el-descriptions-item label="微信">{{ brand?.wechat || '-' }}</el-descriptions-item>
            <el-descriptions-item label="公众号">{{ brand?.officialAccount || '-' }}</el-descriptions-item>
            <el-descriptions-item label="视频号">{{ brand?.videoAccount || '-' }}</el-descriptions-item>
            <el-descriptions-item label="抖音号">{{ brand?.douyinAccount || '-' }}</el-descriptions-item>
            <el-descriptions-item label="品牌简介" :span="3">{{ brand?.description || '-' }}</el-descriptions-item>
            <el-descriptions-item label="业务介绍" :span="3">{{ brand?.businessIntro || '-' }}</el-descriptions-item>
            <el-descriptions-item label="禁用词" :span="3">{{ brand?.forbiddenPhrases || '-' }}</el-descriptions-item>
          </el-descriptions>

          <!-- 编辑模式 -->
          <el-form v-if="editingInfo" ref="infoFormRef" :model="infoForm" :rules="infoRules" label-width="120px">
            <div class="grid grid-cols-2 gap-x-6">
              <el-form-item label="品牌名称" prop="brandName" required>
                <el-input v-model="infoForm.brandName" />
              </el-form-item>
              <el-form-item label="品牌标识" prop="brandSlug" required>
                <el-input v-model="infoForm.brandSlug" />
              </el-form-item>
              <el-form-item label="品牌行业" prop="industry" required>
                <el-select v-model="infoForm.industry" filterable style="width: 100%">
                  <el-option
                    v-for="tag in availableIndustries"
                    :key="tag"
                    :label="industryLabel(tag)"
                    :value="tag"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="主营业务">
                <el-input v-model="infoForm.mainBusiness" />
              </el-form-item>
              <el-form-item label="地区">
                <RegionCascader v-model="infoForm.regionCodes" />
              </el-form-item>
              <el-form-item label="官网">
                <el-input v-model="infoForm.website" />
              </el-form-item>
              <el-form-item label="联系电话">
                <el-input v-model="infoForm.phone" />
              </el-form-item>
              <el-form-item label="微信">
                <el-input v-model="infoForm.wechat" />
              </el-form-item>
              <el-form-item label="公众号">
                <el-input v-model="infoForm.officialAccount" />
              </el-form-item>
              <el-form-item label="视频号">
                <el-input v-model="infoForm.videoAccount" />
              </el-form-item>
              <el-form-item label="抖音号">
                <el-input v-model="infoForm.douyinAccount" />
              </el-form-item>
              <el-form-item label="状态" required>
                <el-select v-model="infoForm.status" style="width: 100%">
                  <el-option
                    v-for="item in dictStore.options('brand_status')"
                    :key="item.dictKey"
                    :label="item.dictValue"
                    :value="item.dictKey"
                  />
                </el-select>
              </el-form-item>
            </div>
            <el-form-item label="品牌简介">
              <el-input v-model="infoForm.description" type="textarea" :rows="3" />
            </el-form-item>
            <el-form-item label="业务介绍">
              <el-input v-model="infoForm.businessIntro" type="textarea" :rows="3" />
            </el-form-item>
            <el-form-item label="禁用词">
              <el-input v-model="infoForm.forbiddenPhrases" type="textarea" :rows="2" placeholder="多个禁用词用逗号分隔" />
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- ═══════ Tab 2: 标准表述（只读快捷查看） ═══════ -->
        <el-tab-pane label="标准表述" name="statement">
          <div class="flex items-center justify-between mb-4">
            <div class="flex items-center gap-2">
              <span class="text-sm text-gray-500">品牌标准表达快捷查看</span>
              <el-tag v-if="statement?.statementStatus" size="small" :type="statementTagType">{{ statementStatusLabel }}</el-tag>
              <el-tag v-if="statement?.statementVersion" size="small" type="info">v{{ statement?.statementVersion }}</el-tag>
            </div>
            <el-button type="primary" link @click="router.push(`/admin/brands/${brandId}`)">
              前往品牌详情编辑表述
            </el-button>
          </div>

          <el-descriptions :column="1" border>
            <el-descriptions-item label="一句话定位">{{ statement?.standardStatement?.positioning || '-' }}</el-descriptions-item>
            <el-descriptions-item label="核心卖点">
              <div v-if="statement?.standardStatement?.selling_points?.length" class="flex flex-wrap gap-2">
                <el-tag v-for="(point, idx) in statement?.standardStatement?.selling_points" :key="idx" type="info">{{ point }}</el-tag>
              </div>
              <span v-else>-</span>
            </el-descriptions-item>
            <el-descriptions-item label="差异化表达">{{ statement?.standardStatement?.differentiation || '-' }}</el-descriptions-item>
            <el-descriptions-item label="推荐品牌介绍段落">{{ statement?.standardStatement?.brand_paragraph || '-' }}</el-descriptions-item>
          </el-descriptions>

          <el-descriptions class="mt-4" :column="2" border>
            <el-descriptions-item label="品牌标准表述">{{ brand?.standardBrandStatement || '-' }}</el-descriptions-item>
            <el-descriptions-item label="业务标准表述">{{ brand?.businessStandardStatement || '-' }}</el-descriptions-item>
            <el-descriptions-item label="生成时间">{{ statement?.statementGeneratedAt || '-' }}</el-descriptions-item>
            <el-descriptions-item label="锁定时间">{{ statement?.statementLockedAt || '-' }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>

        <!-- ═══════ Tab 3: 素材与版本 ═══════ -->
        <el-tab-pane label="素材与版本" name="materials">
          <div class="flex items-center justify-between mb-3">
            <span class="text-sm text-gray-500">最近素材（5 条）</span>
            <el-button type="primary" link @click="router.push(`/admin/brands/${brandId}/assets`)">查看全部</el-button>
          </div>
          <DataState :loading="materialsLoading" :empty="!materialsLoading && recentMaterials.length === 0" empty-text="暂无素材">
            <el-table :data="recentMaterials" border>
              <el-table-column label="文件名" min-width="220">
                <template #default="{ row }">
                  <div class="flex items-center gap-2">
                    <el-icon :size="16" color="#6B7280"><component :is="fileIcon(row.fileType)" /></el-icon>
                    <span class="truncate">{{ row.fileName }}</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="分类" width="110">
                <template #default="{ row }">{{ categoryLabel(row.category) }}</template>
              </el-table-column>
              <el-table-column label="大小" width="100">
                <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
              </el-table-column>
              <el-table-column prop="createdAt" label="上传时间" width="170" />
              <el-table-column label="操作" width="120" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" :loading="row._previewing" @click="previewMaterial(row)">预览</el-button>
                  <el-button link type="primary" :loading="row._downloading" @click="downloadMaterial(row)">下载</el-button>
                </template>
              </el-table-column>
            </el-table>
          </DataState>

          <el-divider />
          <div class="flex items-center justify-between mb-3">
            <span class="text-sm text-gray-500">最近版本（5 条）</span>
            <el-button type="primary" link @click="router.push(`/admin/brands/${brandId}/assets`)">查看全部</el-button>
          </div>
          <DataState :loading="versionsLoading" :empty="!versionsLoading && recentVersions.length === 0" empty-text="暂无版本记录">
            <el-table :data="recentVersions" border>
              <el-table-column label="版本号" width="100">
                <template #default="{ row }">v{{ row.versionNo }}</template>
              </el-table-column>
              <el-table-column prop="changeReason" label="变更原因" min-width="180">
                <template #default="{ row }">{{ row.changeReason || '快照' }}</template>
              </el-table-column>
              <el-table-column prop="createdAt" label="创建时间" width="180" />
            </el-table>
          </DataState>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { useDictStore } from '@/stores/dict'
import {
  getBrandDetail,
  updateBrand,
  getCompanyDetail,
  getBrandStatementDetail,
  getBrandMaterials,
  getBrandMaterialStream,
  getBrandVersions,
} from '@/api/customer'
import type { Brand, BrandStatementView, BrandMaterial, BrandProfileVersion } from '@/types'
import DataState from '@/components/ui/DataState.vue'
import RegionCascader from '@/components/ui/RegionCascader.vue'
import { regionCodesFromPayload, regionDisplayFromPayload, regionPayloadFromCodes } from '@/constants/region'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const dictStore = useDictStore()

// ────────── [FIX-2] brandId 响应式 ──────────
const brandId = computed(() => Number(route.params.id))
const hasValidId = computed(() => Number.isFinite(brandId.value) && brandId.value > 0)
const canWriteCompany = computed(() => userStore.hasPermission('company.write'))

// ────────── 页面状态 ──────────
const loading = ref(false)
const activeTab = ref('info')
const brand = ref<Brand | null>(null)
const statement = ref<BrandStatementView | null>(null)
const companyIndustryTags = ref<string[]>([])

// ────────── Tab 1: 基础资料 ──────────
const editingInfo = ref(false)
const savingInfo = ref(false)
const infoFormRef = ref<FormInstance>()
const infoForm = reactive({
  brandName: '',
  brandSlug: '',
  industry: '',
  mainBusiness: '',
  regionCodes: [] as string[],
  website: '',
  phone: '',
  wechat: '',
  officialAccount: '',
  videoAccount: '',
  douyinAccount: '',
  status: 'active',
  description: '',
  businessIntro: '',
  forbiddenPhrases: '',
})

const infoRules: FormRules = {
  brandName: [{ required: true, message: '请输入品牌名称', trigger: 'blur' }],
  brandSlug: [
    { required: true, message: '请输入品牌标识', trigger: 'blur' },
    { pattern: /^[a-z0-9][a-z0-9_-]{1,127}$/, message: '标识需小写字母数字开头，可含 _ -', trigger: 'blur' },
  ],
  industry: [{ required: true, message: '请选择品牌行业', trigger: 'change' }],
}

const availableIndustries = computed(() => companyIndustryTags.value)

const regionText = computed(() => {
  if (!brand.value) return '-'
  return regionDisplayFromPayload(brand.value) || brand.value.serviceArea || '-'
})

function industryLabel(value?: string | null) {
  if (!value) return '-'
  return dictStore.label('industry_tag', value) || value
}

function normalizeUrl(url: string) {
  return url.startsWith('http') ? url : `https://${url}`
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

function fillInfoForm(data: Brand) {
  infoForm.brandName = data.brandName
  infoForm.brandSlug = data.brandSlug
  infoForm.industry = data.industry || availableIndustries.value[0] || ''
  infoForm.mainBusiness = data.mainBusiness || ''
  infoForm.regionCodes = regionCodesFromPayload(data)
  infoForm.website = data.website || ''
  infoForm.phone = data.phone || ''
  infoForm.wechat = data.wechat || ''
  infoForm.officialAccount = data.officialAccount || ''
  infoForm.videoAccount = data.videoAccount || ''
  infoForm.douyinAccount = data.douyinAccount || ''
  infoForm.status = data.status || 'active'
  infoForm.description = data.description || ''
  infoForm.businessIntro = data.businessIntro || ''
  infoForm.forbiddenPhrases = Array.isArray(data.forbiddenPhrases)
    ? data.forbiddenPhrases.join('，')
    : (data.forbiddenPhrases || '')
}

function startEditInfo() {
  if (brand.value) fillInfoForm(brand.value)
  editingInfo.value = true
}

function cancelEditInfo() {
  editingInfo.value = false
}

async function saveInfo() {
  const valid = await infoFormRef.value?.validate().catch(() => false)
  if (!valid) return
  savingInfo.value = true
  try {
    const region = regionPayloadFromCodes(infoForm.regionCodes)
    await updateBrand(brandId.value, {
      companyId: brand.value?.companyId,
      brandName: infoForm.brandName,
      brandSlug: infoForm.brandSlug,
      industry: infoForm.industry,
      mainBusiness: infoForm.mainBusiness || undefined,
      serviceArea: region.displayName,
      provinceCode: region.provinceCode,
      provinceName: region.provinceName,
      cityCode: region.cityCode,
      cityName: region.cityName,
      districtCode: region.districtCode,
      districtName: region.districtName,
      website: infoForm.website || undefined,
      phone: infoForm.phone || undefined,
      wechat: infoForm.wechat || undefined,
      officialAccount: infoForm.officialAccount || undefined,
      videoAccount: infoForm.videoAccount || undefined,
      douyinAccount: infoForm.douyinAccount || undefined,
      status: infoForm.status,
      description: infoForm.description || undefined,
      businessIntro: infoForm.businessIntro || undefined,
      forbiddenPhrases: infoForm.forbiddenPhrases || undefined,
    })
    ElMessage.success('品牌信息已更新')
    editingInfo.value = false
    await loadBrand()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    savingInfo.value = false
  }
}

// ────────── Tab 2: 标准表述（只读） ──────────
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

async function loadStatement() {
  try {
    const { data } = await getBrandStatementDetail(brandId.value)
    statement.value = data.data
  } catch {
    statement.value = null
  }
}

// ────────── Tab 3: 素材与版本 ──────────
const materialCategories = [
  { label: '品牌形象', value: 'brand_image' },
  { label: '案例', value: 'case' },
  { label: '资质', value: 'qualification' },
  { label: '其他', value: 'other' },
]

const materials = ref<BrandMaterial[]>([])
const materialsLoading = ref(false)
const recentMaterials = computed(() => materials.value.slice(0, 5))

async function loadMaterials() {
  materialsLoading.value = true
  try {
    const { data } = await getBrandMaterials(brandId.value)
    materials.value = (data.data || []).sort((a, b) => {
      const aTime = new Date(a.createdAt).getTime()
      const bTime = new Date(b.createdAt).getTime()
      return bTime - aTime
    })
  } catch {
    materials.value = []
  } finally {
    materialsLoading.value = false
  }
}

// ────────── [FIX-1] 素材预览/下载走 API blob 方案，带 JWT 鉴权 ──────────
async function previewMaterial(row: any) {
  const previewWindow = window.open('about:blank', '_blank')
  if (!previewWindow) {
    ElMessage.warning('浏览器拦截了预览窗口，请允许弹窗后重试')
    return
  }

  row._previewing = true
  try {
    const { data: blob } = await getBrandMaterialStream(brandId.value, row.id, false)
    const url = URL.createObjectURL(blob)
    previewWindow.location.href = url
    // 延迟释放，给浏览器足够时间加载
    setTimeout(() => URL.revokeObjectURL(url), 60000)
  } catch (e: any) {
    previewWindow.close()
    ElMessage.error(e?.response?.data?.message || '预览失败')
  } finally {
    row._previewing = false
  }
}

async function downloadMaterial(row: any) {
  row._downloading = true
  try {
    const { data: blob } = await getBrandMaterialStream(brandId.value, row.id, true)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = row.fileName || 'download'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    setTimeout(() => URL.revokeObjectURL(url), 5000)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '下载失败')
  } finally {
    row._downloading = false
  }
}

// 版本历史
const versions = ref<BrandProfileVersion[]>([])
const versionsLoading = ref(false)
const recentVersions = computed(() => versions.value.slice(0, 5))

async function loadVersions() {
  versionsLoading.value = true
  try {
    const { data } = await getBrandVersions(brandId.value, { current: 1, size: 5 })
    const page = data.data
    versions.value = page?.records || []
  } catch {
    versions.value = []
  } finally {
    versionsLoading.value = false
  }
}

function categoryLabel(cat: string) {
  return materialCategories.find((c) => c.value === cat)?.label || cat
}

function fileIcon(fileType?: string) {
  if (!fileType) return 'Document'
  const t = fileType.toLowerCase()
  if (['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg'].includes(t)) return 'Picture'
  if (t === 'pdf') return 'Document'
  if (['doc', 'docx'].includes(t)) return 'Notebook'
  if (['xls', 'xlsx'].includes(t)) return 'Grid'
  if (['mp4', 'avi', 'mov'].includes(t)) return 'VideoPlay'
  return 'Document'
}

function formatFileSize(bytes?: number | null) {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

// ────────── 初始化与数据加载 ──────────
async function loadBrand() {
  let brandData: Brand | null = null
  try {
    const { data } = await getBrandDetail(brandId.value)
    brandData = data.data
    brand.value = brandData
    fillInfoForm(brandData)
  } catch {
    brand.value = null
    return
  }

  companyIndustryTags.value = []
  if (brandData?.companyId) {
    try {
      const companyRes = await getCompanyDetail(brandData.companyId)
      companyIndustryTags.value = parseIndustryTags((companyRes.data.data as any).industryTags)
      if (!infoForm.industry) {
        infoForm.industry = companyIndustryTags.value[0] || ''
      }
    } catch {
      companyIndustryTags.value = []
    }
  }
}

async function loadAll() {
  if (!hasValidId.value) {
    ElMessage.error('品牌参数无效')
    return
  }
  loading.value = true
  editingInfo.value = false
  await dictStore.ensureLoaded()
  await loadBrand()
  await Promise.all([loadStatement(), loadMaterials(), loadVersions()])
  loading.value = false
}

onMounted(loadAll)

// ────────── [FIX-2] 路由参数变化时自动刷新 ──────────
watch(() => route.params.id, (newId, oldId) => {
  if (newId !== oldId && newId) {
    activeTab.value = 'info'
    loadAll()
  }
})

watch(activeTab, (tab) => {
  if (tab === 'statement') loadStatement()
  if (tab === 'materials') {
    loadMaterials()
    loadVersions()
  }
})
</script>
