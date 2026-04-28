<template>
  <div class="keyword-type-selector">
    <button
      v-for="item in options"
      :key="item.value"
      type="button"
      class="type-option"
      :class="{ active: modelValue === item.value, legacy: item.legacy }"
      @click="emitUserChange(item.value)"
    >
      <span class="radio-dot"></span>
      <span class="type-text">
        <span class="type-label">{{ item.label }}</span>
        <span v-if="item.legacy" class="legacy-hint">历史</span>
      </span>
    </button>
  </div>
</template>

<script setup lang="ts">
import type { KeywordTypeOption } from '@/types'

defineProps<{
  modelValue: string
  options: KeywordTypeOption[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'change-by-user', value: string): void
}>()

function emitUserChange(value: string) {
  emit('change-by-user', value)
}
</script>

<style scoped>
.keyword-type-selector {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.type-option {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 36px;
  border: 1px solid #d8dfeb;
  background: #fff;
  color: #334155;
  border-radius: 6px;
  padding: 7px 10px;
  cursor: pointer;
}

.type-option.active {
  border-color: #4361ee;
  background: #eef1ff;
  color: #1e3a8a;
}

.type-option.legacy {
  color: #64748b;
  background: #f8fafc;
}

.radio-dot {
  width: 14px;
  height: 14px;
  border: 2px solid #cbd5e1;
  border-radius: 50%;
  position: relative;
  flex: 0 0 auto;
}

.type-option.active .radio-dot {
  border-color: #4361ee;
}

.type-option.active .radio-dot::after {
  content: '';
  position: absolute;
  inset: 3px;
  border-radius: 50%;
  background: #4361ee;
}

.type-text {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}

.type-label {
  font-size: 13px;
  font-weight: 500;
}

.legacy-hint {
  font-size: 11px;
  color: #64748b;
  background: #e2e8f0;
  border-radius: 3px;
  padding: 1px 4px;
}
</style>
