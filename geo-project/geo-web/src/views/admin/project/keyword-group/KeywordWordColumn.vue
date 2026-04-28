<template>
  <div class="word-column">
    <div class="column-header">
      <div class="column-step">
        <span class="step-number">{{ step }}</span>
        <span class="step-label">{{ title }}<span v-if="required" class="required-star">*</span></span>
      </div>
      <el-switch
        v-if="showAreaToggle"
        :model-value="enabled"
        size="small"
        active-text="启用"
        @update:model-value="emit('update:enabled', Boolean($event))"
      />
    </div>

    <div class="column-body">
      <template v-if="mode === 'text'">
        <textarea
          :value="text"
          class="core-textarea"
          :rows="rows"
          :placeholder="placeholder"
          @input="emit('update:text', ($event.target as HTMLTextAreaElement).value)"
        ></textarea>
      </template>

      <template v-else>
        <div v-if="hint" class="empty-hint">{{ hint }}</div>
        <template v-else>
          <div v-for="group in groupedOptions" :key="group.name" class="option-group">
            <div class="section-hint">{{ group.name }} ({{ group.items.length }})</div>
            <label
              v-for="item in group.items"
              :key="item.id ?? `${item.wordText}_${item.sortOrder}`"
              class="word-item"
              :class="{ checked: selectedWords.includes(item.wordText), 'word-item-temporary': item.isTemporary }"
            >
              <input
                :checked="selectedWords.includes(item.wordText)"
                type="checkbox"
                class="word-checkbox"
                @change="toggleWord(item.wordText, ($event.target as HTMLInputElement).checked)"
              />
              <span class="checkmark"></span>
              <span class="word-text">{{ item.wordText }}</span>
              <span v-if="item.visualTag" class="badge-tag">{{ item.visualTag }}</span>
              <span v-if="item.isTemporary" class="badge-temp" :title="temporaryTitle(item)">临时</span>
            </label>
          </div>
          <div v-if="!groupedOptions.length" class="empty-hint">暂无候选词</div>
        </template>

        <template v-if="customText !== undefined">
          <div class="section-hint mt">自定义词（每行一个）</div>
          <textarea
            :value="customText"
            class="core-textarea"
            rows="4"
            :placeholder="customPlaceholder"
            @input="emit('update:customText', ($event.target as HTMLTextAreaElement).value)"
          ></textarea>
        </template>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { KeywordAffixWord } from '@/types'

const props = withDefaults(defineProps<{
  step: number
  title: string
  required?: boolean
  mode?: 'text' | 'options'
  rows?: number
  placeholder?: string
  text?: string
  options?: KeywordAffixWord[]
  selectedWords?: string[]
  customText?: string
  customPlaceholder?: string
  showAreaToggle?: boolean
  enabled?: boolean
  hint?: string
}>(), {
  required: false,
  mode: 'options',
  rows: 10,
  placeholder: '',
  text: '',
  options: () => [],
  selectedWords: () => [],
  customPlaceholder: '请输入自定义词',
  showAreaToggle: false,
  enabled: true,
  hint: '',
})

const emit = defineEmits<{
  (e: 'update:text', value: string): void
  (e: 'update:selectedWords', value: string[]): void
  (e: 'update:customText', value: string): void
  (e: 'update:enabled', value: boolean): void
}>()

const groupedOptions = computed(() => {
  const map = new Map<string, KeywordAffixWord[]>()
  for (const item of props.options) {
    const key = item.subCategory || '其他'
    if (!map.has(key)) {
      map.set(key, [])
    }
    map.get(key)!.push(item)
  }
  return Array.from(map.entries()).map(([name, items]) => ({ name, items }))
})

function toggleWord(wordText: string, checked: boolean) {
  const next = new Set(props.selectedWords)
  if (checked) {
    next.add(wordText)
  } else {
    next.delete(wordText)
  }
  emit('update:selectedWords', Array.from(next))
}

function temporaryTitle(item: KeywordAffixWord) {
  if (!item.scopeType) {
    return '临时词'
  }
  return item.scopeId ? `临时词 · ${item.scopeType}:${item.scopeId}` : `临时词 · ${item.scopeType}`
}
</script>

<style scoped>
.word-column {
  border-right: 1px solid #eef2f6;
  min-height: 330px;
}

.word-column:last-child {
  border-right: none;
}

.column-header {
  min-height: 44px;
  padding: 10px 12px;
  border-bottom: 1px solid #eef2f6;
  background: #f8fafc;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.column-step {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.step-number {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #4361ee;
  color: #fff;
  font-size: 11px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
}

.step-label {
  font-size: 13px;
  font-weight: 600;
  color: #1f2937;
}

.required-star {
  color: #ef4444;
  margin-left: 2px;
}

.column-body {
  padding: 12px;
  max-height: 360px;
  overflow: auto;
}

.section-hint {
  font-size: 12px;
  color: #64748b;
  margin: 8px 0 6px;
}

.section-hint:first-child {
  margin-top: 0;
}

.section-hint.mt {
  margin-top: 12px;
}

.core-textarea {
  width: 100%;
  border: 1px dashed #d8dfeb;
  border-radius: 6px;
  padding: 8px 10px;
  resize: vertical;
  font-family: inherit;
  box-sizing: border-box;
  outline: none;
}

.core-textarea:focus {
  border-color: #4361ee;
  box-shadow: 0 0 0 2px rgba(67, 97, 238, 0.08);
}

.word-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 6px;
  cursor: pointer;
  min-height: 30px;
}

.word-item:hover,
.word-item.checked {
  background: #eef1ff;
}

.word-item-temporary {
  background: #fff8e1;
  border-left: 3px solid #ffc107;
}

.word-checkbox {
  display: none;
}

.checkmark {
  width: 14px;
  height: 14px;
  border: 1px solid #cbd5e1;
  border-radius: 3px;
  flex-shrink: 0;
}

.word-item.checked .checkmark {
  background: #4361ee;
  border-color: #4361ee;
}

.word-text {
  font-size: 13px;
  color: #334155;
  overflow-wrap: anywhere;
}

.badge-tag,
.badge-temp {
  font-size: 10px;
  border-radius: 3px;
  padding: 1px 4px;
  margin-left: auto;
}

.badge-tag {
  color: #1d4ed8;
  background: #dbeafe;
}

.badge-temp {
  color: #f57c00;
  background: #fff3e0;
}

.empty-hint {
  color: #94a3b8;
  font-size: 13px;
  padding: 10px 0;
}

@media (max-width: 960px) {
  .word-column {
    border-right: none;
    border-bottom: 1px solid #eef2f6;
  }
}
</style>
