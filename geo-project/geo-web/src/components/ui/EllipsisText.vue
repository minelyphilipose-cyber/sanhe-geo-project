<template>
  <el-tooltip
    :content="text"
    :disabled="!isOverflowing"
    effect="dark"
    placement="top"
  >
    <span ref="textRef" class="ellipsis-text" :class="{ 'is-overflowing': isOverflowing }">
      {{ text }}
    </span>
  </el-tooltip>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps<{
  value?: string | number | string[] | null
}>()

const textRef = ref<HTMLElement>()
const isOverflowing = ref(false)
let resizeObserver: ResizeObserver | null = null

const text = computed(() => {
  if (Array.isArray(props.value)) return props.value.length ? props.value.join('，') : '-'
  if (props.value == null || props.value === '') return '-'
  return String(props.value)
})

function updateOverflow() {
  const el = textRef.value
  if (!el || text.value === '-') {
    isOverflowing.value = false
    return
  }
  isOverflowing.value = el.scrollWidth > el.clientWidth
}

onMounted(() => {
  nextTick(updateOverflow)
  if (textRef.value) {
    resizeObserver = new ResizeObserver(updateOverflow)
    resizeObserver.observe(textRef.value)
  }
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
})

watch(text, () => nextTick(updateOverflow))
</script>

<style scoped>
.ellipsis-text {
  display: block;
  max-width: 100%;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ellipsis-text.is-overflowing {
  cursor: help;
}
</style>
