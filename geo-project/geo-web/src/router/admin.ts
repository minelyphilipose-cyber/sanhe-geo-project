import type { RouteRecordRaw } from 'vue-router'

const adminRoutes: RouteRecordRaw = {
  path: '/admin',
  component: () => import('@/layouts/AdminLayout.vue'),
  redirect: '/admin/overview',
  meta: { requiresAuth: true },
  children: [
    /* ---- 工作台 ---- */
    {
      path: 'overview',
      name: 'Overview',
      component: () => import('@/views/admin/overview/OverviewView.vue'),
      meta: { title: '工作台', icon: 'Odometer' },
    },

    /* ---- 客户管理 ---- */
    {
      path: 'customers',
      name: 'CustomerList',
      component: () => import('@/views/admin/customer/CustomerList.vue'),
      meta: { title: '客户管理', icon: 'User' },
    },
    {
      path: 'customers/:id',
      name: 'CustomerDetail',
      component: () => import('@/views/admin/customer/CustomerDetail.vue'),
      meta: { title: '客户详情', hidden: true },
    },

    /* ---- 品牌管理 ---- */
    {
      path: 'brands/:id',
      name: 'BrandDetail',
      component: () => import('@/views/admin/brand/BrandDetail.vue'),
      meta: { title: '品牌详情', hidden: true },
    },
    {
      path: 'brands/:id/profile',
      name: 'BrandProfile',
      component: () => import('@/views/admin/brand/BrandProfile.vue'),
      meta: { title: '品牌画像', hidden: true },
    },
    {
      path: 'brands/:id/assets',
      name: 'BrandAssets',
      component: () => import('@/views/admin/brand/BrandAssets.vue'),
      meta: { title: '品牌资产', hidden: true },
    },

    /* ---- 项目管理 ---- */
    {
      path: 'projects',
      name: 'ProjectList',
      component: () => import('@/views/admin/project/ProjectList.vue'),
      meta: { title: '项目管理', icon: 'Folder' },
    },
    {
      path: 'projects/:id',
      name: 'ProjectDetail',
      component: () => import('@/views/admin/project/ProjectDetail.vue'),
      meta: { title: '项目详情', hidden: true },
    },
    {
      path: 'projects/:id/questions',
      name: 'QuestionPool',
      component: () => import('@/views/admin/project/QuestionPool.vue'),
      meta: { title: '问题池', hidden: true },
    },
    {
      path: 'projects/:id/tasks',
      name: 'TaskList',
      component: () => import('@/views/admin/project/TaskList.vue'),
      meta: { title: '监测任务', hidden: true },
    },
    {
      path: 'projects/:id/reports',
      name: 'ProjectReports',
      component: () => import('@/views/admin/project/ReportList.vue'),
      meta: { title: '项目报表', hidden: true },
    },
    {
      path: 'projects/:id/review',
      name: 'ReviewPanel',
      component: () => import('@/views/admin/project/ReviewPanel.vue'),
      meta: { title: '达标评估', hidden: true },
    },

    /* ---- 监测中心 ---- */
    {
      path: 'monitoring',
      name: 'Monitoring',
      component: () => import('@/views/admin/monitoring/MonitoringDashboard.vue'),
      meta: { title: '监测中心', icon: 'Monitor' },
    },
    {
      path: 'monitoring/platforms',
      name: 'PlatformHealth',
      component: () => import('@/views/admin/monitoring/PlatformHealth.vue'),
      meta: { title: '平台健康', hidden: true },
    },

    /* ---- 报表管理 ---- */
    {
      path: 'reports',
      name: 'ReportManage',
      component: () => import('@/views/admin/report/ReportManage.vue'),
      meta: {
        title: '报表管理',
        icon: 'DataAnalysis',
        roles: ['delivery_manager', 'manager', 'super_admin'],
      },
    },
    {
      path: 'reports/:id',
      name: 'ReportPreview',
      component: () => import('@/views/admin/report/ReportPreview.vue'),
      meta: { title: '报表预览', hidden: true },
    },

    /* ---- 合伙人管理 ---- */
    {
      path: 'partners',
      name: 'PartnerList',
      component: () => import('@/views/admin/partner/PartnerList.vue'),
      meta: {
        title: '合伙人管理',
        icon: 'Coordinate',
        roles: ['manager', 'super_admin'],
      },
    },
    {
      path: 'partners/:id',
      name: 'PartnerDetail',
      component: () => import('@/views/admin/partner/PartnerDetail.vue'),
      meta: { title: '合伙人详情', hidden: true, roles: ['manager', 'super_admin'] },
    },

    /* ---- 异常中心 ---- */
    {
      path: 'alerts',
      name: 'AlertCenter',
      component: () => import('@/views/admin/alert/AlertCenter.vue'),
      meta: { title: '异常中心', icon: 'Bell' },
    },

    /* ---- 系统设置 ---- */
    {
      path: 'settings/platforms',
      name: 'PlatformConfig',
      component: () => import('@/views/admin/settings/PlatformConfig.vue'),
      meta: { title: '平台配置', icon: 'Setting', roles: ['super_admin'] },
    },
    {
      path: 'settings/packages',
      name: 'PackageConfig',
      component: () => import('@/views/admin/settings/PackageConfig.vue'),
      meta: { title: '套餐配置', hidden: true, roles: ['super_admin'] },
    },
    {
      path: 'settings/users',
      name: 'UserManage',
      component: () => import('@/views/admin/settings/UserManage.vue'),
      meta: { title: '用户管理', hidden: true, roles: ['super_admin'] },
    },
  ],
}

export default adminRoutes
