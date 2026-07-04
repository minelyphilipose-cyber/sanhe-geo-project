<template>
  <div class="partner-page partner-staff-workbench">
    <div class="partner-page-header">
      <div>
        <div class="partner-page-kicker">交付工作台</div>
        <h1 class="partner-page-title">我的交付任务</h1>
        <div class="partner-page-subtitle">聚合分配给自己的客户与项目，完成资料录入后提交给合伙人查看，由合伙人统一向总部提交启动工单。</div>
      </div>
      <div class="partner-page-actions">
        <el-button @click="reload">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
    </div>

    <section class="staff-hero partner-surface">
      <div>
        <div class="staff-hero__eyebrow">当前所属合伙人</div>
        <h2>{{ userStore.userInfo?.partnerName || '合伙人' }}</h2>
        <p>你只会看到负责人分配给自己的客户和项目。客户、品牌、项目与拓词信息由当前员工账号录入维护，最终由合伙人负责人确认后提交总部。</p>
      </div>
      <div class="staff-hero__actions">
        <el-button type="primary" @click="router.push('/partner/my-customers')">进入客户列表</el-button>
        <el-button @click="router.push('/partner/my-projects')">进入项目列表</el-button>
      </div>
    </section>

    <div class="staff-metrics">
      <div v-for="card in metricCards" :key="card.key" class="staff-metric" :style="{ '--accent': card.color }">
        <div class="staff-metric__top">
          <span>{{ card.label }}</span>
          <el-icon><component :is="card.icon" /></el-icon>
        </div>
        <div class="staff-metric__value">{{ loading ? '-' : card.value }}</div>
        <div class="staff-metric__hint">{{ card.hint }}</div>
      </div>
    </div>

    <section v-if="workflowTaskCompanies.length > 0" class="partner-surface staff-panel staff-workflow-panel">
      <div class="partner-section-head">
        <div>
          <h2>协作流转待办</h2>
          <p>根据负责人处理结果继续推进客户资料、项目和核心问题录入。</p>
        </div>
        <el-tag type="warning" round>{{ workflowTaskCompanies.length }}</el-tag>
      </div>
      <div class="staff-task-list">
        <button
          v-for="company in workflowTaskCompanies.slice(0, 6)"
          :key="company.id"
          class="staff-task"
          type="button"
          @click="goWorkflowTask(company)"
        >
          <span class="staff-task__icon is-project">
            <el-icon><Collection /></el-icon>
          </span>
          <span class="staff-task__main">
            <strong>{{ company.companyName }}</strong>
            <small>{{ workflowTaskText(company) }}</small>
          </span>
          <el-icon><ArrowRight /></el-icon>
        </button>
      </div>
    </section>

    <div class="staff-grid">
      <section class="partner-surface staff-panel">
        <div class="partner-section-head">
          <div>
            <h2>资料补齐待办</h2>
            <p>按提交前检查清单展示客户、品牌、项目、额度和图片资产缺口。</p>
          </div>
          <el-tag type="warning" round>{{ customerCompletionTasks.length }}</el-tag>
        </div>

        <el-skeleton v-if="loading" :rows="4" animated />
        <el-empty v-else-if="customerCompletionTasks.length === 0" description="当前无资料补齐待办" :image-size="70" />
        <div v-else class="staff-task-list">
          <button
            v-for="task in customerCompletionTasks.slice(0, 5)"
            :key="task.company.id"
            class="staff-task"
            type="button"
            @click="goCustomerCompletionTask(task)"
          >
            <span class="staff-task__icon is-customer">
              <el-icon><User /></el-icon>
            </span>
            <span class="staff-task__main">
              <strong>{{ task.company.companyName }}</strong>
              <small>{{ task.hint }}</small>
            </span>
            <span v-if="task.pendingCount > 0" class="staff-task__badge">{{ task.pendingCount }} 项</span>
            <el-icon><ArrowRight /></el-icon>
          </button>
        </div>
      </section>

      <section class="partner-surface staff-panel">
        <div class="partner-section-head">
          <div>
            <h2>项目推进</h2>
            <p>优先处理草稿、待启动和总部配置前的项目资料。</p>
          </div>
          <el-tag type="primary" round>{{ actionableProjects.length }}</el-tag>
        </div>

        <el-skeleton v-if="loading" :rows="4" animated />
        <el-empty v-else-if="actionableProjects.length === 0" description="暂无待推进项目" :image-size="70" />
        <div v-else class="staff-task-list">
          <button
            v-for="project in actionableProjects.slice(0, 5)"
            :key="project.id"
            class="staff-task"
            type="button"
            @click="router.push('/partner/my-projects')"
          >
            <span class="staff-task__icon is-project">
              <el-icon><Folder /></el-icon>
            </span>
            <span class="staff-task__main">
              <strong>{{ project.projectName }}</strong>
              <small>{{ project.companyName || '-' }} / {{ projectStatusText(project.status) }}</small>
            </span>
            <el-icon><ArrowRight /></el-icon>
          </button>
        </div>
      </section>
    </div>

    <div class="staff-grid staff-grid--bottom">
      <section class="partner-surface staff-panel">
        <div class="partner-section-head">
          <div>
            <h2>项目提交准备</h2>
            <p>按负责人提交总部前的必要条件汇总，帮助判断哪些项目已经可交由负责人复核。</p>
          </div>
          <el-tag :type="submissionReadyCompanies > 0 && submissionPendingCompanies === 0 ? 'success' : 'warning'" round>
            {{ submissionReadyCompanies }} 可提交
          </el-tag>
        </div>

        <div class="submission-overview">
          <div class="submission-stat-grid">
            <div
              v-for="item in submissionOverviewCards"
              :key="item.label"
              class="submission-stat"
              :class="`is-${item.tone}`"
            >
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
              <small>{{ item.hint }}</small>
            </div>
          </div>

          <div class="submission-progress">
            <div class="staff-progress__row">
              <span>项目核心问题入库进度</span>
              <strong>{{ keywordReadyProjects }} / {{ projects.length || 0 }}</strong>
            </div>
            <el-progress :percentage="keywordReadyPercent" :stroke-width="10" :show-text="false" />
          </div>

          <div class="submission-blockers">
            <div>
              <strong>当前主要卡点</strong>
              <p>按提交前检查清单聚合，点击资料补齐待办可进入客户详情逐项处理。</p>
            </div>
            <div v-if="submissionPendingCategoryList.length" class="submission-chip-list">
              <span v-for="item in submissionPendingCategoryList" :key="item.label" class="submission-chip">
                {{ item.label }} {{ item.count }}
              </span>
            </div>
            <div v-else class="submission-ready-note">检查清单暂无卡点，可等待负责人复核并提交总部工单。</div>
          </div>

          <div class="submission-actions">
            <el-button @click="router.push('/partner/my-customers')">查看客户检查清单</el-button>
            <el-button type="primary" @click="router.push('/partner/my-projects')">进入项目列表</el-button>
          </div>
        </div>
      </section>

      <section class="partner-surface staff-panel">
        <div class="partner-section-head">
          <div>
            <h2>快捷操作</h2>
            <p>直接进入常用维护入口，具体待办以上方列表和提交准备为准。</p>
          </div>
        </div>

        <div class="quick-workbench">
          <div class="staff-quick-actions">
            <button v-for="action in quickActions" :key="action.label" class="quick-action" type="button" @click="router.push(action.path)">
              <span class="quick-action__icon" :class="`is-${action.tone}`">
                <el-icon><component :is="action.icon" /></el-icon>
              </span>
              <span>
                <strong>{{ action.label }}</strong>
                <small>{{ action.description }}</small>
              </span>
            </button>
          </div>

          <div class="quick-flow">
            <div class="quick-flow__title">推荐处理顺序</div>
            <div class="quick-flow__steps">
              <span>补客户/品牌资料</span>
              <span>维护项目资料</span>
              <span>核心问题入库</span>
              <span>提交负责人确认</span>
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Collection, Folder, FolderAdd, Plus, Refresh, User } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getCompanyList, getCompanySubmissionReadiness, type PartnerSubmissionReadiness } from '@/api/customer'
import { getProjectList } from '@/api/project'
import { useUserStore } from '@/stores/user'
import type { Company, Project, ProjectStatus } from '@/types'
import { errorMessage } from '@/utils/error'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const companies = ref<Company[]>([])
const projects = ref<Project[]>([])
const submissionReadinessByCompanyId = ref<Record<number, PartnerSubmissionReadiness>>({})

const incompleteCompanies = computed(() =>
  companies.value.filter((company) => missingCompanyFields(company).length > 0),
)
const customerCompletionTasks = computed(() =>
  companies.value
    .map((company) => {
      const missingFields = missingCompanyFields(company)
      const readiness = submissionReadinessByCompanyId.value[company.id]
      const pendingItems = readiness?.items?.filter((item) => !item.ready) || []
      const hints = [
        ...missingFields.map((field) => `缺少${field}`),
        ...pendingItems.map((item) => item.title),
      ]
      return {
        company,
        pendingCount: Math.max(readiness?.pendingCount || 0, 0) + missingFields.length,
        hint: uniqueTexts(hints).slice(0, 3).join('、') || '资料待补齐',
      }
    })
    .filter((task) => task.pendingCount > 0),
)
const workflowTaskCompanies = computed(() =>
  companies.value.filter((company) => ['project_entry', 'entry_completed'].includes(String(company.partnerWorkflowStatus || ''))),
)
const actionableProjects = computed(() =>
  projects.value.filter((project) => ['draft', 'pending_start', 'submitted', 'rejected', 'approved_pending_setup'].includes(project.status)),
)
const activeProjects = computed(() =>
  projects.value.filter((project) => ['submitted', 'approved_pending_setup', 'setup_ready', 'active'].includes(project.status)),
)
const keywordReadyProjects = computed(() =>
  projects.value.filter((project) =>
    (project.selectedKeywordGroupCount ?? 0) > 0 || (project.selectedCoreQuestionSavedKeywords ?? 0) > 0 || (project.planCoreQuestionLimit ?? 0) > 0,
  ).length,
)
const keywordReadyPercent = computed(() => {
  if (projects.value.length === 0) return 0
  return Math.round((keywordReadyProjects.value / projects.value.length) * 100)
})
const submissionReadinessList = computed(() => Object.values(submissionReadinessByCompanyId.value))
const submissionReadyCompanies = computed(() => submissionReadinessList.value.filter((item) => item.ready).length)
const submissionPendingCompanies = computed(() => customerCompletionTasks.value.length)
const submissionOverviewCards = computed(() => [
  {
    label: '可交负责人复核',
    value: `${submissionReadyCompanies.value} 个客户`,
    hint: '提交前检查清单已通过',
    tone: 'success',
  },
  {
    label: '仍需补齐资料',
    value: `${submissionPendingCompanies.value} 个客户`,
    hint: '客户、品牌、项目或资产存在缺口',
    tone: 'warning',
  },
  {
    label: '核心问题已入库',
    value: `${keywordReadyProjects.value}/${projects.value.length || 0}`,
    hint: '项目核心问题任务完成情况',
    tone: 'primary',
  },
  {
    label: '项目待推进',
    value: `${actionableProjects.value.length} 个项目`,
    hint: '草稿、待启动或总部配置前状态',
    tone: 'info',
  },
])
const submissionPendingCategoryList = computed(() => {
  const counts = new Map<string, number>()
  for (const readiness of submissionReadinessList.value) {
    for (const item of readiness.items || []) {
      if (item.ready) continue
      const label = submissionCategoryLabel(item.category)
      counts.set(label, (counts.get(label) || 0) + 1)
    }
  }
  for (const task of customerCompletionTasks.value) {
    if (!submissionReadinessByCompanyId.value[task.company.id]) {
      counts.set('客户基础资料', (counts.get('客户基础资料') || 0) + task.pendingCount)
    }
  }
  return Array.from(counts.entries()).map(([label, count]) => ({ label, count })).sort((a, b) => b.count - a.count)
})
const quickActions = [
  { label: '新增客户', description: '录入客户基础资料', path: '/partner/my-customers', icon: Plus, tone: 'primary' },
  { label: '维护客户', description: '补齐客户与品牌信息', path: '/partner/my-customers', icon: User, tone: 'success' },
  { label: '新增项目', description: '创建项目并分配额度', path: '/partner/my-projects', icon: FolderAdd, tone: 'sky' },
  { label: '拓词管理', description: '生成并确认核心问题', path: '/partner/layered-keyword-groups', icon: Collection, tone: 'violet' },
]

const metricCards = computed(() => [
  {
    key: 'customers',
    label: '我的客户',
    value: companies.value.length,
    hint: `${customerCompletionTasks.value.length} 个资料待补齐`,
    icon: 'User',
    color: '#2563eb',
  },
  {
    key: 'projects',
    label: '我的项目',
    value: projects.value.length,
    hint: `${activeProjects.value.length} 个推进中`,
    icon: 'Folder',
    color: '#0ea5e9',
  },
  {
    key: 'actions',
    label: '待处理事项',
    value: customerCompletionTasks.value.length + actionableProjects.value.length + workflowTaskCompanies.value.length,
    hint: '资料补齐、协作流转与项目推进合计',
    icon: 'Warning',
    color: '#f59e0b',
  },
  {
    key: 'keywords',
    label: '项目准备率',
    value: `${keywordReadyPercent.value}%`,
    hint: `${keywordReadyProjects.value} 个项目核心问题已入库`,
    icon: 'Collection',
    color: '#10b981',
  },
])

function missingCompanyFields(company: Company) {
  const missing: string[] = []
  if (!company.contactName) missing.push('联系人')
  if (!company.contactPhone) missing.push('联系电话')
  if (!company.industry) missing.push('行业')
  if (!company.businessDirection) missing.push('主营方向')
  if (!company.officialWebsite) missing.push('官网')
  if (!company.cityName && !company.city) missing.push('地区')
  return missing
}

function workflowTaskText(company: Company) {
  const status = String(company.partnerWorkflowStatus || '')
  if (status === 'project_entry') return '请继续录入项目资料，并在拓词管理中维护核心问题'
  if (status === 'entry_completed') return '已提交负责人确认，请等待负责人提交总部工单'
  return '请继续完善客户资料'
}

function goWorkflowTask(company: Company) {
  if (company.partnerWorkflowStatus === 'project_entry') {
    router.push('/partner/my-projects')
    return
  }
  router.push('/partner/my-customers')
}

function goCustomerCompletionTask(task: { company: Company }) {
  router.push(`/partner/customers/${task.company.id}`)
}

function uniqueTexts(values: string[]) {
  return Array.from(new Set(values.map((value) => value.trim()).filter(Boolean)))
}

function projectStatusText(status?: ProjectStatus | null) {
  const labels: Record<string, string> = {
    draft: '草稿',
    pending_start: '待提交启动',
    submitted: '启动申请待审批',
    rejected: '申请已驳回',
    approved_pending_setup: '总部处理中',
    setup_ready: '配置完成',
    active: '执行中',
    paused: '已暂停',
    completed: '已完成',
    archived: '已归档',
    cancelled: '已取消',
    expired: '已过期',
  }
  return labels[String(status || '')] || status || '-'
}

function submissionCategoryLabel(category?: string | null) {
  const labels: Record<string, string> = {
    company: '客户基础资料',
    package: '合伙人套餐',
    project: '项目基础资料',
    brand: '品牌基础资料',
    competitor: '竞品信息',
    keyword: '核心问题',
    channel: '展示渠道额度',
    asset: '品牌图片资产',
  }
  return labels[String(category || '')] || '其他资料'
}

async function reload() {
  loading.value = true
  try {
    const [companyRes, projectRes] = await Promise.all([
      getCompanyList({ current: 1, size: 200 }),
      getProjectList({ current: 1, size: 200 }),
    ])
    companies.value = companyRes.data.data.records || []
    projects.value = projectRes.data.data.records || []
    await loadSubmissionReadiness()
  } catch (error) {
    ElMessage.error(errorMessage(error, '交付工作台数据加载失败'))
  } finally {
    loading.value = false
  }
}

async function loadSubmissionReadiness() {
  const candidates = companies.value.filter((company) =>
    ['project_entry', 'entry_completed'].includes(String(company.partnerWorkflowStatus || '')),
  )
  if (candidates.length === 0) {
    submissionReadinessByCompanyId.value = {}
    return
  }
  const entries = await Promise.allSettled(
    candidates.map(async (company) => {
      const { data } = await getCompanySubmissionReadiness(company.id)
      return [company.id, data.data] as const
    }),
  )
  const next: Record<number, PartnerSubmissionReadiness> = {}
  for (const entry of entries) {
    if (entry.status === 'fulfilled' && entry.value[1]) {
      next[entry.value[0]] = entry.value[1]
    }
  }
  submissionReadinessByCompanyId.value = next
}

onMounted(() => {
  reload()
})
</script>

<style scoped>
.partner-staff-workbench {
  --staff-border: #e2e8f0;
  --staff-muted: #64748b;
}

.staff-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 28px 30px;
  background:
    linear-gradient(135deg, rgba(37, 99, 235, 0.08), rgba(14, 165, 233, 0.08)),
    #ffffff;
}

.staff-hero__eyebrow {
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 700;
  color: #2563eb;
}

.staff-hero h2 {
  margin: 0;
  color: #0f172a;
  font-size: 24px;
  font-weight: 800;
}

.staff-hero p {
  max-width: 760px;
  margin: 10px 0 0;
  color: var(--staff-muted);
  line-height: 1.7;
}

.staff-hero__actions {
  display: flex;
  gap: 12px;
  flex-shrink: 0;
}

.staff-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.staff-metric {
  min-height: 140px;
  padding: 20px;
  border: 1px solid var(--staff-border);
  border-radius: 16px;
  background: #ffffff;
  box-shadow: 0 18px 44px -28px rgba(15, 23, 42, 0.28);
  position: relative;
  overflow: hidden;
}

.staff-metric::before {
  position: absolute;
  inset: 0 auto 0 0;
  width: 4px;
  background: var(--accent);
  content: '';
}

.staff-metric__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--staff-muted);
  font-size: 13px;
  font-weight: 700;
}

.staff-metric__top .el-icon {
  color: var(--accent);
}

.staff-metric__value {
  margin-top: 18px;
  color: #0f172a;
  font-size: 30px;
  font-weight: 800;
  line-height: 1;
}

.staff-metric__hint {
  margin-top: 10px;
  color: var(--staff-muted);
  font-size: 13px;
}

.staff-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.staff-workflow-panel {
  padding: 22px 24px;
  border-color: #fed7aa;
  background: linear-gradient(135deg, #fff7ed 0%, #ffffff 70%);
}

.staff-grid--bottom {
  align-items: stretch;
}

.staff-panel {
  padding: 24px;
}

.staff-task-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.staff-task {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px;
  border: 1px solid #e7edf6;
  border-radius: 14px;
  background: #f8fafc;
  color: inherit;
  text-align: left;
  cursor: pointer;
  transition: all 0.18s ease;
}

.staff-task:hover {
  border-color: #bfdbfe;
  background: #ffffff;
  transform: translateY(-1px);
  box-shadow: 0 12px 30px -24px rgba(15, 23, 42, 0.35);
}

.staff-task__icon {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.staff-task__icon.is-customer {
  background: #ecfdf5;
  color: #059669;
}

.staff-task__icon.is-project {
  background: #eff6ff;
  color: #2563eb;
}

.staff-task__main {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.staff-task__main strong {
  overflow: hidden;
  color: #0f172a;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.staff-task__main small {
  color: var(--staff-muted);
  font-size: 12px;
}

.staff-task__badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 42px;
  height: 26px;
  padding: 0 9px;
  border-radius: 999px;
  background: #fff7ed;
  color: #c2410c;
  font-size: 12px;
  font-weight: 850;
  white-space: nowrap;
}

.submission-overview {
  display: grid;
  gap: 16px;
}

.submission-stat-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.submission-stat {
  min-height: 104px;
  padding: 14px 16px;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background: #f8fafc;
}

.submission-stat span,
.submission-stat small {
  display: block;
  color: var(--staff-muted);
  font-size: 12px;
  font-weight: 750;
}

.submission-stat strong {
  display: block;
  margin: 10px 0 6px;
  color: #0f172a;
  font-size: 22px;
  line-height: 1;
  font-weight: 900;
}

.submission-stat.is-success {
  border-color: #bbf7d0;
  background: linear-gradient(180deg, #f0fdf4 0%, #ffffff 100%);
}

.submission-stat.is-warning {
  border-color: #fed7aa;
  background: linear-gradient(180deg, #fff7ed 0%, #ffffff 100%);
}

.submission-stat.is-primary {
  border-color: #bfdbfe;
  background: linear-gradient(180deg, #eff6ff 0%, #ffffff 100%);
}

.submission-stat.is-info {
  border-color: #ddd6fe;
  background: linear-gradient(180deg, #f5f3ff 0%, #ffffff 100%);
}

.submission-progress {
  padding: 8px 2px 0;
}

.staff-progress__row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
  color: #334155;
}

.staff-progress__row strong {
  color: #0f172a;
}

.submission-blockers {
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background: #ffffff;
}

.submission-blockers strong {
  color: #0f172a;
  font-weight: 900;
}

.submission-blockers p {
  margin: 5px 0 0;
  color: var(--staff-muted);
  font-size: 13px;
  line-height: 1.7;
}

.submission-chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.submission-chip {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border: 1px solid #fed7aa;
  border-radius: 999px;
  background: #fff7ed;
  color: #9a3412;
  font-size: 12px;
  font-weight: 800;
}

.submission-ready-note {
  padding: 10px 12px;
  border-radius: 12px;
  background: #ecfdf5;
  color: #047857;
  font-size: 13px;
  font-weight: 800;
}

.submission-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.quick-workbench {
  display: grid;
  gap: 14px;
}

.staff-quick-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.quick-action {
  min-height: 92px;
  display: inline-flex;
  align-items: center;
  justify-content: flex-start;
  gap: 12px;
  padding: 14px;
  border: 1px solid #dbe4f0;
  border-radius: 14px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
  color: #334155;
  text-align: left;
  cursor: pointer;
  transition: all 0.18s ease;
}

.quick-action:hover {
  border-color: #bfdbfe;
  background: #eff6ff;
  color: #2563eb;
}

.quick-action__icon {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 18px;
}

.quick-action__icon.is-primary {
  background: #eff6ff;
  color: #2563eb;
}

.quick-action__icon.is-success {
  background: #ecfdf5;
  color: #059669;
}

.quick-action__icon.is-sky {
  background: #f0f9ff;
  color: #0284c7;
}

.quick-action__icon.is-violet {
  background: #f5f3ff;
  color: #7c3aed;
}

.quick-action strong,
.quick-action small {
  display: block;
}

.quick-action strong {
  color: #0f172a;
  font-size: 14px;
  font-weight: 900;
}

.quick-action small {
  margin-top: 5px;
  color: var(--staff-muted);
  font-size: 12px;
  line-height: 1.4;
  font-weight: 700;
}

.quick-flow {
  padding: 14px;
  border: 1px dashed #bfdbfe;
  border-radius: 14px;
  background: #f8fbff;
}

.quick-flow__title {
  margin-bottom: 10px;
  color: #334155;
  font-size: 13px;
  font-weight: 900;
}

.quick-flow__steps {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.quick-flow__steps span {
  min-height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 8px;
  border-radius: 999px;
  background: #ffffff;
  color: #475569;
  font-size: 12px;
  font-weight: 800;
}

@media (max-width: 1200px) {
  .staff-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .staff-hero,
  .staff-grid {
    grid-template-columns: 1fr;
  }

  .staff-hero {
    align-items: flex-start;
    flex-direction: column;
  }

  .staff-hero__actions {
    width: 100%;
  }
}

@media (max-width: 640px) {
  .staff-metrics,
  .submission-stat-grid,
  .quick-flow__steps,
  .staff-quick-actions {
    grid-template-columns: 1fr;
  }

  .submission-actions {
    flex-direction: column;
  }
}
</style>
