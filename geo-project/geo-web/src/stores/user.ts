import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { loginApi, refreshTokenApi, logoutApi } from '@/api/auth'
import { isPartnerRole } from '@/utils/constants'
import type { UserInfo, RoleType, LoginRequest } from '@/types'

export const useUserStore = defineStore('user', () => {
  /* ---- state ---- */
  const accessToken = ref<string>('')
  const userInfo = ref<UserInfo | null>(null)

  /* ---- getters ---- */
  const isLoggedIn = computed(() => !!accessToken.value && !!userInfo.value)
  const role = computed<RoleType | null>(() => userInfo.value?.role ?? null)
  const isPartner = computed(() => role.value ? isPartnerRole(role.value) : false)
  const displayName = computed(() => userInfo.value?.displayName ?? '')

  /* ---- actions ---- */
  async function login(form: LoginRequest) {
    const { data } = await loginApi(form)
    const res = data.data
    accessToken.value = res.accessToken
    userInfo.value = res.user
    // refreshToken 由后端写入 httpOnly cookie，前端不存储
  }

  async function refreshAccessToken(): Promise<string> {
    const { data } = await refreshTokenApi()
    const newToken = data.data.accessToken
    accessToken.value = newToken
    return newToken
  }

  async function logout() {
    try {
      await logoutApi()
    } catch {
      // 即使后端失败也清理本地状态
    }
    accessToken.value = ''
    userInfo.value = null
  }

  function hasRole(allowed: RoleType[]): boolean {
    if (!role.value) return false
    if (role.value === 'super_admin') return true
    return allowed.includes(role.value)
  }

  return {
    accessToken,
    userInfo,
    isLoggedIn,
    role,
    isPartner,
    displayName,
    login,
    refreshAccessToken,
    logout,
    hasRole,
  }
})
