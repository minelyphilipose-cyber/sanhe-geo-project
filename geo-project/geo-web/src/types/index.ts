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
  partnerName?: string | null
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

export type PackageType = 'trial_6980' | 'standard_12800' | 'growth_26800'

export type ProjectStatus =
  | 'draft'
  | 'pending_start'
  | 'submitted'
  | 'rejected'
  | 'approved_pending_setup'
  | 'setup_ready'
  | 'active'
  | 'paused'
  | 'completed'
  | 'archived'
  | 'cancelled'
  | 'expired'

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

export type PartnerWorkflowStatus =
  | 'draft'
  | 'package_requested'
  | 'package_bound'
  | 'project_entry'
  | 'entry_completed'
  | 'submitted_to_hq'

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
  ownerId?: number | null
  ownerName?: string | null
  partnerStaffOwnerId?: number | null
  partnerStaffOwnerName?: string | null
  partnerStaffOwnerUsername?: string | null
  partnerStaffOwnerActive?: boolean | null
  activePackageBindingId?: number | null
  activePackageName?: string | null
  partnerWorkflowStatus?: PartnerWorkflowStatus | string | null
  partnerWorkflowUpdatedAt?: string | null
  status?: string
  remark?: string | null
  createdBy?: number | null
  createdAt: string
}

export interface Brand {
  id: number
  companyId: number
  industry: string
  complianceIndustryCode?: string | null
  coverableIndustries?: string[] | string | null
  allowThirdPartyPromotion?: boolean | null
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
  selfMediaPublishLocationName?: string | null
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
  medicalLicense?: string | null
  diagnosisScope?: string | null
  institutionType?: string | null
  practitionerInfoPublic?: string | null
  medicalAdReviewNo?: string | null
  complianceNotesMedical?: string | null
  geoSiteCode?: string | null
  geoSiteStatus?: 'active' | 'disabled' | string | null
  geoSiteName?: string | null
  geoSiteDomain?: string | null
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
  /** @deprecated Use publicUrl or preview-url endpoints instead of the stored object URL. */
  fileUrl: string
  publicUrl?: string | null
  objectKey: string
  fileSize?: number | null
  createdBy: number
  createdAt: string
  updatedAt: string
}

export interface BrandOffering {
  id: number
  brandId: number
  offeringName: string
  offeringAliases: string[]
  targetUsers?: string | null
  offeringIntro?: string | null
  qualificationDescription?: string | null
  remark?: string | null
  status: 'active' | 'disabled' | string
  priority: number
  useScenarios?: string | null
  medicalIndustryCode?: string | null
  medicalCategoryCode?: string | null
  medicalCategoryName?: string | null
  qualificationRef?: string | null
  medicalProjectEnabled?: boolean
  createdAt?: string
  updatedAt?: string
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
  planCoreQuestionLimit?: number | null
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
  coreKeywords?: string | null
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
  selectedCoreQuestionSavedKeywords?: number
  selectedKeywordGroups?: KeywordGroup[]
  channelAllocations?: ProjectChannelAllocationItem[]
  allocationVersion?: number
  thirdPartySource?: boolean | null
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
  coreQuestionQuotaLimit?: number
  activeAllocatedCoreQuestionCount?: number
  currentProjectAllocatedCoreQuestionCount?: number
  remainingCoreQuestionCount?: number
  inputMaxCoreQuestionCount?: number
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
  coreQuestionLimit?: number | null
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
  visibleChannelQuotas?: ProjectChannelAllocationItem[]
}

export interface PlatformConfig {
  id: number
  platformCode: string
  channelCode?: string | null
  usageScene?: string | null
  integrationType?: string | null
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
  channelCode?: string | null
  usageScene?: string | null
  integrationType?: string | null
  platformName: string
  platformHomeUrl?: string | null
  platformLogoUrl?: string | null
  platformLogoObjectKey?: string | null
  priorityLevel: 'P0' | 'P1' | 'P2'
  rpmLimit?: number | null
  tpmLimit?: number | null
  apiKey?: never
  apiKeyConfigured?: boolean
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
  enabledForMobileDashboard?: boolean
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

export interface ProjectDashboardComparisonMetric {
  current: number
  previous: number
  delta: number
  rate?: number | null
}

export interface ProjectDashboardComparison {
  available: boolean
  message?: string
  currentRefreshedAt?: string | null
  previousRefreshedAt?: string | null
  hitTotal?: ProjectDashboardComparisonMetric
  contactTotal?: ProjectDashboardComparisonMetric
  siteTotal?: ProjectDashboardComparisonMetric
  platformCount?: ProjectDashboardComparisonMetric
  monitorQuestionCount?: ProjectDashboardComparisonMetric
  articleCreated?: ProjectDashboardComparisonMetric
  articlePublished?: ProjectDashboardComparisonMetric
  articleIndexed?: ProjectDashboardComparisonMetric
}

export interface ProjectDashboardPlatformItem {
  platformId?: number | null
  platformCode: string
  platformName: string
  platformLogoUrl?: string | null
  platformLogoObjectKey?: string | null
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
  articleIndexed?: number
  hitCount: number
  contactCount?: number
  siteCount?: number
}

export interface ProjectDashboardDetailItem {
  id: number
  questionText: string
  platformId?: number | null
  platformCode: string
  platformName: string
  batchDate: string
  hasSnapshot: boolean
  platformUrl?: string | null
  platformLogoUrl?: string | null
  platformLogoObjectKey?: string | null
  answerText?: string | null
  matchType?: string | null
  effectiveHit?: boolean | null
  judgeStatus?: string | null
  hitLevel?: string | null
  hitSentiment?: string | null
  mentionType?: string | null
  judgeEvidence?: string | null
  judgeRiskReason?: string | null
  siteMentioned?: boolean
  contactMentioned?: boolean
  contactMentionCount?: number
}

export interface ProjectDashboardCompetitorRow {
  displayName: string
  entityType: 'focus_brand' | 'competitor' | string
  recommendedCount: number
  firstRecommendCount: number
  coveragePercent: number
  qaStatus?: string | null
  highlight?: boolean
}

export interface ProjectDashboardCompetitorComparison {
  available: boolean
  reason?: string
  coverageThresholdPercent?: number
  rows: ProjectDashboardCompetitorRow[]
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
  comparison?: ProjectDashboardComparison
  platforms: ProjectDashboardPlatformItem[]
  wordCloud: ProjectDashboardWordItem[]
  contentProgress?: ProjectDashboardContentProgress
  competitorComparison?: ProjectDashboardCompetitorComparison
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

export interface SystemAlertTodoItem {
  id: number
  alertType: string
  severity: AlertSeverity
  source: string
  message: string
  contextJson?: string | null
  isResolved?: boolean
  resolvedAt?: string | null
  readAt?: string | null
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
  taskDisplayName?: string | null
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
  savedCoreQuestionCount?: number
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
  pollingEnabled?: boolean
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
  invocationCount?: number | null
  successCount?: number | null
  failureCount?: number | null
  rateLimitedCount?: number | null
  permitBusyCount?: number | null
  circuitOpenCount?: number | null
  slowResponseCount?: number | null
  failureRate?: number | null
  avgDurationMs?: number | null
  lastSuccessAt?: string | null
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

export interface LlmRuntimeConfig {
  dispatch: {
    questionPollCycleDays: number
    workerPollConcurrency: number
    workerPopAdmissionEnabled: boolean
    workerMaxInFlight: number
    workerPermitGovernorEnabled: boolean
    workerPermitGovernorBusyRatio: number
    capacityFailureClassificationEnabled: boolean
    resourceBusyRetryAfterEnabled: boolean
    resourceBusyRetryMinSeconds: number
    resourceBusyRetryJitterSeconds: number
    resourceBusyRetryMaxSeconds: number
    resourceBusyMaxAttempts: number
    stagger: {
      enabled: boolean
      taskTypes: string
      windowMinutes: number
      maxDelayMinutes: number
      jitterSeconds: number
      capJitterSeconds: number
      maxQueueSize: number
      overflowPolicy: string
      platforms?: Record<string, {
        windowMinutes?: number | null
        maxDelayMinutes?: number | null
        jitterSeconds?: number | null
        capJitterSeconds?: number | null
      }>
    }
  }
  llmPool: {
    enabled: boolean
    globalConcurrency: number
    blockingAcquireFailFastEnabled: boolean
    blockingAcquireFailFastFeatures: string[]
    featureConcurrency: Record<string, number>
  }
  mobileJudge: {
    enabled: boolean
    maxProjectsPerRun: number
    perProjectLimit: number
    workerMs: number
    platformCodes: string[]
  }
  articleRouting: {
    excludedPlatformCodes: string[]
  }
}

export interface HunyuanCapacity {
  platformCodes: string[]
  activeLimit: number
  activePeak: number
  currentActive: number
  totalCount: number
  limitedCount: number
  limitRatio: number
  limitRatioThreshold: number
  limitCategories: string[]
  sliceProgress?: {
    batchDate?: string | null
    questionTier?: string | null
    expectedCount: number
    completedCount: number
    failedCount: number
    resourceWaitCount: number
    actualProgress: number
    expectedProgress: number
    lag: number
    windowMinutes: number
    sliceStart?: string | null
    rows?: Array<{
      platformCode: string
      expectedCount: number
      completedCount: number
      failedCount: number
      resourceWaitCount: number
    }>
  }
  retryExhausted?: {
    count: number
    windowStart?: string | null
    windowEnd?: string | null
  }
  openAlert?: {
    open: boolean
    dedupeKey?: string | null
    alertType?: string | null
    severity?: AlertSeverity | string | null
    message?: string | null
  }
}

export interface DispatchDueTimeDistribution {
  taskType: string
  rangeStart: string
  rangeEnd: string
  bucketMinutes: number
  platforms: Array<{
    platformCode: string
    statuses: Array<{
      status: string
      buckets: Array<{
        bucketStart: string
        taskCount: number
      }>
    }>
  }>
}

export interface PollSliceProgress {
  batchDate?: string | null
  questionTier?: string | null
  platformCodes: string[]
  expectedCount: number
  completedCount: number
  failedCount: number
  resourceWaitCount: number
  actualProgress: number
  expectedProgress: number
  lag: number
  windowMinutes: number
  sliceStart?: string | null
  observedAt?: string | null
  rows?: Array<{
    platformCode: string
    expectedCount: number
    completedCount: number
    failedCount: number
    resourceWaitCount: number
  }>
}

export interface DispatchAlertItem {
  id: number
  alertCode: string
  taskId?: number | null
  projectId?: number | null
  projectName?: string
  dedupeKey?: string | null
  severity: AlertSeverity
  status: 'open' | 'resolved'
  title: string
  content?: string | null
  retryCount: number
  contextJson?: string | null
  groupCount?: number | null
  openGroupCount?: number | null
  detailAlerts?: DispatchAlertItem[] | null
  platformFailures?: DispatchPlatformFailureSummary[] | null
  expectedResultCount?: number | null
  failedCount?: number | null
  failureRate?: number | null
  resolvedAt?: string | null
  resolvedBy?: number | null
  createdAt: string
}

export interface DispatchPlatformFailureSummary {
  platformId?: number | null
  platformCode?: string | null
  platformName?: string | null
  expectedCount?: number | null
  completedCount?: number | null
  failedCount?: number | null
  failureRate?: number | null
  requestCount?: number | null
  reasons?: DispatchFailureReasonSummary[] | null
}

export interface DispatchFailureReasonSummary {
  errorCode?: string | null
  errorMessage?: string | null
  count?: number | null
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
  complianceStatus?: 'pending' | 'passed' | 'failed' | 'discarded_compliance_failed' | string | null
  publishReviewStatus?: 'not_required' | 'pending' | 'passed' | 'rejected' | string | null
  medicalAdReviewNo?: string | null
  medicalChannelTier?: 'education' | 'source_site' | 'official_site' | string | null
  medicalIndustryCode?: 'medical_beauty' | 'oral' | string | null
  medicalCategoryCode?: string | null
  promptTemplateId?: number | null
  promptTemplateVersionId?: number | null
  promptTemplateName?: string | null
  complianceWarningCount?: number | null
  hasComplianceWarnings?: boolean | null
  topic?: string | null
  topicAsQuestion?: string | null
  title: string
  coverImageUrl?: string | null
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
  generatedBy?: string | null
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
  dispatchMode?: 'AUTO' | 'SEMI_AUTO' | string | null
  targetBrandId?: number | null
  brandOfficialSiteId?: number | null
  selfMediaAccountId?: number | null
  browserEnvironmentId?: number | null
  browserEnvironmentAccountId?: number | null
  environmentKey?: string | null
  environmentProvider?: string | null
  providerProfileId?: string | null
  fillToken?: string | null
  fillTokenExpiresAt?: number | null
  fillTokenNonce?: string | null
  authorityMediaId?: number | null
  platformArticleId?: string | null
  platformPublishId?: string | null
  externalStatus?: string | null
  reviewStatus?: string | null
  reviewFeedback?: string | null
  submittedAt?: string | null
  reviewCheckedAt?: string | null
  nextReviewCheckAt?: string | null
  reviewCheckCount?: number | null
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
  liveVerificationBlocked?: boolean
  liveVerificationReason?: string | null
  description?: string | null
  readinessChecks?: WechatReadinessCheck[]
}

export interface WechatReadinessCheck {
  code: string
  label: string
  status: 'ok' | 'warning' | 'missing' | string
  message: string
}

export interface WechatMpAuthUrl {
  authUrl: string
  expiresIn: number
}

export interface DouyinCapability {
  enabled: boolean
  mode: 'mock' | 'real' | string
  disabledReason?: string | null
  liveVerificationBlocked?: boolean
  liveVerificationReason?: string | null
  description?: string | null
  readinessChecks?: DouyinReadinessCheck[]
}

export interface DouyinReadinessCheck {
  code: string
  label: string
  status: 'ok' | 'warning' | 'missing' | string
  message: string
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
  accountIdentity?: 'personal' | 'enterprise' | string | null
  platformAccountId: string
  avatarUrl?: string | null
  qrcodeUrl?: string | null
  status: 'active' | 'expired' | 'revoked' | 'disabled' | string
  lastAuthCheckedAt?: string | null
  lastAuthError?: string | null
  cookieCredentialStatus?: string | null
  cookieCredentialVersion?: number | null
  cookieCredentialCapturedAt?: string | null
  cookieCredentialExpiresAt?: string | null
  cookieCredentialExpirySource?: string | null
  cookieCredentialIdentityStatus?: 'matched' | 'mismatch' | 'unknown' | string | null
  cookieCredentialIdentityName?: string | null
  cookieCredentialIdentityMessage?: string | null
  lastLoginVerifiedAt?: string | null
  lastLoginVerificationResult?: string | null
  lastLoginVerificationMethod?: string | null
  lastLoginVerificationWarning?: string | null
  recommendedReverifyAt?: string | null
  authRiskStatus?: 'normal' | 'reverify_due_soon' | 'reverify_overdue' | 'credential_missing' | 'unknown' | 'monitoring_disabled' | string | null
  recommendedReverifySource?: string | null
  authRiskWarningStartAt?: string | null
  credentialCandidateSuperseded?: boolean | null
  cookieDeclaredExpiryPassed?: boolean | null
  authRiskReasonCodes?: string[] | null
}

export interface SelfMediaAuthHealthPolicy {
  id: number
  platformCode: string
  enabled: boolean
  reverifyIntervalDays: number
  warningDays: number
  credentialReferenceDays?: number | null
  credentialExpiryMode: 'declared_then_reference' | 'declared_only' | 'reference_only' | 'periodic_only' | string
  alertEnabled: boolean
  defaultRecipientRole?: string | null
  version: number
  updatedAt?: string | null
}

export interface SelfMediaLoginVerification {
  id: number
  brandId: number
  selfMediaAccountId: number
  browserEnvironmentId: number
  browserEnvironmentAccountId: number
  platform: string
  expectedAccountName: string
  expectedPlatformAccountId?: string | null
  status: 'pending' | 'running' | 'succeeded' | 'failed' | 'timeout' | 'cancelled' | string
  resultCode?: string | null
  resultMessage?: string | null
  actualAccountName?: string | null
  actualPlatformAccountId?: string | null
  requestedAt: string
  reportedAt?: string | null
  expiresAt: string
}

export interface WechatMenuConfig {
  id: number
  selfMediaAccountId: number
  brandId: number
  authorizerAppid?: string | null
  publicSlug?: string | null
  menuName?: string | null
  menuStatus?: string | null
  listPageUrl?: string | null
  backupMenuJson?: string | null
  backupMenuAt?: string | null
  lastSyncAt?: string | null
  lastSyncError?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

export interface SelfMediaPublishSchedule {
  id: number
  requestId?: number | null
  requestIdempotencyKey?: string | null
  articleId: number
  articleTitle?: string | null
  brandId: number
  brandName?: string | null
  selfMediaAccountId: number
  selfMediaAccountName?: string | null
  browserEnvironmentId?: number | null
  browserEnvironmentAccountId?: number | null
  platform: string
  scheduleStrategy: string
  plannedPublishAt?: string | null
  platformScheduledAt?: string | null
  scheduleDriftSeconds?: number | null
  scheduleDriftReason?: string | null
  status: string
  queueKind?: string | null
  queuePriority?: number | null
  platformScheduleId?: string | null
  platformPublishId?: string | null
  platformPublishedUrl?: string | null
  publishCheckTitle?: string | null
  publishCheckCoverUrl?: string | null
  publishCheckLocationName?: string | null
  publishCheckFingerprint?: string | null
  baseIdempotencyKey?: string | null
  generationNo?: number | null
  attemptCount?: number | null
  maxAttempts?: number | null
  lastAttemptAt?: string | null
  nextAttemptAt?: string | null
  lockedUntil?: string | null
  failureCode?: string | null
  failureLabel?: string | null
  failureRetryable?: boolean | null
  failureActionHint?: string | null
  failureActionKey?: string | null
  failureActionLabel?: string | null
  failureActionKind?: string | null
  failureMessage?: string | null
  diagnosticsJson?: string | null
  activeAlerts?: SelfMediaPublishScheduleAlert[]
  createdAt?: string | null
  updatedAt?: string | null
  scheduledAt?: string | null
  cancelledAt?: string | null
  cancelRequestedAt?: string | null
  publishedConfirmedAt?: string | null
}

export interface SelfMediaPublishScheduleAlert {
  id: number
  scheduleId: number
  alertType: string
  severity: 'critical' | 'warning' | 'info' | string
  status: 'open' | 'resolved' | string
  message: string
  evidenceJson?: string | null
  firstSeenAt?: string | null
  lastSeenAt?: string | null
  resolvedAt?: string | null
}

export interface SelfMediaAutomationOverview {
  generatedAt?: string | null
  queue: {
    activeTotal: number
    dueScheduleExecution: number
    duePublishCheck: number
    runningTotal: number
    lockedRunning: number
    timedOutLockedRunning?: number
    failedTotal: number
    manualRequired: number
    publishUnknown: number
  }
    localExecution: {
      onlineAgents: number
      activeSessions: number
    assumedCapacityPerAgent: number
    estimatedCapacity: number
    runningLoad: number
    waitingForLocalAgent: number
    capacityStatus: 'healthy' | 'pressure' | 'saturated' | 'blocked' | string
    message?: string | null
    latestHeartbeatAt?: string | null
    sessions?: Array<{
      sessionId: number
      operatorId?: number | null
      operatorName?: string | null
      helperName?: string | null
      status?: string | null
      online?: boolean | null
      lastSeenAt?: string | null
      expiresAt?: string | null
      runningLoad?: number | null
        waitingTasks?: number | null
      }>
    }
    metrics?: {
      terminalTotal: number
      successTotal: number
      publishedConfirmed: number
      publishedUrlPending: number
      publishFailed: number
      scheduleFailed: number
      manualRequired: number
      urlAcquired: number
      postPublishFailures: number
      averagePublishDurationSeconds?: number | null
      successRate: number
      manualInterventionRate: number
      urlAcquisitionRate: number
    }
    statusCounts: Array<{ status: string; count: number }>
  platformCounts: Array<{ platform: string; activeCount: number; failedCount: number; dueCount: number }>
  failureCodeCounts: Array<{
    code: string
    label?: string | null
    retryable?: boolean | null
    actionKey?: string | null
    actionLabel?: string | null
    actionKind?: string | null
    count: number
  }>
  platformCapabilities: Array<{
    platform: string
    displayName?: string | null
    publishChannel?: string | null
    strategy?: string | null
    scheduleReady: boolean
    readinessCode?: string | null
    readinessMessage?: string | null
    requiresLocalAgent: boolean
    fillLeadMinutes?: number | null
    minRemainingMinutes?: number | null
    maxAttempts?: number | null
    maxRemainingMinutes?: number | null
    requiresPublishedUrl?: boolean | null
    publishCheckDelayMinutes?: number | null
    publishCheckMaxAttempts?: number | null
  }>
  thirdPartySubjectPool?: {
    sourceTotal: number
    readySourceTotal: number
    missingCoverageTotal: number
    emptyCandidateTotal: number
    templateMissingTotal?: number
    sources: Array<{
      sourceBrandId: number
      sourceBrandName: string
      coverableIndustries: string[]
      candidateCount: number
      excludedCount: number
      nextCandidateBrandName?: string | null
      status: 'ready' | 'missing_coverage' | 'empty_candidate' | 'template_missing' | string
      message?: string | null
      blockingReasons?: string[]
    }>
  } | null
  compensation?: {
    candidateCount: number
    alreadyTriedCount: number
    lastTriedAt?: string | null
    message?: string | null
  } | null
}

export interface SelfMediaPublishScheduleRejectedItem {
  articleId?: number | null
  selfMediaAccountId?: number | null
  platform?: string | null
  code?: string | null
  message?: string | null
  settingPath?: string | null
}

export interface SelfMediaPublishScheduleCreateResponse {
  requestId?: number | null
  requestIdempotencyKey?: string | null
  createdSchedules: SelfMediaPublishSchedule[]
  existingSchedules: SelfMediaPublishSchedule[]
  rejectedItems: SelfMediaPublishScheduleRejectedItem[]
}

export interface SelfMediaPlatformQuickScheduleResponse {
  action: 'ready' | 'created' | 'replace_required' | 'quota_exhausted' | 'article_type_mismatch' | 'account_or_environment_not_ready' | 'rejected' | string
  code?: string | null
  message?: string | null
  articleId?: number | null
  brandId?: number | null
  platform?: string | null
  platformLabel?: string | null
  selfMediaAccountId?: number | null
  replaceScheduleId?: number | null
  plannedPublishAt?: string | null
  nextAttemptAt?: string | null
  brandSafetyIntervalMinutes?: number | null
  createResponse?: SelfMediaPublishScheduleCreateResponse | null
}

export interface SelfMediaScheduleCapability {
  id?: number | null
  platform: string
  verificationStatus: 'unverified' | 'verified' | 'failed' | string
  supportsSchedule: boolean
  minDelayMinutes?: number | null
  maxDelayMinutes?: number | null
  saveCreatesSchedule?: boolean | null
  supportsCancel?: boolean | null
  supportsModify?: boolean | null
  supportsPublishCheck?: boolean | null
  v1Strategy?: 'pending' | 'platform_schedule' | 'backend_delayed_publish' | 'semi_auto' | string | null
  selectorStatus?: string | null
  evidenceJson?: string | null
  notes?: string | null
  displayName?: string | null
  publishChannel?: 'OFFICIAL_API' | 'ADSPOWER_AUTOMATION' | string | null
  scheduleMode?: 'PLATFORM_NATIVE' | 'BACKEND_DELAYED' | 'UNSUPPORTED' | string | null
  contractRequiresCoverUpload?: boolean | null
  contractSupportsLocation?: boolean | null
  contractSupportsOneClickFormat?: boolean | null
  contractSupportsPublishCheck?: boolean | null
  fillLeadMinutes?: number | null
  minRemainingMinutes?: number | null
  maxAttempts?: number | null
  maxRemainingMinutes?: number | null
  verifiedAt?: string | null
  verifiedBy?: number | null
  createdAt?: string | null
  updatedAt?: string | null
}

export interface SelfMediaRuntimeEnvironment {
  brandId: number
  brandName?: string | null
  platform: string
  selfMediaAccountId?: number | null
  accountName?: string | null
  platformAccountId?: string | null
  browserEnvironmentId?: number | null
  environmentName?: string | null
  environmentKey?: string | null
  providerProfileId?: string | null
  browserEnvironmentAccountId?: number | null
  loginStatus?: string | null
  expectedAccountName?: string | null
  expectedPlatformAccountId?: string | null
  extension?: {
    installId?: string | null
    extensionVersion?: string | null
    protocolVersion?: string | null
    lastSeenAt?: string | null
    runtimeStage?: string | null
    runtimeStageMessage?: string | null
    lastErrorCode?: string | null
    lastErrorMessage?: string | null
  } | null
  helper?: {
    sessionId?: number | null
    machineId?: string | null
    activeProfile?: string | null
    helperVersion?: string | null
    protocolVersion?: string | null
    adspowerApiOk?: boolean | null
    runningTaskCount?: number | null
    capacity?: number | null
    lastSeenAt?: string | null
    lastErrorCode?: string | null
    lastErrorMessage?: string | null
  } | null
  readiness: {
    ready: boolean
    blockedReasons: string[]
    retryAfterSeconds?: number | null
    gateMode?: string | null
    scope?: string | null
  }
}

export interface SelfMediaAccountPlatformOption {
  platform: string
  label: string
  eligible: boolean
  quotaEnabled: boolean
  quotaLimit: number
  quotaStatus: 'no_active_package' | 'not_enabled' | 'quota_zero' | 'enabled' | string
  scheduleReady: boolean
  scheduleCode?: string | null
  reason?: string | null
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
  apiCredential?: string | null
  apiCredentialEncrypted?: string | null
  credentialConfigured?: boolean | null
  credentialStatus?: 'configured' | 'missing' | 'expired_or_failed' | string | null
  requestHeaderTemplate?: string | null
  requestBodyTemplate?: string | null
  responseUrlPath?: string | null
  contentConstraints?: string | null
  currentHealthStatus?: string | null
  cookieRiskStatus?: 'normal' | 'expiring' | 'expired' | 'missing' | 'unknown' | string | null
  cookieExpiresAt?: string | null
  cookieDaysUntilExpiry?: number | null
  lastFailureAt?: string | null
  failureRate?: number | null
  remark?: string | null
  createdAt?: string
  updatedAt?: string
}

export interface AccountAuthHealthOverview {
  generatedAt: string
  summary: AccountAuthHealthSummary
  riskItems: AccountAuthHealthRiskItem[]
  alertGroups: AccountAuthHealthAlertGroup[]
  trendBuckets: AccountAuthHealthTrendBucket[]
}

export interface AccountAuthHealthSummary {
  totalTargets: number
  normalCount: number
  expiringCount: number
  expiredCount: number
  missingCount: number
  unknownCount: number
  openAlertCount: number
  highPriorityCount: number
  dueInSevenDays: number
  dueInThirtyDays: number
}

export interface AccountAuthHealthRiskItem {
  targetType: 'self_media' | 'forum' | string
  targetId: number
  targetKey?: string | null
  displayName?: string | null
  platform?: string | null
  platformLabel?: string | null
  brandId?: number | null
  brandName?: string | null
  companyName?: string | null
  ownerUserId?: number | null
  ownerName?: string | null
  riskStatus: 'normal' | 'expiring' | 'expired' | 'missing' | 'unknown' | string
  severity?: string | null
  expiresAt?: string | null
  daysUntilExpiry?: number | null
  expirySource?: string | null
  expirySourceLabel?: string | null
  actionRoute?: string | null
  actionLabel?: string | null
  actionHint?: string | null
}

export interface AccountAuthHealthAlertGroup {
  groupKey: string
  targetType: 'self_media' | 'forum' | string
  issueCode: string
  severity: string
  count: number
  latestCreatedAt?: string | null
  title: string
  sampleMessage?: string | null
  actionRoute?: string | null
  actionLabel?: string | null
}

export interface AccountAuthHealthTrendBucket {
  date: string
  selfMediaCount: number
  forumCount: number
  totalCount: number
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
  audienceType?: 'internal' | 'partner' | string | null
  packageStatus?: 'draft' | 'active' | 'inactive' | string | null
  standardPrice: number
  partnerPoints?: number | null
  partnerVisibleConfigJson?: string | null
  internalDeliveryConfigJson?: string | null
  serviceMonths: number
  coreQuestionLimit?: number | null
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
  channelCode: 'official_site' | 'industry_site' | 'forum' | 'authority_media' | `self_media:${string}` | string
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
  coreQuestionQuotaLimit?: number | null
  usedCoreQuestionCount?: number | null
  remainingCoreQuestionCount?: number | null
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
  channelCode: 'official_site' | 'industry_site' | 'forum' | 'authority_media' | `self_media:${string}` | string
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
