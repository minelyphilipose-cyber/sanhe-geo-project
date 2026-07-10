importScripts('env-config.js', 'fill-result.js', 'platform-baijiahao.js', 'platform-douyin.js', 'platform-xiaohongshu.js', 'platform-zhihu.js')

const EXTENSION_VERSION = '0.1.9'
const EXTENSION_BUILD_REVISION = '20260710.3'
const DOUYIN_MANAGE_URL = 'https://creator.douyin.com/creator-micro/content/manage?enter_from=publish'
const INSTALL_ID_KEY = 'geoEnvInstallId'
const EVENT_LOG_KEY = 'geoEnvEventLog'
const ACTIVE_PROFILE_KEY = 'geoEnvActiveProfile'
const PROFILES_KEY = 'geoEnvProfiles'
const SESSIONS_KEY = 'geoEnvSessions'
const LEGACY_CONFIG_KEY = 'geoEnvConfig'
const LEGACY_SESSION_KEY = 'geoEnvSession'
const DEFAULT_PROFILE_KEY = 'prod'
const DEFAULT_PROFILE_CONFIGS = {
  dev: {
    label: '本地开发',
    apiBase: 'http://127.0.0.1:8080',
    helperBase: 'http://127.0.0.1:17891',
  },
  prod: {
    label: '生产环境',
    apiBase: 'https://www.huanjingaigeo.com',
    helperBase: 'http://127.0.0.1:17891',
  },
}
const BUILD_PROFILE_CONFIG = globalThis.GEO_ENV_BUILD_CONFIG || DEFAULT_PROFILE_CONFIGS.prod
const BUILD_PROFILE_KEY = normalizeProfileKey(BUILD_PROFILE_CONFIG.profileKey || DEFAULT_PROFILE_KEY)
const BUILD_PROFILE_LABEL = BUILD_PROFILE_CONFIG.profileLabel || BUILD_PROFILE_CONFIG.label || DEFAULT_PROFILE_CONFIGS[BUILD_PROFILE_KEY]?.label || BUILD_PROFILE_KEY
const IDENTITY_PRECHECK_PLATFORMS = new Set(['toutiao', 'zhihu', 'xiaohongshu', 'baijiahao', 'douyin'])
const autoLoginReportAtByKey = new Map()
const autoPollTabUpdatedAtByKey = new Map()
const bindIntentInFlight = new Set()
const MAX_IMAGE_FETCH_BYTES = 20 * 1024 * 1024
const RUNTIME_STATUS_HEARTBEAT_MS = 60 * 1000
const HELPER_SIGNING_CONTEXT_CACHE_MS = 10 * 1000
const HELPER_SIGNATURE_CLOCK_WARNING_SECONDS = 240
const extensionSessionRefreshInFlight = new Map()
const helperSigningContextCache = new Map()
let runtimeStatusReportInFlight = false
let lastRuntimeStatusReportAt = 0

function normalizeBaseUrl(value) {
  return String(value || '').replace(/\/+$/, '')
}

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

async function sha256Hex(text) {
  const data = new TextEncoder().encode(text || '')
  const hash = await crypto.subtle.digest('SHA-256', data)
  return Array.from(new Uint8Array(hash))
    .map((byte) => byte.toString(16).padStart(2, '0'))
    .join('')
}

async function storageGet(keys) {
  return chrome.storage.local.get(keys)
}

async function storageSet(values) {
  return chrome.storage.local.set(values)
}

async function appendEventLog(entry) {
  try {
    const result = await storageGet([EVENT_LOG_KEY])
    const events = Array.isArray(result[EVENT_LOG_KEY]) ? result[EVENT_LOG_KEY] : []
    events.unshift({
      at: new Date().toISOString(),
      ...entry,
    })
    await storageSet({ [EVENT_LOG_KEY]: events.slice(0, 80) })
  } catch {
    // Event logging is diagnostic only.
  }
}

async function getInstallId() {
  const result = await storageGet([INSTALL_ID_KEY])
  if (result[INSTALL_ID_KEY]) return result[INSTALL_ID_KEY]
  const installId = crypto.randomUUID()
  await storageSet({ [INSTALL_ID_KEY]: installId })
  return installId
}

function normalizeProfileKey(value) {
  const text = String(value || '').trim().toLowerCase()
  return text.replace(/[^a-z0-9_-]/g, '') || DEFAULT_PROFILE_KEY
}

function defaultProfileConfig(profileKey) {
  const key = normalizeProfileKey(profileKey)
  const buildDefaults = key === BUILD_PROFILE_KEY ? BUILD_PROFILE_CONFIG : {}
  return {
    apiBase: buildDefaults.apiBase || DEFAULT_PROFILE_CONFIGS[key]?.apiBase || DEFAULT_PROFILE_CONFIGS.prod.apiBase,
    helperBase: buildDefaults.helperBase || DEFAULT_PROFILE_CONFIGS[key]?.helperBase || DEFAULT_PROFILE_CONFIGS.prod.helperBase,
    environmentKey: '',
    brandId: null,
    environmentAccountId: null,
    selfMediaAccountId: null,
    platform: '',
    autoRun: true,
    profileKey: key,
    profileLabel: buildDefaults.profileLabel || buildDefaults.label || DEFAULT_PROFILE_CONFIGS[key]?.label || key,
  }
}

async function loadProfileStore() {
  const stored = await storageGet([ACTIVE_PROFILE_KEY, PROFILES_KEY, SESSIONS_KEY, LEGACY_CONFIG_KEY, LEGACY_SESSION_KEY])
  const profiles = {
    dev: defaultProfileConfig('dev'),
    prod: defaultProfileConfig('prod'),
    ...(stored[PROFILES_KEY] || {}),
  }
  const sessions = { ...(stored[SESSIONS_KEY] || {}) }
  const legacyConfig = stored[LEGACY_CONFIG_KEY]
  const legacySession = stored[LEGACY_SESSION_KEY]
  if (legacyConfig && !stored[PROFILES_KEY]?.prod) {
    profiles.prod = {
      ...profiles.prod,
      ...legacyConfig,
      profileKey: 'prod',
      profileLabel: profiles.prod.profileLabel,
    }
  }
  if (legacySession && !sessions.prod) {
    sessions.prod = legacySession
  }
  profiles[BUILD_PROFILE_KEY] = {
    ...defaultProfileConfig(BUILD_PROFILE_KEY),
    ...(profiles[BUILD_PROFILE_KEY] || {}),
    apiBase: BUILD_PROFILE_CONFIG.apiBase || profiles[BUILD_PROFILE_KEY]?.apiBase,
    helperBase: BUILD_PROFILE_CONFIG.helperBase || profiles[BUILD_PROFILE_KEY]?.helperBase,
    profileKey: BUILD_PROFILE_KEY,
    profileLabel: BUILD_PROFILE_LABEL,
  }
  const effectiveActiveProfile = BUILD_PROFILE_KEY
  return { activeProfile: effectiveActiveProfile, profiles, sessions }
}

async function saveProfileStore(store) {
  const activeProfile = BUILD_PROFILE_KEY
  const legacyConfig = store.profiles?.prod || defaultProfileConfig('prod')
  const legacySession = store.sessions?.prod || null
  await storageSet({
    [ACTIVE_PROFILE_KEY]: activeProfile,
    [PROFILES_KEY]: store.profiles || {},
    [SESSIONS_KEY]: store.sessions || {},
    [LEGACY_CONFIG_KEY]: legacyConfig,
    [LEGACY_SESSION_KEY]: legacySession,
  })
}

async function saveActiveConfig(config) {
  const store = await loadProfileStore()
  const activeProfile = BUILD_PROFILE_KEY
  const previous = store.profiles[activeProfile] || defaultProfileConfig(activeProfile)
  store.activeProfile = activeProfile
  store.profiles[activeProfile] = {
    ...previous,
    ...config,
    profileKey: activeProfile,
    profileLabel: BUILD_PROFILE_LABEL || previous.profileLabel || DEFAULT_PROFILE_CONFIGS[activeProfile]?.label || activeProfile,
  }
  await saveProfileStore(store)
  return store.profiles[activeProfile]
}

async function saveActiveSession(session) {
  const store = await loadProfileStore()
  store.sessions[store.activeProfile] = session || null
  await saveProfileStore(store)
  return session || null
}

async function getConfig() {
  const store = await loadProfileStore()
  const profileConfig = store.profiles[store.activeProfile] || defaultProfileConfig(store.activeProfile)
  const config = {
    ...defaultProfileConfig(store.activeProfile),
    ...profileConfig,
    profileKey: store.activeProfile,
  }
  config.platform = normalizePlatform(config.platform)
  return { config, session: store.sessions[store.activeProfile] || null, profileStore: store }
}

async function apiRequest(config, path, init = {}, extensionToken) {
  const headers = new Headers(init.headers)
  headers.set('Content-Type', 'application/json')
  if (extensionToken) headers.set('X-Ext-Token', extensionToken)

  const response = await fetch(`${normalizeBaseUrl(config.apiBase)}${path}`, {
    ...init,
    headers,
  })
  const body = await response.json().catch(() => ({}))
  if (!response.ok || (body.code !== undefined && body.code !== 0)) {
    const error = new Error(body.message || `后台请求失败：${response.status}`)
    error.status = response.status
    error.code = body.code
    throw error
  }
  return body.data
}

function isExtensionUnauthorized(error) {
  const message = String(error?.message || '')
  return error?.status === 401
    || error?.code === 70002
    || message.toLowerCase() === 'unauthorized'
    || message.includes('扩展后台绑定已失效')
}

async function clearExtensionSession(expectedToken = null) {
  const store = await loadProfileStore()
  const currentSession = store.sessions[store.activeProfile] || null
  if (expectedToken) {
    const currentToken = currentSession?.extensionToken || ''
    if (currentToken && currentToken !== expectedToken) return
  }
  store.sessions[store.activeProfile] = null
  await saveProfileStore(store)
}

async function refreshExtensionSession(config, session, options = {}) {
  if (!session?.extensionToken) return session
  const refreshedAt = Date.parse(session.refreshedAt || '')
  if (!options.force && Number.isFinite(refreshedAt) && Date.now() - refreshedAt < 5 * 60 * 1000) {
    return session
  }

  const refreshKey = `${normalizeBaseUrl(config.apiBase)}:${session.extensionToken}`
  const existing = extensionSessionRefreshInFlight.get(refreshKey)
  if (existing) return existing
  const refresh = refreshExtensionSessionOnce(config, session)
  extensionSessionRefreshInFlight.set(refreshKey, refresh)
  try {
    return await refresh
  } finally {
    if (extensionSessionRefreshInFlight.get(refreshKey) === refresh) {
      extensionSessionRefreshInFlight.delete(refreshKey)
    }
  }
}

async function refreshExtensionSessionOnce(config, session) {
  try {
    const refreshed = await apiRequest(config, '/api/v1/extension/token/refresh', {
      method: 'POST',
      body: JSON.stringify({ extensionVersion: EXTENSION_VERSION }),
    }, session.extensionToken)
    const nextSession = {
      ...session,
      sessionId: refreshed?.sessionId || session.sessionId,
      extensionToken: refreshed?.token || session.extensionToken,
      expiresAt: refreshed?.expiresAt || session.expiresAt,
      refreshedAt: new Date().toISOString(),
    }
    await saveActiveSession(nextSession)
    return nextSession
  } catch (error) {
    if (isExtensionUnauthorized(error)) {
      await clearExtensionSession(session.extensionToken)
      const unauthorized = new Error('扩展后台绑定已失效，请在扩展弹窗重新绑定后台')
      unauthorized.status = error?.status || 401
      unauthorized.code = error?.code || 70002
      throw unauthorized
    }
    throw error
  }
}

async function reportRuntimeStatus(options = {}) {
  const { config, session } = await getConfig()
  if (!session?.extensionToken) return { ok: false, skipped: true, reason: 'not_bound' }
  const providerProfileId = firstText(options.providerProfileId, config.providerProfileId)
  const installId = await getInstallId()
  if (!providerProfileId || !installId) {
    return { ok: false, skipped: true, reason: 'missing_runtime_identity' }
  }
  if (runtimeStatusReportInFlight && !options.force) {
    return { ok: false, skipped: true, reason: 'in_flight' }
  }
  const now = Date.now()
  if (!options.force && now - lastRuntimeStatusReportAt < 10_000) {
    return { ok: false, skipped: true, reason: 'throttled' }
  }
  runtimeStatusReportInFlight = true
  try {
    const activeSession = await refreshExtensionSession(config, session)
    const activeTab = await currentActivePlatformTab().catch(() => null)
    const platform = normalizePlatform(firstText(
      options.detectedPlatform,
      options.platform,
      inferPlatformFromUrl(activeTab?.url || ''),
      config.platform,
    ))
    const body = {
      installId,
      environmentKey: firstText(options.environmentKey, config.environmentKey),
      providerProfileId,
      platform: platform || null,
      extensionVersion: EXTENSION_VERSION,
      protocolVersion: '1',
      currentUrl: firstText(options.currentUrl, activeTab?.url),
      detectedPlatform: platform || null,
      detectedAccountName: firstText(options.detectedAccountName),
      detectedPlatformAccountId: firstText(options.detectedPlatformAccountId),
      loginStatus: firstText(options.loginStatus, 'unknown'),
      runtimeStage: firstText(options.runtimeStage, 'extension_seen'),
      runtimeStageMessage: firstText(options.runtimeStageMessage),
      capabilities: {
        fill: true,
        schedule: true,
        accountDetect: true,
        publishSubmit: true,
        publishCheck: false,
        buildRevision: EXTENSION_BUILD_REVISION,
      },
      lastTaskId: options.lastTaskId || null,
      lastErrorCode: firstText(options.lastErrorCode),
      lastErrorMessage: firstText(options.lastErrorMessage),
    }
    const status = await apiRequest(config, '/api/v1/extension/runtime-status', {
      method: 'POST',
      body: JSON.stringify(body),
    }, activeSession.extensionToken)
    lastRuntimeStatusReportAt = Date.now()
    return { ok: true, status }
  } catch (error) {
    await appendEventLog({
      type: 'runtime_status',
      ok: false,
      reason: options.reason || 'report',
      error: error.message,
    })
    return { ok: false, error: error.message }
  } finally {
    runtimeStatusReportInFlight = false
  }
}

async function currentActivePlatformTab() {
  const tabs = await chrome.tabs.query({ active: true, currentWindow: true })
  const active = tabs.find((tab) => inferPlatformFromUrl(tab?.url || ''))
  if (active) return active
  const allTabs = await chrome.tabs.query({})
  return allTabs.find((tab) => inferPlatformFromUrl(tab?.url || '')) || null
}

function scheduleRuntimeStatusHeartbeat() {
  setTimeout(() => {
    reportRuntimeStatus({ reason: 'startup', runtimeStage: 'extension_seen', force: true }).catch(() => null)
  }, 1500)
  setInterval(() => {
    reportRuntimeStatus({ reason: 'heartbeat', runtimeStage: 'extension_seen' }).catch(() => null)
  }, RUNTIME_STATUS_HEARTBEAT_MS)
}

function requestBodyText(init = {}) {
  if (init.body == null) return ''
  if (typeof init.body === 'string') return init.body
  throw new Error('本地助手签名暂只支持字符串请求体')
}

async function helperSigningContext(config, options = {}) {
  const helperBase = normalizeBaseUrl(config.helperBase)
  const cached = helperSigningContextCache.get(helperBase)
  if (!options.force && cached && Date.now() - cached.checkedAt < HELPER_SIGNING_CONTEXT_CACHE_MS) {
    return cached
  }
  const response = await fetch(`${helperBase}/health`)
  const health = await response.json().catch(() => ({}))
  if (!response.ok || health?.ok === false) {
    throw new Error(`本地助手健康检查失败：${response.status}`)
  }
  const sessionId = Number(health?.session?.sessionId)
  if (!health?.paired || !Number.isFinite(sessionId) || sessionId <= 0) {
    throw new Error('本地助手尚未与后台配对，请先完成本地助手配对')
  }
  if (health?.version !== EXTENSION_VERSION || health?.buildRevision !== EXTENSION_BUILD_REVISION) {
    const error = new Error(
      `扩展与本地助手构建不一致：扩展=${EXTENSION_VERSION}/${EXTENSION_BUILD_REVISION}，`
      + `助手=${health?.version || '-'}/${health?.buildRevision || '-'}，请同步更新后重试`,
    )
    error.code = 'EXTENSION_HELPER_BUILD_MISMATCH'
    throw error
  }
  const context = {
    checkedAt: Date.now(),
    sessionId,
    helperVersion: String(health?.version || ''),
    helperBuildRevision: String(health?.buildRevision || ''),
  }
  helperSigningContextCache.set(helperBase, context)
  return context
}

function assertBackendSignatureClock(headers) {
  const timestamp = Number(headers?.['X-Geo-Helper-Timestamp'] || headers?.['x-geo-helper-timestamp'])
  if (!Number.isFinite(timestamp)) return
  const skewSeconds = Math.abs(Math.floor(Date.now() / 1000) - timestamp)
  if (skewSeconds <= HELPER_SIGNATURE_CLOCK_WARNING_SECONDS) return
  const error = new Error(`本机与后台时间偏差约 ${skewSeconds} 秒，请先同步系统时间后重试`)
  error.code = 'LOCAL_HELPER_CLOCK_SKEW'
  throw error
}

async function signedHelperHeaders(config, path, init = {}, session = null) {
  if (!session?.extensionToken) return null
  const activeSession = await refreshExtensionSession(config, session)
  const helperContext = await helperSigningContext(config)
  const method = String(init.method || 'GET').toUpperCase()
  const bodyHash = await sha256Hex(requestBodyText(init))
  const body = JSON.stringify({
    method,
    path,
    bodyHash,
    localAgentSessionId: helperContext.sessionId,
  })

  try {
    const signed = await apiRequest(config, '/api/v1/extension/local-agent/sign', {
      method: 'POST',
      body,
    }, activeSession.extensionToken)
    const headers = signed?.headers || null
    assertBackendSignatureClock(headers)
    return headers
  } catch (error) {
    if (!isExtensionUnauthorized(error)) throw error
    const refreshedSession = await refreshExtensionSession(config, activeSession, { force: true })
    const signed = await apiRequest(config, '/api/v1/extension/local-agent/sign', {
      method: 'POST',
      body,
    }, refreshedSession.extensionToken)
    const headers = signed?.headers || null
    assertBackendSignatureClock(headers)
    return headers
  }
}

async function helperRequest(config, path, init = {}, session = null) {
  const headers = new Headers(init.headers)
  headers.set('Content-Type', 'application/json')

  let signedHeaders = null
  let signError = null
  try {
    signedHeaders = await signedHelperHeaders(config, path, init, session)
  } catch (error) {
    signError = error
  }

  if (signedHeaders) {
    for (const [key, value] of Object.entries(signedHeaders)) {
      headers.set(key, value)
    }
  } else if (signError) {
    const signMessage = String(signError.message || signError)
    if (signMessage === 'Failed to fetch') {
      throw new Error(`本地助手签名失败：后端签名接口不可访问(${normalizeBaseUrl(config.apiBase)})，请检查后端服务、网络或环境配置`)
    }
    throw new Error(`本地助手签名失败：${signMessage}`)
  } else {
    const helperHealth = await fetch(`${normalizeBaseUrl(config.helperBase)}/health`).then((response) => response.json()).catch(() => null)
    const pairedText = helperHealth?.paired ? 'paired=true' : 'paired=false'
    throw new Error(`未建立本地助手 C2 会话：extensionToken=${session?.extensionToken ? 'yes' : 'no'}，helper=${pairedText}`)
  }

  const response = await fetch(`${normalizeBaseUrl(config.helperBase)}${path}`, {
    ...init,
    headers,
  })
  const body = await response.json().catch(() => ({}))
  if (!response.ok || body.ok === false) {
    const error = new Error(body.error || `本地助手请求失败：${response.status}`)
    error.status = response.status
    error.code = body.code || null
    error.details = body.details || null
    throw error
  }
  return body
}

function normalizeBindCode(value) {
  return String(value || '').replace(/[\s-]/g, '').toUpperCase()
}

async function bindExtension(bindCode) {
  const { config } = await getConfig()
  const installId = await getInstallId()
  const normalizedBindCode = normalizeBindCode(bindCode)
  if (normalizedBindCode.length < 6) throw new Error('绑定码不正确')

  const data = await apiRequest(config, '/api/v1/extension/bind', {
    method: 'POST',
    body: JSON.stringify({
      bindCode: normalizedBindCode,
      brandId: config.brandId || undefined,
      installId,
      environmentKey: config.environmentKey || undefined,
      providerProfileId: config.providerProfileId || undefined,
      extensionVersion: EXTENSION_VERSION,
      deviceFingerprint: `env:${config.environmentKey}`,
    }),
  })

  const session = {
    sessionId: data.sessionId,
    extensionToken: data.extensionToken || data.token,
    expiresAt: data.expiresAt,
    brandId: data.brandId,
    operatorId: data.operatorId,
    boundAt: new Date().toISOString(),
  }
  if (!session.extensionToken) throw new Error('后台未返回 extensionToken')
  await saveActiveSession(session)
  await refreshRuntimeConfig({ reason: 'bind' }).catch((error) => appendEventLog({
    type: 'runtime_config',
    ok: false,
    reason: 'bind',
    error: error.message,
  }))
  await reportRuntimeStatus({ reason: 'bind', runtimeStage: 'extension_bound', force: true }).catch(() => null)
  return session
}

function bindIntentFromUrl(value) {
  try {
    const url = new URL(value || '')
    const intentToken = url.searchParams.get('geoEnvBindIntent') || ''
    if (!intentToken) return null
    return {
      intentToken,
      helperBase: normalizeBaseUrl(url.searchParams.get('geoEnvHelperBase') || 'http://127.0.0.1:17891'),
      environmentKey: url.searchParams.get('geoEnvEnvironmentKey') || '',
      providerProfileId: url.searchParams.get('geoEnvProviderProfileId') || '',
    }
  } catch {
    return null
  }
}

function removeBindIntentParams(value) {
  try {
    const url = new URL(value || '')
    url.searchParams.delete('geoEnvBindIntent')
    url.searchParams.delete('geoEnvHelperBase')
    url.searchParams.delete('geoEnvEnvironmentKey')
    url.searchParams.delete('geoEnvProviderProfileId')
    url.searchParams.delete('geoEnvAutoBind')
    return url.toString()
  } catch {
    return ''
  }
}

async function consumeLocalBindIntent(helperBase, intent) {
  const response = await fetch(`${normalizeBaseUrl(helperBase)}/v1/extension/bind-intents/consume`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      intentToken: intent.intentToken,
      environmentKey: intent.environmentKey || undefined,
      providerProfileId: intent.providerProfileId || undefined,
    }),
  })
  const body = await response.json().catch(() => ({}))
  if (!response.ok || body.ok === false) {
    throw new Error(body.error || `本地助手绑定意图领取失败：${response.status}`)
  }
  return body
}

async function bindFromLocalIntent(intent, source = {}) {
  const intentToken = String(intent?.intentToken || '').trim()
  const helperBase = normalizeBaseUrl(intent?.helperBase || 'http://127.0.0.1:17891')
  if (!intentToken) throw new Error('缺少扩展绑定意图')
  if (bindIntentInFlight.has(intentToken)) return { skipped: true, reason: 'in_flight' }
  bindIntentInFlight.add(intentToken)
  try {
    const payload = await consumeLocalBindIntent(helperBase, intent)
    const store = await loadProfileStore()
    const intentProfile = normalizeProfileKey(payload.profileKey || BUILD_PROFILE_KEY)
    if (payload.profileKey && intentProfile !== BUILD_PROFILE_KEY) {
      throw new Error(`绑定意图环境与扩展包环境不一致：intent=${intentProfile}, package=${BUILD_PROFILE_KEY}`)
    }
    const payloadProfile = BUILD_PROFILE_KEY
    const storedConfig = store.profiles[payloadProfile] || defaultProfileConfig(payloadProfile)
    const nextConfig = {
      ...storedConfig,
      profileKey: payloadProfile,
      profileLabel: payload.profileLabel || storedConfig.profileLabel || DEFAULT_PROFILE_CONFIGS[payloadProfile]?.label || payloadProfile,
      apiBase: normalizeBaseUrl(payload.apiBase || storedConfig.apiBase || DEFAULT_PROFILE_CONFIGS[payloadProfile]?.apiBase || DEFAULT_PROFILE_CONFIGS.prod.apiBase),
      helperBase: normalizeBaseUrl(payload.helperBase || helperBase),
      brandId: payload.brandId || storedConfig.brandId || null,
      environmentKey: payload.environmentKey || storedConfig.environmentKey || '',
      providerProfileId: payload.providerProfileId || storedConfig.providerProfileId || '',
      platform: storedConfig.platform || '',
      autoRun: storedConfig.autoRun !== false,
    }
    await saveActiveConfig(nextConfig)
    const session = await bindExtension(payload.bindCode)
    await appendEventLog({
      type: 'bind_intent',
      ok: true,
      source: source.reason || 'unknown',
      profileKey: nextConfig.profileKey,
      environmentKey: nextConfig.environmentKey,
      brandId: nextConfig.brandId,
    })
    return { ok: true, session, config: nextConfig }
  } catch (error) {
    await appendEventLog({
      type: 'bind_intent',
      ok: false,
      source: source.reason || 'unknown',
      error: error.message,
    })
    throw error
  } finally {
    bindIntentInFlight.delete(intentToken)
  }
}

async function bindFromActiveTabIntent() {
  const tabs = await chrome.tabs.query({ active: true, currentWindow: true })
  const tab = tabs[0]
  const intent = bindIntentFromUrl(tab?.url || '')
  if (!intent) return { ok: false, skipped: true, reason: 'no_bind_intent' }
  const result = await bindFromLocalIntent(intent, { reason: 'popup' })
  const cleanUrl = removeBindIntentParams(tab.url)
  if (cleanUrl && cleanUrl !== tab.url && tab.id) {
    await chrome.tabs.update(tab.id, { url: cleanUrl }).catch(() => {})
  }
  return result
}

async function bindFromTabUrl(tabId, url) {
  const intent = bindIntentFromUrl(url)
  if (!intent) return
  try {
    await bindFromLocalIntent(intent, { reason: 'tab_url' })
    const cleanUrl = removeBindIntentParams(url)
    if (cleanUrl && cleanUrl !== url && tabId) {
      await chrome.tabs.update(tabId, { url: cleanUrl }).catch(() => {})
    }
    await setBadge('OK')
  } catch (error) {
    await setBadge('ERR')
  }
}

async function pollOnce(options = {}) {
  try {
    await refreshRuntimeConfig({
      reason: options.reason || 'poll',
      platform: options.platform || '',
    })
  } catch (error) {
    if (isExtensionUnauthorized(error)) {
      await setBadge('ERR')
      await appendEventLog({
        type: 'auto_fill',
        ok: false,
        reason: options.reason || 'poll',
        error: '扩展后台绑定已失效，请重新绑定后台',
      })
      return { ok: false, skipped: true, reason: 'extension_binding_expired' }
    }
    await appendEventLog({
      type: 'runtime_config',
      ok: false,
      reason: options.reason || 'poll',
      error: error.message,
    })
  }
  const { config, session } = await getConfig()
  if (!session?.extensionToken) throw new Error('请先绑定后台')
  const platform = options.platform || ''

  let next = null
  let activeConfig = config
  const candidateKeys = await resolveCandidateEnvironmentKeys(config, session, platform)
  for (const environmentKey of candidateKeys) {
    const scopedConfig = { ...config, environmentKey }
    const candidate = await helperRequest(
      scopedConfig,
      `/v1/extension/tasks/next?environmentKey=${encodeURIComponent(environmentKey)}${platform ? `&platform=${encodeURIComponent(platform)}` : ''}`,
      {},
      session,
    )
    if (candidate.task || candidate.status) {
      next = candidate
      activeConfig = scopedConfig
      break
    }
    next = candidate
  }
  if (!candidateKeys.length) {
    next = {
      ok: true,
      task: null,
      status: 'ENVIRONMENT_KEY_REQUIRED',
    }
  }
  if (!next.task) {
    return {
      ok: true,
      message: next.status ? `暂无可领取任务，当前任务状态：${next.status}` : '暂无任务',
      helperStatus: next.status || null,
    }
  }

  try {
    assertTaskEnvironmentMatchesConfig(activeConfig, next.task)
    const candidatePlatformTabId = options.identityTabId || await findActivePlatformTabId(next.task.platform)
    const identityTabId = await ensureTaskIdentityTab(activeConfig, session, next.task, candidatePlatformTabId)
    const fillResult = await handleTask(activeConfig, session, next.task, {
      identityTabId,
      fillTabId: candidatePlatformTabId,
    })
    await helperRequest(activeConfig, `/v1/extension/tasks/${next.task.taskId}/complete`, {
      method: 'POST',
      body: JSON.stringify({ environmentKey: activeConfig.environmentKey, fillResult }),
    }, session)
    return {
      ok: true,
      message: '任务已填充',
      taskId: next.task.taskId,
      platform: next.task.platform,
      environmentKey: activeConfig.environmentKey,
    }
  } catch (error) {
    const failure = await enrichTaskFailure(activeConfig, next.task, error)
    await apiRequest(activeConfig, `/api/v1/extension/tasks/${next.task.taskId}/fail`, {
      method: 'POST',
      body: JSON.stringify({
        error: failure,
      }),
    }, session.extensionToken).catch(() => {})
    await helperRequest(activeConfig, `/v1/extension/tasks/${next.task.taskId}/fail`, {
      method: 'POST',
      body: JSON.stringify({
        environmentKey: activeConfig.environmentKey,
        error: failure,
      }),
    }, session).catch(() => {})
    throw error
  }
}

async function refreshRuntimeConfig(options = {}) {
  const { config: currentConfig, session } = await getConfig()
  const rawConfig = currentConfig || {}
  if (!session?.extensionToken) return { applied: false, reason: 'not_bound' }
  const activeSession = await refreshExtensionSession(currentConfig, session)
  const query = new URLSearchParams()
  const environmentKeyFilter = rawConfig.environmentKey || options.environmentKey || ''
  const platformFilter = rawConfig.platform || options.platform || ''
  if (environmentKeyFilter) query.set('environmentKey', String(environmentKeyFilter))
  if (platformFilter) query.set('platform', normalizePlatform(platformFilter))
  const path = `/api/v1/extension/runtime-config${query.toString() ? `?${query}` : ''}`
  const runtime = await apiRequest(currentConfig, path, { method: 'GET' }, activeSession.extensionToken)
  await storageSet({ geoEnvRuntimeConfig: { ...runtime, refreshedAt: new Date().toISOString() } })

  const selected = runtime?.selected
  if (!selected) {
    await appendEventLog({
      type: 'runtime_config',
      ok: false,
      reason: options.reason || 'refresh',
      selectionStatus: runtime?.selectionStatus || 'unknown',
      candidateCount: Array.isArray(runtime?.candidates) ? runtime.candidates.length : 0,
      error: runtime?.selectionStatus === 'ambiguous'
        ? '匹配到多个环境账号绑定，已保留当前配置，需人工选择环境或平台'
        : '未匹配到环境账号绑定，已保留当前配置',
    })
    return { applied: false, runtime }
  }

  const decision = runtimeConfigAdoptionDecision(rawConfig, runtime, selected)
  if (!decision.apply) {
    await appendEventLog({
      type: 'runtime_config',
      ok: false,
      reason: options.reason || 'refresh',
      selectionStatus: 'conflict',
      error: decision.reason,
    })
    return { applied: false, runtime, reason: decision.reason }
  }

  const nextConfig = {
    ...rawConfig,
    apiBase: rawConfig.apiBase || currentConfig.apiBase,
    helperBase: rawConfig.helperBase || runtime.helperBase || currentConfig.helperBase,
    brandId: rawConfig.brandId || runtime.brandId || currentConfig.brandId || null,
    environmentKey: selected.environmentKey || rawConfig.environmentKey || currentConfig.environmentKey,
    providerProfileId: selected.providerProfileId || rawConfig.providerProfileId || currentConfig.providerProfileId || null,
    environmentAccountId: selected.browserEnvironmentAccountId || rawConfig.environmentAccountId || null,
    selfMediaAccountId: selected.selfMediaAccountId || rawConfig.selfMediaAccountId || null,
    platform: selected.platform || rawConfig.platform || currentConfig.platform,
    autoRun: rawConfig.autoRun !== false,
  }
  await saveActiveConfig(nextConfig)
  await saveActiveSession(activeSession)
  await appendEventLog({
    type: 'runtime_config',
    ok: true,
    reason: options.reason || 'refresh',
    environmentKey: nextConfig.environmentKey,
    platform: nextConfig.platform,
    status: selected.loginStatus || '-',
  })
  await reportRuntimeStatus({
    reason: options.reason || 'runtime_config',
    environmentKey: nextConfig.environmentKey,
    providerProfileId: nextConfig.providerProfileId,
    platform: nextConfig.platform,
    loginStatus: selected.loginStatus || 'unknown',
    runtimeStage: 'runtime_config_refreshed',
  }).catch(() => null)
  return { applied: true, config: nextConfig, session: activeSession, runtime }
}

function runtimeConfigAdoptionDecision(rawConfig, runtime, selected) {
  const rawEnvironmentKey = String(rawConfig.environmentKey || '').trim()
  const rawPlatform = normalizePlatform(rawConfig.platform || '')
  const selectedEnvironmentKey = String(selected.environmentKey || '').trim()
  const selectedPlatform = normalizePlatform(selected.platform || '')
  if (rawEnvironmentKey && selectedEnvironmentKey && rawEnvironmentKey !== selectedEnvironmentKey) {
    return { apply: false, reason: `当前环境标识=${rawEnvironmentKey}，后台候选=${selectedEnvironmentKey}，不自动覆盖` }
  }
  if (rawPlatform && selectedPlatform && rawPlatform !== selectedPlatform) {
    return { apply: false, reason: `当前平台=${rawPlatform}，后台候选=${selectedPlatform}，不自动覆盖` }
  }
  if (rawConfig.brandId && runtime?.brandId && Number(rawConfig.brandId) !== Number(runtime.brandId)) {
    return { apply: false, reason: `当前品牌=${rawConfig.brandId}，后台绑定品牌=${runtime.brandId}，不自动覆盖` }
  }
  return { apply: true }
}

function classifyTaskFailure(errorOrMessage, platform = '') {
  const text = String(errorOrMessage?.message || errorOrMessage || '')
  const code = errorOrMessage?.code
    || text.match(/^([A-Z0-9_]{3,80})[：:]/)?.[1]
    || classifyTaskFailureCode(text, platform)
  const failure = {
    code,
    message: text || '页面填充失败',
    retryable: isRetryableTaskFailureCode(code),
  }
  if (errorOrMessage?.diagnostics) failure.diagnostics = errorOrMessage.diagnostics
  if (errorOrMessage?.failureSnapshot) failure.failureSnapshot = errorOrMessage.failureSnapshot
  if (errorOrMessage?.extensionVersion) failure.extensionVersion = errorOrMessage.extensionVersion
  return failure
}

async function enrichTaskFailure(config, task, error) {
  const failure = classifyTaskFailure(error, task?.platform)
  failure.extensionVersion = EXTENSION_VERSION
  if (!failure.diagnostics && error?.diagnostics) failure.diagnostics = error.diagnostics
  const tabId = await findActivePlatformTabId(task?.platform).catch(() => null)
  if (tabId) {
    const snapshot = await captureFailureSnapshot(tabId, task?.platform).catch(() => null)
    if (snapshot) failure.failureSnapshot = snapshot
  }
  failure.environmentKey = config?.environmentKey || task?.environmentKey || null
  return failure
}

async function captureFailureSnapshot(tabId, platform) {
  const targetTab = await chrome.tabs.get(tabId).catch(() => null)
  const [activeTab] = await chrome.tabs.query({ active: true, currentWindow: true }).catch(() => [])
  const screenshot = activeTab?.id === tabId
    ? await chrome.tabs.captureVisibleTab(activeTab.windowId, { format: 'jpeg', quality: 45 }).catch(() => '')
    : ''
  const ping = await chrome.tabs.sendMessage(tabId, {
    type: 'GEO_ENV_COLLECT_FAILURE_SNAPSHOT',
    payload: { platform },
  }).catch(() => null)
  return {
    href: targetTab?.url || activeTab?.url || '',
    title: targetTab?.title || activeTab?.title || '',
    screenshotCaptured: Boolean(screenshot),
    screenshotPrefix: screenshot ? screenshot.slice(0, 120) : '',
    page: ping?.ok ? ping.result : null,
  }
}

function classifyTaskFailureCode(text, platform = '') {
  const normalizedPlatform = normalizePlatform(platform)
  if (text.includes('fill token used or expired')) return 'FILL_TOKEN_USED_OR_EXPIRED'
  if (text.includes('作品列表') || text.includes('作品管理页') || text.includes('WORKS_LIST_VERIFY_TIMEOUT')) return 'WORKS_LIST_VERIFY_TIMEOUT'
  if (text.includes('账号不一致') || text.includes('LOGIN_STATUS_MISMATCH') || text.includes('账号身份预检失败')) return 'ACCOUNT_MISMATCH'
  if (text.includes('IDENTITY_EXPECTATION_MISSING')) return 'IDENTITY_EXPECTATION_MISSING'
  if (text.includes('平台定时发布能力')) return 'PLATFORM_CAPABILITY_UNVERIFIED'
  if (text.includes('Material not found') || text.includes('素材不存在')) return 'COVER_MATERIAL_NOT_FOUND'
  if (text.includes('封面图片类型不支持') || text.includes('图片类型不支持')) return 'COVER_IMAGE_UNSUPPORTED'
  const platformCode = classifyPlatformTaskFailureCode(text, normalizedPlatform)
  if (platformCode) return platformCode
  const textMatchedPlatformCode = classifyPlatformTaskFailureCode(text, '')
  if (textMatchedPlatformCode) return textMatchedPlatformCode
  if (text.includes('知乎发布被草稿加载阻塞') || text.includes('知乎草稿加载未完成') || text.includes('草稿加载中')) return 'ZHIHU_DRAFT_LOADING'
  if (text.includes('知乎发布后未检测到完成状态')) return 'ZHIHU_PUBLISH_NOT_SUBMITTED'
  if (text.includes('小红书一键排版按钮未找到')) return 'XIAOHONGSHU_FORMAT_BUTTON_NOT_FOUND'
  if (text.includes('小红书一键排版后未进入排版页') || text.includes('小红书排版页未就绪')) return 'XIAOHONGSHU_FORMAT_NOT_READY'
  if (text.includes('小红书下一步按钮未找到')) return 'XIAOHONGSHU_NEXT_BUTTON_NOT_FOUND'
  if (text.includes('小红书点击下一步后未进入发布设置页') || text.includes('小红书发布设置页未加载完成') || text.includes('小红书发布设置页未就绪')) {
    return 'XIAOHONGSHU_PUBLISH_SETTINGS_NOT_READY'
  }
  if (text.includes('小红书笔记图片生成') || text.includes('小红书图片生成')) return 'XIAOHONGSHU_IMAGE_GENERATION_TIMEOUT'
  if (text.includes('小红书定时发布时间过近')) return 'XIAOHONGSHU_SCHEDULE_TIME_TOO_SOON'
  if (text.includes('小红书定时发布时间过远')) return 'XIAOHONGSHU_SCHEDULE_TIME_TOO_LATE'
  if (text.includes('小红书定时发布时间无效')) return 'XIAOHONGSHU_SCHEDULE_TIME_INVALID'
  if (text.includes('小红书定时发布开关')) return 'XIAOHONGSHU_SCHEDULE_SWITCH_NOT_FOUND'
  if (text.includes('小红书定时发布时间输入框')) return 'XIAOHONGSHU_SCHEDULE_TIME_INPUT_NOT_FOUND'
  if (text.includes('小红书定时发布时间未保持目标值')) return 'XIAOHONGSHU_SCHEDULE_TIME_NOT_APPLIED'
  if (text.includes('小红书定时发布按钮未找到') || text.includes('小红书发布按钮未找到')) return 'XIAOHONGSHU_PUBLISH_BUTTON_NOT_FOUND'
  if (text.includes('小红书发布后未检测到成功状态')) return 'XIAOHONGSHU_PUBLISH_NOT_CONFIRMED'
  if (text.includes('头条定时发布按钮点击后未打开弹窗或预览')) return 'TOUTIAO_SCHEDULE_DIALOG_NOT_OPENED'
  if (text.includes('头条定时发布弹窗时间输入框未找到')
      || text.includes('头条定时发布跨 frame 弹窗时间控件未找到')) {
    return 'TOUTIAO_SCHEDULE_TIME_INPUT_NOT_FOUND'
  }
  if (text.includes('头条定时发布按钮未找到')) return 'TOUTIAO_SCHEDULE_BUTTON_NOT_FOUND'
  if (text.includes('定时发布时间已过期') || text.includes('定时发布时间无效')) return 'SCHEDULE_TIME_INVALID'
  if (text.includes('页面填充执行超时') || text.includes('超时')) return 'PAGE_LOAD_TIMEOUT'
  if (text.includes('未找到') || text.includes('编辑器')) return 'EDITOR_NOT_READY'
  return 'FILL_FAILED'
}

function classifyPlatformTaskFailureCode(text, platform = '') {
  const normalizedPlatform = normalizePlatform(platform)
  if (normalizedPlatform === 'douyin') {
    return globalThis.__GEO_DOUYIN_PLATFORM__?.classifyFailureCode?.(text, 'douyin') || ''
  }
  if (normalizedPlatform === 'zhihu') {
    return globalThis.__GEO_ZHIHU_PLATFORM__?.classifyFailureCode?.(text, 'zhihu') || ''
  }
  if (normalizedPlatform === 'xiaohongshu') {
    return globalThis.__GEO_XIAOHONGSHU_PLATFORM__?.classifyFailureCode?.(text, 'xiaohongshu') || ''
  }
  if (normalizedPlatform === 'baijiahao') {
    return globalThis.__GEO_BAIJIAHAO_PLATFORM__?.classifyFailureCode?.(text, 'baijiahao') || ''
  }
  return globalThis.__GEO_DOUYIN_PLATFORM__?.classifyFailureCode?.(text, '')
    || globalThis.__GEO_ZHIHU_PLATFORM__?.classifyFailureCode?.(text, '')
    || globalThis.__GEO_XIAOHONGSHU_PLATFORM__?.classifyFailureCode?.(text, '')
    || globalThis.__GEO_BAIJIAHAO_PLATFORM__?.classifyFailureCode?.(text, '')
    || ''
}

function isRetryableTaskFailureCode(code) {
  if (globalThis.__GEO_ZHIHU_PLATFORM__?.isRetryableFailureCode?.(code)) return true
  if (globalThis.__GEO_XIAOHONGSHU_PLATFORM__?.isRetryableFailureCode?.(code)) return true
  if (globalThis.__GEO_BAIJIAHAO_PLATFORM__?.isRetryableFailureCode?.(code)) return true
  return [
    'PAGE_LOAD_TIMEOUT',
    'PLATFORM_TAB_GONE',
    'PLATFORM_TAB_REDIRECTED',
    'EDITOR_NOT_READY',
    'COVER_UPLOAD_TIMEOUT',
    'SCHEDULE_DIALOG_NOT_READY',
    'PREVIEW_PAGE_NOT_READY',
    'WORKS_LIST_VERIFY_TIMEOUT',
    'ZHIHU_DRAFT_LOADING',
    'ZHIHU_PUBLISH_NOT_SUBMITTED',
    'LOCAL_HELPER_TEMPORARY_ERROR',
    'FILL_TOKEN_USED_OR_EXPIRED',
  ].includes(code)
}

async function autoPollOnce(reason, senderTabId, options = {}) {
  try {
    const { config, session } = await getConfig()
    if (config.autoRun === false) return { ok: true, skipped: true, reason: 'auto_run_disabled' }
    if (!session?.extensionToken) {
      await setBadge('ERR')
      await appendEventLog({ type: 'auto_fill', ok: false, reason, error: '扩展未绑定后台，无法向本地助手签名领取任务' })
      return { ok: true, skipped: true, reason: 'not_bound' }
    }
    const tab = senderTabId ? await chrome.tabs.get(senderTabId).catch(() => null) : null
    const platform = inferPlatformFromUrl(tab?.url)
    if (shouldSkipPassiveAutoPoll(platform, options.source)) {
      return { ok: true, skipped: true, reason: 'passive_auto_poll_skipped', platform }
    }
    let identityTabId = senderTabId
    let result = await pollOnce({ identityTabId, platform })

    if (!result.taskId) {
      const fallbackTask = await findPendingHelperTaskForOtherPlatform(config, session, platform)
      if (fallbackTask?.platform) {
        const fallbackPlatform = normalizePlatform(fallbackTask.platform)
        identityTabId = await findPlatformTabId(fallbackPlatform)
        result = await pollOnce({ identityTabId, platform: fallbackPlatform })
      }
    }

    await setBadge(result.taskId ? 'OK' : '')
    if (result.taskId) {
      await appendEventLog({ type: 'auto_fill', ok: true, reason, taskId: result.taskId, platform: result.platform })
    }
    return { ...result, auto: true, reason }
  } catch (error) {
    if (isExtensionUnauthorized(error)) {
      await setBadge('ERR')
      await appendEventLog({
        type: 'auto_fill',
        ok: false,
        reason,
        error: '扩展后台绑定已失效，请重新绑定后台',
      })
      return { ok: false, skipped: true, reason: 'extension_binding_expired' }
    }
    await setBadge('ERR')
    await appendEventLog({ type: 'auto_fill', ok: false, reason, error: error.message })
    throw error
  }
}

function shouldSkipPassiveAutoPoll(platform, source) {
  return normalizePlatform(platform) === 'xiaohongshu'
    && source === 'editor_ready'
}

async function findPendingHelperTaskForOtherPlatform(config, session, currentPlatform) {
  const normalizedCurrent = normalizePlatform(currentPlatform)
  const environmentKey = String(config.environmentKey || '').trim()
  if (!environmentKey) return null
  const tasks = await listHelperTasks(config, session)
  return tasks.find((task) => {
    const platform = normalizePlatform(task.platform)
    if (!platform || platform === normalizedCurrent) return false
    if (String(task.environmentKey || '').trim() !== environmentKey) return false
    return isClaimableHelperTaskStatus(task.status)
  }) || null
}

function isClaimableHelperTaskStatus(status) {
  return ['pending', 'requeued'].includes(String(status || '').toLowerCase())
}

async function autoReportLoginStatusFromTab(config, session, tabId, options = {}) {
  if (!tabId || !session?.extensionToken) {
    await appendEventLog({ type: 'login_report', ok: false, platform: options.platform || '-', error: '跳过登录上报：缺少 tab 或后台绑定' })
    return null
  }
  const tab = await chrome.tabs.get(tabId).catch(() => null)
  const platform = options.platform || inferPlatformFromUrl(tab?.url)
  if (!platform || !isAllowedLoginReportUrl(platform, tab?.url)) {
    await appendEventLog({ type: 'login_report', ok: false, platform: platform || '-', error: `跳过登录上报：当前页不允许或无法识别平台，url=${tab?.url || '-'}` })
    return null
  }

  const environmentKey = options.environmentKey || (options.requireTaskEnvironment
    ? ''
    : await resolveLoginReportEnvironmentKey(config, session, platform))
  if (!environmentKey) {
    await appendEventLog({ type: 'login_report', ok: false, platform, error: '跳过登录上报：未解析到环境标识' })
    return null
  }
  const reportConfig = { ...config, environmentKey }
  const throttleKey = `${environmentKey}:${platform}:${tabId}`
  const now = Date.now()
  if (now - (autoLoginReportAtByKey.get(throttleKey) || 0) < 10_000) return null
  autoLoginReportAtByKey.set(throttleKey, now)

  await ensureContentScript(tabId)
  await waitForContentScript(tabId, 8, 500)
  const identity = await readIdentityFromTab(tabId, platform, { requireIdentity: false })
  if (!identity) {
    await appendEventLog({ type: 'login_report', ok: false, platform, environmentKey, error: '跳过登录上报：content-script 未返回身份诊断' })
    return null
  }
  if (!identityHasCandidates(identity)) {
    await appendEventLog({ type: 'login_report', ok: false, platform, environmentKey, error: `跳过登录上报：未读取到平台账号身份，${identity.diagnostics || '-'}` })
    return null
  }
  const status = await reportLoginStatus(reportConfig, session, {
    environmentAccountId: options.environmentAccountId || null,
    environmentKey,
    selfMediaAccountId: options.selfMediaAccountId || null,
    platform,
    identity,
  })
  await appendEventLog({ type: 'login_report', ok: true, platform, environmentKey, status: status?.loginStatus || '-' })
  return status
}

async function resolveCandidateEnvironmentKeys(config, session, platform) {
  const keys = []
  const add = (value) => {
    const key = String(value || '').trim()
    if (key && !keys.includes(key)) keys.push(key)
  }
  add(config.environmentKey)
  return keys
}

async function resolveLoginReportEnvironmentKey(config, session, platform) {
  for (const task of await listHelperTasks(config, session)) {
    if (task.status === 'completed' || task.status === 'cancelled') continue
    if (platform && task.platform && normalizePlatform(task.platform) !== normalizePlatform(platform)) continue
    if (task.environmentKey) return String(task.environmentKey).trim()
  }
  return config.environmentKey || ''
}

async function listHelperTasks(config, session) {
  try {
    const result = await helperRequest(config, '/v1/extension/tasks', {}, session)
    return Array.isArray(result?.tasks) ? result.tasks : []
  } catch {
    return []
  }
}

async function handleTask(config, session, task, options = {}) {
  assertTaskEnvironmentMatchesConfig(config, task)
  const taskApiConfig = task.backendBase ? { ...config, apiBase: task.backendBase } : config
  const precheckedIdentity = await verifyTaskIdentityOnTab(options.identityTabId || null, task)
  await reportTaskLoginStatus(taskApiConfig, session, task, precheckedIdentity).catch(() => {})
  const issued = getPreissuedFillToken(task)
  if (!issued) {
    throw new Error('本地任务缺少本次预签发 fillToken，请重新从后台点击“打开环境并填充”')
  }

  const consumed = await apiRequest(taskApiConfig, '/api/v1/extension/fill-token/consume', {
    method: 'POST',
    body: JSON.stringify({
      fillToken: issued.fillToken,
      platform: task.platform,
      extensionVersion: EXTENSION_VERSION,
    }),
  }, session.extensionToken)

  const payload = parseFillPayload(consumed.fillPayload || consumed)
  if (!payload?.publishUrl) throw new Error('后台未返回 publishUrl')
  assertPlatformUrl(task.platform, payload.publishUrl)

  payload.platform = task.platform
  payload.taskId = task.taskId
  payload.environmentKey = task.environmentKey || config.environmentKey || null
  payload.expectedAccountName = task.expectedAccountName || payload.expectedAccountName || null
  payload.expectedPlatformAccountId = task.expectedPlatformAccountId || payload.expectedPlatformAccountId || null
  const taskScheduledAt = firstText(
    task.platformScheduledAt,
    task.schedule?.platformScheduledAt,
    task.schedule?.plannedPublishAt,
  )
  if (taskScheduledAt) {
    payload.platformScheduledAt = payload.platformScheduledAt || taskScheduledAt
    payload.scheduledAt = payload.scheduledAt || taskScheduledAt
    payload.scheduleRequired = true
  }
  assertExpectedIdentityPresent(payload)
  payload.precheckedIdentity = precheckedIdentity || null

  const tab = await prepareFillTab(options.fillTabId || null, task.platform, payload.publishUrl)
  if (normalizePlatform(task.platform) === 'baijiahao') await delay(1200)
  await waitForPlatformShellReady(tab.id, task.platform)
  let fillResult
  try {
    const fillResponse = await sendFillMessageOnce(tab.id, {
      type: 'GEO_ENV_FILL_TASK',
      payload,
    }, { platform: task.platform })
    fillResult = normalizeFillResult(fillResponse?.result || fillResponse, task)
  } catch (error) {
    try {
      fillResult = normalizeFillResult(
        await recoverPublishAfterFillError(tab.id, task, payload, error),
        task,
      )
    } catch (recoverError) {
      throw normalizeFillTransportError(recoverError, task, payload)
    }
  }

  await apiRequest(taskApiConfig, `/api/v1/extension/tasks/${task.taskId}/ack`, {
    method: 'POST',
    body: JSON.stringify({ fillResult }),
  }, session.extensionToken)
  return fillResult
}

async function waitForPlatformShellReady(tabId, platform) {
  if (normalizePlatform(platform) !== 'douyin') return
  let latest = null
  for (let attempt = 0; attempt < 8; attempt += 1) {
    latest = await inspectDouyinShellState(tabId).catch(() => null)
    if (!latest?.loadingShell) return
    await delay(1200 + attempt * 300)
    if (attempt === 3) {
      await chrome.tabs.reload(tabId, { bypassCache: true }).catch(() => null)
      await waitForTabComplete(tabId, 45_000).catch(() => null)
      await ensureContentScript(tabId).catch(() => null)
    }
  }
  const error = new Error(`DOUYIN_ARTICLE_FORM_NOT_READY：抖音发布页仍在加载中，未出现文章编辑表单；${latest?.diagnostics || ''}`)
  error.code = 'DOUYIN_ARTICLE_FORM_NOT_READY'
  throw error
}

async function inspectDouyinShellState(tabId) {
  const [state] = await executeScriptOnPlatformTab(tabId, {
    target: { tabId, frameIds: [0] },
    func: () => {
      const text = String(document.body?.innerText || document.body?.textContent || '').replace(/\s+/g, '')
      const inputs = Array.from(document.querySelectorAll('input, textarea, [contenteditable="true"]'))
        .filter((el) => {
          const rect = el.getBoundingClientRect()
          const style = getComputedStyle(el)
          return rect.width > 0 && rect.height > 0 && style.visibility !== 'hidden' && style.display !== 'none'
        })
      const loadingShell = location.hostname === 'creator.douyin.com'
        && /加载中|请稍候/.test(text)
        && inputs.length === 0
      return {
        href: location.href,
        inputCount: inputs.length,
        loadingShell,
        diagnostics: `href=${location.href}; inputCount=${inputs.length}; text=${text.slice(0, 160)}`,
      }
    },
  })
  return state?.result || null
}

function assertTaskEnvironmentMatchesConfig(config, task) {
  const configEnvironmentKey = String(config?.environmentKey || '').trim()
  const taskEnvironmentKey = String(task?.environmentKey || '').trim()
  if (!configEnvironmentKey || !taskEnvironmentKey || configEnvironmentKey !== taskEnvironmentKey) {
    const error = new Error(`任务环境不匹配，已阻止填充：当前环境=${configEnvironmentKey || '-'}，任务环境=${taskEnvironmentKey || '-'}`)
    error.code = 'TASK_ENVIRONMENT_MISMATCH'
    throw error
  }
  const configProviderProfileId = String(config?.providerProfileId || '').trim()
  const taskProviderProfileId = String(task?.providerProfileId || '').trim()
  if (configProviderProfileId && taskProviderProfileId && configProviderProfileId !== taskProviderProfileId) {
    const error = new Error(`AdsPower 指纹实例不匹配，已阻止填充：当前 profile=${configProviderProfileId}，任务 profile=${taskProviderProfileId}`)
    error.code = 'TASK_PROVIDER_PROFILE_MISMATCH'
    throw error
  }
}

async function ensureTaskIdentityTab(config, session, task, candidateTabId) {
  if (!requiresIdentityPrecheck(task)) return candidateTabId || null
  const existingTabId = await resolveIdentityPrecheckTabId(candidateTabId, task.platform, true)
  if (existingTabId) {
    await autoReportLoginStatusFromTab(config, session, existingTabId, {
      platform: task.platform,
      environmentKey: task.environmentKey || config.environmentKey,
      environmentAccountId: task.browserEnvironmentAccountId || null,
      selfMediaAccountId: task.selfMediaAccountId || null,
      requireTaskEnvironment: true,
    })
    return existingTabId
  }

  const identityUrl = defaultLoginReportUrl(task.platform)
  if (!identityUrl) {
    throw new Error(`账号身份预检失败：${platformDisplayName(task.platform)}未配置稳定账号身份页`)
  }
  const tab = await chrome.tabs.create({ url: identityUrl, active: true })
  await waitForTabComplete(tab.id, 30_000).catch(() => null)
  await delay(1200)
  await autoReportLoginStatusFromTab(config, session, tab.id, {
    platform: task.platform,
    environmentKey: task.environmentKey || config.environmentKey,
    environmentAccountId: task.browserEnvironmentAccountId || null,
    selfMediaAccountId: task.selfMediaAccountId || null,
    requireTaskEnvironment: true,
  })
  return tab.id
}

function normalizeFillTransportError(error, task = {}, payload = {}) {
  const message = error?.message || String(error || '')
  const platform = normalizePlatform(task?.platform || payload?.platform)
  if (platform === 'douyin' && isMessageChannelClosedError(message)) {
    const normalized = new Error(`抖音填充或发布过程中页面发生跳转，扩展消息通道已关闭；请点击“重新校验”确认平台作品状态。原始错误：${message}`)
    normalized.code = 'DOUYIN_FILL_FAILED'
    normalized.originalMessage = message
    return normalized
  }
  if (isMessageChannelClosedError(message)) {
    const normalized = new Error(`平台页面跳转导致扩展消息通道关闭；请重新校验发布状态。原始错误：${message}`)
    normalized.code = error?.code || 'FILL_MESSAGE_CHANNEL_CLOSED'
    normalized.originalMessage = message
    return normalized
  }
  return error
}

function normalizeFillResult(fillResult, task = {}) {
  return globalThis.__GEO_FILL_RESULT__?.normalizeFillResult
    ? globalThis.__GEO_FILL_RESULT__.normalizeFillResult(fillResult, task)
    : fillResult
}

async function recoverPublishAfterFillError(tabId, task, payload, error) {
  const baijiahaoResult = await recoverBaijiahaoAfterMessageChannelClosed(tabId, task, payload, error).catch((baijiahaoError) => {
    if (baijiahaoError !== error) throw baijiahaoError
    return null
  })
  if (baijiahaoResult) return baijiahaoResult
  const douyinResult = await recoverDouyinPublishAfterMessageChannelClosed(tabId, task, payload, error).catch((douyinError) => {
    if (douyinError !== error) throw douyinError
    return null
  })
  if (douyinResult) return douyinResult
  const zhihuResult = await recoverZhihuPublishAfterMessageChannelClosed(tabId, task, payload, error).catch((zhihuError) => {
    if (zhihuError !== error) throw zhihuError
    return null
  })
  if (zhihuResult) return zhihuResult
  return recoverToutiaoScheduleAfterWorksListTimeout(tabId, task, payload, error)
}

async function recoverBaijiahaoAfterMessageChannelClosed(tabId, task, payload, error) {
  const message = error?.message || String(error || '')
  if (normalizePlatform(task?.platform || payload?.platform) !== 'baijiahao' || !isMessageChannelClosedError(message)) {
    throw error
  }
  await waitForTabComplete(tabId, 45_000).catch(() => null)
  await delay(1500)
  const state = await inspectBaijiahaoPostSubmitTab(tabId)
  if (!state?.submittedLike) throw error
  return {
    titleFilled: true,
    contentFilled: true,
    tagsFilled: false,
    publishOptions: {
      filled: true,
      scheduled: Boolean(payload?.platformScheduledAt || payload?.scheduledAt),
      published: !Boolean(payload?.platformScheduledAt || payload?.scheduledAt),
      publishVerification: state,
      message: `百家号提交后页面跳转导致消息通道关闭，已检测到发布后页面(${state.pageUrl || '-'})，后续由发布回查确认最终状态`,
    },
    recoveredAfterMessageChannelClosed: true,
    messageChannelClosedError: message,
  }
}

async function inspectBaijiahaoPostSubmitTab(tabId) {
  const tab = await chrome.tabs.get(tabId).catch(() => null)
  await ensureContentScript(tabId).catch(() => null)
  const state = await executeScriptOnPlatformTab(tabId, {
    target: { tabId },
    func: () => {
      const text = document.body?.innerText || document.body?.textContent || ''
      const url = location.href
      const postSubmitPath = location.hostname === 'baijiahao.baidu.com'
        && (
          location.pathname.includes('/builder/rc/clue')
          || location.pathname.includes('/builder/rc/content')
          || location.pathname.includes('/builder/rc/home')
        )
      const stillEditor = location.pathname.includes('/builder/rc/edit')
        || /发布|定时发布|存草稿|请输入标题|请输入正文/.test(text)
      const successText = /发布成功|提交成功|已发布|审核中|定时发布|发文成功/.test(text)
      return {
        pageUrl: url,
        title: document.title || '',
        textProbe: text.slice(0, 500),
        submittedLike: postSubmitPath || successText || (location.hostname === 'baijiahao.baidu.com' && !stillEditor),
        postSubmitPath,
        successText,
        stillEditor,
      }
    },
  }).catch(() => null)
  return state?.[0]?.result || {
    pageUrl: tab?.url || '',
    title: tab?.title || '',
    submittedLike: Boolean(tab?.url && /baijiahao\.baidu\.com\/builder\/rc\/(clue|content|home)/.test(tab.url)),
  }
}

async function recoverDouyinPublishAfterMessageChannelClosed(tabId, task, payload, error) {
  const message = error?.message || String(error || '')
  if (normalizePlatform(task?.platform || payload?.platform) !== 'douyin' || !isRecoverableDouyinPublishVerifyError(error, message)) {
    throw error
  }
  const context = buildDouyinManageVerifyContext(payload)
  if (!context.title) throw error

  let latest = null
  let recoveryTabId = tabId
  for (let attempt = 0; attempt < 5; attempt += 1) {
    await delay(900 + attempt * 700)
    await waitForTabComplete(recoveryTabId, 30_000).catch(() => null)
    latest = await inspectDouyinManageTab(recoveryTabId, context, attempt)
    if (latest?.verified) {
      return {
        titleFilled: true,
        contentFilled: true,
        tagsFilled: false,
        publishOptions: {
          filled: true,
          scheduled: Boolean(context.scheduledAt),
          published: !context.scheduledAt,
          publishVerification: latest,
          message: '抖音发布后页面跳转导致消息通道关闭，已通过作品管理页确认发布结果',
        },
        recoveredAfterMessageChannelClosed: true,
        messageChannelClosedError: message,
      }
    }
    if (latest?.isManagePage !== true && attempt < 4) {
      recoveryTabId = await openDouyinManageVerifyTab(recoveryTabId)
      await delay(1600)
      continue
    }
    if (latest?.isManagePage && attempt >= 1 && attempt < 4) {
      await chrome.tabs.reload(recoveryTabId, { bypassCache: true }).catch(() => null)
    }
  }
  const unresolved = new Error(
    `DOUYIN_PUBLISH_NOT_CONFIRMED：抖音页面跳转后已自动打开作品管理页，但暂未匹配到目标作品；`
    + `targetTitle=${context.title}；targetTime=${context.scheduledAt || '立即发布'}；`
    + `diagnostics=${latest?.diagnostics || message}`,
  )
  unresolved.code = 'DOUYIN_PUBLISH_NOT_CONFIRMED'
  unresolved.originalMessage = message
  throw unresolved
}

async function openDouyinManageVerifyTab(tabId) {
  const current = tabId ? await chrome.tabs.get(tabId).catch(() => null) : null
  const tab = current
    ? await chrome.tabs.update(current.id, { url: DOUYIN_MANAGE_URL, active: true }).catch(() => null)
    : null
  const target = tab || await chrome.tabs.create({ url: DOUYIN_MANAGE_URL, active: true })
  if (!target?.id) throw codedError('PLATFORM_TAB_GONE', '无法打开抖音作品管理页进行发布结果恢复校验')
  await waitForTabComplete(target.id, 45_000)
  await delay(1200)
  return target.id
}

function isRecoverableDouyinPublishVerifyError(error, message) {
  const text = String(message || '')
  return isMessageChannelClosedError(text)
    || isWorksListVerifyTimeout(error, text)
    || text.includes('页面填充执行超时')
    || (text.includes('抖音发布后未检测到成功状态') && text.includes('作品管理'))
}

async function recoverZhihuPublishAfterMessageChannelClosed(tabId, task, payload, error) {
  const message = error?.message || String(error || '')
  if (normalizePlatform(task?.platform || payload?.platform) !== 'zhihu' || !isMessageChannelClosedError(message)) {
    throw error
  }
  const verification = await waitForZhihuPublishedTab(tabId, 15000, {
    expectedTitle: payload?.title || payload?.articleTitle || '',
    expectedAccountName: payload?.expectedAccountName || '',
    expectedPlatformAccountId: payload?.expectedPlatformAccountId || '',
  })
  if (!verification?.verified) {
    throw error
  }
  return {
    titleFilled: true,
    contentFilled: true,
    tagsFilled: false,
    publishOptions: {
      filled: true,
      published: true,
      publishVerification: verification,
      message: '知乎发布后页面跳转，已通过标签页状态确认发布',
    },
    recoveredAfterMessageChannelClosed: true,
    messageChannelClosedError: message,
  }
}

function isMessageChannelClosedError(message) {
  const text = String(message || '').toLowerCase()
  return text.includes('message channel closed')
    || text.includes('message port closed')
    || text.includes('receiving end does not exist')
    || text.includes('extension context invalidated')
    || text.includes('asynchronous response') && text.includes('channel closed')
    || text.includes('extension port') && text.includes('back/forward cache')
    || text.includes('no tab with id')
    || text.includes('tab was closed')
}

function isNoReceivingEndError(message) {
  const text = String(message || '')
  return text.includes('Receiving end does not exist')
    || text.includes('Could not establish connection')
    || text.includes('receiving end does not exist')
}

function buildDouyinManageVerifyContext(payload = {}) {
  const platformOptions = payload.platformOptions || {}
  const profileOptions = payload.profile?.platformOptions || {}
  const douyinOptions = payload.douyinOptions || platformOptions.douyin || profileOptions.douyin || {}
  return {
    title: firstText(payload.title, payload.articleTitle).slice(0, 30),
    scheduledAt: firstText(
      payload.scheduledAt,
      payload.platformScheduledAt,
      platformOptions.scheduledAt,
      platformOptions.platformScheduledAt,
      profileOptions.scheduledAt,
      profileOptions.platformScheduledAt,
      douyinOptions.scheduledAt,
      douyinOptions.platformScheduledAt,
    ),
  }
}

async function inspectDouyinManageTab(tabId, context, attempt) {
  const tab = await chrome.tabs.get(tabId).catch(() => null)
  if (!tab?.url || !isAllowedPlatformUrl('douyin', tab.url)) {
    return {
      verified: false,
      isManagePage: false,
      pageUrl: tab?.url || '',
      diagnostics: `attempt=${attempt}; not_douyin_tab`,
    }
  }
  const [state] = await executeScriptOnPlatformTab(tabId, {
    target: { tabId },
    args: [context, attempt],
    func: (context, attempt) => {
      const title = String(context?.title || '').trim().slice(0, 30)
      const scheduledAt = String(context?.scheduledAt || '').trim()
      const normalizeText = (value) => String(value || '').replace(/\s+/g, ' ').trim()
      const normalizeCompact = (value) => normalizeText(value).replace(/\s+/g, '')
      const isManagePage = location.hostname === 'creator.douyin.com' && location.href.includes('/creator-micro/content/manage')
      const pageText = normalizeText(document.body?.innerText || document.body?.textContent || '')
      if (!isManagePage || !title) {
        return {
          verified: false,
          isManagePage,
          pageUrl: location.href,
          pageTitle: document.title,
          diagnostics: `attempt=${attempt}; text=${pageText.slice(0, 220)}`,
        }
      }

      const normalizedTitle = normalizeCompact(title)
      const scheduledVariants = scheduleTextVariants(scheduledAt)
      const candidates = Array.from(document.querySelectorAll('section, article, li, tr, div'))
        .filter(isVisible)
        .map((el) => {
          const rect = el.getBoundingClientRect()
          const text = normalizeText(el.innerText || el.textContent || '')
          return {
            el,
            rect,
            text,
            compactText: normalizeCompact(text),
            hasImage: Array.from(el.querySelectorAll('img')).some(isVisible) || hasBackgroundImage(el),
          }
        })
        .filter((item) => item.text
          && item.rect.width >= 260
          && item.rect.height >= 60
          && item.rect.width <= 1600
          && item.rect.height <= 420
          && !looksLikeWholeManagePage(item.text)
          && item.compactText.includes(normalizedTitle))
        .sort((left, right) => manageRecordScore(right) - manageRecordScore(left))

      const record = candidates.find(isExpectedRecord)
      if (!record) {
        return {
          verified: false,
          isManagePage,
          pageUrl: location.href,
          pageTitle: document.title,
          targetTitle: title,
          scheduledAt,
          diagnostics: candidates.slice(0, 4).map((item) => item.text.slice(0, 160)).join('|') || pageText.slice(0, 260),
        }
      }

      const pageStatus = extractManageStatus(record.text)
      const pageStatusCode = normalizeManageStatus(pageStatus, scheduledAt ? 'scheduled' : 'published')
      const links = extractManageRecordLinks(record.el)
      return {
        verified: true,
        platformStatus: scheduledAt ? 'scheduled' : 'published',
        pageStatusCode,
        pageStatus,
        platformScheduledAt: scheduledAt || null,
        scheduledAtText: extractManageScheduleText(record.text, scheduledVariants) || scheduledAt || null,
        platformPublishId: extractManageRecordPublishId(record.el, links) || null,
        platformPublishedUrl: null,
        coverImageUrl: extractManageRecordImageUrl(record.el) || null,
        recordLinks: links,
        title,
        manageUrl: location.href,
        matchedText: record.text.slice(0, 180),
        refreshed: attempt > 0,
        reloadCount: attempt,
      }

      function isExpectedRecord(item) {
        if (!item.compactText.includes(normalizedTitle)) return false
        if (scheduledAt) {
          if (/已发布|发布成功|审核中/.test(item.text)) return true
          if (!scheduledVariants.some((value) => value && item.compactText.includes(normalizeCompact(value)))) return false
          if (!/定时发布中|定时|修改定时/.test(item.text)) return false
        }
        if (!scheduledAt && /草稿|未通过|删除作品/.test(item.text) && !/审核中|已发布|发布成功/.test(item.text)) return false
        return true
      }

      function manageRecordScore(item) {
        let score = 0
        if (item.compactText.includes(normalizedTitle)) score += 1000
        if (item.hasImage) score += 80
        if (scheduledAt && scheduledVariants.some((value) => value && item.compactText.includes(normalizeCompact(value)))) score += 500
        if (scheduledAt && /定时发布中|修改定时/.test(item.text)) score += 260
        if (!scheduledAt && /审核中|已发布|发布成功/.test(item.text)) score += 220
        if (/播放|点赞|评论|收藏|详情页进入率/.test(item.text)) score += 60
        score -= Math.min(item.text.length, 1200) / 12
        score -= Math.min(item.rect.width * item.rect.height, 1_000_000) / 120_000
        return score
      }

      function scheduleTextVariants(value) {
        const normalized = normalizeScheduleDateTime(value)
        if (!normalized) return []
        const [date, time] = normalized.split(' ')
        const [year, month, day] = date.split('-')
        const looseMonth = String(Number(month))
        const looseDay = String(Number(day))
        return [
          normalized,
          `${year}年${month}月${day}日 ${time}`,
          `${year}年${looseMonth}月${looseDay}日 ${time}`,
          `${month}月${day}日 ${time}`,
          `${looseMonth}月${looseDay}日 ${time}`,
        ]
      }

      function normalizeScheduleDateTime(value) {
        const text = String(value || '').trim().replace('T', ' ')
        const match = text.match(/(\d{4})[-/](\d{1,2})[-/](\d{1,2})\s+(\d{1,2}):(\d{1,2})/)
        if (!match) return ''
        const pad = (num) => String(Number(num)).padStart(2, '0')
        return `${match[1]}-${pad(match[2])}-${pad(match[3])} ${pad(match[4])}:${pad(match[5])}`
      }

      function extractManageStatus(text) {
        return ['定时发布中', '审核中', '已发布', '发布成功', '未通过', '草稿'].find((value) => text.includes(value)) || ''
      }

      function extractManageScheduleText(text, variants) {
        const compact = normalizeCompact(text)
        return variants.find((item) => item && compact.includes(normalizeCompact(item))) || ''
      }

      function normalizeManageStatus(pageStatus, platformStatus) {
        if (/定时发布中|定时/.test(pageStatus)) return 'scheduled'
        if (/审核中/.test(pageStatus)) return 'reviewing'
        if (/已发布|发布成功/.test(pageStatus)) return 'published'
        if (/未通过/.test(pageStatus)) return 'rejected'
        if (/草稿/.test(pageStatus)) return 'draft'
        return platformStatus || ''
      }

      function extractManageRecordLinks(el) {
        return Array.from(el?.querySelectorAll?.('a[href]') || [])
          .map((link) => link.href || link.getAttribute('href') || '')
          .filter(Boolean)
          .slice(0, 8)
      }

      function extractManageRecordPublishedUrl(links) {
        return links.find((href) => /item_id=|aweme_id=|\/video\/|\/note\//.test(href)) || ''
      }

      function extractManageRecordPublishId(el, links) {
        const source = [
          ...links,
          ...Array.from(el?.attributes || []).map((attr) => `${attr.name}=${attr.value}`),
          el?.innerHTML || '',
        ].join(' ')
        const match = source.match(/(?:item_id|aweme_id|group_id|creation_id|itemId|awemeId)[=:]"?([A-Za-z0-9_-]{6,})/)
          || source.match(/\/(?:video|note)\/([A-Za-z0-9_-]{6,})/)
        return match?.[1] || ''
      }

      function extractManageRecordImageUrl(el) {
        const img = Array.from(el?.querySelectorAll?.('img') || []).find(isVisible)
        if (img?.currentSrc || img?.src) return img.currentSrc || img.src
        const withBg = Array.from(el?.querySelectorAll?.('[style*="background-image"]') || []).find((node) => {
          const bg = getComputedStyle(node).backgroundImage || ''
          return isVisible(node) && bg.includes('url(')
        })
        const bg = withBg ? getComputedStyle(withBg).backgroundImage || '' : ''
        return String(bg).match(/url\(["']?([^"')]+)["']?\)/)?.[1] || ''
      }

      function looksLikeWholeManagePage(text) {
        return text.includes('作品管理')
          && text.includes('全部作品')
          && text.includes('已发布')
          && text.includes('审核中')
          && text.length > 260
      }

      function hasBackgroundImage(el) {
        return String(getComputedStyle(el).backgroundImage || '').includes('url(')
      }

      function isVisible(el) {
        if (!el || !el.getBoundingClientRect) return false
        const rect = el.getBoundingClientRect()
        const style = getComputedStyle(el)
        return rect.width > 0 && rect.height > 0 && style.visibility !== 'hidden' && style.display !== 'none'
      }
    },
  }).catch((error) => ([{
    result: {
      verified: false,
      pageUrl: tab.url,
      diagnostics: `attempt=${attempt}; inspect_failed=${error?.message || String(error)}`,
    },
  }]))
  return state?.result || null
}

async function recoverToutiaoScheduleAfterWorksListTimeout(tabId, task, payload, error) {
  const message = error?.message || String(error || '')
  const platform = normalizePlatform(task?.platform || payload?.platform)
  if (platform !== 'toutiao' || (!isWorksListVerifyTimeout(error, message) && !isMessageChannelClosedError(message))) throw error

  const context = buildToutiaoWorksListVerifyContext(payload)
  if (!context.scheduledAt) throw error
  let latest = null
  for (let attempt = 0; attempt < 3; attempt += 1) {
    await chrome.tabs.reload(tabId, { bypassCache: true })
    await delay(300)
    await waitForTabComplete(tabId, 30_000).catch(() => null)
    await delay(1200 + attempt * 800)
    latest = await inspectToutiaoWorksListTab(tabId, context, attempt + 1)
    if (latest?.verified) {
      return {
        titleFilled: true,
        contentFilled: true,
        tagsFilled: false,
        publishOptions: {
          filled: true,
          scheduled: true,
          published: false,
          publishVerification: latest,
          message: isMessageChannelClosedError(message)
            ? `头条发布后页面跳转导致消息通道关闭，刷新${attempt + 1}次后确认定时发布`
            : `头条作品列表首次未命中，刷新${attempt + 1}次后确认定时发布`,
        },
        recoveredAfterWorksListRefresh: true,
        recoveredAfterMessageChannelClosed: isMessageChannelClosedError(message),
        worksListVerifyError: message,
      }
    }
  }
  throw new Error(`WORKS_LIST_VERIFY_TIMEOUT：头条作品列表刷新后仍未匹配到定时文章；target=${context.scheduledAt}；${latest?.diagnostics || message}`)
}

function isWorksListVerifyTimeout(error, message) {
  return error?.code === 'WORKS_LIST_VERIFY_TIMEOUT' || String(message || '').includes('WORKS_LIST_VERIFY_TIMEOUT')
}

function buildToutiaoWorksListVerifyContext(payload = {}) {
  const platformOptions = payload.platformOptions || {}
  const profileOptions = payload.profile?.platformOptions || {}
  const toutiaoOptions = payload.toutiaoOptions || platformOptions.toutiao || profileOptions.toutiao || {}
  return {
    title: firstText(payload.title, payload.articleTitle),
    locationName: firstText(
      payload.locationName,
      payload.location,
      platformOptions.locationName,
      platformOptions.location,
      profileOptions.locationName,
      profileOptions.location,
      toutiaoOptions.locationName,
      toutiaoOptions.location,
    ),
    scheduledAt: firstText(
      payload.scheduledAt,
      payload.platformScheduledAt,
      platformOptions.scheduledAt,
      platformOptions.platformScheduledAt,
      profileOptions.scheduledAt,
      profileOptions.platformScheduledAt,
      toutiaoOptions.scheduledAt,
      toutiaoOptions.platformScheduledAt,
    ),
  }
}

async function inspectToutiaoWorksListTab(tabId, context, refreshAttempt) {
  const tab = await chrome.tabs.get(tabId).catch(() => null)
  if (!tab?.url || !isAllowedPlatformUrl('toutiao', tab.url)) {
    return {
      verified: false,
      pageUrl: tab?.url || '',
      diagnostics: `refreshAttempt=${refreshAttempt}; not_toutiao_tab`,
    }
  }
  const [state] = await executeScriptOnPlatformTab(tabId, {
    target: { tabId },
    args: [context, refreshAttempt],
    func: (context, refreshAttempt) => {
      const normalizeText = (value) => String(value || '').replace(/\s+/g, '')
      const normalizeArticleText = (value) => normalizeText(value)
        .replace(/[《》「」『』"'“”‘’]/g, '')
        .trim()
      const normalizeScheduleValue = (value) => {
        const text = String(value || '').trim()
        const match = text.match(/(\d{4})[-/年](\d{1,2})[-/月](\d{1,2})[日\sT]*(\d{1,2})[:时](\d{1,2})/)
        if (!match) return { full: normalizeText(text), monthDay: '', time: '' }
        const [, year, month, day, hour, minute] = match
        return {
          full: `${year}-${String(Number(month)).padStart(2, '0')}-${String(Number(day)).padStart(2, '0')} ${String(Number(hour)).padStart(2, '0')}:${String(Number(minute)).padStart(2, '0')}`,
          monthDay: `${Number(month)}月${Number(day)}日`,
          time: `${Number(hour)}:${String(Number(minute)).padStart(2, '0')}`,
          hour: Number(hour),
          minute: Number(minute),
        }
      }
      const isVisible = (el) => {
        if (!el || !el.getBoundingClientRect) return false
        const style = window.getComputedStyle(el)
        if (style.display === 'none' || style.visibility === 'hidden' || Number(style.opacity) === 0) return false
        const rect = el.getBoundingClientRect()
        return rect.width > 0 && rect.height > 0
      }
      const isWorksManagePage = () => {
        if (location.hostname !== 'mp.toutiao.com') return false
        if (/\/profile_v\d+\/manage/.test(location.pathname) || location.pathname.includes('/profile_v4/content-manage')) return true
        const text = normalizeText(document.body?.innerText || document.body?.textContent || '')
        return text.includes('作品管理')
          || (text.includes('草稿箱') && (text.includes('已发布') || text.includes('审核中') || text.includes('定时发布中')))
      }
      const collectRows = () => {
        const candidates = Array.from(document.querySelectorAll('li, article, section, div'))
          .filter(isVisible)
          .filter((el) => {
            const rect = el.getBoundingClientRect()
            if (rect.width < 300 || rect.height < 60 || rect.height > 360) return false
            const text = normalizeText(el.textContent || '')
            return text.includes('定时发布中') || text.includes('将于')
          })
        return candidates.filter((candidate) => !candidates.some((other) => other !== candidate && other.contains(candidate)))
      }
      const schedule = normalizeScheduleValue(context.scheduledAt)
      const expectedTitle = normalizeArticleText(context.title || '')
      const titleNeedle = expectedTitle.length > 18 ? expectedTitle.slice(0, 18) : expectedTitle
      const locationName = normalizeText(context.locationName || '')
      const scheduleMatched = (text) => {
        const full = normalizeText(schedule.full)
        const compactFull = full.replace(/[-:]/g, '')
        const compactHourMinute = `${Number(schedule.hour)}时${Number(schedule.minute)}分`
        return text.includes(full)
          || text.includes(compactFull)
          || (schedule.monthDay && text.includes(schedule.monthDay) && (text.includes(schedule.time) || text.includes(compactHourMinute)))
          || (schedule.time && text.includes(schedule.time))
      }
      const extractTitle = (row) => {
        const fallback = String(context.title || '').trim()
        const candidates = Array.from(row.querySelectorAll('a, span, div, h1, h2, h3'))
          .filter(isVisible)
          .map((el) => String(el.textContent || '').trim())
          .filter((text) => text.length >= 8 && text.length <= 80)
          .filter((text) => !text.includes('定时发布中') && !text.includes('查看数据') && !text.includes('查看评论'))
        return candidates[0] || fallback
      }
      const extractScheduleText = (row) => {
        const text = normalizeText(row.textContent || '')
        const index = text.indexOf('将于')
        if (index >= 0) return text.slice(index, Math.min(text.length, index + 32))
        const dateIndex = schedule.monthDay ? text.indexOf(schedule.monthDay) : -1
        if (dateIndex >= 0) return text.slice(Math.max(0, dateIndex - 4), Math.min(text.length, dateIndex + 24))
        return schedule.full
      }
      const rows = collectRows()
      for (const row of rows) {
        const text = normalizeText(row.textContent || '')
        if (!text.includes('定时发布中') && !text.includes('将于')) continue
        if (titleNeedle && !normalizeArticleText(row.textContent || '').includes(titleNeedle)) continue
        if (locationName && !text.includes(locationName)) continue
        if (!scheduleMatched(text)) continue
        return {
          verified: true,
          platformStatus: 'scheduled',
          matchedTitle: extractTitle(row),
          scheduledAtText: extractScheduleText(row),
          locationText: locationName && text.includes(locationName) ? context.locationName : '',
          hasCover: Array.from(row.querySelectorAll('img, [style*="background-image"]')).some(isVisible),
          pageUrl: location.href,
          refreshAttempt,
        }
      }
      const rowText = rows.slice(0, 6).map((row, index) => `${index}:${normalizeText(row.textContent || '').slice(0, 120)}`)
      const body = normalizeText(document.body?.innerText || document.body?.textContent || '').slice(0, 240)
      return {
        verified: false,
        pageUrl: location.href,
        refreshAttempt,
        diagnostics: `refreshAttempt=${refreshAttempt}; worksPage=${isWorksManagePage()}; expectedTitle=${expectedTitle || '-'}; rows=${rowText.join('|') || '-'}; body=${body || '-'}`,
      }
    },
  }).catch(() => [])
  return state?.result || {
    verified: false,
    pageUrl: tab.url,
    diagnostics: `refreshAttempt=${refreshAttempt}; inspect_failed`,
  }
}

async function waitForZhihuPublishedTab(tabId, timeoutMs, context = {}) {
  const deadline = Date.now() + timeoutMs
  let latest = null
  while (Date.now() < deadline) {
    latest = await inspectZhihuPublishedTab(tabId, context)
    if (latest?.verified) return latest
    await delay(500)
  }
  return latest
}

async function inspectZhihuPublishedTab(tabId, context = {}) {
  const tab = await chrome.tabs.get(tabId).catch(() => null)
  const url = tab?.url || ''
  const publishedUrl = normalizeZhihuPublishedUrl(url)
  if (isZhihuPublishedArticleUrl(url)) {
    const pageTitle = normalizeZhihuTitleText(tab?.title || '')
    return {
      verified: true,
      pageUrl: publishedUrl,
      platformPublishedUrl: publishedUrl,
      publishedUrl,
      pageTitle,
      expectedTitle: context.expectedTitle || '',
      titleMatch: matchZhihuPublishedTitle(context.expectedTitle || '', pageTitle, ''),
      account: {
        expectedAccountName: context.expectedAccountName || '',
        expectedPlatformAccountId: context.expectedPlatformAccountId || '',
        accountIds: [],
        accountNames: [],
        profileUrls: [],
        diagnostics: 'recovered from tab url; page script not inspected',
      },
      publishUi: {
        editorStillOpen: false,
        publishButtonVisible: false,
        draftLoadingDialogVisible: false,
      },
      successSignal: {
        publishedUrl: true,
        successText: false,
        reviewText: false,
        publishedAtText: false,
      },
      recoveredFrom: 'tab_url',
    }
  }
  if (!isAllowedPlatformUrl('zhihu', url)) {
    return { verified: false, pageUrl: url, reason: 'not_zhihu_tab' }
  }
  const [state] = await executeScriptOnPlatformTab(tabId, {
    target: { tabId },
    args: [context],
    func: (context) => {
      const normalize = (value) => String(value || '').replace(/\s+/g, '')
      const normalizeTitle = (value) => normalize(value)
        .replace(/[-_—|].*知乎.*/i, '')
        .replace(/^写文章/, '')
        .trim()
      const matchTitle = (expectedTitle, pageTitle, pageText) => {
        const expected = normalizeTitle(expectedTitle || '')
        const actual = normalizeTitle(pageTitle || '')
        if (!expected) return { expected: '', actual, matched: true, method: 'no_expected_title' }
        if (actual && (actual === expected || actual.includes(expected) || expected.includes(actual))) {
          return { expected, actual, matched: true, method: 'page_title' }
        }
        const text = normalizeTitle(pageText || '')
        return { expected, actual, matched: Boolean(text && text.includes(expected)), method: 'page_text' }
      }
      const extractPublishedAt = (value) => {
        const text = String(value || '')
        return text.match(/发布于\d{4}[-年]\d{1,2}[-月]\d{1,2}[^\s。；;，,]{0,16}/)?.[0]
          || text.match(/\d{4}[-年]\d{1,2}[-月]\d{1,2}[^\s。；;，,]{0,16}发布/)?.[0]
          || ''
      }
      const collectIdentity = () => {
        const ids = new Set()
        const names = new Set()
        const profileUrls = new Set()
        const collectProfileHref = (href) => {
          try {
            const url = new URL(href || '', location.href)
            const token = url.pathname.match(/^\/(?:people|org)\/([^/?#]+)/)?.[1]
            if (!token) return
            ids.add(decodeURIComponent(token))
            url.pathname = url.pathname.startsWith('/org/') ? `/org/${token}` : `/people/${token}`
            url.search = ''
            url.hash = ''
            profileUrls.add(url.toString())
          } catch (_) {
            const token = String(href || '').match(/\/(?:people|org)\/([^/?#]+)/)?.[1]
            if (token) ids.add(decodeURIComponent(token))
          }
        }
        for (const link of Array.from(document.querySelectorAll('a[href*="/people/"],a[href*="/org/"]')).slice(0, 80)) {
          collectProfileHref(link.getAttribute('href') || '')
          const imgAlt = link.querySelector('img[alt]')?.getAttribute('alt') || ''
          if (imgAlt && !/^(写文章|发布文章|创作|开始写作|首页|会员|消息|私信|设置|退出|退出登录|知乎|知乎创作助手)$/.test(imgAlt)) names.add(imgAlt)
        }
        const scriptText = Array.from(document.scripts)
          .slice(0, 80)
          .map((script) => script.textContent || '')
          .filter((text) => /(currentUser|viewer|loginUser|urlToken)/i.test(text))
          .join('\n')
          .slice(0, 200000)
        for (const match of scriptText.matchAll(/"urlToken"\s*:\s*"([^"]{2,80})"|"url_token"\s*:\s*"([^"]{2,80})"|"username"\s*:\s*"([^"]{2,80})"/g)) {
          const value = match[1] || match[2] || match[3]
          if (value) ids.add(value)
        }
        for (const match of scriptText.matchAll(/"name"\s*:\s*"([^"]{2,80})"|"nickname"\s*:\s*"([^"]{2,80})"/g)) {
          const value = match[1] || match[2]
          if (value && !/^(写文章|发布文章|创作|开始写作|首页|会员|消息|私信|设置|退出|退出登录|知乎|知乎创作助手)$/.test(value)) names.add(value)
        }
        return {
          expectedAccountName: context.expectedAccountName || '',
          expectedPlatformAccountId: context.expectedPlatformAccountId || '',
          accountIds: Array.from(ids),
          accountNames: Array.from(names),
          profileUrls: Array.from(profileUrls),
          diagnostics: `href=${location.href}; accountIds=${Array.from(ids).join(',') || '-'}; accountNames=${Array.from(names).join(',') || '-'}; profileUrls=${Array.from(profileUrls).join(',') || '-'}`,
        }
      }
      const text = normalize(document.body?.innerText || document.body?.textContent || '')
      const rawText = document.body?.innerText || document.body?.textContent || ''
      const pageTitle = normalizeTitle(document.querySelector('h1')?.textContent || document.title)
      const publishedAtText = extractPublishedAt(rawText)
      const editorStillOpen = location.pathname.startsWith('/write')
        || text.includes('发布设置')
        || text.includes('添加文章封面')
        || text.includes('Markdown语法输入中')
      return {
        href: location.href,
        title: pageTitle,
        expectedTitle: context.expectedTitle || '',
        titleMatch: matchTitle(context.expectedTitle || '', pageTitle, rawText),
        publishedAtText,
        account: collectIdentity(),
        publishUi: {
          editorStillOpen,
          publishButtonVisible: Boolean(Array.from(document.querySelectorAll('button,[role="button"]')).find((el) => normalize(el.textContent) === '发布')),
          draftLoadingDialogVisible: text.includes('草稿加载中'),
        },
        textSample: text.slice(0, 500),
        successText: text.includes('发布成功') || text.includes('审核中') || text.includes('已发布'),
        editorStillOpen,
      }
    },
  }).catch(() => [])
  const result = state?.result || {}
  if (result.successText && !result.editorStillOpen) {
    return {
      verified: true,
      pageUrl: normalizeZhihuPublishedUrl(result.href || url),
      platformPublishedUrl: normalizeZhihuPublishedUrl(result.href || url),
      publishedUrl: normalizeZhihuPublishedUrl(result.href || url),
      pageTitle: result.title || tab?.title || '',
      expectedTitle: result.expectedTitle || context.expectedTitle || '',
      titleMatch: result.titleMatch || null,
      publishedAtText: result.publishedAtText || '',
      account: result.account || null,
      publishUi: result.publishUi || null,
      successSignal: {
        successText: Boolean(result.textSample?.includes?.('发布成功')),
        reviewText: Boolean(result.textSample?.includes?.('审核中')),
        publishedUrl: Boolean(normalizeZhihuPublishedUrl(result.href || url)),
        publishedAtText: Boolean(result.publishedAtText),
      },
      textSample: result.textSample || '',
      recoveredFrom: 'page_text',
    }
  }
  return {
    verified: false,
    pageUrl: result.href || url,
    pageTitle: result.title || tab?.title || '',
    textSample: result.textSample || '',
    reason: result.editorStillOpen ? 'editor_still_open' : 'publish_signal_not_found',
  }
}

function isZhihuPublishedArticleUrl(value) {
  const shared = globalThis.__GEO_ZHIHU_PLATFORM__?.isPublishedArticleUrl?.(value)
  if (typeof shared === 'boolean') return shared
  try {
    const url = new URL(value)
    return (url.hostname === 'zhuanlan.zhihu.com' || url.hostname === 'www.zhihu.com' || url.hostname === 'zhihu.com')
      && (/^\/p\/[^/]+/.test(url.pathname) || /^\/article\/[^/]+/.test(url.pathname))
      && !url.pathname.includes('/write')
      && !url.pathname.endsWith('/edit')
  } catch {
    return false
  }
}

function normalizeZhihuPublishedUrl(value) {
  const shared = globalThis.__GEO_ZHIHU_PLATFORM__?.normalizePublishedUrl?.(value)
  if (shared) return shared
  const raw = String(value || '').trim()
  if (!raw) return ''
  try {
    const url = new URL(raw)
    const match = url.pathname.match(/^\/p\/([^/]+)/)
    if (match) {
      url.pathname = `/p/${match[1]}`
      url.search = ''
      url.hash = ''
      return url.toString()
    }
    const articleMatch = url.pathname.match(/^\/article\/([^/]+)/)
    if (articleMatch) {
      url.pathname = `/article/${articleMatch[1]}`
      url.search = ''
      url.hash = ''
      return url.toString()
    }
    return url.toString()
  } catch {
    return raw.replace(/\/edit(?:[?#].*)?$/, '')
  }
}

function matchZhihuPublishedTitle(expectedTitle, pageTitle, pageText) {
  const shared = globalThis.__GEO_ZHIHU_PLATFORM__?.matchPublishedTitle?.(expectedTitle, pageTitle, pageText)
  if (shared) return shared
  const expected = normalizeZhihuTitleText(expectedTitle)
  const actual = normalizeZhihuTitleText(pageTitle)
  if (!expected) return { expected: '', actual, matched: true, method: 'no_expected_title' }
  if (actual && (actual === expected || actual.includes(expected) || expected.includes(actual))) {
    return { expected, actual, matched: true, method: 'page_title' }
  }
  const text = normalizeZhihuTitleText(pageText || '')
  return { expected, actual, matched: Boolean(text && text.includes(expected)), method: 'page_text' }
}

function normalizeZhihuTitleText(value) {
  const shared = globalThis.__GEO_ZHIHU_PLATFORM__?.normalizeTitleText?.(value)
  if (shared) return shared
  return String(value || '')
    .replace(/\s+/g, '')
    .replace(/[-_—|].*知乎.*/i, '')
    .replace(/^写文章/, '')
    .trim()
}

async function reportTaskLoginStatus(config, session, task, identityCheck) {
  if (!task.browserEnvironmentAccountId || !task.selfMediaAccountId || !task.platform || !identityCheck) return null
  const accountIds = identityCheck.currentAccountIds || []
  const accountNames = identityCheck.currentAccountNames || []
  if (!accountIds.length && !accountNames.length) return null
  return reportLoginStatus(config, session, {
    environmentAccountId: task.browserEnvironmentAccountId,
    environmentKey: task.environmentKey,
    selfMediaAccountId: task.selfMediaAccountId,
    platform: task.platform,
    identity: {
      accountIds,
      accountNames,
      diagnostics: identityCheck.message || '',
    },
  })
}

async function reportActiveTabLoginStatus() {
  const { config, session } = await getConfig()
  if (!session?.extensionToken) throw new Error('请先绑定后台')
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true }).catch(() => [])
  const platform = inferPlatformFromUrl(tab?.url) || config.platform
  const environmentKey = await resolveLoginReportEnvironmentKey(config, session, platform)
  if (!environmentKey || !platform) {
    throw new Error('请先切换到平台页面，或填写环境标识和平台')
  }
  if (!tab?.id || !tab.url || !isAllowedPlatformUrl(platform, tab.url)) {
    throw new Error(`请先切换到 ${platform} 对应的平台页面`)
  }
  if (!isAllowedLoginReportUrl(platform, tab.url)) {
    throw new Error(`请在 ${platformReportPageHint(platform)} 上报登录状态，当前页面不允许用于账号登记`)
  }
  await ensureContentScript(tab.id)
  await waitForContentScript(tab.id, 8, 500)
  const response = await chrome.tabs.sendMessage(tab.id, {
    type: 'GEO_ENV_READ_IDENTITY',
    payload: { platform },
  }, { frameId: 0 })
  if (!response?.ok) throw new Error(response?.error || '读取平台账号身份失败')
  const identity = response.result?.identity || null
  if (!identityHasCandidates(identity)) {
    throw new Error(`未读取到平台账号身份，请打开${platformReportPageHint(platform)}后重试；${identity?.diagnostics || ''}`)
  }
  const reportConfig = { ...config, environmentKey }
  const status = await reportLoginStatus(reportConfig, session, {
    environmentAccountId: null,
    environmentKey,
    selfMediaAccountId: null,
    platform,
    identity,
  })
  await appendEventLog({ type: 'login_report', ok: true, platform, environmentKey, status: status?.loginStatus || '-' })
  return { platform, environmentKey, detectedIdentity: identity, backendStatus: status }
}

function identityHasCandidates(identity) {
  const accountIds = firstArray(identity?.accountIds, identity?.currentAccountIds)
  const accountNames = firstArray(identity?.accountNames, identity?.currentAccountNames)
  return accountIds.length > 0 || accountNames.length > 0
}

async function readIdentityFromTab(tabId, platform, options = {}) {
  let lastIdentity = null
  for (let attempt = 0; attempt < 8; attempt += 1) {
    const response = await chrome.tabs.sendMessage(tabId, {
      type: 'GEO_ENV_READ_IDENTITY',
      payload: { platform },
    }, { frameId: 0 }).catch(() => null)
    if (response?.ok) {
      const identity = response.result?.identity || null
      lastIdentity = identity
      const accountIds = firstArray(identity?.accountIds, identity?.currentAccountIds)
      const accountNames = firstArray(identity?.accountNames, identity?.currentAccountNames)
      if (accountIds.length || accountNames.length) return identity
    }
    await delay(700)
  }
  return options.requireIdentity ? null : lastIdentity
}

async function ensureContentScript(tabId) {
  await requireInjectablePlatformTab(tabId)
  const ping = await chrome.tabs.sendMessage(tabId, { type: 'GEO_ENV_PING' }, { frameId: 0 }).catch(() => null)
  const href = String(ping?.result?.href || '')
  const needsDouyinAdapter = href.includes('creator.douyin.com') && !ping?.result?.adapters?.douyin
  const oldContentScript = ping?.ok && ping.result?.contentScriptVersion && ping.result.contentScriptVersion !== EXTENSION_VERSION
  if (oldContentScript) {
    await chrome.tabs.reload(tabId, { bypassCache: true }).catch(() => null)
    await waitForTabComplete(tabId, 45_000).catch(() => null)
    await delay(1200)
  } else if (ping?.ok && !needsDouyinAdapter) {
    return
  }
  await injectContentScripts(tabId, { allFrames: true })
  await delay(200)
}

async function injectContentScripts(tabId, options = {}) {
  const files = ['fill-result.js', 'identity-policy.js', 'platform-baijiahao.js', 'platform-douyin.js', 'platform-xiaohongshu.js', 'platform-zhihu.js', 'content-script.js']
  await requireInjectablePlatformTab(tabId)
  try {
    await chrome.scripting.executeScript({
      target: { tabId, frameIds: [0] },
      files,
    })
  } catch (error) {
    throw await normalizeTabInjectionError(tabId, error)
  }
  if (!options.allFrames) return
  await chrome.scripting.executeScript({
    target: { tabId, allFrames: true },
    files,
  }).catch((error) => {
    if (!isCannotAccessContentsError(error?.message || error)) throw error
  })
}

function isCannotAccessContentsError(message) {
  const text = String(message || '')
  return text.includes('Cannot access contents of the page')
    || text.includes('Extension manifest must request permission')
}

async function requireInjectablePlatformTab(tabId) {
  const tab = await chrome.tabs.get(tabId).catch(() => null)
  if (!tab) throw codedError('PLATFORM_TAB_GONE', `平台标签页已关闭(tabId=${tabId})`)
  const url = String(tab.url || '')
  if (inferPlatformFromUrl(url)) return tab
  if (/login|signin|passport|sso/i.test(url)) {
    throw codedError('LOGIN_REQUIRED', `平台页面已跳转到登录页：${url || '-'}`)
  }
  throw codedError('PLATFORM_TAB_REDIRECTED', `平台标签页已跳转到不可注入页面：${url || '-'}`)
}

async function normalizeTabInjectionError(tabId, error) {
  const message = error?.message || String(error || '页面脚本注入失败')
  const tab = await chrome.tabs.get(tabId).catch(() => null)
  if (!tab || isNoTabError(message)) {
    return codedError('PLATFORM_TAB_GONE', `平台标签页在脚本注入前已关闭(tabId=${tabId})；原始错误：${message}`)
  }
  const url = String(tab.url || '')
  if (!inferPlatformFromUrl(url)) {
    const code = /login|signin|passport|sso/i.test(url) ? 'LOGIN_REQUIRED' : 'PLATFORM_TAB_REDIRECTED'
    return codedError(code, `平台标签页已跳转，无法注入脚本：${url || '-'}；原始错误：${message}`)
  }
  if (isCannotAccessContentsError(message)) {
    return codedError('EXTENSION_HOST_PERMISSION_DENIED', `扩展无法访问平台页面：${url}；请确认已加载最新扩展包。原始错误：${message}`)
  }
  return error instanceof Error ? error : new Error(message)
}

async function executeScriptOnPlatformTab(tabId, injection) {
  await requireInjectablePlatformTab(tabId)
  try {
    return await chrome.scripting.executeScript(injection)
  } catch (error) {
    throw await normalizeTabInjectionError(tabId, error)
  }
}

function codedError(code, message) {
  const error = new Error(message)
  error.code = code
  return error
}

function isNoTabError(message) {
  return /No tab with id|tab was closed|Invalid tab ID/i.test(String(message || ''))
}

function isPreFillTabLifecycleError(error) {
  const code = String(error?.code || '')
  return code === 'PLATFORM_TAB_GONE' || code === 'PLATFORM_TAB_REDIRECTED' || isNoTabError(error?.message || error)
}

async function reportLoginStatus(config, session, report) {
  const identity = report.identity || {}
  const accountIds = firstArray(identity.accountIds, identity.currentAccountIds)
  const accountNames = firstArray(identity.accountNames, identity.currentAccountNames)
  assertSingleIdentityCandidate(accountIds, accountNames, identity)
  const hasIdentity = accountIds.length > 0 || accountNames.length > 0
  const brandId = session?.brandId || report.brandId || null
  const environmentAccountId = report.environmentAccountId || config.environmentAccountId || null
  const selfMediaAccountId = report.selfMediaAccountId || config.selfMediaAccountId || null
  const path = environmentAccountId
    ? `/api/v1/extension/browser-environment-accounts/${environmentAccountId}/login-status`
    : brandId
      ? `/api/v1/extension/brands/${brandId}/browser-environment-login-status`
      : '/api/v1/extension/browser-environment-login-status'
  const body = {
    selfMediaAccountId,
    platform: report.platform,
    actualPlatformAccountId: accountIds[0] || null,
    actualAccountName: accountNames[0] || null,
    loginStatus: hasIdentity ? 'logged_in' : 'login_required',
    errorCode: hasIdentity ? null : 'IDENTITY_NOT_READ',
    errorMessage: hasIdentity ? null : (identity.diagnostics || '未读取到平台账号身份'),
  }
  if (report.environmentKey) {
    body.environmentKey = report.environmentKey
  }
  const backendStatus = await apiRequest(config, path, {
    method: 'POST',
    body: JSON.stringify(body),
  }, session.extensionToken)
  await reportRuntimeStatus({
    reason: 'login_status',
    environmentKey: report.environmentKey || config.environmentKey,
    platform: report.platform,
    detectedAccountName: accountNames[0] || null,
    detectedPlatformAccountId: accountIds[0] || null,
    loginStatus: backendStatus?.loginStatus || body.loginStatus,
    runtimeStage: 'account_detected',
  }).catch(() => null)
  return backendStatus
}

function assertSingleIdentityCandidate(accountIds, accountNames, identity) {
  if (accountIds.length > 1) {
    throw new Error(`读取到多个候选平台账号ID：${accountIds.join(',')}；${identity.diagnostics || ''}`)
  }
  if (accountIds.length === 0 && accountNames.length > 1) {
    throw new Error(`读取到多个候选账号名称：${accountNames.join(',')}；${identity.diagnostics || ''}`)
  }
}

function firstArray(...values) {
  for (const value of values) {
    if (Array.isArray(value)) return value
  }
  return []
}

function getPreissuedFillToken(task) {
  const backendTask = task?.backendTask || null
  if (!backendTask?.fillToken) return null
  return {
    fillToken: backendTask.fillToken,
    expiresAt: backendTask.fillTokenExpiresAt || null,
    nonce: backendTask.fillTokenNonce || null,
    preissued: true,
  }
}

function parseFillPayload(value) {
  if (typeof value === 'string') return JSON.parse(value)
  return value || null
}

async function resolveFillTab(candidateTabId, platform, publishUrl) {
  const candidate = candidateTabId ? await chrome.tabs.get(candidateTabId).catch(() => null) : null
  if (candidate?.id && candidate.url && isReusablePublishTab(platform, candidate.url, publishUrl)) {
    if (shouldNormalizeReusablePublishTabUrl(platform, candidate.url, publishUrl)) {
      return chrome.tabs.update(candidate.id, { url: publishUrl, active: true })
    }
    await chrome.tabs.update(candidate.id, { active: true })
    return candidate
  }
  return chrome.tabs.create({ url: publishUrl, active: true })
}

async function prepareFillTab(candidateTabId, platform, publishUrl) {
  let tab = await resolveFillTab(candidateTabId, platform, publishUrl)
  try {
    await waitForTabComplete(tab.id, 45_000)
    await waitForFillContentScriptReadyWithRecovery(tab.id, platform, 30_000)
    return tab
  } catch (error) {
    if (!isPreFillTabLifecycleError(error)) throw error
  }

  tab = await chrome.tabs.create({ url: publishUrl, active: true })
  await waitForTabComplete(tab.id, 45_000)
  await waitForFillContentScriptReadyWithRecovery(tab.id, platform, 30_000)
  return tab
}

function isReusablePublishTab(platform, currentUrlValue, publishUrlValue) {
  if (!isAllowedPlatformUrl(platform, currentUrlValue)) return false
  const currentUrl = new URL(currentUrlValue)
  const publishUrl = new URL(publishUrlValue)
  if (currentUrl.hostname !== publishUrl.hostname) return false
  if (platform === 'toutiao') return currentUrl.pathname.includes('/graphic/publish')
  if (platform === 'zhihu') return currentUrl.pathname.startsWith('/write')
  if (platform === 'xiaohongshu') return currentUrl.pathname.includes('/publish/publish')
  return currentUrl.pathname === publishUrl.pathname
}

function shouldNormalizeReusablePublishTabUrl(platform, currentUrlValue, publishUrlValue) {
  if (normalizePlatform(platform) !== 'xiaohongshu') return false
  const currentUrl = new URL(currentUrlValue)
  const publishUrl = new URL(publishUrlValue)
  return currentUrl.pathname === publishUrl.pathname
    && currentUrl.search !== publishUrl.search
    && publishUrl.searchParams.get('target') === 'article'
}

async function verifyTaskIdentityOnTab(tabId, task) {
  const requiresPrecheck = requiresIdentityPrecheck(task)
  const identityTabId = await resolveIdentityPrecheckTabId(tabId, task.platform, requiresPrecheck)
  if (!identityTabId) {
    if (requiresPrecheck) {
      const error = new Error(`账号名称未预检：未找到 ${platformReportPageHint(task.platform)}，已阻止填充`)
      error.code = 'TASK_ACCOUNT_IDENTITY_NOT_CONFIRMED'
      throw error
    }
    return null
  }
  const tab = await chrome.tabs.get(identityTabId).catch(() => null)
  if (!tab?.url || !isAllowedLoginReportUrl(task.platform, tab.url)) {
    if (requiresPrecheck) {
      const error = new Error(`账号名称未预检：当前页面不是 ${platformReportPageHint(task.platform)}，已阻止填充`)
      error.code = 'TASK_ACCOUNT_IDENTITY_NOT_CONFIRMED'
      throw error
    }
    return null
  }
  await ensureContentScript(identityTabId)
  await waitForContentScript(identityTabId, 8, 500)
  try {
    const response = await chrome.tabs.sendMessage(identityTabId, {
      type: 'GEO_ENV_CHECK_IDENTITY',
      payload: {
        platform: task.platform,
        expectedAccountName: task.expectedAccountName || null,
        expectedPlatformAccountId: task.expectedPlatformAccountId || null,
      },
    }, { frameId: 0 })
    if (!response?.ok) throw new Error(response?.error || '账号身份校验失败')
    if (response.result?.warning) {
      const error = new Error(response.result.message || '账号身份未确认，已阻止填充')
      error.code = 'TASK_ACCOUNT_IDENTITY_MISMATCH'
      throw error
    }
    return response.result
  } catch (error) {
    const normalized = error instanceof Error ? error : new Error(String(error || '账号身份校验失败'))
    normalized.code = normalized.code || 'TASK_ACCOUNT_IDENTITY_NOT_CONFIRMED'
    throw normalized
  }
}

async function resolveIdentityPrecheckTabId(candidateTabId, platform, requiresPrecheck) {
  if (!requiresPrecheck) return candidateTabId || null
  const candidate = candidateTabId ? await chrome.tabs.get(candidateTabId).catch(() => null) : null
  if (candidate?.id && candidate.url && isAllowedLoginReportUrl(platform, candidate.url)) {
    return candidate.id
  }

  const tabs = await chrome.tabs.query({}).catch(() => [])
  const existing = tabs.find((tab) => tab.id && tab.url && isAllowedLoginReportUrl(platform, tab.url))
  if (existing?.id) return existing.id

  const identityUrl = defaultLoginReportUrl(platform)
  if (identityUrl) {
    const tab = await chrome.tabs.create({ url: identityUrl, active: true })
    await waitForTabComplete(tab.id, 30_000).catch(() => null)
    await delay(1200)
    return tab.id || null
  }

  return null
}

function requiresIdentityPrecheck(task) {
  if (!IDENTITY_PRECHECK_PLATFORMS.has(task.platform)) return false
  return Boolean(task.expectedAccountName || task.expectedPlatformAccountId)
}

function assertExpectedIdentityPresent(payload) {
  const platform = normalizePlatform(payload.platform)
  if (!IDENTITY_PRECHECK_PLATFORMS.has(platform)) return
  if (!payload.expectedAccountName) {
    const error = new Error('IDENTITY_EXPECTATION_MISSING：任务缺少 expectedAccountName，已阻止填充以避免账号串门')
    error.code = 'IDENTITY_EXPECTATION_MISSING'
    throw error
  }
}

async function runSelfTest() {
  const checks = []
  const { config, session } = await getConfig()
  const runtimeRefresh = await refreshRuntimeConfig({ reason: 'self_test' }).catch((error) => ({ error }))

  checks.push({
    name: 'extension_bound',
    ok: Boolean(session?.extensionToken),
    detail: session?.extensionToken ? `sessionId=${session.sessionId || '-'}` : '未绑定后台',
  })

  try {
    const health = await fetch(`${normalizeBaseUrl(config.helperBase)}/health`)
      .then((response) => response.json())
    checks.push({
      name: 'local_helper_health',
      ok: Boolean(health?.ok),
      detail: `paired=${Boolean(health?.paired)}, version=${health?.version || '-'}, build=${health?.buildRevision || '-'}`,
    })
    const compatibleBuild = health?.version === EXTENSION_VERSION
      && health?.buildRevision === EXTENSION_BUILD_REVISION
    checks.push({
      name: 'extension_helper_build_match',
      ok: compatibleBuild,
      detail: `extension=${EXTENSION_VERSION}/${EXTENSION_BUILD_REVISION}, helper=${health?.version || '-'}/${health?.buildRevision || '-'}`,
    })
  } catch (error) {
    checks.push({
      name: 'local_helper_health',
      ok: false,
      error: `本地助手不可访问：${error.message}`,
    })
    checks.push({
      name: 'extension_helper_build_match',
      ok: false,
      error: '无法读取本地助手版本与构建标识',
    })
  }

  if (session?.extensionToken) {
    try {
      await signedHelperHeaders(
        config,
        `/v1/extension/tasks/next?environmentKey=${encodeURIComponent(config.environmentKey || '')}`,
        {},
        session,
      )
      checks.push({ name: 'local_agent_sign', ok: true })
    } catch (error) {
      checks.push({
        name: 'local_agent_sign',
        ok: false,
        error: error.message,
      })
    }
  }

  checks.push({
    name: 'runtime_config',
    ok: Boolean(runtimeRefresh?.applied || runtimeRefresh?.runtime?.selected),
    detail: runtimeRefresh?.runtime
      ? `selection=${runtimeRefresh.runtime.selectionStatus || '-'}, candidates=${runtimeRefresh.runtime.candidates?.length || 0}`
      : runtimeRefresh?.reason || '',
    error: runtimeRefresh?.error?.message,
  })

  for (const platform of IDENTITY_PRECHECK_PLATFORMS) {
    try {
      assertExpectedIdentityPresent({ platform })
      checks.push({ name: `missing_expected_rejected:${platform}`, ok: false, error: '未拒绝缺 expected 任务' })
    } catch (error) {
      checks.push({
        name: `missing_expected_rejected:${platform}`,
        ok: error.message.includes('IDENTITY_EXPECTATION_MISSING'),
        error: error.message,
      })
    }
  }

  checks.push({
    name: 'service_worker_memory_lock_removed',
    ok: true,
    detail: 'auto run concurrency is delegated to local-helper task claim state',
  })

  const failed = checks.filter((check) => !check.ok)
  if (failed.length) {
    const error = new Error(`安全自检失败：${failed.map((item) => item.name).join(', ')}`)
    error.checks = checks
    throw error
  }
  return checks
}

function assertPlatformUrl(platform, urlValue) {
  const url = new URL(urlValue)
  if (!isAllowedPlatformHost(platform, url.hostname)) {
    throw new Error(`平台 ${platform} 的填充地址不允许：${url.hostname}`)
  }
}

function isAllowedPlatformUrl(platform, urlValue) {
  try {
    const url = new URL(urlValue)
    return isAllowedPlatformHost(normalizePlatform(platform), url.hostname)
  } catch {
    return false
  }
}

function isAutoPollReadyUrl(urlValue) {
  try {
    const url = new URL(urlValue)
    const platform = inferPlatformFromUrl(urlValue)
    if (platform === 'toutiao') {
      return url.hostname === 'mp.toutiao.com' && url.pathname.includes('/graphic/publish')
    }
    if (platform === 'zhihu') {
      return (url.hostname === 'www.zhihu.com' || url.hostname === 'zhihu.com' || url.hostname === 'zhuanlan.zhihu.com')
        && url.pathname.startsWith('/write')
    }
    if (platform === 'xiaohongshu') {
      return url.hostname === 'creator.xiaohongshu.com'
        && url.pathname.includes('/publish/publish')
        && url.searchParams.get('published') !== 'true'
    }
    if (platform === 'baijiahao') {
      return url.hostname === 'baijiahao.baidu.com' && url.pathname.includes('/builder/rc/edit')
    }
    if (platform === 'douyin') {
      return url.hostname === 'creator.douyin.com' && isDouyinPublishPath(url)
    }
    return false
  } catch {
    return false
  }
}

function isAllowedLoginReportUrl(platform, urlValue) {
  try {
    const url = new URL(urlValue)
    const normalizedPlatform = normalizePlatform(platform)
    if (normalizedPlatform === 'toutiao') {
      return url.hostname === 'mp.toutiao.com'
        && !isToutiaoArticlePreviewUrl(url)
        && !url.pathname.includes('/graphic/publish')
    }
    if (normalizedPlatform === 'zhihu') {
      return isZhihuIdentityUrl(url)
    }
    if (normalizedPlatform === 'xiaohongshu') {
      return url.hostname === 'creator.xiaohongshu.com'
    }
    if (normalizedPlatform === 'baijiahao') {
      return url.hostname === 'baijiahao.baidu.com'
        && !url.pathname.includes('/builder/rc/edit')
    }
    if (normalizedPlatform === 'douyin') {
      return url.hostname === 'creator.douyin.com'
        && isDouyinIdentityUrl(url)
    }
    return false
  } catch {
    return false
  }
}

function isDouyinPublishPath(url) {
  return url.pathname.includes('/creator-micro/content/upload')
    || url.pathname.includes('/creator-micro/content/post/article')
}

function isDouyinIdentityUrl(url) {
  return url.pathname === '/creator-micro/home'
    || url.pathname === '/creator-micro/home/'
}

function isToutiaoArticlePreviewUrl(url) {
  return url.hostname === 'mp.toutiao.com' && url.pathname.includes('/mp-article-preview/')
}

function isZhihuCreatorCenterUrl(url) {
  return (url.hostname === 'www.zhihu.com' || url.hostname === 'zhihu.com')
    && url.pathname.startsWith('/creator/')
}

function isZhihuIdentityUrl(url) {
  return isZhihuCreatorCenterUrl(url)
    || ((url.hostname === 'www.zhihu.com' || url.hostname === 'zhihu.com')
      && url.pathname.startsWith('/organization/verify/'))
}

function inferPlatformFromUrl(urlValue) {
  try {
    const url = new URL(urlValue)
    if (url.hostname === 'mp.toutiao.com') return 'toutiao'
    if (url.hostname === 'www.zhihu.com' || url.hostname === 'zhihu.com' || url.hostname === 'zhuanlan.zhihu.com') return 'zhihu'
    if (url.hostname === 'creator.xiaohongshu.com' || url.hostname === 'www.xiaohongshu.com') return 'xiaohongshu'
    if (url.hostname === 'baijiahao.baidu.com') return 'baijiahao'
    if (url.hostname === 'creator.douyin.com') return 'douyin'
    return ''
  } catch {
    return ''
  }
}

function platformReportPageHint(platform) {
  const normalizedPlatform = normalizePlatform(platform)
  const hints = {
    toutiao: '头条设置页(mp.toutiao.com/profile_v4/personal/info)',
    zhihu: '知乎创作中心或企业认证页(www.zhihu.com/organization/verify/levelup)',
    xiaohongshu: '小红书创作者主页(creator.xiaohongshu.com/new/home?source=official)',
    baijiahao: '百家号个人中心页(baijiahao.baidu.com/builder/rc/settings/accountSet)',
    douyin: '抖音创作者中心首页(creator.douyin.com/creator-micro/home)',
  }
  return hints[normalizedPlatform] || `${platform || '对应平台'}后台页`
}

function platformDisplayName(platform) {
  const normalizedPlatform = normalizePlatform(platform)
  const names = {
    toutiao: '头条',
    zhihu: '知乎',
    xiaohongshu: '小红书',
    baijiahao: '百家号',
    douyin: '抖音',
  }
  return names[normalizedPlatform] || '平台'
}

function defaultLoginReportUrl(platform) {
  const normalizedPlatform = normalizePlatform(platform)
  if (normalizedPlatform === 'toutiao') return 'https://mp.toutiao.com/profile_v4/personal/info'
  if (normalizedPlatform === 'zhihu') return globalThis.__GEO_ZHIHU_PLATFORM__?.CREATOR_CENTER_URL || 'https://www.zhihu.com/creator/manage/creation/article'
  if (normalizedPlatform === 'xiaohongshu') return 'https://creator.xiaohongshu.com/new/home?source=official'
  if (normalizedPlatform === 'baijiahao') return 'https://baijiahao.baidu.com/builder/rc/settings/accountSet'
  if (normalizedPlatform === 'douyin') return 'https://creator.douyin.com/creator-micro/home'
  return null
}

function isAllowedPlatformHost(platform, host) {
  const normalizedPlatform = normalizePlatform(platform)
  const allowed = {
    toutiao: ['mp.toutiao.com'],
    zhihu: ['zhuanlan.zhihu.com', 'www.zhihu.com', 'zhihu.com'],
    xiaohongshu: ['creator.xiaohongshu.com', 'www.xiaohongshu.com'],
    baijiahao: ['baijiahao.baidu.com'],
    douyin: ['creator.douyin.com'],
  }
  const hosts = allowed[normalizedPlatform] || []
  return hosts.includes(host)
}

function normalizePlatform(value) {
  const text = String(value || '').trim().toLowerCase()
  const aliases = {
    '头条': 'toutiao',
    '今日头条': 'toutiao',
    'toutiao': 'toutiao',
    '知乎': 'zhihu',
    'zhihu': 'zhihu',
    '小红书': 'xiaohongshu',
    'xiaohongshu': 'xiaohongshu',
    'xhs': 'xiaohongshu',
    '百家号': 'baijiahao',
    'baijiahao': 'baijiahao',
    '抖音': 'douyin',
    'douyin': 'douyin',
  }
  return aliases[text] || text
}

async function findActivePlatformTabId(platform) {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true }).catch(() => [])
  if (tab?.id && tab.url && isAllowedPlatformUrl(platform, tab.url)) return tab.id
  return null
}

async function findPlatformTabId(platform) {
  const activeTabId = await findActivePlatformTabId(platform)
  if (activeTabId) return activeTabId
  const tabs = await chrome.tabs.query({}).catch(() => [])
  const existing = tabs.find((tab) => tab.id && tab.url && isAllowedPlatformUrl(platform, tab.url))
  return existing?.id || null
}

function waitForTabComplete(tabId, timeoutMs) {
  return new Promise((resolve, reject) => {
    let settled = false
    let timeout = null
    function finish(error) {
      if (settled) return
      settled = true
      if (timeout) clearTimeout(timeout)
      chrome.tabs.onUpdated.removeListener(listener)
      chrome.tabs.onRemoved.removeListener(removedListener)
      if (error) reject(error)
      else resolve()
    }

    chrome.tabs.get(tabId, (tab) => {
      if (chrome.runtime.lastError || !tab) {
        finish(codedError('PLATFORM_TAB_GONE', `平台标签页不存在(tabId=${tabId})`))
        return
      }
      if (tab?.status === 'complete') finish()
    })

    timeout = setTimeout(() => {
      finish(new Error('编辑页加载超时'))
    }, timeoutMs)

    function listener(updatedTabId, changeInfo) {
      if (updatedTabId !== tabId || changeInfo.status !== 'complete') return
      finish()
    }

    function removedListener(removedTabId) {
      if (removedTabId !== tabId) return
      finish(codedError('PLATFORM_TAB_GONE', `平台标签页在等待加载时已关闭(tabId=${tabId})`))
    }

    chrome.tabs.onUpdated.addListener(listener)
    chrome.tabs.onRemoved.addListener(removedListener)
  })
}

async function waitForContentScript(tabId, attempts, delayMs) {
  let lastError = null
  for (let attempt = 0; attempt < attempts; attempt += 1) {
    try {
      const response = await chrome.tabs.sendMessage(tabId, { type: 'GEO_ENV_PING' }, { frameId: 0 })
      if (response?.ok) return response
    } catch (error) {
      lastError = error
      await new Promise((resolve) => setTimeout(resolve, delayMs))
    }
  }
  throw lastError || new Error('页面脚本未就绪')
}

async function waitForFillContentScriptReady(tabId, timeoutMs = 30_000) {
  const deadline = Date.now() + timeoutMs
  let lastError = null
  while (Date.now() < deadline) {
    try {
      await ensureContentScript(tabId)
      const response = await chrome.tabs.sendMessage(tabId, { type: 'GEO_ENV_PING' }, { frameId: 0 })
      if (response?.ok) return response
    } catch (error) {
      lastError = error
    }
    await delay(400)
  }
  throw lastError || new Error('页面脚本未就绪')
}

async function waitForFillContentScriptReadyWithRecovery(tabId, platform, timeoutMs = 30_000) {
  try {
    return await waitForFillContentScriptReady(tabId, timeoutMs)
  } catch (error) {
    if (!isNoReceivingEndError(error?.message || error)) throw error
    if (normalizePlatform(platform) === 'baijiahao') {
      await chrome.tabs.reload(tabId, { bypassCache: true }).catch(() => null)
      await waitForTabComplete(tabId, 45_000).catch(() => null)
      await delay(1800)
    }
    await ensureContentScript(tabId)
    return waitForFillContentScriptReady(tabId, 15_000)
  }
}

async function sendFillMessageOnce(tabId, message, options = {}) {
  let response
  const timeoutMs = fillMessageTimeoutMs(options.platform)
  try {
    response = await withTimeout(
      chrome.tabs.sendMessage(tabId, message),
      timeoutMs,
      '页面填充执行超时，请检查定时发布弹窗或平台页面是否阻塞',
    )
  } catch (error) {
    if (!options.channelRecovered && isRecoverableFillChannelError(error?.message || error, options.platform)) {
      return retryFillMessageAfterChannelRecovery(tabId, message, options, error)
    }
    if (!isNoReceivingEndError(error?.message || error)) {
      throw await enrichFillMessageError(tabId, options.platform, error)
    }
    await waitForFillContentScriptReadyWithRecovery(tabId, options.platform, 20_000)
    try {
      response = await withTimeout(
        chrome.tabs.sendMessage(tabId, message),
        timeoutMs,
        '页面填充执行超时，请检查定时发布弹窗或平台页面是否阻塞',
      )
    } catch (retryError) {
      throw await enrichFillMessageError(tabId, options.platform, retryError)
    }
  }
  if (!response?.ok) {
    const error = new Error(response?.error || '页面填充失败')
    if (response?.failureCode) error.code = response.failureCode
    if (response?.diagnostics) error.diagnostics = response.diagnostics
    error.extensionVersion = EXTENSION_VERSION
    throw error
  }
  return response
}

function fillMessageTimeoutMs(platform) {
  if (normalizePlatform(platform) === 'xiaohongshu') return 150_000
  return 90_000
}

function isRecoverableFillChannelError(message, platform) {
  return normalizePlatform(platform) === 'xiaohongshu'
    && isMessageChannelClosedError(message)
}

async function retryFillMessageAfterChannelRecovery(tabId, message, options, originalError) {
  await waitForTabComplete(tabId, 45_000).catch(() => null)
  await delay(1200)
  await waitForFillContentScriptReadyWithRecovery(tabId, options.platform, 20_000)
  try {
    return await sendFillMessageOnce(tabId, message, { ...options, channelRecovered: true })
  } catch (retryError) {
    retryError.originalMessage = originalError?.message || String(originalError || '')
    throw retryError
  }
}

async function enrichFillMessageError(tabId, platform, error) {
  const message = error?.message || String(error || '')
  if (!message.includes('页面填充执行超时') && !message.includes('超时')) return error
  const snapshot = await captureFailureSnapshot(tabId, platform).catch(() => null)
  if (!snapshot) return error
  const page = snapshot.page || {}
  const activeFillTask = page.activeFillTask || null
  const stageText = activeFillTask?.stage ? `；stage=${activeFillTask.stage}` : ''
  const diagnosticsText = page.diagnostics ? `；${String(page.diagnostics).slice(0, 800)}` : ''
  const enriched = new Error(`${message}${stageText}${diagnosticsText}`)
  enriched.code = error?.code || 'PAGE_LOAD_TIMEOUT'
  enriched.diagnostics = page
  enriched.failureSnapshot = snapshot
  enriched.extensionVersion = EXTENSION_VERSION
  return enriched
}

async function fillToutiaoScheduleAcrossFrames(tabId, value, platform) {
  if (!tabId) throw new Error('跨 frame 设置定时发布缺少 tabId')
  await ensureContentScript(tabId)
  await injectContentScripts(tabId, { allFrames: true }).catch(() => {})
  await delay(200)

  const frames = await executeScriptOnPlatformTab(tabId, {
    target: { tabId, allFrames: true },
    func: () => {
      const text = String(document.body?.innerText || document.body?.textContent || '').replace(/\s+/g, '')
      const hasScheduleDialog = text.includes('定时发布')
        && (text.includes('请选择当前时间后') || text.includes('本文将于北京时间') || text.includes('预览并定时发布'))
      const hasScheduleControls = /月\d{1,2}日/.test(text) && text.includes('时') && text.includes('分')
      return {
        href: location.href,
        title: document.title,
        hasScheduleDialog,
        hasScheduleControls,
        text: text.slice(0, 160),
      }
    },
  }).catch((error) => {
    if (!isCannotAccessContentsError(error?.message || error)) throw error
    return executeScriptOnPlatformTab(tabId, {
      target: { tabId, frameIds: [0] },
      func: () => {
        const text = String(document.body?.innerText || document.body?.textContent || '').replace(/\s+/g, '')
        return {
          href: location.href,
          title: document.title,
          hasScheduleDialog: text.includes('定时发布'),
          hasScheduleControls: /月\d{1,2}日/.test(text) && text.includes('时') && text.includes('分'),
          text: text.slice(0, 160),
        }
      },
    })
  })
  const frame = frames
    .filter((item) => item?.result?.hasScheduleDialog || item?.result?.hasScheduleControls)
    .sort((left, right) => Number(Boolean(right.result?.hasScheduleDialog)) - Number(Boolean(left.result?.hasScheduleDialog)))[0]
  if (!frame) {
    const diagnostic = frames.map((item) => `${item.frameId}:${item.result?.text || '-'}`).join('|').slice(0, 600)
    throw new Error(`头条定时发布弹窗 frame 未找到；frames=${diagnostic}`)
  }
  const response = await withTimeout(
    chrome.tabs.sendMessage(tabId, {
      type: 'GEO_ENV_FILL_TOUTIAO_SCHEDULE_FRAME',
      value,
      platform,
    }, { frameId: frame.frameId }),
    30_000,
    '头条定时发布跨 frame 设置超时',
  )
  if (!response?.ok) throw new Error(response?.error || '头条定时发布跨 frame 设置失败')
  return response.result || response
}

function withTimeout(promise, timeoutMs, message) {
  let timeout = null
  const timer = new Promise((_, reject) => {
    timeout = setTimeout(() => reject(new Error(message)), timeoutMs)
  })
  return Promise.race([promise, timer]).finally(() => {
    if (timeout) clearTimeout(timeout)
  })
}

async function dispatchTrustedClick(tabId, click) {
  if (!tabId || !Number.isFinite(click?.clientX) || !Number.isFinite(click?.clientY)) {
    throw new Error('真实点击参数不完整')
  }
  const tab = await chrome.tabs.get(tabId).catch(() => null)
  if (!tab?.url
      || (!isAllowedPlatformUrl('xiaohongshu', tab.url)
        && !isAllowedPlatformUrl('toutiao', tab.url)
        && !isAllowedPlatformUrl('zhihu', tab.url)
        && !isAllowedPlatformUrl('baijiahao', tab.url))) {
    throw new Error('真实点击仅允许用于小红书、头条、知乎或百家号页面')
  }
  const target = { tabId }
  await chrome.debugger.attach(target, '1.3')
  try {
    await chrome.debugger.sendCommand(target, 'Input.dispatchMouseEvent', {
      type: 'mouseMoved',
      x: click.clientX,
      y: click.clientY,
      button: 'none',
    })
    await chrome.debugger.sendCommand(target, 'Input.dispatchMouseEvent', {
      type: 'mousePressed',
      x: click.clientX,
      y: click.clientY,
      button: 'left',
      clickCount: 1,
    })
    await chrome.debugger.sendCommand(target, 'Input.dispatchMouseEvent', {
      type: 'mouseReleased',
      x: click.clientX,
      y: click.clientY,
      button: 'left',
      clickCount: 1,
    })
  } finally {
    await chrome.debugger.detach(target).catch(() => {})
  }
  return { ok: true }
}

async function setFileInputFromUrl(tabId, urlValue, options = {}) {
  if (!tabId || !urlValue) throw new Error('文件上传参数不完整')
  const tab = await chrome.tabs.get(tabId).catch(() => null)
  const platform = normalizePlatform(options.platform || inferPlatformFromUrl(tab?.url || ''))
  if (!tab?.url || !isAllowedPlatformUrl(platform, tab.url)) {
    throw new Error(`本地文件上传仅允许用于${platformDisplayName(platform)}页面`)
  }
  const { config, session } = await getConfig()
  const uploaded = await helperRequest(config, '/v1/extension/files/upload-image-to-page', {
    method: 'POST',
    body: JSON.stringify({
      url: urlValue,
      backendBase: config.apiBase,
      taskId: options.taskId || null,
      environmentKey: options.environmentKey || config.environmentKey,
      platform,
      targetPageUrl: tab.url,
    }),
  }, session)
  if (!uploaded?.ok) throw new Error(`本地助手未完成${platformDisplayName(platform)}文件上传`)
  return {
    ok: true,
    fileName: uploaded?.image?.fileName || '',
    contentType: uploaded?.image?.contentType || '',
    size: uploaded?.image?.size || 0,
    inputState: uploaded?.upload?.inputState || null,
    fileInputCount: uploaded?.upload?.fileInputCount || null,
    pageUrl: uploaded?.upload?.pageUrl || '',
  }
}

async function setBaijiahaoUeditorContentInMainWorld(tabId, message = {}) {
  if (!tabId) throw new Error('百家号正文写入缺少 tabId')
  const frameId = String(message.frameId || 'ueditor_0')
  const instantId = String(message.instantId || '')
  const html = String(message.html || '')
  const [result] = await executeScriptOnPlatformTab(tabId, {
    target: { tabId },
    world: 'MAIN',
    args: [frameId, instantId, html],
    func: (targetFrameId, targetInstantId, contentHtml) => {
      function pickEditor() {
        const candidates = [
          window.UE_V2?.getEditor?.(targetFrameId),
          window.UE_V2?.instants?.[targetFrameId],
          window.UE_V2?.instances?.[targetFrameId],
          targetInstantId ? window.UE_V2?.instants?.[targetInstantId] : null,
          targetInstantId ? window.UE_V2?.instances?.[targetInstantId] : null,
          window.UE?.getEditor?.(targetFrameId),
          window.UE?.instants?.[targetFrameId],
          window.UE?.instances?.[targetFrameId],
        ]
        return candidates.find((item) => item && typeof item.setContent === 'function') || null
      }
      const editor = pickEditor()
      if (editor) {
        editor.setContent(contentHtml)
        editor.sync?.()
        editor.fireEvent?.('contentchange')
        editor.fireEvent?.('selectionchange')
      }
      const frame = document.getElementById(targetFrameId)
      const doc = frame?.contentDocument || frame?.contentWindow?.document || null
      const body = doc?.body || null
      if (body) {
        if (!String(body.innerText || body.textContent || '').trim()) {
          try {
            body.focus?.()
            const selection = frame.contentWindow?.getSelection?.()
            const range = doc.createRange()
            range.selectNodeContents(body)
            selection?.removeAllRanges()
            selection?.addRange(range)
            doc.execCommand?.('delete', false)
            doc.execCommand?.('insertHTML', false, contentHtml)
          } catch (_) {
            // Fall back to direct DOM assignment below.
          }
        }
        body.innerHTML = contentHtml
        const EventCtor = frame.contentWindow?.Event || Event
        const InputEventCtor = frame.contentWindow?.InputEvent || InputEvent
        body.dispatchEvent(new InputEventCtor('input', { bubbles: true, inputType: 'insertHTML' }))
        body.dispatchEvent(new InputEventCtor('beforeinput', { bubbles: true, inputType: 'insertHTML' }))
        body.dispatchEvent(new EventCtor('change', { bubbles: true }))
        body.dispatchEvent(new EventCtor('blur', { bubbles: true }))
      }
      editor?.sync?.()
      return {
        ok: Boolean(editor || body),
        editorFound: Boolean(editor),
        bodyFound: Boolean(body),
        bodyText: String(body?.innerText || body?.textContent || '').slice(0, 200),
      }
    },
  })
  return result?.result || { ok: false, editorFound: false, bodyFound: false, bodyText: '' }
}

async function findDouyinUploadClickPoints(tabId) {
  const result = await executeScriptOnPlatformTab(tabId, {
    target: { tabId },
    func: () => {
      function visible(el) {
        if (!el || !el.getBoundingClientRect) return false
        const style = window.getComputedStyle(el)
        const rect = el.getBoundingClientRect()
        return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0
      }
      function normalize(text) {
        return String(text || '').replace(/\s+/g, '')
      }
      function contextText(el) {
        const parts = []
        let current = el
        for (let depth = 0; current && depth < 7; depth += 1) {
          parts.push(normalize(current.textContent))
          current = current.parentElement
        }
        return parts.join('')
      }
      function scoreCandidate(el, text) {
        if (!text.includes('上传图片') && !text.includes('点击上传图片')) return 0
        const context = contextText(el)
        let score = 10
        if (text === '点击上传图片' || text === '上传图片') score += 100
        if (context.includes('文章头图')) score += 400
        if (context.includes('封面设置')) score -= 200
        if (context.includes('AI配图')) score += 10
        if (String(el.className || '').includes('mycard')) score += 20
        return score
      }
      function pointFromRect(rect, label, score, text, clickableText) {
        if (!rect || rect.width <= 0 || rect.height <= 0) return null
        const clientX = Math.round(rect.left + rect.width / 2)
        const clientY = Math.round(rect.top + rect.height / 2)
        const screenOffsetX = window.screenX + Math.round((window.outerWidth - window.innerWidth) / 2)
        const screenOffsetY = window.screenY + Math.round(window.outerHeight - window.innerHeight)
        return {
          score,
          text,
          clickableText,
          pointLabel: label,
          clientX,
          clientY,
          screenX: screenOffsetX + clientX,
          screenY: screenOffsetY + clientY,
        }
      }
      const candidates = Array.from(document.querySelectorAll('button, [role="button"], div, span, label'))
        .filter(visible)
        .flatMap((el) => {
          const text = normalize(el.textContent || el.getAttribute('aria-label') || el.getAttribute('title'))
          const score = scoreCandidate(el, text)
          if (score <= 0) return []
          const clickable = el.closest('button, label, [role="button"], [class*="mycard"], [class*="content-upload"]') || el
          if (!visible(clickable)) return []
          const icon = clickable.querySelector('[class*="addIcon"], [class*="addInnerIcon"]')
          const textNode = Array.from(clickable.querySelectorAll('span, div'))
            .filter(visible)
            .find((item) => normalize(item.textContent).includes('上传图片'))
          const cardRect = clickable.getBoundingClientRect()
          const clickableText = normalize(clickable.textContent).slice(0, 80)
          const points = [
            pointFromRect(visible(icon) ? icon.getBoundingClientRect() : null, 'icon', score + 20, text, clickableText),
            pointFromRect(visible(textNode) ? textNode.getBoundingClientRect() : null, 'text', score + 15, text, clickableText),
            pointFromRect(cardRect, 'card_center', score + 10, text, clickableText),
            {
            score: score + 5,
            text,
            clickableText,
            pointLabel: 'card_left',
            clientX: Math.round(cardRect.left + Math.min(48, Math.max(12, cardRect.width * 0.08))),
            clientY: Math.round(cardRect.top + cardRect.height / 2),
            screenX: window.screenX + Math.round((window.outerWidth - window.innerWidth) / 2)
              + Math.round(cardRect.left + Math.min(48, Math.max(12, cardRect.width * 0.08))),
            screenY: window.screenY + Math.round(window.outerHeight - window.innerHeight)
              + Math.round(cardRect.top + cardRect.height / 2),
          },
          ].filter(Boolean)
          return points
        })
        .sort((left, right) => right.score - left.score)
      const seen = new Set()
      return candidates.filter((item) => {
        const key = `${item.clientX},${item.clientY}`
        if (seen.has(key)) return false
        seen.add(key)
        return true
      }).slice(0, 10)
    },
  }).catch(() => null)
  return Array.isArray(result?.[0]?.result) ? result[0].result : []
}

async function setFileInputByFileChooserClick(tabId, filePath, clickCandidates) {
  const target = { tabId }
  await chrome.debugger.attach(target, '1.3')
  try {
    await chrome.debugger.sendCommand(target, 'Page.enable').catch(() => {})
    await chrome.debugger.sendCommand(target, 'DOM.enable').catch(() => {})
    const liveClicks = await findDouyinUploadClickPoints(tabId)
    const attempts = liveClicks.length ? liveClicks : clickCandidates
    const tried = []
    const runtimeClick = await clickDouyinUploadEntryByRuntime(target).catch((error) => ({
      ok: false,
      error: error?.message || String(error),
    }))
    tried.push({ trigger: 'runtime_click', runtimeClick })
    const runtimeInput = await waitForDouyinArticleImageInput(target, filePath, 'after_runtime_click', 5000)
    if (runtimeInput) return runtimeInput

    for (const click of attempts.slice(0, 10)) {
      tried.push(click)
      await dispatchDebuggerClick(target, click)
      const createdInput = await waitForDouyinArticleImageInput(target, filePath, `after_click_${click.pointLabel || 'point'}`, 5000)
      if (createdInput) return createdInput
    }
    const diagnostics = await describeDouyinFileInputs(target)
    throw new Error(`抖音文件上传控件未生成；tried=${JSON.stringify(tried).slice(0, 900)}; inputs=${JSON.stringify(diagnostics).slice(0, 800)}`)
  } finally {
    await chrome.debugger.detach(target).catch(() => {})
  }
}

async function setPlatformImageInputInCurrentTab(tabId, filePath, platform, options = {}) {
  const target = { tabId }
  await chrome.debugger.attach(target, '1.3')
  try {
    await chrome.debugger.sendCommand(target, 'Page.enable').catch(() => {})
    await chrome.debugger.sendCommand(target, 'DOM.enable').catch(() => {})
    await chrome.debugger.sendCommand(target, 'Runtime.enable').catch(() => {})

    const direct = await waitForPlatformImageInput(target, filePath, platform, 'initial', 1200)
    if (direct) return direct

    const clickCandidates = await findPlatformUploadClickPoints(tabId, platform)
    const fallbackClick = options.click?.clientX != null && options.click?.clientY != null ? options.click : null
    const attempts = clickCandidates.length ? clickCandidates : (fallbackClick ? [fallbackClick] : [])
    const tried = []

    for (const click of attempts.slice(0, 8)) {
      tried.push(click)
      await dispatchDebuggerClick(target, click)
      const afterClick = await waitForPlatformImageInput(target, filePath, platform, `after_click_${click.pointLabel || 'point'}`, 3000)
      if (afterClick) return { ...afterClick, chooserTried: tried }
    }

    const diagnostics = await describePlatformFileInputs(target, platform)
    throw new Error(`${platformDisplayName(platform)} image file input not found; diagnostics=${JSON.stringify({
      platform,
      strategy: `${platform || 'platform'}_exact_tab_upload`,
      pageUrl: (await chrome.tabs.get(tabId).catch(() => null))?.url || '',
      fileInputCount: diagnostics.length,
      chooserTried: tried,
      inputs: diagnostics.slice(0, 8),
    }).slice(0, 1200)}`)
  } finally {
    await chrome.debugger.detach(target).catch(() => {})
  }
}

async function waitForPlatformImageInput(target, filePath, platform, stage, timeoutMs) {
  const startedAt = Date.now()
  let last = null
  while (Date.now() - startedAt < timeoutMs) {
    last = await setPlatformImageInputIfAvailable(target, filePath, platform, stage).catch(() => null)
    if (last) return last
    await delay(250)
  }
  return null
}

async function setPlatformImageInputIfAvailable(target, filePath, platform, stage) {
  const documentResult = await chrome.debugger.sendCommand(target, 'DOM.getDocument', { depth: -1, pierce: true }).catch(() => null)
  const rootNodeId = documentResult?.root?.nodeId
  if (!rootNodeId) return null
  const query = await chrome.debugger.sendCommand(target, 'DOM.querySelectorAll', {
    nodeId: rootNodeId,
    selector: 'input[type="file"]',
  }).catch(() => null)
  const nodeIds = Array.isArray(query?.nodeIds) ? query.nodeIds : []
  const chosen = await choosePlatformImageInputNode(target, nodeIds, platform)
  if (!chosen?.nodeId) return null
  await chrome.debugger.sendCommand(target, 'DOM.setFileInputFiles', {
    nodeId: chosen.nodeId,
    files: [filePath],
  })
  const inputState = await dispatchFileInputEvents(target, chosen.nodeId)
  return {
    pageUrl: (await chrome.tabs.get(target.tabId).catch(() => null))?.url || '',
    fileInputCount: nodeIds.length,
    inputState,
    chosenInput: chosen.diagnostic,
    via: `debugger_exact_tab_input_${stage}`,
  }
}

async function choosePlatformImageInputNode(target, nodeIds, platform) {
  const candidates = []
  for (const nodeId of nodeIds) {
    const described = await chrome.debugger.sendCommand(target, 'DOM.describeNode', { nodeId }).catch(() => null)
    const attrs = attributesToObject(described?.node?.attributes || [])
    const context = await describeFileInputContext(target, nodeId)
    const diagnostic = { attrs, context }
    const score = scorePlatformImageFileInput(attrs, context, platform)
    if (score > 0) candidates.push({ nodeId, score, diagnostic })
  }
  candidates.sort((left, right) => right.score - left.score)
  return candidates[0] || null
}

function scorePlatformImageFileInput(attrs, context, platform) {
  const normalizedPlatform = normalizePlatform(platform)
  const descriptor = `${attrs.accept || ''} ${attrs.id || ''} ${attrs.name || ''} ${attrs.class || ''} ${context?.contextText || ''}`.toLowerCase()
  let score = 0
  if (/(image|jpg|jpeg|png|webp|gif|jfif)/.test(descriptor)) score += 100
  if (/视频|video|mp4|mov|avi|mkv|webm|mpeg|ogg|flv|vob|rmvb/.test(descriptor)) score -= 260
  if (/头像|avatar|logo|账号|profile/.test(descriptor)) score -= 120
  if (normalizedPlatform === 'baijiahao') {
    if (/media|cheetah-upload|本地上传|点击本地上传|正文\/本地上传|设置封面|封面预览|支持jpg|支持png/.test(descriptor)) score += 180
    if (/ai封图|免费正版图库/.test(descriptor)) score -= 30
  } else if (normalizedPlatform === 'zhihu') {
    if (/添加文章封面|添加封面|上传封面|图片上传格式|jpeg|jpg|png/.test(descriptor)) score += 180
  }
  if (context?.visible) score += 5
  return score
}

async function describePlatformFileInputs(target, platform) {
  const documentResult = await chrome.debugger.sendCommand(target, 'DOM.getDocument', { depth: -1, pierce: true }).catch(() => null)
  const rootNodeId = documentResult?.root?.nodeId
  if (!rootNodeId) return []
  const query = await chrome.debugger.sendCommand(target, 'DOM.querySelectorAll', {
    nodeId: rootNodeId,
    selector: 'input[type="file"]',
  }).catch(() => null)
  const nodeIds = Array.isArray(query?.nodeIds) ? query.nodeIds : []
  const result = []
  for (const nodeId of nodeIds.slice(0, 12)) {
    const described = await chrome.debugger.sendCommand(target, 'DOM.describeNode', { nodeId }).catch(() => null)
    const attrs = attributesToObject(described?.node?.attributes || [])
    const context = await describeFileInputContext(target, nodeId)
    result.push({
      accept: attrs.accept || '',
      id: attrs.id || '',
      name: attrs.name || '',
      class: attrs.class || '',
      visible: Boolean(context?.visible),
      contextText: String(context?.contextText || '').slice(0, 220),
      score: scorePlatformImageFileInput(attrs, context, platform),
    })
  }
  return result
}

async function findPlatformUploadClickPoints(tabId, platform) {
  const result = await executeScriptOnPlatformTab(tabId, {
    target: { tabId },
    args: [normalizePlatform(platform)],
    func: (platform) => {
      function visible(el) {
        if (!el || !el.getBoundingClientRect) return false
        const rect = el.getBoundingClientRect()
        const style = window.getComputedStyle(el)
        return rect.width > 0 && rect.height > 0 && style.visibility !== 'hidden' && style.display !== 'none'
      }
      function normalize(text) {
        return String(text || '').replace(/\s+/g, '')
      }
      function contextText(el) {
        const parts = []
        let current = el
        for (let depth = 0; current && depth < 7; depth += 1) {
          parts.push(normalize(current.textContent))
          parts.push(normalize(current.className))
          current = current.parentElement
        }
        return parts.join('')
      }
      function scoreCandidate(el, text) {
        const context = contextText(el)
        let score = 0
        if (platform === 'baijiahao') {
          if (text.includes('点击本地上传') || text.includes('本地上传')) score += 140
          if (context.includes('正文/本地上传') || context.includes('设置封面')) score += 160
          if (context.includes('AI封图') || context.includes('免费正版图库')) score -= 40
        } else if (platform === 'zhihu') {
          if (text.includes('添加文章封面') || text.includes('添加封面') || text.includes('上传封面')) score += 180
          if (context.includes('发布设置') || context.includes('图片上传格式')) score += 120
        }
        return score
      }
      function pointFromRect(rect, label, score, text, clickableText) {
        if (!rect || rect.width <= 0 || rect.height <= 0) return null
        const clientX = Math.round(rect.left + rect.width / 2)
        const clientY = Math.round(rect.top + rect.height / 2)
        const screenOffsetX = window.screenX + Math.round((window.outerWidth - window.innerWidth) / 2)
        const screenOffsetY = window.screenY + Math.round(window.outerHeight - window.innerHeight)
        return {
          score,
          text,
          clickableText,
          pointLabel: label,
          clientX,
          clientY,
          screenX: screenOffsetX + clientX,
          screenY: screenOffsetY + clientY,
        }
      }
      const candidates = Array.from(document.querySelectorAll('button, [role="button"], label, div, span'))
        .filter(visible)
        .flatMap((el) => {
          const text = normalize(el.textContent || el.getAttribute('aria-label') || el.getAttribute('title'))
          const score = scoreCandidate(el, text)
          if (score <= 0) return []
          const clickable = el.closest('button, label, [role="button"], [class*="upload"], [class*="cover"], [class*="Cover"]') || el
          if (!visible(clickable)) return []
          const rect = clickable.getBoundingClientRect()
          const clickableText = normalize(clickable.textContent).slice(0, 120)
          return [
            pointFromRect(rect, 'platform_upload_center', score + 10, text, clickableText),
            pointFromRect({
              left: rect.left + Math.min(48, Math.max(12, rect.width * 0.12)),
              top: rect.top,
              width: 4,
              height: rect.height,
            }, 'platform_upload_left', score + 5, text, clickableText),
          ].filter(Boolean)
        })
        .sort((left, right) => right.score - left.score)
      const seen = new Set()
      return candidates.filter((item) => {
        const key = `${item.clientX},${item.clientY}`
        if (seen.has(key)) return false
        seen.add(key)
        return true
      }).slice(0, 10)
    },
  }).catch(() => null)
  return Array.isArray(result?.[0]?.result) ? result[0].result : []
}

async function waitForDouyinArticleImageInput(target, filePath, stage, timeoutMs) {
  const startedAt = Date.now()
  let last = null
  while (Date.now() - startedAt < timeoutMs) {
    last = await setDouyinArticleImageInputIfAvailable(target, filePath, stage).catch(() => null)
    if (last) return last
    await delay(250)
  }
  return null
}

async function clickDouyinUploadEntryByRuntime(target) {
  const result = await chrome.debugger.sendCommand(target, 'Runtime.evaluate', {
    returnByValue: true,
    awaitPromise: true,
    userGesture: true,
    expression: `(() => {
      function visible(el) {
        if (!el || !el.getBoundingClientRect) return false;
        const rect = el.getBoundingClientRect();
        const style = window.getComputedStyle(el);
        return rect.width > 0 && rect.height > 0 && style.visibility !== 'hidden' && style.display !== 'none';
      }
      function norm(value) {
        return String(value || '').replace(/\\s+/g, '');
      }
      function contextText(el) {
        const parts = [];
        let current = el;
        for (let depth = 0; current && depth < 8; depth += 1) {
          parts.push(norm(current.textContent));
          parts.push(norm(current.className));
          current = current.parentElement;
        }
        return parts.join('');
      }
      function score(el) {
        const text = norm(el.textContent || el.getAttribute('aria-label') || el.getAttribute('title'));
        if (!text.includes('上传图片') && !text.includes('点击上传图片')) return 0;
        const context = contextText(el);
        let value = 10;
        if (text === '点击上传图片' || text === '上传图片') value += 100;
        if (context.includes('文章头图')) value += 600;
        if (context.includes('封面设置')) value -= 300;
        if (context.includes('AI配图')) value += 10;
        if (context.includes('文章正文')) value -= 80;
        return value;
      }
      const candidates = Array.from(document.querySelectorAll('button, [role="button"], label, div, span'))
        .filter(visible)
        .map((el) => {
          const clickable = el.closest('button, label, [role="button"], [class*="mycard"], [class*="content-upload"]') || el;
          return {
            el: clickable,
            score: score(el),
            text: norm(el.textContent || '').slice(0, 80),
            clickableText: norm(clickable.textContent || '').slice(0, 120),
          };
        })
        .filter((item) => item.score > 0 && visible(item.el))
        .sort((left, right) => right.score - left.score);
      const best = candidates[0];
      if (!best) return { ok: false, reason: 'entry_not_found', candidates: [] };
      best.el.scrollIntoView({ block: 'center', inline: 'nearest' });
      best.el.focus?.();
      best.el.click();
      const rect = best.el.getBoundingClientRect();
      return {
        ok: true,
        score: best.score,
        text: best.text,
        clickableText: best.clickableText,
        clientX: Math.round(rect.left + rect.width / 2),
        clientY: Math.round(rect.top + rect.height / 2),
      };
    })()`,
  })
  const value = result?.result?.value || null
  if (!value?.ok) throw new Error(`抖音上传入口 runtime click 失败：${JSON.stringify(value).slice(0, 500)}`)
  return value
}

async function describeDouyinFileInputsInPage(tabId) {
  const target = { tabId }
  await chrome.debugger.attach(target, '1.3')
  try {
    await chrome.debugger.sendCommand(target, 'DOM.enable').catch(() => {})
    return await describeDouyinFileInputs(target)
  } finally {
    await chrome.debugger.detach(target).catch(() => {})
  }
}

async function dispatchDebuggerClick(target, click) {
  await chrome.debugger.sendCommand(target, 'Input.dispatchMouseEvent', {
    type: 'mouseMoved',
    x: click.clientX,
    y: click.clientY,
    button: 'none',
    buttons: 0,
    pointerType: 'mouse',
  }).catch(() => {})
  await chrome.debugger.sendCommand(target, 'Input.dispatchMouseEvent', {
    type: 'mousePressed',
    x: click.clientX,
    y: click.clientY,
    button: 'left',
    buttons: 1,
    clickCount: 1,
    pointerType: 'mouse',
  })
  await delay(80)
  await chrome.debugger.sendCommand(target, 'Input.dispatchMouseEvent', {
    type: 'mouseReleased',
    x: click.clientX,
    y: click.clientY,
    button: 'left',
    buttons: 0,
    clickCount: 1,
    pointerType: 'mouse',
  })
}

async function setDouyinArticleImageInputIfAvailable(target, filePath, stage) {
  const documentResult = await chrome.debugger.sendCommand(target, 'DOM.getDocument', { depth: -1, pierce: true }).catch(() => null)
  const rootNodeId = documentResult?.root?.nodeId
  if (!rootNodeId) return null
  const query = await chrome.debugger.sendCommand(target, 'DOM.querySelectorAll', {
    nodeId: rootNodeId,
    selector: 'input[type="file"]',
  }).catch(() => null)
  const nodeIds = Array.isArray(query?.nodeIds) ? query.nodeIds : []
  const chosen = await chooseDouyinArticleImageInputNode(target, nodeIds)
  if (!chosen?.nodeId) return null
  await chrome.debugger.sendCommand(target, 'DOM.setFileInputFiles', {
    nodeId: chosen.nodeId,
    files: [filePath],
  })
  const inputState = await dispatchFileInputEvents(target, chosen.nodeId)
  return {
    pageUrl: (await chrome.tabs.get(target.tabId).catch(() => null))?.url || '',
    fileInputCount: nodeIds.length,
    inputState,
    chosenInput: chosen.diagnostic,
    via: `debugger_direct_input_${stage}`,
  }
}

async function chooseDouyinArticleImageInputNode(target, nodeIds) {
  const candidates = []
  for (const nodeId of nodeIds) {
    const described = await chrome.debugger.sendCommand(target, 'DOM.describeNode', { nodeId }).catch(() => null)
    const attrs = attributesToObject(described?.node?.attributes || [])
    const context = await describeFileInputContext(target, nodeId)
    const diagnostic = { attrs, context }
    const score = scoreDouyinArticleImageInput(attrs, context)
    if (score > 0) candidates.push({ nodeId, score, diagnostic })
  }
  candidates.sort((left, right) => right.score - left.score)
  return candidates[0] || null
}

function scoreDouyinArticleImageInput(attrs, context) {
  const descriptor = `${attrs.accept || ''} ${attrs.id || ''} ${attrs.name || ''} ${attrs.class || ''} ${context?.contextText || ''}`.toLowerCase()
  let score = 0
  if (/(image|jpg|jpeg|png|webp|gif|jfif)/.test(descriptor)) score += 100
  if (/文章头图|点击上传图片|上传图片|ai配图/.test(descriptor)) score += 120
  if (/封面设置|点击上传封面图|选择封面|编辑封面/.test(descriptor)) score -= 80
  if (/(video|mp4|mov|avi|mkv|webm|mpeg)/.test(descriptor)) score -= 220
  if (/头像|avatar|logo|账号|profile/.test(descriptor)) score -= 120
  if (context?.visible) score += 5
  return score
}

async function describeDouyinFileInputs(target) {
  const documentResult = await chrome.debugger.sendCommand(target, 'DOM.getDocument', { depth: -1, pierce: true }).catch(() => null)
  const rootNodeId = documentResult?.root?.nodeId
  if (!rootNodeId) return []
  const query = await chrome.debugger.sendCommand(target, 'DOM.querySelectorAll', {
    nodeId: rootNodeId,
    selector: 'input[type="file"]',
  }).catch(() => null)
  const nodeIds = Array.isArray(query?.nodeIds) ? query.nodeIds : []
  const result = []
  for (const nodeId of nodeIds.slice(0, 12)) {
    const described = await chrome.debugger.sendCommand(target, 'DOM.describeNode', { nodeId }).catch(() => null)
    const attrs = attributesToObject(described?.node?.attributes || [])
    const context = await describeFileInputContext(target, nodeId)
    result.push({
      accept: attrs.accept || '',
      id: attrs.id || '',
      name: attrs.name || '',
      class: attrs.class || '',
      visible: Boolean(context?.visible),
      contextText: String(context?.contextText || '').slice(0, 160),
      score: scoreDouyinArticleImageInput(attrs, context),
    })
  }
  return result
}

async function setLatestFileInputFiles(tabId, filePath) {
  const target = { tabId }
  await chrome.debugger.attach(target, '1.3')
  try {
    const documentResult = await chrome.debugger.sendCommand(target, 'DOM.getDocument', { depth: -1, pierce: true })
    const rootNodeId = documentResult?.root?.nodeId
    if (!rootNodeId) throw new Error('未获取到页面 DOM 根节点')
    const query = await chrome.debugger.sendCommand(target, 'DOM.querySelectorAll', {
      nodeId: rootNodeId,
      selector: 'input[type="file"]',
    })
    const nodeIds = Array.isArray(query?.nodeIds) ? query.nodeIds : []
    const chosen = await chooseImageFileInputNode(target, nodeIds)
    const nodeId = chosen?.nodeId || null
    if (!nodeId) throw new Error('头条封面本地上传文件框未找到')
    await chrome.debugger.sendCommand(target, 'DOM.setFileInputFiles', {
      nodeId,
      files: [filePath],
    })
    const state = await dispatchFileInputEvents(target, nodeId)
    return { ...state, chosenInput: chosen?.attrs || null, fileInputCount: nodeIds.length }
  } finally {
    await chrome.debugger.detach(target).catch(() => {})
  }
}

async function chooseImageFileInputNode(target, nodeIds) {
  let fallback = null
  const preferred = []
  for (const nodeId of nodeIds) {
    const described = await chrome.debugger.sendCommand(target, 'DOM.describeNode', { nodeId }).catch(() => null)
    const attrs = attributesToObject(described?.node?.attributes || [])
    const context = await describeFileInputContext(target, nodeId)
    const item = { nodeId, attrs, context, score: scoreImageFileInput(attrs, context) }
    if (!fallback) fallback = item
    const accept = String(attrs.accept || '').toLowerCase()
    const name = String(attrs.name || attrs.class || attrs.id || '').toLowerCase()
    if (accept.includes('image') || accept.includes('jpg') || accept.includes('png') || /image|upload|cover|file/.test(name)) {
      preferred.push(item)
    }
  }
  preferred.sort((left, right) => right.score - left.score)
  if (preferred.length) return preferred[0]
  return nodeIds.length ? { nodeId: nodeIds[nodeIds.length - 1], attrs: {} } : fallback
}

async function describeFileInputContext(target, nodeId) {
  const resolved = await chrome.debugger.sendCommand(target, 'DOM.resolveNode', { nodeId }).catch(() => null)
  const objectId = resolved?.object?.objectId
  if (!objectId) return {}
  const result = await chrome.debugger.sendCommand(target, 'Runtime.callFunctionOn', {
    objectId,
    returnByValue: true,
    functionDeclaration: `function() {
      function norm(value) { return String(value || '').replace(/\\s+/g, ' ').trim(); }
      const parts = [];
      let current = this;
      for (let depth = 0; current && depth < 7; depth += 1) {
        parts.push(current.id || '');
        parts.push(String(current.className || ''));
        parts.push(current.getAttribute && current.getAttribute('data-e2e') || '');
        const text = norm(current.textContent || '');
        if (text && text.length <= 180) parts.push(text);
        current = current.parentElement;
      }
      const rect = this.getBoundingClientRect();
      return {
        contextText: norm(parts.join(' ')),
        visible: rect.width > 0 && rect.height > 0,
        width: rect.width,
        height: rect.height,
      };
    }`,
  }).catch(() => null)
  return result?.result?.value || {}
}

function scoreImageFileInput(attrs, context) {
  const descriptor = `${attrs.accept || ''} ${attrs.id || ''} ${attrs.name || ''} ${attrs.class || ''} ${context?.contextText || ''}`.toLowerCase()
  let score = 0
  if (/(image|jpg|jpeg|png|webp)/.test(descriptor)) score += 20
  if (/btn-upload-handle|upload-handler|本地上传|上传图片|btn-upload|upload-btn/.test(descriptor)) score += 120
  if (/upload-drag-input/.test(descriptor)) score += 90
  if (/扫码上传/.test(descriptor)) score -= 30
  if (/头像|avatar|logo|账号|profile/.test(descriptor)) score -= 80
  if (context?.visible) score += 5
  return score
}

function attributesToObject(attributes) {
  const result = {}
  for (let index = 0; index < attributes.length; index += 2) {
    result[attributes[index]] = attributes[index + 1] || ''
  }
  return result
}

async function dispatchFileInputEvents(target, nodeId) {
  const resolved = await chrome.debugger.sendCommand(target, 'DOM.resolveNode', { nodeId })
  const objectId = resolved?.object?.objectId
  if (!objectId) return { filesLength: null }
  const result = await chrome.debugger.sendCommand(target, 'Runtime.callFunctionOn', {
    objectId,
    returnByValue: true,
    functionDeclaration: `function() {
      this.dispatchEvent(new Event('input', { bubbles: true }));
      this.dispatchEvent(new Event('change', { bubbles: true }));
      return {
        filesLength: this.files ? this.files.length : 0,
        fileName: this.files && this.files[0] ? this.files[0].name : '',
        accept: this.getAttribute('accept') || '',
        id: this.id || '',
        name: this.name || '',
      };
    }`,
  })
  return result?.result?.value || { filesLength: null }
}

async function fetchImageDataUrl(urlValue, depth = 0) {
  const url = new URL(urlValue)
  if (!['http:', 'https:'].includes(url.protocol)) {
    throw new Error('封面图片地址仅支持 http/https')
  }
  const response = await fetch(url.href)
  if (!response.ok) {
    throw new Error(`封面图片下载失败：HTTP ${response.status}`)
  }
  const type = response.headers.get('content-type') || 'image/jpeg'
  if (!type.startsWith('image/')) {
    const bodyText = await response.text().catch(() => '')
    const nestedUrl = extractImageUrlFromJsonText(bodyText)
    if (nestedUrl && depth < 2) {
      return fetchImageDataUrl(nestedUrl, depth + 1)
    }
    const backendUrl = await rewritePublicMaterialUrlToConfiguredBackend(url.href)
    if (backendUrl && depth < 2) {
      return fetchImageDataUrl(backendUrl, depth + 1)
    }
    throw new Error(`封面图片类型不支持：${type}；url=${url.href}；响应=${bodyText.slice(0, 240) || '-'}`)
  }
  const buffer = await response.arrayBuffer()
  if (buffer.byteLength > MAX_IMAGE_FETCH_BYTES) {
    throw new Error('封面图片超过 20MB，头条本地上传不支持')
  }
  return {
    dataUrl: `data:${type};base64,${arrayBufferToBase64(buffer)}`,
    type,
    size: buffer.byteLength,
  }
}

function extractImageUrlFromJsonText(text) {
  try {
    const json = JSON.parse(text)
    return firstText(
      json?.url,
      json?.data?.url,
      json?.data?.previewUrl,
      json?.data?.downloadUrl,
      json?.data?.fileUrl,
      json?.result?.url,
      json?.result?.previewUrl,
      json?.result?.downloadUrl,
    )
  } catch {
    const match = String(text || '').match(/https?:\/\/[^"'\\\s]+/i)
    return match?.[0] || ''
  }
}

async function rewritePublicMaterialUrlToConfiguredBackend(urlValue) {
  try {
    const url = new URL(urlValue)
    if (!url.pathname.startsWith('/api/public/brand-materials/')) return ''
    const { config } = await getConfig()
    if (!config?.apiBase) return ''
    const backend = new URL(config.apiBase)
    if (backend.origin === url.origin) return ''
    return `${backend.origin}${url.pathname}${url.search}`
  } catch {
    return ''
  }
}

function arrayBufferToBase64(buffer) {
  const bytes = new Uint8Array(buffer)
  const chunkSize = 0x8000
  let binary = ''
  for (let index = 0; index < bytes.length; index += chunkSize) {
    const chunk = bytes.subarray(index, index + chunkSize)
    binary += String.fromCharCode(...chunk)
  }
  return btoa(binary)
}

function firstText(...values) {
  for (const value of values) {
    if (typeof value === 'string' && value.trim()) return value.trim()
  }
  return ''
}

async function setBadge(text) {
  try {
    await chrome.action.setBadgeText({ text })
    await chrome.action.setBadgeBackgroundColor({ color: text === 'ERR' ? '#b91c1c' : '#15803d' })
  } catch {
    // Badge updates are non-critical in the PoC.
  }
}

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  const run = async () => {
    if (message?.type === 'GEO_ENV_BIND') {
      const session = await bindExtension(message.bindCode)
      return { ok: true, session }
    }
    if (message?.type === 'GEO_ENV_BIND_FROM_INTENT') {
      return bindFromActiveTabIntent()
    }
    if (message?.type === 'GEO_ENV_POLL_ONCE') {
      return pollOnce()
    }
    if (message?.type === 'GEO_ENV_REPORT_LOGIN_STATUS') {
      const status = await reportActiveTabLoginStatus()
      return { ok: true, status }
    }
    if (message?.type === 'GEO_ENV_SELF_TEST') {
      return { ok: true, checks: await runSelfTest() }
    }
    if (message?.type === 'GEO_ENV_EDITOR_READY') {
      const readyUrl = sender.url || message.href || sender.tab?.url || ''
      const frameId = Number.isInteger(sender.frameId) ? sender.frameId : 0
      if (frameId !== 0 || !isAutoPollReadyUrl(readyUrl)) {
        return { ok: true, skipped: true, reason: 'not_publish_editor_ready' }
      }
      return autoPollOnce('editor_ready', sender.tab?.id || null, { source: 'editor_ready', href: message.href || readyUrl })
    }
    if (message?.type === 'GEO_ENV_TRUSTED_CLICK') {
      return dispatchTrustedClick(sender.tab?.id || null, message.click || {})
    }
    if (message?.type === 'GEO_ENV_SET_FILE_INPUT_FROM_URL') {
      return setFileInputFromUrl(sender.tab?.id || null, message.url, {
        platform: message.platform || null,
        taskId: message.taskId || null,
        environmentKey: message.environmentKey || null,
        click: message.click || null,
      })
    }
    if (message?.type === 'GEO_ENV_SET_BAIJIAHAO_UEDITOR_CONTENT') {
      const result = await setBaijiahaoUeditorContentInMainWorld(sender.tab?.id || null, message)
      return { ok: Boolean(result?.ok), result }
    }
    if (message?.type === 'GEO_ENV_FETCH_IMAGE_DATA_URL') {
      const result = await fetchImageDataUrl(message.url)
      return { ok: true, result }
    }
    if (message?.type === 'GEO_ENV_FILL_TOUTIAO_SCHEDULE_ACROSS_FRAMES') {
      const result = await fillToutiaoScheduleAcrossFrames(sender.tab?.id || null, message.value || {}, message.platform || 'toutiao')
      return { ok: true, result }
    }
    return { ok: false, error: 'unknown message' }
  }

  run()
    .then((result) => sendResponse(result))
    .catch((error) => sendResponse({ ok: false, error: error.message }))
  return true
})

chrome.tabs.onUpdated.addListener((tabId, changeInfo, tab) => {
  const url = changeInfo.url || tab?.url || ''
  if (url && url.includes('geoEnvBindIntent=')) {
    bindFromTabUrl(tabId, url)
    return
  }
  if (changeInfo.status !== 'complete') return
  const readyUrl = tab?.url || url
  if (isAutoPollReadyUrl(readyUrl)) {
    triggerAutoPollFromTabComplete(tabId, readyUrl)
    return
  }
  if (isAutoLoginReportReadyUrl(readyUrl)) {
    triggerAutoLoginReportFromTabComplete(tabId, readyUrl)
  }
})

scheduleRuntimeStatusHeartbeat()

function isAutoLoginReportReadyUrl(urlValue) {
  const platform = inferPlatformFromUrl(urlValue)
  return Boolean(platform && isAllowedLoginReportUrl(platform, urlValue))
}

function loginReportContextFromUrl(urlValue) {
  try {
    const url = new URL(urlValue || '')
    const enabled = url.searchParams.get('geoEnvLoginReport') === '1'
    if (!enabled) return {}
    return {
      platform: url.searchParams.get('geoEnvPlatform') || inferPlatformFromUrl(urlValue),
      environmentKey: url.searchParams.get('geoEnvEnvironmentKey') || '',
      environmentAccountId: url.searchParams.get('geoEnvEnvironmentAccountId') || '',
      selfMediaAccountId: url.searchParams.get('geoEnvSelfMediaAccountId') || '',
    }
  } catch {
    return {}
  }
}

function triggerAutoLoginReportFromTabComplete(tabId, urlValue) {
  const context = loginReportContextFromUrl(urlValue)
  const platform = context.platform || inferPlatformFromUrl(urlValue)
  if (!platform) return
  const key = `${tabId}:${platform}:${context.environmentAccountId || 'auto'}:login`
  const now = Date.now()
  if (now - (autoLoginReportAtByKey.get(key) || 0) < 12_000) return
  autoLoginReportAtByKey.set(key, now)
  setTimeout(async () => {
    try {
      const { config, session } = await getConfig()
      await autoReportLoginStatusFromTab(config, session, tabId, {
        platform,
        environmentKey: context.environmentKey || undefined,
        environmentAccountId: context.environmentAccountId || undefined,
        selfMediaAccountId: context.selfMediaAccountId || undefined,
      })
    } catch (error) {
      await appendEventLog({
        type: 'login_report',
        ok: false,
        platform,
        environmentKey: context.environmentKey || undefined,
        error: error?.message || String(error || ''),
      })
    }
  }, 1200)
}

function triggerAutoPollFromTabComplete(tabId, urlValue) {
  const platform = inferPlatformFromUrl(urlValue)
  const key = `${tabId}:${platform || 'unknown'}`
  const now = Date.now()
  if (now - (autoPollTabUpdatedAtByKey.get(key) || 0) < 12_000) return
  autoPollTabUpdatedAtByKey.set(key, now)
  setTimeout(async () => {
    try {
      await ensureContentScript(tabId)
      await autoPollOnce(`tab_complete:${platform || 'unknown'}`, tabId)
    } catch (error) {
      await appendEventLog({
        type: 'auto_fill',
        ok: false,
        reason: 'tab_complete',
        platform,
        error: error.message,
      })
    }
  }, 600)
}
