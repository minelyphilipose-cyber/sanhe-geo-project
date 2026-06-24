<template>
  <main class="mobile-dashboard-shell">
    <template v-if="loading">
      <div class="mobile-dashboard-loading">
        <van-skeleton title :row="8" />
      </div>
    </template>
    <template v-else-if="errorMessage">
      <div class="mobile-dashboard-error">
        <van-icon name="warning-o" />
        <h1>链接不可用</h1>
        <p>{{ errorMessage }}</p>
      </div>
    </template>
    <template v-else>
      <AppHeader
        :brand-name="mobileDashboardStore.brandName"
        :page-name="currentPageName"
        :filter-label="currentFilterLabel"
      />
      <section class="mobile-dashboard-content">
        <router-view />
      </section>
      <BottomTabbar />
    </template>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from '@/components/mobile-dashboard/AppHeader.vue'
import BottomTabbar from '@/components/mobile-dashboard/BottomTabbar.vue'
import { useMobileDashboardStore } from '@/stores/mobileDashboard'

const route = useRoute()
const mobileDashboardStore = useMobileDashboardStore()
const loading = ref(true)
const errorMessage = ref('')

const pageConfig: Record<string, { name: string; filter?: string }> = {
  '/home': { name: '客户总览', filter: '近14天' },
  '/monitor': { name: 'AI监测', filter: '近14天' },
  '/content': { name: '内容交付', filter: '本月' },
  '/report': { name: '阶段报告' },
}

const currentPageName = computed(() => pageConfig[route.path]?.name || '客户总览')
const currentFilterLabel = computed(() => pageConfig[route.path]?.filter)

function removeTokenFromAddressBar() {
  if (!('t' in route.query)) return
  const url = new URL(window.location.href)
  url.searchParams.delete('t')
  const clean = `${url.pathname}${url.search}${url.hash}`
  window.history.replaceState(window.history.state, '', clean)
}

onMounted(async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const entryToken = typeof route.query.t === 'string' ? route.query.t : ''
    await mobileDashboardStore.initialize(entryToken)
    removeTokenFromAddressBar()
  } catch (error: any) {
    mobileDashboardStore.clearAll()
    errorMessage.value = error?.message || '分享链接已失效或已过期，请联系交付顾问重新获取。'
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.mobile-dashboard-shell {
  min-height: 100vh;
  background: #fff;
  color: #0f172a;
  font-family: 'Noto Sans SC', -apple-system, BlinkMacSystemFont, 'PingFang SC', sans-serif;
  overflow-x: hidden;
}

.mobile-dashboard-loading {
  padding: 28px 16px;
}

.mobile-dashboard-content {
  min-height: calc(100vh - 56px);
  padding: 14px 12px calc(128px + env(safe-area-inset-bottom));
  background: #fff;
  overflow-x: hidden;
}

.mobile-dashboard-error {
  min-height: 100vh;
  display: grid;
  place-content: center;
  gap: 10px;
  padding: 24px;
  text-align: center;
  background: #fff;
}

.mobile-dashboard-error .van-icon {
  justify-self: center;
  width: 48px;
  height: 48px;
  display: grid;
  place-items: center;
  border-radius: 16px;
  background: #fff7ed;
  color: #f97316;
  font-size: 26px;
}

.mobile-dashboard-error h1 {
  margin: 0;
  color: #0f172a;
  font-size: 20px;
  font-weight: 800;
}

.mobile-dashboard-error p {
  max-width: 280px;
  margin: 0;
  color: #9ca3af;
  font-size: 14px;
  line-height: 1.65;
}
</style>
