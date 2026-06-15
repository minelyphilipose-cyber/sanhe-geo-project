import { signLocalAgentRequest } from '@/api/localAgent'
import { normalizeLocalAgentErrorMessage } from '@/api/localAgentErrorMessages'

export interface LocalHelperClientConfig {
  helperBase: string
  localAgentSessionId?: number | null
}

export interface LocalHelperLaunchTaskPayload {
  environmentKey: string
  providerProfileId?: string | null
  environmentName?: string | null
  backendBase?: string
  taskId: number
  selfMediaAccountId: number
  browserEnvironmentAccountId?: number | null
  platform: string
  url?: string | null
  expectedPlatformAccountId?: string | null
  expectedAccountName?: string | null
  backendTask?: unknown
}

export interface LocalHelperCreateAndLaunchResponse {
  ok: boolean
  task?: {
    id: number
    articleId: number
    platform: string
    status: string
  }
  environment?: unknown
  error?: string
}

export interface LocalHelperOpenEnvironmentPayload {
  environmentKey: string
  providerProfileId?: string | null
  environmentName?: string | null
  url?: string | null
}

export interface LocalHelperExtensionStatus {
  installed: boolean
  detected: boolean
  status: 'installed' | 'not_detected' | 'unknown' | string
  extensionId?: string | null
  name?: string | null
  version?: string | null
  targetType?: string | null
  targetUrl?: string | null
  reason?: string | null
  inspectedExtensionTargets?: number | null
}

export interface LocalHelperOpenEnvironmentResponse {
  ok: boolean
  environmentKey?: string | null
  environmentName?: string | null
  providerProfileId?: string | null
  openResult?: unknown
  extensionStatus?: LocalHelperExtensionStatus | null
  error?: string
}

export interface LocalHelperExtensionStatusResponse {
  ok: boolean
  environmentKey?: string | null
  environmentName?: string | null
  providerProfileId?: string | null
  extensionStatus?: LocalHelperExtensionStatus | null
  error?: string
}

export interface LocalHelperExtensionBindIntentPayload {
  bindCode: string
  brandId?: number | null
  apiBase?: string | null
  helperBase?: string | null
  environmentKey: string
  providerProfileId: string
  environmentName?: string | null
  expiresInSeconds?: number | null
}

export interface LocalHelperExtensionBindIntentResponse {
  ok: boolean
  intentToken: string
  expiresAt?: string | null
  environmentKey?: string | null
  providerProfileId?: string | null
  environmentName?: string | null
  error?: string
}

export interface LocalHelperHealthResponse {
  ok: boolean
  service?: string | null
  version?: string | null
  time?: string | null
  paired?: boolean
  session?: {
    sessionId?: number | null
    brandId?: number | null
    operatorId?: number | null
    pairedAt?: string | null
    expiresAt?: string | null
  } | null
  adspower?: LocalHelperAdspowerSettings | null
  runtime?: {
    pid?: number | null
    ppid?: number | null
    node?: string | null
    startedAt?: string | null
    uptimeSeconds?: number | null
    supervised?: boolean | null
    cwd?: string | null
  } | null
  schedulePoll?: {
    inFlight?: boolean
    last?: unknown
    platforms?: string[]
    platformSource?: string | null
    platformFetchError?: string | null
    intervalMs?: number | null
  } | null
  config?: {
    host?: string | null
    port?: number | null
    backendBase?: string | null
    trustedBackendBase?: string | null
  } | null
  error?: string
}

export interface LocalHelperAdspowerSettings {
  apiBase?: string | null
  apiKeyConfigured?: boolean
  apiKeyPreview?: string | null
}

export interface LocalHelperAdspowerProfile {
  providerProfileId: string
  name?: string | null
  serialNumber?: string | null
  groupName?: string | null
  remark?: string | null
  status?: string | null
}

export interface LocalHelperAdspowerProfileListResponse {
  ok: boolean
  list: LocalHelperAdspowerProfile[]
  page?: number | null
  pageSize?: number | null
  total?: number | null
  error?: string
}

function normalizeBaseUrl(base: string) {
  return base.trim().replace(/\/+$/, '')
}

async function sha256Hex(input: string) {
  const bytes = new TextEncoder().encode(input)
  const digest = await crypto.subtle.digest('SHA-256', bytes)
  return Array.from(new Uint8Array(digest))
    .map((byte) => byte.toString(16).padStart(2, '0'))
    .join('')
}

async function readJsonResponse(response: Response) {
  const text = await response.text()
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    return { error: text }
  }
}

async function fetchLocalHelper(input: RequestInfo | URL, init?: RequestInit) {
  try {
    return await fetch(input, init)
  } catch (error) {
    const message = error instanceof Error ? error.message : null
    throw new Error(normalizeLocalAgentErrorMessage(message, '无法连接本地助手，请确认本地助手已启动，并允许当前后台地址访问'))
  }
}

async function resolvePairedSessionId(config: LocalHelperClientConfig) {
  let sessionId = config.localAgentSessionId || null
  const health = await getLocalHelperHealth(config.helperBase)
  const helperSessionId = Number(health.session?.sessionId)
  if (health.paired && Number.isFinite(helperSessionId) && helperSessionId > 0) {
    sessionId = helperSessionId
  }
  if (!sessionId) {
    throw new Error('请先完成本地助手配对')
  }
  return sessionId
}

async function buildAuthHeaders(config: LocalHelperClientConfig, method: string, path: string, bodyText: string) {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  }
  const localAgentSessionId = await resolvePairedSessionId(config)
  const bodyHash = await sha256Hex(bodyText)
  let data: Awaited<ReturnType<typeof signLocalAgentRequest>>['data']
  try {
    const response = await signLocalAgentRequest(localAgentSessionId, {
      method,
      path,
      bodyHash,
    })
    data = response.data
  } catch (error) {
    const message = error instanceof Error ? error.message : null
    throw new Error(normalizeLocalAgentErrorMessage(message, '本地助手签名失败，请重新配对本地助手'))
  }
  return {
    ...headers,
    ...(data.data?.headers || {}),
  }
}

export async function getLocalHelperHealth(helperBase: string) {
  const base = normalizeBaseUrl(helperBase)
  const response = await fetchLocalHelper(`${base}/health`)
  const body = await readJsonResponse(response) as LocalHelperHealthResponse | null
  if (!response.ok || !body || body.ok === false) {
    throw new Error(normalizeLocalAgentErrorMessage(body?.error, `本地助手健康检查失败：${response.status}`))
  }
  return body
}

export async function getLocalHelperAdspowerSettings(helperBase: string) {
  const base = normalizeBaseUrl(helperBase)
  const response = await fetchLocalHelper(`${base}/v1/settings/adspower`)
  const body = await readJsonResponse(response) as { ok?: boolean; adspower?: LocalHelperAdspowerSettings; error?: string } | null
  if (!response.ok || body?.ok === false) {
    throw new Error(normalizeLocalAgentErrorMessage(body?.error, `本地助手 AdsPower 配置读取失败：${response.status}`))
  }
  return body?.adspower || null
}

export async function updateLocalHelperAdspowerSettings(
  helperBase: string,
  payload: { apiBase?: string | null; apiKey?: string | null },
) {
  const base = normalizeBaseUrl(helperBase)
  const response = await fetchLocalHelper(`${base}/v1/settings/adspower`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
  const body = await readJsonResponse(response) as { ok?: boolean; adspower?: LocalHelperAdspowerSettings; error?: string } | null
  if (!response.ok || body?.ok === false) {
    throw new Error(normalizeLocalAgentErrorMessage(body?.error, `本地助手 AdsPower 配置保存失败：${response.status}`))
  }
  return body?.adspower || null
}

export async function listLocalHelperAdspowerProfiles(
  config: LocalHelperClientConfig,
  params: { page?: number; pageSize?: number; search?: string } = {},
) {
  const base = normalizeBaseUrl(config.helperBase)
  const query = new URLSearchParams()
  query.set('page', String(params.page || 1))
  query.set('pageSize', String(params.pageSize || 50))
  if (params.search?.trim()) query.set('search', params.search.trim())
  const path = `/v1/adspower/profiles?${query.toString()}`
  const response = await fetchLocalHelper(`${base}${path}`, {
    method: 'GET',
    headers: await buildAuthHeaders(config, 'GET', path, ''),
  })
  const body = await readJsonResponse(response) as LocalHelperAdspowerProfileListResponse | null
  if (!response.ok || body?.ok === false) {
    throw new Error(normalizeLocalAgentErrorMessage(body?.error, `本地助手 AdsPower 环境列表读取失败：${response.status}`))
  }
  return body || { ok: true, list: [] }
}

export async function openLocalHelperEnvironment(
  config: LocalHelperClientConfig,
  payload: LocalHelperOpenEnvironmentPayload,
) {
  const base = normalizeBaseUrl(config.helperBase)
  const path = '/v1/poc/open-environment'
  const bodyText = JSON.stringify(payload)
  const response = await fetchLocalHelper(`${base}${path}`, {
    method: 'POST',
    headers: await buildAuthHeaders(config, 'POST', path, bodyText),
    body: bodyText,
  })
  const body = await readJsonResponse(response) as { ok?: boolean; error?: string } | null
  if (!response.ok || body?.ok === false) {
    throw new Error(normalizeLocalAgentErrorMessage(body?.error, `本地助手请求失败：${response.status}`))
  }
  return body as LocalHelperOpenEnvironmentResponse | null
}

export async function inspectLocalHelperAdspowerExtension(
  config: LocalHelperClientConfig,
  payload: Omit<LocalHelperOpenEnvironmentPayload, 'url'>,
) {
  const base = normalizeBaseUrl(config.helperBase)
  const path = '/v1/adspower/extension-status'
  const bodyText = JSON.stringify(payload)
  const response = await fetchLocalHelper(`${base}${path}`, {
    method: 'POST',
    headers: await buildAuthHeaders(config, 'POST', path, bodyText),
    body: bodyText,
  })
  const body = await readJsonResponse(response) as LocalHelperExtensionStatusResponse | null
  if (!response.ok || body?.ok === false) {
    throw new Error(normalizeLocalAgentErrorMessage(body?.error, `本地助手扩展状态探测失败：${response.status}`))
  }
  return body
}

export async function createLocalHelperExtensionBindIntent(
  config: LocalHelperClientConfig,
  payload: LocalHelperExtensionBindIntentPayload,
) {
  const base = normalizeBaseUrl(config.helperBase)
  const path = '/v1/extension/bind-intents'
  const bodyText = JSON.stringify(payload)
  const response = await fetchLocalHelper(`${base}${path}`, {
    method: 'POST',
    headers: await buildAuthHeaders(config, 'POST', path, bodyText),
    body: bodyText,
  })
  const body = await readJsonResponse(response) as LocalHelperExtensionBindIntentResponse | null
  if (!response.ok || body?.ok === false || !body?.intentToken) {
    throw new Error(normalizeLocalAgentErrorMessage(body?.error, `本地助手扩展绑定意图创建失败：${response.status}`))
  }
  return body
}

export async function launchLocalHelperTask(
  config: LocalHelperClientConfig,
  payload: LocalHelperLaunchTaskPayload,
) {
  const base = normalizeBaseUrl(config.helperBase)
  const path = '/v1/poc/launch'
  const bodyText = JSON.stringify(payload)
  const response = await fetchLocalHelper(`${base}${path}`, {
    method: 'POST',
    headers: await buildAuthHeaders(config, 'POST', path, bodyText),
    body: bodyText,
  })
  const body = await readJsonResponse(response) as LocalHelperCreateAndLaunchResponse | null
  if (!response.ok || body?.ok === false) {
    throw new Error(normalizeLocalAgentErrorMessage(body?.error, `本地助手请求失败：${response.status}`))
  }
  return body
}
