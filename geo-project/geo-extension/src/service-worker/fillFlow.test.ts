import { beforeEach, describe, expect, it, vi } from 'vitest'
import { extensionApi } from '@/shared/api'
import { sessionStorage } from '@/shared/storage'
import { startFillTask } from './fillFlow'
import { startTaskLifecycle } from './taskLifecycle'

vi.mock('@/shared/env', () => ({ EXTENSION_VERSION: '0.1.0' }))
vi.mock('@/shared/storage', () => ({
  sessionStorage: { get: vi.fn() },
}))
vi.mock('@/shared/api', () => ({
  extensionApi: {
    issueFillToken: vi.fn(),
    consumeFillToken: vi.fn(),
    ackTask: vi.fn(),
  },
}))
vi.mock('./taskLifecycle', () => ({
  startTaskLifecycle: vi.fn(),
}))

let readyListener: ((message: unknown, sender: chrome.runtime.MessageSender) => void) | undefined

beforeEach(() => {
  vi.clearAllMocks()
  readyListener = undefined
  vi.mocked(sessionStorage.get).mockResolvedValue({
    token: 'ext.secret',
    sessionId: 88,
    extensionVersion: '0.1.0',
    expiresAt: '2026-05-14T00:00:00Z',
    boundAt: '2026-05-07T00:00:00Z',
  })
  vi.mocked(extensionApi.issueFillToken).mockResolvedValue({ fillToken: 'fill-token', expiresAt: 200, nonce: 'nonce-1' })
  vi.mocked(extensionApi.consumeFillToken).mockResolvedValue({
    taskTargetId: 30,
    expiresAt: 200,
    nonce: 'nonce-1',
    platform: 'toutiao',
    credentialVersion: 3,
    cookiesJson: JSON.stringify([{ name: 'sessionid', value: 'secret-cookie', domain: '.toutiao.com', path: '/', secure: true }]),
    fillPayload: JSON.stringify({
      platform: 'toutiao',
      publishUrl: 'https://mp.toutiao.com/editor',
      title: '<Draft>',
      renderedHtml: '<p>Hello</p><script>bad()</script>',
      tags: ['geo'],
    }),
  })
  vi.mocked(extensionApi.ackTask).mockResolvedValue({ taskId: 30, status: 'filled' })
  vi.stubGlobal('chrome', {
    cookies: { set: vi.fn(async () => ({})) },
    tabs: {
      create: vi.fn(async () => ({ id: 9 })),
      sendMessage: vi.fn(async () => ({ ok: true })),
      onRemoved: { addListener: vi.fn(), removeListener: vi.fn() },
    },
    runtime: {
      onMessage: {
        addListener: vi.fn((listener) => { readyListener = listener }),
        removeListener: vi.fn(),
      },
    },
  })
})

describe('fill service worker flow', () => {
  it('issues, consumes, injects cookies, fills editor, and acks task', async () => {
    const promise = startFillTask(task())
    await vi.waitFor(() => expect(readyListener).toBeTypeOf('function'))
    readyListener?.({ type: 'GEO_EDITOR_READY' }, { tab: { id: 9 } } as chrome.runtime.MessageSender)

    const result = await promise

    expect(result.status).toBe('filled')
    expect(extensionApi.issueFillToken).toHaveBeenCalledWith('ext.secret', expect.objectContaining({ taskTargetId: 30 }))
    expect(extensionApi.consumeFillToken).toHaveBeenCalledTimes(1)
    expect(chrome.cookies.set).toHaveBeenCalledWith(expect.objectContaining({ name: 'sessionid', value: 'secret-cookie' }))
    expect(chrome.tabs.create).toHaveBeenCalledWith({ url: 'https://mp.toutiao.com/editor' })
    expect(JSON.stringify(vi.mocked(chrome.tabs.sendMessage).mock.calls[0][1])).not.toContain('secret-cookie')
    expect(vi.mocked(chrome.tabs.sendMessage).mock.calls[0][1]).toMatchObject({
      type: 'GEO_FILL_TASK',
      payload: expect.objectContaining({ title: 'Draft', contentHtml: '<p>Hello</p>' }),
    })
    expect(extensionApi.ackTask).toHaveBeenCalledWith('ext.secret', 30)
    expect(startTaskLifecycle).toHaveBeenCalledWith(30, 9, 'ext.secret')
  })

  it('sanitizes XSS payload before sending fill command to content script', async () => {
    vi.mocked(extensionApi.consumeFillToken).mockResolvedValue({
      taskTargetId: 30,
      expiresAt: 200,
      nonce: 'nonce-1',
      platform: 'toutiao',
      cookiesJson: '[]',
      fillPayload: JSON.stringify({
        platform: 'toutiao',
        publishUrl: 'https://mp.toutiao.com/editor',
        title: 'Draft',
        renderedHtml: '<svg onload=alert(1)></svg><iframe src="javascript:alert(1)"></iframe><form action="javascript:alert(1)"></form>',
      }),
    })
    const promise = startFillTask(task())
    await vi.waitFor(() => expect(readyListener).toBeTypeOf('function'))
    readyListener?.({ type: 'GEO_EDITOR_READY' }, { tab: { id: 9 } } as chrome.runtime.MessageSender)

    await promise

    const message = JSON.stringify(vi.mocked(chrome.tabs.sendMessage).mock.calls[0][1]).toLowerCase()
    expect(message).not.toContain('onload')
    expect(message).not.toContain('javascript:')
    expect(message).not.toContain('<iframe')
    expect(message).not.toContain('<form')
  })

  it('does not retry consume failure or set cookies', async () => {
    vi.mocked(extensionApi.consumeFillToken).mockRejectedValue(new Error('used'))

    await expect(startFillTask(task())).rejects.toThrow('used')

    expect(extensionApi.consumeFillToken).toHaveBeenCalledTimes(1)
    expect(chrome.cookies.set).not.toHaveBeenCalled()
  })

  it('rejects publishUrl outside platform whitelist before issuing token', async () => {
    await expect(startFillTask({ ...task(), publishUrl: 'https://evil.example/editor' })).rejects.toThrow('白名单')

    expect(extensionApi.issueFillToken).not.toHaveBeenCalled()
  })

  it('validates all cookie domains before writing any cookie', async () => {
    vi.mocked(extensionApi.consumeFillToken).mockResolvedValue({
      taskTargetId: 30,
      expiresAt: 200,
      nonce: 'nonce-1',
      platform: 'toutiao',
      cookiesJson: JSON.stringify([
        { name: 'a', value: '1', domain: '.toutiao.com', path: '/' },
        { name: 'b', value: '2', domain: '.toutiao.com', path: '/' },
        { name: 'c', value: '3', domain: '.evil.example', path: '/' },
        { name: 'd', value: '4', domain: '.toutiao.com', path: '/' },
        { name: 'e', value: '5', domain: '.toutiao.com', path: '/' },
      ]),
      fillPayload: JSON.stringify({
        platform: 'toutiao',
        publishUrl: 'https://mp.toutiao.com/editor',
        title: 'Draft',
        renderedHtml: '<p>Hello</p>',
      }),
    })

    await expect(startFillTask(task())).rejects.toThrow('cookie 域名')

    expect(chrome.cookies.set).not.toHaveBeenCalled()
  })
})

function task() {
  return {
    taskId: 30,
    platform: 'toutiao',
    status: 'token_issued' as const,
    publishUrl: 'https://mp.toutiao.com/editor',
    title: 'Draft',
    createdAt: '2026-05-07T12:00:00',
    fillTokenIssuedAt: '2026-05-07T12:00:00',
    expiresAt: '2026-05-07T12:10:00',
  }
}
