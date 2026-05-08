const BRIDGE_CHANNEL = 'GEO_EXTENSION_BRIDGE'
const BRIDGE_TIMEOUT_MS = 3_000

type BridgeInboundType = 'GEO_PONG' | 'GEO_FILL_STATUS' | 'GEO_FILL_ERROR' | 'GEO_COOKIE_CAPTURE_STATUS'
type BridgeOutboundType = 'GEO_PING' | 'GEO_START_FILL' | 'GEO_START_COOKIE_CAPTURE'

interface BridgeMessage<T = unknown> {
  channel: typeof BRIDGE_CHANNEL
  type: BridgeInboundType | BridgeOutboundType
  requestId: string
  payload?: T
}

export interface ExtensionStartFillCommand {
  type: 'GEO_START_FILL'
  requestId: string
  taskId: number
  articleId?: number
  accountId?: number
  platform: string
}

export interface ExtensionStartCookieCaptureCommand {
  type: 'GEO_START_COOKIE_CAPTURE'
  requestId: string
  brandId: number
  accountId: number
  platform: string
  accountName?: string | null
}

export interface ExtensionBridgeResult {
  type: BridgeInboundType
  payload?: {
    taskId?: number
    status?: string
    code?: string
    message?: string
    installed?: boolean
    extensionVersion?: string
    bound?: boolean
  }
}

export function pingExtensionBridge(): Promise<ExtensionBridgeResult> {
  return sendBridgeMessage('GEO_PING', {})
}

export function startExtensionFill(command: ExtensionStartFillCommand): Promise<ExtensionBridgeResult> {
  return sendBridgeMessage(command.type, {
    taskId: command.taskId,
    articleId: command.articleId,
    accountId: command.accountId,
    platform: command.platform,
  }, command.requestId)
}

export function startExtensionCookieCapture(command: ExtensionStartCookieCaptureCommand): Promise<ExtensionBridgeResult> {
  return sendBridgeMessage(command.type, {
    brandId: command.brandId,
    accountId: command.accountId,
    platform: command.platform,
    accountName: command.accountName,
  }, command.requestId)
}

function sendBridgeMessage<T extends object>(
  type: BridgeOutboundType,
  payload: T,
  requestId = createRequestId('geo_extension'),
): Promise<ExtensionBridgeResult> {
  return new Promise((resolve, reject) => {
    const timer = window.setTimeout(() => {
      cleanup()
      reject(new Error('未检测到浏览器扩展，请确认扩展已安装并启用。'))
    }, BRIDGE_TIMEOUT_MS)

    const onMessage = (event: MessageEvent) => {
      if (event.source !== window) return
      if (!isBridgeMessage(event.data)) return
      if (event.data.requestId !== requestId) return
      if (!['GEO_PONG', 'GEO_FILL_STATUS', 'GEO_FILL_ERROR', 'GEO_COOKIE_CAPTURE_STATUS'].includes(event.data.type)) return
      cleanup()
      resolve({
        type: event.data.type as BridgeInboundType,
        payload: event.data.payload as ExtensionBridgeResult['payload'],
      })
    }

    const cleanup = () => {
      window.clearTimeout(timer)
      window.removeEventListener('message', onMessage)
    }

    window.addEventListener('message', onMessage)
    window.postMessage({
      channel: BRIDGE_CHANNEL,
      type,
      requestId,
      payload,
    } satisfies BridgeMessage<T>, window.location.origin)
  })
}

function isBridgeMessage(value: unknown): value is BridgeMessage {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Partial<BridgeMessage>
  return candidate.channel === BRIDGE_CHANNEL
    && typeof candidate.type === 'string'
    && typeof candidate.requestId === 'string'
}

function createRequestId(prefix: string) {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID()
  }
  return `${prefix}_${Date.now()}_${Math.random().toString(16).slice(2)}`
}
