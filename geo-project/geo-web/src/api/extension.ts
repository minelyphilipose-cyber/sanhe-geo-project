import request from './request'
import type { R } from '@/types'

export interface ExtensionBindCode {
  code: string
  brandId: number
  operatorId: number
  expiresInSeconds: number
}

export interface ExtensionSession {
  id: number
  brandId?: number | null
  operatorId: number
  installId: string
  extensionVersion?: string | null
  userAgent?: string | null
  status: string
  boundAt?: string | null
  lastSeenAt?: string | null
  expiresAt?: string | null
}

export function createExtensionBindCode(brandId: number) {
  return request.post<R<ExtensionBindCode>>('/v1/extension/bind-codes', { brandId })
}

export function listBrandExtensionSessions(brandId: number) {
  return request.get<R<ExtensionSession[]>>(`/v1/extension/brands/${brandId}/sessions`)
}

export function revokeBrandExtensionSession(brandId: number, sessionId: number) {
  return request.post<R<void>>(`/v1/extension/brands/${brandId}/sessions/${sessionId}/revoke`)
}
