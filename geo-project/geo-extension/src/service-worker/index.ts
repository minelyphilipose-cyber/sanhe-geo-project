import { EXTENSION_VERSION } from '@/shared/env'
import { ExtensionApiError, extensionApi } from '@/shared/api'
import { logger } from '@/shared/logger'
import { sessionStorage } from '@/shared/storage'
import { captureCookiesForAccount } from './cookieCapture'
import { startFillTask } from './fillFlow'
import type { ExtensionMessage, ExtensionSelfMediaAccount, ExtensionTaskListItem } from '@/types/extension'

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
    // TODO(B6): map 70002/70004 to an explicit rebind state in popup. Keeping the
    // stale local session here does not grant access because the backend rejects it,
    // but the UX needs a clear recovery path.
    if (error instanceof ExtensionApiError && (error.code === 70002 || error.code === 70004)) {
      logger.warn('token refresh requires rebind', { code: error.code })
      return
    }
    logger.warn('token refresh failed', error instanceof Error ? error.message : error)
  }
})

chrome.runtime.onMessage.addListener((message: ExtensionMessage, _sender, sendResponse) => {
  if (message.type === 'GEO_COOKIE_DOMAIN_READY' || message.type === 'GEO_EDITOR_READY') {
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
  if (message.type !== 'GEO_CAPTURE_COOKIES') return false

  void captureCookiesForAccount(message.payload as ExtensionSelfMediaAccount)
    .then(result => sendResponse({ ok: true, result }))
    .catch(error => sendResponse({
      ok: false,
      message: error instanceof Error ? error.message : 'cookie capture failed',
    }))
  return true
})
