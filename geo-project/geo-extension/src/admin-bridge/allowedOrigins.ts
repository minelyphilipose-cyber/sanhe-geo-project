const DEV_ADMIN_ORIGINS = [
  'http://localhost:3000',
  'http://127.0.0.1:3000',
  'http://localhost:5173',
  'http://127.0.0.1:5173',
]

const PROD_ADMIN_ORIGINS = [
  import.meta.env.VITE_GEO_ADMIN_ORIGIN,
].filter(Boolean)

export function isAllowedAdminOrigin(origin: string): boolean {
  const allowed = import.meta.env.PROD && PROD_ADMIN_ORIGINS.length > 0
    ? PROD_ADMIN_ORIGINS
    : DEV_ADMIN_ORIGINS
  return allowed.includes(origin)
}
