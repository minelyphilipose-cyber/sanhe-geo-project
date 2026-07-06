import type { RouteRecordRaw } from 'vue-router'

const partnerRoutes: RouteRecordRaw = {
  path: '/partner',
  component: () => import('@/layouts/PartnerLayout.vue'),
  redirect: '/partner/home',
  meta: {
    requiresAuth: true,
    roles: ['partner', 'partner_staff'],
  },
  children: [
    {
      path: 'home',
      name: 'PartnerHome',
      component: () => import('@/views/partner/PartnerHome.vue'),
      meta: { title: '合伙人首页', icon: 'HomeFilled', roles: ['partner'], permissions: ['partner.read'] },
    },
    {
      path: 'staff-workbench',
      name: 'PartnerStaffWorkbench',
      component: () => import('@/views/partner/PartnerStaffWorkbench.vue'),
      meta: { title: '工作台', icon: 'Odometer', roles: ['partner_staff'] },
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
      meta: { title: '客户管理', icon: 'User', roles: ['partner', 'partner_staff'], permissions: ['company.read'] },
    },
    {
      path: 'customers/:id',
      name: 'PartnerCustomerDetail',
      component: () => import('@/views/partner/PartnerCustomerDetail.vue'),
      meta: { title: '客户详情', hidden: true, roles: ['partner', 'partner_staff'], permissions: ['company.read'] },
    },
    {
      path: 'brands/create',
      name: 'PartnerBrandCreate',
      component: () => import('@/views/partner/PartnerBrandCreate.vue'),
      meta: { title: '新建品牌', hidden: true, roles: ['partner_staff'], permissions: ['brand.create'] },
    },
    {
      path: 'brands/:id',
      name: 'PartnerBrandDetail',
      component: () => import('@/views/partner/PartnerBrandDetail.vue'),
      meta: {
        title: '品牌详情',
        hidden: true,
        roles: ['partner', 'partner_staff', 'operator', 'delivery_manager', 'manager', 'super_admin'],
        permissions: ['company.read', 'delivery.assignment.manage'],
      },
    },
    {
      path: 'staff',
      name: 'PartnerStaffManage',
      component: () => import('@/views/partner/PartnerStaffManage.vue'),
      meta: { title: '交付员工', icon: 'UserFilled', roles: ['partner'], permissions: ['partner.staff.manage'] },
    },
    {
      path: 'my-projects',
      name: 'MyProjects',
      component: () => import('@/views/partner/MyProjects.vue'),
      meta: { title: '项目管理', icon: 'Folder', roles: ['partner', 'partner_staff'], permissions: ['project.read'] },
    },
    {
      path: 'projects/:id',
      name: 'PartnerProjectDetail',
      component: () => import('@/views/partner/PartnerProjectDetail.vue'),
      meta: { title: '项目详情', hidden: true, roles: ['partner', 'partner_staff'], permissions: ['project.read'] },
    },
    {
      path: 'layered-keyword-groups',
      name: 'PartnerLayeredKeywordGroupManage',
      component: () => import('@/views/partner/PartnerKeywordManage.vue'),
      meta: {
        title: '拓词管理',
        icon: 'Collection',
        roles: ['partner', 'partner_staff'],
        permissions: ['keyword_group.read'],
      },
    },
    {
      path: 'alerts',
      name: 'PartnerAlertCenter',
      component: () => import('@/views/admin/alert/AlertCenter.vue'),
      meta: {
        title: '站内信',
        icon: 'Bell',
        roles: ['partner', 'partner_staff'],
      },
    },
    {
      path: 'balance',
      name: 'PartnerBalance',
      component: () => import('@/views/partner/BalanceView.vue'),
      meta: { title: '余额与扣款', icon: 'Wallet', roles: ['partner'], permissions: ['partner.read'] },
    },
    {
      path: 'presale/report',
      name: 'PartnerPresaleReportList',
      component: () => import('@/views/admin/presale/report/PresaleReportList.vue'),
      meta: {
        title: 'AI可见度诊断报告',
        icon: 'Document',
        roles: ['partner'],
        permissions: ['presale.report.list'],
      },
    },
    {
      path: 'presale/report/create',
      name: 'PartnerPresaleReportCreate',
      component: () => import('@/views/admin/presale/report/PresaleReportCreate.vue'),
      meta: {
        title: '新建报告',
        hidden: true,
        roles: ['partner'],
        permissions: ['presale.report.create'],
      },
    },
    {
      path: 'presale/report/:id/progress',
      name: 'PartnerPresaleReportProgress',
      component: () => import('@/views/admin/presale/report/PresaleReportProgress.vue'),
      meta: {
        title: '生成进度',
        hidden: true,
        roles: ['partner'],
        permissions: ['presale.report.view'],
      },
    },
    {
      path: 'presale/report/:id/detail',
      name: 'PartnerPresaleReportDetail',
      component: () => import('@/views/admin/presale/report/PresaleReportDetail.vue'),
      meta: {
        title: '报告详情',
        hidden: true,
        roles: ['partner'],
        permissions: ['presale.report.view'],
      },
    },
    {
      path: 'presale/report/:id/edit',
      name: 'PartnerPresaleReportEdit',
      component: () => import('@/views/admin/presale/report/PresaleReportEdit.vue'),
      meta: {
        title: '编辑AI可见度诊断报告',
        hidden: true,
        roles: ['partner'],
        permissions: ['presale.report.view'],
      },
    },
    {
      path: 'presale/report/:id/versions/:versionNo/prompts',
      name: 'PartnerPresaleReportPromptList',
      component: () => import('@/views/admin/presale/report/PresaleReportPromptList.vue'),
      meta: {
        title: 'Prompt 调用记录',
        hidden: true,
        roles: ['partner'],
        permissions: ['presale.report.view'],
      },
    },
    {
      path: 'presale/report/:id/versions/:versionNo/prompts/:promptResultId',
      name: 'PartnerPresaleReportPromptDetail',
      component: () => import('@/views/admin/presale/report/PresaleReportPromptDetail.vue'),
      meta: {
        title: 'Prompt 调用详情',
        hidden: true,
        roles: ['partner'],
        permissions: ['presale.report.view'],
      },
    },
  ],
}

export default partnerRoutes
