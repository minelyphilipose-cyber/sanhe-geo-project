<template>
  <header class="mobile-header">
    <div class="mobile-header__text">
      <h1>幻境AI·数据看板</h1>
      <p>{{ brandName }} | {{ pageName }}</p>
    </div>
    <button v-if="filterLabel" class="mobile-header__filter" type="button">
      <van-icon name="calendar-o" />
      <span>{{ filterLabel }}</span>
      <van-icon name="arrow-down" />
    </button>
  </header>
</template>

<script setup lang="ts">
defineProps<{
  brandName: string
  pageName: string
  filterLabel?: string
}>()
</script>

<style scoped>
.mobile-header {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 16px 12px;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid #eef0f2;
}

.mobile-header__text {
  min-width: 0;
}

.mobile-header h1 {
  margin: 0;
  color: #0f172a;
  font-size: 18px;
  font-weight: 800;
  line-height: 1.25;
  letter-spacing: 0;
}

.mobile-header p {
  margin: 4px 0 0;
  color: #9ca3af;
  font-size: 12px;
  line-height: 1.35;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.mobile-header__filter {
  flex: 0 0 auto;
  height: 34px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 0 10px;
  border: 1px solid #d7f0e5;
  border-radius: 999px;
  background: #e6f7ef;
  color: #07a66b;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}
</style>
