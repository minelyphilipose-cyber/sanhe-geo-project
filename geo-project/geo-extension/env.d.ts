/// <reference types="vite/client" />

declare const __EXTENSION_VERSION__: string

interface ExtensionRuntimeProfile {
  apiBaseUrl: string
  adminOrigins: string[]
  hostPermissions: string[]
}

declare const __EXTENSION_PROFILE__: ExtensionRuntimeProfile

interface ImportMetaEnv {
  readonly VITE_GEO_EXTENSION_PROFILE?: 'test' | 'production'
  readonly VITE_GEO_API_BASE_URL?: string
}
