import http from 'node:http'
import { Worker } from 'node:worker_threads'
import { execFile } from 'node:child_process'
import { promisify } from 'node:util'
import { existsSync } from 'node:fs'
import fs from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath, URL } from 'node:url'
import crypto from 'node:crypto'
import { stringifyBoundedDiagnostics } from './diagnostics-json.js'
import { describeUploadPageCandidates, puppeteerPageTargetId, selectUploadTargetPage } from './browser-target.js'
import {
  evaluateBaijiahaoPublishSignals,
  evaluateDouyinPublishSignals,
  evaluateToutiaoPublishSignals,
  evaluateXiaohongshuPublishSignals,
} from './publish-check.js'
import {
  preferScheduleClaimBlock,
  schedulePollBlockLogDecision,
} from './schedule-poll-observability.js'
import {
  BrowserResourceRegistry,
  browserWsBrowserId,
  observedBrowserSessionEpoch,
} from './browser-resource-registry.js'
import { ExclusiveOperationTracker } from './exclusive-operation-tracker.js'
import {
  BrowserRuntimeErrorCounter,
  HelperTaskThroughputCounter,
  buildFailedBrowserObservationMetrics,
  summarizeBrowserProcessMetrics,
} from './browser-observation-metrics.js'

const CONFIG_PATH = new URL('../config.local.json', import.meta.url)
const EXAMPLE_CONFIG_PATH = new URL('../config.example.json', import.meta.url)
const PACKAGE_JSON_PATH = new URL('../package.json', import.meta.url)
const PUBLIC_DIR = new URL('../public/', import.meta.url)
const RUNTIME_DIR = new URL('../runtime/', import.meta.url)
const TASKS_PATH = new URL('tasks.json', RUNTIME_DIR)
const SESSION_PATH = new URL('session.json', RUNTIME_DIR)
const SESSIONS_DIR = new URL('sessions/', RUNTIME_DIR)
const NONCES_PATH = new URL('nonces.json', RUNTIME_DIR)
const SETTINGS_PATH = new URL('settings.json', RUNTIME_DIR)
const MACHINE_ID_PATH = new URL('machine-id', RUNTIME_DIR)
const TEMP_FILES_DIR = new URL('temp-files/', RUNTIME_DIR)
const BROWSER_RESOURCES_PATH = new URL('browser-resources.json', RUNTIME_DIR)
const BROWSER_RESOURCE_AUDIT_PATH = new URL('browser-resource-audit.jsonl', RUNTIME_DIR)
const tasksById = new Map()
const extensionBindIntentsByHash = new Map()
const CLAIM_TIMEOUT_MS = 30_000
const EXTENSION_CLAIM_TIMEOUT_MS = 90_000
const SCHEDULE_PROGRESS_STALL_TIMEOUT_MS = 15 * 60_000
const CLAIMABLE_STATUSES = new Set(['pending', 'requeued'])
const SIGNATURE_MAX_SKEW_SECONDS = 300
const NONCE_FLUSH_DELAY_MS = 1_000
const DEFAULT_FETCH_TIMEOUT_MS = 15_000
const ADSPOWER_FETCH_TIMEOUT_MS = 20_000
const BACKEND_FETCH_TIMEOUT_MS = 20_000
const RESPONSE_JSON_TIMEOUT_MS = 10_000
const SCHEDULE_POLL_STEP_TIMEOUT_MS = 180_000
const BAIJIAHAO_PUBLISH_CHECK_STEP_TIMEOUT_MS = 120_000
const DOUYIN_IMAGE_UPLOAD_COMPLETE_TIMEOUT_MS = 180_000
const SCHEDULE_HEARTBEAT_INTERVAL_MS = 20_000
const PUBLISH_CHECK_TASK_ID_OFFSET = 900_000_000_000
const PUPPETEER_DISCONNECT_TIMEOUT_MS = 2_000
const PUPPETEER_PROTOCOL_TIMEOUT_MS = 120_000
const PUPPETEER_PAGE_GOTO_TIMEOUT_MS = 75_000
const EVENT_LOOP_WATCHDOG_INTERVAL_MS = 5_000
const EVENT_LOOP_WATCHDOG_THRESHOLD_MS = 90_000
const LOCAL_AGENT_RUNTIME_STATUS_HEARTBEAT_MS = 60_000
const FAILED_SCHEDULE_REPORT_MAX_ATTEMPTS = 3
const TERMINAL_SCHEDULE_CLAIM_ERROR_CODES = new Set([
  'SCHEDULE_EXECUTOR_MISMATCH',
  'SCHEDULE_ENVIRONMENT_MISMATCH',
  'SCHEDULE_STATUS_NOT_RUNNING',
  'SCHEDULE_CLAIM_GENERATION_MISMATCH',
  'SCHEDULE_LOCK_RENEW_FAILED',
  'SCHEDULE_ENVIRONMENT_LOCK_LOST',
])
const RUNTIME_TASK_MAX_RECORDS = 200
const RUNTIME_TASK_TERMINAL_TTL_MS = 7 * 24 * 60 * 60 * 1000
const SELF_MEDIA_SCHEDULE_PLATFORMS_CACHE_MS = 60_000
const SCHEDULE_POLL_BLOCK_LOG_INTERVAL_MS = 5 * 60 * 1000
const EXTENSION_BIND_INTENT_TTL_MS = 2 * 60 * 1000
const ADSPOWER_BROWSER_SESSION_CACHE_MS = 2 * 60 * 1000
const ADSPOWER_RATE_LIMIT_RETRY_DELAYS_MS = [800, 1600, 2400]
const BROWSER_OBSERVATION_INTERVAL_MS = 60_000
const GEO_ENV_EXTENSION_NAME = 'GEO 自媒体助手'
const DEFAULT_ALLOWED_WEB_ORIGINS = [
  'https://www.huanjingaigeo.com',
  'http://119.45.154.127',
]
const DEFAULT_PROFILE_KEY = 'prod'
const DEFAULT_PROFILE_LABELS = {
  dev: '本地开发',
  prod: '生产环境',
}
const EXIT_CODE_PORT_IN_USE = 2
const STARTED_AT = new Date().toISOString()
const HELPER_BOOT_ID = crypto.randomUUID()
const execFileAsync = promisify(execFile)
const nonceCache = new Map()
const adspowerBrowserSessions = new Map()
const adspowerBrowserStartInFlight = new Map()
const observedBrowserEnvironments = new Map()
const browserObservationInFlight = new ExclusiveOperationTracker()
const browserProcessCpuSamples = new Map()
const browserRuntimeErrorCounter = new BrowserRuntimeErrorCounter()
const helperTaskThroughputCounter = new HelperTaskThroughputCounter()
const throughputTerminalTasks = new WeakSet()
const browserResourceRegistry = new BrowserResourceRegistry({
  registryPath: BROWSER_RESOURCES_PATH,
  auditPath: BROWSER_RESOURCE_AUDIT_PATH,
  runtimeDir: RUNTIME_DIR,
  helperBootId: HELPER_BOOT_ID,
})
let nonceFlushTimer = null
let runtimeSession = null
let runtimeSettings = { activeProfile: '', adspower: {} }
let packageInfoCache = null
let pendingPairing = null
let schedulePollInFlight = false
let scheduleHeartbeatInFlight = false
let lastSchedulePollStatus = null
let lastSchedulePollBlockLog = { reason: null, at: 0 }
let lastScheduleHeartbeatStatus = null
let cachedSelfMediaSchedulePlatforms = null
let cachedSelfMediaSchedulePlatformsAt = 0
let lastSelfMediaSchedulePlatformsError = null
let schedulePlatformCursor = 0
let machineIdCache = null
let localAgentRuntimeStatusInFlight = false
let lastLocalAgentRuntimeStatus = null
let lastAdspowerApiStatus = { ok: false, checkedAt: null, error: null }
let lastBrowserObservationStatus = null

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

function parseJsonText(raw) {
  return JSON.parse(String(raw || '').replace(/^\uFEFF/, ''))
}

async function readPackageInfo() {
  if (packageInfoCache) return packageInfoCache
  try {
    const raw = await fs.readFile(PACKAGE_JSON_PATH, 'utf8')
    const pkg = parseJsonText(raw)
    packageInfoCache = {
      name: String(pkg.name || 'geo-local-helper'),
      version: pkg.version ? String(pkg.version) : null,
      buildRevision: pkg.buildRevision ? String(pkg.buildRevision) : null,
    }
  } catch {
    packageInfoCache = {
      name: 'geo-local-helper',
      version: null,
      buildRevision: null,
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

async function loadConfig(activeProfileOverride = '') {
  const path = existsSync(CONFIG_PATH) ? CONFIG_PATH : EXAMPLE_CONFIG_PATH
  const raw = await fs.readFile(path, 'utf8')
  const rawConfig = parseJsonText(raw)
  const config = normalizeProfiledConfig(rawConfig, activeProfileOverride)
  applyActiveProfile(config, config.activeProfile)
  return config
}

function normalizeProfiledConfig(rawConfig = {}, activeProfileOverride = '') {
  const baseConfig = { ...(rawConfig || {}) }
  const profiles = normalizeProfiles(rawConfig)
  delete baseConfig.profiles
  delete baseConfig.activeProfile
  const requestedProfile = normalizeProfileKey(activeProfileOverride || rawConfig.activeProfile || DEFAULT_PROFILE_KEY)
  const activeProfile = profiles[requestedProfile] ? requestedProfile : Object.keys(profiles)[0] || DEFAULT_PROFILE_KEY
  return {
    ...baseConfig,
    _baseConfig: baseConfig,
    profiles,
    activeProfile,
  }
}

function normalizeProfiles(rawConfig = {}) {
  if (rawConfig.profiles && typeof rawConfig.profiles === 'object') {
    const profiles = {}
    for (const [key, profile] of Object.entries(rawConfig.profiles)) {
      const normalizedKey = normalizeProfileKey(key)
      if (!normalizedKey || !profile || typeof profile !== 'object') continue
      profiles[normalizedKey] = normalizeProfile(normalizedKey, profile)
    }
    if (Object.keys(profiles).length) return profiles
  }
  const profileKey = legacyProfileKey(rawConfig)
  return {
    [profileKey]: normalizeProfile(profileKey, rawConfig),
  }
}

function legacyProfileKey(rawConfig = {}) {
  const backend = String(rawConfig.trustedBackendBase || rawConfig.backendBase || '').trim()
  if (/^https:\/\/www\.huanjingaigeo\.com\/?$/i.test(backend)) return DEFAULT_PROFILE_KEY
  if (/^https?:\/\/(127\.0\.0\.1|localhost|192\.168\.|10\.|172\.(1[6-9]|2\d|3[0-1])\.)/i.test(backend)) return 'dev'
  return DEFAULT_PROFILE_KEY
}

function normalizeProfileKey(value) {
  const text = String(value || '').trim().toLowerCase()
  return text.replace(/[^a-z0-9_-]/g, '') || DEFAULT_PROFILE_KEY
}

function normalizeProfile(key, profile = {}) {
  return {
    label: String(profile.label || DEFAULT_PROFILE_LABELS[key] || key),
    backendBase: String(profile.backendBase || '').trim(),
    trustedBackendBase: String(profile.trustedBackendBase || profile.backendBase || '').trim(),
    allowedOrigins: Array.isArray(profile.allowedOrigins) ? profile.allowedOrigins.filter(Boolean) : [],
    helperName: String(profile.helperName || '').trim(),
  }
}

function applyActiveProfile(config, profileKey) {
  const key = normalizeProfileKey(profileKey)
  const fallbackKey = config.profiles[DEFAULT_PROFILE_KEY] ? DEFAULT_PROFILE_KEY : Object.keys(config.profiles)[0] || DEFAULT_PROFILE_KEY
  const activeKey = config.profiles[key] ? key : fallbackKey
  const profile = config.profiles[activeKey] || {}
  const base = config._baseConfig || {}

  Object.assign(config, base)
  config._baseConfig = base
  config.profiles ||= {}
  config.activeProfile = activeKey
  config.activeProfileLabel = profile.label || DEFAULT_PROFILE_LABELS[config.activeProfile] || config.activeProfile
  config.adspower ||= {}
  config.adspower.apiBase ||= 'http://localhost:50325'
  config.host ||= '127.0.0.1'
  config.port ||= 17891
  config.backendBase = profile.backendBase || base.backendBase || ''
  config.trustedBackendBase = profile.trustedBackendBase || profile.backendBase || base.trustedBackendBase || base.backendBase || ''
  config.helperName = profile.helperName || base.helperName || ''
  config.allowedOrigins = [
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
  for (const origin of Array.isArray(base.allowedOrigins) ? base.allowedOrigins : []) {
    appendAllowedOrigin(config, origin)
  }
  for (const origin of Array.isArray(profile.allowedOrigins) ? profile.allowedOrigins : []) {
    appendAllowedOrigin(config, origin)
  }
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

function publicProfiles(config) {
  return Object.entries(config.profiles || {}).map(([key, profile]) => ({
    key,
    label: profile.label || DEFAULT_PROFILE_LABELS[key] || key,
    trustedBackendBase: profile.trustedBackendBase || profile.backendBase || '',
    active: key === config.activeProfile,
  }))
}

function appendAllowedOrigin(config, origin) {
  if (!origin) return
  config.allowedOrigins ||= []
  if (!config.allowedOrigins.includes(origin)) config.allowedOrigins.push(origin)
}

async function loadRuntimeSettings() {
  try {
    const raw = await fs.readFile(SETTINGS_PATH, 'utf8')
    const settings = parseJsonText(raw)
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
  const activeProfile = String(settings?.activeProfile || '').trim()
  return {
    activeProfile: activeProfile ? normalizeProfileKey(activeProfile) : '',
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

function puppeteerProtocolTimeoutMs(config = {}) {
  const value = Number(config.puppeteerProtocolTimeoutMs)
  return Number.isFinite(value) && value >= 30_000 ? value : PUPPETEER_PROTOCOL_TIMEOUT_MS
}

function puppeteerPageGotoTimeoutMs(config = {}) {
  const value = Number(config.puppeteerPageGotoTimeoutMs)
  return Number.isFinite(value) && value >= 30_000 ? value : PUPPETEER_PAGE_GOTO_TIMEOUT_MS
}

async function connectPuppeteer(puppeteer, wsEndpoint, config = {}) {
  return puppeteer.connect({
    browserWSEndpoint: wsEndpoint,
    protocolTimeout: puppeteerProtocolTimeoutMs(config),
    defaultViewport: null,
  })
}

function browserObservationEnabled(config = {}) {
  return config.browserObservationEnabled === true
}

function targetIdOf(target) {
  return target?._targetId || target?._targetInfo?.targetId || null
}

function browserResourceType(target) {
  const type = String(target?.type?.() || '').trim().toLowerCase()
  if (type === 'page') return 'observed_tab'
  if (type === 'background_page') return 'extension_background_page'
  if (type === 'service_worker') return 'service_worker'
  if (type === 'shared_worker') return 'shared_worker'
  return type ? `target_${type}` : 'unknown_target'
}

function rememberObservedBrowserEnvironment(context = {}, data = null) {
  const providerProfileId = String(context.providerProfileId || '').trim()
  const wsEndpoint = data?.ws?.puppeteer || context.wsEndpoint || ''
  if (!providerProfileId || !wsEndpoint) return null
  const previous = observedBrowserEnvironments.get(providerProfileId) || {}
  const browserId = browserWsBrowserId(wsEndpoint)
  const sessionEpoch = observedBrowserSessionEpoch(HELPER_BOOT_ID, wsEndpoint)
  const next = {
    ...previous,
    browserEnvironmentId: Number(context.browserEnvironmentId) || previous.browserEnvironmentId || null,
    environmentKey: context.environmentKey || previous.environmentKey || null,
    providerProfileId,
    browserWsBrowserId: browserId,
    browserSessionEpoch: sessionEpoch,
    platform: context.platform || previous.platform || null,
    ownerType: context.ownerType || previous.ownerType || 'unknown',
    lastTaskActivityAt: context.lastTaskActivityAt || previous.lastTaskActivityAt || null,
    wsEndpoint,
    lastRememberedAt: nowIso(),
  }
  if (previous.browserSessionEpoch && previous.browserSessionEpoch !== sessionEpoch) {
    next.consecutiveCdpFailures = 0
    next.metrics = null
    browserProcessCpuSamples.delete(providerProfileId)
  }
  observedBrowserEnvironments.set(providerProfileId, next)
  return next
}

function browserResourceContext(environmentContext = {}, resourceContext = {}) {
  return {
    providerProfileId: environmentContext.providerProfileId,
    browserSessionEpoch: environmentContext.browserSessionEpoch,
    browserWsBrowserId: environmentContext.browserWsBrowserId,
    browserEnvironmentId: environmentContext.browserEnvironmentId,
    environmentKey: environmentContext.environmentKey,
    platform: resourceContext.platform || environmentContext.platform || null,
    taskId: resourceContext.taskId || null,
    scheduleId: resourceContext.scheduleId || null,
    claimAttempt: resourceContext.claimAttempt || null,
    ownership: resourceContext.ownership || 'unknown',
    resourceOrigin: resourceContext.resourceOrigin || 'unknown',
    resourceType: resourceContext.resourceType || 'observed_tab',
    backendReportState: resourceContext.backendReportState || 'unknown',
    irreversibleBoundary: resourceContext.irreversibleBoundary || 'no_mutation',
    lastTaskActivityAt: resourceContext.lastTaskActivityAt || environmentContext.lastTaskActivityAt || null,
  }
}

async function registerCreatedBrowserPage(environmentContext, page, resourceContext = {}) {
  if (!environmentContext?.providerProfileId || !environmentContext.browserSessionEpoch || !page) return null
  const target = page.target()
  const targetId = targetIdOf(target)
  if (!targetId) return null
  const context = browserResourceContext(environmentContext, resourceContext)
  return browserResourceRegistry.registerResource({
    ...context,
    targetId,
    parentTargetId: targetIdOf(target?.opener?.()),
    pageUrl: page.url(),
    lifecycleState: 'active',
    openedAt: nowIso(),
    lastObservedAt: nowIso(),
  })
}

async function updateObservedBrowserPage(environmentContext, page, changes = {}) {
  if (!environmentContext?.providerProfileId || !environmentContext.browserSessionEpoch || !page) return null
  const targetId = targetIdOf(page.target())
  if (!targetId) return null
  return browserResourceRegistry.updateResource({
    providerProfileId: environmentContext.providerProfileId,
    browserSessionEpoch: environmentContext.browserSessionEpoch,
    targetId,
  }, {
    pageUrl: page.url(),
    ...changes,
  })
}

async function markObservedBrowserPageClosed(environmentContext, page, reason) {
  if (!environmentContext?.providerProfileId || !environmentContext.browserSessionEpoch || !page) return null
  const targetId = targetIdOf(page.target())
  if (!targetId) return null
  return browserResourceRegistry.markResourceClosed({
    providerProfileId: environmentContext.providerProfileId,
    browserSessionEpoch: environmentContext.browserSessionEpoch,
    targetId,
  }, reason)
}

function environmentTaskVolume(providerProfileId) {
  const tasks = listTasks().filter(
    (task) => String(task.providerProfileId || '').trim() === providerProfileId,
  )
  return {
    retainedTaskCount: tasks.length,
    activeTaskCount: tasks.filter(
      (task) => task.status === 'pending' || task.status === 'claimed',
    ).length,
    throughputSinceHelperBoot: helperTaskThroughputCounter.snapshot(providerProfileId),
  }
}

function recordTaskTerminalThroughput(task, status, providerProfileId = null) {
  if (task && typeof task === 'object') {
    if (throughputTerminalTasks.has(task)) return
    throughputTerminalTasks.add(task)
  }
  helperTaskThroughputCounter.increment(
    status === 'failed' ? 'failedTotal' : 'completedTotal',
    task?.providerProfileId || providerProfileId,
  )
}

async function collectWindowsBrowserProcessRows(processIds) {
  if (process.platform !== 'win32') return null
  const ids = [...new Set(processIds)]
    .map(Number)
    .filter((value) => Number.isInteger(value) && value > 0)
  if (!ids.length) return []
  const script = [
    `$ids = @(${ids.join(',')})`,
    '$rows = @(Get-Process -Id $ids -ErrorAction SilentlyContinue | Select-Object Id, ProcessName, CPU, WorkingSet64, HandleCount)',
    'ConvertTo-Json -Compress -InputObject $rows',
  ].join('; ')
  const { stdout } = await execFileAsync('powershell.exe', [
    '-NoLogo',
    '-NoProfile',
    '-NonInteractive',
    '-Command',
    script,
  ], {
    encoding: 'utf8',
    timeout: 5_000,
    windowsHide: true,
    maxBuffer: 512 * 1024,
  })
  const parsed = JSON.parse(String(stdout || '[]').replace(/^\uFEFF/, '') || '[]')
  return Array.isArray(parsed) ? parsed : [parsed]
}

async function collectBrowserProcessMetrics(providerProfileId, cdpSession) {
  const processStartedAt = Date.now()
  if (!cdpSession) {
    return { status: 'unavailable', reason: 'missing_browser_cdp_session' }
  }
  try {
    const response = await cdpSession.send('SystemInfo.getProcessInfo')
    const processInfo = Array.isArray(response?.processInfo) ? response.processInfo : []
    if (process.platform !== 'win32') {
      return {
        status: 'unsupported',
        platform: process.platform,
        processCount: processInfo.length,
        collectionLatencyMs: Date.now() - processStartedAt,
      }
    }
    const processRows = await collectWindowsBrowserProcessRows(
      processInfo.map((item) => item.id),
    )
    const previousSample = browserProcessCpuSamples.get(providerProfileId) || null
    const summarized = summarizeBrowserProcessMetrics({
      processInfo,
      processRows,
      previousSample,
      observedAtMs: Date.now(),
    })
    if (summarized.status === 'ok') {
      browserProcessCpuSamples.set(providerProfileId, summarized.sample)
    }
    const { sample, ...reported } = summarized
    return {
      ...reported,
      platform: process.platform,
      collectionLatencyMs: Date.now() - processStartedAt,
    }
  } catch (error) {
    browserRuntimeErrorCounter.record(error, providerProfileId)
    return {
      status: 'unavailable',
      platform: process.platform,
      reason: String(error?.message || error || '').slice(0, 300),
      collectionLatencyMs: Date.now() - processStartedAt,
    }
  }
}

function environmentMetricsFromObservation(context, targets, probe, observedAt) {
  const snapshot = browserResourceRegistry.snapshot()
  const activeResources = snapshot.resources.filter((resource) => (
    resource.providerProfileId === context.providerProfileId
      && resource.browserSessionEpoch === context.browserSessionEpoch
      && !['closed', 'create_failed', 'stale', 'target_missing'].includes(resource.lifecycleState)
  ))
  const ownershipCount = (ownership) => activeResources
    .filter((resource) => resource.ownership === ownership).length
  const protectedTargetCount = activeResources.filter((resource) => (
    resource.manualHoldRequired === true
      || resource.ownership === 'operator'
      || resource.lifecycleState === 'manual_hold_required'
  )).length
  const lastTaskActivityMs = Date.parse(context.lastTaskActivityAt || '')
  return {
    browserEnvironmentId: context.browserEnvironmentId || null,
    environmentKey: context.environmentKey || null,
    providerProfileId: context.providerProfileId,
    browserSessionEpoch: context.browserSessionEpoch,
    ownerType: context.ownerType || 'unknown',
    totalTargetCount: targets.length,
    managedTargetCount: ownershipCount('automation'),
    operatorTargetCount: ownershipCount('operator'),
    unknownTargetCount: ownershipCount('unknown'),
    protectedTargetCount,
    lastTaskActivityAt: context.lastTaskActivityAt || null,
    idleSeconds: Number.isFinite(lastTaskActivityMs)
      ? Math.max(0, Math.floor((Date.now() - lastTaskActivityMs) / 1_000))
      : null,
    cdpProbeLatencyMs: Math.max(0, Math.round(probe.totalLatencyMs)),
    cdpStepLatencyMs: probe.stepLatencyMs,
    browserPageCount: probe.browserPageCount,
    browserVersion: probe.browserVersion,
    processMetrics: probe.processMetrics,
    errorCounts: browserRuntimeErrorCounter.snapshot(context.providerProfileId),
    taskVolume: environmentTaskVolume(context.providerProfileId),
    helperUptimeSeconds: Math.max(0, Math.floor((Date.now() - Date.parse(STARTED_AT)) / 1_000)),
    circuitState: 'not_implemented',
    consecutiveCdpFailures: 0,
    lastCleanupAt: null,
    lastCleanupResult: 'observation_only',
    observationStatus: 'ok',
    lastSuccessfulObservedAt: observedAt,
    observedAt,
  }
}

async function observeAdspowerBrowserSession(config, context, data = null) {
  if (!browserObservationEnabled(config)) return null
  const environmentContext = rememberObservedBrowserEnvironment(context, data)
  if (!environmentContext?.wsEndpoint || !environmentContext.browserSessionEpoch) return null
  const startedAt = Date.now()
  let browser
  let cdpSession
  try {
    const { default: puppeteer } = await import('puppeteer-core')
    const connectStartedAt = Date.now()
    browser = await connectPuppeteer(puppeteer, environmentContext.wsEndpoint, config)
    const connectLatencyMs = Date.now() - connectStartedAt
    cdpSession = await browser.target().createCDPSession()
    const getVersionStartedAt = Date.now()
    const version = await cdpSession.send('Browser.getVersion')
    const getVersionLatencyMs = Date.now() - getVersionStartedAt
    const pagesStartedAt = Date.now()
    const pages = await browser.pages()
    const pagesLatencyMs = Date.now() - pagesStartedAt
    const targets = browser.targets().map((target) => ({
      targetId: targetIdOf(target),
      parentTargetId: targetIdOf(target.opener?.()),
      resourceType: browserResourceType(target),
      pageUrl: target.url(),
      ownership: 'unknown',
      resourceOrigin: 'startup_discovered',
    })).filter((target) => target.targetId)
    const cdpProbeLatencyMs = connectLatencyMs + getVersionLatencyMs + pagesLatencyMs
    const processMetrics = await collectBrowserProcessMetrics(
      environmentContext.providerProfileId,
      cdpSession,
    )
    const observedAt = nowIso()
    await browserResourceRegistry.reconcileEnvironment({
      ...environmentContext,
      targets,
      observedAt,
    })
    const latest = observedBrowserEnvironments.get(environmentContext.providerProfileId) || environmentContext
    latest.consecutiveCdpFailures = 0
    latest.lastObservationError = null
    latest.metrics = environmentMetricsFromObservation(latest, targets, {
      totalLatencyMs: cdpProbeLatencyMs,
      stepLatencyMs: {
        connectMs: connectLatencyMs,
        browserGetVersionMs: getVersionLatencyMs,
        browserPagesMs: pagesLatencyMs,
      },
      browserPageCount: pages.length,
      browserVersion: {
        product: String(version?.product || '').slice(0, 128) || null,
        protocolVersion: String(version?.protocolVersion || '').slice(0, 32) || null,
      },
      processMetrics,
    }, observedAt)
    observedBrowserEnvironments.set(environmentContext.providerProfileId, latest)
    lastBrowserObservationStatus = {
      at: observedAt,
      ok: true,
      providerProfileId: environmentContext.providerProfileId,
      targetCount: targets.length,
      latencyMs: latest.metrics.cdpProbeLatencyMs,
    }
    return latest.metrics
  } catch (error) {
    browserRuntimeErrorCounter.record(error, environmentContext.providerProfileId)
    const latest = observedBrowserEnvironments.get(environmentContext.providerProfileId) || environmentContext
    latest.consecutiveCdpFailures = Number(latest.consecutiveCdpFailures || 0) + 1
    latest.lastObservationError = String(error?.message || error || '').slice(0, 500)
    latest.metrics = buildFailedBrowserObservationMetrics({
      context: latest,
      previousMetrics: latest.metrics,
      failedProbeDurationMs: Date.now() - startedAt,
      consecutiveCdpFailures: latest.consecutiveCdpFailures,
      errorCounts: browserRuntimeErrorCounter.snapshot(latest.providerProfileId),
      taskVolume: environmentTaskVolume(latest.providerProfileId),
      helperUptimeSeconds: Math.max(0, Math.floor((Date.now() - Date.parse(STARTED_AT)) / 1_000)),
      observationError: latest.lastObservationError,
      observedAt: nowIso(),
    })
    observedBrowserEnvironments.set(environmentContext.providerProfileId, latest)
    lastBrowserObservationStatus = {
      at: nowIso(),
      ok: false,
      providerProfileId: environmentContext.providerProfileId,
      error: latest.lastObservationError,
    }
    throw error
  } finally {
    await cdpSession?.detach().catch(() => null)
    await safePuppeteerDisconnect(browser)
  }
}

function scheduleBrowserObservation(config, context, data = null) {
  if (!browserObservationEnabled(config)) return
  const environmentContext = rememberObservedBrowserEnvironment(context, data)
  const profileId = environmentContext?.providerProfileId
  if (!profileId || browserObservationInFlight.has(profileId)) return
  browserObservationInFlight
    .start(profileId, () => observeAdspowerBrowserSession(config, environmentContext))
    .catch(() => null)
}

async function refreshBrowserResourceObservations(config) {
  if (!browserObservationEnabled(config)) return []
  const operations = []
  const configuredInterval = Number(
    config.browserObservationIntervalMs || BROWSER_OBSERVATION_INTERVAL_MS,
  )
  const minimumIntervalMs = Number.isFinite(configuredInterval)
    ? Math.max(30_000, configuredInterval)
    : BROWSER_OBSERVATION_INTERVAL_MS
  for (const context of observedBrowserEnvironments.values()) {
    const lastObservedAt = Date.parse(context.metrics?.observedAt || '')
    const alreadyRunning = browserObservationInFlight.has(context.providerProfileId)
    if (!alreadyRunning
      && Number.isFinite(lastObservedAt)
      && Date.now() - lastObservedAt < minimumIntervalMs) {
      continue
    }
    if (!alreadyRunning) {
      browserObservationInFlight.start(
        context.providerProfileId,
        () => observeAdspowerBrowserSession(config, context),
      ).catch(() => null)
    }
    const wait = browserObservationInFlight.wait(
      context.providerProfileId,
      20_000,
      `browser observation ${context.providerProfileId}`,
    ).catch(() => null)
    operations.push(wait)
  }
  await Promise.all(operations)
  return Array.from(observedBrowserEnvironments.values())
    .map((context) => context.metrics)
    .filter(Boolean)
}

function browserResourceMetrics() {
  const snapshot = browserResourceRegistry.snapshot()
  const environments = Array.from(observedBrowserEnvironments.values())
    .map((context) => context.metrics)
    .filter(Boolean)
  return {
    schemaVersion: 1,
    helperBootId: HELPER_BOOT_ID,
    cumulativeScope: 'helper_boot',
    observationOnly: true,
    environments,
    summary: {
      managedEnvironmentCount: environments.length,
      totalManagedTabCount: environments.reduce(
        (total, item) => total + Number(item.managedTargetCount || 0),
        0,
      ),
      totalObservedTargetCount: environments.reduce(
        (total, item) => total + Number(item.totalTargetCount || 0),
        0,
      ),
      cleanupClosedTabsTotal: 0,
      cleanupStoppedEnvironmentsTotal: 0,
      globalCircuitState: 'not_implemented',
      registryRevision: snapshot.registryRevision,
      registryHealth: snapshot.registryHealth?.status || 'unknown',
      helperUptimeSeconds: Math.max(0, Math.floor((Date.now() - Date.parse(STARTED_AT)) / 1_000)),
      retainedRuntimeTaskCount: listTasks().length,
      activeRuntimeTaskCount: activeRuntimeTaskCount(),
      errorCounts: browserRuntimeErrorCounter.snapshot(),
      inFlightObservationCount: browserObservationInFlight.size(),
      taskThroughputSinceHelperBoot: helperTaskThroughputCounter.snapshot(),
    },
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

function runtimeSessionPath(profileKey = runtimeSettings.activeProfile || DEFAULT_PROFILE_KEY) {
  return new URL(`${normalizeProfileKey(profileKey)}.json`, SESSIONS_DIR)
}

async function loadRuntimeSession(profileKey = runtimeSettings.activeProfile || DEFAULT_PROFILE_KEY) {
  runtimeSession = null
  try {
    const sessionPath = runtimeSessionPath(profileKey)
    const raw = await fs.readFile(sessionPath, 'utf8')
    const session = parseJsonText(raw)
    if (session?.sessionId && session?.hmacSecret) {
      runtimeSession = session
      return
    }
  } catch {
    runtimeSession = await loadLegacyRuntimeSession(profileKey)
    return
  }
  runtimeSession = await loadLegacyRuntimeSession(profileKey)
}

async function loadLegacyRuntimeSession(profileKey) {
  if (normalizeProfileKey(profileKey) !== DEFAULT_PROFILE_KEY) return null
  try {
    const raw = await fs.readFile(SESSION_PATH, 'utf8')
    const session = parseJsonText(raw)
    if (session?.sessionId && session?.hmacSecret) return session
  } catch {
    // Older helpers may not have a paired session yet.
  }
  return null
}

async function saveRuntimeSession(session, profileKey = runtimeSettings.activeProfile || DEFAULT_PROFILE_KEY) {
  await fs.mkdir(SESSIONS_DIR, { recursive: true })
  await fs.writeFile(runtimeSessionPath(profileKey), JSON.stringify(session, null, 2), 'utf8')
}

async function getMachineId() {
  if (machineIdCache) return machineIdCache
  try {
    const value = String(await fs.readFile(MACHINE_ID_PATH, 'utf8')).trim()
    if (value) {
      machineIdCache = value
      return machineIdCache
    }
  } catch {
    // First run has no machine id yet.
  }
  machineIdCache = crypto.randomUUID()
  await fs.mkdir(RUNTIME_DIR, { recursive: true })
  await fs.writeFile(MACHINE_ID_PATH, `${machineIdCache}\n`, 'utf8')
  return machineIdCache
}

function activeRuntimeTaskCount() {
  return occupiedScheduleClaimSlotCount()
}

function occupiedScheduleClaimSlotCount() {
  return listTasks().filter((task) => (
    scheduleIdOfTask(task)
    && (task.status === 'pending' || task.status === 'claimed')
  )).length
}

function hasAvailableScheduleClaimSlot(config) {
  const capacity = Math.max(1, Number(config.localAgentCapacity || 1) || 1)
  return occupiedScheduleClaimSlotCount() < capacity
}

async function probeAdspowerApi(config) {
  try {
    await adspowerGet(config, '/api/v1/user/list?page=1&page_size=1')
    lastAdspowerApiStatus = { ok: true, checkedAt: nowIso(), error: null }
  } catch (error) {
    lastAdspowerApiStatus = {
      ok: false,
      checkedAt: nowIso(),
      error: String(error?.message || error || '').slice(0, 500),
    }
  }
  return lastAdspowerApiStatus
}

async function reportLocalAgentRuntimeStatus(config, options = {}) {
  if (!runtimeSession?.sessionId || !runtimeSession?.hmacSecret) {
    return { ok: false, skipped: true, reason: 'not_paired' }
  }
  if (localAgentRuntimeStatusInFlight && !options.force) {
    return { ok: false, skipped: true, reason: 'in_flight' }
  }
  localAgentRuntimeStatusInFlight = true
  try {
    if (options.probeAdspower !== false) {
      await probeAdspowerApi(config)
    }
    if (browserObservationEnabled(config)) {
      refreshBrowserResourceObservations(config).catch(() => null)
    }
    const packageInfo = await readPackageInfo()
    const lifecycleMetrics = browserResourceMetrics()
    const body = JSON.stringify({
      machineId: await getMachineId(),
      activeProfile: config.activeProfile || runtimeSettings.activeProfile || DEFAULT_PROFILE_KEY,
      helperVersion: packageInfo.version || '0.0.0',
      protocolVersion: '1',
      helperName: config.helperName || packageInfo.name || 'geo-local-helper',
      adspowerApiOk: Boolean(lastAdspowerApiStatus.ok),
      adspowerApiBase: effectiveAdspowerConfig(config).apiBase,
      runningTaskCount: activeRuntimeTaskCount(),
      capacity: Number(config.localAgentCapacity || 1) || 1,
      supportedPlatforms: cachedSelfMediaSchedulePlatforms || [],
      capabilities: {
        adspowerLaunch: true,
        claim: true,
        publishCheck: true,
        douyinImageText: true,
        extensionStatusProbe: true,
        buildRevision: packageInfo.buildRevision || null,
        browserLifecycle: {
          version: 2,
          observation: browserObservationEnabled(config),
          tabCleanup: false,
          environmentStopLease: false,
          cleanupPlans: false,
        },
      },
      runtimeState: browserObservationEnabled(config) ? 'observing' : 'legacy',
      resourceMetrics: lifecycleMetrics,
      lastCleanupAt: null,
      helperBootId: HELPER_BOOT_ID,
      policyVersion: null,
      lastErrorCode: options.lastErrorCode || null,
      lastErrorMessage: options.lastErrorMessage || lastAdspowerApiStatus.error || null,
    })
    const status = await signedTrustedBackendRequest(config, '/api/v1/local-agent/runtime-status', {
      method: 'POST',
      body,
      signatureBodyText: '',
    })
    lastLocalAgentRuntimeStatus = {
      at: nowIso(),
      ok: true,
      reason: options.reason || 'report',
      status,
    }
    return { ok: true, status }
  } catch (error) {
    lastLocalAgentRuntimeStatus = {
      at: nowIso(),
      ok: false,
      reason: options.reason || 'report',
      error: String(error?.message || error || '').slice(0, 500),
    }
    return { ok: false, error: lastLocalAgentRuntimeStatus.error }
  } finally {
    localAgentRuntimeStatusInFlight = false
  }
}

async function loadRuntimeTasks() {
  try {
    const raw = await fs.readFile(TASKS_PATH, 'utf8')
    const tasks = parseJsonText(raw)
    for (const task of Array.isArray(tasks) ? tasks : []) {
      const normalized = normalizePersistedTask(task)
      if (normalized) tasksById.set(normalized.taskId, normalized)
    }
  } catch {
    // Fresh PoC helper startup has no task file.
  }
}

function restoreObservedBrowserEnvironmentsFromTasks() {
  for (const task of tasksById.values()) {
    const providerProfileId = String(task.providerProfileId || '').trim()
    const wsEndpoint = task.adspower?.puppeteerWs
    if (!providerProfileId || !wsEndpoint) continue
    const activityAt = task.lastStageAt
      || task.completedAt
      || task.failedAt
      || task.claimedAt
      || task.createdAt
      || null
    rememberObservedBrowserEnvironment({
      browserEnvironmentId: task.schedule?.browserEnvironmentId
        || task.backendTask?.browserEnvironmentId
        || null,
      environmentKey: task.environmentKey,
      providerProfileId,
      platform: task.platform,
      ownerType: 'unknown',
      lastTaskActivityAt: activityAt,
    }, { ws: { puppeteer: wsEndpoint } })
  }
}

async function saveRuntimeTasks() {
  pruneRuntimeTasks()
  await fs.mkdir(RUNTIME_DIR, { recursive: true })
  await fs.writeFile(TASKS_PATH, JSON.stringify(listTasks().map(compactRuntimeTaskForStorage), null, 2), 'utf8')
}

function pruneRuntimeTasks() {
  const tasks = listTasks()
  const now = Date.now()
  for (const task of tasks) {
    if (!isTerminalStatus(task.status)) continue
    if (hasPendingBackendReport(task)) continue
    const terminalAt = terminalTimeMs(task)
    if (Number.isFinite(terminalAt) && now - terminalAt > RUNTIME_TASK_TERMINAL_TTL_MS) {
      tasksById.delete(Number(task.taskId))
    }
  }

  const remaining = listTasks()
  compactStoredTerminalTasks()
  if (remaining.length <= RUNTIME_TASK_MAX_RECORDS) return
  const removable = remaining
    .filter((task) => isTerminalStatus(task.status) && !hasPendingBackendReport(task))
    .sort((left, right) => terminalTimeMs(left) - terminalTimeMs(right))
  let overflow = remaining.length - RUNTIME_TASK_MAX_RECORDS
  for (const task of removable) {
    if (overflow <= 0) break
    tasksById.delete(Number(task.taskId))
    overflow -= 1
  }
  compactStoredTerminalTasks()
}

function compactStoredTerminalTasks() {
  for (const task of tasksById.values()) {
    if (!isTerminalStatus(task.status) || hasPendingBackendReport(task)) continue
    tasksById.set(Number(task.taskId), compactRuntimeTaskForStorage(task))
  }
}

function hasPendingBackendReport(task) {
  if (!task || !isTerminalStatus(task.status)) return false
  if (task.taskKind === 'publish_result_check') {
    if (task.status === 'completed' && task.lastResult?.found === true) {
      return !task.backendSuccessReportedAt && !task.backendSuccessReportRejectedAt
    }
    if (task.status === 'completed' && task.lastResult?.found !== true) {
      return !task.backendUnknownReportedAt && !task.backendUnknownReportRejectedAt
    }
    if (task.status === 'failed') return !task.backendFailureReportedAt && !task.backendFailureReportRejectedAt
    return false
  }
  if (task.status === 'completed') return !task.backendSuccessReportedAt && !task.backendSuccessReportRejectedAt
  if (task.status === 'failed') return !task.backendFailureReportedAt && !task.backendFailureReportRejectedAt
  return false
}

function terminalTimeMs(task) {
  const value = task?.completedAt || task?.failedAt || task?.cancelledAt || task?.updatedAt || task?.createdAt || ''
  const parsed = Date.parse(value)
  return Number.isFinite(parsed) ? parsed : 0
}

function compactRuntimeTaskForStorage(task) {
  if (!task || !isTerminalStatus(task.status) || hasPendingBackendReport(task)) return task
  const compact = { ...task }
  if (compact.backendTask) {
    compact.backendTask = {
      id: compact.backendTask.id,
      scheduleId: compact.backendTask.scheduleId || compact.backendTask.platformOptions?.scheduleId,
      platform: compact.backendTask.platform || compact.backendTask.integrationMethod,
      selfMediaAccountId: compact.backendTask.selfMediaAccountId,
      browserEnvironmentAccountId: compact.backendTask.browserEnvironmentAccountId,
      platformOptions: compact.backendTask.platformOptions?.scheduleId
        ? { scheduleId: compact.backendTask.platformOptions.scheduleId }
        : compact.backendTask.platformOptions,
    }
  }
  if (compact.schedule) {
    compact.schedule = {
      id: compact.schedule.id,
      requestId: compact.schedule.requestId,
      requestIdempotencyKey: compact.schedule.requestIdempotencyKey,
      articleId: compact.schedule.articleId,
      brandId: compact.schedule.brandId,
      platform: compact.schedule.platform,
      status: compact.schedule.status,
      queueKind: compact.schedule.queueKind,
      attemptCount: compact.schedule.attemptCount,
      maxAttempts: compact.schedule.maxAttempts,
      failureCode: compact.schedule.failureCode,
      failureMessage: compact.schedule.failureMessage,
      platformPublishedUrl: compact.schedule.platformPublishedUrl,
    }
  }
  if (compact.fillResult) {
    compact.fillResult = {
      titleFilled: compact.fillResult.titleFilled,
      contentFilled: compact.fillResult.contentFilled,
      platform: compact.fillResult.platform,
      publishOptions: compact.fillResult.publishOptions
        ? {
            filled: compact.fillResult.publishOptions.filled,
            scheduled: compact.fillResult.publishOptions.scheduled,
            published: compact.fillResult.publishOptions.published,
          }
        : undefined,
    }
  }
  return compact
}

function cleanupRuntimeExtensionBindIntents() {
  pruneExtensionBindIntents()
}

async function loadRuntimeNonces() {
  try {
    const raw = await fs.readFile(NONCES_PATH, 'utf8')
    const records = parseJsonText(raw)
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
  return parseJsonText(raw)
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
    const error = new Error(`本地助手会话不匹配：当前助手 sessionId=${runtimeSession.sessionId}，请重新配对并刷新扩展绑定`)
    error.statusCode = 401
    error.code = 'LOCAL_HELPER_SESSION_MISMATCH'
    throw error
  }
  const timestampNumber = Number(timestamp)
  const clockSkewSeconds = Number.isFinite(timestampNumber)
    ? Math.abs(Math.floor(Date.now() / 1000) - timestampNumber)
    : null
  if (!Number.isFinite(timestampNumber) || clockSkewSeconds > SIGNATURE_MAX_SKEW_SECONDS) {
    const error = new Error(`本地助手与后台时间偏差过大(${clockSkewSeconds ?? '未知'}秒)，请同步系统时间`)
    error.statusCode = 401
    error.code = 'LOCAL_HELPER_CLOCK_SKEW'
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
    reportLocalAgentRuntimeStatus(config, { reason: 'paired', force: true }).catch(() => null)
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

async function handleProfiles(req, res, config) {
  sendJson(req, res, config, 200, {
    ok: true,
    activeProfile: config.activeProfile,
    activeProfileLabel: config.activeProfileLabel,
    profiles: publicProfiles(config),
  })
}

async function adspowerGet(config, path) {
  const adspower = effectiveAdspowerConfig(config)
  const url = new URL(path, adspower.apiBase)
  const headers = {}
  if (adspower.apiKey) headers.Authorization = `Bearer ${adspower.apiKey}`

  const response = await fetchWithTimeout(url, { headers }, ADSPOWER_FETCH_TIMEOUT_MS)
  const body = await responseJsonWithTimeout(response).catch(() => ({}))
  if (!response.ok || body.code !== 0) {
    const message = body.msg || body.message || `AdsPower request failed: ${response.status}`
    const error = new Error(message)
    error.statusCode = 502
    error.details = body
    throw error
  }
  return body.data
}

function isAdsPowerRateLimitError(error) {
  const text = `${error?.message || ''} ${error?.details?.msg || ''} ${error?.details?.message || ''}`
  return /too many requests|requests per second|rate limit/i.test(text)
}

async function adspowerGetWithRetry(config, path, options = {}) {
  const retryDelays = options.retryDelays || ADSPOWER_RATE_LIMIT_RETRY_DELAYS_MS
  let lastError = null
  for (let attempt = 0; attempt <= retryDelays.length; attempt += 1) {
    try {
      return await adspowerGet(config, path)
    } catch (error) {
      lastError = error
      if (!isAdsPowerRateLimitError(error) || attempt >= retryDelays.length) break
      await delay(retryDelays[attempt])
    }
  }
  if (isAdsPowerRateLimitError(lastError)) {
    const error = new Error('AdsPower 请求过于频繁，请稍后重试')
    error.statusCode = 429
    error.details = lastError?.details || { message: lastError?.message || String(lastError || '') }
    throw error
  }
  throw lastError
}

function adspowerSessionCacheKey(providerProfileId) {
  return String(providerProfileId || '').trim()
}

function cachedAdspowerBrowserSession(providerProfileId) {
  const key = adspowerSessionCacheKey(providerProfileId)
  const cached = adspowerBrowserSessions.get(key)
  if (!cached?.data?.ws?.puppeteer) return null
  if (Date.now() - Number(cached.updatedAt || 0) > ADSPOWER_BROWSER_SESSION_CACHE_MS) {
    adspowerBrowserSessions.delete(key)
    return null
  }
  return cached.data
}

async function startAdspowerBrowser(config, providerProfileId, options = {}) {
  const key = adspowerSessionCacheKey(providerProfileId)
  if (!key) {
    const error = new Error('providerProfileId is required')
    error.statusCode = 400
    throw error
  }
  if (options.forceRefresh) adspowerBrowserSessions.delete(key)
  const cached = cachedAdspowerBrowserSession(key)
  if (cached) return cached

  const existing = adspowerBrowserStartInFlight.get(key)
  if (existing) return existing

  const profileId = encodeURIComponent(key)
  const promise = adspowerGetWithRetry(
    config,
    `/api/v1/browser/start?user_id=${profileId}&open_tabs=1&ip_tab=0`,
  ).then((data) => {
    adspowerBrowserSessions.set(key, { data, updatedAt: Date.now() })
    return data
  }).finally(() => {
    adspowerBrowserStartInFlight.delete(key)
  })
  adspowerBrowserStartInFlight.set(key, promise)
  return promise
}

function isStaleAdspowerBrowserSessionError(error) {
  const text = [
    error?.code,
    error?.message,
    error?.cause?.code,
    error?.cause?.message,
  ].filter(Boolean).join(' ')
  return /ECONNREFUSED|ECONNRESET|socket hang up|websocket.*(?:closed|failed)|browser has disconnected|Network\.enable timed out|protocolTimeout|Protocol error/i.test(text)
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
    profileKey: config.activeProfile,
    profileLabel: config.activeProfileLabel,
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
    profileKey: intent.profileKey,
    profileLabel: intent.profileLabel,
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
    profileKey: intent.profileKey,
    profileLabel: intent.profileLabel,
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
  const body = await responseJsonWithTimeout(response).catch(() => ({}))
  if (!response.ok || (body.code !== undefined && body.code !== 0)) {
    const error = new Error(body.message || `backend request failed: ${response.status}`)
    error.statusCode = response.status === 401 ? 401 : 502
    error.backendCode = body?.data?.code || body?.code || null
    error.details = body
    throw error
  }
  return body.data
}

async function trustedBackendRequest(config, path, init = {}) {
  const backendBase = String(config.trustedBackendBase || '').replace(/\/+$/, '')
  const requestUrl = `${backendBase}${path}`
  let response
  try {
    response = await fetchWithTimeout(requestUrl, {
      ...init,
      headers: {
        'Content-Type': 'application/json',
        ...(init.headers || {}),
      },
    }, BACKEND_FETCH_TIMEOUT_MS)
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error || '')
    const backendError = new Error(
      `无法连接后台地址 ${backendBase || '(未配置)'}：${message || 'network request failed'}。请检查本地助手当前后台地址、后端服务和网络/防火墙。`,
    )
    backendError.statusCode = 502
    throw backendError
  }
  const body = await responseJsonWithTimeout(response).catch(() => ({}))
  if (!response.ok || (body.code !== undefined && body.code !== 0)) {
    const details = body && Object.keys(body).length ? `; details=${JSON.stringify(body).slice(0, 600)}` : ''
    const error = new Error(body.message || `trusted backend request failed: ${response.status}${details}`)
    error.statusCode = response.status
    error.backendCode = body?.data?.code || body?.code || null
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
  const bodyText = Object.prototype.hasOwnProperty.call(init, 'signatureBodyText')
    ? String(init.signatureBodyText || '')
    : typeof init.body === 'string' ? init.body : ''
  const { signatureBodyText: _signatureBodyText, ...requestInit } = init
  return trustedBackendRequest(config, path, {
    ...requestInit,
    method,
    headers: {
      ...(requestInit.headers || {}),
      ...signedBackendHeaders(method, path, bodyText),
    },
  })
}

async function openUrlWithPuppeteer(config, wsEndpoint, targetUrl, resourceContext = {}) {
  if (!wsEndpoint || !targetUrl) return { opened: false, reason: 'missing_ws_or_url' }

  const { default: puppeteer } = await import('puppeteer-core')
  const browser = await connectPuppeteer(puppeteer, wsEndpoint, config)
  const environmentContext = browserObservationEnabled(config)
    ? rememberObservedBrowserEnvironment(resourceContext, { ws: { puppeteer: wsEndpoint } })
    : null
  let page
  try {
    page = await browser.newPage()
    await registerCreatedBrowserPage(environmentContext, page, resourceContext).catch(() => null)
    try {
      await page.goto(targetUrl, { waitUntil: 'domcontentloaded', timeout: puppeteerPageGotoTimeoutMs(config) })
    } catch (error) {
      await updateObservedBrowserPage(environmentContext, page, {
        lifecycleState: 'navigation_failed',
      }).catch(() => null)
      throw error
    }
    await updateObservedBrowserPage(environmentContext, page, {
      lifecycleState: 'active',
      lastTaskActivityAt: resourceContext.lastTaskActivityAt || nowIso(),
    }).catch(() => null)
    await page.bringToFront().catch(() => null)
    const target = page.target()
    return { opened: true, url: targetUrl, pageUrl: page.url(), targetId: target?._targetId || null }
  } finally {
    await safePuppeteerDisconnect(browser)
  }
}

async function safePuppeteerDisconnect(browser) {
  if (!browser) return
  await Promise.race([
    Promise.resolve().then(() => browser.disconnect()),
    delay(PUPPETEER_DISCONNECT_TIMEOUT_MS),
  ]).catch(() => null)
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
    buildRevision: '',
  }
  if (target.type() === 'service_worker') {
    const worker = await target.worker().catch(() => null)
    if (worker) {
      const manifest = await worker.evaluate(() => chrome.runtime.getManifest()).catch(() => null)
      result.name = String(manifest?.name || '')
      result.version = String(manifest?.version || '')
      result.buildRevision = String(manifest?.version_name || '')
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
  const browser = await connectPuppeteer(puppeteer, wsEndpoint)
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
        buildRevision: matched.buildRevision || null,
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
    await safePuppeteerDisconnect(browser)
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
  const data = await startAdspowerBrowser(config, environment.providerProfileId)
  const task = normalizeLaunchTask(body, environment, data)
  const observationContext = {
    browserEnvironmentId: body.browserEnvironmentId || body.backendTask?.browserEnvironmentId,
    environmentKey: environment.environmentKey,
    providerProfileId: environment.providerProfileId,
    platform: task.platform,
    ownerType: body.taskId ? 'unknown' : 'operator',
    lastTaskActivityAt: task.createdAt,
  }
  scheduleBrowserObservation(config, observationContext, data)
  upsertTask(task)
  await saveRuntimeTasks()
  task.openResult = await openUrlWithPuppeteer(config, data?.ws?.puppeteer, body.url, {
    ...observationContext,
    taskId: task.taskId,
    scheduleId: body.backendTask?.scheduleId || body.backendTask?.platformOptions?.scheduleId,
    ownership: 'automation',
    resourceOrigin: 'schedule_execution',
    resourceType: 'editor_tab',
    backendReportState: 'pending',
  })
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
    return {
      ok: true,
      claimed: false,
      claimBlockedReason: claim?.claimBlockedReason || 'NO_DUE_TASK',
      retryAfterSeconds: Number(claim?.retryAfterSeconds) || null,
    }
  }
  const taskId = Number(claim.launch.taskId || claim.task.id)
  const existing = tasksById.get(taskId)
  const claimedAttempt = Number(claim.schedule?.attemptCount || 0) || null
  if (isReusableActiveTask(existing) && claimAttemptOfTask(existing) === claimedAttempt) {
    return { ok: true, claimed: true, reused: true, task: existing, schedule: claim.schedule }
  }
  const claimedProviderProfileId = claim.launch.providerProfileId || claim.task.providerProfileId
  helperTaskThroughputCounter.increment('claimedTotal', claimedProviderProfileId)
  helperTaskThroughputCounter.increment('executionClaimedTotal', claimedProviderProfileId)
  let runtimeTask = null
  try {
    const environment = normalizeProviderEnvironment(
      config,
      claim.launch.environmentKey,
      claim.launch.providerProfileId,
      claim.launch.environmentName,
    )
    let data = await startAdspowerBrowser(config, environment.providerProfileId)
    const observationContext = {
      browserEnvironmentId: claim.launch.browserEnvironmentId || claim.schedule?.browserEnvironmentId,
      environmentKey: environment.environmentKey,
      providerProfileId: environment.providerProfileId,
      platform: claim.launch.platform || claim.task.platform,
      ownerType: 'unknown',
      lastTaskActivityAt: nowIso(),
    }
    scheduleBrowserObservation(config, observationContext, data)
    runtimeTask = normalizeLaunchTask({
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
    runtimeTask.schedule = claim.schedule || null
    runtimeTask.platformScheduledAt = claim.schedule?.platformScheduledAt || null
    upsertTask(runtimeTask)
    await saveRuntimeTasks()
    try {
      runtimeTask.openResult = await openUrlWithPuppeteer(config, data?.ws?.puppeteer, runtimeTask.url, {
        ...observationContext,
        taskId,
        scheduleId: claim.schedule?.id,
        claimAttempt: claimedAttempt,
        ownership: 'automation',
        resourceOrigin: 'schedule_execution',
        resourceType: 'editor_tab',
        backendReportState: 'pending',
      })
    } catch (error) {
      browserRuntimeErrorCounter.record(error, environment.providerProfileId)
      if (!isStaleAdspowerBrowserSessionError(error)) throw error
      data = await startAdspowerBrowser(config, environment.providerProfileId, { forceRefresh: true })
      rememberObservedBrowserEnvironment(observationContext, data)
      scheduleBrowserObservation(config, observationContext, data)
      runtimeTask.adspower = {
        puppeteerWs: data?.ws?.puppeteer || null,
        selenium: data?.ws?.selenium || null,
      }
      runtimeTask.openResult = await openUrlWithPuppeteer(config, data?.ws?.puppeteer, runtimeTask.url, {
        ...observationContext,
        taskId,
        scheduleId: claim.schedule?.id,
        claimAttempt: claimedAttempt,
        ownership: 'automation',
        resourceOrigin: 'schedule_execution',
        resourceType: 'editor_tab',
        backendReportState: 'pending',
      })
    }
    helperTaskThroughputCounter.increment(
      'executionStartedTotal',
      runtimeTask.providerProfileId || claimedProviderProfileId,
    )
    upsertTask(runtimeTask)
    await saveRuntimeTasks()
    return { ok: true, claimed: true, task: runtimeTask, schedule: claim.schedule }
  } catch (error) {
    browserRuntimeErrorCounter.record(
      error,
      runtimeTask?.providerProfileId || claimedProviderProfileId,
    )
    const failedAt = nowIso()
    const failureDetails = {
      code: 'LOCAL_HELPER_LAUNCH_FAILED',
      message: error instanceof Error ? error.message : String(error),
    }
    const failureTask = runtimeTask || {
      taskId,
      platform: claim.launch.platform || claim.task.platform,
      backendTask: claim.task,
      schedule: claim.schedule || null,
    }
    failureTask.status = 'failed'
    failureTask.failedAt = failedAt
    failureTask.failureCode = failureDetails.code
    failureTask.lastError = failureDetails
    failureTask.claimedAt = null
    failureTask.claimOwner = null
    recordTaskTerminalThroughput(failureTask, 'failed', claimedProviderProfileId)
    failureTask.backendFailureReportedAt = null
    failureTask.backendFailureReportAttempts = Number(failureTask.backendFailureReportAttempts || 0) + 1
    if (runtimeTask) {
      upsertTask(failureTask)
      await saveRuntimeTasks().catch((saveError) => {
        console.error('Failed to persist schedule launch failure:', saveError.message)
      })
    }
    try {
      await reportScheduleExecutionFailed(config, failureTask, {
        failureCode: failureDetails.code,
        failureMessage: failureDetails.message,
      })
      failureTask.backendFailureReportedAt = nowIso()
      failureTask.backendFailureReportLastError = null
    } catch (reportError) {
      failureTask.backendFailureReportLastError = formatBackendError(reportError)
      const terminated = terminateTaskForScheduleClaimError(failureTask, reportError)
      if (!terminated && isNonRetryableBackendReportError(reportError)) {
        failureTask.backendFailureReportRejectedAt = nowIso()
      }
      if (!terminated) {
        console.error('Failed to report schedule launch failure:', failureTask.backendFailureReportLastError)
      }
    }
    if (runtimeTask) {
      await saveRuntimeTasks().catch((saveError) => {
        console.error('Failed to persist schedule launch report result:', saveError.message)
      })
    }
    throw error
  }
}

async function claimAndCheckPublishResult(config, platform = 'toutiao') {
  await flushPendingPublishCheckFailureReports(config, platform)
  await flushPendingPublishCheckUnknownReports(config, platform)
  await flushPendingPublishCheckSuccessReports(config, platform)
  const path = `/api/v1/local-agent/self-media-schedules/publish-checks/claim-next?platform=${encodeURIComponent(platform)}`
  const claim = await signedTrustedBackendRequest(config, path, { method: 'GET' })
  if (!claim?.schedule || !claim?.launch) {
    return {
      ok: true,
      claimed: false,
      claimBlockedReason: claim?.claimBlockedReason || 'NO_DUE_TASK',
      retryAfterSeconds: Number(claim?.retryAfterSeconds) || null,
    }
  }
  const scheduleId = Number(claim.launch.scheduleId || claim.schedule.id)
  const claimAttempt = Number(claim.schedule.attemptCount || 0) || null
  const claimedProviderProfileId = claim.launch.providerProfileId || claim.schedule.providerProfileId
  helperTaskThroughputCounter.increment('claimedTotal', claimedProviderProfileId)
  helperTaskThroughputCounter.increment('publishCheckClaimedTotal', claimedProviderProfileId)
  let runtimeTask = null
  let result
  try {
    const environment = normalizeProviderEnvironment(
      config,
      claim.launch.environmentKey,
      claim.launch.providerProfileId,
      claim.launch.environmentName,
    )
    runtimeTask = createPublishCheckRuntimeTask(config, claim, scheduleId, environment)
    upsertTask(runtimeTask)
    await saveRuntimeTasks()
    markPublishCheckRuntimeTaskStage(runtimeTask, 'starting_adspower')
    upsertTask(runtimeTask)
    await saveRuntimeTasks()
    const data = await startAdspowerBrowser(config, environment.providerProfileId)
    const observationContext = {
      browserEnvironmentId: claim.launch.browserEnvironmentId || claim.schedule.browserEnvironmentId,
      environmentKey: environment.environmentKey,
      providerProfileId: environment.providerProfileId,
      platform: claim.launch.platform || claim.schedule.platform,
      ownerType: 'unknown',
      lastTaskActivityAt: nowIso(),
    }
    scheduleBrowserObservation(config, observationContext, data)
    runtimeTask.adspower = {
      puppeteerWs: data?.ws?.puppeteer || null,
      selenium: data?.ws?.selenium || null,
    }
    upsertTask(runtimeTask)
    await saveRuntimeTasks()
    markPublishCheckRuntimeTaskStage(runtimeTask, 'checking_page')
    upsertTask(runtimeTask)
    await saveRuntimeTasks()
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
    helperTaskThroughputCounter.increment(
      'publishCheckStartedTotal',
      runtimeTask.providerProfileId || claimedProviderProfileId,
    )
    result = await withTimeout(
      checkPublishResultInAdspowerPage(
        config,
        data?.ws?.puppeteer,
        checkUrl,
        checkSchedule,
        {
          ...observationContext,
          taskId: runtimeTask.taskId,
          scheduleId,
          claimAttempt,
          ownership: 'automation',
          resourceOrigin: 'publish_result_check',
          resourceType: 'publish_check_tab',
          backendReportState: 'pending',
        },
      ),
      publishCheckPageTimeoutMs(config, checkSchedule.platform || claim.launch.platform),
      `publish check page ${claim.launch.platform || claim.schedule.platform}`,
    )
    markPublishCheckRuntimeTaskStage(runtimeTask, 'reporting_result')
    runtimeTask.lastResult = compactPublishCheckRuntimeResult(result)
    upsertTask(runtimeTask)
    await saveRuntimeTasks()
  } catch (error) {
    browserRuntimeErrorCounter.record(
      error,
      runtimeTask?.providerProfileId || claimedProviderProfileId,
    )
    if (isPublishCheckEnvironmentConfigError(error)) {
      const configResult = {
        found: false,
        reason: 'BROWSER_ENVIRONMENT_BINDING_INVALID',
        failureCode: 'BROWSER_ENVIRONMENT_BINDING_INVALID',
        failureMessage: error instanceof Error ? error.message : String(error),
        targetTitle: claim.schedule?.publishCheckTitle || '',
        platformScheduledAt: claim.schedule?.platformScheduledAt || '',
        url: claim.launch?.url || '',
      }
      await reportPublishCheckUnknown(config, scheduleId, claimAttempt, configResult).catch((reportError) => {
        if (runtimeTask) {
          runtimeTask.backendUnknownReportAttempts = Number(runtimeTask.backendUnknownReportAttempts || 0) + 1
          runtimeTask.backendUnknownReportLastError = formatBackendError(reportError)
        }
        console.error('Failed to report publish check environment config issue:', formatBackendError(reportError))
      })
      if (runtimeTask && !runtimeTask.backendUnknownReportLastError) {
        runtimeTask.backendUnknownReportedAt = nowIso()
      }
      markPublishCheckRuntimeTaskFinished(runtimeTask, 'completed', configResult, claimedProviderProfileId)
      await saveRuntimeTasks().catch(() => null)
      return { ok: true, claimed: true, scheduleId, outcome: 'unknown', result: configResult }
    }
    if (isPublishCheckTimeoutError(error)) {
      const timeoutResult = {
        found: false,
        reason: 'PUBLISH_CHECK_PAGE_TIMEOUT',
        failureCode: 'PUBLISH_CHECK_PAGE_TIMEOUT',
        failureMessage: error instanceof Error ? error.message : String(error),
        targetTitle: claim.schedule?.publishCheckTitle || '',
        platformScheduledAt: claim.schedule?.platformScheduledAt || '',
        url: claim.launch?.url || '',
      }
      await reportPublishCheckUnknown(config, scheduleId, claimAttempt, timeoutResult).catch((reportError) => {
        if (runtimeTask) {
          runtimeTask.backendUnknownReportAttempts = Number(runtimeTask.backendUnknownReportAttempts || 0) + 1
          runtimeTask.backendUnknownReportLastError = formatBackendError(reportError)
        }
        console.error('Failed to report publish check timeout:', formatBackendError(reportError))
      })
      if (runtimeTask && !runtimeTask.backendUnknownReportLastError) {
        runtimeTask.backendUnknownReportedAt = nowIso()
      }
      markPublishCheckRuntimeTaskFinished(runtimeTask, 'completed', timeoutResult, claimedProviderProfileId)
      await saveRuntimeTasks().catch(() => null)
      return { ok: true, claimed: true, scheduleId, outcome: 'unknown', result: timeoutResult }
    }
    const failureResult = {
      failureCode: 'PUBLISH_RESULT_CHECK_HELPER_FAILED',
      failureMessage: error instanceof Error ? error.message : String(error),
    }
    await reportPublishCheckFailed(config, scheduleId, claimAttempt, failureResult).catch((reportError) => {
      console.error('Failed to report publish check helper failure:', formatBackendError(reportError))
    })
    markPublishCheckRuntimeTaskFinished(runtimeTask, 'failed', failureResult, claimedProviderProfileId)
    await saveRuntimeTasks().catch(() => null)
    throw error
  }
  if (result.failed) {
    markPublishCheckRuntimeTaskStage(runtimeTask, 'reporting_failed')
    upsertTask(runtimeTask)
    await saveRuntimeTasks()
    await reportPublishCheckFailed(config, scheduleId, claimAttempt, result)
    markPublishCheckRuntimeTaskFinished(runtimeTask, 'failed', result, claimedProviderProfileId)
    await saveRuntimeTasks().catch(() => null)
    return { ok: true, claimed: true, scheduleId, outcome: 'failed', result }
  }
  if (result.found) {
    markPublishCheckRuntimeTaskStage(runtimeTask, 'reporting_published')
    runtimeTask.lastResult = compactPublishCheckRuntimeResult(result)
    upsertTask(runtimeTask)
    await saveRuntimeTasks()
    try {
      await withTimeout(
        reportPublishCheckPublished(config, scheduleId, claimAttempt, result),
        BACKEND_FETCH_TIMEOUT_MS + RESPONSE_JSON_TIMEOUT_MS + 5_000,
        `report publish check published ${scheduleId}`,
      )
      runtimeTask.backendSuccessReportedAt = nowIso()
      runtimeTask.backendSuccessReportLastError = null
    } catch (error) {
      runtimeTask.backendSuccessReportAttempts = Number(runtimeTask.backendSuccessReportAttempts || 0) + 1
      runtimeTask.backendSuccessReportLastError = formatBackendError(error)
      terminateTaskForScheduleClaimError(runtimeTask, error)
      markPublishCheckRuntimeTaskFinished(runtimeTask, 'completed', result, claimedProviderProfileId)
      await saveRuntimeTasks().catch(() => null)
      return { ok: true, claimed: true, scheduleId, outcome: 'published_report_pending', result }
    }
    markPublishCheckRuntimeTaskFinished(runtimeTask, 'completed', result, claimedProviderProfileId)
    await saveRuntimeTasks().catch(() => null)
    return { ok: true, claimed: true, scheduleId, outcome: 'published', result }
  }
  markPublishCheckRuntimeTaskStage(runtimeTask, 'reporting_unknown')
  upsertTask(runtimeTask)
  await saveRuntimeTasks()
  try {
    await withTimeout(
      reportPublishCheckUnknown(config, scheduleId, claimAttempt, result),
      BACKEND_FETCH_TIMEOUT_MS + RESPONSE_JSON_TIMEOUT_MS + 5_000,
      `report publish check unknown ${scheduleId}`,
    )
    runtimeTask.backendUnknownReportedAt = nowIso()
    runtimeTask.backendUnknownReportLastError = null
  } catch (error) {
    runtimeTask.backendUnknownReportAttempts = Number(runtimeTask.backendUnknownReportAttempts || 0) + 1
    runtimeTask.backendUnknownReportLastError = formatBackendError(error)
    terminateTaskForScheduleClaimError(runtimeTask, error)
    markPublishCheckRuntimeTaskFinished(runtimeTask, 'completed', result, claimedProviderProfileId)
    await saveRuntimeTasks().catch(() => null)
    return { ok: true, claimed: true, scheduleId, outcome: 'unknown_report_pending', result }
  }
  markPublishCheckRuntimeTaskFinished(runtimeTask, 'completed', result, claimedProviderProfileId)
  await saveRuntimeTasks().catch(() => null)
  return { ok: true, claimed: true, scheduleId, outcome: 'unknown', result }
}

function isPublishCheckTimeoutError(error) {
  const message = error instanceof Error ? error.message : String(error || '')
  return /publish check page .*timeout after/i.test(message)
}

function isPublishCheckEnvironmentConfigError(error) {
  const message = error instanceof Error ? error.message : String(error || '')
  return /environmentKey is required|providerProfileId is required|unknown environmentKey/i.test(message)
}

function createPublishCheckRuntimeTask(config, claim, scheduleId, environment) {
  const platform = claim.launch.platform || claim.schedule.platform || ''
  return {
    taskId: publishCheckRuntimeTaskId(scheduleId),
    taskKind: 'publish_result_check',
    platform,
    backendBase: config.trustedBackendBase,
    backendTask: {
      scheduleId,
      platform,
      platformOptions: { scheduleId },
    },
    schedule: claim.schedule || null,
    url: claim.launch.url || defaultWorksListUrlForPlatform(platform),
    selfMediaAccountId: claim.launch.selfMediaAccountId || claim.schedule.selfMediaAccountId,
    browserEnvironmentAccountId: claim.launch.browserEnvironmentAccountId || claim.schedule.browserEnvironmentAccountId,
    expectedPlatformAccountId: claim.launch.expectedPlatformAccountId || claim.schedule.expectedPlatformAccountId,
    expectedAccountName: claim.launch.expectedAccountName || claim.schedule.expectedAccountName || '',
    environmentKey: environment.environmentKey,
    providerProfileId: environment.providerProfileId,
    environmentName: environment.environmentName,
    status: 'claimed',
    createdAt: nowIso(),
    claimedAt: nowIso(),
    completedAt: null,
    failedAt: null,
    requeuedAt: null,
    cancelledAt: null,
    claimOwner: 'local-helper-publish-check',
    lastError: null,
    adspower: null,
  }
}

function publishCheckRuntimeTaskId(scheduleId) {
  return PUBLISH_CHECK_TASK_ID_OFFSET + Number(scheduleId || 0)
}

function publishCheckPageTimeoutMs(config, platform) {
  const configured = Number(config.selfMediaPublishCheckPageTimeoutMs)
  if (Number.isFinite(configured) && configured >= 60_000) return configured
  const minimum = puppeteerProtocolTimeoutMs(config) + 30_000
  const normalized = String(platform || '').trim().toLowerCase()
  if (normalized === 'baijiahao') return Math.max(150_000, minimum)
  if (normalized === 'douyin') return Math.max(150_000, minimum)
  return Math.max(120_000, minimum)
}

function markPublishCheckRuntimeTaskStage(task, stage) {
  if (!task) return
  task.lastStage = stage
  task.lastStageAt = nowIso()
}

function compactPublishCheckRuntimeResult(result) {
  if (!result || typeof result !== 'object') return null
  return {
    found: result.found === true,
    failed: result.failed === true,
    reason: result.reason || '',
    platformStatus: result.platformStatus || '',
    matchStrategy: result.matchStrategy || '',
    candidateCount: result.candidateCount,
    cardCandidateCount: result.cardCandidateCount,
    matchedCard: result.matchedCard || null,
    platformPublishedUrl: publishedUrlFromPublishCheckResult(result),
    platformPublishId: result.platformPublishId || '',
    topCandidates: Array.isArray(result.topCandidates) ? result.topCandidates.slice(0, 5) : [],
    targetTitle: result.targetTitle || '',
    url: result.url || '',
    pageTitle: result.pageTitle || '',
    textSample: String(result.textSample || '').slice(0, 1200),
    reloadCount: result.reloadCount,
    hasTitle: result.hasTitle === true,
    hasPublishedSignal: result.hasPublishedSignal === true,
  }
}

function markPublishCheckRuntimeTaskFinished(task, status, result, providerProfileId = null) {
  recordTaskTerminalThroughput(task, status, providerProfileId)
  if (!task) return
  task.status = status
  markPublishCheckRuntimeTaskStage(task, status === 'failed' ? 'failed' : 'completed')
  task.claimedAt = null
  task.claimOwner = null
  if (status === 'failed') {
    task.failedAt = nowIso()
    task.lastError = {
      code: result?.failureCode || 'PUBLISH_RESULT_CHECK_FAILED',
      message: result?.failureMessage || result?.reason || 'publish result check failed',
    }
    return
  }
  task.completedAt = nowIso()
  task.lastResult = compactPublishCheckRuntimeResult(result)
}

async function checkPublishResultInAdspowerPage(
  config,
  wsEndpoint,
  targetUrl,
  schedule,
  resourceContext = {},
) {
  if (!wsEndpoint || !targetUrl) {
    throw new Error('publish result check requires active AdsPower browser and works list url')
  }
  const { default: puppeteer } = await import('puppeteer-core')
  const browser = await connectPuppeteer(puppeteer, wsEndpoint, config)
  const environmentContext = browserObservationEnabled(config)
    ? rememberObservedBrowserEnvironment(resourceContext, { ws: { puppeteer: wsEndpoint } })
    : null
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
    const checkPage = await reuseOrCreatePublishCheckPage(browser, platform, effectiveTargetUrl)
    const { page } = checkPage
    if (checkPage.created) {
      await registerCreatedBrowserPage(environmentContext, page, resourceContext).catch(() => null)
    }
    try {
      await page.goto(effectiveTargetUrl, { waitUntil: 'domcontentloaded', timeout: puppeteerPageGotoTimeoutMs(config) })
      await updateObservedBrowserPage(environmentContext, page, {
        lifecycleState: 'active',
        lastTaskActivityAt: resourceContext.lastTaskActivityAt || nowIso(),
      }).catch(() => null)
      await waitForPublishCheckPageReady(page, platform)
      await delay(1_000)
      const deadline = Date.now() + publishCheckEvaluateTimeoutMs(platform)
      let latest = null
      let reloadCount = 0
      while (Date.now() < deadline) {
        latest = await evaluatePublishResult(page, schedule)
        if (latest.found || latest.failed) return latest
        if (shouldReloadPublishCheckPage(platform, latest, reloadCount)) {
          reloadCount += 1
          await page.reload({ waitUntil: 'domcontentloaded', timeout: puppeteerPageGotoTimeoutMs(config) }).catch(() => null)
          await waitForPublishCheckPageReady(page, platform)
          await delay(1_000)
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
      if (checkPage.created && !page.isClosed()) {
        await page.close().catch(() => null)
        if (page.isClosed()) {
          await markObservedBrowserPageClosed(
            environmentContext,
            page,
            'existing_publish_check_cleanup',
          ).catch(() => null)
        }
      }
    }
  } finally {
    await browser.disconnect()
  }
}

async function waitForPublishCheckPageReady(page, platform) {
  const normalized = String(platform || '').trim().toLowerCase()
  if (normalized === 'toutiao') {
    await page.waitForFunction(() => {
      const text = document.body?.innerText || ''
      return location.pathname.includes('/profile_v4/graphic/articles')
        && Boolean(
          document.querySelector('.article-card')
          || /共\s*0\s*条内容|暂无内容/.test(text)
        )
    }, { timeout: 15_000 }).catch(() => null)
    return
  }
  if (normalized === 'douyin') {
    await page.waitForFunction(() => {
      const text = document.body?.innerText || ''
      return location.pathname.includes('/creator-micro/content/manage')
        && Boolean(
          document.querySelector('[class*="info-title-text-"]')
          || document.querySelector('[class*="video-card-content-"]')
          || /共\s*0\s*个作品|暂无作品|没有更多作品/.test(text)
        )
    }, { timeout: 15_000 }).catch(() => null)
    return
  }
  if (normalized !== 'baijiahao') return
  await page.waitForFunction(() => {
    const text = document.body?.innerText || ''
    return Boolean(
      document.querySelector('[class*="articleItem"]')
      || document.querySelector('a[href*="baijiahao.baidu.com/s?id="]')
      || /共\s*\d+\s*篇/.test(text)
    )
  }, { timeout: 10_000 }).catch(() => null)
}

function publishCheckEvaluateTimeoutMs(platform) {
  const normalized = String(platform || '').trim().toLowerCase()
  return normalized === 'douyin' || normalized === 'toutiao' ? 45_000 : 20_000
}

function shouldReloadPublishCheckPage(platform, result, reloadCount) {
  const normalized = String(platform || '').trim().toLowerCase()
  if (reloadCount >= 2 || result?.found) return false
  if (normalized === 'toutiao') {
    return Number(result?.cardCandidateCount || 0) === 0
      || !result?.hasTitle
      || (!result?.hasPublishedSignal && !result?.hasScheduledSignal)
  }
  if (normalized === 'baijiahao') {
    const text = String(result?.textSample || '')
    if (/定时发文|提交成功[，,]\s*正在审核中/.test(text)) return true
    const hasLoadedWorksList = Number(result?.cardCandidateCount || 0) > 0
      || Number(result?.candidateCount || 0) > 0
      || /共\s*\d+\s*篇/.test(text)
    if (hasLoadedWorksList) return false
    return !result?.hasTitle && !result?.hasPublishedSignal
  }
  if (normalized !== 'douyin') return false
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
      && (current.pathname.includes('/profile_v4/graphic/articles')
        || current.pathname.includes('/profile_v4/manage/content'))
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
  const pageState = await page.evaluate(() => {
    const isVisible = (element) => {
      if (!element?.getBoundingClientRect) return false
      const style = window.getComputedStyle(element)
      const rect = element.getBoundingClientRect()
      return style.display !== 'none'
        && style.visibility !== 'hidden'
        && Number(style.opacity) !== 0
        && rect.width > 0
        && rect.height > 0
    }
    const text = document.body?.innerText || ''
    return {
      url: location.href,
      pageTitle: document.title,
      textSample: text.slice(0, 1200),
      text,
      toutiaoCards: Array.from(document.querySelectorAll('.article-card'))
        .filter(isVisible)
        .map((card) => {
          const titleElement = card.querySelector('a.title, .title-wrap a[href*="toutiao.com/item/"]')
          const tags = Array.from(card.querySelectorAll('.abstruct .byte-tag'))
            .map((element) => String(element.textContent || '').replace(/\s+/g, ' ').trim())
            .filter(Boolean)
          const locationIcon = card.querySelector('.byte-icon-location')
          const locationTag = locationIcon?.closest('.byte-tag')
          return {
            title: String(titleElement?.textContent || '').trim(),
            status: tags.find((value) => /定时发布中|待发布|已发布|发布成功|审核中|审核未通过|未通过|仅我可见|草稿/.test(value)) || '',
            location: String(locationTag?.textContent || '').replace(/\s+/g, ' ').trim(),
            publishedAt: String(card.querySelector('.create-time')?.textContent || '').trim(),
            text: String(card.textContent || '').replace(/\s+/g, ' ').trim(),
            coverImageUrl: card.querySelector('a.image img')?.src || '',
            links: Array.from(card.querySelectorAll('a[href]')).map((anchor) => ({
              text: String(anchor.textContent || '').trim(),
              href: anchor.href || '',
            })),
          }
        }),
    }
  })
  return evaluateToutiaoPublishSignals(target, pageState)
}

async function evaluateZhihuPublishResult(page, schedule) {
  const target = {
    title: schedule?.publishCheckTitle || '',
    platformScheduledAt: schedule?.platformScheduledAt || schedule?.plannedPublishAt || '',
  }
  return page.evaluate((input) => {
    const normalize = (value) => String(value || '').replace(/\s+/g, '').trim()
    const normalizeTitle = (value) => normalize(value).replace(/[「」『』【】\[\]（）()《》<>“”"‘’'`,，。！？!?、:：；;·.\-—_]/g, '')
    const isPublishedZhihuPath = (pathname) => /^\/p\/[^/]+/.test(pathname) || /^\/article\/[^/]+/.test(pathname)
    const text = document.body?.innerText || ''
    const normalizedText = normalizeTitle(text)
    const normalizedTitle = normalizeTitle(input.title)
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
        return normalizeTitle(item.textContent).includes(titleProbe) && /zhuanlan\.zhihu\.com\/(p|article)\//.test(href)
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
    const xiaohongshuCards = Array.from(document.querySelectorAll('.note-card')).map((card) => {
      let impression = null
      try {
        impression = JSON.parse(card.getAttribute('data-impression') || 'null')
      } catch (_) {
        impression = null
      }
      return {
        title: card.querySelector('.note-card__title')?.textContent || '',
        publishedAt: card.querySelector('.note-card__time')?.textContent || '',
        noteId: impression?.noteTarget?.value?.noteId || '',
        text: card.innerText || card.textContent || '',
      }
    })
    return {
      text,
      url: location.href,
      pageTitle: document.title,
      xiaohongshuCards,
      anchors: Array.from(document.querySelectorAll('a[href]'))
        .map((item) => ({ text: item.textContent || '', href: item.href || '' }))
        .slice(0, 80),
    }
  })
  const result = evaluateXiaohongshuPublishSignals(target, pageState)
  if (result.found && !result.platformPublishedUrl) {
    const detailUrl = await openXiaohongshuPublishedNoteDetail(page, schedule, result.platformPublishId).catch((error) => {
      result.detailOpenError = error instanceof Error ? error.message : String(error)
      return result.platformPublishId
        ? `https://www.xiaohongshu.com/explore/${encodeURIComponent(result.platformPublishId)}`
        : ''
    })
    if (detailUrl) {
      result.platformPublishedUrl = detailUrl
      result.url = detailUrl
    }
  }
  return result
}

async function openXiaohongshuPublishedNoteDetail(page, schedule, expectedNoteId = '') {
  const title = schedule?.publishCheckTitle || ''
  const browser = page.browser()
  const beforeTargets = new Set(browser.targets().map((target) => target._targetId || target.url()))
  const clickTarget = await page.evaluate((input) => {
    const normalize = (value) => String(value || '').replace(/\s+/g, '').trim()
    const normalizeTitle = (value) => normalize(value).replace(/[「」『』【】\[\]（）()《》<>“”"‘’'`,，。！？!?、:：；;·.\-—_]/g, '')
    const title = normalizeTitle(input.title)
    const titleProbe = title.length > 18 ? title.slice(0, 18) : title
    if (!titleProbe) return { clickReady: false, reason: 'missing title' }
    const cards = Array.from(document.querySelectorAll('.note-card'))
      .map((el) => {
        const rect = el.getBoundingClientRect()
        const text = normalizeTitle(el.innerText || el.textContent || '')
        let noteId = ''
        try {
          noteId = JSON.parse(el.getAttribute('data-impression') || 'null')?.noteTarget?.value?.noteId || ''
        } catch (_) {
          noteId = ''
        }
        const titleText = normalizeTitle(el.querySelector('.note-card__title')?.textContent || '')
        return { el, rect, text, titleText, noteId }
      })
      .filter((item) => (input.expectedNoteId && item.noteId === input.expectedNoteId)
        || item.titleText.includes(titleProbe)
        || titleProbe.includes(item.titleText)
        || item.text.includes(titleProbe))
      .filter((item) => item.titleText || item.noteId)
      .filter((item) => item.rect.width > 0 && item.rect.height > 0)
      .filter((item) => item.rect.width >= 180
        && item.rect.width <= 1200
        && item.rect.height <= 800)
      .sort((left, right) => Number(right.noteId === input.expectedNoteId) - Number(left.noteId === input.expectedNoteId))
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
    const target = card.querySelector('.note-card__cover, .note-card__title') || card
    target.scrollIntoView({ block: 'center', inline: 'center' })
    const rect = target.getBoundingClientRect()
    return {
      clickReady: true,
      reason: 'click point found',
      clientX: Math.round(rect.left + Math.min(Math.max(rect.width / 2, 12), Math.max(rect.width - 12, 12))),
      clientY: Math.round(rect.top + Math.min(Math.max(rect.height / 2, 12), Math.max(rect.height - 12, 12))),
      targetText: normalize(target.innerText || target.textContent || '').slice(0, 120),
      noteId: cards[0]?.noteId || '',
    }
  }, { title, expectedNoteId })
  if (!clickTarget?.clickReady) {
    throw new Error(`xiaohongshu note card click failed: ${clickTarget?.reason || 'unknown'}`)
  }
  if (clickTarget.href && /xiaohongshu\.com\/(explore|discovery\/item)\//.test(clickTarget.href)) {
    return clickTarget.href
  }
  if (!Number.isFinite(clickTarget.clientX) || !Number.isFinite(clickTarget.clientY)) {
    throw new Error(`xiaohongshu note card click point invalid: ${JSON.stringify(clickTarget).slice(0, 500)}`)
  }
  const noteId = clickTarget.noteId || expectedNoteId
  await page.mouse.click(clickTarget.clientX, clickTarget.clientY, { delay: 30 })
  const deadline = Date.now() + 12_000
  let detailPage = null
  while (Date.now() < deadline && !detailPage) {
    detailPage = await newestXiaohongshuExplorePage(browser, beforeTargets, noteId)
    if (!detailPage && /xiaohongshu\.com\/(explore|discovery\/item)\//.test(page.url())) detailPage = page
    if (!detailPage) await delay(250)
  }
  if (!detailPage) {
    if (noteId) return `https://www.xiaohongshu.com/explore/${encodeURIComponent(noteId)}`
    throw new Error('xiaohongshu note detail tab not found')
  }
  await detailPage.bringToFront().catch(() => {})
  await detailPage.waitForFunction(
    (input) => {
      const normalize = (value) => String(value || '').replace(/\s+/g, '').trim()
      const normalizeTitle = (value) => normalize(value).replace(/[「」『』【】\[\]（）()《》<>“”"‘’'`,，。！？!?、:：；;·.\-—_]/g, '')
      const title = normalizeTitle(input.title)
      const titleProbe = title.length > 18 ? title.slice(0, 18) : title
      const text = normalizeTitle(document.body?.innerText || '')
      return Boolean(/\/(explore|discovery\/item)\//.test(location.pathname) && (!titleProbe || text.includes(titleProbe)))
    },
    { timeout: 15_000 },
    { title },
  ).catch(() => null)
  const verification = await detailPage.evaluate((input) => {
    const normalize = (value) => String(value || '').replace(/\s+/g, '').trim()
    const normalizeTitle = (value) => normalize(value).replace(/[「」『』【】\[\]（）()《》<>“”"‘’'`,，。！？!?、:：；;·.\-—_]/g, '')
    const title = normalizeTitle(input.title)
    const titleProbe = title.length > 18 ? title.slice(0, 18) : title
    const text = normalizeTitle(document.body?.innerText || '')
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

async function newestXiaohongshuExplorePage(browser, beforeTargets, expectedNoteId = '') {
  const pages = await browser.pages()
  const candidates = []
  for (const item of pages) {
    const url = item.url()
    if (!/xiaohongshu\.com\/(explore|discovery\/item)\//.test(url)) continue
    if (expectedNoteId && !url.includes(expectedNoteId)) continue
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
    const articleCards = Array.from(document.querySelectorAll('[class*="articleItem"]'))
      .slice(0, 20)
      .map((card) => {
        const anchors = Array.from(card.querySelectorAll('a[href]'))
          .map((item) => ({
            text: item.textContent || '',
            href: item.href || '',
            className: item.className || '',
          }))
        const publicAnchor = anchors.find((item) => /baijiahao\.baidu\.com\/s\?id=/.test(item.href || '')) || null
        const titleLink = card.querySelector('[class*="articleTitle"] a[href], .title a[href]')
        const titleAnchor = anchors.find((item) => String(item.text || '').trim()) || null
        const titleText = String(titleLink?.textContent || titleAnchor?.text || publicAnchor?.text || '').trim()
        const statusNode = card.querySelector('[class*="articleTags"]')
        const timeNode = card.querySelector('[class*="time"]')
        return {
          text: card.innerText || card.textContent || '',
          title: titleText,
          status: String(statusNode?.innerText || statusNode?.textContent || '').trim(),
          publishedAt: String(timeNode?.innerText || timeNode?.textContent || '').trim(),
          publishedUrl: publicAnchor?.href || '',
          anchors,
        }
      })
    return {
      text,
      url: location.href,
      pageTitle: document.title,
      anchors: Array.from(document.querySelectorAll('a[href]'))
        .map((item) => ({ text: item.textContent || '', href: item.href || '' }))
        .slice(0, 80),
      baijiahaoCards: articleCards,
    }
  })
  return evaluateBaijiahaoPublishSignals(target, pageState)
}

async function evaluateDouyinPublishResult(page, schedule) {
  const target = {
    title: schedule?.publishCheckTitle || '',
    platformScheduledAt: schedule?.platformScheduledAt || schedule?.plannedPublishAt || '',
    contentKind: schedule?.contentKind || '',
    expectedImageCount: Number(schedule?.expectedImageCount || 0),
    taskStartedAt: schedule?.lastAttemptAt || schedule?.updatedAt || schedule?.snapshotCreatedAt || '',
  }
  const structuredPageState = await page.evaluate(() => {
    const titleNodes = Array.from(document.querySelectorAll('[class*="info-title-text-"]'))
    const contentNodes = Array.from(document.querySelectorAll('[class*="video-card-content-"]'))
    const findCardRoot = (node) => {
      let current = node
      let fallback = node?.parentElement || node
      for (let depth = 0; current && depth < 10; depth += 1, current = current.parentElement) {
        if (current.matches?.('[class*="video-card-new-"]')) return current
        if (current.querySelector?.('[class*="info-title-text-"]')) {
          fallback = current
          if (current.querySelector('[class*="info-time-"]')
            || current.querySelector('[class*="video-card-cover-"]')
            || /已发布|审核中|未通过/.test(current.innerText || '')) {
            return current
          }
        }
      }
      return fallback
    }
    const cardNodes = Array.from(new Set([...titleNodes, ...contentNodes].map(findCardRoot).filter(Boolean)))
    const backgroundImageUrl = (element) => {
      const value = element ? getComputedStyle(element).backgroundImage : ''
      return String(value || '').match(/^url\(["']?(.*?)["']?\)$/)?.[1] || ''
    }
    return {
      text: document.body?.innerText || '',
      url: location.href,
      pageTitle: document.title,
      douyinCards: cardNodes.map((card) => {
        const rect = card.getBoundingClientRect()
        const cover = card.querySelector('[class*="video-card-cover-"]')
        return {
          title: card.querySelector('[class*="info-title-text-"]')?.textContent || '',
          publishedAt: card.querySelector('[class*="info-time-"]')?.textContent || '',
          status: card.querySelector('[class*="info-status-"]')?.textContent || '',
          text: card.innerText || card.textContent || '',
          width: Math.round(rect.width * 10) / 10,
          height: Math.round(rect.height * 10) / 10,
          coverImageUrl: backgroundImageUrl(cover),
          links: Array.from(card.querySelectorAll('a[href]')).map((link) => ({
            text: link.textContent || '',
            href: link.href || '',
          })),
        }
      }),
    }
  })
  if (structuredPageState.douyinCards.length) {
    return evaluateDouyinPublishSignals(target, structuredPageState)
  }
  return page.evaluate((input) => {
    const normalize = (value) => String(value || '').replace(/\s+/g, '').trim()
    const normalizeTitle = (value) => normalize(value).replace(/[「」『』【】\[\]（）()《》<>“”"‘’'`,，。！？!?、:：；;·.\-—_]/g, '')
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
    const title = normalizeTitle(input.title)
    const titleProbe = title.length > 24 ? title.slice(0, 24) : title
    const expectedScheduleVariants = scheduleVariants(input.platformScheduledAt)
    const imageText = String(input.contentKind || '') === 'image_text'
    const expectedImageCount = Number(input.expectedImageCount || 0)
    const taskStartedAtMs = Date.parse(String(input.taskStartedAt || ''))
    const recordDateTimeMs = (text) => {
      const fullMatch = String(text || '').match(
        /(\d{4})\s*(?:年|[-/])\s*(\d{1,2})\s*(?:月|[-/])\s*(\d{1,2})\s*(?:日)?\s+(\d{1,2}):(\d{1,2})/,
      )
      const shortMatch = fullMatch ? null : String(text || '').match(
        /(?:^|\s)(\d{1,2})\s*(?:月|[-/])\s*(\d{1,2})\s*(?:日)?\s+(\d{1,2}):(\d{1,2})/,
      )
      if (!fullMatch && !shortMatch) return Number.NaN
      const reference = Number.isFinite(taskStartedAtMs) ? new Date(taskStartedAtMs) : new Date()
      const year = fullMatch ? Number(fullMatch[1]) : reference.getFullYear()
      const values = fullMatch || shortMatch
      const offset = fullMatch ? 1 : 0
      return new Date(year, Number(values[1 + offset]) - 1, Number(values[2 + offset]), Number(values[3 + offset]), Number(values[4 + offset])).getTime()
    }
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
        const titleText = normalizeTitle(text)
        const links = Array.from(el.querySelectorAll('a[href]')).map((link) => ({
          text: String(link.textContent || '').trim(),
          href: link.href || '',
        }))
        const images = Array.from(el.querySelectorAll('img[src]')).map((img) => img.src || '').filter(Boolean)
        return { el, rect, text, compactText, titleText, links, images }
      })
      .filter((item) => item.text
        && item.rect.width >= 260
        && item.rect.height >= 60
        && item.rect.height <= 460)
      .filter((item) => titleProbe && item.titleText.includes(titleProbe))
      .filter((item) => !imageText || expectedImageCount <= 0
        || new RegExp(`${expectedImageCount}\\s*张`).test(item.text))
      .filter((item) => {
        if (!imageText || !Number.isFinite(taskStartedAtMs)) return true
        const recordAtMs = recordDateTimeMs(item.text)
        return Number.isFinite(recordAtMs)
          && recordAtMs >= taskStartedAtMs - 15 * 60 * 1000
          && recordAtMs <= Date.now() + 10 * 60 * 1000
      })
      .filter((item) => {
        if (imageText) return true
        if (!expectedScheduleVariants.length) return true
        return expectedScheduleVariants.some((value) => value && item.compactText.includes(normalizeCompact(value)))
          || /已发布|审核中|发布成功/.test(item.text)
      })
      .map((item) => {
        let score = 0
        if (titleProbe && item.titleText.includes(titleProbe)) score += 1000
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
    const topCandidates = records.slice(0, 5).map((item) => ({
      text: item.text.slice(0, 180),
      titleMatched: Boolean(titleProbe && item.titleText.includes(titleProbe)),
      status: (item.text.match(/(定时发布中|已发布|审核中|发布成功|未通过|草稿)/) || [])[1] || '',
      score: item.score,
    }))
    const record = records[0]
    if (!record) {
      const text = document.body?.innerText || ''
      const fallbackCandidates = Array.from(document.querySelectorAll('[class*="video-card"], section, article, li'))
        .filter(isVisible)
        .map((el) => String(el.innerText || el.textContent || '').replace(/\s+/g, ' ').trim())
        .filter(Boolean)
        .slice(0, 5)
      return {
        found: false,
        hasTitle: Boolean(titleProbe && normalizeTitle(text).includes(titleProbe)),
        hasPublishedSignal: /定时发布中|已发布|审核中|发布成功/.test(text),
        candidateCount: 0,
        cardCandidateCount: fallbackCandidates.length,
        topCandidates: fallbackCandidates.map((item) => ({ text: item.slice(0, 180), titleMatched: false })),
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
      if (/未通过/.test(statusText)) return 'rejected'
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
      failed: pageStatusCode === 'rejected',
      failureCode: pageStatusCode === 'rejected' ? 'DOUYIN_REVIEW_REJECTED' : undefined,
      failureMessage: pageStatusCode === 'rejected' ? '抖音作品未通过审核' : undefined,
      pendingScheduled: pageStatusCode === 'scheduled',
      reason: pageStatusCode === 'scheduled' ? 'platform schedule time not due' : '',
      hasTitle: true,
      hasPublishedSignal: Boolean(pageStatusCode),
      candidateCount: records.length,
      cardCandidateCount: records.length,
      topCandidates,
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

async function reportPublishCheckPublished(config, scheduleId, claimAttempt, result) {
  const publishedUrl = publishedUrlFromPublishCheckResult(result)
  const body = JSON.stringify({
    claimAttempt,
    platformPublishedUrl: publishedUrl || undefined,
    diagnosticsJson: publishCheckReportDiagnosticsJson(result),
  })
  const path = `/api/v1/local-agent/self-media-schedules/${encodeURIComponent(scheduleId)}/publish-checks/published`
  return signedTrustedBackendRequest(config, path, { method: 'POST', body, signatureBodyText: '' })
}

async function reportPublishCheckUnknown(config, scheduleId, claimAttempt, result) {
  const body = JSON.stringify({
    claimAttempt,
    diagnosticsJson: publishCheckReportDiagnosticsJson(result),
  })
  const path = `/api/v1/local-agent/self-media-schedules/${encodeURIComponent(scheduleId)}/publish-checks/unknown`
  return signedTrustedBackendRequest(config, path, { method: 'POST', body, signatureBodyText: '' })
}

async function reportPublishCheckFailed(config, scheduleId, claimAttempt, result) {
  const body = JSON.stringify({
    claimAttempt,
    failureCode: result?.failureCode || 'PUBLISH_RESULT_CHECK_HELPER_FAILED',
    failureMessage: String(result?.failureMessage || 'publish result check failed').slice(0, 480),
    diagnosticsJson: publishCheckReportDiagnosticsJson(result),
  })
  const path = `/api/v1/local-agent/self-media-schedules/${encodeURIComponent(scheduleId)}/publish-checks/failed`
  return signedTrustedBackendRequest(config, path, { method: 'POST', body, signatureBodyText: '' })
}

async function reportScheduleExecutionFailed(config, task, result) {
  const scheduleId = scheduleIdOfTask(task)
  if (!scheduleId) return null
  const failureCode = result?.failureCode || task?.lastError?.code || task?.failureCode || 'FILL_FAILED'
  const failureMessage = String(result?.failureMessage || task?.lastError?.message || 'schedule execution failed').slice(0, 480)
  const body = JSON.stringify({
    claimAttempt: claimAttemptOfTask(task),
    failureCode,
    failureMessage,
    diagnosticsJson: shortDiagnosticsJson({
      ...result,
      taskId: task?.taskId,
      platform: task?.platform,
      lastStage: task?.lastStage || null,
      lastStageAt: task?.lastStageAt || null,
      error: task?.lastError || null,
    }),
  })
  const path = `/api/v1/local-agent/self-media-schedules/${encodeURIComponent(scheduleId)}/executions/failed`
  try {
    return await signedTrustedBackendRequest(config, path, { method: 'POST', body, signatureBodyText: '' })
  } catch (error) {
    if (Number(error?.statusCode || 0) < 500) throw error
    const fallbackBody = JSON.stringify({
      claimAttempt: claimAttemptOfTask(task),
      failureCode: String(failureCode).slice(0, 64),
      failureMessage: failureMessage.slice(0, 240),
      diagnosticsJson: JSON.stringify({
        fallbackReport: true,
        taskId: task?.taskId || null,
        platform: task?.platform || null,
        lastStage: task?.lastStage || null,
        lastStageAt: task?.lastStageAt || null,
      }),
    })
    try {
      return await signedTrustedBackendRequest(config, path, {
        method: 'POST',
        body: fallbackBody,
        signatureBodyText: '',
      })
    } catch (fallbackError) {
      fallbackError.details = {
        ...(fallbackError.details || {}),
        initialReportError: formatBackendError(error),
        fallbackReport: true,
      }
      throw fallbackError
    }
  }
}

async function reportScheduleExecutionSuccess(config, task, fillResult) {
  const scheduleId = scheduleIdOfTask(task)
  if (!scheduleId) return null
  const outcome = resolveScheduleExecutionOutcome(fillResult)
  const publishedUrl = extractPublishedUrl(fillResult)
  const body = JSON.stringify({
    claimAttempt: claimAttemptOfTask(task),
    platformPublishedUrl: outcome === 'published' && publishedUrl ? publishedUrl : undefined,
    diagnosticsJson: shortDiagnosticsJson({
      fillResult,
      taskId: task?.taskId,
      platform: task?.platform,
      scheduleId,
    }),
  })
  const path = `/api/v1/local-agent/self-media-schedules/${encodeURIComponent(scheduleId)}/executions/${outcome}`
  return signedTrustedBackendRequest(config, path, { method: 'POST', body, signatureBodyText: '' })
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

async function reportScheduleHeartbeat(config, task) {
  const scheduleId = scheduleIdOfTask(task)
  if (!scheduleId) return null
  const claimAttempt = claimAttemptOfTask(task)
  const path = `/api/v1/local-agent/self-media-schedules/${encodeURIComponent(scheduleId)}/heartbeat?claimAttempt=${encodeURIComponent(claimAttempt || '')}`
  return signedTrustedBackendRequest(config, path, { method: 'POST' })
}

function scheduleIdOfTask(task) {
  return task?.schedule?.id || task?.backendTask?.platformOptions?.scheduleId || task?.backendTask?.scheduleId
}

function claimAttemptOfTask(task) {
  const value = task?.schedule?.attemptCount
    ?? task?.backendTask?.platformOptions?.claimAttempt
    ?? task?.backendTask?.claimAttempt
  const claimAttempt = Number(value)
  return Number.isInteger(claimAttempt) && claimAttempt > 0 ? claimAttempt : null
}

function claimedTimeoutMsForTask(task) {
  return scheduleIdOfTask(task) ? SCHEDULE_PROGRESS_STALL_TIMEOUT_MS : CLAIM_TIMEOUT_MS
}

function taskProgressAt(task) {
  return task?.lastProgressAt || task?.lastStageAt || task?.claimedAt || task?.createdAt || null
}

function taskProgressAgeMs(task) {
  const timestamp = Date.parse(taskProgressAt(task) || '')
  return Number.isFinite(timestamp) ? Date.now() - timestamp : Number.POSITIVE_INFINITY
}

function expireClaimedTask(task) {
  if (scheduleIdOfTask(task)) {
    task.status = 'failed'
    task.failedAt = nowIso()
    task.failureCode = 'LOCAL_HELPER_CLAIM_TIMEOUT'
    task.lastError = {
      code: 'LOCAL_HELPER_CLAIM_TIMEOUT',
      message: task.taskKind === 'publish_result_check'
        ? '本地助手发布结果回查超时，已释放后端排期锁'
        : '本地助手等待扩展回写超时，已释放后端排期锁',
    }
  } else {
    task.status = 'requeued'
    task.requeuedAt = nowIso()
    task.lastError = { message: 'claimed task timed out and was requeued' }
  }
  task.claimedAt = null
  task.claimOwner = null
}

async function expireTimedOutPendingScheduleTasks(config) {
  let changed = false
  for (const task of tasksById.values()) {
    if (task.status !== 'pending' || !scheduleIdOfTask(task)) continue
    const pendingSince = Date.parse(task.createdAt || '')
    if (!Number.isFinite(pendingSince) || Date.now() - pendingSince <= EXTENSION_CLAIM_TIMEOUT_MS) continue
    task.status = 'failed'
    task.failedAt = nowIso()
    task.failureCode = 'EXTENSION_CLAIM_TIMEOUT'
    task.lastError = {
      code: 'EXTENSION_CLAIM_TIMEOUT',
      message: '浏览器扩展未在 90 秒内领取任务，已释放后端排期锁并重新排队',
    }
    task.claimedAt = null
    task.claimOwner = null
    task.backendFailureReportedAt = null
    task.backendFailureReportAttempts = Number(task.backendFailureReportAttempts || 0)
    try {
      await reportScheduleExecutionFailed(config, task, {
        failureCode: task.failureCode,
        failureMessage: task.lastError.message,
      })
      task.backendFailureReportedAt = nowIso()
      task.backendFailureReportLastError = null
    } catch (error) {
      task.backendFailureReportAttempts += 1
      task.backendFailureReportLastError = formatBackendError(error)
    }
    changed = true
  }
  if (changed) await saveRuntimeTasks()
  return changed
}

function shouldHeartbeatScheduleTask(task) {
  if (!task || isTerminalStatus(task.status)) return false
  if (!scheduleIdOfTask(task)) return false
  if (!task.backendTask && !task.schedule) return false
  if (task.status !== 'pending' && task.status !== 'claimed') return false
  const maxAgeMs = task.status === 'pending' ? EXTENSION_CLAIM_TIMEOUT_MS : SCHEDULE_PROGRESS_STALL_TIMEOUT_MS
  return taskProgressAgeMs(task) <= maxAgeMs
}

function activeScheduleHeartbeatTasks() {
  return listTasks()
    .filter((task) => shouldHeartbeatScheduleTask(task))
    .map((task) => ({
      taskId: task.taskId,
      scheduleId: scheduleIdOfTask(task),
      platform: task.platform || null,
      environmentKey: task.environmentKey || null,
      status: task.status || null,
      createdAt: task.createdAt || null,
      claimedAt: task.claimedAt || null,
      backendHeartbeatAt: task.backendHeartbeatAt || null,
      backendHeartbeatLastError: task.backendHeartbeatLastError || null,
    }))
}

function backendReportSummary() {
  const summary = {
    pendingSuccess: 0,
    pendingUnknown: 0,
    pendingFailure: 0,
    publishCheckPendingSuccess: 0,
    publishCheckPendingUnknown: 0,
    publishCheckPendingFailure: 0,
    schedulePendingSuccess: 0,
    schedulePendingFailure: 0,
    lastError: null,
    pendingTasks: [],
  }
  for (const task of tasksById.values()) {
    const isPublishCheck = task.taskKind === 'publish_result_check'
    const scheduleId = scheduleIdOfTask(task)
    if (task.taskKind === 'publish_result_check'
        && task.status === 'completed'
        && task.lastResult?.found !== true
        && !task.backendUnknownReportedAt
        && !task.backendUnknownReportRejectedAt) {
      summary.publishCheckPendingUnknown += 1
      summary.pendingUnknown += 1
      appendPendingBackendReportTask(summary, task, scheduleId, 'unknown')
    }
    if (task.status === 'completed'
        && (!task.taskKind || task.taskKind !== 'publish_result_check' || task.lastResult?.found === true)
        && !task.backendSuccessReportedAt
        && !task.backendSuccessReportRejectedAt) {
      summary.pendingSuccess += 1
      if (isPublishCheck) summary.publishCheckPendingSuccess += 1
      else summary.schedulePendingSuccess += 1
      appendPendingBackendReportTask(summary, task, scheduleId, 'success')
    }
    if (task.status === 'failed' && !task.backendFailureReportedAt && !task.backendFailureReportRejectedAt) {
      summary.pendingFailure += 1
      if (isPublishCheck) summary.publishCheckPendingFailure += 1
      else summary.schedulePendingFailure += 1
      appendPendingBackendReportTask(summary, task, scheduleId, 'failure')
    }
    const lastError = task.backendSuccessReportLastError || task.backendUnknownReportLastError || task.backendFailureReportLastError
    if (lastError) {
      summary.lastError = {
        taskId: task.taskId,
        scheduleId,
        platform: task.platform || null,
        taskKind: task.taskKind || null,
        message: String(lastError).slice(0, 500),
      }
    }
  }
  return summary
}

async function flushPendingBackendReports(config, platform = '') {
  const before = backendReportSummary()
  await flushPendingScheduleFailureReports(config, platform)
  await flushPendingScheduleSuccessReports(config, platform)
  await flushPendingPublishCheckFailureReports(config, platform)
  await flushPendingPublishCheckUnknownReports(config, platform)
  await flushPendingPublishCheckSuccessReports(config, platform)
  const after = backendReportSummary()
  return {
    ok: true,
    platform: platform || '',
    before,
    after,
    flushed: {
      success: Math.max(0, Number(before.pendingSuccess || 0) - Number(after.pendingSuccess || 0)),
      unknown: Math.max(0, Number(before.pendingUnknown || 0) - Number(after.pendingUnknown || 0)),
      failure: Math.max(0, Number(before.pendingFailure || 0) - Number(after.pendingFailure || 0)),
    },
  }
}

function runtimeTaskStorageSummary() {
  let active = 0
  let awaitingExtension = 0
  let running = 0
  let terminal = 0
  let pendingBackendReport = 0
  let oldestAwaitingExtensionAt = null
  let oldestTerminalAt = null
  let newestTaskAt = null
  for (const task of tasksById.values()) {
    if (isTerminalStatus(task.status)) {
      terminal += 1
      if (hasPendingBackendReport(task)) pendingBackendReport += 1
      const terminalAt = terminalTimeMs(task)
      if (terminalAt > 0 && (oldestTerminalAt === null || terminalAt < oldestTerminalAt)) {
        oldestTerminalAt = terminalAt
      }
    } else {
      active += 1
      if (task.status === 'pending') {
        awaitingExtension += 1
        const pendingAt = Date.parse(task.createdAt || '')
        if (Number.isFinite(pendingAt)
          && (oldestAwaitingExtensionAt === null || pendingAt < oldestAwaitingExtensionAt)) {
          oldestAwaitingExtensionAt = pendingAt
        }
      }
      if (task.status === 'claimed') running += 1
    }
    const createdAt = Date.parse(task.createdAt || '')
    if (Number.isFinite(createdAt) && (newestTaskAt === null || createdAt > newestTaskAt)) {
      newestTaskAt = createdAt
    }
  }
  return {
    total: tasksById.size,
    active,
    awaitingExtension,
    running,
    terminal,
    pendingBackendReport,
    maxRecords: RUNTIME_TASK_MAX_RECORDS,
    terminalTtlHours: Math.round(RUNTIME_TASK_TERMINAL_TTL_MS / 3_600_000),
    remainingCapacity: Math.max(0, RUNTIME_TASK_MAX_RECORDS - tasksById.size),
    nearLimit: tasksById.size >= Math.floor(RUNTIME_TASK_MAX_RECORDS * 0.9),
    oldestTerminalAt: oldestTerminalAt ? new Date(oldestTerminalAt).toISOString() : null,
    oldestAwaitingExtensionAt: oldestAwaitingExtensionAt
      ? new Date(oldestAwaitingExtensionAt).toISOString()
      : null,
    oldestAwaitingExtensionAgeSeconds: oldestAwaitingExtensionAt
      ? Math.max(0, Math.floor((Date.now() - oldestAwaitingExtensionAt) / 1000))
      : 0,
    newestTaskAt: newestTaskAt ? new Date(newestTaskAt).toISOString() : null,
  }
}

function appendPendingBackendReportTask(summary, task, scheduleId, reportKind) {
  if (!summary || summary.pendingTasks.length >= 20) return
  summary.pendingTasks.push({
    taskId: task.taskId,
    scheduleId,
    platform: task.platform || null,
    taskKind: task.taskKind || null,
    reportKind,
    status: task.status || null,
    lastStage: task.lastStage || null,
    attempts: reportKind === 'success'
      ? Number(task.backendSuccessReportAttempts || 0)
      : reportKind === 'unknown'
        ? Number(task.backendUnknownReportAttempts || 0)
      : Number(task.backendFailureReportAttempts || 0),
    lastError: String(reportKind === 'success'
      ? task.backendSuccessReportLastError || ''
      : reportKind === 'unknown'
        ? task.backendUnknownReportLastError || ''
      : task.backendFailureReportLastError || '').slice(0, 300) || null,
  })
}

async function heartbeatActiveScheduleTasks(config) {
  await expireTimedOutPendingScheduleTasks(config)
  let sent = 0
  let failed = 0
  let changed = false
  const staleClaimKeys = new Set()
  for (const task of tasksById.values()) {
    if (task.status !== 'claimed' || !task.claimedAt) continue
    if (taskProgressAgeMs(task) <= SCHEDULE_PROGRESS_STALL_TIMEOUT_MS) continue
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
      const schedule = await reportScheduleHeartbeat(config, task)
      task.schedule = schedule || task.schedule || null
      task.backendHeartbeatAt = nowIso()
      task.backendHeartbeatLastError = null
      sent += 1
      changed = true
    } catch (error) {
      task.backendHeartbeatLastError = formatBackendError(error)
      failed += 1
      changed = true
      if (!terminateTaskForScheduleClaimError(task, error)) {
        console.error('Failed to heartbeat self-media schedule:', task.backendHeartbeatLastError)
      }
    }
  }
  if (changed) await saveRuntimeTasks()
  return { sent, failed }
}

function shouldFlushScheduleFailure(task, platform) {
  if (!task || task.status !== 'failed') return false
  if (platform && task.platform !== platform) return false
  if (!scheduleIdOfTask(task)) return false
  if (task.taskKind === 'publish_result_check') return false
  if (task.backendFailureReportedAt) return false
  if (task.backendFailureReportRejectedAt) return false
  return Number(task.backendFailureReportAttempts || 0) < FAILED_SCHEDULE_REPORT_MAX_ATTEMPTS
}

function shouldFlushPublishCheckFailure(task, platform) {
  if (!task || task.status !== 'failed') return false
  if (task.taskKind !== 'publish_result_check') return false
  if (platform && task.platform !== platform) return false
  if (!scheduleIdOfTask(task)) return false
  if (task.backendFailureReportedAt) return false
  if (task.backendFailureReportRejectedAt) return false
  return Number(task.backendFailureReportAttempts || 0) < FAILED_SCHEDULE_REPORT_MAX_ATTEMPTS
}

function shouldFlushPublishCheckSuccess(task, platform) {
  if (!task || task.status !== 'completed') return false
  if (task.taskKind !== 'publish_result_check') return false
  if (task.lastResult?.found !== true) return false
  if (platform && task.platform !== platform) return false
  if (!scheduleIdOfTask(task)) return false
  if (task.backendSuccessReportedAt) return false
  if (task.backendSuccessReportRejectedAt) return false
  return Number(task.backendSuccessReportAttempts || 0) < FAILED_SCHEDULE_REPORT_MAX_ATTEMPTS
}

function shouldFlushPublishCheckUnknown(task, platform) {
  if (!task || task.status !== 'completed') return false
  if (task.taskKind !== 'publish_result_check') return false
  if (task.lastResult?.found === true) return false
  if (platform && task.platform !== platform) return false
  if (!scheduleIdOfTask(task)) return false
  if (task.backendUnknownReportedAt) return false
  if (task.backendUnknownReportRejectedAt) return false
  return Number(task.backendUnknownReportAttempts || 0) < FAILED_SCHEDULE_REPORT_MAX_ATTEMPTS
}

async function flushPendingPublishCheckSuccessReports(config, platform = '') {
  let changed = false
  for (const task of tasksById.values()) {
    if (!shouldFlushPublishCheckSuccess(task, platform)) continue
    task.backendSuccessReportAttempts = Number(task.backendSuccessReportAttempts || 0) + 1
    try {
      task.schedule = await withTimeout(
        reportPublishCheckPublished(config, scheduleIdOfTask(task), claimAttemptOfTask(task), task.lastResult),
        BACKEND_FETCH_TIMEOUT_MS + RESPONSE_JSON_TIMEOUT_MS + 5_000,
        `flush publish check published ${scheduleIdOfTask(task)}`,
      ) || task.schedule || null
      task.backendSuccessReportedAt = nowIso()
      task.backendSuccessReportLastError = null
    } catch (error) {
      task.backendSuccessReportLastError = formatBackendError(error)
      const terminated = terminateTaskForScheduleClaimError(task, error)
      if (!terminated && isNonRetryableBackendReportError(error)) {
        task.backendSuccessReportRejectedAt = nowIso()
      }
      if (!terminated) console.error('Failed to report pending publish check success:', task.backendSuccessReportLastError)
    }
    changed = true
  }
  if (changed) await saveRuntimeTasks()
}

async function flushPendingPublishCheckUnknownReports(config, platform = '') {
  let changed = false
  for (const task of tasksById.values()) {
    if (!shouldFlushPublishCheckUnknown(task, platform)) continue
    task.backendUnknownReportAttempts = Number(task.backendUnknownReportAttempts || 0) + 1
    try {
      task.schedule = await withTimeout(
        reportPublishCheckUnknown(config, scheduleIdOfTask(task), claimAttemptOfTask(task), task.lastResult),
        BACKEND_FETCH_TIMEOUT_MS + RESPONSE_JSON_TIMEOUT_MS + 5_000,
        `flush publish check unknown ${scheduleIdOfTask(task)}`,
      ) || task.schedule || null
      task.backendUnknownReportedAt = nowIso()
      task.backendUnknownReportLastError = null
    } catch (error) {
      task.backendUnknownReportLastError = formatBackendError(error)
      const terminated = terminateTaskForScheduleClaimError(task, error)
      if (!terminated && isNonRetryableBackendReportError(error)) {
        task.backendUnknownReportRejectedAt = nowIso()
      }
      if (!terminated) console.error('Failed to report pending publish check unknown:', task.backendUnknownReportLastError)
    }
    changed = true
  }
  if (changed) await saveRuntimeTasks()
}

async function flushPendingPublishCheckFailureReports(config, platform = '') {
  let changed = false
  for (const task of tasksById.values()) {
    if (!shouldFlushPublishCheckFailure(task, platform)) continue
    task.backendFailureReportAttempts = Number(task.backendFailureReportAttempts || 0) + 1
    try {
      const scheduleId = scheduleIdOfTask(task)
      const result = {
        found: false,
        reason: task.lastError?.code || task.failureCode || 'LOCAL_HELPER_PUBLISH_CHECK_FAILED',
        failureCode: task.lastError?.code || task.failureCode || 'LOCAL_HELPER_PUBLISH_CHECK_FAILED',
        failureMessage: task.lastError?.message || String(task.lastError || '发布结果回查失败'),
        taskId: task.taskId,
        platform: task.platform,
        targetTitle: task.schedule?.publishCheckTitle || '',
        platformScheduledAt: task.schedule?.platformScheduledAt || '',
        url: task.url || '',
        backendHeartbeatAt: task.backendHeartbeatAt || '',
        backendHeartbeatLastError: task.backendHeartbeatLastError || null,
      }
      task.schedule = await reportPublishCheckUnknown(
        config, scheduleId, claimAttemptOfTask(task), result,
      ) || task.schedule || null
      task.backendFailureReportedAt = nowIso()
      task.backendFailureReportLastError = null
    } catch (error) {
      task.backendFailureReportLastError = formatBackendError(error)
      const terminated = terminateTaskForScheduleClaimError(task, error)
      if (!terminated && isNonRetryableBackendReportError(error)) {
        task.backendFailureReportRejectedAt = nowIso()
      }
      if (!terminated) console.error('Failed to report pending publish check failure:', task.backendFailureReportLastError)
    }
    changed = true
  }
  if (changed) await saveRuntimeTasks()
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
      const terminated = terminateTaskForScheduleClaimError(task, error)
      if (!terminated && isNonRetryableBackendReportError(error)) {
        task.backendFailureReportRejectedAt = nowIso()
      }
      if (!terminated) console.error('Failed to report pending schedule execution failure:', task.backendFailureReportLastError)
    }
    changed = true
  }
  if (changed) await saveRuntimeTasks()
}

function shouldFlushScheduleSuccess(task, platform) {
  if (!task || task.status !== 'completed') return false
  if (task.taskKind === 'publish_result_check') return false
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
      const terminated = terminateTaskForScheduleClaimError(task, error)
      if (!terminated && isNonRetryableBackendReportError(error)) {
        task.backendSuccessReportRejectedAt = nowIso()
      }
      if (!terminated) console.error('Failed to report pending schedule execution success:', task.backendSuccessReportLastError)
    }
    changed = true
  }
  if (changed) await saveRuntimeTasks()
}

function formatBackendError(error) {
  const details = error?.details ? `; details=${JSON.stringify(error.details).slice(0, 600)}` : ''
  const status = error?.statusCode ? `; status=${error.statusCode}` : ''
  const backendCode = error?.backendCode ? `; backendCode=${error.backendCode}` : ''
  return `${error?.message || error}${status}${backendCode}${details}`
}

function isNonRetryableBackendReportError(error) {
  if (error?.statusCode === 400) return true
  return error?.backendCode === 'SCHEDULE_STATUS_NOT_CHECKING_PUBLISH_RESULT'
    || isTerminalScheduleClaimError(error)
}

function isTerminalScheduleClaimError(error) {
  return TERMINAL_SCHEDULE_CLAIM_ERROR_CODES.has(String(error?.backendCode || ''))
}

function terminateTaskForScheduleClaimError(task, error) {
  if (!task || !isTerminalScheduleClaimError(error)) return false
  const firstTermination = !task.scheduleClaimTerminatedAt
  const terminatedAt = task.scheduleClaimTerminatedAt || nowIso()
  task.scheduleClaimTerminatedAt = terminatedAt
  task.scheduleClaimTerminationCode = error.backendCode || 'SCHEDULE_CLAIM_TERMINATED'
  task.backendFailureReportRejectedAt = task.backendFailureReportRejectedAt || terminatedAt
  task.backendSuccessReportRejectedAt = task.backendSuccessReportRejectedAt || terminatedAt
  task.backendUnknownReportRejectedAt = task.backendUnknownReportRejectedAt || terminatedAt
  if (task.status === 'pending' || task.status === 'claimed') {
    task.status = 'failed'
    task.failedAt = terminatedAt
    task.claimedAt = null
    task.claimOwner = null
  }
  task.lastError = {
    code: task.scheduleClaimTerminationCode,
    message: formatBackendError(error),
  }
  if (firstTermination) {
    console.error('Self-media schedule claim terminated:', task.lastError.message)
  }
  return true
}

function shortDiagnosticsJson(value) {
  const normalized = {
    ...value,
    textSample: typeof value?.textSample === 'string' ? value.textSample.slice(0, 800) : value?.textSample,
    checkedAt: nowIso(),
  }
  return stringifyBoundedDiagnostics(normalized, 6000)
}

function publishedUrlFromPublishCheckResult(result) {
  return firstText(
    result?.platformPublishedUrl,
    result?.matchedCard?.publishedUrl,
    result?.matchedCard?.publicUrl,
  )
}

function publishCheckReportDiagnosticsJson(result) {
  const diagnostics = {
    found: result?.found === true,
    failed: result?.failed === true,
    reason: result?.reason || '',
    failureCode: result?.failureCode || '',
    failureMessage: result?.failureMessage || '',
    platformStatus: result?.platformStatus || '',
    matchStrategy: result?.matchStrategy || '',
    checkStages: result?.checkStages || undefined,
    evidence: result?.evidence ? trimPublishCheckEvidence(result.evidence) : undefined,
    targetTitle: result?.targetTitle || '',
    platformScheduledAt: result?.platformScheduledAt || '',
    platformPublishedUrl: publishedUrlFromPublishCheckResult(result),
    platformPublishId: result?.platformPublishId || '',
    candidateCount: result?.candidateCount,
    cardCandidateCount: result?.cardCandidateCount,
    matchedCard: result?.matchedCard ? {
      title: result.matchedCard.title || '',
      status: result.matchedCard.status || '',
      publishedAt: result.matchedCard.publishedAt || '',
      publishedUrl: publishedUrlFromPublishCheckResult(result),
      titleMatched: result.matchedCard.titleMatched === true,
    } : undefined,
    url: result?.url || '',
    checkedAt: nowIso(),
  }
  return stringifyBoundedDiagnostics(diagnostics, 2500)
}

function trimPublishCheckEvidence(evidence = {}) {
  return {
    listLoaded: evidence.listLoaded === true,
    listItemCount: evidence.listItemCount,
    bestTitleScore: evidence.bestTitleScore,
    matchedTitle: typeof evidence.matchedTitle === 'string' ? evidence.matchedTitle.slice(0, 160) : evidence.matchedTitle,
    matchedStatus: evidence.matchedStatus,
    matchedPublishedAt: evidence.matchedPublishedAt,
    matchedPublishedUrl: evidence.matchedPublishedUrl,
    topCardCandidates: Array.isArray(evidence.topCardCandidates)
      ? evidence.topCardCandidates.slice(0, 3).map((item) => ({
        title: String(item.title || '').slice(0, 120),
        status: item.status || '',
        publishedAt: item.publishedAt || '',
        publishedUrl: item.publishedUrl || '',
        titleScore: item.titleScore,
        titleMatched: item.titleMatched === true,
      }))
      : undefined,
    topAnchorCandidates: Array.isArray(evidence.topAnchorCandidates)
      ? evidence.topAnchorCandidates.slice(0, 3).map((item) => ({
        text: String(item.text || '').slice(0, 120),
        href: item.href || '',
        titleScore: item.titleScore,
        titleMatched: item.titleMatched === true,
        isPublicUrl: item.isPublicUrl === true,
      }))
      : undefined,
  }
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
  if (normalized === 'douyin') return 'https://creator.douyin.com/creator-micro/content/upload?default-tab=3'
  if (normalized === 'zhihu') return 'https://zhuanlan.zhihu.com/write'
  if (normalized === 'xiaohongshu') return 'https://creator.xiaohongshu.com/publish/publish?from=tab_switch&target=article'
  if (normalized === 'baijiahao') return 'https://baijiahao.baidu.com/builder/rc/edit?type=news&is_from_cms=1'
  return null
}

function defaultWorksListUrlForPlatform(platform) {
  const normalized = String(platform || '').trim().toLowerCase()
  if (normalized === 'toutiao') return 'https://mp.toutiao.com/profile_v4/graphic/articles'
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
  const data = await startAdspowerBrowser(config, environment.providerProfileId)
  const observationContext = {
    browserEnvironmentId: body.browserEnvironmentId,
    environmentKey: environment.environmentKey,
    providerProfileId: environment.providerProfileId,
    platform: body.platform,
    ownerType: 'operator',
    lastTaskActivityAt: null,
  }
  scheduleBrowserObservation(config, observationContext, data)
  const openResult = await openUrlWithPuppeteer(config, data?.ws?.puppeteer, body.url, {
    ...observationContext,
    ownership: 'operator',
    resourceOrigin: 'operator_open',
    resourceType: 'operator_tab',
    backendReportState: 'not_applicable',
  })
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
  let data = await startAdspowerBrowser(config, environment.providerProfileId)
  const observationContext = {
    browserEnvironmentId: body.browserEnvironmentId,
    environmentKey: environment.environmentKey,
    providerProfileId: environment.providerProfileId,
    platform: body.platform,
    ownerType: 'operator',
    lastTaskActivityAt: null,
  }
  scheduleBrowserObservation(config, observationContext, data)
  let extensionStatus
  try {
    extensionStatus = await inspectGeoEnvExtension(data?.ws?.puppeteer)
  } catch (error) {
    if (!isStaleAdspowerBrowserSessionError(error)) throw error

    // AdsPower assigns a dynamic DevTools port whenever a browser session starts.
    // Refresh its start response once instead of reconnecting to a cached, closed port.
    data = await startAdspowerBrowser(config, environment.providerProfileId, { forceRefresh: true })
    rememberObservedBrowserEnvironment(observationContext, data)
    scheduleBrowserObservation(config, observationContext, data)
    try {
      extensionStatus = await inspectGeoEnvExtension(data?.ws?.puppeteer)
    } catch (retryError) {
      retryError.statusCode ||= 502
      retryError.details ||= { reason: 'adspower_browser_session_unavailable' }
      throw retryError
    }
  }
  sendJson(req, res, config, 200, {
    ok: true,
    environmentKey: environment.environmentKey,
    environmentName: environment.name || environment.environmentKey,
    providerProfileId: environment.providerProfileId,
    extensionStatus,
  })
}

async function handleManagedBrowserResources(req, res, config) {
  await requireHelperAccess(req, config)
  if (browserObservationEnabled(config)) {
    await refreshBrowserResourceObservations(config)
  }
  sendJson(req, res, config, 200, {
    ok: true,
    observationOnly: true,
    executionCapabilities: {
      tabCleanup: false,
      environmentStop: false,
    },
    metrics: browserResourceMetrics(),
    registry: browserResourceRegistry.snapshot(),
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
  const data = await startAdspowerBrowser(config, environment.providerProfileId)
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
  const observationContext = {
    browserEnvironmentId: createdTask.browserEnvironmentId || body.browserEnvironmentId,
    environmentKey: environment.environmentKey,
    providerProfileId: environment.providerProfileId,
    platform: task.platform,
    ownerType: 'unknown',
    lastTaskActivityAt: task.createdAt,
  }
  scheduleBrowserObservation(config, observationContext, data)
  upsertTask(task)
  await saveRuntimeTasks()
  task.openResult = await openUrlWithPuppeteer(config, data?.ws?.puppeteer, body.url, {
    ...observationContext,
    taskId,
    scheduleId: createdTask.scheduleId || createdTask.platformOptions?.scheduleId,
    ownership: 'automation',
    resourceOrigin: 'schedule_execution',
    resourceType: 'editor_tab',
    backendReportState: 'pending',
  })
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
  if (!environmentKey) {
    const error = new Error('environmentKey is required for extension task claims')
    error.statusCode = 400
    error.code = 'ENVIRONMENT_KEY_REQUIRED'
    throw error
  }
  await requeueTimedOutClaims(environmentKey)
  const task = findNextClaimableTask(environmentKey, platform)
  if (!task) {
    const active = listTasks().find((item) => (
      item.environmentKey === environmentKey
      && (!platform || item.platform === platform)
      && !isTerminalStatus(item.status)
    ))
    sendJson(req, res, config, 200, { ok: true, task: null, status: active?.status || null })
    return
  }
  task.status = 'claimed'
  task.claimedAt = nowIso()
  task.lastProgressAt = task.claimedAt
  task.lastStageAt = task.claimedAt
  task.lastStage = 'extension_claimed'
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
    if (taskProgressAgeMs(task) <= claimedTimeoutMsForTask(task)) continue
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
    if (taskProgressAgeMs(task) <= claimedTimeoutMsForTask(task)) continue
    expireClaimedTask(task)
    changed = true
  }
  if (changed) await saveRuntimeTasks()
}

function findNextClaimableTask(environmentKey, platform = '') {
  if (!environmentKey) return null
  const claimable = listTasks().filter((task) => (
    task.environmentKey === environmentKey
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
  const environmentKey = String(body.environmentKey || '').trim()
  if (!environmentKey) {
    const error = new Error('environmentKey is required for task completion')
    error.statusCode = 400
    error.code = 'ENVIRONMENT_KEY_REQUIRED'
    throw error
  }
  const task = tasksById.get(Number(taskId))
  if (!task) {
    const error = new Error('task not found')
    error.statusCode = 404
    throw error
  }
  if (task.environmentKey !== environmentKey) {
    const error = new Error('task environment mismatch')
    error.statusCode = 409
    error.code = 'TASK_ENVIRONMENT_MISMATCH'
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
      const terminated = terminateTaskForScheduleClaimError(task, error)
      if (!terminated && isNonRetryableBackendReportError(error)) {
        task.backendSuccessReportRejectedAt = nowIso()
      }
      if (!terminated) console.error('Failed to report schedule execution success:', task.backendSuccessReportLastError)
    })
  } else {
    task.status = 'failed'
    task.failedAt = nowIso()
    task.failureCode = classifyFailureStatus(body.error)
    task.lastError = body.error || null
    browserRuntimeErrorCounter.record(body.error, task.providerProfileId)
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
      const terminated = terminateTaskForScheduleClaimError(task, error)
      if (!terminated && isNonRetryableBackendReportError(error)) {
        task.backendFailureReportRejectedAt = nowIso()
      }
      if (!terminated) console.error('Failed to report schedule execution failure:', task.backendFailureReportLastError)
    })
  }
  recordTaskTerminalThroughput(task, status)
  await saveRuntimeTasks()
  await reportLocalAgentRuntimeStatus(config, {
    reason: `task_${status}`,
    probeAdspower: false,
    force: true,
  }).catch(() => null)
  sendJson(req, res, config, 200, { ok: true, task })
}

async function handleTaskProgress(req, res, config, taskId) {
  const body = await readJson(req)
  await requireHelperAccess(req, config)
  const environmentKey = String(body.environmentKey || '').trim()
  if (!environmentKey) {
    const error = new Error('environmentKey is required for task progress')
    error.statusCode = 400
    error.code = 'ENVIRONMENT_KEY_REQUIRED'
    throw error
  }
  const task = tasksById.get(Number(taskId))
  if (!task) {
    const error = new Error('task not found')
    error.statusCode = 404
    throw error
  }
  if (task.environmentKey !== environmentKey) {
    const error = new Error('task environment mismatch')
    error.statusCode = 409
    error.code = 'TASK_ENVIRONMENT_MISMATCH'
    throw error
  }
  if (task.status !== 'claimed') {
    const error = new Error(`task is not claimed: ${task.status}`)
    error.statusCode = 409
    error.code = 'TASK_NOT_CLAIMED'
    throw error
  }
  const stage = String(body.stage || '').trim().slice(0, 80)
  if (!stage) {
    const error = new Error('stage is required for task progress')
    error.statusCode = 400
    error.code = 'TASK_STAGE_REQUIRED'
    throw error
  }
  const reportedAt = nowIso()
  task.lastStage = stage
  task.lastStageAt = reportedAt
  task.lastProgressAt = reportedAt
  await saveRuntimeTasks()
  sendJson(req, res, config, 200, { ok: true, taskId: task.taskId, stage, progressAt: reportedAt })
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
  const upload = await uploadImageFileToAdsPowerPage(config, body, image.filePath)
  sendJson(req, res, config, 200, { ok: true, image, upload })
}

async function handleUploadImagesToPage(req, res, config) {
  const body = await readJson(req)
  await requireHelperAccess(req, config)
  const urls = Array.isArray(body.urls) ? body.urls.map((value) => String(value || '').trim()).filter(Boolean) : []
  if (body.uploadTarget !== 'douyin_image_text_images') {
    throw new Error(`douyin image-text upload target is not allowed: ${body.uploadTarget || '-'}`)
  }
  if (urls.length < 4 || urls.length > 6) {
    throw new Error(`douyin image-text requires 4-6 images, received ${urls.length}`)
  }
  const taskId = Number(body.taskId)
  const task = Number.isFinite(taskId) && taskId > 0 ? tasksById.get(taskId) : null
  if (!task
      || isTerminalStatus(task.status)
      || task.platform !== 'douyin'
      || task.environmentKey !== String(body.environmentKey || '').trim()) {
    throw new Error('douyin image-text upload task does not match the active helper task')
  }
  const images = []
  try {
    for (const url of urls) {
      const image = await downloadImageToTempFile(config, url, 0, body.backendBase, 50 * 1024 * 1024)
      if (!['image/jpeg', 'image/png', 'image/webp'].some((type) => image.contentType.toLowerCase().startsWith(type))) {
        throw new Error(`douyin image-text image type is not supported: ${image.contentType}`)
      }
      await verifyDownloadedImageSignature(image)
      images.push(image)
    }
    const upload = await uploadDouyinImageTextFilesToAdsPowerPage(
      config,
      body,
      images.map((image) => image.filePath),
    )
    sendJson(req, res, config, 200, {
      ok: true,
      images: images.map(({ filePath: ignored, ...image }) => image),
      upload,
    })
  } finally {
    await Promise.all(images.map((image) => fs.unlink(image.filePath).catch(() => {})))
  }
}

async function uploadDouyinImageTextFilesToAdsPowerPage(config, body, filePaths) {
  const task = tasksById.get(Number(body.taskId))
  if (!task?.adspower?.puppeteerWs) {
    throw new Error(`no active AdsPower puppeteer session for taskId=${body.taskId || '-'}`)
  }
  const { default: puppeteer } = await import('puppeteer-core')
  const browser = await connectPuppeteer(puppeteer, task.adspower.puppeteerWs, config)
  try {
    const pages = await browser.pages()
    const page = selectUploadTargetPage(pages, {
      platform: 'douyin',
      targetPageUrl: body.targetPageUrl || '',
      browserTargetId: body.browserTargetId || '',
    })
    if (!page) {
      throw new Error(`AdsPower browser has no unambiguous Douyin upload page for taskId=${body.taskId}`)
    }
    if (!body.browserTargetId || puppeteerPageTargetId(page) !== String(body.browserTargetId)) {
      throw new Error(`douyin image-text browser target mismatch for taskId=${body.taskId}`)
    }
    const currentUrl = page.url()
    if (!currentUrl.includes('/creator-micro/content/upload')) {
      throw new Error(`douyin image-text upload is only allowed on upload page: ${currentUrl}`)
    }
    await page.bringToFront().catch(() => {})
    const unpublishedDraft = await readDouyinUnpublishedDraftState(page)
    if (unpublishedDraft.blocked) {
      throw new Error(
        'DOUYIN_UNPUBLISHED_DRAFT_BLOCKED：检测到抖音账号存在上次未发布图文；'
        + '请在当前浏览器环境中人工选择“继续编辑”或“放弃”后，再点击立即重试；'
        + `continue=${unpublishedDraft.hasContinue ? 'yes' : 'no'}；`
        + `giveUp=${unpublishedDraft.hasGiveUp ? 'yes' : 'no'}；`
        + `url=${unpublishedDraft.href || currentUrl}`,
      )
    }
    const inputs = await page.$$('input[type="file"]')
    const targets = await chooseDouyinImageTextInputs(inputs)
    if (targets.length !== 1) {
      throw new Error(`douyin image-text multiple image input not found; inputCount=${inputs.length}`)
    }
    const target = targets[0]
    await target.uploadFile(...filePaths)
    const state = await readAndDispatchFileInputState(target)
    if (state.filesLength !== filePaths.length) {
      throw new Error(`douyin image-text file input count mismatch: expected=${filePaths.length}, actual=${state.filesLength}`)
    }
    const completion = await waitForDouyinImageTextUploadCompleted(page, filePaths.length)
    return {
      pageUrl: page.url(),
      fileInputCount: inputs.length,
      inputState: state,
      expectedImageCount: filePaths.length,
      completion,
    }
  } finally {
    await browser.disconnect()
  }
}

async function readDouyinUnpublishedDraftState(page) {
  return page.evaluate(() => {
    const normalize = (value) => String(value || '').replace(/\s+/g, ' ').trim()
    const isVisible = (element) => {
      if (!(element instanceof HTMLElement)) return false
      const style = getComputedStyle(element)
      const rect = element.getBoundingClientRect()
      return rect.width > 0
        && rect.height > 0
        && style.display !== 'none'
        && style.visibility !== 'hidden'
        && Number.parseFloat(style.opacity || '1') > 0.01
    }
    const prompt = Array.from(document.querySelectorAll('div, span, p'))
      .filter(isVisible)
      .find((element) => normalize(element.textContent)
        === '你还有上次未发布的图文，是否继续编辑？')
    if (!prompt) {
      return {
        blocked: false,
        href: location.href,
        hasContinue: false,
        hasGiveUp: false,
      }
    }
    const visibleActions = Array.from(document.querySelectorAll('button, a, span, div'))
      .filter(isVisible)
      .map((element) => normalize(element.textContent))
    return {
      blocked: true,
      href: location.href,
      hasContinue: visibleActions.includes('继续编辑'),
      hasGiveUp: visibleActions.includes('放弃'),
    }
  })
}

async function waitForDouyinImageTextUploadCompleted(page, expectedImageCount) {
  let resultHandle
  try {
    resultHandle = await page.waitForFunction((expected) => {
      const normalize = (value) => String(value || '').replace(/\s+/g, ' ').trim()
      const text = normalize(document.body?.innerText || document.body?.textContent || '')
      const failed = /图片上传失败|上传失败|重新上传失败/.test(text)
      if (failed) {
        return {
          complete: false,
          failed: true,
          href: location.href,
          text: text.slice(0, 500),
        }
      }
      const explicit = text.match(/已添加\s*(\d+)\s*张图片/)
      const explicitCount = explicit ? Number(explicit[1]) : 0
      let thumbnailCount = 0
      const editImageLabel = Array.from(document.querySelectorAll('span, div'))
        .find((item) => normalize(item.textContent || '') === '编辑图片')
      let section = editImageLabel?.parentElement || null
      while (section && section !== document.body && thumbnailCount === 0) {
        thumbnailCount = Array.from(section.querySelectorAll('[class*="img-"], [draggable="true"]'))
          .filter((item) => {
            const style = getComputedStyle(item)
            const rect = item.getBoundingClientRect()
            return rect.width >= 24
              && rect.height >= 24
              && style.display !== 'none'
              && style.visibility !== 'hidden'
              && /url\(/i.test(style.backgroundImage || '')
          })
          .length
        section = section.parentElement
      }
      const pending = /取消上传|正在上传|上传中/.test(text)
      const confirmedCount = explicitCount || thumbnailCount
      if (confirmedCount !== expected || pending) return false
      return {
        complete: true,
        failed: false,
        href: location.href,
        confirmedCount,
        explicitCount,
        thumbnailCount,
      }
    }, {
      polling: 300,
      timeout: DOUYIN_IMAGE_UPLOAD_COMPLETE_TIMEOUT_MS,
    }, expectedImageCount)
  } catch (error) {
    const state = await page.evaluate(() => ({
      href: location.href,
      text: String(document.body?.innerText || document.body?.textContent || '').replace(/\s+/g, ' ').trim().slice(0, 600),
    })).catch(() => ({ href: page.url(), text: '' }))
    throw new Error(
      `DOUYIN_IMAGE_UPLOAD_TIMEOUT：抖音详情页未确认${expectedImageCount}张图片上传完成；`
      + `url=${state.href || page.url()}；page=${state.text || '-'}；cause=${error.message}`,
    )
  }
  try {
    const completion = await resultHandle.jsonValue()
    if (completion?.failed) {
      throw new Error(
        `DOUYIN_IMAGE_UPLOAD_FAILED：抖音页面报告图片上传失败；`
        + `url=${completion.href || page.url()}；page=${completion.text || '-'}`,
      )
    }
    return completion
  } finally {
    await resultHandle.dispose().catch(() => {})
  }
}

async function chooseDouyinImageTextInputs(inputs) {
  const candidates = []
  for (const input of inputs) {
    const meta = await input.evaluate((el) => {
      const accept = String(el.getAttribute('accept') || '').toLowerCase()
      const descriptor = `${accept} ${el.id || ''} ${el.name || ''} ${String(el.className || '')}`.toLowerCase()
      return {
        accept,
        descriptor,
        multiple: Boolean(el.multiple),
      }
    }).catch(() => ({}))
    if (!meta.multiple) continue
    if (!/(image|jpg|jpeg|png|webp)/.test(`${meta.accept || ''} ${meta.descriptor || ''}`)) continue
    if (isVideoFileInputDescriptor(meta.accept, meta.descriptor)) continue
    candidates.push(input)
  }
  return candidates.slice(0, 1)
}

async function uploadImageFileToAdsPowerPage(config, body, filePath) {
  const task = resolveTaskWithPuppeteerWs(body)
  if (!task?.adspower?.puppeteerWs) {
    throw new Error(`no active AdsPower puppeteer session for environmentKey=${body.environmentKey || '-'}`)
  }
  const platform = String(body.platform || task.platform || 'toutiao').trim().toLowerCase()
  const { default: puppeteer } = await import('puppeteer-core')
  const browser = await connectPuppeteer(puppeteer, task.adspower.puppeteerWs, config)
  try {
    const pages = await browser.pages()
    const page = selectUploadTargetPage(pages, {
      platform,
      targetPageUrl: body.targetPageUrl || body.pageUrl || '',
      browserTargetId: body.browserTargetId || '',
    })
    if (!page) {
      const candidates = describeUploadPageCandidates(pages)
      throw new Error(`AdsPower browser has no unambiguous active ${platform || 'target'} page; browserTargetId=${body.browserTargetId || '-'}; candidates=${JSON.stringify(candidates).slice(0, 1200)}`)
    }
    await page.bringToFront().catch(() => {})
    if (platform === 'douyin' && body.uploadTarget !== 'douyin_article_head_image') {
      throw new Error(`douyin upload target is not allowed: ${body.uploadTarget || '-'}`)
    }
    if (platform === 'toutiao' && body.uploadTarget !== 'toutiao_article_cover') {
      throw new Error(`toutiao upload target is not allowed: ${body.uploadTarget || '-'}`)
    }
    const chooserState = await acceptPlatformFileChooser(page, filePath, platform, {
      uploadTarget: body.uploadTarget || '',
      click: body.click || null,
    })
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
          : platform === 'douyin'
            ? await chooseDouyinArticleHeadImageInputs(inputs)
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

async function acceptPlatformFileChooser(page, filePath, platform, options = {}) {
  const labels = uploadChooserLabels(platform)
  const normalized = String(platform || '').trim().toLowerCase()
  // Baijiahao reuses the same upload text for article, cover, and video controls.
  // Use the direct image input path below instead of opening a generic file chooser.
  if (normalized === 'baijiahao') {
    return null
  }
  if (normalized === 'douyin') {
    return acceptDouyinArticleHeadFileChooser(page, filePath, options)
  }
  if (normalized === 'toutiao' && options.uploadTarget !== 'toutiao_article_cover') {
    throw new Error(`toutiao upload target is not allowed: ${options.uploadTarget || '-'}`)
  }

  const chooserPromise = page.waitForFileChooser({ timeout: 3_000 }).catch(() => null)
  const clicked = await clickPlatformUploadChooser(page, labels)
  if (!clicked) return null
  const chooser = await chooserPromise
  if (!chooser) return null
  return acceptChooserAndReadState(page, chooser, filePath)
}

async function acceptDouyinArticleHeadFileChooser(page, filePath, options = {}) {
  const candidates = await collectDouyinArticleHeadUploadCandidates(page, options.click)
  const tried = []
  for (const candidate of candidates.slice(0, 5)) {
    tried.push({
      source: candidate.source,
      text: candidate.text,
      x: Math.round(candidate.x),
      y: Math.round(candidate.y),
    })
    const chooserPromise = page.waitForFileChooser({ timeout: 2_500 }).catch(() => null)
    await page.mouse.click(candidate.x, candidate.y, { delay: 40 }).catch(() => {})
    const chooser = await chooserPromise
    if (chooser) return acceptChooserAndReadState(page, chooser, filePath, { tried, uploadTarget: options.uploadTarget })
  }
  return { accepted: false, noChooser: true, tried, uploadTarget: options.uploadTarget }
}

async function collectDouyinArticleHeadUploadCandidates(page, providedClick = null) {
  return page.evaluate((click) => {
    const normalize = (value) => String(value || '').replace(/\s+/g, '')
    const visible = (el) => {
      if (!el?.getBoundingClientRect) return false
      const rect = el.getBoundingClientRect()
      const style = getComputedStyle(el)
      return rect.width > 0 && rect.height > 0 && style.display !== 'none' && style.visibility !== 'hidden'
    }
    const isHeadImageContext = (el) => {
      let current = el
      for (let depth = 0; current && depth < 7; depth += 1) {
        const text = normalize(current.textContent || '')
        if (text.includes('封面设置') || text.includes('文章正文')) return false
        if (text.includes('文章头图') && (text.includes('点击上传图片') || text.includes('上传图片'))) return true
        current = current.parentElement
      }
      return false
    }
    const points = []
    if (Number.isFinite(click?.clientX) && Number.isFinite(click?.clientY)) {
      const pointTarget = document.elementFromPoint(click.clientX, click.clientY)
      if (pointTarget && isHeadImageContext(pointTarget)) {
        points.push({ source: 'provided_head_image_point', text: normalize(pointTarget.textContent || ''), x: click.clientX, y: click.clientY })
      }
    }
    const labels = Array.from(document.querySelectorAll('label, div, span, p'))
      .filter(visible)
      .filter((el) => normalize(el.textContent || '') === '文章头图')
    for (const label of labels) {
      let row = label
      const rows = []
      for (let depth = 0; row && depth < 7; depth += 1) {
        const text = normalize(row.textContent || '')
        if (text.includes('文章头图') && (text.includes('点击上传图片') || text.includes('上传图片'))
            && !text.includes('封面设置') && !text.includes('文章正文')) rows.push(row)
        row = row.parentElement
      }
      rows.sort((left, right) => {
        const a = left.getBoundingClientRect()
        const b = right.getBoundingClientRect()
        return a.width * a.height - b.width * b.height
      })
      const headRow = rows[0]
      if (!headRow) continue
      const entries = Array.from(headRow.querySelectorAll('button, [role="button"], div, span, label'))
        .filter(visible)
        .filter((el) => {
          const text = normalize(el.textContent || '')
          return text === '点击上传图片' || text === '上传图片'
        })
      for (const entry of entries) {
        const target = entry.closest('[class*="content-upload"], button, label, [role="button"]') || entry
        if (!visible(target) || !isHeadImageContext(target)) continue
        const icon = target.querySelector('[class*="addIcon"], [class*="addInnerIcon"]')
        const rect = visible(icon) ? icon.getBoundingClientRect() : target.getBoundingClientRect()
        points.push({ source: 'scoped_article_head_dom', text: normalize(target.textContent || '').slice(0, 80), x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 })
      }
    }
    const seen = new Set()
    return points.filter((item) => {
      const key = `${Math.round(item.x)},${Math.round(item.y)}`
      if (seen.has(key)) return false
      seen.add(key)
      return true
    })
  }, providedClick).catch(() => [])
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
      states.push(extra.uploadTarget === 'douyin_article_head_image'
        ? await readFileInputState(input)
        : await readAndDispatchFileInputState(input))
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

async function chooseDouyinArticleHeadImageInputs(inputs) {
  const candidates = []
  for (const input of inputs) {
    const meta = await input.evaluate((el) => {
      const rect = el.getBoundingClientRect()
      const parts = []
      let current = el
      let contextKind = ''
      for (let depth = 0; current && depth < 8; depth += 1) {
        parts.push(current.id || '')
        parts.push(String(current.className || ''))
        const text = String(current.textContent || '').replace(/\s+/g, '')
        if (text && text.length <= 220) parts.push(text)
        if (text.includes('文章头图') && (text.includes('点击上传图片') || text.includes('上传图片'))) {
          contextKind = 'article_head'
          break
        }
        if (text.includes('封面设置') || text.includes('点击上传封面图')) {
          contextKind = 'cover'
          break
        }
        if (text.includes('文章正文') || /prosemirror|tiptap|toolbar|editor/i.test(String(current.className || ''))) {
          contextKind = 'article_body'
          break
        }
        current = current.parentElement
      }
      return {
        accept: el.getAttribute('accept') || '',
        id: el.id || '',
        name: el.name || '',
        className: String(el.className || ''),
        visible: rect.width > 0 && rect.height > 0,
        contextKind,
        nearbyText: parts.join(' '),
      }
    }).catch(() => ({}))
    const descriptor = `${meta.accept || ''} ${meta.id || ''} ${meta.name || ''} ${meta.className || ''} ${meta.contextKind || ''} ${meta.nearbyText || ''}`.toLowerCase()
    if (!/(image|jpg|jpeg|png|webp|gif|jfif)/.test(descriptor)) continue
    if (meta.contextKind === 'article_body' || /文章正文|prosemirror|tiptap|toolbar|editor|contenteditable|插入图片/.test(descriptor)) continue
    if (meta.contextKind === 'cover' || /封面设置|点击上传封面图|选择封面|编辑封面|mycard/.test(descriptor)) continue
    if (meta.contextKind !== 'article_head' || !/文章头图|点击上传图片|content-upload/.test(descriptor)) continue
    let score = 100
    if (/文章头图/.test(descriptor)) score += 300
    if (/点击上传图片/.test(descriptor)) score += 180
    if (/content-upload/.test(descriptor)) score += 100
    if (meta.visible) score += 5
    candidates.push({ input, score })
  }
  candidates.sort((left, right) => right.score - left.score)
  return candidates.length ? [candidates[0].input] : []
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

async function readFileInputState(input) {
  return input.evaluate((el) => ({
    filesLength: el.files?.length || 0,
    fileName: el.files?.[0]?.name || '',
    accept: el.getAttribute('accept') || '',
    id: el.id || '',
    name: el.name || '',
    className: String(el.className || ''),
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

async function downloadImageToTempFile(config, urlValue, depth = 0, backendBase = '', maxBytes = 20 * 1024 * 1024) {
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
    if (nestedUrl && depth < 3) return downloadImageToTempFile(config, nestedUrl, depth + 1, backendBase, maxBytes)
    throw new Error(`image content-type is not supported: ${contentType}; url=${url.href}; body=${bodyText.slice(0, 240) || '-'}`)
  }
  const buffer = Buffer.from(await response.arrayBuffer())
  if (buffer.byteLength > maxBytes) {
    throw new Error(`image exceeds ${Math.round(maxBytes / 1024 / 1024)}MB`)
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

async function verifyDownloadedImageSignature(image) {
  const buffer = await fs.readFile(image.filePath)
  if (!buffer.length) {
    throw new Error('douyin image-text image is empty')
  }
  const type = String(image.contentType || '').toLowerCase().split(';', 1)[0]
  const valid = type === 'image/jpeg'
    ? buffer.length >= 3 && buffer[0] === 0xff && buffer[1] === 0xd8 && buffer[2] === 0xff
    : type === 'image/png'
      ? buffer.length >= 8
        && buffer.subarray(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]))
      : type === 'image/webp'
        ? buffer.length >= 12
          && buffer.subarray(0, 4).toString('ascii') === 'RIFF'
          && buffer.subarray(8, 12).toString('ascii') === 'WEBP'
        : false
  if (!valid) {
    throw new Error(`douyin image-text image signature does not match MIME: ${type || '-'}`)
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

async function responseJsonWithTimeout(response, timeoutMs = RESPONSE_JSON_TIMEOUT_MS) {
  return withTimeout(response.json(), timeoutMs, `response json ${response.url || response.status}`)
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
      buildRevision: packageInfo.buildRevision,
      time: nowIso(),
      paired: Boolean(runtimeSession?.sessionId && runtimeSession?.hmacSecret),
      session: publicSession(),
      activeProfile: config.activeProfile,
      activeProfileLabel: config.activeProfileLabel,
      profiles: publicProfiles(config),
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
        activeTasks: activeScheduleHeartbeatTasks(),
      },
      backendReports: backendReportSummary(),
      runtimeTasks: runtimeTaskStorageSummary(),
      runtimeStatus: {
        last: lastLocalAgentRuntimeStatus,
        adspowerApi: lastAdspowerApiStatus,
      },
      browserLifecycle: {
        helperBootId: HELPER_BOOT_ID,
        observationEnabled: browserObservationEnabled(config),
        executionEnabled: false,
        lastObservation: lastBrowserObservationStatus,
        metrics: browserResourceMetrics(),
      },
      config: {
        host: config.host,
        port: config.port,
        backendBase: config.backendBase || null,
        trustedBackendBase: config.trustedBackendBase || null,
        activeProfile: config.activeProfile,
        activeProfileLabel: config.activeProfileLabel,
        allowedOrigins: config.allowedOrigins || [],
        privateNetworkAccess: true,
      },
    })
    return
  }
  if (req.method === 'GET' && url.pathname === '/v1/profiles') return handleProfiles(req, res, config)
  if ((req.method === 'GET' || req.method === 'POST') && url.pathname === '/v1/settings/adspower') {
    return handleAdspowerSettings(req, res, config)
  }
  if (req.method === 'GET' && url.pathname === '/v1/adspower/profiles') return handleAdspowerProfiles(req, res, config, url)
  if (req.method === 'POST' && url.pathname === '/v1/adspower/extension-status') return handleAdspowerExtensionStatus(req, res, config)
  if (req.method === 'GET' && url.pathname === '/v1/adspower/managed-resources') {
    return handleManagedBrowserResources(req, res, config)
  }
  if (req.method === 'POST' && url.pathname === '/v1/extension/bind-intents') return handleCreateExtensionBindIntent(req, res, config)
  if (req.method === 'POST' && url.pathname === '/v1/extension/bind-intents/consume') return handleConsumeExtensionBindIntent(req, res, config)
  if (req.method === 'POST' && url.pathname === '/v1/c2/pairing-code') return handlePairingCode(req, res, config)
  if (req.method === 'POST' && url.pathname === '/v1/c2/pairing-status') return handlePairingStatus(req, res, config)
  if (req.method === 'POST' && url.pathname === '/v1/poc/launch') return handleLaunch(req, res, config)
  if (req.method === 'POST' && url.pathname === '/v1/poc/open-environment') return handleOpenEnvironment(req, res, config)
  if (req.method === 'POST' && url.pathname === '/v1/poc/create-and-launch') return handleCreateAndLaunch(req, res, config)
  if ((req.method === 'GET' || req.method === 'POST') && url.pathname === '/v1/poc/schedule-poll-once') return handleSchedulePollOnce(req, res, config)
  if (req.method === 'POST' && url.pathname === '/v1/backend-reports/flush') {
    const body = await readJson(req).catch(() => ({}))
    const platform = String(body.platform || url.searchParams.get('platform') || '').trim()
    return sendJson(req, res, config, 200, await flushPendingBackendReports(config, platform))
  }
  if (req.method === 'POST' && url.pathname === '/v1/poc/accounts') return handleAccounts(req, res, config)
  if (req.method === 'POST' && url.pathname === '/v1/poc/requeue') return handleRequeue(req, res, config)
  if (req.method === 'POST' && url.pathname === '/v1/poc/cancel') return handleCancel(req, res, config)
  if (req.method === 'POST' && url.pathname === '/v1/poc/stop') return handleStop(req, res, config)
  if (req.method === 'GET' && url.pathname === '/v1/poc/tasks') return handleTasks(req, res, config)
  if (req.method === 'GET' && url.pathname === '/v1/extension/tasks') return handleTasks(req, res, config)
  if (req.method === 'GET' && url.pathname === '/v1/extension/tasks/next') return handleNextTask(req, res, config, url)
  if (req.method === 'POST' && url.pathname === '/v1/extension/files/download-image') return handleDownloadImageFile(req, res, config)
  if (req.method === 'POST' && url.pathname === '/v1/extension/files/upload-image-to-page') return handleUploadImageToPage(req, res, config)
  if (req.method === 'POST' && url.pathname === '/v1/extension/files/upload-images-to-page') return handleUploadImagesToPage(req, res, config)

  const completeMatch = url.pathname.match(/^\/v1\/extension\/tasks\/(\d+)\/complete$/)
  if (req.method === 'POST' && completeMatch) return handleTaskComplete(req, res, config, completeMatch[1], 'completed')

  const failMatch = url.pathname.match(/^\/v1\/extension\/tasks\/(\d+)\/fail$/)
  if (req.method === 'POST' && failMatch) return handleTaskComplete(req, res, config, failMatch[1], 'failed')

  const progressMatch = url.pathname.match(/^\/v1\/extension\/tasks\/(\d+)\/progress$/)
  if (req.method === 'POST' && progressMatch) return handleTaskProgress(req, res, config, progressMatch[1])

  sendJson(req, res, config, 404, { ok: false, error: 'not found' })
}

await loadRuntimeSettings()
const config = await loadConfig()
runtimeSettings.activeProfile = config.activeProfile
await browserResourceRegistry.load()
await loadRuntimeTasks()
restoreObservedBrowserEnvironmentsFromTasks()
await loadRuntimeSession(config.activeProfile)
await loadRuntimeNonces()
const server = http.createServer((req, res) => {
  route(req, res, config).catch((error) => {
    browserRuntimeErrorCounter.record(error)
    const statusCode = error.statusCode || 500
    sendJson(req, res, config, statusCode, {
      ok: false,
      code: error.code || null,
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
    reportLocalAgentRuntimeStatus(config, {
      reason: 'schedule_poll_before',
      probeAdspower: false,
    }).catch(() => null)
    pollSelfMediaSchedules(config)
      .then((result) => {
        lastSchedulePollStatus = {
          at: nowIso(),
          ok: true,
          claimed: Boolean(result?.claimed),
          claimBlockedReason: result?.claimBlockedReason || null,
          retryAfterSeconds: Number(result?.retryAfterSeconds) || null,
          platform: result?.platform || null,
          kind: result?.kind || null,
          outcome: result?.outcome || null,
        }
        maybeLogSchedulePollBlock(result)
        reportLocalAgentRuntimeStatus(config, {
          reason: 'schedule_poll_after',
          probeAdspower: false,
        }).catch(() => null)
      })
      .catch((error) => {
        browserRuntimeErrorCounter.record(error)
        lastSchedulePollStatus = {
          at: nowIso(),
          ok: false,
          error: error.message,
        }
        reportLocalAgentRuntimeStatus(config, {
          reason: 'schedule_poll_failed',
          probeAdspower: false,
          lastErrorCode: 'SCHEDULE_POLL_FAILED',
          lastErrorMessage: error.message,
        }).catch(() => null)
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

function startLocalAgentRuntimeStatusReporter(config) {
  const tick = (reason, force = false) => {
    if (!runtimeSession?.sessionId || !runtimeSession?.hmacSecret) return
    reportLocalAgentRuntimeStatus(config, { reason, force }).catch(() => null)
  }
  setTimeout(() => tick('startup', true), 2_000).unref?.()
  setInterval(() => tick('heartbeat'), LOCAL_AGENT_RUNTIME_STATUS_HEARTBEAT_MS).unref?.()
}

async function pollSelfMediaSchedules(config) {
  await expireTimedOutPendingScheduleTasks(config)
  if (!hasAvailableScheduleClaimSlot(config)) {
    return { ok: true, claimed: false, claimBlockedReason: 'HELPER_CAPACITY_FULL' }
  }
  const timeoutMs = Number(config.selfMediaSchedulePollStepTimeoutMs || SCHEDULE_POLL_STEP_TIMEOUT_MS)
  const platforms = rotateSchedulePlatforms(await selfMediaSchedulePlatforms(config))
  let lastNoClaimReason = ''
  let lastRetryAfterSeconds = null
  for (const platform of platforms) {
    const publishCheck = await withTimeout(
      claimAndCheckPublishResult(config, platform),
      publishCheckPollStepTimeoutMs(config, platform, timeoutMs),
      `self-media publish check poll ${platform}`,
    )
    if (publishCheck.claimed) return { ...publishCheck, platform, kind: 'publish_result_check' }
    ;({ reason: lastNoClaimReason, retryAfterSeconds: lastRetryAfterSeconds } = preferScheduleClaimBlock(
      lastNoClaimReason,
      lastRetryAfterSeconds,
      publishCheck.claimBlockedReason,
      publishCheck.retryAfterSeconds,
    ))
    const launch = await withTimeout(
      claimAndLaunchScheduledTask(config, platform),
      timeoutMs,
      `self-media schedule execution poll ${platform}`,
    )
    if (launch.claimed) return { ...launch, platform, kind: 'schedule_execution' }
    ;({ reason: lastNoClaimReason, retryAfterSeconds: lastRetryAfterSeconds } = preferScheduleClaimBlock(
      lastNoClaimReason,
      lastRetryAfterSeconds,
      launch.claimBlockedReason,
      launch.retryAfterSeconds,
    ))
  }
  return {
    ok: true,
    claimed: false,
    claimBlockedReason: lastNoClaimReason || 'NO_DUE_TASK',
    retryAfterSeconds: lastRetryAfterSeconds,
  }
}

function maybeLogSchedulePollBlock(result) {
  const now = Date.now()
  const decision = schedulePollBlockLogDecision(
    lastSchedulePollBlockLog,
    result,
    now,
    SCHEDULE_POLL_BLOCK_LOG_INTERVAL_MS,
  )
  lastSchedulePollBlockLog = decision.state
  if (!decision.shouldLog) return
  const reason = decision.state.reason
  const retryAfterSeconds = Number(result?.retryAfterSeconds) || null
  console.warn(
    `GEO self-media schedule claim blocked: ${reason}`
      + (retryAfterSeconds ? `; retryAfterSeconds=${retryAfterSeconds}` : ''),
  )
}

function rotateSchedulePlatforms(platforms) {
  if (!Array.isArray(platforms) || platforms.length < 2) return platforms || []
  const offset = schedulePlatformCursor % platforms.length
  schedulePlatformCursor = (offset + 1) % platforms.length
  return platforms.slice(offset).concat(platforms.slice(0, offset))
}

function publishCheckPollStepTimeoutMs(config, platform, defaultTimeoutMs) {
  const configured = Number(config.selfMediaPublishCheckPollStepTimeoutMs)
  if (Number.isFinite(configured) && configured > 0) return configured
  const normalized = String(platform || '').trim().toLowerCase()
  if (normalized === 'baijiahao') return BAIJIAHAO_PUBLISH_CHECK_STEP_TIMEOUT_MS
  return defaultTimeoutMs
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
startLocalAgentRuntimeStatusReporter(config)
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
