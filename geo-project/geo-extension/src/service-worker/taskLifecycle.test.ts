import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ExtensionApiError, extensionApi } from '@/shared/api'
import { sessionStorage } from '@/shared/storage'
import { HEARTBEAT_INTERVAL_MS, publishActiveTask, startTaskLifecycle, stopTaskLifecycle } from './taskLifecycle'

vi.mock('@/shared/storage', () => ({
  sessionStorage: { clear: vi.fn() },
}))
vi.mock('@/shared/api', async () => {
  const actual = await vi.importActual<typeof import('@/shared/api')>('@/shared/api')
  return {
    ...actual,
    extensionApi: {
      ...actual.extensionApi,
      heartbeatTask: vi.fn(),
      publishedTask: vi.fn(),
    },
  }
})

let removedListener: ((tabId: number) => void) | undefined

beforeEach(() => {
  vi.useFakeTimers()
  vi.clearAllMocks()
  removedListener = undefined
  vi.mocked(extensionApi.heartbeatTask).mockResolvedValue({ taskId: 30, status: 'filling' })
  vi.mocked(extensionApi.publishedTask).mockResolvedValue({ taskId: 30, status: 'published' })
  vi.stubGlobal('chrome', {
    tabs: {
      onRemoved: {
        addListener: vi.fn((listener) => { removedListener = listener }),
        removeListener: vi.fn(),
      },
    },
    runtime: {
      sendMessage: vi.fn(async () => undefined),
    },
  })
})

afterEach(() => {
  stopTaskLifecycle()
  vi.useRealTimers()
})

describe('task lifecycle heartbeat', () => {
  it('sends heartbeat only after 30 seconds', async () => {
    startTaskLifecycle(30, 9, 'ext.secret')

    await vi.advanceTimersByTimeAsync(HEARTBEAT_INTERVAL_MS - 1)
    expect(extensionApi.heartbeatTask).not.toHaveBeenCalled()
    await vi.advanceTimersByTimeAsync(1)

    expect(extensionApi.heartbeatTask).toHaveBeenCalledWith('ext.secret', 30)
  })

  it('keeps polling on rate limit but stops on state conflict', async () => {
    vi.mocked(extensionApi.heartbeatTask)
      .mockRejectedValueOnce(new ExtensionApiError(429, 70013, 'limited'))
      .mockRejectedValueOnce(new ExtensionApiError(409, 70012, 'conflict'))
    startTaskLifecycle(30, 9, 'ext.secret')

    await vi.advanceTimersByTimeAsync(HEARTBEAT_INTERVAL_MS)
    await vi.advanceTimersByTimeAsync(HEARTBEAT_INTERVAL_MS)
    await vi.advanceTimersByTimeAsync(HEARTBEAT_INTERVAL_MS)

    expect(extensionApi.heartbeatTask).toHaveBeenCalledTimes(2)
    expect(chrome.runtime.sendMessage).toHaveBeenCalledWith(expect.objectContaining({
      payload: expect.objectContaining({ kind: 'stopped' }),
    }))
  })

  it('stops heartbeat when editor tab closes', async () => {
    startTaskLifecycle(30, 9, 'ext.secret')
    removedListener?.(9)
    await vi.advanceTimersByTimeAsync(HEARTBEAT_INTERVAL_MS)

    expect(extensionApi.heartbeatTask).not.toHaveBeenCalled()
    expect(extensionApi.publishedTask).not.toHaveBeenCalled()
  })

  it('reports published and stops heartbeat', async () => {
    startTaskLifecycle(30, 9, 'ext.secret')

    await publishActiveTask(30)
    await vi.advanceTimersByTimeAsync(HEARTBEAT_INTERVAL_MS)

    expect(extensionApi.publishedTask).toHaveBeenCalledWith('ext.secret', 30)
    expect(extensionApi.heartbeatTask).not.toHaveBeenCalled()
    expect(chrome.runtime.sendMessage).toHaveBeenCalledWith(expect.objectContaining({
      payload: expect.objectContaining({ kind: 'published' }),
    }))
  })

  it('switches active task by stopping the old heartbeat', async () => {
    startTaskLifecycle(30, 9, 'ext.secret')
    startTaskLifecycle(31, 10, 'ext.secret')
    await vi.advanceTimersByTimeAsync(HEARTBEAT_INTERVAL_MS)

    expect(extensionApi.heartbeatTask).toHaveBeenCalledWith('ext.secret', 31)
    expect(extensionApi.heartbeatTask).not.toHaveBeenCalledWith('ext.secret', 30)
  })

  it('clears session on auth errors', async () => {
    vi.mocked(extensionApi.heartbeatTask).mockRejectedValueOnce(new ExtensionApiError(401, 70002, 'expired'))
    startTaskLifecycle(30, 9, 'ext.secret')

    await vi.advanceTimersByTimeAsync(HEARTBEAT_INTERVAL_MS)

    expect(sessionStorage.clear).toHaveBeenCalled()
    expect(chrome.runtime.sendMessage).toHaveBeenCalledWith(expect.objectContaining({
      payload: expect.objectContaining({ kind: 'auth_required' }),
    }))
  })
})
