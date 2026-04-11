import request from './request'
import type { ActivityLog, PageResult, R } from '@/types'

export interface AdminUserItem {
  id: number
  username: string
  displayName: string
  primaryRole: string
  roleKeys: string[]
  partnerId: number | null
  phone: string | null
  email: string | null
  isActive: boolean
  lastLoginAt: string | null
  createdAt: string
  updatedAt: string
}

export interface RoleOption {
  id: number
  roleKey: string
  roleName: string
  roleType: string
  status: string
  sortOrder: number
}

export interface UserQuery {
  current?: number
  size?: number
  keyword?: string
  roleKey?: string
  partnerId?: number
  isActive?: boolean
}

export function getAdminUsers(params: UserQuery) {
  return request.get<R<PageResult<AdminUserItem>>>('/admin/users', { params })
}

export function getRoleOptions() {
  return request.get<R<RoleOption[]>>('/admin/roles')
}

export function createAdminUser(payload: Record<string, any>) {
  return request.post<R<AdminUserItem>>('/admin/users', payload)
}

export function updateAdminUser(id: number, payload: Record<string, any>) {
  return request.put<R<AdminUserItem>>(`/admin/users/${id}`, payload)
}

export function updateAdminUserStatus(id: number, isActive: boolean) {
  return request.put<R<void>>(`/admin/users/${id}/status`, { isActive })
}

export function resetAdminUserPassword(id: number, newPassword: string) {
  return request.post<R<void>>(`/admin/users/${id}/reset-password`, { newPassword })
}

export function bindAdminUserRole(id: number, roleKey: string) {
  return request.put<R<AdminUserItem>>(`/admin/users/${id}/role`, { roleKey })
}

export interface ActivityLogQuery {
  current?: number
  size?: number
  userId?: number
  action?: string
  targetType?: string
  targetId?: number
  dateFrom?: string
  dateTo?: string
}

export function getActivityLogs(params: ActivityLogQuery) {
  return request.get<R<PageResult<ActivityLog>>>('/admin/activity-logs', { params })
}
