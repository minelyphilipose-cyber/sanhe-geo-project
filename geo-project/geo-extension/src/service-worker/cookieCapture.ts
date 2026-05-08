import { EXTENSION_VERSION } from '@/shared/env'
import { ExtensionApiError, extensionApi } from '@/shared/api'
import { sessionStorage } from '@/shared/storage'
import type { CookieCaptureResponse, ExtensionSelfMediaAccount } from '@/types/extension'

const PLATFORM_DOMAINS: Record<string, string[]> = {
  toutiao: ['toutiao.com'],
  zhihu: ['zhihu.com'],
}

const REQUIRED_COOKIE_NAMES: Record<string, string[]> = {
  toutiao: ['sessionid'],
  zhihu: ['z_c0'],
}

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

export function domainsForPlatform(platform: string): string[] {
  return PLATFORM_DOMAINS[platform] ?? []
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
