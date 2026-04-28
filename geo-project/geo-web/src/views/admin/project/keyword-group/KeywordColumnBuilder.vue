<template>
  <div class="columns-wrapper">
    <KeywordWordColumn
      v-if="typeConfig.columns.area"
      :step="columnStep('area')"
      title="地区词"
      mode="text"
      :text="form.areaText"
      :enabled="form.areaEnabled"
      :show-area-toggle="true"
      placeholder="推荐词如：&#10;北京&#10;上海&#10;深圳&#10;南京"
      @update:text="form.areaText = $event"
      @update:enabled="form.areaEnabled = $event"
    />
    <KeywordWordColumn
      v-if="typeConfig.columns.prefix"
      :step="columnStep('prefix')"
      title="前缀词"
      :required="typeConfig.requiredColumns.prefix"
      :options="options.prefixWords"
      :selected-words="form.prefixSystemWords"
      :custom-text="form.prefixCustomText"
      custom-placeholder="请输入自定义前缀词"
      @update:selected-words="form.prefixSystemWords = $event"
      @update:custom-text="form.prefixCustomText = $event"
    />
    <KeywordWordColumn
      v-if="typeConfig.columns.core"
      :step="columnStep('core')"
      title="核心词"
      mode="text"
      :required="typeConfig.requiredColumns.core"
      :text="form.coreText"
      placeholder="请填写核心词，每行一个"
      @update:text="form.coreText = $event"
    />
    <KeywordWordColumn
      v-if="typeConfig.columns.industry"
      :step="columnStep('industry')"
      title="行业词"
      :required="typeConfig.requiredColumns.industry"
      :options="industryOptionsWithSelected"
      :selected-words="form.industryWords"
      @update:selected-words="form.industryWords = $event"
    />
    <KeywordWordColumn
      v-if="typeConfig.columns.suffix"
      :step="columnStep('suffix')"
      title="后缀词"
      :required="typeConfig.requiredColumns.suffix"
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
import { computed } from 'vue'
import type { KeywordAffixWord, KeywordAffixWordOptionResult, KeywordTypeConfig } from '@/types'
import KeywordWordColumn from './KeywordWordColumn.vue'
import type { KeywordGroupFormState } from './types'

const props = defineProps<{
  typeConfig: KeywordTypeConfig
  options: KeywordAffixWordOptionResult
  form: KeywordGroupFormState
}>()

type StandardColumnKey = 'area' | 'prefix' | 'core' | 'industry' | 'suffix'

function columnStep(key: StandardColumnKey) {
  const order: StandardColumnKey[] = ['area', 'prefix', 'core', 'industry', 'suffix']
  return order.filter((item) => props.typeConfig.columns[item]).indexOf(key) + 1
}

const industryOptionsWithSelected = computed(() => {
  const options = [...props.options.industryWords]
  const existed = new Set(options.map((item) => item.wordText))
  for (const wordText of props.form.industryWords) {
    if (existed.has(wordText)) {
      continue
    }
    options.push({
      id: -options.length - 1,
      type: props.form.type,
      affixKind: 'industry',
      wordText,
      sortOrder: 9999,
      enabled: true,
    } satisfies KeywordAffixWord)
  }
  return options
})
</script>

<style scoped>
.columns-wrapper {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
}
</style>
