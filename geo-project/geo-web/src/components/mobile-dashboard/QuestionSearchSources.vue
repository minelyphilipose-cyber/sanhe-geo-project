<template>
  <section v-if="sources.length" class="detail-block source-panel">
    <div class="block-title source-title">
      <div>
        <MobileIcon name="globe" />
        <h3>{{ platformLabel }} 联网内容出处</h3>
      </div>
      <span>{{ sources.length }} 条</span>
    </div>
    <div class="source-list">
      <article v-for="(source, index) in sources" :key="source.sourceId" class="source-card">
        <a
          class="source-card__link"
          :href="source.safeUrl"
          target="_blank"
          rel="noopener noreferrer"
        >
          <span class="source-index">{{ index + 1 }}</span>
          <div class="source-card__body">
            <strong>{{ source.title || source.host || '未命名来源' }}</strong>
            <p v-if="source.snippet">{{ source.snippet }}</p>
            <div class="source-meta">
              <span>{{ source.host }}</span>
              <time v-if="source.publishTime">{{ formatSourceDate(source.publishTime) }}</time>
              <i v-if="source.cited">回答引用</i>
              <i v-else-if="source.brandMatched" class="related">品牌相关</i>
            </div>
          </div>
          <MobileIcon name="open_in_new" />
        </a>
        <button
          type="button"
          class="source-copy"
          :aria-label="`复制第 ${index + 1} 条来源链接`"
          @click="copySourceUrl(source.safeUrl)"
        >
          复制
        </button>
      </article>
    </div>
    <p class="source-note">当前仅展示本条 {{ platformLabel }} 回答的搜索来源；可打开原文或复制链接。</p>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { showToast } from 'vant'
import type { QuestionSearchSource } from '@/types/mobileDashboard'
import { buildDisplaySearchSources } from '@/utils/mobileDashboardSources'
import MobileIcon from './MobileIcon.vue'

const props = defineProps<{
  platformLabel: string
  searchSources?: QuestionSearchSource[]
}>()

const sources = computed(() => buildDisplaySearchSources(props.searchSources))

function formatSourceDate(value: string) {
  return value.slice(0, 10)
}

async function copySourceUrl(url: string) {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(url)
    } else {
      copySourceUrlFallback(url)
    }
    showToast('链接已复制')
  } catch {
    try {
      copySourceUrlFallback(url)
      showToast('链接已复制')
    } catch {
      showToast('复制失败，请长按链接复制')
    }
  }
}

function copySourceUrlFallback(url: string) {
  const input = document.createElement('textarea')
  input.value = url
  input.setAttribute('readonly', '')
  input.style.position = 'fixed'
  input.style.opacity = '0'
  document.body.appendChild(input)
  input.select()
  const copied = document.execCommand('copy')
  document.body.removeChild(input)
  if (!copied) throw new Error('copy failed')
}
</script>

<style scoped>
.detail-block {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #edf1ef;
}

.block-title,
.source-title > div {
  display: flex;
  align-items: center;
  gap: 7px;
}

.block-title {
  margin-bottom: 10px;
}

.block-title .mobile-icon {
  color: #006D44;
  font-size: 18px;
}

.block-title h3 {
  margin: 0;
  color: #131b2e;
  font-size: var(--mobile-text-lg, 16px);
  font-weight: 700;
  line-height: var(--mobile-leading-lg, 22px);
}

.source-title {
  justify-content: space-between;
}

.source-title > span {
  flex: 0 0 auto;
  padding: 2px 8px;
  border-radius: 999px;
  background: #e6f7ef;
  color: #006D44;
  font-size: var(--mobile-text-2xs, 10px);
  font-weight: 700;
  line-height: var(--mobile-leading-label-sm, 14px);
}

.source-list {
  display: grid;
  gap: 8px;
}

.source-card {
  min-height: 72px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 52px;
  overflow: hidden;
  border: 1px solid #e6eee9;
  border-radius: 13px;
  background: #fbfdfc;
  color: inherit;
  touch-action: manipulation;
  transition: border-color 160ms ease, background-color 160ms ease, transform 160ms ease;
}

.source-card__link {
  min-width: 0;
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr) 18px;
  align-items: start;
  gap: 10px;
  padding: 12px;
  color: inherit;
  text-decoration: none;
}

.source-card:active {
  border-color: #a7dbc5;
  background: #f2fbf7;
  transform: scale(0.99);
}

.source-card__link:focus-visible,
.source-copy:focus-visible {
  outline: 2px solid #07a66b;
  outline-offset: -2px;
}

@media (hover: hover) {
  .source-card:hover {
    border-color: #b8dfcf;
    background: #f7fcf9;
  }
}

.source-index {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border-radius: 9px;
  background: #e6f7ef;
  color: #006D44;
  font-size: var(--mobile-text-xs, 12px);
  font-weight: 700;
}

.source-card__body {
  min-width: 0;
}

.source-card__body strong,
.source-card__body > p {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.source-card__body strong {
  color: #131b2e;
  font-size: var(--mobile-text-md, 14px);
  font-weight: 700;
  line-height: var(--mobile-leading-md, 20px);
}

.source-card__body > p {
  margin: 5px 0 0;
  color: #52625C;
  font-size: var(--mobile-text-xs, 12px);
  line-height: var(--mobile-leading-label, 16px);
}

.source-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 5px 8px;
  margin-top: 7px;
  color: #718078;
  font-size: var(--mobile-text-2xs, 10px);
  line-height: var(--mobile-leading-label-sm, 14px);
}

.source-meta > span {
  max-width: 155px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.source-meta i {
  padding: 1px 6px;
  border-radius: 999px;
  background: #006D44;
  color: #fff;
  font-style: normal;
  font-weight: 700;
}

.source-meta i.related {
  background: #e6f7ef;
  color: #006D44;
}

.source-card__link > .mobile-icon {
  margin-top: 4px;
  color: #789087;
  font-size: 18px;
}

.source-copy {
  min-width: 44px;
  min-height: 44px;
  align-self: stretch;
  border: 0;
  border-left: 1px solid #e6eee9;
  background: #f4faf7;
  color: #006D44;
  font-size: var(--mobile-text-2xs, 10px);
  font-weight: 700;
  touch-action: manipulation;
}

.source-copy:active {
  background: #e6f7ef;
}

.source-note {
  margin: 9px 2px 0;
  color: #718078;
  font-size: var(--mobile-text-2xs, 10px);
  line-height: var(--mobile-leading-label-sm, 14px);
}
</style>
