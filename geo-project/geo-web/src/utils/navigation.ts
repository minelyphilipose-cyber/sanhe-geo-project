import type { RoleType } from '@/types'

interface AccessContext {
  isPartner: boolean
  hasPermission: (required: string | string[]) => boolean
  hasRole: (roles: RoleType[]) => boolean
}

const OPS_ROLES: RoleType[] = ['operator', 'delivery_manager', 'manager', 'super_admin']

export function resolvePostLoginPath(ctx: AccessContext): string | null {
  if (ctx.isPartner) {
    const partnerCandidates: Array<{
      path: string
      roles?: RoleType[]
      permissions?: string[]
    }> = [
      { path: '/partner/staff-workbench', roles: ['partner_staff'] },
      { path: '/partner/home', permissions: ['partner.read'] },
      { path: '/partner/my-customers', permissions: ['company.read'] },
      { path: '/partner/my-projects', permissions: ['project.read'] },
      { path: '/partner/profile' },
    ]

    for (const item of partnerCandidates) {
      const rolePass = !item.roles || item.roles.length === 0 || ctx.hasRole(item.roles)
      const permPass = !item.permissions || item.permissions.length === 0 || ctx.hasPermission(item.permissions)
      if (rolePass && permPass) {
        return item.path
      }
    }

    return null
  }

  const candidates: Array<{
    path: string
    roles?: RoleType[]
    permissions?: string[]
  }> = [
    { path: '/admin/overview' },
    { path: '/admin/customers', permissions: ['company.read'] },
    { path: '/admin/projects', permissions: ['project.read'] },
    { path: '/admin/content/execution', permissions: ['content.read'] },
    { path: '/admin/presale/report', permissions: ['presale.report.list'] },
    { path: '/admin/monitoring/tasks', roles: OPS_ROLES },
    { path: '/admin/alerts', roles: OPS_ROLES },
    { path: '/admin/partners', permissions: ['partner.read'] },
    { path: '/admin/settings/platforms', permissions: ['user.manage'] },
  ]

  for (const item of candidates) {
    const rolePass = !item.roles || item.roles.length === 0 || ctx.hasRole(item.roles)
    const permPass = !item.permissions || item.permissions.length === 0 || ctx.hasPermission(item.permissions)
    if (rolePass && permPass) {
      return item.path
    }
  }

  return null
}
