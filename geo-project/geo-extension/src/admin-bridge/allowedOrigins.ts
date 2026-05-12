const EXTENSION_PROFILE = __EXTENSION_PROFILE__
const ALLOWED_ADMIN_ORIGINS = EXTENSION_PROFILE.adminOrigins.map(origin => origin.replace(/\/$/, ''))

export function isAllowedAdminOrigin(origin: string): boolean {
  return ALLOWED_ADMIN_ORIGINS.includes(origin)
}
