import http from 'node:http'
import { Worker } from 'node:worker_threads'
import { existsSync } from 'node:fs'
import fs from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath, URL } from 'node:url'
import crypto from 'node:crypto'
import { evaluateBaijiahaoPublishSignals, evaluateXiaohongshuPublishSignals } from './publish-check.js'

const CONFIG_PATH = new URL('../config.local.json', import.meta.url)
const EXAMPLE_CONFIG_PATH = new URL('../config.example.json', import.meta.url)
const PACKAGE_JSON_PATH = new URL('../package.json', import.meta.url)
const PUBLIC_DIR = new URL('../public/', import.meta.url)
const RUNTIME_DIR = new URL('../runtime/', import.meta.url)
const TASKS_PATH = new URL('tasks.json', RUNTIME_DIR)
const SESSION_PATH = new URL('session.json', RUNTIME_DIR)
const NONCES_PATH = new URL('nonces.json', RUNTIME_DIR)
const SETTINGS_PATH = new URL('settings.json', RUNTIME_DIR)
const TEMP_FILES_DIR = new URL('temp-files/', RUNTIME_DIR)
const tasksById = new Map()
const extensionBindIntentsByHash = new Map()
const CLAIM_TIMEOUT_MS = 30_000
const CLAIM_BACKEND_HEARTBEAT_MAX_MS = 2 * 60_000
const CLAIMABLE_STATUSES = new Set(['pending', 'requeued'])
const SIGNATURE_MAX_SKEW_SECONDS = 300
const NONCE_FLUSH_DELAY_MS = 1_000
const DEFAULT_FETCH_TIMEOUT_MS = 15_000
const ADSPOWER_FETCH_TIMEOUT_MS = 20_000
const BACKEND_FETCH_TIMEOUT_MS = 20_000
const SCHEDULE_POLL_STEP_TIMEOUT_MS = 60_000
const SCHEDULE_HEARTBEAT_INTERVAL_MS = 60_000
const EVENT_LOOP_WATCHDOG_INTERVAL_MS = 5_000
const EVENT_LOOP_WATCHDOG_THRESHOLD_MS = 90_000
const FAILED_SCHEDULE_REPORT_MAX_ATTEMPTS = 3
const SELF_MEDIA_SCHEDULE_PLATFORMS_CACHE_MS = 60_000
const EXTENSION_BIND_INTENT_TTL_MS = 2 * 60 * 1000
const GEO_ENV_EXTENSION_NAME = 'GEO 自媒体助手'
const DEFAULT_ALLOWED_WEB_ORIGINS = [
  'https://www.huanjingaigeo.com',
  'http://119.45.154.127',
]
const EXIT_CODE_PORT_IN_USE = 2
const STARTED_AT = new Date().toISOString()
const nonceCache = new Map()
let nonceFlushTimer = null
let runtimeSession = null
let runtimeSettings = { adspower: {} }
let packageInfoCache = null
let pendingPairing = null
let schedulePollInFlight = false
let scheduleHeartbeatInFlight = false
let lastSchedulePollStatus = null
let lastScheduleHeartbeatStatus = null
let cachedSelfMediaSchedulePlatforms = null
let cachedSelfMediaSchedulePlatformsAt = 0
let lastSelfMediaSchedulePlatformsError = null

process.on('uncaughtException', (error) => {
  console.error('GEO local helper uncaught exception:', error?.stack || error?.message || error)
  if (process.env.GEO_HELPER_SUPERVISED === '1') process.exit(1)
})

process.on('unhandledRejection', (reason) => {
  const message = reason?.stack || reason?.message || reason
  console.error('GEO local helper unhandled rejection:', message)
  if (process.env.GEO_HELPER_SUPERVISED === '1') process.exit(1)
})

function nowIso() {
  return new Date().toISOString()
}

async function readPackageInfo() {
  if (packageInfoCache) return packageInfoCache
  try {
    const raw = await fs.readFile(PACKAGE_JSON_PATH, 'utf8')
    const pkg = JSON.parse(raw)
    packageInfoCache = {
      name: String(pkg.name || 'geo-local-helper'),
      version: pkg.version ? String(pkg.version) : null,
    }
  } catch {
    packageInfoCache = {
      name: 'geo-local-helper',
      version: null,
    }
  }
  return packageInfoCache
}

function sha256Hex(value) {
  return crypto.createHash('sha256').update(value, 'utf8').digest('hex')
}

function hmacSha256Base64Url(secret, value) {
  return crypto.createHmac('sha256', secret).update(value, 'utf8').digest('base64url')
}

function withTimeout(promise, timeoutMs, label) {
  let timer = null
  return Promise.race([
    promise,
    new Promise((_, reject) => {
      timer = setTimeout(() => reject(new Error(`${label} timeout after ${timeoutMs}ms`)), timeoutMs)
      timer.unref?.()
    }),
  ]).finally(() => {
    if (timer) clearTimeout(timer)
  })
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
  config.trustedBackendBase ||= config.backendBase || ''
  config.enableLegacyBackendTokenRoutes = config.enableLegacyBackendTokenRoutes === true
  config.enableStaticHelperToken = config.enableStaticHelperToken === true
  for (const origin of DEFAULT_ALLOWED_WEB_ORIGINS) {
    appendAllowedOrigin(config, origin)
  }
  const trustedBackendOrigin = safeOrigin(config.trustedBackendBase)
  appendAllowedOrigin(config, trustedBackendOrigin)
  return config
}

function appendAllowedOrigin(config, origin) {
  if (!origin) return
  config.allowedOrigins ||= []
  if (!config.allowedOrigins.includes(origin)) config.allowedOrigins.push(origin)
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

function cleanupRuntimeExtensionBindIntents() {
  pruneExtensionBindIntents()
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

function pruneExtensionBindIntents() {
  const now = Date.now()
  for (const [hash, intent] of extensionBindIntentsByHash.entries()) {
    if (intent.consumedAt || Date.parse(intent.expiresAt) <= now) {
      extensionBindIntentsByHash.delete(hash)
    }
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

  const response = await fetchWithTimeout(url, { headers }, ADSPOWER_FETCH_TIMEOUT_MS)
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

function normalizeAdspowerProfile(row) {
  if (!row || typeof row !== 'object') return null
  const providerProfileId = firstScalarText(row.user_id, row.userId, row.id, row.profile_id, row.profileId)
  if (!providerProfileId) return null
  return {
    providerProfileId,
    name: firstScalarText(row.name, row.user_name, row.username, row.profile_name, row.serial_number, providerProfileId),
    serialNumber: firstScalarText(row.serial_number, row.serialNumber),
    groupName: firstScalarText(row.group_name, row.groupName),
    remark: firstScalarText(row.remark, row.remarks),
    status: firstScalarText(row.status, row.state),
  }
}

function firstScalarText(...values) {
  for (const value of values) {
    if (typeof value === 'string' && value.trim()) return value.trim()
    if (typeof value === 'number' && Number.isFinite(value)) return String(value)
  }
  return ''
}

async function listAdspowerProfiles(config, query = {}) {
  const page = Number(query.page || 1)
  const pageSize = Number(query.pageSize || query.page_size || 50)
  const search = firstText(query.search, query.keyword, query.q)
  const path = new URL('/api/v1/user/list', 'http://adspower.local')
  path.searchParams.set('page', String(Number.isFinite(page) && page > 0 ? Math.floor(page) : 1))
  path.searchParams.set('page_size', String(Number.isFinite(pageSize) && pageSize > 0 ? Math.min(Math.floor(pageSize), 100) : 50))
  if (search) path.searchParams.set('search', search)
  const data = await adspowerGet(config, `${path.pathname}${path.search}`)
  const rawList = Array.isArray(data?.list)
    ? data.list
    : Array.isArray(data?.data?.list)
      ? data.data.list
      : Array.isArray(data)
        ? data
        : []
  return {
    list: rawList.map(normalizeAdspowerProfile).filter(Boolean),
    page: Number(data?.page || page || 1),
    pageSize: Number(data?.page_size || data?.pageSize || pageSize || 50),
    total: Number(data?.total || data?.count || rawList.length),
  }
}

async function handleAdspowerProfiles(req, res, config, url) {
  await requireHelperAccess(req, config)
  const result = await listAdspowerProfiles(config, {
    page: url.searchParams.get('page'),
    pageSize: url.searchParams.get('pageSize') || url.searchParams.get('page_size'),
    search: url.searchParams.get('search') || url.searchParams.get('keyword') || url.searchParams.get('q'),
  })
  sendJson(req, res, config, 200, { ok: true, ...result })
}

function normalizeExtensionBindIntentPayload(config, body) {
  const bindCode = String(body.bindCode || '').replace(/[\s-]/g, '').toUpperCase()
  if (bindCode.length < 6) {
    const error = new Error('bindCode is required')
    error.statusCode = 400
    throw error
  }
  const environment = normalizeProviderEnvironment(
    config,
    body.environmentKey,
    body.providerProfileId,
    body.environmentName,
  )
  const ttlMs = Math.min(
    EXTENSION_BIND_INTENT_TTL_MS,
    Math.max(30_000, Number(body.expiresInSeconds || 120) * 1000),
  )
  const expiresAt = new Date(Date.now() + ttlMs).toISOString()
  return {
    bindCode,
    brandId: Number.isFinite(Number(body.brandId)) ? Number(body.brandId) : null,
    apiBase: String(body.apiBase || config.trustedBackendBase || config.backendBase || '').replace(/\/+$/, ''),
    helperBase: String(body.helperBase || `http://${config.host || '127.0.0.1'}:${config.port || 17891}`).replace(/\/+$/, ''),
    environmentKey: environment.environmentKey,
    providerProfileId: environment.providerProfileId,
    environmentName: environment.name || environment.environmentKey,
    expiresAt,
  }
}

async function handleCreateExtensionBindIntent(req, res, config) {
  const body = await readJson(req)
  await requireHelperAccess(req, config)
  const payload = normalizeExtensionBindIntentPayload(config, body)
  const intentToken = crypto.randomBytes(32).toString('base64url')
  const intent = {
    ...payload,
    intentTokenHash: sha256Hex(intentToken),
    createdAt: nowIso(),
    consumedAt: null,
  }
  extensionBindIntentsByHash.set(intent.intentTokenHash, intent)
  cleanupRuntimeExtensionBindIntents()
  sendJson(req, res, config, 200, {
    ok: true,
    intentToken,
    expiresAt: intent.expiresAt,
    environmentKey: intent.environmentKey,
    providerProfileId: intent.providerProfileId,
    environmentName: intent.environmentName,
  })
}

async function handleConsumeExtensionBindIntent(req, res, config) {
  const body = await readJson(req)
  pruneExtensionBindIntents()
  const intentToken = String(body.intentToken || '').trim()
  if (intentToken.length < 32) {
    const error = new Error('intentToken is required')
    error.statusCode = 400
    throw error
  }
  const hash = sha256Hex(intentToken)
  const intent = extensionBindIntentsByHash.get(hash)
  if (!intent) {
    const error = new Error('extension bind intent not found or expired')
    error.statusCode = 404
    throw error
  }
  const expectedEnvironmentKey = String(body.environmentKey || '').trim()
  const expectedProviderProfileId = String(body.providerProfileId || '').trim()
  if (expectedEnvironmentKey && expectedEnvironmentKey !== intent.environmentKey) {
    extensionBindIntentsByHash.delete(hash)
    cleanupRuntimeExtensionBindIntents()
    const error = new Error('extension bind intent environment mismatch')
    error.statusCode = 409
    throw error
  }
  if (expectedProviderProfileId && expectedProviderProfileId !== intent.providerProfileId) {
    extensionBindIntentsByHash.delete(hash)
    cleanupRuntimeExtensionBindIntents()
    const error = new Error('extension bind intent profile mismatch')
    error.statusCode = 409
    throw error
  }
  extensionBindIntentsByHash.delete(hash)
  cleanupRuntimeExtensionBindIntents()
  sendJson(req, res, config, 200, {
    ok: true,
    bindCode: intent.bindCode,
    brandId: intent.brandId,
    apiBase: intent.apiBase || config.trustedBackendBase || config.backendBase || '',
    helperBase: intent.helperBase || `http://${config.host || '127.0.0.1'}:${config.port || 17891}`,
    environmentKey: intent.environmentKey,
    providerProfileId: intent.providerProfileId,
    environmentName: intent.environmentName,
    expiresAt: intent.expiresAt,
  })
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

  const response = await fetchWithTimeout(`${String(backendBase).replace(/\/+$/, '')}${path}`, {
    ...init,
    headers,
  }, BACKEND_FETCH_TIMEOUT_MS)
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
  const response = await fetchWithTimeout(`${String(config.trustedBackendBase).replace(/\/+$/, '')}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(init.headers || {}),
    },
  }, BACKEND_FETCH_TIMEOUT_MS)
  const body = await response.json().catch(() => ({}))
  if (!response.ok || (body.code !== undefined && body.code !== 0)) {
    const error = new Error(body.message || `trusted backend request failed: ${response.status}`)
    error.statusCode = response.status
    error.details = body
    throw error
  }
  return body.data
}

function signedBackendHeaders(method, path, bodyText = '') {
  if (!runtimeSession?.sessionId || !runtimeSession?.hmacSecret) {
    const error = new Error('local helper is not paired with backend')
    error.statusCode = 401
    throw error
  }
  const normalizedMethod = String(method || 'GET').toUpperCase()
  const timestamp = String(Math.floor(Date.now() / 1000))
  const nonce = crypto.randomBytes(16).toString('hex')
  const helperAccess = `helper.session.${runtimeSession.sessionId}`
  const bodyHash = sha256Hex(bodyText || '')
  const canonical = canonicalRequest(normalizedMethod, path, bodyHash, timestamp, nonce, helperAccess)
  return {
    'X-Geo-Helper-Access': helperAccess,
    'X-Geo-Helper-Timestamp': timestamp,
    'X-Geo-Helper-Nonce': nonce,
    'X-Geo-Helper-Signature': hmacSha256Base64Url(runtimeSession.hmacSecret, canonical),
  }
}

async function signedTrustedBackendRequest(config, path, init = {}) {
  const method = String(init.method || 'GET').toUpperCase()
  const bodyText = typeof init.body === 'string' ? init.body : ''
  return trustedBackendRequest(config, path, {
    ...init,
    method,
    headers: {
      ...(init.headers || {}),
      ...signedBackendHeaders(method, path, bodyText),
    },
  })
}

async function openUrlWithPuppeteer(wsEndpoint, targetUrl) {
  if (!wsEndpoint || !targetUrl) return { opened: false, reason: 'missing_ws_or_url' }

  const { default: puppeteer } = await import('puppeteer-core')
  const browser = await puppeteer.connect({ browserWSEndpoint: wsEndpoint, protocolTimeout: 30_000 })
  try {
    const page = await browser.newPage()
    await page.goto(targetUrl, { waitUntil: 'domcontentloaded', timeout: 45_000 })
    return { opened: true, url: targetUrl }
  } finally {
    await browser.disconnect()
  }
}

function extensionIdFromTargetUrl(value) {
  const match = String(value || '').match(/^chrome-extension:\/\/([^/]+)\//)
  return match ? match[1] : ''
}

async function inspectGeoEnvExtensionTarget(target) {
  const url = target.url()
  const extensionId = extensionIdFromTargetUrl(url)
  if (!extensionId) return null
  const result = {
    extensionId,
    targetType: target.type(),
    targetUrl: url,
    name: '',
    version: '',
  }
  if (target.type() === 'service_worker') {
    const worker = await target.worker().catch(() => null)
    if (worker) {
      const manifest = await worker.evaluate(() => chrome.runtime.getManifest()).catch(() => null)
      result.name = String(manifest?.name || '')
      result.version = String(manifest?.version || '')
    }
  }
  return result
}

function isGeoEnvExtensionTarget(info) {
  if (!info?.extensionId) return false
  return info.name === GEO_ENV_EXTENSION_NAME
}

async function inspectGeoEnvExtension(wsEndpoint) {
  if (!wsEndpoint) {
    return {
      installed: false,
      detected: false,
      status: 'unknown',
      reason: 'missing_puppeteer_ws',
    }
  }
  const { default: puppeteer } = await import('puppeteer-core')
  const browser = await puppeteer.connect({ browserWSEndpoint: wsEndpoint, protocolTimeout: 30_000 })
  try {
    const targets = browser.targets()
    const inspected = []
    for (const target of targets) {
      if (!String(target.url() || '').startsWith('chrome-extension://')) continue
      const info = await inspectGeoEnvExtensionTarget(target).catch(() => null)
      if (info) inspected.push(info)
    }
    const matched = inspected.find(isGeoEnvExtensionTarget) || null
    if (matched) {
      return {
        installed: true,
        detected: true,
        status: 'installed',
        extensionId: matched.extensionId,
        name: matched.name || GEO_ENV_EXTENSION_NAME,
        version: matched.version || null,
        targetType: matched.targetType,
        targetUrl: matched.targetUrl,
      }
    }
    return {
      installed: false,
      detected: false,
      status: 'not_detected',
      reason: 'geo_env_extension_target_not_found',
      inspectedExtensionTargets: inspected.length,
    }
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

async function claimAndLaunchScheduledTask(config, platform = 'toutiao') {
  await flushPendingScheduleFailureReports(config, platform)
  await flushPendingScheduleSuccessReports(config, platform)
  const path = `/api/v1/local-agent/self-media-schedules/claim-next?platform=${encodeURIComponent(platform)}`
  const claim = await signedTrustedBackendRequest(config, path, { method: 'GET' })
  if (!claim?.task || !claim?.launch) {
    return { ok: true, claimed: false, claimBlockedReason: claim?.claimBlockedReason || 'NO_DUE_TASK' }
  }
  const taskId = Number(claim.launch.taskId || claim.task.id)
  const existing = tasksById.get(taskId)
  if (isReusableActiveTask(existing)) {
    return { ok: true, claimed: true, reused: true, task: existing, schedule: claim.schedule }
  }
  try {
    const environment = normalizeProviderEnvironment(
      config,
      claim.launch.environmentKey,
      claim.launch.providerProfileId,
      claim.launch.environmentName,
    )
    const profileId = encodeURIComponent(environment.providerProfileId)
    const data = await adspowerGet(
      config,
      `/api/v1/browser/start?user_id=${profileId}&open_tabs=1&ip_tab=0`,
    )
    const task = normalizeLaunchTask({
      taskId,
      platform: claim.launch.platform || claim.task.platform,
      backendBase: config.trustedBackendBase,
      backendTask: claim.task,
      url: claim.launch.url || defaultPublishUrlForPlatform(claim.launch.platform || claim.task.platform),
      selfMediaAccountId: claim.launch.selfMediaAccountId || claim.task.selfMediaAccountId,
      browserEnvironmentAccountId: claim.launch.browserEnvironmentAccountId || claim.task.browserEnvironmentAccountId,
      expectedPlatformAccountId: claim.launch.expectedPlatformAccountId,
      expectedAccountName: claim.launch.expectedAccountName,
      environmentKey: claim.launch.environmentKey || claim.task.environmentKey,
      providerProfileId: claim.launch.providerProfileId || claim.task.providerProfileId,
      environmentName: claim.launch.environmentName || claim.launch.environmentKey || claim.task.environmentKey,
    }, environment, data)
    task.schedule = claim.schedule || null
    task.platformScheduledAt = claim.schedule?.platformScheduledAt || null
    upsertTask(task)
    await saveRuntimeTasks()
    task.openResult = await openUrlWithPuppeteer(data?.ws?.puppeteer, task.url)
    upsertTask(task)
    await saveRuntimeTasks()
    return { ok: true, claimed: true, task, schedule: claim.schedule }
  } catch (error) {
    const failureTask = {
      taskId,
      platform: claim.launch.platform || claim.task.platform,
      backendTask: claim.task,
      schedule: claim.schedule || null,
      lastError: {
        code: 'LOCAL_HELPER_LAUNCH_FAILED',
        message: error instanceof Error ? error.message : String(error),
      },
    }
    await reportScheduleExecutionFailed(config, failureTask, {
      failureCode: 'LOCAL_HELPER_LAUNCH_FAILED',
      failureMessage: failureTask.lastError.message,
    }).catch((reportError) => {
      console.error('Failed to report schedule launch failure:', formatBackendError(reportError))
    })
    throw error
  }
}

async function claimAndCheckPublishResult(config, platform = 'toutiao') {
  const path = `/api/v1/local-agent/self-media-schedules/publish-checks/claim-next?platform=${encodeURIComponent(platform)}`
  const claim = await signedTrustedBackendRequest(config, path, { method: 'GET' })
  if (!claim?.schedule || !claim?.launch) {
    return { ok: true, claimed: false, claimBlockedReason: claim?.claimBlockedReason || 'NO_DUE_TASK' }
  }
  const scheduleId = Number(claim.launch.scheduleId || claim.schedule.id)
  const environment = normalizeProviderEnvironment(
    config,
    claim.launch.environmentKey,
    claim.launch.providerProfileId,
    claim.launch.environmentName,
  )
  const profileId = encodeURIComponent(environment.providerProfileId)
  const data = await adspowerGet(
    config,
    `/api/v1/browser/start?user_id=${profileId}&open_tabs=1&ip_tab=0`,
  )
  try {
    const checkSchedule = {
      ...claim.schedule,
      expectedPlatformAccountId: claim.launch.expectedPlatformAccountId || claim.schedule.expectedPlatformAccountId || '',
      expectedAccountName: claim.launch.expectedAccountName || claim.schedule.expectedAccountName || '',
    }
    const checkUrl = worksListUrlForPublishCheck(
      claim.launch.platform || claim.schedule.platform,
      claim.launch.url,
      {
        launch: claim.launch,
        schedule: checkSchedule,
      },
    )
    const result = await checkPublishResultInAdspowerPage(
      data?.ws?.puppeteer,
      checkUrl,
      checkSchedule,
    )
    if (result.failed) {
      await reportPublishCheckFailed(config, scheduleId, result)
      return { ok: true, claimed: true, scheduleId, outcome: 'failed', result }
    }
    if (result.found) {
      await reportPublishCheckPublished(config, scheduleId, result)
      return { ok: true, claimed: true, scheduleId, outcome: 'published', result }
    }
    await reportPublishCheckUnknown(config, scheduleId, result)
    return { ok: true, claimed: true, scheduleId, outcome: 'unknown', result }
  } catch (error) {
    await reportPublishCheckFailed(config, scheduleId, {
      failureCode: 'PUBLISH_RESULT_CHECK_HELPER_FAILED',
      failureMessage: error instanceof Error ? error.message : String(error),
    })
    throw error
  }
}

async function checkPublishResultInAdspowerPage(wsEndpoint, targetUrl, schedule) {
  if (!wsEndpoint || !targetUrl) {
    throw new Error('publish result check requires active AdsPower browser and works list url')
  }
  const { default: puppeteer } = await import('puppeteer-core')
  const browser = await puppeteer.connect({ browserWSEndpoint: wsEndpoint, protocolTimeout: 30_000 })
  try {
    let effectiveTargetUrl = targetUrl
    const platform = String(schedule?.platform || '').trim().toLowerCase()
    if (platform === 'baijiahao' && !baijiahaoWorksListHasAppId(effectiveTargetUrl)) {
      const appId = baijiahaoAppIdFromContext(schedule)
      if (appId) {
        effectiveTargetUrl = buildBaijiahaoWorksListUrl(appId)
        schedule.expectedPlatformAccountId = schedule.expectedPlatformAccountId || appId
      } else {
        throw new Error('baijiahao publish check requires app_id from self media account platformAccountId')
      }
    }
    const { page } = await reuseOrCreatePublishCheckPage(browser, platform, effectiveTargetUrl)
    await page.goto(effectiveTargetUrl, { waitUntil: 'domcontentloaded', timeout: 45_000 })
    await delay(3_000)
    const deadline = Date.now() + publishCheckEvaluateTimeoutMs(platform)
    let latest = null
    let reloadCount = 0
    while (Date.now() < deadline) {
      latest = await evaluatePublishResult(page, schedule)
      if (latest.found) return latest
      if (shouldReloadPublishCheckPage(platform, latest, reloadCount)) {
        reloadCount += 1
        await page.reload({ waitUntil: 'domcontentloaded', timeout: 45_000 }).catch(() => null)
        await delay(3_000)
        latest = {
          ...(latest || {}),
          reloadedForStaleWorksList: true,
          reloadCount,
          reason: latest?.reason || 'works list looked stale before reload',
        }
        continue
      }
      await delay(2_000)
    }
    return latest ? { ...latest, reloadCount } : {
      found: false,
      reason: 'works list not evaluated',
      targetTitle: schedule?.publishCheckTitle || '',
      url: page.url(),
      reloadCount,
    }
  } finally {
    await browser.disconnect()
  }
}

function publishCheckEvaluateTimeoutMs(platform) {
  const normalized = String(platform || '').trim().toLowerCase()
  return normalized === 'douyin' ? 45_000 : 20_000
}

function shouldReloadPublishCheckPage(platform, result, reloadCount) {
  const normalized = String(platform || '').trim().toLowerCase()
  if (normalized !== 'douyin' || reloadCount >= 2 || result?.found) return false
  const text = String(result?.textSample || '')
  if (/没有更多作品|暂无作品|共\s*0\s*个作品/.test(text)) return true
  const looksLikeManageShell = text.includes('作品管理')
    && text.includes('全部作品')
    && text.includes('已发布')
    && text.includes('审核中')
  if (looksLikeManageShell && !result?.hasTitle) return true
  return !result?.hasTitle && !result?.hasPublishedSignal
}

async function reuseOrCreatePublishCheckPage(browser, platform, targetUrl) {
  const pages = await browser.pages()
  const reusablePage = pages.find((page) => isReusablePublishCheckPage(platform, page.url(), targetUrl))
  if (reusablePage) {
    await reusablePage.bringToFront().catch(() => {})
    return { page: reusablePage, created: false }
  }
  return { page: await browser.newPage(), created: true }
}

function isReusablePublishCheckPage(platform, currentUrl, targetUrl) {
  const normalized = String(platform || '').trim().toLowerCase()
  let current
  let target
  try {
    current = new URL(currentUrl)
    target = new URL(targetUrl)
  } catch (_) {
    return false
  }
  if (current.hostname !== target.hostname) return false
  if (normalized === 'baijiahao') {
    return current.hostname.includes('baijiahao.baidu.com')
      && current.pathname === '/builder/rc/content'
  }
  if (normalized === 'zhihu') {
    return current.hostname.includes('zhihu.com')
      && current.pathname.includes('/creator/manage/creation/article')
  }
  if (normalized === 'xiaohongshu') {
    return current.hostname.includes('xiaohongshu.com')
      && current.pathname.includes('/new/note-manager')
  }
  if (normalized === 'toutiao') {
    return current.hostname.includes('toutiao.com')
      && current.pathname.includes('/profile_v4/manage/content')
  }
  return current.pathname === target.pathname
}

async function evaluatePublishResult(page, schedule) {
  const platform = String(schedule?.platform || '').trim().toLowerCase()
  if (platform === 'zhihu') {
    return evaluateZhihuPublishResult(page, schedule)
  }
  if (platform === 'xiaohongshu') {
    return evaluateXiaohongshuPublishResult(page, schedule)
  }
  if (platform === 'baijiahao') {
    return evaluateBaijiahaoPublishResult(page, schedule)
  }
  if (platform === 'douyin') {
    return evaluateDouyinPublishResult(page, schedule)
  }
  return evaluateToutiaoPublishResult(page, schedule)
}

async function evaluateToutiaoPublishResult(page, schedule) {
  const target = {
    title: schedule?.publishCheckTitle || '',
    locationName: schedule?.publishCheckLocationName || '',
    platformScheduledAt: schedule?.platformScheduledAt || schedule?.plannedPublishAt || '',
  }
  return page.evaluate((input) => {
    const normalize = (value) => String(value || '').replace(/\s+/g, '').trim()
    const parseTimeMs = (value) => {
      const match = String(value || '').match(/(\d{4})-(\d{1,2})-(\d{1,2})[T\s](\d{1,2}):(\d{1,2})/)
      if (!match) return Number.NaN
      return new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]), Number(match[4]), Number(match[5])).getTime()
    }
    const text = document.body?.innerText || ''
    const normalizedText = normalize(text)
    const normalizedTitle = normalize(input.title)
    const titleProbe = normalizedTitle.length > 24 ? normalizedTitle.slice(0, 24) : normalizedTitle
    const locationProbe = normalize(input.locationName)
    const hasTitle = Boolean(titleProbe && normalizedText.includes(titleProbe))
    const hasLocation = !locationProbe || normalizedText.includes(locationProbe)
    const scheduledAtMs = parseTimeMs(input.platformScheduledAt)
    const isBeforeScheduledAt = Number.isFinite(scheduledAtMs) && scheduledAtMs > Date.now()
    const hasScheduledSignal = /定时发布中|待发布|将于\d{1,2}[-月]\d{1,2}|发布时间/.test(text)
    const hasPublishedSignal = /已发布|发布成功|审核中/.test(text)
    let matchedUrl = ''
    if (hasTitle) {
      const anchors = Array.from(document.querySelectorAll('a[href]'))
      const anchor = anchors.find((item) => {
        const href = item.href || ''
        return normalize(item.textContent).includes(titleProbe) && /toutiao\.com\/item\//.test(href)
      }) || anchors.find((item) => {
        const href = item.href || ''
        const boxText = normalize(item.closest('.article-card, [class*="article-card"], li, tr, div')?.textContent || item.textContent)
        return boxText.includes(titleProbe) && /toutiao\.com\/item\//.test(href)
      })
      matchedUrl = anchor?.href || ''
    }
    const pendingScheduled = hasTitle && hasLocation && (isBeforeScheduledAt || (hasScheduledSignal && !hasPublishedSignal))
    const found = hasTitle && hasLocation && !isBeforeScheduledAt && hasPublishedSignal
    return {
      found,
      pendingScheduled,
      reason: pendingScheduled
        ? 'platform schedule time not due'
        : hasTitle && hasLocation && !hasPublishedSignal
          ? 'title matched but published signal missing'
          : 'title not matched',
      hasTitle,
      hasLocation,
      hasScheduledSignal,
      hasPublishedSignal,
      isBeforeScheduledAt,
      platformStatus: found ? (/审核中/.test(text) ? 'reviewing' : 'published') : (pendingScheduled ? 'scheduled' : 'unknown'),
      pageStatusCode: found ? (/审核中/.test(text) ? 'reviewing' : 'published') : (pendingScheduled ? 'scheduled' : ''),
      targetTitle: input.title,
      locationName: input.locationName,
      platformScheduledAt: input.platformScheduledAt,
      url: location.href,
      platformPublishedUrl: found ? matchedUrl : '',
      pageTitle: document.title,
      textSample: text.slice(0, 1200),
    }
  }, target)
}

async function evaluateZhihuPublishResult(page, schedule) {
  const target = {
    title: schedule?.publishCheckTitle || '',
    platformScheduledAt: schedule?.platformScheduledAt || schedule?.plannedPublishAt || '',
  }
  return page.evaluate((input) => {
    const normalize = (value) => String(value || '').replace(/\s+/g, '').trim()
    const isPublishedZhihuPath = (pathname) => /^\/p\/[^/]+/.test(pathname) || /^\/article\/[^/]+/.test(pathname)
    const text = document.body?.innerText || ''
    const normalizedText = normalize(text)
    const normalizedTitle = normalize(input.title)
    const titleProbe = normalizedTitle.length > 24 ? normalizedTitle.slice(0, 24) : normalizedTitle
    const hasTitle = Boolean(titleProbe && normalizedText.includes(titleProbe))
    const hasPublishedSignal = /发布成功|已发布|审核中|发布于\d{4}[-年]\d{1,2}[-月]\d{1,2}/.test(text)
      || isPublishedZhihuPath(location.pathname)
    const normalizeZhihuUrl = (value) => {
      try {
        const url = new URL(value || location.href, location.href)
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
      } catch (_) {
        return String(value || '')
      }
    }
    let matchedUrl = ''
    if (hasTitle) {
      const anchors = Array.from(document.querySelectorAll('a[href]'))
      const anchor = anchors.find((item) => {
        const href = item.href || ''
        return normalize(item.textContent).includes(titleProbe) && /zhuanlan\.zhihu\.com\/(p|article)\//.test(href)
      })
      matchedUrl = anchor?.href || ''
    }
    const realUrlSource = matchedUrl || (isPublishedZhihuPath(location.pathname) ? location.href : '')
    const realUrl = realUrlSource ? normalizeZhihuUrl(realUrlSource) : ''
    return {
      found: hasTitle && hasPublishedSignal,
      hasTitle,
      hasPublishedSignal,
      targetTitle: input.title,
      platformScheduledAt: input.platformScheduledAt,
      url: location.href,
      platformPublishedUrl: hasTitle && hasPublishedSignal ? realUrl : '',
      pageTitle: document.title,
      textSample: text.slice(0, 1200),
    }
  }, target)
}

async function evaluateXiaohongshuPublishResult(page, schedule) {
  const target = {
    title: schedule?.publishCheckTitle || '',
    platformScheduledAt: schedule?.platformScheduledAt || schedule?.plannedPublishAt || '',
  }
  const pageState = await page.evaluate(() => {
    const text = document.body?.innerText || ''
    return {
      text,
      url: location.href,
      pageTitle: document.title,
      anchors: Array.from(document.querySelectorAll('a[href]'))
        .map((item) => ({ text: item.textContent || '', href: item.href || '' }))
        .slice(0, 80),
    }
  })
  const result = evaluateXiaohongshuPublishSignals(target, pageState)
  if (result.found && !result.platformPublishedUrl) {
    const detailUrl = await openXiaohongshuPublishedNoteDetail(page, schedule).catch((error) => {
      result.detailOpenError = error instanceof Error ? error.message : String(error)
      return ''
    })
    if (detailUrl) {
      result.platformPublishedUrl = detailUrl
      result.url = detailUrl
    }
  }
  return result
}

async function openXiaohongshuPublishedNoteDetail(page, schedule) {
  const title = schedule?.publishCheckTitle || ''
  const browser = page.browser()
  const beforeTargets = new Set(browser.targets().map((target) => target._targetId || target.url()))
  const clickTarget = await page.evaluate((input) => {
    const normalize = (value) => String(value || '').replace(/\s+/g, '').trim()
    const title = normalize(input.title)
    const titleProbe = title.length > 18 ? title.slice(0, 18) : title
    if (!titleProbe) return { clickReady: false, reason: 'missing title' }
    const cards = Array.from(document.querySelectorAll('.note-card, [class*="note-card"], section, article, li, div'))
      .map((el) => {
        const rect = el.getBoundingClientRect()
        const text = normalize(el.innerText || el.textContent || '')
        const hasMedia = Boolean(el.querySelector('a[href*="/explore/"], img, [style*="background-image"], .media, [class*="media"]'))
        const noteClass = String(el.className || '').includes('note-card') ? 1 : 0
        return { el, rect, text, hasMedia, noteClass }
      })
      .filter((item) => item.text.includes(titleProbe)
        && item.rect.width >= 180
        && item.rect.height >= 120
        && item.rect.width <= 1200
        && item.rect.height <= 800)
      .sort((left, right) => {
        return right.noteClass - left.noteClass
          || Number(right.hasMedia) - Number(left.hasMedia)
          || left.rect.top - right.rect.top
      })
    const card = cards[0]?.el
    if (!card) {
      return {
        clickReady: false,
        reason: 'card not found',
        candidateCount: cards.length,
      }
    }
    const directLink = card.querySelector('a[href*="/explore/"], a[href*="/discovery/item/"]')
    if (directLink?.href) {
      return {
        clickReady: true,
        href: directLink.href,
        reason: 'direct link found',
      }
    }
    const target = card.querySelector('a[href*="/explore/"], img, [style*="background-image"], .media, [class*="media"]') || card
    target.scrollIntoView({ block: 'center', inline: 'center' })
    const rect = target.getBoundingClientRect()
    return {
      clickReady: true,
      reason: 'click point found',
      clientX: Math.round(rect.left + Math.min(Math.max(rect.width / 2, 12), Math.max(rect.width - 12, 12))),
      clientY: Math.round(rect.top + Math.min(Math.max(rect.height / 2, 12), Math.max(rect.height - 12, 12))),
      targetText: normalize(target.innerText || target.textContent || '').slice(0, 120),
    }
  }, { title })
  if (!clickTarget?.clickReady) {
    throw new Error(`xiaohongshu note card click failed: ${clickTarget?.reason || 'unknown'}`)
  }
  if (clickTarget.href && /xiaohongshu\.com\/(explore|discovery\/item)\//.test(clickTarget.href)) {
    return clickTarget.href
  }
  if (!Number.isFinite(clickTarget.clientX) || !Number.isFinite(clickTarget.clientY)) {
    throw new Error(`xiaohongshu note card click point invalid: ${JSON.stringify(clickTarget).slice(0, 500)}`)
  }
  await page.mouse.click(clickTarget.clientX, clickTarget.clientY, { delay: 30 })
  const target = await browser.waitForTarget((item) => {
    const url = item.url()
    if (!/xiaohongshu\.com\/(explore|discovery\/item)\//.test(url)) return false
    const key = item._targetId || url
    return !beforeTargets.has(key)
  }, { timeout: 12_000 }).catch(() => null)
  const detailPage = target ? await target.page().catch(() => null) : await newestXiaohongshuExplorePage(browser, beforeTargets)
  if (!detailPage) {
    throw new Error('xiaohongshu note detail tab not found')
  }
  await detailPage.bringToFront().catch(() => {})
  await detailPage.waitForFunction(
    (input) => {
      const normalize = (value) => String(value || '').replace(/\s+/g, '').trim()
      const title = normalize(input.title)
      const titleProbe = title.length > 18 ? title.slice(0, 18) : title
      const text = normalize(document.body?.innerText || '')
      return Boolean(/\/(explore|discovery\/item)\//.test(location.pathname) && (!titleProbe || text.includes(titleProbe)))
    },
    { timeout: 15_000 },
    { title },
  ).catch(() => null)
  const verification = await detailPage.evaluate((input) => {
    const normalize = (value) => String(value || '').replace(/\s+/g, '').trim()
    const title = normalize(input.title)
    const titleProbe = title.length > 18 ? title.slice(0, 18) : title
    const text = normalize(document.body?.innerText || '')
    return {
      url: location.href,
      titleMatched: Boolean(!titleProbe || text.includes(titleProbe)),
      textSample: (document.body?.innerText || '').slice(0, 500),
    }
  }, { title })
  if (!verification.titleMatched || !/xiaohongshu\.com\/(explore|discovery\/item)\//.test(verification.url || '')) {
    throw new Error(`xiaohongshu note detail mismatch: ${JSON.stringify(verification).slice(0, 500)}`)
  }
  return verification.url
}

async function newestXiaohongshuExplorePage(browser, beforeTargets) {
  const pages = await browser.pages()
  const candidates = []
  for (const item of pages) {
    const url = item.url()
    if (!/xiaohongshu\.com\/(explore|discovery\/item)\//.test(url)) continue
    const target = item.target()
    const key = target?._targetId || url
    candidates.push({ page: item, isNew: !beforeTargets.has(key) })
  }
  candidates.sort((left, right) => Number(right.isNew) - Number(left.isNew))
  return candidates[0]?.page || null
}

async function evaluateBaijiahaoPublishResult(page, schedule) {
  const target = {
    title: schedule?.publishCheckTitle || '',
    platformScheduledAt: schedule?.platformScheduledAt || schedule?.plannedPublishAt || '',
  }
  const pageState = await page.evaluate(() => {
    const text = document.body?.innerText || ''
    return {
      text,
      url: location.href,
      pageTitle: document.title,
      anchors: Array.from(document.querySelectorAll('a[href]'))
        .map((item) => ({ text: item.textContent || '', href: item.href || '' }))
        .slice(0, 80),
    }
  })
  return evaluateBaijiahaoPublishSignals(target, pageState)
}

async function evaluateDouyinPublishResult(page, schedule) {
  const target = {
    title: schedule?.publishCheckTitle || '',
    platformScheduledAt: schedule?.platformScheduledAt || schedule?.plannedPublishAt || '',
  }
  return page.evaluate((input) => {
    const normalize = (value) => String(value || '').replace(/\s+/g, '').trim()
    const normalizeCompact = (value) => normalize(value)
      .replace(/[年月/.]/g, '-')
      .replace(/[日号]/g, '')
      .replace(/(\d{4})-(\d{1,2})-(\d{1,2})/, (_, y, m, d) => `${y}-${m.padStart(2, '0')}-${d.padStart(2, '0')}`)
      .replace(/(\d{1,2}):(\d{1,2})/, (_, h, m) => `${h.padStart(2, '0')}:${m.padStart(2, '0')}`)
    const scheduleVariants = (value) => {
      const raw = String(value || '').trim()
      if (!raw) return []
      const compact = normalizeCompact(raw.replace('T', ' '))
      const withoutSeconds = compact.replace(/:\d{2}$/, '')
      return Array.from(new Set([raw, raw.replace('T', ' '), compact, withoutSeconds].filter(Boolean)))
    }
    const title = normalize(input.title)
    const titleProbe = title.length > 24 ? title.slice(0, 24) : title
    const expectedScheduleVariants = scheduleVariants(input.platformScheduledAt)
    const isVisible = (el) => {
      const rect = el?.getBoundingClientRect?.()
      const style = el ? getComputedStyle(el) : null
      return Boolean(rect && rect.width > 0 && rect.height > 0 && style?.display !== 'none' && style?.visibility !== 'hidden')
    }
    const records = Array.from(document.querySelectorAll('section, article, li, tr, div'))
      .filter(isVisible)
      .map((el) => {
        const rect = el.getBoundingClientRect()
        const text = String(el.innerText || el.textContent || '').replace(/\s+/g, ' ').trim()
        const compactText = normalizeCompact(text)
        const links = Array.from(el.querySelectorAll('a[href]')).map((link) => ({
          text: String(link.textContent || '').trim(),
          href: link.href || '',
        }))
        const images = Array.from(el.querySelectorAll('img[src]')).map((img) => img.src || '').filter(Boolean)
        return { el, rect, text, compactText, links, images }
      })
      .filter((item) => item.text
        && item.rect.width >= 260
        && item.rect.height >= 60
        && item.rect.width <= 1600
        && item.rect.height <= 460)
      .filter((item) => titleProbe && normalize(item.text).includes(titleProbe))
      .filter((item) => {
        if (!expectedScheduleVariants.length) return true
        return expectedScheduleVariants.some((value) => value && item.compactText.includes(normalizeCompact(value)))
          || /已发布|审核中|发布成功/.test(item.text)
      })
      .map((item) => {
        let score = 0
        if (titleProbe && normalize(item.text).includes(titleProbe)) score += 1000
        if (/已发布|发布成功/.test(item.text)) score += 340
        if (/审核中/.test(item.text)) score += 260
        if (/定时发布中|修改定时/.test(item.text)) score += 180
        if (expectedScheduleVariants.some((value) => value && item.compactText.includes(normalizeCompact(value)))) score += 500
        if (item.images.length) score += 40
        if (/删除作品|作品置顶|设置权限/.test(item.text)) score += 30
        if (/草稿|未通过/.test(item.text) && !/已发布|审核中|发布成功/.test(item.text)) score -= 500
        return { ...item, score }
      })
      .sort((left, right) => right.score - left.score)
    const record = records[0]
    if (!record) {
      const text = document.body?.innerText || ''
      return {
        found: false,
        hasTitle: Boolean(titleProbe && normalize(text).includes(titleProbe)),
        hasPublishedSignal: /定时发布中|已发布|审核中|发布成功/.test(text),
        targetTitle: input.title,
        platformScheduledAt: input.platformScheduledAt,
        url: location.href,
        pageTitle: document.title,
        textSample: text.slice(0, 1200),
      }
    }
    const statusText = (() => {
      const match = record.text.match(/(定时发布中|已发布|审核中|发布成功|未通过|草稿)/)
      return match?.[1] || ''
    })()
    const pageStatusCode = (() => {
      if (/已发布|发布成功/.test(statusText)) return 'published'
      if (/审核中/.test(statusText)) return 'reviewing'
      if (/定时发布中/.test(statusText)) return 'scheduled'
      return ''
    })()
    const publishedLink = record.links.find((link) => /\/video\/|\/note\/|modal_id=|item_id=/.test(link.href)) || record.links[0]
    const publishId = (() => {
      const href = publishedLink?.href || ''
      const patterns = [/\/video\/(\d+)/, /modal_id=(\d+)/, /item_id=(\d+)/, /\/note\/([^/?#]+)/]
      for (const pattern of patterns) {
        const match = href.match(pattern)
        if (match?.[1]) return match[1]
      }
      return ''
    })()
    return {
      found: ['published', 'reviewing'].includes(pageStatusCode),
      pendingScheduled: pageStatusCode === 'scheduled',
      reason: pageStatusCode === 'scheduled' ? 'platform schedule time not due' : '',
      hasTitle: true,
      hasPublishedSignal: Boolean(pageStatusCode),
      platformStatus: pageStatusCode || 'matched',
      pageStatusCode,
      pageStatus: statusText,
      targetTitle: input.title,
      platformScheduledAt: input.platformScheduledAt,
      scheduledAtText: expectedScheduleVariants.find((value) => value && record.compactText.includes(normalizeCompact(value))) || '',
      url: publishedLink?.href || location.href,
      platformPublishedUrl: '',
      platformPublishId: publishId,
      coverImageUrl: record.images[0] || '',
      pageTitle: document.title,
      matchedText: record.text.slice(0, 300),
      textSample: record.text.slice(0, 1200),
    }
  }, target)
}

async function reportPublishCheckPublished(config, scheduleId, result) {
  const query = new URLSearchParams()
  if (result?.platformPublishedUrl) query.set('platformPublishedUrl', result.platformPublishedUrl)
  query.set('diagnosticsJson', shortDiagnosticsJson(result))
  const path = `/api/v1/local-agent/self-media-schedules/${encodeURIComponent(scheduleId)}/publish-checks/published?${query}`
  return signedTrustedBackendRequest(config, path, { method: 'POST' })
}

async function reportPublishCheckUnknown(config, scheduleId, result) {
  const query = new URLSearchParams()
  query.set('diagnosticsJson', shortDiagnosticsJson(result))
  const path = `/api/v1/local-agent/self-media-schedules/${encodeURIComponent(scheduleId)}/publish-checks/unknown?${query}`
  return signedTrustedBackendRequest(config, path, { method: 'POST' })
}

async function reportPublishCheckFailed(config, scheduleId, result) {
  const query = new URLSearchParams()
  query.set('failureCode', result?.failureCode || 'PUBLISH_RESULT_CHECK_HELPER_FAILED')
  query.set('failureMessage', String(result?.failureMessage || 'publish result check failed').slice(0, 480))
  query.set('diagnosticsJson', shortDiagnosticsJson(result))
  const path = `/api/v1/local-agent/self-media-schedules/${encodeURIComponent(scheduleId)}/publish-checks/failed?${query}`
  return signedTrustedBackendRequest(config, path, { method: 'POST' })
}

async function reportScheduleExecutionFailed(config, task, result) {
  const scheduleId = scheduleIdOfTask(task)
  if (!scheduleId) return null
  const query = new URLSearchParams()
  query.set('failureCode', result?.failureCode || task?.lastError?.code || task?.failureCode || 'FILL_FAILED')
  query.set('failureMessage', String(result?.failureMessage || task?.lastError?.message || 'schedule execution failed').slice(0, 480))
  query.set('diagnosticsJson', shortDiagnosticsJson({
    ...result,
    taskId: task?.taskId,
    platform: task?.platform,
    error: task?.lastError || null,
  }))
  const path = `/api/v1/local-agent/self-media-schedules/${encodeURIComponent(scheduleId)}/executions/failed?${query}`
  return signedTrustedBackendRequest(config, path, { method: 'POST' })
}

async function reportScheduleExecutionSuccess(config, task, fillResult) {
  const scheduleId = scheduleIdOfTask(task)
  if (!scheduleId) return null
  const outcome = resolveScheduleExecutionOutcome(fillResult)
  const query = new URLSearchParams()
  const publishedUrl = extractPublishedUrl(fillResult)
  if (outcome === 'published' && publishedUrl) query.set('platformPublishedUrl', publishedUrl)
  query.set('diagnosticsJson', shortDiagnosticsJson({
    fillResult,
    taskId: task?.taskId,
    platform: task?.platform,
    scheduleId,
  }))
  const path = `/api/v1/local-agent/self-media-schedules/${encodeURIComponent(scheduleId)}/executions/${outcome}?${query}`
  return signedTrustedBackendRequest(config, path, { method: 'POST' })
}

function resolveScheduleExecutionOutcome(fillResult) {
  const publishOptions = fillResult?.publishOptions || {}
  const verification = publishOptions.publishVerification || {}
  const verified = verification.verified === true || verification.verified === 'true'
  if (verified && publishOptions.published === true) return 'published'
  if (verified && publishOptions.scheduled === true) return 'scheduled'
  return 'filled'
}

function extractPublishedUrl(fillResult) {
  const publishOptions = fillResult?.publishOptions || {}
  const verification = publishOptions.publishVerification || {}
  return publishOptions.platformPublishedUrl
    || publishOptions.publishedUrl
    || verification.platformPublishedUrl
    || verification.publishedUrl
    || fillResult?.platformPublishedUrl
    || fillResult?.url
    || ''
}

async function reportScheduleHeartbeat(config, scheduleId) {
  if (!scheduleId) return null
  const path = `/api/v1/local-agent/self-media-schedules/${encodeURIComponent(scheduleId)}/heartbeat`
  return signedTrustedBackendRequest(config, path, { method: 'POST' })
}

function scheduleIdOfTask(task) {
  return task?.schedule?.id || task?.backendTask?.platformOptions?.scheduleId || task?.backendTask?.scheduleId
}

function claimedTimeoutMsForTask(task) {
  return scheduleIdOfTask(task) ? CLAIM_BACKEND_HEARTBEAT_MAX_MS : CLAIM_TIMEOUT_MS
}

function expireClaimedTask(task) {
  if (scheduleIdOfTask(task)) {
    task.status = 'failed'
    task.failedAt = nowIso()
    task.failureCode = 'LOCAL_HELPER_CLAIM_TIMEOUT'
    task.lastError = {
      code: 'LOCAL_HELPER_CLAIM_TIMEOUT',
      message: '本地助手等待扩展回写超时，已释放后端排期锁',
    }
  } else {
    task.status = 'requeued'
    task.requeuedAt = nowIso()
    task.lastError = { message: 'claimed task timed out and was requeued' }
  }
  task.claimedAt = null
  task.claimOwner = null
}

function shouldHeartbeatScheduleTask(task) {
  if (!task || isTerminalStatus(task.status)) return false
  if (!scheduleIdOfTask(task)) return false
  if (!task.backendTask && !task.schedule) return false
  if (task.status !== 'claimed' || !task.claimedAt) return false
  return Date.now() - Date.parse(task.claimedAt) <= CLAIM_BACKEND_HEARTBEAT_MAX_MS
}

async function heartbeatActiveScheduleTasks(config) {
  let sent = 0
  let failed = 0
  let changed = false
  const staleClaimKeys = new Set()
  for (const task of tasksById.values()) {
    if (task.status !== 'claimed' || !task.claimedAt) continue
    const claimedMs = Date.now() - Date.parse(task.claimedAt)
    if (claimedMs <= CLAIM_BACKEND_HEARTBEAT_MAX_MS) continue
    if (task.environmentKey) staleClaimKeys.add(`env:${task.environmentKey}`)
    if (task.platform) staleClaimKeys.add(`platform:${task.platform}`)
  }
  for (const key of staleClaimKeys) {
    if (key.startsWith('env:')) await requeueTimedOutClaims(key.slice(4))
    if (key.startsWith('platform:')) await requeueTimedOutClaimsByPlatform(key.slice(9))
  }
  for (const task of tasksById.values()) {
    if (!shouldHeartbeatScheduleTask(task)) continue
    const scheduleId = scheduleIdOfTask(task)
    try {
      const schedule = await reportScheduleHeartbeat(config, scheduleId)
      task.schedule = schedule || task.schedule || null
      task.backendHeartbeatAt = nowIso()
      task.backendHeartbeatLastError = null
      sent += 1
      changed = true
    } catch (error) {
      task.backendHeartbeatLastError = formatBackendError(error)
      failed += 1
      changed = true
      console.error('Failed to heartbeat self-media schedule:', task.backendHeartbeatLastError)
    }
  }
  if (changed) await saveRuntimeTasks()
  return { sent, failed }
}

function shouldFlushScheduleFailure(task, platform) {
  if (!task || task.status !== 'failed') return false
  if (platform && task.platform !== platform) return false
  if (!scheduleIdOfTask(task)) return false
  if (task.backendFailureReportedAt) return false
  if (task.backendFailureReportRejectedAt) return false
  return Number(task.backendFailureReportAttempts || 0) < FAILED_SCHEDULE_REPORT_MAX_ATTEMPTS
}

async function flushPendingScheduleFailureReports(config, platform = '') {
  let changed = false
  for (const task of tasksById.values()) {
    if (!shouldFlushScheduleFailure(task, platform)) continue
    task.backendFailureReportAttempts = Number(task.backendFailureReportAttempts || 0) + 1
    try {
      await reportScheduleExecutionFailed(config, task, {
        failureCode: task.lastError?.code || task.failureCode || 'FILL_FAILED',
        failureMessage: task.lastError?.message || String(task.lastError || '任务填充失败'),
      })
      task.backendFailureReportedAt = nowIso()
      task.backendFailureReportLastError = null
    } catch (error) {
      task.backendFailureReportLastError = formatBackendError(error)
      if (error?.statusCode === 400) {
        task.backendFailureReportRejectedAt = nowIso()
      }
      console.error('Failed to report pending schedule execution failure:', task.backendFailureReportLastError)
    }
    changed = true
  }
  if (changed) await saveRuntimeTasks()
}

function shouldFlushScheduleSuccess(task, platform) {
  if (!task || task.status !== 'completed') return false
  if (platform && task.platform !== platform) return false
  if (!scheduleIdOfTask(task)) return false
  if (task.backendSuccessReportedAt) return false
  if (task.backendSuccessReportRejectedAt) return false
  return Number(task.backendSuccessReportAttempts || 0) < FAILED_SCHEDULE_REPORT_MAX_ATTEMPTS
}

async function flushPendingScheduleSuccessReports(config, platform = '') {
  let changed = false
  for (const task of tasksById.values()) {
    if (!shouldFlushScheduleSuccess(task, platform)) continue
    task.backendSuccessReportAttempts = Number(task.backendSuccessReportAttempts || 0) + 1
    try {
      task.schedule = await reportScheduleExecutionSuccess(config, task, task.fillResult) || task.schedule || null
      task.backendSuccessReportedAt = nowIso()
      task.backendSuccessReportLastError = null
    } catch (error) {
      task.backendSuccessReportLastError = formatBackendError(error)
      if (error?.statusCode === 400) {
        task.backendSuccessReportRejectedAt = nowIso()
      }
      console.error('Failed to report pending schedule execution success:', task.backendSuccessReportLastError)
    }
    changed = true
  }
  if (changed) await saveRuntimeTasks()
}

function formatBackendError(error) {
  const details = error?.details ? `; details=${JSON.stringify(error.details).slice(0, 600)}` : ''
  const status = error?.statusCode ? `; status=${error.statusCode}` : ''
  return `${error?.message || error}${status}${details}`
}

function shortDiagnosticsJson(value) {
  const normalized = {
    ...value,
    textSample: typeof value?.textSample === 'string' ? value.textSample.slice(0, 800) : value?.textSample,
    checkedAt: nowIso(),
  }
  return JSON.stringify(normalized).slice(0, 6000)
}

async function handleSchedulePollOnce(req, res, config) {
  await requireHelperAccess(req, config)
  const body = req.method === 'POST' ? await readJson(req) : {}
  const queryPlatform = new URL(req.url, 'http://localhost').searchParams.get('platform')
  const platform = String(body.platform || queryPlatform || '').trim()
  if (!platform) {
    const result = await pollSelfMediaSchedules(config)
    sendJson(req, res, config, 200, result)
    return
  }
  const publishCheck = await claimAndCheckPublishResult(config, platform)
  if (publishCheck.claimed) {
    sendJson(req, res, config, 200, { ...publishCheck, kind: 'publish_result_check' })
    return
  }
  const result = await claimAndLaunchScheduledTask(config, platform)
  sendJson(req, res, config, 200, result)
}

function defaultPublishUrlForPlatform(platform) {
  const normalized = String(platform || '').trim().toLowerCase()
  if (normalized === 'toutiao') return 'https://mp.toutiao.com/profile_v4/graphic/publish'
  if (normalized === 'douyin') return 'https://creator.douyin.com/creator-micro/content/post/article?media_type=article&type=new&enter_from=publish_page'
  if (normalized === 'zhihu') return 'https://zhuanlan.zhihu.com/write'
  if (normalized === 'xiaohongshu') return 'https://creator.xiaohongshu.com/publish/publish'
  if (normalized === 'baijiahao') return 'https://baijiahao.baidu.com/builder/rc/edit?type=news&is_from_cms=1'
  return null
}

function defaultWorksListUrlForPlatform(platform) {
  const normalized = String(platform || '').trim().toLowerCase()
  if (normalized === 'toutiao') return 'https://mp.toutiao.com/profile_v4/manage/content/all'
  if (normalized === 'douyin') return 'https://creator.douyin.com/creator-micro/content/manage?enter_from=publish'
  if (normalized === 'xiaohongshu') return 'https://creator.xiaohongshu.com/new/note-manager'
  if (normalized === 'baijiahao') return null
  return defaultPublishUrlForPlatform(platform)
}

function worksListUrlForPublishCheck(platform, launchUrl, context = {}) {
  const normalized = String(platform || '').trim().toLowerCase()
  if (normalized === 'baijiahao') {
    const appId = baijiahaoAppIdFromContext(context)
    if (appId) return buildBaijiahaoWorksListUrl(appId)
    if (baijiahaoWorksListHasAppId(launchUrl)) return launchUrl
    return null
  }
  if (normalized === 'douyin' || normalized === 'xiaohongshu' || normalized === 'toutiao') {
    return defaultWorksListUrlForPlatform(normalized)
  }
  return launchUrl || defaultWorksListUrlForPlatform(normalized)
}

function buildBaijiahaoWorksListUrl(appId = '') {
  const url = new URL('https://baijiahao.baidu.com/builder/rc/content')
  url.searchParams.set('currentPage', '1')
  url.searchParams.set('pageSize', '10')
  url.searchParams.set('search', '')
  url.searchParams.set('type', '')
  url.searchParams.set('collection', '')
  if (appId) url.searchParams.set('app_id', String(appId).trim())
  url.searchParams.set('startDate', '')
  url.searchParams.set('endDate', '')
  return url.toString()
}

function baijiahaoWorksListHasAppId(value) {
  try {
    const url = new URL(value)
    const appId = String(url.searchParams.get('app_id') || '').trim()
    return url.hostname.includes('baijiahao.baidu.com') && /^\d{6,}$/.test(appId)
  } catch (_) {
    return false
  }
}

function baijiahaoAppIdFromContext(context = {}) {
  const candidates = [
    context.baijiahaoAppId,
    context.appId,
    context.expectedPlatformAccountId,
    context.platformAccountId,
    context.launch?.expectedPlatformAccountId,
    context.launch?.platformAccountId,
    context.schedule?.expectedPlatformAccountId,
    context.schedule?.platformAccountId,
  ]
  for (const candidate of candidates) {
    const appId = String(candidate || '').trim()
    if (/^\d{6,}$/.test(appId)) return appId
  }
  return ''
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
  const extensionStatus = await inspectGeoEnvExtension(data?.ws?.puppeteer).catch((error) => ({
    installed: false,
    detected: false,
    status: 'unknown',
    reason: error instanceof Error ? error.message : String(error),
  }))
  sendJson(req, res, config, 200, {
    ok: true,
    environmentKey: body.environmentKey,
    environmentName: environment.name || body.environmentKey,
    providerProfileId: environment.providerProfileId,
    openResult,
    extensionStatus,
    adspower: {
      puppeteerWs: data?.ws?.puppeteer || null,
      selenium: data?.ws?.selenium || null,
    },
  })
}

async function handleAdspowerExtensionStatus(req, res, config) {
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
  const extensionStatus = await inspectGeoEnvExtension(data?.ws?.puppeteer)
  sendJson(req, res, config, 200, {
    ok: true,
    environmentKey: environment.environmentKey,
    environmentName: environment.name || environment.environmentKey,
    providerProfileId: environment.providerProfileId,
    extensionStatus,
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
  const environmentKey = String(url.searchParams.get('environmentKey') || '').trim()
  const platform = String(url.searchParams.get('platform') || '').trim()
  if (environmentKey) {
    await requeueTimedOutClaims(environmentKey)
  } else {
    await requeueTimedOutClaimsByPlatform(platform)
  }
  const task = findNextClaimableTask(environmentKey, platform)
  if (!task) {
    const active = listTasks().find((item) => (
      (!environmentKey || item.environmentKey === environmentKey)
      && (!platform || item.platform === platform)
      && !isTerminalStatus(item.status)
    ))
    sendJson(req, res, config, 200, { ok: true, task: null, status: active?.status || null })
    return
  }
  task.status = 'claimed'
  task.claimedAt = nowIso()
  task.claimOwner = {
    environmentKey: task.environmentKey,
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
    if (claimedMs <= claimedTimeoutMsForTask(task)) continue
    expireClaimedTask(task)
    changed = true
  }
  if (changed) await saveRuntimeTasks()
}

async function requeueTimedOutClaimsByPlatform(platform) {
  let changed = false
  for (const task of tasksById.values()) {
    if (platform && task.platform !== platform) continue
    if (task.status !== 'claimed' || !task.claimedAt) continue
    const claimedMs = Date.now() - Date.parse(task.claimedAt)
    if (claimedMs <= claimedTimeoutMsForTask(task)) continue
    expireClaimedTask(task)
    changed = true
  }
  if (changed) await saveRuntimeTasks()
}

function findNextClaimableTask(environmentKey, platform = '') {
  const claimable = listTasks().filter((task) => (
    (!environmentKey || task.environmentKey === environmentKey)
    && (!platform || task.platform === platform)
    && CLAIMABLE_STATUSES.has(task.status)
  ))
  claimable.sort((left, right) => {
    const leftScheduleId = Number(left.schedule?.id || 0)
    const rightScheduleId = Number(right.schedule?.id || 0)
    if (leftScheduleId || rightScheduleId) return rightScheduleId - leftScheduleId
    return 0
  })
  return claimable[0] || null
}

function isTerminalStatus(status) {
  return status === 'completed' || status === 'cancelled' || status === 'failed'
}

function isReusableActiveTask(task) {
  return Boolean(
    task
    && task.adspower?.puppeteerWs
    && (task.status === 'pending' || task.status === 'claimed')
  )
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
    task.backendSuccessReportedAt = null
    task.backendSuccessReportLastError = null
    task.backendSuccessReportAttempts = Number(task.backendSuccessReportAttempts || 0)
    task.claimedAt = null
    task.claimOwner = null
    await reportScheduleExecutionSuccess(config, task, task.fillResult).then((schedule) => {
      task.schedule = schedule || task.schedule || null
      task.backendSuccessReportedAt = nowIso()
      task.backendSuccessReportLastError = null
    }).catch((error) => {
      task.backendSuccessReportAttempts += 1
      task.backendSuccessReportLastError = formatBackendError(error)
      if (error?.statusCode === 400) {
        task.backendSuccessReportRejectedAt = nowIso()
      }
      console.error('Failed to report schedule execution success:', task.backendSuccessReportLastError)
    })
  } else {
    task.status = 'failed'
    task.failedAt = nowIso()
    task.failureCode = classifyFailureStatus(body.error)
    task.lastError = body.error || null
    task.backendFailureReportedAt = null
    task.backendFailureReportLastError = null
    task.backendFailureReportAttempts = Number(task.backendFailureReportAttempts || 0)
    task.claimedAt = null
    task.claimOwner = null
    await reportScheduleExecutionFailed(config, task, {
      failureCode: task.lastError?.code || task.failureCode || 'FILL_FAILED',
      failureMessage: task.lastError?.message || String(task.lastError || '任务填充失败'),
    }).then(() => {
      task.backendFailureReportedAt = nowIso()
      task.backendFailureReportLastError = null
    }).catch((error) => {
      task.backendFailureReportAttempts += 1
      task.backendFailureReportLastError = formatBackendError(error)
      if (error?.statusCode === 400) {
        task.backendFailureReportRejectedAt = nowIso()
      }
      console.error('Failed to report schedule execution failure:', task.backendFailureReportLastError)
    })
  }
  await saveRuntimeTasks()
  sendJson(req, res, config, 200, { ok: true, task })
}

function classifyFailureStatus(error) {
  if (error?.code) return String(error.code)
  const message = String(error?.message || error || '')
  const explicitCode = message.match(/^([A-Z0-9_]{3,80})[：:]/)?.[1]
  if (explicitCode) return explicitCode
  if (message.includes('触发过快') || message.includes('点击速度太快') || message.includes('操作频繁') || message.includes('稍后再试')) {
    return 'BAIJIAHAO_PLATFORM_RATE_LIMITED'
  }
  if (message.includes('Material not found') || message.includes('素材不存在') || message.includes('素材已删除')) {
    return 'PUBLIC_MATERIAL_NOT_FOUND'
  }
  if (message.includes('image content-type is not supported') || message.includes('content-type is not supported') || message.includes('/api/public/brand-materials/')) {
    return 'MATERIAL_IMAGE_UNAVAILABLE'
  }
  if (message.includes('fill token used or expired')) return 'token_expired'
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

async function handleDownloadImageFile(req, res, config) {
  const body = await readJson(req)
  await requireHelperAccess(req, config)
  const result = await downloadImageToTempFile(config, body.url, 0, body.backendBase)
  sendJson(req, res, config, 200, { ok: true, ...result })
}

async function handleUploadImageToPage(req, res, config) {
  const body = await readJson(req)
  await requireHelperAccess(req, config)
  const image = await downloadImageToTempFile(config, body.url, 0, body.backendBase)
  const upload = await uploadImageFileToAdsPowerPage(body, image.filePath)
  sendJson(req, res, config, 200, { ok: true, image, upload })
}

async function uploadImageFileToAdsPowerPage(body, filePath) {
  const task = resolveTaskWithPuppeteerWs(body)
  if (!task?.adspower?.puppeteerWs) {
    throw new Error(`no active AdsPower puppeteer session for environmentKey=${body.environmentKey || '-'}`)
  }
  const platform = String(body.platform || task.platform || 'toutiao').trim().toLowerCase()
  const { default: puppeteer } = await import('puppeteer-core')
  const browser = await puppeteer.connect({ browserWSEndpoint: task.adspower.puppeteerWs, protocolTimeout: 30_000 })
  try {
    const pages = await browser.pages()
    const page = findUploadTargetPage(pages, platform, body.targetPageUrl || body.pageUrl || '')
    if (!page) {
      throw new Error(`AdsPower browser has no active ${platform || 'target'} page`)
    }
    await page.bringToFront().catch(() => {})
    const chooserState = await acceptPlatformFileChooser(page, filePath, platform)
    if (chooserState?.accepted) {
      return chooserState
    }
    const inputs = await page.$$('input[type="file"]')
    const targets = platform === 'zhihu'
      ? await chooseZhihuCoverImageInputs(inputs)
      : platform === 'baijiahao'
        ? await chooseBaijiahaoCoverImageInputs(inputs)
        : platform === 'toutiao'
          ? await chooseToutiaoCoverImageInputs(inputs)
          : await choosePuppeteerImageInputs(inputs)
    if (!targets.length) {
      const diagnostic = chooserState?.tried
        ? `; chooserTried=${JSON.stringify(chooserState.tried).slice(0, 800)}`
        : ''
      throw new Error(`${platform || 'platform'} image file input not found${diagnostic}`)
    }
    const states = []
    for (const target of targets) {
      await target.uploadFile(filePath)
      states.push(await readAndDispatchFileInputState(target))
    }
    return {
      pageUrl: page.url(),
      fileInputCount: inputs.length,
      inputState: states[0] || null,
      inputStates: states,
    }
  } finally {
    await browser.disconnect()
  }
}

function findUploadTargetPage(pages, platform, targetPageUrl = '') {
  const normalized = String(platform || '').trim().toLowerCase()
  const targetUrl = String(targetPageUrl || '').trim()
  if (targetUrl) {
    const matched = pages.find((item) => sameBrowserPageUrl(item.url(), targetUrl))
      || pages.find((item) => sameBrowserPagePath(item.url(), targetUrl))
    if (matched) return matched
  }
  if (normalized === 'zhihu') {
    return pages.find((item) => {
      const pageUrl = item.url()
      return pageUrl.includes('zhihu.com') && (pageUrl.includes('/write') || pageUrl.includes('/edit'))
    }) || pages.find((item) => item.url().includes('zhihu.com'))
  }
  if (normalized === 'toutiao') {
    return pages.find((item) => {
      const pageUrl = item.url()
      return pageUrl.includes('mp.toutiao.com') && pageUrl.includes('/graphic/publish')
    }) || pages.find((item) => item.url().includes('mp.toutiao.com'))
  }
  if (normalized === 'douyin') {
    return pages.find((item) => {
      const pageUrl = item.url()
      return pageUrl.includes('creator.douyin.com')
        && (pageUrl.includes('/creator-micro/content/upload') || pageUrl.includes('/creator-micro/content/post/article'))
    }) || pages.find((item) => item.url().includes('creator.douyin.com'))
  }
  if (normalized === 'baijiahao') {
    return pages.find((item) => {
      const pageUrl = item.url()
      return pageUrl.includes('baijiahao.baidu.com') && pageUrl.includes('/builder/rc/edit')
    }) || pages.find((item) => item.url().includes('baijiahao.baidu.com'))
  }
  return pages.find((item) => item.url().includes(normalized))
}

function sameBrowserPageUrl(left, right) {
  try {
    const leftUrl = new URL(String(left || ''))
    const rightUrl = new URL(String(right || ''))
    leftUrl.hash = ''
    rightUrl.hash = ''
    return leftUrl.href === rightUrl.href
  } catch {
    return String(left || '') === String(right || '')
  }
}

function sameBrowserPagePath(left, right) {
  try {
    const leftUrl = new URL(String(left || ''))
    const rightUrl = new URL(String(right || ''))
    return leftUrl.origin === rightUrl.origin && leftUrl.pathname === rightUrl.pathname
  } catch {
    return false
  }
}

async function acceptPlatformFileChooser(page, filePath, platform) {
  const labels = uploadChooserLabels(platform)
  const normalized = String(platform || '').trim().toLowerCase()
  // Baijiahao reuses the same upload text for article, cover, and video controls.
  // Use the direct image input path below instead of opening a generic file chooser.
  if (normalized === 'baijiahao') {
    return null
  }
  if (normalized === 'douyin') {
    return acceptFileChooserByClickCandidates(page, filePath, labels)
  }

  const chooserPromise = page.waitForFileChooser({ timeout: 3_000 }).catch(() => null)
  const clicked = await clickPlatformUploadChooser(page, labels)
  if (!clicked) return null
  const chooser = await chooserPromise
  if (!chooser) return null
  return acceptChooserAndReadState(page, chooser, filePath)
}

async function acceptFileChooserByClickCandidates(page, filePath, labels) {
  const candidates = await collectUploadClickCandidates(page, labels)
  const tried = []
  for (const candidate of candidates.slice(0, 6)) {
    tried.push({
      score: candidate.score,
      text: candidate.text,
      clickableText: candidate.clickableText,
      x: Math.round(candidate.x),
      y: Math.round(candidate.y),
    })
    const chooserPromise = page.waitForFileChooser({ timeout: 2_500 }).catch(() => null)
    await page.mouse.move(candidate.x, candidate.y).catch(() => {})
    await page.mouse.down().catch(() => {})
    await delay(40)
    await page.mouse.up().catch(() => {})
    const chooser = await chooserPromise
    if (chooser) return acceptChooserAndReadState(page, chooser, filePath, { tried })
  }
  return { accepted: false, noChooser: true, tried }
}

async function acceptChooserAndReadState(page, chooser, filePath, extra = {}) {
  await chooser.accept([filePath])
  await delay(500)
  const states = []
  let inputs = []
  let stateReadError = ''
  try {
    inputs = await page.$$('input[type="file"]')
    for (const input of inputs) {
      states.push(await readAndDispatchFileInputState(input))
    }
  } catch (error) {
    stateReadError = error?.message || String(error)
  }
  let pageUrl = ''
  try {
    pageUrl = page.url()
  } catch {
    pageUrl = ''
  }
  return {
    accepted: true,
    pageUrl,
    fileInputCount: inputs.length,
    inputState: states.find((state) => state.filesLength > 0) || states[0] || null,
    inputStates: states,
    stateReadError: stateReadError || undefined,
    ...extra,
  }
}

async function clickPlatformUploadChooser(page, labels) {
  const candidates = await collectUploadClickCandidates(page, labels)
  const chosen = candidates[0]
  if (!chosen) return false
  await page.mouse.click(chosen.x, chosen.y, { delay: 30 })
  return true
}

async function collectUploadClickCandidates(page, labels) {
  return page.evaluate((inputLabels) => {
    function visible(el) {
      const style = window.getComputedStyle(el)
      const rect = el.getBoundingClientRect()
      return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0
    }
    const labels = Array.isArray(inputLabels) ? inputLabels : []
    return Array.from(document.querySelectorAll('button, [role="button"], div, span, label'))
      .filter(visible)
      .map((el) => {
        const text = String(el.textContent || el.getAttribute('aria-label') || el.getAttribute('title') || '').replace(/\s+/g, '')
        const score = scoreUploadCandidate(el, text, labels)
        if (score <= 0) return null
        const clickable = el.closest('button, label, [role="button"], [class*="mycard"], [class*="content-upload"]') || el
        const rect = clickable.getBoundingClientRect()
        const icon = clickable.querySelector('[class*="addIcon"], [class*="addInnerIcon"]')
        const iconRect = icon?.getBoundingClientRect?.()
        const point = iconRect && iconRect.width > 0 && iconRect.height > 0
          ? { x: iconRect.left + iconRect.width / 2, y: iconRect.top + iconRect.height / 2 }
          : { x: rect.left + Math.min(48, Math.max(12, rect.width * 0.08)), y: rect.top + rect.height / 2 }
        return {
          score,
          text,
          clickableText: String(clickable.textContent || '').replace(/\s+/g, '').slice(0, 120),
          x: point.x,
          y: point.y,
        }
      })
      .filter(Boolean)
      .sort((left, right) => right.score - left.score)

    function scoreUploadCandidate(el, text, labels) {
      if (!labels.some((label) => text === label || text.includes(label))) return 0
      let score = 10
      if (labels.some((label) => text === label)) score += 100
      const context = contextText(el)
      if (context.includes('文章头图')) score += 300
      if (context.includes('封面设置')) score -= 120
      if (context.includes('点击上传图片')) score += 80
      if (context.includes('点击上传封面图')) score -= 60
      if (String(el.className || '').includes('mycard')) score += 20
      return score
    }

    function contextText(el) {
      const parts = []
      let current = el
      for (let depth = 0; current && depth < 6; depth += 1) {
        parts.push(String(current.textContent || '').replace(/\s+/g, ''))
        current = current.parentElement
      }
      return parts.join('')
    }
  }, labels).catch(() => [])
}

function uploadChooserLabels(platform) {
  const normalized = String(platform || '').trim().toLowerCase()
  if (normalized === 'zhihu') {
    return ['添加文章封面', '添加封面', '上传封面', '上传图片', '选择图片']
  }
  if (normalized === 'baijiahao') {
    return ['点击本地上传', '本地上传', '上传图片', '选择图片']
  }
  if (normalized === 'douyin') {
    return ['点击上传图片', '上传图片', '点击上传封面图', '上传封面图', '替换封面']
  }
  return ['本地上传', '上传图片', '选择图片']
}

async function choosePuppeteerImageInputs(inputs) {
  const preferred = []
  const fallback = []
  for (const input of inputs) {
    const meta = await input.evaluate((el) => ({
      accept: el.getAttribute('accept') || '',
      id: el.id || '',
      name: el.name || '',
      className: String(el.className || ''),
      visible: (() => {
        const rect = el.getBoundingClientRect()
        return rect.width > 0 && rect.height > 0
      })(),
      nearbyText: (() => {
        let current = el
        const parts = []
        for (let depth = 0; current && depth < 7; depth += 1) {
          parts.push(current.id || '')
          parts.push(String(current.className || ''))
          parts.push(current.getAttribute?.('data-e2e') || '')
          const text = String(current.textContent || '').replace(/\s+/g, '')
          if (text && text.length <= 180) parts.push(text)
          current = current.parentElement
        }
        return parts.join(' ')
      })(),
    })).catch(() => ({}))
    const text = `${meta.accept} ${meta.id} ${meta.name} ${meta.className} ${meta.nearbyText}`.toLowerCase()
    if (text.includes('image') || text.includes('jpg') || text.includes('png') || /upload|cover|file/.test(text)) {
      const item = { input, score: scorePuppeteerImageInput(text, meta) }
      if (text.includes('drag')) fallback.push(item)
      else preferred.push(item)
    }
  }
  const candidates = preferred.concat(fallback)
  candidates.sort((left, right) => right.score - left.score)
  return candidates.length ? candidates.map((item) => item.input) : inputs.slice().reverse()
}

async function chooseToutiaoCoverImageInputs(inputs) {
  const candidates = []
  for (const input of inputs) {
    const meta = await input.evaluate((el) => {
      const rect = el.getBoundingClientRect()
      const accept = el.getAttribute('accept') || ''
      const id = el.id || ''
      const name = el.name || ''
      const className = String(el.className || '')
      const nearbyText = (() => {
        let current = el
        const parts = []
        for (let depth = 0; current && depth < 8; depth += 1) {
          parts.push(current.id || '')
          parts.push(String(current.className || ''))
          parts.push(current.getAttribute?.('data-e2e') || '')
          const text = String(current.textContent || '').replace(/\s+/g, '')
          if (text && text.length <= 220) parts.push(text)
          current = current.parentElement
        }
        const drawer = el.closest?.('[class*="drawer"], [class*="modal"], [class*="dialog"], [class*="upload"]')
        const drawerText = String(drawer?.textContent || '').replace(/\s+/g, '')
        if (drawerText && drawerText.length <= 260) parts.push(drawerText)
        return parts.join(' ')
      })()
      return { accept, id, name, className, visible: rect.width > 0 && rect.height > 0, nearbyText }
    }).catch(() => ({}))
    const accept = String(meta.accept || '').toLowerCase()
    const descriptor = `${meta.accept || ''} ${meta.id || ''} ${meta.name || ''} ${meta.className || ''} ${meta.nearbyText || ''}`.toLowerCase()
    if (isVideoFileInputDescriptor(accept, descriptor)) continue
    const hasImageAccept = /(image|jpg|jpeg|png|webp|gif|jfif|bmp)/.test(accept)
    const hasToutiaoUploadIdentity = /(upload|file|image|img|cover|封面|图片|本地上传|上传图片|upload-drag-input|btn-upload)/.test(descriptor)
    if (!hasImageAccept && !hasToutiaoUploadIdentity) continue
    const score = scoreToutiaoCoverInput(descriptor, meta)
    if (score > 0) candidates.push({ input, score })
  }
  candidates.sort((left, right) => right.score - left.score)
  return candidates.length ? [candidates[0].input] : []
}

function scorePuppeteerImageInput(descriptor, meta) {
  let score = 0
  if (/(image|jpg|jpeg|png|webp)/.test(descriptor)) score += 20
  if (/btn-upload-handle|upload-handler|本地上传|上传图片|btn-upload|upload-btn/.test(descriptor)) score += 120
  if (/upload-drag-input/.test(descriptor)) score += 90
  if (/扫码上传/.test(descriptor)) score -= 30
  if (/头像|avatar|logo|账号|profile/.test(descriptor)) score -= 80
  if (meta?.visible) score += 5
  return score
}

function scoreToutiaoCoverInput(descriptor, meta) {
  let score = 0
  if (/(image|jpg|jpeg|png|webp|gif|jfif|bmp)/.test(descriptor)) score += 80
  if (/本地上传|上传图片|选择图片|展示封面|单图|封面|btn-upload|upload-handler|upload-drag-input/.test(descriptor)) score += 140
  if (/drawer|modal|dialog|upload/.test(descriptor)) score += 40
  if (meta?.visible) score += 5
  if (/正文|toolbar|editor|contenteditable|插入|链接|表情|ai创作|头条创作助手/.test(descriptor)) score -= 90
  if (/扫码上传|手机上传/.test(descriptor)) score -= 40
  if (/头像|avatar|logo|账号|profile/.test(descriptor)) score -= 120
  if (isVideoFileInputDescriptor('', descriptor)) score -= 300
  return score
}

async function chooseZhihuCoverImageInputs(inputs) {
  const candidates = []
  for (const input of inputs) {
    const meta = await input.evaluate((el) => {
      const rect = el.getBoundingClientRect()
      const accept = el.getAttribute('accept') || ''
      const id = el.id || ''
      const name = el.name || ''
      const className = String(el.className || '')
      const nearbyText = (() => {
        let current = el.parentElement
        for (let depth = 0; current && depth < 5; depth += 1) {
          const text = String(current.textContent || '').replace(/\s+/g, '')
          if (/添加封面|添加文章封面|发布设置/.test(text)) return text.slice(0, 120)
          current = current.parentElement
        }
        return ''
      })()
      return { accept, id, name, className, visible: rect.width > 0 && rect.height > 0, nearbyText }
    }).catch(() => ({}))
    const accept = String(meta.accept || '').toLowerCase()
    const descriptor = `${meta.accept || ''} ${meta.id || ''} ${meta.name || ''} ${meta.className || ''} ${meta.nearbyText || ''}`.toLowerCase()
    if (!/(image|jpg|jpeg|png|webp|avif|heic)/.test(accept + descriptor)) continue
    candidates.push({ input, score: scoreZhihuCoverInput(descriptor, meta) })
  }
  candidates.sort((left, right) => right.score - left.score)
  return candidates.length ? [candidates[0].input] : []
}

async function chooseBaijiahaoCoverImageInputs(inputs) {
  const candidates = []
  for (const input of inputs) {
    const meta = await input.evaluate((el) => {
      const rect = el.getBoundingClientRect()
      const accept = el.getAttribute('accept') || ''
      const id = el.id || ''
      const name = el.name || ''
      const className = String(el.className || '')
      const pickerInfo = (() => {
        let current = el.parentElement
        for (let depth = 0; current && depth < 9; depth += 1) {
          const text = String(current.textContent || '').replace(/\s+/g, '')
          if (/正文\/本地上传|点击本地上传|本地上传|AI封图|免费正版图库|封面预览|确定\(\d+\)/.test(text)) {
            return { inPicker: true, text: text.slice(0, 240) }
          }
          current = current.parentElement
        }
        return { inPicker: false, text: '' }
      })()
      return {
        accept,
        id,
        name,
        className,
        visible: rect.width > 0 && rect.height > 0,
        inPicker: pickerInfo.inPicker,
        nearbyText: pickerInfo.text,
      }
    }).catch(() => ({}))
    const accept = String(meta.accept || '').toLowerCase()
    const identity = `${meta.id || ''} ${meta.name || ''} ${meta.className || ''}`.toLowerCase()
    const descriptor = `${meta.accept || ''} ${identity} ${meta.nearbyText || ''}`.toLowerCase()
    if (isVideoFileInputDescriptor(accept, descriptor)) continue
    const hasImageAccept = /(image|jpg|jpeg|png|webp|avif|heic)/.test(accept)
    const hasCoverPickerContext = Boolean(meta.inPicker) || /正文\/本地上传|点击本地上传|本地上传|ai封图|免费正版图库|封面预览|确定\(\d+\)/.test(descriptor)
    const hasImageIdentity = /(image|img|upload|cover|file|封面|图片)/.test(identity)
    if (!hasImageAccept && !(hasCoverPickerContext && hasImageIdentity)) continue
    candidates.push({ input, score: scoreBaijiahaoCoverInput(descriptor, meta) })
  }
  candidates.sort((left, right) => right.score - left.score)
  return candidates.length ? [candidates[0].input] : []
}

function isVideoFileInputDescriptor(accept, descriptor) {
  return /(video|mp4|mov|avi|mkv|wmv|webm|mpeg|flv|rmvb|vob|ogg|视频)/.test(`${accept || ''} ${descriptor || ''}`)
}

function scoreBaijiahaoCoverInput(descriptor, meta) {
  let score = 0
  const accept = String(meta?.accept || '').toLowerCase()
  if (meta?.inPicker) score += 180
  if (/正文\/本地上传|点击本地上传|本地上传|ai封图|免费正版图库|封面预览|确定\(\d+\)/.test(descriptor)) score += 120
  if (/^image\/\*/.test(accept)) score += 80
  if (/cover|image|upload|file|封面|图片/.test(descriptor)) score += 20
  if (meta?.visible) score += 5
  if (/toolbar|editor|content|article|正文输入|请输入正文|插入/.test(descriptor)) score -= 60
  if (isVideoFileInputDescriptor('', descriptor)) score -= 300
  if (/\.pdf|\.doc|\.ppt|\.xls|mobi|epub|csv|azw3/.test(descriptor)) score -= 80
  return score
}

function scoreZhihuCoverInput(descriptor, meta) {
  let score = 0
  if (/添加封面|添加文章封面|发布设置/.test(descriptor)) score += 100
  if (/cover|avatar|image/.test(descriptor)) score += 20
  if (meta?.visible) score += 5
  if (/\.pdf|\.doc|\.ppt|\.xls|mobi|epub|csv|azw3/.test(descriptor)) score -= 80
  if (/toolbar|editor|content|article|正文/.test(descriptor)) score -= 30
  return score
}

async function readAndDispatchFileInputState(input) {
  return input.evaluate((el) => {
    el.dispatchEvent(new Event('input', { bubbles: true }))
    el.dispatchEvent(new Event('change', { bubbles: true }))
    return {
      filesLength: el.files ? el.files.length : 0,
      fileName: el.files && el.files[0] ? el.files[0].name : '',
      accept: el.getAttribute('accept') || '',
      id: el.id || '',
      name: el.name || '',
      className: String(el.className || ''),
    }
  }).catch((error) => ({
    filesLength: null,
    fileName: '',
    accept: '',
    id: '',
    name: '',
    className: '',
    stateReadError: error.message,
  }))
}

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function resolveTaskWithPuppeteerWs(body) {
  const taskId = Number(body.taskId)
  if (Number.isFinite(taskId) && taskId > 0) {
    const task = tasksById.get(taskId)
    if (task?.adspower?.puppeteerWs) return task
  }
  const environmentKey = String(body.environmentKey || '').trim()
  const platform = String(body.platform || '').trim()
  const candidates = listTasks()
    .filter((task) => !isTerminalStatus(task.status))
    .filter((task) => task.adspower?.puppeteerWs)
    .filter((task) => !environmentKey || task.environmentKey === environmentKey)
    .filter((task) => !platform || task.platform === platform)
  return candidates[candidates.length - 1] || null
}

async function downloadImageToTempFile(config, urlValue, depth = 0, backendBase = '') {
  const url = new URL(String(urlValue || '').trim())
  if (!['http:', 'https:'].includes(url.protocol)) {
    const error = new Error('image url only supports http/https')
    error.statusCode = 400
    throw error
  }
  const response = await fetchWithTimeout(url.href, {}, DEFAULT_FETCH_TIMEOUT_MS)
  if (!response.ok) {
    throw new Error(`image download failed: HTTP ${response.status}`)
  }
  const contentType = response.headers.get('content-type') || 'image/jpeg'
  if (!contentType.startsWith('image/')) {
    const bodyText = await response.text().catch(() => '')
    const nestedUrl = extractImageUrlFromJsonText(bodyText) || rewritePublicMaterialUrlToTrustedBackend(config, url.href, backendBase)
    if (nestedUrl && depth < 3) return downloadImageToTempFile(config, nestedUrl, depth + 1, backendBase)
    throw new Error(`image content-type is not supported: ${contentType}; url=${url.href}; body=${bodyText.slice(0, 240) || '-'}`)
  }
  const buffer = Buffer.from(await response.arrayBuffer())
  if (buffer.byteLength > 20 * 1024 * 1024) {
    throw new Error('image exceeds 20MB')
  }
  await fs.mkdir(TEMP_FILES_DIR, { recursive: true })
  const ext = imageExtension(contentType)
  const fileName = `geo-cover-${Date.now()}-${crypto.randomBytes(6).toString('hex')}.${ext}`
  const filePath = path.join(fileURLToPath(TEMP_FILES_DIR), fileName)
  await fs.writeFile(filePath, buffer)
  return {
    filePath,
    fileName,
    contentType,
    size: buffer.byteLength,
    sourceUrl: url.href,
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

function rewritePublicMaterialUrlToTrustedBackend(config, urlValue, backendBase = '') {
  try {
    const url = new URL(urlValue)
    if (!url.pathname.startsWith('/api/public/brand-materials/')) return ''
    const backendBaseValue = firstText(backendBase, config.trustedBackendBase, config.backendBase)
    if (!backendBaseValue) {
      throw new Error('trusted backend base is not configured for public material url')
    }
    const backend = new URL(backendBaseValue)
    if (backend.origin === url.origin) return ''
    return `${backend.origin}${url.pathname}${url.search}`
  } catch (error) {
    if (error?.message === 'trusted backend base is not configured for public material url') throw error
    return ''
  }
}

function imageExtension(contentType) {
  const type = String(contentType || '').toLowerCase()
  if (type.includes('png')) return 'png'
  if (type.includes('webp')) return 'webp'
  if (type.includes('gif')) return 'gif'
  return 'jpg'
}

function firstText(...values) {
  for (const value of values) {
    if (typeof value === 'string' && value.trim()) return value.trim()
  }
  return ''
}

async function fetchWithTimeout(url, init = {}, timeoutMs = DEFAULT_FETCH_TIMEOUT_MS) {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), Math.max(Number(timeoutMs) || DEFAULT_FETCH_TIMEOUT_MS, 1_000))
  timer.unref?.()
  try {
    return await fetch(url, {
      ...init,
      signal: init.signal || controller.signal,
    })
  } catch (error) {
    if (error?.name === 'AbortError') {
      throw new Error(`request timeout after ${timeoutMs}ms: ${url}`)
    }
    throw error
  } finally {
    clearTimeout(timer)
  }
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
    const packageInfo = await readPackageInfo()
    sendJson(req, res, config, 200, {
      ok: true,
      service: packageInfo.name,
      version: packageInfo.version,
      time: nowIso(),
      paired: Boolean(runtimeSession?.sessionId && runtimeSession?.hmacSecret),
      session: publicSession(),
      adspower: publicAdspowerSettings(config),
      runtime: {
        pid: process.pid,
        ppid: process.ppid,
        node: process.version,
        startedAt: STARTED_AT,
        uptimeSeconds: Math.floor(process.uptime()),
        supervised: process.env.GEO_HELPER_SUPERVISED === '1',
        cwd: process.cwd(),
      },
      schedulePoll: {
        inFlight: schedulePollInFlight,
        last: lastSchedulePollStatus,
        platforms: cachedSelfMediaSchedulePlatforms || [],
        platformSource: 'backend',
        platformFetchError: lastSelfMediaSchedulePlatformsError,
        intervalMs: Number(config.selfMediaSchedulePollIntervalMs || 10_000),
      },
      scheduleHeartbeat: {
        inFlight: scheduleHeartbeatInFlight,
        last: lastScheduleHeartbeatStatus,
        intervalMs: Number(config.selfMediaScheduleHeartbeatIntervalMs || SCHEDULE_HEARTBEAT_INTERVAL_MS),
      },
      config: {
        host: config.host,
        port: config.port,
        backendBase: config.backendBase || null,
        trustedBackendBase: config.trustedBackendBase || null,
        allowedOrigins: config.allowedOrigins || [],
        privateNetworkAccess: true,
      },
    })
    return
  }
  if ((req.method === 'GET' || req.method === 'POST') && url.pathname === '/v1/settings/adspower') {
    return handleAdspowerSettings(req, res, config)
  }
  if (req.method === 'GET' && url.pathname === '/v1/adspower/profiles') return handleAdspowerProfiles(req, res, config, url)
  if (req.method === 'POST' && url.pathname === '/v1/adspower/extension-status') return handleAdspowerExtensionStatus(req, res, config)
  if (req.method === 'POST' && url.pathname === '/v1/extension/bind-intents') return handleCreateExtensionBindIntent(req, res, config)
  if (req.method === 'POST' && url.pathname === '/v1/extension/bind-intents/consume') return handleConsumeExtensionBindIntent(req, res, config)
  if (req.method === 'POST' && url.pathname === '/v1/c2/pairing-code') return handlePairingCode(req, res, config)
  if (req.method === 'POST' && url.pathname === '/v1/c2/pairing-status') return handlePairingStatus(req, res, config)
  if (req.method === 'POST' && url.pathname === '/v1/poc/launch') return handleLaunch(req, res, config)
  if (req.method === 'POST' && url.pathname === '/v1/poc/open-environment') return handleOpenEnvironment(req, res, config)
  if (req.method === 'POST' && url.pathname === '/v1/poc/create-and-launch') return handleCreateAndLaunch(req, res, config)
  if ((req.method === 'GET' || req.method === 'POST') && url.pathname === '/v1/poc/schedule-poll-once') return handleSchedulePollOnce(req, res, config)
  if (req.method === 'POST' && url.pathname === '/v1/poc/accounts') return handleAccounts(req, res, config)
  if (req.method === 'POST' && url.pathname === '/v1/poc/requeue') return handleRequeue(req, res, config)
  if (req.method === 'POST' && url.pathname === '/v1/poc/cancel') return handleCancel(req, res, config)
  if (req.method === 'POST' && url.pathname === '/v1/poc/stop') return handleStop(req, res, config)
  if (req.method === 'GET' && url.pathname === '/v1/poc/tasks') return handleTasks(req, res, config)
  if (req.method === 'GET' && url.pathname === '/v1/extension/tasks') return handleTasks(req, res, config)
  if (req.method === 'GET' && url.pathname === '/v1/extension/tasks/next') return handleNextTask(req, res, config, url)
  if (req.method === 'POST' && url.pathname === '/v1/extension/files/download-image') return handleDownloadImageFile(req, res, config)
  if (req.method === 'POST' && url.pathname === '/v1/extension/files/upload-image-to-page') return handleUploadImageToPage(req, res, config)

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

server.once('error', (error) => {
  if (error?.code === 'EADDRINUSE') {
    console.error(`GEO local helper port already in use: http://${config.host}:${config.port}`)
    if (process.env.GEO_HELPER_SUPERVISED === '1') process.exit(EXIT_CODE_PORT_IN_USE)
    process.exitCode = EXIT_CODE_PORT_IN_USE
    return
  }
  throw error
})

server.listen(config.port, config.host, () => {
  console.log(`GEO local helper listening on http://${config.host}:${config.port}`)
})

function startSchedulePoller(config) {
  const intervalMs = Number(config.selfMediaSchedulePollIntervalMs || 10_000)
  if (!Number.isFinite(intervalMs) || intervalMs <= 0) return
  const tick = () => {
    if (!runtimeSession?.sessionId || !runtimeSession?.hmacSecret) return
    if (schedulePollInFlight) {
      lastSchedulePollStatus = {
        at: nowIso(),
        ok: false,
        skipped: true,
        reason: 'previous poll still running',
      }
      return
    }
    schedulePollInFlight = true
    pollSelfMediaSchedules(config)
      .then((result) => {
        lastSchedulePollStatus = {
          at: nowIso(),
          ok: true,
          claimed: Boolean(result?.claimed),
          platform: result?.platform || null,
          kind: result?.kind || null,
          outcome: result?.outcome || null,
        }
      })
      .catch((error) => {
        lastSchedulePollStatus = {
          at: nowIso(),
          ok: false,
          error: error.message,
        }
        console.error('GEO self-media schedule poll failed:', error.message)
      })
      .finally(() => {
        schedulePollInFlight = false
      })
  }
  setTimeout(tick, 1_000).unref?.()
  setInterval(tick, Math.max(intervalMs, 10_000)).unref?.()
}

function startScheduleHeartbeat(config) {
  const intervalMs = Number(config.selfMediaScheduleHeartbeatIntervalMs || SCHEDULE_HEARTBEAT_INTERVAL_MS)
  if (!Number.isFinite(intervalMs) || intervalMs <= 0) return
  const tick = () => {
    if (!runtimeSession?.sessionId || !runtimeSession?.hmacSecret) return
    if (scheduleHeartbeatInFlight) {
      lastScheduleHeartbeatStatus = {
        at: nowIso(),
        ok: false,
        skipped: true,
        reason: 'previous heartbeat still running',
      }
      return
    }
    scheduleHeartbeatInFlight = true
    heartbeatActiveScheduleTasks(config)
      .then((result) => {
        lastScheduleHeartbeatStatus = {
          at: nowIso(),
          ok: result.failed === 0,
          sent: result.sent,
          failed: result.failed,
        }
      })
      .catch((error) => {
        lastScheduleHeartbeatStatus = {
          at: nowIso(),
          ok: false,
          error: error.message,
        }
        console.error('GEO self-media schedule heartbeat failed:', error.message)
      })
      .finally(() => {
        scheduleHeartbeatInFlight = false
      })
  }
  setTimeout(tick, 5_000).unref?.()
  setInterval(tick, Math.max(intervalMs, 15_000)).unref?.()
}

async function pollSelfMediaSchedules(config) {
  const timeoutMs = Number(config.selfMediaSchedulePollStepTimeoutMs || SCHEDULE_POLL_STEP_TIMEOUT_MS)
  const platforms = await selfMediaSchedulePlatforms(config)
  let lastNoClaimReason = ''
  for (const platform of platforms) {
    const publishCheck = await withTimeout(
      claimAndCheckPublishResult(config, platform),
      timeoutMs,
      `self-media publish check poll ${platform}`,
    )
    if (publishCheck.claimed) return { ...publishCheck, platform, kind: 'publish_result_check' }
    lastNoClaimReason = publishCheck.claimBlockedReason || lastNoClaimReason
    const launch = await withTimeout(
      claimAndLaunchScheduledTask(config, platform),
      timeoutMs,
      `self-media schedule execution poll ${platform}`,
    )
    if (launch.claimed) return { ...launch, platform, kind: 'schedule_execution' }
    lastNoClaimReason = launch.claimBlockedReason || lastNoClaimReason
  }
  return { ok: true, claimed: false, claimBlockedReason: lastNoClaimReason || 'NO_DUE_TASK' }
}

async function selfMediaSchedulePlatforms(config) {
  const now = Date.now()
  if (cachedSelfMediaSchedulePlatforms
    && now - cachedSelfMediaSchedulePlatformsAt < SELF_MEDIA_SCHEDULE_PLATFORMS_CACHE_MS) {
    return cachedSelfMediaSchedulePlatforms
  }
  try {
    const result = await signedTrustedBackendRequest(config, '/api/v1/local-agent/self-media-schedules/platforms', { method: 'GET' })
    const platforms = normalizePlatformList(result?.platforms)
    if (!platforms.length) {
      const error = new Error('backend returned no self-media schedule platforms')
      error.details = result
      throw error
    }
    cachedSelfMediaSchedulePlatforms = platforms
    cachedSelfMediaSchedulePlatformsAt = now
    lastSelfMediaSchedulePlatformsError = null
    return platforms
  } catch (error) {
    lastSelfMediaSchedulePlatformsError = formatBackendError(error)
    throw error
  }
}

function normalizePlatformList(raw) {
  const values = Array.isArray(raw) ? raw : []
  const platforms = values
    .map((item) => String(item || '').trim().toLowerCase())
    .filter(Boolean)
  return Array.from(new Set(platforms))
}

startSchedulePoller(config)
startScheduleHeartbeat(config)
startEventLoopWatchdog(config)

function startEventLoopWatchdog(config) {
  if (config.enableEventLoopWatchdog === false) return
  const thresholdMs = Number(config.eventLoopWatchdogThresholdMs || EVENT_LOOP_WATCHDOG_THRESHOLD_MS)
  if (!Number.isFinite(thresholdMs) || thresholdMs <= 0) return
  const supervised = process.env.GEO_HELPER_SUPERVISED === '1'
  const killOnStall = config.eventLoopWatchdogKillOnStall === true
    || (supervised && config.eventLoopWatchdogKillOnStall !== false)

  const worker = new Worker(`
    const { parentPort } = require('node:worker_threads')
    let lastBeat = Date.now()
    let warned = false
    parentPort.on('message', (message) => {
      if (message && message.type === 'beat') {
        lastBeat = Date.now()
        warned = false
      }
    })
    setInterval(() => {
      const thresholdMs = ${JSON.stringify(thresholdMs)}
      const killOnStall = ${JSON.stringify(killOnStall)}
      if (Date.now() - lastBeat > thresholdMs) {
        if (!warned) {
          warned = true
          console.error('GEO local helper event loop watchdog detected stalled process')
        }
        if (killOnStall) {
          console.error('GEO local helper event loop watchdog terminating stalled process')
          process.kill(process.pid, 'SIGKILL')
        }
      }
    }, ${JSON.stringify(EVENT_LOOP_WATCHDOG_INTERVAL_MS)}).unref()
  `, { eval: true })

  const beat = () => {
    worker.postMessage({ type: 'beat' })
  }
  beat()
  setInterval(beat, EVENT_LOOP_WATCHDOG_INTERVAL_MS).unref?.()
}

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
