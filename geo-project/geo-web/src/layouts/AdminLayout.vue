<template>
  <div class="admin-layout">
    <Sidebar
      :collapsed="appStore.sidebarCollapsed"
      :groups="sidebarGroupsWithBadge"
      @toggle="appStore.toggleSidebar"
    />

    <div
      class="admin-layout__main"
      :style="{ marginLeft: appStore.sidebarCollapsed ? 'var(--sidebar-collapsed-width)' : 'var(--sidebar-width)' }"
    >
      <TopBar />

      <main class="admin-layout__content">
        <router-view v-slot="{ Component }">
          <transition name="fade-slide" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, computed } from 'vue'
import Sidebar from './components/Sidebar.vue'
import TopBar from './components/TopBar.vue'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { getDispatchAlerts } from '@/api/dispatch'
import { getMySystemAlertTodos } from '@/api/systemAlert'
import type { RoleType } from '@/types'

interface MenuItem {
  path: string
  name: string
  title: string
  icon?: string
  roles?: RoleType[]
  excludeRoles?: RoleType[]
  permissions?: string[]
  badgeCount?: number
}

interface MenuGroup {
  key: string
  title?: string
  menus: MenuItem[]
}

const appStore = useAppStore()
const userStore = useUserStore()
const openAlertCount = ref(0)
let badgeTimer: number | null = null

const sidebarGroups: MenuGroup[] = [
  {
    key: 'workspace',
    menus: [
      { path: '/admin/workbench/operator', name: 'OperatorWorkbench', title: '运营工作台', icon: 'Odometer', permissions: ['workbench.operator.read'], excludeRoles: ['super_admin'] },
      { path: '/admin/workbench/sales', name: 'SalesWorkbench', title: '销售工作台', icon: 'TrendCharts', permissions: ['workbench.sales.read'], excludeRoles: ['super_admin'] },
      { path: '/admin/workbench/delivery', name: 'DeliveryWorkbench', title: '交付工作台', icon: 'DataAnalysis', permissions: ['delivery.overview.read'], excludeRoles: ['super_admin'] },
      { path: '/admin/workbench/manager', name: 'ManagerWorkbench', title: '系统工作台', icon: 'Setting', permissions: ['workbench.manager.read'], excludeRoles: ['super_admin'] },
      { path: '/admin/workbench/super-admin', name: 'SuperAdminWorkbench', title: '全局总控', icon: 'DataBoard', roles: ['super_admin'] },
    ],
  },
  {
    key: 'business',
    title: '业务操作',
    menus: [
      { path: '/admin/presale/report', name: 'PresaleReportList', title: 'AI可见度诊断报告', icon: 'Document', permissions: ['presale.report.list'] },
      { path: '/admin/customers', name: 'CustomerList', title: '客户管理', icon: 'User', roles: ['sales', 'operator', 'delivery_manager', 'manager', 'super_admin'], permissions: ['company.read'] },
      { path: '/admin/projects', name: 'ProjectList', title: '项目管理', icon: 'Folder', roles: ['operator', 'delivery_manager', 'manager', 'super_admin'], permissions: ['project.read'] },
      { path: '/admin/layered-keyword-groups', name: 'LayeredKeywordGroupManage', title: '拓词管理', icon: 'Collection', roles: ['sales', 'operator', 'delivery_manager', 'manager', 'super_admin'], permissions: ['keyword_group.read'] },
      { path: '/admin/content/execution', name: 'ContentExecution', title: '内容与执行', icon: 'Memo', roles: ['operator', 'delivery_manager', 'manager', 'super_admin'], permissions: ['content.read'] },
      { path: '/admin/content/special-industry-compliance', name: 'SpecialIndustryComplianceWorkbench', title: '行业专项', icon: 'Files', roles: ['operator', 'delivery_manager', 'manager', 'super_admin'], permissions: ['content.read'] },
      { path: '/admin/partner-start-requests', name: 'PartnerStartRequestWorkbench', title: '合伙人启动工单', icon: 'Tickets', roles: ['delivery_manager', 'manager', 'super_admin'], permissions: ['delivery.assignment.manage'] },
    ],
  },
  {
    key: 'monitoring',
    title: '监控中心',
    menus: [
      { path: '/admin/monitoring/tasks', name: 'MonitoringTasks', title: '调度监控', icon: 'Monitor', permissions: ['content.distribution.retry', 'dispatch.alert.resolve', 'dispatch.task.replay.dead_letter'] },
      { path: '/admin/monitoring/self-media-automation', name: 'SelfMediaAutomationOverview', title: '自媒体自动化', icon: 'DataLine', permissions: ['content.read'] },
      { path: '/admin/monitoring/account-auth-health', name: 'AccountAuthHealth', title: '账号授权健康', icon: 'Warning', permissions: ['content.read'] },
      { path: '/admin/monitoring/platforms', name: 'PlatformHealth', title: '平台健康', icon: 'Cpu', permissions: ['content.read', 'delivery.overview.read', 'user.manage'] },
      { path: '/admin/monitoring/question-poll-verification', name: 'QuestionPollVerification', title: '轮询链路验证', icon: 'VideoPlay', permissions: ['dispatch.question_poll.manual'] },
      { path: '/admin/alerts', name: 'AlertCenter', title: '告警中心', icon: 'Bell', permissions: ['content.distribution.retry', 'dispatch.alert.resolve', 'system.alert.resolve'], badgeCount: 0 },
      { path: '/admin/activity-logs', name: 'ActivityLogs', title: '操作日志', icon: 'Document', roles: ['operator', 'delivery_manager', 'manager', 'super_admin'], permissions: ['user.manage'] },
      { path: '/admin/model-diagnostics', name: 'ModelDiagnosticConsole', title: '大模型诊断台', icon: 'ChatDotRound', permissions: ['ai.platform.diagnose'] },
    ],
  },
  {
    key: 'partner',
    title: '合伙人',
    menus: [
      { path: '/admin/partners', name: 'PartnerList', title: '合伙人管理', icon: 'Coordinate', permissions: ['partner.read'] },
    ],
  },
  {
    key: 'settings',
    title: '系统配置',
    menus: [
      { path: '/admin/settings/platforms', name: 'PlatformConfig', title: 'AI平台配置', icon: 'Setting', permissions: ['user.manage'] },
      { path: '/admin/settings/packages', name: 'PackageConfig', title: '套餐配置', icon: 'CollectionTag', permissions: ['user.manage'] },
      { path: '/admin/settings/dicts', name: 'DictCenter', title: '字典中心', icon: 'Tickets', permissions: ['user.manage'] },
      { path: '/admin/settings/business-calendar', name: 'BusinessCalendarManagement', title: '工作日历', icon: 'Calendar', permissions: ['user.manage'] },
      { path: '/admin/settings/users', name: 'UserManage', title: '用户与权限', icon: 'Setting', permissions: ['user.manage'] },
    ],
  },
]

const sidebarGroupsWithBadge = computed<MenuGroup[]>(() =>
  sidebarGroups.map((group) => ({
    ...group,
    menus: group.menus.map((menu) =>
      menu.name === 'AlertCenter'
        ? { ...menu, badgeCount: openAlertCount.value }
        : menu,
    ),
  })),
)

async function loadOpenAlertCount() {
  const canViewDispatchAlerts = userStore.hasPermission(['content.distribution.retry', 'dispatch.alert.resolve'])
  const canViewSystemAlerts = userStore.hasPermission('system.alert.resolve')
  if (!canViewDispatchAlerts && !canViewSystemAlerts) {
    openAlertCount.value = 0
    return
  }
  try {
    const [dispatchRes, systemRes] = await Promise.all([
      canViewDispatchAlerts
        ? getDispatchAlerts({ current: 1, size: 1, rangeType: 'last7', status: 'open' })
        : Promise.resolve(null),
      canViewSystemAlerts ? getMySystemAlertTodos({ current: 1, size: 1 }) : Promise.resolve(null),
    ])
    openAlertCount.value =
      Number(dispatchRes?.data.data?.total || 0) +
      Number(systemRes?.data.data?.total || 0)
  } catch {
    openAlertCount.value = 0
  }
}

onMounted(async () => {
  await loadOpenAlertCount()
  badgeTimer = window.setInterval(() => {
    if (document.hidden) return
    loadOpenAlertCount()
  }, 60000)
})

onBeforeUnmount(() => {
  if (badgeTimer) {
    window.clearInterval(badgeTimer)
    badgeTimer = null
  }
})
</script>

<style scoped>
.admin-layout {
  min-height: 100vh;
  background: var(--page-bg);
}

.admin-layout__main {
  transition: margin-left 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.admin-layout__content {
  flex: 1;
  padding: 20px 24px;
  overflow-x: hidden;
}
</style>
