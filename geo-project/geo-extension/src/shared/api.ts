import { API_BASE_URL } from './env'
import type {
  ApiErrorBody,
  BindResponse,
  CookieCaptureRequest,
  CookieCaptureResponse,
  ExtensionSelfMediaAccount,
  ExtensionTaskStateResponse,
  ExtensionTaskListItem,
  FillTokenConsumeResponse,
  FillTokenIssueResponse,
  PublishTaskReport,
  TokenRefreshResponse,
  VersionCheckResponse,
} from '@/types/extension'

export class ExtensionApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: number | undefined,
    message: string,
  ) {
    super(message)
    this.name = 'ExtensionApiError'
  }
}

interface ApiResponseEnvelope<T> {
  code?: number
  message?: string
  data?: T
}

const REQUEST_TIMEOUT_MS = 15_000

async function request<T>(path: string, init: RequestInit = {}, token?: string): Promise<T> {
  const headers = new Headers(init.headers)
  headers.set('Content-Type', 'application/json')
  if (token) headers.set('X-Ext-Token', token)

  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS)
  try {
    const response = await fetch(`${API_BASE_URL}${path}`, { ...init, headers, signal: controller.signal })
    const body = (await response.json().catch(() => ({}))) as ApiResponseEnvelope<T> & ApiErrorBody
    if (!response.ok || (body.code !== undefined && body.code !== 0)) {
      throw new ExtensionApiError(response.status, body.code, body.message || 'Request failed')
    }
    return body.data as T
  } catch (error) {
    if (error instanceof ExtensionApiError) throw error
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw new Error('请求服务端超时，请确认后端服务可用后重试。')
    }
    throw error
  } finally {
    clearTimeout(timeout)
  }
}

export const extensionApi = {
  versionCheck(currentVersion: string) {
    return request<VersionCheckResponse>('/api/v1/extension/version-check', {
      method: 'POST',
      body: JSON.stringify({ platform: 'chrome', currentVersion }),
    })
  },

  bind(bindCode: string, installId: string, extensionVersion: string) {
    return request<BindResponse>('/api/v1/extension/bind', {
      method: 'POST',
      body: JSON.stringify({ bindCode, installId, extensionVersion }),
    })
  },

  refresh(token: string, extensionVersion: string) {
    return request<TokenRefreshResponse>('/api/v1/extension/token/refresh', {
      method: 'POST',
      body: JSON.stringify({ extensionVersion }),
    }, token)
  },

  revoke(token: string, sessionId: number) {
    return request<void>(`/api/v1/extension/token/${sessionId}/revoke`, {
      method: 'POST',
    }, token)
  },

  tasks(token: string) {
    return request<ExtensionTaskListItem[]>('/api/v1/extension/tasks', {
      method: 'GET',
    }, token)
  },

  selfMediaAccounts(token: string) {
    return request<ExtensionSelfMediaAccount[]>('/api/v1/extension/self-media-accounts', {
      method: 'GET',
    }, token)
  },

  captureCookies(token: string, payload: CookieCaptureRequest) {
    return request<CookieCaptureResponse>('/api/v1/extension/cookies/capture', {
      method: 'POST',
      body: JSON.stringify(payload),
    }, token)
  },

  issueFillToken(token: string, payload: { taskTargetId: number, platform: string, extensionVersion: string }) {
    return request<FillTokenIssueResponse>('/api/v1/extension/fill-token/issue', {
      method: 'POST',
      body: JSON.stringify(payload),
    }, token)
  },

  consumeFillToken(token: string, payload: { fillToken: string, platform: string, extensionVersion: string }) {
    return request<FillTokenConsumeResponse>('/api/v1/extension/fill-token/consume', {
      method: 'POST',
      body: JSON.stringify(payload),
    }, token)
  },

  ackTask(token: string, taskId: number) {
    return request<ExtensionTaskStateResponse>(`/api/v1/extension/tasks/${taskId}/ack`, {
      method: 'POST',
    }, token)
  },

  heartbeatTask(token: string, taskId: number) {
    return request<ExtensionTaskStateResponse>(`/api/v1/extension/tasks/${taskId}/heartbeat`, {
      method: 'POST',
    }, token)
  },

  publishedTask(token: string, taskId: number, payload?: PublishTaskReport) {
    return request<ExtensionTaskStateResponse>(`/api/v1/extension/tasks/${taskId}/published`, {
      method: 'POST',
      body: JSON.stringify(payload ?? {}),
    }, token)
  },

  abandonTask(token: string, taskId: number) {
    return request<ExtensionTaskStateResponse>(`/api/v1/extension/tasks/${taskId}/abandon`, {
      method: 'POST',
    }, token)
  },
}
