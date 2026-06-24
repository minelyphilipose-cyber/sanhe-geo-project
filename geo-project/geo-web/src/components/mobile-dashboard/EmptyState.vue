<template>
  <div class="mobile-empty-state">
    <van-icon name="bar-chart-o" />
    <strong>{{ title }}</strong>
    <span>{{ description }}</span>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title?: string
  description?: string
}>()
</script>

<style scoped>
.mobile-empty-state {
  min-height: 112px;
  display: grid;
  place-items: center;
  gap: 6px;
  padding: 18px 12px;
  color: #9ca3af;
  text-align: center;
}

.mobile-empty-state .van-icon {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  background: #e6f7ef;
  color: #07a66b;
  font-size: 20px;
}

.mobile-empty-state strong {
  color: #0f172a;
  font-size: 14px;
  font-weight: 700;
}

.mobile-empty-state span {
  max-width: 230px;
  color: #9ca3af;
  font-size: 12px;
  line-height: 1.55;
}
</style>
