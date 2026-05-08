const BRIDGE_CHANNEL = 'GEO_EXTENSION_BRIDGE'

type AdminBridgeInboundType = 'GEO_PING' | 'GEO_START_FILL' | 'GEO_START_COOKIE_CAPTURE'
type AdminBridgeOutboundType = 'GEO_PONG' | 'GEO_FILL_STATUS' | 'GEO_FILL_ERROR' | 'GEO_COOKIE_CAPTURE_STATUS'

interface AdminBridgeMessage<T = unknown> {
  channel: typeof BRIDGE_CHANNEL
  type: AdminBridgeInboundType | AdminBridgeOutboundType
  requestId: string
  payload?: T
}

const DEV_ADMIN_ORIGINS = [
  'http://localhost:3000',
  'http://127.0.0.1:3000',
  'http://localhost:5173',
  'http://127.0.0.1:5173',
]

const configuredAdminOrigin = import.meta.env.VITE_GEO_ADMIN_ORIGIN?.replace(/\/$/, '')

function isAllowedAdminOrigin(origin: string): boolean {
  if (configuredAdminOrigin) return origin === configuredAdminOrigin
  if (DEV_ADMIN_ORIGINS.includes(origin)) return true
  return isPrivateNetworkHttpOrigin(origin)
}

function isPrivateNetworkHttpOrigin(origin: string): boolean {
  try {
    const url = new URL(origin)
    if (url.protocol !== 'http:') return false
    const host = url.hostname
    if (host === 'localhost') return true
    return /^10\./.test(host)
      || /^192\.168\./.test(host)
      || /^172\.(1[6-9]|2\d|3[0-1])\./.test(host)
      || /^127\./.test(host)
  } catch {
    return false
  }
}

window.addEventListener('message', event => {
  if (event.source !== window) return
  if (!isAllowedAdminOrigin(event.origin)) return
  if (!isBridgeMessage(event.data)) return
  if (
    event.data.type !== 'GEO_PING'
    && event.data.type !== 'GEO_START_FILL'
    && event.data.type !== 'GEO_START_COOKIE_CAPTURE'
  ) return

  void chrome.runtime.sendMessage(event.data)
    .then(response => {
      postToAdmin(event.origin, event.data.requestId, response)
    })
    .catch(error => {
      postToAdmin(event.origin, event.data.requestId, {
        type: 'GEO_FILL_ERROR',
        payload: {
          code: 'BRIDGE_RUNTIME_ERROR',
          message: error instanceof Error ? error.message : '扩展后台通信失败',
        },
      })
    })
})

function postToAdmin(origin: string, requestId: string, response: Partial<AdminBridgeMessage>) {
  window.postMessage({
    channel: BRIDGE_CHANNEL,
    requestId,
    ...response,
  }, origin)
}

function isBridgeMessage(value: unknown): value is AdminBridgeMessage {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Partial<AdminBridgeMessage>
  return candidate.channel === BRIDGE_CHANNEL
    && typeof candidate.type === 'string'
    && typeof candidate.requestId === 'string'
}
