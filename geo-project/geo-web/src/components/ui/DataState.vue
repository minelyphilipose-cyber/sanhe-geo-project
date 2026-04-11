<template>
  <div v-if="loading" class="data-state">
    <el-skeleton :rows="4" animated />
  </div>
  <div v-else-if="empty" class="data-state">
    <el-empty :description="emptyText" :image-size="88" />
  </div>
  <slot v-else />
</template>

<script setup lang="ts">
defineProps<{
  loading: boolean
  empty: boolean
  emptyText?: string
}>()
</script>

<style scoped>
.data-state {
  padding: 24px 0;
}
</style>

