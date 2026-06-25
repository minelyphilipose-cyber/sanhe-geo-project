<template>
  <section class="mobile-dashboard-card">
    <div v-if="title || $slots.action" class="mobile-dashboard-card__head">
      <h2 v-if="title">
        <MobileIcon v-if="icon" :name="icon" />
        <span>{{ title }}</span>
      </h2>
      <slot name="action" />
    </div>
    <slot />
  </section>
</template>

<script setup lang="ts">
import MobileIcon from './MobileIcon.vue'

defineProps<{
  title?: string
  icon?: string
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
  box-shadow: 0 8px 22px rgba(15, 23, 42, 0.03);
}

.mobile-dashboard-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.mobile-dashboard-card__head h2 {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  color: #131b2e;
  font-size: 16px;
  font-weight: 800;
  line-height: 1.35;
  letter-spacing: 0;
}

.mobile-dashboard-card__head h2 .mobile-icon {
  color: #006D44;
  font-size: 18px;
}

@media (max-width: 374px) {
  .mobile-dashboard-card {
    padding: 14px;
    border-radius: 15px;
  }
}
</style>
