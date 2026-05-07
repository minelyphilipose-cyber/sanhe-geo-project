import { beforeEach, describe, expect, it, vi } from 'vitest'
import { extensionApi } from './api'

beforeEach(() => {
  vi.stubGlobal('fetch', vi.fn(async (_url: string, init?: RequestInit) => ({
    ok: true,
    json: async () => ({
      data: {
        supported: true,
        minVersion: '0.1.0',
        latestVersion: '0.1.0',
        requestBody: init?.body,
      },
    }),
  })))
})

describe('extensionApi', () => {
  it('posts version check with chrome platform', async () => {
    const result = await extensionApi.versionCheck('0.1.0')

    expect(result.supported).toBe(true)
    expect(fetch).toHaveBeenCalledWith(
      'http://localhost:8080/api/v1/extension/version-check',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ platform: 'chrome', currentVersion: '0.1.0' }),
      }),
    )
  })

  it('uses auth header for token refresh and revoke without leaking token in URL', async () => {
    await extensionApi.refresh('ext.secret', '0.1.0')
    await extensionApi.revoke('ext.secret', 88)

    expect(fetch).toHaveBeenNthCalledWith(
      1,
      'http://localhost:8080/api/v1/extension/token/refresh',
      expect.objectContaining({
        method: 'POST',
        headers: expect.any(Headers),
      }),
    )
    expect(fetch).toHaveBeenNthCalledWith(
      2,
      'http://localhost:8080/api/v1/extension/token/88/revoke',
      expect.objectContaining({
        method: 'POST',
        headers: expect.any(Headers),
      }),
    )
    const refreshHeaders = (vi.mocked(fetch).mock.calls[0][1] as RequestInit).headers as Headers
    const revokeHeaders = (vi.mocked(fetch).mock.calls[1][1] as RequestInit).headers as Headers
    expect(refreshHeaders.get('X-Ext-Token')).toBe('ext.secret')
    expect(revokeHeaders.get('X-Ext-Token')).toBe('ext.secret')
    expect(vi.mocked(fetch).mock.calls[1][0]).not.toContain('ext.secret')
  })
})
