import { defineStore } from 'pinia'
import {
  exchangeMobileDashboardSession,
  getMobileDashboardBootstrap,
} from '@/api/mobileDashboard'
import type { MobileDashboardBootstrap, MobileDashboardSession } from '@/types/mobileDashboard'

const SHARE_CODE_KEY = 'geo_mobile_dashboard_share_code'
const SESSION_TOKEN_KEY = 'geo_mobile_dashboard_session_token'
const CONTEXT_KEY = 'geo_mobile_dashboard_context'

function readJson<T>(key: string): T | null {
  const raw = sessionStorage.getItem(key)
  if (!raw) return null
  try {
    return JSON.parse(raw) as T
  } catch {
    sessionStorage.removeItem(key)
    return null
  }
}

export const useMobileDashboardStore = defineStore('mobileDashboard', {
  state: () => ({
    sessionToken: sessionStorage.getItem(SESSION_TOKEN_KEY) || '',
    shareCode: sessionStorage.getItem(SHARE_CODE_KEY) || '',
    context: readJson<MobileDashboardSession | MobileDashboardBootstrap>(CONTEXT_KEY),
    initialized: false,
  }),

  getters: {
    brandName: (state) => state.context?.brandName || '月娇家居',
    projectName: (state) => state.context?.projectName || '',
    contentPlatforms: (state) => state.context?.contentPlatforms || [],
  },

  actions: {
    async initialize(entryShareCode?: string) {
      const codeFromUrl = entryShareCode?.trim()
      if (codeFromUrl) {
        await this.exchange(codeFromUrl)
        this.initialized = true
        return
      }

      if (this.sessionToken) {
        try {
          await this.loadBootstrap()
          this.initialized = true
          return
        } catch {
          this.clearSessionOnly()
        }
      }

      if (this.shareCode) {
        await this.exchange(this.shareCode)
        this.initialized = true
        return
      }

      throw new Error('请使用有效的数据看板分享链接访问')
    },

    async exchange(shareCode: string) {
      const res = await exchangeMobileDashboardSession(shareCode)
      const data = res.data.data
      this.shareCode = shareCode
      this.sessionToken = data.sessionToken
      this.context = data
      sessionStorage.setItem(SHARE_CODE_KEY, shareCode)
      sessionStorage.setItem(SESSION_TOKEN_KEY, data.sessionToken)
      sessionStorage.setItem(CONTEXT_KEY, JSON.stringify(data))
    },

    async renewSession() {
      if (!this.shareCode) {
        throw new Error('请使用有效的数据看板分享链接访问')
      }
      await this.exchange(this.shareCode)
      return this.sessionToken
    },

    async loadBootstrap() {
      const res = await getMobileDashboardBootstrap(this.sessionToken)
      this.context = res.data.data
      sessionStorage.setItem(CONTEXT_KEY, JSON.stringify(res.data.data))
    },

    clearSessionOnly() {
      this.sessionToken = ''
      sessionStorage.removeItem(SESSION_TOKEN_KEY)
    },

    clearAll() {
      this.sessionToken = ''
      this.shareCode = ''
      this.context = null
      this.initialized = false
      sessionStorage.removeItem(SESSION_TOKEN_KEY)
      sessionStorage.removeItem(SHARE_CODE_KEY)
      sessionStorage.removeItem(CONTEXT_KEY)
    },
  },
})
