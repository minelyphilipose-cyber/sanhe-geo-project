<template>
  <div v-if="batch || loadError" class="douyin-image-progress" aria-live="polite">
    <el-alert :title="loadError || title" :description="description" :type="alertType" :closable="false" show-icon />
    <el-button size="small" :loading="loading" @click="load">刷新图片状态</el-button>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { listDouyinImages, type DouyinImageBatch } from '@/api/douyinImages'

const props = defineProps<{ articleId: number; currentVersionNo: number; active: boolean }>()
const emit = defineEmits<{ applied: [] }>()
const batch = ref<DouyinImageBatch | null>(null)
const loadError = ref('')
const loading = ref(false)
const labels: Record<DouyinImageBatch['status'], string> = {
  queued: '等待生成', running: '生成中', succeeded: '已生成，待应用',
  failed: '生成失败', applied: '已插入文章', cancelled: '已取消',
}
const title = computed(() => batch.value
  ? `豆包图片${labels[batch.value.status]} · ${batch.value.imageUrls.length}/${batch.value.imageCount} 张` : '')
const description = computed(() => {
  if (loadError.value) return '图片任务可能仍在继续，可重新读取进度。'
  if (batch.value?.status === 'cancelled') return '项目已停用，图片生成已取消。'
  if (batch.value?.errorMessage) return batch.value.errorMessage
  if (batch.value?.status === 'queued' || batch.value?.status === 'running') {
    return '正文已完成，图片正在处理中，可点击“刷新图片状态”查看最新结果。'
  }
  if (batch.value?.status === 'failed') return '可在“生图测试”中重试未完成图片。'
  if (batch.value?.status === 'succeeded') return '图片尚未插入正文，可在“生图测试”中查看并应用。'
  return ''
})
const alertType = computed(() => {
  if (loadError.value || batch.value?.status === 'failed') return 'error'
  if (batch.value?.status === 'applied') return 'success'
  if (batch.value?.status === 'succeeded' && batch.value.errorMessage) return 'warning'
  return 'info'
})
let context = 0
let notifiedVersion = 0

async function load() {
  if (!props.active || loading.value) return
  loading.value = true
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
  } catch {
    if (requestContext === context) loadError.value = '暂时无法读取豆包图片进度'
  } finally { if (requestContext === context) loading.value = false }
}

watch(() => [props.active, props.articleId, props.currentVersionNo] as const, () => {
  context++
  batch.value = null
  loading.value = false
  loadError.value = ''
  notifiedVersion = 0
  if (props.active) void load()
}, { immediate: true })
onBeforeUnmount(() => { context++ })
</script>

<style scoped>
.douyin-image-progress { display: grid; gap: 8px; margin-top: 14px; }
.douyin-image-progress .el-button { justify-self: start; }
</style>
