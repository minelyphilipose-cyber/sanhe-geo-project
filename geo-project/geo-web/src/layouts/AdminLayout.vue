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
import type { RoleType } from '@/types'

interface MenuItem {
  path: string
  name: string
  title: string
  icon?: string
  roles?: RoleType[]
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
      { path: '/admin/overview', name: 'Overview', title: '工作台', icon: 'Odometer', permissions: ['company.read'] },
    ],
  },
  {
    key: 'business',
    title: '业务操作',
    menus: [
      { path: '/admin/presale/report', name: 'PresaleReportList', title: '售前报告', icon: 'Document', permissions: ['presale.report.list'] },
      { path: '/admin/customers', name: 'CustomerList', title: '客户管理', icon: 'User', roles: ['sales', 'operator', 'delivery_manager', 'manager', 'super_admin'], permissions: ['company.read'] },
      { path: '/admin/layered-keyword-groups', name: 'LayeredKeywordGroupManage', title: '分层拓词管理', icon: 'Collection', roles: ['sales', 'operator', 'delivery_manager', 'manager', 'super_admin'], permissions: ['keyword_group.read'] },
      { path: '/admin/projects', name: 'ProjectList', title: '项目管理', icon: 'Folder', roles: ['operator', 'delivery_manager', 'manager', 'super_admin'], permissions: ['project.read'] },
      { path: '/admin/content/execution', name: 'ContentExecution', title: '内容与执行', icon: 'Memo', roles: ['operator', 'delivery_manager', 'manager', 'super_admin'], permissions: ['project.read'] },
    ],
  },
  {
    key: 'monitoring',
    title: '监控中心',
    menus: [
      { path: '/admin/monitoring/tasks', name: 'MonitoringTasks', title: '调度监控', icon: 'Monitor', roles: ['operator', 'delivery_manager', 'manager', 'super_admin'] },
      { path: '/admin/monitoring/platforms', name: 'PlatformHealth', title: '平台健康', icon: 'Cpu', roles: ['operator', 'delivery_manager', 'manager', 'super_admin'] },
      { path: '/admin/alerts', name: 'AlertCenter', title: '告警中心', icon: 'Bell', roles: ['operator', 'delivery_manager', 'manager', 'super_admin'], badgeCount: 0 },
      { path: '/admin/activity-logs', name: 'ActivityLogs', title: '操作日志', icon: 'Document', roles: ['operator', 'delivery_manager', 'manager', 'super_admin'], permissions: ['user.manage'] },
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
      { path: '/admin/settings/affix-words', name: 'KeywordAffixWordManage', title: '拓词信息维护', icon: 'EditPen', permissions: ['keyword_affix.manage'] },
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
  if (!userStore.hasRole(['operator', 'delivery_manager', 'manager', 'super_admin'])) {
    openAlertCount.value = 0
    return
  }
  try {
    const { data } = await getDispatchAlerts({ current: 1, size: 1, rangeType: 'last7', status: 'open' })
    openAlertCount.value = Number(data.data?.total || 0)
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
