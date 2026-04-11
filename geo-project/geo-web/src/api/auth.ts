import request from './request'
import type { R, LoginRequest, LoginResponse } from '@/types'

export function loginApi(data: LoginRequest) {
  return request.post<R<LoginResponse>>('/auth/login', data)
}

export function refreshTokenApi() {
  return request.post<R<{ accessToken: string }>>('/auth/refresh')
}

export function logoutApi() {
  return request.post<R<void>>('/auth/logout')
}
