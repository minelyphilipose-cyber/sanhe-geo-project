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
}

const appStore = useAppStore()

const sidebarMenus: MenuItem[] = [
  { path: '/admin/overview', name: 'Overview', title: '工作台', icon: 'Odometer' },
  { path: '/admin/customers', name: 'CustomerList', title: '客户管理', icon: 'User' },
  { path: '/admin/projects', name: 'ProjectList', title: '项目管理', icon: 'Folder' },
  { path: '/admin/monitoring', name: 'Monitoring', title: '监测中心', icon: 'Monitor' },
  { path: '/admin/reports', name: 'ReportManage', title: '报表管理', icon: 'DataAnalysis', roles: ['delivery_manager', 'manager', 'super_admin'] },
  { path: '/admin/partners', name: 'PartnerList', title: '合伙人管理', icon: 'Coordinate', roles: ['manager', 'super_admin'] },
  { path: '/admin/alerts', name: 'AlertCenter', title: '异常中心', icon: 'Bell' },
  { path: '/admin/settings/platforms', name: 'Settings', title: '平台配置', icon: 'Setting', roles: ['super_admin'] },
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
