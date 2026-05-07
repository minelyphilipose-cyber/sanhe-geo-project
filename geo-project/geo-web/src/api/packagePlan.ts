import request from './request'
import type { PackageChannelQuotaConfig, PackagePlan, PageResult, R } from '@/types'

export interface PackageChannelQuotaPayload {
  channelCode: string
  periodType: 'day' | 'week' | 'month' | 'total' | string
  quotaLimit: number
  enabled: boolean
}

export function getEnabledPackagePlans() {
  return request.get<R<PackagePlan[]>>('/package-plans/enabled')
}

export function getAdminPackagePlans(params: {
  current?: number
  size?: number
  keyword?: string
  enabled?: boolean
}) {
  return request.get<R<PageResult<PackagePlan>>>('/admin/package-plans', { params })
}

export function createPackagePlan(data: {
  packageType: string
  packageName: string
  standardPrice: number
  serviceMonths: number
  questionPoolSize: number
  coreQuestionCount: number
  monthlyReportDepth: string
  quarterlyReportDepth: string
  consultantIntensity: string
  competitorInsightDepth: string
  mediaDistributionIntensity: string
  commitmentTargetIntensity: string
  targetMetricType: string
  targetMetricValue: number
  targetWindowDays: number
  enabled: boolean
  sortOrder: number
  remark?: string
  channelQuotaConfigs?: PackageChannelQuotaPayload[]
}) {
  return request.post<R<PackagePlan>>('/admin/package-plans', data)
}

export function updatePackagePlan(id: number, data: {
  packageName: string
  standardPrice: number
  serviceMonths: number
  questionPoolSize: number
  coreQuestionCount: number
  monthlyReportDepth: string
  quarterlyReportDepth: string
  consultantIntensity: string
  competitorInsightDepth: string
  mediaDistributionIntensity: string
  commitmentTargetIntensity: string
  targetMetricType: string
  targetMetricValue: number
  targetWindowDays: number
  sortOrder: number
  remark?: string
  channelQuotaConfigs?: PackageChannelQuotaPayload[]
}) {
  return request.put<R<PackagePlan>>(`/admin/package-plans/${id}`, data)
}

export function updatePackagePlanStatus(id: number, enabled: boolean) {
  return request.put<R<void>>(`/admin/package-plans/${id}/status`, { enabled })
}

export function getPackageChannelQuotas(id: number) {
  return request.get<R<PackageChannelQuotaConfig[]>>(`/admin/package-plans/${id}/channel-quotas`)
}

export function updatePackageChannelQuotas(id: number, data: PackageChannelQuotaPayload[]) {
  return request.put<R<PackageChannelQuotaConfig[]>>(`/admin/package-plans/${id}/channel-quotas`, data)
}
