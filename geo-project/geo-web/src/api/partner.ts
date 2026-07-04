import request from './request'
import type { R, PageResult } from '@/types'

export interface PartnerItem {
  id: number
  partnerCode: string
  partnerName: string
  partnerLevel: string
  discountRate: number
  presaleReportFreeQuotaLimit?: number | null
  presaleReportExtraPoints?: number | null
  status: string
  contactName?: string | null
  contactPhone?: string | null
  city?: string | null
  remark?: string | null
  customerCount?: number | null
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

export interface PartnerRechargeOrder {
  id: number
  orderNo: string
  partnerId: number
  amount: number
  status: 'pending' | 'cancelled' | 'approved' | 'rejected' | 'expired'
  offlineReference?: string | null
  applyRemark?: string | null
  rejectReason?: string | null
  applicantUserId: number
  auditedBy?: number | null
  auditedAt?: string | null
  accountTxnId?: number | null
  expiresAt?: string | null
  createdAt: string
  updatedAt: string
}

export interface PartnerVoucherFile {
  fileName: string
  fileSize?: number | null
  contentType?: string | null
  objectKey?: string | null
  downloadUrl?: string | null
  previewUrl?: string | null
}

export interface PartnerStaff {
  id: number
  username: string
  displayName: string
  partnerId: number
  phone?: string | null
  email?: string | null
  isActive: boolean
  createdAt?: string | null
  updatedAt?: string | null
}

export interface PartnerStaffCreateResult {
  staff: PartnerStaff
  initialPassword: string
}

export interface PartnerStaffResetPasswordResult {
  staff: PartnerStaff
  newPassword: string
}

export interface PartnerCreateResult {
  partner: PartnerItem
  username: string
  initialPassword: string
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
  partnerName: string
  partnerLevel?: string
  discountRate: number
  initialAmount?: number
  initialOfflineReference?: string
  presaleReportFreeQuotaLimit?: number
  presaleReportExtraPoints?: number
  contactName?: string
  contactPhone?: string
  city?: string
  remark?: string
}) {
  return request.post<R<PartnerCreateResult>>('/partners', data)
}

export function updatePartner(id: number, data: {
  partnerName: string
  partnerLevel?: string
  discountRate: number
  presaleReportFreeQuotaLimit?: number
  presaleReportExtraPoints?: number
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

export function getPartnerAccountTxns(id: number, params: {
  current?: number
  size?: number
  txnType?: string
  bizType?: string
  dateFrom?: string
  dateTo?: string
}) {
  return request.get<R<PageResult<PartnerTxn>>>(`/partners/${id}/account/txns`, { params })
}

export function getPartnerRechargeOrders(id: number, params: {
  current?: number
  size?: number
  status?: string
}) {
  return request.get<R<PageResult<PartnerRechargeOrder>>>(`/partners/${id}/account/recharge-orders`, { params })
}

export function applyPartnerRecharge(id: number, data: {
  amount: number
  offlineReference?: string
  remark?: string
}) {
  return request.post<R<PartnerRechargeOrder>>(`/partners/${id}/account/recharge-orders`, data)
}

export function cancelPartnerRechargeOrder(id: number, orderId: number) {
  return request.post<R<void>>(`/partners/${id}/account/recharge-orders/${orderId}/cancel`)
}

export function auditPartnerRechargeOrder(id: number, orderId: number, data: {
  action: 'approve' | 'reject'
  rejectReason?: string
  remark?: string
}) {
  return request.post<R<PartnerRechargeOrder>>(`/partners/${id}/account/recharge-orders/${orderId}/audit`, data)
}

export function rechargePartnerAccount(id: number, data: {
  amount: number
  offlineReference?: string
  remark?: string
}) {
  return request.post<R<PartnerTxn>>(`/partners/${id}/account/recharge`, data)
}

export function uploadPartnerAccountVoucher(id: number, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<R<PartnerVoucherFile>>(`/partners/${id}/account/vouchers/upload`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function uploadPartnerInitialAccountVoucher(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<R<PartnerVoucherFile>>('/partners/account/vouchers/initial-upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function downloadPartnerAccountVoucher(id: number, objectKey: string) {
  return request.get<Blob>(`/partners/${id}/account/vouchers/download`, {
    params: { objectKey },
    responseType: 'blob',
  })
}

export function adjustPartnerAccount(id: number, data: {
  amount: number
  offlineReference?: string
  remark?: string
}) {
  return request.post<R<PartnerTxn>>(`/partners/${id}/account/adjust`, data)
}

export function getMyPartnerStaff() {
  return request.get<R<PartnerStaff[]>>('/partners/me/staff')
}

export function createMyPartnerStaff(data: {
  username: string
  displayName: string
  phone?: string
  email?: string
}) {
  return request.post<R<PartnerStaffCreateResult>>('/partners/me/staff', data)
}

export function updateMyPartnerStaff(staffUserId: number, data: {
  displayName: string
  phone?: string
  email?: string
}) {
  return request.put<R<PartnerStaff>>(`/partners/me/staff/${staffUserId}`, data)
}

export function updateMyPartnerStaffStatus(staffUserId: number, isActive: boolean) {
  return request.put<R<PartnerStaff>>(`/partners/me/staff/${staffUserId}/status`, { isActive })
}

export function deleteMyPartnerStaff(staffUserId: number) {
  return request.delete<R<void>>(`/partners/me/staff/${staffUserId}`)
}

export function resetMyPartnerStaffPassword(staffUserId: number) {
  return request.post<R<PartnerStaffResetPasswordResult>>(`/partners/me/staff/${staffUserId}/reset-password`)
}

