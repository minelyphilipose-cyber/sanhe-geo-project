import request from './request'
import type { R } from '@/types'

export interface ExtensionBindCode {
  bindCode: string
  brandId: number
  operatorId: number
  ttlSeconds: number
}

export function createExtensionBindCode(brandId: number) {
  return request.post<R<ExtensionBindCode>>('/v1/extension/bind-codes', { brandId })
}
