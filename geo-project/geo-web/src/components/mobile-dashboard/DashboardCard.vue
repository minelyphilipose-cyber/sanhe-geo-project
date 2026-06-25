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
  border: 1px solid var(--mobile-border, #eef0f2);
  border-radius: 16px;
  background: #fff;
  box-shadow: var(--mobile-card-shadow, 0 4px 20px rgba(15, 23, 42, 0.04));
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
  color: var(--mobile-text, #131b2e);
  font-size: var(--mobile-text-lg, 16px);
  font-weight: 600;
  line-height: var(--mobile-leading-lg, 22px);
}

.mobile-dashboard-card__head h2 .mobile-icon {
  color: var(--mobile-primary, #006D44);
  font-size: 20px;
}

@media (max-width: 374px) {
  .mobile-dashboard-card {
    padding: 14px;
    border-radius: 15px;
  }
}
</style>
