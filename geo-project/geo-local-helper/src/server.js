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
const CLAIM_TIMEOUT_MS = 30_000
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
    const page = await browser.newPage()
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
    await page.goto(effectiveTargetUrl, { waitUntil: 'domcontentloaded', timeout: 45_000 })
    await delay(3_000)
    const deadline = Date.now() + 20_000
    let latest = null
    while (Date.now() < deadline) {
      latest = await evaluatePublishResult(page, schedule)
      if (latest.found) return latest
      await delay(2_000)
    }
    return latest || {
      found: false,
      reason: 'works list not evaluated',
      targetTitle: schedule?.publishCheckTitle || '',
      url: page.url(),
    }
  } finally {
    await browser.disconnect()
  }
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
    const text = document.body?.innerText || ''
    const normalizedText = normalize(text)
    const normalizedTitle = normalize(input.title)
    const titleProbe = normalizedTitle.length > 24 ? normalizedTitle.slice(0, 24) : normalizedTitle
    const locationProbe = normalize(input.locationName)
    const hasTitle = Boolean(titleProbe && normalizedText.includes(titleProbe))
    const hasLocation = !locationProbe || normalizedText.includes(locationProbe)
    const hasPublishedSignal = /定时发布中|已发布|审核中|将于\d{1,2}[-月]\d{1,2}/.test(text)
    let matchedUrl = ''
    if (hasTitle) {
      const anchors = Array.from(document.querySelectorAll('a[href]'))
      const anchor = anchors.find((item) => normalize(item.textContent).includes(titleProbe))
      matchedUrl = anchor?.href || ''
    }
    return {
      found: hasTitle && hasLocation && hasPublishedSignal,
      hasTitle,
      hasLocation,
      hasPublishedSignal,
      targetTitle: input.title,
      locationName: input.locationName,
      platformScheduledAt: input.platformScheduledAt,
      url: matchedUrl || location.href,
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
    const text = document.body?.innerText || ''
    const normalizedText = normalize(text)
    const normalizedTitle = normalize(input.title)
    const titleProbe = normalizedTitle.length > 24 ? normalizedTitle.slice(0, 24) : normalizedTitle
    const hasTitle = Boolean(titleProbe && normalizedText.includes(titleProbe))
    const hasPublishedSignal = /发布成功|已发布|审核中|发布于\d{4}[-年]\d{1,2}[-月]\d{1,2}/.test(text)
      || /\/p\/|\/article\//.test(location.href)
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
      const anchor = anchors.find((item) => normalize(item.textContent).includes(titleProbe))
      matchedUrl = anchor?.href || ''
    }
    return {
      found: hasTitle && hasPublishedSignal,
      hasTitle,
      hasPublishedSignal,
      targetTitle: input.title,
      platformScheduledAt: input.platformScheduledAt,
      url: normalizeZhihuUrl(matchedUrl || location.href),
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
  return evaluateXiaohongshuPublishSignals(target, pageState)
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

async function reportPublishCheckPublished(config, scheduleId, result) {
  const query = new URLSearchParams()
  if (result?.url) query.set('platformPublishedUrl', result.url)
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

function shouldHeartbeatScheduleTask(task) {
  if (!task || isTerminalStatus(task.status)) return false
  if (!scheduleIdOfTask(task)) return false
  return Boolean(task.backendTask || task.schedule)
}

async function heartbeatActiveScheduleTasks(config) {
  let sent = 0
  let failed = 0
  let changed = false
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
  return JSON.stringify(normalized).slice(0, 1800)
}

async function handleSchedulePollOnce(req, res, config) {
  await requireHelperAccess(req, config)
  const body = req.method === 'POST' ? await readJson(req) : {}
  const platform = String(body.platform || 'toutiao').trim() || 'toutiao'
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
  if (normalized === 'zhihu') return 'https://zhuanlan.zhihu.com/write'
  if (normalized === 'xiaohongshu') return 'https://creator.xiaohongshu.com/publish/publish'
  if (normalized === 'baijiahao') return 'https://baijiahao.baidu.com/builder/rc/edit?type=news&is_from_cms=1'
  return null
}

function defaultWorksListUrlForPlatform(platform) {
  const normalized = String(platform || '').trim().toLowerCase()
  if (normalized === 'toutiao') return 'https://mp.toutiao.com/profile_v4/manage/content/all'
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
  if (normalized === 'xiaohongshu' || normalized === 'toutiao') {
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

async function requeueTimedOutClaimsByPlatform(platform) {
  let changed = false
  for (const task of tasksById.values()) {
    if (platform && task.platform !== platform) continue
    if (task.status !== 'claimed' || !task.claimedAt) continue
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
    && (CLAIMABLE_STATUSES.has(task.status) || task.status === 'claimed')
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
    const page = findUploadTargetPage(pages, platform)
    if (!page) {
      throw new Error(`AdsPower browser has no active ${platform || 'target'} page`)
    }
    await page.bringToFront().catch(() => {})
    await page.waitForSelector('input[type="file"]', { timeout: 3_000 }).catch(() => null)
    const chooserState = await acceptPlatformFileChooser(page, filePath, platform)
    if (chooserState?.accepted) {
      return chooserState
    }
    const inputs = await page.$$('input[type="file"]')
    const targets = platform === 'zhihu'
      ? await chooseZhihuCoverImageInputs(inputs)
      : platform === 'baijiahao'
        ? await chooseBaijiahaoCoverImageInputs(inputs)
        : await choosePuppeteerImageInputs(inputs)
    if (!targets.length) {
      throw new Error(`${platform || 'platform'} image file input not found`)
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

function findUploadTargetPage(pages, platform) {
  const normalized = String(platform || '').trim().toLowerCase()
  if (normalized === 'zhihu') {
    return pages.find((item) => {
      const pageUrl = item.url()
      return pageUrl.includes('zhihu.com') && pageUrl.includes('/write')
    }) || pages.find((item) => item.url().includes('zhihu.com'))
  }
  if (normalized === 'toutiao') {
    return pages.find((item) => {
      const pageUrl = item.url()
      return pageUrl.includes('mp.toutiao.com') && pageUrl.includes('/graphic/publish')
    }) || pages.find((item) => item.url().includes('mp.toutiao.com'))
  }
  if (normalized === 'baijiahao') {
    return pages.find((item) => {
      const pageUrl = item.url()
      return pageUrl.includes('baijiahao.baidu.com') && pageUrl.includes('/builder/rc/edit')
    }) || pages.find((item) => item.url().includes('baijiahao.baidu.com'))
  }
  return pages.find((item) => item.url().includes(normalized))
}

async function acceptPlatformFileChooser(page, filePath, platform) {
  const labels = uploadChooserLabels(platform)
  const chooserPromise = page.waitForFileChooser({ timeout: 3_000 }).catch(() => null)
  const clicked = await page.evaluate((inputLabels) => {
    function visible(el) {
      const style = window.getComputedStyle(el)
      const rect = el.getBoundingClientRect()
      return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0
    }
    const labels = Array.isArray(inputLabels) ? inputLabels : []
    const candidates = Array.from(document.querySelectorAll('button, [role="button"], div, span, label'))
      .filter(visible)
      .filter((el) => {
        const text = String(el.textContent || el.getAttribute('aria-label') || el.getAttribute('title') || '').replace(/\s+/g, '')
        return labels.some((label) => text === label || text.includes(label))
      })
    const target = candidates[0]
    if (!target) return false
    const clickable = target.closest('button, label, [role="button"]') || target
    clickable.click()
    return true
  }, labels).catch(() => false)
  if (!clicked) return null

  const chooser = await chooserPromise
  if (!chooser) return null
  await chooser.accept([filePath])
  await delay(500)
  const inputs = await page.$$('input[type="file"]')
  const states = []
  for (const input of inputs) {
    states.push(await readAndDispatchFileInputState(input))
  }
  return {
    accepted: true,
    pageUrl: page.url(),
    fileInputCount: inputs.length,
    inputState: states.find((state) => state.filesLength > 0) || states[0] || null,
    inputStates: states,
  }
}

function uploadChooserLabels(platform) {
  const normalized = String(platform || '').trim().toLowerCase()
  if (normalized === 'zhihu') {
    return ['添加文章封面', '添加封面', '上传封面', '上传图片', '选择图片']
  }
  if (normalized === 'baijiahao') {
    return ['点击本地上传', '本地上传', '上传图片', '选择图片']
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
    })).catch(() => ({}))
    const text = `${meta.accept} ${meta.id} ${meta.name} ${meta.className}`.toLowerCase()
    if (text.includes('image') || text.includes('jpg') || text.includes('png') || /upload|cover|file/.test(text)) {
      if (text.includes('drag')) fallback.push(input)
      else preferred.push(input)
    }
  }
  return preferred.concat(fallback).length ? preferred.concat(fallback) : inputs.slice().reverse()
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
      const nearbyText = (() => {
        let current = el.parentElement
        for (let depth = 0; current && depth < 7; depth += 1) {
          const text = String(current.textContent || '').replace(/\s+/g, '')
          if (/正文\/本地上传|本地上传|AI封图|免费正版图库|封面预览|确定\(\d+\)/.test(text)) {
            return text.slice(0, 180)
          }
          current = current.parentElement
        }
        return ''
      })()
      return { accept, id, name, className, visible: rect.width > 0 && rect.height > 0, nearbyText }
    }).catch(() => ({}))
    const accept = String(meta.accept || '').toLowerCase()
    const descriptor = `${meta.accept || ''} ${meta.id || ''} ${meta.name || ''} ${meta.className || ''} ${meta.nearbyText || ''}`.toLowerCase()
    if (!/(image|jpg|jpeg|png|webp|avif|heic)/.test(accept + descriptor)) continue
    candidates.push({ input, score: scoreBaijiahaoCoverInput(descriptor, meta) })
  }
  candidates.sort((left, right) => right.score - left.score)
  return candidates.length ? [candidates[0].input] : []
}

function scoreBaijiahaoCoverInput(descriptor, meta) {
  let score = 0
  if (/正文\/本地上传|本地上传|ai封图|免费正版图库|封面预览|确定\(\d+\)/.test(descriptor)) score += 120
  if (/cover|image|upload|file|封面|图片/.test(descriptor)) score += 20
  if (meta?.visible) score += 5
  if (/toolbar|editor|content|article|正文输入|请输入正文|插入/.test(descriptor)) score -= 60
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
  }).catch((error) => ({ error: error.message }))
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
        intervalMs: Number(config.selfMediaSchedulePollIntervalMs || 30_000),
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
      },
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
  const intervalMs = Number(config.selfMediaSchedulePollIntervalMs || 30_000)
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
