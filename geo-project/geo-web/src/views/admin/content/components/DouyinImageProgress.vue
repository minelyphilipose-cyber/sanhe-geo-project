<template>
  <div v-if="batch || loadError" class="douyin-image-progress" aria-live="polite">
    <el-alert :title="loadError || title" :description="description" :type="alertType" :closable="false" show-icon />
    <el-button v-if="loadError" size="small" @click="load">重新读取图片进度</el-button>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { listDouyinImages, type DouyinImageBatch } from '@/api/douyinImages'

const props = defineProps<{ articleId: number; currentVersionNo: number; active: boolean }>()
const emit = defineEmits<{ applied: [] }>()
const batch = ref<DouyinImageBatch | null>(null)
const loadError = ref('')
const labels = { queued: '等待生成', running: '生成中', succeeded: '已生成，待应用', failed: '生成失败', applied: '已插入文章' }
const title = computed(() => batch.value
  ? `豆包图片${labels[batch.value.status]} · ${batch.value.imageUrls.length}/${batch.value.imageCount} 张` : '')
const description = computed(() => {
  if (loadError.value) return '图片任务可能仍在继续，可重新读取进度。'
  if (batch.value?.errorMessage) return batch.value.errorMessage
  if (batch.value?.status === 'queued' || batch.value?.status === 'running') {
    return '正文已完成，图片正在处理中，完成后页面会自动刷新。'
  }
  if (batch.value?.status === 'failed') return '可在“生图测试”中重试未完成图片。'
  if (batch.value?.status === 'succeeded') return '图片尚未插入正文，可在“生图测试”中查看并应用。'
  return ''
})
const alertType = computed(() => loadError.value || batch.value?.errorMessage || batch.value?.status === 'failed'
  ? 'error' : batch.value?.status === 'applied' ? 'success' : 'info')
let timer: ReturnType<typeof setTimeout> | undefined
let context = 0
let notifiedVersion = 0

async function load() {
  clearTimeout(timer)
  if (!props.active) return
  const requestContext = context
  try {
    const { data } = await listDouyinImages(props.articleId)
    if (requestContext !== context) return
    loadError.value = ''
    batch.value = data.data.find(item => item.sourceVersionNo === props.currentVersionNo
      || (item.appliedVersionNo ?? 0) >= props.currentVersionNo) ?? null
    const appliedVersion = Math.max(0, ...data.data.map(item => item.status === 'applied' ? item.appliedVersionNo ?? 0 : 0))
    if (appliedVersion > props.currentVersionNo && appliedVersion > notifiedVersion) {
      notifiedVersion = appliedVersion
      emit('applied')
    }
    // Keep watching a completed generation until the worker has also applied it.
    if (batch.value && ['queued', 'running', 'succeeded'].includes(batch.value.status) && !batch.value.errorMessage) {
      timer = setTimeout(load, 8000)
    }
  } catch {
    if (requestContext === context) loadError.value = '暂时无法读取豆包图片进度'
  }
}

watch(() => [props.active, props.articleId, props.currentVersionNo] as const, () => {
  context++
  clearTimeout(timer)
  batch.value = null
  loadError.value = ''
  notifiedVersion = 0
  if (props.active) void load()
}, { immediate: true })
onBeforeUnmount(() => { context++; clearTimeout(timer) })
</script>

<style scoped>
.douyin-image-progress { display: grid; gap: 8px; margin-top: 14px; }
.douyin-image-progress .el-button { justify-self: start; }
</style>
