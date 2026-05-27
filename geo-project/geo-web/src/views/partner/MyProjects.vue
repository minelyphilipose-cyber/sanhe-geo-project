<template>
  <div class="space-y-4">
    <el-card>
      <template #header>
        <div class="flex items-center justify-between">
          <span>我的项目</span>
          <div class="flex items-center gap-2">
            <el-input v-model="keyword" clearable placeholder="搜索项目名称" style="width: 240px" @keyup.enter="load" />
            <el-button @click="load">查询</el-button>
            <el-button v-if="canCreateProject" type="primary" @click="openCreate">新增项目</el-button>
          </div>
        </div>
      </template>

      <DataState :loading="loading" :empty="!loading && rows.length === 0" empty-text="暂无项目">
        <el-table :data="rows" border>
          <el-table-column prop="projectName" label="项目名称" min-width="220" />
          <el-table-column prop="companyName" label="客户" min-width="180" />
          <el-table-column prop="brandName" label="品牌" min-width="160" />
          <el-table-column label="项目状态" width="120">
            <template #default="scope">{{ projectStatusLabel(scope.row.status) }}</template>
          </el-table-column>
          <el-table-column v-if="canUpdateProject" label="操作" width="100" fixed="right">
            <template #default="scope">
              <el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>
      </DataState>
    </el-card>

    <el-dialog v-model="formVisible" :title="formMode === 'create' ? '新增项目' : '编辑项目'" width="760px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="项目名称" prop="projectName" required><el-input v-model="form.projectName" /></el-form-item>
        <el-form-item label="项目别名">
          <el-input v-model="form.projectAliases" placeholder="多个别名用英文逗号分隔" />
        </el-form-item>
        <el-form-item label="客户" prop="companyId" required>
          <el-select
            v-model="form.companyId"
            filterable
            style="width: 100%"
            placeholder="先选择客户"
            :disabled="formMode === 'edit'"
            @change="onCompanyChange"
          >
            <el-option v-for="c in companyOptions" :key="c.id" :label="c.companyName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="品牌" prop="brandId" required>
          <el-select
            v-model="form.brandId"
            filterable
            style="width: 100%"
            placeholder="请选择品牌；选品牌前需先选客户"
            :disabled="!form.companyId || formMode === 'edit' || dependencyLoading"
            :loading="brandLoading"
          >
            <el-option v-for="b in brandOptions" :key="b.id" :label="b.brandName" :value="b.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="拓词组" prop="keywordGroupIds" required>
          <div class="w-full">
            <el-select
              v-model="form.keywordGroupIds"
              style="width: 100%"
              multiple
              filterable
              collapse-tags
              collapse-tags-tooltip
              :max-collapse-tags="3"
              placeholder="请选择当前客户下的拓词组"
              :disabled="!form.companyId || dependencyLoading"
              :loading="keywordGroupLoading"
            >
              <el-option
                v-for="kg in keywordGroupOptions"
                :key="kg.id"
                :label="`${kg.name}（已入库${kg.savedKeywordCount || 0}条）`"
                :value="kg.id"
              />
            </el-select>
            <div class="keyword-summary">{{ keywordGroupSummary }}</div>
          </div>
        </el-form-item>
        <el-form-item label="问题额度">
          <div class="keyword-quota-panel">
            <div class="channel-note">默认填入当前客户套餐 A/B/C 剩余额度，单档最大不可超过可分配数量</div>
            <div class="quota-row">
              <span>A档</span>
              <el-input-number v-model="form.keywordGroupLimitA" :min="0" :max="keywordTierMax('A')" controls-position="right" />
              <small>可分配 {{ keywordQuota?.inputMaxA ?? 0 }}</small>
            </div>
            <div class="quota-row">
              <span>B档</span>
              <el-input-number v-model="form.keywordGroupLimitB" :min="0" :max="keywordTierMax('B')" controls-position="right" />
              <small>可分配 {{ keywordQuota?.inputMaxB ?? 0 }}</small>
            </div>
            <div class="quota-row">
              <span>C档</span>
              <el-input-number v-model="form.keywordGroupLimitC" :min="0" :max="keywordTierMax('C')" controls-position="right" />
              <small>可分配 {{ keywordQuota?.inputMaxC ?? 0 }}</small>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="分发渠道">
          <div class="channel-allocation-panel">
            <div class="channel-note">剩余额度不含草稿/暂停项目，项目启动时会再次校验</div>
            <div v-for="item in channelQuotaItems" :key="item.channelCode" class="channel-row">
              <div class="channel-meta">
                <span>{{ item.channelName }}</span>
                <small>{{ channelQuotaText(item) }}</small>
              </div>
              <el-input-number
                v-model="form.channelAllocations[item.channelCode]"
                :min="0"
                :max="channelInputMax(item)"
                :disabled="!item.enabled"
                controls-position="right"
              />
            </div>
          </div>
        </el-form-item>
        <el-form-item label="地区"><RegionCascader v-model="form.regionCodes" /></el-form-item>
        <el-divider content-position="left">内容策略配置</el-divider>
        <el-form-item label="核心关键词" prop="coreKeywords" required>
          <el-input
            v-model="form.coreKeywords"
            maxlength="200"
            show-word-limit
            placeholder="可填多个，用逗号隔开，例如：装修公司,旧房翻新,局部改造"
          />
        </el-form-item>
        <el-form-item label="目标区域词" prop="targetRegions" required>
          <el-select
            v-model="form.targetRegions"
            multiple
            filterable
            allow-create
            default-first-option
            style="width: 100%"
            placeholder="输入并回车，例如：北京、上海、广州"
          />
        </el-form-item>
        <el-form-item label="目标受众" prop="targetAudience" required>
          <el-input v-model="form.targetAudience" placeholder="例如：装修业主、二手房翻新用户" />
        </el-form-item>
        <el-form-item label="交付模式"><el-input v-model="form.deliveryMode" /></el-form-item>
        <el-form-item label="主目标"><el-input v-model="form.primaryGoal" type="textarea" :rows="3" /></el-form-item>
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
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { getBrandList, getCompanyList } from '@/api/customer'
import {
  createProject,
  getProjectChannelAllocationQuota,
  getProjectKeywordGroupQuota,
  getKeywordGroupPage,
  getProjectList,
  updateProject,
} from '@/api/project'
import type { Brand, Company, KeywordGroup, Project, ProjectChannelAllocationItem, ProjectKeywordGroupQuota } from '@/types'
import DataState from '@/components/ui/DataState.vue'
import RegionCascader from '@/components/ui/RegionCascader.vue'
import { regionCodesFromPayload, regionPayloadFromCodes } from '@/constants/region'
import { useDictStore } from '@/stores/dict'
import { useUserStore } from '@/stores/user'
import { errorMessage } from '@/utils/error'

const dictStore = useDictStore()
const userStore = useUserStore()
const canCreateProject = computed(() => userStore.hasPermission('project.create'))
const canUpdateProject = computed(() => userStore.hasPermission('project.update'))

const loading = ref(false)
const saving = ref(false)
const brandLoading = ref(false)
const keywordGroupLoading = ref(false)
const keyword = ref('')
const rows = ref<Project[]>([])
const companyOptions = ref<Company[]>([])
const brandOptions = ref<Brand[]>([])
const keywordGroupOptions = ref<KeywordGroup[]>([])
const channelQuotaItems = ref<ProjectChannelAllocationItem[]>([])
const keywordQuota = ref<ProjectKeywordGroupQuota | null>(null)
const allocationVersion = ref<number | null>(null)

const formVisible = ref(false)
const formRef = ref<FormInstance>()
const formMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)

const form = reactive({
  projectName: '',
  projectAliases: '',
  companyId: null as number | null,
  brandId: null as number | null,
  keywordGroupIds: [] as number[],
  keywordGroupLimitA: 0,
  keywordGroupLimitB: 0,
  keywordGroupLimitC: 0,
  channelAllocations: {} as Record<string, number>,
  regionCodes: [] as string[],
  targetRegions: [] as string[],
  coreKeywords: '',
  targetAudience: '',
  deliveryMode: 'managed',
  primaryGoal: '',
  remark: '',
})

const rules: FormRules = {
  projectName: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
  companyId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  brandId: [{ required: true, message: '请选择品牌', trigger: 'change' }],
  keywordGroupIds: [{ required: true, type: 'array', min: 1, max: 10, message: '请选择 1-10 个拓词组', trigger: 'change' }],
  coreKeywords: [
    { required: true, message: '请输入核心关键词', trigger: 'blur' },
    { max: 200, message: '核心关键词不能超过 200 字', trigger: 'blur' },
  ],
  targetRegions: [{ required: true, type: 'array', min: 1, message: '请输入目标区域词', trigger: 'change' }],
  targetAudience: [{ required: true, message: '请输入目标受众', trigger: 'blur' }],
}

const dependencyLoading = computed(() => brandLoading.value || keywordGroupLoading.value)

const keywordGroupSummary = computed(() => {
  const selected = new Set(form.keywordGroupIds)
  let saved = 0
  for (const group of keywordGroupOptions.value) {
    if (selected.has(group.id)) {
      saved += group.savedKeywordCount || 0
    }
  }
  return `已选 ${form.keywordGroupIds.length} 个拓词组，已入库 ${saved} 条关键词`
})

function projectStatusLabel(status?: string | null) {
  if (!status) return '-'
  return dictStore.label('project_status', status) || status
}

function parseStringArray(value?: string | string[] | null) {
  if (Array.isArray(value)) {
    return value.map((item) => String(item).trim()).filter((item, index, arr) => item.length > 0 && arr.indexOf(item) === index)
  }
  if (!value) {
    return [] as string[]
  }
  try {
    const parsed = JSON.parse(value)
    if (Array.isArray(parsed)) {
      return parsed
        .map((item) => String(item).trim())
        .filter((item, index, arr) => item.length > 0 && arr.indexOf(item) === index)
    }
  } catch {
    return String(value)
      .split(/[,，、;；\n\r]+/)
      .map((item) => item.trim())
      .filter((item, index, arr) => item.length > 0 && arr.indexOf(item) === index)
  }
  return []
}

function resetForm() {
  form.projectName = ''
  form.projectAliases = ''
  form.companyId = null
  form.brandId = null
  form.keywordGroupIds = []
  form.keywordGroupLimitA = 0
  form.keywordGroupLimitB = 0
  form.keywordGroupLimitC = 0
  keywordQuota.value = null
  form.channelAllocations = {}
  channelQuotaItems.value = []
  allocationVersion.value = null
  form.regionCodes = []
  form.targetRegions = []
  form.coreKeywords = ''
  form.targetAudience = ''
  form.deliveryMode = 'managed'
  form.primaryGoal = ''
  form.remark = ''
}

async function onCompanyChange() {
  if (!form.companyId) {
    form.brandId = null
    brandOptions.value = []
    keywordGroupOptions.value = []
    form.keywordGroupIds = []
    form.channelAllocations = {}
    channelQuotaItems.value = []
    keywordQuota.value = null
    allocationVersion.value = null
    return
  }
  if (formMode.value === 'create') {
    form.brandId = null
  }
  form.keywordGroupIds = []
  await Promise.all([loadBrands(form.companyId), loadKeywordGroups(form.companyId), loadChannelAllocationQuota(form.companyId), loadKeywordGroupQuota(form.companyId)])
}

async function loadKeywordGroupQuota(companyId?: number | null, excludeProjectId?: number | null, applyDefault = true) {
  if (!companyId) {
    keywordQuota.value = null
    form.keywordGroupLimitA = 0
    form.keywordGroupLimitB = 0
    form.keywordGroupLimitC = 0
    return
  }
  const { data } = await getProjectKeywordGroupQuota({
    companyId,
    excludeProjectId: excludeProjectId || undefined,
  })
  keywordQuota.value = data.data
  if (applyDefault) {
    form.keywordGroupLimitA = data.data.remainingCountA || 0
    form.keywordGroupLimitB = data.data.remainingCountB || 0
    form.keywordGroupLimitC = data.data.remainingCountC || 0
  }
}

function keywordTierMax(tier: 'A' | 'B' | 'C') {
  if (!keywordQuota.value) return 0
  if (tier === 'A') return Math.max(keywordQuota.value.inputMaxA || 0, 0)
  if (tier === 'B') return Math.max(keywordQuota.value.inputMaxB || 0, 0)
  return Math.max(keywordQuota.value.inputMaxC || 0, 0)
}

async function loadChannelAllocationQuota(companyId?: number | null, excludeProjectId?: number | null) {
  if (!companyId) {
    channelQuotaItems.value = []
    allocationVersion.value = null
    form.channelAllocations = {}
    return
  }
  const { data } = await getProjectChannelAllocationQuota({
    companyId,
    excludeProjectId: excludeProjectId || undefined,
  })
  channelQuotaItems.value = data.data.items || []
  allocationVersion.value = data.data.allocationVersion
  const next: Record<string, number> = {}
  for (const item of channelQuotaItems.value) {
    next[item.channelCode] = item.currentProjectAllocatedCount || 0
  }
  form.channelAllocations = next
}

function channelInputMax(item: ProjectChannelAllocationItem) {
  return Math.max(item.inputMax ?? item.remainingCount ?? 0, 0)
}

function channelQuotaText(item: ProjectChannelAllocationItem) {
  if (!item.enabled) {
    return '套餐未启用'
  }
  const periodType = item.periodType === 'none' ? '-' : item.periodType || '-'
  return `剩余 ${item.remainingCount || 0} / 总量 ${item.quotaLimit}（${periodType}）`
}

function openCreate() {
  formMode.value = 'create'
  editingId.value = null
  resetForm()
  formVisible.value = true
}

async function openEdit(row: Project) {
  formMode.value = 'edit'
  editingId.value = row.id
  form.projectName = row.projectName
  form.projectAliases = row.projectAliases || ''
  form.companyId = row.companyId || null
  form.brandId = row.brandId
  form.keywordGroupIds = [...(row.selectedKeywordGroupIds || [])]
  form.keywordGroupLimitA = row.planKeywordGroupLimitA ?? row.planKeywordGroupLimit ?? 0
  form.keywordGroupLimitB = row.planKeywordGroupLimitB ?? 0
  form.keywordGroupLimitC = row.planKeywordGroupLimitC ?? 0
  form.regionCodes = regionCodesFromPayload(row)
  form.targetRegions = parseStringArray(row.targetRegions)
  form.coreKeywords = row.coreKeywords || ''
  form.targetAudience = row.targetAudience || ''
  form.deliveryMode = row.deliveryMode || 'managed'
  form.primaryGoal = row.primaryGoal || ''
  form.remark = row.remark || ''
  await Promise.all([loadBrands(form.companyId), loadKeywordGroups(form.companyId), loadChannelAllocationQuota(form.companyId, editingId.value), loadKeywordGroupQuota(form.companyId, editingId.value, false)])
  formVisible.value = true
}

async function submit() {
  if (!formRef.value) {
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  if (!form.companyId || !form.brandId) {
    return
  }

  saving.value = true
  try {
    const region = regionPayloadFromCodes(form.regionCodes)
    const payload = {
      projectName: form.projectName,
      projectAliases: form.projectAliases || undefined,
      companyId: form.companyId,
      brandId: form.brandId,
      keywordGroupIds: form.keywordGroupIds,
      keywordGroupLimitA: form.keywordGroupLimitA,
      keywordGroupLimitB: form.keywordGroupLimitB,
      keywordGroupLimitC: form.keywordGroupLimitC,
      allocationVersion: allocationVersion.value,
      channelAllocations: Object.entries(form.channelAllocations).map(([channelCode, allocatedCount]) => ({
        channelCode,
        allocatedCount,
      })),
      deliveryMode: form.deliveryMode || 'managed',
      provinceCode: region.provinceCode,
      provinceName: region.provinceName,
      cityCode: region.cityCode,
      cityName: region.cityName,
      districtCode: region.districtCode,
      districtName: region.districtName,
      targetRegions: form.targetRegions,
      coreKeywords: form.coreKeywords || undefined,
      targetAudience: form.targetAudience || undefined,
      primaryGoal: form.primaryGoal || undefined,
      remark: form.remark || undefined,
    }

    if (formMode.value === 'create') {
      await createProject(payload)
    } else if (editingId.value) {
      await updateProject(editingId.value, payload)
    }

    formVisible.value = false
    ElMessage.success('保存成功')
    await load()
  } catch (err) {
    ElMessage.error(errorMessage(err, '保存失败'))
  } finally {
    saving.value = false
  }
}

async function loadCompanies() {
  try {
    const { data } = await getCompanyList({ current: 1, size: 500 })
    companyOptions.value = data.data.records || []
  } catch {
    companyOptions.value = []
  }
}

async function loadBrands(companyId?: number | null) {
  brandLoading.value = true
  try {
    const { data } = await getBrandList({ current: 1, size: 500, companyId: companyId || undefined })
    brandOptions.value = data.data.records || []
  } catch (err) {
    brandOptions.value = []
    ElMessage.error(errorMessage(err, '加载品牌失败'))
  } finally {
    brandLoading.value = false
  }
}

async function loadKeywordGroups(companyId?: number | null) {
  if (!companyId) {
    keywordGroupOptions.value = []
    form.keywordGroupIds = []
    return
  }
  keywordGroupLoading.value = true
  try {
    const { data } = await getKeywordGroupPage({ current: 1, size: 500, companyId })
    keywordGroupOptions.value = data.data.records || []
    const validIds = new Set(keywordGroupOptions.value.map((item) => item.id))
    form.keywordGroupIds = form.keywordGroupIds.filter((id) => validIds.has(id))
  } catch (err) {
    keywordGroupOptions.value = []
    form.keywordGroupIds = []
    ElMessage.error(errorMessage(err, '加载拓词组失败'))
  } finally {
    keywordGroupLoading.value = false
  }
}

async function load() {
  loading.value = true
  try {
    const { data } = await getProjectList({
      current: 1,
      size: 200,
      keyword: keyword.value || undefined,
    })
    rows.value = data.data.records || []
  } catch {
    rows.value = []
    ElMessage.error('加载项目失败')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await dictStore.ensureLoaded()
  await loadCompanies()
  await load()
})
</script>

<style scoped>
.keyword-summary {
  margin-top: 8px;
  font-size: 12px;
  color: #606266;
}
.keyword-quota-panel {
  width: 100%;
  display: grid;
  gap: 10px;
}
.quota-row {
  display: grid;
  grid-template-columns: 44px minmax(160px, 220px) 1fr;
  align-items: center;
  gap: 10px;
}
.quota-row small {
  color: #909399;
}
.channel-allocation-panel {
  width: 100%;
  display: grid;
  gap: 10px;
}
.channel-note {
  font-size: 12px;
  color: #909399;
}
.channel-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.channel-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.channel-meta small {
  color: #909399;
}
</style>
