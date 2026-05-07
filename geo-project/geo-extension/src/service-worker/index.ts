import { EXTENSION_VERSION } from '@/shared/env'
import { extensionApi } from '@/shared/api'
import { logger } from '@/shared/logger'
import { sessionStorage } from '@/shared/storage'

chrome.runtime.onInstalled.addListener(() => {
  logger.info('installed', EXTENSION_VERSION)
})

chrome.alarms.create('geo-token-refresh', { periodInMinutes: 30 })

chrome.alarms.onAlarm.addListener(async (alarm) => {
  if (alarm.name !== 'geo-token-refresh') return
  const session = await sessionStorage.get()
  if (!session) return
  try {
    await extensionApi.refresh(session.token, EXTENSION_VERSION)
  } catch (error) {
    logger.warn('token refresh failed', error instanceof Error ? error.message : error)
  }
})
