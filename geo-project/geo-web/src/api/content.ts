import request from './request'
import type {
  ArticleDraft,
  ArticleDetailResponse,
  DistributionTask,
  PageResult,
  PublishQuota,
  RecommendedSitesResponse,
  R,
  SelfMediaAccount,
  DouyinAuthUrl,
  DouyinCapability,
  DouyinPlatformOptions,
  WechatMpAuthUrl,
  WechatMpCapability,
} from '@/types'

export function getContentArticles(params: {
  projectId?: number
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

export function createManualContentArticle(data: {
  projectId: number
  articleType: string
  title?: string
  contentMarkdown: string
}) {
  return request.post<R<ArticleDraft>>('/content/articles/manual', data)
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
