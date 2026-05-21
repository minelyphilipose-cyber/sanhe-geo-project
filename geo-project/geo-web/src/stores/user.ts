import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { loginApi, refreshTokenApi, logoutApi, meApi } from '@/api/auth'
import { isPartnerRole } from '@/utils/constants'
import type { UserInfo, RoleType, LoginRequest } from '@/types'

const AUTH_STORAGE_KEY = 'geo_auth_v1'

interface PersistedAuth {
  accessToken: string
  refreshToken: string
  userInfo: UserInfo | null
}

function loadPersistedAuth(): PersistedAuth {
  try {
    const raw = localStorage.getItem(AUTH_STORAGE_KEY)
    if (!raw) {
      return { accessToken: '', refreshToken: '', userInfo: null }
    }
    const data = JSON.parse(raw) as PersistedAuth
    return {
      accessToken: data.accessToken || '',
      refreshToken: data.refreshToken || '',
      userInfo: data.userInfo || null,
    }
  } catch {
    return { accessToken: '', refreshToken: '', userInfo: null }
  }
}

export const useUserStore = defineStore('user', () => {
  const persisted = loadPersistedAuth()
  const accessToken = ref<string>(persisted.accessToken)
  const refreshToken = ref<string>(persisted.refreshToken)
  const userInfo = ref<UserInfo | null>(persisted.userInfo)
  const profileSynced = ref(false)

  const isLoggedIn = computed(() => !!accessToken.value && !!userInfo.value)
  const role = computed<RoleType | null>(() => userInfo.value?.role ?? null)
  const isPartner = computed(() => role.value ? isPartnerRole(role.value) : false)
  const displayName = computed(() => userInfo.value?.displayName ?? '')
  const avatarUrl = computed(() => userInfo.value?.avatarUrl ?? '')
  const permissions = computed(() => userInfo.value?.permissions ?? [])

  function persistAuth() {
    const payload: PersistedAuth = {
      accessToken: accessToken.value,
      refreshToken: refreshToken.value,
      userInfo: userInfo.value,
    }
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(payload))
  }

  function clearPersistedAuth() {
    localStorage.removeItem(AUTH_STORAGE_KEY)
  }

  function clearAuth() {
    accessToken.value = ''
    refreshToken.value = ''
    userInfo.value = null
    profileSynced.value = false
    clearPersistedAuth()
  }

  async function login(form: LoginRequest) {
    const { data } = await loginApi(form)
    const res = data.data
    accessToken.value = res.accessToken
    refreshToken.value = res.refreshToken
    userInfo.value = res.user
    if (!userInfo.value.permissions) {
      userInfo.value.permissions = []
    }
    profileSynced.value = true
    persistAuth()
  }

  async function refreshAccessToken(): Promise<string> {
    if (!refreshToken.value) {
      throw new Error('refresh token missing')
    }
    const { data } = await refreshTokenApi(refreshToken.value)
    const newToken = data.data.accessToken
    accessToken.value = newToken
    persistAuth()
    return newToken
  }

  function updateAccessToken(token: string) {
    if (!token || token === accessToken.value) {
      return
    }
    accessToken.value = token
    persistAuth()
  }

  async function syncProfile() {
    if (!accessToken.value) {
      return
    }
    const { data } = await meApi()
    const profile = data.data
    userInfo.value = {
      id: profile.id,
      username: profile.username,
      displayName: profile.displayName,
      role: profile.role as RoleType,
      partnerId: profile.partnerId,
      phone: profile.phone,
      email: profile.email,
      avatarUrl: profile.avatarUrl,
      permissions: profile.permissions || [],
    }
    profileSynced.value = true
    persistAuth()
  }

  async function logout() {
    try {
      await logoutApi()
    } catch {
      // ignore backend logout error and clear local state
    } finally {
      clearAuth()
    }
  }

  function hasRole(allowed: RoleType[]): boolean {
    if (!role.value) return false
    if (role.value === 'super_admin') return true
    return allowed.includes(role.value)
  }

  function hasPermission(required: string | string[]): boolean {
    const requiredList = Array.isArray(required) ? required : [required]
    if (requiredList.length === 0) return true
    if (role.value === 'super_admin') return true
    const userPerms = permissions.value
    return requiredList.some((perm) => userPerms.includes(perm))
  }

  return {
    accessToken,
    refreshToken,
    userInfo,
    isLoggedIn,
    role,
    isPartner,
    displayName,
    avatarUrl,
    permissions,
    profileSynced,
    login,
    refreshAccessToken,
    updateAccessToken,
    syncProfile,
    logout,
    clearAuth,
    hasRole,
    hasPermission,
  }
})
