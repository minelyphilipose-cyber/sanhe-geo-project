import request from './request'
import type {
  DispatchAlertItem,
  DispatchDashboardMetrics,
  LlmPoolSnapshot,
  DispatchPlatformHealthItem,
  DispatchTaskItem,
  PageResult,
  R,
} from '@/types'

export interface DispatchRangeParams {
  rangeType?: 'today' | 'last7' | 'last30' | 'custom'
  startDate?: string
  endDate?: string
  projectId?: number
}

export interface DispatchTaskQuery extends DispatchRangeParams {
  current?: number
  size?: number
  projectId?: number
  taskType?: string
  status?: string
  keyword?: string
}

export interface DispatchAlertQuery extends DispatchRangeParams {
  current?: number
  size?: number
  severity?: string
  status?: string
}

export function getDispatchDashboard(params?: DispatchRangeParams) {
  return request.get<R<DispatchDashboardMetrics>>('/dispatch/monitor/dashboard', { params })
}

export function getDispatchTasks(params?: DispatchTaskQuery) {
  return request.get<R<PageResult<DispatchTaskItem>>>('/dispatch/monitor/tasks', { params })
}

export function getDispatchPlatforms(params?: DispatchRangeParams) {
  return request.get<R<DispatchPlatformHealthItem[]>>('/dispatch/monitor/platforms', { params })
}

export function getLlmPoolSnapshot() {
  return request.get<R<LlmPoolSnapshot>>('/monitoring/llm-pool')
}

export function getDispatchAlerts(params?: DispatchAlertQuery) {
  return request.get<R<PageResult<DispatchAlertItem>>>('/dispatch/monitor/alerts', { params })
}

export function getDispatchAlert(id: number) {
  return request.get<R<DispatchAlertItem>>(`/dispatch/monitor/alerts/${id}`)
}

export function resolveDispatchAlert(id: number, note?: string) {
  return request.post<R<void>>(`/dispatch/monitor/alerts/${id}/resolve`, { note })
}

export function replayDispatchTask(taskId: number) {
  return request.post<R<void>>('/dispatch/tasks/replay', { taskId })
}

export function getDispatchTask(taskId: number) {
  return request.get<R<DispatchTaskItem>>(`/dispatch/monitor/tasks/${taskId}`)
}
