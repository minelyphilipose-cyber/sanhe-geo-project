<template>
  <van-tabbar
    :model-value="activeName"
    fixed
    safe-area-inset-bottom
    active-color="#006D44"
    inactive-color="#52625C"
    @change="handleChange"
  >
    <van-tabbar-item name="home">
      <template #icon><MobileIcon name="home" /></template>
      首页
    </van-tabbar-item>
    <van-tabbar-item name="monitor">
      <template #icon><MobileIcon name="monitor" /></template>
      监测
    </van-tabbar-item>
    <van-tabbar-item name="content">
      <template #icon><MobileIcon name="content" /></template>
      内容
    </van-tabbar-item>
  </van-tabbar>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MobileIcon from './MobileIcon.vue'

const route = useRoute()
const router = useRouter()
const tabRouteMap: Record<string, string> = {
  home: 'MobileDashboardHome',
  monitor: 'MobileDashboardMonitor',
  content: 'MobileDashboardContent',
}

const activeName = computed(() => {
  if (route.name === 'MobileDashboardMonitor' || route.name === 'MobileDashboardQuestionDetail') return 'monitor'
  if (route.name === 'MobileDashboardContent') return 'content'
  return 'home'
})

function handleChange(name: string) {
  const routeName = tabRouteMap[name]
  const shareCode = String(route.params.shareCode || '')
  if (routeName && shareCode && routeName !== route.name) {
    router.replace({ name: routeName, params: { shareCode } })
  }
}
</script>

<style scoped>
:deep(.van-tabbar) {
  height: calc(64px + env(safe-area-inset-bottom));
  padding-bottom: env(safe-area-inset-bottom);
  border-top: 1px solid var(--mobile-border, #eef0f2);
  box-shadow: 0 -2px 12px rgba(15, 23, 42, 0.03);
}

:deep(.van-tabbar-item) {
  min-width: 44px;
  min-height: 44px;
  font-size: var(--mobile-text-xs, 12px);
  font-weight: 500;
  line-height: var(--mobile-leading-label, 16px);
}

:deep(.van-tabbar-item__icon) {
  font-size: 24px;
  margin-bottom: 2px;
}
</style>
