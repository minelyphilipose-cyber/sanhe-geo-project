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
})
