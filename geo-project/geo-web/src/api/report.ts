import request from './request'
import type { PresaleReportSnapshot, R, PageResult, Report } from '@/types'

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
  return request.get<R<{
    report: Report
    presaleSnapshot?: PresaleReportSnapshot
    postsaleSnapshot?: any
    subject?: {
      customerName?: string
      brandName?: string
      projectName?: string
    }
  }>>(`/reports/${id}`)
}

export function generateReport(data: { projectId: number; reportType: string }) {
  return request.post<R<Report>>('/reports/generate', data)
}

export function updatePresaleSnapshot(id: number, data: {
  diagnosisSummary?: string
  actionRecommendations?: string
}) {
  return request.put<R<Report>>(`/reports/${id}/presale-snapshot`, data)
}

export function publishReport(id: number, data?: { sharePassword?: string; shareExpiresAt?: string }) {
  return request.put<R<Report>>(`/reports/${id}/publish`, data || {})
}

export function interceptReport(id: number, reason: string) {
  return request.put<R<Report>>(`/reports/${id}/intercept`, { reason })
}

export function regenerateReportPdf(id: number) {
  return request.put<R<Report>>(`/reports/${id}/pdf/regenerate`)
}

export function regeneratePostsaleReport(id: number) {
  return request.post<R<{ client: Report; internal: Report }>>(`/reports/${id}/regenerate`)
}

/* 公开分享接口 (无需 JWT) */
export function getShareReport(token: string) {
  return request.get<R<any>>(`/share/${token}`)
}

export function verifySharePassword(token: string, password: string) {
  return request.post<R<any>>(`/share/${token}/verify`, { password })
}
