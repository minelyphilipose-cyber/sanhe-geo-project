import request from '@/api/request'
import type { R, SelfMediaAuthHealthPolicy, SelfMediaLoginVerification } from '@/types'

export function getSelfMediaAuthHealthPolicy(platform: string) {
  return request.get<R<SelfMediaAuthHealthPolicy>>(`/content/self-media-platforms/${platform}/auth-health-policy`)
}

export function updateSelfMediaAuthHealthPolicy(
  platform: string,
  payload: Omit<SelfMediaAuthHealthPolicy, 'id' | 'platformCode' | 'updatedAt'> & { changeReason: string },
) {
  return request.put<R<SelfMediaAuthHealthPolicy>>(`/content/self-media-platforms/${platform}/auth-health-policy`, payload)
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
