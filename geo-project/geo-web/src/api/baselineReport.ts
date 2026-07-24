import request from './request'
import type { PageResult, R } from '@/types'

export interface BaselinePlatformOption {
  id: number
  code: string
  name: string
  priorityLevel?: string
}

export interface BaselineQuestionTierOption {
  tier: 'A' | 'B' | 'C'
  questionCount: number
}

export interface BaselinePollBatch {
  id: number
  projectId: number
  status: string
  selectedPlatformCodes: string[]
  selectedQuestionTiers: string[]
  platformCount: number
  questionCount: number
  totalCount: number
  completedCount: number
  failedCount: number
  errorMessage?: string | null
  startedAt?: string
  finishedAt?: string | null
}

export interface BaselinePollOptions {
  platforms: BaselinePlatformOption[]
  questionTiers: BaselineQuestionTierOption[]
  latestBatch?: BaselinePollBatch | null
}

export interface BaselinePollResult {
  id: number
  batchId: number
  keywordResultId: number
  questionTier: 'A' | 'B' | 'C'
  questionText: string
  platformCode: string
  platformName?: string | null
  status: string
  requestCount: number
  responseTimeMs?: number | null
  responseText?: string | null
  errorMessage?: string | null
  createdAt?: string
}

export type BaselineSnapshotStatus = 'DRAFT' | 'SEALED' | 'RECOMPUTED'
export type BaselineValueTier = 'HIGH' | 'MID' | 'LOW'
export type BaselineIntentType = 'RECOMMENDATION' | 'COMPARISON' | 'PROBLEM' | 'AWARENESS' | 'SCENE'
export type BaselineCellState =
  | 'STABLE_PRESENT'
  | 'STABLE_ABSENT'
  | 'UNSTABLE_PARTIAL'
  | 'INSUFFICIENT_SAMPLE'
  | 'NO_DATA'

export interface BaselineQuestionSnapshot {
  id: number
  questionKey: string
  sourceKeywordResultId?: number | null
  questionText: string
  valueTier: BaselineValueTier
  sourceQuestionTier?: 'A' | 'B' | 'C' | null
  sourcePriority?: string | null
  intentType: BaselineIntentType
  sceneCode?: string | null
  sortOrder: number
  createdAt?: string
}

export interface BaselineSnapshot {
  id: number
  projectId: number
  companyId?: number | null
  brandId?: number | null
  runSeq: number
  status: BaselineSnapshotStatus
  schemaVersion: 'baseline_canonical_v1'
  intentRubricVersion: string
  algorithmVersionsJson: string
  selectedVersionsJson: string
  sourcePollBatchId?: number | null
  sealedAt?: string | null
  sealedBy?: number | null
  createdBy: number
  createdAt?: string
  updatedAt?: string
  questionCount: number
  questions: BaselineQuestionSnapshot[]
  competitorCount?: number
  competitorSources?: BaselineSnapshotCompetitorSource[]
  warnings?: string[]
}

export interface BaselineSnapshotCompetitorSource {
  competitorId?: number | null
  competitorName: string
  aliasesJson?: string | null
  sourceType?: string | null
  sourceUrl?: string | null
  sourceNote?: string | null
  reviewStatus: 'UNVERIFIED' | 'VERIFIED' | 'REJECTED'
}

export interface BaselineSnapshotReviewQuestion {
  questionSnapshotId?: number
  keywordResultId?: number
  intentType?: BaselineIntentType
  valueTier?: BaselineValueTier
}

export interface BaselineCollectionTask {
  taskId: number
  baselineId: number
  projectId: number
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'PARTIAL_FAILED' | 'FAILED' | 'CANCELED'
  questionCount: number
  platformCount: number
  samplePerCell: number
  totalObservationCount: number
  successObservationCount: number
  failedObservationCount: number
  scoreCount: number
  competitorMentionCount: number
  queuePosition?: number
  maxConcurrentBaselines?: number
  errorMessage?: string | null
}

export interface BaselineCanonicalMeta {
  schema_version: 'baseline_canonical_v1'
  baseline_id: number
  selected_versions: {
    score_algorithm_version: string
    highlight_algorithm_version: string
    competitor_normalization_version: string
    canonical_aggregate_version: string
  }
  algorithm_versions: {
    mention: string
    recommendation: string
    ranking: string
    sentiment: string
    impression: string
    highlight: string
    competitor_normalization: string
    coverage: string
    band: string
  }
}

export interface BaselineCanonicalCell {
  question_id: string
  question_snapshot_id: number
  platform_code: string
  cell_state: BaselineCellState
  covered: boolean
  expected_samples: number
  success_samples: number
  failed_samples: number
  positive_samples: number
  rate_denominator_eligible: boolean
}

export interface BaselineCanonicalReport {
  meta: BaselineCanonicalMeta
  cell_state_enum: BaselineCellState[]
  coverage: {
    metric_kind: 'mention_rate'
    covered_cell_count: number
    denominator_cell_count: number
    rate: number
    value_tiers: Array<{
      value_tier: BaselineValueTier
      appeared: number
      recommended: number
      denominator: number
      appeared_rate: number
    }>
    heatmap: Array<{
      intent_type: BaselineIntentType
      platform_code: string
      metric_kind: 'mention_rate' | 'awareness' | 'favorability'
      n: number
      positive: number
      low_sample: boolean
      rate: number
    }>
    platforms: Array<{
      platform_code: string
      mention_rate: number
      recommended_rate: number
      avg_ranking?: number | null
      positive_sentiment_rate: number
      denominator: number
    }>
    dimensions: Record<string, {
      rate: number
      numerator: number
      denominator: number
      band: {
        low: number
        high: number
        method: 'WILSON_95' | 'NO_SAMPLE'
      }
    }>
  }
  hero_metrics: {
    coverage_rate: number
    brand_mention_count: number
    tracked_competitor_mention_count: number
    negative_count: number
  }
  sentiment: {
    brand_mention_count: number
    denominator_evaluable_sentiment_count: number
    unknown_count: number
    distribution: Record<'POSITIVE' | 'NEUTRAL' | 'NEGATIVE', number>
    positive_keywords: string[]
    negative_evidence_count?: number
    negative_evidence: Array<{
      observation_id: number
      platform_code: string
      severity: 'HIGH' | 'MID' | 'LOW'
      excerpt: string
    }>
    platform_impressions: Array<{
      platform_code: string
      impression_state: 'POSITIVE' | 'NEUTRAL' | 'NEGATIVE' | 'INFO_MISSING' | 'NO_AWARENESS'
      count: number
    }>
  }
  cells: BaselineCanonicalCell[]
  competitors: {
    tracked: Array<{
      name: string
      mention_count: number
      review_status: 'UNVERIFIED' | 'VERIFIED' | 'REJECTED'
      verified_sources: BaselineVerifiedCompetitorSource[]
      render_source_explanation: boolean
      source_explanation?: string | null
      quotes?: Array<{
        observation_id: number
        platform_code: string
        excerpt: string
      }>
    }>
    untracked_mentions: Array<{
      name: string
      mention_count: number
    }>
    verified_sources: BaselineVerifiedCompetitorSource[]
    counts: Record<string, number>
  }
  competitor_counts: Record<string, number>
  competitor_gap_matrix: Array<{
    question_id: string
    question_snapshot_id: number
    question_text?: string | null
    intent_type?: BaselineIntentType | null
    value_tier?: BaselineValueTier | null
    you: boolean
    competitors: Record<string, boolean>
  }>
  evidence_cards: Array<{
    observation_id: number
    platform_code: string
    question_id?: string | null
    question_text?: string | null
    intent_type?: BaselineIntentType | null
    value_tier?: BaselineValueTier | null
    takeaway?: string | null
    raw_response_excerpt: string
    sample_label: string
    highlight_spans: Array<{
      type: 'BRAND' | 'COMPETITOR' | 'NEGATIVE'
      text: string
      start_offset: number
      end_offset: number
    }>
  }>
  key_findings: Array<{
    template_id: string
    values: Record<string, unknown>
    rendered_text: string
  }>
  delta_placeholders?: Array<{
    metric_key: string
    label: string
    status: 'PENDING_NEXT_PERIOD'
  }>
  brand: {
    id?: number | null
    name: string
  }
}

export interface BaselineVerifiedCompetitorSource {
  competitor_id?: number | null
  competitor_name: string
  aliases_json?: string | null
  source_type?: string | null
  source_url?: string | null
  source_note?: string | null
  verified_by?: number | null
  verified_at?: string | null
}

export interface BaselineCanonicalReportResponse {
  baselineId: number
  canonicalSchemaVersion: 'baseline_canonical_v1'
  scoreAlgorithmVersion: string
  highlightAlgorithmVersion: string
  competitorNormalizationVersion: string
  canonicalAggregateVersion: string
  canonicalJson: string
}

export type BaselineExportStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED'

export interface BaselineReportExportResponse {
  exportId: number
  baselineId: number
  projectId: number
  status: BaselineExportStatus
  idempotencyKey: string
  errorMsg?: string | null
  fileKey?: string | null
  fileSize?: number | null
  filePages?: number | null
  triggerAt?: string | null
  runningExportId?: number | null
  runningStatus?: BaselineExportStatus | null
}

export interface BaselinePrintRenderResponse {
  exportId: number
  baselineId: number
  projectId: number
  canonical: BaselineCanonicalReport
  renderProfile: {
    deviceScaleFactor: number
    pageFormat: string
  }
}

export function getBaselineReportOptions(projectId: number) {
  return request.get<R<BaselinePollOptions>>(`/projects/${projectId}/baseline-report/options`)
}

export function startBaselineReportPoll(projectId: number, data: { platformCodes: string[]; questionTiers: string[] }) {
  return request.post<R<BaselinePollBatch>>(`/projects/${projectId}/baseline-report/poll`, data)
}

export function getBaselineReportResults(projectId: number, params: { batchId?: number; current?: number; size?: number }) {
  return request.get<R<PageResult<BaselinePollResult>>>(`/projects/${projectId}/baseline-report/results`, { params })
}

export function createBaselineSnapshot(projectId: number, data?: { sourcePollBatchId?: number; intentOverrides?: Array<{ keywordResultId: number; intentType: BaselineIntentType }>; competitorSources?: BaselineSnapshotCompetitorSource[] }) {
  return request.post<R<BaselineSnapshot>>(`/projects/${projectId}/baseline-report/snapshots`, data ?? {})
}

export function getLatestBaselineSnapshot(projectId: number) {
  return request.get<R<BaselineSnapshot | null>>(`/projects/${projectId}/baseline-report/snapshots/latest`)
}

export function getBaselineSnapshot(projectId: number, baselineId: number) {
  return request.get<R<BaselineSnapshot>>(`/projects/${projectId}/baseline-report/snapshots/${baselineId}`)
}

export function reviewBaselineSnapshot(projectId: number, baselineId: number, data: { questions: BaselineSnapshotReviewQuestion[]; competitorSources?: BaselineSnapshotCompetitorSource[] }) {
  return request.put<R<BaselineSnapshot>>(`/projects/${projectId}/baseline-report/snapshots/${baselineId}/review`, data)
}

export function sealBaselineSnapshot(projectId: number, baselineId: number) {
  return request.post<R<BaselineSnapshot>>(`/projects/${projectId}/baseline-report/snapshots/${baselineId}/seal`)
}

export function collectBaselineObservations(projectId: number, baselineId: number, data?: { platformCodes?: string[] }) {
  return request.post<R<BaselineCollectionTask>>(`/projects/${projectId}/baseline-report/snapshots/${baselineId}/collect`, data ?? {})
}

export function getBaselineCollectTask(projectId: number, baselineId: number, taskId: number) {
  return request.get<R<BaselineCollectionTask>>(`/projects/${projectId}/baseline-report/snapshots/${baselineId}/collect/tasks/${taskId}`)
}

export function cancelBaselineCollectTask(projectId: number, baselineId: number, taskId: number) {
  return request.post<R<BaselineCollectionTask>>(`/projects/${projectId}/baseline-report/snapshots/${baselineId}/collect/tasks/${taskId}/cancel`)
}

export function getLatestBaselineCollectTask(projectId: number, baselineId: number, silentError = false) {
  return request.get<R<BaselineCollectionTask>>(
    `/projects/${projectId}/baseline-report/snapshots/${baselineId}/collect/latest`,
    { silentError } as any,
  )
}

export function recomputeBaselineCanonical(projectId: number, baselineId: number) {
  return request.post<R<BaselineCanonicalReportResponse>>(`/projects/${projectId}/baseline-report/snapshots/${baselineId}/canonical/recompute`)
}

export function getBaselineCanonical(projectId: number, baselineId: number, silentError = false) {
  return request.get<R<BaselineCanonicalReportResponse>>(
    `/projects/${projectId}/baseline-report/snapshots/${baselineId}/canonical`,
    { silentError } as any,
  )
}

export function createBaselineExport(projectId: number, baselineId: number) {
  return request.post<R<BaselineReportExportResponse>>(`/projects/${projectId}/baseline-report/snapshots/${baselineId}/exports`, {})
}

export function getBaselineExport(projectId: number, baselineId: number, exportId: number) {
  return request.get<R<BaselineReportExportResponse>>(`/projects/${projectId}/baseline-report/snapshots/${baselineId}/exports/${exportId}`)
}

export async function downloadBaselineExportPdf(projectId: number, baselineId: number, exportId: number) {
  const response = await request.get<Blob>(
    `/projects/${projectId}/baseline-report/snapshots/${baselineId}/exports/${exportId}/download`,
    { responseType: 'blob' }
  )
  const url = window.URL.createObjectURL(response.data)
  const link = document.createElement('a')
  link.href = url
  link.download = `基线监测报告_${baselineId}-${exportId}.pdf`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}

export function getBaselinePrintRenderDetail(renderToken: string) {
  return request.get<R<BaselinePrintRenderResponse>>(`/baseline-report/exports/render/${encodeURIComponent(renderToken)}`)
}
