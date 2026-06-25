<template>
  <header class="mobile-header">
    <div class="mobile-header__main">
      <div class="mobile-header__brand">
        <MobileIcon
          :name="iconName"
          class="mobile-header__mark"
          :style="{ fontSize: `${iconSize}px` }"
        />
        <h1 :class="{ primary: titleTone === 'primary' }">幻境AI·数据看板</h1>
      </div>
      <button v-if="filterLabel" class="mobile-header__filter" type="button">
        <MobileIcon name="calendar" />
        <span>{{ filterLabel }}</span>
        <MobileIcon name="chevronDown" />
      </button>
    </div>
    <p :class="subtitleTone">{{ brandName }} | {{ pageName }}</p>
  </header>
</template>

<script setup lang="ts">
import MobileIcon from './MobileIcon.vue'

withDefaults(defineProps<{
  brandName: string
  pageName: string
  filterLabel?: string
  iconName?: string
  iconSize?: number
  titleTone?: 'default' | 'primary'
  subtitleTone?: 'body' | 'label' | 'micro'
}>(), {
  iconName: 'dashboard',
  iconSize: 24,
  titleTone: 'default',
  subtitleTone: 'body',
})
</script>

<style scoped>
.mobile-header {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 12px;
  padding: 24px 16px 8px;
  background: rgba(250, 248, 255, 0.96);
  backdrop-filter: blur(12px);
}

.mobile-header__main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
}

.mobile-header__brand {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.mobile-header__mark {
  flex: 0 0 auto;
  color: var(--mobile-primary, #006D44);
}

.mobile-header h1 {
  margin: 0;
  color: var(--mobile-text, #131b2e);
  font-size: var(--mobile-title-lg, 20px);
  font-weight: 700;
  line-height: var(--mobile-leading-title-lg, 26px);
  letter-spacing: -0.01em;
}

.mobile-header h1.primary {
  color: var(--mobile-primary, #006D44);
}

.mobile-header p {
  margin: 0;
  color: var(--mobile-muted, #52625C);
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.mobile-header p.body {
  font-size: var(--mobile-text-md, 14px);
  line-height: var(--mobile-leading-md, 20px);
  font-weight: 400;
}

.mobile-header p.label {
  font-size: var(--mobile-text-xs, 12px);
  line-height: var(--mobile-leading-label, 16px);
}

.mobile-header p.micro {
  font-size: var(--mobile-text-2xs, 10px);
  line-height: var(--mobile-leading-label-sm, 14px);
  font-weight: 400;
  opacity: 0.7;
}

.mobile-header__filter {
  flex: 0 0 auto;
  min-height: 32px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: 1px solid #d7f0e5;
  border-radius: 999px;
  background: #f2f3ff;
  color: var(--mobile-muted, #52625C);
  font-size: var(--mobile-text-xs, 12px);
  font-weight: 500;
  line-height: var(--mobile-leading-label, 16px);
  white-space: nowrap;
}
</style>
