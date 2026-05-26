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

export type ExtensionTaskStatus = 'token_issued' | 'filling' | 'filled' | 'published' | 'failed'

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

export interface FillTokenIssueResponse { fillToken: string, expiresAt: number, nonce: string }

export interface FillTokenConsumeResponse {
  taskTargetId: number
  expiresAt: number
  nonce: string
  platform?: string
  fillPayload: string
}

export interface ExtensionTaskStateResponse { taskId: number, status: ExtensionTaskStatus }

export type PublishCompletionAction = 'publish_clicked' | 'draft_saved_clicked' | 'success_feedback'

export interface PublishTaskReport {
  action: PublishCompletionAction
  href?: string
  platform?: string
  detectedText?: string
}

export interface SemiAutoFillPayload {
  platform: string
  publishUrl: string
  title?: string | null
  renderedHtml?: string | null
  coverImageUrl?: string | null
  tags?: string[] | null
  category?: string | null
}

export interface FillCommandPayload {
  taskId: number
  platform: string
  publishUrl: string
  title: string
  contentHtml: string
  coverImageUrl?: string | null
  tags: string[]
  category?: string | null
}

export interface ExtensionSelfMediaAccount {
  accountId: number
  platform: string
  accountName?: string | null
  brandId: number
  brandName?: string | null
}

export interface PlatformIdentitySnapshot {
  status: 'detected' | 'unknown'
  displayName?: string | null
  source?: string | null
  host?: string | null
  href?: string | null
}

export interface CookieCaptureStartedResponse {
  accountId: number
  platform: string
  status: 'captured' | 'opening_login' | 'waiting_login' | 'capture_conflict' | 'identity_review_required' | 'stopped'
  message: string
  expectedAccountName?: string | null
  actualDisplayName?: string | null
}

export interface CookieCaptureRequest {
  brandId: number
  accountId: number
  platform: string
  extensionVersion: string
  installId: string
  operatorConfirmed: boolean
  confirmNonce: string
  cookiesJson: string
  userAgent?: string
  requiredCookieCheckJson?: string
  capturedFingerprintJson?: string
}

export interface CookieCaptureResponse {
  credentialId: number
  accountId: number
  brandId: number
  platform: string
  version: number
  capturedAt: string
  status: 'ACTIVE'
}

export interface ExtensionMessage<T = unknown> {
  type: string
  payload?: T
}

export interface TaskLifecycleEvent {
  taskId?: number
  kind: 'published' | 'stopped' | 'auth_required'
  message: string
}
