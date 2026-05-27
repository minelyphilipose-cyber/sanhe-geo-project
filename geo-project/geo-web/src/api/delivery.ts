import request from './request'
import type { PageResult, R } from '@/types'

export interface DeliveryOverview {
  totalCustomers: number
  activeProjects: number
  highRiskProjects: number
  openExceptions: number
  failedDispatchTasks: number
  monthlyReports: number
  monthlyArticles: number
  activeOperators: number
}

export interface DeliveryOperatorStats {
  operatorId: number
  operatorName: string
  customerCount: number
  activeProjectCount: number
  highRiskProjectCount: number
  monthlyReportCount: number
  monthlyArticleCount: number
  openExceptionCount: number
  failedDispatchTaskCount: number
}

export interface DeliveryException {
  id: number
  alertCode?: string | null
  taskId?: number | null
  projectId?: number | null
  projectName?: string | null
  ownerId?: number | null
  ownerName?: string | null
  severity: string
  status: string
  title: string
  content?: string | null
  retryCount?: number | null
  contextJson?: string | null
  resolvedAt?: string | null
  resolvedBy?: number | null
  createdAt: string
}

export interface DeliveryExceptionQuery {
  current?: number
  size?: number
  severity?: string
  status?: string
}

export function getDeliveryOverview() {
  return request.get<R<DeliveryOverview>>('/delivery/dashboard/overview')
}

export function getDeliveryOperatorStats() {
  return request.get<R<DeliveryOperatorStats[]>>('/delivery/dashboard/operator-stats')
}

export function getDeliveryExceptions(params?: DeliveryExceptionQuery) {
  return request.get<R<PageResult<DeliveryException>>>('/delivery/dashboard/exceptions', { params })
}

export function handleDeliveryException(id: number, note?: string) {
  return request.post<R<void>>(`/delivery/dashboard/exceptions/${id}/handle`, { note })
}
