import { EXTENSION_VERSION } from '@/shared/env'
import { ExtensionApiError, extensionApi } from '@/shared/api'
import { logger } from '@/shared/logger'
import { sessionStorage } from '@/shared/storage'
import { bindExtension } from '@/popup/bindFlow'
import { captureCookiesForAccount, handleCookieDomainReady, handleCookieIdentityDecision, startCookieCaptureForAccount } from './cookieCapture'
import { startFillTask } from './fillFlow'
import { getActiveTask, HEARTBEAT_ALARM_NAME, handleTaskHeartbeatAlarm, handleTaskTabRemoved, publishActiveTask } from './taskLifecycle'
import { BRIDGE_CHANNEL, pongMessage, type AdminBindExtensionPayload, type AdminBridgeMessage, type AdminStartCookieCapturePayload, type AdminStartFillPayload } from '@/admin-bridge/bridgeMessages'
import type { ExtensionMessage, ExtensionSelfMediaAccount, ExtensionTaskListItem, PublishTaskReport } from '@/types/extension'

const REFRESH_ALARM = 'geo-token-refresh'

function ensureRefreshAlarm() {
  chrome.alarms.create(REFRESH_ALARM, { periodInMinutes: 30 })
}

chrome.runtime.onInstalled.addListener(() => {
  logger.info('installed', EXTENSION_VERSION)
  ensureRefreshAlarm()
})

chrome.runtime.onStartup.addListener(() => {
  ensureRefreshAlarm()
})

chrome.alarms.onAlarm.addListener(async (alarm) => {
  if (alarm.name === HEARTBEAT_ALARM_NAME) {
    await handleTaskHeartbeatAlarm()
    return
  }
  if (alarm.name !== REFRESH_ALARM) return
  const session = await sessionStorage.get()
  if (!session) return
  try {
    const refreshed = await extensionApi.refresh(session.token, EXTENSION_VERSION)
    if (!refreshed.renewed && refreshed.sessionId !== session.sessionId) {
      logger.warn('token refresh returned mismatched session id', {
        storedSessionId: session.sessionId,
        refreshedSessionId: refreshed.sessionId,
      })
      await sessionStorage.clear()
      return
    }
    await sessionStorage.set({
      ...session,
      token: refreshed.renewed && refreshed.token ? refreshed.token : session.token,
      sessionId: refreshed.sessionId,
      expiresAt: refreshed.expiresAt,
      extensionVersion: EXTENSION_VERSION,
    })
  } catch (error) {
    if (error instanceof ExtensionApiError && (error.code === 70002 || error.code === 70004)) {
      await sessionStorage.clear()
      void chrome.runtime.sendMessage({
        type: 'GEO_TASK_LIFECYCLE_EVENT',
        payload: { kind: 'auth_required', message: '扩展登录已失效，请重新绑定。' },
      }).catch(() => undefined)
      logger.warn('token refresh requires rebind', { code: error.code })
      return
    }
    logger.warn('token refresh failed', error instanceof Error ? error.message : error)
  }
})

chrome.tabs.onRemoved.addListener((tabId) => {
  void handleTaskTabRemoved(tabId)
})

chrome.tabs.onUpdated.addListener((_tabId, changeInfo, tab) => {
  const url = changeInfo.url || tab.url
  if (!url) return
  let host = ''
  try {
    host = new URL(url).hostname
  } catch {
    return
  }
  if (!host) return
  void handleCookieDomainReady(host).catch(error => {
    logger.warn('cookie capture tab update check failed', error instanceof Error ? error.message : error)
  })
})

chrome.runtime.onMessage.addListener((message: ExtensionMessage, _sender, sendResponse) => {
  if (isAdminBridgeMessage(message)) {
    void handleAdminBridgeMessage(message as AdminBridgeMessage)
      .then(sendResponse)
      .catch(error => sendResponse({
        type: 'GEO_FILL_ERROR',
        payload: {
          code: error instanceof ExtensionApiError && error.code ? String(error.code) : 'SERVICE_WORKER_ERROR',
          message: error instanceof Error ? error.message : '扩展后台处理失败',
        },
      }))
    return true
  }
  if (message.type === 'GEO_COOKIE_DOMAIN_READY') {
    const payload = message.payload as { host?: string, platformIdentity?: unknown } | undefined
    void handleCookieDomainReady(payload?.host || '', payload?.platformIdentity)
      .then(result => sendResponse({ ok: true, result }))
      .catch(error => sendResponse({
        ok: false,
        message: error instanceof Error ? error.message : 'cookie capture failed',
      }))
    return true
  }
  if (message.type === 'GEO_COOKIE_IDENTITY_DECISION') {
    const payload = message.payload as { decision?: 'continue' | 'stop', host?: string, platformIdentity?: unknown } | undefined
    void handleCookieIdentityDecision(payload)
      .then(result => sendResponse({ ok: true, result }))
      .catch(error => sendResponse({
        ok: false,
        message: error instanceof Error ? error.message : 'cookie identity decision failed',
      }))
    return true
  }
  if (message.type === 'GEO_EDITOR_READY') {
    sendResponse({ ok: true })
    return true
  }
  if (message.type === 'GEO_START_FILL_TASK') {
    // Compliance requirement: B5a opens and fills the editor only. Publishing remains manual.
    void startFillTask(message.payload as ExtensionTaskListItem)
      .then(result => sendResponse({ ok: true, result }))
      .catch(error => sendResponse({
        ok: false,
        message: error instanceof Error ? error.message : 'fill failed',
      }))
    return true
  }
  if (message.type === 'GEO_TASK_PUBLISHED') {
    const payload = message.payload as PublishTaskReport & { taskId: number }
    const { taskId, ...report } = payload
    void publishActiveTask(taskId, report)
      .then(() => sendResponse({ ok: true }))
      .catch(error => sendResponse({
        ok: false,
        message: error instanceof Error ? error.message : 'publish report failed',
      }))
    return true
  }
  if (message.type !== 'GEO_CAPTURE_COOKIES') return false

  void captureCookiesForAccount(message.payload as ExtensionSelfMediaAccount)
    .then(result => sendResponse({ ok: true, result }))
    .catch(error => sendResponse({
      ok: false,
      message: error instanceof Error ? error.message : 'cookie capture failed',
    }))
  return true
})

function isAdminBridgeMessage(message: ExtensionMessage): boolean {
  return (message as AdminBridgeMessage).channel === BRIDGE_CHANNEL
}

export async function handleAdminBridgeMessage(message: AdminBridgeMessage) {
  if (message.type === 'GEO_PING') {
    return pongMessage(message.requestId, Boolean(await sessionStorage.get()))
  }
  if (message.type === 'GEO_BIND_EXTENSION') {
    return handleAdminBindExtension(message)
  }
  if (message.type === 'GEO_START_COOKIE_CAPTURE') {
    return handleAdminCookieCapture(message)
  }
  if (message.type !== 'GEO_START_FILL') {
    return {
      type: 'GEO_FILL_ERROR',
      payload: {
        code: 'UNSUPPORTED_BRIDGE_MESSAGE',
        message: '不支持的后台扩展命令',
      },
    }
  }
  const session = await sessionStorage.get()
  if (!session) {
    return {
      type: 'GEO_FILL_ERROR',
      payload: {
        code: 'EXTENSION_UNBOUND',
        message: '扩展未绑定，请先在扩展中完成绑定。',
      },
    }
  }
  const payload = message.payload as AdminStartFillPayload | undefined
  if (!payload?.taskId || !payload.platform) {
    return {
      type: 'GEO_FILL_ERROR',
      payload: {
        code: 'BAD_START_FILL_PAYLOAD',
        message: '后台启动填充参数不完整。',
      },
    }
  }
  const activeTask = await getActiveTask()
  if (activeTask) {
    return {
      type: 'GEO_FILL_ERROR',
      payload: {
        taskId: payload.taskId,
        code: 'ACTIVE_TASK_EXISTS',
        message: '已有填充任务正在处理中，请完成或关闭后再启动新的任务。',
      },
    }
  }

  const task: ExtensionTaskListItem = {
    taskId: payload.taskId,
    platform: payload.platform,
    status: 'token_issued',
    createdAt: new Date().toISOString(),
    fillTokenIssuedAt: new Date().toISOString(),
    expiresAt: new Date(Date.now() + 5 * 60_000).toISOString(),
  }
  await startFillTask(task)
  return {
    type: 'GEO_FILL_STATUS',
    payload: {
      taskId: payload.taskId,
      status: 'filled',
      message: '已打开编辑器并完成填充，请在平台页面人工确认并发布。',
    },
  }
}

async function handleAdminBindExtension(message: AdminBridgeMessage) {
  const existing = await sessionStorage.get()
  if (existing) {
    return {
      type: 'GEO_BIND_STATUS',
      payload: {
        bound: true,
        sessionId: existing.sessionId,
        message: '扩展已绑定，无需重复绑定。',
      },
    }
  }
  const payload = message.payload as AdminBindExtensionPayload | undefined
  if (!payload?.bindCode || !payload.brandId) {
    return {
      type: 'GEO_FILL_ERROR',
      payload: {
        code: 'BAD_BIND_PAYLOAD',
        message: '后台绑定扩展参数不完整。',
      },
    }
  }
  const session = await bindExtension({
    bindCode: payload.bindCode,
    brandId: payload.brandId,
  })
  return {
    type: 'GEO_BIND_STATUS',
    payload: {
      bound: true,
      sessionId: session.sessionId,
      message: '扩展已自动绑定到当前品牌。',
    },
  }
}

async function handleAdminCookieCapture(message: AdminBridgeMessage) {
  const session = await sessionStorage.get()
  if (!session) {
    return {
      type: 'GEO_FILL_ERROR',
      payload: {
        code: 'EXTENSION_UNBOUND',
        message: '扩展未绑定，请先在扩展中完成绑定。',
      },
    }
  }
  const payload = message.payload as AdminStartCookieCapturePayload | undefined
  if (!payload?.brandId || !payload.accountId || !payload.platform) {
    return {
      type: 'GEO_FILL_ERROR',
      payload: {
        code: 'BAD_COOKIE_CAPTURE_PAYLOAD',
        message: '后台启动凭证捕获参数不完整。',
      },
    }
  }
  const result = await startCookieCaptureForAccount({
    brandId: payload.brandId,
    accountId: payload.accountId,
    platform: payload.platform,
    accountName: payload.accountName ?? null,
  })
  return {
    type: 'GEO_COOKIE_CAPTURE_STATUS',
    payload: result,
  }
}
