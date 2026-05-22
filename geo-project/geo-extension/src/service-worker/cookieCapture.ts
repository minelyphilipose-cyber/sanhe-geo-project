import { EXTENSION_VERSION } from '@/shared/env'
import { ExtensionApiError, extensionApi } from '@/shared/api'
import { sessionStorage } from '@/shared/storage'
import type { CookieCaptureResponse, CookieCaptureStartedResponse, ExtensionSelfMediaAccount } from '@/types/extension'

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

const PENDING_CAPTURE_KEY = 'geo.extension.pendingCookieCapture'

export async function captureCookiesForAccount(
  account: ExtensionSelfMediaAccount,
): Promise<CookieCaptureResponse> {
  const session = await sessionStorage.get()
  if (!session) throw new Error('扩展登录已失效，请重新绑定。')
  const installId = await sessionStorage.getOrCreateInstallId()
  const cookies = await readPlatformCookies(account.platform)
  const cookiesJson = JSON.stringify(cookies)
  const requiredCookieCheckJson = JSON.stringify(requiredCookieCheck(account.platform, cookies))
  const capturedFingerprintJson = JSON.stringify({
    browser: 'chrome',
    domains: domainsForPlatform(account.platform),
  })
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
  if (await hasRequiredCookies(account.platform)) {
    const captured = await captureCookiesForAccount(account)
    await clearPendingCookieCapture()
    return {
      accountId: captured.accountId,
      platform: captured.platform,
      status: 'captured',
      message: '已检测到登录状态并自动捕获凭证。',
    }
  }

  await savePendingCookieCapture(account)
  await chrome.tabs.create({ url: loginUrlForPlatform(account.platform) })
  return {
    accountId: account.accountId,
    platform: account.platform,
    status: 'opening_login',
    message: '已打开平台登录页，请完成登录；登录成功后扩展会自动捕获凭证。',
  }
}

export async function handleCookieDomainReady(host: string): Promise<CookieCaptureStartedResponse | null> {
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
  const captured = await captureCookiesForAccount(pending)
  await clearPendingCookieCapture()
  return {
    accountId: captured.accountId,
    platform: captured.platform,
    status: 'captured',
    message: '平台登录状态已捕获，后台账号状态将自动刷新。',
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
