<template>
  <div class="partner-page">
    <div class="partner-page-header">
      <div>
        <div class="partner-page-kicker">项目协作</div>
        <h1 class="partner-page-title">项目管理</h1>
        <div class="partner-page-subtitle">{{ pageSubtitle }}</div>
      </div>
      <div class="partner-page-actions">
        <el-button v-if="canCreateProject" type="primary" @click="openCreate">新增项目</el-button>
      </div>
    </div>

    <el-card shadow="never" class="partner-surface">
      <div class="partner-toolbar">
        <div>
          <div class="partner-toolbar-title">项目列表</div>
          <div class="partner-toolbar-subtitle">共 {{ rows.length }} 个项目，{{ activeProjectCount }} 个执行中或待配置</div>
        </div>
        <div class="partner-toolbar-controls">
          <el-input v-model="keyword" class="partner-search" clearable placeholder="搜索项目名称" @keyup.enter="load" />
          <el-button @click="load">查询</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="partner-table-card">
      <DataState :loading="loading" :empty="!loading && rows.length === 0" empty-text="暂无项目">
        <el-table :data="rows" border table-layout="fixed">
          <el-table-column label="项目名称" min-width="280" show-overflow-tooltip>
            <template #default="scope">
              <div class="partner-entity-cell">
                <div class="partner-entity-avatar">{{ entityInitial(scope.row.projectName) }}</div>
                <div class="min-w-0">
                  <div class="partner-entity-main">{{ scope.row.projectName }}</div>
                  <div class="partner-entity-sub">{{ scope.row.projectAliases || scope.row.primaryGoal || '未填写项目别名' }}</div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="客户 / 品牌" min-width="240">
            <template #default="scope">
              <div class="partner-cell-stack">
                <span class="partner-cell-main">{{ scope.row.companyName || '-' }}</span>
                <span class="partner-cell-sub">{{ scope.row.brandName || '未绑定品牌' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="项目状态" width="150">
            <template #default="scope">
              <span class="partner-status-tag" :class="projectStatusClass(scope.row.status)">
                {{ projectStatusLabel(scope.row.status) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column v-if="canUpdateProject" label="操作" width="100" fixed="right">
            <template #default="scope">
              <div class="partner-row-actions">
                <el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </DataState>
    </el-card>

    <el-dialog
      v-model="formVisible"
      :title="formMode === 'create' ? '新增项目' : '编辑项目'"
      width="860px"
      class="partner-form-dialog partner-project-dialog"
    >
      <div class="project-form-tip">
        <span class="tip-dot">i</span>
        <span>交付员工负责录入项目资料；项目创建完成后再进入拓词管理创建核心问题，启动申请由合伙人负责人确认后提交工单。</span>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="partner-project-form">
        <div class="form-section">
          <div class="form-section-title">基础信息</div>
          <div class="form-grid two-columns">
            <el-form-item label="项目名称" prop="projectName" required>
              <el-input v-model="form.projectName" placeholder="请输入项目名称" />
            </el-form-item>
            <el-form-item label="项目别名">
              <el-input v-model="form.projectAliases" placeholder="多个别名用逗号分隔" />
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
                placeholder="请选择品牌"
                :disabled="!form.companyId || formMode === 'edit' || dependencyLoading"
                :loading="brandLoading"
              >
                <el-option v-for="b in brandOptions" :key="b.id" :label="b.brandName" :value="b.id" />
              </el-select>
            </el-form-item>
          </div>
        </div>

        <div v-if="formMode === 'edit'" class="form-section">
          <div class="form-section-title">核心问题与额度</div>
          <el-form-item label="核心问题组" prop="keywordGroupIds">
            <div class="w-full">
              <el-select
                v-model="form.keywordGroupIds"
                style="width: 100%"
                multiple
                filterable
                collapse-tags
                collapse-tags-tooltip
                :max-collapse-tags="3"
                placeholder="请选择当前客户下的核心问题组"
                :disabled="!form.companyId || dependencyLoading"
                :loading="keywordGroupLoading"
              >
                <el-option
                  v-for="kg in keywordGroupOptions"
                  :key="kg.id"
                  :label="`${kg.name}（核心问题 ${keywordGroupCoreCount(kg)} 条）`"
                  :value="kg.id"
                />
              </el-select>
              <div class="keyword-summary">{{ keywordGroupSummary }}</div>
            </div>
          </el-form-item>
          <el-form-item label="核心问题额度">
            <div class="keyword-quota-panel">
              <div class="channel-note">默认填入当前客户套餐核心问题剩余额度，最大不可超过可分配数量。</div>
              <div class="quota-row">
                <span>核心问题</span>
                <el-input-number v-model="form.coreQuestionLimit" :min="0" :max="coreQuestionMax" controls-position="right" />
                <small>可分配 {{ coreQuestionMax }}</small>
              </div>
            </div>
          </el-form-item>
        </div>

        <div class="form-section">
          <div class="form-section-title">可见渠道额度</div>
          <div v-if="keywordQuota" class="core-quota-overview">
            <div>
              <span>核心问题池额度</span>
              <strong>{{ coreQuestionQuotaLimit }} 个</strong>
            </div>
            <div>
              <span>已分配</span>
              <strong>{{ allocatedCoreQuestionCount }} 个</strong>
            </div>
            <div>
              <span>剩余可分配</span>
              <strong>{{ remainingCoreQuestionCount }} 个</strong>
            </div>
          </div>
          <el-form-item label="展示渠道">
            <div class="channel-allocation-panel">
              <div v-for="item in visibleChannelQuotaItems" :key="item.channelCode" class="channel-row">
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
              <div v-if="visibleChannelQuotaItems.length === 0" class="channel-empty">暂无可分配展示渠道额度</div>
            </div>
          </el-form-item>
        </div>

        <div class="form-section">
          <div class="form-section-title">项目资料补充</div>
          <div class="form-grid two-columns">
            <el-form-item label="地区"><RegionCascader v-model="form.regionCodes" /></el-form-item>
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
            <el-form-item label="核心关键词" prop="coreKeywords" required class="form-wide">
              <el-input
                v-model="form.coreKeywords"
                maxlength="200"
                show-word-limit
                placeholder="可填多个，用逗号隔开，例如：装修公司、旧房翻新、局部改造"
              />
            </el-form-item>
            <el-form-item label="目标受众" prop="targetAudience" required>
              <el-input v-model="form.targetAudience" placeholder="例如：装修业主、二手房翻新用户" />
            </el-form-item>
            <el-form-item label="主目标">
              <el-input v-model="form.primaryGoal" type="textarea" :rows="3" placeholder="补充项目主要目标" />
            </el-form-item>
            <el-form-item label="备注" class="form-wide">
              <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="补充需要合伙人或总部关注的信息" />
            </el-form-item>
          </div>
        </div>
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
const isPartnerStaff = computed(() => userStore.role === 'partner_staff')
const canCreateProject = computed(() => isPartnerStaff.value && userStore.hasPermission('project.create'))
const canUpdateProject = computed(() => isPartnerStaff.value && userStore.hasPermission('project.update'))

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
const partnerVisibleSelfMediaChannels = new Set([
  'self_media:wechat',
  'self_media:douyin',
  'self_media:toutiao',
  'self_media:zhihu',
  'self_media:baijiahao',
  'self_media:xiaohongshu',
])
const visibleChannelQuotaItems = computed(() => channelQuotaItems.value.filter(isPartnerVisibleQuotaChannel))

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
  coreQuestionLimit: 0,
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
  coreKeywords: [
    { required: true, message: '请输入核心关键词', trigger: 'blur' },
    { max: 200, message: '核心关键词不能超过 200 字', trigger: 'blur' },
  ],
  targetRegions: [{ required: true, type: 'array', min: 1, message: '请输入目标区域词', trigger: 'change' }],
  targetAudience: [{ required: true, message: '请输入目标受众', trigger: 'blur' }],
}

const dependencyLoading = computed(() => brandLoading.value || keywordGroupLoading.value)
const activeProjectCount = computed(() =>
  rows.value.filter((item) => ['approved_pending_setup', 'setup_ready', 'active', 'pending_start', 'submitted'].includes(String(item.status || ''))).length
)
const pageSubtitle = computed(() => (
  isPartnerStaff.value
    ? '创建并维护分配客户下的项目资料；项目创建后再进入拓词管理准备核心问题。'
    : '查看合伙人名下项目资料和交付进度；项目资料录入与维护由交付员工处理。'
))

const keywordGroupSummary = computed(() => {
  const selected = new Set(form.keywordGroupIds)
  let saved = 0
  for (const group of keywordGroupOptions.value) {
    if (selected.has(group.id)) {
      saved += keywordGroupCoreCount(group)
    }
  }
  return `已选 ${form.keywordGroupIds.length} 个核心问题组，核心问题 ${saved} 条`
})

const coreQuestionMax = computed(() => Math.max(keywordQuota.value?.inputMaxCoreQuestionCount ?? 0, 0))
const coreQuestionQuotaLimit = computed(() => keywordQuota.value?.coreQuestionQuotaLimit ?? keywordQuota.value?.quotaLimitA ?? 0)
const allocatedCoreQuestionCount = computed(() => keywordQuota.value?.activeAllocatedCoreQuestionCount ?? keywordQuota.value?.activeAllocatedCountA ?? 0)
const remainingCoreQuestionCount = computed(() => keywordQuota.value?.remainingCoreQuestionCount ?? keywordQuota.value?.remainingCountA ?? 0)
const selectedCompanyName = computed(() => {
  const company = companyOptions.value.find((item) => item.id === form.companyId)
  return company?.companyName?.trim() || ''
})

function isPartnerVisibleQuotaChannel(item: ProjectChannelAllocationItem) {
  const code = String(item.channelCode || '')
  return code === 'official_site' || partnerVisibleSelfMediaChannels.has(code)
}

function keywordGroupCoreCount(group: KeywordGroup) {
  return group.savedCoreQuestionCount ?? 0
}

function projectStatusLabel(status?: string | null) {
  if (!status) return '-'
  return dictStore.label('project_status', status) || status
}

function entityInitial(value?: string | null) {
  const text = String(value || '').trim()
  return text ? Array.from(text)[0] : '项'
}

function projectStatusClass(status?: string | null) {
  if (status === 'active' || status === 'setup_ready') return 'is-success'
  if (status === 'approved_pending_setup' || status === 'submitted' || status === 'pending_start') return 'is-warning'
  if (status === 'rejected') return 'is-danger'
  if (status === 'completed' || status === 'archived' || status === 'cancelled' || status === 'expired') return 'is-muted'
  return ''
}

function withSelectedCompanyNameForPackageError(message: string) {
  if (!selectedCompanyName.value || !message.includes('客户尚未绑定有效套餐')) {
    return message
  }
  return `客户「${selectedCompanyName.value}」尚未绑定有效套餐，请先在客户详情中绑定合伙人套餐`
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
  form.coreQuestionLimit = 0
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
  const dependencyTasks = formMode.value === 'edit'
    ? [loadBrands(form.companyId), loadKeywordGroups(form.companyId)]
    : [loadBrands(form.companyId)]
  await Promise.all(dependencyTasks)
  try {
    await Promise.all([
      loadChannelAllocationQuota(form.companyId),
      loadKeywordGroupQuota(form.companyId, undefined, formMode.value === 'edit'),
    ])
  } catch (err) {
    resetPackageQuota()
    ElMessage.error(withSelectedCompanyNameForPackageError(errorMessage(err, '加载客户套餐额度失败')))
    if (formMode.value === 'create') {
      formVisible.value = false
      resetForm()
    }
  }
}

function resetPackageQuota() {
  keywordQuota.value = null
  form.coreQuestionLimit = 0
  channelQuotaItems.value = []
  allocationVersion.value = null
  form.channelAllocations = {}
}

async function loadKeywordGroupQuota(companyId?: number | null, excludeProjectId?: number | null, applyDefault = true) {
  if (!companyId) {
    keywordQuota.value = null
    form.coreQuestionLimit = 0
    return
  }
  const { data } = await getProjectKeywordGroupQuota({
    companyId,
    excludeProjectId: excludeProjectId || undefined,
  }, true)
  keywordQuota.value = data.data
  if (applyDefault) {
    form.coreQuestionLimit = data.data.remainingCoreQuestionCount ?? 0
  }
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
  }, true)
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
  form.coreQuestionLimit = row.planCoreQuestionLimit ?? 0
  form.regionCodes = regionCodesFromPayload(row)
  form.targetRegions = parseStringArray(row.targetRegions)
  form.coreKeywords = row.coreKeywords || ''
  form.targetAudience = row.targetAudience || ''
  form.deliveryMode = row.deliveryMode || 'managed'
  form.primaryGoal = row.primaryGoal || ''
  form.remark = row.remark || ''
  await Promise.all([loadBrands(form.companyId), loadKeywordGroups(form.companyId)])
  try {
    await loadChannelAllocationQuota(form.companyId, editingId.value)
    await loadKeywordGroupQuota(form.companyId, editingId.value, false)
  } catch (err) {
    resetPackageQuota()
    ElMessage.error(withSelectedCompanyNameForPackageError(errorMessage(err, '加载客户套餐额度失败')))
  }
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
    const visibleChannelCodes = new Set(visibleChannelQuotaItems.value.map((item) => item.channelCode))
    const payload = {
      projectName: form.projectName,
      projectAliases: form.projectAliases || undefined,
      companyId: form.companyId,
      brandId: form.brandId,
      allocationVersion: allocationVersion.value,
      channelAllocations: Object.entries(form.channelAllocations)
        .filter(([channelCode]) => visibleChannelCodes.has(channelCode))
        .map(([channelCode, allocatedCount]) => ({
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
      await createProject(payload, true)
    } else if (editingId.value) {
      await updateProject(editingId.value, {
        ...payload,
        keywordGroupIds: form.keywordGroupIds,
        keywordGroupLimitA: form.coreQuestionLimit,
        keywordGroupLimitB: 0,
        keywordGroupLimitC: 0,
      }, true)
    }

    formVisible.value = false
    ElMessage.success('保存成功')
    await load()
  } catch (err) {
    ElMessage.error(withSelectedCompanyNameForPackageError(errorMessage(err, '保存失败')))
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
    ElMessage.error(errorMessage(err, '加载核心问题组失败'))
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
:deep(.partner-project-dialog .el-dialog__body) {
  padding-top: 18px;
}

.project-form-tip {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 14px 16px;
  margin-bottom: 18px;
  border: 1px solid #bfdbfe;
  border-radius: 10px;
  background: linear-gradient(135deg, #eff6ff 0%, #f0fdfa 100%);
  color: #475569;
  font-size: 14px;
  line-height: 1.7;
  font-weight: 700;
}

.tip-dot {
  width: 18px;
  height: 18px;
  flex: 0 0 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-top: 2px;
  border-radius: 50%;
  background: #2563eb;
  color: #fff;
  font-size: 12px;
  font-weight: 900;
}

.partner-project-form {
  display: grid;
  gap: 16px;
}

.form-section {
  padding: 16px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #fff;
}

.form-section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  color: #0f172a;
  font-size: 15px;
  font-weight: 900;
}

.form-section-title::before {
  content: '';
  width: 4px;
  height: 16px;
  border-radius: 999px;
  background: linear-gradient(180deg, #2563eb, #14b8a6);
}

.form-grid {
  display: grid;
  gap: 16px 18px;
}

.two-columns {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.form-wide {
  grid-column: 1 / -1;
}

:deep(.partner-project-form .el-form-item) {
  margin-bottom: 0;
}

:deep(.partner-project-form .el-form-item__label) {
  padding-bottom: 8px;
  color: #334155;
  font-size: 14px;
  line-height: 1.2;
  font-weight: 800;
}

:deep(.partner-project-form .el-input__wrapper),
:deep(.partner-project-form .el-select__wrapper),
:deep(.partner-project-form .el-textarea__inner) {
  border-radius: 10px;
  box-shadow: 0 0 0 1px #dbe4f0 inset;
}

.keyword-summary {
  margin-top: 8px;
  font-size: 12px;
  color: #64748b;
  font-weight: 700;
}
.keyword-quota-panel {
  width: 100%;
  display: grid;
  gap: 12px;
  padding: 12px;
  border: 1px solid #dbeafe;
  border-radius: 10px;
  background: #f8fbff;
}
.quota-row {
  display: grid;
  grid-template-columns: 72px minmax(160px, 220px) 1fr;
  align-items: center;
  gap: 10px;
  color: #0f172a;
  font-weight: 800;
}
.quota-row small {
  color: #64748b;
  font-weight: 700;
}
.core-quota-overview {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin: 10px 0 12px;
}
.core-quota-overview > div {
  padding: 12px;
  border: 1px solid #dbeafe;
  border-radius: 10px;
  background: linear-gradient(135deg, #fff 0%, #f8fbff 100%);
}
.core-quota-overview span {
  display: block;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}
.core-quota-overview strong {
  display: block;
  margin-top: 6px;
  color: #0f172a;
  font-size: 18px;
  font-weight: 900;
}
.channel-allocation-panel {
  width: 100%;
  display: grid;
  gap: 12px;
  padding: 12px;
  border: 1px solid #dbeafe;
  border-radius: 10px;
  background: #f8fbff;
}
.channel-note {
  font-size: 12px;
  color: #64748b;
  font-weight: 700;
}
.channel-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #fff;
}
.channel-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  color: #0f172a;
  font-weight: 800;
}
.channel-meta small {
  color: #64748b;
  font-weight: 600;
}
.channel-empty {
  padding: 18px;
  border: 1px dashed #bfdbfe;
  border-radius: 10px;
  background: #fff;
  color: #64748b;
  text-align: center;
  font-size: 13px;
  font-weight: 700;
}

@media (max-width: 768px) {
  .two-columns {
    grid-template-columns: 1fr;
  }
  .core-quota-overview {
    grid-template-columns: 1fr;
  }
}
</style>
