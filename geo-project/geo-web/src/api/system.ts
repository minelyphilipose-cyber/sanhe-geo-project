import request from './request'
import type { ActivityLog, PageResult, R, KeywordAffixWord, KeywordAffixWordOptionResult } from '@/types'

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

export type KeywordAffixKind = 'area' | 'prefix' | 'suffix' | 'industry' | 'compare'

export interface KeywordAffixWordQuery {
  current?: number
  size?: number
  type?: string
  affixKind?: KeywordAffixKind
  keyword?: string
  enabled?: boolean
}

export function getAdminKeywordAffixWords(params: KeywordAffixWordQuery) {
  return request.get<R<PageResult<KeywordAffixWord>>>('/admin/keyword-affix-words', { params })
}

export function createAdminKeywordAffixWord(payload: {
  type?: string
  affixKind: KeywordAffixKind
  wordText: string
  sortOrder: number
  enabled?: boolean
}) {
  return request.post<R<KeywordAffixWord>>('/admin/keyword-affix-words', payload)
}

export function updateAdminKeywordAffixWord(id: number, payload: {
  type?: string
  affixKind: KeywordAffixKind
  wordText: string
  sortOrder: number
}) {
  return request.put<R<KeywordAffixWord>>(`/admin/keyword-affix-words/${id}`, payload)
}

export function updateAdminKeywordAffixWordStatus(id: number, enabled: boolean) {
  return request.put<R<void>>(`/admin/keyword-affix-words/${id}/status`, { enabled })
}

export function getKeywordAffixWordOptions(params?: string | {
  type?: string
  industryTag?: string
  includeManual?: boolean
  scopeType?: string
  scopeId?: number
}) {
  const finalParams = typeof params === 'string' ? { type: params } : params
  return request.get<R<KeywordAffixWordOptionResult>>('/keyword-affix-words/options', {
    params: {
      type: finalParams?.type || undefined,
      industryTag: finalParams?.industryTag || undefined,
      includeManual: finalParams?.includeManual,
      scopeType: finalParams?.scopeType || undefined,
      scopeId: finalParams?.scopeId,
    },
  })
}

export interface BusinessCalendarFileStatus {
  year: number
  exists: boolean
  activeSource: 'runtime' | 'classpath' | 'missing' | string
  runtimePath: string
  classpathLocation: string
  sourceUrl: string | null
  updatedAt: string | null
  publishAllowedDays: number
  adjustedWorkdays: number
  holidays: number
}

export interface BusinessCalendarAdminStatus {
  targetYear: number
  today: string
  generationWindow: string
  generationAllowed: boolean
  superAdmin: boolean
  forceAvailable: boolean
  message: string
  calendar: BusinessCalendarFileStatus
}

export function getNextYearBusinessCalendarStatus() {
  return request.get<R<BusinessCalendarAdminStatus>>('/admin/business-calendar/next-year/status')
}

export function generateNextYearBusinessCalendar(force = false) {
  return request.post<R<BusinessCalendarAdminStatus>>('/admin/business-calendar/next-year/generate', { force })
}
