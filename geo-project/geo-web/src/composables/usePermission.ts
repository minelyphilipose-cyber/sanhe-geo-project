import { computed } from 'vue'
import { useUserStore } from '@/stores/user'
import type { RoleType } from '@/types'

/**
 * 权限组合式函数
 * 用法: const { can } = usePermission()
 *       if (can(['manager', 'super_admin'])) { ... }
 */
export function usePermission() {
  const userStore = useUserStore()

  const can = (roles: RoleType[]) => userStore.hasRole(roles)

  const isAdmin = computed(() =>
    userStore.hasRole(['super_admin', 'manager'])
  )

  const isOperator = computed(() =>
    userStore.hasRole(['operator', 'delivery_manager', 'manager', 'super_admin'])
  )

  return { can, isAdmin, isOperator }
}
