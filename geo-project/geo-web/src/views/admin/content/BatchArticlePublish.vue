<template>
  <div class="batch-publish-page">
    <div class="page-head">
      <div>
        <p class="eyebrow">content.publish.batch</p>
        <h2>批量发布文章</h2>
        <p class="subtitle">按发布平台自动归类文章，同一平台内按顺序提交，避免同一时刻并发发布。</p>
      </div>
      <div class="head-actions">
        <el-button @click="goBack">返回列表</el-button>
        <el-button type="primary" :loading="submitting" :disabled="!canSubmit" @click="submitPublish">确认发布</el-button>
      </div>
    </div>

    <el-card shadow="never" class="config-card">
      <div class="section-title">
        <span>发布任务</span>
        <small>当前共 {{ validItems.length }} 篇可发布文章</small>
      </div>
      <el-form label-position="top" class="config-form">
        <el-form-item label="发布时间">
          <el-radio-group v-model="publishMode">
            <el-radio-button label="now">立刻发布</el-radio-button>
            <el-radio-button label="scheduled">定时发布</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="publishMode === 'scheduled'" label="定时开始时间">
          <el-date-picker
            v-model="scheduledAt"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择开始发布时间"
            style="width: 280px"
          />
          <div class="form-tip">定时发布需要后端批量发布任务调度承接，当前页面先保留配置入口。</div>
        </el-form-item>
        <div class="inline-fields">
          <el-form-item label="同平台发布间隔">
            <div class="interval-control">
              <el-input-number v-model="intervalValue" :min="1" :max="1440" controls-position="right" />
              <el-select v-model="intervalUnit" style="width: 96px">
                <el-option label="分钟" value="minutes" />
                <el-option label="小时" value="hours" />
              </el-select>
            </div>
            <div class="form-tip">同一平台存在多篇文章时，按该间隔规划发布时间。</div>
          </el-form-item>
          <el-form-item label="同平台并发上限">
            <el-input-number v-model="platformConcurrency" :min="1" :max="1" controls-position="right" />
            <div class="form-tip">当前固定为 1，确保同平台文章不会同时发布。</div>
          </el-form-item>
        </div>
      </el-form>
    </el-card>

    <DataState :loading="loading" :empty="!loading && !articleItems.length" empty-text="未找到可发布文章">
      <el-alert
        v-if="invalidItems.length"
        type="warning"
        show-icon
        :closable="false"
        class="mb-3"
        title="以下文章无法自动发布"
        :description="invalidItems.map((item) => `#${item.detail.article.id} ${item.detail.article.title || ''}：${item.invalidReason}`).join('；')"
      />

      <div class="platform-groups">
        <el-card v-for="group in publishGroups" :key="group.platformKey" shadow="never" class="platform-card">
          <template #header>
            <div class="platform-header">
              <div>
                <div class="platform-title">{{ group.platformName }}</div>
                <div class="platform-meta">{{ group.items.length }} 篇文章 · {{ group.executorLabel }}</div>
              </div>
              <el-tag type="success">支持自动发布</el-tag>
            </div>
          </template>

          <div v-if="group.platformKey === 'industry_site'" class="target-row">
            <div>
              <strong>行业资讯站目标</strong>
              <p>可手动指定本次发布站点；不选择时，系统会按文章所属品牌配置的资讯站唯一标识自动匹配。</p>
            </div>
            <el-select v-model="industryTargetSiteId" clearable placeholder="自动匹配或手动选择" style="width: 280px">
              <el-option
                v-for="site in activeIndustrySites"
                :key="site.id"
                :label="`${site.siteName}（${site.siteCode}）`"
                :value="site.id"
              />
            </el-select>
          </div>

          <el-table :data="group.items" border>
            <el-table-column label="文章ID" width="90">
              <template #default="{ row }">#{{ row.detail.article.id }}</template>
            </el-table-column>
            <el-table-column label="文章标题" min-width="260" show-overflow-tooltip>
              <template #default="{ row }">{{ row.detail.article.title || '-' }}</template>
            </el-table-column>
            <el-table-column label="文章主题" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ row.detail.batchGenerationTask?.topic || '-' }}</template>
            </el-table-column>
            <el-table-column label="平台风格" width="130">
              <template #default="{ row }">{{ contentStyleLabel(row.contentStyle) }}</template>
            </el-table-column>
            <el-table-column label="计划时间" width="180">
              <template #default="{ row, $index }">{{ plannedTimeLabel(row, $index) }}</template>
            </el-table-column>
            <el-table-column label="提交状态" width="160">
              <template #default="{ row }">
                <el-tag :type="resultTagType(row.resultStatus)">{{ resultStatusLabel(row.resultStatus) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="结果" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ row.resultMessage || '-' }}</template>
            </el-table-column>
          </el-table>
        </el-card>
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
  const contentStyle = detail.batchGenerationTask?.contentStyle || ''
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

function resultTagType(status: PublishResultStatus): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'success') return 'success'
  if (status === 'running') return 'warning'
  if (status === 'failed') return 'danger'
  return 'info'
}

function goBack() {
  router.push('/admin/content/execution')
}
</script>

<style scoped>
.batch-publish-page {
  padding: 8px 0 24px;
}

.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 16px;
}

.page-head h2 {
  margin: 4px 0 6px;
  font-size: 24px;
  color: #111827;
}

.eyebrow {
  margin: 0;
  color: #6b7280;
  font-size: 12px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.subtitle {
  margin: 0;
  color: #6b7280;
  font-size: 14px;
}

.head-actions {
  display: flex;
  gap: 10px;
  flex-shrink: 0;
}

.config-card {
  margin-bottom: 16px;
}

.section-title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 16px;
  color: #111827;
  font-weight: 700;
}

.section-title small {
  color: #6b7280;
  font-weight: 400;
}

.config-form {
  max-width: 820px;
}

.inline-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(220px, 1fr));
  gap: 16px;
}

.interval-control {
  display: flex;
  gap: 10px;
}

.form-tip {
  margin-top: 6px;
  color: #8a94a6;
  font-size: 12px;
  line-height: 1.5;
}

.platform-groups {
  display: grid;
  gap: 16px;
}

.platform-card {
  border-radius: 8px;
}

.platform-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.platform-title {
  color: #111827;
  font-size: 16px;
  font-weight: 700;
}

.platform-meta {
  margin-top: 4px;
  color: #6b7280;
  font-size: 13px;
}

.target-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 14px 16px;
  margin-bottom: 14px;
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.target-row p {
  margin: 4px 0 0;
  color: #6b7280;
  font-size: 13px;
}

.mb-3 {
  margin-bottom: 16px;
}

@media (max-width: 768px) {
  .page-head,
  .target-row {
    flex-direction: column;
    align-items: stretch;
  }

  .head-actions {
    justify-content: flex-start;
  }

  .inline-fields {
    grid-template-columns: 1fr;
  }
}
</style>
