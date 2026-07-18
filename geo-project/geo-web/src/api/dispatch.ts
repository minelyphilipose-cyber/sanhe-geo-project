import request from './request'
import type {
  DispatchAlertItem,
  DispatchDashboardMetrics,
  DispatchDueTimeDistribution,
  HunyuanCapacity,
  LlmPoolSnapshot,
  LlmRuntimeConfig,
  PollSliceProgress,
  DispatchPlatformHealthItem,
  DispatchTaskItem,
  PageResult,
  R,
} from '@/types'

export interface DispatchRangeParams {
  rangeType?: 'today' | 'last7' | 'last30' | 'custom'
  startDate?: string
  endDate?: string
  projectId?: number
}

export interface DispatchTaskQuery extends DispatchRangeParams {
  current?: number
  size?: number
  projectId?: number
  taskType?: string
  status?: string
  keyword?: string
}

export interface DispatchAlertQuery extends DispatchRangeParams {
  current?: number
  size?: number
  severity?: string
  status?: string
}

export interface ManualQuestionPollPlatformOption {
  platformId: number
  platformCode: string
  channelCode: string
  platformName: string
  integrationType: string
  modelId: string | null
  enabled: boolean
  enabledForQuestionPoll: boolean
  selectable: boolean
  unavailableReason: string | null
}

export interface ManualQuestionPollRequest {
  projectId: number
  questionTier: 'A' | 'B' | 'C'
  platformIds: number[]
  questionLimit: number
  clientRequestId: string
}

export interface ManualQuestionPollPlatformProgress {
  platformId: number
  platformCode: string
  channelCode: string
  platformName: string
  shardCount: number
  readyCount: number
  runningCount: number
  completedShardCount: number
  failedShardCount: number
  expectedCount: number
  completedCount: number
  failedCount: number
  resourceWaitCount: number
}

export interface ManualQuestionPollSourceDetail {
  sourceId: number
  rankNo: number | null
  title: string | null
  url: string | null
  domain: string | null
  brandMatched: boolean | null
  brandMatchStrength: string | null
}

export interface ManualQuestionPollCitationDetail {
  citationIndex: number | null
  sourceId: number | null
  sourceTitle: string | null
  sourceUrl: string | null
  answerStart: number | null
  answerEnd: number | null
  confidence: string | null
  validationStatus: string | null
}

export interface ManualQuestionPollResultDetail {
  pollResultId: number
  platformId: number
  platformCode: string
  platformName: string
  question: string
  status: string
  resultCode: string | null
  requestCount: number | null
  responseTimeMs: number | null
  executionFinalized: boolean | null
  searchStatus: string | null
  searchTriggered: boolean | null
  confirmedCitationExposure: boolean | null
  answer: string | null
  errorCategory: string | null
  errorMessage: string | null
  latencyMs: number | null
  sources: ManualQuestionPollSourceDetail[]
  citations: ManualQuestionPollCitationDetail[]
}

export interface ManualQuestionPollBatchView {
  batchId: number
  projectId: number
  projectName: string
  batchDate: string
  batchNo: number
  questionTier: string
  triggerType: 'MANUAL'
  status: string
  questionLimit: number
  platformCount: number
  shardCount: number
  terminalShardCount: number
  failedShardCount: number
  resultCount: number
  completedCount: number
  failedCount: number
  searchConfirmedCount: number
  confirmedCitationExposureCount: number
  triggeredAt: string
  finishedAt: string | null
  platforms: ManualQuestionPollPlatformProgress[]
  results: ManualQuestionPollResultDetail[]
}

export function getDispatchDashboard(params?: DispatchRangeParams) {
  return request.get<R<DispatchDashboardMetrics>>('/dispatch/monitor/dashboard', { params })
}

export function getDispatchTasks(params?: DispatchTaskQuery) {
  return request.get<R<PageResult<DispatchTaskItem>>>('/dispatch/monitor/tasks', { params })
}

export function getDispatchPlatforms(params?: DispatchRangeParams) {
  return request.get<R<DispatchPlatformHealthItem[]>>('/dispatch/monitor/platforms', { params })
}

export function getLlmPoolSnapshot() {
  return request.get<R<LlmPoolSnapshot>>('/monitoring/llm-pool')
}

export function getLlmRuntimeConfig() {
  return request.get<R<LlmRuntimeConfig>>('/monitoring/llm-capacity/runtime-config')
}

export function getHunyuanCapacity() {
  return request.get<R<HunyuanCapacity>>('/monitoring/llm-capacity/hunyuan')
}

export function getDispatchDueTimeDistribution(params?: { bucketMinutes?: number; platformCode?: string }) {
  return request.get<R<DispatchDueTimeDistribution>>('/dispatch/monitor/due-time-distribution', { params })
}

export function getPollSliceProgress(params?: { batchDate?: string; questionTier?: string; platformCode?: string }) {
  return request.get<R<PollSliceProgress>>('/dispatch/monitor/poll-slice-progress', { params })
}

export function getDispatchAlerts(params?: DispatchAlertQuery) {
  return request.get<R<PageResult<DispatchAlertItem>>>('/dispatch/monitor/alerts', { params })
}

export function getDispatchAlert(id: number) {
  return request.get<R<DispatchAlertItem>>(`/dispatch/monitor/alerts/${id}`)
}

export function resolveDispatchAlert(id: number, note?: string) {
  return request.post<R<void>>(`/dispatch/monitor/alerts/${id}/resolve`, { note })
}

export function replayDispatchTask(taskId: number) {
  return request.post<R<void>>('/dispatch/tasks/replay', { taskId })
}

export function getDispatchTask(taskId: number) {
  return request.get<R<DispatchTaskItem>>(`/dispatch/monitor/tasks/${taskId}`)
}

export function getManualQuestionPollPlatforms() {
  return request.get<R<ManualQuestionPollPlatformOption[]>>('/dispatch/question-poll/manual/platforms')
}

export function startManualQuestionPoll(payload: ManualQuestionPollRequest) {
  return request.post<R<ManualQuestionPollBatchView>>('/dispatch/question-poll/manual', payload)
}

export function getRecentManualQuestionPollBatches(size = 20) {
  return request.get<R<ManualQuestionPollBatchView[]>>('/dispatch/question-poll/manual', {
    params: { size },
  })
}

export function getManualQuestionPollBatch(batchId: number) {
  return request.get<R<ManualQuestionPollBatchView>>(`/dispatch/question-poll/manual/${batchId}`)
}
