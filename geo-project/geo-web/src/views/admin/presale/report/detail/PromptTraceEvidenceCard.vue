<template>
  <el-card shadow="never" class="evidence-card admin-rich-card">
    <template #header>
      <div class="evidence-header">
        <div class="evidence-heading">
          <span class="heading-icon" aria-hidden="true">
            <el-icon><Link /></el-icon>
          </span>
          <div>
            <div class="heading-title">引用来源</div>
            <div class="heading-subtitle">{{ summaryText }}</div>
          </div>
        </div>
        <div class="status-tags">
          <el-tag size="small" effect="plain" :type="statusTagType">
            {{ evidence.webSearch ? evidence.searchStatusText : '原生 API' }}
          </el-tag>
          <el-tag v-if="sourceCount" size="small" effect="plain" type="info">
            {{ sourceCount }} 条来源
          </el-tag>
        </div>
      </div>
    </template>

    <div v-if="evidence.searchQueries.length" class="query-strip">
      <el-icon aria-hidden="true"><Search /></el-icon>
      <span class="query-label">检索词</span>
      <div class="query-list">
        <span v-for="query in evidence.searchQueries" :key="query" class="query-chip">
          {{ query }}
        </span>
      </div>
    </div>

    <div v-if="sourceCount" class="source-grid">
      <article v-for="source in visibleSources" :key="`${source.index}-${source.url}`" class="source-item">
        <div class="source-index" aria-hidden="true">{{ source.index }}</div>
        <div class="source-content">
          <a
            class="source-title"
            :href="source.url"
            target="_blank"
            rel="noopener noreferrer"
            :aria-label="`在新窗口打开来源：${source.title}`"
          >
            <span>{{ source.title }}</span>
            <el-icon aria-hidden="true"><TopRight /></el-icon>
          </a>
          <div v-if="sourceMeta(source).length" class="source-meta">
            <span v-for="item in sourceMeta(source)" :key="item">{{ item }}</span>
          </div>
          <p v-if="source.snippet" class="source-snippet">{{ source.snippet }}</p>
          <div v-if="source.query" class="source-query" :title="source.query">
            <el-icon aria-hidden="true"><Search /></el-icon>
            <span>{{ source.query }}</span>
          </div>
        </div>
      </article>
    </div>

    <div v-else class="evidence-empty" :class="{ 'is-native': !evidence.webSearch }">
      <span class="empty-icon" aria-hidden="true">
        <el-icon><InfoFilled /></el-icon>
      </span>
      <div>
        <div class="empty-title">{{ emptyTitle }}</div>
        <p>{{ evidence.notice || defaultNotice }}</p>
      </div>
    </div>

    <div v-if="sourceCount > defaultVisibleCount" class="expand-row">
      <el-button text type="primary" @click="expanded = !expanded">
        {{ expanded ? '收起来源' : `查看全部 ${sourceCount} 条来源` }}
        <el-icon class="expand-icon" :class="{ 'is-expanded': expanded }"><ArrowDown /></el-icon>
      </el-button>
    </div>

    <el-collapse v-if="evidence.citations.length" class="citation-collapse">
      <el-collapse-item name="citations">
        <template #title>
          <span class="citation-title">回答引用片段</span>
          <el-tag size="small" effect="plain" type="info">{{ evidence.citations.length }}</el-tag>
        </template>
        <div class="citation-list">
          <blockquote
            v-for="citation in evidence.citations"
            :key="`${citation.index}-${citation.text}`"
            class="citation-item"
          >
            <span class="citation-number">{{ citation.index }}</span>
            <span>{{ citation.text }}</span>
          </blockquote>
        </div>
      </el-collapse-item>
    </el-collapse>
  </el-card>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ArrowDown, InfoFilled, Link, Search, TopRight } from '@element-plus/icons-vue'
import type {
  PresalePromptTraceEvidenceSourceVO,
  PresalePromptTraceEvidenceVO
} from '@/api/presaleReport'

const props = defineProps<{
  evidence: PresalePromptTraceEvidenceVO
}>()

const defaultVisibleCount = 4
const expanded = ref(false)
const sourceCount = computed(() => props.evidence.sources.length)
const visibleSources = computed(() =>
  expanded.value ? props.evidence.sources : props.evidence.sources.slice(0, defaultVisibleCount)
)

const summaryText = computed(() => {
  if (!props.evidence.webSearch) return '本次回答来自平台原生接口'
  if (sourceCount.value) return `平台返回 ${sourceCount.value} 条可核验来源`
  return '本次调用没有可展示的外部来源'
})

const statusTagType = computed<'success' | 'warning' | 'info'>(() => {
  if (!props.evidence.webSearch) return 'info'
  if (!props.evidence.searchTriggered || props.evidence.searchStatus === 'FAILED') return 'warning'
  if (sourceCount.value) return 'success'
  return 'warning'
})

const emptyTitle = computed(() => {
  if (!props.evidence.webSearch) return '本次未使用独立联网搜索'
  if (!props.evidence.searchTriggered) return '联网搜索未实际触发'
  return '暂无可展示的来源链接'
})

const defaultNotice = computed(() =>
  props.evidence.webSearch
    ? '本次回答未获取到可展示来源，但不影响查看真实模型回答。'
    : '平台原生接口没有返回独立引用来源。'
)

function sourceMeta(source: PresalePromptTraceEvidenceSourceVO) {
  const values = [source.media, source.domain, formatPublishTime(source.publishTime)]
  return [...new Set(values.filter((item): item is string => Boolean(item)))]
}

function formatPublishTime(value: string | null) {
  if (!value) return null
  const matched = value.match(/^\d{4}-\d{2}-\d{2}/)
  return matched ? matched[0] : value
}
</script>

<style scoped>
.evidence-card {
  margin-bottom: 16px;
}

.evidence-header,
.evidence-heading,
.status-tags,
.query-strip,
.query-list,
.source-meta,
.source-query,
.expand-row,
.citation-title {
  display: flex;
  align-items: center;
}

.evidence-header {
  justify-content: space-between;
  gap: 16px;
}

.evidence-heading {
  min-width: 0;
  gap: 11px;
}

.heading-icon,
.empty-icon {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  color: #2563eb;
  background: #eff6ff;
}

.heading-icon {
  width: 36px;
  height: 36px;
  font-size: 18px;
}

.heading-title {
  color: #172033;
  font-size: 15px;
  font-weight: 700;
  line-height: 1.4;
}

.heading-subtitle {
  margin-top: 2px;
  color: #7b8495;
  font-size: 12px;
  line-height: 1.4;
}

.status-tags {
  flex: 0 0 auto;
  gap: 8px;
}

.query-strip {
  gap: 8px;
  margin-bottom: 14px;
  padding: 10px 12px;
  border: 1px solid #e5edf8;
  border-radius: 9px;
  background: #f8fbff;
  color: #64748b;
}

.query-label {
  flex: 0 0 auto;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}

.query-list {
  min-width: 0;
  flex-wrap: wrap;
  gap: 6px;
}

.query-chip {
  max-width: 100%;
  padding: 3px 8px;
  overflow: hidden;
  border-radius: 5px;
  background: #ffffff;
  color: #475569;
  font-size: 12px;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.source-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.source-item {
  position: relative;
  display: flex;
  min-width: 0;
  gap: 11px;
  padding: 14px;
  border: 1px solid #e5eaf2;
  border-radius: 10px;
  background: #ffffff;
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
}

.source-item:hover {
  border-color: #bfd3f5;
  box-shadow: 0 6px 18px rgb(37 99 235 / 7%);
}

.source-index {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 7px;
  background: #eef4ff;
  color: #2563eb;
  font-size: 12px;
  font-weight: 700;
}

.source-content {
  min-width: 0;
  flex: 1;
}

.source-title {
  display: inline-flex;
  max-width: 100%;
  align-items: center;
  gap: 5px;
  color: #1d4ed8;
  font-size: 14px;
  font-weight: 700;
  line-height: 1.45;
  text-decoration: none;
}

.source-title span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.source-title .el-icon {
  flex: 0 0 auto;
}

.source-title:hover,
.source-title:focus-visible {
  text-decoration: underline;
}

.source-title:focus-visible {
  border-radius: 3px;
  outline: 2px solid #93c5fd;
  outline-offset: 2px;
}

.source-meta {
  flex-wrap: wrap;
  gap: 6px 12px;
  margin-top: 5px;
  color: #8490a2;
  font-size: 11px;
}

.source-meta span + span::before {
  margin-right: 8px;
  color: #cbd5e1;
  content: '·';
}

.source-snippet {
  display: -webkit-box;
  margin: 9px 0 0;
  overflow: hidden;
  color: #526071;
  font-size: 12px;
  line-height: 1.65;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.source-query {
  min-width: 0;
  gap: 5px;
  margin-top: 9px;
  color: #94a3b8;
  font-size: 11px;
}

.source-query span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.evidence-empty {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px;
  border: 1px solid #f5dfad;
  border-radius: 10px;
  background: #fffbeb;
}

.evidence-empty.is-native {
  border-color: #e5eaf2;
  background: #f8fafc;
}

.empty-icon {
  width: 30px;
  height: 30px;
  color: #b7791f;
  background: #fef3c7;
}

.is-native .empty-icon {
  color: #64748b;
  background: #e9eef5;
}

.empty-title {
  color: #394457;
  font-size: 13px;
  font-weight: 700;
}

.evidence-empty p {
  margin: 4px 0 0;
  color: #7b8495;
  font-size: 12px;
  line-height: 1.6;
}

.expand-row {
  justify-content: center;
  padding-top: 8px;
}

.expand-icon {
  margin-left: 4px;
  transition: transform 0.18s ease;
}

.expand-icon.is-expanded {
  transform: rotate(180deg);
}

.citation-collapse {
  margin-top: 14px;
  border-top: 1px solid #edf0f5;
}

.citation-title {
  gap: 8px;
  color: #475569;
  font-size: 13px;
  font-weight: 700;
}

.citation-list {
  display: grid;
  gap: 8px;
}

.citation-item {
  display: flex;
  gap: 10px;
  margin: 0;
  padding: 10px 12px;
  border-left: 3px solid #bfdbfe;
  border-radius: 0 7px 7px 0;
  background: #f8fbff;
  color: #526071;
  font-size: 12px;
  line-height: 1.65;
}

.citation-number {
  flex: 0 0 auto;
  color: #2563eb;
  font-weight: 700;
}

@media (max-width: 900px) {
  .source-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .evidence-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .source-item {
    padding: 12px;
  }

  .query-strip {
    align-items: flex-start;
  }
}
</style>
