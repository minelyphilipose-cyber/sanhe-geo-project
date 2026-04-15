<template>
  <div class="space-y-4">
    <el-page-header content="品牌资产" @back="$router.back()">
      <template #extra>
        <div class="space-x-2">
          <el-button link @click="router.push(`/admin/brands/${brandId}`)">品牌详情</el-button>
          <el-button link @click="router.push(`/admin/brands/${brandId}/profile`)">品牌画像</el-button>
        </div>
      </template>
    </el-page-header>

    <div class="grid grid-cols-4 gap-4">
      <el-card shadow="never" class="stat-card">
        <div class="text-sm text-gray-500 mb-1">素材总数</div>
        <div class="text-2xl font-bold text-blue-600">{{ materials.length }}</div>
        <div class="text-xs text-gray-400 mt-1">共 {{ categoryStats.length }} 个分类</div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="text-sm text-gray-500 mb-1">图片素材</div>
        <div class="text-2xl font-bold text-green-600">{{ imageCount }}</div>
        <div class="text-xs text-gray-400 mt-1">可缩略图预览</div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="text-sm text-gray-500 mb-1">存储占用</div>
        <div class="text-2xl font-bold text-purple-600">{{ totalSizeDisplay }}</div>
        <div class="text-xs text-gray-400 mt-1">单文件限制 10MB</div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="text-sm text-gray-500 mb-1">资料版本</div>
        <div class="text-2xl font-bold text-amber-600">{{ versionsTotal }}</div>
        <div class="text-xs text-gray-400 mt-1">历史快照记录</div>
      </el-card>
    </div>

    <div class="grid grid-cols-2 gap-4">
      <el-card shadow="never">
        <template #header><span class="text-sm font-medium">品牌资料完整度</span></template>
        <div class="space-y-2">
          <CheckItem label="品牌官网" :ok="!!brand?.website" />
          <CheckItem label="联系方式（电话或微信）" :ok="!!(brand?.phone || brand?.wechat)" />
          <CheckItem label="品牌简介" :ok="!!brand?.description" />
          <CheckItem label="业务介绍" :ok="!!brand?.businessIntro" />
          <CheckItem label="标准表述" :ok="!!brand?.standardBrandStatement" />
          <CheckItem label="品牌形象素材" :ok="hasCategoryMaterial('brand_image')" />
          <CheckItem label="资质文件" :ok="hasCategoryMaterial('qualification')" />
        </div>
        <div class="mt-3 pt-3 border-t border-gray-100">
          <el-progress
            :percentage="completenessPercent"
            :color="completenessPercent >= 80 ? '#059669' : completenessPercent >= 50 ? '#D97706' : '#EF4444'"
            :stroke-width="8"
          />
        </div>
      </el-card>
      <el-card shadow="never">
        <template #header><span class="text-sm font-medium">资料时效性提醒</span></template>
        <div v-if="timelinessAlerts.length === 0" class="flex items-center justify-center h-full">
          <el-result icon="success" title="资料状态良好" sub-title="暂无时效性风险" />
        </div>
        <div v-else class="space-y-2">
          <el-alert
            v-for="(alert, idx) in timelinessAlerts"
            :key="idx"
            :title="alert.title"
            :description="alert.description"
            :type="alert.type"
            show-icon
            :closable="false"
          />
        </div>
      </el-card>
    </div>

    <el-card v-loading="materialsLoading" shadow="never">
      <template #header>
        <div class="flex items-center justify-between">
          <span class="text-sm font-medium">素材管理</span>
          <div class="flex items-center gap-3">
            <el-select v-model="materialFilter" placeholder="全部分类" clearable size="small" style="width: 130px">
              <el-option v-for="cat in materialCategories" :key="cat.value" :label="cat.label" :value="cat.value" />
            </el-select>
            <el-input v-model="searchKeyword" placeholder="搜索文件名" clearable size="small" style="width: 180px" :prefix-icon="Search" />
            <el-radio-group v-model="viewMode" size="small">
              <el-radio-button value="grid">
                <el-icon><Grid /></el-icon>
              </el-radio-button>
              <el-radio-button value="list">
                <el-icon><List /></el-icon>
              </el-radio-button>
            </el-radio-group>
            <el-dropdown v-if="canWriteCompany" @command="handleUploadCategory">
              <el-button type="primary" size="small">
                上传素材
                <el-icon class="el-icon--right"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-for="cat in materialCategories" :key="cat.value" :command="cat.value">
                    {{ cat.label }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </template>

      <input ref="fileInputRef" type="file" multiple class="hidden" @change="onFileSelected" />

      <DataState :loading="materialsLoading" :empty="!materialsLoading && filteredMaterials.length === 0" empty-text="暂无素材">
        <div v-if="viewMode === 'grid'" class="grid grid-cols-5 gap-4">
          <div
            v-for="mat in filteredMaterials"
            :key="mat.id"
            class="group relative border border-gray-200 dark:border-gray-700 rounded-lg overflow-hidden hover:shadow-md transition-shadow cursor-pointer"
            @click="handleMaterialCardClick(mat)"
          >
            <div class="aspect-square bg-gray-50 dark:bg-gray-800 flex items-center justify-center overflow-hidden">
              <img
                v-if="isImageType(mat.fileType) && mat._thumbUrl"
                :src="mat._thumbUrl"
                class="w-full h-full object-cover"
                :alt="mat.fileName"
              />
              <div v-else class="flex flex-col items-center gap-2 text-gray-400">
                <el-icon :size="36"><component :is="fileIcon(mat.fileType)" /></el-icon>
                <span class="text-xs uppercase">{{ mat.fileType || '?' }}</span>
              </div>
            </div>
            <div class="p-2">
              <div class="text-xs font-medium text-gray-700 dark:text-gray-300 truncate" :title="mat.fileName">{{ mat.fileName }}</div>
              <div class="text-xs text-gray-400 mt-0.5">{{ categoryLabel(mat.category) }} · {{ formatFileSize(mat.fileSize) }}</div>
            </div>
            <div class="absolute top-1 right-1 hidden group-hover:flex gap-1">
              <el-button circle size="small" type="primary" :icon="Download" @click.stop="downloadMaterial(mat)" />
              <el-button v-if="canWriteCompany" circle size="small" type="danger" :icon="Delete" @click.stop="confirmDeleteMaterial(mat)" />
            </div>
            <el-tag class="absolute top-1 left-1" size="small" :type="categoryTagType(mat.category)" effect="dark">
              {{ categoryLabel(mat.category) }}
            </el-tag>
          </div>
        </div>

        <el-table v-if="viewMode === 'list'" :data="filteredMaterials" border>
          <el-table-column label="文件名" min-width="240">
            <template #default="{ row }">
              <div class="flex items-center gap-2">
                <img
                  v-if="isImageType(row.fileType) && row._thumbUrl"
                  :src="row._thumbUrl"
                  class="w-8 h-8 rounded object-cover shrink-0"
                />
                <el-icon v-else :size="20" color="#6B7280"><component :is="fileIcon(row.fileType)" /></el-icon>
                <span class="truncate">{{ row.fileName }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="分类" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="categoryTagType(row.category)">{{ categoryLabel(row.category) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="80">
            <template #default="{ row }">
              <span class="text-xs text-gray-500 uppercase">{{ row.fileType || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="大小" width="100">
            <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
          </el-table-column>
          <el-table-column prop="createdAt" label="上传时间" width="170" />
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="isPreviewableType(row.fileType)"
                link
                type="primary"
                :loading="row._previewing"
                @click="previewMaterial(row)"
              >
                预览
              </el-button>
              <el-button link type="primary" :loading="row._downloading" @click="downloadMaterial(row)">下载</el-button>
              <el-popconfirm v-if="canWriteCompany" title="确定删除此素材？" @confirm="removeMaterial(row)">
                <template #reference>
                  <el-button link type="danger">删除</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </DataState>

      <div v-if="materials.length > 0" class="flex items-center gap-4 mt-4 pt-3 border-t border-gray-100">
        <span class="text-xs text-gray-400">分类统计：</span>
        <span v-for="stat in categoryStats" :key="stat.category" class="text-xs text-gray-500">
          {{ stat.label }} <span class="font-medium text-gray-700">{{ stat.count }}</span>
        </span>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Download, Delete, Grid, List } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import {
  getBrandDetail,
  getBrandMaterials,
  getBrandMaterialStream,
  getBrandStatementDetail,
  uploadBrandMaterial,
  deleteBrandMaterial,
  getBrandVersions,
} from '@/api/customer'
import type { Brand, BrandMaterial, BrandStatementView } from '@/types'
import DataState from '@/components/ui/DataState.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const brandId = computed(() => Number(route.params.id))
const hasValidId = computed(() => Number.isFinite(brandId.value) && brandId.value > 0)
const canWriteCompany = computed(() => userStore.hasPermission('company.write'))

// ────────── 状态 ──────────
type BrandMaterialExt = BrandMaterial & {
  _thumbUrl?: string | null
  _previewing?: boolean
  _downloading?: boolean
}

const brand = ref<Brand | null>(null)
const statement = ref<BrandStatementView | null>(null)
const materials = ref<BrandMaterialExt[]>([])
const materialsLoading = ref(false)
const materialFilter = ref('')
const searchKeyword = ref('')
const viewMode = ref<'grid' | 'list'>('grid')
const uploadingCategory = ref('')
const fileInputRef = ref<HTMLInputElement>()
const versionsTotal = ref(0)
const thumbUrls = ref<string[]>([]) // 用于 cleanup
let thumbRequestId = 0

const MAX_UPLOAD_SIZE = 10 * 1024 * 1024

const materialCategories = [
  { label: '品牌形象', value: 'brand_image' },
  { label: '案例', value: 'case' },
  { label: '资质', value: 'qualification' },
  { label: '其他', value: 'other' },
]

// ────────── 仪表盘计算 ──────────
const imageCount = computed(() => materials.value.filter((m) => isImageType(m.fileType)).length)

const totalSizeDisplay = computed(() => {
  const total = materials.value.reduce((sum, m) => sum + (m.fileSize || 0), 0)
  return formatFileSize(total)
})

const categoryStats = computed(() => {
  const map = new Map<string, number>()
  materials.value.forEach((m) => {
    const cat = m.category || 'other'
    map.set(cat, (map.get(cat) || 0) + 1)
  })
  return Array.from(map.entries()).map(([category, count]) => ({
    category,
    label: categoryLabel(category),
    count,
  }))
})

function hasCategoryMaterial(category: string) {
  return materials.value.some((m) => m.category === category)
}

const completenessItems = computed(() => [
  !!brand.value?.website,
  !!(brand.value?.phone || brand.value?.wechat),
  !!brand.value?.description,
  !!brand.value?.businessIntro,
  !!brand.value?.standardBrandStatement,
  hasCategoryMaterial('brand_image'),
  hasCategoryMaterial('qualification'),
])

const completenessPercent = computed(() => {
  const items = completenessItems.value
  const done = items.filter(Boolean).length
  return Math.round((done / items.length) * 100)
})

const timelinessAlerts = computed(() => {
  const alerts: { title: string; description: string; type: 'warning' | 'error' | 'info' }[] = []
  if (!brand.value) return alerts

  const latest = latestUpdatedMeta.value
  if (latest?.updatedAt) {
    const daysSince = Math.floor((Date.now() - latest.updatedAt.getTime()) / (1000 * 60 * 60 * 24))
    if (daysSince > 180) {
      alerts.push({
        title: '品牌资料长期未更新',
        description: `最近一次更新来自${latest.source}，距今 ${daysSince} 天，建议尽快核查资料准确性`,
        type: 'error',
      })
    } else if (daysSince > 90) {
      alerts.push({
        title: '品牌资料更新较久',
        description: `最近一次更新来自${latest.source}，距今 ${daysSince} 天，建议安排例行复核`,
        type: 'warning',
      })
    }
  }
  if (!brand.value.website) {
    alerts.push({ title: '缺少品牌官网', description: '品牌官网是 GEO 监测的核心依据之一，建议尽快补充', type: 'warning' })
  }
  if (!brand.value.phone && !brand.value.wechat) {
    alerts.push({ title: '缺少联系方式', description: '缺少电话或微信，影响平台联系方式曝光的判定', type: 'warning' })
  }
  if (!hasCategoryMaterial('brand_image')) {
    alerts.push({ title: '缺少品牌形象素材', description: '建议上传品牌 Logo、产品图等形象素材', type: 'info' })
  }
  return alerts
})

const latestUpdatedMeta = computed(() => {
  const candidates: Array<{ time: number; source: string }> = []

  const brandTime = parseDateTime(brand.value?.updatedAt)
  if (brandTime) candidates.push({ time: brandTime.getTime(), source: '品牌信息' })

  for (const material of materials.value) {
    const materialTime = parseDateTime(material.updatedAt || material.createdAt)
    if (materialTime) candidates.push({ time: materialTime.getTime(), source: '素材' })
  }

  const statementTimes = [
    parseDateTime(statement.value?.statementGeneratedAt),
    parseDateTime(statement.value?.statementLockedAt),
  ].filter((v): v is Date => !!v)
  for (const time of statementTimes) {
    candidates.push({ time: time.getTime(), source: '标准表述' })
  }

  if (candidates.length === 0) return null
  const latest = candidates.reduce((max, cur) => (cur.time > max.time ? cur : max), candidates[0])
  return {
    updatedAt: new Date(latest.time),
    source: latest.source,
  }
})

// ────────── 素材筛选 ──────────
const filteredMaterials = computed(() => {
  let list = materials.value
  if (materialFilter.value) {
    list = list.filter((m) => m.category === materialFilter.value)
  }
  if (searchKeyword.value.trim()) {
    const kw = searchKeyword.value.trim().toLowerCase()
    list = list.filter((m) => m.fileName?.toLowerCase().includes(kw))
  }
  return list
})

// ────────── 数据加载 ──────────
async function loadBrand() {
  try {
    const { data } = await getBrandDetail(brandId.value)
    brand.value = data.data
  } catch {
    brand.value = null
  }
}

async function loadStatement() {
  try {
    const { data } = await getBrandStatementDetail(brandId.value)
    statement.value = data.data
  } catch {
    statement.value = null
  }
}

async function loadMaterials() {
  materialsLoading.value = true
  try {
    const { data } = await getBrandMaterials(brandId.value)
    materials.value = (data.data || []).sort((a, b) => {
      const bTime = parseDateTime(b.updatedAt || b.createdAt)?.getTime() ?? 0
      const aTime = parseDateTime(a.updatedAt || a.createdAt)?.getTime() ?? 0
      return bTime - aTime
    })
    // 为图片类素材生成缩略图 URL
    await loadThumbnails()
  } catch {
    materials.value = []
  } finally {
    materialsLoading.value = false
  }
}

async function loadThumbnails() {
  const currentRequestId = ++thumbRequestId
  // 清理旧的 blob URL
  cleanupThumbs()

  const targets = materials.value.filter((mat) => isImageType(mat.fileType))
  if (targets.length === 0) return

  const concurrency = Math.min(6, targets.length)
  let cursor = 0

  const worker = async () => {
    while (cursor < targets.length) {
      const mat = targets[cursor++]
      try {
        const { data: blob } = await getBrandMaterialStream(brandId.value, mat.id, false)
        const url = URL.createObjectURL(blob)
        if (currentRequestId !== thumbRequestId) {
          URL.revokeObjectURL(url)
          return
        }
        mat._thumbUrl = url
        thumbUrls.value.push(url)
      } catch {
        if (currentRequestId !== thumbRequestId) return
        mat._thumbUrl = null
      }
    }
  }

  await Promise.all(Array.from({ length: concurrency }, () => worker()))
}

function cleanupThumbs() {
  thumbUrls.value.forEach((url) => URL.revokeObjectURL(url))
  thumbUrls.value = []
}

async function loadVersionsCount() {
  try {
    const { data } = await getBrandVersions(brandId.value, { current: 1, size: 1 })
    versionsTotal.value = Number(data.data?.total || 0)
  } catch {
    versionsTotal.value = 0
  }
}

// ────────── 上传 ──────────
function handleUploadCategory(category: string) {
  uploadingCategory.value = category
  fileInputRef.value?.click()
}

async function onFileSelected(e: Event) {
  const input = e.target as HTMLInputElement
  const files = input.files
  if (!files || files.length === 0) return
  if (!uploadingCategory.value) {
    ElMessage.warning('请先选择素材分类')
    input.value = ''
    return
  }
  let successCount = 0
  for (const file of Array.from(files)) {
    if (file.size > MAX_UPLOAD_SIZE) {
      ElMessage.error(`文件「${file.name}」超过 10MB，已跳过`)
      continue
    }
    try {
      await uploadBrandMaterial(brandId.value, uploadingCategory.value, file)
      successCount++
    } catch (err: any) {
      ElMessage.error(`「${file.name}」上传失败：${err?.response?.data?.message || '未知错误'}`)
    }
  }
  input.value = ''
  if (successCount > 0) {
    ElMessage.success(`成功上传 ${successCount} 个文件`)
    await loadMaterials()
  }
}

// ────────── 预览/下载（blob 方案，带 JWT） ──────────
async function previewMaterial(row: BrandMaterialExt) {
  if (!isPreviewableType(row.fileType)) {
    ElMessage.warning('该类型文件不支持预览，请直接下载')
    return
  }

  const win = window.open('about:blank', '_blank')
  if (!win) {
    ElMessage.warning('浏览器拦截了预览窗口，请允许弹窗后重试')
    return
  }

  row._previewing = true
  try {
    const { data: blob } = await getBrandMaterialStream(brandId.value, row.id, false)
    const url = URL.createObjectURL(blob)
    win.location.href = url
    setTimeout(() => URL.revokeObjectURL(url), 60000)
  } catch (e: any) {
    win.close()
    ElMessage.error(e?.response?.data?.message || '预览失败')
  } finally {
    row._previewing = false
  }
}

function handleMaterialCardClick(row: BrandMaterialExt) {
  if (isPreviewableType(row.fileType)) {
    void previewMaterial(row)
    return
  }
  void downloadMaterial(row)
}

async function downloadMaterial(row: BrandMaterialExt) {
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

async function confirmDeleteMaterial(mat: BrandMaterialExt) {
  try {
    await ElMessageBox.confirm(`确定删除素材「${mat.fileName}」？`, '删除确认', { type: 'warning' })
    await removeMaterial(mat)
  } catch {
    // cancelled
  }
}

async function removeMaterial(row: BrandMaterial) {
  try {
    await deleteBrandMaterial(brandId.value, row.id)
    ElMessage.success('已删除')
    await loadMaterials()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '删除失败')
  }
}

// ────────── 工具函数 ──────────
function isImageType(fileType?: string | null) {
  if (!fileType) return false
  return ['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg'].includes(fileType.toLowerCase())
}

function isPreviewableType(fileType?: string | null) {
  if (!fileType) return false
  const t = fileType.toLowerCase()
  return isImageType(t) || t === 'pdf'
}

function fileIcon(fileType?: string | null) {
  if (!fileType) return 'Document'
  const t = fileType.toLowerCase()
  if (isImageType(t)) return 'Picture'
  if (t === 'pdf') return 'Document'
  if (['doc', 'docx'].includes(t)) return 'Notebook'
  if (['xls', 'xlsx'].includes(t)) return 'Grid'
  if (['mp4', 'avi', 'mov'].includes(t)) return 'VideoPlay'
  return 'Document'
}

function categoryLabel(cat: string) {
  return materialCategories.find((c) => c.value === cat)?.label || cat
}

function categoryTagType(cat: string): '' | 'success' | 'warning' | 'info' | 'danger' {
  const map: Record<string, '' | 'success' | 'warning' | 'info' | 'danger'> = {
    brand_image: '',
    case: 'success',
    qualification: 'warning',
    other: 'info',
  }
  return map[cat] || 'info'
}

function formatFileSize(bytes?: number | null) {
  if (!bytes) return '0 B'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
  return (bytes / (1024 * 1024 * 1024)).toFixed(2) + ' GB'
}

function parseDateTime(value?: string | null) {
  if (!value) return null
  const time = new Date(value)
  return Number.isNaN(time.getTime()) ? null : time
}

// ────────── 初始化 ──────────
async function loadAll() {
  if (!hasValidId.value) {
    ElMessage.error('品牌参数无效')
    return
  }
  thumbRequestId++
  cleanupThumbs()
  await Promise.all([loadBrand(), loadStatement(), loadMaterials(), loadVersionsCount()])
}

onMounted(loadAll)

watch(() => route.params.id, (newId, oldId) => {
  if (newId !== oldId && newId) {
    materialFilter.value = ''
    searchKeyword.value = ''
    viewMode.value = 'grid'
    thumbRequestId++
    cleanupThumbs()
    void loadAll()
  }
})

onBeforeUnmount(() => {
  thumbRequestId++
  cleanupThumbs()
})
</script>

<script lang="ts">
import { defineComponent, h } from 'vue'
import { CircleCheckFilled, WarningFilled } from '@element-plus/icons-vue'
import { ElIcon } from 'element-plus'

const CheckItem = defineComponent({
  name: 'CheckItem',
  props: {
    label: { type: String, default: '' },
    ok: { type: Boolean, default: false },
  },
  setup(props) {
    return () =>
      h('div', { class: 'flex items-center gap-2' }, [
        h(ElIcon, { size: 16, color: props.ok ? '#059669' : '#D97706' }, () =>
          h(props.ok ? CircleCheckFilled : WarningFilled),
        ),
        h('span', { class: `text-sm ${props.ok ? 'text-gray-600' : 'text-amber-600'}` }, props.label),
      ])
  },
})

export { CheckItem }
export default {}
</script>

<style scoped>
.stat-card :deep(.el-card__body) {
  padding: 16px;
}
</style>
