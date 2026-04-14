<template>
  <div class="admin-layout">
    <Sidebar
      :collapsed="appStore.sidebarCollapsed"
      :menus="sidebarMenus"
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
import Sidebar from './components/Sidebar.vue'
import TopBar from './components/TopBar.vue'
import { useAppStore } from '@/stores/app'
import type { RoleType } from '@/types'

interface MenuItem {
  path: string
  name: string
  title: string
  icon?: string
  roles?: RoleType[]
  permissions?: string[]
}

const appStore = useAppStore()

const sidebarMenus: MenuItem[] = [
  { path: '/admin/overview', name: 'Overview', title: '工作台', icon: 'Odometer', permissions: ['company.read'] },
  { path: '/admin/customers', name: 'CustomerList', title: '客户管理', icon: 'User', permissions: ['company.read'] },
  { path: '/admin/projects', name: 'ProjectList', title: '项目管理', icon: 'Folder', permissions: ['project.read'] },
  { path: '/admin/monitoring', name: 'Monitoring', title: '监测中心', icon: 'Monitor', roles: ['delivery_manager', 'manager', 'super_admin'] },
  { path: '/admin/content/execution', name: 'ContentExecution', title: '内容与执行', icon: 'Memo', permissions: ['project.read'] },
  { path: '/admin/reports', name: 'ReportManage', title: '报表管理', icon: 'DataAnalysis', permissions: ['report.review'] },
  { path: '/admin/partners', name: 'PartnerList', title: '合伙人管理', icon: 'Coordinate', permissions: ['partner.read'] },
  { path: '/admin/alerts', name: 'AlertCenter', title: '异常中心', icon: 'Bell', roles: ['delivery_manager', 'manager', 'super_admin'] },
  { path: '/admin/activity-logs', name: 'ActivityLogs', title: '操作日志', icon: 'Document', permissions: ['user.manage'] },
  { path: '/admin/settings/platforms', name: 'Settings', title: '平台配置', icon: 'Setting', roles: ['super_admin'] },
  { path: '/admin/settings/publish-sites', name: 'PublishSiteConfig', title: '发布站点配置', icon: 'Promotion', permissions: ['user.manage'] },
  { path: '/admin/settings/packages', name: 'PackageConfig', title: '套餐配置', icon: 'CollectionTag', permissions: ['user.manage'] },
  { path: '/admin/settings/dicts', name: 'DictCenter', title: '字典中心', icon: 'Tickets', permissions: ['user.manage'] },
  { path: '/admin/settings/users', name: 'UserManage', title: '用户管理', icon: 'Setting', permissions: ['user.manage'] },
]
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
