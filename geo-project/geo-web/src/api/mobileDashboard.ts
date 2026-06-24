import axios, { type AxiosError, type AxiosResponse } from 'axios'
import request from './request'
import type { R } from '@/types'
import type {
  MobileDashboardBootstrap,
  ContentDashboardData,
  EntityJudgeBudgetConfig,
  EntityJudgeBudgetConfigPayload,
  HomeDashboardData,
  MobileDashboardOperations,
  MobileDashboardSession,
  MobileDashboardShare,
  MobileDashboardShareAccessSummary,
  MonitorDashboardData,
  ReportDashboardData,
} from '@/types/mobileDashboard'

interface MobileApiError<T = unknown> extends Error {
  code?: number
  status?: number
  data?: T
}

const mobileRequest = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
})

function buildApiError(message: string, code?: number, data?: unknown, status?: number): MobileApiError {
  const err = new Error(message) as MobileApiError
  err.code = code
  err.data = data
  err.status = status
  return err
}

mobileRequest.interceptors.response.use(
  (response: AxiosResponse<R>) => {
    const res = response.data
    if (res.code !== 0) {
      return Promise.reject(buildApiError(res.message || '请求失败', res.code, res.data, response.status))
    }
    return response
  },
  (error: AxiosError<R>) => {
    const res = error.response?.data
    if (res && typeof res === 'object' && 'code' in res) {
      return Promise.reject(buildApiError(res.message || '网络异常', res.code, res.data, error.response?.status))
    }
    return Promise.reject(buildApiError(error.message || '网络异常', undefined, error.response?.data, error.response?.status))
  },
)

export function exchangeMobileDashboardSession(token: string) {
  return mobileRequest.post<R<MobileDashboardSession>>('/public/mobile-dashboard/session', { token })
}

export function getMobileDashboardBootstrap(sessionToken: string) {
  return mobileRequest.get<R<MobileDashboardBootstrap>>('/public/mobile-dashboard/bootstrap', {
    headers: { Authorization: `Bearer ${sessionToken}` },
  })
}

export function getMobileDashboardHome(sessionToken: string) {
  return mobileRequest.get<R<HomeDashboardData>>('/public/mobile-dashboard/home', {
    headers: { Authorization: `Bearer ${sessionToken}` },
  })
}

export function getMobileDashboardMonitor(sessionToken: string, platformCode?: string) {
  return mobileRequest.get<R<MonitorDashboardData>>('/public/mobile-dashboard/monitor', {
    headers: { Authorization: `Bearer ${sessionToken}` },
    params: platformCode && platformCode !== 'all' ? { platformCode } : undefined,
  })
}

export function getMobileDashboardContent(sessionToken: string) {
  return mobileRequest.get<R<ContentDashboardData>>('/public/mobile-dashboard/content', {
    headers: { Authorization: `Bearer ${sessionToken}` },
  })
}

export function getMobileDashboardReport(sessionToken: string) {
  return mobileRequest.get<R<ReportDashboardData>>('/public/mobile-dashboard/report', {
    headers: { Authorization: `Bearer ${sessionToken}` },
  })
}

export function getMobileDashboardShares(projectId: number) {
  return request.get<R<MobileDashboardShare[]>>(`/projects/${projectId}/mobile-dashboard-share`)
}

export function getMobileDashboardShareAccessSummary(projectId: number) {
  return request.get<R<MobileDashboardShareAccessSummary[]>>(`/projects/${projectId}/mobile-dashboard-share/access-summary`)
}

export function createMobileDashboardShare(projectId: number, payload?: { expiresAt?: string }) {
  return request.post<R<MobileDashboardShare>>(`/projects/${projectId}/mobile-dashboard-share`, payload || {})
}

export function disableMobileDashboardShare(id: number) {
  return request.put<R<void>>(`/mobile-dashboard-shares/${id}/disable`)
}

export interface ProjectCompetitorConfig {
  id?: number
  projectId?: number
  competitorName: string
  aliases: string[]
  advantages?: string | null
  disadvantages?: string | null
  displayOrder: number
  status?: 'active' | 'disabled' | string
  qaStatus?: 'pending' | 'passed' | 'failed' | string
  qaCheckedAt?: string | null
  configVersion?: number
  updatedAt?: string | null
}

export interface ProjectCompetitorConfigPayloadItem {
  id?: number
  competitorName: string
  aliases?: string[]
  advantages?: string | null
  disadvantages?: string | null
  displayOrder: number
  active: boolean
  qaStatus?: 'pending' | 'passed' | 'failed' | string
}

export interface EntityJudgeRunResult {
  scanned: number
  judged: number
  skipped: number
  failed: number
  budgetBlocked?: number
  budgetReason?: string | null
}

export function getProjectMobileDashboardCompetitors(projectId: number) {
  return request.get<R<ProjectCompetitorConfig[]>>(`/projects/${projectId}/mobile-dashboard/competitors`)
}

export function updateProjectMobileDashboardCompetitors(projectId: number, items: ProjectCompetitorConfigPayloadItem[]) {
  return request.put<R<ProjectCompetitorConfig[]>>(`/projects/${projectId}/mobile-dashboard/competitors`, { items })
}

export function runProjectMobileDashboardEntityJudge(projectId: number, payload?: { startDate?: string; endDate?: string; limit?: number }) {
  return request.post<R<EntityJudgeRunResult>>(`/projects/${projectId}/mobile-dashboard/entity-judge/run`, {
    projectId,
    ...(payload || {}),
  })
}

export function getProjectMobileDashboardOperations(projectId: number, params?: { startDate?: string; endDate?: string }) {
  return request.get<R<MobileDashboardOperations>>(`/projects/${projectId}/mobile-dashboard/operations`, { params })
}

export function getProjectMobileDashboardJudgeBudget(projectId: number) {
  return request.get<R<EntityJudgeBudgetConfig>>(`/projects/${projectId}/mobile-dashboard/entity-judge/budget`)
}

export function updateProjectMobileDashboardJudgeBudget(projectId: number, payload: EntityJudgeBudgetConfigPayload) {
  return request.put<R<EntityJudgeBudgetConfig>>(`/projects/${projectId}/mobile-dashboard/entity-judge/budget`, payload)
}
