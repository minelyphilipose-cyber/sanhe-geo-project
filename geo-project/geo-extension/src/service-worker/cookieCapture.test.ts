import { beforeEach, describe, expect, it, vi, type Mock } from 'vitest'
import { ExtensionApiError, extensionApi } from '@/shared/api'
import { sessionStorage } from '@/shared/storage'
import { captureCookiesForAccount, domainsForPlatform, handleCookieDomainReady, startCookieCaptureForAccount } from './cookieCapture'

vi.mock('@/shared/env', () => ({
  EXTENSION_VERSION: '0.1.0',
}))

vi.mock('@/shared/storage', () => ({
  sessionStorage: {
    get: vi.fn(),
    getOrCreateInstallId: vi.fn(),
  },
}))

vi.mock('@/shared/api', async () => {
  const actual = await vi.importActual<typeof import('@/shared/api')>('@/shared/api')
  return {
    ...actual,
    extensionApi: {
      ...actual.extensionApi,
      captureCookies: vi.fn(),
    },
  }
})

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(sessionStorage.get).mockResolvedValue({
    token: 'ext.secret',
    sessionId: 88,
    extensionVersion: '0.1.0',
    expiresAt: '2026-05-14T00:00:00Z',
    boundAt: '2026-05-07T00:00:00Z',
  })
  vi.mocked(sessionStorage.getOrCreateInstallId).mockResolvedValue('install-1')
  vi.mocked(extensionApi.captureCookies).mockResolvedValue({
    credentialId: 1,
    accountId: 20,
    brandId: 10,
    platform: 'toutiao',
    version: 3,
    capturedAt: '2026-05-07T12:00:00',
    status: 'ACTIVE',
  })
  vi.stubGlobal('chrome', {
    cookies: {
      getAll: vi.fn(async () => [{ name: 'sessionid', value: 'secret', domain: '.toutiao.com' }]),
    },
    storage: {
      local: {
        get: vi.fn(async () => ({})),
        set: vi.fn(async () => undefined),
        remove: vi.fn(async () => undefined),
      },
    },
    tabs: {
      create: vi.fn(async () => ({ id: 9 })),
    },
  })
  vi.stubGlobal('crypto', { randomUUID: () => 'nonce-1' })
  vi.stubGlobal('navigator', { userAgent: 'Mozilla/5.0' })
})

describe('cookie capture service worker flow', () => {
  it('maps platform to approved domains', () => {
    expect(domainsForPlatform('toutiao')).toEqual(['toutiao.com'])
    expect(domainsForPlatform('zhihu')).toEqual(['zhihu.com'])
  })

  it('reads cookies via chrome.cookies and uploads through extension API', async () => {
    await captureCookiesForAccount({
      accountId: 20,
      brandId: 10,
      platform: 'toutiao',
      accountName: 'Toutiao Account',
    })

    expect(chrome.cookies.getAll).toHaveBeenCalledWith({ domain: 'toutiao.com' })
    expect(extensionApi.captureCookies).toHaveBeenCalledWith(
      'ext.secret',
      expect.objectContaining({
        brandId: 10,
        accountId: 20,
        platform: 'toutiao',
        operatorConfirmed: true,
        confirmNonce: 'nonce-1',
        cookiesJson: expect.stringContaining('sessionid'),
      }),
    )
  })

  it('captures immediately when required platform cookie already exists', async () => {
    const result = await startCookieCaptureForAccount({
      accountId: 20,
      brandId: 10,
      platform: 'toutiao',
      accountName: 'Toutiao Account',
    })

    expect(result).toMatchObject({
      accountId: 20,
      platform: 'toutiao',
      status: 'captured',
    })
    expect(chrome.tabs.create).not.toHaveBeenCalled()
    expect(chrome.storage.local.remove).toHaveBeenCalled()
  })

  it('treats toutiao session alternatives as valid login cookies', async () => {
    vi.mocked(chrome.cookies.getAll).mockResolvedValueOnce([
      { name: 'sessionid_ss', value: 'secret', domain: '.toutiao.com' } as chrome.cookies.Cookie,
    ])

    const result = await startCookieCaptureForAccount({
      accountId: 20,
      brandId: 10,
      platform: 'toutiao',
      accountName: 'Toutiao Account',
    })

    expect(result.status).toBe('captured')
    expect(extensionApi.captureCookies).toHaveBeenCalledTimes(1)
  })

  it('opens login page and stores pending capture when required cookie is missing', async () => {
    vi.mocked(chrome.cookies.getAll).mockResolvedValueOnce([])

    const result = await startCookieCaptureForAccount({
      accountId: 20,
      brandId: 10,
      platform: 'toutiao',
      accountName: 'Toutiao Account',
    })

    expect(result.status).toBe('opening_login')
    expect(chrome.storage.local.set).toHaveBeenCalledWith({
      'geo.extension.pendingCookieCapture': expect.objectContaining({ accountId: 20, platform: 'toutiao' }),
    })
    expect(chrome.tabs.create).toHaveBeenCalledWith({ url: 'https://mp.toutiao.com/' })
    expect(extensionApi.captureCookies).not.toHaveBeenCalled()
  })

  it('captures pending account after platform page reports a logged-in cookie', async () => {
    ;(chrome.storage.local.get as unknown as Mock).mockResolvedValueOnce({
      'geo.extension.pendingCookieCapture': {
        accountId: 20,
        brandId: 10,
        platform: 'toutiao',
        accountName: 'Toutiao Account',
      },
    })

    const result = await handleCookieDomainReady('mp.toutiao.com')

    expect(result).toMatchObject({
      accountId: 20,
      status: 'captured',
    })
    expect(extensionApi.captureCookies).toHaveBeenCalledTimes(1)
    expect(chrome.storage.local.remove).toHaveBeenCalledWith('geo.extension.pendingCookieCapture')
  })

  it('does not retry business 4xx errors', async () => {
    vi.mocked(extensionApi.captureCookies).mockRejectedValue(
      new ExtensionApiError(409, 70016, 'cookie capture confirmation already used'),
    )

    await expect(captureCookiesForAccount({
      accountId: 20,
      brandId: 10,
      platform: 'toutiao',
      accountName: 'Toutiao Account',
    })).rejects.toMatchObject({ code: 70016 })

    expect(extensionApi.captureCookies).toHaveBeenCalledTimes(1)
  })

  it('uses a fresh confirmNonce for retried transient failures', async () => {
    let nonce = 0
    vi.stubGlobal('crypto', { randomUUID: () => `nonce-${++nonce}` })
    vi.mocked(extensionApi.captureCookies)
      .mockRejectedValueOnce(new Error('network timeout'))
      .mockResolvedValueOnce({
        credentialId: 1,
        accountId: 20,
        brandId: 10,
        platform: 'toutiao',
        version: 3,
        capturedAt: '2026-05-07T12:00:00',
        status: 'ACTIVE',
      })

    await captureCookiesForAccount({
      accountId: 20,
      brandId: 10,
      platform: 'toutiao',
      accountName: 'Toutiao Account',
    })

    expect(extensionApi.captureCookies).toHaveBeenCalledTimes(2)
    expect(vi.mocked(extensionApi.captureCookies).mock.calls[0][1].confirmNonce).toBe('nonce-1')
    expect(vi.mocked(extensionApi.captureCookies).mock.calls[1][1].confirmNonce).toBe('nonce-2')
  })
})
