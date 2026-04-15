import request from './request'
import type { R } from '@/types'

export interface DashboardOverviewVO {
  totalCustomers: number
  activeProjects: number
  totalProjects: number
  monthlyReports: number
  openAlerts: number
  totalPartners: number | null
  monthlyNewCustomers: number
  highRiskProjects: number
}

export interface PendingItemVO {
  type: string
  title: string
  description: string
  targetPath: string
  targetId: number | null
  createdAt: string | null
  priority: 'high' | 'medium' | 'low' | string
}

export interface ProjectStageDistributionVO {
  stage: string
  label: string
  count: number
}

export interface ReportTrendVO {
  date: string
  count: number
}

export function getDashboardOverview() {
  return request.get<R<DashboardOverviewVO>>('/dashboard/overview')
}

export function getDashboardPendingItems(limit = 20) {
  return request.get<R<PendingItemVO[]>>('/dashboard/pending-items', { params: { limit } })
}

export function getDashboardStageDistribution() {
  return request.get<R<ProjectStageDistributionVO[]>>('/dashboard/project-stage-distribution')
}

export function getDashboardReportTrend(days = 30) {
  return request.get<R<ReportTrendVO[]>>('/dashboard/report-trend', { params: { days } })
}
