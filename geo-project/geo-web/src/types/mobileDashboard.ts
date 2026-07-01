export interface MobileDashboardSession {
  sessionToken: string
  sessionExpiresAt: string
  sessionTtlSeconds: number
  shareId: number
  projectId: number
  projectName: string
  brandName: string
  contentPlatforms?: ContentPlatform[]
}

export interface MobileDashboardBootstrap {
  projectId: number
  projectName: string
  brandName: string
  availablePages: Record<string, boolean>
  contentPlatforms?: ContentPlatform[]
  message?: string
}

export type MobileDashboardPageKey = 'home' | 'monitor' | 'content' | 'report'

export interface DashboardMetric<T = unknown> {
  available: boolean
  reason?: string
  value?: T | null
  unit?: string | null
}

export interface TrendPoint {
  date: string
  value: number | null
}

export interface KeyMetric {
  key: string
  metric: DashboardMetric
}

export interface PlatformMetric {
  platformId?: number | null
  code: string
  platformLogoUrl?: string | null
  platformLogoObjectKey?: string | null
  rate: DashboardMetric<number>
  completedCount?: number
  mentionCount?: number
}

export interface SceneMetric {
  code: string
  visible: boolean
  covered: DashboardMetric<number>
  total: DashboardMetric<number>
}

export interface ContentProgress {
  monthContent: DashboardMetric<number>
  published: DashboardMetric<number>
  indexed: DashboardMetric<number>
  building: DashboardMetric<number>
  indexMeasurementScope?: string
}

export interface ContentPlatform {
  code: string
  label: string
  icon: string
}

export interface EcoAssets {
  totalAssets: DashboardMetric<number>
  monthNew: DashboardMetric<number>
  indexed: DashboardMetric<number>
  coveredQuestions: DashboardMetric<number>
  indexMeasurementScope?: string
}

export interface CompetitorComparisonRow {
  displayName: string
  entityType: 'focus_brand' | 'competitor' | string
  recommendedCount: number
  firstRecommendCount: number
  coveragePercent: number
  highlight: boolean
}

export interface CompetitorComparison {
  available: boolean
  reason?: string
  rows: CompetitorComparisonRow[]
}

export interface HomeDashboardData {
  overallMentionRate: DashboardMetric<number>
  trend: TrendPoint[]
  metrics: KeyMetric[]
  platformPerformance: PlatformMetric[]
  sceneCoverage: SceneMetric[]
  competitorComparison: CompetitorComparison
  contentProgress: ContentProgress
  ecoAssets: EcoAssets
}

export interface MonitorDashboardData {
  overview: {
    monitoredQuestions: DashboardMetric<number>
    brandMentioned: DashboardMetric<number>
    aiRecommendRate: DashboardMetric<number>
    firstRecommendCount: DashboardMetric<number>
  }
  platformFilters: string[]
  questionList: {
    available: boolean
    reason?: string
    page?: number
    size?: number
    total?: number
    totalPages?: number
    items: QuestionMonitorItem[]
  }
  scenePerformance: SceneMetric[]
  questionCoverage: {
    covered: DashboardMetric<number>
    monitoring: DashboardMetric<number>
    building: DashboardMetric<number>
  }
}

export interface QuestionMonitorItem {
  keywordResultId?: number | null
  pollResultId?: number | null
  platformId?: number | null
  platformCode: string
  platformLogoUrl?: string | null
  platformLogoObjectKey?: string | null
  platformCodes?: string[]
  questionTitle: string
  completedAt?: string | null
  mentioned: boolean
  recommended: DashboardMetric<boolean>
  firstRecommend: DashboardMetric<boolean>
  rankPosition: DashboardMetric<number>
  evidence?: string | null
  responseText?: string | null
  tags: string[]
  relatedContentTasks?: ContentTaskItem[]
}

export interface PlatformCompletion {
  code: string
  completionRate: DashboardMetric<number>
  published: number
  quota: number
}

export interface OwnedPublish {
  code: string
  published: DashboardMetric<number>
  indexed: DashboardMetric<number>
}

export interface ContentDashboardData {
  overview: ContentProgress
  contentPlatforms?: ContentPlatform[]
  platformCompletion: PlatformCompletion[]
  taskList: {
    available: boolean
    reason?: string
    page?: number
    size?: number
    total?: number
    totalPages?: number
    items: ContentTaskItem[]
  }
  ownedPublish: OwnedPublish[]
  ecoAssets: EcoAssets
}

export interface ContentTaskItem {
  draftId: number
  title: string
  keywords: string[]
  platformCodes: string[]
  publishUrl?: string | null
  status: 'indexed' | 'published' | 'building' | string
  date?: string | null
}

export interface ReportDashboardData {
  overallMentionRate: DashboardMetric<number>
  trend: TrendPoint[]
  coreResults: KeyMetric[]
  highlights: { available: boolean; reason?: string; items: unknown[] }
  deliverySummary: {
    published: DashboardMetric<number>
    assetNew: DashboardMetric<number>
    indexed: DashboardMetric<number>
    coveredQuestions: DashboardMetric<number>
    indexMeasurementScope?: string
  }
  ecoAssets: EcoAssets
}

export interface MobileDashboardShare {
  id: number
  projectId: number
  shareCode?: string | null
  tokenPrefix: string
  status: 'active' | 'disabled' | string
  expiresAt: string
  createdBy?: number | null
  createdAt?: string | null
  disabledAt?: string | null
  lastAccessAt?: string | null
  accessCount?: number | null
  shareUrl?: string | null
}

export interface MobileDashboardShareAccessSummary {
  shareId: number
  totalAccess: number
  successAccess: number
  failedAccess: number
  distinctIpCount: number
  lastAccessAt?: string | null
  latestFailReason?: string | null
  latestUserAgent?: string | null
}

export interface MobileDashboardOperations {
  projectId: number
  startDate: string
  endDate: string
  judgeHealth: {
    expectedCount: number
    successCount: number
    coveragePercent: number
    coverageReady: boolean
    failedCount: number
    lastRecomputedAt?: string | null
  }
  apiErrorStats: {
    total: number
    failed: number
    errorRatePercent: number
    endpoints: Array<{
      eventType: string
      total: number
      failed: number
      errorRatePercent: number
      latestFailReason?: string | null
    }>
  }
  llmUsage: {
    totalCalls: number
    successCalls: number
    failedCalls: number
    totalTokens: number
    estimatedCost: number | string
    currency?: string | null
    estimated: boolean
  }
  shareRisks: Array<{
    shareId: number
    tokenPrefix: string
    totalAccess: number
    distinctIpCount: number
    failedAccess: number
    lastAccessAt?: string | null
    suspicious: boolean
  }>
}

export interface EntityJudgeBudgetConfig {
  id?: number | null
  scopeType: string
  projectId?: number | null
  enabled: boolean
  dailyCallLimit?: number | null
  monthlyCallLimit?: number | null
  dailyEstimatedCostLimit?: number | string | null
  monthlyEstimatedCostLimit?: number | string | null
  updatedAt?: string | null
  updatedBy?: number | null
}

export interface EntityJudgeBudgetConfigPayload {
  enabled: boolean
  dailyCallLimit?: number | null
  monthlyCallLimit?: number | null
  dailyEstimatedCostLimit?: number | null
  monthlyEstimatedCostLimit?: number | null
}
