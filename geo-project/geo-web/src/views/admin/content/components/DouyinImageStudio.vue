<template>
  <el-dialog v-model="visible" title="抖音图文 · 生图测试" width="min(1080px, 94vw)" append-to-body destroy-on-close>
    <p class="studio-note">围绕文章主体生成带文字的图片。先比较效果，选定后再应用到文章；这里不会创建发布任务。</p>
    <el-form inline aria-label="生图设置">
      <el-form-item label="视觉方向">
        <el-select v-model="styleCode" style="width: 220px">
          <el-option label="自动变化 · 避开近期风格" value="auto" />
          <el-option v-for="(label, code) in styles" :key="code" :label="label" :value="code" />
        </el-select>
      </el-form-item>
      <el-form-item label="图片张数"><el-input-number v-model="imageCount" :min="1" :max="6" /></el-form-item>
      <el-form-item><el-button type="primary" :loading="busy" @click="generate">生成一组测试图</el-button></el-form-item>
      <el-form-item><el-button :loading="loading" @click="load">刷新</el-button></el-form-item>
    </el-form>
    <p class="studio-note">9:16 竖图 · 2K级分辨率 · 无水印 · 每组内部保持统一</p>
    <el-alert v-if="loadError" :title="loadError" type="error" :closable="false" />
    <el-empty v-if="!loading && !batches.length && !loadError" description="还没有测试图片。可用同一篇文章比较不同视觉方向。" />
    <section v-for="batch in batches" :key="batch.id" class="image-batch">
      <div class="batch-heading">
        <div><strong>{{ styles[batch.styleCode] || batch.styleCode }}</strong><span>文章 v{{ batch.sourceVersionNo }} · 第 {{ batch.id }} 组</span></div>
        <el-tag :type="batch.status === 'failed' ? 'danger' : batch.status === 'applied' ? 'success' : 'info'">{{ statuses[batch.status] }} · {{ batch.imageUrls.length }}/{{ batch.imageCount }}</el-tag>
        <el-button v-if="batch.status === 'failed'" :disabled="busy || batch.sourceVersionNo !== currentVersionNo" @click="retry(batch)">重试未完成图片</el-button>
        <el-button v-if="batch.status === 'succeeded'" type="primary" plain :disabled="busy || batch.sourceVersionNo !== currentVersionNo" @click="apply(batch)">应用这一组</el-button>
      </div>
      <p v-if="batch.sourceVersionNo !== currentVersionNo && batch.status !== 'applied'" class="studio-note">文章已更新，这组图片仍可预览，请基于最新版本重新生成后再应用。</p>
      <el-alert v-if="batch.errorMessage" :title="batch.errorMessage" type="error" :closable="false" />
      <div v-if="batch.imageUrls.length" class="image-grid">
        <figure v-for="(url, index) in batch.imageUrls" :key="url">
          <el-image :src="url" :alt="batch.plan?.pages[index]?.title || `图文第${index + 1}页`" :preview-src-list="batch.imageUrls" :initial-index="index" fit="contain" lazy preview-teleported />
          <figcaption>{{ index === 0 ? '封面' : `第 ${index + 1} 页` }} · {{ batch.plan?.pages[index]?.title }}</figcaption>
        </figure>
      </div>
      <el-collapse v-if="batch.plan">
        <el-collapse-item title="查看分图文案与视觉方案" :name="batch.id">
          <p>{{ batch.plan.visualDirection }}</p>
          <div v-for="(page, index) in batch.plan.pages" :key="index" class="page-plan">
            <strong>{{ index + 1 }}. {{ page.title }}</strong>
            <p v-if="page.subtitle">{{ page.subtitle }}</p><p>{{ page.text }}</p>
            <small>画面：{{ page.visual }}</small>
          </div>
        </el-collapse-item>
      </el-collapse>
    </section>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { applyDouyinImages, generateDouyinImages, listDouyinImages, retryDouyinImages, type DouyinImageBatch } from '@/api/douyinImages'

const props = defineProps<{ modelValue: boolean; articleId: number; currentVersionNo: number }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; applied: [] }>()
const visible = computed({ get: () => props.modelValue, set: (value: boolean) => emit('update:modelValue', value) })
const batches = ref<DouyinImageBatch[]>([])
const imageCount = ref(4)
const styleCode = ref('auto')
const busy = ref(false)
const loading = ref(false)
const loadError = ref('')
const styles: Record<string, string> = {
  editorial: '国际编辑杂志', photographic: '商业场景摄影', material_3d: '高精度立体材质',
  minimal: '静奢极简', illustrated: '现代编辑插画', paper_collage: '艺术纸艺拼贴',
  swiss_grid: '瑞士网格', luxury_catalog: '高端品牌画册', architectural: '建筑空间构成',
  data_story: '高级数据叙事', oriental: '现代东方美学', geometric: '现代几何构成',
}
const statuses: Record<DouyinImageBatch['status'], string> = { queued: '等待生成', running: '生成中', succeeded: '待选用', failed: '生成失败', applied: '已应用' }
let requestKey: string | undefined
let context = 0

async function load() {
  const requestContext = context
  loading.value = true
  try {
    const response = await listDouyinImages(props.articleId)
    if (requestContext !== context) return
    batches.value = response.data.data
    loadError.value = ''
  } catch (error) {
    if (requestContext === context) loadError.value = error instanceof Error ? error.message : '图片任务读取失败，请刷新重试'
  } finally { if (requestContext === context) loading.value = false }
}

async function generate() {
  if (busy.value) return
  busy.value = true
  // Retain the key after a network failure so a repeated click cannot create a duplicate paid job.
  requestKey ??= crypto.randomUUID()
  try {
    await generateDouyinImages(props.articleId, { requestKey, imageCount: imageCount.value, styleCode: styleCode.value })
    requestKey = undefined
    ElMessage.success('生图任务已创建，请点击刷新查看生成结果')
    await load()
  } catch { /* The shared request handler displays the error. */ }
  finally { busy.value = false }
}

async function retry(batch: DouyinImageBatch) {
  busy.value = true
  try { await retryDouyinImages(props.articleId, batch.id); await load() }
  catch { /* Shared request handler. */ }
  finally { busy.value = false }
}

async function apply(batch: DouyinImageBatch) {
  busy.value = true
  try {
    await applyDouyinImages(props.articleId, batch.id)
    ElMessage.success('已保存新的文章版本，并按顺序应用整组图片。未创建发布任务。')
    emit('applied')
    await load()
  } catch { /* Shared request handler. */ }
  finally { busy.value = false }
}

watch(() => [props.modelValue, props.articleId] as const, ([open]) => {
  context++
  batches.value = []
  requestKey = undefined
  if (open) void load()
}, { immediate: true })
onBeforeUnmount(() => { context++ })
</script>

<style scoped>
.studio-note { color: var(--el-text-color-secondary); line-height: 1.7; margin: 0 0 18px; }
.image-batch { border-top: 1px solid var(--el-border-color-lighter); padding: 22px 0; }
.batch-heading { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; margin-bottom: 16px; }
.batch-heading > div { flex: 1; min-width: 180px; }
.batch-heading span { display: block; font-size: 12px; color: var(--el-text-color-secondary); margin-top: 5px; }
.image-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 16px; }
figure { margin: 0; }
figure .el-image { display: block; width: 100%; aspect-ratio: 9 / 16; background: var(--el-fill-color-light); border-radius: 8px; }
figcaption { margin: 10px 0 16px; color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.6; }
.page-plan { margin: 16px 0; white-space: pre-wrap; }
</style>
