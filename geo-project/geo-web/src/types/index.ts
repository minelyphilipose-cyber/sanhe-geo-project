/* ====================================================
   API 响应包装
   ==================================================== */
export interface R<T = any> {
  code: number
  message: string
  data: T
}

export interface PageResult<T = any> {
  records: T[]
  total: number
  current: number
  size: number
}

/* ====================================================
   璁よ瘉
   ==================================================== */
export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  accessToken: string
  refreshToken: string
  user: UserInfo
}

export interface UserInfo {
  id: number
  username: string
  displayName: string
  role: RoleType
  partnerId: number | null
  phone: string | null
  email: string | null
  avatarUrl: string | null
  permissions?: string[]
}

/* ====================================================
   鏋氫妇/鑱斿悎绫诲瀷
   ==================================================== */
export type RoleType =
  | 'super_admin'
  | 'sales'
  | 'operator'
  | 'delivery_manager'
  | 'manager'
  | 'partner'
  | 'partner_staff'
  | 'partner_viewer'

export type PackageType = 'trial_6980' | 'standard_12800' | 'growth_26800'

export type ProjectStatus = 'pending_start' | 'active' | 'paused' | 'expired'

export type ProjectStage =
  | 'pending_start'
  | 'collecting_materials'
  | 'baseline_diagnosis'
  | 'executing'
  | 'needs_renewal'
  | 'high_risk'
  | 'dispute_handling'
  | 'completed'

export type OwnerType = 'direct' | 'partner' | 'joint'

export type PlatformPriority = 'P0' | 'P1' | 'P2'

export type PlatformHealth =
  | 'normal' | 'slow_response' | 'high_failure'
  | 'degraded' | 'manual_takeover' | 'maintenance'

export type ReportType =
  | 'biweekly' | 'monthly' | 'quarterly' | 'management'

export type ReportStatus =
  | 'generating' | 'draft' | 'intercepted' | 'published' | 'superseded' | 'archived'

export type PartnerLevel = 'level_29800' | 'level_59800' | 'level_99800'

export type TrainingStatus = 'not_trained' | 'in_training' | 'passed' | 'production_enabled'

export type AlertSeverity = 'info' | 'warn' | 'error' | 'critical'

/* ====================================================
   业务实体
   ==================================================== */
export interface Company {
  id: number
  companyName: string
  contactName?: string | null
  contactPhone?: string | null
  industry: string | null
  industryTags?: string | string[] | null
  businessDirection?: string | null
  competitors?: string | null
  officialWebsite?: string | null
  officialAccount?: string | null
  videoAccount?: string | null
  douyinAccount?: string | null
  city: string | null
  provinceCode?: string | null
  provinceName?: string | null
  cityCode?: string | null
  cityName?: string | null
  districtCode?: string | null
  districtName?: string | null
  serviceArea?: string | null
  ownerType: OwnerType
  sourceType?: 'internal' | 'partner' | null
  partnerId: number | null
  partnerName?: string | null
  referralSource: string | null
  salesOwnerId: number | null
  status?: string
  remark?: string | null
  createdBy?: number | null
  createdAt: string
}

export interface Brand {
  id: number
  companyId: number
  industry: string
  brandName: string
  brandShortName?: string | null
  brandSlug: string
  mainBusiness: string | null
  coreProducts?: string | null
  brandPositioning?: string | null
  serviceArea: string | null
  provinceCode?: string | null
  provinceName?: string | null
  cityCode?: string | null
  cityName?: string | null
  districtCode?: string | null
  districtName?: string | null
  website: string | null
  officialAccount?: string | null
  videoAccount?: string | null
  douyinAccount?: string | null
  phone: string | null
  publicPhone?: string | null
  publicAddress?: string | null
  wechat: string | null
  description: string | null
  businessIntro?: string | null
  brandQualificationDescription?: string | null
  brandCaseDescription?: string | null
  standardStatement?: {
    positioning?: string | null
    selling_points?: string[] | null
    differentiation?: string | null
    brand_paragraph?: string | null
  } | string | null
  statementStatus?: 'pending' | 'draft' | 'locked' | string | null
  statementGeneratedAt?: string | null
  statementLockedAt?: string | null
  statementLockedBy?: number | null
  statementVersion?: number | null
  statementHistory?: any[] | null
  forbiddenPhrases: string | string[] | null
  geoSiteCode?: string | null
  geoSiteStatus?: 'active' | 'disabled' | string | null
  industrySiteName?: string | null
  industrySiteCode?: string | null
  status?: string
  createdAt: string
  updatedAt: string
}

export interface BrandMaterial {
  id: number
  brandId: number
  folderId?: number | null
  category: string
  fileName: string
  fileType?: string | null
  fileUrl: string
  objectKey: string
  fileSize?: number | null
  createdBy: number
  createdAt: string
  updatedAt: string
}

export interface BrandImageFolder {
  id: number
  brandId: number
  folderName: string
  description?: string | null
  status: 'active' | 'disabled' | string
  isDefault?: boolean
  projectIds: number[]
  tags: string[]
  materials: BrandMaterial[]
  materialCount?: number
  projectRelated?: boolean
  createdAt?: string
  updatedAt?: string
}

export interface BrandProfileVersion {
  id: number
  brandId: number
  versionNo: number
  snapshotJson: string
  changeReason?: string | null
  createdBy: number
  createdAt: string
}

export interface Project {
  id: number
  projectCode?: string
  companyId?: number | null
  brandId: number | null
  projectName: string
  projectAliases?: string | null
  planKeywordGroupLimit?: number | null
  planKeywordGroupLimitA?: number | null
  planKeywordGroupLimitB?: number | null
  planKeywordGroupLimitC?: number | null
  planMonthlyReportDepth?: string | null
  planQuarterlyReportDepth?: string | null
  planConsultantIntensity?: string | null
  planCompetitorInsightDepth?: string | null
  planMediaDistributionIntensity?: string | null
  planCommitmentTargetIntensity?: string | null
  planTargetMetricType?: string | null
  planTargetMetricValue?: number | null
  planTargetWindowDays?: number | null
  status: ProjectStatus
  stage: ProjectStage
  startDate: string | null
  activatedAt?: string | null
  endDate: string | null
  expiredAt?: string | null
  primaryGoal: string | null
  customerRequirements?: string[]
  ownerType: OwnerType
  sourceType?: 'internal' | 'partner'
  partnerId: number | null
  provinceCode?: string | null
  provinceName?: string | null
  cityCode?: string | null
  cityName?: string | null
  districtCode?: string | null
  districtName?: string | null
  targetRegions?: string | string[] | null
  targetAudience?: string | null
  customStatement?: string | null
  contentTone?: string | null
  preferredAngles?: string | string[] | null
  extraForbiddenPhrases?: string | string[] | null
  contentNote?: string | null
  discountRateSnapshot?: number | null
  deductionAmount?: number | null
  deductionTxnNo?: string | null
  deliveryMode: string
  remark?: string | null
  createdAt: string
  selectedKeywordGroupIds?: number[]
  selectedKeywordGroupCount?: number
  selectedKeywordSavedKeywords?: number
  selectedKeywordSavedKeywordsA?: number
  selectedKeywordSavedKeywordsB?: number
  selectedKeywordSavedKeywordsC?: number
  selectedKeywordGroups?: KeywordGroup[]
  channelAllocations?: ProjectChannelAllocationItem[]
  allocationVersion?: number
  // 鍏宠仈灞曠ず瀛楁
  brandName?: string
  companyName?: string
}

export interface ProjectChannelAllocationItem {
  channelCode: string
  channelName: string
  periodType?: string | null
  enabled: boolean
  quotaLimit: number
  activeAllocatedCount?: number
  currentProjectAllocatedCount: number
  remainingCount?: number
  inputMax?: number
}

export interface ProjectChannelAllocationQuota {
  companyId: number
  excludeProjectId?: number | null
  allocationVersion: number
  note?: string | null
  items: ProjectChannelAllocationItem[]
}

export interface ProjectKeywordGroupQuota {
  companyId: number
  excludeProjectId?: number | null
  quotaLimit: number
  quotaLimitA: number
  quotaLimitB: number
  quotaLimitC: number
  activeAllocatedCount: number
  activeAllocatedCountA: number
  activeAllocatedCountB: number
  activeAllocatedCountC: number
  currentProjectAllocatedCount: number
  currentProjectAllocatedCountA: number
  currentProjectAllocatedCountB: number
  currentProjectAllocatedCountC: number
  remainingCount: number
  remainingCountA: number
  remainingCountB: number
  remainingCountC: number
  inputMaxA: number
  inputMaxB: number
  inputMaxC: number
}

export interface CompanyAccount {
  id: number
  companyId: number
  currentBalance: number
  totalRecharge: number
  totalDeduction: number
  currency: string
  status: string
}

export interface CompanyAccountTxn {
  id: number
  companyId: number
  accountId: number
  txnNo: string
  txnType: string
  bizType: string
  amount: number
  balanceBefore: number
  balanceAfter: number
  relatedProjectId?: number | null
  operatorUserId: number
  offlineReference?: string | null
  reason: string
  remark?: string | null
  createdAt: string
}

export interface CompanyPackageBinding {
  id: number
  companyId: number
  packagePlanId: number
  packageType: string
  packageName: string
  standardPrice: number
  serviceMonths: number
  keywordGroupLimit: number
  keywordGroupLimitA: number
  keywordGroupLimitB: number
  keywordGroupLimitC: number
  channelQuotaSnapshot: string
  status: 'active' | 'inactive' | string
  activeFlag?: number | null
  boundAt: string
  unboundAt?: string | null
  createdAt?: string
  updatedAt?: string
}

export interface PlatformConfig {
  id: number
  platformCode: string
  platformName: string
  providerName: string
  priorityLevel: PlatformPriority
  apiEnabled: boolean
  appCaptureEnabled: boolean
  isActive: boolean
  healthStatus: PlatformHealth
  failureRate: number
  lastSuccessTime: string | null
  currentActiveChannel: string
  alertLevel: string
}

export interface AIPlatformConfigItem {
  id: number
  platformCode: string
  platformName: string
  priorityLevel: 'P0' | 'P1' | 'P2'
  apiKey: string
  primaryKeyRef?: string | null
  backupKeyRef?: string | null
  backupProviderName?: string | null
  backupApiUrl?: string | null
  backupModelId?: string | null
  apiUrl: string
  modelId: string
  lowModelId?: string | null
  modelName: string
  concurrencyLimit?: number | null
  enabled: boolean
  enabledForPresale?: boolean
  presaleEvaluateEnabled?: boolean
  enabledForArticle?: boolean
  enabledForGeoQuestion?: boolean
  enabledForQuestionPoll?: boolean
  maxRetry?: number | null
  timeoutMs?: number | null
  rateLimitQps?: number | null
  degraded: boolean
  degradedReason?: string | null
  remark?: string | null
  createdAt?: string
  updatedAt?: string
}

export interface Report {
  id: number
  projectId: number
  reportType: ReportType
  versionNo?: number
  periodStart: string | null
  periodEnd: string | null
  shareToken: string | null
  shareExpiresAt?: string | null
  pdfUrl: string | null
  pdfGeneratedAt?: string | null
  visibility: string
  pairReportId?: number | null
  isLatest?: boolean
  status: ReportStatus
  stageAdvice?: string | null
  supersededBy?: number | null
  publishedAt: string | null
  publishedBy?: number | null
  createdBy?: number | null
  createdAt: string
  updatedAt?: string
  // 鍏宠仈
  projectName?: string
  brandName?: string
}

export interface ProjectDashboardShare {
  id: number
  projectId: number
  shareCode: string
  status: 'active' | 'disabled' | string
  createdBy?: number | null
  createdAt: string
  disabledAt?: string | null
}

export interface ProjectDashboardSummary {
  hitTotal: number
  platformCount: number
  contactTotal: number
  siteTotal: number
}

export interface ProjectDashboardPlatformItem {
  platformCode: string
  platformName: string
  hitCount: number
  contactCount: number
  siteCount: number
}

export interface ProjectDashboardWordItem {
  word: string
  frequency: number
}

export interface ProjectDashboardContentProgressItem {
  key: 'generated' | 'approved' | 'distributed' | 'published' | 'pending' | 'generation_failed' | 'distribution_failed' | string
  label: string
  value: number
  description?: string
}

export interface ProjectDashboardContentProgress {
  generatedCount: number
  approvedCount: number
  distributedCount: number
  publishedCount: number
  pendingCount: number
  generationFailureCount?: number
  distributionFailureCount?: number
  items: ProjectDashboardContentProgressItem[]
}

export interface ProjectDashboardAdvice {
  id?: number
  projectId?: number
  summary?: string
  highlights?: string[]
  improvementDirections?: string[]
  nextActions?: string[]
  status?: 'draft' | 'published' | string
  publishedAt?: string
  updatedAt?: string
}

export interface ProjectDashboardTrendItem {
  date: string
  articleCreated: number
  articlePublished: number
  hitCount: number
  contactCount?: number
  siteCount?: number
}

export interface ProjectDashboardDetailItem {
  id: number
  questionText: string
  platformCode: string
  platformName: string
  batchDate: string
  hasSnapshot: boolean
  platformUrl?: string | null
}

export interface ProjectDashboardSummaryResponse {
  projectName: string
  brandName?: string | null
  projectStage?: string | null
  startDate?: string | null
  endDate?: string | null
  monitorPlatformCount?: number
  monitorQuestionCount?: number
  days?: number
  summary: ProjectDashboardSummary
  platforms: ProjectDashboardPlatformItem[]
  wordCloud: ProjectDashboardWordItem[]
  contentProgress?: ProjectDashboardContentProgress
  advice?: ProjectDashboardAdvice | null
  refreshedAt?: string | null
}

export interface ProjectDashboardRefreshResponse {
  status: 'SUCCESS' | 'RUNNING' | string
  message?: string
  startedAt?: string
  refreshedAt?: string
}

export interface ProjectDashboardSnapshotStatusResponse {
  projectId: number
  refreshedAt?: string
}

export interface ProjectDashboardTrendResponse {
  items: ProjectDashboardTrendItem[]
}

export interface ProjectDashboardDetailResponse {
  total: number
  page: number
  size: number
  maxViewable: number
  items: ProjectDashboardDetailItem[]
}

export interface Partner {
  id: number
  partnerName: string
  partnerLevel: PartnerLevel
  discountRate: number
  prepaidBalance: number
  totalRecharge: number
  totalDeduction: number
  status: string
  trainingStatus: TrainingStatus
  contactName: string | null
  contactPhone: string | null
  city: string | null
}

export interface SystemAlert {
  id: number
  alertType: string
  severity: AlertSeverity
  source: string
  message: string
  context: any
  isResolved: boolean
  resolvedBy: number | null
  resolvedAt: string | null
  createdAt: string
}

export interface DispatchDashboardMetrics {
  activeProjectCount: number
  dueTaskCount: number
  runningTaskCount: number
  completedTaskCount: number
  failedTaskCount: number
  deadLetterPendingCount: number
  platformExceptionCount: number
  avgTaskDurationMs: number
  rangeLabel: string
}

export interface DispatchTaskItem {
  id: number
  taskNo: string
  projectId: number
  projectName: string
  platformCode?: string | null
  currentChannel?: 'primary' | 'backup_key' | 'backup_provider' | null
  taskType: string
  priorityLevel: number
  status: string
  windowStart: string
  windowEnd: string
  dueTime: string
  retryCount: number
  maxRetry?: number | null
  firstStartedAt?: string | null
  lastStartedAt?: string | null
  nextRetryAt?: string | null
  timeoutAt?: string | null
  finishedAt?: string | null
  lastError?: string | null
  errorContext?: string | null
  payloadJson?: string | null
  createdAt: string
  updatedAt?: string | null
}

export interface KeywordWordItem {
  id?: number
  wordText: string
  source?: 'system' | 'custom' | string
  sortOrder?: number
}

export interface KeywordGroupColumns {
  areaWords: KeywordWordItem[]
  /** @deprecated V1.6 删除，仅兼容旧 payload */
  regionWords?: KeywordWordItem[]
  prefixWords: KeywordWordItem[]
  coreWords: KeywordWordItem[]
  industryWords: KeywordWordItem[]
  suffixWords: KeywordWordItem[]
  coreWordsA: KeywordWordItem[]
  compareWords: KeywordWordItem[]
  coreWordsB: KeywordWordItem[]
}

export interface KeywordGroupPayload {
  companyId: number
  projectId?: number | null
  name?: string
  type: string
  areaEnabled?: boolean
  functionIndustryTag?: string | null
  remark?: string
  count?: number
  llmGenerationToken?: string
  llmQuestions?: LlmQuestionItem[]
  resultKeywords?: KeywordPreviewItem[]
  columns: KeywordGroupColumns
}

export interface KeywordGroup {
  id: number
  companyId: number
  companyName?: string | null
  projectId?: number | null
  projectName?: string | null
  packageType?: string | null
  name: string
  type: string
  typeLabel?: string | null
  legacyType?: boolean
  areaEnabled?: boolean | null
  functionIndustryTag?: string | null
  remark?: string | null
  estimatedKeywordCount?: number
  savedKeywordCount?: number
  savedKeywordCountA?: number
  savedKeywordCountB?: number
  savedKeywordCountC?: number
  columns?: KeywordGroupColumns
  llmQuestions?: LlmQuestionItem[]
  createdAt: string
  updatedAt: string
}

export interface KeywordPreviewItem {
  text: string
  sourceType: 'cartesian' | 'llm' | string
  seedText?: string | null
  questionTier?: 'A' | 'B' | 'C' | string
}

export interface KeywordGroupQuestion {
  id: number
  groupId: number
  questionCode?: string | null
  questionText: string
  sceneCode?: string | null
  questionTier?: 'A' | 'B' | 'C' | string
  priority?: string | null
  monitorFrequency?: string | null
  scoreRelevance?: number | null
  scoreIntent?: number | null
  scoreCompetition?: number | null
  scoreConversion?: number | null
  scoreCoverage?: number | null
  totalScore?: number | null
  articleGenerationNote?: string | null
  sortOrder?: number | null
  createdAt?: string
  updatedAt?: string
}

export interface KeywordGroupImportResult {
  group: KeywordGroup
  importedCount: number
  countA: number
  countB: number
  countC: number
}

export interface LlmQuestionItem {
  questionText: string
  seedText: string
}

export interface KeywordPreviewResult {
  totalEstimated: number
  totalAvailable: number
  totalGenerated: number
  filteredCount?: number
  items: KeywordPreviewItem[]
}

export interface KeywordLlmQuestionGenerateResult {
  generationToken: string
  seedText: string
  newQuestions: string[]
}

export interface KeywordAffixWord {
  id: number
  type: string
  affixKind?: 'area' | 'prefix' | 'suffix' | 'industry' | 'compare' | 'type' | string
  wordText: string
  subCategory?: string | null
  visualTag?: string | null
  industryTag?: string | null
  isManual?: boolean
  isTemporary?: boolean
  scopeType?: string | null
  scopeId?: number | null
  sortOrder: number
  enabled: boolean
  createdAt?: string
  updatedAt?: string
}

export interface KeywordTypeOption {
  value: string
  label: string
  legacy?: boolean
}

export interface KeywordColumnVisibility {
  area: boolean
  prefix: boolean
  core: boolean
  industry: boolean
  suffix: boolean
  compareCore: boolean
  compareWord: boolean
}

export interface KeywordTypeConfig {
  type: string
  label: string
  description?: string
  structure: 'standard' | 'compare' | string
  areaEnabledByDefault: boolean
  industryRequired: boolean
  supportsManualAdd: boolean
  functionIndustryRequired: boolean
  columns: KeywordColumnVisibility
  requiredColumns: KeywordColumnVisibility
}

export interface KeywordAffixWordOptionResult {
  areaWords: KeywordAffixWord[]
  prefixWords: KeywordAffixWord[]
  suffixWords: KeywordAffixWord[]
  industryWords: KeywordAffixWord[]
  compareWords: KeywordAffixWord[]
  typeOptions: KeywordTypeOption[]
  typeConfigs: KeywordTypeConfig[]
  currentTypeConfig?: KeywordTypeConfig | null
}

export interface BrandStatementContent {
  positioning?: string | null
  selling_points?: string[] | null
  differentiation?: string | null
  brand_paragraph?: string | null
}

export interface BrandStatementView {
  brandId: number
  statementStatus?: 'pending' | 'draft' | 'locked' | string | null
  statementVersion?: number | null
  statementGeneratedAt?: string | null
  statementLockedAt?: string | null
  statementLockedBy?: number | null
  standardStatement?: BrandStatementContent | null
  promptStatement?: BrandStatementContent | null
  statementHistory?: Array<{
    version: number
    content: BrandStatementContent
    created_at: string
    created_by?: number | null
    change_source: 'auto_generated' | 'manual_edit' | string
  }>
}

export interface DispatchPlatformHealthItem {
  id: number
  platformCode: string
  platformName: string
  priorityLevel: 'P0' | 'P1' | 'P2'
  enabled: boolean
  rpmLimit: number
  tpmLimit: number
  concurrencyLimit?: number | null
  activePermitCount?: number | null
  degraded: boolean
  degradedReason?: string | null
  currentHealthStatus?: string | null
  lastFailureAt?: string | null
  exceptionCount: number
}

export interface LlmPoolSnapshot {
  enabled: boolean
  globalConcurrency: number
  activeGlobal: number
  featureConcurrency?: Record<string, number>
  activeFeatures?: Record<string, number>
  trackedLeases: number
  counters: Record<string, number>
  circuitBreakers?: Record<string, {
    failureCount?: number
    open?: boolean
    openedAtMillis?: number
  }>
}

export interface DispatchAlertItem {
  id: number
  alertCode: string
  taskId?: number | null
  projectId?: number | null
  projectName?: string
  severity: AlertSeverity
  status: 'open' | 'resolved'
  title: string
  content?: string | null
  retryCount: number
  contextJson?: string | null
  resolvedAt?: string | null
  resolvedBy?: number | null
  createdAt: string
}

export interface ArticleDraft {
  id: number
  batchId?: number | null
  projectId: number
  projectName?: string
  targetChannel?: string | null
  articleType: 'faq' | 'scenario_content' | 'industry_article' | 'stage_advice' | string
  articleTypeCode?: string | null
  contentStyle?: string | null
  channelGroupCode?: string | null
  channelSubCode?: string | null
  allocationMode?: string | null
  templateSource?: 'smart' | 'weighted' | 'custom' | 'fallback_default_prompt' | string | null
  promptTemplateId?: number | null
  promptTemplateVersionId?: number | null
  promptTemplateName?: string | null
  topic?: string | null
  topicAsQuestion?: string | null
  title: string
  status: 'approved' | 'distributing' | 'distributed' | 'published' | 'unpublished' | string
  hasRisk: boolean
  riskSeverity: 'none' | 'warn' | 'block' | string
  riskWordsJson?: string | null
  isDuplicateTitle: boolean
  duplicateScore?: number | null
  duplicateArticleId?: number | null
  currentVersionNo: number
  systemGenerated?: boolean | null
  generationMode?: 'batch' | 'single' | string | null
  createdAt: string
  updatedAt: string
  publishedAt?: string | null
}

export interface ArticleDraftVersion {
  id: number
  articleId: number
  versionNo: number
  title: string
  contentMarkdown: string
  promptSnapshot?: string | null
  inputSnapshot?: string | null
  modelPlatformCode?: string | null
  modelId?: string | null
  generatedBy: string
  createdBy?: number | null
  createdAt: string
}

export interface ArticleReviewLog {
  id: number
  articleId: number
  action: string
  comment?: string | null
  riskOverridden: boolean
  operatorId: number
  createdAt: string
}

export interface ArticlePublishLog {
  id: number
  articleId: number
  publishAction: 'publish' | 'unpublish' | string
  channelName?: string | null
  channelUrl?: string | null
  operatorId: number
  note?: string | null
  createdAt: string
}

export interface BatchArticleGenerationTaskDetail {
  id: number
  batchId: number
  projectId: number
  articleId?: number | null
  rowNo?: number | null
  articleIndexInRow?: number | null
  articleIndexInBatch?: number | null
  articleType?: string | null
  tone?: string | null
  contentStyle?: string | null
  channelGroupCode?: string | null
  channelSubCode?: string | null
  agentSiteModule?: string | null
  articleTypeCode?: string | null
  promptTemplateId?: number | null
  promptTemplateVersionId?: number | null
  promptTemplateName?: string | null
  allocationMode?: string | null
  templateSource?: 'smart' | 'weighted' | 'custom' | 'fallback_default_prompt' | string | null
  length?: string | null
  topic?: string | null
  topicAsQuestion?: string | null
  keywordGroupId?: number | null
  keywordGroupName?: string | null
  contentAngle?: string | null
  audiencePerspective?: string | null
  extraPrompt?: string | null
  status?: string | null
  qualityStatus?: string | null
}

export interface ArticleDetailResponse {
  article: ArticleDraft
  project: Project
  batchGenerationTask?: BatchArticleGenerationTaskDetail | null
  versions: ArticleDraftVersion[]
  reviewLogs: ArticleReviewLog[]
  publishLogs: ArticlePublishLog[]
}

export interface SelfMediaCookieStatusAccount {
  accountId: number
  platform: 'toutiao' | 'zhihu' | string
  accountName: string
  platformAccountId?: string | null
  accountStatus: string
  credentialStatus: 'VALID' | 'MISSING' | 'EXPIRED' | 'UNKNOWN' | string
  lastCapturedAt?: string | null
  canStartFill: boolean
  reason?: string | null
}

export interface SelfMediaCookieStatusItem {
  articleId: number
  brandId: number
  accounts: SelfMediaCookieStatusAccount[]
}

export interface SelfMediaCookieStatusBatchResponse {
  items: SelfMediaCookieStatusItem[]
}

export interface DistributionTask {
  id: number
  articleId?: number
  projectId?: number
  siteId: number | null
  siteName?: string
  domain?: string
  tier?: string
  attemptNo: number
  status: 'pending' | 'submitting' | 'submitted' | 'failed' | 'confirmed' | string
  integrationMethod: 'rest_api' | 'ftp' | 'email' | 'manual' | string
  targetKind?: string | null
  targetBrandId?: number | null
  brandOfficialSiteId?: number | null
  selfMediaAccountId?: number | null
  authorityMediaId?: number | null
  platformArticleId?: string | null
  externalStatus?: string | null
  reviewStatus?: string | null
  reviewFeedback?: string | null
  failureKind?: string | null
  nextRetryAt?: string | null
  requestPayload?: string | null
  responsePayload?: string | null
  publishedUrl?: string | null
  errorMessage?: string | null
  retryCount?: number
  operatorId?: number
  createdAt?: string
  finishedAt?: string | null
}

export interface AuthorityMediaResource {
  id: number
  resourceType: 'NEWS_MEDIA' | string
  externalResourceId: string
  name: string
  platform?: string | null
  industry?: string | null
  province?: string | null
  price: number
  status: number
  pcWeight?: number | null
  mWeight?: number | null
  newsResource?: number | null
  entranceLevel?: number | null
  includeCondition?: number | null
  publicationTime?: number | null
  weekendPublish?: number | null
  publishRate?: string | null
  inclusionRate?: number | null
  remark?: string | null
  entranceLink?: string | null
  caseLink?: string | null
  noDisclaimer?: number | null
  canSign?: number | null
  firstPublish?: number | null
  keep3Month?: number | null
  focalPic?: string | null
  uptime?: number | null
  updatedAt?: string | null
}

export interface WechatMpCapability {
  draftDistributionEnabled: boolean
  autoPublishEnabled?: boolean
  clientMode: string
  reason?: string | null
  description?: string | null
}

export interface WechatMpAuthUrl {
  authUrl: string
  expiresIn: number
}

export interface DouyinCapability {
  enabled: boolean
  mode: 'mock' | 'real' | string
  disabledReason?: string | null
  description?: string | null
}

export interface DouyinAuthUrl {
  authUrl: string
  expiresIn: number
}

export interface DouyinPlatformOptions {
  text?: string
}

export interface SelfMediaAccount {
  id: number
  brandId: number
  platform: 'wechat_mp' | string
  accountName: string
  platformAccountId: string
  avatarUrl?: string | null
  qrcodeUrl?: string | null
  status: 'active' | 'expired' | 'revoked' | 'disabled' | string
  lastAuthCheckedAt?: string | null
  lastAuthError?: string | null
}

export interface PublishQuota {
  month: string
  monthUsed: number
  monthLimit: number
  weekUsed: number
  weekLimit: number
  allowedSiteTiers: string[]
}

export interface RecommendedSite {
  siteId: number
  siteName: string
  domain: string
  iconUrl?: string | null
  tier: 'S0' | 'S1' | 'S2' | string
  status: string
  integrationMethod: 'rest_api' | 'ftp' | 'email' | 'manual' | string
  currentHealthStatus: string
  failureRate?: number | null
  successRate30d: number
  matchType: 'exact' | 'general' | string
  industryTags?: string[] | null
  contentConstraints?: string | null
}

export interface RecommendedSitesResponse {
  fallbackToGeneral: boolean
  sites: RecommendedSite[]
}

export interface PublishSite {
  id: number
  siteName: string
  siteCode: string
  domain: string
  iconUrl?: string | null
  industryTags?: string | string[] | null
  tier: 'S0' | 'S1' | 'S2' | string
  status: 'active' | 'suspended' | 'maintenance' | string
  integrationMethod: 'rest_api' | 'ftp' | 'email' | 'manual' | string
  apiEndpoint?: string | null
  httpMethod?: 'POST' | 'PUT' | string | null
  authType?: 'api_key' | 'bearer_token' | 'basic_auth' | 'oauth2' | string | null
  credentialRef?: string | null
  apiCredentialEncrypted?: string | null
  requestHeaderTemplate?: string | null
  requestBodyTemplate?: string | null
  responseUrlPath?: string | null
  contentConstraints?: string | null
  currentHealthStatus?: string | null
  lastFailureAt?: string | null
  failureRate?: number | null
  remark?: string | null
  createdAt?: string
  updatedAt?: string
}

export interface ActivityLog {
  id: number
  userId: number | null
  operatorName: string
  action: string
  targetType: string
  targetId: number | null
  targetName?: string | null
  detailJson: string | null
  ipAddress: string | null
  createdAt: string
}

export interface PackagePlan {
  id: number
  packageType: string
  packageName: string
  standardPrice: number
  serviceMonths: number
  keywordGroupLimit: number
  keywordGroupLimitA: number
  keywordGroupLimitB: number
  keywordGroupLimitC: number
  monthlyReportDepth: string
  quarterlyReportDepth: string
  consultantIntensity: string
  competitorInsightDepth: string
  mediaDistributionIntensity: string
  commitmentTargetIntensity: string
  targetMetricType: string
  targetMetricValue: number
  targetWindowDays: number
  enabled: boolean
  sortOrder: number
  remark?: string | null
  channelQuotaConfigs?: PackageChannelQuotaConfig[]
  createdAt?: string
  updatedAt?: string
}

export interface PackageChannelQuotaConfig {
  id?: number
  packagePlanId?: number
  channelCode: 'official_site' | 'industry_site' | 'forum' | 'self_media' | 'authority_media' | string
  periodType: 'day' | 'week' | 'month' | 'total' | string
  quotaLimit: number
  enabled: boolean
  createdAt?: string
  updatedAt?: string
}

export interface CompanyKeywordGroupQuota {
  companyId: number
  packageBindingId?: number | null
  packageName?: string | null
  activeBinding: boolean
  quotaLimit: number
  quotaLimitA: number
  quotaLimitB: number
  quotaLimitC: number
  usedCount: number
  usedCountA: number
  usedCountB: number
  usedCountC: number
  remainingCount: number
  remainingCountA: number
  remainingCountB: number
  remainingCountC: number
  usageRate: number
}

export interface CompanyDistributionQuota {
  companyId: number
  hasLimitMismatch: boolean
  items: CompanyDistributionQuotaItem[]
}

export interface CompanyDistributionQuotaItem {
  channelCode: 'official_site' | 'industry_site' | 'forum' | 'self_media' | 'authority_media' | string
  channelName: string
  enabled: boolean
  periodType?: 'day' | 'week' | 'month' | 'total' | string | null
  periodKey?: string | null
  quotaLimit: number
  usageQuotaLimit?: number | null
  limitMismatch: boolean
  usedCount: number
  remainingCount: number
  usageRate: number
  nextResetAt?: string | null
  status: 'not_configured' | 'normal' | 'warning' | 'exceeded' | string
}

export interface PackageContentConfig {
  id?: number
  packageType?: string
  articleType: 'faq' | 'scenario_content' | 'industry_article' | 'stage_advice' | string
  articlesPerBatch: number
  questionsPerArticle: number
  publishSiteTier: 'S0' | 'S1' | 'S2' | string
  publishSiteCount: number
  isActive: boolean
  createdAt?: string
  updatedAt?: string
}

/* ====================================================
   璺敱 meta 鎵╁睍
   ==================================================== */
export interface RouteMeta {
  title?: string
  roles?: RoleType[]
  permissions?: string[]
  requiresAuth?: boolean
  icon?: string
  hidden?: boolean
}

export * from './presale'
