export type ExtensionProfileName = 'test' | 'production'

export interface ExtensionProfile {
  apiBaseUrl: string
  adminOrigins: string[]
  hostPermissions: string[]
}

const PLATFORM_HOST_PERMISSIONS = [
  'https://*.toutiao.com/*',
  'https://*.zhihu.com/*',
]

export const EXTENSION_PROFILES: Record<ExtensionProfileName, ExtensionProfile> = {
  test: {
    apiBaseUrl: 'http://127.0.0.1:8080',
    adminOrigins: [
      'http://localhost:3000',
      'http://127.0.0.1:3000',
      'http://localhost:5173',
      'http://127.0.0.1:5173',
    ],
    hostPermissions: [
      'http://127.0.0.1:8080/*',
      'http://localhost:8080/*',
      ...PLATFORM_HOST_PERMISSIONS,
    ],
  },
  production: {
    apiBaseUrl: 'http://119.45.154.127',
    adminOrigins: [
      'http://119.45.154.127',
    ],
    hostPermissions: [
      'http://119.45.154.127/*',
      ...PLATFORM_HOST_PERMISSIONS,
    ],
  },
}

export function resolveExtensionProfileName(value: string | undefined): ExtensionProfileName {
  if (value === 'production' || value === 'test') return value
  return 'test'
}

export function resolveExtensionProfile(value: string | undefined): ExtensionProfile {
  return EXTENSION_PROFILES[resolveExtensionProfileName(value)]
}
