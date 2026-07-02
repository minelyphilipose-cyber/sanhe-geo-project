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
    partnerName: string | null
    phone: string | null
    email: string | null
    avatarUrl: string | null
    isActive: boolean
    permissions: string[]
  }>>('/me')
}

export function updateMyProfileApi(payload: {
  displayName: string
  phone?: string
  email?: string
}) {
  return request.put<R<{
    id: number
    username: string
    displayName: string
    role: string
    partnerId: number | null
    partnerName: string | null
    phone: string | null
    email: string | null
    avatarUrl: string | null
    isActive: boolean
    permissions: string[]
  }>>('/me/profile', payload)
}

export function uploadMyAvatarApi(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<R<{
    id: number
    username: string
    displayName: string
    role: string
    partnerId: number | null
    partnerName: string | null
    phone: string | null
    email: string | null
    avatarUrl: string | null
    isActive: boolean
    permissions: string[]
  }>>('/me/avatar', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function changeMyPasswordApi(payload: {
  oldPassword: string
  newPassword: string
}) {
  return request.put<R<void>>('/me/password', payload)
}
