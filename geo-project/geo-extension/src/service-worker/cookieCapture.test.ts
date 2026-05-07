import { beforeEach, describe, expect, it, vi } from 'vitest'
import { extensionApi } from '@/shared/api'
import { sessionStorage } from '@/shared/storage'
import { captureCookiesForAccount, domainsForPlatform } from './cookieCapture'

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
})
