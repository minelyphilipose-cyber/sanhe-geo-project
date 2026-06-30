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
  SelfMediaAccountPlatformOption,
  SelfMediaAutomationOverview,
  SelfMediaCookieStatusBatchResponse,
  SelfMediaScheduleCapability,
  SelfMediaPublishSchedule,
  SelfMediaPublishScheduleCreateResponse,
  SelfMediaPlatformQuickScheduleResponse,
  DouyinAuthUrl,
  DouyinCapability,
  DouyinPlatformOptions,
  WechatMpAuthUrl,
  WechatMpCapability,
} from '@/types'

export function getContentArticles(params: {
  articleId?: number
  projectName?: string
  status?: string
  articleType?: string
  articleTypeCode?: string
  channelGroupCode?: string
  channelSubCode?: string
  generationMode?: 'batch' | 'single'
  complianceStatus?: string
  publishReviewStatus?: string
  medicalIndustryCode?: string
  medicalChannelTier?: string
  specialIndustryOnly?: boolean
  createdStartDate?: string
  createdEndDate?: string
  current?: number
  size?: number
}) {
  return request.get<R<PageResult<ArticleDraft>>>('/content/articles', { params })
}

export function getSpecialIndustryArticles(params: {
  articleId?: number
  projectName?: string
  status?: string
  complianceStatus?: string
  publishReviewStatus?: string
  medicalIndustryCode?: string
  medicalChannelTier?: string
  specialIndustryOnly?: boolean
  current?: number
  size?: number
}) {
  return getContentArticles(params)
}

export function getContentArticleDetail(articleId: number) {
  return request.get<R<ArticleDetailResponse>>(`/content/articles/${articleId}`)
}

export interface WechatRenderRoleSchema {
  wrapperHtml: string
  wrapperSafe?: boolean | null
}

export interface WechatBodyStyle {
  fontSize?: string | null
  lineHeight?: string | null
  letterSpacing?: string | null
  color?: string | null
  textAlign?: string | null
  paragraphMargin?: string | null
}

export interface WechatTemplateSlice {
  id: string
  order: number
  suggestedRole: string
  role: string
  fingerprint: string
  outlier: boolean
  html: string
  previewText?: string
  previewHtml?: string
  warnings?: string[]
}

export interface WechatTemplateRoleDraft {
  role: string
  wrapperHtml: string
  wrapperSafe?: boolean | null
  reuseCount: number
  sliceIds: string[]
  needsConfirmation: boolean
}

export interface WechatRenderWarning {
  type: string
  blockId?: string | null
  role?: string | null
  message: string
}

export interface WechatTemplateParseResponse {
  sourceType: string
  bodyStyle?: WechatBodyStyle | null
  slices: WechatTemplateSlice[]
  roles: WechatTemplateRoleDraft[]
  warnings: WechatRenderWarning[]
}

export interface PlatformRenderTemplate {
  id: number
  platformCode: string
  name: string
  description?: string | null
  status: string
  createdBy?: number | null
  createdByName?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

export interface PlatformRenderTemplateVersion {
  id: number
  templateId: number
  versionNo: number
  sourceType: string
  sourceHtml?: string | null
  templateSchemaJson: string
  sanitizedPreviewHtml?: string | null
  createdBy?: number | null
  createdAt?: string | null
}

export interface WechatArticleBlock {
  id: string
  type: string
  defaultRole: string
  allowedRoles: string[]
  text?: string | null
  html?: string | null
  imageUrl?: string | null
  imageAlt?: string | null
  contentHash: string
  order: number
}

export interface WechatRenderMark {
  blockId: string
  order?: number | null
  role: string
}

export interface WechatRenderInsert {
  afterBlockId?: string | null
  role: string
  content?: string | null
}

export interface WechatRenderTextMark {
  blockId: string
  order?: number | null
  role: string
  quote: string
  prefix?: string | null
  suffix?: string | null
  start?: number | null
  end?: number | null
}

export interface WechatRenderAnnotations {
  marks: WechatRenderMark[]
  textMarks: WechatRenderTextMark[]
  inserts: WechatRenderInsert[]
}

export interface WechatRenderConfigResponse {
  articleId: number
  platformCode: string
  templateId?: number | null
  templateVersionId?: number | null
  blocks: WechatArticleBlock[]
  annotations: WechatRenderAnnotations
  renderConfig?: Record<string, unknown> | null
  warnings: WechatRenderWarning[]
}

export interface WechatRenderPreviewResponse {
  html: string
  warnings: WechatRenderWarning[]
}

export function parseWechatRenderTemplate(data: { sourceHtml: string; sourceType?: string }) {
  return request.post<R<WechatTemplateParseResponse>>('/wechat-render-templates/parse', data)
}

export function getWechatRenderTemplates(params?: { current?: number; size?: number }) {
  return request.get<R<PageResult<PlatformRenderTemplate>>>('/wechat-render-templates', { params })
}

export function getWechatRenderTemplate(id: number) {
  return request.get<R<PlatformRenderTemplate>>(`/wechat-render-templates/${id}`)
}

export function createWechatRenderTemplate(data: {
  name: string
  description?: string
  sourceType: string
  sourceHtml: string
  roles: Record<string, WechatRenderRoleSchema>
  bodyStyle?: WechatBodyStyle | null
}) {
  return request.post<R<PlatformRenderTemplate>>('/wechat-render-templates', data)
}

export function updateWechatRenderTemplate(id: number, data: {
  name: string
  description?: string
  status?: 'enabled' | 'disabled'
}) {
  return request.put<R<PlatformRenderTemplate>>(`/wechat-render-templates/${id}`, data)
}

export function createWechatRenderTemplateVersion(id: number, data: {
  sourceType: string
  sourceHtml: string
  roles: Record<string, WechatRenderRoleSchema>
  bodyStyle?: WechatBodyStyle | null
}) {
  return request.post<R<PlatformRenderTemplateVersion>>(`/wechat-render-templates/${id}/versions`, data)
}

export function getWechatRenderTemplateCurrentVersion(id: number) {
  return request.get<R<PlatformRenderTemplateVersion | null>>(`/wechat-render-templates/${id}/current-version`)
}

export function updateWechatRenderTemplateStatus(id: number, status: 'enabled' | 'disabled') {
  return request.put<R<void>>(`/wechat-render-templates/${id}/status`, undefined, { params: { status } })
}

export function deleteWechatRenderTemplate(id: number) {
  return request.delete<R<void>>(`/wechat-render-templates/${id}`)
}

export function getArticleWechatRender(articleId: number) {
  return request.get<R<WechatRenderConfigResponse>>(`/content/articles/${articleId}/wechat-render`)
}

export function saveArticleWechatRender(articleId: number, data: {
  templateVersionId: number
  annotations: WechatRenderAnnotations
  renderConfig?: Record<string, unknown>
}) {
  return request.post<R<WechatRenderConfigResponse>>(`/content/articles/${articleId}/wechat-render`, data)
}

export function previewArticleWechatRender(articleId: number, data: {
  templateVersionId?: number | null
  annotations?: WechatRenderAnnotations
  renderConfig?: Record<string, unknown>
}) {
  return request.post<R<WechatRenderPreviewResponse>>(`/content/articles/${articleId}/wechat-render/preview`, data)
}

export function finalPreviewArticleWechatRender(articleId: number, data?: {
  templateVersionId?: number | null
  annotations?: WechatRenderAnnotations
  renderConfig?: Record<string, unknown>
}) {
  return request.post<R<WechatRenderPreviewResponse>>(`/content/articles/${articleId}/wechat-render/final-preview`, data || {})
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
  contentStyle: string
  topic: string
  topicAsQuestion?: string
  title?: string
  contentMarkdown: string
  coverMaterialId?: number
  headImageMaterialId?: number
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

export interface ArticleTemplatePreviewRequest {
  projectId: number
  articleType: string
  channelGroupCode: string
  channelSubCode?: string | null
  topic: string
  topicAsQuestion?: string
  length?: 'short' | 'medium' | 'long' | string
  keywordGroupId?: number
  extraPrompt?: string
  promptTemplateId?: number
  promptTemplateVersionId: number
  modelPlatformCode?: string
  modelId?: string
}

export interface ArticleTemplatePreviewIssue {
  code?: string
  level?: string
  message?: string
  [key: string]: unknown
}

export interface ArticleTemplatePreviewResponse {
  title: string
  contentMarkdown: string
  promptSnapshot?: string | null
  inputSnapshot?: string | null
  templateId?: number | null
  templateVersionId?: number | null
  templateName?: string | null
  channelGroupCode?: string | null
  channelSubCode?: string | null
  contentStyle?: string | null
  topicAsQuestion?: string | null
  qualityStatus?: string | null
  qualityIssues?: ArticleTemplatePreviewIssue[] | null
  unresolvedVariables?: string[] | null
  modelPlatformCode?: string | null
  modelId?: string | null
  modelName?: string | null
  promptTokens?: number | null
  completionTokens?: number | null
  durationMs?: number | null
}

export interface ArticleTemplateGenerateResponse {
  articleId: number
  status: string
}

export function previewArticleTemplate(data: ArticleTemplatePreviewRequest) {
  return request.post<R<ArticleTemplatePreviewResponse>>('/content/articles/template-preview', data, {
    timeout: 180000,
  })
}

export function generateArticleTemplate(data: ArticleTemplatePreviewRequest) {
  return request.post<R<ArticleTemplateGenerateResponse>>('/content/articles/template-generate', data, {
    timeout: 180000,
  })
}

export interface BatchArticleGeneratePlatform {
  contentStyle?: string
  channelGroupCode?: string
  channelSubCode?: string | null
  allocationMode?: 'auto' | 'custom'
  agentSiteModule?: string | null
  articleTypeCode?: string | null
  count: number
  extraPrompt?: string
  templateCounts?: BatchArticleGenerateTemplateCount[]
  previewTemplateCounts?: BatchArticleGenerateTemplateCount[]
}

export interface BatchArticleGenerateTemplateCount {
  templateId: number
  templateVersionId?: number
  templateName?: string
  articleTypeName?: string | null
  agentSiteModule?: string | null
  count: number
  extraPrompt?: string
}

export interface BatchArticleGenerateTopic {
  topic: string
  topicAsQuestion?: string
  questionSceneCode?: string | null
  keywordGroupId?: number
  keywordGroupName?: string
  readinessWarningConfirmed?: boolean
  readinessWarningCodes?: string[]
  platforms: BatchArticleGeneratePlatform[]
}

export interface BatchArticleGenerateRequest {
  projectId: number
  topicSource: 'keyword_group' | 'manual'
  topics: BatchArticleGenerateTopic[]
}

export interface BatchArticleGenerateResponse {
  batchId: number
  totalCount: number
  status: string
  allocationChanged?: boolean
  customSkipped?: boolean
  notices?: BatchArticleGenerateNotice[]
}

export interface BatchArticleGenerateNotice {
  type: string
  level: 'warning' | 'info' | string
  message: string
  items?: BatchArticleGenerateNoticeItem[]
}

export interface BatchArticleGenerateNoticeItem {
  topic?: string
  channelGroupCode?: string
  channelSubCode?: string
  templateId?: number
  templateName?: string
  requestedCount?: number
  reason?: string
  before?: BatchArticleGenerateTemplateCount[]
  after?: BatchArticleGenerateTemplateCount[]
}

export function createBatchContentArticles(data: BatchArticleGenerateRequest) {
  return request.post<R<BatchArticleGenerateResponse>>('/content/articles/batch-generate', data)
}

export interface BatchArticleGenerationTask {
  taskId: number
  articleId?: number | null
  sourceBrandId?: number | null
  sourceBrandName?: string | null
  subjectBrandId?: number | null
  subjectBrandName?: string | null
  subjectProjectId?: number | null
  rowNo: number
  articleIndexInBatch: number
  articleType?: string | null
  articleTypeCode?: string | null
  channelGroupCode?: string | null
  channelSubCode?: string | null
  tone?: string | null
  contentStyle?: string | null
  agentSiteModule?: string | null
  contentAngle?: string | null
  audiencePerspective?: string | null
  promptTemplateId?: number | null
  promptTemplateVersionId?: number | null
  perspectiveCode?: string | null
  perspectiveMatchedScope?: string | null
  perspectiveMatchedConfigId?: number | null
  allocationMode?: string | null
  templateSource?: string | null
  suggestedPlatformCodes?: string | null
  selectedPlatformCodes?: string | null
  readinessWarningConfirmed?: boolean | null
  readinessWarningCodes?: string | null
  status: string
  qualityStatus?: string | null
  complianceStatus?: string | null
  complianceIssuesJson?: string | null
  discardedArticleId?: number | null
  retryCount?: number | null
  medicalIndustryCode?: string | null
  medicalCategoryCode?: string | null
  medicalCategoryName?: string | null
  topicAngleId?: number | null
  structureSkeleton?: string | null
  focus?: string | null
  errorMessage?: string | null
  startedAt?: string | null
  finishedAt?: string | null
}

export interface BatchArticleGenerationDetailResponse {
  batchId: number
  projectId: number
  topic?: string | null
  topicAsQuestion?: string | null
  status: string
  totalCount: number
  successCount: number
  failedCount: number
  warningCount: number
  createdAt?: string | null
  startedAt?: string | null
  finishedAt?: string | null
  tasks: BatchArticleGenerationTask[]
}

export function getBatchArticleGeneration(batchId: number) {
  return request.get<R<BatchArticleGenerationDetailResponse>>(`/content/articles/batch-generate/${batchId}`)
}

export function retryFailedBatchArticleGeneration(batchId: number) {
  return request.post<R<BatchArticleGenerationDetailResponse>>(`/content/articles/batch-generate/${batchId}/retry-failed`)
}

export interface ArticleGenerationReadinessRequest {
  projectId: number
  questionSceneCodes: string[]
}

export interface ArticleGenerationReadinessBaseItem {
  code: string
  label: string
  status: 'ok' | 'missing' | string
  severity: 'normal' | 'warning' | 'critical' | string
  message?: string
  source?: string
}

export interface ArticleGenerationReadinessSceneItem {
  code: string
  severity: 'warning' | 'critical' | string
  message: string
  warningCode?: string | null
  requiresConfirmation?: boolean
}

export interface ArticleGenerationReadinessSceneImpact {
  questionSceneCode: string
  questionSceneName: string
  status: 'ok' | 'warning' | 'critical' | string
  score: number
  items: ArticleGenerationReadinessSceneItem[]
}

export interface ArticleGenerationReadinessReport {
  projectId: number
  score: number
  status: 'ok' | 'warning' | 'critical' | string
  baseItems: ArticleGenerationReadinessBaseItem[]
  sceneImpacts: ArticleGenerationReadinessSceneImpact[]
}

export function checkBatchArticleGenerationReadiness(data: ArticleGenerationReadinessRequest) {
  return request.post<R<ArticleGenerationReadinessReport>>('/content/articles/batch-generate/readiness', data)
}

export interface ArticlePromptTemplate {
  id: number
  name: string
  description?: string | null
  channelGroupCode: string
  channelGroupName?: string | null
  channelSubCode?: string | null
  channelSubName?: string | null
  agentSiteModule?: string | null
  articleTypeCode: string
  articleTypeName?: string | null
  questionSceneCode?: string | null
  questionSceneName?: string | null
  perspectiveCode: string
  status: 'draft' | 'active' | 'disabled' | string
  weight: number
  sortOrder: number
  sampleOutputUrl?: string | null
  contactDisclosureMode?: 'full' | 'soft_hint' | 'brand_only' | 'none' | string | null
  currentVersionId?: number | null
  currentVersionNo?: number | null
  updatedAt?: string | null
  createdAt?: string | null
}

export interface ArticlePromptTemplateVersion {
  id: number
  templateId: number
  versionNo: number
  status: 'draft' | 'published' | 'archived' | string
  systemPrompt: string
  userPromptTemplate: string
  variablesJson?: string | null
  qualityRulesJson?: string | null
  changeNote?: string | null
  createdAt?: string | null
  publishedAt?: string | null
}

export interface ArticlePromptTemplateDetail extends ArticlePromptTemplate {
  versions: ArticlePromptTemplateVersion[]
}

export interface ArticlePromptTemplateDetailResponse {
  template: ArticlePromptTemplate
  currentVersion?: ArticlePromptTemplateVersion | null
  versions: ArticlePromptTemplateVersion[]
}

export interface ArticlePromptTemplateSaveRequest {
  name: string
  description?: string
  channelGroupCode: string
  channelSubCode?: string | null
  agentSiteModule?: string | null
  articleTypeCode: string
  questionSceneCode?: string | null
  perspectiveCode?: string | null
  status: 'draft' | 'active' | 'disabled' | string
  weight: number
  sortOrder: number
  sampleOutputUrl?: string | null
  contactDisclosureMode?: 'full' | 'soft_hint' | 'brand_only' | 'none' | string | null
  systemPrompt: string
  userPromptTemplate: string
  variablesJson?: string | null
  qualityRulesJson?: string | null
  changeNote?: string
}

export interface ArticlePromptVariable {
  code: string
  name: string
  description: string
  source: string
  emptyStrategy: 'KEEP_EMPTY' | 'DASH' | 'SAFE_TEXT' | string
  emptyText?: string | null
  sampleValue?: string | null
}

export interface ArticleGenerationTemplateOption {
  templateId: number
  templateVersionId: number
  templateName: string
  channelGroupCode: string
  channelSubCode?: string | null
  agentSiteModule?: string | null
  articleTypeCode: string
  articleTypeName?: string | null
  questionSceneCode?: string | null
  questionSceneName?: string | null
  perspectiveCode?: string | null
  weight: number
  sortOrder: number
}

export interface ArticleGenerationChannelOption {
  channelGroupCode: string
  channelGroupName: string
  channelSubCode?: string | null
  channelSubName?: string | null
  label: string
  description: string
  contentStyle: string
  enabled: boolean
  disabledReason?: string | null
  templateCount: number
  templates: ArticleGenerationTemplateOption[]
}

export interface ArticleGenerationChannelGroup {
  code: string
  name: string
  description: string
  channels: ArticleGenerationChannelOption[]
}

export interface ArticleGenerationOptions {
  groups: ArticleGenerationChannelGroup[]
  questionScenePlatformSuggestions?: ArticleQuestionScenePlatformSuggestion[]
}

export interface ArticleQuestionScenePlatformSuggestion {
  questionSceneCode: string
  questionSceneName?: string | null
  platformCodes: string[]
}

export interface ArticleAllocationItem {
  templateId: number
  templateVersionId: number
  templateName: string
  articleTypeCode: string
  articleTypeName?: string | null
  questionSceneCode?: string | null
  questionSceneName?: string | null
  agentSiteModule?: string | null
  perspectiveCode?: string | null
  weight: number
  count: number
}

export interface ArticleAllocationPreviewResponse {
  channelGroupCode: string
  channelSubCode?: string | null
  totalCount: number
  items: ArticleAllocationItem[]
}

export function getArticlePromptTemplates(params?: {
  channelGroupCode?: string
  channelSubCode?: string
  agentSiteModule?: string
  questionSceneCode?: string
  perspectiveCode?: string
  status?: string
  keyword?: string
  current?: number
  size?: number
}) {
  return request.get<R<PageResult<ArticlePromptTemplate>>>('/content/article-prompt-templates', { params })
}

export interface TemplatePerspective {
  code: string
  name: string
  description?: string | null
  enabled: boolean
  sortOrder: number
  thirdPartySubjectEnabled?: boolean | null
}

export interface BrandChannelTemplatePerspective {
  id: number
  brandId: number
  channelGroupCode: string
  channelSubCode: string
  perspectiveCode: string
  perspectiveName?: string | null
  enabled: boolean
  createdAt?: string | null
  updatedAt?: string | null
}

export interface BrandChannelTemplatePerspectiveSaveRequest {
  brandId: number
  channelGroupCode: string
  channelSubCode?: string | null
  perspectiveCode: string
  enabled?: boolean
}

export interface TemplatePerspectiveResolveResponse {
  perspectiveCode: string
  perspectiveName?: string | null
  matchedScope: 'exact' | 'channel_all' | 'default' | string
  matchedConfigId?: number | null
}

export interface TemplatePerspectiveConfigListResponse {
  perspectives: TemplatePerspective[]
  configs: BrandChannelTemplatePerspective[]
}

export function getTemplatePerspectives(params?: { includeDisabled?: boolean }) {
  return request.get<R<TemplatePerspective[]>>('/content/template-perspectives', { params })
}

export function updateTemplatePerspectiveEnabled(code: string, enabled: boolean) {
  return request.patch<R<TemplatePerspective>>(`/content/template-perspectives/${code}/enabled`, { enabled })
}

export function getBrandTemplatePerspectiveConfigs(brandId: number) {
  return request.get<R<TemplatePerspectiveConfigListResponse>>('/content/template-perspectives/brand-configs', { params: { brandId } })
}

export function saveBrandTemplatePerspectiveConfig(data: BrandChannelTemplatePerspectiveSaveRequest) {
  return request.post<R<BrandChannelTemplatePerspective>>('/content/template-perspectives/brand-configs', data)
}

export function deleteBrandTemplatePerspectiveConfig(id: number) {
  return request.delete<R<void>>(`/content/template-perspectives/brand-configs/${id}`)
}

export function resolveTemplatePerspective(params: {
  brandId: number
  channelGroupCode: string
  channelSubCode?: string | null
}) {
  return request.get<R<TemplatePerspectiveResolveResponse>>('/content/template-perspectives/resolve', { params })
}

export function getArticlePromptTemplate(id: number) {
  return request.get<R<ArticlePromptTemplateDetailResponse>>(`/content/article-prompt-templates/${id}`)
}

export function getArticlePromptTemplateVariables() {
  return request.get<R<ArticlePromptVariable[]>>('/content/article-prompt-templates/variables')
}

export function createArticlePromptTemplate(data: ArticlePromptTemplateSaveRequest) {
  return request.post<R<ArticlePromptTemplateDetailResponse>>('/content/article-prompt-templates', data)
}

export function updateArticlePromptTemplate(id: number, data: ArticlePromptTemplateSaveRequest) {
  return request.put<R<ArticlePromptTemplateDetailResponse>>(`/content/article-prompt-templates/${id}`, data)
}

export function updateArticlePromptTemplateWeight(id: number, data: { weight: number }) {
  return request.patch<R<ArticlePromptTemplate>>(`/content/article-prompt-templates/${id}/weight`, data)
}

export function createArticlePromptTemplateVersion(id: number, data: {
  systemPrompt: string
  userPromptTemplate: string
  changeNote?: string
}) {
  return request.post<R<ArticlePromptTemplateDetailResponse>>(`/content/article-prompt-templates/${id}/versions`, data)
}

export function publishArticlePromptTemplateVersion(id: number, versionId: number) {
  return request.post<R<ArticlePromptTemplateDetailResponse>>(`/content/article-prompt-templates/${id}/versions/${versionId}/publish`)
}

export interface MedicalTopicAngle {
  id: number
  industryCode: string
  industryName?: string | null
  categoryCode: string
  categoryName: string
  topicAngle: string
  recommendedFocus?: string | null
  enabled: boolean
  sortOrder: number
}

export interface SpecialIndustryTopicAngleCategory {
  industryCode: string
  industryName?: string | null
  categoryCode: string
  categoryName: string
  topicAngleCount: number
}

export interface MedicalComplianceRule {
  id: number
  ruleType: string
  industryCode?: string | null
  channelTier?: string | null
  channelGroupCode?: string | null
  channelSubCode?: string | null
  pattern: string
  matchMode: 'contains' | 'regex' | string
  severity: 'block' | 'warn' | string
  enabled: boolean
  remark?: string | null
}

export interface MedicalComplianceKernel {
  id: number
  industryCode: string
  channelTier: string
  kernelName: string
  systemPrompt: string
  brandExposureLimit?: number | null
  requireManualPublishReview: boolean
  enabled: boolean
  versionNo: number
}

export interface MedicalChannelStyleModule {
  id: number
  channelGroupCode: string
  channelSubCode?: string | null
  channelTier: string
  stylePrompt: string
  highRisk: boolean
  enabled: boolean
}

export interface MedicalComplianceHitLog {
  id: number
  articleId?: number | null
  batchId?: number | null
  taskId?: number | null
  projectId?: number | null
  projectName?: string | null
  brandId?: number | null
  brandName?: string | null
  ruleId?: number | null
  ruleType?: string | null
  matchedText?: string | null
  checkStage?: string | null
  action?: string | null
  createdAt?: string | null
}

export interface MedicalGenerationHistory {
  id: number
  projectId?: number | null
  projectName?: string | null
  brandId?: number | null
  brandName?: string | null
  topicAngleId?: number | null
  topicAngle?: string | null
  structureSkeleton?: string | null
  focus?: string | null
  articleId?: number | null
  articleTitle?: string | null
  createdAt?: string | null
}

export interface SpecialIndustryRuleHitSummary {
  ruleType: string
  hitCount: number
}

export interface SpecialIndustryBatchTrace {
  batchId: number
  projectId?: number | null
  projectName?: string | null
  brandId?: number | null
  brandName?: string | null
  medicalIndustryCode?: string | null
  medicalChannelTier?: string | null
  topic?: string | null
  status?: string | null
  totalCount?: number | null
  successCount?: number | null
  failedCount?: number | null
  discardedCount?: number | null
  retryTaskCount?: number | null
  createdAt?: string | null
  finishedAt?: string | null
  errorMessage?: string | null
}

export interface SpecialIndustryOverview {
  pendingReviewCount: number
  rejectedReviewCount: number
  complianceFailedCount: number
  discardedCount: number
  officialPendingCount: number
  todayHitCount: number
  sevenDayHitCount: number
  sevenDayDiscardedCount: number
  topRuleHits: SpecialIndustryRuleHitSummary[]
  recentProblemBatches: SpecialIndustryBatchTrace[]
}

export interface SpecialIndustryRuleTestRequest {
  industryCode?: string | null
  channelTier?: string | null
  channelGroupCode?: string | null
  channelSubCode?: string | null
  brandName?: string | null
  brandExposureLimit?: number | null
  highRiskChannel?: boolean | null
  title?: string | null
  content: string
}

export interface SpecialIndustryRuleTestIssue {
  ruleId?: number | null
  ruleType?: string | null
  severity?: string | null
  matchedText?: string | null
  message?: string | null
}

export interface SpecialIndustryRuleTestResult {
  passed: boolean
  issues: SpecialIndustryRuleTestIssue[]
}

export type SpecialIndustryTopicAngle = MedicalTopicAngle
export type SpecialIndustryComplianceRule = MedicalComplianceRule
export type SpecialIndustryComplianceKernel = MedicalComplianceKernel
export type SpecialIndustryChannelStyleModule = MedicalChannelStyleModule
export type SpecialIndustryComplianceHitLog = MedicalComplianceHitLog
export type SpecialIndustryGenerationHistory = MedicalGenerationHistory

export function getSpecialIndustryTopicAngles(params?: Record<string, any>) {
  return request.get<R<PageResult<SpecialIndustryTopicAngle>>>('/content/special-industry/topic-angles', { params })
}

export function getSpecialIndustryTopicAngleCategories(params?: {
  industryCode?: string | null
  enabled?: boolean
}) {
  return request.get<R<SpecialIndustryTopicAngleCategory[]>>('/content/special-industry/topic-angle-categories', { params })
}

export function createSpecialIndustryTopicAngle(data: Partial<SpecialIndustryTopicAngle>) {
  return request.post<R<SpecialIndustryTopicAngle>>('/content/special-industry/topic-angles', data)
}

export function updateSpecialIndustryTopicAngle(id: number, data: Partial<SpecialIndustryTopicAngle>) {
  return request.put<R<SpecialIndustryTopicAngle>>(`/content/special-industry/topic-angles/${id}`, data)
}

export function deleteSpecialIndustryTopicAngle(id: number) {
  return request.delete<R<void>>(`/content/special-industry/topic-angles/${id}`)
}

export function getSpecialIndustryComplianceRules(params?: Record<string, any>) {
  return request.get<R<PageResult<SpecialIndustryComplianceRule>>>('/content/special-industry/rules', { params })
}

export function createSpecialIndustryComplianceRule(data: Partial<SpecialIndustryComplianceRule>) {
  return request.post<R<SpecialIndustryComplianceRule>>('/content/special-industry/rules', data)
}

export function updateSpecialIndustryComplianceRule(id: number, data: Partial<SpecialIndustryComplianceRule>) {
  return request.put<R<SpecialIndustryComplianceRule>>(`/content/special-industry/rules/${id}`, data)
}

export function getSpecialIndustryComplianceKernels(params?: Record<string, any>) {
  return request.get<R<PageResult<SpecialIndustryComplianceKernel>>>('/content/special-industry/kernels', { params })
}

export function createSpecialIndustryComplianceKernel(data: Partial<SpecialIndustryComplianceKernel>) {
  return request.post<R<SpecialIndustryComplianceKernel>>('/content/special-industry/kernels', data)
}

export function getSpecialIndustryChannelStyleModules(params?: Record<string, any>) {
  return request.get<R<PageResult<SpecialIndustryChannelStyleModule>>>('/content/special-industry/channel-styles', { params })
}

export function createSpecialIndustryChannelStyleModule(data: Partial<SpecialIndustryChannelStyleModule>) {
  return request.post<R<SpecialIndustryChannelStyleModule>>('/content/special-industry/channel-styles', data)
}

export function updateSpecialIndustryChannelStyleModule(id: number, data: Partial<SpecialIndustryChannelStyleModule>) {
  return request.put<R<SpecialIndustryChannelStyleModule>>(`/content/special-industry/channel-styles/${id}`, data)
}

export function getSpecialIndustryComplianceHitLogs(params?: Record<string, any>) {
  return request.get<R<PageResult<SpecialIndustryComplianceHitLog>>>('/content/special-industry/hit-logs', { params })
}

export function getSpecialIndustryGenerationHistory(params?: Record<string, any>) {
  return request.get<R<PageResult<SpecialIndustryGenerationHistory>>>('/content/special-industry/generation-history', { params })
}

export const getMedicalTopicAngles = getSpecialIndustryTopicAngles
export const createMedicalTopicAngle = createSpecialIndustryTopicAngle
export const updateMedicalTopicAngle = updateSpecialIndustryTopicAngle
export const deleteMedicalTopicAngle = deleteSpecialIndustryTopicAngle
export const getMedicalComplianceRules = getSpecialIndustryComplianceRules
export const createMedicalComplianceRule = createSpecialIndustryComplianceRule
export const updateMedicalComplianceRule = updateSpecialIndustryComplianceRule
export const getMedicalComplianceKernels = getSpecialIndustryComplianceKernels
export const createMedicalComplianceKernel = createSpecialIndustryComplianceKernel
export const getMedicalChannelStyleModules = getSpecialIndustryChannelStyleModules
export const createMedicalChannelStyleModule = createSpecialIndustryChannelStyleModule
export const updateMedicalChannelStyleModule = updateSpecialIndustryChannelStyleModule
export const getMedicalComplianceHitLogs = getSpecialIndustryComplianceHitLogs
export const getMedicalGenerationHistory = getSpecialIndustryGenerationHistory
export function getMedicalTopicAnglesLegacy(params?: Record<string, any>) {
  return request.get<R<PageResult<MedicalTopicAngle>>>('/content/medical-articles/topic-angles', { params })
}

export function getMedicalComplianceRulesLegacy(params?: Record<string, any>) {
  return request.get<R<PageResult<MedicalComplianceRule>>>('/content/medical-articles/rules', { params })
}

export function getSpecialIndustryOverview() {
  return request.get<R<SpecialIndustryOverview>>('/content/special-industry/overview')
}

export function getSpecialIndustryBatches(params?: Record<string, any>) {
  return request.get<R<PageResult<SpecialIndustryBatchTrace>>>('/content/special-industry/batches', { params })
}

export function testSpecialIndustryRules(data: SpecialIndustryRuleTestRequest) {
  return request.post<R<SpecialIndustryRuleTestResult>>('/content/special-industry/rules/test', data)
}

export function getArticleGenerationOptions() {
  return request.get<R<ArticleGenerationOptions>>('/content/article-prompt-templates/generation-options')
}

export function previewArticleTemplateAllocation(data: {
  projectId?: number
  channelGroupCode: string
  channelSubCode?: string | null
  questionSceneCode?: string | null
  count: number
}) {
  return request.post<R<ArticleAllocationPreviewResponse>>('/content/article-prompt-templates/preview-allocation', data)
}

export function deleteContentArticle(articleId: number) {
  return request.delete<R<void>>(`/content/articles/${articleId}`)
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

export function reviewMedicalPublishArticle(articleId: number, data: {
  action: 'approve' | 'reject'
  comment?: string
}) {
  return request.post<R<void>>(`/content/articles/${articleId}/medical-publish-review`, data)
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

export function distributeContentArticleToIndustrySite(articleId: number, siteId: number) {
  return request.post<R<DistributionTask>>(`/content/articles/${articleId}/distribute-to-industry-site`, { siteId })
}

export function distributeContentArticleToForumSite(articleId: number, siteId: number, fid?: number | null) {
  return request.post<R<DistributionTask>>(`/content/articles/${articleId}/distribute-to-forum-site`, {
    siteId,
    fid: fid || undefined,
  })
}

export function distributeContentArticleToGeoSite(articleId: number, brandId: number) {
  return request.post<R<DistributionTask>>(`/content/articles/${articleId}/distribute-to-geo-site`, null, {
    params: { brandId },
  })
}

export function distributeContentArticleToAgentSite(articleId: number, brandId: number) {
  return distributeContentArticleToGeoSite(articleId, brandId)
}

export interface BatchArticlePublishRequest {
  articleIds: number[]
  publishMode: 'now' | 'scheduled'
  scheduledAt?: string
  intervalMinutes: number
  platformConcurrency: number
  industrySiteId?: number
  forumSiteId?: number
  forumFid?: number
}

export interface BatchArticlePublishItem {
  id: number
  articleId: number
  articleTitle?: string | null
  projectName?: string | null
  platformKey: 'agent_site' | 'industry_site' | string
  contentStyle?: string | null
  targetSiteId?: number | null
  targetSiteName?: string | null
  targetForumFid?: number | null
  targetBrandId?: number | null
  plannedAt: string
  publishedAt?: string | null
  status: 'pending' | 'running' | 'success' | 'failed' | string
  distributionTaskId?: number | null
  errorMessage?: string | null
}

export interface BatchArticlePublishResponse {
  jobId: number
  jobName?: string | null
  jobSource?: 'manual' | 'auto' | string
  publishMode: 'now' | 'scheduled' | string
  status: string
  scheduledAt?: string | null
  intervalMinutes: number
  totalCount: number
  successCount: number
  failedCount: number
  items: BatchArticlePublishItem[]
}

export interface BatchArticlePublishJobSummary {
  jobId: number
  jobName?: string | null
  jobSource?: 'manual' | 'auto' | string
  publishMode: 'now' | 'scheduled' | string
  status: string
  scheduledAt?: string | null
  intervalMinutes: number
  totalCount: number
  successCount: number
  failedCount: number
  createdBy?: number | null
  createdAt?: string | null
  startedAt?: string | null
  finishedAt?: string | null
}

export function submitBatchArticlePublish(data: BatchArticlePublishRequest) {
  return request.post<R<BatchArticlePublishResponse>>('/content/articles/batch-publish', data)
}

export function getBatchArticlePublishJobs(params?: { current?: number; size?: number; status?: string; jobSource?: 'manual' | 'auto' | 'all' }) {
  return request.get<R<PageResult<BatchArticlePublishJobSummary>>>('/content/articles/batch-publish', { params })
}

export function getBatchArticlePublish(jobId: number) {
  return request.get<R<BatchArticlePublishResponse>>(`/content/articles/batch-publish/${jobId}`)
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

export function getArticleDistribution(articleId: number, params?: { targetKind?: string }) {
  return request.get<R<{ articleId: number; articleStatus: string; attempts: DistributionTask[] }>>(`/content/articles/${articleId}/distribution`, { params })
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

export function confirmSemiAutoDistribution(taskId: number, data: { publishedUrl?: string | null; responsePayload?: string }) {
  return request.patch<R<DistributionTask>>(`/content/distribution-tasks/${taskId}/confirm-semi-auto`, data)
}

export function abandonSemiAutoDistribution(taskId: number, data: { reason?: string }) {
  return request.patch<R<DistributionTask>>(`/content/distribution-tasks/${taskId}/abandon-semi-auto`, data)
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

export function getSelfMediaAccountPlatformOptions(brandId: number) {
  return request.get<R<SelfMediaAccountPlatformOption[]>>(`/content/brands/${brandId}/self-media-account-platforms`)
}

export function getSelfMediaScheduleCapabilities() {
  return request.get<R<SelfMediaScheduleCapability[]>>('/content/self-media-schedule-capabilities')
}

export function updateSelfMediaScheduleCapability(platform: string, data: {
  verificationStatus: string
  supportsSchedule: boolean
  minDelayMinutes?: number | null
  maxDelayMinutes?: number | null
  saveCreatesSchedule?: boolean | null
  supportsCancel?: boolean | null
  supportsModify?: boolean | null
  supportsPublishCheck?: boolean | null
  v1Strategy?: string | null
  selectorStatus?: string | null
  evidenceJson?: string | null
  notes?: string | null
}) {
  return request.put<R<SelfMediaScheduleCapability>>(`/content/self-media-schedule-capabilities/${platform}`, data)
}

export function createSelfMediaAccount(brandId: number, data: {
  platform: string
  accountName: string
  accountIdentity?: 'personal' | 'enterprise'
  platformAccountId?: string
  status?: 'active' | 'disabled'
}) {
  return request.post<R<SelfMediaAccount>>(`/content/brands/${brandId}/self-media-accounts`, data)
}

export function updateSelfMediaAccount(id: number, data: {
  platform: string
  accountName: string
  accountIdentity?: 'personal' | 'enterprise'
  platformAccountId?: string
  status?: 'active' | 'disabled'
}) {
  return request.put<R<SelfMediaAccount>>(`/content/self-media-accounts/${id}`, data)
}

export function checkSelfMediaAccountAuth(id: number) {
  return request.post<R<SelfMediaAccount>>(`/content/self-media-accounts/${id}/check-auth`)
}

export function deleteSelfMediaAccount(id: number) {
  return request.delete<R<void>>(`/content/self-media-accounts/${id}`)
}

export function destroySelfMediaCookieCredential(id: number) {
  return request.delete<R<SelfMediaAccount>>(`/content/self-media-accounts/${id}/cookie-credential`)
}

export function distributeContentArticleToSelfMediaAccount(articleId: number, data: {
  selfMediaAccountId: number
  coverMaterialId?: number
  imageMaterialIds?: number[]
  privateStatus?: number | string
  downloadType?: number | string
  platformOptions?: DouyinPlatformOptions | Record<string, unknown>
  requestId: string
}) {
  return request.post<R<DistributionTask>>(`/content/articles/${articleId}/distribute-to-self-media`, data)
}

export function getSelfMediaPublishSchedules(params?: {
  brandId?: number
  platform?: string
  status?: string
  failureCode?: string
  articleId?: number
  selfMediaAccountId?: number
  current?: number
  size?: number
}) {
  return request.get<R<PageResult<SelfMediaPublishSchedule>>>('/content/self-media-schedules', { params })
}

export function getSelfMediaAutomationOverview() {
  return request.get<R<SelfMediaAutomationOverview>>('/content/self-media-automation/overview')
}

export function createSelfMediaPublishSchedules(data: {
  brandId: number
  articleIds: number[]
  selfMediaAccountIds: number[]
  windowStart: string
  windowEnd: string
  scheduleStrategy?: string
  minIntervalMinutes?: number
}) {
  return request.post<R<SelfMediaPublishScheduleCreateResponse>>('/content/self-media-schedules', data, {
    headers: {
      'Idempotency-Key': `manual-schedule-${Date.now()}-${Math.random().toString(16).slice(2)}`,
    },
  })
}

export function previewSelfMediaPlatformQuickSchedule(data: {
  articleId: number
  platform: string
}) {
  return request.post<R<SelfMediaPlatformQuickScheduleResponse>>('/content/self-media-schedules/platform-quick-preview', data)
}

export function createSelfMediaPlatformQuickSchedule(data: {
  articleId: number
  platform: string
  replaceNextScheduled?: boolean
}) {
  return request.post<R<SelfMediaPlatformQuickScheduleResponse>>('/content/self-media-schedules/platform-quick-create', data, {
    headers: {
      'Idempotency-Key': `platform-quick-schedule-${data.articleId}-${data.platform}-${Date.now()}-${Math.random().toString(16).slice(2)}`,
    },
  })
}

export function dispatchSelfMediaPlatformQuickSchedule(data: {
  articleId: number
  platform: string
}) {
  return request.post<R<SelfMediaPlatformQuickScheduleResponse>>('/content/self-media-schedules/platform-quick-dispatch', data, {
    headers: {
      'Idempotency-Key': `platform-quick-dispatch-${data.articleId}-${data.platform}-${Date.now()}-${Math.random().toString(16).slice(2)}`,
    },
  })
}

export function cancelSelfMediaPublishSchedule(id: number, data?: { reason?: string }) {
  return request.post<R<SelfMediaPublishSchedule>>(`/content/self-media-schedules/${id}/cancel`, data ?? {})
}

export function confirmSelfMediaPublishSchedulePublished(id: number, data?: { platformPublishedUrl?: string }) {
  return request.post<R<SelfMediaPublishSchedule>>(`/content/self-media-schedules/${id}/confirm-published`, data ?? {})
}

export function confirmSelfMediaPublishScheduleFailed(id: number, data?: { failureCode?: string; failureMessage?: string }) {
  return request.post<R<SelfMediaPublishSchedule>>(`/content/self-media-schedules/${id}/confirm-publish-failed`, data ?? {})
}

export function retrySelfMediaPublishScheduleNow(id: number) {
  return request.post<R<SelfMediaPublishSchedule>>(`/content/self-media-schedules/${id}/retry-now`)
}

export function markSelfMediaPublishScheduleManualRequired(id: number, data?: { reason?: string }) {
  return request.post<R<SelfMediaPublishSchedule>>(`/content/self-media-schedules/${id}/mark-manual-required`, data ?? {})
}

export function recheckSelfMediaPublishScheduleResult(id: number) {
  return request.post<R<SelfMediaPublishSchedule>>(`/content/self-media-schedules/${id}/recheck-publish-result`)
}
