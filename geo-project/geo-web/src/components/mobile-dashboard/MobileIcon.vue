<template>
  <span class="mobile-icon material-symbols-outlined" aria-hidden="true">
    {{ symbolName }}
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  name: string
}>()

const symbols: Record<string, string> = {
  dashboard: 'analytics',
  home: 'home',
  monitor: 'query_stats',
  content: 'content_copy',
  report: 'bar_chart',
  calendar: 'calendar_today',
  chevronDown: 'expand_more',
  chevronRight: 'chevron_right',
  chevronLeft: 'chevron_left',
  shield: 'shield',
  at: 'alternate_email',
  star: 'star',
  bars: 'bar_chart',
  check: 'check_circle',
  document: 'description',
  inbox: 'inventory_2',
  tools: 'construction',
  cluster: 'account_tree',
  search: 'search',
  grid: 'grid_view',
  eye: 'visibility',
  plus: 'add',
  bookmark: 'bookmark',
  clock: 'schedule',
  question: 'contact_support',
  tag: 'sell',
  mapPin: 'location_on',
  cart: 'shopping_cart',
  layers: 'layers',
  info: 'info',
  chat: 'chat_bubble',
  globe: 'public',
  movie: 'movie',
  favorite: 'favorite',
  newspaper: 'newspaper',
  article: 'article',

  // Legacy Vant-like names used by early mobile dashboard code.
  'description-o': 'description',
  upgrade: 'publish',
  'bookmark-o': 'inventory_2',
  'setting-o': 'construction',
  'orders-o': 'article',
  'eye-o': 'visibility',
  passed: 'check_circle',
}

const symbolName = computed(() => symbols[props.name] || props.name || 'analytics')
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:wght,FILL@300..700,0..1&display=swap');

.mobile-icon {
  width: 1em;
  height: 1em;
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  font-family: 'Material Symbols Outlined';
  font-size: 1em;
  font-style: normal;
  font-weight: normal;
  line-height: 1;
  letter-spacing: normal;
  text-transform: none;
  white-space: nowrap;
  direction: ltr;
  -webkit-font-feature-settings: 'liga';
  -webkit-font-smoothing: antialiased;
  font-feature-settings: 'liga';
  font-variation-settings: 'FILL' 0, 'wght' 500, 'GRAD' 0, 'opsz' 24;
}
</style>
