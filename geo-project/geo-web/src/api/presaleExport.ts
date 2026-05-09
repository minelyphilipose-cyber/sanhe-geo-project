import request from '@/api/request'
import { unwrap } from '@/api/presale/unwrap'
import type { R } from '@/types'

export type PresaleExportStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'CANCELED'

export interface PresaleExportCreateRequest {
  versionId: number
  exportProfile?: string
  editableContentHash?: string
  forceRefresh?: boolean
}

export interface PresaleExportResponse {
  exportId: number
  reportId: number
  versionId: number
  status: PresaleExportStatus
  idempotencyKey: string
  errorCode?: string | null
  errorMsg?: string | null
  errorDetail?: PresaleExportErrorDetail | null
  retryCount?: number | null
  fileKey?: string | null
  fileSize?: number | null
  filePages?: number | null
  expireAt?: string | null
  runningExportId?: number | null
  runningStatus?: PresaleExportStatus | null
}

export interface PresaleExportErrorDetail {
  pageId?: string
  block?: string
  field?: string
  overflowPx?: number
}

export function createPresaleExport(reportId: number, data: PresaleExportCreateRequest) {
  return unwrap(
    request.post<R<PresaleExportResponse>>(`/presale/reports/${reportId}/exports`, data)
  )
}

export function getPresaleExport(reportId: number, exportId: number) {
  return unwrap(
    request.get<R<PresaleExportResponse>>(`/presale/reports/${reportId}/exports/${exportId}`)
  )
}

export function cancelPresaleExport(reportId: number, exportId: number) {
  return unwrap(
    request.post<R<PresaleExportResponse>>(`/presale/reports/${reportId}/exports/${exportId}/cancel`, {})
  )
}

export function retryPresaleExport(reportId: number, exportId: number) {
  return unwrap(
    request.post<R<PresaleExportResponse>>(`/presale/reports/${reportId}/exports/${exportId}/retry`, {})
  )
}

export function buildPresaleExportDownloadUrl(reportId: number, exportId: number): string {
  return `/api/presale/reports/${reportId}/exports/${exportId}/download`
}

export async function downloadPresaleExportPdf(reportId: number, exportId: number) {
  const response = await request.get<Blob>(`/presale/reports/${reportId}/exports/${exportId}/download`, {
    responseType: 'blob'
  })
  const blob = response.data
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = resolveDownloadFileName(response.headers['content-disposition'], reportId, exportId)
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.URL.revokeObjectURL(url)
}

function resolveDownloadFileName(contentDisposition: unknown, reportId: number, exportId: number): string {
  const header = typeof contentDisposition === 'string' ? contentDisposition : ''
  const utf8Match = header.match(/filename\*=UTF-8''([^;]+)/i)
  if (utf8Match?.[1]) {
    return decodeURIComponent(utf8Match[1])
  }
  const quotedMatch = header.match(/filename="([^"]+)"/i)
  if (quotedMatch?.[1]) {
    return quotedMatch[1]
  }
  return `AI 可见度诊断报告_${reportId}-${exportId}.pdf`
}
