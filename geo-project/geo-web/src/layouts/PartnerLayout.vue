<template>
  <div class="admin-layout">
    <Sidebar
      :collapsed="appStore.sidebarCollapsed"
      :groups="partnerGroupsWithBadge"
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
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import Sidebar from './components/Sidebar.vue'
import TopBar from './components/TopBar.vue'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { getMySystemAlertUnreadCount } from '@/api/systemAlert'
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
const route = useRoute()
const userStore = useUserStore()
const stationMessageCount = ref(0)
let stationMessageTimer: number | null = null
const handleStationMessageCountChanged = () => {
  loadStationMessageCount()
}

const partnerGroups: MenuGroup[] = [
  {
    key: 'partner',
    menus: [
      { path: '/partner/home', name: 'PartnerHome', title: '首页', icon: 'HomeFilled', roles: ['partner'], permissions: ['partner.read'] },
      { path: '/partner/staff-workbench', name: 'PartnerStaffWorkbench', title: '工作台', icon: 'Odometer', roles: ['partner_staff'] },
      { path: '/partner/my-customers', name: 'MyCustomers', title: '客户管理', icon: 'User', roles: ['partner', 'partner_staff'], permissions: ['company.read'] },
      { path: '/partner/staff', name: 'PartnerStaffManage', title: '交付员工', icon: 'UserFilled', roles: ['partner'], permissions: ['partner.staff.manage'] },
      { path: '/partner/my-projects', name: 'MyProjects', title: '项目管理', icon: 'Folder', roles: ['partner', 'partner_staff'], permissions: ['project.read'] },
      { path: '/partner/layered-keyword-groups', name: 'PartnerLayeredKeywordGroupManage', title: '拓词管理', icon: 'Collection', roles: ['partner', 'partner_staff'], permissions: ['keyword_group.read'] },
      { path: '/partner/balance', name: 'PartnerBalance', title: '余额与扣款', icon: 'Wallet', roles: ['partner'], permissions: ['partner.read'] },
      { path: '/partner/presale/report', name: 'PartnerPresaleReportList', title: 'AI可见度诊断报告', icon: 'Document', roles: ['partner'], permissions: ['presale.report.list'] },
      { path: '/partner/alerts', name: 'PartnerAlertCenter', title: '站内信', icon: 'Bell', roles: ['partner', 'partner_staff'] },
    ],
  },
]

const partnerGroupsWithBadge = computed<MenuGroup[]>(() =>
  partnerGroups.map((group) => ({
    ...group,
    menus: group.menus.map((menu) =>
      menu.name === 'PartnerAlertCenter'
        ? { ...menu, badgeCount: stationMessageCount.value }
        : menu,
    ),
  })),
)

async function loadStationMessageCount() {
  try {
    const { data } = await getMySystemAlertUnreadCount()
    stationMessageCount.value = Number(data.data || 0)
  } catch {
    stationMessageCount.value = 0
  }
}

watch(() => route.path, (path) => {
  if (path === '/partner/alerts') {
    loadStationMessageCount()
  }
})

onMounted(async () => {
  await loadStationMessageCount()
  window.addEventListener('system-alert-count-changed', handleStationMessageCountChanged)
  stationMessageTimer = window.setInterval(() => {
    if (document.hidden) return
    loadStationMessageCount()
  }, 60000)
})

onBeforeUnmount(() => {
  window.removeEventListener('system-alert-count-changed', handleStationMessageCountChanged)
  if (stationMessageTimer) {
    window.clearInterval(stationMessageTimer)
    stationMessageTimer = null
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
}
</style>

