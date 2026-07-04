import request from './request'
import type { AccountAuthHealthOverview, R } from '@/types'

export function getAccountAuthHealthOverview() {
  return request.get<R<AccountAuthHealthOverview>>('/content/account-auth-health/overview')
}

export function refreshAccountAuthHealthOverview() {
  return request.post<R<AccountAuthHealthOverview>>('/content/account-auth-health/refresh')
}
