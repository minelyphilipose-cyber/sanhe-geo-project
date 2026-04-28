<template>
  <transition name="fade">
    <div v-if="visible" class="overlay" @click="emit('update:visible', false)"></div>
  </transition>
  <transition name="slide-up">
    <div v-if="visible" class="preview-panel">
      <div class="preview-header">
        <h3 class="preview-title">拓词预览</h3>
        <div class="preview-meta">本次入库 <strong>{{ keywords.length }}</strong> 条，候选池共 {{ totalAvailable }} 条</div>
      </div>
      <div class="preview-body">
        <div v-if="totalAvailable < previewCount" class="preview-tip">
          当前组合可生成 {{ totalAvailable }} 条关键词，少于设定的 {{ previewCount }} 条，请增加选词后再预览。
        </div>
        <div class="keyword-tags">
          <span v-for="(kw, idx) in displayKeywords" :key="`${idx}_${kw}`" class="keyword-tag">{{ kw }}</span>
        </div>
        <div v-if="keywords.length > maxDisplay" class="show-more">
          <button class="btn-show-more" @click="showAll = !showAll">{{ showAll ? '收起' : `展开全部 ${keywords.length} 条` }}</button>
        </div>
      </div>
      <div class="preview-actions">
        <button class="btn-secondary" @click="emit('update:visible', false)">取消</button>
        <button class="btn-primary" :disabled="saving" @click="emit('submit')">{{ saving ? '保存中...' : '保存关键词组' }}</button>
      </div>
    </div>
  </transition>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

const props = withDefaults(defineProps<{
  visible: boolean
  keywords: string[]
  totalAvailable: number
  previewCount: number
  saving?: boolean
}>(), {
  saving: false,
})

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'submit'): void
}>()

const maxDisplay = 50
const showAll = ref(false)
const displayKeywords = computed(() => (showAll.value ? props.keywords : props.keywords.slice(0, maxDisplay)))

watch(() => props.visible, () => {
  showAll.value = false
})
</script>

<style scoped>
.overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.25);
  z-index: 100;
}

.preview-panel {
  position: fixed;
  left: 50%;
  bottom: 0;
  transform: translateX(-50%);
  width: min(920px, 94vw);
  max-height: 72vh;
  background: #fff;
  border-radius: 14px 14px 0 0;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.12);
  z-index: 101;
  display: flex;
  flex-direction: column;
}

.preview-header,
.preview-actions {
  padding: 14px 18px;
  border-bottom: 1px solid #f1f5f9;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.preview-actions {
  border-bottom: none;
  border-top: 1px solid #f1f5f9;
  justify-content: flex-end;
  gap: 10px;
}

.preview-title {
  margin: 0;
  font-size: 15px;
}

.preview-meta {
  font-size: 13px;
  color: #64748b;
}

.preview-body {
  padding: 16px 18px;
  overflow: auto;
}

.preview-tip {
  margin-bottom: 12px;
  font-size: 13px;
  color: #b45309;
  background: #fff7ed;
  border: 1px solid #fdba74;
  border-radius: 8px;
  padding: 10px 12px;
}

.keyword-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.keyword-tag {
  font-size: 13px;
  color: #4361ee;
  background: #eef1ff;
  border-radius: 16px;
  padding: 5px 12px;
}

.show-more {
  margin-top: 12px;
}

.btn-show-more,
.btn-secondary,
.btn-primary {
  border-radius: 6px;
  padding: 7px 14px;
  cursor: pointer;
}

.btn-show-more,
.btn-secondary {
  border: 1px solid #cbd5e1;
  background: #fff;
  color: #334155;
}

.btn-primary {
  border: none;
  background: #4361ee;
  color: #fff;
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.slide-up-enter-active,
.slide-up-leave-active {
  transition: all 0.25s ease;
}

.slide-up-enter-from,
.slide-up-leave-to {
  transform: translateX(-50%) translateY(100%);
  opacity: 0;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
