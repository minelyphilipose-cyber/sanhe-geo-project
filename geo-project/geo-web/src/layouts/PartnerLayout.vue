<template>
  <div class="admin-layout">
    <Sidebar
      :collapsed="appStore.sidebarCollapsed"
      :menus="partnerMenus"
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

const appStore = useAppStore()

const partnerMenus = [
  { path: '/partner/home',         name: 'PartnerHome',    title: '首页',       icon: 'HomeFilled' },
  { path: '/partner/my-customers', name: 'MyCustomers',    title: '我的客户',   icon: 'User'       },
  { path: '/partner/my-projects',  name: 'MyProjects',     title: '我的项目',   icon: 'Folder'     },
  { path: '/partner/balance',      name: 'PartnerBalance', title: '余额与扣款', icon: 'Wallet'     },
  { path: '/partner/training',     name: 'TrainingCenter', title: '培训中心',   icon: 'Reading'    },
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
}
</style>
