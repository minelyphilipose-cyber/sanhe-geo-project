import type { RouteRecordRaw } from 'vue-router'

const partnerRoutes: RouteRecordRaw = {
  path: '/partner',
  component: () => import('@/layouts/PartnerLayout.vue'),
  redirect: '/partner/home',
  meta: {
    requiresAuth: true,
    roles: ['partner', 'partner_staff', 'partner_viewer'],
  },
  children: [
    {
      path: 'home',
      name: 'PartnerHome',
      component: () => import('@/views/partner/PartnerHome.vue'),
      meta: { title: '合伙人首页', icon: 'HomeFilled', permissions: ['partner.read'] },
    },
    {
      path: 'profile',
      name: 'PartnerProfile',
      component: () => import('@/views/profile/ProfileCenter.vue'),
      meta: { title: '个人中心', hidden: true, requiresAuth: true },
    },
    {
      path: 'my-customers',
      name: 'MyCustomers',
      component: () => import('@/views/partner/MyCustomers.vue'),
      meta: { title: '我的客户', icon: 'User', permissions: ['company.read'] },
    },
    {
      path: 'my-projects',
      name: 'MyProjects',
      component: () => import('@/views/partner/MyProjects.vue'),
      meta: { title: '我的项目', icon: 'Folder', permissions: ['project.read'] },
    },
    {
      path: 'balance',
      name: 'PartnerBalance',
      component: () => import('@/views/partner/BalanceView.vue'),
      meta: { title: '余额与扣款', icon: 'Wallet', permissions: ['partner.read'] },
    },
    {
      path: 'training',
      name: 'TrainingCenter',
      component: () => import('@/views/partner/TrainingCenter.vue'),
      meta: { title: '培训中心', icon: 'Reading', permissions: ['partner.read'] },
    },
  ],
}

export default partnerRoutes
