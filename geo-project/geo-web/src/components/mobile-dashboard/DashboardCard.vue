<template>
  <section class="mobile-dashboard-card">
    <div v-if="title || $slots.action" class="mobile-dashboard-card__head">
      <h2 v-if="title">{{ title }}</h2>
      <slot name="action" />
    </div>
    <slot />
  </section>
</template>

<script setup lang="ts">
defineProps<{
  title?: string
}>()
</script>

<style scoped>
.mobile-dashboard-card {
  min-width: 0;
  max-width: 100%;
  padding: 16px;
  border: 1px solid #eef0f2;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.04);
}

.mobile-dashboard-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.mobile-dashboard-card__head h2 {
  margin: 0;
  color: #0f172a;
  font-size: 16px;
  font-weight: 700;
  line-height: 1.35;
  letter-spacing: 0;
}
</style>
