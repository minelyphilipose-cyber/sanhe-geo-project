<template>
  <el-drawer v-model="visible" title="文章详情" size="70%" class="content-detail-drawer">
    <div v-if="detailData" class="detail-wrap">
      <div class="detail-summary-panel">
        <div class="detail-summary-head">
          <div>
            <span class="detail-kicker">文章信息</span>
            <h3>{{ detailData.article.title || '未命名文章' }}</h3>
          </div>
          <div class="detail-summary-actions">
            <el-button
              v-if="canArticleWrite && canEditFromDetail(detailData.article.status)"
              size="small"
              type="primary"
              @click="emit('revision')"
            >
              编辑文章
            </el-button>
            <el-dropdown
              v-if="canStyleRender(detailData.article)"
              trigger="click"
              @command="emit('styleRender', String($event))"
            >
              <el-button size="small">
                样式渲染
                <el-icon class="el-icon--right"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="wechat">公众号</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-tag :type="statusTagType(detailData.article.status)">
              {{ statusLabel(detailData.article.status) }}
            </el-tag>
          </div>
        </div>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="文章ID">{{ detailData.article.id }}</el-descriptions-item>
          <el-descriptions-item label="项目">{{ detailData.project?.projectName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="文章类型">{{ articleTypeLabel(detailData.article.articleTypeCode) }}</el-descriptions-item>
          <el-descriptions-item label="发布渠道">{{ detailChannelLabel(detailData) }}</el-descriptions-item>
          <el-descriptions-item label="文章模板">{{ detailTemplateUsageLabel(detailData) }}</el-descriptions-item>
          <el-descriptions-item v-if="detailData.article.medicalIndustryCode" label="医疗行业">{{ detailData.article.medicalIndustryCode }}</el-descriptions-item>
          <el-descriptions-item v-if="detailData.article.medicalCategoryCode" label="医疗品类">{{ detailData.article.medicalCategoryCode }}</el-descriptions-item>
          <el-descriptions-item v-if="detailData.article.medicalChannelTier" label="医疗档位">
            {{ detailData.article.medicalChannelTier }}
          </el-descriptions-item>
          <el-descriptions-item v-if="detailData.article.complianceStatus" label="合规状态">
            <el-tag size="small" :type="medicalComplianceTag(detailData.article.complianceStatus)">
              {{ medicalComplianceLabel(detailData.article.complianceStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item v-if="detailData.article.medicalChannelTier === 'official_site'" label="发布确认">
            <div class="medical-review-line">
              <el-tag size="small" :type="medicalReviewTag(detailData.article.publishReviewStatus)">
                {{ medicalReviewLabel(detailData.article.publishReviewStatus) }}
              </el-tag>
              <el-button
                v-if="canReviewMedicalPublish(detailData.article)"
                link
                type="warning"
                @click="emit('medicalPublishReview', 'approve')"
              >
                法务通过
              </el-button>
              <el-button
                v-if="canReviewMedicalPublish(detailData.article)"
                link
                type="danger"
                @click="emit('medicalPublishReview', 'reject')"
              >
                驳回
              </el-button>
            </div>
          </el-descriptions-item>
          <el-descriptions-item v-if="detailData.article.medicalAdReviewNo" label="广告审查号" :span="2">
            {{ detailData.article.medicalAdReviewNo }}
          </el-descriptions-item>
          <el-descriptions-item label="文章主题" :span="2">{{ detailTopic(detailData) || '-' }}</el-descriptions-item>
          <el-descriptions-item v-if="riskWordHits(detailData.article).length" label="风险词" :span="2">
            <div class="risk-word-list">
              <el-tag
                v-for="hit in riskWordHits(detailData.article)"
                :key="`${hit.severity}-${hit.source}-${hit.word}`"
                size="small"
                :type="hit.severity === 'block' ? 'danger' : 'warning'"
                effect="light"
              >
                {{ riskSeverityLabel(hit.severity) }} · {{ riskSourceLabel(hit.source) }}: {{ hit.word }}
              </el-tag>
            </div>
          </el-descriptions-item>
        </el-descriptions>
        <div v-if="coverImageUrl" class="detail-cover-panel">
          <div class="detail-cover-copy">
            <span>文章封面</span>
            <strong>{{ detailData.article.title || '封面图片' }}</strong>
          </div>
          <a :href="coverImageUrl" target="_blank" rel="noreferrer">
            <img :src="coverImageUrl" :alt="detailData.article.title || '文章封面'" loading="lazy" />
          </a>
        </div>
      </div>

      <div v-if="detailData.batchGenerationTask" class="detail-section-panel">
        <div class="detail-header trace-header">
          <h4 class="detail-title">批量生成追溯</h4>
          <el-tag size="small" :type="batchTaskStatusTag(detailData.batchGenerationTask.status)">
            {{ batchTaskStatusLabel(detailData.batchGenerationTask.status) }}
          </el-tag>
        </div>
        <div class="trace-body">
          <el-descriptions :column="3" border>
            <el-descriptions-item label="任务ID">{{ detailData.batchGenerationTask.id }}</el-descriptions-item>
            <el-descriptions-item label="批次ID">{{ detailData.batchGenerationTask.batchId }}</el-descriptions-item>
            <el-descriptions-item label="批次序号">{{ detailData.batchGenerationTask.articleIndexInBatch || '-' }}</el-descriptions-item>
            <el-descriptions-item label="生成状态">{{ batchTaskStatusLabel(detailData.batchGenerationTask.status) }}</el-descriptions-item>
            <el-descriptions-item label="质量状态">{{ detailData.batchGenerationTask.qualityStatus || '-' }}</el-descriptions-item>
            <el-descriptions-item label="重试次数">{{ detailData.batchGenerationTask.retryCount ?? 0 }}</el-descriptions-item>
            <el-descriptions-item v-if="detailData.batchGenerationTask.complianceStatus" label="合规状态">
              <el-tag size="small" :type="medicalComplianceTag(detailData.batchGenerationTask.complianceStatus)">
                {{ medicalComplianceLabel(detailData.batchGenerationTask.complianceStatus) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item v-if="detailData.batchGenerationTask.discardedArticleId" label="废弃文章ID">
              {{ detailData.batchGenerationTask.discardedArticleId }}
            </el-descriptions-item>
            <el-descriptions-item v-if="detailData.batchGenerationTask.errorMessage" label="失败原因" :span="3">
              {{ detailData.batchGenerationTask.errorMessage }}
            </el-descriptions-item>
            <el-descriptions-item v-if="detailData.batchGenerationTask.medicalIndustryCode" label="特殊行业">
              {{ detailData.batchGenerationTask.medicalIndustryCode }}
            </el-descriptions-item>
            <el-descriptions-item v-if="detailData.batchGenerationTask.medicalCategoryCode || detailData.batchGenerationTask.medicalCategoryName" label="医疗品类">
              {{ detailData.batchGenerationTask.medicalCategoryName || detailData.batchGenerationTask.medicalCategoryCode }}
            </el-descriptions-item>
            <el-descriptions-item v-if="detailData.batchGenerationTask.topicAngleId" label="选题角度ID">
              {{ detailData.batchGenerationTask.topicAngleId }}
            </el-descriptions-item>
            <el-descriptions-item v-if="detailData.batchGenerationTask.structureSkeleton" label="结构骨架" :span="3">
              {{ detailData.batchGenerationTask.structureSkeleton }}
            </el-descriptions-item>
            <el-descriptions-item v-if="detailData.batchGenerationTask.focus" label="重点方向" :span="3">
              {{ detailData.batchGenerationTask.focus }}
            </el-descriptions-item>
          </el-descriptions>
          <div v-if="batchComplianceIssues(detailData.batchGenerationTask).length" class="trace-issue-list">
            <div class="trace-subtitle">命中规则</div>
            <el-table :data="batchComplianceIssues(detailData.batchGenerationTask)" border>
              <el-table-column label="规则" min-width="160">
                <template #default="scope">{{ scope.row.ruleCode || scope.row.code || '-' }}</template>
              </el-table-column>
              <el-table-column label="类型" min-width="120">
                <template #default="scope">{{ scope.row.ruleType || scope.row.type || '-' }}</template>
              </el-table-column>
              <el-table-column label="命中内容" min-width="160">
                <template #default="scope">{{ scope.row.matchedText || scope.row.keyword || scope.row.word || '-' }}</template>
              </el-table-column>
              <el-table-column label="说明" min-width="220">
                <template #default="scope">{{ scope.row.reason || scope.row.message || scope.row.description || '-' }}</template>
              </el-table-column>
            </el-table>
          </div>
          <el-alert
            v-else-if="detailData.batchGenerationTask.complianceIssuesJson"
            type="warning"
            :closable="false"
            show-icon
            title="命中规则数据暂无法解析，可在合规命中日志中按文章或任务继续追溯。"
          />
        </div>
      </div>

      <div class="detail-section-panel">
        <h4 class="detail-title">版本记录</h4>
        <el-table :data="detailData.versions" border>
          <el-table-column prop="versionNo" label="版本" width="80" />
          <el-table-column prop="title" label="标题" min-width="220" />
          <el-table-column label="来源" width="130">
            <template #default="scope">{{ generatedByLabel(scope.row.generatedBy) }}</template>
          </el-table-column>
          <el-table-column label="时间" width="180">
            <template #default="scope">{{ formatDateTime(scope.row.createdAt) }}</template>
          </el-table-column>
        </el-table>
      </div>

      <div class="detail-section-panel detail-preview-panel">
        <div class="detail-header">
          <h4 class="detail-title">内容预览</h4>
          <el-radio-group v-model="viewMode" size="small">
            <el-radio-button label="preview">预览</el-radio-button>
            <el-radio-button label="markdown">Markdown</el-radio-button>
          </el-radio-group>
        </div>
        <el-input v-if="viewMode === 'markdown'" type="textarea" :rows="14" :model-value="markdown" readonly />
        <div v-else class="markdown-preview" v-html="html"></div>
      </div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ArrowDown } from '@element-plus/icons-vue'
import type { ArticleDetailResponse, ArticleDraft, BatchArticleGenerationTaskDetail } from '@/types'
import { formatDateTime } from '@/utils/format'

type TagType = 'success' | 'warning' | 'danger' | 'info' | 'primary'
type ViewMode = 'preview' | 'markdown'
type RiskWordHit = {
  word: string
  severity: string
  source: string
}
type ComplianceIssueRow = {
  ruleCode?: string
  code?: string
  ruleType?: string
  type?: string
  matchedText?: string
  keyword?: string
  word?: string
  reason?: string
  message?: string
  description?: string
}

const props = defineProps<{
  modelValue: boolean
  viewMode: ViewMode
  detailData: ArticleDetailResponse | null
  coverImageUrl: string
  markdown: string
  html: string
  canArticleWrite: boolean
  canEditFromDetail: (status: string) => boolean
  canStyleRender: (article: Pick<ArticleDraft, 'channelGroupCode' | 'channelSubCode'>) => boolean
  statusTagType: (status: string) => TagType
  statusLabel: (status: string) => string
  articleTypeLabel: (value?: string | null) => string
  detailChannelLabel: (detail: ArticleDetailResponse) => string
  detailTemplateUsageLabel: (detail: ArticleDetailResponse) => string
  detailTopic: (detail: ArticleDetailResponse) => string
  riskWordHits: (article?: Pick<ArticleDraft, 'riskWordsJson'> | null) => RiskWordHit[]
  riskSeverityLabel: (severity?: string | null) => string
  riskSourceLabel: (source?: string | null) => string
  generatedByLabel: (value?: string | null) => string
  medicalComplianceLabel: (value?: string | null) => string
  medicalComplianceTag: (value?: string | null) => TagType
  medicalReviewLabel: (value?: string | null) => string
  medicalReviewTag: (value?: string | null) => TagType
  canReviewMedicalPublish: (article?: ArticleDraft | null) => boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'update:viewMode': [value: ViewMode]
  revision: []
  styleRender: [command: string]
  medicalPublishReview: [action: 'approve' | 'reject']
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const viewMode = computed({
  get: () => props.viewMode,
  set: (value) => emit('update:viewMode', value),
})

function batchTaskStatusLabel(value?: string | null) {
  const labels: Record<string, string> = {
    pending: '待生成',
    running: '生成中',
    success: '成功',
    failed: '失败',
    skipped: '已跳过',
    discarded_compliance_failed: '合规失败已废弃',
  }
  return value ? (labels[value] || value) : '-'
}

function batchTaskStatusTag(value?: string | null): TagType {
  if (value === 'success') return 'success'
  if (value === 'running' || value === 'pending') return 'warning'
  if (value === 'failed' || value === 'discarded_compliance_failed') return 'danger'
  return 'info'
}

function batchComplianceIssues(task?: BatchArticleGenerationTaskDetail | null): ComplianceIssueRow[] {
  if (!task?.complianceIssuesJson) return []
  try {
    const parsed = JSON.parse(task.complianceIssuesJson)
    if (Array.isArray(parsed)) return parsed.filter((item) => item && typeof item === 'object')
    if (Array.isArray(parsed?.issues)) return parsed.issues.filter((item: unknown) => item && typeof item === 'object')
    if (Array.isArray(parsed?.hits)) return parsed.hits.filter((item: unknown) => item && typeof item === 'object')
  } catch (err) {
    console.warn('Failed to parse compliance issues json', err)
  }
  return []
}
</script>

<style scoped>
.detail-wrap {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.content-detail-drawer :deep(.el-drawer__header) {
  margin-bottom: 0;
  padding: 18px 22px;
  border-bottom: 1px solid #e2e8f0;
  background: linear-gradient(135deg, #f8fbff, #eff6ff 58%, #ecfdf5);
}

.content-detail-drawer :deep(.el-drawer__title) {
  color: #0f172a;
  font-size: 18px;
  font-weight: 800;
}

.content-detail-drawer :deep(.el-drawer__body) {
  padding: 18px 22px 24px;
  background: #f7fbff;
}

.detail-summary-panel,
.detail-section-panel {
  border: 1px solid #dbeafe;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 14px 34px rgba(15, 23, 42, 0.06);
  overflow: hidden;
}

.detail-summary-panel {
  padding: 16px;
  background:
    radial-gradient(circle at top right, rgba(37, 99, 235, 0.08), transparent 30%),
    linear-gradient(135deg, #ffffff, #f8fbff);
}

.detail-summary-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 14px;
}

.detail-summary-actions {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  gap: 10px;
}

.detail-summary-head h3 {
  margin: 5px 0 0;
  color: #0f172a;
  font-size: 20px;
  font-weight: 800;
  line-height: 1.35;
}

.detail-kicker {
  color: #2563eb;
  font-size: 12px;
  font-weight: 700;
}

.detail-cover-panel {
  display: grid;
  grid-template-columns: minmax(180px, 1fr) minmax(180px, 280px);
  align-items: center;
  gap: 16px;
  margin-top: 14px;
  padding: 14px;
  border: 1px solid #bfdbfe;
  border-radius: 12px;
  background: rgba(239, 246, 255, 0.72);
}

.detail-cover-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 6px;
}

.detail-cover-copy span {
  color: #2563eb;
  font-size: 12px;
  font-weight: 800;
}

.detail-cover-copy strong {
  overflow: hidden;
  color: #0f172a;
  font-size: 15px;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail-cover-panel a {
  display: block;
  overflow: hidden;
  border-radius: 10px;
  background: #e2e8f0;
}

.medical-review-line {
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.detail-cover-panel img {
  display: block;
  width: 100%;
  aspect-ratio: 16 / 9;
  object-fit: cover;
}

@media (max-width: 760px) {
  .detail-cover-panel {
    grid-template-columns: 1fr;
  }
}

.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.detail-title {
  margin: 0;
  padding: 14px 16px;
  color: #0f172a;
  font-size: 15px;
  font-weight: 800;
}

.detail-section-panel > .el-table {
  border-top: 1px solid #e2e8f0;
}

.detail-preview-panel {
  padding-bottom: 16px;
}

.detail-preview-panel .detail-header {
  padding-right: 16px;
  border-bottom: 1px solid #e2e8f0;
}

.trace-header {
  padding-right: 16px;
  border-bottom: 1px solid #e2e8f0;
}

.trace-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 16px;
}

.trace-issue-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.trace-subtitle {
  color: #334155;
  font-size: 13px;
  font-weight: 800;
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
</style>
