import axios, { type AxiosRequestConfig, type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import router from '@/router'
import type { R } from '@/types'

/* ====================================================
   Axios 实例
   ==================================================== */
const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
})

/* ====================================================
   请求拦截 — 注入 access_token
   ==================================================== */
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

/* ====================================================
   响应拦截 — 统一错误处理 + token 刷新
   ==================================================== */
let isRefreshing = false
let pendingQueue: Array<(token: string) => void> = []

request.interceptors.response.use(
  (response: AxiosResponse<R>) => {
    const res = response.data

    // 后端返回 code !== 0 视为业务错误
    if (res.code !== 0) {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message))
    }

    return response
  },

  async (error) => {
    const originalRequest = error.config as AxiosRequestConfig & { _retry?: boolean }

    // 401: access_token 过期 → 尝试刷新
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true

      if (!isRefreshing) {
        isRefreshing = true
        try {
          const userStore = useUserStore()
          const newToken = await userStore.refreshAccessToken()

          // 刷新成功 → 重放队列中的请求
          pendingQueue.forEach((cb) => cb(newToken))
          pendingQueue = []

          // 重放当前请求
          originalRequest.headers = originalRequest.headers || {}
          originalRequest.headers.Authorization = `Bearer ${newToken}`
          return request(originalRequest)
        } catch {
          // refresh 也失败 → 登出
          const userStore = useUserStore()
          userStore.logout()
          router.push('/login')
          return Promise.reject(error)
        } finally {
          isRefreshing = false
        }
      } else {
        // 正在刷新中 → 排队等待
        return new Promise((resolve) => {
          pendingQueue.push((token: string) => {
            originalRequest.headers = originalRequest.headers || {}
            originalRequest.headers.Authorization = `Bearer ${token}`
            resolve(request(originalRequest))
          })
        })
      }
    }

    // 403: 无权限
    if (error.response?.status === 403) {
      ElMessage.error('无操作权限')
    }

    // 其他错误
    const msg = error.response?.data?.message || error.message || '网络异常'
    ElMessage.error(msg)
    return Promise.reject(error)
  },
)

export default request
