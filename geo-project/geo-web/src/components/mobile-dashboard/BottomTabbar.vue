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
    <van-tabbar-item name="report">
      <template #icon><MobileIcon name="report" /></template>
      报告
    </van-tabbar-item>
  </van-tabbar>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MobileIcon from './MobileIcon.vue'

const route = useRoute()
const router = useRouter()
const tabPathMap: Record<string, string> = {
  home: '/home',
  monitor: '/monitor',
  content: '/content',
  report: '/report',
}

const activeName = computed(() => {
  if (route.path.startsWith('/monitor')) return 'monitor'
  if (route.path.startsWith('/content')) return 'content'
  if (route.path.startsWith('/report')) return 'report'
  return 'home'
})

function handleChange(name: string) {
  const path = tabPathMap[name]
  if (path && path !== route.path) {
    router.replace(path)
  }
}
</script>

<style scoped>
:deep(.van-tabbar-item__icon) {
  font-size: 22px;
  margin-bottom: 2px;
}
</style>
