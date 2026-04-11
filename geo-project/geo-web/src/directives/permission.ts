import type { Directive, DirectiveBinding } from 'vue'
import { useUserStore } from '@/stores/user'
import type { RoleType } from '@/types'

/**
 * 权限指令
 * 用法: v-permission="['manager', 'super_admin']"
 * 无权限时移除 DOM 元素
 */
export const vPermission: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding<RoleType[]>) {
    const userStore = useUserStore()
    const roles = binding.value
    if (roles && roles.length > 0 && !userStore.hasRole(roles)) {
      el.parentNode?.removeChild(el)
    }
  },
}
