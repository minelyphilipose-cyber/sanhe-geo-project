import { EXTENSION_VERSION } from '@/shared/env'
import { extensionApi } from '@/shared/api'
import {
  isAllowedPublishUrl,
  profileForPlatform,
  sanitizeContentHtml,
  sanitizeTitle,
  type PlatformFillProfile,
} from '@/shared/fillProfiles'
import { sessionStorage } from '@/shared/storage'
import type {
  ExtensionMessage,
  ExtensionTaskListItem,
  ExtensionTaskStateResponse,
  FillTokenConsumeResponse,
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
  if (!task.publishUrl || !isAllowedPublishUrl(task.platform, task.publishUrl)) {
    throw new Error('发布地址不在平台白名单内')
  }

  const issue = await extensionApi.issueFillToken(session.token, {
    taskTargetId: task.taskId,
    platform: task.platform,
    extensionVersion: EXTENSION_VERSION,
  })
  let consumed: FillTokenConsumeResponse | undefined = await extensionApi.consumeFillToken(session.token, {
    fillToken: issue.fillToken,
    platform: task.platform,
    extensionVersion: EXTENSION_VERSION,
  })
  const command = buildFillCommand(task.taskId, consumed.fillPayload, profile)
  let cookiesJson: string | undefined = consumed.cookiesJson
  consumed = undefined
  await injectCookies(profile, cookiesJson, command.publishUrl)
  cookiesJson = undefined
  const tab = await chrome.tabs.create({ url: command.publishUrl })
  if (!tab.id) throw new Error('编辑器标签页创建失败')
  await waitForEditorReady(tab.id, 15_000)
  const result = await chrome.tabs.sendMessage<ExtensionMessage<FillCommandPayload>, FillResult>(
    tab.id,
    { type: 'GEO_FILL_TASK', payload: command },
  )
  if (!result?.ok) throw new Error(result?.message || '编辑器填充失败')
  return extensionApi.ackTask(session.token, task.taskId)
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
    contentHtml: sanitizeContentHtml(payload.renderedHtml),
    coverImageUrl: payload.coverImageUrl ?? null,
    tags: payload.tags ?? [],
    category: payload.category ?? null,
  }
}

async function injectCookies(profile: PlatformFillProfile, cookiesJson: string | undefined, publishUrl: string): Promise<void> {
  let cookies: chrome.cookies.Cookie[] = JSON.parse(cookiesJson || '[]') as chrome.cookies.Cookie[]
  try {
    for (const cookie of cookies) validateCookieDomain(profile, cookie, publishUrl)
    await Promise.all(cookies.map(cookie => setCookieRaw(cookie, publishUrl)))
  } finally {
    cookies = []
  }
}

function validateCookieDomain(profile: PlatformFillProfile, cookie: chrome.cookies.Cookie, publishUrl: string) {
  const domain = (cookie.domain || new URL(publishUrl).hostname).replace(/^\./, '')
  if (!profile.cookieDomains.some(allowed => domain === allowed || domain.endsWith(`.${allowed}`))) {
    throw new Error('cookie 域名不在平台白名单内')
  }
}

async function setCookieRaw(cookie: chrome.cookies.Cookie, publishUrl: string) {
  const domain = (cookie.domain || new URL(publishUrl).hostname).replace(/^\./, '')
  await chrome.cookies.set({
    url: `https://${domain}${cookie.path || '/'}`,
    name: cookie.name,
    value: cookie.value,
    domain: cookie.domain,
    path: cookie.path || '/',
    secure: cookie.secure,
    httpOnly: cookie.httpOnly,
    sameSite: cookie.sameSite,
    expirationDate: cookie.expirationDate,
  })
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
