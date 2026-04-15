import request from './request'
import type { PackageContentConfig, PackagePlan, PageResult, R } from '@/types'

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
  platformP0Count: number
  platformP1Count: number
  platformP2Count: number
  perQuestionPlatformCalls: number
  perQuestionCallsP0: number
  perQuestionCallsP1: number
  perQuestionCallsP2: number
  biweeklyFrequency: number
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
}) {
  return request.post<R<PackagePlan>>('/admin/package-plans', data)
}

export function updatePackagePlan(id: number, data: {
  packageName: string
  standardPrice: number
  serviceMonths: number
  questionPoolSize: number
  coreQuestionCount: number
  platformP0Count: number
  platformP1Count: number
  platformP2Count: number
  perQuestionPlatformCalls: number
  perQuestionCallsP0: number
  perQuestionCallsP1: number
  perQuestionCallsP2: number
  biweeklyFrequency: number
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
}) {
  return request.put<R<PackagePlan>>(`/admin/package-plans/${id}`, data)
}

export function updatePackagePlanStatus(id: number, enabled: boolean) {
  return request.put<R<void>>(`/admin/package-plans/${id}/status`, { enabled })
}

export function getPackageContentConfigs(id: number) {
  return request.get<R<PackageContentConfig[]>>(`/admin/package-plans/${id}/content-configs`)
}

export function updatePackageContentConfigs(id: number, data: Array<{
  articleType: string
  articlesPerBatch: number
  questionsPerArticle: number
  publishSiteTier: string
  publishSiteCount: number
  isActive: boolean
}>) {
  return request.put<R<PackageContentConfig[]>>(`/admin/package-plans/${id}/content-configs`, data)
}
