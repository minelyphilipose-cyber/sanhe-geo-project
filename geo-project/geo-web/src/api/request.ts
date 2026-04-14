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

request.interceptors.request.use(
  (config) => {
    const userStore = useUserStore()
    if (userStore.accessToken) {
      config.headers.Authorization = `Bearer ${userStore.accessToken}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

let isRefreshing = false
let pendingQueue: Array<(token: string) => void> = []

request.interceptors.response.use(
  async (response: AxiosResponse<R>) => {
    const responseType = response.config.responseType
    if (responseType === 'blob' || responseType === 'arraybuffer') {
      return response
    }
    const res = response.data
    const reqUrl = response.config.url || ''
    const isAuthApi = reqUrl.includes('/auth/login') || reqUrl.includes('/auth/refresh') || reqUrl.includes('/auth/logout')

    if (res.code !== 0) {
      const userStore = useUserStore()
      if (res.code === 401 && !isAuthApi) {
        await userStore.logout()
        router.push('/login')
        return Promise.reject(new Error(res.message || '登录状态已失效'))
      }
      if (res.code === 403) {
        router.push('/403')
        return Promise.reject(new Error(res.message || '无权限访问'))
      }
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message))
    }

    return response
  },

  async (error) => {
    const originalRequest = error.config as AxiosRequestConfig & { _retry?: boolean }

    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true

      if (!isRefreshing) {
        isRefreshing = true
        try {
          const userStore = useUserStore()
          const newToken = await userStore.refreshAccessToken()

          pendingQueue.forEach((cb) => cb(newToken))
          pendingQueue = []

          originalRequest.headers = originalRequest.headers || {}
          originalRequest.headers.Authorization = `Bearer ${newToken}`
          return request(originalRequest)
        } catch {
          const userStore = useUserStore()
          await userStore.logout()
          router.push('/login')
          return Promise.reject(error)
        } finally {
          isRefreshing = false
        }
      }

      return new Promise((resolve) => {
        pendingQueue.push((token: string) => {
          originalRequest.headers = originalRequest.headers || {}
          originalRequest.headers.Authorization = `Bearer ${token}`
          resolve(request(originalRequest))
        })
      })
    }

    if (error.response?.status === 403) {
      router.push('/403')
      return Promise.reject(error)
    }

    const msg = error.response?.data?.message || error.message || '网络异常'
    ElMessage.error(msg)
    return Promise.reject(error)
  },
)

export default request
