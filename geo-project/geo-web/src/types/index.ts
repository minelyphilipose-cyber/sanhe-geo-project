/* ====================================================
   API 鍝嶅簲鍖呰
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

export type ProjectStatus = 'draft' | 'active' | 'paused' | 'dispute' | 'completed' | 'archived'

export type ProjectStage =
  | 'pending_start'
  | 'collecting_materials'
  | 'baseline_diagnosis'
  | 'building_questions'
  | 'executing'
  | 'biweekly_feedback'
  | 'monthly_report'
  | 'quarterly_report'
  | 'needs_renewal'
  | 'high_risk'
  | 'dispute_handling'
  | 'completed'

export type OwnerType = 'direct' | 'partner' | 'joint'

export type QuestionType =
  | 'brand' | 'location' | 'industry' | 'decision'
  | 'transaction' | 'qa' | 'comparison' | 'competitor'

export type QuestionPriority = 'A' | 'B' | 'C'

export type PlatformPriority = 'P0' | 'P1' | 'P2'

export type PlatformHealth =
  | 'normal' | 'slow_response' | 'high_failure'
  | 'degraded' | 'manual_takeover' | 'maintenance'

export type ReportType =
  | 'presale_diagnosis' | 'biweekly' | 'monthly' | 'quarterly' | 'management'

export type ReportStatus =
  | 'generating' | 'pending_review' | 'auto_approved'
  | 'manually_approved' | 'intercepted' | 'published' | 'archived'

export type PartnerLevel = 'level_29800' | 'level_59800' | 'level_99800'

export type TrainingStatus = 'not_trained' | 'in_training' | 'passed' | 'production_enabled'

export type AlertSeverity = 'info' | 'warn' | 'error' | 'critical'

/* ====================================================
   涓氬姟瀹炰綋
   ==================================================== */
export interface Company {
  id: number
  companyName: string
  contactName?: string | null
  contactPhone?: string | null
  industry: string | null
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
  brandName: string
  brandSlug: string
  mainBusiness: string | null
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
  wechat: string | null
  description: string | null
  businessIntro?: string | null
  standardBrandStatement: string | null
  businessStandardStatement?: string | null
  standardStatement?: {
    positioning?: string | null
    selling_points?: string[] | null
    differentiation?: string | null
    brand_paragraph?: string | null
  } | null
  statementStatus?: 'pending' | 'draft' | 'locked' | string | null
  statementGeneratedAt?: string | null
  statementLockedAt?: string | null
  statementLockedBy?: number | null
  statementVersion?: number | null
  statementHistory?: any[] | null
  forbiddenPhrases: string | null
  status?: string
  createdAt: string
  updatedAt: string
}

export interface BrandMaterial {
  id: number
  brandId: number
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
  packageType: PackageType
  packagePrice?: number
  serviceMonths?: number
  planQuestionPoolSize?: number | null
  planCoreQuestionCount?: number | null
  planPlatformP0Count?: number | null
  planPlatformP1Count?: number | null
  planPlatformP2Count?: number | null
  planPerQuestionPlatformCalls?: number | null
  planPerQuestionCallsP0?: number | null
  planPerQuestionCallsP1?: number | null
  planPerQuestionCallsP2?: number | null
  planBiweeklyFrequency?: number | null
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
  primaryGoal: string | null
  ownerType: OwnerType
  sourceType?: 'internal' | 'partner'
  partnerId: number | null
  provinceCode?: string | null
  provinceName?: string | null
  cityCode?: string | null
  cityName?: string | null
  districtCode?: string | null
  districtName?: string | null
  discountRateSnapshot?: number | null
  deductionAmount?: number | null
  deductionTxnNo?: string | null
  deliveryMode: string
  remark?: string | null
  createdAt: string
  selectedPlatformCodesP0?: string[]
  selectedPlatformCodesP1?: string[]
  selectedPlatformCodesP2?: string[]
  // 鍏宠仈灞曠ず瀛楁
  brandName?: string
  companyName?: string
}


export interface ProjectPlatformOption {
  platformCode: string
  platformName: string
  priorityLevel: 'P0' | 'P1' | 'P2'
}

export interface Question {
  id: number
  questionSetId: number
  content: string
  questionType: QuestionType
  priority: QuestionPriority
  isCore: boolean
  isPromisedScope: boolean
  isObservationOnly: boolean
  status: string
  platformScope: string[]
  terminalScope: string[]
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

export interface QuestionPoolItemInput {
  questionText: string
  questionType: string
  priority: 'A' | 'B' | 'C'
  isCore: boolean
}

export interface QuestionPoolItemVO extends QuestionPoolItemInput {
  id: number
  projectId: number
  versionId: number
  contentStrategy?: string | null
  strategyKeywords?: string | null
  strategySuggestedType?: 'faq' | 'scenario_content' | 'industry_article' | string | null
  strategyGeneratedAt?: string | null
  strategyStatus?: 'none' | 'generated' | 'edited' | string | null
}

export interface QuestionPoolVersionVO {
  id: number
  projectId: number
  versionNo: number
  changeReason?: string | null
  createdBy: number
  createdAt: string
  totalQuestions: number
  coreQuestions: number
  items?: QuestionPoolItemVO[]
}

export interface QuestionPoolManageItemVO {
  projectId: number
  projectName: string
  versionNo: number
  totalQuestions: number
  coreQuestions: number
  changeReason?: string | null
  createdBy: number
  createdAt: string
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
  modelName: string
  enabled: boolean
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
  periodStart: string | null
  periodEnd: string | null
  shareToken: string
  pdfUrl: string | null
  visibility: string
  status: ReportStatus
  autoPublish: boolean
  interceptReason: string | null
  publishedAt: string | null
  createdAt: string
  // 鍏宠仈
  projectName?: string
  brandName?: string
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
  finishedAt?: string | null
  lastError?: string | null
  errorContext?: string | null
  createdAt: string
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
  degraded: boolean
  degradedReason?: string | null
  currentHealthStatus?: string | null
  lastFailureAt?: string | null
  exceptionCount: number
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
  batchId: number
  projectId: number
  articleType: 'faq' | 'scenario_content' | 'industry_article' | 'stage_advice' | string
  title: string
  status: 'pending_review' | 'approved' | 'rejected' | 'under_revision' | 'published' | 'unpublished' | string
  hasRisk: boolean
  riskSeverity: 'none' | 'warn' | 'block' | string
  riskWordsJson?: string | null
  isDuplicateTitle: boolean
  duplicateScore?: number | null
  duplicateArticleId?: number | null
  currentVersionNo: number
  createdAt: string
  updatedAt: string
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

export interface ArticleDetailResponse {
  article: ArticleDraft
  project: Project
  versions: ArticleDraftVersion[]
  reviewLogs: ArticleReviewLog[]
  publishLogs: ArticlePublishLog[]
}

export interface ActivityLog {
  id: number
  userId: number | null
  operatorName: string
  action: string
  targetType: string
  targetId: number | null
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
  questionPoolSize: number
  coreQuestionCount: number
  platformP0Count: number
  platformP1Count: number
  platformP2Count: number
  perQuestionPlatformCalls: number
  perQuestionCallsP0: number
  perQuestionCallsP1: number
  perQuestionCallsP2: number
  biweeklyFrequency: number
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
  contentConfigs?: PackageContentConfig[]
  createdAt?: string
  updatedAt?: string
}

export interface PackageContentConfig {
  id?: number
  packageType?: string
  articleType: 'faq' | 'scenario_content' | 'industry_article' | 'stage_advice' | string
  articlesPerBatch: number
  questionsPerArticle: number
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
