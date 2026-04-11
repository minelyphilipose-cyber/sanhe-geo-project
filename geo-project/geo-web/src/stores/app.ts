import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getUIPreference, setUIPreference } from '@/utils/storage'

export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(getUIPreference('sidebar_collapsed', false))

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
    setUIPreference('sidebar_collapsed', sidebarCollapsed.value)
  }

  // 全局 loading (用于页面切换)
  const pageLoading = ref(false)

  return {
    sidebarCollapsed,
    toggleSidebar,
    pageLoading,
  }
})
