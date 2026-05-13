<template>
  <div class="llm-panel">
    <div class="llm-input-row">
      <div class="llm-input-main">
        <label class="form-label">AI 问题扩写</label>
        <el-input
          :model-value="seedText"
          maxlength="10"
          show-word-limit
          placeholder="输入 1 个关键词或短句(≤10 字),如:合肥小吃"
          @update:model-value="emit('update:seedText', String($event))"
        />
      </div>
      <div class="llm-target-count">
        <label class="form-label">生成条数</label>
        <el-input-number
          v-model="targetCount"
          :min="LLM_TARGET_MIN"
          :max="targetInputMax"
          :step="5"
          :disabled="disabled || !canGenerateMore"
          controls-position="right"
          style="width: 110px"
        />
        <div v-if="exceedHint" class="target-count-hint">{{ exceedHint }}</div>
      </div>
      <div class="llm-actions">
        <el-button type="primary" :loading="generating" :disabled="disabled || !seedText.trim() || !canGenerateMore" @click="handleGenerate">
          {{ questions.length ? `继续扩写 ${targetCount} 条` : `生成 ${targetCount} 条` }}
        </el-button>
        <el-button v-if="questions.length" :disabled="disabled" @click="clearAll">全部删除</el-button>
      </div>
    </div>

    <el-collapse-transition>
      <div v-if="questions.length" class="llm-question-form">
        <div class="llm-question-head">
          <span>AI 生成问题</span>
          <strong>{{ questions.length }}</strong>
          <el-button
            v-if="groupedQuestions.length > 1"
            link
            size="small"
            @click="toggleAll"
          >
            {{ allExpanded ? '全部收起' : '全部展开' }}
          </el-button>
        </div>
        <div class="llm-batch-list">
          <div v-for="group in groupedQuestions" :key="group.seedText" class="llm-batch">
            <div class="llm-batch-head" @click="toggleSeed(group.seedText)">
              <span class="batch-arrow">{{ expandedSeeds.has(group.seedText) ? '▼' : '▶' }}</span>
              <span class="batch-title">{{ group.seedText || '未标记种子词' }}</span>
              <span class="batch-count">({{ group.items.length }})</span>
              <el-button link type="danger" :disabled="disabled" @click.stop="removeBatch(group.seedText)">删除整批</el-button>
            </div>
            <el-collapse-transition>
              <div v-show="expandedSeeds.has(group.seedText)" class="llm-question-list">
                <div v-for="item in group.items" :key="`${item.seedText}_${item.questionText}`" class="llm-question-item">
                  <span>{{ item.questionText }}</span>
                  <el-button link type="danger" :disabled="disabled" @click="removeOne(item)">删除</el-button>
                </div>
              </div>
            </el-collapse-transition>
          </div>
        </div>
      </div>
    </el-collapse-transition>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { generateKeywordGroupLlmQuestions } from '@/api/project'
import { ERROR_CODE_HINTS, parseErrorCode } from '@/utils/errorCode'
import type { LlmQuestionItem } from '@/types'

const props = defineProps<{
  companyId: number | null
  projectId?: number | null
  seedText: string
  questions: LlmQuestionItem[]
  generationToken: string
  previewCount: number
  quotaCount?: number
  disabled?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:seedText', value: string): void
  (e: 'update:questions', value: LlmQuestionItem[]): void
  (e: 'update:generationToken', value: string): void
}>()

const generating = ref(false)
const targetCount = ref(30)
const expandedSeeds = ref<Set<string>>(new Set())
const LLM_TARGET_MIN = 5
const LLM_TARGET_MAX = 50
const groupedQuestions = computed(() => {
  const map = new Map<string, LlmQuestionItem[]>()
  for (const item of props.questions) {
    const seedText = item.seedText || ''
    if (!map.has(seedText)) {
      map.set(seedText, [])
    }
    map.get(seedText)!.push(item)
  }
  return Array.from(map.entries()).map(([seedText, items]) => ({ seedText, items }))
})
const allExpanded = computed(() => (
  groupedQuestions.value.length > 0
  && groupedQuestions.value.every((group) => expandedSeeds.value.has(group.seedText))
))
const exceedHint = computed(() => {
  const after = props.questions.length + targetCount.value
  if (hasProjectQuotaLimit.value && props.quotaCount! <= 0) {
    return '当前项目未配置问题额度'
  }
  if (remainingCapacity.value <= 0) {
    return '已达到预览总数或项目额度'
  }
  if (remainingCapacity.value < LLM_TARGET_MIN) {
    return `剩余可生成 ${remainingCapacity.value} 条，单次至少 ${LLM_TARGET_MIN} 条`
  }
  if (after > props.previewCount) {
    return `累积将达 ${after} 条,超过预览总数`
  }
  if (props.quotaCount && after > props.quotaCount) {
    return `累积将达 ${after} 条,超过项目额度`
  }
  return ''
})
const remainingCapacity = computed(() => {
  const previewRemaining = Math.max(props.previewCount - props.questions.length, 0)
  const quotaRemaining = hasProjectQuotaLimit.value
    ? Math.max((props.quotaCount ?? 0) - props.questions.length, 0)
    : previewRemaining
  return Math.min(previewRemaining, quotaRemaining)
})
const hasProjectQuotaLimit = computed(() => Boolean(props.projectId) && props.quotaCount !== undefined)
const targetMax = computed(() => {
  return Math.min(LLM_TARGET_MAX, remainingCapacity.value)
})
const targetInputMax = computed(() => Math.max(LLM_TARGET_MIN, targetMax.value))
const canGenerateMore = computed(() => {
  if (hasProjectQuotaLimit.value && props.quotaCount! <= 0) {
    return false
  }
  return targetMax.value >= LLM_TARGET_MIN
})

watch(targetMax, (max) => {
  if (max >= LLM_TARGET_MIN && targetCount.value > max) {
    targetCount.value = max
  } else if (max >= LLM_TARGET_MIN && targetCount.value < LLM_TARGET_MIN) {
    targetCount.value = LLM_TARGET_MIN
  }
}, { immediate: true })

watch(() => groupedQuestions.value.length, (count) => {
  if (count <= 1) {
    expandedSeeds.value = new Set(groupedQuestions.value.map((group) => group.seedText))
  }
}, { immediate: true })

async function handleGenerate() {
  if (!props.projectId && !props.companyId) {
    ElMessage.warning('请选择项目')
    return
  }
  const seed = props.seedText.trim()
  if (!seed) {
    ElMessage.warning('请输入种子词')
    return
  }
  if (seed.length > 10) {
    ElMessage.warning('种子词长度不能超过 10 字')
    return
  }
  if (hasProjectQuotaLimit.value && props.quotaCount! <= 0) {
    ElMessage.warning('当前项目未配置问题额度')
    return
  }
  if (!Number.isInteger(targetCount.value) || targetCount.value < LLM_TARGET_MIN || targetCount.value > LLM_TARGET_MAX) {
    ElMessage.warning(`单次生成数量必须在 ${LLM_TARGET_MIN}-${LLM_TARGET_MAX} 条之间`)
    return
  }
  if (targetCount.value > targetMax.value) {
    if (remainingCapacity.value < LLM_TARGET_MIN) {
      ElMessage.warning(`剩余可生成 ${remainingCapacity.value} 条，单次至少 ${LLM_TARGET_MIN} 条`)
      return
    }
    ElMessage.warning(`本次最多可生成 ${targetMax.value} 条`)
    return
  }
  if (props.questions.length + targetCount.value > props.previewCount) {
    ElMessage.warning(`生成 ${targetCount.value} 条后累积将达 ${props.questions.length + targetCount.value} 条,超过预览总数 ${props.previewCount}`)
    return
  }
  if (props.quotaCount && props.questions.length + targetCount.value > props.quotaCount) {
    ElMessage.warning(`生成 ${targetCount.value} 条后累积将达 ${props.questions.length + targetCount.value} 条,超过项目额度 ${props.quotaCount}`)
    return
  }
  generating.value = true
  try {
    const { data } = await generateKeywordGroupLlmQuestions({
      companyId: props.companyId || undefined,
      projectId: props.projectId || undefined,
      seedText: seed,
      currentToken: props.generationToken || undefined,
      count: props.previewCount,
      currentLlmCount: props.questions.length,
      targetCount: targetCount.value,
    })
    const batchSeed = data.data.seedText || seed
    const next = dedupQuestions([
      ...props.questions,
      ...(data.data.newQuestions || []).map((questionText) => ({ questionText, seedText: batchSeed })),
    ])
    emit('update:questions', next)
    emit('update:generationToken', data.data.generationToken || '')
    emit('update:seedText', '')
    expandSeed(batchSeed)
    ElMessage.success(`已新增 ${data.data.newQuestions?.length || 0} 条问题`)
  } catch (error: any) {
    const payload = error?.response?.data || error
    const parsed = parseErrorCode({ errorCode: payload?.errorCode, message: payload?.message })
    ElMessage.error(parsed.code ? ERROR_CODE_HINTS[parsed.code] : (parsed.text || 'AI 扩写失败'))
  } finally {
    generating.value = false
  }
}

function removeOne(item: LlmQuestionItem) {
  emit('update:questions', props.questions.filter((q) => !(q.questionText === item.questionText && q.seedText === item.seedText)))
}

function removeBatch(seedText: string) {
  emit('update:questions', props.questions.filter((q) => (q.seedText || '') !== seedText))
}

function toggleSeed(seedText: string) {
  const next = new Set(expandedSeeds.value)
  if (next.has(seedText)) {
    next.delete(seedText)
  } else {
    next.add(seedText)
  }
  expandedSeeds.value = next
}

function expandSeed(seedText: string) {
  const next = new Set(expandedSeeds.value)
  next.add(seedText)
  expandedSeeds.value = next
}

function toggleAll() {
  if (allExpanded.value) {
    expandedSeeds.value = new Set()
  } else {
    expandedSeeds.value = new Set(groupedQuestions.value.map((group) => group.seedText))
  }
}

async function clearAll() {
  try {
    await ElMessageBox.confirm('确认删除全部 AI 生成问题？', '删除确认', { type: 'warning' })
    emit('update:questions', [])
  } catch {
    // canceled
  }
}

function dedupQuestions(items: LlmQuestionItem[]) {
  const map = new Map<string, LlmQuestionItem>()
  for (const item of items) {
    const questionText = item.questionText.trim()
    if (!questionText || map.has(questionText)) {
      continue
    }
    map.set(questionText, { questionText, seedText: item.seedText })
  }
  return Array.from(map.values())
}
</script>

<style scoped>
.llm-panel {
  margin: 16px 0;
  padding: 14px;
  background: #fff;
  border: 1px solid #e8ecf1;
  border-radius: 8px;
}

.llm-input-row {
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.llm-input-main {
  flex: 1;
  min-width: 0;
}

.llm-target-count {
  width: 120px;
}

.target-count-hint {
  margin-top: 4px;
  color: #dc2626;
  font-size: 12px;
  line-height: 1.3;
}

.form-label {
  display: block;
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 500;
}

.llm-actions {
  display: flex;
  gap: 8px;
  padding-bottom: 1px;
}

.llm-question-form {
  margin-top: 12px;
  border-top: 1px solid #eef2f6;
  padding-top: 12px;
}

.llm-question-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 10px;
  font-size: 13px;
  color: #475569;
}

.llm-batch-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.llm-batch {
  border: 1px solid #dcfce7;
  border-radius: 8px;
  overflow: hidden;
}

.llm-batch-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-height: 38px;
  padding: 0 10px;
  background: #f0fdf4;
  color: #166534;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  user-select: none;
  transition: background 0.2s;
}

.llm-batch-head:hover {
  background: #e6f7ed;
}

.batch-arrow {
  color: #16a34a;
  font-size: 11px;
  transition: transform 0.2s;
}

.batch-title {
  flex: 1;
  margin-left: 6px;
  min-width: 0;
  overflow-wrap: anywhere;
}

.batch-count {
  color: #64748b;
  font-weight: normal;
}

.llm-question-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  padding: 10px;
}

.llm-question-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 36px;
  gap: 8px;
  padding: 7px 10px;
  border: 1px solid #bbf7d0;
  border-radius: 6px;
  background: #f0fdf4;
  color: #166534;
  font-size: 13px;
}

.llm-question-item span {
  min-width: 0;
  overflow-wrap: anywhere;
}

@media (max-width: 760px) {
  .llm-input-row {
    flex-direction: column;
    align-items: stretch;
  }

  .llm-question-list {
    grid-template-columns: 1fr;
  }
}
</style>
