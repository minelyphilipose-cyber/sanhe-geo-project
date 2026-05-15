<template>
  <div>
    <div class="mb-4 flex items-center justify-between gap-3">
      <div class="flex items-center gap-2">
        <el-input v-model="query.keyword" placeholder="搜索项目名称" clearable style="width: 240px" @keyup.enter="load" />
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px" @change="load">
          <el-option v-for="v in statusOptions" :key="v" :label="projectStatusLabel(v)" :value="v" />
        </el-select>
        <el-button @click="load">查询</el-button>
      </div>
      <el-button v-if="canCreateProject" type="primary" @click="openCreate">新建项目</el-button>
    </div>

    <el-card>
      <DataState :loading="loading" :empty="!loading && rows.length === 0" empty-text="暂无项目数据">
        <el-table :data="rows" border>
          <el-table-column prop="projectCode" label="编码" min-width="170" />
          <el-table-column prop="projectName" label="项目名称" min-width="180" />
          <el-table-column prop="companyName" label="客户名称" min-width="180" />
          <el-table-column prop="brandName" label="品牌名称" min-width="180">
            <template #default="scope">{{ scope.row.brandName || '-' }}</template>
          </el-table-column>
          <el-table-column label="拓词组" width="160">
            <template #default="scope">
              {{ scope.row.selectedKeywordGroupCount || 0 }}组/{{ scope.row.selectedKeywordSavedKeywords || 0 }}条
              <div class="table-subtext">A{{ scope.row.selectedKeywordSavedKeywordsA || 0 }} / B{{ scope.row.selectedKeywordSavedKeywordsB || 0 }} / C{{ scope.row.selectedKeywordSavedKeywordsC || 0 }}</div>
            </template>
          </el-table-column>
          <el-table-column label="归属" width="100">
            <template #default="scope">{{ dictStore.label('owner_type', scope.row.ownerType) }}</template>
          </el-table-column>
          <el-table-column prop="cityName" label="地区" min-width="200">
            <template #default="scope">{{ regionDisplay(scope.row) || '-' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="scope">{{ projectStatusLabel(scope.row.status) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="scope">
              <div class="op-actions">
                <el-button link type="primary" @click="goDetail(scope.row.id)">详情</el-button>
                <el-dropdown
                  trigger="click"
                  @command="(cmd: string | number | object) => onMoreCommand(scope.row, String(cmd))"
                >
                  <el-button link type="primary">更多</el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item v-if="canStartProject(scope.row)" command="activate">{{ scope.row.status === 'paused' ? '再次启动' : '启动' }}</el-dropdown-item>
                      <el-dropdown-item v-if="scope.row.status === 'active' && canCloseProject" command="pause">暂停</el-dropdown-item>
                      <el-dropdown-item v-if="canUpdateProject" command="edit">编辑</el-dropdown-item>
                      <el-dropdown-item v-if="canDeleteProject" command="delete">删除</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="mt-4 flex justify-end">
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

    <el-dialog v-model="formVisible" :title="formMode === 'create' ? '新建项目' : '编辑项目'" width="760px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="项目名称" required><el-input v-model="form.projectName" /></el-form-item>
        <el-form-item label="项目别名">
          <el-input v-model="form.projectAliases" placeholder="可选，多个别名用英文逗号分隔" />
        </el-form-item>
        <el-form-item label="客户" required>
          <el-select
            v-model="form.companyId"
            filterable
            style="width: 100%"
            placeholder="先选择客户"
            :disabled="lockCompanyBrandSelection"
            @change="onCompanyChange"
          >
            <el-option v-for="c in companyOptions" :key="c.id" :label="c.companyName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="品牌" required>
          <el-select
            v-model="form.brandId"
            filterable
            style="width: 100%"
            placeholder="请选择品牌；选品牌前需先选客户"
            :disabled="!form.companyId || lockCompanyBrandSelection"
          >
            <el-option v-for="b in brandOptions" :key="b.id" :label="b.brandName" :value="b.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="formMode === 'edit'" label="拓词组">
          <div class="w-full">
            <el-select
              v-model="form.keywordGroupIds"
              style="width: 100%"
              multiple
              filterable
              collapse-tags
              collapse-tags-tooltip
              :multiple-limit="10"
              placeholder="项目启动前必须至少绑定 1 个拓词组"
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
            <div class="channel-note">官网、行业资讯站额度大于 0 时才会触发文章生成；可填范围为客户套餐总额度减去当前已激活项目占用</div>
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
        <el-form-item label="归属类型">
          <el-input :value="companyOwnerTypeLabel" disabled />
        </el-form-item>
        <el-form-item label="地区"><RegionCascader v-model="form.regionCodes" /></el-form-item>
        <el-form-item label="交付模式"><el-input v-model="form.deliveryMode" /></el-form-item>
        <el-form-item label="主目标"><el-input v-model="form.primaryGoal" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="客户需求">
          <div class="customer-requirements">
            <div v-for="(_, index) in form.customerRequirements" :key="index" class="customer-requirement-row">
              <div class="requirement-row-head">
                <span>需求 {{ index + 1 }}</span>
                <el-button link type="danger" :disabled="form.customerRequirements.length <= 1" @click="removeCustomerRequirement(index)">删除</el-button>
              </div>
              <el-input
                v-model="form.customerRequirements[index]"
                type="textarea"
                :rows="3"
                maxlength="100"
                show-word-limit
                resize="none"
                placeholder="请输入 10-100 字客户需求"
              />
            </div>
            <el-button class="requirement-add" plain @click="addCustomerRequirement">新增需求</el-button>
          </div>
        </el-form-item>
        <el-divider content-position="left">内容策略配置（选填）</el-divider>
        <el-form-item label="目标区域词">
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
        <el-form-item label="目标受众">
          <el-input v-model="form.targetAudience" placeholder="例如：装修业主、二手房翻新用户" />
        </el-form-item>
        <el-form-item label="品牌基准表述">
          <el-input :model-value="brandBaseStatement" type="textarea" :rows="4" readonly placeholder="当前品牌未配置基准表述" />
        </el-form-item>
        <el-form-item label="项目定制表述">
          <div class="w-full">
            <el-input v-model="form.customStatement" type="textarea" :rows="4" placeholder="留空时生成内容将使用上方品牌基准表述" />
            <div class="form-tip">留空时生成内容将使用上方品牌基准表述</div>
          </div>
        </el-form-item>
        <el-form-item label="内容调性">
          <el-input v-model="form.contentTone" placeholder="例如：专业务实，强调可执行性" />
        </el-form-item>
        <el-form-item label="优先写作角度">
          <el-select
            v-model="form.preferredAngles"
            multiple
            filterable
            allow-create
            default-first-option
            style="width: 100%"
            placeholder="输入并回车，例如：指南、成本、避坑"
          />
        </el-form-item>
        <el-form-item label="品牌禁用词">
          <div class="w-full">
            <div v-if="brandForbiddenPhraseList.length" class="tag-list">
              <el-tag v-for="item in brandForbiddenPhraseList" :key="item" type="info">{{ item }}</el-tag>
            </div>
            <div v-else class="form-tip">当前品牌未配置禁用词</div>
          </div>
        </el-form-item>
        <el-form-item label="补充禁用词">
          <el-select
            v-model="form.extraForbiddenPhrases"
            multiple
            filterable
            allow-create
            default-first-option
            style="width: 100%"
            placeholder="输入并回车，作为项目级补充禁用词"
          />
        </el-form-item>
        <el-form-item label="内容备注">
          <el-input v-model="form.contentNote" type="textarea" :rows="3" placeholder="补充说明内容生成时需要强调或避免的点" />
        </el-form-item>
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
import { computed, reactive, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { useDictStore } from '@/stores/dict'
import {
  getProjectList,
  createProject,
  deleteProject,
  getProjectChannelAllocationQuota,
  getProjectKeywordGroupQuota,
  getKeywordGroupPage,
  updateProject,
  updateProjectStatus,
} from '@/api/project'
import { getBrandList, getCompanyList } from '@/api/customer'
import type { Brand, Company, KeywordGroup, Project, ProjectChannelAllocationItem, ProjectKeywordGroupQuota } from '@/types'
import DataState from '@/components/ui/DataState.vue'
import RegionCascader from '@/components/ui/RegionCascader.vue'
import { regionCodesFromPayload, regionDisplayFromPayload, regionPayloadFromCodes } from '@/constants/region'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const dictStore = useDictStore()
const canCreateProject = computed(() => userStore.hasPermission('project.create'))
const canUpdateProject = computed(() => userStore.hasPermission('project.update'))
const canDeleteProject = computed(() => userStore.hasPermission('project.delete'))
const canActivateProject = computed(() => userStore.hasPermission('project.start'))
const canCloseProject = computed(() => userStore.hasPermission('project.pause'))
const presetCompanyId = computed(() => {
  const raw = Number(route.query.companyId)
  return Number.isFinite(raw) && raw > 0 ? raw : null
})
const presetBrandId = computed(() => {
  const raw = Number(route.query.brandId)
  return Number.isFinite(raw) && raw > 0 ? raw : null
})
const fromCustomerBrandPath = computed(() => {
  return route.query.source === 'customer_brand' && !!presetCompanyId.value && !!presetBrandId.value
})
const lockCompanyBrandSelection = computed(() => formMode.value === 'create' && fromCustomerBrandPath.value)
const companyOwnerTypeLabel = computed(() => {
  const company = companyOptions.value.find((c) => c.id === form.companyId)
  const key = company?.ownerType || (company?.partnerId ? 'partner' : 'direct')
  return dictStore.label('owner_type', key) || '-'
})
const selectedBrand = computed(() => brandOptions.value.find((item) => item.id === form.brandId) || null)
const brandBaseStatement = computed(() => extractBrandBaseStatement(selectedBrand.value))
const brandForbiddenPhraseList = computed(() => parseStringArray(selectedBrand.value?.forbiddenPhrases))

const statusOptions = computed(() => ['pending_start', 'active', 'paused', 'expired'])
const loading = ref(false)
const saving = ref(false)
const rows = ref<Project[]>([])
const companyOptions = ref<Company[]>([])
const brandOptions = ref<Brand[]>([])
const keywordGroupOptions = ref<KeywordGroup[]>([])
const channelQuotaItems = ref<ProjectChannelAllocationItem[]>([])
const keywordQuota = ref<ProjectKeywordGroupQuota | null>(null)
const allocationVersion = ref<number | null>(null)
const page = reactive({ current: 1, size: 10, total: 0 })
const query = reactive({ keyword: '', status: '' })

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
  deliveryMode: 'managed',
  primaryGoal: '',
  customerRequirements: [''],
  targetRegions: [] as string[],
  targetAudience: '',
  customStatement: '',
  contentTone: '',
  preferredAngles: [] as string[],
  extraForbiddenPhrases: [] as string[],
  contentNote: '',
  remark: '',
})

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

const rules: FormRules = {
  projectName: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
  companyId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  brandId: [{ required: true, message: '请选择品牌', trigger: 'change' }],
}

function regionDisplay(project: Project) {
  return regionDisplayFromPayload(project)
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

function extractBrandBaseStatement(brand: Brand | null) {
  if (!brand) {
    return ''
  }
  const statement = brand.standardStatement
  if (statement && typeof statement === 'object') {
    const brandParagraph = statement.brand_paragraph?.trim()
    if (brandParagraph) {
      return brandParagraph
    }
  }
  if (typeof statement === 'string' && statement.trim()) {
    try {
      const parsed = JSON.parse(statement)
      const brandParagraph = typeof parsed?.brand_paragraph === 'string' ? parsed.brand_paragraph.trim() : ''
      if (brandParagraph) {
        return brandParagraph
      }
    } catch {
      return statement.trim()
    }
  }
  return brand.standardBrandStatement || brand.businessStandardStatement || ''
}

function resetForm() {
  form.projectName = ''
  form.projectAliases = ''
  form.companyId = fromCustomerBrandPath.value ? presetCompanyId.value : null
  form.brandId = fromCustomerBrandPath.value ? presetBrandId.value : null
  form.keywordGroupIds = []
  form.keywordGroupLimitA = 0
  form.keywordGroupLimitB = 0
  form.keywordGroupLimitC = 0
  keywordQuota.value = null
  form.channelAllocations = {}
  channelQuotaItems.value = []
  allocationVersion.value = null
  form.regionCodes = []
  form.deliveryMode = 'managed'
  form.primaryGoal = ''
  form.customerRequirements = ['']
  form.targetRegions = []
  form.targetAudience = ''
  form.customStatement = ''
  form.contentTone = ''
  form.preferredAngles = []
  form.extraForbiddenPhrases = []
  form.contentNote = ''
  form.remark = ''
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
  try {
    const { data } = await getBrandList({ current: 1, size: 500, companyId: companyId || undefined })
    brandOptions.value = data.data.records || []
  } catch {
    brandOptions.value = []
  }
}

async function loadKeywordGroups(companyId?: number | null) {
  if (!companyId) {
    keywordGroupOptions.value = []
    form.keywordGroupIds = []
    return
  }
  try {
    const { data } = await getKeywordGroupPage({ current: 1, size: 500, companyId })
    keywordGroupOptions.value = data.data.records || []
    const validIds = new Set(keywordGroupOptions.value.map((item) => item.id))
    form.keywordGroupIds = form.keywordGroupIds.filter((id) => validIds.has(id))
  } catch {
    keywordGroupOptions.value = []
    form.keywordGroupIds = []
  }
}

async function onCompanyChange(nextCompanyId: number) {
  if (lockCompanyBrandSelection.value) {
    form.companyId = presetCompanyId.value
    form.brandId = presetBrandId.value
    return
  }
  const hasSelectedGroups = formMode.value === 'edit' && form.keywordGroupIds.length > 0
  if (hasSelectedGroups) {
    try {
      await ElMessageBox.confirm(
        '切换客户后，已选拓词组将被清空，是否继续？',
        '切换客户确认',
        { type: 'warning', confirmButtonText: '继续', cancelButtonText: '取消' },
      )
    } catch {
      return
    }
  }
  form.companyId = nextCompanyId
  form.brandId = null
  form.keywordGroupIds = []
  const loadTasks = [
    loadBrands(nextCompanyId),
    loadChannelAllocationQuota(nextCompanyId),
    loadKeywordGroupQuota(nextCompanyId),
  ]
  if (formMode.value === 'edit') {
    loadTasks.push(loadKeywordGroups(nextCompanyId))
  }
  await Promise.all(loadTasks)
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

async function load() {
  loading.value = true
  try {
    const { data } = await getProjectList({
      current: page.current,
      size: page.size,
      keyword: query.keyword || undefined,
      status: query.status || undefined,
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

function onPageChange(v: number) {
  page.current = v
  load()
}

async function openCreate() {
  formMode.value = 'create'
  editingId.value = null
  resetForm()
  if (form.companyId) {
    keywordGroupOptions.value = []
    await Promise.all([loadBrands(form.companyId), loadChannelAllocationQuota(form.companyId), loadKeywordGroupQuota(form.companyId)])
    if (lockCompanyBrandSelection.value) {
      const hasPresetBrand = brandOptions.value.some((b) => b.id === presetBrandId.value)
      if (!hasPresetBrand) {
        ElMessage.warning('当前客户下未找到预设品牌，请返回客户详情页重新发起')
      } else {
        form.brandId = presetBrandId.value
      }
    }
  } else {
    brandOptions.value = []
    keywordGroupOptions.value = []
  }
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
  form.deliveryMode = row.deliveryMode || 'managed'
  form.primaryGoal = row.primaryGoal || ''
  form.customerRequirements = normalizeCustomerRequirementInputs(row.customerRequirements)
  form.targetRegions = parseStringArray(row.targetRegions)
  form.targetAudience = row.targetAudience || ''
  form.customStatement = row.customStatement || ''
  form.contentTone = row.contentTone || ''
  form.preferredAngles = parseStringArray(row.preferredAngles)
  form.extraForbiddenPhrases = parseStringArray(row.extraForbiddenPhrases)
  form.contentNote = row.contentNote || ''
  form.remark = (row as any).remark || ''
  await Promise.all([loadBrands(form.companyId), loadKeywordGroups(form.companyId), loadChannelAllocationQuota(form.companyId, editingId.value), loadKeywordGroupQuota(form.companyId, editingId.value, false)])
  formVisible.value = true
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  if (!form.companyId) {
    ElMessage.warning('请先选择客户')
    return
  }
  if (!form.brandId) {
    ElMessage.warning('请选择品牌')
    return
  }
  if (formMode.value === 'edit' && form.keywordGroupIds.length > 10) {
    ElMessage.warning('拓词组最多选择 10 个')
    return
  }
  const customerRequirements = buildCustomerRequirements()
  if (!customerRequirements) {
    return
  }
  saving.value = true
  try {
    const region = regionPayloadFromCodes(form.regionCodes)
    const payload: Record<string, any> = {
      provinceCode: region.provinceCode,
      provinceName: region.provinceName,
      cityCode: region.cityCode,
      cityName: region.cityName,
      districtCode: region.districtCode,
      districtName: region.districtName,
      projectName: form.projectName,
      projectAliases: form.projectAliases || undefined,
      companyId: form.companyId,
      brandId: form.brandId,
      keywordGroupLimitA: form.keywordGroupLimitA,
      keywordGroupLimitB: form.keywordGroupLimitB,
      keywordGroupLimitC: form.keywordGroupLimitC,
      allocationVersion: allocationVersion.value,
      channelAllocations: Object.entries(form.channelAllocations).map(([channelCode, allocatedCount]) => ({
        channelCode,
        allocatedCount,
      })),
      deliveryMode: form.deliveryMode || 'managed',
      primaryGoal: form.primaryGoal || undefined,
      customerRequirements,
      targetRegions: form.targetRegions,
      targetAudience: form.targetAudience || undefined,
      customStatement: form.customStatement || undefined,
      contentTone: form.contentTone || undefined,
      preferredAngles: form.preferredAngles,
      extraForbiddenPhrases: form.extraForbiddenPhrases,
      contentNote: form.contentNote || undefined,
      remark: form.remark || undefined,
    }
    if (lockCompanyBrandSelection.value) {
      payload.companyId = presetCompanyId.value
      payload.brandId = presetBrandId.value
    }
    if (formMode.value === 'edit') {
      payload.keywordGroupIds = form.keywordGroupIds
    }

    if (formMode.value === 'create') {
      const { data } = await createProject(payload)
      ElMessage.success('保存成功')
      formVisible.value = false
      await load()
      goDetail(data.data.id)
    } else if (editingId.value) {
      await updateProject(editingId.value, payload)
      ElMessage.success('保存成功')
      formVisible.value = false
      load()
    }
  } finally {
    saving.value = false
  }
}

function addCustomerRequirement() {
  if (form.customerRequirements.length >= 20) {
    ElMessage.warning('客户需求最多录入 20 条')
    return
  }
  form.customerRequirements.push('')
}

function removeCustomerRequirement(index: number) {
  if (form.customerRequirements.length <= 1) {
    return
  }
  form.customerRequirements.splice(index, 1)
}

function normalizeCustomerRequirementInputs(requirements?: string[] | null) {
  const normalized = (requirements || [])
    .map((item) => String(item || '').trim())
    .filter(Boolean)
  return normalized.length ? normalized : ['']
}

function buildCustomerRequirements() {
  const requirements = form.customerRequirements.map((item) => item.trim()).filter(Boolean)
  if (requirements.length > 20) {
    ElMessage.warning('客户需求最多录入 20 条')
    return null
  }
  for (const requirement of requirements) {
    const length = Array.from(requirement).length
    if (length < 10 || length > 100) {
      ElMessage.warning('每条客户需求字数需在 10-100 之间')
      return null
    }
  }
  return requirements
}

function goDetail(id: number) {
  router.push(`/admin/projects/${id}`)
}

function goActivate(id: number) {
  router.push({ path: `/admin/projects/${id}`, query: { activate: '1' } })
}

function onMoreCommand(row: Project, command: string) {
  if (command === 'activate') {
    goActivate(row.id)
    return
  }
  if (command === 'edit') {
    openEdit(row)
    return
  }
  if (command === 'pause') {
    pauseProject(row)
    return
  }
  if (command === 'delete') {
    removeProject(row)
  }
}

function projectStatusLabel(status?: string | null) {
  if (!status) return '-'
  return dictStore.label('project_status', status) || status
}

function canStartProject(row: Project) {
  return canActivateProject.value && (row.status === 'pending_start' || row.status === 'paused')
}

async function pauseProject(row: Project) {
  try {
    await ElMessageBox.confirm(
      `确认暂停项目「${row.projectName}」？`,
      '暂停确认',
      { type: 'warning', confirmButtonText: '确认暂停', cancelButtonText: '取消' },
    )
    await updateProjectStatus(row.id, 'paused')
    ElMessage.success('项目已暂停')
    await load()
  } catch {
    // canceled
  }
}

async function removeProject(row: Project) {
  try {
    await ElMessageBox.confirm(
      `确认删除项目「${row.projectName}」？该操作不可撤销。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' },
    )
    await deleteProject(row.id)
    ElMessage.success('删除成功')
    await load()
  } catch {
    // canceled
  }
}

onMounted(async () => {
  await dictStore.ensureLoaded()
  await loadCompanies()
  await loadBrands(form.companyId)
  await load()
  if (fromCustomerBrandPath.value && canCreateProject.value) {
    await openCreate()
  }
})
</script>

<style scoped>
.op-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}
.keyword-summary {
  margin-top: 8px;
  font-size: 12px;
  color: #606266;
}
.table-subtext {
  margin-top: 2px;
  font-size: 12px;
  color: #909399;
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
.customer-requirements {
  width: 100%;
  display: grid;
  gap: 12px;
}
.customer-requirement-row {
  padding: 12px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #fafafa;
}
.requirement-row-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}
.requirement-add {
  width: 100%;
  border-style: dashed;
}
.form-tip {
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
}
.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
</style>
