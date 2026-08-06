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
  channelCode?: string
  usageScene?: string
  integrationType?: string
  platformName: string
  platformHomeUrl?: string
  platformLogoUrl?: string
  priorityLevel: string
  rpmLimit?: number
  tpmLimit?: number
  apiKey: string
  primaryKeyRef?: string
  backupKeyRef?: string
  backupProviderName?: string
  backupApiUrl?: string
  backupModelId?: string
  apiUrl: string
  modelId: string
  lowModelId?: string
  modelName: string
  concurrencyLimit?: number
  enabled: boolean
  enabledForPresale?: boolean
  enabledForPresaleQuestionGeneration?: boolean
  presaleEvaluateEnabled?: boolean
  enabledForArticle?: boolean
  enabledForGeoQuestion?: boolean
  enabledForQuestionPoll?: boolean
  enabledForMobileDashboard?: boolean
  maxRetry?: number
  timeoutMs?: number
  rateLimitQps?: number
  degraded: boolean
  degradedReason?: string
  remark?: string
}) {
  return request.post<R<AIPlatformConfigItem>>('/admin/platform-configs', data)
}

export function updatePlatformConfig(id: number, data: {
  platformCode: string
  channelCode?: string
  usageScene?: string
  integrationType?: string
  platformName: string
  platformHomeUrl?: string
  platformLogoUrl?: string
  priorityLevel: string
  rpmLimit?: number
  tpmLimit?: number
  apiKey: string
  clearApiKey?: boolean
  clearPrimaryKeyRef?: boolean
  primaryKeyRef?: string
  backupKeyRef?: string
  backupProviderName?: string
  backupApiUrl?: string
  backupModelId?: string
  apiUrl: string
  modelId: string
  lowModelId?: string
  modelName: string
  concurrencyLimit?: number
  enabled: boolean
  enabledForPresale?: boolean
  enabledForPresaleQuestionGeneration?: boolean
  presaleEvaluateEnabled?: boolean
  enabledForArticle?: boolean
  enabledForGeoQuestion?: boolean
  enabledForQuestionPoll?: boolean
  enabledForMobileDashboard?: boolean
  maxRetry?: number
  timeoutMs?: number
  rateLimitQps?: number
  degraded: boolean
  degradedReason?: string
  remark?: string
}) {
  return request.put<R<AIPlatformConfigItem>>(`/admin/platform-configs/${id}`, data)
}

export function deletePlatformConfig(id: number) {
  return request.delete<R<void>>(`/admin/platform-configs/${id}`)
}

export function updatePresaleEnabled(id: number, enabledForPresale: boolean) {
  return request.put<R<AIPlatformConfigItem>>(`/admin/platform-configs/${id}/presale-enabled`, {
    enabledForPresale,
  })
}

export function uploadPlatformLogo(id: number, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<R<AIPlatformConfigItem>>(`/admin/platform-configs/${id}/logo`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
