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
            <h2>待完善客户资料</h2>
            <p>缺少官网、行业、主营方向、联系人等基础信息的客户。</p>
          </div>
          <el-tag type="warning" round>{{ incompleteCompanies.length }}</el-tag>
        </div>

        <el-skeleton v-if="loading" :rows="4" animated />
        <el-empty v-else-if="incompleteCompanies.length === 0" description="客户资料较完整" :image-size="70" />
        <div v-else class="staff-task-list">
          <button
            v-for="company in incompleteCompanies.slice(0, 5)"
            :key="company.id"
            class="staff-task"
            type="button"
            @click="router.push('/partner/my-customers')"
          >
            <span class="staff-task__icon is-customer">
              <el-icon><User /></el-icon>
            </span>
            <span class="staff-task__main">
              <strong>{{ company.companyName }}</strong>
              <small>{{ missingCompanyFields(company).join('、') }}</small>
            </span>
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
            <h2>核心问题准备</h2>
            <p>关注项目是否已选择核心问题池及额度。</p>
          </div>
          <el-tag :type="keywordReadyProjects === projects.length ? 'success' : 'info'" round>
            {{ keywordReadyProjects }}/{{ projects.length }}
          </el-tag>
        </div>
        <div class="staff-progress">
          <div class="staff-progress__row">
            <span>已配置核心问题</span>
            <strong>{{ keywordReadyProjects }} 个项目</strong>
          </div>
          <el-progress :percentage="keywordReadyPercent" :stroke-width="10" :show-text="false" />
          <div class="staff-progress__hint">拓词与核心问题准备完成后，合伙人负责人可核对资料并向总部提交启动工单。</div>
        </div>
      </section>

      <section class="partner-surface staff-panel">
        <div class="partner-section-head">
          <div>
            <h2>快捷操作</h2>
            <p>直接进入今天最常用的录入与维护入口。</p>
          </div>
        </div>
        <div class="staff-quick-actions">
          <el-button type="primary" @click="router.push('/partner/my-customers')">
            <el-icon><Plus /></el-icon>
            新增客户
          </el-button>
          <el-button @click="router.push('/partner/my-customers')">
            <el-icon><User /></el-icon>
            维护客户
          </el-button>
          <el-button @click="router.push('/partner/my-projects')">
            <el-icon><FolderAdd /></el-icon>
            新增项目
          </el-button>
          <el-button @click="router.push('/partner/layered-keyword-groups')">
            <el-icon><Collection /></el-icon>
            拓词管理
          </el-button>
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
import { getCompanyList } from '@/api/customer'
import { getProjectList } from '@/api/project'
import { useUserStore } from '@/stores/user'
import type { Company, Project, ProjectStatus } from '@/types'
import { errorMessage } from '@/utils/error'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const companies = ref<Company[]>([])
const projects = ref<Project[]>([])

const incompleteCompanies = computed(() =>
  companies.value.filter((company) => missingCompanyFields(company).length > 0),
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

const metricCards = computed(() => [
  {
    key: 'customers',
    label: '我的客户',
    value: companies.value.length,
    hint: `${incompleteCompanies.value.length} 个资料待完善`,
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
    value: incompleteCompanies.value.length + actionableProjects.value.length + workflowTaskCompanies.value.length,
    hint: '客户资料、协作流转与项目推进合计',
    icon: 'Warning',
    color: '#f59e0b',
  },
  {
    key: 'keywords',
    label: '核心问题完成率',
    value: `${keywordReadyPercent.value}%`,
    hint: `${keywordReadyProjects.value} 个项目已准备`,
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

async function reload() {
  loading.value = true
  try {
    const [companyRes, projectRes] = await Promise.all([
      getCompanyList({ current: 1, size: 200 }),
      getProjectList({ current: 1, size: 200 }),
    ])
    companies.value = companyRes.data.data.records || []
    projects.value = projectRes.data.data.records || []
  } catch (error) {
    ElMessage.error(errorMessage(error, '交付工作台数据加载失败'))
  } finally {
    loading.value = false
  }
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

.staff-progress {
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

.staff-progress__hint {
  margin-top: 14px;
  color: var(--staff-muted);
  font-size: 13px;
  line-height: 1.7;
}

.staff-quick-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.staff-quick-actions :deep(.el-button) {
  height: 44px;
  margin-left: 0;
  justify-content: center;
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
  .staff-quick-actions {
    grid-template-columns: 1fr;
  }
}
</style>
