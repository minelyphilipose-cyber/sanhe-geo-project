<template>
  <div class="partner-customer-detail">
    <div class="partner-detail-nav">
      <el-button link @click="router.back()">返回</el-button>
      <span>客户详情</span>
    </div>

    <DataState :loading="loading" :empty="!loading && !company" empty-text="客户不存在或无权访问">
      <template v-if="company">
        <section class="detail-hero">
          <div class="detail-hero__main">
            <div class="entity-avatar">{{ entityInitial(company.companyName) }}</div>
            <div>
              <div class="detail-kicker">客户资产</div>
              <h1>{{ company.companyName }}</h1>
              <p>{{ company.industry || '未填写行业' }} · {{ regionText }} · {{ roleText }}</p>
            </div>
          </div>
          <span class="status-pill" :class="companyStatusClass(company.status)">
            {{ companyStatusText }}
          </span>
        </section>

        <section class="metric-grid">
          <div class="metric-card is-blue">
            <span>品牌数量</span>
            <strong>{{ brands.length }}</strong>
            <small>当前客户下品牌资料</small>
          </div>
          <div class="metric-card" :class="activePackageBinding ? 'is-green' : 'is-amber'">
            <span>套餐状态</span>
            <strong>{{ activePackageBinding ? '已绑定' : '未绑定' }}</strong>
            <small>{{ activePackageBinding?.packageName || '请负责人绑定合伙人套餐' }}</small>
          </div>
          <div class="metric-card is-purple">
            <span>核心问题额度</span>
            <strong>{{ keywordQuotaText }}</strong>
            <small>{{ activePackageBinding ? '按客户套餐计算' : '绑定套餐后生效' }}</small>
          </div>
          <div class="metric-card is-slate">
            <span>协作状态</span>
            <strong>{{ workflowMeta.label }}</strong>
            <small>{{ workflowMeta.hint }}</small>
          </div>
        </section>

        <section class="detail-card workflow-card">
          <div class="section-head">
            <div>
              <h2>协作进度</h2>
              <p>按合伙人员工录入、负责人配置套餐、继续录入、确认提交的流程推进。</p>
            </div>
            <div class="section-actions">
              <el-button
                v-if="canRequestPackage"
                type="primary"
                :loading="workflowSubmitting"
                @click="requestPackage"
              >
                提交负责人添加套餐
              </el-button>
              <el-button
                v-if="canNotifyEntry"
                type="primary"
                :loading="workflowSubmitting"
                @click="notifyEntry"
              >
                通知继续录入
              </el-button>
              <el-button v-if="canSubmitWorkorder" type="primary" plain @click="goSubmitWorkorder">
                查看项目并提交工单
              </el-button>
              <el-button
                v-if="canReturnEntry"
                type="warning"
                plain
                :loading="workflowSubmitting"
                @click="openReturnEntry"
              >
                退回修改
              </el-button>
            </div>
          </div>
          <div class="workflow-steps">
            <div
              v-for="step in workflowSteps"
              :key="step.key"
              class="workflow-step"
              :class="{ active: step.active, done: step.done }"
            >
              <span>{{ step.index }}</span>
              <div>
                <strong>{{ step.title }}</strong>
                <small>{{ step.desc }}</small>
              </div>
            </div>
          </div>
        </section>

        <section class="detail-card customer-info-card">
          <div class="section-head">
            <div>
              <h2>客户信息</h2>
              <p>展示协作所需的客户基础资料。</p>
            </div>
          </div>
          <div class="info-grid">
            <div v-for="item in infoItems" :key="item.label" class="info-item">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </div>
          </div>
        </section>

        <section v-loading="packageLoading" class="detail-card package-card">
          <div class="section-head">
            <div>
              <h2>客户套餐</h2>
              <p>套餐绑定后，交付员工才能继续录入项目和核心问题。</p>
            </div>
            <div v-if="canManagePackage" class="section-actions">
              <el-button v-if="!activePackageBinding" type="primary" @click="openPackageBind">绑定套餐</el-button>
              <template v-else>
                <el-button :loading="packageSubmitting" @click="confirmRefreshPackage">更新套餐</el-button>
                <el-button type="danger" plain :disabled="packageSubmitting" @click="confirmUnbindPackage">解绑套餐</el-button>
              </template>
            </div>
          </div>

          <div v-if="activePackageBinding" class="package-summary">
            <div>
              <span>套餐名称</span>
              <strong>{{ activePackageBinding.packageName }}</strong>
            </div>
            <div>
              <span>服务周期</span>
              <strong>{{ activePackageBinding.serviceMonths }} 个月</strong>
            </div>
            <div>
              <span>核心问题额度</span>
              <strong>{{ coreQuestionQuotaLimit }} 个</strong>
            </div>
            <div>
              <span>已用核心问题</span>
              <strong>{{ usedCoreQuestionCount }} 个</strong>
            </div>
            <div>
              <span>剩余核心问题</span>
              <strong>{{ remainingCoreQuestionCount }} 个</strong>
            </div>
            <div>
              <span>可见发布额度</span>
              <strong>{{ visibleChannelQuotaSummaryText }}</strong>
            </div>
            <div>
              <span>绑定时间</span>
              <strong>{{ formatDateTimeSeconds(activePackageBinding.boundAt) }}</strong>
            </div>
          </div>
          <div v-if="activePackageBinding" class="package-channel-summary">
            <div class="package-channel-title">
              <span>可见渠道额度</span>
              <el-tag size="small" type="info">{{ visibleChannelTableItems.length }} 个渠道</el-tag>
            </div>
            <el-table
              v-if="visibleChannelTableItems.length > 0"
              :data="visibleChannelTableItems"
              border
              table-layout="fixed"
              :row-class-name="quotaRowClassName"
            >
              <el-table-column label="平台" min-width="160">
                <template #default="scope">
                  <div class="quota-channel-cell">
                    <span>{{ scope.row.channelName }}</span>
                    <el-tag v-if="!scope.row.enabled" size="small" type="info">未开通</el-tag>
                    <el-tag v-else-if="scope.row.status === 'exceeded'" size="small" type="danger">超额</el-tag>
                    <el-tag v-else-if="scope.row.status === 'warning'" size="small" type="warning">预警</el-tag>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="周期" width="110">
                <template #default="scope">{{ scope.row.enabled ? channelPeriodText(scope.row.periodType) : '-' }}</template>
              </el-table-column>
              <el-table-column label="已用 / 额度" min-width="220">
                <template #default="scope">
                  <span v-if="!scope.row.enabled">未开通</span>
                  <div v-else class="quota-used-cell">
                    <span>{{ scope.row.usedCount }} / {{ scope.row.quotaLimit }}</span>
                    <el-progress
                      :percentage="quotaPercentage(scope.row)"
                      :status="quotaProgressStatus(scope.row)"
                      :show-text="false"
                      :stroke-width="8"
                    />
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="剩余额度" width="120">
                <template #default="scope">{{ quotaRemainingText(scope.row) }}</template>
              </el-table-column>
              <el-table-column label="下次重置" width="150">
                <template #default="scope">{{ nextResetText(scope.row) }}</template>
              </el-table-column>
            </el-table>
            <div v-else class="package-channel-empty">当前套餐暂无合伙人可见渠道额度</div>
          </div>
          <div v-else class="empty-action">
            <strong>当前客户未绑定套餐</strong>
            <span>请由合伙人负责人选择合伙人套餐，绑定后再通知交付员工继续录入项目和核心问题。</span>
            <el-button v-if="canManagePackage" type="primary" @click="openPackageBind">绑定合伙人套餐</el-button>
          </div>
        </section>

        <section class="detail-card brand-list-card">
          <div class="section-head">
            <div>
              <h2>品牌资料</h2>
              <p>品牌资料用于后续项目创建、内容生成和核心问题准备。</p>
            </div>
            <el-button v-if="isPartnerStaff" type="primary" @click="goCreateBrand">新增品牌</el-button>
          </div>

          <DataState :loading="brandLoading" :empty="!brandLoading && brands.length === 0" empty-text="暂无品牌资料">
            <el-table :data="brands" border table-layout="fixed">
              <el-table-column prop="brandName" label="品牌名称" min-width="180" show-overflow-tooltip />
              <el-table-column prop="brandShortName" label="品牌简称" min-width="150" show-overflow-tooltip />
              <el-table-column label="行业" min-width="140">
                <template #default="scope">{{ scope.row.industry || '-' }}</template>
              </el-table-column>
              <el-table-column label="主营业务" min-width="220" show-overflow-tooltip>
                <template #default="scope">{{ scope.row.mainBusiness || '-' }}</template>
              </el-table-column>
              <el-table-column label="状态" width="110">
                <template #default="scope">
                  <span class="status-pill" :class="scope.row.status === 'active' ? 'is-success' : 'is-muted'">
                    {{ scope.row.status === 'active' ? '启用' : '停用' }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="120" fixed="right">
                <template #default="scope">
                  <el-button link type="primary" @click="goBrandDetail(scope.row.id)">详情</el-button>
                </template>
              </el-table-column>
            </el-table>
          </DataState>
        </section>

      </template>
    </DataState>

    <el-dialog v-model="packageBindVisible" title="绑定合伙人套餐" width="560px" class="partner-package-dialog">
      <el-form label-position="top">
        <el-form-item label="选择套餐" required>
          <el-select v-model="packageBindForm.packagePlanId" placeholder="请选择已上架的合伙人套餐" style="width: 100%">
            <el-option
              v-for="plan in packagePlanOptions"
              :key="plan.id"
              :label="packagePlanLabel(plan)"
              :value="plan.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <div class="package-bind-tip">
        绑定后会进入“待通知继续录入”，负责人需要通知交付员工继续录入项目和核心问题。
      </div>
      <template #footer>
        <el-button @click="packageBindVisible = false">取消</el-button>
        <el-button type="primary" :loading="packageSubmitting" @click="submitPackageBind">确认绑定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="returnEntryVisible" title="退回交付员工修改" width="560px" class="partner-package-dialog">
      <el-form label-position="top">
        <el-form-item label="退回原因" required>
          <el-input
            v-model="returnEntryForm.reason"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="请说明需要补充或修正的客户、品牌、项目、竞品、核心问题或图片资产内容"
          />
        </el-form-item>
      </el-form>
      <div class="package-bind-tip">
        退回后协作状态会回到“项目与拓词录入中”，交付员工修改完成后需要再次提交负责人确认。
      </div>
      <template #footer>
        <el-button @click="returnEntryVisible = false">取消</el-button>
        <el-button type="warning" :loading="workflowSubmitting" @click="submitReturnEntry">确认退回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import DataState from '@/components/ui/DataState.vue'
import {
  bindCompanyPackage,
  getActiveCompanyPackageBinding,
  getBrandList,
  getCompanyDetail,
  getCompanyDistributionQuotas,
  getCompanyKeywordGroupQuota,
  notifyCompanyProjectEntry,
  refreshCompanyPackage,
  requestCompanyPackageReview,
  returnCompanyEntry,
  unbindCompanyPackage,
} from '@/api/customer'
import { getGeoProjectWorkorders } from '@/api/geoQuestion'
import { getEnabledPackagePlans } from '@/api/packagePlan'
import { getProjectList } from '@/api/project'
import { useUserStore } from '@/stores/user'
import type { Brand, Company, CompanyDistributionQuota, CompanyKeywordGroupQuota, CompanyPackageBinding, PackagePlan, Project } from '@/types'
import { errorMessage } from '@/utils/error'
import { formatDateTimeSeconds } from '@/utils/format'
import { regionDisplayFromPayload } from '@/constants/region'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const companyId = Number(route.params.id)
const loading = ref(false)
const brandLoading = ref(false)
const packageLoading = ref(false)
const workflowSubmitting = ref(false)
const packageSubmitting = ref(false)
const packageBindVisible = ref(false)
const returnEntryVisible = ref(false)

const company = ref<Company | null>(null)
const brands = ref<Brand[]>([])
const activePackageBinding = ref<CompanyPackageBinding | null>(null)
const keywordQuota = ref<CompanyKeywordGroupQuota | null>(null)
const distributionQuota = ref<CompanyDistributionQuota | null>(null)
const entryReadiness = ref<{ hasProject: boolean; allCoreQuestionsReady: boolean }>({ hasProject: false, allCoreQuestionsReady: false })
const packagePlanOptions = ref<PackagePlan[]>([])
const packageBindForm = reactive({
  packagePlanId: null as number | null,
})
const returnEntryForm = reactive({
  reason: '',
})

type ChannelQuotaRow = {
  channelCode: string
  channelName: string
  enabled: boolean
  periodType?: string | null
  quotaLimit: number
  usageQuotaLimit?: number | null
  limitMismatch?: boolean
  usedCount: number
  remainingCount: number
  usageRate?: number
  nextResetAt?: string | null
  status?: string | null
}

const isPartnerOwner = computed(() => userStore.role === 'partner')
const isPartnerStaff = computed(() => userStore.role === 'partner_staff')
const canManagePackage = computed(() => isPartnerOwner.value)

const effectiveWorkflowStatus = computed(() => {
  const status = String(company.value?.partnerWorkflowStatus || 'draft')
  if (!activePackageBinding.value && ['package_bound', 'project_entry', 'entry_completed', 'submitted_to_hq'].includes(status)) {
    return 'package_requested'
  }
  return status
})

const workflowMeta = computed(() => {
  const map: Record<string, { label: string; hint: string }> = {
    draft: { label: '资料录入中', hint: '交付员工维护客户与品牌资料' },
    package_requested: { label: '待负责人加套餐', hint: '负责人绑定客户套餐后继续推进' },
    package_bound: { label: '待通知继续录入', hint: '套餐已绑定，等待负责人通知员工' },
    project_entry: { label: '项目与拓词录入中', hint: '交付员工继续录入项目和核心问题' },
    entry_completed: { label: '待负责人确认', hint: '负责人核对资料后提交总部工单' },
    submitted_to_hq: { label: '已提交总部', hint: '总部已收到启动工单，等待总部处理' },
  }
  return map[effectiveWorkflowStatus.value] || map.draft
})

const workflowOrder = ['draft', 'package_requested', 'package_bound', 'project_entry', 'entry_completed', 'submitted_to_hq']
const workflowSteps = computed(() => {
  const currentIndex = Math.max(workflowOrder.indexOf(effectiveWorkflowStatus.value), 0)
  return [
    { key: 'draft', title: '客户与品牌', desc: '录入客户基础资料和品牌信息' },
    { key: 'package_requested', title: '添加套餐', desc: '负责人绑定合伙人套餐' },
    { key: 'package_bound', title: '通知继续', desc: '通知员工继续录入项目资料' },
    { key: 'project_entry', title: '项目与核心问题', desc: '录入项目和核心问题' },
    { key: 'entry_completed', title: '负责人确认', desc: '核对后提交总部工单' },
    { key: 'submitted_to_hq', title: '已提交总部', desc: '等待总部审批与启动配置' },
  ].map((step, index) => ({
    ...step,
    index: index + 1,
    active: index === currentIndex,
    done: index < currentIndex,
  }))
})

const canRequestPackage = computed(() => isPartnerStaff.value && effectiveWorkflowStatus.value === 'draft')
const canNotifyEntry = computed(() => isPartnerOwner.value && effectiveWorkflowStatus.value === 'package_bound')
const canSubmitWorkorder = computed(() => isPartnerOwner.value && effectiveWorkflowStatus.value === 'entry_completed')
const canReturnEntry = computed(() => isPartnerOwner.value && effectiveWorkflowStatus.value === 'entry_completed')

const regionText = computed(() => company.value ? regionDisplayFromPayload(company.value) || company.value.city || '-' : '-')
const roleText = computed(() => company.value?.partnerStaffOwnerName ? `交付员工：${company.value.partnerStaffOwnerName}` : '未分配交付员工')
const companyStatusText = computed(() => {
  const status = company.value?.status
  if (status === 'signed') return '已签约'
  if (status === 'inactive') return '停用'
  return '潜在'
})

const coreQuestionQuotaLimit = computed(() => (
  keywordQuota.value?.coreQuestionQuotaLimit
  ?? keywordQuota.value?.quotaLimitA
  ?? activePackageBinding.value?.coreQuestionLimit
  ?? activePackageBinding.value?.keywordGroupLimitA
  ?? 0
))

const usedCoreQuestionCount = computed(() => (
  keywordQuota.value?.usedCoreQuestionCount
  ?? keywordQuota.value?.usedCountA
  ?? 0
))

const remainingCoreQuestionCount = computed(() => (
  keywordQuota.value?.remainingCoreQuestionCount
  ?? keywordQuota.value?.remainingCountA
  ?? Math.max(coreQuestionQuotaLimit.value - usedCoreQuestionCount.value, 0)
))

const visibleChannelQuotaItems = computed(() => (
  distributionQuota.value?.items || []
).filter((item) => item.enabled !== false))

const fallbackChannelQuotaItems = computed(() => (
  activePackageBinding.value?.visibleChannelQuotas || []
).filter((item) => item.enabled !== false))

const visibleChannelTableItems = computed<ChannelQuotaRow[]>(() => {
  if (visibleChannelQuotaItems.value.length > 0) {
    return visibleChannelQuotaItems.value.map((item) => ({
      channelCode: item.channelCode,
      channelName: item.channelName,
      enabled: item.enabled !== false,
      periodType: item.periodType,
      quotaLimit: Number(item.quotaLimit || 0),
      usageQuotaLimit: item.usageQuotaLimit,
      limitMismatch: item.limitMismatch,
      usedCount: Number(item.usedCount || 0),
      remainingCount: Number(item.remainingCount || 0),
      usageRate: item.usageRate,
      nextResetAt: item.nextResetAt,
      status: item.status,
    }))
  }
  return fallbackChannelQuotaItems.value.map((item) => {
    const source = item as any
    return {
      channelCode: item.channelCode,
      channelName: item.channelName,
      enabled: item.enabled !== false,
      periodType: item.periodType,
      usedCount: Number(source.usedCount || 0),
      quotaLimit: Number(item.quotaLimit || 0),
      remainingCount: Number(item.remainingCount ?? item.quotaLimit ?? 0),
      limitMismatch: false,
      status: 'normal',
      nextResetAt: null,
    }
  })
})

const totalVisibleChannelLimit = computed(() => visibleChannelQuotaItems.value
  .reduce((sum, item) => sum + Number(item.quotaLimit || 0), 0))

const totalVisibleChannelRemaining = computed(() => visibleChannelQuotaItems.value
  .reduce((sum, item) => sum + Number(item.remainingCount || 0), 0))

const visibleChannelQuotaSummaryText = computed(() => {
  if (!activePackageBinding.value) return '未绑定套餐'
  if (visibleChannelQuotaItems.value.length > 0) {
    return `${totalVisibleChannelRemaining.value} / ${totalVisibleChannelLimit.value}`
  }
  if (fallbackChannelQuotaItems.value.length > 0) {
    const totalLimit = fallbackChannelQuotaItems.value.reduce((sum, item) => sum + Number(item.quotaLimit || 0), 0)
    const totalRemaining = fallbackChannelQuotaItems.value
      .reduce((sum, item) => sum + Number(item.remainingCount ?? item.quotaLimit ?? 0), 0)
    return `${totalRemaining} / ${totalLimit}`
  }
  return '暂无额度'
})

const keywordQuotaText = computed(() => {
  if (!activePackageBinding.value) return '未绑定套餐'
  return `${usedCoreQuestionCount.value} / ${coreQuestionQuotaLimit.value}`
})
const infoItems = computed(() => {
  const item = company.value
  if (!item) return []
  return [
    { label: '客户名称', value: item.companyName || '-' },
    { label: '行业', value: item.industry || '-' },
    { label: '地区', value: regionText.value },
    { label: '联系人', value: item.contactName || '-' },
    { label: '联系电话', value: item.contactPhone || '-' },
    { label: '官网', value: item.officialWebsite || '-' },
    { label: '合伙人', value: item.partnerName || '-' },
    { label: '交付员工', value: item.partnerStaffOwnerName || '未分配' },
    { label: '创建时间', value: formatDateTimeSeconds(item.createdAt) || '-' },
  ]
})

function entityInitial(value?: string | null) {
  const text = String(value || '').trim()
  return text ? Array.from(text)[0] : '客'
}

function companyStatusClass(status?: string | null) {
  if (status === 'signed' || status === 'active') return 'is-success'
  if (status === 'inactive') return 'is-muted'
  return 'is-info'
}

function packagePlanLabel(plan: PackagePlan) {
  const points = Number(plan.standardPrice || 0).toFixed(2)
  const coreQuestionLimit = plan.coreQuestionLimit ?? plan.keywordGroupLimitA ?? 0
  return `${plan.packageName} · 消耗积分 ${points} · ${plan.serviceMonths}个月 · 核心问题 ${coreQuestionLimit} 个`
}

function channelPeriodText(periodType?: string | null) {
  const mapping: Record<string, string> = {
    day: '日',
    week: '周',
    month: '月',
    total: '总额度',
    none: '总额度',
  }
  return mapping[String(periodType || 'none')] || String(periodType || '-')
}

function quotaPercentage(row: { enabled?: boolean; quotaLimit?: number | null; usedCount?: number | null }) {
  if (!row.enabled) return 0
  const limit = Number(row.quotaLimit || 0)
  const used = Number(row.usedCount || 0)
  if (limit <= 0) return used > 0 ? 100 : 0
  return Math.min(100, Math.round((used * 100) / limit))
}

function quotaProgressStatus(row: { enabled?: boolean; status?: string | null }) {
  if (!row.enabled) return undefined
  if (row.status === 'exceeded') return 'exception'
  if (row.status === 'warning') return 'warning'
  return undefined
}

function quotaRemainingText(row: { enabled?: boolean; status?: string | null; usedCount?: number | null; quotaLimit?: number | null; remainingCount?: number | null }) {
  if (!row.enabled) return '-'
  const used = Number(row.usedCount || 0)
  const limit = Number(row.quotaLimit || 0)
  if (row.status === 'exceeded') return `超出 ${Math.max(used - limit, 0)}`
  return String(Number(row.remainingCount ?? Math.max(limit - used, 0)))
}

function nextResetText(row: { enabled?: boolean; periodType?: string | null; nextResetAt?: string | null }) {
  if (!row.enabled) return '-'
  if (row.periodType === 'total' || row.periodType === 'none') return '不重置'
  if (!row.nextResetAt) return '-'
  const date = dayjs(row.nextResetAt)
  if (!date.isValid()) return '-'
  const weekdays = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  return `${date.format('M月D日')} ${weekdays[date.day()]}`
}

function quotaRowClassName({ row }: { row: { enabled?: boolean; status?: string | null } }) {
  if (!row.enabled) return 'quota-row-disabled'
  if (row.status === 'exceeded') return 'quota-row-danger'
  if (row.status === 'warning') return 'quota-row-warning'
  return ''
}

async function loadCompany() {
  loading.value = true
  try {
    const { data } = await getCompanyDetail(companyId)
    company.value = data.data
  } catch (err) {
    ElMessage.error(errorMessage(err, '加载客户详情失败'))
  } finally {
    loading.value = false
  }
}

async function loadBrands() {
  brandLoading.value = true
  try {
    const { data } = await getBrandList({ current: 1, size: 100, companyId })
    brands.value = data.data.records || []
  } catch (err) {
    ElMessage.error(errorMessage(err, '加载品牌资料失败'))
  } finally {
    brandLoading.value = false
  }
}

async function loadPackageInfo() {
  packageLoading.value = true
  try {
    const [bindingRes, quotaRes, distributionRes] = await Promise.all([
      getActiveCompanyPackageBinding(companyId),
      getCompanyKeywordGroupQuota(companyId).catch(() => null),
      getCompanyDistributionQuotas(companyId).catch(() => null),
    ])
    activePackageBinding.value = bindingRes.data.data
    keywordQuota.value = quotaRes?.data.data || null
    distributionQuota.value = distributionRes?.data.data || null
  } catch (err) {
    activePackageBinding.value = null
    keywordQuota.value = null
    distributionQuota.value = null
    ElMessage.error(errorMessage(err, '加载客户套餐失败'))
  } finally {
    packageLoading.value = false
  }
}

async function loadEntryReadiness() {
  try {
    const { data } = await getProjectList({ current: 1, size: 500, companyId })
    entryReadiness.value = await buildEntryReadiness(data.data.records || [])
  } catch {
    entryReadiness.value = { hasProject: false, allCoreQuestionsReady: false }
  }
}

async function buildEntryReadiness(projects: Project[]) {
  const relatedProjects = projects.filter((project) => Number(project.companyId || 0) === companyId)
  if (!relatedProjects.length) {
    return { hasProject: false, allCoreQuestionsReady: false }
  }
  const results = await Promise.all(relatedProjects.map(async (project) => {
    const allocatedCount = projectCoreQuestionLimit(project)
    if (allocatedCount <= 0) return false
    try {
      const { data } = await getGeoProjectWorkorders(project.id)
      const actualCount = (data.data || [])
        .filter((item) => item.status === 'committed')
        .reduce((sum, item) => sum + Number(item.countTotal || 0), 0)
      return actualCount === allocatedCount
    } catch {
      return false
    }
  }))
  return {
    hasProject: true,
    allCoreQuestionsReady: results.length > 0 && results.every(Boolean),
  }
}

function projectCoreQuestionLimit(project: Project) {
  return Number(project.planCoreQuestionLimit ?? project.planKeywordGroupLimitA ?? project.planKeywordGroupLimit ?? 0)
}

async function loadPackagePlanOptions() {
  try {
    const { data } = await getEnabledPackagePlans()
    packagePlanOptions.value = data.data || []
  } catch (err) {
    packagePlanOptions.value = []
    ElMessage.error(errorMessage(err, '加载合伙人套餐失败'))
  }
}

async function reloadAll() {
  await Promise.all([loadCompany(), loadBrands(), loadPackageInfo(), loadEntryReadiness()])
}

async function openPackageBind() {
  packageBindForm.packagePlanId = null
  await loadPackagePlanOptions()
  packageBindVisible.value = true
}

async function submitPackageBind() {
  if (!packageBindForm.packagePlanId) {
    ElMessage.warning('请选择需要绑定的合伙人套餐')
    return
  }
  packageSubmitting.value = true
  try {
    await bindCompanyPackage(companyId, packageBindForm.packagePlanId)
    ElMessage.success('客户套餐已绑定')
    packageBindVisible.value = false
    await reloadAll()
  } catch (err) {
    ElMessage.error(errorMessage(err, '绑定客户套餐失败'))
  } finally {
    packageSubmitting.value = false
  }
}

async function confirmRefreshPackage() {
  if (!activePackageBinding.value) return
  try {
    await ElMessageBox.confirm(
      `确认将套餐「${activePackageBinding.value.packageName}」的最新配置更新到该客户？已使用额度不会重置。`,
      '更新套餐',
      { type: 'warning', confirmButtonText: '确认更新', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  packageSubmitting.value = true
  try {
    await refreshCompanyPackage(companyId)
    ElMessage.success('客户套餐信息已更新')
    await reloadAll()
  } catch (err) {
    ElMessage.error(errorMessage(err, '更新客户套餐失败'))
  } finally {
    packageSubmitting.value = false
  }
}

async function confirmUnbindPackage() {
  if (!activePackageBinding.value) return
  try {
    await ElMessageBox.confirm(
      `确认解绑套餐「${activePackageBinding.value.packageName}」？解绑后交付员工将不能继续提交项目录入完成。`,
      '解绑确认',
      { type: 'warning', confirmButtonText: '确认解绑', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  packageSubmitting.value = true
  try {
    await unbindCompanyPackage(companyId)
    ElMessage.success('客户套餐已解绑')
    await reloadAll()
  } catch (err) {
    ElMessage.error(errorMessage(err, '解绑客户套餐失败'))
  } finally {
    packageSubmitting.value = false
  }
}

async function requestPackage() {
  workflowSubmitting.value = true
  try {
    await requestCompanyPackageReview(companyId)
    ElMessage.success('已提交负责人添加客户套餐')
    await reloadAll()
  } catch (err) {
    ElMessage.error(errorMessage(err, '提交负责人失败'))
  } finally {
    workflowSubmitting.value = false
  }
}

async function notifyEntry() {
  workflowSubmitting.value = true
  try {
    await notifyCompanyProjectEntry(companyId)
    ElMessage.success('已通知交付员工继续录入')
    await reloadAll()
  } catch (err) {
    ElMessage.error(errorMessage(err, '通知继续录入失败'))
  } finally {
    workflowSubmitting.value = false
  }
}

function openReturnEntry() {
  returnEntryForm.reason = ''
  returnEntryVisible.value = true
}

async function submitReturnEntry() {
  const reason = returnEntryForm.reason.trim()
  if (!reason) {
    ElMessage.warning('请填写退回原因，方便交付员工明确修改方向')
    return
  }
  workflowSubmitting.value = true
  try {
    await returnCompanyEntry(companyId, { reason })
    ElMessage.success('已退回交付员工修改')
    returnEntryVisible.value = false
    await reloadAll()
  } catch (err) {
    ElMessage.error(errorMessage(err, '退回修改失败'))
  } finally {
    workflowSubmitting.value = false
  }
}

function goCreateBrand() {
  router.push({ path: '/partner/brands/create', query: { companyId: String(companyId) } })
}

function goBrandDetail(id: number) {
  if (route.name === 'PartnerSubmittedCustomerDetail') {
    router.push(`/admin/partner-start-requests/brands/${id}`)
    return
  }
  router.push(`/partner/brands/${id}`)
}

function goSubmitWorkorder() {
  router.push({ name: 'MyProjects', query: { companyId: companyId } })
}

onMounted(reloadAll)
</script>

<style scoped>
.partner-customer-detail {
  display: grid;
  gap: 14px;
}

.partner-detail-nav {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #334155;
  font-size: 15px;
  font-weight: 800;
}

.detail-hero,
.detail-card,
.metric-card {
  border: 1px solid var(--card-border, #dbeafe);
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.055);
}

.detail-hero {
  display: flex;
  min-height: 126px;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  overflow: hidden;
  position: relative;
  padding: 22px 24px;
  background:
    radial-gradient(circle at 92% 8%, rgba(14, 165, 233, 0.16) 0, rgba(14, 165, 233, 0.16) 120px, transparent 122px),
    linear-gradient(135deg, #fff 0%, #f1f7ff 56%, #ecfdf5 100%);
}

.detail-hero::before {
  position: absolute;
  inset: 0 auto 0 0;
  width: 4px;
  background: linear-gradient(180deg, #2563eb 0%, #14b8a6 100%);
  content: '';
}

.detail-hero__main {
  display: flex;
  align-items: center;
  gap: 16px;
  min-width: 0;
}

.entity-avatar {
  display: grid;
  width: 58px;
  height: 58px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 14px;
  background: linear-gradient(135deg, #2563eb 0%, #14b8a6 100%);
  color: #fff;
  font-size: 26px;
  font-weight: 900;
}

.detail-kicker {
  color: #2563eb;
  font-size: 12px;
  font-weight: 900;
}

.detail-hero h1 {
  margin: 5px 0 8px;
  color: #0f172a;
  font-size: 25px;
  font-weight: 900;
  letter-spacing: 0;
}

.detail-hero p {
  margin: 0;
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #bfdbfe;
  border-radius: 999px;
  padding: 5px 11px;
  background: #eff6ff;
  color: #2563eb;
  font-size: 12px;
  font-weight: 900;
}

.status-pill.is-success {
  border-color: #a7f3d0;
  background: #ecfdf5;
  color: #047857;
}

.status-pill.is-muted {
  border-color: #e2e8f0;
  background: #f8fafc;
  color: #64748b;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.metric-card {
  position: relative;
  overflow: hidden;
  min-height: 96px;
  padding: 14px 16px;
  background: linear-gradient(180deg, #fff 0%, var(--metric-soft, #f8fbff) 100%);
}

.metric-card::before {
  content: "";
  position: absolute;
  inset: 0 auto 0 0;
  width: 4px;
  background: var(--metric-color, #2563eb);
}

.metric-card span,
.metric-card small {
  display: block;
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.metric-card strong {
  display: block;
  margin: 8px 0 6px;
  color: #0f172a;
  font-size: 23px;
  font-weight: 900;
}

.metric-card.is-blue {
  --metric-color: #2563eb;
  --metric-soft: #eff6ff;
}

.metric-card.is-green {
  --metric-color: #10b981;
  --metric-soft: #ecfdf5;
}

.metric-card.is-amber {
  --metric-color: #f59e0b;
  --metric-soft: #fffbeb;
}

.metric-card.is-purple {
  --metric-color: #7c3aed;
  --metric-soft: #f5f3ff;
}

.metric-card.is-slate {
  --metric-color: #64748b;
  --metric-soft: #f8fafc;
}

.detail-card {
  --section-accent: #2563eb;
  --section-soft: #f8fbff;
  --card-border: #dbeafe;
  padding: 18px;
}

.workflow-card {
  --section-accent: #f59e0b;
  --section-soft: #fffbeb;
  --card-border: #fde68a;
}

.customer-info-card {
  --section-accent: #2563eb;
  --section-soft: #eff6ff;
}

.package-card {
  --section-accent: #0f766e;
  --section-soft: #ecfdf5;
  --card-border: #b8eee5;
}

.brand-list-card {
  --section-accent: #7c3aed;
  --section-soft: #f5f3ff;
  --card-border: #ddd6fe;
}

.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.section-head h2 {
  position: relative;
  padding-left: 12px;
  margin: 0;
  color: #0f172a;
  font-size: 17px;
  font-weight: 900;
}

.section-head h2::before {
  position: absolute;
  top: 2px;
  bottom: 2px;
  left: 0;
  width: 4px;
  border-radius: 999px;
  background: var(--section-accent);
  content: '';
}

.section-head p {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
}

.section-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.workflow-steps {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 8px;
}

.workflow-step {
  display: flex;
  gap: 10px;
  min-height: 78px;
  padding: 13px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #f8fafc;
}

.workflow-step > span {
  display: grid;
  width: 28px;
  height: 28px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 999px;
  background: #e2e8f0;
  color: #64748b;
  font-size: 12px;
  font-weight: 900;
}

.workflow-step strong,
.workflow-step small {
  display: block;
}

.workflow-step strong {
  color: #0f172a;
  font-size: 13px;
  font-weight: 900;
}

.workflow-step small {
  margin-top: 5px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.4;
}

.workflow-step.done {
  border-color: #bfdbfe;
  background: #eff6ff;
}

.workflow-step.done > span {
  background: #2563eb;
  color: #fff;
}

.workflow-step.active {
  border-color: #fde68a;
  background: linear-gradient(180deg, #fff 0%, #fffbeb 100%);
}

.workflow-step.active > span {
  background: #f59e0b;
  color: #fff;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.info-item,
.package-summary > div {
  min-height: 68px;
  padding: 12px 13px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: linear-gradient(180deg, #fff 0%, #f8fafc 100%);
}

.info-item:hover,
.package-summary > div:hover {
  border-color: var(--card-border, #dbeafe);
  background: linear-gradient(180deg, #fff 0%, var(--section-soft, #f8fbff) 100%);
}

.info-item span,
.package-summary span {
  display: block;
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.info-item strong,
.package-summary strong {
  display: block;
  margin-top: 8px;
  color: #0f172a;
  font-size: 14px;
  font-weight: 900;
  line-height: 1.5;
  word-break: break-word;
}

.package-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.package-channel-summary {
  margin-top: 12px;
  padding: 12px;
  border: 1px solid #b8eee5;
  border-radius: 12px;
  background: linear-gradient(180deg, #fff 0%, #f0fdfa 100%);
}

.package-channel-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  color: #0f172a;
  font-size: 13px;
  font-weight: 900;
}

.package-channel-title span {
  color: #0f172a;
  font-size: 14px;
  font-weight: 900;
}

.quota-channel-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.quota-channel-cell span {
  overflow: hidden;
  color: #334155;
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quota-used-cell {
  display: grid;
  gap: 8px;
}

.quota-used-cell span {
  color: #334155;
  font-weight: 800;
}

.package-channel-summary :deep(.el-table th.el-table__cell) {
  height: 42px;
  background: #f0fdfa;
  color: #334155;
  font-weight: 900;
}

.package-channel-summary :deep(.el-table td.el-table__cell) {
  height: 48px;
}

.brand-list-card :deep(.el-table) {
  overflow: hidden;
  border-radius: 10px;
}

.brand-list-card :deep(.el-table th.el-table__cell) {
  height: 42px;
  background: #f5f3ff;
  color: #334155;
  font-weight: 900;
}

.brand-list-card :deep(.el-table td.el-table__cell) {
  height: 48px;
}

.package-channel-summary :deep(.quota-row-disabled) {
  color: #94a3b8;
  background: #f8fafc;
}

.package-channel-summary :deep(.quota-row-warning) {
  background: #fffbeb;
}

.package-channel-summary :deep(.quota-row-danger) {
  background: #fff1f2;
}

.package-channel-empty {
  padding: 16px;
  border: 1px dashed #bfdbfe;
  border-radius: 10px;
  background: #fff;
  color: #64748b;
  text-align: center;
  font-size: 13px;
  font-weight: 700;
}

.empty-action {
  display: flex;
  min-height: 190px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  border: 1px dashed #bfdbfe;
  border-radius: 12px;
  background: #f8fbff;
  text-align: center;
}

.empty-action strong {
  color: #0f172a;
  font-size: 16px;
  font-weight: 900;
}

.empty-action span,
.package-bind-tip {
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
}

.package-bind-tip {
  border-radius: 10px;
  padding: 12px;
  background: #f8fafc;
}

@media (max-width: 1200px) {
  .metric-grid,
  .package-summary,
  .workflow-steps {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .detail-hero,
  .section-head {
    flex-direction: column;
  }

  .metric-grid,
  .workflow-steps,
  .info-grid,
  .package-summary {
    grid-template-columns: 1fr;
  }
}
</style>
