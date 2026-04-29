<template>
  <div class="content-execution-page">
    <el-card shadow="never" class="mb-3">
      <div class="toolbar">
        <div class="toolbar-left">
          <el-input-number v-model="query.projectId" :min="1" :controls="false" placeholder="项目ID" style="width: 140px" />
          <el-select v-model="query.articleType" clearable placeholder="文章类型" style="width: 160px">
            <el-option label="FAQ" value="faq" />
            <el-option label="场景内容" value="scenario_content" />
            <el-option label="行业文章" value="industry_article" />
            <el-option label="阶段建议" value="stage_advice" />
          </el-select>
          <el-select v-model="query.status" clearable placeholder="状态" style="width: 150px">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never">
      <DataState :loading="loading" :empty="!loading && rows.length === 0" empty-text="暂无文章数据">
        <el-table :data="rows" border>
          <el-table-column prop="id" label="文章ID" width="90" />
          <el-table-column label="项目" min-width="180" show-overflow-tooltip>
            <template #default="scope">{{ scope.row.projectName || `#${scope.row.projectId}` }}</template>
          </el-table-column>
          <el-table-column label="文章类型" width="120">
            <template #default="scope">{{ articleTypeLabel(scope.row.articleType) }}</template>
          </el-table-column>
          <el-table-column prop="title" label="标题" min-width="240" show-overflow-tooltip />
          <el-table-column label="状态" width="120">
            <template #default="scope">
              <el-tag :type="statusTagType(scope.row.status)">{{ statusLabel(scope.row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="创建时间" width="180" />
          <el-table-column label="操作" width="460" fixed="right">
            <template #default="scope">
              <el-button link type="primary" @click="openDetail(scope.row.id)">详情</el-button>
              <el-button v-if="canWrite && canReview(scope.row.status)" link type="primary" @click="openReview(scope.row)">审核</el-button>
              <el-button v-if="canWrite && canEdit(scope.row.status)" link type="primary" @click="openRevision(scope.row)">修订</el-button>
              <el-button v-if="canWrite && canResubmit(scope.row.status)" link type="primary" @click="openResubmit(scope.row)">重新提交</el-button>
              <el-button v-if="canWrite && canDistribute(scope.row.status)" link type="success" @click="openDistribute(scope.row)">分发到站点</el-button>
              <el-button v-if="canWrite && canDistribute(scope.row.status)" link type="success" @click="openOfficialDistribute(scope.row)">发布到官网</el-button>
              <el-button v-if="canWrite && canPublish(scope.row.status)" link type="info" @click="openPublish(scope.row)">Legacy发布</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pager">
          <el-pagination
            background
            layout="prev, pager, next, total"
            :current-page="page.current"
            :page-size="page.size"
            :total="page.total"
            @current-change="onPageChange"
          />
        </div>
      </DataState>
    </el-card>

    <el-drawer v-model="detailVisible" title="文章详情" size="70%">
      <div v-if="detailData" class="detail-wrap">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="文章ID">{{ detailData.article.id }}</el-descriptions-item>
          <el-descriptions-item label="项目">{{ detailData.project?.projectName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="文章类型">{{ articleTypeLabel(detailData.article.articleType) }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusLabel(detailData.article.status) }}</el-descriptions-item>
          <el-descriptions-item label="标题" :span="2">{{ detailData.article.title }}</el-descriptions-item>
        </el-descriptions>

        <h4 class="detail-title">版本记录</h4>
        <el-table :data="detailData.versions" border>
          <el-table-column prop="versionNo" label="版本" width="80" />
          <el-table-column prop="title" label="标题" min-width="220" />
          <el-table-column prop="generatedBy" label="来源" width="130" />
          <el-table-column prop="createdAt" label="时间" width="180" />
        </el-table>

        <div class="detail-header">
          <h4 class="detail-title">内容预览</h4>
          <el-radio-group v-model="detailViewMode" size="small">
            <el-radio-button label="preview">预览</el-radio-button>
            <el-radio-button label="markdown">Markdown</el-radio-button>
          </el-radio-group>
        </div>
        <el-input v-if="detailViewMode === 'markdown'" type="textarea" :rows="14" :model-value="detailMarkdown" readonly />
        <div v-else class="markdown-preview" v-html="detailHtml"></div>
      </div>
    </el-drawer>

    <el-dialog v-model="reviewVisible" title="审核文章" width="540px">
      <el-form :model="reviewForm" label-width="110px">
        <el-form-item label="审核动作" required>
          <el-select v-model="reviewForm.action" style="width: 100%">
            <el-option label="通过" value="approve" />
            <el-option label="驳回" value="reject" />
            <el-option label="退回修改" value="return_for_revision" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="selectedArticleHasRisk" label="风险覆盖">
          <el-checkbox v-model="reviewForm.riskOverride">强制通过提醒级风险</el-checkbox>
        </el-form-item>
        <el-form-item label="审核意见">
          <el-input v-model="reviewForm.comment" type="textarea" :rows="4" placeholder="驳回或退回修改时必填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitReview">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="revisionVisible" title="修订文章" width="760px">
      <el-form :model="revisionForm" label-width="90px">
        <el-form-item label="标题">
          <el-input v-model="revisionForm.title" />
        </el-form-item>
        <el-form-item label="正文" required>
          <div class="editor-wrap">
            <div class="detail-header editor-header">
              <span class="editor-title">内容编辑</span>
              <el-radio-group v-model="revisionViewMode" size="small">
                <el-radio-button label="markdown">Markdown</el-radio-button>
                <el-radio-button label="preview">预览</el-radio-button>
              </el-radio-group>
            </div>
            <el-input v-if="revisionViewMode === 'markdown'" v-model="revisionForm.contentMarkdown" type="textarea" :rows="14" />
            <div v-else class="markdown-preview editor-preview" v-html="revisionHtml"></div>
          </div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="revisionForm.note" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="revisionVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitRevision">保存修订</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resubmitVisible" title="重新提交审核" width="520px">
      <el-form :model="resubmitForm" label-width="90px">
        <el-form-item label="备注">
          <el-input v-model="resubmitForm.comment" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resubmitVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitResubmit">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="distributeVisible" title="分发到站点" width="760px">
      <div class="distribute-wrap">
        <el-alert v-if="fallbackToGeneral" type="warning" :closable="false" show-icon title="暂无本行业专属站点，以下为综合类站点" />
        <el-table :data="sites" border max-height="320">
          <el-table-column width="52">
            <template #default="scope">
              <el-radio :model-value="distributeForm.siteId" :label="scope.row.siteId" @change="() => (distributeForm.siteId = scope.row.siteId)" />
            </template>
          </el-table-column>
          <el-table-column prop="siteName" label="站点" min-width="130" />
          <el-table-column prop="domain" label="域名" min-width="160" />
          <el-table-column prop="tier" label="层级" width="80" />
          <el-table-column prop="integrationMethod" label="方式" width="100" />
          <el-table-column label="行业匹配" width="140">
            <template #default="scope">
              <el-tag v-if="scope.row.matchType === 'exact'" type="success">{{ firstIndustryLabel(scope.row.industryTags) }}</el-tag>
              <el-tag v-else type="info">综合</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="近30天成功率" width="120">
            <template #default="scope">{{ percent(scope.row.successRate30d) }}</template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <el-button @click="distributeVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" :disabled="!distributeForm.siteId" @click="submitDistribute">确认分发</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="officialDistributeVisible" title="发布到官网" width="620px">
      <div class="official-distribute-wrap">
        <el-alert
          v-if="officialQuota"
          :type="officialQuotaExhausted ? 'warning' : 'info'"
          :closable="false"
          show-icon
          :title="officialQuotaText"
        />
        <el-alert v-else type="info" :closable="false" show-icon title="正在加载项目发布配额" />
        <el-form label-width="110px">
          <el-form-item label="官网站点">
            <el-select
              v-model="officialDistributeForm.siteId"
              :loading="officialSitesLoading"
              placeholder="请选择官网站点"
              style="width: 100%"
            >
              <el-option
                v-for="site in officialSites"
                :key="site.id"
                :label="site.siteName"
                :value="site.id"
              />
            </el-select>
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="officialDistributeVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="submitting"
          :disabled="!officialDistributeForm.siteId || officialQuotaExhausted"
          @click="submitOfficialDistribute"
        >
          确认发布
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="publishVisible" title="Legacy发布记录" width="560px">
      <el-form :model="publishForm" label-width="100px">
        <el-form-item label="动作" required>
          <el-select v-model="publishForm.publishAction" style="width: 100%">
            <el-option label="发布" value="publish" />
            <el-option label="下架" value="unpublish" />
          </el-select>
        </el-form-item>
        <el-form-item label="渠道名称">
          <el-input v-model="publishForm.channelName" placeholder="例如：官网、公众号、小红书" />
        </el-form-item>
        <el-form-item label="渠道链接">
          <el-input v-model="publishForm.channelUrl" placeholder="发布后的页面地址" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="publishForm.note" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="publishVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitPublish">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import MarkdownIt from 'markdown-it'
import { ElMessage } from 'element-plus'
import DataState from '@/components/ui/DataState.vue'
import { useUserStore } from '@/stores/user'
import { useDictStore } from '@/stores/dict'
import type { ArticleDetailResponse, ArticleDraft, PublishQuota, RecommendedSite } from '@/types'
import {
  distributeContentArticle,
  getContentArticleDetail,
  getContentArticles,
  getProjectPublishQuota,
  getRecommendedSites,
  publishContentArticle,
  resubmitContentArticle,
  reviewContentArticle,
  saveContentArticleRevision,
} from '@/api/content'
import {
  distributeArticleToBrandOfficialSite,
  listBrandOfficialSites,
  type BrandOfficialSite,
} from '@/api/brandOfficialSite'

const userStore = useUserStore()
const dictStore = useDictStore()
const canWrite = computed(() => userStore.hasPermission('project.write'))

const loading = ref(false)
const submitting = ref(false)
const rows = ref<ArticleDraft[]>([])
const page = reactive({ current: 1, size: 10, total: 0 })
const query = reactive({
  projectId: undefined as number | undefined,
  status: '',
  articleType: '',
})

const detailVisible = ref(false)
const detailData = ref<ArticleDetailResponse | null>(null)
const detailViewMode = ref<'preview' | 'markdown'>('preview')
const currentArticleId = ref<number | null>(null)
const selectedArticleHasRisk = ref(false)

const reviewVisible = ref(false)
const reviewForm = reactive({
  action: 'approve' as 'approve' | 'reject' | 'return_for_revision',
  comment: '',
  riskOverride: false,
})

const revisionVisible = ref(false)
const revisionViewMode = ref<'preview' | 'markdown'>('markdown')
const revisionForm = reactive({
  title: '',
  contentMarkdown: '',
  note: '',
})

const resubmitVisible = ref(false)
const resubmitForm = reactive({ comment: '' })

const distributeVisible = ref(false)
const fallbackToGeneral = ref(false)
const sites = ref<RecommendedSite[]>([])
const distributeForm = reactive({
  articleId: 0,
  projectId: 0,
  siteId: 0,
})

const officialDistributeVisible = ref(false)
const officialSitesLoading = ref(false)
const officialSites = ref<BrandOfficialSite[]>([])
const officialQuota = ref<PublishQuota | null>(null)
const officialDistributeForm = reactive({
  articleId: 0,
  projectId: 0,
  brandId: 0,
  siteId: 0,
})

const publishVisible = ref(false)
const publishForm = reactive({
  publishAction: 'publish' as 'publish' | 'unpublish',
  channelName: '',
  channelUrl: '',
  note: '',
})

const markdown = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
})

const detailMarkdown = computed(() => detailData.value?.versions?.[0]?.contentMarkdown || '')
const detailHtml = computed(() => markdown.render(detailMarkdown.value || ''))
const revisionHtml = computed(() => markdown.render(revisionForm.contentMarkdown || ''))
const officialQuotaExhausted = computed(() => {
  if (!officialQuota.value) return false
  return officialQuota.value.monthUsed >= officialQuota.value.monthLimit
})
const officialQuotaText = computed(() => {
  if (!officialQuota.value) return '正在加载项目发布配额'
  const text = `本月已发布 ${officialQuota.value.monthUsed}/${officialQuota.value.monthLimit}`
  return officialQuotaExhausted.value ? `${text}，配额已用尽` : text
})

const statusOptions = [
  { label: '待审核', value: 'pending_review' },
  { label: '已通过', value: 'approved' },
  { label: '已驳回', value: 'rejected' },
  { label: '修改中', value: 'under_revision' },
  { label: '已发布', value: 'published' },
  { label: '已下架', value: 'unpublished' },
]

function articleTypeLabel(v: string) {
  const map: Record<string, string> = {
    faq: 'FAQ',
    scenario_content: '场景内容',
    industry_article: '行业文章',
    stage_advice: '阶段建议',
  }
  return map[v] || v
}

function statusLabel(v: string) {
  return statusOptions.find((s) => s.value === v)?.label || v
}

function statusTagType(v: string): 'success' | 'warning' | 'danger' | 'info' {
  if (v === 'approved' || v === 'published') return 'success'
  if (v === 'rejected') return 'danger'
  if (v === 'under_revision' || v === 'unpublished') return 'warning'
  return 'info'
}

function canReview(status: string) {
  return status === 'pending_review'
}

function canEdit(status: string) {
  return status === 'pending_review' || status === 'under_revision' || status === 'rejected'
}

function canResubmit(status: string) {
  return status === 'under_revision' || status === 'rejected'
}

function canDistribute(status: string) {
  return status === 'approved' || status === 'unpublished'
}

function canPublish(status: string) {
  return status === 'approved' || status === 'published' || status === 'unpublished'
}

function percent(v: number | undefined) {
  const n = Number(v || 0)
  return `${(n * 100).toFixed(1)}%`
}

function firstIndustryLabel(tags?: string[] | null) {
  const value = (tags || [])[0]
  if (!value) return '-'
  return dictStore.label('industry_tag', value) || value
}

async function load() {
  loading.value = true
  try {
    const { data } = await getContentArticles({
      current: page.current,
      size: page.size,
      projectId: query.projectId,
      status: query.status || undefined,
      articleType: query.articleType || undefined,
    })
    rows.value = data.data.records || []
    page.total = data.data.total || 0
  } catch {
    rows.value = []
    page.total = 0
    ElMessage.error('加载文章失败')
  } finally {
    loading.value = false
  }
}

function search() {
  page.current = 1
  load()
}

function resetQuery() {
  query.projectId = undefined
  query.status = ''
  query.articleType = ''
  search()
}

function onPageChange(v: number) {
  page.current = v
  load()
}

async function openDetail(articleId: number) {
  try {
    const { data } = await getContentArticleDetail(articleId)
    detailData.value = data.data
    detailViewMode.value = 'preview'
    detailVisible.value = true
  } catch {
    ElMessage.error('加载详情失败')
  }
}

function openReview(row: ArticleDraft) {
  currentArticleId.value = row.id
  selectedArticleHasRisk.value = !!row.hasRisk
  reviewForm.action = 'approve'
  reviewForm.comment = ''
  reviewForm.riskOverride = false
  reviewVisible.value = true
}

async function openRevision(row: ArticleDraft) {
  currentArticleId.value = row.id
  revisionForm.title = row.title
  revisionForm.note = ''
  revisionViewMode.value = 'markdown'
  try {
    const { data } = await getContentArticleDetail(row.id)
    revisionForm.contentMarkdown = data.data.versions?.[0]?.contentMarkdown || ''
  } catch {
    revisionForm.contentMarkdown = ''
  }
  revisionVisible.value = true
}

function openResubmit(row: ArticleDraft) {
  currentArticleId.value = row.id
  resubmitForm.comment = ''
  resubmitVisible.value = true
}

function openPublish(row: ArticleDraft) {
  currentArticleId.value = row.id
  publishForm.publishAction = row.status === 'published' ? 'unpublish' : 'publish'
  publishForm.channelName = ''
  publishForm.channelUrl = ''
  publishForm.note = ''
  publishVisible.value = true
}

async function openDistribute(row: ArticleDraft) {
  distributeForm.articleId = row.id
  distributeForm.projectId = row.projectId
  distributeForm.siteId = 0
  fallbackToGeneral.value = false
  try {
    const { data } = await getRecommendedSites(row.projectId)
    fallbackToGeneral.value = !!data.data.fallbackToGeneral
    sites.value = data.data.sites || []
    distributeVisible.value = true
  } catch {
    ElMessage.error('加载分发站点失败')
  }
}

async function openOfficialDistribute(row: ArticleDraft) {
  officialDistributeForm.articleId = row.id
  officialDistributeForm.projectId = row.projectId
  officialDistributeForm.brandId = 0
  officialDistributeForm.siteId = 0
  officialSites.value = []
  officialQuota.value = null
  officialSitesLoading.value = true
  officialDistributeVisible.value = true
  try {
    const [detailRes, quotaRes] = await Promise.all([
      getContentArticleDetail(row.id),
      getProjectPublishQuota(row.projectId),
    ])
    const brandId = detailRes.data.data.project?.brandId
    if (!brandId) {
      ElMessage.error('当前文章未绑定品牌，无法发布到官网')
      officialDistributeVisible.value = false
      return
    }
    officialDistributeForm.brandId = brandId
    officialQuota.value = quotaRes.data.data
    const sites = await listBrandOfficialSites(brandId)
    officialSites.value = sites.filter((site) => site.status === 'active' || site.status === 'enabled')
  } catch {
    ElMessage.error('加载官网发布信息失败')
    officialDistributeVisible.value = false
  } finally {
    officialSitesLoading.value = false
  }
}

async function submitReview() {
  if (!currentArticleId.value) return
  if ((reviewForm.action === 'reject' || reviewForm.action === 'return_for_revision') && !reviewForm.comment.trim()) {
    ElMessage.warning('驳回或退回修改时，审核意见不能为空')
    return
  }
  submitting.value = true
  try {
    await reviewContentArticle(currentArticleId.value, {
      action: reviewForm.action,
      comment: reviewForm.comment || undefined,
      riskOverride: reviewForm.riskOverride,
    })
    reviewVisible.value = false
    ElMessage.success('审核提交成功')
    await load()
  } finally {
    submitting.value = false
  }
}

async function submitRevision() {
  if (!currentArticleId.value) return
  if (!revisionForm.contentMarkdown.trim()) {
    ElMessage.warning('正文不能为空')
    return
  }
  submitting.value = true
  try {
    await saveContentArticleRevision(currentArticleId.value, {
      title: revisionForm.title || undefined,
      contentMarkdown: revisionForm.contentMarkdown,
      note: revisionForm.note || undefined,
    })
    revisionVisible.value = false
    ElMessage.success('修订保存成功')
    await load()
  } finally {
    submitting.value = false
  }
}

async function submitResubmit() {
  if (!currentArticleId.value) return
  submitting.value = true
  try {
    await resubmitContentArticle(currentArticleId.value, {
      comment: resubmitForm.comment || undefined,
    })
    resubmitVisible.value = false
    ElMessage.success('已重新提交审核')
    await load()
  } finally {
    submitting.value = false
  }
}

async function submitDistribute() {
  if (!distributeForm.articleId || !distributeForm.siteId) return
  submitting.value = true
  try {
    await distributeContentArticle(distributeForm.articleId, distributeForm.siteId)
    distributeVisible.value = false
    ElMessage.success('分发任务已触发')
    await load()
  } finally {
    submitting.value = false
  }
}

async function submitOfficialDistribute() {
  if (!officialDistributeForm.articleId || !officialDistributeForm.siteId || officialQuotaExhausted.value) return
  submitting.value = true
  try {
    const task = await distributeArticleToBrandOfficialSite(
      officialDistributeForm.siteId,
      officialDistributeForm.articleId,
    )
    officialDistributeVisible.value = false
    if (task.status === 'submitted') {
      ElMessage.success(`已提交，平台文章 ID: ${task.platformArticleId || '-'}`)
    } else if (task.status === 'failed') {
      ElMessage.error(`发布失败: ${task.failureKind || 'UNKNOWN'}`)
    } else {
      ElMessage.info(`发布任务状态: ${task.status}`)
    }
    await load()
  } finally {
    submitting.value = false
  }
}

async function submitPublish() {
  if (!currentArticleId.value) return
  submitting.value = true
  try {
    await publishContentArticle(currentArticleId.value, {
      publishAction: publishForm.publishAction,
      channelName: publishForm.channelName || undefined,
      channelUrl: publishForm.channelUrl || undefined,
      note: publishForm.note || undefined,
    })
    publishVisible.value = false
    ElMessage.success('发布记录已保存')
    await load()
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await dictStore.ensureLoaded()
  await load()
})
</script>

<style scoped>
.content-execution-page {
  padding: 8px 0;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.mb-3 {
  margin-bottom: 12px;
}

.pager {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

.detail-wrap {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.detail-title {
  margin: 2px 0;
  font-size: 14px;
  font-weight: 600;
}

.markdown-preview {
  min-height: 360px;
  padding: 16px;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  background: #fff;
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
}

.distribute-wrap {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.official-distribute-wrap {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
</style>
