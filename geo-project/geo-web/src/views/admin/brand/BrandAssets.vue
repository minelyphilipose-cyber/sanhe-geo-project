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
          <CheckItem label="联系方式（公开电话/公开地址/电话/微信）" :ok="hasContactInfo" />
          <CheckItem label="业务介绍" :ok="!!brand?.businessIntro" />
          <CheckItem label="品牌资质描述" :ok="!!brand?.brandQualificationDescription" />
          <CheckItem label="品牌案例描述" :ok="!!brand?.brandCaseDescription" />
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

    <el-card v-loading="foldersLoading" shadow="never">
      <template #header>
        <div class="flex items-center justify-between">
          <span class="text-sm font-medium">图库文件夹</span>
          <el-button v-if="canUploadMaterial" type="primary" size="small" @click="openCreateFolder">新建文件夹</el-button>
        </div>
      </template>
      <DataState :loading="foldersLoading" :empty="!foldersLoading && imageFolders.length === 0" empty-text="暂无图库文件夹">
        <div class="grid grid-cols-4 gap-3">
          <div
            v-for="folder in imageFolders"
            :key="folder.id"
            class="folder-card"
            :class="{ 'folder-card-active': selectedFolderId === folder.id }"
            @click="selectFolder(folder.id)"
          >
            <div class="flex items-start justify-between gap-2">
              <div class="min-w-0">
                <div class="font-medium truncate">{{ folder.folderName }}</div>
                <div class="text-xs text-gray-400 mt-1">{{ folderImageCount(folder.id) }} 张图片</div>
              </div>
              <el-tag size="small" :type="folder.status === 'active' ? 'success' : 'info'">
                {{ folder.status === 'active' ? '启用' : '停用' }}
              </el-tag>
            </div>
            <div v-if="folder.tags?.length" class="flex flex-wrap gap-1 mt-2">
              <el-tag v-for="tag in folder.tags" :key="tag" size="small" type="info">{{ tag }}</el-tag>
            </div>
            <div v-if="folder.projectIds?.length" class="text-xs text-gray-500 mt-2">关联项目：{{ formatFolderProjects(folder.projectIds) }}</div>
            <div class="flex justify-end gap-2 mt-3">
              <el-button v-if="canUploadMaterial" size="small" @click.stop="openEditFolder(folder)">编辑</el-button>
            </div>
          </div>
        </div>
      </DataState>
    </el-card>

    <el-card v-loading="materialsLoading" shadow="never">
      <template #header>
        <div class="flex items-center justify-between">
          <div>
            <span class="text-sm font-medium">{{ selectedFolderName }}图片</span>
            <span class="ml-2 text-xs text-gray-400">共 {{ selectedFolderMaterials.length }} 张</span>
          </div>
          <div class="flex items-center gap-3">
            <el-input v-model="searchKeyword" placeholder="搜索文件名" clearable size="small" style="width: 180px" :prefix-icon="Search" />
            <el-radio-group v-model="viewMode" size="small">
              <el-radio-button value="grid">
                <el-icon><Grid /></el-icon>
              </el-radio-button>
              <el-radio-button value="list">
                <el-icon><List /></el-icon>
              </el-radio-button>
            </el-radio-group>
            <el-button
              v-if="canUploadMaterial"
              type="primary"
              size="small"
              :loading="uploadInProgress"
              :disabled="uploadInProgress"
              @click="handleImageUpload"
            >
              上传图片
            </el-button>
          </div>
        </div>
      </template>

      <input ref="fileInputRef" type="file" multiple class="hidden" :accept="uploadAccept" :disabled="uploadInProgress" @change="onFileSelected" />

      <div v-if="uploadInProgress" class="upload-progress-panel">
        <div class="upload-progress-panel__header">
          <div>
            <div class="upload-progress-panel__title">{{ uploadProgressTitle }}</div>
            <div class="upload-progress-panel__desc">{{ uploadProgressDescription }}</div>
          </div>
          <el-tag size="small" type="warning">{{ uploadCompleted }}/{{ uploadTotal }}</el-tag>
        </div>
        <el-progress
          :percentage="uploadPercent"
          :stroke-width="8"
          :indeterminate="uploadInProgress && uploadPercent < 100"
        />
      </div>

      <DataState :loading="materialsLoading" :empty="!materialsLoading && visibleMaterials.length === 0" empty-text="暂无图片">
        <div v-if="viewMode === 'grid'" class="grid grid-cols-5 gap-4">
          <div
            v-for="mat in visibleMaterials"
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
              <div class="text-xs text-gray-400 mt-0.5">{{ folderName(mat.folderId) }} · {{ formatFileSize(mat.fileSize) }}</div>
            </div>
            <div class="absolute top-1 right-1 hidden group-hover:flex gap-1">
              <el-button circle size="small" type="primary" :icon="Download" @click.stop="downloadMaterial(mat)" />
              <el-button v-if="canDeleteMaterial" circle size="small" type="danger" :icon="Delete" @click.stop="confirmDeleteMaterial(mat)" />
            </div>
          </div>
        </div>

        <el-table v-if="viewMode === 'list'" :data="visibleMaterials" border>
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
          <el-table-column label="文件夹" width="140">
            <template #default="{ row }">{{ folderName(row.folderId) }}</template>
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
              <el-popconfirm v-if="canDeleteMaterial" title="确定删除此素材？" @confirm="removeMaterial(row)">
                <template #reference>
                  <el-button link type="danger">删除</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>

        <div v-if="selectedFolderMaterials.length > MATERIAL_COLLAPSED_LIMIT" class="folder-toggle">
          <button type="button" class="folder-toggle-btn" @click="folderExpanded = !folderExpanded">
            <span>{{ folderExpanded ? '收起' : '加载更多' }}</span>
            <span class="folder-toggle-chevrons" :class="{ 'folder-toggle-chevrons-up': folderExpanded }">
              <el-icon><ArrowDown /></el-icon>
              <el-icon><ArrowDown /></el-icon>
            </span>
          </button>
        </div>
      </DataState>

      <div v-if="materials.length > 0" class="flex items-center gap-4 mt-4 pt-3 border-t border-gray-100">
        <span class="text-xs text-gray-400">分类统计：</span>
        <span v-for="stat in categoryStats" :key="stat.category" class="text-xs text-gray-500">
          {{ stat.label }} <span class="font-medium text-gray-700">{{ stat.count }}</span>
        </span>
      </div>
    </el-card>

    <el-card v-loading="materialsLoading" shadow="never">
      <template #header>
        <div class="flex items-center justify-between">
          <div>
            <span class="text-sm font-medium">案例与资质资料</span>
            <span class="ml-2 text-xs text-gray-400">共 {{ documentMaterials.length }} 个</span>
          </div>
          <el-dropdown v-if="canUploadMaterial" :disabled="uploadInProgress" @command="handleDocumentUploadCategory">
            <el-button type="primary" size="small" :loading="uploadInProgress" :disabled="uploadInProgress">
              上传资料
              <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-for="cat in documentCategories" :key="cat.value" :command="cat.value">
                  {{ cat.label }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </template>
      <DataState :loading="materialsLoading" :empty="!materialsLoading && documentMaterials.length === 0" empty-text="暂无资料">
        <el-table :data="documentMaterials" border>
          <el-table-column label="文件名" min-width="260">
            <template #default="{ row }">
              <div class="flex items-center gap-2">
                <el-icon :size="20" color="#6B7280"><component :is="fileIcon(row.fileType)" /></el-icon>
                <span class="truncate">{{ row.fileName }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="categoryTagType(row.category)">{{ categoryLabel(row.category) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="格式" width="90">
            <template #default="{ row }">
              <span class="text-xs text-gray-500 uppercase">{{ row.fileType || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="大小" width="110">
            <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
          </el-table-column>
          <el-table-column prop="createdAt" label="上传时间" width="170" />
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button v-if="isPreviewableType(row.fileType)" link type="primary" :loading="row._previewing" @click="previewMaterial(row)">预览</el-button>
              <el-button link type="primary" :loading="row._downloading" @click="downloadMaterial(row)">下载</el-button>
              <el-popconfirm v-if="canDeleteMaterial" title="确定删除此资料？" @confirm="removeMaterial(row)">
                <template #reference>
                  <el-button link type="danger">删除</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </DataState>
    </el-card>

    <el-dialog v-model="folderDialogVisible" :title="editingFolderId ? '编辑图库文件夹' : '新建图库文件夹'" width="520px">
      <el-form :model="folderForm" label-width="90px">
        <el-form-item label="名称" required>
          <el-input v-model="folderForm.folderName" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="folderForm.status">
            <el-radio-button label="active">启用</el-radio-button>
            <el-radio-button label="disabled">停用</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="标签">
          <el-select
            v-model="folderForm.tags"
            multiple
            filterable
            allow-create
            default-first-option
            remote
            reserve-keyword
            :remote-method="searchFolderTags"
            placeholder="输入或选择标签，单个不超过10字"
            style="width: 100%"
          >
            <el-option v-for="tag in folderTagOptions" :key="tag" :label="tag" :value="tag" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联项目">
          <el-select
            v-model="folderForm.projectIds"
            multiple
            filterable
            clearable
            :loading="projectsLoading"
            placeholder="请选择当前品牌项目"
            style="width: 100%"
          >
            <el-option
              v-for="project in brandProjects"
              :key="project.id"
              :label="project.projectName"
              :value="project.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="folderForm.description" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="folderDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="folderSaving" @click="saveFolder">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Download, Delete, Grid, List, ArrowDown } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import {
  getBrandDetail,
  getBrandImageFolders,
  getBrandMaterials,
  getBrandMaterialPreviewUrl,
  getBrandMaterialStream,
  getBrandStatementDetail,
  uploadBrandMaterial,
  deleteBrandMaterial,
  getBrandVersions,
  createBrandImageFolder,
  updateBrandImageFolder,
  suggestBrandImageFolderTags,
} from '@/api/customer'
import { getProjectList } from '@/api/project'
import type { Brand, BrandImageFolder, BrandMaterial, BrandStatementView, Project } from '@/types'
import DataState from '@/components/ui/DataState.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const brandId = computed(() => Number(route.params.id))
const hasValidId = computed(() => Number.isFinite(brandId.value) && brandId.value > 0)
const canUploadMaterial = computed(() => userStore.hasPermission('brand.material.upload'))
const canDeleteMaterial = computed(() => userStore.hasPermission('brand.material.delete'))

// ────────── 状态 ──────────
type BrandMaterialExt = BrandMaterial & {
  _thumbUrl?: string | null
  _thumbLoading?: boolean
  _thumbFailed?: boolean
  _previewing?: boolean
  _downloading?: boolean
}

const brand = ref<Brand | null>(null)
const statement = ref<BrandStatementView | null>(null)
const materials = ref<BrandMaterialExt[]>([])
const imageFolders = ref<BrandImageFolder[]>([])
const brandProjects = ref<Project[]>([])
const foldersLoading = ref(false)
const materialsLoading = ref(false)
const projectsLoading = ref(false)
const selectedFolderId = ref<number | null>(null)
const folderExpanded = ref(false)
const searchKeyword = ref('')
const viewMode = ref<'grid' | 'list'>('grid')
const uploadingCategory = ref('')
const uploadMode = ref<'image' | 'document'>('image')
const uploadInProgress = ref(false)
const uploadTotal = ref(0)
const uploadCompleted = ref(0)
const uploadFailed = ref(0)
const uploadCurrentFileName = ref('')
const uploadPhase = ref<'uploading' | 'refreshing'>('uploading')
const fileInputRef = ref<HTMLInputElement>()
const versionsTotal = ref(0)
const folderDialogVisible = ref(false)
const folderSaving = ref(false)
const editingFolderId = ref<number | null>(null)

type FolderForm = {
  folderName: string
  status: string
  description: string
  tags: string[]
  projectIds: number[]
}

const folderForm = ref<FolderForm>({
  folderName: '',
  status: 'active',
  description: '',
  tags: [],
  projectIds: [],
})
const folderTagOptions = ref<string[]>([])
const thumbUrls = ref<string[]>([]) // 用于 cleanup
let thumbRequestId = 0

const MAX_UPLOAD_SIZE = 10 * 1024 * 1024
const MATERIAL_COLLAPSED_LIMIT = 4

const materialCategories = [
  { label: '品牌形象', value: 'brand_image' },
  { label: '案例', value: 'case' },
  { label: '资质', value: 'qualification' },
  { label: '其他', value: 'other' },
]

const documentCategories = materialCategories.filter((cat) => cat.value !== 'brand_image')

const uploadPercent = computed(() => {
  if (uploadTotal.value <= 0) return 0
  return Math.min(100, Math.round((uploadCompleted.value / uploadTotal.value) * 100))
})

const uploadProgressTitle = computed(() => (
  uploadPhase.value === 'refreshing'
    ? '上传完成，正在刷新图库'
    : uploadMode.value === 'image'
      ? '正在上传图片并压缩'
      : '正在上传资料'
))

const uploadProgressDescription = computed(() => {
  if (uploadPhase.value === 'refreshing') {
    return '正在同步最新素材列表和缩略图，请稍候'
  }
  const fileName = uploadCurrentFileName.value || '当前文件'
  const suffix = uploadMode.value === 'image'
    ? '服务端会自动压缩到 500KB 以内，较大的图片可能需要一些时间'
    : '正在保存到素材库'
  return `${fileName} · ${suffix}`
})

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

const hasContactInfo = computed(() => !!(brand.value?.publicPhone || brand.value?.publicAddress || brand.value?.phone || brand.value?.wechat))

const completenessItems = computed(() => [
  !!brand.value?.website,
  hasContactInfo.value,
  !!brand.value?.businessIntro,
  !!brand.value?.brandQualificationDescription,
  !!brand.value?.brandCaseDescription,
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
  if (!hasContactInfo.value) {
    alerts.push({ title: '缺少联系方式', description: '缺少对外公开电话、对外公开地址、电话或微信，影响平台联系方式曝光的判定', type: 'warning' })
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

const selectedFolder = computed(() => {
  if (!selectedFolderId.value) return null
  return imageFolders.value.find((folder) => folder.id === selectedFolderId.value) || null
})

const selectedFolderName = computed(() => selectedFolder.value?.folderName || '图库')

const uploadAccept = computed(() => uploadMode.value === 'image' ? 'image/*' : undefined)

// ────────── 素材筛选 ──────────
const selectedFolderMaterials = computed(() => {
  let list = selectedFolderId.value
    ? materials.value.filter((m) => m.folderId === selectedFolderId.value && m.category === 'brand_image' && isImageType(m.fileType))
    : []
  if (searchKeyword.value.trim()) {
    const kw = searchKeyword.value.trim().toLowerCase()
    list = list.filter((m) => m.fileName?.toLowerCase().includes(kw))
  }
  return list
})

const visibleMaterials = computed(() => (
  folderExpanded.value
    ? selectedFolderMaterials.value
    : selectedFolderMaterials.value.slice(0, MATERIAL_COLLAPSED_LIMIT)
))

const documentMaterials = computed(() => materials.value.filter((m) => m.category !== 'brand_image'))

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
    resetThumbnails()
    materials.value = (data.data || []).sort((a, b) => {
      const bTime = parseDateTime(b.updatedAt || b.createdAt)?.getTime() ?? 0
      const aTime = parseDateTime(a.updatedAt || a.createdAt)?.getTime() ?? 0
      return bTime - aTime
    })
    void loadVisibleThumbnails()
  } catch {
    resetThumbnails()
    materials.value = []
  } finally {
    materialsLoading.value = false
  }
}

async function loadFolders() {
  foldersLoading.value = true
  try {
    const { data } = await getBrandImageFolders(brandId.value, { includeMaterials: false })
    imageFolders.value = data.data || []
    if (!selectedFolderId.value || !imageFolders.value.some((folder) => folder.id === selectedFolderId.value)) {
      selectedFolderId.value = imageFolders.value[0]?.id || null
      folderExpanded.value = false
    }
  } catch {
    imageFolders.value = []
    selectedFolderId.value = null
  } finally {
    foldersLoading.value = false
  }
}

async function loadBrandProjects() {
  projectsLoading.value = true
  try {
    const { data } = await getProjectList({
      current: 1,
      size: 500,
      brandId: brandId.value,
    })
    brandProjects.value = data.data?.records || []
  } catch {
    brandProjects.value = []
  } finally {
    projectsLoading.value = false
  }
}

async function loadVisibleThumbnails() {
  const currentRequestId = thumbRequestId
  const targets = visibleMaterials.value.filter((mat) => (
    isImageType(mat.fileType)
    && !mat._thumbUrl
    && !mat._thumbLoading
    && !mat._thumbFailed
  ))
  if (targets.length === 0) return

  const concurrency = Math.min(2, targets.length)
  let cursor = 0

  const worker = async () => {
    while (cursor < targets.length) {
      const mat = targets[cursor++]
      mat._thumbLoading = true
      try {
        const { data } = await getBrandMaterialPreviewUrl(brandId.value, mat.id)
        const url = data.data?.url || ''
        if (!url) {
          mat._thumbUrl = null
          mat._thumbFailed = true
          continue
        }
        if (currentRequestId !== thumbRequestId) {
          return
        }
        mat._thumbUrl = url
      } catch {
        if (currentRequestId !== thumbRequestId) return
        mat._thumbUrl = null
        mat._thumbFailed = true
      } finally {
        mat._thumbLoading = false
      }
    }
  }

  await Promise.all(Array.from({ length: concurrency }, () => worker()))
}

function resetThumbnails() {
  thumbRequestId++
  cleanupThumbs()
}

function cleanupThumbs() {
  thumbUrls.value.forEach((url) => URL.revokeObjectURL(url))
  thumbUrls.value = []
}

function selectFolder(folderId: number) {
  if (selectedFolderId.value === folderId) return
  selectedFolderId.value = folderId
  folderExpanded.value = false
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
function handleImageUpload() {
  if (uploadInProgress.value) return
  uploadMode.value = 'image'
  uploadingCategory.value = 'brand_image'
  if (!selectedFolderId.value) {
    selectedFolderId.value = imageFolders.value.find((folder) => folder.status === 'active')?.id || null
  }
  if (!selectedFolderId.value) {
    ElMessage.warning('请先创建或选择一个启用的图库文件夹')
    return
  }
  if (selectedFolder.value?.status !== 'active') {
    ElMessage.warning('停用的图库文件夹不能上传素材')
    return
  }
  fileInputRef.value?.click()
}

function handleDocumentUploadCategory(category: string) {
  if (uploadInProgress.value) return
  uploadMode.value = 'document'
  uploadingCategory.value = category
  fileInputRef.value?.click()
}

async function onFileSelected(e: Event) {
  const input = e.target as HTMLInputElement
  const files = input.files
  if (!files || files.length === 0) return
  if (uploadInProgress.value) {
    input.value = ''
    return
  }
  if (!uploadingCategory.value) {
    ElMessage.warning('请先选择素材分类')
    input.value = ''
    return
  }

  const acceptedFiles: File[] = []
  const selectedFiles = Array.from(files)
  for (const file of selectedFiles) {
    if (file.size > MAX_UPLOAD_SIZE) {
      ElMessage.error(`文件「${file.name}」超过 10MB，已跳过`)
      continue
    }
    if (uploadMode.value === 'image' && !file.type.startsWith('image/')) {
      ElMessage.error(`文件「${file.name}」不是图片，已跳过`)
      continue
    }
    acceptedFiles.push(file)
  }

  if (acceptedFiles.length === 0) {
    input.value = ''
    return
  }

  let successCount = 0
  uploadInProgress.value = true
  uploadTotal.value = acceptedFiles.length
  uploadCompleted.value = 0
  uploadFailed.value = 0
  uploadPhase.value = 'uploading'

  for (const file of acceptedFiles) {
    uploadCurrentFileName.value = file.name
    try {
      const folderId = uploadMode.value === 'image' ? selectedFolderId.value || undefined : undefined
      await uploadBrandMaterial(brandId.value, uploadingCategory.value, file, folderId)
      successCount++
    } catch (err: any) {
      uploadFailed.value++
      ElMessage.error(`「${file.name}」上传失败：${err?.response?.data?.message || '未知错误'}`)
    } finally {
      uploadCompleted.value++
    }
  }

  input.value = ''
  try {
    if (successCount > 0) {
      uploadPhase.value = 'refreshing'
      uploadCurrentFileName.value = ''
      await Promise.all([loadFolders(), loadMaterials()])
      const failedText = uploadFailed.value > 0 ? `，失败 ${uploadFailed.value} 个` : ''
      ElMessage.success(`成功上传 ${successCount} 个文件${failedText}`)
    }
  } finally {
    uploadInProgress.value = false
    uploadTotal.value = 0
    uploadCompleted.value = 0
    uploadFailed.value = 0
    uploadCurrentFileName.value = ''
    uploadPhase.value = 'uploading'
  }
}

function openCreateFolder() {
  editingFolderId.value = null
  folderForm.value = { folderName: '', status: 'active', description: '', tags: [], projectIds: [] }
  searchFolderTags('')
  folderDialogVisible.value = true
}

function openEditFolder(folder: BrandImageFolder) {
  editingFolderId.value = folder.id
  folderForm.value = {
    folderName: folder.folderName,
    status: folder.status || 'active',
    description: folder.description || '',
    tags: [...(folder.tags || [])],
    projectIds: [...(folder.projectIds || [])],
  }
  searchFolderTags('')
  folderDialogVisible.value = true
}

async function searchFolderTags(keyword: string) {
  try {
    const { data } = await suggestBrandImageFolderTags(brandId.value, keyword)
    const selected = folderForm.value.tags || []
    folderTagOptions.value = Array.from(new Set([...selected, ...(data.data || [])]))
  } catch {
    folderTagOptions.value = [...(folderForm.value.tags || [])]
  }
}

async function saveFolder() {
  const name = folderForm.value.folderName.trim()
  if (!name) {
    ElMessage.warning('请输入文件夹名称')
    return
  }
  const tags = Array.from(new Set(folderForm.value.tags.map((item) => item.trim()).filter(Boolean)))
  if (tags.some((tag) => tag.length > 10)) {
    ElMessage.warning('单个标签不能超过10个字')
    return
  }
  const projectIds = Array.from(new Set(folderForm.value.projectIds.filter((item) => Number.isFinite(item) && item > 0)))
  folderSaving.value = true
  try {
    const payload = {
      folderName: name,
      status: folderForm.value.status,
      description: folderForm.value.description.trim() || undefined,
      tags,
      projectIds,
    }
    if (editingFolderId.value) {
      await updateBrandImageFolder(brandId.value, editingFolderId.value, payload)
    } else {
      await createBrandImageFolder(brandId.value, payload)
    }
    ElMessage.success('图库文件夹已保存')
    folderDialogVisible.value = false
    await loadFolders()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    folderSaving.value = false
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
    const { data } = await getBrandMaterialPreviewUrl(brandId.value, row.id)
    const url = data.data?.url
    if (!url) {
      throw new Error('Preview URL is empty')
    }
    win.location.href = url
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
    await Promise.all([loadFolders(), loadMaterials()])
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '删除失败')
  }
}

function folderName(folderId?: number | null) {
  return imageFolders.value.find((folder) => folder.id === folderId)?.folderName || '默认图库'
}

function folderImageCount(folderId: number) {
  return materials.value.filter((material) => material.folderId === folderId && material.category === 'brand_image' && isImageType(material.fileType)).length
}

function formatFolderProjects(projectIds: number[]) {
  const projectNameMap = new Map(brandProjects.value.map((project) => [project.id, project.projectName]))
  return projectIds.map((id) => projectNameMap.get(id) || `项目 ${id}`).join('、')
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
  await Promise.all([loadBrand(), loadStatement(), loadFolders(), loadMaterials(), loadBrandProjects(), loadVersionsCount()])
}

onMounted(loadAll)

watch(() => route.params.id, (newId, oldId) => {
  if (newId !== oldId && newId) {
    selectedFolderId.value = null
    folderExpanded.value = false
    searchKeyword.value = ''
    viewMode.value = 'grid'
    resetThumbnails()
    void loadAll()
  }
})

watch(
  () => visibleMaterials.value.map((mat) => mat.id).join(','),
  () => {
    void loadVisibleThumbnails()
  },
)

onBeforeUnmount(() => {
  resetThumbnails()
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

.folder-card {
  min-height: 112px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 12px;
  background: #fff;
  cursor: pointer;
  transition: border-color 0.16s ease, box-shadow 0.16s ease, background-color 0.16s ease;
}

.folder-card:hover {
  border-color: #f59e0b;
}

.folder-card-active {
  border-color: #f97316;
  background: #fff7ed;
  box-shadow: 0 0 0 2px rgba(249, 115, 22, 0.14);
}

.folder-toggle {
  display: flex;
  justify-content: center;
  padding-top: 14px;
}

.folder-toggle-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: 0;
  background: transparent;
  color: #f97316;
  font-size: 14px;
  line-height: 20px;
  cursor: pointer;
}

.folder-toggle-btn:hover {
  color: #ea580c;
}

.folder-toggle-chevrons {
  display: inline-flex;
  align-items: center;
  gap: 0;
}

.folder-toggle-chevrons :deep(.el-icon) {
  margin-left: -4px;
  font-size: 14px;
}

.folder-toggle-chevrons-up :deep(.el-icon) {
  transform: rotate(180deg);
}

.upload-progress-panel {
  margin-bottom: 16px;
  padding: 12px 14px;
  border: 1px solid #fed7aa;
  border-radius: 8px;
  background: #fff7ed;
}

.upload-progress-panel__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 10px;
}

.upload-progress-panel__title {
  font-size: 14px;
  font-weight: 600;
  line-height: 20px;
  color: #9a3412;
}

.upload-progress-panel__desc {
  margin-top: 2px;
  font-size: 12px;
  line-height: 18px;
  color: #9a3412;
}
</style>
