import { EXTENSION_VERSION } from '@/shared/env'

export const BRIDGE_CHANNEL = 'GEO_EXTENSION_BRIDGE'

export type AdminBridgeInboundType = 'GEO_PING' | 'GEO_START_FILL' | 'GEO_START_COOKIE_CAPTURE'
export type AdminBridgeOutboundType = 'GEO_PONG' | 'GEO_FILL_STATUS' | 'GEO_FILL_ERROR' | 'GEO_COOKIE_CAPTURE_STATUS'

export interface AdminBridgeMessage<T = unknown> {
  channel: typeof BRIDGE_CHANNEL
  type: AdminBridgeInboundType | AdminBridgeOutboundType
  requestId: string
  payload?: T
}

export interface AdminStartFillPayload {
  taskId: number
  articleId?: number
  accountId?: number
  platform: string
}

export interface AdminStartCookieCapturePayload {
  brandId: number
  accountId: number
  platform: string
  accountName?: string | null
}

export interface AdminFillStatusPayload {
  taskId?: number
  status: 'accepted' | 'opening_editor' | 'filled' | 'published' | 'stopped'
  message: string
}

export interface AdminCookieCaptureStatusPayload {
  accountId?: number
  platform?: string
  status: 'captured' | 'opening_login' | 'waiting_login'
  message: string
}

export interface AdminFillErrorPayload {
  taskId?: number
  code: string
  message: string
}

export interface AdminPongPayload {
  installed: true
  extensionVersion: string
  bound: boolean
}

export function isBridgeMessage(value: unknown): value is AdminBridgeMessage {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Partial<AdminBridgeMessage>
  return candidate.channel === BRIDGE_CHANNEL
    && typeof candidate.type === 'string'
    && typeof candidate.requestId === 'string'
}

export function pongMessage(requestId: string, bound: boolean): AdminBridgeMessage<AdminPongPayload> {
  return {
    channel: BRIDGE_CHANNEL,
    type: 'GEO_PONG',
    requestId,
    payload: {
      installed: true,
      extensionVersion: EXTENSION_VERSION,
      bound,
    },
  }
}
