<template>
  <div class="prompt-detail-page admin-page">
    <div class="page-header admin-page-header">
      <div>
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/admin/presale/report' }">AI可见度诊断报告</el-breadcrumb-item>
          <el-breadcrumb-item :to="promptListLocation">Prompt 调用记录</el-breadcrumb-item>
          <el-breadcrumb-item>调用详情</el-breadcrumb-item>
        </el-breadcrumb>
        <div class="admin-page-kicker">调用详情</div>
        <h2 class="page-title admin-page-title">Prompt 调用详情</h2>
        <div class="admin-page-subtitle">查看 Prompt 请求、模型原始回答、解析结果与失败原因。</div>
      </div>
      <div class="admin-page-actions">
        <el-button @click="goList">返回列表</el-button>
      </div>
    </div>

    <div v-if="loading" class="state-panel">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>正在加载...</span>
    </div>

    <template v-else-if="detail">
      <el-card shadow="never" class="summary-card admin-rich-card">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTagType(detail.summary.traceStatus)">
              {{ detail.summary.traceStatusText }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="平台">{{ detail.summary.platformName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="Prompt 类型">{{ batchText(detail.summary.batchNo) }}</el-descriptions-item>
          <el-descriptions-item label="请求模型">
            {{ detail.summary.queryModelName || '—' }}
            <el-tooltip
              v-if="detail.queryModelSnapshotInferred"
              content="模型信息按当前平台配置推断，可能与生成时实际模型不符"
              placement="top"
            >
              <el-icon class="infer-icon"><InfoFilled /></el-icon>
            </el-tooltip>
          </el-descriptions-item>
          <el-descriptions-item label="解析模型">
            {{ detail.summary.analyzeModelName || '—' }}
            <el-tooltip
              v-if="detail.analyzeModelSnapshotInferred"
              content="模型信息按当前平台配置推断，可能与生成时实际模型不符"
              placement="top"
            >
              <el-icon class="infer-icon"><InfoFilled /></el-icon>
            </el-tooltip>
          </el-descriptions-item>
          <el-descriptions-item label="耗用时间">
            {{ formatDuration(detail.summary.totalDurationMs) }}
            <span class="muted">
              QUERY {{ formatDuration(detail.queryDurationMs) }} / ANALYZE {{ formatDuration(detail.analyzeDurationMs) }}
            </span>
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-alert
        v-if="detail.summary.traceStatus === 'QUERY_FAILED'"
        type="error"
        show-icon
        :closable="false"
        class="alert"
        :title="detail.queryFailureReason || 'QUERY 调用失败'"
      />
      <el-alert
        v-else-if="detail.summary.traceStatus === 'ANALYZE_FAILED'"
        type="warning"
        show-icon
        :closable="false"
        class="alert"
        :title="detail.analyzeFailureReason || 'QUERY 调用成功，但解析失败'"
      />

      <el-card shadow="never" class="content-card admin-rich-card">
        <template #header>请求 Prompt</template>
        <div class="block-hint">业务 Prompt（变量替换后）</div>
        <pre class="text-block">{{ detail.summary.requestPromptContent || detail.queryPromptContent || '—' }}</pre>
      </el-card>

      <el-card shadow="never" class="content-card admin-rich-card">
        <template #header>大模型回答详情</template>
        <div
          v-if="renderedQueryResponse"
          class="markdown-block"
          v-html="renderedQueryResponse"
        />
        <pre v-else class="text-block">—</pre>
      </el-card>

      <PromptTraceEvidenceCard :evidence="detail.evidence" />

      <el-card shadow="never" class="content-card admin-rich-card">
        <template #header>解析结果</template>
        <div class="parse-grid">
          <div class="parse-item">
            <span class="parse-label">是否提及品牌</span>
            <el-tag :type="detail.parseView.mentionedText === '已提及' ? 'success' : 'info'">
              {{ detail.parseView.mentionedText }}
            </el-tag>
          </div>
          <div class="parse-item">
            <span class="parse-label">排名</span>
            <span>{{ detail.parseView.rankingText }}</span>
          </div>
          <div class="parse-item">
            <span class="parse-label">情感倾向</span>
            <el-tag :type="detail.parseView.sentimentType">
              {{ detail.parseView.sentimentText }}
            </el-tag>
          </div>
        </div>

        <div class="parse-section">
          <div class="section-title">提及竞品</div>
          <div v-if="detail.parseView.mentionedCompetitors.length" class="tag-list">
            <el-tag v-for="item in detail.parseView.mentionedCompetitors" :key="item" effect="plain">
              {{ item }}
            </el-tag>
          </div>
          <span v-else class="muted">无</span>
        </div>

        <div class="parse-section">
          <div class="section-title">场景优势</div>
          <ul v-if="detail.parseView.sceneAdvantages.length" class="plain-list">
            <li v-for="item in detail.parseView.sceneAdvantages" :key="item">{{ item }}</li>
          </ul>
          <span v-else class="muted">无</span>
        </div>

        <div class="parse-section">
          <div class="section-title">情感关键词</div>
          <div v-if="detail.parseView.topKeywords.length" class="tag-list">
            <el-tag
              v-for="item in detail.parseView.topKeywords"
              :key="`${item.keyword}-${item.sentimentText}`"
              :type="item.sentimentType"
              effect="plain"
            >
              {{ item.keyword }} · {{ item.sentimentText }}
            </el-tag>
          </div>
          <span v-else class="muted">无</span>
        </div>

        <div class="parse-section">
          <div class="section-title">负面证据</div>
          <el-tag :type="detail.parseView.negativeEvidence.hasNegative ? 'danger' : 'info'" effect="plain">
            {{ detail.parseView.negativeEvidence.hasNegativeText }}
          </el-tag>
          <blockquote v-if="detail.parseView.negativeEvidence.snippet" class="quote">
            {{ detail.parseView.negativeEvidence.snippet }}
          </blockquote>
          <span v-else class="muted quote-empty">无</span>
        </div>
      </el-card>

      <el-card shadow="never" class="content-card admin-rich-card">
        <template #header>原始解析 JSON</template>
        <el-alert
          v-if="!formattedAnalyzeJson.isJson && detail.analyzeRawResponse"
          type="error"
          show-icon
          :closable="false"
          class="json-alert"
          title="该响应未通过 JSON 解析"
        />
        <pre class="text-block code-block">{{ formattedAnalyzeJson.text || detail.analyzeFailureReason || '—' }}</pre>
      </el-card>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { InfoFilled, Loading } from '@element-plus/icons-vue'
import MarkdownIt from 'markdown-it'
import {
  getReportPromptTraceDetail,
  type PresalePromptTraceDetailVO,
  type PresalePromptTraceStatus
} from '@/api/presaleReport'
import PromptTraceEvidenceCard from './detail/PromptTraceEvidenceCard.vue'

const markdown = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true
})

const route = useRoute()
const router = useRouter()
const reportId = computed(() => Number(route.params.id))
const versionNo = computed(() => Number(route.params.versionNo))
const promptResultId = computed(() => Number(route.params.promptResultId))
const promptListPath = computed(() =>
  `/admin/presale/report/${reportId.value}/versions/${versionNo.value}/prompts`
)
const promptListLocation = computed(() => ({
  path: promptListPath.value,
  query: route.query
}))

const loading = ref(false)
const detail = ref<PresalePromptTraceDetailVO | null>(null)

const renderedQueryResponse = computed(() => {
  const raw = detail.value?.queryRawResponse || detail.value?.queryFailureReason || ''
  return raw.trim() ? markdown.render(raw) : ''
})

const formattedAnalyzeJson = computed(() => {
  const raw = detail.value?.analyzeRawResponse || ''
  if (!raw) return { isJson: true, text: '' }
  const stripped = stripMarkdownJsonFence(raw)
  try {
    return { isJson: true, text: JSON.stringify(JSON.parse(stripped), null, 2) }
  } catch {
    return { isJson: false, text: raw }
  }
})

async function load() {
  loading.value = true
  try {
    detail.value = await getReportPromptTraceDetail(
      reportId.value,
      versionNo.value,
      promptResultId.value
    )
  } finally {
    loading.value = false
  }
}

function goList() {
  router.push(promptListLocation.value)
}

function stripMarkdownJsonFence(value: string) {
  const trimmed = value.trim()
  const match = trimmed.match(/^```(?:json)?\s*([\s\S]*?)\s*```$/)
  return match ? match[1].trim() : trimmed
}

function batchText(batchNo: number) {
  if (batchNo === 1) return '认知型 Prompt'
  if (batchNo === 2) return '对比型 Prompt'
  return `BATCH ${batchNo}`
}

function formatDuration(value: number | null | undefined) {
  if (value == null) return '—'
  if (value < 1000) return `${value}ms`
  return `${(value / 1000).toFixed(1)}s`
}

function statusTagType(status: PresalePromptTraceStatus) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'ANALYZE_FAILED') return 'warning'
  return 'danger'
}

onMounted(load)
</script>

<style scoped>
.prompt-detail-page {
  padding: 16px 24px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 16px;
  margin-bottom: 16px;
}
.page-title {
  margin: 8px 0 0;
  font-size: 22px;
  font-weight: 600;
}
.state-panel {
  padding: 80px 24px;
  text-align: center;
  color: #909399;
}
.summary-card,
.content-card,
.alert {
  margin-bottom: 16px;
}
.muted {
  color: #909399;
  margin-left: 8px;
}
.text-block {
  margin: 0;
  padding: 14px 16px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  background: #fafafa;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.6;
  font-family: inherit;
  color: #303133;
  user-select: text;
}
.markdown-block {
  padding: 16px 18px;
  border: 1px solid #dbeafe;
  border-radius: 10px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
  color: #1f2937;
  line-height: 1.75;
  word-break: break-word;
  user-select: text;
}
.markdown-block :deep(*) {
  box-sizing: border-box;
}
.markdown-block :deep(*:first-child) {
  margin-top: 0;
}
.markdown-block :deep(*:last-child) {
  margin-bottom: 0;
}
.markdown-block :deep(h1),
.markdown-block :deep(h2),
.markdown-block :deep(h3),
.markdown-block :deep(h4) {
  margin: 18px 0 10px;
  color: #0f172a;
  font-weight: 800;
  line-height: 1.35;
}
.markdown-block :deep(h1) {
  font-size: 22px;
}
.markdown-block :deep(h2) {
  font-size: 19px;
}
.markdown-block :deep(h3) {
  font-size: 17px;
}
.markdown-block :deep(p) {
  margin: 8px 0;
}
.markdown-block :deep(ul),
.markdown-block :deep(ol) {
  margin: 8px 0 12px;
  padding-left: 22px;
}
.markdown-block :deep(li) {
  margin: 4px 0;
}
.markdown-block :deep(blockquote) {
  margin: 12px 0;
  padding: 10px 14px;
  border-left: 4px solid #93c5fd;
  border-radius: 0 8px 8px 0;
  background: #eff6ff;
  color: #475569;
}
.markdown-block :deep(code) {
  padding: 2px 5px;
  border-radius: 5px;
  background: #eef2ff;
  color: #1d4ed8;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12px;
}
.markdown-block :deep(pre) {
  overflow: auto;
  margin: 12px 0;
  padding: 12px 14px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #0f172a;
  color: #e5e7eb;
}
.markdown-block :deep(pre code) {
  padding: 0;
  background: transparent;
  color: inherit;
}
.markdown-block :deep(table) {
  width: 100%;
  margin: 12px 0;
  border-collapse: collapse;
  font-size: 13px;
}
.markdown-block :deep(th),
.markdown-block :deep(td) {
  padding: 8px 10px;
  border: 1px solid #dbe3ee;
  text-align: left;
}
.markdown-block :deep(th) {
  background: #eff6ff;
  color: #334155;
  font-weight: 800;
}
.markdown-block :deep(a) {
  color: #2563eb;
  text-decoration: none;
}
.markdown-block :deep(a:hover) {
  text-decoration: underline;
}
.code-block {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12px;
}
.parse-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}
.parse-item {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 36px;
}
.parse-label,
.section-title {
  color: #606266;
  font-size: 13px;
}
.parse-section {
  padding: 14px 0;
  border-top: 1px solid #ebeef5;
}
.section-title {
  margin-bottom: 8px;
}
.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.plain-list {
  margin: 0;
  padding-left: 18px;
  line-height: 1.8;
}
.quote {
  margin: 10px 0 0;
  padding: 10px 12px;
  border-left: 3px solid #f56c6c;
  background: #fef0f0;
  color: #606266;
}
.quote-empty {
  display: inline-block;
  margin-top: 8px;
}
.json-alert {
  margin-bottom: 10px;
}
.block-hint {
  margin-bottom: 8px;
  color: #909399;
  font-size: 12px;
}
.infer-icon {
  margin-left: 4px;
  color: #e6a23c;
  vertical-align: middle;
}
</style>
