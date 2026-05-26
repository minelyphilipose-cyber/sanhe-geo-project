import { beforeEach, describe, expect, it, vi, type Mock } from 'vitest'
import { ExtensionApiError, extensionApi } from '@/shared/api'
import { sessionStorage } from '@/shared/storage'
import { captureCookiesForAccount, domainsForPlatform, handleCookieDomainReady, handleCookieIdentityDecision, startCookieCaptureForAccount } from './cookieCapture'

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
      remove: vi.fn(async () => ({ name: 'sessionid', url: 'https://toutiao.com/' })),
    },
    browsingData: {
      remove: vi.fn(async () => undefined),
    },
    storage: {
      local: {
        get: vi.fn(async () => ({})),
        set: vi.fn(async () => undefined),
        remove: vi.fn(async () => undefined),
      },
    },
    tabs: {
      query: vi.fn(async () => []),
      remove: vi.fn(async () => undefined),
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
    expect(domainsForPlatform('xiaohongshu')).toEqual(['xiaohongshu.com'])
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

  it('opens platform page and stores pending capture even when login cookie already exists', async () => {
    vi.mocked(chrome.tabs.query).mockResolvedValueOnce([
      { id: 2, url: 'https://mp.toutiao.com/profile' } as chrome.tabs.Tab,
      { id: 3, url: 'https://example.com/' } as chrome.tabs.Tab,
    ])
    const result = await startCookieCaptureForAccount({
      accountId: 20,
      brandId: 10,
      platform: 'toutiao',
      accountName: 'Toutiao Account',
    })

    expect(result).toMatchObject({
      accountId: 20,
      platform: 'toutiao',
      status: 'opening_login',
    })
    expect(chrome.storage.local.set).toHaveBeenCalledWith({
      'geo.extension.pendingCookieCapture': expect.objectContaining({ accountId: 20, platform: 'toutiao' }),
    })
    expect(chrome.tabs.remove).toHaveBeenCalledWith(2)
    expect(chrome.cookies.remove).toHaveBeenCalledWith(expect.objectContaining({
      name: 'sessionid',
      url: 'http://toutiao.com/',
    }))
    expect(chrome.browsingData.remove).toHaveBeenCalledWith(
      { origins: ['https://mp.toutiao.com', 'https://www.toutiao.com', 'https://sso.toutiao.com'] },
      expect.objectContaining({
        cookies: true,
        indexedDB: true,
        localStorage: true,
        serviceWorkers: true,
      }),
    )
    expect(chrome.tabs.create).toHaveBeenCalledWith({ url: 'https://mp.toutiao.com/' })
    expect(extensionApi.captureCookies).not.toHaveBeenCalled()
  })

  it('treats toutiao session alternatives as valid login cookies', async () => {
    vi.mocked(chrome.cookies.getAll).mockResolvedValueOnce([
      { name: 'sessionid_ss', value: 'secret', domain: '.toutiao.com' } as chrome.cookies.Cookie,
    ])
    ;(chrome.storage.local.get as unknown as Mock).mockResolvedValueOnce({
      'geo.extension.pendingCookieCapture': {
        accountId: 20,
        brandId: 10,
        platform: 'toutiao',
        accountName: 'Toutiao Account',
      },
    })

    const result = await handleCookieDomainReady('mp.toutiao.com', {
      status: 'detected',
      displayName: 'Toutiao Account',
      source: 'test',
    })

    expect(result?.status).toBe('captured')
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

  it('does not overwrite another pending cookie capture target', async () => {
    vi.mocked(chrome.cookies.getAll).mockResolvedValueOnce([])
    ;(chrome.storage.local.get as unknown as Mock).mockResolvedValueOnce({
      'geo.extension.pendingCookieCapture': {
        accountId: 21,
        brandId: 10,
        platform: 'zhihu',
        accountName: '知乎账号',
      },
    })

    const result = await startCookieCaptureForAccount({
      accountId: 20,
      brandId: 10,
      platform: 'toutiao',
      accountName: 'Toutiao Account',
    })

    expect(result).toMatchObject({
      accountId: 21,
      platform: 'zhihu',
      status: 'capture_conflict',
    })
    expect(chrome.storage.local.set).not.toHaveBeenCalled()
    expect(chrome.tabs.create).not.toHaveBeenCalled()
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

    const result = await handleCookieDomainReady('mp.toutiao.com', {
      status: 'detected',
      displayName: 'Toutiao Account',
      source: 'test',
    })

    expect(result).toMatchObject({
      accountId: 20,
      status: 'captured',
    })
    expect(extensionApi.captureCookies).toHaveBeenCalledTimes(1)
    expect(vi.mocked(extensionApi.captureCookies).mock.calls[0][1].capturedFingerprintJson)
      .toContain('"status":"matched"')
    expect(chrome.storage.local.remove).toHaveBeenCalledWith('geo.extension.pendingCookieCapture')
  })

  it('asks for operator decision when detected identity mismatches selected account', async () => {
    ;(chrome.storage.local.get as unknown as Mock).mockResolvedValueOnce({
      'geo.extension.pendingCookieCapture': {
        accountId: 20,
        brandId: 10,
        platform: 'toutiao',
        accountName: 'Toutiao Account',
      },
    })

    const result = await handleCookieDomainReady('mp.toutiao.com', {
      status: 'detected',
      displayName: 'Other Account',
      source: 'test',
    })

    expect(result).toMatchObject({
      accountId: 20,
      platform: 'toutiao',
      status: 'identity_review_required',
      expectedAccountName: 'Toutiao Account',
      actualDisplayName: 'Other Account',
    })
    expect(result?.message).toContain('不一致')
    expect(extensionApi.captureCookies).not.toHaveBeenCalled()
    expect(chrome.storage.local.remove).not.toHaveBeenCalled()
  })

  it('skips account-name comparison when platform identity cannot be detected', async () => {
    ;(chrome.storage.local.get as unknown as Mock).mockResolvedValueOnce({
      'geo.extension.pendingCookieCapture': {
        accountId: 20,
        brandId: 10,
        platform: 'toutiao',
        accountName: '系统平台账号',
      },
    })

    const result = await handleCookieDomainReady('mp.toutiao.com', {
      status: 'unknown',
      displayName: null,
      source: 'test',
    })

    expect(result).toMatchObject({
      accountId: 20,
      platform: 'toutiao',
      status: 'captured',
    })
    const fingerprint = vi.mocked(extensionApi.captureCookies).mock.calls[0][1].capturedFingerprintJson
    expect(fingerprint).toContain('"status":"unknown"')
    expect(fingerprint).toContain('已跳过账号名称比对')
  })

  it('continues capture with audit fingerprint when operator ignores identity mismatch', async () => {
    ;(chrome.storage.local.get as unknown as Mock).mockResolvedValueOnce({
      'geo.extension.pendingCookieCapture': {
        accountId: 20,
        brandId: 10,
        platform: 'toutiao',
        accountName: 'Toutiao Account',
      },
    })

    const result = await handleCookieIdentityDecision({
      decision: 'continue',
      host: 'mp.toutiao.com',
      platformIdentity: {
        status: 'detected',
        displayName: 'Other Account',
        source: 'test',
      },
    })

    expect(result).toMatchObject({
      accountId: 20,
      platform: 'toutiao',
      status: 'captured',
    })
    expect(extensionApi.captureCookies).toHaveBeenCalledTimes(1)
    const fingerprint = vi.mocked(extensionApi.captureCookies).mock.calls[0][1].capturedFingerprintJson
    expect(fingerprint).toContain('"status":"mismatch"')
    expect(fingerprint).toContain('"operatorDecision":"operator_ignored_mismatch"')
    expect(chrome.storage.local.remove).toHaveBeenCalledWith('geo.extension.pendingCookieCapture')
  })

  it('stops capture and clears pending target when operator chooses to handle account mismatch', async () => {
    ;(chrome.storage.local.get as unknown as Mock).mockResolvedValueOnce({
      'geo.extension.pendingCookieCapture': {
        accountId: 20,
        brandId: 10,
        platform: 'toutiao',
        accountName: 'Toutiao Account',
      },
    })

    const result = await handleCookieIdentityDecision({
      decision: 'stop',
      host: 'mp.toutiao.com',
      platformIdentity: {
        status: 'detected',
        displayName: 'Other Account',
        source: 'test',
      },
    })

    expect(result).toMatchObject({
      accountId: 20,
      platform: 'toutiao',
      status: 'stopped',
    })
    expect(extensionApi.captureCookies).not.toHaveBeenCalled()
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
