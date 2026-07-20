<template>
  <header class="mobile-header">
    <div class="mobile-header__main">
      <div class="mobile-header__brand">
        <h1 class="mobile-header__title">
          <img class="mobile-header__brand-logo" :src="systemLogo" alt="幻境AI" />
          <span class="mobile-header__suffix">数据看板</span>
        </h1>
      </div>
      <button v-if="filterLabel" class="mobile-header__filter" type="button">
        <MobileIcon name="calendar" />
        <span>{{ filterLabel }}</span>
        <MobileIcon name="chevronDown" />
      </button>
    </div>
    <div class="mobile-header__context">
      <p :class="subtitleTone">{{ brandName }} | {{ pageName }}</p>
      <span v-if="cutoffText" class="mobile-header__cutoff">数据截至&nbsp;&nbsp;{{ cutoffText }}</span>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import systemLogo from '@/assets/brand/fantasy-logo-light.png'
import MobileIcon from './MobileIcon.vue'

const props = withDefaults(defineProps<{
  brandName: string
  pageName: string
  dataUpdatedAt?: string
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

const cutoffText = computed(() => {
  const value = props.dataUpdatedAt?.trim()
  if (!value) return ''
  const normalized = value.replace('T', ' ')
  return normalized.length >= 16 ? normalized.slice(5, 16) : normalized
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
  min-width: 0;
}

.mobile-header__title {
  margin: 0;
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  line-height: 1;
}

.mobile-header__brand-logo {
  flex: 0 0 auto;
  width: 112px;
  height: 38px;
  display: block;
  object-fit: contain;
  object-position: left center;
}

.mobile-header__suffix {
  flex: 0 1 auto;
  align-self: flex-start;
  margin-top: 4px;
  padding: 5px 9px;
  border-radius: 6px;
  background: #f0f1f3;
  color: #4a4d52;
  font-size: 13px;
  font-weight: 600;
  line-height: 1;
  letter-spacing: 2px;
  white-space: nowrap;
}

.mobile-header__context {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
}

.mobile-header p {
  min-width: 0;
  margin: 0;
  color: var(--mobile-muted, #52625C);
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.mobile-header__cutoff {
  flex: 0 0 auto;
  color: var(--mobile-muted, #52625C);
  font-size: var(--mobile-text-xs, 12px);
  font-weight: 500;
  line-height: var(--mobile-leading-label, 16px);
  white-space: nowrap;
}

@media (max-width: 374px) {
  .mobile-header__context {
    gap: 8px;
  }

  .mobile-header__cutoff {
    font-size: var(--mobile-text-2xs, 10px);
  }
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
