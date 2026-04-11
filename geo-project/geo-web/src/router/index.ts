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
    /* ---- 公开页面 (无需登录) ---- */
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

    /* ---- 内部后台 ---- */
    adminRoutes,

    /* ---- 合伙人后台 ---- */
    partnerRoutes,

    /* ---- 根路径重定向 ---- */
    {
      path: '/',
      redirect: '/admin/overview',
    },

    /* ---- 404 ---- */
    {
      path: '/:pathMatch(.*)*',
      name: 'NotFound',
      component: () => import('@/views/login/LoginView.vue'), // 暂用 login 页，后续替换 404 页
      meta: { title: '页面未找到' },
    },
  ],
})

/* ====================================================
   全局路由守卫
   ==================================================== */
// 不需要登录的白名单路径
const PUBLIC_PATHS = ['/login', '/r/']

function isPublicPath(path: string): boolean {
  return PUBLIC_PATHS.some((p) => path.startsWith(p))
}

router.beforeEach((to, _from, next) => {
  NProgress.start()
  document.title = `${to.meta?.title ?? ''} · 幻境AI GEO`

  const userStore = useUserStore()

  // 1. 公开页面直接放行
  if (isPublicPath(to.path)) {
    // 已登录访问 login → 重定向到首页
    if (to.path === '/login' && userStore.isLoggedIn) {
      const target = userStore.isPartner ? '/partner/home' : '/admin/overview'
      return next(target)
    }
    return next()
  }

  // 2. 未登录 → 跳登录页
  if (!userStore.isLoggedIn) {
    return next(`/login?redirect=${encodeURIComponent(to.fullPath)}`)
  }

  // 3. 路由级别角色校验
  const requiredRoles = to.meta?.roles as RoleType[] | undefined
  if (requiredRoles && requiredRoles.length > 0) {
    if (!userStore.hasRole(requiredRoles)) {
      // 无权限 → 回首页
      const home = userStore.isPartner ? '/partner/home' : '/admin/overview'
      return next(home)
    }
  }

  // 4. 合伙人不能访问 /admin，内部人员不能访问 /partner
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
