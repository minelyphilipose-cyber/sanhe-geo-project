export type ExtensionProfileName = 'test' | 'lan' | 'production'

export interface ExtensionProfile {
  apiBaseUrl: string
  adminOrigins: string[]
  hostPermissions: string[]
}

const PLATFORM_HOST_PERMISSIONS = [
  'https://*.toutiao.com/*',
  'https://*.zhihu.com/*',
  'https://*.xiaohongshu.com/*',
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
  lan: {
    apiBaseUrl: 'http://192.168.3.121:8080',
    adminOrigins: [
      'http://192.168.3.121:3000',
      'http://192.168.3.121:5173',
    ],
    hostPermissions: [
      'http://192.168.3.121:8080/*',
      ...PLATFORM_HOST_PERMISSIONS,
    ],
  },
  production: {
    apiBaseUrl: 'https://www.huanjingaigeo.com',
    adminOrigins: [
      'https://www.huanjingaigeo.com',
      'https://huanjingaigeo.com',
    ],
    hostPermissions: [
      'https://huanjingaigeo.com/*',  
      'https://www.huanjingaigeo.com/*',
      ...PLATFORM_HOST_PERMISSIONS,
    ],
  },
}

export function resolveExtensionProfileName(value: string | undefined): ExtensionProfileName {
  if (value === 'production' || value === 'test' || value === 'lan') return value
  return 'test'
}

export function resolveExtensionProfile(value: string | undefined): ExtensionProfile {
  return EXTENSION_PROFILES[resolveExtensionProfileName(value)]
}
