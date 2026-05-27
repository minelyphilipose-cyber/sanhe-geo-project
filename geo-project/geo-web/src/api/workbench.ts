import request from './request'
import type { R, SystemAlertTodoItem } from '@/types'

export interface OperatorWorkbenchOverview {
  customerCount: number
  brandCount: number
  projectCount: number
  activeProjectCount: number
  highRiskProjectCount: number
  monthlyReportCount: number
  monthlyArticleCount: number
  failedDistributionTaskCount: number
  retryDistributionTaskCount: number
  semiAutoTaskCount: number
  inFlightExtensionTaskCount: number
  completedDistributionTaskCount: number
}

export interface ManagerWorkbenchOverview {
  activeUserCount: number
  activeOperatorCount: number
  permissionCount: number
  aiPlatformConfigCount: number
  publishSiteCount: number
  openSystemAlertCount: number
  highSeveritySystemAlertCount: number
  latestSystemAlerts: SystemAlertTodoItem[]
}

export interface SuperAdminWorkbenchOverview {
  totalUserCount: number
  activeUserCount: number
  totalCompanyCount: number
  totalProjectCount: number
  nullOwnerCompanyCount: number
  deprecatedEffectivePermissionCount: number
  openSystemAlertCount: number
}

export function getOperatorWorkbenchOverview() {
  return request.get<R<OperatorWorkbenchOverview>>('/workbench/operator/overview')
}

export function getManagerWorkbenchOverview() {
  return request.get<R<ManagerWorkbenchOverview>>('/workbench/manager/overview')
}

export function getSuperAdminWorkbenchOverview() {
  return request.get<R<SuperAdminWorkbenchOverview>>('/workbench/super-admin/overview')
}
