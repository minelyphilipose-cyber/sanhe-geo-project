import request from './request'
import type { R, PageResult } from '@/types'

export interface PartnerItem {
  id: number
  partnerCode: string
  partnerName: string
  partnerLevel: string
  discountRate: number
  status: string
  contactName?: string | null
  contactPhone?: string | null
  city?: string | null
  remark?: string | null
  createdAt: string
}

export interface PartnerAccount {
  id: number
  partnerId: number
  currentBalance: number
  totalRecharge: number
  totalDeduction: number
  currency: string
  status: string
}

export interface PartnerTxn {
  id: number
  partnerId: number
  accountId: number
  txnNo: string
  txnType: string
  bizType: string
  amount: number
  balanceBefore: number
  balanceAfter: number
  relatedProjectId?: number | null
  operatorUserId: number
  offlineReference?: string | null
  remark?: string | null
  createdAt: string
}

export function getPartnerList(params: {
  current?: number
  size?: number
  keyword?: string
  status?: string
}) {
  return request.get<R<PageResult<PartnerItem>>>('/partners', { params })
}

export function getPartnerDetail(id: number) {
  return request.get<R<PartnerItem>>(`/partners/${id}`)
}

export function createPartner(data: {
  partnerCode: string
  partnerName: string
  partnerLevel: string
  discountRate: number
  contactName?: string
  contactPhone?: string
  city?: string
  remark?: string
}) {
  return request.post<R<PartnerItem>>('/partners', data)
}

export function updatePartner(id: number, data: {
  partnerName: string
  partnerLevel: string
  discountRate: number
  status: string
  contactName?: string
  contactPhone?: string
  city?: string
  remark?: string
}) {
  return request.put<R<PartnerItem>>(`/partners/${id}`, data)
}

export function updatePartnerStatus(id: number, status: string) {
  return request.put<R<void>>(`/partners/${id}/status`, { status })
}

export function getPartnerAccount(id: number) {
  return request.get<R<PartnerAccount>>(`/partners/${id}/account`)
}

export function getPartnerAccountTxns(id: number, params: { current?: number; size?: number }) {
  return request.get<R<PageResult<PartnerTxn>>>(`/partners/${id}/account/txns`, { params })
}

export function rechargePartnerAccount(id: number, data: {
  amount: number
  offlineReference?: string
  remark?: string
}) {
  return request.post<R<PartnerTxn>>(`/partners/${id}/account/recharge`, data)
}

export function adjustPartnerAccount(id: number, data: {
  amount: number
  remark?: string
}) {
  return request.post<R<PartnerTxn>>(`/partners/${id}/account/adjust`, data)
}

