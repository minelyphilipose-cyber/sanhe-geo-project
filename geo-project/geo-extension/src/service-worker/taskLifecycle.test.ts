import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ExtensionApiError, extensionApi } from '@/shared/api'
import { sessionStorage } from '@/shared/storage'
import {
  ACTIVE_TASK_KEY,
  HEARTBEAT_ALARM_NAME,
  handleTaskHeartbeatAlarm,
  handleTaskTabRemoved,
  publishActiveTask,
  startTaskLifecycle,
  stopTaskLifecycle,
  type PersistedActiveTask,
} from './taskLifecycle'

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

let storage: Record<string, unknown>
let now = 1_700_000_000_000

beforeEach(() => {
  vi.clearAllMocks()
  storage = {}
  now = 1_700_000_000_000
  vi.spyOn(Date, 'now').mockImplementation(() => now)
  vi.mocked(extensionApi.heartbeatTask).mockResolvedValue({ taskId: 30, status: 'filling' })
  vi.mocked(extensionApi.publishedTask).mockResolvedValue({ taskId: 30, status: 'published' })
  vi.stubGlobal('chrome', {
    alarms: {
      create: vi.fn(),
      clear: vi.fn(async () => true),
    },
    storage: {
      session: {
        get: vi.fn(async (key: string) => ({ [key]: storage[key] })),
        set: vi.fn(async (values: Record<string, unknown>) => { storage = { ...storage, ...values } }),
        remove: vi.fn(async (key: string) => { delete storage[key] }),
      },
    },
    tabs: {
      get: vi.fn(async () => ({ id: 9 })),
    },
    runtime: {
      sendMessage: vi.fn(async () => undefined),
    },
  })
})

afterEach(async () => {
  await stopTaskLifecycle()
  vi.restoreAllMocks()
})

describe('task lifecycle heartbeat', () => {
  it('persists active task in session storage and creates heartbeat alarm', async () => {
    await startTaskLifecycle(30, 9, 'ext.secret')

    expect(storage[ACTIVE_TASK_KEY]).toEqual({
      taskId: 30,
      tabId: 9,
      token: 'ext.secret',
      startedAt: now,
    })
    expect(chrome.alarms.create).toHaveBeenCalledWith(HEARTBEAT_ALARM_NAME, { periodInMinutes: 0.5 })
  })

  it('loads active task from storage when heartbeat alarm fires', async () => {
    await startTaskLifecycle(30, 9, 'ext.secret')

    await handleTaskHeartbeatAlarm()

    expect(chrome.tabs.get).toHaveBeenCalledWith(9)
    expect(extensionApi.heartbeatTask).toHaveBeenCalledWith('ext.secret', 30)
  })

  it('switches active task by overwriting storage', async () => {
    await startTaskLifecycle(30, 9, 'old.secret')
    await startTaskLifecycle(31, 10, 'new.secret')

    await handleTaskHeartbeatAlarm()

    expect(extensionApi.heartbeatTask).toHaveBeenCalledWith('new.secret', 31)
    expect(extensionApi.heartbeatTask).not.toHaveBeenCalledWith('old.secret', 30)
  })

  it('stops lifecycle when editor tab closes', async () => {
    await startTaskLifecycle(30, 9, 'ext.secret')

    await handleTaskTabRemoved(9)

    expect(storage[ACTIVE_TASK_KEY]).toBeUndefined()
    expect(chrome.alarms.clear).toHaveBeenCalledWith(HEARTBEAT_ALARM_NAME)
  })

  it('reports published and clears persisted lifecycle state', async () => {
    await startTaskLifecycle(30, 9, 'ext.secret')

    await publishActiveTask(30)
    await handleTaskHeartbeatAlarm()

    expect(extensionApi.publishedTask).toHaveBeenCalledWith('ext.secret', 30)
    expect(extensionApi.heartbeatTask).not.toHaveBeenCalled()
    expect(storage[ACTIVE_TASK_KEY]).toBeUndefined()
    expect(chrome.alarms.clear).toHaveBeenCalledWith(HEARTBEAT_ALARM_NAME)
    expect(chrome.runtime.sendMessage).toHaveBeenCalledWith(expect.objectContaining({
      payload: expect.objectContaining({ kind: 'published' }),
    }))
  })

  it('stops stale task when alarm fires after two hours', async () => {
    await startTaskLifecycle(30, 9, 'ext.secret')
    now += 2 * 60 * 60 * 1000 + 1

    await handleTaskHeartbeatAlarm()

    expect(extensionApi.heartbeatTask).not.toHaveBeenCalled()
    expect(storage[ACTIVE_TASK_KEY]).toBeUndefined()
    expect(chrome.runtime.sendMessage).toHaveBeenCalledWith(expect.objectContaining({
      payload: expect.objectContaining({ kind: 'stopped' }),
    }))
  })

  it('clears alarm when storage has no active task', async () => {
    await handleTaskHeartbeatAlarm()

    expect(chrome.alarms.clear).toHaveBeenCalledWith(HEARTBEAT_ALARM_NAME)
    expect(extensionApi.heartbeatTask).not.toHaveBeenCalled()
  })

  it('stops lifecycle when tab lookup fails', async () => {
    await startTaskLifecycle(30, 9, 'ext.secret')
    vi.mocked(chrome.tabs.get).mockRejectedValueOnce(new Error('missing tab'))

    await handleTaskHeartbeatAlarm()

    expect(storage[ACTIVE_TASK_KEY]).toBeUndefined()
    expect(extensionApi.heartbeatTask).not.toHaveBeenCalled()
  })

  it('keeps polling on rate limit but stops on state conflict', async () => {
    vi.mocked(extensionApi.heartbeatTask)
      .mockRejectedValueOnce(new ExtensionApiError(429, 70013, 'limited'))
      .mockRejectedValueOnce(new ExtensionApiError(409, 70012, 'conflict'))
    await startTaskLifecycle(30, 9, 'ext.secret')

    await handleTaskHeartbeatAlarm()
    expect(storage[ACTIVE_TASK_KEY]).toBeDefined()
    await handleTaskHeartbeatAlarm()

    expect(extensionApi.heartbeatTask).toHaveBeenCalledTimes(2)
    expect(storage[ACTIVE_TASK_KEY]).toBeUndefined()
    expect(chrome.runtime.sendMessage).toHaveBeenCalledWith(expect.objectContaining({
      payload: expect.objectContaining({ kind: 'stopped' }),
    }))
  })

  it('clears session on auth errors', async () => {
    vi.mocked(extensionApi.heartbeatTask).mockRejectedValueOnce(new ExtensionApiError(401, 70002, 'expired'))
    await startTaskLifecycle(30, 9, 'ext.secret')

    await handleTaskHeartbeatAlarm()

    expect(sessionStorage.clear).toHaveBeenCalled()
    expect(storage[ACTIVE_TASK_KEY]).toBeUndefined()
    expect(chrome.runtime.sendMessage).toHaveBeenCalledWith(expect.objectContaining({
      payload: expect.objectContaining({ kind: 'auth_required' }),
    }))
  })

  it('stops lifecycle when persisted token is missing', async () => {
    storage[ACTIVE_TASK_KEY] = { taskId: 30, tabId: 9, token: '', startedAt: now } satisfies PersistedActiveTask

    await handleTaskHeartbeatAlarm()

    expect(storage[ACTIVE_TASK_KEY]).toBeUndefined()
    expect(extensionApi.heartbeatTask).not.toHaveBeenCalled()
  })
})
