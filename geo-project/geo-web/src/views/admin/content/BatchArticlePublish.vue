<template>
  <div class="batch-publish-page">
    <header class="page-head">
      <div>
        <h2 class="page-title">批量发布文章</h2>
        <p class="page-subtitle">按发布平台自动归类文章，同一平台内按顺序提交，避免同一时刻并发发布</p>
      </div>
      <div class="head-actions">
        <el-button @click="goBack">返回列表</el-button>
        <el-button type="primary" :loading="submitting" :disabled="!canSubmit" @click="submitPublish">确认发布</el-button>
      </div>
    </header>

    <div class="stat-row">
      <div class="stat-item">
        <span class="stat-label">可发布文章</span>
        <span class="stat-value">{{ validItems.length }}</span>
        <span class="stat-unit">篇</span>
      </div>
      <div class="stat-divider"></div>
      <div class="stat-item">
        <span class="stat-label">涉及平台</span>
        <span class="stat-value">{{ platformCount }}</span>
        <span class="stat-unit">个</span>
      </div>
      <template v-if="invalidItems.length">
        <div class="stat-divider"></div>
        <div class="stat-item warn">
          <span class="stat-label">不可发布</span>
          <span class="stat-value">{{ invalidItems.length }}</span>
          <span class="stat-unit">篇</span>
        </div>
      </template>
    </div>

    <section class="card config-card">
      <div class="card-header">
        <div class="card-title-wrap">
          <span class="card-dot"></span>
          <h3 class="card-title">发布任务配置</h3>
        </div>
      </div>
      <div class="card-body">
        <el-form label-position="top" class="config-form">
          <el-form-item label="发布时间" class="form-item">
            <el-radio-group v-model="publishMode">
              <el-radio-button label="now">立刻发布</el-radio-button>
              <el-radio-button label="scheduled">定时发布</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="publishMode === 'scheduled'" label="定时开始时间" class="form-item">
            <el-date-picker
              v-model="scheduledAt"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              placeholder="选择开始发布时间"
              class="scheduled-picker"
            />
            <div class="form-tip">定时发布需要后端批量发布任务调度承接，当前页面先保留配置入口</div>
          </el-form-item>
          <div class="inline-fields">
            <el-form-item label="同平台发布间隔" class="form-item">
              <div class="interval-control">
                <el-input-number v-model="intervalValue" :min="1" :max="1440" controls-position="right" />
                <el-select v-model="intervalUnit" class="interval-unit">
                  <el-option label="分钟" value="minutes" />
                  <el-option label="小时" value="hours" />
                </el-select>
              </div>
              <div class="form-tip">同一平台多篇文章时，按该间隔规划发布时间</div>
            </el-form-item>
            <el-form-item label="同平台并发上限" class="form-item">
              <el-input-number v-model="platformConcurrency" :min="1" :max="1" controls-position="right" />
              <div class="form-tip">当前固定为 1，确保同平台文章不会同时发布</div>
            </el-form-item>
          </div>
        </el-form>
      </div>
    </section>

    <DataState :loading="loading" :empty="!loading && !articleItems.length" empty-text="未找到可发布文章">
      <el-alert
        v-if="invalidItems.length"
        type="warning"
        show-icon
        :closable="false"
        class="mb-3"
        title="以下文章无法自动发布"
        :description="invalidItems.map((item) => `${item.detail.article.title || '未命名文章'}：${item.invalidReason}`).join('；')"
      />

      <div class="platform-groups">
        <section v-for="group in publishGroups" :key="group.platformKey" class="card platform-card">
          <div class="card-header">
            <div class="platform-info">
              <div class="platform-icon" :class="group.platformKey === 'agent_site' ? 'agent' : 'industry'">
                {{ group.platformName.slice(0, 1) }}
              </div>
              <div>
                <div class="platform-title">{{ group.platformName }}</div>
                <div class="platform-meta">
                  <span>{{ group.items.length }} 篇文章</span>
                  <span class="dot-sep">·</span>
                  <span>{{ group.executorLabel }}</span>
                </div>
              </div>
            </div>
            <span class="pill-tag success">
              <span class="dot"></span>
              支持自动发布
            </span>
          </div>

          <div v-if="group.platformKey === 'industry_site'" class="target-row">
            <div class="target-info">
              <div class="target-label">行业资讯站目标</div>
              <div class="target-desc">可手动指定本次发布站点；不选择时，系统会按文章所属品牌配置的资讯站唯一标识自动匹配</div>
            </div>
            <el-select v-model="industryTargetSiteId" clearable placeholder="自动匹配或手动选择" class="target-select">
              <el-option
                v-for="site in activeIndustrySites"
                :key="site.id"
                :label="`${site.siteName}（${site.siteCode}）`"
                :value="site.id"
              />
            </el-select>
          </div>

          <div class="table-wrap">
            <el-table :data="group.items" class="article-table">
              <el-table-column label="文章标题" min-width="260" show-overflow-tooltip>
                <template #default="{ row }">
                  <span class="article-title">{{ row.detail.article.title || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="文章主题" min-width="180" show-overflow-tooltip>
                <template #default="{ row }">
                  <span class="article-topic">{{ detailTopic(row.detail) || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="平台风格" width="130">
                <template #default="{ row }">
                  <span class="style-chip">{{ contentStyleLabel(row.contentStyle) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="计划时间" width="180">
                <template #default="{ row, $index }">
                  <span class="plan-time" :class="{ pending: publishMode === 'scheduled' && !scheduledAt && !row.plannedAt }">
                    {{ plannedTimeLabel(row, $index) }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="提交状态" width="160">
                <template #default="{ row }">
                  <span class="status-pill" :class="resultStatusClass(row.resultStatus)">
                    <span class="status-dot"></span>
                    <span>{{ resultStatusLabel(row.resultStatus) }}</span>
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="结果" min-width="180" show-overflow-tooltip>
                <template #default="{ row }">
                  <span class="result-text">{{ row.resultMessage || '-' }}</span>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </section>
      </div>
    </DataState>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import DataState from '@/components/ui/DataState.vue'
import type { ArticleDetailResponse, PublishSite } from '@/types'
import {
  getContentArticleDetail,
  submitBatchArticlePublish,
  type BatchArticlePublishResponse,
} from '@/api/content'
import { getPublishSites } from '@/api/publishSite'
import { formatDateTime } from '@/utils/format'

type PublishPlatformKey = 'agent_site' | 'industry_site'
type PublishResultStatus = 'pending' | 'running' | 'success' | 'failed'

interface BatchPublishItem {
  detail: ArticleDetailResponse
  contentStyle: string
  platformKey: PublishPlatformKey | null
  platformName: string
  invalidReason?: string
  resultStatus: PublishResultStatus
  resultMessage?: string
  plannedAt?: string | null
  distributionTaskId?: number | null
}

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const submitting = ref(false)
const articleItems = ref<BatchPublishItem[]>([])
const publishSites = ref<PublishSite[]>([])
const publishMode = ref<'now' | 'scheduled'>('now')
const scheduledAt = ref('')
const intervalValue = ref(30)
const intervalUnit = ref<'minutes' | 'hours'>('minutes')
const platformConcurrency = ref(1)
const industryTargetSiteId = ref<number | null>(null)

const validItems = computed(() => articleItems.value.filter((item) => item.platformKey && !item.invalidReason))
const invalidItems = computed(() => articleItems.value.filter((item) => item.invalidReason))
const platformCount = computed(() => new Set(validItems.value.map((item) => item.platformKey).filter(Boolean)).size)
const activeIndustrySites = computed(() => publishSites.value.filter((site) => {
  const integrationMethod = site.integrationMethod || ''
  return site.status === 'active'
    && integrationMethod !== 'brand_geo_site'
    && site.siteCode !== 'agent_official_site'
}))
const publishGroups = computed(() => {
  const grouped = new Map<PublishPlatformKey, BatchPublishItem[]>()
  for (const item of validItems.value) {
    if (!item.platformKey) continue
    grouped.set(item.platformKey, [...(grouped.get(item.platformKey) || []), item])
  }
  return Array.from(grouped.entries()).map(([platformKey, items]) => ({
    platformKey,
    platformName: platformKey === 'agent_site' ? 'Agent 官网' : '行业资讯站',
    executorLabel: platformKey === 'agent_site' ? 'Agent 官网发布器' : '行业资讯站发布器',
    items,
  }))
})
const canSubmit = computed(() => {
  if (submitting.value || loading.value || !validItems.value.length || invalidItems.value.length) return false
  if (publishMode.value === 'scheduled' && !scheduledAt.value) return false
  return true
})

onMounted(loadPage)

async function loadPage() {
  loading.value = true
  try {
    const ids = parseIds()
    if (!ids.length) {
      articleItems.value = []
      ElMessage.warning('缺少待发布文章')
      return
    }
    const [detailResponses, siteResponse] = await Promise.all([
      Promise.all(ids.map((id) => getContentArticleDetail(id).then((res) => res.data.data))),
      getPublishSites({ status: 'active' }).then((res) => res.data.data),
    ])
    publishSites.value = siteResponse || []
    const firstIndustrySite = activeIndustrySites.value[0]
    industryTargetSiteId.value = firstIndustrySite?.id || null
    articleItems.value = detailResponses.map(toBatchPublishItem)
  } catch {
    ElMessage.error('加载批量发布数据失败')
    articleItems.value = []
  } finally {
    loading.value = false
  }
}

function parseIds() {
  const raw = Array.isArray(route.query.ids) ? route.query.ids.join(',') : route.query.ids || ''
  return String(raw)
    .split(',')
    .map((item) => Number(item.trim()))
    .filter((id) => Number.isFinite(id) && id > 0)
}

function toBatchPublishItem(detail: ArticleDetailResponse): BatchPublishItem {
  const contentStyle = detailContentStyle(detail)
  const platform = resolvePlatform(contentStyle)
  return {
    detail,
    contentStyle,
    platformKey: platform.platformKey,
    platformName: platform.platformName,
    invalidReason: platform.invalidReason,
    resultStatus: 'pending',
  }
}

function detailContentStyle(detail: ArticleDetailResponse) {
  return detail.batchGenerationTask?.contentStyle || detail.article.contentStyle || ''
}

function detailTopic(detail: ArticleDetailResponse) {
  return detail.batchGenerationTask?.topic || detail.article.topic || ''
}

function resolvePlatform(contentStyle: string): {
  platformKey: PublishPlatformKey | null
  platformName: string
  invalidReason?: string
} {
  if (contentStyle === 'agent_site_article' || contentStyle === 'linkedin') {
    return { platformKey: 'agent_site', platformName: 'Agent 官网' }
  }
  if (contentStyle === 'industry_site') {
    return { platformKey: 'industry_site', platformName: '行业资讯站' }
  }
  const blocked: Record<string, string> = {
    toutiao: '今日头条不允许自动发布',
    wechat: '公众号不允许自动发布',
    zhihu: '知乎不允许自动发布',
    douyin_image_text: '抖音图文不允许自动发布',
    authority_media: '权威媒体不允许自动发布',
    forum: '论坛发布执行器暂未接入',
  }
  return {
    platformKey: null,
    platformName: contentStyleLabel(contentStyle),
    invalidReason: blocked[contentStyle] || '文章未绑定可自动发布的平台风格',
  }
}

function contentStyleLabel(v?: string | null) {
  if (!v) return '-'
  const map: Record<string, string> = {
    toutiao: '今日头条',
    wechat: '公众号',
    zhihu: '知乎',
    douyin_image_text: '抖音图文',
    linkedin: '领英风格',
    agent_site_article: 'Agent 官网文章',
    industry_site: '行业资讯站',
    authority_media: '权威媒体',
    forum: '论坛',
    xiaohongshu: '小红书',
  }
  return map[v] || v
}

function plannedTimeLabel(row: BatchPublishItem, index: number) {
  if (row.plannedAt) return formatDateTime(row.plannedAt)
  if (publishMode.value === 'now') return index === 0 ? '立刻提交' : `第 ${index + 1} 顺位提交`
  if (!scheduledAt.value) return '待设置'
  const base = new Date(scheduledAt.value.replace(/-/g, '/')).getTime()
  const intervalMs = intervalUnit.value === 'hours' ? intervalValue.value * 60 * 60 * 1000 : intervalValue.value * 60 * 1000
  return formatDateTime(new Date(base + index * intervalMs).toISOString())
}

async function submitPublish() {
  if (!canSubmit.value) {
    ElMessage.warning('请先完成发布配置')
    return
  }
  try {
    await ElMessageBox.confirm('确认按当前分组提交自动发布？同一平台内将按顺序逐篇提交。', '确认发布', {
      type: 'warning',
      confirmButtonText: '确认发布',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  submitting.value = true
  try {
    validItems.value.forEach((item) => {
      item.resultStatus = 'running'
      item.resultMessage = publishMode.value === 'scheduled' ? '计划创建中' : '提交中'
    })
    const { data } = await submitBatchArticlePublish({
      articleIds: validItems.value.map((item) => item.detail.article.id),
      publishMode: publishMode.value,
      scheduledAt: publishMode.value === 'scheduled' ? scheduledAt.value : undefined,
      intervalMinutes: intervalUnit.value === 'hours' ? intervalValue.value * 60 : intervalValue.value,
      platformConcurrency: platformConcurrency.value,
      industrySiteId: industryTargetSiteId.value || undefined,
    })
    applyPublishResponse(data.data)
    ElMessage.success(publishMode.value === 'scheduled' ? '定时发布任务已创建' : '批量发布提交完成')
  } finally {
    submitting.value = false
  }
}

function applyPublishResponse(response: BatchArticlePublishResponse) {
  const itemMap = new Map(response.items.map((item) => [item.articleId, item]))
  validItems.value.forEach((item) => {
    const backendItem = itemMap.get(item.detail.article.id)
    if (!backendItem) return
    item.plannedAt = backendItem.plannedAt
    item.distributionTaskId = backendItem.distributionTaskId || null
    if (backendItem.status === 'success') {
      item.resultStatus = 'success'
      item.resultMessage = backendItem.distributionTaskId ? `分发任务 #${backendItem.distributionTaskId}` : '已提交'
    } else if (backendItem.status === 'failed') {
      item.resultStatus = 'failed'
      item.resultMessage = backendItem.errorMessage || '提交失败'
    } else if (backendItem.status === 'running') {
      item.resultStatus = 'running'
      item.resultMessage = '执行中'
    } else {
      item.resultStatus = 'pending'
      item.resultMessage = response.publishMode === 'scheduled' ? `任务 #${response.jobId} 待执行` : '待执行'
    }
  })
}

function resultStatusLabel(status: PublishResultStatus) {
  const map: Record<PublishResultStatus, string> = {
    pending: '待提交',
    running: '提交中',
    success: '已提交',
    failed: '提交失败',
  }
  return map[status]
}

function resultStatusClass(status: PublishResultStatus) {
  return {
    pending: 'status-pending',
    running: 'status-running',
    success: 'status-success',
    failed: 'status-failed',
  }[status]
}

function goBack() {
  router.push('/admin/content/execution')
}
</script>

<style scoped>
.batch-publish-page {
  --bp-bg: #f6f8fb;
  --bp-card-bg: #ffffff;
  --bp-border: #eef0f4;
  --bp-border-strong: #e2e6ee;
  --bp-text: #0f172a;
  --bp-text-secondary: #475569;
  --bp-text-muted: #94a3b8;
  --bp-primary: #3b6df5;
  --bp-primary-hover: #2f5cdb;
  --bp-success: #10b981;
  --bp-success-soft: #ecfdf5;
  --bp-warning: #f59e0b;
  --bp-warning-soft: #fffbeb;
  --bp-danger: #ef4444;
  --bp-danger-soft: #fef2f2;
  --bp-radius-sm: 8px;
  --bp-radius: 12px;
  --bp-shadow-sm: 0 1px 2px rgba(15, 23, 42, 0.04);
  --bp-font: Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC",
    "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
  --bp-mono: "JetBrains Mono", ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;

  max-width: 1400px;
  margin: 0 auto;
  padding: 28px 32px 48px;
  color: var(--bp-text);
  font-family: var(--bp-font);
  font-size: 14px;
  line-height: 1.5;
}

.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 20px;
}

.page-title {
  margin: 0 0 6px;
  color: var(--bp-text);
  font-size: 22px;
  font-weight: 600;
  line-height: 1.3;
}

.page-subtitle {
  margin: 0;
  color: var(--bp-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.head-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.head-actions :deep(.el-button) {
  height: 36px;
  padding: 0 18px;
  border-radius: var(--bp-radius-sm);
  font-size: 13px;
  font-weight: 500;
}

.head-actions :deep(.el-button--primary) {
  --el-button-bg-color: var(--bp-primary);
  --el-button-border-color: var(--bp-primary);
  --el-button-hover-bg-color: var(--bp-primary-hover);
  --el-button-hover-border-color: var(--bp-primary-hover);
  box-shadow: 0 2px 4px rgba(59, 109, 245, 0.18);
}

.stat-row {
  display: flex;
  align-items: center;
  gap: 28px;
  padding: 14px 20px;
  margin-bottom: 16px;
  background: var(--bp-card-bg);
  border: 1px solid var(--bp-border);
  border-radius: var(--bp-radius);
  box-shadow: var(--bp-shadow-sm);
}

.stat-item {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.stat-label {
  color: var(--bp-text-muted);
  font-size: 13px;
}

.stat-value {
  color: var(--bp-text);
  font-size: 20px;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}

.stat-item.warn .stat-value {
  color: var(--bp-warning);
}

.stat-unit {
  color: var(--bp-text-muted);
  font-size: 12px;
}

.stat-divider {
  width: 1px;
  height: 20px;
  background: var(--bp-border);
}

.card {
  margin-bottom: 16px;
  overflow: hidden;
  background: var(--bp-card-bg);
  border: 1px solid var(--bp-border);
  border-radius: var(--bp-radius);
  box-shadow: var(--bp-shadow-sm);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 20px;
  background: linear-gradient(to bottom, #fbfcfd 0%, #ffffff 100%);
  border-bottom: 1px solid var(--bp-border);
}

.card-title-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
}

.card-dot {
  width: 4px;
  height: 16px;
  background: var(--bp-primary);
  border-radius: 2px;
}

.card-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.card-body {
  padding: 20px;
}

.config-form {
  max-width: 820px;
}

.form-item {
  margin-bottom: 18px;
}

.form-item:last-child {
  margin-bottom: 0;
}

.config-form :deep(.el-form-item__label) {
  margin-bottom: 8px;
  color: var(--bp-text-secondary);
  font-size: 13px;
  font-weight: 500;
  line-height: 1.5;
}

.config-form :deep(.el-radio-group) {
  overflow: hidden;
  border: 1px solid #d4d8e0;
  border-radius: var(--bp-radius-sm);
}

.config-form :deep(.el-radio-button__inner) {
  height: 34px;
  padding: 8px 18px;
  color: var(--bp-text-secondary);
  background: #fff;
  border: 0;
  border-left: 1px solid #d4d8e0;
  border-radius: 0;
  box-shadow: none;
  font-size: 13px;
  line-height: 18px;
}

.config-form :deep(.el-radio-button:first-child .el-radio-button__inner) {
  border-left: 0;
}

.config-form :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  color: #fff;
  background: var(--bp-primary);
  border-color: var(--bp-primary);
  box-shadow: none;
  font-weight: 500;
}

.scheduled-picker {
  width: 320px;
}

.scheduled-picker :deep(.el-input__wrapper),
.interval-control :deep(.el-input__wrapper),
.interval-control :deep(.el-select__wrapper),
.config-form :deep(.el-input-number .el-input__wrapper) {
  min-height: 36px;
  border-radius: var(--bp-radius-sm);
  box-shadow: 0 0 0 1px #d4d8e0 inset;
}

.scheduled-picker :deep(.el-input__wrapper:hover),
.interval-control :deep(.el-input__wrapper:hover),
.interval-control :deep(.el-select__wrapper:hover),
.config-form :deep(.el-input-number .el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--bp-primary) inset;
}

.inline-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(240px, 360px));
  gap: 24px;
}

.interval-control {
  display: flex;
  gap: 8px;
}

.interval-control :deep(.el-input-number) {
  flex: 1;
  min-width: 0;
}

.interval-unit {
  width: 90px;
}

.form-tip {
  margin-top: 6px;
  color: var(--bp-text-muted);
  font-size: 12px;
  line-height: 1.5;
}

.platform-groups {
  display: grid;
  gap: 16px;
}

.platform-info {
  display: flex;
  align-items: center;
  gap: 14px;
}

.platform-icon {
  display: flex;
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  color: #fff;
  border-radius: 10px;
  font-size: 16px;
  font-weight: 600;
}

.platform-icon.industry {
  background: linear-gradient(135deg, #3b6df5 0%, #6366f1 100%);
}

.platform-icon.agent {
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
}

.platform-title {
  color: var(--bp-text);
  font-size: 15px;
  font-weight: 600;
  line-height: 1.4;
}

.platform-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 2px;
  color: var(--bp-text-muted);
  font-size: 12px;
}

.dot-sep {
  color: var(--bp-border-strong);
}

.pill-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
  line-height: 1.4;
}

.pill-tag.success {
  color: var(--bp-success);
  background: var(--bp-success-soft);
  border: 1px solid #a7f3d0;
}

.pill-tag .dot {
  width: 6px;
  height: 6px;
  background: currentColor;
  border-radius: 50%;
}

.target-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 14px 20px;
  background: #fafbfc;
  border-bottom: 1px solid var(--bp-border);
}

.target-info {
  flex: 1;
  min-width: 0;
}

.target-label {
  color: var(--bp-text);
  font-size: 13px;
  font-weight: 600;
}

.target-desc {
  margin-top: 2px;
  color: var(--bp-text-muted);
  font-size: 12px;
  line-height: 1.5;
}

.target-select {
  width: 280px;
  flex-shrink: 0;
}

.target-select :deep(.el-select__wrapper) {
  min-height: 36px;
  border-radius: var(--bp-radius-sm);
  box-shadow: 0 0 0 1px #d4d8e0 inset;
}

.table-wrap {
  padding: 4px;
}

.article-table {
  --el-table-border-color: var(--bp-border);
  --el-table-header-bg-color: #fafbfc;
  --el-table-row-hover-bg-color: #fafbff;
  width: 100%;
}

.article-table :deep(.el-table__inner-wrapper::before),
.article-table :deep(.el-table__border-left-patch),
.article-table :deep(.el-table__border-right-patch) {
  display: none;
}

.article-table :deep(th.el-table__cell) {
  height: 44px;
  background: #fafbfc;
  border-bottom: 1px solid var(--bp-border);
}

.article-table :deep(th.el-table__cell .cell) {
  color: var(--bp-text-secondary);
  font-size: 12px;
  font-weight: 500;
  line-height: 20px;
}

.article-table :deep(td.el-table__cell) {
  padding: 14px 0;
  border-bottom: 1px solid var(--bp-border);
}

.article-table :deep(.cell) {
  padding: 0 16px;
  font-size: 13px;
  line-height: 1.5;
}

.article-title {
  display: block;
  overflow: hidden;
  color: var(--bp-text);
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.article-topic {
  display: block;
  overflow: hidden;
  color: var(--bp-text-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.style-chip {
  display: inline-block;
  padding: 3px 10px;
  color: var(--bp-text-secondary);
  background: #f1f3f7;
  border-radius: 12px;
  font-size: 12px;
}

.plan-time {
  color: var(--bp-text-secondary);
  font-family: var(--bp-mono);
  font-size: 12px;
}

.plan-time.pending {
  color: var(--bp-warning);
  font-family: var(--bp-font);
}

.result-text {
  color: var(--bp-text-muted);
  font-size: 12px;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px 3px 8px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
  line-height: 1.4;
}

.status-dot {
  width: 6px;
  height: 6px;
  background: currentColor;
  border-radius: 50%;
}

.status-pending {
  color: #64748b;
  background: #f1f5f9;
}

.status-running {
  color: var(--bp-warning);
  background: var(--bp-warning-soft);
}

.status-running .status-dot {
  animation: pulse 1.4s ease-in-out infinite;
}

.status-success {
  color: var(--bp-success);
  background: var(--bp-success-soft);
}

.status-failed {
  color: var(--bp-danger);
  background: var(--bp-danger-soft);
}

@keyframes pulse {
  0%,
  100% {
    opacity: 1;
    transform: scale(1);
  }

  50% {
    opacity: 0.5;
    transform: scale(0.85);
  }
}

.mb-3 {
  margin-bottom: 16px;
}

@media (max-width: 900px) {
  .batch-publish-page {
    padding: 20px 16px;
  }

  .page-head {
    flex-direction: column;
    align-items: stretch;
  }

  .head-actions {
    justify-content: flex-end;
  }

  .stat-row {
    flex-wrap: wrap;
    gap: 16px;
  }

  .inline-fields {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .target-row {
    flex-direction: column;
    align-items: stretch;
  }

  .target-select,
  .scheduled-picker {
    width: 100%;
  }
}
</style>
