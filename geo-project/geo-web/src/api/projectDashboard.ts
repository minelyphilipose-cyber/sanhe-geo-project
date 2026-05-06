import request from './request'
import type {
  ProjectDashboardAdvice,
  ProjectDashboardDetailResponse,
  ProjectDashboardRefreshResponse,
  ProjectDashboardShare,
  ProjectDashboardSnapshotStatusResponse,
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

export function refreshProjectDashboardSnapshot(projectId: number) {
  return request.post<R<ProjectDashboardRefreshResponse>>(`/projects/${projectId}/dashboard-snapshot/refresh`)
}

export function getProjectDashboardSnapshotStatus(projectId: number) {
  return request.get<R<ProjectDashboardSnapshotStatusResponse>>(`/projects/${projectId}/dashboard-snapshot/status`)
}

export function getProjectDashboardAdvice(projectId: number) {
  return request.get<R<ProjectDashboardAdvice | null>>(`/projects/${projectId}/dashboard-advice`)
}

export function saveProjectDashboardAdvice(projectId: number, payload: ProjectDashboardAdvice) {
  return request.put<R<ProjectDashboardAdvice>>(`/projects/${projectId}/dashboard-advice`, payload)
}

export function publishProjectDashboardAdvice(projectId: number, payload: ProjectDashboardAdvice) {
  return request.post<R<ProjectDashboardAdvice>>(`/projects/${projectId}/dashboard-advice/publish`, payload)
}

export function getPublicProjectDashboardSummary(shareCode: string, params?: { days?: number }) {
  return request.get<R<ProjectDashboardSummaryResponse>>(`/public/dashboard/${shareCode}/summary`, { params })
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
