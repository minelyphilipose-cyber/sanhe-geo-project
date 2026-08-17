import request from '@/api/request'
import type { R, SelfMediaAuthHealthPolicy, SelfMediaLoginObservation, SelfMediaLoginVerification } from '@/types'

export function getSelfMediaAuthHealthPolicy(platform: string) {
  return request.get<R<SelfMediaAuthHealthPolicy>>(`/content/self-media-platforms/${platform}/auth-health-policy`)
}

export function updateSelfMediaAuthHealthPolicy(
  platform: string,
  payload: Omit<SelfMediaAuthHealthPolicy, 'id' | 'platformCode' | 'updatedAt'> & { changeReason: string },
) {
  return request.put<R<SelfMediaAuthHealthPolicy>>(`/content/self-media-platforms/${platform}/auth-health-policy`, payload)
}

export function previewSelfMediaAuthHealthPolicyImpact(platform: string, reverifyIntervalDays: number, warningDays: number) {
  return request.get<R<{
    platform: string; activeAccountCount: number; currentlyOverdueCount: number; projectedOverdueCount: number
    projectedDueSoonCount: number; newlyOverdueCount: number; customerImpact: string
  }>>(`/content/self-media-platforms/${platform}/auth-health-policy/impact-preview`, {
    params: { reverifyIntervalDays, warningDays },
  })
}

export function createSelfMediaLoginVerification(brandId: number, accountId: number) {
  return request.post<R<SelfMediaLoginVerification>>(
    `/content/brands/${brandId}/self-media-accounts/${accountId}/login-verifications`,
  )
}

export function getSelfMediaLoginVerification(brandId: number, accountId: number, verificationId: number) {
  return request.get<R<SelfMediaLoginVerification>>(
    `/content/brands/${brandId}/self-media-accounts/${accountId}/login-verifications/${verificationId}`,
  )
}

export function getSelfMediaLoginObservations(brandId: number, accountId: number, limit = 30) {
  return request.get<R<SelfMediaLoginObservation[]>>(
    `/content/brands/${brandId}/self-media-accounts/${accountId}/login-observations`,
    { params: { limit } },
  )
}
