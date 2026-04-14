import request from './request'
import type { AIPlatformConfigItem, PageResult, R } from '@/types'

export function getPlatformConfigPage(params: {
  current?: number
  size?: number
  keyword?: string
  priorityLevel?: string
  enabled?: boolean
}) {
  return request.get<R<PageResult<AIPlatformConfigItem>>>('/admin/platform-configs', { params })
}

export function createPlatformConfig(data: {
  platformCode: string
  platformName: string
  priorityLevel: string
  apiKey: string
  primaryKeyRef?: string
  backupKeyRef?: string
  backupProviderName?: string
  backupApiUrl?: string
  backupModelId?: string
  apiUrl: string
  modelId: string
  modelName: string
  enabled: boolean
  degraded: boolean
  degradedReason?: string
  remark?: string
}) {
  return request.post<R<AIPlatformConfigItem>>('/admin/platform-configs', data)
}

export function updatePlatformConfig(id: number, data: {
  platformCode: string
  platformName: string
  priorityLevel: string
  apiKey: string
  primaryKeyRef?: string
  backupKeyRef?: string
  backupProviderName?: string
  backupApiUrl?: string
  backupModelId?: string
  apiUrl: string
  modelId: string
  modelName: string
  enabled: boolean
  degraded: boolean
  degradedReason?: string
  remark?: string
}) {
  return request.put<R<AIPlatformConfigItem>>(`/admin/platform-configs/${id}`, data)
}

export function deletePlatformConfig(id: number) {
  return request.delete<R<void>>(`/admin/platform-configs/${id}`)
}
