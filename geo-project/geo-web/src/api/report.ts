import request from './request'
import type { R, PageResult, Report } from '@/types'

export function getReportList(params: {
  current?: number
  size?: number
  projectId?: number
  reportType?: string
  status?: string
}) {
  return request.get<R<PageResult<Report>>>('/reports', { params })
}

export function getReportDetail(id: number) {
  return request.get<R<Report>>(`/reports/${id}`)
}

export function generateReport(data: { projectId: number; reportType: string }) {
  return request.post<R<Report>>('/reports/generate', data)
}

export function publishReport(id: number) {
  return request.put<R<void>>(`/reports/${id}/publish`)
}

export function interceptReport(id: number, reason: string) {
  return request.put<R<void>>(`/reports/${id}/intercept`, { reason })
}

/* 公开分享接口 (无需 JWT) */
export function getShareReport(token: string) {
  return request.get<R<any>>(`/share/${token}`)
}

export function verifySharePassword(token: string, password: string) {
  return request.post<R<any>>(`/share/${token}/verify`, { password })
}
