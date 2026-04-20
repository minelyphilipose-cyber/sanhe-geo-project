import request from './request'
import type {
  ProjectDashboardDetailResponse,
  ProjectDashboardShare,
  ProjectDashboardSummaryResponse,
  ProjectDashboardTrendResponse,
  R,
} from '@/types'

export function getProjectDashboardShares(projectId: number) {
  return request.get<R<ProjectDashboardShare[]>>(`/projects/${projectId}/dashboard-share`)
}

export function createProjectDashboardShare(projectId: number) {
  return request.post<R<ProjectDashboardShare>>(`/projects/${projectId}/dashboard-share`)
}

export function disableProjectDashboardShare(id: number) {
  return request.put<R<void>>(`/dashboard-shares/${id}/disable`)
}

export function getPublicProjectDashboardSummary(shareCode: string) {
  return request.get<R<ProjectDashboardSummaryResponse>>(`/public/dashboard/${shareCode}/summary`)
}

export function getPublicProjectDashboardTrend(shareCode: string, params?: { days?: number }) {
  return request.get<R<ProjectDashboardTrendResponse>>(`/public/dashboard/${shareCode}/trend`, { params })
}

export function getPublicProjectDashboardDetails(shareCode: string, params?: {
  page?: number
  size?: number
  platformCode?: string
  startDate?: string
  endDate?: string
  keyword?: string
}) {
  return request.get<R<ProjectDashboardDetailResponse>>(`/public/dashboard/${shareCode}/details`, { params })
}
