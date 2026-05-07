export type ExtensionStatus = 'unbound' | 'bound'

export interface StoredSession {
  token: string
  operatorId?: number
  extensionVersion: string
  boundAt: string
}

export interface ApiErrorBody {
  code?: number
  message?: string
}

export interface VersionCheckResponse {
  supported: boolean
  minVersion: string
  latestVersion: string
  recommendedVersion?: string
  downloadUrl?: string
  warning?: string
}

export interface BindResponse {
  extensionToken: string
  operatorId: number
  expiresAt: string
  extensionVersion: string
}

export interface ExtensionMessage<T = unknown> {
  type: string
  payload?: T
}
