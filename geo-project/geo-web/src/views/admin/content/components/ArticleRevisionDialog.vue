<template>
  <el-dialog v-model="visible" title="修订文章" width="840px" class="admin-editor-dialog">
    <el-form class="admin-dialog-form content-revision-form" :model="form" label-width="90px">
      <el-form-item class="is-full revision-title-field" label="标题">
        <el-input
          v-model="form.title"
          type="textarea"
          :autosize="{ minRows: 2, maxRows: 4 }"
          maxlength="160"
          show-word-limit
          placeholder="请输入文章标题"
        />
      </el-form-item>
      <el-form-item class="is-full" label="正文" required>
        <div class="editor-wrap">
          <div class="detail-header editor-header">
            <span class="editor-title">内容编辑</span>
            <el-radio-group v-model="viewMode" size="small">
              <el-radio-button label="markdown">Markdown</el-radio-button>
              <el-radio-button label="preview">预览</el-radio-button>
            </el-radio-group>
          </div>
          <el-input v-if="viewMode === 'markdown'" v-model="form.contentMarkdown" type="textarea" :rows="14" />
          <div v-else class="markdown-preview editor-preview" v-html="html"></div>
        </div>
      </el-form-item>
      <el-form-item class="is-full" label="备注">
        <el-input v-model="form.note" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="emit('submit')">保存修订</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'

type ViewMode = 'preview' | 'markdown'

const props = defineProps<{
  modelValue: boolean
  viewMode: ViewMode
  form: {
    title: string
    contentMarkdown: string
    note: string
  }
  html: string
  submitting: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'update:viewMode': [value: ViewMode]
  submit: []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const viewMode = computed({
  get: () => props.viewMode,
  set: (value) => emit('update:viewMode', value),
})
</script>

<style scoped>
.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.markdown-preview {
  min-height: 360px;
  margin: 16px;
  padding: 22px;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background:
    linear-gradient(180deg, #ffffff 0%, #ffffff 74%, #f8fafc 100%);
  overflow: auto;
  line-height: 1.75;
  color: var(--el-text-color-primary);
}

.markdown-preview :deep(h1),
.markdown-preview :deep(h2),
.markdown-preview :deep(h3),
.markdown-preview :deep(h4) {
  margin: 1.1em 0 0.6em;
  font-weight: 700;
  line-height: 1.35;
}

.markdown-preview :deep(p),
.markdown-preview :deep(ul),
.markdown-preview :deep(ol),
.markdown-preview :deep(blockquote) {
  margin: 0 0 0.9em;
}

.markdown-preview :deep(ul),
.markdown-preview :deep(ol) {
  padding-left: 1.4em;
}

.markdown-preview :deep(code) {
  padding: 0.15em 0.4em;
  border-radius: 4px;
  background: #f5f7fa;
  font-size: 0.92em;
}

.markdown-preview :deep(pre) {
  padding: 12px 14px;
  border-radius: 8px;
  background: #0f172a;
  color: #e2e8f0;
  overflow: auto;
}

.markdown-preview :deep(pre code) {
  padding: 0;
  background: transparent;
  color: inherit;
}

.markdown-preview :deep(blockquote) {
  margin-left: 0;
  padding-left: 12px;
  border-left: 4px solid #cbd5e1;
  color: #475569;
}

.markdown-preview :deep(img) {
  display: block;
  max-width: 100%;
  height: auto;
  margin: 14px auto;
  border-radius: 6px;
}

.markdown-preview :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin-bottom: 1em;
}

.markdown-preview :deep(th),
.markdown-preview :deep(td) {
  padding: 8px 10px;
  border: 1px solid #e5e7eb;
  text-align: left;
}

.editor-wrap {
  width: 100%;
  padding: 12px;
  border: 1px solid #dbeafe;
  border-radius: 14px;
  background: #f8fbff;
}

.revision-title-field :deep(.el-textarea__inner) {
  font-size: 15px;
  line-height: 1.6;
}

.editor-header {
  margin-bottom: 8px;
}

.editor-title {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.editor-preview {
  min-height: 360px;
  margin: 0;
}
</style>
