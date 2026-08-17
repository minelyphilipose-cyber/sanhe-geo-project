import request from './request'
import type { AccountAuthHealthOverview, R } from '@/types'

export function getAccountAuthHealthOverview() {
  return request.get<R<AccountAuthHealthOverview>>('/content/account-auth-health/overview')
}

export function refreshAccountAuthHealthOverview() {
  return request.post<R<{ status: string; acceptedAt: string; message: string }>>('/content/account-auth-health/refresh')
}

export function getAccountAuthRuntimeVersionCoverage() {
  return request.get<R<{
    generatedAt: string
    extensionVersions: Array<{ version: string; count: number; latestSeenAt?: string | null }>
    helperVersions: Array<{ version: string; count: number; latestSeenAt?: string | null }>
    recentExtensionInstallCount: number
    recentHelperCount: number
  }>>('/content/account-auth-health/runtime-version-coverage')
}
