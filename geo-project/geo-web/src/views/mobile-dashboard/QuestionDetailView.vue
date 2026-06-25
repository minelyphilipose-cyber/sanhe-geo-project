<template>
  <div class="mobile-page question-detail-page">
    <button type="button" class="back-link" @click="router.back()">
      <MobileIcon name="chevronLeft" />
      返回监测
    </button>

    <DashboardCard title="问答详情" icon="search">
      <template v-if="loading">
        <van-skeleton title :row="5" />
      </template>
      <template v-else-if="item">
        <section class="detail-hero">
          <div class="detail-avatar">
            <img
              v-if="platformLogo(item.platformCode)"
              :src="platformLogo(item.platformCode)"
              :alt="platformLabel(item.platformCode)"
            >
            <span v-else>{{ platformInitial(item.platformCode) }}</span>
          </div>
          <div class="detail-title">
            <p>{{ hitPlatformLabel(item) }} · 核心问题</p>
            <h2>{{ item.questionTitle }}</h2>
          </div>
          <span class="detail-status" :class="{ building: !item.mentioned }">{{ rightStatus(item) }}</span>
        </section>

        <section class="result-card">
          <div class="result-card__head">
            <span>命中结论</span>
            <strong>{{ item.mentioned ? '该平台回答已提及品牌' : '相关内容建设中' }}</strong>
          </div>
          <div class="tag-row detail-tags">
            <span class="tag" :class="item.mentioned ? 'platform-tag' : 'building'">
              {{ item.mentioned ? hitPlatformLabel(item) : '持续覆盖' }}
            </span>
            <span v-for="tag in statusTags(item)" :key="tag.text" class="tag" :class="tag.kind">
              {{ tag.text }}
            </span>
          </div>
          <p>{{ fullSummary(item) }}</p>
        </section>

        <section class="detail-block">
          <div class="block-title">
            <MobileIcon name="question" />
            <h3>提问内容</h3>
          </div>
          <p class="question-text">{{ item.questionTitle }}</p>
        </section>

        <section class="detail-block answer-panel">
          <div class="block-title">
            <MobileIcon name="chat" />
            <h3>{{ hitPlatformLabel(item) }} 原始回答</h3>
          </div>
          <article
            v-if="item.mentioned && renderedResponse"
            class="answer-content markdown-answer"
            v-html="renderedResponse"
          />
          <p v-else-if="item.mentioned" class="empty-answer">
            该条历史监测记录未保留原始回答文本，命中结论以系统分析结果为准。
          </p>
          <p v-else class="empty-answer">该核心问题相关场景内容正在持续建设与覆盖，暂无命中平台问答详情。</p>
        </section>

        <section class="detail-block">
          <div class="block-title">
            <MobileIcon name="document" />
            <h3>推荐分析</h3>
          </div>
          <div class="analysis-list">
            <div v-for="row in analysisRows(item)" :key="row.label" class="analysis-row">
              <span>{{ row.label }}</span>
              <strong>{{ row.value }}</strong>
            </div>
          </div>
          <blockquote v-if="item.evidence?.trim()" class="evidence-quote">
            {{ item.evidence.trim() }}
          </blockquote>
        </section>

        <section class="detail-block meta-panel">
          <div class="block-title">
            <MobileIcon name="clock" />
            <h3>监测信息</h3>
          </div>
          <div class="meta-grid">
            <div>
              <span>命中平台</span>
              <strong>{{ item.mentioned ? hitPlatformLabel(item) : '暂无' }}</strong>
            </div>
            <div>
              <span>监测时间</span>
              <strong>{{ formatDateTime(item.completedAt) }}</strong>
            </div>
          </div>
        </section>
      </template>
      <EmptyState v-else description="未找到该问题监测记录" />
    </DashboardCard>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import MarkdownIt from 'markdown-it'
import { getMobileDashboardMonitor, withRenewedMobileDashboardSession } from '@/api/mobileDashboard'
import DashboardCard from '@/components/mobile-dashboard/DashboardCard.vue'
import EmptyState from '@/components/mobile-dashboard/EmptyState.vue'
import MobileIcon from '@/components/mobile-dashboard/MobileIcon.vue'
import { useMobileDashboardStore } from '@/stores/mobileDashboard'
import type { DashboardMetric, QuestionMonitorItem } from '@/types/mobileDashboard'
import { aiPlatformLabel } from '@/utils/mobileDashboardDictionaries'
import deepseekLogo from '@/assets/ai-model-logos/deepseek-color.png'
import doubaoLogo from '@/assets/ai-model-logos/doubao.png'
import hunyuanLogo from '@/assets/ai-model-logos/hunyuan-color.png'
import qwenLogo from '@/assets/ai-model-logos/qwen-color.png'
import wenxinLogo from '@/assets/ai-model-logos/文心一言.png'

const QUESTION_DETAIL_CACHE_KEY = 'mobile_dashboard_question_detail'
const route = useRoute()
const router = useRouter()
const store = useMobileDashboardStore()
const loading = ref(false)
const item = ref<QuestionMonitorItem | null>(null)
const pollResultId = computed(() => Number(route.params.pollResultId))
const markdown = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
})
const renderedResponse = computed(() => renderMarkdown(item.value?.responseText))

const aiPlatformLogos: Record<string, string> = {
  doubao: doubaoLogo,
  deepseek: deepseekLogo,
  tongyi: qwenLogo,
  qwen: qwenLogo,
  wenxin: wenxinLogo,
  ernie: wenxinLogo,
  yuanbao: hunyuanLogo,
  hunyuan: hunyuanLogo,
}

function metricBool(metric?: DashboardMetric<boolean>) {
  return metric?.available && metric.value === true
}

function platformLabel(code: string) {
  return aiPlatformLabel(code)
}

function platformInitial(code: string) {
  return platformLabel(code).slice(0, 1)
}

function platformLogo(code?: string | null) {
  if (!code) return ''
  return aiPlatformLogos[code] || ''
}

function renderMarkdown(value?: string | null) {
  const raw = value?.trim() || ''
  return raw ? markdown.render(raw) : ''
}

function hitPlatformLabel(row: QuestionMonitorItem) {
  const codes = row.platformCodes?.length ? row.platformCodes : [row.platformCode]
  return platformLabel(codes[0])
}

function rightStatus(row: QuestionMonitorItem) {
  if (metricBool(row.firstRecommend)) return '首推'
  if (row.rankPosition?.available && row.rankPosition.value) return `第${row.rankPosition.value}位`
  if (metricBool(row.recommended)) return '已推荐'
  if (row.mentioned) return '已提及'
  return '建设中'
}

function statusTags(row: QuestionMonitorItem) {
  const tags: Array<{ text: string; kind: string }> = []
  if (row.mentioned) {
    tags.push({ text: '已提及品牌', kind: 'success' })
  }
  if (metricBool(row.recommended)) tags.push({ text: '推荐', kind: 'success' })
  if (metricBool(row.firstRecommend)) tags.push({ text: '首推', kind: 'primary' })
  return tags
}

function fullSummary(row: QuestionMonitorItem) {
  if (row.evidence?.trim()) return row.evidence.trim()
  if (metricBool(row.recommended)) return '本轮回答中出现主动推荐，推荐详情已通过样本分析校验。'
  if (row.mentioned) return `${hitPlatformLabel(row)} 回答已提及品牌。推荐与排名结果将在核心样本分析充分后展示。`
  return '相关场景内容正在持续建设与覆盖。'
}

function judgeState(row: QuestionMonitorItem) {
  if (row.recommended?.available || row.firstRecommend?.available || row.rankPosition?.available) return '已达标'
  return row.recommended?.reason || row.firstRecommend?.reason || '样本分析中'
}

function metricStatus(metric?: DashboardMetric<boolean>, positiveText = '是', negativeText = '否') {
  if (!metric?.available) return '样本分析中'
  return metric.value === true ? positiveText : negativeText
}

function rankText(row: QuestionMonitorItem) {
  if (metricBool(row.firstRecommend)) return '首推'
  if (row.rankPosition?.available && row.rankPosition.value) return `第${row.rankPosition.value}位`
  if (!row.rankPosition?.available) return '样本分析中'
  return '未进入排序'
}

function analysisRows(row: QuestionMonitorItem) {
  return [
    { label: '品牌提及', value: row.mentioned ? '已提及品牌' : '建设中' },
    { label: '推荐判定', value: metricStatus(row.recommended, '已推荐', '未推荐') },
    { label: '首推/排名', value: rankText(row) },
    { label: '分析进度', value: judgeState(row) },
  ]
}

function formatDateTime(value?: string | null) {
  if (!value) return '暂无'
  return value.replace('T', ' ').slice(0, 16)
}

function readCachedItem() {
  try {
    const cached = sessionStorage.getItem(QUESTION_DETAIL_CACHE_KEY)
    if (!cached) return null
    const parsed = JSON.parse(cached) as QuestionMonitorItem
    return Number(parsed.pollResultId) === pollResultId.value ? parsed : null
  } catch {
    return null
  }
}

async function loadItem() {
  item.value = readCachedItem()
  if (item.value) return
  loading.value = true
  try {
    const res = await withRenewedMobileDashboardSession(
      (sessionToken) => getMobileDashboardMonitor(sessionToken),
      store,
    )
    item.value = res.data.data.questionList.items.find((row) => Number(row.pollResultId) === pollResultId.value) || null
  } catch (error: any) {
    showToast(error?.message || '详情加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadItem)
</script>

<style scoped>
.question-detail-page {
  display: grid;
  gap: 12px;
}

.back-link {
  justify-self: start;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0;
  border: 0;
  background: transparent;
  color: #006D44;
  font-size: 13px;
  font-weight: 800;
}

.detail-hero {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  min-width: 0;
}

.detail-avatar {
  flex: 0 0 auto;
  width: 48px;
  height: 48px;
  display: grid;
  place-items: center;
  border-radius: 16px;
  background: #e6f7ef;
  color: #006D44;
  font-size: 18px;
  font-weight: 800;
}

.detail-avatar img {
  width: 32px;
  height: 32px;
  object-fit: contain;
}

.detail-title {
  flex: 1;
  min-width: 0;
}

.detail-title p {
  margin: 0 0 4px;
  color: #52625C;
  font-size: 12px;
  font-weight: 700;
}

.detail-title h2 {
  margin: 0;
  color: #131b2e;
  font-size: 18px;
  font-weight: 900;
  line-height: 1.4;
}

.detail-status {
  flex: 0 0 auto;
  color: #006D44;
  font-size: 12px;
  font-weight: 900;
  white-space: nowrap;
}

.detail-status.building {
  color: #006D44;
}

.result-card {
  margin-top: 14px;
  padding: 12px;
  border-radius: 14px;
  background: #f7fbf9;
}

.result-card__head {
  display: grid;
  gap: 4px;
}

.result-card__head span {
  color: #52625C;
  font-size: 12px;
  font-weight: 700;
}

.result-card__head strong {
  color: #131b2e;
  font-size: 15px;
  font-weight: 900;
  line-height: 1.4;
}

.result-card p {
  margin: 10px 0 0;
  color: #3d4a41;
  font-size: 13px;
  line-height: 1.7;
}

.detail-tags {
  margin-top: 10px;
}

.tag-row {
  display: flex;
  gap: 6px;
  overflow-x: auto;
  scrollbar-width: none;
}

.tag-row::-webkit-scrollbar {
  display: none;
}

.tag {
  flex: 0 0 auto;
  padding: 4px 9px;
  border-radius: 999px;
  background: #f3f4f6;
  color: #6b7280;
  font-size: 11px;
  font-weight: 800;
  line-height: 1.4;
  white-space: nowrap;
}

.tag.platform-tag {
  background: #eef4ff;
}

.tag.success,
.tag.primary {
  background: #e6f7ef;
  color: #006D44;
}

.tag.building {
  border: 1px solid #d7dee8;
  background: #f8fafc;
  color: #52625C;
}

.detail-block {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #eef0f2;
}

.block-title {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 10px;
}

.block-title .mobile-icon {
  color: #006D44;
  font-size: 17px;
}

.block-title h3 {
  margin: 0;
  color: #131b2e;
  font-size: 15px;
  font-weight: 900;
}

.question-text,
.empty-answer {
  margin: 0;
  color: #3d4a41;
  font-size: 13px;
  line-height: 1.7;
  word-break: break-word;
}

.answer-panel {
  padding: 16px 0 0;
}

.answer-content,
.empty-answer {
  margin: 0;
  padding: 13px 14px;
  border-radius: 14px;
  background: #f8fafc;
  color: #24352c;
  font-size: 13px;
  line-height: 1.75;
  word-break: break-word;
}

.answer-content {
  max-height: 420px;
  overflow-y: auto;
  user-select: text;
}

.markdown-answer :deep(*) {
  max-width: 100%;
}

.markdown-answer :deep(p) {
  margin: 0 0 10px;
}

.markdown-answer :deep(p:last-child),
.markdown-answer :deep(ul:last-child),
.markdown-answer :deep(ol:last-child),
.markdown-answer :deep(pre:last-child),
.markdown-answer :deep(blockquote:last-child) {
  margin-bottom: 0;
}

.markdown-answer :deep(h1),
.markdown-answer :deep(h2),
.markdown-answer :deep(h3),
.markdown-answer :deep(h4) {
  margin: 14px 0 8px;
  color: #131b2e;
  font-weight: 900;
  line-height: 1.35;
}

.markdown-answer :deep(h1) {
  font-size: 17px;
}

.markdown-answer :deep(h2),
.markdown-answer :deep(h3),
.markdown-answer :deep(h4) {
  font-size: 15px;
}

.markdown-answer :deep(ul),
.markdown-answer :deep(ol) {
  margin: 8px 0 10px;
  padding-left: 18px;
}

.markdown-answer :deep(li) {
  margin: 4px 0;
}

.markdown-answer :deep(blockquote) {
  margin: 10px 0;
  padding: 8px 10px;
  border-left: 3px solid #006D44;
  border-radius: 0 8px 8px 0;
  background: #f2fbf7;
  color: #52625C;
}

.markdown-answer :deep(code) {
  padding: 1px 5px;
  border-radius: 5px;
  background: #eef2f7;
  color: #1f2937;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
}

.markdown-answer :deep(pre) {
  margin: 10px 0;
  padding: 10px;
  overflow-x: auto;
  border-radius: 10px;
  background: #eef2f7;
}

.markdown-answer :deep(pre code) {
  padding: 0;
  background: transparent;
}

.markdown-answer :deep(table) {
  display: block;
  width: 100%;
  overflow-x: auto;
  border-collapse: collapse;
}

.markdown-answer :deep(th),
.markdown-answer :deep(td) {
  padding: 6px 8px;
  border: 1px solid #e5e7eb;
  white-space: nowrap;
}

.markdown-answer :deep(a) {
  color: #006D44;
  font-weight: 800;
  word-break: break-all;
}

.analysis-list {
  display: grid;
  gap: 8px;
}

.analysis-row {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  padding: 10px 0;
  border-bottom: 1px solid #f1f5f9;
}

.analysis-row:last-child {
  border-bottom: 0;
}

.analysis-row span {
  flex: 1;
  min-width: 0;
  color: #52625C;
  font-size: 13px;
}

.analysis-row strong {
  flex: 0 1 auto;
  color: #131b2e;
  font-size: 13px;
  font-weight: 900;
  line-height: 1.4;
  text-align: right;
  word-break: break-word;
}

.evidence-quote {
  margin: 12px 0 0;
  padding: 11px 12px;
  border-left: 3px solid #006D44;
  border-radius: 0 10px 10px 0;
  background: #f2fbf7;
  color: #3d4a41;
  font-size: 13px;
  line-height: 1.7;
}

.meta-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.meta-grid div {
  min-width: 0;
  padding: 12px;
  border-radius: 12px;
  background: #f8fafc;
}

.meta-grid span {
  display: block;
  color: #52625C;
  font-size: 12px;
  line-height: 1.35;
}

.meta-grid strong {
  display: block;
  margin-top: 6px;
  color: #131b2e;
  font-size: 14px;
  font-weight: 900;
  line-height: 1.4;
  word-break: break-word;
}

@media (max-width: 374px) {
  .detail-hero {
    gap: 10px;
  }

  .detail-avatar {
    width: 42px;
    height: 42px;
    border-radius: 14px;
  }

  .detail-avatar img {
    width: 28px;
    height: 28px;
  }

  .detail-title h2 {
    font-size: 16px;
  }

  .meta-grid {
    grid-template-columns: 1fr;
  }
}

</style>
