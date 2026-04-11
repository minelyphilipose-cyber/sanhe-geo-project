<template>
  <div class="page-placeholder">
    <div class="page-placeholder__card glass-card">
      <el-icon :size="48" color="#CBD5E1"><component :is="icon" /></el-icon>
      <h2 class="page-placeholder__title">{{ title }}</h2>
      <p class="page-placeholder__desc">{{ description }}</p>
      <el-tag type="info" size="small">Phase {{ phase }} · 待开发</el-tag>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  description: string
  icon?: string
  phase?: number
}>()
</script>

<style scoped>
.page-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: calc(100vh - var(--header-height) - 80px);
}
.page-placeholder__card {
  padding: 48px 64px;
  text-align: center;
  max-width: 480px;
}
.page-placeholder__title {
  font-size: 20px;
  font-weight: 700;
  color: #1E293B;
  margin: 16px 0 8px;
}
.page-placeholder__desc {
  font-size: 14px;
  color: #64748B;
  margin: 0 0 16px;
  line-height: 1.6;
}
</style>
