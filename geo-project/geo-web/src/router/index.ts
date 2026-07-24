import { createRouter, createWebHistory } from 'vue-router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import adminRoutes from './admin'
import partnerRoutes from './partner'
import type { RoleType } from '@/types'
import { resolvePostLoginPath } from '@/utils/navigation'

NProgress.configure({ showSpinner: false })

const AUTH_STORAGE_KEY = 'geo_auth_v1'
const SESSION_EXPIRED_MESSAGE = '登录信息已超时，请重新登录'

const enablePresalePrintPoc = import.meta.env.DEV && import.meta.env.VITE_ENABLE_PRESALE_POC !== 'false'
const presalePrintPocRoutes = enablePresalePrintPoc
  ? [
      {
        path: '/presale-print-poc/:reportId',
        name: 'PresalePrintPoc',
        component: () => import('@/views/admin/presale/report/PresalePrintPoc.vue'),
        meta: { title: '售前报表打印 PoC' },
      },
    ]
  : []
const mobileDashboardRoutes = [
  {
    path: '/m/:shareCode',
    component: () => import('@/views/mobile-dashboard/MobileDashboardLayout.vue'),
    children: [
      {
        path: '',
        name: 'MobileDashboardHome',
        component: () => import('@/views/mobile-dashboard/HomeView.vue'),
        meta: { title: '移动数据看板' },
      },
      {
        path: 'monitor',
        name: 'MobileDashboardMonitor',
        component: () => import('@/views/mobile-dashboard/MonitorView.vue'),
        meta: { title: '移动数据看板' },
      },
      {
        path: 'monitor/question/:pollResultId',
        name: 'MobileDashboardQuestionDetail',
        component: () => import('@/views/mobile-dashboard/QuestionDetailView.vue'),
        meta: { title: '移动数据看板' },
      },
      {
        path: 'content',
        name: 'MobileDashboardContent',
        component: () => import('@/views/mobile-dashboard/ContentView.vue'),
        meta: { title: '移动数据看板' },
      },
    ],
  },
]
const publicPathPrefixes = [
  '/login',
  '/r/',
  '/realtime-dashboard/',
  '/dashboard/',
  '/m/',
  '/wechat/mp/',
  '/presale-print/',
  '/baseline-print/',
  ...(enablePresalePrintPoc ? ['/presale-print-poc/'] : []),
  '/403',
  '/session-expired',
]
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
      path: '/realtime-dashboard/:shareCode',
      name: 'RealtimeProjectDashboard',
      component: () => import('@/views/admin/project/ReportList.vue'),
      meta: { title: '项目实时数据看板' },
    },
    {
      path: '/dashboard/:shareCode',
      name: 'ProjectDashboard',
      component: () => import('@/views/share/ProjectDashboard.vue'),
      meta: { title: '项目统计看板' },
    },
    {
      path: '/wechat/mp/:publicSlug/articles',
      name: 'WechatMpArticles',
      component: () => import('@/views/share/WechatMpArticles.vue'),
      meta: { title: '往期文章' },
    },
    ...mobileDashboardRoutes,
    {
      path: '/presale-print/:renderToken',
      name: 'PresalePrint',
      component: () => import('@/views/admin/presale/report/PresalePrint.vue'),
      meta: { title: '售前报表打印' },
    },
    {
      path: '/baseline-print/:renderToken',
      name: 'BaselinePrint',
      component: () => import('@/views/admin/project/BaselineReportPoll.vue'),
      meta: { title: '基线报告打印' },
    },
    ...presalePrintPocRoutes,
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

function isPublicPath(path: string): boolean {
  return publicPathPrefixes.some((p) => path.startsWith(p))
}

router.beforeEach(async (to, _from, next) => {
  NProgress.start()
  const pageTitle = String(to.meta?.title ?? '').trim()
  document.title = to.path.startsWith('/m/')
    ? pageTitle
    : `${pageTitle} | 幻境AI GEO`

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

  if (!userStore.profileSynced || !userStore.permissions.length) {
    try {
      await userStore.syncProfile()
    } catch {
      userStore.clearAuth()
      localStorage.removeItem(AUTH_STORAGE_KEY)
      ElMessage.info(SESSION_EXPIRED_MESSAGE)
      return next(`/login?redirect=${encodeURIComponent(to.fullPath)}`)
    }
  }

  if (userStore.isPartner) {
    const partnerEntry = resolvePostLoginPath({
      isPartner: userStore.isPartner,
      hasPermission: userStore.hasPermission,
      hasRole: userStore.hasRole,
    })

    if (to.path === '/partner' || (to.path === '/partner/home' && !userStore.hasRole(['partner']))) {
      return next(partnerEntry && partnerEntry !== to.fullPath ? partnerEntry : '/403')
    }

    if (to.path.startsWith('/admin/presale/report')) {
      return next({
        path: to.path.replace(/^\/admin\/presale\/report/, '/partner/presale/report'),
        query: to.query,
        hash: to.hash,
      })
    }

    if (to.path.startsWith('/admin')) {
      return next(partnerEntry && partnerEntry !== to.fullPath ? partnerEntry : '/403')
    }
  }

  if (!userStore.isPartner && to.name === 'PartnerBrandDetail') {
    return next({
      name: 'PartnerSubmittedBrandDetail',
      params: to.params,
      query: to.query,
      hash: to.hash,
      replace: true,
    })
  }

  if (!userStore.isPartner && to.path.startsWith('/partner')) {
    return next('/admin/overview')
  }

  const requiredRoles = to.meta?.roles as RoleType[] | undefined
  if (requiredRoles && requiredRoles.length > 0 && !userStore.hasRole(requiredRoles)) {
    return next('/403')
  }

  if (userStore.isPartner && to.path === '/partner/home' && !userStore.hasPermission('partner.read')) {
    const target = resolvePostLoginPath({
      isPartner: userStore.isPartner,
      hasPermission: userStore.hasPermission,
      hasRole: userStore.hasRole,
    })
    return next(target && target !== to.fullPath ? target : '/403')
  }

  const requiredPerms = (to.meta?.permissions as string[] | undefined) || []
  if (userStore.isSales && (to.path.startsWith('/admin/customers') || to.path.startsWith('/admin/projects'))) {
    return next()
  }
  if (requiredPerms.length > 0 && !userStore.hasPermission(requiredPerms)) {
    return next('/403')
  }

  next()
})

router.afterEach(() => {
  NProgress.done()
})

export default router
