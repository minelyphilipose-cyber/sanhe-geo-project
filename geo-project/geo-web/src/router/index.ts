import { createRouter, createWebHistory } from 'vue-router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { useUserStore } from '@/stores/user'
import adminRoutes from './admin'
import partnerRoutes from './partner'
import type { RoleType } from '@/types'

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
      path: '/r/:token',
      name: 'ShareReport',
      component: () => import('@/views/share/ShareReport.vue'),
      meta: { title: '报表查看' },
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

const PUBLIC_PATHS = ['/login', '/r/']

function isPublicPath(path: string): boolean {
  return PUBLIC_PATHS.some((p) => path.startsWith(p))
}

router.beforeEach((to, _from, next) => {
  NProgress.start()
  document.title = `${to.meta?.title ?? ''} | 幻境AI GEO`

  const userStore = useUserStore()

  if (isPublicPath(to.path)) {
    if (to.path === '/login' && userStore.isLoggedIn) {
      const target = userStore.isPartner ? '/partner/home' : '/admin/overview'
      return next(target)
    }
    return next()
  }

  if (!userStore.isLoggedIn) {
    return next(`/login?redirect=${encodeURIComponent(to.fullPath)}`)
  }

  const requiredRoles = to.meta?.roles as RoleType[] | undefined
  if (requiredRoles && requiredRoles.length > 0) {
    if (!userStore.hasRole(requiredRoles)) {
      const home = userStore.isPartner ? '/partner/home' : '/admin/overview'
      return next(home)
    }
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

