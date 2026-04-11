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
   认证
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
   枚举/联合类型
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

export type AlertSeverity = 'info' | 'warning' | 'critical'

/* ====================================================
   业务实体
   ==================================================== */
export interface Company {
  id: number
  companyName: string
  industry: string | null
  city: string | null
  ownerType: OwnerType
  partnerId: number | null
  referralSource: string | null
  salesOwnerId: number | null
  status?: string
  remark?: string | null
  createdAt: string
}

export interface Brand {
  id: number
  companyId: number
  brandName: string
  brandSlug: string
  mainBusiness: string | null
  serviceArea: string | null
  website: string | null
  phone: string | null
  wechat: string | null
  description: string | null
  standardBrandStatement: string | null
  forbiddenPhrases: string | null
  status?: string
  createdAt: string
  updatedAt: string
}

export interface Project {
  id: number
  projectCode?: string
  brandId: number
  projectName: string
  packageType: PackageType
  packagePrice?: number
  serviceMonths?: number
  status: ProjectStatus
  stage: ProjectStage
  startDate: string | null
  endDate: string | null
  primaryGoal: string | null
  ownerType: OwnerType
  partnerId: number | null
  deliveryMode: string
  remark?: string | null
  createdAt: string
  // 关联展示字段
  brandName?: string
  companyName?: string
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
  // 关联
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

/* ====================================================
   路由 meta 扩展
   ==================================================== */
export interface RouteMeta {
  title?: string
  roles?: RoleType[]
  permissions?: string[]
  requiresAuth?: boolean
  icon?: string
  hidden?: boolean
}
