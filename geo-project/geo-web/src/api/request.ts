import axios, { type AxiosRequestConfig, type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import router from '@/router'
import type { R } from '@/types'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
})

function syncAccessTokenFromResponse(response: AxiosResponse<any>) {
  const token = response.headers?.['x-access-token']
  if (!token) return
  const userStore = useUserStore()
  userStore.updateAccessToken(token)
}

function isAuthRequest(url?: string): boolean {
  if (!url) return false
  return url.includes('/auth/login') || url.includes('/auth/refresh')
}

function isRefreshRequest(url?: string): boolean {
  if (!url) return false
  return url.includes('/auth/refresh')
}

request.interceptors.request.use(
  (config) => {
    const userStore = useUserStore()
    if (userStore.accessToken && !isAuthRequest(config.url)) {
      config.headers.Authorization = `Bearer ${userStore.accessToken}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

let isRefreshing = false
let pendingQueue: Array<{
  resolve: (token: string) => void
  reject: (error: ApiError) => void
}> = []
const AUTH_STORAGE_KEY = 'geo_auth_v1'
const SESSION_EXPIRED_MESSAGE = '登录信息已超时，请重新登录'

export interface ApiError<T = unknown> extends Error {
  code?: number
  status?: number
  data?: T
}

function buildApiError(message: string, code?: number, data?: unknown, status?: number): ApiError {
  const err = new Error(message) as ApiError
  err.code = code
  err.data = data
  err.status = status
  return err
}

function buildLoginUrl() {
  const current = router.currentRoute.value
  const currentPath = current?.fullPath || ''
  const isAuthPage = currentPath.startsWith('/login') || currentPath.startsWith('/session-expired')
  const redirect = currentPath && !isAuthPage ? currentPath : '/admin/overview'
  return `/login?redirect=${encodeURIComponent(redirect)}`
}

async function redirectToLoginForExpiredSession() {
  const userStore = useUserStore()
  userStore.clearAuth()
  localStorage.removeItem(AUTH_STORAGE_KEY)
  ElMessage.info(SESSION_EXPIRED_MESSAGE)
  await router.replace(buildLoginUrl())
}

function rejectPendingQueue(error: ApiError) {
  pendingQueue.forEach((item) => item.reject(error))
  pendingQueue = []
}

function resolvePendingQueue(token: string) {
  pendingQueue.forEach((item) => item.resolve(token))
  pendingQueue = []
}

request.interceptors.response.use(
  async (response: AxiosResponse<R>) => {
    syncAccessTokenFromResponse(response)

    const responseType = response.config.responseType
    if (responseType === 'blob' || responseType === 'arraybuffer') {
      return response
    }
    const res = response.data
    const reqUrl = response.config.url || ''
    const isAuthApi = isAuthRequest(reqUrl)

    if (res.code !== 0) {
      if (isRefreshRequest(reqUrl)) {
        return Promise.reject(buildApiError(SESSION_EXPIRED_MESSAGE, 401, res.data, response.status))
      }
      if (res.code === 401 && !isAuthApi) {
        await redirectToLoginForExpiredSession()
        return Promise.reject(buildApiError(res.message || '登录状态已失效', res.code, res.data))
      }
      if (res.code === 403) {
        router.push('/403')
        return Promise.reject(buildApiError(res.message || '无权限访问', res.code, res.data))
      }
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(buildApiError(res.message || '请求失败', res.code, res.data))
    }

    return response
  },

  async (error) => {
    const originalRequest = error.config as AxiosRequestConfig & { _retry?: boolean }
    const isAuthApi = isAuthRequest(originalRequest?.url)

    if (error.response?.status === 401 && !isAuthApi && !originalRequest._retry) {
      originalRequest._retry = true

      if (!isRefreshing) {
        isRefreshing = true
        try {
          const userStore = useUserStore()
          const newToken = await userStore.refreshAccessToken()

          resolvePendingQueue(newToken)

          originalRequest.headers = originalRequest.headers || {}
          originalRequest.headers.Authorization = `Bearer ${newToken}`
          return request(originalRequest)
        } catch {
          const sessionError = buildApiError(SESSION_EXPIRED_MESSAGE, 401, error.response?.data, 401)
          rejectPendingQueue(sessionError)
          await redirectToLoginForExpiredSession()
          return Promise.reject(sessionError)
        } finally {
          isRefreshing = false
        }
      }

      return new Promise((resolve, reject) => {
        pendingQueue.push({
          resolve: (token: string) => {
            originalRequest.headers = originalRequest.headers || {}
            originalRequest.headers.Authorization = `Bearer ${token}`
            resolve(request(originalRequest))
          },
          reject,
        })
      })
    }

    if (error.response?.status === 403) {
      if (isAuthApi) {
        await redirectToLoginForExpiredSession()
        return Promise.reject(error)
      }

      if (!originalRequest._retry) {
        originalRequest._retry = true

        if (!isRefreshing) {
          isRefreshing = true
          try {
            const userStore = useUserStore()
            const newToken = await userStore.refreshAccessToken()

            resolvePendingQueue(newToken)

            originalRequest.headers = originalRequest.headers || {}
            originalRequest.headers.Authorization = `Bearer ${newToken}`
            return request(originalRequest)
          } catch {
            const sessionError = buildApiError(SESSION_EXPIRED_MESSAGE, 401, error.response?.data, 401)
            rejectPendingQueue(sessionError)
            await redirectToLoginForExpiredSession()
            return Promise.reject(sessionError)
          } finally {
            isRefreshing = false
          }
        }

        return new Promise((resolve, reject) => {
          pendingQueue.push({
            resolve: (token: string) => {
              originalRequest.headers = originalRequest.headers || {}
              originalRequest.headers.Authorization = `Bearer ${token}`
              resolve(request(originalRequest))
            },
            reject,
          })
        })
      }

      router.push('/403')
      return Promise.reject(error)
    }

    const responseData = error.response?.data
    if (responseData && typeof responseData === 'object' && 'code' in responseData) {
      const msg = responseData.message || error.message || '网络异常'
      ElMessage.error(msg)
      return Promise.reject(buildApiError(msg, responseData.code, responseData.data, error.response?.status))
    }

    const msg = error.response?.data?.message || error.message || '网络异常'
    ElMessage.error(msg)
    return Promise.reject(error)
  },
)

export default request
