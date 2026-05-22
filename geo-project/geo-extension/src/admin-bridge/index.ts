const BRIDGE_CHANNEL = 'GEO_EXTENSION_BRIDGE'

type AdminBridgeInboundType = 'GEO_PING' | 'GEO_BIND_EXTENSION' | 'GEO_START_FILL' | 'GEO_START_COOKIE_CAPTURE'
type AdminBridgeOutboundType = 'GEO_PONG' | 'GEO_BIND_STATUS' | 'GEO_FILL_STATUS' | 'GEO_FILL_ERROR' | 'GEO_COOKIE_CAPTURE_STATUS'

interface AdminBridgeMessage<T = unknown> {
  channel: typeof BRIDGE_CHANNEL
  type: AdminBridgeInboundType | AdminBridgeOutboundType
  requestId: string
  payload?: T
}

const EXTENSION_PROFILE = __EXTENSION_PROFILE__
const ALLOWED_ADMIN_ORIGINS = EXTENSION_PROFILE.adminOrigins.map(origin => origin.replace(/\/$/, ''))

function isAllowedAdminOrigin(origin: string): boolean {
  return ALLOWED_ADMIN_ORIGINS.includes(origin)
}

window.addEventListener('message', event => {
  if (event.source !== window) return
  if (!isAllowedAdminOrigin(event.origin)) return
  if (!isBridgeMessage(event.data)) return
  if (
    event.data.type !== 'GEO_PING'
    && event.data.type !== 'GEO_BIND_EXTENSION'
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
