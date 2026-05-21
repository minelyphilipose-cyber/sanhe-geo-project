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
      'http://127.0.0.1:8080/api/v1/extension/version-check',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ platform: 'chrome', currentVersion: '0.1.0' }),
      }),
    )
  })

  it('treats non-zero response code as an API error even when HTTP status is 200', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({
      ok: true,
      status: 200,
      json: async () => ({
        code: 70008,
        message: 'bind code invalid',
      }),
    })))

    await expect(extensionApi.bind('ABCDEFGH', 'install-1', '0.1.0')).rejects.toMatchObject({
      status: 200,
      code: 70008,
    })
  })

  it('uses auth header for token refresh and revoke without leaking token in URL', async () => {
    await extensionApi.refresh('ext.secret', '0.1.0')
    await extensionApi.revoke('ext.secret', 88)

    expect(fetch).toHaveBeenNthCalledWith(
      1,
      'http://127.0.0.1:8080/api/v1/extension/token/refresh',
      expect.objectContaining({
        method: 'POST',
        headers: expect.any(Headers),
      }),
    )
    expect(fetch).toHaveBeenNthCalledWith(
      2,
      'http://127.0.0.1:8080/api/v1/extension/token/88/revoke',
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

  it('gets task list with extension token header and no query string', async () => {
    await extensionApi.tasks('ext.secret')

    expect(fetch).toHaveBeenCalledWith(
      'http://127.0.0.1:8080/api/v1/extension/tasks',
      expect.objectContaining({
        method: 'GET',
        headers: expect.any(Headers),
      }),
    )
    const headers = (vi.mocked(fetch).mock.calls[0][1] as RequestInit).headers as Headers
    expect(headers.get('X-Ext-Token')).toBe('ext.secret')
  })

  it('posts cookie capture with extension token header', async () => {
    await extensionApi.captureCookies('ext.secret', {
      brandId: 10,
      accountId: 20,
      platform: 'toutiao',
      extensionVersion: '0.1.0',
      installId: 'install-1',
      operatorConfirmed: true,
      confirmNonce: 'nonce-1',
      cookiesJson: '[]',
    })

    expect(fetch).toHaveBeenCalledWith(
      'http://127.0.0.1:8080/api/v1/extension/cookies/capture',
      expect.objectContaining({
        method: 'POST',
        headers: expect.any(Headers),
      }),
    )
    const init = vi.mocked(fetch).mock.calls[0][1] as RequestInit
    const headers = init.headers as Headers
    expect(headers.get('X-Ext-Token')).toBe('ext.secret')
    expect(init.body).toContain('"confirmNonce":"nonce-1"')
  })

  it('issues, consumes, and acks fill flow with extension token header', async () => {
    await extensionApi.issueFillToken('ext.secret', {
      taskTargetId: 30,
      platform: 'toutiao',
      extensionVersion: '0.1.0',
    })
    await extensionApi.consumeFillToken('ext.secret', {
      fillToken: 'fill-token',
      platform: 'toutiao',
      extensionVersion: '0.1.0',
    })
    await extensionApi.ackTask('ext.secret', 30)
    await extensionApi.heartbeatTask('ext.secret', 30)
    await extensionApi.publishedTask('ext.secret', 30, {
      action: 'publish_clicked',
      href: 'https://mp.toutiao.com/editor',
      platform: 'toutiao',
      detectedText: '发布',
    })
    await extensionApi.abandonTask('ext.secret', 30)

    expect(fetch).toHaveBeenNthCalledWith(
      1,
      'http://127.0.0.1:8080/api/v1/extension/fill-token/issue',
      expect.objectContaining({ method: 'POST', headers: expect.any(Headers) }),
    )
    expect(fetch).toHaveBeenNthCalledWith(
      2,
      'http://127.0.0.1:8080/api/v1/extension/fill-token/consume',
      expect.objectContaining({ method: 'POST', headers: expect.any(Headers) }),
    )
    expect(fetch).toHaveBeenNthCalledWith(
      3,
      'http://127.0.0.1:8080/api/v1/extension/tasks/30/ack',
      expect.objectContaining({ method: 'POST', headers: expect.any(Headers) }),
    )
    expect(fetch).toHaveBeenNthCalledWith(
      4,
      'http://127.0.0.1:8080/api/v1/extension/tasks/30/heartbeat',
      expect.objectContaining({ method: 'POST', headers: expect.any(Headers) }),
    )
    expect(fetch).toHaveBeenNthCalledWith(
      5,
      'http://127.0.0.1:8080/api/v1/extension/tasks/30/published',
      expect.objectContaining({ method: 'POST', headers: expect.any(Headers) }),
    )
    expect((vi.mocked(fetch).mock.calls[4][1] as RequestInit).body).toContain('"action":"publish_clicked"')
    expect(fetch).toHaveBeenNthCalledWith(
      6,
      'http://127.0.0.1:8080/api/v1/extension/tasks/30/abandon',
      expect.objectContaining({ method: 'POST', headers: expect.any(Headers) }),
    )
  })
})
