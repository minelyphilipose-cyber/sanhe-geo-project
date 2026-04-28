<template>
  <div class="columns-wrapper compare-columns">
    <KeywordWordColumn
      :step="1"
      title="核心词 A"
      mode="text"
      required
      :text="form.coreTextA"
      placeholder="请填写核心词 A，每行一个"
      @update:text="form.coreTextA = $event"
    />
    <KeywordWordColumn
      :step="2"
      title="对比词"
      required
      :options="options.compareWords"
      :selected-words="form.compareWords"
      @update:selected-words="form.compareWords = $event"
    />
    <KeywordWordColumn
      :step="3"
      title="核心词 B"
      mode="text"
      required
      :text="form.coreTextB"
      placeholder="请填写核心词 B，每行一个"
      @update:text="form.coreTextB = $event"
    />
    <KeywordWordColumn
      :step="4"
      title="后缀词"
      required
      :options="options.suffixWords"
      :selected-words="form.suffixSystemWords"
      :custom-text="form.suffixCustomText"
      custom-placeholder="请输入自定义后缀词"
      @update:selected-words="form.suffixSystemWords = $event"
      @update:custom-text="form.suffixCustomText = $event"
    />
  </div>
</template>

<script setup lang="ts">
import type { KeywordAffixWordOptionResult } from '@/types'
import KeywordWordColumn from './KeywordWordColumn.vue'
import type { KeywordGroupFormState } from './types'

defineProps<{
  options: KeywordAffixWordOptionResult
  form: KeywordGroupFormState
}>()
</script>

<style scoped>
.columns-wrapper {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

@media (max-width: 960px) {
  .columns-wrapper {
    grid-template-columns: 1fr;
  }
}
</style>
