import request from '@/api/request'
import type { R } from '@/types'

export type BrowserEnvironmentLoginStatus =
  | 'unknown'
  | 'logged_in'
  | 'login_required'
  | 'mismatch'
  | 'expired'
  | 'error'
  | string

export interface BrowserEnvironmentAccount {
  id: number
  brandId: number
  browserEnvironmentId: number
  environmentKey?: string | null
  provider?: string | null
  providerProfileId?: string | null
  selfMediaAccountId: number
  platform: string
  expectedPlatformAccountId?: string | null
  expectedAccountName?: string | null
  loginStatus: BrowserEnvironmentLoginStatus
  lastVerifiedAt?: string | null
  lastLoginSeenAt?: string | null
  lastErrorCode?: string | null
  lastErrorMessage?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

export interface BrowserEnvironment {
  id: number
  brandId: number
  provider: string
  environmentKey: string
  providerProfileId: string
  name?: string | null
  status: string
  lastStartedAt?: string | null
  lastStoppedAt?: string | null
  lastErrorCode?: string | null
  lastErrorMessage?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

export interface BrowserEnvironmentCreatePayload {
  brandId: number
  provider?: string | null
  environmentKey: string
  providerProfileId: string
  name?: string | null
}

export interface BrowserEnvironmentUpdatePayload {
  providerProfileId?: string | null
  name?: string | null
  status?: string | null
  lastErrorCode?: string | null
  lastErrorMessage?: string | null
}

export interface BrowserEnvironmentAccountCreatePayload {
  browserEnvironmentId: number
  selfMediaAccountId: number
  expectedPlatformAccountId?: string | null
  expectedAccountName?: string | null
}

export interface BrowserEnvironmentAccountUpdatePayload {
  expectedPlatformAccountId?: string | null
  expectedAccountName?: string | null
  loginStatus?: BrowserEnvironmentLoginStatus
}

export function listBrowserEnvironments(brandId: number) {
  return request.get<R<BrowserEnvironment[]>>('/v1/browser-environments', { params: { brandId } })
}

export function createBrowserEnvironment(payload: BrowserEnvironmentCreatePayload) {
  return request.post<R<BrowserEnvironment>>('/v1/browser-environments', payload)
}

export function updateBrowserEnvironment(id: number, payload: BrowserEnvironmentUpdatePayload) {
  return request.patch<R<BrowserEnvironment>>(`/v1/browser-environments/${id}`, payload)
}

export function deleteBrowserEnvironment(id: number) {
  return request.delete<R<void>>(`/v1/browser-environments/${id}`)
}

export function createBrowserEnvironmentAccount(payload: BrowserEnvironmentAccountCreatePayload) {
  return request.post<R<BrowserEnvironmentAccount>>('/v1/browser-environment-accounts', payload)
}

export function updateBrowserEnvironmentAccount(id: number, payload: BrowserEnvironmentAccountUpdatePayload) {
  return request.patch<R<BrowserEnvironmentAccount>>(`/v1/browser-environment-accounts/${id}`, payload)
}

export function deleteBrowserEnvironmentAccount(id: number) {
  return request.delete<R<void>>(`/v1/browser-environment-accounts/${id}`)
}

export function getBrowserEnvironmentAccountBySelfMedia(selfMediaAccountId: number) {
  return request.get<R<BrowserEnvironmentAccount | null>>(
    `/v1/browser-environment-accounts/by-self-media/${selfMediaAccountId}/status`,
  )
}
