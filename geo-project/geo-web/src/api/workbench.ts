import request from './request'
import type { R, SystemAlertTodoItem } from '@/types'

export interface WorkbenchTodo {
  id: number
  sourceType: string
  alertType?: string | null
  severity?: string | null
  message?: string | null
  customerName?: string | null
  brandName?: string | null
  route?: string | null
  createdAt?: string | null
}

export interface WorkbenchRiskGroup {
  customerName?: string | null
  brandName?: string | null
  riskCount: number
  highSeverityCount: number
  latestMessage?: string | null
  todos: WorkbenchTodo[]
}

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
  openTodoCount: number
  highSeverityTodoCount: number
  priorityTodos: WorkbenchTodo[]
  customerRiskGroups: WorkbenchRiskGroup[]
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
  priorityTodos: WorkbenchTodo[]
  customerRiskGroups: WorkbenchRiskGroup[]
}

export interface SalesWorkbenchOverview {
  customerCount: number
  signedCustomerCount: number
  potentialCustomerCount: number
  reportCount: number
  monthlyReportCount: number
  generatingReportCount: number
  doneReportCount: number
  failedReportCount: number
  openTodoCount: number
  highSeverityTodoCount: number
  priorityTodos: WorkbenchTodo[]
  customerRiskGroups: WorkbenchRiskGroup[]
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

export function getSalesWorkbenchOverview() {
  return request.get<R<SalesWorkbenchOverview>>('/workbench/sales/overview')
}

export function getManagerWorkbenchOverview() {
  return request.get<R<ManagerWorkbenchOverview>>('/workbench/manager/overview')
}

export function getSuperAdminWorkbenchOverview() {
  return request.get<R<SuperAdminWorkbenchOverview>>('/workbench/super-admin/overview')
}
