import request from './request'
import type { R } from '@/types'

export interface DataRetentionSchemaStatus {
  historyQuerySucceeded: boolean
  currentVersion: string | null
  requiredVersion: string
  missingRequiredVersions: string[]
  compatibleForLocalAcceptance: boolean
  errorCode: string | null
}

export interface DataRetentionStaticSafetyGates {
  schedulerEnabled: boolean
  schedulerExecuteEnabled: boolean
  websiteExecuteEnabled: boolean
  articleArchiveExecuteEnabled: boolean
  articlePurgeExecuteEnabled: boolean
  pollExecuteEnabled: boolean
  contentUrlRewriteExecuteEnabled: boolean
  objectStorageMigrationExecuteEnabled: boolean
  executePromotionEnabled: boolean
  dynamicEnableWriteEnabled: boolean
  detailCleanupEnabled: boolean
}

export interface DataRetentionReadiness {
  primaryCode: string
  severity: string
  codes: string[]
  eligibleAttempts: number
  eligibleItems: number
  oldestAnchor: string | null
  retentionBytes: number | null
  projectedFiveYearBytes: number | null
  ready: boolean
  requiresDisable: boolean
}

export interface DataRetentionOperationsStatus {
  schema: DataRetentionSchemaStatus
  auditWriter: {
    configured: boolean
    applicationVersion: string | null
    errorCode: string | null
  }
  staticSafetyGates: DataRetentionStaticSafetyGates
  storage: {
    provider: string
    readFallbackToMinio: boolean
    cosTargetConfigured: boolean
    objectStorageMigrationApplicable: boolean
  }
  allStaticMutationGatesClosed: boolean
  detailCleanupReadiness: DataRetentionReadiness
}

export interface DynamicSwitchView {
  switchKey: string
  runtime: {
    integrity: string
    payload?: {
      enabled?: boolean
      version?: number
      changeId?: string
      changedAt?: string
    } | null
    errorCode?: string | null
  } | null
  latestAudit: {
    changeId: string
    version: number
    targetEnabled: boolean
    status: string
    resultCode: string
  } | null
  divergence: string
}

export interface WebsiteDryRunRequest {
  retentionHours: number
  limit: number
  cursorArticleId?: number
  reason: string
}

export interface WebsiteDryRunResult {
  retentionRunId: number
  dryRun: boolean
  retentionHours: number
  candidateCount: number
  hasMore: boolean
  nextCursorArticleId: number | null
  items: Array<{
    articleId: number
    projectId: number
    articleStatus: string
    lastPublishedAt: string
    bodyRowCount: number
    bodyBytes: number
    payloadRowCount: number
    payloadBytes: number
    result: string
  }>
}

export interface PollDryRunRequest {
  projectId: number
  startDate: string
  endDate: string
  questionTier: string
  hotRetentionDays: number
  limit: number
  reason: string
}

export interface PollDryRunResult {
  retentionRunId: number
  dryRun: boolean
  cutoffDate: string
  candidateSlices: number
  eligibleSlices: number
  blockedSlices: number
  warningCount: number
  pollResultRows: number
  shardRows: number
  shardItemRows: number
  invocationAttemptRows: number
  providerCallRows: number
  searchSourceRows: number
  citationRows: number
  entityJudgeRows: number
  slices: Array<{
    projectId: number
    batchDate: string
    questionTier: string
    eligible: boolean
    action: string
    result: string
    blockedReasons: string[]
    warnings: string[]
    pollResultRows: number
    latestLiveResultRows: number
    shardRows: number
    shardItemRows: number
    invocationAttemptRows: number
    providerCallRows: number
    searchSourceRows: number
    citationRows: number
    entityJudgeRows: number
  }>
}

export interface ArticleDryRunItem {
  articleId: number
  versionId: number
  projectId: number
  versionNo: number
  publishedAt: string
  eligible: boolean
  blockedReasons: string[]
  action: string
  result: string
  plannedObjectKey: string | null
  contentBytes: number
  activeDistributionTaskCount: number
  activeSelfMediaScheduleCount: number
}

export interface ArticleDryRunResult {
  retentionRunId: number
  dryRun: boolean
  candidateCount: number
  eligibleCount: number
  blockedCount: number
  estimatedBytes: number
  items: ArticleDryRunItem[]
}

export interface ArticleArchiveDryRunRequest {
  dryRun: true
  projectId: number
  minPublishedAgeDays: number
  limit: number
  reason: string
}

export interface ArticlePurgeDryRunRequest {
  projectId: number
  retentionDays: number
  archiveGraceHours: number
  limit: number
  reason: string
}

export function getDataRetentionOperationsStatus() {
  return request.get<R<DataRetentionOperationsStatus>>('/data-retention/operations/status')
}

export function getDataRetentionDynamicSwitches() {
  return request.get<R<DynamicSwitchView[]>>('/data-retention/dynamic-switches')
}

export function runWebsiteRetentionDryRun(payload: WebsiteDryRunRequest) {
  return request.post<R<WebsiteDryRunResult>>('/data-retention/articles/website-published/dry-run', payload)
}

export function runPollRetentionDryRun(payload: PollDryRunRequest) {
  return request.post<R<PollDryRunResult>>('/data-retention/poll-results/dry-run', payload)
}

export function runArticleArchiveDryRun(payload: ArticleArchiveDryRunRequest) {
  return request.post<R<ArticleDryRunResult>>('/data-retention/articles/archive/dry-run', payload)
}

export function runArticlePurgeDryRun(payload: ArticlePurgeDryRunRequest) {
  return request.post<R<ArticleDryRunResult>>('/data-retention/articles/purge/dry-run', payload)
}
