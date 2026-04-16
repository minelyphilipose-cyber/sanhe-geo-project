import type { RouteRecordRaw } from 'vue-router'

const adminRoutes: RouteRecordRaw = {
  path: '/admin',
  component: () => import('@/layouts/AdminLayout.vue'),
  redirect: '/admin/overview',
  meta: { requiresAuth: true },
  children: [
    {
      path: 'overview',
      name: 'Overview',
      component: () => import('@/views/admin/overview/OverviewView.vue'),
      meta: { title: '工作台', icon: 'Odometer', permissions: ['company.read'] },
    },
    {
      path: 'customers',
      name: 'CustomerList',
      component: () => import('@/views/admin/customer/CustomerList.vue'),
      meta: { title: '客户管理', icon: 'User', permissions: ['company.read'] },
    },
    {
      path: 'customers/:id',
      name: 'CustomerDetail',
      component: () => import('@/views/admin/customer/CustomerDetail.vue'),
      meta: { title: '客户详情', hidden: true, permissions: ['company.read'] },
    },
    {
      path: 'brands/create',
      name: 'BrandCreate',
      component: () => import('@/views/admin/brand/BrandCreate.vue'),
      meta: { title: '新建品牌', hidden: true, permissions: ['company.write'] },
    },
    {
      path: 'brands/:id',
      name: 'BrandDetail',
      component: () => import('@/views/admin/brand/BrandDetail.vue'),
      meta: { title: '品牌详情', hidden: true, permissions: ['company.read'] },
    },
    {
      path: 'brands/:id/profile',
      name: 'BrandProfile',
      component: () => import('@/views/admin/brand/BrandProfile.vue'),
      meta: { title: '品牌画像', hidden: true, permissions: ['company.read'] },
    },
    {
      path: 'brands/:id/assets',
      name: 'BrandAssets',
      component: () => import('@/views/admin/brand/BrandAssets.vue'),
      meta: { title: '品牌资产', hidden: true, permissions: ['company.read'] },
    },
    {
      path: 'projects',
      name: 'ProjectList',
      component: () => import('@/views/admin/project/ProjectList.vue'),
      meta: { title: '项目管理', icon: 'Folder', permissions: ['project.read'] },
    },
    {
      path: 'projects/:id',
      name: 'ProjectDetail',
      component: () => import('@/views/admin/project/ProjectDetail.vue'),
      meta: { title: '项目详情', hidden: true, permissions: ['project.read'] },
    },
    {
      path: 'projects/:id/presale',
      name: 'PresaleDiagnosis',
      component: () => import('@/views/admin/project/PresaleDiagnosis.vue'),
      meta: { title: '售前诊断', hidden: true, permissions: ['project.read'] },
    },
    {
      path: 'projects/:id/tasks',
      name: 'TaskList',
      component: () => import('@/views/admin/project/TaskList.vue'),
      meta: { title: '监测任务', hidden: true, roles: ['operator', 'delivery_manager', 'manager', 'super_admin'] },
    },
    {
      path: 'projects/:id/reports',
      name: 'ProjectReports',
      component: () => import('@/views/admin/project/ReportList.vue'),
      meta: { title: '项目报表', hidden: true, permissions: ['project.read'] },
    },
    {
      path: 'projects/:id/review',
      name: 'ReviewPanel',
      component: () => import('@/views/admin/project/ReviewPanel.vue'),
      meta: { title: '达标评估', hidden: true, permissions: ['project.read'] },
    },
    {
      path: 'monitoring',
      redirect: '/admin/monitoring/tasks',
      meta: { hidden: true, roles: ['operator', 'delivery_manager', 'manager', 'super_admin'] },
    },
    {
      path: 'monitoring/tasks',
      name: 'MonitoringTasks',
      component: () => import('@/views/admin/monitoring/MonitoringDashboard.vue'),
      meta: { title: '调度监控', icon: 'Monitor', roles: ['operator', 'delivery_manager', 'manager', 'super_admin'] },
    },
    {
      path: 'content/execution',
      name: 'ContentExecution',
      component: () => import('@/views/admin/content/ContentExecution.vue'),
      meta: { title: '内容与执行', icon: 'Memo', permissions: ['project.read'] },
    },
    {
      path: 'keyword-groups',
      name: 'KeywordGroupManage',
      component: () => import('@/views/admin/project/KeywordGroupManage.vue'),
      meta: { title: '拓词管理', icon: 'MagicStick', permissions: ['keyword_group.read'] },
    },
    {
      path: 'monitoring/platforms',
      name: 'PlatformHealth',
      component: () => import('@/views/admin/monitoring/PlatformHealth.vue'),
      meta: { title: '平台健康', roles: ['operator', 'delivery_manager', 'manager', 'super_admin'] },
    },
    {
      path: 'reports',
      name: 'ReportManage',
      component: () => import('@/views/admin/report/ReportManage.vue'),
      meta: {
        title: '报表管理',
        icon: 'DataAnalysis',
        permissions: ['report.review'],
      },
    },
    {
      path: 'reports/:id',
      name: 'ReportPreview',
      component: () => import('@/views/admin/report/ReportPreview.vue'),
      meta: { title: '报表预览', hidden: true, permissions: ['report.review'] },
    },
    {
      path: 'partners',
      name: 'PartnerList',
      component: () => import('@/views/admin/partner/PartnerList.vue'),
      meta: {
        title: '合伙人管理',
        icon: 'Coordinate',
        permissions: ['partner.read'],
      },
    },
    {
      path: 'partners/:id',
      name: 'PartnerDetail',
      component: () => import('@/views/admin/partner/PartnerDetail.vue'),
      meta: { title: '合伙人详情', hidden: true, permissions: ['partner.read'] },
    },
    {
      path: 'alerts',
      name: 'AlertCenter',
      component: () => import('@/views/admin/alert/AlertCenter.vue'),
      meta: { title: '告警中心', icon: 'Bell', roles: ['operator', 'delivery_manager', 'manager', 'super_admin'] },
    },
    {
      path: 'activity-logs',
      name: 'ActivityLogs',
      component: () => import('@/views/admin/system/ActivityLogs.vue'),
      meta: { title: '操作日志', icon: 'Document', permissions: ['user.manage'] },
    },
    {
      path: 'settings/platforms',
      name: 'PlatformConfig',
      component: () => import('@/views/admin/settings/PlatformConfig.vue'),
      meta: { title: 'AI平台配置', icon: 'Setting', permissions: ['user.manage'] },
    },
    {
      path: 'settings/publish-sites',
      name: 'PublishSiteConfig',
      component: () => import('@/views/admin/settings/PublishSiteConfig.vue'),
      meta: { title: '发布站点配置', permissions: ['user.manage'] },
    },
    {
      path: 'settings/packages',
      name: 'PackageConfig',
      component: () => import('@/views/admin/settings/PackageConfig.vue'),
      meta: { title: '套餐配置', permissions: ['user.manage'] },
    },
    {
      path: 'settings/dicts',
      name: 'DictCenter',
      component: () => import('@/views/admin/settings/DictCenter.vue'),
      meta: { title: '字典中心', permissions: ['user.manage'] },
    },
    {
      path: 'settings/affix-words',
      name: 'KeywordAffixWordManage',
      component: () => import('@/views/admin/settings/KeywordAffixWordManage.vue'),
      meta: { title: '拓词信息维护', permissions: ['keyword_affix.manage'] },
    },
    {
      path: 'settings/users',
      name: 'UserManage',
      component: () => import('@/views/admin/settings/UserManage.vue'),
      meta: { title: '用户与权限', hidden: true, permissions: ['user.manage'] },
    },
  ],
}

export default adminRoutes
