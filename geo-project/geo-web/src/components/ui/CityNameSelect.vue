<template>
  <el-popover
    v-model:visible="popoverVisible"
    trigger="click"
    placement="bottom-start"
    :width="320"
  >
    <template #reference>
      <el-input
        :model-value="displayText"
        readonly
        clearable
        :placeholder="placeholder"
        @clear="clearValue"
      />
    </template>

    <div class="city-panel">
      <el-cascader-panel
        :model-value="draftValue"
        :options="cityOptions"
        :props="cascaderProps"
        @update:model-value="onDraftChange"
      />
      <div class="city-footer">
        <el-button size="small" @click="cancelSelect">取消</el-button>
        <el-button size="small" type="primary" @click="confirmSelect">确认</el-button>
      </div>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { chinaRegionOptions, regionPayloadFromCodes, type RegionOption } from '@/constants/region'

const props = withDefaults(defineProps<{
  modelValue?: string | null
  placeholder?: string
}>(), {
  modelValue: '',
  placeholder: '请选择城市',
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

const MUNICIPALITIES = new Set(['北京', '上海', '天津', '重庆'])

const cityOptions = computed<RegionOption[]>(() => chinaRegionOptions.map((province) => {
  const provinceLabel = normalizeCityName(province.label)
  if (MUNICIPALITIES.has(provinceLabel)) {
    return {
      value: province.value,
      label: provinceLabel,
      children: [{
        value: province.value,
        label: provinceLabel,
      }],
    }
  }
  return {
    value: province.value,
    label: province.label,
    children: province.children?.map((city) => ({
      value: city.value,
      label: normalizeCityName(city.label),
    })),
  }
}))

const cascaderProps = {
  emitPath: true,
  checkStrictly: false,
  value: 'value',
  label: 'label',
}

const popoverVisible = ref(false)
const draftValue = ref<string[]>([])

const displayText = computed(() => normalizeCityName(props.modelValue || ''))

watch(
  () => props.modelValue,
  (value) => {
    const normalized = normalizeCityName(value || '')
    draftValue.value = findCityCodes(normalized)
    if (value && normalized && normalized !== value) {
      emit('update:modelValue', normalized)
    }
  },
  { immediate: true },
)

function onDraftChange(value: string[] | undefined) {
  draftValue.value = value || []
  if (draftValue.value.length >= 2) {
    emitSelectedCity()
    popoverVisible.value = false
  }
}

function cancelSelect() {
  draftValue.value = findCityCodes(props.modelValue || '')
  popoverVisible.value = false
}

function confirmSelect() {
  emitSelectedCity()
  popoverVisible.value = false
}

function clearValue() {
  draftValue.value = []
  emit('update:modelValue', '')
}

function emitSelectedCity() {
  const payload = regionPayloadFromCodes(draftValue.value)
  emit('update:modelValue', normalizeCityName(payload.cityName || payload.provinceName || ''))
}

function findCityCodes(value: string): string[] {
  const normalized = normalizeCityName(value)
  if (!normalized) return []
  for (const province of cityOptions.value) {
    for (const city of province.children || []) {
      if (normalizeCityName(city.label) === normalized) {
        return [province.value, city.value]
      }
    }
  }
  return []
}

function normalizeCityName(value: string): string {
  const raw = String(value || '').trim()
  if (!raw) return ''
  const compact = raw
    .split(/[\/,，、;；|｜\s]+/u)
    .find((item) => item && item.length >= 2) || raw
  const withoutProvince = compact
    .replace(/^(北京|上海|天津|重庆)市/u, '$1')
    .replace(/^(河北|山西|辽宁|吉林|黑龙江|江苏|浙江|安徽|福建|江西|山东|河南|湖北|湖南|广东|海南|四川|贵州|云南|陕西|甘肃|青海|台湾)省?/u, '')
    .replace(/^(内蒙古|广西壮族|西藏|宁夏回族|新疆维吾尔)(自治区)?/u, '')
    .replace(/^(香港|澳门)特别行政区/u, '$1')
  const cityMatch = withoutProvince.match(/([\u4e00-\u9fa5]{2,12}?市)/u)
  return (cityMatch?.[1] || withoutProvince).replace(/[省市县区]$/u, '').trim()
}
</script>

<style scoped>
.city-panel {
  width: 100%;
}

.city-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}
</style>
