import request from './request'
import type { R, LoginRequest, LoginResponse } from '@/types'

export function loginApi(data: LoginRequest) {
  return request.post<R<LoginResponse>>('/auth/login', data)
}

export function refreshTokenApi(refreshToken: string) {
  return request.post<R<{ accessToken: string }>>('/auth/refresh', { refreshToken })
}

export function logoutApi() {
  return request.post<R<void>>('/auth/logout')
}

export function meApi() {
  return request.get<R<{
    id: number
    username: string
    displayName: string
    role: string
    partnerId: number | null
    isActive: boolean
    permissions: string[]
  }>>('/me')
}
