import request from './request'
import type {
  ArticleDraft,
  ArticleDetailResponse,
  AuthorityMediaResource,
  DistributionTask,
  PageResult,
  PublishQuota,
  RecommendedSitesResponse,
  R,
  SelfMediaAccount,
  SelfMediaCookieStatusBatchResponse,
  DouyinAuthUrl,
  DouyinCapability,
  DouyinPlatformOptions,
  WechatMpAuthUrl,
  WechatMpCapability,
} from '@/types'

export function getContentArticles(params: {
  projectName?: string
  status?: string
  articleType?: string
  current?: number
  size?: number
}) {
  return request.get<R<PageResult<ArticleDraft>>>('/content/articles', { params })
}

export function getContentArticleDetail(articleId: number) {
  return request.get<R<ArticleDetailResponse>>(`/content/articles/${articleId}`)
}

export function getSelfMediaCookieStatusBatch(data: {
  articleIds: number[]
  platforms: string[]
}) {
  return request.post<R<SelfMediaCookieStatusBatchResponse>>('/content/articles/self-media-cookie-status/batch', data)
}

export function createManualContentArticle(data: {
  projectId: number
  articleType: string
  title?: string
  contentMarkdown: string
  source?: 'manual' | 'ai_preview' | string
  aiMetadata?: Record<string, unknown>
}) {
  return request.post<R<ArticleDraft>>('/content/articles/manual', data)
}

export interface ArticleAiDraftPreviewRequest {
  projectId: number
  articleType: string
  contentStyle: string
  tone: string
  length: string
  topic: string
  extraPrompt?: string
  referenceMaterials?: string
  modelPlatformCode?: string
  modelId?: string
}

export interface ArticleAiDraftPreviewResponse {
  title: string
  contentMarkdown: string
  promptSnapshot?: string | null
  inputSnapshot?: string | null
  modelResponseSnapshot?: string | null
  modelPlatformCode?: string | null
  modelId?: string | null
  modelName?: string | null
}

export function previewAiContentArticleDraft(data: ArticleAiDraftPreviewRequest) {
  return request.post<R<ArticleAiDraftPreviewResponse>>('/content/articles/ai-draft/preview', data, {
    timeout: 180000,
  })
}

export function saveContentArticleRevision(articleId: number, data: {
  title?: string
  contentMarkdown: string
  note?: string
}) {
  return request.post<R<void>>(`/content/articles/${articleId}/revision`, data)
}

export function resubmitContentArticle(articleId: number, data?: { comment?: string }) {
  return request.post<R<void>>(`/content/articles/${articleId}/resubmit`, data || {})
}

export function reviewContentArticle(articleId: number, data: {
  action: 'approve' | 'reject' | 'return_for_revision'
  comment?: string
  riskOverride?: boolean
}) {
  return request.post<R<void>>(`/content/articles/${articleId}/review`, data)
}

export function publishContentArticle(articleId: number, data: {
  publishAction: 'publish' | 'unpublish'
  channelName?: string
  channelUrl?: string
  note?: string
}) {
  return request.post<R<void>>(`/content/articles/${articleId}/publish`, data)
}

export function distributeContentArticle(articleId: number, siteId: number) {
  return request.post<R<DistributionTask>>(`/content/articles/${articleId}/distribute`, { siteId })
}

export function distributeContentArticleToGeoSite(articleId: number, brandId: number) {
  return request.post<R<DistributionTask>>(`/content/articles/${articleId}/distribute-to-geo-site`, null, {
    params: { brandId },
  })
}

export function distributeContentArticleToAgentSite(articleId: number, brandId: number) {
  return distributeContentArticleToGeoSite(articleId, brandId)
}

export function getAuthorityMediaResources(params?: {
  keyword?: string
  industry?: string
  province?: string
  entranceLevel?: number
  newsResource?: number
  includeCondition?: number
  weekendPublish?: number
  minPrice?: number
  maxPrice?: number
  minPcWeight?: number
  minMWeight?: number
  current?: number
  size?: number
}) {
  return request.get<R<PageResult<AuthorityMediaResource>>>('/content/authority-media/resources', { params })
}

export function distributeContentArticleToAuthorityMedia(articleId: number, data: {
  resourceId: number
  salingPrice: number
  publishedAt?: string
  remark?: string
}) {
  return request.post<R<DistributionTask>>(`/content/articles/${articleId}/distribute-to-authority-media`, data)
}

export function getArticleDistribution(articleId: number) {
  return request.get<R<{ articleId: number; articleStatus: string; attempts: DistributionTask[] }>>(`/content/articles/${articleId}/distribution`)
}

export function retryDistributionTask(taskId: number) {
  return request.post<R<DistributionTask>>(`/content/distribution-tasks/${taskId}/retry`)
}

export function refreshDistributionTaskReviewStatus(taskId: number) {
  return request.post<R<DistributionTask>>(`/content/distribution-tasks/${taskId}/refresh-review-status`)
}

export function confirmManualDistribution(taskId: number, data: { publishedUrl: string; responsePayload?: string }) {
  return request.patch<R<DistributionTask>>(`/content/distribution-tasks/${taskId}/confirm-manual`, data)
}

export function getProjectPublishQuota(projectId: number) {
  return request.get<R<PublishQuota>>(`/content/projects/${projectId}/publish-quota`)
}

export function getRecommendedSites(projectId: number) {
  return request.get<R<RecommendedSitesResponse>>(`/content/projects/${projectId}/recommended-sites`)
}

export function getWechatMpCapability() {
  return request.get<R<WechatMpCapability>>('/content/self-media-accounts/wechat/capability')
}

export function getWechatMpAuthUrl(params: { brandId: number; redirectArticleId?: number }) {
  return request.get<R<WechatMpAuthUrl>>('/content/self-media-accounts/wechat/auth-url', { params })
}

export function getDouyinCapability() {
  return request.get<R<DouyinCapability>>('/content/self-media-accounts/douyin/capability')
}

export function getDouyinAuthUrl(params: { brandId: number; redirectArticleId?: number }) {
  return request.get<R<DouyinAuthUrl>>('/content/self-media-accounts/douyin/auth-url', { params })
}

export function getSelfMediaAccountsByBrand(brandId: number) {
  return request.get<R<SelfMediaAccount[]>>(`/content/brands/${brandId}/self-media-accounts`)
}

export function createSelfMediaAccount(brandId: number, data: {
  platform: 'toutiao' | 'zhihu'
  accountName: string
  platformAccountId?: string
  status?: 'active' | 'disabled'
}) {
  return request.post<R<SelfMediaAccount>>(`/content/brands/${brandId}/self-media-accounts`, data)
}

export function updateSelfMediaAccount(id: number, data: {
  platform: 'toutiao' | 'zhihu'
  accountName: string
  platformAccountId?: string
  status?: 'active' | 'disabled'
}) {
  return request.put<R<SelfMediaAccount>>(`/content/self-media-accounts/${id}`, data)
}

export function checkSelfMediaAccountAuth(id: number) {
  return request.post<R<SelfMediaAccount>>(`/content/self-media-accounts/${id}/check-auth`)
}

export function distributeContentArticleToSelfMediaAccount(articleId: number, data: {
  selfMediaAccountId: number
  coverMaterialId?: number
  imageMaterialIds?: number[]
  privateStatus?: number | string
  downloadType?: number | string
  platformOptions?: DouyinPlatformOptions
  requestId: string
}) {
  return request.post<R<DistributionTask>>(`/content/articles/${articleId}/distribute-to-self-media`, data)
}
