<template>
  <div class="mobile-empty-state">
    <MobileIcon name="dashboard" />
    <strong>{{ title }}</strong>
    <span>{{ description }}</span>
  </div>
</template>

<script setup lang="ts">
import MobileIcon from './MobileIcon.vue'

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
  color: #52625C;
  text-align: center;
}

.mobile-empty-state .mobile-icon {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  background: #e6f7ef;
  color: #006D44;
  font-size: 20px;
}

.mobile-empty-state strong {
  color: #131b2e;
  font-size: 14px;
  font-weight: 700;
}

.mobile-empty-state span {
  max-width: 230px;
  color: #52625C;
  font-size: 12px;
  line-height: 1.55;
}
</style>
