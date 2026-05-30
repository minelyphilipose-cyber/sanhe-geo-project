import request from '@/api/request'
import type { R } from '@/types'

export interface LocalAgentSession {
  id: number
  brandId?: number | null
  helperName?: string | null
  status: string
  boundAt?: string | null
  lastSeenAt?: string | null
  expiresAt?: string | null
}

export interface LocalAgentPairingApprovePayload {
  pairingCode: string
}

export interface LocalAgentPairingApproveResponse {
  sessionId: number
  brandId?: number | null
  expiresAt?: string | null
}

export interface LocalAgentSignPayload {
  method: string
  path: string
  bodyHash: string
}

export interface LocalAgentSignResponse {
  headers: Record<string, string>
}

export function approveLocalAgentPairing(payload: LocalAgentPairingApprovePayload) {
  return request.post<R<LocalAgentPairingApproveResponse>>('/v1/local-agent/pairings/approve', payload)
}

export function listLocalAgentSessions() {
  return request.get<R<LocalAgentSession[]>>('/v1/local-agent/sessions')
}

export function signLocalAgentRequest(sessionId: number, payload: LocalAgentSignPayload) {
  return request.post<R<LocalAgentSignResponse>>(`/v1/local-agent/sessions/${sessionId}/sign`, payload)
}

export function revokeLocalAgentSession(sessionId: number) {
  return request.post<R<void>>(`/v1/local-agent/sessions/${sessionId}/revoke`)
}
