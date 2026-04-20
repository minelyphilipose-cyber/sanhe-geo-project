import { createRouter, createWebHistory } from 'vue-router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { useUserStore } from '@/stores/user'
import adminRoutes from './admin'
import partnerRoutes from './partner'
import type { RoleType } from '@/types'
import { resolvePostLoginPath } from '@/utils/navigation'

NProgress.configure({ showSpinner: false })

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/login/LoginView.vue'),
      meta: { title: '登录' },
    },
    {
      path: '/403',
      name: 'Forbidden',
      component: () => import('@/views/system/ForbiddenView.vue'),
      meta: { title: '无权限' },
    },
    {
      path: '/session-expired',
      name: 'SessionExpired',
      component: () => import('@/views/system/SessionExpiredView.vue'),
      meta: { title: '登录已过期' },
    },
    {
      path: '/r/:token',
      name: 'ShareReport',
      component: () => import('@/views/share/ShareReport.vue'),
      meta: { title: '报表查看' },
    },
    {
      path: '/dashboard/:shareCode',
      name: 'ProjectDashboard',
      component: () => import('@/views/share/ProjectDashboard.vue'),
      meta: { title: '项目统计看板' },
    },
    adminRoutes,
    partnerRoutes,
    {
      path: '/',
      redirect: '/admin/overview',
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'NotFound',
      component: () => import('@/views/login/LoginView.vue'),
      meta: { title: '页面未找到' },
    },
  ],
})

const PUBLIC_PATHS = ['/login', '/r/', '/dashboard/', '/403', '/session-expired']

function isPublicPath(path: string): boolean {
  return PUBLIC_PATHS.some((p) => path.startsWith(p))
}

router.beforeEach(async (to, _from, next) => {
  NProgress.start()
  document.title = `${to.meta?.title ?? ''} | 幻境AI GEO`

  const userStore = useUserStore()

  if (isPublicPath(to.path)) {
    if (to.path === '/login' && userStore.isLoggedIn) {
      try {
        await userStore.syncProfile()
      } catch {
        await userStore.logout()
        return next()
      }
      const target = resolvePostLoginPath({
        isPartner: userStore.isPartner,
        hasPermission: userStore.hasPermission,
        hasRole: userStore.hasRole,
      })
      if (target && target !== '/login') {
        return next(target)
      }
    }
    return next()
  }

  if (!userStore.isLoggedIn) {
    return next(`/login?redirect=${encodeURIComponent(to.fullPath)}`)
  }

  if (!userStore.permissions.length) {
    try {
      await userStore.syncProfile()
    } catch {
      await userStore.logout()
      localStorage.removeItem('geo_auth_v1')
      return next(`/session-expired?redirect=${encodeURIComponent(to.fullPath)}`)
    }
  }

  const requiredRoles = to.meta?.roles as RoleType[] | undefined
  if (requiredRoles && requiredRoles.length > 0 && !userStore.hasRole(requiredRoles)) {
    return next('/403')
  }

  const requiredPerms = (to.meta?.permissions as string[] | undefined) || []
  if (requiredPerms.length > 0 && !userStore.hasPermission(requiredPerms)) {
    return next('/403')
  }

  if (userStore.isPartner && to.path.startsWith('/admin')) {
    return next('/partner/home')
  }
  if (!userStore.isPartner && to.path.startsWith('/partner')) {
    return next('/admin/overview')
  }

  next()
})

router.afterEach(() => {
  NProgress.done()
})

export default router
