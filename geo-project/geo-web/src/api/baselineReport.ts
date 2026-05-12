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

export function getBaselineReportOptions(projectId: number) {
  return request.get<R<BaselinePollOptions>>(`/projects/${projectId}/baseline-report/options`)
}

export function startBaselineReportPoll(projectId: number, data: { platformCodes: string[]; questionTiers: string[] }) {
  return request.post<R<BaselinePollBatch>>(`/projects/${projectId}/baseline-report/poll`, data)
}

export function getBaselineReportResults(projectId: number, params: { batchId?: number; current?: number; size?: number }) {
  return request.get<R<PageResult<BaselinePollResult>>>(`/projects/${projectId}/baseline-report/results`, { params })
}
