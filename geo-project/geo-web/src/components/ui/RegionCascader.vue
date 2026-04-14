<template>
  <el-popover
    v-model:visible="popoverVisible"
    trigger="click"
    placement="bottom-start"
    :width="360"
  >
    <template #reference>
      <el-input
        :model-value="displayText"
        readonly
        clearable
        placeholder="请选择省/市/县"
        @clear="clearValue"
      />
    </template>

    <div class="region-panel">
      <el-cascader-panel
        :model-value="draftValue"
        :options="chinaRegionOptions"
        :props="cascaderProps"
        @update:model-value="onDraftChange"
      />
      <div class="region-footer">
        <el-button size="small" @click="cancelSelect">取消</el-button>
        <el-button size="small" type="primary" @click="confirmSelect">确认</el-button>
      </div>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { chinaRegionOptions, regionDisplayFromCodes } from '@/constants/region'

const props = defineProps<{
  modelValue: string[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string[]): void
}>()

const popoverVisible = ref(false)
const draftValue = ref<string[]>([])

const cascaderProps = {
  emitPath: true,
  checkStrictly: true,
  value: 'value',
  label: 'label',
}

watch(
  () => props.modelValue,
  (value) => {
    draftValue.value = [...(value || [])]
  },
  { immediate: true },
)

const displayText = computed(() => regionDisplayFromCodes(props.modelValue || []))

function onDraftChange(value: string[] | undefined) {
  draftValue.value = value || []
  if (draftValue.value.length >= 3) {
    emit('update:modelValue', [...draftValue.value])
    popoverVisible.value = false
  }
}

function cancelSelect() {
  draftValue.value = [...(props.modelValue || [])]
  popoverVisible.value = false
}

function confirmSelect() {
  emit('update:modelValue', [...draftValue.value])
  popoverVisible.value = false
}

function clearValue() {
  draftValue.value = []
  emit('update:modelValue', [])
}
</script>

<style scoped>
.region-panel {
  width: 100%;
}

.region-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}
</style>
