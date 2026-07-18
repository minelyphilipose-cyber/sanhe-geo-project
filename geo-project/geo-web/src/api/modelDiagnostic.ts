import request from './request'
import type { R } from '@/types'

export const DIAGNOSTIC_REQUEST_TIMEOUT_MS = 200_000

export type DiagnosticMode = 'BASIC_CHAT' | 'WEB_SEARCH'
export type DiagnosticModelTier = 'PRIMARY' | 'LOW'
export type DiagnosticTestMode = 'FREE_CHAT' | 'STANDARD_PROBE' | 'PRODUCTION_POLL_TEMPLATE'

export interface DiagnosticPlatformOption {
  platformConfigId: number
  channelCode: string
  platformCode: string
  platformName: string
  modelId: string | null
  modelTier: DiagnosticModelTier
  usageScene: string | null
  integrationType: string | null
  enabled: boolean | null
  enabledForQuestionPoll: boolean | null
  credentialAvailable: boolean
  supportedModes: DiagnosticMode[]
  responseModes: string[]
  selectable: boolean
  unavailableReason: string | null
}

export interface DiagnosticProbeOption {
  code: string
  version: string
  templateVersion: string | null
  label: string
  diagnosticMode: DiagnosticMode
  testMode: DiagnosticTestMode
  inputMode: 'FIXED' | 'USER_REQUIRED' | null
  userMessageRequired: boolean
}

export interface DiagnosticRunRequest {
  sessionId: string
  clientRequestId: string
  platformConfigId: number
  modelTier: DiagnosticModelTier
  mode: DiagnosticMode
  testMode: DiagnosticTestMode
  probeCode?: string
  systemPrompt?: string
  userMessage?: string
}

export interface DiagnosticRunView {
  runId: number
  sessionId: string
  turnNo: number
  platformConfigId: number
  platformName: string
  diagnosticMode: DiagnosticMode
  testMode: DiagnosticTestMode
  status: 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'REJECTED' | 'ABANDONED'
  conclusion: 'PASS' | 'WARNING' | 'FAIL' | null
  conclusionReason: string | null
  userMessage: string
  assistantMessage: string | null
  providerRequestId: string | null
  requestedModelId: string | null
  responseModelId: string | null
  httpStatus: number | null
  durationMs: number | null
  responseMode: 'SYNC'
  promptTokens: number | null
  completionTokens: number | null
  totalTokens: number | null
  webSearchCallCount: number | null
  searchStatus: string | null
  sourceCount: number | null
  validSourceCount: number | null
  citationCount: number | null
  validCitationCount: number | null
  capabilities: Record<string, string | null>
  searchEvidence: unknown[]
  sources: Array<Record<string, unknown>>
  citations: Array<Record<string, unknown>>
  usage: Record<string, unknown>
  sanitizedRequest: string | null
  sanitizedResponse: string | null
  error: { category?: string; code?: string; message?: string } | null
  startedAt: string | null
  completedAt: string | null
  createdAt: string
}

export interface DiagnosticRunSummary {
  id: number
  sessionId: string
  turnNo: number
  platformName: string
  requestedModelId: string | null
  diagnosticMode: DiagnosticMode
  testMode: DiagnosticTestMode
  status: string
  conclusion: string | null
  durationMs: number | null
  createdAt: string
}

export interface DiagnosticHistoryPage {
  records: DiagnosticRunSummary[]
  total: number
  page: number
  size: number
}

export function getDiagnosticPlatforms() {
  return request.get<R<DiagnosticPlatformOption[]>>('/admin/model-diagnostics/platforms')
}

export function getDiagnosticProbes() {
  return request.get<R<DiagnosticProbeOption[]>>('/admin/model-diagnostics/probes')
}

export function executeDiagnostic(payload: DiagnosticRunRequest) {
  return request.post<R<DiagnosticRunView>>('/admin/model-diagnostics/runs', payload, {
    timeout: DIAGNOSTIC_REQUEST_TIMEOUT_MS,
  })
}

export function getDiagnosticHistory(params: Record<string, unknown>) {
  return request.get<R<DiagnosticHistoryPage>>('/admin/model-diagnostics/runs', { params })
}

export function getDiagnosticSessionRuns(sessionId: string) {
  return request.get<R<DiagnosticRunView[]>>(`/admin/model-diagnostics/sessions/${encodeURIComponent(sessionId)}/runs`)
}
