import type { Directive, DirectiveBinding } from 'vue'
import { useUserStore } from '@/stores/user'
import type { RoleType } from '@/types'

type PermissionBinding =
  | RoleType[]
  | string[]
  | {
      roles?: RoleType[]
      permissions?: string[]
    }

function removeEl(el: HTMLElement) {
  el.parentNode?.removeChild(el)
}

export const vPermission: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding<PermissionBinding>) {
    const userStore = useUserStore()
    const value = binding.value

    if (!value) {
      return
    }

    if (Array.isArray(value)) {
      const first = value[0]
      if (typeof first === 'string' && first.includes('.')) {
        if (!userStore.hasPermission(value as string[])) {
          removeEl(el)
        }
        return
      }
      if (!userStore.hasRole(value as RoleType[])) {
        removeEl(el)
      }
      return
    }

    const rolesOk = !value.roles || value.roles.length === 0 || userStore.hasRole(value.roles)
    const permsOk = !value.permissions || value.permissions.length === 0 || userStore.hasPermission(value.permissions)
    if (!rolesOk || !permsOk) {
      removeEl(el)
    }
  },
}
