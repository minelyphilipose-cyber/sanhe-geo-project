<template>
  <router-view />
  <SiteFilingFooter v-if="showFilingFooter" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import SiteFilingFooter from '@/components/common/SiteFilingFooter.vue'

const route = useRoute()

const hideFilingPathPrefixes = ['/presale-print/', '/presale-print-poc/']

const showFilingFooter = computed(() => !hideFilingPathPrefixes.some((prefix) => route.path.startsWith(prefix)))
</script>
