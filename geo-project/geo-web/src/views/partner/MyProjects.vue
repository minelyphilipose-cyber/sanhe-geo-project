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
          <el-table-column label="协作进度" min-width="230">
            <template #default="scope">
              <div class="workflow-cell" :class="projectWorkflowClass(scope.row)">
                <span class="workflow-badge">
                  <i />
                  {{ projectWorkflowLabel(scope.row) }}
                </span>
                <small>{{ projectWorkflowHint(scope.row) }}</small>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="340" fixed="right">
            <template #default="scope">
              <div class="partner-row-actions">
                <el-button link type="primary" @click="openDetail(scope.row)">详情</el-button>
                <el-button
                  v-if="canViewSubmissionChecklist(scope.row)"
                  link
                  type="primary"
                  @click="openSubmissionChecklist(scope.row)"
                >
                  检查清单
                </el-button>
                <el-button
                  v-if="canCompleteProjectEntry(scope.row)"
                  link
                  type="success"
                  :loading="completingCompanyId === scope.row.companyId"
                  @click="completeProjectEntry(scope.row)"
                >
                  提交负责人确认
                </el-button>
                <el-button
                  v-if="canSubmitStartRequest(scope.row)"
                  link
                  type="primary"
                  :loading="submittingStartRequestId === scope.row.id"
                  @click="submitStartRequest(scope.row)"
                >
                  提交工单
                </el-button>
                <el-button
                  v-if="canReturnEntry(scope.row)"
                  link
                  type="warning"
                  :loading="returningCompanyId === scope.row.companyId"
                  @click="openReturnEntry(scope.row)"
                >
                  退回修改
                </el-button>
                <span v-else-if="isPartnerOwner && scope.row.status === 'submitted'" class="partner-row-note">工单已提交</span>
                <el-button v-if="canEditProject(scope.row)" link type="primary" @click="openEdit(scope.row)">编辑</el-button>
                <el-button v-if="canDeleteProject" link type="danger" @click="removeProject(scope.row)">删除</el-button>
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

        <div class="form-section">
          <div class="form-section-head">
            <div>
              <div class="form-section-title">竞品信息</div>
              <p>用于后续诊断识别竞品提及。请录入竞品全名和常见简称/别名。</p>
            </div>
            <el-button size="small" plain :disabled="form.competitors.length >= 3" @click="addCompetitor">添加竞品</el-button>
          </div>
          <div class="competitor-list">
            <div v-for="(item, index) in form.competitors" :key="item.uid" class="competitor-row">
              <div class="competitor-index">{{ index + 1 }}</div>
              <el-form-item label="竞品全名" required>
                <el-input v-model="item.competitorName" maxlength="128" placeholder="请输入竞品全名" />
              </el-form-item>
              <el-form-item label="简称/别名">
                <el-select
                  v-model="item.aliases"
                  multiple
                  filterable
                  allow-create
                  default-first-option
                  collapse-tags
                  collapse-tags-tooltip
                  style="width: 100%"
                  placeholder="输入后回车，可添加多个"
                />
              </el-form-item>
              <el-button link type="danger" :disabled="form.competitors.length <= 1" @click="removeCompetitor(index)">删除</el-button>
            </div>
          </div>
        </div>

        <div v-if="formMode === 'edit'" class="form-section">
          <div class="form-section-title">核心问题与额度</div>
          <el-form-item label="核心问题组" prop="keywordGroupIds">
            <div class="w-full">
              <el-select
                v-if="!keywordGroupLocked"
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
              <div v-else class="locked-keyword-groups">
                <div v-if="selectedKeywordGroups.length" class="locked-keyword-group-list">
                  <span v-for="kg in selectedKeywordGroups" :key="kg.id" class="locked-keyword-group-tag">
                    {{ kg.name }}（核心问题 {{ keywordGroupCoreCount(kg) }} 条）
                  </span>
                </div>
                <div v-else class="locked-keyword-group-empty">暂无已绑定核心问题组</div>
                <div class="locked-keyword-group-tip">项目拓词组已提交总部并锁定，核心问题组仅可查看，不可删除或调整。</div>
              </div>
              <div class="keyword-summary">{{ keywordGroupSummary }}</div>
            </div>
          </el-form-item>
          <el-form-item label="核心问题额度">
            <div class="keyword-quota-panel">
              <div class="channel-note">{{ coreQuestionQuotaNote }}</div>
              <div class="quota-row">
                <span>核心问题</span>
                <el-input-number
                  v-model="form.coreQuestionLimit"
                  :min="0"
                  :max="coreQuestionMax"
                  :disabled="keywordGroupLocked"
                  controls-position="right"
                />
                <small>{{ coreQuestionQuotaInputTip }}</small>
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

    <el-dialog
      v-model="checklistVisible"
      title="提交前检查清单"
      width="920px"
      class="partner-form-dialog project-checklist-dialog"
    >
      <DataState :loading="checklistLoading" :empty="!checklistLoading && !checklistReadiness" empty-text="暂无检查结果">
        <div v-if="checklistReadiness" class="checklist-panel">
          <div class="checklist-head">
            <div>
              <strong>{{ selectedChecklistProject?.projectName || '项目资料' }}</strong>
              <span>交付员工按清单补齐资料，全部完成后提交负责人确认。</span>
            </div>
            <div class="checklist-summary">{{ checklistReadyCount }} / {{ checklistTotalCount }}</div>
          </div>
          <div class="checklist-status" :class="{ ready: checklistReady }">
            <strong>{{ checklistReady ? '资料已完整，可提交负责人确认' : `还有 ${checklistPendingCount} 项需要补充` }}</strong>
            <span>{{ checklistReady ? '负责人确认后再提交总部启动工单。' : '请先处理待补充项，避免负责人提交总部时被拦截。' }}</span>
          </div>
          <div class="checklist-grid">
            <div
              v-for="item in checklistItems"
              :key="item.key"
              class="checklist-item"
              :class="{ done: item.ready }"
            >
              <div class="checklist-icon">{{ item.ready ? '✓' : '!' }}</div>
              <div class="checklist-body">
                <div class="checklist-title">
                  <strong>{{ item.title }}</strong>
                  <el-tag size="small" :type="item.ready ? 'success' : 'danger'" round>
                    {{ item.ready ? '已完成' : '待补充' }}
                  </el-tag>
                </div>
                <p>{{ item.description }}</p>
                <span>{{ item.ready ? '无需处理' : item.actionText || '去补充' }}</span>
              </div>
            </div>
          </div>
        </div>
      </DataState>
      <template #footer>
        <el-button @click="checklistVisible = false">关闭</el-button>
        <el-button plain @click="reloadSubmissionChecklist">刷新检查</el-button>
        <el-button
          v-if="selectedChecklistProject && canCompleteProjectEntry(selectedChecklistProject)"
          type="primary"
          :loading="completingCompanyId === selectedChecklistProject.companyId"
          @click="completeProjectEntryFromChecklist"
        >
          提交负责人确认
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="returnVisible" title="退回交付员工修改" width="560px" class="partner-form-dialog">
      <div class="project-form-tip">
        <span class="tip-dot">i</span>
        <span>退回后客户会回到“项目与拓词录入中”，交付员工修改完成后需要再次提交负责人确认。</span>
      </div>
      <el-form label-position="top">
        <el-form-item label="退回原因" required>
          <el-input
            v-model="returnForm.reason"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="请说明需要补充或修正的项目、品牌、竞品、核心问题或图片资产内容"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="returnVisible = false">取消</el-button>
        <el-button type="warning" :loading="returningCompanyId === returningProject?.companyId" @click="submitReturnEntry">确认退回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  completeCompanyEntry,
  getBrandList,
  getCompanyList,
  getCompanySubmissionReadiness,
  returnCompanyEntry,
  type PartnerSubmissionReadiness,
  type PartnerSubmissionReadinessItem,
} from '@/api/customer'
import {
  createProject,
  deleteProject,
  getProjectChannelAllocationQuota,
  getProjectKeywordGroupQuota,
  getKeywordGroupPage,
  getProjectList,
  submitPartnerProjectStartRequest,
  updateProject,
} from '@/api/project'
import {
  getProjectMobileDashboardCompetitors,
  updateProjectMobileDashboardCompetitors,
  type ProjectCompetitorConfig,
  type ProjectCompetitorConfigPayloadItem,
} from '@/api/mobileDashboard'
import { getGeoProjectWorkorders } from '@/api/geoQuestion'
import type { Brand, Company, KeywordGroup, Project, ProjectChannelAllocationItem, ProjectKeywordGroupQuota } from '@/types'
import DataState from '@/components/ui/DataState.vue'
import RegionCascader from '@/components/ui/RegionCascader.vue'
import { regionCodesFromPayload, regionPayloadFromCodes } from '@/constants/region'
import { useDictStore } from '@/stores/dict'
import { useUserStore } from '@/stores/user'
import { errorMessage } from '@/utils/error'

const dictStore = useDictStore()
const userStore = useUserStore()
const route = useRoute()
const router = useRouter()
const isPartnerOwner = computed(() => userStore.role === 'partner')
const isPartnerStaff = computed(() => userStore.role === 'partner_staff')
const canCreateProject = computed(() => isPartnerStaff.value && userStore.hasPermission('project.create'))
const canUpdateProject = computed(() => isPartnerStaff.value && userStore.hasPermission('project.update'))
const canDeleteProject = computed(() => isPartnerStaff.value && userStore.hasPermission('project.delete'))

const loading = ref(false)
const saving = ref(false)
const completingCompanyId = ref<number | null>(null)
const submittingStartRequestId = ref<number | null>(null)
const returningCompanyId = ref<number | null>(null)
const brandLoading = ref(false)
const keywordGroupLoading = ref(false)
const keyword = ref('')
const rows = ref<Project[]>([])
const companyOptions = ref<Company[]>([])
const projectEntryReadinessById = ref<Record<number, boolean>>({})
const brandOptions = ref<Brand[]>([])
const keywordGroupOptions = ref<KeywordGroup[]>([])
const channelQuotaItems = ref<ProjectChannelAllocationItem[]>([])
const keywordQuota = ref<ProjectKeywordGroupQuota | null>(null)
const keywordGroupLocked = ref(false)
const allocationVersion = ref<number | null>(null)
const checklistVisible = ref(false)
const checklistLoading = ref(false)
const checklistReadiness = ref<PartnerSubmissionReadiness | null>(null)
const selectedChecklistProject = ref<Project | null>(null)
const returnVisible = ref(false)
const returningProject = ref<Project | null>(null)
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

interface CompetitorFormItem {
  uid: number
  id?: number
  competitorName: string
  aliases: string[]
}

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
  competitors: [] as CompetitorFormItem[],
})
const returnForm = reactive({
  reason: '',
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
const scopedCompanyId = computed(() => {
  const raw = Array.isArray(route.query.companyId) ? route.query.companyId[0] : route.query.companyId
  const id = Number(raw)
  return Number.isFinite(id) && id > 0 ? id : undefined
})

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
const selectedKeywordGroups = computed(() => {
  const optionMap = new Map(keywordGroupOptions.value.map((item) => [item.id, item]))
  return form.keywordGroupIds.map((id) => optionMap.get(id) || ({
    id,
    companyId: form.companyId || 0,
    name: `核心问题组 #${id}`,
    savedCoreQuestionCount: 0,
    createdAt: '',
    updatedAt: '',
  } as KeywordGroup))
})

const lockedCoreQuestionCount = computed(() => keywordGroupLocked.value ? Math.max(Number(form.coreQuestionLimit || 0), 0) : 0)
const rawCoreQuestionMax = computed(() => Math.max(keywordQuota.value?.inputMaxCoreQuestionCount ?? 0, 0))
const rawAllocatedCoreQuestionCount = computed(() => keywordQuota.value?.activeAllocatedCoreQuestionCount ?? keywordQuota.value?.activeAllocatedCountA ?? 0)
const rawRemainingCoreQuestionCount = computed(() => keywordQuota.value?.remainingCoreQuestionCount ?? keywordQuota.value?.remainingCountA ?? 0)
const coreQuestionMax = computed(() => keywordGroupLocked.value ? lockedCoreQuestionCount.value : rawCoreQuestionMax.value)
const coreQuestionQuotaLimit = computed(() => keywordQuota.value?.coreQuestionQuotaLimit ?? keywordQuota.value?.quotaLimitA ?? 0)
const allocatedCoreQuestionCount = computed(() => rawAllocatedCoreQuestionCount.value + lockedCoreQuestionCount.value)
const remainingCoreQuestionCount = computed(() => Math.max(rawRemainingCoreQuestionCount.value - lockedCoreQuestionCount.value, 0))
const coreQuestionQuotaNote = computed(() => keywordGroupLocked.value
  ? '项目拓词组已锁定，核心问题额度仅可查看，不可再修改。'
  : '默认填入当前客户套餐核心问题剩余额度，最大不可超过可分配数量。')
const coreQuestionQuotaInputTip = computed(() => keywordGroupLocked.value ? '已锁定' : `可分配 ${coreQuestionMax.value}`)
const selectedCompanyName = computed(() => {
  const company = companyOptions.value.find((item) => item.id === form.companyId)
  return company?.companyName?.trim() || ''
})
const checklistItems = computed<PartnerSubmissionReadinessItem[]>(() => checklistReadiness.value?.items || [])
const checklistReady = computed(() => Boolean(checklistReadiness.value?.ready))
const checklistTotalCount = computed(() => checklistReadiness.value?.totalCount || checklistItems.value.length)
const checklistReadyCount = computed(() => checklistReadiness.value?.readyCount || checklistItems.value.filter((item) => item.ready).length)
const checklistPendingCount = computed(() => checklistReadiness.value?.pendingCount ?? Math.max(checklistTotalCount.value - checklistReadyCount.value, 0))
let competitorUid = 1

function newCompetitorItem(source?: Partial<CompetitorFormItem>): CompetitorFormItem {
  return {
    uid: competitorUid++,
    id: source?.id,
    competitorName: source?.competitorName || '',
    aliases: [...(source?.aliases || [])],
  }
}

function addCompetitor() {
  if (form.competitors.length >= 3) {
    ElMessage.warning('竞品最多添加 3 个')
    return
  }
  form.competitors.push(newCompetitorItem())
}

function removeCompetitor(index: number) {
  form.competitors.splice(index, 1)
  if (form.competitors.length === 0) {
    addCompetitor()
  }
}

function normalizeCompetitorPayload(): ProjectCompetitorConfigPayloadItem[] | null {
  const items = form.competitors
    .map((item, index) => ({
      id: item.id,
      competitorName: item.competitorName.trim(),
      aliases: item.aliases.map((alias) => String(alias).trim()).filter((alias, aliasIndex, arr) => alias && arr.indexOf(alias) === aliasIndex),
      advantages: null,
      disadvantages: null,
      displayOrder: index + 1,
      active: true,
      qaStatus: 'passed',
    }))
    .filter((item) => item.competitorName)
  if (items.length === 0) {
    ElMessage.warning('请至少添加 1 个竞品全名')
    return null
  }
  return items
}

function applyCompetitors(items: ProjectCompetitorConfig[]) {
  const activeItems = (items || [])
    .filter((item) => item.status !== 'disabled')
    .slice(0, 3)
    .map((item) => newCompetitorItem({
      id: item.id,
      competitorName: item.competitorName || '',
      aliases: item.aliases || [],
    }))
  form.competitors = activeItems.length ? activeItems : [newCompetitorItem()]
}

function isPartnerVisibleQuotaChannel(item: ProjectChannelAllocationItem) {
  const code = String(item.channelCode || '')
  return item.enabled && (code === 'official_site' || partnerVisibleSelfMediaChannels.has(code))
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

function canSubmitStartRequest(row: Project) {
  if (!isPartnerOwner.value) return false
  return companyWorkflowStatus(row.companyId) === 'entry_completed'
    && String(row.status || '') === 'pending_start'
}

function canReturnEntry(row: Project) {
  if (!isPartnerOwner.value) return false
  return companyWorkflowStatus(row.companyId) === 'entry_completed'
    && ['pending_start', 'rejected'].includes(String(row.status || ''))
}

function projectWorkflowLabel(row: Project) {
  const projectStatus = String(row.status || '')
  const workflowStatus = companyWorkflowStatus(row.companyId)
  if (projectStatus === 'submitted') return '已提交总部'
  if (projectStatus === 'rejected') return workflowStatus === 'project_entry' ? '驳回修改中' : '总部驳回待负责人处理'
  if (workflowStatus === 'entry_completed') return '待负责人提交总部'
  if (workflowStatus === 'project_entry') return '项目资料录入中'
  if (workflowStatus === 'package_bound') return '待通知继续录入'
  if (workflowStatus === 'package_requested') return '待负责人加套餐'
  return '资料准备中'
}

function projectWorkflowHint(row: Project) {
  const projectStatus = String(row.status || '')
  const workflowStatus = companyWorkflowStatus(row.companyId)
  if (projectStatus === 'submitted') return '总部已收到启动工单，等待审批'
  if (projectStatus === 'rejected') {
    return workflowStatus === 'project_entry'
      ? '交付员工修改完成后在项目列表提交负责人确认'
      : '负责人查看驳回原因后退回交付员工修改'
  }
  if (workflowStatus === 'entry_completed') return '负责人核对后可提交总部工单'
  if (workflowStatus === 'project_entry') return '交付员工补齐项目、品牌、竞品和核心问题'
  if (workflowStatus === 'package_bound') return '负责人通知交付员工继续录入项目资料'
  if (workflowStatus === 'package_requested') return '负责人先绑定客户套餐'
  return '先完善客户与品牌基础资料'
}

function projectWorkflowClass(row: Project) {
  const projectStatus = String(row.status || '')
  const workflowStatus = companyWorkflowStatus(row.companyId)
  if (projectStatus === 'rejected') return 'is-danger'
  if (projectStatus === 'submitted' || workflowStatus === 'submitted_to_hq') return 'is-success'
  if (workflowStatus === 'entry_completed') return 'is-ready'
  if (workflowStatus === 'project_entry') return 'is-info'
  if (workflowStatus === 'package_requested') return 'is-warning'
  return 'is-draft'
}

function companyWorkflowStatus(companyId?: number | null) {
  if (!companyId) return 'draft'
  const company = companyOptions.value.find((item) => item.id === companyId)
  return company?.partnerWorkflowStatus || 'draft'
}

function companyProjectsReady(companyId?: number | null) {
  if (!companyId) return false
  const projects = rows.value.filter((item) => item.companyId === companyId)
  return projects.length > 0 && projects.every((item) => projectEntryReadinessById.value[item.id])
}

function canCompleteProjectEntry(row: Project) {
  return isPartnerStaff.value
    && companyWorkflowStatus(row.companyId) === 'project_entry'
    && companyProjectsReady(row.companyId)
}

function canViewSubmissionChecklist(row: Project) {
  return isPartnerStaff.value
    && Boolean(row.companyId)
    && ['project_entry', 'entry_completed'].includes(companyWorkflowStatus(row.companyId))
}

function canEditProject(row: Project) {
  if (!canUpdateProject.value) return false
  if (companyWorkflowStatus(row.companyId) !== 'project_entry') return false
  return ['draft', 'pending_start', 'rejected'].includes(String(row.status || ''))
}

function channelPeriodText(periodType?: string | null) {
  if (!periodType || periodType === 'none') return '-'
  const map: Record<string, string> = {
    day: '日',
    week: '周',
    month: '月',
    quarter: '季度',
    year: '年',
  }
  return map[periodType] || periodType
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
  keywordGroupLocked.value = false
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
  form.competitors = [newCompetitorItem()]
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
  const periodType = channelPeriodText(item.periodType)
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
  keywordGroupLocked.value = projectHasLockedKeywordGroups(row)
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
  form.competitors = [newCompetitorItem()]
  await Promise.all([loadBrands(form.companyId), loadKeywordGroups(form.companyId)])
  try {
    await loadChannelAllocationQuota(form.companyId, editingId.value)
    await loadKeywordGroupQuota(form.companyId, editingId.value, false)
  } catch (err) {
    resetPackageQuota()
    ElMessage.error(withSelectedCompanyNameForPackageError(errorMessage(err, '加载客户套餐额度失败')))
  }
  await Promise.all([
    loadProjectCompetitors(row.id),
    loadKeywordGroupLockState(row.id),
  ])
  formVisible.value = true
}

function openDetail(row: Project) {
  router.push({ name: 'PartnerProjectDetail', params: { id: row.id } })
}

function projectHasLockedKeywordGroups(row: Project) {
  return Boolean(
    (row.selectedCoreQuestionSavedKeywords ?? row.selectedKeywordSavedKeywordsA ?? 0) > 0
    || ['submitted', 'approved_pending_setup', 'setup_ready', 'active', 'completed'].includes(String(row.status || '')),
  )
}

async function removeProject(row: Project) {
  try {
    await ElMessageBox.confirm(
      `确认删除项目「${row.projectName}」？删除后项目资料、额度分配将按后端规则释放，该操作不可撤销。`,
      '删除项目',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' },
    )
    await deleteProject(row.id)
    ElMessage.success('删除成功')
    await load()
  } catch (err) {
    if (err === 'cancel' || err === 'close') return
    ElMessage.error(errorMessage(err, '删除失败'))
  }
}

async function submitStartRequest(row: Project) {
  try {
    await ElMessageBox.confirm(
      `确认将项目「${row.projectName}」提交总部启动工单？提交前请确认项目资料、竞品信息、核心问题和展示渠道额度均已核对无误。`,
      '提交总部工单',
      { type: 'warning', confirmButtonText: '确认提交', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  submittingStartRequestId.value = row.id
  try {
    await submitPartnerProjectStartRequest(row.id)
    ElMessage.success('工单已提交总部')
    await load()
  } catch (err) {
    ElMessage.error(errorMessage(err, '提交工单失败'))
  } finally {
    submittingStartRequestId.value = null
  }
}

async function completeProjectEntry(row: Project) {
  if (!row.companyId) return false
  try {
    await ElMessageBox.confirm(
      `确认将项目「${row.projectName}」资料提交负责人复核？提交后负责人将在项目管理中核对并提交总部工单。`,
      '提交负责人确认',
      { type: 'warning', confirmButtonText: '确认提交', cancelButtonText: '取消' },
    )
  } catch {
    return false
  }
  completingCompanyId.value = row.companyId
  try {
    await completeCompanyEntry(row.companyId)
    ElMessage.success('已提交负责人确认')
    await Promise.all([loadCompanies(), load()])
    return true
  } catch (err) {
    ElMessage.error(errorMessage(err, '提交负责人确认失败'))
    return false
  } finally {
    completingCompanyId.value = null
  }
}

async function openSubmissionChecklist(row: Project) {
  selectedChecklistProject.value = row
  checklistVisible.value = true
  await loadSubmissionChecklist(row.companyId)
}

async function loadSubmissionChecklist(companyId?: number | null) {
  if (!companyId) {
    checklistReadiness.value = null
    return
  }
  checklistLoading.value = true
  try {
    const { data } = await getCompanySubmissionReadiness(companyId)
    checklistReadiness.value = data.data
  } catch (err) {
    checklistReadiness.value = null
    ElMessage.error(errorMessage(err, '加载提交前检查清单失败'))
  } finally {
    checklistLoading.value = false
  }
}

async function reloadSubmissionChecklist() {
  await loadSubmissionChecklist(selectedChecklistProject.value?.companyId)
}

async function completeProjectEntryFromChecklist() {
  const row = selectedChecklistProject.value
  if (!row) return
  const ok = await completeProjectEntry(row)
  if (ok) {
    checklistVisible.value = false
  }
}

function openReturnEntry(row: Project) {
  returningProject.value = row
  returnForm.reason = ''
  returnVisible.value = true
}

async function submitReturnEntry() {
  const row = returningProject.value
  const reason = returnForm.reason.trim()
  if (!row?.companyId) return
  if (!reason) {
    ElMessage.warning('请填写退回原因，方便交付员工明确修改方向')
    return
  }
  returningCompanyId.value = row.companyId
  try {
    await returnCompanyEntry(row.companyId, { reason })
    returnVisible.value = false
    ElMessage.success('已退回交付员工修改')
    await Promise.all([loadCompanies(), load()])
  } catch (err) {
    ElMessage.error(errorMessage(err, '退回修改失败'))
  } finally {
    returningCompanyId.value = null
  }
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
  const competitors = normalizeCompetitorPayload()
  if (!competitors) {
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
      keywordGroupLimitA: form.coreQuestionLimit,
      keywordGroupLimitB: 0,
      keywordGroupLimitC: 0,
      remark: form.remark || undefined,
    }

    if (formMode.value === 'create') {
      const { data } = await createProject(payload, true)
      if (data.data?.id) {
        formMode.value = 'edit'
        editingId.value = data.data.id
        await saveProjectCompetitors(data.data.id, competitors)
      }
    } else if (editingId.value) {
      const {
        keywordGroupLimitA: _keywordGroupLimitA,
        keywordGroupLimitB: _keywordGroupLimitB,
        keywordGroupLimitC: _keywordGroupLimitC,
        ...lockedUpdatePayload
      } = payload
      const updatePayload = keywordGroupLocked.value
        ? lockedUpdatePayload
        : {
            ...payload,
            keywordGroupIds: form.keywordGroupIds,
          }
      await updateProject(editingId.value, updatePayload, true)
      await saveProjectCompetitors(editingId.value, competitors)
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

async function loadKeywordGroupLockState(projectId: number) {
  try {
    const { data } = await getGeoProjectWorkorders(projectId)
    keywordGroupLocked.value = keywordGroupLocked.value || (data.data || []).some((item) =>
      item.partnerReviewStatus === 'submitted_to_hq'
      || ['committed', 'completed', 'active'].includes(String(item.status || '')),
    )
  } catch (err) {
    ElMessage.error(errorMessage(err, '加载项目拓词组锁定状态失败'))
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

async function loadProjectCompetitors(projectId: number) {
  try {
    const { data } = await getProjectMobileDashboardCompetitors(projectId)
    applyCompetitors(data.data || [])
  } catch (err) {
    form.competitors = [newCompetitorItem()]
    ElMessage.error(errorMessage(err, '加载竞品信息失败'))
  }
}

async function saveProjectCompetitors(projectId: number, competitors: ProjectCompetitorConfigPayloadItem[]) {
  await updateProjectMobileDashboardCompetitors(projectId, competitors)
}

async function load() {
  loading.value = true
  try {
    const { data } = await getProjectList({
      current: 1,
      size: 200,
      keyword: keyword.value || undefined,
      companyId: scopedCompanyId.value,
    })
    rows.value = data.data.records || []
    projectEntryReadinessById.value = isPartnerStaff.value
      ? await buildProjectEntryReadiness(rows.value)
      : {}
  } catch {
    rows.value = []
    projectEntryReadinessById.value = {}
    ElMessage.error('加载项目失败')
  } finally {
    loading.value = false
  }
}

async function buildProjectEntryReadiness(projects: Project[]) {
  const next: Record<number, boolean> = {}
  await Promise.all(projects.map(async (project) => {
    const allocatedCount = projectCoreQuestionLimit(project)
    if (allocatedCount <= 0) {
      next[project.id] = false
      return
    }
    try {
      const { data } = await getGeoProjectWorkorders(project.id)
      const actualCount = (data.data || [])
        .filter((item) => item.status === 'committed')
        .reduce((sum, item) => sum + Number(item.countTotal || 0), 0)
      next[project.id] = actualCount === allocatedCount
    } catch {
      next[project.id] = false
    }
  }))
  return next
}

function projectCoreQuestionLimit(project: Project) {
  return Number(project.planCoreQuestionLimit ?? project.planKeywordGroupLimitA ?? project.planKeywordGroupLimit ?? 0)
}

async function openEditFromRouteQuery() {
  const raw = Number(route.query.editProjectId)
  if (!canUpdateProject.value || !Number.isFinite(raw) || raw <= 0) {
    return
  }
  const row = rows.value.find((item) => item.id === raw)
  if (row && canEditProject(row)) {
    await openEdit(row)
  } else if (row) {
    ElMessage.warning('当前项目已进入负责人确认或总部处理阶段，不能继续编辑')
  }
  router.replace({ name: 'MyProjects', query: {} })
}

onMounted(async () => {
  await dictStore.ensureLoaded()
  await loadCompanies()
  await load()
  await openEditFromRouteQuery()
})
</script>

<style scoped>
:deep(.partner-project-dialog .el-dialog__body) {
  padding-top: 18px;
}

.workflow-cell {
  --workflow-color: #64748b;
  --workflow-bg: #f8fafc;
  --workflow-border: #e2e8f0;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 7px;
  min-width: 0;
}

.workflow-cell.is-draft {
  --workflow-color: #64748b;
  --workflow-bg: #f8fafc;
  --workflow-border: #e2e8f0;
}

.workflow-cell.is-warning {
  --workflow-color: #b45309;
  --workflow-bg: #fffbeb;
  --workflow-border: #fde68a;
}

.workflow-cell.is-info {
  --workflow-color: #2563eb;
  --workflow-bg: #eff6ff;
  --workflow-border: #bfdbfe;
}

.workflow-cell.is-success {
  --workflow-color: #047857;
  --workflow-bg: #ecfdf5;
  --workflow-border: #a7f3d0;
}

.workflow-cell.is-ready {
  --workflow-color: #7c3aed;
  --workflow-bg: #f5f3ff;
  --workflow-border: #ddd6fe;
}

.workflow-cell.is-danger {
  --workflow-color: #dc2626;
  --workflow-bg: #fef2f2;
  --workflow-border: #fecaca;
}

.workflow-badge {
  display: inline-flex;
  max-width: 100%;
  align-items: center;
  gap: 7px;
  border: 1px solid var(--workflow-border);
  border-radius: 999px;
  padding: 5px 11px;
  background: var(--workflow-bg);
  color: var(--workflow-color);
  font-size: 12px;
  font-weight: 900;
  line-height: 1;
}

.workflow-badge i {
  width: 7px;
  height: 7px;
  flex: 0 0 auto;
  border-radius: 999px;
  background: var(--workflow-color);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--workflow-color) 14%, transparent);
}

.workflow-cell small {
  max-width: 100%;
  color: #64748b;
  font-size: 12px;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

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

.form-section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.form-section-head .form-section-title {
  margin-bottom: 4px;
}

.form-section-head p {
  margin: 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.5;
  font-weight: 700;
}

.partner-row-note {
  color: #047857;
  font-size: 13px;
  font-weight: 800;
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

.locked-keyword-groups {
  display: grid;
  gap: 10px;
  min-height: 46px;
  padding: 10px 12px;
  border: 1px solid #dbeafe;
  border-radius: 10px;
  background: #f8fbff;
}

.locked-keyword-group-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.locked-keyword-group-tag {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  padding: 0 10px;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  background: #fff;
  color: #334155;
  font-size: 12px;
  font-weight: 800;
}

.locked-keyword-group-empty {
  color: #94a3b8;
  font-size: 13px;
  font-weight: 700;
}

.locked-keyword-group-tip {
  color: #2563eb;
  font-size: 12px;
  font-weight: 800;
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
.competitor-list {
  display: grid;
  gap: 10px;
}
.competitor-row {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr) minmax(0, 1fr) 48px;
  align-items: end;
  gap: 12px;
  padding: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #f8fbff;
}
.competitor-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  margin-bottom: 4px;
  border-radius: 999px;
  background: #2563eb;
  color: #fff;
  font-size: 12px;
  font-weight: 900;
}

.checklist-panel {
  display: grid;
  gap: 14px;
}

.checklist-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 14px 16px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: linear-gradient(135deg, #fff 0%, #f8fbff 100%);
}

.checklist-head strong {
  display: block;
  color: #0f172a;
  font-size: 16px;
  font-weight: 900;
}

.checklist-head span {
  display: block;
  margin-top: 4px;
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
}

.checklist-summary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 68px;
  height: 36px;
  border-radius: 8px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 16px;
  font-weight: 900;
}

.checklist-status {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border: 1px solid #fed7aa;
  border-radius: 8px;
  background: #fff7ed;
  color: #9a3412;
}

.checklist-status.ready {
  border-color: #a7f3d0;
  background: #ecfdf5;
  color: #047857;
}

.checklist-status strong {
  font-size: 15px;
  font-weight: 900;
}

.checklist-status span {
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
}

.checklist-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.checklist-item {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  gap: 12px;
  padding: 14px;
  border: 1px solid #fecaca;
  border-radius: 8px;
  background: #fffafa;
}

.checklist-item.done {
  border-color: #bbf7d0;
  background: #f8fffb;
}

.checklist-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: #fee2e2;
  color: #dc2626;
  font-weight: 900;
}

.checklist-item.done .checklist-icon {
  background: #dcfce7;
  color: #16a34a;
}

.checklist-body {
  min-width: 0;
}

.checklist-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.checklist-title strong {
  min-width: 0;
  color: #0f172a;
  font-size: 14px;
  font-weight: 900;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.checklist-body p {
  margin: 8px 0 6px;
  color: #475569;
  font-size: 13px;
  line-height: 1.5;
}

.checklist-body > span {
  color: #2563eb;
  font-size: 12px;
  font-weight: 800;
}

@media (max-width: 768px) {
  .two-columns {
    grid-template-columns: 1fr;
  }
  .core-quota-overview {
    grid-template-columns: 1fr;
  }
  .form-section-head {
    flex-direction: column;
  }
  .competitor-row {
    grid-template-columns: 1fr;
  }
  .checklist-head,
  .checklist-status {
    align-items: flex-start;
    flex-direction: column;
  }
  .checklist-grid {
    grid-template-columns: 1fr;
  }
}
</style>
