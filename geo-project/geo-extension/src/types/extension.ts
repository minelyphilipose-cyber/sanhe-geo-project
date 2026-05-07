export type ExtensionStatus = 'unbound' | 'bound'

export interface StoredSession {
  token: string
  sessionId: number
  operatorId?: number
  extensionVersion: string
  expiresAt: string
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
  token: string
  expiresAt: string
  sessionId: number
}

export interface TokenRefreshResponse {
  token?: string | null
  renewed: boolean
  expiresAt: string
  sessionId: number
}

export type ExtensionTaskStatus = 'token_issued' | 'filling' | 'filled'

export interface ExtensionTaskListItem {
  taskId: number
  platform: string
  status: ExtensionTaskStatus
  publishUrl?: string | null
  title?: string | null
  createdAt: string
  fillTokenIssuedAt: string
  expiresAt: string
}

export interface ExtensionMessage<T = unknown> {
  type: string
  payload?: T
}
