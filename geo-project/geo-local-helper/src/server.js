import http from 'node:http'
import { existsSync } from 'node:fs'
import fs from 'node:fs/promises'
import { URL } from 'node:url'
import crypto from 'node:crypto'

const CONFIG_PATH = new URL('../config.local.json', import.meta.url)
const EXAMPLE_CONFIG_PATH = new URL('../config.example.json', import.meta.url)
const PUBLIC_DIR = new URL('../public/', import.meta.url)
const RUNTIME_DIR = new URL('../runtime/', import.meta.url)
const TASKS_PATH = new URL('tasks.json', RUNTIME_DIR)
const SESSION_PATH = new URL('session.json', RUNTIME_DIR)
const NONCES_PATH = new URL('nonces.json', RUNTIME_DIR)
const SETTINGS_PATH = new URL('settings.json', RUNTIME_DIR)
const tasksById = new Map()
const CLAIM_TIMEOUT_MS = 30_000
const CLAIMABLE_STATUSES = new Set(['pending', 'requeued'])
const SIGNATURE_MAX_SKEW_SECONDS = 300
const NONCE_FLUSH_DELAY_MS = 1_000
const nonceCache = new Map()
let nonceFlushTimer = null
let runtimeSession = null
let runtimeSettings = { adspower: {} }
let pendingPairing = null

function nowIso() {
  return new Date().toISOString()
}

function sha256Hex(value) {
  return crypto.createHash('sha256').update(value, 'utf8').digest('hex')
}

function hmacSha256Base64Url(secret, value) {
  return crypto.createHmac('sha256', secret).update(value, 'utf8').digest('base64url')
}

function randomPairingCode() {
  const alphabet = '23456789ABCDEFGHJKMNPQRSTUVWXYZ'
  let code = ''
  for (let i = 0; i < 10; i += 1) {
    code += alphabet[crypto.randomInt(0, alphabet.length)]
  }
  return `${code.slice(0, 5)}-${code.slice(5)}`
}

function normalizePairingCode(code) {
  return String(code || '').replace(/-/g, '').trim().toUpperCase()
}

function canonicalRequest(method, path, bodyHash, timestamp, nonce, helperAccess) {
  return [
    String(method || '').toUpperCase(),
    path,
    bodyHash,
    timestamp,
    nonce,
    helperAccess,
  ].join('\n')
}

function constantTimeEqual(left, right) {
  const leftBuffer = Buffer.from(String(left || ''), 'utf8')
  const rightBuffer = Buffer.from(String(right || ''), 'utf8')
  if (leftBuffer.length !== rightBuffer.length) return false
  return crypto.timingSafeEqual(leftBuffer, rightBuffer)
}

async function loadConfig() {
  const path = existsSync(CONFIG_PATH) ? CONFIG_PATH : EXAMPLE_CONFIG_PATH
  const raw = await fs.readFile(path, 'utf8')
  const config = JSON.parse(raw)
  config.adspower ||= {}
  config.adspower.apiBase ||= 'http://localhost:50325'
  config.host ||= '127.0.0.1'
  config.port ||= 17891
  config.allowedOrigins ||= [
    `http://${config.host}:${config.port}`,
    `http://localhost:${config.port}`,
    'http://127.0.0.1:3000',
    'http://localhost:3000',
    'http://127.0.0.1:5173',
    'http://localhost:5173',
    'http://127.0.0.1:8080',
    'http://localhost:8080',
    'http://119.45.154.127',
  ]
  config.trustedBackendBase ||= config.backendBase || 'http://119.45.154.127'
  config.enableLegacyBackendTokenRoutes = config.enableLegacyBackendTokenRoutes === true
  config.enableStaticHelperToken = config.enableStaticHelperToken === true
  const trustedBackendOrigin = safeOrigin(config.trustedBackendBase)
  if (trustedBackendOrigin && !config.allowedOrigins.includes(trustedBackendOrigin)) {
    config.allowedOrigins.push(trustedBackendOrigin)
  }
  return config
}

async function loadRuntimeSettings() {
  try {
    const raw = await fs.readFile(SETTINGS_PATH, 'utf8')
    const settings = JSON.parse(raw)
    runtimeSettings = normalizeRuntimeSettings(settings)
  } catch {
    runtimeSettings = { adspower: {} }
  }
}

async function saveRuntimeSettings(settings) {
  runtimeSettings = normalizeRuntimeSettings(settings)
  await fs.mkdir(RUNTIME_DIR, { recursive: true })
  await fs.writeFile(SETTINGS_PATH, JSON.stringify(runtimeSettings, null, 2), 'utf8')
}

function normalizeRuntimeSettings(settings) {
  return {
    adspower: {
      apiBase: String(settings?.adspower?.apiBase || '').trim(),
      apiKey: String(settings?.adspower?.apiKey || '').trim(),
    },
  }
}

function effectiveAdspowerConfig(config) {
  const apiBase = runtimeSettings.adspower?.apiBase || config.adspower?.apiBase || 'http://localhost:50325'
  const apiKey = runtimeSettings.adspower?.apiKey || ''
  return { apiBase, apiKey }
}

function publicAdspowerSettings(config) {
  const adspower = effectiveAdspowerConfig(config)
  return {
    apiBase: adspower.apiBase,
    apiKeyConfigured: Boolean(adspower.apiKey),
    apiKeyPreview: previewSecret(adspower.apiKey),
  }
}

function previewSecret(value) {
  const text = String(value || '')
  if (!text) return ''
  if (text.length <= 8) return '****'
  return `${text.slice(0, 4)}****${text.slice(-4)}`
}

function requireLegacyBackendTokenRoutesEnabled(config) {
  if (config.enableLegacyBackendTokenRoutes) return
  const error = new Error('legacy backend token routes are disabled; use backend-created task launch')
  error.statusCode = 410
  throw error
}

function safeOrigin(value) {
  try {
    return new URL(value).origin
  } catch {
    return null
  }
}

async function loadRuntimeSession() {
  try {
    const raw = await fs.readFile(SESSION_PATH, 'utf8')
    const session = JSON.parse(raw)
    if (session?.sessionId && session?.hmacSecret) {
      runtimeSession = session
    }
  } catch {
    runtimeSession = null
  }
}

async function saveRuntimeSession(session) {
  await fs.mkdir(RUNTIME_DIR, { recursive: true })
  await fs.writeFile(SESSION_PATH, JSON.stringify(session, null, 2), 'utf8')
}

async function loadRuntimeTasks() {
  try {
    const raw = await fs.readFile(TASKS_PATH, 'utf8')
    const tasks = JSON.parse(raw)
    for (const task of Array.isArray(tasks) ? tasks : []) {
      const normalized = normalizePersistedTask(task)
      if (normalized) tasksById.set(normalized.taskId, normalized)
    }
  } catch {
    // Fresh PoC helper startup has no task file.
  }
}

async function saveRuntimeTasks() {
  await fs.mkdir(RUNTIME_DIR, { recursive: true })
  await fs.writeFile(TASKS_PATH, JSON.stringify(listTasks(), null, 2), 'utf8')
}

async function loadRuntimeNonces() {
  try {
    const raw = await fs.readFile(NONCES_PATH, 'utf8')
    const records = JSON.parse(raw)
    nonceCache.clear()
    const min = Math.floor(Date.now() / 1000) - SIGNATURE_MAX_SKEW_SECONDS
    for (const record of Array.isArray(records) ? records : []) {
      const nonce = String(record?.nonce || '')
      const timestamp = Number(record?.timestamp)
      if (nonce && Number.isFinite(timestamp) && timestamp >= min) {
        nonceCache.set(nonce, timestamp)
      }
    }
    await saveRuntimeNonces()
  } catch {
    nonceCache.clear()
  }
}

async function saveRuntimeNonces() {
  pruneNonceCache()
  await fs.mkdir(RUNTIME_DIR, { recursive: true })
  const records = Array.from(nonceCache.entries()).map(([nonce, timestamp]) => ({ nonce, timestamp }))
  await fs.writeFile(NONCES_PATH, JSON.stringify(records, null, 2), 'utf8')
}

function scheduleNonceFlush() {
  if (nonceFlushTimer) return
  nonceFlushTimer = setTimeout(() => {
    nonceFlushTimer = null
    saveRuntimeNonces().catch((error) => {
      console.error('Failed to persist helper nonce cache:', error.message)
    })
  }, NONCE_FLUSH_DELAY_MS)
  nonceFlushTimer.unref?.()
}

async function flushRuntimeNonces() {
  if (nonceFlushTimer) {
    clearTimeout(nonceFlushTimer)
    nonceFlushTimer = null
  }
  await saveRuntimeNonces()
}

function rememberNonce(nonce, timestamp) {
  nonceCache.set(nonce, timestamp)
  scheduleNonceFlush()
}

function normalizePersistedTask(task) {
  const taskId = Number(task?.taskId)
  if (!Number.isFinite(taskId) || taskId <= 0 || !task?.environmentKey) return null
  return {
    ...task,
    taskId,
    status: task.status || 'pending',
    createdAt: task.createdAt || nowIso(),
    claimedAt: task.claimedAt || null,
    completedAt: task.completedAt || null,
    failedAt: task.failedAt || null,
    requeuedAt: task.requeuedAt || null,
    cancelledAt: task.cancelledAt || null,
    claimOwner: task.claimOwner || null,
    lastError: task.lastError || null,
  }
}

function listTasks() {
  return Array.from(tasksById.values()).sort((left, right) => {
    const leftTime = Date.parse(left.createdAt || '') || 0
    const rightTime = Date.parse(right.createdAt || '') || 0
    if (leftTime !== rightTime) return leftTime - rightTime
    return Number(left.taskId) - Number(right.taskId)
  })
}

function upsertTask(task) {
  tasksById.set(Number(task.taskId), task)
  return task
}

function sendJson(req, res, config, statusCode, body) {
  res.writeHead(statusCode, {
    'Content-Type': 'application/json; charset=utf-8',
    ...corsHeaders(req, config),
    'Access-Control-Allow-Headers': 'Content-Type, X-Geo-Helper-Token, X-Geo-Helper-Access, X-Geo-Helper-Timestamp, X-Geo-Helper-Nonce, X-Geo-Helper-Signature',
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
  })
  res.end(JSON.stringify(body, null, 2))
}

function corsHeaders(req, config) {
  const origin = req.headers.origin
  if (!origin) return {}
  if (!isAllowedOrigin(origin, config)) return {}
  const headers = {
    'Access-Control-Allow-Origin': origin,
    'Vary': 'Origin',
  }
  if (String(req.headers['access-control-request-private-network'] || '').toLowerCase() === 'true') {
    headers['Access-Control-Allow-Private-Network'] = 'true'
  }
  return headers
}

function isAllowedOrigin(origin, config) {
  if (origin.startsWith('chrome-extension://')) return true
  return (config.allowedOrigins || []).includes(origin)
}

async function sendFile(res, fileName, contentType) {
  const fileUrl = new URL(fileName, PUBLIC_DIR)
  const content = await fs.readFile(fileUrl)
  res.writeHead(200, { 'Content-Type': contentType })
  res.end(content)
}

async function readJson(req) {
  const chunks = []
  for await (const chunk of req) chunks.push(chunk)
  const raw = Buffer.concat(chunks).toString('utf8').trim()
  req.rawBody = raw
  if (!raw) return {}
  return JSON.parse(raw)
}

function requireToken(req, config) {
  if (!config.enableStaticHelperToken || !config.helperToken) {
    const error = new Error('static helper token is disabled; use signed helper access')
    error.statusCode = 401
    throw error
  }
  const token = req.headers['x-geo-helper-token']
  if (token !== config.helperToken) {
    const error = new Error('invalid helper token')
    error.statusCode = 401
    throw error
  }
}

async function requireHelperAccess(req, config) {
  if (req.headers['x-geo-helper-signature']) {
    return requireSignedAccess(req)
  }
  return requireToken(req, config)
}

async function requireSignedAccess(req) {
  if (!runtimeSession?.sessionId || !runtimeSession?.hmacSecret) {
    const error = new Error('local helper is not paired')
    error.statusCode = 401
    throw error
  }
  const helperAccess = String(req.headers['x-geo-helper-access'] || '')
  const timestamp = String(req.headers['x-geo-helper-timestamp'] || '')
  const nonce = String(req.headers['x-geo-helper-nonce'] || '')
  const signature = String(req.headers['x-geo-helper-signature'] || '')
  const expectedHelperAccess = `helper.session.${runtimeSession.sessionId}`
  if (!constantTimeEqual(helperAccess, expectedHelperAccess)) {
    const error = new Error('invalid helper access token')
    error.statusCode = 401
    throw error
  }
  const timestampNumber = Number(timestamp)
  if (!Number.isFinite(timestampNumber)
      || Math.abs(Math.floor(Date.now() / 1000) - timestampNumber) > SIGNATURE_MAX_SKEW_SECONDS) {
    const error = new Error('helper request timestamp expired')
    error.statusCode = 401
    throw error
  }
  if (!nonce || nonce.length < 16) {
    const error = new Error('invalid helper request nonce')
    error.statusCode = 401
    throw error
  }
  pruneNonceCache()
  if (nonceCache.has(nonce)) {
    const error = new Error('replayed helper request nonce')
    error.statusCode = 409
    throw error
  }
  const url = new URL(req.url, `http://${req.headers.host || '127.0.0.1'}`)
  const bodyHash = sha256Hex(req.rawBody || '')
  const canonical = canonicalRequest(req.method, `${url.pathname}${url.search}`, bodyHash, timestamp, nonce, helperAccess)
  const expectedSignature = hmacSha256Base64Url(runtimeSession.hmacSecret, canonical)
  if (!constantTimeEqual(signature, expectedSignature)) {
    const error = new Error('invalid helper request signature')
    error.statusCode = 401
    throw error
  }
  rememberNonce(nonce, timestampNumber)
}

function pruneNonceCache() {
  const min = Math.floor(Date.now() / 1000) - SIGNATURE_MAX_SKEW_SECONDS
  for (const [nonce, timestamp] of nonceCache.entries()) {
    if (timestamp < min) nonceCache.delete(nonce)
  }
}

function normalizeProviderEnvironment(config, environmentKey, overrideProviderProfileId = null, overrideName = null) {
  const key = String(environmentKey || '').trim()
  if (!key) {
    const error = new Error('environmentKey is required')
    error.statusCode = 400
    throw error
  }
  const providerProfileId = String(overrideProviderProfileId || '').trim()
  if (!providerProfileId) {
    const error = new Error(`providerProfileId is required for environmentKey: ${key}`)
    error.statusCode = 400
    throw error
  }
  return {
    name: overrideName || key,
    environmentKey: key,
    providerProfileId,
  }
}

function requireEnvironment(config, environmentKey) {
  const environment = normalizeProviderEnvironment(config, environmentKey)
  if (!environment?.providerProfileId) {
    const error = new Error(`unknown environmentKey: ${environmentKey}`)
    error.statusCode = 404
    throw error
  }
  return environment
}

function requireEnvironmentKey(environmentKey) {
  const key = String(environmentKey || '').trim()
  if (!key) {
    const error = new Error('environmentKey is required')
    error.statusCode = 400
    throw error
  }
  return key
}

async function ensureDeviceSecret() {
  if (runtimeSession?.deviceSecret) return runtimeSession.deviceSecret
  const deviceSecret = crypto.randomBytes(32).toString('base64url')
  runtimeSession = {
    ...(runtimeSession || {}),
    deviceSecret,
    deviceSecretHash: sha256Hex(deviceSecret),
    createdAt: runtimeSession?.createdAt || nowIso(),
  }
  await saveRuntimeSession(runtimeSession)
  return deviceSecret
}

async function handlePairingCode(req, res, config) {
  const body = await readJson(req)
  const deviceSecret = await ensureDeviceSecret()
  const pairingCode = randomPairingCode()
  const normalized = normalizePairingCode(pairingCode)
  const codeHash = sha256Hex(normalized)
  const deviceSecretHash = sha256Hex(deviceSecret)
  await trustedBackendRequest(config, '/api/v1/local-agent/pairing-intents', {
    method: 'POST',
    body: JSON.stringify({
      codeHash,
      deviceSecretHash,
      helperName: String(body.helperName || config.helperName || 'GEO Local Helper').slice(0, 80),
    }),
  })
  pendingPairing = {
    pairingCode,
    codeHash,
    deviceSecretHash,
    expiresAt: new Date(Date.now() + 300_000).toISOString(),
  }
  sendJson(req, res, config, 200, {
    ok: true,
    pairingCode,
    expiresAt: pendingPairing.expiresAt,
    trustedBackendBase: config.trustedBackendBase,
  })
}

async function handlePairingStatus(req, res, config) {
  if (!pendingPairing?.pairingCode) {
    sendJson(req, res, config, 200, {
      ok: true,
      paired: Boolean(runtimeSession?.sessionId && runtimeSession?.hmacSecret),
      pending: false,
      session: publicSession(),
    })
    return
  }
  try {
    const data = await trustedBackendRequest(config, '/api/v1/local-agent/pairings/claim', {
      method: 'POST',
      body: JSON.stringify({
        pairingCode: pendingPairing.pairingCode,
        deviceSecretHash: pendingPairing.deviceSecretHash,
      }),
    })
    const currentSession = { ...(runtimeSession || {}) }
    delete currentSession.accessTokenLookupHash
    runtimeSession = {
      ...currentSession,
      sessionId: data.sessionId,
      brandId: data.brandId,
      operatorId: data.operatorId,
      hmacSecret: data.hmacSecret,
      expiresAt: data.expiresAt,
      pairedAt: nowIso(),
    }
    await saveRuntimeSession(runtimeSession)
    pendingPairing = null
    sendJson(req, res, config, 200, { ok: true, paired: true, pending: false, session: publicSession() })
  } catch (error) {
    if (error.statusCode === 404) {
      sendJson(req, res, config, 200, {
        ok: true,
        paired: false,
        pending: true,
        expiresAt: pendingPairing.expiresAt,
      })
      return
    }
    throw error
  }
}

function publicSession() {
  if (!runtimeSession?.sessionId || !runtimeSession?.hmacSecret) return null
  return {
    sessionId: runtimeSession.sessionId,
    brandId: runtimeSession.brandId,
    operatorId: runtimeSession.operatorId,
    pairedAt: runtimeSession.pairedAt,
    expiresAt: runtimeSession.expiresAt,
  }
}

async function adspowerGet(config, path) {
  const adspower = effectiveAdspowerConfig(config)
  const url = new URL(path, adspower.apiBase)
  const headers = {}
  if (adspower.apiKey) headers.Authorization = `Bearer ${adspower.apiKey}`

  const response = await fetch(url, { headers })
  const body = await response.json().catch(() => ({}))
  if (!response.ok || body.code !== 0) {
    const message = body.msg || body.message || `AdsPower request failed: ${response.status}`
    const error = new Error(message)
    error.statusCode = 502
    error.details = body
    throw error
  }
  return body.data
}

async function handleAdspowerSettings(req, res, config) {
  if (req.method === 'GET') {
    sendJson(req, res, config, 200, {
      ok: true,
      adspower: publicAdspowerSettings(config),
    })
    return
  }

  const body = await readJson(req)
  const apiBase = String(body.apiBase || '').trim() || 'http://localhost:50325'
  const apiKey = String(body.apiKey || '').trim()
  if (!safeOrigin(apiBase)) {
    const error = new Error('AdsPower API 地址无效')
    error.statusCode = 400
    throw error
  }
  const current = effectiveAdspowerConfig(config)
  await saveRuntimeSettings({
    ...runtimeSettings,
    adspower: {
      apiBase,
      apiKey: apiKey || current.apiKey || '',
    },
  })
  sendJson(req, res, config, 200, {
    ok: true,
    adspower: publicAdspowerSettings(config),
  })
}

async function backendRequest(backendBase, accessToken, path, init = {}) {
  const headers = new Headers(init.headers)
  headers.set('Content-Type', 'application/json')
  if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`)

  const response = await fetch(`${String(backendBase).replace(/\/+$/, '')}${path}`, {
    ...init,
    headers,
  })
  const body = await response.json().catch(() => ({}))
  if (!response.ok || (body.code !== undefined && body.code !== 0)) {
    const error = new Error(body.message || `backend request failed: ${response.status}`)
    error.statusCode = response.status === 401 ? 401 : 502
    error.details = body
    throw error
  }
  return body.data
}

async function trustedBackendRequest(config, path, init = {}) {
  const response = await fetch(`${String(config.trustedBackendBase).replace(/\/+$/, '')}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(init.headers || {}),
    },
  })
  const body = await response.json().catch(() => ({}))
  if (!response.ok || (body.code !== undefined && body.code !== 0)) {
    const error = new Error(body.message || `trusted backend request failed: ${response.status}`)
    error.statusCode = response.status
    error.details = body
    throw error
  }
  return body.data
}

async function openUrlWithPuppeteer(wsEndpoint, targetUrl) {
  if (!wsEndpoint || !targetUrl) return { opened: false, reason: 'missing_ws_or_url' }

  const { default: puppeteer } = await import('puppeteer-core')
  const browser = await puppeteer.connect({ browserWSEndpoint: wsEndpoint })
  try {
    const page = await browser.newPage()
    await page.goto(targetUrl, { waitUntil: 'domcontentloaded', timeout: 45_000 })
    return { opened: true, url: targetUrl }
  } finally {
    await browser.disconnect()
  }
}

function normalizeLaunchTask(body, environment, data) {
  const taskId = Number(body.taskId)
  if (!Number.isFinite(taskId) || taskId <= 0) {
    const error = new Error('taskId must be a positive number')
    error.statusCode = 400
    throw error
  }
  if (!body.platform) {
    const error = new Error('platform is required')
    error.statusCode = 400
    throw error
  }
  return {
    taskId,
    platform: String(body.platform),
    backendBase: body.backendBase ? String(body.backendBase).replace(/\/+$/, '') : null,
    backendTask: body.backendTask || null,
    url: body.url ? String(body.url) : null,
    selfMediaAccountId: body.selfMediaAccountId ? Number(body.selfMediaAccountId) : null,
    browserEnvironmentAccountId: body.browserEnvironmentAccountId ? Number(body.browserEnvironmentAccountId) : null,
    expectedPlatformAccountId: body.expectedPlatformAccountId ? String(body.expectedPlatformAccountId) : null,
    expectedAccountName: body.expectedAccountName ? String(body.expectedAccountName) : null,
    environmentKey: body.environmentKey,
    environmentName: environment.name || body.environmentKey,
    providerProfileId: environment.providerProfileId,
    status: 'pending',
    createdAt: nowIso(),
    claimedAt: null,
    completedAt: null,
    failedAt: null,
    requeuedAt: null,
    cancelledAt: null,
    claimOwner: null,
    lastError: null,
    adspower: {
      puppeteerWs: data?.ws?.puppeteer || null,
      selenium: data?.ws?.selenium || null,
    },
  }
}

async function handleLaunch(req, res, config) {
  const body = await readJson(req)
  await requireHelperAccess(req, config)
  const environment = normalizeProviderEnvironment(
    config,
    body.environmentKey,
    body.providerProfileId,
    body.environmentName,
  )
  const profileId = encodeURIComponent(environment.providerProfileId)
  const data = await adspowerGet(
    config,
    `/api/v1/browser/start?user_id=${profileId}&open_tabs=1&ip_tab=0`,
  )
  const task = normalizeLaunchTask(body, environment, data)
  upsertTask(task)
  await saveRuntimeTasks()
  task.openResult = await openUrlWithPuppeteer(data?.ws?.puppeteer, body.url)
  upsertTask(task)
  await saveRuntimeTasks()
  sendJson(req, res, config, 200, { ok: true, task })
}

async function handleOpenEnvironment(req, res, config) {
  const body = await readJson(req)
  await requireHelperAccess(req, config)
  const environment = normalizeProviderEnvironment(
    config,
    body.environmentKey,
    body.providerProfileId,
    body.environmentName,
  )
  const profileId = encodeURIComponent(environment.providerProfileId)
  const data = await adspowerGet(
    config,
    `/api/v1/browser/start?user_id=${profileId}&open_tabs=1&ip_tab=0`,
  )
  const openResult = await openUrlWithPuppeteer(data?.ws?.puppeteer, body.url)
  sendJson(req, res, config, 200, {
    ok: true,
    environmentKey: body.environmentKey,
    environmentName: environment.name || body.environmentKey,
    providerProfileId: environment.providerProfileId,
    openResult,
    adspower: {
      puppeteerWs: data?.ws?.puppeteer || null,
      selenium: data?.ws?.selenium || null,
    },
  })
}

async function createSemiAutoTask(body) {
  const articleId = Number(body.articleId)
  const selfMediaAccount = await resolveSelfMediaAccount(body)
  const selfMediaAccountId = selfMediaAccount.id
  if (!Number.isFinite(articleId) || articleId <= 0) {
    const error = new Error('articleId must be a positive number')
    error.statusCode = 400
    throw error
  }
  if (!body.backendToken) {
    const error = new Error('backendToken is required')
    error.statusCode = 400
    throw error
  }

  const createdTask = await backendRequest(
    body.backendBase || 'http://119.45.154.127',
    body.backendToken,
    `/api/content/articles/${articleId}/distribute-to-self-media`,
    {
      method: 'POST',
      body: JSON.stringify({
        brandId: Number(body.brandId) || null,
        selfMediaAccountId,
        coverMaterialId: body.coverMaterialId || null,
        imageMaterialIds: Array.isArray(body.imageMaterialIds) ? body.imageMaterialIds : [],
        requestId: body.requestId || `poc-${Date.now()}`,
        platformOptions: body.platformOptions || {},
      }),
    },
  )
  return {
    createdTask,
    selfMediaAccount,
  }
}

async function listSelfMediaAccounts(body) {
  const brandId = Number(body.brandId)
  if (!Number.isFinite(brandId) || brandId <= 0) {
    const error = new Error('brandId must be a positive number')
    error.statusCode = 400
    throw error
  }
  if (!body.backendToken) {
    const error = new Error('backendToken is required')
    error.statusCode = 400
    throw error
  }
  return backendRequest(
    body.backendBase || 'http://119.45.154.127',
    body.backendToken,
    `/api/content/brands/${brandId}/self-media-accounts`,
    { method: 'GET' },
  )
}

async function resolveSelfMediaAccount(body) {
  const directId = Number(body.selfMediaAccountId)
  const accounts = await listSelfMediaAccounts(body)
  if (Number.isFinite(directId) && directId > 0) {
    const matchedById = (accounts || []).find((account) => Number(account.id) === directId)
    return normalizeSelfMediaAccount(matchedById || { id: directId })
  }

  const matched = (accounts || []).find((account) => {
    if (body.platform && account.platform !== body.platform) return false
    if (body.platformAccountId && account.platformAccountId !== body.platformAccountId) return false
    return account.status === 'active'
  })
  if (!matched?.id) {
    const error = new Error('no matching active self media account')
    error.statusCode = 404
    error.details = { accounts, platform: body.platform, platformAccountId: body.platformAccountId }
    throw error
  }
  return normalizeSelfMediaAccount(matched)
}

function normalizeSelfMediaAccount(account) {
  return {
    id: Number(account.id),
    platform: account.platform || null,
    platformAccountId: account.platformAccountId ? String(account.platformAccountId) : null,
    accountName: account.accountName || account.nickname || account.name || null,
  }
}

async function handleCreateAndLaunch(req, res, config) {
  const body = await readJson(req)
  await requireHelperAccess(req, config)
  requireLegacyBackendTokenRoutesEnabled(config)
  const { createdTask, selfMediaAccount } = await createSemiAutoTask(body)
  const taskId = Number(createdTask?.id)
  if (!Number.isFinite(taskId) || taskId <= 0) {
    const error = new Error('backend did not return task data.id')
    error.statusCode = 502
    error.details = createdTask
    throw error
  }

  const environment = normalizeProviderEnvironment(
    config,
    body.environmentKey,
    body.providerProfileId,
    body.environmentName,
  )
  const profileId = encodeURIComponent(environment.providerProfileId)
  const data = await adspowerGet(
    config,
    `/api/v1/browser/start?user_id=${profileId}&open_tabs=1&ip_tab=0`,
  )
  const task = normalizeLaunchTask({
    ...body,
    taskId,
    platform: selfMediaAccount.platform || body.platform,
    selfMediaAccountId: selfMediaAccount.id,
    browserEnvironmentAccountId: createdTask.browserEnvironmentAccountId || body.browserEnvironmentAccountId || null,
    expectedPlatformAccountId: selfMediaAccount.platformAccountId,
    expectedAccountName: selfMediaAccount.accountName,
  }, environment, data)
  task.backendTask = createdTask
  task.selfMediaAccount = selfMediaAccount
  upsertTask(task)
  await saveRuntimeTasks()
  task.openResult = await openUrlWithPuppeteer(data?.ws?.puppeteer, body.url)
  upsertTask(task)
  await saveRuntimeTasks()
  sendJson(req, res, config, 200, { ok: true, createdTask, task })
}

async function handleAccounts(req, res, config) {
  const body = await readJson(req)
  await requireHelperAccess(req, config)
  requireLegacyBackendTokenRoutesEnabled(config)
  const accounts = await listSelfMediaAccounts(body)
  sendJson(req, res, config, 200, { ok: true, accounts })
}

async function handleStop(req, res, config) {
  const body = await readJson(req)
  await requireHelperAccess(req, config)
  const environment = normalizeProviderEnvironment(
    config,
    body.environmentKey,
    body.providerProfileId,
    body.environmentName,
  )
  const profileId = encodeURIComponent(environment.providerProfileId)
  const data = await adspowerGet(config, `/api/v1/browser/stop?user_id=${profileId}`)
  sendJson(req, res, config, 200, { ok: true, environmentKey: body.environmentKey, data })
}

async function handleNextTask(req, res, config, url) {
  await requireHelperAccess(req, config)
  const environmentKey = requireEnvironmentKey(url.searchParams.get('environmentKey'))
  await requeueTimedOutClaims(environmentKey)
  const task = findNextClaimableTask(environmentKey)
  if (!task) {
    const active = listTasks().find((item) => item.environmentKey === environmentKey && !isTerminalStatus(item.status))
    sendJson(req, res, config, 200, { ok: true, task: null, status: active?.status || null })
    return
  }
  task.status = 'claimed'
  task.claimedAt = nowIso()
  task.claimOwner = {
    environmentKey,
    claimedAt: task.claimedAt,
  }
  await saveRuntimeTasks()
  sendJson(req, res, config, 200, { ok: true, task })
}

async function requeueTimedOutClaims(environmentKey) {
  let changed = false
  for (const task of tasksById.values()) {
    if (task.environmentKey !== environmentKey || task.status !== 'claimed' || !task.claimedAt) continue
    const claimedMs = Date.now() - Date.parse(task.claimedAt)
    if (claimedMs <= CLAIM_TIMEOUT_MS) continue
    task.status = 'requeued'
    task.claimedAt = null
    task.claimOwner = null
    task.requeuedAt = nowIso()
    task.lastError = { message: 'claimed task timed out and was requeued' }
    changed = true
  }
  if (changed) await saveRuntimeTasks()
}

function findNextClaimableTask(environmentKey) {
  return listTasks().find((task) => (
    task.environmentKey === environmentKey
    && CLAIMABLE_STATUSES.has(task.status)
  )) || null
}

function isTerminalStatus(status) {
  return status === 'completed' || status === 'cancelled'
}

async function handleTaskComplete(req, res, config, taskId, status) {
  const body = await readJson(req)
  await requireHelperAccess(req, config)
  const environmentKey = body.environmentKey
  const task = tasksById.get(Number(taskId))
  if (!task) {
    const error = new Error('task not found')
    error.statusCode = 404
    throw error
  }
  if (environmentKey && task.environmentKey !== environmentKey) {
    const error = new Error('task environment mismatch')
    error.statusCode = 409
    throw error
  }
  if (task.status !== 'claimed') {
    const error = new Error(`task is not claimed: ${task.status}`)
    error.statusCode = 409
    throw error
  }
  if (status === 'completed') {
    task.status = 'completed'
    task.completedAt = nowIso()
    task.fillResult = body.fillResult || null
    task.claimedAt = null
    task.claimOwner = null
  } else {
    task.status = 'failed'
    task.failedAt = nowIso()
    task.failureCode = classifyFailureStatus(body.error)
    task.lastError = body.error || null
    task.claimedAt = null
    task.claimOwner = null
  }
  await saveRuntimeTasks()
  sendJson(req, res, config, 200, { ok: true, task })
}

function classifyFailureStatus(error) {
  const message = String(error?.message || error || '')
  if (message.includes('平台账号未登录') || message.includes('需登录')) return 'login_required'
  if (message.includes('账号一致性校验失败') || message.includes('账号不一致')) return 'account_mismatch'
  if (message.includes('等待编辑器超时') || message.includes('未找到')) return 'editor_not_found'
  return 'failed'
}

async function handleRequeue(req, res, config) {
  const body = await readJson(req)
  await requireHelperAccess(req, config)
  const environmentKey = requireEnvironmentKey(body.environmentKey)
  const task = resolveTaskForEnvironmentAction(body, environmentKey)
  if (!task) {
    const error = new Error('task not found')
    error.statusCode = 404
    throw error
  }
  task.status = 'requeued'
  task.claimedAt = null
  task.claimOwner = null
  task.failedAt = null
  task.completedAt = null
  task.cancelledAt = null
  task.requeuedAt = nowIso()
  task.failureCode = null
  task.lastError = null
  await saveRuntimeTasks()
  sendJson(req, res, config, 200, { ok: true, task })
}

async function handleCancel(req, res, config) {
  const body = await readJson(req)
  await requireHelperAccess(req, config)
  const environmentKey = requireEnvironmentKey(body.environmentKey)
  const task = resolveTaskForEnvironmentAction(body, environmentKey)
  if (!task) {
    const error = new Error('task not found')
    error.statusCode = 404
    throw error
  }
  task.status = 'cancelled'
  task.cancelledAt = nowIso()
  task.claimedAt = null
  task.claimOwner = null
  task.lastError = body.reason ? { message: String(body.reason) } : null
  await saveRuntimeTasks()
  sendJson(req, res, config, 200, { ok: true, task })
}

function resolveTaskForEnvironmentAction(body, environmentKey) {
  const taskId = Number(body.taskId)
  if (Number.isFinite(taskId) && taskId > 0) {
    const task = tasksById.get(taskId)
    if (!task || task.environmentKey !== environmentKey) return null
    return task
  }
  return listTasks().find((task) => (
    task.environmentKey === environmentKey
    && task.status !== 'completed'
    && task.status !== 'cancelled'
  )) || null
}

async function handleTasks(req, res, config) {
  await requireHelperAccess(req, config)
  sendJson(req, res, config, 200, { ok: true, tasks: listTasks() })
}

async function route(req, res, config) {
  if (req.method === 'OPTIONS') {
    sendJson(req, res, config, 204, {})
    return
  }

  const url = new URL(req.url, `http://${req.headers.host || `${config.host}:${config.port}`}`)
  if (req.method === 'GET' && (url.pathname === '/' || url.pathname === '/index.html')) {
    await sendFile(res, 'index.html', 'text/html; charset=utf-8')
    return
  }
  if (req.method === 'GET' && url.pathname === '/health') {
    sendJson(req, res, config, 200, {
      ok: true,
      service: 'geo-local-helper',
      time: nowIso(),
      paired: Boolean(runtimeSession?.sessionId && runtimeSession?.hmacSecret),
      session: publicSession(),
      adspower: publicAdspowerSettings(config),
    })
    return
  }
  if ((req.method === 'GET' || req.method === 'POST') && url.pathname === '/v1/settings/adspower') {
    return handleAdspowerSettings(req, res, config)
  }
  if (req.method === 'POST' && url.pathname === '/v1/c2/pairing-code') return handlePairingCode(req, res, config)
  if (req.method === 'POST' && url.pathname === '/v1/c2/pairing-status') return handlePairingStatus(req, res, config)
  if (req.method === 'POST' && url.pathname === '/v1/poc/launch') return handleLaunch(req, res, config)
  if (req.method === 'POST' && url.pathname === '/v1/poc/open-environment') return handleOpenEnvironment(req, res, config)
  if (req.method === 'POST' && url.pathname === '/v1/poc/create-and-launch') return handleCreateAndLaunch(req, res, config)
  if (req.method === 'POST' && url.pathname === '/v1/poc/accounts') return handleAccounts(req, res, config)
  if (req.method === 'POST' && url.pathname === '/v1/poc/requeue') return handleRequeue(req, res, config)
  if (req.method === 'POST' && url.pathname === '/v1/poc/cancel') return handleCancel(req, res, config)
  if (req.method === 'POST' && url.pathname === '/v1/poc/stop') return handleStop(req, res, config)
  if (req.method === 'GET' && url.pathname === '/v1/poc/tasks') return handleTasks(req, res, config)
  if (req.method === 'GET' && url.pathname === '/v1/extension/tasks/next') return handleNextTask(req, res, config, url)

  const completeMatch = url.pathname.match(/^\/v1\/extension\/tasks\/(\d+)\/complete$/)
  if (req.method === 'POST' && completeMatch) return handleTaskComplete(req, res, config, completeMatch[1], 'completed')

  const failMatch = url.pathname.match(/^\/v1\/extension\/tasks\/(\d+)\/fail$/)
  if (req.method === 'POST' && failMatch) return handleTaskComplete(req, res, config, failMatch[1], 'failed')

  sendJson(req, res, config, 404, { ok: false, error: 'not found' })
}

const config = await loadConfig()
await loadRuntimeSettings()
await loadRuntimeTasks()
await loadRuntimeSession()
await loadRuntimeNonces()
const server = http.createServer((req, res) => {
  route(req, res, config).catch((error) => {
    const statusCode = error.statusCode || 500
    sendJson(req, res, config, statusCode, {
      ok: false,
      error: error.message,
      details: error.details,
    })
  })
})

server.listen(config.port, config.host, () => {
  console.log(`GEO local helper listening on http://${config.host}:${config.port}`)
})

async function shutdown() {
  try {
    await flushRuntimeNonces()
  } catch (error) {
    console.error('Failed to flush helper nonce cache before shutdown:', error.message)
  } finally {
    server.close(() => process.exit(0))
    setTimeout(() => process.exit(0), 2_000).unref?.()
  }
}

process.once('SIGINT', () => {
  void shutdown('SIGINT')
})

process.once('SIGTERM', () => {
  void shutdown('SIGTERM')
})
