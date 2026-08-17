import request from './request'
import type { R } from '@/types'

export interface AccountAuthWorkOrderPage {
  records: AccountAuthWorkOrder[]
  total: number
  current: number
  size: number
  unclaimedCount: number
  processingCount: number
  resolvedCount: number
}

export interface AccountAuthWorkOrder {
  id: number
  brandId: number
  browserEnvironmentId?: number | null
  status: string
  priority: string
  ownerUserId?: number | null
  ownerName?: string | null
  issueSummary?: string | null
  latestNote?: string | null
  nextFollowUpAt?: string | null
  resolvedAt?: string | null
  resolutionMode?: string | null
  resolutionReason?: string | null
  updatedAt?: string | null
}

export interface AccountAuthWorkOrderEvent {
  id: number
  action: string
  fromStatus?: string | null
  toStatus?: string | null
  note?: string | null
  operatorId?: number | null
  operatorName?: string | null
  createdAt: string
}

export function listAccountAuthWorkOrders(params?: { stage?: string; ownerUserId?: number; current?: number; size?: number }) {
  return request.get<R<AccountAuthWorkOrderPage>>('/content/account-auth-health/work-orders', { params })
}

export function getOrCreateAccountAuthWorkOrder(payload: {
  brandId: number
  browserEnvironmentId?: number | null
  issueSummary?: string
}) {
  return request.post<R<AccountAuthWorkOrder>>('/content/account-auth-health/work-orders', payload)
}

export function updateAccountAuthWorkOrder(id: number, payload: {
  status?: string
  priority?: string
  note?: string
  nextFollowUpAt?: string | null
  resolutionMode?: 'login_restored' | 'special'
  resolutionReason?: string
  affectedAccountIds?: number[]
}) {
  return request.patch<R<AccountAuthWorkOrder>>(`/content/account-auth-health/work-orders/${id}`, payload)
}

export function claimAccountAuthWorkOrder(id: number) {
  return request.post<R<AccountAuthWorkOrder>>(`/content/account-auth-health/work-orders/${id}/claim`)
}

export function listAccountAuthWorkOrderEvents(id: number) {
  return request.get<R<AccountAuthWorkOrderEvent[]>>(`/content/account-auth-health/work-orders/${id}/events`)
}
