import { EXTENSION_VERSION } from '@/shared/env'
import { ExtensionApiError, extensionApi } from '@/shared/api'
import { sessionStorage } from '@/shared/storage'
import type { CookieCaptureResponse, CookieCaptureStartedResponse, ExtensionSelfMediaAccount, PlatformIdentitySnapshot } from '@/types/extension'

const PLATFORM_DOMAINS: Record<string, string[]> = {
  toutiao: ['toutiao.com'],
  zhihu: ['zhihu.com'],
  xiaohongshu: ['xiaohongshu.com'],
}

const REQUIRED_COOKIE_NAMES: Record<string, string[]> = {
  toutiao: ['sessionid', 'sessionid_ss', 'sid_tt', 'sid_guard'],
  zhihu: ['z_c0'],
  xiaohongshu: ['web_session', 'a1'],
}

const PLATFORM_LOGIN_URLS: Record<string, string> = {
  toutiao: 'https://mp.toutiao.com/',
  zhihu: 'https://www.zhihu.com/signin',
  xiaohongshu: 'https://creator.xiaohongshu.com/',
}

const PLATFORM_HOST_SUFFIXES: Record<string, string[]> = {
  toutiao: ['toutiao.com', 'bytedance.com', 'byteacctimg.com'],
  zhihu: ['zhihu.com'],
  xiaohongshu: ['xiaohongshu.com'],
}

const PLATFORM_SITE_ORIGINS: Record<string, string[]> = {
  toutiao: ['https://mp.toutiao.com', 'https://www.toutiao.com', 'https://sso.toutiao.com'],
  zhihu: ['https://www.zhihu.com', 'https://zhuanlan.zhihu.com'],
  xiaohongshu: ['https://creator.xiaohongshu.com', 'https://www.xiaohongshu.com', 'https://edith.xiaohongshu.com'],
}

const PENDING_CAPTURE_KEY = 'geo.extension.pendingCookieCapture'

export async function captureCookiesForAccount(
  account: ExtensionSelfMediaAccount,
  platformIdentity?: PlatformIdentitySnapshot | null,
  operatorDecision?: string | null,
): Promise<CookieCaptureResponse> {
  const session = await sessionStorage.get()
  if (!session) throw new Error('扩展登录已失效，请重新绑定。')
  const installId = await sessionStorage.getOrCreateInstallId()
  const cookies = await readPlatformCookies(account.platform)
  const cookiesJson = JSON.stringify(cookies)
  const requiredCookieCheckJson = JSON.stringify(requiredCookieCheck(account.platform, cookies))
  const capturedFingerprintJson = JSON.stringify(buildCapturedFingerprint(account, platformIdentity, operatorDecision))
  return withRetry(() => extensionApi.captureCookies(session.token, {
    brandId: account.brandId,
    accountId: account.accountId,
    platform: account.platform,
    extensionVersion: EXTENSION_VERSION,
    installId,
    operatorConfirmed: true,
    confirmNonce: crypto.randomUUID(),
    cookiesJson,
    userAgent: navigator.userAgent,
    requiredCookieCheckJson,
    capturedFingerprintJson,
  }), 3)
}

export async function startCookieCaptureForAccount(
  account: ExtensionSelfMediaAccount,
): Promise<CookieCaptureStartedResponse> {
  await requireBoundSession()
  ensureSupportedPlatform(account.platform)
  const pending = await getPendingCookieCapture()
  if (pending && !isSameCookieCaptureTarget(pending, account)) {
    return {
      accountId: pending.accountId,
      platform: pending.platform,
      status: 'capture_conflict',
      message: `已有 ${pending.accountName || pending.platform} 登录捕获正在进行，请先完成或重新捕获该账号后再切换。`,
    }
  }

  const cleared = await clearPlatformLoginState(account.platform)
  await savePendingCookieCapture(account)
  await chrome.tabs.create({ url: loginUrlForPlatform(account.platform) })
  return {
    accountId: account.accountId,
    platform: account.platform,
    status: 'opening_login',
    message: cleared.closedTabsCount > 0
      ? '已关闭当前平台页面并清理本地登录状态，请登录目标账号；登录成功后扩展会自动捕获。'
      : '已清理当前平台本地登录状态并打开登录页，请登录目标账号；登录成功后扩展会自动捕获。',
  }
}

export async function handleCookieDomainReady(host: string, platformIdentity?: unknown): Promise<CookieCaptureStartedResponse | null> {
  const pending = await getPendingCookieCapture()
  if (!pending || !domainsForPlatform(pending.platform).some(domain => host === domain || host.endsWith(`.${domain}`))) {
    return null
  }
  if (!await hasRequiredCookies(pending.platform)) {
    return {
      accountId: pending.accountId,
      platform: pending.platform,
      status: 'waiting_login',
      message: '已进入平台页面，等待登录完成后自动捕获凭证。',
    }
  }
  if (platformIdentity === undefined) {
    return null
  }
  const identity = normalizePlatformIdentity(platformIdentity, host)
  const identityCheck = checkPlatformIdentity(pending, identity)
  if (identityCheck.status === 'mismatch') {
    return {
      accountId: pending.accountId,
      platform: pending.platform,
      status: 'identity_review_required',
      expectedAccountName: identityCheck.expectedAccountName,
      actualDisplayName: identityCheck.actualDisplayName,
      message: identityCheck.message,
    }
  }
  const captured = await captureCookiesForAccount(pending, identity)
  await clearPendingCookieCapture()
  return {
    accountId: captured.accountId,
    platform: captured.platform,
    status: 'captured',
    message: '平台登录状态已捕获，后台账号状态将自动刷新。',
  }
}

export async function handleCookieIdentityDecision(payload?: {
  decision?: 'continue' | 'stop'
  host?: string
  platformIdentity?: unknown
}): Promise<CookieCaptureStartedResponse | null> {
  const pending = await getPendingCookieCapture()
  if (!pending) return null
  const host = payload?.host
  if (host && !domainsForPlatform(pending.platform).some(domain => host === domain || host.endsWith(`.${domain}`))) {
    return null
  }
  const identity = normalizePlatformIdentity(payload?.platformIdentity, host || null)
  const identityCheck = checkPlatformIdentity(pending, identity)
  if (payload?.decision === 'stop') {
    await clearPendingCookieCapture()
    return {
      accountId: pending.accountId,
      platform: pending.platform,
      status: 'stopped',
      message: '已停止捕获流程，请处理账号配置或重新登录正确平台账号后再发起捕获。',
      expectedAccountName: identityCheck.expectedAccountName,
      actualDisplayName: identityCheck.actualDisplayName,
    }
  }
  if (!await hasRequiredCookies(pending.platform)) {
    return {
      accountId: pending.accountId,
      platform: pending.platform,
      status: 'waiting_login',
      message: '当前平台登录状态尚未完成，请登录后重试。',
    }
  }
  const captured = await captureCookiesForAccount(pending, identity, identityCheck.status === 'mismatch' ? 'operator_ignored_mismatch' : null)
  await clearPendingCookieCapture()
  return {
    accountId: captured.accountId,
    platform: captured.platform,
    status: 'captured',
    message: identityCheck.status === 'mismatch'
      ? '已按用户确认继续捕获凭证，请回到后台确认账号信息。'
      : '平台登录状态已捕获，后台账号状态将自动刷新。',
    expectedAccountName: identityCheck.expectedAccountName,
    actualDisplayName: identityCheck.actualDisplayName,
  }
}

export function domainsForPlatform(platform: string): string[] {
  return PLATFORM_DOMAINS[platform] ?? []
}

export function requiredCookieNamesForPlatform(platform: string): string[] {
  return REQUIRED_COOKIE_NAMES[platform] ?? []
}

async function readPlatformCookies(platform: string): Promise<chrome.cookies.Cookie[]> {
  const domains = domainsForPlatform(platform)
  if (domains.length === 0) {
    throw new Error('暂不支持该平台的 cookie 捕获')
  }
  const results = await Promise.all(domains.map(domain => chrome.cookies.getAll({ domain })))
  return results.flat()
}

function requiredCookieCheck(platform: string, cookies: chrome.cookies.Cookie[]): Record<string, string> {
  const names = new Set(cookies.map(cookie => cookie.name))
  return Object.fromEntries((REQUIRED_COOKIE_NAMES[platform] ?? []).map(name => [
    name,
    names.has(name) ? 'present' : 'missing',
  ]))
}

async function hasRequiredCookies(platform: string): Promise<boolean> {
  const requiredNames = requiredCookieNamesForPlatform(platform)
  if (requiredNames.length === 0) return false
  const cookies = await readPlatformCookies(platform)
  const names = new Set(cookies.map(cookie => cookie.name))
  if (platform === 'toutiao') {
    return requiredNames.some(name => names.has(name))
  }
  return requiredNames.every(name => names.has(name))
}

async function requireBoundSession() {
  const session = await sessionStorage.get()
  if (!session) throw new Error('扩展登录已失效，请重新绑定。')
}

function ensureSupportedPlatform(platform: string) {
  if (domainsForPlatform(platform).length === 0 || !PLATFORM_LOGIN_URLS[platform]) {
    throw new Error('暂不支持该平台的自动登录捕获')
  }
}

function loginUrlForPlatform(platform: string) {
  const url = PLATFORM_LOGIN_URLS[platform]
  if (!url) throw new Error('暂不支持该平台的自动登录捕获')
  return url
}

async function clearPlatformLoginState(platform: string) {
  const closedTabsCount = await closePlatformTabs(platform)
  const removedCookieCount = await removePlatformCookies(platform)
  await chrome.browsingData.remove({
    origins: PLATFORM_SITE_ORIGINS[platform] ?? [new URL(loginUrlForPlatform(platform)).origin],
  }, {
    cache: true,
    cacheStorage: true,
    cookies: true,
    fileSystems: true,
    indexedDB: true,
    localStorage: true,
    serviceWorkers: true,
    webSQL: true,
  })
  return { closedTabsCount, removedCookieCount }
}

async function closePlatformTabs(platform: string) {
  const suffixes = PLATFORM_HOST_SUFFIXES[platform] ?? domainsForPlatform(platform)
  const tabs = await chrome.tabs.query({})
  let count = 0
  for (const tab of tabs) {
    const host = safeHostname(tab.url)
    if (!tab.id || !host || !hostMatches(host, suffixes)) continue
    await chrome.tabs.remove(tab.id).catch(() => undefined)
    count += 1
  }
  return count
}

async function removePlatformCookies(platform: string) {
  const suffixes = PLATFORM_HOST_SUFFIXES[platform] ?? domainsForPlatform(platform)
  const cookies = await chrome.cookies.getAll({})
  let count = 0
  for (const cookie of cookies) {
    const domain = cookie.domain.replace(/^\./, '')
    if (!hostMatches(domain, suffixes)) continue
    const url = `${cookie.secure ? 'https' : 'http'}://${domain}${cookie.path || '/'}`
    const details: chrome.cookies.Details = { url, name: cookie.name }
    if (cookie.storeId) details.storeId = cookie.storeId
    const removed = await chrome.cookies.remove(details).catch(() => null)
    if (removed) count += 1
  }
  return count
}

function hostMatches(host: string, suffixes: string[]) {
  return suffixes.some(suffix => host === suffix || host.endsWith(`.${suffix}`))
}

function safeHostname(url?: string) {
  try {
    return url ? new URL(url).hostname : null
  } catch {
    return null
  }
}

async function savePendingCookieCapture(account: ExtensionSelfMediaAccount) {
  await chrome.storage.local.set({ [PENDING_CAPTURE_KEY]: account })
}

async function getPendingCookieCapture(): Promise<ExtensionSelfMediaAccount | null> {
  const result = await chrome.storage.local.get(PENDING_CAPTURE_KEY)
  return (result[PENDING_CAPTURE_KEY] as ExtensionSelfMediaAccount | undefined) ?? null
}

async function clearPendingCookieCapture() {
  await chrome.storage.local.remove(PENDING_CAPTURE_KEY)
}

function isSameCookieCaptureTarget(left: ExtensionSelfMediaAccount, right: ExtensionSelfMediaAccount) {
  return left.brandId === right.brandId
    && left.accountId === right.accountId
    && left.platform === right.platform
}

function buildCapturedFingerprint(account: ExtensionSelfMediaAccount, platformIdentity?: PlatformIdentitySnapshot | null, operatorDecision?: string | null) {
  const identity = normalizePlatformIdentity(platformIdentity, null)
  const identityCheck = checkPlatformIdentity(account, identity)
  return {
    browser: 'chrome',
    domains: domainsForPlatform(account.platform),
    platformIdentity: identity,
    identityCheck: operatorDecision ? { ...identityCheck, operatorDecision } : identityCheck,
  }
}

function normalizePlatformIdentity(value: unknown, fallbackHost: string | null): PlatformIdentitySnapshot {
  const candidate = value && typeof value === 'object' ? value as Partial<PlatformIdentitySnapshot> : {}
  const displayName = typeof candidate.displayName === 'string' ? candidate.displayName.trim() : ''
  return {
    status: displayName ? 'detected' : 'unknown',
    displayName: displayName || null,
    source: typeof candidate.source === 'string' ? candidate.source : null,
    host: typeof candidate.host === 'string' ? candidate.host : fallbackHost,
    href: typeof candidate.href === 'string' ? candidate.href : null,
  }
}

function checkPlatformIdentity(account: ExtensionSelfMediaAccount, identity: PlatformIdentitySnapshot) {
  const expected = normalizeIdentityText(account.accountName || '')
  const actual = normalizeIdentityText(identity.displayName || '')
  if (!expected || !actual) {
    return {
      status: 'unknown',
      expectedAccountName: account.accountName || null,
      actualDisplayName: identity.displayName || null,
      message: '未能自动识别当前平台账号昵称，已跳过账号名称比对。',
    }
  }
  const matched = expected.includes(actual) || actual.includes(expected)
  if (matched) {
    return {
      status: 'matched',
      expectedAccountName: account.accountName || null,
      actualDisplayName: identity.displayName || null,
      message: `当前平台账号「${identity.displayName}」与系统账号匹配。`,
    }
  }
  return {
    status: 'mismatch',
    expectedAccountName: account.accountName || null,
    actualDisplayName: identity.displayName || null,
    message: `当前平台账号「${identity.displayName}」与系统账号「${account.accountName}」不一致。`,
  }
}

function normalizeIdentityText(value: string) {
  return value
    .trim()
    .toLowerCase()
    .replace(/[\s_\-—|｜（）()【】\[\]{}]+/g, '')
}

async function withRetry<T>(fn: () => Promise<T>, maxAttempts: number): Promise<T> {
  let lastError: unknown
  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    try {
      return await fn()
    } catch (error) {
      lastError = error
      if (error instanceof ExtensionApiError && error.status >= 400 && error.status < 500) {
        throw error
      }
      if (attempt < maxAttempts) {
        await delay(100 * 2 ** (attempt - 1))
      }
    }
  }
  throw lastError
}

function delay(ms: number) {
  return new Promise(resolve => setTimeout(resolve, ms))
}
