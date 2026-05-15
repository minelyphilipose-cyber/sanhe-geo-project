import { beforeEach, describe, expect, it, vi } from 'vitest'
import { sessionStorage } from '@/shared/storage'
import { BRIDGE_CHANNEL } from '@/admin-bridge/bridgeMessages'
import { startFillTask } from './fillFlow'
import { startCookieCaptureForAccount } from './cookieCapture'
import { getActiveTask } from './taskLifecycle'

vi.mock('@/shared/env', () => ({ EXTENSION_VERSION: '0.1.0' }))
vi.mock('@/shared/logger', () => ({
  logger: {
    info: vi.fn(),
    warn: vi.fn(),
  },
}))
vi.mock('@/shared/storage', () => ({
  sessionStorage: {
    get: vi.fn(),
    set: vi.fn(),
    clear: vi.fn(),
  },
}))
vi.mock('@/shared/api', () => ({
  ExtensionApiError: class ExtensionApiError extends Error {
    constructor(public status: number, public code: number, message: string) {
      super(message)
    }
  },
  extensionApi: {
    refresh: vi.fn(),
  },
}))
vi.mock('./fillFlow', () => ({
  startFillTask: vi.fn(),
}))
vi.mock('./taskLifecycle', () => ({
  HEARTBEAT_ALARM_NAME: 'geo-task-heartbeat',
  getActiveTask: vi.fn(),
  handleTaskHeartbeatAlarm: vi.fn(),
  handleTaskTabRemoved: vi.fn(),
  publishActiveTask: vi.fn(),
}))
vi.mock('./cookieCapture', () => ({
  captureCookiesForAccount: vi.fn(),
  handleCookieDomainReady: vi.fn(),
  startCookieCaptureForAccount: vi.fn(),
}))

beforeEach(() => {
  vi.clearAllMocks()
  vi.stubGlobal('chrome', {
    runtime: {
      onInstalled: { addListener: vi.fn() },
      onStartup: { addListener: vi.fn() },
      onMessage: { addListener: vi.fn() },
      sendMessage: vi.fn(async () => undefined),
    },
    alarms: {
      create: vi.fn(),
      onAlarm: { addListener: vi.fn() },
    },
    tabs: {
      onRemoved: { addListener: vi.fn() },
      onUpdated: { addListener: vi.fn() },
    },
  })
  vi.mocked(sessionStorage.get).mockResolvedValue({
    token: 'ext.secret',
    sessionId: 88,
    extensionVersion: '0.1.0',
    expiresAt: '2026-05-14T00:00:00Z',
    boundAt: '2026-05-07T00:00:00Z',
  })
  vi.mocked(getActiveTask).mockResolvedValue(null)
  vi.mocked(startFillTask).mockResolvedValue({ taskId: 30, status: 'filled' })
  vi.mocked(startCookieCaptureForAccount).mockResolvedValue({
    accountId: 60,
    platform: 'toutiao',
    status: 'opening_login',
    message: '已打开平台登录页，请完成登录；登录成功后扩展会自动捕获凭证。',
  })
})

describe('admin bridge service worker messages', () => {
  it('responds to ping with extension version and bound state', async () => {
    const { handleAdminBridgeMessage } = await import('./index')

    const result = await handleAdminBridgeMessage({
      channel: BRIDGE_CHANNEL,
      type: 'GEO_PING',
      requestId: 'req-1',
    })

    expect(result).toMatchObject({
      channel: BRIDGE_CHANNEL,
      type: 'GEO_PONG',
      requestId: 'req-1',
      payload: {
        installed: true,
        extensionVersion: '0.1.0',
        bound: true,
      },
    })
  })

  it('starts a fill task from admin bridge command when session is bound and no task is active', async () => {
    const { handleAdminBridgeMessage } = await import('./index')

    const result = await handleAdminBridgeMessage({
      channel: BRIDGE_CHANNEL,
      type: 'GEO_START_FILL',
      requestId: 'req-2',
      payload: {
        taskId: 30,
        articleId: 20,
        accountId: 60,
        platform: 'toutiao',
      },
    })

    expect(startFillTask).toHaveBeenCalledWith(expect.objectContaining({
      taskId: 30,
      platform: 'toutiao',
      status: 'token_issued',
    }))
    expect(result).toMatchObject({
      type: 'GEO_FILL_STATUS',
      payload: {
        taskId: 30,
        status: 'filled',
      },
    })
  })

  it('rejects start fill when extension is not bound', async () => {
    vi.mocked(sessionStorage.get).mockResolvedValue(null)
    const { handleAdminBridgeMessage } = await import('./index')

    const result = await handleAdminBridgeMessage({
      channel: BRIDGE_CHANNEL,
      type: 'GEO_START_FILL',
      requestId: 'req-3',
      payload: { taskId: 30, platform: 'toutiao' },
    })

    expect(startFillTask).not.toHaveBeenCalled()
    expect(result).toMatchObject({
      type: 'GEO_FILL_ERROR',
      payload: {
        code: 'EXTENSION_UNBOUND',
      },
    })
  })

  it('rejects start fill when another fill task is active', async () => {
    vi.mocked(getActiveTask).mockResolvedValue({
      taskId: 29,
      tabId: 9,
      token: 'ext.secret',
      startedAt: Date.now(),
    })
    const { handleAdminBridgeMessage } = await import('./index')

    const result = await handleAdminBridgeMessage({
      channel: BRIDGE_CHANNEL,
      type: 'GEO_START_FILL',
      requestId: 'req-4',
      payload: { taskId: 30, platform: 'toutiao' },
    })

    expect(startFillTask).not.toHaveBeenCalled()
    expect(result).toMatchObject({
      type: 'GEO_FILL_ERROR',
      payload: {
        taskId: 30,
        code: 'ACTIVE_TASK_EXISTS',
      },
    })
  })

  it('starts cookie capture from admin bridge command', async () => {
    const { handleAdminBridgeMessage } = await import('./index')

    const result = await handleAdminBridgeMessage({
      channel: BRIDGE_CHANNEL,
      type: 'GEO_START_COOKIE_CAPTURE',
      requestId: 'req-5',
      payload: {
        brandId: 10,
        accountId: 60,
        platform: 'toutiao',
        accountName: '头条账号',
      },
    })

    expect(startCookieCaptureForAccount).toHaveBeenCalledWith({
      brandId: 10,
      accountId: 60,
      platform: 'toutiao',
      accountName: '头条账号',
    })
    expect(result).toMatchObject({
      type: 'GEO_COOKIE_CAPTURE_STATUS',
      payload: {
        accountId: 60,
        status: 'opening_login',
      },
    })
  })
})
