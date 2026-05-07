import { ExtensionApiError, extensionApi } from '@/shared/api'
import { sessionStorage } from '@/shared/storage'
import type { TaskLifecycleEvent } from '@/types/extension'

export const HEARTBEAT_INTERVAL_MS = 30_000

interface ActiveTask {
  taskId: number
  tabId: number
  token: string
  timer: ReturnType<typeof setInterval>
  onRemoved: (tabId: number) => void
}

let activeTask: ActiveTask | null = null

export function startTaskLifecycle(taskId: number, tabId: number, token: string) {
  stopTaskLifecycle()
  const onRemoved = (closedTabId: number) => {
    if (closedTabId === tabId) stopTaskLifecycle()
  }
  activeTask = {
    taskId,
    tabId,
    token,
    timer: setInterval(() => {
      void heartbeat(taskId)
    }, HEARTBEAT_INTERVAL_MS),
    onRemoved,
  }
  chrome.tabs.onRemoved.addListener(onRemoved)
}

export function stopTaskLifecycle() {
  if (!activeTask) return
  clearInterval(activeTask.timer)
  chrome.tabs.onRemoved.removeListener(activeTask.onRemoved)
  activeTask = null
}

export async function publishActiveTask(taskId: number) {
  if (!activeTask || activeTask.taskId !== taskId) return
  const task = activeTask
  await extensionApi.publishedTask(task.token, taskId)
  stopTaskLifecycle()
  notifyPopup({ taskId, kind: 'published', message: '任务已上报 published。' })
}

async function heartbeat(taskId: number) {
  const task = activeTask
  if (!task || task.taskId !== taskId) return
  try {
    await extensionApi.heartbeatTask(task.token, taskId)
  } catch (error) {
    if (!(error instanceof ExtensionApiError)) return
    if (error.code === 70013) return
    if (error.code === 70002 || error.code === 70004) {
      await sessionStorage.clear()
      stopTaskLifecycle()
      notifyPopup({ taskId, kind: 'auth_required', message: '扩展登录已失效，请重新绑定。' })
      return
    }
    if (error.code === 70011 || error.code === 70012) {
      stopTaskLifecycle()
      notifyPopup({ taskId, kind: 'stopped', message: '任务状态已变化，已停止 heartbeat。' })
    }
  }
}

function notifyPopup(payload: TaskLifecycleEvent) {
  void chrome.runtime.sendMessage({ type: 'GEO_TASK_LIFECYCLE_EVENT', payload }).catch(() => undefined)
}
