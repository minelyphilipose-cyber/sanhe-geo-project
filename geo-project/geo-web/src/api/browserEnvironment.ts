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

export interface SelfMediaAutomationReadinessIssue {
  code: string
  level: 'error' | 'warning' | string
  title: string
  action: string
  actionKey?: string | null
}

export interface SelfMediaAutomationAccountReadiness {
  selfMediaAccountId: number
  platform: string
  accountName?: string | null
  bindingConfigured: boolean
  browserEnvironmentAccountId?: number | null
  loginStatus?: BrowserEnvironmentLoginStatus | null
  loginReady: boolean
  issueCode?: string | null
  issueMessage?: string | null
}

export interface SelfMediaAutomationReadiness {
  brandId: number
  status: 'ready' | 'warning' | 'blocked' | string
  ready: boolean
  localAgent: {
    bound: boolean
    online: boolean
    sessionId?: number | null
    helperName?: string | null
    lastSeenAt?: string | null
    expiresAt?: string | null
  }
  browserEnvironment: {
    configured: boolean
    active: boolean
    id?: number | null
    environmentKey?: string | null
    providerProfileId?: string | null
    name?: string | null
  }
  extensionBinding: {
    bound: boolean
    online: boolean
    sessionId?: number | null
    environmentKey?: string | null
    providerProfileId?: string | null
    extensionVersion?: string | null
    expectedVersion?: string | null
    versionSupported?: boolean
    lastSeenAt?: string | null
    expiresAt?: string | null
  }
  accounts: SelfMediaAutomationAccountReadiness[]
  issues: SelfMediaAutomationReadinessIssue[]
}

export interface BrowserEnvironmentCreatePayload {
  brandId: number
  provider?: string | null
  environmentKey: string
  providerProfileId: string
  name?: string | null
  localAgentSessionId?: number | null
}

export interface BrowserEnvironmentUpdatePayload {
  providerProfileId?: string | null
  name?: string | null
  status?: string | null
  lastErrorCode?: string | null
  lastErrorMessage?: string | null
  localAgentSessionId?: number | null
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

export function getSelfMediaAutomationReadiness(brandId: number) {
  return request.get<R<SelfMediaAutomationReadiness>>(`/v1/brands/${brandId}/self-media-automation/readiness`)
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

export function resetBrowserEnvironmentAccountLoginIdentity(id: number) {
  return request.post<R<BrowserEnvironmentAccount>>(`/v1/browser-environment-accounts/${id}/reset-login-identity`)
}

export function deleteBrowserEnvironmentAccount(id: number) {
  return request.delete<R<void>>(`/v1/browser-environment-accounts/${id}`)
}

export function getBrowserEnvironmentAccountBySelfMedia(selfMediaAccountId: number) {
  return request.get<R<BrowserEnvironmentAccount | null>>(
    `/v1/browser-environment-accounts/by-self-media/${selfMediaAccountId}/status`,
  )
}
