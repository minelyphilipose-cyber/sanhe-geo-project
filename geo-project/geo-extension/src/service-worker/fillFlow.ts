import { EXTENSION_VERSION } from '@/shared/env'
import { extensionApi } from '@/shared/api'
import {
  isAllowedPublishUrl,
  profileForPlatform,
  sanitizeTitle,
  type PlatformFillProfile,
} from '@/shared/fillProfiles'
import { sessionStorage } from '@/shared/storage'
import { startTaskLifecycle } from './taskLifecycle'
import type {
  ExtensionMessage,
  ExtensionTaskListItem,
  ExtensionTaskStateResponse,
  FillCommandPayload,
  SemiAutoFillPayload,
} from '@/types/extension'

interface FillResult {
  ok: boolean
  message?: string
}

export async function startFillTask(task: ExtensionTaskListItem): Promise<ExtensionTaskStateResponse> {
  // Compliance requirement: this flow only prepares the editor. It must never trigger publish.
  const session = await sessionStorage.get()
  if (!session) throw new Error('扩展登录已失效，请重新绑定。')
  const profile = profileForPlatform(task.platform)

  const issue = await extensionApi.issueFillToken(session.token, {
    taskTargetId: task.taskId,
    platform: task.platform,
    extensionVersion: EXTENSION_VERSION,
  })
  const consumed = await extensionApi.consumeFillToken(session.token, {
    fillToken: issue.fillToken,
    platform: task.platform,
    extensionVersion: EXTENSION_VERSION,
  })
  const command = buildFillCommand(task.taskId, consumed.fillPayload, profile)
  const tab = await chrome.tabs.create({ url: command.publishUrl })
  if (!tab.id) throw new Error('编辑器标签页创建失败')
  await startTaskLifecycle(task.taskId, tab.id, session.token)
  await waitForEditorReady(tab.id, 15_000)
  const result = await chrome.tabs.sendMessage<ExtensionMessage<FillCommandPayload>, FillResult>(
    tab.id,
    { type: 'GEO_FILL_TASK', payload: command },
  )
  if (!result?.ok) throw new Error(result?.message || '编辑器填充失败')
  const acked = await extensionApi.ackTask(session.token, task.taskId)
  return acked
}

function buildFillCommand(taskId: number, fillPayload: string, profile: PlatformFillProfile): FillCommandPayload {
  const payload = JSON.parse(fillPayload) as SemiAutoFillPayload
  const publishUrl = payload.publishUrl
  if (!publishUrl || !isAllowedPublishUrl(profile.platform, publishUrl)) {
    throw new Error('服务端返回的发布地址不在白名单内')
  }
  return {
    taskId,
    platform: profile.platform,
    publishUrl,
    title: sanitizeTitle(payload.title),
    contentHtml: payload.renderedHtml ?? '',
    coverImageUrl: payload.coverImageUrl ?? null,
    tags: payload.tags ?? [],
    category: payload.category ?? null,
  }
}

function waitForEditorReady(tabId: number, timeoutMs: number): Promise<void> {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      cleanup()
      reject(new Error('编辑器加载超时'))
    }, timeoutMs)
    const onRemoved = (closedTabId: number) => {
      if (closedTabId !== tabId) return
      cleanup()
      reject(new Error('编辑器标签页已关闭'))
    }
    const onMessage = (message: ExtensionMessage, sender: chrome.runtime.MessageSender) => {
      if (message.type !== 'GEO_EDITOR_READY' || sender.tab?.id !== tabId) return
      cleanup()
      resolve()
    }
    const cleanup = () => {
      clearTimeout(timer)
      chrome.runtime.onMessage.removeListener(onMessage)
      chrome.tabs.onRemoved.removeListener(onRemoved)
    }
    chrome.runtime.onMessage.addListener(onMessage)
    chrome.tabs.onRemoved.addListener(onRemoved)
  })
}
