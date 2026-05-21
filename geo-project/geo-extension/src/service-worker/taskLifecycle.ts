import { ExtensionApiError, extensionApi } from '@/shared/api'
import { sessionStorage } from '@/shared/storage'
import type { PublishTaskReport, TaskLifecycleEvent } from '@/types/extension'

export const HEARTBEAT_INTERVAL_MS = 30_000
export const HEARTBEAT_ALARM_NAME = 'geo-task-heartbeat'
export const ACTIVE_TASK_KEY = 'geo:active-task'
export const PUBLISHING_TASK_KEY = 'geo:publishing-task'
const HEARTBEAT_PERIOD_MINUTES = HEARTBEAT_INTERVAL_MS / 60_000
const MAX_ACTIVE_TASK_AGE_MS = 2 * 60 * 60 * 1000

export interface PersistedActiveTask {
  taskId: number
  tabId: number
  token: string
  startedAt: number
}

export async function startTaskLifecycle(taskId: number, tabId: number, token: string) {
  await chrome.storage.session.set({
    [ACTIVE_TASK_KEY]: {
      taskId,
      tabId,
      token,
      startedAt: Date.now(),
    } satisfies PersistedActiveTask,
  })
  chrome.alarms.create(HEARTBEAT_ALARM_NAME, { periodInMinutes: HEARTBEAT_PERIOD_MINUTES })
}

export async function stopTaskLifecycle() {
  await chrome.storage.session.remove(ACTIVE_TASK_KEY)
  await chrome.alarms.clear(HEARTBEAT_ALARM_NAME)
}

export async function publishActiveTask(taskId: number, report?: PublishTaskReport) {
  const task = await getActiveTask()
  if (!task || task.taskId !== taskId) return
  await chrome.storage.session.set({ [PUBLISHING_TASK_KEY]: taskId })
  try {
    await extensionApi.publishedTask(task.token, taskId, report)
    await stopTaskLifecycle()
    notifyPopup({ taskId, kind: 'published', message: '任务已上报 published。' })
  } finally {
    await chrome.storage.session.remove(PUBLISHING_TASK_KEY)
  }
}

export async function handleTaskHeartbeatAlarm() {
  const active = await getActiveTask()
  if (!active) {
    await chrome.alarms.clear(HEARTBEAT_ALARM_NAME)
    return
  }
  if (!active.token || Date.now() - active.startedAt > MAX_ACTIVE_TASK_AGE_MS) {
    await stopTaskLifecycle()
    notifyPopup({ taskId: active.taskId, kind: 'stopped', message: '任务填充已超时，已停止 heartbeat。' })
    return
  }
  try {
    await chrome.tabs.get(active.tabId)
  } catch {
    await abandonActiveTask(active, '平台编辑器已关闭，本次分发已取消。')
    return
  }
  await heartbeat(active)
}

export async function handleTaskTabRemoved(closedTabId: number) {
  const active = await getActiveTask()
  if (active?.tabId !== closedTabId) return
  await abandonActiveTask(active, '平台编辑器已关闭，本次分发已取消。')
}

async function abandonActiveTask(active: PersistedActiveTask, successMessage: string) {
  await stopTaskLifecycle()
  if (await isPublishingTask(active.taskId)) return
  try {
    await extensionApi.abandonTask(active.token, active.taskId)
    notifyPopup({ taskId: active.taskId, kind: 'stopped', message: successMessage })
  } catch (error) {
    if (error instanceof ExtensionApiError && error.code === 70013) return
    notifyPopup({ taskId: active.taskId, kind: 'stopped', message: '平台编辑器已关闭，本地任务已停止。' })
  }
}

export async function getActiveTask(): Promise<PersistedActiveTask | null> {
  const stored = await chrome.storage.session.get(ACTIVE_TASK_KEY)
  return (stored[ACTIVE_TASK_KEY] as PersistedActiveTask | undefined) ?? null
}

async function isPublishingTask(taskId: number): Promise<boolean> {
  const stored = await chrome.storage.session.get(PUBLISHING_TASK_KEY)
  return stored[PUBLISHING_TASK_KEY] === taskId
}

async function heartbeat(task: PersistedActiveTask) {
  try {
    await extensionApi.heartbeatTask(task.token, task.taskId)
  } catch (error) {
    if (!(error instanceof ExtensionApiError)) return
    if (error.code === 70013) return
    if (error.code === 70002 || error.code === 70004) {
      await sessionStorage.clear()
      await stopTaskLifecycle()
      notifyPopup({ taskId: task.taskId, kind: 'auth_required', message: '扩展登录已失效，请重新绑定。' })
      return
    }
    if (error.code === 70011 || error.code === 70012) {
      await stopTaskLifecycle()
      notifyPopup({ taskId: task.taskId, kind: 'stopped', message: '任务状态已变化，已停止 heartbeat。' })
    }
  }
}

function notifyPopup(payload: TaskLifecycleEvent) {
  void chrome.runtime.sendMessage({ type: 'GEO_TASK_LIFECYCLE_EVENT', payload }).catch(() => undefined)
}
